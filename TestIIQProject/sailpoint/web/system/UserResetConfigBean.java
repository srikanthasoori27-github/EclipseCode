/* (c) Copyright 2014 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.system;

import sailpoint.object.Application;
import sailpoint.object.Application.Feature;
import sailpoint.object.Attributes;
import sailpoint.object.AuditEvent;
import sailpoint.object.Configuration;
import sailpoint.service.LinkService;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.BaseBean;
import sailpoint.web.LoginConfigBean;
import sailpoint.web.messages.MessageKeys;

import javax.faces.context.FacesContext;
import java.util.List;
import java.util.Map;

/**
 * This backing bean consolidates the logic between the two forms of password reset, either authentication
 * questions or SMS.  In either of these cases, certain SSO and Pass thru App configurations must be made 
 * and apply regardless of the password reset method used.
 * @author chris.annino
 *
 */
public class UserResetConfigBean extends BaseBean {
    
    protected boolean enableForgotPassword;
    protected boolean enableAccountUnlock;

    
    /**
     * Default constructor.
     * @throws GeneralException 
     */
    public UserResetConfigBean() throws GeneralException {
        super();
        
        this.initialize();
    }
    
    protected void initialize() throws GeneralException {
        
        //Get the configuration
        Configuration config = getContext().getConfiguration();
        this.enableForgotPassword = 
                config.getBoolean(Configuration.ENABLE_FORGOT_PASSWORD);
        this.enableAccountUnlock =
                config.getBoolean(Configuration.ENABLE_ACCOUNT_UNLOCK);
    }
    
    

    public boolean isEnableForgotPassword() {
        return enableForgotPassword;
    }

    public void setEnableForgotPassword(boolean enableForgotPassword) {
        this.enableForgotPassword = enableForgotPassword;
    }

    public boolean isEnableAccountUnlock() {
        return enableAccountUnlock;
    }

    public void setEnableAccountUnlock(boolean accountUnlockEnabled) {
        this.enableAccountUnlock = accountUnlockEnabled;
    }

    public boolean validate(Configuration config, LoginConfigBean loginConfig) throws GeneralException {
        // if this method adds Error checking (instead of the current Warn level), ensure you set valid = false
        boolean valid = true;
        
        // Warning #1: No integrations setup for password
        if (!isPasswordManagementEnabled() && isEnableForgotPassword()) {
            loginConfig.addWarning(MessageKeys.WARN_AUTH_QUESTIONS_NO_PASSWORD_INTEGRATIONS);
        }
        
        // Warning #2: No integrations setup for unlock
        if(isEnableAccountUnlock() && !isAccountUnlockSupported()) {
            loginConfig.addWarning(MessageKeys.USER_RESET_NO_UNLOCK_INTEGRATIONS);
        }

        // Warning #3: No pass-through applications configured
        if (!anyPassThruApps() && (isEnableForgotPassword() || isEnableAccountUnlock())) {
            loginConfig.addWarning(MessageKeys.WARN_AUTH_QUESTIONS_NO_PASS_THRU_APPS);
        }
        
        // Warning #4: PasswordReset and/or AccountUnlock enabled but No AuthQuestions OR SMS Reset configured
        if ((isEnableForgotPassword() || isEnableAccountUnlock()) 
                && (!getAuthQuestionConfigBean().isAuthQuestionsEnabled() && !getSMSResetConfigBean().isSmsResetEnabled())) {
            loginConfig.addError(MessageKeys.LOGIN_NO_USER_RESET_CONFIG);
            valid = false;
        }
        
        return valid && getAuthQuestionConfigBean().validate(config, loginConfig) && getSMSResetConfigBean().validate(config, loginConfig);
    }
    
    public boolean save(Configuration config, LoginConfigBean loginConfig) throws GeneralException {
        if (!validate(config, loginConfig)) {
            return false;
        }
        
        AuthQuestionConfigBean authBean = getAuthQuestionConfigBean();
        SMSResetConfigBean smsReset = getSMSResetConfigBean();

        // If this doesn't pass validation, don't commit just return an error.
        if (!authBean.save(config, loginConfig) || !smsReset.save(config, loginConfig)) {
            return false;
        }
        
        config.put(Configuration.ENABLE_FORGOT_PASSWORD, 
                this.enableForgotPassword);
        config.put(Configuration.ENABLE_ACCOUNT_UNLOCK, 
                this.enableAccountUnlock);

        auditUserResetConfigChanges(config, loginConfig);
        
        return true;
    }

