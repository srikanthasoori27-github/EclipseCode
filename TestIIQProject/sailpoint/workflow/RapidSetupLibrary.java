/*
 * (c) Copyright 2019 SailPoint Technologies, Inc., All Rights Reserved.
 */
package sailpoint.workflow;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.PasswordPolice;
import sailpoint.object.AuditEvent;
import sailpoint.object.WorkflowCase;
import sailpoint.rapidsetup.plan.BasePlanBuilder;
import sailpoint.rapidsetup.plan.DefaultLeaverAppConfigProvider;
import sailpoint.rapidsetup.plan.DeferredPlan;
import sailpoint.rapidsetup.plan.LeaverDeferredPlanExecutor;

import sailpoint.rapidsetup.plan.LeaverPlanBuilder;
import sailpoint.rapidsetup.plan.LeaverAppConfigProvider;
import sailpoint.rapidsetup.tools.CertificationLauncher;
import sailpoint.rapidsetup.tools.LinkAndEntitlementTools;
import sailpoint.rapidsetup.tools.ReassignResult;
import sailpoint.rapidsetup.tools.Reassigner;
import sailpoint.rapidsetup.tools.IdentityReassigner;
import sailpoint.api.EntitlementCorrelator;
import sailpoint.api.ObjectUtil;
import sailpoint.api.SailPointContext;
import sailpoint.api.Workflower;
import sailpoint.object.ApprovalItem;
import sailpoint.object.Attributes;
import sailpoint.object.AuthenticationAnswer;
import sailpoint.object.Configuration;
import sailpoint.object.Identity;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.ProvisioningProject;
import sailpoint.object.QueryOptions;
import sailpoint.object.RoleAssignment;
import sailpoint.object.Rule;
import sailpoint.object.RapidSetupConfigUtils;
import sailpoint.object.WorkItem;
import sailpoint.object.Workflow;
import sailpoint.server.Auditor;
import sailpoint.tools.GeneralException;
import sailpoint.object.Filter;
import sailpoint.tools.ObjectNotFoundException;
import sailpoint.tools.Util;

import java.util.Set;

public class RapidSetupLibrary {

    private static Log log = LogFactory.getLog(RapidSetupLibrary.class);

    public static final String VAR_NAME_REQUEST_TYPE = "requestType";

    private static String DEFERRED_LEAVER_ARG_APP_PLANS = "appPlans";

    public static void updateProcessStatus(WorkflowContext wfc) throws GeneralException {
        SailPointContext context = wfc.getSailPointContext();
        Attributes<String, Object> args = wfc.getArguments();
        String identityName = args.getString("identityName");
        String status = args.getString("status");

        Identity identity = context.getObjectByName(Identity.class, identityName);
        Identity locked = ObjectUtil.lockIdentity(context, identity);
        identity.setAttribute(Identity.ATT_RAPIDSETUP_PROC_STATE, status);
        // ObjectUtil.unlockIdentity will save and commit
        ObjectUtil.unlockIdentity(context, locked);
    }

    /**
     * Set the workflow's 'approvalScheme' variable to 'none'
     * unless RapidSetup config has turned on approval generation
     * for the given requestType
     * @param wfc the workflow context
     */
    public static void updateApprovalScheme(WorkflowContext wfc) {
        boolean shouldGenApprovals = false;

        SailPointContext context = wfc.getSailPointContext();
        Attributes<String, Object> args = wfc.getArguments();
        String requestType = args.getString("requestType");
        boolean isTerminateIdentity = args.getBoolean("isTerminateIdentity");
        if (Util.isNotNullOrEmpty(requestType)) {
            if(Configuration.RAPIDSETUP_CONFIG_LEAVER.equalsIgnoreCase(requestType) &&
                isTerminateIdentity) {
                shouldGenApprovals = RapidSetupConfigUtils.shouldGenerateApprovals(Configuration.RAPIDSETUP_CONFIG_TERMINATE);
            } else {
                shouldGenApprovals = RapidSetupConfigUtils.shouldGenerateApprovals(requestType);
            }
        }
        if (!shouldGenApprovals) {
            log.debug("Setting approvalScheme to 'none' for " + requestType + " execution");
            wfc.setVariable("approvalScheme", "none");
        }
        else {
            log.debug("Leaving approvalScheme unaltered for " + requestType + " execution");
        }
    }

