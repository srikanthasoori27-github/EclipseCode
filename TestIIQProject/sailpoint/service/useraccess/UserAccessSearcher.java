package sailpoint.service.useraccess;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.FullTextifier;
import sailpoint.api.ObjectUtil;
import sailpoint.api.SailPointContext;
import sailpoint.object.Bundle;
import sailpoint.object.Configuration;
import sailpoint.object.Filter;
import sailpoint.object.FullTextIndex;
import sailpoint.object.Identity;
import sailpoint.object.ManagedAttribute;
import sailpoint.object.QueryInfo;
import sailpoint.object.QueryOptions;
import sailpoint.object.SailPointObject;
import sailpoint.object.ScoreBandConfig;
import sailpoint.object.ScoreConfig;
import sailpoint.service.CurrentAccessService;
import sailpoint.service.LCMConfigService;
import sailpoint.service.quicklink.QuickLinkOptionsConfigService;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.util.MapListComparator;

/**
 * Abstract class containing shared search logic for specific searchers. Should be overridden
 * with specific implementation details.
 */
public abstract class UserAccessSearcher {

    private static final Log log = LogFactory.getLog(UserAccessSearcher.class);
    
    /**
     * Interface used by addPopulationStats() that can count the number of
     * members in a population that have the object represented by a row.
     */
    public static interface PopulationCounter {
        /**
         * Return the number of users in the population that have the given row.
         */
        public int getCount(Map<String,Object> row) throws GeneralException;
    }
    
    /**
     * Class to hold information about population statistics for results
     */
    public static class PopulationStatistics implements Comparable<PopulationStatistics> {
        
        private int total;
        private int count;
        private boolean hasHighRisk;
        
        public PopulationStatistics(int total, int count, boolean hasHighRisk) {
            this.total = total;
            this.count = count;
            this.hasHighRisk = hasHighRisk;
        }

        public int getTotal() {
            return total;
        }

        public void setTotal(int total) {
            this.total = total;
        }

        public int getCount() {
            return count;
        }

        public void setCount(int count) {
            this.count = count;
        }

        public boolean isHasHighRisk() {
            return hasHighRisk;
        }

        public void setHasHighRisk(boolean hasHighRisk) {
            this.hasHighRisk = hasHighRisk;
        }

        @Override
        public int compareTo(PopulationStatistics o) {
            if (o == null) {
                return -1;
            }
            
            return Util.nullSafeCompareTo(this.getCount(), o.getCount());
        }
    }
    
    /**
     * List of columns we will always include in projection columns
     */
    public final List<String> STANDARD_COLUMNS =
            Arrays.asList(UserAccessSearchColumns.COLUMN_ID, UserAccessSearchColumns.COLUMN_DISPLAYABLE_NAME);

    private SailPointContext context;
    private UserAccessSearchOptions searchOptions;
    private LCMConfigService lcmConfigService;
    private QuickLinkOptionsConfigService qloService;
    private FullTextifier fullTextifier;

    /**
     * Constructor
     * @param context SailPointContext
     * @param searchOptions UserAccessSearchOptions
     * @throws sailpoint.tools.GeneralException
     */
    public UserAccessSearcher(SailPointContext context, UserAccessSearchOptions searchOptions)
            throws GeneralException {
        this.context = context;
        this.searchOptions = searchOptions;

        validate();
    }

    /**
     * Perform a keyword based search
     * @param searchTerms List of search terms, can be null
     * @return UserAccessResults with the results of keyword search
     * @throws GeneralException
     */
    protected abstract UserAccessSearchResults searchKeyword(List<String> searchTerms) throws GeneralException;

    /**
     * Perform an identity based search
     * @return UserAccessSearchResults with the results of identity based search
     * @throws GeneralException
     */
    protected abstract UserAccessSearchResults searchIdentity() throws GeneralException;

    /**
     * Perform a search on current access for a single identity
     * @return UserAccessSearchResults 
     * @throws GeneralException
     */
    protected abstract UserAccessSearchResults searchCurrentAccess(List<String> searchTerms) throws GeneralException;

    /**
     * Internal method to get the object type. Should be overridden.
     * @return Object type of the result, to add to the result map.
     */
    protected abstract UserAccessSearchOptions.ObjectTypes getObjectType();

