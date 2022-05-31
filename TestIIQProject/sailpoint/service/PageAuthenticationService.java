/* (c) Copyright 2017 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.SailPointContext;
import sailpoint.api.SailPointFactory;
import sailpoint.injection.SailPointInjection;
import sailpoint.object.Configuration;
import sailpoint.object.Identity;
import sailpoint.object.Link;
import sailpoint.object.WorkItem;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.web.LoginBean;
import sailpoint.web.PageAuthenticationFilter;
import sailpoint.web.PageCodeBase;
import sailpoint.web.sso.DefaultSSOValidator;
import sailpoint.web.sso.DefaultSSOValidator.DefaultSSOValidatorInput;
import sailpoint.web.sso.DefaultSSOValidator.DefaultSSOValidatorResult;
import sailpoint.web.sso.SSOAuthenticationRunner;
import sailpoint.web.sso.SSOAuthenticator.SSOAuthenticationInput;
import sailpoint.web.sso.SSOAuthenticator.SSOAuthenticationResult;

/**
 * This class is the business logic layer for PageAuthenticationFilter. It should have no dependency on javax.servlet classes or any
 * sailpoint.web classes for that matter. Unfortunately, there are still ties to the HttpServletRequest and HttpServletResponse objects
 * in the SSOAuthentication layers.
 */
public class PageAuthenticationService {
    
    /**
     * Abstracts http request method
     */
    public interface PageAuthRequest {
        String getHeader(String name);
        String getParameter(String name);
        String getRequestURI();
        String getContextPath();
    }
    
    /**
     * Abstracts http response method
     */
    public interface PageAuthResponse {
        void sendRedirect(String redir) throws IOException;
    }
    
    /**
     * Similar to {@link SessionStorage}, abstracts out configuration information
     */
    public interface ConfigStorage {
        /**
         * Returns a configuration
         * @param key String value of the key
         * @return the value as a string
         */
        String get(String key);
    }
    
    /**
     * Abstracts http session methods
     */
    public interface InvalidatableStorage extends SessionStorage {
        String getId();
        void writeIdentity(Identity identity);
        void invalidate();
    }
    
    public interface InvalidatableStorageFactory {
        InvalidatableStorage renew();
        InvalidatableStorage getStore();
    }

    /**
     * Abstracts filterchain methods
     */
    public interface Chainable {
        void doFilter() throws ServletException, IOException;
    }
    
    
    /* too many references to refactor for little gain in package cyclic dependencies */
    /** use to reduce dependencies on web packages **/
    public static final String ATT_PRINCIPAL = PageAuthenticationFilter.ATT_PRINCIPAL;

    /** value used with ATT_SSO_AUTH */
    public static final String TRUE = "true";

    /**
     * Flag to indicate we've authenticated using an sso rule.
     * requested URL before we redirected for login.
     */
    public static final String ATT_SSO_AUTH = "ssoAuthenticated";

    /**
     * Name of the SSOAuthenticator class that successfully authenticated the user
     */
    public static final String ATT_SSO_AUTHENTICATOR = "ssoAuthenticator";

    /**
     * Name of an HttpRequest attribute we set to hold the originally
     * requested URL before we redirect from setting the TimeZone.
     */
    public static final String ATT_TZ_REDIRECT = "timeZoneRedirect";

    /**
     * Flag to indicate this is an exceptional, single use authorization.  
     * Currently used for 'user' page authorization, to flag the session to 
     * re-authorize next time.
     */
    public static final String ATT_USER_PAGE_AUTH = "userPageAuth";
    
    /**
     * Name of an HttpRequest attribute we set to hold the originally
     * requested URL before we redirected for login.
     */
    public static final String ATT_PRE_LOGIN_URL = "preLoginUrl";

    /**
     * This serves a similar purpose to ATT_PRE_LOGIN_URL, except that
     * it's used in the case that we are doing SSO and MFA.  During
     * SSO, if we need to redirect to do MFA, we don't want to redirect
     * to the ATT_PRE_LOGIN_URL until after the MFA.  If ATT_PRE_LOGIN_URL
     * is set, we would try and redirect there, but then go throw SSO
     * again.  Having this second Attribute will allow us to break that
     * cycle.
     */
    public static final String ATT_POST_MFA_REDIRECT = "postMFARedirect";

    /**
     * Flag used to track if a user logged in through the mobile responsive ui
     * or attempted to access a mobile responsive ui page directly
     */
    public static final String ATT_MOBILE_LOGIN = "mobileLogin";

    
    private static final Log log = LogFactory.getLog(PageAuthenticationService.class);
    
    /**
     * Cached system configuration value for whether to allow unauthenticated
     * access to the end user pages.
     */
    private static Boolean allowUnauthenticatedEndUserPages;

    /**
     * Root path for the mobile responsive ui
     */
    private static final String MOBILE_PATH = "/ui/";

    /**
     * Don't consider urls that contain these strings mobile urls
     */
    private static final String[] MOBILE_PATH_EXCLUDE_LIST = {"/ui/js/"};

