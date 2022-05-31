/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.authorization;

import sailpoint.tools.GeneralException;
import sailpoint.web.UserContext;

/**
 * Provides static utility methods to help with authorization.
 * 
 * @author jeff.upton
 */
public class AuthorizationUtility {

	/**
     * Determines whether the current user is authorized for all passed authorizers.
     * 
     * @param authorizers The authorizers to check.
     * @return True if authorization is successful, false otherwise.
     * @throws GeneralException If an error occurs during authorization.
     */
    public static boolean isAuthorized(UserContext userContext, Authorizer...authorizers) throws GeneralException {
    	try {
    		authorize(userContext, authorizers);
    		return true;
    	} catch (UnauthorizedAccessException ex) {
    		return false;
    	}
    }
    
    /**
     * Authorizes the current user with the specified authorizers.
     * 
     * @param authorizers The authorizers to check.
     * @throws UnauthorizedAccessException If authorization fails.
     * @throws GeneralException If an error occurs during authorization.
     */
    public static void authorize(UserContext userContext, Authorizer... authorizers) throws GeneralException {
    	for (Authorizer authorizer : authorizers) {
    		authorizer.authorize(userContext);
    	}
    }
	
}
