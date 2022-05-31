
/* (c) Copyright 2016 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.rest.plugin;

import sailpoint.api.SailPointContext;
import sailpoint.api.SailPointFactory;
import sailpoint.object.Identity;
import sailpoint.rest.AuthenticationFilter;
import sailpoint.rest.BaseOAuthRestFilter;
import sailpoint.tools.GeneralException;

import javax.annotation.Priority;
import javax.ws.rs.Priorities;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.ResourceInfo;
import javax.ws.rs.core.Context;
import javax.ws.rs.ext.Provider;
import java.io.IOException;
import java.lang.reflect.Method;

import org.glassfish.jersey.server.wadl.internal.WadlResource;

/**
 * Filter that is responsible for handling authorization of
 * plugin REST resource endpoints.
 *
 * @author Dustin Dobervich <dustin.dobervich@sailpoint.com>
 */
@Provider
@Priority(Priorities.AUTHORIZATION)
public class PluginAuthorizationFilter implements ContainerRequestFilter {

    /**
     * The resource information used to find annotations on the resource class and method.
     */
    @Context
    ResourceInfo resourceInfo;

    /**
     * {@inheritDoc}
     */
    @Override
    public void filter(ContainerRequestContext containerRequestContext) throws IOException {
        Class<?> cls = resourceInfo.getResourceClass();
        Method method = resourceInfo.getResourceMethod();
        // If attempting to generate WADL using Jersey resource, let through without checking any annotations.
        if (!WadlResource.class.equals(cls)) {
            PluginResourceUtil.authorizeEndpoint(getLoggedInUser(containerRequestContext), cls, method);
        }
    }

    /**
     * Gets the Identity object for the currently logged in user.
     *
     * @param containerRequestContext The container request context.
     * @return The identity.
     */
    private Identity getLoggedInUser(ContainerRequestContext containerRequestContext) {
        try {
            SailPointContext context = getContext();

            Identity identity = context.getObjectByName(Identity.class, context.getUserName());
            if (identity == null) {
                // in the basic auth scenario the user name on the context is meaningless and we
                // must look at the attribute set on the request for the authenticated name
                Object basicAuthPrincipal = containerRequestContext.getProperty(AuthenticationFilter.ATT_IDENTITY_NAME);
                if(basicAuthPrincipal == null) {
                    basicAuthPrincipal = containerRequestContext.getProperty(BaseOAuthRestFilter.ATT_OAUTH_IDENTITY_NAME);
                }
                if (basicAuthPrincipal != null) {
                    identity = context.getObjectByName(Identity.class, basicAuthPrincipal.toString());
                }
            }

            return identity;
        } catch (GeneralException e) {
            return null;
        }
    }

    /**
     * Gets the SailPointContext for the current thread.
     *
     * @return The context.
     */
    private SailPointContext getContext() {
        try {
            return SailPointFactory.getCurrentContext();
        } catch (Throwable e) {
            throw new IllegalStateException(e);
        }
    }

}
