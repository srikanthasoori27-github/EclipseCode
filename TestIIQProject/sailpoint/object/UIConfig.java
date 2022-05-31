/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * Various global configuration items for the UI.
 * Author: Jeff
 */
package sailpoint.object;

import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.tools.Util;
import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;
import sailpoint.web.help.ContextualHelpItem;

@XMLClass
public class UIConfig extends SailPointObject implements Cloneable, Cacheable {

    private static Log log = LogFactory.getLog(UIConfig.class);

    //////////////////////////////////////////////////////////////////////
    //
    // Constants
    //
    //////////////////////////////////////////////////////////////////////

    // Singleton database object name
    public static final String OBJ_NAME = "UIConfig";

    //
    // property names
    //
    
    public static final String IDENTITY_TABLE_COLUMNS =
        "identityTableColumns";

    public static final String IDENTITY_HISTORY_TABLE_COLUMNS =
        "identityHistoryTableColumns";

    public static final String IDENTITY_HISTORY_BY_ITEM_TABLE_COLUMNS =
        "identityHistoryByItemTableColumns";

    public static final String UI_IDENTITY_HISTORY_BY_ITEM_TABLE_COLUMNS =
        "uiIdentityHistoryByItemTableColumns";

    public static final String IDENTITY_SEARCH_ATTRIBUTES =
        "identitySearchAttributes";

    public static final String IDENTITY_VIEW_ATTRIBUTES =
        "identityViewAttributes";
    
    public static final String DASHBOARD_OUTBOX_TABLE_COLUMNS = 
        "dashboardOutboxTableColumns";
    
    public static final String DASHBOARD_INBOX_TABLE_COLUMNS = 
        "dashboardInboxTableColumns";
    
    public static final String MANAGE_WORKITEMS_TABLE_COLUMNS = 
        "manageWorkItemsTableColumns";
    
    public static final String WORKITEMS_ARCHIVE_TABLE_COLUMNS = 
        "workItemsArchiveTableColumns";
    
    public static final String DASHBOARD_SIGNOFF_TABLE_COLUMNS = 
        "dashboardSignoffStatusTableColumns";
    
    public static final String DASHBOARD_APPLICATION_STATISTICS_TABLE_COLUMNS = 
        "dashApplicationStatisticsColumns";
    
    public static final String DASHBOARD_CERTIFICATION_COMPLETION_TABLE_COLUMNS = 
        "dashCertificationCompletionColumns";

    public static final String CERTIFICATION_ITEM_TABLE_COLUMNS =
        "certificationItemTableColumns";

    public static final String CERTIFICATION_ITEM_DATA_OWNER_TABLE_COLUMNS =
        "certificationItemDataOwnerTableColumns";
    
    public static final String REMEDIATION_TABLE_COLUMNS = 
        "certificationRemediationTableColumns";
    
    public static final String CERTIFICATION_ENTITY_TABLE_COLUMNS =
        "certificationEntityTableColumns";

    public static final String CERTIFICATION_ENTITY_DATA_OWNER_TABLE_COLUMNS =
        "certificationEntityDataOwnerTableColumns";
    
    public static final String MY_CERTIFICATIONS_TABLE_COLUMNS =
        "myCertificationsTableColumns";
    
    public static final String CERTIFICATION_ACCOUNT_GROUP_TABLE_COLUMNS =
        "certificationAccountGroupTableColumns";

    public static final String CERTIFICATION_BUSINESS_ROLE_MEMBERSHIP_COLUMNS =
        "businessRoleMembershipCertificationItemTableColumns";
    
    public static final String CERTIFICATION_BUSINESS_ROLE_COMPOSITION_TABLE_COLUMNS =
        "certificationBusinessRoleCompositionTableColumns";
    
    public static final String CERTIFICATION_SCHEDULE_TABLE_COLUMNS = 
        "certificationScheduleTableColumns";
    public static final String CERTIFICATION_EVENTS_TABLE_COLUMNS = 
        "certificationEventsTableColumns";
    public static final String CERTIFICATIONS_TABLE_COLUMNS =
        "certificationsTableColumns";

    public static final String CERTIFICATION_REVOCATION_DETAIL_TABLE_COLUMNS =
            "uiCertificationRevocationDetailsColumns";
    
    public static final String TASK_DEFINITION_TABLE_COLUMNS = 
        "taskDefinitionTableColumns";
    public static final String TASK_SCHEDULE_TABLE_COLUMNS = 
        "taskScheduleTableColumns";
    public static final String TASK_RESULTS_TABLE_COLUMNS = 
        "taskResultsTableColumns";
    public static final String MINING_RESULTS_TABLE_COLUMNS = 
        "miningResultsTableColumns";
    
