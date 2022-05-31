/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.web;

import java.util.List;
import java.util.Map;

import javax.faces.application.FacesMessage;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.tools.GeneralException;

/**
 * @author jonathan.bryant@sailpoint.com
 */
public class RegistrationBean extends BaseBean {

    private static final Log log = LogFactory.getLog(RegistrationBean.class);

    /* Matches "flow" variable in LCM Registration workflow. Typically these are in XXXRequestBean, but we
     * don't have one for self-service registration, so stick it here */
    public static final String FLOW_CONFIG_NAME = "Registration";

    /**
     * Constant used to detect a mobile registration launch.
     */
    public static final String ATT_MOBILE_SSR_LAUNCH = "mobileRegistrationLaunch";
    public static final String SELF_SERVICE_REGISTRATION_REDIRECT_PATH = "selfServiceRegistrationRedirectPath";

    public String getState() {
        Map session = getSessionScope();
        return null;
    }

    /**
     * Return a boolean that indicates whether any errors were encountered during
     * the Self-Service Registration workflow. Errors are added to the Faces context
     * in the WorkItemFormBean so they can be displayed in the JSF pages.
     *
     * @return boolean true if errors were found
     */
    public boolean getHasErrors() {
        Map session = getFacesContext().getExternalContext().getSessionMap();
        List<FacesMessage> msgs = (List<FacesMessage>) session.get(PageCodeBase.SESSION_MESSAGES);

        if (null != msgs) {
            for (FacesMessage msg : msgs) {
                if (msg.getSeverity() == FacesMessage.SEVERITY_ERROR ||
                    msg.getSeverity() == FacesMessage.SEVERITY_FATAL) {
                    return true;
                }
            }
        }

        return false;
    }

    //back to login page
    public String login() {
        return "logoutSuccess";
    }

    public String getRegistrationLoginOutcome() {
        String outcome = "logoutSuccess";

        // if we started from mobile login then return there
        if (getSessionScope().containsKey(ATT_MOBILE_SSR_LAUNCH)) {
            getSessionScope().remove(ATT_MOBILE_SSR_LAUNCH);
            outcome = "mobileLogoutSuccess";
        }

        return outcome;
    }

    /**
     * Get URL configured in system setup, or return null to default to redirecting to the login page.
     *
     * @return
     */
    public String getSelfServiceRedirectPath() {
        StringBuilder url = new StringBuilder();
        try {
            String path = getContext().getConfiguration().getString(SELF_SERVICE_REGISTRATION_REDIRECT_PATH);
            if (path != null) {
                url.append(path);
            }
        } catch (GeneralException e) {
            if (log.isDebugEnabled()) {
                log.debug("Error getting system config: " + e.getMessage());
            }
        }
        if (url.length() > 0) {
            return url.toString();
        }
        return null;
    }

}
