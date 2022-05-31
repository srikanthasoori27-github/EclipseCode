/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import javax.faces.application.FacesMessage;
import javax.faces.context.FacesContext;
import javax.faces.model.SelectItem;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.validator.EmailValidator;

import net.sf.jasperreports.engine.export.JRCsvExporterParameter;
import sailpoint.api.MessageAccumulator;
import sailpoint.api.ObjectUtil;
import sailpoint.api.PasswordPolice;
import sailpoint.api.SailPointContext;
import sailpoint.api.passwordConstraints.PasswordConstraintBasic;
import sailpoint.api.passwordConstraints.PasswordConstraintHistory;
import sailpoint.api.passwordConstraints.PasswordConstraintRepeatedCharacters;
import sailpoint.object.Attributes;
import sailpoint.object.AuditEvent;
import sailpoint.object.Certification;
import sailpoint.object.Certification.SelfCertificationAllowedLevel;
import sailpoint.object.CertificationAction;
import sailpoint.object.Configuration;
import sailpoint.object.Configuration.EmailNotify;
import sailpoint.object.Configuration.SmtpConfiguration;
import sailpoint.object.Duration;
import sailpoint.object.EmailTemplate;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.MitigationExpiration;
import sailpoint.object.ProvisioningTransaction.Level;
import sailpoint.object.QueryOptions;
import sailpoint.object.RequestDefinition;
import sailpoint.object.Rule;
import sailpoint.object.Source;
import sailpoint.object.WorkItem;
import sailpoint.object.Workflow;
import sailpoint.recommender.RecommenderUtil;
import sailpoint.reporting.JasperInit;
import sailpoint.server.Auditor;
import sailpoint.server.SMTPEmailNotifier.SmtpEncryptionType;
import sailpoint.tools.GeneralException;
import sailpoint.tools.JsonHelper;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.certification.CertificationScheduleBean;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.util.WebUtil;

