package sailpoint.rest.jaxrs;

import java.io.IOException;
import java.util.List;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;

import sailpoint.tools.Util;

/**
 * Support verb tunneling through POST requests, initially to allow for PATCH requests.  
 * Set of supported methods are configured with the 'allowedMethods' parameter.
 */
public class MethodOverrideFilter implements Filter {

    /**
     * Wrap the request so we can return the overridden method 
     */
    public class MethodOverrideRequestWrapper extends HttpServletRequestWrapper {
        private String method;

        public MethodOverrideRequestWrapper(HttpServletRequest request, String method) {
            super(request);
            
            this.method = method;
        }

        @Override
        public String getMethod() {
            if (this.method != null) {
                return this.method;
            }

            return super.getMethod();
        }
    }
    
    private static final String ALLOWED_METHODS = "allowedMethods";
    private static final String[] OVERRIDE_HEADERS = {
            "X-HTTP-Method-Override",
            "X-HTTP-Method",
            "X-METHOD-OVERRIDE"
    };
    
    private List<String> allowedMethodOverrides;

    /* (non-Javadoc)
     * @see javax.servlet.Filter#init(javax.servlet.FilterConfig)
     */
    public void init(FilterConfig config) throws ServletException {
        String allowedMethods = config.getInitParameter(ALLOWED_METHODS);
        this.allowedMethodOverrides = Util.csvToList(allowedMethods);
    }

    /* (non-Javadoc)
     * @see javax.servlet.Filter#destroy()
     */
    public void destroy() {
        // no-op
    }

    /* (non-Javadoc)
     * @see javax.servlet.Filter#doFilter(javax.servlet.ServletRequest, javax.servlet.ServletResponse, javax.servlet.FilterChain)
     */
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        ServletRequest chainRequest = request;
        HttpServletRequest httpRequest = (HttpServletRequest)request;
        
        // Check for HTTP POST with one of the headers that can override the method.
        if (httpRequest.getMethod().equals("POST")) {
            for (String overrideHeader : OVERRIDE_HEADERS) {
                if (httpRequest.getHeader(overrideHeader) != null) {
                    String override = httpRequest.getHeader(overrideHeader);
                    if (this.allowedMethodOverrides.contains(override)) {
                        chainRequest = new MethodOverrideRequestWrapper(httpRequest, override);
                        break;
                    }
                }
            }
        }
        
        chain.doFilter(chainRequest, response);        
   }
}