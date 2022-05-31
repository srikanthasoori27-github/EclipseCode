/* (c) Copyright 2011 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.web.lcm;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.faces.model.SelectItem;

import org.apache.commons.collections4.ListUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.ObjectUtil;
import sailpoint.api.SailPointContext;
import sailpoint.object.Application;
import sailpoint.object.Configuration;
import sailpoint.object.Filter;
import sailpoint.object.Form;
import sailpoint.object.Form.Usage;
import sailpoint.object.ProvisioningPlan.AccountRequest.Operation;
import sailpoint.object.QueryOptions;
import sailpoint.object.Rule;
import sailpoint.object.ServiceDefinition;
import sailpoint.object.Workflow;
import sailpoint.server.FullTextService;
import sailpoint.service.LinkService;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Message.Type;
import sailpoint.tools.Util;
import sailpoint.web.BaseBean;
import sailpoint.web.SelectOptionBean;
import sailpoint.web.identity.IdentityPolicyBean;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.util.WebUtil;

/**
 * JSF bean for editing the LCM config.
 *
 * @author peter.holcomb
 */
public class LCMConfigBean extends BaseBean {

    private static Log log = LogFactory.getLog(LCMConfigBean.class);

    ////////////////////////////////////////////////////////////////////////////
    //
    // CONSTANTS
    //
    ////////////////////////////////////////////////////////////////////////////

    private static final String REQUEST_PERMITTED_FOR_SELF =
            Configuration.LCM_REQUEST_ROLES_PREFIX + Configuration.LCM_REQUEST_ROLES_PERMITTED + Configuration.LCM_SELF + Configuration.LCM_OP_ENABLED_SUFFIX;
    private static final String REQUEST_PERMITTED_FOR_OTHERS =
            Configuration.LCM_REQUEST_ROLES_PREFIX + Configuration.LCM_REQUEST_ROLES_PERMITTED + Configuration.LCM_OTHERS + Configuration.LCM_OP_ENABLED_SUFFIX;
    private static final String REQUEST_ASSIGNABLE_FOR_SELF =
            Configuration.LCM_REQUEST_ROLES_PREFIX + Configuration.LCM_REQUEST_ROLES_ASSIGNABLE + Configuration.LCM_SELF + Configuration.LCM_OP_ENABLED_SUFFIX;
    private static final String REQUEST_ASSIGNABLE_FOR_OTHERS =
            Configuration.LCM_REQUEST_ROLES_PREFIX + Configuration.LCM_REQUEST_ROLES_ASSIGNABLE + Configuration.LCM_OTHERS + Configuration.LCM_OP_ENABLED_SUFFIX;

    public static final String SELF_SERVICE_REGISTRATION_REDIRECT_PATH = "selfServiceRegistrationRedirectPath";

    private static final String FULL_TEXT_SERVICE_DEFINITION = "FullText";


    ////////////////////////////////////////////////////////////////////////////
    //
    // FIELDS
    //
    ////////////////////////////////////////////////////////////////////////////

    // Business Processes
    private Map<String, String> workflowMappings;
    private List <SelectItem> provisioningWorkflows;
    private List <SelectItem> identityWorkflows;

    private Map<String, String> batchWorkflowMappings;
    private List <SelectItem> batchProvisioningWorkflows;
    private List <SelectItem> selfServiceRegistrationWorkflows;
    private List <SelectItem> batchIdentityWorkflows;

    // Additional Options
    private Map <String, Object> requestRolesOptions;
    private Map <String, SelectOptionBean> requestRolesActions;
    private Map <String, Object> requestEntitlementsOptions;
    private boolean requirePasswordIdentityCreate;
    private boolean enableSelfServiceRegistration;
    private String selfServiceRegistrationRedirectPath;
    private List<ManageAccountAction> manageAccountActions;
    private boolean generatePasswordDelegated;
    private boolean enableApprovalRecommendations;
    private boolean lcmManageAccountsShowAllButtons;
    private boolean lcmManageAccountsAllowGroupManagement;
    private List<String> additionalAccountApplicationIds;
    private Boolean additionalAccountAllApplications;
    private List<String> disableAutoRefAccountStatusAppsIds;
    private Boolean disableAutoRefreshAccountStatus;
    private List<String> accountOnlyApplicationIds;
    private Boolean accountOnlyAllApplications;
    private String lcmSearchType;
    private Boolean allowPriorityEditing;

    private String passwordValidationRule;

    private String batchRequestApprover;

    private boolean requireBatchRequestApproval;

    private boolean displayClassificationsInAccessRequest;

    private boolean enableFulltext;
    private String fullTextIndexPath;
    private boolean enableAutoExecutionMode;
    private int executionModeInterval;

    private boolean allowPopulationSearch;
    private boolean allowIdentitySearch;

    private int searchMaxResults;
    private int lcmMobileMaxSelectableUsers;
    private int useByDays;

    private boolean showExternalTicketId;

    private IdentityPolicyBean identityPolicy;

