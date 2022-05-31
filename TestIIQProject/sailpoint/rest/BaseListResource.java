/* (c) Copyright 2014 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.rest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.annotation.PostConstruct;
import javax.ws.rs.Consumes;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;

import sailpoint.api.ObjectUtil;
import sailpoint.integration.ListResult;
import sailpoint.object.ColumnConfig;
import sailpoint.object.Filter;
import sailpoint.object.ObjectConfig;
import sailpoint.object.QueryOptions;
import sailpoint.object.SailPointObject;
import sailpoint.object.UIConfig;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Internationalizer;
import sailpoint.tools.JsonHelper;
import sailpoint.tools.Localizable;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.util.MapListComparator;
import sailpoint.web.util.Sorter;
import sailpoint.web.util.WebUtil;

/**
 * @author peter.holcomb
 *
 */
@Produces(MediaType.APPLICATION_JSON)
@Consumes({ MediaType.APPLICATION_JSON, MediaType.WILDCARD })
public class BaseListResource extends BaseResource {
    
    public static final String PARAM_START = "start";
    public static final String PARAM_LIMIT = "limit";
    public static final String PARAM_QUERY = "query";
    public static final String PARAM_SORT = "sort";
    public static final String PARAM_DIR = "dir";
    public static final String PARAM_COL_KEY = "colKey";
    public static final String PARAM_GROUP_BY = "groupBy";
    public static final String PARAM_SELECTED = "selected";
    public static final String PARAM_EXCLUDED_IDS = "excludedIds";
    public static final String PARAM_SELECT_ALL = "selectAll";

    /**
     * List of the standard query parameter names, to distinguish from "other" parameters that will be filters, usually.
     */
    public static final List<String> STANDARD_PARAMS =
            Arrays.asList(PARAM_START, PARAM_LIMIT, PARAM_QUERY, PARAM_SORT, PARAM_DIR, PARAM_COL_KEY, PARAM_GROUP_BY, PARAM_SELECTED, PARAM_EXCLUDED_IDS, PARAM_SELECT_ALL);

    ////////////////////////////////////////////////////////////////////////////
    //
    // CONSTANTS
    //
    ////////////////////////////////////////////////////////////////////////////
    
    protected static String CALCULATED_COLUMN_PREFIX = "IIQ_";


    ////////////////////////////////////////////////////////////////////////////
    //
    // FIELDS
    //
    ////////////////////////////////////////////////////////////////////////////
    
    // Common request parameters for returning lists.
    @QueryParam(PARAM_START) protected int start;
    @QueryParam(PARAM_LIMIT) protected int limit;
    @QueryParam(PARAM_QUERY) protected String query;
    @QueryParam(PARAM_SORT) protected String sortBy;
    @QueryParam(PARAM_DIR) protected String sortDirection;
    @QueryParam(PARAM_COL_KEY) protected String colKey;
    @QueryParam(PARAM_GROUP_BY) protected String groupBy;

    @QueryParam(PARAM_SELECTED) protected String selectedIds;
    @QueryParam(PARAM_EXCLUDED_IDS) protected String excludedIds;
    @QueryParam(PARAM_SELECT_ALL) protected String selectAll;

    /**
     * List of filters to limit list results. This is not set by default
     * but should be set by sub class if needed.
     */
    protected List<Filter> filters;

    // IIQSAW-3161: true if currently assigned items are to be excluded. Default to true to maintain current behavior.
    protected boolean exclude = true;

    ////////////////////////////////////////////////////////////////////////////
    //
    // CONSTRUCTORS
    //
    ////////////////////////////////////////////////////////////////////////////
    
    /**
     * Default constructor.
     */
    public BaseListResource() {
        super();
    }

    // if exlude is not provided, default to true for pre-IIQSAW-3161 behavior.
    public BaseListResource(BaseResource parent) {
        this(parent, true);
    }

