/* (c) Copyright 2016 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.service;

import sailpoint.object.ColumnConfig;
import sailpoint.tools.GeneralException;

import java.util.Collections;
import java.util.List;

/**
 * Implementation of ListServiceColumnSelector for list services that will construct
 * their own objects and only need the object id.
 */
public class IdColumnSelector implements ListServiceColumnSelector {

    public static final String ID_COLUMN = "id";
    
    @Override
    public List<String> getProjectionColumns() throws GeneralException {
        return Collections.singletonList(ID_COLUMN);
    }

    @Override
    public List<ColumnConfig> getColumns() throws GeneralException {
        return Collections.singletonList(new ColumnConfig(ID_COLUMN, ID_COLUMN));
    }

    @Override
    public String getColumnsKey() {
        return null;
    }
}
