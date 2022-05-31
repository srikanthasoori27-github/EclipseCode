package sailpoint.rest;

import sailpoint.integration.ListResult;
import sailpoint.object.UIConfig;
import sailpoint.service.BaseListResourceColumnSelector;
import sailpoint.service.BaseListServiceContext;
import sailpoint.service.EffectiveAccessListService;
import sailpoint.tools.GeneralException;

import javax.ws.rs.GET;

/**
 * Created by ryan.pickens on 5/27/16.
 *
 */
public class TargetAssociationListResource extends BaseListResource implements BaseListServiceContext {
    private static final String DEFAULT_LIST_COL_KEY = UIConfig.ACCOUNT_GROUP_ACCESS_TABLE_COLUMNS;
    
    private String _objectId;

    public TargetAssociationListResource(String objectId, BaseResource parent) {
        super(parent);
        _objectId = objectId;
    }

    @GET
    public ListResult getAssociations() throws GeneralException {
        String listColKey = (this.colKey != null) ? this.colKey : DEFAULT_LIST_COL_KEY;
        EffectiveAccessListService svc = new EffectiveAccessListService(getContext(), this, new BaseListResourceColumnSelector(listColKey));
        return svc.getEffectiveAccess(_objectId);
    }

}
