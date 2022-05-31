package sailpoint.web.application;

import java.util.ArrayList;
import java.util.List;

import javax.faces.model.SelectItem;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import sailpoint.connector.Connector;
import sailpoint.object.Application;
import sailpoint.object.AttributeDefinition;
import sailpoint.object.AttributeDefinition.UserInterfaceInputType;
import sailpoint.object.Attributes;
import sailpoint.object.Rule;
import sailpoint.object.Schema;
import sailpoint.service.listfilter.ListFilterValue;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.web.ApplicationObjectBean;
import sailpoint.web.BaseDTO;
import sailpoint.web.SchemaAttributeDTO;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.util.WebUtil;

public class SchemaDTO extends BaseDTO {

    private static Log log = LogFactory.getLog(SchemaDTO.class);

    private String name;
    private String objectType;
    private String aggregationType;
    private String nativeObjectType;
    private String displayAttribute;
    private String identityAttribute;
    private String groupAttribute;
    private String hierarchyAttribute;
    private String instanceAttribute;
    private String descriptionAttribute;
    private boolean includePermissions;
    private boolean indexPermissions;
    private boolean childHierarchy;
    private UserInterfaceInputType remediationModifiable;
    private List<SchemaAttributeDTO> attributes;
    private boolean provisioningEnabled;
    private boolean aggregationEnabled;
    private Attributes<String, Object> config;
    private List<SelectItem> aggregationTypes;
    //DTO to aid in massaging attributes
    private AttributeDTO _attributeDTO;
    //Reference to the parent ApplicationObjectBean
    private ApplicationObjectBean _appObjectBean;

    private String _creationRule;
    private String _customizationRule;
    private String _correlationRule;
    private String _refreshRule;

    List<SelectItem> _correlationRules;
    List<SelectItem> _creationRules;
    List<SelectItem> _customizationRules;
    List<SelectItem> _refreshRules;
    private boolean supportsDisable;
    private String disableFilterValue;
    private boolean supportsLock;
    private String lockFilterValue;
    private boolean supportsServiceAccount;
    private String serviceAccountFilterValue;
    private boolean supportsRpaAccount;
    private String rpaAccountFilterValue;
    
