/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.view;

import sailpoint.object.ColumnConfig;
import sailpoint.object.Filter;
import sailpoint.tools.GeneralException;

import java.util.List;
import java.util.Map;

/**
 * @author jonathan.bryant@sailpoint.com
 */
public interface ViewColumn {

    /**
     * Initializes evaluator
     * @param context
     * @param conf
     */
    void init(ViewEvaluationContext context, ColumnConfig conf);

    /**
     * Filter to be added to builder query for this column.
     * @return Filter object or null
     * @throws GeneralException
     */
    Filter getFilter() throws GeneralException;

    /**
     * List of columns names to include in the builder's projection query.
     * @return Column list
     * @throws GeneralException
     */
    List<String> getProjectionColumns()  throws GeneralException;

    /**
     * Get the value from the current row.
     * @param row Tow returned by the projection query
     * @return Row value
     * @throws GeneralException
     */
    Object getValue(Map<String, Object> row) throws GeneralException;

    /**
     * Allow the column evaluator to perform any changes to
     * the grid after we've iterated through all the records.
     * This could include things like adjusting the ColumnConfig list.
     */
    void afterRender();

}
