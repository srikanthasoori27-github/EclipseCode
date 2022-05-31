/* (c) Copyright 2015 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.rest.ui.requestaccess;

import java.util.List;

import javax.ws.rs.GET;
import javax.ws.rs.QueryParam;

import sailpoint.integration.ListResult;
import sailpoint.object.Filter;
import sailpoint.object.SailPointObject;
import sailpoint.rest.BaseListResource;
import sailpoint.rest.BaseResource;
import sailpoint.rest.UserAccessUtil;
import sailpoint.service.BaseListResourceColumnSelector;
import sailpoint.service.IdentityListService;
import sailpoint.service.IdentityListServiceContext;
import sailpoint.service.LCMConfigService;
import sailpoint.service.ListServiceColumnSelector;
import sailpoint.tools.GeneralException;

/**
 * Population resource for mobile
 */
public class AccessItemPopulationResource extends BaseListResource implements IdentityListServiceContext {

    private Filter sessionFilter = null;
    private SailPointObject accessItem;
    private final static String COLUMN_CONFIG_NAME = "uiAccessRequestItemPopulationColumns";

    /**
     * Constructor
     *
     * @param accessItem role or entitlement item
     */
    public AccessItemPopulationResource(BaseResource parent, SailPointObject accessItem) {
        super(parent);
        this.accessItem = accessItem;
    }

    /**
     * Creates a properly initialized IdentityListService
     */
    private IdentityListService createIdentityListService()
    {
        ListServiceColumnSelector selector = new BaseListResourceColumnSelector(COLUMN_CONFIG_NAME);
        return new IdentityListService(getContext(), this, selector);
    }

    /**
     * Get identities that match
     *
     * @param showNonMatched boolean If true return unmatched identities. if false or undefined show matched
     * @return list of identities that match
     */
    @GET
    public ListResult getIdentities(@QueryParam("showNonMatched") Boolean showNonMatched) throws GeneralException {
        UserAccessUtil userAccessUtil = new UserAccessUtil(getContext(), getLocale());
        List<Filter> identityFilters = userAccessUtil.getIdentityFilters(getQueryParamMap());
        sessionFilter = Filter.and(identityFilters);

        // Setup the identity list service
        IdentityListService service = createIdentityListService();
        String quicklinkName = (String) request.getSession().getAttribute(LCMConfigService.ATT_LCM_CONFIG_SERVICE_QUICKLINK);

        return service.getMatchingManagedIdentities(quicklinkName, accessItem, showNonMatched);
    }

    @Override
    public boolean isCurrentUserFirst() {
        return false;
    }

    @Override
    public boolean isRemoveCurrentUser() {
        return true;
    }

    @Override
    public Filter getSessionFilter() {
        return this.sessionFilter;
    }

    @Override
    public String getQuickLink() {
       return (String) request.getSession().getAttribute(LCMConfigService.ATT_LCM_CONFIG_SERVICE_QUICKLINK);
    }

    @Override
    public Filter getNameFilter() {
        return null;
    }

    @Override
    public Filter getIdFilter() {
        return null;
    }
}