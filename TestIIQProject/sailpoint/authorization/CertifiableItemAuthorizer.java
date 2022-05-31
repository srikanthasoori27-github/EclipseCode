/*
 * (c) Copyright 2020 SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.authorization;

import sailpoint.object.AbstractCertificationItem;
import sailpoint.object.Certification;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.UserContext;
import sailpoint.web.messages.MessageKeys;

/**
 * This authorizer is used to authorize access to a CertificationItem or CertificationEntity, assuming that access
 * to the certification itself has already been authorized.
 */
public class CertifiableItemAuthorizer implements Authorizer {

    Certification certification;
    AbstractCertificationItem certifiableItem;

    /**
     * Constructor
     * @param certification Required. The certification
     * @param certifiableItem Required. The certification item or entity.
     */
    public CertifiableItemAuthorizer(Certification certification, AbstractCertificationItem certifiableItem)
        throws GeneralException {
        if (certification == null || certifiableItem == null) {
            throw new GeneralException("Certification and item are required.");
        }
        this.certification = certification;
        this.certifiableItem = certifiableItem;
    }

    @Override
    public void authorize(UserContext userContext) throws GeneralException {
        String certId = this.certification != null ? this.certification.getId() : null;
        String itemCertId = this.certifiableItem.getCertification() != null ? this.certifiableItem.getCertification().getId() : null;
        if (!Util.nullSafeEq(certId, itemCertId)) {
            throw new UnauthorizedAccessException(new Message(MessageKeys.CERT_UNAUTHORIZED_ACCESS_EXCEPTION));
        }
    }
}