    public static final String GROUP_TABLE_COLUMNS = 
        "groupTableColumns";
    public static final String SUB_GROUP_DEF_TABLE_COLUMNS =
        "subGroupDefinitionTableColumns";
    public static final String POPULATION_TABLE_COLUMNS = 
        "populationTableColumns";
    public static final String POPULATION_EDIT_TABLE_COLUMNS = 
        "populationEditTableColumns";
    public static final String ACCOUNT_GROUP_TABLE_COLUMNS = 
        "accountGroupTableColumns";

    public static final String ACCOUNT_GROUP_ACCESS_TABLE_COLUMNS = "accountGroupAccessTableColumns";

    public static final String NAME_ONLY_ACCOUNT_GROUP_TABLE_COLUMNS =
        "nameOnlyAccountGroupTableColumns";
    
    public static final String ENTITLEMENT_CATALOG_TABLE_COLUMNS =
        "entitlementCatalogTableColumns";
    
    public static final String ACCOUNT_GROUP_IDENTITY_TABLE_COLUMNS = 
        "accountGroupIdentityTableColumns";
    
    public static final String ACCOUNT_GROUP_PERMISSION_TABLE_COLUMNS = 
        "accountGroupPermissionTableColumns";

    public static final String POLICY_VIOLATION_COLUMNS =
        "policyViolationTableColumns";

    public static final String MANUAL_CORRELATION_IDENTITY_COLUMNS =
        "manualCorrelationIdentityTableColumns";

    public static final String APPLICATION_TABLE_COLUMNS =
        "applicationTableColumns";
    
    public static final String APPLICATION_ACCOUNTS_COLUMNS =
        "applicationAccountsTableColumns";

    public static final String MODULE_TABLE_COLUMNS = "moduleTableColumns";

    public static final String BATCHREQUEST_TABLE_COLUMNS = "batchRequestTableColumns";
    
    public static final String RISK_SCORE_TABLE_COLUMNS =
        "riskScoreTableColumns";

    public static final String WORKGROUP_TABLE_COLUMNS =
        "workgroupTableColumns";

    public static final String WORKGROUP_MEMBER_COLUMNS = 
        "workgroupMemberColumns";

    public static final String IDENTITY_EXCLUSIONS_COLUMNS =
        "identityExclusions";

    public static final String IDENTITY_INDIRECT_ENTITLEMENT_COLUMNS =
            "identityEntitlementIndirectGridColumns";

    public static final String ACCOUNT_GRP_MEMBERSHIP_EXCLUSIONS_COLUMNS =
        "accountGroupMembershipExclusions";

    public static final String ACCOUNT_GRP_PERMISSIONS_EXCLUSIONS_COLUMNS =
        "accountGroupPermissionsExclusions";

    public static final String DATA_OWNER_EXCLUSIONS_COLUMNS =
        "dataOwnerExclusions";
    
    public static final String ROLE_EXCLUSIONS_COLUMNS = 
        "roleExclusions";
    
    public static final String ROLE_METRICS_IDENTITIES_COLUMNS = 
        "roleMetricsIdentitiesColumns";

    /**
     * The path to an xhtml file to include to render custom information at the
     * top of the certification entity page.
     */
    public static final String CUSTOM_CERTIFICATION_ENTITY_PAGE_INCLUDE =
        "customCertificationEntityPageInclude";


    /**
     * The path to an xhtml file to include to render custom rows for the
     * certification entity page (for delegated items).
     */
    public static final String CUSTOM_CERTIFICATION_ITEM_ROWS_INCLUDE =
        "customCertificationItemRowsInclude";

    /**
     * The path to an xhtml file to include to render custom rows for the
     * certification entity page (for delegated items).
     */
    public static final String BULK_CUSTOM_ENTITY_FIELDS_INCLUDE =
        "bulkCustomEntityFieldsInclude";
    
    /**
     * List of AccountIconConfig objects that describe the icons  
     * that should be displayed for extended attributes.
     */
    public static final String ACCOUNT_ICON_CONFIG = "accountIconConfig";
    
    /** @deprecated Use Form.Type instead */
    public static final String LCM_CREATE_IDENTITY_POLICY = "lcmCreateIdentityProvisioningPolicy";
    public static final String LCM_CREATE_IDENTITY_POLICY_REQUIRED_FIELDS = "lcmCreateIdentityProvisioningPolicyRequiredFields";
    
    /** @deprecated Use Form.Type instead */
    public static final String LCM_UPDATE_IDENTITY_POLICY = "lcmUpdateIdentityProvisioningPolicy";

    // Certification Detail Page Violations Grid
    public static final String CERT_DETAIL_VIOLATIONS = "certificationDetailViolationTableColumns";

    // Certification Detail Roles grid - manager certifications
    public static final String CERT_DETAIL_MGR_ROLES = "certificationDetailManagerRoleTableColumns";

    // Certification Detail Page entitlements grid
    public static final String CERT_DETAIL_ENTITLEMENTS = "certificationDetailEntitlementsColumns";

