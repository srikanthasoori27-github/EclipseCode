/* (c) Copyright 2009 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.web.util;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.WriteListener;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.tools.Util;

/**
 * A servlet filter that will attempt to dump the HTML of the page being
 * rendered to the log.
 *
 * Note that this is largely untested an mileage may vary depending on app
 * server, etc...  Use at your own risk.
 * 
 * To enable, add something like this to web.xml and turn on INFO level logging
 * for sailpoint.web.util.PageDumpingFilter.
 *
 *  <filter>
 *    <display-name>Page Dumping Filter</display-name>
 *    <filter-name>pageDumpingFilter</filter-name>
 *    <filter-class>sailpoint.web.util.PageDumpingFilter</filter-class>
 *    <init-param>
 *      <param-name>pages</param-name>
 *      <param-value>/role,/manage/certification</param-value>
 *      <description>A comma-separated list of URI substrings to dump</description>
 *    </init-param>
 *  </filter>
 *
 *  <filter-mapping>
 *    <filter-name>pageDumpingFilter</filter-name>
 *    <url-pattern>*.jsf</url-pattern>
 *  </filter-mapping>
 *
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */
public class PageDumpingFilter implements Filter {
    
    private static final Log LOG = LogFactory.getLog(PageDumpingFilter.class);

    /**
     * A servlet filter parameter that can be used to specify the pages that
     * we dump.  If not given, we dump all pages.
     */
    public static final String PARAM_PAGES = "pages";
    

    /**
     * A List of all pages that we should dump.  If this is empty, all pages
     * are dumped.
     */
    private List<String> pages;
    
    

    /* (non-Javadoc)
     * @see javax.servlet.Filter#init(javax.servlet.FilterConfig)
     */
    public void init(FilterConfig config) throws ServletException {

        this.pages = new ArrayList<String>();

        String pageNames = config.getInitParameter(PARAM_PAGES);
        if (null != Util.getString(pageNames)) {
            this.pages = Util.csvToList(pageNames);
        }
    }

    /* (non-Javadoc)
     * @see javax.servlet.Filter#destroy()
     */
    public void destroy() {
    }

