/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.reporting.datasource;

import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JRField;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import sailpoint.object.Attributes;
import sailpoint.object.Configuration;
import sailpoint.object.Filter;
import sailpoint.object.QueryOptions;
import sailpoint.object.ReportColumnConfig;
import sailpoint.tools.GeneralException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/**
 * @author jonathan.bryant@sailpoint.com
 */
public abstract class BaseProjectionDataSource extends AbstractDataSource implements LiveReportDataSource {

    private static final Log log = LogFactory.getLog(BaseProjectionDataSource.class);

    private static final String SCRIPT_ARG_PREFIX = "__scriptarg_";

    private Integer maxRowSize;

    protected List<ReportColumnConfig> columns;
    protected QueryOptions ops;
    protected Class objectType;

    protected Iterator<Object[]> results;
    protected Map<String, Object> currentRow;

    protected List<String> projectionColumns;

    protected DataSourceColumnHelper columnHelper;

    protected BaseProjectionDataSource() {
        super();
    }

    protected BaseProjectionDataSource(Class objectType, QueryOptions ops, List<ReportColumnConfig> columns,
                                       Locale locale, TimeZone timezone) {
        super(locale, timezone);
        init(objectType, ops, columns, locale, timezone);
    }

    protected void init(Class objectType, QueryOptions ops, List<ReportColumnConfig> columns,
                        Locale locale, TimeZone timezone){
        this.ops = ops;
        this.objectType = objectType;
        this.columns = columns;
        columnHelper = new DataSourceColumnHelper(locale, timezone);
    }

    protected abstract Iterator prepare() throws GeneralException;
    public abstract int getSizeEstimate() throws GeneralException;

    private void addProjectionColumn(String column){
        if (column != null && !projectionColumns.contains(column))
            projectionColumns.add(column);
    }

    private boolean getNextRow() throws GeneralException{

        currentRow = null;

        if (results.hasNext()) {
            Object next = results.next();
            if (next instanceof Object[]){
                currentRow = createRow((Object[])next);
            } else {
                currentRow = createRow(new Object[]{next});
            }
        }

        if (currentRow == null)
            return false;

        return true;
    }

    private Map<String, Object> convertRowToMap(Object[] row) throws GeneralException{
        Map<String, Object> result = new HashMap<String, Object>();
        for(int i=0;i<row.length;i++){
            if (i < projectionColumns.size()){
                Object val = row[i];
                result.put(projectionColumns.get(i), val);
            }
        }

        return result;
    }

    private static Object evaluateColumn(String property, Map<String, Object> rawResults){

        Object value = rawResults.get(getProjectionColumn(property));

        if (isAttributesProperty(property) && value != null){
            Attributes<String, Object> attrs = (Attributes<String, Object>)value;
            String attributeName = getAttributeName(property);
            if (attributeName != null){
                value = attrs.get(attributeName);
            } else {
                value = null;
            }
        }

        return value;
    }

    protected void prepareProjectionColumns(){
        projectionColumns = new ArrayList<String>();
        for(ReportColumnConfig column : columns){

            if (column.getSubQueryKey() == null){
                addProjectionColumn(getProjectionColumn(column.getProperty()));

                if (column.getIfEmpty() != null){
                    addProjectionColumn(getProjectionColumn(column.getIfEmpty()));
                }

                List<String> scriptArguments = column.getScriptArgumentsList();
                if(scriptArguments != null){
                    for(String arg : scriptArguments){
                        addProjectionColumn(getProjectionColumn(arg));
                    }
                }
            } else {
                addProjectionColumn(column.getSubQueryKey());
            }
        }
    }


