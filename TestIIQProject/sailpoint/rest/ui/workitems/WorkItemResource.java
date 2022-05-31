/* (c) Copyright 2015 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.rest.ui.workitems;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.ws.rs.BadRequestException;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Response;

import sailpoint.api.ObjectUtil;
import sailpoint.api.ViolationDetailer;
import sailpoint.api.WorkflowSession;
import sailpoint.authorization.WorkItemAuthorizer;
import sailpoint.integration.ListResult;
import sailpoint.object.ApprovalItem;
import sailpoint.object.Bundle;
import sailpoint.object.Certification;
import sailpoint.object.Identity;
import sailpoint.object.ManagedAttribute;
import sailpoint.object.UIConfig;
import sailpoint.object.WorkItem;
import sailpoint.rest.BaseResource;
import sailpoint.rest.HttpSessionStorage;
import sailpoint.rest.jaxrs.PATCH;
import sailpoint.rest.ui.SuccessResult;
import sailpoint.rest.ui.identities.RoleDetailResource;
import sailpoint.rest.ui.managedattribute.ManagedAttributeDetailResource;
import sailpoint.service.AccessRequestPolicyViolationDecision;
import sailpoint.service.ApprovalItemsService;
import sailpoint.service.ApprovalWorkItemService;
import sailpoint.service.IdentityDetailsService;
import sailpoint.service.ViolationReviewResult;
import sailpoint.service.WorkItemService;
import sailpoint.service.WorkflowSessionService;
import sailpoint.tools.GeneralException;
import sailpoint.tools.InvalidParameterException;
import sailpoint.tools.Message;
import sailpoint.tools.ObjectNotFoundException;
import sailpoint.tools.RetryableEmailException;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.util.WebUtil;
import sailpoint.web.workitem.WorkItemDTO;

/**
 * @author: peter.holcomb
 */
public class WorkItemResource extends BaseResource {

    private String workItemId;
    private WorkItemService workItemService;


    ////////////////////////////////////////////////////////////////////////////
    //
    // CONSTRUCTORS
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Sub-resource constructor.
     *
     * @param parent The parent resource.
     */
    public WorkItemResource(BaseResource parent) {
        super(parent);
    }

    /**
     * Create an approval work item resource for the given work item id.
     * @param  workItemId  The name or ID of the application.
     * @param  parent       The parent of this subresource.
     */
    public WorkItemResource(String workItemId, BaseResource parent) throws GeneralException {
        super(parent);
        this.workItemId = decodeRestUriComponent(workItemId);
        this.workItemService = this.getWorkItemService(this.workItemId);
    }

    /**
     * Create an work item resource for the given work item id.
     * @param  workItemId  The name or ID of the application.
     * @param  parent       The parent of this subresource.
     */
    public WorkItemResource(String workItemId, BaseResource parent, WorkItemService workItemService) {
        super(parent);
        this.workItemId = decodeRestUriComponent(workItemId);
        this.workItemService = workItemService;
    }

    /**
     * Get work item DTO of the given work item id
     *
     * @return WorkItemDTO
     * @throws GeneralException
     */
    @GET
    public WorkItemDTO getWorkItem() throws GeneralException
    {
        return this.workItemService.getWorkItemDTO();
    }

    /**
     * Patches a single work item with the provided values.
     *
     * @param values A Map containing values to update on the WorkItem
     * @return The updated workitem.
     */
    @PATCH
    public WorkItemDTO patchWorkItem(Map<String, Object> values) throws GeneralException {
        this.workItemService.patch(values);
        return this.getWorkItem();
    }

    /**
     * Forward work item to new owner
     * @param inputs Map of JSON inputs. targetIdentity is required, comments is optional.
     * @throws GeneralException
     */
    @POST
    @Path("forward")
    public SuccessResult forward(Map<String, Object> inputs) throws GeneralException {
        if (inputs == null || !inputs.containsKey("targetIdentity")) {
            throw new InvalidParameterException(new Message(MessageKeys.ERR_APPROVAL_WORK_ITEM_RESOURCE_MISSING_TARGET_IDENTITY));
        }
        SuccessResult result = new SuccessResult(true);
        String targetIdentity = (String) inputs.get("targetIdentity");
        String comment = (String) inputs.get("comment");
        WorkItemService service;

        // Let this throw on GeneralExceptions so we can pass up authorization errors
        try {
            service = getWorkItemService(workItemId, false);
        } catch (ObjectNotFoundException e) {
            throw WorkItemResourceUtil.makeFriendlyObjectNotFoundException(e);
        }

        // Catch the GeneralExceptions here so we can send back a friendly error if the user cannot forward the item
        if(service != null) {
            try {
                if (service.isCertWorkItem()) {
                    Identity newOwner = getContext().getObjectById(Identity.class, targetIdentity);
                    if (newOwner == null) {
                        throw new ObjectNotFoundException(new Message(MessageKeys.ERR_IDENTITY_NOT_FOUND));
                    }
                    service.checkSelfCertificationForward(newOwner);
                    service.checkReassignmentLimit();

                    // check if locked
                    if (ObjectUtil.isLockedById(getContext(), Certification.class,
                            service.getWorkItem().getCertification())) {
                        throw new GeneralException(new Message(Message.Type.Warn,
                                MessageKeys.CERT_LOCKED_FAIL_FORWARD));
                    }
                }
                service.forward(targetIdentity, comment);
            } catch (GeneralException e) {
                // IIQCB-3653: If a RetryableEmailException is caught at this level, that means the message was
                // already handled/logged as part of the Retry process and not additional action is required.
                if (!(e instanceof RetryableEmailException)) {
                    result.setSuccess(false);
                    result.setMessage(e.getLocalizedMessage(getLocale(), getUserTimeZone()));
                }
            }
        }
        return result;
    }

