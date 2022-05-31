/* (c) Copyright 2011 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.object;

import java.util.List;

/**
 * This is a light-weight representation of a query that is intended for consumption by suggest components
 * and/or LCM rules that communicate with suggest components. Unlike QueryOptions, this class carries data
 * regarding whether or not a query is supposed to return anything. This facilitates logic in suggest components
 * that determines whether a query should be executed or an empty Collection should be returned.
 * 
 * @author <a href="mailto:bernie.margolis@sailpoint.com">Bernie Margolis</a>
 */
public class QueryInfo {
    private Filter filter;
    private boolean returnAll;
    private boolean returnNone;
    
    /**
     * Construct a QueryInfo object from the specified QueryOptions
     * @param qo QueryOptions used to construct this QueryInfo. Note that if more than one Filter
     *           exists on the QueryOptions the list will be 'anded' into a single Filter
     */
    public QueryInfo(QueryOptions qo) {
        if (qo == null) {
            filter = null;
            returnAll = false;
            returnNone = true;
        } else {
            List<Filter> filters = qo.getFilters();
            if (filters == null || filters.isEmpty()) {
                filter = null;
            } else if (filters.size() == 1) {
                filter = filters.get(0);
            } else {
                filter = Filter.and(qo.getFilters());
            }
            returnNone = false;
            returnAll = (filters == null || filters.isEmpty());
        }
    }
    
    /**
     * @param filter Filter used in the query represented by this QueryInfo
     * @param isReturnNone true if this QueryInfo represents a query that should not return anything;
     *                     false otherwise. Note that this cannot be set to true unless the Filter
     *                     is null because it is intended to disambiguate that case. If a non-null 
     *                     Filter is passed in and this is set to true, an IllegalArgumentException 
     *                     will be thrown.
     * @throws IllegalArgumentException if isReturnNone is set to true but a non-null filter is passed in
     */
    public QueryInfo(Filter filter, boolean isReturnNone) throws IllegalArgumentException {
        this.filter = filter;
        this.returnNone = isReturnNone;
        if (isReturnNone) {
            if (filter != null) {
                throw new IllegalArgumentException("A filter was passed into the sailpoint.object.QueryInfo constructor and the isReturnNone parameter was set to true.  A query with a Filter applied to it should be capable of returning something.");
            }
            returnAll = false;
        } else {
            returnAll = (filter == null);
        }
    }
    
    /**
     * @return Filter used in the query that this object represents
     */
    public Filter getFilter() {
        return filter;
    }
    
    /**
     * @return true if the query represented by this object is returning all the objects of the type that is being queried;
     *         false otherwise
     */
    public boolean isReturnAll() {
        return returnAll;
    }
    
    /**
     * @return true if the query represented by this object should not return anything; false otherwise
     */
    public boolean isReturnNone() {
        return returnNone;
    }
}
