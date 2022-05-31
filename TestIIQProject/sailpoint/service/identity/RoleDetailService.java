/* (c) Copyright 2016 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.service.identity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.AccountGroupService;
import sailpoint.api.EntitlementCorrelator;
import sailpoint.api.Explanator;
import sailpoint.api.ManagedAttributer;
import sailpoint.object.Application;
import sailpoint.object.ApprovalItem;
import sailpoint.object.Bundle;
import sailpoint.object.CertificationItem;
import sailpoint.object.EntitlementGroup;
import sailpoint.object.Identity;
import sailpoint.object.IdentityRequest;
import sailpoint.object.ManagedAttribute;
import sailpoint.object.Permission;
import sailpoint.object.Profile;
import sailpoint.object.RoleAssignment;
import sailpoint.object.RoleDetection;
import sailpoint.object.RoleTypeDefinition;
import sailpoint.service.ApprovalItemsService;
import sailpoint.service.LinkService;
import sailpoint.service.certification.RoleProfileHelper;
import sailpoint.service.classification.ClassificationService;
import sailpoint.tools.GeneralException;
import sailpoint.tools.InvalidParameterException;
import sailpoint.tools.ObjectNotFoundException;
import sailpoint.tools.Util;
import sailpoint.web.UserContext;
import sailpoint.web.util.WebUtil;
import sailpoint.web.view.IdentitySummary;

/**
 * Service to get role details for the UI
 */
public class RoleDetailService {

    /**
     * List of role types that are not interesting to include in hierarchies, permits or requirements.
     */
    private static final List<String> EXCLUDED_ROLE_TYPES = Arrays.asList("organizational");
    private static final Log log = LogFactory.getLog(RoleDetailService.class);

    private UserContext userContext;
    private boolean classificationsEnabled = true;
    
    public RoleDetailService(UserContext userContext) {
        this.userContext = userContext;
    }

    public RoleDetailService(UserContext userContext, boolean classificationsEnabled) {
        this.userContext = userContext;
        this.classificationsEnabled = classificationsEnabled;
    }

    /**
     * Get the role details for the current role assignment for identity requests.
     *
     * @param roleId                ID of the Bundle object
     * @param assignmentId          RoleAssignment ID. Possibly null.
     * @param identityId            Identity ID
     * @param identityRequestId     identity request id
     * @param identityRequestItemId Identity request item id
     * @return RoleDetailDTO object
     * @throws GeneralException
     */
    public RoleDetailDTO getDetails(String roleId, String assignmentId, String identityId, String identityRequestId,
                                    String identityRequestItemId) throws GeneralException {
        RoleDetailDTO roleDetailDTO = getDetails(roleId, assignmentId, identityId, true);

        // If account details is not set see if we can set it but make sure we have all the necessary data.
        if (Util.isEmpty(roleDetailDTO.getAccountDetails()) && Util.isNotNullOrEmpty(identityId) &&
                Util.isNotNullOrEmpty(identityRequestId) && Util.isNotNullOrEmpty(identityRequestItemId)) {
            TargetAccountService targetAccountService = new TargetAccountService(this.userContext.getContext(), identityId);
            roleDetailDTO.setAccountDetails(targetAccountService.getTargetsForRequestItem(identityRequestId, identityRequestItemId));
        }

        return roleDetailDTO;
    }

    /**
     * Get the role details for the current role from an approval item
     *
     * @param roleId            ID of the Bundle object
     * @param assignmentId      RoleAssignment ID
     * @param identityId        Identity ID
     * @param identityRequestId the identity request id
     * @param approvalItem      the approval item
     * @return RoleDetailDTO dto object with role details from approval item.
     * @throws GeneralException
     */
    public RoleDetailDTO getDetails(String roleId, String assignmentId, String identityId,
                                    String identityRequestId, ApprovalItem approvalItem) throws GeneralException {
        RoleDetailDTO roleDetailDTO = getDetails(roleId, assignmentId, identityId, true);

        if (Util.isNotNullOrEmpty(identityId)) {
            Bundle role = this.userContext.getContext().getObjectById(Bundle.class, roleId);
            if (Util.isEmpty(roleDetailDTO.getAccountDetails())) {
                TargetAccountService accountService = new TargetAccountService(this.userContext.getContext(), identityId);
                List<TargetAccountDTO> targets = accountService.getTargetsForApprovalItem(identityRequestId, role.getName(), assignmentId);
                roleDetailDTO.setAccountDetails(targets);
            }

            Identity identity = this.userContext.getContext().getObjectById(Identity.class, identityId);
            roleDetailDTO.setIdentityDisplayName(identity.getDisplayableName());
            if (Util.isNullOrEmpty(roleDetailDTO.getAssignmentNote())) {
                IdentityRequest identityRequest = this.userContext.getContext().getObjectById(IdentityRequest.class, identityRequestId);
                String assignmentNote = null;
                if (identity != null && identityRequest != null) {
                    ApprovalItemsService itemService = new ApprovalItemsService(this.userContext.getContext(), approvalItem);
                    assignmentNote = WebUtil.escapeComment(itemService.getAssignmentNote(identityRequest, identity));
                }
                roleDetailDTO.setAssignmentNote(assignmentNote);
            }
        }
        return roleDetailDTO;
    }


