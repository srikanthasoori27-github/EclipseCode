package sailpoint.web.sso;

import java.io.IOException;
import java.util.HashMap;

import javax.servlet.ServletException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.SailPointContext;
import sailpoint.api.SailPointFactory;
import sailpoint.object.Configuration;
import sailpoint.object.Identity;
import sailpoint.object.Rule;
import sailpoint.rest.HttpSessionStorage;
import sailpoint.service.LoginService;
import sailpoint.service.SessionStorage;
import sailpoint.tools.Base64;
import sailpoint.tools.GeneralException;
import sailpoint.web.PageAuthenticationFilter;

/**
 * This class provides an implementation of {@link SSOAuthenticator} interface
 * that provides the functionality that was existing before the major refactoring of {@link PageAuthenticationFilter} took place.
 * 
 * It will looks at system configuration property {@link Configuration#LOGIN_SSO_VALIDATION_RULE} 
 * and {@link Configuration#LOGIN_SSO_RULE}. If these rules are present it tries to validate via these rules.
 * 
 * @author tapash.majumder
 *
 */
public class DefaultSSOAuthenticator implements SSOAuthenticator {

    private static final Log log = LogFactory.getLog(DefaultSSOAuthenticator.class);
    
    private static final String SAML_RESPONSE_PARAM = "SAMLResponse";
    private static final String SAML_RELAY_STATE = "RelayState";

    private Identity ssoUser;
    
    /**
     * First we will see if the sso session is invalidated.
     * If it is invalidated then authentication fails.
     * 
     * Then we will try to use logonToken
     * Finally we will run the SSOAuthentication rule.
     */
    @Override
    public SSOAuthenticationResult authenticate(SSOAuthenticationInput input) throws ServletException, IOException {
        SSOAuthenticationResult result = new SSOAuthenticationResult();
        
       //Do not want to redirect the request with the SAMLResponse still attached, will cause problems
        //with URL length exceeding max
        result.addParamToFilter(SAML_RESPONSE_PARAM);
        result.addParamToFilter(SAML_RELAY_STATE);
        
        successfulTokenLogin(result, input);
        if (!result.isSuccess()) {
            doSSOAuthentication(result, input);
        }

        return result;
    }
    
    protected void doSSOAuthentication(SSOAuthenticationResult authResult, SSOAuthenticationInput input)
            throws ServletException {
        String ssoRuleName = getSSORuleName();
        if ( ssoRuleName != null ) {
            if (runSSORule(ssoRuleName, input)) {
                authResult.setSuccess(true);
                if (authResult.getIdentity() == null) {
                    authResult.setIdentity(ssoUser);
                }
                authResult.setNeedsRedirectUrl(true);
                authResult.setRedirectURL(RedirectUtil.calculateRedirectUrl(input));
            }
        }
    }
    
    /**
     * Check to see if there is a logonToken passed as a parameter.
     * Use this parameter like an SSO token and see if its a 
     * valid RemoteLoginToken object in our database.
     * 
     * This option is an alternative for environments
     * where they don't have an integrated SSO solution.
     * 
     * Typically the SSO token is generated using a web-service call 
     * to IIQ and then included in a generated url string on the 
     * parameter of the url.    
     * 
     * The SRM Launchers will call us twice with the same remoteLoginToken,
     * once to "Show URL" and again for Launch.  Housekeeper cleans
     * up the expired tokens.
     */
    protected void successfulTokenLogin(SSOAuthenticationResult result, SSOAuthenticationInput input) {
        String loginTokenParameter = input.getRequest().getParameter(PageAuthenticationFilter.ATT_REMOTE_LOGIN);
        if ( loginTokenParameter != null ) {
            byte[] loginTokenBytes = Base64.decode(loginTokenParameter);
            String tokenId = null;
            if ( loginTokenBytes != null ) {
                tokenId = new String(loginTokenBytes);
            }
            if ( tokenId != null ) {
                try {
                    SailPointContext ctx = SailPointFactory.getCurrentContext();
                    Identity user = new LoginService(ctx).processRemoteTokenLogin(tokenId);
                    if ( user != null ) {
                        result.setSuccess(true);
                        result.setIdentity(user);
                        SessionStorage sessionStorage = new HttpSessionStorage(input.getRequest().getSession());
                        LoginService.writeIdentitySession(sessionStorage, user);
                    }
                } catch(Exception e ) {
                    log.error("Problem processing remote login token." + e);
                }
            }
        }
    }
    
    protected boolean runSSORule(String ssoRuleName, SSOAuthenticationInput input) {
        boolean success = false;

        try {
            SailPointContext ctx = SailPointFactory.getCurrentContext();
            HashMap<String,Object> ruleCtx = new HashMap<>();
            ruleCtx.put("log", log);
            ruleCtx.put("ctx", ctx);
            ruleCtx.put("httpRequest", input.getRequest());

            Rule ssorule = ctx.getObjectByName(Rule.class, ssoRuleName);
            if ( ssorule == null ) {
                 throw new GeneralException("Could not find sso rule named ["+ ssoRuleName+"]");
            }

            SessionStorage sessionStorage = new HttpSessionStorage(input.getRequest().getSession());
            Identity user = new LoginService(ctx).runSSORule(sessionStorage, ssorule, ruleCtx);
            if(user != null) {
                success = true;
                ssoUser = user;
            }
        } catch(GeneralException e) {
            // djs: humm.. should we optionally throw here?
            // if we throw, you can't login until you change the
            // sytem configuration setting
            log.error("Problem executing SSO rule: " + e.toString());
            //throw new ServletException(e);
        }
        return success;
    }
    
    /**
     * Checkwith the system config for an Single Sign On rule.
     */
    protected String getSSORuleName() throws ServletException {

        String ruleName = null;
        try {
             SailPointContext ctx = SailPointFactory.getCurrentContext();
             Configuration config = ctx.getConfiguration();
             if (null != config) {
                 ruleName = config.getString(Configuration.LOGIN_SSO_RULE);
             }
        } catch (GeneralException e) {
            throw new ServletException(e);
        }
        return ruleName;
    }
}