    /**
     * Internal method to get the QuickLink action for authorization
     * @return QuickLink action property linked to the object type
     */
    protected abstract String getQuickLinkAction();
    
    /**
     * Internal method to get the QuickLink request control option to determine if the QuickLink feature is enabled
     * @return QuickLink request control option name linked to the object type
     */
    protected abstract String getQuickLinkRequestControlOption();

    /**
     * Internal method to add filters for search query term to existing query options
     * @param ops Existing QueryOptions
     * @param searchTerms List of search terms
     * @return new QueryOptions with extra filters added for search query
     */
    protected abstract QueryOptions addSearchTerms(QueryOptions ops, List<String> searchTerms) throws GeneralException;

    /**
     * Internal method to get full set of filters for a keyword search. Put in a QueryInfo 
     * object so can represent returning no results if necessary.
     * @return QueryInfo with filter representing full filter set for keyword search
     * @throws GeneralException
     */
    protected abstract QueryInfo getKeywordQueryInfo() throws GeneralException;

    /**
     * Perform a search of whatever type is specified in options with no terms.
     * If quicklink is not enabled for a type, an empty results will
     * be returned for that type.
     * @return UserAccessSearchResults
     * @throws GeneralException
     */
    public UserAccessSearchResults search() throws GeneralException {
        return search(null);
    }

    /**
     * Perform a search of whatever type is specified in options.
     * If quicklink is not enabled for a type, an empty results will
     * be returned for that type.
     * @param searchTerms List of search terms, can be null
     * @return UserAccessSearchResults
     * @throws GeneralException
     */
    public UserAccessSearchResults search(List<String> searchTerms) throws GeneralException {
        UserAccessSearchResults results;
        if (!isAccessTypeEnabled()) {
            results = createEmptyResults();
        } else {
            switch (getSearchOptions().getSearchType()) {
                case Identity:
                case Population:
                    results = searchIdentity();
                    break;
                case Keyword:
                    results = searchKeyword(searchTerms);
                    break;
                case CurrentAccess:
                    results = searchCurrentAccess(searchTerms);
                    break;
                default:
                    results = createEmptyResults();
                    log.warn("Invalid search type for user access search");
                    break;
            }
        }

        return results;
    }

    /**
     * Get the SailPointContext
     */
    protected SailPointContext getContext() {
        return this.context;
    }

    /**
     * Get the UserAccessSearchOptions
     */
    protected UserAccessSearchOptions getSearchOptions() {
        return this.searchOptions;
    }

    /**
     * Get the FullTextifier for Fulltext operations
     * @return FullTextifier initialized with correct index
     * @throws GeneralException
     */
    protected FullTextifier getFullTextifier() throws GeneralException {
        if (this.fullTextifier == null) {
            String fullTextIndexName = this.context.getConfiguration().getString(Configuration.LCM_USER_ACCESS_FULLTEXT_INDEX);
            FullTextIndex index = this.context.getObjectByName(FullTextIndex.class, fullTextIndexName);
            if (index == null) {
                throw new GeneralException("Missing FullTextIndex object for " + fullTextIndexName);
            }
            this.fullTextifier = new FullTextifier(this.context, index, getSearchOptions().getMaxResultCount());
        }
        return this.fullTextifier;
    }

    /**
     * Get the ObjectTypeOptions for the given type
     * @return ObjectTypeOptions
     */
    protected UserAccessSearchOptions.ObjectTypeOptions getObjectTypeOptions() {
        return getSearchOptions().getObjectTypeOptions(getObjectType());
    }

    /**
     * Validate the input data before performing search. Should be 
     * overridden for searcher-specific validation as needed.
     * @throws GeneralException
     */
    protected void validate() throws GeneralException {
        if (this.context == null) {
            throw new GeneralException("context must be defined");
        }
        if (searchOptions == null) {
            throw new GeneralException("searchOptions must be defined");
        }
        if (searchOptions.getMaxResultCount() < 0) {
            throw new GeneralException("maxResultCount must be set to a value greater than 0");
        }
        if (searchOptions.getRequester() == null) {
            throw new GeneralException("requester must be defined");
        }
        UserAccessSearchOptions.SearchTypes searchType = searchOptions.getSearchType();                
        if (searchType != null && searchType.isIdentitySearch() &&
                getObjectType() != null && Util.isEmpty(getObjectTypeOptions().getIdentityFilters())) {
            throw new GeneralException("identityFilters must be defined for Identity search type");
        }
        if (UserAccessSearchOptions.SearchTypes.CurrentAccess.equals(searchType)) {
            if (searchOptions.getTargetIdentity() == null) {
                throw new GeneralException("target identity must be defined for CurrentAccess search");
            }
        }

        //TODO: override this in other classes to cover more cases
    }

