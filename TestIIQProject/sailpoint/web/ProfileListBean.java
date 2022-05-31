/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 *
 */
package sailpoint.web;

import sailpoint.object.Profile;
import sailpoint.object.QueryOptions;
import sailpoint.tools.GeneralException;

/**
 *
 */
public class ProfileListBean extends BaseListBean<Profile> {    
    /**
     *
     */
    public ProfileListBean() {
        super();
        setScope(Profile.class);
    }  // ProfileListBean() 
    
    @Override
    public QueryOptions getQueryOptions() throws GeneralException {
        QueryOptions qo = super.getQueryOptions();
        
        String sortOrderStr = (String) getRequestParam().get("s2");
        boolean sortOrder;
        if (null == sortOrderStr) {
            sortOrder = true;
        } else {
            sortOrder = "ASC".equals(sortOrderStr);
        }

        qo.addOrdering("name", sortOrder);
        return qo;
    }
}  // class ProfileListBean
