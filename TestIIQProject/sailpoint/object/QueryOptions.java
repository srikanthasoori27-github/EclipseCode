/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
/**
 * Class used to specify options to a database query.
 * 
 * Author: Rob
 */
package sailpoint.object;

import org.apache.commons.lang3.builder.HashCodeBuilder;

import sailpoint.tools.Util;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Class used to specify options to a database query.
 */
public class QueryOptions implements Serializable
{
    
    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////
    public static int MAX_WORKGROUPS_SIZE = 100;

    private List<Ordering> _orderings = new ArrayList<Ordering>();
    private List<String> _groupBys = new ArrayList<String>();

    private boolean _distinct;
    private boolean _ignoreCase;
    private int _firstRow;
    private int _resultLimit;
    private List<Filter> _filters = new ArrayList<Filter>();
    private List<Filter> _scopeExtensions = new ArrayList<Filter>();
    private Boolean _scopeResults;
    private Boolean _unscopedGloballyAccessible;
    private String _query;
    private boolean _transactionLock;

    private boolean _cacheResults;
    private boolean _flushBeforeQuery;

    //Due to a bug in Hibernate ignoring ResultSet holdability, we must clone results into memory if the session will
    //commit before fully iterating the results. https://hibernate.atlassian.net/browse/HHH-10394 -rap
    private boolean _cloneResults;
    
    //Transaction level read uncommitted at the lowest levels - aka dirty reads prevent locking
    private boolean _dirtyRead;
    
    //////////////////////////////////////////////////////////////////////
    //
    // Ordering
    //
    //////////////////////////////////////////////////////////////////////

    public static class Ordering {
        private String column;
        private boolean ascending;
        private boolean ignoreCase;
        
        public Ordering(String column, boolean ascending, boolean ignoreCase) {
            this.column = column;
            this.ascending = ascending;
            this.ignoreCase = ignoreCase;
        }
        
        public Ordering(String column, boolean ascending) {
            this.column = column;
            this.ascending = ascending;
            this.ignoreCase = false;
        }

        public String getColumn() {
            return this.column;
        }
        
        public void setColumn(String column) {
            this.column = column;
        }

        public boolean isAscending() {
            return this.ascending;
        }

        public void setAscending(boolean ascending) {
            this.ascending = ascending;
        }
        
        public boolean isIgnoreCase() {
            return this.ignoreCase;
        }
        
        /**
         * Specifies that the order by column has a case insensitive index.
         * There is no longer a need to set this explicitly, starting in 
         * 7.1 the persistence layer will determine this automatically.
         */
        @Deprecated
        public void setIgnoreCase(boolean ignoreCase) {
            this.ignoreCase = ignoreCase;
        }
        
        @Override
        public String toString() {
            return this.column + " " + ((this.ascending) ? "ascending" : "descending");
        }

        @Override
        public boolean equals(Object o) {
            if (o == this)
                return true;
            
            if (!(o instanceof Ordering))
                return false;
            
            Ordering ordering = (Ordering) o;
            return Util.nullSafeEq(this.column, ordering.column) &&
                   this.ascending == ordering.ascending;
        }