    public static List<RoleAssignment> calculateBirthrightRoles(WorkflowContext wfc) throws GeneralException {
        SailPointContext context = wfc.getSailPointContext();
        Attributes<String, Object> args = wfc.getArguments();
        String identityName = args.getString("identityName");

        EntitlementCorrelator ec = EntitlementCorrelator.createDefaultEntitlementCorrelator(context);
        Identity identity = context.getObjectByName(Identity.class, identityName);
        if(identity == null) {
            throw new GeneralException("Unable to lookup identity: " + identityName);
        }
        ec.processBirthrightRoles(identity);

        return ec.getNewRoleAssignments();
    }

    /**
     * Build the ProvisioningPlan for joiner, including birth role assignments, and bare account provisioning
     * <pre>
     * Workflow Arguments:
     * context - persistent context
     * workflow - the workflow which is asking for the ProvisioningPlan
     * identityName - the identity name that we need the joiner plan for
     * birthrightRoleAssignments - the birthright roles applicable for this identity
     * </pre>
     * @return the provisioning plan
     * @throws GeneralException
     */
    public static ProvisioningPlan buildJoinerPlan(WorkflowContext wfc) throws GeneralException {
        SailPointContext context = wfc.getSailPointContext();
        Attributes<String, Object> args = wfc.getArguments();
        String identityName = args.getString("identityName");
        List<RoleAssignment> birthrightRoleAssignments = args.getList("birthrightRoles");
        return BasePlanBuilder.joinerPlan(context, identityName, birthrightRoleAssignments).buildPlan();
    }


    /**
     * Update the Link objects in the given given identity if the given
     * ProvisioningPlan has resulted in a move of an account in LDAP-type
     * application.
     * @param wfc
     * @throws GeneralException
     */
    public static void repairIdentity(WorkflowContext wfc) throws GeneralException {
        SailPointContext context = wfc.getSailPointContext();
        Attributes<String, Object> args = wfc.getArguments();
        String identityName = args.getString("identityName");
        ProvisioningPlan plan = (ProvisioningPlan)args.get("plan");

        LinkAndEntitlementTools.updateLinks(context, plan, identityName);
    }

    /**
     * Build the ProvisioningPlan for mover, including birth role assignments, old roles removal and bare account provisioning
     * <pre>
     * Workflow Arguments:
     * context - persistent context
     * workflow - the workflow which is asking for the ProvisioningPlan
     * identityName - the identity name that we need the mover plan for
     * birthrightRoleAssignments - the birthright roles applicable for this identity
     * </pre>
     * @return the provisioning plan
     * @throws GeneralException
     */
    public static ProvisioningPlan buildMoverPlan(WorkflowContext wfc) throws GeneralException {
        SailPointContext context = wfc.getSailPointContext();
        Attributes<String, Object> args = wfc.getArguments();
        String identityName = args.getString("identityName");
        List<RoleAssignment> birthrightRoleAssignments = args.getList("birthrightRoles");
        return BasePlanBuilder.moverPlan(context, identityName, birthrightRoleAssignments).buildPlan();
    }
    /**
     * Checks to see if mover joiners is enabled.
     * @param wfc the workflow context
     * @return true if mover joining is enabled, false if not.
     */
    public static boolean isMoverJoinerEnabled(WorkflowContext wfc) {
        Map moverCertParams = RapidSetupConfigUtils.getRapidSetupBusinessProcessConfiguration(Configuration.RAPIDSETUP_CONFIG_MOVER);
        return Util.getBoolean(moverCertParams, Configuration.RAPIDSETUP_CONFIG_MOVER_JOINER_ENABLED);
    }

    /**
     * Checks to see if mover certifications are enabled.
     * @param wfc the workflow context
     * @return true if mover certifications are enabled, false if not.
     */
    public static boolean isMoverCertificationEnabled(WorkflowContext wfc) {
        Map moverCertParams = RapidSetupConfigUtils.getRapidSetupCertificationParams(Configuration.RAPIDSETUP_CONFIG_MOVER);
        return Util.getBoolean(moverCertParams, Configuration.RAPIDSETUP_CONFIG_MOVER_CERTIFICATION_ENABLED);
    }

    /**
     * Gets the mover certification configuration.
     *
     * @param wfc the workflow context.
     * @return a map containing the mover certification configuration parameters.
     */
    public static Map<String, Object> getMoverCertificationConfiguration(WorkflowContext wfc) {
        return RapidSetupConfigUtils.getRapidSetupCertificationParams(Configuration.RAPIDSETUP_CONFIG_MOVER);
    }


