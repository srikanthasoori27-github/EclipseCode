/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * A singleton object containing global system configuration options.
 * Configuration for several system components have been split
 * out into their own objects: ObjectConfig, UIConfig, RuleRegistry.
 * This object is for random things that don't really warrant
 * their own object.
 * Author: Jeff
 */
package sailpoint.object;

import java.util.Date;
import java.util.List;

import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;

/**
 * Class to hold extensible configuration parameters.
 *
 * An instance of this class named "SystemConfiguration" holds
 * many configuration parameters presented in the UI
 * under the "System Setup" tab.
 *
 * Most of the name constants defined in this class are key
 * names in the system configuration object.
 */
@XMLClass
public class Configuration extends SailPointObject implements Cloneable, Cacheable {

    //private static Log log = LogFactory.getLog(Configuration.class);

    //////////////////////////////////////////////////////////////////////
    //
    // Constants
    //
    //////////////////////////////////////////////////////////////////////

    private static final long serialVersionUID = -4035364793343125345L;

    /**
     * The name of the singleton system configuration object.
     */
    public static final String OBJ_NAME = "SystemConfiguration";

    /**
     * The name of the configuration for electronic signatures.
     */
    public static final String ELECTRONIC_SIGNATURE = "ElectronicSignature";

    /**
     * The name of the singleton identity filter configuration object
     */
    public static final String IDENTITY_SELECTOR_CONFIG = "IdentitySelectorConfiguration";

    /**
     * The name of the singleton configuration object for the keys
     * used during secure data transmission. 
     */
    public static final String IQ_SERVICE_CONFIG = "IQServiceRPCConfiguration";
    
    /**
     * The name of the configuration for SAML SSO
     */
    public static final String SAML = "SAML";

    /**
     * The name of the system config key to hold the Rule Based SSO enabled state.
     */
    public static final String RULE_BASED_SSO_ENABLED = "RuleBasedSSOEnabled";

    /**
     * The name of the system config key to hold the SAML enabled state.
     */
    public static final String SAML_ENABLED = "SAMLEnabled";
    
    /**
     * The name of the SAML provider. Currently, only one provider is supported.  
     */
    public static final String SAML_PROVIDER = "IdentityNow";
    
    /**
     * Allows an override to SAML SPNameQualifier setting normally set to the entityId.
     * @ignore
     * @see sailpoint.web.sso.SAMLSSOAuthenticator.phase1GetHandler for details
     */
    public static final String SAML_NAMEQUALIFIER = "spNameQualifier";
    
    /**
     * The name of the configuration for defining what rights have access to what URL patterns
     */
    public static final String WEB_RESOURCE_CONFIG = "WebResource";

    /**
     * The name of the configuration for MFA
     */
    public static final String MFA = "MFAConfiguration";
    
    /**
     * The name of the List of MFA config items in the configuration for MFA
     */
    public static final String MFA_CONFIG_LIST = "MFAConfigs";


    //
    // WebServices properties
    //
    // WebServices Config - the hostname where web services can be reached.
    public static final String WEB_SERVICE_CONFIG_HOSTNAME= "hostname";

    // WebServices Config - OAuth client id
    public static final String WEB_SERVICE_CONFIG_CLIENT_ID = "clientId";

    // WebServices Config - OAuth client secret
    public static final String WEB_SERVICE_CONFIG_CLIENT_SECRET = "clientSecret";

    public static final String WEB_SERVICE_CONFIG_OAUTH_PATH = "oauthPath";

    public static final String WEB_SERVICE_CONFIG_OAUTH_CLIENT_USE_QUERY_PARAMS = "useQueryParamForClientAuth";

    // WebServices Config - BasicAuth userName
    public static final String WEB_SERVICE_CONFIG_USER_NAME = "userName";

    // WebServices Config - BasicAuth password
    public static final String WEB_SERVICE_CONFIG_PASSWORD = "password";

    // WebServices Config - socket read timeout
    public static final String WEB_SERVICE_CONFIG_READ_TIMEOUT_SECS = "readTimeoutSeconds";

    // WebServices Config - socket connect timeout
    public static final String WEB_SERVICE_CONFIG_CONNECT_TIMEOUT_SECS = "connectTimeoutSeconds";


    // WebServices Config - (hidden) the interval (in milliseconds) to look for stale connections
    public static final String WEB_SERVICE_CONFIG_IDLE_CONNECTION_REFRESH_INTERVAL = "connectionRefreshInterval";

    // WebServices Config - (hidden) the interval (in milliseconds) to check for failed token
    public static final String WEB_SERVICE_CONFIG_FAILED_TOKEN_CHECK_INTERVAL = "failedTokenCheckInterval";

    // WebServices Config - (hidden) the percentage of delta time until expiry to wait until trying to get new token
    public static final String WEB_SERVICE_CONFIG_TOKEN_REFRESH_PERCENT = "tokenRefreshPercent";



    //
    // IdentityAI properties
    //

    // IdentityAI Configuration object name
    public static final String IAI_CONFIG = "IdentityAIConfiguration";

    // IdentityAI Config - the hostname where IdentityAI can be reached.
    public static final String IAI_CONFIG_HOSTNAME= WEB_SERVICE_CONFIG_HOSTNAME;

    // IdentityAI Config - OAuth client id
    public static final String IAI_CONFIG_CLIENT_ID = WEB_SERVICE_CONFIG_CLIENT_ID;

    // IdentityAI Config - OAuth client secret
    public static final String IAI_CONFIG_CLIENT_SECRET = WEB_SERVICE_CONFIG_CLIENT_SECRET;

    // IdentityAI Config - socket read timeout
    public static final String IAI_CONFIG_READ_TIMEOUT_SECS = WEB_SERVICE_CONFIG_READ_TIMEOUT_SECS;

    // IdentityAI Config - socket connect timeout
    public static final String IAI_CONFIG_CONNECT_TIMEOUT_SECS = WEB_SERVICE_CONFIG_CONNECT_TIMEOUT_SECS;


    // IdentityAI Config - (hidden) the interval (in milliseconds) to look for stale connections
    public static final String IAI_CONFIG_IDLE_CONNECTION_REFRESH_INTERVAL = WEB_SERVICE_CONFIG_IDLE_CONNECTION_REFRESH_INTERVAL;

    // IdentityAI Config - (hidden) the interval (in milliseconds) to check for failed token
    public static final String IAI_CONFIG_FAILED_TOKEN_CHECK_INTERVAL = WEB_SERVICE_CONFIG_FAILED_TOKEN_CHECK_INTERVAL;

    // IdentityAI Config - (hidden) the percentage of delta time until expiry to wait until trying to get new token
    public static final String IAI_CONFIG_TOKEN_REFRESH_PERCENT = WEB_SERVICE_CONFIG_TOKEN_REFRESH_PERCENT;

    // IdentityAI Config - (hidden) the endpoint of the recommendations API
    public static final String IAI_CONFIG_RECO_ENDPOINT = "recommendationsEndpoint";

    // IdentityAI Config - (hidden) the endpoint of the recommendations catalog API
    public static final String IAI_CONFIG_RECO_CATALOG_ENDPOINT = "recommendationsCatalogEndpoint";

    /*
     * Configuration attribute (long) to configure the Reason Message Catalog Cache.
     * Time to Live is measured in milliseconds and the default is 86400000 (24 hours) or
     * {@link RecommenderResourceBundleControl#DEFAULT_CACHE_EXPIRATION} 
     */
    public static final String ATT_CATALOG_CACHE_TTL_MILLIS = "catalogCacheTTLMillis";

    // identityAI Config - (hidden) should we ignore HTTP 429 responses, and just treat as error?
    public static final String IAI_CONFIG_IGNORE_RATE_LIMIT = "ignoreRateLimit";


    //
    // RapidSetup properties
    //

    // RapidSetup Configuration object name
    public static final String RAPIDSETUP_CONFIG = "RapidSetupConfiguration";

    // RapidSetup Configuration key for applications section
    public static final String RAPIDSETUP_CONFIG_SECTION_APPS = "applications";

    // RapidSetup Configuration key for supportedProcesses String list
    public static final String RAPIDSETUP_CONFIG_SUPT_PROC = "supportedProcesses";

    // RapidSetup Configuration key for businessProcesses section
    public static final String RAPIDSETUP_CONFIG_SECTION_BUSINESS_PROCESSES = "businessProcesses";

    // RapidSetup Configuration key for birthright section
    public static final String RAPIDSETUP_CONFIG_BIRTHRIGHT = "birthright";

    // RapidSetup Configuration key for workflow section
    public static final String RAPIDSETUP_CONFIG_SECTION_WORKFLOW = "workflow";

    // RapidSetup Configuration key for email section
    public static final String RAPIDSETUP_CONFIG_SECTION_EMAIL = "email";

    // RapidSetup Configuration key for email section
    public static final String RAPIDSETUP_CONFIG_SECTION_EMAIL_ALT_MANAGER = "altManager";

    // RapidSetup Configuration key for email section
    public static final String RAPIDSETUP_CONFIG_SECTION_EMAIL_ERROR_WORKGROUP = "errorNotificationWorkGroup";

    // RapidSetup Configuration key and suggest id for style sheet
    public static final String RAPIDSETUP_CONFIG_SECTION_EMAIL_STYLE_SHEET = "styleSheet";
    public static final String RAPIDSETUP_CONFIG_SECTION_EMAIL_STYLE_SHEET_SUGGEST = "styleSheetEmailTemplate";

    // RapidSetup Configuration key for email section
    public static final String RAPIDSETUP_CONFIG_SECTION_EMAIL_HEADER = "headerTemplate";
    public static final String RAPIDSETUP_CONFIG_SECTION_EMAIL_HEADER_SUGGEST = "headerTemplateEmail";

    // RapidSetup Configuration key and suggest id for email section
    public static final String RAPIDSETUP_CONFIG_SECTION_EMAIL_FOOTER = "footerTemplate";
    public static final String RAPIDSETUP_CONFIG_SECTION_EMAIL_FOOTER_SUGGEST = "footerTemplateEmail";

    // RapidSetup Configuation key for birthright roletypes
    public static final String RAPIDSETUP_CONFIG_BIRTHRIGHT_ROLETYPES = "roleTypes";

    // RapidSetup Configuration key for workflow requester
    public static final String RAPIDSETUP_CONFIG_WORKFLOW_REQUESTER = "requester";

    // RapidSetup Configuration key for disabled attribute
    public static final String RAPIDSETUP_CONFIG_ENABLED = "enabled";

    // RapidSetup Configuration key for businessProcesses section
    public static final String RAPIDSETUP_CONFIG_PARAM_TRIGGER_FILTER = "triggerFilter";

    public static final String RAPIDSETUP_CONFIG_PARAM_GROUP = "group";
    public static final String RAPIDSETUP_CONFIG_PARAM_ITEMS = "items";

    // RapidSetup Configuration key for a businessProcesses sections workflow
    public static final String RAPIDSETUP_CONFIG_PARAM_TRIGGER_WORKFLOW = "triggerWorkflow";
    // RapidSetup Configuration suggest id for a businessProcesses sections workflow
    public static final String RAPIDSETUP_CONFIG_JOINER_WORKFLOW_SUGGEST = "joinerWorkflow";

    // RapidSetup Configuration key for the optional selector to respect before doing bare  provisioning
    public static final String RAPIDSETUP_CONFIG_JOINER_PROV_SELECTOR = "identitySelector";

    // RapidSetup Configuration key for the optional selector to respect before doing bare  provisioning
    public static final String RAPIDSETUP_CONFIG_JOINER_PROV_SELECTOR_DTO = "identSelectorDTO";

    // RapidSetup Configuration key for the joiner config of an application or global
    public static final String RAPIDSETUP_CONFIG_JOINER = "joiner";

    // RapidSetup Configuration key for the leaver config of an application or global
    public static final String RAPIDSETUP_CONFIG_LEAVER = "leaver";

    // RapidSetup Configuration key for the global leaver config when running via
    // terminate identity
    public static final String RAPIDSETUP_CONFIG_TERMINATE = "terminate";

    // RapidSetup Configuration key for the global sections that contain email subsections
    public static final String RAPIDSETUP_CONFIG_EMAIL = "email";

    public static final String RAPIDSETUP_CONFIG_LEAVER_WORKFLOW_SUGGEST = "leaverWorkflow";

    // RapidSetup Configuration key - whether identities are required to be correlated for leaver.
    public static final String RAPIDSETUP_CONFIG_LEAVER_REQUIRE_CORRELATED = "requireCorrelated";

    // RapidSetup Configuration key - whether identities with uncorrelated accounts should be excluded from joiner.
    public static final String RAPIDSETUP_CONFIG_JOINER_REQUIRE_CORRELATED = "requireCorrelated";

    // RapidSetup Configuration key - whether identities with uncorrelated accounts should be excluded from mover.
    public static final String RAPIDSETUP_CONFIG_MOVER_REQUIRE_CORRELATED = "requireCorrelated";

    // RapidSetup Configuration key - whether identities are automatically joined if new AND empty
    public static final String RAPIDSETUP_CONFIG_JOINER_AUTO_JOIN_NEW_EMPTY = "autoJoinNewEmpty";

    // RapidSetup Configuration key - whether identities which fail joiner trigger filter should be reprocessed
    public static final String RAPIDSETUP_CONFIG_JOINER_REPROCESS_SKIPPED = "reprocessSkipped";

    // RapidSetup Configuration key and suggest id for the leaver config, identifies the post leaver rule to evaluate
    public static final String RAPIDSETUP_CONFIG_LEAVER_POST_RULE = "postLeaverRule";
    public static final String RAPIDSETUP_CONFIG_LEAVER_POST_RULE_SUGGEST = "postLeaverRuleSuggest";

    // RapidSetup Configuration key and suggest id for the leaver completed email template
    public static final String RAPIDSETUP_CONFIG_LEAVER_COMPLETED = "leaverCompleted";
    public static final String RAPIDSETUP_CONFIG_LEAVER_COMPLETED_SUGGEST = "leaverCompletedEmailTemplateSuggest";

    // RapidSetup Configuration key and suggest id for the leaver ownership reassignment email template
    public static final String RAPIDSETUP_CONFIG_LEAVER_OWNERSHIP_REASSIGNMENT = "ownershipReassignment";
    public static final String RAPIDSETUP_CONFIG_LEAVER_OWNERSHIP_REASSIGNMENT_SUGGEST = "ownershipReassignmentEmailTemplateSuggest";

    // RapidSetup Configuration key and suggest id for the leaver notification workgroup 
    public static final String RAPIDSETUP_CONFIG_LEAVER_ALT_NOTIFY_WORKGROUP = "altNotifyWorkgroup";
    public static final String RAPIDSETUP_CONFIG_LEAVER_ALT_NOTIFY_WORKGROUP_SUGGEST = "altNotifyWorkgroupSuggest";

    // RapidSetup Configuration key for the leaver config, identifies the identity to reassign to if no manager is found
    public static final String RAPIDSETUP_CONFIG_LEAVER_REASSIGN_TO_IF_NO_MANAGER = "reassignToIfNoManager";

    // RapidSetup Configuration key for reassignIdentities
    public static final String RAPIDSETUP_CONFIG_LEAVER_REASSIGN_IDENTITIES = "reassignIdentities";

