/* (c) Copyright 2011 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.identity;

import sailpoint.object.IdentityEntitlement;
import sailpoint.web.util.NavigationHistory;
import sailpoint.web.*;

/**
 * 
 *
 * @author dan.smith
 */
public class IdentityEntitlementRoleListBean extends BaseListBean<IdentityEntitlement> 
       implements NavigationHistory.Page {

    public IdentityEntitlementRoleListBean() {
        super();
    }
    
    public String getPageName() {
        return "Identity Entitlement Role List";
    }

    public String getNavigationString() {
        return "viewIdentityEntitlements";
    }

    public Object calculatePageState() {
        // TODO Auto-generated method stub
        return null;
    }

    public void restorePageState(Object state) {
        // TODO Auto-generated method stub
    }
}
