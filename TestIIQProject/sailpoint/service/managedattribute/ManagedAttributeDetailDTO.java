/* (c) Copyright 2016 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.service.managedattribute;

import sailpoint.service.identity.IdentityEntitlementDTO;

import java.util.List;
import java.util.Map;

/**
 * DTO holding details of a managed attribute
 */
public class ManagedAttributeDetailDTO  {

    /**
     * ID of the ManagedAttribute object
     */
    private String id;

    /**
     * Name of the application
     */
    private String application;

    /**
     * Type of the managed attribute
     */
    private String type;

    /**
     * Name of the attribute
     */
    private String attribute;

    /**
     * Value of the attribute
     */
    private String value;

    /**
     * Display value of the attribute
     */
    private String displayValue;

    /**
     * Description of the attribute
     */
    private String description;

    /**
     * Owner of the attribute
     */
    private String owner;

    /**
     * Flag indicating managed attribute can be requested
     */
    private boolean requestable;

    /**
     * Map with name/value pairs for any extended attributes with value on the managed attribute
     */
    private Map<String, String> extendedAttributes;
    
    /**
     * Attributes of the account group that are not entitlements.
     */
    private Map<String, Object> groupAttributes;

    /**
     * Entitlements from the account group.
     */
    private List<IdentityEntitlementDTO> groupEntitlements;

    /**
     * Flag to indicate this managed attribute has some inheritance.
     */
    private boolean hasInheritance;

    /**
     * Flag to indicate whether this managed attribute has effective access objects.
     */
    private boolean hasAccess;

    /**
     * Flag to indicate whether this managed attribute has members.
     */
    private boolean hasMembers;

    /**
     * Flag to indicate whether this managed attribute has classifications.
     */
    private boolean hasClassifications;

    /**
     * @return ID of the ManagedAttribute object
     */
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    /**
     * @return Name of the application
     */
    public String getApplication() {
        return application;
    }

    public void setApplication(String application) {
        this.application = application;
    }

    /**
     * @return Type of the managed attribute
     */
    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    /**
     * @return Name of the attribute
     */
    public String getAttribute() {
        return attribute;
    }

    public void setAttribute(String attribute) {
        this.attribute = attribute;
    }

    /**
     * @return Value of the attribute
     */
    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    /**
     * @return Display value of the attribute
     */
    public String getDisplayValue() {
        return displayValue;
    }

    public void setDisplayValue(String displayValue) {
        this.displayValue = displayValue;
    }

    /**
     * @return Description of the attribute
     */
    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * @return Flag indicating managed attribute can be requested
     */
    public boolean isRequestable() {
        return requestable;
    }

    public void setRequestable(boolean requestable) {
        this.requestable = requestable;
    }

    /**
     * @return Map with name/value pairs for any extended attributes with value on the managed attribute
     */
    public Map<String, String> getExtendedAttributes() {
        return extendedAttributes;
    }

    public void setExtendedAttributes(Map<String, String> extendedAttributes) {
        this.extendedAttributes = extendedAttributes;
    }

    /**
     * @return Attributes of the account group that are not entitlements.
     */
    public Map<String, Object> getGroupAttributes() {
        return groupAttributes;
    }

    public void setGroupAttributes(Map<String, Object> groupAttributes) {
        this.groupAttributes = groupAttributes;
    }

    /**
     * @return Entitlements of the account group.
     */
    public List<IdentityEntitlementDTO> getGroupEntitlements() {
        return groupEntitlements;
    }

    public void setGroupEntitlements(List<IdentityEntitlementDTO> groupEntitlements) {
        this.groupEntitlements = groupEntitlements;
    }

    /**
     * @return Owner of the attribute.
     */
    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    public boolean isHasInheritance() {
        return hasInheritance;
    }

    public void setHasInheritance(boolean hasInheritance) {
        this.hasInheritance = hasInheritance;
    }

    /**
     * @return flag to indicate whether this managed attribute has effective access objects.
     */
    public boolean isHasAccess() {
        return hasAccess;
    }

    public void setHasAccess(boolean hasAccess) {
        this.hasAccess = hasAccess;
    }

    /**
     * @return flag to indicate whether this managed attribute has members.
     */
    public boolean isHasMembers() {
        return hasMembers;
    }

    public void setHasMembers(boolean hasMembers) {
        this.hasMembers = hasMembers;
    }

    public boolean isHasClassifications() {
        return hasClassifications;
    }

    public void setHasClassifications(boolean hasClassifications) {
        this.hasClassifications = hasClassifications;
    }
}
