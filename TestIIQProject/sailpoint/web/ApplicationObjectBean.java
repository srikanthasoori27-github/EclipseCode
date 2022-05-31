/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 *
 */
package sailpoint.web;

import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.TreeMap;

import javax.faces.application.FacesMessage;
import javax.faces.component.UIComponent;
import javax.faces.context.FacesContext;
import javax.faces.el.ValueBinding;
import javax.faces.event.ActionEvent;
import javax.faces.model.SelectItem;
import javax.faces.validator.ValidatorException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.Describer;
import sailpoint.api.ExceptionCleaner;
import sailpoint.api.Localizer;
import sailpoint.api.ObjectUtil;
import sailpoint.api.SailPointContext;
import sailpoint.api.passwordConstraints.PasswordConstraintAttributeAccount;
import sailpoint.connector.CollectorServices;
import sailpoint.connector.Connector;
import sailpoint.connector.ConnectorException;
import sailpoint.connector.ConnectorFactory;
import sailpoint.connector.DefaultApplicationFactory;
import sailpoint.connector.JDBCConnector;
import sailpoint.connector.LDAPConnector;
import sailpoint.connector.UnixConnector;
import sailpoint.credential.CredentialRetriever;
import sailpoint.integration.JsonUtil;
import sailpoint.object.ActivityDataSource;
import sailpoint.object.Application;
import sailpoint.object.Application.Feature;
import sailpoint.object.ApplicationScorecard;
import sailpoint.object.AttributeDefinition;
import sailpoint.object.AttributeDefinition.UserInterfaceInputType;
import sailpoint.object.Attributes;
import sailpoint.object.AuditEvent;
import sailpoint.object.Certification;
import sailpoint.object.CompositeDefinition;
import sailpoint.object.CompositeDefinition.Tier;
import sailpoint.object.Configuration;
import sailpoint.object.ConnectorConfig;
import sailpoint.object.ConnectorOperationData;
import sailpoint.object.CorrelationConfig;
import sailpoint.object.Describable;
import sailpoint.object.DomainData;
import sailpoint.object.Filter;
import sailpoint.object.Filter.LeafFilter;
import sailpoint.object.ForestData;
import sailpoint.object.Form;
import sailpoint.object.Identity;
import sailpoint.object.IdentitySelector;
import sailpoint.object.Link;
import sailpoint.object.ObjectAttribute;
import sailpoint.object.ObjectConfig;
import sailpoint.object.PasswordPolicy;
import sailpoint.object.PasswordPolicyHolder;
import sailpoint.object.PropertyInfo;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.QueryOptions;
import sailpoint.object.Rule;
import sailpoint.object.RapidSetupConfigUtils;
import sailpoint.object.Schema;
import sailpoint.object.ScoreConfig;
import sailpoint.object.ScoreDefinition;
import sailpoint.object.Source;
import sailpoint.object.TargetSource;
import sailpoint.server.Auditor;
import sailpoint.service.ScorecardDTO;
import sailpoint.service.listfilter.ListFilterValue;
import sailpoint.tools.GeneralException;
import sailpoint.tools.JsonHelper;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.tools.xml.XMLReferenceResolver;
import sailpoint.web.AccountSearchScopeBean.AccountDataBean;
import sailpoint.web.GroupSearchScopeBean.GroupDataBean;
import sailpoint.web.ManageDomainBean.DomainDataBean;
import sailpoint.web.ManageDomainBean.ExchangeDataBean;
import sailpoint.web.ManageDomainBean.ForestDataBean;
import sailpoint.web.ManageRestBean.ConnectorOperationBean;
import sailpoint.web.application.ActivityDataSourceDTO;
import sailpoint.web.application.AttributeDefinitionEditBean;
import sailpoint.web.application.CorrelationConfigBean;
import sailpoint.web.application.DependencyValidator;
import sailpoint.web.application.JDBCAttributeDTO;
import sailpoint.web.application.PasswordPolicyHolderBean;
import sailpoint.web.application.PasswordPolicyHolderDTO;
import sailpoint.web.application.SchemaDTO;
import sailpoint.web.extjs.DescriptionData;
import sailpoint.web.form.editor.FormDTO;
import sailpoint.web.form.editor.SectionDTO;
import sailpoint.web.identity.IdentityDTO;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.policy.IdentitySelectorDTO;
import sailpoint.web.util.NavigationHistory;
import sailpoint.web.util.SelectItemByLabelComparator;
import sailpoint.web.util.WebUtil;

/**
 * managedBeanName = "applicationObject"
 * scope = request
 */
public class ApplicationObjectBean extends BaseEditBean<Application>  implements NavigationHistory.Page {
    private static Log log = LogFactory.getLog(ApplicationObjectBean.class);

    private final static String SELECTED_DS_ID = "SELECTED_DS_ID";
    public final static String EDITED_ACTIVITY_DS = "EDITED_DS";
    public final static String EDITED_ACTIVITY_DS_MAP = "EDITED_ACTIVITY_DS_MAP";
    public final static String DELETED_ACTIVITY_DS_LIST = "EDITED_ACTIVITY_DS_LIST";
    private final static String SELECTED_TARGET_DS_ID = "SELECTED_TARGET_DS_ID";
    public final static String EDITED_TARGET_DS = "EDITED_TARGET_DS";
    public final static String EDITED_TARGET_DS_MAP = "EDITED_TARGET_DS_MAP";
    public final static String DELETED_TARGET_DS_LIST = "EDITED_TARGET_DS_LIST";

    private final static String ACCOUNT_SCHEMA_NEW = "ACCOUNT_SCHEMA_NEW";
    private final static String GROUP_SCHEMA_NEW = "GROUP_SCHEMA_NEW";
    private final static String ATT_ATTRIBUTE_EDITOR = "ApplicationAttributeEditor";
    public final static String CURRENT_TAB = "APP_CURRENT_TAB";
    public static final String APPLICATION_MESSAGES = "applicationObjectBeanMessages";
    public static final String NEW_TARGET_SOURCE = "NEW_TARGET_SOURCE";
    public static final String SCHEMA_LOADED = "APP_SCHEMA_LOADED";

    public final static String EDITED_PASSWORD_POLICY = "EDITED_PP";

    public final static String CURRENT_SELECTOR = "APP_CURRENT_TIER_SELECTOR";
    private final static List <String> emptySchemaAppType = Arrays
            .asList(new String[] { "SCIM 2.0", "DelimitedFile",
                    "Web Services", "JDBC", "SQLLoader" });

    /**
     * Map of schema DTOs keyed by schema name
     */
    public static final String SCHEMA_DTOS = "schemaDTOs";

    public static final String SCHEMA_ATTRIBUTE_EDITOR = "SchemaAttributeEditor";

    public static final String APPLICATION_TEMPLATE_EDITOR = "ApplicationTemplateEditor";
    /** 
    * Application attribute which filters application type from list of new application
    */
    public final static String DEPRECATED_CONNECTOR = "DeprecatedConnector";
    /**
     * Controlled Availability for Connectors.
     * Another application attribute which filters application type from list of new application.
     */
    private final static String CONTROLLED_AVAILABILITY_CONNECTOR = "CAConnector";

    public static final String ACCOUNT_INDEX_COLUMN_DATABASE = "indexColumns";
    public static final String ACCOUNT_MERGE_COLUMN_DATABASE = "mergeColumns";
    
    public static final String SCHEMA_OBJECT_TYPE_INDEX_COLUMN_CONFIG = "indexColumns_";
    public static final String SCHEMA_OBJECT_TYPE_MERGE_COLUMN_CONFIG = "mergeColumns_";
    
    public static final String SCHEMA_OBJECT_TYPE_INDEX_COLUMN_DATABASE_TEMPLATE = "{0}indexColumns";
    public static final String SCHEMA_OBJECT_TYPE_MERGE_COLUMN_DATABASE_TEMPLATE = "{0}mergeColumns";
    
    // GENERIC- REST- WEBSERVICE
    private static final String WEBSERVICE_APP_TYPE = "Web Services";
    private static final String SCIM2_APP_TYPE = "SCIM 2.0";
    /**
     * Flag to represents the mode of the application reconfiguration task
     */
    public static final String APP_RECONF_MODE = "appReconfMode";
    /**
     * Flag for validation of the completion of the schema mapping
     */
    public static final String IS_SAVED_MAPPING = "isSavedMapping";

    public static final String LIST_OF_APP_RECONFIG_ATTR_OBJECTS = "listOfAppReconfigAttrObject";

    public static final String NEW_APP_ACCOUNT_ATTRIBUTES = "newAppAccountAttributes";

    /**
     * Session Key for the list which represents the list of new application schema object type (previously group) attributes
     * which will be used to generate the drop down list in the schema mapping
     */
    public static final String NEW_APP_SCHEMA_OBJECT_TYPE_ATTRIBUTES = "newAppSchemaObjectTypeAttributes";

    public static final String RECONFIG_APPLICATION = "reconfigApplication";

    /**
     * Flag for validation of the completion of updating provisioning policy
     */
    public static final String IS_PROV_POLICY_UPDATED = "isProvPolicyUpdated";

    /**
     * Flag to check whether old application has particular objectType schema attribute or not.
     * It will be used to check whether to render schema mapping table or not,
     * in the application reconfiguration task 
     */
    public static final String IS_SCHEMA_OBJECT_TYPE_EMPTY = "isSchemaObjectTypeEmpty";

    public static final String MAINT_ENABLED_STATE = "maintenanceEnabled";
    public static final String MAINT_EXPIRATION_STATE = "maintenanceExpiration";

    /**
     * This list contains application types which supports JWT implemention
     */
    private static final List<String> JWT_SUPPORTED_APPS = Arrays
            .asList(new String[] { "Box", "SCIM", "Web Services", "SuccessFactors", "G Suite" , "Azure Active Directory", "Salesforce","SCIM 2.0", "Microsoft SharePoint Online"});
    
    //Ldap multigroup application config attribute
    public static final String CONFIG_LDAPAPPLICATIONVERSION="LDAPApplicationVersion";
    public final String CONFIG_LDAP_APPLICATION_VERSION_VALUE = "2.0";

    //list of application only password policy attributes for auditing any change
    //based on all fields in passwordPolicyConfig.xhtml but not in passwords.xhtml
    public static final String[][] APPLICATION_ONLY_PASSWORD_POLICY_ATTRIBUTE_NAMES = {
        {PasswordConstraintAttributeAccount.CHECK_PASSWORDS_AGAINST_DISPLAY_NAME, 
            MessageKeys.PSWD_CHECK_PASSWORD_DISPLAY_NAME},
        {PasswordConstraintAttributeAccount.MIN_DISPLAY_NAME_UNIQUECHARS, 
            MessageKeys.PSWD_MIN_NUMBER_UNIQUE_CHARS_CHECK_PASSWORD_DISPLAY_NAME},
        {PasswordConstraintAttributeAccount.CHECK_PASSWORDS_AGAINST_ACCOUNTID, 
            MessageKeys.PSWD_CHECK_PASSWORD_ACCOUNT_ID},
        {PasswordConstraintAttributeAccount.MIN_ACCOUNT_ID_UNIQUECHARS, 
            MessageKeys.PSWD_MIN_NUMBER_UNIQUE_CHARS_CHECK_PASSWORD_ACCOUNT_ID},
        {Configuration.CHECK_PASSWORDS_AGAINST_ACCOUNT_ATTRIBUTES, 
            MessageKeys.PSWD_CHECK_PASSWORDS_ACCOUNT_ATTRIBUTES}
    };
    

    private Map<String, String> _appTypes;
    private List<AttributeDefinition> _attrConfig;
    AttributeEditBean _editedAttributes;
    ScorecardDTO _scorecard;

    private boolean _testSuccess;

    /**
     * Result of testConfiguration being called on connector
     */
    private String _testResult;
    
    private static final List<String> AZURE_AS_IDP_APPS = Arrays
            .asList(new String[] {"Azure Active Directory" , "Microsoft SharePoint Online"}); 

    /**
     * Constants for AD UI
     */
    private static final String AD_APP_TYPE = "Active Directory - Direct";
    private static final String LDAP_MULTIGROUP_APPS = "|SunOne - Direct|OpenLDAP - Direct|IBM Tivoli DS - Direct|";
    private static final String AD_ATT_DOMAIN_DN = "domainDN";
    public static final String AD_ATT_DOMAIN_NET_BIOS = "domainNetBiosName";
    
    private static final String ATT_DOMAIN_FOREST_NAME = "forestName";
    private static final String AD_ATT_FOREST_NAME = "forestName";
    private static final String AD_ATT_GC_SERVER = "gcServer";
    
    private static final String AD_ATT_USER = "user";
    private static final String AD_ATT_PASSWORD = "password";

    private static final String ATT_EXCHANGE_FOREST_NAME    = "exchangeForest";
    private static final String ATT_EXCHANGE_HOSTS          = "ExchHost";
    private static final String ATT_EXCHANGE_USER           = "user";
    private static final String ATT_EXCHANGE_PASSWORD       = "password";
    private static final String ATT_EXCHANGE_ACCOUNT_FOREST = "accountForestList";
    private static final String ATT_EXCHANGE_SETTINGS       = "exchangeSettings";
    private static final String ATT_EXCHANGE_USE_TLS        = "useTLS";

    private static final String AD_ATT_PORT = "port";
    private static final String AD_ATT_AUTH_TYPE = "authorizationType";
    private static final String AD_EXCHANGE_SERVERS = "ExchHost";
    private static final String AD_EXCHANGE_VERSION = "exchangeversion";
    private static final String ATT_FOREST_ADMIN = "forestAdmin";
    private static final String ATT_FOREST_ADMIN_PASS = "forestAdminPassword";
    private static final String ATT_FOREST_GC = "forestGC";
    private static final String AD_ATT_SERVERS = "servers";
    private static final String AD_ATT_USE_SSL = "useSSL";
    private static final String ATT_AUTHENTICATION_TYPE = "authenticationType";
    private static final String AD_ATT_DOMAIN_ITERATE_SEARCH_FILTER = "domainIterateSearchFilter";
    private static final String AD_ATT_USE_GROUP_MEMBERSHIP_PRELOADING = "useGroupMembershipPreloading";
    private static final String AD_ATT_MANAGE_ALL_DOMAINS = "manageAllDomains";
    private static final String AD_ATT_SEARCH_DN = "searchDN";
    private static final String ATT_OBJECT_TYPE = "objectType";
    private static final String AD_ATT_GROUP_MEMBERSHIP_SEARCH_DN = "groupMembershipSearchDN";
    private static final String AD_ATT_GROUP_MEMBER_FILTER_STRING = "groupMemberFilterString";
    private static final String AD_ATT_PRIMARY_GROUP_SEARCH_DN = "primaryGroupSearchDN";
    private static final String AD_ATT_SEARCH_SCOPE = "searchScope";
    private static final String AD_ATT_ITERATE_SEARCH_FILTER = "iterateSearchFilter";
    private static final String AD_ATT_SEARCH_DNS = "searchDNs";
    private static final String AD_ATT_ACCOUNT_SEARCH_DNS = "account.searchDNs";
    private static final String AD_ATT_CONTACT_SEARCH_DNS = "contact.searchDNs";
    private static final String AD_ATT_GMSA_SEARCH_DNS = "gmsa.searchDNs";
    private static final String AD_ATT_DOMAIN_SETTINGS = "domainSettings";
    private static final String AD_ATT_FOREST_SETTINGS = "forestSettings";
    private static final String AD_ATT_GROUP_SEARCH_DNS = "group.searchDNs";
    private static final String AD_ATT_AUTH_NONE = "none";
    private static final String AD_ATT_GROUP_SEARCH_SCOPE = "group.searchScope";
    private static final String AD_ATT_GROUP_ENTITLEMENT = "groupEntitlementAttr";
    private static final String AD_ATT_IS_RESOURCE_FOREST = "isResourceForest";
    private static final String AD_ATT_DISABLE_SHADOW_ACCOUNT_MEMBERSHIP   = "disableShadowAccountMembership";
    private static final String AD_ATT_SHADOW_ACCOUNT_MEMBERSHIP_FILTER    = "shadowAccountMembershipFilter";
    private static final String LDAP_MULTI_GRP_IGNORE_ATT = "|searchDN|searchScope|group.searchScope|";
	private static final String ATT_GROUP_MEMBERSHIP_SEARCH_SCOPE = "groupMembershipSearchScope";
    private static final String ATT_GROUP_SEARCH_DN = "group.searchDN";
    private static final String ATT_GROUP_SEARCH_FILTER = "group.iterateSearchFilter";
    private static final String ATT_ACCOUNT_SEARCH_FILTER = "account.iterateSearchFilter"; 
    private static final String ATT_ACCOUNT_SEARCH_DNS = "account.searchDNs"; 
    private final String CONFIG_LDAP_APPLICATION_OLD_GROUP_DELTA_KEY= "change_saved_for_groups";
    private final String CONFIG_LDAP_APPLICATION_NEW_GROUP_DELTA_KEY= "change_saved_for_group";
    private final String CONFIG_LDAP_APPLICATION_DELTA_KEY = "deltaAggregation";
    private static final String CONFIG_MEMBER_ATTRIBUTE_KEY = "memberAttribute";
    private static final String CONFIG_MEMBER_ATTRIBUTE_VALUE = "dn";
    private static final String CONFIG_GROUP_OBJECT_TYPE_VALUE = "group";
    private static final String CONFIG_SEARCH_DNS_SUFFIX = "."+AD_ATT_SEARCH_DNS;
    private static final String ATT_ENABLE_CACHE = "enableCache";
	private static final String ATT_CACHE_RMI_PORT = "cacheRmiPort";
	private static final String ATT_CACHE_REMOTE_OBJECT_PORT = "cacheRemoteObjectPort";
    private static final String ATT_DISABLE_COMPUTEPRELOADING = "disableComputePreloading";
    private static final String ATT_DISABLE_FSPAGGREGATION = "disableFspAggregation";
    private static final String ATT_IQSERVICE_CONFIGURATION = "IQServiceConfiguration";
    private static final String ATT_USE_TLS_FOR_IQSERVICE = "useTLSForIQService";
    private final static String ATT_IQSERVICE_HOST = "IQServiceHost";
    private final static String ATT_IQSERVICE_PORT = "IQServicePort";
    private final static String ATT_IQSERVICE_USER = "IQServiceUser";
    private final static String ATT_IQSERVICE_PASSWORD = "IQServicePassword";
    private final static String EMPTY_STRING = "";
    /**
     * Constants for RACF UI
     */
    private static final String RACF_ATT_AUTHSEARCH_ATTR = "authSearchAttributes";
    private static final String RACF_ATT_PAGE_SIZE = "pageSize";
    
    /**
     * Constants for SUCCESSFACTOR UI
     */
    private static final String SUCCESSFACTOR_APP_TYPE = "SuccessFactors";


    /**
     * Constants for Applications to exclude/ special condition during iq service validation
     */
    private static final String APP_WINDOWS_LOCAL = "Windows Local - Direct";
    private static final String APP_MICROSOFT_FOREFRONT_IDENTITY_MANAGER="Microsoft Forefront Identity Manager";
    
    List<SelectItem> _correlationRules;
    List<SelectItem> _creationRules;
    List<SelectItem> _managerCorrelationRules;
    List<SelectItem> _customizationRules;
    List<SelectItem> _managedAttributeCustomizationRules;
    List<SelectItem> _buildMapRules;
    List<SelectItem> _jdbcBuildMapRules;
    List<SelectItem> _sapBuildMapRules;
    List<SelectItem> _mergeMapRules;
    List<SelectItem> _transformationRules;
    List<SelectItem> _preIterateRules;
    List<SelectItem> _postIterateRules;
    List<SelectItem> _peopleSoftBuildMapRules;

    /**
     * Rule list that contains the BeforeProvisioning type rules
     */
    List<SelectItem> _beforeProvisioningRules;
    
    /**
     * Rule list that contains the AfterProvisioning type rules
     */
    List<SelectItem> _afterProvisioningRules;
    

    List<SelectItem> _remediationModificationTypes;
    List<SelectItem> _smEncryptions;
    
    List<SelectItem> _jdbcProvisionRules;

    List<SelectItem> _jdbcOperationProvisioningRules;
    
    List<SelectItem>  _jdbcEnableProvisioningRules;
    
    List<SelectItem>  _jdbcDisableProvisioningRules;
    
    List<SelectItem>  _jdbcDeleteProvisioningRules;
    
    List<SelectItem>  _jdbcCreateProvisioningRules;
    
    List<SelectItem>  _jdbcUnlockProvisioningRules;
    
    List<SelectItem>  _jdbcModifyProvisioningRules;
    
    List<SelectItem> _saphrProvisionRules;

    List<SelectItem> _saphrOperationProvisioningRules;
    
    List<SelectItem>  _saphrEnableProvisioningRules;
    
    List<SelectItem>  _saphrDisableProvisioningRules;
    
    List<SelectItem>  _saphrDeleteProvisioningRules;
    
    List<SelectItem>  _saphrCreateProvisioningRules;
    
    List<SelectItem>  _saphrUnlockProvisioningRules;
    
    List<SelectItem>  _saphrModifyProvisioningRules;

    
    List<SelectItem> _peoplesofthrmsProvisionRules;

    List<SelectItem> _peoplesofthrmsOperationProvisioningRules;
    
    List<SelectItem>  _peoplesofthrmsEnableProvisioningRules;
    
    List<SelectItem>  _peoplesofthrmsDisableProvisioningRules;
    
    List<SelectItem>  _peoplesofthrmsDeleteProvisioningRules;
    
    List<SelectItem>  _peoplesofthrmsCreateProvisioningRules;
    
    List<SelectItem>  _peoplesofthrmsUnlockProvisioningRules;
    
    List<SelectItem>  _peoplesofthrmsModifyProvisioningRules;
    List<SelectItem> _sapHRCustomManagerRule;
    
    List<SelectItem> _webServiceAfterOperationRules;
    
    List<SelectItem> _webServiceBeforeOperationRules;
    
    List<SelectItem> _successFactorsOperationProvisioning;
    
    private String _selectedDataSourceId;
    
    private String _selectedTargetSourceId;

    private String _currentTab;

    private boolean isAccountSchemaNew;

    private Map<String, Boolean> groupSchemaNew = new HashMap<String, Boolean>();

    private List<SchemaDTO> _schemaDTOs;

    /**
     * Flag represents the checkbox to add the missing attributes
     * from new application to previous application
     */
    private boolean addMissingAttr = false;

    /**
     * Attribute to store the list of rule attribute names which will be copied into the reconfigured application.
     * For defect #20827 we removed "postIterateRule","preIterateRule","buildMapRule","mapToResourceObjectRule","mergeMapsRule" as 
     * those found to be connector specific and can be found in the Delimited File Or JDBC connector's attribute tab.
     * "afterProvisioningRule","beforeProvisioningRule" these rules are iiq specific as those will be there for all connectors 
     * and can be found in the Rule tab of the application 
     */
    private static List<String> ruleAttributeNames = Arrays.asList( "afterProvisioningRule","beforeProvisioningRule" );

    /**
     * Flag represents the checkbox whether to keep the unmapped previous 
     * application attributes or not
     */
    private boolean keepExtraAttr = false;

    /**
     * List of composite account rules selectable on the tier tab.
     */
    private List<SelectItem> _compositeAccountRuleSelectList;
   /**
    * List of composite remedation rules selectable on the tier tab.
    */
    private List<SelectItem> _compositeRemediationRuleSelectList;

    /**
     * List of new application account attributes list for the drop down
     */
    private List<SelectItem> _newApplicationAccountSelectList;

    /**
     * A map of a list select items for schema object type
     */
    private Map<String, List<SelectItem>> _newApplicationSchemaObjectTypeSelectListMap;

    /**
     * List of ApplicationReconfigMapping objects to store the schema mapping
     */
    List<ApplicationReconfigMapping> _appReconfigAttributeList;

    /**
    * List of composite tier correlation rules selectable on the tier tab.
    */
    private List<SelectItem> _compositeTierCorrelationRuleSelectList;

    /**
     * Source application of a newly created composite tier attribute.
     */
    private String _sourceApplication;

    /**
     * Source attribute of a newly created composite tier attribute.
     */
    private String _sourceAttribute;

    /**
     * Embed the bean here so the target stuff can be confined to
     * a separate bean and then brought together at the end.
     */
    private TargetSourceBean _targetSourceBean;

    /**
     * Flag that will indicate to the page if we should re-render the schema tab,
     *  because with large schemas it's expensive to draw the entire table.
     */
    boolean _schemaLoaded;

    /**
     * Bean that holds the information needed to correlate accounts
     * from this application to identity objects.
     */
    private CorrelationConfigBean _accountCorrelationBean;

    /**
     * A LeafFilter that will help us find an account's manager.
     */
    private LeafFilter _managerCorrelationFilter;

    /**
     * A List of ListFilterValue that will help us find if the account is service account or not
     */
    private List<ListFilterValue> _serviceAccountFilter;

    /**
     * A List of ListFilterValue that will help us find if the account is RPA account or not
     */
    private List<ListFilterValue> _rpaAccountFilter;

    /**
     * A List of ListFilterValue that will help us find if the account is disabled or not
     */
    private List<ListFilterValue> _disableAccountFilter;

    /**
     * A List of ListFilterValue that will help us find if the account is locked or not
     */
    private List<ListFilterValue> _lockAccountFilter;

    /**
     * A DescriptionData object representing the contents of the description widget on the UI
     */
    private DescriptionData _descriptionData;

    private boolean _isAppInitialized = false;

    /**
     * Form Bean for managing the forms associated with the application
     */
    private FormEditBean _formEditBean;

    /**
     * Edit bean to manage editing Schema Attribute Definitions
     */
    private AttributeDefinitionEditBean _attrDefEditBean;

    /**
     * Keep the current Tier around so we can tell when the apps have changed
     * and save off any changes that were made to the current application.
     */
    String _currentTierApp;

    /**
     * The "Logical" tier selector that's being edited.
     */
    IdentitySelectorDTO _currentSelector;

    private List<PasswordPolicyHolderBean> _passwordPolicyList;

    private List<SelectItem> _passwordPolicySelectList;

    private Map<String, List<String>> _policyDetailsMap;
    
    private String identityToView;

    /**
     * Attribute represent the user choice for previous or new 
     * application's provisioning policies used for the reconfigured app
     */
    private String _templateChoice;

    /**
     * Flag that is set when the "save button" on the
     * logical pages has been pushed. We look at this
     * flag to see if we want to run a "partial" save
     * of the application.
     */
    boolean _logicalConfigDirty;
    
    private Localizer localizer;

    /**
     * List which represents the list of new application account attributes
     * which will be used to generate the drop down list in the schema mapping
     */
    private List<String> newAppAccountAttributes =  new ArrayList<String>();

    /**
     * The object type when creating/deleting/modifying a group schema.
     * New in 6.4 we need to support multiple group schemas. So this can be
     * something like 'role' or 'group'.
     */
    private String schemaObjectType;

    private boolean maintenanceEnabled;

    private Date maintenanceExpiration;

    /**
     * Default App fetched from Connector Registry
     */
    private Application _defaultApp;
    private boolean isLdapMultigrp = false;

    /*
     * In case we navigated from one app to another
     * without 1st clearing the session vars.
     */
    private boolean _appLoadDirty;

    /**
     * Used to get '|' group object types from schema
     */
    private String groupObjectTypes;

    public String getGroupObjectTypes() throws GeneralException {
        if (Util.isNullOrEmpty(groupObjectTypes)) {
            getGroupObjectTypesAsString();
        }
        return  groupObjectTypes;
    }

    public void setGroupObjectTypes(String groupObjectTypes) {
        this.groupObjectTypes = groupObjectTypes;
    }

    /**
     *
     */
    public ApplicationObjectBean() {
        
        super();
       
        restore();
        
    } // ApplicationObjectBean()

    @Override
    public boolean isStoredOnSession() {
        return true;
    }
    
    @Override
    protected Class<Application> getScope() {
        return Application.class;
    }

    public boolean getAddMissingAttr(){
        return addMissingAttr;
    }
    
    public void setAddMissingAttr(boolean val){
        this.addMissingAttr = val;
    }
    
    public boolean getKeepExtraAttr(){
        return keepExtraAttr;
    }

    public void setKeepExtraAttr(boolean val){
        this.keepExtraAttr = val;
    }

    public boolean isSchemaMappingSaved() {
        return Util.otob((Boolean) getEditState(IS_SAVED_MAPPING));
    }

    public void setSchemaMappingSaved(boolean isSavedMapping) {
        addEditState(IS_SAVED_MAPPING, isSavedMapping);
    }

    public List<ApplicationReconfigMapping> getAppReconfigAttributeList() {
        _appReconfigAttributeList = (List<ApplicationReconfigMapping>) getEditState(LIST_OF_APP_RECONFIG_ATTR_OBJECTS);
        return _appReconfigAttributeList;
    }

    public void setAppReconfigAttributeList( List<ApplicationReconfigMapping> _appReconfigAttributeList) {
        this._appReconfigAttributeList = _appReconfigAttributeList;
        addEditState(LIST_OF_APP_RECONFIG_ATTR_OBJECTS, this._appReconfigAttributeList);
    }

    public String getTemplateChoice() {
        return _templateChoice;
    }

    public void setTemplateChoice(String _templateChoice) {
        this._templateChoice = _templateChoice;
    }

    public boolean isAppReconfMode() {
        return Util.otob( (Boolean) getEditState(APP_RECONF_MODE) );
    }

    public void setAppReconfMode( boolean appReconfMode ) {
        addEditState(APP_RECONF_MODE, appReconfMode);
    }

    public List<String> getNewAppAccountAttributes() {
        newAppAccountAttributes = (List<String>) getEditState(NEW_APP_ACCOUNT_ATTRIBUTES);
        return newAppAccountAttributes;
    }

    public void setNewAppAccountAttributes(List<String> newAppAccountAttributes) {
        this.newAppAccountAttributes = newAppAccountAttributes;
        addEditState(NEW_APP_ACCOUNT_ATTRIBUTES, newAppAccountAttributes);
    }

    /**
     * @deprecated use {@link #setNewAppSchemaObjectTypeAttributes(String, java.util.List)}
     *
     */
    public void setNewAppGroupAttributes(List<String> newAppGroupAttributes) {
        setNewAppSchemaObjectTypeAttributes(Connector.TYPE_GROUP, newAppGroupAttributes);
    }

    /**
     * @deprecated  use {@link #getNewAppSchemaObjectTypeAttributes()}
     *
     */
    public List<String> getNewAppGroupAttributes() {
        return getNewAppSchemaObjectTypeAttributes().get(Connector.TYPE_GROUP);
    }

    private void setNewAppSchemaObjectTypeAttributes(String type, List<String> attributes) {
        Map<String, List<String>> map = getNewAppSchemaObjectTypeAttributes();
        map.put(type, attributes);
    }

    public Map<String, List<String>> getNewAppSchemaObjectTypeAttributes() {
        Map<String, List<String>> map = (Map<String, List<String>>) getEditState(NEW_APP_SCHEMA_OBJECT_TYPE_ATTRIBUTES);
        if (map == null) {
            map = new HashMap<String, List<String>>();
            addEditState(NEW_APP_SCHEMA_OBJECT_TYPE_ATTRIBUTES, map);
        }
        return map;
    }

    /**
     * @deprecated {@link #isSchemaObjectTypeEmpty(String)}
     */
    public Boolean getGroupSchemaEmpty() {
        return isSchemaObjectTypeEmpty(Connector.TYPE_GROUP);
    }

    /**
     * @deprecated {@link #setSchemaObjectTypeEmpty(String, Boolean)}
     */
    public void setGroupSchemaEmpty(Boolean empty) {
        setSchemaObjectTypeEmpty(Connector.TYPE_GROUP, empty);
    }

    private void setSchemaObjectTypeEmpty(String schemaObjectType, Boolean empty) {
        Map<String, Boolean> emptyMap = getSchemaObjectTypeEmpty();
        emptyMap.put(schemaObjectType, empty);
    }

    /**
     * Returns a map of whether an objectType is empty. This can be called by the jsf components
     */
    public Map<String, Boolean> getSchemaObjectTypeEmpty() {
        Map<String, Boolean> map = (Map<String, Boolean>) getEditState(IS_SCHEMA_OBJECT_TYPE_EMPTY);
        if (map == null) {
            map = new HashMap<String, Boolean>();
            addEditState(IS_SCHEMA_OBJECT_TYPE_EMPTY, map);
        }
        return map;
    }

    private boolean isSchemaObjectTypeEmpty(String schemaObjectType) {
        boolean empty = true;
        Map<String, Boolean> emptyMap = (Map<String, Boolean>) getEditState(IS_SCHEMA_OBJECT_TYPE_EMPTY);
        if (emptyMap != null) {
            if (emptyMap.get(schemaObjectType) != null) {
                empty = Util.otob(emptyMap.get(schemaObjectType));
            }
        }
        return empty;
    }

    /**
     * List of keys ruleAttribute that if present in the old application need to be added in 
     * the new Reconfigured application. 
     */
    private List<String> getRuleAttributeNames(){
        return ruleAttributeNames;
    }

    /**
     * @return
     */
    @Override
    public Application createObject() {
        return new Application();
    } // createObject()

    public void attachObject(Application object) {
        // bernie - Ugly Hibernate hack: if the schema is new, Hibernate
        // may have attached an ID to it, but it won't have been valid
        // because we never committed.  This will cause problems when
        // we actually want to save, so we have to null it out.
        if (object != null) {
            List<Schema> schemas = object.getSchemas();
            if (schemas != null) {
                for (Schema schema : schemas) {
                    if (Connector.TYPE_ACCOUNT.equals(schema.getObjectType())) {
                        if (isAccountSchemaNew) {
                            schema.setId(null);
                        }
                    } else {
                        if (isSchemaObjectTypeNew(schema.getObjectType())) {
                            schema.setId(null);
                        }
                    }
                }
            }
        }
    }

    /**
     * @return
     */
    /* (non-Javadoc)
     * @see sailpoint.web.BaseObjectBean#getObject()
     */
    @Override
    public Application getObject() throws GeneralException {
        Application app = super.getObject();

        // initialize the correlation key assignment flags
        if (app != null && !_isAppInitialized) {
            List<Schema> schemas = app.getSchemas();
            if (schemas != null) {
                for (Schema schema : schemas) {
                    if ( schema == null ) continue;
                    schema.initCorrelationKeys();
                }
            } // if schemas != null

            //
            // Correlation configuration ( non-rule based )
            //
            CorrelationConfig accountConfig = app.getAccountCorrelationConfig();
            if ( accountConfig == null ) {
                accountConfig = new CorrelationConfig();
            }
            _accountCorrelationBean = new CorrelationConfigBean(accountConfig);

            _isAppInitialized = true;
        } // if app != null

        //Set the application object for Domain and Scope classes
        if((app != null) && (app.getType()!= null)) {
        	 isLdapMultigrp = app.getAttributeValue(CONFIG_LDAPAPPLICATIONVERSION)!=null?true:false;
            if(app.getType().equalsIgnoreCase(AD_APP_TYPE) || (LDAP_MULTIGROUP_APPS.toLowerCase().contains("|"+app.getType().toLowerCase()+"|")) ) {
                ManageDomainBean.setApplicationObject(app);
                AccountSearchScopeBean.setApplicationObject(app);
                GroupSearchScopeBean.setApplicationObject(app);
                if(AD_APP_TYPE.equalsIgnoreCase(app.getType())){
                    ContactSearchScopeBean.setApplicationObject(app);
                    GMSASearchScopeBean.setApplicationObject(app);
                }
            }
            if (app.getType().equalsIgnoreCase(SUCCESSFACTOR_APP_TYPE)) {
                ManageSFSchemaBean.setApplicationObject(app);
            }
            resetAndReloadInternalBeansOnDirtyAppLoad(app);
        }
        // Setting the Rest Web Service application
        if((app != null) && (app.getType()!= null)) {
            if(app.getType().equalsIgnoreCase(WEBSERVICE_APP_TYPE)) {
                ManageRestBean.setApplicationObject(app);
            }
        }
        return app;
    } // getObject()
    
