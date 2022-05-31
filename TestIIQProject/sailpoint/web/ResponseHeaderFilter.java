package sailpoint.web;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

/**
 * Filter to add an arbitrary number of response headers to a HttpServletResponse.
 * Configurable in web.xml, naturally.
 *
 * Author: michael.hide
 * Created: 1/13/14 1:52 PM
 */
public class ResponseHeaderFilter implements Filter {

    private Map<String, String> headerFields;

    /* (non-Javadoc)
     * @see javax.servlet.Filter#init(javax.servlet.FilterConfig)
     */
    @Override
    public void init(FilterConfig config) throws ServletException {
        String key = null;
        headerFields = new HashMap<String, String>();
        @SuppressWarnings("unchecked")
        Enumeration headerKeys = config.getInitParameterNames();

        while (headerKeys.hasMoreElements()) {
            key = (String) headerKeys.nextElement();
            headerFields.put(key, config.getInitParameter(key));
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
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletResponse httpResponse = (HttpServletResponse) response;

        for (String key : headerFields.keySet()) {
            httpResponse.setHeader(key, headerFields.get(key));
        }

        chain.doFilter(request, response);
    }
}
