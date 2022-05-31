/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.reporting.datasource;

import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.IncrementalProjectionIterator;
import sailpoint.object.QueryOptions;
import sailpoint.object.ReportColumnConfig;
import sailpoint.tools.GeneralException;


/**
 * @author jonathan.bryant@sailpoint.com
 */
public class ProjectionDataSource extends BaseProjectionDataSource{

    private static final Log log = LogFactory.getLog(ProjectionDataSource.class);

    public ProjectionDataSource() {
        super();
    }

    public ProjectionDataSource(Class objectType,  QueryOptions ops,  List<ReportColumnConfig> columns,
                                Locale locale, TimeZone timezone) {
        super(objectType, ops, columns, locale, timezone);
        this.ops = ops;
        this.objectType = objectType;
        this.columns = columns;

        columnHelper = new DataSourceColumnHelper(locale, timezone);
    }

    protected Iterator prepare() throws GeneralException {

        prepareProjectionColumns();

        if (ops.getResultLimit() == 0){

            int size = getContext().countObjects(objectType, ops);
            Integer max = getMaxRowSize();
            if (max != null && size > max.intValue()){
                return new IncrementalProjectionIterator(getContext(),  objectType, ops,
                        projectionColumns);
            }
        }

        //TODO: Use cloneResults or always return IncrementalProjectionIterator?
        // With creating a new context for the datasource, do we need to clone? -rap
        // ops.setCloneResults(true);
        return getContext().search(objectType, ops, projectionColumns);
    }

    @Override
    public int getSizeEstimate() throws GeneralException {
        return getContext().countObjects(objectType, ops);
    }

    public QueryOptions getBaseQueryOptions() {
        return ops;
    }

    public String getBaseHql() {
        return null;
    }

}