    // RapidSetup Configuration keys and suggest ids in reassignIdentities key
    public static final String RAPIDSETUP_CONFIG_LEAVER_ENABLE_REASSIGN_IDENTITIES = "enableReassignment";
    public static final String RAPIDSETUP_CONFIG_LEAVER_REASSIGN_IDENTITIES_TO_MANAGER = "reassignToManager";
    public static final String RAPIDSETUP_CONFIG_LEAVER_REASSIGN_ALT_IDENTITIES_SUGGEST = "reassignIdentitiesAlternativeSuggest";
    public static final String RAPIDSETUP_CONFIG_LEAVER_REASSIGN_IDENTITIES_RULE_SUGGEST = "reassignIdentitiesRuleSuggest";

    // RapidSetup Configuration key and suggest id, leaver config - identifies the alternative if reassign to manager is false
    // used in both reassignArtifacts and reassignIdentities
    public static final String RAPIDSETUP_CONFIG_LEAVER_REASSIGN_ALTERNATIVE = "reassignAlternative";

    // RapidSetup Configuration key for reassignArtifacts
    public static final String RAPIDSETUP_CONFIG_LEAVER_REASSIGN_ARTIFACTS = "reassignArtifacts";

    // RapidSetup Configuration keys and suggest ids in reassignArtifacts key
    public static final String RAPIDSETUP_CONFIG_LEAVER_ENABLE_REASSIGN_ARTIFACTS = "enableReassignment";
    public static final String RAPIDSETUP_CONFIG_LEAVER_REASSIGN_ARTIFACTS_TO_MANAGER = "reassignToManager";
    public static final String RAPIDSETUP_CONFIG_LEAVER_REASSIGN_ALT_ARTIFACTS_SUGGEST = "reassignArtifactAlternativeSuggest";
    public static final String RAPIDSETUP_CONFIG_LEAVER_REASSIGN_ARTIFACTS_RULE_SUGGEST = "reassignArtifactRuleSuggest";
    public static final String RAPIDSETUP_CONFIG_LEAVER_REASSIGN_ARTIFACT_TYPES = "reassignArtifactTypes";

    // RapidSetup Configuration key for the leaver config. Used in reassignArtifacts and reassignIdentities.
    public static final String RAPIDSETUP_CONFIG_LEAVER_REASSIGN_RULE = "reassignRule";

    // RapidSetup Configuration key for the leaver config, identifies the filter to use for exceptions to entitlement removal
    public static final String RAPIDSETUP_CONFIG_LEAVER_ENTITLEMENT_EXCEPTION = "entitlementException";

    // RapidSetup Configuration key for the mover config of an application or global
    public static final String RAPIDSETUP_CONFIG_MOVER = "mover";

    // RapidSetup Configuration key which indicates if application supports bare account provisioning
    public static final String RAPIDSETUP_CONFIG_JOINER_BARE_PROV = "enableBareProvisioning";

    // RapidSetup Configuration key for the joiner global config to support notifying manager temporary password
    public static final String RAPIDSETUP_CONFIG_JOINER_SEND_TEMPORARY_PASSWORD_EMAIL = "sendTemporaryPasswordEmail";

    // RapidSetup Configuration key for the joiner global config to support joiner email
    public static final String RAPIDSETUP_CONFIG_JOINER_EMAIL = "email";

    // RapidSetup Configuration key for the joiner global config to support joiner completed email template
    public static final String RAPIDSETUP_CONFIG_JOINER_COMPLETED_EMAIL_TEMPLATE = "joinerCompleted";
    // RapidSetup Configuration suggest id for the joiner global config to support joiner completed email template
    public static final String RAPIDSETUP_CONFIG_JOINER_COMPLETED_EMAIL_TEMPLATE_SUGGEST = "completedEmailTemplate";

    // RapidSetup Configuration key for the joiner global config to send joiner email to workgroup instead of manager
    public static final String RAPIDSETUP_CONFIG_JOINER_ALT_NOTIFY_WORKGROUP = "altNotifyWorkgroup";
    public static final String RAPIDSETUP_CONFIG_JOINER_ALT_NOTIFY_WORKGROUP_SUGGEST = "altJoinerNotifyWorkgroupSuggest";

    public static final String RAPIDSETUP_CONFIG_JOINER_POST_JOINER_RULE = "postJoinerRule";
    public static final String RAPIDSETUP_CONFIG_JOINER_POST_JOINER_RULE_SUGGEST = "postJoinerRuleSuggest";

    // RapidSetup Configuration key which indicates if application's newly created identities should
    // be sent to joiner
    public static final String RAPIDSETUP_CONFIG_JOINER_PRODUCING = "isJoinerProducer";

    // RapidSetup Configuration key which indicates if an application mover certification should include additional entitlements
    public static final String RAPIDSETUP_CONFIG_MOVER_CERTIFICATION_ADDITIONAL_ENTITLEMENTS = "moverCertAdditionalEntitlements";

    // RapidSetup Configuration key which indicates if an application mover certification should include target permission
    public static final String RAPIDSETUP_CONFIG_MOVER_CERTIFICATION_TARGET_PERMISSION = "moverCertTargetPermission";

    // RapidSetup Configuration key which indicates if an application should perform a certification on mover
    public static final String RAPIDSETUP_CONFIG_MOVER_PERFORM_JOINER = "performJoiner";

    // RapidSetup Configuration key which holds mover option Perform Certification
    public static final String RAPIDSETUP_CONFIG_MOVER_CERTIFICATION_PARAMS = "certificationParams";

    // RapidSetup Configuration key which holds value that turns cert on or off for mover workflow
    public static final String RAPIDSETUP_CONFIG_MOVER_CERTIFICATION_ENABLED = "certificationEnabled";

    // RapidSetup Configuration key which holds value that turns joiner processing on or off for mover workflow
    public static final String RAPIDSETUP_CONFIG_MOVER_JOINER_ENABLED = "joinerEnabled";

    // RapidSetup Configuration key which holds the identity name of the certification owner
    public static final String RAPIDSETUP_CONFIG_MOVER_CERTIFICATION_OWNER = "certificationOwner";

    // RapidSetup Configuration key which holds the identity name of the backup certifier
    public static final String RAPIDSETUP_CONFIG_MOVER_BACKUP_CERTIFIER = "backupCertifier";

    public static final String RAPIDSETUP_CONFIG_MOVER_WORKFLOW_SUGGEST = "moverWorkflow";

    public static final String RAPIDSETUP_CONFIG_MOVER_POST_MOVER_RULE = "postMoverRule";
    public static final String RAPIDSETUP_CONFIG_MOVER_POST_MOVER_RULE_SUGGEST = "postMoverRuleSuggest";

    // File Access Manager Configuration object name
    public static final String FAM_CONFIG = "FAMConfiguration";

    // File Access Manager URL
    public static final String FAM_CONFIG_URL = WEB_SERVICE_CONFIG_HOSTNAME;

    // File Access Manager Auth Type
    public static final String FAM_CONFIG_AUTH_TYPE = "authType";

    // File Access Manager URL
    public static final String FAM_CONFIG_CLIENT_ID = WEB_SERVICE_CONFIG_CLIENT_ID;

    // File Access Manager Client Secret
    public static final String FAM_CONFIG_CLIENT_SECRET = WEB_SERVICE_CONFIG_CLIENT_SECRET;
 
    // File Access Manager User Name
    public static final String FAM_CONFIG_USER_NAME = WEB_SERVICE_CONFIG_USER_NAME;
 
    // File Access Manager Password
    public static final String FAM_CONFIG_PASSWORD = WEB_SERVICE_CONFIG_PASSWORD;

    // File Access Manager Correlated Applications
    public static final String FAM_CONFIG_SCIM_CORRELATION_APPS = "scimCorrelationApplications";

    // File Access Manager Correlation Rule
    public static final String FAM_CONFIG_SCIM_CORRELATION_RULE = "scimCorrelationRule";

    // File Access Manager Menu Path
    public static final String FAM_CONFIG_MENU_PATH = "menuPath";

    //
    // Misc Properties
    //

    public static final String DASHBOARD_MAX_CERT_PERCENTS = "dashboardMaxCertPercents";
    public static final String DASHBOARD_MAX_CERT_PERCENT_APPLICATIONS = "dashboardMaxCertPercentApplications";
    public static final String DASHBOARD_MAX_CERT_PERCENT_GROUPS = "dashboardMaxCertPercentGroups";
    public static final String ENTITLEMENT_MINING_MAX_APP_BUCKETS = "entitlementMiningMaxAppBuckets";
    public static final String ENTITLEMENT_MINING_MAX_BUCKETS = "entitlementMiningMaxBuckets";
    public static final String APPLICATION_TEMPLATE_ACCOUNT_USAGES = "applicationTemplateAccountUsages";

    public static final String SEARCH_INPUT_DEFINITIONS = "searchInputDefinitions";

    /**
     * Holds the path to the custom.css for the CodeMirror editor.
     * (e.g. scripts/codemirror-x.x/theme/custom.css)
     */
    public static final String CUSTOM_THEME_CODE_MIRROR = "customThemeCodeMirror";

    public static final String DISABLE_ROLE_MODELER_TREE_VIEW = "disableRoleModelerTreeView";
    public static final String BUNDLES_UI_DISPLAY_LIMIT = "bundlesUIDisplayLimit";
    public static final String SCOPE_TREE_UI_DISPLAY_LIMIT = "scopeTreeUIDisplayLimit";

    //Accelerator Pack
    public static final String ACCELERATOR_PACK_VERSION = "acceleratorPackVersion";

    //
    // Certification
    //

    /**
     * Interval at which commits and hibernate decache occurs while processing
     * entities during certification generation. Setting it lower
     * will reduce memory consumption, but might increase certification
     * generation time. Lowering the value might help avoid OutOfMemoryExceptions
     * in cases where individual entities include 100s or 1000s or individual
     * certification items. If this property is not set, the certificationer will flush
     * every 100 entities.
     */
    public static final String CERTIFICATION_ENTITY_DECACHE_INTERVAL =
        "certificationEntityDecacheInterval";

    /**
     * A flag that tells certification generation *not* to look in
     * CertificationArchives to calculate whether there are any differences
     * since the last certification. This should not be required for
     * certifications that were finished more recently, but older certifications
     * could require this. Enabling this can prevent a performance hit when an
     * archive has to be decompressed, but all certification items might show up
     * as "new user".
     */
    public static final String CERTIFICATION_DIFFERENCING_NO_ARCHIVE_SEARCHING =
        "certificationDifferencingNoArchiveSearching";
    
    /**
     * Name of a configuration attribute that controls whether it is required
     * that all delegated CertificationItems be reviewed by the original
     * certifier before the Certification can be completed.
     */
    public static final String CERTIFICATION_DELEGATION_REVIEW =
        "certificationDelegationReview";

    /**
     * Name of a configuration attribute that controls whether delegation from
     * a certification must be fully decided upon before completing it.
     */
    public static final String REQUIRE_DELEGATION_COMPLETION =
        "requireDelegationCompletion";

    /**
     * Name of a Rule to run on newly created CertificationItems to customize
     * them.
     */
    public static final String CERTIFICATION_ITEM_CUSTOMIZATION_RULE =
        "certificationItemCustomizationRule";

    /**
     * Name of a Rule to run when refreshing the completion status of a
     * certification item to determine whether it is complete.
     */
    public static final String CERTIFICATION_ITEM_COMPLETION_RULE =
        "certificationItemCompletionRule";

    /**
     * Name of a Rule to run when refreshing the completion status of a
     * certification entity to determine whether it is complete.
     */
    public static final String CERTIFICATION_ENTITY_COMPLETION_RULE =
        "certificationEntityCompletionRule";

    /**
     * Name of a Rule to run on newly created CertificationEntities to customize
     * them.
     */
    public static final String CERTIFICATION_ENTITY_CUSTOMIZATION_RULE =
        "certificationEntityCustomizationRule";

    /**
     * Name of a Rule to run on when refreshing CertificationEntities to allow
     * modifying the refresh behavior. For example, pushing a custom field from
     * the CertificationEntity on the items.
     */
    public static final String CERTIFICATION_ENTITY_REFRESH_RULE =
        "certificationEntityRefreshRule";

    /**
     * Rules to run when entering/exiting different phases of certifications
     */
    public static final String CERTIFICATION_ACTIVE_PHASE_ENTER_RULE =
        "certificationActivePhaseEnterRule";
    public static final String CERTIFICATION_ACTIVE_PHASE_EXIT_RULE =
        "certificationActivePhaseExitRule";
    public static final String CERTIFICATION_CHALLENGE_PHASE_ENTER_RULE_LEGACY =
        "certificationChallengeChallengeEnterRule";
    public static final String CERTIFICATION_CHALLENGE_PHASE_ENTER_RULE =
        "certificationChallengePhaseEnterRule";
    public static final String CERTIFICATION_CHALLENGE_PHASE_EXIT_RULE =
        "certificationChallengePhaseExitRule";
    public static final String CERTIFICATION_REMEDIATION_PHASE_ENTER_RULE =
        "certificationRemediationPhaseEnterRule";
    public static final String CERTIFICATION_REMEDIATION_PHASE_EXIT_RULE =
        "certificationRemediationPhaseExitRule";
    public static final String CERTIFICATION_FINISH_PHASE_ENTER_RULE =
        "certificationFinishPhaseEnterRule";

    /**
     * Option to send email notifications to users when one of their items is
     * remediated.
     */
    public static final String NOTIFY_REMEDIATION =
        "notifyRemediation";

    /**
     * Name of a configuration attribute that controls whether child
     * reassignment certifications must be completed before the parent certification
     * can be completed. This is similar to COMPLETE_CERTIFICATION_HIERARCHY
     * except that this applies to reassignment certifications only, where the hierarchy
     * option applies to hierarchical manager certifications.
     */
    public static final String REQUIRE_REASSIGNMENT_COMPLETION =
        "requireReassignmentCompletion";

    /**
     * Name of a configuration attribute that controls whether certifications
     * will automatically get signed off if all of the work to be done in a certification
     * is reassigned away. Note that if the REQUIRE_REASSIGNMENT_COMPLETION is enabled
     * this option will effectively be ignored.
     */
    public static final String AUTOMATE_SIGNOFF_ON_REASSIGNMENT =
        "automateSignOffOnReassignment";

    public static final String SUPPRESS_EMAIL_WHEN_NOTHING_TO_CERTIFY = "suppressEmailWhenNothingToCertify";
    public static final String AUTO_SIGN_OFF_WHEN_NOTHING_TO_CERTIFY = "autoSignOffWhenNothingToCertify";
    
    /**
     * Name of the configuration attribute that controls the certification type to
     * use. In most cases this is the standard TaskResult signoff, but when electronic 
     * signatures are used this attribute equals the electronic signature name.
     */
    public static final String ATT_SIGNATURE_TYPE = "certificationSignatureType";
    /**
     * Name of a configuration attribute that controls whether the signoff popup should
     * automatically be displayed on the certification page when a certificate is ready to
     * be signed off on.
     */
    public static final String AUTOMATE_SIGNOFF_POPUP =
        "automateSignOffPopup";

    /**
     * Name of a configuration attribute that controls whether child
     * reassignment certification should have their contents merged into the
     * parent certification and be deleted after they are signed.
     */
    public static final String ASSIMILATE_BULK_REASSIGNMENTS =
        "assimilateBulkReassignments";

    public static final String SUPPRESS_INITIAL_NOTIFICATION = "suppressInitialNotification";


    /**
     * If true, scheduling a certification that might create lots of
     * certifications will require a confirmation.
     */
    public static final String CONFIRM_RISKY_CERTIFICATION_SCHEDULES =
        "confirmRiskyCertificationSchedules";

