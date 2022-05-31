/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.authorization;

import sailpoint.tools.GeneralException;
import sailpoint.web.UserContext;

/**
 * Allows all users access. Used by methods that do not require any authorization.
 *
 * @author Jeff Upton <jeff.upton@sailpoint.com>
 */
public class AllowAllAuthorizer implements Authorizer {
	public void authorize(UserContext userContext) throws GeneralException {
	}
}
