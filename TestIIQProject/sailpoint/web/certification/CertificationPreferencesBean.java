package sailpoint.web.certification;

import java.util.Map;

import sailpoint.tools.GeneralException;
import sailpoint.web.BaseBean;

/**
 * @author jonathan.bryant@sailpoint.com
 */
public class CertificationPreferencesBean extends BaseBean {

    private static final String SESSION_DEF_VIEW_PREF = "certificationDefaultViewPreference";
    private static final String SESSION_GRID_VIEW_PREF = "certificationGridViewPreference";

    private String certificationId;

    public CertificationPreferencesBean() {
        super();
        if (getRequestParameter("id") != null)
            certificationId = getRequestParameter("id");
        if (getRequestParameter("certificationId") != null)
            certificationId = getRequestParameter("certificationId");
    }

    public CertificationPreferencesBean(String certificationId) {
        super();
        this.certificationId = certificationId;
    }

    /**
     * Get the action string for the responsive certifications.
     * @return JSF action String for the certification
     * @throws GeneralException
     */
    public String getDefaultView() {
        return "viewResponsiveCertification#/certification/" + this.certificationId;
    }

    public static void clearUserPreferences(Map<?,?> session) {
        if (session.containsKey(SESSION_DEF_VIEW_PREF))
            session.remove(SESSION_DEF_VIEW_PREF);
        if (session.containsKey(SESSION_GRID_VIEW_PREF))
            session.remove(SESSION_GRID_VIEW_PREF);
    }
}