    /**
     * Utility method to allow us to reset and reinitialize any beans
     * that may not be getting properly re-init'd when a user views
     * a new application without hitting save nor cancel on a previous app.
     * @param app
     */
    private void resetAndReloadInternalBeansOnDirtyAppLoad(Application app) {
        if (_accountCorrelationBean != null && _appLoadDirty) {
            
            // prevent preservation of a navigated-away-from config editor
            _accountCorrelationBean.setDirty(false);
            _appLoadDirty = false;
            _accountCorrelationBean.reset();
            _accountCorrelationBean = null;
            CorrelationConfig accountConfig = app.getAccountCorrelationConfig();
            if ( accountConfig == null ) {
                accountConfig = new CorrelationConfig();
            }
            _accountCorrelationBean = new CorrelationConfigBean(accountConfig);
        }
    }

    /**
     * Return a simplified representation of the application scorecard.
     */
    public ScorecardDTO getScorecard() throws GeneralException {

        if (_scorecard == null) {
            Application app = getObject();
            
            if (app != null) {
                ApplicationScorecard card = app.getScorecard();
                if ( card != null ) {
                    SailPointContext con = getContext();
                    ScoreConfig config = con.getObjectByName(ScoreConfig.class,
                                                   ScoreConfig.OBJ_NAME);
                    if (config != null) {
                        List<ScoreDefinition> scores = config.getApplicationScores();
                        if ( scores != null ) {
                            _scorecard = new ScorecardDTO(card, scores, getLocale(), getUserTimeZone());
                        }
                    }
                }
            }
        }

        return _scorecard;
    }
    
    /**
     * Override this method so that it uses the multi language option
     * 
     */
    @Override
    public Locale getLocale(){
        boolean useLocalizedDescriptions = Util.otob(Configuration.getSystemConfig().get("enableLocalizeApplicationDescriptions"));
        Locale locale = null;
        if (useLocalizedDescriptions) {
            FacesContext o = FacesContext.getCurrentInstance();
            if (o != null && o.getViewRoot() != null) {
                locale = o.getViewRoot().getLocale(); 
                 // check if locale is supported, if not fallback to default locale
                if (!localizer.isLocaleSupported(locale.toString())) {
                    locale = localizer.getDefaultLocale();
                }
            }
        }
        else {
            locale = localizer.getDefaultLocale();
        }
        return locale;
    }

    /**
     * For now, we are only supporting one remediator per application.
     * This may change.  That's why the remediators are backed by a list.
     * @return The remediator for this application
     * @throws GeneralException if the Application can't be fetched
     */
    public Identity getRemediator() throws GeneralException {
        final Application app = getObject();
        final Identity remediator;

        if (app != null) {
            List<Identity> remediators = app.getRemediators();
            if (remediators != null && !remediators.isEmpty()) {
                remediator = getObject().getRemediators().get(0);
            } else {
                remediator = null;
            }
        } else {
            remediator = null;
        }

        return remediator;
    }

    /**
     * Set the remediator for this application.  We are only supporting one for now,
     * but this may change, which is why the remediators are backed by a list.
     * @param remediator The new Identity that acts as the remediator for this application
     * @throws GeneralException if the Application can't be fetched
     */
    public void setRemediator(Identity remediator) throws GeneralException {
        List<Identity> remediators = getObject().getRemediators();
        if (remediators == null) {
            remediators = new ArrayList<Identity>();
        }
        
        try {
            if (remediators.isEmpty() && remediator != null) {
                remediators.add(remediator);
            } else if (!remediators.isEmpty()){
                remediators.set(0, remediator);
            }
        } catch (UnsupportedOperationException e) {
            remediators = Arrays.asList(new Identity[] {remediator});
        }

        getObject().setRemediators(remediators);
    }
    /**
     * First gets all the application types then add an extra string (Select One ...) in the application type
     * convert the application list into json string and return it.
     * @return
     */
    public String getApplicationTypesJsonString() {
        Map<String, String> type = null;
        List<Map<String, String>> returnList = new ArrayList<Map<String, String>>();
        Set<String> appTypeSet = getApplicationTypes().keySet();
        Iterator<String> iter = appTypeSet.iterator();
        while (iter.hasNext()) {
            type = new HashMap<String, String>();
            type.put("type", iter.next());
            returnList.add(type);
        }
        Message msg = new Message(MessageKeys.APP_RECONF_SELECT_ONE);
        type = new HashMap<String, String>();
        type.put("type", msg.toString());
        returnList.add(0,type);

        return JsonHelper.toJson(returnList);
    }

    /**
     *
     *  Use the connector registry to fill in the available applications
     *  list by application type.  First use the older 'installedConnectors'
     *  list, but also fill in any Application templates defined in the
     *  registry.
     *
     * @return a Map<String,String> used to populate the select
     */
    public Map<String, String> getApplicationTypes() {
        if (_appTypes == null) {
            _appTypes = new TreeMap<String, String>();

            Configuration connectorRegistry = null;
            try {
                connectorRegistry = getContext().getObjectByName(Configuration.class, Configuration.CONNECTOR_REGISTRY);
            } catch (GeneralException ex) {
                Message msg = new Message(Message.Type.Error,
                        MessageKeys.ERR_CANT_FIND_CONN_REGISTRY);
                log.error(msg.getMessage(), ex);
                addMessage(msg, null);
            }

            if (connectorRegistry != null) {
                List<ConnectorConfig> configs = (List<ConnectorConfig>)connectorRegistry.getList(Configuration.INSTALLED_CONNECTORS);
                if (configs != null) {
                    for (ConnectorConfig config : configs) {
                        _appTypes.put(config.getDisplayName(), config.getDisplayName());
                    }
                }
                List<Application> apps = (List<Application>)connectorRegistry.getList(Configuration.APPLICATION_TEMPLATES);
                if (apps != null) {
                    for (Application app : apps) {
                        Object isDeprecatedConnector = app.getAttributeValue(DEPRECATED_CONNECTOR);
                        Object isCAConnector = app.getAttributeValue(CONTROLLED_AVAILABILITY_CONNECTOR);
                        if ((null == isDeprecatedConnector || isDeprecatedConnector.toString().equalsIgnoreCase("false"))
                                && (null == isCAConnector || isCAConnector.toString().equalsIgnoreCase("false"))) {
                            _appTypes.put(app.getType(), app.getType());
                        }
                    }
                }
            }
        } // if _appTypes == null

        return _appTypes;
    } // getApplicationTypes()

    /**
     * wrapper around Application.getType()
     *
     * @return the application type
     */
    public String getType() {
        String type = null;

        Application app = null;
        try {
            app = getObject();
        } catch (GeneralException ex) {
            // ignore
        }
        if (app != null) {
            type = app.getType();
        }
        return type;
    }

    public void setType(String type)throws GeneralException {
        if ( type != null ) {
            Application app = getObject();
            if ( app != null )
                app.setType(type);
        }
    }

    /**
     * This method will initialise newAppAccountAttributes and newAppGroupAttributes
     * which will be used to generate the List<SelectItem> for the drop down list in
     * the schema mapping page 
     */
    public void createListOfAttributes(Application newApp){
        List<Schema> schemas = newApp.getSchemas();
        if (schemas != null) {
            for (Schema schema : schemas){
                if (schema.getObjectType().equalsIgnoreCase(Application.SCHEMA_ACCOUNT)){
                    newAppAccountAttributes = schema.getAttributeNames();
                } else {
                    setNewAppSchemaObjectTypeAttributes(schema.getObjectType(), schema.getAttributeNames());
                }
            }
            setNewAppAccountAttributes(newAppAccountAttributes);
        }
    }

    /**
     * Initialize the List<ApplicationReconfigMapping> object to populate schema mapping page.
     * Default mapping will be shown for those previous application attributes whose name
     * matches with the new application attribute name
     */
    public void initApplicationReconfigObjectList(){
        _appReconfigAttributeList = new ArrayList<ApplicationReconfigMapping>();
        initAppReconfAttributeList ( getSchemaDTO(Connector.TYPE_ACCOUNT) );

        for (SchemaDTO dto : _schemaDTOs) {
            if (!Connector.TYPE_ACCOUNT.equals(dto.getObjectType())) {
                if (getSchemaDTO(dto.getObjectType()) != null) {
                    initAppReconfAttributeList(dto);
                    setSchemaObjectTypeEmpty(dto.getObjectType(), false);
                } else {
                    setSchemaObjectTypeEmpty(dto.getObjectType(), true);
                }
            }
        }

        setAppReconfigAttributeList(_appReconfigAttributeList);
    }

    /**
     * Method to initialize application reconfiguration objects
     */
    void initAppReconfAttributeList ( SchemaDTO schemaDTO ) {
        if ( schemaDTO != null ) {
            for ( int i = 0; i < schemaDTO.getAttributes().size(); i++ ) {
                ApplicationReconfigMapping reconfigAppObj = new ApplicationReconfigMapping();
                reconfigAppObj.setOldApplicationAttribute( schemaDTO.getAttributes().get(i).getName() );
                reconfigAppObj.setNewApplicationAttribute( defaultMappedAttribute( schemaDTO.getObjectType(),
                                                                                   schemaDTO.getAttributes().get(i).getName() ) );
                reconfigAppObj.setType( schemaDTO.getObjectType() );
                _appReconfigAttributeList.add( reconfigAppObj );
            }
        }
    }

    /**
     * Default mapping of the attributes. The previous application 
     * attributes will be mapped to the new application automatically 
     * at the time of rendering if their name matches 
     */
    private String defaultMappedAttribute(String type, String oldAttrName){
        String name = "";
        List<String> attr = Connector.TYPE_ACCOUNT.equals(type) ? newAppAccountAttributes :
                            ( getNewAppSchemaObjectTypeAttributes().get(type) );
        if ( attr != null ) {
            if ( attr.contains( oldAttrName) ) {
                name = oldAttrName;
            }
        }
        return name;
    }

    /**
     * Method which will get called from the xhtml when Save Mapping 
     * button is clicked
     */
    public void reconfigSchema() throws GeneralException{
       if ( validateMappingAction() ) {
           modifySchema(getObject(), getReconfigurableApp(), keepExtraAttr, addMissingAttr);
       }
    }

    /**
     * Method will check that no more than one attribute from the 
     * previous application is mapped to the same attribute of the new application
     */
    private boolean validateMappingAction() {

        List<ApplicationReconfigMapping> appReconfigObjs = 
            (List<ApplicationReconfigMapping>) getEditState(LIST_OF_APP_RECONFIG_ATTR_OBJECTS);
        HashSet<String> mappedAccountAttr = new HashSet<String>();
        Map<String, HashSet<String>> mappedSchemaObjectTypeAttrMap = new HashMap<String, HashSet<String>>();
        boolean isAccountMapped = false;
        boolean error = false;
        if (appReconfigObjs != null){
            for (ApplicationReconfigMapping appReconfigObj : appReconfigObjs){
                if ( appReconfigObj.getType().equals( Connector.TYPE_ACCOUNT ) ){
                    if ( Util.isNotNullOrEmpty(appReconfigObj.getNewApplicationAttribute()) 
                            && (!mappedAccountAttr.add(appReconfigObj.getNewApplicationAttribute())) ) {
                        error = true;
                        break;
                    } else if ( Util.isNotNullOrEmpty( appReconfigObj.getNewApplicationAttribute()) ) {
                        isAccountMapped = true;
                    }
                }  else {
                    if ( Util.isNotNullOrEmpty( appReconfigObj.getNewApplicationAttribute())) {
                        HashSet<String> attributes = mappedSchemaObjectTypeAttrMap.get(appReconfigObj.getType());
                        if (attributes == null) {
                            attributes = new HashSet<String>();
                            mappedSchemaObjectTypeAttrMap.put(appReconfigObj.getType(), attributes);
                        }
                        if (!attributes.add(appReconfigObj.getNewApplicationAttribute())) {
                            error = true;
                            break;
                        }
                    }
                }
            }
        }

        if ( error ) {
            addMessage(new Message(Message.Type.Error, MessageKeys.APP_RECONF_ERR_SAVE_MAPPING_MSG), null);
            return false;
        } else if (!emptySchemaAppType.contains(_object.getType()) && !isAccountMapped) {
            addMessage(new Message(Message.Type.Error, MessageKeys.APP_RECONF_ERR_NO_MAPPING), null);
            return false;
        } else {
            return true;
        }
    }

    /**
     * The method updates the old application with the new application attribute definitions.
     * If the attribute is mapped then name of AttributeDefinition of the new application
     * becomes internalName and name becomes old application attribute name.
     * addNew : will add the unmapped attributes from new application to reconfigured application
     * keepExtra : will add the unmapped attributes from old application to reconfigured application
     * It does not add group schema of the new application if old application haven't it,
     * regardless value of keepExtra 
     */
    private void modifySchema(Application oldApp,
            Application newApp,
            boolean keepExtra,
            boolean addNew) throws GeneralException{

        List<String> validAccountAttrDefinition = new ArrayList<String>();
        List<String> validSchemaObjTypeAttributes = new ArrayList<String>();
        
        Map<String, List<String>> validSchemaObjectTypeAttrDefinitionMap = new HashMap<String, List<String>>();
        /*
         *  appReconfigObjs contains all previous application attributes,
         *  those which are mapped and those which are not mapped. Unmapped
         *  attributes has empty value for the newApplicationAttribute attribute
         */
        List<ApplicationReconfigMapping> appReconfigObjs = 
            (List<ApplicationReconfigMapping>) getEditState(LIST_OF_APP_RECONFIG_ATTR_OBJECTS);
        if (appReconfigObjs != null){
            for ( ApplicationReconfigMapping appReconfigObj : appReconfigObjs ){
                String internalName = appReconfigObj.getNewApplicationAttribute();
                // If internalName is not empty then set the name of the attribute definition
                // of the new application schema as the internalName and set the name as the
                // oldApplicationAttribute value in the appReconfigObj object
                if ( Util.isNotNullOrEmpty( internalName ) ) {
                    // Check for the existence of the internal name in the mapped object 
                    if ( appReconfigObj.getType().equals(Connector.TYPE_ACCOUNT) ) {
                        modifyAttributeDefinition( appReconfigObj, newApp.getAccountSchema(), validAccountAttrDefinition );
                    } else if (newApp.getSchema(appReconfigObj.getType()) != null ) {
                        modifyAttributeDefinition( appReconfigObj, newApp.getSchema(appReconfigObj.getType()), validSchemaObjTypeAttributes );
                        validSchemaObjectTypeAttrDefinitionMap.put(appReconfigObj.getType(), validSchemaObjTypeAttributes);
                    }
                } else if ( keepExtra ) {
                    // If keepExtra is true then copy all those attribute definition from old 
                    // application schema which are not mapped to the reconfigured application schema
                    if ( appReconfigObj.getType().equals(Connector.TYPE_ACCOUNT) ) {
                        addAttributeDefinition( appReconfigObj, 
                                oldApp.getAccountSchema(), 
                                newApp.getAccountSchema(), 
                                validAccountAttrDefinition);
                    } else if ( oldApp.getSchema(appReconfigObj.getType())  != null ) {
                        List<String> validSchemaObjectTypeAttributes = new ArrayList<String>();
                        validSchemaObjectTypeAttrDefinitionMap.put(appReconfigObj.getType(), validSchemaObjectTypeAttributes);
                        addAttributeDefinition( appReconfigObj,
                                oldApp.getSchema(appReconfigObj.getType()),
                                newApp.getSchema(appReconfigObj.getType()),
                                validSchemaObjectTypeAttributes);
                    }
                }
            }
        }
        // newAppAccountSchema contains mapped as well as unmapped attribute definitions
        // remove unmapped attributes if addNew flag is not selected
        if ( !addNew ) {
            ListIterator<AttributeDefinition> accAttrDefIterator = newApp.getAccountSchema().getAttributes().listIterator();
            while ( accAttrDefIterator.hasNext() ) {
                if ( !validAccountAttrDefinition.contains( accAttrDefIterator.next().getName() ) ) {
                    accAttrDefIterator.remove();
                }
            }
            for (SchemaDTO dto : _schemaDTOs) {
                if (!Connector.TYPE_ACCOUNT.equals(dto.getObjectType())) {
                    Schema newAppSchema = newApp.getSchema(dto.getObjectType());
                    if (newAppSchema != null) {
                        ListIterator<AttributeDefinition> iterator = newAppSchema.getAttributes().listIterator();
                        while ( iterator.hasNext() ) {
                            List<String> attributeDefinitions = validSchemaObjectTypeAttrDefinitionMap.get(dto.getObjectType());
                            if (!Util.nullSafeContains(attributeDefinitions, iterator.next().getName()) ) {
                                iterator.remove();
                            }
                        }
                    }
                }
            }
        }
        // Set the new attribute definitions and nativeObjectType to the schemaDTO
        // This will remain in the schemaDTO and will get saved to iiq database
        // only after the application gets saved
        modifySchemaDTO( newApp.getAccountSchema(), getSchemaDTO( Connector.TYPE_ACCOUNT ) );
        List<SchemaDTO> tempDto = new ArrayList<SchemaDTO>();
        for (SchemaDTO dto : _schemaDTOs) {
            if (!Connector.TYPE_ACCOUNT.equals(dto.getObjectType())) {
                if ( newApp.getSchema(dto.getObjectType()) != null && getSchemaDTO( dto.getObjectType() ) != null ) {
                    if ( newApp.getSchema(dto.getObjectType()).getAttributes().size() > 0 ) {
                        modifySchemaDTO( newApp.getSchema(dto.getObjectType()), getSchemaDTO( dto.getObjectType() ) );
                    } else {
                        // remove group schema from the old app if no group
                        // attribute is mapped and keepExtra, addNew are false
                        tempDto.add(getSchemaDTO( dto.getObjectType()));
                        oldApp.getSchemas().remove(oldApp.getSchemas().indexOf(oldApp.getSchema(dto.getObjectType())));
                        setSchemaObjectTypeEmpty(dto.getObjectType(), true);
                    }
                }
            }
        }
        if (tempDto != null && tempDto.size() > 0) {
            getSchemaDTOs().remove(tempDto);
	}
        oldApp.setType(getReconfigurableApp().getType());
        setSchemaMappingSaved( true ); // user done with the mapping
    }

    /**
     * Initialise valid list of attribute definitions which has internalName
     * and modify the attribute definition by changing internalName and name
     */
    private void modifyAttributeDefinition( ApplicationReconfigMapping appReconfigObj,
                                            Schema newAppSchema,
                                            List<String> validADList ) {
        if ( Util.isNotNullOrEmpty(appReconfigObj.getNewApplicationAttribute()) ) {
            AttributeDefinition newAppAD = newAppSchema.getAttributeDefinition( appReconfigObj.getNewApplicationAttribute() );
            newAppAD.setInternalName( appReconfigObj.getNewApplicationAttribute() );
            newAppAD.setName( appReconfigObj.getOldApplicationAttribute() );
            validADList.add( newAppAD.getName() );
        }
    }

    /**
     * Add attribute definitions names in the valid AttributreDefinition list and
     * add the attribute definition in the schema
     */
    private void addAttributeDefinition( ApplicationReconfigMapping appReconfigObj,
                                         Schema oldAppSchema,
                                         Schema newAppSchema,
                                         List<String> validADList ) {
        AttributeDefinition oldAttrDef = oldAppSchema.
        getAttributeDefinition( appReconfigObj.getOldApplicationAttribute());
        if (newAppSchema != null) {
            newAppSchema.addAttributeDefinition( oldAttrDef );
	}
        validADList.add( oldAttrDef.getName() );
    }

    /**
     * Modify schemaDTO for attributes and nativeObjectType 
     */
    private void modifySchemaDTO(Schema newAppSchema, SchemaDTO schemaDTO ) {
        if ( schemaDTO != null ) {
            schemaDTO.setAttributes( convertAttrDefToList( newAppSchema.getAttributes(), schemaDTO.getObjectType() ) );
            schemaDTO.setNativeObjectType(newAppSchema.getNativeObjectType());
        }
    }
    
    private List< SchemaAttributeDTO > convertAttrDefToList( List<AttributeDefinition> listOfAttrDef, String schemaObjectType ) {
        List< SchemaAttributeDTO > newListOfAttributes = new ArrayList< SchemaAttributeDTO >();
        for ( AttributeDefinition attrDef : listOfAttrDef ) {
            newListOfAttributes.add( new SchemaAttributeDTO( attrDef, schemaObjectType ) );
        }
        return newListOfAttributes;
    }

    public void setSchemaDTOs(List<SchemaDTO> schemaDTOs) {
        _schemaDTOs = schemaDTOs;
        addEditState(SCHEMA_DTOS, _schemaDTOs);
    }

    public List<SchemaDTO> getSchemaDTOs() {
        return _schemaDTOs;
    }
    
    /**
     * Fetch a list of schema object Types that are defined in the connector registry but not yet defined on the application
     * @return List of strings representing schema object types defined in connector registry but not defined on the app.
     * @throws GeneralException
     */
    public List<String> getUndefinedSchemas() throws GeneralException {

        //JDBC/Delimited File schemas are not driven from connector registry. Let the attributes form drive the UI
        if (getType() == null || getType().equals(JDBCConnector.CONNECTOR_TYPE) || getType().equals("DelimitedFile")) {
            return Collections.emptyList();
        }

        List<String> undefinedObjectTypes = new ArrayList<String>();

        _defaultApp = getDefaultApplication();
        if (_defaultApp != null) {
            //List of defined schema Obeject Types
            List<String> definedObjectTypes = new ArrayList<String>();
            for(SchemaDTO defined : Util.safeIterable(getSchemaDTOs())) {
                definedObjectTypes.add(defined.getObjectType());
            }

            for (Schema s : Util.safeIterable(_defaultApp.getSchemas())) {
                if (s.getObjectType() != null && !definedObjectTypes.contains(s.getObjectType())) {
                    undefinedObjectTypes.add(s.getObjectType());
                }
            }

        }
        return undefinedObjectTypes;
    }

    /**
     *
     * @return JSON representatino of the Undefined Schemas
     * @throws GeneralException
     */
    public String getUndefinedScheamsJSON() throws GeneralException {
        return JsonHelper.toJson(getUndefinedSchemas());
    }

    /**
     * This will always return false for DelimitedFile and JDBC connectors, because the schemas for these are
     * not driven from connectorRegistry.
     * @return true if the connector registry template contains schemas that are not defined on the application
     */
    public boolean isHasUndefinedSchemas() {
        try {
            return !Util.isEmpty(getUndefinedSchemas());
        } catch (GeneralException ex) {
            return false;
        }
    }

    /**
     * Iterate this DTO list during attribute rendering for JDBC applications. JDBCAttributeDTO is
     * especially helpful in rendering the schema object type specific index and merge columns.
     * @return a list of JDBCAttributeDTOs
     */
    public List<JDBCAttributeDTO> getJDBCAttributeDTOs() {
        List<JDBCAttributeDTO> jdbcDTOs = new ArrayList<JDBCAttributeDTO>();
        
        try {
            if (_schemaDTOs == null) {
                _schemaDTOs = buildSchemaDTOs();
            }
            
            for (SchemaDTO dto : Util.safeIterable(_schemaDTOs)) {
                String indexCol = getIndexColumnsForSchemaObjectTypeJSON(dto.getObjectType());
                String mergeCol = getMergeColumnsForSchemaObjectTypeJSON(dto.getObjectType());
                
                jdbcDTOs.add(new JDBCAttributeDTO(dto, indexCol, mergeCol));
            }
        } catch (GeneralException e) {
            log.error("Failed to create JDBC index and merge column configuration.", e);
        }
        
        return jdbcDTOs;
    }

    public String getCurrentTab() {
        return _currentTab;
    }

    public void setCurrentTab(String tab) {
        _currentTab = tab;
        addEditState(CURRENT_TAB, tab);
    }

    public boolean getSchemaLoaded() {
        return _schemaLoaded;
    }

    public void setSchemaLoaded(boolean val) {
        _schemaLoaded = val;
        addEditState(SCHEMA_LOADED, new Boolean(val));
    }


    /**
     * Return the name of the currently selected correlation rule.
     */
    public String getCorrelationRule() throws GeneralException {
        Application app = getObject();
        Rule rule = app.getCorrelationRule();
        return (rule != null) ? rule.getName() : null;
    }


    /**
     * Set the correlation rule by name.
     */
    public void setCorrelationRule(String name) throws GeneralException {

        Application app = getObject();
        if (name == null || name.length() == 0) {
            // this is the "Select a Rule" placeholder, null the reference
            app.setCorrelationRule(null);
        }
        else {
            Rule rule = getContext().getObjectByName(Rule.class, name);
            if (rule != null)
                app.setCorrelationRule(rule);
            else {
                // somehow got an invalid selector, null or leave
                // the current value?
                app.setCorrelationRule(null);
            }
        }
    }

    /**
     * Return the names of all correlation rules, suitable for use
     * in a selectOneMenu.
     */
    public List<SelectItem> getCorrelationRules() throws GeneralException {

        if (_correlationRules == null) {
            Rule selected = (null != getObject()) ? getObject().getCorrelationRule() : null;
            _correlationRules = WebUtil.getRulesByType(getContext(),
                                                                           Rule.Type.Correlation,
                                                                           true, false,
                                                                           ((null != selected) ? selected.getName() : null));

        }
        return _correlationRules;
    }


    public List<SelectItem> getBuildMapRules() throws GeneralException {
        if ( _buildMapRules == null) {
            _buildMapRules = getConfigBasedRules(Rule.Type.BuildMap, Connector.CONFIG_BUILD_MAP_RULE);
        }
        return _buildMapRules;
    }

    public List<SelectItem> getJDBCBuildMapRules() throws GeneralException {
        if ( _jdbcBuildMapRules == null) {
            _jdbcBuildMapRules = getConfigBasedRules(Rule.Type.JDBCBuildMap, Connector.CONFIG_BUILD_MAP_RULE);
            
        }
        return _jdbcBuildMapRules;
    }
    
    public List<SelectItem> getSAPBuildMapRules() throws GeneralException {
        if ( _sapBuildMapRules == null) {
            _sapBuildMapRules = getConfigBasedRules(Rule.Type.SAPBuildMap, Connector.CONFIG_BUILD_MAP_RULE);
        }
        return _sapBuildMapRules;
    }
/**
 * Introduced as a getter for SAPHR Provisioning rule UI. bug24761-US4453
 * Called from ui form, for generic provisioining rule
 * @return
 * @throws GeneralException
 */
    public List<SelectItem> getSAPHRProvisionRules() throws GeneralException {
        if ( _saphrProvisionRules == null) {
            _saphrProvisionRules = getConfigBasedRules(Rule.Type.SapHrProvision, Connector.CONFIG_SAPHR_PROVISION_RULE);
            
        }
        return _saphrProvisionRules;
    }

    /**
     * Introduced as a getter for SAPHR Provisioning rule UI. bug24761-US4453
     * called from UI form, for by operation enable provisioning rule
     * @return
     * @throws GeneralException
     */

    public List<SelectItem> getSAPHREnableProvisioningRules() throws GeneralException {
        if (_saphrEnableProvisioningRules == null) {
            _saphrEnableProvisioningRules = getConfigBasedRules(Rule.Type.SapHrOperationProvisioning, Connector.CONFIG_SAPHR_ENABLE_PROVISION_RULE);

        }
        return _saphrEnableProvisioningRules;
    }

    /**
     * Introduced as a getter for SAPHR Provisioning rule UI. bug24761-US4453
     * called from UI form, for by operation disable provisioning rule
     * @return
     * @throws GeneralException
     */
    public List<SelectItem> getSAPHRDisableProvisioningRules() throws GeneralException {
        if (_saphrDisableProvisioningRules == null) {
            _saphrDisableProvisioningRules = getConfigBasedRules(Rule.Type.SapHrOperationProvisioning, Connector.CONFIG_SAPHR_DISABLE_PROVISION_RULE);

        }
        return _saphrDisableProvisioningRules;
    }

    /**
     * Introduced as a getter for SAPHR Provisioning rule UI. bug24761-US4453
     * called from UI form, for by operation delete provisioning rule
     * @return
     * @throws GeneralException
     */
    public List<SelectItem> getSAPHRDeleteProvisioningRules() throws GeneralException {
        if (_saphrDeleteProvisioningRules == null) {
            _saphrDeleteProvisioningRules = getConfigBasedRules(Rule.Type.SapHrOperationProvisioning, Connector.CONFIG_SAPHR_DELETE_PROVISION_RULE);
        }
        return _saphrDeleteProvisioningRules;
    }

    /**
     * Introduced as a getter for SAPHR Provisioning rule UI. bug24761-US4453
     * called from UI form, for by operation unlock provisioning rule
     * @return
     * @throws GeneralException
     */
    public List<SelectItem> getSAPHRUnlockProvisioningRules() throws GeneralException {
        if (_saphrUnlockProvisioningRules == null) {
            _saphrUnlockProvisioningRules = getConfigBasedRules(Rule.Type.SapHrOperationProvisioning, Connector.CONFIG_SAPHR_UNLOCK_PROVISION_RULE);

        }
        return _saphrUnlockProvisioningRules;
    }
    
    /**
     * Introduced as a getter for SAPHR Custom Manager Model Rules UI.
     * called from UI form, for Custom manager rule
     * @return
     * @throws GeneralException
     */
    public List<SelectItem> getSAPHRCustomManagerModelRule() throws GeneralException {
        if (_sapHRCustomManagerRule == null) {
            _sapHRCustomManagerRule = getConfigBasedRules(Rule.Type.SAPHRManagerRule, Connector.SAPHR_CUSTOM_MANAGER_MODEL_RULE);
        }       
        return _sapHRCustomManagerRule;
    }
    
    /**
     * Introduced as a getter for SAPHR Provisioning rule UI. bug24761-US4453
     * called from UI form, for by operation create provisioning rule
     * @return
     * @throws GeneralException
     */
    public List<SelectItem> getSAPHRCreateProvisioningRules() throws GeneralException {
        if (_saphrCreateProvisioningRules == null) {
            _saphrCreateProvisioningRules = getConfigBasedRules(Rule.Type.SapHrOperationProvisioning, Connector.CONFIG_SAPHR_CREATE_PROVISION_RULE);

        }
        return _saphrCreateProvisioningRules;
    }

    /**
     * Introduced as a getter for SAPHR Provisioning rule UI. bug24761-US4453
     * called from UI form, for by operation modify provisioning rule
     * @return
     * @throws GeneralException
     */
    public List<SelectItem> getSAPHRModifyProvisioningRules() throws GeneralException {
        if (_saphrModifyProvisioningRules == null) {
            _saphrModifyProvisioningRules = getConfigBasedRules(Rule.Type.SapHrOperationProvisioning, Connector.CONFIG_SAPHR_MODIFY_PROVISION_RULE);

        }
        return _saphrModifyProvisioningRules;
    }

    /**
     * Introduced as a getter for PeopleSoft HRMS Provisioning rule UI.
     * Called from ui form, for generic provisioining rule
     * @return
     * @throws GeneralException
     */
    public List<SelectItem> getPeopleSoftHRMSProvisionRules() throws GeneralException {
        if ( _peoplesofthrmsProvisionRules == null) {
            _peoplesofthrmsProvisionRules = getConfigBasedRules(Rule.Type.PeopleSoftHRMSProvision, Connector.CONFIG_PEOPLESOFTHRMS_PROVISION_RULE);

        }
        return _peoplesofthrmsProvisionRules;
    }

    /**
     * Introduced as a getter for PeopleSoft HRMS Provisioning rule UI.
     * called from UI form, for by operation enable provisioning rule
     * @return
     * @throws GeneralException
     */

    public List<SelectItem> getPeopleSoftHRMSEnableProvisioningRules() throws GeneralException {
        if (_peoplesofthrmsEnableProvisioningRules == null) {
            _peoplesofthrmsEnableProvisioningRules = getConfigBasedRules(Rule.Type.PeopleSoftHRMSOperationProvisioning, Connector.CONFIG_PEOPLESOFTHRMS_ENABLE_PROVISION_RULE);

        }
        return _peoplesofthrmsEnableProvisioningRules;
    }

    /**
     * Introduced as a getter for PeopleSoft HRMS Provisioning rule UI.
     * called from UI form, for by operation disable provisioning rule
     * @return
     * @throws GeneralException
     */
    public List<SelectItem> getPeopleSoftHRMSDisableProvisioningRules() throws GeneralException {
        if (_peoplesofthrmsDisableProvisioningRules == null) {
            _peoplesofthrmsDisableProvisioningRules = getConfigBasedRules(Rule.Type.PeopleSoftHRMSOperationProvisioning, Connector.CONFIG_PEOPLESOFTHRMS_DISABLE_PROVISION_RULE);

        }
        return _peoplesofthrmsDisableProvisioningRules;
    }

    /**
     * Introduced as a getter for PeopleSoft HRMS Provisioning rule UI. 
     * called from UI form, for by operation delete provisioning rule
     * @return
     * @throws GeneralException
     */
    public List<SelectItem> getPeopleSoftHRMSDeleteProvisioningRules() throws GeneralException {
        if (_peoplesofthrmsDeleteProvisioningRules == null) {
            _peoplesofthrmsDeleteProvisioningRules = getConfigBasedRules(Rule.Type.PeopleSoftHRMSOperationProvisioning, Connector.CONFIG_PEOPLESOFTHRMS_DELETE_PROVISION_RULE);
        }
        return _peoplesofthrmsDeleteProvisioningRules;
    }

    /**
     * Introduced as a getter for PeopleSoft HRMS Provisioning rule UI.
     * called from UI form, for by operation unlock provisioning rule
     * @return
     * @throws GeneralException
     */
    public List<SelectItem> getPeopleSoftHRMSUnlockProvisioningRules() throws GeneralException {
        if (_peoplesofthrmsUnlockProvisioningRules == null) {
            _peoplesofthrmsUnlockProvisioningRules = getConfigBasedRules(Rule.Type.PeopleSoftHRMSOperationProvisioning, Connector.CONFIG_PEOPLESOFTHRMS_UNLOCK_PROVISION_RULE);

        }
        return _peoplesofthrmsUnlockProvisioningRules;
    }