    /**
     * Whether to show the certification decision icon tooltips or not (defaults
     * to false).
     */
    public static final String SHOW_CERTIFICATION_ICON_TOOLTIPS =
        "showCertificationIconTooltips";

    /**
     * Whether to default to the list items view on the certification page or
     * the list entities view. Note that preferences stored on the identity can
     * override this setting.
     */
    public static final String CERT_PAGE_LIST_ITEMS =
        "certificationPageListItems";

    /**
     * User is directed to the detail view of the first open entity when
     * entering the certification UI. If no open entities exist, the user goes to
     * the worksheet view.
     */
    public static final String CERTIFICATION_DETAILED_VIEW_FILTER_SET =
        "certificationDetailedViewFilterSet";

    /**
     * Whether to use ManagedAttributes instead of extended Link attributes when
     * performing complex remediations with the remediation modification type
     * set to "select". This will likely be true except for legacy installs
     * that still want to use the old style.
     */
    public static final String USE_MANAGED_ATTRIBUTES_FOR_REMEDIATION =
        "useManagedAttributesForRemediation";

    public static final String ALLOW_CREATE_ROLES_FROM_CERTIFICATION =
        "allowCreatingRolesFromCertification";

    /**
     * Used to force using the classic extjs UI for approvals. This is mostly
     * intended when embedding custom form within the approval widget.
     */
    public static final String FORCE_CLASSIC_APPROVAL_UI = 
         "forceClassicApprovalUI";
  
    /**
     * Allow certifier to provision missing role requirements during a certification.
     * This removes the checkbox as a certification creation option, rather than disabling it in
     * the certification ui.
     */
    public static final String ALLOW_PROV_MISSING_REQUIREMENTS = "allowProvisioningMissingRequirements";

    /**
     * Require that the certifier enter comments when approving an item.
     */
    public static final String REQUIRE_APPROVAL_COMMENTS = "requireApprovalComments";

    /**
     * Require that the certifier enter comments when allowing exceptions on an item.
     */
    public static final String REQUIRE_MITIGATION_COMMENTS = "requireMitigationComments";

    /**
     * True if accounts which underlie a composite account should be
     * excluded from certifications.
     */
    public static final String EXCLUDE_BASE_APP_ACCOUNTS = "excludeBaseAppAccounts";

    /**
     * When enabled the builder will filter out the entitlements that are not
     * part of the Logical application's Managed Attributes.
     */
    public static final String FILTER_LOGICAL_ENTITLEMENTS = "filterLogicalEntitlements";

    /**
     * Specifies whether bulk approving from the list view is allowed.
     */
    public static final String ALLOW_LIST_VIEW_BULK_APPROVE =
        "allowListViewBulkApprove";

    /**
     * Specifies whether bulk revoking from the list view is allowed.
     */
    public static final String ALLOW_LIST_VIEW_BULK_REVOKE =
        "allowListViewBulkRevoke";

    /**
     * Specifies whether bulk ACCOUNT revoking from the list view is allowed.
     */
    public static final String ALLOW_LIST_VIEW_BULK_ACCOUNT_REVOKE =
        "allowListViewBulkAccountRevoke";


    /**
     * Specifies whether bulk mitigating from the list view is allowed.
     */
    public static final String ALLOW_LIST_VIEW_BULK_MITIGATE =
        "allowListViewBulkMitigate";

    /**
     * Specifies whether bulk reassigning from the list view is allowed.
     */
    public static final String ALLOW_LIST_VIEW_BULK_REASSIGN =
        "allowListViewBulkReassign";

    /**
     * Specifies whether bulk canceling is allowed.
     */
    public static final String ALLOW_LIST_VIEW_BULK_CLEAR_DECISIONS =
        "allowListViewBulkClearDecisions";

    
    /**
     * Specifies whether bulk setting custom entity fields from the list view is
     * allowed.
     */
    public static final String ALLOW_LIST_VIEW_BULK_SAVE_CUSTOM_ENTITY_FIELDS =
        "allowListViewSaveCustomEntityFields";


    /**
     * Specifies that approve all should be available on the certification
     * entity page.
     */
    public static final String ALLOW_CERT_ENTITY_BULK_APPROVE =
        "allowCertificationEntityBulkApprove";

    /**
     * Specifies that revoke all should be available on the certification
     * entity page.
     */
    public static final String ALLOW_CERT_ENTITY_BULK_REVOCATION =
        "allowCertificationEntityBulkRevocation";

    /**
     * Specifies that revoke all accounts should be available on the
     * certification entity page.
     */
    public static final String ALLOW_CERT_ENTITY_BULK_ACCOUNT_REVOCATION =
        "allowCertificationEntityBulkAccountRevocation";

    /**
     * Specifies whether or not bulk canceling is allowed.
     */
    public static final String ALLOW_CERT_ENTITY_BULK_CLEAR_DECISIONS =
        "allowCertificationEntityBulkClearDecisions";
    
    /**
     * Level where self-certification is allowed.
     */
    public static final String ALLOW_SELF_CERTIFICATION =
        "allowSelfCertification";

    /**
     * Name of the identity that will be the owner of the self certification reassignment as well
     * as delegations violating self certification.  They will be responsible for deciding or distributing items
     * that are moved out of other certs due to violating the self certification level.
     */
    public static final String SELF_CERTIFICATION_VIOLATION_OWNER =
            "selfCertificationViolationOwner";

    /**
     * Whether or not mitigation is allowed as a certification decision.
     */
    public static final String CERTIFICATION_MITIGATION_ENABLED =
        "certificationMitigationEnabled";

    public static final String CERTIFICATION_MITIGATION_POPUP_ENABLED = "certificationMitigationPopupEnabled";

    public static final String CERTIFICATION_MITIGATION_DURATION = "certificationMitigationDuration";
    public static final String CERTIFICATION_MITIGATION_DURATION_AMOUNT = "mitigationDurationAmount";
    public static final String CERTIFICATION_MITIGATION_DURATION_SCALE = "mitigationDurationScale";

    /**
     * Whether or not auto deprovision upon mitigation expiration.
     */
    public static final String CERTIFICATION_MITIGATION_DEPROVISION_ENABLED = "certificationMitigationDeprovisionEnabled";

    /**
     * Whether or not item-level delegation is allowed as a certification
     * decision.
     */
    public static final String CERTIFICATION_ITEM_DELEGATION_ENABLED =
        "certificationItemDelegationEnabled";

    /**
     * Whether or not limit the reassignment.
     */
    public static final String CERTIFICATION_LIMIT_REASSIGNMENTS = "certificationLimitReassignments";

    /**
     * Reassignment Limit.
     */
    public static final String CERTIFICATION_REASSIGNMENT_LIMIT = "certificationReassignmentLimit";

    /**
     * Whether or not entity-level delegation is allowed as a certification
     * decision.
     */
    public static final String CERTIFICATION_ENTITY_DELEGATION_ENABLED =
        "certificationEntityDelegationEnabled";

    /**
     * Whether or not delegation forwarding is disabled.
     */
    public static final String CERTIFICATION_DISABLE_DELEGATION_FORWARDING =
        "certificationDisableDelegationForwarding";

    /**
     * Whether or not to display the auto-remediation dialog for remediations
     * that go through a provisioning system.
     */
    public static final String SHOW_AUTO_REMEDIATION_DIALOG =
        "showAutoRemediationDialog";
    
    /**
     * Whether or not to require comments for certification remediations.
     */
    public static final String REQUIRE_REMEDIATION_COMMENTS =
            "requireRemediationComments";

    /**
     * Whether or not to require comments for certification "Revoke Account" decisions.
     * @deprecated This is unused, use {@link #REQUIRE_REMEDIATION_COMMENTS} instead.
     */
    @Deprecated
    public static final String REQUIRE_ACCOUNT_REVOKE_COMMENTS =
            "requireAccountRevokeComments";
    
    /**
     * Whether to disable defaulting to the application owner for remediations where
     * there is a single application owner.
     */
    public static final String DISABLE_DEFAULT_TO_APP_OWNER_REMEDIATION =
        "disableDefaultToAppOwnerRemediation";

    /**
     * The name of an Identity to use as the default remediator if it cannot
     * otherwise be determine if there is one for remediation requests.
     */
    public static final String DEFAULT_REMEDIATOR =
        "defaultRemediator";

    /**
      * Allow certifier to override the default remediator for a violation. Otherwise the remediator select
      * box is hidden.
      */
     public static final String ENABLE_OVERRIDE_VIOLATION_DEFAULT_REMEDIATOR =
            "enableOverrideViolationDefaultRemediator";
     
     /**
      * Recalculate remediation work item owners after compiling the remediation
      * plan. This has the effect of assigning the work item to the tier application
      * revoker instead of the logical application revoker.
      */
     public static final String REMEDIATE_LOGICAL_APP_TO_TIER = 
             "remediateLogicalAppToTier";
     
     /**
      * Flag used to indicate if the option to disable
      * the IdentityEntitlement adornment that happens during the certification
      * lifecycle should be shown.
      */
     public static final String CERT_SCHEDULE_SHOW_ENTITLEMENT_UPDATE_CHECKBOX =
         "showIdentityEntitlementUpdateCheckBox";

     /**
      * Requires that a certifier confirm their bulk certification decisions even
      * when no additional input information is needed.
      */
     public static final String REQUIRE_BULK_CERTIFY_CONFIRMATION =
             "requireBulkCertifyConfirmation";

     /**
      * Selected item count that will result in confirmation dialog on 
      * bulk revocation
      */
     public static final String BULK_SELECTION_COUNT_FOR_CONFIRMATION = 
             "bulkSelectionCountForConfirmation";
             
    /**
     * The number of items of each type to show per page on the certification
     * entity page before paging. If 0 or less, paging is disabled.
     */
    public static final String CERTIFICATION_ITEM_PAGE_SIZE =
        "certificationItemPageSize";

    /**
     * The number of subordinate certifications to show per page on the
     * certification entity page before paging.
     * If 0 or less, paging is disabled.
     */
    public static final String CERTIFICATION_SUBCERT_PAGE_SIZE =
        "subordinateCertificationPageSize";

    /**
     * Name of a configuration attribute that controls whether certifications
     * should be scheduled for all of a manager's subordinates when one is
     * scheduled for that manager. Mutually exclusive with
     * FLATTEN_CERTIFICATION_MANAGER_HIERARCHY.
     */
    public static final String SUBORDINATE_CERTIFICATION_ENABLED =
        "subordinateCertificationEnabled";

    /**
     * Name of a configuration attribute that controls whether manager
     * certifications should include all of the manager's direct and indirect
     * subordinates. Mutually exclusive with SUBORDINATE_CERTIFICATION_ENABLED.
     */
    public static final String FLATTEN_CERTIFICATION_MANAGER_HIERARCHY =
        "flattenCertificationManagerHierarchy";

    /**
     * Name of a configuration attribute that controls whether
     * sub-certifications need to be completed before the parent certification
     * can be finished (signed off). True means that sub-certifications
     * need to be completed first.
     */
    public static final String COMPLETE_CERTIFICATION_HIERARCHY =
        "completeCertificationHierarchy";

    /**
     * Name of a configuration attribute that controls the granularity at
     * which additional entitlements should be certified. This should hold
     * the name of a BaseCertificationBuilder.EntitlementGranularity.
     */
    public static final String ADDITIONAL_ENTITLEMENT_GRANULARITY =
        "additionalEntitlementGranularity";

    /**
     * Name of a configuration attribute that has the default active period
     * duration (a Duration object).
     */
    public static final String ACTIVE_PERIOD_DURATION =
        "activePeriodDuration";

    /**
     * Name of a configuration attribute that has the default certified
     * duration (a Duration object) for continuous certifications.
     *
     * @deprecated Continuous certification support is removed
     */
    @Deprecated
    public static final String CERTIFIED_DURATION =
        "certifiedDuration";

    /**
     * Name of a configuration attribute that has the default certification
     * required duration (a Duration object) for continuous certifications.
     *
     * @deprecated Continuous certification support is removed
     */
    @Deprecated
    public static final String CERTIFICATION_REQUIRED_DURATION =
        "certificationRequiredDuration";

    /**
     * Name of a configuration attribute that has the default value of whether
     * the challenge period is enabled.
     */
    public static final String CHALLENGE_PERIOD_ENABLED =
        "challengePeriodEnabled";

    /**
     * Name of a configuration attribute that has the default challenge period
     * duration (a Duration object).
     */
    public static final String CHALLENGE_PERIOD_DURATION =
        "challengePeriodDuration";

    /**
     * Name of a configuration attribute that has the default value of whether
     * automatic closing is enabled.
     */
    public static final String AUTOMATIC_CLOSING_ENABLED =
        "automaticClosingEnabled";

    /**
     * Name of a configuration attribute that has the default automatic
     * closing interval (a Duration object).
     */
    public static final String AUTOMATIC_CLOSING_DURATION =
        "automaticClosingDuration";

    /**
     * Name of a configuration attribute that has the default automatic
     * closing Rule name.
     */
    public static final String AUTOMATIC_CLOSING_RULE_NAME =
        "automaticClosingRuleName";

    /**
     * Name of a configuration attribute that has the default automatic
     * closing action (a CertificationAction.Status object).
     */
    public static final String AUTOMATIC_CLOSING_ACTION =
        "automaticClosingAction";

    /**
     * Name of a configuration attribute that has the default automatic
     * closing comments.
     */
    public static final String AUTOMATIC_CLOSING_COMMENTS =
        "automaticClosingComments";

    /**
     * Name of a configuration attribute that has the default automatic
     * closing Identity name.
     */
    public static final String AUTOMATIC_CLOSING_SIGNER =
        "automaticClosingSigner";

    /**
     * Name of a configuration attribute that has the default value of whether
     * the remediation period is enabled.
     */
    public static final String REMEDIATION_PERIOD_ENABLED =
        "remediationPeriodEnabled";

    /**
     * Name of a configuration attribute that has the default remediation period
     * duration (a Duration object).
     */
    public static final String REMEDIATION_PERIOD_DURATION =
        "remediationPeriodDuration";

    /**
     * The sailpoint.object.Duration that should pass between scans for items
     * in a continuous certification that need to be transitioned to the
     * CertificationRequired state.
     *
     * @deprecated Continuous certification support is remove
     */
    @Deprecated
    public static final String CONTINUOUS_CERT_REQUIRED_SCAN_INTERVAL =
        "continuousCertCertificationRequiredScanInterval";

    /**
     * The sailpoint.object.Duration that should pass between scans for items
     * in a continuous certification that need to be transitioned to the
     * Overdue state.
     *
     * @deprecated Continuous certification support is remove
     */
    @Deprecated
    public static final String CONTINUOUS_OVERDUE_SCAN_INTERVAL =
        "continuousCertOverdueScanInterval";

    public static final String SUPPRESS_DUPLICATE_EMAILS =
        "suppressDuplicateEmails";

    /**
     * The last time at which a full refresh was run by the
     * CertificationRefresher.
     */
    @Deprecated
    public static final String LAST_FULL_CERT_REFRESH =
        "lastFullCertificationRefresh";

    /**
     * Controls the granularity of the generation of IdentitySnapshots
     * during an aggregation scan. The value is a time duration in seconds.
     * @see sailpoint.api.Aggregator for more information.
     */
    public static final String IDENTITY_SNAPSHOT_INTERVAL =
        "identitySnapshotInterval";

    /***
     * The time in milliseconds that should pass between scanning for completed
     * remediations.
     */
    public static final String REMEDIATION_SCAN_INTERVAL =
        "remediationScanInterval";

