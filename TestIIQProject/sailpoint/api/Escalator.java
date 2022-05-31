/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.api;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.google.inject.Inject;

import sailpoint.object.Attributes;
import sailpoint.object.AuditEvent;
import sailpoint.object.CertificationItem;
import sailpoint.object.Configuration;
import sailpoint.object.EmailOptions;
import sailpoint.object.EmailTemplate;
import sailpoint.object.Identity;
import sailpoint.object.InvalidEscalationTargetException;
import sailpoint.object.Notifiable;
import sailpoint.object.NotificationConfig;
import sailpoint.object.NotificationConfig.EscalationConfig;
import sailpoint.object.NotificationConfig.IConfig;
import sailpoint.object.NotificationConfig.ReminderConfig;
import sailpoint.object.Rule;
import sailpoint.object.WorkItem;
import sailpoint.server.Auditor;
import sailpoint.tools.ConvertTools;
import sailpoint.tools.DateUtil;
import sailpoint.tools.EmailException;
import sailpoint.tools.GeneralException;
import sailpoint.tools.RetryableEmailException;
import sailpoint.tools.Util;


/**
 * This is used to handle reminders, escalations, and expirations on Notifiable
 * items.  This can be called in two ways.  The first is with the general
 * handleNotification() method, which will reminder, escalate, or expire
 * appropriately based on the state of the Notifiable and the
 * NotificationConfig.  The other is that sendReminder(), escalate(), and
 * expire() can be called explicitly if we're not depending on a trigger from
 * NotificationConfig to kick of the initial action.
 *
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */
public class Escalator {

    private static Log log = LogFactory.getLog(Escalator.class);

    ////////////////////////////////////////////////////////////////////////////
    //
    // CONSTANTS
    //
    ////////////////////////////////////////////////////////////////////////////
    
    /**
     * Constants that define the names of arguments passed to 
     * EmailTemplates rendered by the scanner.
     *
     * NOTE: toward the end of 5.1 I made a consistency pass
     * over the email templates and changed several of these names.
     * The old names are still available for backward compatibility
     * but they won't be seen in the new template signatures. - jsl
     */
    static enum NotificationEmailOptions {

        // Generic names to reference the Notifier being escalated,
        // either a WorkItem or CertificationItem.  This is considered
        // deprecated for WorkItem escalations
        item,
        itemName,
      
        // the official names used for WorkItem escalation
        workItem,
        workItemName,

        // certification used by WorkItem escalation 
        certification,
        certificationName,
        
        // various properties of the thing being escalated
        // saves calling property accessors on the item
        requester,
        expiration,
        created,

        // we have historically passed "owner" here, changed to     
        // have the "Name" suffix like most other names, sadly
        // we can't make "owner" be the Identity 
        ownerName,

        // claculated properties of the escalation
        ordinalNumReminders,
        oldDueDate,
        newDueDate,
        nowDate,
        remindersRemaining,
        oldOwner,
    };
    
    /**
     * Mapping between the 5.1 official names and the 
     * names used in the past.
     */
    private static final String[][] EmailOptionDowngrades = {

        {"itemName", "ItemName"},
        {"workItemName", "WorkItemName"},
        {"requester", "Requester"},
        {"ownerName", "owner"},
        {"ownerName", "Owner"},
        {"expiration", "Expiration"},
        {"created", "Created"}
        
    };

    ////////////////////////////////////////////////////////////////////////////
    //
    // FIELDS
    //
    ////////////////////////////////////////////////////////////////////////////

    private SailPointContext context;
    private EmailSuppressor emailSuppressor;
    private int totalReminders;
    private int totalExpirations;
    private int totalEscalations;
    private int totalPushes;

    /**
     * Used to inject custom NotificationHandler
     * for unit tests.
     */
    @Inject
    private NotificationHandler notificationHandler;
    
    private EscalatorOld oldMinion;
    ////////////////////////////////////////////////////////////////////////////
    //
    // CONSTRUCTORS
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Constructor.
     */
    @Inject
    public Escalator(SailPointContext context) throws GeneralException {
        this.context = context;
        this.emailSuppressor = new EmailSuppressor(context);
        oldMinion = new EscalatorOld();
    }
    
    ////////////////////////////////////////////////////////////////////////////
    //
    // GETTERS
    //
    ////////////////////////////////////////////////////////////////////////////

    public int getTotalReminders() {
        return totalReminders;
    }

    public int getTotalExpirations() {
        return totalExpirations;
    }
    
    public int getTotalEscalations() {
        return totalEscalations;
    }
    
    public int getTotalPushes() {
        return totalPushes;
    }
    
    public int getEmailsSuppressed() {
        return this.emailSuppressor.getEmailsSuppressed();
    }
    
    /**
     * Helper class for old model Reminders and Escalations.
     * Sailpoint.object.NotificationConfig was modified to handle multiple 
     * reminders and escalations but we also have to support old model with
     * single reminder and escalation (for backward API and workflow xml compatibility).
     * This inner class encapsulates the functionality that was existing before.
     * Having a separate class also allows us to keep all deprecated apis encapsulated
     * in this class.
     */
    @SuppressWarnings("deprecation")
    private class EscalatorOld {
        
