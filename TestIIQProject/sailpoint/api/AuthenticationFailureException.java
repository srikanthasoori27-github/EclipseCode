/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * Exception thrown when we detect that a password has expired.
 *
 * Author: Jeff
 */

package sailpoint.api;

import sailpoint.tools.GeneralException;
import sailpoint.web.messages.MessageKeys;

public class AuthenticationFailureException extends GeneralException {

    public AuthenticationFailureException() {
        super(MessageKeys.AUTH_FAILURE);
    }

    public AuthenticationFailureException(String msg) {
        super(msg);
    }
    
    public AuthenticationFailureException(Throwable t) {
        super(t);        
    }

}
