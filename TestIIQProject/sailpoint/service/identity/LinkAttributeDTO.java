/* (c) Copyright 2016 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.service.identity;

import java.util.ArrayList;
import java.util.List;


/**
 * DTO representing the details of link attribute.  
 */
public class LinkAttributeDTO {

    /**
     * Name of the link attribute
     */
    private String name;

    /**
     * Display name of the link attribute.
     */
    private String displayName;

    /**
     * List of values
     */
    private List<Object> values = new ArrayList<>();

    /**
     * True if this is a permission
     */
    private boolean permission;

    /**
     * True if this is an entitlement attribute
     */
    private boolean entitlement;

    /**
     * Description of the attribute
     */
    private String description;


    public LinkAttributeDTO() {}

    /**
     * @return Name of the attribute
     */
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    /**
     * @return List of values for the attributes
     */
    public List<Object> getValues() {
        return values;
    }

    public void setValues(List<Object> values) {
        this.values = values;
    }

    /**
     * @return True if this is a permission attribute
     */
    public boolean isPermission() {
        return permission;
    }

    public void setPermission(boolean permission) {
        this.permission = permission;
    }

    /**
     * @return True if this is an entitlement attribute
     */
    public boolean isEntitlement() {
        return entitlement;
    }

    public void setEntitlement(boolean entitlement) {
        this.entitlement = entitlement;
    }

    /**
     * @return Description
     */
    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * @return Display name of the link attribute.
     */
    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }
}
