/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * Scorer implementation that calculates a score based on an attribute of a 
 * Link.  This is the implementation behind several application scores.
 * 
 * Author: Jeff
 *
 * This is a relatively general scoring algorithm that can be configured
 * several times to calculate scores for different attribute/value
 * combinations.
 * 
 * The arguments from the ScoreDefinition are:
 *
 *   attribute
 *     - name of the link attribute to ponder
 *
 *   value
 *     - value the attribute must have for the link to 
 *       be included in this score
 *
 *   expression
 *     - Beanshell fragment that evaluates to true
 *        for the link to be included in the score
 *
 *   sensitivity
 *     - multiplied by the percentage of matching links
 *       to arrive at the score, if not set the default is 1
 */

package sailpoint.score;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.SailPointContext;
import sailpoint.object.GenericIndex;
import sailpoint.object.Link;
import sailpoint.object.SailPointObject;
import sailpoint.object.ScoreConfig;
import sailpoint.object.ScoreDefinition;
import sailpoint.object.ScoreItem;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.tools.Message;
import sailpoint.web.messages.MessageKeys;

public class LinkAttributeScorer extends AbstractScorer {

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

	private static Log log = LogFactory.getLog(LinkAttributeScorer.class);

    public static final String ARG_ATTRIBUTE = "attribute";
    public static final String ARG_VALUE = "value";
    public static final String ARG_TARGET = "target";
    public static final String ARG_SUGGESTION = "suggestion";
    public static final String ARG_EXPRESSION = "expression";
    public static final String ARG_SENSITIVITY = "sensitivity";

    SailPointContext _context;

    /**
     * Attribute we're looking at.
     */
    String _attribute;

    /**
     * Value we're looking for.
     */
    String _value;
    
    /**
     * The actual reason behind the "match"
     * Example: Dormant Account
     */
    String _target;
    
    /** A suggestion of what to do about the target **/
    String _suggestion;

    // TODO: Support complex expressions

    /**
     * Score sensitivity.
     */
    float _sensitivity;

    //
    // Aggregation state
    //

    /**
     * Total number of account links we processed.
     */
    int _total;

    /**
     * Number of account links that matched the selection criteria.
     */
    int _matches;

    //////////////////////////////////////////////////////////////////////
    //
    // Scorer Methods
    //
    //////////////////////////////////////////////////////////////////////

    public LinkAttributeScorer() {
    }

    /**
     * Extract the score value maintained by this Scorer from
     * the Scorecard.  This is used in the calculation of
     * composite scores from an partially refreshed scorecard.
     */
    public int getScore(ScoreDefinition def, GenericIndex index) {
        int score = 0;
        String name = def.getName();
        if (name != null)
            score = index.getScore(name);
        return score;
    }

    /**
     * Reset the internal state of aggregate scorers.
     */
    public void prepare(SailPointContext context, 
                        ScoreConfig config,
                        ScoreDefinition def,
                        GenericIndex index)
        throws GeneralException {

        _total = 0;
        _matches = 0;

        _attribute = def.getString(ARG_ATTRIBUTE);
        _value = def.getString(ARG_VALUE);
        _target = def.getString(ARG_TARGET);
        _suggestion = def.getString(ARG_SUGGESTION);
        //_expression = def.getString(ARG_EXPRESSION);
        _sensitivity = def.getFloat(ARG_SENSITIVITY);

        if (def.getName() == null)
            log.warn("No score name configured for score: " + def.getId());

        // this can't be zero
        if (_sensitivity == 0.0f) _sensitivity = 1.0f;

        if (_attribute == null && isAttributeRequired())
            log.warn("No attribute configured for score: " + def.getName());
        _context = context;
        // this might be ok, just looking for attributes with a null value?
        //if (_value == null)
        //log.warn("No value configured for score: " + def.getName());
    }

    /**
     * Return true if a link attribute name is required for this scorer.
     * Overloaded by subclases such as RiskyAccountScorer that
     * don't base their decisions on link attributes.
     */
    public boolean isAttributeRequired() {
        return true;
    }

    /**
     * Update the aggregate score state from a link.
     */
    public void score(SailPointContext context,
                      ScoreConfig config,
                      ScoreDefinition def,
                      SailPointObject src,
                      GenericIndex index)
        throws GeneralException {

        // save this for the isMatch method in subclasses
        _context = context;

        if (!(src instanceof Link))
            throw new GeneralException("Invalid score source object");

        Link link = (Link)src;
        _total++;

        if (isMatch(link)!=null) {
            _matches++;
            if (log.isInfoEnabled()) {
                log.info(def.getName() + " matches " + 
                         link.getNativeIdentity());
            }
        }
    }

    /**
     * Test to see if a link matches our criteria.
     * This may be overloaded in a subclass that has
     * a more complex way to determine matches.
     */
    public ScoreItem isMatch(SailPointObject obj) throws GeneralException {
        
        Link link = (Link)obj;
        
        ScoreItem item = null;

        if (_attribute != null) {
            boolean match = false;

            // The link attribute may be String, Boolean, Integer, or
            // something else but all we support in the score config
            // model is String.  So we have to coerce before comparing.
            Object ovalue = link.getAttribute(_attribute);
            String value = (ovalue != null) ? ovalue.toString() : null;

            // TODO: Should we consider null and empty strings equal?
            if (value == null)
                match = _value == null;

            else if (_value != null)
                match = _value.equals(value);
            
            /** 
             * If this is a match, we return a score item with the target
             * set to the reason why this was a match 
             */
            if (match) {
                item = new ScoreItem();
                item.setTargetMessage(new Message(_target));
                if (_suggestion != null)
                    item.setSuggestionMessage(new Message(_suggestion));
            }
        }

        return item;
    }

    /**
     * Convert the accumulated state into a score.
     */
    public void finish(SailPointContext context, 
                       ScoreConfig config,
                       ScoreDefinition def,
                       GenericIndex index)
        throws GeneralException {

        // calculate the percentage of matches
        float percentage = (float)_matches / (float)_total;

        // convert this to a percentage of the score range
        int max = getMaximumScore(config);
        float score = (float)max * percentage;

        // factor in the sensitivity
        score *= _sensitivity;

        // round up and constrain
        int intScore = Math.round(score);
        int constrainedScore = constrainScore(config, intScore);
        
        // save it in the index
        String name = def.getName();
        if (name != null)
            index.setScore(name, constrainedScore);

        if (constrainedScore > 0) {
            // there is only one "item" for this score type
            ScoreItem item = new ScoreItem(def);
            item.setScore(constrainedScore);
            item.setScorePercentage(100);

            item.setTargetMessage(new Message(MessageKeys.LINK_ATTR_SCORER_MATCH_COUNT,
                    Util.itoa(_matches), Util.itoa(_total)));
            if (_suggestion != null)
                item.setSuggestionMessage(new Message(_suggestion));

            // let the subclass mutate it if it wants
            annotateScoreItem(def, item);

            index.addItem(item);
        }
    }

    public void annotateScoreItem(ScoreDefinition def, ScoreItem item) {
    }

}
