/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * Backing bean for the Identity selection table.
 * This is more complicated than most BaseListBeans due to the scale of
 * Identity objects.  Consider factoring out a reusable ScalableListBean
 * if we need this more than once.  Among the issues are:
 *
 * UPDATE: Not dealing with these issues, just using a scrollable LiveGrid
 * which is adequate but still may become too slow as the size of the
 * identity table increases.
 *
 * 1) Deferred object fetching
 *
 * We do not want to fully materialize all Identity objects just to 
 * populate the table.  Need to use the Projection facility to 
 * request only those attributes we need to display.
 *
 * 2) Maximum results
 *
 * There must be a limit placed on the number of results.  For large
 * scale result sets, a few hundred is generally more than enough since
 * users aren't laboriuosly scrolling through them one page at a time.
 * If there are too many results, they refine the search criteria and
 * generate a new result.
 *
 * We could theoretically avoid this if SailPointContext and Hibernate
 * provided a way to obtain a handle to a live SQL cursor rather than 
 * building a memory model for the entire result.  But this would also
 * cause issues for clustering, we would require host affinity since
 * cursors cannot be transfered.
 *
 * 3) Cached results
 *
 * Even with the previous two optimizations, it is still relatively
 * expensive to go back to the database every time this page
 * is refreshed.  Especially annoying if you're bouncing between the
 * list and edit pages.
 *
 * The tradeff of cource is cache invalidation.  How long do we keep
 * this cache since we can't easily detect if it becomes stale?
 *
 * 4) Filtered results
 *
 * With large result sets, scrollable tables are only practical if
 * you have the ability to specify filter criteria to limit the number
 * of results.  
 *
 * 5) Table column sorting
 *
 * The combination of having a maximum result limit and a result cache
 * potentially causes problems for table sorting.  If for example 
 * you were displaying an unfiltered list of identities, and you did
 * an inverse sort on the "Name" column, you might expect to see 
 * names beginning with 'Z' at the top.  Unless the cache was reset,
 * you would instead see the names at the bottom of the cache, which
 * may be far from Z.
 *
 * We could invalidate the cache before every column sort but this
 * then could result in inconsistent result rows.  The sort changes
 * the order of all rows, but the result row limit is still causing us
 * to use only the first N rows.  For example if you start ordering
 * by name, then want to see it ordered by modification date, the names
 * could completely change.
 *
 * It feels that the first issue (sorting based on the full result)
 * is more important than the second (consistent rows during sorting).
 * We can make this configurable or add a "result lock" flag but for
 * now will err on the side of full result sorting.
 *
 * 6) Cache Invalidation
 *
 * This is less of an issue if we decache on every column sort.
 * If we don't decache on sorts, then we will at least decache every
 * time the filter changes, or whenever the "Search" or "Refresh" 
 * button is pressed again.
 *
 * However, it is more of an issue when you leave the list page for
 * a long period of time then return.  The expectation is that the
 * contents of the page will be accurate, but without automatic cache
 * invalidation you would have to manually click a Refresh button.
 *
 * The current thinking is that Refresh buttons suck, so without
 * a timeout of some kind, we can't maintain a cache.  We'll have
 * to wait and see how annoying this.
 *
 * FILTER NOTES:
 *
 */
package sailpoint.web.identity;

import java.util.*;

import org.apache.commons.beanutils.PropertyUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.*;
import sailpoint.object.Filter.MatchMode;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.web.BaseListBean;
import sailpoint.web.Consts;
import sailpoint.web.util.NavigationHistory;
import sailpoint.web.util.WebUtil;

/**
 * managedBeanName = "identityList"
 * scope = request
 */
public class IdentityListBean extends BaseListBean<Identity> implements NavigationHistory.Page {

	private static final Log log = LogFactory.getLog(IdentityListBean.class);

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * The name of an HttpSession attribute where we store the filter
     * between requests.
     */
    public static final String ATT_FILTER = "IdentityListFilter";
    public static final String GRID_STATE = "identityListGridState";

    private static final String CFG_ALLOW_WORKGROUPS_IN_IDENTITY_LIST_BEAN = "allowWorkgroupsInIdListBean";

