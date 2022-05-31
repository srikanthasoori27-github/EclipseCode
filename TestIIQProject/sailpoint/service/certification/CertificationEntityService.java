package sailpoint.service.certification;

import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.Bundle;
import sailpoint.object.Certification;
import sailpoint.object.CertificationEntity;
import sailpoint.object.Identity;
import sailpoint.object.IdentitySnapshot;
import sailpoint.object.ManagedAttribute;
import sailpoint.object.ObjectConfig;
import sailpoint.object.ScoreConfig;
import sailpoint.object.ScoreDefinition;
import sailpoint.object.Scorecard;
import sailpoint.object.UIConfig;
import sailpoint.service.IdentityAttributesDTO;
import sailpoint.service.IdentityDetailsService;
import sailpoint.service.RoleSnapshotDTO;
import sailpoint.service.ScorecardDTO;
import sailpoint.tools.GeneralException;
import sailpoint.tools.InvalidParameterException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.UserContext;
import sailpoint.web.certification.CertificationUtil;
import sailpoint.web.messages.MessageKeys;

/**
 * Service for certification entity
 *
 */

public class CertificationEntityService {

    private static final Log log = LogFactory.getLog(CertificationEntityService.class);

    private Certification certification;
    private UserContext userContext;


    /**
     * Constructor
     *
     * @param certification Certification object
     * @param userContext UserContext
     * @throws GeneralException
     */
    public CertificationEntityService(
        Certification certification,
        UserContext userContext) throws GeneralException {

        if (certification == null) {
            throw new InvalidParameterException("certification required");
        }
        this.certification = certification;
        this.userContext = userContext;
    }

    /**
     * Get the CertificationEntityDTO
     * @param entity CertificationEntity
     * @return CertificationEntityDTO
     */
    public CertificationEntityDTO getCertificationEntityDTO(CertificationEntity entity) throws GeneralException {
        if (entity == null) {
            throw new InvalidParameterException("Certification entity required");
        }
        CertificationEntityDTO entityDTO = new CertificationEntityDTO(entity);

        // Add extra information to the DTO.
        addItemStatusCount(entityDTO, entity);
        addIdentityAttributes(entityDTO, entity);
        addScorecard(entityDTO, entity);
        addDifferences(entityDTO, entity);
        addNativeIdentity(entityDTO, entity);
        addTypeSpecificProperties(entityDTO);

        return entityDTO;
    }

    /**
     * Add type-specific properties to Certification entity DTO
     * @param entityDTO Certification entity DTO
     * @throws GeneralException
     */
    public void addTypeSpecificProperties (CertificationEntityDTO entityDTO) throws GeneralException {
        if (entityDTO != null) {
            Certification.Type certType = this.certification.getType();

            switch (certType) {
                case DataOwner:
                    addEntitlementProperties(entityDTO);
                    break;
                case BusinessRoleComposition:
                    addRoleCompositionProperties(entityDTO);
                    break;
                case AccountGroupMembership:
                case AccountGroupPermissions:
                    addAccountGroupProperties(entityDTO);
                    break;
                case Manager:
                case ApplicationOwner:
                case BusinessRoleMembership:
                case Identity:
                case Group:
                case Focused:
                    addIdentityProperties(entityDTO);
            }
        }
    }

    /**
     * Adds role composition specific attributes to the dto
     * @param entityDTO The certification entity to update
     * @throws GeneralException If not able to get certification entity
     */
    private void addRoleCompositionProperties(CertificationEntityDTO entityDTO) throws GeneralException {
        CertificationEntity certificationEntity = userContext.getContext().getObjectById(CertificationEntity.class, entityDTO.getId());
        if (certificationEntity != null && certificationEntity.getRoleSnapshot() != null) {
            entityDTO.setDescription(certificationEntity.getRoleSnapshot().getObjectDescription(userContext.getLocale()));
        }
    }

    /**
     * Add entitlement properties to Certification entity DTO
     * @param entityDTO Certification entity DTO
     * @throws GeneralException
     */
    private void addEntitlementProperties (CertificationEntityDTO entityDTO) throws GeneralException {
        String targetId = entityDTO.getTargetId();
        ManagedAttribute managedAttribute = userContext.getContext().getObjectById(ManagedAttribute.class, targetId);
        if (managedAttribute == null) {
            if (log.isDebugEnabled()) {
                log.debug("ManagedAttribute with ID " + entityDTO.getTargetId() + " does not exist");
            }
            // Pre-8.1 we didnt store the actual displayable value anywhere for Data Owner certs (why?!), so the best
            // we could do was the value, which is in the nativeIdentity field. Now we store it, but keep this fallback
            // so there is some displayable name in all cases.
            if (Util.isNullOrEmpty(entityDTO.getDisplayableName())) {
                entityDTO.setDisplayableName(entityDTO.getNativeIdentity());
            }
        } else {
            if (managedAttribute.isPermission()) {
                CertificationEntity entity = userContext.getContext().getObjectById(CertificationEntity.class, entityDTO.getId());
                entityDTO.setPermission(entity.getNativeIdentity());
            }
            entityDTO.setDescription(managedAttribute.getDescription(userContext.getLocale()));

            // For permissions the displayable name is not sufficient by itself, we need the create/update etc
            // parts of the full name, so use a similar construction as cert item display names.
            String displayableName = managedAttribute.getDisplayableName();
            if (managedAttribute.isPermission() && entityDTO.getNativeIdentity() != null) {
                Message displayNameMsg = new Message(MessageKeys.UI_CERT_DATA_OWNER_ENTITLEMENT_NAME, entityDTO.getPermission(), displayableName);
                displayableName = displayNameMsg.getLocalizedMessage();
            }

            entityDTO.setDisplayableName(displayableName);
            entityDTO.setAttribute(managedAttribute.getAttribute());
        }
    }

