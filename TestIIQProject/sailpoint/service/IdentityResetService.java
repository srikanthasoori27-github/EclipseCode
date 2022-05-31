package sailpoint.service;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

import javax.inject.Inject;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.SailPointContext;
import sailpoint.injection.SMSResetConfigProvider;
import sailpoint.injection.SailPointContextProvider;
import sailpoint.messaging.MessagingService;
import sailpoint.messaging.sms.SMSMessage;
import sailpoint.messaging.sms.SMSMessageResult;
import sailpoint.object.Identity;
import sailpoint.object.SMSResetConfig;
import sailpoint.object.VerificationToken;
import sailpoint.service.PasswordReseter.AuthQuestionsHelper;
import sailpoint.tools.DateService;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.PhoneNumberFormatResult;
import sailpoint.tools.PhoneNumberUtil;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;

/**
 * A service layer to deal with user Reset
 * 
 * @author ryan.pickens
 * @author tapash.majumder
 *
 */
public class IdentityResetService {

    private static final Log log = LogFactory.getLog(IdentityResetService.class);
    
    /**
     * Holds const values to be used
     * 
     * @author tapash.majumder
     *
     */
    public static class Consts {
        
        public enum SessionAttributes {
            /**
             * the session attribute where identity name is stored
             */
            SESSION_IDENT_NAME("userResetIdentityName"),
            /**
             * the session attribute where account id is saved.
             */
            SESSION_ACCOUNT_ID("userResetAccountId"),

            /**
             * Identity auth attributes, used by login bean etc.
             */
            ID_AUTH_ATTR_MGR_STATUS("managerStatus"),
            
            /**
             * Identity auth attributes, used by login bean etc.
             */
            ID_AUTH_ATTR_VIOLATION_OWNER("violationOwner"),

            /** Key for the session variable that stores a list of dynamic scope names applicable for the identity */
            ATT_DYNAMIC_SCOPES("dynamicScopes"),
            
            /**
             * session attribute used to determine if the userReset completed successful
             */
            SESSION_SUCCESSFUL_RESET("successfulReset"),

            /**
             * Session attribute for storing message request times for use with throttling.
             */
            SESSION_MESSAGE_SENT_DATE("messageSentDate");
            
            private String value;
            
            private SessionAttributes(String value) {
                this.value = value;
            }
            
            public String value() {
                return value;
            }
        }
        
        public enum Flows {
            FORGOT_PASSWORD_FLOW("ForgotPassword"),
            EXPIRED_PASSWORD_FLOW("ExpirePassword"),
            PASSWORDS_REQUEST_FLOW("PasswordsRequest"),
            ACCOUNTS_REQUEST_FLOW("AccountsRequest"),
            UNLOCK_ACCOUNT_FLOW("UnlockAccount");
    
            private String value;
            
            private Flows(String value) {
                this.value = value;
            }
            
            public String value() {
                return value;
            }
        }
    }
    
    /**
     * Serialization/Deserialization keys for the response that is sent back.
     * 
     * The names must match what is there in
     * the Javascript world
     * @author tapash.majumder
     *
     */
    public static final class Keys {
        public static final class AuthQuestions {
            public static final String QUESTIONS = "questions";
            public static final String NUM_REQUIRED = "numRequired";
        }
    }
    
    // Lengh of the token to be generated.
    public static final int TOKEN_LENGTH = 6;
    
    /**
     * Generates the SMS text to be sent.
     * We have abstracted it into an interface so that
     * we can generate different messages for unit tests etc.
     * Plus it gives us more flexibility if we want to send different messages.
     */
    public interface SMSMessageComposer {
        String createMessage(VerificationToken token, Locale locale, TimeZone timezone);
    }
    
    private SailPointContext context;
    private SMSResetConfig config;
    private MessagingService<SMSMessage> smsMessagingService;
    private TokenGenerator tokenGenerator;
    private SMSMessageComposer messageComposer;
    private DateService dateService;
    private PasswordReseter reseter;
    
    /**
     * Constructor. Being a good citizen of the IOC country we will ask for everything we depend on. 
     * 
     * @param context SailPointContext
     * @param config SMS reset options. This is nullable in the case a user has never updated SystemConfig
     * the only real way we can get here with a null SMSResetConfig is to have upgraded with auth questions enabled
     * and never have visited/saved login configuration (or manually removed the smsResetConfig option from SysConfig). -rap
     * @param smsMessagingService The messaging service used to send the message.
     * @param tokenGenerator delegate generation of token to this class
     * @param messageComposer delegate generation of SMS message to this class
     * @param dateService performs date calculations (can be used to fast forward time etc).
     * @param reseter is a helper object which will reset password see {@link PasswordReseter}
     */
    @Inject
    public IdentityResetService(
            SailPointContextProvider context,
            SMSResetConfigProvider config,
            MessagingService<SMSMessage> smsMessagingService, 
            TokenGenerator tokenGenerator, 
            SMSMessageComposer messageComposer, 
            DateService dateService,
            PasswordReseter reseter) {
        this.context = context.get();
        this.config = config.get();
        this.smsMessagingService = smsMessagingService;
        this.tokenGenerator = tokenGenerator;
        this.messageComposer = messageComposer;
        this.dateService = dateService;
        this.reseter = reseter;

        if (log.isInfoEnabled()) {
            log.info("IdentityResetService, smsMessagingService: " + smsMessagingService.getClass().getName());
        }
    }