    /**
     * Introduced as a getter for PeopleSoft HRMS Provisioning rule UI.
     * called from UI form, for by operation create provisioning rule
     * @return
     * @throws GeneralException
     */
    public List<SelectItem> getPeopleSoftHRMSCreateProvisioningRules() throws GeneralException {
        if (_peoplesofthrmsCreateProvisioningRules == null) {
            _peoplesofthrmsCreateProvisioningRules = getConfigBasedRules(Rule.Type.PeopleSoftHRMSOperationProvisioning, Connector.CONFIG_PEOPLESOFTHRMS_CREATE_PROVISION_RULE);

        }
        return _peoplesofthrmsCreateProvisioningRules;
    }

    /**
     * Introduced as a getter for PeopleSoft HRMS Provisioning rule UI.
     * called from UI form, for by operation modify provisioning rule
     * @return
     * @throws GeneralException
     */
    public List<SelectItem> getPeopleSoftHRMSModifyProvisioningRules() throws GeneralException {
        if (_peoplesofthrmsModifyProvisioningRules == null) {
            _peoplesofthrmsModifyProvisioningRules = getConfigBasedRules(Rule.Type.PeopleSoftHRMSOperationProvisioning, Connector.CONFIG_PEOPLESOFTHRMS_MODIFY_PROVISION_RULE);

        }
        return _peoplesofthrmsModifyProvisioningRules;
    }

    public List<SelectItem> getJDBCProvisionRules() throws GeneralException {
        if ( _jdbcProvisionRules == null) {
            _jdbcProvisionRules = getConfigBasedRules(Rule.Type.JDBCProvision, Connector.CONFIG_JDBC_PROVISION_RULE);
            
        }
        return _jdbcProvisionRules;
    }
    
    public List<SelectItem> getJDBCEnableProvisioningRules() throws GeneralException {
        if ( _jdbcEnableProvisioningRules == null) {
        	_jdbcEnableProvisioningRules = getConfigBasedRules(Rule.Type.JDBCOperationProvisioning, Connector.CONFIG_JDBC_ENABLE_PROVISION_RULE);
            
        }
        return _jdbcEnableProvisioningRules;
    }
    public List<SelectItem> getJDBCDisableProvisioningRules() throws GeneralException {
        if ( _jdbcDisableProvisioningRules == null) {
        	_jdbcDisableProvisioningRules = getConfigBasedRules(Rule.Type.JDBCOperationProvisioning, Connector.CONFIG_JDBC_DISABLE_PROVISION_RULE);
            
        }
        return _jdbcDisableProvisioningRules;
    }
    public List<SelectItem> getJDBCDeleteProvisioningRules() throws GeneralException {
        if ( _jdbcDeleteProvisioningRules == null) {
        	_jdbcDeleteProvisioningRules = getConfigBasedRules(Rule.Type.JDBCOperationProvisioning, Connector.CONFIG_JDBC_DELETE_PROVISION_RULE);
            
        }
        return _jdbcDeleteProvisioningRules;
    }
    public List<SelectItem> getJDBCUnlockProvisioningRules() throws GeneralException {
        if ( _jdbcUnlockProvisioningRules == null) {
        	_jdbcUnlockProvisioningRules = getConfigBasedRules(Rule.Type.JDBCOperationProvisioning, Connector.CONFIG_JDBC_UNLOCK_PROVISION_RULE);
            
        }
        return _jdbcUnlockProvisioningRules;
    }
    public List<SelectItem> getJDBCCreateProvisioningRules() throws GeneralException {
        if ( _jdbcCreateProvisioningRules == null) {
        	_jdbcCreateProvisioningRules = getConfigBasedRules(Rule.Type.JDBCOperationProvisioning, Connector.CONFIG_JDBC_CREATE_PROVISION_RULE);
            
        }
        return _jdbcCreateProvisioningRules;
    }
    public List<SelectItem> getJDBCModifyProvisioningRules() throws GeneralException {
        if ( _jdbcModifyProvisioningRules == null) {
        	_jdbcModifyProvisioningRules = getConfigBasedRules(Rule.Type.JDBCOperationProvisioning, Connector.CONFIG_JDBC_MODIFY_PROVISION_RULE);
            
        }
        return _jdbcModifyProvisioningRules;
    }
    
    /**
     * This method is used for successfactors connector's provisioning rule for
     * MODIFY operation.
     * 
     * @throws GeneralException
     * 
     */
    public List<SelectItem> getSuccessFactorsOperationProvisioning() throws GeneralException {
        if ( _successFactorsOperationProvisioning == null) {
            _successFactorsOperationProvisioning = getConfigBasedRules(Rule.Type.SuccessFactorsOperationProvisioning, Connector.CONFIG_SUCCESSFACTORS_PROVISION_RULE);
        }
        return _successFactorsOperationProvisioning;
    }  
    public List<SelectItem> getSMEncryptions() throws Exception {
        _smEncryptions = new ArrayList<SelectItem>();
        _smEncryptions.add(new SelectItem("-", "Encryption Off"));
        _smEncryptions.add(new SelectItem("T", "Triple DES"));
        _smEncryptions.add(new SelectItem("E", "DES"));
    
        return _smEncryptions;
    }
    
    public List<SelectItem> getMergeMapsRules() throws GeneralException {
        if ( _mergeMapRules == null) {
            _mergeMapRules = getConfigBasedRules(Rule.Type.MergeMaps, Connector.CONFIG_MERGE_MAPS_RULE);
        }
        return _mergeMapRules;
    }
    
    public List<SelectItem> getWebServiceAfterOperationRules() throws GeneralException {
        if ( _webServiceAfterOperationRules == null) {
            _webServiceAfterOperationRules = getConfigBasedRules(Rule.Type.WebServiceAfterOperationRule, Connector.CONFIG_RWS_AFTER_OPERATION_RULE);
            
        }
        return _webServiceAfterOperationRules;
    }
    
    public List<SelectItem> getWebServiceBeforeOperationRules() throws GeneralException {
        if ( _webServiceBeforeOperationRules == null) {
            _webServiceBeforeOperationRules = getConfigBasedRules(Rule.Type.WebServiceBeforeOperationRule, Connector.CONFIG_RWS_BEFORE_OPERATION_RULE);
            
        }
        return _webServiceBeforeOperationRules;
    }

    public List<SelectItem> getTransformationRules() throws GeneralException {
        if ( _transformationRules == null) {
            _transformationRules = getConfigBasedRules(Rule.Type.Transformation, Connector.CONFIG_TRANSFORMATION_RULE);
        }
        return _transformationRules;
    }

    public List<SelectItem> getPreIterateRules() throws GeneralException {
        if ( _preIterateRules == null) {
            _preIterateRules = getConfigBasedRules(Rule.Type.PreIterate, "preIterateRule");
        }
        return _preIterateRules;
    }

    public List<SelectItem> getPostIterateRules() throws GeneralException {
        if ( _postIterateRules == null) {
            _postIterateRules = getConfigBasedRules(Rule.Type.PostIterate, "postIterateRule");
        }
        return _postIterateRules;
    }

    public List<SelectItem> getBeforeProvisioningRules() throws GeneralException {
        if ( _beforeProvisioningRules == null) {
            _beforeProvisioningRules = getConfigBasedRules(Rule.Type.BeforeProvisioning, Application.ATTR_BEFORE_PROVISIONING_RULE);
        }
        return _beforeProvisioningRules;
    }
    
    public List<SelectItem> getAfterProvisioningRules() throws GeneralException {
        if ( _afterProvisioningRules == null) {
            _afterProvisioningRules = getConfigBasedRules(Rule.Type.AfterProvisioning, Application.ATTR_AFTER_PROVISIONING_RULE);
        }
        return _afterProvisioningRules;
    }

    public List<SelectItem> getConfigBasedRules(Rule.Type type, String configAttr) throws GeneralException {
        List<SelectItem> rules = null;
        String ruleName = null;
        Rule selected = null;
        Application app = getObject();
        if  ( app != null ) {
            Attributes<String, Object> attrs = app.getAttributes();
            if (attrs != null) {
                //wtf is going on here?
                String rule = attrs.getString(configAttr);
                if (rule != null) {
                    selected = getContext().getObjectByName(Rule.class, rule);
                }
            }
        }
        rules = WebUtil.getRulesByType(getContext(), type, true, false, ((null != selected) ? ruleName : null));

        return rules;
    }


    /**
     * Return the name of the currently selected creation rule.
     */
    public String getCreationRule() throws GeneralException {
        Application app = getObject();
        Rule rule = app.getCreationRule();
        return (rule != null) ? rule.getName() : null;
    }

    /**
     * Set the creation rule by name.
     */
    public void setCreationRule(String name) throws GeneralException {
        Application app = getObject();
        if (name == null || name.length() == 0) {
            // the "select a rule" placeholder
            app.setCreationRule(null);
        }
        else {
            Rule rule = getContext().getObjectByName(Rule.class, name);
            if (rule != null)
                app.setCreationRule(rule);
            else
                app.setCreationRule(null);
        }
    }

    /**
     * Return the names of all correlation rules, suitable for use
     * in a selectOneMenu.
     */
    public List<SelectItem> getCreationRules() throws GeneralException {

        if (_creationRules == null) {
            Rule selected = (null != getObject()) ? getObject().getCreationRule() : null;
            _creationRules = WebUtil.getRulesByType(getContext(),
                                                                        Rule.Type.IdentityCreation,
                                                                        true, false,
                                                                        ((null != selected) ? selected.getName() : null));

        }
        return _creationRules;
    }

    /**
     * Return the names of all manager correlation rules, suitable for use
     * in a selectOneMenu.
     */
    public List<SelectItem> getManagerCorrelationRules() throws GeneralException {

        if (_managerCorrelationRules == null) {
            Rule selected = (null != getObject()) ? getObject().getManagerCorrelationRule() : null;
            _managerCorrelationRules = WebUtil.getRulesByType(getContext(),
                                                                                         Rule.Type.ManagerCorrelation,
                                                                                         true, false,
                                                                                         ((null != selected) ? selected.getName() : null));

        }
        return _managerCorrelationRules;
    }

    /**
     * Return the name of the currently selected manager correlation rule.
     */
    public String getManagerCorrelationRule() throws GeneralException {
        Application app = getObject();
        Rule rule = app.getManagerCorrelationRule();
        return (rule != null) ? rule.getName() : null;
    }


    /**
     * Set the manager correlation rule by name.
     */
    public void setManagerCorrelationRule(String name) throws GeneralException {

        Application app = getObject();
        if (name == null || name.length() == 0) {
            // this is the "Select a Rule" placeholder, null the reference
            app.setManagerCorrelationRule(null);
        }
        else {
            Rule rule = getContext().getObjectByName(Rule.class, name);
            if (rule != null)
                app.setManagerCorrelationRule(rule);
            else {
                // somehow got an invalid selector, null or leave
                // the current value?
                app.setManagerCorrelationRule(null);
            }
        }
    }

    public List<SelectItem> getCustomizationRules() throws GeneralException {

        if (_customizationRules == null) {
            Rule selected = (null != getObject()) ? getObject().getCustomizationRule() : null;
            _customizationRules = WebUtil.getRulesByType(getContext(),
                                                                                Rule.Type.ResourceObjectCustomization,
                                                                                true, false,
                                                                                ((null != selected) ? selected.getName() : null));

        }
        return _customizationRules;
    }

    public String getCustomizationRule() throws GeneralException {
        Application app = getObject();
        Rule rule = app.getCustomizationRule();
        return (rule != null) ? rule.getName() : null;
    }

    public void setCustomizationRule(String name ) throws GeneralException {
        Application app = getObject();
        if (name == null || name.length() == 0) {
            // this is the "Select a Rule" placeholder, null the reference
            app.setCustomizationRule(null);
        }
        else {
            Rule rule = getContext().getObjectByName(Rule.class, name);
            if (rule != null)
                app.setCustomizationRule(rule);
            else {
                // somehow got an invalid selector, null or leave
                // the current value?
                app.setCustomizationRule(null);
            }
        }
    }

    public List<SelectItem> getManagedAttributeCustomizationRules() throws GeneralException {

        if (_managedAttributeCustomizationRules == null) {
            Rule selected = (null != getObject()) ? getObject().getManagedAttributeCustomizationRule() : null;
            _managedAttributeCustomizationRules = WebUtil.getRulesByType(getContext(),
                                                                                                          Rule.Type.ManagedAttributePromotion, true, false,
                                                                                                          ((null != selected) ? selected.getName() : null));

        }
        return _managedAttributeCustomizationRules;
    }

    public String getManagedAttributeCustomizationRule() throws GeneralException {
        Application app = getObject();
        Rule rule = app.getManagedAttributeCustomizationRule();
        return (rule != null) ? rule.getName() : null;
    }

    public void setManagedAttributeCustomizationRule(String name) throws GeneralException {
        Application app = getObject();
        if (name == null || name.length() == 0) {
            // this is the "Select a Rule" placeholder, null the reference
            app.setManagedAttributeCustomizationRule(null);
        }
        else {
            Rule rule = getContext().getObjectByName(Rule.class, name);
            if (rule != null)
                app.setManagedAttributeCustomizationRule(rule);
            else {
                // somehow got an invalid selector, null or leave
                // the current value?
                app.setManagedAttributeCustomizationRule(null);
            }
        }
    }

    public List<SelectItem> getRemediationModificationTypes() {
        if (null == _remediationModificationTypes) {
            _remediationModificationTypes = new ArrayList<SelectItem>();
            _remediationModificationTypes.add(new SelectItem(UserInterfaceInputType.None,
                                                             getMessage(UserInterfaceInputType.None.getMessageKey())));
            _remediationModificationTypes.add(new SelectItem(UserInterfaceInputType.Select,
                                                             getMessage(UserInterfaceInputType.Select.getMessageKey())));
            _remediationModificationTypes.add(new SelectItem(UserInterfaceInputType.Freetext,
                                                             getMessage(UserInterfaceInputType.Freetext.getMessageKey())));
        }

        return _remediationModificationTypes;
    }
    
    /**
     * This will return a JSON representation of the remediation modification types to be used for the popup 
     * window in the attribute definition schema
     * 
     * @return Serialzed JSON of the remediation modification types
     * @throws GeneralException
     */
    public String getRemediationModificationTypesJSON() throws GeneralException {
        return JsonHelper.toJson(getRemediationModificationTypes());
    }
    /**
     * This is a deprecated way to return the attributes that are necessary
     * for configuration.  The "newer" way is to just define a formPath on the
     * ApplicationTemplate defined in the ConnectorRegistry and let the xhtml
     * form render/collect the variables.
     */
    public List<AttributeDefinition> getAttributeConfig()
            throws GeneralException {
        if (_attrConfig == null) {
            _attrConfig = new ArrayList<AttributeDefinition>();

            Application app = getObject();
            if (app != null) {
                String templateName = app.getTemplateApplication();
                if ( templateName == null ) {
                    return _attrConfig;
                }
                if ( Util.getString(templateName) != null ) {
                    try {
                        _attrConfig = DefaultApplicationFactory.getDefaultConfigAttributesByTemplate(templateName);
                    } catch (GeneralException ex) {
                        addMessage(new Message(Message.Type.Error,
                                MessageKeys.ERR_CANT_FIND_ATTR_CONF, templateName), null);
                    }
                }
            }
        }

        return _attrConfig;
    } // getAttributeConfig()

    public AttributeEditBean getAttributeEditor() throws GeneralException {
        return getAttributeEditor(true);
    }

    private AttributeEditBean getAttributeEditor(boolean bootstrap)
        throws GeneralException {
        if (_editedAttributes == null) {
            _editedAttributes = (AttributeEditBean)getSessionScope().get(ATT_ATTRIBUTE_EDITOR);
            if (_editedAttributes == null && bootstrap) {
                // cache okay or get a fresh one?
                ObjectConfig config = Application.getObjectConfig();
                if (config == null) {
                    // fake it so we don't NPE
                    config = new ObjectConfig();
                }
                Application app = getObject();

                if (app.getAttributes() == null || app.getAttributes().isEmpty()) {
                    if (isNewApplication(app)){
                        app.setAttributes(config.getDefaultValues());
                    } else {
                        app.setAttributes(new Attributes<String, Object>());
                    }
                }

                _editedAttributes = new AttributeEditBean(app.getId(), Application.class, config.getObjectAttributes(), null, app.getAttributes());
            }
        }
        return _editedAttributes;
    }

    private boolean isNewApplication(Application app) {
       return (app.getId() == null);
    }

    /**
     * @return true if RapidSetup is enabled
     */
    public boolean  isRapidSetupEnabled() {
        return RapidSetupConfigUtils.isEnabled();
    }

    /**
     *
     * @param type
     * @return
     */
    private boolean isSchemaDefined(String type) {
        boolean isSchemaDefined = false;
        List<SchemaDTO> schemas = getSchemaDTOs();
        for (SchemaDTO dto : schemas) {
            String objectType = dto.getObjectType();
            if ( ( type != null ) && ( type.equals(objectType) ) ) {
                isSchemaDefined = true;
            }
        }
        return isSchemaDefined;
    }  // isSchemaDefined(String)

    /**
     *
     * @return
     */
    public boolean isAccountSchemaDefined() {
        return isSchemaDefined(Connector.TYPE_ACCOUNT);
    }

    public int getDataSourceCount() {
        int dataSourceCount = 0;        

        try {
            Application app = getObject();
            if (app != null) {
                List<ActivityDataSource> dataSources = app.getActivityDataSources();
                if (dataSources == null) {
                    dataSourceCount = 0;
                } else {
                    dataSourceCount = dataSources.size();
                }
            }
        } catch (GeneralException e) {
            Message msg = new Message(Message.Type.Error, MessageKeys.ERR_DS_NOT_AVAILABLE);
            log.error(msg.getMessage() , e);
            addMessage(msg, null);
        }

        return dataSourceCount;
    }

    /**
     * @deprecated use {@link #isSchemaDefined(String)}
     *
     * @return
     */
    public boolean isGroupSchemaDefined() {
        return isSchemaDefined(Connector.TYPE_GROUP);
    }

    /**
     *
     * @param type objectType of the schema to add
     */
    private void addSchema(String type) throws GeneralException {
        List<SchemaDTO> schemaDTOs = getSchemaDTOs();

        if (!isSchemaDefined(type)) {
            //Should use the connectorRegistry Schema template instead of empty
            Application app = getObject();
            Schema newSchema = null;
            if (app != null) {
                String appKey = app.getTemplateApplication();
                newSchema = getDefaultApplication().getSchema(type);
            }
            if (newSchema == null) {
                //Couldn't find schema on app, create empty schema
                newSchema = new Schema(type, "");
            }
            schemaDTOs.add(new SchemaDTO(newSchema, this));
        } else {
            addMessage(new Message(Message.Type.Error, MessageKeys.ERR_APP_ALREADY_CONTAINS_SCHEMA, type), null);
       }
    }  // addSchema(String)

    private void removeSchema(String type) {
        List<SchemaDTO> schemaDTOs = getSchemaDTOs();
        Iterator<SchemaDTO> iterator = schemaDTOs.iterator();
        while (iterator.hasNext()) {
            SchemaDTO dto = iterator.next();
            if (dto.getObjectType() != null && dto.getObjectType().equals(type)) {
                iterator.remove();
            }
        }
    }

    /**
     *
     * @return
     */
    public String addGroupSchemaAction() throws GeneralException {
        // IIQSAW-1887: Important to strip bad things from new group types, since it will be rendered directly
        String type = WebUtil.safeHTML(getCurrentlySelectedSchemaObjectType());
        //Validate that the schemaObjectType is valid
        if (!validateObjectType(type)) {
            addMessage(new Message(Message.Type.Error, MessageKeys.APP_SCHEMA_INVALID_OBJECT_TYPE));
            return "";
        }

        addSchema(type);
        groupSchemaNew.put(type, Boolean.TRUE);

        List<Form> forms = null;

        //Try to fetch forms for Default App
        if (getDefaultApplication() != null) {
            forms = getDefaultApplication().getProvisioningForms(type);
        }

        getFormEditor().addObjectType(type, forms);
        buildSchemaAttributeEditBean();

        return "";
    }

    /**
     * Restricted names of Schema Object Types.
     */
    protected enum RestrictedObjectTypes {
      ManagedAttribute(ProvisioningPlan.OBJECT_TYPE_MANAGED_ATTRIBUTE),
      Permission(sailpoint.object.ManagedAttribute.Type.Permission.name()),
      Entitlement(sailpoint.object.ManagedAttribute.Type.Entitlement.name());

      private String val;

       RestrictedObjectTypes(String val) {
           this.val = val;
       }

       public String getVal() {
           return this.val;
       }
    };

    /**
     * Test if a given schemaObjectType is not contained in our restricted objectTypes
     * @param type SchemaObjectType to test
     * @return false if the objectType value is not allowed
     */
    private boolean validateObjectType(String type) {
        boolean isValid = Util.isNotNullOrEmpty(type);

        if (isValid) {
            for (RestrictedObjectTypes invalidType : RestrictedObjectTypes.values()) {
                if (invalidType.getVal().equalsIgnoreCase(type)) {
                    isValid = false;
                    break;
                }
            }
        }
        return isValid;
    }

    /**
     * Read whether the schemaObjectType is new from the session variable
     */
    private boolean isSchemaObjectTypeNew(String schemaObjectType) {
        Boolean isNew = (Boolean) groupSchemaNew.get(schemaObjectType);
        if (isNew == null) {
            isNew = false;
        }
        return isNew;
    }

    /**
     * When we are creating, deleting, modifying a group schema, we need the
     * schemaObjectType now (since we support multiple schema types for 6.4).
     * This method will look at the schemaObjectType and if null or empty return
     * the default which is 'group' type.
     */
    private String getCurrentlySelectedSchemaObjectType() {
        String type = getSchemaObjectType();
        if (Util.isNullOrEmpty(type)) {
            type = Connector.TYPE_GROUP;
        }
        return type;
    }

    /**
     * "New Schema Attribute" action listener for the edit Application page.
     * This adds a new attribute definition to the schema list which will cause
     * a new row to be displayed in the UI table.
     */
    public void newSchemaAttributeAction(ActionEvent event)
            throws GeneralException {

        UIComponent c = event.getComponent();
        UIComponent otComponent = c.getParent().findComponent("objectType");
        ValueBinding vb = otComponent.getValueBinding("value");
        Object objectType = vb.getValue(getFacesContext());
        Application app = getObject();
        if (app != null && objectType != null) {

            AttributeDefinition attrDef = null;
            // Check if the new attribute is being sourced from a tier. If so copy the original
            if ( isComposite() && _sourceApplication != null && !"".equals(_sourceAttribute)){
                Application sourceApp = getContext().getObjectByName(Application.class, _sourceApplication);
                Schema schema = sourceApp.getSchema(objectType.toString());
                attrDef = schema.getAttributeDefinition(_sourceAttribute);
                attrDef.setCompositeSource(_sourceApplication,  _sourceAttribute);
            } else {
                attrDef = new AttributeDefinition("", PropertyInfo.TYPE_STRING);
            }

            // reset these fields
            _sourceApplication = null;
            _sourceAttribute = null;

            SchemaDTO s = getSchemaDTO(objectType.toString());

            if (s != null) {
                s.getAttributes().add(new SchemaAttributeDTO(attrDef, s.getObjectType()));
            }
        }
    } // newSchemaAction()

    public void discoverSchemasAction() throws GeneralException {
        if (getSupportsSchemaDiscovery()) {
            List<String> objectTypes = Arrays.asList(Connector.TYPE_ACCOUNT, Connector.TYPE_GROUP);

            for (SchemaDTO dto : _schemaDTOs) {
                if (dto.getObjectType() != null && !objectTypes.contains(dto.getObjectType())) {
                    objectTypes.add(dto.getObjectType());
                }
            }
            for ( String objectType : objectTypes) {
                discoverSchema(objectType);
            }
        }
    }

    public void discoverSchemaAttributesAction(ActionEvent event) throws GeneralException {
        UIComponent c = event.getComponent();
        UIComponent otComponent = c.getParent().findComponent("objectType");
        ValueBinding vb = otComponent.getValueBinding("value");
        Object objectType = vb.getValue(getFacesContext());
        discoverSchema((String)objectType);
    }
    
    private void discoverSchema(String objectType) throws GeneralException {
        Application app = getObject();
        if (app != null && objectType != null) {
            pruneConfig(app);
            Connector connector = ConnectorFactory.getConnectorNoClone(app,null);
            if ( connector != null ) {
                try {
                    Schema discovered = connector.discoverSchema(objectType,null);
                    if ( discovered != null ) {
                        SchemaDTO current = getSchemaDTO(objectType);
                        if ( current != null )
                           discovered = mergeSchemas(current, discovered);

                        addSchemaDTO(discovered);
                        app.addSchema(discovered);    //the application is updated or else Test configuartion fails to work when applictaion not saved

                    } else {
                        log.debug("Connector returned an empty schema for ["+objectType+"].");
                    }
                    ObjectUtil.updateUnSavedApplicationConfig(app);
                } catch(ConnectorException e) {
                    log.error("Exception while discovering schema ["+e.toString()+"]");
                    addMessage(new Message(Message.Type.Error, "Unable to discover the ["+objectType+"] schema for this application.["+e.toString()+"]"), null);
                }
            }

        }
    } // newSchemaAction()

    /**
     * Preserve any values that have been entered prior to the discover.
     */
    private Schema mergeSchemas(SchemaDTO old, Schema neu) throws GeneralException {

         Schema schema = neu;
         if ( old != null ) {

             // merge all top level attributes if they are non null and exist in the new schema
             String displayName = old.getDisplayAttribute();
             if ( ( Util.getString(displayName) != null ) && ( neu.getAttributeDefinition(displayName) != null ) ) {
                 schema.setDisplayAttribute(displayName);
             }
             String identityAttribute = old.getIdentityAttribute();
             if ( ( Util.getString(identityAttribute) != null ) && ( neu.getAttributeDefinition(identityAttribute) != null ) ) {
                 schema.setIdentityAttribute(identityAttribute);
             }
             String instance = old.getInstanceAttribute();
             if ( ( Util.getString(instance) != null )  && ( neu.getAttributeDefinition(instance) != null ) )  {
                 schema.setInstanceAttribute(instance);
             }
             String descriptionAttribute = old.getDescriptionAttribute();
             if ( ( Util.getString(descriptionAttribute) != null )  && ( neu.getAttributeDefinition(descriptionAttribute) != null ) )  {
                 schema.setDescriptionAttribute(descriptionAttribute);
             }
             schema.setIncludePermissions(old.isIncludePermissions());
             schema.setPermissionsRemediationModificationType(old.getRemediationModifiable());

             // merge any previously configured descriptions
             List<AttributeDefinition> defs = neu.getAttributes();
             if ( Util.size(defs) > 0 ) {
                 for ( AttributeDefinition def : defs ) {
                     if ( Util.getString(def.getDescription()) == null ) {
                         String name = def.getName();
                         SchemaAttributeDTO oldDef = old.getAttribute(name);
                         if ( oldDef != null ) {
                             String description = oldDef.getDescription();
                             if ( Util.getString(description) != null ) {
                                 def.setDescription(description);
                             }
                         }
                     }
                 }
             }
         }
         return schema;
    }

    public String updateProvPolicies() throws GeneralException
    {
        setIsProvPolicyUpdated(true);
        mergeProvisioningPolicies(_templateChoice);
        return "";
    }

    /**
     * If user select option as new then Provisioning Policies of new Application will replaced with Provisioning Policies of 
     * existing application else Provisioning Policies will be belong to the old application.
     */
    public void mergeProvisioningPolicies(String option) throws GeneralException
    {
        if ( ( getReconfigurableApp() != null ) && Util.isNotNullOrEmpty(option) && option.equalsIgnoreCase("new") ){
            getObject().setOldTemplates(getReconfigurableApp().getTemplates());
            buildFormEditBean();
        }
    }

    /**
     * New Schema Attribute action. There is nothing here, but
     * apparently is required for a command button to call an
     * actionListener.
     *
     * @return empty string to keep current page visible
     */
    public String newSchemaAttribute() {
        return "";
    } // newSchemaAttribute()

    /**
     * "Delete Schema Attribute" action listener for the edit
     * Application page.  This deletes an attribute definition to the
     * schema list which will cause a row to be deleted in the UI table.
     */
    public void deleteSchemaAttributeAction(ActionEvent event)
            throws GeneralException {
        UIComponent c = event.getComponent();
        UIComponent otComponent = c.getParent().findComponent("objectType");
        ValueBinding vb = otComponent.getValueBinding("value");
        String objectType = vb.getValue(getFacesContext()).toString();

        if (objectType != null && _schemaDTOs != null) {
            SchemaDTO schemaToModify = getSchemaDTO(objectType);

            if (schemaToModify == null)
                return;

            // to avoid concurrent modification exceptions from modifying the
            // lists that we are walking, save off the items to be deleted
            // and then explicitly delete them as a follow on step.
            List<SchemaAttributeDTO> current = schemaToModify.getAttributes();
            List<SchemaAttributeDTO> toBeDeleted = new ArrayList<SchemaAttributeDTO>();
            for (SchemaAttributeDTO attr : current) {
                if (attr.isSelected()) {
                    toBeDeleted.add(attr);
                }
            }

            current.removeAll(toBeDeleted);
        }
    } // deleteSchemaAttributeAction()

    /**
     * Delete Schema Attribute action. There is nothing here, but
     * apparently is required for a command button to call an
     * actionListener.
     *
     * @return empty string to keep current page visible
     */
    public String deleteSchemaAttribute() {
        return "";
    } // deleteSchemaAttribute()


    /**
     *
     * @return
     */
    public String changeType() {
        try {
            Application app = getObject();
            app.setFormPath(null);
            app.setFormPathRules(null);
            app.setTemplateApplication(null);

            _attrConfig = null;
            
            //bug#20471 -- clear defaultApp
            _defaultApp = null; 
            
            Application defaultApp = getDefaultApplication();
            if ( defaultApp != null ) {
                boolean appReconf = isAppReconfMode();
                if (appReconf) {
                    initReconfigurableApp(defaultApp);
                    replaceOldAppAttributes(app);
                } else {
                    app.assimilate(defaultApp);
                }
            }
            /* Remove any unstructured target junk */
            removeEditState(SELECTED_TARGET_DS_ID);
            removeEditState(EDITED_TARGET_DS);
            removeEditState(EDITED_TARGET_DS_MAP);
            removeEditState(DELETED_TARGET_DS_LIST);
            // If no schemas exist, create account schema
            if (Util.isEmpty(app.getSchemas())) {
                if (app.getSchemas() == null) {
                    app.setSchemas(new ArrayList<Schema>());
                }
                List<Schema> schemas = app.getSchemas();
                schemas.add(new Schema(Connector.TYPE_ACCOUNT, Connector.TYPE_ACCOUNT));
                getSessionScope().put(ACCOUNT_SCHEMA_NEW, true);
            }
            List<SchemaDTO> schemaDTOs = buildSchemaDTOs();
            setSchemaDTOs(schemaDTOs);
            buildSchemaAttributeEditBean();
            buildFormEditBean();
        } catch (GeneralException ex) {
            // ignore
        }

        return "";
    }

    /**
     * Replacing below parameters of the existing (old) application with new (reconfigured) application
     * type
     * icon
     * connector class
     * configuration attribute (But making sure that we don't remove vital rule attributes 
     * from the old application)
     * FeaturesString  (But making sure that we don't remove vital Features 
     * from the old application featureString))
     */    
    public void replaceOldAppAttributes(Application oldApp) throws GeneralException
     {
        if (getReconfigurableApp() != null && oldApp != null){

            oldApp.setType(getReconfigurableApp().getType());

            oldApp.setIcon(getReconfigurableApp().getIcon());

            oldApp.setConnector(getReconfigurableApp().getConnector());

            Map<String,Object> appConfAttributes = oldApp.getAttributes();

            if (!Util.isEmpty(appConfAttributes) ) {
                Iterator<String> keys = appConfAttributes.keySet().iterator();

                while (keys.hasNext()) {
                    String key = keys.next().toString();
                    if( getRuleAttributeNames().contains(key) ) {
                        getReconfigurableApp().setAttribute(key,appConfAttributes.get(key));
                    }
                }
                oldApp.setAttributes(getReconfigurableApp().getAttributes()); 
            }

            List<Feature> oldFeatureList = oldApp.getFeatures();
            List<Feature> newFeatureList=getReconfigurableApp().getFeatures();
            if ( oldFeatureList.contains( Feature.ADDITIONAL_ACCOUNT_REQUEST ) 
                              || oldFeatureList.contains( Feature.ACCOUNT_ONLY_REQUEST ) ) {
                if (!newFeatureList.contains( Feature.ADDITIONAL_ACCOUNT_REQUEST ) ) {
                    newFeatureList.add( Feature.ADDITIONAL_ACCOUNT_REQUEST );
                }
                if (!newFeatureList.contains( Feature.ACCOUNT_ONLY_REQUEST )) {
                    newFeatureList.add( Feature.ACCOUNT_ONLY_REQUEST );
                }
            }
            oldApp.setFeatures( getReconfigurableApp().getFeatures() );
        }
    }
    /**
     * @return true if the applicaton is using the connector type of Active Directory - Direct
     * @throws GeneralException
     */
    public boolean isActiveDirectoryType() throws GeneralException {
        Application app = getObject();
        String type = (app != null) ? app.getType() : "";
        return LDAPConnector.CONFIG_APP_AD_DIRECT.equals(type);
    }
    
    
    /**
     * @return true if the applicaton is using the connector type of RACF - LDAP
     * @throws GeneralException
     */
    public boolean isLdapRacfType() throws GeneralException {
        Application app = getObject();
        String type = (app != null) ? app.getType() : "";
        return LDAPConnector.CONFIG_APP_RACF_LDAP.equals(type);
    }

    /**
     * Executing this action will stash the current, edited application in
     * the session state. Once this request completes we will then call
     * ApplicationResource.testConnector which will retrieve the applicaiton
     * object and run connector debug. The session object created in this
     * request will also be removed in when connector debug is run.
     */
    public String prepareConnectorDebugSession() throws GeneralException{
        Application app = getObject();
        persistSchemaDTOs();
        this.getSessionScope().put("connectorDebug-" + app.getName(), app);
        return "";
    }