    private static String[][] USER_RESET_CONFIG_ATTRIBUTE_NAMES = {
            {Configuration.ENABLE_FORGOT_PASSWORD, MessageKeys.USER_RESET_ENABLE_FORGOT_PASSWORD},
            {Configuration.ENABLE_ACCOUNT_UNLOCK, MessageKeys.USER_RESET_ENABLE_ACCOUNT_UNLOCK}
    };

    //TODO: Will this ever be independent of LoginConfigBean? If so, need to re-factor
    public void auditUserResetConfigChanges(Configuration config, LoginConfigBean loginConfig) {

        if (loginConfig != null) {
            AuditEvent evt = loginConfig.getAuditEvent();
            if (evt != null) {
                //Should be non-null if auditing is enabled
                Map<String, Object> origConfig = loginConfig.getOriginalConfig();
                if (origConfig != null) {
                    Attributes origAtts = (Attributes)origConfig.get(Configuration.OBJ_NAME);
                    if (origAtts != null) {
                        for (int i=0; i<USER_RESET_CONFIG_ATTRIBUTE_NAMES.length; i++) {
                            Object origValue = origAtts.get(USER_RESET_CONFIG_ATTRIBUTE_NAMES[i][0]);
                            Object neuVal = config != null ? config.get(USER_RESET_CONFIG_ATTRIBUTE_NAMES[i][0]) : null;

                            if (!Util.nullSafeEq(origValue, neuVal, true, true)) {
                                evt.setAttribute(USER_RESET_CONFIG_ATTRIBUTE_NAMES[i][1],
                                        new Message(Message.Type.Info, MessageKeys.AUDIT_ACTION_LOGIN_CONFIG_VALUE_UPDATE,
                                                origValue, neuVal));
                            }
                        }
                    }

                }
            }
        }
    }
    
    private LoginConfigBean getLoginConfigBean() {
        FacesContext fc = getFacesContext();
        return fc.getApplication().evaluateExpressionGet(fc, "#{loginConfig}", LoginConfigBean.class);
    }

    private AuthQuestionConfigBean getAuthQuestionConfigBean() {
        FacesContext fc = getFacesContext();
        return fc.getApplication().evaluateExpressionGet(fc, "#{authQuestionConfig}", AuthQuestionConfigBean.class);
    }
    
    private SMSResetConfigBean getSMSResetConfigBean() {
        FacesContext fc = getFacesContext();
        return fc.getApplication().evaluateExpressionGet(fc, "#{smsResetConfig}", SMSResetConfigBean.class);
    }

    private boolean isSsoEnabled() throws GeneralException {
        // Ask the LoginConfigBean since this could have been changed but not yet saved.
        return (null != Util.getString(getLoginConfigBean().getSsoRule()));
    }
    
    private boolean isPasswordManagementEnabled() throws GeneralException {
        
        if (anyPasswordIntegrations()) {
            return true;
        }
        return isPasswordManagementSupportedByAnyApplication();
    }

    private boolean anyPasswordIntegrations() throws GeneralException {
        LinkService service = new LinkService(getContext());
        return service.anyPasswordIntegrations();
    }
    
    private boolean anyPassThruApps() {
        // Ask the LoginConfigBean since this could have been changed but not yet saved.
        List<?> apps = getLoginConfigBean().getPassThroughApplications();
        return ((null != apps) && !apps.isEmpty());
    }
    
    private boolean isPasswordManagementSupportedByAnyApplication() {
        
        boolean supported = false;

        List<Application> applications = getLoginConfigBean().getPassThroughApplications();
        if (applications != null) {
            for (Application app : applications) {
                if (app.getFeatures() != null && app.getFeatures().contains(Feature.PASSWORD)) {
                    supported = true;
                    break;
                }
            }
        }
        
        return supported;
    }
    
    private boolean isAccountUnlockSupported() throws GeneralException {
        
        if(anyUnlockIntegrations()) {
            return true;
        }
        
        return isAccountUnlockSupportedByAnyApplication();
    }
    
    private boolean anyUnlockIntegrations() throws GeneralException {

        LinkService service = new LinkService(getContext());
        return service.anyUnlockIntegrations();
    }
    
    /**
     * Checks to see if any passthrough apps support Unlock
     * @return
     */
    private boolean isAccountUnlockSupportedByAnyApplication() {
        
        boolean supported = false;
        List<Application> apps = getLoginConfigBean().getPassThroughApplications();
        if(apps != null) {
            for(Application app : apps) {
                if(app.supportsFeature(Feature.UNLOCK)) {
                    supported = true;
                    break;
                }
            }
            
        }
        
        return supported;
    }

}
