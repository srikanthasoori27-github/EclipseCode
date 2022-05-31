package sailpoint.service.identitypreferences;

import sailpoint.api.Identitizer;
import sailpoint.api.PasswordPolice;
import sailpoint.api.PasswordPolicyException;
import sailpoint.api.SailPointContext;
import sailpoint.object.*;
import sailpoint.service.IdentitySummaryDTO;
import sailpoint.service.identity.ForwardingInfoDTO;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;

import java.util.*;

/**
 * Service for identity preferences
 */
public class IdentityPreferencesService {
    private SailPointContext context;
    private Identity user;

    public static final String SHOW_EDIT_FORWARDING = "showEditForwarding";
    public static final String SHOW_CHANGE_PASSWORD_TAB = "showChangePasswordTab";
    public static final String SHOW_UPDATE_SECURITY_PREFERENCES_TAB = "showUpdateSecurityQuestionsTab";
    public static final String SHOW_SYSTEM_USER_ATTRIBUTES = "showSystemUserAttributes";
    public static final String SHOW_DISABLE_NOTIFICATIONS = "showDisableNotifications";

    public static final String FIRST_NAME = "firstName";
    public static final String LAST_NAME = "lastName";
    public static final String EMAIL = "emailAddress";

    public static final String FORWARD_USER = "forwardUser";
    public static final String FORWARD_START_DATE = "forwardStartDate";
    public static final String FORWARD_END_DATE = "forwardEndDate";

    public static final String DISABLE_NOTIFICATIONS = "disableNotifications";

    public static final String REQUIRE_CURRENT_PASSWORD = "requireCurrentPassword";

    private List<String> validationErrors = new ArrayList<>();

    private final List<String> SHOW_CONFIGS = Arrays.asList(SHOW_EDIT_FORWARDING, SHOW_CHANGE_PASSWORD_TAB, 
            SHOW_UPDATE_SECURITY_PREFERENCES_TAB, SHOW_SYSTEM_USER_ATTRIBUTES, SHOW_DISABLE_NOTIFICATIONS);

    public IdentityPreferencesService(SailPointContext context, Identity user) {
        this.context = context;
        this.user = user;
    }

    /**
     * Get the identity preferences page config.
     *
     * @param isSsoAuthenticated true if session is SSO authenticated
     * @return Map of config values
     * @throws GeneralException
     */
    public Map<String, Object> getConfig(boolean isSsoAuthenticated) throws GeneralException {
        Map<String, Object> configMap = new HashMap<>();

        if (this.context == null) {
            return configMap;
        }

        Configuration config = this.context.getConfiguration();

        // show edit forwarding form
        boolean enableEditForwarding = config.getBoolean(Configuration.SHOW_EDIT_FORWARDING, true);
        configMap.put(SHOW_EDIT_FORWARDING, enableEditForwarding);

        // show change password tab
        boolean enableChangePassword = config.getBoolean(Configuration.SHOW_CHANGE_PASSWORD_TAB, true);
        configMap.put(SHOW_CHANGE_PASSWORD_TAB, enableChangePassword && !isSsoAuthenticated);

        if (enableChangePassword) {
            boolean requireCurrentPassword = config.getBoolean(Configuration.REQUIRE_OLD_PASSWORD_AT_CHANGE);
            configMap.put(REQUIRE_CURRENT_PASSWORD, requireCurrentPassword);
        }

        // derive from existing config options
        boolean enableForgotPassword = config.getBoolean(Configuration.ENABLE_FORGOT_PASSWORD);
        boolean authQuestionsEnabled = config.getBoolean(Configuration.AUTH_QUESTIONS_ENABLED);
        configMap.put(SHOW_UPDATE_SECURITY_PREFERENCES_TAB, enableForgotPassword && authQuestionsEnabled);

        // show disable notifications
        boolean allowDisableNotifications = config.getBoolean(Configuration.ALLOW_DISABLE_NOTIFICATIONS, true);
        configMap.put(SHOW_DISABLE_NOTIFICATIONS, allowDisableNotifications);


        // show system user attributes for system user (protected)
        if (this.user != null) {
            configMap.put(SHOW_SYSTEM_USER_ATTRIBUTES, user.isProtected());
        }

        return configMap;
    }

