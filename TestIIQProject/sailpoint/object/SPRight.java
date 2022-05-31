/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
/* 
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */

package sailpoint.object;

import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;

/**
 * A SPRight serves as a low-level task-based right within IdentityIQ. These are
 * used by IdentityIQ to to restrict access to certain parts of the UI or to
 * disable UI controls and make them read-only. Each right should denote both an
 * action and a target - for example a right named ViewApplication would be
 * required to have a read-only view of a certification, while
 * ManageCertification would be required to see a read/write view of a
 * certification. The "manage" right denotes create, update, and delete access.
 * There is also the concept of a "full access" type of right that provides
 * all-or-none access to some part of the system. In other words,
 * FullAccessGroup will allow viewing, creating, updating, deleting groups,
 * while lack of this right will completely disable any access to groups. These
 * are intentionally fairly coarse-grained, but can be split out into individual
 * rights as needed. When this happens, adding an
 * "implied rights" list to SPRight that allows for easier upgrades should be considered.
 * 
 * These are maintained as SailPointObjects rather than an enumeration because
 * they are extensible. New SPRights can be added to the system by a customer to
 * add specific access control around objects in the system (for example - reports).
 * 
 * In addition to displayName, name and description from
 * SailPointObject are also used.
 */
@XMLClass
public class SPRight extends SailPointObject {

    ////////////////////////////////////////////////////////////////////////////
    //
    // RIGHT NAMES
    //
    ////////////////////////////////////////////////////////////////////////////
    
    // Dashboard rights

    // Identity rights
    public static final String ViewIdentity = "ViewIdentity";
    public static final String SetIdentityPassword = "SetIdentityPassword";
    public static final String SetIdentityCapability = "SetIdentityCapability";
    public static final String SetIdentityControlledScope = "SetIdentityControlledScope";
    public static final String SetIdentityForwarding = "SetIdentityForwarding";
    public static final String DeleteIdentityLink = "DeleteIdentityLink";
    public static final String MoveIdentityLink = "MoveIdentityLink";
    public static final String DeleteIdentitySnapshot = "DeleteIdentitySnapshot";
    public static final String MonitorIdentityActivity = "MonitorIdentityActivity";
    public static final String MonitorIdentityHistory = "MonitorIdentityHistory";
    public static final String AssignIdentityRole = "AssignIdentityRole";
    public static final String MonitorIdentityEvents = "MonitorIdentityEvents";
    public static final String UnlockIdentity = "UnlockIdentity";
    
    // Define tab rights
    public static final String ViewApplication = "ViewApplication";
    public static final String ManageApplication = "ManageApplication";
    public static final String ViewBusinessProcess = "ViewBusinessProcess";
    public static final String ManageBusinessProcess = "ManageBusinessProcess";
    public static final String ViewRole = "ViewRole";
    public static final String ManageRole = "ManageRole";
    public static final String RunRoleImpactAnalysis = "RunRoleImpactAnalysis";
    public static final String FullAccessRoleMining = "FullAccessRoleMining";
    public static final String FullAccessGroup = "FullAccessGroup";
    public static final String ManageWorkgroup = "ManageWorkgroup";
    public static final String SetWorkgroupCapability = "SetWorkgroupCapability";
    public static final String SetWorkgroupControlledScope = "SetWorkgroupControlledScope";
    public static final String FullAccessActivityCategory = "FullAccessActivityCategory";
    public static final String ViewPolicy = "ViewPolicy";
    public static final String ManagePolicy = "ManagePolicy";
    public static final String FullAccessIdentityRiskModel = "FullAccessIdentityRiskModel";
    public static final String FullAccessApplicationRiskModel = "FullAccessApplicationRiskModel";
    public static final String FullAccessWorkflows = "FullAccessWorkflows";

    // RapidSetup rights
    public static final String ViewRapidSetup = "ViewRapidSetup";
    public static final String FullAccessRapidSetup = "FullAccessRapidSetup";
    public static final String ViewRapidSetupConfiguration = "ViewRapidSetupConfiguration";
    public static final String FullAccessRapidSetupConfiguration = "FullAccessRapidSetupConfiguration";

    // Role Mgmt rights
    public static final String ManageOrganizationalRoles = "ManageOrganizationalRoles";
    public static final String ManageITRoles = "ManageITRoles";
    public static final String ManageBusinessRoles = "ManageBusinessRoles";
    public static final String ManageEntitlementRoles = "ManageEntitlementRoles";
    public static final String ManageRapidSetupBirthrightRoles = "ManageRapidSetupBirthrightRoles";

