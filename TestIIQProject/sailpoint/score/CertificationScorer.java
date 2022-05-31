/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * Score calculator for certification age.
 * 
 * ALGORITHM NOTES
 * 
 * Requirements are less clear here, but one measure that I've heard a
 * lot is that the score should increase as the length of time since the
 * last certification increases.
 * 
 * We might want to factor in the number of mitigations and remediations
 * made in the last certification, but for now we'll just do time.
 * 
 * Some options:
 * 
 * - Linear
 * 
 * Configuration sets a simple time range, e.g. 180 days
 * The score range (0-1000) is then spread out over the days
 * in that time range.  For example, a score range of 1000 
 * would divide by 180 for 5.5 "units" per day.  Example
 * scores would be:
 * 
 *   today : 0
 *   1 day ago : 5
 *   2 days ago : 11
 *   3 days ago : 16
 *   ...
 * 
 * - Offset Linear
 * 
 * Like linear but non-zero scoring doesn't kick in until a 
 * number of days has past.  E.g. score remains zero for
 * 4 weeks, then starts accumulating.
 * 
 * - Bands
 * 
 * For each band, set a date range, e.g. 0-30 days is band 0,
 * 31-60 days is band 1, 61-* days is band 3.  Then assign
 * each band a score.
 * 
 * The effect is similar to Linear, but the scores are more
 * "bucketed".  This doesn't feel as useful and is actually
 * harder to configure than linear.  For time based scores,
 * we are likely to want more than 3 or 4 bands.  
 * 
 * And the winner is...Offset Linear!
 * 
 * Author: Jeff
 */

package sailpoint.score;

import java.util.Date;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.Meter;
import sailpoint.api.ObjectUtil;
import sailpoint.api.SailPointContext;
import sailpoint.object.Attributes;
import sailpoint.object.Certification;
import sailpoint.object.CertificationEntity;
import sailpoint.object.CertificationLink;
import sailpoint.object.GenericIndex;
import sailpoint.object.Identity;
import sailpoint.object.SailPointObject;
import sailpoint.object.ScoreConfig;
import sailpoint.object.ScoreDefinition;
import sailpoint.object.ScoreItem;
import sailpoint.object.Scorecard;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.tools.Message;
import sailpoint.web.messages.MessageKeys;

public class CertificationScorer extends AbstractScorer {

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

	private static Log log = LogFactory.getLog(CertificationScorer.class);

    public static final String ARG_OFFSET = "offset";
    public static final String ARG_RANGE = "range";

    //////////////////////////////////////////////////////////////////////
    //
    // Scorer Methods
    //
    //////////////////////////////////////////////////////////////////////

    public CertificationScorer() {
    }

    public ScoreItem isMatch(SailPointObject obj) {
        return null;
    }

    /**
     * Extract the score value maintained by this Scorer from
     * the Scorecard.
     */
    public int getScore(ScoreDefinition def, GenericIndex index) {

        Scorecard card = (Scorecard)index;
        return card.getCertificationScore();
    }

    /**
     * Calculate a score from the contents of an Identity cube.
     */
    public void score(SailPointContext context,
                      ScoreConfig config,
                      ScoreDefinition def,
                      SailPointObject src,
                      GenericIndex index)
        throws GeneralException {

        final String METER_NAME = "Score - certification";
        Meter.enterByName(METER_NAME);

        // can only be used with these
        Identity id = (Identity)src;
        Scorecard card = (Scorecard)index;

        int score = 0;

        // ignore identities that don't have anything worth certifiying
        if (needsCertification(context, id)) {
            Attributes<String,Object> args = config.getEffectiveArguments(def);

            // this could be cached since we will normally use the same 
            // ScoreDefinition many times...
            int offset = getInt(args, ARG_OFFSET);
            int range = getInt(args, ARG_RANGE);
            int max = getMaximumScore(config);
            float units = (float)max / (float)range;
            long days = 0;
    
            if (range < 0) {
                // if there is no range specified, we could either treat this
                // as an ininite range that never raises the score, or an extremely
                // short range that immediately produces the maximum score
            }
            else {
                Date last = getLastCertificationDate(context, id);
                if (last == null) {
                    // no certification, we max
                    score = getMaximumScore(config);
                }
                else {
                    // can probably use Calendar for this, but it sure 
                    // isn't obvious
                    long lastMillis = last.getTime();
                    long nowMillis = new Date().getTime();
                    if (lastMillis > nowMillis) {
                        // certification is after the current time, 
                        // something is probably wrong, but assume this
                        // means that a fresh certification is in range
                    }
                    else {
                        long millisPerDay = 86400000;
                        days = (nowMillis - lastMillis) / millisPerDay;
                        // factor out the offset
                        days -= offset;
                        if (days > 0)
                            score = (int)(units * days);
                    }
                }
            }

            score = constrainScore(config, score);

            // there is only one "item" for this score type
            // weight should always be non-zero here since we don't
            // have the baseline/compensated dichotomy
            if (def.getWeight() > 0) {
                ScoreItem item = new ScoreItem(def);
                item.setScore(score);
                item.setScorePercentage(100);

                if (days == 0)
                    item.setTargetMessage(new Message(MessageKeys.CERT_SCORER_TARGET_ID_NOT_CERTIFIED));
                else
                    item.setTargetMessage(new Message(MessageKeys.CERT_SCORER_TARGET_NOT_CERTIFIED_RECENTLY,
                            Util.ltoa(days)));               

                card.addItem(item);
            }
        }

        card.setCertificationScore(score);
        
        Meter.exitByName(METER_NAME);
    }

    /**
     * Return true if this identity has something that needs to 
     * be certified.
     */
    public boolean needsCertification(SailPointContext context, Identity id) 
        throws GeneralException {

        // What about mitigation expirations?  If the entitlement is no longer
        // there then pending mitigations are meaningless?

        return (notEmpty(id.getBundles()) || 
                notEmpty(id.getExceptions()) ||
                notEmpty(ObjectUtil.getPolicyViolations(context, id)));
    }

    private boolean notEmpty(List l) {

        return (l != null && l.size() > 0);
    }

    /**
     * Returns the Date of the most recent certification. 
     * 
     * For periodic certs we look for the Manager or Identity
     * cert link that has the most recent completion date.
     *
     * For continuous certs we look for the link that has the most
     * recent modification date since these will not have completion dates.
     *
     * Only continuous certs will have null completion dates.
     * If there is a mixture (unusual) we'll take the one with the most
     * recent date.
     *
     */
    public Date getLastCertificationDate(SailPointContext context, Identity id)
        throws GeneralException {

        Date date = null;

        // Identity has this utility method but we don't use it any
        // more so we have more control.  Delete this method if we
        // dont't need it anywhere else!
        //CertificationLink link = id.getLatestCertification();

        List<CertificationLink> links = id.getCertifications();
        if (links != null) {
            for (CertificationLink link : links) {

                // !!should we be ignoring app owner certs?
                
                Date d = link.getCompleted();
                if (d == null) {
                    // a continuous cert, look at mod time
                    d = link.getModified();
                    if (d == null) {
                        // !! we're not currently maintining this so
                        // have to dig it out of the Cert, 
                        // this is EXPENSIVE
                        Certification cert = context.getObjectById(Certification.class, link.getId());
                        if (cert != null) {
                            CertificationEntity ent = cert.getEntity(id);
                            if (ent != null)
                                d = ent.getModified();
                            // try to keep the cache clean
                            context.decache(cert);
                        }
                    }
                }

                if (d != null && (date == null || d.compareTo(date) > 0))
                    date = d;
            }
        }

        return date;
    }

}
