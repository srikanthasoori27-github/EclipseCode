package sailpoint.rest.ui.jaxrs;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import sailpoint.authorization.UnauthorizedAccessException;

import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.Provider;

/**
 * JAX-RS exception mapper that will send servlet response indicating unauthorized access in 
 * authorization fails
 */
@Provider
public class UnauthorizedAccessExceptionMapper extends AbstractExceptionMapper<UnauthorizedAccessException> {
    private static final Log log = LogFactory.getLog(UnauthorizedAccessExceptionMapper.class);

    @Override
    protected Response buildResponse(UnauthorizedAccessException exception) {
        return buildResponse(HttpServletResponse.SC_UNAUTHORIZED, exception);
    }
    
    @Override
    protected void logException(UnauthorizedAccessException exception) {
        log.error("Uncaught JAX-RS exception.", exception);
    }
}