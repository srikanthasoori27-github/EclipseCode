/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web;

import java.io.PrintWriter;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import javax.faces.context.FacesContext;
import javax.faces.event.ActionEvent;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.beanutils.PropertyUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.IdentityService;
import sailpoint.object.ColumnConfig;
import sailpoint.object.Filter;
import sailpoint.object.GridState;
import sailpoint.object.Identity;
import sailpoint.object.QueryOptions;
import sailpoint.object.QueryOptions.Ordering;
import sailpoint.object.SailPointObject;
import sailpoint.object.TaskResult;
import sailpoint.task.ReportExportMonitor;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Internationalizer;
import sailpoint.tools.JsonHelper;
import sailpoint.tools.Localizable;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.extjs.GridResponse;
import sailpoint.web.extjs.GridResponseMetaData;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.util.MapListComparator;
import sailpoint.web.util.NavigationHistory;
import sailpoint.web.util.Sorter;


/**
 * Base UI bean used to list objects.
 */
public class BaseListBean<E extends SailPointObject> extends BaseBean {
    private static Log log = LogFactory.getLog(BaseListBean.class);

    public static final String ATT_SELECTED_ID = "SelectedId";
    public static final String ATT_EDITFORM_ID = "editForm:id";
    public static final String ATT_LAST_SELECTED_ID = "LastSelectedId";
    public static final String MIME_CSV = "application/vnd.ms-excel";

    public static final String EXPORT_MONITOR = "exportMonitor";
    
    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * The class of object we're editing.  Must be set by the
     * subclass.
     */
    Class<E> _scope;

    /**
     * The loaded GridState for the identity accessing the grid.
     */
    GridState _gridState;
    /**
     * The loaded ColumnsConfigs for this grid.
     */
    List<ColumnConfig> _columns;
    
    /** When fetching the column configs, some columns will be fieldOnly.  We don't want
     * to return these in the list of columns fetched, but we do want to keep them around 
     * for the projection columns;
     */
    List<ColumnConfig> _fieldColumns;

    /**
     * The QueryOptions used to calculate the caches count.  This is used to
     * recognize when we need to recalculate the count.
     */
    QueryOptions _countQueryOptions;

    /**
     * A flag that is falst by default that if enabled ( set to true )
     * the defalult owner scoping added to the QueryOptions in
     * getQueryOptions will be ommited.
     */
    boolean _disableOwnerScoping;


    /**
     * Cached list of objects.  This should be used when there are no projection
     * columns.
     */
    List<E> _objects;

    /**
     * Cached list of rows to be displayed.  This should be used when there are
     * projection columns.
     */
    protected List<Map<String,Object>> _rows;

    /**
     * Cached count of the number of rows or objects.
     */
    protected Integer _count;

    /**
     * A flag that is false by default that if enabled will disable the search function
     * and return an empty list of results to the caller.
     */
    protected boolean _disableSearchLoad = false;

    Map<String, String> _displayableNames;


    /**
     * The ID of the object that has been selected.  In a multi-select list, the
     * <code>selected</code> Map should be used instead of this.
     */
    String _selectedId;

    Map<String, Boolean> _selected;

    /** Limits and sort orderings **/
    int _start;
    int _limit;

    String _sort;
    String _secondarySort;
    String _direction;


    public String getSort() throws GeneralException {
        if(_sort == null) {
            _sort = getRequestParameter("sort");
        }

        if ("".equals(_sort)) {
            _sort = null;
        }

        if(_sort == null) {
            try {
                _sort = getDefaultSortColumn();
            } catch(GeneralException ge) {}
        }
        return _sort;
    }

    public void setSort(String sort) {
        _sort = sort;
    }
    
    public String getSecondarySort() {
        if(_secondarySort == null) {
            _secondarySort = getRequestParameter("secondarySort");
        }
        return _secondarySort;
    }

    public void setSecondarySort(String secondarySort) {
        _secondarySort = secondarySort;
    }

    public String getDirection() {
        if(_direction==null) {
            _direction = getRequestParameter("dir");
        }
        if(_direction==null)
            _direction = "ASC";
        return _direction;
    }

    public void setDirection(String direction) {
        _direction = direction;
    }


    //////////////////////////////////////////////////////////////////////
    //
    // Constructors
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Default constructor.
     */
    public BaseListBean() {
        super();
        restoreFromHistory();
    }  // BaseListBean()

    /**
     * Restore state from the NavigationHistory if we can retrieve state for
     * this page.
     */
    public void restoreFromHistory() {

        if (this instanceof NavigationHistory.Page) {
            NavigationHistory.getInstance().restorePageState((NavigationHistory.Page) this);
        }
    }


    //////////////////////////////////////////////////////////////////////
    //
    // METHODS TO BE OVERRIDDEN
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Specify the persistent object class we will be dealing with.
     * This must be set by the subclass.
     */
    public void setScope(Class<E> c) {
        _scope = c;
    }

    /**
     * "Scope" in this context is just the class being listed.
     * Don't confuse this with assigned and controlled scopes, which
     * control the visibility of objects being listed.
     * @return The class of the object being listed
     */
    public Class<E> getScope() {
        return _scope;
    }

