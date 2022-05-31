/* (c) Copyright 2009 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.rest.ui.jaxrs;

import javax.ws.rs.core.Response;
import javax.ws.rs.ext.Provider;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.tools.GeneralException;


/**
 * A JAX-RS ExceptionMapper to catch all GeneralExceptions and return a 
 * 500 response with exception message
 */
@Provider
public class GeneralExceptionMapper extends AbstractExceptionMapper<GeneralException> {

    private static final Log LOG = LogFactory.getLog(GeneralExceptionMapper.class);
    
    @Override
    protected Response buildResponse(GeneralException exception) {
        return Response.serverError().entity(getResponseEntity(exception)).build();
    }
    
    @Override
    protected void logException(GeneralException exception) {
        LOG.error("Uncaught JAX-RS exception.", exception);
    }
}
