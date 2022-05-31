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

import javax.ws.rs.POST;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.UriInfo;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Resource to handle selected identity for identity operation
 */
public class IdentityOperationsSelectedIdentityListResource extends BaseIdentityListResource {
    private static Log log = LogFactory.getLog(IdentityOperationsSelectedIdentityListResource.class);

    public IdentityOperationsSelectedIdentityListResource(BaseResource parent) {
        super(parent);
    }
    
    /**
     * Returns a list of identities requested. Used to get identity info for selected identities.
     *
     * Lets the IdentityListService do all of the heavy lifting.
     *
     * @return ListResult JSON with representations of identities
     */
    @POST
    public ListResult getSelectedIdentities(@Context UriInfo uriInfo, Map<String, Object> input) throws GeneralException
    {
        authorize(CompoundAuthorizer.or(new CapabilityAuthorizer(Capability.IDENTITY_OPERATIONS_ADMIN), new RightAuthorizer(SPRight.FullAccessTerminateIdentity)));

        if(log.isDebugEnabled()) {
            log.debug("Full Query Params: " + uriInfo.getQueryParameters() + " and POST body: " + input);
        }

        List idList = input != null ? (List)input.get(PARAM_ID) : new ArrayList();
        IdentityListService service = createIdentityListService(IdentityOperationsIdentityListResource.COLUMNS_KEY);

        return service.getSelectedIdentities(idList, null);
    }
    
}