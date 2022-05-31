/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.Capability;
import sailpoint.object.Configuration;


public class PageAuthorizationFilter implements javax.servlet.Filter {
    private static Log log = LogFactory.getLog(PageAuthorizationFilter.class);

    /**
     * The URL to redirect to if access to a page is denied.
     */
    private String _accessDeniedUrl;

    /**
     * Initialize the filter by reading the configuration parameters and
     * saving them for reference when the filter is invoked.
     */
    public void init(FilterConfig config) throws ServletException {
        _accessDeniedUrl = config.getInitParameter("accessDeniedUrl");
    }

    /**
     * Called by the container when the filter is destroyed.
     */
    public void destroy() { }

    /**
     *
     */
    @SuppressWarnings("unchecked")
    public void doFilter(ServletRequest request, ServletResponse response,
            FilterChain chain) throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest)request;
        HttpServletResponse httpResponse = (HttpServletResponse)response;

            // don't create one unless we have to
        HttpSession httpSession = httpRequest.getSession(false);
        Object capObject =
            httpSession.getAttribute(PageAuthenticationFilter.ATT_CAPABILITIES);
        Object rightObject =
            httpSession.getAttribute(PageAuthenticationFilter.ATT_RIGHTS);

        List<Capability> capList = null;
        if ( capObject instanceof List )
            capList = (List)capObject;

        Collection<String> rightList = null;
        if ( rightObject instanceof Collection )
            rightList = (Collection)rightObject;

        String url = httpRequest.getServletPath();
        if ( url != null && url.startsWith("/") )
            url = url.substring(1);

        log.debug("URL = " + url);

        // get any identity properties of the session needed for auth or menu building
        Object o = httpSession.getAttribute(PageAuthenticationFilter.ATT_ID_AUTH_ATTRS);

        if(!Authorizer.getInstance().isAuthorized(url, capList, rightList, o!=null ? (Map<String, Object>)o : null)){
            httpResponse.sendRedirect(httpRequest.getContextPath() + _accessDeniedUrl);
            return;
        }           
        
        // prevents using the back button after logout
        setXFrameOptions(httpResponse);
        httpResponse.addDateHeader("Expires", System.currentTimeMillis() - 10*60*1000); 

        // any risk associated with passing the cast http request and response objects
        // instead of the original servlet request and response?
        chain.doFilter(httpRequest, httpResponse);
    }  // doFilter(ServeletRequest, ServletResponse, FilterChain)
    
    private void setXFrameOptions(HttpServletResponse httpResponse) {
        
        if (!isAllowXFrameOptions()) {
            httpResponse.addHeader("X-Frame-Options", "deny");
        }
    }
    
    /**
     * This setting determines whether we allow IIQ to be called inside an iFrame?
     */    
    public static boolean isAllowXFrameOptions() {
        return Configuration.getSystemConfig().getBoolean(Configuration.IIQ_ALLOW_IFRAME, false);
    }

}  // class PageAuthorizationFilter
