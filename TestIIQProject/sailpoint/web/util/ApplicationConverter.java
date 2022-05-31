/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.util;

import sailpoint.object.Application;


/**
 * A JSF converter that converts between ids and Applications.
 */
public class ApplicationConverter extends BaseObjectConverter<Application> 	
{
    public ApplicationConverter() 
    	{
        super(Application.class);
    	}
	}
