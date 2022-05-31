/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
/*
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */

package sailpoint.object;

import java.util.List;
import java.util.Map;

import sailpoint.object.Rule;
import sailpoint.object.Script;
import sailpoint.tools.GeneralException;


/**
 * Interface of an object that is able to execute rules and scripts.
 */
public interface RuleRunner
{
    /**
     * Run a rule and return the result or null if there is no result.
     * 
     * @param  rule    The Rule to run.
     * @param  params  A name/value map of parameters to pass into the rule.
     * 
     * @return The result of the execution of the Rule, or null if there is no
     *         result.
     */
    public Object runRule(Rule rule, Map<String,Object> params) throws GeneralException;

    /**
     * Run a rule and return the result or null if there is no result.
     * 
     * @param  rule    The Rule to run.
     * @param  params  A name/value map of parameters to pass into the rule.
     * @param  libraries List of Rule libraries that should be evaluated with the rule
     * 
     * @return The result of the execution of the Rule, or null if there is no
     *         result.
     */
    public Object runRule(Rule rule, Map<String,Object> params, List<Rule> libraries) 
        throws GeneralException;

    /**
     * Run a script and return the result.
     */
    public Object runScript(Script script, Map<String,Object> params)
        throws GeneralException;

    /**
     * Run a script and return the result, also include any passed in libraries
     * into the script's runtime context.
     */
    public Object runScript(Script script, Map<String,Object> params, List<Rule> libraries) 
        throws GeneralException;
}
