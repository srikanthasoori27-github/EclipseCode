
/* (c) Copyright 2016 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.rest.plugin;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Authorization annotation which indicates that the authorization
 * of the plugin REST endpoint is deferred to the method call. In this case,
 * an implementation of the Authorizer interface is handling authorization.
 *
 * @ignore
 * An annotation like this is necessary since the method/class has to have
 * some annotation on it. If there were no annotation then the endpoint
 * method would never be called. The AllowAll annotation could have been
 * used but it seemed wrong to force AllowAll to be specified when actual
 * authorization would happen in the method call. This way it is hopefully
 * clear what is going on.
 *
 * @author Dustin Dobervich <dustin.dobervich@sailpoint.com>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Deferred {}
