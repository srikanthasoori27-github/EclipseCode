/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/*
 * A Servlet Filter that looks for #{...} references in Javascript
 * files and replaces them with message catalog strings.
 *
 * While at first glance these look like el expressions they're not, 
 * we assume anything within the {} is a catalog key.  To make this
 * more consistent with xhtml pages we will also support the
 * {msgs.foo} convention and just ignore the msgs prefix.
 *
 * Author: Jeff Larson
 *
 * This isn't working, I've got something wrong in the filter chain 
 * handling.  Revisit...
 */

package sailpoint.web.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.Locale;

import javax.faces.FactoryFinder;
import javax.faces.component.UIViewRoot;
import javax.faces.context.FacesContext;
import javax.faces.context.FacesContextFactory;
import javax.faces.lifecycle.Lifecycle;
import javax.faces.lifecycle.LifecycleFactory;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.WriteListener;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;

import sailpoint.tools.Internationalizer;


public class JavascriptFilter implements Filter {

    public static final String DEFAULT_ENCODING = "UTF-8";

    /**
     * Locale used for message rendering.
     */
    Locale _locale;
    String _encoding;
    ServletContext _servletContext;

    /**
     * Wrapper we put around the HttpServletResponse 
     * to capture the Javascript. Use the ByteArrayOutputStream to avoid encoding issues.   
     */
    class JavascriptResponseWrapper extends HttpServletResponseWrapper {

        private ByteArrayOutputStream output;

        public JavascriptResponseWrapper(HttpServletResponse response) {
            super(response);
            output = new ByteArrayOutputStream();			
        }

        @Override
        public PrintWriter getWriter() {
            return new PrintWriter(output);
        }
        
        @Override
        public ServletOutputStream getOutputStream() throws IOException {
            return new ServletOutputStream() {
                /**
                 * Everything in ServletOutputStream ends up calling through to
                 * the write() method.  Override this to write to our output
                 * buffer that we're capturing.
                 */
                @Override
                public void write(int b) throws IOException {
                    output.write(b);
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setWriteListener(WriteListener writeListener) {

                }
            };
        }

        /**
         * Get the string contents using the default charset
         * @return String contents of the response
         */
        @Override
        public String toString() {
            return output.toString();
        }

        /**
         * Get the string contents using the given charset
         * @param charset Charset name 
         * @return String contents of the response
         * @throws UnsupportedEncodingException
         */
        public String toString(String charset) throws UnsupportedEncodingException {
            return output.toString(charset);
        }
        
        @Override
        public void flushBuffer() {
            //DO NOTHING: We do not want to commit the response
        }
    }

    
    /** The following gives us access to the faces context which we need in order to get at the locale **/
    private abstract static class InnerFacesContext extends FacesContext {
        protected static void setFacesContextAsCurrentInstance(final FacesContext facesContext) {
            FacesContext.setCurrentInstance(facesContext);
        }
    }

    private FacesContext getFacesContext(final ServletRequest request, final ServletResponse response) {

        FacesContext facesContext = FacesContext.getCurrentInstance();
        if (facesContext != null) {
            return facesContext;
        }

        FacesContextFactory contextFactory = (FacesContextFactory)FactoryFinder.getFactory(FactoryFinder. FACES_CONTEXT_FACTORY);
        LifecycleFactory lifecycleFactory = (LifecycleFactory)FactoryFinder.getFactory(FactoryFinder. LIFECYCLE_FACTORY);
        Lifecycle lifecycle = lifecycleFactory.getLifecycle(LifecycleFactory. DEFAULT_LIFECYCLE);

        facesContext = contextFactory.getFacesContext(_servletContext, request, response, lifecycle);

        InnerFacesContext.setFacesContextAsCurrentInstance(facesContext);
        UIViewRoot view = facesContext.getApplication().getViewHandler().createView(facesContext, "/login");
        facesContext.setViewRoot(view);

        return facesContext;
    }

    public JavascriptFilter() {
    }

    /**
     * Extract the <init-param>s.
     */
    public void init(FilterConfig config) throws ServletException {
        _servletContext = config.getServletContext();
        if(config.getInitParameter("encoding")!=null) {
            this._encoding = config.getInitParameter("encoding");
        } else {
            this._encoding = DEFAULT_ENCODING;
        }
    }

    public void destroy() {
    }

    /**
     * Wrap the response, let the chain continue and capture the result.
     * Pass the raw result through the reference expander, 
     * and sent it on it's way.
     */
    public void doFilter(ServletRequest req, ServletResponse response,
                         FilterChain chain)
            throws IOException, ServletException {
        req.setCharacterEncoding(_encoding);
        response.setContentType("text/javascript; charset="+_encoding);
        response.setCharacterEncoding(_encoding);

        PrintWriter out = response.getWriter();
        // determine Locale and TimeZone
        // XHTML pages determine the locale using
        // FacesContext.getViewRoot().getLocale()
        // Need to implement this in the same way!
        // Are there standard session attributes to look at?

        _locale = Locale.getDefault();

        FacesContext facesContext = getFacesContext(req, response);
        if(facesContext!=null) {
            _locale = facesContext.getViewRoot().getLocale();
        }

        // wrap the response, let the chain continue and capture the result
        JavascriptResponseWrapper wrapper =
                new JavascriptResponseWrapper((HttpServletResponse)response);
        chain.doFilter(req, wrapper);

        // Convert the raw contents of the response to a utf-8 encoded string
        String raw = wrapper.toString("UTF-8");
        // expand references
        String cooked = FilterHelper.expandReferences(raw, _locale);
        // pass it along
        byte[] msg = cooked.getBytes(_encoding);
        response.setContentLength(msg.length);

        out.write(cooked);
        out.close();
    }

    public static final class FilterHelper
    {
        private FilterHelper(){}
        
        public static String expandReferences(String src, Locale locale) {

            String expanded = null;
            if (src != null) {
                if (src.indexOf("#{") == -1) {
                    // no references, optimize
                    expanded = src;
                }
                else {
                    StringBuffer b = new StringBuffer();

                    int max = src.length();
                    for (int i = 0 ; i < max ; i++) {
                        char ch = src.charAt(i);
                        
                        
                        if (ch != '#')
                            b.append(ch);
                        else {
                            i++;
                            if (i < max) {
                                char next = src.charAt(i);
                                if (src.charAt(i) != '{') {
                                    // a # without { is like a day without sunshine
                                    b.append(ch);
                                    b.append(next);
                                }
                                else {
                                    // smells like a teen reference
                                    i++;
                                    int start = i;
                                    int end = start;

                                    while (end < max && src.charAt(end) != '}')
                                        end++;

                                    if (end > start) {
                                        String name = src.substring(start, end);
                                        String sub = resolve(name, locale);
                                        b.append(sub);
                                    }
                                    i = end;
                                }
                            }
                        }
                    }
                    expanded = b.toString();
                }
            }
            return expanded;
        }

        /**
         * Given something from within a #{...} expression
         * resolve it to a substitution value.
         *
         * The only thing we support right now are message catalog lookups.
         */
        public static String resolve(String symbol, Locale locale) {
            // strip this off so we look more like xhtml
            String key = symbol;
            String msgsPrefix = "msgs.";
            String helpPrefix = "help.";
            if (symbol.startsWith(msgsPrefix) || symbol.startsWith(helpPrefix)) {
                key = symbol.substring(5);
            }
            String result = Internationalizer.getMessage(key, locale);
            if (result == null)
                result = symbol;
            
            result = WebUtil.escapeJavascript(result);
            return result;
        }

    }

}
