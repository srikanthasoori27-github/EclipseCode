/* (c) Copyright 2009 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.api;

import java.util.HashSet;

import sailpoint.object.Configuration;
import sailpoint.object.EmailTemplate;
import sailpoint.tools.GeneralException;


/**
 * The EmailSuppressor can be used to track who has received an email of a
 * particular type within a logical "session".  This can then be used to
 * suppress sending duplicate emails to the same person.
 *
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */
public class EmailSuppressor {

    ////////////////////////////////////////////////////////////////////////////
    //
    // FIELDS
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Is email suppression enabled?
     */
    private boolean enabled;

    /**
     * A set of the email keys that have been sent.
     */
    private HashSet<String> sentKeys;
    
    /**
     * The number of emails that have been suppressed.
     */
    private int emailsSuppressed;


    ////////////////////////////////////////////////////////////////////////////
    //
    // CONSTRUCTOR
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Constructor.  This will be enabled or disabled based on the system
     * configuration.
     */
    public EmailSuppressor(SailPointContext context) throws GeneralException {
        this.enabled =
            context.getConfiguration().getBoolean(Configuration.SUPPRESS_DUPLICATE_EMAILS, false);
        this.sentKeys = new HashSet<String>();
    }

    
    ////////////////////////////////////////////////////////////////////////////
    //
    // METHODS
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Return the number of emails that have been suppressed.
     */
    public int getEmailsSuppressed() {
        return this.emailsSuppressed;
    }
    
    /**
     * The given email is going to be sent to the given, return whether or not
     * the email should be sent.  If so, this is recorded so that the email will
     * not be sent again.  If not, the emailsSuppressed count is incremented.
     * This does nothing if the EmailSuppressor is not enabled.
     * 
     * @param  template   The EmailTemplate that was sent.
     * @param  recipient  The recipient of the email.
     * 
     * @return True if the email should be sent to the given recipient, false
     *         otherwise.
     */
    public boolean shouldSend(EmailTemplate template, String recipient) {

        // Short-circuit if we're not enabled.
        if (!this.enabled) {
            return true;
        }
        
        String key = createKey(template, recipient);
        boolean shouldSend = !this.sentKeys.contains(key);

        if (!shouldSend) {
            this.emailsSuppressed++;
        }
        else {
            this.sentKeys.add(key);
        }

        return shouldSend;
    }

    /**
     * Create the unique key for the given email and recipient.
     */
    private static String createKey(EmailTemplate template, String recipient) {
        return template.getId() + ":" + recipient;
    }
}
