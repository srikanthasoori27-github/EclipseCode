package sailpoint.rest.ui;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;

import sailpoint.authorization.AllowAllAuthorizer;
import sailpoint.authorization.CapabilityAuthorizer;
import sailpoint.authorization.CompoundAuthorizer;
import sailpoint.authorization.RightAuthorizer;
import sailpoint.integration.ListResult;
import sailpoint.object.Capability;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.SPRight;
import sailpoint.rest.BaseListResource;
import sailpoint.service.ApprovalConfigDTO;
import sailpoint.service.ApprovalDTO;
import sailpoint.service.ApprovalListService;
import sailpoint.service.ApprovalListServiceContext;
import sailpoint.service.listfilter.ListFilterDTO;
import sailpoint.service.listfilter.ListFilterService;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.ObjectNotFoundException;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;


/**
 * REST resource to provide list of approval work items
 * 
 * @author matt.tucker
 */
@Path("approvals")
public class ApprovalWorkItemListResource
    extends BaseListResource
    implements ApprovalListServiceContext {
    

    ////////////////////////////////////////////////////////////////////////////
    //
    // PARAMETERS
    //
    ////////////////////////////////////////////////////////////////////////////
    
    /**
     * Optional. ID of owner to filter work items. If not specified, will
     * query for work items owned by current logged in user.
     */
    @QueryParam("ownerId") protected String ownerId;

    
    ////////////////////////////////////////////////////////////////////////////
    //
    // PUBLIC METHODS
    //
    ////////////////////////////////////////////////////////////////////////////
    
    /**
     * List all the approval work items of the specified type of identity request.   
     * @return ListResult JSON with representations of work items with approval sets 
     * @throws GeneralException
     */
    @GET
    public ListResult getApprovals() throws GeneralException {
        validateParams();
        authorizeOwner();
        List<Filter> filters = getListFilterService().convertQueryParametersToFilters(getOtherQueryParams(), true);
        
        return getApprovalListService().getApprovals(this.query, filters);
    }
    
    /**
     * Get count of approval work items of the specified type of identity request.
     * @return Integer. Count of approval work items.
     * @throws GeneralException
     */
    @GET
    @Path("count")
    public int getApprovalsCount() throws GeneralException {
        validateParams();
        authorizeOwner();
        List<Filter> filters = getListFilterService().convertQueryParametersToFilters(getOtherQueryParams(), true);

        return getApprovalListService().getApprovalsCount(this.query, filters);
    }

    /**
     * Get approval configuration DTO objects from a specific workflowcase
     * @return List of ApprovalConfigDTO objects
     * @throws GeneralException
     */
    @GET
    @Path("approvalConfigDTOS")
    public List<ApprovalConfigDTO> getApprovalConfigDTOS() throws GeneralException {
        authorizeOwner();

        //getApprovals() is returning a ListResult which doesn't have a type, this will merit further handling on specific approvals
        List<ApprovalDTO> approvalList = getApprovals().getObjects();
        List<ApprovalConfigDTO> approvalConfigDTOList = new ArrayList<ApprovalConfigDTO>();
        if(null != approvalList) {
            Iterator iterator = approvalList.iterator();
            while(iterator.hasNext()) {
                //Retrieving an Object as some approval types are not polymorphic, for example PAMApprovalDTO is not castable to ApprovalDTO
                Object approval = iterator.next();
                //Checking if the approval is castable to ApprovalDTO, otherwise we don't process it as it may not support requiring comments
                if(approval instanceof ApprovalDTO) {
                    String wfcId = ((ApprovalDTO)approval).getWorkflowCaseId();
                    approvalConfigDTOList.add(getApprovalListService().getApprovalConfigDTO(wfcId));
                }
            }
        }

        return approvalConfigDTOList;
    }

    /**
     * Gets the list of filters available to the approval list page filter
     * panel.
     *
     * @return A list of filter DTOs
     * @throws GeneralException
     */
    @GET
    @Path(Paths.FILTERS)
    public List<ListFilterDTO> getFilterList() throws GeneralException {
        authorize(new AllowAllAuthorizer());
        return this.getListFilterService().getListFilters(true);
    }

    /**
     * Route workItemId to the ApprovalWorkItemResource class.
     * @return ApprovalWorkItemResource for the workItemId.
     * @throws GeneralException
     */
    @Path("{workItemId}") 
    public ApprovalWorkItemResource getApprovalWorkItem(@PathParam("workItemId") String workItemId) {
        return new ApprovalWorkItemResource(workItemId, this);
    }

    
    ////////////////////////////////////////////////////////////////////////////
    //
    // ApprovalListServiceContext interface
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Get the owner to filter work items. If ownerId is specified get that Identiy, otherwise
     * get logged in user.
     * 
     * @return Identity
     * @throws GeneralException
     */
    @Override
    public Identity getOwner() throws GeneralException {
        if (!Util.isNullOrEmpty(this.ownerId)) {
            return getContext().getObjectById(Identity.class, this.ownerId);
        }

        return getLoggedInUser();
    }
    
    /* (non-Javadoc)
     * @see sailpoint.rest.BaseResource#isMobileLogin()
     */
    @Override
    public boolean isMobileLogin() {
        return super.isMobileLogin();
    }

    ////////////////////////////////////////////////////////////////////////////
    //
    // PRIVATE HELPER METHODS
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Return the IdentityRequestApprovalListService to use for listing identityRequestApprovals.
     */
    private ApprovalListService getApprovalListService() {
        return new ApprovalListService(getContext(), this);
    }

    /**
     * Validate the query parameter requirements and validity
     * 
     * @throws GeneralException
     */
    private void validateParams()
            throws GeneralException {

        // If ownerId was provided, ensure the identity exists
        if (!Util.isNullOrEmpty(this.ownerId) && getOwner() == null) {
            throw new ObjectNotFoundException(new Message(MessageKeys.ERR_APPROVAL_WORK_ITEM_LIST_RESOURCE_OWNER_NOT_FOUND));
        }
    }

    /**
     * Authorize that the logged in user can query for work items by specified owner
     * 
     * @throws GeneralException
     */
    private void authorizeOwner() throws GeneralException {
        // if ownerId query parameter is not specified, we will just get the work items owned by logged in user, 
        // so no authorization is required.
        if (this.ownerId != null) {
            if (!this.ownerId.equals(getLoggedInUser().getId()) &&
                !getLoggedInUser().isInWorkGroup(getOwner())) {
                authorize(CompoundAuthorizer.or(
                        new RightAuthorizer(SPRight.FullAccessWorkItems), 
                        new CapabilityAuthorizer(Capability.SYSTEM_ADMINISTRATOR)));
            }
        }
    }

    /**
     * Create a ListFilterService.
     */
    private ListFilterService getListFilterService() {
        ApprovalWorkItemListFilterContext filterContext = new ApprovalWorkItemListFilterContext();
        return new ListFilterService(getContext(), getLocale(), filterContext);
    }
}