    /**
     * Subclasses should extend to set sort order.
     *
     * @return A QueryOptions used to filter and sort results.
     */
    public void getSortOrdering(QueryOptions qo) throws GeneralException {

        if(getStart()>0) {
            qo.setFirstRow(_start);
        }

        if(getLimit()>0) {
            qo.setResultLimit(_limit);
        }

        //Figure out which column to sort by.
        Map<String,ColumnConfig> sortCols = getSortColumnConfigMap();
        if (null != sortCols)
        {
            if(getSort() != null) {

                // if it starts with a bracket, we can assume it is a JSON array of sorters, ExtJS Store style.
                if(_sort.startsWith("[")) {
                    @SuppressWarnings("unchecked")
                    List<Sorter> sorters = JsonHelper.listFromJson(Sorter.class, _sort);
                    for(Sorter sorter : sorters) {
                        addSorters(qo, sortCols, sorter.getProperty(), sorter.isAscending());
                    }
                }
                else {
                    addSorters(qo, sortCols, _sort, getDirection().equalsIgnoreCase("ASC"));
                }

            } else {
                // Look for a sort column in the request.
                for (Map.Entry<String,ColumnConfig> entry : sortCols.entrySet())
                {
                    String direction = super.getRequestParameter(entry.getKey());
                    if (null != direction)
                    {
                        boolean ascending = direction.equalsIgnoreCase("ASC");
                        ColumnConfig colConfig = entry.getValue();
                        List<String> cols = Util.csvToList(colConfig.getSortProperty());

                        if (null != cols) {
                            for (String col : cols) {
                                qo.addOrdering(col, ascending);
                            }
                            if(colConfig.getSecondarySort()!=null) {
                                qo.addOrdering(colConfig.getSecondarySort(), ascending);
                            }
                        }
                        break;
                    }
                }
            }

            // should we just automatically pick the first one?
            if (qo.getOrderings().isEmpty()) {
                String defaultSort = getDefaultSortColumn();
                boolean defaultOrder = "ASC".equalsIgnoreCase(getDefaultSortOrder());

                if (defaultSort != null) {
                    qo.addOrdering(defaultSort, defaultOrder);
                }

            }
        } else if(getSort() != null) {
            // if it starts with a bracket, we can assume it is a JSON array of sorters, ExtJS Store style.
            if(_sort.startsWith("[")) {
                @SuppressWarnings("unchecked")
                List<Sorter> sorters = JsonHelper.listFromJson(Sorter.class, _sort);
                for(Sorter sorter : sorters) {
                    addSorters(qo, sortCols, sorter.getProperty(), sorter.isAscending());
                }
            }
            else {
                boolean ascending = getDirection().equalsIgnoreCase("ASC");
                qo.addOrdering(_sort, ascending);
                if (getSecondarySort() != null) {
                    qo.addOrdering(getSecondarySort(), ascending);
                }
            }
        }
    }

    private void addSorters(QueryOptions qo, Map<String,ColumnConfig> sortCols, String prop, boolean ascending) {
        // replace the ExtJS-safe dashes with the dot notation used by the UI config
        ColumnConfig colConfig = sortCols.get(prop);

        if(colConfig != null) {
            List<String> cols = Util.csvToList(colConfig.getSortProperty());
            if (null != cols) {
                for (String col : cols) {
                    qo.addOrdering(col, ascending);
                }
                if(colConfig.getSecondarySort() != null) {
                    List<String> secondaries = Util.csvToList(colConfig.getSecondarySort());
                    for (String secondary : secondaries) {
                        qo.addOrdering(secondary, ascending);
                    }
                }
            }
        }
    }


    /**
     * Subclasses should extend to filter and sort results.
     *
     * @return A QueryOptions used to filter and sort results.
     */
    public QueryOptions getQueryOptions() throws GeneralException
    {
        QueryOptions qo = new QueryOptions();

        getSortOrdering(qo);
        if ( !_disableOwnerScoping ) {
            qo.setScopeResults(true);
            qo.addOwnerScope(super.getLoggedInUser());
        }
        return qo;
    }

    /**
     * Return a ListElementWrapper to use to proxy all elements returned by
     * getObjects().  Typically, this is used to wrap a SailPointObject with an
     * object that holds a SailPointContext so that the web tier can call
     * getters that would otherwise require a SailPointContext.  If this returns
     * null, the object list is not wrapped.
     *
     * @return A ListElementWrapper to use to proxy all elements returned by
     *         getObjects(), or null if you don't need proxying.
     */
    public Util.ListElementWrapper<E> getListElementWrapper() {
        return null;
    }

    /**
     * Overload this in the subclass to provide the default sort order.
     * If not specified, then ASC is the default.
     */
    public String getDefaultSortOrder() throws GeneralException {
        return "ASC";
    }

