/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
/*
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */

package sailpoint.object;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.SailPointContext;
import sailpoint.service.certification.schedule.NotificationConfigDTO;
import sailpoint.service.suggest.SuggestHelper;
import sailpoint.tools.DateUtil;
import sailpoint.tools.GeneralException;
import sailpoint.tools.JsonHelper;
import sailpoint.tools.Util;
import sailpoint.tools.xml.AbstractXmlObject;
import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;


/**
 * A class holding information about when and how to execute notifications,
 * reminders, escalations, etc...  There are three modes for triggering the
 * initial reminder or escalation:
 * 
 * <ol>
 *   <li>Time after start: This uses an amount of milliseconds after the item
 *       is started to kick of the first reminder/escalation.</li>
 *   <li>Time before expiration: This counts backwards from a specified
 *       expiration time to determine when to kick off the first reminder or
 *       escalation.</li>
 *   <li>Max number of reminders: This is only applicable for escalations when
 *       reminders are enabled.  After a max number of reminders have been sent,
 *       the first escalation will be kicked off.</li>
 * </ol>
 * 
 * IMPORTANT ADDENDUM:
 * 
 * This model has been updated to support multiple reminders and escalations
 * per notificationconfig.
 * 
 * Initially only one reminder config and one escalation config were supported.
 * Now there can be multiples of these. There are a lot of deprecated properties
 * to maintain backward compatibility in order to support workflows etc from before.
 * 
 */
@XMLClass
public class NotificationConfig extends AbstractXmlObject {

    private static final long serialVersionUID = 3814947741524623984L;
    private static final Log log = LogFactory.getLog(NotificationConfig.class);

    private final static String ARG_NOTIFICATION_CONFIGS = "configs";
    private final static String ARG_NOTIFICATION_TYPE = "type";
    private final static String ARG_BEFORE = "before";
    private final static String ARG_START_HOW_MANY_DAYS = "startHowManyDays";
    private final static String ARG_ONCE_EVERY_DAYS = "onceEveryHowManyDays";
    private final static String ARG_EMAIL_TEMPLATE = "emailTemplate";
    private final static String ARG_ONCE = "once";
    private final static String ARG_MAX_REMINDERS = "maxReminders";
    private final static String ARG_ESCALATION_RULE = "escalationRule";
    private final static String ARG_RECIPIENTS_RULE = "recipientsRule";
    private final static String ARG_RECIPIENTS = "additionalRecipients";

    public interface IConfig {
        boolean isEnabled();
        void setEnabled(boolean val);
        boolean isBefore();
        void setBefore(boolean val);
        long getMillis();
        void setMillis(long val);
        String getEmailTemplateName();
        void setEmailTemplateName(String val);
        long getFrequency();
        void setFrequency(long val);
        boolean isAdditionalRecipientsPresent();
        void setAdditionalRecipientsPresent(boolean val);
        List<String> getAdditionalRecipientNames();
        void setAdditionalRecipientNames(List<String> val);
        String getAdditionalRecipientsRuleName();
        void setAdditionalRecipientsRuleName(String val);
        int getCount();
        void setCount(int val);
        boolean isExhausted();
        // This method should only be used when copying count values. Only applies to IConfigs already used.
        boolean isSame(IConfig otherConfig);
        public Date calculateStartDate(Date notificationStartDate, Date notificationEndDate, IConfig previousConfig);
        public Date calculateNextNotificationDate(Date lastNotificationDate);
    }
    
    public static abstract class ConfigBase extends AbstractXmlObject implements IConfig {

        private static final long serialVersionUID = 9210558827138870278L;

        private boolean enabled;
        /**
         * true if millis is before end.
         * false if millis is after start.
         */
        private boolean before;
        
        /**
         * how many milliseconds (before or after)
         */
        private long millis;
        
        private String emailTemplateName;

        /**
         * repeat how many milliseconds
         */
        private long frequency;
        
        private boolean additionalRecipientsPresent;
        
        /**
         * choose identities from a list
         */
        private List<String> additionalRecipientNames;
        
        /**
         *  rule to select additional recipients
         */
        private String additionalRecipientsRuleName;
        
        /**
         * How many times has the notification(reminder/escalation) been sent
         */
        private int count;

        protected ConfigBase() {}

        /**
         * Constructor to set shared items based on the provided map data.
         * @param config Map with ConfigBase items.
         */
        protected ConfigBase (Map<String, Object> config) {
            setEnabled(true);

            if (config.containsKey(ARG_ONCE_EVERY_DAYS)) {
                setFrequency(Util.otoi(config.get(ARG_ONCE_EVERY_DAYS)) * Util.MILLI_IN_DAY);
            }
            if (config.containsKey(ARG_START_HOW_MANY_DAYS)) {
                setMillis(Util.otoi(config.get(ARG_START_HOW_MANY_DAYS)) * Util.MILLI_IN_DAY);
            }
            if (config.containsKey(ARG_BEFORE)) {
                setBefore(Util.otob(config.get(ARG_BEFORE)));
            }

            if (config.get(ARG_EMAIL_TEMPLATE) != null) {
                Object templateInfo = config.get(ARG_EMAIL_TEMPLATE);
                String name = SuggestHelper.extractNameOrIdFromSuggestObject(templateInfo);
                if (name != null) {
                    setEmailTemplateName(name);
                }
            }

            if (config.get(ARG_RECIPIENTS_RULE) != null) {
                Object ruleInfo = config.get(ARG_RECIPIENTS_RULE);
                String name = SuggestHelper.extractNameOrIdFromSuggestObject(ruleInfo);
                if (name != null) {
                    setAdditionalRecipientsRuleName(name);
                }
            }

            if (config.get(ARG_RECIPIENTS) != null) {
                ArrayList<String> recipients = new ArrayList<>();

                for (Object recipient : (List<Object>)config.get(ARG_RECIPIENTS)) {
                    String identity = SuggestHelper.extractNameOrIdFromSuggestObject(recipient);
                    if (identity != null) {
                        recipients.add(identity);
                    }
                }

                setAdditionalRecipientNames(recipients);
            }
        }

        @XMLProperty
        public boolean isEnabled() {
            return enabled;
        }
        
        public void setEnabled(boolean val) {
            enabled = val;
        }
        
        @XMLProperty
        public long getMillis() {
            return millis;
        }

        public void setMillis(long val) {
            millis = val;
        }

        @XMLProperty
        public boolean isBefore() {
            return before;
        }

        public void setBefore(boolean val) {
            before = val;
        }

        @XMLProperty
        public String getEmailTemplateName() {
            return emailTemplateName;
        }

        public void setEmailTemplateName(String val) {
            emailTemplateName = val;
        }

        @XMLProperty
        public long getFrequency() {
            return frequency;
        }

        public void setFrequency(long val) {
            frequency = val;
        }

        @XMLProperty
        public boolean isAdditionalRecipientsPresent() {
            return additionalRecipientsPresent;
        }

        public void setAdditionalRecipientsPresent(boolean val) {
            additionalRecipientsPresent = val;
        }

        @XMLProperty
        public List<String> getAdditionalRecipientNames() {
            return additionalRecipientNames;
        }

        public void setAdditionalRecipientNames(List<String> val) {
            additionalRecipientNames = val;
        }

        @XMLProperty
        public String getAdditionalRecipientsRuleName() {
            return additionalRecipientsRuleName;
        }

        public void setAdditionalRecipientsRuleName(String val) {
            additionalRecipientsRuleName = val;
        }

        @XMLProperty
        public int getCount() {
            return count;
        }

