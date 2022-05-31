package sailpoint.service.identity;

import java.util.List;
import java.util.Map;

/**
 * DTO for transmitting details about BusinessRoleProfile certification Item Types
 */
public class RoleProfileDTO {

    private String name;
    private String application;
    private List<String> constraints;
    private Map<String, String> permissions;
    private List<BaseEntitlementDTO> entitlements;

    public RoleProfileDTO() {

    }

    public RoleProfileDTO(String name, List<String> constraints, Map<String, String> permissions) {
        this.name = name;
        this.constraints = constraints;
        this.permissions = permissions;
    }

    /**
     * The target name
     * @return The targetName
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the target name
     * @param name The new target name
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * List of localized descriptions of the constraints
     * @return List of localized descriptions of the constraints
     */
    public List<String> getConstraints() {
        return constraints;
    }

    /**
     * Sets the list of constraint descriptions
     * @param constraints List of constraint descriptions
     */
    public void setConstraints(List<String> constraints) {
        this.constraints = constraints;
    }

    /**
     * List of localized descriptions of the permissions
     * @return List of localized descriptions of the permissions
     */
    public Map<String, String> getPermissions() {
        return permissions;
    }

    /**
     * Sets the list of permission descriptions
     * @param permissions The list of permission descriptions
     */
    public void setPermissions(Map<String, String> permissions) {
        this.permissions = permissions;
    }

    public List<BaseEntitlementDTO> getEntitlements() {
        return entitlements;
    }

    public void setEntitlements(List<BaseEntitlementDTO> entitlements) {
        this.entitlements = entitlements;
    }

    public String getApplication() {
        return application;
    }

    public void setApplication(String application) {
        this.application = application;
    }
}