    /**
     * Cancels the specified work item.
     *
     * @return A Response.
     */
    @DELETE
    @SuppressWarnings("unchecked")
    public Response deleteWorkItem() throws GeneralException {
        WorkItem workItem = getWorkItem(this.workItemId);

        workItemService.deleteWorkItem();

        // If we get this far without throwing, return OK
        return Response.ok().build();
    }

    /**
     * Returns details of the given policy violation on the work item
     *
     * @param policyName Name of the policy
     * @param ruleName   Name of the rule
     * @return Map of policy violation details
     * @throws GeneralException
     */
    @GET
    @Path("violations/{policyName}/{ruleName}")
    public Map<String, Object> getPolicyViolationDetails(@PathParam("policyName") String policyName, @PathParam("ruleName") String ruleName)
            throws GeneralException {
        policyName = decodeRestUriComponent(policyName, false);
        ruleName = decodeRestUriComponent(ruleName, false);
        ViolationDetailer violationDetailer = this.workItemService.getViolationDetails(policyName, ruleName);
        if (violationDetailer == null) {
            throw new ObjectNotFoundException(new Message(MessageKeys.ERR_APPROVAL_WORK_ITEM_RESOURCE_NOT_FOUND_POLICY_VIOLATION));
        }

        return this.workItemService.convertPolicyViolation(violationDetailer);
    }

    /**
     * Returns a list of maps containing policy violation information for the passed workitem
     *
     * @return ListResult containing policy violation details
     * @throws GeneralException If unable to find the workitem
     */
    @GET
    @Path("violationReviews")
    public ListResult getViolationReviews() throws GeneralException {
        List<Map<String, Object>> violations = workItemService.getViolations();
        if (violations == null) {
            violations = new ArrayList<Map<String, Object>>();
        }
        return new ListResult(violations, violations.size());
    }

    /**
     * Approves or rejects the approval items for the specified work item. This submits the
     * existing work item back into workflow, and returns a ViolationReviewResult.
     *
     * @param values A Map containing violationReviewDecision, completionComments, and rejectedApprovalItems
     * @return A ViolationReviewResult with details about the policy violations.
     */
    @PATCH
    @Path("violationReviews")
    public ViolationReviewResult patchViolationReviews(Map<String, Object> values) throws GeneralException {
        WorkItem workItem = getWorkItem(workItemId);

        if (Util.isEmpty(values)) {
            throw new InvalidParameterException(new Message("Empty map not allowed."));
        }

        AccessRequestPolicyViolationDecision dto = new AccessRequestPolicyViolationDecision(workItem, values);

        //Need to get a hold of the WorkflowSession so that we can keep working with it and avoid WorkItem/WorkflowSession duplicates later
        WorkflowSession existingWfSession = (WorkflowSession) new HttpSessionStorage(getSession()).get(WorkflowSessionService.ATT_WORKFLOW_SESSION);
        // Update the WorkItem
        return workItemService.updateAccessRequestPolicyViolationDecisions(dto, existingWfSession);
    }


    /**
     * Get the identity details for a work item with the given workItemId.
     *
     * @return List<Map<String, String>> of the details for the work item's target identity.
     * @throws GeneralException
     */
    @GET
    @Path("identityDetails")
    public List<Map<String, String>> getWorkItemIdentityDetails() throws GeneralException {
        Identity targetIdentity = this.workItemService.getTargetIdentity();
        return new IdentityDetailsService(targetIdentity)
                .getIdentityDetails(getLocale(), getUserTimeZone(),
                        UIConfig.getUIConfig().getIdentityViewAttributesList());
    }

    /**
     * Returns history owner details
     *
     * @return List of owner history details.
     * @throws GeneralException
     */
    @GET
    @Path("ownerHistory")
    public List<Map<String, Object>> getWorkItemHistory() throws GeneralException {
        WorkItem workItem = getWorkItem(this.workItemId);

        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        List<WorkItem.OwnerHistory> histories = workItem.getOwnerHistory();
        if (histories != null) {
            for (WorkItem.OwnerHistory history : histories) {
                result.add(generateHistoryMap(history));
            }
        }
        return result;
    }

