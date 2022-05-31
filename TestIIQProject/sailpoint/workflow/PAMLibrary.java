/* (c) Copyright 2017. SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.workflow;

import java.util.List;

import sailpoint.api.SailPointContext;
import sailpoint.object.ApprovalSet;
import sailpoint.object.Attributes;
import sailpoint.object.AuditEvent;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.Link;
import sailpoint.object.ManagedAttribute;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.ProvisioningPlan.AccountRequest;
import sailpoint.object.ProvisioningPlan.PermissionRequest;
import sailpoint.object.QueryOptions;
import sailpoint.object.Source;
import sailpoint.object.WorkItem;
import sailpoint.object.Workflow;
import sailpoint.object.Workflow.Approval;
import sailpoint.server.Auditor;
import sailpoint.service.pam.ContainerService;
import sailpoint.service.pam.PamRequest;
import sailpoint.service.pam.PamRequestService;
import sailpoint.service.pam.PamUtil;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;


/**
 * A workflow library with methods that are used with PAM workflows.
 */
public class PAMLibrary {

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //
    // CONSTANTS
    //
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Argument that holds the name of the identity.
     */
    public static final String ARG_IDENTITY_NAME = "identityName";

    /**
     * Argument that holds the display name of the identity.
     */
    public static final String ARG_IDENTITY_DISPLAY_NAME = "identityDisplayName";

    /**
     * Argument that holds the name of the container.
     */
    public static final String ARG_CONTAINER_NAME = "containerName";

    /**
     * Argument that holds the display name of the container.
     */
    public static final String ARG_CONTAINER_DISPLAY_NAME = "containerDisplayName";

    /**
     * Argument that holds the name of the container owner.
     */
    public static final String ARG_CONTAINER_OWNER_NAME = "containerOwnerName";

    /**
     * Argument that holds the ProvisioningPlan.
     */
    public static final String ARG_PLAN = "plan";

    /**
     * Argument that holds the PamRequest.
     */
    public static final String ARG_PAM_REQUEST = "pamRequest";

    private static final String OWNER_VALUE_PROPERTY = "owner.value";


    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //
    // PAM REQUEST DTO
    //
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Create a PAMRequestDTO using the arguments found in the WorkflowContext.
     *
     * @param wfc  The WorkflowContext.
     *
     * @return A PAMRequestDTO.
     *
     * @throws GeneralException If any required arguments were not specified in the WorkflowContext.
     */
    public PamRequest createPamRequest(WorkflowContext wfc) throws GeneralException {
        ProvisioningPlan plan = ensureArgument(wfc, ARG_PLAN);
        String identityName = ensureArgument(wfc, ARG_IDENTITY_NAME);
        String identityDisplayName = ensureArgument(wfc, ARG_IDENTITY_DISPLAY_NAME);
        String containerName = ensureArgument(wfc, ARG_CONTAINER_NAME);
        String containerDisplayName = ensureArgument(wfc, ARG_CONTAINER_DISPLAY_NAME);
        String containerOwnerName = (String) wfc.getArguments().get(ARG_CONTAINER_OWNER_NAME);

        PamRequestService svc = new PamRequestService(wfc.getSailPointContext());
        return svc.createPamRequest(plan, identityName, identityDisplayName, containerName, containerDisplayName, containerOwnerName);
    }


    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //
    // APPROVALS
    //
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Return a list of approvals for the PAM request based on the configured approvalScheme.
     *
     * @param wfc  The WorkflowContext.
     *
     * @return A List of Approvals, or null if no Approvals are required.
     */
    public List<Approval> createApprovals(WorkflowContext wfc) throws GeneralException {
        PAMApprovalGenerator generator = new PAMApprovalGenerator(wfc);
        return generator.buildCommonApprovals();
    }