    /**
     * Add account group properties to Certification entity DTO
     * @param entityDTO Certification entity DTO
     * @throws GeneralException
     */
    private void addAccountGroupProperties(CertificationEntityDTO entityDTO) throws GeneralException {
        String targetId = entityDTO.getTargetId();
        ManagedAttribute managedAttribute = userContext.getContext().getObjectById(ManagedAttribute.class, targetId);
        if (managedAttribute == null) {
            if (log.isDebugEnabled()) {
                log.debug("ManagedAttribute with ID " + entityDTO.getTargetId() + " does not exist");
            }
        } else {
            entityDTO.setDescription(managedAttribute.getDescription(userContext.getLocale()));
        }

        entityDTO.setDisplayableName(entityDTO.getAccountGroup());
    }

    /**
     * Add identity properties to Certification entity DTO
     * @param entityDTO Certification entity DTO
     * @throws GeneralException
     */
    private void addIdentityProperties(CertificationEntityDTO entityDTO) throws GeneralException {
        String targetId = entityDTO.getTargetId();
        Identity identity = userContext.getContext().getObjectById(Identity.class, targetId);
        if (identity != null) {
            entityDTO.setEmail(identity.getEmail());
            entityDTO.setIdentityName(identity.getName());
        }
        else {
            entityDTO.setIdentityName(entityDTO.getDisplayableName());
        }
    }

    /**
     * Add the item status counts to the given DTO.
     */
    private void addItemStatusCount(CertificationEntityDTO dto, CertificationEntity entity) throws GeneralException {
        dto.setItemStatusCount(CertificationUtil.getItemStatusCount(userContext, certification, entity));
    }

    /**
     * Add the identity attributes to the given DTO.
     */
    private void addIdentityAttributes(CertificationEntityDTO dto, CertificationEntity entity) throws GeneralException {
        Identity id = entity.getIdentity(this.userContext.getContext());
        if (null != id) {
            List<String> attrs = UIConfig.getUIConfig().getIdentityViewAttributesList();
            IdentityDetailsService svc = new IdentityDetailsService(id);
            IdentityAttributesDTO details = svc.getIdentityAttributesDTO(userContext.getLocale(), userContext.getUserTimeZone(), attrs);
            dto.setIdentityAttributes(details);
        }
    }

    /**
     * Add the scorecard to the given DTO.
     */
    private void addScorecard(CertificationEntityDTO dto, CertificationEntity entity) throws GeneralException {
        dto.setScorecard(this.getScorecard(entity));
    }

    /**
     * Add the differences to the given DTO.
     */
    private void addDifferences(CertificationEntityDTO dto, CertificationEntity entity) {
        dto.setDifferences(entity.getDifferences());
    }

    /**
     * Add the native identity to the given DTO.
     */
    private void addNativeIdentity(CertificationEntityDTO dto, CertificationEntity entity) {
        dto.setNativeIdentity(entity.getNativeIdentity());
    }

    /**
     * Return the scorecard for the given entity.
     *
     * @param  entity  The CertificationEntity for which to build the scorecard.
     *
     * @return The scorecard for the given entity, or null if the entity does not support scores (ie - not an identity).
     */
    public ScorecardDTO getScorecard(CertificationEntity entity) throws GeneralException {
        ScorecardDTO dto = null;

        IdentitySnapshot identitySnapshot = entity.getIdentitySnapshot(this.userContext.getContext());
        if (identitySnapshot != null) {
            Scorecard card = identitySnapshot.getScorecard();
            if (card != null) {
                List<ScoreDefinition> scores = null;
                ScoreConfig config = this.userContext.getContext().getObjectByName(ScoreConfig.class, ScoreConfig.OBJ_NAME);
                if (config != null) {
                    scores = config.getIdentityScores();
                }
                dto = new ScorecardDTO(card, scores, this.userContext.getLocale(), this.userContext.getUserTimeZone());
            }
        }
        return dto;
    }

    /**
     * Get the role snapshot DTO
     * @param entity CertificationEntity
     * @return RoleSnapshotDTO
     */
    public RoleSnapshotDTO getRoleSnapshotDTO(CertificationEntity entity) throws GeneralException {
        if (entity == null) {
            throw new InvalidParameterException("Certification entity required");
        }
        if (entity.getRoleSnapshot() == null) {
            throw new GeneralException("Role snapshot should not be null");
        }
        ObjectConfig roleConfig = this.userContext.getContext().getObjectByName(ObjectConfig.class, Bundle.class.getSimpleName());
        RoleSnapshotDTO roleSnapshotDTO = new RoleSnapshotDTO(entity.getRoleSnapshot(), this.userContext, roleConfig);
        return roleSnapshotDTO;
    }
}
