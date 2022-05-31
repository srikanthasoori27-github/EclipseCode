/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.authorization;

import java.util.Arrays;
import java.util.List;

import sailpoint.object.Capability;
import sailpoint.tools.GeneralException;
import sailpoint.web.UserContext;

/**
 * Ensures that the user has at least one of the required capabilities.
 * If no capabilities are passed in, authorization is successful.
 * 
 * @author jeff.upton
 */
public class CapabilityAuthorizer implements Authorizer {

	private String[] _requiredCapabilities;
	
	public CapabilityAuthorizer(String... requiredCapabilities) {
		_requiredCapabilities = requiredCapabilities;
	}
	
	public void authorize(UserContext userContext) throws GeneralException {
		if (_requiredCapabilities.length > 0) {	
			List<Capability> userCapabilities = userContext.getLoggedInUserCapabilities();
			
			if (Capability.hasSystemAdministrator(userCapabilities)) {
				return;
			}
			
			for (String requiredCapability : _requiredCapabilities) {
				if (Capability.hasCapability(requiredCapability, userCapabilities)) {
					return;
				}
			}
			
			throw new UnauthorizedAccessException("User does not have any of the required capabilities: " + Arrays.toString(_requiredCapabilities));
		}
	}

}
