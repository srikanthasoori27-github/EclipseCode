/* (c) Copyright 2011 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.identity;

import sailpoint.object.IdentityEntitlement;
import sailpoint.tools.GeneralException;
import sailpoint.web.util.NavigationHistory;
import sailpoint.web.*;

/**
 * 
 *
 * @author dan.smith
 */
public class IdentityEntitlementListBean extends BaseListBean<IdentityEntitlement> 
       implements NavigationHistory.Page {

    public IdentityEntitlementListBean() {
        super();
    }
    
    public String getPageName() {
        return "Identity Entitlement List";
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
    
    /** Returns a grid meta data that can be used to build grid column configs and provide
     * the list of fields to the datasource.
     * @return
     * @throws GeneralException
  
    public String getRoleColumnJSON() throws GeneralException{
        ArrayList<ColumnConfig> columns = null;
        
            Object cols = getUIConfig().getAttributes().get("identityEntitlementRolesGridColumns");
            if ((null != cols) && (cols instanceof List)) {
                // Could look at the first element to make sure it is the right
                // type.
                columns = new ArrayList<ColumnConfig>();
                for(ColumnConfig config : (List<ColumnConfig>) cols) {
                    if(!config.isFieldOnly()) {
                        columns.add(config);
                    } else {
                        this.addFieldColumn(config);
                    }
                }
            }
        
        return getColumnJSON(null, columns);
    }
       */
}
