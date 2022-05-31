/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.web.chart;

import sailpoint.api.SailPointContext;
import sailpoint.object.Attributes;
import sailpoint.object.Chart;
import sailpoint.object.LiveReport;
import sailpoint.object.QueryOptions;
import sailpoint.object.ReportColumnConfig;
import sailpoint.object.Script;
import sailpoint.object.Sort;
import sailpoint.reporting.ReportHelper;
import sailpoint.reporting.datasource.DataSourceColumnHelper;
import sailpoint.reporting.datasource.LiveReportDataSource;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Internationalizer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/**
 * @author jonathan.bryant@sailpoint.com
 */
public class ChartHelper {

    private SailPointContext context;
    private Locale locale;
    private TimeZone timezone;
    private DataSourceColumnHelper columnHelper;
    private ReportHelper reportHelper;

    public ChartHelper(SailPointContext context, Locale locale, TimeZone timezone) {
        this.context = context;
        this.locale = locale;
        this.timezone = timezone;

        this.columnHelper = new DataSourceColumnHelper(locale, timezone);
        this.reportHelper = new ReportHelper(context, locale, timezone);
    }

    public Chart initReportChart(LiveReport report, LiveReportDataSource ds, Attributes<String, Object> args) throws GeneralException {

        Chart chart = null;

        if (report.getChart() != null){

            chart = (Chart)report.getChart().deepCopy(context);

            Attributes<String, Object> chartDsArgs  = new Attributes<String, Object>();
            chartDsArgs.put("context", context);
            chartDsArgs.put("args", args);
            chartDsArgs.put("report", report);

            String baseHql = ds.getBaseHql();

            if (baseHql != null)
                chartDsArgs.put("baseHql", baseHql);

            QueryOptions baseOpts = null;
            if (ds.getBaseQueryOptions() != null){
                baseOpts = new QueryOptions(ds.getBaseQueryOptions());
                baseOpts.setOrderings(new ArrayList<QueryOptions.Ordering>()); // remove any ordering.
                chartDsArgs.put("baseQueryOptions", baseOpts);
            }

            List<Map<String, Object>> chartData = null;
            if (chart.getDataSourceRule() != null){
                chartData = (List<Map<String, Object>>)context.runRule(chart.getDataSourceRule(),
                        chartDsArgs);
            } else if (chart.getScript() != null){
                Script dsScript = chart.getScript();
                chartData = (List<Map<String, Object>>)context.runScript(dsScript, chartDsArgs);
            } else {

                chartData = new ArrayList<Map<String, Object>>();

                if (baseOpts != null){
                    for(String groupBy : chart.getGroupByList()){
                        baseOpts.addGroupBy(groupBy);
                    }

                    if (chart.getSortBy() != null){
                        for(Sort sort : chart.getSortBy()){
                            baseOpts.addOrdering(sort.getField(), sort.isAscending());
                        }
                    }

                    if (chart.getLimit() > 0)
                        baseOpts.setResultLimit(chart.getLimit());

                    List<String> fields = new ArrayList<String>();
                    fields.add(chart.getValue());
                    fields.add(chart.getCategory());
                    if (chart.getSeries() != null){
                        fields.add(chart.getSeries());
                    }

                    Iterator<Object[]> results = context.search(report.getDataSource().getObjectClass(), baseOpts, fields);

                    chartData = createChartData(report.getChart(), results);
                } else if (baseHql != null){

                    List<String> fields = new ArrayList<String>();
                    fields.add(chart.getValue());
                    fields.add(chart.getCategory());
                    if (chart.getSeries() != null){
                        fields.add(chart.getSeries());
                    }

                    String fullQuery = "select " + sailpoint.tools.Util.listToCsv(fields) + " " + baseHql;

                    QueryOptions ops = new QueryOptions();
                    if (chart.getLimit() > 0)
                        ops.setResultLimit(chart.getLimit());

                    if (chart.getSortBy() != null){
                        String orderByClause = "";
                        for(Sort sort : chart.getSortBy()){
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

                    String groupByClause = "";
                    if (chart.getGroupBy() != null){
                       for(String groupBy : chart.getGroupByList()){
                           if (!"".equals(groupByClause))
                               groupByClause += ", ";
                          groupByClause += groupBy;
                       }

                       if (groupByClause.length() > 0)
                            groupByClause = " group by " + groupByClause;
                    }
                    fullQuery += groupByClause;

                    Map<String, Object> queryArgs = reportHelper.getHqlQueryArgs(report.getDataSource(), args);

                    Iterator<Object[]> results = context.search(fullQuery, queryArgs, baseOpts);
                    chartData = createChartData(report.getChart(), results);
                }
            }

            chart.setData(chartData);

            chart = this.localizeChart(chart);
        }

        return chart;
    }

    private List<Map<String, Object>> createChartData(Chart chart, Iterator<Object[]> results){
        List<Map<String, Object>> chartData = new ArrayList<Map<String, Object>>();
        while(results.hasNext()){
            Object[] result = results.next();
            Map<String, Object> row = new HashMap<String, Object>();
            row.put("value", result[0]);
            if (result[1] != null){
                row.put("category", columnHelper.getColumnValue(result[1], null));
            } else if (chart.getNullCategory() != null) {
                row.put("category", columnHelper.getColumnValue(chart.getNullCategory(), null));
            }
            if (result.length > 2){
                if (result[2] != null){
                    row.put("series", columnHelper.getColumnValue(result[2], null));
                } else if (chart.getNullSeries() != null){
                    row.put("series", columnHelper.getColumnValue(chart.getNullSeries(), null));
                } else {
                    // Avoid null series since this will cause an exception
                    row.put("series", "");
                }
            }
            chartData.add(row);
        }

        return chartData;
    }

    public Chart localizeChart(Chart chart) throws GeneralException{
        if (chart != null){
            if (chart.getTitle() != null){
                chart.setTitle(localize(chart.getTitle()));
            }

            if (chart.getData() != null){
                for(Map<String, Object> row : chart.getData()){
                    if (row.containsKey("category") && row.get("category") != null
                            && row.get("category") instanceof String){
                        row.put("category", localize((String)row.get("category")));
                    }

                    if (row.containsKey("series") && row.get("series") != null
                            && row.get("series") instanceof String){
                        row.put("series", localize((String)row.get("series")));
                    }
                }
            }

            if (chart.getLeftLabel() != null){
                chart.setLeftLabel(localize(chart.getLeftLabel()));
            }

            if (chart.getBottomLabel() != null){
                chart.setBottomLabel(localize(chart.getBottomLabel()));
            }
        }

        return chart;
    }

    private String localize(String msg){
        if (msg != null){
            String localized = Internationalizer.getMessage(msg, locale);
            if (localized != null)
                return localized;
        }

        return msg;
    }

}