    /**
     * The primary name filter string.
     * Currently expecting this to be a simple wildcard used to match
     * name, firstname, or lastname.
     *
     * Will likely want more of these for specific attributes like
     * manager, modificationDate, bundles, and links.
     */
    String _filter;


    /** PH
     * Used by other beans if they want to instantiate an IdentityListBean with an
     * actual filter object.
     */
    Filter _filterObj;

    /**
     * The configured attributes we will display for the identity.
     */
    List<ColumnConfig> _columns;

    /**
     * Cached list of Identity properties we ask for in the query.
     */
    List<String> _projectionAttributes;

    /**
     * Cached list of Identity properties we may include in search filters.
     */
    List<String> _searchAttributes;

    /**
     * Special result representation.
     */
    List<IdentityProxy> _rows;

    boolean convertResultsToIdentityProxy = true;

    boolean uiMaxManagers;

    GridState _gridState;
    //////////////////////////////////////////////////////////////////////
    //
    // Constructor/Properties
    //
    //////////////////////////////////////////////////////////////////////

    public IdentityListBean() {
        super();
        if (log.isInfoEnabled()) {
        	log.info("IdentityListBean(): @" + Integer.toHexString(hashCode()));
        }
        setScope(Identity.class);
        restore();
    }

    ////////////////////////////////////////////////////////////////////////////
    //
    // NavigationHistory.Page methods
    //
    ////////////////////////////////////////////////////////////////////////////

    public String getPageName() {
        return "Identity List Page";
    }

    public String getNavigationString() {
        return "identities";
    }

    public Object calculatePageState() {
        Object[] state = new Object[1];
        state[0] = this.getGridState();
        return state;
    }

    public void restorePageState(Object state) {
        Object[] myState = (Object[]) state;
        setGridState((GridState) myState[0]);
    }

    /**
     * Sigh, have to save the filter on the HttpSession until we can figure
     * out a way to be a session scoped bean without confusing the
     * infrastructure built up around BaseBean.
     */
    private void restore() {

        Map<String, Object> session = getSessionScope();
        Object o = session.get(ATT_FILTER);
        if (o != null) {
            _filter = o.toString();
        } else {
            _filter = null;
    	}
    }

    private void save() {

        getSessionScope().put(ATT_FILTER, _filter);
    }

    public String selectIdentity() throws GeneralException{
        /**The last tab clicked on the identity page is cached on the session,
         * If a new identity is selected, clear out the active tab so we start on the 
         * first tab.
         */
    	getSessionScope().put(IdentityPagingBean.ATT_IDENTITY_IDS, null);

        String next = null;
        String selected = getSelectedId();

        if (selected == null || selected.length() == 0) {
            // can get here by pressing return in the filter box without
            // clicking on the Search button, which I guess makes it
            // look like a click in the live grid
            next = null;
        }
        else {
            next = Consts.NavigationString.edit.name();
        }
        NavigationHistory.getInstance().saveHistory((NavigationHistory.Page) this);

        return next;
    }

    public void setFilter(String s) {
        _filter = s;
        save();
    }

    public String getFilter() {
        return _filter;
    }

    public List<ColumnConfig> getColumns()
        throws GeneralException {

        if (_columns == null) {
        	_columns = super.getUIConfig().getIdentityTableColumns();

            /** Do special processing to grab the display names from the object config **/
            ObjectConfig identityConfig = getContext().getObjectByName(ObjectConfig.class, ObjectConfig.IDENTITY);
            if(identityConfig!=null) {
                for(ColumnConfig column : _columns) {
                    String property = column.getProperty();
                    ObjectAttribute attribute = identityConfig.getObjectAttribute(property);
                    if(attribute!=null && attribute.getDisplayName()!=null) {
                        column.setHeaderKey(attribute.getDisplayName());
                    } else if(attribute!=null) {
                        column.setHeaderKey(attribute.getName());
                    }
                }
            }

        }
        return _columns;
    }

    public int getColumnCount() throws GeneralException {
        List<ColumnConfig> columns = getColumns();
        return ((columns != null) ? columns.size() : 0);
    }