        public void handleNotification(Notifiable item) throws GeneralException {

            NotificationConfig notifConfig = item.getNotificationConfig();

            if ((null != notifConfig) && notifConfig.isRemindersEnabled()) {
                // Negative or zero max reminders means to remind forever.  This
                // will only be set to a positive number if we're escalating.
                if (((item.getRemindersSent() < notifConfig.getEscalationMaxReminders()) ||
                     (notifConfig.getEscalationMaxReminders() <= 0)) &&
                    !item.isExpired()) {
                    if (EscalatorUtil.isReminderNeeded(item, context)) {
                        sendReminder(item);
                    }
                }
                else {
                    escalateOrExpire(item, notifConfig, true);
                }
            }
            else {
                escalateOrExpire(item, notifConfig, false);
            }
        }
        
        public void sendReminder(Notifiable item) throws GeneralException {

            NotificationConfig notifConfig = item.getNotificationConfig();

            // Don't send if reminders aren't enabled.  We'll include this check
            // here since this is a public method.
            if ((null == notifConfig) || !notifConfig.isRemindersEnabled()) {
                return;
            }
            
            int currentReminders = item.getRemindersSent();
            int maxReminders = notifConfig.getEscalationMaxReminders();
        
            totalReminders++;
            
            if (log.isDebugEnabled()) {
                log.debug("Sending reminder for:\n" + item);
            }
        
            // The work item has not expired yet, so we send a reminder
            EmailTemplate reminderTemplate = getReminderEmail(item, notifConfig);
            if (null != reminderTemplate) {
                Date thisReminderDate =
                    (null != item.getWakeUpDate()) ? item.getWakeUpDate() : DateUtil.getCurrentDate();

                // Next reminder only occurs when there is a positive reminder
                // frequency (ie - this is not a one-time reminder).
                Date nextReminder = null;
                if (notifConfig.getReminderFrequency() > 0) {
                    long currentTime = DateUtil.getCurrentDate().getTime();

                    long nextReminderTime = thisReminderDate.getTime() + notifConfig.getReminderFrequency();
                    while (nextReminderTime < currentTime) {
                        nextReminderTime += notifConfig.getReminderFrequency();
                    }
                    
                    nextReminder = new Date(nextReminderTime);
                }
        
                Identity owner = item.getNotificationOwner(context);
                if ((null != owner) && (null != ObjectUtil.getEffectiveEmails(context,owner))) {

                    // If we don't have a maximum, use -1 so the template will be able
                    // to know what's going on.
                    int remindersRemaining = -1;
                    if (maxReminders > 0) {
                        remindersRemaining = maxReminders - currentReminders - 1;
                    }
        
                    try {
                        EmailOptions options =
                            buildEmailOptions(item, item.getRemindersSent() + 1, thisReminderDate, nextReminder, remindersRemaining, null);
                        if (emailSuppressor.shouldSend(reminderTemplate, options.getTo())) {
                            context.sendEmailNotification(reminderTemplate, options);
                        }
                    } catch (RetryableEmailException e) {
                        if (log.isWarnEnabled()) {
                            log.warn("A reminder was sent, but it may not reach its " + 
                                     "intended recipient because the mail server was down. " + 
                                     "The request to send an e-mail was queued. " + 
                                     "Reason for failure: " + e.getMessage(), e);
                        }
                    }
                }
        
                // Set the reminder as specified by the interval or by the expiration: whichever comes first
                item.incrementRemindersSent();
        
                item.setWakeUpDate(getNextWakeUp(item, nextReminder));
                
                if (log.isDebugEnabled()) {
                    log.debug("nextWakeupDate: " + getNextWakeUp(item, nextReminder));
                }
            }
        }
        
