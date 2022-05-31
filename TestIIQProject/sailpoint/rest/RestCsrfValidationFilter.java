package sailpoint.rest;

import java.io.IOException;
import java.util.Collection;
import java.util.List;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import javax.servlet.http.Part;

import sailpoint.server.CsrfService;
import sailpoint.server.CsrfValidationException;
import sailpoint.service.PageAuthenticationService;
import sailpoint.tools.Util;

/**
 * Request filter which validates XSRF token in the request header against the one stored in the session.
 * Throws a CsrfValidationException if they don't match.
 * 
 * AngularJS will automatically include the XSRF token in the 'x-xsrf-token' header as long as an analogous 
 * 'xsrf-token' cookie is found in the page. For Ext requests, we manually emulate AngularJS behavior by defining a 
 * default 'x-xsrf-token' header containing the value of the same cookie.
 * 
 * See Bug #18624 for details.
 */
public class RestCsrfValidationFilter implements Filter {

    /**
     * Request header which contains the XSRF token.
     */
    public static final String XSRF_TOKEN_HEADER = "X-XSRF-TOKEN";

    private static final String INIT_PARAM_IGNORED_PATHS = "ignoredPaths";

    /**
     * The list of GET paths that are configured to be ignored. POST and PUT paths are never ignored.
     */
    private List<String> ignoredPaths;

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        String ignored = filterConfig.getInitParameter(INIT_PARAM_IGNORED_PATHS);
        if (null != ignored) {
            ignoredPaths = Util.csvToList(ignored);
        }
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) servletRequest;

        // If we are ignoring this request, or if it is a basic authn request, carry on without CSRF validation.
        if (ignore(httpRequest) || isBasicAuthRequest(httpRequest)) {
            filterChain.doFilter(servletRequest, servletResponse);
            return;
        }

        String csrfToken = httpRequest.getHeader(XSRF_TOKEN_HEADER);

        // Validate the token. Validation will fail if it's null or does not match the expected token.
        validateCsrfToken(csrfToken, httpRequest);

        // Call the next filter (continue request processing)
        filterChain.doFilter(servletRequest, servletResponse);
    }

    @Override
    public void destroy() {
        // no-op
    }

    protected void validateCsrfToken(String token, HttpServletRequest request) {
        //If authenticated Session, validate csrf
        if(getSessionUserName(request) != null) {
            HttpSession session = getSession(request);

            if(session != null) {
                CsrfService csrf = new CsrfService(session);
                csrf.validate(token, request.getRequestURI());
            } else {
                throw new CsrfValidationException();
            }
        }
    }

    /**
     * Return whether csrf validation for the given request should be ignored.
     */
    protected boolean ignore(HttpServletRequest request) {
        String uri = request.getRequestURI();

        // The only things we will possibly ignore are GETs. We always return false for POST/PUT/PATCH/DELETE etc.
        // We also return false if ".." is discovered anywhere in the URI since it's an indication of possible
        // path-traversal shenanigans.
        if ("GET".equals(request.getMethod()) && !uri.contains("..")) {
            for (String ignoredPath : Util.iterate(ignoredPaths)) {
                if (uri.indexOf(ignoredPath) > -1) {
                    // Based on the path, we should be able to ignore this one (bypass CSRF checking).
                    return true;
                }
            }
        }

        return false;
    }


    private String getSessionUserName(HttpServletRequest request){
        HttpSession httpSession = request.getSession(true);
        Object o = httpSession.getAttribute(PageAuthenticationService.ATT_PRINCIPAL);
        return o!=null ? (String)o : null;
    }

    private HttpSession getSession(HttpServletRequest request){
        return request != null ? request.getSession() : null;
    }

    /**
     * Returns true if the request contains basic authn credentials.
     */
    public boolean isBasicAuthRequest(ServletRequest request) {
        return !Util.isNullOrEmpty((String)request.getAttribute(AuthenticationFilter.ATT_IDENTITY_NAME));
    }

}