    /**
     * Return the list of configured identity attributes we can
     * search on.
     */
    public List<String> getSearchAttributes()
        throws GeneralException {

        if (_searchAttributes == null) {
            _searchAttributes = new ArrayList<String>();

            UIConfig uiConfig = getUIConfig();
            if (uiConfig != null) {
                List<String> atts = uiConfig.getIdentitySearchAttributesList();
                if (atts != null)
                    _searchAttributes.addAll(atts);
            }
        }
        return _searchAttributes;
    }

    /**
     * Return the list of attributes we request in the search projection.
     * Same as searchAttributes plus the hidden id.
     */
    @Override
    public List<String> getProjectionColumns() throws GeneralException {

        if (_projectionAttributes == null) {
            _projectionAttributes = new ArrayList<String>();

            List<ColumnConfig> cols = getColumns();
            if (cols != null) {
                for (ColumnConfig col : cols) {
                    _projectionAttributes.add(col.getProperty());
                }
            }
        }
        return _projectionAttributes;
    }

    /**
     * BaseListBean calls this as it builds the default
     * set of QueryOptions for getQueryOptions.
     *
     * The value will be "ASC" for ascending.  Translation of this
     * is handled down in BaseListBean.
     */
    @Override
    public Map<String,String> getSortColumnMap()
    {
        Map<String,String> sortCols = new HashMap<String,String>();
        try {
            List<ColumnConfig> cols = getColumns();
            if (cols != null) {
                for (ColumnConfig col : cols) {
                	//the key col.getJsonProperty() here is same as what is used at the identities ExtJS grid level
                	//when the key col is posted for sorting
                	sortCols.put(col.getJsonProperty(), col.getSortProperty());
                }
            }
        }
        catch (Throwable t) {
            // method we're overriding can't throw
        }
        return sortCols;
    }

    /**
     * BaseListBean calls this to get the default attribute
     * to sort on if there was no sort control posted on
     * the last request.  The first column is always the
     * hidden id field.
     */
    @Override
    public String getDefaultSortColumn()
        throws GeneralException {

        String column = null;
        List<ColumnConfig> cols = getColumns();
        if (cols != null && cols.size() > 1)
            column = cols.get(1).getProperty();
        return column;
    }

    /**
     * Overload this to pass our search filters down to
     * BaseObjectBean when generating the backing Identity list.
     */
    public QueryOptions getQueryOptions() throws GeneralException
    {
        // let this handle sort options
        QueryOptions ops = super.getQueryOptions();

        // Performance hack: there's a configuration option
        // to allow workgroups listed in the Define --> Identities
        // screen. This isn't an ideal place to show workgroups, but
        // a minor distraction in favor of severe performance degredation
        Configuration cfg = getContext().getConfiguration();
        if (cfg != null && cfg.getBoolean(CFG_ALLOW_WORKGROUPS_IN_IDENTITY_LIST_BEAN)) {
            List trueFalse = new ArrayList();
            trueFalse.add(true);
            trueFalse.add(false);
            // hacky way of skipping the workgroup filter: Hibernate PM will see
            // this and pop all "is workgroup" filters out (including this one)
            ops.add(Filter.in("workgroup", trueFalse));
        }
        ops.setScopeResults(true);

        if(_filterObj != null) {
            ops.add(_filterObj);
            // jsl - Since we can't control what's in here, have
            // to add distinct.  Some iPOP filters are joins
            // on the Link table which can cause duplicates.    
            ops.setDistinct(true);
        }

        getQueryOptionsFromRequest(ops);

        return ops;
    }

    /** Retrieves any passed in filters from the request **/
    public void getQueryOptionsFromRequest(QueryOptions ops) throws GeneralException
    {
    	// right now, name is the only filter option for identities from the request
    	String name = getRequestParameter("name");
    	if((name != null) && (!name.equals(""))) {
    		Identity tempIdent = new Identity();
            List<Filter> filters = new ArrayList<Filter>();
	        List<String> satts = getSearchAttributes();
	        for (String att : satts) {

	        	/** Need to protect ourselves from non-string properties **/
	        	try {
	        		Class<?> clazz = PropertyUtils.getPropertyDescriptor(tempIdent, att).getPropertyType();
	        		if(!clazz.equals(String.class)) {
	        			continue;
	        		}
	        	} catch(Exception e) {}

	        	Filter attributeFilter = null;
	        	if (isMultiProperty(att)) {
	        	    attributeFilter = getMultiFilter(att, name);
	        	} else {
	        	    attributeFilter = getFilter(att, name);
	        	}

	            filters.add(attributeFilter);
	        }
	        ops.add(Filter.or(filters));
    	}
    }

