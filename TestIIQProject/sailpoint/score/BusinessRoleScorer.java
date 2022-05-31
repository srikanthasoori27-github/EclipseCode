/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * Score calculator for Business Role assignments.
 *
 * Author: Jeff
 * 
 * NOTE: The language used in this file is old, what we now call
 * "detected roles" we used to call "business roles".
 *
 * Business roles are now a type of "assignable role" which are
 * different than detected roles.  Confused?  You bet!
 * The algorithm originally only looked at the detected role list,
 * now it looks at both the detected and assingned roles.
 * 
 * ALGORITHM NOTES
 *
 * This class calculates two scores depending on configuration:
 *
 *   Raw Business Role
 *
 *     This is a simple sum of the weights of all assigned
 *     business roles.  It can be used as a measure of raw 
 *     entitlement.
 *
 *   Compensated Business Role
 *
 *     This takes the raw score, and modifies it based on actions
 *     taken during the last certification.
 *
 * Since the code for both of these is very similar, we implement
 * both in the same class, but they are configired as seperate
 * scores in the ScoreConfig.  
 *
 * To calculate the compensated score, for each assigment, we get 
 * the business role weight and multiply it by a factor selected
 * based on the last certification status for* this bundle.
 * The default factors are:
 *
 *   not certified = 1
 *   certified = 0
 *   mitigated = .5
 *   mitigated expired = 1
 *   remediated = 2
 *
 * The notion here is that if a business role was certified, then 
 * it should be considered "safe" and no longer contribute to the
 * score until this certification ages.  You can however still
 * have the raw business role score contribute to the compensated
 * score by giving it a non-zero "certified" factor.
 *
 * If the business role was mitigated, you would typically want
 * the business role weight reduced somewhat.
 *
 * If the business role was mitigated, and the mitigation has
 * expired you want the job scored at at least its maximum
 * weight and possibly higher.
 *
 * If the business role was remediated, and still exists, the
 * weight can be multiplied to reflect a greater severity.
 * 
 */

package sailpoint.score;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.IdentityHistoryService;
import sailpoint.api.Meter;
import sailpoint.api.SailPointContext;
import sailpoint.object.ActivityConfig;
import sailpoint.object.Application;
import sailpoint.object.ApplicationConfig;
import sailpoint.object.Attributes;
import sailpoint.object.Bundle;
import sailpoint.object.CertificationAction;
import sailpoint.object.GenericIndex;
import sailpoint.object.Identity;
import sailpoint.object.IdentityHistoryItem;
import sailpoint.object.RoleAssignment;
import sailpoint.object.RoleDetection;
import sailpoint.object.RoleTarget;
import sailpoint.object.SailPointObject;
import sailpoint.object.ScoreConfig;
import sailpoint.object.ScoreDefinition;
import sailpoint.object.ScoreItem;
import sailpoint.object.Scorecard;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;

public class BusinessRoleScorer extends AbstractScorer {

    /**
     * A flag that says whether to check certification history for
     * compensated scoring.  This is an optimization to prevent many
     * expensive lookups for CertificationHistoryItems if an identity
     * has no history.
     */
    Boolean _checkCertHistory;

    //
    // Transient score state
    //

    SailPointContext _context;
    Identity _identity;
    ScoreConfig _config;
    ScoreDefinition _definition;

    List<ScoreItem> _items;
    float _score;
    Date _now;

    boolean _compensated;
    float _uncertifiedFactor;
    float _certifiedFactor;
    float _mitigatedFactor;
    float _expiredFactor;
    float _remediatedFactor;

    Set<Application> _apps;
    IdentityHistoryService _historyService;
    Map<String,List<RoleAssignment>> _scoredAssignments;

    //////////////////////////////////////////////////////////////////////
    //
    // Scorer Methods
    //
    //////////////////////////////////////////////////////////////////////

    public BusinessRoleScorer() {
    }

    private static Log log = LogFactory.getLog(BusinessRoleScorer.class);
    /**
     * Extract the score value maintained by this Scorer from
     * the Scorecard.
     */
    public int getScore(ScoreDefinition def, GenericIndex index) {

        int score = 0;
        Scorecard card = (Scorecard)index;

        if (def.isCompensated())
            score = card.getBusinessRoleScore();
        else
            score = card.getRawBusinessRoleScore();
        
        return score;
    }

