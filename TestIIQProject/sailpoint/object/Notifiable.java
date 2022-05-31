/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
/*
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */

package sailpoint.object;

import java.util.Date;

import sailpoint.tools.GeneralException;


/**
 * Interface for objects that can be reminded, escalated, and expired.
 */
public interface Notifiable {

    ////////////////////////////////////////////////////////////////////////////
    //
    // General notification methods.
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Return the ID of this item.
     */
    public String getId();

    /**
     * Return a friendly name for this item.
     */
    public String getNotificationName();

    /**
     * Return the NotificationConfig for this item.
     */
    public NotificationConfig getNotificationConfig();
    
    /**
     * Return the date at which this object should next be checked for
     * reminders, escalations, or expiration. This should return null if there
     * is nothing left to process on this item.
     */
    public Date getWakeUpDate();

    /**
     * Set the date at which this object should next be checked for reminders,
     * escalations, or expiration.
     */
    public void setWakeUpDate(Date nextWakeUp);


    ////////////////////////////////////////////////////////////////////////////
    //
    // Reminders
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Get the number of reminders that have been sent.
     */
    public int getRemindersSent();

    /**
     * Increment the number of reminders that have been sent.
     */
    public void incrementRemindersSent();

    /**
     * Return the "owner" of this item. This is the user that should receive
     * reminders or be subject to escalation.
     */
    public Identity getNotificationOwner(Resolver resolver)
        throws GeneralException;


    ////////////////////////////////////////////////////////////////////////////
    //
    // Escalations
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Return whether this item escalates when the maximum number of reminders
     * has been reached. This should return false if this item kicks off the
     * escalation process differently.
     */
    public boolean isEscalateOnMaxReminders();

    /**
     * Save the given escalation error on this item.
     */
    public void saveEscalationError(InvalidEscalationTargetException error);


    ////////////////////////////////////////////////////////////////////////////
    //
    // Expirations
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Return true if this item supports expiration.
     */
    public boolean isExpirable();

    /**
     * Return true if this item is expired according to the current time and
     * the expiration date for this item.
     */
    public boolean isExpired();

    /**
     * Return the expiration date for this item.
     */
    public Date getExpirationDate();
    
    /**
     * Increment the number of escalations.
     */
    public void incrementEscalationCount();
    
    public int getEscalationCount();
}