    /**
     * Check to see if there are any edit preferences to show. Used to toggle the menu link.
     * Only config values that are part of the SHOW_CONFIGS list matter.
     * @return boolean true if there are preferences to edit
     * @throws GeneralException
     */
    public boolean isPreferencesEnabled(boolean isSsoAuthenticated) throws GeneralException {
        Map<String, Object> configMap = this.getConfig(isSsoAuthenticated);

        Map.Entry<String, Object> result =
                configMap.entrySet().stream()
                .filter(config -> SHOW_CONFIGS.contains(config.getKey()) && Util.otob(config.getValue()))
                .findFirst()
                .orElse(null);

        return result != null;
    }

    /**
     * Get the forwarding user preferences
     * @return ForwardingInfoDTO
     * @throws GeneralException
     */
    public ForwardingInfoDTO getIdentityForwardingPreferences() throws GeneralException {
        return new ForwardingInfoDTO(this.user, context);
    }

    /**
     * Update the logged in user forwarding preferences
     * @param data containing forwarding preferences
     * @return List<String> list of any validation errors
     * @throws GeneralException
     */
    public List<String> updateIdentityForwardingPreferences(Map<String, Object> data) throws GeneralException {
        if (data == null || data.isEmpty()) {
            return validationErrors;
        }

        Map userDataMap = (Map)data.get(FORWARD_USER);
        IdentitySummaryDTO forwardUser = null;
        if (!Util.isEmpty(userDataMap)) {
            forwardUser = new IdentitySummaryDTO(userDataMap);

            if (Util.isNotNullOrEmpty(forwardUser.getId())) {
                Identity forwardIdentity = context.getObjectById(Identity.class, forwardUser.getId());
                if (forwardIdentity == null) {
                    return validationErrors;
                }
            }
        }

        Date forwardStartDate = null;
        Date forwardEndDate = null;

        // only process dates if there is a forward user set
        if (forwardUser != null) {
            // clear out any existing errors
            validationErrors.clear();

            Long startDate = (Long)data.get(FORWARD_START_DATE);
            Long endDate = (Long)data.get(FORWARD_END_DATE);

            if (startDate != null) {
                Calendar cal = Calendar.getInstance();
                cal.setTime(new Date(startDate));
                cal.set(Calendar.HOUR_OF_DAY, 23);
                cal.set(Calendar.MINUTE, 59);
                cal.set(Calendar.SECOND, 58);
                forwardStartDate = cal.getTime();
            }

            if (endDate != null) {
                Calendar cal = Calendar.getInstance();
                cal.setTime(new Date(endDate));
                cal.set(Calendar.HOUR_OF_DAY, 23);
                cal.set(Calendar.MINUTE, 59);
                cal.set(Calendar.SECOND, 59);
                forwardEndDate = cal.getTime();
            }

            if (startDate != null || endDate != null) {
                if (!validateStartAndEndDates(forwardStartDate, forwardEndDate)) {
                    return validationErrors;
                }
            }
        }

        this.user.setPreference(Identity.PRF_FORWARD, forwardUser != null ? forwardUser.getName() : null);
        this.user.setPreference(Identity.PRF_FORWARD_START_DATE, forwardStartDate);
        this.user.setPreference(Identity.PRF_FORWARD_END_DATE, forwardEndDate);

        this.saveUser();

        return validationErrors;
    }

    /**
     * Get the system user attributes
     * @return Map system user attributes
     */
    public Map<String, Object> getSystemUserAttributes() {
        Map<String, Object> systemUserAttributes = new HashMap<>();

        // return empty map for non system user
        if (!this.user.isProtected()) {
            return systemUserAttributes;
        }

        systemUserAttributes.put(FIRST_NAME, this.user.getFirstname());
        systemUserAttributes.put(LAST_NAME, this.user.getLastname());
        systemUserAttributes.put(EMAIL, this.user.getEmail());
        return systemUserAttributes;
    }

    /**
     * Set the system user attributes
     *
     * @param data
     */
    public void setSystemUserAttributes(Map<String, Object> data) throws GeneralException {
        if (data == null || data.isEmpty()) {
            return;
        }

        this.user.setFirstname((String)data.get(FIRST_NAME));
        this.user.setLastname((String)data.get(LAST_NAME));
        this.user.setEmail((String)data.get(EMAIL));

        this.saveUser();
    }

