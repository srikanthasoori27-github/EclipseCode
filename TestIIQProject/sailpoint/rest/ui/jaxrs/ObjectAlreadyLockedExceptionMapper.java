/* (c) Copyright 2016 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.rest.ui.jaxrs;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import sailpoint.api.ObjectAlreadyLockedException;

import javax.ws.rs.core.Response;

/**
 * A JAX-RS ExceptionMapper to catch all ObjectAlreadyLockedExceptions and return 409 status 
 */
public class ObjectAlreadyLockedExceptionMapper extends AbstractExceptionMapper<ObjectAlreadyLockedException> {
    private static final Log log = LogFactory.getLog(ObjectAlreadyLockedExceptionMapper.class);

    @Override
    protected Response buildResponse(ObjectAlreadyLockedException exception) {
        return buildResponse(Response.Status.CONFLICT.getStatusCode(), exception);
    }

    @Override
    protected void logException(ObjectAlreadyLockedException exception) {
        log.warn("Caught JAX-RS exception.", exception);
    }
}
