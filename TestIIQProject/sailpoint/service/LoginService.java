/* (c) Copyright 2017 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.DynamicScopeMatchmaker;
import sailpoint.api.SailPointContext;
import sailpoint.api.SailPointFactory;
import sailpoint.object.Configuration;
import sailpoint.object.DynamicScope;
import sailpoint.object.Identity;
import sailpoint.object.Link;
import sailpoint.object.MFAConfig;
import sailpoint.object.RemoteLoginToken;
import sailpoint.object.Rule;
import sailpoint.server.Authenticator;
import sailpoint.service.IdentityResetService.Consts.SessionAttributes;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.web.LoginBean;
import sailpoint.web.MFAFilter;
import sailpoint.web.PageAuthenticationFilter;
import sailpoint.web.identity.PolicyViolationsHelper;

/**
 * A service class that handles retrieving and returning login data.
 */
public class LoginService {

    private SailPointContext _context;
    private Redirectable redirector;
    private static final Log _log = LogFactory.getLog(LoginService.class);

    public LoginService(SailPointContext context) {
        _context = context;
    }

    /**
     * @return the redirector
     */
    public Redirectable getRedirector() {
        return redirector;
    }

    /**
     * @param redirector the redirector to set
     */
    public void setRedirector(Redirectable redirector) {
        this.redirector = redirector;
    }

    /**
     * Returns all applicable MFA workflow names for the user.
     *
     */
    public List<String> getApplicableMFAWorkflowNames(Identity user) throws GeneralException {
        List<String> appWorkflownames = new ArrayList<>();
        // abort if user is null
        if (user == null) {
            return appWorkflownames;
        }
        DynamicScopeMatchmaker dsm = new DynamicScopeMatchmaker(_context);

        Configuration configuration = _context.getObjectByName(Configuration.class, Configuration.MFA);
        if (configuration != null) {
            @SuppressWarnings("unchecked")
            List<MFAConfig> mfaConfigs = configuration.getList(Configuration.MFA_CONFIG_LIST);
            for(MFAConfig mfaConfig : Util.safeIterable(mfaConfigs)) {
                if(mfaConfig.isEnabled()) {
                    List<DynamicScope> populations = mfaConfig.getPopulations();
                    for (DynamicScope ds : Util.safeIterable(populations)) {
                        if (dsm.isMatch(ds, user, false)) {
                            if (mfaConfig.getWorkflow() != null) {
                                appWorkflownames.add(mfaConfig.getWorkflow().getName());
                                break;
                            }
                            
                            _log.error("Workflow in MFAConfig is null:" + mfaConfig);
                        }
                    }
                }
            }
        }
        return appWorkflownames;
    }
    
    public boolean isLoggedIn(SessionStorage store) {
        return null != store.get(PageAuthenticationService.ATT_PRINCIPAL);
    }
    
    /**
     * Consolidates various calls in the web layer to one place. Will check the SystemConfiguration object and return
     * if detailed error logging is enabled.
     * @return if detailed error logging is enabled.
     */
    public boolean isDetailedErrorLogging() {
        try {
            Configuration config = _context.getConfiguration();
            if ( config != null ) {
                if (Configuration.DETAILED_LOGIN_STYLE.equals(config.getString(Configuration.LOGIN_ERROR_STYLE))){
                    return true;
                }
            }
        } catch (GeneralException ex) {
            // default to "no details"
        }
        return false;
    }
    
    /**
     * Updates the SessionStorage with variables used to configure multi factor authentication. A REDIRECTOR
     * MUST BE CONFIGURED BEFORE CALLING THIS METHOD. A side effect of setting the storage is a redirection
     * to the multi factor authentication page.
     * @param validWorkflows valid MFA workflows for the user
     * @param userName username that has performed successful password based authentication
     * @param store session storage to use
     * @param performSingleMFARedirect will redirect to the mfa form page if true
     * @return jsfOutcome useful in places like LoginBean that are still jsf bean based.
     * @throws IOException
     */
    public String updateStore(List<String> validWorkflows, String userName, SessionStorage store, boolean performSingleMFARedirect) 
            throws IOException {
        if (getRedirector() == null) {
            throw new IllegalStateException("must set redirectable before calling updateStore");
        }
        
        if (Util.size(validWorkflows) == 1) {
            store.put(MFAFilter.MFA_USER_NAME, userName);  
            store.put(MFAFilter.MFA_SELECTED_WORKFLOW, validWorkflows.get(0));
            if (performSingleMFARedirect) {
                getRedirector().redirect(null, "/external/mfa/mfa.jsf", null, null);
            }
            return "mfa";
        }
        
        store.put(MFAFilter.MFA_USER_NAME, userName);
        store.put(MFAFilter.MFA_WORKFLOWS, Util.listToCsv(validWorkflows));  
        getRedirector().redirect(null, "/external/mfa/mfa.jsf#/selection", null, null);
        return null;
    }

