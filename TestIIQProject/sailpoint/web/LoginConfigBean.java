/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import sailpoint.api.ObjectUtil;
import sailpoint.api.SailPointContext;
import sailpoint.integration.JsonUtil;
import sailpoint.object.Application;
import sailpoint.object.Attributes;
import sailpoint.object.AuditEvent;
import sailpoint.object.Configuration;
import sailpoint.object.Filter;
import sailpoint.object.QueryOptions;
import sailpoint.object.Reference;
import sailpoint.object.Rule;
import sailpoint.object.RuleRegistry;
import sailpoint.object.Source;
import sailpoint.server.Auditor;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Message.Type;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.system.MFAConfigBean;
import sailpoint.web.system.SAMLConfigBean;
import sailpoint.web.system.UserResetConfigBean;
import sailpoint.web.util.WebUtil;

import javax.faces.context.FacesContext;
import javax.faces.model.SelectItem;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * JSF UI bean used to view and modify login configuration settings.
 *
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 * sso stuff added by Dan Smith
 */
public class LoginConfigBean extends BaseBean
{
    private static final Log log = LogFactory.getLog(LoginConfigBean.class);

    private List<Application> _passThroughApps;
    private String autoCreateUserRuleName;
    private String loginErrorStyle;

    /**
     * Currently selected single sign on rule.
     */
    private String _ssoRule;
    private String _ssoValidationRule;

    /**
     * Setting for where to redirect when logging in after a session timeout
     */
    private boolean _loginReturnsToDashboard;
    
    private boolean _enableAuthLockout;
    
    private int _loginAttempts;
    
    private long _failedLoginLockDurationMillis;
    
    private boolean _enableProtectedUserLockout;

    private int _activeTab = 0;
    
    /**
     * Flag to indicate to the Authenticator if the Identity's 
     * pass-through link should be refreshed after each 
     * aggregation.
     * 
     * Prior to 6.2 we always refreshed the identity's link.
     * 
     * @ignore changed in BUG#17472
     */
    private boolean _refreshIdentityAfterPassthrough;
    
    private List<Message> messages;

    /**
     * Used to store the previous config, to help with Auditing any changes made
     */
    Map<String, Object> _originalConfig;

    //AuditEvent created to audit any changes
    AuditEvent _auditEvent;

    //list of password policy attributes for auditing any change
    //based on all fields in passwords.xhtml
    public static String[][] LOGIN_CONFIG_ATTRIBUTE_NAMES = {
            {Configuration.LOGIN_ERROR_STYLE, MessageKeys.LOGIN_ERR_STYLE},
            {Configuration.LOGIN_SSO_RULE, MessageKeys.SSO_RULE},
            {Configuration.LOGIN_SSO_VALIDATION_RULE, MessageKeys.SSO_VALIDATION_RULE},
            {Configuration.LOGIN_RETURNS_TO_DASHBOARD, MessageKeys.LOGIN_RETURNS_TO_DASHBOARD},
            {Configuration.ENABLE_AUTH_LOCKOUT, MessageKeys.ENABLE_AUTH_LOCKOUT},
            {Configuration.FAILED_LOGIN_ATTEMPTS, MessageKeys.LOGIN_CONF_AUTH_LOCKOUT_ATTEMPTS},
            {Configuration.LOGIN_LOCKOUT_DURATION, MessageKeys.LOGIN_CONF_AUTH_LOCKOUT_DURATION},
            {Configuration.PROTECTED_USER_LOCKOUT, MessageKeys.ENABLE_PROTECTED_USER_LOCKOUT},
            {Configuration.REFRESH_PASSTHROUGH_LINK_DURING_AUTHENTICATION, MessageKeys.REFRESH_AFTER_LOGIN},
            {Configuration.LOGIN_PASS_THROUGH, MessageKeys.LOGIN_CONF_PASS_THRU_APP}
    };

    protected static String AUTO_CREATE_USER_RULE = "autoCreateUserRuleName";