    // special column config for application granularity certifications
    public static final String CERT_DETAIL_ENTITLEMENTS_APP_GRANULARITY =
            "certificationDetailAppGranularityEntitlementsColumns";

    public static final String CERT_DETAIL_ACCT_GRP_MEMBERSHIPS =
            "certificationDetailAccountGroupMembershipsColumns";

    public static final String CERT_DETAIL_ACCT_GRP_PERMISSIONS =
            "certificationDetailAccountGroupPermissionsColumns";

    public static final String CERT_DETAIL_ACCT_GRP_PERMISSIONS_APP_GRANULATIRY =
            "certificationDetailAccountGroupPermissionsAppGranularityColumns";

    public static final String CERT_DETAIL_DATA_OWNER = "certificationDetailDataOwnerColumns";

    public static final String CERT_DETAIL_RELATED_ROLES = "certificationDetailRelatedRolesColumns";

    public static final String CERT_DETAIL_PROFILES = "certificationDetailProfilesColumns";

    public static final String CERT_DETAIL_SCOPE_AND_CAPS = "certificationDetailScopesAndCapabilitiesColumns";
    
    public static final String REQUESTS_TABLE_COLUMNS = "requestsTableColumns";

    public static final String UI_IDENTITY_REQUEST_CARD_COLUMNS = "uiIdentityRequestCardColumns";

    public static final String UI_IDENTITY_REQUEST_ITEMS_COLUMNS = "uiIdentityRequestItemsColumns";

    public static final String UI_IDENTITY_REQUEST_CHANGE_ITEMS_COLUMNS = "uiIdentityRequestChangeItemsColumns";

    // LCM Request Violations Grid
    public static final String WORKITEM_VIOLATIONS = "workItemViolationTableColumns";
    
    public static final String LCM_SEARCH_IDENTITY_ATTRIBUTES =
        "lcmSearchIdentityAttributes";
    public static final String LCM_SEARCH_ROLE_ATTRIBUTES =
        "lcmSearchRoleAttributes";
    public static final String LCM_SEARCH_ENTITLEMENT_ATTRIBUTES =
            "lcmSearchEntitlementAttributes";

    public static final String DISABLE_ROLE_EDITOR_IMPACT_ANALYSIS =
        "disableRoleEditorImpactAnalysis"; 
    
    public static final String POLICIES_TABLE_COLUMNS = "policiesTableColumns";

    public static final String BATCH_REQUEST_ITEMS_TABLE_COLUMNS = "batchRequestItemsTableColumns";
    
    public static final String BUSINESS_ROLE_REMEDIATION_ITEM_TABLE_COLUMNS = "businessRoleRemediationItemTableColumns";
    
    public static final String UI_APPROVALS_LIST_COLUMNS = "uiApprovalListColumns";
    public static final String UI_BATCH_REQUEST_ITEMS_TABLE_COLUMNS = "uiBatchRequestItemsTableColumns";

    public static final String FORMS_COLUMNS = "formsColumns";

    // Responsive Work Item List Column Configs
    public static final String UI_WORK_ITEM_LIST_CARD_COLUMNS= "uiWorkItemListCardColumns";
    
    // Responsive Certification Column Configs
    public static final String UI_CERTIFICATION_ITEM_WORKSHEET_COLUMNS = "uiCertificationItemWorksheetColumns";
    public static final String UI_CERTIFICATION_ITEM_RETURNED_ITEMS_COLUMNS = "uiCertificationItemReturnedItemsColumns";
    public static final String UI_CERTIFICATION_ITEM_POLICY_VIOLATION_COLUMNS = "uiCertificationItemPolicyViolationsColumns";
    public static final String UI_CERTIFICATION_ITEM_DETAIL_VIEW_WORKSHEET_COLUMNS = "uiCertificationItemDetailViewColumns";
    public static final String UI_CERTIFICATION_ITEM_DETAIL_VIEW_RETURNED_ITEMS_COLUMNS = "uiCertificationItemDetailViewReturnedItemsColumns";
    public static final String UI_CERTIFICATION_ITEM_DETAIL_VIEW_POLICY_VIOLATION_COLUMNS = "uiCertificationItemDetailViewPolicyViolationsColumns";
    public static final String UI_CERTIFICATION_ITEM_EXPORT_COLUMNS = "uiCertificationExportColumns"; 
    public static final String UI_CERTIFICATION_ENTITIES_COLUMNS = "uiCertificationEntitiesColumns";
    public static final String UI_CERTIFICATION_CARD_COLUMNS = "uiCertificationCardColumns";
    public static final String UI_ENTITLEMENT_CERTIFICATION_ENTITIES_COLUMNS = "uiEntitlementOwnerCertificationEntitiesColumns";
    public static final String UI_POLICY_VIOLATIONS_COLUMNS = "uiPolicyViolationsColumns";
    public static final String PROVISIONING_TRANSACTION_COLUMNS = "provisioningTransactionColumns";
    
