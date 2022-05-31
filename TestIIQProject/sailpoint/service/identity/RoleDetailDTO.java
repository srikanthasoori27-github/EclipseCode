/* (c) Copyright 2016 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.service.identity;

import java.util.List;

import sailpoint.web.view.IdentitySummary;

/**
 * DTO representing the details of a role in reference to an identity.  
 */
public class RoleDetailDTO {

    /**
     * Bundle ID
     */
    private String id;

    /**
     * Identity ID
     */
    private String identityId;

    /**
     * Display name of the identity
     */
    private String identityDisplayName;

    /**
     * Role Assignment ID 
     */
    private String assignmentId;

    /**
     * True if the identity has the given role.
     */
    private boolean identityHasRole;

    /**
     * True if role is assigned
     */
    private boolean assigned;

    /**
     * True if role is detected
     */
    private boolean detected;

    /**
     * Displayable name for the role
     */
    private String displayName;

    /**
     * Localized string representing type, from the RoleTypeDefinition
     */
    private String type;

    /**
     * String name of the icon to represent this role in the UI, from the RoleTypeDefinition
     */
    private String typeIcon;

    /**
     * Owner of the role
     */
    private IdentitySummary owner;

    /**
     * Localized description of the role
     */
    private String description;

    /**
     * CSV list of roles permitting the given role. Relevant for detected roles only. 
     */
    private String permittedBy;

    /**
     * Comments accompanying the role assignment, if defined.
     */
    private String assignmentNote;

    /**
     * List of TargetAccountDTO holding account information from the assignment/detection
     */
    private List<TargetAccountDTO> accountDetails;

    /**
     * List of EntitlementDTOs representing a flat list of entitlements that contribute to the identity having this role.
     */
    private List<IdentityEntitlementDTO> contributingEntitlements;

    /**
     * List of permitted roles, limited to what the identity has.
     */
    private List<RoleDetailDTO> permittedRoles;

    /**
     * List of required roles, including roles that user does not have.
     */
    private List<RoleDetailDTO> requiredRoles;

    /**
     * List of roles under this one in the hierarchy. Only goes one level. 
     */
    private List<RoleDetailDTO> hierarchy;

    /**
     * Whether this role has a hierarchy with interesting roles or not.  If this role has a hierarchy, this will be true
     * regardless of whether hierarchy is null or not, and is useful for lazily-loading the hierarchy.
     */
    private boolean hasHierarchy;

    /**
     * List of classification display names
     */
    private List<String> classificationNames;

    /**
     * List of profiles on the role
     */
    private List<RoleProfileDTO> profiles;

    public RoleDetailDTO() {}

    public RoleDetailDTO(String roleId, String assignmentId, String identityId) {
        this.id = roleId;
        this.assignmentId = assignmentId;
        this.identityId = identityId;
    }

    /**
     * @return Bundle ID
     */
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    /**
     * @return True if role is assigned
     */
    public boolean isAssigned() {
        return assigned;
    }

    public void setAssigned(boolean assigned) {
        this.assigned = assigned;
    }
    
    /**
     * @return True if role was detected for the identity  
     */
    public boolean getDetected() {
        return detected;
    }

    public void setDetected(boolean detected) {
        this.detected = detected;
    }

    /**
     * @return Role Assignment ID 
     */
    public String getAssignmentId() {
        return assignmentId;
    }

    public void setAssignmentId(String assignmentId) {
        this.assignmentId = assignmentId;
    }

    /**
     * @return Displayable name for the role
     */
    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    /**
     * @return Localized string representing type, from the RoleTypeDefinition
     */
    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    /**
     * @return String name of the icon to represent this role in the UI, from the RoleTypeDefinition
     */
    public String getTypeIcon() {
        return typeIcon;
    }

    public void setTypeIcon(String typeIcon) {
        this.typeIcon = typeIcon;
    }

    /**
     * @return Owner of the role
     */
    public IdentitySummary getOwner() {
        return owner;
    }

    public void setOwner(IdentitySummary owner) {
        this.owner = owner;
    }

    /**
     * @return Localized description of the role
     */
    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * @return CSV list of roles permitting the given role. Relevant for detected roles only. 
     */
    public String getPermittedBy() {
        return permittedBy;
    }

    public void setPermittedBy(String permittedBy) {
        this.permittedBy = permittedBy;
    }

    /**
     * @return Comments accompanying the role assignment, if defined.
     */
    public String getAssignmentNote() {
        return assignmentNote;
    }

    public void setAssignmentNote(String assignmentNote) {
        this.assignmentNote = assignmentNote;
    }

    /**
     * @return True if the identity has the given role.
     */
    public boolean isIdentityHasRole() {
        return identityHasRole;
    }

    public void setIdentityHasRole(boolean identityHasRole) {
        this.identityHasRole = identityHasRole;
    }

    /**
     * @return List of TargetAccountDTO holding account information from the assignment/detection
     */
    public List<TargetAccountDTO> getAccountDetails() {
        return accountDetails;
    }

    public void setAccountDetails(List<TargetAccountDTO> accountDetails) {
        this.accountDetails = accountDetails;
    }

    /**
     * @return List of EntitlementDTOs representing a flat list of entitlements that contribute to the identity having this role.
     */
    public List<IdentityEntitlementDTO> getContributingEntitlements() {
        return contributingEntitlements;
    }

    public void setContributingEntitlements(List<IdentityEntitlementDTO> contributingEntitlements) {
        this.contributingEntitlements = contributingEntitlements;
    }

    /**
     * @return Identity ID
     */
    public String getIdentityId() {
        return identityId;
    }

    public void setIdentityId(String identityId) {
        this.identityId = identityId;
    }

    /**
     * @return Identity Display Name
     */
    public String getIdentityDisplayName() { return identityDisplayName; }

    public void setIdentityDisplayName(String identityDisplayName) { this.identityDisplayName = identityDisplayName; }

    /**
     * @return True if role is detected
     */
    public boolean isDetected() {
        return detected;
    }

    /**
     * @return List of permitted roles, limited to what the identity has.
     */
    public List<RoleDetailDTO> getPermittedRoles() {
        return permittedRoles;
    }

    public void setPermittedRoles(List<RoleDetailDTO> permittedRoles) {
        this.permittedRoles = permittedRoles;
    }

    /**
     * @return List of required roles, including roles that user does not have.
     */
    public List<RoleDetailDTO> getRequiredRoles() {
        return requiredRoles;
    }

    public void setRequiredRoles(List<RoleDetailDTO> requiredRoles) {
        this.requiredRoles = requiredRoles;
    }

    /**
     * @return List of roles under this one in the hierarchy. Only goes one level. 
     */
    public List<RoleDetailDTO> getHierarchy() {
        return hierarchy;
    }

    public void setHierarchy(List<RoleDetailDTO> hierarchy) {
        this.hierarchy = hierarchy;
    }

    /**
     * @return True if this role has a hierarchy.  This may be true even if the hierarchy attribute is null, and is
     * useful for lazily-loading a role hierarchy.
     */
    public boolean getHasHierarchy() {
        return hasHierarchy;
    }

    public void setHasHierarchy(boolean hasHierarchy) {
        this.hasHierarchy = hasHierarchy;
    }

    public List<String> getClassificationNames() {
        return classificationNames;
    }

    public void setClassificationNames(List<String> classificationNames) {
        this.classificationNames = classificationNames;
    }

    public List<RoleProfileDTO> getProfiles() {
        return profiles;
    }

    public void setProfiles(List<RoleProfileDTO> profiles) {
        this.profiles = profiles;
    }
}