    ////////////////////////////////////////////////////////////////////////////
    //
    // CONSTRUCTORS
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Default constructor.
     */
    public LoginConfigBean() throws GeneralException
    {
        super();

        // Lookup the rule to run (if configured) from the rule registry.
        RuleRegistry reg = RuleRegistry.getInstance(getContext());
        Rule rule = reg.getRule(RuleRegistry.Callout.AUTO_CREATE_USER_AUTHENTICATION);
        if (null != rule)
            this.autoCreateUserRuleName = rule.getName();

        Configuration config = getContext().getConfiguration();

        if ( config != null ) {
            this.loginErrorStyle = config.getString(Configuration.LOGIN_ERROR_STYLE);
            _ssoRule = config.getString(Configuration.LOGIN_SSO_RULE);
            _ssoValidationRule = config.getString(Configuration.LOGIN_SSO_VALIDATION_RULE);
            _loginReturnsToDashboard = config.getBoolean(Configuration.LOGIN_RETURNS_TO_DASHBOARD);
            _enableAuthLockout = config.getBoolean(Configuration.ENABLE_AUTH_LOCKOUT, false);
            _loginAttempts = config.getInt(Configuration.FAILED_LOGIN_ATTEMPTS);
            _failedLoginLockDurationMillis = config.getLong(Configuration.LOGIN_LOCKOUT_DURATION);
            _enableProtectedUserLockout = config.getBoolean(Configuration.PROTECTED_USER_LOCKOUT);
            _refreshIdentityAfterPassthrough = config.getBoolean(Configuration.REFRESH_PASSTHROUGH_LINK_DURING_AUTHENTICATION);
        }


        _originalConfig = new HashMap<String, Object>();
        //Add System Config
        _originalConfig.put(Configuration.OBJ_NAME, new Attributes(config.getAttributes()));
        //Add Rule Registry Entry
        _originalConfig.put(AUTO_CREATE_USER_RULE, this.autoCreateUserRuleName);
    }

    ////////////////////////////////////////////////////////////////////////////
    //
    // GETTERS & SETTERS
    //
    ////////////////////////////////////////////////////////////////////////////

    public String getAutoCreateUserRuleName() throws GeneralException
    {
        return this.autoCreateUserRuleName;
    }

    public void setAutoCreateUserRuleName(String name)
    {
        this.autoCreateUserRuleName = name;
    }

    public String getLoginErrorStyle() {
        return this.loginErrorStyle;
    }

    public void setLoginErrorStyle(String value) {
        this.loginErrorStyle = value;
    }

    public String getDetailLoginStyle() {
        return Configuration.DETAILED_LOGIN_STYLE;
    }

    public String getSimpleLoginStyle() {
        return Configuration.SIMPLE_LOGIN_STYLE;
    }

    /**
     * Return the name of the currently selected sso rule.
     */
    public String getSsoRule() throws GeneralException {
        return _ssoRule;
    }

    /**
     * Set the sso rule by name.
     */
    public void setSsoRule(String name) throws GeneralException {
        _ssoRule = name;
    }
    
    /**
     * Return the name of the currently selected sso validation rule.
     */
    public String getSsoValidationRule() throws GeneralException {
        return _ssoValidationRule;
    }
    
    /**
     * Set the sso validation rule by name.
     */
    public void setSsoValidationRule(String name) throws GeneralException {
        _ssoValidationRule = name;
    }

    public int getActiveTab() {
        return _activeTab;
    }

    public void setActiveTab(int tab) {
        _activeTab = tab;
    }

    /**
     * Return the names of all auto create user rules, suitable for use
     * in a selectOneMenu.
     */
    public List<SelectItem> getAutoCreateUserRules() throws GeneralException {
        return WebUtil.getRulesByType(getContext(), Rule.Type.IdentityCreation, true);
    }

    /**
     * Return the names of all sso rules, suitable for use
     * in a selectOneMenu.
     */
    public List<SelectItem> getSSORules() throws GeneralException {
        return WebUtil.getRulesByType(getContext(), Rule.Type.SSOAuthentication, true);
    }
    
