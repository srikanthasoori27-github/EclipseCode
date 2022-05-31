/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.authorization;

import sailpoint.tools.GeneralException;
import sailpoint.web.UserContext;


/**
 * CompoundAuthorizer
 *
 * @author Jeff Upton <jeff.upton@sailpoint.com>
 */
public abstract class CompoundAuthorizer implements Authorizer {
	
	protected Authorizer[] _authorizers;
	
	public CompoundAuthorizer(Authorizer... authorizers) {
		_authorizers = authorizers;
	}
	
	public static Authorizer or(Authorizer... authorizers) {
		return new OrAuthorizer(authorizers);
	}
	
	public static Authorizer and(Authorizer... authorizers) {
		return new AndAuthorizer(authorizers);
	}
	
	private static class OrAuthorizer extends CompoundAuthorizer {		
		public OrAuthorizer(Authorizer... authorizers) {
			super(authorizers);
		}
		
		public void authorize(UserContext userContext) throws GeneralException {
			if (_authorizers.length == 0) {
				return;
			}
			
			for (Authorizer authorizer : _authorizers) {
				try {
					authorizer.authorize(userContext);
					return;
				} catch (UnauthorizedAccessException ex) {
					/* ignore so we can attempt another authorizer */
				}
			}
			
			/* all authorizers failed */
			throw new UnauthorizedAccessException("No authorizers passed");
			
		}		
	}
	
	private static class AndAuthorizer extends CompoundAuthorizer {		
		public AndAuthorizer(Authorizer... authorizers) {
			super(authorizers);
		}
		
		public void authorize(UserContext userContext) throws GeneralException {
			for (Authorizer authorizer : _authorizers) {
				authorizer.authorize(userContext);
			}			
		}		
	}

}
