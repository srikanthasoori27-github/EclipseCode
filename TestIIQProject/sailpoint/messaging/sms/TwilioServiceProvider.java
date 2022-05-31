package sailpoint.messaging.sms;

import com.twilio.Twilio;
import com.twilio.http.TwilioRestClient;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.rest.api.v2010.account.MessageCreator;
import com.twilio.type.PhoneNumber;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import sailpoint.messaging.MessageResult;
import sailpoint.messaging.MessagingServiceProvider;
import sailpoint.tools.Util;

import javax.inject.Inject;
import java.text.MessageFormat;

/**
 * Twilio specific implementation of ServiceProvider.
 * 
 * @author tapash.majumder
 *
 */
public class TwilioServiceProvider implements MessagingServiceProvider<SMSMessage> {

    private static final Log log = LogFactory.getLog(TwilioServiceProvider.class);

    public static class TwilioOptions {
        private String accountSid;
        private String authToken;
        private String fromPhone;
        
        public String getAccountSid() {
            return accountSid;
        }
        
        public void setAccountSid(String accountSid) {
            this.accountSid = accountSid;
        }
        
        public String getAuthToken() {
            return authToken;
        }
        
        public void setAuthToken(String authToken) {
            this.authToken = authToken;
        }
        
        public String getFromPhone() {
            return fromPhone;
        }
        
        public void setFromPhone(String val) {
            fromPhone = val;
        }
        
        @Override
        public String toString() {
            return MessageFormat.format("AccountSid: {0}, AuthToken: {1}, FromPhone: {2}", accountSid, authToken, fromPhone);
        }
    }

    private TwilioOptions options;

    @Inject
    public TwilioServiceProvider(TwilioOptions options) {
        this.options = options;
    }


    /**
     * This will validate the phone numbers and also format to the right number if formatting is necessary.
     */
    @Override
    public MessageResult<SMSMessage> validateAndFormat(SMSMessage message) {
        MessageResult<SMSMessage> result = message.validateAndFormat();
        if (!result.isSuccess()) {
            return result;
        }
        
        String from = message.getFrom() != null ? message.getFrom() : options.getFromPhone();
        message.setFrom(SMSMessage.validateAndFormatPhoneNumber(result, from, "from"));
        if (!result.isSuccess()) {
            return result;
        }
        
        return result;
    }
    
    /**
     * IMP: Please note that this method assumes that {@link TwilioServiceProvider#validateAndFormat(SMSMessage)} has
     * already been called.
     * 
     * This will dispatch the message. 
     */
    @Override
    public SMSMessageResult sendMessage(SMSMessage message) {
        SMSMessageResult result = new SMSMessageResult();
        result.setSuccess(true);
        
        try {
            if (needsInit(options)) {
                init(options);
            }

            PhoneNumber toPhone = new PhoneNumber(message.getTo());
            PhoneNumber fromPhone = new PhoneNumber(message.getFrom());
            String body =  message.getMessage();
            MessageCreator msgCreator = Message.creator(toPhone, fromPhone,  body);
            Message msg = msgCreator.create();

            result.setSid(msg.getSid());
        } catch (Throwable ex) {
            result.setSuccess(false);
            result.getFailureMessages().add("Failed due to exception: " + ex.getMessage());
            result.getFailureMessages().add(Util.stackToString(ex));
        }
        
        return result;
    }

    //////////////////////////////////////////////////////////
    // Twilio singleton management utility methods
    //////////////////////////////////////////////////////////

    private static String accountSid; // the current accountSid held in the Twilio singleton
    private static String authToken; // the current authToken help in the Twilio singleton

    /**
     * @return true if this is the first time called, or if the
     * accountSid or authToken have changed
     */
    private static boolean needsInit(TwilioOptions options) {
        if (accountSid == null || authToken == null) {
            return true;
        }

        if (!accountSid.equals(options.getAccountSid())) {
            return true;
        }

        if (!authToken.equals(options.getAuthToken())) {
            return true;
        }

        return false;
    }

    /**
     * Initialize (or possibly re-initialize) the Twilio singleton.
     * Create a custom rest client for the singleton if https
     * proxy settings are present.
     */
    private static void init(TwilioOptions options) {

        accountSid = options.getAccountSid();
        authToken = options.getAuthToken();

        Twilio.init(accountSid, authToken);

        String httpsProxyHost = System.getProperty("https.proxyHost");
        String httpsProxyPort = System.getProperty("https.proxyPort");

        if (log.isDebugEnabled()) {
            log.debug("httpsProxyHost: " + httpsProxyHost);
            log.debug("httpsProxyPort: " + httpsProxyPort);
        }

        if ((httpsProxyHost != null) && (httpsProxyHost.length() > 0)) {

            /*
             * Bug 23126 SMS verification MUST honor proxy settings.
             * Note: Twilio URL uses 'https' protocol, hence using
             *       https.proxy* properties.
             */

            if (log.isDebugEnabled()) {
                log.debug("Setting proxy to: " + httpsProxyHost + ":" + Integer.parseInt(httpsProxyPort));
            }

            if (httpsProxyPort == null) {
                log.debug("https.proxyPort is not specified.  Using value of 80.");
                httpsProxyPort = "80";
            }

            int httpsProxyPortInt = 80;
            try {
                httpsProxyPortInt = Integer.parseInt(httpsProxyPort);
            }
            catch (NumberFormatException e) {
                log.debug("https.proxyPort " + httpsProxyPort + " is not an integer.  Using value of 80.");
                // leave at port 80
            }

            ProxiedTwilioClientCreator clientCreator = new ProxiedTwilioClientCreator(
                    options.getAccountSid(), options.getAuthToken(), httpsProxyHost, httpsProxyPortInt);
            TwilioRestClient twilioRestClient = clientCreator.getClient();
            Twilio.setRestClient(twilioRestClient);

            // End bug 23126 changes
        }
    }

}
