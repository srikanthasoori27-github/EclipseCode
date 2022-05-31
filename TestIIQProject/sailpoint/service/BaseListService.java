package sailpoint.service;

import org.apache.commons.beanutils.PropertyUtils;
import sailpoint.api.SailPointContext;
import sailpoint.authorization.AuthorizationUtility;
import sailpoint.authorization.Authorizer;
import sailpoint.authorization.UnauthorizedAccessException;
import sailpoint.object.ColumnConfig;
import sailpoint.object.Filter;
import sailpoint.object.QueryOptions;
import sailpoint.object.SailPointObject;
import sailpoint.service.listfilter.ListFilterValue;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Internationalizer;
import sailpoint.tools.Localizable;
import sailpoint.tools.Message;
import sailpoint.tools.Rfc4180CsvBuilder;
import sailpoint.tools.Util;
import sailpoint.web.util.FilterHelper;
import sailpoint.web.util.MapListComparator;
import sailpoint.web.util.Sorter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/**
 * A base service layer which supports list/paging operations and query infrastructure.
 */
public class BaseListService<SC extends BaseListServiceContext> {

    /**
     * Inner class to hold some data about result groups. Will be used in metadata for list
     * results with groupBy specified. 
     */
    public static class ListResultGroup {
        
        /**
         * Map holding dataIndex/value pairs that define the group
         */
        private Map<String, Object> properties;

        /**
         * Map holding projectionColumn/value pairs that define the group 
         */
        private Map<String, ListFilterValue> filterValues;
        
        /**
         * Count of results in this group
         */
        private int count;
        
        /**
         * IDs of objects in the groups. Used when properties are not enough to define a group, such as 
         * for calculated columns
         */
        private List<String> objectIds;

        /**
         * @return Map holding property/value pairs that define the group
         */
        public Map<String, ListFilterValue> getFilterValues() {
            return filterValues;
        }
        
        public void addFilterValue(String projectionColumn, Object value) {
            if (this.filterValues == null) {
                this.filterValues = new HashMap<>();
            }
            this.filterValues.put(projectionColumn, new ListFilterValue(value, ListFilterValue.Operation.Equals));
        }


        /**
         * @return Map holding dataIndex/value pairs that define the group
         */
        public Map<String, Object> getProperties() {
            return properties;
        }

        public void addProperty(String property, Object value) {
            if (this.properties == null) {
                this.properties = new HashMap<>();
            }
            this.properties.put(property, value);
        }
        /**
         * @return Count of results in this group
         */
        public int getCount() {
            return count;
        }

        public void setCount(int count) {
            this.count = count;
        }

        /**
         * @return IDs of objects in the groups. Used when properties are not enough to define a group, such as 
         * for calculated columns
         */
        public List<String> getObjectIds() {
            return objectIds;
        }
        
        public void addObjectId(String objectId) {
            if (this.objectIds == null) {
                this.objectIds = new ArrayList<>();
            }
            this.objectIds.add(objectId);
        }
    }
    
    protected SailPointContext context;
    
    protected SC listServiceContext;
    
    protected ListServiceColumnSelector columnSelector;

    /**
     * Create a base list service.
     * @param context sailpoint context
     * @param listServiceContext list service context
     */
    public BaseListService(SailPointContext context, SC listServiceContext, 
                           ListServiceColumnSelector columnSelector)
    {
        this.context = context;
        this.listServiceContext = listServiceContext;
        this.columnSelector = columnSelector;
    }

    public SailPointContext getContext() {
        return context;
    }

    public SC getListServiceContext() {
        return listServiceContext;
    }

    /**
     * Export the row data to a CSV string with the column configs provided.
     * The export data should be keyed with the column config properties and the values should have been converted
     * and ready to print via evaluators.
     *
     * @return CSV string
     * @throws GeneralException
     */
    public String exportToCSV(List<Map<String,Object>> rows, List<ColumnConfig> columns) throws GeneralException {
        // we need both columns and rows
        if (columns == null || rows == null) {
            return "";
        }

        //Create the CSV builder
        Rfc4180CsvBuilder csvBuilder = new Rfc4180CsvBuilder();
        csvBuilder.setQuoteLineFeed(true);

        /** Print header using localized messages **/
        printCSVHeader(csvBuilder, columns);

        /** Print rows **/
        printCSVRows(csvBuilder, rows, columns);

        return csvBuilder.build();
    }