    ////////////////////////////////////////////////////////////////////////////
    //
    // CONSTRUCTORS
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * @throws GeneralException
     */
    public LCMConfigBean() throws GeneralException {
        super();
        Configuration systemConfig = Configuration.getSystemConfig();

        initWorkflowMappings(systemConfig);

        initBatchWorkflowMappings(systemConfig);

        initRequestRolesOptions(systemConfig);
        initRequestRolesActions(systemConfig);
        initRequestEntitlementsOptions(systemConfig);
        allowPriorityEditing = Util.otob(systemConfig.getBoolean(Configuration.LCM_ALLOW_PRIORITY_EDITING));
        generatePasswordDelegated = Util.otob(systemConfig.getBoolean(Configuration.LCM_ALLOW_GENERATE_PASSWORD_DELEGATION));
        enableApprovalRecommendations = Util.otob(systemConfig.getBoolean(Configuration.LCM_ENABLE_APPROVAL_RECOMMENDATIONS));
        requirePasswordIdentityCreate = Util.otob(systemConfig.getBoolean("lcmRequirePasswordIdentityCreate"));
        enableSelfServiceRegistration = Util.otob(systemConfig.getBoolean("enableSelfServiceRegistration"));
        selfServiceRegistrationRedirectPath = systemConfig.getString(SELF_SERVICE_REGISTRATION_REDIRECT_PATH);
        lcmManageAccountsShowAllButtons = Util.otob(systemConfig.getBoolean(Configuration.LCM_MANAGE_ACCOUNTS_SHOW_ALL_BUTTONS));
        lcmManageAccountsAllowGroupManagement = Util.otob(systemConfig.getBoolean(Configuration.LCM_MANAGE_ACCOUNTS_ALLOW_GROUP_MANAGEMENT));
        enableFulltext = Util.otob(systemConfig.getBoolean(Configuration.LCM_ENABLE_FULLTEXT));

        fullTextIndexPath = systemConfig.getString(Configuration.LCM_FULL_TEXT_INDEX_PATH);

        initFullTextServiceDefinitionArgs();
        allowPopulationSearch = Util.otob(systemConfig.getBoolean(Configuration.LCM_ALLOW_POPULATION_SEARCH));
        allowIdentitySearch = Util.otob(systemConfig.getBoolean(Configuration.LCM_ALLOW_IDENTITY_SEARCH));
        searchMaxResults = Util.otoi(systemConfig.getInt(Configuration.LCM_SEARCH_MAX_RESULTS));
        lcmMobileMaxSelectableUsers = Util.otoi(systemConfig.getInt(Configuration.LCM_MOBILE_MAX_SELECTABLE_USERS));
        showExternalTicketId = Util.otob(systemConfig.getBoolean(Configuration.LCM_SHOW_EXTERNAL_TICKET_ID));
        useByDays = systemConfig.getInt(Configuration.LCM_CREATE_IDENTITY_USE_BY_DAYS);
        initManageAccountActions(systemConfig);
        initDisableAutoRefreshAcctStatusApplications(systemConfig);
        initAdditionalAccountApplications();
        initAccountOnlyApplications();
        passwordValidationRule = systemConfig.getString(Configuration.PASSWORD_VALIDATION_RULE);
        lcmSearchType = systemConfig.getString(Configuration.LCM_SEARCH_TYPE);
        batchRequestApprover = systemConfig.getString(Configuration.BATCH_REQUEST_APPROVER);
        requireBatchRequestApproval = systemConfig.getBoolean(Configuration.REQUIRE_BATCH_REQUEST_APPROVAL, false);
        displayClassificationsInAccessRequest = systemConfig.getBoolean(Configuration.DISPLAY_CLASSIFICATIONS_IN_ACCESS_REQUEST, false);
    }


    ////////////////////////////////////////////////////////////////////////////
    //
    // INITIALIZATION
    //
    ////////////////////////////////////////////////////////////////////////////

    private void initFullTextServiceDefinitionArgs() throws GeneralException {
        ServiceDefinition fullTextDef = getFullTextServiceDefinition();
        if (fullTextDef != null) {
            String executionMode = fullTextDef.getString(FullTextService.ARG_EXECUTION_MODE);

            enableAutoExecutionMode = FullTextService.MODE_AUTOMATIC.equals(executionMode);

            // stored in seconds but configured in minutes so divide by 60
            executionModeInterval = fullTextDef.getInterval() / 60;
        }
    }

    private ServiceDefinition getFullTextServiceDefinition() throws GeneralException {
        return getContext().getObjectByName(ServiceDefinition.class, FULL_TEXT_SERVICE_DEFINITION);
    }

    @SuppressWarnings("unchecked")
    private void initManageAccountActions(Configuration systemConfig) {
        manageAccountActions = new ArrayList<ManageAccountAction>();
        List<Operation> manageAccountOperations = (List<Operation>) systemConfig.getList(Configuration.LCM_MANAGE_ACCOUNTS_ACTIONS);
        if (manageAccountOperations != null && !manageAccountOperations.isEmpty()) {
            for (Operation manageAccountOperation : manageAccountOperations) {
                final String opName = manageAccountOperation.name();
                final boolean isSelfEnabled = Util.otob(systemConfig.getBoolean("manageAccounts" + opName + "SelfEnabled"));
                final boolean isOthersEnabled = Util.otob(systemConfig.getBoolean("manageAccounts" + opName + "SubordinateEnabled"));
                manageAccountActions.add(new ManageAccountAction(opName, isSelfEnabled, isOthersEnabled));
            }
        }
    }

    private void initRequestRolesActions(Configuration systemConfig) throws GeneralException{
        requestRolesActions = new HashMap<String, SelectOptionBean>();
        requestRolesActions.put(REQUEST_PERMITTED_FOR_SELF, new SelectOptionBean(REQUEST_PERMITTED_FOR_SELF, Configuration.LCM_SELF, Util.otob(systemConfig.getBoolean(REQUEST_PERMITTED_FOR_SELF))));
        requestRolesActions.put(REQUEST_PERMITTED_FOR_OTHERS, new SelectOptionBean(REQUEST_PERMITTED_FOR_OTHERS, Configuration.LCM_OTHERS, Util.otob(systemConfig.getBoolean(REQUEST_PERMITTED_FOR_OTHERS))));
        requestRolesActions.put(REQUEST_ASSIGNABLE_FOR_SELF, new SelectOptionBean(REQUEST_ASSIGNABLE_FOR_SELF, Configuration.LCM_SELF, Util.otob(systemConfig.getBoolean(REQUEST_ASSIGNABLE_FOR_SELF))));
        requestRolesActions.put(REQUEST_ASSIGNABLE_FOR_OTHERS, new SelectOptionBean(REQUEST_ASSIGNABLE_FOR_OTHERS, Configuration.LCM_SELF, Util.otob(systemConfig.getBoolean(REQUEST_ASSIGNABLE_FOR_OTHERS))));
    }

    private void initWorkflowMappings(Configuration systemConfig) {
        final String[] workflowOptions = {
                Configuration.WORKFLOW_LCM_ACCESS_REQUEST,
                Configuration.WORKFLOW_LCM_ROLES_REQUEST,
                Configuration.WORKFLOW_LCM_ENTITLEMENTS_REQUEST,
                Configuration.WORKFLOW_LCM_ACCOUNTS_REQUEST,
                Configuration.WORKFLOW_LCM_UNLOCK_ACCOUNT,
                Configuration.WORKFLOW_LCM_PASSWORD_REQUEST,
                Configuration.WORKFLOW_LCM_IDENTITY_EDIT_REQUEST,
                Configuration.WORKFLOW_LCM_CREATE_IDENTITY_REQUEST,
                Configuration.WORKFLOW_LCM_SSR_REQUEST
        };

        workflowMappings = new HashMap<String, String>();
        Map<String, Object> systemAttributes = systemConfig.getAttributes();
        for (int i = 0; i < workflowOptions.length; ++i) {
            String option = workflowOptions[i];
            String workflow = (String) systemAttributes.get(option);
            workflowMappings.put(option, workflow);
        }
    }

