package sailpoint.rest.ui.certifications;

import javax.ws.rs.GET;
import javax.ws.rs.Path;

import sailpoint.authorization.CertifiableItemAuthorizer;
import sailpoint.authorization.UnauthorizedAccessException;
import sailpoint.object.Certification;
import sailpoint.object.CertificationDefinition;
import sailpoint.object.CertificationEntity;
import sailpoint.object.ManagedAttribute;
import sailpoint.rest.BaseResource;
import sailpoint.rest.ui.identities.RoleDetailResource;
import sailpoint.rest.ui.managedattribute.ManagedAttributeDetailResource;
import sailpoint.service.RoleSnapshotDTO;
import sailpoint.service.certification.CertificationEntityDTO;
import sailpoint.service.certification.CertificationEntityService;
import sailpoint.tools.GeneralException;
import sailpoint.tools.ObjectNotFoundException;
import sailpoint.tools.Util;

public class CertificationEntityResource extends BaseResource {

    private String entityId;
    private Certification certification;

    /**
     * Constructor
     *
     * @param parent The parent of this resource.
     * @param certification Certification
     * @param entityId Entity Id
     */
    public CertificationEntityResource(BaseResource parent, Certification certification, String entityId) {
        super(parent);
        this.entityId = entityId;
        this.certification = certification;
    }

    /**
     * Get the CertificationItemListResource for certification items
     * @return CertificationItemListResource
     * @throws GeneralException
     */
    @Path("items")
    public CertificationItemListResource getCertificationItems() throws GeneralException {
        return new CertificationItemListResource(this, this.certification, this.entityId);
    }

    /**
    * Get CertificationEntityDTO
    * @return CertificationEntityDTO
    * @throws GeneralException
    */
    @GET
    public CertificationEntityDTO getCertificationEntity() throws GeneralException {

        CertificationEntity entity = getEntity();

        return new CertificationEntityService(certification, this).getCertificationEntityDTO(entity);
    }

    /** 
     * Gets the managed attribute details represented by the certification entity.  
     * Passes through to the ManagedAttributeDetailsResource. 
     * @return ManagedAttributeDetailResource whose main getter will return a ManagedAttributeDetailDTO 
     * @throws GeneralException 
     */  
    @Path("managedAttributeDetails")  
    public ManagedAttributeDetailResource getManagedAttributeDetails() throws GeneralException {
        CertificationEntity entity = getEntity();

        // Only account group and entitlement owner related entities would have managed attribute details.
        if (!CertificationEntity.Type.AccountGroup.equals(entity.getType()) &&
                !CertificationEntity.Type.DataOwner.equals(entity.getType())) {
            throw new UnauthorizedAccessException("Entity does not have managed attribute details");
        }
        String targetId = entity.getTargetId();
        if (Util.isNothing(targetId)) {
            throw new GeneralException("Entity does not have valid target ID " + targetId);
        }

        ManagedAttribute managedAttribute = getContext().getObjectById(ManagedAttribute.class, targetId);
        if (managedAttribute == null) {
            throw new ObjectNotFoundException(ManagedAttribute.class, targetId);
        }

        return new ManagedAttributeDetailResource(managedAttribute, this);
    }

    /**
     * Pass through to the RoleDetailResource. Will throw if this entity is not a role type.
     * @return RoleDetailResource
     * @throws GeneralException
     */
    @Path("roleDetails")
    public RoleDetailResource getRoleDetails() throws GeneralException {
        CertificationEntity entity = getEntity();

        if (!CertificationEntity.Type.BusinessRole.equals(entity.getType())) {
            throw new UnauthorizedAccessException("Entity does not have role details");
        }
        String targetId = entity.getTargetId();
        if (Util.isNothing(targetId)) {
            throw new GeneralException("Entity does not have valid target ID " + targetId);
        }

        return new RoleDetailResource(targetId, null, null, isClassificationsEnabled(entity), this);
    }

    /** 
     * Gets the role snapshot details represented by the certification entity.  
     * Delegate to CertificationEntityService 
     * @return RoleSnapshotDTO 
     * @throws GeneralException 
     */
    @GET
    @Path("roleSnapshotDetails")  
    public RoleSnapshotDTO getRoleSnapshotDetails() throws GeneralException {
        CertificationEntity entity = getEntity();

        RoleSnapshotDTO snapshotDTO = new CertificationEntityService(certification, this).getRoleSnapshotDTO(entity);
        return snapshotDTO;
    }

    /**
     * Looks up the entity and authorizes access.
     * @return the certification entity
     * @throws GeneralException
     */
    private CertificationEntity getEntity() throws GeneralException {
        CertificationEntity entity = getContext().getObjectById(CertificationEntity.class, this.entityId);
        if (entity == null) {
            throw new ObjectNotFoundException(CertificationEntity.class, this.entityId);
        }

        authorize(new CertifiableItemAuthorizer(this.certification, entity));

        return entity;
    }

    private boolean isClassificationsEnabled(CertificationEntity entity) throws GeneralException {
        Certification certification = entity.getCertification();
        if (certification == null) {
            throw new GeneralException("CertificationEntity found with no associated certification");
        }

        CertificationDefinition definition = certification.getCertificationDefinition(getContext());
        if (definition == null) {
            throw new GeneralException("Certification found with no associated definition");
        }

        return definition.isIncludeClassifications();
    }
}
