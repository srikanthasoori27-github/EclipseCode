/* (c) Copyright 2009 SailPoint Technologies, Inc., All Rights Reserved. */
/*
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */

package sailpoint.object;

import java.io.Serializable;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import sailpoint.api.ObjectUtil;
import sailpoint.api.SailPointContext;
import sailpoint.object.Attributes.AttributesGetter;
import sailpoint.object.Attributes.AttributesSetter;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;
import sailpoint.web.messages.MessageKeys;

/**
 * Certification definitions hold information that specify how certifications
 * should be created and what behavior they should exhibit. For example, phase
 * durations, notifications/reminder configuration, type of certification,
 * etc. are configured here.
 *
 * @ignore Implementation note: Historically, this information used to live in a
 * TaskSchedule.  As such, all parameters were just stuck in the attributes map
 * on the schedule and converted to/from a CertificationScheduleDTO using the
 * CertificationScheduler.  There are many reasons that it is desirable to
 * split this out into its own class - they can be reused, they can be used by
 * non-scheduled certifications (ie - event-based), etc...  For ease of
 * migration, I'm leaving everything in the attributes map and letting the
 * CertificationScheduler and CertificationScheduleDTO put methods around
 * accessing the information in here.  Eventually, we may want to pull the
 * conversion logic out of the CertificationScheduler and into this class.
 * This would largely remove the need for the DTO and make life simpler.  I
 * am in favor of keeping much of the data in an attributes map rather than
 * individual fields (especially for primitives and very simple objects), since
 * this makes it easier to add new options.
 */
@SuppressWarnings("serial")
@XMLClass
public class CertificationDefinition extends SailPointObject {

    ////////////////////////////////////////////////////////////////////////////
    //
    // ANNOTATION CLASSES
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * @exclude
     *
     * Annotation that should be added to AttributesSetter methods for attributes that are allowed
     * to be edited after the certifications have been generated. The value should be the latest phase
     * that the attribute is allowed be edited in.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public @interface EditablePhase {

        Certification.Phase value();
    }

    ////////////////////////////////////////////////////////////////////////////
    //
    // ENUMERATIONS
    //
    ////////////////////////////////////////////////////////////////////////////

    public static enum CertifierSelectionType {
        Owner("Owner"),
        Manager("Manager"),
        Rule("Rule"),
        Manual("Manual");

        private String name;