    public static final String ALERT_COLUMNS = "alertColumns";

    public static final String ALERT_DEF_COLUMNS = "alertDefinitionColumns";

    public static final String SERVER_STATISTIC_COLUMNS = "serverStatisticColumns";
    public static final String SERVER_COLUMNS = "environmentMonitoringHostsColumns";
    public static final String MON_STAT_COLUMNS = "monitoringStatisticColumns";

    public static final String UI_MANAGE_ACCOUNTS_ATTRIBUTE = "uiManageAccountsIdentityCard";
    public static final String UI_MANAGE_PASSWORDS_ATTRIBUTE = "uiManagePasswordsIdentityCard";
    public static final String UI_MANAGE_VIEW_IDENTITY = "uiManageViewIdentityAttributesCard";
    public static final String UI_MANAGE_EDIT_IDENTITY = "uiManageEditIdentityCard";

    public static final String CONTEXTUAL_HELP = "contextualHelp";

    public static final String UI_CLASSIFICATIONS_COLUMNS = "uiClassificationsColumns";


    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Normally the UI code will reference this cache to avoid hitting
     * the db every time.  
     */
    private static CacheReference _cache;

    Attributes<String,Object> _attributes = new Attributes<String,Object>();

    //////////////////////////////////////////////////////////////////////
    //
    // Constructors
    //
    //////////////////////////////////////////////////////////////////////

    public UIConfig() {
    }

    @XMLProperty(mode=SerializationMode.INLINE)
    public Attributes getAttributes() {
        return _attributes;
    }

