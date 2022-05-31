/* (c) Copyright 2016 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.rest.oauth;

import java.util.Map;

import javax.ws.rs.Consumes;
import javax.ws.rs.FormParam;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.SailPointContext;
import sailpoint.api.SailPointFactory;
import sailpoint.service.oauth.OAuthTokenIssuer;
import sailpoint.service.oauth.OAuthTokenRequest;
import sailpoint.service.oauth.OAuthTokenRequestException;
import sailpoint.service.oauth.OAuthTokenRequestException.ErrorType;
import sailpoint.service.oauth.OAuthTokenResponse;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

/**
 * This resource performs the generation of access tokens to the client.
 * The payload is defined at http://tools.ietf.org/html/rfc6749#section-5.1 .
 * 
 * @author danny.feng
 *
 */
@Consumes({MediaType.APPLICATION_FORM_URLENCODED})
@Produces(MediaType.APPLICATION_JSON)
@Path("token")
public class OAuthAccessTokenResource {

    private static Log log = LogFactory.getLog(OAuthAccessTokenResource.class);

    @POST
    public Map<String, Object> token(@HeaderParam(HttpHeaders.AUTHORIZATION) String authHeader,
        @FormParam(OAuthTokenRequest.PARAM_GRANT_TYPE) String grantType,
        @FormParam(OAuthTokenRequest.PARAM_SCOPE) String scopes) throws OAuthTokenRequestException {
        
        if (Util.isNullOrEmpty(authHeader)) {
            throw new OAuthTokenRequestException(ErrorType.INVALID_REQUEST);
        }

        SailPointContext ctx = null;
        try {
            ctx = SailPointFactory.getCurrentContext();

            OAuthTokenIssuer issuer = new OAuthTokenIssuer(ctx);
            OAuthTokenResponse tokenResponse = issuer.generateToken(new OAuthTokenRequest(authHeader, grantType, scopes));

            return tokenResponse.toMap();

        } catch (GeneralException e) {
            if (log.isWarnEnabled()) {
                log.warn("unable to create oauth2 access token", e);
            }
            throw new OAuthTokenRequestException(ErrorType.INVALID_REQUEST);
        }
    }

}