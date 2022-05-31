/*
 * (c) Copyright 2017. SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.rest.ui;

import sailpoint.api.IdentityService;
import sailpoint.authorization.LcmActionAuthorizer;
import sailpoint.integration.ListResult;
import sailpoint.rest.ui.identities.RoleDetailResource;
import sailpoint.object.*;
import sailpoint.rest.BaseResource;
import sailpoint.rest.HttpSessionStorage;
import sailpoint.rest.jaxrs.PATCH;
import sailpoint.rest.ui.managedattribute.ManagedAttributeDetailResource;
import sailpoint.rest.ui.workitems.WorkItemResourceUtil;
import sailpoint.service.ApprovalItemsService;
import sailpoint.service.ApprovalWorkItemService;
import sailpoint.service.RolesService;
import sailpoint.service.identity.TargetAccountDTO;
import sailpoint.tools.GeneralException;
import sailpoint.tools.InvalidParameterException;
import sailpoint.tools.Message;
import sailpoint.tools.ObjectNotFoundException;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Resource for working with approval items.
 */
public class ApprovalItemResource extends BaseResource {

    private static final String APPROVAL_ITEM_PATCH_SUNRISE = "sunrise";
    private static final String APPROVAL_ITEM_PATCH_SUNSET = "sunset";

    private String itemId;
    private String workItemId;
    private ApprovalWorkItemService approvalWorkItemService;
    private Identity targetIdentity;


    /**
     * Constructor
     * @param parent resource parent
     * @param itemId approval item id
     * @throws GeneralException
     */
    public ApprovalItemResource(BaseResource parent, String workItemId, String itemId) throws GeneralException {
        super(parent);

        if (itemId == null) {
            throw new InvalidParameterException("itemId");
        }
        if (workItemId == null) {
            throw new InvalidParameterException("workItemId");
        }

        this.itemId = itemId;
        this.workItemId = workItemId;
    }

    /**
     * Approve a single approval item
     * @throws GeneralException
     */
    @POST
    @Path("approve")
    public void approveItem(Map<String, String> comments) throws GeneralException {
        try {
            ApprovalResourceUtil.CommentRequest req = new ApprovalResourceUtil.CommentRequest(comments);
            if(Util.isNotNullOrEmpty(req.getComment())) {
                getApprovalWorkItemService().authorize(true);
                ApprovalItemsService svc = getApprovalItemsService(itemId);
                svc.addComment(getWorkItem(), getLoggedInUser(), req.getComment());
            }

            getApprovalWorkItemService().approveItem(itemId);
            getApprovalWorkItemService().commit();
        } catch (ObjectNotFoundException e) {
            throw WorkItemResourceUtil.makeFriendlyObjectNotFoundException(e);
        }
    }

    /**
     * Reject a single approval item
     * @throws GeneralException
     */
    @POST
    @Path("reject")
    public void rejectItem(Map<String, String> comments) throws GeneralException {
        try {
            ApprovalResourceUtil.CommentRequest req = new ApprovalResourceUtil.CommentRequest(comments);
            if(Util.isNotNullOrEmpty(req.getComment())) {
                getApprovalWorkItemService().authorize(true);
                ApprovalItemsService svc = getApprovalItemsService(itemId);
                svc.addComment(getWorkItem(), getLoggedInUser(), req.getComment());
            }

            getApprovalWorkItemService().rejectItem(itemId);
            getApprovalWorkItemService().commit();
        } catch (ObjectNotFoundException e) {
            throw WorkItemResourceUtil.makeFriendlyObjectNotFoundException(e);
        }
    }

    /**
     * Undo decision on a single approval item
     * @throws GeneralException
     */
    @POST
    @Path("undo")
    public void undoItem() throws GeneralException {
        try {
            getApprovalWorkItemService().undoItem(itemId);
            getApprovalWorkItemService().commit();
        } catch (ObjectNotFoundException e) {
            throw WorkItemResourceUtil.makeFriendlyObjectNotFoundException(e);
        }
    }

    /**
     * Get target account details for role approvals
     * @return List of JSON maps with account details.
     * @throws GeneralException
     */
    // todo rshea: this should go away once we wire in role details
    @GET
    @Path("targetAccounts")
    public List<Map<String, String>> getTargetAccounts() throws GeneralException {
        List<Map<String, String>> result;

        try {
            List<TargetAccountDTO> targetAccounts = getApprovalWorkItemService().getTargetAccounts(itemId, getTargetIdentity());
            result = convertTargetAccounts(targetAccounts);
        } catch (ObjectNotFoundException e) {
            throw WorkItemResourceUtil.makeFriendlyObjectNotFoundException(e);
        }

        return result;
    }

    /**
     * Get the comments (include the requester comment) for the requested
     * approval item.  Each comment contains:
     *  - author: The display name of the author.
     *  - comment: The comment text.
     *  - date: The date (as a long) that the comment was made.
     *
     * @return A list of maps containing the comments.
     */
    @GET
    @Path("comments")
    public List<Map<String,Object>> getItemComments()
            throws GeneralException {

        List<Map<String,Object>> comments = null;

        // Requesters should be able to view item comments.
        getApprovalWorkItemService().authorize(true);
        try {
            ApprovalItemsService svc = getApprovalItemsService(itemId);
            comments = ApprovalResourceUtil.generateComments(svc.getComments(getWorkItem()));
        }
        catch (ObjectNotFoundException e) {
            throw WorkItemResourceUtil.makeFriendlyObjectNotFoundException(e);
        }

        return comments;
    }