    /**
     * Launches a cert from a cert definition, for a specific user.
     * @param wfc the workflow context
     * <pre>
     * Workflow Parameters:
     * identityName (required) - the identity to launch the cert for
     * templateName (required) - the cert definition to use as a template - must be targeted cert
     * requestorName (required) - the name of the requesting identity
     * includeBirthrightRoles (optional) - set to true to include birthright roles in this cert
     * stageCert (optional) - set to true to stage the new cert
     * nameTemplate (optional) - name template for creating the cert schedule name
     * newCertDefName (optional) - name of the new cert definition referenced by the cert schedule
     * </pre>
     * @throws GeneralException
     */
    static public boolean launchCertificationFromTemplate(WorkflowContext wfc) throws GeneralException {
        sailpoint.rapidsetup.tools.CertificationLauncher launcher = new CertificationLauncher();
        boolean completed = launcher.launchAndWait(wfc);
        return completed;
    }

    /**
     * To be called after the wait period has expired for a Mover cert gen. It will handle any necessary
     * logging or other activities if the cert gen has still not completed.
     * @param wfc workflow context
     * @throws GeneralException
     */
    static public void certWaitExpired(WorkflowContext wfc) throws GeneralException {
        sailpoint.rapidsetup.tools.CertificationLauncher launcher = new CertificationLauncher();
        launcher.warnIfCertGenNotCompleted(wfc);
    }


    /**
     * Change the owners of objects that are curently owned by the leaving identity
     * <pre>
     * Workflow Arguments:
     * identityName - the name of the identity that is leaving
     * plan - the ProvisioningPlan to be passed into optional rule
     * to get identity to reassign to
     * </pre>
     * @return a map which tracks the objects which have had their ownership changed
     * to be optionally used later by email notifications
     * @throws GeneralException unexpected exception
     */
    public static Map reassignOwnership(WorkflowContext wfc) throws GeneralException {
        SailPointContext context = wfc.getSailPointContext();
        Attributes<String, Object> args = wfc.getArguments();
        String identityName = args.getString("identityName");
        boolean isTerminate = args.getBoolean("isTerminateIdentity");
        ProvisioningPlan plan = (ProvisioningPlan)args.get("plan");
        Workflow workflow = wfc.getWorkflow();

        Map reassignConfig = (Map)Util.get(
                RapidSetupConfigUtils.getRapidSetupBusinessProcessConfiguration(
                        isTerminate ? Configuration.RAPIDSETUP_CONFIG_TERMINATE : Configuration.RAPIDSETUP_CONFIG_LEAVER),
                "reassignArtifacts");

        Reassigner reassigner = new Reassigner(reassignConfig);
        ReassignResult result = reassigner.reassignOwnerships(context, identityName);

        auditReassignResult(result, workflow.getName(), identityName);

        // This is for notification, we want send notification to right owner
        String newOwnerName = result.newOwnerName();
        if (newOwnerName != null) {
            workflow.put("identityManager", newOwnerName);
        }

        return result.getOwnershipMap();
    }

