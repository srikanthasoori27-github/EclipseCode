/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.tools;

import sailpoint.web.messages.MessageKeys;

import java.util.Locale;
import java.util.TimeZone;


/**
 * An exception for errors sending emails.
 *
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */
public class EmailException extends GeneralException {

    private String recipient;

    /**
     * Constructor.
     * 
     * @param  recipient  The intended recipient of the email.
     * @param  t          The wrapped mail exception.
     */
    public EmailException(String recipient, Throwable t) {
        super(new Message(Message.Type.Warn, MessageKeys.ERR_SEND_EMAIL_FAILED, recipient, t));
    }

    /**
     * Constructor that accepts an error message.
     */
    public EmailException(String msg) {
        super(msg);
    }
    
    /**
     * Constructor that accepts a localizable error message.
     */
    public EmailException(Message msg) {
        super(msg);
    }

    /**
     * Return the intended recipient of the email.
     * 
     * @return  The intended recipient of the email.
     */
    public String getRecipient() {
        return this.recipient;
    }

    public String getLocalizedMessage(Locale locale, TimeZone timezone) {
        return super.getLocalizedMessage(locale, timezone);    //To change body of overridden methods use File | Settings | File Templates.
    }

    @Override
    public String getMessage() {
        return super.getMessage();
    }

    @Override
    public String getLocalizedMessage() {
        return super.getLocalizedMessage();
    }
}
