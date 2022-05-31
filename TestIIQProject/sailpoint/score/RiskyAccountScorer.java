/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * A specialization of the LinkAttributeScorer that matches
 * the link based on the risk score of the owning identity.
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
import sailpoint.object.SailPointObject;
import sailpoint.object.ScoreConfig;
import sailpoint.object.ScoreDefinition;
import sailpoint.object.ScoreItem;
import sailpoint.object.Scorecard;
import sailpoint.object.QueryOptions;
import sailpoint.object.Filter;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;

import java.util.Arrays;
import java.util.Iterator;

public class RiskyAccountScorer extends LinkAttributeScorer {

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    // not we share the same logging control
	private static Log log = LogFactory.getLog(RiskyAccountScorer.class);

    public static final String ARG_THRESHOLD = "threshold";
    public static final String TARGET = "High Risk Account";

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

    public RiskyAccountScorer() {
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
     */
    @Override
    public ScoreItem isMatch(SailPointObject obj) throws GeneralException{

        Link link = (Link)obj;
        ScoreItem item = null;
        int score = 0;
        Identity ident = link.getIdentity();
        if (ident != null) {
            QueryOptions ops = new QueryOptions();
            ops.add(Filter.eq("identity", ident));
            //MEH 16548 get only the last created scorecard
            ops.addOrdering("created", false);
            ops.setResultLimit(1);
            Iterator<Object[]> results = _context.search(Scorecard.class, ops, Arrays.asList("compositeScore"));
            if(results != null && results.hasNext()){
                score = (Integer)results.next()[0];
                //MEH 16548 exhaust the iterator (should only be 1 result)
                while(results.hasNext()){
                	results.next();
                }
            }
        }

        if(score >= _threshold) {
            item = new ScoreItem();
            item.setTargetMessage(new Message(_target));
            if (_suggestion != null)
                item.setSuggestionMessage(new Message(_suggestion));
        }

        return item;
    }

}
