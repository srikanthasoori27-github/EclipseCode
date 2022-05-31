package sailpoint.messaging.sms;

import java.text.MessageFormat;

import javax.inject.Inject;

import sailpoint.api.SailPointContext;
import sailpoint.messaging.MessageResult;
import sailpoint.messaging.MessagingServiceProvider;
import sailpoint.object.Configuration;
import sailpoint.object.EmailOptions;
import sailpoint.object.EmailTemplate;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

/**
 * This class will send out sms messages as emails.
 * 
 * 
 * @author tapash.majumder
 *
 */
public class SMSEmailServiceProvider implements MessagingServiceProvider<SMSMessage> {

    /**
     * System configuration Keys which will hold the to, from addresses etc.
     */
    public static final String KEY_SMS_EMAIL_TO = "sms_email_to";
    public static final String KEY_SMS_EMAIL_FROM = "sms_email_from";
    public static final String KEY_SMS_EMAIL_SUBJECT = "sms_email_subject";
    
    public static class SMSEmailOptions {
        /**
         * When we are sending the sms as email. whom should we sent it to?
         * This is where the values are set.
         */
        private String to;
        private String from;
        private String subject;
        
        public String getTo() {
            return to;
        }
        
        public void setTo(String to) {
            this.to = to;
        }
        
        public String getFrom() {
            return from;
        }
        
        public void setFrom(String from) {
            this.from = from;
        }
        
        public String getSubject() {
            return subject;
        }
        
        public void setSubject(String subject) {
            this.subject = subject;
        }
    }
    
    private SailPointContext context;
    private SMSEmailOptions options;

    @Inject
    public SMSEmailServiceProvider(SailPointContext context) throws GeneralException {
        this.context = context;
        readOptionsFromSystemConfiguration();
    }
    

    @Override
    public MessageResult<SMSMessage> validateAndFormat(SMSMessage message) {
        return message.validateAndFormat();
    }
    
    @Override
    public MessageResult<SMSMessage> sendMessage(SMSMessage message) {
        
        SMSMessageResult result = new SMSMessageResult();
        result.setSuccess(true);
        
        try {
            EmailTemplate template = new EmailTemplate();
            
            template.setFrom(options.getFrom());
            template.setSubject(options.getSubject());
            template.setBody(createMockEmailMessageBody(message));
            
            EmailOptions emailOptions = new EmailOptions();
            emailOptions.setTo(options.getTo());
            emailOptions.setSendImmediate(true);
            
            context.sendEmailNotification(template, emailOptions);
        } catch (Throwable ex) {
            result.setSuccess(false);
            result.getFailureMessages().add("Failed due to exception: " + ex.getMessage());
            result.getFailureMessages().add(Util.stackToString(ex));
        }
        
        return result;
    }

    private String createMockEmailMessageBody(SMSMessage message) {
        return MessageFormat.format("Sending SMS Message, to: {0}, from: {1}, message: {2}", message.getTo(), message.getFrom(), message.getMessage());
        
    }
    
    private void readOptionsFromSystemConfiguration() throws GeneralException {
        
        options = new SMSEmailOptions();
        
        Configuration config = context.getConfiguration();
        options.setTo(getConfigStringValue(config, KEY_SMS_EMAIL_TO, "demo@example.com"));
        options.setFrom(getConfigStringValue(config, KEY_SMS_EMAIL_FROM, "user@sailpoint.com"));
        options.setSubject(getConfigStringValue(config, KEY_SMS_EMAIL_SUBJECT, "Sending SMS as Email."));
    }
    
    private String getConfigStringValue(Configuration config, String name, String defVal) {
        String val = config.getString(name);
        if (val == null) {
            val = defVal;
        }
        return val;
    }
}
