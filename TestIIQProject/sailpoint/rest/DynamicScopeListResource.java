/* (c) Copyright 2015 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.rest;

import java.util.Map;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;

import sailpoint.authorization.RightAuthorizer;
import sailpoint.integration.ListResult;
import sailpoint.object.SPRight;
import sailpoint.service.DynamicScopeDTO;
import sailpoint.service.DynamicScopeService;
import sailpoint.tools.GeneralException;

/**
 * @author danny.feng
 *
 */
@Path("dynamicScopes")
public class DynamicScopeListResource extends BaseListResource {

    /**
     * Return a ListResult of all DynamicScopes with name and id.
     *
     * @return A ListResult of DynamicScopes.
     */
    @GET
    public ListResult getList() throws GeneralException {
        authorize(new RightAuthorizer(SPRight.FullAccessDynamicScope));

        return getService().getDynamicScopes(getStart(), getLimit());
    }

    /** Sub Resource Methods **/
    @Path("{dynamicScopeId}")
    public DynamicScopeResource getDynamicScope(@PathParam("dynamicScopeId") String dynamicScopeId)
            throws GeneralException {
        return new DynamicScopeResource(dynamicScopeId, this);
    }

    /**
     * Creates a new DynamicScope object based on passed in data map.
     * @param data the map 
     * @return Map the map representation of newly created dynamic scope
     * @throws GeneralException
     */
    @POST
    public Map<String, Object> createDynamicScope(Map<String,Object> data) throws GeneralException {
        authorize(new RightAuthorizer(SPRight.FullAccessDynamicScope));

        DynamicScopeDTO dto = new DynamicScopeDTO(data, getContext());
        
        DynamicScopeDTO created = getService().createDynamicScope(dto);
        
        return created.toMap();
    }

    protected DynamicScopeService getService() {
        return new DynamicScopeService(getContext());
    }
}