    /**
     * Check if access type is enabled, including quick links
     * @return True if enabled, otherwise false
     * @throws GeneralException
     */
    protected boolean isAccessTypeEnabled() throws GeneralException {
        // in the current access context roles and entitlements are always displayed
        if (UserAccessSearchOptions.SearchTypes.CurrentAccess.equals(getSearchOptions().getSearchType())) {
            return true;
        }

        boolean enabled = true;
        if (getQuickLinkAction() != null) {
            enabled = getQuickLinkOptionsConfigService().isRequestControlOptionEnabled(getSearchOptions().getRequester(),
                    getSearchOptions().getQuickLinkName(), getQuickLinkAction(), getQuickLinkRequestControlOption(), isSelfService());
        }
        return enabled;
    }

    /**
     * Get query options for a single filter with standard start/limit
     * @param filter Filter to use for query options.
     * @param sortColumn Column to sort on. Can be null.
     * @return QueryOptions
     */
    protected QueryOptions getQueryOptions(Filter filter, String sortColumn) {
        List<Filter> filters = new ArrayList<Filter>();
        if (filter != null) {
            filters.add(filter);
        }
        return getQueryOptions(filters, sortColumn);
    }

    /**
     * Get query options for multiple filters with standard start/limit
     * @param filters Filters to use for query options
     * @param sortColumn Column to sort on. Can be null.
     * @return QueryOptions
     */
    protected QueryOptions getQueryOptions(List<Filter> filters, String sortColumn) {
        return getQueryOptions(filters, getSearchOptions().getStart(), getSearchOptions().getLimit(), sortColumn);
    }

    /**
     * Get query options for single filter with specific start/limit 
     * @param filter Filter to use for query options
     * @param start Integer to start results
     * @param limit Limit of number of results 
     * @param sortColumn Column to sort on
     * @return QueryOptions
     */
    protected QueryOptions getQueryOptions(Filter filter, int start, int limit, String sortColumn) {
        List<Filter> filters = new ArrayList<Filter>();
        filters.add(filter);
        return getQueryOptions(filters, start, limit, sortColumn);
    }

    /**
     * Get query options for multiple filters with specific start/limit 
     * @param filters Filters to use for query options
     * @param start Integer to start results
     * @param limit Limit of number of results 
     * @param sortColumn Column to sort on
     * @return QueryOptions
     */
    protected QueryOptions getQueryOptions(List<Filter> filters, int start, int limit, String sortColumn) {
        QueryOptions ops = new QueryOptions();
        if (!Util.isEmpty(filters)) {
            for (Filter f: filters) {
                ops.add(f);
            }
        }

        if (start > 0) {
            ops.setFirstRow(start);
        }
        if (limit > 0) {
            ops.setResultLimit(limit);
        }

        if (sortColumn != null) {
            /* Make sure we ignore case here, since we will have to sort combined results in memory later,
             * and we need it to all be consistent. If we ever need to query sort on a non-string column,
             * we will have to revisit and make this configurable */
            ops.addOrdering(sortColumn, true, true);
        }
        ops.setScopeResults(false);

        return ops;
    }

    /**
     * Get the UserAccessSearch results for the given class and QueryOptions and terms. Will use full 
     * text search if allowed by options and filters.
     * @param clazz Class to search for
     * @param ops QueryOptions
     * @param searchTerms List of search terms           
     * @return UserAccessSearchResults
     * @throws GeneralException
     */
    protected UserAccessSearchResults getResults(Class<? extends SailPointObject> clazz, QueryOptions ops, List<String> searchTerms)
            throws GeneralException {
        if (canDoFulltext(ops, searchTerms)) {
            return getFulltextResults(addFullTextObjectClass(ops, clazz), searchTerms);
        } else {
            return getHibernateResults(clazz, ops, searchTerms);
        }
    }

