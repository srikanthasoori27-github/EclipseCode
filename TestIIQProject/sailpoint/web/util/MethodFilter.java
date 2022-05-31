/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.util;

import java.io.IOException;
import java.util.List;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;

import sailpoint.tools.Util;


/**
 * A Servlet Filter that strips off the <html> and <DOCTYPE> tags
 * on the response so that the caller receives only JSON in the response
 *
 * @author Peter Holcomb
 */
public class MethodFilter implements Filter {

    private static final String METHODS = "methods";
    
    private static List<String> unsupportedMethods;
    
    /* (non-Javadoc)
     * @see javax.servlet.Filter#init(javax.servlet.FilterConfig)
     */
    public void init(FilterConfig config) throws ServletException {
        unsupportedMethods = Util.csvToList(config.getInitParameter(METHODS));        
    }
    
    public void destroy() {}

    /* (non-Javadoc)
     * @see javax.servlet.Filter#doFilter(javax.servlet.ServletRequest, javax.servlet.ServletResponse, javax.servlet.FilterChain)
     */
    public void doFilter(ServletRequest req, ServletResponse response,
            FilterChain chain)
    throws IOException, ServletException {
        
        HttpServletRequest request = (HttpServletRequest)req;
        if(unsupportedMethods.contains(request.getMethod())){
            throw new ServletException("Unsupported HTTP Method");
        }
        chain.doFilter(req, response);
    }
}
