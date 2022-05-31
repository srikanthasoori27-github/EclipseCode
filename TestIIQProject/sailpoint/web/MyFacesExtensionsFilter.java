/* (c) Copyright 2019 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web;

import java.io.IOException;
import java.util.List;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;

import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.myfaces.webapp.filter.ExtensionsFilter;

import sailpoint.tools.Util;

/**
 * JSF 2.3 does not behave well with the commons-fileupload method of uploading files, using the Apache Faces Extensions.
 * We switched to use Primefaces uploadFile component with native upload. However, the extensions filter required for other
 * faces extensions components we use (i.e. tomahawk) is still required. When that filter processes the request, it mangles it
 * in a way that loses the UploadedFile.
 *
 * The solution is to wrap that filter in our own and exclude it on pages that we are using the <p:uploadFile /> component.
 * For all other pages, let the filter do its normal thing. Side effect is that we should not use any tomahawk components on
 * pages where we do file upload.
 */
public class MyFacesExtensionsFilter implements Filter {

    private static final Log log = LogFactory.getLog(MyFacesExtensionsFilter.class);

    public static final String EXCLUDED_URLS = "excludedUrls";
    private static final ExtensionsFilter extensionsFilter = new ExtensionsFilter();

    private List<String> excludedUrls;

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        excludedUrls = Util.csvToList(filterConfig.getInitParameter(EXCLUDED_URLS));

        extensionsFilter.init(filterConfig);
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest)servletRequest;
        String path = httpRequest.getRequestURI().substring(httpRequest.getContextPath().length());

        // Only skip extensions filter if this is multipart content, which indicates a file upload.
        // Other requests from the excluded urls should still go through the extensions filter so the
        // response can be handled consistently, especially for CSV/PDF export (see IIQSAW-2145)
        if (excludedUrls.contains(path) && ServletFileUpload.isMultipartContent(httpRequest)) {
            filterChain.doFilter(servletRequest, servletResponse);
            return;
        }

        extensionsFilter.doFilter(servletRequest, servletResponse, filterChain);
    }

    @Override
    public void destroy() {
        extensionsFilter.destroy();
    }
}
