
/* (c) Copyright 2016 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.rest.plugin;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Authorization annotation used to specify an SPRight that is needed
 * by the current user to access the plugin REST endpoint.
 *
 * @author Dustin Dobervich <dustin.dobervich@sailpoint.com>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.TYPE, ElementType.METHOD })
public @interface RequiredRight {

    /**
     * The name of the required SPRight.
     *
     * @return The right.
     */
    String value();

}
