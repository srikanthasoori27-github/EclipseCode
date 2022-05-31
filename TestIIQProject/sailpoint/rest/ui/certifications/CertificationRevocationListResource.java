/*
 * (c) Copyright 2016. SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.rest.ui.certifications;

import javax.ws.rs.GET;

import sailpoint.integration.ListResult;
import sailpoint.object.Certification;
import sailpoint.rest.BaseListResource;
import sailpoint.rest.BaseResource;
import sailpoint.service.BaseListServiceContext;
import sailpoint.service.certification.CertificationRevocationService;
import sailpoint.tools.GeneralException;

/**
 * Rest resource for getting certification revocation detail info.
 *
 * @author patrick.jeong
 */
public class CertificationRevocationListResource extends BaseListResource implements BaseListServiceContext {

    private Certification certification;

    /**
     * Constructor
     *
     * @param parent Parent resource
     * @param certification Certification
     */
    public CertificationRevocationListResource(BaseResource parent, Certification certification)
            throws GeneralException {
        super(parent);
        if (certification == null) {
            throw new GeneralException("Certification is required");
        }
        this.certification = certification;
    }

    /**
     * Get certification revocations
     *
     * @return ListResult of certification revocations
     * @throws GeneralException
     */
    @GET
    public ListResult getCertificationRevocations() throws GeneralException {
        return new CertificationRevocationService(certification, getContext(), this).getCertificationRevocations();
    }
}