    /**
     * Sub-resource constructor.
     */
    public BaseListResource(BaseResource parent, boolean exclude) {
        super(parent);
        this.exclude = exclude;

        // Figure out something better!  Since sub-resources are manaully
        // constructed, they do not get injected with the query parameters,
        // etc...  We will manually copy these over for now.  Seems like a
        // there's room for improvement in the spec.
        // TODO: It would be nice to be able to use the injection built-in to
        // JAX-RS.  There are no public APIs for this, though.  Instead, we
        // could use introspection to lookup class level fields with known
        // annotations and try to mimic what JAX-RS does to some extent.  For
        // now, though, we'll just hard-code it.
        MultivaluedMap<String,String> params = this.uriInfo.getQueryParameters();
        this.start = Util.atoi(params.getFirst(PARAM_START));
        this.limit = WebUtil.getResultLimit(Util.atoi(params.getFirst(PARAM_LIMIT)));
        this.query = params.getFirst(PARAM_QUERY);
        this.sortBy = params.getFirst(PARAM_SORT);
        this.sortDirection = params.getFirst(PARAM_DIR);
        this.colKey = params.getFirst(PARAM_COL_KEY);
        this.groupBy = params.getFirst(PARAM_GROUP_BY);

        this.selectedIds = params.getFirst(PARAM_SELECTED);
        this.excludedIds = params.getFirst(PARAM_EXCLUDED_IDS);
        this.selectAll = params.getFirst(PARAM_SELECT_ALL);

    }

    /**
     * PostContstruct method will fire after all the class level QueryParams are injected.
     * We use this to validate and massage those values as necessary. For now, it is just to
     * ensure the "limit" is within the configured bounds.
     */
    @PostConstruct
    public void init() {
        // We only want to do this for "GET" requests, POSTs will handle parsing their own parameters
        if ("GET".equals(this.request.getMethod())) {
            this.limit = WebUtil.getResultLimit(this.limit);
        }
    }


    ////////////////////////////////////////////////////////////////////////////
    //
    // GETTERS
    //
    ////////////////////////////////////////////////////////////////////////////
    
    protected String getColumnKey(){
        return !"".equals(colKey) ? colKey : null;
    }
    
    
    /**
     * Returns any query params that are not used elsewhere for things like sorting, paging, etc...
     */
    protected Map<String, String> getOtherQueryParams() {
        MultivaluedMap<String, String> restParameters = uriInfo.getQueryParameters();
        Map<String, String> queryParameters = new HashMap<String, String>();
        for(String key : restParameters.keySet()) {
            
            /* Skip it if is one of the standard params */
            if(STANDARD_PARAMS.contains(key)) {
                continue;
            }
            List<String> valueList = restParameters.get(key);
            if (Util.size(valueList) == 1) {
                queryParameters.put(key, valueList.get(0));
            } else {
                queryParameters.put(key, Util.listToCsv(valueList));
            }
        }
        
        return queryParameters;
    }


    ////////////////////////////////////////////////////////////////////////////
    //
    // FORM PROCESSING
    //
    ////////////////////////////////////////////////////////////////////////////
    
    protected static String getSingleFormValue(MultivaluedMap<String, String> form, String key){
        if (form.containsKey(key)){
            List<String> val = form.get(key);
            if (!"".equals(val.get(0))){
                return val.get(0);
            }
        }
        return null;
    }

    protected void processForm(MultivaluedMap<String, String> form){
        if (form != null){
            if (getSingleFormValue(form, PARAM_START)!=null)
                this.start = Integer.parseInt(getSingleFormValue(form, PARAM_START));
            if (getSingleFormValue(form, PARAM_LIMIT)!=null)
                this.limit = WebUtil.getResultLimit(Integer.parseInt(getSingleFormValue(form, PARAM_LIMIT)));

            this.query = getSingleFormValue(form, PARAM_QUERY);
            this.sortBy = getSingleFormValue(form, PARAM_SORT);
            this.sortDirection = getSingleFormValue(form, PARAM_DIR);
            this.colKey = getSingleFormValue(form, PARAM_COL_KEY);

            this.selectedIds = getSingleFormValue(form, PARAM_SELECTED);
            this.excludedIds = getSingleFormValue(form, PARAM_EXCLUDED_IDS);
            this.selectAll = getSingleFormValue(form, PARAM_SELECT_ALL);
        }
    }


    ////////////////////////////////////////////////////////////////////////////
    //
    // GRID MULTI-SELECTION
    //
    ////////////////////////////////////////////////////////////////////////////
    
