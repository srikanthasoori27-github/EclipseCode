/**
 * 
 */
package sailpoint.search;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.Filter;
import sailpoint.object.Filter.LeafFilter;
import sailpoint.tools.GeneralException;

/**
 * @author peter.holcomb
 *
 */
public class AccountGroupFilterBuilder extends BaseFilterBuilder {
    
    private static final Log log = LogFactory.getLog(AccountGroupFilterBuilder.class);
    
    private static final String NAME_TARGET = "accountGroup.target";
    private static final String PROPERTY_PERMISSIONS_TARGET = "permissions.target";
    private static final String PROPERTY_TARGET_PERMISSIONS_TARGET="targetPermissions.target";
    
    private static final String NAME_RIGHTS = "accountGroup.rights";
    private static final String PROPERTY_PERMISSIONS_RIGHTS = "permissions.rights";
    private static final String PROPERTY_TARGET_PERMISSIONS_RIGHTS="targetPermissions.rights";
    
    @Override
    public Filter getFilter() throws GeneralException {
        Filter filter = getFilterByType();
        
        /** If we are searching over the account_group_perms table, we need to or
         * the filter with the account_group_target_perms table as well
         */
        if(name.equals(NAME_TARGET) || name.equals(NAME_RIGHTS)) {
            String property = null;
            if(name.equals(NAME_TARGET))
                property = PROPERTY_TARGET_PERMISSIONS_TARGET;
            else
                property = PROPERTY_TARGET_PERMISSIONS_RIGHTS;
            
            LeafFilter filter1 = (LeafFilter)filter;            
            LeafFilter filter2 = new LeafFilter(filter1.getOperation(), property, filter1.getValue(), filter1.getMatchMode());
            
            if(ignoreCase) {
                filter = Filter.or(Filter.ignoreCase(filter1),Filter.ignoreCase(filter2));
            } else {
                filter = Filter.or(filter1, filter2);
            }
        }
        
        
        
        return filter;
    }

}
