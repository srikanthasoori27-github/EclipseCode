package sailpoint.rest.ui.requestaccess;

import sailpoint.authorization.LcmActionAuthorizer;
import sailpoint.integration.ListResult;
import sailpoint.object.QuickLink;
import sailpoint.rest.BaseResource;
import sailpoint.rest.UserAccessUtil;
import sailpoint.rest.ui.Paths;
import sailpoint.rest.ui.suggest.SuggestResource;
import sailpoint.service.ManagedAttributeServiceContext;
import sailpoint.service.listfilter.ListFilterDTO;
import sailpoint.service.listfilter.ListFilterValue;
import sailpoint.service.suggest.BaseSuggestAuthorizerContext;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Resource to search for access items to request
 */
public class AccessItemListResource extends BaseAccessItemListResource implements ManagedAttributeServiceContext {

    public static final String COLUMNS_KEY_PREFIX = "uiAccessItemsColumns";

    private String requesteeId;

    public AccessItemListResource(BaseResource parent) {
        super(parent);
    }

    @Path("{accessItemId}")
    public AccessItemResource getAccessItemResource(@PathParam("accessItemId") String accessItemId)
            throws GeneralException {
        return new AccessItemResource(accessItemId, this);
    }

    /**
     * List all applicable access items given the user and passed in filters.
     *
     * Rest parameters:
     * start (Integer) - the index to start from
     * limit (Integer) - the number of results to return
     * identityId (String) - ID of identity being targeted (requestee). If more than one identity is being targeted, 
     *     then do not specify this parameter at all. 
     * accessType (String) - "role" or "entitlement" to return only that type of result
     * searchType (String) - "Population" for searches based on identity attributes,
     *     "Identity" for searches based on lists of identities,
     *     "Keyword" for full text searches
     * query (String) - query to use for full text search
     * ownerId (String) - ID of access owner (ie. Role owner or Entitlement owner)
     * applicationIds (List) - If defined, limit results to given applications
     * attributes (List) - If defined, limit results to given attribute names
     * value (String) - If defined, limit results to given value
     * q_<extendedAttributeName> - This "q_" prefix can go before parameters that define filters on extended attributes
     * q_Identity.<property> - This "q_" prefix can go before Identity join filters
     * identityIds (List) - If defined, join to Identity objects in this set of ID's only
     *
     * @return ListResult JSON with representations of access items
     */
    @GET
    public ListResult getAccessItems() throws GeneralException {

        //'Request Access' must be enabled for the user
        authorize(new LcmActionAuthorizer(QuickLink.LCM_ACTION_REQUEST_ACCESS));

        return getAccessItems(COLUMNS_KEY_PREFIX, null);
    }

    /**
     * List all the filters supported in User Access search
     * 
     * Rest parameters:
     * identityId (String) - ID of identity being targeted (requestee). If more than one identity is being targeted, 
     *     then do not specify this parameter at all.
     * searchType (String) - "Population" for searches based on identity attributes,
     *     "Identity" for searches based on lists of identities,
     *     "Keyword" for full text searches
     *     
     * @return List of ListFilterDTO objects
     * @throws GeneralException
     */
    @GET
    @Path(Paths.FILTERS)
    public List<ListFilterDTO> getAccessItemFilters() throws GeneralException {
        authorize(new LcmActionAuthorizer(QuickLink.LCM_ACTION_REQUEST_ACCESS));
        UserAccessUtil userAccessUtil = new UserAccessUtil(getContext(), getLocale());
        Map<String, String> queryParams = getQueryParamMap();
        String searchType = queryParams.get(UserAccessUtil.PARAM_SEARCH_TYPE);
        String identityId = queryParams.get(UserAccessUtil.PARAM_IDENTITY_ID);
        if (Util.isNothing(identityId)) {
            identityId = "null";
        }
        String suggestUrl = getMatchedUri().replace(Paths.FILTERS, "") + searchType + "/" + identityId + "/suggest";
        return userAccessUtil.getAccessItemFilters(getQueryParamMap(), getLoggedInUser(), suggestUrl);
    }

    /**
     * Get the suggest resource for access items filters. We are using path paremeters for search type and target identity here
     * since the suggest url needs to work for both POST and GET and query parameters are not appropriate for our suggest auth
     * stuff.
     *
     * @param searchType SearchType enumeration for UserAccessUtil
     * @param identityId ID of target identity, or "null" for multiple target identities
     * @return SuggestResource authorized based on the filters
     * @throws GeneralException
     */
    @Path("{searchType}/{identityId}/suggest")
    public SuggestResource getSuggestResource(@PathParam("searchType") String searchType, @PathParam("identityId") String identityId) throws GeneralException {
        authorize(new LcmActionAuthorizer(QuickLink.LCM_ACTION_REQUEST_ACCESS));

        Map<String, String> params = new HashMap<>();
        params.put(UserAccessUtil.PARAM_IDENTITY_ID, Util.getString(identityId));
        params.put(UserAccessUtil.PARAM_SEARCH_TYPE, Util.getString(searchType));

        List<ListFilterDTO> filters =
                new UserAccessUtil(getContext(), getLocale()).getAccessItemFilters(params, getLoggedInUser(), null);
        return new SuggestResource(this, new BaseSuggestAuthorizerContext(filters), false);
    }

    /**
     * Return values for any filters that we support based upon what is passed into the
     * call as request parameters
     * 
     * Supports the same standard rest parameters as {@link #getAccessItemFilters()} along with 
     * filter parameters
     *     
     * @return Map<String,ListFilterValue> a mapping of filter properties to actual values that should be set
     * in the suggest
     * @throws GeneralException
     */
    @GET
    @Path("filterValues")
    public Map<String,ListFilterValue> getAccessItemFilterValues() throws GeneralException {
        authorize(new LcmActionAuthorizer(QuickLink.LCM_ACTION_REQUEST_ACCESS));

        return new UserAccessUtil(getContext(), getLocale())
                .getAccessItemFilterValues(getQueryParamMap(), getLoggedInUser(), this);
    }

    /**
     * Needed to support implementing ManagedAttributeServiceContext
     */
    public String getRequesteeId() {
        return requesteeId;
    }

    public void setRequesteeId(String requesteeId) {
        this.requesteeId = requesteeId;
    }
}