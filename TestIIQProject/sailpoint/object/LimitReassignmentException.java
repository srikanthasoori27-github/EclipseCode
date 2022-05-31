/* (c) Copyright 2015 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.object;

import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.web.messages.MessageKeys;

/**
 * Exception thrown when amount of reassignment exceeds the defined limit of reassignment.
 */
public class LimitReassignmentException extends GeneralException {

    private static final long serialVersionUID = -2146008819802617670L;

    public LimitReassignmentException() {
        super(new Message(MessageKeys.ERR_REASSIGNMENT_LIMIT_EXCEEDED));
    }
}