    private void initBatchWorkflowMappings(Configuration systemConfig) {
        final String[] workflowOptions = {
                "batchRequestAccessRequest",
                "batchRequestRolesRequest",
                "batchRequestEntitlementsRequest",
                "batchRequestAccountsRequest",
                "batchRequestPasswordsRequest",
                "batchRequestIdentityEditRequest",
                "batchRequestIdentityCreateRequest"
        };
        batchWorkflowMappings = new HashMap<String, String>();
        Map<String, Object> systemAttributes = systemConfig.getAttributes();
        for (int i = 0; i < workflowOptions.length; ++i) {
            String option = workflowOptions[i];
            String workflow = (String) systemAttributes.get(option);
            batchWorkflowMappings.put(option, workflow);
        }
    }

    public void initRequestRolesOptions(Configuration systemConfig) {
        requestRolesOptions = new HashMap<String, Object>();
        requestRolesOptions.put("lcmUseRoleDetailsPage", Util.otob(systemConfig.getBoolean("lcmUseRoleDetailsPage")));
        requestRolesOptions.put("lcmAllowRequestPermits", Util.otob(systemConfig.getBoolean("lcmAllowRequestPermits")));
        requestRolesOptions.put("lcmAllowPercentLimitRoles", Util.otob(systemConfig.getBoolean("lcmAllowPercentLimitRoles")));
        requestRolesOptions.put("lcmAllowPercentLimitRolesPercent", Util.otoi(systemConfig.getInt("lcmAllowPercentLimitRolesPercent")));
        requestRolesOptions.put(Configuration.LCM_ALLOW_ROLE_POPULATION_SEARCH, Util.otob(systemConfig.getBoolean(Configuration.LCM_ALLOW_ROLE_POPULATION_SEARCH)));
        requestRolesOptions.put(Configuration.LCM_ALLOW_ROLE_ATTRIBUTES_SEARCH, Util.otob(systemConfig.getBoolean(Configuration.LCM_ALLOW_ROLE_ATTRIBUTES_SEARCH)));
        requestRolesOptions.put(Configuration.LCM_ALLOW_ROLE_IDENTITIES_SEARCH, Util.otob(systemConfig.getBoolean(Configuration.LCM_ALLOW_ROLE_IDENTITIES_SEARCH)));
        requestRolesOptions.put(Configuration.LCM_ALLOW_ROLE_PERCENTAGE_SLIDER, Util.otob(systemConfig.getBoolean(Configuration.LCM_ALLOW_ROLE_PERCENTAGE_SLIDER)));
        requestRolesOptions.put(Configuration.LCM_ALLOW_ROLE_SORT_PERCENTAGE, Util.otob(systemConfig.getBoolean(Configuration.LCM_ALLOW_ROLE_SORT_PERCENTAGE)));
        requestRolesOptions.put(Configuration.LCM_ALLOW_ROLE_SORT_PERCENTAGE_COUNT, systemConfig.getInt(Configuration.LCM_ALLOW_ROLE_SORT_PERCENTAGE_COUNT));
    }

    public void initRequestEntitlementsOptions(Configuration systemConfig) {
        requestEntitlementsOptions = new HashMap<String, Object>();
        requestEntitlementsOptions.put(Configuration.LCM_ALLOW_ENTITLEMENT_POPULATION_SEARCH, Util.otob(systemConfig.getBoolean(Configuration.LCM_ALLOW_ENTITLEMENT_POPULATION_SEARCH)));
        requestEntitlementsOptions.put(Configuration.LCM_ALLOW_ENTITLEMENT_ATTRIBUTES_SEARCH, Util.otob(systemConfig.getBoolean(Configuration.LCM_ALLOW_ENTITLEMENT_ATTRIBUTES_SEARCH)));
        requestEntitlementsOptions.put(Configuration.LCM_ALLOW_ENTITLEMENT_IDENTITIES_SEARCH, Util.otob(systemConfig.getBoolean(Configuration.LCM_ALLOW_ENTITLEMENT_IDENTITIES_SEARCH)));
        requestEntitlementsOptions.put(Configuration.LCM_ALLOW_ENTITLEMENT_PERCENTAGE_SLIDER, Util.otob(systemConfig.getBoolean(Configuration.LCM_ALLOW_ENTITLEMENT_PERCENTAGE_SLIDER)));
        requestEntitlementsOptions.put(Configuration.LCM_ALLOW_ENTITLEMENT_SORT_PERCENTAGE, Util.otob(systemConfig.getBoolean(Configuration.LCM_ALLOW_ENTITLEMENT_SORT_PERCENTAGE)));
        requestEntitlementsOptions.put(Configuration.LCM_ALLOW_ENTITLEMENT_SORT_PERCENTAGE_COUNT, systemConfig.getInt(Configuration.LCM_ALLOW_ENTITLEMENT_SORT_PERCENTAGE_COUNT));
        requestEntitlementsOptions.put(Configuration.LCM_MANAGED_ATTRIBUTE_STATS_MAX_IDENTITIES, systemConfig.getInt(Configuration.LCM_MANAGED_ATTRIBUTE_STATS_MAX_IDENTITIES));
        requestEntitlementsOptions.put(Configuration.LCM_MANAGED_ATTRIBUTE_STATS_MAX_ATTRIBUTES, systemConfig.getInt(Configuration.LCM_MANAGED_ATTRIBUTE_STATS_MAX_ATTRIBUTES));

        requestEntitlementsOptions.put("lcmAllowPercentLimitEntitlements", Util.otob(systemConfig.getBoolean("lcmAllowPercentLimitEntitlements")));
        requestEntitlementsOptions.put("lcmAllowPercentLimitEntitlementsPercent", Util.otoi(systemConfig.getInt("lcmAllowPercentLimitEntitlementsPercent")));
    }

    private void initAdditionalAccountApplications() throws GeneralException {
        this.additionalAccountApplicationIds =
                initApplications(getContext(), "supportsAdditionalAccounts");
        this.additionalAccountAllApplications =
                (null == this.additionalAccountApplicationIds);
    }

