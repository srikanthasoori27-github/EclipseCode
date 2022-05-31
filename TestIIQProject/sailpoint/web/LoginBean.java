/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Map;

import javax.faces.application.FacesMessage;
import javax.faces.component.UIComponent;
import javax.faces.context.ExternalContext;
import javax.faces.el.ValueBinding;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.AuthenticationFailureException;
import sailpoint.api.Challenger;
import sailpoint.api.Lockinator;
import sailpoint.api.ObjectUtil;
import sailpoint.api.PasswordPolice;
import sailpoint.api.PasswordPolicyException;
import sailpoint.api.SailPointContext;
import sailpoint.connector.ConnectorException;
import sailpoint.connector.ExpiredPasswordException;
import sailpoint.object.Application;
import sailpoint.object.AuditEvent;
import sailpoint.object.BulkIdJoin;
import sailpoint.object.Capability;
import sailpoint.object.Configuration;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.Identity.CapabilityManager;
import sailpoint.object.Link;
import sailpoint.object.PasswordPolicy;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.QueryOptions;
import sailpoint.object.SMSResetConfig;
import sailpoint.object.SPRight;
import sailpoint.object.WorkflowLaunch;
import sailpoint.server.Auditor;
import sailpoint.server.Authenticator;
import sailpoint.service.IdentityFinder;
import sailpoint.service.IdentityResetService;
import sailpoint.service.IdentityResetService.Consts.SessionAttributes;
import sailpoint.service.LoginService;
import sailpoint.service.MapSessionStorage;
import sailpoint.service.PageAuthenticationService;
import sailpoint.service.PasswordReseter.PasswordResetHelper;
import sailpoint.service.Redirectable;
import sailpoint.tools.AbstractLocalizableException;
import sailpoint.tools.BrandingServiceFactory;
import sailpoint.tools.GeneralException;
import sailpoint.tools.IdentityLockedException;
import sailpoint.tools.LazyLoad;
import sailpoint.tools.LazyLoad.ILazyLoader;
import sailpoint.tools.Message;
import sailpoint.tools.Pair;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.util.WebUtil;


public class LoginBean extends BaseBean
{

    private static final Log log = LogFactory.getLog(LoginBean.class);

    public static final String ID_AUTH_ATTR_MGR_STATUS = SessionAttributes.ID_AUTH_ATTR_MGR_STATUS.value();
    public static final String ID_AUTH_ATTR_VIOLATION_OWNER = SessionAttributes.ID_AUTH_ATTR_VIOLATION_OWNER.value();

	public static final String SESSION_EXPIRATION = "sessionExpiration";

    /**
     * Used to convey the Identity name from the login page
     * to the expired password page.
     */
    public static final String ATT_EXPIRED_ACCOUNT_ID = "loginExpiredAccountId";


    /**
     * Used for the User Reset functionality
     */
    //Account Id entered in the username field
    public static final String ATT_RESET_ACCOUNT_ID = SessionAttributes.SESSION_ACCOUNT_ID.value();
    //Identity name of the identity correlated to the accountId
    public static final String ATT_RESET_IDENT_ID = SessionAttributes.SESSION_IDENT_NAME.value();

    /**
     * Enum representing the type of user Reset being performed.
     * TODO: Should these live in the Service Layer?
     *
     */
    public enum ResetType {
        PASSWORD_RESET("passwordReset"),
        ACCOUNT_UNLOCK("accountUnlock");
        
        private final String text;
        
        private ResetType(final String text) {
            this.text = text;
        }
        
        public String getText() {
            return text;
        }
    }
    
    /**
     * Enum representing the origin of a userReset workflow
     * TODO: Should these live in the service layer?
     *
     */
    public enum ResetOrigin {
        DESKTOP_RESET("desktopReset"),
        DESKTOP_UI("desktopUIReset"),
        MOBILE_UI("mobileUIReset");
        
        private final String text;
        
        private ResetOrigin(final String text) {
            this.text = text;
        }
        
        public String getText() {
            return this.text;
        }
    
    }
    
    /**
     * Used to store of the user name used during the login into IdentityIQ.
     * We use this when authenticating during e-signature authentication. 
     */
    public static final String ATT_ORIGINAL_ACCOUNT_ID = "originalAccountId";
    
    /**
     * Used to store off the nativeIdentitifer from the Link we used to
     * authenticate when the comes from SSO.  This is typically used 
     * with ATT_ORIGINAL_ACCOUNT_ID which will contain the 
     * display name of the account.  
     * 
     * In cases of SSO the native identity may be something like
     * a DN.  We don't want to show DNs in the UI tier so we
     * keep this around in the session for the actual call to
     * authenticate, while displaying the ATT_ORIGINAL_ACCOUNT_ID.
     */
    public static final String ATT_ORIGINAL_NATIVE_ID = "originalNativeId";
        
    public static final String ATT_USERNAME = "username";

    public static final String ATT_EXPIRED_APP_NAME = "expiredAppName";
    
    public static final String ATT_DESKTOP_RESET = "desktopReset";
    
    

    // When we transition to the password reset page, we'll check the session
    // to make sure the previous page (auth questions or expired pass) was successful
    public static final String ATT_PASSWORD_RESET_AUTHORIZED = "passwordResetAuthorized";
    public static final String ATT_PASSWORD_RESET_FORGOT = "forgot";
    public static final String ATT_PASSWORD_RESET_EXPIRED = "expired";

    /** Key for the session variable that stores the typed in or SSO auth string. */
    public static final String ORIGINAL_AUTH_ID = "authenticationID";
    public static final String ORIGINAL_AUTH_APPLICATION = "authenticationApp";
    
    /** Key for the session variable that stores a list of dynamic scope names applicable for the identity */
    public static final String ATT_DYNAMIC_SCOPES = SessionAttributes.ATT_DYNAMIC_SCOPES.value();
    
    
    /** URL for the userReset template page*/
    public static final String USER_RESET_TEMPLATE_URL = "/ui/external/reset.jsf#";
    public static final String ENCODING = "UTF-8";

    /* ****************************************************** */
    /* Fields                                                 */
    /* ****************************************************** */

    private String accountId;
    private String password;
    private String username;
    private String originalAccountId;

    // We use binding on the mobile login page
    private UIComponent accountIdInput;
    private UIComponent passwordInput;

    private boolean loginError = false;

    private String preLoginUrl;
    private String newPassword;
    private String confirmPassword;
    
    private List<String> passwordRequirements;

    /** 
     * Time in milliseconds that the server will allow a session to remain idle
     */
    private transient long _sessionTimeout = 0;
    
    /** 
     * Time in milliseconds until the current session should expire
     */
    private transient long _expiry = 0;
    
