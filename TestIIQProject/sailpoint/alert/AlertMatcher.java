/*
 *  (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.alert;

import sailpoint.api.SailPointContext;
import sailpoint.object.Alert;
import sailpoint.object.AlertDefinition;
import sailpoint.object.AlertMatchConfig;
import sailpoint.object.Rule;
import sailpoint.tools.GeneralException;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by ryan.pickens on 6/22/16.
 */
public class AlertMatcher {

    SailPointContext _context;

    Map<String, Object> _arguments;

    static final String ARG_ALERT = "alert";

    public AlertMatcher(SailPointContext ctx, Map<String, Object> args) {
        _context = ctx;
        _arguments = args;
    }

    /**
     * Set the arguments to use for for script and rule evaluation.
     */
    public void setArguments(Map<String,Object> args) {
        _arguments = args;

    }

    /**
     * Set an argument to use for rule evaluation.
     */
    public void setArgument(String name, Object value) {
        if (_arguments == null)
            _arguments = new HashMap<String,Object>();
        _arguments.put(name, value);
    }


    public boolean isMatch(Alert a, AlertDefinition def) throws GeneralException {
        boolean match = true;

        AlertMatchConfig cfg = def.getMatchConfig();

        if (cfg == null) {
            //Nothing to match, return false;
        } else {
            //TODO: Do we AND/OR all configs, or just use the first we find?
            //For now we will evalute ALL, and && together
            //Evaluate MatchExpression first
            if (cfg.getMatchExpression() != null) {
                match &= cfg.getMatchExpression().match(a);
            }
            //Rule Second
            if (match && cfg.getMatchRule() != null) {
                Rule rule = cfg.getMatchRule();
                setArgument(ARG_ALERT, a);
                Object result = _context.runRule(rule, _arguments);
                match &= isTruthy(result);

            }

        }

        return match;
    }


    /**
     * truthy is defined as any value that is not one of the following:
     *
     *      null
     *      "false"
     *      Boolean(false)
     *
     */
    private boolean isTruthy(Object result) {

        boolean truthy = false;

        if (result instanceof Boolean)
            truthy = ((Boolean)result).booleanValue();

        else if (result instanceof String)
            truthy = !((String)result).equalsIgnoreCase("false");

        else if (result != null)
            truthy = true;

        return truthy;
    }


}