        private CertifierSelectionType(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    ////////////////////////////////////////////////////////////////////////////
    //
    // INNER CLASSES
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Inner class that holds information about the group definitions and
     * associated certifiers when certifying with an iPOP population.
     */
    public static class GroupBean implements Serializable {
        private GroupDefinition groupDefinition;
        private List<Identity> certifiers;
        private boolean checked;

        /**
         * Default constructor.
         */
        public GroupBean() {
        }

        /**
         * Constructor.
         *
         * @param groupDefinition The GroupDefinition being certified.
         */
        public GroupBean(GroupDefinition groupDefinition) {
            this.groupDefinition = groupDefinition;
        }

        public GroupBean(GroupDefinition groupDefinition, List<Identity> certifiers) {
            this.groupDefinition = groupDefinition;
            this.certifiers = certifiers;
        }

        public GroupDefinition getGroupDefinition() {
            return this.groupDefinition;
        }

        public List<Identity> getCertifiers() {
            return certifiers;
        }

        public void setCertifiers(List<Identity> certifiers) {
            this.certifiers = certifiers;
        }

        public boolean isChecked() {
            return checked;
        }

        public void setChecked(boolean checked) {
            this.checked = checked;
        }
    }

    /**
     * Inner class that holds information about the group factories and
     * associated certifier rules when certifying factories.
     */
    public static class FactoryBean implements Serializable {

        private String id;
        private String name;
        private String certifierRuleName;

        private boolean checked;

        public FactoryBean() {
        }

        public FactoryBean(String id, String name) {
            this.id = id;
            this.name = name;
        }

        public String getId() {
            return this.id;
        }

        public String getName() {
            return this.name;
        }

        public String getCertifierRuleName() {
            return certifierRuleName;
        }

        public void setCertifierRuleName(String ruleName) {
            this.certifierRuleName = ruleName;
        }

        public boolean isChecked() {
            return checked;
        }

        public void setChecked(boolean checked) {
            this.checked = checked;
        }

    }


    /**
     * This class holds information about notifications.
     * There can be multiple notifications and escalations
     * in a notification. This is a more UI Friendly version
     * of {@link sailpoint.object.NotificationConfig} class
     * 
     * @ignore
     * Bug #5900.
     * Some attributes and fields are present for backward 
     * compatibility only. Please do not use them for new code.
     */
    public static class NotificationConfig implements Serializable {
        
        private static final long serialVersionUID = -7394224559837568572L;

        @JsonTypeInfo(
                use = JsonTypeInfo.Id.NAME,
                property = "type")
        @JsonSubTypes({
                @JsonSubTypes.Type(value = ReminderConfig.class, name = CertificationDefinition.NotificationConfig.ReminderConfig.TYPE_STRING),
                @JsonSubTypes.Type(value = EscalationConfig.class, name = CertificationDefinition.NotificationConfig.EscalationConfig.TYPE_STRING)
        })
        public interface IConfig {
            // This is needed for Json serialization
            // on the javascript side.
            public String getType();
            public void setType(String val);
            boolean isEnabled();
            void setEnabled(boolean val);
            boolean isBefore();
            void setBefore(boolean val);
            int getStartHowManyDays();
            void setStartHowManyDays(int val);
            int getOnceEveryHowManyDays();
            void setOnceEveryHowManyDays(int val);
            boolean isAdditionalEmailRecipientsPresent();
            void setAdditionalEmailRecipientsPresent(boolean val);
            List<RecipientInfo> getAdditionalRecipients();
            void setAdditionalRecipients(List<RecipientInfo> val);
            String getEmailTemplateId();
            void setEmailTemplateId(String val);
            String getRecipientsRuleName();
            void setRecipientsRuleName(String val);
            boolean isFromDb();
            void setFromDb(boolean val);
            int getCount();
            void setCount(int val);
        }
        
        public static abstract class ConfigBase implements IConfig {
            
            private boolean enabled;
            private int startHowManyDays;
            private int onceEveryHowManyDays;
            private boolean before;
            private boolean additionalEmailRecipientsPresent;
            private List<RecipientInfo> additionalRecipients;
            private String emailTemplateId;
            private String recipientsRuleName;
            private int count;
            private boolean fromDb;
        
            public boolean isEnabled() {
                return enabled;
            }
            
            public void setEnabled(boolean val) {
                enabled = val;
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
        
            public boolean isAdditionalEmailRecipientsPresent() {
                return additionalEmailRecipientsPresent;
            }
        
            public void setAdditionalEmailRecipientsPresent(boolean val) {
                additionalEmailRecipientsPresent = val;
            }
        
            public List<RecipientInfo> getAdditionalRecipients() {
                return additionalRecipients;
            }
            
            public void setAdditionalRecipients(List<RecipientInfo> val) {
                additionalRecipients = val;
            }
            
            public String getEmailTemplateId() {
                return emailTemplateId;
            }
            
            public void setEmailTemplateId(String val) {
                emailTemplateId = val;
            }
        
            public String getRecipientsRuleName() {
                return recipientsRuleName;
            }
        
            public void setRecipientsRuleName(String val) {
                recipientsRuleName = val;
            }
            
            public int getCount() {
                return count;
            }
            
            public void setCount(int val) {
                count = val;
            }
            
            public boolean isFromDb() {
                return fromDb;
            }
            
            public void setFromDb(boolean val) {
                fromDb = val;
            }
        }
        
        public static class ReminderConfig extends ConfigBase implements IConfig {

            public static final String TYPE_STRING = "Reminder";
            
            private boolean once;

            public ReminderConfig() {}
            
            public String getType() {
                return TYPE_STRING;
            }
            
            public void setType(String val) {}
            
            public boolean isOnce() {
                return once;
            }
        
            public void setOnce(boolean val) {
                once = val;
            }
            
        }

        public static class EscalationConfig extends ConfigBase implements IConfig {

            public static final String TYPE_STRING = "Escalation";
            
            private int maxReminders;
            private String escalationRuleId;
            
            public EscalationConfig() {}
            
            public String getType() {
                return TYPE_STRING;
            }
            
            public void setType(String val) {}
            
            public int getMaxReminders() {
                return this.maxReminders;
            }
            
            public void setMaxReminders(int val) {
                this.maxReminders = val;
            }
            
            public String getEscalationRuleId() {
                return this.escalationRuleId;
            }
            
            public void setEscalationRuleId(String val) {
                this.escalationRuleId = val;
            }
        }

        public static class RecipientInfo {
            private String id;
            private String displayField;
            private String icon;
            
            public String getId() {
                return id;
            }
            
            public void setId(String val) {
                this.id = val;
            }
            
            public String getDisplayField() {
                return displayField;
            }
            
            public void setDisplayField(String val) {
                displayField = val;
            }
            
            public String getIcon() {
                return icon;
            }
            
            public void setIcon(String val) {
                icon = val;
            }
        }

        private boolean enabled;
        private List<NotificationConfig.IConfig> configs;
        
        private Date startDate;
        private Date endDate;
        
        /**
         * When creating new reminder/escalation using ui the following
         * values should be used.
         */
        private NotificationConfig.ReminderConfig defaultReminderConfigForNew;
        private NotificationConfig.EscalationConfig defaultEscalationConfigForNew;
        
        public NotificationConfig() {}
        
        public boolean isEnabled() {
            return this.enabled;
        }
    
        public void setEnabled(boolean val) {
            this.enabled = val;
        }
        
        public List<NotificationConfig.IConfig> getConfigs() {
            return configs;
        }
        
        public void setConfigs(List<NotificationConfig.IConfig> val) {
            configs = val;
        }
    
        public Date getStartDate() {
            return startDate;
        }
    
        public void setStartDate(Date val) {
            startDate = val;
        }
    
        public Date getEndDate() {
            return endDate;
        }
    
        public void setEndDate(Date val) {
            endDate = val;
        }
        
        @Deprecated
        @JsonIgnore
        public NotificationConfig.ReminderConfig getDefaultReminderConfig() {
            if (configs != null && configs.size() > 0) {
                return (NotificationConfig.ReminderConfig) configs.get(0);
            }
            return null;
        }
        
        @Deprecated
        @JsonIgnore
        public NotificationConfig.EscalationConfig getDefaultEscalationConfig() {
            if (configs != null && configs.size() > 1) {
                return (NotificationConfig.EscalationConfig) configs.get(1);
            }
            return null;
        }
        
        public NotificationConfig.ReminderConfig getDefaultReminderConfigForNew() {
            return defaultReminderConfigForNew;
        }
        
        public void setDefaultReminderConfigForNew(NotificationConfig.ReminderConfig val) {
            defaultReminderConfigForNew = val;
        }
        
        public NotificationConfig.EscalationConfig getDefaultEscalationConfigForNew() {
            return defaultEscalationConfigForNew;
        }
        
        public void setDefaultEscalationConfigForNew(NotificationConfig.EscalationConfig val) {
            defaultEscalationConfigForNew = val;
        }
        
        public boolean isAnyEnabled() {
            
            boolean anyEnabled = false;
            
            for (IConfig config : getConfigs()) {
                if (config.isEnabled()) {
                    anyEnabled = true;
                    break;
                }
            }
            
            return anyEnabled;
        }
        
        // EVERYTHING BELOW IS FOR BACKWARD COMPATIBILITY ONLY.
        // PLEASE DON'T USE.
        // BEGIN DEPRECATED ========================================================================>
        
        private static final String FREQ_ONCE = "Once";
        private static final String FREQ_DAILY = "Daily";
        private static final String FREQ_WEEKLY = "Weekly";

        // New Values
        private static final String FREQ_ONCE_EVERY = "OnceEvery";

        // Email Settings
        @Deprecated
        private boolean sendReminders;
        @Deprecated
        private String reminderEmailTemplate;
        @Deprecated
        private boolean once;
        @Deprecated
        private Long reminderFrequencyMillis;
        @Deprecated
        private Long reminderStartMillisBeforeEnd;

        // Escalation Settings
        @Deprecated
        private boolean escalate;
        @Deprecated
        private String escalationRule;
        @Deprecated
        private String escalationEmailTemplate;
        @Deprecated
        private Long escalationFrequencyMillis;
        @Deprecated
        private Long escalationStartMillisBeforeEnd;
        @Deprecated
        private Integer maxReminders;

        /////////////////////////////////////////////////////////////////
        //
        // UI HELPER METHODS - these convert between milliseconds and
        // the format used by the UI.
        //
        /////////////////////////////////////////////////////////////////

        @Deprecated
        public Long getReminderStartDaysBeforeEnd() {
            return (null != this.reminderStartMillisBeforeEnd) ?
                    this.reminderStartMillisBeforeEnd / Util.MILLIS_PER_DAY : null;
        }

        @Deprecated
        public void setReminderStartDaysBeforeEnd(Long days) {
            if (null != days) {
                this.reminderStartMillisBeforeEnd = days * Util.MILLIS_PER_DAY;
            }
        }

        @Deprecated
        public String getReminderFrequency() throws GeneralException {
            if (this.reminderFrequencyMillis == null) {
                return FREQ_ONCE;
            } else {
                return FREQ_ONCE_EVERY;
            }
        }
        
        @Deprecated
        public boolean isOnce() {
            return this.once;
        }

        @Deprecated
        public void setReminderFrequency(String freq) throws GeneralException {
            if (FREQ_ONCE.equals(freq)) {
                this.once = true;
            } else {
                this.once = false;
            }
        }

        @Deprecated
        public Long getReminderFrequencyDays() throws GeneralException {
            return millisToFrequencyDays(this.reminderFrequencyMillis);
        }

        @Deprecated
        public void setReminderFrequencyDays(Long val) throws GeneralException {
            this.reminderFrequencyMillis = frequencyDaysToFrequencyMillis(val);
        }

        @Deprecated
        public Long getEscalationStartDaysBeforeEnd() {
            return (null != this.escalationStartMillisBeforeEnd) ?
                    this.escalationStartMillisBeforeEnd / Util.MILLIS_PER_DAY : null;
        }

        @Deprecated
        public void setEscalationStartDaysBeforeEnd(Long days) {
            if (null != days) {
                this.escalationStartMillisBeforeEnd = days * Util.MILLIS_PER_DAY;
            }
        }

        @Deprecated
        public String getEscalationFrequency() throws GeneralException {
            return millisToFrequency(this.escalationFrequencyMillis);
        }

        @Deprecated
        public void setEscalationFrequency(String freq) throws GeneralException {
            this.escalationFrequencyMillis = frequencyToMillis(freq);
        }

        //
        // Helper that converts milliseconds to a frequency constant.
        //
        private static String millisToFrequency(Long millis)
                throws GeneralException {

            String freq = FREQ_ONCE;

            if ((null != millis) && (millis > 0)) {

                if (1 == millis / Util.MILLIS_PER_DAY) {
                    freq = FREQ_DAILY;
                } else if (1 == millis / Util.MILLI_IN_WEEK) {
                    freq = FREQ_WEEKLY;
                } else {
                    // Default to once - legacy schedules may store bad values
                    // like 9223372036854775807.  See bug 3785.
                }
            }

            return freq;
        }

        private static Long millisToFrequencyDays(Long millis)
                throws GeneralException {

            if (millis == null) {
                return null;
            }
            return new Double(Math.floor(millis / Util.MILLIS_PER_DAY)).longValue();
        }

        //
        // Helper that converts a frequency constant to milliseconds.
        //
        private static long frequencyToMillis(String freq)
                throws GeneralException {

            long millis = 0;

            if (FREQ_ONCE.equals(freq)) {
                millis = 0L;
            } else if (FREQ_DAILY.equals(freq)) {
                millis = Util.MILLIS_PER_DAY;
            } else if (FREQ_WEEKLY.equals(freq)) {
                millis = Util.MILLI_IN_WEEK;
            } else {
                throw new GeneralException("Unknown reminder frequency: " + millis);
            }

            return millis;
        }

        private static Long frequencyDaysToFrequencyMillis(Long days)
                throws GeneralException {

            if (days == null) {
                return null;
            }
            // TQM: todo need to have a max otherwise there could
            // be overflow
            return Util.MILLIS_PER_DAY * days;
        }


        @Deprecated
        public boolean isSendReminders() {
            return sendReminders;
        }

        @Deprecated
        public void setSendReminders(boolean sendEmailOnExpiration) {
            this.sendReminders = sendEmailOnExpiration;
        }

        @Deprecated
        public String getReminderEmailTemplate() {
            return reminderEmailTemplate;
        }

        @Deprecated
        public void setReminderEmailTemplate(String selectedEmailTemplate) {
            this.reminderEmailTemplate = selectedEmailTemplate;
        }

        @Deprecated
        public Long getReminderFrequencyMillis() {
            return this.reminderFrequencyMillis;
        }

        @Deprecated
        public void setReminderFrequencyMillis(Long millis) {
            this.reminderFrequencyMillis = millis;
        }

        @Deprecated
        public Long getReminderStartMillisBeforeEnd() {
            return this.reminderStartMillisBeforeEnd;
        }

        @Deprecated
        public void setReminderStartMillisBeforeEnd(Long reminderStart) {
            this.reminderStartMillisBeforeEnd = reminderStart;
        }

        @Deprecated
        public boolean isEscalate() {
            return escalate;
        }

        @Deprecated
        public void setEscalate(boolean escalateBeforeExpiring) {
            this.escalate = escalateBeforeExpiring;
        }

        @Deprecated
        public Long getEscalationStartMillisBeforeEnd() {
            return escalationStartMillisBeforeEnd;
        }

        @Deprecated
        public void setEscalationStartMillisBeforeEnd(Long escalationFrequency) {
            this.escalationStartMillisBeforeEnd = escalationFrequency;
        }

        @Deprecated
        public Integer getMaxReminders() {
            return this.maxReminders;
        }

        @Deprecated
        public void setMaxReminders(Integer maxReminders) {
            this.maxReminders = maxReminders;
        }

        @Deprecated
        public String getEscalationRule() {
            return escalationRule;
        }

        @Deprecated
        public void setEscalationRule(String selectedEscalationRule) {
            this.escalationRule = selectedEscalationRule;
        }

        @Deprecated
        public String getEscalationEmailTemplate() {
            return this.escalationEmailTemplate;
        }

        @Deprecated
        public void setEscalationEmailTemplate(String escalationEmailTemplate) {
            this.escalationEmailTemplate = escalationEmailTemplate;
        }

        @Deprecated
        public Long getEscalationFrequencyMillis() {
            return this.escalationFrequencyMillis;
        }

        @Deprecated
        public void setEscalationFrequencyMillis(Long millis) {
            this.escalationFrequencyMillis = millis;
        }
        
        //END DEPRECATED ==========================================================================>
    }


    /**
     * This class holds information about which groups to certify for
     * AccountGroupPermission and AccountGroupMembership certs.
     *
     */
    @XMLClass
    public static class ApplicationGroup {
        private String applicationName;
        private String schemaObjectType;

        @XMLProperty
        public String getApplicationName() {
            return applicationName;
        }

        public void setApplicationName(String applicationName) {
            this.applicationName = applicationName;
        }

        @XMLProperty
        public String getSchemaObjectType() {
            return schemaObjectType;
        }

        public void setSchemaObjectType(String schemaObjectType) {
            this.schemaObjectType = schemaObjectType;
        }
    }

    /**
     * Comma-separated list of the IDs of the owners of the
     * access reviews generated for this certification.
     */
    private static final String ARG_OWNERS = "owners";

    /**
     * A list of {@link sailpoint.object.CertificationDefinition.ApplicationGroup} objects.
     */
    private static final String ARG_APPLICATION_GROUPS = "applicationGroups";

    /**
     * ID of the Certification owner - note this is different from the
     * access review owners.
     */
    public static final String ARG_CERT_OWNER = "certOwner";

    private static final String ARG_TRIGGER_ID = "triggerId";

    public final static String ARG_CERT_NAME_TEMPLATE = "certificationNameTemplate";
    private final static String ARG_LEGACY_CERT_NAME_TEMPLATE = "certNameTemplate";

    private static final String ARG_NAME_TEMPLATE = "nameTemplate";
    private static final String ARG_SHORT_NAME_TEMPLATE = "shortNameTemplate";

    public final static String ARG_CERTIFY_EMPTY_ACCOUNTS = "certifyEmptyAccounts";
    public final static String ARG_CERTIFY_ACCOUNTS = "certifyAccounts";
    public static final String ARG_CERTIFICATION_TYPE = "certificationType";
    private static final String ARG_GLOBAL = "certificationGlobal";

    private static final String ARG_INCLUDED_APPLICATION_IDS = "includedApplications";
    private static final String ARG_INCLUDE_ROLES = "includeRoles";
    public static final String ARG_INCLUDE_ADDITIONAL_ENTITLEMENTS = "includeAdditionalEntitlements";
    private static final String ARG_INCLUDE_POLICY_VIOLATIONS = "includePolicyViolations";
    private static final String ARG_INCLUDE_CAPABILITIES = "includeCapabilities";
    private static final String ARG_INCLUDE_SCOPES = "includeScopes";
    public static final String ARG_INCLUDE_TARGET_PERMISSIONS = "includeTargetPermissions";

    // Continuous certification settings.
    private static final String ARG_CONTINUOUS = "continuous";
    private static final String ARG_CERTIFIED_DURATION_AMOUNT = "certifiedDurationAmount";
    private static final String ARG_CERTIFIED_DURATION_SCALE = "certifiedDurationScale";
    private static final String ARG_CERTIFICATION_REQUIRED_DURATION_AMOUNT = "certificationRequiredDurationAmount";
    private static final String ARG_CERTIFICATION_REQUIRED_DURATION_SCALE = "certificationRequiredDurationScale";

    public static final String ARG_ACTIVE_PERIOD_DURATION_AMOUNT = "activePeriodDurationAmount";
    public static final String ARG_ACTIVE_PERIOD_DURATION_SCALE = "activePeriodDurationScale";

    private static final String ARG_CERTIFICATION_ALLOW_PROV_MISSING_REQS = "allowProvisioningMissingRequirements";
    private static final String ARG_CERTIFICATION_ALLOW_PROV_MISSING_REQS_LEGACY = "allowProvisionMissingRequiredRoles";
    private static final String ARG_CERTIFICATION_REQUIRE_APPROVAL_COMMENTS = "requireApprovalComments";

    private static final String ARG_EXCLUDE_BASE_APP_ACCOUNTS = "excludeBaseAppAccounts";

    public static final String ARG_AUTOMATE_SIGNOFF_POPUP = "automateSignoffPopup"; 
    
    public static final String ARG_SUBORDINATE_CERTIFICATION_ENABLED = "subordinateCertificationEnabled";
    public static final String ARG_FLATTEN_MANAGER_CERTIFICATION_HIERARCHY_ENABLED = "flattenManagerCertificationHierarchy";
    public static final String ARG_COMPLETE_CERTIFICATION_HIERARCHY_ENABLED = "completeCertificationHierarchyEnabled";
    public static final String ARG_ENTITLEMENT_GRANULARITY = "entitlementGranularity";

    private static final String ARG_EXCLUSION_RULE_NAME = "exclusionRuleName";
    private static final String ARG_SAVE_EXCLUSIONS = "saveExclusions";
    public static final String ARG_EXCLUDE_INACTIVE = "excludeInactive";

    public static final String ARG_PRE_DELEGATION_RULE_NAME = "preDelegationRuleName";
    public static final String ARG_ACTIVE_PHASE_ENTER_RULE_NAME = Configuration.CERTIFICATION_ACTIVE_PHASE_ENTER_RULE;
    public static final String ARG_CHALLENGE_PHASE_ENTER_RULE_NAME = Configuration.CERTIFICATION_CHALLENGE_PHASE_ENTER_RULE;
    public static final String ARG_REMEDIATION_PHASE_ENTER_RULE_NAME = Configuration.CERTIFICATION_REMEDIATION_PHASE_ENTER_RULE;
    public static final String ARG_FINISH_PHASE_ENTER_RULE_NAME = Configuration.CERTIFICATION_FINISH_PHASE_ENTER_RULE;
    public static final String ARG_SIGN_OFF_APPROVER_RULE_NAME = "signOffApproverRuleName";

    private static final String ARG_DISPLAY_ENT_DESCS = "displayEntitlementDescriptions";

    private static final String ARG_PROCESS_REVOKES_IMMEDIATELY = "processRevokesImmediately";
    private static final String ARG_SUPPRESS_INITIAL_NOTIFICATION = "suppressInitialNotification";

    public static final String ARG_CHALLENGE_PERIOD_ENABLED = "challengePeriodEnabled";
    public static final String ARG_CHALLENGE_PERIOD_DURATION_AMOUNT = "challengePeriodDurationAmount";
    public static final String ARG_CHALLENGE_PERIOD_DURATION_SCALE = "challengePeriodDurationScale";

    public static final String ARG_REMEDIATION_PERIOD_ENABLED = "remediationPeriodEnabled";
    public static final String ARG_REMEDIATION_PERIOD_DURATION_AMOUNT = "remediationPeriodDurationAmount";
    public static final String ARG_REMEDIATION_PERIOD_DURATION_SCALE = "remediationPeriodDurationScale";

    public static final String ARG_AUTOMATIC_CLOSING_ENABLED = "automaticClosingEnabled";
    public static final String ARG_AUTOMATIC_CLOSING_DURATION_AMOUNT = "automaticClosingDurationAmount";
    public static final String ARG_AUTOMATIC_CLOSING_DURATION_SCALE = "automaticClosingDurationScale";
    public static final String ARG_AUTOMATIC_CLOSING_RULE_NAME = "automaticClosingRuleName";
    private static final String ARG_AUTOMATIC_CLOSING_ACTION = "automaticClosingAction";
    private static final String ARG_AUTOMATIC_CLOSING_COMMENTS = "automaticClosingComments";
    public static final String ARG_AUTOMATIC_CLOSING_SIGNER = "automaticClosingSigner";

    private static final String ARG_INCLUDE_UNOWNED_DATA = "includeUnownedData";
    private static final String ARG_INCLUDE_ENTITLEMENTS_GRANTED_BY_ROLES = "includeEntitlementsGrantedByRoles";
    private static final String ARG_APP_OWNER_IS_UNOWNED_DATA_OWNER = "appOwnerIsUnownedDataOwner";
    private static final String ARG_UNOWNED_DATA_OWNER_NAME = "unownedDataOwnerName";

    private static final String ARG_BUSINESS_ROLE_LIST = "businessRoles";
    private static final String ARG_INCLUDE_ROLE_HIERARCHY = "includeRoleHierarchy";

    private static final String ARG_ROLE_TYPES = "roleTypes";
    private static final String ARG_IDENTITY_LIST = "identities";

    private static final String ARG_IPOP_CERTIFIER_MAP = "iPOPCertifierMap";

    private static final String ARG_FACTORY_CERTIFIER_MAP = "factoryCertifierMap";

    private static final String ARG_ALLOW_LIST_VIEW_BULK_APPROVE = "allowListViewBulkApprove";
    private static final String ARG_ALLOW_LIST_VIEW_BULK_MITIGATE = "allowListViewBulkMitigate";
    private static final String ARG_ALLOW_LIST_VIEW_BULK_REVOKE = "allowListViewBulkRevoke";
    private static final String ARG_ALLOW_LIST_VIEW_BULK_REASSIGN = "allowListViewBulkReassign";

    private static final String ARG_ALLOW_ENTITY_BULK_APPROVE = "allowEntityBulkApprove";

    private static final String ARG_RECOMMENDATIONS_GENERATED = "recommendationsGenerated";

    /**
     * Prefix for certification work item reminder and escalations args.
     */
    public static final String CERTIFICATION_NOTIF_PREFIX = "certification.";
    /**
     * Prefix for remediation work item reminder and escalations args.
     */
    public static final String REMEDIATION_NOTIF_PREFIX = "remediation.";
    /**
     * Prefix for "certification required" reminder args.
     */
    private static final String CERT_REQUIRED_NOTIF_PREFIX = "certificationRequired.";
    /**
     * Prefix for "overdue" escalation args.
     */
    private static final String OVERDUE_NOTIF_PREFIX = "overdue.";

    /**
     * A comma-separated list of application names to certify.
     * TODO: Why refactor something called applicationIds and save names?! -rap
     */
    private static final String ARG_CERTIFIED_APPLICATION_IDS = "certifiedApplicationIds";

    /**
     * Name of the user assigned to this Manager Certification.
     */
    public static final String ARG_CERTIFIER = "certifier";

    /**
     * Names or Names of the identities who will certify the
     * certification items.
     */
    private static final String ARG_CERTIFIERS = "certifiers";

    // Who the certifier should be if it's not manually selected or
    // if the certifier type is not implicit such as in an app owner
    // cert of a manager cert.
    public static final String ARG_CERTIFIER_TYPE = "certifierType";

    private static final String ARG_ITEM_CUSTOMIZATION_RULE = "certificationItemCustomizationRule";

    private static final String ARG_ENTITY_CUSTOMIZATION_RULE = "certificationEntityCustomizationRule";

    private static final String ARG_ENTITY_COMPLETION_RULE = "certificationEntityCompletionRule";

    private static final String ARG_ENTITY_REFRESH_RULE = "certificationEntityRefreshRule";

    public static final String ARG_SEND_PRE_DELEGATION_COMPLETION_EMAILS ="sendPreDelegationCompleteEmails";

    public static final String ARG_SUPPRESS_EMAIL_WHEN_NOTHING_TO_CERTIFY = Configuration.SUPPRESS_EMAIL_WHEN_NOTHING_TO_CERTIFY;

    public static final String ARG_AUTO_SIGN_OFF_WHEN_NOTHING_TO_CERTIFY = Configuration.AUTO_SIGN_OFF_WHEN_NOTHING_TO_CERTIFY;

    //private static final String ARG_TASK_SCHEDULE_ID = "taskScheduleId";
    
    private static final String ARG_STAGING_ENABLED = "stagingEnabled";

    /**
     * ARG_PREFIX + remindersAndEscalations
     * will give the key in attributes for 
     * the notificationconfig data
     */
    public static final String REMINDERS_AND_ESCALATIONS_KEY = "remindersAndEscalations";
        
    /**
     * Flag to indicate that during the certification lifecycle (activation,
     * removal, finalize) the identity entitlements should be updated to
     * reflect the certification. This is enabled by default.
     */
    public static final String ARG_UPDATE_IDENTITY_ENTITLEMENTS = "enableIdentityEntitlementUpdate";
    
    /**
     * Flag to indicate that the certification engine will add/remove assignments stored
     * on the identity when a decision is made during a certification.
     * 
     * This can be used to drive how assignments are made on things like 
     * group membership.
     * 
     * When things are approved assignments are persisted on the identity. 
     * Likewise, when they are rejected assignments are removed from the 
     * identity.
     * 
     * Roles have their own assignment model and this flag only
     * will update assignments for non role entitlements.
     */
    public static final String ARG_ENABLE_ENTITLEMENT_ASSIGNMENTS = "enableEntitlementAssignments";
    
    public static final String ARG_ELECTRONIC_SIGNATURE_REQUIRED = "electronicSignatureRequired";
    
    public static final String ARG_DEFAULT_ASSIGNEE_NAME = "defaultAssigneeName";
    
    /**
     * This constant is used as a placeholder to save the defaultAssigneeName 
     * when no selection has been made from the ui.  Without this, the system default assignee is always loaded.
     */
    private static final String ALLOW_USER_SELECTION = "ALLOW_USER_SELECTION";
    
    /**
     * When set it will use partitioned requests.
     */
    public static final String ARG_ENABLE_PARTITIONING = "enablePartitioning";

    /**
        Configuration to set whether roles that require other roles should be included
        in a Role Membership cert.
    */
    public static final String ARG_INCLUDE_REQUIRED_ROLES = "includeRequiredRoles";

    //
    // New "Targeted" cert options
    // 

    /**
     * How the entities in the certification should be selected, whether Filter, Rule, Population or All
     */
    public static final String ARG_ENTITY_SELECTION_TYPE = "entitySelectionType";
    public static final String ENTITY_SELECTION_TYPE_POPULATION = "Population";
    public static final String ENTITY_SELECTION_TYPE_FILTER = "Filter";
    public static final String ENTITY_SELECTION_TYPE_RULE = "Rule";

    /**
     * Filter for entities in Focused certs
     */
    public static final String ARG_ENTITY_FILTER = "entityFilter";

    /**
     * List of FilterValue maps for entities, used by UI only. ARG_ENTITY_FILTER holds the Filter object.
     */
    public static final String ARG_ENTITY_FILTER_VALUES = "entityFilterValues";

    /**
     * Name of rule used to select entities for Focused cert
     */
    public static final String ARG_ENTITY_RULE = "entityRule";

    /**
     * Name of population to select entities for Focused cert
     */
    public static final String ARG_ENTITY_POPULATION = "entityPopulation";

    /**
     * Filter for Roles in Focused cert
     */
    public static final String ARG_ROLE_FILTER = "roleFilter";
    /**
     * List of FilterValue maps for roles, used by UI only. ARG_ROLE_FILTER holds the Filter object.
     */
    public static final String ARG_ROLE_FILTER_VALUES = "roleFilterValues";

    /**
     * Filter for ManagedAttributes used to limit additional entitlements in Focused cert
     */
    public static final String ARG_ENTITLEMENT_FILTER = "entitlementFilter";
    /**
     * List of FilterValue maps for additional entitlements, used by UI only. ARG_ENTITLEMENT_FILTER_VALUES holds the Filter object.
     */
    public static final String ARG_ENTITLEMENT_FILTER_VALUES = "entitlementFilterValues";

    /**
     * Filter for Links used to limit accounts in Focused cert
     */
    public static final String ARG_ACCOUNT_FILTER = "accountFilter";
    /**
     * List of FilterValue maps for accounts, used by UI only. ARG_ACCOUNT_FILTER holds the Filter object.
     */
    public static final String ARG_ACCOUNT_FILTER_VALUES = "accountFilterValues";

    /**
     * Filter for TargetAssociation used to limit target permissions in Targeted cert
     */
    public static final String ARG_TARGET_PERMISSION_FILTER = "targetPermissionFilter";

    /**
     * List of FilterValue maps for target permissions, used by UI only. ARG_TARGET_PERMISSION_FILTER holds the Filter object.
     */
    public static final String ARG_TARGET_PERMISSION_FILTER_VALUES = "targetPermissionFilterValues";

    /**
     * Filter for link applications to inspect for target permissions. Target permission filtering has these two levels, most of the filtering
     * is on TargetAssociation queried based on a link, but we want to filter the application on the links inspected too. So we keep this filter
     * separated to get a list of apps in CertifiableAnalyzer.
     */
    public static final String ARG_TARGET_PERMISSION_APP_FILTER = "targetPermissionAppFilter";

    /**
     * Boolean option to enable the creation of ArchivedCertificationEntity for 
     * entities that have been excluded by either being inactive or from by the
     * exclusion rule.  Before targeted certs this was the default.
     */
    public static final String ARG_INCLUDE_ARCHIVED_ENTITIES = "includeArchivedEntities";

    /**
     * Name of rule used to select certifier
     */
    public static final String ARG_CERTIFIER_RULE = "certifierRule";

    /**
     * Name of backup certifier, who should be assigned any items that do not fit in major buckets
     */
    public static final String ARG_BACKUP_CERTIFIER = "backupCertifier";

    /**
     * For "Owner" certifier selection type, defines who gets Role items. See CERTIFIER_OWNER_* for possible values.
     */
    public static final String ARG_CERTIFIER_OWNER_ROLE = "certifierOwnerRole";

    /**
     * For "Owner" certifier selection type, defines who gets addtional entitlement items. See CERTIFIER_OWNER_* for possible values.
     */
    public static final String ARG_CERTIFIER_OWNER_ENTITLEMENT = "certifierOwnerEntitlement";

    /**
     * For "Owner" certifier selection type, defines who gets account items. See CERTIFIER_OWNER_* for possible values.
     */
    public static final String ARG_CERTIFIER_OWNER_ACCOUNT = "certifierOwnerAccount";

    public static final String CERTIFIER_OWNER_TYPE_APPLICATION_OWNER = "ApplicationOwner";
    public static final String CERTIFIER_OWNER_TYPE_ENTITLEMENT_OWNER = "EntitlementOwner";
    public static final String CERTIFIER_OWNER_TYPE_ROLE_OWNER = "RoleOwner";


    public static final String ARG_PARTITION_COUNT = "partitionCount";
    public static final String ARG_PARTITION_SIZE = "partitionSize";
    public static final String ARG_PROFILE = "profile";

    public static final String ARG_USE_ENTITLEMENTS_TABLE = "useEntitlementsTable";
    public static final String ARG_NO_SIMPLE_ENTITLEMENT_FILTER = "noSimpleEntitlementFilter";
    
    private Attributes<String, Object> attributes;
    private List<Tag> tags;

    /**
     * Default constructor.
     */
    public CertificationDefinition() {
        super();
    }

    /**
     * Initialize this CertificationDefinition, setting default values pulled
     * from the SystemConfiguration
     */
    public void initialize(SailPointContext context) throws GeneralException {

        // set a default granularity
        setEntitlementGranularity(Certification.EntitlementGranularity.Value);

        Configuration sysConfig = context.getConfiguration();
        
        boolean automateSignoffPopup = false;
        if (sysConfig.containsAttribute(Configuration.AUTOMATE_SIGNOFF_POPUP)) {
            automateSignoffPopup = sysConfig.getBoolean(Configuration.AUTOMATE_SIGNOFF_POPUP);
        }
        setAutomateSignoffPopup(automateSignoffPopup);

        boolean subordinateEnabled = true;
        if (sysConfig.containsAttribute(Configuration.SUBORDINATE_CERTIFICATION_ENABLED)) {
            subordinateEnabled = sysConfig.getBoolean(Configuration.SUBORDINATE_CERTIFICATION_ENABLED);
        }
        setIsSubordinateCertificationEnabled(subordinateEnabled);

        boolean flatten = false;
        if (sysConfig.containsAttribute(Configuration.FLATTEN_CERTIFICATION_MANAGER_HIERARCHY)) {
            flatten = sysConfig.getBoolean(Configuration.FLATTEN_CERTIFICATION_MANAGER_HIERARCHY);
        }
        setFlattenManagerCertificationHierarchy(flatten);

        boolean completeHierarchy = false;
        if (sysConfig.containsAttribute(Configuration.COMPLETE_CERTIFICATION_HIERARCHY)) {
            completeHierarchy = sysConfig.getBoolean(Configuration.COMPLETE_CERTIFICATION_HIERARCHY);
        }
        setCompleteCertificationHierarchy(completeHierarchy);

        String entGranStr = sysConfig.getString(Configuration.ADDITIONAL_ENTITLEMENT_GRANULARITY);
        if (null != entGranStr) {
            setEntitlementGranularity(Certification.EntitlementGranularity.valueOf(entGranStr));
        } else {
            setEntitlementGranularity(Certification.EntitlementGranularity.Application);
        }

        setSaveExclusions(false);

        Duration duration = (Duration) sysConfig.get(Configuration.ACTIVE_PERIOD_DURATION);
        if (null != duration) {
            setActivePeriodDurationAmount(duration.getAmount());
            setActivePeriodDurationScale(duration.getScale());
        }

        setChallengePeriodEnabled(sysConfig.getBoolean(Configuration.CHALLENGE_PERIOD_ENABLED, false));
        duration = (Duration) sysConfig.get(Configuration.CHALLENGE_PERIOD_DURATION);
        if (null != duration) {
            setChallengePeriodDurationAmount(duration.getAmount());
            setChallengePeriodDurationScale(duration.getScale());
        }

        setAutomaticClosingEnabled(sysConfig.getBoolean(Configuration.AUTOMATIC_CLOSING_ENABLED, false));
        duration = (Duration) sysConfig.get(Configuration.AUTOMATIC_CLOSING_DURATION);
        if (null != duration) {
            setAutomaticClosingInterval(duration.getAmount());
            setAutomaticClosingIntervalScale(duration.getScale());
        }
        setAutomaticClosingRuleName(sysConfig.getString(Configuration.AUTOMATIC_CLOSING_RULE_NAME));
        setAutomaticClosingAction((CertificationAction.Status)sysConfig.get(Configuration.AUTOMATIC_CLOSING_ACTION));
        setAutomaticClosingComments(sysConfig.getString(Configuration.AUTOMATIC_CLOSING_COMMENTS));
        setAutomaticClosingSignerName(sysConfig.getString(Configuration.AUTOMATIC_CLOSING_SIGNER));

        setRemediationPeriodEnabled(sysConfig.getBoolean(Configuration.REMEDIATION_PERIOD_ENABLED, false));
        duration = (Duration) sysConfig.get(Configuration.REMEDIATION_PERIOD_DURATION);
        if (null != duration) {
            setRemediationPeriodDurationAmount(duration.getAmount());
            setRemediationPeriodDurationScale(duration.getScale());
        }

        setAllowExceptionPopup(sysConfig.getBoolean(Configuration.CERTIFICATION_MITIGATION_POPUP_ENABLED, false));
        duration = (Duration) sysConfig.get(Configuration.CERTIFICATION_MITIGATION_DURATION);
        if (null != duration) {
            setAllowExceptionDurationAmount(duration.getAmount());
            setAllowExceptionDurationScale(duration.getScale());
        }

        setShowRecommendations(sysConfig.getBoolean(Configuration.CERTIFICATION_SHOW_RECOMMENDATIONS, false));
        setAutoApprove(sysConfig.getBoolean(Configuration.CERTIFICATION_AUTO_APPROVE, false));

        setIncludeRoles(true);
        setIncludeAdditionalEntitlements(true);
        setIncludeTargetPermissions(true);

        // The default value for app owner certs is different.
        setIncludePolicyViolations(!Certification.Type.ApplicationOwner.equals(getType()));

        setCertifyAccounts(false);
        setCertifyEmptyAccounts(false);
        setIncludeCapabilities(false);
        setIncludeScopes(false);
        setIncludeRoleHierarchy(false);
        setProcessRevokesImmediately(false);
        setExcludeInactive(false);
        setFilterLogicalEntitlements(false);

        setSendPreDelegationCompletionEmails(false);
        setCertifierOwnerRole(CERTIFIER_OWNER_TYPE_ROLE_OWNER);
        setCertifierOwnerEntitlement(CERTIFIER_OWNER_TYPE_APPLICATION_OWNER);
        setCertifierOwnerAccount(CERTIFIER_OWNER_TYPE_APPLICATION_OWNER);

        //load esig setting from configuration
        String defaultSigName = context.getConfiguration().getString(Configuration.REQUIRE_ELECTRONIC_SIGNATURE);
        if (!Util.isNullOrEmpty(defaultSigName)) {
            this.setElectronicSignatureName(defaultSigName);
            this.setElectronicSignatureRequired(true);
        } else {
            this.setElectronicSignatureRequired(false);
        }

        // Set manually so we never end up with the enum value from system config
        setSelfCertificationAllowedLevel(sysConfig.getString(Configuration.ALLOW_SELF_CERTIFICATION));

        // Attempt to read attribute defaults from system config.
        Attributes<String, Object> attrs = new Attributes<String, Object>();
        for (String attr : Configuration.CERTIFICATION_DEFINITION_ATTRIBUTES) {
            Object val = sysConfig.get(attr);
            if (null != val) {
                attrs.put(attr, val);
            }
        }
        this.mergeAttributes(attrs);
    }

    /**
     * Initialize this CertificationDefinition with the default values from the SystemConfiguration.
     * Any additional attributes passed in with the options parameter are merged into the
     * definition's attributes map.
     */
    public void initialize(SailPointContext context, Attributes<String, Object> options) throws GeneralException {
        this.initialize(context);
        this.mergeAttributes(options);
    }

    /**
     * This method is used by the CertificationBuilder to copy the CertificationDefinition details
     * to a newly created certification.
     */
    public void storeContext(SailPointContext context, Certification cert) throws GeneralException {
        cert.setTriggerId(getTriggerId());
        cert.setCertificationDefinitionId(getId());

        // pjeong: for all of these attributes we need to be checking the
        // definition value. all of these attributes need to eventually be moved off of the cert object
        for (String attr : Configuration.CERTIFICATION_ATTRIBUTES) {
            if (getAttributes().containsKey(attr) && getAttributes().get(attr) != null) {
                cert.getAttributes().put(attr, getAttributes().get(attr));
            }
        }

        cert.setEntitlementGranularity(getEntitlementGranularity());
        cert.setExcludeInactive(isExcludeInactive());

        if (cert.getAttributes() == null)
            cert.setAttributes(new Attributes<String, Object>());

        if (null != getEntityCustomizationRuleName()) {
            cert.getAttributes().put(Configuration.CERTIFICATION_ENTITY_CUSTOMIZATION_RULE,
                    getEntityCustomizationRuleName());
        }

        if (null != getItemCustomizationRuleName()) {
            cert.getAttributes().put(Configuration.CERTIFICATION_ITEM_CUSTOMIZATION_RULE,
                    getItemCustomizationRuleName());
        }

        if (null != getEntityRefreshRuleName()) {
            cert.getAttributes().put(Configuration.CERTIFICATION_ENTITY_REFRESH_RULE,
                    getEntityRefreshRuleName());
        }

        if (null != getEntityCompletionRuleName()) {
            cert.getAttributes().put(Configuration.CERTIFICATION_ENTITY_COMPLETION_RULE,
                    getEntityCompletionRuleName());
        }
        
        if (null != getApproverRuleName()) {
            Rule rule = context.getObjectByName(Rule.class, getApproverRuleName());
            cert.setApproverRule(rule);
        }

        List<Tag> tags = null;
        // clone the list so hibernate doesnt choke
        //bug #8017: We have some NonUniqueObjectExceptions happenings with tags
        //           These tag objects may not belong to the correct context session if decaching occurred.
        //           To be safe, we can re-attach them here.
        if (getTags() != null && !getTags().isEmpty()) {
            tags = new ArrayList<Tag>();
            for (Tag tag : getTags()) {
                tags.add(ObjectUtil.reattach(context, tag));
            }
        }
        cert.setTags(tags);
    }

    /**
     * Creates a copy of this CertificationDefinition. This is deliberately outside of the Object.clone functionality.
     * @return The cloned definition.
     */
    public CertificationDefinition createCopy()
    {
    	CertificationDefinition result = new CertificationDefinition();
    	result.setId(getId());
    	result.setCreated(getCreated());
    	result.setOwner(getOwner());
    	result.setName(getName());
    	result.setDescription(getDescription());
    	result.setAttributes(getAttributes().mediumClone());
        result.setAssignedScope(getAssignedScope());
    	
    	if (getTags() != null) {
    	    List<Tag> newTags = new ArrayList<>();
    	    for (Tag tag : getTags()) {
    	        newTags.add(tag);
            }
            result.setTags(newTags);
    	}
    	
    	return result;
    }    

    @Override
    public void visit(Visitor v) throws GeneralException {
        v.visitCertificationDefinition(this);
    }


    @XMLProperty(mode = SerializationMode.UNQUALIFIED)
    public Attributes<String, Object> getAttributes() {
        if (null == this.attributes) {
            this.attributes = new Attributes<String, Object>();
        }
        return this.attributes;
    }

    public void setAttributes(Attributes<String, Object> attributes) {
        this.attributes = attributes;
    }

    public void mergeAttributes(Attributes<String, Object> mergeAttributes) {
        if (mergeAttributes != null) {
            for (String key : mergeAttributes.getKeys()) {
                getAttributes().put(key, mergeAttributes.get(key));
            }
        }
    }

    @XMLProperty(mode = SerializationMode.REFERENCE_LIST)
    public List<Tag> getTags() {
        return this.tags;
    }

    public void setTags(List<Tag> tags) {
        this.tags = tags;
    }

    @Override
    public void load() {
        super.load();
        for (Tag tag : Util.iterate(tags)) {
            tag.load();
        }
    }

    /***************************************************************
     *
     * Basic Certification Properties
     *
     * ***************************************************************/

    @AttributesSetter(ARG_TRIGGER_ID)
    public void setTriggerId(String triggerId) {
        setAttribute(ARG_TRIGGER_ID, triggerId);
    }

    @AttributesGetter(ARG_TRIGGER_ID)
    public String getTriggerId() {
        return getAttributes().getString(ARG_TRIGGER_ID);
    }

    @AttributesGetter(ARG_CERT_NAME_TEMPLATE)
    public String getCertificationNameTemplate() {
        String name = (String) getAttribute(ARG_CERT_NAME_TEMPLATE);

        if(name == null)
            name = (String) getAttribute(ARG_LEGACY_CERT_NAME_TEMPLATE);
        return name;
    }

    @AttributesSetter(ARG_CERT_NAME_TEMPLATE)
    public void setCertificationNameTemplate(String template) {
        setAttribute(ARG_CERT_NAME_TEMPLATE, template);
    }

    @AttributesGetter(ARG_GLOBAL)
    public boolean isGlobal() {
        return getAttributes().getBoolean(ARG_GLOBAL);
    }

    @AttributesSetter(ARG_GLOBAL)
    public void setGlobal(boolean global) {
        setAttribute(ARG_GLOBAL, Boolean.toString(global));
    }

    @AttributesGetter(ARG_CERTIFICATION_TYPE)
    public Certification.Type getType() {
        return getAttribute(ARG_CERTIFICATION_TYPE) != null ?
                Certification.Type.valueOf(getAttributes().getString(ARG_CERTIFICATION_TYPE)) : null;
    }

    public Message getTypeScheduleDescription() {
        Certification.Type type = this.getType();
        boolean isGlobal = this.isGlobal();

        if (type == null)
            return null;

        String key = null;
        switch (type) {
            case Manager:
                key = isGlobal ? MessageKeys.CERT_SCHEDULE_TYPE_MGR_GLOBAL :
                        MessageKeys.CERT_SCHEDULE_TYPE_MGR;
                break;
            case ApplicationOwner:
                key = isGlobal ? MessageKeys.CERT_SCHEDULE_TYPE_APP_OWNER_GLOBAL :
                        MessageKeys.CERT_SCHEDULE_TYPE_APP_OWNER;
                break;
            case Group:
                key =  MessageKeys.CERT_SCHEDULE_TYPE_ADV;
                break;
            case Identity:
                key =  MessageKeys.CERT_SCHEDULE_TYPE_IDENTITY;
                break;
            case AccountGroupMembership:
                key = isGlobal ? MessageKeys.CERT_SCHEDULE_TYPE_ACCOUNT_GRP_MEMB_GLOBAL :
                        MessageKeys.CERT_SCHEDULE_TYPE_ACCOUNT_GRP_MEMB;
                break;
            case AccountGroupPermissions:
                key = isGlobal ? MessageKeys.CERT_SCHEDULE_TYPE_ACCOUNT_GRP_PERMS_GLOBAL :
                        MessageKeys.CERT_SCHEDULE_TYPE_ACCOUNT_GRP_PERMS;
                break;
            case DataOwner:
                key = isGlobal ? MessageKeys.CERT_SCHEDULE_TYPE_DATA_OWNER_GLOBAL :
                        MessageKeys.CERT_SCHEDULE_TYPE_DATA_OWNER;
                break;
            case BusinessRoleComposition:
                key = isGlobal ? MessageKeys.CERT_SCHEDULE_TYPE_ROLE_COMP_GLOBAL :
                        MessageKeys.CERT_SCHEDULE_TYPE_ROLE_COMP;
                break;
            case BusinessRoleMembership:
                key = isGlobal ? MessageKeys.CERT_SCHEDULE_TYPE_ROLE_MEMB_GLOBAL :
                        MessageKeys.CERT_SCHEDULE_TYPE_ROLE_MEMB;
                break;
            default:
                throw new RuntimeException("Unknown certification type");
        }

        return new Message(key);
    }

    public void setType(Certification.Type type) {
        setType(type != null ? type.name() : null);
    }

    @AttributesSetter(ARG_CERTIFICATION_TYPE)
    public void setType(String type) {
        setAttribute(ARG_CERTIFICATION_TYPE, type);
    }

    @AttributesGetter(ARG_CERT_OWNER)
    public String getCertificationOwner() {
        return (String) getAttribute(ARG_CERT_OWNER);
    }

    @AttributesSetter(ARG_CERT_OWNER)
    public void setCertificationOwner(String ownerIdOrName) {
        setAttribute(ARG_CERT_OWNER, ownerIdOrName);
    }

    public void setCertificationOwner(Identity owner) {
        setCertificationOwner(owner != null ? owner.getName() : null);
    }

    public Identity getCertificationOwner(Resolver resolver) throws GeneralException {
        return getCertificationOwner() != null ?
                resolver.getObjectByName(Identity.class, getCertificationOwner()) : null;
    }

    @AttributesGetter(ARG_NAME_TEMPLATE)
    public String getNameTemplate() {
        return getAttributes().getString(ARG_NAME_TEMPLATE);
    }

    @AttributesSetter(ARG_NAME_TEMPLATE)
    public void setNameTemplate(String nameTemplate) {
        setAttribute(ARG_NAME_TEMPLATE, nameTemplate);
    }

    @AttributesGetter(ARG_SHORT_NAME_TEMPLATE)
    public String getShortNameTemplate() {
        return getAttributes().getString(ARG_SHORT_NAME_TEMPLATE);
    }

    @AttributesSetter(ARG_SHORT_NAME_TEMPLATE)
    public void setShortNameTemplate(String shortNameTemplate) {
        setAttribute(ARG_SHORT_NAME_TEMPLATE, shortNameTemplate);
    }

    /**
     * Application Certification setting that indicates the IDs/names of the owners for the
     * application(s) being certified
     */
    @AttributesGetter(ARG_OWNERS)
    public List<String> getOwnerIds() {
        return readList(ARG_OWNERS);
    }

    @AttributesSetter(ARG_OWNERS)
    public void setOwnerIds(List<String> ownerIds) {
        setList(ARG_OWNERS, ownerIds);
    }

    public List<Identity> getOwners(SailPointContext context) throws GeneralException{
        List<String> ownerIds = getOwnerIds();
        if (Util.isEmpty(ownerIds)) {
            return null;
        }
        QueryOptions ops = new QueryOptions(Filter.or(Filter.in("id", ownerIds), Filter.in("name", ownerIds)));
        ops.add(Filter.in(Identity.ATT_WORKGROUP_FLAG, Arrays.asList(new Boolean(true), new Boolean(false))));
        return context.getObjects(Identity.class, ops);
    }

    /**
     * List of Names of the identities who will
     * certify the items in the certification.
     */
    @AttributesGetter(ARG_CERTIFIERS)
    public List<String> getCertifiers() {
        return readList(ARG_CERTIFIERS);
    }

    @AttributesSetter(ARG_CERTIFIERS)
    public void setCertifiers(List<String> names) {
        setList(ARG_CERTIFIERS, names);
    }

    public void setCertifierIdentities(List<Identity> identities) {

        List<String> names = null;
        if (identities != null && !identities.isEmpty()) {
            names = new ArrayList<String>();
            for (Identity identity : identities) {
                names.add(identity.getName());
            }
        }

        setCertifiers(names);
    }

    /**
     * Common setting that specifies the granularity at which additional entitlements should be certified
     */
    @AttributesGetter(ARG_ENTITLEMENT_GRANULARITY)
    public Certification.EntitlementGranularity getEntitlementGranularity() {
        String val = getAttributes().getString(ARG_ENTITLEMENT_GRANULARITY);
        return val != null ? Certification.EntitlementGranularity.valueOf(val) : null;
    }

    public void setEntitlementGranularity(Certification.EntitlementGranularity entitlementGranularity) {
        setEntitlementGranularity(entitlementGranularity != null ? entitlementGranularity.name() : null);
    }

    @AttributesSetter(ARG_ENTITLEMENT_GRANULARITY)
    public void setEntitlementGranularity(String entitlementGranularity) {
        setAttribute(ARG_ENTITLEMENT_GRANULARITY, entitlementGranularity);
    }

    /**
     * Common setting that indicates the applications to include in the certification
     *
     * @return ids/names of all included applications
     */
    @AttributesGetter(ARG_INCLUDED_APPLICATION_IDS)
    public List<String> getIncludedApplicationIds() {
        return readList(ARG_INCLUDED_APPLICATION_IDS);
    }

    @AttributesSetter(ARG_INCLUDED_APPLICATION_IDS)
    public void setIncludedApplicationIds(List<String> includedApplicationIds) {
        setList(ARG_INCLUDED_APPLICATION_IDS, includedApplicationIds);
    }

    @AttributesGetter(ARG_PROCESS_REVOKES_IMMEDIATELY)
    public boolean isProcessRevokesImmediately() {
        return getBoolean(ARG_PROCESS_REVOKES_IMMEDIATELY, false);
    }

    @AttributesSetter(ARG_PROCESS_REVOKES_IMMEDIATELY)
    public void setProcessRevokesImmediately(Boolean processRevokes) {
        setBoolean(ARG_PROCESS_REVOKES_IMMEDIATELY, processRevokes);
    }

    @AttributesGetter(ARG_CERTIFIER_TYPE)
    public CertifierSelectionType getCertifierSelectionType() {
        String certifierSelectionType = getAttributes().getString(ARG_CERTIFIER_TYPE);
        return certifierSelectionType != null ? CertifierSelectionType.valueOf(certifierSelectionType) : null;
    }

    @AttributesSetter(ARG_CERTIFIER_TYPE)
    public void setCertifierSelectionType(CertifierSelectionType certifierSelectionType) {
        setCertifierSelectionType(certifierSelectionType != null ? certifierSelectionType.name : null);
    }

    public void setCertifierSelectionType(String certifierSelectionType) {
        setAttribute(ARG_CERTIFIER_TYPE, certifierSelectionType);
    }

    @AttributesGetter(ARG_STAGING_ENABLED)
    public boolean isStagingEnabled() {
        return getBoolean(ARG_STAGING_ENABLED, false);
    }

    @AttributesSetter(ARG_STAGING_ENABLED)
    public void setStagingEnabled(boolean stagingEnabled) {
        setBoolean(ARG_STAGING_ENABLED, stagingEnabled);
    }

    public Boolean isAutomateSignoffPopup() {
        return getBoolean(ARG_AUTOMATE_SIGNOFF_POPUP);
    }

    @AttributesGetter(ARG_AUTOMATE_SIGNOFF_POPUP)
    public boolean isAutomateSignoffPopup(SailPointContext context) throws GeneralException {
        if (isAutomateSignoffPopup() != null) {
            return isAutomateSignoffPopup();
        }

        return context.getConfiguration().getBoolean(Configuration.AUTOMATE_SIGNOFF_POPUP, false);
    }

    @AttributesSetter(ARG_AUTOMATE_SIGNOFF_POPUP)
    public void setAutomateSignoffPopup(boolean automateSignoffPopup) {
        setBoolean(ARG_AUTOMATE_SIGNOFF_POPUP, automateSignoffPopup) ;
    }

    /**
     * @return name of identity who will own any self certification violations, either inside
     * a self certification reassignment or through forwarded delegations
     * @throws GeneralException
     */
    public String getSelfCertificationViolationOwner() throws GeneralException {
        return getString(Configuration.SELF_CERTIFICATION_VIOLATION_OWNER);
    }

    @AttributesGetter(Configuration.SELF_CERTIFICATION_VIOLATION_OWNER)
    public Identity getSelfCertificationViolationOwner(Resolver resolver) throws GeneralException {
        String owner = getSelfCertificationViolationOwner();
        if (owner == null) {
            return null;
        }

        return resolver.getObjectByName(Identity.class, owner);
    }

    @AttributesSetter(Configuration.SELF_CERTIFICATION_VIOLATION_OWNER)
    public void setSelfCertificationViolationsOwner(String selfCertificationViolationsOwner) {
        setString(Configuration.SELF_CERTIFICATION_VIOLATION_OWNER, selfCertificationViolationsOwner);
    }

    public void setSelfCertificationViolationsOwner(Identity selfCertificationViolationsOwner) {
        setSelfCertificationViolationsOwner(selfCertificationViolationsOwner != null ? selfCertificationViolationsOwner.getName() : null);
    }

    public Certification.SelfCertificationAllowedLevel getSelfCertificationAllowedLevel() {
        String allowedLevel = getString(Configuration.ALLOW_SELF_CERTIFICATION);
        return (allowedLevel == null) ? null : Certification.SelfCertificationAllowedLevel.valueOf(allowedLevel);
    }

    @AttributesGetter(Configuration.ALLOW_SELF_CERTIFICATION)
    public Certification.SelfCertificationAllowedLevel getSelfCertificationAllowedLevel(SailPointContext context) throws GeneralException {
        Certification.SelfCertificationAllowedLevel allowedLevel = getSelfCertificationAllowedLevel();
        if (allowedLevel != null) {
            return allowedLevel;
        }

        String sysAllowedLevel = context.getConfiguration().getString(Configuration.ALLOW_SELF_CERTIFICATION);
        return (sysAllowedLevel == null) ? null : Certification.SelfCertificationAllowedLevel.valueOf(sysAllowedLevel);
    }

    @AttributesSetter(Configuration.ALLOW_SELF_CERTIFICATION)
    public void setSelfCertificationAllowedLevel(Certification.SelfCertificationAllowedLevel allowedLevel) {
        setString(Configuration.ALLOW_SELF_CERTIFICATION, allowedLevel);
    }

    public void setSelfCertificationAllowedLevel(String allowedLevel) {
        setString(Configuration.ALLOW_SELF_CERTIFICATION, allowedLevel);
    }

    /***************************************************************
     *
     * Generic Identity Certification Properties
     *
     * ***************************************************************/

    private Boolean isAllowEntityDelegation() {
        return getBoolean(Configuration.CERTIFICATION_ENTITY_DELEGATION_ENABLED);
    }

    @AttributesSetter(Configuration.CERTIFICATION_ENTITY_DELEGATION_ENABLED)
    public void setAllowEntityDelegation(Boolean allow) {
        setBoolean(Configuration.CERTIFICATION_ENTITY_DELEGATION_ENABLED, allow);
    }

    @AttributesGetter(Configuration.CERTIFICATION_ENTITY_DELEGATION_ENABLED)
    public boolean isAllowEntityDelegation(SailPointContext context) throws GeneralException{
        if (isAllowEntityDelegation() != null)
            return isAllowEntityDelegation().booleanValue();

        return context.getConfiguration().getBoolean(Configuration.CERTIFICATION_ENTITY_DELEGATION_ENABLED, false);
    }

    @AttributesGetter(Configuration.CERTIFICATION_DISABLE_DELEGATION_FORWARDING)
    public Boolean isDelegationForwardingDisabled() {
        return getBoolean(Configuration.CERTIFICATION_DISABLE_DELEGATION_FORWARDING, false);
    }

    @AttributesSetter(Configuration.CERTIFICATION_DISABLE_DELEGATION_FORWARDING)
    public void setDelegationForwardingDisabled(Boolean allow) {
        setBoolean(Configuration.CERTIFICATION_DISABLE_DELEGATION_FORWARDING, allow);
    }

    public Boolean isLimitReassignments() {
        return getBoolean(Configuration.CERTIFICATION_LIMIT_REASSIGNMENTS, false);
    }

    @AttributesSetter(Configuration.CERTIFICATION_LIMIT_REASSIGNMENTS)
    public void setLimitReassignments(Boolean allow) {
        setBoolean(Configuration.CERTIFICATION_LIMIT_REASSIGNMENTS, allow);
    }

    public Integer getReassignmentLimit() {
        return getInteger(Configuration.CERTIFICATION_REASSIGNMENT_LIMIT);
    }

    @AttributesSetter(Configuration.CERTIFICATION_REASSIGNMENT_LIMIT)
    public void setReassignmentLimit(Integer value) {
        setString(Configuration.CERTIFICATION_REASSIGNMENT_LIMIT, value);
    }

    @AttributesGetter(Configuration.CERTIFICATION_LIMIT_REASSIGNMENTS)
    public boolean isLimitReassignments(SailPointContext context) throws GeneralException {
        Boolean limitReassignments = getBoolean(Configuration.CERTIFICATION_LIMIT_REASSIGNMENTS);
        return (limitReassignments != null) ? limitReassignments.booleanValue() : context.getConfiguration().getBoolean(Configuration.CERTIFICATION_LIMIT_REASSIGNMENTS);
    }

    @AttributesGetter(Configuration.CERTIFICATION_REASSIGNMENT_LIMIT)
    public int getReassignmentLimit(SailPointContext context) throws GeneralException {
        Integer reassignmentLimit = getReassignmentLimit();
        return (reassignmentLimit != null) ? reassignmentLimit.intValue() : Util.atoi(context.getConfiguration().getString(Configuration.CERTIFICATION_REASSIGNMENT_LIMIT));
    }

    private Boolean isAllowItemDelegation() {
        return getBoolean(Configuration.CERTIFICATION_ITEM_DELEGATION_ENABLED);
    }

    @AttributesSetter(Configuration.CERTIFICATION_ITEM_DELEGATION_ENABLED)
    public void setAllowItemDelegation(Boolean allow) {
        setBoolean(Configuration.CERTIFICATION_ITEM_DELEGATION_ENABLED, allow);
    }

    @AttributesGetter(Configuration.CERTIFICATION_ITEM_DELEGATION_ENABLED)
    public boolean isAllowItemDelegation(SailPointContext context) throws GeneralException{
        if (isAllowItemDelegation() != null)
            return isAllowItemDelegation().booleanValue();

        return context.getConfiguration().getBoolean(Configuration.CERTIFICATION_ITEM_DELEGATION_ENABLED, false);
    }

    private Boolean isRequireBulkCertifyConfirmation() {
        return getBoolean(Configuration.REQUIRE_BULK_CERTIFY_CONFIRMATION);
    }

    @AttributesSetter(Configuration.REQUIRE_BULK_CERTIFY_CONFIRMATION)
    public void setRequireBulkCertifyConfirmation(Boolean require) {
        setBoolean(Configuration.REQUIRE_BULK_CERTIFY_CONFIRMATION, require);
    }

    @AttributesGetter(Configuration.REQUIRE_BULK_CERTIFY_CONFIRMATION)
    public boolean isRequireBulkCertifyConfirmation(SailPointContext context) throws GeneralException{
        if (isRequireBulkCertifyConfirmation() != null)
            return isRequireBulkCertifyConfirmation().booleanValue();

        return context.getConfiguration().getBoolean(Configuration.REQUIRE_BULK_CERTIFY_CONFIRMATION, false);
    }

    private Boolean isCertificationDetailedViewFilterSet() {
        return getBoolean(Configuration.CERTIFICATION_DETAILED_VIEW_FILTER_SET);
    }

    @AttributesSetter(Configuration.CERTIFICATION_DETAILED_VIEW_FILTER_SET)
    public void setCertificationDetailedViewFilterSet(Boolean doit) {
        setBoolean(Configuration.CERTIFICATION_DETAILED_VIEW_FILTER_SET, doit);
    }

    @AttributesGetter(Configuration.CERTIFICATION_DETAILED_VIEW_FILTER_SET)
    public boolean isCertificationDetailedViewFilterSet(SailPointContext context) throws GeneralException{
        if (isCertificationDetailedViewFilterSet() != null)
            return isCertificationDetailedViewFilterSet().booleanValue();

        return context.getConfiguration().getBoolean(Configuration.CERTIFICATION_DETAILED_VIEW_FILTER_SET, false);
    }

    private Boolean isAllowListBulkAccountRevocation() {
        return getBoolean(Configuration.ALLOW_LIST_VIEW_BULK_ACCOUNT_REVOKE);
    }

    @AttributesGetter(Configuration.ALLOW_LIST_VIEW_BULK_ACCOUNT_REVOKE)
    public Boolean isAllowListBulkAccountRevocation(SailPointContext context) throws GeneralException {
        if (isAllowListBulkAccountRevocation() != null) {
            return isAllowListBulkAccountRevocation().booleanValue();
        }
        return context.getConfiguration().getBoolean(Configuration.ALLOW_LIST_VIEW_BULK_ACCOUNT_REVOKE, false);
    }

    @AttributesSetter(Configuration.ALLOW_LIST_VIEW_BULK_ACCOUNT_REVOKE)
    public void setAllowListBulkAccountRevocation(Boolean allow) {
        setBoolean(Configuration.ALLOW_LIST_VIEW_BULK_ACCOUNT_REVOKE, allow);
    }

    private Boolean isAllowListBulkRevocation() {
        return getBoolean(Configuration.ALLOW_LIST_VIEW_BULK_REVOKE);
    }

    @AttributesGetter(Configuration.ALLOW_LIST_VIEW_BULK_REVOKE)
    public Boolean isAllowListBulkRevocation(SailPointContext context) throws GeneralException {
        if (isAllowListBulkRevocation() != null) {
            return isAllowListBulkRevocation().booleanValue();
        }
        return context.getConfiguration().getBoolean(Configuration.ALLOW_LIST_VIEW_BULK_REVOKE, false);
    }

    @AttributesSetter(Configuration.ALLOW_LIST_VIEW_BULK_REVOKE)
    public void setAllowListBulkRevocation(Boolean allow) {
        setBoolean(Configuration.ALLOW_LIST_VIEW_BULK_REVOKE, allow);
    }

    private Boolean isAllowEntityBulkRevocation() {
        return getBoolean(Configuration.ALLOW_CERT_ENTITY_BULK_REVOCATION);
    }

    @AttributesGetter(Configuration.ALLOW_CERT_ENTITY_BULK_REVOCATION)
    public boolean isAllowEntityBulkRevocation(SailPointContext context) throws GeneralException {
        if (isAllowEntityBulkRevocation() != null) {
            return isAllowEntityBulkRevocation().booleanValue();
        }

        return context.getConfiguration().getBoolean(Configuration.ALLOW_CERT_ENTITY_BULK_REVOCATION, false);
    }

    @AttributesSetter(Configuration.ALLOW_CERT_ENTITY_BULK_REVOCATION)
    public void setAllowEntityBulkRevocation(Boolean allow) {
        setBoolean(Configuration.ALLOW_CERT_ENTITY_BULK_REVOCATION, allow);
    }

    private Boolean isAllowEntityBulkAccountRevocation() {
        return getBoolean(Configuration.ALLOW_CERT_ENTITY_BULK_ACCOUNT_REVOCATION);
    }

    @AttributesGetter(Configuration.ALLOW_CERT_ENTITY_BULK_ACCOUNT_REVOCATION)
    public boolean isAllowEntityBulkAccountRevocation(SailPointContext context) throws GeneralException {
        if (isAllowEntityBulkAccountRevocation() != null) {
            return isAllowEntityBulkAccountRevocation().booleanValue();
        }

        return context.getConfiguration().getBoolean(Configuration.ALLOW_CERT_ENTITY_BULK_ACCOUNT_REVOCATION, false);
    }

    @AttributesSetter(Configuration.ALLOW_CERT_ENTITY_BULK_ACCOUNT_REVOCATION)
    public void setAllowEntityBulkAccountRevocation(Boolean allow) {
        setBoolean(Configuration.ALLOW_CERT_ENTITY_BULK_ACCOUNT_REVOCATION, allow);
    }
    
    public Boolean isAllowEntityBulkClearDecisions() throws GeneralException {
        return getBoolean(Configuration.ALLOW_CERT_ENTITY_BULK_CLEAR_DECISIONS);
    }

    @AttributesGetter(Configuration.ALLOW_CERT_ENTITY_BULK_CLEAR_DECISIONS)
    public boolean isAllowEntityBulkClearDecisions(SailPointContext context) throws GeneralException {
        if (isAllowEntityBulkClearDecisions() != null) {
            return isAllowEntityBulkClearDecisions().booleanValue();
        }

        return context.getConfiguration().getBoolean(Configuration.ALLOW_CERT_ENTITY_BULK_CLEAR_DECISIONS, false);
    }

    @AttributesSetter(Configuration.ALLOW_CERT_ENTITY_BULK_CLEAR_DECISIONS)
    public void setAllowEntityBulkClearDecisions(Boolean allow) {
        setBoolean(Configuration.ALLOW_CERT_ENTITY_BULK_CLEAR_DECISIONS, allow);
    }
    
    private Boolean isAllowApproveAccounts() {
        return getBoolean(Configuration.ENABLE_ACCOUNT_APPROVE_ACTION);
    }

    @AttributesSetter(Configuration.ENABLE_ACCOUNT_APPROVE_ACTION)
    public void setAllowApproveAccounts(Boolean allow) {
        setBoolean(Configuration.ENABLE_ACCOUNT_APPROVE_ACTION, allow);
    }

    @AttributesGetter(Configuration.ENABLE_ACCOUNT_APPROVE_ACTION)
    public boolean isAllowApproveAccounts(SailPointContext context) throws GeneralException {
        if (isAllowApproveAccounts() != null)
            return isAllowApproveAccounts().booleanValue();

        return context.getConfiguration().getBoolean(Configuration.ENABLE_ACCOUNT_APPROVE_ACTION, false);
    }

    private Boolean isAllowAccountRevocation() {
        return getBoolean(Configuration.ENABLE_ACCOUNT_REVOKE_ACTION);
    }

    @AttributesGetter(Configuration.ENABLE_ACCOUNT_REVOKE_ACTION)
    public boolean isAllowAccountRevocation(SailPointContext context) throws GeneralException {
        if (isAllowAccountRevocation() != null)
            return isAllowAccountRevocation().booleanValue();
        return context.getConfiguration().getBoolean(Configuration.ENABLE_ACCOUNT_REVOKE_ACTION, false);
    }

    @AttributesSetter(Configuration.ENABLE_ACCOUNT_REVOKE_ACTION)
    public void setAllowAccountRevocation(Boolean allow) {
        setBoolean(Configuration.ENABLE_ACCOUNT_REVOKE_ACTION, allow);
    }

    private Boolean isEnableReassignAccount() {
        return getBoolean(Configuration.ENABLE_ACCOUNT_REASSIGN_ACTION);
    }

    @AttributesGetter(Configuration.ENABLE_ACCOUNT_REASSIGN_ACTION)
    public boolean isEnableReassignAccount(SailPointContext context) throws GeneralException {
        if (isEnableReassignAccount() != null)
            return isEnableReassignAccount().booleanValue();
        return context.getConfiguration().getBoolean(Configuration.ENABLE_ACCOUNT_REASSIGN_ACTION, false);
    }

    @AttributesSetter(Configuration.ENABLE_ACCOUNT_REASSIGN_ACTION)
    public void setEnableReassignAccount(Boolean enable) {
        setBoolean(Configuration.ENABLE_ACCOUNT_REASSIGN_ACTION, enable);
    }

    @AttributesGetter(ARG_DEFAULT_ASSIGNEE_NAME)
    private String getDefaultAssignee() {
        return (String) getAttribute(ARG_DEFAULT_ASSIGNEE_NAME);
    }

    public Identity getDefaultAssignee(SailPointContext context) throws GeneralException {
        if (getDefaultAssignee() != null)
            return (ALLOW_USER_SELECTION.equals(getDefaultAssignee())) ? null : context.getObjectByName(Identity.class, getDefaultAssignee());
        
        String assigneeName = Util.getString(context.getConfiguration().getString(Configuration.DEFAULT_ASSIGNEE));
        return (assigneeName != null) ? context.getObjectByName(Identity.class, assigneeName) : null;
    }

    @AttributesSetter(ARG_DEFAULT_ASSIGNEE_NAME)
    public void setDefaultAssignee(String assigneeIdOrName) {
        setAttribute(ARG_DEFAULT_ASSIGNEE_NAME, assigneeIdOrName);
    }

    public void setDefaultAssignee(Identity assignee) {
        setDefaultAssignee(assignee != null ? assignee.getName() : ALLOW_USER_SELECTION);
    }
    
    private Boolean isAllowExceptions() {
        return getBoolean(Configuration.CERTIFICATION_MITIGATION_ENABLED);
    }

    @AttributesSetter(Configuration.CERTIFICATION_MITIGATION_ENABLED)
    public void setAllowExceptions(Boolean allowExceptions) {
        setBoolean(Configuration.CERTIFICATION_MITIGATION_ENABLED, allowExceptions);
    }

    @AttributesGetter(Configuration.CERTIFICATION_MITIGATION_ENABLED)
    public Boolean isAllowExceptions(SailPointContext context) throws GeneralException{

        if (isAllowExceptions() != null)
            return isAllowExceptions().booleanValue();

        return context.getConfiguration().getBoolean(Configuration.CERTIFICATION_MITIGATION_ENABLED, false);
    }

    public Boolean isMitigationDeprovisionEnabled() {
        return getBoolean(Configuration.CERTIFICATION_MITIGATION_DEPROVISION_ENABLED);
    }

    @AttributesSetter(Configuration.CERTIFICATION_MITIGATION_DEPROVISION_ENABLED)
    public void setMitigationDeprovisionEnabled(Boolean mitigationDeprovisionEnabled) {
        setBoolean(Configuration.CERTIFICATION_MITIGATION_DEPROVISION_ENABLED, mitigationDeprovisionEnabled);
    }

    @AttributesGetter(Configuration.CERTIFICATION_MITIGATION_DEPROVISION_ENABLED)
    public Boolean isMitigationDeprovisionEnabled(SailPointContext context) throws GeneralException{

        if (isMitigationDeprovisionEnabled() != null)
            return isMitigationDeprovisionEnabled().booleanValue();

        return context.getConfiguration().getBoolean(Configuration.CERTIFICATION_MITIGATION_DEPROVISION_ENABLED, false);
    }

    public Boolean isAllowExceptionPopup() {
        if ((isAllowExceptions() != null && isAllowExceptions()) || isIncludePolicyViolations()) {
            return getBoolean(Configuration.CERTIFICATION_MITIGATION_POPUP_ENABLED, false);
        }
        return null;
    }

    @AttributesGetter(Configuration.CERTIFICATION_MITIGATION_POPUP_ENABLED)
    public Boolean isAllowExceptionPopup(SailPointContext context) throws GeneralException {
        if (isAllowExceptionPopup() != null) {
            return isAllowExceptionPopup();
        }
        // default this to true so that old certs will show the popup
        return context.getConfiguration().getBoolean(Configuration.CERTIFICATION_MITIGATION_POPUP_ENABLED, true);
    }

    @AttributesSetter(Configuration.CERTIFICATION_MITIGATION_POPUP_ENABLED)
    public void setAllowExceptionPopup(Boolean allowDuration) {
        setBoolean(Configuration.CERTIFICATION_MITIGATION_POPUP_ENABLED, allowDuration);
    }

    private Long getAllowExceptionDurationAmount() {
        return getAttributes().get(Configuration.CERTIFICATION_MITIGATION_DURATION_AMOUNT) != null ?
                getAttributes().getLong(Configuration.CERTIFICATION_MITIGATION_DURATION_AMOUNT) : null;
    }

    @AttributesGetter(Configuration.CERTIFICATION_MITIGATION_DURATION_AMOUNT)
    public Long getAllowExceptionDurationAmount(SailPointContext context) throws GeneralException {
        if (getAllowExceptionDurationAmount() != null) {
            return getAllowExceptionDurationAmount();
        }
        Duration exceptionDuration = (Duration)context.getConfiguration().get(Configuration.CERTIFICATION_MITIGATION_DURATION);
        if (exceptionDuration != null) {
            return exceptionDuration.getAmount();
        }
        Long amount = context.getConfiguration().getLong(Configuration.CERTIFICATION_MITIGATION_DURATION_AMOUNT);
        return amount;
    }

    @AttributesSetter(Configuration.CERTIFICATION_MITIGATION_DURATION_AMOUNT)
    public void setAllowExceptionDurationAmount(Long allowDuration) {
        setString(Configuration.CERTIFICATION_MITIGATION_DURATION_AMOUNT, allowDuration);
    }

    private Duration.Scale getAllowExceptionDurationScale() {
        return getScale(Configuration.CERTIFICATION_MITIGATION_DURATION_SCALE);
    }
    
    public Duration getAllowExceptionDuration(SailPointContext context) throws GeneralException {
        Duration duration = null;
        Long amount = getAllowExceptionDurationAmount(context);
        Duration.Scale scale = getAllowExceptionDurationScale(context);
        if (amount != null && scale != null) {
           duration = new Duration(amount, scale); 
        }
        
        return duration;
    }

    @AttributesGetter(Configuration.CERTIFICATION_MITIGATION_DURATION_SCALE)
    public Duration.Scale getAllowExceptionDurationScale(SailPointContext context) throws GeneralException {
        if (getAllowExceptionDurationScale() != null) {
            return getAllowExceptionDurationScale();
        }
        Duration exceptionDuration = (Duration)context.getConfiguration().get(Configuration.CERTIFICATION_MITIGATION_DURATION);
        if (exceptionDuration != null) {
            return exceptionDuration.getScale();
        }
        String scale = context.getConfiguration().getString(Configuration.CERTIFICATION_MITIGATION_DURATION_SCALE);
        return scale != null ? Duration.Scale.valueOf(scale) : null;
    }

    @AttributesSetter(Configuration.CERTIFICATION_MITIGATION_DURATION_SCALE)
    public void setAllowExceptionDurationScale(Duration.Scale scale) {
        setString(Configuration.CERTIFICATION_MITIGATION_DURATION_SCALE, scale);
    }

    @AttributesGetter(ARG_INCLUDE_ADDITIONAL_ENTITLEMENTS)
    public boolean isIncludeAdditionalEntitlements() {
        return getBoolean(ARG_INCLUDE_ADDITIONAL_ENTITLEMENTS, false);
    }

    @AttributesSetter(ARG_INCLUDE_ADDITIONAL_ENTITLEMENTS)
    public void setIncludeAdditionalEntitlements(Boolean includeEntitlements) {
        setBoolean(ARG_INCLUDE_ADDITIONAL_ENTITLEMENTS, includeEntitlements);
    }

    @AttributesGetter(ARG_INCLUDE_ROLES)
    public boolean isIncludeRoles() {
        return getBoolean(ARG_INCLUDE_ROLES, false);
    }

    @AttributesSetter(ARG_INCLUDE_ROLES)
    public void setIncludeRoles(Boolean includeRoles) {
        setBoolean(ARG_INCLUDE_ROLES, includeRoles);
    }

    @AttributesGetter(ARG_INCLUDE_POLICY_VIOLATIONS)
    public boolean isIncludePolicyViolations() {
        return getBoolean(ARG_INCLUDE_POLICY_VIOLATIONS, false);
    }

    @AttributesSetter(ARG_INCLUDE_POLICY_VIOLATIONS)
    public void setIncludePolicyViolations(Boolean includeViolations) {
        setBoolean(ARG_INCLUDE_POLICY_VIOLATIONS, includeViolations);
    }

    @AttributesGetter(ARG_INCLUDE_CAPABILITIES)
    public boolean isIncludeCapabilities() {
        return getBoolean(ARG_INCLUDE_CAPABILITIES, false);
    }

    @AttributesSetter(ARG_INCLUDE_CAPABILITIES)
    public void setIncludeCapabilities(Boolean includeCaps) {
        setBoolean(ARG_INCLUDE_CAPABILITIES, includeCaps);
    }

    @AttributesGetter(ARG_INCLUDE_SCOPES)
    public boolean isIncludeScopes() {
        return getBoolean(ARG_INCLUDE_SCOPES, false);
    }

    @AttributesSetter(ARG_INCLUDE_SCOPES)
    public void setIncludeScopes(Boolean include) {
        setBoolean(ARG_INCLUDE_SCOPES, include);
    }

    @AttributesGetter(ARG_INCLUDE_TARGET_PERMISSIONS)
    public boolean isIncludeTargetPermissions() {
        // UPGRADE: Target Permissions flag was added in 8.1, definitions from before that will not have this
        // attribute set and we should just match the additional entitlements flag
        if (getAttribute(CertificationDefinition.ARG_INCLUDE_TARGET_PERMISSIONS) == null) {
            return isIncludeAdditionalEntitlements();
        }

        return getBoolean(ARG_INCLUDE_TARGET_PERMISSIONS, false);
    }

    @AttributesSetter(ARG_INCLUDE_TARGET_PERMISSIONS)
    public void setIncludeTargetPermissions(Boolean include) {
        setBoolean(ARG_INCLUDE_TARGET_PERMISSIONS, include);
    }

    @AttributesGetter(ARG_CERTIFY_ACCOUNTS)
    public boolean isCertifyAccounts() {
        return getBoolean(ARG_CERTIFY_ACCOUNTS, false);
    }

    @AttributesSetter(ARG_CERTIFY_ACCOUNTS)
    public void setCertifyAccounts(Boolean certifyAcounts) {
        setBoolean(ARG_CERTIFY_ACCOUNTS, certifyAcounts);
    }

    @AttributesGetter(ARG_CERTIFY_EMPTY_ACCOUNTS)
    public boolean isCertifyEmptyAccounts() {
        return getBoolean(ARG_CERTIFY_EMPTY_ACCOUNTS, false);
    }

    @AttributesSetter(ARG_CERTIFY_EMPTY_ACCOUNTS)
    public void setCertifyEmptyAccounts(Boolean certifyAcounts) {
        setBoolean(ARG_CERTIFY_EMPTY_ACCOUNTS, certifyAcounts);
    }

    @AttributesGetter(ARG_CERTIFICATION_ALLOW_PROV_MISSING_REQS)
    public boolean isAllowProvisioningRequirements() {

        if (getBoolean(ARG_CERTIFICATION_ALLOW_PROV_MISSING_REQS_LEGACY, false))
            return true;

        return getBoolean(ARG_CERTIFICATION_ALLOW_PROV_MISSING_REQS, false);
    }

    @AttributesSetter(ARG_CERTIFICATION_ALLOW_PROV_MISSING_REQS)
    public void setAllowProvisioningRequirements(Boolean allow) {
        setBoolean(ARG_CERTIFICATION_ALLOW_PROV_MISSING_REQS, allow);
    }

    public boolean isRequireApprovalComments() {
        return getBoolean(Configuration.REQUIRE_APPROVAL_COMMENTS, false);
    }

    @AttributesGetter(Configuration.REQUIRE_APPROVAL_COMMENTS)
    public boolean isRequireApprovalComments(SailPointContext context)  throws GeneralException {
        Boolean require =  getBoolean(Configuration.REQUIRE_APPROVAL_COMMENTS);

        if (require == null) {
            require = context.getConfiguration().getBoolean(Configuration.REQUIRE_MITIGATION_COMMENTS, false);
        }

        return require.booleanValue();
    }

    @AttributesGetter(Configuration.REQUIRE_REMEDIATION_COMMENTS)
    public boolean isRequireRemediationComments(SailPointContext context) throws GeneralException {
        Boolean require = getBoolean(Configuration.REQUIRE_REMEDIATION_COMMENTS);
        if (require == null) {
            require = context.getConfiguration().getBoolean(Configuration.REQUIRE_REMEDIATION_COMMENTS, false);
        }
        
        return require.booleanValue();
    }

    @AttributesSetter(Configuration.REQUIRE_REMEDIATION_COMMENTS)
    public void setRequireRemediationComments(Boolean requireRemediationComments) {
        setBoolean(Configuration.REQUIRE_REMEDIATION_COMMENTS, requireRemediationComments);
    }

    @AttributesGetter(Configuration.REQUIRE_ACCOUNT_REVOKE_COMMENTS)
    @Deprecated
    public boolean isRequireAccountRevokeComments(SailPointContext context) throws GeneralException {
        return isRequireRemediationComments(context);
    }

    @AttributesSetter(ARG_CERTIFICATION_REQUIRE_APPROVAL_COMMENTS)
    public void setRequireApprovalComments(Boolean requireApprovalComments) {
        setBoolean(ARG_CERTIFICATION_REQUIRE_APPROVAL_COMMENTS, requireApprovalComments);
    }

    @AttributesGetter(Configuration.REQUIRE_MITIGATION_COMMENTS)
    public boolean isRequireMitigationComments(SailPointContext context) throws GeneralException {
        Boolean require =  getBoolean(Configuration.REQUIRE_MITIGATION_COMMENTS);

        if (require == null) {
            require = context.getConfiguration().getBoolean(Configuration.REQUIRE_MITIGATION_COMMENTS, false);
        }

        return require.booleanValue();
    }

    @AttributesSetter(Configuration.REQUIRE_MITIGATION_COMMENTS)
    public void setRequireMitigationComments(Boolean requireMitigationComments) {
        setBoolean(Configuration.REQUIRE_MITIGATION_COMMENTS, requireMitigationComments);
    }

    /**
     * Helper method to get list of statuses requiring comments
     * @param context SailPointContext
     * @return List of CertificationAction.Status values that require comments. 
     * @throws GeneralException
     */
    public List<CertificationAction.Status> getStatusesRequiringComments(SailPointContext context)
            throws GeneralException {
        List<CertificationAction.Status> statusesRequiringComments = new ArrayList<CertificationAction.Status>();
        if (isRequireApprovalComments(context)) {
            statusesRequiringComments.add(CertificationAction.Status.Approved);
        }

        if (isRequireMitigationComments(context)) {
            statusesRequiringComments.add(CertificationAction.Status.Mitigated);
        }
        
        if (isRequireRemediationComments(context)) {
            statusesRequiringComments.add(CertificationAction.Status.Remediated);
            statusesRequiringComments.add(CertificationAction.Status.RevokeAccount);
        }

        return statusesRequiringComments;
    }

    @AttributesGetter(ARG_EXCLUDE_BASE_APP_ACCOUNTS)
    public boolean isExcludeBaseAppAccounts() {
        return getBoolean(ARG_EXCLUDE_BASE_APP_ACCOUNTS, false);
    }

    @AttributesSetter(ARG_EXCLUDE_BASE_APP_ACCOUNTS)
    public void setExcludeBaseAppAccounts(Boolean excludeBaseAppAccounts) {
        setBoolean(ARG_EXCLUDE_BASE_APP_ACCOUNTS, excludeBaseAppAccounts);
    }

    @AttributesGetter(ARG_SAVE_EXCLUSIONS)
    public boolean getSaveExclusions() {
        return getBoolean(ARG_SAVE_EXCLUSIONS, false);
    }

    @AttributesSetter(ARG_SAVE_EXCLUSIONS)
    public void setSaveExclusions(Boolean saveExclusions) {
        setBoolean(ARG_SAVE_EXCLUSIONS, Boolean.valueOf(saveExclusions));
    }

    @AttributesGetter(ARG_EXCLUDE_INACTIVE)
    public boolean isExcludeInactive() {
        return getBoolean(ARG_EXCLUDE_INACTIVE, false);
    }

    @AttributesSetter(ARG_EXCLUDE_INACTIVE)
    public void setExcludeInactive(Boolean exclude) {
        setBoolean(ARG_EXCLUDE_INACTIVE, Boolean.valueOf(exclude));
    }

    @AttributesGetter(ARG_ENABLE_PARTITIONING)
    public boolean isEnablePartitioning() {
        return getBoolean(ARG_ENABLE_PARTITIONING, false);
    }

    @AttributesSetter(ARG_ENABLE_PARTITIONING)
    public void setEnablePartitioning(boolean val) {
        setBoolean(ARG_ENABLE_PARTITIONING, Boolean.valueOf(val));
    }

    @AttributesGetter(Configuration.FILTER_LOGICAL_ENTITLEMENTS)
    public boolean isFilterLogicalEntitlements() {
        return getBoolean(Configuration.FILTER_LOGICAL_ENTITLEMENTS, false);
    }

    @AttributesSetter(Configuration.FILTER_LOGICAL_ENTITLEMENTS)
    public void setFilterLogicalEntitlements(boolean filter) {
        setBoolean(Configuration.FILTER_LOGICAL_ENTITLEMENTS, filter);
    }

    @AttributesSetter(Configuration.ENABLE_OVERRIDE_VIOLATION_DEFAULT_REMEDIATOR)
    public void setEnableOverrideViaolationDefaultRemediator(Boolean allowOverrideRemediator) {
    	setBoolean(Configuration.ENABLE_OVERRIDE_VIOLATION_DEFAULT_REMEDIATOR, allowOverrideRemediator);
    }

    @AttributesGetter(Configuration.ENABLE_OVERRIDE_VIOLATION_DEFAULT_REMEDIATOR)
    public boolean isEnableOverrideViolationDefaultRemediator(SailPointContext context) throws GeneralException {
    	if (isEnableOverrideViolationDefaultRemediator() != null) {
    		return isEnableOverrideViolationDefaultRemediator().booleanValue();
    	} else {
    		return context.getConfiguration().getBoolean(Configuration.ENABLE_OVERRIDE_VIOLATION_DEFAULT_REMEDIATOR, false);
    	}
    }
    
    private Boolean isEnableOverrideViolationDefaultRemediator() {
    	return getBoolean(Configuration.ENABLE_OVERRIDE_VIOLATION_DEFAULT_REMEDIATOR);
    }

    /****************************************************************
     *
     * Manager Certification Properties
     *
     * ****************************************************************/

    @AttributesGetter(ARG_CERTIFIER)
    public String getCertifierName() {
        return getAttributes().getString(ARG_CERTIFIER);
    }

    @AttributesSetter(ARG_CERTIFIER)
    public void setCertifierName(String certifier) {
        setAttribute(ARG_CERTIFIER, certifier);
    }

    public Identity getCertifier(Resolver resolver) throws GeneralException {
        return getCertifierName() != null ? resolver.getObjectByName(Identity.class, getCertifierName()) : null;
    }

    /**
     * Common setting indicating if certifications should be generated for the
     * certifier's subordinate certifiers as well
     */
    @AttributesGetter(ARG_SUBORDINATE_CERTIFICATION_ENABLED)
    public boolean isSubordinateCertificationEnabled() {
        return getBoolean(ARG_SUBORDINATE_CERTIFICATION_ENABLED, false);
    }

    @AttributesSetter(ARG_SUBORDINATE_CERTIFICATION_ENABLED)
    public void setIsSubordinateCertificationEnabled(Boolean subordinateCertificationEnabled) {
        setBoolean(ARG_SUBORDINATE_CERTIFICATION_ENABLED, subordinateCertificationEnabled);
    }

    @AttributesGetter(ARG_COMPLETE_CERTIFICATION_HIERARCHY_ENABLED)
    public boolean isCompleteCertificationHierarchy() {
        return getBoolean(ARG_COMPLETE_CERTIFICATION_HIERARCHY_ENABLED, false);
    }

    @AttributesSetter(ARG_COMPLETE_CERTIFICATION_HIERARCHY_ENABLED)
    public void setCompleteCertificationHierarchy(Boolean completeCertificationHierarchy) {
        setBoolean(ARG_COMPLETE_CERTIFICATION_HIERARCHY_ENABLED, completeCertificationHierarchy);
    }

    /**
     * Manager Certification setting indicating if all employees reporting to subordinate
     * certifiers should also be included in the certification
     */
    @AttributesGetter(ARG_FLATTEN_MANAGER_CERTIFICATION_HIERARCHY_ENABLED)
    public boolean isFlattenManagerCertificationHierarchy() {
        return getBoolean(ARG_FLATTEN_MANAGER_CERTIFICATION_HIERARCHY_ENABLED, false);
    }

    @AttributesSetter(ARG_FLATTEN_MANAGER_CERTIFICATION_HIERARCHY_ENABLED)
    public void setFlattenManagerCertificationHierarchy(Boolean flattenManagerCertificationHierarchy) {
        setBoolean(ARG_FLATTEN_MANAGER_CERTIFICATION_HIERARCHY_ENABLED, flattenManagerCertificationHierarchy);
    }

    @AttributesGetter(Configuration.REQUIRE_REASSIGNMENT_COMPLETION)
    public boolean isRequireReassignmentCompletion(SailPointContext context) throws GeneralException  {
        return getBoolean(Configuration.REQUIRE_REASSIGNMENT_COMPLETION, context, false);
    }

    @AttributesSetter(Configuration.REQUIRE_REASSIGNMENT_COMPLETION)
    public void setRequireReassignmentCompletion(Boolean require) {
        setBoolean(Configuration.REQUIRE_REASSIGNMENT_COMPLETION, require);
    }

    @AttributesGetter(Configuration.ASSIMILATE_BULK_REASSIGNMENTS)
    public boolean isAssimilateBulkReassignments(SailPointContext context) throws GeneralException {
        return getBoolean(Configuration.ASSIMILATE_BULK_REASSIGNMENTS, context, false);
    }

    @AttributesSetter(Configuration.ASSIMILATE_BULK_REASSIGNMENTS)
    public void setAssimilateBulkReassignments(Boolean assimilate) {
        setBoolean(Configuration.ASSIMILATE_BULK_REASSIGNMENTS, assimilate);
    }

    @AttributesGetter(Configuration.AUTOMATE_SIGNOFF_ON_REASSIGNMENT)
    public boolean isAutomateSignOffOnReassignment(SailPointContext context) throws GeneralException {
        return getBoolean(Configuration.AUTOMATE_SIGNOFF_ON_REASSIGNMENT, context, false);
    }

    @AttributesSetter(Configuration.AUTOMATE_SIGNOFF_ON_REASSIGNMENT)
    public void setAutomateSignOffOnReassignment(Boolean automate) {
        setBoolean(Configuration.AUTOMATE_SIGNOFF_ON_REASSIGNMENT, automate);
    }

    @AttributesGetter(Configuration.CERTIFICATION_DELEGATION_REVIEW)
    public boolean isCertificationDelegationReviewRequired(SailPointContext context) throws GeneralException {
        return getBoolean(Configuration.CERTIFICATION_DELEGATION_REVIEW, context, false);
    }

    @AttributesSetter(Configuration.CERTIFICATION_DELEGATION_REVIEW)
    public void setCertificationDelegationReviewRequired(Boolean required) {
        setBoolean(Configuration.CERTIFICATION_DELEGATION_REVIEW, required);
    }

    /***************************************************************
     *
     * Data Owner Certification Properties
     *
     * ****************************************************************/

    @AttributesGetter(ARG_INCLUDE_UNOWNED_DATA)
    public boolean isIncludeUnownedData() {
        return getBoolean(ARG_INCLUDE_UNOWNED_DATA, false);
    }

    @AttributesSetter(ARG_INCLUDE_UNOWNED_DATA)
    public void setIncludeUnownedData(Boolean includeUnownedData) {
        setBoolean(ARG_INCLUDE_UNOWNED_DATA, includeUnownedData);
    }

    @AttributesGetter(ARG_INCLUDE_ENTITLEMENTS_GRANTED_BY_ROLES)
    public boolean isIncludeEntitlementsGrantedByRoles() {
        return getBoolean(ARG_INCLUDE_ENTITLEMENTS_GRANTED_BY_ROLES, false);
    }

    @AttributesSetter(ARG_INCLUDE_ENTITLEMENTS_GRANTED_BY_ROLES)
    public void setIncludeEntitlementsGrantedByRoles(Boolean val) {
        setBoolean(ARG_INCLUDE_ENTITLEMENTS_GRANTED_BY_ROLES, val);
    }

    @AttributesGetter(ARG_APP_OWNER_IS_UNOWNED_DATA_OWNER)
    public boolean isAppOwnerIsUnownedOwner() {
        return getBoolean(ARG_APP_OWNER_IS_UNOWNED_DATA_OWNER, true);
    }

    @AttributesSetter(ARG_APP_OWNER_IS_UNOWNED_DATA_OWNER)
    public void setAppOwnerIsUnownedOwner(Boolean appOwnerIsUnownedOwner) {
        setBoolean(ARG_APP_OWNER_IS_UNOWNED_DATA_OWNER, appOwnerIsUnownedOwner);
    }

    @AttributesGetter(ARG_UNOWNED_DATA_OWNER_NAME)
    public String getUnownedDataOwner() {
        return getAttributes().getString(ARG_UNOWNED_DATA_OWNER_NAME);
    }

    public Identity getUnownedDataOwner(Resolver resolver) throws GeneralException {
        String name = getUnownedDataOwner();
        if (name != null)
            return resolver.getObjectByName(Identity.class, name);
        return null;
    }

    @AttributesSetter(ARG_UNOWNED_DATA_OWNER_NAME)
    public void setUnownedDataOwner(String val) {
        setAttribute(ARG_UNOWNED_DATA_OWNER_NAME, val);
    }

    /****************************************************************
     *
     * Application Certification Properties
     *
     *****************************************************************/

    /**
     * Application Certification setting that indicates the applications being certified
     */
    @AttributesGetter(ARG_CERTIFIED_APPLICATION_IDS)
    public List<String> getApplicationIds() {
        return readList(ARG_CERTIFIED_APPLICATION_IDS);
    }

    @AttributesSetter(ARG_CERTIFIED_APPLICATION_IDS)
    public void setApplicationIds(List<String> applicationIds) {
        setList(ARG_CERTIFIED_APPLICATION_IDS, applicationIds);
    }

    public int getApplicationCount() {
        List<String> nameOrIds = readList(ARG_CERTIFIED_APPLICATION_IDS);
        return nameOrIds != null ? nameOrIds.size() : 0;
    }
    /***************************************************************
     *
     *  Identity Certification settings
     *
     ***************************************************************/

    /**
     * Bulk/Individual certification setting that specifies the identities being certified
     *
     * @return List of identity names that should be certified
     */
    @AttributesGetter(ARG_IDENTITY_LIST)
    public List<String> getIdentitiesToCertify() {
        return readList(ARG_IDENTITY_LIST);
    }

    @AttributesSetter(ARG_IDENTITY_LIST)
    public void setIdentitiesToCertify(List<String> identitiesToCertify) {
        setList(ARG_IDENTITY_LIST, identitiesToCertify);
    }

    /***************************************************************
     *
     *  Role Certification settings
     *
     ***************************************************************/

    /**
     * Returns list of business role ids/names selected for certification.
     * 
     * @return List of business role ids/names selected for certification
     */
    @AttributesGetter(ARG_BUSINESS_ROLE_LIST)
    public List<String> getBusinessRoleIds() {
        return readList(ARG_BUSINESS_ROLE_LIST);
    }

    public void setBusinessRoleIds(List<String> businessRoleIds) {
        this.setList(ARG_BUSINESS_ROLE_LIST, businessRoleIds);
    }

    /**
     * @return True if the user has elected to include all the children of selected nodes
     *         in the certification.
     */
    public boolean isIncludeRoleHierarchy() {
        return getBoolean(ARG_INCLUDE_ROLE_HIERARCHY, false);
    }

    public void setIncludeRoleHierarchy(Boolean includeRoleHierarchy) {
        setBoolean(ARG_INCLUDE_ROLE_HIERARCHY, includeRoleHierarchy);
    }


    public List<String> getRoleTypes() {
        return readList(ARG_ROLE_TYPES);
    }

    public void setRoleTypes(List<String> roleTypes) {
        setList(ARG_ROLE_TYPES, roleTypes);
    }


    /**************************************************************
     *
     * Advanced Certification settings
     *
     ***************************************************************/

    @SuppressWarnings("unchecked")
    public Map<String, List<String>> getIpopCertifierMap() {
        return (Map<String, List<String>>) getAttributes().get(ARG_IPOP_CERTIFIER_MAP);
    }

    public void setIpopCertifierMap(Map<String, List<String>> map) {
        setAttribute(ARG_IPOP_CERTIFIER_MAP, map);
    }

    /**
     * Given a GroupBean, convert it into a list of strings so it can
     * easily be stored in the map.
     */
    private List<String> serializeGroup(GroupBean group) {
        List<String> certifiers = new ArrayList<String>();
        if (null != group && null != group.getCertifiers()) {
            for (Identity certifier : group.getCertifiers()) {
                certifiers.add(certifier.getId());
            }
        }
        return certifiers;
    }

    private GroupBean deSerializeGroup(Resolver resolver, Map<String, List<String>> map, GroupDefinition groupDefinition)
            throws GeneralException {
        
        GroupBean groupBean = new GroupBean(groupDefinition);

        List<String> certifierIds = map.get(groupDefinition.getId());
        List<Identity> certifiers = new ArrayList<Identity>();
        if (certifierIds != null) {
            for (String certifierId : certifierIds) {
                certifiers.add(resolver.getObjectById(Identity.class, certifierId));
            }
        }

        groupBean.setCertifiers(certifiers);

        return groupBean;
    }

    public void removeGroups() {
        getAttributes().remove(ARG_IPOP_CERTIFIER_MAP);
    }

    public GroupBean getGroup(Resolver resolver, String id) throws GeneralException {

        GroupBean group = null;

        if (id != null) {
            Map<String, List<String>> map = getIpopCertifierMap();
            if (map != null && map.containsKey(id)) {
                GroupDefinition groupDefinition = resolver.getObjectById(GroupDefinition.class, id);
                if (groupDefinition != null) {
                    return deSerializeGroup(resolver, map, groupDefinition);
                }
            }
        }

        return group;
    }

    public void addGroup(GroupDefinition def) {

        if (def == null)
            return;

        GroupBean group = new GroupBean(def);
        Map<String, List<String>> map = getIpopCertifierMap();
        if (map == null)
            map = new HashMap<String, List<String>>();

        map.put(group.getGroupDefinition().getId(), serializeGroup(group));
    }

    public boolean hasGroup(String id) {

        if (id == null)
            return false;

        Map<String, List<String>> map = getIpopCertifierMap();
        if (map == null)
            return false;

        return map.containsKey(id);
    }

    public List<GroupBean> getGroupBeans(Resolver resolver) throws GeneralException {
        List<GroupBean> groupBeans = new ArrayList<GroupBean>();
        Map<String, List<String>> map = getIpopCertifierMap();
        if (null != map) {
            Set<String> groupDefIds = map.keySet();
            for (String groupDefId : groupDefIds) {
                GroupDefinition groupDefinition = resolver.getObjectById(GroupDefinition.class, groupDefId);
                if (groupDefinition != null) {
                    GroupBean groupBean = deSerializeGroup(resolver, map, groupDefinition);
                    groupBeans.add(groupBean);
                }
            }
        }
        return groupBeans;
    }

    public void setGroupBeans(List<GroupBean> factories) {
        Map<String, List<String>> map = null;
        if (factories != null) {
            map = new HashMap<String, List<String>>();
            for (GroupBean group : factories) {
                map.put(group.getGroupDefinition().getId(), serializeGroup(group));
            }
        }
        setIpopCertifierMap(map);
    }

    public void setFactoryCertifierMap(Map<String, String> map) {
        setAttribute(ARG_FACTORY_CERTIFIER_MAP, map);
    }

    @SuppressWarnings("unchecked")
    public Map<String, String> getFactoryCertifierMap() {
        return (Map<String, String>) getAttributes().get(ARG_FACTORY_CERTIFIER_MAP);
    }

    private void addFactoryBean(FactoryBean factory) {

        if (factory == null)
            return;

        Map<String, String> map = getFactoryCertifierMap();
        if (map == null)
            map = new HashMap<String, String>();
        map.put(factory.getId(), factory.getName());
    }

    public void addFactory(GroupFactory factory) {
        if (factory == null)
            return;

        addFactoryBean(new FactoryBean(factory.getId(), factory.getName()));
    }

    public FactoryBean getFactory(Resolver resolver, String factoryId) throws GeneralException {
        FactoryBean factoryBean = null;
        if (factoryId != null) {
            Map<String, String> facmap = getFactoryCertifierMap();
            if (facmap != null && facmap.containsKey(factoryId)) {
                // have to load the object to get the name, usually will have the factoryId
                // here but support name lookups too for hand edited schedules
                GroupFactory factory = resolver.getObjectById(GroupFactory.class, factoryId);
                if (factory != null) {
                    factoryBean = new FactoryBean(factory.getId(), factory.getName());
                    factoryBean.setCertifierRuleName(facmap.get(factoryId));
                }
            }
        }
        return factoryBean;
    }

    public void removeFactories() {
        setFactoryCertifierMap(null);
    }

    public boolean hasFactory(String id) {
        Map<String, String> map = getFactoryCertifierMap();
        return map != null && id != null && map.containsKey(id);
    }

    public List<FactoryBean> getFactoryBeans(Resolver resolver) throws GeneralException {
        List<FactoryBean> factoryBeans = new ArrayList<FactoryBean>();
        Map<String, String> facmap = getFactoryCertifierMap();
        if (facmap != null) {
            Set<String> factoryIds = facmap.keySet();
            for (String factoryId : factoryIds) {
                FactoryBean bean = getFactory(resolver, factoryId);
                if (bean != null)
                    factoryBeans.add(bean);
            }
        }

        return factoryBeans;
    }

    public void setFactoryBeans(List<FactoryBean> factories) {
        Map<String, String> map = null;
        if (factories != null) {
            // for factories, store a map of <FactoryId,RuleId>
            map = new HashMap<String, String>();
            for (FactoryBean f : factories) {
                String ruleName = f.getCertifierRuleName();
                // we should have caught missing ids by now but if not
                // keep going, maybe we can let the task use a default rule
                map.put(f.getId(), ruleName);
            }
        }
        setFactoryCertifierMap(map);
    }

    /**************************************************************
     *
     * Focused Certification settings
     *
     ***************************************************************/

    @AttributesSetter(ARG_ENTITY_FILTER)
    public void setEntityFilter(Filter f) {
        setAttribute(ARG_ENTITY_FILTER, f);
    }

    @AttributesGetter(ARG_ENTITY_FILTER)
    public Filter getEntityFilter() {
        return getFilter(ARG_ENTITY_FILTER);
    }

    @AttributesSetter(ARG_ENTITY_FILTER_VALUES)
    public void setEntityFilterValues(List<Map<String, Object>> filterValues) {
        setAttribute(ARG_ENTITY_FILTER_VALUES, filterValues);
    }

    @AttributesGetter(ARG_ENTITY_FILTER_VALUES)
    public List<Map<String, Object>> getEntityFilterValues() {
        return getFilterValues(ARG_ENTITY_FILTER_VALUES);
    }

    @AttributesSetter(ARG_ROLE_FILTER)
    public void setRoleFilter(Filter f) {
        setAttribute(ARG_ROLE_FILTER, f);
    }

    @AttributesGetter(ARG_ROLE_FILTER)
    public Filter getRoleFilter() {
        return getFilter(ARG_ROLE_FILTER);
    }

    @AttributesSetter(ARG_ROLE_FILTER_VALUES)
    public void setRoleFilterValues(List<Map<String, Object>> filterValues) {
        setAttribute(ARG_ROLE_FILTER_VALUES, filterValues);
    }

    @AttributesGetter(ARG_ROLE_FILTER_VALUES)
    public List<Map<String, Object>> getRoleFilterValues() {
        return getFilterValues(ARG_ROLE_FILTER_VALUES);
    }

    @AttributesSetter(ARG_ENTITLEMENT_FILTER)
    public void setEntitlementFilter(Filter f) {
        setAttribute(ARG_ENTITLEMENT_FILTER, f);
    }

    @AttributesGetter(ARG_ENTITLEMENT_FILTER)
    public Filter getEntitlementFilter() {
        return getFilter(ARG_ENTITLEMENT_FILTER);
    }

    @AttributesSetter(ARG_ENTITLEMENT_FILTER_VALUES)
    public void setEntitlementFilterValues(List<Map<String, Object>> filterValues) {
        setAttribute(ARG_ENTITLEMENT_FILTER_VALUES, filterValues);
    }

    @AttributesGetter(ARG_ENTITLEMENT_FILTER_VALUES)
    public List<Map<String, Object>> getEntitlementFilterValues() {
        return getFilterValues(ARG_ENTITLEMENT_FILTER_VALUES);
    }

    @AttributesSetter(ARG_TARGET_PERMISSION_FILTER)
    public void setTargetPermissionFilter(Filter f) {
        setAttribute(ARG_TARGET_PERMISSION_FILTER, f);
    }

    @AttributesGetter(ARG_TARGET_PERMISSION_FILTER)
    public Filter getTargetPermissionFilter() {
        return getFilter(ARG_TARGET_PERMISSION_FILTER);
    }

    @AttributesSetter(ARG_TARGET_PERMISSION_FILTER_VALUES)
    public void setTargetPermissionFilterValues(List<Map<String, Object>> filterValues) {
        setAttribute(ARG_TARGET_PERMISSION_FILTER_VALUES, filterValues);
    }

    @AttributesGetter(ARG_TARGET_PERMISSION_FILTER_VALUES)
    public List<Map<String, Object>> getTargetPermissionFilterValues() {
        return getFilterValues(ARG_TARGET_PERMISSION_FILTER_VALUES);
    }

    @AttributesSetter(ARG_TARGET_PERMISSION_APP_FILTER)
    public void setTargetPermissionApplicationFilter(Filter f) {
        setAttribute(ARG_TARGET_PERMISSION_APP_FILTER, f);
    }

    @AttributesGetter(ARG_TARGET_PERMISSION_APP_FILTER)
    public Filter getTargetPermissionApplicationFilter() {
        return getFilter(ARG_TARGET_PERMISSION_APP_FILTER);
    }

    @AttributesSetter(ARG_ACCOUNT_FILTER)
    public void setAccountFilter(Filter f) {
        setAttribute(ARG_ACCOUNT_FILTER, f);
    }

    @AttributesGetter(ARG_ACCOUNT_FILTER)
    public Filter getAccountFilter() {
        return getFilter(ARG_ACCOUNT_FILTER);
    }

    @AttributesSetter(ARG_ACCOUNT_FILTER_VALUES)
    public void setAccountFilterValues(List<Map<String, Object>> filterValues) {
        setAttribute(ARG_ACCOUNT_FILTER_VALUES, filterValues);
    }

    @AttributesGetter(ARG_ACCOUNT_FILTER_VALUES)
    public List<Map<String, Object>> getAccountFilterValues() {
        return getFilterValues(ARG_ACCOUNT_FILTER_VALUES);
    }

    @AttributesGetter(ARG_INCLUDE_ARCHIVED_ENTITIES)
    public boolean isIncludeArchivedEntities() {
        return getAttributes().getBoolean(ARG_INCLUDE_ARCHIVED_ENTITIES);
    }

    @AttributesSetter(ARG_INCLUDE_ARCHIVED_ENTITIES)
    public void setIncludeArchivedEntities(boolean b) {
        setAttribute(ARG_INCLUDE_ARCHIVED_ENTITIES, b);
    }
    
    @AttributesGetter(ARG_BACKUP_CERTIFIER)
    public String getBackupCertifierName() {
        return getAttributes().getString(ARG_BACKUP_CERTIFIER);
    }

    @AttributesSetter(ARG_BACKUP_CERTIFIER)
    public void setBackupCertifierName(String backupCertifier) {
        setAttribute(ARG_BACKUP_CERTIFIER, backupCertifier);
    }

    @AttributesGetter(ARG_CERTIFIER_OWNER_ROLE)
    public String getCertifierOwnerRole() {
        return getAttributes().getString(ARG_CERTIFIER_OWNER_ROLE);
    }

    @AttributesSetter(ARG_CERTIFIER_OWNER_ROLE)
    public void setCertifierOwnerRole(String certifierOwnerRole) {
        setAttribute(ARG_CERTIFIER_OWNER_ROLE, certifierOwnerRole);
    }

    @AttributesGetter(ARG_CERTIFIER_OWNER_ENTITLEMENT)
    public String getCertifierOwnerEntitlement() {
        return getAttributes().getString(ARG_CERTIFIER_OWNER_ENTITLEMENT);
    }

    @AttributesSetter(ARG_CERTIFIER_OWNER_ENTITLEMENT)
    public void setCertifierOwnerEntitlement(String certifierOwnerEntitlement) {
        setAttribute(ARG_CERTIFIER_OWNER_ENTITLEMENT, certifierOwnerEntitlement);
    }

    @AttributesGetter(ARG_CERTIFIER_OWNER_ACCOUNT)
    public String getCertifierOwnerAccount() {
        return getAttributes().getString(ARG_CERTIFIER_OWNER_ACCOUNT);
    }

    @AttributesSetter(ARG_CERTIFIER_OWNER_ACCOUNT)
    public void setCertifierOwnerAccount(String certifierOwnerAccount) {
        setAttribute(ARG_CERTIFIER_OWNER_ACCOUNT, certifierOwnerAccount);
    }


    /**
     * Gets the filter based on the argument in the attributes map.
     * @param arg Keyed argument for the filters
     */
    private Filter getFilter(String arg) {
        Filter f = null;
        Object o = getAttribute(arg);
        if (o instanceof Filter) {
            f = (Filter)o;
        }
        else if (o != null) {
            // log something...
            setAttribute(arg, null);
        }
        return f;
    }

    /**
     * Gets the UI filter values based on the keyed argument.
     * We store the filter values as maps instead of ListFilterValue objects to
     * eliminate reference to the service layer
     * @param arg Keyed argument for the filter values inside the attributes map.
     * @return A collection of maps of filter values.
     */
    private List<Map<String, Object>> getFilterValues(String arg) {
        List<Map<String, Object>> filterValues = null;
        List list = getAttributes().getList(arg);
        if (!Util.isEmpty(list)) {
            filterValues = (List<Map<String, Object>>)list;
        }

        return filterValues;
    }

    /**
     * Do we have any consistency with rules, store names or
     * <References>?
     */
    @AttributesSetter(ARG_ENTITY_RULE)
    public void setEntityRule(String name) {
        setAttribute(ARG_ENTITY_RULE, name);
    }

    @AttributesGetter(ARG_ENTITY_RULE)
    public String getEntityRule() {
        return getAttributes().getString(ARG_ENTITY_RULE);
    }

    @AttributesSetter(ARG_CERTIFIER_RULE)
    public void setCertifierRule(String name) {
        setAttribute(ARG_CERTIFIER_RULE, name);
    }

    @AttributesGetter(ARG_CERTIFIER_RULE)
    public String getCertifierRule() {
        return getAttributes().getString(ARG_CERTIFIER_RULE);
    }

    @AttributesSetter(ARG_PARTITION_COUNT)
    public void setPartitionCount(Integer count) {
        setString(ARG_PARTITION_COUNT, count);
    }

    @AttributesGetter(ARG_PARTITION_COUNT)
    public Integer getPartitionCount() {
        return getInteger(ARG_PARTITION_COUNT);
    }

    public void setPartitionSize(int i) {
        setAttribute(ARG_PARTITION_SIZE, Util.itoa(i));
    }

    public int getPartitionSize() {
        return getAttributes().getInt(ARG_PARTITION_SIZE);
    }

    @AttributesSetter(ARG_ENTITY_SELECTION_TYPE)
    public void setEntitySelectionType(String entitySelectionType) {
        setAttribute(ARG_ENTITY_SELECTION_TYPE, entitySelectionType);
    }

    @AttributesGetter(ARG_ENTITY_SELECTION_TYPE)
    public String getEntitySelectionType() {
        return getAttributes().getString(ARG_ENTITY_SELECTION_TYPE);
    }

    @AttributesSetter(ARG_ENTITY_POPULATION)
    public void setEntityPopulation(String name) {
        setAttribute(ARG_ENTITY_POPULATION, name);
    }

    @AttributesGetter(ARG_ENTITY_POPULATION)
    public String getEntityPopulation() {
        return getAttributes().getString(ARG_ENTITY_POPULATION);
    }

    public void setProfile(boolean b) {
        setAttribute(ARG_PROFILE, Util.otoa(b));
    }

    public boolean isProfile() {
        return getAttributes().getBoolean(ARG_PROFILE);
    }

    /**************************************************************
     *
     * Continuous Certification Properties
     *
     * ************************************************************/

    public boolean isContinuous() {
        return getAttributes().getBoolean(ARG_CONTINUOUS);
    }

    public void setContinuous(boolean continuous) {
        setAttribute(ARG_CONTINUOUS, Boolean.toString(continuous));
    }

    public List<ContinuousStateConfig> createContinuousConfig(SailPointContext context)
            throws GeneralException {

        List<ContinuousStateConfig> configs = new ArrayList<ContinuousStateConfig>();

        // Certified config - no notification config for now.
        ContinuousStateConfig certified =
                new ContinuousStateConfig(AbstractCertificationItem.ContinuousState.Certified, true,
                        this.getCertifiedDuration(), null);
        configs.add(certified);

        // Certification required config.
        sailpoint.object.NotificationConfig certReqNotif = getNotificationConfigFromDb(CERT_REQUIRED_NOTIF_PREFIX);
        ContinuousStateConfig certReq =
                new ContinuousStateConfig(AbstractCertificationItem.ContinuousState.CertificationRequired, true,
                        this.getCertificationRequiredDuration(),
                        certReqNotif);
        configs.add(certReq);

        // Overdue config.
        sailpoint.object.NotificationConfig overdueNotif = getNotificationConfigFromDb(OVERDUE_NOTIF_PREFIX);
        ContinuousStateConfig overdue =
                new ContinuousStateConfig(AbstractCertificationItem.ContinuousState.Overdue, true, null, overdueNotif);
        configs.add(overdue);

        return configs;
    }

    /***************************************************************
     *
     * Phase Configuration Properties
     *
     * **************************************************************/

    public List<CertificationPhaseConfig> createPhaseConfig(SailPointContext context)
    	throws GeneralException {
    	
    	return createPhaseConfig(context, false);
    }
    
    public List<CertificationPhaseConfig> createPhaseConfig(SailPointContext context, boolean bypassStaging)
            throws GeneralException {

        List<CertificationPhaseConfig> phases = new ArrayList<CertificationPhaseConfig>();

        // The certification notification config should be null for continuous.
        sailpoint.object.NotificationConfig certNotifConfig = null;
        if (!isContinuous()) {
            certNotifConfig = getNotificationConfigFromDb(CERTIFICATION_NOTIF_PREFIX);
        }
        
        // Staging period..
        if (isStagingEnabled() && !bypassStaging) {
            CertificationPhaseConfig stagingPhase = new CertificationPhaseConfig(Certification.Phase.Staged, true, 
                    null, certNotifConfig);
            
            phases.add(stagingPhase);
        }

        // Active Period.

        // Get the duration, if it's not in the args use one month.  Note that
        // active period duration does not apply to continuous certs.  Instead
        // we use the continuous config.
        Duration activePeriodDuration = null;
        if (!isContinuous()) {
            if (null != getActivePeriodDurationScale()) {
                activePeriodDuration =
                        new Duration(getActivePeriodDurationAmount(),
                                getActivePeriodDurationScale());
            } else {
                activePeriodDuration = new Duration(1, Duration.Scale.Month);
            }
        }
        CertificationPhaseConfig active =
                new CertificationPhaseConfig(Certification.Phase.Active, true,
                        activePeriodDuration, certNotifConfig);
        phases.add(active);

        // Challenge Period
        if (isChallengePeriodEnabled()) {
            Duration challengePeriodDuration = null;
            if (null != getChallengePeriodDurationScale()) {
                challengePeriodDuration =
                        new Duration(getChallengePeriodDurationAmount(),
                                getChallengePeriodDurationScale());
            }
            phases.add(new CertificationPhaseConfig(Certification.Phase.Challenge, true,
                    challengePeriodDuration, null));
        }

        // Remediation Period
        // Currently, we're overloading the notification config on the remediation phase
        // to hold the notification configuration for remediation work items, so we'll always
        // add the remediation configuration and just disable it.
        sailpoint.object.NotificationConfig remediationNotifConfig = getNotificationConfigFromDb(REMEDIATION_NOTIF_PREFIX);

        Duration remediationPeriodDuration = null;
        if (null != getRemediationPeriodDurationScale()) {
            remediationPeriodDuration =
                    new Duration(getRemediationPeriodDurationAmount(),
                            getRemediationPeriodDurationScale());
        }

        phases.add(new CertificationPhaseConfig(Certification.Phase.Remediation,
                        isRemediationPeriodEnabled(), remediationPeriodDuration, remediationNotifConfig));

        return phases;
    }

    @AttributesGetter(ARG_CERTIFIED_DURATION_AMOUNT)
    public Long getCertifiedDurationAmount() {
        return getAttributes().get(ARG_CERTIFIED_DURATION_AMOUNT) != null ?
                getAttributes().getLong(ARG_CERTIFIED_DURATION_AMOUNT) : null;
    }

    @AttributesSetter(ARG_CERTIFIED_DURATION_AMOUNT)
    public void setCertifiedDurationAmount(Long amount) {
        setString(ARG_CERTIFIED_DURATION_AMOUNT, amount);
    }

    @AttributesGetter(ARG_CERTIFIED_DURATION_SCALE)
    public Duration.Scale getCertifiedDurationScale() {
        return getScale(ARG_CERTIFIED_DURATION_SCALE);
    }

    @AttributesSetter(ARG_CERTIFIED_DURATION_SCALE)
    public void setCertifiedDurationScale(Duration.Scale scale) {
        setAttribute(ARG_CERTIFIED_DURATION_SCALE, scale != null ? scale.name() : null);
    }

    public Duration getCertifiedDuration() {
        return createDuration(getCertifiedDurationAmount(),
                getCertifiedDurationScale());
    }

    public Long getCertificationRequiredDurationAmount() {
        return getAttributes().get(ARG_CERTIFICATION_REQUIRED_DURATION_AMOUNT) != null ?
                getAttributes().getLong(ARG_CERTIFICATION_REQUIRED_DURATION_AMOUNT) : null;
    }

    public void setCertificationRequiredDurationAmount(Long amount) {
        setString(ARG_CERTIFICATION_REQUIRED_DURATION_AMOUNT, amount);
    }

    public Duration.Scale getCertificationRequiredDurationScale() {
        String scale = getAttributes().getString(ARG_CERTIFICATION_REQUIRED_DURATION_SCALE);
        return scale != null ? Duration.Scale.valueOf(scale) : null;
    }

    public void setCertificationRequiredDurationScale(Duration.Scale scale) {
        setAttribute(ARG_CERTIFICATION_REQUIRED_DURATION_SCALE, scale != null ? scale.name() : null);
    }

    public Duration getCertificationRequiredDuration() {
        return createDuration(getCertificationRequiredDurationAmount(),
                getCertificationRequiredDurationScale());
    }

    /**
     * Length of the active period.
     */
    @AttributesGetter(ARG_ACTIVE_PERIOD_DURATION_AMOUNT)
    public Long getActivePeriodDurationAmount() {
        return getAttributes().get(ARG_ACTIVE_PERIOD_DURATION_AMOUNT) != null ?
                getAttributes().getLong(ARG_ACTIVE_PERIOD_DURATION_AMOUNT) : null;
    }

    @EditablePhase(Certification.Phase.Active)
    @AttributesSetter(ARG_ACTIVE_PERIOD_DURATION_AMOUNT)
    public void setActivePeriodDurationAmount(Long amount) {
        setString(ARG_ACTIVE_PERIOD_DURATION_AMOUNT, amount);
    }

    /**
     * Scale of the active period duration.
     */
    @AttributesGetter(ARG_ACTIVE_PERIOD_DURATION_SCALE)
    public Duration.Scale getActivePeriodDurationScale() {
        return getScale(ARG_ACTIVE_PERIOD_DURATION_SCALE);
    }

    @EditablePhase(Certification.Phase.Active)
    @AttributesSetter(ARG_ACTIVE_PERIOD_DURATION_SCALE)
    public void setActivePeriodDurationScale(Duration.Scale scale) {
        setString(ARG_ACTIVE_PERIOD_DURATION_SCALE, scale);
    }

    public Boolean isAllowEntityBulkApprove() {
        return getBoolean(ARG_ALLOW_ENTITY_BULK_APPROVE);
    }

    @AttributesGetter(ARG_ALLOW_ENTITY_BULK_APPROVE)
    public boolean isAllowEntityBulkApprove(SailPointContext context) throws GeneralException {
        if (isAllowEntityBulkApprove() != null)
            return isAllowEntityBulkApprove();

        return context.getConfiguration().getBoolean(Configuration.ALLOW_CERT_ENTITY_BULK_APPROVE, false);
    }

    @AttributesSetter(ARG_ALLOW_ENTITY_BULK_APPROVE)
    public void setAllowEntityBulkApprove(Boolean approve) {
        setBoolean(ARG_ALLOW_ENTITY_BULK_APPROVE, approve);
    }

    public Boolean isAllowListBulkApprove() {
        return getBoolean(ARG_ALLOW_LIST_VIEW_BULK_APPROVE);
    }

    @AttributesGetter(ARG_ALLOW_LIST_VIEW_BULK_APPROVE)
    public boolean isAllowListBulkApprove(SailPointContext context) throws GeneralException {
        if (isAllowListBulkApprove() != null)
            return isAllowListBulkApprove();

        return context.getConfiguration().getBoolean(Configuration.ALLOW_LIST_VIEW_BULK_APPROVE, false);
    }

    @AttributesSetter(ARG_ALLOW_LIST_VIEW_BULK_APPROVE)
    public void setAllowListBulkApprove(Boolean approve) {
        setBoolean(ARG_ALLOW_LIST_VIEW_BULK_APPROVE, approve);
    }

    public Boolean isAllowListBulkRevoke() {
        return getBoolean(ARG_ALLOW_LIST_VIEW_BULK_REVOKE);
    }

    @AttributesGetter(ARG_ALLOW_LIST_VIEW_BULK_REVOKE)
    public boolean isAllowListBulkRevoke(SailPointContext context) throws GeneralException {
        if (isAllowListBulkRevoke() != null)
            return isAllowListBulkRevoke().booleanValue();

        return context.getConfiguration().getBoolean(Configuration.ALLOW_LIST_VIEW_BULK_REVOKE, false);
    }

    @AttributesSetter(ARG_ALLOW_LIST_VIEW_BULK_REVOKE)
    public void setAllowListBulkRevoke(Boolean revoke) {
        setBoolean(ARG_ALLOW_LIST_VIEW_BULK_REVOKE, revoke);
    }

    public Boolean isAllowListBulkMitigate() {
        return getBoolean(ARG_ALLOW_LIST_VIEW_BULK_MITIGATE);
    }

    @AttributesGetter(ARG_ALLOW_LIST_VIEW_BULK_MITIGATE)
    public boolean isAllowListBulkMitigate(SailPointContext context) throws GeneralException {
        if (isAllowListBulkMitigate() != null)
            return isAllowListBulkMitigate().booleanValue();

        return context.getConfiguration().getBoolean(Configuration.ALLOW_LIST_VIEW_BULK_MITIGATE, false);
    }

    @AttributesSetter(ARG_ALLOW_LIST_VIEW_BULK_MITIGATE)
    public void setAllowListBulkMitigate(Boolean mitigate) {
        setBoolean(ARG_ALLOW_LIST_VIEW_BULK_MITIGATE, mitigate);
    }

    public Boolean isAllowListBulkReassign() {
        return getBoolean(ARG_ALLOW_LIST_VIEW_BULK_REASSIGN);
    }

    @AttributesGetter(ARG_ALLOW_LIST_VIEW_BULK_REASSIGN)
    public boolean isAllowListBulkReassign(SailPointContext context) throws GeneralException {
        if (isAllowListBulkReassign() != null) {
            return isAllowListBulkReassign();
        }

        return context.getConfiguration().getBoolean(Configuration.ALLOW_LIST_VIEW_BULK_REASSIGN, false);
    }

    @AttributesSetter(ARG_ALLOW_LIST_VIEW_BULK_REASSIGN)
    public void setAllowListBulkReassign(boolean allow) {
        setBoolean(ARG_ALLOW_LIST_VIEW_BULK_REASSIGN, allow);
    }

    public Boolean isAllowListBulkClearDecisions() {
        return getBoolean(Configuration.ALLOW_LIST_VIEW_BULK_CLEAR_DECISIONS);
    }

    @AttributesGetter(Configuration.ALLOW_LIST_VIEW_BULK_CLEAR_DECISIONS)
    public boolean isAllowListBulkClearDecisions(SailPointContext context) throws GeneralException {
        if (isAllowListBulkClearDecisions() != null)
            return isAllowListBulkClearDecisions().booleanValue();

        return context.getConfiguration().getBoolean(Configuration.ALLOW_LIST_VIEW_BULK_CLEAR_DECISIONS, false);
    }

    @AttributesSetter(Configuration.ALLOW_LIST_VIEW_BULK_CLEAR_DECISIONS)
    public void setAllowListBulkClearDecisions(Boolean revoke) {
        setBoolean(Configuration.ALLOW_LIST_VIEW_BULK_CLEAR_DECISIONS, revoke);
    }

    @AttributesGetter(ARG_CHALLENGE_PERIOD_ENABLED)
    public boolean isChallengePeriodEnabled() {
        return getBoolean(ARG_CHALLENGE_PERIOD_ENABLED, false);
    }

    @AttributesSetter(ARG_CHALLENGE_PERIOD_ENABLED)
    @EditablePhase(Certification.Phase.Active)
    public void setChallengePeriodEnabled(Boolean enabled) {
        setBoolean(ARG_CHALLENGE_PERIOD_ENABLED, enabled);
    }

    @AttributesGetter(ARG_CHALLENGE_PERIOD_DURATION_AMOUNT)
    public Long getChallengePeriodDurationAmount() {
        return getAttributes().get(ARG_CHALLENGE_PERIOD_DURATION_AMOUNT) != null ?
                getAttributes().getLong(ARG_CHALLENGE_PERIOD_DURATION_AMOUNT) : null;
    }

    @AttributesSetter(ARG_CHALLENGE_PERIOD_DURATION_AMOUNT)
    @EditablePhase(Certification.Phase.Challenge)
    public void setChallengePeriodDurationAmount(Long amount) {
        setString(ARG_CHALLENGE_PERIOD_DURATION_AMOUNT, amount);
    }

    @AttributesGetter(ARG_CHALLENGE_PERIOD_DURATION_SCALE)
    public Duration.Scale getChallengePeriodDurationScale() {
        return getScale(ARG_CHALLENGE_PERIOD_DURATION_SCALE);
    }

    @AttributesSetter(ARG_CHALLENGE_PERIOD_DURATION_SCALE)
    @EditablePhase(Certification.Phase.Challenge)
    public void setChallengePeriodDurationScale(Duration.Scale scale) {
        setAttribute(ARG_CHALLENGE_PERIOD_DURATION_SCALE, scale != null ? scale.name() : null);
    }

    public String getChallengePeriodStartEmailTemplate() {
        return getEmailTemplateNameFor(Configuration.CHALLENGE_PERIOD_START_EMAIL_TEMPLATE);
    }

    @AttributesGetter(Configuration.CHALLENGE_PERIOD_START_EMAIL_TEMPLATE)
    public String getChallengePeriodStartEmailTemplate(SailPointContext context) throws GeneralException {
        String template = getChallengePeriodStartEmailTemplate();
        return template != null ?
               template :
               context.getConfiguration().getString(Configuration.CHALLENGE_PERIOD_START_EMAIL_TEMPLATE);
    }

    @AttributesSetter(Configuration.CHALLENGE_PERIOD_START_EMAIL_TEMPLATE)
    @EditablePhase(Certification.Phase.Active)
    public void setChallengePeriodStartEmailTemplate(String template) {
        setEmailTemplateNameFor(Configuration.CHALLENGE_PERIOD_START_EMAIL_TEMPLATE, template);
    }

    public String getChallengeGenerationEmailTemplate() {
        return getEmailTemplateNameFor(Configuration.CHALLENGE_GENERATION_EMAIL_TEMPLATE);
    }

    @AttributesGetter(Configuration.CHALLENGE_GENERATION_EMAIL_TEMPLATE)
    public String getChallengeGenerationEmailTemplate(SailPointContext context) throws GeneralException {
        String template = getChallengeGenerationEmailTemplate();
        return template != null ?
               template :
               context.getConfiguration().getString(Configuration.CHALLENGE_GENERATION_EMAIL_TEMPLATE);
    }

    @AttributesSetter(Configuration.CHALLENGE_GENERATION_EMAIL_TEMPLATE)
    @EditablePhase(Certification.Phase.Challenge)
    public void setChallengeGenerationEmailTemplate(String template) {
        setEmailTemplateNameFor(Configuration.CHALLENGE_GENERATION_EMAIL_TEMPLATE, template);
    }

    public String getChallengePeriodEndEmailTemplate() {
        return getEmailTemplateNameFor(Configuration.CHALLENGE_PERIOD_END_EMAIL_TEMPLATE);
    }

    @AttributesGetter(Configuration.CHALLENGE_PERIOD_END_EMAIL_TEMPLATE)
    public String getChallengePeriodEndEmailTemplate(SailPointContext context) throws GeneralException {
        String template = getChallengePeriodEndEmailTemplate();
        return template != null ?
               template :
               context.getConfiguration().getString(Configuration.CHALLENGE_PERIOD_END_EMAIL_TEMPLATE);
    }

    @AttributesSetter(Configuration.CHALLENGE_PERIOD_END_EMAIL_TEMPLATE)
    @EditablePhase(Certification.Phase.Challenge)
    public void setChallengePeriodEndEmailTemplate(String template) {
        setEmailTemplateNameFor(Configuration.CHALLENGE_PERIOD_END_EMAIL_TEMPLATE, template);
    }

    public String getChallengeExpirationEmailTemplate() {
        return getEmailTemplateNameFor(Configuration.CHALLENGE_EXPIRED_EMAIL_TEMPLATE);
    }

    @AttributesGetter(Configuration.CHALLENGE_EXPIRED_EMAIL_TEMPLATE)
    public String getChallengeExpirationEmailTemplate(SailPointContext context) throws GeneralException {
        String template = getChallengeExpirationEmailTemplate();
        return template != null ?
               template :
               context.getConfiguration().getString(Configuration.CHALLENGE_EXPIRED_EMAIL_TEMPLATE);
    }

    @AttributesSetter(Configuration.CHALLENGE_EXPIRED_EMAIL_TEMPLATE)
    @EditablePhase(Certification.Phase.Challenge)
    public void setChallengeExpirationEmailTemplate(String template) {
        setEmailTemplateNameFor(Configuration.CHALLENGE_EXPIRED_EMAIL_TEMPLATE, template);
    }

    public String getCertificationDecisionChallengedEmailTemplate() {
        return getEmailTemplateNameFor(Configuration.CERT_DECISION_CHALLENGED_EMAIL_TEMPLATE);
    }

    @AttributesGetter(Configuration.CERT_DECISION_CHALLENGED_EMAIL_TEMPLATE)
    public String getCertificationDecisionChallengedEmailTemplate(SailPointContext context) throws GeneralException {
        String template = getCertificationDecisionChallengedEmailTemplate();
        return template != null ?
               template :
               context.getConfiguration().getString(Configuration.CERT_DECISION_CHALLENGED_EMAIL_TEMPLATE);
    }

    @AttributesSetter(Configuration.CERT_DECISION_CHALLENGED_EMAIL_TEMPLATE)
    @EditablePhase(Certification.Phase.Challenge)
    public void setCertificationDecisionChallengedEmailTemplate(String template) {
        setEmailTemplateNameFor(Configuration.CERT_DECISION_CHALLENGED_EMAIL_TEMPLATE, template);
    }

    public String getChallengeAcceptedEmailTemplate() {
        return getEmailTemplateNameFor(Configuration.CHALLENGE_ACCEPTED_EMAIL_TEMPLATE);
    }

    @AttributesGetter(Configuration.CHALLENGE_ACCEPTED_EMAIL_TEMPLATE)
    public String getChallengeAcceptedEmailTemplate(SailPointContext context) throws GeneralException {
        String template = getChallengeAcceptedEmailTemplate();
        return template != null ?
               template :
               context.getConfiguration().getString(Configuration.CHALLENGE_ACCEPTED_EMAIL_TEMPLATE);
    }

    @AttributesSetter(Configuration.CHALLENGE_ACCEPTED_EMAIL_TEMPLATE)
    @EditablePhase(Certification.Phase.Challenge)
    public void setChallengeAcceptedEmailTemplate(String template) {
        setEmailTemplateNameFor(Configuration.CHALLENGE_ACCEPTED_EMAIL_TEMPLATE, template);
    }

    public String getChallengeDecisionExpirationEmailTemplate() {
        return getEmailTemplateNameFor(Configuration.CHALLENGE_DECISION_EXPIRED_EMAIL_TEMPLATE);
    }

    @AttributesGetter(Configuration.CHALLENGE_DECISION_EXPIRED_EMAIL_TEMPLATE)
    public String getChallengeDecisionExpirationEmailTemplate(SailPointContext context) throws GeneralException {
        String template = getChallengeDecisionExpirationEmailTemplate();
        return template != null ?
               template :
               context.getConfiguration().getString(Configuration.CHALLENGE_DECISION_EXPIRED_EMAIL_TEMPLATE);
    }

    @AttributesSetter(Configuration.CHALLENGE_DECISION_EXPIRED_EMAIL_TEMPLATE)
    @EditablePhase(Certification.Phase.Challenge)
    public void setChallengeDecisionExpirationEmailTemplate(String template) {
        setEmailTemplateNameFor(Configuration.CHALLENGE_DECISION_EXPIRED_EMAIL_TEMPLATE, template);
    }

    public String getChallengeRejectedEmailTemplate() {
        return getEmailTemplateNameFor(Configuration.CHALLENGE_REJECTED_EMAIL_TEMPLATE);
    }

    @AttributesGetter(Configuration.CHALLENGE_REJECTED_EMAIL_TEMPLATE)
    public String getChallengeRejectedEmailTemplate(SailPointContext context) throws GeneralException {
        String template = getChallengeRejectedEmailTemplate();
        return template != null ?
               template :
               context.getConfiguration().getString(Configuration.CHALLENGE_REJECTED_EMAIL_TEMPLATE);
    }

    @AttributesSetter(Configuration.CHALLENGE_REJECTED_EMAIL_TEMPLATE)
    @EditablePhase(Certification.Phase.Challenge)
    public void setChallengeRejectedEmailTemplate(String template) {
        setEmailTemplateNameFor(Configuration.CHALLENGE_REJECTED_EMAIL_TEMPLATE, template);
    }

    public String getCertificationEmailTemplate() {
        return getEmailTemplateNameFor(Configuration.CERTIFICATION_EMAIL_TEMPLATE);
    }

    @AttributesGetter(Configuration.CERTIFICATION_EMAIL_TEMPLATE)
    public String getCertificationEmailTemplate(SailPointContext context) throws GeneralException {
        String template = getCertificationEmailTemplate();
        return template != null ?
               template :
               context.getConfiguration().getString(Configuration.CERTIFICATION_EMAIL_TEMPLATE);
    }

    @AttributesSetter(Configuration.CERTIFICATION_EMAIL_TEMPLATE)
    @EditablePhase(Certification.Phase.Staged)
    public void setCertificationEmailTemplate(String template) {
        setEmailTemplateNameFor(Configuration.CERTIFICATION_EMAIL_TEMPLATE, template);
    }

    @AttributesSetter(Configuration.BULK_REASSIGNMENT_EMAIL_TEMPLATE)
    @EditablePhase(Certification.Phase.Remediation)
    public void setBulkReassignmentEmailTemplate(String template) {
        setEmailTemplateNameFor(Configuration.BULK_REASSIGNMENT_EMAIL_TEMPLATE, template);
    }

    public String getBulkReassignmentEmailTemplate() {
        return getEmailTemplateNameFor(Configuration.BULK_REASSIGNMENT_EMAIL_TEMPLATE);
    }

    @AttributesGetter(Configuration.BULK_REASSIGNMENT_EMAIL_TEMPLATE)
    public String getBulkReassignmentEmailTemplate(SailPointContext context) throws GeneralException {
        String template = getBulkReassignmentEmailTemplate();
        return template != null ?
                template :
                context.getConfiguration().getString(Configuration.BULK_REASSIGNMENT_EMAIL_TEMPLATE);
    }


    @AttributesGetter(ARG_AUTOMATIC_CLOSING_ENABLED)
    public boolean isAutomaticClosingEnabled() {
        return getBoolean(ARG_AUTOMATIC_CLOSING_ENABLED, false);
    }

    @AttributesSetter(ARG_AUTOMATIC_CLOSING_ENABLED)
    @EditablePhase(Certification.Phase.Remediation)
    public void setAutomaticClosingEnabled(Boolean enabled) {
        setBoolean(ARG_AUTOMATIC_CLOSING_ENABLED, enabled);
    }

    @AttributesGetter(ARG_AUTOMATIC_CLOSING_DURATION_AMOUNT)
    public Long getAutomaticClosingInterval() {
        return getAttributes().get(ARG_AUTOMATIC_CLOSING_DURATION_AMOUNT) != null ?
                getAttributes().getLong(ARG_AUTOMATIC_CLOSING_DURATION_AMOUNT) : null;
    }

    @AttributesSetter(ARG_AUTOMATIC_CLOSING_DURATION_AMOUNT)
    @EditablePhase(Certification.Phase.Remediation)
    public void setAutomaticClosingInterval(Long amount) {
        setString(ARG_AUTOMATIC_CLOSING_DURATION_AMOUNT, amount);
    }

    @AttributesGetter(ARG_AUTOMATIC_CLOSING_DURATION_SCALE)
    public Duration.Scale getAutomaticClosingIntervalScale() {
        return getScale(ARG_AUTOMATIC_CLOSING_DURATION_SCALE);
    }

    @AttributesSetter(ARG_AUTOMATIC_CLOSING_DURATION_SCALE)
    @EditablePhase(Certification.Phase.Remediation)
    public void setAutomaticClosingIntervalScale(Duration.Scale scale) {
        setAttribute(ARG_AUTOMATIC_CLOSING_DURATION_SCALE, scale != null ? scale.name() : null);
    }

    @AttributesGetter(ARG_AUTOMATIC_CLOSING_ACTION)
    public CertificationAction.Status getAutomaticClosingAction() {
        return (CertificationAction.Status)getAttributes().get(ARG_AUTOMATIC_CLOSING_ACTION);
    }

    @AttributesSetter(ARG_AUTOMATIC_CLOSING_ACTION)
    @EditablePhase(Certification.Phase.Remediation)
    public void setAutomaticClosingAction(String action) {
        setAutomaticClosingAction(action != null ? CertificationAction.Status.valueOf(action) : null);
    }

    public void setAutomaticClosingAction(CertificationAction.Status action) {
        setAttribute(ARG_AUTOMATIC_CLOSING_ACTION, action);
    }

    @AttributesGetter(ARG_AUTOMATIC_CLOSING_COMMENTS)
    public String getAutomaticClosingComments() {
        return (String)getAttribute(ARG_AUTOMATIC_CLOSING_COMMENTS);
    }

    @AttributesSetter(ARG_AUTOMATIC_CLOSING_COMMENTS)
    @EditablePhase(Certification.Phase.Remediation)
    public void setAutomaticClosingComments(String comments) {
        setString(ARG_AUTOMATIC_CLOSING_COMMENTS, comments);
    }

    @AttributesGetter(ARG_AUTOMATIC_CLOSING_SIGNER)
    public String getAutomaticClosingSignerName() {
        return (String)getAttribute(ARG_AUTOMATIC_CLOSING_SIGNER);
    }

    @AttributesSetter(ARG_AUTOMATIC_CLOSING_SIGNER)
    @EditablePhase(Certification.Phase.Remediation)
    public void setAutomaticClosingSignerName(String signer) {
        setAttribute(ARG_AUTOMATIC_CLOSING_SIGNER, signer);
    }

    public Identity getAutomaticClosingSigner(Resolver resolver) throws GeneralException {
        return getAutomaticClosingSignerName() != null ?
                    resolver.getObjectByName(Identity.class, getAutomaticClosingSignerName()) : null;
    }

    public void setAutomaticClosingSigner(Identity signer) {
        setAutomaticClosingSignerName(signer != null ? signer.getName() : null);
    }

    @AttributesGetter(ARG_REMEDIATION_PERIOD_ENABLED)
    public boolean isRemediationPeriodEnabled() {
        return getBoolean(ARG_REMEDIATION_PERIOD_ENABLED, true);
    }

    @AttributesSetter(ARG_REMEDIATION_PERIOD_ENABLED)
    @EditablePhase(Certification.Phase.Challenge)
    public void setRemediationPeriodEnabled(Boolean enabled) {
        setBoolean(ARG_REMEDIATION_PERIOD_ENABLED, enabled);
    }

    @AttributesGetter(ARG_REMEDIATION_PERIOD_DURATION_AMOUNT)
    public Long getRemediationPeriodDurationAmount() {
        return getAttributes().get(ARG_REMEDIATION_PERIOD_DURATION_AMOUNT) != null ?
                getAttributes().getLong(ARG_REMEDIATION_PERIOD_DURATION_AMOUNT) : null;
    }

    @AttributesSetter(ARG_REMEDIATION_PERIOD_DURATION_AMOUNT)
    @EditablePhase(Certification.Phase.Remediation)
    public void setRemediationPeriodDurationAmount(Long amount) {
        setString(ARG_REMEDIATION_PERIOD_DURATION_AMOUNT, amount);
    }

    @AttributesGetter(ARG_REMEDIATION_PERIOD_DURATION_SCALE)
    public Duration.Scale getRemediationPeriodDurationScale() {
        return getScale(ARG_REMEDIATION_PERIOD_DURATION_SCALE);
    }

    @AttributesSetter(ARG_REMEDIATION_PERIOD_DURATION_SCALE)
    @EditablePhase(Certification.Phase.Remediation)
    public void setRemediationPeriodDurationScale(Duration.Scale scale) {
        setAttribute(ARG_REMEDIATION_PERIOD_DURATION_SCALE, scale != null ? scale.name() : null);
    }

    /**************************************************************
     *
     * Rules
     *
     * *************************************************************/

    public String getExclusionRuleName() {
        return getAttributes().getString(ARG_EXCLUSION_RULE_NAME);
    }

    public void setExclusionRuleName(String ruleName) {
        setAttribute(ARG_EXCLUSION_RULE_NAME, ruleName);
    }

    public String getItemCustomizationRuleName(SailPointContext ctx) throws GeneralException {

        String val = getAttributes().getString(ARG_ITEM_CUSTOMIZATION_RULE);
        return val != null ? val :
                ctx.getConfiguration().getString(Configuration.CERTIFICATION_ITEM_CUSTOMIZATION_RULE);
    }

    public String getItemCustomizationRuleName() {
        return getAttributes().getString(ARG_ITEM_CUSTOMIZATION_RULE);
    }

    public void setItemCustomizationRuleName(String ruleName) {
        setAttribute(ARG_ITEM_CUSTOMIZATION_RULE, ruleName);
    }

    public String getEntityCustomizationRuleName(SailPointContext ctx) throws GeneralException {
        String val = getAttributes().getString(ARG_ENTITY_CUSTOMIZATION_RULE);
        return val != null ? val :
                ctx.getConfiguration().getString(Configuration.CERTIFICATION_ENTITY_CUSTOMIZATION_RULE);
    }

    public String getEntityCustomizationRuleName() {
        return getAttributes().getString(ARG_ENTITY_CUSTOMIZATION_RULE);
    }

    public void setEntityCustomizationRuleName(String ruleName) {
        setAttribute(ARG_ENTITY_CUSTOMIZATION_RULE, ruleName);
    }

    public String getEntityCompletionRuleName() {
        return getAttributes().getString(ARG_ENTITY_COMPLETION_RULE);
    }

    public void setEntityCompletionRuleName(String ruleName) {
        setAttribute(ARG_ENTITY_COMPLETION_RULE, ruleName);
    }

    public String getEntityRefreshRuleName() {
        return getAttributes().getString(ARG_ENTITY_REFRESH_RULE);
    }

    public void setEntityRefreshRuleName(String ruleName) {
        setAttribute(ARG_ENTITY_REFRESH_RULE, ruleName);
    }

    @AttributesGetter(ARG_PRE_DELEGATION_RULE_NAME)
    public String getPreDelegationRuleName() {
        return getAttributes().getString(ARG_PRE_DELEGATION_RULE_NAME);
    }

    @AttributesSetter(ARG_PRE_DELEGATION_RULE_NAME)
    public void setPreDelegationRuleName(String ruleName) {
        setAttribute(ARG_PRE_DELEGATION_RULE_NAME, ruleName);
    }

    @AttributesGetter(ARG_SIGN_OFF_APPROVER_RULE_NAME)
    public String getApproverRuleName() {
        return getAttributes().getString(ARG_SIGN_OFF_APPROVER_RULE_NAME);
    }

    @AttributesSetter(ARG_SIGN_OFF_APPROVER_RULE_NAME)
    public void setApproverRuleName(String ruleName) {
        setAttribute(ARG_SIGN_OFF_APPROVER_RULE_NAME, ruleName);
    }

    @AttributesGetter(ARG_ACTIVE_PHASE_ENTER_RULE_NAME)
    public String getActivePhaseEnterRuleName() {
        return getAttributes().getString(ARG_ACTIVE_PHASE_ENTER_RULE_NAME);
    }

    @AttributesSetter(ARG_ACTIVE_PHASE_ENTER_RULE_NAME)
    @EditablePhase(Certification.Phase.Staged)
    public void setActivePhaseEnterRuleName(String activePhaseEnterRuleName) {
        setAttribute(ARG_ACTIVE_PHASE_ENTER_RULE_NAME, activePhaseEnterRuleName);
    }

    @AttributesGetter(ARG_CHALLENGE_PHASE_ENTER_RULE_NAME)
    public String getChallengePhaseEnterRuleName() {
        // Silliness for checking against typo version of CERTIFICATION_CHALLENGE_PHASE_ENTER_RULE attribute
        String ruleName = getAttributes().getString( ARG_CHALLENGE_PHASE_ENTER_RULE_NAME );
        if ( ruleName == null ) {
            ruleName = getAttributes().getString( Configuration.CERTIFICATION_CHALLENGE_PHASE_ENTER_RULE_LEGACY );
        }
        return ruleName;
    }

    @AttributesSetter(ARG_CHALLENGE_PHASE_ENTER_RULE_NAME)
    @EditablePhase(Certification.Phase.Active)
    public void setChallengePhaseEnterRuleName(String challengePhaseEnterRuleName) {
        setAttribute(ARG_CHALLENGE_PHASE_ENTER_RULE_NAME, challengePhaseEnterRuleName);
    }

    @AttributesGetter(ARG_AUTOMATIC_CLOSING_RULE_NAME)
    public String getAutomaticClosingRuleName() {
        return getAttributes().getString(ARG_AUTOMATIC_CLOSING_RULE_NAME);
    }

    @AttributesSetter(ARG_AUTOMATIC_CLOSING_RULE_NAME)
    @EditablePhase(Certification.Phase.Remediation)
    public void setAutomaticClosingRuleName(String automaticClosingRuleName) {
        setAttribute(ARG_AUTOMATIC_CLOSING_RULE_NAME, automaticClosingRuleName);
    }

    public Rule getAutomaticClosingRule(Resolver resolver) throws GeneralException {
        return getAutomaticClosingRuleName() != null ?
                resolver.getObjectByName(Rule.class, getAutomaticClosingRuleName()) : null;
    }

    @AttributesGetter(ARG_REMEDIATION_PHASE_ENTER_RULE_NAME)
    public String getRemediationPhaseEnterRuleName() {
        return getAttributes().getString(ARG_REMEDIATION_PHASE_ENTER_RULE_NAME);
    }

    @EditablePhase(Certification.Phase.Challenge)
    @AttributesSetter(ARG_REMEDIATION_PHASE_ENTER_RULE_NAME)
    public void setRemediationPhaseEnterRuleName(String remediationPhaseEnterRuleName) {
        setAttribute(ARG_REMEDIATION_PHASE_ENTER_RULE_NAME, remediationPhaseEnterRuleName);
    }

    @AttributesGetter(ARG_FINISH_PHASE_ENTER_RULE_NAME)
    public String getEndPhaseEnterRuleName() {
        return getAttributes().getString(ARG_FINISH_PHASE_ENTER_RULE_NAME);
    }

    @AttributesSetter(ARG_FINISH_PHASE_ENTER_RULE_NAME)
    @EditablePhase(Certification.Phase.Remediation)
    public void setEndPhaseEnterRuleName(String val) {
        setAttribute(ARG_FINISH_PHASE_ENTER_RULE_NAME, val);
    }

    /****************************************************************************
     *
     * Notification Config
     *
     *****************************************************************************/

    public NotificationConfig getCertificationRequiredNotificationConfig(SailPointContext context) throws GeneralException {
        return readOrCreateNotificationConfig(context, CERT_REQUIRED_NOTIF_PREFIX, false);
    }
    
    public void setCertificationRequiredNotificationConfig(sailpoint.object.NotificationConfig val) {
        putNotificationConfigInDb(CERT_REQUIRED_NOTIF_PREFIX, val);
    }

    public NotificationConfig getOverdueNotificationConfig(SailPointContext context) throws GeneralException {
        return readOrCreateNotificationConfig(context, OVERDUE_NOTIF_PREFIX, false);
    }
    
    public void setOverdueNotificationConfig(sailpoint.object.NotificationConfig val) {
        putNotificationConfigInDb(OVERDUE_NOTIF_PREFIX, val);
    }

    @AttributesGetter(CERTIFICATION_NOTIF_PREFIX + REMINDERS_AND_ESCALATIONS_KEY)
    public sailpoint.object.NotificationConfig getCertificationNotificationConfig(SailPointContext context) throws GeneralException {
        return getNotificationConfigFromDb(CERTIFICATION_NOTIF_PREFIX);
    }

    @EditablePhase(Certification.Phase.Active)
    @AttributesSetter(CERTIFICATION_NOTIF_PREFIX + REMINDERS_AND_ESCALATIONS_KEY)
    public void setCertificationNotificationConfig(sailpoint.object.NotificationConfig val) {
        putNotificationConfigInDb(CERTIFICATION_NOTIF_PREFIX, val);
    }

    public NotificationConfig getRemediationNotificationConfig(SailPointContext context) throws GeneralException {
        return readOrCreateNotificationConfig(context, REMEDIATION_NOTIF_PREFIX, true);
    }

    @AttributesGetter(REMEDIATION_NOTIF_PREFIX + REMINDERS_AND_ESCALATIONS_KEY)
    public sailpoint.object.NotificationConfig getRemediationNotificationConfigFromDb(SailPointContext context) throws GeneralException {
        return getNotificationConfigFromDb(REMEDIATION_NOTIF_PREFIX);
    }

    @EditablePhase(Certification.Phase.Remediation)
    @AttributesSetter(REMEDIATION_NOTIF_PREFIX + REMINDERS_AND_ESCALATIONS_KEY)
    public void setRemediationNotificationConfig(sailpoint.object.NotificationConfig notifConfig) {
        putNotificationConfigInDb(REMEDIATION_NOTIF_PREFIX, notifConfig);
    }

    private sailpoint.object.NotificationConfig getNotificationConfigFromDb(String argPrefix) {
        return (sailpoint.object.NotificationConfig) getAttributes().get(argPrefix + REMINDERS_AND_ESCALATIONS_KEY);        
    }
    
    private void putNotificationConfigInDb(String argPrefix, sailpoint.object.NotificationConfig notifConfig) {
        getAttributes().put(argPrefix + REMINDERS_AND_ESCALATIONS_KEY, notifConfig);
    }
    

    /****************************************************************************
     *
     * UI Configuration
     *
     * ****************************************************************************/

    public String getCertificationDetailedView() {
        return getAttributes().getString(Configuration.CERTIFICATION_DETAILED_VIEW_FILTER_SET);
    }

    public String getCertificationDetailedView(SailPointContext context) throws GeneralException {
        if (getCertificationDetailedView() != null) {
            return getCertificationDetailedView();
        }
        return context.getConfiguration().getString(Configuration.CERTIFICATION_DETAILED_VIEW_FILTER_SET);
    }

    public void setCertificationDetailedView(String certificationDetailedView) {
        setAttribute(Configuration.CERTIFICATION_DETAILED_VIEW_FILTER_SET, certificationDetailedView);
    }

    public Boolean getCertPageListItems() {
        return getBoolean(Configuration.CERT_PAGE_LIST_ITEMS);
    }

    public boolean getCertPageListItems(SailPointContext context) throws GeneralException {
        if (getCertPageListItems() != null) {
            return getCertPageListItems();
        }
        return context.getConfiguration().getBoolean(Configuration.CERT_PAGE_LIST_ITEMS, false);
    }

    public void setCertPageListItems(Boolean certPageListItems) {
        setBoolean(Configuration.CERT_PAGE_LIST_ITEMS, certPageListItems);
    }

    public Boolean isDisplayEntitlementDescriptions() {
        return getBoolean(ARG_DISPLAY_ENT_DESCS);
    }

    public Boolean isDisplayEntitlementDescriptions(SailPointContext context) throws GeneralException {
        if (isDisplayEntitlementDescriptions() != null) {
            return isDisplayEntitlementDescriptions();
        }

        return context.getConfiguration().getBoolean(Configuration.DISPLAY_ENTITLEMENT_DESC, false);
    }

    public void setDisplayEntitlementDescriptions(Boolean displayEntitlementDescriptions) {
        setBoolean(ARG_DISPLAY_ENT_DESCS, displayEntitlementDescriptions);
    }
    
    public String  getCertificationItemFilterAttributes() { return getString(Configuration.CERTIFICATION_ITEM_FILTER_ATTRIBUTES); }
    
    public String getCertificationItemFilterAttributes(SailPointContext context) throws GeneralException {
        if (getCertificationItemFilterAttributes() != null) {
            return getCertificationItemFilterAttributes();
        }
        
        return context.getConfiguration().getString(Configuration.CERTIFICATION_ITEM_FILTER_ATTRIBUTES);
    }

    @AttributesGetter(ARG_SEND_PRE_DELEGATION_COMPLETION_EMAILS)
    public boolean isSendPreDelegationCompletionEmails() {
        return getBoolean(ARG_SEND_PRE_DELEGATION_COMPLETION_EMAILS, false);
    }

    @AttributesSetter(ARG_SEND_PRE_DELEGATION_COMPLETION_EMAILS)
    public void setSendPreDelegationCompletionEmails(boolean val) {
        setBoolean(ARG_SEND_PRE_DELEGATION_COMPLETION_EMAILS, val);
    }

    @AttributesGetter(ARG_SUPPRESS_EMAIL_WHEN_NOTHING_TO_CERTIFY)
    public boolean isSuppressEmailWhenNothingToCertify() {
        return getBoolean(ARG_SUPPRESS_EMAIL_WHEN_NOTHING_TO_CERTIFY, false);
    }
    
    @AttributesSetter(ARG_SUPPRESS_EMAIL_WHEN_NOTHING_TO_CERTIFY)
    public void setSuppressEmailWhenNothingToCertify( boolean suppressEmailWhenNothingToCertify ) {
        setBoolean(ARG_SUPPRESS_EMAIL_WHEN_NOTHING_TO_CERTIFY, suppressEmailWhenNothingToCertify);
    }

    @AttributesGetter(ARG_AUTO_SIGN_OFF_WHEN_NOTHING_TO_CERTIFY)
    public boolean isAutoSignOffWhenNothingToCertify() {
        return getBoolean(ARG_AUTO_SIGN_OFF_WHEN_NOTHING_TO_CERTIFY, false);
    }

    @AttributesSetter(ARG_AUTO_SIGN_OFF_WHEN_NOTHING_TO_CERTIFY)
    public void setAutoSignOffWhenNothingToCertify( boolean autoSignOffWhenNothingToCertify ) {
        setBoolean(ARG_AUTO_SIGN_OFF_WHEN_NOTHING_TO_CERTIFY, autoSignOffWhenNothingToCertify);
    }
    
    public Boolean isElectronicSignatureRequired() {
    	return getBoolean(ARG_ELECTRONIC_SIGNATURE_REQUIRED);
    }

    @AttributesGetter(ARG_ELECTRONIC_SIGNATURE_REQUIRED)
    public Boolean isElectronicSignatureRequired(SailPointContext context) throws GeneralException {
    	if (isElectronicSignatureRequired() != null) {
    		return getBoolean(ARG_ELECTRONIC_SIGNATURE_REQUIRED);
    	}
    	
    	return StringUtils.isNotBlank(context.getConfiguration().getString(Configuration.REQUIRE_ELECTRONIC_SIGNATURE));
    }
    
    @AttributesSetter(ARG_ELECTRONIC_SIGNATURE_REQUIRED)
    public void setElectronicSignatureRequired(boolean electronicSignatureRequired) {
    	setBoolean(ARG_ELECTRONIC_SIGNATURE_REQUIRED, electronicSignatureRequired);
    }
    
    public String getElectronicSignatureName() {
    	return (String)getAttribute(Certification.ATT_SIGNATURE_TYPE);
    }

    @AttributesGetter(Certification.ATT_SIGNATURE_TYPE)
    public String getElectronicSignatureName(SailPointContext context) throws GeneralException {
        if (getElectronicSignatureName() != null) {
        	return getElectronicSignatureName();
        }
        
        // this is the first time the page loads so get the default
		if (isElectronicSignatureRequired() == null) {
			return context.getConfiguration().getString(Configuration.REQUIRE_ELECTRONIC_SIGNATURE);
		}
		
		return null;
    }

    @AttributesSetter(Certification.ATT_SIGNATURE_TYPE)
    public void setElectronicSignatureName(String electronicSignatureName) {
    	setString(Certification.ATT_SIGNATURE_TYPE, electronicSignatureName);
    }

    public List<ApplicationGroup> getApplicationGroups() {
        return (List<ApplicationGroup>) getAttribute(ARG_APPLICATION_GROUPS);
    }

    public void setApplicationGroups(List<ApplicationGroup> val) {
        setAttribute(ARG_APPLICATION_GROUPS, val);
    }

    /***************************************************************************
     *
     * UTILITY Methods
     *
     * ***************************************************************************/

    public Object getAttribute(String name) {
        if (attributes != null) {
            return attributes.get(name);
        }
        return null;
    }

    private String getString(String name) {
        if (attributes != null && attributes.containsKey(name)) {
            return attributes.getString(name);
        }
        return null;
    }

    private Boolean getBoolean(String name) {
        if (attributes != null && attributes.containsKey(name)) {
            return attributes.getBoolean(name);
        }
        return null;
    }

    public boolean getBoolean(String name, boolean defaultValue) {
        if (attributes != null && attributes.containsKey(name)) {
            return attributes.getBoolean(name);
        }
        return defaultValue;
    }

    private void setBoolean(String name, Boolean val) {
        if (val != null) {
            setAttribute(name, Boolean.toString(val));
        } else if (attributes != null && attributes.containsKey(name)) {
            attributes.remove(name);
        }
    }

    private boolean getBoolean(String name, SailPointContext context, boolean defaultValue) throws GeneralException {
        Boolean val = getBoolean(name);
        if (val != null) {
            return val.booleanValue();
        }
        return context.getConfiguration().getBoolean(name, defaultValue);
    }

    private Duration.Scale getScale(String name) {
        String scale = getAttributes().getString(name);
        return scale != null ? Duration.Scale.valueOf(scale) : null;
    }

    public void setAttribute(String name, Object value) {
        if (value == null && getAttributes().containsKey(name))
            getAttributes().remove(name); // null is same as no value
        else if (value != null)
            getAttributes().put(name, value);
    }

    private void setString(String name, Object value) {
        setAttribute(name, value != null ? String.valueOf(value) : null);
    }

    private void setList(String name, List<String> values) {
        String valStr = null;
        if (values != null && !values.isEmpty())
            valStr = Util.listToCsv(values);
        setAttribute(name, valStr);
    }

    private List<String> readList(String name) {
        String valStr = attributes.getString(name);
        if (valStr != null)
            return Util.csvToList(valStr);

        return null;
    }

    private Integer getInteger(String name) {
        if (attributes != null && attributes.containsKey(name)) {
            return attributes.getInteger(name);
        }
        return null;
    }


    private Duration createDuration(long amount, Duration.Scale scale) {
        return (null != scale) ? new Duration(amount, scale) : null;
    }

    private NotificationConfig readOrCreateNotificationConfig(SailPointContext context, String argPrefix, boolean maxPresentInDefaultEscalation) 
        throws GeneralException{
        
        sailpoint.object.NotificationConfig fromDb = getNotificationConfigFromDb(argPrefix);
        if (fromDb != null) {
            return sailpoint.object.NotificationConfig.createRestNotificationConfig(context, fromDb);
        }
        
        return createDefaultNotificationConfig(context, maxPresentInDefaultEscalation);
    }
    
    private NotificationConfig createDefaultNotificationConfig(SailPointContext context, boolean maxPresentInEscalation) 
        throws GeneralException {
        
        NotificationConfig notifConfig = new NotificationConfig();
        notifConfig.setEnabled(false);
        
        List<NotificationConfig.IConfig> configs = new ArrayList<NotificationConfig.IConfig>();
        notifConfig.setConfigs(configs);

        NotificationConfig.ReminderConfig reminderConfig = sailpoint.object.NotificationConfig.createDefaultRestReminderConfig(context);
        configs.add(reminderConfig);
        
        NotificationConfig.EscalationConfig escalationConfig = sailpoint.object.NotificationConfig.createDefaultRestEscalationConfig(context, maxPresentInEscalation);
        configs.add(escalationConfig);
        
        return notifConfig;
    }

    @AttributesGetter(ARG_SUPPRESS_INITIAL_NOTIFICATION)
    public boolean isSuppressInitialNotification(SailPointContext context) throws GeneralException {
        if (isSuppressInitialNotification() != null)
            return isSuppressInitialNotification().booleanValue();

        return context.getConfiguration().getBoolean(Configuration.SUPPRESS_INITIAL_NOTIFICATION, false);
    }

    public Boolean isSuppressInitialNotification() {
        return getBoolean(ARG_SUPPRESS_INITIAL_NOTIFICATION);
    }

    @AttributesSetter(ARG_SUPPRESS_INITIAL_NOTIFICATION)
    @EditablePhase(Certification.Phase.Staged)
    public void setSuppressInitialNotification(boolean suppress) {
        setBoolean(ARG_SUPPRESS_INITIAL_NOTIFICATION, suppress);
    }

    @AttributesGetter(Configuration.NOTIFY_REMEDIATION)
    public boolean isNotifyRemediation(SailPointContext context) throws GeneralException {
        Boolean notify = isNotifyRemediation();
        return notify != null ?
               notify.booleanValue() :
               context.getConfiguration().getBoolean(Configuration.NOTIFY_REMEDIATION, false);
    }

    public Boolean isNotifyRemediation() {
        return getBoolean(Configuration.NOTIFY_REMEDIATION);
    }

    @AttributesSetter(Configuration.NOTIFY_REMEDIATION)
    @EditablePhase(Certification.Phase.Remediation)
    public void setNotifyRemediation(boolean suppress) {
        setBoolean(Configuration.NOTIFY_REMEDIATION, suppress);
    }

    @AttributesGetter(Configuration.CERTIFICATION_SHOW_RECOMMENDATIONS)
    public boolean getShowRecommendations(SailPointContext context) throws GeneralException {
        if (getShowRecommendations() != null) {
            return getShowRecommendations().booleanValue();
        }

        return context.getConfiguration().getBoolean(Configuration.CERTIFICATION_SHOW_RECOMMENDATIONS, false);
    }

    public Boolean getShowRecommendations() { return getBoolean(Configuration.CERTIFICATION_SHOW_RECOMMENDATIONS, false); }

    @AttributesSetter(Configuration.CERTIFICATION_SHOW_RECOMMENDATIONS)
    @EditablePhase(Certification.Phase.Staged)
    public void setShowRecommendations(boolean showRecs) {
        setBoolean(Configuration.CERTIFICATION_SHOW_RECOMMENDATIONS, showRecs);
    }

    @AttributesGetter(Configuration.CERTIFICATION_AUTO_APPROVE)
    public boolean isAutoApprove(SailPointContext context) throws GeneralException {
        if (isAutoApprove() != null) {
            return isAutoApprove().booleanValue();
        }

        return context.getConfiguration().getBoolean(Configuration.CERTIFICATION_AUTO_APPROVE, false);
    }

    public Boolean isAutoApprove() { return getBoolean(Configuration.CERTIFICATION_AUTO_APPROVE, false); }

    @AttributesSetter(Configuration.CERTIFICATION_AUTO_APPROVE)
    public void setAutoApprove(boolean autoDecisions) {
        setBoolean(Configuration.CERTIFICATION_AUTO_APPROVE, autoDecisions);
    }

    @AttributesGetter(ARG_RECOMMENDATIONS_GENERATED)
    public Boolean getRecommendationsGenerated() { return getBoolean(ARG_RECOMMENDATIONS_GENERATED, false); }

    @AttributesSetter(ARG_RECOMMENDATIONS_GENERATED)
    public void setRecommendationsGenerated(boolean recsGenerated) {
        setBoolean(ARG_RECOMMENDATIONS_GENERATED, recsGenerated);
    }

    @AttributesGetter(Configuration.CERTIFICATION_INCLUDE_CLASSIFICATIONS)
    public boolean isIncludeClassifications(SailPointContext context) throws GeneralException {
        if (isIncludeClassifications() != null) {
            return isIncludeClassifications().booleanValue();
        }

        return context.getConfiguration().getBoolean(Configuration.CERTIFICATION_INCLUDE_CLASSIFICATIONS, false);
    }

    public Boolean isIncludeClassifications() { return getBoolean(Configuration.CERTIFICATION_INCLUDE_CLASSIFICATIONS, false); }

    public boolean shouldIncludeClassifications() {
        final Certification.Type type = this.getType();
        return Certification.Type.Focused.equals(type) ||
               Certification.Type.Manager.equals(type) ||
               Certification.Type.ApplicationOwner.equals(type) ||
               Certification.Type.BusinessRoleMembership.equals(type) ||
               Certification.Type.Group.equals(type) ||
               Certification.Type.Identity.equals(type);

    }

    @AttributesSetter(Configuration.CERTIFICATION_INCLUDE_CLASSIFICATIONS)
    public void setIncludeClassifications(boolean includeClassifications) {
        setBoolean(Configuration.CERTIFICATION_INCLUDE_CLASSIFICATIONS, includeClassifications);
    }

    private EmailTemplate getEmailTemplateByName(SailPointContext context, String templateName)
            throws GeneralException {
        EmailTemplate emailTemplate = null;
        if (templateName != null) {
            emailTemplate = context.getObjectByName(EmailTemplate.class, templateName);
            if (emailTemplate != null) {
                return emailTemplate;
            }
        }
        return emailTemplate;
    }

    public void setEmailTemplateNameFor(String key, String template) {
        setString(key, template);
    }

    public String getEmailTemplateNameFor(String key) {
        return getAttributes().getString(key);
    }

    public EmailTemplate getEmailTemplateFor(SailPointContext context, String key) throws GeneralException {
        String templateName = getAttributes().getString(key);
        EmailTemplate emailTemplate = null;
        if (templateName != null) {
            emailTemplate = getEmailTemplateByName(context, templateName);
        }
        return emailTemplate;
    }

    public boolean isUpdateIdentityEntitlements() {
        return getBoolean(ARG_UPDATE_IDENTITY_ENTITLEMENTS, true);
    }

    public void setUpdateIdentityEntitlements(boolean update) {
        setBoolean(ARG_UPDATE_IDENTITY_ENTITLEMENTS, update);
    }
    
    /**
     * Flag set when creating a certification that will
     * tell the certification engine to persist 
     * AttributeAssignments based on the certification
     * decision. 
     * 
     * @return true when special arguments is defined on the certification
     */
    public boolean isUpdateAttributeAssignments() {
        return getBoolean(ARG_ENABLE_ENTITLEMENT_ASSIGNMENTS, false);
    }

    public void setUpdateAttributeAssignments(boolean update) {
        setBoolean(ARG_ENABLE_ENTITLEMENT_ASSIGNMENTS, update);
    }

    /**
        Configuration to set whether roles that require other roles should be included
        in a Role Membership cert.
    */
    public boolean getIncludeRequiredRoles() {
        return getBoolean(ARG_INCLUDE_REQUIRED_ROLES, false);
    }

    public void setIncludeRequiredRoles(boolean value) {
        setBoolean(ARG_INCLUDE_REQUIRED_ROLES, value);
    }

    /**
     * Gets a list of editable attributes for a given certification phase.
     * @param phase Current phase
     * @return List of attribute names that are allowed to be edited for the phase.
     */
    public static List<String> getEditableAttributesForPhase(Certification.Phase phase) {
        List<String> attributeNames = new ArrayList<>();

        if (phase != null) {
            Method[] methods = CertificationDefinition.class.getMethods();
            for (Method method : methods) {
                EditablePhase editablePhase = method.getAnnotation(EditablePhase.class);
                if (editablePhase != null && (phase.ordinal() <= editablePhase.value().ordinal())) {
                    AttributesSetter setter = method.getAnnotation(AttributesSetter.class);
                    if (setter != null) {
                        attributeNames.add(setter.value());
                    }
                }
            }
        }

        return attributeNames;
    }
}
