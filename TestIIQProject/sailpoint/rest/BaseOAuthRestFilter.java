package sailpoint.rest;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import sailpoint.api.SailPointContext;
import sailpoint.api.SailPointFactory;
import sailpoint.integration.AuthenticationUtil;
import sailpoint.service.oauth.OAuthAccessToken;
import sailpoint.service.oauth.OAuthTokenExpiredException;
import sailpoint.service.oauth.OAuthTokenValidator;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

import javax.servlet.ServletRequest;
import javax.servlet.http.HttpServletRequest;
import java.security.GeneralSecurityException;
import java.util.List;

public class BaseOAuthRestFilter extends AuthenticationFilter {

    private static final Log log = LogFactory.getLog(BaseOAuthRestFilter.class);

    /**
     * Similar to AuthenticationFilter.ATT_IDENTITY_NAME this attribute is added to the
     * ServletRequest and passed to base rest layers for authentication purposes.
     */
    public static final String ATT_OAUTH_IDENTITY_NAME = "sailpoint.oauth.identityName";

    /**
     * Attribute used to pass the requested OAuth scope to the underlying rest resource.
     */
    public static final String ATT_OAUTH_CLIENT_SCOPE = "sailpoint.oauth.clientScope";

    @Override
    protected boolean isAuthRequest(HttpServletRequest httpRequest) {
        boolean isAuth = super.isAuthRequest(httpRequest);
        if (!isAuth) {
            String authHeader = getAuthHeader(httpRequest);
            isAuth = AuthenticationUtil.isBearerAuth(authHeader);
        }

        return isAuth;
    }

    @Override
    protected AuthenticationResult authenticate(HttpServletRequest httpRequest) {
        // try authenticating via Basic Auth first
        AuthenticationResult authenticationResult =  super.authenticate(httpRequest);
        if (!authenticationResult.isAuthenticated()) {
            // try Bearer Auth
            authenticationResult = bearerAuthenticate(httpRequest);
        }

        return authenticationResult;
    }

    /**
     * Authenticates using the Bearer token issued from an OAuth token request. Will not
     * throw exceptions.
     * @param httpRequest if successful will set ATT_OAUTH_IDENTITY_NAME and ATT_OAUTH_CLIENT_SCOPE on the ServletRequest
     * @return if authentication was successful
     */
    protected AuthenticationResult bearerAuthenticate(HttpServletRequest httpRequest) {
        boolean success = false;
        AuthenticationResult.Reason reason = AuthenticationResult.Reason.UNSPECIFIED;

        String authHeader = getAuthHeader(httpRequest);
        if (!AuthenticationUtil.isBearerAuth(authHeader)) {
            return new AuthenticationResult(false);
        }

        try {
            SailPointContext ctx = SailPointFactory.getCurrentContext();
            OAuthTokenValidator validator = new OAuthTokenValidator(ctx);
            OAuthAccessToken token = validator.authenticate(authHeader);
            if (null != token) {
                success = true;
                httpRequest.setAttribute(ATT_OAUTH_IDENTITY_NAME, token.getIdentityId());
                httpRequest.setAttribute(ATT_OAUTH_CLIENT_SCOPE, token.getScope());
            }
        } catch (OAuthTokenExpiredException e) {
            success = false;
            reason = AuthenticationResult.Reason.OAUTH_TOKEN_EXPIRED;
        } catch (GeneralException ignore) {
            // hmm no context? AuthenticationFilter.hasBasicAuthenticationCredentials() ignores so we will too
        } catch (GeneralSecurityException e) {
            log.warn("Unable to authenticate using Bearer Authentication", e);
        }

        return new AuthenticationResult(success, reason);
    }

    /**
     * Return the name of the authenticated identity if authentication
     * credentials were retrieved from the HTTP Authorization header. Delegates to
     * AuthenticationFilter, if null or empty, will try and retrieve the OAuth Identity Name.
     * @param request http request
     * @return identity name which authenticated via OAuth Bearer token
     */
    public static String getIdentityName(ServletRequest request) {
        String name = AuthenticationFilter.getIdentityName(request);
        return (Util.isNullOrEmpty(name)) ? (String) request.getAttribute(ATT_OAUTH_IDENTITY_NAME) : name;
    }

    /**
     * Return the name of the requested rights requested via the OAuth token request.
     * @param request http request
     * @return list of rights requested in the original OAuth token request, can be null
     */
    public static List<String> getOAuthClientScope(ServletRequest request) {
        return (List<String>) request.getAttribute(ATT_OAUTH_CLIENT_SCOPE);
    }

}
