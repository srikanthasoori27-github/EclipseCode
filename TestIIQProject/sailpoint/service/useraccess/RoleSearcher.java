package sailpoint.service.useraccess;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.collections4.multimap.HashSetValuedHashMap;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.ObjectUtil;
import sailpoint.api.SailPointContext;
import sailpoint.api.SearchResultsIterator;
import sailpoint.object.Bundle;
import sailpoint.object.Configuration;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.QueryInfo;
import sailpoint.object.QueryOptions;
import sailpoint.object.QuickLink;
import sailpoint.service.CurrentAccessService;
import sailpoint.service.CurrentAccessService.CurrentAccessStatus;
import sailpoint.service.LCMConfigService;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.web.modeler.RoleConfig;
import sailpoint.web.util.FilterConverter;
import sailpoint.web.util.WebUtil;

/**
 * Override of UserAccessSearcher to perform a search for roles
 */
public class RoleSearcher extends UserAccessSearcher {

    public static final String COLUMN_ASSIGNED_ROLES = "assignedRoles";
    public static final String TOO_MANY_ROLES = "Too many roles returned for the selected user(s) during access request search, limiting to amount specified in sys config";
    private Log _log = LogFactory.getLog(RoleSearcher.class);
    /**
     * Constructor
     * @param context SailPointContext
     * @param searchOptions UserAccessSearchOptions
     * @throws sailpoint.tools.GeneralException
     */
    public RoleSearcher(SailPointContext context, UserAccessSearchOptions searchOptions)
            throws GeneralException {
        super(context, searchOptions);
    }

    @Override
    protected UserAccessSearchOptions.ObjectTypes getObjectType() {
        return UserAccessSearchOptions.ObjectTypes.Role;
    }

    @Override
    protected void validate() throws GeneralException {
        super.validate();

        if (getSearchOptions().getRequester() == null) {
            throw new GeneralException("Requester must be set in search options for role search");
        }

    }

    @Override
    protected String getQuickLinkAction() {
        return QuickLink.LCM_ACTION_REQUEST_ACCESS;
    }

    @Override
    protected String getQuickLinkRequestControlOption() {
        return Configuration.LCM_ALLOW_REQUEST_ROLES;
    }
    
    @Override
    protected QueryOptions addSearchTerms(QueryOptions ops, List<String> searchTerms) throws GeneralException {
        QueryOptions newOps = new QueryOptions(ops);
        List<Filter> searchTermFilters = buildSearchTermFilters(searchTerms);
        newOps.add(searchTermFilters.toArray(new Filter[searchTermFilters.size()]));

        return newOps;
    }

    private List<Filter> buildSearchTermFilters(List<String> searchTerms) throws GeneralException {
        List<Filter> filters = new ArrayList<Filter>();
        Filter.MatchMode matchMode = getMatchMode();
        if (!Util.isEmpty(searchTerms)) {
            for (String searchTerm : searchTerms) {
                filters.add(Filter.ignoreCase(Filter.like(UserAccessSearchColumns.COLUMN_DISPLAYABLE_NAME, searchTerm, matchMode)));
            }
        }
        return filters;
    }

    @Override
    public QueryInfo getKeywordQueryInfo() throws GeneralException {
        return getKeywordQueryInfo(false);
    }
    
    private QueryInfo getKeywordQueryInfo(boolean permitted, Bundle role) throws GeneralException {
        List<Filter> filters = Util.isEmpty(getObjectTypeOptions().getFilters()) ? new ArrayList<Filter>() :
                new ArrayList<Filter>(getObjectTypeOptions().getFilters());

        QueryInfo selectorQueryInfo;
        LCMConfigService lcmConfigSvc = getLcmConfigService();
        Identity requestor = getSearchOptions().getRequester();
        Identity target = getSearchOptions().getTargetIdentity();

        if (!isEnabled(permitted)) {
            // If not enabled, use query info that returns no results
            selectorQueryInfo = new QueryInfo(null, true);
        } else {
            if (permitted) {
                selectorQueryInfo = lcmConfigSvc.getRoleSelectorQueryInfo(requestor, target, false, true, false, role);
            } else {
                selectorQueryInfo = lcmConfigSvc.getRoleSelectorQueryInfo(requestor, target, true, false, false);
            }
        }

        if (selectorQueryInfo.getFilter() != null) {
            filters.add(selectorQueryInfo.getFilter());
        }

        if (selectorQueryInfo.isReturnNone()) {
            return selectorQueryInfo;
        } else {
            return new QueryInfo(Filter.and(filters), false);
        }
    }
    