        public void escalate(Notifiable item) throws GeneralException {

            NotificationConfig notifConfig = item.getNotificationConfig();
            
            // Don't escalate if escalation isn't enabled.  We'll include this check
            // here since this is a public method.
            if ((null == notifConfig) || !notifConfig.isEscalationEnabled()) {
                return;
            }

            // Time to escalate!
            Rule response = notifConfig.getEscalationRule(context);
        
            if (response == null) {
                // No action required here since no escalation rule was defined.
                totalPushes++;
                
                if (log.isDebugEnabled())
                    log.debug(item + " could not be escalated because no escalation rule is defined for it.");
                
                item.setWakeUpDate(item.getExpirationDate());
            } else {
                Map<String, Object> ruleParams = new HashMap<String, Object>();
                ruleParams.put("item", item);
                // Legacy - add work item as parameter for old rules.
                ruleParams.put("workItem", item);
                
                String escalationTarget = (String) context.runRule(response, ruleParams);
                
                Identity oldOwner = item.getNotificationOwner(context);
                
                if (log.isDebugEnabled())
                    log.debug("Escalating " + item + " from " + ((null != oldOwner) ? 
                              oldOwner.getName() : null) + " to " + escalationTarget);
        
                if (escalationTarget == null) {
                    if (log.isDebugEnabled())
                        log.debug(item + " cannot escalate further.  It will now expire.");
                    
                    expire(item);
                } else {
                    totalEscalations++;
                    try {
                        // Calculate the wakeup dates.
                        long wakeUpInterval = notifConfig.getEscalationFrequencyMillis();
        
                        // Fallback to the reminder frequency if escalation frequency is not specified.
                        if ((wakeUpInterval <= 0) && (notifConfig.getReminderFrequency() > 0)) {
                            wakeUpInterval = notifConfig.getReminderFrequency();
                        }
        
                        // If still not specified, use the system config value.
                        if (wakeUpInterval <= 0) {
                            Configuration config = context.getConfiguration();
                            wakeUpInterval = config.getLong(Configuration.DEFAULT_WAKEUP_INTERVAL);
                        }
                        if (log.isDebugEnabled()) {
                            log.debug("wakeupInterval: " + wakeUpInterval);
                        }

                        Date thisWakeUp =
                            (null != item.getWakeUpDate()) ? item.getWakeUpDate() : DateUtil.getCurrentDate();
                        Date nextWakeUp = new Date(thisWakeUp.getTime() + wakeUpInterval);
        
                        // Cache the old owner's name for the e-mail 
                        String oldOwnerName = null;
                        if (null != oldOwner) {
                            oldOwnerName = oldOwner.getDisplayableName();
                        }
                        Identity newOwner = context.getObjectByName(Identity.class, escalationTarget);
        
                        if (null == newOwner) {
                            // Just log a warning.  We'll continue trying to escalate in hopes that
                            // the rule gets corrected and an escalation will eventually happen.  If
                            // not, this will eventually expire.
                            if (log.isWarnEnabled())
                                log.warn("Escalation rule returned invalid owner: " + escalationTarget);
                        }
                        else {
                            // Let the notification handler do the escalation.
                            getNotificationHandler(item).executeEscalation(item, newOwner);
                            item.incrementEscalationCount();
                        
                            // this may have changed
                            newOwner = item.getNotificationOwner(context);
        
                            // audit the change
                            Auditor.log(AuditEvent.ActionEscalate, oldOwnerName, newOwner.getName());
        
                            // Send an e-mail to the new owner
                            EmailTemplate ownerTemplate = getEscalationEmail(item, notifConfig);
        
                            if (ownerTemplate != null) {
                                EmailOptions options =  buildEmailOptions(item, item.getRemindersSent() + 1, thisWakeUp, nextWakeUp, -1, null);
                                Map<String, Object> escalationOptions = new HashMap<String, Object>();
                                escalationOptions.put(NotificationEmailOptions.oldOwner.toString(), oldOwnerName);
                                options.addVariables(escalationOptions);
                                if (emailSuppressor.shouldSend(ownerTemplate, options.getTo())) {
                                    context.sendEmailNotification(ownerTemplate, options);
                                }
                            }
                        }
        
                        // Reset the wakeup date.
                        nextWakeUp = getNextWakeUp(item, nextWakeUp);
                        if (log.isDebugEnabled()) {
                            log.debug("nextWakeup: " + nextWakeUp);
                        }
                        item.setWakeUpDate(nextWakeUp);
                    } catch (GeneralException e) {
                        if (log.isErrorEnabled())
                            log.error("Error trying to escalate " + item + ".  Item will now expire.", e);
                        
                        item.saveEscalationError(new InvalidEscalationTargetException(item, escalationTarget, e));
        
                        if (item.isExpirable()) {
                            expire(item);
                        }
                    }
                }
            }
        }
        
        /**
         * Either escalate or expire the given item.
         */
        private void escalateOrExpire(Notifiable item, NotificationConfig notifConfig,
                                      boolean maxRemindersTrigger)
            throws GeneralException {

            // If we get to the max number of reminders but don't escalate on max
            // reminders, just set the wake up date.
            if (maxRemindersTrigger && !item.isEscalateOnMaxReminders()) {
                item.setWakeUpDate(null);
            }
            else {
                // If we got here, we're OK to escalate or expire.
                if ((null != notifConfig) && notifConfig.isEscalationEnabled() &&
                    !item.isExpired()) {
                    escalate(item);
                }
                else if (item.isExpirable()) {
                    expire(item);
                }
                else {
                    // Should have expired, but the item is not expirable.  Just set
                    // the wake up date to null so we don't keep processing it.
                    item.setWakeUpDate(null);
                }
            }
        }
        
        /**
         * Return the reminder email either specified in the given
         * NotificationConfig or pulled from system config.
         * This is to support old code.
         */
        @Deprecated
        private EmailTemplate getReminderEmail(Notifiable item,
                                               NotificationConfig notifConfig)
            throws GeneralException {
            
            EmailTemplate reminderTemplate = notifConfig.getReminderEmailTemplate(context);

            // Get a default template if none can be found for this work item
            if (reminderTemplate == null) {
                reminderTemplate = fetchDefaultReminderEmailTemplate();
            }

            return reminderTemplate;
        }
        
        /**
         * Return the escalation email either specified in the given
         * NotificationConfig or pulled from system config.
         */
        private EmailTemplate getEscalationEmail(Notifiable item,
                                                 NotificationConfig notifConfig)
            throws GeneralException {
            
            EmailTemplate ownerTemplate = notifConfig.getEscalationEmailTemplate(context);

            if (ownerTemplate == null) {
                Configuration sailpointConfig = context.getConfiguration();
                String defaultTemplateName = sailpointConfig.getString("escalationEmailTemplate");
                ownerTemplate = context.getObjectByName(EmailTemplate.class, defaultTemplateName);
                if (ownerTemplate == null) {
                    if (log.isErrorEnabled()) {
                        log.error("The default escalation email template, " + defaultTemplateName + 
                                  ", could not be found.  Since no e-mail template was specified, " +
                                  "no escalation notifications can be sent for work item " + item);
                    }
                }
            }

            return ownerTemplate;
        }
    }