    /**
     * Get the role details for the current role from a certification item
     *
     * @param roleId            ID of the Bundle object
     * @param assignmentId      RoleAssignment ID. Possibly null.
     * @param identityId        Identity ID
     * @param certificationItem CertificationItem
     * @return RoleDetailDTO object with account details from certification item.
     * @throws GeneralException
     */
    public RoleDetailDTO getDetails(String roleId, String assignmentId, String identityId, CertificationItem certificationItem) throws GeneralException {
        RoleDetailDTO roleDetailDTO = getDetails(roleId, assignmentId, identityId, true);

        if (certificationItem != null && Util.isNotNullOrEmpty(identityId)) {
            TargetAccountService targetAccountService = new TargetAccountService(this.userContext.getContext(), identityId);
            // The role targets are stored on the certification item, so use those instead of what we found on the identity.
            List<TargetAccountDTO> targets = targetAccountService.getTargetsForCertificationItem(certificationItem);
            roleDetailDTO.setAccountDetails(targets);
        }

        return roleDetailDTO;
    }

    /**
     * Get the role details for the current role assignment.
     *
     * @param roleId       ID of the Bundle object
     * @param assignmentId RoleAssignment ID. Possibly null.
     * @param identityId   Identity ID
     * @return RoleDetailDTO object
     * @throws GeneralException
     */
    public RoleDetailDTO getDetails(String roleId, String assignmentId, String identityId) throws GeneralException {
        return getDetails(roleId, assignmentId, identityId, true);
    }