    public SchemaDTO(Schema schema, ApplicationObjectBean bean) {
        name = schema.getName();
        // Object type is rendered directly in the HTML so need to make double sure its safe
        objectType = WebUtil.safeHTML(schema.getObjectType());
        aggregationType = schema.getAggregationType();
        nativeObjectType = schema.getNativeObjectType();
        identityAttribute = schema.getIdentityAttribute();
        displayAttribute = schema.getDisplayAttribute();
        groupAttribute = schema.getGroupAttribute();
        hierarchyAttribute = schema.getHierarchyAttribute();
        instanceAttribute = schema.getInstanceAttribute();
        descriptionAttribute = schema.getDescriptionAttribute();
        includePermissions = schema.getIncludePermissions();
        indexPermissions = schema.isIndexPermissions();
        childHierarchy = schema.isChildHierarchy();
        remediationModifiable = schema.getPermissionsRemediationModificationType();
        attributes = new ArrayList<SchemaAttributeDTO>();
        List<AttributeDefinition> attributeDefinitions = schema.getAttributes();
        if (attributeDefinitions != null) {
            for (AttributeDefinition attributeDef : attributeDefinitions) {
                attributes.add(new SchemaAttributeDTO(attributeDef, getObjectType()));
            }
        }
        config = schema.getConfig();
        _appObjectBean = bean;
        _attributeDTO = buildAttributeDTO();

        _creationRule = schema.getCreationRule() != null ? schema.getCreationRule().getName() : null;
        _customizationRule = schema.getCustomizationRule() != null ? schema.getCustomizationRule().getName() : null;
        _correlationRule = schema.getCorrelationRule() != null ? schema.getCorrelationRule().getName() : null;
        _refreshRule = schema.getRefreshRule() != null ? schema.getRefreshRule().getName() : null;

        setSupportsDisable(bean.getSupportsEnable() && Schema.TYPE_ACCOUNT.equalsIgnoreCase(schema.getObjectType()));
        setSupportsLock(bean.getSupportsUnlock() && Schema.TYPE_ACCOUNT.equalsIgnoreCase(schema.getObjectType()));
        setSupportsServiceAccount(Schema.TYPE_ACCOUNT.equalsIgnoreCase(schema.getObjectType()));
        setSupportsRpaAccount(Schema.TYPE_ACCOUNT.equalsIgnoreCase(schema.getObjectType()));
        try {
            List<ListFilterValue> disableFilter = _appObjectBean.getDisableAccountFilter();
            String disableFilterValueDisplay = createListFilterDisplayValue(disableFilter);
            setDisableFilterValue(disableFilter == null ? null : disableFilterValueDisplay);
            List<ListFilterValue> lockFilter = _appObjectBean.getLockAccountFilter();
            String lockFilterValueDisplay = createListFilterDisplayValue(lockFilter);
            setLockFilterValue(lockFilter == null ? null : lockFilterValueDisplay);
            List<ListFilterValue> serviceAccountFilter = _appObjectBean.getServiceAccountFilter();
            String serviceAccountFilterValueDisplay = createListFilterDisplayValue(serviceAccountFilter);
            setServiceAccountFilterValue(serviceAccountFilter == null ? null : serviceAccountFilterValueDisplay);
            List<ListFilterValue> rpaAccountFilter = _appObjectBean.getRpaAccountFilter();
            String rpaAccountFilterValueDisplay = createListFilterDisplayValue(rpaAccountFilter);
            setRpaAccountFilterValue(rpaAccountFilter == null ? null : rpaAccountFilterValueDisplay);
        } catch (GeneralException e) {
            log.warn("Unable to access the account disable/lock filters");
        }
        setProvisioningEnabled(schema.supportsFeature(Application.Feature.PROVISIONING) ? true : false);
        setAggregationEnabled(!schema.supportsFeature(Application.Feature.NO_AGGREGATION));
    }

    private String createListFilterDisplayValue(List<ListFilterValue> filterList) {
        String tooComplex = getMessage(MessageKeys.FILTER_TOO_COMPLEX_TO_DISPLAY);
        if (Util.nullSafeSize(filterList) == 0) return null;
        if (Util.nullSafeSize(filterList) == 1) {
            return filterList.get(0).getDisplayString();
        }
        return tooComplex;
    }

    public String getObjectType() {
        return objectType;
    }
    
    public void setObjectType(String objectType) {
        this.objectType = objectType;
    }
    
    public String getAggregationType() {
        return aggregationType;
    }
    
    public void setAggregationType(String type) {
        aggregationType = type;
    }

    /**
     * Pseudo property used to control the rendering of the aggregation type.
     */
    public boolean getNeedsAggregationType() {
        //If AccountSchema, test for Agg Type
        return !Schema.isAggregationType(objectType) && isAggregationEnabled();
    }

    public String getNativeObjectType() {
        return nativeObjectType;
    }

    public void setNativeObjectType(String nativeObjectType) {
        this.nativeObjectType = nativeObjectType;
    }

    public String getIdentityAttribute() {
        return identityAttribute;
    }

    public void setIdentityAttribute(String identityAttribute) {
        this.identityAttribute = identityAttribute;
    }

    public String getDisplayAttribute() {
        return displayAttribute;
    }

    public void setDisplayAttribute(String displayAttribute) {
        this.displayAttribute = displayAttribute;
    }

    public String getInstanceAttribute() {
        return instanceAttribute;
    }

    public void setInstanceAttribute(String instanceAttribute) {
        this.instanceAttribute = instanceAttribute;
    }

    public String getDescriptionAttribute() {
        return descriptionAttribute;
    }

    public void setDescriptionAttribute(String descriptionAttribute) {
        this.descriptionAttribute = descriptionAttribute;
    }

    public String getGroupAttribute() {
        return groupAttribute;
    }

