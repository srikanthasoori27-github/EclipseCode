package sailpoint.service;

import sailpoint.api.DynamicScopeMatchmaker;
import sailpoint.api.IdentityLifecycler;
import sailpoint.api.ObjectUtil;
import sailpoint.api.SailPointContext;
import sailpoint.api.WorkflowSession;
import sailpoint.object.Attributes;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.Link;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.QueryOptions;
import sailpoint.object.QuickLink;
import sailpoint.object.Source;
import sailpoint.object.WorkItem;
import sailpoint.object.Workflow;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

import java.util.List;

public class LCMRequestProcessor {

    private final SailPointContext context;
    private final Identity requester;
    private final Identity requestee;
    private final QuickLink quickLink;
    private final String ticketApp;
    private final String externalApp;
    private final WorkItem.Level priority;
    
    public LCMRequestProcessor(SailPointContext context, Identity requester, Identity requestee, QuickLink quickLink,
            String ticketApp, String externalApp, WorkItem.Level priority) {
        this.context = context;
        this.requester = requester;
        this.requestee = requestee;
        this.quickLink = quickLink;
        this.ticketApp = ticketApp;
        this.externalApp = externalApp;
        this.priority = priority;
    }

    /**
     * Given account requests this kicks off the workflow
     *
     * @param accountRequests The account requests to process in the workflow
     * @param flowName The name of the flow to start
     * @return the workflow session
     * @throws GeneralException In a variety of unrecoverable cases
     */
    public WorkflowSession startWorkflow(List<ProvisioningPlan.AccountRequest> accountRequests, String flowName) throws GeneralException {
        return startWorkflow(accountRequests, flowName, null);
    }

    /**
     * Given account requests this kicks off the workflow
     *
     * @param accountRequests The account requests to process in the workflow
     * @param flowName The name of the flow to start
     * @param requesterComment Comment to add to the plan
     *
     * @return the workflow session
     * @throws GeneralException In a variety of unrecoverable cases
     */
    public WorkflowSession startWorkflow(List<ProvisioningPlan.AccountRequest> accountRequests, String flowName, String requesterComment) throws GeneralException {
        for (ProvisioningPlan.AccountRequest accountRequest : accountRequests) {
            this.validate(accountRequest);
        }
        ProvisioningPlan plan = this.createPlan(accountRequests);
        if(!Util.isNullOrEmpty(requesterComment)) {
            plan.setComments(requesterComment);
        }
        String workflow = ObjectUtil.getLCMWorkflowName(this.context, flowName);
        Attributes<String, Object> arguments = this.createWorkflowArguments(flowName);
        WorkflowSession workflowSession = this.runWorkflow(workflow, arguments, plan);
        workflowSession.save(this.context);
        return workflowSession;
    }


    /**
     * Asserts that the account request is sane for the given context
     * @param accountRequest The account request to validate
     * @throws GeneralException If something seems awry
     */
    public void validate(ProvisioningPlan.AccountRequest accountRequest) throws GeneralException {
        validateAccountRequest(accountRequest);
        validateRequesterAccess();
    }

    /**
     * Create a provisioning plan from the passed AccountRequest
     * @param accountRequests The AccountRequests to build the plan from
     * @return The new ProbvisioningPlan
     */
    private ProvisioningPlan createPlan(List<ProvisioningPlan.AccountRequest> accountRequests) {
        ProvisioningPlan plan = new ProvisioningPlan();
        Identity targetIdentity = this.requestee;
        // create case will leave identityId null
        if(targetIdentity == null) {
            targetIdentity = new Identity();
            String nativeIdentity = null;
            for (ProvisioningPlan.AccountRequest req : accountRequests) {
                if (ProvisioningPlan.AccountRequest.Operation.Create.equals(req.getOperation())) {
                    nativeIdentity = req.getNativeIdentity();
                    // There should only be one of these per submit
                    break;
                }
            }
            if (nativeIdentity != null) {
                // For the create case just create a stub identity
                targetIdentity.setName(nativeIdentity);
            }
        }
        plan.setIdentity(targetIdentity);
        plan.setSource(Source.LCM);
        for (ProvisioningPlan.AccountRequest accountRequest : accountRequests) {
            plan.add(accountRequest);
        }

        return plan;
    }