    /**
     * Reject the approvals in approval WorkItem objects that are
     * targeted for the leaving identity
     * <pre>
     * Workflow Arguments:
     * identityName - the name of the identity that is leaving
     * </pre>
     * @throws GeneralException unexpected exception
     */
    public static void rejectApprovalWorkItems(WorkflowContext wfc) throws GeneralException {
        SailPointContext context = wfc.getSailPointContext();
        Attributes<String, Object> args = wfc.getArguments();
        String identityName = args.getString("identityName");

        log.debug("Start rejectApprovalWorkItems for identity " + identityName);

        // validate params
        if (Util.isEmpty(identityName)) {
            log.debug("Missing identityName");
            return;
        }

        Set<String> workItemIds = new HashSet<>();

        // Find the ids of pending approval workitems that are targeted for the leaving identiuty
        QueryOptions qo = new QueryOptions();
        qo.addFilter(Filter.eq("targetName", identityName));
        qo.addFilter(Filter.eq("type", "Approval"));
        qo.addFilter(Filter.or(Filter.isnull("state"), Filter.eq("state", WorkItem.State.Pending)));
        Iterator<Object[]> searchRes = context.search(WorkItem.class, qo, "id");
        if (searchRes != null) {
            while(searchRes.hasNext()) {
                Object[] objArr = searchRes.next();
                if (objArr != null) {
                    String workItemId = (String)objArr[0];
                    if (Util.isNotNullOrEmpty(workItemId)) {
                        workItemIds.add(workItemId);
                    }
                }
            }
            Util.flushIterator(searchRes);
        }

        log.debug("approval workItems (pending) targeted for " + identityName + " : " + workItemIds);

        // Reject the approvals in the workitems
        for (String workItemId : Util.safeIterable(workItemIds)) {
            WorkItem workItem = context.getObjectById(WorkItem.class, workItemId);
            if (workItem != null) {
                try {
                    String identityReqId = workItem.getIdentityRequestId();
                    if (log.isDebugEnabled()) {
                        log.debug("workItem name    = " + workItem.getName());
                        log.debug("workItem req id  = " + identityReqId);
                        log.debug("workItem id      = " + workItem.getId());
                        WorkflowCase wfcase = workItem.getWorkflowCase();
                        if (wfcase != null) {
                            log.debug("workItem case id = " + wfcase.getId());
                        }
                    }
                    if(workItem.getApprovalSet() != null) {
                        List<ApprovalItem> approvalItems = workItem.getApprovalSet().getItems();
                        for (ApprovalItem approvalItem : approvalItems) {
                            log.debug("Reject approvalItem " + approvalItem.getId());
                            approvalItem.reject();
                        }
                        Workflower wf = new Workflower(context);
                        log.debug("Finish Workflow for workitem " + workItem.getName());
                        wf.finish(workItem);
                    }
                }
                finally {
                    context.decache(workItem);
                }
            }
        }
        log.debug("End  rejectApprovalWorkItems for identity " + identityName);
    }

    /**
     * Clear all of the authentication questions and answers and the IIQ password for the leaving identity
     * <pre>
     * Workflow Arguments:
     * identityName - the name of the identity that is leaving
     * </pre>
     * @throws GeneralException unexpected exception
     */
    public static void clearAuthenticationQuestionsAndPassword(WorkflowContext wfc) throws GeneralException {

        SailPointContext context = wfc.getSailPointContext();
        Attributes<String, Object> args = wfc.getArguments();
        String identityName = args.getString("identityName");

        log.debug("Start clearAuthenticationQuestionsAndPassword for identity " + identityName );

        // validate params
        if (Util.isEmpty(identityName)) {
            log.debug("Missing identityName");
            return;
        }

        QueryOptions qo = new QueryOptions();
        qo.add(Filter.eq("name", identityName));
        Iterator<Object[]> terminatedId = context.search(Identity.class, qo, "id");
        while (terminatedId.hasNext()) {
            Object[] objArray = terminatedId.next();
            if (objArray != null) {
                String identityId = (String)objArray[0];
                log.debug("Lock Terminated Identity..."+identityName);
                Identity terminatedIdentity = ObjectUtil.lockIdentity(context, identityId);
                if (terminatedIdentity != null) {
                    try {
                        List<AuthenticationAnswer> terminatedIdentityanswers = terminatedIdentity.getAuthenticationAnswers();
                        if (terminatedIdentityanswers != null && terminatedIdentityanswers.size()>0) {
                            while (terminatedIdentityanswers.size() != 0) {
                                AuthenticationAnswer answer = (AuthenticationAnswer) terminatedIdentityanswers.get(0);
                                log.debug("Clear Questions and Answers..." + identityName);
                                terminatedIdentity.removeAuthenticationAnswer(answer);
                            }
                        }
                        if (Util.isNotNullOrEmpty(terminatedIdentity.getPassword())) {
                            PasswordPolice pp = new PasswordPolice(context);
                            pp.addPasswordHistory(terminatedIdentity, context.decrypt(terminatedIdentity.getPassword()));
                            log.debug("Clear password..." + identityName);
                            terminatedIdentity.setPassword(null);
                        }
                    }
                    finally {
                        try {
                            log.debug("UnLock Terminated Identity.."+identityName);
                            // Save, Commit, Unlock^M
                            ObjectUtil.unlockIdentity(context, terminatedIdentity);
                        }
                        catch (Throwable t) {
                            log.error("Error Unable to unlock terminated identity..."+identityName);
                        }
                    }
                }
            }
        }

        log.debug("End clearAuthenticationQuestionsAndPassword for identity " + identityName );
    }

