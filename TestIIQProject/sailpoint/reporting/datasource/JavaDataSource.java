/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.reporting.datasource;

import java.util.List;

import sailpoint.api.SailPointContext;
import sailpoint.object.Attributes;
import sailpoint.object.LiveReport;
import sailpoint.object.Sort;
import sailpoint.tools.GeneralException;

/**
 * This class allows a java datasource implement to be used with the
 * GridReportExecutor.
 *
 * @author jonathan.bryant@sailpoint.com
 */
public interface JavaDataSource extends LiveReportDataSource {

    /**
     * Used by the executor to pass the report definition
     * arguments to the datasource.
     *
     * @param arguments Task Definition arguments for the report
     * @param groupBy Field name to group the report data by.
     * @param sort    List of Sort objects definition the sort order on this
     * report.
     */
    void initialize(SailPointContext context, LiveReport report, Attributes<String, Object> arguments,
                    String groupBy, List<Sort> sort)
            throws GeneralException;


    /**
     *
     * @param startRow Row number First row to start with in the resultset.
     * @param pageSize Number of rows to include in the result set, or zero to include all.
     */
    void setLimit(int startRow, int pageSize);
}
