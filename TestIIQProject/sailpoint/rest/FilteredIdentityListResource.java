/* (c) Copyright 2015 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.rest;

import java.util.List;
import java.util.Map;

import javax.ws.rs.GET;

import sailpoint.integration.ListResult;
import sailpoint.tools.Message;
import sailpoint.object.Bundle;
import sailpoint.object.Filter;
import sailpoint.object.ManagedAttribute;
import sailpoint.service.BaseListResourceColumnSelector;
import sailpoint.service.IdentityListServiceContext;

import sailpoint.object.SailPointObject;
import sailpoint.service.IdentityListService;
import sailpoint.service.ListServiceColumnSelector;
import sailpoint.tools.GeneralException;
import sailpoint.tools.ObjectNotFoundException;
import sailpoint.tools.Util;
import sailpoint.web.util.WebUtil;

/**
 * Classic resource to provide identities that match population search for roles and entitlements.
 *
 * @author patrick.jeong
 */
public class FilteredIdentityListResource extends BaseListResource implements IdentityListServiceContext {

    public final String PARAM_QUICK_LINK_NAME = "quickLink";

    String roleId;
    String entitlementId;
    Boolean showNonMatched;
    Filter sessionFilter;
    String quickLinkName;

    /**
     * Resource constructor
     *
     * @param parent parent resource
     * @param roleId role id
     * @param entitlementId entitlement id
     * @param showNonMatched true to show non matched false to show matched
     */
    public FilteredIdentityListResource(BaseResource parent, String roleId, String entitlementId, Boolean showNonMatched) {
        super(parent);

        this.roleId = roleId;
        this.entitlementId = entitlementId;
        this.showNonMatched = showNonMatched;
        this.quickLinkName = getOtherQueryParams().get(PARAM_QUICK_LINK_NAME);
    }

    /**
     * Get a list of identities filtered by search params
     *
     * @return list of identities that match
     * @throws GeneralException
     */
    @GET
    public ListResult getFilteredIdentities() throws GeneralException {

        SailPointObject roleOrEntitlement = getAccessItem();

        IdentitySearchUtil searchUtil = new IdentitySearchUtil(getContext());
        List<Filter> identityFilters = searchUtil.getIdentityFilters(request, null);
        if (!identityFilters.isEmpty()) {
            sessionFilter = Filter.and(identityFilters);
        }

        // Setup the identity list service
        ListServiceColumnSelector selector = new BaseListResourceColumnSelector(colKey);
        IdentityListService service = new IdentityListService(getContext(), this, selector);

        ListResult result = service.getMatchingManagedIdentities(quickLinkName, roleOrEntitlement, showNonMatched);

        List<Map<String, Object>> resultsList = result.getObjects();
        int total = result.getCount();

        /** Add the pretty colors! **/
        for(Map<String,Object> resultMap : resultsList) {
            if(resultMap != null) {
                int score = Util.getInt(resultMap, Util.getJsonSafeKey("scorecard.compositeScore"));
                resultMap.put("IIQ_color", WebUtil.getScoreColor(score));
            }
        }

        return new ListResult(resultsList, total);
    }

    /**
     * Get the access item represented by the ID. Should be either a Bundle or a ManagedAttribute
     *
     * @return SailPointObject, either a Bundle or ManagedAttribute
     * @throws GeneralException if roleId or entitlementId does not refer to valid Bundle or ManagedAttribute
     */
    private SailPointObject getAccessItem() throws GeneralException {
        SailPointObject roleOrEntitlement = null;
        if (Util.isNotNullOrEmpty(roleId)) {
            roleOrEntitlement = getContext().getObjectById(Bundle.class, roleId);
        }
        else if (Util.isNotNullOrEmpty(entitlementId)) {
            roleOrEntitlement = getContext().getObjectById(ManagedAttribute.class, entitlementId);
        }

        if (roleOrEntitlement == null) {
            throw new ObjectNotFoundException(new Message("Unable to find matching Bundle or ManagedAttribute"));
        }

        return roleOrEntitlement;
    }

    @Override
    public boolean isCurrentUserFirst()  {
        return false;
    }

    @Override
    public boolean isRemoveCurrentUser() {
        return true;
    }

    @Override
    public Filter getNameFilter() {
        return null;
    }

    @Override
    public Filter getIdFilter() {
        return null;
    }

    // Sorting never worked for classic so we're returning null here to avoid blowing up from the IIQ_population sort value
    @Override
    public String getSortBy() {
        return null;
    }

    @Override
    public Filter getSessionFilter() {
        return this.sessionFilter;
    }

    @Override
    public String getQuickLink() {
        return quickLinkName;
    }
}
