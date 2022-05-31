/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 *
 */
package sailpoint.web;

import java.io.IOException;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hibernate.StaleObjectStateException;

import sailpoint.api.SailPointContext;
import sailpoint.api.SailPointFactory;
import sailpoint.tools.GeneralException;

public class SailPointContextRequestFilter implements Filter {

    private static Log log =
                       LogFactory.getLog(SailPointContextRequestFilter.class);

    public void doFilter(ServletRequest request,
                         ServletResponse response,
                         FilterChain chain)
            throws IOException, ServletException {

        SailPointContext c = null;
        try {
            log.debug("Requesting a SailPoint context.");

            // the auth filter is supposed to leave the user name here
            HttpServletRequest httpRequest = (HttpServletRequest)request;
            HttpSession httpSession = httpRequest.getSession(true);
            Object o = httpSession.getAttribute(PageAuthenticationFilter.ATT_PRINCIPAL);
            String userName = (o != null) ? o.toString() : "SailPointContextRequestFilter";

            c = SailPointFactory.createContext(userName);

            log.debug("SailPointContext = " + c);

                // Call the next filter (continue request processing)
            chain.doFilter(request, response);
        } catch (StaleObjectStateException staleEx) {
            log.error("This interceptor does not implement optimistic " +
                      "concurrency control!  Your application will not " +
                      "work until you add compensation actions!");
                // Rollback, close everything, possibly compensate for
                // any permanent changes during the conversation, and
                // finally restart business conversation. Maybe give the
                // user of the application a chance to merge some of his
                // work with fresh data... what you do here depends on
                // your applications design.
            throw staleEx;
        } catch (Throwable ex) {
            log.error(ex.getMessage(), ex);

                // Let others handle it... maybe another interceptor for
                // exceptions?
            throw new ServletException(ex);
        }
        finally
        {
            if (null != c)
            {
                // Complete the use of the context
                log.debug("Releasing the SailPoint context.");
                try {
                    SailPointFactory.releaseContext(c);
                } catch (GeneralException e) {
                    if (log.isWarnEnabled())
                        log.warn("Failed releasing SailPointContext: "
                                 + e.getLocalizedMessage(), e);
                }
            }
        }
    }

    public void init(FilterConfig filterConfig) throws ServletException {
        log.debug("Initializing SailPointContextRequestFilter ...");
    }

    public void destroy() {
        log.debug("Destroying SailPointContextRequestFilter ...");
    }
}  // class SailPointContextRequestFilter