    /**
     * Overload this in the subclass to provide the name of the default sorting
     * attribute.  This now attempts to use the column configuration from
     * getColumns() to sort by the first non-hidden column.  If getColumns() is
     * not implemented, this must be overridden to make the results not be
     * randomly ordered.  This can be overridden to allow a column other than
     * the first column to be the default sort column.
     */
    public String getDefaultSortColumn() throws GeneralException {

        String defaultSortCol = null;

        List<ColumnConfig> cols = this.getColumns();
        if ((null != cols) && !cols.isEmpty()) {
            for (ColumnConfig col : cols) {
                if (col!=null && !col.isHidden() && col.isSortable()) {
                    defaultSortCol = col.getProperty();
                    break;
                }
            }
        }

        return defaultSortCol;
    }

    /**
     * Return a Map mapping the sort column request parameter name to the name
     * of the property (or comma-separated list of properties) on the object on
     * which to sort.  This now attempts to use the column configuration from
     * getColumns() to auto-generate the sort column map.  If getColumns() is
     * not implemented, this must be overridden to allow server-side sorting.
     * Subclasses can extend the map that is returned by this method to provide
     * more sophisticated sorting if required.
     *
     * @return A Map mapping the sort column request parameter name to the name
     *         of the property (or comma-separated list of properties) on the
     *         object on which to sort.
     */
    public Map<String,String> getSortColumnMap() throws GeneralException {

        Map<String,String> sortMap = null;

        List<ColumnConfig> cols = this.getColumns();
        if ((null != cols) && !cols.isEmpty()) {
            sortMap = new HashMap<String,String>();
            for (ColumnConfig col : cols) {
                if (col.isSortable()) {
                    sortMap.put(col.getJsonProperty(), col.getSortProperty());
                }
            }
        }

        return sortMap;
    }

    public Map<String,ColumnConfig> getSortColumnConfigMap() throws GeneralException {
        Map<String,ColumnConfig> sortMap = null;

        List<ColumnConfig> cols = this.getColumns();
        if ((null != cols) && !cols.isEmpty()) {
            sortMap = new HashMap<String,ColumnConfig>();
            for (ColumnConfig col : cols) {
                if (col!=null && col.isSortable()) {
                    String jsonProperty = col.getJsonProperty();
                    sortMap.put(jsonProperty, col);
                    // We should always use the data index.  I don't 
                    // want to regress anything so I'm just going to duplicate
                    // the property for now.  --Bernie 5/23/2012
                    String dataIndex = col.getDataIndex();
                    if (dataIndex != null && !dataIndex.equals(jsonProperty)) {
                        sortMap.put(dataIndex, col);
                    }
                }
            }
        }

        return sortMap;
    }

    /**
     * Return a list of the projection columns to retrieve.  This now attempts
     * to use the column configuration from getColumns() to auto-generate the
     * list of projection attributes.  If getColumns() is not implemented,
     * subclasses should override this.  If the grid needs properties that are
     * not returned for a specific column value (ie - the property is used to
     * calculate another value), subclasses can override and add the other
     * required properties to the lis treturned by the superclass.
     *
     * This is only used if the list uses getRows() or getGridResponseJson()
     * rather than getObjects().
     *
     * @return A list of the projection columns to retrieve for each row.
     */
    public List<String> getProjectionColumns() throws GeneralException {

        List<String> projectionAttributes = null;

        List<ColumnConfig> cols = getColumns();
        if ((null != cols) && !cols.isEmpty()) {
            projectionAttributes = new ArrayList<String>();
            for (ColumnConfig col : cols) {
                // Only include this as a projection attribute if it has a
                // property (ie - don't use dataIndex columns that are just
                // calculated).
                if (null != col.getProperty()) {
                    projectionAttributes.add(col.getProperty());
                }
            }
            
            /** Add any fieldOnly columns to the list **/
            if(_fieldColumns!=null) {
                for (ColumnConfig col : _fieldColumns) {
                    if (null != col.getProperty()) {
                        projectionAttributes.add(col.getProperty());
                    }
                }
            }

            // Always need an ID.
            if (!projectionAttributes.contains("id")) {
                projectionAttributes.add("id");
            }
        }

        return projectionAttributes;
    }

    /**
     * Overload this in the subclass to provide the displayable column names.
     * By default we will look in UIConfig for a list of columns keyed by the
     * class name.  Subclasses can override this if the list bean services
     * multiple grids (eg - the inbox and outbox).  Historically, list beans
     * always implemented this method, but it is a best practice to not
     * implement this if you don't have to.
     */
    @SuppressWarnings("unchecked")
    public List<ColumnConfig> getColumns() throws GeneralException {
        return getColumnsFor(getClass().getName());
    }
    
    /**
     * This logic has been refactored out of getColumns() to make it easier
     * for subclasses to get ColumnConfigs that are keyed by something 
     * other than the classname without having to copy and paste the majority
     * of this logic
     * @param configEntryKey The UIConfig entry that contains the list of 
     * ColumnConfigs to return 
     * @return List<ColumnConfig> for the specified entry
     * @throws GeneralException
     */
    protected List<ColumnConfig> getColumnsFor(String configEntryKey) throws GeneralException {
        if (null == _columns) {
            Object cols = getUIConfig().getAttributes().get(configEntryKey);
            if ((null != cols) && (cols instanceof List)) {
                // Could look at the first element to make sure it is the right
                // type.
                _columns = new ArrayList<ColumnConfig>();
                for(ColumnConfig config : (List<ColumnConfig>) cols) {
                    if(!config.isFieldOnly()) {
                        _columns.add(config);
                    } else {
                        this.addFieldColumn(config);
                    }
                }
            }
        }

        return _columns;
    }

