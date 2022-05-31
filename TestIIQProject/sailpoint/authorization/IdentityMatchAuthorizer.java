/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.authorization;

import sailpoint.object.Identity;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.web.UserContext;

/**
 * Ensures that the user matches the specified identity.
 * 
 * @author jeff.upton
 */
public class IdentityMatchAuthorizer implements Authorizer {
	
	private Identity _identity;
	
	public IdentityMatchAuthorizer(Identity identity) {
		_identity = identity;
	}

	public void authorize(UserContext userContext) throws GeneralException {
		if (_identity == null || userContext.getLoggedInUser() == null 
				|| !Util.nullSafeEq(userContext.getLoggedInUser().getId(), _identity.getId())) {
            throw new UnauthorizedAccessException();
        }
	}

}
