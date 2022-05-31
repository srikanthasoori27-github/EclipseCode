package sailpoint.rest.ui.requestaccess;

import sailpoint.authorization.LcmActionAuthorizer;
import sailpoint.authorization.utils.LcmUtils;
import sailpoint.integration.ListResult;
import sailpoint.object.QuickLink;
import sailpoint.rest.BaseResource;
import sailpoint.rest.UserAccessUtil;
import sailpoint.rest.ui.Paths;
import sailpoint.rest.ui.suggest.SuggestResource;
import sailpoint.service.listfilter.ListFilterDTO;
import sailpoint.service.suggest.BaseSuggestAuthorizerContext;
import sailpoint.service.useraccess.UserAccessSearchOptions;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Resource to search and list current access items for a single identity
 */
public class CurrentAccessItemListResource extends BaseAccessItemListResource {

    public static final String COLUMNS_KEY_PREFIX = "uiCurrentAccessItemsColumns";

    public CurrentAccessItemListResource(BaseResource parent) {
        super(parent);
    }

    /**
     * List all access items (roles and exceptions) including requested access items for the 
     * given identity. 
     *
     * Rest parameters:
     * start (Integer) - the index to start from
     * limit (Integer) - the number of results to return
     * identityId (String) - ID of identity being targeted (requestee). If more than one identity is being targeted, 
     *     then do not specify this parameter at all. 
     * accessType (String) - "role" or "entitlement" to return only that type of result
     * searchType (String) - "Identity" for searches based on identity attributes and/or lists of identities
     *     or "Keyword" for full text searches
     * query (String) - query to use for full text search
     * ownerId (String) - ID of access owner (ie. Role owner or Entitlement owner)
     * applicationIds (List) - If defined, limit results to given applications
     * attributes (List) - If defined, limit results to given attribute names
     * value (String) - If defined, limit results to given value
     * q_<extendedAttributeName> - This "q_" prefix can go before parameters that define filters on extended attributes
     *
     * @param identityId ID of identity to get current access for. Required.
     * @return ListResult with count and page of results for current access
     * @throws sailpoint.tools.GeneralException
     */
    @GET
    public ListResult getCurrentAccess(@QueryParam("identityId") String identityId) throws GeneralException {
        authorize(new LcmActionAuthorizer(QuickLink.LCM_ACTION_REQUEST_ACCESS));
        LcmUtils.authorizeTargetIdentity(identityId, getContext(), this);

        Map<String, String> additionalParams = new HashMap<String, String>();
        additionalParams.put(UserAccessUtil.PARAM_SEARCH_TYPE, UserAccessSearchOptions.SearchTypes.CurrentAccess.toString());
        return getAccessItems(COLUMNS_KEY_PREFIX, additionalParams);
    }

    /**
     * List all the filters supported in current access search
     *
     * @param identityId ID of identity to get current access for. Required.
     * @return List of ListFilterDTO objects
     * @throws GeneralException
     */
    @GET
    @Path(Paths.FILTERS)
    public List<ListFilterDTO> getCurrentAccessFilters(@QueryParam("identityId") String identityId) throws GeneralException {
        authorize(new LcmActionAuthorizer(QuickLink.LCM_ACTION_REQUEST_ACCESS));
        LcmUtils.authorizeTargetIdentity(identityId, getContext(), this);

        Map<String, String> queryParams = getQueryParamMap();
        String searchType = queryParams.get(UserAccessUtil.PARAM_SEARCH_TYPE);
        String suggestUrl = getMatchedUri().replace(Paths.FILTERS, "") + searchType + "/" + identityId + "/suggest";

        return new UserAccessUtil(getContext(), getLocale()).getCurrentAccessFilters(getQueryParamMap(), getLoggedInUser(), suggestUrl);
    }

    /**
     * Get the suggest resource for access items filters. We are using path paremeters for search type and target identity here
     * since the suggest url needs to work for both POST and GET and query parameters are not appropriate for our suggest auth
     * stuff.
     *
     * @param searchType SearchType enumeration for UserAccessUtil
     * @param identityId ID of target identity
     * @return SuggestResource authorized based on the filters
     * @throws GeneralException
     */
    @Path("{searchType}/{identityId}/suggest")
    public SuggestResource getSuggestResource(@PathParam("searchType") String searchType, @PathParam("identityId") String identityId) throws GeneralException {
        authorize(new LcmActionAuthorizer(QuickLink.LCM_ACTION_REQUEST_ACCESS));
        LcmUtils.authorizeTargetIdentity(identityId, getContext(), this);

        Map<String, String> params = new HashMap<>();
        params.put(UserAccessUtil.PARAM_IDENTITY_ID, identityId);
        params.put(UserAccessUtil.PARAM_SEARCH_TYPE, Util.getString(searchType));

        List<ListFilterDTO> filters =
                new UserAccessUtil(getContext(), getLocale()).getCurrentAccessFilters(params, getLoggedInUser(), null);
        return new SuggestResource(this, new BaseSuggestAuthorizerContext(filters), false);
    }

}
