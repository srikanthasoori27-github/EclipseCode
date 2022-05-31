/*
 * (c) Copyright 2020 SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.rapidsetup.plan;

import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.Localizer;
import sailpoint.api.RequestManager;
import sailpoint.api.SailPointContext;
import sailpoint.object.Attributes;
import sailpoint.object.Identity;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.RapidSetupConfigUtils;
import sailpoint.object.Request;
import sailpoint.object.RequestDefinition;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;

public class LeaverDeferredPlanExecutor {

    private static Log log = LogFactory.getLog(LeaverDeferredPlanExecutor.class);

    private static String DEFERRED_LEAVER_ARG_IDENTITY_NAME = "identityName";
    private static String DEFERRED_LEAVER_ARG_PLAN = "plan";
    private static String DEFERRED_LEAVER_ARG_FLOW = "flow";
    private static String DEFERRED_LEAVER_ARG_REQUEST_TYPE = "requestType";
    private static String DEFERRED_LEAVER_ARG_REASON_COMMENTS = "reasonComments";
    private static String DEFERRED_LEAVER_ARG_AUTOVERIFY_IDENTITY_REQUEST = "autoVerifyIdentityRequest";
    private static String DEFERRED_LEAVER_ARG_NO_FILTERING = "noFiltering";
    private static String DEFERRED_LEAVER_ARG_WORKFLOW = "workflow";
    private static String DEFERRED_LEAVER_ARG_TRACE = "trace";

    private static String DEFERRED_LEAVER_ARG_SOURCE = "source";
    private static String DEFERRED_LEAVER_ARG_APPROVAL_SCHEME = "approvalScheme";
    private static String DEFERRED_LEAVER_ARG_POLICY_SCHEME = "policyScheme";
    private static String DEFERRED_LEAVER_ARG_NOTIFICATION_SCHEME = "notificationScheme";
    private static String DEFERRED_LEAVER_ARG_FALLBACK_APPROVER = "fallbackApprover";
    private static String DEFERRED_LEAVER_ARG_FOREGROUND_PROVISIONING = "foregroundProvisioning";
    private static String DEFERRED_LEAVER_ARG_NO_APPLICATION_TEMPLATES = "noApplicationTemplates";
    private static String DEFERRED_LEAVER_CONFIG_WORKFLOW_REQUESTER = "workflow,requester";

    private LeaverAppConfigProvider appConfigProvider;
    private SailPointContext context = null;
    private Map<String, Object> args = null;

    public LeaverDeferredPlanExecutor(SailPointContext context, Map<String, Object> args,
                                      LeaverAppConfigProvider appConfigProvider) {
        this.appConfigProvider = appConfigProvider;
        this.context = context;
        this.args = args;
    }

    public void executePlanForApplication(String appName, ProvisioningPlan plan, int deferDays)
            throws GeneralException {
        log.debug("Creating plan for application: " + appName);
        long amountOfSeconds = TimeUnit.DAYS.toSeconds(deferDays);

        log.debug("Start Request Manager Provisioning");

        // Use the Request Launcher
        Request req = new Request();
        RequestDefinition reqdef = context.getObjectByName(RequestDefinition.class, "Workflow Request");
        req.setDefinition(reqdef);
        long startTime = System.currentTimeMillis();
        startTime += TimeUnit.SECONDS.toMillis(amountOfSeconds);

        Identity planIdentity = plan.getIdentity();
        Attributes allArgs = new Attributes();
        String requesterIdentityName = RapidSetupConfigUtils.getString(DEFERRED_LEAVER_CONFIG_WORKFLOW_REQUESTER);
        if(Util.isNullOrEmpty(requesterIdentityName)) {
            requesterIdentityName = "spadmin";
        }
        req.setLauncher(requesterIdentityName);
        String requestName = buildRequestName(planIdentity.getName(), deferDays, new Date(startTime));

        allArgs.put("requestName", requestName);
        log.debug("requestName.." + requestName);

        Map launchArgsMap = getWorkflowArguments(requesterIdentityName, args);
        launchArgsMap.put(DEFERRED_LEAVER_ARG_PLAN, plan);
        log.debug("launchArgsMap.." + launchArgsMap);
        allArgs.putAll(launchArgsMap);

        req.setEventDate(new Date(startTime));
        // Change the owner to the plan identity so that will show under
        // the user's Events tab of Identity Warehouse
        req.setOwner(planIdentity);
        req.setName(requestName);
        req.setAttributes(reqdef, allArgs);
        // Actually launch the work flow via the request manager.
        RequestManager.addRequest(context, req);
        if (reqdef != null) {
            context.decache(reqdef);
        }

    }

    private String buildRequestName(String targetIdentityName, int delayDays, Date startTime) {
        String requestName = "Deferred Leaver Request for " + targetIdentityName +
                " to launch " + Util.dateToString(startTime);
        return requestName;
    }

    private static Map getWorkflowArguments(String requesterIdentityName, Map<String, Object> args) {
        //Workflow launchArguments
        Map launchArgsMap = new HashMap();
        launchArgsMap.put("launcher", requesterIdentityName);
        launchArgsMap.put("sessionOwner", requesterIdentityName);
        launchArgsMap.put("noTriggers", "true");
        setValueFromArgs(args, DEFERRED_LEAVER_ARG_IDENTITY_NAME, launchArgsMap);
        setValueFromArgs(args, DEFERRED_LEAVER_ARG_FLOW, launchArgsMap);
        setValueFromArgs(args, DEFERRED_LEAVER_ARG_REQUEST_TYPE, launchArgsMap);
        setValueFromArgs(args, DEFERRED_LEAVER_ARG_REASON_COMMENTS, launchArgsMap);
        setValueFromArgs(args, DEFERRED_LEAVER_ARG_AUTOVERIFY_IDENTITY_REQUEST, launchArgsMap);
        setValueFromArgs(args, DEFERRED_LEAVER_ARG_WORKFLOW, launchArgsMap);
        setValueFromArgs(args, DEFERRED_LEAVER_ARG_NO_FILTERING, launchArgsMap);
        setValueFromArgs(args, DEFERRED_LEAVER_ARG_TRACE, launchArgsMap);
        setValueFromArgs(args, DEFERRED_LEAVER_ARG_SOURCE, launchArgsMap, "Workflow");
        setValueFromArgs(args, DEFERRED_LEAVER_ARG_APPROVAL_SCHEME, launchArgsMap);
        setValueFromArgs(args, DEFERRED_LEAVER_ARG_POLICY_SCHEME, launchArgsMap);
        setValueFromArgs(args, DEFERRED_LEAVER_ARG_NOTIFICATION_SCHEME, launchArgsMap);
        setValueFromArgs(args, DEFERRED_LEAVER_ARG_FALLBACK_APPROVER, launchArgsMap,"spadmin");
        setValueFromArgs(args, DEFERRED_LEAVER_ARG_FOREGROUND_PROVISIONING, launchArgsMap);
        setValueFromArgs(args, DEFERRED_LEAVER_ARG_NO_APPLICATION_TEMPLATES, launchArgsMap);
        return launchArgsMap;
    }

    private static void setValueFromArgs(Map<String, Object> args, String argName,
                                         Map launchArgsMap) {
        setValueFromArgs(args, argName, launchArgsMap, null);
    }

    private static void setValueFromArgs(Map<String, Object> args, String argName,
                                         Map launchArgsMap, Object defaultValue) {
        Object argValue = args.get(argName);
        if(argValue != null) {
            launchArgsMap.put(argName, argValue);
        } else if (defaultValue != null) {
            launchArgsMap.put(argName, defaultValue);
        }
    }
}
