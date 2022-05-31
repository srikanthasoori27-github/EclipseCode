/* (c) Copyright 2014 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.rest.ui;

import java.net.URI;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.core.Response;

import sailpoint.authorization.AllowAllAuthorizer;
import sailpoint.rest.BaseResource;
import sailpoint.service.RedirectService;
import sailpoint.tools.GeneralException;

/**
 * Project: identityiq
 * Author: michael.hide
 * Created: 5/2/14 9:53 AM
 */
@Path(Paths.REDIRECT)
public class RedirectResource extends BaseResource {

    /*
     * Member variables
     */
    private URI url;
    private String hash;
    private RedirectService service;

    /**
     * Cache the service for subsequent use.
     *
     * @return The RedirectService service instance
     */
    private RedirectService getService() {
        if (this.service == null) {
            this.service = new RedirectService();
        }
        return this.service;
    }

    /**
     * Handles the redirection to the target URL and appends the specified named anchor to the path as
     * well as setting a session variable RedirectService.HASH_TOKEN to be reapplied in PageAuthFilter.
     *
     * @return
     * @throws GeneralException
     */
    @GET
    public Response redirect() throws GeneralException {

        authorize(new AllowAllAuthorizer());

        // Get the hash 'page'
        // Re-encode the hash portion since we could have troublesome characters in the values
        hash = getService().encodeURIFragmentValues(getRequest().getParameter(RedirectService.HASH_KEY));

        // Set AngularJS 'page' on the session to be reapplied in PageAuthFilter after
        // authentication.
        getSession().setAttribute(RedirectService.HASH_TOKEN, hash);

        // Format the URL
        url = getService().getRedirectURL(getRequest().getScheme(),
                getRequest().getServerName(),
                getRequest().getServerPort(),
                getRequest().getContextPath(),
                getRequest().getQueryString(),
                getRequest().getParameter(RedirectService.PAGE_KEY), hash);

        // Send a redirect
        return Response.temporaryRedirect(url).build();
    }

}