    /**
     * Print rows for CSV export file. Allow for both json and non-json property keys.
     * This depends on the the export data is constructed.
     *
     * @param csvBuilder Rfc4180CsvBuilder to use
     * @param rows List<Map<String, Object>> rows of items to write out
     * @param columns List<ColumnConfig> column configs to use to help writing out data
     */
    private void printCSVRows(Rfc4180CsvBuilder csvBuilder, List<Map<String, Object>> rows, List<ColumnConfig> columns) {
        for (Map<String, Object> row : rows) {
            for (ColumnConfig column: columns) {
                Object value = getColumnValue(row, column.getProperty());
                // also try with column json property
                if (value == null) {
                    value = getColumnValue(row, column.getJsonProperty());
                }
                // This allows us to export post processed columns
                if(value == null) {
                    value = getColumnValue(row, column.getDataIndex());
                }

                csvBuilder.addValue(value == null ? "" : value.toString());
            }
            csvBuilder.endCurrentRecord();
        }
    }

    /**
     * Helper function to write out column headers for CSV export.
     *
     * @param csvBuilder Rfc4180CsvBuilder to use to build header
     * @param columns List<ColumnConfig> the list of column configs to use
     */
    private void printCSVHeader(Rfc4180CsvBuilder csvBuilder, List<ColumnConfig> columns) {
        for(ColumnConfig column : columns) {
            Message msg = new Message(column.getHeaderKey());
            String header = msg.getLocalizedMessage(listServiceContext.getLocale(), listServiceContext.getUserTimeZone());
            csvBuilder.addValue(header);
        }
        csvBuilder.endCurrentRecord();
    }

    /**
     * Return a list of results for the given query.
     * @param scope scope
     * @param qo query options
     * @return the list of result rows
     */
    public List<Map<String,Object>> getResults(Class<? extends SailPointObject> scope,
                                               QueryOptions qo)
            throws GeneralException 
    {
        List<String> cols = columnSelector.getProjectionColumns();
        assert (null != cols) : "Projection columns required for using getRows().";
        Iterator<Object[]> rows = getContext().search(scope, qo, cols);
        List<Map<String,Object>> results = new ArrayList<Map<String,Object>>();
        if (rows != null) {
            while (rows.hasNext()) {
                results.add(convertRow(rows.next(), cols));
            }
        }
        makeJsonSafeKeys(results);
        return results;
    }

    /**
     * Convert the given search result row into a list result.
     * @param row raw row to convert
     * @param projectionCols projection columns
     * @return row in result form
     */
    public Map<String,Object> convertRow(Object[] row, List<String> projectionCols)
            throws GeneralException
    {
        Map<String,Object> rawQueryResults = getRawResults(row, projectionCols);
        return convertRow(rawQueryResults);
    }

    /**
     * Convert the given raw data into a result row.  This performs basic auto
     * tranformations (eg - i18n) as well as adding calculated columns via
     * calculateColumns().
     *
     * @param rawObject raw object
     * @return row in result form
     */
    protected Map<String,Object> convertRow(Map<String,Object> rawObject)
            throws GeneralException {
        return convertRow(rawObject, false);
    }

    /**
     * Convert the given raw data into a result row.  This performs basic auto
     * tranformations (eg - i18n) as well as adding calculated columns via
     * calculateColumns().
     *
     * @param rawObject raw object
     * @param keepUnconfiguredColumns If true, keep values in row even if no corresponding column config                 
     * @return row in result form
     */
    protected Map<String,Object> convertRow(Map<String,Object> rawObject, boolean keepUnconfiguredColumns)
            throws GeneralException
    {

        Map<String,Object> converted = new HashMap<String,Object>(rawObject);
        List<ColumnConfig> columns = columnSelector.getColumns();

        // First, perform auto-convert on columns that we understand (i18n, etc...).
        for (Map.Entry<String,Object> entry : rawObject.entrySet()) {
            ColumnConfig config = null;
            String col = entry.getKey();

            for (ColumnConfig column : Util.iterate(columns)) {
                if (col.equals(column.getProperty()) || col.equals(column.getJsonProperty())) {
                    config = column;
                    break;
                }
            }

            if (config != null) {
                Object value = this.convertColumn(entry, config, rawObject);

                // Use the dataIndex if config is not null
                converted.put(config.getDataIndex(), value);
            } else if (keepUnconfiguredColumns) {
                // Just shove it back in the map as-is
                converted.put(entry.getKey(), entry.getValue());
            }
        }

        // Perform any custom calculations for the row
        calculateColumns(rawObject, converted);

        return converted;
    }
    
