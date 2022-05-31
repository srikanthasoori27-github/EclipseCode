/*
 * (c) Copyright 2013 SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.task;

import java.util.*;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.SailPointContext;
import sailpoint.api.SailPointFactory;
import sailpoint.object.Attributes;
import sailpoint.object.Rule;
import sailpoint.object.TaskResult;
import sailpoint.object.TaskSchedule;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.RFC4180LineParser;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;

/**
 * An executor which runs an arbitrary rule. A task def should provide a ruleName
 * and a ruleConfig, which is a map of key value pairs which will be pass as an arguments
 * to the rule.
 */
public class RuleExecutor extends AbstractTaskExecutor {

    private static Log log = LogFactory.getLog(RuleExecutor.class);

    private static final String ARG_RULE_NAME = "ruleName";
    private static final String ARG_RULE_CONFIG = "ruleConfig";
    private static final String ARG_LAUNCHER = "launcher";
    private static final String ARG_TIMEZONE = "timezone";
    private static final String ARG_LOCALE = "locale";

    private static Set<String> RESERVED_ARG_KEYS = new HashSet<String>(Arrays.asList(
            ARG_RULE_NAME,
            ARG_RULE_CONFIG,
            ARG_LAUNCHER,
            ARG_TIMEZONE,
            ARG_LOCALE
    ));

    private SailPointContext context;

    /*
     * Termination flag
     */
    boolean _terminate;

    /*
     * Holds all the errors
     */
    List<Message> errors = new ArrayList<Message>();

    /*
     * Holds all the warnings
     */
    List<Message> warnings = new ArrayList<Message>();

    public void execute(SailPointContext context, TaskSchedule schedule,
                        TaskResult result, Attributes<String, Object> args)
            throws Exception {

        log.debug("Running RuleExecutor Task...");

        this.context = context;

		/*
		 * Create a new TaskMonitor for the GUI.
		 */
        setMonitor(new TaskMonitor(context, result));

		/*
		 * Try to get the Context.
		 */
        try {

            log.debug("Getting Current Context.");
            context = SailPointFactory.getCurrentContext();

        } catch (GeneralException e) {

            throw new Exception(e);
        }

		/*
		 * Get the name of the rule from the TaskDefinition arguments.
		 */
        String ruleName = args.getString(ARG_RULE_NAME);
        log.debug(ARG_RULE_NAME + " [" + ruleName + "]");

        if (Util.isNullOrEmpty(ruleName)) {
            Message msg = new Message(Message.Type.Error,
                    MessageKeys.TASK_RUN_RULE_MISSING_RULE);
            throw new GeneralException(msg);
        }

		/*
		 * Get the configuration map from the TaskDefinition arguments.
		 */
        Map<String, Object> configMap = getRuleConfigMap(args);
        log.debug(ARG_RULE_CONFIG + " [" + configMap.toString() + "]");

		/*
		 * This is the input for the rule execution.
		 */
        Map<String, Object> inputMap = new HashMap<String, Object>();

        try {

            inputMap.put("context", this.context);
            inputMap.put("log", log);
            inputMap.put("config", configMap);
            inputMap.put("taskResult", result);
            inputMap.put("taskExecutor", this);

            Object status = runRule(ruleName, inputMap);

            if (status != null) {
                // the original name for this, should be using a catalog
                // key if we're that concerned about formatting
                result.setAttribute("Result: ", status);
            }
            
        }
        catch (GeneralException e) {

            log.debug("Exception: " + e);
            throw new Exception(e);
        }

        log.debug("Process complete.");
        getMonitor().completed();

    }

    public boolean terminate() {
        //The TaskResult's terminateRequested transient field
        //is used to pass the info to the Rule (if the Rule complies)
        //See sailpoint.api.TaskManager.terminate().
        return true;
    }

    /*
     * Runs the rule with the specified name and context.
     * @param ruleName the rule name
     * @param ruleContext the rule context
     * @return rule result
     * @throws Exception
     */
    private Object runRule(String ruleName, Map<String, Object> ruleContext)
            throws Exception {

        log.debug("Entering runRule.");

        Object status = null;

        log.debug("ruleName[" + ruleName + "]");
        log.debug("ruleContext[" + ruleContext + "]");

        try {

            if (null == ruleContext.get("context")) {
                ruleContext.put("context", this.context);
            }
            if (null == ruleContext.get("log")) {
                ruleContext.put("log", log);
            }

            Rule rule = context.getObjectByName(Rule.class, ruleName);

            status = context.runRule(rule, ruleContext);

        }
        catch (Exception e) {

            log.debug("Exception: " + e);
            throw new GeneralException(e);

        }

        return status;
    }

    /*
     * Gets ARG_RULE_CONFIG from the argument map and returns it as a map. If the
     * original rule config was a string, attempts to parse it as CSV of the form
     * "key1,value1,key2,value2,..." and build a map from the pairs.
     *
     * @param args the executor arguments
     * @return the ruleConfig in map form
     * @throws GeneralException If the rule config was not a map or a parseable String
     */
    private Map<String, Object> getRuleConfigMap(Attributes<String, Object> args) throws GeneralException
    {
        // launcher, timezone, locale
        Object ruleConfigObj = args.get(ARG_RULE_CONFIG);
        Map<String, Object> ruleConfigMap = new HashMap<String, Object>();

        if (ruleConfigObj instanceof String) {
            // We need to parse the string and build a map.
            RFC4180LineParser parser = new RFC4180LineParser(',');
            List<String> parsedLine = parser.parseLine((String)ruleConfigObj);

            // If the parse line doesn't have an even number of terms it's malformed.
            if (parsedLine.size() % 2 != 0)
            {
                Message msg = new Message(Message.Type.Error,
                        MessageKeys.TASK_RUN_RULE_RULECONFIG_PAIRS_ERR, ruleConfigObj);
                throw new GeneralException(msg);
            }
            Iterator<String> iter = parsedLine.iterator();
            while (iter.hasNext())
            {
                ruleConfigMap.put(iter.next(), iter.next());
            }
        }
        else if (ruleConfigObj instanceof Map) {
            // It's already a map which likely came from a custom Task Def,
            // so just use it as-is.
            ruleConfigMap = (Map<String, Object>)ruleConfigObj;
        }
        else {
            // jsl - changed this to not warn if it is null, not all rules need args
            if (ruleConfigObj != null) {
                log.warn("ruleConfig " + ruleConfigObj + " was not Map or parseable String, ignoring it");
            }
        }

        // In addition to whatever rule arguments we got from above, we will also add any additional arguments
        // which we assume came from the Edit Task pane of a custom task def. We'll ignore the reserved internal keys.
        for (String key : args.keySet()) {
            if (!RESERVED_ARG_KEYS.contains(key))
            {
                ruleConfigMap.put(key, args.get(key));
            }
        }

        return ruleConfigMap;
    }
}
