/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * Filter installed to check for an authenticated SailPoint server session,
 * redirecting to the login page if not found.
 */

package sailpoint.web;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.Identity;
import sailpoint.rest.HttpSessionStorage;
import sailpoint.server.Auditor;
import sailpoint.service.LoginService;
import sailpoint.service.PageAuthenticationService;
import sailpoint.service.PageAuthenticationService.Chainable;
import sailpoint.service.PageAuthenticationService.ConfigStorage;
import sailpoint.service.PageAuthenticationService.InvalidatableStorage;
import sailpoint.service.PageAuthenticationService.InvalidatableStorageFactory;
import sailpoint.service.PageAuthenticationService.PageAuthRequest;
import sailpoint.service.PageAuthenticationService.PageAuthResponse;
import sailpoint.service.RedirectService;
import sailpoint.service.Redirectable;
import sailpoint.service.RequestAccessService;
import sailpoint.tools.Base64;
import sailpoint.tools.Util;
import sailpoint.web.sso.RedirectUtil;

/**
 * Filter installed to check for an authenticated SailPoint server session,
 * redirecting to the login page if not found.
 */
public class PageAuthenticationFilter implements Filter
{
    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    private static Log log = LogFactory.getLog(PageAuthenticationFilter.class);

    /**
     * Name of the HttpSession attribute where the principal name is storeed.
     */
    public static final String ATT_PRINCIPAL = "principal";

    /**
     * Name of the HttpSession attribute where the principal's capability
     * list is stored.
     */
    public static final String ATT_CAPABILITIES = "principalCapabilities";

    /**
     * Name of the HttpSession attribute where the principal's rights are
     * stored.
     */
    public static final String ATT_RIGHTS = "principalRights";

    /**
     * Identities properties used for authentication. Currently we only
     * use this for building menus in cases in which some properties other
     * than capabilities grant access to a page.
     */
    public static final String ATT_ID_AUTH_ATTRS = "principalAuthAttributes";

    /**
     * Parameter that is included on the url when requests are made from
     * an external management system like BMC SRM or ServiceNow launcher.
     *
     * The idea here is to allow these systems to pass the login token for
     * a Identity. When this request parameter passed to the our app, then
     * IdentityIQ allows login session to identity for which token is passed.
     */
    public static final String ATT_REMOTE_LOGIN = "remoteLogin";

    private ConfigStorage configStore;

    //////////////////////////////////////////////////////////////////////
    //
    // Methods
    //
    //////////////////////////////////////////////////////////////////////

    public PageAuthenticationFilter() {
    }

    /**
     * Extract the <init-param>s.
     */
    @Override
    public void init(FilterConfig config) throws ServletException {
        configStore = new FilterConfigStorage(config);
    }

    /**
     * Called by the container when the filter is destroyed.
     */
    @Override
    public void destroy() { }

    /**
     * Check to see if there is a principal name registered for this
     * HTTP session, if not redirect to the login page.
     */
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
        throws IOException, ServletException {

        // capture the client host for auditing
        // Getting the host name may requre a DNS lookup, should we skip that
        // and just always use the IP address?  Docs say the implementation
        // may choose to return an IP address here for performance reasons.
        String client = request.getRemoteHost();
        if (client == null)
            client = request.getRemoteAddr();
        Auditor.setClientHost(client);

        MyHandler handler = new MyHandler((HttpServletRequest)request, (HttpServletResponse)response, chain);
        handler.handle();
    }
    
    private class AuthRequest implements PageAuthRequest {
        HttpServletRequest request;
        private AuthRequest(HttpServletRequest request) {
            this.request = request;
        }
        @Override
        public String getHeader(String name) {
            return request.getHeader(name);
        }
        @Override
        public String getParameter(String name) {
            return request.getParameter(name);
        }
        @Override
        public String getRequestURI() {
            return request.getRequestURI();
        }
        @Override
        public String getContextPath() {
            return request.getContextPath();
        }
        
    }
    
    private class AuthResponse implements PageAuthResponse {
        HttpServletResponse response;
        private AuthResponse(HttpServletResponse response) {
            this.response = response;
        }
        @Override
        public void sendRedirect(String redir) throws IOException {
            response.sendRedirect(redir);
        }
        
    }

    private class FilterConfigStorage implements ConfigStorage {
        private FilterConfig config;
        
        FilterConfigStorage(FilterConfig config) {
            this.config = config;
        }

        @Override
        public String get(String key) {
            return config.getInitParameter(key);
        }
        
    }
    
    private class InvalidatableSessionStorage extends HttpSessionStorage implements InvalidatableStorage {
        InvalidatableSessionStorage(HttpSession httpSession) {
            super(httpSession);
        }

