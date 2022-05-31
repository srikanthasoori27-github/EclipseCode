/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * A subclass of GeneralException that is thrown when an attempt
 * is made to obtain a long duration lock on an object that already
 * has a lock.
 * 
 * Author: Jeff
 */

package sailpoint.api;

import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;

public class ObjectAlreadyLockedException extends GeneralException {

    public ObjectAlreadyLockedException(String message) {
        super(message);
    }
    
    public ObjectAlreadyLockedException(Message message) {
        super(message);
    }

}