    /**
     * Runs ssoRule against a rule context. If the rule returns a Link, sessionStorage put
     * the original native id and account id used to authenticate in the store. If the rule
     * returns an Identity (original SSO Rule behavior) the Identity will be returned without
     * setting Link session variables.
     * @param sessionStorage store to write Link session variables
     * @param ssorule rule to run
     * @param ruleCtx rule context to run under
     * @return Identity returned in the rule
     * @throws GeneralException
     */
    public Identity runSSORule(SessionStorage sessionStorage, Rule ssorule, HashMap<String, Object> ruleCtx) throws GeneralException {
        Object ruleResult = _context.runRule(ssorule, ruleCtx);
        Identity user = null;

        if (ruleResult != null) {
            // 6.1 - Added the ability for the SSO rules to return a Link
            // object so we know exactly which account was used during the
            // authentication.
            if (ruleResult instanceof Link) {
                Link link = (Link) ruleResult;
                user = link.getIdentity();
                writeLinkToSession(sessionStorage, link);
            } else if (ruleResult instanceof Identity) {
                user = (Identity) ruleResult;
            } else {
                _log.error("SSO Rule must return either an identity or a Link object.");
            }
        }

        return user;
    }

    public Identity processRemoteTokenLogin(String tokenId) throws GeneralException {
        RemoteLoginToken rlt = _context.getObjectById(RemoteLoginToken.class, tokenId);
        Identity user = null;
        if (rlt == null) {
            _log.error("Remote login failed. Remote Token was not found with id [" + tokenId + "]");
        } else {
            if (rlt.isExpired()) {
                _log.error("Remote login failed. Remote Token [" + tokenId + "] IS EXPIRED. Removing...");
                _context.removeObject(rlt);
                _context.commitTransaction();
                _context.decache(rlt);
            } else {
                String identityName = rlt.getName();
                if (identityName != null) {
                    user =_context.getObjectByName(Identity.class, identityName);
                }
            }
        }
        return user;
    }
    
    public static void writeIdentitySession(SessionStorage sessionStorage, Identity user, Link link) {
        writeIdentitySession(sessionStorage, user, link, true);
    }

    public static void writeIdentitySession(SessionStorage sessionStorage, Identity user) {
        writeIdentitySession(sessionStorage, user, null, true);
    }

    public static void writeIdentitySession(SessionStorage sessionStorage, Identity user, Link link, boolean audit) {

        sessionStorage.put(PageAuthenticationFilter.ATT_PRINCIPAL, user.getName());
        // jsl - to make auth filtering faster, store the capabilities
        // list too so we don't have to keep fetching the Identity
        // KG - We might not need these in the end user pagse.
        // djs: these getEffective methods are required so user's also
        // get rights/capabiities granted via workgroups
        sessionStorage.put(PageAuthenticationFilter.ATT_CAPABILITIES, user.getCapabilityManager().getEffectiveCapabilities());
        sessionStorage.put(PageAuthenticationFilter.ATT_RIGHTS, user.getCapabilityManager().getEffectiveFlattenedRights());

        // Get a list of attributes used for authentication or
        // authorization purposes.
        sessionStorage.put(PageAuthenticationFilter.ATT_ID_AUTH_ATTRS, getIdentityAuthAttributes(user));
        
        writeLinkToSession(sessionStorage, link);

        // TODO: We shouldn't use the default TimeZone here.  The problem
        // is that (unlike locale) there is no way to determine the user's
        // time zone from the HTTP headers.  The only way to do this in a
        // browser is to use javascript to calculate the time zone and post
        // it to the server.  This is no problem if you're using a login
        // page (since the login page can calculate this value), but if you
        // are going directly to the page you don't have an opportunity to
        // get the time zone.  There are lots of possibly solutions - all
        // of which are pretty ugly or major pains - so for now we'll just
        // ignore the problem.
        // session.setAttribute(BaseBean.SESSION_TIMEZONE, TimeZone.getDefault());
        try {
            SailPointContext ctx = SailPointFactory.getCurrentContext();
            putDynamicScopeInSession(ctx, user, sessionStorage);
            if (audit) {
                // This is set in Authenticator.authenticate methods
                String accountId = user.getAuthAccount();
                if (accountId == null) {
                    // This is set by SSO
                    accountId = (String)sessionStorage.get(LoginBean.ATT_ORIGINAL_NATIVE_ID);
                }
                if (accountId == null) {
                    // This is set by LoginBean
                    accountId = (String)sessionStorage.get(LoginBean.ORIGINAL_AUTH_ID);
                }
                // This is set in Authenticator.authenticate methods
                String source = user.getAuthApplication();
                if (source == null) {
                    if (Util.otob(sessionStorage.get(PageAuthenticationService.ATT_SSO_AUTH))) {
                        // This is set to the SSOAuthenticator class name if that is how we were authenticated
                        source = (String)sessionStorage.get(PageAuthenticationService.ATT_SSO_AUTHENTICATOR);
                    } else {
                        // This is set in LoginBean in case of MFA
                        source = (String)sessionStorage.get(LoginBean.ORIGINAL_AUTH_APPLICATION);
                    }
                }

                Authenticator.logAuthSuccess(ctx, user.getName(), source, accountId);
            }
        } catch (GeneralException e) {
            _log.error("Couldn't audit the login for user: " + user.getName(), e);
        }
    }
    