    /**
     * Converts a column
     * @param entry column entry
     * @param config column config
     * @param rawObject raw object
     * @return converted column
     */
    protected Object convertColumn(Map.Entry<String,Object> entry, ColumnConfig config, Map<String,Object> rawObject ) throws GeneralException {
        // if the value can be localized, localize it
        // do not localize if fieldOnly, this will need to stay in original type
        Object value = entry.getValue();
        if (value != null && !config.isFieldOnly() && Localizable.class.isAssignableFrom(value.getClass())){
            if (listServiceContext == null) {
                value = ((Localizable)value).getLocalizedMessage(Locale.getDefault(), TimeZone.getDefault());
            } else{
                value = ((Localizable)value).getLocalizedMessage(listServiceContext.getLocale(), listServiceContext.getUserTimeZone());
            }
        }

        // if value is a Date, format it for display
        value = convertDate(value, config);

        return value;
    }

    /**
     * Format Date object into String
     * 
     * @param value entry value take from column map
     * @param config column config
     * @return {Object} value formated date String
     */
    protected Object convertDate(Object value, ColumnConfig config) {
        if(value instanceof Date) {
            Integer dateStyle = Internationalizer.IIQ_DEFAULT_DATE_STYLE;
            Integer timeStyle = null;
            if(config.getDateStyle() != null) {
                dateStyle = config.getDateStyleValue();
                timeStyle = config.getDateStyleValue();
            }
            if (listServiceContext == null) {
                value = Util.dateToString((Date) value, dateStyle, timeStyle, TimeZone.getDefault(), Locale.getDefault());
            } else {
                value = Util.dateToString((Date) value, dateStyle, timeStyle, listServiceContext.getUserTimeZone(), listServiceContext.getLocale());
            }
        }
        return value;
    }
    
    /**
     * Overload this in the subclass in order to provide different values
     * for each column depending on how the subclass wants to display.
     * This is the last point where data conversion may occur. Subclasses that override this should make sure data
     * toString() returns the displayable value.
     *
     * @param row map of column values
     * @param column column name
     * @return the evaluated column value
     */
    public Object getColumnValue(Map<String, Object> row, String column) {
        Object value = row.get(column);
        return value;
    }

    /**
     * Add or update any calculated columns for the given map.  Usually subclasses
     * should override calculateColumn() instead of this.
     *
     * @param rawQueryResults raw query results
     * @param map column map
     */
    protected void calculateColumns(Map<String,Object> rawQueryResults,
                                    Map<String,Object> map)
            throws GeneralException
    {

        for(ColumnConfig config : Util.iterate(columnSelector.getColumns())) {
            if(config.getProperty() == null) {
                calculateColumn(config, rawQueryResults, map);
            }
        }
    }

    /**
     * Override this to calculate data for a column.
     * 
     * @param config column config 
     * @param rawQueryResults raw query results
     * @param map column map
     */
    protected void calculateColumn(ColumnConfig config, Map<String,Object> rawQueryResults,
                                   Map<String,Object> map) throws GeneralException{
        return;
    }

    /**
     * Authorizes the current user with the specified authorizers.
     * 
     * @param authorizers The authorizers to check.
     * @throws UnauthorizedAccessException If authorization fails.
     * @throws GeneralException If an error occurs during authorization.
     */
    protected void authorize(Authorizer... authorizers) throws GeneralException {       
        AuthorizationUtility.authorize(this.getListServiceContext(), authorizers);
    }