    public String testConnectorAction() throws GeneralException {
        Application app = null;
        Application connApp = null;
        try {
            app = getObject();
            persistSchemaDTOs();
            if((app != null) && (app.getType()!= null)) {
                if(app.getType().equalsIgnoreCase(AD_APP_TYPE)) {
                    saveForest(app);
                    saveDomains(app); //take newly added domains
                    saveAccountSearchScope(app); //take newly added account scopes
                    saveGroupSearchScope(app); //take newly added group scopes
                    saveExchangeSettings(app);   // take newly added exchange scopes
                }
                if (JWT_SUPPORTED_APPS.contains(app.getType())) {
                    // For JWT enabled connectors, process the private key
                    // file...
                    processPrivateKeyInfo(app);
                    if(_object.getType().equalsIgnoreCase(SCIM2_APP_TYPE)) {
                        processAdditionalPayloadInfo(app);
                    }
                }
                
                //For existing applications new secret attributes might not be present in encrypted
                //tag . Here we are checking if any attribute is missing and updating the encrypted tag
                if(AZURE_AS_IDP_APPS.contains(app.getType())) {
                    String encrypted = app.getAttributes().getString("encrypted");
                    if(Util.isNotNullOrEmpty(encrypted)) {
                       app.getAttributes().put("encrypted", updateEncryptedTagForSecrets(encrypted));
                    }
                    
                }
                
                // For client certificate authentication enabled
                // connectors, process the client certificate and
                // certificate key
                processClientCertificateInfo(app);
                
                if (app.getType().equalsIgnoreCase(WEBSERVICE_APP_TYPE) || _object.getType().equals(SCIM2_APP_TYPE)) {
                    saveParams(app, true);
                }
            }
            pruneConfig(app);
            // TODO: template instances relevant here?
            Connector conn = ConnectorFactory.getConnector(app, null);
            connApp = ObjectUtil.getLocalApplication(conn);
            
            conn.testConfiguration();
            _testResult = "Test Successful";
            _testSuccess = true;
        } catch (Throwable ex) {
            _testResult = ExceptionCleaner.cleanConnectorException(ex);
            _testSuccess = false;
            log.error("Connector failed.", ex);
        }
        finally {
            ObjectUtil.updateApplicationConfig(getContext(), connApp);
        }
        return "";
    }  // testConnectorAction()
    
    /**
     * Processes the private key information to be stored in application config.
     * 
     * @param app
     */
    private static void processPrivateKeyInfo(Application app) {
        Attributes<String, Object> attrs = app.getAttributes();
        String privateKeyToUpdate = (String) attrs.get("private_key_to_update");
        if (!Util.isEmpty(privateKeyToUpdate)) {
            attrs.remove("private_key_to_update");
            attrs.put("private_key", privateKeyToUpdate.trim());
            SimpleDateFormat dateFormatter = new SimpleDateFormat(
                    new Message(MessageKeys.CON_FORM_JWT_DATEFORMAT)
                    .toString());
            dateFormatter.setTimeZone(TimeZone.getTimeZone("GMT"));
            attrs.put("privateKeyUploadedOn", dateFormatter.format(Calendar
                    .getInstance(TimeZone.getTimeZone("GMT")).getTime()));
        }
    }
    
    /**
     * Processes the Addtional Payload information to be stored in application config.
     * 
     * @param app
     */
    private static void processAdditionalPayloadInfo(Application app) {
        Attributes<String, Object> attrs = app.getAttributes();
        String additionalPayloadToUpdate = (String) attrs.get("additional_payload_to_update");
        if (!Util.isEmpty(additionalPayloadToUpdate)) {
            attrs.remove("additional_payload_to_update");
            attrs.put("additional_payload", additionalPayloadToUpdate.trim());
            SimpleDateFormat dateFormatter = new SimpleDateFormat(
                    new Message(MessageKeys.CON_FORM_JWT_DATEFORMAT)
                    .toString());
            dateFormatter.setTimeZone(TimeZone.getTimeZone("GMT"));
            attrs.put("additionalPayloadUploadedOn", dateFormatter.format(Calendar
                    .getInstance(TimeZone.getTimeZone("GMT")).getTime()));
        }
    }
    
    
    /**
     * Processes the client certificate information to be stored in application
     * config.
     * 
     * @param app
     */
    private static void processClientCertificateInfo(Application app) {
        Attributes<String, Object> attrs = app.getAttributes();
        if (!attrs.getBoolean("clientCertAuthEnabled")) {
            attrs.remove("clientCertificate");
            attrs.remove("clientCertUploadedOn");
            attrs.remove("clientKeySpec");
        } else {
            String clientCertToUpdate = attrs
                    .getString("client_cert_to_update");
            String clientKeySpecToUpdate = attrs
                    .getString("clientKeySpec_to_update");

            if (!Util.isEmpty(clientCertToUpdate)) {
                attrs.put("clientCertificate", clientCertToUpdate.trim());
                SimpleDateFormat dateFormatter = new SimpleDateFormat(
                        new Message(MessageKeys.CON_FORM_CLIENT_CERT_DATEFORMAT)
                                .toString());
                dateFormatter.setTimeZone(TimeZone.getTimeZone("GMT"));
                attrs.put("clientCertUploadedOn", dateFormatter.format(Calendar
                        .getInstance(TimeZone.getTimeZone("GMT")).getTime()));
            }

            if (!Util.isEmpty(clientKeySpecToUpdate)) {
                attrs.put("clientKeySpec", clientKeySpecToUpdate.trim());
            }
        }
        attrs.remove("client_cert_to_update");
        attrs.remove("clientKeySpec_to_update");
    }

    /**
     * Checks if any secret attribute is missing in encrypted tag and adds
     * missing attributes to return updated value
     */
    private String updateEncryptedTagForSecrets(String encrypted ) {
     
        StringBuilder strBuilder = new StringBuilder(encrypted);
        
        if(encrypted.indexOf("clientSecret") == -1 ) {
            strBuilder.append(",clientSecret");
        }
        
        if(encrypted.indexOf("oauth_token_info") == -1 ) {
            strBuilder.append(",oauth_token_info");
        }
        
        if(encrypted.indexOf("clientCertificate") == -1 ) {
            strBuilder.append(",clientCertificate");
        }
        
        if(encrypted.indexOf("privateKeyPassword") == -1 ) {
            strBuilder.append(",privateKeyPassword");
        }
        
        if(encrypted.indexOf("private_key") == -1 ) {
            strBuilder.append(",private_key");
        }
        
        if(encrypted.indexOf("refresh_token") == -1 ) {
            strBuilder.append(",refresh_token");
        }
        
        if(encrypted.indexOf("exchangeUserPassword") == -1 ) {
            strBuilder.append(",exchangeUserPassword");
        }
        
        return strBuilder.toString();
    }
    
    /**
     *
     * @return
     */
    public boolean isTestSuccess() {
        return _testSuccess;
    }

    /**
     *
     * @return
     */
    public String getTestResult() {
        return _testResult;
    }

    private boolean validateDependencies() {
        boolean error = false; 

        DependencyValidator validator = new DependencyValidator();
        Set<Application> cyclicApps = validator.checkCycles(_object);

        if (!Util.isEmpty(cyclicApps)) {
        String cyclicString = Util.listToCsv(ObjectUtil.getObjectNames(cyclicApps));
            addMessage(new Message(Message.Type.Error, MessageKeys.APP_ERR_DEPENDENCIES_CYCLE_DETECTED, cyclicString), null);
            error = true;
        }

        return error;
    }

    /**
     * Validates that the any application that is depended upon by a field in the
     * Create provisioning policy is in the dependency list of the application.
     *
     * @return False if valid, true otherwise.
     */
    private boolean validateFieldDependencies() {
        // make sure that no fields in the create policy depend on an app that is not in the dependency list
        List<FieldDTO> dependentFields = new ArrayList<FieldDTO>();
        for (FormDTO formDto : Util.iterate(getFormEditor().getFormDTOs())) {
            // only worry about Create policy for now
            if (Form.Usage.Create.equals(formDto.getUsage())) {
                for (SectionDTO sectionDto : Util.iterate(formDto.getSections())) {
                    for (FieldDTO fieldDto : Util.iterate(sectionDto.getFieldDTOs())) {
                        if (!Util.isNullOrEmpty(fieldDto.getDependentAppName())) {
                            dependentFields.add(fieldDto);
                        }
                    }
                }
            }
        }

        // valid if none found
        if (0 == dependentFields.size()) {
            return false;
        }

        // enumerate application dependency names - form uses app ID instead of app name as value
        Map<String, String> dependentApps = new HashMap<>();
        for (Application app : Util.safeIterable(_object.getDependencies())) {
            dependentApps.put(app.getId(), app.getName());
        }

        for (FieldDTO field : dependentFields) {
            String appIdToFind = field.getDependentAppName();   // form uses ID instead of name
            if (!dependentApps.containsKey(appIdToFind)) {
                addMessage(new Message(Message.Type.Error,
                        MessageKeys.APP_ERR_FIELD_DEPENDENCY_MISSING,
                        field.getName(),
                        dependentApps.get(appIdToFind)));

                return true;
            }
        }

        return false;
    }
    