        public void setCount(int val) {
            count = val;
        }
        
        public boolean isExhausted() {
            return ((getFrequency() == 0) && getCount() > 0);
        }
        
        public boolean isSame(IConfig otherConfig) {
            
            if (!otherConfig.getClass().equals(getClass())) {
                return false;
            }
            
            if (
                (otherConfig.isEnabled() ==  isEnabled())
                    &&
                (otherConfig.isBefore() == isBefore())
                    &&
                (otherConfig.getMillis() == getMillis())
                    &&
                (otherConfig.getFrequency() == getFrequency())) {
                return true;
            } else {
                return false;
            }
        }
        
        public Date calculateNextNotificationDate(Date lastNotificationDate) {
            /*
             * IIQETN-216: When we moved in time enough to have multiple notifications already due
             * we may need to use the frequency to get up to date, specially when other
             * configurations don't let us advance in time.
             */
            if (getFrequency() <= 0 && !lastNotificationDate.after(DateUtil.getCurrentDate())) {
                return null;
            } else {
                while(!(lastNotificationDate.after(DateUtil.getCurrentDate()))) {
                    lastNotificationDate = new Date(lastNotificationDate.getTime() + getFrequency());
                }
                return lastNotificationDate;
            }
        }

        
        /**
         * Do not take into account maxReminders
         */
        protected Date getFirstDateConsideringBeforeOrAfter(Date startDate, Date endDate) {
            if (isBefore()) {
                if (endDate == null) {
                    return null;
                } else {
                    return new Date(endDate.getTime() - getMillis());
                }
            } else {
                if (startDate == null) {
                    return null;
                } else {
                    return new Date(startDate.getTime() + getMillis());
                }
            }
        }
    }
    
    @XMLClass
    public static class ReminderConfig extends ConfigBase implements IConfig {

        private static final long serialVersionUID = -2640863371134283854L;

        /**
         * true if only one reminder is sent
         */
        private boolean once;

        public ReminderConfig() {}

        /**
         * Constructor that builds a new ReminderConfig based on the provided map.
         * @param config Map with ReminderConfig data
         */
        public ReminderConfig (Map<String, Object> config) {
            super(config);
            if (config.containsKey(ARG_ONCE)) {
                setOnce(Util.otob(config.get(ARG_ONCE)));
            }
        }

        @XMLProperty
        public boolean isOnce() {
            return once;
        }

        public void setOnce(boolean val) {
            once = val;
        }

        public Date calculateStartDate(Date notificationStartDate, Date notificationEndDate, IConfig previousConfig) {
            return getFirstDateConsideringBeforeOrAfter(notificationStartDate, notificationEndDate);
        }
        
        public String toString() {
            return ToStringBuilder.reflectionToString(this, ToStringStyle.MULTI_LINE_STYLE);
        }
    }
    
    @XMLClass
    public static class EscalationConfig extends ConfigBase implements IConfig {

        private static final long serialVersionUID = -3124940353664888743L;

        /**
         * Maximum number of reminders after which to escalate
         */
        private int maxReminders;
        
        /**
         * the escalation rule
         */
        private String escalationRuleName;
        
        public EscalationConfig() {}

        /**
         * Constructor that creates a new EscalationConfig based on the provided map.
         * @param config Map with EscalationConfig data.
         */
        public EscalationConfig (Map<String, Object> config) {
            super(config);
            if (config.containsKey(ARG_MAX_REMINDERS)) {
                setMaxReminders(Util.otoi(config.get(ARG_MAX_REMINDERS)));
            }

            if (config.get(ARG_ESCALATION_RULE) != null) {
                Object ruleInfo = config.get(ARG_ESCALATION_RULE);
                String name = SuggestHelper.extractNameOrIdFromSuggestObject(ruleInfo);
                if (name != null) {
                    setEscalationRuleName(name);
                }
            }
        }

        @XMLProperty
        public int getMaxReminders() {
            return maxReminders;
        }
        
        public void setMaxReminders(int val) {
            maxReminders = val;
        }
        
        @XMLProperty
        public String getEscalationRuleName() {
            return escalationRuleName;
        }
        
        public void setEscalationRuleName(String val) {
            escalationRuleName = val;
        }
        
        // override, need to take maxReminders into account
        public Date getFirstDateConsideringBeforeOrAfter(Date startDate, Date endDate) {
            if (maxReminders > 0) {
                return null;
            } else {
                return super.getFirstDateConsideringBeforeOrAfter(startDate, endDate);
            }
        }

        public Date calculateStartDate(Date notificationStartDate, Date notificationEndDate, IConfig previousConfig) {
            if (maxReminders > 0) {
                //previousConfig must be reminder, so no need to get previousConfig of that.
                if (previousConfig == null) {
                    throw new IllegalStateException("previousConfig expected, cannot have maxReminders without previousConfig");
                }
                Date previousConfigStartDate = previousConfig.calculateStartDate(notificationStartDate, notificationEndDate, null);
                if (previousConfigStartDate == null) {
                    throw new IllegalStateException("when maxReminders is set, the earlier reminder config must have a start");
                }
                return new Date(previousConfigStartDate.getTime() + (maxReminders * previousConfig.getFrequency()));
            } else {
                return getFirstDateConsideringBeforeOrAfter(notificationStartDate, notificationEndDate);
            }
        }
        
        public String toString() {
            return ToStringBuilder.reflectionToString(this, ToStringStyle.MULTI_LINE_STYLE);
        }
    }
    
    private boolean enabled;
    private List<IConfig> configs;
    private Date startDate;
    private Date endDate;

    @XMLProperty
    public boolean isEnabled() {
        return enabled;
    }
    
    public void setEnabled(boolean val) {
        enabled = val;
    }
    
    @XMLProperty
    public List<IConfig> getConfigs() {
        return configs;
    }
    
    public void setConfigs(List<IConfig> val) {
        configs = val;
    }
    
    @XMLProperty
    public Date getStartDate() {
        return startDate;
    }
    
    public void setStartDate(Date val) {
        startDate = val;
    }

    @XMLProperty
    public Date getEndDate() {
        return endDate;
    }
    
    public void setEndDate(Date val) {
        endDate = val;
    }
    
    public boolean isNew() {
        return !Util.isEmpty(configs);
    }
    
    public String toString() {
        return ToStringBuilder.reflectionToString(this, ToStringStyle.MULTI_LINE_STYLE);
    }
    
    /**
     * The following properties are deprecated.
     * and used for backward compatibility only.
     */
    @Deprecated
    private boolean remindersEnabled;
    @Deprecated
    private long initialReminderMillisAfterStart;
    @Deprecated
    private long initialReminderMillisBeforeEnd;
    @Deprecated
    private long reminderFrequency;
    @Deprecated
    private EmailTemplate reminderEmailTemplate;

    @Deprecated
    private boolean escalationEnabled;
    @Deprecated
    private EmailTemplate escalationEmailTemplate;
    @Deprecated
    private int escalationMaxReminders;
    @Deprecated
    private long escalationMillisAfterStart;
    @Deprecated
    private long escalationMillisBeforeEnd;
    @Deprecated
    private long escalationFrequency;
    @Deprecated
    private Rule escalationRule;

    
    /**
     * Default constructor.
     */
    public NotificationConfig() {}

    /**
     * Constructor that adapts a WorkItemConfig into a NotificationConfig. At
     * some point merge these might be merged, or at least the
     * WorkItemConfig made use a NotificationConfig for the reminder/escalation
     * properties.
     * 
     * @param  config  The WorkItemConfig adapting.
     */
    public NotificationConfig(WorkItemConfig config) {
        
        initializeOldStyleNotificationConfig(config);
    }