    /**
     * Audit the approval decision that was made for the PAM request.
     *
     * @param wfc  The WorkflowContext that contains the approved state and the ProvisioningPlan.
     */
    public void auditApprovalDecision(WorkflowContext wfc) throws GeneralException {
        PamRequest request = ensureArgument(wfc, ARG_PAM_REQUEST);

        // Shouldn't have a null state at this point, but we'll assume that it was approved if it is null.
        WorkItem.State approvalState = request.getApprovalState();
        boolean approved = ((null == approvalState) || WorkItem.State.Finished.equals(approvalState));

        String action = (approved) ? AuditEvent.ActionApprovePamRequest : AuditEvent.ActionRejectPamRequest;
        String actor = request.getApprover();

        // Just quit now if auditing is not enabled for the action.
        if (!Auditor.isEnabled(action)) {
            return;
        }

        AuditEvent event = new AuditEvent();
        event.setAction(action);

        // Setup basic information on the event.
        event.setAttribute("requester", wfc.getString(Workflow.VAR_LAUNCHER));
        event.setSource(wfc.getString(Workflow.VAR_LAUNCHER));
        event.setTarget(wfc.getString(ARG_IDENTITY_NAME));
        event.setInterface(Source.PAM.toString());
        String taskResultId = wfc.getString(Workflow.VAR_TASK_RESULT);
        if (taskResultId != null) {
            event.setAttribute(Workflow.VAR_TASK_RESULT, taskResultId);
        }

        // Set the provisioning plan information.
        addPlanToAuditEvent(wfc, event);

        SailPointContext spcon = wfc.getSailPointContext();
        // bug#7698 do not trust the context when we need to commit
        spcon.decache();

        // This should have been set on the PamRequest, but fallback to the context user if not there.
        if (null == actor) {
            // bug#8337 we're supposed to be auditing the name of the
            // user that actually made the decision and closed the work item
            // since the owner may be the name of a work group.  We no longer
            // have the WorkItem so we don't know who the owning Identity 
            // was, assume that we transitioned here within the same
            // workflow session so the current user is still set.
            actor = spcon.getUserName();
        }

        if (null != actor) {
            Identity ident = spcon.getObjectByName(Identity.class, actor);
            if (ident != null) {
                event.setSource(ident.getDisplayableName());
            }
        }

        // We would have bailed already if auditing wasn't enabled, so ... ahead warp zillion!!
        Auditor.log(event);
        spcon.commitTransaction();
    }

    /**
     * Add the information from the ProvisioningPlan in the WorkflowContext to the given event.
     *
     * @param wfc  The WorkflowContext.
     * @param event  The event to which to add the plan details.
     */
    private void addPlanToAuditEvent(WorkflowContext wfc, AuditEvent event) {
        ProvisioningPlan plan = (ProvisioningPlan) wfc.get(ARG_PLAN);
        if ((null != plan) && !Util.isEmpty(plan.getAccountRequests())) {
            // There should be a single account request.  Just grab the first.
            AccountRequest acctReq = plan.getAccountRequests().get(0);
            event.setApplication(acctReq.getApplication());
            event.setAccountName(acctReq.getNativeIdentity());
            event.setInstance(acctReq.getInstance());

            // Expect a single permission request.
            if (!Util.isEmpty(acctReq.getPermissionRequests())) {
                PermissionRequest permReq = acctReq.getPermissionRequests().get(0);
                event.setAttribute("operation", permReq.getOperation().toString());
                event.setAttributeName(permReq.getTarget());
                event.setAttributeValue(permReq.getRights());
            }
        }
    }

    /**
     * Update the PamRequest and IdentityRequest with the appropriate information after the given approval work item
     * is opened.
     *
     * @param wfc  The WorkflowContext.
     * @param request  The PamRequest.
     * @param item  The WorkItem for the approval that was just opened.
     */
    public static void approvalWorkItemOpened(WorkflowContext wfc, PamRequest request, WorkItem item) throws GeneralException {
        // Set the owner name on the PamRequest.
        request.setApprovalOwner(getWorkItemOwnerName(item));

        // Update the IdentityRequest status.
        updateIdentityRequestApprovalStatus(wfc, request, false);
    }

    /**
     * Update the PamRequest and IdentityRequest with the appropriate information after the given approval work item
     * is completed.
     *
     * @param wfc  The WorkflowContext.
     * @param request  The PamRequest.
     * @param item  The WorkItem for the approval that was just completed.
     */
    public static void approvalWorkItemCompleted(WorkflowContext wfc, PamRequest request, WorkItem item) throws GeneralException {
        // Set the owner name on the PamRequest in case it was forwarded.  Note that for parent approvals, the item may
        // be null, so only set the name if we have an item.
        if (null != item) {
            String approver = getWorkItemOwnerName(item);
            request.setApprovalOwner(approver);
            request.setApprover(approver);
        }

        // Update the IdentityRequest status.
        updateIdentityRequestApprovalStatus(wfc, request, true);
    }

