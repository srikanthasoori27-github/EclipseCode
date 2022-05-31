/* (c) Copyright 2018 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.rest.ui.batchrequest;

import javax.ws.rs.GET;
import javax.ws.rs.QueryParam;

import sailpoint.authorization.RightAuthorizer;
import sailpoint.integration.ListResult;
import sailpoint.object.SPRight;
import sailpoint.rest.BaseListResource;
import sailpoint.rest.BaseResource;
import sailpoint.service.BaseListServiceContext;
import sailpoint.service.batchrequest.BatchRequestItemsListService;
import sailpoint.tools.GeneralException;

/**
 * Endpoint to list batch request items. The requestData attribute represents a single line in the batch request
 * file.
 */
public class BatchRequestItemsListResource extends BaseListResource implements BaseListServiceContext {
    ////////////////////////////////////////////////////////////////////////////
    //
    // FIELDS
    //
    ////////////////////////////////////////////////////////////////////////////
    
    // Common request parameters for returning lists.
    @QueryParam(PARAM_START) protected int start;
    @QueryParam(PARAM_LIMIT) protected int limit;
    @QueryParam(PARAM_SORT) protected String sortBy;

    private String batchRequestId;

    public BatchRequestItemsListResource(String batchRequestId, BaseResource parent) {
        super(parent);
        this.batchRequestId = batchRequestId;
    }
    
    @GET
    public ListResult getBatchRequestItems() throws GeneralException {
        authorize(new RightAuthorizer(SPRight.FullAccessBatchRequest));
        BatchRequestItemsListService brService = new BatchRequestItemsListService(getContext(), this);
        
        return brService.getBatchRequestItems(batchRequestId, start, limit, sortBy);
    }
}