    /**
     * Constructor that creates a new NotificationConfig based on the provided map data.
     * The Map is assumed to be a generic representation of NotificationConfigDTO.
     * @param config Map with NotificationConfig data
     */
    public NotificationConfig(Map<String, Object> config) {

        enabled = true;
        configs = new ArrayList<IConfig>();

        if (config.containsKey(ARG_NOTIFICATION_CONFIGS)) {
            for (Map<String, Object> property : (ArrayList<Map<String, Object>>) config.get(ARG_NOTIFICATION_CONFIGS)) {

                if (property.containsKey(ARG_NOTIFICATION_TYPE)) {
                    if (property.get(ARG_NOTIFICATION_TYPE).equals(NotificationConfigDTO.ReminderConfigDTO.TYPE_STRING)) {
                        configs.add(new ReminderConfig(property));
                    } else {
                        configs.add(new EscalationConfig(property));
                    }
                }
            }
        }
    }

    @SuppressWarnings("unused")
    private void initializeNewStyleNotificationConfig(WorkItemConfig config) {

        // We'll assume a non-null template and reminder or both type means reminders are enabled.
        setEnabled(true);
        
        //MEH 16142 warn the user for misconfigured WorkItemConfigs
        if ((config.getEscalationStyle().equals(WorkItemConfig.STYLE_BOTH) || config.getEscalationStyle().equals(WorkItemConfig.STYLE_REMINDER)) && config.getReminderEmail() == null) {
        	log.warn("No reminder email specified for reminder or both workitem config escalation style, reminder notifcations will not occur: " + config);
        }
        
        if ((config.getEscalationStyle().equals(WorkItemConfig.STYLE_BOTH) || config.getEscalationStyle().equals(WorkItemConfig.STYLE_ESCALATION)) && config.getEscalationRule() == null) {
        	log.warn("No escalation rule specified for escalation or both workitem config escalation style, escalations will not occur: " + config);
        }
        
        if(config.getEscalationStyle() == null || config.getEscalationStyle().equals(WorkItemConfig.STYLE_NONE)) {
        	log.warn("No escalation style specified, no escalations or reminders will run for: " + config);
        }
        
        //MEH 16142 use the escalation style as the explicit wish of the user
        if ((config.getEscalationStyle().equals(WorkItemConfig.STYLE_BOTH) || config.getEscalationStyle().equals(WorkItemConfig.STYLE_REMINDER)) && config.getReminderEmail() != null) {
            
            configs = new ArrayList<IConfig>();
            ReminderConfig reminderConfig = new ReminderConfig();
            reminderConfig.setEnabled(true);
            configs.add(reminderConfig);
            reminderConfig.setBefore(false);
            
            // Hours till escalation is use for reminders if reminders are
            // configured.
            // jsl - this is hacked to treat negative values as seconds
            // rather than hours for demos.  
            int hours = config.getHoursTillEscalation();
            if (hours < 0) {
                reminderConfig.setMillis(-hours * Util.MILLI_IN_SECOND);
            }
            else {
                reminderConfig.setMillis(hours * Util.MILLI_IN_HOUR);
            }

            hours = config.getHoursBetweenReminders();
            if (hours < 0) {
                reminderConfig.setFrequency(-hours * Util.MILLI_IN_SECOND);
            }
            else {
                reminderConfig.setFrequency(hours * Util.MILLI_IN_HOUR);
            }

            reminderConfig.setEmailTemplateName(config.getReminderEmail() == null ? null: config.getReminderEmail().getName());
            
        }

        // We'll assume a non-null rule and escalation or both type means escalations are enabled.
        if ((config.getEscalationStyle().equals(WorkItemConfig.STYLE_BOTH) || config.getEscalationStyle().equals(WorkItemConfig.STYLE_ESCALATION)) && config.getEscalationRule() != null) {
            if (configs == null) {
                configs = new ArrayList<IConfig>();
            }
            EscalationConfig escalationConfig = new EscalationConfig();
            escalationConfig.setEnabled(true);
            configs.add(escalationConfig);
            escalationConfig.setBefore(false);
            escalationConfig.setMaxReminders(config.getMaxReminders());

            int hours = config.getHoursTillEscalation();
            if (hours < 0) {
                escalationConfig.setMillis(-hours * Util.MILLI_IN_SECOND);
            }
            else {
                escalationConfig.setMillis(hours * Util.MILLI_IN_HOUR);
            }
            
            hours = config.getHoursBetweenReminders();
            if (hours < 0) {
                escalationConfig.setFrequency(-hours * Util.MILLI_IN_SECOND);
            }
            else {
                escalationConfig.setFrequency(hours * Util.MILLI_IN_HOUR);
            }
            
            escalationConfig.setEmailTemplateName(config.getEscalationEmail() == null ? null: config.getEscalationEmail().getName());
            escalationConfig.setEscalationRuleName(config.getEscalationRule() == null ? null: config.getEscalationRule().getName());
        }
    }
    
    private void initializeOldStyleNotificationConfig(WorkItemConfig config) {
        
        // We'll assume a non-null template means reminders are enabled.
        this.remindersEnabled = (config.getReminderEmail() != null);

        if (this.remindersEnabled) {
            // Hours till escalation is use for reminders if reminders are
            // configured.
            // jsl - this is hacked to treat negative values as seconds
            // rather than hours for demos.  
            int hours = config.getHoursTillEscalation();
            if (hours < 0)
                this.initialReminderMillisAfterStart =
                    hours * Util.MILLI_IN_SECOND;
            else
                this.initialReminderMillisAfterStart =
                    hours * Util.MILLI_IN_HOUR;

            hours = config.getHoursBetweenReminders();
            if (hours < 0) {
                this.reminderFrequency = hours * Util.MILLI_IN_SECOND;
                if ( this.reminderFrequency < 0 ) {
                    // djs: This needs to be a positive number downstream when we test
                    // this in the Escalator 
                    this.reminderFrequency = -(this.reminderFrequency);
                }
            } else {
                this.reminderFrequency = hours * Util.MILLI_IN_HOUR;
            }

            this.reminderEmailTemplate = config.getReminderEmail();
            this.escalationMaxReminders = config.getMaxReminders();
        }

        // We'll assume a non-null rule means escalations are enabled.
        this.escalationEnabled = (config.getEscalationRule() != null);
        
        if (this.escalationEnabled) {
            // If reminders aren't enabled, the hours till escalation applies
            // to escalations.
            if (!this.remindersEnabled) {
                int hours = config.getHoursTillEscalation();
                if (hours < 0)
                    this.escalationMillisAfterStart = 
                        hours * Util.MILLI_IN_SECOND;
                else
                    this.escalationMillisAfterStart = 
                        hours * Util.MILLI_IN_HOUR;

                hours = config.getHoursBetweenReminders();
                if (hours < 0)
                    this.escalationFrequency = - hours * Util.MILLI_IN_SECOND;
                else
                    this.escalationFrequency = hours * Util.MILLI_IN_HOUR;
                
            }

            this.escalationEmailTemplate = config.getEscalationEmail();
            this.escalationRule = config.getEscalationRule();
        }
        
    }
    
    @Deprecated
    private ReminderConfig getFirstEnabledReminderConfig() {
        for (IConfig config : configs) {
            if (config instanceof ReminderConfig) {
                if (config.isEnabled()) {
                    return (ReminderConfig) config;
                }
            }
        }
        return null;
    }

