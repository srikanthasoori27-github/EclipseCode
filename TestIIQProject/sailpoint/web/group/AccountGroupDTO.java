/* (c) Copyright 2013 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.group;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.json.JSONArray;
import org.json.JSONException;

import sailpoint.api.Localizer;
import sailpoint.api.SailPointContext;
import sailpoint.object.Application;
import sailpoint.object.Classification;
import sailpoint.object.Configuration;
import sailpoint.object.Field;
import sailpoint.object.Filter;
import sailpoint.object.Form;
import sailpoint.object.Identity;
import sailpoint.object.ManagedAttribute;
import sailpoint.object.ObjectAttribute;
import sailpoint.object.ObjectConfig;
import sailpoint.object.ProvisioningPlan.AttributeRequest;
import sailpoint.object.ProvisioningPlan.Operation;
import sailpoint.object.QueryOptions;
import sailpoint.object.Schema;
import sailpoint.object.Scope;
import sailpoint.object.Template;
import sailpoint.object.Template.Usage;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.BaseDTO;
import sailpoint.web.FormHandler;
import sailpoint.service.form.renderer.extjs.FormRenderer;
import sailpoint.web.extjs.DescriptionData;
import sailpoint.web.messages.MessageKeys;

/**
 * 
 * @author bernie.margolis
 */
public class AccountGroupDTO extends BaseDTO {
    private static final Log log = LogFactory.getLog(AccountGroupDTO.class);
    
    private AccountGroupEditBean formBean;

    private String id;
    private String displayValue;
    private DescriptionData descriptionData;
    private String applicationName;
    private String referenceAttribute;
    private String nativeIdentity;
    private String nativeIdentityAttribute;
    private boolean requestable;
    private boolean group;
    private String type;
    private boolean aggregated;
    
    /**
     * List of Ids of AccountGroups that inherit this one
     */
    private List<String> inheritingAccountGroups;
    /**
     * List of Ids of AccountGroups that this one inherits
     */
    private List<String> inheritedAccountGroups;
    private String ownerId;
    private String scopeId;
    private Form extendedAttributeMasterForm;
    private Form extendedAttributeExpandedForm;
    private FormRenderer extendedAttributeFormBean;
    private Form groupAttributeMasterForm;
    private Form groupAttributeExpandedForm;
    private FormRenderer groupAttributeFormBean;
    private boolean containsGroupAttributes;
    private boolean containsExtendedAttributes;
    private Map<String, Object> originalGroupFields;
    /*
     *  isProvisioningEnabled should only be set to true when all of these conditions apply:
     *  1. The edited group's application supports provisioining
     *  2. The user editing the group has provisioning capabilities
     *  3. LCM is enabled
     */
    private boolean isProvisioningEnabled;
    
    /* These are not editable for 6.0 but we will possibly add them in future releases.
    private List<Permission> permissions;
    private List<Permission> targetPermissions;
     */

    private boolean useLocalizedDescriptions;
    
    private String defaultLocale;
    
    //Map to hold Classification properties to support in-memory add/remove classification.
    private Map<String, Map<String, Object>> classificationsMap = new HashMap<>();
    
    
    ////////////////////////////////////////////////////////////////////////////
    //
    // CONSTRUCTORS
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Constructor for a new AccountGroup.
     */
    public AccountGroupDTO(boolean withProvisioning, AccountGroupEditBean formBean) {
        super();
        // Make Entitlement the Default Type
        this.type = ManagedAttribute.Type.Entitlement.name();
        this.isProvisioningEnabled = withProvisioning;
        this.formBean = formBean;
        initialize(formBean.getContext(), null);
    }