    protected QueryOptions getSelectionQuery(){
        List<String> idList = Util.csvToList(selectedIds);
        List<String> exclusionList = Util.csvToList(excludedIds);
        if (Util.atob(selectAll)){
            QueryOptions ops = new QueryOptions();
            if (exclusionList != null){
                for(String id : exclusionList){
                    ops.add(Filter.ne("id", id));
                }
            }
            return ops;
        } else if (idList != null && !idList.isEmpty()){
            return new QueryOptions(Filter.in("id", idList));
        }

        return null;
    }

    
    ////////////////////////////////////////////////////////////////////////////
    //
    // QUERY OPTIONS
    //
    ////////////////////////////////////////////////////////////////////////////
    
    /**
     * Return the QueryOptions used to query - subclasses will likely override.
     */
    protected QueryOptions getQueryOptions() throws GeneralException{
        return getQueryOptions(null);
    }

    /**
     * Return the QueryOptions used to query with the given column key.  This
     * allows a resource to service multiple grids.
     */
    protected QueryOptions getQueryOptions(String columnsKey) throws GeneralException{
        QueryOptions qo = new QueryOptions();

        if (null == columnsKey) {
            columnsKey = getColumnKey();
        }
        
        if (start > 0)
            qo.setFirstRow(start);

        if (limit > 0)
            qo.setResultLimit(limit);

        if (!Util.isNullOrEmpty(this.sortBy)) {

            // jsl - for ManagedAttributesBean this comes in as Application-name 
            // and I don't see where that can be changed.  Fix it in an overload
            // until it can be done correctly.
            sortBy = fixSortBy(sortBy);

            handleOrdering(qo, columnsKey);
        }

        return qo;
    }

    /**
     * Default implementation of a method that can make adjustments
     * to the order by column name.
     */
    public String fixSortBy(String s) {
        return s;
    }

    /**
     * Add ordering to the given query options.
     */
    protected void handleOrdering(QueryOptions qo, String columnsKey)
        throws GeneralException {

        if (null != columnsKey) {
            List<ColumnConfig> cols = this.getColumns(columnsKey);
    
            if (null != cols) {
                List<Sorter> sorts = this.getSorters(cols);
                for (Sorter sorter: Util.safeIterable(sorts)) {
                    if (sorter.getProperty() != null && !sorter.getProperty().startsWith(CALCULATED_COLUMN_PREFIX)) {
                        sorter.addToQueryOptions(qo);
                    }
                }
            }
        }
    }
    
    /**
     * Get the Sorters for the list results
     * @param columnConfigs List of ColumnConfigs. Optional.
     * @return List of Sorters based on the sortBy (and possible sortDirection) properties. If none defined, null.
     */
    public List<Sorter> getSorters(List<ColumnConfig> columnConfigs) throws GeneralException {
        List<Sorter> sorts = parseSorters();

        // If we have a group by, stick it at the front of list of sorts
        if (this.groupBy != null) {
            Sorter groupSorter = new Sorter(this.groupBy, Sorter.SORTER_DIRECTION_ASC, false);
            if (sorts == null) {
                sorts = new ArrayList<>();
            }
            sorts.add(0, groupSorter);
        }
        
        // Make sure the sorters correspond to a real column, and update sort property and secondary sort.
        if (sorts != null && columnConfigs != null) {
            Iterator<Sorter> sorterIterator = sorts.iterator();
            while (sorterIterator.hasNext()) {
                Sorter sorter = sorterIterator.next();
                ColumnConfig columnConfig = findSortColumn(columnConfigs, sorter.getProperty());
                if (columnConfig != null) {
                    sorter.setSortProperty(columnConfig.getSortProperty());
                    // only add secondary sort property if its the last sorter
                    if (!sorterIterator.hasNext()) {
                        sorter.setSecondarySort(getSecondarySort(columnConfig.getSecondarySort(), columnConfigs));
                    }
                } else {
                    sorterIterator.remove();
                }
            }
        }

        return sorts;
    }

    /**
     * Get the "real" secondary sort for the given secondary sort. This looks at all secondary sort properties
     * and attempts to find sortProperty values for matching ColumnConfigs. Useful for calculated columns or those
     * with multiple sortProperty values. 
     * @param secondarySort String secondary sort property. Can be CSV.
     * @param columnConfigs List of ColumnConfigs
     * @return CSV string of secondary sort, with appropriate properties replaces as needed. 
     */
    private String getSecondarySort(String secondarySort, List<ColumnConfig> columnConfigs) {
        List<String> newSecondarySort = new ArrayList<String>();
        for (String secondarySortProperty : Util.csvToList(secondarySort)) {
            ColumnConfig secondarySortConfig = findSortColumn(columnConfigs, secondarySortProperty);
            if (secondarySortConfig == null) {
                newSecondarySort.add(secondarySortProperty);
            } else {
                newSecondarySort.addAll(Util.csvToList(secondarySortConfig.getSortProperty()));
            }
        }
        
        return Util.listToCsv(newSecondarySort);
    }
    