    /**
     * Builds argument map for the workflow
     * @param flowName The name of the flow
     * @return arguments map for the workflow
     */
    private Attributes<String, Object> createWorkflowArguments(String flowName) {
        Attributes<String,Object> args = new Attributes<String,Object>();
        if(requestee != null) {
            args.put("identityDisplayName", requestee.getDisplayableName());
        }
        args.put("flow", flowName);
        args.put(Workflow.ARG_WORK_ITEM_PRIORITY, getWorkItemPriority());
        if(!Util.isNullOrEmpty(ticketApp)) {
            args.put(RequestAccessService.ATT_TICKET_MANAGEMENT_APPLICATION, ticketApp);
        }
        if (!Util.isNullOrEmpty(externalApp)) {
            args.put(RequestAccessService.ATT_EXTERNAL_SOURCE, externalApp);
        }
        return args;
    }

    /**
     * Launch the workflow
     * @param workflow The name of the workflow
     * @param arguments The workflow arguments
     * @param plan The provisioning plan
     * @return The workflow session for the executing workflow
     * @throws GeneralException If unable to launch the workflow
     */
    private WorkflowSession runWorkflow(String workflow, Attributes<String, Object> arguments, ProvisioningPlan plan) throws GeneralException {
        IdentityLifecycler cycler = new IdentityLifecycler(context);
        // first arg is owner
        WorkflowSession workflowSession = cycler.launchUpdate(null, plan.getIdentity(), plan, workflow, arguments);
        return workflowSession;
    }

    /**
     * Returns the priority the workitem should be.
     * It seems like for change password this is always Normal priority.
     *
     * @return the priority the workitem should be
     */
    private WorkItem.Level getWorkItemPriority() {
        return (this.priority != null) ? this.priority : WorkItem.Level.Normal;
    }

    /**
     * Validates the account request
     * @param accountRequest The account request to validate
     * @throws GeneralException If the native identity does not belong to the target id
     */
    private void validateAccountRequest(ProvisioningPlan.AccountRequest accountRequest) throws GeneralException {
        /* Don't validate creation requests since the native identity will be null */
        if(accountRequest.getOperation().equals(ProvisioningPlan.AccountRequest.Operation.Create)) {
            return;
        }
        /* If changing and IIQ account obviously there will be no link */
        if(!Util.nullSafeEq(accountRequest.getApplication(), "IIQ")) {
            /* Check the native identity to make sure that it belongs to the target identity */
            QueryOptions qo = new QueryOptions();
            qo.add(Filter.eq("identity.id", requestee.getId()));
            qo.add(Filter.eq("nativeIdentity", accountRequest.getNativeIdentity()));
            int count = context.countObjects(Link.class, qo);
            if (count <= 0) {
                throw new GeneralException("Native identity is not for requested identity");
            }
        }
    }

    /**
     * Validates the the requester can request changes for the target identity
     * @throws GeneralException If the requester cannot request the change for the target identity
     */
    private void validateRequesterAccess() throws GeneralException {
        /* Cannot validate scope on a new user request */
        if(requestee == null) {
            return;
        }
        if (!requestee.equals(requester)) {
            /* Check the identity id to see if it is within the scope of the requestor's ability */
            LCMConfigService svc = new LCMConfigService(context);
            List<String> scopes = getDynamicScopes(requester);
            QueryOptions identityOptions = svc.getRequestableIdentityOptions(requester, scopes, quickLink.getName(), quickLink.getAction());
            if (identityOptions == null) {
                // null is only returned when no identities are in LCM scope
                throw new RuntimeException("Requestor does not have rights to request for anyone.");
            }
            identityOptions.add(Filter.eq("id", requestee.getId()));
            int count = context.countObjects(Identity.class, identityOptions);
            if (count < 1) {
                throw new GeneralException("Not able to request for: " + requestee.getId());
            }
        }
    }

    /**
     * Get the identities dynamic scopes
     * @param identity The identity to get the dynamic scopes of
     * @return List of dynamic scopes
     * @throws GeneralException If not able to get DynamicScopes
     */
    private List<String> getDynamicScopes(Identity identity) throws GeneralException {
        DynamicScopeMatchmaker matcher = new DynamicScopeMatchmaker(context);
        List<String> dynamicScopeNames = matcher.getMatches(identity);
        return dynamicScopeNames;
    }
}