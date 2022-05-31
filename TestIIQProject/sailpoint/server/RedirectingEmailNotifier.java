/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.server;

import sailpoint.api.EmailNotifier;
import sailpoint.api.SailPointContext;
import sailpoint.object.EmailOptions;
import sailpoint.object.EmailTemplate;
import sailpoint.tools.GeneralException;


/**
 * An email notifier used for testing that will send all emails to a designated
 * recipient.
 *
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */
public class RedirectingEmailNotifier implements EmailNotifier {

    private EmailNotifier delegate;
    private String emailAddress;
    private String fileName;

    /**
     * Flag to indicate we should give the email to the
     * Notifier immediately when processing email. If this
     * flag is non-null and true the email will not 
     * be queued. Queuing is the default behavior for
     * performance reasons, but for unit-testing its important
     * that the emails are sent out immediately.
     */
    private Boolean _immediate;

    /**
     * Default constructor.
     */
    public RedirectingEmailNotifier() {
        _immediate = null;
    }

    /**
     * Set the EmailNotifier to use to do the actual sending.
     * 
     * @param  delegate  The EmailNotifier to use to actually send the email.
     */
    public void setDelegate(EmailNotifier delegate) {
        this.delegate = delegate;
    }

    /**
     * Return the email address that should receive all redirected emails.
     */
    public String getEmailAddress() {
        return this.emailAddress;
    }
    
    /**
     * Set the email address that should receive all redirected emails.
     * 
     * @param  emailAddress  The email address that should receive all
     *                       redirected emails.
     */
    public void setEmailAddress(String emailAddress) {
        this.emailAddress = emailAddress;
    }
    
    /**
     * Return the file name that will get the email appended to it.
     */
    public String getFileName() {
        return this.fileName;
    }
    
    /**
     * Set the file name that will get the email appended to it.
     * 
     * @param fileName The file name where emails will be sent
     */
    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    /* (non-Javadoc)
     * @see sailpoint.api.EmailNotifier#sendEmailNotification(sailpoint.object.EmailTemplate, sailpoint.object.EmailOptions)
     */
    public void sendEmailNotification(SailPointContext context,
                                      EmailTemplate template,
                                      EmailOptions options)
        throws GeneralException {


        EmailOptions myOps = null;
        try {
            myOps = (EmailOptions) options.clone();
        }
        catch (CloneNotSupportedException e) {
            throw new GeneralException(e);
        }

        myOps.setFileName(this.fileName);

            // only override the "To:" if we are not redirecting to a file
        if ( this.fileName == null || this.fileName.length() == 0 )
                // the email notifier doesn't like invalid email addresses
            if ( this.emailAddress != null && this.emailAddress.length() > 0 ) {
                template.setTo(this.emailAddress);

                // Override the CC and BCC also if they are set.
                if (null != template.getCc()) {
                    template.setCc(this.emailAddress);
                }
                if (null != template.getBcc()) {
                    template.setBcc(this.emailAddress);
                }
            }

        this.delegate.sendEmailNotification(context, template, myOps);
    }

    public Boolean sendImmediate() {
        return _immediate;
    }

    /**
     * If set to true the email will NOT be queued and instead
     * it will be directly sent to the notifier. This is 
     * typically set by our spring configuration.
     */
    public void setSendImmediate(Boolean immediate) {
        _immediate = immediate;
    }
}