    /**
     * A CSV list of identity attributes to show as top level filters for 
     * certification items in the responsive UI. The same attributes will also be reflected in the certification
     * entity filters.
     */
    public static final String CERTIFICATION_ITEM_FILTER_ATTRIBUTES = "certificationItemFilterAttributes";

    /**
     * Whether or not to show recommendations for certification items in this cert.
     */
    public static final String CERTIFICATION_SHOW_RECOMMENDATIONS = "certificationShowRecommendations";

    /**
     * Whether approvals should be made automatically based on the recommendation.
     */
    public static final String CERTIFICATION_AUTO_APPROVE = "certificationAutoApprove";

    /**
     * If true, include Classification association in certification items and support display in the UI.
     */
    public static final String CERTIFICATION_INCLUDE_CLASSIFICATIONS = "certificationIncludeClassifications";

    //
    // END Certification
    //
    
    /**
     * The time in seconds that an object managed by CachedObject
     * is allowed to be used before fetching a fresh one.
     * UPDATE: This is not the refresh interval for CacheService,
     * it is no longer used with CachedObject.
     * jsl - yes it is
     */
    public static final String CACHED_OBJECT_DURATION =
    "cachedObjectDuration";

    /**
     * Debugging option to disable expiration of cached objects.
     * This is not exposed in the UI you have to set it in XML.
     */
    public static final String CACHED_OBJECT_NO_EXPIRATION =
    "cachedObjectNoExpiration";

    /**
     * Maximum number of workgroups allowed in QueryOptions.getOwnerScopeFilter to use IN clause.
     * Value of 0 means using the default value of 100.
     * Negative value means no IN clause will not be used, Filter of subquery will be returned. 
     * This is not exposed in the UI you have to set it in XML.
     */
    public static final String MAX_WORKGROUPS_FOR_OWNER_SCOPE_FILTER =
    "maxWorkgroupsForOwnerScopeFilter";

    /**
     * The interval at which the IntegrationConfigCache will reload itself
     * if it detects that it is stale.  This represents the number of times
     * that IntegrationConfigCache.refresh will need to be called before
     * we actually perform any work.  CacheService runs once per minute, so
     * this is effectively the number of minutes between which we will check the
     * IntegrationConfigCache for that not-so-fresh feeling.
     * 0 or non-existent means default.  Default is 10.
     */
    public static final String INTEGRATION_CONFIG_CACHE_REFRESH_INTERVAL =
            "integrationConfigCacheRefreshInterval";

    /**
     * boolean setting that will force the IntegrationConfigCache to check
     * much more frequently for updated Application objects and IntegrationConfigs,
     * which will result in many more queries, but always up-to-date objects.
     */
    public static final String INTEGRATION_CONFIG_CACHE_AGGRESIVE_REFRESH =
            "integrationConfigCacheAggressiveRefresh";
    
    /**
     * The default operation for remediation-modifiable attributes
     * in the Revocation Dialog
     */
    public static final String DEFAULT_REMEDIATION_MODIFIABLE_OP =
    "defaultRemediationModifiableOp";

    /**
     * Controls whether the application customization rule will be 
     * applied to the ResourceObject after provisioning when one or 
     * more attributes are synchronized to a target.
     */
    public static final String APPLY_CUSTOM_RULE_AFTER_ATTRIBUTE_SYNC =
        "applyCustomRuleAfterAttributeSync";

    /**
     * Dashboard layout related items. Contains the system-wide available
     * layout and content options. These options can be chosen by the user.
     */
    public static final String DAHSBOARD_DEFAULT = "dashboardDefault";

    public static final String DASHBOARD_COMPLIANCE_DEFAULT = "dashboardComplianceDefault";

    public static final String DASHBOARD_LIFECYCLE_DEFAULT = "dashboardLifecycleDefault";

    public static final String DASHBOARD_LCM_SUFFIX = "LCM";

    /**
     * The root path of the server.
     * e.g. <SERVER_ROOT_PATH>/path/foo.jsf
     *      if SERVER_ROOT_PATH is 'http://localhost/identityiq' then
     *      http://localhost/identityiq/path/foo.jsf
     */
    public static final String SERVER_ROOT_PATH = "serverRootPath";
    public static final String DEFAULT_SERVER_ROOT_PATH = "http://localhost:8080/identityiq";

    //
    // IdentityAI
    //
    public static final String IDENTITYAI_ENABLED = "identityAIEnabled";

    //
    // RapidSetup
    //
    public static final String RAPIDSETUP_ENABLED = "rapidSetupEnabled";

    //
    // PAM
    //

    public static final String PAM_ENABLED = "pamEnabled";
    
    //
    // FAM
    //
    
    public static final String FAM_ENABLED = "famEnabled";

    //
    // LCM
    //

    public static final String LCM_ENABLED = "lcmEnabled";

    public static final String LCM_MANAGE_ACCOUNTS_ACTIONS = "manageAccountsActions";
    public static final String LCM_MANAGE_ACCOUNTS_PREFIX = "manageAccounts";
    public static final String LCM_MANAGE_ACCOUNTS_DISABLE_AUTO_REFRESH_STATUS="manageAccountsDisableAutoRefreshStatus";
    public static final String LCM_REQUEST_ROLES_PREFIX = "requestRoles";

    public static final String LCM_REQUEST_ROLES_PERMITTED = "Permitted";
    public static final String LCM_REQUEST_ROLES_ASSIGNABLE = "Assignable";

    public static final String LCM_OP_ENABLED_SUFFIX = "Enabled";
    public static final String LCM_SELF = "Self";
    public static final String LCM_SUBORDINATE = "Subordinate";
    // The option name below is slightly misleading, but it's being preserved to facilitate migration from 5.0
    public static final String LCM_OTHERS = "Subordinate";

    public static final String LCM_SELF_SERVICE = "selfService";
    public static final String LCM_MANAGER = "manager";
    public static final String LCM_HELP_DESK = "helpDesk";
    public static final String LCM_GENERAL_POPULATION = "generalPopulation";

    public static final String LCM_ALLOW_ROLE_POPULATION_SEARCH = "lcmAllowRolePopulationSearch";
    public static final String LCM_ALLOW_ROLE_ATTRIBUTES_SEARCH = "lcmAllowRoleAttributesSearch";
    public static final String LCM_ALLOW_ROLE_IDENTITIES_SEARCH = "lcmAllowRoleIdentitiesSearch";
    public static final String LCM_ALLOW_ROLE_PERCENTAGE_SLIDER = "lcmAllowRolePercentageSlider";
    public static final String LCM_ALLOW_ROLE_SORT_PERCENTAGE_COUNT = "lcmAllowRoleSortPercentageCount";
    public static final String LCM_ALLOW_ROLE_SORT_PERCENTAGE = "lcmAllowRoleSortPercentage";
    public static final String LCM_ALLOW_ROLE_PERCENT_LIMIT = "lcmAllowPercentLimitRoles";
    public static final String LCM_ALLOW_ROLE_PERCENT_LIMIT_PERCENT = "lcmAllowPercentLimitRolesPercent";
    public static final String LCM_ALLOW_ROLE_SELECTOR_RULE_OVERRIDE = "lcmAllowRoleSelectorRuleOverride";
    
    public static final String LCM_ALLOW_ENTITLEMENT_POPULATION_SEARCH = "lcmAllowEntitlementPopulationSearch";
    public static final String LCM_ALLOW_ENTITLEMENT_ATTRIBUTES_SEARCH = "lcmAllowEntitlementAttributesSearch";
    public static final String LCM_ALLOW_ENTITLEMENT_IDENTITIES_SEARCH = "lcmAllowEntitlementIdentitiesSearch";
    public static final String LCM_ALLOW_ENTITLEMENT_PERCENTAGE_SLIDER = "lcmAllowEntitlementPercentageSlider";
    public static final String LCM_ALLOW_ENTITLEMENT_SORT_PERCENTAGE_COUNT = "lcmAllowEntitlementSortPercentageCount";
    public static final String LCM_ALLOW_ENTITLEMENT_SORT_PERCENTAGE = "lcmAllowEntitlementSortPercentage";
    public static final String LCM_ALLOW_ENTITLEMENT_PERCENT_LIMIT = "lcmAllowPercentLimitEntitlements";
    public static final String LCM_ALLOW_ENTITLEMENT_PERCENT_LIMIT_PERCENT = "lcmAllowPercentLimitEntitlementsPercent";
    
    public static final String QUICKLINK_CATEGORIES = "quickLinkCategories";
    public static final String GENERAL_QUICKLINKS = "generalQuickLinks";
    /**
     * A list of all of the LCM "user types" (for example - LCM_SELF_SERVICE, etc...).
     * Any new user type should be added to this list.
     */
    public static final String[] LCM_USER_TYPES = new String[] {
        LCM_SELF_SERVICE, LCM_MANAGER, LCM_HELP_DESK, LCM_GENERAL_POPULATION
    };

    public static final String LCM_OP_DELETE = "Delete";
    public static final String LCM_OP_DISABLE = "Disable";
    public static final String LCM_OP_ENABLE = "Enable";
    public static final String LCM_OP_LOCK = "Lock";
    public static final String LCM_OP_UNLOCK = "Unlock";

    public static final String LCM_SHOW_ROLE_DETAILS = "lcmUseRoleDetailsPage";
    public static final String LCM_ALLOW_REQUEST_PERMITS = "lcmAllowRequestPermits";

    public static final String LCM_ALLOW_REQUEST_ENTITLEMENTS_SHOW_POPUPLATION = "allowRequestEntitlementsShowPopulation";
    public static final String LCM_ALLOW_REQUEST_ENTITLEMENTS_REMOVE = "allowRequestEntitlementsRemove";
    public static final String LCM_ALLOW_REQUEST_ROLES = "allowRequestRoles";
    public static final String LCM_ALLOW_REQUEST_ROLES_ADDITIONAL_ACCOUNT_REQUESTS = "allowRequestRolesAdditionalAccountRequests";
    public static final String LCM_ALLOW_REQUEST_ROLES_SHOW_POPUPLATION = "allowRequestRolesShowPopulation";
    public static final String LCM_ALLOW_REQUEST_ROLES_REMOVE = "allowRequestRolesRemove";

    public static final String LCM_ALLOW_REQUEST_ENTITLEMENTS = "allowRequestEntitlements";
    public static final String LCM_ALLOW_REQUEST_ENTITLEMENTS_ADDITIONAL_ACCOUNT_REQUESTS = "allowRequestEntitlementsAdditionalAccountRequests";
    public static final String LCM_ALLOW_MANAGE_ACCOUNTS_ADDITIONAL_ACCOUNT_REQUESTS = "allowManageAccountsAdditionalAccountRequests";
    public static final String LCM_ALLOW_MANAGE_EXISTING_ACCOUNTS = "allowManageExistingAccounts";
    public static final String LCM_ALLOW_ACCOUNT_ONLY_REQUESTS = "allowAccountOnlyRequests";
    
    public static final String LCM_ALLOW_PRIORITY_EDITING = "allowPriorityEditing";
    public static final String LCM_ALLOW_WORKGROUP_SELF_APPROVAL = "allowWorkgroupSelfApproval";
    public static final String LCM_ALLOW_GENERATE_PASSWORD_DELEGATION = "allowGeneratePasswordDelegated";
    public static final String LCM_ENABLE_APPROVAL_RECOMMENDATIONS = "lcmEnableApprovalRecommendations";
    
    public static final String LCM_MANAGE_ACCOUNTS_SHOW_ALL_BUTTONS = "lcmManageAccountsShowAllButtons";

    public static final String LCM_REQUEST_CONTROLS_ALLOW_ALL = "allowAnythingFromAnyone";

    public static final String LCM_REQUEST_CONTROLS_MATCH_ANY_OR_ALL = "matchAnyOrAll";
    public static final String LCM_REQUEST_CONTROLS_MATCH_ANY = "matchAny";
    public static final String LCM_REQUEST_CONTROLS_MATCH_ALL = "matchAll";
    public static final String LCM_REQUEST_CONTROLS_MATCH_NONE = "matchNone";

    public static final String LCM_REQUEST_CONTROLS_DISABLE_SCOPING = "ignoreIIQScoping";

    public static final String LCM_REQUEST_CONTROLS_ENABLE_ATTRIBUTE_CONTROL = "enableAttributeControl";
    public static final String LCM_REQUEST_CONTROLS_ATTRIBUTE_FILTER_CONTROL = "attributeFilterControl";

    public static final String LCM_REQUEST_CONTROLS_ENABLE_SUBORDINATE_CONTROL = "enableSubordinateControl";
    public static final String LCM_REQUEST_CONTROLS_MAX_HIERARCHY_DEPTH = "maxHierarchyDepth";
    public static final String LCM_REQUEST_CONTROLS_SUBORDINATE_CHOICE = "subordinateChoice";
    public static final String LCM_REQUEST_CONTROLS_DIRECT_OR_INDIRECT = "directOrIndirect";
    public static final String LCM_REQUEST_CONTROLS_DIRECT = "direct";

    public static final String LCM_REQUEST_CONTROLS_ENABLE_CUSTOM_CONTROL = "enableCustomControl";
    public static final String LCM_REQUEST_CONTROLS_CUSTOM_CONTROL = "customControl";

    public static final String LCM_REQUEST_CONTROLS_ROLE_SELECTOR_RULE = "roleSelectorRule";
    public static final String LCM_REQUEST_CONTROLS_APPLICATION_SELECTOR_RULE = "applicationSelectorRule";
    public static final String LCM_REQUEST_CONTROLS_MANAGED_ATTRIBUTE_SELECTOR_RULE = "managedAttributeSelectorRule";

    public static final String LCM_MAX_ROLES_RETURNED_FROM_USER_SEARCH = "lcmMaxRolesFromUserSearch";
    public static final String LCM_DISABLE_ROLE_POPULATION_STATS = "lcmDisableRolePopulationStats";
    public static final String LCM_REQUIRE_PASSWORD_IDENTITY_CREATE = "lcmRequirePasswordIdentityCreate";
    
    public static final String LCM_MANAGED_ATTRIBUTE_STATS_MAX_IDENTITIES = "managedAttributeStatsMaxIdentities";
    public static final String LCM_MANAGED_ATTRIBUTE_STATS_MAX_ATTRIBUTES = "managedAttributeStatsMaxAttributes";
    
    public static final String LCM_SEARCH_TYPE = "lcmSearchType";
    public static final String LCM_SEARCH_TYPE_STARTSWITH = "startsWith";
    public static final String LCM_SEARCH_TYPE_CONTAINS = "contains";

    public static final String LCM_MANAGE_ACCOUNTS_ALLOW_GROUP_MANAGEMENT = "allowGroupManagement";
    
    public static final String LCM_ALLOW_POPULATION_SEARCH = "allowPopulationSearch";
    public static final String LCM_ALLOW_IDENTITY_SEARCH = "allowIdentitySearch";

    public static final String LCM_SEARCH_MAX_RESULTS = "lcmSearchMaxResults";

    public static final String LCM_MOBILE_MAX_SELECTABLE_USERS = "lcmMobileMaxSelectableUsers";

    public static final String LCM_SHOW_EXTERNAL_TICKET_ID = "lcmShowExternalTicketId";

    public static final String LCM_CREATE_IDENTITY_USE_BY_DAYS = 
        "lcmCreateIdentityUseByDays";
    /**
     * Whether to force the user to enter their current password when changing their password
     * to a new password
     */
    public static final String REQUIRE_OLD_PASSWORD_AT_CHANGE =
        "requireOldPasswordAtChange";