    // Monitor tab rights
    public static final String FullAccessCertificationSchedule = "FullAccessCertificationSchedule";
    public static final String ViewGroupCertification = "ViewGroupCertification";
    public static final String FullAccessTask = "FullAccessTask";
    public static final String ReadTaskResults = "ReadTaskResults";

    // Analyze tab rights
    public static final String ViewActivity = "ViewActivity";
    public static final String ViewAuditLog = "ViewAuditLog";
    public static final String ViewCertifications = "ViewCertifications";
    public static final String ViewRoles = "ViewRoles";
    public static final String FullAccessReport = "FullAccessReport";
    public static final String DeleteSignOffResult = "DeleteSignOffResult";
    public static final String FullAccessIdentityCorrelation = "FullAccessIdentityCorrelation";
    public static final String ViewAccountGroups = "ViewAccountGroups";
    public static final String ViewProcessInstrumentation = "ViewProcessInstrumentation";
    public static final String ViewIdentityRequest = "ViewIdentityRequest";
    public static final String ViewSyslog = "ViewSyslog";
    public static final String ViewLink = "ViewLink";
    public static final String ViewPopulations = "ViewPopulations";
    public static final String ViewGroups = "ViewGroups";
    public static final String ViewWorkgroup = "ViewWorkgroup";

    // Manage tab rights
    public static final String FullAccessCertifications = "FullAccessCertifications";
    public static final String CertifyAllCertifications = "CertifyAllCertifications";
    public static final String FullAccessPolicyViolation = "FullAccessPolicyViolation";
    public static final String FullAccessIdentityRisk = "FullAccessIdentityRisk";
    public static final String FullAccessApplicationRisk = "FullAccessApplicationRisk";
    public static final String FullAccessWorkItems = "FullAccessWorkItems";
    public static final String FullAccessRequest = "FullAccessRequest";
    public static final String FullAccessIdentityRequest = "FullAccessIdentityRequest";
    public static final String FullAccessBatchRequest = "FullAccessBatchRequest";

    // System tab rights
    public static final String FullAccessLoginConfig = "FullAccessLoginConfig";
    public static final String FullAccessIdentityMapping = "FullAccessIdentityMapping";
    public static final String FullAccessAccountMapping = "FullAccessAccountMapping";
    public static final String FullAccessSystemConfig = "FullAccessSystemConfig";
    public static final String ManageScope = "ManageScope";
    public static final String ViewScope = "ViewScope";
    public static final String FullAccessTimePeriod = "FullAccessTimePeriod";
    public static final String FullAccessAuditConfig = "FullAccessAuditConfig";
    public static final String ImportFromFile = "ImportFromFile";
    public static final String FormsAdmin = "FormsAdmin";
    public static final String FullAccessDynamicScope = "FullAccessDynamicScope";
    public static final String ViewQuickLinks = "ViewQuickLinks";

    // Debug tab rights
    public static final String FullAccessAboutPage = "FullAccessAboutPage";
    public static final String FullAccessDebugPage = "FullAccessDebugPage";
    public static final String FullAccessMemoryPage = "FullAccessMemoryPage";
    public static final String FullAccessBeansPage = "FullAccessBeansPage";
    public static final String FullAccessThreadsPage = "FullAccessThreadsPage";
    public static final String FullAccessMetersPage = "FullAccessMetersPage";
    
    // Managed Attribute rights
    public static final String ManagedAttributePropertyAdministrator = "ManagedAttributePropertyAdministrator";
    public static final String ManagedAttributeProvisioningAdministrator = "ManagedAttributeProvisioningAdministrator";
    public static final String FullAccessForms = "FullAccessForms";
    
    // SCIM rights
    public static final String ReadSCIMResourceType = "ReadSCIMResourceType";
    public static final String ReadSCIMSchema = "ReadSCIMSchema";
    public static final String ReadSCIMUser = "ReadSCIMUser";
    public static final String CreateSCIMUser = "CreateSCIMUser";
    public static final String UpdateSCIMUser = "UpdateSCIMUser";
    public static final String DeleteSCIMUser = "DeleteSCIMUser";
    public static final String ReadSCIMApplication = "ReadSCIMApplication";
    public static final String ReadSCIMAccount = "ReadSCIMAccount";
    public static final String CreateSCIMAccount = "CreateSCIMAccount";
    public static final String UpdateSCIMAccount = "UpdateSCIMAccount";
    public static final String DeleteSCIMAccount = "DeleteSCIMAccount";
    public static final String ReadSCIMEntitlement = "ReadSCIMEntitlement";
    public static final String ReadSCIMRole = "ReadSCIMRole";
    public static final String ReadSCIMWorkflow = "ReadSCIMWorkflow";
    public static final String ReadSCIMTaskResult = "ReadSCIMTaskResult";
    public static final String ReadSCIMObjectConfig = "ReadSCIMObjectConfig";
    public static final String ReadSCIMLaunchedWorkflow = "ReadSCIMLaunchedWorkflow";
    public static final String CreateSCIMLaunchedWorkflow = "CreateSCIMLaunchedWorkflow";
    public static final String ReadSCIMPolicyViolation = "ReadSCIMPolicyViolation";
    public static final String ReadSCIMCheckedPolicyViolation = "ReadSCIMCheckedPolicyViolation";
    public static final String CreateSCIMCheckedPolicyViolation = "CreateSCIMCheckedPolicyViolation";
    public static final String CreateSCIMAlert = "CreateSCIMAlert";
    public static final String ReadSCIMAlert = "ReadSCIMAlert";
    