    private void initAccountOnlyApplications() throws GeneralException {
        this.accountOnlyApplicationIds =
                initApplications(getContext(), "supportsAccountOnly");
        this.accountOnlyAllApplications =
                (null == this.accountOnlyApplicationIds);
    }
    private void initDisableAutoRefreshAcctStatusApplications(Configuration systemConfig) throws GeneralException {
        List<String> disableAutoRefreshAccountStatusApps = new ArrayList<String>();
        disableAutoRefreshAccountStatus=false;
        disableAutoRefAccountStatusAppsIds=new ArrayList<String>();
        disableAutoRefreshAccountStatusApps = (List<String>) systemConfig.getList(Configuration.LCM_MANAGE_ACCOUNTS_DISABLE_AUTO_REFRESH_STATUS);
        if (disableAutoRefreshAccountStatusApps != null && !disableAutoRefreshAccountStatusApps.isEmpty()) {
            if(disableAutoRefreshAccountStatusApps.size()==1 && disableAutoRefreshAccountStatusApps.contains(LinkService.AUTO_REFRESH_DISABLE_ALL_APPS)){
                disableAutoRefreshAccountStatus=true;
            }
            else{
                for (String application : disableAutoRefreshAccountStatusApps) {
                    disableAutoRefAccountStatusAppsIds.add(application);
                }
            }
        }
    }

    /**
     * Initialize the list of applications that support the feature for the
     * given property.  This will return null if all applications (ie - global)
     * are supported.  Otherwise, a non-null and possibly empty list is
     * returned.
     */
    private static List<String> initApplications(SailPointContext ctx, String prop)
            throws GeneralException {

        List<String> appIds = null;

        QueryOptions qo = new QueryOptions();
        qo.add(Filter.eq(prop, true));
        qo.addOrdering("name", true);
        int allApps = ctx.countObjects(Application.class, null);
        int theseApps = ctx.countObjects(Application.class, qo);

        boolean global = ((0 != allApps) && (allApps == theseApps));
        if (!global) {
            appIds = new ArrayList<String>();
            Iterator<Object[]> it = ctx.search(Application.class, qo, "id");
            while (it.hasNext()) {
                appIds.add((String) it.next()[0]);
            }
        }

        return appIds;
    }


    ////////////////////////////////////////////////////////////////////////////
    //
    // ACTIONS
    //
    ////////////////////////////////////////////////////////////////////////////

    public String saveChangesAction() {
        String result;
        try {
            Configuration systemConfig = getContext().getObjectByName(Configuration.class, Configuration.OBJ_NAME);

            saveWorkflowMappings(systemConfig);
            saveBatchWorkflowMappings(systemConfig);
            saveRequestRolesInfo(systemConfig);
            saveRequestEntitlementsInfo(systemConfig);
            saveIdentityPolicy();
            systemConfig.put("lcmRequirePasswordIdentityCreate", requirePasswordIdentityCreate);
            systemConfig.put("enableSelfServiceRegistration", enableSelfServiceRegistration);
            systemConfig.put(SELF_SERVICE_REGISTRATION_REDIRECT_PATH, selfServiceRegistrationRedirectPath);
            systemConfig.put(Configuration.LCM_MANAGE_ACCOUNTS_SHOW_ALL_BUTTONS, lcmManageAccountsShowAllButtons);
            systemConfig.put(Configuration.LCM_MANAGE_ACCOUNTS_ALLOW_GROUP_MANAGEMENT, lcmManageAccountsAllowGroupManagement);
            systemConfig.put(Configuration.LCM_ENABLE_FULLTEXT, enableFulltext);
            systemConfig.put(Configuration.LCM_FULL_TEXT_INDEX_PATH, fullTextIndexPath);
            saveFullTextServiceDefinitionArgs();
            systemConfig.put(Configuration.LCM_ALLOW_POPULATION_SEARCH, allowPopulationSearch);
            systemConfig.put(Configuration.LCM_ALLOW_IDENTITY_SEARCH, allowIdentitySearch);
            systemConfig.put(Configuration.LCM_SEARCH_MAX_RESULTS, searchMaxResults);
            systemConfig.put(Configuration.LCM_MOBILE_MAX_SELECTABLE_USERS, lcmMobileMaxSelectableUsers);
            systemConfig.put(Configuration.LCM_SHOW_EXTERNAL_TICKET_ID, showExternalTicketId);
            systemConfig.put("allowGeneratePasswordDelegated", generatePasswordDelegated);
            systemConfig.put(Configuration.LCM_ENABLE_APPROVAL_RECOMMENDATIONS, enableApprovalRecommendations);
            systemConfig.put(Configuration.LCM_ALLOW_PRIORITY_EDITING, allowPriorityEditing);
            saveManageAccountActions(systemConfig);
            saveDisableAutoRefreshAcctStatusApplications(systemConfig);
            systemConfig.put(Configuration.PASSWORD_VALIDATION_RULE, passwordValidationRule);
            systemConfig.put(Configuration.BATCH_REQUEST_APPROVER, batchRequestApprover);
            systemConfig.put(Configuration.REQUIRE_BATCH_REQUEST_APPROVAL, requireBatchRequestApproval);
            systemConfig.put(Configuration.DISPLAY_CLASSIFICATIONS_IN_ACCESS_REQUEST, displayClassificationsInAccessRequest);
            systemConfig.put(Configuration.LCM_SEARCH_TYPE, lcmSearchType);
            systemConfig.put(Configuration.LCM_CREATE_IDENTITY_USE_BY_DAYS, useByDays);
            getContext().saveObject(systemConfig);
            getContext().commitTransaction();

            this.saveAdditionalAccountApplications();
            this.saveAccountOnlyApplications();
            result = "save";
            addMessageToSession(new Message(MessageKeys.LCM_CONFIG_SAVE));
        } catch (GeneralException e) {
            result = "error";
            Message error = new Message(Type.Error, MessageKeys.LCM_CONFIG_SAVE_FAILED, e.getMessage());
            log.error(error.getLocalizedMessage(getLocale(), getUserTimeZone()), e);
            addMessageToSession(error);
        }

        return result;
    }