    /**
     * Writes the attributes linkAttrs to the sessionStorage. Useful when you want to persist attributes
     * when the session may have been renewed and attributes were lost from the original session. 
     * @param sessionStorage sessionStore to write to
     * @param linkAttrs attributes to write to the session. Will only write attributes with non-null values.
     */
    public static void writeAncillarySession(SessionStorage sessionStorage, Map<String, Object> linkAttrs) {
        if (sessionStorage != null && !Util.isEmpty(linkAttrs)) {
            for (String key : Util.safeIterable(linkAttrs.keySet())) {
                if (linkAttrs.get(key) != null) {
                    sessionStorage.put(key, linkAttrs.get(key));
                }
            }
        }
    }
    
    /**
     * Prior to renewing the SessionStorage object, extract a few Link session attributes that
     * got set in {@link sailpoint.service.LoginService#runSSORule(SessionStorage, Rule, HashMap)}.
     * @param sessionStorage abstracted SessionStorage object
     * @return Map of session variables {@link sailpoint.web.LoginBean#ATT_ORIGINAL_ACCOUNT_ID}
     *   and {@link sailpoint.web.LoginBean#ATT_ORIGINAL_NATIVE_ID}
     */
    public static Map<String, Object> getLinkSessionAttributes(SessionStorage sessionStorage) {
        Map<String, Object> result = new HashMap<>();
        if (sessionStorage != null) {
            putIfNotNull(sessionStorage, LoginBean.ATT_ORIGINAL_ACCOUNT_ID, result);
            putIfNotNull(sessionStorage, LoginBean.ATT_ORIGINAL_NATIVE_ID, result);
        }
        
        return result;
    }

    private static void putIfNotNull(SessionStorage sessionStorage, String key, Map<String, Object> result) {
        if (sessionStorage != null && key != null && result != null) {
            Object value = sessionStorage.get(key);
            if (value != null) {
                result.put(key, value);
            }
        }
    }
    
    private static void writeLinkToSession(SessionStorage sessionStorage, Link link) {
        if (link != null) {
            sessionStorage.put(LoginBean.ATT_ORIGINAL_ACCOUNT_ID, link.getDisplayName());
            sessionStorage.put(LoginBean.ATT_ORIGINAL_NATIVE_ID, link.getNativeIdentity());
        }
    }

    public static void putDynamicScopeInSession(SailPointContext context, Identity user, SessionStorage sessionStorage) {
        putDynamicScopeInSession(context, user, createSessionAddable(sessionStorage));
    }

    private static void putDynamicScopeInSession(SailPointContext context, Identity user, SessionAddable session) {
        try {
            DynamicScopeMatchmaker speedDater = new DynamicScopeMatchmaker(context);
            List<String> matches = speedDater.getMatches(user);

            session.put(SessionAttributes.ATT_DYNAMIC_SCOPES.value(), matches);
        } catch (GeneralException ge) {
            _log.warn("Something went wrong obtaining dynamic scopes for user: "
                    + ((user == null) ? "null" : user.getName()) + "... continuing login process...", ge);
        }
    }

    /**
     * An interface which allows users to put values in there.
     *
     * We need this interface instead of HttpSession
     * because sailpoint.web.* classes return a
     * {@link Map} class. We need this to bridge the
     * gap between two different Session interfaces.
     *
     */
    private interface SessionAddable {
        void put(String key, Object val);
    }

    private static SessionAddable createSessionAddable(final SessionStorage sessionStorage) {
        return new SessionAddable() {
            @Override
            public void put(String key, Object val) {
                sessionStorage.put(key, val);
            }
        };
    }

    /**
     * Gets a list of attributes used for authentication or authorization
     * purposes. These do not reflect normal identity attributes, but any sort
     * of property, attribute or derived stuff we might need.
     *
     * @param user User being authenticated
     * @return Map of identity attributes used for authentication
     */
    public static Map<String, Object> getIdentityAuthAttributes(Identity user){

        Map<String, Object> identityAuthAttrs = new HashMap<>();

        if (user==null) {
            return identityAuthAttrs;
        }

        // set a flag that indicates that this user is a mgr. Since we
        // have no 'Manager' capability, This flag controls the display
        // of the policy violation viewer menu list of identity properties
        //  which affect some aspects of authentication
        identityAuthAttrs.put(SessionAttributes.ID_AUTH_ATTR_MGR_STATUS.value(), user.getManagerStatus());

        // the display of the policy violation viewer has been expanded
        // to support the ability of non-manager to be assigned ownership
        // of policy violations
        try {
            identityAuthAttrs.put(
                    SessionAttributes.ID_AUTH_ATTR_VIOLATION_OWNER.value(),
                    PolicyViolationsHelper.isViolationOwner(user, SailPointFactory.getCurrentContext()));
        } catch (GeneralException e) {
            _log.warn("Problem determining violation ownership for " + user.getName() +
                " [" + e.getMessage() + "]");
        }

        return identityAuthAttrs;
    }
}
