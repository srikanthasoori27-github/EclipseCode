package sailpoint.rest.ui.workitems;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;

/**
 * Created by dan.vernon on 3/19/18.
 */
public class WorkItemListRedirectFilter implements Filter {
    protected static final String CurrentWorkItemListUrl = "/workitem/workItems.jsf#/workItems";
    protected static final String ArchiveWorkItemListUrl = "/manage/workItems/workItemArchive.jsf";
    private static final String WORKITEM_TYPE = "workItemType";
    private static final String ACTIVE_TAB_PARAM = "activeTab";
    private static final String ARCHIVE_TAB = "archiveTab";

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {

    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) servletRequest;
        HttpServletResponse httpResponse = (HttpServletResponse) servletResponse;

        String url = getRedirectURL(httpRequest.getContextPath(), servletRequest.getParameterMap());
        httpResponse.sendRedirect(url);
    }

    protected String getRedirectURL(String contextPath, Map<String, String []> queryParams) {

        StringBuilder url = new StringBuilder(contextPath);
        StringBuilder paramBuilder = new StringBuilder();
        if(queryParams != null) {
            // if it's the old archive page, redirect to the new archive page
            if (isArchivePageUrl(queryParams)) {
                url.append(ArchiveWorkItemListUrl);
                return url.toString();
            }

            // append the query params to the redirect url
            for (String key : queryParams.keySet()) {
                String[] values = queryParams.get(key);
                if (values != null) {
                    addParam(paramBuilder, key, values);
                }
            }

        }

        url.append(CurrentWorkItemListUrl);
        url.append(paramBuilder.toString());
        return url.toString();
    }

    private boolean isArchivePageUrl(Map<String, String[]> queryParams) {
        String[] values = queryParams.get(ACTIVE_TAB_PARAM);
        if((values != null) && (values.length > 0)) {
            return ARCHIVE_TAB.equals(values[0]);
        }
        return false;
    }

    private void addParam(StringBuilder paramBuilder, String key, String[] values) {
        if(values == null) {
            return;
        }

        // At present, workItemType is the only supported filter, so this is the only
        // one we will add
        for(String value : values) {
            if(WORKITEM_TYPE.equals(key)) {
                if (paramBuilder.length() == 0) {
                    paramBuilder.append("?");
                } else {
                    paramBuilder.append("&");
                }
                paramBuilder.append(key);
                paramBuilder.append("=");
                paramBuilder.append(value);
            }
        }
    }

    @Override
    public void destroy() {

    }
}