    /**
     * Perform a full text search for given QueryOptions and search terms. This will not do any massaging or checking of 
     * QueryOptions, it is assumed caller knows it is ok to use full text search.
     * @param ops QueryOptions                                                        `
     * @param searchTerms List of search terms           
     * @return UserAccessSearchResults
     * @throws GeneralException
     */
    protected UserAccessSearchResults getFulltextResults(QueryOptions ops, List<String> searchTerms)
            throws GeneralException {
        FullTextifier.SearchResult ftResults = getFullTextifier().search(searchTerms, ops);
        if (!Util.isEmpty(ftResults.rows)) {
            enhanceRows(ftResults.rows);
        }
        return new UserAccessSearchResults(ftResults.rows, ftResults.totalRows);
    }

    /**
     * Perform a hibernate search for given class, QueryOptions and search terms. This will not check for full text search,
     * will do hibernate search in all cases
     * @param clazz SailPointObject class to search on
     * @param ops QueryOptions
     * @param searchTerms List of search terms
     * @return UserAccessSearchResults
     * @throws GeneralException
     */
    protected UserAccessSearchResults getHibernateResults(Class<? extends SailPointObject> clazz, QueryOptions ops, List<String> searchTerms)
            throws GeneralException {

        QueryOptions fullOps = addSearchTerms(ops, searchTerms);
        int totalCount = Math.min(getSearchOptions().getMaxResultCount(), getCount(clazz, fullOps));
        List<Map<String, Object>> rows = getRows(clazz, fullOps, getFullColumns());
        return new UserAccessSearchResults(rows, totalCount);
    }

    /**
     * Add a filter to limit full text search to a specific object class
     * @param ops QueryOptions 
     * @param clazz Class we are searching for. 
     * @return new QueryOptions limited by objectClass
     */
    protected QueryOptions addFullTextObjectClass(QueryOptions ops, Class clazz) {
        QueryOptions ftOps = new QueryOptions(ops);
        List<Filter> filters = ftOps.getFilters();
        Filter objectClassFilter = getObjectClassFilter(clazz);
        List<Filter> newFilters = new ArrayList<Filter>();
        newFilters.add(objectClassFilter);
        if (!Util.isEmpty(filters)) {
            newFilters.addAll(filters);
        }
        ftOps.setFilters(newFilters);
        return ftOps;
    }

    protected Filter getObjectClassFilter(Class clazz) {
        return Filter.eq(FullTextifier.FIELD_OBJECT_CLASS, clazz.getSimpleName());
    }

    /**
     * Check if Fulltext search can be used for the given QueryOptions and search terms
     */
    public boolean canDoFulltext(QueryOptions ops, List<String> searchTerms) throws GeneralException {
        return canDoFulltext(ops.getRestrictions(), searchTerms);
    }

    /**
     * Check if Fulltext search can be used for the given filters and search terms
     */
    public boolean canDoFulltext(List<Filter> filters, List<String> searchTerms) throws GeneralException {
            /* 1. Must be keyword search
             * 2. Query must be non-null
             * 3. No joins in QueryOptions 
             * 4. Fulltextifier must be enabled
             */
        return UserAccessSearchOptions.SearchTypes.Keyword.equals(getSearchOptions().getSearchType()) &&
                !Util.isEmpty(searchTerms) &&
                !hasJoin(filters) &&
                !hasRestrictedFilter(filters) &&
                getFullTextifier().isSearchEnabled();
    }

    /**
     * Walk over IIQ filters looking for joins.
     * TODO: A FilterUtil would be nice, if we're going to need this elsewhere
     */
    private boolean hasJoin(List<Filter> filters) {

        boolean join = false;
        if (filters != null) {
            for (Filter f : filters) {
                join = hasJoin(f);
                if (join)
                    break;
            }
        }
        return join;
    }