    ////////////////////////////////////////////////////////////////////////////
    //
    // PUBLIC METHODS
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Remind, escalate, or expire the given item appropriately depending on its
     * current state and the given NotificationConfig.
     */
    public void handleNotification(Notifiable item) throws GeneralException {

        if (isOld(item)) {
            if (log.isDebugEnabled()) {log.debug("handling old notifications");}
            oldMinion.handleNotification(item);
            return;
        }

        if (log.isDebugEnabled()) {log.debug("==============================\nhandling new notifications.\n======================================");}
        
        if (isExpired(item)) {
            if (log.isDebugEnabled()) {
                log.debug(item.getNotificationName() + " has expired.");
            }

            // Don't expire WorkItems twice
            if (item instanceof WorkItem && ((WorkItem)item).getState() == WorkItem.State.Expired) {
                return;
            }
            expire(item);

            return;
        }

        //Sanity Check
        if (Util.isEmpty(item.getNotificationConfig().getConfigs())) {
            log.error("Could not find NotificationConfig for item: " + item);
            return;
        }
        if (!item.getNotificationConfig().isEnabled()) {
            if (log.isInfoEnabled()) {
                log.info("Notifications are not enabled for item: " + item);
            }
            return;
        }
        
        int index = item.getNotificationConfig().findConfigToUseIndex();
        if (index == -1) {
            log.error("Could not find notification to use for item: " + item);
            expire(item);
            return;
        }
        
        IConfig config = item.getNotificationConfig().getConfigs().get(index);
        if (config.isExhausted()) {
            if (log.isDebugEnabled()) {log.debug("item: " + item.getNotificationName() + ", can't use the last config as it is exhausted.");}
            item.setWakeUpDate(null);
            return;
        }
        
        if (log.isDebugEnabled()) {log.debug("configIndex: " + index);}
        
        IConfig nextConfig = null;
        int nextIndex = item.getNotificationConfig().findNextConfigIndex(index);
        if (nextIndex != -1) {
            nextConfig = item.getNotificationConfig().getConfigs().get(nextIndex);
        }
        if (log.isDebugEnabled()) {log.debug("nextConfigIndex: " + nextIndex);}
        
        if (log.isTraceEnabled()) {
            log.trace("config: " + config);
            log.trace("nextConfig: " + nextConfig);
        }
        
        if (config instanceof ReminderConfig) {
            if (EscalatorUtil.isReminderNeeded(item, context)) {
                handleReminder(item, (ReminderConfig)config, nextConfig);
            }
        } else {
            handleEscalation(item, (EscalationConfig) config, nextConfig);
        }
    }

    /**
     * Calculate on the basis of the end of day. On the day of the expiration the work item
     * doesn't expire until midnight.
     */
    private boolean isExpired(Notifiable notifiable) {
        Date expirationDate = notifiable.getExpirationDate();
        if (expirationDate == null) {
            return false;
        }

        return Util.getEndOfDay(expirationDate).before(Util.getEndOfDay(DateUtil.getCurrentDate()));
    }

    
    
    /**
     * Send a reminder for the given item, update the state of the item, and set
     * the next wake up time.
     * 
     * Added Comments: Basically this start the chain of notifications.
     * From what I can see this method is only called from 
     * ContinuousCertificationer.launchStateChangeAction.
     * That method does not assume a new model for notifications.
     * 
     */
    public void sendReminder(Notifiable item) throws GeneralException {

        if (isOld(item)) {
            oldMinion.sendReminder(item);
            return;
        }
        
        if (!item.getNotificationConfig().isEnabled()) {
            if (log.isDebugEnabled()) {log.debug("Notifiations not enabled, returning.");}
            return;
        }

        int index = findFirstEnabledReminderIndex(item);
        if (index == -1) {
            if (log.isDebugEnabled()) {log.debug("No reminders are enabled, returning.");}
            return;
        }
        ReminderConfig reminderConfig = (ReminderConfig)item.getNotificationConfig().getConfigs().get(index);
        
        IConfig nextConfig = null;
        int nextIndex = item.getNotificationConfig().findNextConfigIndex(index);
        if (nextIndex != -1) {
            nextConfig = item.getNotificationConfig().getConfigs().get(nextIndex);
        }
        
        handleReminder(item, reminderConfig, nextConfig);
    }

    
    /**
     * Escalate the given item, update the state of the item, and set the next
     * wake up time.
     *
     * Added Comments: Basically this start the chain of notifications.
     * From what I can see this method is only called from 
     * ContinuousCertificationer.launchStateChangeAction.
     * That method does not assume a new model for notifications.
     * 
     */
    public void escalate(Notifiable item) throws GeneralException {

        if (isOld(item)) {
            oldMinion.escalate(item);
            return;
        }
        
        if (!item.getNotificationConfig().isEnabled()) {
            if (log.isDebugEnabled()) {log.debug("Notifiations not enabled, returning.");}
            return;
        }

        int index = getFirstEnabledEscalationIndex(item);
        if (index == -1) {
            if (log.isDebugEnabled()) {log.debug("No escalations are enabled. Returning.");}
            return;
        }
        EscalationConfig escalationConfig = (EscalationConfig) item.getNotificationConfig().getConfigs().get(index);
        if (escalationConfig == null) {
            if (log.isDebugEnabled()) {log.debug("No reminders are enabled, returning");}
            return;
        }
        IConfig nextConfig = null;
        int nextIndex = item.getNotificationConfig().findNextConfigIndex(index);
        if (nextIndex != -1) {
            nextConfig = item.getNotificationConfig().getConfigs().get(nextIndex);
        }

        handleEscalation(item, escalationConfig, nextConfig);
    }