    /**
     * Return the names of all sso validation rules, suitable for use
     * in a selectOneMenu.
     */
    public List<SelectItem> getSSOValidationRules() throws GeneralException {
        return WebUtil.getRulesByType(getContext(), Rule.Type.SSOValidation, true);
    }

    /**
     * @return A list of IDs for the pass through applications.
     * Though we maintain this as a list of names in the Configuration,
     * it has to be exposed as ids for the suggest component.
     */
    public List<Application> getPassThroughApplications() {
        if (_passThroughApps == null) {
            _passThroughApps = new ArrayList<Application>();
            try {
                Configuration config = getContext().getConfiguration();
                Object value = config.get(Configuration.LOGIN_PASS_THROUGH);
                _passThroughApps = 
                	ObjectUtil.getObjects(getContext(), Application.class, value);
            }
            catch (GeneralException e) {
                log.error(e);
            }
        }
        return _passThroughApps;
    }
    
    public void setPassThroughApplications(List<Application> apps) {
        _passThroughApps = apps;
    }

    /**
     * Return the setting for logins returning to the dashboard
     *
     * @return true if logins return to the dashboard, false if they return
     *         to the previously viewed page
     */
    public boolean isLoginReturnsToDashboard() {
        return _loginReturnsToDashboard;
    }

    /**
     * Sets the setting for logins returning to the dashboard
     *
     * @param loginReturnsToDashboard true if logins return to the dashboard,
     *        false if they return to the previously viewed page
     */
    public void setLoginReturnsToDashboard(boolean loginReturnsToDashboard) {
        _loginReturnsToDashboard = loginReturnsToDashboard;
    }

    ////////////////////////////////////////////////////////////////////////////
    //
    // ACTION METHODS
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Helper for saveAction, convert the list of application ids to a 
     * List<Reference> with names we can read when looking at the XML.
     */
    private List<Reference> convertApplicationIds() throws GeneralException {
        List<Reference> refs = null;
        if (_passThroughApps != null && _passThroughApps.size() > 0) {
            refs = new ArrayList<Reference>();
            for (Application app : _passThroughApps) {
                if (app != null)
                    refs.add(new Reference(app));
            }
        }
        return refs;
    }
    
    /**
     * JSF action to run validation.  This will recalculated the messages field.
     */
    public String validate() throws GeneralException {
        
        // Reset the validation state.
        this.messages = null;
        
        // Only validate if LCM is enabled - otherwise these settings aren't
        // shown in the UI.
        if (isLcmEnabled()) {
            getPasswordResetConfigBean().validate(getContext().getConfiguration(), this);
        }

        getSAMLConfigBean().validate(this);
        getMFAConfigBean().validate(this);

        return null;
    }