    /**
     * Update the logged in users password.
     * @param currentPassword
     * @param newPassword
     * @return List<String> list of errors
     */
    public List<Message> updatePassword(String currentPassword, String newPassword, String confirmPassword) throws GeneralException {
        List<Message> errors = new ArrayList<>();

        // nothing to update if no input
        if (Util.isNullOrEmpty(newPassword)) {
            errors.add(new Message(MessageKeys.LOGIN_MISSING_PASSWORD));
            return errors;
        }

        if (Util.isNullOrEmpty(confirmPassword)) {
            errors.add(new Message(MessageKeys.LOGIN_MISSING_CONFIRMATION));
            return errors;
        }

        Configuration systemConfig = this.context.getConfiguration();
        boolean requireCurrentPassword = systemConfig.getBoolean(Configuration.REQUIRE_OLD_PASSWORD_AT_CHANGE);

        try {
            PasswordPolice passwordPolice  = new PasswordPolice(this.context);
            passwordPolice.validatePasswordFields(newPassword, confirmPassword, requireCurrentPassword, currentPassword);

            boolean isSystemAdmin = Capability.hasSystemAdministrator(this.user.getCapabilityManager().getEffectiveCapabilities());
            boolean isPasswordAdmin = isSystemAdmin || this.user.getCapabilityManager().getEffectiveFlattenedRights().contains(SPRight.SetIdentityPassword);

            if (requireCurrentPassword) {
                passwordPolice.setPassword(this.user, newPassword, currentPassword,
                        PasswordPolice.Expiry.USE_SYSTEM_EXPIRY, isSystemAdmin, isPasswordAdmin);

            }
            else {
                passwordPolice.setPassword(this.user, newPassword, PasswordPolice.Expiry.USE_SYSTEM_EXPIRY,
                        isSystemAdmin, isPasswordAdmin);
            }

            this.saveUser();
        }
        catch (PasswordPolicyException pve) {
            errors = pve.getAllMessages();
        }

        return errors;
    }

    /**
     * Save the user and audit
     * @throws GeneralException
     */
    private void saveUser() throws GeneralException {
        Identitizer i = new Identitizer(this.context);
        i.setRefreshSource(Source.UI, this.user.getDisplayableName());
        i.saveAndTrigger(this.user, true);
    }


    /**
     * Validates that the start date is set to a value that
     * is before the end date. Also check that dates are not in the past.
     *
     * @return True if start is before end; false otherwise
     */
    private boolean validateStartAndEndDates(Date forwardStartDate, Date forwardEndDate) {
        boolean valid = true;

        if (forwardStartDate != null) {
            valid = valid && validateFutureDate(forwardStartDate);
        }

        if (forwardEndDate != null) {
            valid = valid && validateFutureDate(forwardEndDate);
        }

        if (forwardStartDate != null && forwardEndDate != null) {
            valid = valid && validateStartDateBeforeEndDate(forwardStartDate, forwardEndDate);
        }

        return valid;
    }

    /**
     * Make sure theDate is in the future
     * @param theDate
     * @return True if date is valid
     */
    private boolean validateFutureDate(Date theDate) {
        if (theDate == null ) {
            validationErrors.add(new Message(Message.Type.Error, MessageKeys.ERR_DATE_INVALID).getLocalizedMessage());
            return false;
        }

        // get todays calendar instance and set to start of day
        Calendar now = Calendar.getInstance();
        now.set(Calendar.HOUR_OF_DAY, 0);
        now.set(Calendar.MINUTE, 0);
        now.set(Calendar.SECOND, 0);
        now.set(Calendar.MILLISECOND, 0);

        Calendar calDate = Calendar.getInstance();
        calDate.setTime(theDate);

        if (theDate != null && calDate.before(now)) {
            validationErrors.add(new Message(Message.Type.Error, MessageKeys.ERR_DATE_PAST).getLocalizedMessage());
            return false;
        }

        return true;
    }

    /**
     * Make sure startDate is before endDate
     *
     * @param startDate
     * @param endDate
     * @return true if startDate is before endDate
     */
    private boolean validateStartDateBeforeEndDate(Date startDate, Date endDate) {
        if (startDate != null && endDate != null && !startDate.before(endDate)) {
            validationErrors.add(new Message(Message.Type.Error, MessageKeys.ERR_DATE_ORDER).getLocalizedMessage());
            return false;
        }

        return true;
    }

    public Map<String,Object> getUserPreferences() {
        Map<String, Object> userPreferences = new HashMap<>();
        userPreferences.put(DISABLE_NOTIFICATIONS, Util.getBoolean(this.user.getPreferences(), DISABLE_NOTIFICATIONS));
        return userPreferences;
    }

    public void setUserPreferences(Map<String,Object> data) throws GeneralException {
        this.user.setPreference(DISABLE_NOTIFICATIONS, Util.getBoolean(data, DISABLE_NOTIFICATIONS));
        this.saveUser();
    }

}