    /**
     * Get the role details for the current role assignment.
     *
     * @param roleId       ID of the Bundle object
     * @param assignmentId RoleAssignment ID. Possibly null.
     * @param identityId   Identity ID
     * @param goDeep       If true, get all the permits/requirements along with hierarchy. If false, skip those for a "flat" representation.
     * @return RoleDetailDTO object
     * @throws GeneralException
     */
    private RoleDetailDTO getDetails(String roleId, String assignmentId, String identityId, boolean goDeep) throws GeneralException {

        if (roleId == null) {
            throw new InvalidParameterException("roleId");
        }

        Bundle role = this.userContext.getContext().getObjectById(Bundle.class, roleId);
        if (role == null) {
            throw new ObjectNotFoundException(Bundle.class, roleId);
        }

        // 1. Set basic role information
        RoleDetailDTO dto = new RoleDetailDTO(roleId, assignmentId, identityId);
        dto.setDisplayName(role.getDisplayableName());
        dto.setDescription(role.getDescription(this.userContext.getLocale()));
        if (role.getOwner() != null) {
            dto.setOwner(new IdentitySummary(role.getOwner()));
        }
        // don't set the classifications flag if we are intentionally hiding classifications data
        if (this.classificationsEnabled) {
            dto.setClassificationNames(new ClassificationService(this.userContext.getContext()).getClassificationNames(Bundle.class, roleId));
        }
        dto.setProfiles(getProfiles(role));

        //2. Set role type information
        RoleTypeDefinition roleType = role.getRoleTypeDefinition();
        if (roleType != null) {
            dto.setType(roleType.getDisplayableName());
            dto.setTypeIcon(roleType.getIcon());
        }

        Identity identity = null;

        // 3. Pull off interesting details based on if role is assigned or detected.
        if (identityId != null) {
            // figure out is the role is assigned or detected
            identity = this.userContext.getContext().getObjectById(Identity.class, identityId);
            dto.setIdentityDisplayName(identity.getDisplayableName());
            RoleAssignment roleAssignment = (assignmentId != null) ? identity.getRoleAssignmentById(assignmentId) : null;
            boolean identityHasRole = identity.hasRole(role, true);
            boolean isAssigned = identityHasRole && isAssigned(identity, roleId);
            boolean isDetected = identityHasRole && identity.getDetectedRole(roleId) != null;

            TargetAccountService targetAccountService = new TargetAccountService(this.userContext.getContext(), identityId);
            dto.setIdentityHasRole(identityHasRole);
            dto.setAssigned(isAssigned);
            dto.setDetected(isDetected);
            if (isAssigned && isDetected) {
                if (roleAssignment != null) {
                    dto.setAssignmentNote(roleAssignment.getComments());
                }
                // for assigned & detected we might have to pull the account info using detected role logic. check assigned first
                List<TargetAccountDTO> targetsForIdentity = targetAccountService.getTargetsForIdentity(roleId, assignmentId);
                if (!Util.isEmpty(targetsForIdentity)) {
                    dto.setAccountDetails(targetsForIdentity);
                } else {
                    setDetectedRoleInfo(roleId, assignmentId, dto, identity, targetAccountService);
                }
            }
            else if (isAssigned) {
                if (roleAssignment != null) {
                    dto.setAssignmentNote(roleAssignment.getComments());
                }
                dto.setAccountDetails(targetAccountService.getTargetsForIdentity(roleId, assignmentId));
            } else if (isDetected) {
                setDetectedRoleInfo(roleId, assignmentId, dto, identity, targetAccountService);
            }
            // 4. Get the contributing entitlements
            dto.setContributingEntitlements(getContributingEntitlements(identity, roleAssignment, role));
        }

        // 5. Get the permits and requirements, along with the hierarchy, if needed.
        if (goDeep) {
            dto.setPermittedRoles(getPermittedRoles(role, assignmentId, identity));
            dto.setRequiredRoles(getRequiredRoles(role, assignmentId, identity));
            dto.setHierarchy(getSubRoleDetailDtos(role.getInheritance(), assignmentId, identity));
        }

        // Always set whether there is a hierarchy or not.  We'll conditionally load the deeper info.
        dto.setHasHierarchy(hasHierarchy(role));

        return dto;
    }

    private void setDetectedRoleInfo(String roleId, String assignmentId, RoleDetailDTO dto, Identity identity, TargetAccountService targetAccountService) throws GeneralException {
        RoleDetection roleDetection = identity.getRoleDetection(assignmentId, roleId);
        List<String> permittedByList = new ArrayList<>();
        if (roleDetection != null) {
            for (String permittedById : Util.iterate(roleDetection.getAssignmentIdList())) {
                RoleAssignment permittingAssignment = identity.getRoleAssignmentById(permittedById);
                if (permittingAssignment != null) {
                    Bundle permittedByRole = this.userContext.getContext().getObjectById(Bundle.class, permittingAssignment.getRoleId());
                    permittedByList.add(permittedByRole.getDisplayableName());
                }
            }
        }
        if (!Util.isEmpty(permittedByList)) {
            dto.setPermittedBy(Util.listToCsv(permittedByList));
        }
        dto.setAccountDetails(targetAccountService.getTargetsForRoleDetection(roleId, assignmentId));
    }

    /**
     * Return true if this role has a hierarchy that includes interesting roles as parents or ancestors.
     */
    private static boolean hasHierarchy(Bundle role) {
        // Climb up the inheritance to see if there are any non-excluded types.
        for (Bundle superRole : Util.iterate(role.getInheritance())) {
            if (!EXCLUDED_ROLE_TYPES.contains(superRole.getType())) {
                return true;
            }

            // Recurse.
            boolean superHasHierarchy = hasHierarchy(superRole);
            if (superHasHierarchy) {
                return true;
            }
        }

        // No supers were found, so return false.
        return false;
    }

