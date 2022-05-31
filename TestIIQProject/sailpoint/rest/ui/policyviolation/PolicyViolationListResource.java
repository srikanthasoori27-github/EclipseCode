/* (c) Copyright 2017 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.rest.ui.policyviolation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;

import sailpoint.authorization.AllowAllAuthorizer;
import sailpoint.authorization.PolicyViolationAuthorizer;
import sailpoint.authorization.RightAuthorizer;
import sailpoint.integration.ListResult;
import sailpoint.integration.RequestResult;
import sailpoint.object.ColumnConfig;
import sailpoint.object.Filter;
import sailpoint.object.PolicyViolation;
import sailpoint.object.SPRight;
import sailpoint.object.UIConfig;
import sailpoint.rest.BaseListResource;
import sailpoint.rest.ui.Paths;
import sailpoint.rest.ui.suggest.SuggestResource;
import sailpoint.service.BaseListResourceColumnSelector;
import sailpoint.service.listfilter.ListFilterDTO;
import sailpoint.service.listfilter.ListFilterService;
import sailpoint.service.policyviolation.PolicyViolationDecision;
import sailpoint.service.policyviolation.PolicyViolationDecisionConfigDTO;
import sailpoint.service.policyviolation.PolicyViolationDecisioner;
import sailpoint.service.policyviolation.PolicyViolationListFilterContext;
import sailpoint.service.policyviolation.PolicyViolationListService;
import sailpoint.service.policyviolation.PolicyViolationListServiceContext;
import sailpoint.service.suggest.BaseSuggestAuthorizerContext;
import sailpoint.tools.GeneralException;
import sailpoint.tools.InvalidParameterException;
import sailpoint.tools.Util;
import sailpoint.web.certification.BulkCertificationHelper;
import sailpoint.web.certification.CertificationScheduleDTO;
import sailpoint.web.util.Sorter;

@Path("policyViolations")
/**
 * Resource for listing PolicyViolations.
 */
public class PolicyViolationListResource extends BaseListResource implements PolicyViolationListServiceContext {

    private static String FIRSTNAME_SORT_COLUMN = "identity.firstname";
    private static String STATUS_SORT_COLUMN = "status";
    private static String SHOW_ALL = "showAll";
    private static String DECISIONS = "decisions";

    // Session attribute key used for setting the cert schedule for certify action
    private static final String EDITED_CERT_SCHEDULE_KEY = "EDITED_CERT_SCHEDULE";

    private BaseListResourceColumnSelector policyViolationListResourceSelector =
        new BaseListResourceColumnSelector(UIConfig.UI_POLICY_VIOLATIONS_COLUMNS);
    private boolean showAll = false;

    /**
     * Constructor
     */
    public PolicyViolationListResource() throws GeneralException {
        super();
    }

    /**
     * Get the list of active policy violations for the logged in user
     * @return ListResult with PolicyViolationDTO objects
     * @throws GeneralException
     */
    @GET
    public ListResult getPolicyViolations() throws GeneralException {

        authorize(new AllowAllAuthorizer());
        PolicyViolationListService policyViolationListService =
                new PolicyViolationListService(this, this, policyViolationListResourceSelector);

        setFilters();
        setShowAll(getOtherQueryParams());
        return policyViolationListService.getPolicyViolations();
    }

    /**
     * Pass through to PolicyViolationResource.
     *
     * @param policyViolationId Policy violation id
     * @return PolicyViolationResource
     * @throws GeneralException
     */
    @Path("{policyViolationId}")
    public PolicyViolationResource getPolicyViolationResource(@PathParam("policyViolationId") String policyViolationId) throws GeneralException {
        return new PolicyViolationResource(this, policyViolationId);
    }

    /**
     * Get the list of filters for policy violations
     * @return List of ListFilterDTO objects
     * @throws GeneralException
     */
    @GET
    @Path(Paths.FILTERS)
    public List<ListFilterDTO> getPolicyViolationFilters() throws GeneralException {
        authorize(new AllowAllAuthorizer());
        String suggestUrl = getMatchedUri().replace(Paths.FILTERS, Paths.SUGGEST);
        return this.getListFilterService(suggestUrl).getListFilters(true);
    }

    /**
     * Get the SuggestResource for the filters on the policy violation list
     * @return SuggestResource
     * @throws GeneralException
     */
    @Path(Paths.SUGGEST)
    public SuggestResource getSuggestResource() throws GeneralException {
        authorize(new AllowAllAuthorizer());
        return new SuggestResource(this, new BaseSuggestAuthorizerContext(getPolicyViolationFilters()));
    }

    /**
     * Get the decisions configuration for policy violations
     * @return PolicyViolationDecisionConfigDTO object
     * @throws GeneralException
     */
    @GET
    @Path("config")
    public PolicyViolationDecisionConfigDTO getDecisionConfig() throws GeneralException {
        authorize(new AllowAllAuthorizer());
        PolicyViolationListService policyViolationListService =
                new PolicyViolationListService(this, this, policyViolationListResourceSelector);

        return policyViolationListService.getDecisionConfig();
    }

    /**
     * Set the filter panel filters
     */
    private void setFilters() throws GeneralException {
        Map<String,String> params = super.getOtherQueryParams();
        ListFilterService svc = getListFilterService(null);
        setFilters(svc.convertQueryParametersToFilters(params, true));
    }