    public static String getRequestType(WorkflowContext wfc) throws GeneralException {
        log.debug("Enter getRequestType");

        SailPointContext context = wfc.getSailPointContext();
        Workflow workflow = wfc.getWorkflow();
        Attributes<String, Object> args = wfc.getArguments();
        String defaultValue = args.getString("defaultValue");

        String requestType = null;
        if (workflow != null) {
            String requestTypeInitialized = (String)workflow.get(VAR_NAME_REQUEST_TYPE);
            if (Util.isNotNullOrEmpty(requestTypeInitialized)) {
                log.debug("requestTypeInitialized..."+requestTypeInitialized);
                requestType = requestTypeInitialized;
            }
        }
        if (Util.isEmpty(requestType)) {
            log.debug("requestType defaulting to ..." + defaultValue);
            requestType = defaultValue;
        }

        log.debug("End getRequestType..."+requestType);
        return requestType;
    }

    /////////////////////////////////////////////
    // Leaver-specific
    /////////////////////////////////////////////

    /**
     * Build the ProvisioningPlan for leaver
     * <pre>
     * Workflow Arguments:
     * context - persistent context
     * workflow - the workflow which is asking for the ProvisioningPlan
     * identityName - the identity name that we need the leaver plan for
     * birthrightRoleAssignments - the birthright roles applicable for this identity
     * </pre>
     * @return the provisioning plan
     * @throws GeneralException
     */
    public static ProvisioningPlan buildLeaverPlan(WorkflowContext wfc) throws GeneralException {
        SailPointContext context = wfc.getSailPointContext();
        Attributes<String, Object> args = wfc.getArguments();
        String identityName = args.getString("identityName");
        String requestType = args.getString("requestType");
        String reasonComments = args.getString("reasonComments");
        boolean isTerminateIdentity = args.getBoolean("isTerminateIdentity");

        Map<String,Object> additionalArgs = new HashMap<>();
        additionalArgs.put("requestType", requestType);
        additionalArgs.put("reasonComments", reasonComments);

        LeaverAppConfigProvider appConfigProvider = new DefaultLeaverAppConfigProvider(
                isTerminateIdentity ?
                        DefaultLeaverAppConfigProvider.OPT_LEAVER_CONFIG_BREAKGLASS :
                        DefaultLeaverAppConfigProvider.OPT_LEAVER_CONFIG_NORMAL);
        return BasePlanBuilder.leaverPlan(context, identityName, additionalArgs, appConfigProvider, isTerminateIdentity).buildPlan();
    }

    public static void executeLeaverDeferActions(WorkflowContext wfc) throws GeneralException {
        log.debug("Start RapidSetupLibrary.executeLeaverDeferActions");
        SailPointContext context = wfc.getSailPointContext();
        Attributes<String, Object> args = wfc.getArguments();
        boolean isTerminateIdentity = args.getBoolean("isTerminateIdentity");
        Map<String, List<DeferredPlan>> deferredPlans =
                (Map<String, List<DeferredPlan>>)args.get(DEFERRED_LEAVER_ARG_APP_PLANS);

        if(deferredPlans == null) {
            log.debug("No application plans found.");
            return;
        }

        LeaverAppConfigProvider appConfigProvider = new DefaultLeaverAppConfigProvider(
                isTerminateIdentity ?
                        DefaultLeaverAppConfigProvider.OPT_LEAVER_CONFIG_BREAKGLASS :
                        DefaultLeaverAppConfigProvider.OPT_LEAVER_CONFIG_NORMAL);
        for(String appName : deferredPlans.keySet()) {
            List<DeferredPlan> appDeferredPlans = deferredPlans.get(appName);
            for (DeferredPlan plan : Util.safeIterable(appDeferredPlans)) {
                new LeaverDeferredPlanExecutor(context, args, appConfigProvider).
                        executePlanForApplication(appName, plan.getPlan(), plan.getDelay());
            }
        }

        log.debug("End RapidSetupLibrary.executeLeaverDeferActions");
    }

