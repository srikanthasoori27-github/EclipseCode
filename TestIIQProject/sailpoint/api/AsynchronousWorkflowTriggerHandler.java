package sailpoint.api;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import sailpoint.object.IdentityChangeEvent;
import sailpoint.object.IdentityTrigger;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.RapidSetupConfigUtils;
import sailpoint.tools.GeneralException;

import java.util.HashMap;
import java.util.Map;

public class AsynchronousWorkflowTriggerHandler extends WorkflowTriggerHandler {

    private static final Log log = LogFactory.getLog(AsynchronousWorkflowTriggerHandler.class);

    /**
     * The name of the Workflow we're supposed to launch.
     * This may be a name, or it may be a key in the System Configuration
     * object.
     */
    public static final String ARG_WORKFLOW = "workflow";

    @Override
    protected void handleEventInternal(IdentityChangeEvent event,
                                       IdentityTrigger trigger)
            throws GeneralException {
        AsynchronousRapidSetupWorkflowLauncher launcher = new AsynchronousRapidSetupWorkflowLauncher();

        String matchProcess = trigger.getMatchProcess();
        String workflowName = RapidSetupConfigUtils.getBusinessProcessWorkflowName(matchProcess);

        String identityName = event.getIdentityName();

        if (trigger != null && event != null && context != null) {
            log.debug("Start Asynchronous Workflow Request Executor");
            log.debug("Start Asynchronous Workflow Request Executor trigger name.." + trigger.getName());
            log.debug("Start Asynchronous Workflow Request Executor event cause.." + event.getCause());

            // Workflow launchArguments
            Map launchArgsMap = new HashMap();
            launchArgsMap.put("identityName", identityName);
            launchArgsMap.put("trigger", trigger);
            launchArgsMap.put("event", event);
            launchArgsMap.put("plan", new ProvisioningPlan());

            long current = System.currentTimeMillis();
            String requestName = trigger.getName() + " FOR " + identityName + " " + current;
            launcher.launchWorkflow(context, workflowName, requestName, launchArgsMap);

            log.debug("End Asynchronous Workflow Request Executor");
        }
    }
}