    /* redirector to abstract external context from Service classes */
    class ExternalContextRedirector implements Redirectable {
        @Override
        public void redirect(String sessionKey, String redirectUrl, List<String> paramsToFilter, String preRedirectUrl)
            throws IOException {
            ExternalContext ec = getFacesContext().getExternalContext();
            ec.redirect(getRequestContextPath() + redirectUrl);
        }
        
    }
    
    boolean requireCurrentPassword;
    
    String expiredPasswordAppName;
    
    Lockinator padLock;

    Boolean registrationEnabled;
    
    /**
     * Passed in from login.xhtml based on the link the user clicks.
     * Currently, we support ForgotPassword and AccountUnlock
     */
    String _userResetType;

    /**
     * System options for account unlock and forgot password
     */
    private boolean forgotPasswordConfigOptionEnabled;
    
    private Boolean accountUnlockConfigOptionEnabled;
    
    /**
     * This is lazy loaded because it is expensive.
     * THis will take into account the system configuration option
     * as well as whether passthrough apps are enabled.
     */
    private LazyLoad<Boolean> forgotPasswordEnabled;
    private LazyLoad<Boolean> accountUnlockEnabled;
    
    private LoginService loginService;

    /* ****************************************************** */
    /* Constructors                                           */
    /* ****************************************************** */

    public LoginBean() throws GeneralException {
        super();

        this.preLoginUrl = retrievePreLoginUrl();
        this.accountId = (String) getSessionScope().get(ATT_EXPIRED_ACCOUNT_ID);
        this.username =
            super.getRequestOrSessionParameter(ATT_USERNAME);
    	this.padLock = new Lockinator(getContext());
        Configuration systemConfig = getContext().getConfiguration();
        if ( systemConfig != null ) {
        	this.requireCurrentPassword = systemConfig.getBoolean(Configuration.REQUIRE_OLD_PASSWORD_AT_CHANGE);

            Map session = getSessionScope();
            this.expiredPasswordAppName = (String)session.get(ATT_EXPIRED_APP_NAME);
            checkAuthAppName(this.requireCurrentPassword, this.expiredPasswordAppName);

            this.forgotPasswordConfigOptionEnabled = systemConfig.getBoolean(Configuration.ENABLE_FORGOT_PASSWORD, false);
            this.accountUnlockConfigOptionEnabled = systemConfig.getBoolean(Configuration.ENABLE_ACCOUNT_UNLOCK, false);
        }
        // this will be loaded lazily when called and
        // once called the value won't be loaded again.
        forgotPasswordEnabled = new LazyLoad<Boolean>(new ILazyLoader<Boolean>() {
            @Override
            public Boolean load() throws GeneralException {
                return loadForgotPasswordEnabled();
            }
        });
        accountUnlockEnabled = new LazyLoad<Boolean>(new ILazyLoader<Boolean>() {
            @Override
            public Boolean load() throws GeneralException {
                return loadAccountUnlockEnabled();
            }
        });

        loginService = new LoginService(getContext());
        loginService.setRedirector(new ExternalContextRedirector());

    }  // LoginBean()
    
    /* used in unit tests only to override the Redirectable */
    void setLoginService(LoginService login) {
        this.loginService = login;
    }

    protected void checkAuthAppName(boolean requireCurrentPassword, String expiredPasswordAppName) throws GeneralException {
        if (requireCurrentPassword && expiredPasswordAppName != null &&
                !expiredPasswordAppName.equals(Authenticator.IIQ)) {
            // check to see if the app actually supports the current password feature
            Application expiredapp = getContext().getObjectByName(Application.class, expiredPasswordAppName);
            this.requireCurrentPassword = expiredapp.supportsFeature(Application.Feature.CURRENT_PASSWORD);
        }
    }

    /**
     * Retrieve the preLoginUrl from the request or session for the LoginBean.
     */
    public String retrievePreLoginUrl() throws GeneralException {
        return retrievePreLoginUrl(this, true);
    }

    /**
     * Retrieve the preLoginUrl for the given bean.
     */
    public static String retrievePreLoginUrl(BaseBean bean) throws GeneralException {
        return retrievePreLoginUrl(bean, false);
    }

    /**
     * Retrieve the preLoginUrl from the request or session if we need to go
     * there after login.
     */
    private static String retrievePreLoginUrl(BaseBean bean, boolean constructing)
        throws GeneralException {

        String preLoginUrl = null;
        Configuration systemConfig = bean.getContext().getConfiguration();
        if ( systemConfig != null && ! systemConfig.getBoolean(Configuration.LOGIN_RETURNS_TO_DASHBOARD) ) {
            preLoginUrl = (String) bean.getSessionScope().get(PageAuthenticationService.ATT_PRE_LOGIN_URL);

            // If the LoginBean was already constructed it may have removed the
            // preLoginUrl from the session already.  If so, grab the LoginBean
            // and try to get the preLoginUrl from it.
            if ((null == preLoginUrl) && !constructing) {
                ValueBinding vb =
                    bean.getFacesContext().getApplication().createValueBinding("#{login}");
                LoginBean lb = (LoginBean) vb.getValue(bean.getFacesContext());
                preLoginUrl = lb.getPreLoginUrl();
            }
        }
        return preLoginUrl;
    }
    
    /* ****************************************************** */
    /* Accessors/Mutators                                     */
    /* ****************************************************** */

    public String getAccountId() { return accountId; }
    public void setAccountId(String accountId) { this.accountId = accountId; }

