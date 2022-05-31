/* (c) Copyright 2018 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.service.certification.schedule;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import sailpoint.api.SailPointContext;
import sailpoint.object.Configuration;
import sailpoint.object.EmailTemplate;
import sailpoint.object.Identity;
import sailpoint.object.NotificationConfig;
import sailpoint.object.Rule;
import sailpoint.service.suggest.SuggestHelper;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

/**
 * This DTO holds information about Certification notifications, such as reminders or escalations.
 * It is based on the DTO defined in {@link sailpoint.object.CertificationDefinition.NotificationConfig},
 * but removes the fields and methods used exclusively by JSF. It also uses Maps for email templates, rules and
 * identities for compatibility with suggests.
 */
public class NotificationConfigDTO {
    public interface IConfig {
        String getType();
        boolean isBefore();
        void setBefore(boolean val);
        int getStartHowManyDays();
        void setStartHowManyDays(int val);
        int getOnceEveryHowManyDays();
        void setOnceEveryHowManyDays(int val);
        List<Map<String, Object>> getAdditionalRecipients();
        void setAdditionalRecipients(List<Map<String, Object>> val);
        Map<String, Object> getEmailTemplate();
        void setEmailTemplate(Map<String, Object> val);
        Map<String, Object> getRecipientsRule();
        void setRecipientsRule(Map<String, Object> val);
    }

    public static abstract class ConfigBase implements IConfig {

        private int startHowManyDays;
        private int onceEveryHowManyDays;
        private boolean before;
        private boolean saved;
        private List<Map<String, Object>> additionalRecipients;
        private Map<String, Object> emailTemplate;
        private Map<String, Object> recipientsRule;

        protected ConfigBase() {}

        protected ConfigBase(NotificationConfig.IConfig config, SailPointContext context) throws GeneralException {
            setStartHowManyDays((int) (config.getMillis() / Util.MILLI_IN_DAY));
            setOnceEveryHowManyDays((int) (config.getFrequency() / Util.MILLI_IN_DAY));
            setBefore(config.isBefore());
            setSaved(true);

            if (config.getEmailTemplateName() != null) {
                Map<String, Object> template =
                        SuggestHelper.getSuggestObject(EmailTemplate.class, config.getEmailTemplateName(), context);
                if (template != null) {
                    setEmailTemplate(template);
                }
            }

            if (config.getAdditionalRecipientsRuleName() != null) {
                Map<String, Object> rule =
                        SuggestHelper.getSuggestObject(Rule.class, config.getAdditionalRecipientsRuleName(), context);
                if (rule != null) {
                    setRecipientsRule(rule);
                }
            }

            if (config.getAdditionalRecipientNames() != null) {
                ArrayList<Map<String, Object>> recipients = new ArrayList<>();

                for (String recipient : config.getAdditionalRecipientNames()) {
                    Map<String, Object> identity = SuggestHelper.getSuggestObject(Identity.class, recipient, context);

                    if (identity != null) {
                        recipients.add(identity);
                    }
                }

                setAdditionalRecipients(recipients);
            }
        }

        public int getStartHowManyDays() {
            return this.startHowManyDays;
        }

        public void setStartHowManyDays(int val) {
            startHowManyDays = val;
        }

        public int getOnceEveryHowManyDays() {
            return onceEveryHowManyDays;
        }

        public void setOnceEveryHowManyDays(int val) {
            onceEveryHowManyDays = val;
        }

        public boolean isBefore() {
            return before;
        }

        public void setBefore(boolean val) {
            before = val;
        }

        public boolean isSaved() {
            return saved;
        }

        public void setSaved(boolean saved) {
            this.saved = saved;
        }

        public List<Map<String, Object>> getAdditionalRecipients() {
            return additionalRecipients;
        }

        public void setAdditionalRecipients(List<Map<String, Object>> val) {
            additionalRecipients = val;
        }

        public Map<String, Object> getEmailTemplate() {
            return emailTemplate;
        }

        public void setEmailTemplate(Map<String, Object> val) {
            emailTemplate = val;
        }

        public Map<String, Object> getRecipientsRule() {
            return recipientsRule;
        }

        public void setRecipientsRule(Map<String, Object> val) {
            recipientsRule = val;
        }
    }

    public static class ReminderConfigDTO extends ConfigBase implements IConfig {

        public static final String TYPE_STRING = "Reminder";

        private boolean once;

        public ReminderConfigDTO() {}

        public ReminderConfigDTO(NotificationConfig.ReminderConfig config, SailPointContext context) throws GeneralException {
            super(config, context);
            setOnce(config.isOnce());
        }

        public String getType() {
            return TYPE_STRING;
        }