    public String saveAction() throws GeneralException
    {

        SailPointContext context = getContext();

        // Update the System Configuration
        // Be sure to fetch it and not use the cached version
        // that SailPointContext.getConfiguration returns.
        Configuration config =
            context.getObjectByName(Configuration.class, Configuration.OBJ_NAME);

        List<Reference> authApps = convertApplicationIds();
        config.put(Configuration.LOGIN_PASS_THROUGH, authApps);
        config.put(Configuration.LOGIN_ERROR_STYLE, this.loginErrorStyle);
        config.put(Configuration.LOGIN_SSO_RULE, _ssoRule);
        config.put(Configuration.LOGIN_SSO_VALIDATION_RULE, _ssoValidationRule);
        config.put(Configuration.LOGIN_RETURNS_TO_DASHBOARD, _loginReturnsToDashboard);
        config.put(Configuration.ENABLE_AUTH_LOCKOUT, _enableAuthLockout);
        config.put(Configuration.FAILED_LOGIN_ATTEMPTS, _loginAttempts);
        config.put(Configuration.LOGIN_LOCKOUT_DURATION, _failedLoginLockDurationMillis);
        config.put(Configuration.PROTECTED_USER_LOCKOUT, _enableProtectedUserLockout);
        config.put(Configuration.REFRESH_PASSTHROUGH_LINK_DURING_AUTHENTICATION, _refreshIdentityAfterPassthrough);
        
        // Clear any existing authn resources, 
        // this is the old convention that we'll clean up dynamically.
        QueryOptions qo = new QueryOptions();
        qo.add(Filter.eq("authenticationResource", true));
        List<Application> apps = context.getObjects(Application.class, qo);
        if ((null != apps) && !apps.isEmpty()) {
            for (Application app : apps) {
                app.setAuthenticationResource(false);
                context.saveObject(app);
            }
        }

        // Set the auto create rule.
        Rule autoCreateRule = null;
        if ((null != this.autoCreateUserRuleName) &&
            (this.autoCreateUserRuleName.length() > 0))
        {
            autoCreateRule =
                context.getObjectByName(Rule.class, this.autoCreateUserRuleName);
        }
        RuleRegistry reg = RuleRegistry.getInstance(context);
        reg.setRule(RuleRegistry.Callout.AUTO_CREATE_USER_AUTHENTICATION, autoCreateRule);

        //Audit changes
        auditLoginConfigChanges(config.getAttributes());

        // Save the PasswordResetConfigBean if LCM is enabled.
        if (super.isLcmEnabled()) {
            getPasswordResetConfigBean().save(config, this);
        }

        // Save the SAMLConfig settings
        getSAMLConfigBean().save(this);
        getMFAConfigBean().save(this);


        //Add to session here, because SAML/MFA both commit
        if (getAuditEvent() != null && !Util.isEmpty(getAuditEvent().getAttributes())) {
            //If no attributes, no need to audit
            context.saveObject(getAuditEvent());
        }

        
        context.saveObject(config);
        context.commitTransaction();

        return "success";
    }

    private void auditLoginConfigChanges(Attributes neuConfig) throws GeneralException {

        if (Auditor.isEnabled(AuditEvent.LoginConfigChange)) {

            _auditEvent = new AuditEvent();
            _auditEvent.setAction(AuditEvent.LoginConfigChange);
            _auditEvent.setSource(getLoggedInUserName());
            _auditEvent.setInterface(Source.UI.toString());
            _auditEvent.setAttributeValue("op");
            _auditEvent.setAttributeValue(AuditEvent.ActionUpdate);
            Attributes origAtts = (Attributes)_originalConfig.get(Configuration.OBJ_NAME);
            if (origAtts != null) {
                for (int i=0; i<LOGIN_CONFIG_ATTRIBUTE_NAMES.length; i++) {
                    Object origValue = origAtts.get(LOGIN_CONFIG_ATTRIBUTE_NAMES[i][0]);
                    Object neuVal = neuConfig.get(LOGIN_CONFIG_ATTRIBUTE_NAMES[i][0]);

                    if (!Util.nullSafeEq(origValue, neuVal, true, true)) {
                        _auditEvent.setAttribute(LOGIN_CONFIG_ATTRIBUTE_NAMES[i][1],
                                new Message(Message.Type.Info, MessageKeys.AUDIT_ACTION_LOGIN_CONFIG_VALUE_UPDATE,
                                        origValue, neuVal));
                    }
                }
            }

            if (!Util.nullSafeEq(_originalConfig.get(AUTO_CREATE_USER_RULE), this.autoCreateUserRuleName, true, true)) {
                _auditEvent.setAttribute(AUTO_CREATE_USER_RULE,
                        new Message(Message.Type.Info, MessageKeys.AUDIT_ACTION_LOGIN_CONFIG_VALUE_UPDATE,
                                _originalConfig.get(AUTO_CREATE_USER_RULE), this.autoCreateUserRuleName));
            }


        }

    }


    public AuditEvent getAuditEvent() { return _auditEvent; }

    public Map getOriginalConfig() { return _originalConfig; }