    /**
     * Whether to check new passwords against words in the password dictionary to see
     * if any dictionary word is a substring of the new password
     */
    public static final String CHECK_PASSWORDS_AGAINST_DICTIONARY =
        "checkPasswordsAgainstDictionary";

    /**
     * Whether to check new passwords against identity attributes to see
     * if any attribute value is a substring of the new password
     */
    public static final String CHECK_PASSWORDS_AGAINST_IDENTITY_ATTRIBUTES =
        "checkPasswordsAgainstIdentityAttributes";

    public static final String CHECK_PASSWORDS_AGAINST_ACCOUNT_ATTRIBUTES = "checkPasswordsAgainstAccountAttributes";

    /**
     * What is the minimum duration (IN HOURS) between password changes?
     * set to zero to indicate no min duration
     */
    public static final String PASSWORD_CHANGE_MIN_DURATION =
        "passwordChangeMinDuration";

    public static String PASSWORD_UPPER_CHARACTERS= "passwordUpperCharacters";
    public static String PASSWORD_LOWER_CHARACTERS= "passwordLowerCharacters";
    public static String PASSWORD_NUMBER_CHARACTERS= "passwordNumberCharacters";
    public static String PASSWORD_SPECIAL_CHARACTERS= "passwordSpecialCharacters";

    // plugin options
    public static String PLUGIN_RELAX_EXPORT_ENFORCEMENT = "pluginRelaxExportDeclarationEnforcement";
    public static String PLUGIN_PROHIBIT_SCRIPTING       = "pluginProhibitScripting";


    // edit preferences options, allow configuration to show/hide tabs
    public static String ALLOW_DISABLE_NOTIFICATIONS = "allowDisableNotifications";
    public static String SHOW_EDIT_FORWARDING = "showEditForwarding";
    public static String SHOW_CHANGE_PASSWORD_TAB = "showChangePasswordTab";

    // Recommender options
    /**
     * The id or name of the RecommenderDefinition to use when asking for
     * recommendations -- typically from IdentityAI.
     */
    public static final String RECOMMENDER_SELECTED = "recommenderSelected";


    // global Email options

    public static final String DEFAULT_EMAIL_HOST = "defaultEmailHost";
    public static final String DEFAULT_EMAIL_PORT = "defaultEmailPort";
    public static final String DEFAULT_EMAIL_FROM = "defaultEmailFromAddress";
    /**
     * Minimum Gap in milliseconds between emails
     */
    public static final String DEFAULT_EMAIL_GAP = "defaultEmailGap";

    /**
     * Added in 6.1 to allow the specification of a custom
     * EmailNotifier class rather than using one of the three types below
     * which force you to use stock notifier classes.
     * If this is set, it overrides emailNotifierType.
     */
    public static final String EMAIL_NOTIFIER_CLASS = "emailNotifierClass";

    /**
     * One of the EMAIL_NOTIFIER_TYPE_* constants that indicates how to send
     * emails.
     */
    public static final String EMAIL_NOTIFIER_TYPE = "emailNotifierType";

    /**
     * Email notifier type constant that sends through SMTP.
     */
    public static final String EMAIL_NOTIFIER_TYPE_SMTP = "smtp";

    /**
     * Email notifier type constant that redirects all emails to a given email
     * address or to a file.
     */
    public static final String EMAIL_NOTIFIER_TYPE_REDIRECT_TO_FILE = "redirectToFile";
    
    public static final String EMAIL_NOTIFIER_TYPE_REDIRECT_TO_EMAIL = "redirectToEmail";

    /**
     * Email notifier option that specifies whether emails should be sent
     * immediately or backgrounded on the request queue.
     */
    public static final String EMAIL_NOTIFIER_SEND_IMMEDIATELY = "emailNotifierSendImmediately";

    /**
     * Email thread locking option that determines how long the thread will wait until
     * expiring the locking attempt.
     */
    public static final String EMAIL_THREAD_LOCK_TIMEOUT_MIN = "emailThreadLockTimeoutMinutes";

    /**
     * Redirecting email notifier option that specifies the address to which to
     * redirect all emails. This is ignored if a filename is set.
     */
    public static final String REDIRECTING_EMAIL_NOTIFIER_ADDRESS = "redirectingEmailNotifierAddress";

    /**
     * Redirecting email notifier option that specifies the file to which to
     * write all emails.
     */
    public static final String REDIRECTING_EMAIL_NOTIFIER_FILENAME = "redirectingEmailNotifierFilename";

    /** E-mail template to notify about comments added to work items. */
    public static final String WORK_ITEM_COMMENT_EMAIL_TEMPLATE = "workItemCommentEmailTemplate";
    /** E-mail template to notify that a work item was forwarded. */
    public static final String WORK_ITEM_FORWARD_EMAIL_TEMPLATE = "workItemForwardEmailTemplate";
     /** E-mail template to notify that a work item was assigned to the user. */
    public static final String WORK_ITEM_ASSIGNMENT_EMAIL_TEMPLATE = "workItemAssignmentEmailTemplate";
    public static final String REMED_ITEM_ASSIGNMENT_EMAIL_TEMPLATE = "remediationItemAssignmentEmailTemplate";
     /** E-mail template to notify that a work item was removed from their queue. */
    public static final String WORK_ITEM_ASSIGNMENT_REMOVAL_EMAIL_TEMPLATE = "workItemAssignmentRemovalEmailTemplate";
    public static final String WORK_ITEM_FILTER_ATTRIBUTES = "workItemFilterAttributes";
    public static final String REMED_ITEM_ASSIGNMENT_REMOVAL_EMAIL_TEMPLATE = "remediationItemAssignmentRemovalEmailTemplate";
    /** E-mail Template Pending Role Request Delete  */
    //TQM: TODO: This is not used anywhere consider deleting
    public static final String PENDING_ROLE_REQUEST_DELETE_EMAIL_TEMPLATE = "pendingRoleRequestDeleteEmailTemplate";
    /** E-mail template to notify about new certifications. */
    public static final String CERTIFICATION_EMAIL_TEMPLATE = "certificationEmailTemplate";
    /** E-mail template for bulk reassignments. */
    public static final String BULK_REASSIGNMENT_EMAIL_TEMPLATE = "bulkReassignmentEmailTemplate";
    /** E-mail template for delegations. */
    public static final String DELEGATION_EMAIL_TEMPLATE = "delegationEmailTemplate";
    /** E-mail template when the delegation finishes*/
    public static final String DELEGATION_FINISHED_EMAIL_TEMPLATE = "delegationFinishedEmailTemplate";
    /** E-mail template for delegation revocation. */
    public static final String DELEGATION_REVOCATION_EMAIL_TEMPLATE =
        "delegationRevocationEmailTemplate";
    /** E-mail template for notifying a user that one of their entitlements is being remediated. */
    public static final String REMEDIATION_NOTIFICATION_EMAIL_TEMPLATE = "remediationNotificationEmailTemplate";
    /** E-mail template for remediation work item notification. */
    public static final String REMEDIATION_WORK_ITEM_EMAIL_TEMPLATE = "remediationEmailTemplate";
    /** Allow a remediation for a role with multiple applications to go to the role owner */
    public static final String REMEDIATION_GOES_TO_ROLE_OWNER_INSTEAD_OF_DEFAULT = "remediationToRoleOwnerOverDefault";
    /** E-mail template for notifying about certification sign off approval. */
    public static final String CERT_SIGN_OFF_APPROVAL_EMAIL_TEMPLATE = "certificationSignOffApprovalEmailTemplate";
    /** Default e-mail template for WorkItem Reminders */
    public static final String DEFAULT_REMINDER_EMAIL_TEMPLATE = "reminderEmailTemplate";
    /** Default e-mail template for Escalation */
    public static final String DEFAULT_ESCALATION_EMAIL_TEMPLATE = "escalationEmailTemplate";
    /** Default escalation rule for certifications */
    public static final String DEFAULT_ESCALATION_RULE = "defaultEscalationRule";
    
    public static final String ACCESS_REQUEST_REMINDER_EMAIL_TEMPLATE = "accessRequestReminderEmailTemplate";
    
    public static final String CHALLENGE_PERIOD_START_EMAIL_TEMPLATE = "challengePeriodStartEmailTemplate";
    public static final String CHALLENGE_PERIOD_END_EMAIL_TEMPLATE = "challengePeriodEndEmailTemplate";
    public static final String CHALLENGE_GENERATION_EMAIL_TEMPLATE = "challengeGenerationEmailTemplate";
    public static final String CERT_DECISION_CHALLENGED_EMAIL_TEMPLATE = "certificationDecisionChallengedEmailTemplate";
    public static final String CHALLENGE_EXPIRED_EMAIL_TEMPLATE = "challengeExpirationEmailTemplate";
    public static final String CHALLENGE_DECISION_EXPIRED_EMAIL_TEMPLATE = "challengeDecisionExpirationEmailTemplate";
    public static final String CHALLENGE_ACCEPTED_EMAIL_TEMPLATE = "challengeAcceptedEmailTemplate";
    public static final String CHALLENGE_REJECTED_EMAIL_TEMPLATE = "challengeRejectedEmailTemplate";
    public static final String CERTIFICATION_REMINDER_EMAIL_TEMPLATE = "certificationReminderEmailTemplate";
    public static final String OPEN_CERTIFICATIONS_EMAIL_TEMPLATE = "openCertsEmailTemplate";

    public static final String POLICY_VIOLATION_DELEGATION_EMAIL_TEMPLATE = "policyViolationDelegationEmailTemplate";

    public static final String WORK_ITEM_ASSIGNMENT_NOTIFICATION = "workItemAssignmentNotification";
    public static final String WORK_ITEM_ASSIGNMENT_REMOVAL_NOTIFICATION = "workItemAssignmentNotificationRemoval";
    public static final String WORK_ITEM_PRIORITY_EDITING_ENABLED = "workItemPriorityEditingEnabled";

    /**
     * The time at which e-mail reminders should begin to get sent.
     * This value is specified as the number of days before a work item expires
     */
    public static final String DEFAULT_REMINDER_START = "defaultReminderStart";
    /** Minimum time that should pass before scanning work items */
    public static final String DEFAULT_WAKEUP_INTERVAL = "defaultWakeUpInterval";
    /** Maximum number of reminders that will be sent on a work item before it escalates */
    public static final String DEFAULT_MAX_REMINDERS = "defaultMaxReminders";
    /** Default duration of new work items in terms of days */
    public static final String DEFAULT_WORK_ITEM_DURATION = "defaultWorkItemDuration";

    /** E-mail template to notify certifier about a mitigation expiration. */
    public static final String MITIGATION_EXPIRATION_EMAIL_TEMPLATE = "mitigationExpirationEmailTemplate";

    /** The MitigationExpiration.Action enum constant of the action to execute when mitigations expire. */
    public static final String MITIGATION_EXPIRATION_ACTION = "mitigationExpirationAction";

    /** The parameters to pass to the execution of the mitigation expiration action. */
    public static final String MITIGATION_EXPIRATION_ACTION_PARAMETERS = "mitigationExpirationActionParameters";

    /** Determines what type of message will be displayed by the LoginBean
     *  on a failed login. A value of "detailed" will show what actually
     *  failed (for example, bad userid, locked account, etc.). Any other value will
     *  just show that login failed.
     */
    public static final String LOGIN_ERROR_STYLE = "loginErrorStyle";
    public static final String DETAILED_LOGIN_STYLE = "detailed";
    public static final String SIMPLE_LOGIN_STYLE = "simple";

    /**
     * Used to control whether to return to the dashboard (instead of the
     * previously viewed page) after a session timeout.
     */
    public static final String LOGIN_RETURNS_TO_DASHBOARD = "loginReturnsToDashboard";

    /**
     * Used to enable the "forgot password" link on the login page.
     */
    public static final String ENABLE_FORGOT_PASSWORD = "enableForgotPassword";
    
    /**
     * Used to enable the "account unlock" link on the login page.
     */
    public static final String ENABLE_ACCOUNT_UNLOCK = "enableAccountUnlock";

    /**
     * Used to enable the color contrast. Defaults to false.
     */
    public static final String ENABLE_CONTRAST = "enableContrast";

    /**
     * Used to enable the "forgot password" link on the login page.
     */
    public static final String AUTH_QUESTIONS_ENABLED = "authQuestionsEnabled";
    
    /**
     * Used to enable locking of accounts upon failed Login Attempts
     */
    public static final String ENABLE_AUTH_LOCKOUT = "enableAuthLockout";

    /**
     * Maximum number of consecutive failed login attempts before user is locked
     */
    public static final String FAILED_LOGIN_ATTEMPTS = "maxAuthLoginFailures";
    
    /**
     * Time in milliseconds a user will be locked upon exceeding the configured 
     * maximum number of consecutive failed logins
     */
    public static final String LOGIN_LOCKOUT_DURATION = "LoginAuthDurationMillis";
    
    /**
     * Option to enable lockout on protected users upon login authorization lockout
     */
    public static final String PROTECTED_USER_LOCKOUT = "ProtectedUserLockout";
    
    /**
     * The list of applications enable for pass-through authentication.
     * This is normally a List<Reference> but ObjectUtil.getObjects
     * is used to process it so it can be a CSV if you manually edit it.
     */
    public static final String LOGIN_PASS_THROUGH = "loginPassThrough";
    
    /**
     * Allows the OOTB administrator account, "spadmin" or "admin", to use application
     * based pass-through authentication.
     */
    public static final String ALLOW_ADMIN_PASS_THROUGH = "allowAdminPassThrough";

    /**
     * Number of questions that must be answered for authenticating with questions.
     */
    public static final String NUM_AUTH_QUESTIONS_FOR_AUTHN = "numAuthQuestionsForAuthn";
   
    /**
     * Starting in 6.2 the pass-through link is not refreshed every time 
     * authentication is performed. Added this flag as a way to achieve the expense if the
     * customer wishes.
     */
    public static final String REFRESH_PASSTHROUGH_LINK_DURING_AUTHENTICATION = "refreshPassthroughLinkOnAuthenticate"; 
    
    /**
     * Flag that controls if we should create new cubes during successful pass-through
     * attempts. This option is on by default and must be explicitly disabled 
     * through this attribute.
     */
    public static final String DISABLE_PASSTHROUGH_AUTO_CREATE = "disablePassthroughAutoCreate";
    
    /**
     * Number of authentication questions for which the user must setup answers.
     */
    public static final String NUM_AUTH_QUESTION_ANSWERS_REQUIRED = "numAuthQuestionAnswersRequired";

    /**
     * The maximum number of failed authentication question attempts before lockout
     * (less than 1 is no lockout).
     */
    public static final String MAX_AUTH_QUESTION_FAILURES = "maxAuthQuestionFailures";

    /**
     * The amount of time to lockout a user due to authentication question failures.
     */
    public static final String AUTH_QUESTION_LOCKOUT_DURATION_MILLIS = "authQuestionLockoutDurationMillis";

    /**
     * Whether users with unanswered authentication questions should be required
     * to answer upon login.
     */
    public static final String PROMPT_FOR_AUTH_QUESTION_ANSWERS_AFTER_LOGIN = "promptForAuthQuestionAnswersAfterLogin";

    /**
     * The configuration object responsible for storing all sms password reset changes 
     */
    public static final String SMS_RESET_CONFIG = "smsResetConfig";
    