    /**
     * Parses a list of Sorter objects from the sorting related
     * class properties.
     *
     * @return The list of sorters.
     */
    protected List<Sorter> parseSorters() throws GeneralException {
        List<Sorter> sorts = null;

        String sort = getSortBy();
        if (!Util.isNullOrEmpty(sort)) {
            if (sort.startsWith("[")) {
                // if it starts with a bracket, we can assume it is a JSON array of
                // sorters, ExtJS Store style.
                sorts = JsonHelper.listFromJson(Sorter.class, sort);
            } else {
                Sorter sorter = new Sorter(sort, getSortDirection(), false);
                sorts = new ArrayList<Sorter>(Arrays.asList(sorter));
            }
        }

        return sorts;
    }

    /**
     * Find the requested sort column in the list of ColumnConfigs, searching
     * by the property name or the data index.  Return null if a matching
     * sortable column cannot be found.
     */
    private static ColumnConfig findSortColumn(List<ColumnConfig> cols, String sortCol) {
        ColumnConfig col = null;
        if (!Util.isEmpty(cols)) {
            for (ColumnConfig temp : cols) {
                if (temp.isSortable() &&
                    (Util.nullSafeEq(temp.getProperty(), sortCol) ||
                     Util.nullSafeEq(temp.getDataIndex(),sortCol))) {
                    col = temp;
                    break;
                }
            }
        }
        return col;
    }
    
    
    ////////////////////////////////////////////////////////////////////////////
    //
    // LISTING AND COUNTING
    //
    ////////////////////////////////////////////////////////////////////////////
    
    /**
     * Count the results - similar to BaseListBean.countResults()
     */
    public int countResults(Class<? extends SailPointObject> scope, QueryOptions qo)
        throws GeneralException {
        return getContext().countObjects(scope, qo);
    }

    /**
     * Return the ListResult for the given query.
     */
    public ListResult getListResult(String configKey,
                                    Class<? extends SailPointObject> clazz,
                                    QueryOptions qo)
        throws GeneralException{

        List<Map<String, Object>> out = getResults(configKey, clazz, qo);
        int total = countResults(clazz, qo);
        return new ListResult(out, total);
    }

    /**
     * Return a list of results for the given query.
     */
    public List<Map<String,Object>> getResults(String columnsKey,
                                               Class<? extends SailPointObject> scope,
                                               QueryOptions qo)
        throws GeneralException {

        List<String> cols = this.getProjectionColumns(columnsKey);
        assert (null != cols) : "Projection columns required for using getRows().";
        Iterator<Object[]> rows = getContext().search(scope, qo, cols);
        List<Map<String,Object>> results = new ArrayList<Map<String,Object>>();
        if (rows != null) {
            while (rows.hasNext()) {
                results.add(convertRow(rows.next(), cols, columnsKey));
            }
        }
        makeJsonSafeKeys(results);
        return results;
    }

    /**
     * Convert the given search result row into a list result.
     */
    protected Map<String,Object> convertRow(Object[] row, List<String> projectionCols, String columnsKey)
        throws GeneralException {

        Map<String,Object> rawQueryResults = getRawResults(row, projectionCols);
        return convertRow(rawQueryResults, columnsKey);
    }

    /**
     * Convert a row of resultset data into a map where the projection
     * column names are the keys.
     * @param row Row of projection data
     * @param cols List of columns names used in the projection search
     * @return Map of result data where key=column name and value = query result
     * @throws GeneralException
     */
    public Map<String,Object> getRawResults(Object[] row, List<String> cols)
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
     * Convert the given raw data into a result row.  This performs basic auto
     * tranformations (eg - i18n) as well as adding calculated columns via
     * calculateColumns().
     */
    public Map<String,Object> convertRow(Map<String,Object> rawObject, String columnsKey)
        throws GeneralException {
    
        Map<String,Object> converted = new HashMap<String,Object>(rawObject);
        List<ColumnConfig> columns = getColumns(columnsKey);

        // First, perform auto-convert on columns that we understand (i18n, etc...).
        for(Map.Entry<String,Object> entry : rawObject.entrySet()){
            ColumnConfig config = null;
            String col = entry.getKey();
    
            for(ColumnConfig column : columns) {
                if(col.equals(column.getProperty())) {
                    config = column;
                    break;
                }
            }
    
            if(config != null) {
                Object value = this.convertColumn(entry, config, rawObject);
                
                // Use the dataIndex if config is not null
                converted.put(config.getDataIndex(), value);
            }
        }
    
        // Perform any custom calculations for the row
        calculateColumns(rawObject, columnsKey, converted);

        return converted;
    }
    
