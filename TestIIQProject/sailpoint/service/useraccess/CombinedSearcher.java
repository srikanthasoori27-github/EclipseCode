package sailpoint.service.useraccess;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import sailpoint.api.SailPointContext;
import sailpoint.object.Bundle;
import sailpoint.object.Filter;
import sailpoint.object.ManagedAttribute;
import sailpoint.object.QueryInfo;
import sailpoint.object.QueryOptions;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

/**
 * Override of UserAccessSearcher to do a combined search of both Roles and Entitlements
 */
public class CombinedSearcher extends UserAccessSearcher {

    public CombinedSearcher(SailPointContext context, UserAccessSearchOptions searchOptions)
            throws GeneralException {
        super(context, searchOptions);
    }

    @Override
    protected UserAccessSearchOptions.ObjectTypes getObjectType() {
        //Don't need this since we are using other searcher's results
        return null;
    }

    @Override
    protected String getQuickLinkAction() {
        //Don't need this since we are using other searcher's searches
        return null;
    }
    
    @Override
    protected String getQuickLinkRequestControlOption() {
        //Don't need this since we are using other searcher's searches
        return null;
    }

    @Override
    protected QueryOptions addSearchTerms(QueryOptions ops, List<String> searchTerms) throws GeneralException {
        // Nothing to do here, other searchers will do it.  
        return ops;
    }

    @Override
    protected QueryInfo getKeywordQueryInfo() throws GeneralException {
        // Nothing to do here, other searchers will do it.
        return null;
    }

    @Override
    protected void validate() throws GeneralException {
        super.validate();

        if (getSearchOptions().getMaxResultCount() <= 0) {
            throw new GeneralException("Maximum result count must be set in the search options for combined search");
        }
    }

    @Override
    public UserAccessSearchResults search(List<String> searchTerms) throws GeneralException {
        //Check quicklinks ahead of time and fallback to individual search
        RoleSearcher roleSearcher = new RoleSearcher(getContext(), getSearchOptions());
        EntitlementSearcher entitlementSearcher = new EntitlementSearcher(getContext(), getSearchOptions());

        boolean rolesEnabled = roleSearcher.isAccessTypeEnabled();
        boolean entitlementsEnabled = entitlementSearcher.isAccessTypeEnabled();

        if (!rolesEnabled && !entitlementsEnabled) {
            return createEmptyResults();
        } else if (!rolesEnabled) {
            return entitlementSearcher.search(searchTerms);
        } else if (!entitlementsEnabled) {
            return roleSearcher.search(searchTerms);
        } else {
            return super.search(searchTerms);
        }
    }

    /**
     * Perform a keyword based search. This will search both Roles and Entitlements, and then merge, sort and
     * trim results to combine the object types
     * @param searchTerms List of search terms, or null
     * @return UserAccessSearchResults
     * @throws GeneralException
     */
    @Override
    protected UserAccessSearchResults searchKeyword(List<String> searchTerms) throws GeneralException {

        RoleSearcher roleSearcher = new RoleSearcher(getContext(), getSearchOptions());
        EntitlementSearcher entitlementSearcher = new EntitlementSearcher(getContext(), getSearchOptions());

        // 1. Get the QueryInfos from Roles and Entitlements and check full textability
        QueryInfo roleQueryInfo = roleSearcher.getKeywordQueryInfo();
        QueryInfo entitlementQueryInfo = entitlementSearcher.getKeywordQueryInfo();
        boolean searchRoles = !roleQueryInfo.isReturnNone();
        boolean searchEntitlements = !entitlementQueryInfo.isReturnNone();
        
        // If both are return none, nothing to do. 
        if (!searchRoles && !searchEntitlements) {
            return createEmptyResults();
        }

        List<Filter> comboFilters = new ArrayList<Filter>();
        if (searchRoles && roleQueryInfo.getFilter() != null ) {
            comboFilters.add(roleQueryInfo.getFilter());

        }
        if (searchEntitlements && entitlementQueryInfo.getFilter() != null) {
            comboFilters.add(entitlementQueryInfo.getFilter());
        }
        boolean fullText = canDoFulltext(comboFilters, searchTerms);

        // 2. Get permitted roles
        List<Map<String, Object>> permittedRoles = null;
        if (searchRoles) {
            permittedRoles = roleSearcher.getPermittedRoles(getSearchOptions().getTargetIdentity(), searchTerms, getSearchOptions().getRequester());
        }

        // 3. Get results
        UserAccessSearchResults searchResults;
        int oldStart = getSearchOptions().getStart();
        int oldLimit = getSearchOptions().getLimit();
        int maxCount = getSearchOptions().getMaxResultCount();

        if (fullText) {
            QueryOptions roleQueryOptions = (searchRoles) ? getClassQueryOptions(Bundle.class, roleQueryInfo, oldStart, oldLimit) : null;
            QueryOptions entitlementQueryOptions = (searchEntitlements) ? getClassQueryOptions(ManagedAttribute.class, entitlementQueryInfo, oldStart, oldLimit ) : null;
            Filter ftFilter = null;
            if (roleQueryOptions != null && entitlementQueryOptions != null) {
                ftFilter = Filter.or(Filter.and(roleQueryOptions.getFilters()), Filter.and(entitlementQueryOptions.getFilters()));
            } else if (roleQueryOptions != null) {
                ftFilter = Filter.and(roleQueryOptions.getFilters());
            } else if (entitlementQueryOptions != null) {
                ftFilter = Filter.and(entitlementQueryOptions.getFilters());
            }

            if (ftFilter == null) {
                searchResults = createEmptyResults();
            } else {
                QueryOptions ftOps = getQueryOptions(ftFilter, oldStart, oldLimit, null);
                roleSearcher.adjustForPermittedRoles(ftOps, Util.size(permittedRoles), oldStart, oldLimit);
                searchResults = 
                        roleSearcher.handlePermittedRoles(getFulltextResults(ftOps, searchTerms), permittedRoles, oldStart, oldLimit);
            }

        } else {
            /*
             * We need to get all the possible results for each page.  If we just get a page worth from the database
             * and the sorting is different than what combineAndSort does (for instance, if a name starts with '_', then
             * it's ordered differently by the db than what combineAndSort does.), then for each page load, we're sorting a different
             * result set - and we can make those '_' names not appear to the end user because it's always on another page, forever. 
             */
            UserAccessSearchResults roleResults = (searchRoles) ?
                    roleSearcher.getHibernateResults(Bundle.class,
                            getQueryOptions(roleQueryInfo.getFilter(), 0, maxCount, UserAccessSearchColumns.COLUMN_DISPLAYABLE_NAME),
                            searchTerms) : 
                    createEmptyResults();
            UserAccessSearchResults entitlementResults = (searchEntitlements) ?
                    entitlementSearcher.getHibernateResults(ManagedAttribute.class,
                            getQueryOptions(entitlementQueryInfo.getFilter(), 0, maxCount, UserAccessSearchColumns.COLUMN_DISPLAYABLE_NAME),
                            searchTerms) : 
                    createEmptyResults();

            List<Map<String, Object>> results = 
                    combineAndSort(roleResults.getResults(), entitlementResults.getResults(), getKeywordSearchComparator(), false);
            int totalCount = Math.min(roleResults.getTotalResultCount() + entitlementResults.getTotalResultCount(), maxCount);

            //Add the permitted roles to the front
            if (!Util.isEmpty(permittedRoles)) {
                results.addAll(0, permittedRoles);
                totalCount = Math.min(totalCount + permittedRoles.size(), maxCount);
            }

            //Trim to page
            results = trimResults(results, getSearchOptions().getStart(), getSearchOptions().getLimit());            
            searchResults = new UserAccessSearchResults(results, totalCount);
        }
        
        return searchResults;
        
    }

