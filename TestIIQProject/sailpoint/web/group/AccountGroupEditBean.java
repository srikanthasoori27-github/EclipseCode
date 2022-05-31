/* (c) Copyright 2012 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.group;

import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONWriter;

import sailpoint.Version;
import sailpoint.api.AccountGroupService;
import sailpoint.api.ManagedAttributer;
import sailpoint.api.SailPointContext;
import sailpoint.api.ScopeService;
import sailpoint.api.WorkflowSession;
import sailpoint.authorization.RightAuthorizer;
import sailpoint.connector.Connector;
import sailpoint.connector.ConnectorException;
import sailpoint.connector.ConnectorFactory;
import sailpoint.connector.ObjectNotFoundException;
import sailpoint.object.Application;
import sailpoint.object.Application.Feature;
import sailpoint.object.AttributeDefinition;
import sailpoint.object.Capability;
import sailpoint.object.ColumnConfig;
import sailpoint.object.Configuration;
import sailpoint.object.Field;
import sailpoint.object.Filter;
import sailpoint.object.Form;
import sailpoint.object.Identity;
import sailpoint.object.ManagedAttribute;
import sailpoint.object.Permission;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.ProvisioningPlan.AttributeRequest;
import sailpoint.object.ProvisioningPlan.ObjectOperation;
import sailpoint.object.ProvisioningPlan.ObjectRequest;
import sailpoint.object.ProvisioningPlan.Operation;
import sailpoint.object.QueryOptions;
import sailpoint.object.ResourceObject;
import sailpoint.object.SPRight;
import sailpoint.object.Schema;
import sailpoint.object.Scope;
import sailpoint.object.Source;
import sailpoint.object.Template.Usage;
import sailpoint.object.WorkflowCase;
import sailpoint.object.WorkflowLaunch;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Internationalizer;
import sailpoint.tools.JsonHelper;
import sailpoint.tools.Message;
import sailpoint.tools.Message.Type;
import sailpoint.tools.Util;
import sailpoint.web.BaseObjectBean;
import sailpoint.web.FormBean;
import sailpoint.web.FormHandler;
import sailpoint.web.FormHandler.FormStore;
import sailpoint.service.LCMConfigService;
import sailpoint.service.form.renderer.extjs.FormRenderer;
import sailpoint.web.analyze.AnalyzeControllerBean;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.util.NavigationHistory;

/**
 * Bean used to edit ManagedAttributes.  This was originally only for Account Groups, but its
 * scope has been expanded to include Entitlement and Permission-based ManagedAttributes as well.
 * @author <a href="mailto:bernie.margolis@sailpoint.com">Bernie Margolis</a>
 */
