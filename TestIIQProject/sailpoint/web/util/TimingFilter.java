/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.util;

import java.io.IOException;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.Meter;
import sailpoint.tools.Util;


/**
 * A Servlet Filter that prints out the time taken to complete a request and
 * can collect per-request timing information.  To enable page timings, you
 * can either a) set the "sailpoint.storePageTimings" system property to "true",
 * or b) add an initialization parameter called "storePageTimings" with the
 * value of "true" to the Timing Filter filter definition in web.xml.
 * 
 * @author Kelly Grizzle
 */
public class TimingFilter implements Filter {

    private static final Log LOG = LogFactory.getLog(TimingFilter.class);

    private boolean storeMeters;

    
    /**
     * Default constructor.
     */
    public TimingFilter() {
        super();
        this.storeMeters =
            Util.atob(System.getProperty("sailpoint.storePageTimings"));
    }

    /* (non-Javadoc)
     * @see javax.servlet.Filter#init(javax.servlet.FilterConfig)
     */
    public void init(FilterConfig config) throws ServletException {

        if (!this.storeMeters) {
            this.storeMeters =
                Util.atob(config.getInitParameter("storePageTimings"));
        }
    }

    /* (non-Javadoc)
     * @see javax.servlet.Filter#destroy()
     */
    public void destroy() {}

    /* (non-Javadoc)
     * @see javax.servlet.Filter#doFilter(javax.servlet.ServletRequest, javax.servlet.ServletResponse, javax.servlet.FilterChain)
     */
    public void doFilter(ServletRequest req, ServletResponse response,
                         FilterChain chain)
        throws IOException, ServletException {

        long start = System.currentTimeMillis();

        String meterKey = null;
        HttpServletRequest httpReq = (HttpServletRequest) req;
        String uri = httpReq.getRequestURI();

        if (storeMeters) {
            String requestedWith = httpReq.getHeader("x-requested-with");
            boolean ajax = ((null != requestedWith) && (0 != requestedWith.length()));
            meterKey = uri;
            if (ajax) {
                meterKey += " (ajax)";
            }

            Meter.enterByName(meterKey);
        }

        try {
            chain.doFilter(req, response);
        }
        finally {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Request " + uri + " took " +
                          (System.currentTimeMillis() - start) + " ms");
            }

            if (storeMeters) {
                Meter.exitByName(meterKey);
            }
        }
    }
}
