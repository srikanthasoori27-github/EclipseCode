/* (c) Copyright 2018 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.rest.ui.batchrequest;

import javax.ws.rs.Path;

import sailpoint.rest.BaseResource;

/**
 * This is a very simple stub class to expose a list of batch request items endpoint.
 */
public class BatchRequestResource extends BaseResource {
    private String id;

    public BatchRequestResource(String id, BaseResource parent) {
        super(parent);
        this.id = id;
    }
    
    @Path("items")
    public BatchRequestItemsListResource getBatchRequestItems() {
        return new BatchRequestItemsListResource(id, this);
    }

}
