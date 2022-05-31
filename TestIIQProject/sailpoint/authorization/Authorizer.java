/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.authorization;

import sailpoint.tools.GeneralException;
import sailpoint.web.UserContext;

/**
 * An interface for classes that authorize access to an object or action.
 * 
 * @author jeff.upton
 */
public interface Authorizer {
	
	/**
	 * Invoked to determine whether or not the specified UserContext
	 * has access to this authorizer's data. Implementers should throw an
	 * instance of UnauthorizedAccessException if the user is not
	 * authorized.
	 * 
	 * @param userContext The UserContext to authorize.
	 * 
	 * @throws UnauthorizedAccessException If authorization fails.
	 * @throws GeneralException If an error occurs during authorization
	 */
	public void authorize(UserContext userContext) throws GeneralException;
	
}