    private boolean validateSaveAction() {
        boolean error = false;
        if ( _object != null ) {
            // In application reconfigure mode check whether user has done with
            // both updating the provisioning policies and schema mapping
            boolean appReconfMode = isAppReconfMode();
            if ( appReconfMode ){
                boolean updatePPolicies = isProvPolicyUpdated();
                boolean isSavedMapping = isSchemaMappingSaved();
                String appType = _object.getType();
                if (!emptySchemaAppType.contains(appType)) {
                    if (updatePPolicies == false || (isSavedMapping == false) ) {
                        addMessage(new Message(Message.Type.Error, MessageKeys.APP_RECONF_ERR_APPSAVE_MSG), null);
                        error = true;
                    }
                }
            
            }

            String value = _object.getName();
            if ( value == null || value.trim().length() == 0 ) {
                addMessage(new Message(Message.Type.Error, MessageKeys.NAME_REQUIRED), null);
                error = true;
            }
            
            // check for safe name
            if (!WebUtil.isSafeValue(value)) {
                addMessage(new Message(Message.Type.Error, MessageKeys.NAME_UNSAFE), null);
                error = true;
            }

            if (Certification.IIQ_APPLICATION.equals(value)) {
                addMessage(new Message(Message.Type.Error, MessageKeys.ERR_RESERVED_APPLICATION_NAME, value), null);
                error = true;
            }
            
            if (_object.getId() == null) {
                try {
                    Application existingApp = getContext().getObjectByName(Application.class, value);
                    if (existingApp != null) {
                        addMessage(new Message(Message.Type.Error,
                                MessageKeys.ERR_DUPLICATE_APP_NAME, value), null);
                        error = true;
                    }
                } catch (GeneralException e) {
                    addMessage(new Message(Message.Type.Error,
                            MessageKeys.ERR_APP_SAVE_FAILED, e), null);
                    log.error("Unable to save application object", e);
                }
            }

            if ( _object.getOwner() == null ) {
                addMessage(new Message(Message.Type.Error, MessageKeys.OWNER_REQUIRED), null);
                error = true;
            }
            
            value = _object.getType();
            if ( value == null || value.trim().length() == 0 ) {
                addMessage(new Message(Message.Type.Error,
                        MessageKeys.ERR_APP_TYPE_REQUIRED), null);
                error = true;
            }
            
            // make sure that required application attributes have a value
            List<AttributeDefinition> appAttrs = null;
            boolean hasConfiguration = true;
            try {
                appAttrs = getAttributeConfig();
                hasConfiguration = isHasCredentialConfiguration();
            } catch ( GeneralException ex ) {
                // getAttributeConfig() will add error messages to the context
                // We could also possibly wind up here because of CredentialRetriever.hasConfiguration(),
                // but it's unlikely, and in the very worst case it would just mean that we skip validating
                // required fields, so no need to worry about that.
            }
            if (( appAttrs != null ) && (!hasConfiguration)) {
                for ( AttributeDefinition attrDef : appAttrs ) {
                    if ( attrDef != null && attrDef.isRequired() ) {
                        Object attrVal =
                            _object.getAttributeValue(attrDef.getName());
                        
                        if(LDAP_MULTI_GRP_IGNORE_ATT.contains(attrDef.getName()))
                        	continue;
                        if ((attrVal == null ||
                                attrVal.toString().length() == 0)) {
                            addMessage(new Message(Message.Type.Error,
                                    MessageKeys.ERR_APP_ATTR_VALUE_REQUIRED, attrDef.getName()), null);
                            error = true;
                        }
                    }
                }
            }  // if appAttrs != null
            
            error |= persistSchemaDTOs();
            error |=  validateDependencies();
            error |= validateFieldDependencies();
            error |= validateIQserviceConfiguration();
            
            // assign/deassign correlation keys for schema attributes
            List<Schema> schemas = _object.getSchemas();
            if ( schemas != null ) {
                // !! TODO: need to get this from System Configuration
                int max = 4;
                for (Schema schema : schemas) {
                    schema.initCorrelationKeys();
                    if (!schema.assignCorrelationKeys(max)) {
                        error = true;
                        addMessage(new Message(Message.Type.Error,
                                MessageKeys.ERR_SCHEMA_CORRELATION_KEY_CNT, schema.getObjectType(), Util.itoa(max)), null);
                    }
                }
            }
        }
        
        return error;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private boolean validateIQserviceConfiguration() {
        boolean error = false;
        List<String> appsToExcludeValidation = Arrays.asList(new String[] {
            APP_MICROSOFT_FOREFRONT_IDENTITY_MANAGER.trim().toLowerCase() });
        String appType = _object.getType().trim().toLowerCase();
        if (!appsToExcludeValidation.contains(appType)) {
            try {
                Map<String,Object> iqserviceConfigurationMap = getIQServiceInfo();
                if (!Util.isEmpty(iqserviceConfigurationMap)) {
                    boolean isTLSChecked = Util.otob(iqserviceConfigurationMap
                            .get(ATT_USE_TLS_FOR_IQSERVICE));
                    // validate IQService Configuration only if TLS is checked
                    // If TLS is checked, then Client Authentication details are
                    // mandatory!
                    if (isTLSChecked) {
                        List<String> iqServiceAttributesKeys = null;
                        if (appType.equals(
                                APP_WINDOWS_LOCAL.trim().toLowerCase())) {
                            iqServiceAttributesKeys = Arrays
                                    .asList(new String[] { ATT_IQSERVICE_USER,
                                        ATT_IQSERVICE_PASSWORD });
                        } else {
                            iqServiceAttributesKeys = Arrays
                                    .asList(new String[] { ATT_IQSERVICE_HOST,
                                        ATT_IQSERVICE_PORT, ATT_IQSERVICE_USER,
                                        ATT_IQSERVICE_PASSWORD });
                        }
                        boolean isInvalidConfiguration = false;
                        List missingAttributes = new ArrayList<String>();
                        Message message = new Message();
                        for (String attributeKey : iqServiceAttributesKeys) {
                            if (Util.isNullOrEmpty(
                                    (String) iqserviceConfigurationMap
                                            .get(attributeKey))) {
                                message.setKey(getIQServiceAttrMessageKey(
                                        attributeKey));
                                missingAttributes.add(
                                        message.getLocalizedMessage(getLocale(),
                                                null));
                                isInvalidConfiguration = true;
                            }
                        }
                        if (isInvalidConfiguration) {
                            StringBuilder attributesBuilder = new StringBuilder("");
                            for (int i = 0; i < missingAttributes.size(); i++) {
                                if (i != 0) {
                                    attributesBuilder.append(",").append(" ");
                                }
                                attributesBuilder
                                        .append(missingAttributes.get(i));
                            }
                            addMessage(
                                    new Message(Message.Type.Error,
                                                MessageKeys.ERR_INCOMPLETE_IQSERVICE_CONFIGURATION,
                                                attributesBuilder.toString()));
                            error = true;
                        }
                    }
                }
            } catch (GeneralException e) {
                e.printStackTrace();
            }
        }
        return error;
    }

    private String getIQServiceAttrMessageKey(String attributeKey) throws GeneralException{
        String messageKey = "";
        switch(attributeKey) {
        case ATT_IQSERVICE_HOST:
            messageKey = MessageKeys.IQ_SERVICE_HOST;
            break;
        case ATT_IQSERVICE_PORT:
            messageKey = MessageKeys.IQ_SERVICE_PORT;
            break;
        case ATT_IQSERVICE_USER:
            messageKey = MessageKeys.IQ_SERVICE_USER;
            break;
        case ATT_IQSERVICE_PASSWORD:
            messageKey = MessageKeys.IQ_SERVICE_PASSWORD;
            break;
            default:
                throw new GeneralException("Invalid IQService attribute received!");
        }
        return messageKey;
    }

    @Override
    public String saveAction() {
        boolean error;
        try {
            Application app = getObject();
            error = this.validateSaveAction();
            
            if ( _object != null ) {
                
                // special case for Unix applications.  If the file transport
                // is "local", then authn is not supported, so remove it from
                // the feature list (and correspondingly add it if it is not
                // there for other file transports).
                if ( UnixConnector.CONNECTOR_TYPE.equals(_object.getType()) ) {
                    
                    Application.Feature authFeature =
                                                 Application.Feature.AUTHENTICATE;
                    String transport =
                                 _object.getStringAttributeValue("filetransport");
                    if ( "local".equals(transport) ) {
                        _object.removeFeature(authFeature);
                    } else {
                        if ( ! _object.supportsFeature(authFeature) ) {
                            _object.addFeature(authFeature);
                        }
                    }
                }

                if(JDBCConnector.CONNECTOR_TYPE.equals(_object.getType()) || JDBCConnector.CONNECTOR_SQLLOADER_TYPE.equals(_object.getType())) {
                    saveJDBCMultiSuggestAttributes();
                }
                
              //For existing applications new secret attributes might not be present in encrypted
                //tag . Here we are checking if any attribute is missing and updating the encrypted tag
                if(AZURE_AS_IDP_APPS.contains(app.getType())) {
                    String encrypted = app.getAttributes().getString("encrypted");
                    if(Util.isNotNullOrEmpty(encrypted)) {
                       app.getAttributes().put("encrypted", updateEncryptedTagForSecrets(encrypted));
                    }
                    
                }

                //Save maintenance window. Validation should have already happend with #validateMaintenanceExpiration
                if (this.maintenanceEnabled) {
                    if (this.maintenanceExpiration != null) {
                        _object.setMaintenanceExpirationDate(this.maintenanceExpiration);
                    } else {
                        //Maintenance enabled, but no expiration set. Infinite
                        _object.setMaintenanceExpiration(-1);
                    }
                } else {
                    _object.setMaintenanceExpiration(0);
                }

                // Add warnings about remediation modifiable attributes that
                // aren't mapped as link attributes.
                warnAboutRemediationSelectableAttrs();

                // Save changes to the extensible attributes.
                AttributeEditBean editor = getAttributeEditor(false);
                if (editor != null)
                    editor.commit(_object);

                // Handle the Targets
                if (!error) {
                    saveTargetSource();
                }

                // Handle the Activity Data Sources
                if (!error) {
                    saveActivityDataSources();
                }

                if(!error) {
                    saveForms();
                }

                if ( !error ) {
                    LeafFilter managerFilter = getManagerCorrelationFilter();
                    if ( managerFilter != null ) {
                        if ( ( Util.getString(managerFilter.getProperty()) != null  ) &&
                             ( Util.getString((String)managerFilter.getValue()) != null  ) )  {
                            _object.setManagerCorrelationFilter(managerFilter);
                        }
                        else {
                            // clear the filter
                            _object.setManagerCorrelationFilter(null);
                        }
                    } else {
                        _object.setManagerCorrelationFilter(managerFilter);
                    }
                }

                if ( !error ) {
                    setCompositeSelector(_currentSelector);
                }
                
                //Added for AD UI
                if(!error && _object.getType()!= null) {
                	
                   
                    if(app!=null) {
                      
                       if(LDAP_MULTIGROUP_APPS.toLowerCase().contains("|"+_object.getType().toLowerCase()+"|")) {
                            //Removing attributes not required for multigrp applications
                           if(!isLdapMultigrp) {
                               app.setAttribute(CONFIG_LDAPAPPLICATIONVERSION, CONFIG_LDAP_APPLICATION_VERSION_VALUE);
                               
                               HashMap<String,Object> DeltaKey =(HashMap<String,Object>)app.getAttributeValue(CONFIG_LDAP_APPLICATION_DELTA_KEY);
                               if(!Util.isEmpty(DeltaKey)){
                                   
                                   String grpDeltaValue = (String)DeltaKey.get(CONFIG_LDAP_APPLICATION_OLD_GROUP_DELTA_KEY);
                                   if(Util.isNotNullOrEmpty(grpDeltaValue)){
                                       DeltaKey.put(CONFIG_LDAP_APPLICATION_NEW_GROUP_DELTA_KEY, grpDeltaValue);   
                                       DeltaKey.remove(CONFIG_LDAP_APPLICATION_OLD_GROUP_DELTA_KEY); 
                                       app.removeAttribute(CONFIG_LDAP_APPLICATION_DELTA_KEY);
                                       app.setAttribute(CONFIG_LDAP_APPLICATION_DELTA_KEY, DeltaKey);
                                   }
                               }
                               app.removeAttribute(AD_ATT_GROUP_SEARCH_DNS);
                               app.removeAttribute(ATT_GROUP_SEARCH_DN);
                               app.removeAttribute(ATT_GROUP_SEARCH_FILTER);
                               app.removeAttribute(ATT_ACCOUNT_SEARCH_DNS);
                               app.removeAttribute(ATT_ACCOUNT_SEARCH_FILTER);
                               app.removeAttribute(AD_ATT_ITERATE_SEARCH_FILTER);
                           }
                           //common attributes to be removed
                           app.removeAttribute(AD_ATT_SEARCH_DN);
                           app.removeAttribute(AD_ATT_SEARCH_SCOPE);
                           app.removeAttribute(AD_ATT_GROUP_SEARCH_SCOPE);
                           app.removeAttribute(AD_ATT_GROUP_ENTITLEMENT);
                            //Added for ldap multigroup applications to support new account and group serach scope
                           List<Schema>grpSchemas = app.getGroupSchemas();
                           
                           if(!Util.isEmpty(grpSchemas)) {
                               for (Schema s : grpSchemas) {
                                   app.removeAttribute(s.getObjectType()+CONFIG_SEARCH_DNS_SUFFIX);
                               }
                           }
                           saveGroupSearchScopeldap(app);
                           saveAccountSearchScopeldap(app);
                       } else if(_object.getType().equals(AD_APP_TYPE) ) {
                                saveGroupSearchScope(app); 
                                saveForest(app);
                                saveDomains(app); //save newly added domains
                                saveExchangeSettings(app);
                                saveAccountSearchScope(app); //save newly added account scopes//save newly added group scopes
                       } else if (isLdapRacfType()) {
                           app.removeAttribute(RACF_ATT_AUTHSEARCH_ATTR);
                           app.removeAttribute(RACF_ATT_PAGE_SIZE);
                        } else if (_object.getType().equals(SUCCESSFACTOR_APP_TYPE)) {
                            saveSuccessFactorAttributeMap(app); // save newly added schema attribute
                        }
                     }
                     super.getContext().decache(_object);
                }
                // REST WEBSERVICE
                if(!error && _object.getType()!= null) {
                    if(_object.getType().equals(WEBSERVICE_APP_TYPE) || _object.getType().equals(SCIM2_APP_TYPE)) {
                        if(app!=null) {
                            saveParams(app, false);
                        }
                        super.getContext().decache(_object);
                    }
                }

                if (!error && _object.getType() != null) {
                    // For client certificate authentication enabled
                    // connectors, process the client certificate and
                    // certificate key
                    if (app != null) {
                        processClientCertificateInfo(app);
                    }
                    super.getContext().decache(_object);
                }
                
                if (!error && _object.getType() != null) {
                    if (JWT_SUPPORTED_APPS.contains(_object.getType())) {
                        // For JWT enabled connectors, process the private key
                        // file...
                        if(app!=null) {
                            processPrivateKeyInfo(app);
                            if(_object.getType().equalsIgnoreCase(SCIM2_APP_TYPE)) {
                                processAdditionalPayloadInfo(app);
                            }
                        }
                        super.getContext().decache(_object);
                    }
                }
                
                if (_accountCorrelationBean != null) {
                    _accountCorrelationBean.setDirty(false);
                }

            } else {
                error = true;
            } // if _object != null
        } catch (GeneralException e) {
            error = true;
            log.error("The application editor could not get the application.", e);
            addMessage(new Message(Message.Type.Error, MessageKeys.ERR_APP_SAVE_FAILED, e), null);
        }

        if ( error ) {
            return "";
        } else {
            String action = "";
            try {
                // Prune out null values from the map
                pruneConfig(_object);
                _object.setDescriptions(_descriptionData.getDescriptionMap());
                action = super.saveAction();
                setObjectId(_object.getId());
                
                /** Save the localized descriptions **/
                Describer describer = new Describer((Describable)_object);
                SailPointContext ctx = getContext();
                describer.saveLocalizedAttributes(ctx);
                ctx.commitTransaction();
                
                if ( _targetSourceBean != null ) _targetSourceBean.reset();
                if ( _accountCorrelationBean != null ) _accountCorrelationBean.reset();
            } catch ( GeneralException ex ) {
                 addMessage(new Message(Message.Type.Error,
                         MessageKeys.ERR_APP_SAVE_FAILED, ex), null);
            }
            return action;
        }
    }

    /**
     * This function save the additional schema attribute map in application.
     * 
     * @param app
     *            SuccessFactors application.
     * @throws GeneralException
     */
    @SuppressWarnings("unchecked")
    private void saveSuccessFactorAttributeMap(Application app) throws GeneralException {
        ManageSFSchemaBean manageSuccessFactorBean = new ManageSFSchemaBean();
        Map<String, String> beans = manageSuccessFactorBean.getschemaExtMap();
        Attributes<String, Object> attrs = app.getAttributes();
        if (beans != null && !(beans.isEmpty())) {
            Map map = attrs.getMap();
            map.put(ManageSFSchemaBean.ATT_SF_SCHEMA_SETTINGS, beans);
            attrs.setMap(map);
        } else {
            Map map = attrs.getMap();
            map.remove(ManageSFSchemaBean.ATT_SF_SCHEMA_SETTINGS);
            attrs.setMap(map);
        }
    }
    
    /**
     * Check the schemas on this application for attributes that are marked to
     * be selectable for remediation but do not have searchable link attributes,
     * and add warnings to the session for any such attributes.
     */
    private void warnAboutRemediationSelectableAttrs() {

        List<Message> msgs = new ArrayList<Message>();
        if (null != _object) {
            ObjectConfig linkConfig = Link.getObjectConfig();

            if (null != _object.getSchemas()) {
                for (Schema schema : _object.getSchemas()) {
                    if (null != schema.getAttributes()) {
                        for (AttributeDefinition attr : schema.getAttributes()) {
                            if (UserInterfaceInputType.Select.equals(attr.getRemediationModificationType())) {
                                ObjectAttribute oa =
                                    linkConfig.getObjectAttributeWithSource(_object, attr.getName());
                                if ((null == oa) || (!oa.isSearchable() && !oa.isMulti())) {
                                    msgs.add(new Message(Message.Type.Warn,
                                                      MessageKeys.WARN_NO_LINK_ATTRIBUTE_FOR_REMED_ATTR,
                                                      attr.getDisplayableName()));
                                }
                            }
                        }
                    }
                }
            }
        }

        if (!msgs.isEmpty()) {
            super.getSessionScope().put(APPLICATION_MESSAGES, msgs);
        }
    }

    @Override
    public String cancelAction() {
        if ( _targetSourceBean != null )
            _targetSourceBean.reset();
        if ( _accountCorrelationBean != null ) {
            _accountCorrelationBean.setDirty(false);
            _accountCorrelationBean.reset();
        }
        ManageDomainBean.reset();
        AccountSearchScopeBean.reset();
        GroupSearchScopeBean.reset();
        ContactSearchScopeBean.reset();
        GMSASearchScopeBean.reset();
        return super.cancelAction();
    }

    @Override
    public void clearHttpSession() {
        getSessionScope().remove(BaseListBean.ATT_SELECTED_ID);
        getSessionScope().remove(NEW_TARGET_SOURCE);
        getSessionScope().remove(ACCOUNT_SCHEMA_NEW);
        getSessionScope().remove(GROUP_SCHEMA_NEW);
        getSessionScope().remove(SCHEMA_LOADED);
        getSessionScope().remove(CURRENT_TAB);
        getSessionScope().remove(SCHEMA_DTOS);
        getSessionScope().remove(SCHEMA_ATTRIBUTE_EDITOR);
        getSessionScope().remove(CURRENT_SELECTOR);
        getSessionScope().remove(APPLICATION_TEMPLATE_EDITOR);
        getSessionScope().remove(AD_ATT_DOMAIN_SETTINGS);
        getSessionScope().remove(AD_ATT_FOREST_SETTINGS);
        getSessionScope().remove(ManageSFSchemaBean.ATT_SF_SCHEMA_SETTINGS);
        getSessionScope().remove(ATT_EXCHANGE_SETTINGS);
        getSessionScope().remove(AD_ATT_SEARCH_DNS);
        getSessionScope().remove(AD_ATT_ACCOUNT_SEARCH_DNS);
        getSessionScope().remove(AD_ATT_CONTACT_SEARCH_DNS);
        getSessionScope().remove(AD_ATT_GMSA_SEARCH_DNS);
        getSessionScope().remove(AD_ATT_GROUP_SEARCH_DNS);
        getSessionScope().remove(ManageRestBean.ATT_INNER_BEAN_PARAMETERS);
        getSessionScope().remove(ManageRestBean.ATT_CONNECTION_PARAMETERS);
        getSessionScope().remove(MAINT_ENABLED_STATE);
        getSessionScope().remove(MAINT_EXPIRATION_STATE);
        ManageDomainBean.clearApplicationObject();
        ManageSFSchemaBean.clearApplicationObject();
        AccountSearchScopeBean.clearApplicationObject();
        GroupSearchScopeBean.clearApplicationObject();
        ContactSearchScopeBean.clearApplicationObject();
        GMSASearchScopeBean.clearApplicationObject();
        ManageRestBean.clearApplicationObject();
        super.clearHttpSession();
        clearEditState();
    }

    public String getSelectedDataSourceId() {
        return _selectedDataSourceId;
    }

    public void setSelectedDataSourceId(String selectedDataSourceId) {
        _selectedDataSourceId = selectedDataSourceId;
        getSessionScope().put(SELECTED_DS_ID, selectedDataSourceId);
    }

    public ActivityDataSourceObjectBean getSelectedDataSourceBean() {
        ActivityDataSourceObjectBean activityDS = null;

        activityDS = (ActivityDataSourceObjectBean) getEditState(EDITED_ACTIVITY_DS);

        if (activityDS == null) {
            addEditedActivityDataSourceToSession();
        }

        return activityDS;
    }

    public String createDataSource() {
        ActivityDataSourceObjectBean activityDS = new ActivityDataSourceObjectBean();
        addEditState(EDITED_ACTIVITY_DS, activityDS);
        getSessionScope().put(EDITED_OBJ_ID, getObjectId());
        return "createDataSource";
    }

    public String editDataSource() {
        addEditedActivityDataSourceToSession();
        getSessionScope().put(EDITED_OBJ_ID, getObjectId());
        return "editDataSource";
    }

    @SuppressWarnings("unchecked")
    public String deleteDataSource() {
        if (_selectedDataSourceId != null) {
            List<String> deletedDataSourceIds = (List<String>)getEditState(DELETED_ACTIVITY_DS_LIST);
            if (deletedDataSourceIds == null) {
                deletedDataSourceIds = new ArrayList<String>();
                addEditState(DELETED_ACTIVITY_DS_LIST, deletedDataSourceIds);
            }

            // First clear any edits that may have been made on this data source
            Map<String, ActivityDataSourceDTO> editedDataSources = (Map<String, ActivityDataSourceDTO>) getEditState(EDITED_ACTIVITY_DS_MAP);
            if (editedDataSources != null) {
                editedDataSources.remove(_selectedDataSourceId);
            }

            // Keep track of persisted data sources that are deleted so we can remove them when committing.
            // Note that "temp" data sources are not persisted yet, so by clearing them in the block above
            // we have already prevented them from ever being persisted anyways
            if (!_selectedDataSourceId.startsWith("temp")) {
                deletedDataSourceIds.add(_selectedDataSourceId);
            }
        }

        return "deletedDataSource";
    }

    //Handle multiple target sources
    
    public String getSelectedTargetSourceId() {
        return _selectedTargetSourceId;
    }

    public void setSelectedTargetSourceId(String selectedTargetSourceId) {
        _selectedTargetSourceId = selectedTargetSourceId;
        getSessionScope().put(SELECTED_TARGET_DS_ID, selectedTargetSourceId);
    }
    
    public TargetSourceBean getSelectedTargetSourceBean() {
        TargetSourceBean targetDS = null;

        targetDS = (TargetSourceBean) getEditState(EDITED_TARGET_DS);

        if (targetDS == null) {
            addEditedtargetSourceToSession();
        }
        return targetDS;
    }

    public String createTargetSource() throws GeneralException {
        TargetSourceBean targetDS = new TargetSourceBean();
        targetDS.setApplicationObject(getObject());
        addEditState(EDITED_TARGET_DS, targetDS);
        getSessionScope().put(EDITED_OBJ_ID, getObjectId());
        return "createTargetSource";
    }

    public String editTargetSource() {
        addEditedtargetSourceToSession();
        getSessionScope().put(EDITED_OBJ_ID, getObjectId());
        return "editTargetSource";
    }

    //Associate a TargetSource with the application
    public String addTargetSource() throws GeneralException {

        if (_selectedTargetSourceId != null) {
            Map<String, TargetSourceDTO> editedDataSources = (Map<String, TargetSourceDTO>) getEditState(EDITED_TARGET_DS_MAP);
            if (editedDataSources == null) {
                editedDataSources = new HashMap<String, TargetSourceDTO>();
                addEditState(EDITED_TARGET_DS_MAP, editedDataSources);
            }
            //Look up TargetSource with ID/Name
            TargetSource ts = getContext().getObjectById(TargetSource.class, _selectedTargetSourceId);
            if (ts != null) {
                //Create a TargetSourceBean from the TargetSource
                TargetSourceDTO tsBean = new TargetSourceDTO(ts);
                //Add the TargetSourceBean to the EDITED_TARGET_DS_MAP
                editedDataSources.put(tsBean.getId(), tsBean);
                //If it was previously deleted, remove it from the deleted list
                List<String> deletedDataSourceIds = (List<String>)getEditState(ApplicationObjectBean.DELETED_TARGET_DS_LIST);
                if (!Util.isEmpty(deletedDataSourceIds) && deletedDataSourceIds.contains(ts.getId())) {
                    deletedDataSourceIds.remove(ts.getId());
                }
            }

        }

        return "addTargetSource";
    }



    @SuppressWarnings("unchecked")
    public String deleteTargetSource() {
        if (_selectedTargetSourceId != null) {
            List<String> deletedDataSourceIds = (List<String>)getEditState(DELETED_TARGET_DS_LIST);
            if (deletedDataSourceIds == null) {
                deletedDataSourceIds = new ArrayList<String>();
                addEditState(DELETED_TARGET_DS_LIST, deletedDataSourceIds);
            }

            // First clear any edits that may have been made on this data source
            Map<String, TargetSourceDTO> editedDataSources = (Map<String, TargetSourceDTO>) getEditState(EDITED_TARGET_DS_MAP);
            if (editedDataSources != null) {
                editedDataSources.remove(_selectedTargetSourceId);
            }

            // Keep track of persisted data sources that are deleted so we can remove them when committing.
            // Note that "temp" data sources are not persisted yet, so by clearing them in the block above
            // we have already prevented them from ever being persisted anyways
            if (!_selectedTargetSourceId.startsWith("temp")) {
                deletedDataSourceIds.add(_selectedTargetSourceId);
            }
        }

        return "deleteTargetSource";
    }
    
///////////////////////////////////////////////////////////////////////////
    //
    // Target Data Sources
    //
    ///////////////////////////////////////////////////////////////////////////

    private void addEditedtargetSourceToSession() {
        if ( Util.getString(_selectedTargetSourceId) != null ) {
            try {
                boolean found = false;
                
                // first check the edit map so we can display the most up to date changes
                Map<String, TargetSourceDTO> editedTargetSources = (Map<String, TargetSourceDTO>)getEditState(EDITED_TARGET_DS_MAP);
                if ( editedTargetSources != null ) {
                    TargetSourceDTO dto = editedTargetSources.get(_selectedTargetSourceId);
                    if ( dto != null ) {
                        TargetSource dataSourceToPopulate = new TargetSource();
                        if (null != dto.getId()) {
                            // if we have an id then try to load it, probably will never be null but guard
                            // against NPE anyway
                            dataSourceToPopulate = getContext().getObjectById(TargetSource.class, dto.getId());
                            if (null == dataSourceToPopulate) {
                                dataSourceToPopulate = new TargetSource();
                            }
                        }
                        
                        dto.updateTargetSource(getContext(), dataSourceToPopulate);
                        TargetSourceBean targetDS = new TargetSourceBean(dataSourceToPopulate);
                        //try to set the Application object to TargetSourceBean
                        targetDS.setApplicationObject(getObject());
                        addEditState(EDITED_TARGET_DS, targetDS);
                        found = true;
                    }
                }

                // if we have an id and it's not in the edit map, check the application
                if ( !found ) {
                    List<TargetSource> availableSources = getObject().getTargetSources();
                    if (availableSources != null) {
                        for (TargetSource ads : availableSources) {
                            if (ads.getId().equals(_selectedTargetSourceId)) {
                                TargetSource source = getContext().getObjectById(TargetSource.class, _selectedTargetSourceId);
                                TargetSourceBean targetDS = new TargetSourceBean(source);
                                //try to set the Application object to TargetSourceBean
                                targetDS.setApplicationObject(getObject());
                                addEditState(EDITED_TARGET_DS, targetDS);
                                break;
                            }
                        }
                    }
                }
            } catch (GeneralException e) {
                Message errMsg = new Message(Message.Type.Error,
                        MessageKeys.ERR_ACTIVITY_DATA_SRC_CANNOT_BE_EDITED);
                log.error(errMsg.getMessage(), e);
                addMessage(errMsg, null);
            }
        } else {
            TargetSourceBean targetDS = new TargetSourceBean();
            addEditState(EDITED_TARGET_DS, targetDS);
        }

    }

    public String getAvailableTargetSourcesJSON() throws GeneralException {

        Map<String, List> availableSources = new HashMap<String, List>();
        //Get all defined TargetSources
        //TODO: Only fetch the TargetSources with collectors available for this application (Need to figure out what is available for SecurityIQ)
        List<TargetSource> sources = getContext().getObjects(TargetSource.class);


        if (!Util.isEmpty(sources)) {

            for (TargetSource ts: sources) {
                boolean available = true;

                //Get the TS in the EditedDS
                Map<String, TargetSourceDTO> editedDto = (Map<String, TargetSourceDTO>)getEditState(EDITED_TARGET_DS_MAP);
                if (editedDto != null) {
                    for (TargetSourceDTO dto : Util.safeIterable(editedDto.values())) {
                        if (ts.getId().equalsIgnoreCase(dto.getId())) {
                            available = false;
                            break;
                        }
                    }
                }

                if (available) {
                    //Only need the Id/Name
                    List<String> src = new ArrayList<String>();
                    src.add(ts.getId());
                    src.add(ts.getName());
                    availableSources.put(ts.getId(), src);
                }
            }

            //Remove any target sources that are currently on the App
            List<TargetSource> currentSources = getObject().getTargetSources();
            List<String> deletedDataSourceIds = (List<String>)getEditState(ApplicationObjectBean.DELETED_TARGET_DS_LIST);
            for(TargetSource currentSource : Util.safeIterable(currentSources)) {
                //If it's in the deleted list, allow selecting
                if (Util.isEmpty(deletedDataSourceIds) || !Util.nullSafeContains(deletedDataSourceIds, currentSource.getId())) {
                    availableSources.remove(currentSource.getId());
                }
            }
        }

        return JsonHelper.toJson(availableSources.values());
    }

    
    // Password Policy stuff
    public PasswordPolicyHolderBean getSelectedPasswordPolicyHolderBean() {
        PasswordPolicyHolderBean ppBean = null;

        ppBean = (PasswordPolicyHolderBean) getEditState(EDITED_PASSWORD_POLICY);

        return ppBean;
    }

    public String createPasswordPolicy() {
        createPasswordPolicyInternal();
        return "createPasswordPolicy";
    }


    public String selectPasswordPolicy() {
        createPasswordPolicyInternal();
        return "selectPasswordPolicy";
    }

    /**
     * Create new PasswordPolicyHolder bean
     * This will generate a new PasswordPolicyHolder object.
     *
     */
    private void createPasswordPolicyInternal() {
        Application app = null;
        try {
            app = getObject();
        }
        catch (GeneralException e) {
            if (log.isErrorEnabled())
                log.error(e.getMessage(), e);
        }

        PasswordPolicyHolderBean ppBean = new PasswordPolicyHolderBean(app.getName());

        addEditState(EDITED_PASSWORD_POLICY, ppBean);

        // pj: don't really understand what this is for
        getSessionScope().put(EDITED_OBJ_ID, getObjectId());
    }

    public String editPasswordPolicy() {
        return viewEditPasswordPolicy(false);
    }

    public String viewPasswordPolicy() {
        return viewEditPasswordPolicy(true);
    }

    private String viewEditPasswordPolicy(boolean readonly) {
        boolean foundSelected = false;

        for (PasswordPolicyHolderBean pb : _passwordPolicyList) {
            if (pb.isSelected()) {
                foundSelected = true;
                pb.setReadOnly(readonly);
                addEditState(EDITED_PASSWORD_POLICY, pb);
                break;
            }
        }

        if (!foundSelected) {
            // Add error message for selecting first
            addMessage(new Message(Message.Type.Error, MessageKeys.ERR_NO_PASSWORD_POLICY_SELECTED), null);
            return "";
        }

        getSessionScope().put(EDITED_OBJ_ID, getObjectId());

        if (readonly) {
            return "viewPasswordPolicy";
        }

        return "editPasswordPolicy";
    }

    public String deletePasswordPolicy() throws GeneralException {

        boolean foundSelected = false;
        Application app = null;
        try {
            app = getObject();
        }
        catch (GeneralException e) {
            if (log.isErrorEnabled())
                log.error(e.getMessage(), e);
        }
        for (Iterator<PasswordPolicyHolderBean> iter = _passwordPolicyList.iterator(); iter.hasNext();) {
            PasswordPolicyHolderBean pb = iter.next();
            if (pb.isSelected()) {
                //audit policy change
                PasswordPolicy policy = getContext().getObjectByName(PasswordPolicy.class, pb.getPolicyName());
                auditPasswordPolicyChange(policy, null);
                
                PasswordPolicyHolder holder = app.removePasswordPolicy(pb.getPolicyName());
                getContext().removeObject(holder);
                iter.remove();
                foundSelected = true;
            }
        }

        if (!foundSelected) {
            // Add error message for selecting first
            addMessage(new Message(Message.Type.Error, MessageKeys.ERR_NO_PASSWORD_POLICY_SELECTED), null);
            return "";
        }
        // reset the select list so that we recalculate it
        _passwordPolicySelectList = null;

        getContext().saveObject(app);
        getContext().commitTransaction();

        return "deletePasswordPolicy";
    }

    /**
     * Method called by the app pages to determine if the "Include Permissions"
     * flag should be disabled or enabled.
     */
    public boolean getSupportsPermissions() {
        return checkFeature(Feature.DIRECT_PERMISSIONS);
    }

    /**
     * Method called by the app pages to determine if the "Include Permissions"
     * flag should be disabled or enabled.
     */
    public boolean getSupportsUnstructuredTargets() {
        return checkFeature(Feature.UNSTRUCTURED_TARGETS);
    }

    public boolean getSupportsSchemaDiscovery() {
        return checkFeature(Feature.DISCOVER_SCHEMA);
    }

    public boolean getSupportsNoAggregation() {
        return checkFeature(Feature.NO_AGGREGATION);
    }

    public boolean getSupportsComposite() {
        return checkFeature(Feature.COMPOSITE);
    }

    public boolean getSupportsEnable() {
        return checkFeature(Feature.ENABLE);
    }

    public boolean getSupportsUnlock() {
        return checkFeature(Feature.UNLOCK);
    }

    private boolean checkFeature(Feature feature) {
        boolean supportsFeature = false;
        try {
           Application app = getObject();
           if ( app != null )
               supportsFeature = app.supportsFeature(feature);
        } catch (GeneralException e) {
            addMessage(new Message(Message.Type.Error, MessageKeys.ERR_DS_NOT_AVAILABLE), null);
            log.error("Error checking feature :", e);
        }
        return supportsFeature;
    }

    private void restore() {
        NavigationHistory.getInstance().restorePageState(this);
        // Call getObject() to trigger a force load as needed
        try {
            getObject();
        } catch (GeneralException e) {
            log.error("Failed to properly restore the ApplicationObjectBean. ", e);
        }

        String currentTab = getEditState(CURRENT_TAB) != null ?
                getEditState(CURRENT_TAB).toString() : null;

        if (currentTab == null) {
            setCurrentTab(null);
            addEditState(CURRENT_TAB, null);
        } else {
            _currentTab = currentTab;
        }

        Boolean accountSchemaState = (Boolean) getSessionScope().get(ACCOUNT_SCHEMA_NEW);
        isAccountSchemaNew = accountSchemaState == null ? false : accountSchemaState.booleanValue();

        Map<String, Boolean> groupSchemaState = (Map<String, Boolean>) getSessionScope().get(GROUP_SCHEMA_NEW);
        groupSchemaNew = groupSchemaState == null ? new HashMap<String, Boolean>() : groupSchemaState;
        getSessionScope().put(GROUP_SCHEMA_NEW, groupSchemaNew);

        Boolean schemaLoaded = (Boolean)getEditState(SCHEMA_LOADED);
        _schemaLoaded = (schemaLoaded == null) ? false : schemaLoaded.booleanValue();

        Boolean logicalDirty = (Boolean)getEditState("logicalDirty");
        _logicalConfigDirty = Util.otob(logicalDirty);

        _schemaDTOs = (List<SchemaDTO>)getEditState(SCHEMA_DTOS);
        if (_schemaDTOs == null || !_schemaLoaded) {
            try {
                _schemaDTOs = buildSchemaDTOs();
            } catch (GeneralException e) {
                log.error("The application editor failed to initialize the schemas for application with ID " + getObjectId(), e);
                _schemaDTOs = new ArrayList<SchemaDTO>();
            }
            addEditState(SCHEMA_DTOS, _schemaDTOs);
        }

        _attrDefEditBean = (AttributeDefinitionEditBean) getEditState(SCHEMA_ATTRIBUTE_EDITOR);
        if (_attrDefEditBean == null || !_schemaLoaded) {
            buildSchemaAttributeEditBean();
        }

        _formEditBean = (FormEditBean) getEditState(APPLICATION_TEMPLATE_EDITOR);
        if(_formEditBean == null) {
            buildFormEditBean();
        }


        
        localizer = new Localizer(getContext(), getObjectId());
        /** Load descriptions **/
        if(_object!=null) {
            try {
                _descriptionData = new DescriptionData(_object.getDescriptions(), getDefaultLanguage());
            } catch (GeneralException ge) {
                log.error("Could not load descriptions.", ge);
            }
        }

        //Load maintenance window
        if (getEditState(MAINT_ENABLED_STATE) != null) {
            //Sigh, I hate this dumn editState, but am currently unable to find a workaround. -rap
            //Without the editstate, AJAX calls are overriding the values
            this.maintenanceEnabled = (Boolean)getEditState(MAINT_ENABLED_STATE);
            this.maintenanceExpiration = (Date)getEditState(MAINT_EXPIRATION_STATE);
        } else {
            if (_object != null) {
                if (_object.getMaintenanceExpiration() != 0) {
                    this.maintenanceEnabled = true;
                }

                if (_object.getMaintenanceExpiration() > 0) {
                    if (new Date(_object.getMaintenanceExpiration()).before(new Date())) {
                        //In the past, clear it out
                        if (log.isInfoEnabled()) {
                            log.info("Application[" + _object.getName() + "] maintenance expiration in the past, clearing value");
                        }
                        _object.setMaintenanceExpiration(0);
                        //Set maintenanceEnabled false
                        this.maintenanceEnabled = false;
                    } else {
                        this.maintenanceExpiration = new Date(_object.getMaintenanceExpiration());
                    }
                }
            }
        }

        IdentitySelectorDTO s = (IdentitySelectorDTO)getEditState(CURRENT_SELECTOR);
        this.setCurrentSelector(s);

        if (_passwordPolicyList == null) {
            try {
                initPasswordPolicyList();
            }
            catch (GeneralException e) {
                log.error("Failed to initialize the password policy list for application with ID " + getObjectId(), e);
            }
        }
    }

    public Application getReconfigurableApp(){
        return (Application)getEditState(RECONFIG_APPLICATION);
    }

    public void setReconfigurableApp( Application reconfigurableApp ) {
        addEditState( RECONFIG_APPLICATION, reconfigurableApp );
    }

    public void initReconfigurableApp( Application reconfigurableApp ) {
        createListOfAttributes( reconfigurableApp );
        initApplicationReconfigObjectList();
        setReconfigurableApp( reconfigurableApp );
    }

    public boolean isProvPolicyUpdated() {
        return Util.otob( (Boolean) getEditState(IS_PROV_POLICY_UPDATED) );
    }

    public void setIsProvPolicyUpdated( boolean isProvPolicyUpdated ) {
        addEditState( IS_PROV_POLICY_UPDATED, isProvPolicyUpdated );
    }

    /**
     * Schemas are keyed by their object type.  There are currently
     * two supported types which are account and group.  Technically
     * this can be anything but guard here against duplicates.
     * Duplicates can occur if the Template defined in the registry
     * or the application has been incorrectly defined.
     */
    private List<SchemaDTO> buildSchemaDTOs() throws GeneralException {
        List<SchemaDTO> schemaDTOs = new ArrayList<SchemaDTO>();
        List<Schema> schemas = getObject().getSchemas();
        if ( Util.size(schemas) > 0 ) {
            Set<String> types = new HashSet<String>();
            for (Schema schema : schemas) {
                schema.initCorrelationKeys();
                // add only each type once..
                String objectType = schema.getObjectType();
                if ( objectType != null ) {
                    if ( !types.contains(objectType) ) {
                        types.add(objectType);
                        schemaDTOs.add(new SchemaDTO(schema, this));
                    } else {
                        addMessage(new Message(Message.Type.Error, "Found duplicate schema type of ["+objectType+"] on application type ["+getObject().getTemplateApplication() +"]"), null);
                    }
                }
            }
        }
        return schemaDTOs;
    }


    @Override
    protected void initObjectId() {

        //Application Id is now placed on the request parameters via SailpointNavigationHandler
        String appId = getRequestParameter("appId");
        String oldAppId = (String)getSessionScope().get(ApplicationListBean.ATT_OBJECT_ID);
        _appLoadDirty = false;
        if (appId != null) {
            getSessionScope().put(ApplicationListBean.ATT_OBJECT_ID, appId);
        } else {
            // list page will set this initially, thereafter we keep it
            // refreshed as we transition among our pages
            appId = (String)getSessionScope().get(ApplicationListBean.ATT_OBJECT_ID);
            if (appId == null) {
                appId = (String) getSessionScope().get(BaseListBean.ATT_SELECTED_ID);
            }
        }

        if (appId != null) {
            if (oldAppId != null && !Util.nullSafeEq(oldAppId, appId)) {
                _appLoadDirty = true;
                //Do what the user did not.
                cancelAction();
                getSessionScope().put(ApplicationListBean.ATT_OBJECT_ID, appId);
            }
            setObjectId(appId);
        } else {
            //Shouldn't ever get here, but just in case 
            super.initObjectId();            
        }
    }

    /**
     * Restores this bean to a pristine state
     */
    protected void reset() {
        setObjectId(null);
        setObject(null);
        //TODO:
        _attrConfig = null;
        _selectedDataSourceId = null;
        _selectedTargetSourceId = null;
        _testSuccess = false;
        _testResult = null;
        _targetSourceBean = null;
        _accountCorrelationBean = null;
        setCurrentSelector(null);
        setCurrentTier(null);
    }

    /**
     * Go through the configuration attributes and prune out any
     * values that are null. This will reduce the size of the application
     * xml and make it easier to read.
     *
     * djs NOTE: we don't want to prune custom attributes, so
     * if we find a key, but not attribute definition then
     * leave it as is...
     *
     */
    private void pruneConfig(Application app)
        throws GeneralException  {

        Attributes<String,Object> orig = app.getAttributes();
        if ( orig != null ) {
            List<AttributeDefinition> defs = getAttributeConfig();
            // make a copy so we can modify the orig as we iterate
            Attributes<String,Object> configCopy =
                new Attributes<String,Object>(orig);
            Iterator<String> configKeys = configCopy.keySet().iterator();
            while ( configKeys.hasNext() ) {
                String key = configKeys.next();
                if ( key != null )  {
                    Object value = orig.get(key);
                    if ( value == null) {
                        orig.remove(key);
                        continue;
                    }
                    AttributeDefinition def = getDefinition(defs, key);
                    if ( def != null ) {
                        boolean multi = def.isMulti();
                        if ( multi ) {
                            List list = orig.getList(key);
                            // Also filter empty lists
                            if ( ( list != null ) && ( list.size() == 0 ) ) {
                                orig.remove(key);
                            }
                         } else {
                             // djs: previously were removing false Boolean
                             // values which must be maintained for values
                             // that default to true

                             // filter null string values
                             String strValue = orig.getString(key);
                             if ( Util.getString(strValue) == null )  {
                                 orig.remove(key);
                             }
                         }

                    } // else leave em alone
                }
            }

            Attributes<String,Object> newAttributes = new Attributes<String, Object>();
            newAttributes.putAll(orig);
            app.setAttributes(newAttributes);
        }
    }

    /*
     * Return the attributdefinition for the named attribute.  If not
     * found return null.
     */
    private static AttributeDefinition
        getDefinition(List<AttributeDefinition> defs, String targetAttrName) {

         AttributeDefinition definition = null;
         if (defs != null) {
             for ( AttributeDefinition def : defs ) {
                 String attrName = def.getName();
                 if ( targetAttrName.compareTo(attrName) == 0 ) {
                     definition = def;
                     break;
                 }
             }
         }
         return definition;

    }

    /**
     * If the attribute map is null (like it will be for stub applications)
     * initialize the app so values can be stored from the ui.
     */
    public Attributes<String,Object> getAttributes() throws GeneralException {
        Attributes<String,Object> attrs = null;
        Application app = getObject();
        if ( app != null ) {
            attrs = app.getAttributes();
            if ( attrs == null ) {
                attrs = new Attributes<String,Object>();
                app.setAttributes(attrs);
            }
        }
        return attrs;
    }

    public void setAttributes(Attributes<String,Object> attrs)
        throws GeneralException {

        Application app = getObject();
        if ( app != null ) {
            if ( ( attrs == null ) || ( attrs.size() == 0 ) ) {
                app.setAttributes(null);
            } else {
                app.setAttributes(attrs);
            }
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    //
    // Unstructured Targets
    //
    ///////////////////////////////////////////////////////////////////////////

    @SuppressWarnings("unchecked")
    private void saveTargetSource() throws GeneralException {
        Application app = getObject();

        // Remove the deleted stuff
        List<String> deletedDataSourceIds = (List<String>)getEditState(DELETED_TARGET_DS_LIST);
        if (deletedDataSourceIds != null && !deletedDataSourceIds.isEmpty()) {
            for (String deletedDataSourceId : deletedDataSourceIds) {
                TargetSource deletedDataSource = getContext().getObjectById(TargetSource.class, deletedDataSourceId);
                app.remove(deletedDataSource);
                //Don't delete the TargetSource if other Apps reference it, simply remove it from the App.
                if (shouldDeleteTargetSource(deletedDataSource)) {
                    getContext().removeObject(deletedDataSource);
                }
            }
        }

        // Update the existing stuff and add the new stuff
        Map<String, TargetSourceDTO> editedTargetSources =
            (Map<String, TargetSourceDTO>)getEditState(EDITED_TARGET_DS_MAP);
        if (editedTargetSources != null && !editedTargetSources.isEmpty()) {
            Collection<TargetSourceDTO> dtos = editedTargetSources.values();
            for (TargetSourceDTO dto : dtos) {
                String dtoName = dto.getName();
                String dtoId = dto.getId();
                TargetSource dataSourceToPopulate = null;
                // First try by id which will hit most cases, but
                // try by name just incase the datasource hasn't been
                // persisted yet
                if ( dtoId != null )
                    dataSourceToPopulate = app.getTargetSourceById(dtoId);
                if ( dataSourceToPopulate == null )
                    dataSourceToPopulate = app.getTargetSource(dtoName);

                // make a new one and assimilate the info from the dto
                if (dataSourceToPopulate == null) {
                    //Try to find in DB
                    dataSourceToPopulate = dto.getId() != null ? getContext().getObjectById(TargetSource.class, dto.getId())
                            : getContext().getObjectByName(TargetSource.class, dto.getName());
                    if (dataSourceToPopulate == null) {
                        dataSourceToPopulate = new TargetSource();
                    }
                    app.add(dataSourceToPopulate);
                }
                dto.updateTargetSource(getContext(), dataSourceToPopulate);
            }
        }
    }

    /**
     * If a TargetSource is referenced by other Applications, we should not delete the TargetSource, but rather
     * remove it from the given Application.
     * @param ts
     * @return true if the TargetSource is not referenced by any other Applications
     * @throws GeneralException
     */
    private boolean shouldDeleteTargetSource(TargetSource ts) throws GeneralException {
        QueryOptions ops = new QueryOptions();
        ops.add(Filter.eq("targetSources.id", ts.getId()));
        ops.add(Filter.ne("id", this.getObject().getId()));

        int referenceApps = getContext().countObjects(Application.class, ops);
        return referenceApps == 0;
    }

    public void setTargetSourceBean(TargetSourceBean bean) {
        _targetSourceBean = bean;
    }

    public TargetSourceBean getTargetSourceBean() {
        return _targetSourceBean;
    }
    
    /////////////////////////////////////////////////////////
    // Active Directory Save Forest Setting
    //
    ////////////////////////////////////////////////////////////
    /**
     * The method will save AD domains
     * @throws GeneralException 
     */
    @SuppressWarnings("unchecked")
    private void saveForest(Application app) throws GeneralException {
        List<ForestDataBean> beans =  ManageDomainBean._forestDataList;
        Attributes<String, Object> attrs = app.getAttributes();
        
          /*During page load of any old application (apps before 7.2) we do UI upgrade to transform old AD application into Multi-forest application 
           Multi-forest application introduced in 7.2 and done in function of upgradeToMultiforestUI() where we maintain  flag "_upgradedSucessfully"
           so once upgrade done successfully during page load then on save operation of that upgraded Multi forest application we need to remove old fields 
           whose places are moved in new UI */
        if (ManageDomainBean._upgradedSucessfully) {
            app.removeAttribute(ATT_FOREST_GC);
            app.removeAttribute(ATT_FOREST_ADMIN);
            app.removeAttribute(ATT_FOREST_ADMIN_PASS);
            
            //adding relevant attribute 7.2 
            app.setAttribute(ATT_ENABLE_CACHE, false);
            app.setAttribute(ATT_CACHE_RMI_PORT, "40001");
            app.setAttribute(ATT_CACHE_REMOTE_OBJECT_PORT, "40002");
            app.setAttribute(ATT_DISABLE_COMPUTEPRELOADING, true);
            app.setAttribute(ATT_DISABLE_FSPAGGREGATION, app.getBooleanAttributeValue(ATT_DISABLE_FSPAGGREGATION) ? true : false);
            ManageDomainBean._upgradedSucessfully =  false;
        }
        
        if ((beans != null) && !(beans.isEmpty())) {
            List forestDataList = new ArrayList();
            Map data = null;

            // the objects should be saved as maps
            // convert each object to map first before saving
            for (int i = 0; i < beans.size(); i++) {
                if (beans.get(i) instanceof ForestDataBean) {
                    if(Util.isNullOrEmpty(beans.get(i).getObject().getForestName())) {
                        throw new GeneralException("Forest Name is mandatory for each entry of forest please re-configure domains for forest");
                    }
                    
                    if (!ManageDomainBean.isDomainEntryPresentForForest(beans.get(i))){
                        throw new GeneralException("Details for at least one Domain under Forest : "+ beans.get(i).getObject().getForestName() + " is required ");
                    }
                    if (beans.get(i).getObject().isManageAllDomain()) {
                        ManageDomainBean.syncForestDataWithDomain(beans.get(i));
                    }
                    
                    data = new HashMap();
                    data.put(AD_ATT_FOREST_NAME, beans.get(i).getObject()
                            .getForestName());
                    data.put(AD_ATT_USE_SSL, beans.get(i).getObject()
                            .isUseSSL());
                    data.put(ATT_AUTHENTICATION_TYPE, beans.get(i).getObject()
                            .getAuthenticationType());
                    
                    if(beans.get(i).getObject().isUseSSL()) {
                        //if port is not given in GC then  append default one 
                        if (!beans.get(i).getObject().getGcServer().contains(":")) {
                            StringBuilder GCServer = new StringBuilder(beans.get(i).getObject().getGcServer().trim());
                            GCServer = GCServer.append(":").append(ForestData.GC_SSL_PORT);
                            data.put(AD_ATT_GC_SERVER, GCServer.toString());
                        } else {
                            data.put(AD_ATT_GC_SERVER,beans.get(i).getObject().getGcServer().trim());
                        }
                    }
                    if(!beans.get(i).getObject().isUseSSL()) {
                        //if port is not given in GC then  append default one 
                        if (Util.isNotNullOrEmpty(beans.get(i).getObject().getGcServer()) && !beans.get(i).getObject().getGcServer().contains(":")){
                            StringBuilder GCServer = new StringBuilder(beans.get(i).getObject().getGcServer().trim());
                            GCServer = GCServer.append(":").append(ForestData.GC_DEFAULT_PORT);
                            data.put(AD_ATT_GC_SERVER, GCServer.toString());
                        } else {
                            data.put(AD_ATT_GC_SERVER,beans.get(i).getObject().getGcServer().trim()); 
                        }
                    }
                    
                    // If we have strong authentication configured by user
                    // then check if username is in UPN format
                    if("strong".equals(beans.get(i).getObject().getAuthenticationType()) &&
                            Util.isNotNullOrEmpty(beans.get(i).getObject().getUser())    &&
                            !beans.get(i).getObject().getUser().contains("@")) {
                        throw new GeneralException(
                                "Error in forest [" + beans.get(i).getObject().getForestName() + "] " +
                                " For strong authentication to work, username must be in UPN format i.e UserName@DNSDomainName.com");
                    }
                    
                    data.put(AD_ATT_MANAGE_ALL_DOMAINS, beans.get(i).getObject()
                            .isManageAllDomain());
                    data.put(AD_ATT_USER, beans.get(i).getObject().getUser());
                    data.put(AD_ATT_PASSWORD, beans.get(i).getObject()
                            .getPassword());
                    data.put(AD_ATT_AUTH_TYPE, beans.get(i).getObject()
                            .getAuthType());
                    data.put(AD_ATT_USE_GROUP_MEMBERSHIP_PRELOADING, beans.get(i).getObject()
                            .getUseGroupMembershipPreloading());
                    data.put(AD_ATT_IS_RESOURCE_FOREST, beans.get(i).getObject()
                            .getIsResourceForest());
                    forestDataList.add(data);
                } else {
                    data = (Map) beans.get(i);
                    String user = (String) data.get(AD_ATT_USER);
                    String forestName = (String) data.get(AD_ATT_FOREST_NAME);
                    String gcServer = (String) data.get(AD_ATT_GC_SERVER );
                    
                    if(Util.isNullOrEmpty(forestName) && 
                            (Util.isNotNullOrEmpty(gcServer) || Util.isNotNullOrEmpty(user))) {
                        throw new GeneralException("Forest Name is mandatory for each entry of forest");
                    }
                    forestDataList.add(data);
                }
              }

            Map map = attrs.getMap();
            map.put(AD_ATT_FOREST_SETTINGS, forestDataList);
            attrs.setMap(map);
        } else if (attrs != null
                && attrs.containsKey(Connector.CONFIG_HOST)
                && Util.isNotNullOrEmpty((String) attrs
                        .get(Connector.CONFIG_HOST))) {
         // This is for backward compatibility of AD application
        } else {
            /*throw new GeneralException(
                    "Forest details for at least one domain is mandatory");*/
        }
        
    }
    
    
    /////////////////////////////////////////////////////////
    // Active Directory Auto Discover Domains
    //
    ////////////////////////////////////////////////////////////
       
    /**
     * The method will save AD domains
     * @throws GeneralException 
     */
    @SuppressWarnings("unchecked")
    private void saveDomains(Application app) throws GeneralException {
        List<DomainDataBean> beans =  ManageDomainBean._domainDataList;
        Attributes<String, Object> attrs = app.getAttributes();
 
        if ((beans != null) && !(beans.isEmpty())) {
            List domainDataList = new ArrayList();
            Map data = null;

            // the objects should be saved as maps
            // convert each object to map first before saving
            for (int i = 0; i < beans.size(); i++) {
                if (beans.get(i) instanceof DomainDataBean) {
                    if(Util.isNullOrEmpty(beans.get(i).getObject().getUser()) || Util.isNullOrEmpty(beans.get(i).getObject().getDomainDN()) 
                         || Util.isNullOrEmpty(beans.get(i).getObject()
                            .getDomainForestName())) {
                        throw new GeneralException("Forest Name, Domain DN and User details are mandatory for each entry of domain");
                    }
                    data = new HashMap();
                    data.put(ATT_DOMAIN_FOREST_NAME, beans.get(i).getObject()
                            .getDomainForestName());
                    data.put(AD_ATT_DOMAIN_DN, beans.get(i).getObject()
                            .getDomainDN());
                    data.put(AD_ATT_DOMAIN_NET_BIOS, beans.get(i).getObject().getNetBIOS());
                    data.put(AD_ATT_SERVERS, beans.get(i).getObject()
                             .getServerList());
                    data.put(AD_ATT_USE_SSL, beans.get(i).getObject()
                            .isUseSSL());
                    data.put(ATT_AUTHENTICATION_TYPE, beans.get(i).getObject()
                            .getAuthenticationType());
                    if(beans.get(i).getObject().isUseSSL()) {
                        if(beans.get(i).getObject().getPort()!=null && beans.get(i).getObject().getPort().equalsIgnoreCase(DomainData.DEFAULT_PORT)) {
                            beans.get(i).getObject().setPort(DomainData.SSL_PORT);
                        }
                    }
                    if(!beans.get(i).getObject().isUseSSL()) {
                        if(beans.get(i).getObject().getPort()!=null && beans.get(i).getObject().getPort().equalsIgnoreCase(DomainData.SSL_PORT)) {
                            beans.get(i).getObject().setPort(DomainData.DEFAULT_PORT);
                        }
                    }
                    
                     // If we have strong authentication configured by user
                    // then check if username is in UPN format
                    if("strong".equals(beans.get(i).getObject().getAuthenticationType()) &&
                            beans.get(i).getObject().getUser() != null                   &&
                            !beans.get(i).getObject().getUser().contains("@")) {
                        throw new GeneralException(
                                "Error in domain [" + beans.get(i).getObject().getDomainDN() + "] " +
                                " For strong authentication to work, username must be in UPN format i.e UserName@DNSDomainName.com");
                    }
                    
                    data.put(AD_ATT_USER, beans.get(i).getObject().getUser());
                    data.put(AD_ATT_PASSWORD, beans.get(i).getObject()
                            .getPassword());
                    data.put(AD_ATT_PORT, beans.get(i).getObject()
                            .getPort());
                    data.put(AD_ATT_AUTH_TYPE, beans.get(i).getObject()
                            .getAuthType());
                    data.put(AD_ATT_DOMAIN_ITERATE_SEARCH_FILTER, beans.get(i).getObject()
                            .getDomainIterateSearchFilter());
                    if(ManageDomainBean.isResourceForesrDomain(beans.get(i))) {
                        data.put(AD_ATT_DISABLE_SHADOW_ACCOUNT_MEMBERSHIP, beans.get(i).getObject()
                                .isDisableShadowAccountMembership());
                        data.put(AD_ATT_SHADOW_ACCOUNT_MEMBERSHIP_FILTER, beans.get(i).getObject()
                                .getShadowAccountMembershipFilter());
                    }
                    domainDataList.add(data);
                } else {
                    data = (Map) beans.get(i);
                    String user = (String) data.get(AD_ATT_USER);
                    String domainDN = (String) data.get(AD_ATT_DOMAIN_DN);
                    if(Util.isNullOrEmpty(user) || Util.isNullOrEmpty(domainDN)) {
                        throw new GeneralException("Domain DN and User details are mandatory for each entry of domain");
                    } 
                    if((data.get(AD_ATT_USE_SSL) != null) && (Boolean) data.get(AD_ATT_USE_SSL)) {
                        if((data.get(AD_ATT_PORT) != null) && data.get(AD_ATT_PORT).toString().equalsIgnoreCase(DomainData.DEFAULT_PORT)) {
                            data.put(AD_ATT_PORT, DomainData.SSL_PORT);
                        }
                    }
                    if((data.get(AD_ATT_USE_SSL) != null) && !(Boolean) data.get(AD_ATT_USE_SSL)) {
                        if((data.get(AD_ATT_PORT) != null) && data.get(AD_ATT_PORT).toString().equalsIgnoreCase(DomainData.SSL_PORT)) {
                            data.put(AD_ATT_PORT, DomainData.DEFAULT_PORT);
                        }
                    }
                    domainDataList.add(data);
                }
            }

            Map map = attrs.getMap();
            map.put(AD_ATT_DOMAIN_SETTINGS, domainDataList);
            attrs.setMap(map);
        } else if (attrs != null
                && attrs.containsKey(Connector.CONFIG_HOST)
                && Util.isNotNullOrEmpty((String) attrs
                        .get(Connector.CONFIG_HOST))) {
         // This is for backward compatibility of AD application
        } else {
            throwWhenNotConfigured("Details for at least one Domain/Forest is required");
        }
    }

    /**
     * The method will save Exchange settings in application
     * @throws GeneralException
     */
    @SuppressWarnings("unchecked")
    private void saveExchangeSettings(Application app) throws GeneralException {

        List<ExchangeDataBean>     beans           = ManageDomainBean._exchangeDataList;
        Attributes<String, Object> attrs           = app.getAttributes();
        String                     exchangeVersion = Util.otos(app.getAttributeValue(AD_EXCHANGE_VERSION));

        if ((beans != null) && !(beans.isEmpty())) {
            List exchangeDataList = new ArrayList();
            Map data = null;

            // the objects should be saved as maps
            // convert each object to map first before saving
            for (int i = 0; i < beans.size(); i++) {

                if (beans.get(i) instanceof ExchangeDataBean) {
                    if(Util.isNullOrEmpty(beans.get(i).getObject().getUser()) || Util.isNullOrEmpty(beans.get(i).getObject().getPassword()) 
                            || Util.isNullOrEmpty(beans.get(i).getObject().getExchangeForest()) || Util.isEmpty(beans.get(i).getObject().getExchHost()) || Util.isEmpty(beans.get(i).getObject().getAccountForestList()) ) {
                        throw new GeneralException("Exchange Forest Name, Exchange hosts, User, Password and Account Forest Name details are required.");
                    }

                    //Check if account forest names provided under list are valid forest names
                    if(!ManageDomainBean.validateExchAccountForestName(beans.get(i).getObject().getAccountForestList())) {
                        throw new GeneralException("Exchange Account Forest Name is invalid or not defined in forest setting");
                    }

                    data = new HashMap();
                    data.put(ATT_EXCHANGE_FOREST_NAME, beans.get(i).getObject()
                            .getExchangeForest());
                    data.put(ATT_EXCHANGE_HOSTS, beans.get(i).getObject()
                            .getExchHost());
                    data.put(ATT_EXCHANGE_USER, beans.get(i).getObject().getUser());
                    data.put(ATT_EXCHANGE_PASSWORD, beans.get(i).getObject()
                            .getPassword());
                    data.put(ATT_EXCHANGE_ACCOUNT_FOREST, beans.get(i).getObject()
                            .getAccountForests());
                    data.put(ATT_EXCHANGE_USE_TLS, beans.get(i).getObject()
                            .isUseTLS());

                    exchangeDataList.add(data);
                } else {
                    data = (Map) beans.get(i);
                    String user              = (String) data.get(ATT_EXCHANGE_USER);
                    String password          = (String) data.get(ATT_EXCHANGE_PASSWORD);
                    String forestName        = (String) data.get(ATT_EXCHANGE_FOREST_NAME);
                    List exchHosts           = (List) data.get(ATT_EXCHANGE_HOSTS);
                    List<String> accountforestName = (List<String>) data.get(ATT_EXCHANGE_ACCOUNT_FOREST);
                    if(Util.isNullOrEmpty(user) || Util.isNullOrEmpty(password) || Util.isNullOrEmpty(forestName) || Util.isEmpty(exchHosts) || Util.isEmpty(accountforestName)) {
                        throw new GeneralException("Exchange Forest Name, Exchange hosts, User , Password and Account Forest Name details are required.");
                    }
                    exchangeDataList.add(data);
                }
            }

            Map map = attrs.getMap();
            map.put(ATT_EXCHANGE_SETTINGS, exchangeDataList);
            attrs.setMap(map);
            // remove ExchHost entry from old application
            attrs.remove(ATT_EXCHANGE_HOSTS);
            // for exchange 2007 do not remove exchangeversion entry from debug
            if ( Util.isNotNullOrEmpty(exchangeVersion) && !exchangeVersion.equals("2007") ) {
                attrs.remove(AD_EXCHANGE_VERSION);
            }
        } else {
        	attrs.remove(ATT_EXCHANGE_SETTINGS);
        }
     }

    /**
     * The method will save Account Search Scopes
     * 
     * @throws GeneralException
     */
    @SuppressWarnings("unchecked")
    private void saveAccountSearchScope(Application app) throws GeneralException {
        Attributes<String, Object> attrs = app.getAttributes();
        List<AccountDataBean> beans = AccountSearchScopeBean._scopes; 
        List<ContactSearchScopeBean.AccountDataBean> contactBeans = ContactSearchScopeBean._scopes;
        List<GMSASearchScopeBean.AccountDataBean> gmsaBeans = GMSASearchScopeBean._scopes;

        if ((beans != null) && !(beans.isEmpty())) {
            List accountDataList = new ArrayList();
            Map data = null;

            for (int i = 0; i < beans.size(); i++) {
                if (beans.get(i) instanceof AccountDataBean) {
                    if(Util.isNullOrEmpty(beans.get(i).getObject().getSearchDN())) {
                        throw new GeneralException("Search DN is mandatory for each entry in user search scope");
                    } 
                    data = new HashMap();
                    data.put(AD_ATT_SEARCH_DN, beans.get(i).getObject()
                            .getSearchDN());
                    data.put(AD_ATT_GROUP_MEMBERSHIP_SEARCH_DN, beans.get(i)
                            .getObject().getGroupMembershipSearchDN());
                    data.put(AD_ATT_PRIMARY_GROUP_SEARCH_DN, beans.get(i)
                            .getObject().getPrimaryGroupSearchDN());
                    data.put(AD_ATT_SEARCH_SCOPE, beans.get(i).getObject()
                            .getSearchScope());
                    data.put(AD_ATT_ITERATE_SEARCH_FILTER, beans.get(i)
                            .getObject().getIterateSearchFilter());
                    data.put(AD_ATT_GROUP_MEMBER_FILTER_STRING, beans.get(i)
                            .getObject().getGroupMemberFilterString());
                    accountDataList.add(data);
                } else {
                    data = (Map) beans.get(i);
                    String user = (String) data.get(AD_ATT_USER);
                    String domainDN = (String) data.get(AD_ATT_DOMAIN_DN);
                    if(Util.isNullOrEmpty(user) || Util.isNullOrEmpty(domainDN)) {
                        throw new GeneralException("Search DN is mandatory for each entry");
                    }
                    accountDataList.add(data);
                }
            }

            Map map = attrs.getMap();
            if (map.containsKey(AD_ATT_ACCOUNT_SEARCH_DNS))
                map.put(AD_ATT_ACCOUNT_SEARCH_DNS, accountDataList);
            else
                map.put(AD_ATT_SEARCH_DNS, accountDataList);
            attrs.setMap(map);

        } else if (attrs != null
                && attrs.containsKey(LDAPConnector.CONFIG_SEARCH_DN)
                && Util.isNotNullOrEmpty((String) attrs
                        .get(LDAPConnector.CONFIG_SEARCH_DN))) {
            // This is for backward compatibility of AD application
        } else {
            throwWhenNotConfigured("Entry for at least one search DN is required for account search scope");
        }
        
        // Save contact.SearchDNs if present
        if(!Util.isEmpty(contactBeans)){
            List contactDataList = new ArrayList();
            Map data = null;

            for (int i = 0; i < contactBeans.size(); i++) {
                if (contactBeans.get(i) instanceof ContactSearchScopeBean.AccountDataBean) {
                    if(Util.isNullOrEmpty(contactBeans.get(i).getObject().getSearchDN())) {
                        throw new GeneralException("Search DN is mandatory for each entry in contact search scope");
                    } 
                    data = new HashMap();
                    data.put(AD_ATT_SEARCH_DN, contactBeans.get(i).getObject()
                            .getSearchDN());
                    data.put(AD_ATT_GROUP_MEMBERSHIP_SEARCH_DN, contactBeans.get(i)
                            .getObject().getGroupMembershipSearchDN());
                    data.put(AD_ATT_SEARCH_SCOPE, contactBeans.get(i).getObject()
                            .getSearchScope());
                    data.put(AD_ATT_ITERATE_SEARCH_FILTER, contactBeans.get(i)
                            .getObject().getIterateSearchFilter());
                    contactDataList.add(data);
                }
            }

            Map map = attrs.getMap();
            map.put(AD_ATT_CONTACT_SEARCH_DNS, contactDataList);
            attrs.setMap(map);
        } else if (attrs.containsKey(AD_ATT_CONTACT_SEARCH_DNS)
                && attrs.getMap().get(AD_ATT_CONTACT_SEARCH_DNS) != null) {
            Map map = attrs.getMap();
            map.put(AD_ATT_CONTACT_SEARCH_DNS, null);
            attrs.setMap(map);
        }

        // Save gmsa.SearchDNs if present
        if(!Util.isEmpty(gmsaBeans)){
            List gmsaDataList = new ArrayList();
            Map data = null;

            for (int i = 0; i < gmsaBeans.size(); i++) {
                if (gmsaBeans.get(i) instanceof GMSASearchScopeBean.AccountDataBean) {
                    if(Util.isNullOrEmpty(gmsaBeans.get(i).getObject().getSearchDN())) {
                        throw new GeneralException("Search DN is mandatory for each entry in gMSA search scope");
                    }
                    data = new HashMap();
                    data.put(AD_ATT_SEARCH_DN, gmsaBeans.get(i).getObject()
                            .getSearchDN());
                    data.put(AD_ATT_GROUP_MEMBERSHIP_SEARCH_DN, gmsaBeans.get(i)
                            .getObject().getGroupMembershipSearchDN());
                    data.put(AD_ATT_SEARCH_SCOPE, gmsaBeans.get(i).getObject()
                            .getSearchScope());
                    data.put(AD_ATT_ITERATE_SEARCH_FILTER, gmsaBeans.get(i)
                            .getObject().getIterateSearchFilter());
                    gmsaDataList.add(data);
                }
            }

            Map map = attrs.getMap();
            map.put(AD_ATT_GMSA_SEARCH_DNS, gmsaDataList);
            attrs.setMap(map);
        } else if (attrs.containsKey(AD_ATT_GMSA_SEARCH_DNS)
                && attrs.getMap().get(AD_ATT_GMSA_SEARCH_DNS) != null) {
            Map map = attrs.getMap();
            map.put(AD_ATT_GMSA_SEARCH_DNS, null);
            attrs.setMap(map);
        }

    }
    
    
    
    /**
     * @param app
     * @throws GeneralException
     * This function saves account search scope details for ldap applications
     */
    private void saveAccountSearchScopeldap(Application app) throws GeneralException {
        Attributes<String, Object> attrs = app.getAttributes();
        List<AccountDataBean> beans = AccountSearchScopeBean._scopes;        

        if ((beans != null) && !(beans.isEmpty())) {
            List accountDataList = new ArrayList();
            Map data = null;

            for (int i = 0; i < beans.size(); i++) {
                if (beans.get(i) instanceof AccountDataBean) {
                    if(Util.isNullOrEmpty(beans.get(i).getObject().getSearchDN())) {
                        throwWhenNotConfigured("Search DN is mandatory for each entry");
                    } 
                    data = new HashMap();
                    data.put(AD_ATT_SEARCH_DN, beans.get(i).getObject()
                            .getSearchDN());
                    data.put(AD_ATT_SEARCH_SCOPE, beans.get(i).getObject()
                            .getSearchScope());
                    data.put(AD_ATT_ITERATE_SEARCH_FILTER, beans.get(i)
                            .getObject().getIterateSearchFilter());
                    data.put(ATT_GROUP_MEMBERSHIP_SEARCH_SCOPE, beans.get(i).getObject().getGroupMembershipSearchScope());
                    accountDataList.add(data);
                } 
            }

            Map map = attrs.getMap();
            map.put(AD_ATT_SEARCH_DNS, accountDataList);
            attrs.setMap(map);

        } else {
            throwWhenNotConfigured("Entry for at least one search DN is required for account search scope");
        }
    }

    /**
     * The method will save Group Search DNs for ldap applications
     * 
     * @throws GeneralException
     */
    @SuppressWarnings("unchecked")
    private void saveGroupSearchScopeldap(Application app) throws GeneralException {
        Attributes<String, Object> attrs = app.getAttributes();
        List<GroupDataBean> beans = GroupSearchScopeBean._scopes;
        
        //CONETN-2857: Updating memberAttribute to dn only when application is being upgraded
        if (!isLdapMultigrp) {
            if (app.getSchema(CONFIG_GROUP_OBJECT_TYPE_VALUE) != null) {
                app.getSchema(CONFIG_GROUP_OBJECT_TYPE_VALUE).addConfig(CONFIG_MEMBER_ATTRIBUTE_KEY,CONFIG_MEMBER_ATTRIBUTE_VALUE);
            }
        }
        if ((beans != null) && !(beans.isEmpty())) {
            List groupDataList = null;
            Map data = null;
            Map map = attrs.getMap();
            String objectType = "";
            boolean multiScope = false; 
            
            for (int i = 0; i < beans.size(); i++) {
                multiScope = false;
                if (beans.get(i) instanceof GroupDataBean) {
                    data = new HashMap();
                    
                    data.put(ATT_OBJECT_TYPE, beans.get(i).getObject().getObjectType());
                    objectType = beans.get(i).getObject().getObjectType();
                    
                    data.put(AD_ATT_ITERATE_SEARCH_FILTER, beans.get(i)
                            .getObject().getIterateSearchFilter());
                    
                    data.put(AD_ATT_SEARCH_DN, beans.get(i).getObject()
                            .getSearchDN());
                    data.put(AD_ATT_SEARCH_SCOPE, "SUBTREE");
                } else {
                    data = (Map) beans.get(i);
                    objectType = beans.get(i).getObject().getObjectType();
                }
                if (map.containsKey(objectType+CONFIG_SEARCH_DNS_SUFFIX)) {
                    groupDataList = new ArrayList();
                    groupDataList.addAll((List)map.get(objectType+CONFIG_SEARCH_DNS_SUFFIX));
                    groupDataList.add(data);
                    multiScope = true;
                }
                if (multiScope) {
                    map.put(objectType+CONFIG_SEARCH_DNS_SUFFIX, groupDataList);
                    groupDataList = null;
                } else {
                    groupDataList = new ArrayList();
                    groupDataList.add(data);
                    map.put(objectType+CONFIG_SEARCH_DNS_SUFFIX, groupDataList);
                }
            }
            attrs.setMap(map);
        } else {
            Map map = attrs.getMap();
            map.remove(AD_ATT_GROUP_SEARCH_DNS);
            attrs.setMap(map);
        }
    }
    
    
    
    
    /**
     * The method will save Group Search DNs
     * 
     * @throws GeneralException
     */
    @SuppressWarnings("unchecked")
    private void saveGroupSearchScope(Application app) throws GeneralException {
        Attributes<String, Object> attrs = app.getAttributes();
        
        List<GroupDataBean> beans = GroupSearchScopeBean._scopes;
        
        if ((beans != null) && !(beans.isEmpty())) {
            List groupDataList = new ArrayList();
            Map data = null;

            for (int i = 0; i < beans.size(); i++) {
                if (beans.get(i) instanceof GroupDataBean) {
                    if(Util.isNullOrEmpty(beans.get(i).getObject().getSearchDN())) {
                        throw new GeneralException("Search DN is mandatory for each 'Group Search Scope' entry");
                    } 
                    data = new HashMap();
                    data.put(AD_ATT_SEARCH_DN, beans.get(i).getObject()
                            .getSearchDN());
                    data.put(AD_ATT_SEARCH_SCOPE, beans.get(i).getObject()
                            .getSearchScope());
                    data.put(AD_ATT_ITERATE_SEARCH_FILTER, beans.get(i)
                            .getObject().getIterateSearchFilter());
                    groupDataList.add(data);
                } else {
                    data = (Map) beans.get(i);
                    groupDataList.add(data);
                }
            }

            Map map = attrs.getMap();
            map.put(AD_ATT_GROUP_SEARCH_DNS, groupDataList);
            attrs.setMap(map);
        } else {
            Map map = attrs.getMap();
            map.remove(AD_ATT_GROUP_SEARCH_DNS);
            attrs.setMap(map);
        }
    }
    
    /**
     * Throws a GeneralException using textMessage when no Credential Configuration exists for the Application.
     * @param textMessage text to use as the message in the GeneralException
     * @throws GeneralException when no credential configuration exists for the app
     */
    private void throwWhenNotConfigured(String textMessage) throws GeneralException {
        if (!isHasCredentialConfiguration()) {
            throw new GeneralException(textMessage);
        }
    }
    
    /////////////////////////////////////////////////////////
    // REST WEBSERVICE
    //
    ////////////////////////////////////////////////////////////
       
    /**
     * The method will save REST WEBSERVICE CONFIGURATION 
     * @throws GeneralException 
     */
    @SuppressWarnings("unchecked")
    private void saveParams(Application app, boolean isTestConnection) throws GeneralException {
        Attributes<String, Object> attrs = app.getAttributes();
        
        if (!isTestConnection) {
            // Remove the non-relevant attributes only if it is SAVE operation.
            // DO NOTHING FOR TEST CONNECTION 
            String authType = (String) attrs.get("authenticationMethod");
            String grantType = (String) attrs.get("grant_type");
            if (_object.getType().equals(SCIM2_APP_TYPE)) {
                authType = (String) attrs.get("authType");
            }
            if (Util.isNotNullOrEmpty(authType)) {
                attrs.remove("oauth_token_info");
                // "BasicLogin" for Web Services connector and "basic" for SCIM 2.0 connector
                if (authType.equals("BasicLogin") || authType.equals("basic")) {
                    attrs.remove("accesstoken");
                    attrs.remove("client_id");
                    attrs.remove("client_secret");
                    attrs.remove("refresh_token");
                    attrs.remove("grant_type");
                    attrs.remove("token_url");
                    attrs.remove("private_key");
                    attrs.remove("privateKeyUploadedOn");
                    attrs.remove("private_key_password");
                    attrs.remove("resourceOwnerUsername");
                    attrs.remove("resourceOwnerPassword");
                    //Removing below attribute for SCIM 2.0
                    attrs.remove("oauthBearerToken");
                } else if (authType.equals("OAuth2Login")) {
                    if (Util.isNotNullOrEmpty(grantType)) {
                        if (!grantType.equals("REFRESH_TOKEN")) {
                            attrs.remove("refresh_token");
                        }
                        if (!(grantType.equals("PASSWORD")
                                || grantType.equals("SAML_BEARER_ASSERTION"))) {
                            attrs.remove("resourceOwnerUsername");
                            attrs.remove("resourceOwnerPassword");
                        }
                        if (grantType.equals("PASSWORD")) {
                            attrs.remove("client_id");
                            attrs.remove("client_secret");
                        }
                        if (!grantType.equals("JWT_BEARER")) {
                            attrs.remove("private_key");
                            attrs.remove("privateKeyUploadedOn");
                            attrs.remove("private_key_password");
                        }
                    }
                    attrs.remove("username");
                    attrs.remove("password");
                    attrs.remove("accesstoken");
                    //Removing below attribute for SCIM 2.0.
                    attrs.remove("oauthBearerToken");
                } else if (authType.equals("No Auth")) {
                    attrs.remove("accesstoken");
                    attrs.remove("client_id");
                    attrs.remove("client_secret");
                    attrs.remove("refresh_token");
                    attrs.remove("grant_type");
                    attrs.remove("token_url");
                    attrs.remove("private_key");
                    attrs.remove("privateKeyUploadedOn");
                    attrs.remove("private_key_password");
                    attrs.remove("username");
                    attrs.remove("password");
                    attrs.remove("resourceOwnerUsername");
                    attrs.remove("resourceOwnerPassword");
                } else if (authType.equals("OAuthLogin") || authType.equals("oauthBearer")) {
                 // "OAuthLogin" for Web Services connector and "oauthBearer" for SCIM 2.0 connector
                    attrs.remove("client_id");
                    attrs.remove("client_secret");
                    attrs.remove("refresh_token");
                    attrs.remove("grant_type");
                    attrs.remove("token_url");
                    attrs.remove("private_key");
                    attrs.remove("privateKeyUploadedOn");
                    attrs.remove("private_key_password");
                    attrs.remove("username");
                    attrs.remove("password");
                    attrs.remove("resourceOwnerUsername");
                    attrs.remove("resourceOwnerPassword");
                }
            }
        }
        
        List<Integer> delRecList = new ArrayList<Integer>(); 
        List<ConnectorOperationBean> beans = (List<ConnectorOperationBean>) getEditState(ManageRestBean.ATT_CONNECTION_PARAMETERS);
        if (beans != null) {
            List connectionParametersList = new ArrayList();
            Map data = null;
            // the objects should be saved as maps
            // convert each object to map first before saving
            for (int i = 0; i < beans.size(); i++) {
                if (beans.get(i) instanceof ConnectorOperationBean) {
                    data = new HashMap<String, Object>();
                    if (Util.isNullOrEmpty((String)beans.get(i).getObject().getOrder())){
                        delRecList.add(i);
                    } else {
                        data.put(ConnectorOperationData.ATT_SEQUENCE_NUMBER_FOR_ENDPOINT, beans.get(i)
                                .getObject().getOrder());
                        data.put(ConnectorOperationData.ATT_CONTEXT_URL, beans.get(i).getObject()
                                .getContextUrl());
                        data.put(ConnectorOperationData.ATT_CUSTOM_AUTH_URL, beans.get(i).getObject()
                                .getCustomAuthUrl());
                        data.put(ConnectorOperationData.ATT_HTTP_METHOD_TYPE, beans.get(i).getObject()
                                .getHttpMethodType());
                        data.put(ConnectorOperationData.ATT_UNIQUE_NAME, beans.get(i).getObject()
                                .getName());
                        data.put(ConnectorOperationData.ATT_OPERATION_TYPE, beans.get(i).getObject()
                                .getOperation());
                        data.put(ConnectorOperationData.ATT_AFTER_RULE, beans.get(i).getObject()
                                .getAfterRule());
                        data.put(ConnectorOperationData.ATT_BEFORE_RULE, beans.get(i).getObject()
                                .getBeforeRule());
                        data.put(ConnectorOperationData.ATT_HEADER, beans.get(i).getObject()
                                .getConfigObject(ConnectorOperationData.ATT_HEADER));

                        Map<String, Object> bodyMap = new HashMap<String, Object>();
                        bodyMap.put(ConnectorOperationData.ATT_BODY_FORMAT, beans.get(i).getObject()
                                .getBodyFormat());
                        if (Util.isNullOrEmpty((String)bodyMap.get(ConnectorOperationData.ATT_BODY_FORMAT)))
                            bodyMap.put(ConnectorOperationData.ATT_BODY_FORMAT, ConnectorOperationData.ATT_BODY_FORMAT_RAW);

                        if (bodyMap.get(ConnectorOperationData.ATT_BODY_FORMAT).equals(ConnectorOperationData.ATT_BODY_FORMAT_RAW)){
                            bodyMap.put(ConnectorOperationData.ATT_BODY_FORM_DATA, null);
                            bodyMap.put(ConnectorOperationData.ATT_JSON_BODY, beans.get(i).getObject()
                                    .getJsonBody());
                        } else {
                            bodyMap.put(ConnectorOperationData.ATT_BODY_FORM_DATA, beans.get(i).getObject()
                                    .getConfigObject(ConnectorOperationData.ATT_BODY_FORM_DATA));
                            bodyMap.put(ConnectorOperationData.ATT_JSON_BODY, null);
                        }
                        data.put(ConnectorOperationData.ATT_BODY, bodyMap);

                        data.put(ConnectorOperationData.ATT_RES_MAP_OBJ, beans.get(i).getObject()
                                .getConfigObject(ConnectorOperationData.ATT_RES_MAP_OBJ));

                        if (beans.get(i).getObject().getResponseCode() != null) {
                            String resStr = beans.get(i).getObject()
                            .getResponseCode();
                            if (resStr != null){
                                List<String> resList = Util.csvToList(resStr);
                                if (resList != null) {
                                    data.put(ConnectorOperationData.ATT_RES_CODE, resList);
                                }
                            }
                        }
                        data.put(ConnectorOperationData.ATT_ROOT_PATH, beans.get(i).getObject()
                                .getRootPath());
                        data.put(ConnectorOperationData.ATT_PAGINATION_STEPS,
                                beans.get(i).getObject().getPaginationSteps());
                        data.put(ConnectorOperationData.ATT_PAGINATION_INITIAL_OFFSET,
                                beans.get(i).getObject().getPagingInitialOffset());
                        data.put(ConnectorOperationData.ATT_PAGINATION_SIZE,
                                beans.get(i).getObject().getPagingSize());

                        data.put(ConnectorOperationData.ATT_XPATH_NAMESPACES,beans.get(i).getObject()
                                .getConfigObject(ConnectorOperationData.ATT_XPATH_NAMESPACES));
                        
                        data.put(
                                ConnectorOperationData.ATT_PARENT_ENDPOINT_NAME,
                                beans.get(i).getObject()
                                        .getParentEndpointName());
                        connectionParametersList.add(data);
                    }
                }
            }
            Map map = attrs.getMap();
            if (!Util.isEmpty(delRecList)){
                for (int i=0; i < delRecList.size(); i++)
                    connectionParametersList.remove(delRecList.get(i));
            }
            map.put(ManageRestBean.ATT_CONNECTION_PARAMETERS,
                    connectionParametersList);
            attrs.setMap(map);
        } 

    }
    


    ///////////////////////////////////////////////////////////////////////////
    //
    // Password Policy Stuff
    //
    ///////////////////////////////////////////////////////////////////////////

    /**
     * Validate and save. Handles the saving of multiple cases.
     * 1. Adding new password policy.  New holder and policy should be created.
     * 2. Editing existing policy. Just the policy object should be updated.
     * 3. Adding existing policy. New holder object and the app object should be updated.
     *
     * @throws GeneralException
     */
    public void savePasswordPolicies(PasswordPolicyHolderDTO dto) throws GeneralException {
        Application app = getObject();
        PasswordPolicy originalPolicy = null;
        
        PasswordPolicyHolder policyToPopulate = app.getPasswordPolicyHolderById(dto.getId());
        // Is this a new PasswordPolicyHolder?
        if (policyToPopulate == null) {
            policyToPopulate = new PasswordPolicyHolder();
            app.addPasswordPolicy(policyToPopulate);
        } else {
            originalPolicy = getContext().getObjectById(PasswordPolicy.class, dto.getPolicyId());
            originalPolicy = (PasswordPolicy)originalPolicy.deepCopy(
                    ((XMLReferenceResolver)getContext()));
        }

        // Update regardless of new or edit
        dto.updatePasswordPolicyHolder(getContext(), policyToPopulate);

        //audit policy change
        this.auditPasswordPolicyChange(originalPolicy, policyToPopulate.getPolicy());
        
        getContext().saveObject(policyToPopulate.getPolicy());
        
        //Ensure we are synced-up with our session's users.
        List<Identity> remediators = app.getRemediators();
        List<Identity> thisSessionsRemediators = new ArrayList<Identity>();
        for(Identity remediator : Util.iterate(remediators)) {
            if ( remediator.isWorkgroup() && getLoggedInUser().isInWorkGroup(remediator)) {
                thisSessionsRemediators.add(getContext().getObjectById(Identity.class, remediator.getId()));
            } else {
                if ( remediator.getId().equals(getLoggedInUser().getId())) {
                    thisSessionsRemediators.add(getLoggedInUser());
                } else {
                    thisSessionsRemediators.add(remediator);
                }
            }
        }
        
        if(!Util.isEmpty(thisSessionsRemediators)) {
            app.setRemediators(thisSessionsRemediators);
        }

        // need to save off the object for selector edits
        getContext().saveObject(app);

        getContext().commitTransaction();
    }

    ///////////////////////////////////////////////////////////////////////////
    //
    // Activity Data Sources
    //
    ///////////////////////////////////////////////////////////////////////////

    private void addEditedActivityDataSourceToSession() {
        if ( Util.getString(_selectedDataSourceId) != null ) {
            try {
                boolean found = false;
                
                // first check the edit map so we can display the most up to date changes
                Map<String, ActivityDataSourceDTO> editedActivityDataSources = (Map<String, ActivityDataSourceDTO>)getEditState(EDITED_ACTIVITY_DS_MAP);
                if ( editedActivityDataSources != null ) {
                    ActivityDataSourceDTO dto = editedActivityDataSources.get(_selectedDataSourceId);
                    if ( dto != null ) {
                        ActivityDataSource dataSourceToPopulate = new ActivityDataSource();
                        if (null != dto.getId()) {
                            // if we have an id then try to load it, probably will never be null but guard
                            // against NPE anyway
                            dataSourceToPopulate = getContext().getObjectById(ActivityDataSource.class, dto.getId());
                            if (null == dataSourceToPopulate) {
                                dataSourceToPopulate = new ActivityDataSource();
                            }
                        }
                        
                        dto.updateActivityDataSource(getContext(), dataSourceToPopulate);
                        addEditState(EDITED_ACTIVITY_DS, new ActivityDataSourceObjectBean(dataSourceToPopulate));
                        found = true;
                    }
                }

                // if we have an id and it's not in the edit map, check the application
                if ( !found ) {
                    List<ActivityDataSource> availableSources = getObject().getActivityDataSources();
                    if (availableSources != null) {
                        for (ActivityDataSource ads : availableSources) {
                            if (ads.getId().equals(_selectedDataSourceId)) {
                                ActivityDataSourceObjectBean activityDS = new ActivityDataSourceObjectBean(ads);
                                addEditState(EDITED_ACTIVITY_DS, activityDS);
                                break;
                            }
                        }
                    }
                }
            } catch (GeneralException e) {
                Message errMsg = new Message(Message.Type.Error,
                        MessageKeys.ERR_ACTIVITY_DATA_SRC_CANNOT_BE_EDITED);
                log.error(errMsg.getMessage(), e);
                addMessage(errMsg, null);
            }
        } else {
            ActivityDataSourceObjectBean activityDS = new ActivityDataSourceObjectBean();
            addEditState(EDITED_ACTIVITY_DS, activityDS);
        }

    }

    @SuppressWarnings("unchecked")
    private void saveActivityDataSources() throws GeneralException {
        Application app = getObject();

        // Remove the deleted stuff
        List<String> deletedDataSourceIds = (List<String>)getEditState(DELETED_ACTIVITY_DS_LIST);
        if (deletedDataSourceIds != null && !deletedDataSourceIds.isEmpty()) {
            for (String deletedDataSourceId : deletedDataSourceIds) {
                ActivityDataSource deletedDataSource = getContext().getObjectById(ActivityDataSource.class, deletedDataSourceId);
                app.removeActivityDataSource(deletedDataSource);
                getContext().removeObject(deletedDataSource);
            }
        }

        // Update the existing stuff and add the new stuff
        Map<String, ActivityDataSourceDTO> editedActivityDataSources =
            (Map<String, ActivityDataSourceDTO>)getEditState(EDITED_ACTIVITY_DS_MAP);
        if (editedActivityDataSources != null && !editedActivityDataSources.isEmpty()) {
            Collection<ActivityDataSourceDTO> dtos = editedActivityDataSources.values();
            for (ActivityDataSourceDTO dto : dtos) {
                String dtoName = dto.getName();
                String dtoId = dto.getId();
                ActivityDataSource dataSourceToPopulate = null;
                // First try by id which will hit most cases, but
                // try by name just incase the datasource hasn't been
                // persisted yet
                if ( dtoId != null )
                    dataSourceToPopulate = app.getActivityDataSourceById(dtoId);
                if ( dataSourceToPopulate == null )
                    dataSourceToPopulate = app.getActivityDataSource(dtoName);

                // make a new one and assimilate the info from the dto
                if (dataSourceToPopulate == null) {
                    dataSourceToPopulate = new ActivityDataSource();
                    app.addActivityDataSource(dataSourceToPopulate);
                }
                dto.updateActivityDataSource(getContext(), dataSourceToPopulate);
            }
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    //
    // Composite stuff  - mostly in compositeTiers.xhtml
    //
    ///////////////////////////////////////////////////////////////////////////

    public boolean isComposite() throws GeneralException{
        Application app = getObject();
        return app != null ? app.isLogical() : false;
    }

    /**
     * Returns true if this application
     * @return
     * @throws GeneralException
     */
    public boolean isAllowTiersEdit() throws GeneralException{
        return ( ( isComposite()) &&
                 ( sailpoint.connector.DefaultLogicalConnector.class.getName().equals(getObject().getConnector()) ));
    }

    public String getSourceApplication() {
        return _sourceApplication;
    }

    public void setSourceApplication(String sourceApplication) {
        this._sourceApplication = sourceApplication;
    }

    public String getSourceAttribute() {
        return _sourceAttribute;
    }

    public void setSourceAttribute(String sourceAttribute) {
        this._sourceAttribute = sourceAttribute;
    }

    /**
     * Used to populate the account rule select box.
     * @return
     * @throws GeneralException
     */
    public String getCompositeAccountRule() throws GeneralException{
        CompositeDefinition definition = getObject().getCompositeDefinition();
        return definition != null ? definition.getAccountRule() : null;
    }

    /**
     * Do nothing here. We need a mutator for this property to make it easy
     * to populate select lists.
     * @param v
     */
    public void setCompositeAccountRule(String v){
        // do nothing
    }

    /**
     * Used to populate the composite remediation rule select box.
     * @return
     * @throws GeneralException
     */
    public String getCompositeRemediationRule() throws GeneralException{
        CompositeDefinition definition = getObject().getCompositeDefinition();
        return definition != null ? definition.getRemediationRule() : null;
    }

    /**
     * Do nothing here. We need a mutator for this property to make it easy
     * to populate select lists.
     * @param v
     */
    public void setCompositeRemediationRule(String v){
        // do nothing
    }

    public List<SelectItem> getCompositeAccountRuleSelectList() throws GeneralException {

        if (_compositeAccountRuleSelectList == null)
            _compositeAccountRuleSelectList = WebUtil.getRulesByType(getContext(),
                                                                                                  Rule.Type.CompositeAccount,
                                                                                                  false);

        return _compositeAccountRuleSelectList;
    }

    public List<SelectItem> getCompositeTierCorrelationRuleSelectList() throws GeneralException {
        if (_compositeTierCorrelationRuleSelectList == null)
            _compositeTierCorrelationRuleSelectList = WebUtil.getRulesByType(getContext(),
                                                                                                           Rule.Type.CompositeTierCorrelation,
                                                                                                           false);

        return _compositeTierCorrelationRuleSelectList;
    }

    public List<SelectItem> getCompositeRemediationRuleSelectList() throws GeneralException {
        if (_compositeRemediationRuleSelectList == null)
            _compositeRemediationRuleSelectList = WebUtil.getRulesByType(getContext(),
                                                                                                        Rule.Type.CompositeRemediation,
                                                                                                        false);

        return _compositeRemediationRuleSelectList;
    }

    public List<SelectItem> getNewApplicationAccountSelectList() throws GeneralException {
        if (_newApplicationAccountSelectList == null)
          _newApplicationAccountSelectList = WebUtil.getSelectItems(getNewAppAccountAttributes(), false);

        return _newApplicationAccountSelectList;
    }

    /**
     * @deprecated use {@link #getNewApplicationSchemaObjectTypeSelectListMap()}
     */
    public List<SelectItem> getNewApplicationGroupSelectList() throws GeneralException {
        return getNewApplicationSchemaObjectTypeSelectListMap().get(Connector.TYPE_GROUP);
    }

    public Map<String, List<SelectItem>> getNewApplicationSchemaObjectTypeSelectListMap() throws GeneralException {
        if (_newApplicationSchemaObjectTypeSelectListMap == null) {
            _newApplicationSchemaObjectTypeSelectListMap = new HashMap<String, List<SelectItem>>();
            for (SchemaDTO dto : _schemaDTOs) {
                if (!Connector.TYPE_ACCOUNT.equals(dto.getObjectType())) {
                    _newApplicationSchemaObjectTypeSelectListMap.put(
                            dto.getObjectType(),
                            WebUtil.getSelectItems(
                                    getNewAppSchemaObjectTypeAttributes().get(dto.getObjectType()),
                                    false));
                }
            }
        }
        return _newApplicationSchemaObjectTypeSelectListMap;
    }


    public String getCompositeDefinitionJson() throws GeneralException{
        CompositeDefinition definition = getObject().getCompositeDefinition();
        if (definition == null) {
            definition = new CompositeDefinition();
        }
        return JsonHelper.toJson(definition);
    }

    /**
     * Converts the submitted json into a map, then populates the backing
     * CompositeDefinition object.
     * @param json
     * @throws GeneralException
     */
    public void setCompositeDefinitionJson(String json) throws GeneralException{

        try {
            Object o = JsonUtil.parse(json);
            if (o != null){
                Application app = this.getObject();
                Map defInput = (Map)o;

                CompositeDefinition logicalDefinition = app.getCompositeDefinition();
                if ( logicalDefinition == null ) {
                    logicalDefinition = new CompositeDefinition();
                }
                logicalDefinition.setPrimaryTier((String)defInput.get("primaryTier"));
                String accountRule = Util.getString(defInput,"accountRule");
                    logicalDefinition.setAccountRule(accountRule);
                String remediationRule = Util.getString(defInput,"remediationRule");
                    logicalDefinition.setRemediationRule(remediationRule);

                List<Map> tiersList = Util.asList(defInput.get("tiers"));
                //
                // Build a map of the json tiers by app to make removes easy to
                // detect
                //
                Map<String,Map> jsonTiers = new HashMap<String,Map>();
                if ( Util.size(tiersList) > 0 ) {
                    for ( Map tier : tiersList ) {
                        String appName = Util.getString(tier, "application");
                        jsonTiers.put(appName, tier);
                    }
                }

                //
                // Go through the current tiers and update existing
                // and remove tiers that weren't found in the new
                // definition.
                //
                List<Tier> tiers = logicalDefinition.getTiers();
                
                // Remove tiers that are no longer part of the config..
                if (!Util.isEmpty(tiers)) {
                    List<Tier> tiersToRemove = new ArrayList<Tier>();
                    for (Tier tier : Util.safeIterable(tiers)) {
                    	if (jsonTiers.get(tier.getApplication()) == null) {
                    		tiersToRemove.add(tier);
                    	}
                    }
                    tiers.removeAll(tiersToRemove);
                }
                
                for (Map tierMap : Util.safeIterable(tiersList)) {
                    String appName = Util.getString(tierMap, "application");
     
                    Tier newTier =  logicalDefinition.getTierByAppName(appName);
                    String rule = Util.getString(tierMap, "correlationRule");
                    
                    Map<String,String> correlationMap = null;
                    
                    Object correlationMapValue = tierMap.get("correlationMap");
                    if (correlationMapValue instanceof Map) {
                        correlationMap = (Map<String, String>) correlationMapValue;
                    }
                    
                    if ( newTier == null ) {
                        newTier = new CompositeDefinition.Tier(appName,
                                                               rule,
                                                               correlationMap);
                        logicalDefinition.addTier(newTier);
                    } else {
                        newTier.setCorrelationRule(rule);
                        newTier.setCorrelationMap(correlationMap);
                    }

                    IdentitySelectorDTO selector = getCurrentSelector();
                    if ( selector != null ) {
                        String selectorApp = selector.getApplication();
                        if ( ( selectorApp != null ) &&
                             ( selector.getApplication().equals(appName) ) ) {
                            newTier.setIdentitySelector(selector.convert());
                        }
                    }
                }

                // re-configure the app with the new definition
                app.setCompositeDefinition(logicalDefinition);            }
        } catch (Exception e) {
            log.error(e);
            throw new GeneralException(e);
        }
    }

    public String saveSelector() throws GeneralException {
        setCompositeSelector(_currentSelector);
        return "";
    }

    /**
     * Save the schemas and logical definition for this application.
     *
     * This action is called before the managed entitlements page
     * executes is find entitlements task. The task depends on
     * the logical configuraiton and the schema, and we are
     * trying to save off the parts of the app the scanner
     * is using without requireing a "full" save. ( and re-edit )
     *
     * We can only do this on updates because the app page
     * depends on knowing (via id presense) the difference
     * between edit and create.
     */
    public String savePartialApplication() throws GeneralException {
        Application app = getObject();
        if ( ( app != null ) && ( app.getId() != null ) ) {
            boolean updated = false;
            if ( _schemaLoaded )  {
                persistSchemaDTOs();
                setSchemaLoaded(false);
                updated = true;
            }
            if ( ( app.isLogical() ) && ( getLogicalConfigDirty() ) ) {
                setCompositeSelector(_currentSelector);
                updated = true;
            }
            if ( updated ) {
                getContext().saveObject(app);
                getContext().commitTransaction();
            }
        }
        return "";
    }

    public boolean getLogicalConfigDirty() {
        return _logicalConfigDirty;
    }

    public void setLogicalConfigDirty(boolean val) {
        _logicalConfigDirty = val;
        addEditState("logicalDirty", new Boolean(val));
    }

    public String switchTiers() throws GeneralException {
        if ( this._currentSelector != null ) {
            String app = _currentSelector.getApplication();
            if ( ( app != null ) && ( _currentTierApp != null ) ) {
                if ( _currentTierApp.compareTo(app) != 0 ) {
                    setCompositeSelector(_currentSelector);
                }
            }
            setCurrentSelector(null);
        }
        return "";
    }

    public String getCurrentTier() {
        return _currentTierApp;
    }

    public void setCurrentTier(String current) {
        _currentTierApp = current;
    }

    private IdentitySelectorDTO createDefaultSelector(String app) throws GeneralException {
        IdentitySelector s = new IdentitySelector();

        IdentitySelectorDTO selector = new IdentitySelectorDTO();
        if ( app != null ) {
            Application o = getContext().getObjectByName(Application.class, app);
            if ( o != null ) {
                selector = new IdentitySelectorDTO(s);
                selector.setApplication(o.getName());
                selector.setType(IdentitySelector.SELECTOR_TYPE_MATCH_LIST);
            }
        }
        return selector;
    }

    public void setCompositeSelector(IdentitySelectorDTO selector) throws GeneralException {

        Application appObject = getObject();
        if ( appObject == null ) return;

        CompositeDefinition definition = appObject.getCompositeDefinition();
        if ( ( definition != null ) && ( selector != null ) ) {
            //
            String appId = selector.getApplication();
            if ( appId != null ) {
                Application app = getContext().getObjectById(Application.class, appId);
                if ( app != null ) {
                    IdentitySelector selectorObject = selector.convert();
                    Tier currentTier  = definition.getTierByAppName(app.getName());
                    if ( currentTier != null ) {
                       currentTier.setIdentitySelector(selectorObject);
                    } else {
                       Tier tier = new Tier();
                       tier.setApplication(app.getName());
                       tier.setIdentitySelector(selectorObject);
                       definition.addTier(tier);
                    }
                }
            }
        }
    }

    public IdentitySelectorDTO getCompositeSelector() throws GeneralException {
        IdentitySelectorDTO dto = getCurrentSelector();
        if ( dto == null ) {
            dto = getTierSelector();
        }
        return dto;
    }

    private IdentitySelectorDTO getTierSelector() throws GeneralException {
        IdentitySelectorDTO selector = new IdentitySelectorDTO();
        String app = getCurrentTier();
        if ( app != null ) {
            selector  = _currentSelector;
            if ( selector == null ) {
                CompositeDefinition definition = getObject().getCompositeDefinition();
                if ( definition != null ) {
                    Tier currentTier  = definition.getTierByAppName(app);
                    if ( currentTier != null ) {
                        IdentitySelector s = currentTier.getIdentitySelector();
                        if ( s != null ) {
                            selector  = new IdentitySelectorDTO(s);
                            selector.setApplication(app);
                        }
                    }
                }
                if ( selector == null ) {
                    selector = createDefaultSelector(app);
                }
            }
        } else {
            selector = createDefaultSelector(null);
        }
        setCurrentSelector(selector);
        return selector;
    }

    public IdentitySelectorDTO getCurrentSelector() {
        return _currentSelector;
    }

    public void setCurrentSelector(IdentitySelectorDTO selector) {
         addEditState(CURRENT_SELECTOR, selector);
        _currentSelector = selector;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Composite attribute matching IdentitySelector
    //
    //    selector.xhtml action listeners and supporting methods
    //
    //////////////////////////////////////////////////////////////////////

    public void addSelectorAttribute(ActionEvent e) {
        try {

           IdentitySelectorDTO selector = getCurrentSelector();
           if (selector  != null) {
               selector.addMatchTerm(false, IdentitySelector.MatchTerm.Type.Entitlement.name());
           }

        } catch (Throwable t) {
            log.error(t.getMessage(), t);
            addMessage(t);
        }
    }

    public void addSelectorPermission(ActionEvent e) {
        try {
           IdentitySelectorDTO selector = getCurrentSelector();
           if (selector  != null)
               selector.addMatchTerm(false, IdentitySelector.MatchTerm.Type.Permission.name());
        }
        catch (Throwable t) {
            log.error(t.getMessage(), t);
            addMessage(t);
        }
    }

    public void deleteSelectorTerms(ActionEvent e) {
        try {
           IdentitySelectorDTO selector = getCurrentSelector();
           if (selector  != null)
               selector.deleteSelectedTerms();
        }
        catch (Throwable t) {
            log.error(t.getMessage(), t);
            addMessage(t);
        }
    }

    public void groupSelectedTerms(ActionEvent e) {
        try {
           IdentitySelectorDTO selector = getCurrentSelector();
           if (selector  != null)
                selector.groupSelectedTerms();
        }
        catch (Throwable t) {
            log.error(t.getMessage(), t);
            addMessage(t);
        }
    }

    public void ungroupSelectedTerms(ActionEvent e) {
        try {
           IdentitySelectorDTO selector = getCurrentSelector();
            if (selector != null)
                selector.ungroupSelectedTerms();
        }
        catch (Throwable t) {
            log.error(t.getMessage(), t);
            addMessage(t);
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    //
    // Correlation Config
    //
    ///////////////////////////////////////////////////////////////////////////

    /**
     * Get a list of SelectItems one for each of the attributes defined in
     * the account schema.
     */
    public List<SelectItem> getAccountAttributeNames() throws GeneralException {
        List<SelectItem> names = new ArrayList<SelectItem>();

        if (_schemaDTOs == null) {
            return names;
        }

        for (SchemaDTO dto : _schemaDTOs) {
            if (dto.getObjectType().equals(Connector.TYPE_ACCOUNT)) {
                List<SchemaAttributeDTO> attrDtos = dto.getAttributes();
                if (attrDtos != null) {

                    for (SchemaAttributeDTO attrDto : attrDtos) {
                        names.add(new SelectItem(attrDto.getName(), attrDto.getName()));
                    }

                    //#21130 - sorting attrDtos will modify underlying collection i.e. _schemaDTOs
                    //instead we will sort on SelectItem list
                    Collections.sort(names, new SelectItemByLabelComparator(getLocale()));
                }
            }
        }

        return names;
    }

    public void setAccountCorrelationBean(CorrelationConfigBean account) {
        _accountCorrelationBean = account;
    }

    public CorrelationConfigBean getAccountCorrelationBean() {
        return _accountCorrelationBean;
    }

    public String getAccountCorrelationConfig() throws GeneralException {
        Application app = getObject();
        CorrelationConfig cfg = app.getAccountCorrelationConfig();
        return (cfg != null) ? cfg.getName() : null;
    }

    public void setAccountCorrelationConfig(String name) throws GeneralException {
        Application app = getObject();
        if ( Util.getString(name) == null ) {
            app.setAccountCorrelationConfig(null);
        }
        else {
            CorrelationConfig config = getContext().getObjectByName(CorrelationConfig.class, name);
            if (config != null) {
                app.setAccountCorrelationConfig(config);
            }
        }
    }

    public String saveAccountCorrelation() throws GeneralException {
        if ( ( _object != null ) && ( _accountCorrelationBean != null ) ) {
            _accountCorrelationBean.setSessionObjectsIntoConfig();
            CorrelationConfig config = _accountCorrelationBean.getObject();
            if ( config != null ) {
                _object.setAccountCorrelationConfig(config);
                SailPointContext ctx = getContext();
                ctx.saveObject(config);
                ctx.commitTransaction();
                _accountCorrelationBean.setDirty(false);
            }
        }
        return "";
    }

    public String switchCorrelationConfig() throws GeneralException {
         if ( _accountCorrelationBean != null ) {
             _accountCorrelationBean.setDirty(false);
             _accountCorrelationBean.reset();
         }
         Application obj = getObject();
         CorrelationConfig config = obj.getAccountCorrelationConfig();
         if ( config == null ) {
             _accountCorrelationBean = new CorrelationConfigBean();
         } else {
             _accountCorrelationBean = new CorrelationConfigBean(config);
         }
         return "";
    }

    public FormEditBean getFormEditor() {
        return _formEditBean;
    }

    public void setFormEditor(FormEditBean formEditBean) {
        this._formEditBean = formEditBean;
    }

    private void saveForms() {
        try {
            getObject().setProvisioningForms(_formEditBean.commitForms());
        } catch (GeneralException ge) {
            log.warn("Unable to save form edit bean: " + ge.getMessage());
        }
    }

    private void buildFormEditBean() {
        try {
            List <Form> forms = getObject().getProvisioningForms();

            _formEditBean = new FormEditBean(forms,
                                             getObject().getSchemas(),
                                             Configuration.APPLICATION_TEMPLATE_ACCOUNT_USAGES);
        } catch (GeneralException ge) {
            log.warn("Unable to build form edit bean: " + ge.getMessage());
        }
        addEditState(APPLICATION_TEMPLATE_EDITOR, _formEditBean);
    }

    public void setAttributeDefinitionEditor(AttributeDefinitionEditBean attDefEditBean) {
        this._attrDefEditBean = attDefEditBean;
    }

    public AttributeDefinitionEditBean getSchemaAttributeEditor() {
        return this._attrDefEditBean;
    }

    public void buildSchemaAttributeEditBean() {
        _attrDefEditBean = new AttributeDefinitionEditBean(getSchemaDTOs());
        addEditState(SCHEMA_ATTRIBUTE_EDITOR, _attrDefEditBean);
    }


    public void setManagerCorrelationFilter(LeafFilter filter) {
        _managerCorrelationFilter = filter;
    }

    public LeafFilter getManagerCorrelationFilter() throws GeneralException {
        if ( _managerCorrelationFilter == null ) {
           Application app = getObject();
           if ( app != null ) {
               _managerCorrelationFilter = app.getManagerCorrelationFilter();
           }
        }
        if ( _managerCorrelationFilter == null )
           _managerCorrelationFilter = (LeafFilter) Filter.eq("","");

        return _managerCorrelationFilter;
    }

    public List<ListFilterValue> getServiceAccountFilter() throws GeneralException {
        if ( _serviceAccountFilter == null ) {
            Application app = getObject();
            if ( app != null ) {
                _serviceAccountFilter = app.getServiceAccountFilter();
            }
        }
        return _serviceAccountFilter;
    }

    public void setServiceAccountFilter(List<ListFilterValue> filter) {
        _serviceAccountFilter = filter;
    }

    public List<ListFilterValue> getRpaAccountFilter() throws GeneralException {
        if ( _rpaAccountFilter == null ) {
            Application app = getObject();
            if ( app != null ) {
                _rpaAccountFilter = app.getRpaAccountFilter();
            }
        }
        return _rpaAccountFilter;
    }

    public void setRpaAccountFilter(List<ListFilterValue> filter) {
        _rpaAccountFilter = filter;
    }

    public void setDisableAccountFilter(List<ListFilterValue> filter) {
        _disableAccountFilter = filter;
    }

    public List<ListFilterValue> getDisableAccountFilter() throws GeneralException {
        if ( _disableAccountFilter == null ) {
            Application app = getObject();
            if ( app != null ) {
                _disableAccountFilter = app.getDisableAccountFilter();
            }
        }
        return _disableAccountFilter;
    }

    public void setLockAccountFilter(List<ListFilterValue> filter) {
        _lockAccountFilter = filter;
    }

    public List<ListFilterValue> getLockAccountFilter() throws GeneralException {
        if ( _lockAccountFilter == null ) {
            Application app = getObject();
            if ( app != null ) {
                _lockAccountFilter = app.getLockAccountFilter();
            }
        }
        return _lockAccountFilter;
    }

    // provides the ability to reuse the same logic in getForm to
    // return either the form path, rules form or otherwise
    private interface FormResolver {
        public String getForm(Application app);
        public String getForm(ConnectorConfig config);
        public String getDefaultForm();
    }
    
    /**
     * @see #getForm(FormResolver)
     * @return the path to the forms on the settings page
     */
    public String getAttributesForm() {
        return getForm(new FormResolver() {
            @Override
            public String getForm(Application app) {
                return app.getFormPath();
            }
            @Override
            public String getForm(ConnectorConfig config) {
                return config.getAttributesForm();
            }
            @Override
            public String getDefaultForm() {
                try {
                    //We may not have a configForm for the deprecated DefaultComposite connector
                    if (isAllowTiersEdit()) {
                        return "compositeTiers.xhtml";
                    } else {
                        return "applicationAttributes.xhtml";
                    }
                } catch (GeneralException ge) {
                    if (log.isInfoEnabled()) {
                        log.info("Error checking if tier edit allowed", ge);
                    }
                    return "applicationAttributes.xhtml";
                }
            }
        });
    }
    
    /**
     * @see #getForm(FormResolver)
     * @return the rules form
     */
    public String getRulesForm() {
        return getForm(new FormResolver() {
            @Override
            public String getForm(Application app) {
                return app.getFormPathRules();
            }
            @Override
            public String getForm(ConnectorConfig config) {
                return null;
            }
            @Override
            public String getDefaultForm() {
                return null;
            }
        });
    }
    
    /**
     *  Calculate the form that should be used when displaying the attributes.
     *
     *  Here are the rules for resolving the form:
     *
     *    1) Check the app for a form, if it has one use it
     *    2) If not specified on the app, check on the template using the type in the 
     *       app selector 
     *    3) If the application is not new ( has an id ) then work back from
     *       the app to the template and check it for a form on the template
     *    4) If nothing on the app instance or template check the old way using
     *       connector registry.  This is unlikely but here for
     *       backward compatability.
     *  
     */
    @SuppressWarnings("unchecked")
    protected String getForm(FormResolver resolver) {
        String attributesForm = null;
        try {

            Application app = getObject();
            if ( ( app != null ) && ( Util.getString(resolver.getForm(app)) != null ) ) {
                // If has a form path in the attribute use it typically this is
                // stored on the template application only
                return resolver.getForm(app);
            }
            //
            // First attempt to derive the template and ultimately the form from the
            // type in the form
            //
            String type = getTypeFromForm();
            if ( attributesForm == null ) {
                Application template = DefaultApplicationFactory.getTemplateByType(type);
                if ( template != null ) {
                    attributesForm = resolver.getForm(template);
                }
            }

            if ( attributesForm == null ) {
                if ( app.getId() != null ) {
                    String templateApp = app.getTemplateApplication();
                    if ( templateApp != null  ) {
                        Application template =  DefaultApplicationFactory.getTemplateByName(templateApp);
                        if ( template != null ) {
                            attributesForm = resolver.getForm(template);
                        }
                    }
                }
            }
            //
            // If both of those fail try and look it up using the type and then deprecated
            // ConnectorConfigs if there are any present in the registry
            //
            if ( attributesForm == null ) {
                Configuration connectorRegistry = getContext().getObjectByName(Configuration.class, Configuration.CONNECTOR_REGISTRY);
                if ( connectorRegistry != null ) {
                    // djs: continue support the INSTALLED_CONNECTOR in-case there were upgrade issues moving them to templates
                    // should only be very limited number of these if any...
                    List<ConnectorConfig> configs = (List<ConnectorConfig>)connectorRegistry.getList(Configuration.INSTALLED_CONNECTORS);
                    if ( Util.size(configs) > 0 ) {
                        for ( ConnectorConfig config : configs ) {
                            String name = config.getDisplayName();
                            if ( Util.nullSafeEq(name,type, false) ) {
                                String form = resolver.getForm(config);
                                if ( Util.getString(form) != null )  {
                                    attributesForm = form;
                                    break;
                                }
                            }
                        }
                    }
                }
            }
            
            if ( attributesForm != null ) {
                // 
                // If the connector is null, let this indicate that 
                // we've got a multiplex application. The default form 
                // applicationAttributes.xhtml will avoid rendering 
                // the configuration attributes.
                //
                if ( app.getId() != null && app.getConnector() == null ) {
                    attributesForm = null;
                }
            }          
        } catch (GeneralException ex) {
            Message msg = new Message(Message.Type.Error,
            MessageKeys.ERR_CANT_FIND_CONN_REGISTRY);
            log.error(msg.getMessage(), ex);
            addMessage(msg, null);
        }        
        return (attributesForm == null ) ? resolver.getDefaultForm() : attributesForm;
    }
    
    public String getConnectorClass() {
        String connector = null;
        try {
            Application app = getObject();
            if ( app != null ) {
                connector = app.getConnector();
            }
        } catch(GeneralException  e) {
            log.error(e);
            addMessage(new Message(e.toString()), null);
        }
        return connector;
    }

    /**
     * Get the type field as it was last posted.
     */
    private String getTypeFromForm() throws GeneralException {
        String appClass = super.getRequestParameter("editForm:type");
        if ( Util.getString(appClass) == null ) {
            Application app = getObject();
            if ( app != null ) {
                appClass = app.getType();
            }
        }
        return appClass;
    }

    private boolean persistSchemaDTOs() {
        boolean error = false;

        // make sure that all schema items have a name
        Collection<SchemaDTO> schemaDTOs = _schemaDTOs;
        
        
        if ( schemaDTOs != null ) {
            List<String> dupNames = new ArrayList<String>();
            List<String> dupObjectType = new ArrayList<String>();
            boolean emptySchema = false;
            boolean missingAppTier = false;

            List<String> compositeApps = null;
            if(_object.isLogical()) {
                if(_object.getCompositeDefinition().getTiers() != null) {
                // Get a list of all app tiers
                compositeApps = _object.getCompositeDefinition().getTierAppList();
                }
            }
            for ( SchemaDTO schema : schemaDTOs ) {
                dupObjectType.clear();
                if ( schema == null ) continue;

                List<SchemaAttributeDTO> attrDefs = schema.getAttributes();
                if ( attrDefs == null ) continue;

                List<String> attrNames = new ArrayList<String>();
                List<String> objectTypes = new ArrayList<String>();
                for ( SchemaAttributeDTO attrDef : attrDefs ) {
                    if ( attrDef == null ) continue;
                    String attrName = attrDef.getName();
                    if ( attrName == null || attrName.length() == 0 )
                        emptySchema = true;

                    if (attrNames.contains(attrName)) {
                        dupNames.add(attrName);
                    } else {
                        attrNames.add(attrName);
                    }

                    //If app is logical, verify that the composite definition contains the app
                    // for all attributes with associated app
                    if(_object.isLogical() && Util.isNullOrEmpty(_object.getCompositeDefinition().getAccountRule())) {
                        String tierApp = attrDef.getCompositeSourceApplication();
                        if(tierApp != null && (compositeApps == null || !compositeApps.contains(tierApp))) {
                            missingAppTier = true;
                        }
                    }

                    //Verify that an objectType (non Primitive type) is only referenced by a single attribute definition
                    //in a given schema
                    if (!attrDef.isPrimitiveType()) {
                        if(objectTypes.contains(attrDef.getType())) {
                            dupObjectType.add(attrDef.getType());
                            break;
                        } else {
                            objectTypes.add(attrDef.getType());
                        }
                    }
                    
                }  // foreach AttributeDefinition
                if ( emptySchema || dupNames.size() > 0 || dupObjectType.size() > 0) break;
            }  // foreach Schema

            if ( dupNames.size() > 0 ) {
                error = true;
                addMessage(new Message(Message.Type.Error,
                    MessageKeys.ERR_ALL_SCHEMA_ATTR_UNIQUE_NAME,Util.listToCsv(dupNames)));
            }

            if ( emptySchema ) {
                error = true;
                addMessage(new Message(Message.Type.Error,
                    MessageKeys.ERR_ALL_SCHEMA_ATTR_REQ_NAME), null);
            }

            if (dupObjectType.size() > 0 ) {
                error = true;
                addMessage(new Message(Message.Type.Error,
                        MessageKeys.ERR_ALL_SCHEMA_ATTR_UNIQUE_OBJECT_TYPE), null);
            }
            
            if(missingAppTier) {
                addMessage(new Message(Message.Type.Error,
                        MessageKeys.ERR_APP_TIER_MISSING), null);    
                error = true; 
            }
        }  // if schemaDTOs != null

        if (!error) {
            List<Schema> schemas = _object.getSchemas();
            // Build a map of the defined schemas keyed by objectType
            Map<String,Schema> currentSchemas = new HashMap<String,Schema>();
            if ( Util.size(schemas) > 0 ) {
                for (Schema schema : schemas) {
                    currentSchemas.put(schema.getObjectType(), schema);
                }
            }

            // Go through the dtos and update any existing schemas
            // and add any that aren't found.
            if ( Util.size(_schemaDTOs) > 0 ) {
                for (SchemaDTO dto : _schemaDTOs) {
                    String type = dto.getObjectType();
                    if ( type != null ) {
                        Schema currentSchema = currentSchemas.get(type);
                        if ( currentSchema != null ) {
                            // let the dto populate the schema with dto contents
                            dto.populate(currentSchema);
                        } else {
                            String objectType = dto.getObjectType();
                            if ( objectType != null ) {
                                // let the dto populate the schema with dto contents
                                Schema newSchema = new Schema();
                                dto.populate(newSchema);
                                currentSchemas.put(dto.getObjectType(), newSchema);
                            }
                        }
                    }
                }
            }

            //
            // Maintain the account, group ordering
            //
            ArrayList<Schema> updatedSchemas = new ArrayList<Schema>();
            if ( currentSchemas != null ) {
                // Keep these in order account, then group
                Schema accountSchema = currentSchemas.remove(Connector.TYPE_ACCOUNT);
                if ( accountSchema != null ) {
                    updatedSchemas.add(accountSchema);
                }
                // We used to pull the group schema off here add it to updatedSchemas. As of 6.4,
                // one may choose to create a schema of object type "group" or object type "whateverUWant".
                // If you replace the order of the objects in the hibernate list NPE errors ensue when there
                // are gaps in the internal hibernate index (named idx in the database).
            }
            //
            // Set the new/updated schemas on the application. Here's were we will now add "group" if one exists
            //
            Collection<Schema> remainingMapValues = ( currentSchemas!= null ) ? currentSchemas.values() : null;
            if ( Util.size(remainingMapValues) > 0 ) {
                updatedSchemas.addAll(remainingMapValues);
            }
            _object.setSchemas(updatedSchemas);
        }
        return error;
    }
    

    private SchemaDTO getSchemaDTO(String objectType) {
        SchemaDTO schemaDTO = null;

        for (SchemaDTO dto : _schemaDTOs) {
            if (objectType.equals(dto.getObjectType())) {
                schemaDTO = dto;
            }
        }

        return schemaDTO;
    }

    private void addSchemaDTO(Schema schema) {
        // Remove the obsolete schema if necessary
        SchemaDTO dtoToReplace = null;
        for (SchemaDTO dto : _schemaDTOs) {
            if (dto.getObjectType().equals(schema.getObjectType())) {
                dtoToReplace = dto;
            }
        }
        if (dtoToReplace != null) {
            _schemaDTOs.remove(dtoToReplace);
        }

        // Add the schema
        _schemaDTOs.add(new SchemaDTO(schema, this));

        // Maintain list order
        Collections.sort(_schemaDTOs, new Comparator<SchemaDTO>(){
            @Override
            public int compare(SchemaDTO o1, SchemaDTO o2) {
                final String type1 = o1.getObjectType();
                final String type2 = o2.getObjectType();
                final int result;

                if (type1 == null) {
                    if (type2 == null) {
                        result = 0;
                    } else {
                        result = -1;
                    }
                } else if (type2 == null) {
                    result = 1;
                } else {
                    result = type1.compareTo(type2);
                }

                return result;
            }
        });
    }

    // djs: can we validate this during save to avoid the overhead
    // of the list in memory ?
    private Set<String> getUsedPolicyNames() throws GeneralException {
        Application app = getObject();

        // Get a list of all the PasswordPolicyHolders of the other apps
        // We need to know when we're sharing a policy with another app
        // so that if someone decides to edit one they know that it is used by another app.
        QueryOptions qo = new QueryOptions();
        // filter out this app
        qo.addFilter(Filter.ne("name", app.getName()));
        qo.setDistinct(true);

        Iterator<Object[]> policies = getContext().search(Application.class, qo, "passwordPolicies.policy");
        Set<String> usedPolicyNames = new HashSet<String>();
        while ( policies.hasNext() ) {
             Object[] row  = policies.next();
             if ( row[0] != null ) {
                 PasswordPolicy policy = (PasswordPolicy)row[0];
                 usedPolicyNames.add(policy.getId());
             }
        }
        return usedPolicyNames;
    }

    public boolean isPolicyNameUsed(String policyName) throws GeneralException {
        boolean used = false;
        QueryOptions ops = new QueryOptions();
        try {
            List<PasswordPolicy> passwordPolicies = getContext().getObjects(PasswordPolicy.class, ops);
            for (PasswordPolicy policy : passwordPolicies) {
                if (policy.getName().equals(policyName)) {
                    used = true;
                    break;
                }
            }
        }
        catch (GeneralException e) {
            addMessage(new Message(Message.Type.Error, "Error while getting PasswordPolicy list"), null);
            return false;
        }

        return used;
    }

    private void initPasswordPolicyList()  throws GeneralException {
        Application app = getObject();

        // Get a list of all the PasswordPolicyHolders of the other apps
        // We need to know when we're sharing a policy with another app
        // so that if someone decides to edit one they know that it is used by another app.
        Set<String> usedPolicyNames = getUsedPolicyNames();

        _passwordPolicyList = new ArrayList<PasswordPolicyHolderBean>();
        List<PasswordPolicyHolder> policyList = app.getPasswordPolicies();
        boolean shared = false;
        if (null != policyList) {
            for (PasswordPolicyHolder policy : policyList) {
                if (usedPolicyNames.contains(policy.getPolicy().getId())) {
                    shared = true;
                }
                else {
                    shared = false;
                }
                _passwordPolicyList.add(new PasswordPolicyHolderBean(policy, app.getName(), shared));
            }
        }
    }

    public List<PasswordPolicyHolderBean> getPasswordPolicyList() throws GeneralException {
        if (_passwordPolicyList == null) {
            initPasswordPolicyList();
        }

        return _passwordPolicyList;
    }

    public void setPasswordPolicySelectList(List<SelectItem> passwordPolicySelectList) {
        this._passwordPolicySelectList = passwordPolicySelectList;
    }

    public Map<String, List<String>> getPolicyDetailsMap() {
        return _policyDetailsMap;
    }

    public void setPolicyDetailsMap(Map<String, List<String>> policyDetailsMap) {
        this._policyDetailsMap = policyDetailsMap;
    }

    public boolean isHasCredentialConfiguration() throws GeneralException {
        CredentialRetriever cr = new CredentialRetriever(getContext());
        return cr.hasConfiguration(getObject());
    }

    public boolean isPasswordPoliciesDefined() {
        if (_passwordPolicyList != null) {
            return (_passwordPolicyList.size() > 0);
        }

        return false;
    }

    public boolean isPasswordPoliciesExist() throws GeneralException {
        if (_passwordPolicySelectList == null) {
            getPasswordPolicySelectList();
        }
        boolean exists = false;
        String value;
        for (SelectItem item : _passwordPolicySelectList) {
            value = (String)item.getValue();
            if (!item.isDisabled() && value != null && value.length() != 0) {
                exists = true;
                break;
            }
        }
        return exists;
    }

    /**
     * List of all configured password policies excluding the ones already configured for this app.
     *
     * @return
     * @throws GeneralException
     */
    public List<SelectItem> getPasswordPolicySelectList() throws GeneralException {

        if (_passwordPolicySelectList == null) {
            Application app = getObject();

            List<PasswordPolicyHolder> policyList = app.getPasswordPolicies();

            // Collect the set of policies already configured for this app
            // dont include these in the select list
            Set<String> policySet = new HashSet<String>();
            for (PasswordPolicyHolder ph : policyList) {
                policySet.add(ph.getPolicyName());
            }

            _passwordPolicySelectList = new ArrayList<SelectItem>();

            QueryOptions qo = new QueryOptions();
            qo.addOrdering("name", true);
            List<PasswordPolicy> policies = getContext().getObjects(PasswordPolicy.class, qo);

            if (policies.size() != 0) {
                _passwordPolicySelectList.add(new SelectItem("", getMessage(MessageKeys.PSWD_POLICY_SELECT_POLICY)));
            }

            _policyDetailsMap = new HashMap<String, List<String>>();

            String policyName;
            for (PasswordPolicy policy : policies) {
                policyName = policy.getName();
                SelectItem item = new SelectItem(policyName, policyName);
                if (policySet.contains(policyName))
                    item.setDisabled(true);

                _passwordPolicySelectList.add(item);
                _policyDetailsMap.put(policyName, policy.convertConstraints());
            }
        }

        return _passwordPolicySelectList;
    }

    public String getDescriptionsJSON() {
        return _descriptionData == null ? "[]" : _descriptionData.getDescriptionsJson();
    }

    public void setDescriptionsJSON(String descriptionsJSON) throws GeneralException {
        if (_descriptionData == null)
            _descriptionData = new DescriptionData("");
        _descriptionData.setDescriptionsJson(descriptionsJSON);
        Map<String, String> descriptionMap = _descriptionData.getDescriptionMap();
        if (Util.isEmpty(descriptionMap)) {
            descriptionMap = null;
        }
        getObject().setDescriptions(descriptionMap);
    }
    
    ///////////////////////////////////////////////////////////////////////////
    //
    // Native Change Detection
    //
    ///////////////////////////////////////////////////////////////////////////
    
    public String getNativeChangeAttributeScope() throws GeneralException {        
        String selection = "entitlements";
        Application app = getObject();
        if ( app != null ) {    
            selection = app.getNativeChangeAttributeScope();
            if ( selection == null  ) {
                selection = "entitlements"; 
                if ( Util.size(app.getNativeChangeAttributes()) > 0 ) {
                    selection = "userDefined";
                }
            }
        } 
        return selection;
    }
    
    public void setNativeChangeAttributeScope(String val) 
        throws GeneralException {
        
        if ( Util.getString(val) != null ) {
            Application app = getObject();
            if ( app != null ) {    
                app.setNativeChangeAttributeScope(val);      
            }        
        }
    }
    
    ///////////////////////////////////////////////////////////////////////////
    //
    // Application Accounts Grid
    //
    ///////////////////////////////////////////////////////////////////////////
    public String getIdentityToView() {
        return identityToView;
    }

    public void setIdentityToView(String identityToView) {
        this.identityToView = identityToView;
    }
    
    public String viewIdentity() {
        NavigationHistory.getInstance().saveHistory((NavigationHistory.Page) this);
        return IdentityDTO.createNavString(Consts.NavigationString.viewIdentity.name(), identityToView);
    }

    @Override
    public String getPageName() {
        return "Application Configuration";
    }

    @Override
    public String getNavigationString() {
        return "editApplication";
    }

    @Override
    public Object calculatePageState() {
        Object[] state = new Object[2];
        state[0] = getObjectId();
        String editStateKey = getEditStateSessionKey();
        state[1] = getSessionScope().get(editStateKey);
        return state;
    }

    @Override
    public void restorePageState(Object state) {
        Object[] myState = (Object[]) state;
        String objectId = (String) myState[0];
        setObjectId(objectId);
        getSessionScope().put(ApplicationListBean.ATT_OBJECT_ID, objectId);
        String editStateKey = getEditStateSessionKey();
        getSessionScope().put(editStateKey, myState[1]);
    }

    public Map<String, List<Map<String,String>>> getIndexColumns() throws GeneralException {
        Map<String, List<Map<String,String>>> map = new HashMap<String, List<Map<String,String>>>();
        
        List<Map<String,String>> accountColumn = getColumns(ACCOUNT_INDEX_COLUMN_DATABASE);
        if ( accountColumn != null && ! accountColumn.isEmpty() ) {
            map.put("", accountColumn);
        }

        for (SchemaDTO dto : _schemaDTOs) {
            if (!Connector.TYPE_ACCOUNT.equals(dto.getObjectType())) {
                List<Map<String, String>> column = getColumns(getIndexColumnDatabase(dto.getObjectType()));
                if (!Util.isEmpty(column)) {
                    map.put(dto.getObjectType(), column);
                }
            }
        }

        return map;
    }
    
    public  Map<String, List<Map<String,String>>> getMergeColumns() throws GeneralException {
        Map<String, List<Map<String,String>>> map = new HashMap<String, List<Map<String,String>>>();
        
        List<Map<String,String>> accountColumn = getColumns(ACCOUNT_MERGE_COLUMN_DATABASE);
        if ( accountColumn != null && ! accountColumn.isEmpty() ) {
            map.put("", accountColumn);
        }

        for (SchemaDTO dto : _schemaDTOs) {
            if (!Connector.TYPE_ACCOUNT.equals(dto.getObjectType())) {
                List<Map<String, String>> column = getColumns(getMergeColumnDatabase(dto.getObjectType()));
                if (!Util.isEmpty(column)) {
                    map.put(dto.getObjectType(), column);
                }
            }
        }

        return map;
    }

    /**
     * The key in the application attribute where merge columns for the objectType will be stored
     * @param schemaObjectType the schema object type
     * @return the key in application attribute where the merge config values will be stored.
     */
    private String getMergeColumnDatabase(String schemaObjectType) {
        String attributePrefix = getAttributePrefix(schemaObjectType);
        return MessageFormat.format(SCHEMA_OBJECT_TYPE_MERGE_COLUMN_DATABASE_TEMPLATE, attributePrefix);
    }

    /**
     * The key in the application attribute where index columns for the objectType will be stored
     * @param schemaObjectType the schema object type
     * @return the key in application attribute where the index config values will be stored.
     */
    private String getIndexColumnDatabase(String schemaObjectType) {
        String attributePrefix = getAttributePrefix(schemaObjectType);
        return MessageFormat.format(SCHEMA_OBJECT_TYPE_INDEX_COLUMN_DATABASE_TEMPLATE, attributePrefix);
    }
    
    /**
     * @param schemaObjectType the schema object type
     * @return either empty string for object type account or the schema object type plus period for
     *         anything other object type.
     */
    private String getAttributePrefix(String schemaObjectType) {
        return (Connector.TYPE_ACCOUNT.equals(schemaObjectType)) ? "" : schemaObjectType + ".";
    }

    @SuppressWarnings("unchecked")
    private List<Map<String,String>> getColumns(String column) throws GeneralException {          

        Application app = getObject();
        Attributes<String, Object> _config = app.getAttributes();
        Object o = _config.get(column);
        List<String> list = Util.asList(o);
        List<Map<String,String>> mlist = new ArrayList<Map<String,String>>();
        Map<String, String> attrMap = null;

        if ( null != list ) {
            for( int i = 0; i < list.size(); i++ ) {
                attrMap = new HashMap<String, String>();
                attrMap.put("id", list.get(i));
                attrMap.put("displayField", list.get(i));
                attrMap.put("displayName", list.get(i));
                mlist.add(attrMap);
            }
        }
        
        return mlist;
    }

    public String getIndexColumnsForAccountJSON() throws GeneralException {

        List<Map<String,String>>  columns = getColumns(ACCOUNT_INDEX_COLUMN_DATABASE);

        return JsonHelper.toJson(columns);
    }
    
    public String getMergeColumnsForAccountJSON() throws GeneralException {
        
        List<Map<String,String>>  columns = getColumns(ACCOUNT_MERGE_COLUMN_DATABASE);

        return JsonHelper.toJson(columns);
    }

    /**
     * @deprecated use {@link #getIndexColumnsForAllSchemaObjectTypesJSON()}
     */
    public String getIndexColumnsForGroupJSON() throws GeneralException {
        return getIndexColumnsForSchemaObjectTypeJSON(Connector.TYPE_GROUP);
    }

    /**
     * @deprecated use {@link #getMergeColumnsForAllSchemaObjectTypesJSON()}
     */
    public String getMergeColumnsForGroupJSON() throws GeneralException {
        return getMergeColumnsForSchemaObjectTypeJSON(Connector.TYPE_GROUP);
    }

    /**
     * This will return json representation of the map of index columns keyed by the schema object type.
     */
    public String getIndexColumnsForAllSchemaObjectTypesJSON() throws GeneralException {
        Map<String, List<Map<String, String>>> map = new HashMap<String, List<Map<String, String>>>();

        for (SchemaDTO dto : _schemaDTOs) {
            if (!Connector.TYPE_ACCOUNT.equals(dto.getObjectType())) {
                map.put(
                        dto.getObjectType(),
                        getColumns(getIndexColumnDatabase(dto.getObjectType())));
            }
        }

        return JsonHelper.toJson(map);
    }

    /**
     * This will return json representation of the map of merge columns keyed by the schema object type.
     */
    public String getMergeColumnsForAllSchemaObjectTypesJSON() throws GeneralException {
        Map<String, List<Map<String, String>>> map = new HashMap<String, List<Map<String, String>>>();

        for (SchemaDTO dto : _schemaDTOs) {
            if (!Connector.TYPE_ACCOUNT.equals(dto.getObjectType())) {
                map.put(
                        dto.getObjectType(),
                        getColumns(getMergeColumnDatabase(dto.getObjectType())));
            }
        }

        return JsonHelper.toJson(map);
    }

    public String getMergeColumnsForSchemaObjectTypeJSON(String schemaObjectType) throws GeneralException {
        List<Map<String,String>>  columns = getColumns(getMergeColumnDatabase(schemaObjectType));

        return JsonHelper.toJson(columns);
    }

    public String getIndexColumnsForSchemaObjectTypeJSON(String schemaObjectType) throws GeneralException {
        List<Map<String,String>>  columns = getColumns(getIndexColumnDatabase(schemaObjectType));

        return JsonHelper.toJson(columns);
    }

    private List<String> getDiscoveredSchemaAttributes(String type) {

        List<String> attrNames = new ArrayList<String>();

        // make sure that all schema items have a name
        Collection<SchemaDTO> schemaDTOs = _schemaDTOs;
       
        if ( schemaDTOs != null ) {
        
            boolean emptySchema = false;
           
            for ( SchemaDTO schema : schemaDTOs ) {
                if ( schema == null ) continue;

                if ( schema.getObjectType().equals(type) ) {
                    List<SchemaAttributeDTO> attrDefs = schema.getAttributes();
                    if ( attrDefs == null ) continue;

                    for ( SchemaAttributeDTO attrDef : attrDefs ) {
                        if ( attrDef == null ) continue;
                        String attrName = attrDef.getName();
                        if ( attrName == null || attrName.length() == 0 )
                            emptySchema = true;                 
                        else
                            attrNames.add(attrName);
                    }
                }
            }
        }
        
        return attrNames;
    }
    
    protected Map<String,Object> getDiscoveredSchema(String objType) {
        
        List<Map<String,String>> list = new ArrayList<Map<String,String>>();
        Map<String, String> attrMap = null;
        List<String> listColumns = getDiscoveredSchemaAttributes(objType);

        int count = listColumns.size();

        for ( int i = 0; i < count; i++ ) {
            attrMap = new HashMap<String, String>();
            String discAttributes = listColumns.get(i);
            attrMap.put("displayName", discAttributes);
            attrMap.put("id", discAttributes);
            list.add(attrMap);
        }
                
        Map<String,Object> ret = new HashMap<String,Object>();
        ret.put("objects", list);
        ret.put("count", list.size());
        
        return ret;
    }

    public String getDiscoveredSchemaJSON() {
        String objectType = super.getRequestParameter("objectType");
        Map<String,Object> ret = getDiscoveredSchema(objectType);

        return JsonHelper.toJson(ret);
    }

    public String getDiscoveredSchemaObjectTypesJSON() {
        Map<String, Map<String, Object>>  map = new HashMap<String, Map<String, Object>>();

        for (SchemaDTO dto : _schemaDTOs) {
            if (!Connector.TYPE_ACCOUNT.equals(dto.getObjectType())) {
                Map<String, Object> schemaMap = getDiscoveredSchema(dto.getObjectType());
                map.put(dto.getObjectType(), schemaMap);
            }
        }

        return JsonHelper.toJson(map);
    }

    public String getSchemaObjectType() {
        return schemaObjectType;
    }

    public void setSchemaObjectType(String val) {
        schemaObjectType = val;
    }
    
    private void saveJDBCMultiSuggestAttributes() throws GeneralException    {
        Application app = getObject();
        boolean isNewApp = isNewApplication(app);
        
        Map<String, Object> requestParams = super.getRequestParam();

        for (SchemaDTO dto : _schemaDTOs) {
                handleJDBCAttributeData(isNewApp, requestParams, getIndexColumnDatabase(dto.getObjectType()), 
                        dto.getObjectType(), SCHEMA_OBJECT_TYPE_INDEX_COLUMN_CONFIG);
                handleJDBCAttributeData(isNewApp, requestParams, getMergeColumnDatabase(dto.getObjectType()), 
                        dto.getObjectType(), SCHEMA_OBJECT_TYPE_MERGE_COLUMN_CONFIG);
        }
    }

    /**
     *
     * @param newApp whether the app is new
     * @param appVariable the attribute where the values need to be saved
     * @param configParam the request parameter from which to read the values
     */
    private void handleJDBCAttributeData(boolean newApp, Map<String, Object> requestParams, String appVariable, 
                                         String objectType, String configParam) {
        if (requestParams == null || configParam == null)
            return;
        
        String data = null;
        
        // In version 6.4 we redesigned the JDBC application UI allowing for multiple application object types (i.e. groups).
        // It made the jsf code more generic but didn't play nice with the extjs multiselect components. Boils down to using 
        // ui:repeat and ui:include to render id elements which are build at "build" time vs. "render" time in the jsf lifecyle.
        // Here we are checking if the request params contain a parameter with the object type AND ends with configParam to
        // create uniqueness.
        for (String key : requestParams.keySet()) {
            if (Util.isNotNullOrEmpty(key) && key.contains(objectType) && key.endsWith(configParam)) {
                data = Util.otos(requestParams.get(key));
                break;
            }
        }
        
        if (newApp) {
            if ( Util.getString(data) != null ) {
                List<String> list = CollectorServices.buildStringList(data, true);
                _object.setAttribute(appVariable, list);
            } 
        }
        else {
            if (Util.isNotNullOrEmpty(data)) {
                List<String> list = CollectorServices.buildStringList(data, true);
                _object.setAttribute(appVariable, list); 
            } else {
                _object.removeAttribute(appVariable);
            }
        }
    }
    
    private void auditPasswordPolicyChange(PasswordPolicy originalPolicy, PasswordPolicy newPolicy) 
            throws GeneralException 
    {
        if (Auditor.isEnabled(AuditEvent.PasswordPolicyChange)) {
            AuditEvent event = new AuditEvent();
            event.setAction(AuditEvent.PasswordPolicyChange);
            event.setSource(getLoggedInUserName());
            event.setInterface(Source.UI.toString());
            if (Util.isNotNullOrEmpty(getObject().getName())) {
                event.setApplication(getObject().getName());
            } else {
                event.setApplication(getObject().getId());
            }
            
            if (originalPolicy != null && newPolicy == null) {
                event.setAttributeName(AuditEvent.ActionDelete);
                event.setAttributeValue(originalPolicy.getName());
            } else if (originalPolicy == null && newPolicy != null) {
                event.setAttributeName(AuditEvent.ActionCreate);
                event.setAttributeValue(newPolicy.getName());
            } else if (originalPolicy != null && newPolicy != null) {
                event.setAttributeName(AuditEvent.ActionUpdate);
                event.setAttributeValue(newPolicy.getName());
            }
    
            if (originalPolicy != null && newPolicy != null) {
                for (int i = 0; i < SystemConfigBean.PASSWORD_POLICY_ATTRIBUTE_NAMES.length; ++i) {
                    SystemConfigBean.addAttributeChangeToAuditEvent(event, 
                            SystemConfigBean.PASSWORD_POLICY_ATTRIBUTE_NAMES[i][0], 
                            SystemConfigBean.PASSWORD_POLICY_ATTRIBUTE_NAMES[i][1], 
                            originalPolicy.getPasswordConstraints(), 
                            newPolicy.getPasswordConstraints());
                }
    
                for (int i = 0; i < APPLICATION_ONLY_PASSWORD_POLICY_ATTRIBUTE_NAMES.length; ++i) {
                    SystemConfigBean.addAttributeChangeToAuditEvent(event, 
                            APPLICATION_ONLY_PASSWORD_POLICY_ATTRIBUTE_NAMES[i][0], 
                            APPLICATION_ONLY_PASSWORD_POLICY_ATTRIBUTE_NAMES[i][1], 
                            originalPolicy.getPasswordConstraints(), 
                            newPolicy.getPasswordConstraints());
                }
                
                if (!Util.nullSafeEq(originalPolicy.getName(), newPolicy.getName())) {
                    event.setAttribute(
                            MessageKeys.PSWD_POLICY_NAME, 
                            new Message(Message.Type.Info, MessageKeys.AUDIT_ACTION_PASSWORD_POLICY_VALUE_UPDATE, 
                                    originalPolicy.getName(), newPolicy.getName()));
                }
                if (!Util.nullSafeEq(originalPolicy.getDescription(), newPolicy.getDescription())) {
                    event.setAttribute(
                            MessageKeys.PSWD_POLICY_DESCR, 
                            new Message(Message.Type.Info, MessageKeys.AUDIT_ACTION_PASSWORD_POLICY_VALUE_UPDATE, 
                                    originalPolicy.getDescription(), newPolicy.getDescription()));
                }
            } else if (originalPolicy != null) {
                addNewOrRemovedAttributeToAuditEvent(event, originalPolicy,
                        MessageKeys.AUDIT_ACTION_PASSWORD_POLICY_VALUE_REMOVED);
            } else if (newPolicy != null) {
                addNewOrRemovedAttributeToAuditEvent(event, newPolicy,
                        MessageKeys.AUDIT_ACTION_PASSWORD_POLICY_VALUE_ADDED);
            }
            
            Auditor.log(event);
        }        
    }
    
    private void addNewOrRemovedAttributeToAuditEvent(
                       AuditEvent event, 
                       PasswordPolicy policy, 
                       String actionKey) 
    {
        Map<String,Object> policyConstrains = policy.getPasswordConstraints();
        for (int i = 0; i < SystemConfigBean.PASSWORD_POLICY_ATTRIBUTE_NAMES.length; ++i) {
            String value = Util.getString(policyConstrains, SystemConfigBean.PASSWORD_POLICY_ATTRIBUTE_NAMES[i][0]);
            if (Util.isNotNullOrEmpty(value) && !"false".equalsIgnoreCase(value)) {
                event.setAttribute(
                        SystemConfigBean.PASSWORD_POLICY_ATTRIBUTE_NAMES[i][1], 
                        new Message(Message.Type.Info, actionKey, value));
            }
        }

        for (int i = 0; i < APPLICATION_ONLY_PASSWORD_POLICY_ATTRIBUTE_NAMES.length; ++i) {
            String value = Util.getString(policyConstrains, APPLICATION_ONLY_PASSWORD_POLICY_ATTRIBUTE_NAMES[i][0]);
            if (Util.isNotNullOrEmpty(value) && !"false".equalsIgnoreCase(value)) {
                event.setAttribute(
                        APPLICATION_ONLY_PASSWORD_POLICY_ATTRIBUTE_NAMES[i][1], 
                        new Message(Message.Type.Info, actionKey, value));
            }
        }
    }

    /**
     * Return a list of SelectItems for the Schema Attribute Definition Type selector. This will contain the
     * primitive types defined in {@link sailpoint.object.AttributeDefinition.AttributeType} as well as any object
     * types of schemas defined other than account
     * @return List of SelectItems for the Schema Attribute Definition Type selector
     * @throws GeneralException
     */
    public List<SelectItem> getSchemaAttributeTypes() throws GeneralException {

        List<SelectItem> schemaAttrTypes = new ArrayList<SelectItem>();
        //Add the default primitive types
        for (AttributeDefinition.AttributeType type : AttributeDefinition.AttributeType.values()) {
            schemaAttrTypes.add(new SelectItem(type.getValue(), type.getDisplayName()));
        }

        //Add schema objectTypes
        for(SchemaDTO schema : Util.safeIterable(this.getSchemaDTOs())) {
            //Don't add account objectType
            if (!schema.getObjectType().equals(Application.SCHEMA_ACCOUNT)) {
                schemaAttrTypes.add(new SelectItem(schema.getObjectType(), schema.getObjectType()));
            }
        }

        return schemaAttrTypes;
    }
    
    
    
    
    /**
     * Return a list of SelectItems for the Schema Attribute Definition Type selector. This will contain the
     * primitive types defined in {@link sailpoint.object.AttributeDefinition.AttributeType} as well as any object
     * types of schemas defined other than account
     * @return List of SelectItems for the Schema Attribute Definition Type selector
     * @throws GeneralException
     */
    public List<SelectItem> getSchemaObjectTypes() throws GeneralException {

        List<SelectItem> schemaObjectTypes = new ArrayList<SelectItem>();

        //Add schema objectTypes
        for(SchemaDTO schema : Util.safeIterable(this.getSchemaDTOs())) {
            //Don't add account objectType
            if (!schema.getObjectType().equals(Application.SCHEMA_ACCOUNT)) {
            	schemaObjectTypes.add(new SelectItem(schema.getObjectType(), schema.getObjectType()));
            }
        }

        return schemaObjectTypes;
    }

    /**
     * Creates '|' separted list of object types
     */
    private void getGroupObjectTypesAsString() throws GeneralException {
        String jsonObj = "";
        //Add schema objectTypes
        for(SchemaDTO schema : Util.safeIterable(this.getSchemaDTOs())) {
            //Don't add account objectType
            if (Util.isNotNullOrEmpty(schema.getObjectType()) &&
                    !Util.nullSafeEq(schema.getObjectType(), Application.SCHEMA_ACCOUNT)) {
                jsonObj = jsonObj + schema.getObjectType() + "|";
            }
        }
        if (Util.isNotNullOrEmpty(jsonObj)) {
            groupObjectTypes = jsonObj.substring(0,jsonObj.length()-1);
        }
    }

    /**
     * Get the Default Application By Template. This gets the default application from the connector registry
     * @return Default application from connector registry
     * @throws GeneralException
     */
    protected Application getDefaultApplication() throws GeneralException {
        if (_defaultApp == null) {
            Application app = getObject();
            if (app != null) {
                String appKey = app.getTemplateApplication();
                // Default to type here in-case its and old ConnectorConfig
                // based connector.
                if ( appKey == null ) {
                    appKey = app.getType();
                }
                _defaultApp = DefaultApplicationFactory.getDefaultApplicationByTemplate(appKey);
            }
        }
        return _defaultApp;
    }

    /**
     * Get a list of schema object Types that support group hierarchy. This looks at the connector registry for the
     * given application to see if we have group hierarchy set by default. If it is set by default in the connectorRegistry,
     * we will allow the user to set/edit it via the UI
     * @return List of schema object types that support group hierarchy
     * @throws GeneralException
     */
    public List<String> getGroupHierarchySchemaList() throws GeneralException {
        List<String> schemas = new ArrayList<String>();
        if (getDefaultApplication() != null) {
            for(Schema s : Util.safeIterable(getDefaultApplication().getSchemas())) {
                if (s.getHierarchyAttribute() != null) {
                    schemas.add(s.getObjectType());
                }
            }
        }
        return schemas;
    }

    /**
     * Get a list of schema object types that support membership on the group itself. This is driven from the connector
     * registry. If the default template supports group membership on the group, we will allow set/edit via the UI
     * @return List of schema object types that support group membership
     * @throws GeneralException
     */
    public List<String> getGroupMemberAttrSchemaList() throws GeneralException {
        List<String> schemas = new ArrayList<String>();
        if (getDefaultApplication() != null) {
            for(Schema s : Util.safeIterable(getDefaultApplication().getSchemas())) {
                if (s.getStringAttributeValue(Application.ATTR_GROUP_MEMBER_ATTRIBUTE) != null) {
                    schemas.add(s.getObjectType());
                }
            }
        }
        return schemas;
    }
    
    /**
     * Return the names of all PeopleSoft buildMap rules, suitable for use
     * in a selectOneMenu.
     */
    public List<SelectItem> getPeopleSoftBuildMapRules() throws GeneralException {
        if ( _peopleSoftBuildMapRules == null) {
            _peopleSoftBuildMapRules = getConfigBasedRules(Rule.Type.PeopleSoftHRMSBuildMap, Connector.CONFIG_BUILD_MAP_RULE);
        }
        return _peopleSoftBuildMapRules;
    }

    /**
     * Return a map of searchScope attributes and values for Top Secret LDAP
     * @return
     * @throws GeneralException
     */
    private Map<String,Object> getTSSSearchScope() throws GeneralException {
        Map<String, Object> data = null;
        Application app = getObject();
        if ( app != null ) {
            Attributes<String,Object> attrs = app.getAttributes();
            if (!Util.isEmpty(attrs)) {
                List accountDataList = (List) attrs.getMap().get(AD_ATT_SEARCH_DNS);
                if (!Util.isEmpty(accountDataList)) {
                    data = (Map<String, Object>) accountDataList.get(0);
                }
            }
        }
        return (null == data) ? new HashMap<String, Object>() : data;
    }

    /**
     * Save searchscope for Top Secret LDAP in following format
     * <entry key="searchDNs">
     *   <value>
     *     <List>
     *       <Map>
     *         <entry key="iterateSearchFilter value="tssacid=*"/>
     *         <entry key="searchDN" value="host=SYSB,o=SAILPOINT,c=us"/>
     *       </Map>
     *     </List>
     *   </value>
     * </entry>
     * @param key
     * @param value
     * @throws GeneralException
     */
    private void setTSSSearchScope(String key, String value) throws GeneralException {
        Application app = getObject();
        if (app != null) {
            if (AD_ATT_SEARCH_DN.equalsIgnoreCase(key) && Util.isNullOrEmpty(value)) {
                throwWhenNotConfigured("Suffix is mandatory");
            }
            Attributes<String, Object> attrs = app.getAttributes();
            Map map = attrs.getMap();
            Map<String, Object> data = null;
            List accountDataList = (List) map.get(AD_ATT_SEARCH_DNS);
            if (Util.isEmpty(accountDataList)) {
                accountDataList = new ArrayList();
                data = new HashMap<String, Object>();
                accountDataList.add(data);
            } else {
                data = (Map<String, Object>) accountDataList.get(0);
            }
            data.put(key, value);
            map.put(AD_ATT_SEARCH_DNS, accountDataList);
            attrs.setMap(map);
        }
    }

    /**
     * Get the searchDN for Top Secret LDAP from searchscope
     * @return
     * @throws GeneralException
     */
    public String getTSSSearchDN() throws GeneralException {
        String searchDN = "";
        Map<String, Object> data = getTSSSearchScope();
        if (!Util.isEmpty(data)) { searchDN = (String) data.get(AD_ATT_SEARCH_DN); }
        return searchDN;
    }

    /**
     * Set the searchDN for Top Secret LDAP in searchscope
     * @param searchDN
     */
    public void setTSSSearchDN(String searchDN) {
        try {
            setTSSSearchScope(AD_ATT_SEARCH_DN, searchDN);
        } catch (GeneralException e) {
            log.error("Unbale to Modify Application ", e);
            addMessage(new Message(Message.Type.Error, MessageKeys.ERR_APP_SAVE_FAILED, e), null);
        }
    }

    /**
     * Get the iterateSearchFilter for Top Secret LDAP accounts from searchscope
     * @return
     * @throws GeneralException 
     */
    public String getTSSAccountFilter() throws GeneralException {
        String filter = "";
        Map<String, Object> data = getTSSSearchScope();
        if (!Util.isEmpty(data)) { filter = (String) data.get(AD_ATT_ITERATE_SEARCH_FILTER); }
        return filter;
    }

    /**
     * Set the iterateSearchFilter for Top Secret LDAP accounts in searchscope
     * @param filter
     */
    public void setTSSAccountFilter(String filter) {
        try {
            setTSSSearchScope(AD_ATT_ITERATE_SEARCH_FILTER, filter);
        } catch (GeneralException e) {
            log.error("Unbale to Modify Application ", e);
            addMessage(new Message(Message.Type.Error, MessageKeys.ERR_APP_SAVE_FAILED, e), null);
        }
    }

    /**
     * Getter for property 'IQServiceHost' on iqserviceconfig.xhtml.
     * 
     * @return String IQServiceHost
     * @throws GeneralException
     */
    public String getIQServiceHost() throws GeneralException {
        String host = EMPTY_STRING;
        Map<String, Object> data = getIQServiceInfo();
        if (!Util.isEmpty(data)) {
            host = (String) data.get(ATT_IQSERVICE_HOST);
        }
        return host;
    }

    /**
     * Setter for property 'IQServiceHost' on iqserviceconfig.xhtml.
     * 
     * @param host
     */
    public void setIQServiceHost(String host) {
        try {
            setIQServiceInfo(ATT_IQSERVICE_HOST, host);
        } catch (GeneralException e) {
            log.error("Unable to Modify Application ", e);
            addMessage(new Message(Message.Type.Error, MessageKeys.ERR_APP_SAVE_FAILED, e), null);
        }
    }

    /**
     * Getter for property 'IQServicePort' on iqserviceconfig.xhtml.
     * 
     * @return String IQServicePort
     * @throws GeneralException
     */
    public String getIQServicePort() throws GeneralException {
        String port = EMPTY_STRING;
        Map<String, Object> data = getIQServiceInfo();
        if (!Util.isEmpty(data)) {
            port = (String) data.get(ATT_IQSERVICE_PORT);
        }
        return port;
    }

    /**
     * Setter for property 'IQServicePort' on iqserviceconfig.xhtml.
     * 
     * @param port
     */
    public void setIQServicePort(String port) {
        try {
            setIQServiceInfo(ATT_IQSERVICE_PORT, port);
        } catch (GeneralException e) {
            log.error("Unable to Modify Application ", e);
            addMessage(new Message(Message.Type.Error, MessageKeys.ERR_APP_SAVE_FAILED, e), null);
        }
    }
    
    /**
     * Setter for property 'IQServiceUser' on iqserviceconfig.xhtml.
     * 
     * @param user
     */
    public void setIQServiceUser(String user) {
        try {
            setIQServiceInfo(ATT_IQSERVICE_USER, user);
        } catch (GeneralException e) {
            log.error("Unable to Modify Application ", e);
            addMessage(new Message(Message.Type.Error, MessageKeys.ERR_APP_SAVE_FAILED, e), null);
        }
    }
    
    /**
     * Getter for property 'IQServiceUser' on iqserviceconfig.xhtml.
     * 
     * @return String IQServiceUser
     * @throws GeneralException
     */
    public String getIQServiceUser() throws GeneralException {
        String user = EMPTY_STRING;
        Map<String, Object> data = getIQServiceInfo();
        if (!Util.isEmpty(data)) {
            user = (String) data.get(ATT_IQSERVICE_USER);
        }
        return user;
    }
    
    /**
     * Setter for property 'IQServicePassword' on iqserviceconfig.xhtml.
     * 
     * @param pwd
     */
    public void setIQServicePassword(String pwd) {
        try {
            setIQServiceInfo(ATT_IQSERVICE_PASSWORD, pwd);
        } catch (GeneralException e) {
            log.error("Unable to Modify Application ", e);
            addMessage(new Message(Message.Type.Error, MessageKeys.ERR_APP_SAVE_FAILED, e), null);
        }
    }
    
    /**
     * Getter for property 'IQServicePassword' on iqserviceconfig.xhtml.
     * 
     * @return String IQServicePassword
     * @throws GeneralException
     */
    public String getIQServicePassword() throws GeneralException {
        String pwd = EMPTY_STRING;
        Map<String, Object> data = getIQServiceInfo();
        if (!Util.isEmpty(data)) {
        pwd = (String) data.get(ATT_IQSERVICE_PASSWORD);
        }
        return pwd;
    }

    /**
     * Getter for property 'useTLSForIQService' on iqserviceconfig.xhtml.
     * 
     * @return boolean TLS Flag for IQService
     * @throws GeneralException
     */
    public boolean isUseTLSForIQService() throws GeneralException {
        boolean useTLS = false;
        Map<String, Object> data = getIQServiceInfo();
        if (!Util.isEmpty(data) && data.get(ATT_USE_TLS_FOR_IQSERVICE) != null) {
            useTLS = (boolean) data.get(ATT_USE_TLS_FOR_IQSERVICE);
        }
        return useTLS;
    }

    /**
     * Setter for property 'useTLSForIQService' on iqserviceconfig.xhtml.
     * 
     * @param useTLS
     */
    public void setUseTLSForIQService(boolean useTLS) {
        try {
            setIQServiceInfo(ATT_USE_TLS_FOR_IQSERVICE, useTLS);
        } catch (GeneralException e) {
            log.error("Unable to Modify Application ", e);
            addMessage(new Message(Message.Type.Error, MessageKeys.ERR_APP_SAVE_FAILED, e), null);
        }
    }

    /**
     * Save IQServiceINfo in following format. <entry key="IQServiceConfiguration"> <value> <List> <Map> <entry
     * key="IQServiceHost" value="iqservice.machine"/> <entry key="IQServicePort" value="5050"/> <entry
     * key="useTLSForIQServcie"> <value> <Boolean>true</Boolean> </value> </entry> </Map> </List> </value> </entry>
     * 
     * @param key
     * @param value
     * @throws GeneralException
     */
    private void setIQServiceInfo(String key, Object value) throws GeneralException {
        Application app = getObject();
        if (app != null) {

            Attributes<String, Object> attrs = app.getAttributes();
            Map map = attrs.getMap();
            Map<String, Object> data = null;
            List iqserviceInfo = (List) map.get(ATT_IQSERVICE_CONFIGURATION);
            if (Util.isEmpty(iqserviceInfo)) {
                iqserviceInfo = new ArrayList();
                data = new HashMap<String, Object>();
                iqserviceInfo.add(data);
            } else {
                data = (Map<String, Object>) iqserviceInfo.get(0);
            }
            data.put(key, value);
            map.put(ATT_IQSERVICE_CONFIGURATION, iqserviceInfo);
            attrs.setMap(map);
            attrs.remove(key);
        }
    }

    /**
     * Getter for IQServiceInfo containing all IQService related options.
     * 
     * @return
     * @throws GeneralException
     */
    private Map<String, Object> getIQServiceInfo() throws GeneralException {
        Map<String, Object> data = null;
        Application app = getObject();
        if (app != null) {
            Attributes<String, Object> attrs = app.getAttributes();
            if (!Util.isEmpty(attrs)) {
                List iqserviceInfo = (List) attrs.getMap().get(ATT_IQSERVICE_CONFIGURATION);
                if (!Util.isEmpty(iqserviceInfo)) {
                    data = (Map<String, Object>) iqserviceInfo.get(0);
                } else {
                    data = new HashMap<String, Object>();
                    data.put(ATT_IQSERVICE_HOST, attrs.getString(ATT_IQSERVICE_HOST));
                    data.put(ATT_IQSERVICE_PORT, attrs.getString(ATT_IQSERVICE_PORT));
                    data.put(ATT_IQSERVICE_USER, attrs.getString(ATT_IQSERVICE_USER));
                    data.put(ATT_IQSERVICE_PASSWORD, attrs.getString(ATT_IQSERVICE_PASSWORD));
                    data.put(ATT_USE_TLS_FOR_IQSERVICE, attrs.getBoolean(ATT_USE_TLS_FOR_IQSERVICE));
                }
            }
        }
        return (null == data) ? new HashMap<String, Object>() : data;
    }
    public boolean isMaintenanceEnabled() {
        return maintenanceEnabled;
    }

    public void setMaintenanceEnabled(boolean maintenanceEnabled) {

        this.maintenanceEnabled = maintenanceEnabled;
        addEditState(MAINT_ENABLED_STATE, this.maintenanceEnabled);
    }

    public Date getMaintenanceExpiration() {
        return maintenanceExpiration;
    }

    public void setMaintenanceExpiration(Date maintenanceExpiration) {
        this.maintenanceExpiration = maintenanceExpiration;
        addEditState(MAINT_EXPIRATION_STATE, this.maintenanceExpiration);
    }

    public void validateMaintenanceExpiration(FacesContext context, UIComponent component, Object value)
        throws ValidatorException {
        if (value == null) {
            //Null value OK
            return;
        }
        Date d = (Date)value;
        if (d.before(new Date())) {
            FacesMessage msg = new FacesMessage();
            msg.setSeverity(FacesMessage.SEVERITY_ERROR);
            throw new ValidatorException(msg);
        }
    }
    
    public String getWebServicesConnectorVersion() throws GeneralException {
        return Util.otos(getObject().getAttributeValue("version"));
    }
    
    public String getWebServicesAuthType() throws GeneralException {
        return Util.otos(getObject().getAttributeValue("authenticationMethod"));
    }

} // class ApplicationObjectBean
