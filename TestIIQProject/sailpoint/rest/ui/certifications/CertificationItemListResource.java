/* (c) Copyright 2016 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.rest.ui.certifications;

import java.util.List;
import java.util.Map;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;

import sailpoint.authorization.CertificationAuthorizer;
import sailpoint.integration.ListResult;
import sailpoint.object.Certification;
import sailpoint.object.UIConfig;
import sailpoint.rest.BaseListResource;
import sailpoint.rest.BaseResource;
import sailpoint.rest.ui.Paths;
import sailpoint.service.suggest.BaseSuggestAuthorizerContext;
import sailpoint.service.certification.CertificationItemListFilterContext;
import sailpoint.service.certification.CertificationItemListService;
import sailpoint.service.certification.CertificationItemListServiceContext;
import sailpoint.service.certification.ObjectCertItemListFilterContext;
import sailpoint.service.certification.IdentityCertItemListFilterContext;
import sailpoint.service.certification.RoleCompositionItemListFilterContext;
import sailpoint.service.listfilter.ListFilterDTO;
import sailpoint.service.listfilter.ListFilterService;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

/**
 * Resource to handle listing certification items.
 */
public class CertificationItemListResource extends BaseListResource implements CertificationItemListServiceContext {

    private Certification certification;
    private String entityId;
    private CertificationItemListFilterContext listFilterContext;

    /**
     * Constructor
     *
     * @param parent        Parent resource
     * @param certification Certification object.
     * @param entityId Entity Id (optional)
     */
    public CertificationItemListResource(BaseResource parent, Certification certification, String entityId) throws GeneralException {
        super(parent);

        if (certification == null) {
            throw new GeneralException("certification is required");
        }
        this.certification = certification;
        this.entityId = entityId;
        
        if (certification.getType().isObjectType()) {
            if (certification.getType() == Certification.Type.BusinessRoleComposition) {
                this.listFilterContext = new RoleCompositionItemListFilterContext(this.certification.getId());
            } 
            else {
                this.listFilterContext = new ObjectCertItemListFilterContext(this.certification.getId());
            }
        }
        else {           
            this.listFilterContext = new IdentityCertItemListFilterContext(this.certification.getId());
        }

        this.listFilterContext.setSuggestUrl(this.getMatchedUri() + "/" + Paths.SUGGEST);
    }

    /**
     * Get a ListResult representing certification items of specific summary status
     *
     * @return ListResult with CertificationItemDTO objects
     * @throws GeneralException
     */
    @GET
    public ListResult getCertificationItems(@QueryParam("colKey") String colKey) throws GeneralException {
        authorize(new CertificationAuthorizer(this.certification));

        // Use a default column config just in case, but we really don't expect colKey to be null.
        String key = Util.isNullOrEmpty(colKey) ? UIConfig.UI_CERTIFICATION_ITEM_WORKSHEET_COLUMNS : colKey;
        CertificationItemListService.CertificationItemListColumnSelector columnSelector =
                new CertificationItemListService.CertificationItemListColumnSelector(key, this.certification, getContext());

        setFilters();

        return new CertificationItemListService(this, this, columnSelector)
                .getCertificationItems(this.certification, this.entityId);
    }
    
    /**
     * Get the filter for these certification items
     * @return List of ListFilterDTO objects
     * @throws GeneralException
     */
    @GET
    @Path(Paths.FILTERS)
    public List<ListFilterDTO> getCertificationItemFilters() throws GeneralException {
        authorize(new CertificationAuthorizer(this.certification));

        return getListFilterService().getListFilters(true);
    }

    /**
     * Create a ListFilterService.
     */
    private ListFilterService getListFilterService() {
        return new ListFilterService(getContext(), getLocale(), this.listFilterContext);
    }

    /**
     * Overridden to also remove the parameters that are already handled when listing items.
     */
    @Override
    protected Map<String,String> getOtherQueryParams() {
        Map<String,String> params = super.getOtherQueryParams();
        params.remove("colKey");
        return params;
    }

    /**
     * Set the filter panel filters by using any query parameters that aren't being used for other things and
     * converting them into filters.
     */
    private void setFilters() throws GeneralException {
        Map<String,String> params = getOtherQueryParams();
        ListFilterService svc = getListFilterService();
        setFilters(svc.convertQueryParametersToFilters(params, true));
    }

    @Override
    public boolean isJoinedToIdentity() {
        return this.listFilterContext.isJoinedToIdentity();
    }

    @Override
    public Certification getCertification() {
        return this.certification;
    }

    /**
     * Pass through to the CertificationItemResource.
     *
     * @param itemId CertificationItem id
     * @return CertificationItemResource
     * @throws GeneralException
     */
    @Path("{itemId}")
    public CertificationItemResource getCertificationItemResource(@PathParam("itemId") String itemId) throws GeneralException {
        return new CertificationItemResource(this, this.certification, itemId);
    }

    @Path(Paths.SUGGEST)
    public CertificationItemSuggestResource getSuggestResource() throws GeneralException {
        return new CertificationItemSuggestResource(this.certification, new BaseSuggestAuthorizerContext(getCertificationItemFilters()), this);
    }
}
