/*
 * (c) Copyright 2019 SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.rest.ui.certifications;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;

import sailpoint.authorization.CertifiableItemAuthorizer;
import sailpoint.authorization.UnauthorizedAccessException;
import sailpoint.object.Certification;
import sailpoint.object.CertificationEntity;
import sailpoint.object.CertificationItem;
import sailpoint.object.ManagedAttribute;
import sailpoint.object.RoleSnapshot;
import sailpoint.rest.BaseResource;
import sailpoint.rest.ui.managedattribute.ManagedAttributeDetailResource;
import sailpoint.service.certification.RoleProfileHelper;
import sailpoint.service.identity.RoleProfileDTO;
import sailpoint.tools.GeneralException;
import sailpoint.tools.ObjectNotFoundException;

/**
 * Sub resource to get details for a role profile as well as chain to the managed attribute details
 * based on what is in the profile
 */
public class CertificationItemProfileResource extends BaseResource {

    private String itemId;
    private Certification certification;

    public CertificationItemProfileResource(BaseResource baseResource, Certification certification, String itemId) throws GeneralException {
        super(baseResource);
        if (certification == null) {
            throw new GeneralException("certification is required");
        }
        this.certification = certification;
        this.itemId = itemId;
    }

    /**
     * Returns the RoleProfile details for the given CertificationItem
     * @return the RoleProfile details for the given CertificationItem
     * @throws GeneralException If unable to get a ProfileSnap to get details from
     */
    @GET
    public RoleProfileDTO getRoleProfileDTO() throws GeneralException {
        CertificationItem certificationItem = getCertificationItem();
        CertificationEntity certificationEntity = certificationItem.getParent();
        RoleSnapshot roleSnapshot = certificationEntity.getRoleSnapshot();
        RoleSnapshot.ProfileSnapshot profile = null;
        if(roleSnapshot != null ) {
            profile = roleSnapshot.getProfileSnapshot(certificationItem.getTargetId());
        }
        if(profile == null) {
            throw new GeneralException("No profile snapshot found for " + certificationItem.getTargetId());
        }
        RoleProfileHelper helper = new RoleProfileHelper(profile, getContext(), getLocale(), getUserTimeZone());
        return helper.getRoleProfileDTO(roleSnapshot);
    }

    @Path("managedAttributeDetails/{managedAttributeId}")
    public ManagedAttributeDetailResource getManagedAttributeDetails(@PathParam("managedAttributeId") String manageAttributeId)
            throws GeneralException {

        // We don't need the cert item to generate results but still need to verify it exists and we're authorized
        // to see it.
        getCertificationItem();

        ManagedAttribute managedAttribute = getContext().getObjectById(ManagedAttribute.class, manageAttributeId);
        if (managedAttribute == null) {
            throw new ObjectNotFoundException(ManagedAttribute.class, manageAttributeId);
        }

        // Only allow for managed attributes that are part of simple entitlements of this profile
        RoleProfileDTO profileDTO = getRoleProfileDTO();
        boolean existsInProfile = profileDTO.getEntitlements() != null &&
                profileDTO.getEntitlements().stream().anyMatch(ent -> ent.getManagedAttributeId() != null && ent.getManagedAttributeId().equals(manageAttributeId));

        if (!existsInProfile) {
            throw new UnauthorizedAccessException("Profile does not grant access to this managed attribute");
        }

        return new ManagedAttributeDetailResource(managedAttribute, this);
    }

    private CertificationItem getCertificationItem() throws GeneralException {
        CertificationItem item = getContext().getObjectById(CertificationItem.class, this.itemId);
        if (item == null) {
            throw new ObjectNotFoundException(CertificationItem.class, this.itemId);
        }

        authorize(new CertifiableItemAuthorizer(this.certification, item));

        return item;
    }
}
