/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * Exception thrown to indicate that a task cannot be
 * launched because a previous result with the desired name
 * still exists.  This is used on in the case where
 * ResultAction is Cancel.  A specific exception is thrown
 * to the UI knows whether it is important to log.
 *
 * Author(s): Jeff Larson
 * 
 */

package sailpoint.api;

import sailpoint.tools.GeneralException;

public class TaskResultExistsException extends GeneralException {

    private static final long serialVersionUID = 1L;

    //////////////////////////////////////////////////////////////////////
    //
    // Constructors
    //
    //////////////////////////////////////////////////////////////////////

    public TaskResultExistsException(String msg) {
        super(msg);
        checkBreakpoint();
    }

}
