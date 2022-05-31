/*
 * (c) Copyright 2017. SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.rest.ui.identityrequest;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;

import sailpoint.authorization.identityrequest.IdentityRequestAccessAuthorizer;
import sailpoint.authorization.identityrequest.IdentityRequestItemAccessAuthorizer;
import sailpoint.integration.ListResult;
import sailpoint.object.IdentityRequest;
import sailpoint.object.UIConfig;
import sailpoint.rest.BaseListResource;
import sailpoint.rest.BaseResource;
import sailpoint.service.BaseListServiceContext;
import sailpoint.service.identityrequest.IdentityRequestItemListFilterContext;
import sailpoint.service.identityrequest.IdentityRequestItemListService;
import sailpoint.service.listfilter.ListFilterService;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

/**
 * Resource for listing IdentityRequestItem objects.
 * Authorization for identity request happens on parent resource.
 */
public class IdentityRequestItemListResource extends BaseListResource implements BaseListServiceContext {

    private IdentityRequest identityRequest;
    private IdentityRequestItemListService.IdentityRequestItemListColumnSelector listColumnSelector;

    /**
     * Constructor
     * @param identityRequest The Identity Request object
     * @param parent parent resource
     * @throws GeneralException
     */
    public IdentityRequestItemListResource(IdentityRequest identityRequest, BaseResource parent) throws GeneralException {
        super(parent);
        this.identityRequest = identityRequest;

        if (Util.isNothing(this.colKey)) {
            this.colKey = UIConfig.UI_IDENTITY_REQUEST_ITEMS_COLUMNS;
        }
        this.listColumnSelector = new IdentityRequestItemListService.IdentityRequestItemListColumnSelector(this.colKey);
    }

    /**
     * Get a ListResult containing IdentityRequestItemDTOs for the given request
     * @return ListResult
     * @throws GeneralException
     */
    @GET
    public ListResult getItems() throws GeneralException {
        authorize(new IdentityRequestAccessAuthorizer(this.identityRequest));
        setFilters(new ListFilterService(getContext(), getLocale(), new IdentityRequestItemListFilterContext()).convertQueryParametersToFilters(getOtherQueryParams(), true));
        IdentityRequestItemListService listService = new IdentityRequestItemListService(this, this, this.listColumnSelector);
        return listService.getIdentityRequestItems(identityRequest, false);
    }

    /**
     * Get the identity request item resource
     * @param itemId identity request item id
     * @return IdentityRequestItemResource
     * @throws GeneralException
     */
    @Path("{itemId}")
    public IdentityRequestItemResource getIdentityRequestItemResource(@PathParam("itemId") String itemId) throws GeneralException {
        authorize(new IdentityRequestItemAccessAuthorizer(itemId, this.identityRequest.getName()));
        return new IdentityRequestItemResource(this, itemId);
    }
}