    /**
     * Return the name of the identity that owns the given work item.
     *
     * @param item  The WorkItem.
     *
     * @return The name of the identity that ownes the given work item, or null if the item or owner is null.
     */
    private static String getWorkItemOwnerName(WorkItem item) {
        String ownerName = null;

        if ((null != item) && (null != item.getOwner())) {
            ownerName = item.getOwner().getName();
        }

        return ownerName;
    }

    /**
     * Update the IdentityRequest in the workflow with the approval information from the given PamRequest.
     *
     * @param wfc  The WorkflowContext - expect the identityRequestId to be available.
     * @param request  The PamRequest with the approval status.
     * @param updateTaskResult  Whether the WorkflowSummary in the task result should also be updated.
     */
    private static void updateIdentityRequestApprovalStatus(WorkflowContext wfc, PamRequest request, boolean updateTaskResult)
        throws GeneralException {
        ApprovalSet approvalSet = createApprovalSet(wfc, request);
        IdentityRequestLibrary.assimilateWorkItemApprovalSetToIdentityRequest(wfc, approvalSet, updateTaskResult);
    }

    /**
     * Create an ApprovalSet from the PamRequest in the given workflow.
     *
     * Note: The PAM workflows use a PamRequest to handle approvals instead of an ApprovalSet because originally the
     * PAM requests were a bit different than LCM requests.  This is no longer the case, so we should at some point
     * move to using an ApprovalSet in the PAM workflows and phase out PamRequest.
     *
     * @param wfc  The WorkflowContext.
     *
     * @return An ApprovalSet with information from the PamRequest in the given workflow.
     */
    public ApprovalSet createApprovalSetFromPamRequest(WorkflowContext wfc) throws GeneralException {
        PamRequest request = ensureArgument(wfc, ARG_PAM_REQUEST);
        return createApprovalSet(wfc, request);
    }

    /**
     * Create an ApprovalSet from the given PamRequest.
     *
     * @param request  The PamRequest.
     *
     * @return An ApprovalSet with information from the given PamRequest.
     */
    private static ApprovalSet createApprovalSet(WorkflowContext wfc, PamRequest request) {
        PamRequestService svc = new PamRequestService(wfc.getSailPointContext());
        return svc.createApprovalSet(request);
    }


    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //
    // HELPER METHODS
    //
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Return the requested argument from the WorkflowContext, or throw if it is null.
     */
    private <T> T ensureArgument(WorkflowContext wfc, String argName) throws GeneralException {
        Attributes<String,Object> args = wfc.getArguments();

        @SuppressWarnings("unchecked")
        T value = (T) Util.get(args, argName);
        if (null == value) {
            throw new GeneralException(argName + " is required in the arguments");
        }

        return value;
    }

    /**
     * Try to set the owner of the managed attribute to the owner.value of the container if a user with a matching
     * link exists.
     * @param attr
     */
    public static void setManagedAttributeOwner(ManagedAttribute attr, SailPointContext context) throws GeneralException {
        if (ContainerService.OBJECT_TYPE_CONTAINER.equals(attr.getType())) {
            String containerOwner = (String) attr.getAttribute(OWNER_VALUE_PROPERTY);
            if (Util.isNotNullOrEmpty(containerOwner)) {
                QueryOptions qo = new QueryOptions();
                qo.add(Filter.eq("nativeIdentity", containerOwner));
                List<Link> accounts = context.getObjects(Link.class, qo);
                for (Link account : Util.safeIterable(accounts)) {
                    if (PamUtil.PAM_APPLICATION_TYPE.equals(account.getApplication().getType())) {
                        Identity matchingIdentity = account.getIdentity();
                        if (matchingIdentity != null) {
                            attr.setOwner(matchingIdentity);
                            break;
                        }
                    }
                }
            }
        }
    }
}