    /**
     * Expire the given item, update the state of the item, and clear the next
     * wake up time.
     * 
     * Added Comments:
     * I don't see this public method being called from anywhere else.
     * 
     */
    public void expire(Notifiable item) throws GeneralException {

        // Time to expire
        if (item.isExpirable()) {
            totalExpirations++;
            
            if (log.isDebugEnabled()) {log.debug(item.getNotificationName() + " is past its expiration date.  It will now expire.");}

            // jsl - items without owners is a new mechanism to send
            // resume events to cases, don't log these
            // (well not any more, but still be careful)
            Identity owner = item.getNotificationOwner(this.context);
            if (owner != null) {
                String ownerName = (owner != null) ? owner.getName() : "???";
                Auditor.log(AuditEvent.ActionExpire, ownerName);
            }

            // Let the notification handler do the expiration.
            getNotificationHandler(item).executeExpiration(item);
        }

        item.setWakeUpDate(null);
    }

    
    ////////////////////////////////////////////////////////////////////////////
    //
    // PRIVATE METHODS
    //
    ////////////////////////////////////////////////////////////////////////////

    private boolean isOld(Notifiable item) {
        return (item.getNotificationConfig() == null || !item.getNotificationConfig().isNew());
    }
    
    //TODO: Think about using new model for continuous certs.
    private int findFirstEnabledReminderIndex(Notifiable item) {

        for (int i=0; i<item.getNotificationConfig().getConfigs().size(); ++i) {
            IConfig config = item.getNotificationConfig().getConfigs().get(i);
            if (config instanceof ReminderConfig && config.isEnabled()) {
                return i;
            }
        }

        return -1;
    }

    //TODO: Think about using new model for continuous certs.
    private int getFirstEnabledEscalationIndex(Notifiable item) {

        for (int i=0; i<item.getNotificationConfig().getConfigs().size(); ++i) {
            IConfig config = item.getNotificationConfig().getConfigs().get(i);
            if (config instanceof EscalationConfig && config.isEnabled()) {
                return i;
            }
        }

        return -1;
    }
    
    private String getEscalationTarget(Notifiable item, EscalationConfig escalationConfig) throws GeneralException {
        
        Rule escalationRule = getEscalationRule(escalationConfig);
        if (escalationRule == null) {
            // No action required here since no escalation rule was defined.
            totalPushes++;
            
            if (log.isDebugEnabled()) {log.debug(item + " could not be escalated because no escalation rule is defined for it.");}
            
            item.setWakeUpDate(item.getExpirationDate());
            return null;
        }        
        
        
        Map<String, Object> ruleParams = new HashMap<String, Object>();
        ruleParams.put("item", item);
        // Legacy - add work item as parameter for old rules.
        ruleParams.put("workItem", item);
        
        return (String) context.runRule(escalationRule, ruleParams);
    }
    
    private Rule getEscalationRule(EscalationConfig escalationConfig) throws GeneralException {
        String escalationRuleName = escalationConfig.getEscalationRuleName();
        if (escalationRuleName == null) {
            return null;
        } else {
            return context.getObjectByName(Rule.class, escalationRuleName);
        }
    }
    