    public void setGroupAttribute(String groupAttribute) {
        this.groupAttribute = groupAttribute;
    }

    public String getHierarchyAttribute() {
        return hierarchyAttribute;
    }

    public void setHierarchyAttribute(String hierarchyAttribute) {
        this.hierarchyAttribute = hierarchyAttribute;
    }

    public boolean isIncludePermissions() {
        return includePermissions;
    }

    public void setIncludePermissions(boolean includePermissions) {
        this.includePermissions = includePermissions;
    }

    public boolean isIndexPermissions() {
        return indexPermissions;
    }

    public void setIndexPermissions(boolean b) {
        this.indexPermissions = b;
    }

    public boolean isChildHierarchy() {
        return childHierarchy;
    }

    public void setChildHierarchy(boolean b) {
        this.childHierarchy = b;
    }

    public UserInterfaceInputType getRemediationModifiable() {
        return remediationModifiable;
    }

    public void setRemediationModifiable(
            UserInterfaceInputType remediationModifiable) {
        this.remediationModifiable = remediationModifiable;
    }

    public List<SchemaAttributeDTO> getAttributes() {
        return attributes;
    }

    public void setAttributes(List<SchemaAttributeDTO> attributes) {
        this.attributes = attributes;
    }

    public SchemaAttributeDTO getAttribute(String name ) {
        List<SchemaAttributeDTO> attrs = getAttributes();
        for ( SchemaAttributeDTO dto : attrs ) {
            String attrName = dto.getName();
            if ( name != null ) {
                if ( attrName.compareTo(name) == 0 ) {
                    return dto;
                }
            }
        }
        return null;
    }

    /**
     * Return the SchemaAttributeDTO with the given UID
     * @param uid - UID of the schemaAttributeDTO
     * @return SchemaAttributeDTO with the given UID
     */
    public SchemaAttributeDTO getAttributeByUid(String uid) {
        List<SchemaAttributeDTO> attrs = getAttributes();
        for ( SchemaAttributeDTO dto : attrs ) {
            String schemaUid = dto.getUid();
            if ( uid != null ) {
                if ( schemaUid.compareTo(uid) == 0 ) {
                    return dto;
                }
            }
        }
        return null;
    }

    public String getName() {
        return name;
    }
    
    public boolean isGroup() {
        return (getObjectType() == null || Connector.TYPE_ACCOUNT.equals(getObjectType())) ? false : true;
    }

    public boolean isProvisioningEnabled() {
        return this.provisioningEnabled;
    }

    public void setProvisioningEnabled(boolean b) {
        provisioningEnabled = b;
    }

    public boolean isAggregationEnabled() { return this.aggregationEnabled; }

    public void setAggregationEnabled(boolean b) { aggregationEnabled = b; }

    public Attributes<String, Object> getConfig() {
        if (config == null) {
            config = new Attributes<String, Object>();
        }
        return config;
    }

    public String getCreationRule() { return _creationRule; }

    public void setCreationRule(String s) { _creationRule = s; }

    public String getCustomizationRule() { return _customizationRule; }

    public void setCustomizationRule(String s) { _customizationRule = s; }

    public String getCorrelationRule() { return _correlationRule; }

    public void setCorrelationRule(String s) { _correlationRule = s; }

    public String getRefreshRule() { return _refreshRule; }

    public void setRefreshRule(String s) { _refreshRule = s; }

    public ApplicationObjectBean getAppObjectBean() { return _appObjectBean; }

    /**
     * Generalize AttributeDTO.
     */
    public AttributeDTO getAttributeDTO() {
        return _attributeDTO;
    }

    public void setAttributeDTOs(AttributeDTO dto) {
        _attributeDTO = dto;
    }

    public AttributeDTO buildAttributeDTO() {
        return AttributeDTOFactory.getAttributeDTO(this);
    }
    