        @Override
        public void invalidate() {
            if(getHttpSession() != null) {
                getHttpSession().invalidate();
            }
        }

        @Override
        public String getId() {
            return (getHttpSession() != null) ? getHttpSession().getId() : null;
        }

        @Override
        public void writeIdentity(Identity identity) {
            LoginService.writeIdentitySession(this, identity);
        }
    }
    
    /* 
     * Implementation meant to encapsulate logic around getting a new SessionStore. The problem
     * with a pointer to httpSession is once invalidated, any get/set to httpSession will throw
     * exceptions. Use this implementation to hide the details of getting a new session after
     * the current has been invalidated.
     */
    private class InvalidatableSessionStorageFactory implements InvalidatableStorageFactory {
        private HttpServletRequest request;
        private InvalidatableStorage store;
        
        InvalidatableSessionStorageFactory(HttpServletRequest request) {
            this.request = request;
        }
        
        @Override
        public InvalidatableStorage renew() {
            HttpSession session = this.request.getSession(false);
            String hash = null;
            if (session != null) {
                //preserve redirect hash if present;
                hash = (String) session.getAttribute(RedirectService.HASH_TOKEN);
                session.invalidate();
            }
            setStore(true);
            getStore().put(RedirectService.HASH_TOKEN, hash);
            return getStore();
        }
        
        @Override
        public InvalidatableStorage getStore() {
            if (this.store == null) {
                // return existing session
                setStore(false);
            }
            return this.store;
        }
        
        void setStore(boolean create) {
            this.store = new InvalidatableSessionStorage(this.request.getSession(create));
        }
    }
    
    private class Chainlink implements Chainable {
        private FilterChain chain;
        private HttpServletRequest request; 
        private HttpServletResponse response;
        private Chainlink(FilterChain chain, HttpServletRequest request, HttpServletResponse response) {
            this.chain = chain;
            this.request = request;
            this.response = response;
        }

        @Override
        public void doFilter() throws ServletException, IOException {
            chain.doFilter(request, response);
        }
        
    }
    
    /**
     * We need to instantiate a new class because instance variables inside a filter can't be used between requests.
     *
     * Inner class that handles Command Handling logic.
     * This class replaces a giant if then else logic.
     * @see {@link #handleCommands()} method.
     *
     * @author tapash.majumder
     *
     */
    private class MyHandler {

        /**
         * These instance variables are set every time {@link doFilter} method is called.
         */
        private HttpServletRequest httpRequest;
        private HttpServletResponse httpResponse;
        private FilterChain chain;
        private PageAuthenticationService service;
        private InvalidatableSessionStorageFactory storeFactory;

        public MyHandler(HttpServletRequest request, HttpServletResponse response, FilterChain chain) {
            this.httpRequest = request;
            this.httpResponse = response;
            this.chain = chain;
            init();
        }

        /**
         * It will run through a list of {@link PageAuthenticationService#handleCommands()} until a suitable one is found.
         */
        public void handle() throws ServletException, IOException {

            handleExternalContextParameters();

            checkReservedUrl();

            if (!service.handleCommands()) {
                service.handleFinally();
            }
        }

        /**
         * This will set instance variables for this class depending on the ServletRequest and ServletResponse.
         * Instead of setting local variables we will use instance variables so that they don't have to be passed
         * between methods.
         */
        private void init() {
            // don't create one unless we have to
            this.storeFactory = new InvalidatableSessionStorageFactory(this.httpRequest);
            // create service after httpSession init
            service = new PageAuthenticationService(configStore, this.storeFactory);
            // like to refactor these out as well, but SSOAuthenticator needs them
            service.setHttpRequest(httpRequest);
            service.setHttpResponse(httpResponse);
            
            service.setRequest(new AuthRequest(httpRequest));
            service.setResponse(new AuthResponse(httpResponse));
            service.setChain(new Chainlink(chain, httpRequest, httpResponse));
            service.setRedirector(new Redirector());
        }
        
