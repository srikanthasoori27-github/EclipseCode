
/* (c) Copyright 2009 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.rest.jaxrs;

import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.authorization.UnauthorizedAccessException;
import sailpoint.tools.GeneralException;

/**
 * A JAX-RS ExceptionMapper that will send errors to the client for
 * GeneralExceptions.  This serves as a fault barrier for all general
 * exceptions from JAX-RS requests.
 *
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */
@Provider
public class GeneralExceptionMapper
        implements ExceptionMapper<GeneralException> {

    private static final Log LOG = LogFactory.getLog(GeneralExceptionMapper.class);


    /**
     * Logs the GeneralException and returns an error response with the
     * exception's message as the entity body.
     */
    public Response toResponse(GeneralException exception) {
        LOG.error("Uncaught JAX-RS exception.", exception);

        if(exception != null &&
                exception.getClass().equals(UnauthorizedAccessException.class)){
            return Response.status(HttpServletResponse.SC_UNAUTHORIZED).entity(exception.getMessage()).build();
        }


        return Response.serverError().entity(exception.getMessage()).build();
    }
}