/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * A specialization of the LinkAttributeScorer that matches
 * the link based on the number of policy violations
 * held by the owning Identity.
 *
 * Author: Jeff
 *
 */

package sailpoint.score;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.SailPointContext;
import sailpoint.object.GenericIndex;
import sailpoint.object.Identity;
import sailpoint.object.Link;
import sailpoint.object.PolicyViolation;
import sailpoint.object.SailPointObject;
import sailpoint.object.ScoreConfig;
import sailpoint.object.ScoreDefinition;
import sailpoint.object.ScoreItem;
import sailpoint.object.QueryOptions;
import sailpoint.object.Filter;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;

public class ViolatorAccountScorer extends LinkAttributeScorer {

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    // not we share the same logging control
	private static Log log = LogFactory.getLog(ViolatorAccountScorer.class);

    public static final String ARG_THRESHOLD = "threshold";
    public static final String TARGET = "Policy Violation";

    /**
     * The composite score threshold, above which an identity
     * is considered risky enough to contribute to the application score.
     */
    int _threshold;

    //////////////////////////////////////////////////////////////////////
    //
    // Scorer Methods
    //
    //////////////////////////////////////////////////////////////////////

    public ViolatorAccountScorer() {
    }

    /**
     * Overload this to tell LinkAttributeScorer that we
     * don't need a link attribute in the argument map.
     */
    public boolean isAttributeRequired() {
        return false;
    }

    /**
     * Reset the internal state of aggregate scorers.
     */
    public void prepare(SailPointContext context,
                        ScoreConfig config,
                        ScoreDefinition def,
                        GenericIndex index)
        throws GeneralException {

        super.prepare(context, config, def, index);

        _threshold = def.getInt(ARG_THRESHOLD);
    }

    /**
     * Overload the matching rule.
     * It isn't clear what we should do here,
     * simply counting the violations doesn't seem like
     * enough, we should be factoring in the weight of the
     * violations as well?
     */
    @Override
    public ScoreItem isMatch(SailPointObject obj)
        throws GeneralException {

        Link link = (Link)obj;
        ScoreItem item = null;
        boolean match = false;
        int score = 0;
        Identity ident = link.getIdentity();
        if (ident != null) {
            QueryOptions qo = new QueryOptions();
            qo.add(Filter.eq("identity", ident));
            qo.add(Filter.eq("active", true));
            // TODO: Figure out a way to factor in the
            // weight of each violation
            score = _context.countObjects(PolicyViolation.class, qo);
        }

        // The other thresholds fire if the score is equal
        // to the threshold so we shold be consistent here.
        // Have to be careful with zero though, zero must
        // mean the same as 1
        match = (_threshold > 0) ? (score >= _threshold) : (score > _threshold);
        if(match) {
            item = new ScoreItem();
            item.setTargetMessage(new Message(_target));
            if (_suggestion != null)
                item.setSuggestionMessage(new Message(_suggestion));
        }
        return item;
    }

}
