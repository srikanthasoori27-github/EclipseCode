/*
 * (c) Copyright 2017. SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.rest.ui.identityrequest;

import javax.ws.rs.GET;

import sailpoint.authorization.identityrequest.IdentityRequestAccessAuthorizer;
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


/**
 * Resource for listing IdentityRequestItem objects that are provisioning.
 * Authorization for identity request happens on parent resource.
 */
public class IdentityRequestChangeItemListResource extends BaseListResource implements BaseListServiceContext {

    private IdentityRequest identityRequest;
    private IdentityRequestItemListService.IdentityRequestItemListColumnSelector listColumnSelector;

    /**
     * Constructor
     * @param identityRequest The Identity Request object
     * @param parent parent resource
     * @throws GeneralException
     */
    public IdentityRequestChangeItemListResource(IdentityRequest identityRequest, BaseResource parent) throws GeneralException {
        super(parent);
        this.identityRequest = identityRequest;
        this.listColumnSelector = new IdentityRequestItemListService.IdentityRequestItemListColumnSelector(UIConfig.UI_IDENTITY_REQUEST_CHANGE_ITEMS_COLUMNS);
    }

    /**
     * Get a ListResult containing provisioning IdentityRequestItemDTOs for the given request
     * @return ListResult list of identity request items that are provisioning
     * @throws GeneralException
     */
    @GET
    public ListResult getChangeItems() throws GeneralException {
        authorize(new IdentityRequestAccessAuthorizer(this.identityRequest));
        setFilters(new ListFilterService(getContext(), getLocale(), new IdentityRequestItemListFilterContext()).convertQueryParametersToFilters(getOtherQueryParams(), true));
        IdentityRequestItemListService listService = new IdentityRequestItemListService(this, this, this.listColumnSelector);
        return listService.getIdentityRequestChangeItems(identityRequest);
    }

}