    private void saveFullTextServiceDefinitionArgs() throws GeneralException {
        ServiceDefinition fullTextDef = getFullTextServiceDefinition();
        if (fullTextDef != null) {
            String executionMode = enableAutoExecutionMode ? FullTextService.MODE_AUTOMATIC : FullTextService.MODE_SCHEDULED;

            // In development and sales environments the interval will typically be less than a minute
            // so that changes will be reflected immediately. This has the effect of causing the value stored in
            // seconds to be rounded down to zero during initialization and when the config is then saved it will
            // be saved as zero instead of the original values. Because of this, do not change the value unless it is
            // more than a zero. This should never affect a production environment because the interval should
            // always be much more than one minute. We can always come back in and change it to do floating point
            // arithmetic in the future.
            if (executionModeInterval > 0) {
                // interval is configured in minutes but stored in seconds so multiply by 60
                fullTextDef.setInterval(executionModeInterval * 60);
            }

            fullTextDef.put(FullTextService.ARG_EXECUTION_MODE, executionMode);

            // transaction will be committed when system config object is saved
            getContext().saveObject(fullTextDef);
        }
    }

    private void saveWorkflowMappings(Configuration configToSaveTo) {
        Set<String> workflowSettings = workflowMappings.keySet();
        for (String workflowSetting : workflowSettings) {
            String workflow = workflowMappings.get(workflowSetting);
            if (workflow != null) {
                configToSaveTo.put(workflowSetting, workflow);
            } else {
                configToSaveTo.remove(workflowSetting);
            }
        }
    }

    private void saveBatchWorkflowMappings(Configuration configToSaveTo) {
        Set<String> workflowSettings = batchWorkflowMappings.keySet();
        for (String workflowSetting : workflowSettings) {
            String workflow = batchWorkflowMappings.get(workflowSetting);
            if (workflow != null) {
                configToSaveTo.put(workflowSetting, workflow);
            } else {
                configToSaveTo.remove(workflowSetting);
            }
        }
    }

    private void saveRequestRolesInfo(Configuration configToSaveTo) {
        Set<String> requestRolesActionSettings = requestRolesActions.keySet();
        for (String requestRolesActionSetting : requestRolesActionSettings) {
            SelectOptionBean requestRolesAction = requestRolesActions.get(requestRolesActionSetting);
            if (requestRolesAction != null) {
                configToSaveTo.put(requestRolesActionSetting, requestRolesAction.isSelected());
            } else {
                configToSaveTo.put(requestRolesActionSetting, false);
            }
        }
        configToSaveTo.put("lcmUseRoleDetailsPage", Util.otob(requestRolesOptions.get("lcmUseRoleDetailsPage")));
        configToSaveTo.put("lcmAllowRequestPermits", Util.otob(requestRolesOptions.get("lcmAllowRequestPermits")));
        configToSaveTo.put("lcmAllowPercentLimitRoles", Util.otob(requestRolesOptions.get("lcmAllowPercentLimitRoles")));
        configToSaveTo.put("lcmAllowPercentLimitRolesPercent", Util.otoi(requestRolesOptions.get("lcmAllowPercentLimitRolesPercent")));
        configToSaveTo.put(Configuration.LCM_ALLOW_ROLE_POPULATION_SEARCH, Util.otob(requestRolesOptions.get(Configuration.LCM_ALLOW_ROLE_POPULATION_SEARCH)));
        configToSaveTo.put(Configuration.LCM_ALLOW_ROLE_ATTRIBUTES_SEARCH, Util.otob(requestRolesOptions.get(Configuration.LCM_ALLOW_ROLE_ATTRIBUTES_SEARCH)));
        configToSaveTo.put(Configuration.LCM_ALLOW_ROLE_IDENTITIES_SEARCH, Util.otob(requestRolesOptions.get(Configuration.LCM_ALLOW_ROLE_IDENTITIES_SEARCH)));
        configToSaveTo.put(Configuration.LCM_ALLOW_ROLE_PERCENTAGE_SLIDER, Util.otob(requestRolesOptions.get(Configuration.LCM_ALLOW_ROLE_PERCENTAGE_SLIDER)));
        configToSaveTo.put(Configuration.LCM_ALLOW_ROLE_SORT_PERCENTAGE, Util.otob(requestRolesOptions.get(Configuration.LCM_ALLOW_ROLE_SORT_PERCENTAGE)));
        configToSaveTo.put(Configuration.LCM_ALLOW_ROLE_SORT_PERCENTAGE_COUNT, requestRolesOptions.get(Configuration.LCM_ALLOW_ROLE_SORT_PERCENTAGE_COUNT));
    }

    private void saveRequestEntitlementsInfo(Configuration configToSaveTo) {
        configToSaveTo.put(Configuration.LCM_ALLOW_ENTITLEMENT_POPULATION_SEARCH, Util.otob(requestEntitlementsOptions.get(Configuration.LCM_ALLOW_ENTITLEMENT_POPULATION_SEARCH)));
        configToSaveTo.put(Configuration.LCM_ALLOW_ENTITLEMENT_ATTRIBUTES_SEARCH, Util.otob(requestEntitlementsOptions.get(Configuration.LCM_ALLOW_ENTITLEMENT_ATTRIBUTES_SEARCH)));
        configToSaveTo.put(Configuration.LCM_ALLOW_ENTITLEMENT_IDENTITIES_SEARCH, Util.otob(requestEntitlementsOptions.get(Configuration.LCM_ALLOW_ENTITLEMENT_IDENTITIES_SEARCH)));
        configToSaveTo.put(Configuration.LCM_ALLOW_ENTITLEMENT_PERCENTAGE_SLIDER, Util.otob(requestEntitlementsOptions.get(Configuration.LCM_ALLOW_ENTITLEMENT_PERCENTAGE_SLIDER)));
        configToSaveTo.put(Configuration.LCM_ALLOW_ENTITLEMENT_SORT_PERCENTAGE, Util.otob(requestEntitlementsOptions.get(Configuration.LCM_ALLOW_ENTITLEMENT_SORT_PERCENTAGE)));
        configToSaveTo.put(Configuration.LCM_ALLOW_ENTITLEMENT_SORT_PERCENTAGE_COUNT, requestEntitlementsOptions.get(Configuration.LCM_ALLOW_ENTITLEMENT_SORT_PERCENTAGE_COUNT));
        configToSaveTo.put(Configuration.LCM_MANAGED_ATTRIBUTE_STATS_MAX_IDENTITIES, requestEntitlementsOptions.get(Configuration.LCM_MANAGED_ATTRIBUTE_STATS_MAX_IDENTITIES));
        configToSaveTo.put(Configuration.LCM_MANAGED_ATTRIBUTE_STATS_MAX_ATTRIBUTES, requestEntitlementsOptions.get(Configuration.LCM_MANAGED_ATTRIBUTE_STATS_MAX_ATTRIBUTES));

        configToSaveTo.put("lcmAllowPercentLimitEntitlements", Util.otob(requestEntitlementsOptions.get("lcmAllowPercentLimitEntitlements")));
        configToSaveTo.put("lcmAllowPercentLimitEntitlementsPercent", Util.otoi(requestEntitlementsOptions.get("lcmAllowPercentLimitEntitlementsPercent")));
    }

