/* (c) Copyright 2016 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.rest.oauth.jaxrs;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

import sailpoint.service.oauth.OAuthTokenRequestException;

/**
 * According to the spec, every error condition maps to a http status 400 Bad Request. This mapper converts
 * the RequestException to a JSON payload according to the spec.
 */
@Provider
public class OAuthExceptionMapper implements ExceptionMapper<OAuthTokenRequestException> {

    @Override
    public Response toResponse(OAuthTokenRequestException e) {
        return Response.status(Status.BAD_REQUEST).entity(e.toMap()).build();
    }

}
