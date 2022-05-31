/* (c) Copyright 2017. SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.rest.ui.pam;

import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;

import sailpoint.authorization.UnauthorizedAccessException;
import sailpoint.rest.BaseResource;
import sailpoint.service.WorkItemService;
import sailpoint.service.pam.PamApprovalService;
import sailpoint.tools.GeneralException;
import sailpoint.tools.ObjectNotFoundException;

/**
 * A REST resource to deal with PAM approvals.  Many of the standard approval behaviors are handled through the
 * ApprovalWorkItemResource.  This just handles things that are specific to PAM approvals.
 */
@Path("pamApprovals")
public class PamApprovalResource extends BaseResource {

    /**
     * Approve the PAM approval work item with the given ID.
     *
     * @param approvalId  The ID of the PAM approval work item.
     */
    @POST
    @Path("{approvalId}/approve")
    public void approve(@PathParam("approvalId") String approvalId) throws GeneralException {
        PamApprovalService svc = this.getServiceAndAuthorize(approvalId);
        svc.approve(approvalId);
    }

    /**
     * Reject the PAM approval work item with the given ID.
     *
     * @param approvalId  The ID of the PAM approval work item.
     */
    @POST
    @Path("{approvalId}/reject")
    public void reject(@PathParam("approvalId") String approvalId) throws GeneralException {
        PamApprovalService svc = this.getServiceAndAuthorize(approvalId);
        svc.reject(approvalId);
    }

    private PamApprovalService getServiceAndAuthorize(String approvalId)
        throws GeneralException, ObjectNotFoundException, UnauthorizedAccessException {

        // Just constructing the WorkItemService will do the 404 and authz checks for us.
        new WorkItemService(approvalId, this);

        return new PamApprovalService(getContext(), this);
    }
}
