/* (c) Copyright 2016 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.service.identity;

import java.util.List;
import java.util.Map;

import sailpoint.object.ColumnConfig;
import sailpoint.tools.Util;

/**
 * DTO representing a single entitlement on an identity, including account and attribute/permission data.
 */
public class EntitlementRoleDTO extends IdentityEntitlementDTO {

    /**
     * Identity Id
     */
    private String identityId;
    /**
     * Role Id
     */
    private String roleId;
    /**
     * Assigner name
     */
    private String assigner;
    /**
     * Assignement Id
     */
    private String assignmentId;
    /**
     * Assignment note
     */
    private String assignmentNote;
    /**
     * How the role is acquired
     */
    private String acquired;
    /**
     * Role displayable name
     */
    private String displayableName;
    /**
     * Allow by name
     */
    private String allowedBy;

    /**
     * Role type
     */
    private String roleType;

    /**
     * The role's display name
     */
    private String displayName;

    public EntitlementRoleDTO() {
        super();
    }

    /**
     * Copy constructor.
     *
     * @param dto Original IdentityEntitlementDTO to copy.
     */
    public EntitlementRoleDTO(EntitlementRoleDTO dto) {
        super(dto);
    }

    /**
     * Constructor, call parent's constructor
     *
     * @param entitlementMap an entitlenment object stored in map, object properties are used for key
     * @param cols    list of UI ColumnConfigs of the projection columns
     */
    public EntitlementRoleDTO(Map<String, Object> entitlementMap, List<ColumnConfig> cols) {
        super(entitlementMap, cols);
    }

    @Override
    protected void initialize(Map<String, Object> entitlementMap) {
        // For legacy grids/fields
        if(entitlementMap.containsKey("roleDescription")) {
            setDescription((String)entitlementMap.get("roleDescription"));
        }
    }

    /**
     *
     * @return role description
     */
    public String getRoleDescription() {
        return getDescription();
    }

    public void setRoleDescription(String roleDescription) {
        setDescription(roleDescription);
    }
    /**
     *
     * @return Identity ID
     */
    public String getIdentityId() {
        return identityId;
    }

    public void setIdentityId(String identityId) {
        this.identityId = identityId;
    }

    /**
     *
     * @return Role ID
     */
    public String getRoleId() {
        return roleId;
    }

    public void setRoleId(String roleId) {
        this.roleId = roleId;
    }

    /**
     *
     * @return Assigner name
     */
    public String getAssigner() {
        return assigner;
    }

    public void setAssigner(String assigner) {
        this.assigner = assigner;
    }

    /**
     *
     * @return Assignement ID
     */
    public String getAssignmentId() {
        return assignmentId;
    }

    public void setAssignmentId(String assignmentId) {
        this.assignmentId = assignmentId;
    }

    /**
     *
     * @return Assignemnt Note
     */
    public String getAssignmentNote() {
        return assignmentNote;
    }

    public void setAssignmentNote(String assignmentNote) {
        this.assignmentNote = assignmentNote;
    }

    /**
     *
     * @return Acquired method
     */
    public String getAcquired() {
        return acquired;
    }

    public void setAcquired(String acquired) {
        this.acquired = acquired;
    }

    /**
     * Returns the role dispalyable name
     * @return Role displayable name
     */
    public String getDisplayableName() {
        return displayableName;
    }

    /**
     * Set the role's displayableName
     * @param displayableName The displayableName
     */
    public void setDisplayableName(String displayableName) {
        this.displayableName = displayableName;
    }

    /**
     * Returns display name or displayableName if not set
     * @return Role display name
     */
    public String getDisplayName() {
        return Util.isNullOrEmpty(this.displayName) ? getDisplayableName() : this.displayName;
    }

    /**
     * Set the diaplyname
     * @param displayName The role's displayname
     */
    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    /**
     * 
     * @return Allowed by name
     */
    public String getAllowedBy() {
        return allowedBy;
    }

    public void setAllowedBy(String allowedBy) {
        this.allowedBy = allowedBy;
    }

    /**
     * 
     * @return Role type
     */
    public String getRoleType() {
        return roleType;
    }

    public void setRoleType(String roleType) {
        this.roleType = roleType;
    }
}
