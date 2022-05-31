/* (c) Copyright 2016 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.service.module;

import sailpoint.object.ColumnConfig;
import sailpoint.service.BaseDTO;

import java.util.List;
import java.util.Map;

/**
 * DTO representing the details of a module
 */
public class ModuleDTO extends BaseDTO {

    /**
     * Module ID
     */
    private String id;

    /**
     * Application Name
     */
    private String name;

    /**
     * Description of the module
     */
    private String description;

    /**
     * The class which will provide module health check
     */
    private String healthCheckClass;

    /**
     * A boolean to indicate whether this module is enabled in system config
     */
    private Boolean enabled;


    public ModuleDTO() {}

    public ModuleDTO(Map<String,Object> app, List<ColumnConfig> cols, List<String> additionalColumns) {
        super(app, cols, additionalColumns);
    }

    /**
     * @return Module Id
     */
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    /**
     * @return Description of the module
     */
    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * @return the class name that provides for the module
     */
    public String getHealthCheckClass() {
        return healthCheckClass;
    }

    public void setHealthCheckClass(String healthCheckClass) {
        this.healthCheckClass = healthCheckClass;
    }

    /**
     * @return a boolean indicating whether this module is enabled.
     */
    public Boolean isEnabled() { return enabled; }

    public void setEnabled(Boolean enabled) { this.enabled = enabled; }

    /**
     * @return Name of the module
     */
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

}