    @Deprecated
    private EscalationConfig getFirstEnabledEscalationConfig() {
        for (IConfig config : configs) {
            if (config instanceof EscalationConfig) {
                if (config.isEnabled()) {
                    return (EscalationConfig) config;
                }
            }
        }
        return null;
    }
    
    @SuppressWarnings({ "unused" })
    private ReminderConfig getOrCreateFirstReminder() {
        
        if (isNew()) {
            ReminderConfig reminderConfig = getFirstEnabledReminderConfig();
            if (reminderConfig != null) {
                return reminderConfig;
            }
        }
        
        if (configs == null) {
            configs = new ArrayList<IConfig>();
        }

        ReminderConfig config = new ReminderConfig();
        config.setEnabled(true);
        configs.add(config);
        
        return config;
    }
    
    @SuppressWarnings("unused")
    private EscalationConfig getOrCreateFirstEscalation() {

        if (isNew()) {
            EscalationConfig escalationConfig = getFirstEnabledEscalationConfig();
            if (escalationConfig != null) {
                return escalationConfig;
            }
        }
        
        if (configs == null) {
            configs = new ArrayList<IConfig>();
        }

        EscalationConfig config = new EscalationConfig();
        config.setEnabled(true);
        configs.add(config);
        
        return config;
    }
    
    @Deprecated
    @XMLProperty
    public boolean isRemindersEnabled() {
        
        if (isNew()) {
            return (getFirstEnabledReminderConfig() != null);
        } else {
            return remindersEnabled;
        }
    }
    
    @Deprecated
    public void setRemindersEnabled(boolean val) {
        remindersEnabled = val;
    }

    @Deprecated
    @XMLProperty
    public long getInitialReminderMillisAfterStart() {
        return this.initialReminderMillisAfterStart;
    }
    
    @Deprecated
    public void setInitialReminderMillisAfterStart(long millis) {
        this.initialReminderMillisAfterStart = millis;
    }
    
    @Deprecated
    @XMLProperty
    public long getInitialReminderMillisBeforeEnd() {
        return initialReminderMillisBeforeEnd;
    }

    @Deprecated
    public void setInitialReminderMillisBeforeEnd(long l) {
        this.initialReminderMillisBeforeEnd = l;
    }

    @Deprecated
    @XMLProperty
    public long getReminderFrequency() {
        
        if (isNew()) {
            ReminderConfig firstReminder = getFirstEnabledReminderConfig();
            if (firstReminder != null) {
                return getFirstEnabledReminderConfig().getFrequency();
            } else {
                return 0;
            }
        } else {
            return reminderFrequency;
        }
    }

    @Deprecated
    public void setReminderFrequency(long val) {
        this.reminderFrequency = val;
    }

    @Deprecated
    @XMLProperty(mode=SerializationMode.REFERENCE)
    public EmailTemplate getReminderEmailTemplate() throws GeneralException {
        return reminderEmailTemplate;
    }    
    
    @Deprecated
    public void setReminderEmailTemplate(EmailTemplate val) {
        this.reminderEmailTemplate = val;
    }

    @Deprecated
    public EmailTemplate getReminderEmailTemplate(Resolver resolver) throws GeneralException {
        
        if (isNew()) {
            ReminderConfig firstReminder = getFirstEnabledReminderConfig();
            if (firstReminder != null) {
                String templateName = getFirstEnabledReminderConfig().getEmailTemplateName();
                if (templateName == null) {
                    return null;
                }
                return resolver.getObjectByName(EmailTemplate.class, templateName);
            } else {
                return null;
            }
        } else {
            return reminderEmailTemplate;
        }
    }

    @Deprecated
    @XMLProperty
    public boolean isEscalationEnabled() {

        if (isNew()) {
            return (getFirstEnabledEscalationConfig() != null);
        } else {
            return escalationEnabled;
        }
    }

    @Deprecated
    public void setEscalationEnabled(boolean val) {
        this.escalationEnabled = val;
    }

    @Deprecated
    @XMLProperty(mode=SerializationMode.REFERENCE)
    public EmailTemplate getEscalationEmailTemplate() {
        return escalationEmailTemplate;
    }

    @Deprecated
    public void setEscalationEmailTemplate(EmailTemplate escalationEmailTemplate) {
        this.escalationEmailTemplate = escalationEmailTemplate;
    }

    @Deprecated
    public EmailTemplate getEscalationEmailTemplate(Resolver resolver) throws GeneralException {
        
        if (isNew()) {
            EscalationConfig firstEscalation = getFirstEnabledEscalationConfig();
            if (firstEscalation != null) {
                String templateName = getFirstEnabledEscalationConfig().getEmailTemplateName();
                if (templateName == null) {
                    return null;
                }
                return resolver.getObjectByName(EmailTemplate.class, templateName);
            } else {
                return null;
            }
        } else {
            return escalationEmailTemplate;
        }
    }
    
    @Deprecated
    @XMLProperty
    public int getEscalationMaxReminders() {
        
        if (isNew()) {
            EscalationConfig firstEscalation = getFirstEnabledEscalationConfig();
            if (firstEscalation != null) {
                return firstEscalation.getMaxReminders();
            } else {
                return 0;
            }
        } else {
            return escalationMaxReminders;
        }
    }

    @Deprecated
    public void setEscalationMaxReminders(int val) {
        escalationMaxReminders = val;
    }

    @Deprecated
    @XMLProperty
    public long getEscalationMillisAfterStart() {
        return this.escalationMillisAfterStart;
    }
    
    @Deprecated
    public void setEscalationMillisAfterStart(long millis) {
        this.escalationMillisAfterStart = millis;
    }
    
    @Deprecated
    @XMLProperty
    public long getEscalationMillisBeforeEnd() {
        return escalationMillisBeforeEnd;
    }
    
    @Deprecated
    public void setEscalationMillisBeforeEnd(long escalationMillisBeforeEnd) {
        this.escalationMillisBeforeEnd = escalationMillisBeforeEnd;
    }
    
    /**
     * Get the frequency of escalations. Note that this getter/setter pair is
     * suffixed with "millis" because the older setEscalationFrequency() method
     * was used for something different, and this needed to be retained for XML
     * backwards compatibility.
     */
    @Deprecated
    @XMLProperty
    public long getEscalationFrequencyMillis() {
        if (isNew()) {
            EscalationConfig firstEscalation = getFirstEnabledEscalationConfig();
            if (firstEscalation != null) {
                return firstEscalation.getFrequency();
            } else {
                return 0;
            }
        }
        return this.escalationFrequency;
    }

    @Deprecated
    public void setEscalationFrequencyMillis(long frequency) {
        this.escalationFrequency = frequency;
    }

    /**
     * @deprecated  Use setEscalationFrequencyMillis(long) instead. This setter
     *              will remain to support legacy XML, and will set the 
     */
    @Deprecated
    @XMLProperty(legacy=true)
    public void setEscalationFrequency(long escalationFrequency) {
        this.escalationFrequency = escalationFrequency;
    }

    @Deprecated
    @XMLProperty(mode=SerializationMode.REFERENCE)
    public Rule getEscalationRule() {
        return escalationRule;
    }

    @Deprecated
    public void setEscalationRule(Rule escalationRule) {
        this.escalationRule = escalationRule;
    }
    
    @Deprecated
    public Rule getEscalationRule(Resolver resolver) throws GeneralException {
        
        if (isNew()) {
            EscalationConfig firstEscalation = getFirstEnabledEscalationConfig();
            if (firstEscalation != null) {
                String name = getFirstEnabledEscalationConfig().getEscalationRuleName();
                if (name == null) {
                    return null;
                } else {
                    return resolver.getObjectByName(Rule.class, name);
                }
            } else {
                return null;
            }
        }
        return escalationRule;
    }

