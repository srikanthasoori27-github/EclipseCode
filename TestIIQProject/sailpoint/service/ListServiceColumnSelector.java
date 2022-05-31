package sailpoint.service;

import sailpoint.object.ColumnConfig;
import sailpoint.tools.GeneralException;

import java.util.List;

/**
 * Encapsulates column config and projection column selection for list services. Clients of BaseListService
 * can implement this interface to provide custom column selection.
 */
public interface ListServiceColumnSelector {

    /**
     * Gets the list of projection columns for a resource.
     *
     * @return the list of projection columns
     */
    List<String> getProjectionColumns() throws GeneralException;

    /**
     * Gets a list of column config objects out of the UIConfig.
     * @return list of column configs
     * @throws GeneralException
     */
    List<ColumnConfig> getColumns() throws GeneralException;

    /**
     * @return the column key
     */
    String getColumnsKey();
}
