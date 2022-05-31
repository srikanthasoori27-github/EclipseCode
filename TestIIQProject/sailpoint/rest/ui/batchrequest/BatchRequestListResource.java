/* (c) Copyright 2018 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.rest.ui.batchrequest;

import javax.ws.rs.Path;
import javax.ws.rs.PathParam;

import sailpoint.rest.BaseListResource;
import sailpoint.service.BaseListServiceContext;

/**
 * This is a very simple stub class to expose a single batch request. If needed add a @Get here and 
 * implement the list of batch requests endpoint.
 */
@Path("batchRequests")
public class BatchRequestListResource extends BaseListResource implements BaseListServiceContext {

    @Path("{batchRequestId}")
    public BatchRequestResource getBatchRequest(@PathParam("batchRequestId") String batchRequestId) {
        return new BatchRequestResource(batchRequestId, this);
    }
}