    ////////////////////////////////////////////////////////////////////////////
    // 
    // METHODS
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Enable reminders using the given email template, frequency, and time
     * before the end.
     */
    @Deprecated
    public void enableReminders(Resolver resolver, String emailTemplateName,
                                long reminderFrequency,
                                long initialReminderMillisBeforeEnd)
        throws GeneralException {

        setRemindersEnabled(true);
        setReminderEmailTemplate(resolver.getObjectByName(EmailTemplate.class, emailTemplateName));
        setReminderFrequency(reminderFrequency);
        setInitialReminderMillisBeforeEnd(initialReminderMillisBeforeEnd);
    }

    /**
     * Enable reminders using the given email template, frequency, and time
     * after the start to first remind.
     */
    @Deprecated
    public void enableRemindersFromStart(Resolver resolver, String emailTemplateName,
                                         long reminderFrequency,
                                         long initialReminderMillisAfterStart)
        throws GeneralException {
    
        setRemindersEnabled(true);
        setReminderEmailTemplate(resolver.getObjectByName(EmailTemplate.class, emailTemplateName));
        setReminderFrequency(reminderFrequency);
        setInitialReminderMillisAfterStart(initialReminderMillisAfterStart);
    }

    /**
     * Enable escalation using a maximum number of reminders.
     */
    @Deprecated
    public void enableEscalation(Resolver resolver, String ruleName,
                                 String emailTemplate, long escalationFrequency,
                                 int maxReminders)
        throws GeneralException {
    
        setEscalationEnabled(true);
        setEscalationRule(resolver.getObjectByName(Rule.class, ruleName));
        setEscalationEmailTemplate(resolver.getObjectByName(EmailTemplate.class, emailTemplate));
        setEscalationFrequency(escalationFrequency);
        setEscalationMaxReminders(maxReminders);
    }

    /**
     * NOTE: this method is used to do INITIAL SETUP of notifications.
     * Startdate is now and enddate is the parameter expiration

     * Set the wake up date on the given Notifiable using this notification
     * configuration and the given expiration.
     * 
     * @param  notifiable  The notifiable on which to set the wake up date.
     * @param  expiration  The expiration for the notifiable.
     */
    public void assignWakeUpDate(Notifiable notifiable, Date expiration) {

        // Default to the expiration.
        notifiable.setWakeUpDate(expiration);
        
        // If reminders or escalation are enabled, change the wake up date.
        Date startDate = DateUtil.getCurrentDate();
        setStartDate(startDate);
        setEndDate(expiration);
        if (isNew()) {
            for (IConfig config : configs) {
                if (config.isEnabled()) {
                    notifiable.setWakeUpDate(calculateFirstDate(startDate, expiration, config.isBefore(), config.getMillis()));
                    break;
                }
            }
        } else {
            if(isRemindersEnabled()) {
                notifiable.setWakeUpDate(calculateFirstReminderDate(startDate, expiration));
            } else if (isEscalationEnabled()) {
                notifiable.setWakeUpDate(calculateFirstEscalationDate(startDate, expiration));
            }
        }
    }
    
    /**
     * This is used when certification has already been scheduled.
     * All the values like configs, startDate and endDate (on "this") is assumed
     * to be already set by the caller.
     * 
     */
    public void reAssignWakeupDate(Notifiable notifiable, NotificationConfig oldNotificationConfig) {

        if (isNew()) {
            copyCountValues(oldNotificationConfig);

            notifiable.setWakeUpDate(calculateNextNotificationDate());
            
        } else {
            if(isRemindersEnabled()) {
                notifiable.setWakeUpDate(calculateFirstReminderDate(startDate, endDate));
            } else if (isEscalationEnabled()) {
                notifiable.setWakeUpDate(calculateFirstEscalationDate(startDate, endDate));
            }
        }
        
    }
    
    // Copy values of notifications that have already been sent
    private void copyCountValues(NotificationConfig otherNotificationConfig) {
        
        for (int i=0; i<otherNotificationConfig.getConfigs().size(); ++i) {
            IConfig otherConfig = otherNotificationConfig.getConfigs().get(i);
            if (otherConfig.getCount() == 0) {
                // we are done, this item was not used in the past.
                break;
            }
            if (i == configs.size()) {
                // we have reached the end of this
                break;
            }
            
            IConfig thisConfig = configs.get(i);
            if (thisConfig.isSame(otherConfig)) {
                thisConfig.setCount(otherConfig.getCount());
            }
        }
    }
    
    public Date calculateNextNotificationDate() {
        
        if (!isNew()) {
            throw new IllegalStateException();
        }
        
        int startIndex = findLastUsedIndex();
        if (startIndex == -1) {
            // nothing has been used so far, try to use the first one that is enabled
            startIndex = findFirstEnabledIndex();
        }
        if (startIndex == -1) {
            if (log.isDebugEnabled()) {log.debug("no enabled configs found");}
            return null;
        }
        
        IConfig previousConfig = null;
        int previousIndex = findPreviousConfigIndex(startIndex);
        if (previousIndex != -1) {
            previousConfig = configs.get(previousIndex);
        }

        /*
         * Here we get the first expected notification date
         * from the initial notification configuration to then
         * move further into the following notification configuration
         */
        Date nextNotificationDate = configs.get(startIndex).calculateStartDate(startDate, endDate, previousConfig);
        int currentIndex = startIndex;
        while (currentIndex < configs.size() && nextNotificationDate != null) {
            IConfig currentConfig = configs.get(currentIndex);
            Date fromCurrentConfig = currentConfig.calculateNextNotificationDate(nextNotificationDate);
            
            Date startDateFromNextConfig = null;
            int nextIndex = findNextConfigIndex(currentIndex);
            if (nextIndex != -1) {
                startDateFromNextConfig = calculateStartDate(nextIndex);
                // First check that the next date in the next config is
                // not before 'fromCurrentConfig'. If it is, it should be the test
                // date instead
                IConfig nextConfig = configs.get(nextIndex);
                if (nextConfig != null && startDateFromNextConfig != null) {
                    Date fromNextConfig = nextConfig.calculateNextNotificationDate(startDateFromNextConfig);
                    //'fromCurrentConfig' may be null if there's an intermediary escalation configuration interrupting the repeating reminders
                    if (fromCurrentConfig == null || fromNextConfig != null && fromNextConfig.before(fromCurrentConfig)) {
                        fromCurrentConfig = fromNextConfig;
                    } else if (fromNextConfig == null && nextIndex + 1 < configs.size()) {
                        /*
                         *  if fromNextConfig is null and there is another configuration after this one, we
                         *  need to try the next configuration to see if we can get a date in the future
                         */
                        nextNotificationDate = startDateFromNextConfig;
                        currentIndex = nextIndex;
                        continue; // Let's go onto the next configuration
                    }
                }
                if (shouldUseSecondDate(fromCurrentConfig, startDateFromNextConfig)) {
                    // need to move to next index
                    nextNotificationDate = startDateFromNextConfig;
                    currentIndex = nextIndex;
                } else {
                    nextNotificationDate = fromCurrentConfig;
                }
            } else {
                // There's no next index
                nextNotificationDate = fromCurrentConfig;
            }
            /*
             * We are looking for the next date to wakeup and send the notification, this must be a non-null date and be in the future.
             */
            if (nextNotificationDate != null && nextNotificationDate.getTime() >= DateUtil.getCurrentDate().getTime()) {
                break;
            }

        }
        
        return nextNotificationDate;
        
    }