    /**
     * Get the QueryOptions for a given class's QueryInfo
     * @param clazz Class
     * @param queryInfo QueryInfo with filter and return none info
     * @param start Start value
     * @param limit Limit value
     * @return QueryOptions if any results, otherwise null
     */
    private QueryOptions getClassQueryOptions(Class clazz, QueryInfo queryInfo, int start, int limit) {
        QueryOptions ops = null;
        if (!queryInfo.isReturnNone()) {
            if (queryInfo.getFilter() != null) {
                ops = addFullTextObjectClass(getQueryOptions(queryInfo.getFilter(), start, limit, null), clazz);
            } else {
                ops = getQueryOptions(getObjectClassFilter(clazz), start, limit, null);
            }
        }
        return ops;
    }

    @Override
    protected UserAccessSearchResults searchIdentity() throws GeneralException {
        return searchCombinedSimple(getIdentitySearchComparator(), true, Collections.<String>emptyList());
    }

    @Override
    protected UserAccessSearchResults searchCurrentAccess(List<String> searchTerms) throws GeneralException {
        return searchCombinedSimple(getKeywordSearchComparator(), false, searchTerms);
    }

    /**
     * Perform a combined search by performing exact same search on both roles and entitlements,
     * then combining, with no special logic
     */
    private UserAccessSearchResults searchCombinedSimple(Comparator<Map<String, Object>> sorter, boolean reverseSort, List<String> searchTerms) throws GeneralException {
        //this way we wont waste time sorting and trimming results too early. also don't have to worry about paging 
        //or limits
        getSearchOptions().setAttribute(UserAccessSearchOptions.OPTION_NO_RESULT_CLEANUP, true);

        RoleSearcher roleSearcher = new RoleSearcher(getContext(), getSearchOptions());
        EntitlementSearcher entitlementSearcher = new EntitlementSearcher(getContext(), getSearchOptions());

        UserAccessSearchResults roleResults = roleSearcher.search(searchTerms);
        UserAccessSearchResults entitlementResults = entitlementSearcher.search(searchTerms);

        getSearchOptions().setAttribute(UserAccessSearchOptions.OPTION_NO_RESULT_CLEANUP, false);

        List<Map<String, Object>> results = trimResults(
                combineAndSort(roleResults.getResults(), entitlementResults.getResults(), sorter, reverseSort),
                getSearchOptions().getStart(), getSearchOptions().getLimit());
        int totalCount =
                Math.min(roleResults.getTotalResultCount() + entitlementResults.getTotalResultCount(), getSearchOptions().getMaxResultCount());

        return new UserAccessSearchResults(results, totalCount);
    }

    /**
     * Combine the lists of results and sort by the given columns
     * @param roles List of Maps representing the Roles
     * @param entitlements List of Maps representing the Entitlements
     * @param comparator Comparator to use for sorting.
     * @param reverse True if want to reverse sorting order (descending)
     * @return List of Maps combining and sorting both given lists
     */
    private List<Map<String,Object>> combineAndSort(List<Map<String,Object>> roles,
                                                    List<Map<String,Object>> entitlements,
                                                    Comparator<Map<String, Object>> comparator,
                                                    boolean reverse)
            throws GeneralException {
        List<Map<String,Object>> newResults = new ArrayList<Map<String,Object>>();
        newResults.addAll(roles);
        newResults.addAll(entitlements);
        Collections.sort(newResults, comparator);
        if (reverse) {
            Collections.reverse(newResults);
        }
        return newResults;
    }
}