    /**
     * Constructor for an existing AccountGroup.
     */
    public AccountGroupDTO(ManagedAttribute accountGroup, boolean withProvisioning, AccountGroupEditBean formBean) {
        super();
        
        this.formBean = formBean;
        SailPointContext context = formBean.getContext();
        
        this.id = accountGroup.getId();
        this.displayValue = accountGroup.getDisplayName();
        
        this.applicationName = accountGroup.getApplication().getName();
        this.referenceAttribute = accountGroup.getAttribute();
        this.nativeIdentity = accountGroup.getValue();
        this.requestable = accountGroup.isRequestable();
        this.aggregated = accountGroup.isAggregated();
        this.type = accountGroup.getType();
        if (this.type == null) {
            // Make Entitlement the default type
            this.type = ManagedAttribute.Type.Entitlement.name();
        }
        Identity owner = accountGroup.getOwner();
        if (owner == null) {
            this.ownerId = null;
        } else {
            this.ownerId = owner.getId();
        }
        Scope scope = accountGroup.getAssignedScope();
        if (scope == null) {
            this.scopeId = null;
        } else {
            this.scopeId = scope.getId();
        }

        this.inheritingAccountGroups = initInheritingAccountGroups(accountGroup);
        this.inheritedAccountGroups = initInheritedAccountGroups(accountGroup);
        this.isProvisioningEnabled = withProvisioning;

        List<Classification> classifications = null;

        if (!Util.isEmpty(accountGroup.getClassifications())) {
            classifications = accountGroup.getClassifications().stream()
                    .map(c -> c.getClassification()).distinct().collect(Collectors.toList());
        }

        for (Classification cls : Util.safeIterable(classifications)) {
            Map<String, Object> dto = new HashMap<>();
            dto.put("id", cls.getId());
            dto.put("displayableName", cls.getDisplayableName());
            dto.put("description", cls.getDescription(formBean.getLocale()));
            dto.put("origin", cls.getOrigin());

            classificationsMap.put(cls.getId(), dto);
        }
        
        initialize(context, accountGroup);
    }

    /**
     * Initialize the DTO using the given ManagedAttribute.
     * 
     * @param  context       The SailPointContext to use.
     * @param  accountGroup  The ManagedAttribute, which will be null for a
     *                       creation request.
     */
    void initialize(SailPointContext context, ManagedAttribute accountGroup) {
        containsExtendedAttributes = false;
        ObjectConfig config = ObjectConfig.getObjectConfig(ManagedAttribute.class);
        if (config != null) {
            List<ObjectAttribute> extendedAttributes = config.getObjectAttributes();
            if (extendedAttributes != null && !extendedAttributes.isEmpty()) {
                containsExtendedAttributes = true;
            }
        }
        
        if (null != accountGroup) {
            Map<String, Object> existingAttributes = accountGroup.getAttributes();
            if (!Util.isEmpty(existingAttributes)) {
                this.originalGroupFields = new HashMap<String, Object>();
                this.originalGroupFields.putAll(existingAttributes);
            }
            // The aggregator strips native identities out of the ManagedAttribute map
            // so we need to manually add it if it's in the template
            if (isNativeIdentityInGroupProvisioningTemplate(context, accountGroup.getType())) {
                if (null == this.originalGroupFields) {
                    this.originalGroupFields = new HashMap<String, Object>();
                }
                this.originalGroupFields.put(getNativeIdentityAttribute(accountGroup.getType()), getNativeIdentity());
            }
        }

        try {
            resetGroupAttributeFormBean(accountGroup);
        } catch (GeneralException e) {
            log.error("Failed to initialize group attribute form for account group " + accountGroup, e);
        }
        try {
            resetExtendedAttributeFormBean(accountGroup);
        } catch (GeneralException e) {
            log.error("Failed to initialize extended attribute form for account group " + accountGroup, e);
        }
        
        defaultLocale = Localizer.getDefaultLocaleName(Configuration.getSystemConfig());
        descriptionData = new DescriptionData((accountGroup == null) ? null : accountGroup.getDescriptions(), defaultLocale);
    }
    
    /**
     * Attach this DTO to the given AccountGroupEditBean.  This should be called
     * after the DTO has been retrieved from the session.
     */
    void attach(AccountGroupEditBean bean) {
        this.formBean = bean;

        try {
            FormRenderer groupAttributeFormBean = getGroupAttributeFormBean(); 
            if (groupAttributeFormBean != null)
                groupAttributeFormBean.setManualContext(getContext());
        } catch (GeneralException e) {
            log.error("Failed to apply a context to the group attribute form renderer ", e);
        }
        try {
            FormRenderer extendedAttributeFormBean = getExtendedAttributeFormBean();
            if (extendedAttributeFormBean != null)
                extendedAttributeFormBean.setManualContext(getContext());
        } catch (GeneralException e) {
            log.error("Failed to apply a context to the extended attribute form renderer ", e);
        }
    }

    
    ////////////////////////////////////////////////////////////////////////////
    //
    // METHODS
    //
    ////////////////////////////////////////////////////////////////////////////
    