    private boolean shouldUseSecondDate(Date firstDate, Date secondDate) {
        
        boolean useSecondDate;
        
        if (firstDate == null) {
            if (secondDate == null) {
                // first null, second null
                useSecondDate = false;
            } else {
                // first null, second not null
                useSecondDate = true;
            }
        } else {
            if (secondDate == null) {
                useSecondDate = false;
            } else {
            	//MEH 16142 we need to take into consideration if 
            	//we have gone beyond now for our choice.
                if (secondDate.getTime() <= firstDate.getTime() && secondDate.getTime() >= DateUtil.getCurrentDate().getTime()) {
                    useSecondDate = true;
                    if (log.isDebugEnabled()) {
                        log.debug("choosing second date");
                    }
                } else {
                    useSecondDate = false;
                    if (log.isDebugEnabled()) {
                        log.debug("choosing first date");
                    }
                }
            }
        }
        return useSecondDate;
    }
    
    /**
     * Will first find the current config that is being used
     * Then it will try to use the next one if available.
     */
    public int findConfigToUseIndex() {
        
        int lastUsedIndex = findLastUsedIndex();
        if (lastUsedIndex == -1) {
            // nothing has been used so far, try to use the first one that is enabled
            return findFirstEnabledIndex();
        }
        
        int indexToUse = lastUsedIndex;

        int nextIndex = findNextConfigIndex(lastUsedIndex);
        if (nextIndex != -1) {
            Date nextStartDate = calculateStartDate(nextIndex);
            if (DateUtil.getCurrentDate().getTime() >= nextStartDate.getTime()) {
                indexToUse = nextIndex;
            }
        }
        
        return indexToUse;
    }
    
    
    private int findFirstEnabledIndex() {
        
        int firstEnabledIndex = -1;
        
        // Starting from the END find the first (last) config
        // which was used to send notifications
        for (int i=0; i<configs.size(); ++i) {
            IConfig config = configs.get(i);
            if (config.isEnabled()) {
                firstEnabledIndex = i;
                break;
            }
        }

        return firstEnabledIndex;
    }

    /**
     * find the next enabled config index
     */
    public int findNextConfigIndex(int i) {
        
        IConfig nextConfig = null;
        int nextToTry = i+1;
        while (nextToTry < configs.size()) {
            nextConfig = configs.get(nextToTry);
            if (nextConfig.isEnabled()) {
                return nextToTry;
            }
            ++nextToTry;
        }
        
        return -1;
    }

    /**
     * find the previous enabled config index
     */
    public int findPreviousConfigIndex(int i) {
        
        IConfig previousConfig = null;
        int previousToTry = i-1;
        while (previousToTry >= 0) {
            previousConfig = configs.get(previousToTry);
            if (previousConfig.isEnabled()) {
                return previousToTry;
            }
            --previousToTry;
        }
        
        return -1;
    }
    
    // The index that was used last used.
    private int findLastUsedIndex() {

        int lastUsedIndex = -1;
        
        // Starting from the END find the first (last) config
        // which was used to send notifications
        for (int i=configs.size()-1; i>=0; --i) {
            IConfig config = configs.get(i);
            if (config.isEnabled() && config.getCount() > 0) {
                lastUsedIndex = i;
                break;
            }
        }

        return lastUsedIndex;
    }
    
    private Date calculateStartDate(int index) {
        
        IConfig config = configs.get(index);
        
        IConfig previousConfig = null;
        int previousConfigIndex = findPreviousConfigIndex(index);
        if (previousConfigIndex >= 0) {
            previousConfig = configs.get(previousConfigIndex);
        }
        
        return config.calculateStartDate(startDate, endDate, previousConfig);
    }
    
    /**
     * Calculate the first reminder date.
     * 
     * @param  start       The start date of the period.
     * @param  expiration  The expiration date of the period.
     * 
     * @return The first reminder date, or null if reminders are not enabled.
     * 
     * @throws IllegalArgumentException If expiration is null and reminders are
     *     based on the expiration date, or start is null and reminders are
     *     based on the start date.
     */
    @Deprecated
    public Date calculateFirstReminderDate(Date start, Date expiration)
        throws IllegalArgumentException {

        Date first = null;
        
        if (isRemindersEnabled()) {
            first = calculateFirstDate(start, expiration, getInitialReminderMillisAfterStart(), getInitialReminderMillisBeforeEnd());
        }
        
        return first;
    }
    
    /**
     * Calculate the first escalation date.
     * 
     * @param  start       The start date of the period.
     * @param  expiration  The expiration date of the period.
     * 
     * @return The first escalation date, or null if escalations are not
     *         configured or are based on a max number of reminders.
     * 
     * @throws IllegalArgumentException  If expiration is null and escalations
     *     are based on the expiration date, or start is null and escalations
     *     are based on the start date.
     */
    @Deprecated
    public Date calculateFirstEscalationDate(Date start, Date expiration)
        throws IllegalArgumentException {

        Date first = null;
        
        if (isEscalationEnabled() && !isRemindersEnabled()) {
            first = calculateFirstDate(start, expiration, getEscalationMillisAfterStart(), getEscalationMillisBeforeEnd());
        }
        
        return first;
    }

    /**
     * Calculate the date that an action should first occur using the given
     * trigger parameters.
     */
    @Deprecated
    private static Date calculateFirstDate(Date start, Date expiration,
                                           long millisAfterStart,
                                           long millisBeforeEnd) {

        Date first = null;

        // Use the milliseconds after the start date.
        if (0 != millisAfterStart) {
            if (null == start) {
                throw new IllegalArgumentException("Start date is required.");
            }
            first = new Date(start.getTime() + millisAfterStart);
        }
        else if (0 != millisBeforeEnd) {
            if (null == expiration) {
                throw new IllegalArgumentException("Expiration is required.");
            }

            first = new Date(expiration.getTime() - millisBeforeEnd);
        }

        // Constrain the date between now and the expiration date.
        if (null != first) {
            Date now = DateUtil.getCurrentDate();
            if (first.before(now)) {
                first = now;
            }
            else if ((null != expiration) && first.after(expiration)) {
                first = expiration;
            }
        }

        return first;
    }
    
    private static Date calculateFirstDate(Date start, Date expiration, boolean before, long millis) {
        Date first = null;
        if (before) {
            // that is if millis is before expiration 
            if (null == expiration) {
                throw new IllegalArgumentException("Expiration is required.");
            }

            first = new Date(expiration.getTime() - millis);
        } else {
            // that is if millis is AFTER start
            if (null == start) {
                throw new IllegalArgumentException("Start date is required.");
            }
            first = new Date(start.getTime() + millis);
        }
        
        // Constrain the date between now and the expiration date.
        if (null != first) {
            Date now = DateUtil.getCurrentDate();
            if (first.before(now)) {
                first = now;
            }
            else if ((null != expiration) && first.after(expiration)) {
                first = expiration;
            }
        }

        return first;
    }
    
