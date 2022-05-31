package sailpoint.web.sso;

import java.util.HashMap;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.SailPointContext;
import sailpoint.api.SailPointFactory;
import sailpoint.object.Configuration;
import sailpoint.object.Rule;
import sailpoint.tools.GeneralException;
import sailpoint.web.PageAuthenticationFilter;

/**
 * This class is used to validate EVERY HttpRequest and has been refactored out of DefaultSSOAuthenticator. The
 * refactoring method of separation of concerns was used to extract the validation from the authentication of SSO Rules.
 * We should have never put validation in the authenticator and instead used a new CommandHandler. The initial refactor
 * of {@link PageAuthenticationFilter} took place at version 6.3, and this class was created while fixing IIQCB-2233.
 * 
 * It will look at system configuration property {@link Configuration#LOGIN_SSO_VALIDATION_RULE}. If the rule is
 * present it tries to validate via the rule.
 */
public class DefaultSSOValidator {

    private static final Log log = LogFactory.getLog(DefaultSSOValidator.class);
    
    private String ssoValidationRuleName;
    
    public static class DefaultSSOValidatorInput {
        private HttpServletRequest request;

        /**
         * The http request
         */
        public HttpServletRequest getRequest() {
            return request;
        }

        public void setRequest(HttpServletRequest request) {
            this.request = request;
        }
    }
    
    public static class DefaultSSOValidatorResult {
        private boolean invalidated;
        /** error message if validation failed */
        private String validationMessage;
        
        /**
         * Whether we need to clear session and logout do to sso validation.
         */
        public boolean isInvalidated() {
            return invalidated;
        }
        
        public void setInvalidated(boolean val) {
            invalidated = val;
        }
        
        /**
         * if validation has failed this may contain an error message here
         */
        public String getValidationMessage() {
            return validationMessage;
        }
        
        public void setValidationMessage(String val) {
            validationMessage = val;
        }
    }
    
    /**
     * First we will see if the sso session is invalidated. If it is invalidated then authentication fails.
     */
    public DefaultSSOValidatorResult validate(DefaultSSOValidatorInput input) throws ServletException {
        DefaultSSOValidatorResult result = new DefaultSSOValidatorResult();
        ssoValidationRuleName = getSSOValidationRuleName();
        
        // If we are configured to always run the SSO Rule, do it.
        if (null != ssoValidationRuleName) {
            String validationMsg = runSSOValidationRule(ssoValidationRuleName,  input.getRequest());
            if (null != validationMsg) {
                result.setInvalidated(true);
                result.setValidationMessage(validationMsg);
            }
        }
        return result;
    }
    
    public boolean isEnabled() {
        boolean result = false;
        try {
            result = getSSOValidationRuleName() != null;
        } catch (ServletException e) {
            log.warn("could not get SSO Validation rule. Preparing to throw exception.", e);
            // deciding to throw here, if customers set up validation rules to validate every request
            // they likely want to fail if there's a validation problem
            throw new IllegalStateException(e);
        }
        return result;
    }

    /**
     * Get the SSO validation rule
     */
    protected String getSSOValidationRuleName() throws ServletException {
        String ruleName = null;
        try {
            SailPointContext ctx = SailPointFactory.getCurrentContext();
            Configuration config = ctx.getConfiguration();
            if (null != config) {
                ruleName = config.getString(Configuration.LOGIN_SSO_VALIDATION_RULE);
            }
        } catch (GeneralException ge) {
            throw new ServletException(ge);
        }
        
        return ruleName;
    }
    
    /**
     * Run the rule specified in the login config that will validate
     * the session on every request.  The rule should return either null
     * or a string.  A string is assumed to be an error, so we will propagate
     * an exception with the string as the error.
     */
    protected String runSSOValidationRule(String ruleName, HttpServletRequest httpRequest) {

        try {
            SailPointContext ctx = SailPointFactory.getCurrentContext();
            HashMap<String,Object> ruleCtx = new HashMap<>();
            ruleCtx.put("log", log);
            ruleCtx.put("ctx", ctx);
            ruleCtx.put("httpRequest", httpRequest);
            Rule ssorule = ctx.getObjectByName(Rule.class, ruleName);
            if ( ssorule == null ) {
                 throw new GeneralException("Could not find sso validation rule named ["+
                                   ruleName+"]");
            }
            String validation = (String) ctx.runRule(ssorule, ruleCtx);
            if ( validation != null ) {
                //validation error.  return the string as the error:
                return validation;
            }
        } catch(GeneralException e) {
            log.error("Problem executing SSO validation rule: " + e.toString());
        }
        
        return null;
    }

}
