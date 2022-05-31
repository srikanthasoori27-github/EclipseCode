package sailpoint.rest.ui;

import java.util.List;
import java.util.Map;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.core.Response;

import sailpoint.api.AuthenticationFailureException;
import sailpoint.api.ValidationException;
import sailpoint.authorization.UnauthorizedAccessException;
import sailpoint.connector.ExpiredPasswordException;
import sailpoint.object.ElectronicSignature;
import sailpoint.object.WorkItem;
import sailpoint.rest.BadRequestDTO;
import sailpoint.rest.BaseResource;
import sailpoint.rest.HttpSessionStorage;
import sailpoint.rest.jaxrs.PATCH;
import sailpoint.rest.ui.workitems.WorkItemResourceUtil;
import sailpoint.service.ApprovalException;
import sailpoint.service.ApprovalListService;
import sailpoint.service.ApprovalValidationException;
import sailpoint.service.ApprovalWorkItemService;
import sailpoint.service.MessageDTO;
import sailpoint.service.WorkItemResult;
import sailpoint.tools.GeneralException;
import sailpoint.tools.InvalidParameterException;
import sailpoint.tools.Message;
import sailpoint.tools.ObjectNotFoundException;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.workitem.WorkItemDTO;

public class ApprovalWorkItemResource extends BaseResource {

    private String workItemId;
    private ApprovalWorkItemService approvalWorkItemService;


    /**
     * Create an approval work item resource for the given work item id.
     * @param  workItemId  The name or ID of the application.
     * @param  parent       The parent of this subresource.
     */
    public ApprovalWorkItemResource(String workItemId, BaseResource parent) {
        super(parent);
        this.workItemId = decodeRestUriComponent(workItemId);
    }


    ////////////////////////////////////////////////////////////////////////////
    //
    // RETRIEVE A SINGLE APPROVAL WORK ITEM
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Return the approval for this endpoint.
     *
     * @return An ApprovalDTO for the approval work item represented by this endpoint.
     */
    @GET
    public WorkItemDTO getApproval() throws GeneralException {
        getService().authorize();
        WorkItem workItem = getContext().getObjectById(WorkItem.class, this.workItemId);
        if (null == workItem) {
            throw new ObjectNotFoundException(WorkItem.class, this.workItemId);
        }

        ApprovalListService svc = new ApprovalListService(this);
        return svc.getApproval(this.workItemId);
    }


    ////////////////////////////////////////////////////////////////////////////
    //
    // APPROVAL ITEM ENDPOINTS
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Get the list resource for approval items.
     *
     * @return ApprovalItemListResource approval item list resource
     * @throws GeneralException
     */
    @Path("items")
    public ApprovalItemListResource getApprovalItemListResource() throws GeneralException {
        return new ApprovalItemListResource(this.workItemId, this);
    }



    ////////////////////////////////////////////////////////////////////////////
    //
    // APPROVAL ENDPOINTS
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Complete the approval work item.
     *
     * @param inputs Map from JSON request body, including signature account and password.
     *
     * @return The WorkItemResult from completing this approval.
     *
     * @throws GeneralException
     */
    @POST 
    @Path("complete")
    public WorkItemResult complete(Map<String, Object> inputs)
            throws GeneralException, ExpiredPasswordException {
        WorkItemResult result = new WorkItemResult();

        try {
            ElectronicSignature signature = getSignature(inputs);
            String workItemStateType = Util.getString(inputs, "workItemState");
            try {
                result = getService().complete(getNotary(),
                                               signature.getAccountId(),
                                               signature.getPassword(),
                                               new HttpSessionStorage(super.getSession()),
                                               workItemStateType);
            } catch (AuthenticationFailureException afe) {
                throw new UnauthorizedAccessException(MessageKeys.ESIG_POPUP_AUTH_FAILURE, afe);
            } catch (ValidationException ve) {
                result.addMessage(new MessageDTO(Message.error(ve.getMessage())));
            }
            saveSignatureAccountId(signature.getAccountId());
        } catch (ObjectNotFoundException e) {
            throw WorkItemResourceUtil.makeFriendlyObjectNotFoundException(e);
        }

        return result;
    }
    
