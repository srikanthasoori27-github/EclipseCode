/*
 * (c) Copyright 2017. SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.rest.ui.identityrequest;

import java.util.List;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;

import sailpoint.authorization.AllowAllAuthorizer;
import sailpoint.authorization.LcmEnabledAuthorizer;
import sailpoint.integration.ListResult;
import sailpoint.object.UIConfig;
import sailpoint.rest.BaseListResource;
import sailpoint.rest.ui.Paths;
import sailpoint.rest.ui.suggest.SuggestResource;
import sailpoint.service.BaseListResourceColumnSelector;
import sailpoint.service.BaseListServiceContext;
import sailpoint.service.identityrequest.IdentityRequestListFilterContext;
import sailpoint.service.identityrequest.IdentityRequestListService;
import sailpoint.service.listfilter.ListFilterDTO;
import sailpoint.service.listfilter.ListFilterService;
import sailpoint.service.suggest.BaseSuggestAuthorizerContext;
import sailpoint.tools.GeneralException;

/**
 * Resource for listing identity request
 */
@Path("identityRequests")
public class IdentityRequestListResource extends BaseListResource implements BaseListServiceContext {

    private BaseListResourceColumnSelector listResourceSelector =
            new BaseListResourceColumnSelector(UIConfig.UI_IDENTITY_REQUEST_CARD_COLUMNS);

    /**
     * Constructor
     */
    public IdentityRequestListResource() throws GeneralException {
        super();
    }

    /**
     * Get the list of identity requests for the logged in user.
     *
     * @return ListResult list of identity requests
     * @throws GeneralException
     */
    @GET
    public ListResult getIdentityRequests() throws GeneralException {
        authorize(new AllowAllAuthorizer(), new LcmEnabledAuthorizer());
        setFilters(new ListFilterService(getContext(), getLocale(), new IdentityRequestListFilterContext()).convertQueryParametersToFilters(getOtherQueryParams(), true));
        IdentityRequestListService identityRequestListService = new IdentityRequestListService(this, this, listResourceSelector);
        return identityRequestListService.getIdentityRequests();
    }

    /**
     * Get the list of filters for the identity requests list page
     * @return List of ListFilterDTO available for the list page
     * @throws GeneralException
     */
    @GET
    @Path(Paths.FILTERS)
    public List<ListFilterDTO> getIdentityRequestFilters() throws GeneralException {
        authorize(new AllowAllAuthorizer(), new LcmEnabledAuthorizer());
        IdentityRequestListFilterContext filterContext = new IdentityRequestListFilterContext();
        String suggestUrl = getMatchedUri().replace(Paths.FILTERS, Paths.SUGGEST);
        filterContext.setSuggestUrl(suggestUrl);
        return new ListFilterService(getContext(), getLocale(), filterContext).getListFilters(true);
    }

    /**
     * Get the SuggestResource for the filters on the identity request list
     * @return SuggestResource
     * @throws GeneralException
     */
    @Path(Paths.SUGGEST)
    public SuggestResource getSuggestResource() throws GeneralException {
        authorize(new AllowAllAuthorizer(), new LcmEnabledAuthorizer());
        return new SuggestResource(this, new BaseSuggestAuthorizerContext(getIdentityRequestFilters()));
    }

    /**
     * Get the identity request resource for the identity request id.
     *
     * @param requestId request ID (name) of identity request to look for.
     * @return IdentityRequestResource resource for the identity request
     * @throws GeneralException
     */
    @Path("{requestId}")
    public IdentityRequestResource getIdentityRequestResource(@PathParam("requestId") String requestId) throws GeneralException {
        return new IdentityRequestResource(this, requestId);
    }
}
