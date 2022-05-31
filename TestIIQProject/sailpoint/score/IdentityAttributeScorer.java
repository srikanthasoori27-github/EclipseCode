/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * Scorer implementation that calculates a score based on an attribute 
 * of an Identity.  Unlike LinkAttributeScorer, this is not
 * an "aggregate scorer", it will calculate a score from a single
 * identity.
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
 *     - name of the identity attribute to ponder
 *
 *   value
 *     - value the attribute must have for the identity 
 *       have this score
 *
 *   expression
 *     - Beanshell fragment that evaluates to true
 *        for the link to be included in the score
 *
 *   score
 *     - the resulting score if the attribute/value 
 *       matches or the expression returns true
 *
 *   rule
 *     - the name of a rule to call to perform the computation
 *       and return the score
 *   
 */

package sailpoint.score;

import java.util.Map;
import java.util.HashMap;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.SailPointContext;
import sailpoint.object.GenericIndex;
import sailpoint.object.Identity;
import sailpoint.object.Rule;
import sailpoint.object.SailPointObject;
import sailpoint.object.ScoreConfig;
import sailpoint.object.ScoreDefinition;
import sailpoint.object.ScoreItem;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.tools.Message;
import sailpoint.web.messages.MessageKeys;

public class IdentityAttributeScorer extends AbstractScorer {

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

	private static Log log = LogFactory.getLog(LinkAttributeScorer.class);

    public static final String ARG_ATTRIBUTE = "attribute";
    public static final String ARG_VALUE = "value";
    public static final String ARG_EXPRESSION = "expression";
    public static final String ARG_SCORE = "score";
    public static final String ARG_RULE = "rule";

    /**
     * Attribute we're looking at.
     */
    String _attribute;

    /**
     * Value we're looking for.
     */
    String _value;

    /**
     * The score if _attribute equals _value.
     */
    int _score;

    /**
     * A complex expression to calculate the score, 
     * an alternative to using attribute/value or a rule.
     */
    String _expression;

    /**
     * A persistent rule that will calculate the score. 
     */
    Rule _rule;

    /**
     * Set to true when we've finished caching our configuration.
     */
    boolean _initialized;

    //////////////////////////////////////////////////////////////////////
    //
    // Scorer Methods
    //
    //////////////////////////////////////////////////////////////////////

    public IdentityAttributeScorer() {
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
     * Cache configuration.  Since we are not an aggregate
     * score, the prepare() method is not necessarily called
     * so we have to boostrap our own cache.
     */
    public void init(SailPointContext context, ScoreDefinition def)
        throws GeneralException {

        if (!_initialized) {

            if (def.getName() == null)
                log.error("No score name configured for score!");

            _attribute = def.getString(ARG_ATTRIBUTE);
            _value = def.getString(ARG_VALUE);
            //_expression = def.getString(ARG_EXPRESSION);
            _score = def.getInt(ARG_SCORE);

            String rule = def.getString(ARG_RULE);
            if (rule != null) {
                _rule = context.getObjectByName(Rule.class, rule);
                if (_rule == null) 
                    log.error("Invalid rule for score: " + def.getName());
            }

            if (_attribute == null && _rule == null)
                log.warn("No attribute configured for score: " + def.getName());

            _initialized = true;
        }

    }

    public void score(SailPointContext context,
                      ScoreConfig config,
                      ScoreDefinition def,
                      SailPointObject src,
                      GenericIndex index)
        throws GeneralException {

        if (!(src instanceof Identity))
            throw new GeneralException("IdentityAttributeScorer called with non-Identity");

        Identity identity = (Identity)src;
        int score = 0;

        init(context, def);

        if (_rule != null) {
            Map<String,Object> args = new HashMap<String,Object>();
            args.put("identity", identity);
            Object result = context.runRule(_rule, args);
            score = Util.otoi(result);
        }
        else if (_attribute != null) {
            boolean match = false;
            Object value = identity.getAttribute(_attribute);

            // TODO: Should we consider null and empty strings equal?
            if (value == null)
                match = _value == null;

            else if (_value != null) {
                // coerce what we have to a string and compare case insensitive
                // case insensitivity is convenient for things like "true" and "TRUE"
                // which are common, and is consistent with searching which
                // is always case insensitive, could have a ScoreDefinition
                // argument to control this if it's an issue
                match = (_value.compareToIgnoreCase(value.toString()) == 0);
            }

            if (match)
                score = _score;
        }

        int constrainedScore = constrainScore(config, score);

        String scoreName = def.getName();
        if (scoreName != null) 
            index.setScore(scoreName, constrainedScore);

        // add a single ScoreItem for the summary but only 
        // if we're a component of the composite score
        if (def.isComponent()) {
            ScoreItem item = new ScoreItem(def);
            item.setScore(constrainedScore);
            item.setScorePercentage(100);
            
            if (_rule != null)
                item.setTargetMessage(new Message(MessageKeys.IDENT_ATTR_SCORER_TARGET_RULE,
                        _rule.getName()));
            else if (_attribute != null)
                item.setTargetMessage(new Message(MessageKeys.IDENT_ATTR_SCORER_TARGET_ATTR_VALUE,
                        _attribute, _value));

            index.addItem(item);
        }
    }

    public ScoreItem isMatch(SailPointObject obj) {
        return null;
    }
}
