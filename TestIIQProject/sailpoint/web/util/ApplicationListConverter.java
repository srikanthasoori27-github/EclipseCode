/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.util;

import sailpoint.object.Application;

/**
 * Convert a list of application IDs into a list of application names.
 *
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */
public class ApplicationListConverter extends BaseObjectListConverter<Application> {

    public ApplicationListConverter() {
        super(Application.class);
    }

}
