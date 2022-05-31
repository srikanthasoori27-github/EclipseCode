/* (c) Copyright 2009 SailPoint Technologies, Inc., All Rights Reserved. */
/**
 * A REST method for receiving password change intercept events.
 *
 * Author: Jeff
 *
 * This is a relatively simple resource that on handles POSTS.
 * It was designed for use with directory plugins to notify us of
 * password changes.  All the plugin knows is the name of the application,
 * native identity of the user, and the new password.  Correlating
 * that back to an IIQ Identity must be done by the resource method.
 *
 * The payload of the request will be a JSON Map of the form:
 *
 *    {application: "My AD Directory",
 *     identity: "cn=Some Ugly DN",
 *     password: "encrypted"}
 *
 * The application name must match the name of an Application object
 * in IIQ.  The plugin must be configured to post the expected name.
 * The identity must be the same value that will come back as the
 * native identity during aggregatino.  We look for a Link that
 * has a matching nativeIdentity property.  If there is no matching
 * Link we do NOT bootstrap an Identity, the event is ignored.
 *
 * The password may be in clear text, but it is expected to be
 * encrypted using one of the same keys IIQ has in it's key store.
 *
 * The service method simply decodes the arguments, locates the Identity
 * and then launches a workflow.  Decisions about what to do with
 * the password are made in the workfloow.
 */

package sailpoint.rest;

import java.util.Map;

import javax.ws.rs.POST;
import javax.ws.rs.Path;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.IdentityLifecycler;
import sailpoint.api.SailPointContext;
import sailpoint.authorization.RightAuthorizer;
import sailpoint.integration.IIQClient;
import sailpoint.object.SPRight;
import sailpoint.tools.GeneralException;
import sailpoint.tools.TracingAspect;

/**
 * REST methods for the "passwordChange" resource.
 *
 */
@Path(IIQClient.RESOURCE_PASSWORD_INTERCEPT)
public class PasswordInterceptResource extends BaseResource {

    private static Log log = LogFactory.getLog(PasswordInterceptResource.class);

    /**
     * Receive a password intercept event.
     * URI : /passwordIntercept
     */
    @POST 
    public String passwordIntercept(Map<String, String> post) 
        throws GeneralException {
    	
    	authorize(new RightAuthorizer(SPRight.WebServiceRights.PasswordInterceptWebService.name()));

        if (log.isInfoEnabled()) {
            // Bug #21173 - filter sensitive password data.  To reduce 
            // the total amount of potential change, I just promoted a 
            // static method in TracingAspect that already does this.
            // That method requires a non-null map, however I believe
            // so too does this method require post to be non-null, so
            // no worries there.
            log.info(TracingAspect.filterMap(post));
        }

        // TODO: may want to support instance someeday, but since
        // we're only here for directories we don't need it yet

        String appname = post.get(IIQClient.ARG_APPLICATION);        
        String nativeIdentity = post.get(IIQClient.ARG_IDENTITY);
        String password = post.get(IIQClient.ARG_PASSWORD);

        if (password == null) {
            // should we let this mean "set to null"?  feels dangerous
            log.warn("Ignoring intercept of null password");
        }
        else if (appname == null)
            log.warn("Missing application name");

        else if (nativeIdentity == null)
            log.warn("Missing native identity");
        
        else {
            // code has been moved here so we share with SMListener
            SailPointContext con = getContext();
            IdentityLifecycler cycler = new IdentityLifecycler(con);

            // let's be sure this only gets consumed where it's needed.
            post.remove(IIQClient.ARG_PASSWORD);
            
            cycler.launchPasswordIntercept(appname, nativeIdentity, password, post);
        }

        return "success";
    }
}