    public String getId() {
        return id;
    }
    
    public String getDisplayValue() {
        return displayValue;
    }

    public void setDisplayValue(String displayValue) {
        //IIQTC-224: Removing unnecessary call to HTML escape characters.
        this.displayValue = displayValue;
    }

    public String getDescription() {
        return descriptionData.getDescriptionMap().get(defaultLocale);
    }

    public void setDescription(String description) {
        //IIQETN-5872 :- There is a special case where adding a new entitlement
        //is wiping out the description. The value that we should receive in the
        //description should be JSON format with some attributes related to
        //localization.
        try {
            //In order to guaranty that the description is a valid JSON string,
            //we need to convert a JSON text into a Java object, if there is
            //not exception we continue with the normal flow otherwise we catch
            //the exception and proceed to get the description in JSON string.
            @SuppressWarnings("unused")
            JSONArray descriptionEntries = new JSONArray(description);
            descriptionData = new DescriptionData(description);
        } catch (JSONException jex) {
            //at this point the description is coming as a normal string
            //we need to make sure to get the string in JSON format.
            descriptionData.setDescriptionsJson(getDescriptionsJson());
        }
    }
    
    public String getDescriptionsJson() {
        return descriptionData.getDescriptionsJson();
    }
    
    public void setDescriptionsJson(String descriptionsJson) {
        descriptionData.setDescriptionsJson(descriptionsJson);
    }

    public String getApplicationName() {
        return applicationName;
    }

    public void setApplicationName(String applicationName) {
        this.applicationName = applicationName;
    }

    public String getReferenceAttribute() {
        return referenceAttribute;
    }
    
    public void setReferenceAttribute(String referenceAttribute) {
        this.referenceAttribute = referenceAttribute;
    }

    public String getNativeIdentity() {
        return nativeIdentity;
    }

    public void setNativeIdentity(String nativeIdentity) {
        this.nativeIdentity = nativeIdentity;
    }

    public boolean isRequestable() {
        return requestable;
    }

    public void setRequestable(boolean requestable) {
        this.requestable = requestable;
    }

    public List<Map<String, Object>> getClassifications() {
        return new ArrayList<>(this.classificationsMap.values());
    }

    /**
     * If the app has a groupSchema with the objectType equal to that of the MA type, it is
     * a group
     * @return
     */
    public boolean isGroup() {
        boolean group = false;
        if(getApplication() != null) {
            group = getApplication().getGroupSchemaObjectTypes().contains(getType());
        }
        return group;
    }