    private boolean hasJoin(Filter f) {
        boolean join = false;
        if (f instanceof Filter.LeafFilter) {
            Filter.LeafFilter lf = (Filter.LeafFilter)f;
            join = Filter.LogicalOperation.JOIN.equals(lf.getOperation());
        }
        else if (f instanceof Filter.CompositeFilter) {
            Filter.CompositeFilter cf = (Filter.CompositeFilter)f;
            List<Filter> children = cf.getChildren();
            if (children != null) {
                for (Filter child : children) {
                    join = hasJoin(child);
                    if (join)
                        break;
                }
            }
        }

        return join;
    }
    
/***
 * Since Lucene doesn't like to allow exact matches on indexed and searchable properties
 * we'll search through a user-configurable set of fields to only use db searches.
 * @param f
 * @return
 */
    private boolean hasRestrictedFilter(List<Filter> filters) {
        boolean foundName = false;
        
        for (Filter f : Util.iterate(filters)) {
            foundName = hasRestrictedFilter(f);
            if (foundName) {
                break;
            }
        }
        
        return foundName;
    }
    
    private boolean hasRestrictedFilter(Filter f) {
        boolean foundName = false;
        List<String> ignorableFields = null;
        
        Configuration systemConfig = Configuration.getSystemConfig();
        if (systemConfig != null) {
            String csvFields = (String)systemConfig.get(Configuration.LCM_SKIP_FULLTEXT_FIELDS);
            if (Util.isNotNullOrEmpty(csvFields)) {
                ignorableFields = Util.csvToList(csvFields);
            }
        }
        
        if (f instanceof Filter.LeafFilter) {
            Filter.LeafFilter lf = (Filter.LeafFilter)f;
            for (String field : Util.iterate(ignorableFields)) {
                if (Filter.LogicalOperation.EQ.equals(lf.getOperation()) && Util.nullSafeCaseInsensitiveEq(field, lf.getProperty())) {
                    foundName = true;
                }
            }
        } else if (f instanceof Filter.CompositeFilter) {
            Filter.CompositeFilter cf = (Filter.CompositeFilter)f;
            List<Filter> children = cf.getChildren();
            if (children != null) {
                for (Filter child : children) {
                    foundName = hasRestrictedFilter(child);
                    if (foundName) {
                        break;
                    }
                }
            }
        }
        
        return foundName;
        
    }

    protected Filter.MatchMode getMatchMode() throws GeneralException {
        return getLcmConfigService().getSearchMode();
    }

    /**
     * Get a count of objects
     * @param clazz SailPointObject class for query
     * @param ops QueryOptions
     * @return count
     * @throws GeneralException
     */
    protected int getCount(Class<? extends SailPointObject> clazz, QueryOptions ops) throws GeneralException {
        return this.context.countObjects(clazz, ops);
    }

    /**
     * Get the List of Maps representing rows in results of search
     * @param clazz SailPointObject class for query
     * @param ops QueryOptions
     * @param columns Projection columns
     * @return List of Maps keyed by column name
     * @throws GeneralException
     */
    protected List<Map<String, Object>> getRows(Class<? extends SailPointObject> clazz, QueryOptions ops, List<String> columns)
            throws GeneralException {
        Date pre = new Date();
        Iterator<Object[]> rows = this.context.search(clazz, ops, columns);
        Date post = new Date();
        log.debug("getRows took " + (post.getTime() - pre.getTime()) + " ms.");
        return convertRows(rows, columns);
    }

    /**
     * Convert the Object array from projection search to List of Maps keyed by column name
     * @param rows Iterator accessing the rows from the projection search
     * @param columns List of projection columns. Order should match that used for search. 
     * @return Converted rows in the form of a List of Maps
     */
    protected List<Map<String, Object>> convertRows(Iterator<Object[]> rows, List<String> columns)
            throws GeneralException {
        List<Map<String, Object>> convertedRows = new ArrayList<Map<String, Object>>();
        while (rows != null && rows.hasNext()) {
            Map<String, Object> convertedRow = new HashMap<String, Object>();
            Object[] row = rows.next();
            for (int i = 0; i < columns.size(); i++) {
                convertedRow.put(columns.get(i), row[i]);
            }
            enhanceRow(convertedRow);
            convertedRows.add(convertedRow);
        }

        return convertedRows;
    }

    /**
     * Enhance the rows with additional fields 
     * @param rows Rows to enhance
     */
    protected void enhanceRows(List<Map<String, Object>> rows) throws GeneralException {
        for (Map<String, Object> row : rows) {
            enhanceRow(row);
        }
    }

