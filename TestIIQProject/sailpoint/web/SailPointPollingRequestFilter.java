/* (c) Copyright 2016 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.servlet.Filter;
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

import sailpoint.server.SailPointSessionListener;
import sailpoint.tools.Util;
import sailpoint.web.util.WebUtil;

/**
 * Filter installed to manage the session excluding polling requests.
 * This maintains the last access time for non-polling request.
 * 
 * All polling request URLs should be defined in <init-param>.
 * 
 * 
 * 
 * @author danny.feng
 *
 */
public class SailPointPollingRequestFilter implements Filter {

    public static final String LAST_ACCESS_TIME_NOT_POLLING = SailPointSessionListener.LAST_ACCESS_TIME_NOT_POLLING;

    private static Log log =
                       LogFactory.getLog(SailPointPollingRequestFilter.class);

    private static final String INIT_PARAM_POLLING_PATHS = "pollingPaths";

    /**
     * The list of paths that are configured to be ignored.
     */
    private List<String> pollingPaths;


    /**
     * Where we go when the session times out.
     */
    private String timeoutUrl;

    /**
     * Where we go when the session times out for mobile responsive ui.
     */
    private String mobileTimeoutUrl;

    /**
     * The path that is the fake REST endpoint used to check and potentially invalidate
     * session on client timeout
     */
    private String checkSessionUrl;

    public void doFilter(ServletRequest request,
                         ServletResponse response,
                         FilterChain chain)
            throws IOException, ServletException {

        try {
            log.debug("SailPointPollingRequestFilter starts.");

            HttpServletRequest httpRequest = (HttpServletRequest)request;
            HttpSession httpSession = httpRequest.getSession(true);
            int timeout = httpSession.getMaxInactiveInterval() * 1000;
            if (timeout < 0) {
                //If the web server has been configured so that sessions never expire
                //(i.e. - the &lt;session-timeout&gt; tag in the web.xml has been set to a
                //negative value), skip this Filter.
                chain.doFilter(request, response);
            }

            String uri = httpRequest.getRequestURI().substring(httpRequest.getContextPath().length());
            boolean isPolling = pollingPaths.contains(uri);
            boolean isCheckSession = checkSessionUrl.equals(uri);

            Object lastAccessTimeNotPolling = httpSession.getAttribute(LAST_ACCESS_TIME_NOT_POLLING);
            long currentTime = System.currentTimeMillis();

            //check for session timeout
            if (lastAccessTimeNotPolling != null) {
                long timePassed = currentTime - (long) lastAccessTimeNotPolling;
                if (timePassed >= timeout) {
                    httpSession.invalidate();

                    // For polling requests or check session, do not redirect, just send the error back to the client
                    if (isPolling || isCheckSession) {
                        ((HttpServletResponse) response).sendError(HttpServletResponse.SC_REQUEST_TIMEOUT);
                        return;
                    }

                    //redirect to timeout page only for non-polling request
                    Map<String, String[]> paramMap = httpRequest.getParameterMap();
                    String preRedirectUrl = httpRequest.getContextPath() +
                            (WebUtil.isMobile(httpRequest) ? mobileTimeoutUrl : timeoutUrl);

                    // If a redirect URL is set, forward the user to that URL instead of the timeout URL.
                    // Since the session is invalid, the user will be prompted to log in again and then
                    // redirected to the page they were on prior to timeout.
                    if (!Util.isEmpty(paramMap)) {
                        if (paramMap.get("sessionTimeoutForm:preRedirectUrlHash") != null) {
                            preRedirectUrl = httpRequest.getRequestURL() + paramMap.get("sessionTimeoutForm:preRedirectUrlHash")[0];
                        }
                    }
                    ((HttpServletResponse) response).sendRedirect(preRedirectUrl);
                    return;
                }
            }
            
            if (!isPolling && !isCheckSession) {
                //update lastAccessTimeNotPolling for "real" requests
                httpSession.setAttribute(LAST_ACCESS_TIME_NOT_POLLING, currentTime);
            }

            // Call the next filter (continue request processing)
            // If its checkSession, do not continue! There is no real endpoint at the other end!
            if (!isCheckSession) {
                chain.doFilter(request, response);
            }
        } catch (Throwable ex) {
            log.error(ex.getMessage(), ex);
            throw new ServletException(ex);
        }
    }

    /* (non-Javadoc)
     * @see javax.servlet.Filter#destroy()
     */
    @Override
    public void destroy() {
    }

    /* (non-Javadoc)
     * @see javax.servlet.Filter#init(javax.servlet.FilterConfig)
     */
    @Override
    public void init(FilterConfig config) throws ServletException {
        timeoutUrl = config.getInitParameter("timeoutUrl");
        mobileTimeoutUrl = config.getInitParameter("mobileTimeoutUrl");
        String pollings = config.getInitParameter(INIT_PARAM_POLLING_PATHS);
        if (null != pollings) {
            pollingPaths = Util.csvToList(pollings);
        } else {
            pollingPaths = new ArrayList<String>();
        }
        checkSessionUrl = config.getInitParameter("checkSessionUrl");
        if (checkSessionUrl == null) {
            checkSessionUrl = "";
        }
    }
}  