    /**
     * Make all of the keys of the given list of Maps JSON-safe.  That is,
     * trade out all dots (periods) with dashes.
     * 
     * @param rows the list of rows
     * @return the json-safe list of rows
     */
    protected static List<Map<String,Object>> makeJsonSafeKeys(List<Map<String,Object>> rows) {

        if (null != rows) {
            Map<String,String> keyFixes = new HashMap<String,String>();

            for (Map<String,Object> row : rows) {

                // First time through, look for any unsafe keys.
                if (keyFixes.isEmpty()) {
                    for (Map.Entry<String,Object> entry : row.entrySet()) {
                        String key = entry.getKey();
                        if(key.contains(".")) {
                            keyFixes.put(key, Util.getJsonSafeKey(key));
                        }
                    }

                    // We didn't find anything bad, bail out.
                    if (keyFixes.isEmpty()) {
                        break;
                    }
                }

                // If we got here there is stuff to fix, so do it.
                for (Map.Entry<String,String> entry : keyFixes.entrySet()) {
                    Object val = row.remove(entry.getKey());
                    row.put(entry.getValue(), val);
                }
            }
        }

        return rows;
    }

    /**
     * Convert a row of resultset data into a map where the projection
     * column names are the keys.
     * @param row Row of projection data
     * @param cols List of columns names used in the projection search
     * @return Map of result data where key=column name and value = query result
     * @throws GeneralException
     */
    protected Map<String,Object> getRawResults(Object[] row, List<String> cols)
            throws GeneralException {

        Map<String,Object> map = new HashMap<String,Object>(row.length);
        int i = 0;
        if ( cols != null ) {
            for (String col : cols) {
                map.put(col, row[i]);
                i++;
            }
        }
        return map;
    }

    /**
     * Count the results - similar to BaseListBean.countResults()
     */
    public int countResults(Class<? extends SailPointObject> scope, QueryOptions qo)
        throws GeneralException {
        return getContext().countObjects(scope, qo);
    }

    /**
     * Performs a trim of the results based on the start/limit and sorts the list
     * based on the sort by column.  This is for lists that must be brought into memory
     * before they can be sorted.
     * @param objects list of objects maps to be trimmed and sorted
     */
    public List<Map<String,Object>> trimAndSortResults(List<Map<String,Object>> objects) throws GeneralException {
        if(null == objects) {
            return null;
        }
        List<Map<String,Object>> newObjects = new ArrayList<Map<String,Object>>();

        SC listServiceContext = getListServiceContext();

        List<Sorter> sorters = listServiceContext.getSorters(this.columnSelector.getColumns());
        Sorter sorter = null;

        if (!Util.isEmpty(sorters)) {
            sorter = sorters.get(0);
        }

        if (sorter != null) {
            Collections.sort(objects, new MapListComparator(sorter.getSortProperty(), true));
            // Reverse the list if descending was specified.
            if (!sorter.isAscending()) {
                Collections.reverse(objects);
            }
        }

        int limit = listServiceContext.getLimit();
        int start = listServiceContext.getStart();

        if(listServiceContext.getStart() < objects.size() && start >= 0) {
            // If a limit wasn't specified, use the size of the list.
            limit = (limit > 0) ? limit : objects.size();

            if((limit+start)>=objects.size()) {
                limit = objects.size();
            } else {
                limit = start+limit;
            }
            for(int i=start; i<limit; i++) {
                newObjects.add(objects.get(i));
            }
        }

        return newObjects;
    }

    protected boolean valueEquals(Map<String, Object> map1, Map<String, Object> map2, String key) {
        return Util.nullSafeEq(map1.get(key), map2.get(key));
    }

    /**
     * Adds the orderings to the QueryOptions based on the sorters in the list service context
     * @param qo QueryOptions to update with orderings
     * @throws GeneralException
     */
    protected void handleOrdering(QueryOptions qo) throws GeneralException {

        List<ColumnConfig> cols = this.columnSelector.getColumns();

        if (null != cols) {
            List<Sorter> sorts = this.listServiceContext.getSorters(cols);
            for (Sorter sorter: Util.safeIterable(sorts)) {
                if (sorter.getProperty() != null) {
                    sorter.addToQueryOptions(qo);
                }
            }
        }
    }