    private QueryInfo getKeywordQueryInfo(boolean permitted) throws GeneralException {
        return getKeywordQueryInfo(permitted, null);
    }

    /**
     * Check if enabled, based on role type (permitted or not)
     * @param permitted True for permitted roles, false for assignable
     * @return True if allowed, otherwise false
     */
    private boolean isEnabled(boolean permitted) {
        if (permitted) {
            return getLcmConfigService().isRequestPermittedRolesAllowed(isSelfService());
        } else {
            return getLcmConfigService().isRequestAssignableRolesAllowed(isSelfService());
        }
    }
    
    @Override
    protected boolean isAccessTypeEnabled() throws GeneralException {
        boolean enabled = super.isAccessTypeEnabled();
        if (enabled) {
            // If neither permitted nor assigned roles are enabled, nothing to do.
            enabled = isEnabled(false) || isEnabled(true);
        }
        return enabled;
    }

    /**
     * Perform a keyword based search. If permitted roles are allowed to be requested, 
     * any permitted roles for the target identity will be fetched and placed at the front of the 
     * full result set.
     * @param searchTerms List of search terms, can be null
     * @return UserAccessResults with the results of keyword search
     * @throws GeneralException
     */
    @Override
    public UserAccessSearchResults searchKeyword(List<String> searchTerms) throws GeneralException {
        int start = getSearchOptions().getStart();
        int limit = getSearchOptions().getLimit();

        QueryInfo queryInfo = getKeywordQueryInfo();
        if (queryInfo.isReturnNone()) {
            return createEmptyResults();
        }

        QueryOptions ops = getQueryOptions(queryInfo.getFilter(), UserAccessSearchColumns.COLUMN_DISPLAYABLE_NAME);

        Identity identity = getSearchOptions().getTargetIdentity();
        Identity requester = getSearchOptions().getRequester();

        // Fetch permitted roles. Method will check if they should be included or not.
        List<Map<String, Object>> permittedRoles = getPermittedRoles(identity, searchTerms, requester);
        int permittedRolesSize = Util.size(permittedRoles);

        UserAccessSearchResults initialResults =
                getResults(Bundle.class, adjustForPermittedRoles(ops, permittedRolesSize, start, limit), searchTerms);
        return handlePermittedRoles(initialResults, permittedRoles, start, limit);
    }

    /**
     * Adjust the start and limit of QueryOptions based on the number of permitted roles discovered
     * @param ops QueryOptions to modify
     * @param permittedRolesSize Count of permitted roles discovered
     * @param start Original start
     * @param limit Original limit
     * @return Updated QueryOptions
     */
    public QueryOptions adjustForPermittedRoles(QueryOptions ops, int permittedRolesSize, int start, int limit) {
        if (permittedRolesSize > 0) {
            // endIndex is the index of the last result from the query that we need to fill
            int endIndex = (start + limit) - permittedRolesSize;
            int firstRow = 0;
            int resultLimit = 0;
            
            // If endIndex is 0 or less, the full result set can be filled with permitted roles. So nothing to do.
            if (endIndex > 0) {
                firstRow = Math.max(0, endIndex - limit);
                // If first row will be after all the permitted roles (no permitted roles on this page), then
                // get the full set asked for. Otherwise only get as many as we need.
                resultLimit = Math.max(0, (firstRow > permittedRolesSize) ? limit : (limit - permittedRolesSize));
            }
            
            ops.setFirstRow(firstRow);
            ops.setResultLimit(resultLimit);
        }
        return ops;
    }

