/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.view;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import sailpoint.integration.ListResult;
import sailpoint.object.ColumnConfig;
import sailpoint.object.Filter;
import sailpoint.object.QueryOptions;
import sailpoint.object.SailPointObject;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.web.extjs.GridColumn;
import sailpoint.web.extjs.GridField;
import sailpoint.web.extjs.GridResponseMetaData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author jonathan.bryant@sailpoint.com
 */
public class ViewBuilder {

    private static final Log LOG = LogFactory.getLog(ViewBuilder.class);

    private ViewEvaluationContext viewContext;
    private List<ColumnConfig> columnConfig;
    private List<ViewColumn> columns;
    private List<String> additionalProjectionColumns;
    private Class scope;


    public ViewBuilder(ViewEvaluationContext viewContext, Class<? extends SailPointObject> scope,
                       List<ColumnConfig> columnConfig) {
        
        this(viewContext, scope, columnConfig, null);
    }

    public ViewBuilder(ViewEvaluationContext viewContext, Class<? extends SailPointObject> scope,
                       List<ColumnConfig> columnConfig, List<String> additionalProjectionColumns) {

        this.viewContext = viewContext;
        this.columnConfig = columnConfig;
        this.scope = scope;
        this.additionalProjectionColumns = additionalProjectionColumns;
        this.columns = new ArrayList<ViewColumn>();

        for(ColumnConfig col : columnConfig){
            ViewColumn eval = null;
            if (col.getEvaluator() != null){
                try {
                    Object evaluator = Class.forName(col.getEvaluator()).newInstance();
                    eval = (ViewColumn)evaluator;
                } catch (Throwable e) {
                    throw new RuntimeException("Could not create a column evaluator of type '"+col.getEvaluator()+"'");
                }
            } else {
                eval = new DefaultColumn();
            }
            eval.init(viewContext, col);
            columns.add(eval);
        }
    }

    public ListResult getResult(QueryOptions ops) throws GeneralException{

        int totalCount = viewContext.getSailPointContext().countObjects(scope, ops);

        List objects = new ArrayList();
        if (totalCount > 0){

            List<String> columns = getProjectionColumns();

            List<Filter> evaluatorFilters = getEvaluatorFilters();
            for(Filter f : evaluatorFilters){
                ops.add(f);
            }

            Iterator<Object[]> iter = viewContext.getSailPointContext().search(scope, ops, columns);
            while(iter != null && iter.hasNext()){
                Map<String, Object> rowMap = buildRowMap(columns, iter.next());
                Map view = getView(rowMap);
                objects.add(view);
                viewContext.clearRowAttributes();
            }
        }

        for(ViewColumn evaluator : columns){
            evaluator.afterRender();
        }

        return new ListResult(objects, totalCount);
    }

    public GridResponseMetaData calculateGridMetaData(){
        GridResponseMetaData meta = new GridResponseMetaData();
        meta.setRoot("objects");
        meta.setTotalProperty("count");
        // the columns may have been updated by the column evaluators
        List<ColumnConfig> updatedColumns = viewContext.getColumns();
        if (updatedColumns != null){
            for (ColumnConfig col : updatedColumns){
                meta.addField(new GridField(col.getJsonProperty()));
                if (!col.isFieldOnly()){
                    meta.addColumn(new GridColumn(col));
                }
            }
        }

        return meta;
    }

    //---------------------------------------------------------------
    //
    //  Private Methods
    //
    //---------------------------------------------------------------

    /**
     * Create a view map using the projection query result.
     */
    public Map<String, Object> getView(Map<String, Object> row) throws GeneralException{
        Map<String, Object> view = new HashMap<String, Object>();

        // Put all the values in the map first and then let the evaluator override if needed
        for (String additionalColumn : Util.safeIterable(this.additionalProjectionColumns)) {
            view.put(additionalColumn, row.get(additionalColumn));
        }
        
        int idx = 0;
        for(ColumnConfig col : columnConfig){
            ViewColumn evaluator = this.columns.get(idx);
            view.put(col.getJsonProperty(), evaluator.getValue(row));
            idx++;
        }

        return view;
    }

    /**
     * Calculate the columns required to run the initial projection
     */
    private List<String> getProjectionColumns() throws GeneralException{
        Set<String> projectionColumns = new HashSet<String>();

        for(ViewColumn evaluator : columns){
            List<String> evalCols = evaluator.getProjectionColumns();
            if (evalCols != null)
                projectionColumns.addAll(evalCols);
        }
        
        if (!Util.isEmpty(this.additionalProjectionColumns)) {
            projectionColumns.addAll(this.additionalProjectionColumns);
        }

        List<String> list = new ArrayList<String>();
        list.addAll(projectionColumns);
        return list;
    }

    /**
     * Collect the filters required by all evaluators used in this this view.
     */
    private List<Filter> getEvaluatorFilters() throws GeneralException{
        List<Filter> filters = new ArrayList<Filter>();

        for(ViewColumn evaluator : columns){
            Filter f = evaluator.getFilter();
            if (f != null)
                filters.add(f);
        }

        return filters;
    }

    /**
     * Convert a projection result into a map using the columns names.
     */
    private Map<String, Object> buildRowMap(List<String> columns, Object[] result){
        Map<String, Object> row = new HashMap<String, Object>();
        int idx = 0;
        for(String col : columns){
            row.put(col, result[idx]);
            idx++;
        }
        return row;
    }
}
