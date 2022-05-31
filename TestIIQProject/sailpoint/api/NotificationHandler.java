/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.api;

import sailpoint.object.EmailOptions;
import sailpoint.object.Identity;
import sailpoint.object.Notifiable;
import sailpoint.tools.GeneralException;


/**
 * This interface should be implemented to handle reminders, escalations, and
 * expirations for a given type of Notifiable.  This is used by the Escalator,
 * and subclasses will be returned by the Escalator.getNotificationHandler()
 * factory method.
 *
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */
public interface NotificationHandler {

    /**
     * Add variables to the given email options for reminder and escalation
     * emails.
     * 
     * @param  item     The Notifiable for which to add options.
     * @param  options  The EmailOptions to which to add information.
     */
    public void addEmailOptions(Notifiable item, EmailOptions options)
        throws GeneralException;

    /**
     * Escalate the given Notifiable to the given new onwer.  This should just
     * do the escalation, and does not need to worry about sending out an
     * escalation email.
     * 
     * @param  item      The Notifiable being escalated.
     * @param  newOwner  The new owner for the item.
     */
    public void executeEscalation(Notifiable item, Identity newOwner)
        throws GeneralException;

    /**
     * Expire the given item if the item is expirable.
     * 
     * @param  item  The Notifiable to expire.
     */
    public void executeExpiration(Notifiable item) throws GeneralException;
}
