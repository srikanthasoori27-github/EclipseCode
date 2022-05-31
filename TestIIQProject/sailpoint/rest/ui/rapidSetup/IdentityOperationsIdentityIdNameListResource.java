/* (c) Copyright 2020 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.rest.ui.rapidSetup;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.authorization.CapabilityAuthorizer;
import sailpoint.authorization.CompoundAuthorizer;
import sailpoint.authorization.RightAuthorizer;
import sailpoint.integration.ListResult;
import sailpoint.object.Capability;
import sailpoint.object.SPRight;
import sailpoint.rest.BaseResource;
import sailpoint.rest.ui.BaseIdentityListResource;
import sailpoint.service.IdentityListService;
import sailpoint.tools.GeneralException;

import javax.ws.rs.GET;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.UriInfo;
import java.util.Map;

/**
 * Resource to handle list of ids and names of identities that can be requested for identity operation
 */
public class IdentityOperationsIdentityIdNameListResource extends BaseIdentityListResource {
    private static Log log = LogFactory.getLog(IdentityOperationsIdentityIdNameListResource.class);

    public IdentityOperationsIdentityIdNameListResource(BaseResource parent) {
        super(parent);
    }

    /**
     * Returns a list of ids & names of identities that the current user can request for.
     * Lets the IdentityListService do all of the heavy lifting.
     *
     * Builds two big filters: A name filter based on the partial or full identity name in the "nameSearch" parameter,
     * and a session filter containing everything else.
     * @return ListResult JSON with representations of identities including id/name
     */
    @GET
    public ListResult getRequestableIdentityIdNames(@Context UriInfo uriInfo) throws GeneralException
    {
        authorize(CompoundAuthorizer.or(new CapabilityAuthorizer(Capability.IDENTITY_OPERATIONS_ADMIN), new RightAuthorizer(SPRight.FullAccessTerminateIdentity)));
        if(log.isDebugEnabled()) {
            log.debug("Full Query Params: " + uriInfo.getQueryParameters());
        }

        IdentityListService service = createIdentityListService(IdentityOperationsIdentityListResource.COLUMNS_KEY);
        /* Build the filters based off of the query params */
        Map<String, String> queryParameters = getOtherQueryParams();
        this.setupFiltersFromParams(queryParameters, service);
        return service.getAllIdNameIdentities();
    }
}