    /**
     * Get the list of RoleDetailDTO representing the next level of inheritance for the given role
     *
     * @param roleId       Role ID
     * @param assignmentId Role Assignment ID
     * @param identityId   Identity ID
     * @return List of RoleDetailDTO representing the next level of inheritance hierarchy
     * @throws GeneralException
     */
    public List<RoleDetailDTO> getHierarchy(String roleId, String assignmentId, String identityId) throws GeneralException {
        if (roleId == null) {
            throw new InvalidParameterException("roleId");
        }
        Identity identity = null;
        Bundle role = this.userContext.getContext().getObjectById(Bundle.class, roleId);
        if (identityId != null) {
            identity = this.userContext.getContext().getObjectById(Identity.class, identityId);
        }
        return getSubRoleDetailDtos(role.getInheritance(), assignmentId, identity);
    }

    /**
     * Get a flattened list of all permits in the role hierarchy
     */
    private List<RoleDetailDTO> getPermittedRoles(Bundle bundle, String assignmentId, Identity identity) throws GeneralException {
        List<RoleDetailDTO> dtos = new ArrayList<>();
        addPermittedRoles(dtos, bundle, assignmentId, identity);
        return dtos;
    }

    /**
     * Add flattened list of permits to the given dto list, skipping dupes
     */
    private void addPermittedRoles(List<RoleDetailDTO> dtos, Bundle bundle, String assignmentId, Identity identity) throws GeneralException {
        addSubRoleDetailDtos(dtos, bundle.getPermits(), assignmentId, identity);
        for (Bundle inheritanceBundle : Util.iterate(bundle.getInheritance())) {
            addPermittedRoles(dtos, inheritanceBundle, assignmentId, identity);
        }
    }

    /**
     * Get a flattened list of all requirements in the role hierarchy
     */
    private List<RoleDetailDTO> getRequiredRoles(Bundle bundle, String assignmentId, Identity identity) throws GeneralException {
        List<RoleDetailDTO> dtos = new ArrayList<>();
        addRequiredRoles(dtos, bundle, assignmentId, identity);
        return dtos;
    }

    /**
     * Add flattened list of requirements to the given dto list, skipping dupes
     */
    private void addRequiredRoles(List<RoleDetailDTO> dtos, Bundle bundle, String assignmentId, Identity identity) throws GeneralException {
        addSubRoleDetailDtos(dtos, bundle.getRequirements(), assignmentId, identity);
        for (Bundle inheritanceBundle : Util.iterate(bundle.getInheritance())) {
            addRequiredRoles(dtos, inheritanceBundle, assignmentId, identity);
        }
    }

    /**
     * Add shallow RoleDetailDTO objects based on the given bundle list to the given list of dtos, excluding duplicates.
     */
    private void addSubRoleDetailDtos(List<RoleDetailDTO> dtos, List<Bundle> bundles, String assignmentId, Identity identity) throws GeneralException {
        for (Bundle bundle : Util.iterate(bundles)) {
            String identityId = null;
            if (identity != null) {
                identityId = identity.getId();
            }
            if (!Util.nullSafeContains(EXCLUDED_ROLE_TYPES, bundle.getType()) &&
                    !hasRole(dtos, bundle)) {
                dtos.add(getDetails(bundle.getId(), assignmentId, identityId, false));
            }

        }
    }

    /**
     * Create a list of shallow RoleDetailDTO objects based on the given bundle list.
     */
    private List<RoleDetailDTO> getSubRoleDetailDtos(List<Bundle> bundles, String assignmentId, Identity identity)
            throws GeneralException {
        List<RoleDetailDTO> dtos = new ArrayList<>();
        addSubRoleDetailDtos(dtos, bundles, assignmentId, identity);
        return dtos;
    }

    /**
     * Check if the given role is already in the dto list
     */
    private boolean hasRole(List<RoleDetailDTO> dtos, Bundle role) {
        for (RoleDetailDTO dto : Util.iterate(dtos)) {
            if (Util.nullSafeEq(dto.getId(), role.getId())) {
                return true;
            }
        }

        return false;
    }