    /** Returns a grid meta data that can be used to build grid column configs and provide
     * the list of fields to the datasource.
     * @return
     * @throws GeneralException
     */
    public String getColumnJSON() throws GeneralException{
        return getColumnJSON(getDefaultSortColumn(), getColumns(), _fieldColumns);
    }

    /**
     * Subclasses can override to provide the name of the grid state - this only
     * needs to be overridden if the list bean services multiple grids (eg - the
     * inbox and outbox).
     */
    public String getGridStateName() {
        // Default to the class name.  Subclasses can override.
        return getClass().getName();
    }

    public String getGridStateSessionKey() {
        return getGridStateName() + "SessionGridState";
    }
    
    public void setDisableOwnerScoping(boolean disable) {
        _disableOwnerScoping = disable;
    }

    public boolean getDisableOwnerScoping() {
        return _disableOwnerScoping;
    }


    //////////////////////////////////////////////////////////////////////
    //
    // UTILITY METHODS
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * A helper method that can do an in-memory sort and paging of a list of
     * results.  This should be used only if the sorting cannot be performed by
     * the database.  In some cases, we calculate column values and need to
     * sort of the calculated value.  If this is the case, the subclass should
     * override getQueryOptions() to clear the sorting and paging information
     * in the appropriate situations, and override getRows() to call this
     * method.  This is usually used when a subclass overrides convertColumn()
     * and makes use of clearSortingAndPaging() in getQueryOptions().
     */
    protected List<Map<String,Object>> sortAndPage(List<Map<String,Object>> rows)
    throws GeneralException {

        // Create a fresh QueryOptions to use to hold the ordering and paging
        // parameters.  The one from getQueryOptions() may have been mucked with
        // since we are explicitly sorting and paging.
        QueryOptions qo = new QueryOptions();
        this.getSortOrdering(qo);

        // Only support ordering by one column for now.
        List<Ordering> orderings = qo.getOrderings();
        if ((null == orderings) || (1 != orderings.size())) {
            throw new GeneralException("In-memory sorting currently only supports a single ordering.");
        }

        final String prop = orderings.get(0).getColumn();
        Comparator<Map<String,Object>> c = new Comparator<Map<String,Object>>() {

            public int compare(Map<String,Object> o1, Map<String,Object> o2) {
                Object v1 = o1.get(prop);
                Object v2 = o2.get(prop);

                if ((v1 != null) && (v2 != null)) {
                    return Collator.getInstance(getLocale()).compare(v1, v2);
                }
                else if ((null == v1) && (null == v2)) {
                    return 0;
                }

                return -1;
            }
        };

        // Sort and possibly reverse the list.
        Collections.sort(rows, c);
        if (!orderings.get(0).isAscending()) {
            Collections.reverse(rows);
        }

        // Trim the list to the current page.
        int start = qo.getFirstRow();
        int end = Math.min(rows.size(), start + qo.getResultLimit());
        rows = rows.subList(start, end);

        return rows;
    }

    /**
     * Clear the sorting and paging parameters off of the given QueryOptions.
     * Usually used when getRows() needs to do an in-memory sort of results.
     *
     * @see #sortAndPage(List)
     */
    protected void clearSortingAndPaging(QueryOptions qo) {
        qo.setFirstRow(-1);
        qo.setResultLimit(-1);
        qo.setOrderings(new ArrayList<Ordering>());
    }

    protected void setNameFilterMatchModeToAnywhere(QueryOptions qo) {
        qo.add(Filter.ignoreCase(Filter.like("name", getRequestParameter("name"), Filter.MatchMode.ANYWHERE)));
    }


    //////////////////////////////////////////////////////////////////////
    //
    // LISTING METHODS
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Return the JSON for a GridResponse object that has the results of the
     * list.  This can be used as the JSON datasource for the for the grid.
     * This delegates to getRows() and getCount() to calculate the interesting
     * information for the response.
     *
     * @return A JSON string with the GridResponse for this list bean.
     */
    public String getGridResponseJson() throws GeneralException {

        List<Map<String,Object>> rows;
        if (!_disableSearchLoad) {
            rows = getRows();
        } else {
            rows = new ArrayList<Map<String,Object>>();
        }

        makeJsonSafeKeys(rows);

        GridResponse response = new GridResponse(this.getMetaData(), rows, this.getCount());
        return JsonHelper.toJson(response);
    }

    /** Override **/
    public GridResponseMetaData getMetaData() {
        return null;
    }

