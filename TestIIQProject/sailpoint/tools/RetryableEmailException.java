/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.tools;

import sailpoint.web.messages.MessageKeys;

/**
 * An email exception thrown when sending an email could be retried.
 *
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */
public class RetryableEmailException extends EmailException {

    /**
     * Constructor.
     * 
     * @param  recipient  The recipient of the email.
     * @param  t          The wrapped MessagingException.
     */
    public RetryableEmailException(String recipient, Throwable t) {
        super(new Message(Message.Type.Error, MessageKeys.ERR_SENDING_EMAIL_QUEUED, recipient, t));
    }
}