    @Deprecated
    public void setGroup(boolean group) {
        this.group = group;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public boolean isAggregated() {
        return this.aggregated;
    }

    public void setAggregated(boolean aggregated) {
        this.aggregated = aggregated;
    }

    public Form getGroupAttributeMasterForm() {
        return groupAttributeMasterForm;
    }
    
    public void setGroupAttributeMasterForm(Form form) {
        groupAttributeMasterForm = form;
    }

    public Form getGroupAttributeExpandedForm() {
        return groupAttributeExpandedForm;
    }
    
    public void setGroupAttributeExpandedForm(Form form) {
        groupAttributeExpandedForm = form;
    }
    
    public void clearGroupAttributeFormBean() {
        groupAttributeFormBean = null;
    }
    
    public Form getExtendedAttributeMasterForm() {
        return extendedAttributeMasterForm;
    }
    
    public void setExtendedAttributeMasterForm(Form form) {
        extendedAttributeMasterForm = form;
    }

    public Form getExtendedAttributeExpandedForm() {
        return extendedAttributeExpandedForm;
    }
    
    public void setExtendedAttributeExpandedForm(Form form) {
        extendedAttributeExpandedForm = form;
    }
    
    public void clearExtendedAttributeFormBean() {
        extendedAttributeFormBean = null;
    }

    public List<String> getInheritingAccountGroups() {
        return inheritingAccountGroups;
    }

    public void setInheritingAccountGroups(List<String> inheritingAccountGroups) {
        this.inheritingAccountGroups = inheritingAccountGroups;
    }

    public List<String> getInheritedAccountGroups() {
        return inheritedAccountGroups;
    }

    public void setInheritedAccountGroups(List<String> inheritedAccountGroups) {
        this.inheritedAccountGroups = inheritedAccountGroups;
    }
    
    public String getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(String ownerId) {
        this.ownerId = ownerId;
    }

    public String getScopeId() {
        return scopeId;
    }

    public void setScopeId(String scopeId) {
        this.scopeId = scopeId;
    }
    
    public boolean isNew() {
        return this.id == null;
    }
    
    public boolean isProvisioningEnabled() {
        return isProvisioningEnabled;
    }

    public void setProvisioningEnabled(boolean isProvisioningEnabled) {
        this.isProvisioningEnabled = isProvisioningEnabled;
    }
    
    /**
     * @return A representation of this group's value that is used for logging purposes 
     */
    public String getDisplayableValue(SailPointContext context) {
        if (displayValue == null || displayValue.trim().length() == 0) {            
            if (type == ManagedAttribute.Type.Permission.name()) {
                return referenceAttribute;
            } else if (isGroup()) {
                String app = this.getApplicationName();
                if (app != null) {
                    try {
                        Application appObj = context.getObjectByName(Application.class, app);
                        Schema groupSchema = appObj.getSchema(getType());
                        if (groupSchema == null) {
                            return nativeIdentity;
                        } else {
                            String displayAttribute = groupSchema.getDisplayAttribute();
                            if (displayAttribute != null && groupSchema.getAttributeNamesHashSet() != null && groupSchema.getAttributeNamesHashSet().contains(displayAttribute)) {
                                return getGroupAttributeValue(displayAttribute);
                            } else {
                                return nativeIdentity;
                            }
                        }
                    } catch (GeneralException e) {
                        return nativeIdentity;
                    }
                } else {
                    return nativeIdentity;
                }
            } else {
                return nativeIdentity;
            }
        } else {
            return displayValue;
        }
    }
    
    public boolean isContainsExtendedAttributes() {
        return containsExtendedAttributes;
    }

    public boolean isContainsGroupAttributes() {
        return containsGroupAttributes;
    }

    void setContainsGroupAttributes(boolean containsGroupAttributes) {
        this.containsGroupAttributes = containsGroupAttributes;
    }
    
    /**
     * This method returns the ID of the persisted Account Group object that corresponds to this DTO.
     * If no such object exists the naitve identity will be returned instead
     */
    public String getIdOrName() {
        String idOrName;
        
        if (id == null || id.trim().length() == 0) {
            idOrName = nativeIdentity;
        } else {
            idOrName = id;
        }
        
        return idOrName;
    }
    
    public List<AttributeRequest> getGroupAttributeRequests() throws GeneralException {
        List<AttributeRequest> attributeRequests;
        FormRenderer formBean = getGroupAttributeFormBean();
        if (formBean != null) {
            Iterator<Field> fields = formBean.getForm().iterateFields();
            attributeRequests = getAttributeRequests(fields);
        } else {
            attributeRequests = Collections.emptyList();
        }

        return attributeRequests;
    }
    
    public List<AttributeRequest> getStandardAttributeRequests() {
        List<AttributeRequest> attributeRequests = new ArrayList<AttributeRequest>();
        attributeRequests.add(new AttributeRequest(ManagedAttribute.PROV_ATTRIBUTE, Operation.Set, referenceAttribute));
        attributeRequests.add(new AttributeRequest(ManagedAttribute.PROV_DISPLAY_NAME, Operation.Set, displayValue));
        attributeRequests.add(new AttributeRequest(ManagedAttribute.PROV_DESCRIPTIONS, Operation.Set, descriptionData.getDescriptionMap()));
        attributeRequests.add(new AttributeRequest(ManagedAttribute.PROV_OWNER, Operation.Set, ownerId));
        attributeRequests.add(new AttributeRequest(ManagedAttribute.PROV_SCOPE, Operation.Set, scopeId));
        attributeRequests.add(new AttributeRequest(ManagedAttribute.PROV_REQUESTABLE, Operation.Set, requestable));
        attributeRequests.add(new AttributeRequest(ManagedAttribute.PROV_MANAGED_ATTRIBUTE_TYPE, Operation.Set, type == null ? "null" : type.toString()));
        attributeRequests.add(new AttributeRequest(ManagedAttribute.PROV_CLASSIFICATIONS, Operation.Set, classificationsMap.keySet()));
        return attributeRequests;
    }
    
    public Collection<AttributeRequest> getExtendedAttributeRequests() throws GeneralException {
        Collection<AttributeRequest> attributeRequests;

        FormRenderer formBean = getExtendedAttributeFormBean();
        if (formBean != null) {
            Iterator<Field> fields = formBean.getForm().iterateFields();
            attributeRequests = getAttributeRequests(fields);            
        } else {
            attributeRequests = Collections.emptyList();
        }

        return attributeRequests;
    }
    
    public String getNativeIdentityAttribute(String schemaObjectType) {
        return getNativeIdentityAttribute(getContext(), schemaObjectType);
    }
    
    public String getNativeIdentityAttribute(SailPointContext context, String schemaObjectType) {
        if (nativeIdentityAttribute == null || isNew()) {
            try {
                Application app = context.getObjectByName(Application.class, applicationName);
                if (app == null) {
                    // Don't need to log an error in this case because we're handling it elsewhere
                    nativeIdentityAttribute = null;
                } else {
                    Schema groupSchema =  (schemaObjectType != null) ? app.getSchema(schemaObjectType) : app.getSchema(Application.SCHEMA_GROUP);
                    if (groupSchema == null) {
                        // Groups without a schema attribute should return null
                        nativeIdentityAttribute = null;
                    } else {
                        nativeIdentityAttribute = trim(groupSchema.getIdentityAttribute());
                    }
                }
            } catch (GeneralException e) {
                // We're already handling this case elsewhere so just eat it here.  Return a null nativeIdentityAttribute.
                nativeIdentityAttribute = null;
            }
        }
        
        return nativeIdentityAttribute;
    }
    
    
    public FormRenderer getGroupAttributeFormBean() throws GeneralException {
        if (null == this.groupAttributeFormBean) {
            GroupAttributeFormStore store = new GroupAttributeFormStore(this);
            Form expanded = store.retrieveExpandedForm();
            
            if (null == expanded) {
                Form master = store.retrieveMasterForm();
                
                if (null == master) {
                    if (originalGroupFields == null) {
                        originalGroupFields = new HashMap<String, Object>();
                    }
                    master = store.createMasterForm(originalGroupFields, getApplication(), isNew(), isGroup(), isProvisioningEnabled, getContext());
                }
                
                FormHandler handler = new FormHandler(getContext(), store);
                expanded = handler.initializeForm(master, false, getArgumentsForFormHandler());
            }
            
            this.groupAttributeFormBean = new FormRenderer(expanded, this.formBean, getLocale(), getContext(), getUserTimeZone());
        }
        
        return this.groupAttributeFormBean;
    }

    public FormRenderer getExtendedAttributeFormBean() throws GeneralException {
        if (null == this.extendedAttributeFormBean) {
            ExtendedAttributeFormStore store = new ExtendedAttributeFormStore(this);
            Form expanded = store.retrieveExpandedForm();
            
            if (null == expanded) {
                Form master = store.retrieveMasterForm();
                
                if (null == master) {
                    master = store.createMasterForm(originalGroupFields, isNew(), getContext(), getLocale());
                }
                
                FormHandler handler = new FormHandler(getContext(), store);
                expanded = handler.initializeForm(master, false, getArgumentsForFormHandler());
            }

            this.extendedAttributeFormBean = new FormRenderer(expanded, this.formBean, getLocale(), getContext(), getUserTimeZone());
        }
        
        return this.extendedAttributeFormBean;
    }
    
    /**
     * Utility method for returning this attribute's type as it should be displayed in the UI
     * @return
     */
    public String getDisplayType() {
        String rawType = getType();
        String typeKey;
        
       if (rawType == ManagedAttribute.Type.Entitlement.name()) {
            typeKey = MessageKeys.ENTITLEMENT;
        } else if (rawType == ManagedAttribute.Type.Permission.name()) {
            typeKey = MessageKeys.PERMISSION;
        } else if (Util.isNotNullOrEmpty(rawType)) {
            typeKey = rawType;
        } else {
            // There should never be an else
            typeKey = MessageKeys.NONE;
        }

        return new Message(typeKey).getLocalizedMessage(getLocale(), getUserTimeZone());
    }
    
    /**
     * Reset the form bean to its original state
     * @param group Persisted Account Group
     */
    void resetGroupAttributeFormBean(ManagedAttribute group) throws GeneralException {
        GroupAttributeFormStore store = new GroupAttributeFormStore(this);
        store.clearMasterForm();
        store.clearExpandedForm();
        FormHandler handler = new FormHandler(getContext(), store);
        if (originalGroupFields == null) {
            originalGroupFields = new HashMap<String, Object>();
        }
        Form master = store.createMasterForm(originalGroupFields, getApplication(), isNew(), isGroup(), isProvisioningEnabled(), getContext());
        getGroupAttributeFormBean();
        handler.initializeForm(master, false, getArgumentsForFormHandler());
    }

    /**
     * Reset the form bean to its original state
     * @param group Persisted Account Group
     */
    void resetExtendedAttributeFormBean(ManagedAttribute group) throws GeneralException{
        ExtendedAttributeFormStore store = new ExtendedAttributeFormStore(this);
        store.clearMasterForm();
        store.clearExpandedForm();
        FormHandler handler = new FormHandler(getContext(), store);
        Form master = store.createMasterForm(originalGroupFields, isNew(), getContext(), getLocale());
        getExtendedAttributeFormBean();
        handler.initializeForm(master, false, getArgumentsForFormHandler());
    }

        
    /**
     * @return Collection of Attribute Request objects that will make changes to reflect the current state of this DTO
     */
    private List<AttributeRequest> getAttributeRequests(Iterator<Field> fields) {
        List<AttributeRequest> attributeRequests = new ArrayList<AttributeRequest>();
        if (fields != null) {
            while (fields.hasNext()) {
                Field field = fields.next();
                String attributeName = field.getName();
                Object attributeValue = field.getValue();
                // Transform the "id" back into an ID for the purposes of building the Provisioning Plan.
                // See bug 16481.
                if (GroupAttributeFormStore.ID_ALIAS.equals(attributeName)) {
                    attributeName = "id";
                }
                attributeRequests.add(new AttributeRequest(attributeName, Operation.Set, attributeValue));
            }
        }

        return attributeRequests;
    }
    
    private List<String> initInheritingAccountGroups(ManagedAttribute accountGroup) {
        QueryOptions qo = new QueryOptions(Filter.containsAll("inheritance", Arrays.asList(new ManagedAttribute[]{accountGroup})));
        List<ManagedAttribute> inheritingGroupObjs;
        try {
            inheritingGroupObjs = getContext().getObjects(ManagedAttribute.class, qo);
        } catch (GeneralException e) {
            log.error("The AccountGroupDTO was unable to determine the groups that inherit it.", e);
            inheritingGroupObjs = null;
        }

        List<String> inheritingGroups = null;

        if (inheritingGroupObjs != null && !inheritingGroupObjs.isEmpty()) {
            inheritingGroups = new ArrayList<String>();
            for (ManagedAttribute inheritingGroupObj : inheritingGroupObjs) {
                inheritingGroups.add(inheritingGroupObj.getId());
            }
        }
        
        return inheritingGroups;
    }
    
    private List<String> initInheritedAccountGroups(ManagedAttribute accountGroup) {
        List<String> inheritedGroups = null;
        List<ManagedAttribute> inheritedGroupObjs = accountGroup.getInheritance();
        if (inheritedGroupObjs != null && !inheritedGroupObjs.isEmpty()) {
            inheritedGroups = new ArrayList<String>();
            for (ManagedAttribute inheritedGroupObj : inheritedGroupObjs) {
                inheritedGroups.add(inheritedGroupObj.getId());
            }
        }
        
        return inheritedGroups;
    }
        
    void styleField(Field field, final boolean hasCategory) {
        field.setAttribute("labelWidth", 175);
        field.setAttribute("style", "width:450px");
        String labelClass;
        if (hasCategory) {
            labelClass = "titleColumn categorizedField breakword";
        } else {
            labelClass = "titleColumn breakword";
        }
        field.setAttribute("labelCls", labelClass);
    }
        
    String getGroupAttributeValue(String attributeName) throws GeneralException {
        String groupAttributeValue = null;
        FormRenderer formBean = getGroupAttributeFormBean();
        if (attributeName != null && formBean != null) {
            Iterator<Field> fields = formBean.getForm().iterateFields();
            while (fields != null && fields.hasNext()) {
                Field field = fields.next();
                if (attributeName.equals(field.getName())) {
                    Object fieldValue = field.getValue();
                    if (fieldValue != null) {
                        groupAttributeValue = fieldValue.toString();
                    }
                }
            }
        }
        
        return groupAttributeValue;
    }
    
    private Application getApplication() {
        Application app = null;
        if (!Util.isNullOrEmpty(applicationName)) {
            try {
                app = getContext().getObjectByName(Application.class, applicationName);
            } catch (GeneralException e) {
                log.error("The entitlement editor failed to find an application named " + applicationName);
            }
        }
        
        return app;
    }
    
    /**
     * @return Map of arguments to pass into the initialize and submit methods of the form handler
     */
    public Map<String, Object> getArgumentsForFormHandler() {
        Map<String, Object> arguments = new HashMap<String, Object>();
        arguments.put("group", this);
        return arguments;
    }
    
    private boolean isNativeIdentityInGroupProvisioningTemplate(SailPointContext context, String schemaObjectType) {
        boolean found = false;
        if (!Util.isNothing(applicationName)) {
            try {
                Application app = context.getObjectByName(Application.class, applicationName);
                if (app != null) {
                    String nativeIdentityAttribute = getNativeIdentityAttribute(schemaObjectType);
                    if (isNew()) {
                        Template createGroupTemplate = app.getOldTemplate(Usage.Create,  schemaObjectType);
                        if (createGroupTemplate != null) {
                            List<Field> createGroupFields = createGroupTemplate.getFields(context);
                            if (isAttributeInFields(nativeIdentityAttribute, createGroupFields)) {
                                found = true;
                            }
                        }
                    }

                    Template editGroupTemplate = app.getOldTemplate(Usage.Update, schemaObjectType);
                    if (editGroupTemplate != null) {
                        List<Field> editGroupFields = editGroupTemplate.getFields(context);
                        if (isAttributeInFields(nativeIdentityAttribute, editGroupFields)) {
                            found = true;
                        }
                    }                    
                }
            } catch (GeneralException e) {
                log.error("The group provisioning templates were inaccessible because the group's application is unavailable.", e);
            }
        }
        
        return found;
    }
    
    private boolean isAttributeInFields(String attribute, List<Field> fields) {
        boolean found = false;
        if (attribute != null && !Util.isEmpty(fields)) {
            for (Field field : fields) {
                if (attribute.equals(field.getName())) {
                    found = true;
                }
            }
        }
        
        return found;
    }
    
    public void addCurrentClassification(SailPointContext context, String classificationIdToAd) throws GeneralException {
        if (classificationIdToAd != null) {
            Classification cls = context.getObjectById(Classification.class, classificationIdToAd);
            if (cls != null) {
                if (!this.classificationsMap.containsKey(cls.getId())) {
                    Map<String, Object> dto = new HashMap<>();
                    dto.put("id", cls.getId());
                    dto.put("displayableName", cls.getDisplayableName());
                    dto.put("description", cls.getDescription());
                    dto.put("origin", cls.getOrigin());
                    this.classificationsMap.put(cls.getId(), dto);
                }
            }
        }
    }

    public void removeCurrentClassifications(List<String> classificationIdsToRemove) {

        for (String idToRemove : Util.safeIterable(classificationIdsToRemove)) {
            if (this.classificationsMap.containsKey(idToRemove)) {
                this.classificationsMap.remove(idToRemove);
            }
        }
    }

}