    /**
     * Approve all approval items
     * @throws GeneralException
     */
    @POST
    @Path("approveAll")
    public void approveAllItems(Map<String, String> comments) throws GeneralException {
        try {
            ApprovalResourceUtil.CommentRequest req = new ApprovalResourceUtil.CommentRequest(comments);
            if (!Util.isNullOrEmpty(req.getComment())) {
                getService().authorize(true);
                getService().addCommentToAllApprovalItems(req.getComment(), getLoggedInUser());
            }

            getService().approveAll();
            getService().commit();
        } catch (ObjectNotFoundException e) {
            throw WorkItemResourceUtil.makeFriendlyObjectNotFoundException(e);
        }
    }

    /**
     * Reject all approval items
     * @throws GeneralException
     */
    @POST
    @Path("rejectAll")
    public void rejectAllItems(Map<String, String> comments) throws GeneralException {
        try {
            ApprovalResourceUtil.CommentRequest req = new ApprovalResourceUtil.CommentRequest(comments);
            if (!Util.isNullOrEmpty(req.getComment())) {
                getService().authorize(true);
                getService().addCommentToAllApprovalItems(req.getComment(), getLoggedInUser());
            }

            getService().rejectAll();
            getService().commit();
        } catch (ObjectNotFoundException e) {
            throw WorkItemResourceUtil.makeFriendlyObjectNotFoundException(e);
        }
    }

    /**
     * Return a list containing the comments for this approval work item, with
     * each list entry containing:
     *  - author: The displayable name of the comment author.
     *  - comment: The HTML-escaped comment.
     *  - date: A long with the date on which the comment was made.
     */
    @GET
    @Path("comments")
    public List<Map<String,Object>> getComments() throws GeneralException {
        // Requesters should be able to view comments.
        getService().authorize(true);
        return ApprovalResourceUtil.generateComments(getWorkItem().getComments());
    }

    /**
     * Add a comment to this approval work item and send notifications.
     *
     * @param  comments  A Map containing a single "comment" attribute.
     *
     * @return A map representation of the added comment.
     */
    @POST
    @Path("comments")
    public Map<String,Object> addComment(Map<String,String> comments)
        throws GeneralException {

        // Requesters should be able to add comments.
        getService().authorize(true);
        ApprovalResourceUtil.CommentRequest req = new ApprovalResourceUtil.CommentRequest(comments);
        return ApprovalResourceUtil.generateComment(getService().addComment(req.getComment()));
    }

    /**
     * Set values on the work item from the values map.  Only values supported
     * by WorkItemService will be allowed here. 
     * @param values Map of string/value pairs for new values
     * @throws GeneralException
     */
    @PATCH
    public Response patch(Map<String, Object> values) throws GeneralException {
        try {
            getService().patch(values);
        } catch (ApprovalValidationException validationException) {
            BadRequestDTO dto = new BadRequestDTO("validation", validationException.getValidationErrors());
            return Response.status(Response.Status.BAD_REQUEST).entity(dto).build();
        } catch (ApprovalException ex) {
            return Response.status(Response.Status.BAD_REQUEST).entity(ex.getMessages()).build();
        }
        
        return Response.ok().build();
    }

    /**
     * Assign approval work item to new workgroup member
     * @param inputs Map of JSON inputs. targetIdentity is required.
     * @throws GeneralException
     */
    @POST
    @Path("assign")
    public void assign(Map<String, Object> inputs) throws GeneralException {
        if (inputs == null || !inputs.containsKey("targetIdentity")) {
            throw new InvalidParameterException(new Message(MessageKeys.ERR_APPROVAL_WORK_ITEM_RESOURCE_MISSING_TARGET_IDENTITY));
        }
        String targetIdentity = (String)inputs.get("targetIdentity");

        try {
            getService().assign(targetIdentity);
        } catch (ObjectNotFoundException e) {
            throw WorkItemResourceUtil.makeFriendlyObjectNotFoundException(e);
        }
    }


    ////////////////////////////////////////////////////////////////////////////
    //
    // HELPER METHODS
    //
    ////////////////////////////////////////////////////////////////////////////

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
     * Get an instance of an ApprovalWorkItemService
     * @return ApprovalWorkItemService
     * @throws GeneralException
     */
    private ApprovalWorkItemService getService() throws GeneralException {
        if (this.approvalWorkItemService == null) {
            this.approvalWorkItemService = new ApprovalWorkItemService(this.workItemId, this, new HttpSessionStorage(getSession()));
        }
        return this.approvalWorkItemService;
    }

}
