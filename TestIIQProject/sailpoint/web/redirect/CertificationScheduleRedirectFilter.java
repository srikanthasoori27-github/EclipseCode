/* (c) Copyright 2018 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.redirect;

import sailpoint.api.SailPointContext;
import sailpoint.api.SailPointFactory;
import sailpoint.object.Certification;
import sailpoint.object.CertificationDefinition;
import sailpoint.object.CertificationGroup;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;

/**
 * Servelet filter to handle redirects to responsive certification schedule page for Focused certs ... relies on
 * parameters sent from the CertificationScheduleListBean and CertificationGroupListBean.
 */
public class CertificationScheduleRedirectFilter implements javax.servlet.Filter {

    private static final String FLEXIBLE_CERTIFICATION_SCHEDULE_URL_NEW = "/certification/schedule.jsf#/schedule/new";
    private static final String FLEXIBLE_CERTIFICATION_SCHEDULE_URL_NEW_CERT_GROUP = "/certification/schedule.jsf#/schedule/new?certificationGroupId=%s";
    private static final String FLEXIBLE_CERTIFICATION_SCHEDULE_URL_EDIT = "/certification/schedule.jsf#/schedule/edit?scheduleId=%s";
    private static final String FLEXIBLE_CERTIFICATION_SCHEDULE_URL_EDIT_CERT_GROUP = "/certification/schedule.jsf#/schedule/edit?certificationGroupId=%s";

    public static final String PARAMETER_TYPE = "type";
    public static final String PARAMETER_IS_NEW = "new";
    public static final String PARAMETER_SCHEDULE_ID = "scheduleId";
    public static final String PARAMETER_CERT_GROUP = "certGroup";

    /**
     * Compare the given certification type to see if it is a "new" type that
     * should be handled by the responsive certification schedule page
     * @param type The Certification.Type
     * @return True if new type, otherwise fales.
     */
    public static boolean isNewScheduleType(Certification.Type type) {
        return Certification.Type.Focused.equals(type);
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        // NOTHING TO DO
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) servletRequest;
        HttpServletResponse httpResponse = (HttpServletResponse) servletResponse;
        String type = httpRequest.getParameter(PARAMETER_TYPE);
        boolean isNew = Util.atob(httpRequest.getParameter(PARAMETER_IS_NEW));
        String scheduleId = httpRequest.getParameter(PARAMETER_SCHEDULE_ID);
        String certGroupId = httpRequest.getParameter(PARAMETER_CERT_GROUP);

        try {
            if (isNew && isNewScheduleType(Certification.Type.valueOf(type))) {
                // New cert of new type, redirect to the 'new' url with optional certification group id
                if (Util.isNullOrEmpty(certGroupId)) {
                    httpResponse.sendRedirect(httpRequest.getContextPath() + FLEXIBLE_CERTIFICATION_SCHEDULE_URL_NEW);
                } else {
                    httpResponse.sendRedirect(httpRequest.getContextPath() + String.format(FLEXIBLE_CERTIFICATION_SCHEDULE_URL_NEW_CERT_GROUP, certGroupId));
                }
            } else if (!Util.isNullOrEmpty(scheduleId)) {
                // Editing based on schedule id, redirect
                httpResponse.sendRedirect(httpRequest.getContextPath() + String.format(FLEXIBLE_CERTIFICATION_SCHEDULE_URL_EDIT, URLEncoder.encode(scheduleId, "UTF-8")));
            } else if (!Util.isNullOrEmpty(certGroupId)) {
                // Editing based on certification group id, load up the group and check the type before redirecting
                CertificationGroup certificationGroup = getSailPointContext().getObjectById(CertificationGroup.class, certGroupId);
                if (certificationGroup != null) {
                    CertificationDefinition certificationDefinition = certificationGroup.getDefinition();
                    if (certificationDefinition != null && isNewScheduleType(certificationDefinition.getType())) {
                        httpResponse.sendRedirect(httpRequest.getContextPath() + String.format(FLEXIBLE_CERTIFICATION_SCHEDULE_URL_EDIT_CERT_GROUP, certGroupId));
                    } else {
                        filterChain.doFilter(servletRequest, servletResponse);
                    }
                } else {
                    filterChain.doFilter(servletRequest, servletResponse);
                }
            } else {
                filterChain.doFilter(servletRequest, servletResponse);
            }
        } catch (GeneralException ge) {
            throw new ServletException(ge);
        }
    }

    private SailPointContext getSailPointContext() throws GeneralException {
        return SailPointFactory.getCurrentContext();
    }

    @Override
    public void destroy() {
        // NOTHING TO DO
    }
}
