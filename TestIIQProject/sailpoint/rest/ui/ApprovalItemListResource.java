/*
 * (c) Copyright 2017. SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.rest.ui;

import sailpoint.rest.BaseListResource;
import sailpoint.rest.BaseResource;
import sailpoint.service.BaseListServiceContext;
import sailpoint.tools.GeneralException;

import javax.ws.rs.Path;
import javax.ws.rs.PathParam;

/**
 * Resource for listing approval items.
 */
public class ApprovalItemListResource extends BaseListResource implements BaseListServiceContext {

    private String workItemId;

    /**
     * Constructor
     * @param workItemId The approval work item id
     * @param parent parent resource
     * @throws GeneralException
     */
    public ApprovalItemListResource(String workItemId, BaseResource parent) throws GeneralException {
        super(parent);
        this.workItemId = workItemId;
    }

    /**
     * Get the approval item resource
     * @param itemId approval item id
     * @return ApprovalItemResource
     * @throws GeneralException
     */
    @Path("{itemId}")
    public ApprovalItemResource getApprovalItemResource(@PathParam("itemId") String itemId) throws GeneralException {
        return new ApprovalItemResource(this, workItemId, itemId);
    }
}