    private UserResetConfigBean getPasswordResetConfigBean() {
        FacesContext fc = getFacesContext();
        return fc.getApplication().evaluateExpressionGet(fc, "#{userResetConfig}", UserResetConfigBean.class);
    }

    private SAMLConfigBean getSAMLConfigBean() {
        FacesContext fc = getFacesContext();
        return fc.getApplication().evaluateExpressionGet(fc, "#{samlConfig}", SAMLConfigBean.class);
    }
    
    private MFAConfigBean getMFAConfigBean() {
        FacesContext fc = getFacesContext();
        return fc.getApplication().evaluateExpressionGet(fc, "#{mfaConfig}", MFAConfigBean.class);
    }
    
    /**
     * Return a JSON-encoded map keyed by message type with a list of localized
     * messages. 
     */
    public String getMessagesJson() throws Exception {
        Map<String,List<String>> map = new HashMap<String,List<String>>();
        map.put("warnings", collectMessages(Type.Warn));
        map.put("errors", collectMessages(Type.Error));
        return JsonUtil.render(map);
    }

    // Need a setter for A4J to work right - this is a no-op.
    public void setMessagesJson(String s) {}
    
    /**
     * Override addMessage() to keep a list of messages on the bean so that we
     * can return the JSON and deal with this client-side.  Note that warnings
     * are not added as standard JSF messages since we're going to display them
     * in a popup to allow saving despite warnings.
     */
    @Override
    public void addMessage(Message summary) {
        
        // Warnings will be handled in a popup.
        if (!Type.Warn.equals(summary.getType())) {
            super.addMessage(summary);
        }

        if (null == this.messages) {
            this.messages = new ArrayList<Message>();
        }
        this.messages.add(summary);
    }

    /**
     * Convenience method to add a warning with a given message key.
     * @param msgKey
     */
    public void addWarning(String msgKey) {
        this.addMessage(new Message(Type.Warn, msgKey));
    }
    
    /**
     * Convenience method to add an error with a given message key.
     * @param msgKey
     */
    public void addError(String msgKey) {
        this.addMessage(new Message(Type.Error, msgKey));
    }

    private List<String> collectMessages(Type msgType) {
        List<String> msgs = new ArrayList<String>();
        if (null != this.messages) {
            for (Message msg : this.messages) {
                if (msgType.equals(msg.getType())) {
                    msgs.add(msg.getLocalizedMessage(getLocale(), getUserTimeZone()));
                }
            }
        }
        return msgs;
    }

    public boolean isEnableAuthLockout() {
        return _enableAuthLockout;
    }

    public void setEnableAuthLockout(boolean _enableAuthLockout) {
        this._enableAuthLockout = _enableAuthLockout;
    }

    public int getLoginAttempts() {
        return _loginAttempts;
    }

    public void setLoginAttempts(int _loginAttempts) {
        this._loginAttempts = _loginAttempts;
    }

    public long getFailedLoginLockDurationMillis() {
        return _failedLoginLockDurationMillis;
    }

    public void setFailedLoginLockDurationMillis(
            long millis) {
        this._failedLoginLockDurationMillis = millis;
    }
    
    public long getFailedLoginLockDurationMinutes() {
        return _failedLoginLockDurationMillis / Util.MILLI_IN_MINUTE;
    }

    public void setFailedLoginLockDurationMinutes(
            long minutes) {
        this._failedLoginLockDurationMillis = minutes * Util.MILLI_IN_MINUTE;
    }

    public boolean isEnableProtectedUserLockout() {
        return _enableProtectedUserLockout;
    }

    public void setEnableProtectedUserLockout(boolean _enableProtectedUserLockout) {
        this._enableProtectedUserLockout = _enableProtectedUserLockout;
    }

    public boolean isRefreshIdentityAfterPassthrough() {
        return _refreshIdentityAfterPassthrough;
    }

    public void setRefreshIdentityAfterPassthrough(
            boolean refreshIdentityAfterPassthrough) {
        this._refreshIdentityAfterPassthrough = refreshIdentityAfterPassthrough;
    }

}