    /**
     * Make all of the keys of the given list of Maps JSON-safe.  That is,
     * trade out all dots (periods) with dashes.
     */
    protected static void makeJsonSafeKeys(List<Map<String,Object>> rows)
    throws GeneralException {

        if (null != rows) {
            Map<String,String> keyFixes = new HashMap<String,String>();

            for (Map<String,Object> row : rows) {

                // First time through, look for any unsafe keys.
                if (keyFixes.isEmpty()) {
                    for (Map.Entry<String,Object> entry : row.entrySet()) {
                        String key = entry.getKey();
                        if(key.contains(".")) {
                            keyFixes.put(key, key.replace('.', '-'));
                        }
                    }

                    // We didn't find anything bad, bail out.
                    if (keyFixes.isEmpty()) {
                        return;
                    }
                }

                // If we got here there is stuff to fix, so do it.
                for (Map.Entry<String,String> entry : keyFixes.entrySet()) {
                    Object val = row.remove(entry.getKey());
                    row.put(entry.getValue(), val);
                }
            }
        }
    }

    /**
     * Return a list of attribute/value maps for each row returned by the query.
     */
    public List<Map<String,Object>> getRows() throws GeneralException {

        if (null == _rows) {
            List<String> cols = getProjectionColumns();
            assert (null != cols) : "Projection columns required for using getRows().";

            Iterator<Object[]> results =
                getContext().search(_scope, getQueryOptions(), cols);
            _rows = new ArrayList<Map<String,Object>>();
            while (results.hasNext()) {
                _rows.add(convertRow(results.next(), cols));
            }
        }
        return _rows;
    }

    /**
     * Convert a project search result into a map for easy manipulation
     * @param row
     * @param cols
     * @return
     * @throws GeneralException
     */
    protected static Map<String,Object> getRawQueryResults(Object[] row, List<String> cols)
        throws GeneralException {

        Map<String,Object> map = new HashMap<String,Object>(row.length);
        int i = 0;

        for (String col : cols) {
           map.put(col, row[i]);
            i++;
        }

        return map;
    }

    /**
     * Convert an Object[] row from a projection query into a attribute/value
     * Map.  This creates a HashMap that has a key/value pair for every column
     * names in the cols list. If the value of hte object implements Localizable,
     * the value will be localized.
     *
     * @param  row   The row to convert to a map.
     * @param  cols  The names of the projection columns returned in the object
     *               array.  The indices of the column names correspond to the
     *               indices of the array of values.
     *
     * @return An attribute/value Map for the converted row.
     */
    public Map<String,Object> convertRow(Object[] row, List<String> cols)
    throws GeneralException {

        Map<String,Object> map = new HashMap<String,Object>(row.length);
        int i = 0;

        List<ColumnConfig> columns = getColumns();

        for (String col : getProjectionColumns()) {

            ColumnConfig config = null;

            if(columns!=null) {
                for(ColumnConfig column : columns) {
                    if(column.getProperty()!=null && column.getProperty().equals(col)) {
                        config = column;
                        break;
                    }
                }
            }
            
            Object value = row[i];
            Object newValue = null;
            if (value != null) {
                // Try to localize the value first.
                newValue = localizeValue(value, config, col);
            }
            if (Util.nullSafeEq(newValue, value, true)) {
                // If we didn't localize it, give the subclass a chance to convert the value
                newValue = convertColumn(col, value);
            }
            value = newValue;

            map.put(col, value);
            // Really the data index should always be used, but I don't want to regress anything so I'm 
            // just going to copy the property over to its correct name if needed -- Bernie 5/23/2012
            if (config != null && config.getDataIndex() != null && !col.equals(config.getDataIndex())) {
                map.put(config.getDataIndex(), value);
            }
            i++;
        }
        return map;
    }