    /**
     * Enhance the single row with additional fields
     * @param row Row to enhance
     */
    protected void enhanceRow(Map<String, Object> row) throws GeneralException {
        if (row.containsKey(FullTextifier.FIELD_OBJECT_CLASS)) {
            String objectClass = (String)row.get(FullTextifier.FIELD_OBJECT_CLASS);
            if (Bundle.class.getSimpleName().equals(objectClass)) {
                row.put(UserAccessSearchColumns.COLUMN_OBJECT_TYPE, UserAccessSearchOptions.ObjectTypes.Role);
            } else if (ManagedAttribute.class.getSimpleName().equals(objectClass)) {
                row.put(UserAccessSearchColumns.COLUMN_OBJECT_TYPE, UserAccessSearchOptions.ObjectTypes.Entitlement);
            }
        } else if (getObjectType() != null) {
            row.put(UserAccessSearchColumns.COLUMN_OBJECT_TYPE, getObjectType());
        } else {
            throw new GeneralException("Unabled to determine objectType of row");
        }
        
        // We expect risk score to be a number, but full text makes it a string. Convert back to int.
        if (row.containsKey(UserAccessSearchColumns.COLUMN_RISK_SCORE)) {
            row.put(UserAccessSearchColumns.COLUMN_RISK_SCORE, Util.otoi(row.get(UserAccessSearchColumns.COLUMN_RISK_SCORE)));
        }
    }

    /**
     * Combine search options columns with standard columns to get full set
     * @return List of columns with standard columns included
     */
    protected List<String> getFullColumns() {
        List<String> fullColumns = new ArrayList<String>(getObjectTypeOptions().getColumns());

        for (String standardColumn: STANDARD_COLUMNS) {
            if (!fullColumns.contains(standardColumn)) {
                fullColumns.add(standardColumn);
            }
        }

        return fullColumns;
    }

    /**
     * Get a LCM Config Service
     * @return LCMConfigService
     */
    protected LCMConfigService getLcmConfigService() {
        if (this.lcmConfigService == null) {
            this.lcmConfigService = new LCMConfigService(getContext(), getSearchOptions().getLocale(),
                    getSearchOptions().getTimeZone(), getSearchOptions().getQuickLinkName());
        }
        return this.lcmConfigService;
    }
    
    /**
     * Gets a cached copy of the quick link options config service
     * @return QuickLinkOptionsConfigService object
     */
    protected QuickLinkOptionsConfigService getQuickLinkOptionsConfigService() {
        if (this.qloService == null) {
            this.qloService = new QuickLinkOptionsConfigService(getContext(), getSearchOptions().getLocale(),
                    getSearchOptions().getTimeZone());
        }
        return this.qloService;
    }

    /**
     * Return true if this is a self-service request
     * @return True if requester matches target identity
     */
    protected boolean isSelfService() {
        Identity requester = getSearchOptions().getRequester();
        Identity targetIdentity = getSearchOptions().getTargetIdentity();
        return (requester != null && targetIdentity != null && Util.nullSafeEq(requester.getId(), targetIdentity.getId()));
    }

    /**
     * Trim the result list to the expected start and size
     * @param results Original result list
     * @param start Index to start
     * @param limit Limit of results
     * @return New trimmed list with only results we expect
     */
    protected List<Map<String, Object>> trimResults(List<Map<String, Object>> results, int start, int limit) {
        List<Map<String, Object>> newResults = new ArrayList<Map<String, Object>>();
        int maxResultCount = getSearchOptions().getMaxResultCount();
        if (start < results.size()) {
            // If a limit wasn't specified, use the size of the list.
            limit = (limit > 0) ? limit : results.size();

            if ((limit + start) >= results.size()) {
                limit = results.size();
            } else {
                limit = start + limit;
            }
            
            //The code that calls this will always be pulling max results,
            //so we need to prevent a 'magical' last page with more than max
            //results
            if (limit > maxResultCount) {
                limit = maxResultCount;
            }

            for (int i = start; i < limit; i++) {
                newResults.add(results.get(i));
            }
        }

        return newResults;
    }