    /**
     * Build the EmailOptions for reminder or escalation email for the given
     * item.  This allows the NotificationHandler to plug additional variables
     * into the context of the email.
     */
    private EmailOptions buildEmailOptions(Notifiable item,
                                           int numRemindersSent,
                                           Date oldDueDate,
                                           Date newDueDate,
                                           int remindersRemaining, 
                                           String ccVal)
        throws GeneralException {

        Map<String, Object> emailOptionsMap = new HashMap<String, Object>();

        // Pass the Notifiable as a generic "item".  In practice this shouldn't
        // be used, at least not for WorkItem esclaations where everyone
        // expects "workitem" and "workitemName" that will be added by
        // WorkItemNotificationHandler - jsl
        emailOptionsMap.put(NotificationEmailOptions.item.toString(), item);
        emailOptionsMap.put(NotificationEmailOptions.itemName.toString(), item.getNotificationName());

        Identity owner = item.getNotificationOwner(this.context);
        if (owner == null)
            throw new GeneralException("Unable to escalate, no owner");
        emailOptionsMap.put(NotificationEmailOptions.ownerName.toString(), owner.getName());

        String whichReminder = ConvertTools.convertToOrdinal(numRemindersSent);
        emailOptionsMap.put(NotificationEmailOptions.ordinalNumReminders.toString(), whichReminder);
        emailOptionsMap.put(NotificationEmailOptions.oldDueDate.toString(), oldDueDate);
        emailOptionsMap.put(NotificationEmailOptions.newDueDate.toString(), newDueDate);
        emailOptionsMap.put(NotificationEmailOptions.remindersRemaining.toString(), String.valueOf(remindersRemaining));
        emailOptionsMap.put(NotificationEmailOptions.nowDate.toString(), DateUtil.getCurrentDate());
        emailOptionsMap.put("recipient", owner);
        Identity sender = null;
        if (item instanceof CertificationItem) {
            sender =((CertificationItem)item).getCertification().getOwner();
            emailOptionsMap.put("sender", sender);
        }
        if (item instanceof WorkItem) {
            sender =((WorkItem)item).getRequester();
            emailOptionsMap.put("sender", sender);
        }
        
        Date exp = item.getExpirationDate();
        if (exp != null)
            emailOptionsMap.put(NotificationEmailOptions.expiration.toString(), exp);
      
        EmailOptions ops =
            new EmailOptions(ObjectUtil.getEffectiveEmails(this.context,owner),emailOptionsMap);
            
        if (ccVal != null){
                ops.setCc(ccVal);
        }
        // Let the notification handler plug in some options.
        getNotificationHandler(item).addEmailOptions(item, ops);

        // kludge: replicate some of the names for backward compatibility
        Map<String,Object> emops = ops.getVariables();
        for (int i = 0 ; i < EmailOptionDowngrades.length ; i++) {
            String[] entry = EmailOptionDowngrades[i];
            Object value = emops.get(entry[0]);
            if (value != null)
                emops.put(entry[1], value);
        }

        return ops;
    }

    /**
     * Factory method to return the correct notification handler for the given
     * item.
     */
    private NotificationHandler getNotificationHandler(Notifiable item)
        throws GeneralException {
        
        if (notificationHandler != null) {
            return notificationHandler;
        }
        
        NotificationHandler handler = null;

        if (item instanceof WorkItem) {
            handler = new WorkItemNotificationHandler(this.context);
        }
        else if (item instanceof CertificationItem) {
            handler = new CertificationItemNotificationHandler(this.context, this.emailSuppressor);
        }
        else {
            throw new GeneralException("Unknown item type: " + item);
        }
        
        return handler;
    }
    
    private EmailTemplate fetchDefaultReminderEmailTemplate() throws GeneralException {
        
        EmailTemplate reminderTemplate;
        Configuration sailpointConfig = context.getConfiguration();
        String defaultTemplateName = sailpointConfig.getString("reminderEmailTemplate");
        reminderTemplate = context.getObjectByName(EmailTemplate.class, defaultTemplateName);                
        
        if (reminderTemplate == null) {
            if (log.isErrorEnabled()) {
                log.error("The default reminder email template, " + defaultTemplateName + 
                          ", could not be found.  Since no e-mail template was specified, " +
                          "no reminder notifications can be sent");
            }
        }
        return reminderTemplate;
    }

    //Bug 18907: Don't set Cc value on template.
    private EmailTemplate getEmailTemplate(Notifiable item, IConfig config) throws GeneralException {
        
        EmailTemplate template = null;
        
        String templateName = config.getEmailTemplateName();
        if (templateName == null) {
            template = fetchDefaultReminderEmailTemplate();
        } else {
            template = context.getObjectByName(EmailTemplate.class, templateName);
            if (template == null) {
                template = fetchDefaultReminderEmailTemplate();
            }
        }
        
        return template;
    }
    
    private String getCc(Notifiable item, IConfig config) throws GeneralException {
        
        List<String> emailAddresses = new ArrayList<String>();
        String ccVal = null;

        List<String> recipientNames = config.getAdditionalRecipientNames();
        if (!Util.isEmpty(recipientNames)) {
            emailAddresses.addAll(getEmailAddresses(recipientNames));
        }
        
        emailAddresses.addAll(getEmailAddressesFromRule(item, config));

        Util.removeDuplicates(emailAddresses);
        if (emailAddresses.size() != 0) {
            ccVal = Util.join(emailAddresses, ",");
        }
        return ccVal;
    }
    
    private List<String> getEmailAddressesFromRule(Notifiable item, IConfig config) throws GeneralException {
        
        List<String> result = new ArrayList<String>();
        
        String ruleName = config.getAdditionalRecipientsRuleName();
        if (!Util.isNullOrEmpty(ruleName)) {
            Rule rule = context.getObjectByName(Rule.class, ruleName);
            if (rule != null) {
                result.addAll(getEmailAddresses(getRecipientsFromRule(item, rule)));
            } else {
                log.error("could not find rule: " + ruleName);
            }
        }
        
        return result;
    }
    
    private List<String> getEmailAddresses(List<String> identityNames) throws GeneralException {

        List<String> result = new ArrayList<String>();
        
        for (String identityName : identityNames) {
            Identity identity = context.getObjectByName(Identity.class, identityName);
            if (identity != null) {
                List<String> addresses = ObjectUtil.getEffectiveEmails(context, identity);
                if (!Util.isEmpty(addresses)) {
                    result.addAll(addresses);
                }
            } else {
                log.error("Could not find identity with name: " + identityName);
            }
        }
        
        return result;
    }
    
