package sailpoint.rest.ui.jaxrs;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import sailpoint.tools.ObjectNotFoundException;

import javax.ws.rs.core.Response;
import javax.ws.rs.ext.Provider;

/**
 * A JAX-RS ExceptionMapper to catch all ObjectNotFoundException and return a 
 * 404 response with localized exception message 
 */
@Provider
public class ObjectNotFoundExceptionMapper extends AbstractExceptionMapper<ObjectNotFoundException> {
    private static final Log log = LogFactory.getLog(ObjectNotFoundExceptionMapper.class);

    public Response buildResponse(ObjectNotFoundException exception) {
       return buildResponse(Response.Status.NOT_FOUND.getStatusCode(), exception);
    }
    
    public void logException(ObjectNotFoundException exception) {
        log.warn("Caught JAX-RS exception.", exception);
    }
}