    public static CertificationDefinition.NotificationConfig createRestNotificationConfig(SailPointContext context, NotificationConfig dbConfig)
            throws GeneralException {

        CertificationDefinition.NotificationConfig restConfig = new CertificationDefinition.NotificationConfig();
        restConfig.setEnabled(dbConfig.isEnabled());

        List<CertificationDefinition.NotificationConfig.IConfig> jsonConfigs = new ArrayList<CertificationDefinition.NotificationConfig.IConfig>();
        restConfig.setConfigs(jsonConfigs);
        if (dbConfig.getConfigs() != null) {
            for (IConfig config : dbConfig.getConfigs()) {
                if (config instanceof ReminderConfig) {
                    jsonConfigs.add(createOneRestReminderConfig(context, (ReminderConfig) config));
                } else {
                    jsonConfigs.add(createOneRestEscalationConfig(context, (EscalationConfig) config));
                }
            }
        }
        
        restConfig.setDefaultReminderConfigForNew(createDefaultRestReminderConfig(context));
        restConfig.setDefaultEscalationConfigForNew(createDefaultRestEscalationConfig(context, true));

        return restConfig;
    }

    private static CertificationDefinition.NotificationConfig.ReminderConfig createOneRestReminderConfig(Resolver resolver, ReminderConfig fromDb)
            throws GeneralException {

        CertificationDefinition.NotificationConfig.ReminderConfig config = new CertificationDefinition.NotificationConfig.ReminderConfig();
        config.setEnabled(fromDb.isEnabled());

        config.setStartHowManyDays((int) (fromDb.getMillis() / Util.MILLI_IN_DAY));
        config.setOnceEveryHowManyDays((int) (fromDb.getFrequency() / Util.MILLI_IN_DAY));
        config.setBefore(fromDb.isBefore());
        config.setOnce(fromDb.isOnce());
        config.setCount(fromDb.getCount());
        config.setFromDb(true);
        config.setAdditionalEmailRecipientsPresent(fromDb
                .isAdditionalRecipientsPresent());
        if (config.isAdditionalEmailRecipientsPresent()) {
            if (fromDb.getAdditionalRecipientsRuleName() != null) {
                Rule rule = resolver.getObjectByName(Rule.class, fromDb
                        .getAdditionalRecipientsRuleName());
                if (rule != null) {
                    config.setRecipientsRuleName(rule.getName());
                }
            }
            List<String> additionalRecipientNames = fromDb
                    .getAdditionalRecipientNames();
            List<CertificationDefinition.NotificationConfig.RecipientInfo> additionalRecipients = new ArrayList<CertificationDefinition.NotificationConfig.RecipientInfo>();
            if (additionalRecipientNames != null) {
                for (String name : additionalRecipientNames) {
                    CertificationDefinition.NotificationConfig.RecipientInfo recipientInfo = new CertificationDefinition.NotificationConfig.RecipientInfo();
                    Identity identity = resolver
                            .getObjectByName(Identity.class, name);
                    if (identity != null) {
                        recipientInfo.setId(identity.getId());
                        recipientInfo.setDisplayField(identity
                                .getDisplayableName());
                        recipientInfo
                                .setIcon(identity.isWorkgroup() ? "groupIcon"
                                        : "userIcon");
                        additionalRecipients.add(recipientInfo);
                    }
                }
            }
            config.setAdditionalRecipients(additionalRecipients);
        }
        if (fromDb.getEmailTemplateName() != null) {
            EmailTemplate template = resolver
                    .getObjectByName(EmailTemplate.class, fromDb
                            .getEmailTemplateName());
            if (template != null) {
                config.setEmailTemplateId(template.getId());
            }
        }

        return config;
    }

    private static CertificationDefinition.NotificationConfig.EscalationConfig createOneRestEscalationConfig(Resolver resolver, EscalationConfig fromDb)
            throws GeneralException {

        CertificationDefinition.NotificationConfig.EscalationConfig config = new CertificationDefinition.NotificationConfig.EscalationConfig();
        config.setEnabled(fromDb.isEnabled());

        config.setStartHowManyDays((int) (fromDb.getMillis() / Util.MILLI_IN_DAY));
        config.setOnceEveryHowManyDays((int) (fromDb.getFrequency() / Util.MILLI_IN_DAY));
        config.setBefore(fromDb.isBefore());
        config.setCount(fromDb.getCount());
        config.setFromDb(true);

        config.setAdditionalEmailRecipientsPresent(fromDb
                .isAdditionalRecipientsPresent());
        if (config.isAdditionalEmailRecipientsPresent()) {
            if (fromDb.getAdditionalRecipientsRuleName() != null) {
                Rule rule = resolver.getObjectByName(Rule.class, fromDb
                        .getAdditionalRecipientsRuleName());
                if (rule != null) {
                    config.setRecipientsRuleName(rule.getName());
                }
            }
            List<String> additionalRecipientNames = fromDb
                    .getAdditionalRecipientNames();
            List<CertificationDefinition.NotificationConfig.RecipientInfo> additionalRecipients = new ArrayList<CertificationDefinition.NotificationConfig.RecipientInfo>();
            if (additionalRecipientNames != null) {
                for (String name : additionalRecipientNames) {
                    CertificationDefinition.NotificationConfig.RecipientInfo recipientInfo = new CertificationDefinition.NotificationConfig.RecipientInfo();
                    Identity identity = resolver
                            .getObjectByName(Identity.class, name);
                    if (identity != null) {
                        recipientInfo.setId(identity.getId());
                        recipientInfo.setDisplayField(identity
                                .getDisplayableName());
                        recipientInfo
                                .setIcon(identity.isWorkgroup() ? "groupIcon"
                                        : "userIcon");
                        additionalRecipients.add(recipientInfo);
                    }
                }
            }
            config.setAdditionalRecipients(additionalRecipients);
        }
        if (fromDb.getEmailTemplateName() != null) {
            EmailTemplate template = resolver
                    .getObjectByName(EmailTemplate.class, fromDb
                            .getEmailTemplateName());
            if (template != null) {
                config.setEmailTemplateId(template.getId());
            }
        }

        if (fromDb.getEscalationRuleName() != null) {
            Rule rule = resolver.getObjectByName(Rule.class, fromDb
                    .getEscalationRuleName());
            if (rule != null) {
                config.setEscalationRuleId(rule.getName());
            }
        }

        config.setMaxReminders(fromDb.getMaxReminders());

        return config;
    }

    public static NotificationConfig createFromJsonString(Resolver resolver, String jsonString)
            throws GeneralException {

        CertificationDefinition.NotificationConfig jsonObject = deserializeJsonString(jsonString);
        if (jsonObject == null) {
            return null;
        }
        
        return createFromJsonObject(resolver, jsonObject);
    }
    
    @SuppressWarnings("unchecked")
    private static CertificationDefinition.NotificationConfig deserializeJsonString(String jsonString) {
        
        if (Util.isNullOrEmpty(jsonString)) {
            return null;
        }
        
        try {
            return JsonHelper.fromJson(CertificationDefinition.NotificationConfig.class, jsonString);
        } catch (Exception ex) {
            if (log.isErrorEnabled()) {
                log.error("Error deserializing reminders data from page", ex);
            }
            return null;
        }
    }
    
    public static NotificationConfig createFromJsonObject(Resolver resolver, CertificationDefinition.NotificationConfig jsonConfig) 
        throws GeneralException {

        NotificationConfig result = new NotificationConfig();
    
        if (jsonConfig.getConfigs() != null) {
            result.setConfigs(new ArrayList<NotificationConfig.IConfig>());
            for (CertificationDefinition.NotificationConfig.IConfig config : jsonConfig.getConfigs()) {
                if (config instanceof CertificationDefinition.NotificationConfig.ReminderConfig) {
                    result.getConfigs().add(createReminderConfig(resolver, (CertificationDefinition.NotificationConfig.ReminderConfig) config));
                } else {
                    result.getConfigs().add(createEscalationConfig(resolver, (CertificationDefinition.NotificationConfig.EscalationConfig) config));
                }
            }
        }
        
        result.setEnabled(jsonConfig.isEnabled() || jsonConfig.isAnyEnabled());
    
        return result;
    }
    