        @Override
        public int hashCode() {
            return Util.nullSafeHashCode(this.column) ^
                   ((this.ascending) ? 1 : -1);
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Constructors
    //
    //////////////////////////////////////////////////////////////////////

    public QueryOptions()
    {
    }

    public QueryOptions(Filter... filters)
    {
        if (filters!=null){
            for(Filter filter : filters){
                this.add(filter);
            }
        }
    }

    /**
     * Copy constructor
     * @param optionsToClone The QueryOptions that you want to copy
     */
    public QueryOptions(QueryOptions optionsToClone) {
        _distinct = optionsToClone._distinct;
        _ignoreCase = optionsToClone._ignoreCase;
        _firstRow = optionsToClone._firstRow;
        _resultLimit = optionsToClone._resultLimit;
        if (optionsToClone._filters != null && !optionsToClone._filters.isEmpty()) {
            _filters.addAll(optionsToClone._filters);
        }
        _query = optionsToClone._query;
        _transactionLock = optionsToClone._transactionLock;
        _cacheResults = optionsToClone._cacheResults;
        _flushBeforeQuery = optionsToClone._flushBeforeQuery;        
        if (null != optionsToClone._orderings) {
            _orderings.addAll(optionsToClone._orderings);
        }
        if (null != optionsToClone._groupBys) {
            _groupBys.addAll(optionsToClone._groupBys);
        }
        _dirtyRead = optionsToClone._dirtyRead;
    }
 
    //////////////////////////////////////////////////////////////////////
    //
    // Properties
    //
    //////////////////////////////////////////////////////////////////////

    public int getResultLimit()
    {
        return _resultLimit;
    }

    public QueryOptions setResultLimit(int limit)
    {
        _resultLimit = limit;
        return this;
    }

    public QueryOptions setFirstRow(int row) {
        _firstRow = row;
        return this;
    }

    public int getFirstRow()
    {
        return _firstRow;
    }

    /**
     * Convenience method to set order ascending by a single column. This will
     * throw an exception if there is more than one ordering or there is a
     * single ordering that already has a property name. Left for backwards
     * compatibility.
     */
    public QueryOptions setOrderBy(String name) {

        // Throw if there are already multiple orderings.
        if (_orderings.size() > 1) {
            throw new RuntimeException("Cannot set orderBy when multiple orderings have been added - use addOrdering()");
        }
        
        if (1 == _orderings.size()) {
            Ordering ordering = _orderings.get(0);

            // Throw if this ordering already has a property name.
            if (null != ordering.getColumn()) {
                throw new RuntimeException("The orderBy has already been set.");
            }

            ordering.setColumn(name);
        }
        else {
            _orderings.add(new Ordering(name, true));
        }

        return this;
    }

    /**
     * Convenience method to set whether to order ascending or descending. This
     * will throw an assertion error if there are multiple orderings. Left for
     * backwards compatibility.
     */
    public QueryOptions setOrderAscending(boolean b) {

        if (_orderings.size() > 1) {
            throw new RuntimeException("Cannot set ascending when multiple orderings have been added - use addOrdering()");
        }
        
        if (1 == _orderings.size()) {
            _orderings.get(0).setAscending(b);
        }
        else {
            _orderings.add(new Ordering(null, b));
        }

        return this;
    }

    /**
     * Add an ordering to these QueryOptions. These will be applied in the
     * order in which they are added.
     * 
     * @param  name       The name of the property to order by.
     * @param  ascending  Whether to order ascending or descending.
     */
    public QueryOptions addOrdering(String name, boolean ascending) {
        return addOrdering(null, name, ascending, false);
    }
    
    public QueryOptions addOrdering(String name, boolean ascending, boolean ignoreCase) {
        return addOrdering(null, name, ascending, ignoreCase);
    }
    
    public QueryOptions addOrdering(Integer index, String name, boolean ascending) {
        return addOrdering(index, name, ascending, false);
    }
    
    public QueryOptions addOrdering(Integer index, String name, boolean ascending, boolean ignoreCase) {
        int orderIndex = getOrderingIndex(name);
        
        if (null == index) {
            if (orderIndex == -1) {
                // if no index was specified and the ordering doesn't already exist, add it
                _orderings.add(new Ordering(name, ascending, ignoreCase));
            }
        } else {
            // if an index was specified, remove it if it's there already and add it to its new index
            if (orderIndex > -1) {
                _orderings.remove(orderIndex);
                if (index > orderIndex) {
                    // Since we've removed one, we've changed the size of the orderings,
                    // we have to account for that
                    index--;
                }
            }
            _orderings.add(index, new Ordering(name, ascending, ignoreCase));
        }
        
    	return this;
    }
    
    public int getOrderingIndex(String name) { 
        for (int i = 0; i < _orderings.size(); i++) { 
            Ordering o = _orderings.get(i); 
            if (null != o) { 
                if(o.getColumn().equals(name)) { 
                    return i; 
                } 
            } 
        } 
  
        return -1; 
    } 

    public void setOrderings(List<Ordering> orderings) {
        _orderings = orderings;
    }

    public List<Ordering> getOrderings() {

        // Filter out any orderings that don't have a column.  This can happen
        // if someone calls setOrderAscending() with having first called
        // setOrderBy().
        List<Ordering> orderings = new ArrayList<Ordering>();
        for (Ordering ordering : _orderings) {
            if (null != ordering.getColumn()) {
                orderings.add(ordering);
            }
        }
        
        return orderings;
    }

    public List<String> getGroupBys() {
        return _groupBys;
    }

    public void setGroupBys(List<String> groupBys) {
        _groupBys = groupBys;
    }
    
    public void addGroupBy(String property) {
        _groupBys.add(property);
    }
    
    public QueryOptions add(Filter... res)
    {
        // I don't this check does anything, null args
        // still come in as an array with a null element
        if (res != null) {
            for (int i = 0; i < res.length; i++)
            {
                // null args still come in as array elements
                Filter f = res[i];
                if (f != null)
                    _filters.add(f);
            }
        }
        return this;
    }

    /**
     * @ignore
     * Beanshell can't deal with varargs, so we provide this.
     */
    public QueryOptions addFilter(Filter f) {
        return add(f);
    }


    public List<Filter> getFilters()
    {
        return _filters;
    }

    public void setFilters(List<Filter> filters)
    {
        _filters = filters;
    }

    /**
     * Old name for setFilters, kept for backward compatibility.
     */
    public void setRestrictions(List<Filter> filters)
    {
        setFilters(filters);
    }

    public List<Filter> getRestrictions()
    {
        return getFilters();
    }

    /**
     * Convenience method to determine if this represents
     * a query for a specific object using the "id" or attribute.  
     * Used by some special persistence managers to convert a filter 
     * search into a getObject(id) call.
     */
    public String isIdentitySearch() {

        String id = null;
        if (_filters != null && _filters.size() == 1) {
            Filter f = _filters.get(0);
            if (f instanceof Filter.CompositeFilter) {
                Filter.CompositeFilter cf = (Filter.CompositeFilter)f;
                f = null;

                // in general this could be quite complicated, assume
                // if first term must be "id" equality
                List<Filter> children = cf.getChildren();
                if (children != null && children.size() > 0) {
                    f = children.get(0);
                }
            }

            if (f instanceof Filter.LeafFilter) {
                Filter.LeafFilter lf = (Filter.LeafFilter)f;
                if (lf.getOperation() == Filter.LogicalOperation.EQ &&
                    ("id".equals(lf.getProperty()) ||
                     "name".equals(lf.getProperty()))) {
                    Object v = lf.getValue();
                    if (v != null)
                        id = v.toString();
                }
            }
        }
        return id;
    }

    /**
     * @ignore
     * Useful only with project queries.  Too bad since we've moved
     * the projection out of the options now, but I don't want to   
     * add another argument to the projection search methods.
     */
    public void setDistinct(boolean b) {
        _distinct = b;
    }

    public boolean isDistinct() {
        return _distinct;
    }

    /**
     * Automatically makes all Filters case insensitive, where
     * it makes sense.  This is an alternative to using
     * Filter.ignoreCase wrapper on individual filter terms.
     *
     * Starting in 7.1 this is deprecated, you should be letting
     * the persistence layer automatically determine what is case
     * insensitive and what isn't.  Use of this option was always
     * debatable since there is no assurance that everything in the
     * filter had a case insensitive index.
     */
    @Deprecated
    public void setIgnoreCase(boolean b) {
        _ignoreCase = b;
    }

    public boolean isIgnoreCase() {
        return _ignoreCase;
    }

    /**
     * Return whether to scope results. If this returns null, this decision
     * is deferred to the SailPointContext.
     * 
     * @return Whether to scope results of the query or not. Can be null if
     *         the decision should be deferred to the SailPointContext.
     */
    public Boolean getScopeResults() {
        return _scopeResults;
    }
    
    /**
     * Set whether or not to scope the results of this query based on the
     * user's controlled scopes.
     */
    public void setScopeResults(Boolean scopeResults) {
        _scopeResults = scopeResults;
    }

    /**
     * Return whether unscoped objects should be returned or not for a scoped
     * query. If not set, the scoping mechanism should default to the system
     * configuration setting.
     */
    public Boolean getUnscopedGloballyAccessible() {
        return _unscopedGloballyAccessible;
    }

    /**
     * This can be set to override the system configuration setting for whether
     * unscoped objects should be returned or not for a scoped query. If not
     * set, this defaults to the system configuration setting.
     */
    public void setUnscopedGloballyAccessible(Boolean b) {
        _unscopedGloballyAccessible = b;
    }
    
    /**
     * Extend the result scope to include objects that match the given filter.
     * This allows for things like allowing an identity to view all other
     * identities that are in their scope as well as any identity for which
     * they are the manager. Programmatically, this would be accomplished
     * with:
     * <code>
     *   qo.setFilterResults(true);
     *   qo.extendScope(Filter.eq("manager", me));
     * </code>
     * 
     * @param  filters  The Filter or Filters that describe the objects which
     *                  should be returned in searches in addition to those
     *                  that are in the user's controlled scopes.
     */
    public void extendScope(Filter... filters) {
        if (null != filters) {
            for (Filter filter : filters) {
                _scopeExtensions.add(filter);
            }
        }
    }

    /**
     * Add a filter for default owner filtering. This filter
     * includes all direct ownership AND ownership held by 
     * any workgroups assigned to the Identity.
     * Programmatically, for a ui bean this would be accomplished
     * with:
     * <code>
     *   qo.setScopeResults(true); 
     *   qo.addOwnerScopeFilter(super.getLoggedInUser());
     * </code>
     */
    public void addOwnerScope(Identity user) {
        Filter ownerFilter = getOwnerScopeFilter(user, "owner");            
        if ( ownerFilter != null ) {
            _scopeExtensions.add(ownerFilter);
        }
    }
    
    /**
     * Return a filter for default owner filtering. This filter
     * includes all direct ownership AND ownership held by 
     * any workgroups assigned to the Identity.
     */
    public static Filter getOwnerScopeFilter(Identity user, String ownerColumn) {
        String ownerCol = (null != ownerColumn) ? ownerColumn : "owner";

        int maxSize = Configuration.getSystemConfig().getInt(Configuration.MAX_WORKGROUPS_FOR_OWNER_SCOPE_FILTER);
        if (maxSize == 0) {
            maxSize = MAX_WORKGROUPS_SIZE;
        }
        
        //IIQSAW-2119: optimization using single IN clause when workgroups size is small.
        //Negative value of MAX_WORKGROUPS_FOR_OWNER_SCOPE_FILTER means ignore optimization
        List<Identity> workgroups = user.getWorkgroups();
        if (maxSize > 0 && Util.size(workgroups) < (maxSize)) {
            List<Identity> owners = new ArrayList<Identity>();
            owners.add(user);
            if (Util.size(workgroups) > 0) {
                owners.addAll(workgroups);
            }
            return Filter.in(ownerCol, owners);
        } else {
            return getOwnerScopeFilterUsingSubqueries(user, ownerColumn);
        }
    }

    /**
     * Return a filter for default owner filtering. This filter includes all direct ownership AND ownership held by
     * any workgroups assigned to the Identity.
     * IIQMAG-2627: This is split from getOwnerScopeFilter so that we can get a query using the owner.id field which is needed
     * by the full textifier for proper query conversion.
     */
    public static Filter getOwnerScopeFilterUsingSubqueries(Identity user, String ownerColumn) {
        String ownerCol = (null != ownerColumn) ? ownerColumn : "owner";

        // We're using a subquery here, which works well for people who are in a lot of workgroups (rare).
        // However, going back to an IN query for people who are members of a small number of workgroups (< 10?, < 100?)
        // may make the query a better performant. http://dictionary.reference.com/browse/performant?db=dictionary

        // Oracle does full table scans when the 2nd filter has a simple eq, i.e.: Filter.eq(ownerCol, user)).
        // Using a subquery for the 2nd filter makes Oracle happy; SQL Server too (though not as much as Oracle).
        // DB2 has a slightly higher cost when using the 2nd filter owner subquery, but it doesn't look to have
        // a noticeable degradation. IIQETN-5744.
        return Filter.or(Filter.subquery(ownerCol + ".id", Identity.class, "workgroups.id", Filter.eq("id", user.getId())),
                Filter.subquery(ownerCol + ".id", Identity.class, "id", Filter.eq("id", user.getId())));
    }

    /**
     * Return the Filters that describe the objects to be included in the
     * query results in addition to those that are in the user's controlled
     * scopes.
     * 
     * @return The Filters that describe the objects to be included in the
     *         query results in addition to those that are in the user's
     *         controlled scopes, or null if there are no scope extensions.
     */
    public List<Filter> getScopeExtensions() {
        return _scopeExtensions;
    }
    
    /**
     * Obtain a transaction lock on objects returned by non-projection queries.
     * Note that searches that obtain persistent locks are not supported.
     * It does not seem very useful to bulk lock a bunch of 
     * objects.
     */
    public void setTransactionLock(boolean b) {
        _transactionLock = b;
    }

    public boolean isTransactionLock() {
        return _transactionLock;
    }

    /**
     * Used to define a search expressed as HQL or SQL.
     * The default is HQL, if you want to use SQL (which is discouraged)
     * prefix the query with "sql:".
     *
     * This field will have priority over _filters if both are set.
     */
    public void setQuery(String q) {
        _query = q;
    }

    public String getQuery() {
        return _query;
    }

    /**
     * When true, the consumer of these QueryOptions has the option to
     * cache the results upon execution. This can improve performance
     * in some cases, but can also add overhead with cache management.
     * 
     * @param  cache  Whether to cache the query results upon execution.
     */
    public void setCacheResults(boolean cache) {
        _cacheResults = cache;
    }

    public boolean isCacheResults() {
        return _cacheResults;
    }

    /**
     * When true, the consumer of these QueryOptions will flush any non-saved
     * data to the database before executing the query to ensure that an
     * accurate set of results is returned.
     * 
     * @param  flush  Whether to flush any non-saved data to the database before
     *                executing the query.
     * 
     * @ignore               
     * TODO: We might consider not using FlushMode.COMMIT as the default in
     * HibernatePersistenceManager and just use FlushMode.AUTO.  There were some
     * reasons for this before that may no longer be valid.  If we do this, then
     * this option can be removed.
     */
    public void setFlushBeforeQuery(boolean flush) {
        _flushBeforeQuery = flush;
    }

    public boolean isFlushBeforeQuery() {
        return _flushBeforeQuery;
    }


    public void setCloneResults(boolean b) {
        this._cloneResults = b;
    }

    public boolean isCloneResults() {
        return this._cloneResults;
    }

    ////////////////////////////////////////////////////////////////////////////
    //
    // Object overrides
    //
    ////////////////////////////////////////////////////////////////////////////

    @Override
    public boolean equals(Object o) {

        if (o == this)
            return true;

        if (!(o instanceof QueryOptions))
            return false;

        QueryOptions qo = (QueryOptions) o;

        return Util.nullSafeEq(_orderings, qo._orderings, true) &&
               Util.nullSafeEq(_groupBys, qo._groupBys, true) &&
               (_distinct == qo._distinct) &&
               (_firstRow == qo._firstRow) &&
               (_resultLimit == qo._resultLimit) &&
               Util.nullSafeEq(_filters, qo._filters, true) &&
               Util.nullSafeEq(_scopeResults, qo._scopeResults, true) &&
               Util.nullSafeEq(_unscopedGloballyAccessible, qo._unscopedGloballyAccessible, true) &&
               Util.nullSafeEq(_scopeExtensions, qo._scopeExtensions, true) &&
               Util.nullSafeEq(_query, qo._query, true) &&
               (_cacheResults == qo._cacheResults) &&
               (_flushBeforeQuery == qo._flushBeforeQuery);
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder()
            .append(_orderings)
            .append(_groupBys)
            .append(_distinct)
            .append(_firstRow)
            .append(_resultLimit)
            .append(_filters)
            .append(_scopeResults)
            .append(_unscopedGloballyAccessible)
            .append(_scopeExtensions)
            .append(_query)
            .append(_cacheResults)
            .append(_flushBeforeQuery)
            .toHashCode();
    }

    @Override
    public String toString() {
        StringBuffer sb = new StringBuffer();
        sb.append("Filters: ");
        sb.append(getFiltersString(_filters));

        sb.append(", Scope extensions: ");
        sb.append(getFiltersString(_scopeExtensions));

        sb.append(",scope results ["+ _scopeResults + "]");

        sb.append(",unscoped globally accessible ["+ _unscopedGloballyAccessible + "]");

        if ( _orderings != null ) {
            sb.append(",Ordering =="+ _orderings.toString() + "");
        }

        if ( _groupBys != null ) {
            sb.append(",Group by == " + _groupBys.toString());
        }
        
        sb.append(",Query ["+ _query+ "]");
        sb.append(",ResultLimit ["+ _resultLimit + "]");
        sb.append(",First Row ["+ _firstRow + "]");
        sb.append(",Distinct ["+ _distinct + "]");
        sb.append(",Cache results ["+ _cacheResults + "]");
        sb.append(",Flush before query ["+ _flushBeforeQuery + "]");
        return sb.toString();
    }

    private String getFiltersString(List<Filter> filters) {

        StringBuilder sb = new StringBuilder();

        if ( (filters != null ) && ( filters.size() > 0 ) ) {
            int i=1;
            for ( Filter filter : filters ) {
                sb.append("Filter [" + i++ + "] == " + filter.getExpression() + "\n");
            }
        } else {
            sb.append("none defined");
        }

        return sb.toString();
    }

    /*
     * @ignore
     */
    public boolean isDirtyRead() {
        return _dirtyRead;
    }

    /*
     * @ignore
     */
    public void setDirtyRead(boolean dirtyRead) {
        this._dirtyRead = dirtyRead;
    }
}
