/* (c) Copyright 2017 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.redirect;

import sailpoint.tools.Util;

import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Filter to redirect deep links targeting the classic identity request details page to responsive
 */
public class IdentityRequestDetailsRedirectFilter implements javax.servlet.Filter {

    private static final String REQUEST_ID = "requestId";
    private static final String ID = "id";
    private static final String IDENTITY_REQUEST_DETAIL_URL = "/identityRequest/identityRequest.jsf#/request/";

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        // NOTHING TO DO
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) servletRequest;
        HttpServletResponse httpResponse = (HttpServletResponse) servletResponse;

        String requestId = httpRequest.getParameter(REQUEST_ID);
        if (Util.isNothing(requestId)) {
            // requestId is the expected parameter, but fallback just in cases
            requestId = httpRequest.getParameter(ID);
        }

        httpResponse.sendRedirect(httpRequest.getContextPath() + IDENTITY_REQUEST_DETAIL_URL + requestId);
    }

    @Override
    public void destroy() {
        // NOTHING TO DO
    }
}
