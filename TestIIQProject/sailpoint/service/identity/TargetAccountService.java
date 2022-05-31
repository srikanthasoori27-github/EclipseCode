/* (c) Copyright 2016 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.service.identity;

import sailpoint.api.SailPointContext;
import sailpoint.object.Bundle;
import sailpoint.object.CertificationItem;
import sailpoint.object.Identity;
import sailpoint.object.IdentityRequest;
import sailpoint.object.IdentityRequestItem;
import sailpoint.object.RoleAssignment;
import sailpoint.object.RoleDetection;
import sailpoint.object.RoleTarget;
import sailpoint.service.identityrequest.IdentityRequestService;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

import java.util.ArrayList;
import java.util.List;

/**
 * Class to help getting target accounts for roles.
 */
public class TargetAccountService {
    
    private SailPointContext context;
    private String identityId;
    
    public TargetAccountService(SailPointContext context, String identityId) {
        this.context = context;
        this.identityId = identityId;
    }

    /**
     * Get a list of TargetAccountDTO that are part of the RoleDetection if found
     * @param roleId ID for the Bundle object
     * @param roleAssignmentId Role assignment ID
     * @return List of TargetAcccountDTO objects
     * @throws GeneralException
     */
    public List<TargetAccountDTO> getTargetsForRoleDetection(String roleId, String roleAssignmentId) throws GeneralException {
        Identity identity = getIdentity();
        RoleDetection roleDetection = null;
        
        //find the RoleDetection based on assignmentId and roleId
        for (RoleDetection rd : Util.safeIterable(identity.getRoleDetections())) {
            if (Util.nullSafeEq(roleId, rd.getRoleId()) &&
                    (Util.isNullOrEmpty(roleAssignmentId) || rd.getAssignmentIdList().contains(roleAssignmentId))) {
                roleDetection = rd;
            }
        }

        return getTargetsForRoleDetection(roleDetection);
    }

    /**
     * Gets the target accounts for an identity based on the role assignment
     * @param roleId Bundle ID
     * @param roleAssignmentId Role assignment ID
     * @return List of TargetAccountDTO objects
     * @throws GeneralException
     */
    public List<TargetAccountDTO> getTargetsForIdentity(String roleId, String roleAssignmentId) throws GeneralException {
        Identity identity = getIdentity();
        RoleAssignment roleAssignment = null;

        if (identity != null) {
            if (!Util.isEmpty(roleAssignmentId)) {
                roleAssignment = identity.getRoleAssignmentById(roleAssignmentId);
            } else {
                // todo rshea TA4710 : this should be fine as it is; may produce more than one detail with the same
                // role name but that's what we want, i think
                // TODO MT: I want to get rid of this but I am too afraid.
                roleAssignment = identity.getRoleAssignment(roleId);
            }
        }

        return getTargetsForRoleAssignment(roleAssignment);
    }

    /**
     * Get the target accounts associated with an identity request item
     * @param identityRequestId Identity Request ID
     * @param identityRequestItemId Identity Request Item ID
     * @return List of TargetAccountDTO objects
     * @throws GeneralException
     */
    public List<TargetAccountDTO> getTargetsForRequestItem(String identityRequestId, String identityRequestItemId) throws GeneralException {
        Identity identity = getIdentity();
        List<TargetAccountDTO> targetAccountDTOs = new ArrayList<>();

        IdentityRequest request = this.context.getObjectById(IdentityRequest.class, identityRequestId);
        if (request != null) {
            IdentityRequestService service = new IdentityRequestService(this.context, request);
            IdentityRequestItem item = service.findIdentityRequestItemById(identityRequestItemId);
            targetAccountDTOs.addAll(service.getTargetAccounts(item, identity));
        }
        
        return targetAccountDTOs;
    }

    /**
     * Get the target accounts for an identity request item based on the role assignment ID
     * @param identityRequestId IDentity request ID
     * @param roleName Name of the role 
     * @param roleAssignmentId Assignment ID for the role
     * @return List of TargetAccountDTO objects
     * @throws GeneralException
     */
    public List<TargetAccountDTO> getTargetsForApprovalItem(String identityRequestId, String roleName, String roleAssignmentId) throws GeneralException {
        List<TargetAccountDTO> targetAccountDTOs = new ArrayList<>();
        Identity identity = getIdentity();
        
        IdentityRequest request = this.context.getObjectById(IdentityRequest.class, identityRequestId);
        if (request != null) {
            IdentityRequestService service = new IdentityRequestService(this.context, request);
            IdentityRequestItem item = service.findIdentityRequestItemByAssignmentIdAndValue(roleAssignmentId, roleName);
            targetAccountDTOs.addAll(service.getTargetAccounts(item, identity));
        }
        
        return targetAccountDTOs;
    }

