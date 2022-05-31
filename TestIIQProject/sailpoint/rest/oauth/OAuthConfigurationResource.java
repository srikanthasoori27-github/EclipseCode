/* (c) Copyright 2016 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.rest.oauth;

import java.util.Map;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.authorization.RightAuthorizer;
import sailpoint.object.SPRight;
import sailpoint.rest.BaseResource;
import sailpoint.service.oauth.OAuthConfigurationDTO;
import sailpoint.service.oauth.OAuthConfigurationService;
import sailpoint.tools.JsonHelper;

/**
 * This oauth configuration resource has been built to retrieve and update oauth configuration.
 * 
 * @author danny.feng
 *
 */
@Consumes(MediaType.WILDCARD)
@Produces(MediaType.APPLICATION_JSON)
@Path("oauth2/configuration")
public class OAuthConfigurationResource  extends BaseResource {

    private static final Log log = LogFactory.getLog(OAuthConfigurationResource.class);

    
    @GET  
    public Response get() throws Exception {
        authorize(new RightAuthorizer(SPRight.FullAccessOAuthClientConfiguration, SPRight.ViewOAuthClientConfiguration));
        
        OAuthConfigurationDTO result = getService().get();
        
        return Response.ok().entity(result).build();
    }
    
    @PUT
    public Response update(Map<String,Object> data) throws Exception {
        authorize(new RightAuthorizer(SPRight.FullAccessOAuthClientConfiguration));
        try {
            String json = JsonHelper.toJson(data);
            OAuthConfigurationDTO configDto = JsonHelper.fromJson(OAuthConfigurationDTO.class, json);
  
            configDto = getService().update(configDto);
            if (configDto != null) {
                return Response.ok(configDto).build();
            } else {
                log.warn("Unable to update OAuthConfiguration:" + data);
                return Response.serverError().build();
            }
        } catch(Exception e) {
            log.warn("Unable to create OAuthClient:" + data, e);
            return Response.serverError().build();
        }
    }
    
    private OAuthConfigurationService getService() {
        return new OAuthConfigurationService(this.getContext());
    }
    
}