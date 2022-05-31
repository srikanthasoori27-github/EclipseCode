/* (c) Copyright 2016 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.service.identity;

import java.util.List;
import java.util.Map;

import sailpoint.object.ColumnConfig;
import sailpoint.service.BaseDTO;
import sailpoint.web.view.IdentitySummary;

/**
 * DTO representing the details of an application tied to an entitlement.  
 */
public class ApplicationDTO extends BaseDTO {

    /**
     * Application ID
     */
    private String id;

    /**
     * Application Name
     */
    private String name;

    /**
     * Description of the application
     */
    private String description;

    /**
     * Application type 
     */
    private String type;

    /**
     * Owner of the application
     */
    private IdentitySummary owner;

    /**
     * List of remediators
     */
    private List<IdentitySummary> remediators;

    public ApplicationDTO() {}

    public ApplicationDTO(Map<String,Object> app, List<ColumnConfig> cols, List<String> additionalColumns) {
        super(app, cols, additionalColumns);
    }

    /**
     * @return Application Id
     */
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    /**
     * @return Description of the application
     */
    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * @return Application Owner
     */
    public IdentitySummary getOwner() {
        return owner;
    }

    public void setOwner(IdentitySummary owner) {
        this.owner = owner;
    }

    /**
     * @return List of Remediators for the application
     */
    public List<IdentitySummary> getRemediators() {
        return remediators;
    }

    public void setRemediators(List<IdentitySummary> remediators) {
        this.remediators = remediators;
    }

    /**
     * @return Application Type
     */
    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    /**
     * @return Name of the application
     */
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

}