    private void saveManageAccountActions(Configuration configToSaveTo) {
        if (manageAccountActions != null && !manageAccountActions.isEmpty()) {
            for (ManageAccountAction manageAccountAction : manageAccountActions) {
                String enableSelfAttribute = "manageAccounts" + manageAccountAction.getAction() + "SelfEnabled";
                if (manageAccountAction.isSelf()) {
                    configToSaveTo.put(enableSelfAttribute, true);
                } else {
                    configToSaveTo.remove(enableSelfAttribute);
                }

                String enableOthersAttribute = "manageAccounts" + manageAccountAction.getAction() + "SubordinateEnabled";
                if (manageAccountAction.isOthers()) {
                    configToSaveTo.put(enableOthersAttribute, true);
                } else {
                    configToSaveTo.remove(enableOthersAttribute);
                }
            }
        }
    }

    private void saveAdditionalAccountApplications() throws GeneralException {
        saveApplications(this.additionalAccountAllApplications,
                this.additionalAccountApplicationIds,
                "supportsAdditionalAccounts",
                Application.Feature.ADDITIONAL_ACCOUNT_REQUEST);
    }

    private void saveAccountOnlyApplications() throws GeneralException {
        saveApplications(this.accountOnlyAllApplications,
                this.accountOnlyApplicationIds,
                "supportsAccountOnly",
                Application.Feature.ACCOUNT_ONLY_REQUEST);
    }

    private void saveDisableAutoRefreshAcctStatusApplications(Configuration configToSaveTo) throws GeneralException {
        if (disableAutoRefreshAccountStatus) {
            List<String> disableAutoRefreshAccountStatus= new ArrayList<String>();
            disableAutoRefreshAccountStatus.add(LinkService.AUTO_REFRESH_DISABLE_ALL_APPS);
            configToSaveTo.put(Configuration.LCM_MANAGE_ACCOUNTS_DISABLE_AUTO_REFRESH_STATUS, disableAutoRefreshAccountStatus);
        }
        else{
            if(disableAutoRefAccountStatusAppsIds!=null){
                configToSaveTo.put(Configuration.LCM_MANAGE_ACCOUNTS_DISABLE_AUTO_REFRESH_STATUS, disableAutoRefAccountStatusAppsIds);
            }
            else{
                disableAutoRefAccountStatusAppsIds=new ArrayList<String>();
                configToSaveTo.put(Configuration.LCM_MANAGE_ACCOUNTS_DISABLE_AUTO_REFRESH_STATUS, disableAutoRefAccountStatusAppsIds);
            }
        }
    }

    private void saveApplications(boolean global, List<String> appIds,
                                  String featureProp, Application.Feature feature)
            throws GeneralException {

        // If global, add the feature to all apps that don't have it.
        if (global) {
            toggleFeature(getAppFilters(featureProp, null, false, null), feature, true);
        }
        else {
            // Unset the feature on all apps that don't need it, and add it to
            // apps that do need it.
            toggleFeature(getAppFilters(featureProp, appIds, false, true), feature, true);
            toggleFeature(getAppFilters(featureProp, appIds, true, false), feature, false);
        }
    }

    /**
     * Save Identity Provisioning Policy form.
     *
     * The Identity Provisioning Policies are optional forms
     * that can be specified to define the fields that must be
     * provided when an LCM Create Identity or Edit Identity
     * request is submitted.
     * @throws GeneralException
     */
    public void saveIdentityPolicy() throws GeneralException {
        IdentityPolicyBean currentIdentityPolicy = getIdentityPolicy();
        List<Form> forms = currentIdentityPolicy.getFormEditor().commitForms();

        try {
            Configuration systemConfig =
                    getContext().getObjectByName(Configuration.class, Configuration.OBJ_NAME);

            if (systemConfig != null) {
                // First, delete any forms that were deleted.
                currentIdentityPolicy.deleteForms();

                // Now save the remaining forms.
                Form createForm = currentIdentityPolicy.getForm(forms, Usage.CreateIdentity);
                if (createForm != null && !currentIdentityPolicy.validateCreateForm(createForm)) {
                    return;
                }
                currentIdentityPolicy.updateForm(Form.Type.CreateIdentity, createForm, systemConfig);

                Form updateForm = currentIdentityPolicy.getForm(forms, Usage.UpdateIdentity);
                currentIdentityPolicy.updateForm(Form.Type.UpdateIdentity, updateForm, systemConfig);

                Form registerForm = currentIdentityPolicy.getForm(forms, Usage.Register);
                currentIdentityPolicy.updateForm(Form.Type.RegisterIdentity, registerForm, systemConfig);

                // Commit to database
                getContext().commitTransaction();

                // bug#27269 - Lcm config save button adding duplicate fields
                // in the event of an error in saveChangesAction after saveIdentityPolicy has been called,
                // the identityPolicyBean still lives on the session and could save fields that have been added already
                currentIdentityPolicy.removeFromSession();
            }
        } catch(GeneralException ge) {
            log.warn("Unable to save form: " + ge.getMessage());
        }
    }

    // IIQSAW-3441: split large lists to avoid "too many parameters" error
    private List<Filter> getAppFilters(String featureProp, List<String> appIds,
                                       boolean hasFeature, Boolean inAppsList) {
        List<Filter> filters = new ArrayList<>();
        Filter featureFilter = Filter.eq(featureProp, hasFeature);

        if ((null != inAppsList) && (null != appIds) && !appIds.isEmpty()) {
            // split the input list into smaller lists if the max is exceeded
            for (Filter appIdFilter : ObjectUtil.getPartitionedInFilters("id", appIds)) {
                if (!inAppsList) {
                    appIdFilter = Filter.not(appIdFilter);
                }

                filters.add(Filter.and(featureFilter, appIdFilter));
            }
        }
        else {
            filters.add(featureFilter);
        }

        return filters;
    }