    public static LeaverPlanBuilder getLeaverPlanBuilder(WorkflowContext wfc) {
        log.debug("Start RapidSetupLibrary.getLeaverPlanBuilder");
        SailPointContext context = wfc.getSailPointContext();
        Attributes<String, Object> args = wfc.getArguments();
        String identityName = args.getString("identityName");
        String requestType = args.getString("requestType");
        String reasonComments = args.getString("reasonComments");
        boolean isTerminateIdentity = args.getBoolean("isTerminateIdentity");

        Map<String,Object> additionalArgs = new HashMap<>();
        additionalArgs.put("requestType", requestType);
        additionalArgs.put("reasonComments", reasonComments);

        LeaverAppConfigProvider appConfigProvider = new DefaultLeaverAppConfigProvider(
                isTerminateIdentity ?
                        DefaultLeaverAppConfigProvider.OPT_LEAVER_CONFIG_BREAKGLASS :
                        DefaultLeaverAppConfigProvider.OPT_LEAVER_CONFIG_NORMAL);
        return new LeaverPlanBuilder(context, identityName, additionalArgs, appConfigProvider, isTerminateIdentity);
    }


    /**
     * Execute the post-Leaver rule
     * <pre>
     * Workflow Arguments:
     * identityName - the name of the identity that is leaving
     * project - the ProvisioningProject that has been performed
     * requestType - the type of request being performed, usually "leaver"
     * </pre>
     * @return the object returned from the rule.  Currently ignored from workflow.
     * @throws GeneralException unexpected exception
     */
    public static Object executePostLeaverRule(WorkflowContext wfc) throws GeneralException {
        Attributes<String, Object> args = wfc.getArguments();
        if(args.getBoolean("isTerminateIdentity")) {
            return executePostRule(wfc, "businessProcesses,terminate,postTerminateRule", true);
        }
        return executePostRule(wfc, "businessProcesses,leaver,postLeaverRule", true);
    }

    /**
     * Checks to see if joiner send temporary password email is enabled.
     * @param wfc the workflow context
     * @return true if joiner send temporary password email is enabled, false if not.
     */
    public static boolean isSendTemporaryPasswordEmailEnabled(WorkflowContext wfc) {
        Map JoinerConfig = RapidSetupConfigUtils.getRapidSetupBusinessProcessConfiguration(Configuration.RAPIDSETUP_CONFIG_JOINER);
        if (Util.isEmpty(JoinerConfig)) {
            log.debug("No joiner configuration found.");
            return false;
        }
        else {
            Map joinerEmailConfig =  (Map<String, Object>) Util.get(JoinerConfig, Configuration.RAPIDSETUP_CONFIG_EMAIL);
            return Util.getBoolean(joinerEmailConfig, Configuration.RAPIDSETUP_CONFIG_JOINER_SEND_TEMPORARY_PASSWORD_EMAIL);
        }
    }

    /**
     * Execute the post-Joiner rule
     * <pre>
     * Workflow Arguments:
     * identityName - the name of the identity that is leaving
     * project - the ProvisioningProject that has been performed
     * requestType - the type of request being performed, usually "joiner"
     * </pre>
     * @return the object returned from the rule.  Currently ignored from workflow.
     * @throws GeneralException unexpected exception
     */
    public static Object executePostJoinerRule(WorkflowContext wfc) throws GeneralException {
        return executePostRule(wfc, "businessProcesses,joiner,postJoinerRule", true);
    }

    /**
     * Execute the post-Mover rule
     * <pre>
     * Workflow Arguments:
     * identityName - the name of the identity that is leaving
     * requestType - the type of request being performed, usually "joiner"
     * </pre>
     * @return the object returned from the rule.  Currently ignored from workflow.
     * @throws GeneralException unexpected exception
     */
    public static Object executePostMoverRule(WorkflowContext wfc) throws GeneralException {
        return executePostRule(wfc, "businessProcesses,mover,postMoverRule", true);
    }