    public void setAttributes(Attributes a) {
        if (a == null)
            _attributes = new Attributes<String,Object>();
        else
            _attributes = a;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Cachealbe Interface
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * @exclude
     * Return true if we want to cache this object. 
     * There should only be one UIConfig object, but we may have
     * some variants in the unit tests so check the name.
     */
    public boolean isCacheable(SailPointObject config) {

        return OBJ_NAME.equals(config.getName());
    }

    /**
     * @exclude
     */
    public void setCache(CacheReference cache) {
        _cache = cache;
    }

    /**
     * Return the cached UIConfig.
     */
    public static UIConfig getUIConfig() {
       UIConfig config = null;
       if (_cache != null)
           config = (UIConfig)_cache.getObject();

       if (config == null) {
           // must be in the bootstrapping window
           config = new UIConfig();
       }

       return config;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Pseudo Properties
    //
    //////////////////////////////////////////////////////////////////////
    
    public Object getObject(String name) {
        return _attributes.get(name);
    }

    public String get(String name) {
        return _attributes.getString(name);
    }

    public List<String> getList(String name) {
        return Util.csvToList(get(name));
    }

    public void put(String name, Object value) {
        _attributes.put(name, value);
    }

    public void putList(String name, List<String> values) {
        put(name, Util.listToCsv(values));
    }
    

    // Certification Item List columns.
    @SuppressWarnings("unchecked")
    public List<ColumnConfig> getCertificationItemTableColumns() {
        return _attributes.getList(CERTIFICATION_ITEM_TABLE_COLUMNS);
    }

    public void setCertificationItemTableColumns(List<ColumnConfig> columns) {
        put(CERTIFICATION_ITEM_TABLE_COLUMNS, columns);
    }

    // Certification Item List columns.
    @SuppressWarnings("unchecked")
    public List<ColumnConfig> getCertificationItemDataOwnerTableColumns() {
        return _attributes.getList(CERTIFICATION_ITEM_DATA_OWNER_TABLE_COLUMNS);
    }

    public void setCertificationItemDataOwnerTableColumns(List<ColumnConfig> columns) {
        put(CERTIFICATION_ITEM_DATA_OWNER_TABLE_COLUMNS, columns);
    }

    //  Remediation List columns.
    @SuppressWarnings("unchecked")
    public List<ColumnConfig> getRemediationTableColumns() {
        return _attributes.getList(REMEDIATION_TABLE_COLUMNS);
    }

    public void setRemediationTableColumns(List<ColumnConfig> columns) {
        put(REMEDIATION_TABLE_COLUMNS, columns);
    }
    
    // Certification Entity List columns.
    @SuppressWarnings("unchecked")
    public List<ColumnConfig> getCertificationEntityTableColumns() {
        return _attributes.getList(CERTIFICATION_ENTITY_TABLE_COLUMNS);
    }

    public void setCertificationEntityTableColumns(List<ColumnConfig> columns) {
        put(CERTIFICATION_ENTITY_TABLE_COLUMNS, columns);
    }

    @SuppressWarnings("unchecked")
    public List<ColumnConfig> getCertificationEntityDataOwnerTableColumns() {
        return _attributes.getList(CERTIFICATION_ENTITY_DATA_OWNER_TABLE_COLUMNS);
    }

    public void setCertificationEntityDataOwnerTableColumns(List<ColumnConfig> columns) {
        put(CERTIFICATION_ENTITY_DATA_OWNER_TABLE_COLUMNS, columns);
    }

    //  Certification Business Role List columns.
    @SuppressWarnings("unchecked")
    public List<ColumnConfig> getCertificationBusinessRoleCompositionTableColumns() {
        return _attributes.getList(CERTIFICATION_BUSINESS_ROLE_COMPOSITION_TABLE_COLUMNS);
    }

    public void setCertificationBusinessRoleCompositionTableColumns(List<ColumnConfig> columns) {
        put(CERTIFICATION_BUSINESS_ROLE_COMPOSITION_TABLE_COLUMNS, columns);
    }
    
    //  Certification Business Role List columns.
    @SuppressWarnings("unchecked")
    public List<ColumnConfig> getCertificationBusinessRoleMembershipTableColumns() {
        return _attributes.getList(CERTIFICATION_BUSINESS_ROLE_MEMBERSHIP_COLUMNS);
    }

    public void setCertificationBusinessRoleMembershipTableColumns(List<ColumnConfig> columns) {
        put(CERTIFICATION_BUSINESS_ROLE_MEMBERSHIP_COLUMNS, columns);
    }

    //	Certification Account Group List columns.
    @SuppressWarnings("unchecked")
    public List<ColumnConfig> getCertificationAccountGroupTableColumns() {
        return _attributes.getList(CERTIFICATION_ACCOUNT_GROUP_TABLE_COLUMNS);
    }

    public void setCertificationAccountGroupTableColumns(List<ColumnConfig> columns) {
        put(CERTIFICATION_ACCOUNT_GROUP_TABLE_COLUMNS, columns);
    }
    
    // My Certifications List columns.
    @SuppressWarnings("unchecked")
    public List<ColumnConfig> getMyCertificationsTableColumns() {
        return _attributes.getList(MY_CERTIFICATIONS_TABLE_COLUMNS);
    }

    public void setMyCertificationsTableColumns(List<ColumnConfig> columns) {
        put(MY_CERTIFICATIONS_TABLE_COLUMNS, columns);
    }
    
    // Dashboard Certification Completion Status columns.
    @SuppressWarnings("unchecked")
    public List<ColumnConfig> getDashboardCertificationStatusTableColumns() {
        return _attributes.getList(DASHBOARD_CERTIFICATION_COMPLETION_TABLE_COLUMNS);
    }
    
    public void setDashboardCertificationStatusTableColumns(List<ColumnConfig> columns) {
        put(DASHBOARD_CERTIFICATION_COMPLETION_TABLE_COLUMNS, columns);
    }
    
    //  Dashboard Inbox List columns.
    @SuppressWarnings("unchecked")
    public List<ColumnConfig> getDashboardInboxTableColumns() {
        return _attributes.getList(DASHBOARD_INBOX_TABLE_COLUMNS);
    }

    public void setDashboardInboxTableColumns(List<ColumnConfig> columns) {
        put(DASHBOARD_INBOX_TABLE_COLUMNS, columns);
    }
    
    //  Dashboard Outbox List columns.
    @SuppressWarnings("unchecked")
    public List<ColumnConfig> getDashboardOutboxTableColumns() {
        return _attributes.getList(DASHBOARD_OUTBOX_TABLE_COLUMNS);
    }

    public void setDashboardOutboxTableColumns(List<ColumnConfig> columns) {
        put(DASHBOARD_OUTBOX_TABLE_COLUMNS, columns);
    }
    
    // Dashboard Signoffs List Columns.
    @SuppressWarnings("unchecked")
    public List<ColumnConfig> getDashboardSignoffTableColumns() {
        return _attributes.getList(DASHBOARD_SIGNOFF_TABLE_COLUMNS);
    }

    public void setDashboardSignoffTableColumns(List<ColumnConfig> columns) {
        put(DASHBOARD_SIGNOFF_TABLE_COLUMNS, columns);
    }
    
    //  Dashboard Outbox List columns.
    @SuppressWarnings("unchecked")
    public List<ColumnConfig> getDashboardApplicatonStatisticsTableColumns() {
        return _attributes.getList(DASHBOARD_APPLICATION_STATISTICS_TABLE_COLUMNS);
    }

    public void setDashboardApplicatonStatisticsTableColumns(List<ColumnConfig> columns) {
        put(DASHBOARD_APPLICATION_STATISTICS_TABLE_COLUMNS, columns);
    }
    
    //  Manage WorkItem List columns.
    @SuppressWarnings("unchecked")
    public List<ColumnConfig> getManageWorkItemsTableColumns() {
        return _attributes.getList(MANAGE_WORKITEMS_TABLE_COLUMNS);
    }

    public void setManageWorkItemTableColumns(List<ColumnConfig> columns) {
        put(MANAGE_WORKITEMS_TABLE_COLUMNS, columns);
    }
    
    //  Manage WorkItem List columns.
    @SuppressWarnings("unchecked")
    public List<ColumnConfig> getWorkItemsArchiveTableColumns() {
        return _attributes.getList(WORKITEMS_ARCHIVE_TABLE_COLUMNS);
    }

    public void setWorkItemsArchiveTableColumns(List<ColumnConfig> columns) {
        put(WORKITEMS_ARCHIVE_TABLE_COLUMNS, columns);
    }
    
    //  Define Identity List columns.
    @SuppressWarnings("unchecked")
    public List<ColumnConfig> getIdentityTableColumns() {
        return _attributes.getList(IDENTITY_TABLE_COLUMNS);
    }

    public void setIdentityTableColumns(List<ColumnConfig> columns) {
        put(IDENTITY_TABLE_COLUMNS, columns);
    }
    
    @SuppressWarnings("unchecked")
    public List<ColumnConfig> getIdentityHistoryTableColumns() {
        return _attributes.getList(IDENTITY_HISTORY_TABLE_COLUMNS);
    }

    public void setIdentityHistoryTableColumns(List<ColumnConfig> columns) {
        put(IDENTITY_HISTORY_TABLE_COLUMNS, columns);
    }
    
    //  Report Definition List columns.
    @SuppressWarnings("unchecked")
    public List<ColumnConfig> getTaskDefinitionTableColumns() {
        return _attributes.getList(TASK_DEFINITION_TABLE_COLUMNS);
    }

    public void setTaskDefinitionTableColumns(List<ColumnConfig> columns) {
        put(TASK_DEFINITION_TABLE_COLUMNS, columns);
    }
    
    //  Report Schedule List columns.
    @SuppressWarnings("unchecked")
    public List<ColumnConfig> getTaskScheduleTableColumns() {
        return _attributes.getList(TASK_SCHEDULE_TABLE_COLUMNS);
    }

    public void setCertificationScheduleTableColumns(List<ColumnConfig> columns) {
        put(CERTIFICATION_SCHEDULE_TABLE_COLUMNS, columns);
    }

    //  Certification Events List columns.
    @SuppressWarnings("unchecked")
    public List<ColumnConfig> getCertificationEventsTableColumns() {
        return _attributes.getList(CERTIFICATION_EVENTS_TABLE_COLUMNS);
    }

    public void setCertificationEventsTableColumns(List<ColumnConfig> columns) {
        put(CERTIFICATION_EVENTS_TABLE_COLUMNS, columns);
    }

    //  Certification Schedule List columns.
    @SuppressWarnings("unchecked")
    public List<ColumnConfig> getCertificationScheduleTableColumns() {
        return _attributes.getList(CERTIFICATION_SCHEDULE_TABLE_COLUMNS);
    }

    public void setTaskScheduleTableColumns(List<ColumnConfig> columns) {
        put(CERTIFICATION_SCHEDULE_TABLE_COLUMNS, columns);
    }
    
    //  Report Results List columns.
    @SuppressWarnings("unchecked")
    public List<ColumnConfig> getTaskResultsTableColumns() {
        return _attributes.getList(TASK_RESULTS_TABLE_COLUMNS);
    }
    
    @SuppressWarnings("unchecked")
    public List<ColumnConfig> getMiningResultsTableColumns() {
        return _attributes.getList(MINING_RESULTS_TABLE_COLUMNS);
    }

    public void setTaskResultsTableColumns(List<ColumnConfig> columns) {
        put(TASK_RESULTS_TABLE_COLUMNS, columns);
    }
   
    //  Manual Correlation identity columns.
    @SuppressWarnings("unchecked")
    public List<ColumnConfig> getManualCorrelationIdentityTableColumns() {
        return _attributes.getList(MANUAL_CORRELATION_IDENTITY_COLUMNS);
    }

    public void setManualCorrelationIdentityTableColumns(List<ColumnConfig> columns) {
        put(MANUAL_CORRELATION_IDENTITY_COLUMNS, columns);
    }
    
    //  Application table columns.
    @SuppressWarnings("unchecked")
    public List<ColumnConfig> getApplicationTableColumns() {
        return _attributes.getList(APPLICATION_TABLE_COLUMNS);
    }

    public List<ColumnConfig> getBatchRequestTableColumns() {
         return _attributes.getList(BATCHREQUEST_TABLE_COLUMNS);
    }
    
    public void setApplicationTableColumns(List<ColumnConfig> columns) {
        put(APPLICATION_TABLE_COLUMNS, columns);
    }
    
    //  Group table columns.
    @SuppressWarnings("unchecked")
    public List<ColumnConfig> getGroupTableColumns() {
        return _attributes.getList(GROUP_TABLE_COLUMNS);
    }

    public void setGroupTableColumns(List<ColumnConfig> columns) {
        put(GROUP_TABLE_COLUMNS, columns);
    }
    
    @SuppressWarnings("unchecked")
    public List<ColumnConfig> getSubGroupDefinitionTableColumns() {
        return _attributes.getList(SUB_GROUP_DEF_TABLE_COLUMNS);
    }

    public void setSubGroupDefinitionTableColumns(List<ColumnConfig> columns) {
        put(SUB_GROUP_DEF_TABLE_COLUMNS, columns);
    }

    
    //  Population table columns.
    @SuppressWarnings("unchecked")
    public List<ColumnConfig> getPopulationTableColumns() {
        return _attributes.getList(POPULATION_TABLE_COLUMNS);
    }

    public void setPopulationTableColumns(List<ColumnConfig> columns) {
        put(POPULATION_TABLE_COLUMNS, columns);
    }
    
    //  Edit Population table columns.
    @SuppressWarnings("unchecked")
    public List<ColumnConfig> getPopulationEditTableColumns() {
        return _attributes.getList(POPULATION_EDIT_TABLE_COLUMNS);
    }

    public void setPopulationEditTableColumns(List<ColumnConfig> columns) {
        put(POPULATION_EDIT_TABLE_COLUMNS, columns);
    }
    
    //  Account Group table columns.
    @SuppressWarnings("unchecked")
    public List<ColumnConfig> getAccountGroupTableColumns() {
        return _attributes.getList(ACCOUNT_GROUP_TABLE_COLUMNS);
    }

    public void setAccountGroupTableColumns(List<ColumnConfig> columns) {
        put(ACCOUNT_GROUP_TABLE_COLUMNS, columns);
    }
    
    @SuppressWarnings("unchecked")
    public List<ColumnConfig> getNameOnlyAccountGroupTableColumns() {
        return _attributes.getList(NAME_ONLY_ACCOUNT_GROUP_TABLE_COLUMNS);
    }

    public void setNameOnlyAccountGroupTableColumns(List<ColumnConfig> columns) {
        put(NAME_ONLY_ACCOUNT_GROUP_TABLE_COLUMNS, columns);
    }    
    
    @SuppressWarnings("unchecked")
    public List<ColumnConfig> getEntitlementCatalogTableColumns() {
        List<ColumnConfig> debug =_attributes.getList(ENTITLEMENT_CATALOG_TABLE_COLUMNS); 
        return debug;
    }

    public void setEntitlementCatalogTableColumns(List<ColumnConfig> columns) {
        put(ENTITLEMENT_CATALOG_TABLE_COLUMNS, columns);
    }
    
    @SuppressWarnings("unchecked")
    public List<ColumnConfig> getAccountGroupPermissionTableColumns() {
        return _attributes.getList(ACCOUNT_GROUP_PERMISSION_TABLE_COLUMNS);
    }

    public void setAccountGroupPermissionTableColumns(List<ColumnConfig> columns) {
        put(ACCOUNT_GROUP_PERMISSION_TABLE_COLUMNS, columns);
    }
    
//  Account Group Identity table columns.
    @SuppressWarnings("unchecked")
    public List<ColumnConfig> getAccountGroupIdentityTableColumns() {
        return _attributes.getList(ACCOUNT_GROUP_IDENTITY_TABLE_COLUMNS);
    }

    public void setAccountGroupIdentityTableColumns(List<ColumnConfig> columns) {
        put(ACCOUNT_GROUP_IDENTITY_TABLE_COLUMNS, columns);
    }

    //  Policy Violation table columns.
    @SuppressWarnings("unchecked")
    public List<ColumnConfig> getPolicyViolationTableColumns() {
        return _attributes.getList(POLICY_VIOLATION_COLUMNS);
    }

    public void setPolicyViolationTableColumns(List<ColumnConfig> columns) {
        put(POLICY_VIOLATION_COLUMNS, columns);
    }

    //  Define risk score table columns.
    @SuppressWarnings("unchecked")
    public List<ColumnConfig> getRiskScoreTableColumns() {
        return _attributes.getList(RISK_SCORE_TABLE_COLUMNS);
    }

    public void setRiskScoreTableColumns(List<ColumnConfig> columns) {
        put(RISK_SCORE_TABLE_COLUMNS, columns);
    }

    //  Workgroup table columns.
    @SuppressWarnings("unchecked")
    public List<ColumnConfig> getWorkgroupTableColumns() {
        return _attributes.getList(WORKGROUP_TABLE_COLUMNS);
    }

    public void setWorkgroupTableColumns(List<ColumnConfig> columns) {
        put(WORKGROUP_TABLE_COLUMNS, columns);
    }

    //  Workgroup member table columns.
    @SuppressWarnings("unchecked")
    public List<ColumnConfig> getWorkgroupMemberTableColumns() {
        return _attributes.getList(WORKGROUP_MEMBER_COLUMNS);
    }

    public void setWorkgroupMemberTableColumns(List<ColumnConfig> columns) {
        put(WORKGROUP_MEMBER_COLUMNS, columns);
    }
    
    //  Workgroup member table columns.
    @SuppressWarnings("unchecked")
    public List<ColumnConfig> getRoleMetricsIdentitiesColumns() {
        return _attributes.getList(ROLE_METRICS_IDENTITIES_COLUMNS);
    }

    public void setRoleMetricsIdentitiesColumns(List<ColumnConfig> columns) {
        put(ROLE_METRICS_IDENTITIES_COLUMNS, columns);
    }
    
    //  Requests table columns.
    @SuppressWarnings("unchecked")
    public List<ColumnConfig> getRequestsTableColumns() {
        return _attributes.getList(REQUESTS_TABLE_COLUMNS);
    }

    public void setRequestsTableColumns(List<ColumnConfig> columns) {
        put(REQUESTS_TABLE_COLUMNS, columns);
    }

    //  Policies table columns.
    @SuppressWarnings("unchecked")
    public List<ColumnConfig> getPoliciesTableColumns() {
        return _attributes.getList(POLICIES_TABLE_COLUMNS);
    }

    public void setPoliciesTableColumns(List<ColumnConfig> columns) {
        put(POLICIES_TABLE_COLUMNS, columns);
    }

    // Batch request items table columns
    @SuppressWarnings("unchecked")
    public List<ColumnConfig> getBatchRequestItemsTableColumns() {
        return _attributes.getList(BATCH_REQUEST_ITEMS_TABLE_COLUMNS);
    }

    public void setBatchRequestItemsTableColumns(List<ColumnConfig> columns) {
        put(BATCH_REQUEST_ITEMS_TABLE_COLUMNS, columns);
    }
    
    @SuppressWarnings("unchecked")
    public List<ColumnConfig> getBusinessRoleRemediationItemTableColumns() {
        return _attributes.getList(BUSINESS_ROLE_REMEDIATION_ITEM_TABLE_COLUMNS);
    }
    
    public void setBusinessRoleRemediationItemTableColumns(List<ColumnConfig> columns) {
        put(BUSINESS_ROLE_REMEDIATION_ITEM_TABLE_COLUMNS, columns);
    }


    //
    // identitySearchAttributes
    //

    public String getIdentitySearchAttributes() {
        return get(IDENTITY_SEARCH_ATTRIBUTES);
    }

    public void setIdentitySearchAttributes(String names) {
        put(IDENTITY_SEARCH_ATTRIBUTES, names);
    }

    public List<String> getIdentitySearchAttributesList() {
        return getList(IDENTITY_SEARCH_ATTRIBUTES);
    }

    public void setIdentitySearchAttributesList(List<String> l) {
        putList(IDENTITY_SEARCH_ATTRIBUTES, l);
    }

    // identityViewAttributes

    public String getIdentityViewAttributes() {
        return get(IDENTITY_VIEW_ATTRIBUTES);
    }

    public void setIdentityViewAttributes(String names) {
        put(IDENTITY_VIEW_ATTRIBUTES, names);
    }

    public List<String> getIdentityViewAttributesList() {
        return getList(IDENTITY_VIEW_ATTRIBUTES);
    }

    public void setIdentityViewAttributesList(List<String> l) {
        putList(IDENTITY_VIEW_ATTRIBUTES, l);
    }

    // Custom certification rendering.

    public String getCustomCertificationEntityPageInclude() {
        return get(CUSTOM_CERTIFICATION_ENTITY_PAGE_INCLUDE);
    }

    public String getCustomCertificationItemRowsInclude() {
        return get(CUSTOM_CERTIFICATION_ITEM_ROWS_INCLUDE);
    }

    public String getBulkCustomEntityFieldsInclude() {
        return get(BULK_CUSTOM_ENTITY_FIELDS_INCLUDE);
    }

    public List<AccountIconConfig> getAccountIcons() {
        return (List<AccountIconConfig>)_attributes.getList(ACCOUNT_ICON_CONFIG);
    }

    public boolean isRoleEditorImpactAnalysisDisabled() {
        return _attributes.getBoolean(DISABLE_ROLE_EDITOR_IMPACT_ANALYSIS, false);
    }

    @SuppressWarnings("unchecked")
    public Map<String, ContextualHelpItem> getContextualHelp() {
        return (Map<String, ContextualHelpItem>) getObject(CONTEXTUAL_HELP);
    }
    
    //////////////////////////////////////////////////////////////////////
    //
    // SailPointObject methods
    //
    //////////////////////////////////////////////////////////////////////

    public Object clone() throws CloneNotSupportedException {
        return super.clone();
    }


}