    /**
     * Get the target accounts for a certification item.  This first looks on the CertificationItem for
     * the role assignment or detection, but falls back to considering the current identity state if nothing is
     * set in the item. This is because pre-7.3 the cert items did NOT have this information.
     *
     * @param certificationItem CertificationItem. This is assumed to be a role item.
     * @return List of TargetAccountDTo objects
     * @throws GeneralException
     */
    public List<TargetAccountDTO> getTargetsForCertificationItem(CertificationItem certificationItem)
        throws GeneralException {
        List<TargetAccountDTO> targetAccountDTOS = new ArrayList<>();
        RoleAssignment roleAssignment = certificationItem.getRoleAssignment();
        RoleDetection roleDetection = certificationItem.getRoleDetection();

        // If neither are set on the item, this must be a pre-7.3 item where we did not store the role assignment
        // information. So look it up the old fashioned way.
        if (roleAssignment == null && roleDetection == null) {
            String bundleAssignmentId = certificationItem.getBundleAssignmentId();
            Bundle bundle = certificationItem.getBundle(this.context);
            Identity identity = getIdentity();
            if (bundleAssignmentId != null) {
                roleAssignment = identity.getRoleAssignmentById(bundleAssignmentId);

                // If the assignment doesn't exist or doesn't match what we are expecting, this must be a detected
                // role as part of an assignment. This usually does not happen because these are rolled up, however
                // for role membership certifications, they might have their own line.

                // IIQTC-351: 'NOT-NULL' validation added to maintain backward compatibility in case a bundle was
                // deleted in a previous version of the product.
                if (bundle != null && (roleAssignment == null || !Util.nullSafeEq(roleAssignment.getRoleId(), bundle.getId()))) {
                    roleDetection = identity.getRoleDetection(bundleAssignmentId, bundle.getId());
                    roleAssignment = null;
                }

            } else if (bundle != null) {
                // No assignment id, must be unassigned detected role
                roleDetection = identity.getUnassignedRoleDetection(bundle);
            }
        }

        if (roleAssignment != null) {
            targetAccountDTOS.addAll(getTargetsForRoleAssignment(roleAssignment));
        } else if (roleDetection != null) {
            targetAccountDTOS.addAll(getTargetsForRoleDetection(roleDetection));
        }

        return targetAccountDTOS;
    }


    /**
     * Get a list of TargetAccountDTO that are part of the RoleDetection
     * @param roleDetection RoleDetection object
     * @return List of TargetAcccountDTO objects
     * @throws GeneralException
     */
    private List<TargetAccountDTO> getTargetsForRoleDetection(RoleDetection roleDetection) {
        List<TargetAccountDTO> targetAccountDTOs = new ArrayList<>();


        if (roleDetection != null) {
            String roleName = roleDetection.getRoleName();
            for (RoleTarget roleTarget : Util.iterate(roleDetection.getTargets())) {
                targetAccountDTOs.add(getTargetAccountDTO(roleName, roleTarget));
            }
        }

        return targetAccountDTOs;
    }

    /**
     * Get a list of TargetAccountDTO that are part of the RoleAssignment
     * @param roleAssignment RoleAssignment object
     * @return List of TargetAcccountDTO objects
     * @throws GeneralException
     */
    private List<TargetAccountDTO> getTargetsForRoleAssignment(RoleAssignment roleAssignment) {
        List<TargetAccountDTO> targetAccountDTOs = new ArrayList<>();

        if (null != roleAssignment) {
            String roleName = roleAssignment.getRoleName();
            for (RoleTarget roleTarget : Util.iterate(roleAssignment.getTargets())) {
                targetAccountDTOs.add(getTargetAccountDTO(roleName, roleTarget));
            }
        }

        return targetAccountDTOs;
    }

    private TargetAccountDTO getTargetAccountDTO(String roleName, RoleTarget roleTarget) {
        TargetAccountDTO dto = new TargetAccountDTO();
        dto.setValue(roleName);
        dto.setApplication(roleTarget.getApplicationName());
        dto.setInstance(roleTarget.getInstance());
        dto.setNativeIdentity(roleTarget.getNativeIdentity());
        dto.setAccount(roleTarget.getDisplayableName());
        dto.setSourceRole(roleTarget.getRoleName());
        return dto;
    }
    
    private Identity getIdentity() throws GeneralException {
        Identity identity = this.context.getObjectById(Identity.class, this.identityId);
        if (identity == null) {
            throw new GeneralException("Unable to find identity " + this.identityId);
        }
        
        return identity;
    }
}
