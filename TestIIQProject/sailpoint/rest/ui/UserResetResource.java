package sailpoint.rest.ui;

import java.util.Date;
import java.util.Map;

import javax.inject.Inject;
import javax.servlet.http.HttpSession;
import javax.ws.rs.POST;
import javax.ws.rs.Path;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.SailPointContext;
import sailpoint.api.SailPointFactory;
import sailpoint.authorization.SMSResetAuthorizer;
import sailpoint.authorization.SystemConfigOptionsAuthorizer;
import sailpoint.object.Configuration;
import sailpoint.object.Identity;
import sailpoint.rest.BaseResource;
import sailpoint.service.AccountUnlockData;
import sailpoint.service.IdentityResetService;
import sailpoint.service.LoginService;
import sailpoint.service.IdentityResetService.Consts.SessionAttributes;
import sailpoint.service.PasswordResetData;
import sailpoint.service.PasswordReseter;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.web.messages.MessageKeys;

/**
 * Deals with password reset workflow.
 * 
 * @author tapash.majumder
 *
 */
@Path(Paths.USER_RESET)
public class UserResetResource extends BaseResource {

    private static final Log log = LogFactory.getLog(UserResetResource.class);

    private IdentityResetService resetService;

    /**
     * Dependency Injection...We will be given the service to use.
     * This should be used only when instantiating an instance of this class.
     */
    public UserResetResource(IdentityResetService resetService, BaseResource parent) {
        super(parent);
        this.resetService = resetService;
    }

    /**
     * This constructor will be used by Jersey Guice injector.
     *
     * @param resetService The injected instance of IdentityResetService to use
     */
    @Inject
    public UserResetResource(IdentityResetService resetService) {
        super();

        if (log.isInfoEnabled()) {
            log.info("UserResetResource");
            log.info("resetService: " + resetService);
        }

        this.resetService = resetService;
    }

    /**
     * This will be used by the web container.
     */
    public UserResetResource() {
        super();
    }

    /**
     * We need to override this method because the parent method
     * tries to set a username and that causes an exception.
     * this resource will have no authenticated user.
     */
    @Override
    public SailPointContext getContext() {
        try {
            return SailPointFactory.getCurrentContext();
        } catch (GeneralException ex) {
            throw new IllegalStateException("Could not get current context");
        }
    }
    
    /**
     * Will send SMS reset text to the identity.
     * It expects to find identity name in the following 
     * session variable {@link SessionAttributes#SESSION_IDENT_NAME}. If the identity name can't be found it will 
     * behave normally and return success.
     * 
     */
    @POST
    @Path(Paths.UserReset.SEND_SMS)
    public void sendSMS() throws GeneralException {
        if (log.isInfoEnabled()) {
            log.info("sendSMS");
        }
        authorize(new SMSResetAuthorizer());

        handleThrottle();

        Identity identity = fetchIdentity();
        // If we can't correlate the identity, don't send the request.
        // Instead just return normally to avoid phishing expeditions.
        if (identity != null) {
            getResetService().sendSMS(identity, getLocale(), getUserTimeZone()).toMap();
        }

        // Add the sent date to the session so we can throttle requests
        getSession().setAttribute(SessionAttributes.SESSION_MESSAGE_SENT_DATE.value(), new Date().getTime());
    }

    /**
     * Tests to see if we need to throttle the current request.
     *
     * @throws GeneralException Throws an exception VISIBLE TO THE USER when rate of requests is exceeded.
     */
    private void handleThrottle() throws GeneralException {
        Object msd = getSession().getAttribute(SessionAttributes.SESSION_MESSAGE_SENT_DATE.value());
        if (msd != null) {
            long sentDate = (Long) msd;
            int period = (getResetService().getConfig().getThrottleMinutes() * 60 * 1000);
            long current = new Date().getTime();
            if ((current - sentDate) < period) {
                int remaining = period - (int) (current - sentDate);
                String err = new Message(MessageKeys.MESSAGE_RATE_EXCEEDED, (remaining / 1000)).getLocalizedMessage();
                if (log.isInfoEnabled()) {
                    String identName = fetchIdentityName();
                    String accountId = fetchAccountId();
                    log.info("SMS message requested for user " + (identName != null ? identName : accountId) +
                            " before throttle period expired. Remaining time: " + remaining + " ms.");
                }
                throw new GeneralException(err);
            }
        }
    }

