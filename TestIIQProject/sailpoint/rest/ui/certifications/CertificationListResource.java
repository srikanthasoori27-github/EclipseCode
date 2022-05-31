/* (c) Copyright 2015 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.rest.ui.certifications;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;

import sailpoint.authorization.CertificationAuthorizer;
import sailpoint.integration.ListResult;
import sailpoint.object.Certification;
import sailpoint.object.UIConfig;
import sailpoint.rest.BaseListResource;
import sailpoint.rest.ui.widgets.WidgetDataResource;
import sailpoint.service.BaseListResourceColumnSelector;
import sailpoint.service.BaseListServiceContext;
import sailpoint.service.certification.CertificationListService;
import sailpoint.tools.GeneralException;
import sailpoint.tools.ObjectNotFoundException;

/**
 * A resource used for managing certifications.
 *
 * @author Dustin Dobervich <dustin.dobervich@sailpoint.com>
 */
@Path("certifications")
public class CertificationListResource extends BaseListResource implements BaseListServiceContext {

    private BaseListResourceColumnSelector certListResourceSelector =
            new BaseListResourceColumnSelector(UIConfig.UI_CERTIFICATION_CARD_COLUMNS);
    /**
     * Get the list of certifications for the logged in user.
     * @param showSigned When true, esigned certifications are included in the results. Defaults to false.
     * @return ListResult with CertificationDTO for all access reviews where logged in user is certifier.
     * @throws GeneralException
     */
    @GET
    public ListResult getCertifications(@QueryParam("showSigned") boolean showSigned) throws GeneralException {
        CertificationListService certificationListService =
                new CertificationListService(this, this, certListResourceSelector);
        return certificationListService.getCurrentCertifications(isMobileLogin(), showSigned);
    }
    
    /**
     * Gets the data for the My Access Reviews widget.
     *
     * @return The list result.
     */
    @GET
    @Path("widgets/myAccessReviews")
    public ListResult getMyAccessReviewsWidgetData() throws GeneralException {
        return new WidgetDataResource(this).getMyAccessReviews();
    }

    /**
     * Gets the data for the Certification Campaigns widget.
     *
     * @return The list result.
     */
    @GET
    @Path("widgets/certificationCampaigns")
    public ListResult getCertificationCampaignsWidgetData() throws GeneralException {
        return new WidgetDataResource(this).getCertificationCampaigns();
    }

    /**
     * Return certification resource for certification
     *
     * @param certificationId ID of the certification
     * @return CertificationResource
     * @throws GeneralException
     */
    @Path("{certificationId}")
    public CertificationResource getCertification(@PathParam("certificationId") String certificationId) throws GeneralException {
        return new CertificationResource(this, certificationId);
    }

    /**
     * Return certification resource for certification
     *
     * @param certificationId ID of the certification
     * @return list of sub certs (forwarded or subordinate)
     * @throws GeneralException
     */
    @GET
    @Path("{certificationId}/subCertifications")
    public ListResult getSubCertifications(@PathParam("certificationId") String certificationId) throws GeneralException {
        // This will authorize
        loadCertification(certificationId);
        CertificationListService certificationListService =
                new CertificationListService(this, this, certListResourceSelector);
        return certificationListService.getSubCertifications(certificationId);
    }

    /**
     * Get the CertificationExportResource for exporting.
     * NOTE: We need this endpoint on the root of /certifications so we can easily exclude it from CSRF validation 
     *
     * @param certificationId the id of the certification to export
     * @return CertificationExportResource
     * @throws GeneralException
     */
    @Path("export/{certificationId}")
    public CertificationExportResource getExportResource(@PathParam("certificationId") String certificationId) throws GeneralException {
        Certification certification = loadCertification(certificationId);
        return new CertificationExportResource(this, certification);
    }

    private Certification loadCertification(String certificationId) throws GeneralException {
        Certification certification = getContext().getObjectById(Certification.class, certificationId);
        if (certification == null) {
            throw new ObjectNotFoundException(Certification.class, certificationId);
        }

        authorize(new CertificationAuthorizer(certification));
        return certification;
    }
    
}
