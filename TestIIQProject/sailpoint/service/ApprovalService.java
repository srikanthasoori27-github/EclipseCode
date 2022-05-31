/* (c) Copyright 2015 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.service;

import sailpoint.api.SailPointContext;
import sailpoint.object.IdentityRequest;
import sailpoint.object.WorkItem;
import sailpoint.tools.GeneralException;

/**
 * A service to help dealing with approvals.  The ApprovalWorkItemService is used more for editing
 * and manipulating approvals, and has authorization builtin.  Hence this separate service.
 */
public class ApprovalService {

    private SailPointContext context;


    /**
     * Constructor.
     */
    public ApprovalService(SailPointContext context) {
        this.context = context;
    }

    /**
     * Check if the work item request is an access request type
     *
     * @param workItem
     * @return true if the associated identity request is access request type
     * @throws GeneralException
     */
    public boolean isAccessRequestApproval(WorkItem workItem) throws GeneralException {
        // First check if this is an approval work item.
        if (WorkItem.Type.Approval.equals(workItem.getType())) {
            // This is an approval work item ... now check that it is a supported type.
            String identityRequestId = workItem.getIdentityRequestId();
            IdentityRequest ir = context.getObjectByName(IdentityRequest.class, identityRequestId);
            if (ir != null) {
                return true;
            }
        }

        // Not an approval or doesn't have an identity request, so return false.
        return false;
    }
}
