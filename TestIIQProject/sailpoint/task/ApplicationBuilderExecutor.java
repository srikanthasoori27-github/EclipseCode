/* (c) Copyright 2018 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.task;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import sailpoint.api.SailPointContext;
import sailpoint.object.Attributes;
import sailpoint.object.Rule;
import sailpoint.object.TaskResult;
import sailpoint.object.TaskSchedule;

/**
 * An executor class which creates/ updates/ exports multiple IdentityIQ applications.
 * Uses 'Application Builder' rule to perform application manipulation.
 */
public class ApplicationBuilderExecutor extends AbstractTaskExecutor {

    private static Log log = LogFactory.getLog(ApplicationBuilderExecutor.class);

    /**
     * Processes input parameters and trigger the rule 'Application Builder'.
     * 
     * @param context SailPoint context object
     * @param schedule task schedule instance.
     * @param result TaskResult instance
     * @param args input attributes received from Task definition UI
     * 
     * @throws Exception
     *           Any exception thrown by the task will be caught and added to the TaskResult automatically.
     */
    public void execute(SailPointContext context, TaskSchedule schedule,
                        TaskResult result, Attributes<String, Object> args)
            throws Exception {
        if (log.isDebugEnabled()) {
            log.debug("Entering execute()...");
        }

        // Create a new TaskMonitor for the GUI.
        setMonitor(new TaskMonitor(context, result));

        // Prepare rule object for the Rule: ApplicationsBuilderRule
        Rule rule = context.getObjectByName(Rule.class, "Application Builder");

        Map<String, Object> inputMap = new HashMap<String, Object>();

        Map<String, Object> inputParams = initializeParams(args);

        // Rule input parameters
        inputMap.put("context", context);
        inputMap.put("log", log);
        inputMap.put("inputParams", inputParams);
        inputMap.put("taskResult", result);

        // Execute the rule for applications builder
        result = (TaskResult) context.runRule(rule, inputMap);
        if (log.isDebugEnabled()) {
            log.debug("Exiting execute()...");
        }

        getMonitor().completed();
    }

    public boolean terminate() {
        return true;
    }

    /**
     * Retrieve the task input attributes and validate it
     * @param args - Input arguments
     * */
    private Map<String, Object> initializeParams(Attributes<String, Object> args) {
        if (log.isDebugEnabled()) {
            log.debug("Entering initializeParams()...");
        }
        Map<String, Object> configMap = new HashMap<String, Object>();
        configMap.put("filePath", args.getString("filePath"));
        configMap.put("triggerAccountAggregation", args.getBoolean("triggerAccountAggregation"));
        configMap.put("triggerGroupAggregation", args.getBoolean("triggerGroupAggregation"));
        configMap.put("operation", args.getString("operation"));
        configMap.put("applicationType", args.getString("applicationType"));
        configMap.put("launcher", args.getString("launcher"));
        configMap.put("applicationsPerAggregation", args.getString("applicationsPerAggregation"));
        configMap.put("skipTestConnection", args.getBoolean("skipTestConnection"));

        if (log.isDebugEnabled()) {
            log.debug("Exiting initializeParams()...");
        }

        return configMap;

    }

}
