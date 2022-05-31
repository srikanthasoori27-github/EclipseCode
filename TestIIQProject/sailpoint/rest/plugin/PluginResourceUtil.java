
/* (c) Copyright 2016 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.rest.plugin;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import sailpoint.object.Capability;
import sailpoint.object.Identity;
import sailpoint.tools.Util;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import java.lang.reflect.Method;

/**
 * Utility class containing functionality for plugin REST resources.
 *
 * @author Dustin Dobervich <dustin.dobervich@sailpoint.com>
 */
public class PluginResourceUtil {

    /**
     * The log.
     */
    private static final Log LOG = LogFactory.getLog(PluginResourceUtil.class);

    /**
     * Private constructor.
     */
    private PluginResourceUtil() {}

    /**
     * Authorizes the plugin REST resource endpoint using annotations
     * from the class and method. The endpoint must be annotated or access
     * will automatically be restricted.
     *
     * @param identity The identity of the logged in user.
     * @param cls The class.
     * @param method The method.
     */
    public static void authorizeEndpoint(Identity identity, Class<?> cls, Method method) {
        // fetch the logged in user
        if (identity == null) {
            LOG.error("Unable to find Identity for logged in user");
            throw new WebApplicationException(Response.Status.FORBIDDEN);
        }

        // assume no annotations found
        boolean hasMethodAnnotation = false;
        boolean hasClassAnnotation = false;

        // we will look most-specific to least so look at method first
        hasMethodAnnotation = authorizeMethod(method, identity);
        if (!hasMethodAnnotation) {
            // if there was no method annotation then try class
            hasClassAnnotation = authorizeClass(cls, identity);
        }

        // if no annotation found at all then halt request
        if (!hasClassAnnotation && !hasMethodAnnotation) {
            LOG.error("No authorization annotation found on plugin resource");
            throw new WebApplicationException(Response.Status.FORBIDDEN);
        }
    }

    /**
     * Authorizes the identity using any class annotations.
     *
     * @param cls The class.
     * @param identity The identity.
     * @return True if a plugin authorization annotation was found on the class, false otherwise.
     */
    private static boolean authorizeClass(Class<?> cls, Identity identity) {
        AllowAll allowAll = cls.getAnnotation(AllowAll.class);
        SystemAdmin systemAdmin = cls.getAnnotation(SystemAdmin.class);
        RequiredRight requiredRight = cls.getAnnotation(RequiredRight.class);

        if (systemAdmin != null) {
            verifySystemAdmin(identity);
        }

        if (requiredRight != null) {
            verifyHasRight(requiredRight.value(), identity);
        }

        return allowAll != null || systemAdmin != null || requiredRight != null;
    }

    /**
     * Authorizes the identity using any method annotations.
     *
     * @param method The method.
     * @param identity The identity.
     * @return True if a plugin authorization annotation was found on the method, false otherwise.
     */
    private static boolean authorizeMethod(Method method, Identity identity) {
        AllowAll allowAll = method.getAnnotation(AllowAll.class);
        Deferred deferred = method.getAnnotation(Deferred.class);
        SystemAdmin systemAdmin = method.getAnnotation(SystemAdmin.class);
        RequiredRight requiredRight = method.getAnnotation(RequiredRight.class);

        if (systemAdmin != null) {
            verifySystemAdmin(identity);
        }

        if (requiredRight != null) {
            verifyHasRight(requiredRight.value(), identity);
        }

        return allowAll != null || requiredRight != null ||
               systemAdmin != null || deferred != null;
    }

    /**
     * Verifies that the identity is a system administrator.
     *
     * @param identity The identity.
     */
    private static void verifySystemAdmin(Identity identity) {
        if (!isSystemAdmin(identity)) {
            throw new WebApplicationException(Response.Status.FORBIDDEN);
        }
    }

    /**
     * Verifies that the identity has the specified right.
     *
     * @param right The right.
     * @param identity The identity.
     */
    private static void verifyHasRight(String right, Identity identity) {
        if (!Util.isNullOrEmpty(right)) {
            // first check for sys admin
            if (!isSystemAdmin(identity)) {
                // next check for the right
                if (!identity.getCapabilityManager().hasRight(right)) {
                    throw new WebApplicationException(Response.Status.FORBIDDEN);
                }
            }
        } else {
            LOG.error("No right specified on RequiredRight annotation");
            throw new WebApplicationException(Response.Status.FORBIDDEN);
        }
    }

    /**
     * Determines if the identity has the system administrator capability.
     *
     * @param identity The identity.
     * @return True if system administrator, false otherwise.
     */
    private static boolean isSystemAdmin(Identity identity) {
        return identity.getCapabilityManager().hasCapability(Capability.SYSTEM_ADMINISTRATOR);
    }

}
