/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web;

import java.util.List;

import sailpoint.object.ColumnConfig;
import sailpoint.object.Request;
import sailpoint.object.UIConfig;
import sailpoint.tools.GeneralException;

public class RequestListBean extends BaseListBean<Request> {
    private List<ColumnConfig> columns;

    public RequestListBean() {
        super();
        setScope(Request.class);
    }

    @Override
    public String getDefaultSortColumn() throws GeneralException {
        return "name";
    }
    
    @Override
    public List<ColumnConfig> getColumns() throws GeneralException {
        if (this.columns == null) {
            columns = UIConfig.getUIConfig().getRequestsTableColumns();
        }
        
        return columns;
    }
}