    /**
     * Create a ListFilterService.
     *
     * @param suggestUrl URL for the suggest endpoint. Optional.
     */
    private ListFilterService getListFilterService(String suggestUrl) {
        PolicyViolationListFilterContext filterContext = new PolicyViolationListFilterContext();
        if (suggestUrl != null) {
            filterContext.setSuggestUrl(suggestUrl);
        }
        return new ListFilterService(getContext(), getLocale(), filterContext);
    }

    @Override
    public List<Filter> getFilters() {
        return this.filters;
    }

    @Override
    public List<Sorter> getSorters(List<ColumnConfig> columnConfigs) throws GeneralException {
        List<Sorter> sorters = super.getSorters(columnConfigs);

        // Default sort on first name if nothing else is specified.
        if (Util.isEmpty(sorters)) {
            if (sorters == null) {
                sorters = new ArrayList<>();
            }
            Sorter defaultSorter = new Sorter(FIRSTNAME_SORT_COLUMN, Sorter.SORTER_DIRECTION_ASC, true);
            defaultSorter.setSecondarySort(STATUS_SORT_COLUMN);
            sorters.add(defaultSorter);
        }

        return sorters;
    }

    @Override
    public boolean isShowAll() {
        return this.showAll;
    }

    /**
     * Sets the show all flag based on what is in the parameters
     * @param parameters Map of parameters to fish the showAll flag out of
     */
    private void setShowAll(Map<String, ? extends Object> parameters) {
        // Check if the showAll param is included
        if (parameters == null || !parameters.containsKey(SHOW_ALL)) {
            this.showAll = false;
        } else {
            this.showAll = Util.atob(parameters.get(SHOW_ALL).toString());
        }
    }

    /**
     * Save decisions for the policy violations
     * @param request Request containing a Map of PolicyViolationDecision objects
     * @return RequestResult containing the errors and/or warnings and the status of the request.
     */
    @SuppressWarnings("unchecked")
    @POST
    @Path("decisions")
    public RequestResult saveDecisions(Map<String, Object> request) throws GeneralException {
        List<PolicyViolationDecision> decisions = validateAndSetupDecisionsRequest(request);
        setShowAll(request);
        PolicyViolationDecisioner decisioner = new PolicyViolationDecisioner(this, this);
        return decisioner.decide(decisions);
    }

    /**
     * Generate identity cert schedules for the selected identities
     *
     * @param request Request containing a Map of PolicyViolationDecision objects
     * @return RequestResult
     * @throws GeneralException
     */
    @POST
    @Path("certify")
    public RequestResult certify(Map<String, Object> request) throws GeneralException {
        // make sure logged in user can certify
        authorize(new RightAuthorizer(SPRight.FullAccessCertificationSchedule));

        List<PolicyViolationDecision> decisions = validateAndSetupDecisionsRequest(request);

        // for bulk certify there should be one decision object with the selected items in the selectionModel
        if (decisions.size() != 1) {
            throw new InvalidParameterException("should only be one decision");
        }

        setShowAll(request);
        PolicyViolationDecisioner decisioner = new PolicyViolationDecisioner(this, this);
        List<String> policyViolationIds = decisioner.getObjectIds(decisions.get(0));

        // authorize on all the policy violations if user does not have FullAccessPolicyViolation
        if (!sailpoint.web.Authorizer.hasAccess(getLoggedInUserCapabilities(), getLoggedInUserRights(),
                SPRight.FullAccessPolicyViolation)) {

            int decacheCount = 0;
            for (String violationId : Util.iterate(policyViolationIds)) {
                PolicyViolation violation = getContext().getObjectById(PolicyViolation.class, violationId);
                authorize(new PolicyViolationAuthorizer(violation));
                if (++decacheCount % 20 == 0) {
                    getContext().decache();
                }
            }
        }

        Map<String, List<String>> identities = decisioner.getPolicyViolationIdentities(policyViolationIds);

        BulkCertificationHelper bulkCertification = new BulkCertificationHelper(getContext(), identities.get("ids"),
                false, identities.get("names"));
        CertificationScheduleDTO certificationScheduleDTO = bulkCertification.generateCertificationScheduleDTO(null, this.getLoggedInUser());

        // save the cert schedule to session
        this.getSession().setAttribute(EDITED_CERT_SCHEDULE_KEY, certificationScheduleDTO);

        // return success response, nav history will be set on client side
        return new RequestResult(PolicyViolationDecisioner.SUCCESS, null, null, null);
    }

    /**
     * Helper method to validate and setup the list of PolicyViolationDecision objects.
     * @param request Map<String, Object> request data
     * @return List<PolicyViolationDecision>
     * @throws GeneralException
     */
    private List<PolicyViolationDecision> validateAndSetupDecisionsRequest(Map<String, Object> request) throws GeneralException {
        if (request == null || !request.containsKey(DECISIONS)) {
            throw new InvalidParameterException(DECISIONS);
        }
        List<Map<String, Object>> decisionMaps = (List<Map<String, Object>>)request.get(DECISIONS);
        if (Util.isEmpty(decisionMaps)) {
            throw new InvalidParameterException(DECISIONS);
        }

        List<PolicyViolationDecision> decisions = new ArrayList<>();
        for (Map<String, Object> decisionMap : Util.iterate(decisionMaps)) {
            decisions.add(new PolicyViolationDecision(decisionMap));
        }

        return decisions;
    }
}