    /** Attempt so use reflection to fill a map with the values of a SailPointObject **/
    protected Map<String,Object> convertObject(Object object, String configKey) throws GeneralException{
        Map<String,Object> map = new HashMap<String,Object>();
        List<String> cols = getProjectionColumns();

        List<ColumnConfig> columns = getColumns();
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
                    value = ((Localizable)value).getLocalizedMessage(getLocale(), getUserTimeZone());
                }
                if(config!=null) {
                    // if the value can be localized. If so, localize it
                    if(config.getDateStyle() !=null) {
                        value = Util.dateToString((Date)value, config.getDateStyleValue(), config.getDateStyleValue(), getUserTimeZone(), getLocale());
                    }
                    /** Use the dataIndex if config is not null **/
                    map.put(config.getDataIndex(), value);

                } else {
                    map.put(col, value);
                }
            } catch(Exception ex) {
                log.info("Unable to get value for: " + col);
            }
        }
        return map;
    }

    /**
     * Localize the object automatically, if possible
     * @param value Value to be localized
     * @param config ColumnConfig for the value
     * @param columnName Name of the column
     * @return Localized value
     */
    public Object localizeValue(Object value, ColumnConfig config, String columnName) {
        Object newValue = value;
        if (newValue != null) {
            if (Localizable.class.isAssignableFrom(value.getClass())){
                newValue = ((Localizable)newValue).getLocalizedMessage(getLocale(), getUserTimeZone());
            } else if (config != null) {
                if (config.getDateStyle() != null) {
                    if(config.getTimeStyle() != null) {
                        newValue = Util.dateToString(Util.getDate(newValue), config.getDateStyleValue(), config.getTimeStyleValue(), getUserTimeZone(), getLocale());
                    } else {
                        newValue = Util.dateToString(Util.getDate(newValue), config.getDateStyleValue(), config.getDateStyleValue(), getUserTimeZone(), getLocale());
                    }
                } else if (config.isLocalize() && newValue instanceof String) {
                    //See comments for ColumnConfig.localize. We should assume value is a key and get the message. 
                    newValue = getMessage((String)newValue);
                }
            }
        }

        return newValue;
    }

    /**
     * Called by convertRow for each column value.
     * This is a hook for subclasses to process the value before display.
     * The initial use was to localize WorkItem types.
     */
    public Object convertColumn(String name, Object value) {
        return value;
    }

    public List<E> getObjects() throws GeneralException {
        if ( _objects == null ) {
            _objects = getContext().getObjects(_scope, getQueryOptions());
            Util.ListElementWrapper<E> wrapper = getListElementWrapper();
            if (null != wrapper) {
                _objects = Util.wrappedList(_objects, wrapper);
            }
        }
        return _objects;
    }

    public void setObjects(List<E> objects) {
        _objects = objects;
    }

    /**
     * Return the number of objects/rows returned by this list bean.
     *
     * @return The number of objects/rows returned by this list bean.
     */
    public int getCount() throws GeneralException {

        // Calculate the count if either a) it hasn't been calculated yet, or
        // b) the QueryOptions have changed since the last time the count was
        // calculated.  This ensures that we always return the correct count.
        // Specifically, if the request changes the list results (for example,
        // by adding a filter) the count first gets calculated and cached in
        // the "restore view" phase before the change to the filter was
        // assimilated (ie - during the "update model" phase).
        QueryOptions qo = getQueryOptions();

        if ((null == _count) ||
                ((null != qo) && !qo.equals(_countQueryOptions))) {
            _count = getContext().countObjects(_scope, qo);
            _countQueryOptions = qo;
        }

        return _count;
    }

    /**
     *
     * @return
     * @throws GeneralException
     */
    public Map<String,String> getSortedDisplayableNames(QueryOptions options) throws GeneralException {
        TreeMap<String,String> t = new TreeMap<String,String>(Collator.getInstance());
        t.putAll(getDisplayableNames(options));

        return t;
    }

    /**
     *
     * @return
     * @throws GeneralException
     */
    public Map<String,String> getSortedDisplayableNames() throws GeneralException {
        QueryOptions qo = new QueryOptions();
        qo.setScopeResults(true);
        return getSortedDisplayableNames(qo);
    }

    /**
     *
     * @return
     * @throws GeneralException
     */
    public Map<String,String> getDisplayableNames() throws GeneralException {
        QueryOptions qo = new QueryOptions();
        qo.setScopeResults(true);
        return getDisplayableNames(qo);
    }  // getDisplayableNames()

    /**
     *
     * @return
     * @throws GeneralException
     */
    public Map<String,String> getDisplayableNames(QueryOptions options) throws GeneralException {
        if ( _displayableNames == null ) {
            Map<String,String> m = new HashMap<String,String>();

            List<String> l = new ArrayList<String>();
            l.add("id");
            l.add("name");

            Iterator<Object []> result = getContext().search(_scope, options, l);
            if ( result != null ) {
                while ( result.hasNext() ) {
                    Object[] row = result.next();
                    // if a result does not have a name or id, then there
                    // is no reason to add it to the list
                    if ( row != null && row.length == 2 &&
                            row[0] != null && row[1] != null ) {
                        m.put(row[1].toString(), row[0].toString());
                    }
                }  // while result.hasNext()
            }  // if result != null

            _displayableNames = m;
        }  // if _displayableNames == null

        return _displayableNames;
    }  // getDisplayableNames()


    /*******************************************************************************
     * In Memory Stuff - The following are utility methods for resources that
     * have to pull all of the results into memory before sorting and limiting them
     * and sending them to the client
     *
     ******************************************************************************/

    /** Performs a trim of the results based on the start/limit and sorts the list
     * based on the sort by column.  This is for lists that must be brought into memory
     * before they can be sorted.
     * @param objects
     */
    public List<Map<String,Object>> trimAndSortResults(List<Map<String,Object>> objects) throws GeneralException {
        if(objects == null) {
            return null;
        }

        List<Map<String,Object>> newObjects = new ArrayList<Map<String,Object>>();
        String s = getSort();
        // if it starts with a bracket, we can assume it is a JSON array of sorters, ExtJS Store style.
        if(s.startsWith("[")) {
            List<Sorter> sorters = JsonHelper.listFromJson(Sorter.class, s);
            for(Sorter sorter : sorters) {
                Collections.sort(objects, new MapListComparator(sorter.getProperty()));
            }
        }
        else {
            Collections.sort(objects, new MapListComparator(s));
        }
        if ("DESC".equalsIgnoreCase(getDirection())) {
            Collections.reverse(objects);
        }

        if(getStart() < objects.size()) {
            if((getLimit()+getStart())>=objects.size()) {
                _limit = objects.size();
            } else {
                _limit = _start+_limit;
            }
            for(int i=_start; i<_limit; i++) {
                newObjects.add(objects.get(i));
            }
        }

        return newObjects;
    }



    //////////////////////////////////////////////////////////////////////
    //
    // UI BEAN STATE
    //
    //////////////////////////////////////////////////////////////////////

    public String getSelectedId()
    {
        return _selectedId;
    }

    public void setSelectedId(String selectedId)
    {
        _selectedId = selectedId;
    }

    public Map<String, Boolean> getSelected()
    {
        return _selected;
    }

    public void setSelected(Map<String, Boolean> m)
    {
        _selected = m;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // ACTIONS
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * An action method called when an identity is selected from the list.
     *
     * TODO: Append id to nav.  Forward doesn't work right so we have to
     * redirect, but we need to let the certification page know the ID to
     * display.  Answer: query parameter.
     *
     * See: http://wiki.apache.org/myfaces/Custom_Navigation_Handler
     *
     * For now, we'll handle this with putting the ID on the session.  This
     * of course has the normal session problems such as stomping on state
     * when you open another window, etc...
     */
    @SuppressWarnings("unchecked")
    public String select() throws GeneralException
    {
        String next = null;
        String selected = getSelectedId();

        if (selected == null || selected.length() == 0) {
            // can get here by pressing return in the filter box without
            // clicking on the Search button, which I guess makes it
            // look like a click in the live grid
            next = null;
        }
        else {
            getSessionScope().put(ATT_EDITFORM_ID, selected);
            getSessionScope().put(ATT_SELECTED_ID, selected);

            // bug#302, jsl - if we left an object hanging and returned
            // to the table with tab navigation or the back button,
            // it needs to be treated like a cancel.  This is the convention
            // BaseObjectBean uses.
            getSessionScope().put(BaseObjectBean.FORCE_LOAD, "true");

            next = Consts.NavigationString.edit.name();
        }
        if (this instanceof NavigationHistory.Page) {
            NavigationHistory.getInstance().saveHistory((NavigationHistory.Page) this);
        }

        return next;
    }  // select()

    /**
     * "Delete" action handler for the list page.
     * Remove the object from the persistent store.
     *
     * @throws GeneralException
     */
    public void deleteObject(ActionEvent event) {
        if ( _selectedId == null ) return;

        E obj = null;
        try {
            obj = getContext().getObjectById(_scope, _selectedId);
        } catch (GeneralException ex) {
            String msg = "Unable to find object with id '" +
            _selectedId + "'.";
            log.error(msg, ex);
            addMessage(new Message(Message.Type.Error, MessageKeys.ERR_FATAL_SYSTEM), null);
        }

        if ( obj == null ) return;

        try {
            getContext().removeObject(obj);
            getContext().commitTransaction();
        } catch (GeneralException ex) {
            String msg = "Unable to remove object with id '" +
            _selectedId + "'.";
            log.error(msg, ex);
            addMessage(new Message(Message.Type.Error, MessageKeys.ERR_FATAL_SYSTEM), null);
        }
    }  // deleteObject(ActionEvent)


    /** An action method called from the ui that will allow a user to export
     * the contents of a list to csv **/
    public void exportToCSV() throws Exception{
        FacesContext fc = FacesContext.getCurrentInstance();
        HttpServletResponse response = (HttpServletResponse) fc.getExternalContext().getResponse();
        response.setCharacterEncoding("UTF-8");
        
        PrintWriter out;
        try {
            out = response.getWriter();
        } catch (Exception e) {
            return;
        }

        try {
            List<ColumnConfig> columns = getColumns();
            List<Map<String,Object>> rows = getRows();

            if(columns!=null && rows!=null) {

                /** Print header using localized messages **/
                for(int i=0; i<columns.size(); i++) {
                    ColumnConfig column = columns.get(i);

                    if (isColumnInCsv(column)) {
                        String header = getMessage(column.getHeaderKey());
                        out.print(header);
                        if(i<(columns.size()-1))
                            out.print(",");
                    }
                }
                out.print("\n");

                /** Print rows **/
                for(Map<String,Object> row : rows) {
                    for(int i=0; i<columns.size(); i++) {
                        ColumnConfig column = columns.get(i);
                        
                        if (isColumnInCsv(column)) {
                            
                            String property = column.getProperty();
                            Object value = getColumnValue(row, property);

                            StringBuilder sb = new StringBuilder();
                            if(value!=null){
                                sb = sb.append(value.toString());

                                // escape any quotes within the value
                                int pos = 0;
                                while((pos = sb.indexOf("\"", pos)) != -1) {
                                    sb = sb.insert(pos, "\"");
                                    pos = pos + 2;
                                }

                                // wrap the value in quotes if it contains a comma
                                if(sb.indexOf(",") != -1 || sb.indexOf("\n") != -1 || sb.indexOf("\r") != -1) {
                                    sb = sb.insert(0, "\"");
                                    sb = sb.append("\"");
                                }
                            }

                            // If the value was null, this will output an empty string so
                            // the rest of the values for this row don't shift left once
                            out.print(sb.toString());
                            if(i<(columns.size()-1))
                                out.print(",");
                        }
                    }
                    out.print("\n");
                }
            }
        } catch (Exception e) {
            log.warn("Unable to export to csv due to exception: " + e.getMessage());
            addMessage(new Message(Message.Type.Error, MessageKeys.ERR_CSV_EXPORT_EXCEPTION), null);
            return;
        }
        out.close();
        response.setHeader("Content-disposition", "attachment; filename=\""
                + getFilename()+ ".csv" + "\"");
        response.setHeader("Cache-control", "must-revalidate, post-check=0, pre-check=0");
        response.setHeader("Pragma", "public");
        response.setContentType(MIME_CSV);
        fc.responseComplete();
    }

    /**
     * Check if column should be included in CSV
     * Only show columns that have a property and are not field-only
     */
    private boolean isColumnInCsv(ColumnConfig column) {
        return (column != null && 
                column.getProperty() != null &&
                !column.isFieldOnly());
    }

    /** Used by a subclass to specify the name of the csv file
     * that will be created by the exportToCSV method
     * Should be overridden by the subclass */
    public String getFilename() {
        return "baseList";
    }

    /**
     * Overload this in the subclass in order to provide different values
     * for each column depending on how the subclass wants to display
     */
    public Object getColumnValue(Map<String, Object> row, String column) {
        Object value = row.get(column);
        return value;
    }

    /** load a configured gridstate object based on what type of cert it is **/
    public GridState loadGridState(String gridName) {
        GridState state = null;
        String name = "";
        
        /* This is the right thing to do going forward, but given where we are in 
         * the 6.0 release now is not the time to do it.  This change fixed the problems
         * with maintaining grid state on the Entitlement Catalog page.  Since the grid 
         * state seems to be working for other pages we'll contain this fix there.  
         * We should look into fixing this application-wide post 6.0 so that this actually
         * works the way the GridState class documentation says it should. --Bernie
        // Check the session for a grid state first.  If none is found pull it 
        // out of the user preferences
        String sessionKey = getGridStateSessionKey();
        if (sessionKey != null) {
            state = (GridState) getSessionScope().get(sessionKey);
        }
         */
        
        // if (state == null) {
        IdentityService iSvc = new IdentityService(getContext());
        try {
            if(gridName!=null)
                state = iSvc.getGridState(getLoggedInUser(), gridName);
        } catch(GeneralException ge) {
            log.info("GeneralException encountered while loading gridstates: "+ge.getMessage());
        }            
        // }

        if(state==null) {
            state = new GridState();
            state.setName(name);
        }
        
        /* See comments above
        if (sessionKey != null) {
            getSessionScope().put(sessionKey, state);            
        }
        */
        
        return state;
    }

    public GridState getGridState() {
        if (null == this._gridState) {
            _gridState = loadGridState(getGridStateName());
        }
        return _gridState;
    }

    public void setGridState(GridState gridState) {
        this._gridState = gridState;
    }

    protected String getTaskDateString(Date date, boolean showPending) {
        String dateVal;
        if (date == null) {
            if (showPending)
                dateVal = getMessage(MessageKeys.TASK_RESULT_PENDING);
            else
                dateVal = "";
        } else {
            dateVal = Internationalizer.getLocalizedDate(date, getLocale(), getUserTimeZone());
        }
        return dateVal;
    }

    protected String getResultStatusString(TaskResult result) {
        TaskResult.CompletionStatus status = 
            (result == null) ? null : result.getCompletionStatus();
        
        return getCompletionStatusString(status);
    }
    
    protected String getCompletionStatusString(TaskResult.CompletionStatus status) {
        String statusString = "";
        if (status != null) {
            statusString = getMessage(status.getMessageKey());
        }
        return statusString;
    }
    
    protected int getResultStatusId(TaskResult result) {
        TaskResult.CompletionStatus status = 
            (result == null) ? null : result.getCompletionStatus();
        
        return getCompletionStatusId(status);
    }
    
    protected int getCompletionStatusId(TaskResult.CompletionStatus status) {
        int statusId = -1;
            if (null != status)  {
                switch (status){
                   case Error:
                        statusId = 0;
                        break;
                    case Warning:
                    case Terminated:
                        statusId = 2;
                        break;
                    case Success:
                        statusId = 1;
                        break;
            }
        }

        return statusId;
    }

    /**
     * Initialize an export monitor and put it on the session.  This will be
     * polled and eventually removed by the ExportResource.
     */
    @SuppressWarnings("unchecked")
    protected ReportExportMonitor initExportMonitor() {
        ReportExportMonitor taskMonitor = new ReportExportMonitor();
        getSessionScope().put(EXPORT_MONITOR, taskMonitor);
        return taskMonitor;
    }
    
    public String getIdentityName(Identity identity) {
        String identityName;
        if (identity == null) {
            identityName = getMessage(MessageKeys.NONE);
        } else {
            identityName = identity.getDisplayName();
        }
        return identityName;
    }


    public int getStart() {
        if(_start==0)
            _start = Util.atoi(getRequestParameter("start"));
        return _start;
    }

    public void setStart(int start) {
        _start = start;
    }

    public int getLimit() {
        if(_limit==0)
            _limit = getResultLimit();
        return _limit;
    }

    public void setLimit(int limit) {
        _limit = limit;
    }
    
    public void addFieldColumn(ColumnConfig column) {
        if(_fieldColumns==null) {
            _fieldColumns = new ArrayList<ColumnConfig>();
        }
        _fieldColumns.add(column);
    }


}  // class BaseListBean