    /**
     * Execute the post- rule for the given business process
     * @param wfc the workflow context
     * <pre>
     * Workflow Arguments:
     * identityName - the name of the identity that is leaving
     * project - the ProvisioningProject that has been performed
     * requestType - the type of request being performed, usually "leaver"
     * </pre>
     * @param rulePath  the key in the RapidSetup config that contains the rule name that
     *                  we should run
     * @return the object returned from the rule.  Currently ignored from workflow.
     * @throws GeneralException unexpected exception
     */
    private static Object executePostRule(WorkflowContext wfc, String rulePath, boolean includeProject) throws GeneralException {

        SailPointContext context = wfc.getSailPointContext();
        Attributes<String, Object> args = wfc.getArguments();
        String identityName = args.getString("identityName");
        String requestType = args.getString("requestType");
        ProvisioningProject project = (ProvisioningProject)args.get("project");

        log.debug("Begin executePostRule " + rulePath + " for identity " + identityName );

        log.debug("..identityName..."+identityName );
        log.debug("..requestType..."+requestType );
        if (includeProject) {
            log.debug("..project..." + project);
        }

        Object retVal = null;

        Rule rule = getRule(context, rulePath);
        if (rule != null) {
            try {
                HashMap params = new HashMap();
                params.put("context", context);
                params.put("identityName", identityName);
                params.put("requestType", requestType);
                params.put("project", includeProject ? project : null);
                log.debug( "...Run the rule " + rule.getName());
                retVal = context.runRule(rule, params);
            } catch (Exception re) {
                log.error("...Rule Exception " + re.getMessage());
                throw new GeneralException("Error During Rule Launch..." + rule.getName() + " " + re.getMessage());
            }
            finally {
                context.decache(rule);
            }
        }

        log.debug("End executePostRule " + rulePath + " for identity " + identityName );
        return retVal;
    }

    /**
     * Get rule identified by the given path in RapidSetup config
     * @param context database context
     * @param rulePath the path in the config to locate the rule name
     * @return the Rule object corresponding to the name given in the configuraiton.  Returns
     * null if not configured
     * @throws GeneralException if there is a rule name found in config, but there is no
     * corresponding Rule in the database
     */
    private static Rule getRule(SailPointContext context, String rulePath) throws GeneralException {
        Rule rule = null;
        String ruleName = RapidSetupConfigUtils.getString(rulePath);
        if (Util.isNotNullOrEmpty(ruleName)) {
            rule = (Rule) context.getObjectByName(Rule.class, ruleName);
            if (rule == null) {
                log.error("Cannot find post rule '" + ruleName + "'.'");
                throw new ObjectNotFoundException(Rule.class, ruleName);
            }
        }
        else {
            log.debug("No post rule configured.  Skipping post rule execution.");
        }
        return rule;
    }

    /**
     * Remove service account ownership
     * <pre>
     * Workflow Arguments:
     * context - persistent context
     * workflow - the workflow which is asking for the ProvisioningPlan
     * identityName - the identity name that we need the leaver plan for
     * </pre>
     * @return the provisioning plan
     * @throws GeneralException
     */
    public static Map<String, List<String>> executeRemoveIdentityOwnership(WorkflowContext wfc) throws GeneralException {
        log.debug("Start removeServiceAccountOwnership" );
        SailPointContext context = wfc.getSailPointContext();
        Workflow workflow = wfc.getWorkflow();
        Attributes<String, Object> args = wfc.getArguments();
        String identityName = args.getString("identityName");
        String requestType = args.getString("requestType");
        boolean isTerminate = args.getBoolean("isTerminateIdentity");
        ProvisioningPlan plan = (ProvisioningPlan) args.getOrDefault("plan", null);
        String process =  isTerminate ? Configuration.RAPIDSETUP_CONFIG_TERMINATE : Configuration.RAPIDSETUP_CONFIG_LEAVER;
        Map businessProcessConfig = RapidSetupConfigUtils.getRapidSetupBusinessProcessConfiguration(process);
        Map reassignIdentitiesConfig = (Map)Util.get(businessProcessConfig,"reassignIdentities");
        IdentityReassigner reassigner = new IdentityReassigner(reassignIdentitiesConfig, process);
        ReassignResult result = reassigner.reassignIdentities(context, identityName);

        auditReassignResult(result, workflow.getName(), identityName);

        // This is for notification, we want send notification to right owner
        String newOwnerName = result.newOwnerName();
        if (newOwnerName != null) {
            workflow.put("identityManager", newOwnerName);
        }

        return result.getOwnershipMap();
    }

    /**
     * Log audit event
     */
    private static void auditReassignResult(ReassignResult result, String workflowName, String identityName) {
        if (result == null || Util.isNullOrEmpty(result.newOwnerName())) {
            return;
        }

        AuditEvent event = new AuditEvent(workflowName, AuditEvent.ActionUpdate);
        // set old owner name
        event.setString1(identityName);
        // Set new owner name
        event.setString2(result.newOwnerName());
        event.setAttribute("comment", result.toDebugString());
        Auditor.log(event);
    }

}
