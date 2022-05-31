/* (c) Copyright 2017 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.redirect;

import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Filter to redirect deep links targeting the classic identity request list page to responsive
 */
public class IdentityRequestListRedirectFilter implements javax.servlet.Filter {

    private static final String IDENTITY_REQUEST_LIST_URL = "/identityRequest/identityRequest.jsf#/requests";

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        // NOTHING TO DO
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) servletRequest;
        HttpServletResponse httpResponse = (HttpServletResponse) servletResponse;

        httpResponse.sendRedirect(httpRequest.getContextPath() + IDENTITY_REQUEST_LIST_URL);
    }

    @Override
    public void destroy() {
        // NOTHING TO DO
    }
}