    /**
     * Various email templates.
     */
    public static final String SIGNOFF_EMAIL_TEMPLATE = "signoffEmailTemplate";
    public static final String REPORT_COMPLETION_EMAIL_TEMPLATE = "reportCompletionEmailTemplate";
    public static final String POLICY_VIOLATION_EMAIL_TEMPLATE = "policyViolationEmailTemplate";
    
    /**
     * Determines the granularity of the group index history.
     */
    public static final String GROUP_INDEX_GRANULE = "groupIndexGranule";

    /**
     * Determines the granularity of the identity index (scorecard) history.
     */
    public static final String IDENTITY_INDEX_GRANULE = "identityIndexGranule";
    
    /**
     * SM read timeout for single read operation
     */
    public static final String SM_READ_TIMEOUT = "smReadTimeout";

    public static final String SM_SOCKET_CONNECT_RETRY = "smSocketConnectRetry";
    public static final String SM_MAX_ACTIVE_TRANSACTIONS = "smMaxActiveTransactions";
    
    public static final String SM_SYNC_SCHEMA = "syncSchema";

    /**
     * Possible values for the index history parameters.
     * The minute and second granules are intended for use only in testing.
     * An option to specify a count along with the granule might be desired so
     * that you can specify things like 3 months, 10 days, etc.
     */
    public static final String GRANULE_NONE = "none";
    public static final String GRANULE_INFINITE = "infinite";
    public static final String GRANULE_SECOND = "second";
    public static final String GRANULE_MINUTE = "minute";
    public static final String GRANULE_HOUR = "hour";
    public static final String GRANULE_DAY = "day";
    public static final String GRANULE_WEEK = "week";
    public static final String GRANULE_MONTH = "month";

    /**
     * RequestDefinition name for sending emails.
     */
    public static final String EMAIL_REQUEST_DEFINITION = "emailRequestDefinition";

    /**
     * Expiration and pruning ages.
     */
    public static final String IDENTITY_SNAPSHOT_MAX_AGE = "identitySnapshotMaxAge";
    public static final String TASK_RESULT_MAX_AGE = "taskResultMaxAge";
    public static final String REQUEST_MAX_AGE = "requestMaxAge";
    public static final String CERTIFICATION_MAX_AGE = "certificationMaxAge";
    public static final String CERTIFICATION_ARCHIVE_MAX_AGE = "certificationArchiveMaxAge";
    public static final String REMOTE_LOGIN_TOKEN_MAX_AGE = "remoteLoginTokenMaxAge";

    /**
     * Extended attribute constraints.
     */
    public static final String IDENTITY_MAX_SEARCHABLE_ATTRIBUTES = "identityMaxSearchableAttributes";
    public static final String LINK_MAX_SEARCHABLE_ATTRIBUTES = "linkMaxSearchableAttributes";

    /**
     * Number of seconds between request processor polls.
     * @deprecated This should now be done in the ServiceDefinition
     * for the Request service.
     */
    @Deprecated
    public static final String REQUEST_PROCESSOR_CYCLE_SECONDS =
        "requestProcessorCycleSeconds";

    /*
     * System configuration property defining whether unauthenticated access is
     * allowed for the end user pages.
     */
    public static final String ALLOW_UNAUTHENTICATED_END_USER_PAGES =
        "allowUnauthenticatedEndUserPages";

    /**
     * System configuration property defining a rule that will process
     * sso header information.
     */
    public static final String LOGIN_SSO_RULE = "loginSSORule";

    /**
     * Will contain a comma delimited list of authenticators.
     * @ignore
     * @see sailpoint.web.sso.SSOAuthenticator interface for more.
     */
    public static final String SSO_AUTHENTICATORS = "ssoAuthenticators";
    
    
    /**
     * System configuration property defining a rule that will process
     * sso header information on every request. It is meant to be very
     * lightweight to validate with the SSO Provider the validity of the session
     */
    public static final String LOGIN_SSO_VALIDATION_RULE = "loginSSOValidationRule";
    
    /**
     * Property defining a default approver for role and profile changes in the event
     * that the rule cannot find one.
     */
    public static final String DEFAULT_ROLE_CHANGE_APPROVER = "roleChangeApprover";

    /**
     * Property defining the rule that will determine the identity that will
     * receive a work item currently assigned to an inactive identity.
     * Technically this is an Escalation rule not a forwarding rule,
     * the rule should be of type Rule.Type.Escalation.
     */
    public static final String INACTIVE_OWNER_WORK_ITEM_FORWARD_RULE = "inactiveOwnerWorkItemForwadRule";

    /**
     * Property defining a rule that will determine the identity that should
     * receive a work item.
     *
     * The rule should be of type Rule.Type.WorkItemForward.
     */
    public static final String WORK_ITEM_FORWARD_RULE = "workItemForwardRule";

    /**
     * Property defining a rule that will determine the identity that should
     * receive a work item if forwarding will result in self-certification.
     *
     * The rule should be of type Rule.Type.FallbackWorkItemForward.
     */
    public static final String FALLBACK_WORK_ITEM_FORWARD_RULE = "fallbackWorkItemForwardRule";

    /**
     * If true, will block manual forwarding of certification work items if new owner would result in any self
     * certification violations. If false, after the forward is complete, those self certification violations will be
     * reassigned to the self certification violation owner upon refresh.
     *
     * Default is false for new installations (7.3 and later), but true for previous installations, to keep previous
     * behavior.
     */
    public static final String BLOCK_FORWARD_WORK_ITEM_SELF_CERTIFICATION = "blockForwardWorkItemSelfCertification";

    /**
     * Property defining which work items will be archived. 
     */
    public static final String WORK_ITEM_ARCHIVE_TYPES = "workItemArchiveTypes";

    /**
     * Enables the provisioning pages in the UI.
     * @deprecated This has been deprecated as of 3.1.
     */
    @Deprecated
    public static final String ENABLE_PROVISIONING_PAGES = "enableProvisioningPages";

    /**
     * Property used to determine whether or not to display a flat list of profiles in the
     * modeler
     */
    public static final String ENABLE_MODELER_PROFILE_VIEW = "enableModelerProfileView";

    /**
     * The name of a rule that can be run when managed attributes are promoted.
     * Usually these are specified on the application, but configuring
     * a global rule through the system config is allowed for simpler setup if the rule is
     * the same across all applications.
     */
    public static final String MANAGED_ATTRIBUTE_CUSTOMIZATION_RULE = "manangedAttributeCustomizationRule";

    //
    // Workflows!
    //

    /**
     * Used by Modeler/RoleLifecycler for role update and delete approvals.
     */
    public static final String WORKFLOW_ROLE_APPROVAL =
    "workflowRoleApproval";

    /**
     * Used by IdentityBean/IdentityLifecycler for identity updates.
     * Primarily this is role assignment changes but it could be more.
     */
    public static final String WORKFLOW_IDENTITY_UPDATE =
    "workflowIdentityUpdate";

    /**
     * Workflow that can be run in the middle of an identity refresh
     * to handle trigger events and account completion work items.
     */
    public static final String WORKFLOW_IDENTITY_REFRESH =
        "workflowIdentityRefresh";

    /**
     * Workflow that can be run when manual correlation is done
     */
    public static final String WORKFLOW_IDENTITY_CORRELATION =
        "workflowIdentityCorrelation";

    /**
     * Workflow launched for managed attribute updates.
     */
    public static final String WORKFLOW_MANAGED_ATTRIBUTE =
    "workflowManagedAttribute";

    /**
     * Workflow launched to handle sunrise/sunset of
     * assigned roles, permitted roles, and entitlements.
     */
    public static final String WORKFLOW_SCHEDULED_ASSIGNMENT =
    "workflowScheduledAssignment";

    /**
     * Workflow launched to handle sunrise/sunset of
     * role definitions.
     */
    public static final String WORKFLOW_SCHEDULED_ROLE_ACTIVATION =
    "workflowScheduledRoleActivation";
    
    public static final String WORKFLOW_LCM_ROLE_REQUEST =
        "workflowLCMRoleRequest";
    public static final String WORKFLOW_LCM_REQUEST_ENTITLEMENTS =
        "workflowLCMRequestEntitlements";
    public static final String WORKFLOW_LCM_MANAGE_ACCOUNTS =
        "workflowLCMManageAccounts";
    public static final String WORKFLOW_LCM_MANAGE_ATTRIBUTES =
        "workflowLCMManageAttributes";
    public static final String WORKFLOW_LCM_CREATE_IDENTITY =
        "workflowLCMCreateIdentity";

    public static final String WORKFLOW_LCM_ACCESS_REQUEST =
            "workflowLCMAccessRequest";
    public static final String WORKFLOW_LCM_ROLES_REQUEST =
                "workflowLCMRolesRequest";
    public static final String WORKFLOW_LCM_ENTITLEMENTS_REQUEST =
                "workflowLCMEntitlementsRequest";
    public static final String WORKFLOW_LCM_ACCOUNTS_REQUEST =
                "workflowLCMAccountsRequest";
    public static final String WORKFLOW_LCM_UNLOCK_ACCOUNT =
                "workflowLCMUnlockAccount";
    public static final String WORKFLOW_LCM_PASSWORD_REQUEST =
                "workflowLCMPasswordsRequest";
    public static final String WORKFLOW_LCM_IDENTITY_EDIT_REQUEST =
                "workflowLCMIdentityEditRequest";
    public static final String WORKFLOW_LCM_CREATE_IDENTITY_REQUEST =
                "workflowLCMIdentityCreateRequest";
    public static final String WORKFLOW_LCM_SSR_REQUEST =
                "workflowLCMSelfServiceRegistrationRequest";


    public static final String WORKFLOW_PASSWORD_INTERCEPT =
        "workflowPasswordIntercept";

    public static final String WORKFLOW_LCM_ROLE_PROPAGATION =
        "workflowLCMRolePropagation";

    //
    // Scoped authorization
    //

    /**
     * Property that holds whether all scopes have had their paths denormalized
     * onto the objects that reference them as an assigned scope. When false,
     * the scoping mechanism will not use the denormalized paths in the
     * SailPointObject classes, but join to the scope.
     */
    public static final String SCOPE_PATHS_DENORMALIZED = "scopePathsDenormalized";

    /**
     * Property used to determine whether objects that do not have an assigned
     * scope should be viewable by all identities. If false, only identities
     * with the SystemAdministrator capability can see these.
     */
    public static final String UNSCOPED_OBJECTS_GLOBALLY_ACCESSIBLE =
        "unscopedObjectsGloballyAccessible";

    /**
     * Property that provides a default value for whether identity's should
     * implicitly have control over their assigned scope (in addition to their
     * controlled scopes). This can be overridden on the identity.
     */
    public static final String IDENTITY_CONTROLS_ASSIGNED_SCOPE =
        "identityControlsAssignedScope";

    /**
     * Property that disables the scoping mechanism. If a customer is not
     * using scopes, disabling this could possibly improve all queries by
     * excluding an 'is null' clause when getting objects, which is possibly
     * slow on Oracle, maybe other DBs.
     */
    public static final String SCOPING_ENABLED = "scopingEnabled";

    //
    // Temporary directory can be used for location where generated
    // aggregation log will be placed along with other temporary
    // files like jasper virtualizer files.
    //

    public static final String TEMP_DIR = "temporaryDirectory";


    public static final String HELP_EMAIL_ADDRESS = "helpEmail";

    public static final String JASPER_ENCODING = "jasperEncoding";

    public static final String JASPER_CSV_DELIMITER = "jasperCSVDelimiter";

    public static final String REPORT_SEARCH_TYPE = "reportSearchType";

    // list of urls and the rights required to view them
    public static final String WEB_RESOURCES = "webResources";

    /**
     * Display entitlement descriptions rather than values.
     */
    public static final String DISPLAY_ENTITLEMENT_DESC= "displayEntitlementDescriptions";

    /**
     * Allow users to approve accounts within certifications.
     */
    public static final String ENABLE_ACCOUNT_APPROVE_ACTION = "enableApproveAccountAction";
    public static final String ENABLE_ACCOUNT_REVOKE_ACTION = "enableAccountRevokeAction";
    public static final String ENABLE_ACCOUNT_REASSIGN_ACTION = "enableAccountReassignAction";
    public static final String DEFAULT_ASSIGNEE = "defaultAssignee";

    public static final String ENABLE_ROLE_SUN_ASSIGNMENT="enableRoleSunAssign";

    public static final String ENABLE_ROLE_SUN_ACTIVATION="enableRoleSunActivate";

    public static final String ROLE_SUNSET_NOTIFICATION_DAYS_REMINDER = "roleSunsetNotificationDaysReminder";
    public static final String SUNSET_EXPIRATION_REMINDER_EMAIL_TEMPLATE = "sunsetExpirationReminderEmailTemplate";

    /**
     * Custom behaviors for the various identity selectors
     */
    public static final String IDENTITY_FILTERS = "identityFilters";


    /**
     * ConnectorRegistry Constants
     *
     * INSTALLED_CONNECTORS deprecated in favor of template applications.
     */
    public static final String APP_TARGET_COLLECTOR_REGISTRY = "ApplicationTargetCollectorRegistry";
    public static final String CONNECTOR_REGISTRY = "ConnectorRegistry";
    @Deprecated
    public static final String INSTALLED_CONNECTORS = "installedConnectors";
    public static final String APPLICATION_TEMPLATES = "applicationTemplates";

    /** 
     * For Email Task Alerts. 
     */
    public static final String TASK_COMPLETION_RULE = "taskCompletionRule";
    public static final String TASK_COMPLETION_EMAIL_NOTIFY = "taskCompletionEmailNotify";
    public static final String TASK_COMPLETION_RECIPIENTS = "taskCompletionEmailRecipients";
    public static final String TASK_COMPLETION_EMAIL_TEMPLATE = "taskCompletionEmailTemplate";
    public static enum EmailNotify {

        // Email Notification is disabled 
        Disabled,

        // Email Notification only in case of Warning
        Warning,

        // Email Notification only in case of failure
        Failure,

        // Always notify
        Always
    }

    /**
     * If not configured, we default to this email template for task notification.
     */
    public static final String DEFAULT_TASK_COMPLETION_EMAIL_TEMPLATE = "Task Status";

    // pre 6.4 constants with less descriptive names, have to preserve
    // in case there are custom rules using the old names
    public static final String ATT_TASK_COMPLETION_RULE = TASK_COMPLETION_RULE;
    public static final String ATT_IDENTITIES = TASK_COMPLETION_RECIPIENTS;
    public static final String ATT_EMAIL_NOTIFY = TASK_COMPLETION_EMAIL_NOTIFY;
    public static final String ATT_EMAIL_TEMPLATE = TASK_COMPLETION_EMAIL_TEMPLATE;

    /**
     * Default number of minutes a persistent lock will be held.
     * This is relevant only for objects locked with the Persistent
     * option as opposed to the Transaction option. This is used by
     * the aggregation and refresh tasks to synchronize access to
     * an Identity.
     *
     * For most of the timeouts, zero means "never" but that is never
     * what you want here. If a lock is left dangling by accident,
     * it must timeout at some point. If the number is negative or zero
     * it defaults to LockInfo.DEFAULT_LOCK_TIMEOUT which is currently 5.
     */
    public static final String LOCK_TIMEOUT = "persistentLockTimeout";

    /**
     * Default number of seconds to wait for a persistent lock to
     * become available.  Used by ObjectUtil in a spin loop.  When this
     * threshold is exceeded the lock will fail and throw
     * ObjectAlreadyLockedException.  The default is ObjectUtil.DEFAULT_LOCK_TIMEOUT
     * which is currently 60.
     */
    public static final String LOCK_WAIT_TIMEOUT = "persistentLockWaitTimeout";