    /**
     * Setup the start/limit on the given QueryOptions.
     * @param qo  The QueryOptions to update.
     */
    protected void handlePagination(QueryOptions qo) {
        qo.setFirstRow(this.listServiceContext.getStart());
        qo.setResultLimit(this.listServiceContext.getLimit());
    }

    /**
     * Setup the filters on the given QueryOptions
     * @param qo QueryOptions
     */
    protected void handleFilters(QueryOptions qo) {
        for (Filter filter : Util.iterate(this.listServiceContext.getFilters())) {
            qo.add(filter);
        }
    }

    /**
     * Create a QueryOptions that has the sorting and pagination already setup.
     *
     * @return A QueryOptions that has the sorting and pagination already setup.
     */
    protected QueryOptions createQueryOptions() throws GeneralException {
        QueryOptions qo = new QueryOptions();
        handleOrdering(qo);
        handlePagination(qo);
        handleFilters(qo);
        return qo;
    }

    /**
     * Get localize messages of the given message key
     * @param key ResourceBundle message key, or a plain text messageargs 
     * @param parameters Any parameters to be inserted into the message. 
     * The list can be made up of any object. If the parameter is another Message, it will be localized, 
     * if not we will call toString() to get the value.
     * 
     * @return string of localized message
     */
    protected String localize(String key, Object... parameters){
        if (key == null)
            return null;

        Message msg = Message.info(key, parameters);
        return msg.getLocalizedMessage(getListServiceContext().getLocale(), getListServiceContext().getUserTimeZone());
    }

    /**
     * Find the column config for the given property, matching both dataIndex and property
     * @param property Name of property
     * @return ColumnConfig, or null
     * @throws GeneralException
     */
    protected ColumnConfig findColumnConfig(String property) throws GeneralException {
        for (ColumnConfig columnConfig : Util.iterate(this.columnSelector.getColumns())) {
            if (Util.nullSafeEq(property, columnConfig.getProperty()) ||
                    Util.nullSafeEq(property, columnConfig.getDataIndex())) {
                return columnConfig;
            }
        }

        return null;
    }
    
    /**
     * Get a list of ListResultGroups to define the group meta data for these results. Only applies if groupBy is specified
     * @param results Actual page of results from the initial query
     * @param scope Object type
     * @param ops Original query options to establish filters
     * @return List of ListResultGroup objects, or null if not group by.
     * @throws GeneralException
     */
    protected List<ListResultGroup> getResultGroups(List<Map<String, Object>> results, Class<? extends SailPointObject> scope, QueryOptions ops) throws GeneralException {
        String groupBy = this.listServiceContext.getGroupBy();
        if (Util.isNothing(groupBy)) {
            return null;
        }
        List<ListResultGroup> groups = new ArrayList<>();
        
        // Make copy of options and clear start/limit/sorts 
        QueryOptions newOps = new QueryOptions(ops);
        newOps.setResultLimit(0);
        newOps.setFirstRow(0);
        newOps.setOrderings(new ArrayList<QueryOptions.Ordering>());
        
        // This will handle adding filters to query options to limit groups matching results
        List<String> projectionColumns = handleGroupByForResultGroups(newOps, groupBy, results);
        projectionColumns.add("count(*)");
        
        Iterator<Object[]> resultGroups = this.getContext().search(scope, newOps, projectionColumns);
        while (resultGroups.hasNext()) {
            Object[] result = resultGroups.next();
            ListResultGroup group = new ListResultGroup();
            for (int i = 0; i < projectionColumns.size(); i++) {
                // Last column is the count
                if (i == projectionColumns.size() - 1) {
                    group.setCount(Util.otoi(result[i]));    
                } else {
                    // Otherwise add a property to the group using data index so it matches the client side model
                    String propertyName = projectionColumns.get(i);
                    String dataIndex = propertyName;
                    ColumnConfig columnConfig = findColumnConfig(propertyName);
                    if (columnConfig != null) {
                        dataIndex = columnConfig.getDataIndex();
                    }
                    group.addProperty(dataIndex, result[i]);
                    group.addFilterValue(propertyName, result[i]);
                }
            }
            groups.add(group);
        }
        
        return groups;
    }

