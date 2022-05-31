package sailpoint.service;

import sailpoint.object.ColumnConfig;
import sailpoint.object.UIConfig;
import sailpoint.tools.GeneralException;

import java.util.ArrayList;
import java.util.List;

/**
 * Injectable component which allows selection of columns based on a UI config key.
 */
public class BaseListResourceColumnSelector implements ListServiceColumnSelector {

    private String columnsKey;
    
    public BaseListResourceColumnSelector(String columnsKey) 
    {
        this.columnsKey = columnsKey;
    }
    
    /**
     * Gets the list of projection columns for a resource based on the column config in the UI config with the
     * specified columnsKey.
     *
     * @return the list of projection columns
     */
    @Override
    public List<String> getProjectionColumns() throws GeneralException {
        List<String> projectionColumns = null;
        List<ColumnConfig> columns = getColumns();

        if (columns != null) {
            projectionColumns = new ArrayList<String>();
            for (ColumnConfig col : columns) {
                // Only add non-calculated columns 
                if (col.getProperty() != null) {
                    projectionColumns.add(col.getProperty());
                }
            }
        }
        return projectionColumns;
    }

    /**
     * @return the column key
     */
    public String getColumnsKey() {
        return this.columnsKey;
    }

    /**
     * Gets a list of column config objects out of the UIConfig object using columnsKey.
     * @return list of column configs
     * @throws GeneralException
     */
    @Override
    @SuppressWarnings("unchecked")
    public List<ColumnConfig> getColumns() throws GeneralException{
        UIConfig uiConfig = UIConfig.getUIConfig();
        List<ColumnConfig> columns = null;
        if (uiConfig != null && columnsKey != null) {
            columns = uiConfig.getAttributes().getList(columnsKey);
            if (columns == null && !uiConfig.getAttributes().containsKey(columnsKey)) {
                throw new GeneralException("Unable to locate column config for [" + columnsKey + "].");
            }
        }
        return columns;
    }

    /**
     * Add a column to the list of projection columns, if not already there.
     *
     * @param columnName Column to add to list of projection columns
     * @param projectionColumns List of projection columns
     */
    protected static void addColumnToProjectionList(String columnName, List<String> projectionColumns) {
        if (projectionColumns != null && !projectionColumns.contains(columnName)) {
            projectionColumns.add(columnName);
        }
    }

}