    /**
     * Add a comment to the given approval item and send notifications.
     *
     * @param  comments        A Map containing a single "comment" attribute.
     *
     * @return A map representation of the added comment.
     */
    @POST
    @Path("comments")
    public Map<String,Object> addItemComment(Map<String,String> comments)
            throws GeneralException {

        // Requesters should be able to add item comments.
        getApprovalWorkItemService().authorize(true);
        ApprovalResourceUtil.CommentRequest req = new ApprovalResourceUtil.CommentRequest(comments);
        ApprovalItemsService svc = getApprovalItemsService(itemId);

        Comment added =
                svc.addComment(getWorkItem(), getLoggedInUser(), req.getComment());
        return ApprovalResourceUtil.generateComment(added);
    }

    /**
     * Patch values on the approval item.
     * @param values Map containing values to patch.
     *
     * @throws GeneralException
     */
    @PATCH
    public void patchApprovalItem(Map<String, Object> values)
            throws GeneralException {

        getApprovalWorkItemService().authorizeActions();

        if (Util.isEmpty(values)) {
            throw new InvalidParameterException(new Message(MessageKeys.ERR_APPROVAL_WORK_ITEM_RESOURCE_INVALID_PATCH_VALUES));
        }

        /* We can't "patch" an ApprovalItem since it is not a SailPointObject, so
         * instead pull out the interesting info from inputs map and set values directly
         */
        try {
            boolean datesSet = false;
            for (String valueKey : values.keySet()) {
                // Always set both sunrise and sunset if either is in values map
                if (APPROVAL_ITEM_PATCH_SUNRISE.equals(valueKey) || APPROVAL_ITEM_PATCH_SUNSET.equals(valueKey)) {
                    if (!datesSet) {
                        Date sunrise = getDate(values.get(APPROVAL_ITEM_PATCH_SUNRISE));
                        Date sunset = getDate(values.get(APPROVAL_ITEM_PATCH_SUNSET));

                        ApprovalItemsService approvalItemsService = getApprovalItemsService(itemId);

                        //IIQETN-6098 :- Updating the sunrise/sunset for each work item that shares the same approval item id.
                        QueryOptions ops = new QueryOptions();
                        ops.addFilter(Filter.eq("identityRequestId", getApprovalWorkItemService().getWorkItem().getIdentityRequestId()));
                        List<WorkItem> irWorkItems = getContext().getObjects(WorkItem.class, ops);
                        for (WorkItem workItem : irWorkItems) {
                            ApprovalItem itemToEdit = approvalItemsService.findApprovalItem(workItem, itemId);
                            if (itemToEdit != null) {
                                approvalItemsService.setSunriseSunset(workItem, sunrise, sunset);
                                datesSet = true;
                            }
                        }
                    }
                } else {
                    throw new GeneralException("Field " + valueKey + " cannot be patched");
                }
            }
        } catch (InvalidParameterException e) {
            throw new InvalidParameterException(new Message(MessageKeys.ERR_APPROVAL_WORK_ITEM_RESOURCE_INVALID_PATCH_VALUES), e);
        }
    }

    /**
     * Gets the simple entitlements associated with the role in the approval item.
     *
     * @return ListResult containing simple entitlements for roles on approval item.
     * @throws GeneralException
     */
    @GET
    @Path("simpleEntitlements")
    public ListResult getSimpleEntitlements() throws GeneralException {

        //IIQETN-4496 :- Since we are showing "Entitlements tab" details while
        //requesting a role, we should allow the requester to have access to the
        //same tab while seeing work item details.
        getApprovalWorkItemService().authorize(true);

        // Get the necessary services
        ApprovalItemsService approvalItemService = getApprovalItemsService(itemId);
        RolesService service = new RolesService(this);

        String identityRequestId = getApprovalWorkItemService().getWorkItem().getIdentityRequestId();
        Bundle role = approvalItemService.getAccessRole(identityRequestId);
        if(role != null && role.getId() != null) {
            return service.getAllSimpleEntitlements(role.getId());
        }

        return new ListResult(Collections.emptyMap());
    }

    /**
     * Gets the roles details associated with the role in the approval item.
     *
     * @return ListResult containing simple entitlements for roles on approval item.
     * @throws GeneralException
     */
    @Path("roleDetails")
    public RoleDetailResource getRoleDetails() throws GeneralException {
        ApprovalWorkItemService workItemService = getApprovalWorkItemService();

        ApprovalItem approvalItem = workItemService.findApprovalItem(itemId);
        if (approvalItem == null) {
            throw new ObjectNotFoundException(ApprovalItem.class, itemId);
        }

        String assignmentId = approvalItem.getAssignmentId();
        String identityId = workItemService.getTargetIdentity().getId();
        IdentityRequest identityRequest = workItemService.getIdentityRequest();
        String identityRequestId = getApprovalWorkItemService().getWorkItem().getIdentityRequestId();
        Bundle role = getApprovalItemsService(this.itemId).getAccessRole(identityRequestId);
        String roleId = role.getId();

        return new RoleDetailResource(roleId, assignmentId, identityId, identityRequest.getId(), approvalItem, this);
    }

