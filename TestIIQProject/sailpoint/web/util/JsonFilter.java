/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.util;

import java.io.CharArrayWriter;
import java.io.IOException;
import java.io.PrintWriter;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.tools.Util;


/**
 * A Servlet Filter that strips off the <html> and <DOCTYPE> tags
 * on the response so that the caller receives only JSON in the response
 *
 * @author Peter Holcomb
 */
public class JsonFilter implements Filter {

    private static final Log log = LogFactory.getLog(JsonFilter.class);

    private final String DEFAULT_ENCODING = "UTF-8";

    // IIQMAG-3476 - The serverInfo property on the servlet context will look like:
    // "IBM WebSphere Liberty/20.0.0.12"
    private final String IBM_LIBERTY_SERVER_NAME = "IBM WebSphere Liberty";

    String _encoding;
    String _serverInfo = null;;
    ServletContext _servletContext;

    class CharResponseWrapper extends
    HttpServletResponseWrapper {
        private CharArrayWriter output;
        public String toString() {
            return output.toString();
        }
        public CharResponseWrapper(HttpServletResponse response){
            super(response);
            output = new CharArrayWriter();
        }
        public PrintWriter getWriter(){
            return new PrintWriter(output);
        }
    }
    /* (non-Javadoc)
     * @see javax.servlet.Filter#init(javax.servlet.FilterConfig)
     */
    public void init(FilterConfig config) throws ServletException {
        _servletContext = config.getServletContext();

        if(config.getInitParameter("encoding") != null) {
            this._encoding = config.getInitParameter("encoding");
        } else {
            this._encoding = DEFAULT_ENCODING;
        }

        _serverInfo = _servletContext.getServerInfo();
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
        req.setCharacterEncoding(_encoding);
        response.setCharacterEncoding(_encoding);

        // IIQMAG-3476 - Add the Content-encoding to the HTTP response header if IIQ is
        // hosted by an IBM Liberty app server. One of the servlet filters in a OOTB Liberty
        // server appears to miscalculate the content length of the JSON returned when it
        // contains a special character such as umlat, tilde, etc. even though the character
        // encoding is set to UTF-8 in the response. The miscalculation causes the browser to
        // see the JSON as an error and will not update the UI. 
        // This really feels like a problem that could or should be solved with a configuration
        // setting on the Liberty server but I've found no such setting at this time. See IIQMAG-3476
        // for more details. This code change is specifically targeted to the Liberty server as 
        // the issue was not found to be a problem on Apache or WebLogic. - jab
        if (Util.isNotNullOrEmpty(_serverInfo) && _serverInfo.contains(IBM_LIBERTY_SERVER_NAME)) {
            HttpServletResponse httpResponse = (HttpServletResponse) response;
            httpResponse.setHeader("Content-encoding", _encoding);
        }

        PrintWriter out = response.getWriter();
        CharResponseWrapper wrapper =
            new CharResponseWrapper(
                    (HttpServletResponse)response);
        chain.doFilter(req, wrapper);

        // This needs to go after the rest of the filter chain because it would get overwritten when Faces is creating
        // the response writer. See bug #19192.
        response.setContentType("application/json; charset="+_encoding);

        CharArrayWriter caw = new CharArrayWriter();

        //strip the <html> </html> tags off of the response
        final String rawOutput = wrapper.toString();
        int beginHtmlTagIndex = rawOutput.indexOf("<html");
        int endHtmlTagIndex = rawOutput.indexOf('>', beginHtmlTagIndex + 1);
        int beginIndex = endHtmlTagIndex + 1;
        int endIndex = rawOutput.indexOf("</html>");
        
        if (beginIndex >= 0 && 
            endIndex > 0 && 
            endIndex < wrapper.toString().length()) {
            caw.write(rawOutput.substring(beginIndex, endIndex));
        
            // calculate the content length correctly
            // this is to support UTF-8 characters
            byte[] byteCount = caw.toString().getBytes(_encoding);
            response.setContentLength(byteCount.length);           
            
            out.write(caw.toString());
        } else {
            if(rawOutput!=null && !rawOutput.equals(""))
                log.error("Invalid Output in Json Filter: " + rawOutput);
            out.write(rawOutput);
        }
        out.close();
    }
}