    /**
     * Flag to indicate we should avoid using SSO authentication. This
     * is required in instances where an SSO Authenticated user visits
     * the Login prompt with the intent to login under a different
     * context
     */
    static final String ATT_SSO_DISABLED = "ssoDisabled";

    static final String SSO_BYPASS_PROMPT = "prompt";

    
    private static String DESKTOP_RESET_SERVLET = "/desktopreset";

    
    /* consider getting rid of this altogether, but is tied to SSOAuthentication */
    private HttpServletRequest httpRequest;
    private HttpServletResponse httpResponse;
    
    /* abstracted interfaces for removing http-like objects from service package */
    private PageAuthRequest request;
    private PageAuthResponse response;
    private InvalidatableStorageFactory storeFactory;
    private Chainable chain;
    private Redirectable redirector;
    
    /* leftover uncommented baggage from PageAuthenticationFilter */
    private Object principal;
    private String url;
    private String relativeUrl;
    private boolean isMobile;


    /**
     * Where we go when we need to authenticate.
     */
    private String loginUrl;

    private String mobileLoginUrl;

    private String promptLoginUrl;

    private String promptMobileLoginUrl;

    /**
     * Where we go after a logout.
     */
    private String logoutUrl;

    /**
     * Where we go when the session times out.
     */
    private String timeoutUrl;

    /**
     * Where we go if we need to set the TimeZone.
     */
    private String setTimezoneUrl;

    /**
     * Where we go when the user password has expired.
     */
    private String expiredUrl;

    private String resetPasswordUrl;
    
    private String forgotPasswordUrl;

    private String desktopResetSuccessUrl;

    /**
     * usually /external/zeePage, /ui/external/zeePage or /external/rest/zeePage
     */
    private String externalPagesPath;
    private String mobileExternalPagesPath;
    private String externalRestPath;
    
    /**
     * Where we go if the user can't be determined an unauthenticated end user
     * request.
     */
    private String endUserLoadFailureUrl;

    /**
     * List of resource urls that are allowed to pass through with
     * user page authentication only.
     */
    private List<String> userPageResourceUrls;

    /**
     * Default landing pages that SSO will redirect to if a user requests
     * context root or login pages
     */
    private String defaultLandingUrl;
    private String defaultMobileLandingUrl;
    
    //List of params to fiter if we make it to the handleFinally block
    private List<String> handleFinallyFilterParams;

    /**
     * This holds an ordered list of commands.
     * Only the first command where the condition is met is executed.
     * This pattern will help us get rid of giant if else if block.
     * {@link CommandHandler}
     */
    private List<CommandHandler> commandHandlers;
    
    /**
     * If a condition {@link CommandHandler#condition()} is met then 
     * the command {@link CommandHandler#command()} is executed.
     * Each instance of this class encapsulates logic for handling 
     * one type of request.
     *
     * @author tapash.majumder
     *
     */
    private interface CommandHandler {
        boolean condition() throws ServletException;
        boolean command() throws ServletException, IOException;
        String getName();
    }
    
    public PageAuthenticationService(ConfigStorage config, InvalidatableStorageFactory storeFactory) {
        this.storeFactory = storeFactory;

        // don't need config for anything but setup of local variables
        loginUrl = config.get("loginUrl");
        mobileLoginUrl = config.get("mobileLoginUrl");
        promptLoginUrl = config.get("promptLoginUrl");
        promptMobileLoginUrl = config.get("promptMobileLoginUrl");
        logoutUrl = config.get("logoutUrl");
        timeoutUrl = config.get("timeoutUrl");
        setTimezoneUrl = config.get("setTimezoneUrl");
        expiredUrl = config.get("expiredPasswordUrl");
        endUserLoadFailureUrl = config.get("endUserLoadFailureUrl");
        resetPasswordUrl = config.get("resetPasswordUrl");
        forgotPasswordUrl = config.get("forgotPasswordUrl");
        desktopResetSuccessUrl = config.get("desktopResetSuccessUrl");
        externalPagesPath = config.get("externalPagesPath");
        mobileExternalPagesPath = config.get("mobileExternalPagesPath");
        externalRestPath = config.get("externalRestPath");
        defaultLandingUrl = config.get("defaultLandingUrl");
        defaultMobileLandingUrl = config.get("defaultMobileLandingUrl");
        userPageResourceUrls = new ArrayList<>();
        String userResourceUrlString = config.get("userPageResourceUrls");
        if (!Util.isNullOrEmpty(userResourceUrlString)) {
            for (String url : userResourceUrlString.split(",")) {
                if (!Util.isNullOrEmpty(url)) {
                    userPageResourceUrls.add(url);
                }
            }
        }
    }

    /**
     * @return the httpRequest
     */
    public HttpServletRequest getHttpRequest() {
        return httpRequest;
    }