public class AccountGroupEditBean
    extends BaseObjectBean<ManagedAttribute>
    implements FormBean {

    private static Log log = LogFactory.getLog(AccountGroupEditBean.class);

    private static final String MANAGED_ATTRIBUTE_ID = "managedAttributeId";
    
    private AccountGroupDTO accountGroup;
    private boolean displayEntitlementDescriptions;
    private boolean pendingWorkflowCase;
    private List<ColumnConfig> permissionColumns;
    private String DTO_SESSION_KEY;
    private String CURRENT_TAB_SESSION_KEY;
    private String type;
    private boolean typeChanged;
    private String currentTab;
        
    private String currentClassificationIdToAdd;

    private List<String> currentClassificationIdsToRemove;



    /**
     * Default constructor used to create JSF bean.
     */
    public AccountGroupEditBean() {
        this((String) null);
    }
    
    /**
     * Constructor used to construct with a previous state.  We expect the ID
     * of the ManagedAttribute in the state.
     * 
     * @param  state  The state produced by {@link #getFormBeanState()}.
     */
    public AccountGroupEditBean(Map<String,Object> state) {
        this(Util.getString((String) state.get(MANAGED_ATTRIBUTE_ID)));
    }

    /**
     * Constructor.
     */
    AccountGroupEditBean(String id) {
        super();
        
        // If an ID was explicitly passed, use it!
        if (null != id) {
            setObjectId(id);
        }
        
        setScope(ManagedAttribute.class);
        // We'll manage our own DTO version of the account group
        setStoredOnSession(false);
        DTO_SESSION_KEY = getSessionKey() + "_DTO";
        CURRENT_TAB_SESSION_KEY = getSessionKey() + "_CURRENT_TAB";
        
        // Flush out of the session if this is a new entitlement
        boolean forceLoad = Util.otob(getRequestParameter("forceLoad"));
        if (forceLoad) {
            accountGroup = null;
            currentTab = "Standard";
            cancel();
        } else {
            accountGroup = (AccountGroupDTO) getSessionScope().get(DTO_SESSION_KEY);
            currentTab = (String) getSessionScope().get(CURRENT_TAB_SESSION_KEY);
        }
        
        if (accountGroup == null) {
            ManagedAttribute persistedObj;
            try {
                persistedObj = getObject();
            } catch (GeneralException e) {
                persistedObj = null;
                log.error("The account group edit bean could not find the account group with id " + getObjectId(), e);
            }
            
            boolean provisioningEnabled = initProvisioningSetting();
            
            if (persistedObj == null) {
                accountGroup = new AccountGroupDTO(provisioningEnabled, this);
                try {
                    // Assign a scope by default when necessary
                    ScopeService scopeSvc = new ScopeService(getContext());
                    boolean scopingEnabled = scopeSvc.isScopingEnabled();
                    if (scopingEnabled) {
                        QueryOptions qo = new QueryOptions();
                        qo.setScopeResults(true);
                        int scopeCount = getContext().countObjects(Scope.class, qo);
                        if (scopeCount == 1) {
                            List<Scope> controlledScopes = getContext().getObjects(Scope.class, qo);
                            Scope scopeToControl = controlledScopes.get(0);
                            accountGroup.setScopeId(scopeToControl.getId());
                        }
                    }
                } catch (GeneralException e) {
                    log.error("Unable to determine a scope.  The default scope will not be set for this newly-created entitlement.", e);
                }
            } else {
                persistedObj.load();
                // Apply the application-specific provisioning settings before creating the DTO
                String appName = persistedObj.getApplication().getName();
                accountGroup = new AccountGroupDTO(persistedObj, provisioningEnabled, this);
            }
            
            getSessionScope().put(DTO_SESSION_KEY, accountGroup);
            
            displayEntitlementDescriptions = Util.otob(Configuration.getSystemConfig().getBoolean(Configuration.DISPLAY_ENTITLEMENT_DESC));
        }
        else {
            accountGroup.attach(this);
        }
        
        assert(accountGroup != null);
        // Need to do a full update in case an app is already set
        updateType();
        updateProvisioningSetting();
        try {
            String groupId = accountGroup.getId();
            if (groupId != null && groupId.trim().length() > 0) {
                List<WorkflowCase> pendingWorkflows = getContext().getObjects(WorkflowCase.class, new QueryOptions(Filter.eq("targetId", groupId)));
                pendingWorkflowCase = (pendingWorkflows != null && !pendingWorkflows.isEmpty());
            }
        } catch (GeneralException e) {
            log.error("That Account Group Editor is unable to find existing workflows.", e);
            pendingWorkflowCase = false;
        } catch (IllegalArgumentException e) {
            log.error("The Account Group Editor is unable to find existing workflows.", e);
            pendingWorkflowCase = false;
        }
    }
    
    public AccountGroupDTO getAccountGroup() {
        return accountGroup;
    }
    
    public boolean isPendingWorkflowCase() {
        return pendingWorkflowCase;
    }
    
    public boolean isSaveDisabled() throws GeneralException{
        return pendingWorkflowCase || !isAuthorized(new RightAuthorizer(SPRight.FullAccessGroup, SPRight.ManagedAttributePropertyAdministrator, SPRight.ManagedAttributeProvisioningAdministrator));
    }
    
    /**
     * This method's name has become misleading due to changes we've made.  This only determines whether or not
     * we want to allow this newly created ManagedAttribute to be of type group.  It has no bearing on whether 
     * or not the Group Properties tab gets displayed.
     * @return true if the edited ManagedAttribute is capable of being a group; false otherwise
     */
    public boolean isGroupOptionsEnabled() {
        boolean groupOptionsEnabled = false;

        String appName = accountGroup.getApplicationName();
        if (!Util.isNullOrEmpty(appName)) {
            try {
                Application app = getContext().getObjectByName(Application.class, appName);
                if (app != null) {
                    groupOptionsEnabled = app.hasGroupSchema(getType());
                }

                // We can provision manually or in an automated manner.  If the case below is 
                // true we'll do it automatically.  Otherwise we still allow editing so that 
                // we can generate work items 
                // groupOptionsEnabled = app.supportsFeature(Feature.GROUP_PROVISIONING);
            } catch (GeneralException e) {
                log.error("The account group edit bean is unable to find an application named " + appName, e);
                groupOptionsEnabled = false;
            }
        }
        
        return groupOptionsEnabled;
    }
    
    /**
     * @return true if we should display a group tab for this ManagedAttribute
     */
    public boolean isShowGroupTab() {
        // never show if it is not a group
        if (!accountGroup.isGroup()) {
            return false;
        }

        // show if creating and provisioning is enabled or this group has been aggregated
        return (isNewGroup() && isProvisioningEnabled()) || accountGroup.isAggregated();
    }

    /**
     * Determines if the members tab should be visible.
     * @return True if tab should be shown, false otherwise.
     * @throws GeneralException
     */
    public boolean isShowMembersTab() throws GeneralException {
        // show tab if we are not creating and the managed attribute is either
        // not a group or has a group attribute on the account schema
        return !isNewGroup() && (!accountGroup.isGroup() || hasGroupAttribute(accountGroup.getType()));
    }

    /**
     * Determines if the Access Tab should be visible
     * @return
     * @throws GeneralException
     */
    public boolean isShowAccessTab() throws GeneralException {
        return !isNewGroup() && !ManagedAttribute.Type.Permission.name().equals(accountGroup.getType());
    }
        

    public String getDescriptionsJSON() throws GeneralException {
        return accountGroup.getDescriptionsJson();
    }

    public void setDescriptionsJSON(String descriptionsJSON) throws GeneralException {
        accountGroup.setDescriptionsJson(descriptionsJSON);
    }
    
    /**
     * @return The edited object's current type as far as the UI is concerned.  Note that this can differ from the 
     * actual type because this gets initialized with some logic to pick an appropriate default.  The two will get
     * reconciled on input anyways
     */
    public String getType() {
        return type;
    }
    
    public String getReasonForTypeChange() {
        String reason;
        
        if (Util.isNullOrEmpty(type)) {
            reason = new Message(MessageKeys.MANAGED_ATTRIBUTE_TYPES_UNAVAILABLE, getAccountGroup().getApplicationName()).getLocalizedMessage(getLocale(), null);
        } else if (getAccountGroup().isGroup()) {
            reason = new Message(MessageKeys.MANAGED_ATTRIBUTE_TYPE_OVERRIDDEN_TO_GROUP, getAccountGroup().getApplicationName(), getAccountGroup().getReferenceAttribute()).getLocalizedMessage(getLocale(), null);
        } else {
            reason = new Message(MessageKeys.MANAGED_ATTRIBUTE_TYPE_OVERRIDDEN, getAccountGroup().getApplicationName()).getLocalizedMessage(getLocale(), null);
        }
        
        return reason;
    }

    private void updateType() {
        type = "";

        String appName = accountGroup.getApplicationName();
        if (!Util.isNullOrEmpty(appName)) {
            String basicType = accountGroup.getType();

            Application app = null;
            try {
                app = getContext().getObjectByName(Application.class, appName);
            } catch (GeneralException ge) {
                //Continue
                if(log.isWarnEnabled()) {
                    log.warn("Could not find application with name: " + appName);
                }
            }

            // set the type
            if (ManagedAttribute.Type.Entitlement.name().equalsIgnoreCase(basicType)) {
                type = "entitlement";
            } else if (ManagedAttribute.Type.Permission.name().equalsIgnoreCase(basicType)) {
                type = "permission";
            } else if (app != null) {
                if (app.hasGroupSchema(basicType)) {
                    type = basicType;
                } else if (!Util.isNullOrEmpty(basicType)) {
                    // warn about the type not having a configured schema on the application
                    log.warn("Type named " + basicType + " does not have a corresponding schema " +
                             "configured on the application with name " + appName);
                    type = basicType;
                }
            }

            // if no type yet then default to first item in list
            if (Util.isNullOrEmpty(type)) {
                selectSupportedType(getSupportedTypesList());
            }
        }
    }
    
    public void setType(String type) {
        if (Util.isNullOrEmpty(type)) {
            accountGroup.setType(null);
            accountGroup.setReferenceAttribute(null);
            this.type = null;
        } else {
            //TODO: Should ensure that the type is supported for the app
            accountGroup.setType(type);
            
            this.type = type;
        }
    }
    
    public String getCurrentTab() {
        return currentTab;
    }

    public void setCurrentTab(String currentTab) {
        this.currentTab = currentTab;
    }

    public String getReadOnlyType() {
        String readOnlyType;
        if (accountGroup.getType() == null) {
            readOnlyType = "";
        }
        else {
            readOnlyType = new Message(accountGroup.getType()).getLocalizedMessage(getLocale(), getUserTimeZone());
        }
        
        return readOnlyType;
    }
    
    /**
     * There are two circumstances under which we enable values to be directly edited:
     * 1. If the ManagedAttribute represents a non-group and non-permission entitlement the value is directly editable
     * 2. If the ManagedAttribute represents a group but provisioning is disabled we allow the value to be directly editable
     * @return true if users can edit the current ManagedAttribute's value directly; false otherwise
     */
    public boolean isSupportsDirectValueEdit() {
        boolean supportsDirectValueEdit = !ManagedAttribute.Type.Permission.name().equalsIgnoreCase(accountGroup.getType()) &&
                (!accountGroup.isGroup() || (accountGroup.isGroup() && !isProvisioningEnabled())) && 
                !Util.isNullOrEmpty(getType());
        
        return supportsDirectValueEdit;
    }
   
    /**
     * We support provisioning when the following conditions apply:
     * 1. The current user has the provisioning right
     * 2. The edited ManagedAttribute's application has a group attribute
     * 3. The edited ManagedAttribute's application has the provisioning feature enabled
     * 4. The edited ManagedAttribute's application has the proper Provisioning Policy in place 
     *    for the current action (either CreateGroup or UpdateGroup)
     * 5. Account Group Management is enabled on the system
     * @return true if the edited ManagedAttribute supports provisioning; false otherwise
     */
    public boolean isProvisioningEnabled() {
        // This was set in the initProvisioningSetting() and/or updateProvisioningSetting() method
        return accountGroup.isProvisioningEnabled();
    }
        
    public String getReadOnlyValueInstructions() {
        String nativeIdentityAttribute = "";
        if (accountGroup != null) {
            nativeIdentityAttribute = accountGroup.getNativeIdentityAttribute(accountGroup.getType());
        }
        Message readOnlyValueInstructions;
        if (accountGroup.isGroup() && isProvisioningEnabled()) {
            readOnlyValueInstructions = new Message(MessageKeys.MANAGED_ATTRIBUTE_READ_ONLY_VALUE_INSTRUCTIONS, nativeIdentityAttribute);
        } else {
            readOnlyValueInstructions = new Message(MessageKeys.MANAGED_ATTRIBUTE_VALUE_NOT_SUPPORTED_INSTRUCTIONS, nativeIdentityAttribute);
        }
        return readOnlyValueInstructions.getLocalizedMessage(getLocale(), getUserTimeZone());
    }

    public String getGroupOwnerInit() {
        String ownerInit = "";
        final Writer jsonString = new StringWriter();
        final JSONWriter jsonWriter = new JSONWriter(jsonString);
        String owner = accountGroup.getOwnerId();
        Identity ownerObj;
        QueryOptions qo = new QueryOptions();
        qo.setScopeResults(true);
        int totalCount;
        
        try {
            totalCount = getContext().countObjects(Identity.class, qo);
        } catch (GeneralException e) {
            log.error("The account group edit bean could not get an accurate identity count", e);
            totalCount = 0;
        }
        
        if (owner == null || owner.trim().length() == 0) {
            ownerObj = null;
        } else {
            try {
                ownerObj = getContext().getObjectById(Identity.class, owner);
            } catch (GeneralException e) {
                log.error("The account group edit bean could not initialize the group owner", e);
                ownerObj = null;
            }
        }
        
        try {
            jsonWriter.object();
            jsonWriter.key("totalCount");
            jsonWriter.value(totalCount);
            jsonWriter.key("identities");
            jsonWriter.array();
            if (ownerObj != null) {
                jsonWriter.object();
                jsonWriter.key("id");
                jsonWriter.value(ownerObj.getId());
                jsonWriter.key("name");
                jsonWriter.value(ownerObj.getName());
                jsonWriter.key("firstname");
                jsonWriter.value(ownerObj.getFirstname());
                jsonWriter.key("lastname");
                jsonWriter.value(ownerObj.getLastname());
                jsonWriter.key("email");
                jsonWriter.value(ownerObj.getEmail());
                jsonWriter.key("emailclass");
                jsonWriter.value("email");
                jsonWriter.key("displayableName");
                jsonWriter.value(ownerObj.getDisplayableName());
                jsonWriter.key("icon");
                jsonWriter.value("groupIcon");
                jsonWriter.endObject();
            }
            jsonWriter.endArray();
            jsonWriter.endObject();
            ownerInit = jsonString.toString();
        } catch (JSONException e) {
            log.error("The account group edit bean was unable to generate a proper initializer for the group owner suggest.", e);
        }
        
        return ownerInit;
    }
    
    public String getScopeInit() {
        String scopeInit = "";
        final Writer jsonString = new StringWriter();
        final JSONWriter jsonWriter = new JSONWriter(jsonString);
        String scopeId = accountGroup.getScopeId();
        Scope scopeObj;
        QueryOptions qo = new QueryOptions();
        qo.setScopeResults(true);
        int totalCount;
        
        try {
            totalCount = getContext().countObjects(Scope.class, qo);
        } catch (GeneralException e) {
            log.error("The account group edit bean could not get an accurate scope count", e);
            totalCount = 0;
        }
        
        if (scopeId == null || scopeId.trim().length() == 0) {
            scopeObj = null;
        } else {
            try {
                scopeObj = getContext().getObjectById(Scope.class, scopeId);
            } catch (GeneralException e) {
                log.error("The account group edit bean could not initialize the scope owner", e);
                scopeObj = null;
            }
        }
        
        try {
            jsonWriter.object();
            jsonWriter.key("totalCount");
            jsonWriter.value(totalCount);
            jsonWriter.key("scopes");
            jsonWriter.array();
            if (scopeObj != null) {
                jsonWriter.object();
                jsonWriter.key("id");
                jsonWriter.value(scopeObj.getId());
                jsonWriter.key("displayName");
                jsonWriter.value(scopeObj.getDisplayName());
                jsonWriter.endObject();
            }
            jsonWriter.endArray();
            jsonWriter.endObject();
            scopeInit = jsonString.toString();
        } catch (JSONException e) {
            log.error("The account group edit bean was unable to generate a proper initializer for the scope suggest.", e);
        }
        
        return scopeInit;        
    }
    
    public String getApplicationInit() {
        String appInit = "";
        final Writer jsonString = new StringWriter();
        final JSONWriter jsonWriter = new JSONWriter(jsonString);
        String appName = accountGroup.getApplicationName();
        Application appObj;
        QueryOptions qo = new QueryOptions();
        qo.setScopeResults(true);
        int totalCount;
        
        try {
            totalCount = getContext().countObjects(Application.class, qo);
        } catch (GeneralException e) {
            log.error("The account group edit bean could not get an accurate scope count", e);
            totalCount = 0;
        }
        
        if (appName == null || appName.trim().length() == 0) {
            appObj = null;
        } else {
            try {
                appObj = getContext().getObjectByName(Application.class, appName);
            } catch (GeneralException e) {
                log.error("The account group edit bean could not initialize the application", e);
                appObj = null;
            }
        }
        
        try {
            jsonWriter.object();
            jsonWriter.key("totalCount");
            jsonWriter.value(totalCount);
            jsonWriter.key("objects");
            jsonWriter.array();
            if (appObj != null) {
                jsonWriter.object();
                jsonWriter.key("id");
                jsonWriter.value(appObj.getId());
                jsonWriter.key("name");
                jsonWriter.value(appObj.getName());
                jsonWriter.key("displayName");
                jsonWriter.value(appObj.getName());
                jsonWriter.endObject();
            }
            jsonWriter.endArray();
            jsonWriter.endObject();
            appInit = jsonString.toString();
        } catch (JSONException e) {
            log.error("The account group edit bean was unable to generate a proper initializer for the scope suggest.", e);
        }
        
        return appInit;        
    }
    
    public String getReferenceAttributeInit() {
        List<AttributeDefinition> attributeDefs = null;
        Schema accountSchema = null;
        String applicationName = accountGroup.getApplicationName();
        
        if (applicationName != null && applicationName.trim().length() > 0) {
            try {
                Application app = getContext().getObjectByName(Application.class, accountGroup.getApplicationName());
                accountSchema = app.getSchema(Application.SCHEMA_ACCOUNT);
                if (accountSchema != null) {
                    attributeDefs = accountSchema.getAttributes();
                }
            } catch (GeneralException e) {
                log.error("The Account Attributes Suggest Bean was unable to retrieve the account attributes from application " + applicationName, e);
            }
        }
        
        StringWriter jsonString = new StringWriter();
        JSONWriter jsonWriter = new JSONWriter(jsonString);
        String attributes;
        try {
            jsonWriter.object();
            int totalCount;
            List<AttributeDefinition> attributesToReturn  = new ArrayList<AttributeDefinition>();
            String referenceAttribute = accountGroup.getReferenceAttribute();
            if (attributeDefs != null && !attributeDefs.isEmpty()) {
                totalCount = attributeDefs.size();
                if (accountSchema != null && referenceAttribute != null && referenceAttribute.trim().length() > 0) {
                    Map<String, AttributeDefinition> schemaAttributes = accountSchema.getAttributeMap();
                    if (schemaAttributes != null) {
                        AttributeDefinition attributeToReturn = schemaAttributes.get(referenceAttribute);
                        if (attributeToReturn != null) {
                            attributesToReturn.add(attributeToReturn);
                        }
                    }
                }
            } else {
                totalCount = 0;
            }

            jsonWriter.key("totalCount");
            jsonWriter.value(totalCount);
            
            jsonWriter.key("attributes");
            jsonWriter.array();
            
            for (AttributeDefinition identityAttribute : attributesToReturn) {
                jsonWriter.object();
                jsonWriter.key("name");
                jsonWriter.value(identityAttribute.getName());
                jsonWriter.key("displayName");
                jsonWriter.value(identityAttribute.getDisplayableName());
                jsonWriter.endObject();
            }
            
            jsonWriter.endArray();
            
            jsonWriter.endObject();
            
            attributes = jsonString.toString();
        } catch (Exception e) {
            log.error("Could not build JSON for identities right now", e);
            attributes = JsonHelper.emptyListResult("totalCount", "attributes");
        } 
        
        return attributes;        
    }

    
    public String getInheritedGroupsInit() {
        String inheritedGroupsInit = "";
        final Writer jsonString = new StringWriter();
        final JSONWriter jsonWriter = new JSONWriter(jsonString);
        List<String> inheritedGroupIds = accountGroup.getInheritedAccountGroups();
        List<ManagedAttribute> inheritedGroups;
        int totalCount;
        
        if (inheritedGroupIds == null || inheritedGroupIds.isEmpty()) {
            totalCount = 0;
            inheritedGroups = new ArrayList<ManagedAttribute>();
        } else {
            QueryOptions qo = new QueryOptions(Filter.in("id", inheritedGroupIds));
            qo.setScopeResults(true);
    
            try {
                totalCount = getContext().countObjects(ManagedAttribute.class, qo);
                inheritedGroups = getContext().getObjects(ManagedAttribute.class, qo);
            } catch (GeneralException e) {
                log.error("The account group edit bean could not get an accurate scope count", e);
                totalCount = 0;
                inheritedGroups = new ArrayList<ManagedAttribute>();
            }
        }
                
        try {
            jsonWriter.object();
            jsonWriter.key("totalCount");
            jsonWriter.value(totalCount);
            jsonWriter.key("objects");
            jsonWriter.array();
            if (inheritedGroups != null && !inheritedGroups.isEmpty()) {
                for (ManagedAttribute inheritedGroup : inheritedGroups) {
                    jsonWriter.object();
                    jsonWriter.key("id");
                    jsonWriter.value(inheritedGroup.getId());
                    jsonWriter.key("displayableName");
                    jsonWriter.value(inheritedGroup.getDisplayableName());
                    // The multi-suggest demands a displayField.  Tried to work around this client-side without success
                    jsonWriter.key("displayField");
                    jsonWriter.value(inheritedGroup.getDisplayableName());
                    jsonWriter.key("owner");
                    jsonWriter.value(inheritedGroup.getOwner() == null ? "" : inheritedGroup.getOwner().getDisplayableName());
                    jsonWriter.endObject();                    
                }
            }
            jsonWriter.endArray();
            jsonWriter.endObject();
            inheritedGroupsInit = jsonString.toString();
        } catch (JSONException e) {
            log.error("The account group edit bean was unable to generate a proper initializer for the scope suggest.", e);
        }
        
        return inheritedGroupsInit;        
        
    }
    
    public int getInheritingGroupsCount() {
        int inheritingGroupsCount = 0;
        
        try {
            ManagedAttribute ma = getObject();
            if (ma != null && ma.isGroupType()) {
                List<ManagedAttribute> thisAttribute = Arrays.asList(new ManagedAttribute [] { ma });
                QueryOptions qo = new QueryOptions(Filter.containsAll("inheritance", thisAttribute));
                inheritingGroupsCount = getContext().countObjects(ManagedAttribute.class, qo);
            }
        } catch (GeneralException e) {
            log.error("The account group edit bean could not get an accurate count of inheriting groups", e);
            inheritingGroupsCount = 0;
        }

        return inheritingGroupsCount;
    }
    
    public int getInheritedGroupsCount() {
        int inheritedGroupsCount = 0;
        
        try {
            ManagedAttribute ma = getObject();
            if (ma != null && ma.isGroupType()) {
                AccountGroupBean groupBean = new AccountGroupBean(ma);
                inheritedGroupsCount = groupBean.getInheritedSize();
            } else {
                inheritedGroupsCount = 0;
            }
        } catch (GeneralException e) {
            log.error("The account group edit bean could not get an accurate count of inheriting groups", e);
            inheritedGroupsCount = 0;
        }

        return inheritedGroupsCount;
    }
    
    public String getTitle() {
        String type;
        if (accountGroup.isGroup()) {
            type = new Message(accountGroup.getType()).getLocalizedMessage(getLocale(), getUserTimeZone());
        } else {
            String attributeType = accountGroup.getType();
            if (attributeType == null) {
                // Display the attribute as an entitlement by default
                attributeType = ManagedAttribute.Type.Entitlement.name();
            }
            if (attributeType.equals(ManagedAttribute.Type.Entitlement.name())) {
                type = new Message(MessageKeys.ENTITLEMENT).getLocalizedMessage(getLocale(), null);
            } else if (attributeType.equals(ManagedAttribute.Type.Permission.name())) {
                type = new Message(MessageKeys.PERMISSION).getLocalizedMessage(getLocale(), null);
            } else {
                type = new Message(attributeType).getLocalizedMessage(getLocale(), getUserTimeZone());
            }
        }
        
        Message msg;
        
        if (isNewGroup()) {
            msg = new Message(MessageKeys.MANAGED_ATTRIBUTE_CREATE_TITLE, type);
        } else {
            msg = new Message(MessageKeys.MANAGED_ATTRIBUTE_EDIT_TITLE, type);
        }
        
        return msg.getLocalizedMessage(getLocale(), getUserTimeZone());
    }
    
    public String getRequiredAppInfoMessage() {
        String type;
        if (accountGroup.isGroup()) {
            type = new Message(MessageKeys.GROUP).getLocalizedMessage(getLocale(), getUserTimeZone());
        } else {
            String attributeType = accountGroup.getType();
            if (attributeType == null) {
                // Display the attribute as an entitlement by default
                attributeType = ManagedAttribute.Type.Entitlement.name();
            }
            type = new Message(attributeType).getLocalizedMessage(getLocale(), getUserTimeZone());
        }
        
        String message = new Message(MessageKeys.MANAGED_ATTRIBUTE_ATT_APP_REQUIRED,type).getLocalizedMessage(getLocale(), null);
        return message;
    }
    
    /**
     * @return JSON-based array of types that are supported by the selected application
     */
    public String getSupportedTypes() {
        List<String[]> supportedTypes = getSupportedTypesList();
        final Writer jsonString = new StringWriter();
        final JSONWriter jsonWriter = new JSONWriter(jsonString);
        String supportedTypesString;
        
        try {
            jsonWriter.object();
            jsonWriter.key("supportedTypes");
            jsonWriter.value(supportedTypes);
            jsonWriter.endObject();
        } catch (JSONException e) {
            // Just return entitlements in this case
            supportedTypesString = "{'supportedTypes': [['entitlement', " + new Message(MessageKeys.ENTITLEMENT).getLocalizedMessage(getLocale(), getUserTimeZone()) + "]]}";
        }
        
        supportedTypesString = jsonString.toString();
        return supportedTypesString;
    }
    
    private List<String[]> getSupportedTypesList() {
        List<String[]> supportedTypes = new ArrayList<String[]>();
        String appNameOrId = getApplication();
        if (appNameOrId != null && appNameOrId.trim().length() > 0) {
            try {
                Application application = getContext().getObjectByName(Application.class, appNameOrId);
                if (application != null) {
                    if (supportsManagedEntitlements(application)) {
                        supportedTypes.add(new String [] {"entitlement", new Message(MessageKeys.ENTITLEMENT).getLocalizedMessage(getLocale(), getUserTimeZone())});
                    }

                    for (Schema schema : Util.iterate(application.getGroupSchemas())) {
                        if (!isNewGroup() || hasProvisioningRight()) {
                            String objectType = schema.getObjectType();

                            supportedTypes.add(new String [] {objectType, new Message(objectType).getLocalizedMessage(getLocale(), getUserTimeZone())});
                        }
                    }
                    
                    if (supportsDirectPermissions(application)) {
                        supportedTypes.add(new String[] {"permission", new Message(MessageKeys.PERMISSION).getLocalizedMessage(getLocale(), getUserTimeZone())});
                    }
                }
            } catch (GeneralException e) {
                // Don't know if any additional types are supported at this point so no entitlements can be created for the application
                log.error("The AccountGroupEditBean could not determine supported types for the application " + appNameOrId);
            }
        }
        return supportedTypes;
    }
    
    private Set<String> getSupportedTypesSet() {
        Set<String> supportedTypes = new HashSet<String>();
        String appNameOrId = getApplication();
        if (appNameOrId != null && appNameOrId.trim().length() > 0) {
            try {
                Application application = getContext().getObjectByName(Application.class, appNameOrId);
                if (application != null) {
                    if (supportsManagedEntitlements(application)) {
                        supportedTypes.add("entitlement");
                    }

                    for (Schema schema : Util.iterate(application.getGroupSchemas())) {
                        if (!isNewGroup() || hasProvisioningRight()) {
                            supportedTypes.add(schema.getObjectType());
                        }
                    }
                    
                    if (supportsDirectPermissions(application)) {
                        supportedTypes.add("permission");
                    }
                }
            } catch (GeneralException e) {
                // Don't know if any additional types are supported at this point so no entitlements can be created for the application
                log.error("The AccountGroupEditBean could not determine supported types for the application " + appNameOrId);
            }
        }
        return supportedTypes;
    }
    
    public String save() throws GeneralException {
        boolean isValid = false;
        String returnPage;
        List<Message> returnMessages = new ArrayList<Message>();
        String displayableName = accountGroup.getDisplayableValue(getContext());
        
        authorize(new RightAuthorizer(SPRight.FullAccessGroup, SPRight.ManagedAttributePropertyAdministrator, SPRight.ManagedAttributeProvisioningAdministrator));

        String type = accountGroup.getDisplayType();

        try {
            isValid = validate();
            // Need to do this again after validation because the form wasn't populated the first time we tried to initialize this
            displayableName = accountGroup.getDisplayableValue(getContext());
        } catch (GeneralException e) {
            log.error("The Account Group Edit bean could not successfully validate changes so nothing was saved.", e);
            returnMessages.add(new Message(Message.Type.Error, MessageKeys.MANAGED_ATTRIBUTE_UPDATE_FAILED, type, displayableName));
            isValid = false;
            returnPage = "error";
        }        

        String workflowName = (String) Configuration.getSystemConfig().get(Configuration.WORKFLOW_MANAGED_ATTRIBUTE);
        WorkflowSession session;
        if (workflowName == null) {
            session = new WorkflowSession();
            session.setReturnPage("error");
            session.addReturnMessage(new Message(Message.Type.Error, MessageKeys.MANAGED_ATTRIBUTE_UPDATE_FAILED_WORKFLOW_UNDEFINED, type, displayableName));
        } else if (!isValid) {
            session = new WorkflowSession();
            session.setReturnPage("error");
            session.addReturnMessage(new Message(Message.Type.Error, MessageKeys.MANAGED_ATTRIBUTE_UPDATE_FAILED_VALIDATION_FAILURE, type, displayableName));            
        } else {
            try {
                session = AccountGroupService.launchWorkflow(accountGroup, buildProvisioningPlan(), this);
            } catch (GeneralException e) {
                log.error("The " + workflowName + " workflow could not be launched.", e);
                WorkflowLaunch launch = new WorkflowLaunch();
                launch.setStatus(WorkflowLaunch.STATUS_FAILED);
                Message errorMsg = new Message(MessageKeys.MANAGED_ATTRIBUTE_UPDATE_FAILED_WORKFLOW_UNDEFINED, type, displayableName);
                launch.addMessage(errorMsg);
                session = new WorkflowSession();
                session.setWorkflowLaunch(launch);
                session.addReturnMessage(errorMsg);
                session.setReturnPage("error");
            }
            
            WorkflowLaunch launch = session.getWorkflowLaunch();
            
            if (launch.isFailed()) {
                List<Message> messages = launch.getMessages();
                if (messages != null && !messages.isEmpty()) {
                    for (Message message : messages) {
                        session.addReturnMessage(message);
                    }
                }
                session.setReturnPage("error");
            } else {
                getSessionScope().remove(DTO_SESSION_KEY);
                if (displayableName == null || displayableName.trim().length() == 0) {
                    session.addReturnMessage(new Message(Message.Type.Info, MessageKeys.MANAGED_ATTRIBUTE_UPDATE_SUCCESSFUL_NULL_NAME, type));
                } else {
                    session.addReturnMessage(new Message(Message.Type.Info, MessageKeys.MANAGED_ATTRIBUTE_UPDATE_SUCCESSFUL, type, displayableName));                    
                }
                returnPage = session.getReturnPage();
                // If the WorkflowSession didn't set its own return page use the default behavior
                if (returnPage == null) {
                    String whereToGo = NavigationHistory.getInstance().back();
                    if (!"accountGroupSearchResults".equals(whereToGo)) {
                        NavigationHistory.getInstance().forward();
                        whereToGo = "save";
                    }
                    applySearchIfNecessary(whereToGo);
                    session.setReturnPage(whereToGo);
                }
            }
        }

        returnMessages.addAll(session.getReturnMessages());
        returnPage = session.getReturnPage();
        
        if (returnMessages != null && !returnMessages.isEmpty()) {
            for (Message returnMessage : returnMessages) {
                addMessageToSession(returnMessage);
            }
        }
        
        return returnPage;
    }
    
    public String getOwner() {
        return accountGroup.getOwnerId();
    }
    
    public void setOwner(String owner) {
        if (owner == null || owner.trim().length() == 0) {
            accountGroup.setOwnerId("");
        } else {
            try {
                Identity ownerObj = getContext().getObjectById(Identity.class, owner);
                accountGroup.setOwnerId(ownerObj.getId());
            } catch (GeneralException e) {
                accountGroup.setOwnerId(owner);
            }
        }
    }
    
    public String getScope() {
        return accountGroup.getScopeId();
    }
    
    public void setScope(String scope) {
        if (scope == null || scope.trim().length() == 0) {
            accountGroup.setScopeId("");
        } else {
            try {
                Scope scopeObj = getContext().getObjectById(Scope.class, scope);
                accountGroup.setScopeId(scopeObj.getId());
            } catch (GeneralException e) {
                accountGroup.setScopeId(scope);
            }
        }
    }

    public void setApplication(String application) {
        if (application == null || application.trim().length() == 0 || application.equals("null")) {
            accountGroup.setApplicationName("");
        } else {
            try {
                Application applicationObj = getContext().getObjectByName(Application.class, application);
                if (applicationObj == null) {
                    accountGroup.setApplicationName("");
                    log.error("The application name[ " + application + " ] could not be found.");
                } else {
                    accountGroup.setApplicationName(applicationObj.getName());
                }
            } catch (GeneralException e) {
                log.error("The account group bean was unable to validate the applicaton name[ " + application + "]", e);
                accountGroup.setApplicationName(application);
            }
        }
    }
    
    public String getApplication() {
        return accountGroup.getApplicationName();        
    }
    
    /**
     * An application supports group hierarchies if it has a non-null group hierarchy attribute that is 
     * matches an attribute in the group schema
     * @return True if the selected Application/Attribute combination indicates that this ManagedAttribute
     * is hierarchical.  False otherwise 
     */
    public boolean isSupportsHierarchy() {
        boolean supportsHierarchy = false;
        
        String application = getApplication();
        if (application != null && application.trim().length() > 0) {
            try {
                Application applicationObj = getContext().getObjectByName(Application.class, application);
                if (applicationObj != null) {
                    Schema objectSchema = applicationObj.getSchema(getType());
                    if (objectSchema != null) {
                        Set<String> schemaAttributes = objectSchema.getAttributeNamesHashSet();
                        String hierarchyAttribute = applicationObj.getGroupHierarchyAttribute(getType());
                        if (hierarchyAttribute != null) {
                            supportsHierarchy = schemaAttributes.contains(hierarchyAttribute);
                        }
                    }
                }
            } catch (GeneralException e) {
                log.error("The Account Group Edit Bean is attempting to process a ManagedAttribute that references an inaccessible application named " + application, e);
            }        
        }

        return supportsHierarchy;
    }

    /**
     * This is a misnomer now.  Originally it just refreshed the group attributes when an application was selected,
     * but now it also does whatever crap needs to happen when an application is selected.
     * @return
     */
    public String refreshGroupAttributes() {
        updateType();
        updateProvisioningSetting();
        String appName = accountGroup.getApplicationName();
        try {
            Application applicationObj = getContext().getObjectByName(Application.class, appName);
            if (accountGroup.isGroup() && appName != null && appName.trim().length() > 0) {
                Schema accountSchema = applicationObj.getSchema(Application.SCHEMA_ACCOUNT);
                if (accountSchema != null) {
                    String groupAttribute = accountSchema.getGroupAttribute(accountGroup.getType());

                    accountGroup.setReferenceAttribute(groupAttribute);
                } else {
                    accountGroup.setReferenceAttribute(null);
                }
            } else {
                accountGroup.setReferenceAttribute(null);
            }
        
            // This method can only be called on new account groups so the persisted
            // account group will always be null
            getAccountGroup().resetGroupAttributeFormBean(null);
            Scope appScope = applicationObj == null ? null : applicationObj.getAssignedScope();
            if (appScope != null) {
                accountGroup.setScopeId(appScope.getId());                
            }
        } catch (GeneralException e) {
            log.error("The application named " + accountGroup.getApplicationName() + " could not be found.  The group status for this application's entitlement did not properly persist.", e);
            accountGroup.setGroup(false);
            accountGroup.setReferenceAttribute(null);
        }        

        return "";
    }
    
    public String refreshForms() {
        FormStore groupStore = new GroupAttributeFormStore(accountGroup);
        Map<String,Object> args = getAccountGroup().getArgumentsForFormHandler();
        FormHandler groupFormHandler = new FormHandler(getContext(), groupStore);
        try {
            groupFormHandler.refresh(groupStore.retrieveMasterForm(),
                                     accountGroup.getGroupAttributeFormBean(),
                                     args);            
        } catch (GeneralException e) {
            log.error("Failed to refresh group form for account group " + accountGroup.getDisplayValue(), e);
        }
        FormStore extendedStore = new ExtendedAttributeFormStore(accountGroup);
        FormHandler extendedFormHandler = new FormHandler(getContext(), extendedStore);
        try {
            extendedFormHandler.refresh(extendedStore.retrieveMasterForm(),
                                        accountGroup.getExtendedAttributeFormBean(),
                                        args);
        } catch (GeneralException e) {
            log.error("Failed to refresh group form for account group " + accountGroup.getDisplayValue(), e);
        }
        return "";
    }
    
    public String refreshExtendedAttributes() throws GeneralException {
        // This method can only be called on new account groups so the persisted
        // account group will always be null
        updateProvisioningSetting();
        accountGroup.resetExtendedAttributeFormBean(null);
        return "";        
    }
    
    public String cancel() {
        getSessionScope().remove(DTO_SESSION_KEY);

        String whereToGo = NavigationHistory.getInstance().back();
        if (!"accountGroupSearchResults".equals(whereToGo)) {
            NavigationHistory.getInstance().forward();
            whereToGo = "cancel";
        }
        applySearchIfNecessary(whereToGo);
        
        return whereToGo;
    }
    
    public int getInheritedSize() {
        int inheritedSize;
        List<String> inheritedGroups = accountGroup.getInheritedAccountGroups();
        if (inheritedGroups == null || inheritedGroups.isEmpty()) {
            inheritedSize = 0;
        } else {
            inheritedSize = inheritedGroups.size();
        }
        return inheritedSize;
    }
    
    public int getInheritingSize() {
        int inheritingSize;
        List<String> inheritingGroups = accountGroup.getInheritingAccountGroups();
        if (inheritingGroups == null || inheritingGroups.isEmpty()) {
            inheritingSize = 0;
        } else {
            inheritingSize = inheritingGroups.size();
        }
        return inheritingSize;        
    }
    
    public int getMemberCount() {
        int size = 0;
        try {
            ManagedAttribute entitlement =  getObject();
            AccountGroupService accountGroupSvc = new AccountGroupService(getContext());
            size = accountGroupSvc.getMemberCount(entitlement);
        } catch(GeneralException e) {
            log.error(e);
            size = 0;
        }
        
        return size;
    }
    
    public int getPermissionsCount() {
        int size;
        try {
            ManagedAttribute group = getObject();
            List<Permission> permissions;
            if (group == null) {
                permissions = null;
            } else {
                permissions = group.getAllPermissions();
            }
            if (permissions == null || permissions.isEmpty()) {
                size = 0;
            } else {
                size = permissions.size();
            }
        } catch (GeneralException e) {
            log.error("The AccountGroupEditBean could not fetch the account group", e);
            size = 0;
        }
        return size;
    }

    public boolean isEntitlementsVisible() {
        return getEntitlementCount() > 0 && getEntitlementMaps().size() > 0;
    }
    
    public int getEntitlementCount() {
        int size;
        try {
            ManagedAttribute group = getObject();
            if (group == null) {
                size = 0;
            } else {
                Application app = group.getApplication();
                if (app != null) {
                    Schema groupSchema = app.getSchema(getType());
                    if (groupSchema == null) {
                        size = 0;
                    } else {
                        List<AttributeDefinition> entitlements = groupSchema.getEntitlementAttributes();
                        if (entitlements == null || entitlements.isEmpty()) {
                            size = 0;
                        } else {
                            size = entitlements.size();
                        }                        
                    }
                } else {
                    size = 0;
                }
            }
        } catch (GeneralException e) {
            log.error("The AccountGroupEditBean could not fetch the account group", e);
            size = 0;
        }
        return size;
    }
    
    /**
     * @return List of entitlements.  Each entitlement is represented by a map containing three keys: 
     * displayName, attribute, and value
     * The entries will be rendered into a table on the group attributes tab
     */
    public List<Map<String, String>> getEntitlementMaps() {
        List<Map<String, String>> entitlements = new ArrayList<Map<String, String>>();
        try {
            if(getObject() != null) {
                ManagedAttribute group = getObject();
                Application app = group.getApplication();
                Schema groupSchema = app.getSchema(group.getType());
                List<AttributeDefinition> entitlementDefs = null;
                if (groupSchema != null) {
                    entitlementDefs = groupSchema.getEntitlementAttributes();
                }

                if (!Util.isEmpty(entitlementDefs)) {
                    for (AttributeDefinition entitlementDef : entitlementDefs) {
                        String attribute = entitlementDef.getName();
                        String objectType = entitlementDef.getSchemaObjectType();

                        Object value = group.getAttribute(attribute);
                        if (value instanceof Collection) {
                            Collection<Object> values = (Collection<Object>) value;
                            if (values != null && !values.isEmpty()) {
                                for (Object individual : values) {
                                    String stringValue = getString(individual);
                                    entitlements.add(getEntitlementMap(app, attribute, stringValue, objectType));
                                }
                            }
                        } else {
                            String stringValue = getString(value);
                            if (Util.isNotNullOrEmpty(stringValue)) {
                                entitlements.add(getEntitlementMap(app, attribute, stringValue, objectType));
                            }
                        }
                    }
                }
            }
        } catch (GeneralException e) {
            log.error("Error getting entitlements", e);
        }
        
        return entitlements;
    }
    
    private String getString(Object value) {
        String stringValue;
        if (value == null) {
            stringValue = "";
        } else if (value instanceof Date) {
            stringValue = Util.dateToString((Date)value);
        } else {
            stringValue = value.toString();
        }
        
        return stringValue;
    }
    
    private Map<String, String> getEntitlementMap(Application app, String attribute, String value, String objectType)
        throws GeneralException {

        // if object type is specified then we need to send the attribute as null
        // because that is how it is stored in the MA table, but we want to keep the
        // original attribute value around to show in the entitlements table in the ui
        String attributeForSearch = attribute;
        if (!Util.isNullOrEmpty(objectType)) {
            attributeForSearch = null;
        }

        String displayName;
        ManagedAttribute ma = ManagedAttributer.get(getContext(), app, false, attributeForSearch, value, objectType);
        if (ma == null) {
            displayName = value;
        } else {
            displayName = ma.getDisplayableName();
        }
        Map<String, String> entitlementMap = new HashMap<String, String>();
        entitlementMap.put("displayName", displayName);
        entitlementMap.put("attribute", attribute);
        entitlementMap.put("value", value);
        return entitlementMap;
    }
    
    public String getPermissionColumnJson() {
        if(permissionColumns == null) {
            try {
                permissionColumns = getUIConfig().getAccountGroupPermissionTableColumns();
            } catch (GeneralException ge) {
                log.warn("Unable to load Account Group Permission Column Config.  Exception:" + ge.getMessage());
            }
        }

        return getColumnJSON("target", permissionColumns);
    }
    
    public boolean isDisplayEntitlementDescriptions() {
        return displayEntitlementDescriptions;
    }
    
    /**
     * @return true if this is a new ManagedAttribute; false otherwise -- Note that this doesn't just apply to groups.
     * The name is a remnant from when this page was exclusively for editing account groups.
     */
    public boolean isNewGroup() {
        return accountGroup.isNew();
    }

    public boolean isAttributeVisible() throws GeneralException {
        String type = getType();
        if (Util.isNullOrEmpty(type)) {
            return true;
        }

        String permission = ManagedAttribute.Type.Permission.name();
        String entitlement = ManagedAttribute.Type.Entitlement.name();

        return entitlement.equalsIgnoreCase(type) ||
               (!permission.equalsIgnoreCase(type) && hasGroupAttribute(type));
    }

    private boolean hasGroupAttribute(String objectType) throws GeneralException {
        boolean hasGroupAttribute = false;

        Application app = getContext().getObjectByName(Application.class, accountGroup.getApplicationName());
        if (app != null) {
            Schema schema = app.getSchema(Application.SCHEMA_ACCOUNT);
            if (schema != null) {
                hasGroupAttribute = schema.getGroupAttribute(objectType) != null;
            }
        }

        return hasGroupAttribute;
    }
    
    /**
     * Custom comparator that sorts roles by type or by name if the types match
     */
    public static final Comparator<ManagedAttribute> GROUP_COMPARATOR = new Comparator<ManagedAttribute>() {
        public int compare(ManagedAttribute o1, ManagedAttribute o2) {
            int result;
            
            if (o1 == null && o2 == null) {
                result = 0;
            } else if (o1 == null) {
                result = -1;
            } else if (o2 == null) {
                result = 1;
            } else {
                result = Internationalizer.INTERNATIONALIZED_STRING_COMPARATOR.compare(o1.getName(), o2.getName());
            } 

            return result;
        }
    };
    
    private ProvisioningPlan buildProvisioningPlan() throws GeneralException {
        ObjectRequest or = new ObjectRequest();
        or.setApplication(accountGroup.getApplicationName()); 
        or.setType(accountGroup.isGroup() ? accountGroup.getType() : ProvisioningPlan.OBJECT_TYPE_MANAGED_ATTRIBUTE);
        or.setNativeIdentity(trim(accountGroup.getNativeIdentity()));
        if (accountGroup.getId() == null || accountGroup.getId().equals("new") || accountGroup.getId().trim().length() == 0) {
            or.setOp(ObjectOperation.Create);
        } else {
            or.setOp(ObjectOperation.Modify);
        }
                
        List<AttributeRequest> standardAttributeRequests = accountGroup.getStandardAttributeRequests();
        or.addAll(standardAttributeRequests);
        //Don't add groupAttributeRequests for new groups with provisioning disabled. We don't want to pass these to the
        //connector
        if (isNewGroup() && !isProvisioningEnabled()) {
            //Set no Provisioning Argument so this won't be passed to the integration
            or.addArgument(ObjectRequest.ATT_OBJ_REQ_NO_PROVISIONING, true);
        } else {
            List<AttributeRequest> groupAttributeRequests = accountGroup.getGroupAttributeRequests();
            syncDisplayValue(standardAttributeRequests, groupAttributeRequests);
            or.addAll(groupAttributeRequests);
        }

        or.addAll(accountGroup.getExtendedAttributeRequests());
        
        String groupHierarchyAttribute = getGroupHierarchyAttribute();
        if (groupHierarchyAttribute != null) {
            List<String> accountGroupIDs = accountGroup.getInheritedAccountGroups();
            if (accountGroupIDs == null || accountGroupIDs.isEmpty()) {
                or.add(new AttributeRequest(groupHierarchyAttribute, Operation.Set, null));
            } else {
                List<Object> groupValues = new ArrayList<Object>();
                for (String accountGroupID : accountGroupIDs) {
                    ManagedAttribute group = getContext().getObjectById(ManagedAttribute.class, accountGroupID);
                    if (group != null) {
                        groupValues.add(group.getValue());
                    } // otherwise it was deleted out from under us and we should ignore it
                }
                
                if (groupValues.isEmpty()) {
                    // Shouldn't happen unless everything was deleted out from under us
                    or.add(new AttributeRequest(groupHierarchyAttribute, Operation.Set, null));
                } else {
                    or.add(new AttributeRequest(groupHierarchyAttribute, Operation.Set, groupValues));                    
                }
            }
        }
        
        ProvisioningPlan plan = new ProvisioningPlan();
        plan.setSource(Source.GroupManagement);
        plan.addRequester(getLoggedInUser());
        plan.addRequest(or);

        return plan;
    }
    
    private boolean validate() throws GeneralException {
        boolean isValid = true;
        String application = accountGroup.getApplicationName();
        String value = accountGroup.getNativeIdentity();
        String attribute = accountGroup.getReferenceAttribute();
        String displayableName = accountGroup.getDisplayableValue(getContext());
        boolean isFormPopulated = false;
        
        if (isNewGroup()) {
            if (!AccountGroupService.isProvisioningEnabled(getLoggedInUserCapabilities(), getLoggedInUserRights())) {
                String modifyEntitlementRight = new Message(MessageKeys.RIGHT_MANAGED_ATTRIBUTE_PROVISIONING_ADMIN).getLocalizedMessage(getLocale(), null);
                addMessageToSession(new Message(Type.Error, MessageKeys.ERR_MANAGED_ATTRIBUTE_CREATION_REQUIRES_RIGHT, modifyEntitlementRight));
                return false;
            }
            
            // jsl - for MAs uniqueness is defined with the combination
            // of application id, attribute name, and value, or what
            // the AccountGroup model called application, referenceAttribute
            // and nativeIdentity.  The AccountGroup.name property 
            // will become the ManagedAttribute.displayableName property.
            SailPointContext spc = getContext();
            Application app = spc.getObjectByName(Application.class, application);
            //If attribute is null, we need to pass the group Type. -rap
            ManagedAttribute existing = ManagedAttributer.get(getContext(), app, ManagedAttribute.Type.Permission.name().equalsIgnoreCase(accountGroup.getType()), attribute, value, attribute == null ? accountGroup.getType() : null);
            String alternateValue = null;
            if (existing == null && accountGroup.isGroup()) {
                // If this is a new group (as opposed to just a generic ManagedAttribute we need to 
                // try again with the identity attribute instead of the value just in case the native
                // identity was being specified on the form
                Schema groupSchema = app.getSchema(accountGroup.getType());
                String identityAttribute = groupSchema.getIdentityAttribute();
                // Populate the form so that we can get the correct group attribute out of it
                FormHandler handler = new FormHandler(getContext(), new GroupAttributeFormStore(accountGroup));
                FormRenderer groupAttributeFormBean = accountGroup.getGroupAttributeFormBean();
                isValid &= handler.submit(accountGroup.getGroupAttributeMasterForm(), groupAttributeFormBean, accountGroup.getArgumentsForFormHandler());
                isFormPopulated = true;
                alternateValue = accountGroup.getGroupAttributeValue(identityAttribute);
                if (alternateValue != null) {
                    existing = ManagedAttributer.get(getContext(), app, false, attribute, alternateValue, accountGroup.getType());
                }
                
                // If this is a group, provisioning is enabled, and the application allows us to look up  
                // objects check the connector to verify that we are creating a new group
                if (existing == null && accountGroup.isGroup() && isProvisioningEnabled() && !app.supportsFeature(Feature.NO_RANDOM_ACCESS)) {
                    Connector connector = ConnectorFactory.getConnector(app, null);
                    try {
                        ResourceObject ro;
                        if (alternateValue != null && alternateValue.trim().length() > 0) {
                            ro = connector.getObject(accountGroup.getType(), alternateValue, null);
                        } else {
                            ro = connector.getObject(accountGroup.getType(), value, null);
                        }
                        
                        if (ro != null) {
                            isValid = false;
                            addMessageToSession(new Message(Type.Error, MessageKeys.MANAGED_ATTRIBUTE_ALREADY_EXISTS, type, accountGroup.getApplicationName(), accountGroup.getReferenceAttribute(), ro.getIdentity()));
                        }
                    } catch (ConnectorException e) {
                        if (!(e instanceof ObjectNotFoundException)) {
                            log.info("Could not determine whether or not the " + value + " group on the " + application + " application already exists.", e);
                        } // Otherwise we got an expected error condition and validation passed
                    }
                }
            }
            if (existing != null) {
                String type = new Message(accountGroup.getType()).getLocalizedMessage(getLocale(), getUserTimeZone());
                
                if (value == null || value.trim().length() == 0) {
                    value = alternateValue;
                }
                
                if (ManagedAttribute.Type.Permission.name().equalsIgnoreCase(accountGroup.getType())) {
                    addMessageToSession(new Message(Type.Error, MessageKeys.MANAGED_ATTRIBUTE_PERMISSION_ALREADY_EXISTS, accountGroup.getApplicationName(), accountGroup.getReferenceAttribute()));
                } else {
                    addMessageToSession(new Message(Type.Error, MessageKeys.MANAGED_ATTRIBUTE_ALREADY_EXISTS, type, accountGroup.getApplicationName(), accountGroup.getReferenceAttribute(), value));
                }
                isValid = false;
            }
            
            if (application == null || application.trim().length() == 0) {
                addMessageToSession(new Message(Type.Error, MessageKeys.APPLICATION_REQUIRED));
                isValid = false;
            }
            
            // Permissions don't have a value so no need to validate it for them.
            // Provisionable groups are special and need to be validated in a different way -- 
            // Defer their validation until after the form bean has been processed
            if (!ManagedAttribute.Type.Permission.name().equalsIgnoreCase(accountGroup.getType()) &&
                    (!accountGroup.isGroup() || (accountGroup.isGroup() && !accountGroup.isProvisioningEnabled())) && 
                    (value == null || value.trim().length() == 0)) {
                addMessageToSession(new Message(Type.Error, MessageKeys.MANAGED_ATTRIBUTE_VALUE_REQUIRED, new Message(accountGroup.getType()).getLocalizedMessage(getLocale(), getUserTimeZone())));
                isValid = false;
            }
            
            if (!accountGroup.isGroup() && Util.isNullOrEmpty(attribute)) {
                String type = new Message(accountGroup.getType()).getLocalizedMessage(getLocale(), getUserTimeZone());

                // Permsissions have a slightly different message because they don't have an attribute; they have a target
                String targetOrAttribute;
                if (ManagedAttribute.Type.Permission.name().equalsIgnoreCase(accountGroup.getType())) {
                    targetOrAttribute = new Message(MessageKeys.TARGET).getLocalizedMessage(getLocale(), getUserTimeZone());
                } else {
                    targetOrAttribute = new Message(MessageKeys.ATTRIBUTE).getLocalizedMessage(getLocale(), getUserTimeZone());
                }
                
                addMessageToSession(new Message(Type.Error, MessageKeys.MANAGED_ATTRIBUTE_ATTRIBUTE_REQUIRED, type, targetOrAttribute));
                isValid = false;
            }
        }

        if (accountGroup.isGroup() && isProvisioningEnabled()) {
            FormRenderer groupAttributeFormBean = accountGroup.getGroupAttributeFormBean();
            // We may already have populated the form if this was a new group.  
            // If that's the case it's a waste to do it again
            if (!isFormPopulated) {
                FormHandler handler = new FormHandler(getContext(), new GroupAttributeFormStore(accountGroup));
                isValid &= handler.submit(accountGroup.getGroupAttributeMasterForm(), groupAttributeFormBean, accountGroup.getArgumentsForFormHandler());
            }
            // Validate after populating the form
            displayableName = accountGroup.getDisplayableValue(getContext());
            if (!groupAttributeFormBean.validate(accountGroup.getArgumentsForFormHandler())) {
                addMessageToSession(new Message(Type.Error, MessageKeys.MANAGED_ATTRIBUTE_ATTRIBUTES_INVALID, new Message(MessageKeys.GROUP).getLocalizedMessage(getLocale(), getUserTimeZone()), displayableName));
                isValid = false;
            }
        }

        String attributeType = accountGroup.getDisplayType();        
        FormHandler handler = new FormHandler(getContext(), new ExtendedAttributeFormStore(accountGroup));
        FormRenderer extendedAttributeFormBean = accountGroup.getExtendedAttributeFormBean();
        extendedAttributeFormBean.setManualContext(getContext());
        if (!handler.submit(accountGroup.getExtendedAttributeMasterForm(), extendedAttributeFormBean, accountGroup.getArgumentsForFormHandler())) {
            addMessageToSession(new Message(Type.Error, MessageKeys.MANAGED_ATTRIBUTE_ATTRIBUTES_INVALID, new Message(attributeType).getLocalizedMessage(getLocale(), getUserTimeZone()), displayableName));
            isValid = false;
        }
        
        return isValid;
    }
    
    /*
     * Initialize the provisioning setting as follows:
     * The user must have the provisioning right to receive support for provisioning.
     * The system configuration must also have the setting that supports provisioning.
     * LCM must be enabled
     * Group provisioning must be enabled on LCM
     */
    public boolean initProvisioningSetting() {
        boolean provisioningEnabled;
        provisioningEnabled = hasProvisioningRight();
        Configuration systemConfig = Configuration.getSystemConfig();
        provisioningEnabled &= Version.isLCMEnabled();
        provisioningEnabled &= Util.otob(systemConfig.getBoolean(Configuration.LCM_MANAGE_ACCOUNTS_ALLOW_GROUP_MANAGEMENT));
        return provisioningEnabled;
    }
    
    /*
     * Update the provisioning setting as follows:
     * There are three requirements that a group has to meet to support provisioning:
     * 1. Only ManagedAttributes with applications that have a group attribute are groups
     * 2. The ManagedAttribute's application must support provisioning
     * 3. The ManagedAttribute's application must have an appropriate provisioning policy in place
     */
    private void updateProvisioningSetting() {
        String appName = accountGroup.getApplicationName();
        updateProvisioningSetting(appName, accountGroup.getType());
    }
    
    private void updateProvisioningSetting(String appName, String schemaObjectType) {
        boolean provisioningEnabled = initProvisioningSetting();
        if (provisioningEnabled) {
            if ((appName == null || appName.trim().length() == 0)) {
                provisioningEnabled = false;
            } else {
                try {
                    Application app = getContext().getObjectByName(Application.class, appName);
                    provisioningEnabled = app.supportsGroupProvisioning(schemaObjectType);
                    if (isNewGroup()) {
                        // Upon creation we require a 'Create' or 'Update' provisioning policy
                        provisioningEnabled &= (app.getOldTemplate(Usage.Create, schemaObjectType) != null || app.getOldTemplate(Usage.Update, schemaObjectType) != null);
                    } else {
                        // Other times we require an 'Update' provisioning policy
                        provisioningEnabled &= app.getOldTemplate(Usage.Update, schemaObjectType)  != null;
                    }
                } catch (GeneralException e) {
                    log.error("The account group edit bean is unable to find an application named " + appName, e);
                    provisioningEnabled = false;
                }
            }
        }
        accountGroup.setProvisioningEnabled(provisioningEnabled);
    }
    
    private boolean hasProvisioningRight() {
        boolean hasProvisioningRight;
        List<Capability> capabilities = getLoggedInUserCapabilities();
        Set<String> rights = new HashSet<String>(getLoggedInUserRights());
        hasProvisioningRight = rights.contains(SPRight.ManagedAttributeProvisioningAdministrator) || Capability.hasSystemAdministrator(capabilities);
        return hasProvisioningRight;
    }
    
    public String getGroupHierarchyAttribute() {
        String groupHierarchyAttribute = null;
        
        String application = getApplication();
        if (application != null && application.trim().length() > 0) {
            try {
                Application applicationObj = getContext().getObjectByName(Application.class, application);
                if (applicationObj != null) {
                    Schema groupSchema = applicationObj.getSchema(getType());
                    if (groupSchema != null) {
                        groupHierarchyAttribute = applicationObj.getGroupHierarchyAttribute(getType());
                    }
                }
            } catch (GeneralException e) {
                log.error("The Account Group Edit Bean is attempting to process a ManagedAttribute that references an inaccessible application named " + application, e);
            }        
        }

        return groupHierarchyAttribute;
    }

    /**
     * @return true if the ManagedAttribute type was overridden when a non-supporting app was selected; false otherwise
     */
    public boolean isTypeChanged() {
        return typeChanged;
    }
    
    public boolean isShowRequestableOption() throws GeneralException {
        return LCMConfigService.isLCMEnabled() && isAttributeVisible();
    }
    
    /*
     * This method handles bug 13542.  IIQ has a display name and the applications to which groups are provisioned frequently
     * have native display names.  There is some expectation that setting the display name on one is the equivalent of setting
     * the display name on the other.  This expectation is false, but we try to accomodate it nonetheless by following these
     * guidelines when creating new groups:
     *     
     *     If the IIQ display name is set and the native display value is null we apply the IIQ display 
     *     value to the native display value if (and only if) the native display value is editable in the create form.
     *     If the native display attribute is not available in the create form we assume that it's a derived value
     *     that we cannot manually set.
     *     
     *     If the IIQ display value is null and the native display value is set we apply the native display value
     *     to the IIQ display value.  Note that we are still deficient with respect to "derived" native display values.
     *     The ResourceObject corresponding to the newly created group may have a display value that was derived during
     *     the creation process.  In order to get that derived value we would have to put it (or a ResourceObject containing it)
     *     on the WorkflowSession and we're not doing that right now.  For the time being we're punting on that because if 
     *     the display value remains null it will be corrected the next time a group aggregation refreshes it.
     *     
     *     If both display values are set or both display values are null we leave them as-is 
     *     
     */
    private void syncDisplayValue(List<AttributeRequest> standardAttributeRequests, List<AttributeRequest> groupAttributeRequests) {
        if (groupAttributeRequests != null && !groupAttributeRequests.isEmpty()) {
            String iiqDisplayValue = accountGroup.getDisplayValue();
            
            // Fetch the display attribute, the native display value, and the index of the request that's setting the native diplay value
            String displayAttribute = null;
            String nativeDisplayValue = null;
            int nativeDisplayValueRequestIdx = -1;
            int iiqDisplayValueRequestIdx = -1;
            boolean isWritableNativeDisplayAttributeInCreateForm = false;
            String application = getApplication();
            if (application != null && application.trim().length() > 0) {
                try {
                    Application applicationObj = getContext().getObjectByName(Application.class, application);
                    if (applicationObj != null) {
                        Schema groupSchema = applicationObj.getSchema(getType());
                        if (groupSchema != null) {
                            displayAttribute = groupSchema.getDisplayAttribute();
                            if (displayAttribute != null && displayAttribute.trim().length() > 0) {
                                // Get the native display value and request index
                                for (int i = 0; i < groupAttributeRequests.size(); ++i) {
                                    AttributeRequest request = groupAttributeRequests.get(i);
                                    if (request != null) {
                                        String requestName = request.getName();
                                        if (requestName != null && requestName.equals(displayAttribute)) {
                                            nativeDisplayValue = request.getValue() == null ? null : request.getValue().toString();
                                            nativeDisplayValueRequestIdx = i;
                                            break;
                                        }
                                    }
                                }

                                // Get the Form from the DTO.  Note that this should already have
                                // been initialized and populated earlier during validate().
                                FormRenderer groupAttributeFormBean = accountGroup.getGroupAttributeFormBean();
                                Form form = groupAttributeFormBean.getForm();
                                isWritableNativeDisplayAttributeInCreateForm = isWritableAttributeInForm(form, displayAttribute);
                            }
                        }
                    }
                } catch (GeneralException e) {
                    log.error("The Account Group Edit Bean is attempting to process a ManagedAttribute that references an inaccessible application named " + application, e);
                }        
            }
            
            // Fetch the index of the request that's setting the IIQ display value
            for (int i = 0; i < standardAttributeRequests.size(); ++i) {
                AttributeRequest request = standardAttributeRequests.get(i);
                if (request != null) {
                    String requestName = request.getName();
                    if (requestName != null && ManagedAttribute.PROV_DISPLAY_NAME.equals(request.getName())) {
                        iiqDisplayValueRequestIdx = i;
                    }
                }
            }

            if (isNewGroup() && displayAttribute != null && iiqDisplayValue != null && nativeDisplayValue == null && isWritableNativeDisplayAttributeInCreateForm) {
                // We have an IIQ display value but no native display value on a new group
                // Force the native display value to be the IIQ display value
                AttributeRequest displayValueRequest = new AttributeRequest(displayAttribute, Operation.Set, iiqDisplayValue);
                if (nativeDisplayValueRequestIdx > 0) {
                    groupAttributeRequests.remove(nativeDisplayValueRequestIdx);
                    groupAttributeRequests.add(nativeDisplayValueRequestIdx, displayValueRequest);
                } else {
                    groupAttributeRequests.add(displayValueRequest);
                }
            } else if (isNewGroup() && (iiqDisplayValue == null || iiqDisplayValue.trim().length() == 0) && displayAttribute != null && nativeDisplayValue != null) {
                // When we're missing an IIQ display value but we have a native display value IIQ forcibly defers to the native display value on new groups
                AttributeRequest displayValueRequest = new AttributeRequest(ManagedAttribute.PROV_DISPLAY_NAME, Operation.Set, nativeDisplayValue);
                if (iiqDisplayValueRequestIdx > 0) {
                    standardAttributeRequests.remove(iiqDisplayValueRequestIdx);
                    standardAttributeRequests.add(iiqDisplayValueRequestIdx, displayValueRequest);
                } else {
                    standardAttributeRequests.add(displayValueRequest);
                }
            }
        }
    }
    
    private boolean supportsDirectPermissions(Application application) {
        boolean supportsDirectPermissions = application.isSupportsDirectPermissions();
        boolean schemaIncludesPermissions = false;
        List<Schema> schemas = application.getSchemas();
        if (schemas != null && !schemas.isEmpty()) {
            for (Schema schema : schemas) {
                schemaIncludesPermissions |= schema.includePermissions();
            }
        }
        supportsDirectPermissions &= schemaIncludesPermissions;
        return supportsDirectPermissions;
    }
    
    private boolean supportsManagedEntitlements(Application application) {
        boolean supportsManagedEntitlements = false;

        // get all group attributes configured on the account schema
        // and then check for an attribute definition that is managed
        // but not a group attribute
        List<AttributeDefinition> groupAttrs = application.getGroupAttributes();
        Schema schema = application.getSchema(Application.SCHEMA_ACCOUNT);
        if (schema != null) {
            List<AttributeDefinition> schemaAttributes = schema.getAttributes();
            if (schemaAttributes != null && !schemaAttributes.isEmpty()) {
                for (AttributeDefinition schemaAttribute : schemaAttributes) {
                    if (schemaAttribute.isManaged() && !groupAttrs.contains(schemaAttribute)) {
                        supportsManagedEntitlements = true;
                    }
                }
            }
        }

        return supportsManagedEntitlements;
    }
    //TODO: Need to think about how this works
    private void selectSupportedType(List<String[]> supportedTypes) {
        // Default to a supported type for this application
        if (supportedTypes != null && !supportedTypes.isEmpty()) {
            type = supportedTypes.get(0)[0];
            if ("permission".equalsIgnoreCase(type)) {
                accountGroup.setType(ManagedAttribute.Type.Permission.name());
            } else if ("entitlement".equalsIgnoreCase(type)) {
                accountGroup.setType(ManagedAttribute.Type.Entitlement.name());
            } else {
                accountGroup.setType(type);
            }

            if (accountGroup.isGroup()) {
                String appNameOrId = getApplication();
                if (appNameOrId != null && appNameOrId.trim().length() > 0) {
                    try {
                        Application application = getContext().getObjectByName(Application.class, appNameOrId);
                        if (application != null) {
                            AttributeDefinition groupAttribute = application.getGroupAttribute(type);
                            if (groupAttribute != null) {
                                String groupAttributeName = groupAttribute.getName();
                                accountGroup.setReferenceAttribute(groupAttributeName);                                        
                            }
                        }
                    } catch (GeneralException e) {
                        // This will not happen because if it was possible to get here the supportedTypesList above 
                        // would not have come back supporting groups
                        log.error("AccountGroupEditBean could not get a group attribute", e);
                    }
                }
            }
        } else {
            accountGroup.setType(null);
            setType(null);
        }
    }
    
    private void applySearchIfNecessary(String whereToGo) {
        if ("accountGroupSearchResults".equals(whereToGo)) {
            getSessionScope().put(AnalyzeControllerBean.CURRENT_CARD_PANEL, AnalyzeControllerBean.ACCOUNT_GROUP_SEARCH_RESULTS);
        }
    }
    
    /*  
     * @return true if the field is in the form and it's writable; 
     *         false if it's not in the form or if it's read-only
     */
    private boolean isWritableAttributeInForm(Form provisioningForm, String attribute) {
        if (attribute == null) {
            return false;
        }
        
        boolean isInForm = false;
        if (provisioningForm != null) {
            Field field = provisioningForm.getField(attribute);
            isInForm = (null != field) && !field.isReadOnly();
        }
        
        return isInForm;
    }


    //*********************************************
    // Classification
    //*********************************************
    
    //since we allow add/remove classification, we have to do in-memory sort
    public String getClassificationGridJson() throws GeneralException {
        SortParams sortParams = createSortParams();
        List<Map<String, Object>> clsDTOs = getAccountGroup().getClassifications();
        
        if(sortParams.getSort() != null) {
            Collections.sort(clsDTOs, new MapValueComparator(sortParams.getSort(), sortParams.isAscending()));
        }

        try {
            Writer stringWriter = new StringWriter();
            JSONWriter jsonWriter = new JSONWriter(stringWriter);
            jsonWriter.object();

            jsonWriter.key("count");
            jsonWriter.value(Util.size(clsDTOs));

            
            jsonWriter.key("classifications");
            JSONArray clsArray = new JSONArray();

            for (Map<String, Object> cls : Util.safeIterable(getItemsInRange(sortParams, clsDTOs))) {
                JSONObject clsAttrs = new JSONObject(cls);
                clsArray.put(clsAttrs);
            }
            jsonWriter.value(clsArray);

            jsonWriter.endObject();
            return stringWriter.toString();
        } catch (JSONException ex) {
            throw new GeneralException(ex.getCause());
        }
    }

    //returns sub-items defined in sort params
    protected List<Map<String, Object>> getItemsInRange(SortParams params, List<Map<String, Object>> items) {
        if (items == null) {
            return null;
        }
        int start = params.getStart();
        int end = start + params.getLimit();
        if (end > items.size()) {
            end = items.size();
        }
        return items.subList(start, end);
    }
    
    public void addClassification() throws GeneralException {
        getAccountGroup().addCurrentClassification(getContext(), getCurrentClassificationIdToAdd());
    }

    public void removeClassifications() throws GeneralException {
        getAccountGroup().removeCurrentClassifications(getCurrentClassificationIdsToRemove());
        setCurrentClassificationIdsToRemove(null);
    }

    public void setCurrentClassificationIdToAdd(String currentClassificationIdToAdd) {
        this.currentClassificationIdToAdd = currentClassificationIdToAdd;
    }

    public String getCurrentClassificationIdToAdd() {
        return this.currentClassificationIdToAdd;
    }


    public List<String> getCurrentClassificationIdsToRemove() {
        return this.currentClassificationIdsToRemove;
    }

    public void setCurrentClassificationIdsToRemove(List<String> currentClassificationIdsToRemove) {
        this.currentClassificationIdsToRemove = currentClassificationIdsToRemove;
    }

    private SortParams createSortParams() {

        SortParams params = new SortParams();

        params.setStart(Util.atoi(getRequestParameter("start")));
        params.setLimit(getResultLimit());
        String direction = super.getRequestParameter("dir");
        String sort = getRequestParameter("sort");
        if (direction != null) {
            params.setAscending(direction.equalsIgnoreCase("ASC"));
            params.setSort(sort);
        } else {
            JSONArray sortArray = null;
            try {
                sortArray = new JSONArray(sort);
                JSONObject sortObject = sortArray.getJSONObject(0);
                direction = sortObject.getString("direction");
                params.setAscending("ASC".equalsIgnoreCase(direction));
                params.setSort(sortObject.getString("property"));
            } catch (Exception e) {
                log.debug("Invalid sort input.");
                params.setAscending(false);
            }
        }

        return params;
    }

    private static class SortParams {
        private int start;
        private int limit;
        private boolean ascending;
        private String sort;

        public int getStart() {
            return this.start;
        }

        public void setStart(int start) {
            this.start = start;
        }

        public int getLimit() {
            return this.limit;
        }

        public void setLimit(int limit) {
            this.limit = limit;
        }

        public boolean isAscending() {
            return this.ascending;
        }

        public void setAscending(boolean ascending) {
            this.ascending = ascending;
        }

        public String getSort() {
            return sort;
        }

        public void setSort(String sort) {
            this.sort = sort;
        }
    }

    public class MapValueComparator implements Comparator {
        
        String property;
        boolean isAscending = true;
        
        public MapValueComparator(String sortKey, boolean ascending) {
            this.property = sortKey;
            this.isAscending = ascending;
        }

        @Override
        public int compare(Object o1, Object o2) {
            Map<String, Object> map1 = cast(o1);
            Map<String, Object> map2 = cast(o2);
            String s1 = (String)map1.get(property);
            String s2 = (String)map2.get(property);
            s1 = s1 == null ? s1 : s1.toLowerCase();
            s2 = s2 == null ? s2 : s2.toLowerCase();
            
            if (isAscending) {
                return Util.nullSafeCompareTo(s1, s2);
            } else {
                return Util.nullSafeCompareTo(s2, s1);
            }
        }
        
        protected Map<String, Object> cast(Object obj) {
            return (Map<String, Object>) obj;
        }
    }
    

    
    ////////////////////////////////////////////////////////////////////////////
    //
    // FormBean interface
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Return the FormRenderer for the requested form.
     */
    @Override
    public FormRenderer getFormRenderer(String formId) throws GeneralException {

        // Check the form ID's to determine which form we're dealing with.
        FormRenderer renderer = getAccountGroup().getExtendedAttributeFormBean();
        if (!formId.equals(renderer.getForm().getId())) {
            renderer = getAccountGroup().getGroupAttributeFormBean();
        }

        return renderer;
    }
    
    /**
     * Return the arguments used by Formicator.
     */
    @Override
    public Map<String,Object> getFormArguments() throws GeneralException {
        return getAccountGroup().getArgumentsForFormHandler();
    }
    
    /**
     * Return a state object that can be used to reconstruct this bean - we only
     * need the ID for now.
     */
    @Override
    public Map<String,Object> getFormBeanState() {
        Map<String,Object> state = new HashMap<String,Object>();
        state.put(MANAGED_ATTRIBUTE_ID, Util.getString(getObjectId()));
        return state;
    }
}
