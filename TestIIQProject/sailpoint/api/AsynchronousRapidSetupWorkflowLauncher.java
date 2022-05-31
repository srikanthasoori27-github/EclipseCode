/*
 * (c) Copyright 2020 SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.api;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.Attributes;
import sailpoint.object.Identity;
import sailpoint.object.IdentityChangeEvent;
import sailpoint.object.IdentityTrigger;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.RapidSetupConfigUtils;
import sailpoint.object.Request;
import sailpoint.object.RequestDefinition;
import sailpoint.object.Workflow;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

public class AsynchronousRapidSetupWorkflowLauncher {
    private static final Log log = LogFactory.getLog(AsynchronousRapidSetupWorkflowLauncher.class);

    /**
     * The name of the Workflow we're supposed to launch.
     * This may be a name, or it may be a key in the System Configuration
     * object.
     */
    public static final String ARG_WORKFLOW = "workflow";

    public void launchWorkflow(SailPointContext context, String workflowName,
                               String requestName, Map launchArgsMap) throws GeneralException {

        Workflow workflow = context.getObjectByName(Workflow.class, workflowName);

        // bug#16874 catch configuration errors and throw a meaningful exception
        if (workflow == null) {
            throw new GeneralException("Unknown workflow: " + workflowName);
        }

        log.debug("Start Asynchronous RapidSetup Workflow Launcher");
        log.debug("Start Asynchronous RapidSetup Workflow Launcher wfname.." + workflow.getName());
        log.debug("Start Asynchronous RapidSetup Workflow Launcher context.." + context);

        launchArgsMap.put("plan", new ProvisioningPlan());

        // Use the Request Launcher
        Request req = new Request();
        RequestDefinition reqdef = context.getObjectByName(RequestDefinition.class, "Workflow Request");
        req.setDefinition(reqdef);
        Attributes allArgs = new Attributes();
        allArgs.put("workflow", workflow.getName());

        long current = System.currentTimeMillis();
        allArgs.put("requestName", requestName);
        allArgs.putAll(launchArgsMap);
        req.setEventDate(new Date(current));
        String launcherName;
        if (Util.otob(launchArgsMap.get("isIdentityOperation"))) {
            launcherName = context.getUserName();
        } else {
            launcherName = RapidSetupConfigUtils.getString("workflow,requester");
        }
        if (Util.isNullOrEmpty(launcherName)) {
            launcherName = "spadmin";
        }
        allArgs.put("launcher", launcherName);
        Identity owner = context.getObjectByName(Identity.class, launcherName);
        req.setOwner(owner);
        req.setName(requestName);
        req.setAttributes(reqdef, allArgs);

        // Launch the work flow via the request manager.
        RequestManager.addRequest(context, req);
        if (reqdef != null && context != null) {
            context.decache(reqdef);
        }
        if (owner != null && context != null) {
            context.decache(owner);
        }
        log.debug("End Asynchronous Workflow Request Executor");
    }
}
