/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.util;

import sailpoint.object.Identity;


/**
 * A JSF converter that converts between ids and identities.  
 *
 * @author <a href="mailto:derry.cannon@sailpoint.com">Derry Cannon</a>
 */
public class IdentityConverter extends BaseObjectConverter<Identity> 
	{
    public IdentityConverter() 
    	{
        super(Identity.class);
    	}
	}