    //TODO: cache this?
    public static boolean isMultiProperty(String att) {

        ObjectConfig identityConfig = ObjectConfig.getObjectConfig(Identity.class);
        List<ObjectAttribute> multiAttrs = identityConfig.getMultiAttributeList();
        for (ObjectAttribute multiAttr : multiAttrs) {
            if (multiAttr.getName().equals(att)) {
                return true;
            }
        }
        return false;
    }

    public static Filter getFilter(String att, String val) {

        Filter like = Filter
                .ignoreCase(Filter.like(att, val, MatchMode.START));
        Filter nn = Filter.notnull(att);

        return Filter.and(nn, like);
    }

    public static Filter getMultiFilter(String att, String val) {

        // We used to use ExternalFilterBuilder here, but this is faster.
        // If we can make ExternalFilterBuilder as fast as this, then we should
        // use it here, so that we're querying all multi-valued attributes
        // the same way.

        Filter whereClause = Filter.and(
                Filter.ignoreCase(Filter.like("value", val, MatchMode.START)),
                Filter.eq("attributeName", att));

        /**
         * select *
         *   from Identity
         *   where id in (select object_id from spt_identity_external_attr
         *     where value like 'val%')
         **/

        return Filter.subquery("id", IdentityExternalAttribute.class, "objectId",
                 whereClause);
    }


    @Override
    public Map<String,Object> convertRow(Object[] row, List<String> columns)
            throws GeneralException {
        if (this.convertResultsToIdentityProxy) {
            return new IdentityProxy(row, columns, getUserTimeZone(), getLocale());
        } else {
            Map<String,Object> converted = super.convertRow(row, columns);
            //Get the score color. We made sure score was not null in convertColumn
            converted.put("scorecard.color", WebUtil.getScoreColor((Integer)converted.get("scorecard.compositeScore")));
            return converted;
        }
    }

    @Override
    public Object convertColumn(String name, Object value) {
        if ("scorecard.compositeScore".equals(name)) {
            return (value == null) ? 0 : value;
        } else if ("type".equals(name) && value != null) {
            ObjectConfig identityConfig = Identity.getObjectConfig();
            IdentityTypeDefinition def = identityConfig.getIdentityType(value.toString());
            return def == null ? null : getMessage(def.getDisplayName());
        }
        return value;
    }

    @Override
    public String getGridResponseJson() throws GeneralException {
        // Check if we need to return blank results
        boolean disableInitialLoad = getContext().getConfiguration().getBoolean("disableInitialIdentitiesGridLoad", false);
        String searchParam = getRequestParameter("name");
        if (disableInitialLoad && Util.isNullOrEmpty(searchParam)) {
            _disableSearchLoad = true;
        }

        //In convertRow, we historically made the row into an IdentityProxy object.
        //For grid JSON, we don't want this, so set a flag to skip it. 
        try {
            this.convertResultsToIdentityProxy = false;
            return super.getGridResponseJson();
        } finally {
            this.convertResultsToIdentityProxy = true;
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Actions
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Action method called to refresh the search result.
     * If we're not caching this is just a normal refresh with
     * the possibly modified filter string.
     */
    public String refresh() throws GeneralException {

        _rows = null;

        // stay on this page
        return null;
    }

    /**
     * Resets the filter text and refreshes the page.
     *
     * @return null
     * @throws GeneralException
     */
    public String clearSearch() throws GeneralException {
    	setFilter("");
        return  refresh();
    }

    /**
     * @return the _filterObj
     */
    public Filter getFilterObj() {
        return _filterObj;
    }

    /**
     * @param obj the _filterObj to set
     */
    public void setFilterObj(Filter obj) {
        _filterObj = obj;
    }

    public String getGridStateName() {
    	return GRID_STATE;
    }


}