    /**
     * Given a result set, insert the permitted roles in front and adjust the paging
     * @param originalResults Original results without permitted roles
     * @param permittedRoles List of discovered permitted roles
     * @param start Original start
     * @param limit Original limit
     * @return new UserAccessSearchResults containing only desired results with permitted roles inserted
     */
    public UserAccessSearchResults handlePermittedRoles(UserAccessSearchResults originalResults, List<Map<String, Object>> permittedRoles, int start, int limit) {
        UserAccessSearchResults newResults = originalResults;

        int permittedRolesSize = Util.size(permittedRoles);

        // if we have permitted roles and they are going to be part of the results 
        // for the requested page then make sure that we have a full page before 
        // returning results by combining the permitted roles with assignable results 
        // and then slicing off the page
        if (permittedRolesSize > 0) {
            // First add permitted roles to total count regardless
            int totalCount = originalResults.getTotalResultCount() + permittedRolesSize;
            List<Map<String, Object>> rows = originalResults.getResults();
            // If start is less than the permitted role count, we will have some in this result
            // set. So add in as many as needed. 
            if (start < permittedRolesSize) {
                int numAssignableResultsNeeded = limit - (permittedRolesSize % limit);
                int assignableLastIndex = Math.min(numAssignableResultsNeeded, rows.size());
                rows = rows.subList(0, assignableLastIndex);
                rows.addAll(0, permittedRoles);

                int lastIndex = Math.min(start + limit, rows.size());
                rows = rows.subList(start, lastIndex);
            }
            newResults = new UserAccessSearchResults(rows, Math.min(totalCount, getSearchOptions().getMaxResultCount()));
        }

        return newResults;
    }

    @Override
    public UserAccessSearchResults searchIdentity() throws GeneralException {
        
        if (!isEnabled(false)) {
            return createEmptyResults();
        }
        
        List<Filter> filters = getObjectTypeOptions().getFilters();
        List<String> columns = getFullColumns();

        QueryOptions ops = addIdentitySearchFilters(getQueryOptions(filters, 0, 0, null));
        // If this is null, user has no access to any other identities, so nothing to do. 
        if (ops == null) {
            return createEmptyResults();
        }
        // This also handles getting the LCM authority filters
        QueryInfo assignablesQueryInfo = getLcmConfigService().getRoleSelectorQueryInfo(getSearchOptions().getRequester(),
                getSearchOptions().getTargetIdentity(), true, false, false);
        if (assignablesQueryInfo.isReturnNone()) {
            return createEmptyResults();
        }
        if (assignablesQueryInfo.getFilter() != null) {
            ops.add(assignablesQueryInfo.getFilter());
        }
        List<Map<String, Object>> rows = getRows(Bundle.class, ops, columns);
        Configuration sysConfig = Configuration.getSystemConfig();
        if (!sysConfig.getBoolean(Configuration.LCM_DISABLE_ROLE_POPULATION_STATS)) {
            getPopulationStats(rows);
            rows = filterByPercent(rows);
        }

        int total = rows.size();
        rows = sortAndCleanupResults(rows, getIdentitySearchComparator(), true);

        return new UserAccessSearchResults(rows, total);
    }

    /**
     * When a user searches for roles based on some population attributes on the identity, we can
     * build statistics on how many people in the population have that role.
     * @param results The result list.
     */
    private void getPopulationStats(List<Map<String,Object>> results) throws GeneralException{
        List<Filter> identityFilters = getObjectTypeOptions().getIdentityFilters();
        final QueryOptions qo = new QueryOptions();

        QueryOptions identityQueryOptions =
                getLcmConfigService().getRequestableIdentityOptions(
                        getSearchOptions().getRequester(),
                        null,
                        getSearchOptions().getQuickLinkName(),
                        getQuickLinkAction());
        int totalPopulation;
        PopulationCounter counter;
        if (null == identityQueryOptions) {
            // When this is the case no one is in scope so respond accordingly
            totalPopulation = 0;
            counter = new PopulationCounter() {
                public int getCount(Map<String, Object> row) throws GeneralException {
                    return 0;
                }
            };
        } else {
            qo.setRestrictions(identityQueryOptions.getRestrictions());
            for(Filter filter : identityFilters) {
                qo.add(filter);
            }

            /** First get the total population stats **/
            totalPopulation = getContext().countObjects(Identity.class, qo);
            counter = new PopulationCounter() {
                public int getCount(Map<String, Object> row) throws GeneralException {
                    // Make a copy of the identity query options and look for only
                    // those that have this role.
                    QueryOptions ops = new QueryOptions(qo);
                    ops.add(Filter.or(Filter.eq("assignedRoles.id", row.get("id")), Filter.eq("bundles.id", row.get("id"))));
                    return getContext().countObjects(Identity.class, ops);
                }
            };
        }

        addPopulationStats(results, identityFilters, totalPopulation, counter);
    }

