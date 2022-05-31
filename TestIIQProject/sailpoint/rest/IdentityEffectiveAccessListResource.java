package sailpoint.rest;

import sailpoint.integration.ListResult;
import sailpoint.integration.Util;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.QueryOptions;
import sailpoint.service.BaseListResourceColumnSelector;
import sailpoint.service.BaseListServiceContext;
import sailpoint.service.EffectiveAccessListService;
import sailpoint.tools.GeneralException;

import javax.ws.rs.GET;
import javax.ws.rs.QueryParam;

/**
 * Created by ryan.pickens on 5/27/16.
 */
public class IdentityEffectiveAccessListResource extends BaseListResource implements BaseListServiceContext {

    public final static String IDENTITY_ENTITLEMENT_INDIRECT_COL = "identityEntitlementIndirectGridColumns";
    private String _identId;

    public IdentityEffectiveAccessListResource(String identityId, BaseResource parent) {
        super(parent);
        _identId = identityId;
    }

    @GET
    public ListResult getEffectiveAccess(@QueryParam("appOrValue") String appOrValue) throws GeneralException {
        if (colKey == null) {
            colKey = IDENTITY_ENTITLEMENT_INDIRECT_COL;
        }
        EffectiveAccessListService svc = new EffectiveAccessListService(getContext(), this, new BaseListResourceColumnSelector(colKey));
        //Need to ensure we have id
        Identity id = getContext().getObjectById(Identity.class, _identId);
        if (id == null) {
            throw new GeneralException("Could not find Identity " + _identId);
        }

        QueryOptions ops = getQueryOptions();

        if (Util.isNotNullOrEmpty(appOrValue)) {
            ops.add(Filter.or(Filter.ignoreCase(Filter.like("targetName", appOrValue)),
                    Filter.ignoreCase(Filter.like("applicationName", appOrValue))));
        }
        return svc.getIdentityEffectiveAccess(id.getId(), ops);
    }
}