    /**
     * Calculate a score from the business roles assigned to an identity.
     */
    public void score(SailPointContext context,
                      ScoreConfig config,
                      ScoreDefinition def,
                      SailPointObject src,
                      GenericIndex index)
        throws GeneralException {

        final String METER_NAME =
            "Score - role " + (def.isCompensated() ? "compensated" : "raw");
        Meter.enterByName(METER_NAME);
        
        Attributes<String,Object> args = config.getEffectiveArguments(def);

        // this scorer can only be used with identities
        _context = context;
        _config = config;
        _definition = def;
        _identity = (Identity)src;
        _items = new ArrayList<ScoreItem>();
        _score = 0;
        _now = new Date();
        _compensated = def.isCompensated();
        _uncertifiedFactor = getFloat(args, ARG_FACTOR_UNCERTIFIED, DEFAULT_FACTOR_UNCERTIFIED);
        _certifiedFactor = getFloat(args, ARG_FACTOR_CERTIFIED, DEFAULT_FACTOR_CERTIFIED);
        _mitigatedFactor = getFloat(args, ARG_FACTOR_MITIGATED, DEFAULT_FACTOR_MITIGATED);
        _expiredFactor = getFloat(args, ARG_FACTOR_EXPIRED, DEFAULT_FACTOR_EXPIRED);
        _remediatedFactor = getFloat(args, ARG_FACTOR_REMEDIATED, DEFAULT_FACTOR_REMEDIATED);

        // TODO: What exactly are we getting here?  If this is simply a union of 
        // all applications on all the bundles we may want to remove this.  Otherwise
        // we should keep it.  I'm commenting this section out for now.  We can always 
        // put it back later if we really need it. 
        // -- Bernie Margolis
        // Get a list of all applications associated with this identity that have
        // activity monitoring enabled on them
        //        Set<String> appIds = id.getActivityConfig().getEnabledApplications();
        _apps = new HashSet<Application>();
        //        QueryOptions ops = new QueryOptions();
        //        ops.add(Filter.in("id", appIds));
        //        apps.addAll(context.getObjectsById(Application.class, ops));

        // tool to look up certification history
        _historyService = new IdentityHistoryService(context);


        // add assignments
        List<RoleAssignment> assignments = _identity.getRoleAssignments();
        for (RoleAssignment ra : Util.iterate(assignments)) {
            addScoredAssignment(ra);
            Bundle role = ra.getRoleObject(_context);
            /*
             * IIQETN-6100 Adding check to see if the RoleAssignment is negative, which
             * would mean that the role was removed from the Identity and should not be
             * scored.
             */
            if(role != null && !ra.isNegative()) {
                score(role);
            } else {
                //Role has been swept out from under us
                log.warn("Unable to score RoleAssignment for deleted role " + ra.getRoleName());
            }
        }

        // then detections that were not also assigned
        List<RoleDetection> detections = _identity.getRoleDetections();
        for (RoleDetection rd : Util.iterate(detections)) {
            if (!isAlreadyScored(rd)) {
                Bundle role = rd.getRoleObject(_context);
                score(role);
            }
        }

        // round up and constrain
        int intScore = Math.round(_score);
        int constrainedScore = constrainScore(config, intScore);
    
        Scorecard card = (Scorecard)index;
        if (_compensated)
            card.setBusinessRoleScore(constrainedScore);
        else
            card.setRawBusinessRoleScore(constrainedScore);

        // Note that percentages are being calculated from the original 
        // score not the constrained score.  This will make the percentages
        // accurage but this may look funy after constrainting.
        // Example item A and B both contributes 1000, so their percentage
        // is 50%.  But the score of 2000 is rolled down to 1000 so really
        // A and B could be considered in the 100% range.  But this
        // would mean that the total of the item percentages could be
        // greater than 100% which might look funny.  Think on these things...

        if (_compensated) {
            setPercentages(_items, intScore);
            card.addItems(_items);
        }

        Meter.exitByName(METER_NAME);
    }
    
    /**
     * Remember this assignment that has been scored.  Used later
     * by isAlreadyScored.
     */
    private void addScoredAssignment(RoleAssignment ra) {
        if (_scoredAssignments == null)
            _scoredAssignments = new HashMap<String,List<RoleAssignment>>();
        List<RoleAssignment> list = _scoredAssignments.get(ra.getRoleName());
        if (list == null)
            list = new ArrayList<RoleAssignment>();
        list.add(ra);
    }