    private QueryOptions addIdentitySearchFilters(QueryOptions ops) throws GeneralException {
        QueryOptions newOps = new QueryOptions();
        
        if (getObjectTypeOptions().getIdentityFilters() != null) {
            QueryOptions optimizedAssignedOptions = new QueryOptions();
            QueryOptions optimizedDetectedOptions = new QueryOptions();
            List<Filter> configuredLCMIdentityFilters = null;
            
            //Since we're using more than one query to prevent outer joins,
            //make sure the identity filters go into the Identity.class queries
            for (Filter inFilter : Util.iterate(ops.getFilters())) {
                if (inFilter.toString().contains("Identity.")) {
                    optimizedAssignedOptions.addFilter(inFilter);
                    optimizedDetectedOptions.addFilter(inFilter);
                } else { //for the Bundles
                    newOps.addFilter(inFilter);
                }
            }

            //We can drop the distinct and use a Set.
            Set<String> bundleIds = new HashSet<String>();;
       
            /** We need to add the filters from the lcm config that limit the identities returned so the identity
             * counts are all consistent--this will cause the list of results to be limited by the identities that
             * have the role as well as by the identities that this user can see */
            QueryOptions identityQueryOptions = getLcmConfigService().getConfiguredIdentityQueryOptions(
                    getSearchOptions().getRequester(), null);
            // If this returns null then nothing is in scope -- respond accordingly
            if (null == identityQueryOptions) {
                newOps = null;
                return newOps;
            } else {
                configuredLCMIdentityFilters = FilterConverter.convertFilters(identityQueryOptions.getFilters(), Identity.class, "Identity");
            }
            
            optimizedAssignedOptions.getFilters().addAll(configuredLCMIdentityFilters);
            optimizedDetectedOptions.getFilters().addAll(configuredLCMIdentityFilters);
            
            Configuration sysConfig = Configuration.getSystemConfig();
            int maxRoles = -1;
            if (sysConfig != null) {
                if (sysConfig.containsAttribute(Configuration.LCM_MAX_ROLES_RETURNED_FROM_USER_SEARCH)) {
                    maxRoles = sysConfig.getInt(Configuration.LCM_MAX_ROLES_RETURNED_FROM_USER_SEARCH);
                }
            }

            Date pre = new Date();
            boolean tooManyRolesWarned = false;
            optimizedAssignedOptions.getFilters().add(sailpoint.object.Filter.join("Identity.assignedRoles.id", "Bundle.id"));
            Iterator<Object[]> rows = getContext().search(Identity.class, optimizedAssignedOptions, Util.asList("Bundle.id"));
            while (rows != null && rows.hasNext()) {
                Object[] row = rows.next();
                if (maxRoles > -1 && bundleIds.size() > maxRoles) {
                    if (!tooManyRolesWarned) {
                        _log.warn(TOO_MANY_ROLES);
                        tooManyRolesWarned = true;
                    }
                } else {
                    bundleIds.add((String)row[0]);
                }
            }
            
            optimizedDetectedOptions.getFilters().add(sailpoint.object.Filter.join("Identity.bundles.id", "Bundle.id"));
            rows = getContext().search(Identity.class, optimizedDetectedOptions, Util.asList("Bundle.id"));
            while (rows != null && rows.hasNext()) {
                Object[] row = rows.next();
                if (maxRoles > -1 && bundleIds.size() > maxRoles) {
                    if (!tooManyRolesWarned) {
                        _log.warn(TOO_MANY_ROLES);
                        tooManyRolesWarned = true;
                    }
                } else {
                    bundleIds.add((String)row[0]);
                }
            }
            Date post = new Date();
            _log.debug("Bundle search and iteration took " + (post.getTime() - pre.getTime()) + " ms.");

            if (!Util.isEmpty(bundleIds)) {
                newOps.getFilters().add(0,Filter.in("Bundle.id", bundleIds));
            } else {
                newOps.getFilters().add(Filter.in("Bundle.id", Util.asList("")));
            }
        return newOps;
        }
        return newOps;
    }

    /**
     * Determines if the specified identity has any assigned roles.
     *
     * @param identity The identity.
     * @return True if has assigned roles, false otherwise.
     */
    private boolean hasAssignedRoles(Identity identity) throws GeneralException {
        if (null == identity) {
            return false;
        }

        QueryOptions qo = new QueryOptions();
        qo.add(Filter.and(
                Filter.eq(UserAccessSearchColumns.COLUMN_ID, identity.getId()),
                Filter.isempty(COLUMN_ASSIGNED_ROLES)
        ));

        int count = getCount(Identity.class, qo);

        return count == 0;
    }