    /**
     * The list of possible aggregation types.
     * This is selectively shown and only when it is a non-account
     * schema, so put group first and let that be the default.
     * Not including account because we've never had a connector that
     * supported more than one type of account schema, and that isn't
     * supported by the aggregator anyway.  Lots of work to do to make
     * that happen.
     *
     * Think about whether these really need to be localized?
     */
    public List<SelectItem> getAggregationTypes() {
        if (aggregationTypes == null) {
            aggregationTypes = new ArrayList<SelectItem>();
            aggregationTypes.add(new SelectItem(Schema.TYPE_GROUP, getMessage(MessageKeys.AGGREGATION_TYPE_GROUP)));
            aggregationTypes.add(new SelectItem(Schema.TYPE_ALERT, getMessage(MessageKeys.AGGREGATION_TYPE_ALERT)));
            aggregationTypes.add(new SelectItem(Schema.TYPE_UNSTRUCTURED, getMessage(MessageKeys.AGGREGATION_TYPE_UNSTRUCTURED)));
        }
        return aggregationTypes;
    }

    public List<SelectItem> getCustomizationRules() throws GeneralException {

        if (_customizationRules == null) {
            Rule selected = Util.isNotNullOrEmpty(getCustomizationRule()) ? getContext().getObjectByName(Rule.class, getCustomizationRule()) : null;
            _customizationRules = WebUtil.getRulesByType(getContext(),
                    Rule.Type.valueOf(getCustomizationRuleType()),
                    true, false,
                    ((null != selected) ? selected.getName() : null));

        }
        return _customizationRules;
    }

    public boolean isSupportsCustomizationRule() {
        return isAggregationEnabled();
    }

    public String getCustomizationRuleType() {
        return Rule.Type.ResourceObjectCustomization.name();
    }

    public List<SelectItem> getRefreshRules() throws GeneralException {
        if (_refreshRules == null) {
            Rule selected = Util.isNotNullOrEmpty(getRefreshRule()) ? getContext().getObjectByName(Rule.class, getRefreshRule()) : null;
            _refreshRules = WebUtil.getRulesByType(getContext(),
                    Rule.Type.valueOf(getRefreshRuleType()),
                    true, false,
                    ((null != selected) ? selected.getName() : null));

        }

        return _refreshRules;
    }

    /**
     * True if schema type supports refresh rule
     * @return
     * @ignore
     * Group refresh rules are defined on the task. Would be nice to fix this.
     * Account refresh rules are only supported on an app level
     */
    public boolean isSupportsRefreshRule() {

        switch (getObjectType()) {
            case Schema.TYPE_UNSTRUCTURED:
                return true;
            default:
                return false;
        }
    }

    public String getRefreshRuleType() {
        switch (getObjectType()) {
            case Schema.TYPE_UNSTRUCTURED:
                return Rule.Type.TargetRefresh.name();
            default:
                return null;
        }
    }

    public void setSupportsDisable(boolean b) {
        supportsDisable = b;
    }

    public boolean isSupportsDisable() {
        return supportsDisable;
    }

    public String getDisableFilterValue() {
        return disableFilterValue;
    }

    public void setDisableFilterValue(String disableFilterValue) {
        this.disableFilterValue = disableFilterValue;
    }


    public void setSupportsLock(boolean b) {
        supportsLock = b;
    }

    public boolean isSupportsLock() {
        return supportsLock;
    }

    public String getLockFilterValue() {
        return lockFilterValue;
    }

    public void setLockFilterValue(String lockFilterValue) {
        this.lockFilterValue = lockFilterValue;
    }

    public void setSupportsServiceAccount(boolean b) {
        supportsServiceAccount = b;
    }

    public boolean isSupportsServiceAccount() {
        return supportsServiceAccount;
    }

    public String getServiceAccountFilterValue() {
        return serviceAccountFilterValue;
    }

    public void setServiceAccountFilterValue(String serviceAccountFilterValue) {
        this.serviceAccountFilterValue = serviceAccountFilterValue;
    }

    public void setSupportsRpaAccount(boolean b) {
        supportsRpaAccount = b;
    }

    public boolean isSupportsRpaAccount() {
        return supportsRpaAccount;
    }