    /**
     * Check to see if a detected role was already scored
     * because it was assigned.  This can only happen when we allow roles
     * to be both asisgnable and detectable.  Pre 6.3 this could simply check
     * for a previous encounter with a role by name, but now we have to match
     * the RoleTarget list as well.  If a detected role does not have the same
     * targets as an assigned role with the same name, it is a distinct detection
     * and must be scored.
     */
    private boolean isAlreadyScored(RoleDetection det) {
        boolean scored = false;
        if (_scoredAssignments != null) {
            List<RoleAssignment> list = _scoredAssignments.get(det.getName());
            for (RoleAssignment ra : Util.iterate(list)) {
                if (RoleTarget.isEqual(ra.getTargets(), det.getTargets())) {
                    scored = true;
                    break;
                }
            }
        }
        return scored;
    }

    /**
     * Calculate a score for one role.
     */
    public void score(Bundle role) throws GeneralException {

        // todo: may eventually need to check the type of bundle
        int weight = role.getRiskScoreWeight();
        float factor = _uncertifiedFactor;

        boolean hasWeightedFactors =
            (1.0f != _uncertifiedFactor) ||
            (1.0f != _certifiedFactor) ||
            (1.0f != _mitigatedFactor) ||
            (1.0f != _expiredFactor) ||
            (1.0f != _remediatedFactor);

        // Only look at history if there is some.  Also only do this if
        // there are non-1 factors, otherwise the history doesn't matter.
        if (weight > 0 && _compensated && hasWeightedFactors && isCheckCertHistory()) {

            IdentityHistoryItem historyItem = _historyService.getLastRoleDecision(_identity.getId(), role.getName());
            CertificationAction action = historyItem != null ? historyItem.getAction() : null;

            if (action != null) {
                switch (action.getStatus()) {

                case Approved: {
                    factor = _certifiedFactor;
                }
                break;

                case Mitigated: {
                    Date exp = action.getMitigationExpiration();
                    if (exp == null) {
                        // assume it never expires?
                        factor = _mitigatedFactor;
                    }
                    else if (_now.compareTo(exp) >= 0) {
                        // the mitigation expired
                        factor = _expiredFactor;
                    }
                    else {
                        factor = _mitigatedFactor;
                    }
                }
                break;

                case Remediated: {
                    factor = _remediatedFactor;
                }
                break;

                default: {
                    // shouldn't see Delegated in a completed
                    // certification, assume uncertified
                }
                break;
                }
            }
        }
                
        float activityMonitoringFactor;
        if (_compensated) {
            activityMonitoringFactor = (float)getActivityMonitoringFactorForJF(_context, role, _apps, _config, _definition);
                    
            // Double check on the identity's applications because activity monitoring may be enabled at that level instead.
            if (activityMonitoringFactor == 1.0f) {
                // Use a nonsense factor to detect changes
                activityMonitoringFactor = -1.0f;
                ActivityConfig idConfig = _identity.getActivityConfig();
                        
                if (idConfig != null) {
                    Set<String> enabledAppsForId = idConfig.getEnabledApplications();
                            
                    // jsl - woah this looks expensive
                    for (String appId : enabledAppsForId) {
                        Application app = _context.getObjectById(Application.class, appId);
                        List<Bundle> businessRolesForId = _identity.getBundles(app);
                        if (businessRolesForId.contains(role)) {
                            ApplicationConfig appConfig = _config.getApplicationConfig(app, _definition);
                            // geez, can't this just be cached as a float?
                            Float amFactor = Float.parseFloat(appConfig.getActivityMonitoringFactor());
                            if (amFactor > activityMonitoringFactor) {
                                activityMonitoringFactor = amFactor;
                            }
                        }
                    }
                }
                        
                // If it's unchanged set it to 1
                if (activityMonitoringFactor == -1.0f) {
                    activityMonitoringFactor = 1.0f;
                }
            }
        } else {
            activityMonitoringFactor = 1.0f;
        }

        float bundleScore = (float)weight * factor * activityMonitoringFactor;
        _score += bundleScore;

        if (_compensated) {
            ScoreItem item = new ScoreItem(_definition);
            item.setTargetMessage(new Message(role.getName()));
            item.setScore((int)bundleScore);
            _items.add(item);
        }
    }

    /**
     * Return whether or not certification history should be checked when
     * compensating.  This returns false if the user has no history for this
     * item type.
     */
    private boolean isCheckCertHistory() throws GeneralException {

        if (null == _checkCertHistory) {
            IdentityHistoryService svc = new IdentityHistoryService(_context);
            int numDecisions = svc.countRoleDecisions(_identity.getId());
            _checkCertHistory = (numDecisions > 0);
        }
        return _checkCertHistory;
    }
    
    public ScoreItem isMatch(SailPointObject obj) {
        return null;
    }


}