    public UIComponent getAccountIdInput() { return accountIdInput; }
    public void setAccountIdInput(UIComponent input) { this.accountIdInput = input; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public UIComponent getPasswordInput() { return passwordInput; }
    public void setPasswordInput(UIComponent input) { this.passwordInput= input; }

    /**
     * Try to get the displayable user name.
     * For the expired password case where the passthrough account is expired
     * we might need to look harder to find the account.
     * 
     * @return
     */
    public String getUsername() { 
        if (username == null) {
            try {
                if (accountId != null) {
                    Identity user = findIdentity(this.accountId);
                    if (user == null) {
                        Authenticator authMan = new Authenticator(getContext());
                        List<Application> apps = authMan.getAuthApplications();
                        
                        IdentityFinder finder = new IdentityFinder(getContext());
                        user = finder.findIdentity(accountId, apps);
                        // save off original account id
                        originalAccountId = accountId;
                        Map session = getSessionScope();
                        session.put(ATT_ORIGINAL_ACCOUNT_ID, originalAccountId);
                    }
                    if (user != null) {
                        accountId = user.getName();
                        username = user.getDisplayableName();
                    }
                }
            }
            catch (GeneralException e) {
                if (log.isErrorEnabled())
                    log.error(e.getMessage(), e);
            }
            catch (ConnectorException e) {
                if (log.isErrorEnabled())
                    log.error(e.getMessage(), e);
            }
        }
        return username; 
    }
    
    public void setUsername(String username) { this.username = username; }

    public String getNewPassword() { return newPassword; }
    public void setNewPassword(String password) { this.newPassword = trim(password); }

    public String getConfirmPassword() { return confirmPassword; }
    public void setConfirmPassword(String password) { this.confirmPassword = trim(password); }

    /**
     * Get the password requirements for the first pass through application that the identity
     * has an account on.
     * 
     * @return
     * @throws GeneralException 
     */
    public List<String> getPasswordRequirements() throws GeneralException {
        if (passwordRequirements == null) {
            do {
                if (accountId == null) {
                    // accountId should exist, if not stop here, can't do much more...
                    break;
                }

                this.expiredPasswordAppName = super.getRequestOrSessionParameter(ATT_EXPIRED_APP_NAME);
               
                Link link = getPassthroughLink(null);

                PasswordPolice pp = new PasswordPolice(getContext());
                PasswordPolicy policy = pp.getEffectivePolicy(link);
                
                if (this.expiredPasswordAppName != null) {
                    // if there is a passthrough app defined for identity 
                    if ( !this.expiredPasswordAppName.equals(Authenticator.IIQ) && link != null) {
                        // check if any are defined first
                        if (policy != null) {
                            passwordRequirements = policy.convertConstraints(getLocale(), getUserTimeZone());
                        }
                    }
                    else {
                        // Load up IIQ password requirements?
                        passwordRequirements = pp.getIIQPasswordConstraints(getLocale(), getUserTimeZone());
                    }
                }
                else if (link != null && policy != null) {
                    passwordRequirements = policy.convertConstraints(getLocale(), getUserTimeZone());
                }
            }
            while (false);
        }
     // put in default text
        if (passwordRequirements == null) {
            passwordRequirements = new ArrayList<String>();
            passwordRequirements.add(getMessage(MessageKeys.NO_PASSWORD_CONSTRAINTS));
        }
        // put back on session for next guy
        Map session = getSessionScope();
        session.put(ATT_EXPIRED_APP_NAME, expiredPasswordAppName);
        return passwordRequirements;
    }

    /**
     * Get the first defined passthrough link for the identity
     * 
     * @return
     * @throws ConnectorException 
     */
    private Link getPassthroughLink(Identity user) {
        
        Link link = null;
        try {
            // this must have been preserved
            // jsl - this looks wrong to me if we're being called
            // in a situation where this.accountId has the native id
            // rather than an Identity name, and we find an Identity
            // that happens to have that name, we'll use it even though
            // it may not contain a Link with that id.  An odd situation
            // but it can happen after Links have been moved manually.
            // We hit this case handling the expired password exception
            // since we never changed this.accountId.  I fixed that
            // at a higher level, but other callsers of this
            // method may have the same issue?
            if (user == null) {
                user = findIdentity(this.accountId);
            }
            
            if (user == null) {
                // can't proceed if we can't find user
                addRequiredErrorMessage("expiredForm", "User not found");
            } else {
                link = PasswordResetHelper.getPassthroughLink(
                        getContext(), 
                        user, 
                        (String)getSessionScope().get(ATT_EXPIRED_APP_NAME) == null ? expiredPasswordAppName : null,
                        (String)getSessionScope().get(ATT_ORIGINAL_NATIVE_ID));

            }
        }
        catch (GeneralException e) {
            addRequiredErrorMessage("expiredForm", e.getMessageInstance());
        }
        
        return link;
    }
    
    public void setPasswordRequirements(List<String> passwordRequirements) {
        this.passwordRequirements = passwordRequirements;
    }

    public boolean isLoginError() {
        return loginError;
    }

    public String getPreLoginUrl() {
        return preLoginUrl;
    }

    public void setPreLoginUrl(String preLoginUrl) {
        this.preLoginUrl = preLoginUrl;
    }

	public long getSessionTimeout() {
		if (_sessionTimeout == 0)
			_sessionTimeout = initSessionTimeout();
			
		return _sessionTimeout;
	}

	public void setSessionTimeout(long sessionTimeout) {
		this._sessionTimeout = sessionTimeout;
	}
	
    /**
     * Collapse empty strings to null since we often get
     * them from JSF components.
     */
    public String trim(String src) {
        if (src != null) {
            src = src.trim();
            if (src.length() == 0)
                src = null;
        }
        return src;
    }

	/**
	 * Get the time in milliseconds until the session should expire.
	 * 
	 * @return Milliseconds until session expiration
	 */
	public long getSessionExpiry() {
		@SuppressWarnings("unchecked")
		Map<String, Object> sessionMap = getSessionScope();
		if (null == sessionMap) {
			// session has been invalidated
			_expiry = -1;
		}
		else {
			Calendar now = Calendar.getInstance();
			Calendar sessionExpiration = (Calendar)sessionMap.get(SESSION_EXPIRATION);
			
			if (null == sessionExpiration)
				// if there's no Calendar in session, assume the session is expired 
				_expiry = -1;
			else 
				_expiry = sessionExpiration.getTimeInMillis() - now.getTimeInMillis();
		}
		
		return _expiry; 
	}
	
	public void setSessionExpiry(long expiry) {
		this._expiry = expiry;
	}
	

    /**
     * Check the configuration object and see if we should
     * show detailed messages.
     */
    private boolean reportDetailedErrors() {
        LoginService loginService = new LoginService(getContext());
        return loginService.isDetailedErrorLogging();
    }

    /* ****************************************************** */
    /* Public Methods                                         */
    /* ****************************************************** */

    @SuppressWarnings("unchecked")
    public String login() throws IOException, GeneralException, ExpiredPasswordException {
        // Invalidate the session and associated elements that were created
        // when we viewed the login page.  This should force a new session when
        // we authenticate to prevent "session fixation" attacks.
        this.logout();

        String outcome = null;
        Identity user = null;

        try {
            this.loginError = false;

            // doing basic validation here because
            // it's good practice to validate input values
            if (accountId== null || accountId.length() == 0
                    || password == null || password.length() == 0) {
                throw new GeneralException();
            }
            
            boolean passwordExpired = false;
            
            try {
                //calling Authenticator directly to bypass update identity and create audit event,
                //update Identity and create audit event will be done upon MFA succeeds 
                Authenticator authMan = createAuthenticator(getContext());
                String decryptedPass = getContext().decrypt(password);
                user = authMan.authenticate(accountId, decryptedPass, false, false);
            } catch (ExpiredPasswordException e) {
                // Authenticator must return this
                expiredPasswordAppName = e.getAppName();
                user = e.getIdentity();

                if (expiredPasswordAppName != null && 
                    !expiredPasswordAppName.equals(Authenticator.IIQ) && 
                    getPassthroughLink(user) == null) {
                    addRequiredErrorMessage("loginForm", MessageKeys.NO_ACCOUNT_ERROR);
                    return "";
                }
                
                Map session = getSessionScope();
                // note that we store the Identity name, not the original accountId
                // so when we get back to changePasswordAction we get back to 
                // the right Identity
                session.put(ATT_ORIGINAL_ACCOUNT_ID, accountId);
                if (user != null)
                    accountId = user.getName();
                session.put(ATT_EXPIRED_ACCOUNT_ID, accountId);
                session.put(ATT_EXPIRED_APP_NAME, expiredPasswordAppName);
                session.put(ATT_PASSWORD_RESET_AUTHORIZED, ATT_PASSWORD_RESET_EXPIRED);
                //Store the native id so we can possibly change the right password
                //in case we are dealing with multiple accounts on the same passthrough app 
                //Also check to make sure we do not overwrite what SSO stored in the session 
                if(session.get(ATT_ORIGINAL_NATIVE_ID) == null && e.getResourceObject() != null) 
                    session.put(ATT_ORIGINAL_NATIVE_ID, e.getResourceObject().getIdentity()); 

                // jsl - I'm not sure how this all works, but Authenticator
                // can now pass back the correlated Identity in the
                // ExpiredPasswordException in some cases depending on the Connector.
                // So if you find that you could save it and avoid having to 
                // use IdentityFinder later...

                // if password has expired, we can't redirect to expiredPassword page
                // if the request is from responsive ui. We don't have the responsive
                // expiredPassword page yet. so until then, we will just show error.
                // also skips MFA pages even MFA is applicable.
                if (isResponsiveMobile()) {
                    reportErrorOnException(e);
                    return "";
                }
                
                passwordExpired = true;
            }

            Map session = getSessionScope();
            
            // Save this for Electronic Signature authentication
            session.put(ORIGINAL_AUTH_ID, accountId);

            List<String> mfaWorkflowNames = loginService.getApplicableMFAWorkflowNames(user);
            
            if (Util.isEmpty(mfaWorkflowNames)) {
                if (passwordExpired) {
                    //for expired password, do not set principal user
                    outcome = "expiredPassword";
                    Message msg = new Message(Message.Type.Error,
                        MessageKeys.LOGIN_EXPIRED_PROMPT, expiredPasswordAppName);
                    // IIQCB-2066 - Expired password no longer logs LoginFailed AuditEvent
                    Authenticator.logAuthFailure(getContext(), accountId);

                    addMessageToSession(msg);
                } else {
                    //update lastLogin and create login audit event
                    Authenticator.updateIdentityOnSuccess(getContext(), user);

                    // save state on the HttpSession
                    setPrincipal(user, true);
                    outcome = getLoginOutcome(user);
                }
                //remove from session after consuming it.
                session.remove(PageAuthenticationService.ATT_PRE_LOGIN_URL);
            } else {
                initializeUserTimeZone( null );
                //This is needed in MFA flow.
                session.put(PageAuthenticationService.ATT_MOBILE_LOGIN, isResponsiveMobile());

                //save the redirect url for MFAFilter
                if (WebUtil.isValidRedirectUrl(this.preLoginUrl)) {
                    session.put(PageAuthenticationService.ATT_PRE_LOGIN_URL, this.preLoginUrl);
                }
                
                //check expiredPassword first,
                //changePasswordAction() will check for needToAnswerAuthQuestion() later. 
                if (passwordExpired) {
                    session.put(MFAFilter.EXPIRED_PASSWORD, true);
                } else if (LoginBean.needToAnswerAuthQuestion(getContext(), user)) {
                    session.put(MFAFilter.NEED_AUTH_ANSWERS, true);
                }

                // Store the original authorization source in the session so when MFAFilter audits login we have it.
                session.put(ORIGINAL_AUTH_APPLICATION, user.getAuthApplication());
                outcome = loginService.updateStore(mfaWorkflowNames, user.getName(), new MapSessionStorage(session), false);
            }
        }
        catch (AuthenticationFailureException e) {
            Authenticator.logAuthFailure(getContext(), accountId);
            reportErrorOnException(e);
            outcome = "";
        }
        catch (IdentityLockedException e) {
            Authenticator.logAuthFailure(getContext(), accountId);
            reportErrorOnException(e);
            outcome = "";
        }
        catch (GeneralException e) {
            reportErrorOnException(e);
            // Return to the same page.
            outcome = "";
        }

        return outcome;
    }

    protected Authenticator createAuthenticator(SailPointContext context) {
        return new Authenticator(context);
    }

    protected void externalContextRedirect(String url) throws IOException {
        //We do not want JSF to handle the navigation, as it is not able to pass the ng-view param through
        ExternalContext ec = getFacesContext().getExternalContext();
        ec.redirect(url);
    }

    /**
     * only show detailed error if it is enabled, otherwise show a generic error.
     */
    protected void reportErrorOnException(AbstractLocalizableException e) {
        this.loginError = true;
        if (reportDetailedErrors() && !Util.isNullOrEmpty(e.getMessage())) {
            addRequiredErrorMessage("loginForm", e.getMessageInstance());
        }
        else {
            addRequiredErrorMessage("loginForm", MessageKeys.LOGIN_AUTHENTICATION_FAILED);
        }
    }

    public boolean isRegistrationEnabled(){
        if (registrationEnabled == null) {
            Configuration systemConfig = Configuration.getSystemConfig();

            registrationEnabled = Util.otob(systemConfig.getBoolean("enableSelfServiceRegistration"));
        }

        return registrationEnabled;
    }

    /**
     * Decide where to go next after login, possibly with a password
     * change inserted.
     */
    @SuppressWarnings("unchecked")
    protected String getLoginOutcome(Identity user)
        throws GeneralException, IOException {

        String outcome = null;
        
        // If the identity needs to answer authentication questions save away
        // the preLoginUrl if there is one and go to the authAnswers page.
        if (LoginBean.needToAnswerAuthQuestion(getContext(), user))
        {
            if (null != this.preLoginUrl) {
                getSessionScope().put(PageAuthenticationService.ATT_PRE_LOGIN_URL, this.preLoginUrl);
            }
            outcome = "authAnswers";
        }
        else if (WebUtil.isValidRedirectUrl(this.preLoginUrl)) {
            // Forward to the requested page if the login is the result of
            // trying to access a particular page when not logged in.
            externalContextRedirect(this.preLoginUrl);
        }
        else {
            outcome = "success";
        }
        return outcome;
    }
    
    /**
     * Check if user needs to answer authentication questions
     * @throws GeneralException 
     */
    static public boolean needToAnswerAuthQuestion(SailPointContext ctx, Identity user) {
        
        boolean result = false;
        
        try {
            if (user != null) {
                // refresh user to make sure we don't get cached info
                user = ctx.getObjectById(Identity.class, user.getId());
                ObjectUtil.reattach(ctx, user);
            }
            Challenger ch = new Challenger(ctx);
            if (ctx.getConfiguration().getBoolean(Configuration.PROMPT_FOR_AUTH_QUESTION_ANSWERS_AFTER_LOGIN, false) &&
                    ch.hasUnansweredQuestions(user)) {
                
                result = true; 
            }
        } catch (GeneralException e) {
            if (log.isWarnEnabled())
            {
                log.warn(e.getMessage(), e);
            }
        }
        return result;
    }
    
    /**
     * Save interesting stuff about an authenticated user on he HttpSession.
     * This is what makes it "authenticated".
     */
    private void setPrincipal(Identity user) {
        //sending "false" when calling writeIdentitySession will avoid to audit the action login event.
        setPrincipal(user, false);
    }

    /**
     * Save interesting stuff about an authenticated user on he HttpSession.
     * This is what makes it "authenticated".
     */
    private void setPrincipal(Identity user, boolean audit) {
        LoginService.writeIdentitySession(new MapSessionStorage(getSessionScope()), user, null, audit);

        // If all else has gone well, set the user's timezone in session.
        initializeUserTimeZone( null );
    }

    /**
     * Return whether we are dealing with a pass-through expired password
     * request.
     */
    private boolean isPassThroughExpiredPassword() {
        Map session = getSessionScope();
        expiredPasswordAppName = (String)session.get(ATT_EXPIRED_APP_NAME);
        return (this.expiredPasswordAppName != null && !this.expiredPasswordAppName.equals(Authenticator.IIQ));
    }
    
    /**
     * Action handler for the expired password page.
     */
    public String changePasswordAction() throws IOException {

        String outcome = null;
        Identity user = null;

        try {
            SailPointContext context = getContext();

            // this must have been preserved
            user = findIdentity(this.accountId);

            // Save original account id. We may need it to re-authenticate later
            
            if (user == null) {

                Authenticator authMan = new Authenticator(getContext());
                List<Application> apps = authMan.getAuthApplications();
                
                IdentityFinder finder = new IdentityFinder(getContext());
                user = finder.findIdentity(accountId, apps);
                // make sure to reset the accountId
                if (user != null) {
                    originalAccountId = accountId;
                    accountId = user.getName();
                }
            }
            
            if (user == null) 
                throw new AuthenticationFailureException();

            // If this is a pass-through expired password, update it now.
            if (isPassThroughExpiredPassword()) {
                // if there is a passthrough app defined for identity 
                if (getPassthroughLink(user) != null) {
                    return updatePasswordAction();
                }
                else {
                    // no passthrough account exists.
                    addRequiredErrorMessage("expiredForm", MessageKeys.NO_ACCOUNT_ERROR);
                    return "";
                }
            }
            
            // logout if it looks like they're already logged in
            if (isLoggedIn()) {
                logout();
            }
            
            boolean fieldError = false;
            
            if (requireCurrentPassword && (password == null || password.equals(""))) {
                fieldError = true;
                addRequiredErrorMessage("expiredForm", MessageKeys.LOGIN_MISSING_CURRENT_PASSWORD);
            }
            
            if (newPassword == null || newPassword.equals("")) {
                fieldError = true;
                addRequiredErrorMessage("expiredForm", MessageKeys.LOGIN_MISSING_PASSWORD);
            }

            if (confirmPassword == null || confirmPassword.equals("")) {
                fieldError = true;
                addRequiredErrorMessage("expiredForm", MessageKeys.LOGIN_MISSING_CONFIRMATION);
            }

            if (newPassword != null && confirmPassword != null && (!nullSafeEncryptedEq(context, newPassword, confirmPassword))) {
                fieldError = true;
                addRequiredErrorMessage("expiredForm", MessageKeys.LOGIN_CONFIRMATION_ERROR);
            }
            
            if (fieldError) {
                return "";
            }
            
            List<Message> errors = null;
            try {
                PasswordPolice pp = new PasswordPolice(context);
                CapabilityManager capManager = user.getCapabilityManager();
                boolean isSystemAdmin = Capability.hasSystemAdministrator(capManager.getEffectiveCapabilities());
                boolean isPasswordAdmin = isSystemAdmin || capManager.getEffectiveFlattenedRights().contains(SPRight.SetIdentityPassword);
                if(requireCurrentPassword)
                    pp.setPassword(user, context.decrypt(newPassword), context.decrypt(password), 
                        PasswordPolice.Expiry.USE_SYSTEM_EXPIRY, isSystemAdmin, isPasswordAdmin);
                else
                    pp.setPassword(user, context.decrypt(newPassword), 
                        PasswordPolice.Expiry.USE_SYSTEM_EXPIRY, isSystemAdmin, isPasswordAdmin);
            }catch (PasswordPolicyException pve) {
                errors = pve.getAllMessages();
                if (errors != null && errors.size() > 0) {
                    for (Message error : errors) {
                        addMessage(error, null);
                    }
                }
                //Audit if IIQ password chnage failed
                PasswordPolice.auditPasswordChangefailure(user);
                return null;
            }
            //Audit change of IIQ password
            PasswordPolice.auditExpiredPasswordChange(user);

            // if we passed the police guantlet we can commit and proceed
            context.saveObject(user);
            context.commitTransaction();
            
            // save state on the HttpSession
            setPrincipal(user);
            outcome = getLoginOutcome(user);
            //If Successful, audit the login
            if ("success".equals(outcome)) {
                // Always IdentityIQ because we are changing IIQ password here.
                Authenticator.logAuthSuccess(context, user.getName(), BrandingServiceFactory.getService().getApplicationName(), null);
            }
        }
        catch (GeneralException e) {
            // this get's it near the form
            addRequiredErrorMessage("expiredForm", e.getMessageInstance());
            outcome = "";
        }
        catch (ConnectorException e) {
            addRequiredErrorMessage("expiredForm", e.getMessageInstance());
            if (log.isErrorEnabled())
                log.error(e.getMessage(), e);
            
            outcome= "";
        }
        
        return outcome;
    }

    public String startRegistration() throws GeneralException {
        return "registration";
    }


    public boolean nullSafeEncryptedEq(SailPointContext context,
                                       String encoded1, String encoded2) throws GeneralException {
        String decoded1 = context.decrypt(encoded1);
        String decoded2 = context.decrypt(encoded2);

        return Util.nullSafeEq(decoded1, decoded2);
    }


    /**
     * Find the identity with the given name - this is case insensitive so that
     * it will behave the same way as authentication.
     */
    private Identity findIdentity(String accountId) throws GeneralException {

        QueryOptions ops = new QueryOptions();
        ops.add(Filter.ignoreCase(Filter.eq("name", accountId)));
        List<Identity> results = getContext().getObjects(Identity.class, ops);

        if (1 != results.size()) {
            return null;
        }

        return results.get(0);
    }
    
    /**
     * Pulls the session's timeout interval and stores a Calendar object 
     * in the session indicating when the current session will expire.  
     * 
     * <p>
     * If the web server has been configured so that sessions never expire
     * (i.e. - the &lt;session-timeout&gt; tag in the web.xml has been set to a 
     * negative value), then this method sets its Calendar object to a date a 
     * month into the future.
     * </p> 
     * 
     * @return timeout The value of the session timeout interval in milliseconds
     */
	protected long initSessionTimeout() {
		HttpSession session =
            (HttpSession)getFacesContext().getExternalContext().getSession(true);
        long timeout = session.getMaxInactiveInterval() * 1000;
        
        if (timeout < 0)
        	timeout = 60 * 60 * 24 * 30 * 1000; // one month

        // store a Calendar object in session indicating when the session will expire
        Calendar sessionExpires = Calendar.getInstance();
        sessionExpires.setTimeInMillis(sessionExpires.getTimeInMillis() + timeout);
        session.setAttribute(SESSION_EXPIRATION, sessionExpires);
        
		return timeout;
	}


    /**
     * Checks whether or not the user still has an active session, indicated
     * by the session attribute mapped to <code>BaseBean.SESSION_EXPIRATION</code>.  
     * If user DOES have a valid session, it simply means that the user logged 
     * back into IdentityIQ via a different browser window or tab than the one that 
     * called this method.
     *  
     * @return Login nav rule if session is invalid or expired; 
     * 		   return to the existing page if otherwise
     */
    public String checkSession() throws GeneralException {
    	String navRule = "";
    	Calendar sessionExpiration = (Calendar)getSessionScope().get(SESSION_EXPIRATION);
    	
    	if (null == sessionExpiration)    
    		// no calendar in scope, so log the user out
    		navRule = logout();
    	else if (getSessionExpiry() < 0)
    		// the session has expired, so log the user out
    		navRule = logout();
    	else
    		// close the dialog and return to the existing page
    		navRule = "ok";
    	
    	return navRule;
    }

    
    public String logout() throws GeneralException {
        // bug#20048 - obtain isMobile before invalidating the session
        boolean isMobile = PageAuthenticationFilter.isMobileLogin(getSessionScope());
        
        Object sessionObj = getFacesContext().getExternalContext().getSession(false);
        if(null != sessionObj && (sessionObj instanceof HttpSession)) {
            HttpSession session = (HttpSession) sessionObj;
            if (session.getAttribute(PageAuthenticationFilter.ATT_PRINCIPAL) != null) {
                auditLogoutEvent();
                // clean out the bulkidjoin table for the user
                clearBulkIdJoinEntries();
            }
            session.invalidate();
        }

        getSessionScope().remove(PageAuthenticationFilter.ATT_PRINCIPAL);
        getSessionScope().remove(PageAuthenticationFilter.ATT_CAPABILITIES);
        getSessionScope().remove(PageAuthenticationFilter.ATT_RIGHTS);

        return (isMobile) ? "mobileLogoutSuccess" : "logoutSuccess";
    }

    private void clearBulkIdJoinEntries() throws GeneralException {
        SailPointContext context = getContext();
        context.removeObjects(BulkIdJoin.class, new QueryOptions(Filter.eq("userId", getLoggedInUser().getId())));
        context.commitTransaction();
    }

    private void auditLogoutEvent() throws GeneralException  {
        SailPointContext con = getContext();
        //current user name will be added as source in Auditor.log()
        Auditor.log(AuditEvent.ActionLogout);
        con.commitTransaction();
    }

    public boolean isRequireCurrentPassword() {
        return requireCurrentPassword;
    }

    public boolean isForgotPasswordEnabled() throws GeneralException {
        return forgotPasswordEnabled.getValue();
    }
    
    public boolean isAccountUnlockEnabled() throws GeneralException {
        return accountUnlockEnabled.getValue();
    }
    
    @Deprecated
    public void setForgotPasswordEnabled(@SuppressWarnings("unused") boolean forgotPassword) {
        // noop - we won't be setting forgot password here
    }
    
    /**
     * Gets the component id of error component based on the desktop or mobile login page.
     * @return the component id of the jsf field associated with the error
     */
    private String getLoginFormComponentId() {
        
        boolean isMobile = false;
        
        try {
            isMobile = isResponsiveMobile();
        } catch (Exception ignore) {
            // use the default if we threw up
        }
        
        return isMobile ? "accountId" : "loginForm";
    }

    /**
     * Whether we are in the responsive pages i.e., /ui/login.xhtml etc.
     */
    protected boolean isResponsiveMobile() {
        ExternalContext ec = this.getFacesContext().getExternalContext();
        return WebUtil.isMobile((HttpServletRequest) ec.getRequest());
    }
    
    @SuppressWarnings("unchecked")
    public String userResetAction() throws GeneralException, ConnectorException, IOException {
        
        String outcome = null;
        
        //Ensure the username is not null
        if(Util.isNullOrEmpty(accountId)) {
            this.loginError = true;
            addRequiredErrorMessage(getLoginFormComponentId(), MessageKeys.ENTER_USERNAME);
            return outcome;
        }
        
        // save username if entered
        @SuppressWarnings("rawtypes")
        Map session = getSessionScope();
        
        session.put(ATT_RESET_ACCOUNT_ID, accountId);
        
        //Correlate username to identity name so we can save the lookup later
        Authenticator authMan = new Authenticator(getContext());
        List<Application> apps = authMan.getAuthApplications();

        IdentityFinder finder = new IdentityFinder(getContext());
        Identity user = null;
        try {
            user = finder.findIdentity(accountId, apps);
        } catch (Exception e) {
            log.error("Exception while locating identity using identity id:" + accountId, e);
        }

        // IIQTC-328: Conditional to provide a message if the user is invalid
        if (user == null) {
            log.warn("Unable to locate identity using account id:" + accountId);
        }
        
        // IIQETN-2309 prevent inactive users from logging in. removing ATT_RESET_IDENT_ID creates a fake user reset
        // flow so we don't give away valid user names
        if (isNotValidForReset(user)) {
            session.remove(ATT_RESET_IDENT_ID);
        } else {
            session.put(ATT_RESET_IDENT_ID, user.getName());
        }

        boolean smsEnabled = false, authQuestionEnabled = false;
        Configuration systemConfig = getContext().getConfiguration();
        if ( systemConfig != null ) {
            
            SMSResetConfig smsConfig = (SMSResetConfig) systemConfig.get(Configuration.SMS_RESET_CONFIG);
            smsEnabled = ((smsConfig != null) ? smsConfig.isSmsResetEnabled() : false);
            authQuestionEnabled = systemConfig.getBoolean(Configuration.AUTH_QUESTIONS_ENABLED);
        }
        
        outcome = getRequestContextPath() + USER_RESET_TEMPLATE_URL;
        //Check the System Config to see if SMS Reset and/or Auth Questions are enabled
        if( smsEnabled && authQuestionEnabled) {
            outcome += "/selection";
        } else if(smsEnabled) {
            outcome += "/sms";
        } else if(authQuestionEnabled) {
            outcome += "/questions";
        } else {
            log.warn("No Password Reset configured in System Configuration");
        }

        if(outcome != null) {
            outcome = craftUserResetQueryParams(outcome);
        }

        List<String> mfaWorkflowNames = loginService.getApplicableMFAWorkflowNames(user);
        if (Util.isEmpty(mfaWorkflowNames)) {
            if (outcome != null) {
                //We do not want JSF to handle the navigation, as it is not able to pass the ng-view param through
                externalContextRedirect(outcome);
            }
        } else {
            //store these in session for MFAFilter redirect upon MFA succeeds.
            session.put(MFAFilter.RESET_UNLOCK, true);  
            session.put(MFAFilter.RESET_UNLOCK_URL, outcome);
            
            loginService.updateStore(mfaWorkflowNames, user.getName(), new MapSessionStorage(session), true);
        }

        return null;
    }

    /**
     * Enum of Query Params we will pass to the user Reset flow
     * @author ryan.pickens
     *
     */
    public enum UserResetQueryParams {
        ACTION("action"), ACCOUNT_ID("accountId"), ORIGIN("origin");
        
        private String text;
        
        private UserResetQueryParams(String text) {
            this.text=text;
        }
        
        public String getText() {
            return this.text;
        }
    }
    
    /**
     * Adds query params to the navigation outcome so Angular can determine the context of the request
     * @param outcome
     * @return
     */
    public String craftUserResetQueryParams(String outcome) {
        StringBuilder navString = new StringBuilder(outcome);
        
        navString.append("?");
        
        //Add Reset Action param;
        navString.append(UserResetQueryParams.ACTION.getText());
        navString.append("=");
        navString.append(getUserResetType());
        
        navString.append("&");
        
        //Add Account Id param
        navString.append(UserResetQueryParams.ACCOUNT_ID.getText());
        navString.append("=");
        try {
            navString.append(java.net.URLEncoder.encode(getAccountId(), ENCODING));
        } catch (UnsupportedEncodingException e) {
            log.error(e);
        }

        navString.append("&");
        
        //Add origin param
        navString.append(UserResetQueryParams.ORIGIN.getText());
        navString.append("=");
        if(isDesktopReset()) {
            navString.append(ResetOrigin.DESKTOP_RESET.getText());
        } else if(WebUtil.isMobile((HttpServletRequest)this.getFacesContext().getExternalContext().getRequest())) {
            navString.append(ResetOrigin.MOBILE_UI.getText());
        } else {
            navString.append(ResetOrigin.DESKTOP_UI.getText());
        }
        
        
        return navString.toString();
    }
    
    public boolean isDesktopReset() {
        return Boolean.parseBoolean(super.getRequestOrSessionParameter(ATT_DESKTOP_RESET));
    }
    
    private boolean isNotValidForReset(Identity user) {
        boolean result = user == null || user.isInactive();
        if (user != null && user.isInactive()) {
            log.warn(user.getName() + " is inactive and can not perform user reset operations.");
        }
        return result;
    }

    private ProvisioningPlan buildForgotPasswordPlan(Identity user) throws GeneralException {
        
        Link passThroughLink = getPassthroughLink(user);
        if (passThroughLink == null) {
            throw new GeneralException(MessageKeys.NO_ACCOUNT_ERROR);
        }

        String currentPassword = Util.getString(this.password);
        if (currentPassword != null) {
            currentPassword = getContext().decrypt(currentPassword);
        }
        
        return PasswordResetHelper.buildForgotPasswordPlan(
                    user, 
                    passThroughLink, 
                    currentPassword, 
                    getContext().decrypt(newPassword));
        
    }
    
    /**
     * Currently only being used for forgot and expired password
     * The form name is the same for both but if we start using
     * this for different views we may need to parameterize this.
     * 
     * @throws GeneralException
     */
    private boolean validateNewPassword(Identity user) throws GeneralException {
        boolean fieldError = false;
        boolean validated = true;
        
        if (newPassword == null || newPassword.equals("")) {
            fieldError = true;
            addRequiredErrorMessage("expiredForm", 
                    new GeneralException(MessageKeys.LOGIN_MISSING_PASSWORD).getMessageInstance());
            
        }

        if (confirmPassword == null || confirmPassword.equals("")) {
            fieldError = true;
            addRequiredErrorMessage("expiredForm", 
                    new GeneralException(MessageKeys.LOGIN_MISSING_CONFIRMATION).getMessageInstance());
        }

        if (newPassword != null && confirmPassword != null && (!nullSafeEncryptedEq(getContext(), newPassword, confirmPassword))) {
            fieldError = true;
            addRequiredErrorMessage("expiredForm", 
                    new GeneralException(MessageKeys.LOGIN_CONFIRMATION_ERROR).getMessageInstance());
        }

        if (fieldError) {
            return false;
        }
        
        Link link = getPassthroughLink(user);
        if (link == null) {
            addRequiredErrorMessage("expiredForm", MessageKeys.NO_ACCOUNT_ERROR);
        }
        
        Pair<Boolean, List<Message>> result = PasswordResetHelper.checkPasswordPolicy(getContext(), link, getContext().decrypt(newPassword));
        validated = result.getFirst();
        if (validated == false) {
            for (Message msg : result.getSecond()) {
                addRequiredErrorMessage("expiredForm", msg);
            }
        }

        return validated;
    }
    
    /**
     * Currently only used after resetting password
     */
    private String loginWithNewPassword() throws IOException, ExpiredPasswordException {
        String outcome = null;
        Identity user = null;

        try {
            this.loginError = false;

            if (originalAccountId == null) {
                originalAccountId = super.getRequestOrSessionParameter(ATT_ORIGINAL_ACCOUNT_ID);
                if (originalAccountId != null) {
                    accountId = originalAccountId;
                }
            }
            
            user = getContext().authenticate(accountId, getContext().decrypt(newPassword));

            // save state on the HttpSession
            setPrincipal(user);
            outcome = getLoginOutcome(user);
        }
        catch (AuthenticationFailureException e) {            
            if (reportDetailedErrors()){
                 addRequiredErrorMessage("loginForm", e.getMessageInstance());
            }
            addRequiredErrorMessage("loginForm",  MessageKeys.ERR_AUTHENTICATION_NEW_PASSWORD_FAILED);
            outcome = "";
        }
        catch (GeneralException e) {
            this.loginError = true;
            if ( ( reportDetailedErrors() ) && 
                 (e.getMessage() != null && e.getMessage().length() != 0) ) {
                getFacesContext().addMessage("expiredForm",
                                            new FacesMessage(e.getMessage()));
            }
            else {
                getFacesContext().addMessage("expiredForm",
                                            new FacesMessage(MessageKeys.LOGIN_AUTHENTICATION_FAILED));
            }
            addRequiredErrorMessage("expiredForm", MessageKeys.ERR_AUTHENTICATION_NEW_PASSWORD_FAILED);
            outcome = "";
        }

        return outcome;
    }
    
    /**
     * Change password for passthrough app.
     * 
     * Can fail at any of these points:
     * 
     *      Validate password.
     *      Run change password workflow.
     *      Try to authenticate using new password.
     * 
     * @return
     */
    public String updatePasswordAction()
            throws GeneralException, IOException, ExpiredPasswordException {
        Identity user = null;
        
        if (accountId == null) {
            addRequiredErrorMessage("expiredForm", MessageKeys.AUTH_FAILURE_INVALID_USR);
            return "";
        }

        Map<String, Object> session = getSessionScope();
        
        // Make sure that we should be here
        // if the value is "forgot" then we came from "forgot password"
        // if the value is "expired" then we had an expired password
        String isResetAuthorized = (String)session.get(ATT_PASSWORD_RESET_AUTHORIZED);
        if (null == isResetAuthorized ||
        		(isPassThroughExpiredPassword() && !ATT_PASSWORD_RESET_EXPIRED.equals(isResetAuthorized))) {
        	throw new AuthenticationFailureException();
        }
        
        // save it off for the next guy
        session.put(ATT_EXPIRED_ACCOUNT_ID, accountId);
        
        try {
            user = findIdentity(this.accountId);
            if (user == null) {
                // bad
                addRequiredErrorMessage("expiredForm", MessageKeys.AUTH_FAILURE_INVALID_USR);
                session.remove(ATT_PASSWORD_RESET_AUTHORIZED);
                return "";
            }
        }
        catch(GeneralException ge) {
            // find failed?
            addRequiredErrorMessage("expiredForm", ge.getMessageInstance());
            session.remove(ATT_PASSWORD_RESET_AUTHORIZED);
            return "";
        }

        if (!validateNewPassword(user)) {
            return "";
        }
        
        // Make sure the passthrough app has an integration config that supports setpassword op
        if (!PasswordResetHelper.supportsSetPassword(getContext(), getPassthroughLink(user))) {
            addRequiredErrorMessage("expiredForm", MessageKeys.ERROR_NO_PASSWORD_INTEGRATION_FOR_LINK);
            session.remove(ATT_PASSWORD_RESET_AUTHORIZED);
            return "";
        }
        
        WorkflowLaunch launch = PasswordResetHelper.launchIdentityWorkflow(
                getContext(), 
                buildForgotPasswordPlan(user), 
                user, 
                getFlow(), null); 
        
        String status = ( launch != null ) ? launch.getStatus() : null;
        if (status != null) {
            if (launch.isFailed()) {                
                for (Message msg : launch.getMessages()) {                    
                    if (msg.getType() != Message.Type.Info) {
                        msg.setType(Message.Type.Error);
                        addRequiredErrorMessage("expiredForm", msg);
                    }
                }
                // The launch can fail for many reasons including password policy
                // and history checks.  Redirect the user back to the change 
                // password page, so they can re-try
                return "";
            }
            else if (launch.isApproving()) {
                Message approvingMsg = new Message("Waiting for workflow approval");
                approvingMsg.setType(Message.Type.Info);
                addRequiredErrorMessage("expiredForm", approvingMsg);
            }
            else if (launch.isComplete()) {
            	Message completeMsg = new Message("Workflow complete");
                completeMsg.setType(Message.Type.Info);
                addRequiredErrorMessage("expiredForm", completeMsg);
                session.remove(ATT_PASSWORD_RESET_AUTHORIZED);
                // only try to login with new password when complete
                
                return (isDesktopReset() ? "desktopResetComplete" : loginWithNewPassword());
            }
            else if (launch.isUndefined()) {
            	// direct to login page
            	return "cancel";
            }
            else if (launch.isExecuting()) {
                Message approvingMsg = new Message("Your password change request is pending. Contact the system admin for details.");
                approvingMsg.setType(Message.Type.Info);
                addRequiredErrorMessage("expiredForm", approvingMsg);
                // direct to login page
                return "cancel";
            }
        }
        
        session.remove(ATT_PASSWORD_RESET_AUTHORIZED);
        return getLoginOutcome(user);
    }

    /**
     * returns the 'flow' for the workflow to be launched.
     * 'ForgotPassword' or 'ExpiredPassword'
     */
    private String getFlow() {
        String flow = IdentityResetService.Consts.Flows.FORGOT_PASSWORD_FLOW.value();
        if( isPassThroughExpiredPassword() ) {
            flow = IdentityResetService.Consts.Flows.EXPIRED_PASSWORD_FLOW.value();
        }
        return flow;
    }

    public String getUserResetType() {
        return _userResetType;
    }

    public void setUserResetType(String userResetType) {
        this._userResetType = userResetType;
    }

    /**
     * Calculate this value based on passthrough apps 
     * as wells as sytem config option.
     */
    private boolean loadForgotPasswordEnabled() {
        
        return (forgotPasswordConfigOptionEnabled && loadPassthroughEnabled());
    }

    /**
     * Calculate this value based on passthrough apps 
     * as wells as sytem config option.
     */
    private boolean loadAccountUnlockEnabled() {
        
        return (accountUnlockConfigOptionEnabled && loadPassthroughEnabled());
    }
    
    private boolean loadPassthroughEnabled() {
        // check is there is a passthrough app
        boolean passthrough = false;

        Authenticator authMan = new Authenticator(getContext());
        
        try {
            List<Application> apps = authMan.getAuthApplications();
            if (apps != null && apps.size() > 0) {
                passthrough = true;
            }
        }
        catch (GeneralException e) {
            addRequiredErrorMessage("expiredForm", e.getMessageInstance());
        }
        
        return passthrough;
    }
}