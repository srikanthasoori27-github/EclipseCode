package sailpoint.rest.ui.jaxrs;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import sailpoint.tools.InvalidParameterException;

import javax.ws.rs.core.Response;
import javax.ws.rs.ext.Provider;

/**
 * A JAX-RS ExceptionMapper to catch all InvalidParameterExceptions and return a 
 * 400 response with localized exception message 
 */
@Provider
public class InvalidParameterExceptionMapper extends AbstractExceptionMapper<InvalidParameterException> {
    private static final Log log = LogFactory.getLog(InvalidParameterExceptionMapper.class);

    @Override
    protected Response buildResponse(InvalidParameterException exception) {
        return buildResponse(Response.Status.BAD_REQUEST.getStatusCode(), exception);
    }
    
    @Override
    protected void logException(InvalidParameterException exception) {
        log.warn("Caught JAX-RS exception.", exception);
    }
}