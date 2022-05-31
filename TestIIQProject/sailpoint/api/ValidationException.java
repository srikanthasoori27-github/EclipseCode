/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * Exception thrown by Workflower during work item assimilation
 * if one of the validation methods fails.  The UI is expected
 * to catch these and display them nicely.  This is relatively
 * general, you can use this in other service classes if
 * you just need to get a fixable error conveyed back to the UI.
 *
 * Author: Jeff
 *
 */

package sailpoint.api;

import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;

public class ValidationException extends GeneralException {

    public ValidationException(String msg) {
        super(msg);
    }

    public ValidationException(Message msg) {
        super(msg);
    }

}