    public String getRpaAccountFilterValue() {
        return rpaAccountFilterValue;
    }

    public void setRpaAccountFilterValue(String rpaAccountFilterValue) {
        this.rpaAccountFilterValue = rpaAccountFilterValue;
    }
    /**
     * Return the names of all correlation rules, suitable for use
     * in a selectOneMenu.
     */
    public List<SelectItem> getCreationRules() throws GeneralException {

        if (_creationRules == null) {
            Rule selected = Util.isNotNullOrEmpty(getCreationRule()) ? getContext().getObjectByName(Rule.class, getCreationRule()) : null;
            _creationRules = WebUtil.getRulesByType(getContext(),
                    Rule.Type.valueOf(getCreationRuleType()),
                    true, false,
                    ((null != selected) ? selected.getName() : null));

        }
        return _creationRules;
    }

    /**
     * True if the schema type supports creation Rule
     * @return
     * @ignore
     * Groups do not currently support creation rules.
     * Account creation rule is only on the app/task, not yet supported on schema level
     */
    public boolean isSupportsCreationRule() {
        switch (getObjectType()) {
            case Schema.TYPE_UNSTRUCTURED:
                return true;
            case Schema.TYPE_ALERT:
                return true;
            default:
                return false;
        }
    }

    /**
     * Return the #sailpoint.object.Rule.Type name for the given schema type creation rule
     * @return
     */
    public String getCreationRuleType() {
        switch (getObjectType()) {
            case Schema.TYPE_UNSTRUCTURED:
                return Rule.Type.TargetCreation.name();
            case Schema.TYPE_ALERT:
                return Rule.Type.AlertCreation.name();
            default:
                return null;
        }
    }

    /**
     * Return the names of all correlation rules, suitable for use
     * in a selectOneMenu.
     */
    public List<SelectItem> getCorrelationRules() throws GeneralException {

        if (_correlationRules == null) {
            Rule selected = Util.isNotNullOrEmpty(getCorrelationRule()) ? getContext().getObjectByName(Rule.class, getCorrelationRule()) : null;
            _correlationRules = WebUtil.getRulesByType(getContext(),
                    Rule.Type.valueOf(getCorrelationRuleType()),
                    true, false,
                    ((null != selected) ? selected.getName() : null));

        }
        return _correlationRules;
    }

    /**
     * True if the schema type supports CorrelationRule
     * @return
     * @ignore
     * Account supports correlation, but not on the schema level.
     */
    public boolean isSupportsCorrelationRule() {
        switch(getObjectType()) {
            case Schema.TYPE_ALERT:
                return true;
            case Schema.TYPE_UNSTRUCTURED:
                return true;
            default:
                return false;
        }
    }

    public String getCorrelationRuleType() {
        switch(getObjectType()) {
            case Schema.TYPE_ALERT:
                return Rule.Type.AlertCorrelation.name();
            case Schema.TYPE_UNSTRUCTURED:
                return Rule.Type.TargetCorrelation.name();
            default:
                return null;
        }
    }

    public boolean isShowSchemaRules() {
        return isSupportsCorrelationRule() ||
                isSupportsCreationRule() ||
                isSupportsCustomizationRule() ||
                isSupportsRefreshRule();
    }

    /**
     * Sets a named schema configuration attribute.
     * Existing values will be overwritten
     */
    public void addConfig(String name, Object value) {
        if ( config == null ) {
            config = new Attributes<String, Object>();
        }
        config.put(name,value);
    }

    /**
     * IIQSAW-2138
     * Bean helper to calculate the namespace and address a Websphere bug where
     * evaluating an EL expression that will
     * be used later within a value attribute which also has brackets would cause a NPE.
     *
     * Making the bean do the evaluation gets around the NPE
     * See {@link WebUtil#concat(String, String)} for examples of this usage 
     * @return Empty String if the object type is "account", otherwise the object type
     */
    public String getNameSpace() {
        return Util.nullSafeEq(this.getObjectType(), Schema.TYPE_ACCOUNT) ?
               "" :
               this.getObjectType();
    }