    protected Object convertColumn(Map.Entry<String,Object> entry, ColumnConfig config, Map<String,Object> rawObject ) {
     // if the value can be localized, localize it
        Object value = entry.getValue();
        if (value != null && Localizable.class.isAssignableFrom(value.getClass())){
            value = ((Localizable)value).getLocalizedMessage(getLocale(), getUserTimeZone());
        }
        
        if (value != null && config.isLocalize() && value instanceof String) {
            value = (new Message((String)value)).getLocalizedMessage(getLocale(), getUserTimeZone());
        }

        // if value is a Date, format it for display
        if(value instanceof Date) {
            Integer dateStyle = Internationalizer.IIQ_DEFAULT_DATE_STYLE;
            Integer timeStyle = null;
            if(config.getDateStyle() != null) {
                dateStyle = config.getDateStyleValue();
                timeStyle = config.getDateStyleValue();
            }
            value = Util.dateToString((Date)value, dateStyle, timeStyle, getUserTimeZone(), getLocale());
        }
        
        return value;
    }
    
    /**
     * Add or update any calculated columns for the given map.  Usually subclasses
     * should override calculateColumn() instead of this.
     */
    protected void calculateColumns(Map<String,Object> rawQueryResults,
                                    String columnsKey,
                                    Map<String,Object> map)
        throws GeneralException{

        for(ColumnConfig config : getColumns(columnsKey)) {
            if(config.getProperty()==null) {
                calculateColumn(config, rawQueryResults, map);
            }
        }
    }

    /**
     * Override this to calculate data for a column.
     */
    protected void calculateColumn(ColumnConfig config, Map<String,Object> rawQueryResults,
                                   Map<String,Object> map) throws GeneralException{
        return;
    }


    /**
     * Produces a list result from a list of sailpoint objects.  Uses the convertObject
     * method turn each object into a result row.
     */
    public ListResult getListResultFromObjects(String configKey, List<?> objects)
        throws GeneralException{

        return getListResultFromObjects(configKey, objects, true);
    }


    /**
     * 
     * @param configKey
     * @param objects
     * @param trim indicates whether trim and sort result list
     * @return
     * @throws GeneralException
     */
    public ListResult getListResultFromObjects(String configKey, List<?> objects, boolean trim)
        throws GeneralException{

        List<Map<String,Object>> out = new ArrayList<Map<String,Object>>();

        for (Object obj : objects) {
            out.add(convertObject(obj, configKey));
        }

        int total = objects.size();
        if(trim)
        	return new ListResult(trimAndSortResults(out), total);
        else
        	return new ListResult(out, total);
    }
    
    /**
     * Turn the given object into a result row by first putting all properties
     * into a map - using objectToMap() - then converting/calculating the row.
     */
    protected Map<String,Object> convertObject(Object object, String configKey, ObjectConfig config)
        throws GeneralException {

        Map<String,Object> rawObject = objectToMap(object, configKey, config);
        return convertRow(rawObject, configKey);
    }

    protected Map<String,Object> convertObject(Object object, String configKey)
    	throws GeneralException {
    	return this.convertObject(object, configKey, null);
    }
    
    /**
     * Conver the given given object to a map by copying every projection column
     * from the object into the map.
     */
    protected Map<String,Object> objectToMap(Object object, String configKey, ObjectConfig config)
        throws GeneralException {

        Map<String,Object> map = new HashMap<String,Object>();
        List<String> cols = getProjectionColumns(configKey);
        if (!Util.isEmpty(cols)) {
            map = ObjectUtil.objectToMap(object, cols);
        }
        return map;
    }



