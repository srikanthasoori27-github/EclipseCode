package sailpoint.rest.ui.suggest;

import java.util.ArrayList;
import java.util.List;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;

import sailpoint.api.ObjectUtil;
import sailpoint.authorization.AllowAllAuthorizer;
import sailpoint.integration.ListResult;
import sailpoint.object.Application;
import sailpoint.object.Filter;
import sailpoint.object.ManagedAttribute;
import sailpoint.object.QueryOptions;
import sailpoint.service.BaseListResourceColumnSelector;
import sailpoint.service.LCMConfigService;
import sailpoint.service.ManagedAttributeService;
import sailpoint.service.ManagedAttributeServiceContext;
import sailpoint.service.listfilter.ListFilterDTO;
import sailpoint.service.listfilter.ListFilterService;
import sailpoint.service.listfilter.UserAccessEntitlementFilterContext;
import sailpoint.service.suggest.SuggestAuthorizer;
import sailpoint.service.suggest.SuggestService;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

/**
 * Suggest resource to list applications and their attributes
 */
public class ApplicationSuggestResource extends BaseSuggestResource implements ManagedAttributeServiceContext {

    public ApplicationSuggestResource() {
        super();
    }
    
    public ApplicationSuggestResource(SuggestResource suggestResource) throws GeneralException {
        super(suggestResource);
        this.extraParamsMap = suggestResource.getExtraParamMap();
        this.suggestClass = suggestResource.getSuggestClass();
        //bug30471 adding filter string as it can be required for filtering suggest allowed values.
        this.filterString = suggestResource.getFilterString();
        this.query = suggestResource.getQuery();
        this.start = suggestResource.getStart();
        this.limit = suggestResource.getLimit();
        this.sortBy = suggestResource.getSortBy();
        this.sortDirection = suggestResource.getSortDirection();

        this.authorizerContext = suggestResource.authorizerContext;
    }
    
    public String getRequesteeId() throws GeneralException {
        return Util.getString(getExtraParamMap(), UserAccessEntitlementFilterContext.ATTR_REQUESTEE_ID);
    }

    /**
     * List of application maps for all applications.
     * 
     * @return ListResult
     */
    @GET @SuppressWarnings("unchecked")
    public ListResult getApplications()
            throws GeneralException {

        this.suggestClass = Application.class.getSimpleName();
        authorize(new AllowAllAuthorizer(), new SuggestAuthorizer(this.authorizerContext, this.suggestClass));
        
        ListResult result = null;
        boolean returnNone = false;

        QueryOptions ops = getQueryOptions();
        ops.add(Filter.eq("noAggregation", false));        
        if (isAttributeOnly()) {
            ops.add(Filter.join("id", "ManagedAttribute.application"));
            ops.add(Filter.eq("ManagedAttribute.requestable", true));
            ops.add(Filter.ne("ManagedAttribute.type", ManagedAttribute.Type.Permission.name()));
        }
        if (isLCM()) {
            //IIQTC-101: Removing scope if this is for an LCM request
            ops.setScopeResults(false);
            LCMConfigService configService = new LCMConfigService(getContext());
            returnNone = configService.addLCMApplicationAuthorityFilters(ops, getLoggedInUser(), getRequesteeId());
        }
        if(isLCMApplication()) {
            SuggestService suggestService = new SuggestService(this);
            List<String> requestedApplications = (List<String>) getExtraParamMap().get(SuggestService.REQUESTED_APPLICATIONS);
            ops.add(suggestService.getLCMAccountOnlyAppFilter(getRequesteeId(), requestedApplications));
        }
        if (returnNone) {
            result = new ListResult(new ArrayList(), 0);
        } else {
            SuggestService suggestService = new SuggestService(this);
            result = suggestService.getObjects(ops);
        }
        
        return result;
    }

    /**
     * List of application attribute maps for the passed in list of applications.
     * 
     * @param applications a list of application ids to get the attribtues for
     * 
     * @return ListResult
     */
    @GET
    @Path("attributes")
    public ListResult getApplicationAttributes(
            @QueryParam("applications") List<String> applications)
            throws GeneralException {
        authorize(new SuggestAuthorizer(this.authorizerContext, ManagedAttribute.class.getSimpleName()));
        this.extraParams = getQueryParamMap().get(PARAM_EXTRA_PARAMS);
        
        ManagedAttributeService  service = new ManagedAttributeService(getContext(), this,
                new BaseListResourceColumnSelector(ListFilterService.MANAGED_ATTRIBUTE_TYPE_WITH_APP));

        // IIQTC-138: Query now uses the variable 'start' to enable 'load more' functionality.
        QueryOptions qo = service.getRequestableQueryOptions(query, this.start, this.limit, applications);

        return service.getResults(qo, isLCM());
    }

    /**
     * List of values for the given list of application and attribute.
     * 
     * @param application String Application ID
     * @param attribute String Attribute name
     * @param isPermission Boolean True if permission, false if attribute
     * 
     * @return ListResult containing the values for the given application and attributes
     */
    @GET
    @Path("attributes/values")
    public ListResult getApplicationAttributeValues(
            @QueryParam("application") String application,
            @QueryParam("attribute") String attribute,
            @QueryParam("isPermission") boolean isPermission)
            throws GeneralException {
        authorize(new SuggestAuthorizer(this.authorizerContext, ManagedAttribute.class.getSimpleName()));

        String applicationName = ObjectUtil.getName(getContext(), Application.class, application);
        SuggestService suggestService = new SuggestService(this);
        return suggestService.getApplicationAttributeValues(
            applicationName, attribute, isPermission);
    }

    private boolean isLCM() throws GeneralException {
        return Util.getBoolean(getExtraParamMap(), ListFilterDTO.ATTR_SUGGEST_IS_LCM);
    }
    
    private boolean isLCMApplication() throws GeneralException {
        return Util.getBoolean(getExtraParamMap(), ListFilterDTO.ATTR_SUGGEST_LCM_APPLICATION);  
    }
    
    private boolean isAttributeOnly() throws GeneralException {
        return Util.getBoolean(getExtraParamMap(), UserAccessEntitlementFilterContext.ATTR_APPLICATION_ATTRIBUTE_ONLY);
    }
    
}