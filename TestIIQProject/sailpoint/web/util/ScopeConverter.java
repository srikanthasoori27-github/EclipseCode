/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.util;

import sailpoint.object.Scope;


/**
 * A JSF converter that converts between ids and scopes.
 *
 * @author <a href="mailto:derry.cannon@sailpoint.com">Derry Cannon</a>
 */
public class ScopeConverter extends BaseObjectConverter<Scope> 
	{
    public ScopeConverter() 
    	{
        super(Scope.class);
    	}
	}