    /*******************************************************************************
     * In Memory Stuff - The following are utility methods for resources that
     * have to pull all of the results into memory before sorting and limiting them
     * and sending them to the client
     *
     ******************************************************************************/


    

    /** Performs a trim of the results based on the start/limit and sorts the list
     * based on the sort by column.  This is for lists that must be brought into memory
     * before they can be sorted.
     * @param objects list of objects maps to be trimmed and sorted
     */
    public List<Map<String,Object>> trimAndSortResults(List<Map<String,Object>> objects) throws GeneralException {
        if(null == objects) {
            return null;
        }
        List<Map<String,Object>> newObjects = new ArrayList<Map<String,Object>>();
        Sorter sorter = null;
        // We have historically not consulted columns here. Stick with it.
        List<Sorter> sorters = getSorters(null);
        if (!Util.isEmpty(sorters)) {
            sorter = sorters.get(0);
        }
        
        if (sorter != null) {
            Collections.sort(objects, new MapListComparator(sorter.getProperty(), true));
            // Reverse the list if descending was specified.
            if (!sorter.isAscending()) {
                Collections.reverse(objects);
            }
        }

        if(start < objects.size()) {
            // If a limit wasn't specified, use the size of the list.
            this.limit = (this.limit > 0) ? this.limit : objects.size();

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


    ////////////////////////////////////////////////////////////////////////////
    //
    // COLUMN CONFIG HELPERS
    //
    ////////////////////////////////////////////////////////////////////////////
    
    /**
     * Gets a list of column config objects out of the UIConfig object based on the columnsKey
     * passed in.
     * @param columnsKey
     * @return
     * @throws GeneralException
     */
    @SuppressWarnings("unchecked")
    protected List<ColumnConfig> getColumns(String columnsKey) throws GeneralException{
        UIConfig uiConfig = UIConfig.getUIConfig();
        List<ColumnConfig> columns = null;
        if(uiConfig!=null) {
            columns = uiConfig.getAttributes().getList(columnsKey);
            if ( columns == null && !uiConfig.getAttributes().containsKey(columnsKey ) ) {
                throw new GeneralException("Unable to locate column config for ["+columnsKey+"].");
            }
        }
        return columns;
    }

    /**
     * Gets the list of projection columns for a resource based on the passed
     * in string that represents the place in the UI config to get the colums
     */
    protected List<String> getProjectionColumns(String columnsKey) throws GeneralException{
        List<String> projectionColumns = null;
        List<ColumnConfig> columns = getColumns(columnsKey);

        if(columns!=null) {
            projectionColumns = new ArrayList<String>();
            for (ColumnConfig col : columns) {
                /** Only add non-calculated columns **/
                if(null != col.getProperty())
                    projectionColumns.add(col.getProperty());
            }
        }
        return projectionColumns;
    }

    public int getStart()
    {
      return start;
    }

    public void setStart(int start)
    {
      this.start = start;
    }

    public int getLimit()
    {
      return limit;
    }

    public void setLimit(int limit)
    {
      this.limit = limit;
    }

    public String getQuery()
    {
      return query;
    }

    public void setQuery(String query)
    {
      this.query = query;
    }

    public String getSortBy()
    {
      return sortBy;
    }

    public void setSortBy(String sortBy)
    {
      this.sortBy = sortBy;
    }

    public String getSortDirection()
    {
      return sortDirection;
    }

    public void setSortDirection(String sortDirection)
    {
      this.sortDirection = sortDirection;
    }

    public String getColKey()
    {
      return colKey;
    }

    public void setColKey(String colKey)
    {
      this.colKey = colKey;
    }

    public String getSelectedIds()
    {
      return selectedIds;
    }

    public void setSelectedIds(String selectedIds)
    {
      this.selectedIds = selectedIds;
    }

    public String getExcludedIds()
    {
      return excludedIds;
    }

    public void setExcludedIds(String excludedIds)
    {
      this.excludedIds = excludedIds;
    }

    public String getSelectAll()
    {
      return selectAll;
    }

    public void setSelectAll(String selectAll)
    {
      this.selectAll = selectAll;
    }
    
    public String getGroupBy() {
        return this.groupBy;
    }
    
    public void setGroupBy(String groupBy) {
        this.groupBy = groupBy;
    }

    public List<Filter> getFilters() { return this.filters; }

    public void setFilters(List<Filter> filters) {
        this.filters = filters;
    }
}