        public boolean isOnce() {
            return once;
        }

        public void setOnce(boolean val) {
            once = val;
        }

        /**
         * Returns a default ReminderConfigDTO with settings pulled from global settings.
         * For items that aren't from global, the defaults set here were originally from NotificationConfig.
         * @param context SailPointContext to use
         * @return ReminderConfigDTO with default values set
         * @throws GeneralException
         */
        public static ReminderConfigDTO getDefault(SailPointContext context) throws GeneralException {
            ReminderConfigDTO reminderConfig = new ReminderConfigDTO();

            reminderConfig.setBefore(true);
            reminderConfig.setStartHowManyDays((int) (Configuration.getSystemConfig().getLong(Configuration.DEFAULT_REMINDER_START) / Util.MILLI_IN_DAY));
            reminderConfig.setOnce(true);

            String emailTemplateName = context.getConfiguration().getString(Configuration.DEFAULT_REMINDER_EMAIL_TEMPLATE);
            if (emailTemplateName != null) {
                Map<String, Object> template =
                        SuggestHelper.getSuggestObject(EmailTemplate.class, emailTemplateName, context);

                if (template != null) {
                    reminderConfig.setEmailTemplate(template);
                }
            }

            return reminderConfig;
        }
    }

    public static class EscalationConfigDTO extends ConfigBase implements IConfig {

        public static final String TYPE_STRING = "Escalation";

        private int maxReminders;
        private Map<String, Object> escalationRule;

        public EscalationConfigDTO() {}

        public EscalationConfigDTO(NotificationConfig.EscalationConfig config, SailPointContext context) throws GeneralException {
            super(config, context);
            setMaxReminders(config.getMaxReminders());

            if (config.getEscalationRuleName() != null) {
                Map<String, Object> rule =
                        SuggestHelper.getSuggestObject(Rule.class, config.getEscalationRuleName(), context);
                if (rule != null) {
                    setEscalationRule(rule);
                }
            }
        }

        public String getType() {
            return TYPE_STRING;
        }

        public int getMaxReminders() {
            return this.maxReminders;
        }

        public void setMaxReminders(int val) {
            this.maxReminders = val;
        }

        public Map<String, Object> getEscalationRule() {
            return this.escalationRule;
        }

        public void setEscalationRule(Map<String, Object> val) {
            this.escalationRule = val;
        }

        /**
         * Returns a default EscalationConfigDTO with settings pulled from global settings.
         * For items that aren't from global, the defaults set here were originally from NotificationConfig.
         * @param context SailPointContext to use
         * @return EscalationConfigDTO with default values set
         * @throws GeneralException
         */
        public static EscalationConfigDTO getDefault(SailPointContext context) throws GeneralException {
            EscalationConfigDTO escalationConfig = new EscalationConfigDTO();

            escalationConfig.setMaxReminders(context.getConfiguration().getAttributes().getInt(Configuration.DEFAULT_MAX_REMINDERS, 3));

            String ruleName = context.getConfiguration().getString(Configuration.DEFAULT_ESCALATION_RULE);
            if (ruleName != null) {
                Map<String, Object> rule = SuggestHelper.getSuggestObject(Rule.class, ruleName, context);

                if (rule != null) {
                    escalationConfig.setEscalationRule(rule);
                }
            }

            String escalationEmailTemplateName = context.getConfiguration().getString(Configuration.DEFAULT_ESCALATION_EMAIL_TEMPLATE);
            if (escalationEmailTemplateName != null) {
                Map<String, Object> template = SuggestHelper.getSuggestObject(EmailTemplate.class, escalationEmailTemplateName, context);

                if (template != null) {
                    escalationConfig.setEmailTemplate(template);
                }
            }

            escalationConfig.setBefore(true);
            escalationConfig.setStartHowManyDays(7);

            escalationConfig.setOnceEveryHowManyDays(0);

            return escalationConfig;
        }
    }

    public NotificationConfigDTO(NotificationConfig config, SailPointContext context) throws GeneralException {
        configs = new ArrayList<IConfig>();

        if (config != null && config.getConfigs() != null) {
            for (NotificationConfig.IConfig configItem : config.getConfigs()) {
                if (configItem instanceof NotificationConfig.ReminderConfig) {
                    configs.add(new ReminderConfigDTO((NotificationConfig.ReminderConfig)configItem, context));
                }
                else {
                    configs.add(new EscalationConfigDTO((NotificationConfig.EscalationConfig)configItem, context));
                }
            }
        }
    }

    private List<IConfig> configs;

    public List<IConfig> getConfigs() { return configs; }

    public void setConfigs(List<IConfig> newConfigs) { configs = newConfigs; }
}
