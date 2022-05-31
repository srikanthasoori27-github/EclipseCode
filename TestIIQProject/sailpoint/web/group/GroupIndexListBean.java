/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * 
 */
package sailpoint.web.group;

import sailpoint.object.GroupIndex;
import sailpoint.tools.GeneralException;
import sailpoint.web.BaseListBean;

/**
 * @author peter.holcomb
 *
 */
public class GroupIndexListBean extends BaseListBean<GroupIndex> {

    /**
     * 
     */
    public GroupIndexListBean() {
        super();
        setScope(GroupIndex.class);
    }
    
    ////////////////////////////////////////////////////////////////////////////
    //
    // BaseListBean overrides
    //
    ////////////////////////////////////////////////////////////////////////////

    @Override
    public String getDefaultSortColumn() throws GeneralException {
        return "name";
    }

}
