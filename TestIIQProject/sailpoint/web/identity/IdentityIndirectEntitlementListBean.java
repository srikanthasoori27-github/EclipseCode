package sailpoint.web.identity;

import sailpoint.object.TargetAssociation;
import sailpoint.object.UIConfig;
import sailpoint.web.BaseListBean;

/**
 * Created by ryan on 5/30/16.
 */
public class IdentityIndirectEntitlementListBean extends BaseListBean<TargetAssociation> {

    public String getColumnConfigKey() {
        return UIConfig.IDENTITY_INDIRECT_ENTITLEMENT_COLUMNS;
    }

}