    /**
     * Will return a list of validation questions for the user.
     * It expects to find identity name in the following 
     * session variable {@link SessionAttributes#SESSION_IDENT_NAME}. If the identity name can't be found it will 
     * still return a list of fake questions to prevent phishing.
     * 
     */
    @POST
    @Path(Paths.UserReset.AUTH_QUESTIONS)
    public Map<String, Object> authQuestions() throws GeneralException {
        if (log.isInfoEnabled()) {
            log.info("authQuestions");
        }
        authorize(new SystemConfigOptionsAuthorizer(Configuration.AUTH_QUESTIONS_ENABLED));

        Identity identity = fetchIdentity();

        // Note that identity may be null here
        return getResetService().getAuthQuestions(identity, getLocale(), getUserTimeZone());
    }
    
    /**
     * Will change the password for the identity.
     * It expects the identity name in the following session variable
     * {@link SessionAttributes#SESSION_IDENT_NAME}.
     * 
     * Input will come in as a Map which encapsulates {@link PasswordResetData} object.
     * 
     * If identity can't be found it will return success.
     * Otherwise, it will return success when password has been changed, false otherwise.
     * 
     */
    @POST
    @Path(Paths.UserReset.CHANGE_PASSWORD)
    public void changePassword(Map<String, Object> input) throws GeneralException {
        if (log.isInfoEnabled()) {
            log.info("changePassword");
        }
        authorize(new SystemConfigOptionsAuthorizer(Configuration.ENABLE_FORGOT_PASSWORD));

        Identity identity = fetchIdentity();
        if (identity == null) {
            // meaning identity could not be correlated.
            LoginService loginService = new LoginService(getContext());
            if (! loginService.isDetailedErrorLogging()) {
                // throw generic exception/message
                PasswordReseter.throwValidationException();
            } else {
                // IIQETN-6631 provide unique message for user not found
                PasswordReseter.throwValidationException(MessageKeys.RESET_ERR_USER_NOT_FOUND);
            }
        }

        PasswordResetData resetData = new PasswordResetData(input);
        
        HttpSession session = getSession();
        String accountId = (String)session.getAttribute(SessionAttributes.SESSION_ACCOUNT_ID.value());
        if (accountId != null) {
            resetData.setAccountId(accountId);
        }
        
        getResetService().reset(identity, resetData, getLocale(), getUserTimeZone());
        
        //Add a token to the session so we can successfully log the user in with consecutive call if needed
        setResetSuccess(true);

    }

    /**
     * Will change the password for the identity.
     * It expects the identity name in the following session variable
     * {@link SessionAttributes#SESSION_IDENT_NAME}.
     * 
     * Input will come in as a Map which encapsulates {@link AccountUnlockData} object.
     * 
     * If identity can't be found it will return success.
     * Otherwise, it will return success when account has been unlocked, false otherwise.
     */
    @POST
    @Path(Paths.UserReset.UNLOCK_ACCOUNT)
    public void unlockAccount(Map<String, Object> input) throws GeneralException {
        if (log.isInfoEnabled()) {
            log.info("unlockAccount");
            log.info("input: " + input);
        }
        authorize(new SystemConfigOptionsAuthorizer(Configuration.ENABLE_ACCOUNT_UNLOCK));

        Identity identity = fetchIdentity();
        if (identity == null) {
            // meaning identity could not be correlated.
            LoginService loginService = new LoginService(getContext());
            if (! loginService.isDetailedErrorLogging()) {
                // throw generic error message.
                PasswordReseter.throwValidationException();
            } else {
                // IIQSR27 - provide message for user not found
                PasswordReseter.throwValidationException(MessageKeys.RESET_ERR_USER_NOT_FOUND);
            }
        }

        AccountUnlockData unlockData = new AccountUnlockData(input);
        String accountId = fetchAccountId();
        getResetService().unlock(identity, accountId, unlockData, getLocale(), getUserTimeZone());
    }
    
    private IdentityResetService getResetService() {
        return resetService;
    }
    
    /**
     * Will look for identity name is session, then fetch the identity object
     * from the db if exists or null.
     */
    private Identity fetchIdentity() throws GeneralException {
        Identity identity = null;
        
        String identityName = fetchIdentityName();
        if (log.isInfoEnabled()) {
            log.info("identityName: " + identityName);
        }
        
        if (identityName != null) {
            return getContext().getObjectByName(Identity.class, identityName);
        }
        
        return identity;
    }
    
    private String fetchIdentityName() {
        return (String) getSession().getAttribute(SessionAttributes.SESSION_IDENT_NAME.value());
    }

    private String fetchAccountId() {
        return (String) getSession().getAttribute(SessionAttributes.SESSION_ACCOUNT_ID.value());
    }
    
    /**
     * Used to set the success of UserReset action on the session.
     * We use this as a validation in loginUser to ensure the userReset was successful before authenticating the user
     * @param successful Status of the userReset
     */
    private void setResetSuccess(boolean successful) {
        getSession().setAttribute(SessionAttributes.SESSION_SUCCESSFUL_RESET.value(), successful);
       
    }
}
