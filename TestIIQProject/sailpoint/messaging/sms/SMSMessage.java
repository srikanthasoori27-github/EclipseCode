package sailpoint.messaging.sms;

import java.text.MessageFormat;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.messaging.Message;
import sailpoint.messaging.MessageResult;
import sailpoint.tools.PhoneNumberFormatResult;
import sailpoint.tools.PhoneNumberUtil;
import sailpoint.tools.Util;

/**
 * Encapsulates text message.
 * 
 * 
 * @author tapash.majumder
 *
 */
public class SMSMessage implements Message {

    private static final Log log = LogFactory.getLog(SMSMessage.class);
    
    private String to;
    private String from;
    private String message;

    /**
     * Instantiate an SMSMessage using phone number and message.
     * 
     * @param to Phone number in acceptable format
     * @param from Phone number to send it from. 
     * @param message the text message to send.
     */
    public SMSMessage(String to, String from, String message) {
        this.to = to;
        this.from = from;
        this.message = message;
    }

    /**
     * Another constructor which uses the default 'from'. 
     */
    public SMSMessage(String to, String message) {
        this(to, null, message);
    }
    
    public String getTo() {
        return to;
    }
    
    public void setTo(String val) {
        this.to = val;
    }
    
    public String getFrom() {
        return from;
    }
    
    public void setFrom(String val) {
        from = val;
    }
    
    public String getMessage() {
        return message;
    }
    
    public void setMessage(String message) {
        this.message = message;
    }
    
    
    @Override
    public String displayString() {
        return MessageFormat.format("To: {0}, From: {1}, Message: {2}", getTo(), getFrom(), getMessage());
    }

    /**
     * This will validate and format to E164.
     * Please note at this point 'from' may be null but 'to' is not allowed to be null.
     */
    @SuppressWarnings("unchecked")
    @Override
    public MessageResult<SMSMessage> validateAndFormat() {
        
        SMSMessageResult result = new SMSMessageResult();
        result.setSuccess(true);
        
        setTo(validateAndFormatPhoneNumber(result, to, "to"));
        if (from != null) {
            setFrom(validateAndFormatPhoneNumber(result, from, "from"));
        }
        
        return result;
    }
    
    /**
     * This will validate that the phone number is in the right format.
     * This will return the formatted phone number.
     */
    static String validateAndFormatPhoneNumber(MessageResult<SMSMessage> result, String number, String numberType) {
        String formatted = null;
        
        if (Util.isNullOrEmpty(number)) {
            failWithMessage(result, "phone number can't be null", numberType);
            return formatted;
        }

        PhoneNumberFormatResult pnfr = PhoneNumberUtil.getValidPhoneNumber(number);
        if (pnfr.isValid() == false) {
            failWithMessage(result, pnfr.getMessage(), numberType);
        }

        return pnfr.getPhoneNumber();
    }

    // Sets status to fail and adds failure message
    private static void failWithMessage(MessageResult<SMSMessage> result, String message, String numberType) {
        if (log.isWarnEnabled()) {
            log.warn(numberType + ": " + message);
        }
        result.getFailureMessages().add(numberType + ": " + message);
        result.setSuccess(false);
    }
}