    @SuppressWarnings({ "unchecked", "rawtypes" })
    private List<String> getRecipientsFromRule(Notifiable item, Rule rule) throws GeneralException {

        List<String> result = new ArrayList<String>();
        
        Map<String, Object> ruleParams = new HashMap<String, Object>();
        ruleParams.put("item", item);
        Object val = context.runRule(rule, ruleParams);
        if (val != null) {
            if (val instanceof String) {
                result.add((String) val);
            } else if (val instanceof List) {
                result.addAll((List) val);
            } else {
                log.error("rule: " + rule.getName() + " returns unsupported value: " + val);
            }
        }
        
        return result;
    }
    
    /**
     * Return the next wake up date or the expiration date depending on which
     * comes first.
     */
    private Date getNextWakeUp(Notifiable item, Date nextWakeUp) {

        Date expiration = item.getExpirationDate();
        
        if (item.isExpirable() 
                &&
            (expiration != null) 
                && 
            (expiration.getTime() != 0) 
                && 
            ((null == nextWakeUp) || expiration.before(nextWakeUp))) {

            //IIQETN-5404 :- if expiration date is before to nextWakeUp date,
            //we have to advance the time to the end of the day to avoid issues
            //getting multiple notifications the day of expiration.
            //why? it turns out that we are using the following filter to retrieve
            //the work items that will need notifications:
            //qo.add(Filter.lt("wakeUpDate", DateUtil.getCurrentDate()));
            //The problem of that filter is that we are using the CurrentDate
            //and at that moment (after the notification has reached the expiration
            //date) the wakeUpDate will be less than the currentDate by hours.
            Calendar cal = Calendar.getInstance();
            cal.setTime(expiration);
            cal.set(Calendar.HOUR_OF_DAY, 23);
            cal.set(Calendar.MINUTE, 59);
            cal.set(Calendar.SECOND, 59);

            nextWakeUp = cal.getTime();
        }

        return nextWakeUp;
    }

    private void handleReminder(Notifiable item, ReminderConfig reminderConfig, IConfig nextConfig) throws GeneralException {
        
        Date lastNotificationDate = (item.getWakeUpDate() == null) ? DateUtil.getCurrentDate() : item.getWakeUpDate();
        Date nextNotificationDate = item.getNotificationConfig().calculateNextNotificationDate();
        if (log.isDebugEnabled()) {
            log.debug("handleReminder: now: " + DateUtil.getCurrentDate() + ", lastNotificationDate: " +  lastNotificationDate + ", nextNotificationDate: " + nextNotificationDate);
        }

        // The work item has not expired yet, so we send a reminder
        // Bug 18907: set cc value on EmailOptions, not EmailTemplate
        EmailTemplate reminderTemplate = getEmailTemplate(item, reminderConfig);
        String ccVal = getCc(item, reminderConfig);
        
        
        if (reminderTemplate == null) {
            if (log.isDebugEnabled()) {log.debug("Could not find reminderTemplate for item: " + item);}
            item.setWakeUpDate(getNextWakeUp(item, nextNotificationDate));
            return;
        }

        if (log.isTraceEnabled()) {log.trace("Sending reminder for " + item);}
        if (log.isDebugEnabled()) {log.debug("Sending reminder for " + item.getNotificationName());}
    
        
        Identity owner = item.getNotificationOwner(this.context);
        // If we don't have a maximum, use -1 so the template will be able
        // to know what's going on.
        int remindersRemaining = calculateRemindersRemaining(reminderConfig, nextConfig);
        if ((owner != null) && !Util.isEmpty(ObjectUtil.getEffectiveEmails(this.context, owner))) {
            totalReminders++;
            sendReminder(item, reminderConfig.getCount()+1, reminderTemplate, lastNotificationDate, nextNotificationDate, remindersRemaining, ccVal);
        }

        item.incrementRemindersSent();
        reminderConfig.setCount(reminderConfig.getCount() + 1);
        // Set the reminder as specified by the interval or by the expiration: whichever comes first
        item.setWakeUpDate(getNextWakeUp(item, nextNotificationDate));
    }

    private void sendReminder(Notifiable item, int numRemindersSent, EmailTemplate reminderTemplate, Date lastNotificationDate, Date nextNotificationDate, int remindersRemaining, String ccVal) throws GeneralException, EmailException {
        
        try {
            EmailOptions options = buildEmailOptions(item, numRemindersSent, lastNotificationDate, nextNotificationDate, remindersRemaining, ccVal);
            if (this.emailSuppressor.shouldSend(reminderTemplate, options.getTo())) {
                context.sendEmailNotification(reminderTemplate, options);
            }
        } catch (RetryableEmailException e) {
            if (log.isWarnEnabled()) {
                log.warn("A reminder was sent, but it may not reach its " + 
                         "intended recipient because the mail server was down. " + 
                         "The request to send an e-mail was queued. " + 
                         "Reason for failure: " + e.getMessage(), e);
            }
        }
    }

    private int calculateRemindersRemaining(ReminderConfig reminderConfig, IConfig nextConfig) {
        
        int remindersRemaining = -1;
        if (nextConfig instanceof EscalationConfig) {
            EscalationConfig nextEscalationConfig = (EscalationConfig) nextConfig;
            if (nextEscalationConfig.getMaxReminders() > 0) {
                remindersRemaining = nextEscalationConfig.getMaxReminders() - reminderConfig.getCount() - 1;
            }
        }
        return remindersRemaining;
    }