    private static ReminderConfig createReminderConfig(Resolver resolver, CertificationDefinition.NotificationConfig.ReminderConfig jsonReminderConfig) throws GeneralException {
        
        ReminderConfig oneConfig = new ReminderConfig();
        
        oneConfig.setEnabled(jsonReminderConfig.isEnabled());
        
        if (jsonReminderConfig.isAdditionalEmailRecipientsPresent()) {
            oneConfig.setAdditionalRecipientsPresent(true);
            
            List<String> additionalRecipientNames = new ArrayList<String>();
            for (CertificationDefinition.NotificationConfig.RecipientInfo recipient : jsonReminderConfig.getAdditionalRecipients()) {
                Identity identity = resolver.getObjectById(Identity.class, recipient.getId());
                if (identity != null) {
                    additionalRecipientNames.add(identity.getName());
                }
            }
            oneConfig.setAdditionalRecipientNames(additionalRecipientNames);
            
            if (jsonReminderConfig.getRecipientsRuleName() != null) {
                Rule rule = resolver.getObjectByName(Rule.class, jsonReminderConfig.getRecipientsRuleName());
                if (rule != null) {
                    oneConfig.setAdditionalRecipientsRuleName(rule.getName());
                }
            }
        } else {
            oneConfig.setAdditionalRecipientsPresent(false);
        }
        
        oneConfig.setOnce(jsonReminderConfig.isOnce());
        oneConfig.setBefore(jsonReminderConfig.isBefore());
        if (jsonReminderConfig.getEmailTemplateId() != null) {
            EmailTemplate template = resolver.getObjectById(EmailTemplate.class, jsonReminderConfig.getEmailTemplateId());
            if (template != null) {
                oneConfig.setEmailTemplateName(template.getName());
            }
        }
        oneConfig.setFrequency(jsonReminderConfig.getOnceEveryHowManyDays() * Util.MILLI_IN_DAY);
        oneConfig.setMillis(jsonReminderConfig.getStartHowManyDays() * Util.MILLI_IN_DAY);
        return oneConfig;
    }

    private static EscalationConfig createEscalationConfig(Resolver resolver, CertificationDefinition.NotificationConfig.EscalationConfig jsonEscalationConfig) throws GeneralException {
 
        EscalationConfig oneConfig = new EscalationConfig();
        oneConfig.setEnabled(jsonEscalationConfig.isEnabled());
        
        if (jsonEscalationConfig.isAdditionalEmailRecipientsPresent()) {
            oneConfig.setAdditionalRecipientsPresent(true);
            
            List<String> additionalRecipientNames = new ArrayList<String>();
            for (CertificationDefinition.NotificationConfig.RecipientInfo recipient : jsonEscalationConfig.getAdditionalRecipients()) {
                Identity identity = resolver.getObjectById(Identity.class, recipient.getId());
                if (identity != null) {
                    additionalRecipientNames.add(identity.getName());
                }
            }
            oneConfig.setAdditionalRecipientNames(additionalRecipientNames);
            
            if (jsonEscalationConfig.getRecipientsRuleName() != null) {
                Rule rule = resolver.getObjectByName(Rule.class, jsonEscalationConfig.getRecipientsRuleName());
                if (rule != null) {
                    oneConfig.setAdditionalRecipientsRuleName(rule.getName());
                }
            }
        } else {
            oneConfig.setAdditionalRecipientsPresent(false);
        }
        
        oneConfig.setBefore(jsonEscalationConfig.isBefore());
   
        if (jsonEscalationConfig.getEmailTemplateId() != null) {
            EmailTemplate template = resolver.getObjectById(EmailTemplate.class, jsonEscalationConfig.getEmailTemplateId());
            if (template != null) {
                oneConfig.setEmailTemplateName(template.getName());
            }
        }
        
        if (jsonEscalationConfig.getEscalationRuleId() != null) {
            Rule rule = resolver.getObjectByName(Rule.class, jsonEscalationConfig.getEscalationRuleId());
            if (rule != null) {
                oneConfig.setEscalationRuleName(rule.getName());
            }
        }
        
        oneConfig.setFrequency(jsonEscalationConfig.getOnceEveryHowManyDays() * Util.MILLI_IN_DAY);
        oneConfig.setMillis(jsonEscalationConfig.getStartHowManyDays() * Util.MILLI_IN_DAY);
        oneConfig.setBefore(jsonEscalationConfig.isBefore());
        oneConfig.setMaxReminders(jsonEscalationConfig.getMaxReminders());
        return oneConfig;
    }
    
    public static String createJsonString(SailPointContext context, NotificationConfig configFromDb) 
        throws GeneralException {

        if (configFromDb == null) {
            configFromDb = new NotificationConfig();
        }
        
        CertificationDefinition.NotificationConfig config = createRestNotificationConfig(context, configFromDb);
        
        return JsonHelper.toJson(config);
    }
    
    public static CertificationDefinition.NotificationConfig.EscalationConfig createDefaultRestEscalationConfig(SailPointContext context, boolean maxPresentInEscalation)
        throws GeneralException {

        CertificationDefinition.NotificationConfig.EscalationConfig escalationConfig = new CertificationDefinition.NotificationConfig.EscalationConfig();
        
        if (maxPresentInEscalation) {
            escalationConfig.setMaxReminders(context.getConfiguration().getAttributes().getInt(Configuration.DEFAULT_MAX_REMINDERS, 3));
        } 
        
        String ruleName = context.getConfiguration().getString(Configuration.DEFAULT_ESCALATION_RULE);
        if (ruleName != null) {
            Rule rule = context.getObjectByName(Rule.class, ruleName);
            if (rule != null) {
                escalationConfig.setEscalationRuleId(rule.getId());
            }
        }
        
        String escalationEmailTemplateName = context.getConfiguration().getString(Configuration.DEFAULT_ESCALATION_EMAIL_TEMPLATE);
        if (escalationEmailTemplateName != null) {
            EmailTemplate template = context.getObjectByName(EmailTemplate.class, escalationEmailTemplateName);
            if (template != null) {
                escalationConfig.setEmailTemplateId(template.getId());
            }
        }
        
        escalationConfig.setBefore(true);
        escalationConfig.setStartHowManyDays(7);
        escalationConfig.setFromDb(false);
        
        escalationConfig.setOnceEveryHowManyDays(0);
        
        return escalationConfig;
    }

    public static CertificationDefinition.NotificationConfig.ReminderConfig createDefaultRestReminderConfig(SailPointContext context)
        throws GeneralException {
    
        CertificationDefinition.NotificationConfig.ReminderConfig reminderConfig = new CertificationDefinition.NotificationConfig.ReminderConfig();
        
        reminderConfig.setBefore(true);
        reminderConfig.setStartHowManyDays((int) (Configuration.getSystemConfig().getLong(Configuration.DEFAULT_REMINDER_START) / Util.MILLI_IN_DAY));
        
        reminderConfig.setOnce(true);
        reminderConfig.setFromDb(false);
        
        String emailTemplateName = context.getConfiguration().getString(Configuration.DEFAULT_REMINDER_EMAIL_TEMPLATE);
        if (emailTemplateName != null) {
            EmailTemplate template = context.getObjectByName(EmailTemplate.class, emailTemplateName);
            if (template != null) {
                reminderConfig.setEmailTemplateId(template.getId());
            }
        }
        
        return reminderConfig;
    }
    
}