    /**
     * Add population statistics to the given results - the total number in the
     * population, the number in the population that has each object, and 
     * whether there are any high risk users.
     *
     * @param  results          The results to modify.
     * @param  identityFilters  The filters for the population.
     * @param  total            The total number of users in the population.
     * @param  counter          The PopulationCounter to use to calculate the
     *                          count for each row.
     */
    public void addPopulationStats(List<Map<String,Object>> results,
                                   List<Filter> identityFilters,
                                   int total,
                                   PopulationCounter counter)
            throws GeneralException {

        boolean hasHighRisk = false;

        /** Determine if we have a high risk identity in the population **/
        ScoreConfig scoreConfig = context.getObjectByName(ScoreConfig.class, ScoreConfig.OBJ_NAME);
        if(scoreConfig!=null && scoreConfig.getBands()!=null) {
            ScoreBandConfig highRisk = null;
            for(ScoreBandConfig band : scoreConfig.getBands()) {
                if(highRisk==null || band.getLowerBound()>highRisk.getLowerBound()) {
                    highRisk = band;
                }
            }

            if (highRisk != null) {
                QueryOptions qo = new QueryOptions();
                qo.setRestrictions(new ArrayList<Filter>(identityFilters));
                qo.add(Filter.ge("scorecard.compositeScore", highRisk.getLowerBound()));
                int highRiskCount = context.countObjects(Identity.class, qo);
                hasHighRisk = highRiskCount>0;
            }
        }

        for(Map<String,Object> row : results) {
            row.put(UserAccessSearchColumns.COLUMN_POP_STATS,
                    new PopulationStatistics(total, counter.getCount(row), hasHighRisk));
        }
    }

    /**
     * Given a list of rows, filter ones that are not above the population minimum, if set
     * @param rows Rows to filter
     */
    protected List<Map<String, Object>> filterByPercent(List<Map<String, Object>> rows) {
        List<Map<String, Object>> newRows = new ArrayList<Map<String, Object>>(rows);
        int populationMin = getObjectTypeOptions().getAttributes().getInt(UserAccessSearchOptions.ObjectTypeOptions.OPTION_POPULATION_MINIMUM);
        if (populationMin > 0) {
            Iterator<Map<String, Object>> rowIterator = newRows.iterator();
            while(rowIterator.hasNext()) {
                Map<String,Object> result = rowIterator.next();
                PopulationStatistics statistics = (PopulationStatistics)result.get(UserAccessSearchColumns.COLUMN_POP_STATS);
                if (statistics != null) {
                    int percent = Util.getPercentage(statistics.getCount(), statistics.getTotal());
                    if (percent < populationMin) {
                        rowIterator.remove();
                    }
                }
            }
        }

        return newRows;
    }

    /**
     * Sort the results given the comparator. Trim to the right size and remove any unwanted values from the maps.
     * @param rows Original results list
     * @param comparator Comparator to use for sorting the maps
     * @param reverse If true, reverse list after sorting
     * @return Sorted, trimmed and cleanup up list of results.
     */
    protected List<Map<String, Object>> sortAndCleanupResults(List<Map<String, Object>> rows,
                                                              Comparator<Map<String, Object>> comparator,
                                                              boolean reverse)
            throws GeneralException {
        if (getSearchOptions().getAttributes().getBoolean(UserAccessSearchOptions.OPTION_NO_RESULT_CLEANUP)) {
            //Skip cleanup if specified
            return rows;
        }

        List<Map<String, Object>> newRows = new ArrayList<Map<String, Object>>(rows);

        Collections.sort(newRows, comparator);
        if (reverse) {
            Collections.reverse(newRows);
        }
        if (getSearchOptions().getAttributes().getBoolean(UserAccessSearchOptions.OPTION_MAXRESULT_CLEANUP_ONLY)) {
            // IIQETN-4215 - This list will be combined with another list of items so dont trim to the limit value.
            // However, since we only return at most maxResultCount, lets trim this list to start at the first record
            // and be at most maxResultsCount in size. This will help to keep unnecessary processing of extremely
            // large lists to a minimum.
            return trimResults(newRows, 0, getSearchOptions().getMaxResultCount());
        } else {
            return trimResults(newRows, getSearchOptions().getStart(), getSearchOptions().getLimit());
        }
    }

