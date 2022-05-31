/* (c) Copyright 2009 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.rest;

import java.util.Map;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;

import sailpoint.api.SailPointContext;
import sailpoint.api.SailPointFactory;
import sailpoint.authorization.AllowAllAuthorizer;
import sailpoint.authorization.RightAuthorizer;
import sailpoint.connector.ExpiredPasswordException;
import sailpoint.integration.IIQClient;
import sailpoint.object.SPRight;
import sailpoint.service.ServiceHandler;
import sailpoint.service.RemoteLoginService;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

/**
 * REST methods for the base IIQ resource.
 *
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */
@Path("")
public class IIQResource extends BaseResource {

    /**
     * Return a ping response.
     */
    @GET @Path(IIQClient.RESOURCE_PING)
    public String ping() throws GeneralException {

        //IIQETN-6159 :- Allowing all users that are logged in to call "ping" service.
        //Since this service could be called by whoever user, we need to open this
        //service to all logged in users.
        authorize(new AllowAllAuthorizer());

        return "IIQ JAX-RS is alive!";
    }

    /**
     * Attempt authentication using the credentials for this request.
     * 
     * @return A map with information about the authentication request.
     * 
     * @see BaseResource#getCredentials()
     */
    @GET @Path(IIQClient.RESOURCE_AUTHENTICATION)
    public Map<String,String> authentication() throws GeneralException, ExpiredPasswordException {
        
        //No way to authorize this call since we don't have a logged in user.
        authorize(new AllowAllAuthorizer());
    	
        String[] credentials = super.getCredentials();
        String user = credentials[0];
        String password = credentials[1];
        
        Map<String,String> authorizationResult = null;
        if ((null != user) && (null != password)) {
            //Have to use getCurrentContext instead of getContext due to getContext calling getLoggedInUsername
            SailPointContext ctx = SailPointFactory.getCurrentContext();
            //Set the userName to the credentials passed in.
            ctx.setUserName(user);
            authorizationResult = getHandler(ctx).checkAuthentication(user, password);
            if(null != authorizationResult && authorizationResult.get(ServiceHandler.IDENTITY) != null) {
                //Need to set the ServletRequest Attribute denoting authentication occurred using the HTTP Authorization request header.
                getRequest().setAttribute(AuthenticationFilter.ATT_IDENTITY_NAME, authorizationResult.get(ServiceHandler.IDENTITY));
            }
        }

        return authorizationResult;
    }
    
    /**
     * Retrieve a SystemConfiguration attribute's value.
     * 
     * @param  attributeName  The name of the SystemConfiguration attribute.
     * 
     * @return The requested SystemConfiguration attribute's value.
     */
    @GET @Path(IIQClient.RESOURCE_CONFIGURATION)
    public Object getConfiguration(@QueryParam("attributeName") String attributeName) throws GeneralException {
    	
    	authorize(new RightAuthorizer(SPRight.WebServiceRights.GetConfigurationWebService.name()));
    	
        return getHandler().getConfiguration(attributeName);
    }

    /**
     * Returns the identity name, and its remote login token.
     * remote login tokens to allow psuedo-sso for integrations that
     * want to launch into our app given some user context instance.
     *
     * @param  Map<String, String>  The Map contains keys 'application' and
     * 'nativeIdentity' to filter by.
     * @param  host                 host name requesting remote login (optional).
     * @return A Map with details identity cube name and its remote login token.
     */
     @POST
     @Path(IIQClient.SUB_RESOURCE_REMOTE_LOGIN)
     public Map<String, String> createRemoteLogin( Map<String, String> requestMap,
                                              @QueryParam(IIQClient.PARAM_HOST) String remoteHost)
         throws Exception {

         authorize(new RightAuthorizer(SPRight.WebServiceRights.RemoteLoginWebService.name()));

         String appName = Util.getString(requestMap, "application");
         String nativeIdentity = Util.getString(requestMap, "nativeIdentity");
         if ( null == appName ||  null == nativeIdentity ) {
             throw new GeneralException("Unable to find identity");
         }

         RemoteLoginService service = new RemoteLoginService(getContext());

         return service.createRemoteLoginToken(appName, nativeIdentity, remoteHost);
     }

    //Path("checkSession")
    // "checkSession" is a pseudo-url on this REST Path. It is intercepted in SailPointPollingRequestFilter
    // to check if the session should be invalidated when the client shows the session timeout dialog
    // We do not need a real method here but documenting so we do not clobber it someday.
}