    private void toggleFeature(List<Filter> appFilters, Application.Feature feature, boolean add) throws GeneralException {
        if (appFilters != null && !appFilters.isEmpty()) {
            int commitThreshold = 10;

            // process each filter individually to avoid 'too many parameters' error
            for (Filter f : appFilters) {
                int count = 0;
                QueryOptions qo = new QueryOptions();

                // IIQSAW-2106: commit will close the resultSet, so we need to clone the result beforehand.
                qo.setCloneResults(true);
                qo.add(f);

                Iterator<Object[]> it = getContext().search(Application.class, qo, "id");
                while (it.hasNext()) {
                    String id = (String) it.next()[0];
                    Application app = getContext().getObjectById(Application.class, id);

                    if (add) {
                        app.addFeature(feature);
                    } else {
                        app.removeFeature(feature);
                    }
                    getContext().saveObject(app);

                    // commit transactions periodically
                    if ((++count % commitThreshold) == 0) {
                        getContext().commitTransaction();
                        getContext().decache();
                        count = 0;
                    }
                }

                // pick up last commit if count is not even multiple of commitThreshold
                if (count > 0) {
                    getContext().commitTransaction();
                }
            }
        }
    }

    ////////////////////////////////////////////////////////////////////////////
    //
    // GETTERS (READ-ONLY PROPERTIES)
    //
    ////////////////////////////////////////////////////////////////////////////

    public List <SelectItem> getLCMProvisioningWorkflows() throws GeneralException {
        if (provisioningWorkflows == null)
            provisioningWorkflows = WebUtil.getWorkflows(getContext(), Workflow.TYPE_LCM_PROVISIONING);
        return provisioningWorkflows;
    }

    public List <SelectItem> getLCMIdentityWorkflows() throws GeneralException {
        if (identityWorkflows == null)
            identityWorkflows = WebUtil.getWorkflows(getContext(), Workflow.TYPE_LCM_IDENTITY);
        return identityWorkflows;
    }

    public List <SelectItem> getBatchProvisioningWorkflows() throws GeneralException {
        if (batchProvisioningWorkflows == null)
            batchProvisioningWorkflows = WebUtil.getWorkflows(getContext(), Workflow.TYPE_LCM_PROVISIONING);
        return batchProvisioningWorkflows;
    }

    public List <SelectItem> getSelfServiceRegistrationWorkflows() throws GeneralException {
        if (selfServiceRegistrationWorkflows == null)
            selfServiceRegistrationWorkflows = WebUtil.getWorkflows(getContext(), Workflow.TYPE_LCM_SELF_SERVICE_REGISTRATION );
        return selfServiceRegistrationWorkflows;
    }

    public List <SelectItem> getBatchIdentityWorkflows() throws GeneralException {
        if (batchIdentityWorkflows == null)
            batchIdentityWorkflows = WebUtil.getWorkflows(getContext(), Workflow.TYPE_LCM_IDENTITY);
        return batchIdentityWorkflows;
    }

    public List<SelectItem> getPasswordValidationRules() throws GeneralException {
        return WebUtil.getRulesByType(getContext(), Rule.Type.FieldValue, true, false, passwordValidationRule);
    }

    public void setPasswordValidationRule(String rule) throws GeneralException {
        passwordValidationRule = rule;
    }

    public String getPasswordValidationRule() throws GeneralException {
        return (String) getContext().getConfiguration().get(Configuration.PASSWORD_VALIDATION_RULE);
    }

    ////////////////////////////////////////////////////////////////////////////
    //
    // GETTERS AND SETTERS
    //
    ////////////////////////////////////////////////////////////////////////////

    public Map<String, String> getWorkflows() {
        return workflowMappings;
    }

    public Map<String, String> getBatchWorkflows() {
        return batchWorkflowMappings;
    }

    public Map<String, SelectOptionBean> getRequestRolesActions() {
        return requestRolesActions;
    }

    public Map<String, Object> getRequestRolesOptions() {
        return requestRolesOptions;
    }

    public Map<String, Object> getRequestEntitlementsOptions() {
        return requestEntitlementsOptions;
    }

    public List<ManageAccountAction> getManageAccountActions() {
        return manageAccountActions;
    }

    public boolean isGeneratePasswordDelegated() {
        return generatePasswordDelegated;
    }

    public void setGeneratePasswordDelegated(boolean generatePasswordDelegated) {
        this.generatePasswordDelegated = generatePasswordDelegated;
    }

    public boolean isRequirePasswordIdentityCreate() {
        return requirePasswordIdentityCreate;
    }

    public void setRequirePasswordIdentityCreate(boolean requirePasswordIdentityCreate) {
        this.requirePasswordIdentityCreate = requirePasswordIdentityCreate;
    }

    public boolean isEnableApprovalRecommendations() {
        return enableApprovalRecommendations;
    }

    public void setEnableApprovalRecommendations(boolean enableApprovalRecommendations) {
        this.enableApprovalRecommendations = enableApprovalRecommendations;
    }

    public boolean isRecommenderConfigured() throws Exception {
        return WebUtil.isRecommenderConfigured();
    }

    public boolean isEnableSelfServiceRegistration() {
        return enableSelfServiceRegistration;
    }

    public void setEnableSelfServiceRegistration(boolean enableSelfServiceRegistration) {
        this.enableSelfServiceRegistration = enableSelfServiceRegistration;
    }

    public String getSelfServiceRegistrationRedirectPath() {
        return selfServiceRegistrationRedirectPath;
    }

    public void setSelfServiceRegistrationRedirectPath(String path) {
        this.selfServiceRegistrationRedirectPath = path;
    }

    public boolean isLcmManageAccountsShowAllButtons() {
        return lcmManageAccountsShowAllButtons;
    }

    public void setLcmManageAccountsShowAllButtons(
            boolean lcmManageAccountsShowAllButtons) {
        this.lcmManageAccountsShowAllButtons = lcmManageAccountsShowAllButtons;
    }

    public boolean isLcmManageAccountsAllowGroupManagement() {
        return lcmManageAccountsAllowGroupManagement;
    }

