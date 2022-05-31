package sailpoint.rest.ui;

import sailpoint.object.Configuration;
import sailpoint.rest.ui.apponboard.ApplicationOnboardResource;
import sailpoint.rest.ui.apponboard.IdentityTriggerResource;
import sailpoint.rest.ui.certifications.schedule.CertificationScheduleResource;
import sailpoint.rest.ui.fam.FAMWidgetResource;

/**
 * The paths to the resources are here.
 * We use this instead of hard coding the paths so that
 * it can be consistently called from unit tests etc.
 * 
 * Please make sure to have the path hierarchical like they are
 * using inner classes.
 * 
 * @author tapash.majumder
 *
 */
public class Paths {
    
    /**
     * The base path to {@link UserResetResource}
     */
    public static final String USER_RESET = "userReset";

    /**
     * Base path for redirect service used with AngularJS
     */
    public static final String REDIRECT = "redirect";

    /**
     * Base path to {@link CertificationScheduleResource}
     */
    public static final String CERTIFICATION_SCHEDULE = "certificationSchedule";

    /**
     * Base path to {@link ApplicationOnboardResource}
     */
    public static final String APPLICATION_ONBOARD = "appOnboard";

    /**
     * Base path to {@link IdentityTriggerResource}
     */
    public static final String IDENTITY_TRIGGERS = "identityTriggers";

    /**
     * Base path to {@link FAMWidgetResource}
     */
    public static final String FAM_WIDGET = "FAMWidget";

    /**
     * Base path for Config resource
     */
    public static final String CONFIGURATION = "configuration";
    public static final String FAMCONFIG = Configuration.FAM_CONFIG;
    public static final String TEST = "test";
    public static final String UICONFIG = "uiconfig/";
    public static final String ENTRIES = "entries";
    public static final String IDENTITY = "identity";
    public static final String ACCESS_REQUEST = "accessRequest";

    /**
     * Base path for Suggest resources
     */
    public static final String SUGGEST = "suggest";

    /**
     * Base path for filters
     */
    public static final String FILTERS = "filters";


    /**
     * Paths for {@link UserResetResource}
     *
     */
    public static class UserReset {
        public static final String SEND_SMS = "sendSMS";
        public static final String CHANGE_PASSWORD = "changePassword";
        public static final String UNLOCK_ACCOUNT = "unlockAccount";
        public static final String AUTH_QUESTIONS = "authQuestions";
        public static final String LOGIN = "login";
    }

    /**
     * Paths for {@link FAMWidgetResource}
     *
     */
    public static class FAMWidget {
        public static final String SENSITIVE_DATA = "overexposedSensitiveResources";
        public static final String SENSITIVE_RESOURCE = "resourcesClassificationNoOwnership";
    }
}