    /**
     * Gets the managed attribute details.
     * Passes through to the ManagedAttributeDetailsResource.
     * @return ManagedAttributeDetailResource whose main getter will return a ManagedAttributeDetailDTO
     * @throws GeneralException
     */
    @Path("managedAttributeDetails")
    public ManagedAttributeDetailResource getManagedAttributeDetails() throws GeneralException {
        authorize(new LcmActionAuthorizer(QuickLink.LCM_ACTION_REQUEST_ACCESS));
        return new ManagedAttributeDetailResource(getApprovalItemManagedAttribute(), this);
    }

    /**
     * Get the access item represented by the ID, assuming it's a ManagedAttribute.
     * @return ManagedAttribute
     * @throws GeneralException if accessItemId does not refer to a valid ManagedAttribute
     */
    private ManagedAttribute getApprovalItemManagedAttribute() throws GeneralException {
        ApprovalItemsService approvalItemService = getApprovalItemsService(itemId);

        ManagedAttribute ma = approvalItemService.getAccessEntitlement();
        if (ma == null) {
            throw new ObjectNotFoundException(new Message("Unable to find matching ManagedAttribute for ID: " + this.itemId));
        }
        return ma;
    }

    /**
     * Get an instance of an ApprovalWorkItemService
     * @return ApprovalWorkItemService
     * @throws GeneralException
     */
    private ApprovalWorkItemService getApprovalWorkItemService() throws GeneralException {
        if (this.approvalWorkItemService == null) {
            this.approvalWorkItemService = new ApprovalWorkItemService(this.workItemId, this, new HttpSessionStorage(getSession()));
        }
        return this.approvalWorkItemService;
    }

    /**
     * Convert a TargetAccount object to a map
     * @param targetAccounts List of TargetAccount objectsgetViolationDetails
     * @return Map with TargetAccount data in the desired key/value pairs
     */
    private List<Map<String, String>> convertTargetAccounts(List<TargetAccountDTO> targetAccounts) {
        List<Map<String, String>> targetAccountMaps = new ArrayList<Map<String, String>>();
        for (TargetAccountDTO targetAccount: targetAccounts) {
            Map<String, String> targetAccountMap = new HashMap<String, String>();
            String role = targetAccount.getSourceRole();
            if (null == role) {
                role = targetAccount.getValue();
            }
            targetAccountMap.put("role", role);
            targetAccountMap.put("application", targetAccount.getApplication());
            targetAccountMap.put("account", targetAccount.getAccount());
            targetAccountMaps.add(targetAccountMap);
        }
        return targetAccountMaps;
    }

    /**
     * Get the target identity for the work item
     * @return Identity that this approval work item targets
     * @throws GeneralException
     */
    private Identity getTargetIdentity() throws GeneralException {
        if (this.targetIdentity == null) {
            WorkItem workItem = getApprovalWorkItemService().getWorkItem();
            /* Since this is for Approval work items, the target id/name will
             * always refer to an identity */
            IdentityService svc = new IdentityService(getContext());
            this.targetIdentity = svc.getIdentityFromIdOrName(workItem.getTargetId(), workItem.getTargetName());
        }

        return this.targetIdentity;
    }

    /**
     * Return the ApprovalItemsService for the requested approval item.
     *
     * @param  approvalItemId  The approval item ID.
     *
     * @throws ObjectNotFoundException  If the approval or item is not found.
     */
    private ApprovalItemsService getApprovalItemsService(String approvalItemId)
            throws GeneralException, ObjectNotFoundException {

        ApprovalItem approvalItem = getApprovalWorkItemService().findApprovalItem(approvalItemId);
        if (approvalItem == null) {
            throw new ObjectNotFoundException(ApprovalItem.class, approvalItemId);
        }

        return new ApprovalItemsService(getContext(), approvalItem);
    }

    /**
     * Return the WorkItem that this resource is servicing.
     */
    private WorkItem getWorkItem() throws GeneralException {
        WorkItem item = getContext().getObjectById(WorkItem.class, this.workItemId);
        if (null == item) {
            throw new ObjectNotFoundException(new Message(MessageKeys.ERR_APPROVAL_WORK_ITEM_RESOURCE_NOT_FOUND_APPROVAL));
        }
        return item;
    }

    /**
     * Convert a value to a date, checking that it is a long
     * @param value Value from the patch map. Should be null or a long
     * @return Date object, or null
     * @throws GeneralException if not a long type
     */
    private Date getDate(Object value) throws GeneralException {
        if (value == null) {
            return null;
        }
        if (!(value instanceof Long)) {
            throw new GeneralException("Value must be type 'long'");
        }
        return new Date((Long)value);
    }


}
