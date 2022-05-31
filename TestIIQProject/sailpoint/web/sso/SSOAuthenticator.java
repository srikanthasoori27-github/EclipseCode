package sailpoint.web.sso;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;

import sailpoint.object.Identity;
import sailpoint.object.Link;

/**
 * An implementation of this interface
 * should return a valid identity or link object when SSO
 * authentication is successful.
 * 
 * IMPORTANT: An implementation of this class should be allowed to instantiated via
 * reflection so it *must* have a no-args constructor.
 * 
 * @author tapash.majumder
 *
 */
public interface SSOAuthenticator {

    /**
     * Encapsulates the input.
     * This will enable us to add more params if necessary
     * 
     */
    public class SSOAuthenticationInput {
        
        private HttpServletRequest request;
        
        /**
         * Values used to redirect to a default landing page in the case a user requests
         * root or login.jsf
         */
        private String loginUrl;
        private String mobileLoginUrl;
        private String defaultLandingUrl;
        private String defaultMobileLandingUrl;

        /**
         * The http request
         */
        public HttpServletRequest getRequest() {
            return request;
        }

        public void setRequest(HttpServletRequest request) {
            this.request = request;
        }

        public String getLoginUrl() {
            return loginUrl;
        }

        public void setLoginUrl(String loginUrl) {
            this.loginUrl = loginUrl;
        }

        public String getMobileLoginUrl() {
            return mobileLoginUrl;
        }

        public void setMobileLoginUrl(String mobileLoginUrl) {
            this.mobileLoginUrl = mobileLoginUrl;
        }

        public String getDefaultLandingUrl() {
            return defaultLandingUrl;
        }

        public void setDefaultLandingUrl(String defaultLandingUrl) {
            this.defaultLandingUrl = defaultLandingUrl;
        }

        public String getDefaultMobileLandingUrl() {
            return defaultMobileLandingUrl;
        }

        public void setDefaultMobileLandingUrl(String defaultMobileLandingUrl) {
            this.defaultMobileLandingUrl = defaultMobileLandingUrl;
        }
    }
    
    /**
     * Encapsulates the output.
     * if isSuccess is true then an identity or
     * link must be returned.
     *
     */
    public class SSOAuthenticationResult {
        private boolean success;
        
        private boolean needsRedirect;
        private String redirectURL;
        private Identity identity;
        private Link link;
        private List<String> paramsToFilter = new ArrayList<>();
        private String authenticator;
        

        /**
         * Whether authentication was successful or whether the phase of authentication
         * has been successfully handled.
         */
        public boolean isSuccess() {
            return success;
        }
        
        public void setSuccess(boolean success) {
            this.success = success;
        }
        
        public boolean containsResult() {
            return getIdentity() != null || getLink() != null;
        }
        
        /**
         * Instead of setting {@link #redirectURL} to null/not null
         * we should explicit ask for this before redirecting.
         */
        public boolean isNeedsRedirect() {
            return needsRedirect;
        }
        
        public void setNeedsRedirectUrl(boolean val) {
            needsRedirect = val;
        }

        /**
         * Some SSO implementations require a two part authentication.  In the case
         * of SAML the first phase simply receives a request and redirects to the 
         * Identity Provider SSO URL.  This property is used so the Command Handler
         * in PageAuthenticationFilter can use the httpResponse object to redirect to
         * the appropriate URL.
         * @return the URL to issue a redirect to
         */
        public String getRedirectURL() {
            return redirectURL;
        }

        public void setRedirectURL(String redirectURL) {
            this.redirectURL = redirectURL;
        }

        /**
         * The identity that was authenticated.
         */
        public Identity getIdentity() {
            // IIQETN-6725 - SAML Correlation rule needs to support returning Identity or Link
            // Defer to an Identity first and inspect the Link if one isn't found
            if (identity == null && link != null) {
                return link.getIdentity();
            }
            
            return identity;
        }
        
        public void setIdentity(Identity identity) {
            this.identity = identity;
        }

        /**
         * The link that was invalidated or authenticated.
         */
        public Link getLink() {
            return link;
        }
        
        public void setLink(Link link) {
            this.link = link;
        }

        /**
         * A list of request parameters which need to be filtered out
         * when the caller redirects.
         */
        public List<String> getParamsToFilter() {
            return paramsToFilter;
        }
        
        public void setParamsToFilter(List<String> val) {
            paramsToFilter = val;
        }
        
        public void addParamToFilter(String param) {
            paramsToFilter.add(param);
        }

        /**
         * Name of the SSOAuthenticator class that successfully authenticated
         */
        public String getAuthenticator() {
            return authenticator;
        }

        public void setAuthenticator(String authenticator) {
            this.authenticator = authenticator;
        }
    }
    
    /**
     * Does the actual authentication of the HttpRequest.
     */
    SSOAuthenticationResult authenticate(SSOAuthenticationInput input) throws ServletException, IOException;
}
