/* (c) Copyright 2009 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.rest;

import java.io.IOException;
import java.util.List;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import sailpoint.api.SailPointContext;
import sailpoint.api.SailPointFactory;
import sailpoint.integration.AuthenticationUtil;
import sailpoint.integration.RestServlet;
import sailpoint.object.Identity;
import sailpoint.service.LoginService;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

/**
 * A ServletFilter that performs authentication for REST web services.
 *
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */
public class AuthenticationFilter implements Filter {

    /**
     * A ServletRequest attribute name used to store the identity name when
     * authentication occurs using the HTTP Authorization request header.
     */
    public static final String ATT_IDENTITY_NAME = "sailpoint.rest.identityName";
    
    private static final String INIT_PARAM_IGNORED_PATHS = "ignoredPaths";

    /**
     * The list of paths that are configured to be ignored.
     */
    private List<String> ignoredPaths;


    /* (non-Javadoc)
     * @see javax.servlet.Filter#init(javax.servlet.FilterConfig)
     */
    @Override
    public void init(FilterConfig config) throws ServletException {
        String ignored = config.getInitParameter(INIT_PARAM_IGNORED_PATHS);
        if (null != ignored) {
            ignoredPaths = Util.csvToList(ignored);
        }
    }

    /* (non-Javadoc)
     * @see javax.servlet.Filter#destroy()
     */
    @Override
    public void destroy() {
        // no-op
    }

    /* (non-Javadoc)
     * @see javax.servlet.Filter#doFilter(javax.servlet.ServletRequest, javax.servlet.ServletResponse, javax.servlet.FilterChain)
     */
    @Override
    public void doFilter(ServletRequest request, ServletResponse response,
                         FilterChain chain)
        throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        // If we are ignoring this request, carry on.
        if (ignore(httpRequest)) {
            chain.doFilter(request, response);
            return;
        }
        
        AuthenticationResult authenticationResult = new AuthenticationResult(false);
        

        if (isAuthRequest(httpRequest)) {
            authenticationResult = authenticate(httpRequest);
        }
        else {
            // Otherwise check if this is an authenticated session.
            authenticationResult = new AuthenticationResult(isAuthenticatedSession(httpRequest));
        }

        if (!authenticationResult.isAuthenticated()) {
            String msg = getErrorMessage(httpRequest);
            RestServlet.sendResponse(httpResponse, HttpServletResponse.SC_UNAUTHORIZED,
                    msg, getContentType(), authenticationResult.getReasonName());
        }
        else {
            chain.doFilter(request, response);
        }
    }
    
    /**
     * Override this class if necessary, return true if the request looks like a Basic Auth request.
     * @param httpRequest request
     * @return if the request looks like an authentication request
     */
    protected boolean isAuthRequest(HttpServletRequest httpRequest) {
        // Check basic authorization first. If client has provided an Authorization header, we'll check credentials
        // only against it and it must be legit or it's a possible attempt to sidestep CSRF validation.
        String authHeader = getAuthHeader(httpRequest);
        return !Util.isNullOrEmpty(authHeader) && AuthenticationUtil.isBasicAuth(authHeader);
    }
    
    /**
     * Override this class if necessary, return true if successfully authenticated.
     * @param httpRequest
     * @return if authentication was successful
     */
    protected AuthenticationResult authenticate(HttpServletRequest httpRequest) {
        return new AuthenticationResult(hasBasicAuthenticationCredentials(httpRequest));
    }
    
    /**
     * Returns a simple error message to use as the Response body 
     * @param httpRequest httpRequest in case you need it
     * @return String that represents the error message to send 
     */
    protected String getErrorMessage(HttpServletRequest httpRequest) {
        return "User is unauthorized to access: " + httpRequest.getRequestURI();
    }
    
    /**
     * Retrieves the Authorization header from the httpRequest.
     * @param httpRequest request
     * @return header "Authorization" (may be null)
     */
    protected String getAuthHeader(HttpServletRequest httpRequest) {
        return httpRequest.getHeader("Authorization");
    }

    /**
     * 
     * @param httpRequest request
     * @return true if basic authorization credentials authenticate  
     */
    private boolean hasBasicAuthenticationCredentials(HttpServletRequest httpRequest) {

        String authHeader = getAuthHeader(httpRequest);

        // When using application/x-www-form-urlencoded, retrieving request
        // parameters before JAX-RS can get them can cause issues.  This will
        // cause a warning saying "A servlet POST request, to the URI <uri>
        // contains form parameters in the request body but the request body has
        // been consumed by the servlet or a servlet filter accessing the
        // request parameters. Only resource methods using @FormParam will work
        // as expected. Resource methods consuming the request body by other
        // means will not work as expected."  Wordy, yes, but it causes our POST
        // and PUT methods that take application/json bodies to not work.  Note
        // that this isn't a problem if the Content-Type header is set to
        // application/json, but in any case, we won't attempt to get the user/password 
        // from the request params at all and will let AuthenticationUtil.getCredentials()
        // parse them from the Authorization header.
        boolean success = false;
        try {
            String[] creds = AuthenticationUtil.getCredentials(authHeader, /*authnUserParam*/null, /*authnUserPass*/null);
            
            // This will throw if the context hasn't been created, so make
            // sure to wrap this in a filter that creates the context.
            SailPointContext ctx = SailPointFactory.getCurrentContext();
            Identity identity = ctx.authenticate(creds[0], creds[1]);
            if (null != identity) {
                success = true;
                httpRequest.setAttribute(ATT_IDENTITY_NAME, identity.getName());
            }
        }
        catch (Exception ignore) {}

        return success;
    }

    private boolean isAuthenticatedSession(HttpServletRequest request){
        boolean result = false;
        HttpSession httpSession = request.getSession(true);
        try {
            result = new LoginService(SailPointFactory.getCurrentContext()).isLoggedIn(new HttpSessionStorage(httpSession));
        } catch (GeneralException e) { /* ignore and default to not authenticated */}
        return result;
    }

    /**
     * Return whether authentication for the given request should be ignored.
     */
    private boolean ignore(HttpServletRequest request) {
        
        if (null != ignoredPaths) {
            String url = request.getRequestURL().toString();
            for (String ignoredPath : ignoredPaths) {
                if (url.indexOf(ignoredPath) > -1) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Return the name of the authenticated identity if authentication
     * credentials were retrieved from the HTTP Authorization header.
     */
    public static String getIdentityName(ServletRequest request) {
        return (String) request.getAttribute(ATT_IDENTITY_NAME);
    }

    /**
     * @return
     */
    protected String getContentType() {
        return null;
    }
}
