/* (c) Copyright 2014 SailPoint Technologies, Inc., All Rights Reserved. */
/*
 * A request executor that calls a rule.
 * Developed for testing, but may have other uses.
 * 
 * Author: Jeff
 *
 */

package sailpoint.request;


import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.Map;

import sailpoint.api.SailPointContext;
import sailpoint.object.Attributes;
import sailpoint.object.Request;
import sailpoint.object.RequestDefinition;
import sailpoint.object.Rule;

public class RuleRequestExecutor extends AbstractRequestExecutor {

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

	private static Log log = LogFactory.getLog(RuleRequestExecutor.class);

    public static final String DEFINITION_NAME = "Rule Request";

    /**
     * The name of the rule to run.
     * All arguments are passed through to the rule so make our
     * control arguments have a prefix to avoid conflict.
     */
    public static final String ARG_RULE = "RuleRequestExecutor.rule";

    /**
     * Argument passed to the rule holding a reference back to the Request.
     */
    public static final String RULE_ARG_REQUEST = "request";

    //////////////////////////////////////////////////////////////////////
    //
    // Execution
    //
    //////////////////////////////////////////////////////////////////////

    @SuppressWarnings("unchecked")
    public void execute(SailPointContext context, Request request,
                        Attributes<String, Object> args)
        throws RequestPermanentException, RequestTemporaryException {

        try {
            String ruleName = request.getString(ARG_RULE);

            if (ruleName == null) {
                // for some tests it is nice to pass the rule name
                // in through the RequestDefinition to make scheduling
                // the request simpler
                ruleName = request.getDefinition().getString(ARG_RULE);
            }
            
            if (ruleName == null) {
                log.error("No rule name passed");
            }
            else {
                Rule rule = context.getObjectByName(Rule.class, ruleName);
                if (rule == null) {
                    log.error("Invalid rule name: " + ruleName);
                }
                else {
                    if (log.isInfoEnabled()) {
                        log.info("Running rule: " + ruleName);
                    }

                    Attributes<String,Object> ruleArgs = new Attributes(args);
                    ruleArgs.remove(ARG_RULE);
                    ruleArgs.put(RULE_ARG_REQUEST, request);

                    Object result = context.runRule(rule, ruleArgs);
                    
                    // do anything with the result?
                    if (log.isInfoEnabled()) {
                        log.info("Rule returned: " + result);
                    }
                }
            }
        }
        catch (Throwable t) {
            throw new RequestPermanentException(t);
        }
        
    }

    /**
     * Schedule a Request for this executor.  The rule name may be passed
     * in either the RequestDefinition or in the arguments map.   
     * This is just a convenience for testing.
     */
    static public void schedule(SailPointContext context, String defName, Map<String,Object> args) {

        try {
            RequestDefinition def = context.getObjectByName(RequestDefinition.class, defName);
            if (def == null) {
                log.error("Invalid RequestDefinition name: " + defName);
            }
            else {
                Request req = new Request(def);
                req.setAttributes(null, args);
                context.saveObject(req);
                context.commitTransaction();
            }
        }
        catch (Throwable t) {
            log.error("Unable to schedule request");
            log.error(t);
        }
    }
}