    //OAuth client configuration rights
    public static final String FullAccessOAuthClientConfiguration = "FullAccessOAuthClientConfiguration";
    public static final String ViewOAuthClientConfiguration = "ViewOAuthClientConfiguration";
    
    //ProvisioningT Transaction rights
    public static final String FullAccessProvisioningTransaction = "FullAccessProvisioningTransaction";
    public static final String ViewProvisioningTransaction = "ViewProvisioningTransaction";

    // Plugin rights
    public static final String FullAccessPlugin = "FullAccessPlugin";

    //Alert rights
    public static final String ViewAlert = "ViewAlert";

    //AlertDefinition Rights
    public static final String ViewAlertDefinition = "ViewAlertDefinition";
    public static final String FullAccessAlertDefinition = "FullAccessAlertDefinition";

    // Monitoring rights
    public static final String ViewEnvironmentMonitoring = "ViewEnvironmentMonitoring";
    public static final String FullAccessEnvironmentMonitoring = "FullAccessEnvironmentMonitoring";

    //TaskManagement rights
    public static final String ViewTaskManagement = "ViewTaskManagement";
    public static final String FullAccessTaskManagement = "FullAccessTaskManagement";

    // FAM rights
    public static final String ViewFAMNavigationMenu = "ViewFAMNavigationMenu";
    public static final String ViewFAMAdminWidgets = "ViewFAMAdminWidgets";
    public static final String FullAccessFAMConfiguration = "FullAccessFAMConfiguration";

    //PAM rights
    public static final String FullAccessPAM = "FullAccessPAM";
    public static final String ViewPAMDetail = "ViewPAMDetail";
    public static final String CreatePAMContainer = "CreatePAMContainer";
    public static final String PAMModifyIdentities = "PAMModifyIdentities";
    public static final String PAMModifyPrivilegedItems = "PAMModifyPrivilegedItems";

    // Identity Operations Rights
    public static final String FullAccessTerminateIdentity = "FullAccessTerminateIdentity";

    // Web services rights

    /**
     * @deprecated This has been broken down into more granular rights. {@link SPRight.WebServiceRights}
     */
    @Deprecated
    public static final String WebServices = "WebServices";
    public static enum WebServiceRights {
        PingWebService, GetConfigurationWebService, CheckRolePoliciesWebService, RolesAssignablePermitsWebService,
        ShowIdentityWebService, GetIdentityListWebService, CheckAuthorizationWebService, GetTaskResultStatusWebService,
        GetLinksWebService, CheckPasswordPolicyWebService, IdentityCreateWebService, AggregateAccountWebService,
        LaunchWorkflowWebService, CancelWorkflowWebService, RemoteLoginWebService, PasswordInterceptWebService,
        IdentityCreateOrUpdateWebService, GetWorkItemCountWebService, GetIdentityNameByLinkWebService
    }

    // Widgets
    public static final String CertificationCampaignsWidget = "CertificationCampaignsWidget";

    public static final String ViewRiskScoreChart = "ViewRiskScoreChart";
    public static final String ViewApplicationRiskScoreChart = "ViewApplicationRiskScoreChart";


    ////////////////////////////////////////////////////////////////////////////
    //
    // FIELDS
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * A message key with the displayable name of a right.
     */
    private String displayName;


    ////////////////////////////////////////////////////////////////////////////
    //
    // CONSTRUCTORS
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Constructor.
     */
    public SPRight() {
        super();
    }

    
    ////////////////////////////////////////////////////////////////////////////
    //
    // GETTERS AND SETTERS
    //
    ////////////////////////////////////////////////////////////////////////////

    public String getDisplayName() {
        return displayName;
    }

    @XMLProperty
    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }
}
