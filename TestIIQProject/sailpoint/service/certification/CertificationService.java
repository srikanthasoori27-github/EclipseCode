/*
 * (c) Copyright 2016. SailPoint Technologies, Inc., All Rights Reserved.
 */
package sailpoint.service.certification;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import sailpoint.api.Certificationer;
import sailpoint.api.ObjectAlreadyLockedException;
import sailpoint.api.ObjectUtil;
import sailpoint.api.SailPointContext;
import sailpoint.api.certification.CertificationDecisioner.Decision;
import sailpoint.api.certification.RemediationAdvisor;
import sailpoint.api.certification.RemediationAdvisor.PermittedRoles;
import sailpoint.connector.ExpiredPasswordException;
import sailpoint.integration.ObjectResult;
import sailpoint.object.Bundle;
import sailpoint.object.Certification;
import sailpoint.object.CertificationAction;
import sailpoint.object.CertificationItem;
import sailpoint.object.ElectronicSignature;
import sailpoint.tools.GeneralException;
import sailpoint.tools.InvalidParameterException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.UserContext;
import sailpoint.web.messages.MessageKeys;

/**
 * Service for certifications
 *
 * @author patrick.jeong
 */
public class CertificationService {

    private Certification certification;
    private UserContext userContext;

    /**
     * Constructor
     *
     * @param certification Certification object
     * @param userContext UserContext
     * @throws GeneralException
     */
    public CertificationService(Certification certification, UserContext userContext) throws GeneralException {
        if (certification == null) {
            throw new InvalidParameterException("certification");
        }
        initContext(userContext);
        this.certification = certification;
    }

    /**
     * Initialize the context
     *
     * @param userContext UserContext
     * @throws GeneralException
     */
    private void initContext(UserContext userContext) throws GeneralException {
        if (userContext == null) {
            throw new InvalidParameterException("userContext");
        }
        this.userContext = userContext;
    }

    private SailPointContext getContext() {
        return this.userContext.getContext();
    }

    /**
     * Get the certification dto
     *
     * @return ObjectResult containing CertificationDTO
     */
    public ObjectResult getCertificationDTO() throws GeneralException {
        ObjectResult result = new ObjectResult(new CertificationDTO(certification, userContext));
        if (Certification.isLockedAndActionable(userContext.getContext(), certification.getId())) {
            // set the attributes map with a property to indicate that the cert is locked
            Map<String, Object> attributes = new HashMap<String, Object>();
            attributes.put("certLocked", true);
            result.setAttributes(attributes);
        }
        return result;
    }

    /**
     * @return CertificationConfigDTO object
     * @throws GeneralException
     */
    public CertificationConfigDTO getConfig() throws GeneralException {
        return new CertificationConfigDTO(certification, userContext);
    }

    /**
     * Sign the certification, with an optional electronic signature. This will decache.   
     * @param signature ElectronicSignature object. Optional.
     * @return ObjectResult containing updated CertificationDTO.
     * @throws GeneralException
     */
    public ObjectResult sign(ElectronicSignature signature)
            throws GeneralException, ExpiredPasswordException {
        Certificationer certificationer = new Certificationer(getContext());
        String signatureAccountId = (signature == null) ? null : signature.getAccountId();
        String signaturePassword = (signature == null) ? null : signature.getPassword();
        List<Message> messages = certificationer.sign(
                this.certification, 
                this.userContext.getLoggedInUser(), 
                true, 
                signatureAccountId, 
                signaturePassword, 
                this.userContext.getLocale());
        
        // Refetch certification after signing
        getContext().decache();
        this.certification = getContext().getObjectById(Certification.class, this.certification.getId());
        ObjectResult result = getCertificationDTO();
        if (!Util.isEmpty(messages)) {
            for (Message message : messages) {
                result.addError(message.getLocalizedMessage(this.userContext.getLocale(), this.userContext.getUserTimeZone()));
            }
        } else {
            result.setStatus(ObjectResult.STATUS_SUCCESS);
        }
        
        return result;
    }

    /**
     * Check the decision object for any missing information and update it accordingly.
     * 
     * @param  decision Decision The decision object
     * @return Decision The updated decision object
     */
    public Decision checkDecision(Decision decision)
        throws GeneralException {

        // for Remediated decisions check for any missing revoked roles
        if (CertificationAction.Status.Remediated.name().equals(decision.getStatus()) &&
                (decision.getRevokedRoles() == null || decision.getRevokedRoles().isEmpty())) {
            
            // If it is a bulk decision, nothing to do.
            String certificationItemId = decision.getSelectionCriteria().isBulk() ? null : decision.getSelectionCriteria().getSelections().get(0);
            if (certificationItemId != null) {
                CertificationItem item = getContext().getObjectById(CertificationItem.class, certificationItemId);

                PermittedRoles roles = getRevokablePermittedRoles(item);
                if (roles != null) {
                    decision.setRevokedRoles(getRequiredOrPermittedRolesNames(roles));
                }
            }
        }
        return decision;
    }

    /**
     * Action to rescind the given certification back to its parent.
     *
     * @return boolean true if successful, false if parent is null.
     * @throws GeneralException - Throws a ModifyImmutableException if cert or parent is locked.
     */
    public boolean rescindChildCertification() throws GeneralException {

        Certification parent = this.certification.getParent();

        // Must have a parent cert in order to rescind it. Duh.
        if (parent != null) {
            boolean certLocked = ObjectUtil.isLockedById(getContext(), Certification.class, this.certification.getId());
            boolean parentLocked = ObjectUtil.isLockedById(getContext(), Certification.class, parent.getId());
            if (!certLocked && !parentLocked) {
                Certificationer certificationer = new Certificationer(getContext());
                certificationer.rescindChildCertification(this.certification, false, false);
                return true;
            }
            throw new ObjectAlreadyLockedException(new Message(MessageKeys.CERT_LOCKED_RESCIND));
        }

        return false;
    }

    /**
     * Gets the revokable permitted roles for the item.
     *
     * @param item The item.
     * @return The permitted roles or null if the item does not have a subtype of AssignedRoles.
     * @throws GeneralException
     */
    private PermittedRoles getRevokablePermittedRoles(CertificationItem item) throws GeneralException {
        if (!CertificationItem.SubType.AssignedRole.equals(item.getSubType())) {
            return null;
        }

        RemediationAdvisor advisor = new RemediationAdvisor(getContext());

        return advisor.getRevokablePermittedRoles(item);
    }

    /**
     * Gets the names of all of the non-filtered roles.
     *
     * @param roles The permitted roles.
     * @return The role names.
     */
    private List<String> getRequiredOrPermittedRolesNames(PermittedRoles roles) {
        List<String> rolesNames = new ArrayList<>();
        if (roles != null) {
            for (Bundle role : Util.iterate(roles.getAllRoles())) {
                rolesNames.add(role.getName());
            }
        }

        return rolesNames;
    }

    /**
     * Return the names of required or permitted roles for the given item (if a bundle) that can also be removed
     * with it.
     */
    public List<String> getRequiredOrPermittedRolesNames(CertificationItem item) throws GeneralException {
        return getRequiredOrPermittedRolesNames(getRevokablePermittedRoles(item));
    }
}
