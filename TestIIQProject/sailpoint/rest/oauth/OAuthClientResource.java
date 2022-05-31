/* (c) Copyright 2016 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.rest.oauth;

import java.util.Map;

import javax.ws.rs.DELETE;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.authorization.RightAuthorizer;
import sailpoint.object.SPRight;
import sailpoint.rest.BaseResource;
import sailpoint.service.oauth.OAuthClientDTO;
import sailpoint.service.oauth.OAuthClientService;
import sailpoint.tools.JsonHelper;

/**
 * This oauth client resource has been built to update oauth client.
 * 
 * @author danny.feng
 *
 */
public class OAuthClientResource  extends BaseResource {

    private static final Log log = LogFactory.getLog(OAuthClientResource.class);

    private String clientId;
    
    public OAuthClientResource(OAuthClientListResource parent, String clientId) {
        super(parent);
        this.clientId = clientId;
    }
    
    @PUT
    public Response update(Map<String,Object> data) throws Exception {
        authorize(new RightAuthorizer(SPRight.FullAccessOAuthClientConfiguration));
        
        try {
            //JSON can't deserialize string date to Date, unless modify the DateObjectFactory.
            //Since createDate is not updated, just remove it.
            data.remove(OAuthClientDTO.PARAM_CREATE_DATE);
            
            String json = JsonHelper.toJson(data);
            OAuthClientDTO clientDto = JsonHelper.fromJson(OAuthClientDTO.class, json);
            
            OAuthClientService service = getService();
            if (service.getClient(clientId) == null) {
                return Response.status(Status.NOT_FOUND).entity(OAuthClientService.MSG_CLIENT_NOT_FOUND).build();
            }
            
            if (!service.isNameUnique(clientId, clientDto.getName())) {
                return Response.status(Status.CONFLICT).entity(OAuthClientService.MSG_CLIENT_NAME_NOT_UNIQUE).build();
            }
            
            //use the clientId in the path param
            OAuthClientDTO client = service.updateClient(clientId, clientDto);
            return Response.ok(client).build();
        } catch(Exception e) {
            log.warn("Unable to update OAuthClient:" + data, e);
            return Response.status(Status.BAD_REQUEST).entity(e.getMessage()).build();
        }
    }
    
    @DELETE
    public Response delete() throws Exception {
        authorize(new RightAuthorizer(SPRight.FullAccessOAuthClientConfiguration));
        
        OAuthClientService service = getService();
        if (service.getClient(clientId) == null) {
            return Response.status(Status.NOT_FOUND).build();
        }
        
        service.deleteClient(clientId);
        return Response.ok().build();
    }
    
    @POST
    @Path("/regenerateKeys")
    public Response regenerateKeys() throws Exception {
        authorize(new RightAuthorizer(SPRight.FullAccessOAuthClientConfiguration));
        
        OAuthClientService service = getService();
        if (service.getClient(clientId) == null) {
            return Response.status(Status.NOT_FOUND).build();
        }
        
        service.regenerateKeys(clientId);
        return Response.ok().build();
    }
    
    private OAuthClientService getService() {
        return new OAuthClientService(this.getContext());
    }    

}