    /**
     * Create a flattened list of entitlement DTOs representing the entitlements that contribute to the given role.
     */
    @SuppressWarnings("unchecked")
    private List<IdentityEntitlementDTO> getContributingEntitlements(Identity identity, RoleAssignment roleAssignment, Bundle role) throws GeneralException {
        AccountGroupService accountGroupService = new AccountGroupService(this.userContext.getContext());

        List<IdentityEntitlementDTO> entitlementDTOs = new ArrayList<>();
        EntitlementCorrelator entitlementCorrelator = new EntitlementCorrelator(this.userContext.getContext());
        List<EntitlementGroup> entitlementGroups = entitlementCorrelator.getContributingEntitlements(identity,
                        roleAssignment != null && roleAssignment.isNegative() ? null : roleAssignment,
                        role, false);
        LinkService linkService = new LinkService(this.userContext.getContext());
        for (EntitlementGroup entitlementGroup : Util.iterate(entitlementGroups)) {
            IdentityEntitlementDTO baseDTO = new IdentityEntitlementDTO();
            baseDTO.setApplication(entitlementGroup.getApplicationName());
            baseDTO.setNativeIdentity(entitlementGroup.getNativeIdentity());
            baseDTO.setInstance(entitlementGroup.getInstance());
            baseDTO.setAccountName(linkService.getAccountDisplayName(identity, entitlementGroup.getApplicationName(), entitlementGroup.getInstance(), entitlementGroup.getNativeIdentity()));
            Application application = entitlementGroup.getApplicationObject(this.userContext.getContext());
            for (Permission permission : Util.iterate(entitlementGroup.getPermissions())) {
                IdentityEntitlementDTO basePermissionDTO = new IdentityEntitlementDTO(baseDTO);
                basePermissionDTO.setPermission(true);
                basePermissionDTO.setAnnotation(permission.getAnnotation());
                basePermissionDTO.setAttribute(permission.getTarget());
                basePermissionDTO.setDescription(Explanator.getPermissionDescription(application, permission.getTarget(), this.userContext.getLocale()));
                for (String right : Util.iterate(permission.getRightsList())) {
                    IdentityEntitlementDTO dto = new IdentityEntitlementDTO(basePermissionDTO);
                    dto.setValue(right);
                    dto.setDisplayValue(right);
                    entitlementDTOs.add(dto);
                }
            }

            for (String attributeName : Util.iterate(entitlementGroup.getAttributeNames())) {
                List<Object> attributeValues = Util.asList(entitlementGroup.getAttributes().get(attributeName));
                //IIQTC-60 :- Since attributeValues could be null, we need to validate it to avoid a NPE.
                if (attributeValues != null) {
                    for (Object oValue : attributeValues) {
                        String attributeValue = String.valueOf(oValue);
                        IdentityEntitlementDTO dto = new IdentityEntitlementDTO(baseDTO);
                        dto.setPermission(false);
                        dto.setAttribute(attributeName);
                        dto.setValue(attributeValue);
                        dto.setDisplayValue(Explanator.getDisplayValue(application, attributeName, attributeValue));
                        dto.setDescription(Explanator.getDescription(application, attributeName, attributeValue, this.userContext.getLocale()));
                        dto.setGroup(accountGroupService.isGroupAttribute(application.getName(), attributeName));
                        ManagedAttribute managedAttribute = ManagedAttributer.get(this.userContext.getContext(), application.getId(), attributeName, attributeValue);
                        if (managedAttribute != null) {
                            dto.setManagedAttributeId(managedAttribute.getId());
                        }
                        entitlementDTOs.add(dto);
                    }
                }
            }
        }

        return entitlementDTOs;
    }

    public List<RoleProfileDTO> getProfiles(Bundle role) {
        List<RoleProfileDTO> profiles = new ArrayList<>();
        for (Profile profile : Util.iterate(role.getProfiles())) {
            RoleProfileHelper helper = new RoleProfileHelper(profile, this.userContext.getContext(), this.userContext.getLocale(), this.userContext.getUserTimeZone());
            RoleProfileDTO dto = helper.getRoleProfileDTO(role, this.classificationsEnabled);
            if (dto != null) {
                profiles.add(dto);
            }
        }

        return profiles;
    }

    private boolean isAssigned(Identity identity, String roleId) {
        boolean assigned = false;
        // It's considered assigned if it is assigned or permitted by an assignment
        List<Bundle> assignments = identity.getAssignedRoles();
        for (Bundle assignment : Util.safeIterable(assignments)) {
            if (roleId.equals(assignment.getId())) {
                assigned = true;
                break;
            } else {
                Collection<Bundle> permits = assignment.getFlattenedPermits();
                for (Bundle permit : Util.safeIterable(permits)) {
                    if (roleId.equals(permit.getId())) {
                        assigned = true;
                        break;
                    }
                }
            }
        }
        return assigned;
    }
}