    /* (non-Javadoc)
     * @see javax.servlet.Filter#doFilter(javax.servlet.ServletRequest, javax.servlet.ServletResponse, javax.servlet.FilterChain)
     */
    public void doFilter(ServletRequest request, ServletResponse response,
                         FilterChain chain)
        throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse)response;

        boolean dump =
            LOG.isInfoEnabled() && shouldDumpResponse(httpRequest, httpResponse);
        if (dump) {
            httpResponse = new CapturingResponse(httpResponse);
        }

        chain.doFilter(httpRequest, httpResponse);

        if (dump) {
            LOG.info("HTML for " + httpRequest.getRequestURI() + ":\n" + httpResponse.toString());
        }
    }

    private boolean shouldDumpResponse(HttpServletRequest req, HttpServletResponse resp) {

        // If no pages are defined, always dump.
        if (this.pages.isEmpty()) {
            return true;
        }

        // If the page contains any of the page substrings from the filter
        // param, dump.
        String uri = req.getRequestURI();
        for (String current : this.pages) {
            if (uri.contains(current)) {
                return true;
            }
        }

        // No match ... hold it.
        return false;
    }


    /**
     * An HttpServletResponse that will capture all output sent to either the
     * writer or the output stream so that we can log it.
     */
    private static class CapturingResponse extends HttpServletResponseWrapper {

        private PrintWriter writer;
        private ServletOutputStream stream;
        
        public CapturingResponse(HttpServletResponse resp) {
            super(resp);
            try {
                writer = new DuplexingWriter(resp.getWriter());
            } catch (IOException e) { throw new RuntimeException(e); }
        }

        public PrintWriter getWriter() {
            return writer;
        }
        
        @Override
        public ServletOutputStream getOutputStream() throws IOException {
            if (null == stream) {
                stream = new WritingServletOutputStream(this.writer);
            }
            return stream;
        }

        public String toString() {
            return this.writer.toString();
        }
    }

    /**
     * A ServletOutputStream that redirects all output operations to a given
     * writer.  I think that most ServletOutputStream methods end up going
     * to a print(String) or write(int) call, so I just overrode these.  This
     * seems to do the trick for our purposes here.
     */
    private static class WritingServletOutputStream extends ServletOutputStream {
        
        private Writer writer;
        
        public WritingServletOutputStream(Writer writer) {
            this.writer = writer;
        }

        @Override
        public void print(String arg0) throws IOException {
            this.writer.write(arg0);
        }

        @Override
        public boolean isReady() {
            return false;
        }

        @Override
        public void setWriteListener(WriteListener writeListener) {

        }

        @Override
        public void write(int b) throws IOException {
            this.writer.write(b);
        }
    }
    
    
    /**
     * A PrintWriter that will write to a delegate writer and to a StringWriter.
     * This allows the StringWriter to capture everything being written and
     * later retrieved.
     * 
     * Note that this implementation is quite a hack and not all of the methods
     * do the right thing.  For now it's good enough for debugging. 
     */
    private static class DuplexingWriter extends PrintWriter {
        
        private PrintWriter delegate;
        private StringWriter stringWriter;

        /**
         * Constructor.
         */
        public DuplexingWriter(PrintWriter delegate) {
            super(new StringWriter());
            this.delegate = delegate;
            this.stringWriter = new StringWriter();
        }

        @Override
        public String toString() {
            return this.stringWriter.toString();
        }
        
        @Override
        public PrintWriter append(char c) {
            stringWriter.append(c);
            delegate.append(c);
            return this;
        }

        @Override
        public PrintWriter append(CharSequence csq, int start, int end) {
            stringWriter.append(csq, start, end);
            delegate.append(csq, start, end);
            return this;
        }

        @Override
        public PrintWriter append(CharSequence csq) {
            stringWriter.append(csq);
            delegate.append(csq);
            return this;
        }

        @Override
        public boolean checkError() {
            return delegate.checkError();
        }

        @Override
        public void close() {
            delegate.close();
        }

        @Override
        public void flush() {
            stringWriter.flush();
            delegate.flush();
        }

        @Override
        public PrintWriter format(Locale l, String format, Object... args) {
            delegate.format(l, format, args);
            return this;
        }

        @Override
        public PrintWriter format(String format, Object... args) {
            delegate.format(format, args);
            return this;
        }

        @Override
        public void print(boolean b) {
            delegate.print(b);
        }

        @Override
        public void print(char c) {
            stringWriter.write(c);
            delegate.print(c);
        }

        @Override
        public void print(char[] s) {
            try {
                stringWriter.write(s);
            } catch (IOException e) { throw new RuntimeException(e); }
            delegate.print(s);
        }

        @Override
        public void print(double d) {
            delegate.print(d);
        }

        @Override
        public void print(float f) {
            delegate.print(f);
        }

        @Override
        public void print(int i) {
            delegate.print(i);
        }

        @Override
        public void print(long l) {
            delegate.print(l);
        }

        @Override
        public void print(Object obj) {
            stringWriter.write((null != obj) ? obj.toString() : null);
            delegate.print(obj);
        }

        @Override
        public void print(String s) {
            stringWriter.write(s);
            delegate.print(s);
        }

        @Override
        public PrintWriter printf(Locale l, String format, Object... args) {
            delegate.printf(l, format, args);
            return this;
        }

        @Override
        public PrintWriter printf(String format, Object... args) {
            delegate.printf(format, args);
            return this;
        }

        @Override
        public void println() {
            stringWriter.write("\n");
            delegate.println();
        }

        @Override
        public void println(boolean x) {
            delegate.println(x);
        }

        @Override
        public void println(char x) {
            delegate.println(x);
        }

        @Override
        public void println(char[] x) {
            delegate.println(x);
        }

        @Override
        public void println(double x) {
            delegate.println(x);
        }

        @Override
        public void println(float x) {
            delegate.println(x);
        }

        @Override
        public void println(int x) {
            delegate.println(x);
        }

        @Override
        public void println(long x) {
            delegate.println(x);
        }

        @Override
        public void println(Object x) {
            delegate.println(x);
        }

        @Override
        public void println(String x) {
            delegate.println(x);
        }

        @Override
        public void write(char[] buf, int off, int len) {
            stringWriter.write(buf, off, len);
            delegate.write(buf, off, len);
        }

        @Override
        public void write(char[] buf) {
            try {
                stringWriter.write(buf);
            } catch (IOException e) { throw new RuntimeException(e); }
            delegate.write(buf);
        }

        @Override
        public void write(int c) {
            stringWriter.write(c);
            delegate.write(c);
        }

        @Override
        public void write(String s, int off, int len) {
            stringWriter.write(s, off, len);
            delegate.write(s, off, len);
        }

        @Override
        public void write(String s) {
            stringWriter.write(s);
            delegate.write(s);
        }
    }
}