    /**
     * Bean helper method to calculate the SchemaType.
     * Same workaround as {@link #getNameSpace()}
     *
     * @return Empty String if the object type is "account", otherwise the object type with a "." appended
     */
    public String getSchemaType() {
        return Util.nullSafeEq(this.getObjectType(), Schema.TYPE_ACCOUNT) ?
               "" :
               this.getObjectType() + ".";
    }

    public void populate(Schema schema) {
        //Allow AttributeDTO to massage any data that might be needed
        if (_attributeDTO != null) {
            _attributeDTO.saveAttributeData();
        }
        schema.setName(name);
        schema.setObjectType(objectType);
        schema.setAggregationType(aggregationType);
        schema.setNativeObjectType(nativeObjectType);
        schema.setIdentityAttribute(identityAttribute);
        schema.setDisplayAttribute(displayAttribute);
        schema.setGroupAttribute(groupAttribute);
        schema.setHierarchyAttribute(hierarchyAttribute);
        schema.setInstanceAttribute(instanceAttribute);
        schema.setDescriptionAttribute(descriptionAttribute);
        schema.setIncludePermissions(includePermissions);
        schema.setIndexPermissions(indexPermissions);
        schema.setChildHierarchy(childHierarchy);
        schema.setPermissionsRemediationModificationType(remediationModifiable);

        if (attributes == null || attributes.isEmpty()) {
            schema.setAttributes(null);
        } else {
            List<AttributeDefinition> updatedAttributeList = new ArrayList<AttributeDefinition>();
            
            for (SchemaAttributeDTO attributeDTO : attributes) {
                AttributeDefinition updatedDef = schema.getAttributeDefinition(attributeDTO.getName());
                if (updatedDef == null) {
                    updatedDef = new AttributeDefinition();
                }                
                attributeDTO.update(updatedDef);
                updatedAttributeList.add(updatedDef);
            }
            
            schema.setAttributes(updatedAttributeList);
        }


        if (isProvisioningEnabled()) {
            schema.addFeature(Application.Feature.PROVISIONING);
        } else {
            schema.removeFeature(Application.Feature.PROVISIONING);
        }

        schema.setConfig(config);

        try {
            if (Util.isNullOrEmpty(_correlationRule)) {
                schema.setCorrelationRule(null);
            } else {
                Rule r = getContext().getObjectByName(Rule.class, _correlationRule);
                if (r != null) {
                    schema.setCorrelationRule(r);
                }
            }
        } catch (GeneralException ge) {
            if (log.isWarnEnabled()) {
                log.warn("Error getting CorrelationRule for schema");
            }
        }

        try {
            if (Util.isNullOrEmpty(_creationRule)) {
                schema.setCreationRule(null);
            } else {
                Rule r = getContext().getObjectByName(Rule.class, _creationRule);
                if (r != null) {
                    schema.setCreationRule(r);
                }
            }
        } catch (GeneralException ge) {
            if (log.isWarnEnabled()) {
                log.warn("Error getting CreationRule for schema");
            }
        }

        try {
            if (Util.isNullOrEmpty(_customizationRule)) {
                schema.setCustomizationRule(null);
            } else {
                Rule r = getContext().getObjectByName(Rule.class, _customizationRule);
                if (r != null) {
                    schema.setCustomizationRule(r);
                }
            }
        } catch (GeneralException ge) {
            if (log.isWarnEnabled()) {
                log.warn("Error getting CustomizationRule for schema");
            }
        }

        try {
            if (Util.isNullOrEmpty(_refreshRule)) {
                schema.setRefreshRule(null);
            } else {
                Rule r = getContext().getObjectByName(Rule.class, _refreshRule);
                if (r != null) {
                    schema.setRefreshRule(r);
                }
            }
        } catch (GeneralException ge) {
            if (log.isWarnEnabled()) {
                log.warn("Error getting RefreshRule for schema");
            }
        }

    }
}
