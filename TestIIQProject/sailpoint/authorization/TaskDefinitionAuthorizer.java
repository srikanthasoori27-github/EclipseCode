/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.authorization;

import sailpoint.integration.Util;
import sailpoint.object.TaskDefinition;
import sailpoint.tools.GeneralException;
import sailpoint.web.UserContext;

/**
 * TaskDefinitionAuthorizer
 * @author jeff.upton
 */
public class TaskDefinitionAuthorizer implements Authorizer {
	
	TaskDefinition _definition;
	
	public TaskDefinitionAuthorizer(TaskDefinition definition) {
		_definition = definition;
	}

	public void authorize(UserContext userContext) throws GeneralException {
		if (_definition == null || 
			_definition.getOwner() == null || 
			Util.nullSafeEq(userContext.getLoggedInUserName(), _definition.getOwner().getName())) {
			
			return;
		}
		
		throw new UnauthorizedAccessException("User has no access to this task definition");
	}

}