    /**
     * Get Comparator used for Identity searches
     */
    protected Comparator<Map<String, Object>> getIdentitySearchComparator() throws GeneralException {
        //TODO: we need to use different comparator to have secondary sort on displayable name if population counts are the same
        return new MapListComparator(UserAccessSearchColumns.COLUMN_POP_STATS);
    }

    /**
     * Get Comparator used for Keyword searches
     */
    protected Comparator<Map<String, Object>> getKeywordSearchComparator() throws GeneralException{
        return new MapListComparator(UserAccessSearchColumns.COLUMN_DISPLAYABLE_NAME, true);
    }

    /**
     * Create UserAccessSearchResults with no results
     */
    protected UserAccessSearchResults createEmptyResults() {
        return new UserAccessSearchResults(new ArrayList<Map<String, Object>>(), 0);
    }

    /**
     * Return the LCM "object authority" QueryInfo for the given selector object, if any are defined.
     */
    protected QueryInfo getLCMAuthorityFilter(LCMConfigService.SelectorObject selectorObject) throws GeneralException {
        LCMConfigService lcmConfig = new LCMConfigService(getContext(), null, null, getSearchOptions().getQuickLinkName());
        QueryInfo selectorQueryInfo = lcmConfig.getSelectorQueryInfo(getSearchOptions().getRequester(),
                getSearchOptions().getTargetIdentity(), selectorObject, isSelfService());


        // Before changing the return type consider that a lone Filter is not enough to go on because 
        // the meaning of a null Filter is ambiguous.  Does it mean that no restrictions were placed
        // on the query or does it mean that nothing was supposed to be returned?  Just because a Filter
        // is simpler doesn't mean it's better.
        return selectorQueryInfo;
    }
    
    protected UserAccessSearchResults convertToResults(List<? extends CurrentAccessService.CurrentAccess> currentAccessList) 
    throws GeneralException {
        if (Util.isEmpty(currentAccessList)) {
            return createEmptyResults(); 
        }
        
        List<Map<String, Object>> results = new ArrayList<Map<String, Object>>();
        for (CurrentAccessService.CurrentAccess currentAccess : currentAccessList) {
             results.add(convertToResult(currentAccess));
        }
        return new UserAccessSearchResults(results, results.size());
    }

    protected Map<String, Object> convertToResult(CurrentAccessService.CurrentAccess currentAccess) throws GeneralException {
        SailPointObject spObject = currentAccess.getObject(getContext());
        Map<String, Object> result;
        if (spObject == null) {
            result = new HashMap<String, Object>();
        } else {
            result = ObjectUtil.objectToMap(spObject, getFullColumns()); 
        }

        result.putAll(getCurrentAccessAdditionalResults(currentAccess));

        enhanceRow(result);
        return result;
    }

    protected Map<String, Object> getCurrentAccessAdditionalResults (CurrentAccessService.CurrentAccess currentAccess)
            throws GeneralException {
        Map<String, Object> result = new HashMap<String, Object>();

        result.put(UserAccessSearchColumns.COLUMN_STATUS, currentAccess.getStatus().getStringValue());
        result.put(UserAccessSearchColumns.COLUMN_DISPLAYABLE_STATUS, localizeMessage(currentAccess.getStatus().getMessageKey()));
        if (currentAccess.getSunrise() != null) {
            result.put(UserAccessSearchColumns.COLUMN_SUNRISE, currentAccess.getSunrise().getTime());
        }
        if (currentAccess.getSunset() != null) {
            result.put(UserAccessSearchColumns.COLUMN_SUNSET, currentAccess.getSunset().getTime());
        }

        result.put(UserAccessSearchColumns.COLUMN_REMOVE_PENDING, currentAccess.isRemoveRequestPending());

        result.put(UserAccessSearchColumns.COLUMN_REMOVABLE, currentAccess.isRemovable(
            getQuickLinkOptionsConfigService(),
            getSearchOptions().getRequester(),
            isSelfService(),
            getSearchOptions().getQuickLinkName()
        ));

        return result;
    }
    
    protected String localizeMessage(String messageKey, Object... args) {
        return new Message(messageKey, args).getLocalizedMessage(getSearchOptions().getLocale(), getSearchOptions().getTimeZone());
    }

}