    /**
     * @param httpRequest the httpRequest to set
     */
    public void setHttpRequest(HttpServletRequest httpRequest) {
        this.httpRequest = httpRequest;
        this.url = httpRequest.getRequestURL().toString();
        this.relativeUrl = httpRequest.getRequestURI().substring(httpRequest.getContextPath().length());

    }

    /**
     * @return the httpResponse
     */
    public HttpServletResponse getHttpResponse() {
        return httpResponse;
    }

    /**
     * @param httpResponse the httpResponse to set
     */
    public void setHttpResponse(HttpServletResponse httpResponse) {
        this.httpResponse = httpResponse;
    }
    
    /**
     * @return the httpRequest
     */
    public PageAuthRequest getRequest() {
        return request;
    }
    
    /**
     * @param request the httpRequest to set
     */
    public void setRequest(PageAuthRequest request) {
        this.request = request;
        
    }
    
    /**
     * @return the httpResponse
     */
    public PageAuthResponse getResponse() {
        return response;
    }
    
    /**
     * @param httpResponse the httpResponse to set
     */
    public void setResponse(PageAuthResponse httpResponse) {
        this.response = httpResponse;
    }

    /**
     * @return the chain
     */
    public Chainable getChain() {
        return chain;
    }

    /**
     * @param chain the chain to set
     */
    public void setChain(Chainable chain) {
        this.chain = chain;
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

    public Object getPrincipal() {
        if (principal == null && storeFactory.getStore() != null) {
            principal = storeFactory.getStore().get(ATT_PRINCIPAL);
        }
        return principal;
    }

    public void setPrincipal(Object principal) {
        this.principal = principal;
    }
    
    public void clearPrincipalAndStuff() {
        InvalidatableStorage store = storeFactory.getStore();
        store.remove(PageAuthenticationService.ATT_PRINCIPAL);
        store.remove(PageAuthenticationFilter.ATT_CAPABILITIES);
        store.remove(PageAuthenticationFilter.ATT_RIGHTS);
        store.remove(PageAuthenticationService.ATT_USER_PAGE_AUTH);
        store.remove(RequestAccessService.ATT_TICKET_MANAGEMENT_APPLICATION);
        store.remove(RequestAccessService.ATT_EXTERNAL_SOURCE);
        store.remove(PageAuthenticationService.ATT_MOBILE_LOGIN);
        store.remove(PageAuthenticationService.ATT_SSO_AUTH);
        store.remove(PageAuthenticationService.ATT_SSO_AUTHENTICATOR);
    }

    public boolean isReservedUrl() {
        // if we timeout invalidate the session and redirect
        return (url.indexOf(timeoutUrl) >= 0 ||
                url.indexOf(setTimezoneUrl) >= 0 ||
                url.indexOf(logoutUrl) >= 0 ||
                url.indexOf(loginUrl) >= 0 ||
                url.indexOf(expiredUrl) >= 0 ||
                url.indexOf(resetPasswordUrl) >= 0 ||
                isExternalPage() ||
                url.indexOf(endUserLoadFailureUrl) >= 0);
    }
    
    /**
     * Adds an SSO authentication attribute to the storage mechanism and performs a possible redirect to the
     * timezone url.
     * @param authResult auth result after authentication
     * @return true if a redirect was performed
     * @throws IOException if Redirectable has exceptions
     */
    private boolean setUpSSOTimeZone(SSOAuthenticationResult authResult, InvalidatableStorage storage) throws IOException {
        boolean redirect = false;
        TimeZone tz = (TimeZone)storage.get(PageCodeBase.SESSION_TIMEZONE);
        if (tz == null) {
            // redirect to setTimeZone page in order to scrape timezone info from browser
            if (url.indexOf(setTimezoneUrl) < 0) {
                redirect = true;
                redirector.redirect(ATT_TZ_REDIRECT, setTimezoneUrl, authResult.getParamsToFilter(), authResult.getRedirectURL());
            }
        }
        return redirect;
    }

    private void writeSsoAuth(SSOAuthenticationResult authResult, InvalidatableStorage storage) {
        storage.put(ATT_SSO_AUTH, PageAuthenticationService.TRUE);
        storage.put(ATT_SSO_AUTHENTICATOR, authResult.getAuthenticator());
    }
    
    /**
     * Means none of the CommandHandlers could handle the special cases.
     * This is the 'else' in the big if else if block
     */
    public void handleFinally() throws ServletException, IOException {
        if (storeFactory.getStore().get(ATT_USER_PAGE_AUTH) != null) {
            clearPrincipalAndStuff();
        }
        if (log.isDebugEnabled()) {
            log.debug("store.id:" + storeFactory.getStore().getId());
        }
        Object principal = getPrincipal();
        doMobileLoginStuff();
        if (principal == null) {
            String redirectUrl = promptLoginUrl;
            if(isMobile) {
                redirectUrl = promptMobileLoginUrl;
            }

            redirector.redirect(ATT_PRE_LOGIN_URL, redirectUrl, handleFinallyFilterParams, null);
        } else {
            // authorized, continue
            chain.doFilter();
        }
    }

    /**
     * The conditions for the command handlers are executed in order
     * and the first {@link CommandHandler#condition()} to succeed is executed.
     *
     * @return true if one of the CommandHandlers was executed.
     */
    public boolean handleCommands() throws ServletException, IOException {
        createCommandHandlers();

        boolean handled = false;
        for (CommandHandler commandHandler : commandHandlers) {
            if (commandHandler.condition() && commandHandler.command()) {
                if (log.isDebugEnabled()) {
                    log.debug("handled command using: " + commandHandler.getName());
                }
                handled = true;
                break;
            }
        }
        return handled;
    }
    
    /**
     * creates the list of commands
     * only one of these commands will be executed.
     * We will check each of these commands sequentially until the {@link CommandHandler#condition()} is met.
     */
    private void createCommandHandlers() {
        commandHandlers = new ArrayList<>();

        // Run these handlers before trying SSO
        commandHandlers.add(createExternalPagesHandler());
        commandHandlers.add(createTimeoutHandler());
        commandHandlers.add(createDesktopResetHandler());
        commandHandlers.add(createFacesResourceHandler());


        //SSO Handlers
        commandHandlers.add(createSSOBypassHandler());
        commandHandlers.add(createSSOValidatorHandler());
        commandHandlers.add(createSSOHandler());


        commandHandlers.add(createLogoutHandler());
        commandHandlers.add(createLoginHandler());
        commandHandlers.add(createExpiredOrResetPasswordsHandler());
        commandHandlers.add(createLegacyForgotPasswordHandler());


        commandHandlers.add(createEndUserLoadFailureHandler());
        commandHandlers.add(createErrorHandler());
        commandHandlers.add(createUnauthenticatedEndUserPagesHandler());
        commandHandlers.add(createUserPageAuthHandler());
    }

    /**
     * Run this every time a request comes in if an SSO Validation rule is configured. The
     * handler will return false in command() if validation is successful to allow for other
     * handlers to process the request. One such case is the DefaultSSOAuthenticator where if
     * the user isn't logged in, we still want to use the SSO handler to process the request.
     */
    private CommandHandler createSSOValidatorHandler() {
        return new CommandHandler() {
            @Override
            public String getName() {
                return "SSOValidator";
            }
            @Override
            public boolean condition() {
                return new DefaultSSOValidator().isEnabled();
            }
            @Override
            public boolean command() throws ServletException, IOException {
                /* if validation is successful, consider the handler 'not handled' to allow
                 * for other handlers to process the request.
                 */
                boolean handled = false;
                
                DefaultSSOValidatorInput input = new DefaultSSOValidatorInput();
                input.setRequest(httpRequest);

                DefaultSSOValidatorResult validatorResult = new DefaultSSOValidator().validate(input);
                
                if (validatorResult.isInvalidated() && isNotLogoutPage()) {
                    // if invalidated can't proceed.
                    if (log.isErrorEnabled()) {
                        log.error("The SSO validation rule has invalidated access: [" + validatorResult.getValidationMessage() + "]  Logging out: " + getPrincipal());
                    }
                    invalidate();
                    response.sendRedirect(request.getContextPath() + logoutUrl);

                    handled = true;
                }
                return handled;
            }
        };
    }
    /**
     * Will take care of handling rule based Authentication as well as SAML SSO's as long as the user is not already
     * logged in.
     */
    private CommandHandler createSSOHandler() {
        return new CommandHandler() {
            @Override
            public String getName() {
                return "SSO";
            }
            
            /**
             * Execute this handler when the user's not logged in and is not trying to logout.
             */
            @Override
            public boolean condition() {
                return getPrincipal() == null && isNotLogoutPage();
            }

            @Override
            public boolean command() throws ServletException, IOException {
                boolean handled = false;
                if (isForceLoginPage()) {
                    return handled; //false
                }

                SSOAuthenticationRunner authRunner = SailPointInjection.injector().getInstance(SSOAuthenticationRunner.class);
                SSOAuthenticationInput input = new SSOAuthenticationInput();
                input.setRequest(httpRequest);
                input.setLoginUrl(loginUrl);
                input.setMobileLoginUrl(mobileLoginUrl);
                input.setDefaultLandingUrl(defaultLandingUrl);
                input.setDefaultMobileLandingUrl(defaultMobileLandingUrl);

                SSOAuthenticationResult authResult = authRunner.run(input);

                try {
                    SailPointContext context = SailPointFactory.getCurrentContext();
                    LoginService loginService = new LoginService(context);
                    Identity identity = authResult.getIdentity();
                    List<String> mfaWorkflowNames = loginService.getApplicableMFAWorkflowNames(identity);
                    if (Util.isEmpty(mfaWorkflowNames)) {
                        // no MFA workflows handle request normally
                        handled = postProcessSSOSingleFactorAuth(getChain(), authResult, handled);
                    } else {
                        handled = postProcessSSOMultiFactorAuth(loginService, mfaWorkflowNames, identity, authResult);
                    }
                } catch (GeneralException e) {
                    throw new ServletException(e);
                }

                return handled;
            }
        };
    }

    protected boolean postProcessSSOMultiFactorAuth(LoginService loginService, List<String> mfaWorkflowNames,
                                                    Identity identity, SSOAuthenticationResult authResult) throws IOException {
        String hash = (String) storeFactory.getStore().get(RedirectService.HASH_TOKEN);
        loginService.setRedirector(redirector);
        loginService.updateStore(mfaWorkflowNames, identity.getName(), storeFactory.getStore(), true);
        writeSsoAuth(authResult, storeFactory.getStore());
        try {
            // check if user needs to answer authentication questions
            if (LoginBean.needToAnswerAuthQuestion(SailPointFactory.getCurrentContext(), identity)) {
                authResult.setRedirectURL(request.getContextPath() + "/authAnswers.jsf");
            } else {
                // check if the loginReturnsToDashboard flag is set to true
                Configuration systemConfig = Configuration.getSystemConfig();
                if (systemConfig != null && systemConfig.getBoolean(Configuration.LOGIN_RETURNS_TO_DASHBOARD)) {
                    authResult.setRedirectURL(this.getRedirectHomeURL(authResult.getRedirectURL()));
                }
            }
        } catch (GeneralException e) {
            if (log.isErrorEnabled()) {
                log.error(e.getMessage(), e);
            }
        }
        StringBuilder postMfaRedirectUrl = new StringBuilder(authResult.getRedirectURL());

        if(!Util.isNullOrEmpty(hash)) {
            postMfaRedirectUrl.append("#");
            postMfaRedirectUrl.append(hash);
        }
        storeFactory.getStore().put(ATT_POST_MFA_REDIRECT, postMfaRedirectUrl.toString());
        return true;
    }

    /**
     * Executed when it it timeout url
     */
    private CommandHandler createTimeoutHandler() {
        return new CommandHandler() {
            @Override
            public String getName() {
                return "Timeout";
            }

            @Override
            public boolean condition() {
                return (url.indexOf(timeoutUrl) >= 0);
            }

            @Override
            public boolean command() throws IOException {
                invalidate();
                if (isMobilePath(url)) {
                    response.sendRedirect(request.getContextPath() + mobileLoginUrl);
                } else {
                    response.sendRedirect(request.getContextPath() + loginUrl);
                }
                return true;
            }
        };
    }

    /**
     * Executed when it is logout url
     */
    private CommandHandler createLogoutHandler() {
        return new CommandHandler() {
            @Override
            public String getName() {
                return "Logout";
            }
            
            @Override
            public boolean condition() {
                return (url.indexOf(logoutUrl) >= 0);
            }

            @Override
            public boolean command() throws IOException {
                invalidate();
                // we already are trying to get to the logout page don't redirect again
                return true;
            }
        };
    }

    /**
     * Executed when it is login url
     */
    private CommandHandler createLoginHandler() {
        return new CommandHandler() {
            @Override
            public String getName() {
                return "Login";
            }

            @Override
            public boolean condition() {
                return (url.indexOf(loginUrl) >= 0);
            }

            @Override
            public boolean command() throws ServletException, IOException {
                // Check if the user is logged in via SSO and redirect to the default landing url
                if (getPrincipal() != null &&
                        storeFactory.getStore().get(ATT_SSO_AUTH) != null &&
                        storeFactory.getStore().get(ATT_SSO_AUTH).equals(TRUE)) {

                    String path = defaultLandingUrl;
                    if (url.indexOf(MOBILE_PATH) >= 0) {
                        path = defaultMobileLandingUrl;
                    }
                    response.sendRedirect(request.getContextPath() + path);
                    return true;

                }
                
                // always let this proceed
                chain.doFilter();
                return true;
            }
        };
    }

    /**
     * Executed when it is externalPages url. 
     * {@link PageAuthenticationFilter#externalPagesPath} is set from the config.
     */
    private CommandHandler createExternalPagesHandler() {
        return new CommandHandler() {
            @Override
            public String getName() {
                return "ExternalPages";
            }

            @Override
            public boolean condition() {
                return isExternalPage();
            }

            @Override
            public boolean command() throws ServletException, IOException {
                // always let this proceed
                chain.doFilter();
                return true;
            }
        };
    }

    /**
     * Executed when the legacy forgot password URL is accessed.  Redirects to the new
     * desktopReset servlet.
     */
    private CommandHandler createLegacyForgotPasswordHandler() {
        return new CommandHandler() {
            @Override
            public String getName() {
                return "LegacyForgotPassword";
            }

            @Override
            public boolean condition() {
                return (url.indexOf(forgotPasswordUrl) >= 0);
            }

            @Override
            public boolean command() throws ServletException, IOException {
                invalidate();
                response.sendRedirect(request.getContextPath() + DESKTOP_RESET_SERVLET);
                return true;
            }
        };
    }

    /**
     * Executed when it is expired url or reset password url
     */
    private CommandHandler createExpiredOrResetPasswordsHandler() {
        return new CommandHandler() {
            @Override
            public String getName() {
                return "ExpiredOrResetPassword";
            }

            @Override
            public boolean condition() {
                return (url.indexOf(expiredUrl) >= 0 || url.indexOf(resetPasswordUrl) >= 0);
            }

            @Override
            public boolean command() throws ServletException, IOException {
                // always let this proceed
                String referer = request.getHeader("referer");

                String expiredAccountId = (String) storeFactory.getStore().get(LoginBean.ATT_EXPIRED_ACCOUNT_ID);
                if (expiredAccountId != null)
                    chain.doFilter();
                else if (referer != null && (referer.indexOf(expiredUrl) >= 0 || referer.indexOf(resetPasswordUrl) >= 0)){
                    chain.doFilter();
                }
                else {
                    redirector.redirect(ATT_PRE_LOGIN_URL, loginUrl, null, null);
                }

                return true;
            }
        };
    }

    /**
     * Executed url is desktop reset success
     */
    private CommandHandler createDesktopResetHandler() {
        return new CommandHandler() {
            @Override
            public String getName() {
                return "DesktopReset";
            }

            @Override
            public boolean condition() {
                return (url.indexOf(desktopResetSuccessUrl) >= 0);
            }

            @Override
            public boolean command() throws ServletException, IOException {
                // always let this proceed
                chain.doFilter();
                return true;
            }
        };
    }

    /**
     * Executed url is end user load failure
     */
    private CommandHandler createEndUserLoadFailureHandler() {
        return new CommandHandler() {
            @Override
            public String getName() {
                return "EndUserLoadFailure";
            }

            @Override
            public boolean condition() {
                return (url.indexOf(endUserLoadFailureUrl) >= 0);
            }

            @Override
            public boolean command() throws ServletException, IOException {
                // always let this proceed
                chain.doFilter();
                return true;
            }
        };
    }

    /**
     * Executed url matches error pattern etc.
     */
    private CommandHandler createErrorHandler() {
        return new CommandHandler() {
            @Override
            public String getName() {
                return "Error";
            }

            @Override
            public boolean condition() {
                return ( url.indexOf("/error") >= 0 || url.indexOf("/exception.jsf") >= 0 || url.indexOf("/ui/500.jsf") >= 0 );
            }

            @Override
            public boolean command() throws ServletException, IOException {
                // always let this proceed
                chain.doFilter();
                return true;
            }
        };
    }

    /**
     * Executed when faces resource is requested
     */
    private CommandHandler createFacesResourceHandler() {
        return new CommandHandler() {
            @Override
            public String getName() {
                return "FacesResource";
            }

            @Override
            public boolean condition() {
                return (url.indexOf("javax.faces.resource") >= 0);
            }

            @Override
            public boolean command() throws ServletException, IOException {
                // always let this proceed
                chain.doFilter();
                return true;
            }
        };
    }

    /**
     * Executed if "/user/" url pattern is requested and unauthenticated end user pages is allowed
     */
    private CommandHandler createUnauthenticatedEndUserPagesHandler() {
        return new CommandHandler() {
            @Override
            public String getName() {
                return "UnauthenticatedEndUserPages";
            }

            @Override
            public boolean condition() throws ServletException {
                return((url.indexOf("/user/") >= 0) && allowUnauthenticatedEndUserPages());
            }

            /**
             * Return whether unauthenticated access to the end user pages is allowed.
             */
            private boolean allowUnauthenticatedEndUserPages() throws ServletException {

                if (null == allowUnauthenticatedEndUserPages) {
                    try {
                        SailPointContext ctx = SailPointFactory.getCurrentContext();
                        Configuration config = ctx.getConfiguration();
                        if (null != config) {
                            allowUnauthenticatedEndUserPages = config.getBoolean(Configuration.ALLOW_UNAUTHENTICATED_END_USER_PAGES, false);
                        }
                    }
                    catch (GeneralException e) {
                        throw new ServletException(e);
                    }
                }

                return allowUnauthenticatedEndUserPages;
            }

            // If this is an end user page and we're configured to unauthenticated
            // end user page access, try to initialize the principal from the request
            // if there isn't already one.
            @Override
            public boolean command() throws ServletException, IOException {
                try {

                    //Check if this is a different person/request and if so, start over.
                    if (storeFactory.getStore().get(ATT_USER_PAGE_AUTH) != null) {
                        Identity requestIdentity = getIdentityFromRequest(request);
                        if (requestIdentity != null && !getPrincipal().equals(requestIdentity.getName())) {
                            clearPrincipalAndStuff();
                            setPrincipal(null);
                        }
                    }

                    boolean principalSet = (getPrincipal() != null);
                    if (!initializePrincipalFromRequest(request)) {
                        response.sendRedirect(request.getContextPath() + endUserLoadFailureUrl);
                    }
                    else {
                        chain.doFilter();
                        if (!principalSet) {
                            //we got this from the request, so set it as single use
                            storeFactory.getStore().put(ATT_USER_PAGE_AUTH, "true");
                        }
                    }
                }
                catch (GeneralException e) {
                    throw new ServletException(e);
                }
                return true;
            }

            /**
             * If this is a request for an end user page and there is not yet a user in
             * the session, try to pull the ID off of the request and load the WorkItem
             * owner into the session.  Return true if the user was already logged in or
             * we were able to pull the owner out of the WorkItem.
             */
            private boolean initializePrincipalFromRequest(PageAuthRequest request)
                throws GeneralException {

                boolean foundUser = false;

                // If the principal is not in the session, try to bootstrap him from
                // the request.
                if (new LoginService(SailPointFactory.getCurrentContext()).isLoggedIn(storeFactory.getStore())) {
                    foundUser = true;
                }
                else {
                    Identity owner = getIdentityFromRequest(request);
                    if (null != owner) {
                        storeFactory.getStore().writeIdentity(owner);
                        foundUser = true;
                    }
                }

                return foundUser;
            }

            /**
             * Attempt to find identity of work item indicated by request parameter, if specified
             */
            private Identity getIdentityFromRequest(PageAuthRequest request) throws GeneralException {
                Identity identity = null;
                String id = request.getParameter("id");
                if (null != id) {
                    SailPointContext ctx = SailPointFactory.getCurrentContext();
                    WorkItem workItem = ctx.getObjectById(WorkItem.class, id);
                    if (null != workItem) {
                        identity = workItem.getOwner();
                    }
                }

                return identity;
            }
        };
    }

    /**
     * Executed when user page auth is enabled in the session.
     *
     */
    private CommandHandler createUserPageAuthHandler() {
        return new CommandHandler() {
            @Override
            public String getName() {
                return "UserPageAuth";
            }

            @Override
            public boolean condition() throws ServletException {
                return (storeFactory.getStore().get(ATT_USER_PAGE_AUTH) != null && userPageResourceUrls.contains(relativeUrl));
            }

            @Override
            public boolean command() throws ServletException, IOException {
                chain.doFilter();
                return true;
            }
        };
    }

    /**
     * Executed when login url contains login.jsf?prompt=true.
     *
     */
    private CommandHandler createSSOBypassHandler() {
        return new CommandHandler() {
            @Override
            public String getName() {
                return "SSOBypass";
            }

            @Override
            public boolean condition() throws ServletException {
                // Two basic conditions: if the target URL is the login prompt and (
                // prompt=true
                // or we came from login prompt)
                String referer = request.getHeader("referer");
                return (relativeUrl.indexOf(loginUrl) >=0 || relativeUrl.indexOf(mobileLoginUrl) >= 0) &&
                          ((referer != null && (referer.indexOf(loginUrl) >= 0 || referer.indexOf(mobileLoginUrl) >= 0)) ||
                          (request.getParameter(SSO_BYPASS_PROMPT) != null && (request.getParameter(SSO_BYPASS_PROMPT)).equalsIgnoreCase(TRUE)));
            }

            @Override
            public boolean command() throws ServletException, IOException {
                // First, invalidate the session auth attributes, as if they had logged out
                clearPrincipalAndStuff();
                // Second, avoid SSO Authentication for the lifetime of the session
                storeFactory.getStore().put(ATT_SSO_DISABLED, TRUE);
                //let this one proceed
                chain.doFilter();
                return true;
            }
        };
    }
    
    /* performs post processing after SSO authentication. This is the "original" code prior to implementing multi factor authentication. */
    boolean postProcessSSOSingleFactorAuth(Chainable chain, SSOAuthenticationResult authResult, boolean handled) 
            throws IOException, ServletException {
        boolean resultIsHandled = handled;

        Identity user = authResult.getIdentity();
        try {
            // successful login
            if (user != null) {
                // check if user needs to answer authentication questions
                if (LoginBean.needToAnswerAuthQuestion(SailPointFactory.getCurrentContext(), user)) {
                    authResult.setNeedsRedirectUrl(true);
                    authResult.setRedirectURL(request.getContextPath() + "/authAnswers.jsf");
                } else {
                    // check if the loginReturnsToDashboard flag is set to true
                    Configuration systemConfig = Configuration.getSystemConfig();
                    if (systemConfig != null && systemConfig.getBoolean(Configuration.LOGIN_RETURNS_TO_DASHBOARD)) {
                        authResult.setNeedsRedirectUrl(true);
                        authResult.setRedirectURL(this.getRedirectHomeURL(authResult.getRedirectURL()));
                    }
                }
            }
        } catch (GeneralException e) {
            if (log.isErrorEnabled()) {
                log.error(e.getMessage(), e);
            }
        }

        //Add filterParams to handleFinallyFilterParams in case we make it to the final handler
        if(!Util.isEmpty(authResult.getParamsToFilter())) {
            handleFinallyFilterParams = authResult.getParamsToFilter();
        }

        if (authResult.isNeedsRedirect() && authResult.getRedirectURL() != null) {
            // if SAML SSO is configured, first time visiting iiq, redirect URL is the IdP site
            // for Rule based SSO, login successful, if unanswered auth questions will redirect to auth questions page 
            // or else homepage
            if (authResult.isSuccess() && authResult.containsResult()) {
                Map<String,Object> linkAttrs = LoginService.getLinkSessionAttributes(storeFactory.getStore());
                // #IIQCB-2560 - reset the session as we are about to login, do this before we set session attributes in setUpSSOTimeZone
                InvalidatableStorage newStorage = storeFactory.renew();
                LoginService.writeAncillarySession(newStorage, linkAttrs);
                // isSuccess() is true because we successfully handled processing the phase 1 SAML request.
                // if isSuccess AND containsResult, we successfully handled a phase 2 SAML request.
                writeSsoAuth(authResult, newStorage);
                setUpSSOTimeZone(authResult, newStorage);
                Identity loggedInUser = authResult.getIdentity();
                Link authLink = authResult.getLink();
                if(loggedInUser != null) {
                    LoginService.writeIdentitySession(newStorage, loggedInUser, authLink);
                }
            } else {
                // redirect to the IdP on phase 1 SAML requests
                httpResponse.sendRedirect(authResult.getRedirectURL());
            }

            resultIsHandled = true;
        } else if (authResult.isSuccess()) {
            Identity loggedInUser = authResult.getIdentity();
            Link authLink = authResult.getLink();
            InvalidatableStorage tempStorage = storeFactory.getStore();
            if(loggedInUser != null) {
                Map<String,Object> linkAttrs = LoginService.getLinkSessionAttributes(storeFactory.getStore());
                // #IIQCB-2560 - reset the session as we are about to login
                InvalidatableStorage newStorage = storeFactory.renew();
                LoginService.writeAncillarySession(newStorage, linkAttrs);
                writeSsoAuth(authResult, newStorage);
                LoginService.writeIdentitySession(newStorage, loggedInUser, authLink);
            }
            // if we did not redirect, then run through the filter chain
            if (!setUpSSOTimeZone(authResult, tempStorage)) {
                chain.doFilter();
            }

            resultIsHandled = true;
        }
        return resultIsHandled;
    }

    /**
     *  If we come in on through the mobile login or a direct mobile
     * link we are considering this a mobile session until we visit
     * the core desktop login page again. */
    private void doMobileLoginStuff() {
        InvalidatableStorage store = storeFactory.getStore();
        if (url.contains(loginUrl)) {
            store.put(ATT_MOBILE_LOGIN, false);
        }
        if (isMobilePath(url)) {
            store.put(ATT_MOBILE_LOGIN, true);
        }
        else {
            store.put(ATT_MOBILE_LOGIN, false);
        }
        if (store.get(ATT_MOBILE_LOGIN) != null) {
            isMobile = (Boolean)store.get(ATT_MOBILE_LOGIN);
        }
    }

    /**
     * Tests whether the url is for an external page.
     *
     * @return True if external page, false otherwise.
     */
    private boolean isExternalPage() {
        return relativeUrl.startsWith(externalPagesPath) || relativeUrl.startsWith(mobileExternalPagesPath) ||
               relativeUrl.startsWith(externalRestPath);
    }
    
    /**
     * Determine whether or not the url is considered mobile. As we integrate more mobile pages into the desktop ui
     * we'll run into the problem where we incorrectly determine a page to be mobile.
     *
     * @param url
     * @return whether or not url is considered mobile
     */
    private boolean isMobilePath(String url) {
        boolean isMobilePath = false;

        if (Util.isNullOrEmpty(url)) {
            return false;
        }

        for (String exclude: MOBILE_PATH_EXCLUDE_LIST) {
            if (url.contains(exclude)) {
                // exclude from isMobile
                return false;
            }
        }

        if (url.contains(MOBILE_PATH)) {
            isMobilePath = true;
        }

        return isMobilePath;
    }
    
    /**
     * Tests if the user requested forcing the login page, ?prompt=true
     */
    private boolean isForceLoginPage() {
        return TRUE.equals(storeFactory.getStore().get(ATT_SSO_DISABLED));
    }
    
    /**
     * Is the url not the logout page?
     */
    private boolean isNotLogoutPage() {
        return url.indexOf(logoutUrl) < 0;
    }
    
    /**
     * Invalidate the session storage.
     */
    private void invalidate() {
        if (log.isDebugEnabled()) {
            log.debug("invalidating session for id: " + storeFactory.getStore().getId());
        }
        clearPrincipalAndStuff();
        storeFactory.renew();
    }

    /**
     * Get the redirect url for desktop / mobile login
     */
    private String getRedirectHomeURL(String redirectUrl) {
        if (this.isMobilePath(redirectUrl)) {
            return request.getContextPath() + mobileLoginUrl;
        }

        return request.getContextPath() + "/home.jsf";
    }
}