    /**
     * The default time is not enough for certifications which can take a long time to
     * finish. This value is defined as separate from {@link #LOCK_TIMEOUT} which 
     * will be used for other objects.
     */
    public static final String LONG_RUNNING_LOCK_TIMEOUT = "longRunningPersistentLockTimeout";
    
    public static final String PASSWORD_VALIDATION_RULE = "passwordValidationRule";

    public static final String CHECK_PASSWORD_POLICY_RULE = "Check Password Policy";
    
    public static final String APPEND_POLICY_REQS = "includePasswordPolicyDetailsInHelp";

    private static final String XML_STRICT_MODE_ATTR_NAME = "strictXmlMode";
    
    public static final String BATCH_REQUEST_APPROVER = "batchRequestApprover";
    
    public static final String REQUIRE_BATCH_REQUEST_APPROVAL = "requireBatchRequestApproval";

    public static final String DISPLAY_CLASSIFICATIONS_IN_ACCESS_REQUEST = "displayClassificationsInAccessRequest";

    /**
     * By default Execute in foreground option is disabled.
     * To enable use 
     */
    
    public static final String TASKS_EXECUTE_IN_FOREGROUND_OPTION = "tasksExecuteInForegroundOption";
    
    /**
     * 
     */
    public static final String USE_CERT_CONTEXTS_CACHE = "useCertContextsCache";

    /**
     * Defines the maximum resultset size that can be iterated over. In some
     * databases (sql server, mysql) result set data will be
     * stored in JVM memory rather than streamed from the database
     * server. This can result in out of memory exceptions.
     * If a result set row size is larger than this threshold, 
     * a list of row IDs is stored in memory and iterated 
     * a row at a time.
     */
    public static final String REPORT_RESULT_ROW_THRESHOLD = "reportingResultRowThreshold";

    /**
     * The names of all certification definition attributes that live in the system
     * configuration but can be overridden in the attributes map on the
     * certification.
     */
    public static final String[] CERTIFICATION_ATTRIBUTES =
    {
        CERTIFICATION_DELEGATION_REVIEW,
        CERTIFICATION_ENTITY_CUSTOMIZATION_RULE,
        CERTIFICATION_ENTITY_COMPLETION_RULE,
        CERTIFICATION_ENTITY_REFRESH_RULE,
        NOTIFY_REMEDIATION,
        REQUIRE_REASSIGNMENT_COMPLETION,
        AUTOMATE_SIGNOFF_ON_REASSIGNMENT,
        SUPPRESS_EMAIL_WHEN_NOTHING_TO_CERTIFY,
        AUTO_SIGN_OFF_WHEN_NOTHING_TO_CERTIFY,
        ATT_SIGNATURE_TYPE

    };

    /**
     * The names of all certification definition attributes that live in the system
     * configuration but can be overridden in the attributes map on the
     * certification.
     *
     * When a CertificationDefinition is created, the values are copied
     * from the configuration into the definition's attribute map.
     */
    public static final String[] CERTIFICATION_DEFINITION_ATTRIBUTES =
    {
        EXCLUDE_BASE_APP_ACCOUNTS,
        ALLOW_PROV_MISSING_REQUIREMENTS,
        CERTIFICATION_MITIGATION_ENABLED,
        ENABLE_ACCOUNT_REVOKE_ACTION,
        ALLOW_CERT_ENTITY_BULK_ACCOUNT_REVOCATION,
        ALLOW_LIST_VIEW_BULK_REASSIGN,
        ALLOW_LIST_VIEW_BULK_MITIGATE,
        ALLOW_CERT_ENTITY_BULK_REVOCATION,
        ALLOW_LIST_VIEW_BULK_REVOKE,
        ALLOW_LIST_VIEW_BULK_ACCOUNT_REVOKE,
        ALLOW_CERT_ENTITY_BULK_APPROVE,
        ALLOW_LIST_VIEW_BULK_APPROVE,
        ALLOW_LIST_VIEW_BULK_CLEAR_DECISIONS,
        ALLOW_CERT_ENTITY_BULK_CLEAR_DECISIONS,
        ENABLE_ACCOUNT_APPROVE_ACTION,
        CERTIFICATION_ITEM_DELEGATION_ENABLED,
        REQUIRE_BULK_CERTIFY_CONFIRMATION,
        CERTIFICATION_ENTITY_DELEGATION_ENABLED,
        CERTIFICATION_DISABLE_DELEGATION_FORWARDING,
        CERTIFICATION_DELEGATION_REVIEW,
        CERTIFICATION_LIMIT_REASSIGNMENTS,
        CERTIFICATION_REASSIGNMENT_LIMIT,
        SUPPRESS_INITIAL_NOTIFICATION,
        REQUIRE_APPROVAL_COMMENTS,
        REQUIRE_MITIGATION_COMMENTS,
        REQUIRE_REMEDIATION_COMMENTS,
        CERTIFICATION_ENTITY_CUSTOMIZATION_RULE,
        CERTIFICATION_ENTITY_COMPLETION_RULE,
        CERTIFICATION_ENTITY_REFRESH_RULE,
        NOTIFY_REMEDIATION,
        REQUIRE_REASSIGNMENT_COMPLETION,
        AUTOMATE_SIGNOFF_ON_REASSIGNMENT,
        ASSIMILATE_BULK_REASSIGNMENTS,
        CERTIFICATION_MITIGATION_POPUP_ENABLED,
        CERTIFICATION_EMAIL_TEMPLATE,
        MITIGATION_EXPIRATION_EMAIL_TEMPLATE,
        BULK_REASSIGNMENT_EMAIL_TEMPLATE,
        CHALLENGE_PERIOD_START_EMAIL_TEMPLATE,
        CHALLENGE_PERIOD_END_EMAIL_TEMPLATE,
        CHALLENGE_GENERATION_EMAIL_TEMPLATE,
        CERT_DECISION_CHALLENGED_EMAIL_TEMPLATE,
        CHALLENGE_EXPIRED_EMAIL_TEMPLATE,
        CHALLENGE_DECISION_EXPIRED_EMAIL_TEMPLATE,
        CHALLENGE_ACCEPTED_EMAIL_TEMPLATE,
        CERT_SIGN_OFF_APPROVAL_EMAIL_TEMPLATE,
        CHALLENGE_REJECTED_EMAIL_TEMPLATE,
        SUPPRESS_EMAIL_WHEN_NOTHING_TO_CERTIFY,
        AUTO_SIGN_OFF_WHEN_NOTHING_TO_CERTIFY,
        SELF_CERTIFICATION_VIOLATION_OWNER,
        CERTIFICATION_MITIGATION_DEPROVISION_ENABLED,
        CERTIFICATION_INCLUDE_CLASSIFICATIONS
    };

    /** Config name for the font to use with JFreeChart titles. */
    public static final String CHART_TITLE_FONT_NAME = "chartTitleFontName";
    /** Config name for the style of the font to use with JFreeChart titles. */
    public static final String CHART_TITLE_FONT_STYLE = "chartTitleFontStyle";
    /** Config name for the size of the font to use with JFreeChart titles. */
    public static final String CHART_TITLE_FONT_SIZE = "chartTitleFontSize";

    /** Config name for the font to use with JFreeChart body. */
    public static final String CHART_BODY_FONT_NAME = "chartBodyFontName";
    /** Config name for the style of the font to use with JFreeChart body. */
    public static final String CHART_BODY_FONT_STYLE = "chartBodyFontStyle";
    /** Config name for the size of the font to use with JFreeChart body. */
    public static final String CHART_BODY_FONT_SIZE = "chartBodyFontSize";
    
    /** Config name for the font to use with JFreeChart labels. */
    public static final String CHART_LABEL_FONT_NAME = "chartLabelFontName";
    /** Config name for the style of the font to use with JFreeChart labels. */
    public static final String CHART_LABEL_FONT_STYLE = "chartLabelFontStyle";
    /** Config name for the size of the font to use with JFreeChart labels. */
    public static final String CHART_LABEL_FONT_SIZE = "chartLabelFontSize";
    
    public static final String UNSUPPORTED_BROWSER_NOTIFICATION = "unsupportedBrowserNotification";

    /** Enable pinch zoom on mobile devices? **/
    public static final String ENABLE_PINCH_ZOOM = "enablePinchZoom";
    
    //
    // SM Listener
    //

    public static final String SM_LISTENER_HOST = "smListenerHost";
    public static final String SM_LISTENER_APPLICATIONS = "smListenerApplications";
    public static final String SM_LISTENER_REFRESH = "smListenerRefresh";
    public static final String SM_LISTENER_REFRESH_OPTIONS = "smListenerRefreshOptions";

    // 
    // ResourceEventService
    //

    // this defaults to true, you can set this to disable refresh
    public static final String RO_SERVICE_REFRESH = "resourceEventRefresh";
    public static final String RO_SERVICE_REFRESH_OPTIONS = "resourceEventRefreshOptions";

    // syslog
    public static final String ENABLE_SYSLOG = "enableSyslog";
    public static final String SYSLOG_LEVEL = "syslogLevel";
    public static final String SYSLOG_PRUNE_AGE = "syslogPurgeAge";

    // provisioning transaction log
    public static final String ENABLE_PROVISIONING_TRANSACTION_LOG = "enableProvisioningTransactionLog";
    public static final String PROVISIONING_TRANSACTION_LOG_LEVEL = "provisioningTransactionLogLevel";
    public static final String PROVISIONING_TRANSACTION_LOG_PRUNE_AGE = "provisioningTransactionLogPruneAge";

    /**
     * The default electronic signature to use when creating certifications. 
     */
    public static final String REQUIRE_ELECTRONIC_SIGNATURE = "defaultElectronicSignature";

    // Localization
    
    /** 
     * The default language for localization.
     * This was pulled out ObjectConfig:ManagedAttribute in 6.0.
     * This is expected to be a String.
     */
    public static final String DEFAULT_LANGUAGE = "defaultLanguage";

    /**
     * The supported list of languages for localization.
     * This was pulled out ObjectConfig:ManagedAttribute in 6.0.  
     * This is expected to be a List<String>.
     */
    public static final String SUPPORTED_LANGUAGES = "supportedLanguages";
    
    public static final String ENABLE_LOCALIZED_APP_DESCRIPTIONS = "enableLocalizeApplicationDescriptions";
    public static final String ENABLE_LOCALIZED_ROLE_DESCRIPTIONS = "enableLocalizeRoleDescriptions";
    public static final String ENABLE_LOCALIZED_POLICY_DESCRIPTIONS ="enableLocalizePolicyDescriptions";
    public static final String ENABLE_LOCALIZED_MANAGED_ATTRIBUTE_DESCRIPTIONS = "enableLocalizeManagedAttributeDescriptions";

    /**
     * Flag for enabling duplicate accounts feature. 
     */
    public static final String ALLOW_DUPLICATE_ACCOUNTS = "allowDuplicateAccounts";

    /**
     * Flag for enabling multiple role assignment. This toggles whether or not
     * multiple assignment can be configured not whether it is turned on or off.
     */
    public static final String ENABLE_MODELER_MULTIPLE_ASSIGNMENTS = "enableModelerMultipleAssignments";

    /**
     * Global flag for roles allowing multiple assignment of assignable roles.
     */
    public static final String ALLOW_MULTIPLE_ROLE_ASSIGNMENTS = "allowMultipleRoleAssignments";

    /**
     * Flag to enable one-way hashing of secret Identity values.
     */
    public static final String HASH_IDENTITY_SECRETS = "hashIdentitySecrets";

    /**
     * Number of hashing iterations. only applicable when one-way hashing is enabled.
     */
    public static final String HASHING_ITERATIONS = "hashingIterations";

    /**
     * Flag for enabling role propagation when the role is saved
     */
    public static final String ALLOW_ROLE_PROPAGATION = "allowRolePropagation";
    
    /**
     * Flag to force all bundles to merge templates
     */
    public static final String FORCE_ROLE_MERGE_TEMPLATE = "forceRoleMergeTemplate";

    /**
     * Flag for determining whether or not to retain assigned entitlements when a detected
     * role is removed from an identity either through LCM or a certification.
     */
    public static final String RETAIN_ASSIGNED_ENTITLEMENTS_DETECTED_ROLE = "retainAssignedEntitlementsDetectedRole";

    public static final String RETAIN_ASSIGNED_ENTITLEMENTS_ASSIGNED_ROLE = "retainAssignedEntitlementsAssignedRole";

    // fulltext
    
    /**
     * True if fulltext search is enabled for the LCM request pages.
     */
    public static final String LCM_ENABLE_FULLTEXT = "lcmEnableFulltext";

    /**
     * True if fulltext search is enabled for the entitlement catalog page.
     */
    public static final String ENTITLEMENT_CATALOG_FULLTEXT = "entitlementCatalogFulltext";

    /**
     * True if fulltext search is enabled for the entitlement search pages.
     */
    public static final String ENTITLEMENT_FULLTEXT = "entitlementFulltext";

    /**
     * Maximum number of results to pull in from a fulltext search on the 
     * entitlement catalog page.  If zero defaults to 1000.
     */
    public static final String ENTITLEMENT_FULLTEXT_MAX_RESULT = "entitlementFulltextMaxResult";


    /**
     * The base path for the location of the full text indices.
     */
    public static final String LCM_FULL_TEXT_INDEX_PATH = "lcmFullTextIndexPath";

    /**
     * String name of the FullTextIndex object to use for user access searches
     */
    public static final String LCM_USER_ACCESS_FULLTEXT_INDEX = "lcmUserAccessFulltextIndex";
    
    /**
     * List of attributes that should not be used with a full text search
     */
    public static final String LCM_SKIP_FULLTEXT_FIELDS = "lcmSkipFulltextFields";

    /**
     * The number of days before a ProvisioningRequest will expire.
     * ProvisioningRequests are created when the Connector returns
     * a status of QUEUED to prevent sending duplicate requests
     * with the same contents. They are pruned during aggregation
     * but need a finite lifespan in case the request is never processed.
     */
    public static final String PROVISIONING_REQUEST_EXPIRATION_DAYS = 
        "provisioningRequestExpirationDays";

    /**
     * Set to something non-null after upgrading the QuickLink 
     * enable/disable flags. See QuickLinkUpgrader.java for
     * the details. This is only expected to live
     * for 6.0 GA and will be removed after the upgrade.
     */
    public static final String QUICK_LINK_UPGRADE = "quickLinkUpgrade";
    
    // Identity Provisioning
    public static final String CREATE_IDENTITY_FORM = "createIdentityForm";
    public static final String UPDATE_IDENTITY_FORM = "updateIdentityForm";
    public static final String REGISTER_FORM = "registerForm";

    // Encryption/decryption
    public static final String SECRET_PROVISION_ATTRIBUTE_NAMES = "secretProvisionAttributeNames";

    // Forms Types
    // Currently only Application, Role and Workflow Type are supported for creating forms.
    public static final String APPLICATION_POLICY_FORM = "form_editor_application_type_label";
    public static final String ROLE_POLICY_FORM = "form_editor_role_type_label";
    public static final String WORKFLOW_FORM = "form_editor_workflow_type_label";

    public static final String ACCESS_REQUEST_TYPES = "accessRequestTypes";

    // The workgroup that initially owns self-service registration workflows
    public static final String SELF_REGISTRATION_WORKGROUP = "selfRegistrationGroup";
    
    /**
     * This setting determines whether IdentityIQ can be called inside an iFrame?
     * The default behavior is NO.
     * 
     * However, if this value is not set (for example, null) in SystemConfiguration then iFrames will be allowed
     * to maintain backward compatibility for upgrades.
     */
    public static final String IIQ_ALLOW_IFRAME = "allowiFrame";

