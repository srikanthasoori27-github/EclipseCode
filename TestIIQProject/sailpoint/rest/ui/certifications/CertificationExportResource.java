/* (c) Copyright 2016 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.rest.ui.certifications;

import sailpoint.authorization.CertificationAuthorizer;
import sailpoint.object.Certification;
import sailpoint.object.Filter;
import sailpoint.rest.BaseListResource;
import sailpoint.rest.BaseResource;
import sailpoint.service.certification.CertificationItemListService;
import sailpoint.service.certification.CertificationItemListServiceContext;
import sailpoint.tools.GeneralException;
import sailpoint.tools.InvalidParameterException;

import javax.ws.rs.GET;
import javax.ws.rs.core.Response;
import java.util.List;

/**
 * Resource to export certification. This should really be in CertificationItemListResource, but we need to be
 * able to ignore the path for CSRF validation, so we need an endpoint on the root of CertificationListResource.
 * 
 */
public class CertificationExportResource extends BaseListResource implements CertificationItemListServiceContext {

    private Certification certification;

    public CertificationExportResource(BaseResource baseResource, Certification certification) throws GeneralException {
        super(baseResource);
        if (certification == null) {
            throw new InvalidParameterException("certification");
        }
        
        this.certification = certification;
    }

    /**
     * GET REST endpoint to trigger certification export.
     *
     * @return Response for browser to trigger csv download
     * @throws GeneralException
     */
    @GET
    public Response exportCSV() throws GeneralException {
        authorize(new CertificationAuthorizer(this.certification));
        
        String itemsCSV = new CertificationItemListService(this, this, null).exportCertification(this.certification);

        // Firefox doesn't like spaces in the filename
        //IIQTC-70: Chrome has issues with commas in the filename
        String fileName = this.certification.getName().isEmpty() ? "certification" :
                this.certification.getName().replace(' ', '_')
                .replace(',', '_');

        return Response.ok(itemsCSV).header("Content-Disposition", "attachment; filename=" + fileName + ".csv;")
                .header("Content-Type", "text/csv")
                .header("Cache-control", "must-revalidate, post-check=0, pre-check=0")
                .build();
    }

    @Override
    public List<Filter> getFilters() {
        return null;
    }

    @Override
    public boolean isJoinedToIdentity() {
        return false;
    }

    @Override
    public Certification getCertification() {
        return this.certification;
    }


}
