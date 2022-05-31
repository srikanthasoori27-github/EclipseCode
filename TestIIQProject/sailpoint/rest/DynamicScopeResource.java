/* (c) Copyright 2015 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.rest;

import java.util.Map;

import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.core.Response;

import sailpoint.authorization.RightAuthorizer;
import sailpoint.object.SPRight;
import sailpoint.service.DynamicScopeDTO;
import sailpoint.service.DynamicScopeService;
import sailpoint.tools.GeneralException;

public class DynamicScopeResource extends BaseResource {

    private String dynamicScopeId;

    public DynamicScopeResource(String dynamicScopeId,
                                DynamicScopeListResource parent) {
        super(parent);
        this.dynamicScopeId = dynamicScopeId;
    }

    /**
     * Gets the details for this dynamic scope (ipop).
     * 
     * @return String The JSON details of this dynamic scope.
     * @throws GeneralException
     */
    @GET
    public Map<String, Object> getSummaryJson() throws GeneralException {
        authorize(new RightAuthorizer(SPRight.FullAccessDynamicScope));

        DynamicScopeDTO dto = getService().getDynamicScope(dynamicScopeId);
        return dto.toMap();
    }

    /**
     * Updates the specified DynamicScope object with passed in data map.
     * @param data  the info map
     * @return Map contains map representation of the dynamicscope
     * @throws GeneralException
     */
    @PUT
    public Map<String, Object> updateDynamicScope(Map<String,Object> data) throws GeneralException {
        authorize(new RightAuthorizer(SPRight.FullAccessDynamicScope));

        DynamicScopeDTO dto = new DynamicScopeDTO(data, getContext());
        
        DynamicScopeDTO updated = getService().updateDynamicScope(dto);
        
        return updated.toMap();
    }

    /**
     * Removes the specified dynamic scope object.
     * 
     * @return Response
     * @throws GeneralException
     */
    @DELETE
    public Response deleteDynamicScope() throws GeneralException
    {
        authorize(new RightAuthorizer(SPRight.FullAccessDynamicScope));

        getService().deleteDynamicScope(dynamicScopeId);
        return Response.ok().build();
    }

    protected DynamicScopeService getService() {
        return new DynamicScopeService(getContext());
    }

}