    /**
     * Get the permitted roles for the target identity
     * @param identity Target identity
     * @param requester Identity making the request
     * @return List of Maps representing any permitted roles
     * @throws GeneralException
     */
    public List<Map<String, Object>> getPermittedRoles(Identity identity, List<String> searchTerms, Identity requester)
            throws GeneralException {
        
        /*
         * The logic is:
         * 1. If no assigned roles, then there are no permitted roles
         * 2. Get permitted roles for directly assigned roles.
         *    We pass null for the Bundle arg, meaning, get all permitted roles for an identity's directly assigned roles.
         * 3. Get permitted roles for inherited roles of assigned roles.
         *    We pass an assigned role and get the permitted roles for inherited roles of the assigned role.
         */

        if (null == identity ||
                !hasAssignedRoles(identity)) {
            return new ArrayList<Map<String, Object>>();
        }
        
        // passing a null for Bundle here means that we are getting all permitted roles for directly assigned roles.
        List<Map<String, Object>> permittedRoles = getPermittedRoles(identity, searchTerms, requester, null);
        
        List<Bundle> assignedRoles = identity.getAssignedRoles();
        for (Bundle assignedRole : Util.iterate(assignedRoles)) {
            // get inherited permitted roles and add them to permittedRoles
            getInheritedPermitted(identity, searchTerms, requester, assignedRole, permittedRoles);
        }

        // Now remove duplicates in case the multiple lists created any...
        Util.removeDuplicates(permittedRoles);
        
        return permittedRoles;
    }
    
    /**
     * Recursively iterate through inherited roles, getting their permits.
     * @param identity Target identity
     * @param searchTerms
     * @param requester
     * @param role Bundle to be queried for inheritance
     * @param allPermittedRoles List of map where we add the permitted roles
     * @throws GeneralException
     */
    private void getInheritedPermitted(Identity identity, List<String> searchTerms, Identity requester, Bundle role, List<Map<String, Object>> allPermittedRoles)
            throws GeneralException {
        
        List<Bundle> inheritedRoles = role.getInheritance();
        for (Bundle inheritedRole : Util.iterate(inheritedRoles)) {
            List<Map<String, Object>> permittedRoles = getPermittedRoles(identity, searchTerms, requester, inheritedRole);
            allPermittedRoles.addAll(permittedRoles);
            getInheritedPermitted(identity, searchTerms, requester, inheritedRole, allPermittedRoles);
        }

    }
    
    public List<Map<String, Object>> getPermittedRoles(Identity identity, List<String> searchTerms, Identity requester, Bundle role) 
            throws GeneralException {
        // use the same filters as assignables for permitted roles, without assignable filter
        // need to query for inherited roles, so call overloaded getKeywordQueryInfo
        QueryInfo queryInfo = getKeywordQueryInfo(true, role);
        if (queryInfo.isReturnNone()) {
            return new ArrayList<Map<String, Object>>();
        }
        QueryOptions permittedOptions = getQueryOptions(queryInfo.getFilter(), UserAccessSearchColumns.COLUMN_DISPLAYABLE_NAME);

        // we want all permitted roles
        permittedOptions.setFirstRow(0);
        permittedOptions.setResultLimit(0);
        
            /* Exclude any roles that are of a type that is assignable.  This will prevent them from appearing in the list twice
             * TODO: This is confusing.  It seems rather arbitrary for a permitted role to be pushed to the bottom because it also
             * happens to be manually assignable 
             */
        RoleConfig rc = new RoleConfig();
        List<String> assignableTypes = rc.getManuallyAssignableRoleTypes();
        for(String type : assignableTypes) {
            permittedOptions.add(Filter.not(Filter.eq(UserAccessSearchColumns.COLUMN_TYPE, type)));
        }

        //No full text for permitted roles.
        UserAccessSearchResults results = getHibernateResults(Bundle.class, permittedOptions, searchTerms);

        // add permitted flag to denote that this is a top-level permitted role
        for (Map<String, Object> row : results.getResults()) {
            row.put(UserAccessSearchColumns.COLUMN_PERMITTED_ROLE, true);
        }

        return results.getResults();
    }
    