    /**
     * Will send SMS message to identity with userName.
     * and send back result which contains the sid etc.

     * In case the sms couldn't be sent. It will throw an exception with details.
     */
    public SMSMessageResult sendSMS(Identity identity, Locale locale, TimeZone timezone) throws GeneralException {
        
        VerificationToken token = identity.getVerificationToken();
        if (token == null) {
            token = tokenGenerator.generateVerificationToken(TOKEN_LENGTH, config.getDurationValid());
        } else {
            if (hasExpired(token)) {
                token = tokenGenerator.generateVerificationToken(TOKEN_LENGTH, config.getDurationValid());
            }
        }

        String toPhoneNumber = fetchToPhoneNumber(identity);
        LoginService loginService = new LoginService(this.context);
        if (loginService.isDetailedErrorLogging()) {
            PhoneNumberFormatResult pnfr = PhoneNumberUtil.getValidPhoneNumber(toPhoneNumber);
            if (! pnfr.isValid()) {
                // Provide unique message key for case where SMS phone number is empty or invalid
                // IIQETN-6631
                PasswordReseter.throwValidationException(MessageKeys.RESET_ERR_NO_SMS_PHONE);
            }
        }

        SMSMessage message = new SMSMessage(
                toPhoneNumber,
                config.getFromPhone(),
                messageComposer.createMessage(token, locale, timezone));

        SMSMessageResult result = (SMSMessageResult) smsMessagingService.sendMessage(message);

        // Save in identity only if successfully sent the sms.
        if (result.isSuccess()) {
            identity.setVerificationToken(token);
            context.commitTransaction();
        } else {
            if (log.isInfoEnabled()) {
                log.info(Util.join(result.getFailureMessages(), ","));
            }
            PasswordReseter.throwValidationException();
        }
        
        return result;
    }

    /**
     * Reset the user password. If the password can't be reset then an exception will be thrown which
     * will be handled by the web service layer.
     * 
     * Please note that session variables have to be set separately to login the user after the password
     * has been reset.
     *  
     * @param identity the identity who password is to be reset
     * @param reset this object contains all the information needed to reset the password see {@link PasswordResetData}
     * 
     */
    public void reset(Identity identity, PasswordResetData reset, Locale locale, TimeZone timezone) throws GeneralException {
        reseter.reset(identity, reset, locale, timezone, getConfig());
    }

    /**
     * Unlock the account. If the account can't be reset then an exception will be thrown which
     * will be handled by the web service layer.
     * 
     * Please note that session variables have to be set separately to login the user after the password
     * has been reset.
     *  
     * @param identity the identity who password is to be reset
     * @param accountId the account that is being unlocked.
     * @param unlockData this object contains all the information needed to reset the password see {@link PasswordResetData}
     * 
     */
    public void unlock(Identity identity, String accountId, AccountUnlockData unlockData, Locale locale, TimeZone timezone) throws GeneralException {
        reseter.unlock(identity, accountId, unlockData, locale, timezone, getConfig());
    }
    
    /**
     * Get a list of auth questions for the user. 
     * Or return a list of fake questions if the user is not found.
     * Note that the param identity may be null.
     * 
     * @param identity the identity whow password is to be reset/unlocked
     * @param locale
     * @param timezone
     * @return a response which contains the list of questions as well as success="true"/"false".
     */
    public Map<String, Object> getAuthQuestions(Identity identity, Locale locale, TimeZone timezone) throws GeneralException {
        Map<String, Object> result = new HashMap<String, Object>();
        
        AuthQuestionsHelper helper = new AuthQuestionsHelper(context);
        result.put(Keys.AuthQuestions.QUESTIONS, helper.getQuestions(identity, locale));
        result.put(Keys.AuthQuestions.NUM_REQUIRED, helper.getMinimumRequired());
        
        return result;
    }
    
    /**
     * We will expire if less than 1 minute is left for expiration.
     */
    private boolean hasExpired(VerificationToken token) {
        if (dateService.calculateMinsLeftFromNow(token.getExpireDate()) > 0) {   
            return false;
        } else {
            return true;
        }
    }
    
    /**
     * Reads the phone number attribute from identity
     */
    private String fetchToPhoneNumber(Identity identity) throws GeneralException {
        return (String) identity.getAttribute(config.getPhoneAttribute());
    }
    
    /**
     * Compose the message based on iiqMessages.
     * It will mention the code and time left in minutes.
     * 
     * @author tapash.majumder
     *
     */
    public static class DefaultSMSMessageComposer implements SMSMessageComposer {

        private DateService dateService;
        
        @Inject
        public DefaultSMSMessageComposer(DateService dateService) {
            this.dateService = dateService;
        }
        
        @Override
        public String createMessage(VerificationToken token, Locale locale, TimeZone timezone) {
            return new Message(MessageKeys.SMS_RESET_TEMPLATE, 
                    token.getTextCode(), 
                    dateService.calculateMinsLeftFromNow(token.getExpireDate()))
                        .getLocalizedMessage(locale, timezone);
        }
    }

    /**
     * Need access to the config in REST resources.
     * @return SMSResetConfig
     */
    public SMSResetConfig getConfig() {
        return config;
    }
}
