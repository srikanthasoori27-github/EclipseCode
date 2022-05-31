/*
 * (c) Copyright 2019 SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.service.identity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import sailpoint.api.Explanator;
import sailpoint.api.ManagedAttributer;
import sailpoint.api.SailPointContext;
import sailpoint.object.Application;
import sailpoint.object.ColumnConfig;
import sailpoint.object.IdentityEntitlement;
import sailpoint.object.ManagedAttribute;
import sailpoint.service.BaseDTO;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.web.UserContext;

/**
 * DTO containing details of a specific entitlement outside the context of an identity
 */
public class BaseEntitlementDTO extends BaseDTO {

    /**
     * Name of the Application
     */
    private String application;

    /**
     * Id of the Application
     */
    private String applicationId;

    /**
     * Name of attribute or permission target
     */
    private String attribute;

    /**
     * Alias for attribute/permission for use on older uis
     */
    private String name;

    /**
     * Value of attribute or permission right.
     */
    private String value;

    /**
     * Displayable name of attribute or permission target.
     */
    private String displayValue;

    /**
     * Localized description for attribute or permission
     */
    private String description;

    /**
     * Whether the entitlement is a group or not
     */
    private boolean group;

    /**
     * True if this is a permission.
     */
    private boolean permission;

    /**
     * Permission annotation.
     */
    private String annotation;

    /**
     * The ManageAttribute Type (Entitlement/Permission)
     */
    private ManagedAttribute.Type type;

    /**
     * The localized message of the type
     */
    private String localizedType;

    /**
     * Whether the entitlement belongs to an application or to iiq
     */
    private boolean iiqEntitlement;

    /**
     * The ID of the managed attribute, if one exists
     */
    private String managedAttributeId;

    /**
     * list of classification names for this entitlement
     */
    private List<String> classificationNames;


    public BaseEntitlementDTO() {

    }

    public BaseEntitlementDTO(BaseEntitlementDTO dto) {
        this.application = dto.application;
        this.applicationId = dto.applicationId;
        this.permission = dto.permission;
        this.annotation = dto.annotation;
        this.attribute = dto.attribute;
        this.name = dto.name;
        this.value = dto.value;
        this.displayValue = dto.displayValue;
        this.description = dto.description;
        this.group = dto.group;
        this.type = dto.getType();
        this.localizedType = dto.getLocalizedType();
        this.managedAttributeId = dto.getManagedAttributeId();
        this.iiqEntitlement = dto.iiqEntitlement;
        if (dto.classificationNames != null) {
            this.classificationNames = new ArrayList<>(dto.classificationNames);
        }
    }

    public BaseEntitlementDTO(IdentityEntitlement identityEntitlement, UserContext userContext, SailPointContext context) throws GeneralException {
        Application app = identityEntitlement.getApplication();
        if(app != null) {
            this.applicationId = app.getId();
        }
        this.name = identityEntitlement.getName();
        this.application = identityEntitlement.getAppName();
        this.permission = Util.nullSafeEq(identityEntitlement.getType(), ManagedAttribute.Type.Permission);
        this.annotation = identityEntitlement.getAnnotation();
        this.attribute = identityEntitlement.getName();
        this.value = identityEntitlement.getStringValue();
        this.displayValue = Explanator.getDisplayValue(this.applicationId, this.name, this.value);
        this.description = identityEntitlement.getDescription();
        this.iiqEntitlement = this.application == null;
        this.type = identityEntitlement.getType();
        if (this.type != null) {
            this.localizedType = type.getLocalizedMessage(userContext.getLocale(), userContext.getUserTimeZone());
            if(localizedType == null)
                localizedType = type.toString();
        }
        if(ManagedAttribute.Type.Entitlement.equals(this.type)) {
            ManagedAttribute managedAttribute = ManagedAttributer.get(context, this.applicationId, this.attribute, this.value);
            this.managedAttributeId = (managedAttribute == null) ? null : managedAttribute.getId();
        }
    }

    public BaseEntitlementDTO(Map<String, Object> entitlementMap, List<ColumnConfig> cols) {
        super(entitlementMap, cols);
        if (entitlementMap.containsKey("type")) {
            this.setPermission(ManagedAttribute.isPermission(Util.otoa(entitlementMap.get("type"))));
        }
        initialize(entitlementMap);
    }


    protected void initialize(Map<String, Object> entitlementMap) {
        // For legacy grids/fields
        if(entitlementMap.containsKey("name")) {
            this.attribute = (String)entitlementMap.get("name");
        }
    }

    /**
     * @return Whether the entitlement belongs to IIQ or to an application
     */
    public boolean isIiqEntitlement() {
        return (this.iiqEntitlement);
    }

    /**
     * @return Name of the Application
     */
    public String getApplication() {
        return application;
    }

    public void setApplication(String application) {
        this.application = application;
    }
    /**
     * @return ID of the Application
     */
    public String getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(String applicationId) {
        this.applicationId = applicationId;
    }

    /**
     * @return True if this is a permission.
     */
    public boolean isPermission() {
        return permission;
    }

    public void setPermission(boolean permission) {
        this.permission = permission;
    }

    /**
     * @return True if this is a permission.
     */
    public boolean isGroup() {
        return group;
    }

    public void setGroup(boolean group) { this.group = group; }

    /**
     * @return Permission annotation.
     */
    public String getAnnotation() {
        return annotation;
    }

    public void setAnnotation(String annotation) {
        this.annotation = annotation;
    }

    /**
     * @return Name of attribute or permission target
     */
    public String getAttribute() {
        return attribute;
    }

    public void setAttribute(String attribute) {
        this.attribute = attribute;
    }

    public String getName() { return name; }

    public void setName(String name) { this.name = name; }

    /**
     * @return Value of attribute or permission right.
     */
    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    /**
     * @return Displayable name of attribute or permission target.
     */
    public String getDisplayValue() {
        return displayValue;
    }

    public void setDisplayValue(String displayValue) {
        this.displayValue = displayValue;
    }

    /**
     * @return Localized description for attribute or permission
     */
    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public ManagedAttribute.Type getType() {
        return type;
    }

    public void setType(ManagedAttribute.Type type) {
        this.type = type;
    }

    /**
     * @return The localized string describing the type
     */
    public String getLocalizedType() {
        return localizedType;
    }

    public void setLocalizedType(String localizedType) {
        this.localizedType = localizedType;
    }

    public List<String> getClassificationNames() {
        return classificationNames;
    }

    public void setClassificationNames(List<String> classificationNames) {
        this.classificationNames = classificationNames;
    }

    /**
     * @return The ID of the managed attribute, if one exists
     */
    public String getManagedAttributeId() {
        return managedAttributeId;
    }

    public void setManagedAttributeId(String managedAttributeId) {
        this.managedAttributeId = managedAttributeId;
    }
}