    /**
     * Given the groupBy and current results, add filters to the queryOptions to limit our count query to only things
     * that match groups in our results. 
     * 
     * @return Names of projection columns to include in projection search
     * @throws GeneralException
     */
    private List<String> handleGroupByForResultGroups(QueryOptions queryOptions, String groupBy, List<Map<String, Object>> results) throws GeneralException {
        List<Filter> allResultFilters = new ArrayList<>();
        ColumnConfig groupColumnConfig = findColumnConfig(groupBy);
        // If column exists for groupBy, use the groupProperty of it
        List<String> groupByProperties = (groupColumnConfig == null) ? Collections.singletonList(groupBy) : Util.csvToList(groupColumnConfig.getGroupProperty());
        queryOptions.setGroupBys(groupByProperties);

        for (Map<String, Object> result : results) {
            List<Filter> resultFilters = new ArrayList<>();
            for (String groupByProperty : groupByProperties) {
                ColumnConfig groupByPropertyConfig = findColumnConfig(groupByProperty);
                // If column exists, use the data index to get the value of the result, otherwise just use the property directly.
                String key = (groupByPropertyConfig != null) ? groupByPropertyConfig.getDataIndex() : groupByProperty; 
                Object value = result.get(key);
                Filter filter = (value == null) ? FilterHelper.getNullFilter(groupByProperty) : Filter.eq(groupByProperty, value);
                resultFilters.add(filter);
            }
            Filter resultFilter = FilterHelper.getFilterFromList(resultFilters, Filter.BooleanOperation.AND);
            if (resultFilter != null && !allResultFilters.contains(resultFilter)) {
                allResultFilters.add(resultFilter);
            }
        }

        Filter orFilter = FilterHelper.getFilterFromList(allResultFilters, Filter.BooleanOperation.OR);
        if (orFilter != null) {
            queryOptions.add(orFilter);
        }
        return new ArrayList<>(groupByProperties);
    }

    /**
     * Attemp to use reflection to create a map out of an object
     * @param objects The list of objects to be converted to a map
     * @return
     * @throws GeneralException
     */
    protected List<Map<String,Object>> convertObjects(List objects) throws GeneralException {
        List<Map<String,Object>> results = new ArrayList<Map<String,Object>>();
        for(Object object : objects) {
            results.add(this.convertObject(object));
        }
        return results;
    }

    /**
     * Attemp to use reflection to create a map out of an object
     * @param object The object to be converted to a map
     * @return
     * @throws GeneralException
     */
    protected Map<String,Object> convertObject(Object object) throws GeneralException{
        Map<String,Object> map = new HashMap<String,Object>();
        List<String> cols = columnSelector.getProjectionColumns();

        List<ColumnConfig> columns = columnSelector.getColumns();
        for (String col : cols) {
            ColumnConfig config = null;

            if(columns!=null) {
                for(ColumnConfig column : columns) {
                    if(column.getProperty()!=null && column.getProperty().equals(col)) {
                        config = column;
                        break;
                    }
                }
            }
            try {
                /** Convert the date if it has the date style set **/
                Object value = PropertyUtils.getNestedProperty(object, col);
                if (value != null && Localizable.class.isAssignableFrom(value.getClass())){
                    value = ((Localizable)value).getLocalizedMessage(listServiceContext.getLocale(),
                            listServiceContext.getUserTimeZone());
                }
                if(config!=null) {
                    // if the value can be localized. If so, localize it
                    if(config.getDateStyle() !=null) {
                        value = Util.dateToString((Date)value, config.getDateStyleValue(), config.getDateStyleValue(),
                                listServiceContext.getUserTimeZone(), listServiceContext.getLocale());
                    }
                    /** Use the dataIndex if config is not null **/
                    map.put(config.getDataIndex(), value);

                } else {
                    map.put(col, value);
                }
            } catch(Exception ex) {
                throw new GeneralException(ex);
            }
        }
        return map;
    }
}