    /**
     * Gets the role details for the role represented by the request item.
     * Passes through to the RoleDetailsResource.
     * @return RoleDetailResource whose main getter will return a RoleDetailDTO
     * @param itemId The id of the item to get details from.
     * @throws GeneralException
     */
    @Path("item/{itemId}/roleDetails")
    public RoleDetailResource getRoleDetails(@PathParam("itemId") String itemId) throws GeneralException {
        WorkItem workItem = getWorkItem(this.workItemId);
        if (!WorkItem.Type.ViolationReview.equals(workItem.getType())) {
            throw new BadRequestException("Invalid work item type");
        }
        ApprovalWorkItemService approvalWorkItemService = new ApprovalWorkItemService(workItem.getId(), this);
        ApprovalItem item = approvalWorkItemService.findApprovalItem(itemId);
        if (item == null) {
            throw new ObjectNotFoundException(new Message("No Request Item found for ID:" + itemId));
        }

        String targetId = null;
        if (workItem.getAttribute("identityName") != null) {
            targetId = ObjectUtil.getId(getContext(), Identity.class, (String) workItem.getAttribute("identityName"));
        }
        ApprovalItemsService itemService = new ApprovalItemsService(getContext(), item);
        String roleId = (String) item.getAttribute("id");
        if (!itemService.isRole() || roleId == null) {
            throw new ObjectNotFoundException(new Message("No Role ID found for the Request Item"));
        }
        Bundle role = getContext().getObjectById(Bundle.class, roleId);
        if (role == null) {
            throw new ObjectNotFoundException(new Message("No role found for ID:" + roleId));
        }
        return new RoleDetailResource(roleId , null, targetId, this);
    }

    /**
     * Gets the managed attribute details.
     * Passes through to the ManagedAttributeDetailsResource.
     * @param itemId The id of the item to get details from.
     * @return ManagedAttributeDetailResource whose main getter will return a ManagedAttributeDetailDTO
     * @throws GeneralException
     */
    @Path("item/{itemId}/managedAttributeDetails")
    public ManagedAttributeDetailResource getManagedAttributeDetails(@PathParam("itemId") String itemId) throws GeneralException {
        WorkItem workItem = getWorkItem(this.workItemId);
        if (!WorkItem.Type.ViolationReview.equals(workItem.getType())) {
            throw new BadRequestException("Invalid work item type");
        }
        ApprovalWorkItemService approvalWorkItemService = new ApprovalWorkItemService(workItem.getId(), this);
        ApprovalItem item = approvalWorkItemService.findApprovalItem(itemId);
        if (item == null) {
            throw new ObjectNotFoundException(new Message("No Request Item found for ID:" + itemId));
        }

        ApprovalItemsService approvalItemService =  new ApprovalItemsService(getContext(), item);
        ManagedAttribute ma = approvalItemService.getAccessEntitlement();
        if (ma == null) {
            throw new ObjectNotFoundException(new Message("Unable to find matching ManagedAttribute for ID: " + itemId));
        }
        return new ManagedAttributeDetailResource(ma, this);
    }

    /**
     * Helper to create a new WorkItemService
     *
     * @param workItemId ID of the workitem
     * @return new WorkItemService
     * @throws GeneralException
     */
    private WorkItemService getWorkItemService(String workItemId) throws GeneralException {
        return getWorkItemService(workItemId, true);
    }

    /**
     * Helper to create a new WorkItemService
     *
     * @param workItemId ID of the workitem
     * @param allowRequester True to allow requester to be authorized on the work item, otherwise false.
     * @return new WorkItemService
     * @throws GeneralException
     */
    private WorkItemService getWorkItemService(String workItemId, boolean allowRequester) throws GeneralException {
        WorkItem workItem = getWorkItem(workItemId);
        return new WorkItemService(workItem, this, allowRequester);
    }

    /**
     * Generates a map of interesting items from an OwnerHistory
     *
     * @param history Item to extract details from
     * @return map of details
     */
    private Map<String, Object> generateHistoryMap(WorkItem.OwnerHistory history) {
        HashMap<String, Object> historyMap = new HashMap<String, Object>();
        historyMap.put("previousOwner", history.getOldOwnerDisplayName());
        historyMap.put("newOwner", history.getNewOwnerDisplayName());
        historyMap.put("date", Long.valueOf(history.getStartDate().getTime()));
        historyMap.put("comment", WebUtil.escapeComment(history.getComment()));
        return historyMap;
    }

    private void authWorkItem(WorkItem workItem) throws GeneralException {
        authorize(new WorkItemAuthorizer(workItem));
    }

    /**
     * Returns the WorkItem for the provided id
     * @param workItemId the id of the work item to fetch
     * @return the WorkItem
     * @throws GeneralException if no WorkItem found with WorkItemId or user is not authorized for the WorkItem
     */
    private WorkItem getWorkItem(String workItemId) throws GeneralException {
        WorkItem workItem = getContext().getObjectById(WorkItem.class, workItemId);
        if (workItem == null) {
            throw new ObjectNotFoundException(new Message(MessageKeys.ERR_WORK_ITEM_NOT_FOUND));
        }
        authWorkItem(workItem);
        return workItem;
    }
}