        /**
         * Some of the "launch context" comes over the wire in the url when IdentitIQ page
         * launched from one of our request system integrations like BMC SRM or ServiceNow launcher.
         *
         * If request contains remoteLogin parameter, then clear the old session attribute's and 'principle',
         * so that going ahead remote login token will be considered for authentication and accordingly session
         * object and 'principal' gets updated as per login token.
         * Previously IdentitIQ gives priority to old session and and it dosen't considers remote login token.
         * Now IdentityIQ prefers remote login token over an already authenticated session.
         */
        private void handleExternalContextParameters() {
            if ( null != httpRequest.getParameter(ATT_REMOTE_LOGIN) ) {
                // clear old session attributes
                service.clearPrincipalAndStuff();

                // set principle to null
                service.setPrincipal(null);
            }

            // djs : these are encoded because they are on the url
            // from SRM and ServiceNow launchers
            promoteRequestParamToSessionParam("tapp", RequestAccessService.ATT_TICKET_MANAGEMENT_APPLICATION);
            promoteRequestParamToSessionParam("esrc", RequestAccessService.ATT_EXTERNAL_SOURCE);
        }

        private void promoteRequestParamToSessionParam(String requestParamName, String sessionParamName) {
            String requestValue =  httpRequest.getParameter(requestParamName);
            if ( requestValue != null ) {
                byte[] requestValueBytes = Base64.decode(requestValue);
                if ( requestValueBytes != null ) {
                    storeFactory.getStore().put(sessionParamName, new String(requestValueBytes));
                }
            }
        }
        
        //If we are going from a user page to one of the reserved pages (login, logout, etc),
        //clear session and start over
        private void checkReservedUrl() {
            if (storeFactory.getStore().get(PageAuthenticationService.ATT_USER_PAGE_AUTH) != null && service.isReservedUrl()) {
                service.clearPrincipalAndStuff();
                service.setPrincipal(null);
            }
        }

        /**
         * This class encapsulates the
         * redirection logic.
         *
         * It will set all the request parameters in a sessionkey
         * Some request parameters may be filtered if {@link #paramsToFilter} is set
         *
         * @author tapash.majumder
         *
     */
        private class Redirector implements Redirectable {

            @Override
            // preRedirectUrl is set if we are not trying to go to the httpRequestUrl, but instead a redirectURL from SSO redirectURL
            public void redirect(String sessionKey, String redirectUrl, List<String> paramsToFilter, String preRedirectUrl) throws IOException {

                String hash = (String) storeFactory.getStore().get(RedirectService.HASH_TOKEN);

                Map<String, String[]> paramMap = getParamMap(paramsToFilter);

                if (!Util.isEmpty(paramMap)) {
                    // Bug 20527: if we have a deepLink, use that for the preRedirectUrl
                    if (paramMap.get("sessionTimeoutForm:preRedirectUrlHash") != null) {
                        if (preRedirectUrl == null) {
                            preRedirectUrl = httpRequest.getRequestURL() + paramMap.get("sessionTimeoutForm:preRedirectUrlHash")[0];
                        }
                    }
                }

                if(Util.isNotNullOrEmpty(hash)) {
                    storeFactory.getStore().remove(RedirectService.HASH_TOKEN);
                    if(Util.isNotNullOrEmpty(preRedirectUrl)) {
                        preRedirectUrl = RedirectUtil.formatUrlString(preRedirectUrl, paramMap, hash);
                    }
                    else {
                        preRedirectUrl = RedirectUtil.formatUrlString(httpRequest.getRequestURL().toString(), paramMap, hash);
                    }
                    if(sessionKey != null) {
                        storeFactory.getStore().put(sessionKey, preRedirectUrl);
                    }
                }
                else {
                    if(sessionKey != null) {
                        storeFactory.getStore().put(sessionKey, (Util.isNullOrEmpty(preRedirectUrl) ?
                                httpRequest.getRequestURL() + RedirectUtil.calculateParamString(paramMap) :
                                preRedirectUrl));
                    }
                }

                httpResponse.sendRedirect(httpRequest.getContextPath() + redirectUrl);
            }

            private Map<String, String[]> getParamMap(List<String> paramsToFilter) {
                if (Util.isEmpty(paramsToFilter)) {
                    return httpRequest.getParameterMap();
                }
                
                return filterParamMap(httpRequest.getParameterMap(), paramsToFilter);
            }

            private Map<String, String[]> filterParamMap(Map<String, String[]> params, List<String> paramsToFilter) {
                Map<String, String[]> filteredParams = new HashMap<>();
                if (params != null) {
                    for(String s : params.keySet()) {
                        if(!paramsToFilter.contains(s)) {
                            filteredParams.put(s, params.get(s));
                        }
                    }
                }
                return filteredParams;
            }
        }
    }

    /**
     * Return whether the user logged in using the mobile login page according
     * to the given session map.
     *
     * @param  sessionMap  A map containing the HttpSession attributes.
     */
    public static boolean isMobileLogin(Map<String,Object> sessionMap) {
        return Util.otob(sessionMap.get(PageAuthenticationService.ATT_MOBILE_LOGIN));
    }
}
