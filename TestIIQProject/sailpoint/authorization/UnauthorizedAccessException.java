/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.authorization;

import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;

/**
 * Thrown by implementers of Authorizer to signify that the user
 * has no access.
 * 
 * @author jeff.upton
 */
public class UnauthorizedAccessException extends GeneralException {
	
	private static final long serialVersionUID = 1L;
    
    public UnauthorizedAccessException() {
    }
    
    public UnauthorizedAccessException(String message) {
        super(message);
    }

    public UnauthorizedAccessException(Message message) {
        super(message);
    }

    public UnauthorizedAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