    /**
     * Whether in the input fields 'autocomplete = "off"/"on" is allowed.
     */
    public static final String IIQ_ALLOW_AUTOCOMPLETE = "allowAutoComplete";

    /**
     * If there is an error in executing a request, this determines if it should be preserved or not.
     *
     */
    public static final String REQUEST_PRESERVE_IF_ERRORS = "requestPreserveIfErrors";
    
    /** 
     * For CEF Log File consuming event data. 
     */
    public static final String ATT_CEF_LOGFILE_TRANSFORMATION_RULE = "cefActivityTransformRule";
    public static final String ATT_CEF_LOGFILE_CORRELATION_RULE = "cefActivityCorrelationRule";
    public static final String ATT_CEF_LOGFILE_ACTIVITYFEED_DATASOURCE = "cefLogFileFieldsFilePath";
    public static final String ATT_CEF_LINK_ATTRIBUTE_NAME = "cefLinkAttributeName";
    public static final String ATT_CEF_USER_NAME = "cefUserName";
    public static final String CEF_LOG_FILE_COLLECTOR_TYPE = "CEF Log File";
    public static final String CEF_LOG_FILE_HEADER = "cefHeader";
    public static final String CEF_LOG_FILE_FORMAT = "cefFormat";
    public static final String CEF_LOG_FILE_VERSION = "cef_version";
    public static final String CEF_LOG_FILE_DEVICE_VENDOR = "device_vendor";
    public static final String CEF_LOG_FILE_DEVICE_PRODUCT = "device_product";
    public static final String CEF_LOG_FILE_DEVICE_VERSION = "device_version";
    public static final String CEF_LOG_FILE_SIGNATURE_ID = "signatureId";
    public static final String CEF_LOG_FILE_NAME = "name";
    public static final String CEF_LOG_FILE_SEVERITY = "severity";
    //syslog table column to get severity value
    public static final String CEF_LOG_FILE_SEVERITY_SYSLOG_COLUMN = "eventLevel";
    public static final String CEF_LOG_FILE_SEVERITY_SYSLOG_SEVERITY = "cefSyslogSeverity";
    public static final String CEF_LOG_FILE_EXTENSION = "extension";

    public static final String CEF_LOG_FILE_IDENTITY_EXTENSION = "identity_extension";
    public static final String CEF_LOG_FILE_AUDIT_EXTENSION = "audit_extension";
    public static final String CEF_LOG_FILE_SYSLOG_EXTENSION = "syslog_extension";
    public static final String CEF_LOG_FILE_LINK_EXTENSION = "link_extension";

    /**
     * Enable asynchhrounous role and policy cache refreshes.
     * If this is set in the system configuration it applies system-wide.
     * This is usually not what you want, synchronous refreshes are
     * fine for background tasks you only really need them for
     * foreground LCM requests.
     *
     * This is also the name of a workflow argument that can
     * be passed to the checkPolicies library method.
     */
    public static final String ASYNC_CACHE_REFRESH = "asyncCacheRefresh";

    /**
     * Disable the policy cache entirely.
     * This is a hidden option that can be used if the policy cache causes
     * problems. After it has hardened for a few releases this can be removed.
     * The one place where this might be necessary is for one customer that
     * dynamically modified their policy definitions as the scan ran.
     */
    public static final String DISABLE_POLICY_CACHE = "disablePolicyCache";

    /**
     * A list of forms type that should be displayed under SystemSetup Forms.
     */
    public static final String FORMS_TYPE_FILTER = "formsTypeFilter";

    /**
     * A list of QuickLink objects to display as default set of QuickLinkCards on home page
     */
    public static final String DEFAULT_QUICKLINK_CARDS = "defaultQuickLinkCards";

    /**
     * List of default widgets to display on home page
     */
    public static final String DEFAULT_HOME_WIDGETS = "defaultHomeWidgets";

    /**
     * A list of QuickLink objects to display on the mobile home page.
     */
    public static final String MOBILE_QUICKLINK_CARDS = "mobileQuickLinkCards";
    
    public static final String WORK_ITEM_NOTIFICATIONS_INTERVAL = "workItemNotificationsInterval";
    
    /**
     * Integer value representing max number of SCIM resources returned in a response
     */
    public static final String SCIM_SEARCH_MAX_RESULTS = "scimSearchMaxResults";

    /**
     * Boolean value to turn off identity snapshotting during SCIM updates.
     */
    public static final String SCIM_TRIGGER_SNAPSHOTS = "scimTriggerSnapshots";

    /**
     * Attribute key responsible for holding the OAuthConfiguration object.
     */
    public static final String OAUTH_CONFIGURATION = "oAuthConfiguration";

    /**
     * Key to determine if the revocation dialog needs to be shown or not
     */
    public static final String SHOW_CERTIFICATION_ROLE_REVOCATION_DETAILS = "showCertificationRoleRevocationDetails";

    /**
    * Map keying class name with a CSV list of properties are blocked from being part of a suggest.
    * Can use * to block ALL properties on a class.
    */
    public static final String COLUMN_SUGGEST_BLACKLIST = "columnSuggestBlacklist";

    /**
     * List of globally accessible class and column names for the generic suggest endpoint (see GlobalSuggestAuthorizerContext for usage)
     */
    public static final String SUGGEST_COLUMN_WHITELIST = "suggestColumnWhitelist";

    /**
     * List of globally accessible class names for the generic suggest endpoint (see GlobalSuggestAuthorizerContext for usage)
     */
    public static final String SUGGEST_OBJECT_WHITELIST = "suggestObjectWhitelist";

    // PAM
    public static final String PAM_PROVISIONING_ENABLED = "pamProvisioningEnabled";
    public static final String PAM_MODIFY_PRIVILEGED_DATA_ENABLED = "pamModifyPrivilegedDataEnabled";
    public static final String PAM_CREATE_CONTAINER_ENABLED = "pamCreateContainerEnabled";
    public static final String PAM_OWNER_CAN_EDIT_CONTAINER ="pamOwnerCanEditContainer";
    public static final String PAM_MAX_SELECTABLE_IDENTITIES = "pamMaxSelectableIdentities";
    public static final String PAM_WORKFLOW_IDENTITY_PROVISIONING = "pamWorkflowIdentityProvisioning";
    public static final String PAM_PRIVILEGED_ITEM_SELECTOR_RULE = "pamPrivilegedItemSelectorRule";

    // Attachments
    public static final String ATTACHMENTS_ENABLED = "attachmentsEnabled";
    public static final String ATTACHMENTS_MAX_FILE_SIZE = "attachmentsMaxFileSize";
    public static final String ATTACHMENTS_MAX_FILE_SIZE_LIMIT = "attachmentsMaxFileSizeLimit";
    public static final String ATTACHMENTS_ALLOWED_FILE_TYPES = "attachmentsAllowedFileTypes";
    public static final String ATTACHMENTS_FILENAME_ALLOWED_SPECIAL_CHARACTERS = "attachmentsFilenameAllowedSpecialCharacters";
    public static final String ATTACHMENT_CONFIG_RULES = "attachmentConfigRules";

    // Require comments for access request items
    public static final String REQUIRE_ACCESS_REQUEST_COMMENTS_ALL = "requireAccessRequestCommentsAll";
    public static final String COMMENT_CONFIG_RULES = "commentConfigRules";

    /**
     * Flag to restore orginal COMMITTED provisioning result for manual object requests
     */
    public static final String COMMIT_MANUAL_OBJECT_REQUESTS = "commitManualObjectRequests";
    
    /**
     * List of OWASP Sanitizer Policies supported for HTML descriptions. Possible values are
     * BLOCKS, FORMATTING, TABLES, STYLES, LINKS, IMAGES.  Note that modifying this list could lead to undesired
     * UI behavior
     */
    public static final String HTML_SANITIZER_POLICIES = "htmlSanitizerPolicies";

    /**
     * Maximum number of results we support for a list result from REST and Bean data sources.
     * This defaults to 100.
     */
    public static final String MAX_LIST_RESULT_SIZE = "maxListResultSize";

    /**
     * The default number of results we support for a list result from REST and Bean data sources.
     * If the limit parameter is not specified for those endpoints, we will use this configured value.
     * Default is 25.
     */
    public static final String DEFAULT_LIST_RESULT_SIZE = "defaultListResultSize";

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Static cache for the System Configuration object.
     *
     * The getSystemConfig method should be used by the UI and other
     * things that need to access the sysconfig frequently and do not
     * want to fetch it from the database every damn time a page is rendered.
     */
    static CacheReference _systemConfigCache;

    /**
     * Static cache for the IdentitySelector configuration object.
     */
    private static CacheReference _identitySelectorConfigCache;

    /**
     * Static cache for the IdentityAI configuration object.
     */
    private static CacheReference _identityAIConfigCache;

    /**
     * Static cache for the FAM configuration object.
     */
    private static CacheReference _FAMConfigCache;

    /**
     * Static cache for the RapidSetup configuration object.
     */
    private static CacheReference _RapidSetupConfigCache;

    /**
     * The attributes(which is a Map) in the configuration.
     */
    private Attributes<String,Object> _attributes;

    //////////////////////////////////////////////////////////////////////
    //
    // Constructors/Properties
    //
    //////////////////////////////////////////////////////////////////////

    public Configuration() {
        _attributes = new Attributes<String, Object>();
    }

    @XMLProperty(mode=SerializationMode.INLINE)
    public Attributes<String, Object> getAttributes() {
        return _attributes;
    }

    public void setAttributes(Attributes<String, Object> a) {
        _attributes = a;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Cacheable Interface
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * @exclude
     * Return true if we want to cache this object. 
     * There are many Configuration objects but we only provide
     * global caches for a few of them.
     */
    @Override
    public boolean isCacheable(SailPointObject config) {

        String name = config.getName();
        return (OBJ_NAME.equals(name) || IDENTITY_SELECTOR_CONFIG.equals(name) ||
                IAI_CONFIG.equals(name) || FAM_CONFIG.equals(name) ||
                RAPIDSETUP_CONFIG.equals(name));
    }

    /**
     * @exclude
     * Called by CacheService when registering an object.
     */
    @Override
    public void setCache(CacheReference ref) {

        // we maintain two of these
        Configuration config = (Configuration)ref.getObject();
        String name = config.getName();

        if (OBJ_NAME.equals(name))
            _systemConfigCache = ref;

        else if (IDENTITY_SELECTOR_CONFIG.equals(name))
            _identitySelectorConfigCache = ref;

        else if (IAI_CONFIG.equals(name))
            _identityAIConfigCache = ref;

        else if (FAM_CONFIG.equals(name))
            _FAMConfigCache = ref;

        else if (RAPIDSETUP_CONFIG.equals(name))
            _RapidSetupConfigCache = ref;

    }

    /**
     * Return the cached system configuration object.
     */
    public static Configuration getSystemConfig() {
        Configuration config = null;
        if (_systemConfigCache != null) 
            config = (Configuration)_systemConfigCache.getObject();

        if (config == null) {
            // must be in the bootstrapping window
            config = new Configuration();
        }

        return config;
    }

    /**
     * Return the cached identity selector configuration object.
     */
    public static Configuration getIdentitySelectorConfig() {
        Configuration config = null;
        if (_identitySelectorConfigCache != null)
            config = (Configuration)_identitySelectorConfigCache.getObject();

        if (config == null) {
            // must be in the bootstrapping window
            config = new Configuration();
        }

        return config;
    }

    /**
     * Return the cached identityAI configuration object.
     */
    public static Configuration getIdentityAIConfig() {
        Configuration config = null;
        if (_identityAIConfigCache != null)
            config = (Configuration)_identityAIConfigCache.getObject();

        if (config == null) {
            // must be in the bootstrapping window
            config = new Configuration();
            config.setName(IAI_CONFIG);
        }

        return config;
    }

    /**
     * Return the cached FAM configuration object.
     */
    public static Configuration getFAMConfig() {
        Configuration config = null;
        if (_FAMConfigCache != null)
            config = (Configuration)_FAMConfigCache.getObject();

        if (config == null) {
            // must be in the bootstrapping window
            config = new Configuration();
            config.setName(FAM_CONFIG);
        }

        return config;
    }

    /**
     * Return the cached RapidSetup configuration object.
     */
    public static Configuration getRapidSetupConfig() {
        Configuration config = null;
        if (_RapidSetupConfigCache != null)
            config = (Configuration) _RapidSetupConfigCache.getObject();

        if (config == null) {
            // must be in the bootstrapping window
            config = new Configuration();
            config.setName(RAPIDSETUP_CONFIG);
        }

        return config;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Convenience accessors
    //
    //////////////////////////////////////////////////////////////////////

    public Object get(String name) {
        return (_attributes != null) ? _attributes.get(name) : null;
    }

    public boolean containsKey(String key) {
        return (_attributes != null) ? _attributes.containsKey(key) : null;
    }

    public void put(String name, Object value) {
        if (_attributes == null)
            _attributes = new Attributes<String,Object>();
        _attributes.put(name, value);
    }

    public void put(String name, int value) {
        if (_attributes == null)
            _attributes = new Attributes<String,Object>();
        _attributes.put(name, new Integer(value));
    }

    public String getString(String name) {
        return (_attributes != null) ? _attributes.getString(name) : null;
    }

    public int getInt(String name) {
        return (_attributes != null) ? _attributes.getInt(name) : null;
    }

    public Integer getInteger(String name) {
        return (_attributes != null) ? _attributes.getInteger(name) : null;
    }

    public long getLong(String name) {
        return (_attributes != null) ? _attributes.getLng(name) : null;
    }

    public boolean getBoolean(String name) {
        return (_attributes != null) ? _attributes.getBoolean(name) : null;
    }

    public boolean getBoolean(String name, boolean dflt) {
        return (_attributes != null) ? _attributes.getBoolean(name, dflt) : dflt;
    }

    public Date getDate(String name) {
        return (_attributes != null) ? _attributes.getDate(name) : null;
    }

    @SuppressWarnings("rawtypes")
    public List getList(String name) {
        return (_attributes != null) ? _attributes.getList(name) : null;
    }

    public List<String> getStringList(String name) {
        return (_attributes != null) ? _attributes.getStringList(name) : null;
    }

    public void remove(String name) {
        if (_attributes != null)
            _attributes.remove(name);
    }

    public boolean containsAttribute(String name) {
        return (_attributes != null) ? _attributes.containsKey(name) : false;
    }

    private static boolean xmlStrictMode = false;
    private static boolean isXmlStrictModeLoaded = false;
    public static boolean isXmlStrictMode() {
        if (!isXmlStrictModeLoaded) {
            if (_systemConfigCache == null) {
                // bootstrap mode
                return true;
            }
            xmlStrictMode = getSystemConfig().getBoolean(XML_STRICT_MODE_ATTR_NAME, false);
            isXmlStrictModeLoaded = true;
        }
        return xmlStrictMode;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // SailPointObject methods
    //
    //////////////////////////////////////////////////////////////////////

    @Override
    public Object clone() throws CloneNotSupportedException {
        return super.clone();
    }

    public static class SmtpConfiguration {
        
        private static final String PREFIX = "smtp_";
        
        public static String Username = PREFIX + "username";
        public static String Password = PREFIX + "password";
        public static String EncryptionType = PREFIX + "encryptionType";
        public static String SslSocketFactoryClass = PREFIX + "sslSocketFactoryClass";
        
    }
}
