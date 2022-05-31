/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * Exception thrown when we detect a database version mismatch.
 * This has its own class so we can catch and format it nicely.
 * Note that this is a RuntimeException rather than a GeneralException
 * so we can throw it more easily from the ServletContextListener.
 *
 * Author: Jeff
 */

package sailpoint.api;

public class DatabaseVersionException extends RuntimeException {

    public DatabaseVersionException(String msg) {
        super(msg);
    }

}