    private void handleEscalation(Notifiable item, EscalationConfig escalationConfig, IConfig nextConfig) throws GeneralException {
        
        Date lastNotificationDate = (item.getWakeUpDate() == null) ? DateUtil.getCurrentDate() : item.getWakeUpDate();
        Date nextNotificationDate = item.getNotificationConfig().calculateNextNotificationDate();
        if (log.isDebugEnabled()) {
            log.debug("handleEscalation: now: " + DateUtil.getCurrentDate() + ", lastNotificationDate: " +  lastNotificationDate + ", nextNextNotificationDate: " + nextNotificationDate);
        }
        
        String escalationTarget = getEscalationTarget(item, escalationConfig);
        if (escalationTarget == null) {
            if (log.isDebugEnabled()) {
                log.debug("Escalation target not found expiring");
            }
            expire(item);
            return;
        }
        
        escalate(item, escalationConfig, escalationTarget, lastNotificationDate, nextNotificationDate);
    }
    
    private void escalate(Notifiable item, EscalationConfig escalationConfig, String escalationTarget, Date lastNotificationDate, Date nextNotificationDate) throws GeneralException {
        
        Identity newOwner = context.getObjectByName(Identity.class, escalationTarget);
        if (null == newOwner) {
            // Just log a warning.  We'll continue trying to escalate in hopes that
            // the rule gets corrected and an escalation will eventually happen.  If
            // not, this will eventually expire.
            if (log.isWarnEnabled()) {log.warn("Escalation rule returned invalid owner: " + escalationTarget);}
            item.setWakeUpDate(getNextWakeUp(item, nextNotificationDate));
            return;
        }

        Identity oldOwner = item.getNotificationOwner(this.context);
        if (oldOwner != null && oldOwner.getName().equals(newOwner.getName())) {
            // Escalating to the same person.
            if (log.isWarnEnabled()) {log.warn("Escalation rule returned the same owner: " + escalationTarget);}
            item.setWakeUpDate(getNextWakeUp(item, nextNotificationDate));
            return;
        }
        
        if (log.isTraceEnabled()) {log.trace("Sending escalation for " + item);}
        if (log.isDebugEnabled()) {log.debug("Sending escalation for " + item.getNotificationName());}
    
        try {
            performEscalation(item, escalationConfig, newOwner, oldOwner, lastNotificationDate, nextNotificationDate);
            totalEscalations++;
            escalationConfig.setCount(escalationConfig.getCount() + 1);
        } catch (GeneralException e) {
            log.error("Error trying to escalate " + item + ".  Item will now expire.", e);
            item.saveEscalationError(new InvalidEscalationTargetException(item, escalationTarget, e));
            expire(item);
        }
    }
    
    /**
     * Does the real escalation.
     * This method assumes that all
     * necessary tests have been made
     * to make sure that all inputs
     * are valid.
     */
    private void performEscalation(Notifiable item, EscalationConfig escalationConfig, Identity newOwner, Identity oldOwner, Date lastNotificationDate, Date nextNotificationDate) throws GeneralException, EmailException {

        // Cache the old owner's name for the e-mail 
        String oldOwnerName = null;
        if (null != oldOwner) {
            oldOwnerName = oldOwner.getDisplayableName();
        }

        // Let the notification handler do the escalation.
        getNotificationHandler(item).executeEscalation(item, newOwner);
        item.incrementEscalationCount();

        // this may have changed
        newOwner = item.getNotificationOwner(this.context);

        // audit the change
        AuditEvent event = new AuditEvent();
        event.setAction(AuditEvent.ActionEscalate);
        event.setTarget(oldOwnerName);
        event.setString1("New Owner: " + newOwner.getName());
        Attributes<String, Object> attributes = new Attributes<String, Object>();
        if (item instanceof WorkItem) {
            WorkItem workItem = (WorkItem) item;
            attributes.put("Type", workItem.getType());
            attributes.put("Description", workItem.getDescription());
            attributes.put("Name", workItem.getName());
            attributes.put("WorkItemId", workItem.getId());
        } else {
            attributes.put("Id", item.getId());
            attributes.put("Name", item.getNotificationName());
        }
        event.setAttributes(attributes);
        Auditor.log(event);

        // Send an e-mail to the new owner
        // Bug 18907: set cc value on EmailOptions, not EmailTemplate
        EmailTemplate emailTemplate = getEmailTemplate(item, escalationConfig);
        String ccVal = getCc(item, escalationConfig);
        if (emailTemplate != null) {
            EmailOptions options =  buildEmailOptions(item, escalationConfig.getCount()+1, lastNotificationDate, nextNotificationDate, -1, ccVal);
            Map<String, Object> escalationOptions = new HashMap<String, Object>();
            escalationOptions.put(NotificationEmailOptions.oldOwner.toString(), oldOwnerName);
            options.addVariables(escalationOptions);
            if (emailSuppressor.shouldSend(emailTemplate, options.getTo())) {
                context.sendEmailNotification(emailTemplate, options);
            }
        }

        // Reset the wakeup date.
        item.setWakeUpDate(getNextWakeUp(item, nextNotificationDate));
    }
}