    private Map<String, Object> createRow(Object[] rowData) throws GeneralException{

        Map<String, Object> rawResults = convertRowToMap(rowData);

        // Take the raw query results and copy into a new map using the field
        // names rather than the object property names.
        Map<String, Object> newRow = new HashMap<String, Object>();
        for(ReportColumnConfig col : columns){
            if (col.getProperty() != null){

                Object value = evaluateColumn(col.getProperty(), rawResults);

                if (col.getIfEmpty() != null && (value == null || value.toString().trim().length()==0)){
                    value = evaluateColumn(col.getIfEmpty(), rawResults);
                }

                List<String> scriptArgumentProps = col.getScriptArgumentsList();
                if (scriptArgumentProps != null){
                    for(String prop : scriptArgumentProps){
                        Object propVal = evaluateColumn(prop, rawResults);
                        newRow.put(SCRIPT_ARG_PREFIX + prop, propVal);
                    }
                }

                newRow.put(col.getField(), value);
            }
        }

        // Handle any columns with subqueries
        for(ReportColumnConfig col : columns){
           if (col.getSubQueryKey() != null){

               Object keyValue = rawResults.get(col.getSubQueryKey());

               QueryOptions ops = new QueryOptions();
               ops.add(Filter.eq(col.getSubQueryKey(), keyValue));
               List<String> fields = Arrays.asList(col.getProperty());

               List subQueryValues = new ArrayList();
               Iterator<Object[]> results =  this.getContext().search(objectType, ops, fields);
               while(results.hasNext()){
                   Object[] row = results.next();
                   Object v = row[0];
                   if (v != null && !subQueryValues.contains(v))
                       subQueryValues.add(v);
               }

               newRow.put(col.getField(), subQueryValues);
           }
        }

        return newRow;
    }


    @Override
    public boolean next() throws JRException {
        if (results == null){
            try {
                results = prepare();
            } catch (GeneralException e) {
                log.error(e);
                throw new JRException("Error preparing ProjectionDataSource", e);
            }
        }

        try {

            getContext().decache();

            return getNextRow();
        } catch (GeneralException e) {
            log.error(e);
            throw new JRException("Error getting next resultset row.", e);
        }
    }

    public Object getFieldValue(JRField jrField) throws JRException {

        String name = jrField.getName();
        Object value = null;
        try {
            value = getFieldValue(name);
        } catch (GeneralException e) {
            log.error(e);
            throw new JRException(e);
        }
        return value;
    }

    public Object getFieldValue(String field) throws GeneralException {

        Object val = null;
        if (currentRow != null){
            val = currentRow.get(field);
            ReportColumnConfig col = getColumnConfig(field);

            if (col != null && col.getRenderDef() != null){
                Map<String, Object> scriptArgs = new HashMap<String, Object>();
                List<String> scriptArgumentProps = col.getScriptArgumentsList();
                if (scriptArgumentProps != null){
                    for(String prop : scriptArgumentProps){
                        Object propVal = currentRow.get(SCRIPT_ARG_PREFIX + prop);
                        scriptArgs.put(prop, propVal);
                    }
                }
                val = columnHelper.runColumnRenderer(getContext(), col, val, scriptArgs);
            }
        }

        return val;
    }

    private ReportColumnConfig getColumnConfig(String field){

        ReportColumnConfig col = null;

        if (columns != null){
            for(ReportColumnConfig column : columns){
                if (column.getField().equals(field)){
                    col = column;
                    break;
                }
            }
        }
        return col;
    }

    private static boolean isAttributesProperty(String prop){
        return prop != null && prop.indexOf("attributes.") > -1;
    }

    private static String getAttributeName(String prop){
        String projectionCol = getProjectionColumn(prop);
        if (projectionCol != null){
            return prop.substring(projectionCol.length() + 1);
        }

        return null;
    }

    private static String getProjectionColumn(String prop){
       if (isAttributesProperty(prop)){
            return prop.substring(0, (prop.indexOf("attributes.") + 10));
        } else {
            return prop;
        }
    }

    protected Integer getMaxRowSize(){
        if (this.maxRowSize != null)
            return maxRowSize;

        return Configuration.getSystemConfig().getInteger(Configuration.REPORT_RESULT_ROW_THRESHOLD);
    }

}
