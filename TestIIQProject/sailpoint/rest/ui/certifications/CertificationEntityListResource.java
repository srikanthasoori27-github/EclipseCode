/*
 * (c) Copyright 2016. SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.rest.ui.certifications;

import java.util.List;
import java.util.Map;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.authorization.CertificationAuthorizer;
import sailpoint.integration.ListResult;
import sailpoint.object.AbstractCertificationItem;
import sailpoint.object.AbstractCertificationItem.Status;
import sailpoint.object.Certification;
import sailpoint.object.CertificationDefinition;
import sailpoint.object.UIConfig;
import sailpoint.rest.BaseListResource;
import sailpoint.rest.BaseResource;
import sailpoint.rest.ui.Paths;
import sailpoint.service.suggest.BaseSuggestAuthorizerContext;
import sailpoint.service.BaseListResourceColumnSelector;
import sailpoint.service.certification.CertificationEntityListFilterContext;
import sailpoint.service.certification.CertificationEntityListService;
import sailpoint.service.certification.CertificationEntityListServiceContext;
import sailpoint.service.certification.IdentityCertEntityListFilterContext;
import sailpoint.service.certification.ObjectCertEntityListFilterContext;
import sailpoint.service.certification.RoleCompositionEntityListFilterContext;
import sailpoint.service.listfilter.ListFilterDTO;
import sailpoint.service.listfilter.ListFilterService;
import sailpoint.tools.GeneralException;

public class CertificationEntityListResource
    extends BaseListResource
    implements CertificationEntityListServiceContext {

    private static Log log = LogFactory.getLog(CertificationEntityListResource.class);

    public static final String PARAM_STATUS = "status";
    public static final String PARAM_EXCLUDED_STATUS = "excludedStatus";
    public static final String PARAM_INCLUDED_STATISTICS = "includeStatistics";

    private Certification certification;

    // These hold query parameters used by the CertificationEntityListServiceContext.
    private List<Status> statuses;
    private List<Status> excludedStatuses;
    private boolean includeStatistics;

    // The entity list filter context appropriate for the certification type.
    private CertificationEntityListFilterContext listFilterContext;

    private static final BaseListResourceColumnSelector ENTITY_SELECTOR =
            new BaseListResourceColumnSelector(UIConfig.UI_CERTIFICATION_ENTITIES_COLUMNS);

    /**
     * Constructor
     *
     * @param parent Parent resource
     * @param certification Certification
     */
    public CertificationEntityListResource(BaseResource parent, Certification certification) throws GeneralException {
        super(parent);
        if (certification == null) {
            throw new GeneralException("Certification is required");
        }
        this.certification = certification;

        if (certification.getType().isObjectType()) {
            if (certification.getType() == Certification.Type.BusinessRoleComposition) {
                this.listFilterContext = new RoleCompositionEntityListFilterContext(this.certification.getId());
            }
            else {
                this.listFilterContext = new ObjectCertEntityListFilterContext(this.certification.getId());
            }
        }
        else {
            this.listFilterContext = new IdentityCertEntityListFilterContext(this.certification.getId());
        }

        this.listFilterContext.setSuggestUrl(getMatchedUri() + "/" + Paths.SUGGEST);

    }

    /**
     * Get certification entities
     *
     * @return ListResult of certification entities
     * @throws GeneralException
     */
    @GET
    public ListResult getCertificationEntities(@QueryParam("status") List<Status> statuses,
                                               @QueryParam("excludedStatus") List<Status> excludedStatuses,
                                               @QueryParam("includeStatistics") boolean includeStatistics)
        throws GeneralException {

        authorizeAndConfigureListContext(statuses, excludedStatuses);

        this.includeStatistics = includeStatistics;

        setFilters();

        return createEntityListService().getCertificationEntities();
    }

    /**
     * Check certification type and create certification entity list service using the correct column selector
     * @return CertificationEntityListService 
     */
    private CertificationEntityListService createEntityListService() {
        boolean isAutoApproveEnabled = false;
        try {
            CertificationDefinition definition = this.certification.getCertificationDefinition(this.getContext());

            if (definition != null) {
                isAutoApproveEnabled = definition.isAutoApprove();
            }
        } catch (GeneralException ge) {
            log.warn("Unable to retrieve the certification definition", ge);
        }
        return new CertificationEntityListService(this, ENTITY_SELECTOR, this.certification.getType(),
                isAutoApproveEnabled);
    }

    /**
     * Overridden to also remove the parameters that are already handled when listing items.
     */
    @Override
    protected Map<String,String> getOtherQueryParams() {
        Map<String,String> params = super.getOtherQueryParams();
        params.remove(PARAM_STATUS);
        params.remove(PARAM_EXCLUDED_STATUS);
        params.remove(PARAM_INCLUDED_STATISTICS);
        return params;
    }

    /**
     * Get the IDs of all CertificationEntities that match the query parameters.
     *
     * @param  statuses  The possibly-null entity summary statuses to include.
     * @param  excludedStatuses  The possibly-null entity summary statuses to exclude.
     *
     * @return A non-null List with the IDs of all CertificationEntities that match the query parameters.
     */
    @GET
    @Path("/ids")
    public List<String> getCertificationEntityIds(@QueryParam("status") List<Status> statuses,
                                                  @QueryParam("excludedStatus") List<Status> excludedStatuses)
        throws GeneralException {

        authorizeAndConfigureListContext(statuses, excludedStatuses);
        return createEntityListService().getCertificationEntityIds();
    }

    /**
     * Get the filter for these certification entities.
     * @return List of ListFilterDTO objects
     * @throws GeneralException
     */
    @GET
    @Path(Paths.FILTERS)
    public List<ListFilterDTO> getCertificationEntityFilters() throws GeneralException {
        authorize(new CertificationAuthorizer(this.certification));

        return getListFilterService().getListFilters(true);
    }

    @Path(Paths.SUGGEST)
    public CertificationItemSuggestResource getSuggestResource() throws GeneralException {
        return new CertificationItemSuggestResource(this.certification, new BaseSuggestAuthorizerContext(getCertificationEntityFilters()), this);
    }

    /**
     * Create a ListFilterService.
     */
    private ListFilterService getListFilterService() {
        return new ListFilterService(getContext(), getLocale(), this.listFilterContext);
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

    /**
     * Authorize that the user can access the entities list and setup the CertificationEntityListServiceContext.
     */
    private void authorizeAndConfigureListContext(List<Status> statuses, List<Status> excludedStatuses)
        throws GeneralException {

        authorize(new CertificationAuthorizer(this.certification));

        // Save the stuff required by the CertificationEntityListServiceContext.
        this.statuses = statuses;
        this.excludedStatuses = excludedStatuses;
    }

    /**
     * Get the CertificationEntityResource for certification items
     *
     * @param entityId CertificationEntityId
     * @return CertificationEntityResource
     * @throws GeneralException
     */
    @Path("{entityId}")
    public CertificationEntityResource getCertificationEntityResource(@PathParam("entityId") String entityId) throws GeneralException {
        return new CertificationEntityResource(this, this.certification, entityId);
    }


    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //
    // CertificationEntityListServiceContext methods
    //
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    public String getCertificationId() {
        return this.certification.getId();
    }

    public List<AbstractCertificationItem.Status> getStatuses() {
        return this.statuses;
    }

    public List<AbstractCertificationItem.Status> getExcludedStatuses() {
        return this.excludedStatuses;
    }

    @Override
    public boolean isIncludeStatistics() {
        return this.includeStatistics;
    }
}
