/* (c) Copyright 2016 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.redirect;

import java.io.IOException;
import java.util.Iterator;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import sailpoint.api.SailPointContext;
import sailpoint.api.SailPointFactory;
import sailpoint.object.CertificationEntity;
import sailpoint.object.QueryOptions;
import sailpoint.tools.GeneralException;

/**
 * A ServletFilter that redirects identity certification deep links
 * to the new certification UI.
 *
 * TODO: This filter should be removed post-8.1, where we will deprecate it
 */
public class CertificationRedirectFilter implements Filter {

    private static final String CERTIFICATION_ID = "certificationId";
    private static final String ID = "id";
    private static final String ENTITY_ID = "entityId";
    private static final String CERTIFICATION_VIEW_URL = "/certification/certification.jsf#/certification/";
    private static final String ENTITY_VIEW_URL = "/certification/certification.jsf#/certification/%s/entities/%s";

    @Override
    public void doFilter(
            ServletRequest request,
            ServletResponse response,
            FilterChain chain) throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        String certificationId = httpRequest.getParameter(CERTIFICATION_ID);
        if (certificationId == null) {
            certificationId = httpRequest.getParameter(ID);
        }
        String entityId = httpRequest.getParameter(ENTITY_ID);

        try {
            if (certificationId != null) {
                httpResponse.sendRedirect(httpRequest.getContextPath() + CERTIFICATION_VIEW_URL + certificationId);
            }
            else if (entityId != null) {
                String certIdFromEntityId = this.getCertificationId(entityId);
                httpResponse.sendRedirect(httpRequest.getContextPath() + String.format(ENTITY_VIEW_URL, certIdFromEntityId, entityId));
            }
            else {
                chain.doFilter(request, response);
            }
        }
        catch (GeneralException e) {
            throw new ServletException(e);
        }
    }

    @Override
    public void destroy() {
    }

    @Override
    public void init(FilterConfig config) throws ServletException {
    }

    private SailPointContext getSailPointContext() throws GeneralException {
        return SailPointFactory.getCurrentContext();
    }

    /**
     * Get the certification id from entityId
     */
    private String getCertificationId(String entityId) throws GeneralException {

        String certId = null;
        QueryOptions options = new QueryOptions();
        options.addFilter(sailpoint.object.Filter.eq("id", entityId));
        Iterator<Object[]> results = this.getSailPointContext().search(CertificationEntity.class, options, "certification.id");
        if (results.hasNext()) {
            certId = (String)results.next()[0];
        }
        return certId;

    }

}
