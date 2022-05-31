/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.reporting.datasource;

import sailpoint.object.QueryOptions;
import sailpoint.tools.GeneralException;

/**
 * Extension of the TopLevelDataSource interface that allows
 * us to execute the datasource outside of Jasper and
 * allows us to get an idea of the size of the data source's
 * dataset.
 *
 * @author jonathan.bryant@sailpoint.com
 */
public interface LiveReportDataSource extends TopLevelDataSource{

    /**
     * Returns the number of rows in the dataset
     * this data source will return.
     *
     * @return Number of rows in this datasource
     * @throws GeneralException
     */
    int getSizeEstimate() throws GeneralException;

    /**
     * Returns the base QueryOptions for this report. This is
     * used when generating a chart for the report. When creating
     * the baseQueryOptions, the paging parameters (resultLimit and firstRow)
     * should be null.
     */
    QueryOptions getBaseQueryOptions();

    /**
     * Returns the base hql string for this report. This is used
     * by to create a chart based on the report data. This can be null
     * if no chart is needed.
     */
    String getBaseHql();

    /**
     * This method should work exactly like getFieldValue defined in JRDatasource,
     * with the exception that we can pass a field name string rather than a
     * JRField object. This is used in cases where we want to evaluate the
     * datasource outside of a Jasper context.
     *
     * @param field Name of the field to retrieve from the datasource.
     * @return Field value
     * @throws GeneralException
     */
    public Object getFieldValue(String field) throws GeneralException;

}