    public void setLcmManageAccountsAllowGroupManagement(boolean lcmManageAccountsAllowGroupManagement) {
        this.lcmManageAccountsAllowGroupManagement = lcmManageAccountsAllowGroupManagement;
    }

    public boolean isEnableFulltext() {
        return enableFulltext;
    }

    public void setEnableFulltext(boolean b) {
        this.enableFulltext = b;
    }

    public String getFullTextIndexPath() {
        return fullTextIndexPath;
    }

    public void setFullTextIndexPath(String fullTextIndexPath) {
        this.fullTextIndexPath = fullTextIndexPath;
    }

    public boolean isEnableAutoExecutionMode() {
        return enableAutoExecutionMode;
    }

    public void setEnableAutoExecutionMode(boolean enableAutoExecutionMode) {
        this.enableAutoExecutionMode = enableAutoExecutionMode;
    }

    public int getExecutionModeInterval() {
        return executionModeInterval;
    }

    public void setExecutionModeInterval(int executionModeInterval) {
        this.executionModeInterval = executionModeInterval;
    }

    public boolean isAllowPopulationSearch() {
        return this.allowPopulationSearch;
    }

    public void setAllowPopulationSearch(boolean b) {
        this.allowPopulationSearch = b;
    }

    public boolean isAllowIdentitySearch() {
        return this.allowIdentitySearch;
    }

    public void setAllowIdentitySearch(boolean b) {
        this.allowIdentitySearch = b;
    }

    public int getSearchMaxResults() {
        return this.searchMaxResults;
    }

    public void setSearchMaxResults(int searchMaxResults) {
        this.searchMaxResults = searchMaxResults;
    }

    public int getLcmMobileMaxSelectableUsers() {
        return this.lcmMobileMaxSelectableUsers;
    }

    public void setLcmMobileMaxSelectableUsers(int lcmMobileMaxSelectableUsers) {
        this.lcmMobileMaxSelectableUsers = lcmMobileMaxSelectableUsers;
    }

    public boolean isShowExternalTicketId() {
        return this.showExternalTicketId;
    }

    public void setShowExternalTicketId(boolean showExternalTicketId) {
        this.showExternalTicketId = showExternalTicketId;
    }

    public int getUseByDays() {
        return this.useByDays;
    }

    public void setUseByDays(int days) {
        this.useByDays = days;
    }

    public List<String> getDisableAutoRefAccountStatusAppsIds() {
        return this.disableAutoRefAccountStatusAppsIds;
    }

    public void setDisableAutoRefAccountStatusAppsIds(List<String> appIds) {
        this.disableAutoRefAccountStatusAppsIds = appIds;
    }

    public boolean isDisableAutoRefreshAccountStatus() {
        return this.disableAutoRefreshAccountStatus;
    }

    public void setDisableAutoRefreshAccountStatus(boolean global) {
        this.disableAutoRefreshAccountStatus = global;
    }

    public List<String> getAdditionalAccountApplicationIds() {
        return this.additionalAccountApplicationIds;
    }

    public void setAdditionalAccountApplicationIds(List<String> appIds) {
        this.additionalAccountApplicationIds = appIds;
    }

    public boolean isAdditionalAccountAllApplications() {
        return this.additionalAccountAllApplications;
    }

    public void setAdditionalAccountAllApplications(boolean global) {
        this.additionalAccountAllApplications = global;
    }

    public List<String> getAccountOnlyApplicationIds() {
        return this.accountOnlyApplicationIds;
    }

    public void setAccountOnlyApplicationIds(List<String> appIds) {
        this.accountOnlyApplicationIds = appIds;
    }

    public boolean isAccountOnlyAllApplications() {
        return this.accountOnlyAllApplications;
    }

    public void setAccountOnlyAllApplications(boolean global) {
        this.accountOnlyAllApplications = global;
    }

    public boolean isAllowPriorityEditing() {
        return this.allowPriorityEditing;
    }

    public void setAllowPriorityEditing(boolean allowPriorityEditing) {
        this.allowPriorityEditing = allowPriorityEditing;
    }

    public String getSearchType() {
        if(this.lcmSearchType == null){
            if (isEnableFulltext()) {
                this.lcmSearchType = Configuration.LCM_SEARCH_TYPE_STARTSWITH; // Always show startsWith when fulltext enabled
            } else {
                this.lcmSearchType = Configuration.LCM_SEARCH_TYPE_CONTAINS; //default to contains
            }
        }
        return this.lcmSearchType;
    }

    public void setSearchType(String type) {
        this.lcmSearchType = type;
    }

    public String getBatchRequestApprover() {
        return batchRequestApprover;
    }

    public void setBatchRequestApprover(String batchRequestApprover) {
        this.batchRequestApprover = batchRequestApprover;
    }

    public boolean isRequireBatchRequestApproval() {
        return requireBatchRequestApproval;
    }

    public void setRequireBatchRequestApproval(boolean requireBatchRequestApproval) {
        this.requireBatchRequestApproval = requireBatchRequestApproval;
    }

    public boolean isDisplayClassificationsInAccessRequest() {
        return displayClassificationsInAccessRequest;
    }

    public void setDisplayClassificationsInAccessRequest(boolean displayClassificationsInAccessRequest) {
        this.displayClassificationsInAccessRequest = displayClassificationsInAccessRequest;
    }

    public IdentityPolicyBean getIdentityPolicy() {
        if (identityPolicy == null) {
            identityPolicy = new IdentityPolicyBean();
        }
        return identityPolicy;
    }

    ////////////////////////////////////////////////////////////////////////////
    //
    // INNER CLASSES
    //
    ////////////////////////////////////////////////////////////////////////////

    public class ManageAccountAction {
        private String action;
        private String displayName;
        private boolean self;
        private boolean others;

        public ManageAccountAction() {}

        public ManageAccountAction(final String action, final boolean self, final boolean others) {
            this.action = action;
            this.displayName = getMessage(action);
            this.self = self;
            this.others = others;
        }

        public void setAction(String action) {
            this.action = action;
        }

        public String getAction() {
            return action;
        }

        public void setSelf(boolean isSelf) {
            this.self = isSelf;
        }

        public boolean isSelf() {
            return self;
        }

        public void setOthers(boolean isOthers) {
            this.others = isOthers;
        }

        public boolean isOthers() {
            return others;
        }

        public String getDisplayName() {
            return displayName;
        }
    }
}