public class SystemConfigBean extends BaseObjectBean<Configuration>
  implements MessageAccumulator {

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    // Email template configuration options
    //    private String selectedReminderEmailTemplate;
    //    private String selectedEscalationEmailTemplate;
    private List <SelectItem> emailTemplates;
    private String selectedEscalationRule;

    /*
    Don't cache any of the rule lists, or new rules created by the
    rule editor while working with the cert schedule can't be detected.
    See bug #5901 for details on the rule editor. - DHC
    private List <SelectItem> escalationRules;
    private List <SelectItem> approverRules;
    private List <SelectItem> workItemForwardRules;
    */

    private List <SelectItem> _granules;
    private List <SelectItem> _langItems;
    private List <SelectItem> emailNotifierTypes;

    private List <SelectItem> roleWorkflows;
    private List <SelectItem> identityWorkflows;
    private List <SelectItem> manualCorrelationWorkflows;
    private List <SelectItem> refreshWorkflows;
    private List <SelectItem> assignmentWorkflows;
    private List <SelectItem> activationWorkflows;
    private List <SelectItem> groupWorkflows;
    private List <SelectItem> interceptWorkflows;
    private List <SelectItem> pamWorkflows;

    /**
     * Cached items for the emailNotification selector.
     */
    static SelectItem[] _emailNotifyOptions;

    // Work Item configuration options
    private String defaultWakeUpInterval;
    private String defaultReminderStart;
    private String defaultMaxReminders;
    private String workItemDuration;

    // Certification configuration options
    private String certPageListItems;
    private String detailedViewFilterSet;
    private List<SelectItem> selfCertificationAllowedLevels;
    private SelfCertificationAllowedLevel selectedSelfCertificationAllowedLevel;
    private Identity selfCertificationViolationOwner;
    private boolean isAllowCreateRolesFromCertification;
    private boolean isMitigationEnabled;
    private boolean isMitigationDeprovisionEnabled;
    private boolean allowProvisioningMissingRequirements;
    private boolean requireApprovalComments;
    private boolean requireRemediationComments;
    private boolean requireMitigationComments;
    private int subCertificationPageSize;
    private boolean isCertificationDelegationReviewRequired;
    private boolean notifyRemediation;
    private String isSubordinateCertificationEnabled;
    private boolean flattenManagerCertificationHierarchy;
    private boolean completeCertificationHierarchy;
    private MitigationExpiration.Action mitigationExpirationAction;
    private List<SelectItem> mitigationExpirationActions;
    private Certification.EntitlementGranularity entitlementGranularity;
    private long activePeriodDurationAmount;
    private Duration.Scale activePeriodDurationScale;
    private boolean challengePeriodEnabled;
    private long challengePeriodDurationAmount;
    private Duration.Scale challengePeriodDurationScale;

    private boolean automaticClosingEnabled;
    private long automaticClosingDurationAmount;
    private Duration.Scale automaticClosingDurationScale;
    private String automaticClosingRuleName;
    private List<SelectItem> automaticClosingActions;
    private CertificationAction.Status automaticClosingAction;
    private String automaticClosingComments;
    private Identity automaticClosingSigner;

    private boolean remediationPeriodEnabled;
    private long remediationPeriodDurationAmount;
    private Duration.Scale remediationPeriodDurationScale;

    private boolean exceptionPopupEnabled;
    private long exceptionPeriodAmount;
    private Duration.Scale exceptionPeriodScale;

    private Identity defaultRemediator;

    // Identity Snapshots
    private String identitySnapshotInterval;

    // Identity Attributes
    private String identityMaxExtendedAttributes;

    // Link Attributes
    private String accountMaxExtendedAttributes;

    /** Display Maximum number of items in a select box **/
    private String applicationsUIDisplayLimit;
    private String bundlesUIDisplayLimit;
    private String managersUIDisplayLimit;

    private String temporaryDirectory;

    private String helpEmailAddress;

    private int maxEmailRetries;

    /**
     * True if app accounts which underly a composite account should be
     * excluded from certifications.
     */
    private boolean excludeBaseAppAccounts;

    /**
     * Display entitlement descriptions rather than values.
     */
    private boolean displayEntitlementDescription;

    /**
    * Enables approve account decision button in certifications
    */
    private boolean enableApproveAccount;

    private boolean enableRevokeAccount;
    
    private boolean enableReassignAccount;
    
    private Identity defaultAssignee;

    /**
     * Allow certifier to override default remediation for violations
     */
    private boolean enableOverrideDefaultViolationRemediator;
    
    /**
     * Last active tab, used so we can return to the last tab
     * if there are any validation errors.
     */
    int _activeTab;
    
    private List<SelectItem> workItemArchiveTypes;
    
    private List<String> selectedWorkItemArchiveTypes = new ArrayList<String>();
    
    private String defaultRemediationModifiableOp;

    public static final String ATT_ACTIVE_TAB = "SysconfigTab";

    private static final long MILLIS_PER_DAY = 86400000l;
    private static final long SECONDS_PER_DAY = 86400l;

    // These two values should usually go unused.  The only time they would
    // kick in is if values were missing from the system configuration
    private static final String DEFAULT_REMINDER_START_VALUE = "30";
    private static final String DEFAULT_WAKEUP_INTERVAL_VALUE = "1";
    private SmtpConfig smtpConfig;
    private MailSettingsHelper mailSettingsHelper;

    // syslog
    private boolean enableSyslog;
    private String syslogLevel;
    private int syslogPruneAge;

    // provisioning transaction log
    private boolean enableProvisioningTransactionLog;
    private String provisioningTransactionLogLevel;
    private int provisioningTransactionLogPruneAge;

    private boolean defaultElectronicSignatureEnabled;
    private String defaultElectronicSignatureName;

    // duplicate accounts
    private boolean allowDuplicateAccounts;

    // multiple role assignments
    private boolean enableModelerMultipleRoleAssignments;
    private boolean allowMultipleRoleAssignments;

    private boolean hashIdentitySecrets;
    private int hashingIterations;

    private boolean allowRolePropagation; //allow propagation of role changes
    private boolean forceRoleMergeTemplate; //make template merging the default behavior
    
    private boolean retainAssignedEntitlementsDetected;
    private boolean retainAssignedEntitlementsAssigned;

    // Task Completion email notification recipient List
    private List <String> _nameList;
    private String emailNotify;
    private String emailTemplate;

    // Limit Reassignment
    private boolean limitReassignments;
    private String reassignmentLimit;

    // PAM
    private boolean pamProvisioningEnabled;
    private boolean pamModifyPrivilegedDataEnabled;
    private boolean pamOwnerCanEditContainer;
    private boolean pamCreateContainerEnabled;
    private int pamMaxSelectableIdentities;
    private String pamWorkflowIdentityProvisioning;
    private String pamPrivilegedItemSelectorRule;

    // Attachments
    private boolean attachmentsEnabled;
    private int attachmentsMaxFileSize;
    private static int attachmentsMaxFileSizeLimit = 20; // default to 20MB
    private List<String> selectedAttachmentConfigRules = new ArrayList<String>();


    // Require comments for access request
    private boolean requireAccessRequestCommentsAll;
    private List selectedCommentConfigRules = new ArrayList<String>();

    // Edit Preferences Tab Settings
    private boolean showEditForwarding;
    private boolean showChangePasswordTab;

    // stored as json string
    private String attachmentsAllowedFileTypes;

    private String attachmentsFilenameAllowedSpecialCharacters;

    //keep a copy for auditing PasswordPolicy changes
    private Attributes<String, Object> previous;

    // Recommendations
    private boolean isRecommenderConfigured;
    private boolean showRecommendations;
    private boolean autoApprove;

    //list of password policy attributes for auditing any change
    //based on all fields in passwords.xhtml
    public static String[][] PASSWORD_POLICY_ATTRIBUTE_NAMES = {
        {Configuration.PASSWORD_NUMBER_CHARACTERS, MessageKeys.PSWD_ALLOWABLE_NUMERIC_CHARACTERS},
        {Configuration.PASSWORD_UPPER_CHARACTERS, MessageKeys.PSWD_ALLOWABLE_UPPER_CHARACTERS},
        {Configuration.PASSWORD_LOWER_CHARACTERS, MessageKeys.PSWD_ALLOWABLE_LOWER_CHARACTERS},
        {Configuration.PASSWORD_SPECIAL_CHARACTERS, MessageKeys.PSWD_ALLOWABLE_SPECIAL_CHARACTERS},
        {PasswordConstraintBasic.MIN_LENGTH, MessageKeys.PSWD_MIN_NUMBER_CHARACTERS},
        {PasswordConstraintBasic.MAX_LENGTH, MessageKeys.PSWD_MAX_NUMBER_CHARACTERS},
        {PasswordConstraintBasic.MIN_ALPHA, MessageKeys.PSWD_MIN_NUMBER_LETTERS},
        {PasswordConstraintBasic.MIN_CHARTYPE, MessageKeys.PSWD_CHAR_REQUIREMENT_TYPES},
        {PasswordConstraintBasic.MIN_NUMERIC, MessageKeys.PSWD_MIN_NUMBER_DIGITS},
        {PasswordConstraintBasic.MIN_UPPER, MessageKeys.PSWD_MIN_NUMBER_UPPERCASE},
        {PasswordConstraintBasic.MIN_LOWER, MessageKeys.PSWD_MIN_NUMBER_LOWERCASE},
        {PasswordConstraintBasic.MIN_SPECIAL, MessageKeys.PSWD_MIN_NUMBER_SPECIAL},
        {PasswordConstraintRepeatedCharacters.REPEATED_CHARACTERS, MessageKeys.PSWD_ALLOWABLE_NUMBER_OF_REPEATED_CHARACTERS},
        {PasswordConstraintHistory.HISTORY, MessageKeys.PSWD_HISTORY},
        {PasswordConstraintHistory.TRIVIALITY_CHECK, MessageKeys.PSWD_TRIVIALITY_CHECK},
        {PasswordConstraintHistory.MIN_HISTORY_UNIQUECHARS, MessageKeys.PSWD_MIN_HISTORY_UNIQUE_CHARS},
        {PasswordConstraintHistory.CASESENSITIVITY_CHECK, MessageKeys.PSWD_CASE_SENSITVITY_CHECK},
        {PasswordPolice.EXPIRATION_DAYS, MessageKeys.PSWD_EXPIRATION_DAYS},
        {PasswordPolice.RESET_EXPIRATION_DAYS, MessageKeys.PSWD_RESET_EXPIRATION_DAYS},
        {PasswordPolice.PASSWORD_CHANGE_MIN_DURATION, MessageKeys.PSWD_CHANGE_MIN_DURATION},
        {Configuration.CHECK_PASSWORDS_AGAINST_DICTIONARY, MessageKeys.PSWD_CHECK_PASSWORDS_DICTIONARY},
        {Configuration.CHECK_PASSWORDS_AGAINST_IDENTITY_ATTRIBUTES, MessageKeys.PSWD_CHECK_PASSWORDS_IDENTITY_ATTRIBUTES},
        {Configuration.REQUIRE_OLD_PASSWORD_AT_CHANGE, MessageKeys.PSWD_REQUIRE_CURRENT_PASSWORD}
    };
    
    private static Log log = LogFactory.getLog(SystemConfigBean.class);

    //////////////////////////////////////////////////////////////////////
    //
    // Constructor/Initialization
    //
    //////////////////////////////////////////////////////////////////////
    @SuppressWarnings("rawtypes")
    public SystemConfigBean() throws GeneralException {
        super();
        setScope(Configuration.class);

        Configuration sysConfig =
            getContext().getObjectByName(Configuration.class, Configuration.OBJ_NAME);
        setObject(sysConfig);
        setObjectId(sysConfig.getId());
        
        previous = new Attributes<String, Object>(sysConfig.getAttributes());
        
        Map session = getSessionScope();
        Integer tab = ((Integer)session.get(ATT_ACTIVE_TAB));
        if (tab != null)
            _activeTab = tab.intValue();
        else
            _activeTab = 0;


        //        selectedReminderEmailTemplate = sysConfig.getString(Configuration.DEFAULT_REMINDER_EMAIL_TEMPLATE);
        //        selectedEscalationEmailTemplate = sysConfig.getString(Configuration.DEFAULT_ESCALATION_EMAIL_TEMPLATE);
        emailTemplates = initializeEmailTemplates();

        selectedEscalationRule = sysConfig.getString(Configuration.DEFAULT_ESCALATION_RULE);

        long reminderStartInMillis;

        try {
            reminderStartInMillis = sysConfig.getLong(Configuration.DEFAULT_REMINDER_START);
            defaultReminderStart = String.valueOf(reminderStartInMillis / MILLIS_PER_DAY);
        } catch (NullPointerException npe) {
            // If for some reason the value isn't in the persistent store we'll initialize it ourselves
            defaultReminderStart = DEFAULT_REMINDER_START_VALUE;
        }


        if (sysConfig.containsAttribute(Configuration.DEFAULT_WAKEUP_INTERVAL)) {
            long wakeUpIntervalInMillis = sysConfig.getLong(Configuration.DEFAULT_WAKEUP_INTERVAL);
            defaultWakeUpInterval = String.valueOf(wakeUpIntervalInMillis / MILLIS_PER_DAY);
        } else {
            // If for some reason the value isn't in the persistent store we'll initialize it ourselves
            defaultWakeUpInterval = DEFAULT_WAKEUP_INTERVAL_VALUE;
        }

        defaultMaxReminders = sysConfig.getString(Configuration.DEFAULT_MAX_REMINDERS);
        workItemDuration = sysConfig.getString(Configuration.DEFAULT_WORK_ITEM_DURATION);

        boolean stateCertPageListItems = true;
        if (sysConfig.containsAttribute(Configuration.CERT_PAGE_LIST_ITEMS)) {
            stateCertPageListItems =    sysConfig.getBoolean(Configuration.CERT_PAGE_LIST_ITEMS);
        } else {
            // If for some reason the value isn't in the persistent store we'll initialize it ourselves
            stateCertPageListItems = true;
        }
        certPageListItems = String.valueOf(stateCertPageListItems);

        boolean stateDetailedViewFilterSet = false;
        if (sysConfig.containsAttribute(Configuration.CERTIFICATION_DETAILED_VIEW_FILTER_SET)) {
            stateDetailedViewFilterSet =    sysConfig.getBoolean(Configuration.CERTIFICATION_DETAILED_VIEW_FILTER_SET);
        } else {
            // If for some reason the value isn't in the persistent store we'll initialize it ourselves
            stateDetailedViewFilterSet = false;
        }
        detailedViewFilterSet = String.valueOf(stateDetailedViewFilterSet);

        if (sysConfig.containsAttribute(Configuration.ALLOW_CREATE_ROLES_FROM_CERTIFICATION)) {
            isAllowCreateRolesFromCertification = sysConfig.getBoolean(Configuration.ALLOW_CREATE_ROLES_FROM_CERTIFICATION);
        } else {
            // If for some reason the value isn't in the persistent store we'll initialize it ourselves
            isAllowCreateRolesFromCertification = true;
        }

        this.selfCertificationAllowedLevels = initializeSelfCertificationAllowedLevels();
        if (sysConfig.containsAttribute(Configuration.ALLOW_SELF_CERTIFICATION)) {
            this.selectedSelfCertificationAllowedLevel = SelfCertificationAllowedLevel.valueOf(sysConfig.getString(Configuration.ALLOW_SELF_CERTIFICATION));
        } else {
            // If for some reason the value isn't in the persistent store we'll initialize it ourselves
            this.selectedSelfCertificationAllowedLevel = SelfCertificationAllowedLevel.CertificationAdministrator;
        }

        String selfCertificationViolationOwnerName = Util.getString(sysConfig.getString(Configuration.SELF_CERTIFICATION_VIOLATION_OWNER));
        if (null != selfCertificationViolationOwnerName) {
            this.selfCertificationViolationOwner = getContext().getObjectByName(Identity.class, selfCertificationViolationOwnerName );
        }


        this.isMitigationEnabled = sysConfig.getBoolean(Configuration.CERTIFICATION_MITIGATION_ENABLED, false);
        this.isMitigationDeprovisionEnabled = sysConfig.getBoolean(Configuration.CERTIFICATION_MITIGATION_DEPROVISION_ENABLED, false);
        this.allowProvisioningMissingRequirements =
                sysConfig.getBoolean(Configuration.ALLOW_PROV_MISSING_REQUIREMENTS, false);

        this.requireApprovalComments =
                sysConfig.getBoolean(Configuration.REQUIRE_APPROVAL_COMMENTS, false);

        this.requireMitigationComments =
            sysConfig.getBoolean(Configuration.REQUIRE_MITIGATION_COMMENTS, false);

        this.requireRemediationComments =
                sysConfig.getBoolean(Configuration.REQUIRE_REMEDIATION_COMMENTS, false);

        this.excludeBaseAppAccounts =
                sysConfig.getBoolean(Configuration.EXCLUDE_BASE_APP_ACCOUNTS, false);

        this.displayEntitlementDescription =
                sysConfig.getBoolean(Configuration.DISPLAY_ENTITLEMENT_DESC, false);

        this.enableApproveAccount =
                        sysConfig.getBoolean(Configuration.ENABLE_ACCOUNT_APPROVE_ACTION, false);

        this.enableRevokeAccount =
                        sysConfig.getBoolean(Configuration.ENABLE_ACCOUNT_REVOKE_ACTION, false);
        
        this.enableReassignAccount =
                        sysConfig.getBoolean(Configuration.ENABLE_ACCOUNT_REASSIGN_ACTION, false);
        
        String assigneeName = Util.getString(sysConfig.getString(Configuration.DEFAULT_ASSIGNEE));
        if (null != assigneeName) {
            this.defaultAssignee = getContext().getObjectByName(Identity.class, assigneeName);
        }

         this.enableOverrideDefaultViolationRemediator =
                 sysConfig.getBoolean(Configuration.ENABLE_OVERRIDE_VIOLATION_DEFAULT_REMEDIATOR, false);

       this.subCertificationPageSize = sysConfig.getAttributes().getInt(Configuration.CERTIFICATION_SUBCERT_PAGE_SIZE, 15);

        if (sysConfig.containsAttribute(Configuration.CERTIFICATION_DELEGATION_REVIEW)) {
            isCertificationDelegationReviewRequired = sysConfig.getBoolean(Configuration.CERTIFICATION_DELEGATION_REVIEW);
        } else {
            // If for some reason the value isn't in the persistent store we'll initialize it ourselves
            isCertificationDelegationReviewRequired = false;
        }

        this.notifyRemediation = sysConfig.getBoolean(Configuration.NOTIFY_REMEDIATION, false);

        if (sysConfig.containsAttribute(Configuration.SUBORDINATE_CERTIFICATION_ENABLED)) {
            isSubordinateCertificationEnabled = sysConfig.getString(Configuration.SUBORDINATE_CERTIFICATION_ENABLED);
        } else {
            // If for some reason the value isn't in the persistent store we'll initialize it ourselves
            isSubordinateCertificationEnabled = "true";
        }

        if (sysConfig.containsAttribute(Configuration.FLATTEN_CERTIFICATION_MANAGER_HIERARCHY)) {
            flattenManagerCertificationHierarchy = sysConfig.getBoolean(Configuration.FLATTEN_CERTIFICATION_MANAGER_HIERARCHY);
        } else {
            // If for some reason the value isn't in the persistent store we'll initialize it ourselves
            flattenManagerCertificationHierarchy = false;
        }

        if (sysConfig.containsAttribute(Configuration.COMPLETE_CERTIFICATION_HIERARCHY)) {
            completeCertificationHierarchy = sysConfig.getBoolean(Configuration.COMPLETE_CERTIFICATION_HIERARCHY);
        } else {
            // If for some reason the value isn't in the persistent store we'll initialize it ourselves
            completeCertificationHierarchy = false;
        }

        mitigationExpirationActions = initializeMitigationExpirationActions();
        if (sysConfig.containsAttribute(Configuration.MITIGATION_EXPIRATION_ACTION)) {
            mitigationExpirationAction = MitigationExpiration.Action.valueOf(sysConfig.getString(Configuration.MITIGATION_EXPIRATION_ACTION));
        } else {
            // If for some reason the value isn't in the persistent store we'll initialize it ourselves
            mitigationExpirationAction = MitigationExpiration.Action.NOTHING;
        }

        String entitlementGranStr = sysConfig.getString(Configuration.ADDITIONAL_ENTITLEMENT_GRANULARITY);
        if (null != entitlementGranStr) {
            this.entitlementGranularity = Certification.EntitlementGranularity.valueOf(entitlementGranStr);
        }
        else {
            this.entitlementGranularity = Certification.EntitlementGranularity.Application;
        }

        automaticClosingActions = initializeAutomaticClosingActions();
        Duration duration = (Duration) sysConfig.get(Configuration.ACTIVE_PERIOD_DURATION);
        if (null != duration) {
            this.activePeriodDurationAmount = duration.getAmount();
            this.activePeriodDurationScale = duration.getScale();
        }

        this.challengePeriodEnabled = sysConfig.getBoolean(Configuration.CHALLENGE_PERIOD_ENABLED);
        duration = (Duration) sysConfig.get(Configuration.CHALLENGE_PERIOD_DURATION);
        if (null != duration) {
            this.challengePeriodDurationAmount = duration.getAmount();
            this.challengePeriodDurationScale = duration.getScale();
        }

        this.remediationPeriodEnabled = sysConfig.getBoolean(Configuration.REMEDIATION_PERIOD_ENABLED);
        duration = (Duration) sysConfig.get(Configuration.REMEDIATION_PERIOD_DURATION);
        if (null != duration) {
            this.remediationPeriodDurationAmount = duration.getAmount();
            this.remediationPeriodDurationScale = duration.getScale();
        }

        this.automaticClosingEnabled = sysConfig.getBoolean(Configuration.AUTOMATIC_CLOSING_ENABLED);
        duration = (Duration) sysConfig.get(Configuration.AUTOMATIC_CLOSING_DURATION);
        if (null != duration) {
            this.automaticClosingDurationAmount = duration.getAmount();
            this.automaticClosingDurationScale = duration.getScale();
        }
        this.automaticClosingRuleName = sysConfig.getString(Configuration.AUTOMATIC_CLOSING_RULE_NAME);
        this.automaticClosingAction = (CertificationAction.Status)sysConfig.get(Configuration.AUTOMATIC_CLOSING_ACTION);
        this.automaticClosingComments = sysConfig.getString(Configuration.AUTOMATIC_CLOSING_COMMENTS);
        String autoCloserName = sysConfig.getString(Configuration.AUTOMATIC_CLOSING_SIGNER);
        if (null != autoCloserName) {
            this.automaticClosingSigner = getContext().getObjectByName(Identity.class, autoCloserName);
        }

        this.exceptionPopupEnabled = sysConfig.getBoolean(Configuration.CERTIFICATION_MITIGATION_POPUP_ENABLED);
        duration = (Duration) sysConfig.get(Configuration.CERTIFICATION_MITIGATION_DURATION);
        if (null != duration) {
            this.exceptionPeriodAmount = duration.getAmount();
            this.exceptionPeriodScale = duration.getScale();
        }

        String remediatorName = Util.getString(sysConfig.getString(Configuration.DEFAULT_REMEDIATOR));
        if (null != remediatorName) {
            this.defaultRemediator = getContext().getObjectByName(Identity.class, remediatorName);
        }

        try {
            if (sysConfig.containsAttribute(Configuration.IDENTITY_SNAPSHOT_INTERVAL)) {
                String identitySnapshotIntervalInSeconds = sysConfig.getString(Configuration.IDENTITY_SNAPSHOT_INTERVAL);
                identitySnapshotInterval = String.valueOf(Long.parseLong(identitySnapshotIntervalInSeconds) / SECONDS_PER_DAY);
            } else {
                identitySnapshotInterval = "30";
            }
        } catch (NumberFormatException e) {
            // If for some reason the value is wrong in the persistent store we'll initialize it ourselves
            identitySnapshotInterval = "30";
        }

        try {
            if (sysConfig.containsAttribute(Configuration.IDENTITY_MAX_SEARCHABLE_ATTRIBUTES)) {
                identityMaxExtendedAttributes = sysConfig.getString(Configuration.IDENTITY_MAX_SEARCHABLE_ATTRIBUTES);
                Integer.parseInt(identityMaxExtendedAttributes);
            } else {
                identityMaxExtendedAttributes = "4";
            }
        } catch (NumberFormatException e) {
            // If for some reason the value is wrong in the persistent store we'll initialize it ourselves
            identityMaxExtendedAttributes = "4";
        }

        try {
            if (sysConfig.containsAttribute(Configuration.LINK_MAX_SEARCHABLE_ATTRIBUTES)) {
                accountMaxExtendedAttributes = sysConfig.getString(Configuration.LINK_MAX_SEARCHABLE_ATTRIBUTES);
                Integer.parseInt(accountMaxExtendedAttributes);
            } else {
                accountMaxExtendedAttributes = "5";
            }
        } catch (NumberFormatException e) {
            // If for some reason the value is wrong in the persistent store we'll initialize it ourselves
            accountMaxExtendedAttributes = "5";
        }

        bundlesUIDisplayLimit = sysConfig.getString(Configuration.BUNDLES_UI_DISPLAY_LIMIT);
        temporaryDirectory = sysConfig.getString(Configuration.TEMP_DIR);
        helpEmailAddress = sysConfig.getString(Configuration.HELP_EMAIL_ADDRESS);

        String defName = sysConfig.getString(Configuration.EMAIL_REQUEST_DEFINITION);
        RequestDefinition emailRequest =
            getContext().getObjectByName(RequestDefinition.class, defName);
        this.maxEmailRetries = emailRequest.getRetryMax();
        
        if (sysConfig.containsAttribute(Configuration.WORK_ITEM_ARCHIVE_TYPES)) {
            String value = sysConfig.getString(Configuration.WORK_ITEM_ARCHIVE_TYPES);
            if (value != null) {
                selectedWorkItemArchiveTypes = Util.csvToList(value);
            }
        }

        if (sysConfig.containsAttribute(Configuration.ATTACHMENT_CONFIG_RULES)) {
            selectedAttachmentConfigRules = sysConfig.getList(Configuration.ATTACHMENT_CONFIG_RULES);
        }

        if (sysConfig.containsAttribute(Configuration.COMMENT_CONFIG_RULES)) {
            selectedCommentConfigRules = sysConfig.getList(Configuration.COMMENT_CONFIG_RULES);
        }

        defaultRemediationModifiableOp = sysConfig.getString(Configuration.DEFAULT_REMEDIATION_MODIFIABLE_OP);

        enableSyslog = sysConfig.getBoolean(Configuration.ENABLE_SYSLOG, true);
        syslogLevel = sysConfig.getString(Configuration.SYSLOG_LEVEL);
        syslogPruneAge = sysConfig.getInt(Configuration.SYSLOG_PRUNE_AGE);

        enableProvisioningTransactionLog = sysConfig.getBoolean(Configuration.ENABLE_PROVISIONING_TRANSACTION_LOG, false);
        provisioningTransactionLogLevel = sysConfig.getString(Configuration.PROVISIONING_TRANSACTION_LOG_LEVEL);
        provisioningTransactionLogPruneAge = sysConfig.getInt(Configuration.PROVISIONING_TRANSACTION_LOG_PRUNE_AGE);


        defaultElectronicSignatureName = sysConfig.getString(Configuration.REQUIRE_ELECTRONIC_SIGNATURE);
        
        smtpConfig = new SmtpConfig(getContext(), sysConfig);

        mailSettingsHelper = new MailSettingsHelper(sysConfig);

        allowDuplicateAccounts = sysConfig.getBoolean(Configuration.ALLOW_DUPLICATE_ACCOUNTS, false);

        enableModelerMultipleRoleAssignments = sysConfig.getBoolean(Configuration.ENABLE_MODELER_MULTIPLE_ASSIGNMENTS, false);
        allowMultipleRoleAssignments = sysConfig.getBoolean(Configuration.ALLOW_MULTIPLE_ROLE_ASSIGNMENTS, false);

        hashIdentitySecrets = sysConfig.getBoolean(Configuration.HASH_IDENTITY_SECRETS, false);
        hashingIterations = sysConfig.getInt(Configuration.HASHING_ITERATIONS);

        allowRolePropagation = sysConfig.getBoolean(Configuration.ALLOW_ROLE_PROPAGATION, false);
        forceRoleMergeTemplate = sysConfig.getBoolean(Configuration.FORCE_ROLE_MERGE_TEMPLATE, false);
        
        retainAssignedEntitlementsDetected = sysConfig.getBoolean(Configuration.RETAIN_ASSIGNED_ENTITLEMENTS_DETECTED_ROLE, false);
        retainAssignedEntitlementsAssigned = sysConfig.getBoolean(Configuration.RETAIN_ASSIGNED_ENTITLEMENTS_ASSIGNED_ROLE, false);

        String tempTemplate = sysConfig.getString(Configuration.TASK_COMPLETION_EMAIL_TEMPLATE);
        if (tempTemplate == null) {
            // Assign default template
            // jsl - don't really have to do this as long as the rule handles it
            emailTemplate = Configuration.DEFAULT_TASK_COMPLETION_EMAIL_TEMPLATE;
        }
        else
        {
            emailTemplate = tempTemplate;
        }

        limitReassignments = sysConfig.getBoolean(Configuration.CERTIFICATION_LIMIT_REASSIGNMENTS, false);
        reassignmentLimit = sysConfig.getString(Configuration.CERTIFICATION_REASSIGNMENT_LIMIT);

        String tempNotify = sysConfig.getString(Configuration.TASK_COMPLETION_EMAIL_NOTIFY);
        if (tempNotify == null) {
            // Assign default option - disabled
            emailNotify = Configuration.EmailNotify.Disabled.toString();
        }
        else
        {
            emailNotify = tempNotify;
        }
        _nameList = (List <String>) sysConfig.getList(Configuration.TASK_COMPLETION_RECIPIENTS);

        pamProvisioningEnabled = sysConfig.getBoolean(Configuration.PAM_PROVISIONING_ENABLED, true);
        pamModifyPrivilegedDataEnabled = sysConfig.getBoolean(Configuration.PAM_MODIFY_PRIVILEGED_DATA_ENABLED, false);
        pamOwnerCanEditContainer = sysConfig.getBoolean(Configuration.PAM_OWNER_CAN_EDIT_CONTAINER, true);
        pamCreateContainerEnabled = sysConfig.getBoolean(Configuration.PAM_CREATE_CONTAINER_ENABLED, true);
        pamMaxSelectableIdentities = sysConfig.getInt(Configuration.PAM_MAX_SELECTABLE_IDENTITIES);
        pamWorkflowIdentityProvisioning = sysConfig.getString(Configuration.PAM_WORKFLOW_IDENTITY_PROVISIONING);
        pamPrivilegedItemSelectorRule = sysConfig.getString(Configuration.PAM_PRIVILEGED_ITEM_SELECTOR_RULE);

        // Attachment Settings
        attachmentsEnabled = sysConfig.getBoolean(Configuration.ATTACHMENTS_ENABLED, false);
        attachmentsMaxFileSize = sysConfig.getInt(Configuration.ATTACHMENTS_MAX_FILE_SIZE);

        // Require comments settings
        requireAccessRequestCommentsAll = sysConfig.getBoolean(Configuration.REQUIRE_ACCESS_REQUEST_COMMENTS_ALL, false);

        // Edit Preferences Settings - values default to true
        showEditForwarding = sysConfig.getBoolean(Configuration.SHOW_EDIT_FORWARDING, true);
        showChangePasswordTab = sysConfig.getBoolean(Configuration.SHOW_CHANGE_PASSWORD_TAB, true);

        // check to see if there is customized max limit and use that
        if (sysConfig.getInt(Configuration.ATTACHMENTS_MAX_FILE_SIZE_LIMIT) > 0) {
            attachmentsMaxFileSizeLimit = sysConfig.getInt(Configuration.ATTACHMENTS_MAX_FILE_SIZE_LIMIT);
        }

        attachmentsAllowedFileTypes = sysConfig.getString(Configuration.ATTACHMENTS_ALLOWED_FILE_TYPES);
        attachmentsFilenameAllowedSpecialCharacters = sysConfig.getString(Configuration.ATTACHMENTS_FILENAME_ALLOWED_SPECIAL_CHARACTERS);

        this.isRecommenderConfigured = RecommenderUtil.isRecommenderConfigured(getContext());
        this.showRecommendations = sysConfig.getBoolean(Configuration.CERTIFICATION_SHOW_RECOMMENDATIONS, false);
        this.autoApprove = sysConfig.getBoolean(Configuration.CERTIFICATION_AUTO_APPROVE, false);
    }

    List <SelectItem> initializeEmailTemplates() throws GeneralException {
        final List <SelectItem> templateNames = new ArrayList<SelectItem>();

        // jsl - if there is no selection, show an obvious header, otherwise
        // it looks like the first one is selected
        templateNames.add(new SelectItem("", getMessage(MessageKeys.SYS_CONFIG_SELECT_TEMPLATE)));

        QueryOptions qo = new QueryOptions();
        qo.addOrdering("name", true);
        
        List <EmailTemplate> templates = getContext().getObjects(EmailTemplate.class, qo);

        for (EmailTemplate template : templates) {
            templateNames.add(new SelectItem(template.getName()));
        }

        if (templateNames.isEmpty()) {
            templateNames.add(new SelectItem(""));
        }

        return templateNames;
    }
    
    List <SelectItem> initializeMitigationExpirationActions()  {
        final List <SelectItem> actions = new ArrayList<SelectItem>();

        for (MitigationExpiration.Action action : MitigationExpiration.Action.values()) {
            actions.add(new SelectItem(action, getMessage(action.getMessageKey())));
        }

        return actions;
    }

    List <SelectItem> initializeAutomaticClosingActions()  {
        return (new CertificationScheduleBean(true)).getAutomaticClosingActions();
    }

    List <SelectItem> initializeSelfCertificationAllowedLevels()  {
        final List <SelectItem> levels = new ArrayList<SelectItem>();

        for (SelfCertificationAllowedLevel level : SelfCertificationAllowedLevel.values()) {
            levels.add(new SelectItem(level, getMessage(level.getMessageKey())));
        }

        return levels;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Properties
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * @return the _activeTab
     */
    public int getActiveTab() {
        return _activeTab;
    }

    /**
     * @param tab the _activeTab to set
     */
    public void setActiveTab(int tab) {
        _activeTab = tab;
    }

    public List <SelectItem> getGranules() throws GeneralException {

        if (_granules == null) {
            _granules = new ArrayList<SelectItem>();
            _granules.add(new SelectItem(Configuration.GRANULE_NONE, getMessage(MessageKeys.NONE)));
            _granules.add(new SelectItem(Configuration.GRANULE_INFINITE, getMessage(MessageKeys.INFINITE)));
            _granules.add(new SelectItem(Configuration.GRANULE_SECOND, getMessage(MessageKeys.SECOND)));
            _granules.add(new SelectItem(Configuration.GRANULE_MINUTE, getMessage(MessageKeys.MINUTE)));
            _granules.add(new SelectItem(Configuration.GRANULE_HOUR, getMessage(MessageKeys.HOUR)));
            _granules.add(new SelectItem(Configuration.GRANULE_DAY, getMessage(MessageKeys.DAY)));
            _granules.add(new SelectItem(Configuration.GRANULE_WEEK, getMessage(MessageKeys.WEEK)));
            _granules.add(new SelectItem(Configuration.GRANULE_MONTH, getMessage(MessageKeys.MONTH)));
        }

        return _granules;
    }

    public String getDefaultMaxReminders() {
        return defaultMaxReminders;
    }

    public void setDefaultMaxReminders(String defaultMaxReminders) {
        this.defaultMaxReminders = defaultMaxReminders;
    }

    /**
     * @return The time at which e-mail reminders should begin to get
     * sent.  This value is specified as the number of days before a work
     * item expires
     */
    public String getDefaultReminderStart() {
        return defaultReminderStart;
    }

    /**
     * @param defaultReminderStart The time at which e-mail reminders should
     * begin to get sent.  This value is specified as the number of days
     * before a work item expires
     */
    public void setDefaultReminderStart(String defaultReminderStart) {
        this.defaultReminderStart = defaultReminderStart;
    }

    public String getDefaultWakeUpInterval() {
        return defaultWakeUpInterval;
    }

    public void setDefaultWakeUpInterval(String defaultWakeUpInterval) {
        this.defaultWakeUpInterval = defaultWakeUpInterval;
    }

    public String getWorkItemDuration() {
        return workItemDuration;
    }

    public void setWorkItemDuration(String defaultWorkItemDuration) {
        this.workItemDuration = defaultWorkItemDuration;
    }

    //public String getSelectedEscalationEmailTemplate() {
    //return selectedEscalationEmailTemplate;
    //}

    //public void setSelectedEscalationEmailTemplate(
    //String selectedEscalationEmailTemplate) {
    //this.selectedEscalationEmailTemplate = selectedEscalationEmailTemplate;
    //}

    //public String getSelectedReminderEmailTemplate() {
    //return selectedReminderEmailTemplate;
    //}

    //public void setSelectedReminderEmailTemplate(
    //String selectedReminderEmailTemplate) {
    //this.selectedReminderEmailTemplate = selectedReminderEmailTemplate;
    //}

    public List<SelectItem> getEmailTemplates() {
        return emailTemplates;
    }

    public String getIdentitySnapshotInterval() {
        return identitySnapshotInterval;
    }

    public String getIdentityMaxExtendedAttributes() {
        return identityMaxExtendedAttributes;
    }

    public void setIdentityMaxExtendedAttributes(
                    String identityMaxExtendedAttributes) {
        this.identityMaxExtendedAttributes = identityMaxExtendedAttributes;
    }

    public String getAccountMaxExtendedAttributes() {
        return accountMaxExtendedAttributes;
    }

    public void setAccountMaxExtendedAttributes( String accountMaxExtendedAttributes) {
        this.accountMaxExtendedAttributes = accountMaxExtendedAttributes;
    }


    public void setIdentitySnapshotInterval(String identitySnapshotInterval) {
        this.identitySnapshotInterval = identitySnapshotInterval;
    }

    public String getCertPageListItems() {
        return this.certPageListItems;
    }

    public void setCertPageListItems(String state) {
        this.certPageListItems = state;
    }

    public String getDetailedViewFilterSet() throws GeneralException {
        return this.detailedViewFilterSet;
    }

    public void setDetailedViewFilterSet(String state) throws GeneralException {
        this.detailedViewFilterSet = state;
    }

    public boolean isAllowCreateRolesFromCertification() {
        return isAllowCreateRolesFromCertification;
    }

    public void setAllowCreateRolesFromCertification(boolean b) {
        this.isAllowCreateRolesFromCertification = b;
    }

    public SelfCertificationAllowedLevel getSelfCertificationAllowedLevel() {
        return this.selectedSelfCertificationAllowedLevel;
    }

    public void setSelfCertificationAllowedLevel(SelfCertificationAllowedLevel allowedLevel) {
        this.selectedSelfCertificationAllowedLevel = allowedLevel;
    }
    
    public List<SelectItem> getSelfCertificationAllowedLevels() {
        return this.selfCertificationAllowedLevels;
    }

    public Identity getSelfCertificationViolationOwner() {
        return this.selfCertificationViolationOwner;
    }

    public void setSelfCertificationViolationOwner(Identity owner) {
        this.selfCertificationViolationOwner = owner;
    }


    public boolean isMitigationEnabled() {
        return isMitigationEnabled;
    }

    public void setMitigationEnabled(boolean isMitigationEnabled) {
        this.isMitigationEnabled = isMitigationEnabled;
    }

    public boolean isMitigationDeprovisionEnabled() {
        return isMitigationDeprovisionEnabled;
    }

    public void setMitigationDeprovisionEnabled(boolean isMitigationDeprovisionEnabled) {
        this.isMitigationDeprovisionEnabled = isMitigationDeprovisionEnabled;
    }

    public boolean isAllowProvisioningMissingRequirements() {
        return allowProvisioningMissingRequirements;
    }

    public void setAllowProvisioningMissingRequirements(boolean allowProvisioningMissingRequirements) {
        this.allowProvisioningMissingRequirements = allowProvisioningMissingRequirements;
    }

    public boolean isRequireApprovalComments() {
        return requireApprovalComments;
    }

    public void setRequireApprovalComments(boolean requireApprovalComments) {
        this.requireApprovalComments = requireApprovalComments;
    }

    public boolean isRequireMitigationComments() {
        return requireMitigationComments;
    }

    public void setRequireMitigationComments (boolean requireMitigationComments) {
        this.requireMitigationComments = requireMitigationComments;
    }

    public boolean isRequireRemediationComments() {
        return requireRemediationComments;
    }

    public void setRequireRemediationComments(boolean requireRemediationComments) {
        this.requireRemediationComments = requireRemediationComments;
    }

    public int getSubCertificationPageSize() {
        return this.subCertificationPageSize;
    }

    public void setSubCertificationPageSize(int size) {
        this.subCertificationPageSize = size;
    }

    public boolean isCertificationDelegationReviewRequired() {
        return isCertificationDelegationReviewRequired;
    }

    public void setCertificationDelegationReviewRequired(boolean isCertificationDelegationReviewRequired) {
        this.isCertificationDelegationReviewRequired = isCertificationDelegationReviewRequired;
    }

    public boolean isNotifyRemediation() {
        return this.notifyRemediation;
    }

    public void setNotifyRemediation(boolean notifyRemediation) {
        this.notifyRemediation = notifyRemediation;
    }

    public String getIsSubordinateCertificationEnabled() {
        return isSubordinateCertificationEnabled;
    }

    public void setIsSubordinateCertificationEnabled(String isSubordinateCertificationEnabled) {
        this.isSubordinateCertificationEnabled = isSubordinateCertificationEnabled;
    }

    public boolean isCompleteCertificationHierarchy() {
        return completeCertificationHierarchy;
    }

    public void setCompleteCertificationHierarchy(boolean complete) {
        this.completeCertificationHierarchy = complete;
    }

    public boolean isFlattenManagerCertificationHierarchy() {
        return flattenManagerCertificationHierarchy;
    }

    public void setFlattenManagerCertificationHierarchy(boolean flatten) {
        this.flattenManagerCertificationHierarchy = flatten;
    }

    public MitigationExpiration.Action getMitigationExpirationAction() {
        return mitigationExpirationAction;
    }

    public void setMitigationExpirationAction(MitigationExpiration.Action mitigationExpirationAction) {
        this.mitigationExpirationAction = mitigationExpirationAction;
    }

    public List<SelectItem> getMitigationExpirationActions() {
        return mitigationExpirationActions;
    }
    
    public boolean isDefaultElectronicSignatureEnabled() {
		return StringUtils.isNotBlank(getDefaultElectronicSignatureName());
	}

	public void setDefaultElectronicSignatureEnabled(
			boolean defaultElectronicSignatureEnabled) {
		this.defaultElectronicSignatureEnabled = defaultElectronicSignatureEnabled;
	}
	
	public String getDefaultElectronicSignatureName() {
		return defaultElectronicSignatureName;
	}
	public void setDefaultElectronicSignatureName(String defaultElectronicSignatureName) {
		this.defaultElectronicSignatureName = defaultElectronicSignatureName;
	}

	public List<SelectItem> getAutomaticClosingActions() {
        return automaticClosingActions;
    }

    public Certification.EntitlementGranularity getEntitlementGranularity() {
        return this.entitlementGranularity;
    }

    public void setEntitlementGranularity(Certification.EntitlementGranularity eg) {
        this.entitlementGranularity = eg;
    }

    public List<SelectItem> getEntitlementGranularities() throws GeneralException {
        return (new CertificationScheduleBean(true)).getEntitlementGranularities();
    }

    public long getActivePeriodDurationAmount() {
        return activePeriodDurationAmount;
    }

    public void setActivePeriodDurationAmount(long amount) {
        this.activePeriodDurationAmount = amount;
    }

    public Duration.Scale getActivePeriodDurationScale() {
        return activePeriodDurationScale;
    }

    public void setActivePeriodDurationScale(Duration.Scale scale) {
        this.activePeriodDurationScale = scale;
    }

    public boolean isChallengePeriodEnabled() {
        return challengePeriodEnabled;
    }

    public void setChallengePeriodEnabled(boolean challengePeriodEnabled) {
        this.challengePeriodEnabled = challengePeriodEnabled;
    }

    public long getChallengePeriodDurationAmount() {
        return challengePeriodDurationAmount;
    }

    public void setChallengePeriodDurationAmount(long amount) {
        this.challengePeriodDurationAmount = amount;
    }

    public Duration.Scale getChallengePeriodDurationScale() {
        return challengePeriodDurationScale;
    }

    public void setChallengePeriodDurationScale(Duration.Scale scale) {
        this.challengePeriodDurationScale = scale;
    }

    public boolean isRemediationPeriodEnabled() {
        return remediationPeriodEnabled;
    }

    public void setRemediationPeriodEnabled(boolean remediationPeriodEnabled) {
        this.remediationPeriodEnabled = remediationPeriodEnabled;
    }

    public long getRemediationPeriodDurationAmount() {
        return remediationPeriodDurationAmount;
    }

    public void setRemediationPeriodDurationAmount(long amount) {
        this.remediationPeriodDurationAmount = amount;
    }

    public Duration.Scale getRemediationPeriodDurationScale() {
        return remediationPeriodDurationScale;
    }

    public void setRemediationPeriodDurationScale(Duration.Scale scale) {
        this.remediationPeriodDurationScale = scale;
    }

    public boolean isAutomaticClosingEnabled() {
        return automaticClosingEnabled;
    }

    public void setAutomaticClosingEnabled(boolean automaticClosingEnabled) {
        this.automaticClosingEnabled = automaticClosingEnabled;
    }

    public long getAutomaticClosingDurationAmount() {
        return automaticClosingDurationAmount;
    }

    public void setAutomaticClosingDurationAmount(long amount) {
        this.automaticClosingDurationAmount = amount;
    }

    public Duration.Scale getAutomaticClosingDurationScale() {
        return automaticClosingDurationScale;
    }

    public void setAutomaticClosingDurationScale(Duration.Scale scale) {
        this.automaticClosingDurationScale = scale;
    }

    public String getAutomaticClosingRuleName() {
        return automaticClosingRuleName;
    }

    public void setAutomaticClosingRuleName(String ruleName) {
        this.automaticClosingRuleName = ruleName;
    }

    public CertificationAction.Status getAutomaticClosingAction() {
        return automaticClosingAction;
    }

    public void setAutomaticClosingAction(CertificationAction.Status action) {
        this.automaticClosingAction = action;
    }

    public String getAutomaticClosingComments() {
        return automaticClosingComments;
    }

    public void setAutomaticClosingComments(String comments) {
        this.automaticClosingComments = comments;
    }

    public Identity getAutomaticClosingSigner() {
        return automaticClosingSigner;
    }

    public void setAutomaticClosingSigner(Identity signer) {
        this.automaticClosingSigner = signer;
    }

    // Exception popup config
    public boolean isExceptionPopupEnabled() {
        return exceptionPopupEnabled;
    }

    public void setExceptionPopupEnabled(boolean exceptionPopupEnabled) {
        this.exceptionPopupEnabled = exceptionPopupEnabled;
    }

    public long getExceptionPeriodAmount() {
        return exceptionPeriodAmount;
    }

    public void setExceptionPeriodAmount(long amount) {
        this.exceptionPeriodAmount = amount;
    }

    public Duration.Scale getExceptionPeriodScale() {
        return exceptionPeriodScale;
    }

    public void setExceptionPeriodScale(Duration.Scale scale) {
        this.exceptionPeriodScale = scale;
    }

    public Identity getDefaultRemediator() {
        return this.defaultRemediator;
    }

    public void setDefaultRemediator(Identity remediator) {
        this.defaultRemediator = remediator;
    }

    public List<SelectItem> getDurationScales() {

        List<SelectItem> scales = new ArrayList<SelectItem>();
        scales.add(new SelectItem(Duration.Scale.Hour,
                                  getMessage(Duration.Scale.Hour.getMessageKey())));
        scales.add(new SelectItem(Duration.Scale.Day,
                                  getMessage(Duration.Scale.Day.getMessageKey())));
        scales.add(new SelectItem(Duration.Scale.Week,
                                  getMessage(Duration.Scale.Week.getMessageKey())));
        scales.add(new SelectItem(Duration.Scale.Month,
                                  getMessage(Duration.Scale.Month.getMessageKey())));
        return scales;
    }

    public String getSelectedEscalationRule() {
        return selectedEscalationRule;
    }

    public void setSelectedEscalationRule(String selectedEscalationRule) {
        this.selectedEscalationRule = selectedEscalationRule;
    }

    public List<SelectItem> getEscalationRules() throws GeneralException {
        return WebUtil.getRulesByType(getContext(), Rule.Type.Escalation, true);
    }

    public List<SelectItem> getAutomaticClosingRules() throws GeneralException {
        return WebUtil.getRulesByType(getContext(), Rule.Type.CertificationAutomaticClosing, true);
    }

    public List <SelectItem> getApproverRules() throws GeneralException {
        return WebUtil.getRulesByType(getContext(), Rule.Type.Approver, true);
    }

    public List<SelectItem> getWorkItemForwardRules() throws GeneralException {
        return WebUtil.getRulesByType(getContext(), Rule.Type.WorkItemForward, true);
    }

    public List<SelectItem> getFallbackWorkItemForwardRules() throws GeneralException {
        return WebUtil.getRulesByType(getContext(), Rule.Type.FallbackWorkItemForward, true);
    }

    public List<SelectItem> getPrivilegedItemSelectorRules() throws GeneralException {
        return WebUtil.getRulesByType(getContext(), Rule.Type.PrivilegedItemSelector, true);
    }
    //////////////////////////////////////////////////////////////////////
    //
    // Workflow Lists
    //
    //////////////////////////////////////////////////////////////////////

    public List <SelectItem> getRoleWorkflows() throws GeneralException {
        if (roleWorkflows == null)
            roleWorkflows = WebUtil.getWorkflows(getContext(), Workflow.TYPE_ROLE_MODELER);
        return roleWorkflows;
    }

    public List <SelectItem> getIdentityWorkflows() throws GeneralException {
        if (identityWorkflows == null)
            identityWorkflows = WebUtil.getWorkflows(getContext(), Workflow.TYPE_IDENTITY_UPDATE);
        return identityWorkflows;
    }

    public List <SelectItem> getManualCorrelationWorkflows() throws GeneralException {
        if (manualCorrelationWorkflows == null) {
            manualCorrelationWorkflows = WebUtil.getWorkflows(getContext(), Workflow.TYPE_IDENTITY_CORRELATION);
        }
        return manualCorrelationWorkflows;
    }

    public List <SelectItem> getRefreshWorkflows() throws GeneralException {
        if (refreshWorkflows == null)
            refreshWorkflows = WebUtil.getWorkflows(getContext(), Workflow.TYPE_IDENTITY_REFRESH);
        return refreshWorkflows;
    }

    public List <SelectItem> getAssignmentWorkflows() throws GeneralException {
        if (assignmentWorkflows == null)
            assignmentWorkflows = WebUtil.getWorkflows(getContext(), Workflow.TYPE_SCHEDULED_ASSIGNMENT);
        return assignmentWorkflows;
    }

    public List <SelectItem> getActivationWorkflows() throws GeneralException {
        if (activationWorkflows == null)
            activationWorkflows = WebUtil.getWorkflows(getContext(), Workflow.TYPE_SCHEDULED_ROLE_ACTIVATION);
        return activationWorkflows;
    }

    public List <SelectItem> getManagedAttributeWorkflows() throws GeneralException {
        if (groupWorkflows == null)
            groupWorkflows = WebUtil.getWorkflows(getContext(), Workflow.TYPE_MANAGED_ATTRIBUTE);
        return groupWorkflows;
    }

    public List <SelectItem> getPasswordInterceptWorkflows() throws GeneralException {
        if (interceptWorkflows == null)
            interceptWorkflows = WebUtil.getWorkflows(getContext(), Workflow.TYPE_PASSWORD_INTERCEPT);
        return interceptWorkflows;
    }

    public List <SelectItem> getPamProvisioningWorkflows() throws GeneralException {
        if (pamWorkflows == null)
            pamWorkflows = WebUtil.getWorkflows(getContext(), Workflow.TYPE_PAM_PROVISIONING, false);
        return pamWorkflows;
    }

    public List<SelectItem> getEmailNotifierTypes() throws GeneralException {

        if (null == this.emailNotifierTypes) {
            this.emailNotifierTypes = new ArrayList<SelectItem>();
            this.emailNotifierTypes.add(new SelectItem(Configuration.EMAIL_NOTIFIER_TYPE_SMTP,
                                                       getMessage(MessageKeys.EMAIL_NOTIFIER_TYPE_SMTP)));
            this.emailNotifierTypes.add(new SelectItem(Configuration.EMAIL_NOTIFIER_TYPE_REDIRECT_TO_EMAIL,
                                                       getMessage(MessageKeys.EMAIL_NOTIFIER_TYPE_REDIRECT_TO_EMAIL)));
            this.emailNotifierTypes.add(new SelectItem(Configuration.EMAIL_NOTIFIER_TYPE_REDIRECT_TO_FILE,
                    getMessage(MessageKeys.EMAIL_NOTIFIER_TYPE_REDIRECT_TO_FILE)));
        }

        return this.emailNotifierTypes;
    }
    
    public List<SelectItem> getWorkItemArchiveTypes()
    {
        if (workItemArchiveTypes == null) {
            List<WorkItem.Type> hiddenTypes = Arrays.asList(
                WorkItem.Type.Event,
                WorkItem.Type.Generic,
                WorkItem.Type.Test
            );
            
            workItemArchiveTypes = new ArrayList<SelectItem>();
            for (WorkItem.Type type : WorkItem.Type.values()) {
                if (!hiddenTypes.contains(type)) {
                    workItemArchiveTypes.add(new SelectItem(type.name(), getMessage(type.getMessageKey())));
                }
            }
        }
        
        return workItemArchiveTypes;        
    }

    public List<String> getSelectedWorkItemArchiveTypes()
    {
        return selectedWorkItemArchiveTypes;
    }
    
    public void setSelectedWorkItemArchiveTypes(List<String> selectedWorkItemArchiveTypes)
    {
        this.selectedWorkItemArchiveTypes = selectedWorkItemArchiveTypes;
    }

    public List<SelectItem> getAttachmentConfigRules() throws GeneralException
    {
        return getConfigRules(Rule.Type.AttachmentConfig);
    }

    public List<String> getSelectedAttachmentConfigRules() { return selectedAttachmentConfigRules; }

    public void setSelectedAttachmentConfigRules(List<String> selectedAttachmentConfigRules) {
        this.selectedAttachmentConfigRules = selectedAttachmentConfigRules;
    }

    // Get the list of available comment config rules for selection
    public List<SelectItem> getCommentConfigRules() throws GeneralException {
        return getConfigRules(Rule.Type.CommentConfig);
    }

    public List<String> getSelectedCommentConfigRules() { return selectedCommentConfigRules; }

    public void setSelectedCommentConfigRules(List<String> selectedCommentConfigRules) {
        this.selectedCommentConfigRules = selectedCommentConfigRules;
    }

    private List<SelectItem> getConfigRules(Rule.Type ruleType) throws GeneralException
    {
        List configRules = new ArrayList<>();
        QueryOptions qo = new QueryOptions();
        qo.add(Filter.ignoreCase(Filter.eq("type", ruleType)));
        qo.addOrdering("name", true, true);
        List<Rule> rules = getContext().getObjects(Rule.class, qo);
        for (Rule rule : Util.safeIterable(rules)) {
            configRules.add(new SelectItem(rule.getName()));
        }
        return configRules;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Localization
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * This will be edited as a CSV but it is normally stored as a List<String>
     * Someone with way more UI ambition than I have can convert this to
     * a fancy selector but note that this page has not been Extized yet...
     * jsl
     */
    public String getSupportedLanguages() throws GeneralException {

        List<String> langs = getSupportedLanguageList();
        return Util.listToCsv(langs);
    }

    public void setSupportedLanguages(String s) throws GeneralException {
        
        Configuration config = getObject();
        config.put(Configuration.SUPPORTED_LANGUAGES, Util.csvToList(s));
    }
    
    public List <SelectItem> getSupportedLanguagesItems() throws GeneralException {

        if (_langItems == null) {
            _langItems = new ArrayList<SelectItem>();

            List<String> langs = getSupportedLanguageList();
            if (langs != null) {
                for (String lang : langs) {
                    _langItems.add(new SelectItem(lang, lang));
                }
            }
        }

        return _langItems;
    }

    public void setDefaultLanguage(String s) throws GeneralException {

        Configuration config = getObject();
        config.put(Configuration.DEFAULT_LANGUAGE, s);
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Reports
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Gets the delimiter used with JasperReports
     *
     * @return The requested delimiter
     * @throws GeneralException
     */
    public String getJasperCsvDelimiter() throws GeneralException {
        Configuration config = getObject();
        return config.getString(Configuration.JASPER_CSV_DELIMITER);
    }

    /**
     * Set the delimiter for use with JasperReports
     *
     * @param delimiter The delimiter to set
     * @throws GeneralException
     */
    public void setJasperCsvDelimiter(String delimiter) throws GeneralException {
        Configuration config = getObject();
        config.put(Configuration.JASPER_CSV_DELIMITER, delimiter);
    }

    /**
     * Gets the search report type
     *
     * @return The report type
     * @throws GeneralException
     */
    public String getReportSearchType() throws GeneralException {
        Configuration config = getObject();
        return config.getString(Configuration.REPORT_SEARCH_TYPE);
    }

    /**
     * Set the search type for reports
     *
     * @param reportSearchType The report search type to be set
     * @throws GeneralException
     */
    public void setReportSearchType(String reportSearchType) throws GeneralException {
        Configuration config = getObject();
        config.put(Configuration.REPORT_SEARCH_TYPE, reportSearchType);
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Duplicate accounts
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * 
     * @return true if duplicate accounts feature is enabled
     */
    public boolean getAllowDuplicateAccounts() {
        return allowDuplicateAccounts;
    }

    /**
     * Enable/disable the duplicate accounts feature.
     * @param allowDuplicateAccounts true if duplicate accounts feature is enabled
     */
    public void setAllowDuplicateAccounts(boolean allowDuplicateAccounts) {
        this.allowDuplicateAccounts = allowDuplicateAccounts;
    }

    public boolean isEnableModelerMultipleRoleAssignments() {
        return enableModelerMultipleRoleAssignments;
    }

    public void setEnableModelerMultipleRoleAssignments(boolean enableModelerMultipleRoleAssignments) {
        this.enableModelerMultipleRoleAssignments = enableModelerMultipleRoleAssignments;
    }

    public boolean isAllowMultipleRoleAssignments() {
        return allowMultipleRoleAssignments;
    }

    public void setAllowMultipleRoleAssignments(boolean allowMultipleRoleAssignments) {
        this.allowMultipleRoleAssignments = allowMultipleRoleAssignments;
    }

    public boolean isHashIdentitySecrets() {
        return hashIdentitySecrets;
    }

    public void setHashIdentitySecrets(boolean hashIdentitySecrets) {
        this.hashIdentitySecrets = hashIdentitySecrets;
    }

    public int getHashingIterations() {
        return hashingIterations;
    }

    public void setHashingIterations(int hashingIterations) {
        this.hashingIterations = hashingIterations;
    }

    public boolean isAllowRolePropagation() {
        return allowRolePropagation;
    }

    public boolean isRetainAssignedEntitlementsDetected() {
        return retainAssignedEntitlementsDetected;
    }

    public void setRetainAssignedEntitlementsDetected(boolean retainAssignedEntitlementsDetected) {
        this.retainAssignedEntitlementsDetected = retainAssignedEntitlementsDetected;
    }

    public boolean isRetainAssignedEntitlementsAssigned() {
        return retainAssignedEntitlementsAssigned;
    }

    public void setRetainAssignedEntitlementsAssigned(boolean retainAssignedEntitlementsAssigned) {
        this.retainAssignedEntitlementsAssigned = retainAssignedEntitlementsAssigned;
    }

    public void setAllowRolePropagation(boolean allowRolePropagation) {
        this.allowRolePropagation = allowRolePropagation;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Recommendations
    //
    //////////////////////////////////////////////////////////////////////

    public boolean getShowRecommendations() {
        return this.showRecommendations;
    }

    public void setShowRecommendations(boolean showRecommendations) {
        this.showRecommendations = showRecommendations;
    }

    public boolean isAutoApprove() {
        return this.autoApprove;
    }

    public void setAutoApprove(boolean autoApprove) {
        this.autoApprove = autoApprove;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Actions
    //
    //////////////////////////////////////////////////////////////////////
    public String cancelChangesAction() {
        return "cancel";
    }

    /**
     * This does a bunch of field validation before saving.
     * The convention is to set result to null, which will
     * be tested at the end.  This is something that cries
     * out to be data driven.  Password policy began the
     * convention of factoring out validation to a business
     * logic class, consider doing the same for the others - jsl
     */
    public String saveChangesAction() {
        String result = "save";

        // save state in case we have a validation error
        // !! actually we don't need this since we'll keep
        // the same bean for the next render?
        //saveSession();

        try {
            Configuration sysConfig = getObject();

            // call the encapsulated validators
            if (!validatePasswordPolicy(sysConfig))
                result = null;

            // Validate Limit Reassignment
            if (!validateReassignmentLimit()) {
                result = null;
            } else {
                sysConfig.put(Configuration.CERTIFICATION_LIMIT_REASSIGNMENTS, limitReassignments);
                sysConfig.put(Configuration.CERTIFICATION_REASSIGNMENT_LIMIT, reassignmentLimit);
            }
            
            if (!validateEmailNotify())
                result = null;
            // then the inline validators

            /*
            if (selectedReminderEmailTemplate != null && !"".equals(selectedReminderEmailTemplate)) {
                sysConfig.put(Configuration.DEFAULT_REMINDER_EMAIL_TEMPLATE, selectedReminderEmailTemplate);
            }

            if (selectedEscalationEmailTemplate != null && !"".equals(selectedEscalationEmailTemplate)) {
                sysConfig.put(Configuration.DEFAULT_ESCALATION_EMAIL_TEMPLATE, selectedEscalationEmailTemplate);
            }
            */

            try {
                long wakeUpIntervalInMillis = Long.parseLong(defaultWakeUpInterval) * MILLIS_PER_DAY;
                sysConfig.put(Configuration.DEFAULT_WAKEUP_INTERVAL, String.valueOf(wakeUpIntervalInMillis));
            } catch (NumberFormatException e2) {
                addRequiredErrorMessage("workItemReminderFrequency",
                        new Message(Message.Type.Error, MessageKeys.INVALID_REMINDER_DAYS_INTERVAL,
                                defaultWakeUpInterval));

                result = null;
            }

            try {
                Integer.parseInt(defaultMaxReminders);
                sysConfig.put(Configuration.DEFAULT_MAX_REMINDERS, defaultMaxReminders);
            } catch (NumberFormatException e3) {
                addRequiredErrorMessage("workItemEscalationFrequency",
                         new Message(Message.Type.Error, MessageKeys.INVALID_MAX_ESCALATION_REMINDERS,
                                defaultMaxReminders));
                result = null;
            }

            try {
                Integer.parseInt(workItemDuration);
                sysConfig.put(Configuration.DEFAULT_WORK_ITEM_DURATION, workItemDuration);
            } catch (NumberFormatException e2) {
                addRequiredErrorMessage("workItemDuration",
                         new Message(Message.Type.Error, MessageKeys.INVALID_WORK_ITEM_DURATION,
                                workItemDuration));
                result = null;
            }

            try {
                long reminderStartInMillis = Long.parseLong(defaultReminderStart) * MILLIS_PER_DAY;
                sysConfig.put(Configuration.DEFAULT_REMINDER_START, String.valueOf(reminderStartInMillis));
            } catch (NumberFormatException e1) {

                addRequiredErrorMessage("workItemReminderStart",
                         new Message(Message.Type.Error, MessageKeys.INVALID_REMINDER_START_VAL,
                                defaultReminderStart));
                result = null;
            }

            if (Boolean.parseBoolean(this.isSubordinateCertificationEnabled) && this.flattenManagerCertificationHierarchy) {
                Message msg = new Message(Message.Type.Error,
                        MessageKeys.CANT_FLATTEN_AND_GEN_SUB_CERTS);
                String msgText = msg.getLocalizedMessage(getLocale(), getUserTimeZone());
                FacesMessage facesMsg =
                    new FacesMessage(FacesMessage.SEVERITY_ERROR,
                                     msgText,
                                     msgText);
                FacesContext.getCurrentInstance().addMessage("enableSubordinateCertification", facesMsg);
                result = null;
            }

            try {
                long identitySnapshotIntervalInSeconds = Long.parseLong(identitySnapshotInterval) * SECONDS_PER_DAY;
                sysConfig.put(Configuration.IDENTITY_SNAPSHOT_INTERVAL, String.valueOf(identitySnapshotIntervalInSeconds));
            } catch (NumberFormatException e) {
                addRequiredErrorMessage("identitySnapshotInterval",
                         new Message(Message.Type.Error, MessageKeys.INVALID_ID_SNAP_INTERVAL,
                                identitySnapshotInterval));
                result = null;
            }

            try {
                sysConfig.put(Configuration.IDENTITY_MAX_SEARCHABLE_ATTRIBUTES, identityMaxExtendedAttributes);
            } catch (NumberFormatException e) {

                addRequiredErrorMessage("identityMaxExtendedAttributes",
                        new Message(Message.Type.Error, MessageKeys.ERR_MAX_NUM_SEARCHABLE_ATTRS,
                                identityMaxExtendedAttributes));
                result = null;
            }

            try {
                sysConfig.put(Configuration.LINK_MAX_SEARCHABLE_ATTRIBUTES, accountMaxExtendedAttributes);
            } catch (NumberFormatException e) {

                addRequiredErrorMessage("accountMaxExtendedAttributes",
                        new Message(Message.Type.Error, MessageKeys.ERR_MAX_NUM_SEARCHABLE_ACCT_ATTRS,
                                accountMaxExtendedAttributes));
                result = null;
            }

            if (helpEmailAddress != null && !"".equals(helpEmailAddress.trim())
                    && !EmailValidator.getInstance().isValid(helpEmailAddress)){
                String msg = Message.error(MessageKeys.SYS_CONFIG_ERR_HELP_EMAIL_INVALID).getLocalizedMessage(
                        getLocale(), getUserTimeZone());
                addRequiredErrorMessage("helpEmailAddress", msg);
                result = null;
            }

            if (!this.showRecommendations) {
                this.autoApprove = false;
            }



            // these we just slam in without validation
            // we could avoid this by simply referencing the attributes
            // map from the JSF form - jsl

            sysConfig.put(Configuration.CERT_PAGE_LIST_ITEMS, certPageListItems);
            sysConfig.put(Configuration.CERTIFICATION_DETAILED_VIEW_FILTER_SET, detailedViewFilterSet);
            sysConfig.put(Configuration.ALLOW_CREATE_ROLES_FROM_CERTIFICATION, isAllowCreateRolesFromCertification);
            sysConfig.put(Configuration.ALLOW_SELF_CERTIFICATION, selectedSelfCertificationAllowedLevel.toString());
            String selfCertificationViolationOwnerName = this.selfCertificationViolationOwner != null ? this.selfCertificationViolationOwner.getName() : null;
            sysConfig.put(Configuration.SELF_CERTIFICATION_VIOLATION_OWNER, selfCertificationViolationOwnerName);
            sysConfig.put(Configuration.CERTIFICATION_MITIGATION_ENABLED, isMitigationEnabled);
            sysConfig.put(Configuration.CERTIFICATION_MITIGATION_DEPROVISION_ENABLED, isMitigationDeprovisionEnabled);
            sysConfig.put(Configuration.ALLOW_PROV_MISSING_REQUIREMENTS, allowProvisioningMissingRequirements);
            sysConfig.put(Configuration.REQUIRE_APPROVAL_COMMENTS, requireApprovalComments);
            sysConfig.put(Configuration.REQUIRE_REMEDIATION_COMMENTS, requireRemediationComments);
            sysConfig.put(Configuration.REQUIRE_MITIGATION_COMMENTS, requireMitigationComments);
            sysConfig.put(Configuration.EXCLUDE_BASE_APP_ACCOUNTS, excludeBaseAppAccounts);
            sysConfig.put(Configuration.CERTIFICATION_SUBCERT_PAGE_SIZE, subCertificationPageSize);
            sysConfig.put(Configuration.CERTIFICATION_DELEGATION_REVIEW, isCertificationDelegationReviewRequired);
            sysConfig.put(Configuration.NOTIFY_REMEDIATION, notifyRemediation);
            sysConfig.put(Configuration.MITIGATION_EXPIRATION_ACTION, mitigationExpirationAction);

            sysConfig.put(Configuration.SUBORDINATE_CERTIFICATION_ENABLED, isSubordinateCertificationEnabled);
            sysConfig.put(Configuration.FLATTEN_CERTIFICATION_MANAGER_HIERARCHY, flattenManagerCertificationHierarchy);
            sysConfig.put(Configuration.COMPLETE_CERTIFICATION_HIERARCHY, completeCertificationHierarchy);
            sysConfig.put(Configuration.ADDITIONAL_ENTITLEMENT_GRANULARITY, this.entitlementGranularity.name());

            sysConfig.put(Configuration.ACTIVE_PERIOD_DURATION,
                    new Duration(this.activePeriodDurationAmount, this.activePeriodDurationScale));

            sysConfig.put(Configuration.CHALLENGE_PERIOD_ENABLED, this.challengePeriodEnabled);
            sysConfig.put(Configuration.CHALLENGE_PERIOD_DURATION,
                          new Duration(this.challengePeriodDurationAmount, this.challengePeriodDurationScale));

            sysConfig.put(Configuration.REMEDIATION_PERIOD_ENABLED, this.remediationPeriodEnabled);
            sysConfig.put(Configuration.REMEDIATION_PERIOD_DURATION,
                          new Duration(this.remediationPeriodDurationAmount, this.remediationPeriodDurationScale));

            sysConfig.put(Configuration.AUTOMATIC_CLOSING_ENABLED, this.automaticClosingEnabled);
            sysConfig.put(Configuration.AUTOMATIC_CLOSING_DURATION,
                          new Duration(this.automaticClosingDurationAmount, this.automaticClosingDurationScale));
            sysConfig.put(Configuration.AUTOMATIC_CLOSING_RULE_NAME, this.automaticClosingRuleName);
            sysConfig.put(Configuration.AUTOMATIC_CLOSING_ACTION, this.automaticClosingAction);
            sysConfig.put(Configuration.AUTOMATIC_CLOSING_COMMENTS, this.automaticClosingComments);
            String autoCloserName = (null != this.automaticClosingSigner) ? this.automaticClosingSigner.getName() : null;
            sysConfig.put(Configuration.AUTOMATIC_CLOSING_SIGNER, autoCloserName);

            String remediatorName = (null != this.defaultRemediator) ? this.defaultRemediator.getName() : null;
            sysConfig.put(Configuration.DEFAULT_REMEDIATOR, remediatorName);

            sysConfig.put(Configuration.BUNDLES_UI_DISPLAY_LIMIT, this.bundlesUIDisplayLimit);
            sysConfig.put(Configuration.TEMP_DIR, this.temporaryDirectory);
            sysConfig.put(Configuration.DISPLAY_ENTITLEMENT_DESC, displayEntitlementDescription);
            sysConfig.put(Configuration.ENABLE_ACCOUNT_APPROVE_ACTION, enableApproveAccount);
            sysConfig.put(Configuration.ENABLE_ACCOUNT_REVOKE_ACTION, enableRevokeAccount);
            sysConfig.put(Configuration.ENABLE_ACCOUNT_REASSIGN_ACTION, enableReassignAccount);
            
            String assigneeName = (null != this.defaultAssignee) ? this.defaultAssignee.getName() : null;
            sysConfig.put(Configuration.DEFAULT_ASSIGNEE, assigneeName);
            
            sysConfig.put(Configuration.ENABLE_OVERRIDE_VIOLATION_DEFAULT_REMEDIATOR,
                    enableOverrideDefaultViolationRemediator);

            sysConfig.put(Configuration.CERTIFICATION_MITIGATION_POPUP_ENABLED, this.exceptionPopupEnabled);
            sysConfig.put(Configuration.CERTIFICATION_MITIGATION_DURATION,
                    new Duration(this.exceptionPeriodAmount, this.exceptionPeriodScale));

            if (selectedEscalationRule != null && !"".equals(selectedEscalationRule)) {
                sysConfig.put(Configuration.DEFAULT_ESCALATION_RULE, selectedEscalationRule);
            }

            if (helpEmailAddress != null && !"".equals(helpEmailAddress.trim()))
                sysConfig.put(Configuration.HELP_EMAIL_ADDRESS, helpEmailAddress.trim());
            else
                sysConfig.put(Configuration.HELP_EMAIL_ADDRESS, null);
            
            sysConfig.put(Configuration.WORK_ITEM_ARCHIVE_TYPES, Util.listToCsv(selectedWorkItemArchiveTypes));
            
            sysConfig.put(Configuration.DEFAULT_REMEDIATION_MODIFIABLE_OP, defaultRemediationModifiableOp);

            sysConfig.put(Configuration.ENABLE_SYSLOG, enableSyslog);
            sysConfig.put(Configuration.SYSLOG_LEVEL, syslogLevel);
            sysConfig.put(Configuration.SYSLOG_PRUNE_AGE, syslogPruneAge);

            sysConfig.put(Configuration.ENABLE_PROVISIONING_TRANSACTION_LOG, enableProvisioningTransactionLog);
            sysConfig.put(Configuration.PROVISIONING_TRANSACTION_LOG_LEVEL, provisioningTransactionLogLevel);
            sysConfig.put(Configuration.PROVISIONING_TRANSACTION_LOG_PRUNE_AGE, provisioningTransactionLogPruneAge);

            // reset the default e sig value if enable is false
           	sysConfig.put(Configuration.REQUIRE_ELECTRONIC_SIGNATURE, (defaultElectronicSignatureEnabled ? defaultElectronicSignatureName : null));

            sysConfig.put(Configuration.ALLOW_DUPLICATE_ACCOUNTS, allowDuplicateAccounts);

            sysConfig.put(Configuration.ENABLE_MODELER_MULTIPLE_ASSIGNMENTS, enableModelerMultipleRoleAssignments);
            
            sysConfig.put(Configuration.ALLOW_ROLE_PROPAGATION, allowRolePropagation);
            
            sysConfig.put(Configuration.FORCE_ROLE_MERGE_TEMPLATE, forceRoleMergeTemplate);

            sysConfig.put(Configuration.RETAIN_ASSIGNED_ENTITLEMENTS_DETECTED_ROLE, retainAssignedEntitlementsDetected);
            sysConfig.put(Configuration.RETAIN_ASSIGNED_ENTITLEMENTS_ASSIGNED_ROLE, retainAssignedEntitlementsAssigned);

            // set Task Email Alert attributes
            sysConfig.put(Configuration.TASK_COMPLETION_EMAIL_NOTIFY, emailNotify);
            sysConfig.put(Configuration.TASK_COMPLETION_EMAIL_TEMPLATE, emailTemplate);
            if (emailNotify.equals(Configuration.EmailNotify.Disabled.toString())) {
                // if email notification is disabled identities should be null
                _nameList = null;
            }
            sysConfig.put(Configuration.TASK_COMPLETION_RECIPIENTS, _nameList);

            sysConfig.put(Configuration.ALLOW_MULTIPLE_ROLE_ASSIGNMENTS, allowMultipleRoleAssignments);

            sysConfig.put(Configuration.HASH_IDENTITY_SECRETS, hashIdentitySecrets);

            sysConfig.put(Configuration.PAM_PROVISIONING_ENABLED, pamProvisioningEnabled);
            sysConfig.put(Configuration.PAM_MODIFY_PRIVILEGED_DATA_ENABLED, pamModifyPrivilegedDataEnabled);
            sysConfig.put(Configuration.PAM_OWNER_CAN_EDIT_CONTAINER, pamOwnerCanEditContainer);
            sysConfig.put(Configuration.PAM_CREATE_CONTAINER_ENABLED, pamCreateContainerEnabled);
            sysConfig.put(Configuration.PAM_MAX_SELECTABLE_IDENTITIES, pamMaxSelectableIdentities);
            sysConfig.put(Configuration.PAM_WORKFLOW_IDENTITY_PROVISIONING, pamWorkflowIdentityProvisioning);
            sysConfig.put(Configuration.PAM_PRIVILEGED_ITEM_SELECTOR_RULE, pamPrivilegedItemSelectorRule);

            sysConfig.put(Configuration.ATTACHMENTS_ALLOWED_FILE_TYPES, attachmentsAllowedFileTypes);
            sysConfig.put(Configuration.ATTACHMENTS_FILENAME_ALLOWED_SPECIAL_CHARACTERS, attachmentsFilenameAllowedSpecialCharacters);
            sysConfig.put(Configuration.ATTACHMENTS_ENABLED, attachmentsEnabled);
            sysConfig.put(Configuration.ATTACHMENT_CONFIG_RULES, selectedAttachmentConfigRules);

            sysConfig.put(Configuration.REQUIRE_ACCESS_REQUEST_COMMENTS_ALL, requireAccessRequestCommentsAll);
            sysConfig.put(Configuration.COMMENT_CONFIG_RULES, selectedCommentConfigRules);

            sysConfig.put(Configuration.SHOW_EDIT_FORWARDING, showEditForwarding);
            sysConfig.put(Configuration.SHOW_CHANGE_PASSWORD_TAB, showChangePasswordTab);
            sysConfig.put(Configuration.CERTIFICATION_SHOW_RECOMMENDATIONS, showRecommendations);
            sysConfig.put(Configuration.CERTIFICATION_AUTO_APPROVE, autoApprove);

            // both in MB
            if (attachmentsMaxFileSize > 0 && attachmentsMaxFileSize <= attachmentsMaxFileSizeLimit) {
                sysConfig.put(Configuration.ATTACHMENTS_MAX_FILE_SIZE, attachmentsMaxFileSize);
            }
            else {
                // reset the value
                attachmentsMaxFileSize = sysConfig.getInt(Configuration.ATTACHMENTS_MAX_FILE_SIZE);

                addErrorMessage("editForm:attachmentsMaxFileSize",
                        Message.error(MessageKeys.ERROR_ATTACHMENTS_MAX_INVALID, attachmentsMaxFileSizeLimit),
                        Message.error(MessageKeys.ERROR_ATTACHMENTS_MAX_INVALID, attachmentsMaxFileSizeLimit));

                result = null;
            }

            if (hashIdentitySecrets) {
                sysConfig.put(Configuration.HASHING_ITERATIONS, hashingIterations);
            }
            
            if (smtpConfig.validate(this)) {
                smtpConfig.save(sysConfig);
            } else {
                result = null;
            }
            
            mailSettingsHelper.save(sysConfig);
            
            // don't save if we had any validation errors
            if (result != null) {

                // bandConfig will be null for Analyzer
                BandConfigBean bandConfig = (BandConfigBean) getFacesContext().getExternalContext().getRequestMap().get("bandConfig");
                if ( bandConfig != null )
                    bandConfig.saveBandChanges();
                
                auditPasswordPolicyChanges(previous, getObject().getAttributes());

                // Check to see if the CSV delimiter has changed.  If so, update JRProperties so we don't have to
                // force a restart of the server.
                String delim = sysConfig.getString(Configuration.JASPER_CSV_DELIMITER);
                JasperInit.updateProperty(JRCsvExporterParameter.PROPERTY_FIELD_DELIMITER, delim);
                
                saveAction();

                // Save the email retry count back on the request definition.
                String defName = sysConfig.getString(Configuration.EMAIL_REQUEST_DEFINITION);
                RequestDefinition emailRequest =
                    getContext().getObjectByName(RequestDefinition.class, defName);
                emailRequest.setRetryMax(this.maxEmailRetries);
                getContext().saveObject(emailRequest);
                getContext().commitTransaction();
            } else {
                // We had an error, ditch whatever is staged in the context so it's not accidentally saved
                getContext().decache();
            }

        } catch (GeneralException e) {
            log.error("The system configuration could not be successfully saved", e);
            result = "error";
        }

        return result;
    }

    /**
     * Save action for the Compliance Manager configuration page. Breaking the JSF action out into its own action, 
     * on success we will add a "save successful" message to the JSF life cycle. 
     * @return always return null to indicate to JSF do not navigate away from the page
     */
    public String saveCertificationConfigAction() {
        String result = saveChangesAction();
        if ("save".equals(result)) {
            addMessage(new Message(MessageKeys.SYS_CONFIG_CERT_SAVE_SUCCESS));
        }
        return null;
    }

    /**
     * Validate the password policy fields.
     * Return false if errors are found.
     * @throws GeneralException 
     */
    private boolean validatePasswordPolicy(Configuration sysConfig) throws GeneralException {

        PasswordPolice pp = new PasswordPolice(getContext());

        // we are a MessageAccumulator so we pass ourselves in
        return pp.validatePasswordPolicy(sysConfig, this);
    }

    /**
     * Validate the reassignment limit.
     * Return false if errors are found.
     */
    private boolean validateReassignmentLimit() {
        boolean valid = true;
        if (limitReassignments) {
            if (Util.isNullOrEmpty(reassignmentLimit)) {
                addMessage(new Message(Message.Type.Error, MessageKeys.SYS_CONFIG_ERR_REASSIGNMENT_LIMIT_EMPTY), null);
                valid = false;
            } else if (Util.otoi(reassignmentLimit) < 1) {
                addMessage(new Message(Message.Type.Error, MessageKeys.SYS_CONFIG_ERR_REASSIGNMENT_LIMIT_NOT_VALID), null);
                valid = false;
            }
        }
        return valid;
    }

    /**
     * Validate the password policy fields.
     * Return false if errors are found.
     */
    private boolean validateEmailNotify() {
        Boolean valid = true;
        if (!emailNotify.equals(Configuration.EmailNotify.Disabled.toString())) {
            if (_nameList != null) {
                if (_nameList.isEmpty()) {
                    addMessage(
                            new Message(
                                    Message.Type.Error,
                                    MessageKeys.TASK_ERR_NOTIFY_EMAIL_ADDR_BLANK),
                                    null);
                    valid = false;
                }
            }
            else {
                addMessage(
                        new Message(
                                Message.Type.Error,
                                MessageKeys.TASK_ERR_NOTIFY_EMAIL_ADDR_BLANK),
                                null);
                valid = false;                
            }
        }
        return (valid);
    }

    public void addMessage(Message message) {
        super.addMessage(message, message);
    }

    public String getApplicationsUIDisplayLimit() {
        return applicationsUIDisplayLimit;
    }

    public void setApplicationsUIDisplayLimit(String applicationsUIDisplayLimit) {
        this.applicationsUIDisplayLimit = applicationsUIDisplayLimit;
    }

    public String getBundlesUIDisplayLimit() {
        return bundlesUIDisplayLimit;
    }

    public void setBundlesUIDisplayLimit(String bundlesUIDisplayLimit) {
        this.bundlesUIDisplayLimit = bundlesUIDisplayLimit;
    }

    public String getManagersUIDisplayLimit() {
        return managersUIDisplayLimit;
    }

    public void setManagersUIDisplayLimit(String managersUIDisplayLimit) {
        this.managersUIDisplayLimit = managersUIDisplayLimit;
    }

   public String getTemporaryDirectory() {
      return temporaryDirectory;

   }

   public void setTemporaryDirectory(String tempDir) {
      this.temporaryDirectory = tempDir;
   }

    public String getHelpEmailAddress() {
        return helpEmailAddress;
    }

    public void setHelpEmailAddress(String helpEmailAddress) {
        this.helpEmailAddress = helpEmailAddress;
    }

    public int getMaxEmailRetries() {
        return this.maxEmailRetries;
    }

    public void setMaxEmailRetries(int maxEmailRetries) {
        this.maxEmailRetries = maxEmailRetries;
    }

    public boolean isExcludeBaseAppAccounts() {
        return excludeBaseAppAccounts;
    }

    public void setExcludeBaseAppAccounts(boolean excludeBaseAppAccounts) {
        this.excludeBaseAppAccounts = excludeBaseAppAccounts;
    }

    public String getDisplayEntitlementDescription() {
        return displayEntitlementDescription ? "true" : "false";
    }

    public void setDisplayEntitlementDescription(String displayEntitlementDescription) {
        this.displayEntitlementDescription = Boolean.parseBoolean(displayEntitlementDescription);
    }

    public boolean getEnableApproveAccount() {
        return enableApproveAccount;
    }

    public void setEnableApproveAccount(boolean enableApproveAccount) {
        this.enableApproveAccount = enableApproveAccount;
    }

    public boolean getEnableRevokeAccount() {
        return enableRevokeAccount;
    }

    public void setEnableRevokeAccount(boolean enableRevokeAccount) {
        this.enableRevokeAccount = enableRevokeAccount;
    }

    public boolean isEnableReassignAccount() {
        return enableReassignAccount;
    }

    public void setEnableReassignAccount(boolean enableReassignAccount) {
        this.enableReassignAccount = enableReassignAccount;
    }

    public Identity getDefaultAssignee() {
        return defaultAssignee;
    }

    public void setDefaultAssignee(Identity defaultAssignee) {
        this.defaultAssignee = defaultAssignee;
    }

    public boolean isEnableOverrideDefaultViolationRemediator() {
        return enableOverrideDefaultViolationRemediator;
    }

    public void setEnableOverrideDefaultViolationRemediator(boolean enableOverrideDefaultViolationRemediator) {
        this.enableOverrideDefaultViolationRemediator = enableOverrideDefaultViolationRemediator;
    }
    
    public String getDefaultRemediationModifiableOp() {
        return defaultRemediationModifiableOp;
    }
    
    public void setDefaultRemediationModifiableOp(String defaultRemediationModifiableOp) {
        this.defaultRemediationModifiableOp = defaultRemediationModifiableOp;
    }

    /**
     * Return labels for a Email Notify selector.
     */
    public SelectItem[] getEmailNotifyOptions() throws GeneralException {

        if (_emailNotifyOptions == null) {
            _emailNotifyOptions = new SelectItem[4];
            _emailNotifyOptions[0] = new SelectItem(EmailNotify.Disabled.toString(), getMessage(MessageKeys.NOTIFY_OPTION_DISABLED));
            _emailNotifyOptions[1] = new SelectItem(EmailNotify.Warning.toString(), getMessage(MessageKeys.NOTIFY_OPTION_WARNING));
            _emailNotifyOptions[2] = new SelectItem(EmailNotify.Failure.toString(), getMessage(MessageKeys.NOTIFY_OPTION_FAILURE));
            _emailNotifyOptions[3] = new SelectItem(EmailNotify.Always.toString(), getMessage(MessageKeys.NOTIFY_OPTION_ALWAYS));
        }
        return _emailNotifyOptions;
    }

    public String getEmailNotify() throws GeneralException {
        return emailNotify;
    }

    public void setEmailNotify(String emailNotify) throws GeneralException {
        this.emailNotify = emailNotify;
        if (emailNotify.equals(Configuration.EmailNotify.Disabled.toString())) {
            this._nameList = null;
        }
    }

    public boolean isLimitReassignments() throws GeneralException {
        return limitReassignments;
    }

    public void setLimitReassignments(boolean limitReassignments) throws GeneralException {
        this.limitReassignments = limitReassignments;
    }

    public String getReassignmentLimit() throws GeneralException {
        return reassignmentLimit;
    }

    public void setReassignmentLimit(String reassignmentLimit) throws GeneralException {
        this.reassignmentLimit = reassignmentLimit;
    }

    public String getEmailTemplate() throws GeneralException {
        return emailTemplate;
    }

    public void setEmailTemplate(String emailTemplate) throws GeneralException {
        this.emailTemplate = emailTemplate;
    }

    public List<String>  getIdentities() throws GeneralException{
        return(_nameList); 
    }

    public void setIdentities(List<String> names)throws GeneralException {
        this._nameList = names;
    }

    /**
     * Holds SmtpSpecific Options
     *
     */
    public static class SmtpConfig {
        
        private SailPointContext context;
        private SmtpEncryptionType encryptionType;
        private String username;
        private String password;
        private String confirmPassword;
        private List<SelectItem> encryptionTypes;
        
        public SmtpConfig(SailPointContext context, Configuration sysConfig) throws GeneralException {

            this.context = context;
            encryptionType = (SmtpEncryptionType)sysConfig.get(Configuration.SmtpConfiguration.EncryptionType);
            if (encryptionType == null) {
                encryptionType = SmtpEncryptionType.NONE;
            }
            
            username = sysConfig.getString(Configuration.SmtpConfiguration.Username);
            // if for some reason it is still encoded, decode
            if (Util.isNotNullOrEmpty(username) && ObjectUtil.isEncoded(username)) {
                username = this.context.decrypt(username);
            }
            
            // decrypt will only happen if it is actually encrypted
            // so this method is safe.
            password = sysConfig.getString(Configuration.SmtpConfiguration.Password);
            password = this.context.decrypt(password);
            
            confirmPassword = password;
            
            initEncryptionTypes();
        }
        
        public SmtpEncryptionType getEncryptionType() {
            return encryptionType;
        }
        
        public void setEncryptionType(SmtpEncryptionType val) {
            encryptionType = val;
        }
        
        public String getUsername() {
            return username;
        }
        
        public void setUsername(String val) {
            username = val;
        }
        
        public String getPassword() {
            return password;
        }
        
        public void setPassword(String val) {
            password = val;
        }
        
        public String getConfirmPassword() {
            return confirmPassword;
        }
        
        public void setConfirmPassword(String val) {
            confirmPassword = val;
        }
        
        public List<SelectItem> getEncryptionTypes() {
            return encryptionTypes;
        }

        private void initEncryptionTypes() {
            
            encryptionTypes = new ArrayList<SelectItem>();
            
            encryptionTypes.add(new SelectItem(SmtpEncryptionType.NONE, SmtpEncryptionType.NONE.getDisplay()));
            encryptionTypes.add(new SelectItem(SmtpEncryptionType.SSL, SmtpEncryptionType.SSL.getDisplay()));
            encryptionTypes.add(new SelectItem(SmtpEncryptionType.TLS, SmtpEncryptionType.TLS.getDisplay()));

        }
        
        private boolean validate(SystemConfigBean parent) throws GeneralException {

            Configuration sysConfig = parent.getObject();

            //Do not validate password if we are redirect to file
            if ( sysConfig != null && !sysConfig.getString(Configuration.EMAIL_NOTIFIER_TYPE).equals(Configuration.EMAIL_NOTIFIER_TYPE_REDIRECT_TO_FILE)) {
                if (Util.isNullOrEmpty(password)) {
                    if (Util.isNullOrEmpty(confirmPassword)) {
                        return true;
                    } else {
                        parent.addErrorMessage("editForm:smtpConfirmPassword", Message.error(MessageKeys.ERROR_IDENTITY_CONFIRM_PASS_MISMATCH), Message.error(MessageKeys.ERROR_IDENTITY_CONFIRM_PASS_MISMATCH));
                        return false;
                    }
                }
                
                boolean valid = false;
                //IIQTC-67 :- In order to comparer the password and confirmPassword we should
                //use the decrypted version.
                if (!this.context.decrypt(password).equals(this.context.decrypt(confirmPassword))) {
                    parent.addErrorMessage("editForm:smtpConfirmPassword", Message.error(MessageKeys.ERROR_IDENTITY_CONFIRM_PASS_MISMATCH), Message.error(MessageKeys.ERROR_IDENTITY_CONFIRM_PASS_MISMATCH));
                    valid = false;
                } else {
                    valid = true;
                }
                
                return valid;
            } else
                return true;
        }
        
        private void save(Configuration sysConfig) throws GeneralException {
            
            String emailNotifierType = sysConfig.getString(Configuration.EMAIL_NOTIFIER_TYPE);
            if (emailNotifierType.equals(Configuration.EMAIL_NOTIFIER_TYPE_REDIRECT_TO_FILE)) {
                encryptionType = SmtpEncryptionType.NONE;
                username = null;
                password = null;
            }
            
            sysConfig.put(SmtpConfiguration.EncryptionType, encryptionType);
            sysConfig.put(SmtpConfiguration.Username, username);
            if (Util.isNotNullOrEmpty(password)) {
                password = context.encrypt(password);
            }
            sysConfig.put(SmtpConfiguration.Password, password);
        }
    }
    
    public SmtpConfig getSmtpConfig() {
        return smtpConfig;
    }
    
    public MailSettingsHelper getMailSettingsHelper() {
        return mailSettingsHelper;
    }

    public boolean getEnableSyslog() {
        return enableSyslog;
    }

    public void setEnableSyslog(boolean enableSyslog) {
        this.enableSyslog = enableSyslog;
    }

    public String getSyslogLevel() {
        return syslogLevel;
    }

    public void setSyslogLevel(String syslogLevel) {
        this.syslogLevel = syslogLevel;
    }

    public int getSyslogPruneAge() {
        return syslogPruneAge;
    }

    public void setSyslogPruneAge(int syslogPruneAge) {
        this.syslogPruneAge = syslogPruneAge;
    }

    public boolean getEnableProvisioningTransactionLog() {
        return enableProvisioningTransactionLog;
    }

    public void setEnableProvisioningTransactionLog(boolean enableProvisioningTransactionLog) {
        this.enableProvisioningTransactionLog = enableProvisioningTransactionLog;
    }

    public ArrayList<SelectItem> getProvisioningTransactionLogLevels() {
        ArrayList<SelectItem> levelMessages = new ArrayList<SelectItem>();
        for (Level enumValue : Level.values()) {
            levelMessages.add(new SelectItem(enumValue, enumValue.getLocalizedMessage(getLocale(), getUserTimeZone())));
        }
        return levelMessages;
    }

    public String getProvisioningTransactionLogLevel() {
        return provisioningTransactionLogLevel;
    }

    public void setProvisioningTransactionLogLevel(String provisioningTransactionLogLevel) {
        this.provisioningTransactionLogLevel = provisioningTransactionLogLevel;
    }

    public int getProvisioningTransactionLogPruneAge() {
        return provisioningTransactionLogPruneAge;
    }

    public void setProvisioningTransactionLogPruneAge(int provisioningTransactionLogPruneAge) {
        this.provisioningTransactionLogPruneAge = provisioningTransactionLogPruneAge;
    }

    /**
     * 
     * Holds information of one email template that is displayed in the UI 
     * @author tapash
     *
     */
    public static class MailSetting {
        
        private String configKey;
        private String name;
        private String value;
        
        public String getConfigKey() {
            return configKey;
        }
        
        public void setConfigKey(String val) {
            configKey = val;
        }
        
        public String getName() {
        
            return name;
        }
        
        public void setName(String name) {
        
            this.name = name;
        }
        
        public String getValue() {
        
            return value;
        }
        
        public void setValue(String value) {
        
            this.value = value;
        }
    }

    /**
     * 
     * This class encapsulates the mechanism for obtaining the email templates 
     * from system configuration on init. And updating the system configuration
     * on saving.
     *
     */
    public static class MailSettingsHelper {

        private List<MailSetting> settings;

        public MailSettingsHelper(Configuration sysConfig) {
            init(sysConfig);
        }

        public List<MailSetting> getMailSettings() {
            return settings;
        }

        private void init(Configuration sysConfig) {

            settings = new ArrayList<SystemConfigBean.MailSetting>();
            addMailSettingFromSysConfig(MessageKeys.EMAIL_TEMPLATE_REMINDER_NOTICES, Configuration.DEFAULT_REMINDER_EMAIL_TEMPLATE, sysConfig);
            addMailSettingFromSysConfig(MessageKeys.EMAIL_TEMPLATE_ESCALATION_NOTICES, Configuration.DEFAULT_ESCALATION_EMAIL_TEMPLATE, sysConfig);
            addMailSettingFromSysConfig(MessageKeys.EMAIL_TEMPLATE_WORK_ITEM_COMMENT, Configuration.WORK_ITEM_COMMENT_EMAIL_TEMPLATE, sysConfig);
            addMailSettingFromSysConfig(MessageKeys.EMAIL_TEMPLATE_WORK_ITEM_FORWARD, Configuration.WORK_ITEM_FORWARD_EMAIL_TEMPLATE, sysConfig);
            addMailSettingFromSysConfig(MessageKeys.EMAIL_TEMPLATE_POLICY_VIOLATION, Configuration.POLICY_VIOLATION_EMAIL_TEMPLATE, sysConfig);
            addMailSettingFromSysConfig(MessageKeys.EMAIL_TEMPLATE_TASK_REPORT_SIGNOFF, Configuration.SIGNOFF_EMAIL_TEMPLATE, sysConfig);
            addMailSettingFromSysConfig(MessageKeys.EMAIL_TEMPLATE_WORKITEM_ASSIGNMENT, Configuration.WORK_ITEM_ASSIGNMENT_EMAIL_TEMPLATE, sysConfig);
            addMailSettingFromSysConfig(MessageKeys.EMAIL_TEMPLATE_WORKITEM_ASSIGNMENT_REMOVAL, Configuration.WORK_ITEM_ASSIGNMENT_REMOVAL_EMAIL_TEMPLATE, sysConfig);
            addMailSettingFromSysConfig(MessageKeys.EMAIL_TEMPLATE_REMEDITEM_ASSIGNMENT, Configuration.REMED_ITEM_ASSIGNMENT_EMAIL_TEMPLATE, sysConfig);
            addMailSettingFromSysConfig(MessageKeys.EMAIL_TEMPLATE_REMEDITEM_ASSIGNMENT_REMOVAL, Configuration.REMED_ITEM_ASSIGNMENT_REMOVAL_EMAIL_TEMPLATE, sysConfig);

            // new keys from bug #11409
            addMailSettingFromSysConfig(MessageKeys.EMAIL_TEMPLATE_DELEGATION, Configuration.DELEGATION_EMAIL_TEMPLATE, sysConfig);
            addMailSettingFromSysConfig(MessageKeys.EMAIL_TEMPLATE_DELEGATION_FINISHED, Configuration.DELEGATION_FINISHED_EMAIL_TEMPLATE, sysConfig);
            addMailSettingFromSysConfig(MessageKeys.EMAIL_TEMPLATE_DELEGATION_REVOCATION, Configuration.DELEGATION_REVOCATION_EMAIL_TEMPLATE, sysConfig);
            addMailSettingFromSysConfig(MessageKeys.EMAIL_TEMPLATE_REMEDIATION_WORK_ITEM, Configuration.REMEDIATION_WORK_ITEM_EMAIL_TEMPLATE, sysConfig);
            addMailSettingFromSysConfig(MessageKeys.EMAIL_TEMPLATE_CERTIFICATION_REMINDER, Configuration.CERTIFICATION_REMINDER_EMAIL_TEMPLATE, sysConfig);
            addMailSettingFromSysConfig(MessageKeys.EMAIL_TEMPLATE_POLICY_VIOLATION_DELEGATION, Configuration.POLICY_VIOLATION_DELEGATION_EMAIL_TEMPLATE, sysConfig);
            addMailSettingFromSysConfig(MessageKeys.EMAIL_TEMPLATE_OPEN_CERTIFICATIONS, Configuration.OPEN_CERTIFICATIONS_EMAIL_TEMPLATE, sysConfig);
            addMailSettingFromSysConfig(MessageKeys.EMAIL_TEMPLATE_ACCESS_REQUEST_REMINDER, Configuration.ACCESS_REQUEST_REMINDER_EMAIL_TEMPLATE, sysConfig);
            addMailSettingFromSysConfig(MessageKeys.EMAIL_TEMPLATE_SUNSET_EXPIRATION_REMINDER, Configuration.SUNSET_EXPIRATION_REMINDER_EMAIL_TEMPLATE, sysConfig);
        }

        private void addMailSettingFromSysConfig(String messageKey, String configKey, Configuration sysConfig) {

            MailSetting setting = new MailSetting();
            setting.setConfigKey(configKey);
            setting.setName(WebUtil.localizeMessage(messageKey));
            setting.setValue(sysConfig.getString(configKey));
            settings.add(setting);
        }
        
        private void save(Configuration sysConfig) {
            
            for (MailSetting setting : settings) {
                
                sysConfig.put(setting.getConfigKey(), setting.getValue());
            }
        }
    }
    
    private void auditPasswordPolicyChanges(Attributes<String,Object> original,
                                           Attributes<String,Object> attributes) 
                                                   throws GeneralException {

        if (Auditor.isEnabled(AuditEvent.PasswordPolicyChange)) {

            AuditEvent event = new AuditEvent();
            event.setAction(AuditEvent.PasswordPolicyChange);
            event.setSource(getLoggedInUserName());
            event.setInterface(Source.UI.toString());
            event.setAttributeName("op");
            event.setAttributeValue(AuditEvent.ActionUpdate);
            
            for (int i = 0; i < PASSWORD_POLICY_ATTRIBUTE_NAMES.length; ++i) {
                addAttributeChangeToAuditEvent(event, 
                        PASSWORD_POLICY_ATTRIBUTE_NAMES[i][0], 
                        PASSWORD_POLICY_ATTRIBUTE_NAMES[i][1], 
                        original, attributes);
            }
            
            if (!Util.isEmpty(event.getAttributes())) {
                Auditor.log(event);
            }
        }
    }

    public static void addAttributeChangeToAuditEvent(AuditEvent event,
                                                String passwordPolicyAttributeName,
                                                String passwordPolicyAttributeDisplay,
                                                Map<String,Object> original,
                                                Map<String,Object> attributes) {
        Object originalValue = original.get(passwordPolicyAttributeName);
        Object newValue = attributes.get(passwordPolicyAttributeName);
        
        if (!Util.nullSafeEq(originalValue, newValue, true, true)) {
            event.setAttribute(
                    passwordPolicyAttributeDisplay, 
                    new Message(Message.Type.Info, MessageKeys.AUDIT_ACTION_PASSWORD_POLICY_VALUE_UPDATE, 
                            originalValue, newValue));
        }
    }

    public boolean isForceRoleMergeTemplate() {
        return forceRoleMergeTemplate;
    }

    public void setForceRoleMergeTemplate(boolean forceRoleMergeTemplate) {
        this.forceRoleMergeTemplate = forceRoleMergeTemplate;
    }

    public boolean isPamProvisioningEnabled() {
        return this.pamProvisioningEnabled;
    }

    public void setPamProvisioningEnabled(boolean pamProvisioningEnabled) {
        this.pamProvisioningEnabled = pamProvisioningEnabled;
    }

    public boolean isPamModifyPrivilegedDataEnabled() { return this.pamModifyPrivilegedDataEnabled; }

    public void setPamModifyPrivilegedDataEnabled(boolean pamModifyPrivilegedDataEnabled) {
        this.pamModifyPrivilegedDataEnabled = pamModifyPrivilegedDataEnabled;
    }

    public boolean isPamOwnerCanEditContainer() {
        return this.pamOwnerCanEditContainer;
    }

    public void setPamOwnerCanEditContainer(boolean pamOwnerCanEditContainer) {
        this.pamOwnerCanEditContainer = pamOwnerCanEditContainer;
    }

    public boolean isPamCreateContainerEnabled() {
        return this.pamCreateContainerEnabled;
    }

    public void setPamCreateContainerEnabled(boolean pamCreateContainerEnabled) {
        this.pamCreateContainerEnabled = pamCreateContainerEnabled;
    }

    public int getPamMaxSelectableIdentities() {
        return this.pamMaxSelectableIdentities;
    }

    public void setPamMaxSelectableIdentities(int pamMaxSelectableIdentities) {
        this.pamMaxSelectableIdentities = pamMaxSelectableIdentities;
    }

    public String getPamWorkflowIdentityProvisioning() {
        return pamWorkflowIdentityProvisioning;
    }

    public void setPamWorkflowIdentityProvisioning(String pamWorkflowIdentityProvisioning) {
        this.pamWorkflowIdentityProvisioning = pamWorkflowIdentityProvisioning;
    }

    public String getPamPrivilegedItemSelectorRule() {
        return pamPrivilegedItemSelectorRule;
    }

    public void setPamPrivilegedItemSelectorRule(String pamPrivilegedItemSelectorRule) {
        this.pamPrivilegedItemSelectorRule = pamPrivilegedItemSelectorRule;
    }

    // ATTACHMENTS CONFIG
    public boolean isAttachmentsEnabled() {
        return attachmentsEnabled;
    }

    public void setAttachmentsEnabled(boolean attachmentsEnabled) {
        this.attachmentsEnabled = attachmentsEnabled;
    }

    public int getAttachmentsMaxFileSize() {
        return attachmentsMaxFileSize;
    }

    public void setAttachmentsMaxFileSize(int attachmentsMaxFileSize) {
        this.attachmentsMaxFileSize = attachmentsMaxFileSize;
    }

    // Require comments for access requests
    public boolean isRequireAccessRequestCommentsAll() {
        return requireAccessRequestCommentsAll;
    }

    public void setRequireAccessRequestCommentsAll(boolean requireCommentsAll) {
        this.requireAccessRequestCommentsAll = requireCommentsAll;
    }

    // Edit Preferences Settings
    public boolean isShowEditForwarding() {
        return showEditForwarding;
    }

    public void setShowEditForwarding(boolean showEditForwarding) {
        this.showEditForwarding = showEditForwarding;
    }

    public boolean isShowChangePasswordTab() {
        return showChangePasswordTab;
    }

    public void setShowChangePasswordTab(boolean showChangePasswordTab) {
        this.showChangePasswordTab = showChangePasswordTab;
    }

    /**
     * Need to transform json list into csv list
     * @return
     */
    public String getAttachmentsAllowedFileTypes() throws GeneralException{
        List<String> allowedFileTypes = JsonHelper.listFromJson(String.class,
                attachmentsAllowedFileTypes);
        return Util.listToCsv(allowedFileTypes);
    }

    public void setAttachmentsAllowedFileTypes(String attachmentsAllowedFileTypes) {
        List<String> allowedFileTypes = Util.csvToList(attachmentsAllowedFileTypes);
        // do not allow special chars
        List<String> cleanFileTypes = new ArrayList<>();
        for (String fileType : allowedFileTypes) {
            if (Util.isNotNullOrEmpty(fileType)) {
                String cleanFile = fileType.replaceAll("[^\\w]","");
                if (Util.isNotNullOrEmpty(cleanFile)) {
                    cleanFileTypes.add(cleanFile);
                }
            }
        }

        this.attachmentsAllowedFileTypes = JsonHelper.toJson(cleanFileTypes);
    }

    public String getAttachmentsFilenameAllowedSpecialCharacters() {
        return attachmentsFilenameAllowedSpecialCharacters;
    }

    public void setAttachmentsFilenameAllowedSpecialCharacters(String attachmentsFilenameAllowedSpecialCharacters) {
        this.attachmentsFilenameAllowedSpecialCharacters = attachmentsFilenameAllowedSpecialCharacters;
    }

    /**
     * Gets the status of the Recommender.
     *
     * @return True if a recommender exists & and is configured. Otherwise false.
     */
    public boolean isRecommenderConfigured() { return this.isRecommenderConfigured; }
}
