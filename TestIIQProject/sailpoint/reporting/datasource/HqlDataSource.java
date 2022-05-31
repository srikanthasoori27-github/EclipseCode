/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.reporting.datasource;

import sailpoint.object.LiveReport;
import sailpoint.object.QueryOptions;
import sailpoint.object.ReportColumnConfig;
import sailpoint.object.Sort;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/**
 * @author jonathan.bryant@sailpoint.com
 */
public class HqlDataSource extends BaseProjectionDataSource{


    private Map<String,Object> args;
    private String query;
    private LiveReport report;

    public HqlDataSource(LiveReport report, Class objectType, String query, QueryOptions ops,
                         List<ReportColumnConfig> columns, Map<String,Object> args,
                                   Locale locale, TimeZone timezone) {
       super(objectType, ops, columns, locale, timezone);
       this.ops = ops;
       this.objectType = objectType;
       this.args = args;
       this.query = query;
       this.report = report;
    }

    @Override
    protected Iterator prepare() throws GeneralException {

        prepareProjectionColumns();

        String fullQuery = query;
        if (projectionColumns != null && !projectionColumns.isEmpty()){
            fullQuery = "select " + Util.listToCsv(projectionColumns) + " " + fullQuery;
        }

        if (report.getDataSource().getSortOrder() != null){
            String orderByClause = "";
            for(Sort sort : report.getDataSource().getSortOrder()){
               if ("".equals(orderByClause))
                   orderByClause = " order by ";
               else
                   orderByClause += ", ";
               String sortDir = sort.isAscending() ? " asc " : " desc";
               ReportColumnConfig config = report.getGridColumnByFieldName(sort.getField());
               orderByClause += config.getProperty() + " " + sortDir;
            }
            fullQuery += orderByClause;
         }

        if (report.getDataSource().getGroupBy() != null){
            String groupByClause = "";
            if (report.getDataSource().getGroupBy() != null){
               for(String groupBy : report.getDataSource().getGroupByColumns()){
                   if (!"".equals(groupByClause))
                       groupByClause += ", ";
                  groupByClause += groupBy;
               }

               if (groupByClause.length() > 0)
                 groupByClause = " group by " + groupByClause;
            }
            fullQuery += groupByClause;
        }

        return getContext().search(fullQuery, args, ops);
    }

    public QueryOptions getBaseQueryOptions() {
        return null;
    }

    public String getBaseHql() {
        return query;
    }

    @Override
    public int getSizeEstimate() throws GeneralException {

        int count = 0;

        String q = "select count(*) " + query;
        Iterator iter = getContext().search(q, args, new QueryOptions());
        if(iter.hasNext()){
            Object row = iter.next();
            if (row != null){
                long l = (Long)row;
                count = (int)l;
            }
        }

        return count;
    }
}