    @Override
    protected UserAccessSearchResults searchCurrentAccess(List<String> searchTerms) throws GeneralException {
        SailPointContext spContext = getContext();
        Identity identity = getSearchOptions().getTargetIdentity();
        CurrentAccessStatus status = getSearchOptions().getStatus();
        CurrentAccessService currentAccessService = new CurrentAccessService(spContext, identity);
        List<CurrentAccessService.CurrentAccessRole> currentAccessRoles = currentAccessService.getRoles(status);

        /*
         * Bug 25530 Remove Access: need to display all instances of a role (assigned/permitted) 
         * when multiple (duplicates) exist
         * Use HashSetValuedHashMap to allow duplicate key (roleId)
         */
        HashSetValuedHashMap idAccessMap = new HashSetValuedHashMap();
        for (CurrentAccessService.CurrentAccessRole currentAccessRole : currentAccessRoles) {
            Bundle role = currentAccessRole.getObject(spContext);
            if(role == null) {
                throw new GeneralException("Current access role does not exist");
            }
            idAccessMap.put(role.getId(), currentAccessRole);
        }

        List<String> accessBundles = new ArrayList<String>(idAccessMap.keySet());
        List<String> filteredBundleIds = getFilteredBundleIds(accessBundles, searchTerms);

        /* Remove entries for roles that were not found */
        for (String bundleId : accessBundles) {
            if(!filteredBundleIds.contains(bundleId)) {
                idAccessMap.remove(bundleId);
            }
        }
        currentAccessRoles = new ArrayList<CurrentAccessService.CurrentAccessRole>(idAccessMap.values());

        UserAccessSearchResults results = convertToResults(currentAccessRoles);
        
        int totalCount = results.getTotalResultCount();
        List<Map<String, Object>> cleanResults = sortAndCleanupResults(results.getResults(), getKeywordSearchComparator(), false);
        
        return new UserAccessSearchResults(cleanResults, totalCount);
    }

    /**
     * Returns subset of bundleIds that also match access filters
     *
     *
     * @param bundleIds List of Bundle Ids to lookup
     * @param searchTerms List of strings to search name with
     * @return Ids of bundles from bundleIds that match access filter
     * @throws GeneralException If problems fetching bundles
     */
    private List<String> getFilteredBundleIds(List<String> bundleIds, List<String> searchTerms) throws GeneralException {
        List<Filter> filters = getObjectTypeOptions().getFilters();
        /* This can be null, at least it happens in test cases */
        if(filters == null) {
            filters = new ArrayList<Filter>();
        }
        filters.addAll(buildSearchTermFilters(searchTerms));
        //add filters from removal rules
        CurrentAccessService currentAccessService = new CurrentAccessService(getContext(), getSearchOptions().getTargetIdentity());
        Filter ruleFilter = currentAccessService.getRoleRuleFilter();
        if (ruleFilter != null) {
            filters.add(ruleFilter);
        }

        SearchResultsIterator bundlesIterator = ObjectUtil.searchAcrossIds(getContext(), Bundle.class, bundleIds, filters, Arrays.asList("id"));
        ArrayList<String> filteredBundleIds = new ArrayList<String>();
        while (bundlesIterator.hasNext()) {
            filteredBundleIds.add((String) bundlesIterator.next()[0]);
        }
        return filteredBundleIds;
    }

    @Override
    protected Map<String, Object> convertToResult(CurrentAccessService.CurrentAccess currentAccess) throws GeneralException {
        Map<String, Object> result = super.convertToResult(currentAccess);
        CurrentAccessService.CurrentAccessRole currentAccessRole = (CurrentAccessService.CurrentAccessRole)currentAccess;
        result.put(UserAccessSearchColumns.COLUMN_ASSIGNMENT_ID, currentAccessRole.getAssignmentId());
        result.put(UserAccessSearchColumns.COLUMN_ASSIGNMENT_NOTE, WebUtil.escapeComment(currentAccessRole.getAssignmentNote()));
        result.put(UserAccessSearchColumns.COLUMN_ROLE_TARGETS, currentAccessRole.getRoleTargets());
        result.put(UserAccessSearchColumns.COLUMN_ROLE_LOCATION, currentAccessRole.getRoleLocation());
        return result;
    }
}
