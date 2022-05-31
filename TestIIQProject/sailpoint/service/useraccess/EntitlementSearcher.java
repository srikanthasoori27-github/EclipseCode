package sailpoint.service.useraccess;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.ManagedAttributeStatistician;
import sailpoint.api.ObjectUtil;
import sailpoint.api.SailPointContext;
import sailpoint.api.SearchResultsIterator;
import sailpoint.object.Application;
import sailpoint.object.Configuration;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.ManagedAttribute;
import sailpoint.object.QueryInfo;
import sailpoint.object.QueryOptions;
import sailpoint.object.QuickLink;
import sailpoint.search.MapMatcher;
import sailpoint.service.CurrentAccessService;
import sailpoint.service.CurrentAccessService.CurrentAccessStatus;
import sailpoint.service.LCMConfigService;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

/**
 * Override of UserAccessSearcher to do a search over Entitlements
 */
public class EntitlementSearcher extends UserAccessSearcher {

    private static final Log log = LogFactory.getLog(EntitlementSearcher.class);
    private static final String APPLICATION_QUERY_PREFIX = "application.";

    // This is the key for the results map in the convertToSimple method that we use to store the currentAccessObj
    private static final String COLUMN_CURRENT_ACCESS = "currentAccessObj";

    /**
     * Constructor
     * @param context SailPointContext
     * @param searchOptions UserAccessSearchOptions
     * @throws sailpoint.tools.GeneralException
     */
    public EntitlementSearcher(SailPointContext context, UserAccessSearchOptions searchOptions)
            throws GeneralException {
        super(context, searchOptions);
    }

    @Override
    protected UserAccessSearchOptions.ObjectTypes getObjectType() {
        return UserAccessSearchOptions.ObjectTypes.Entitlement;
    }

    @Override
    protected String getQuickLinkAction() {
        return QuickLink.LCM_ACTION_REQUEST_ACCESS;
    }

    @Override
    protected String getQuickLinkRequestControlOption() {
        return Configuration.LCM_ALLOW_REQUEST_ENTITLEMENTS;
    }
    
    @Override
    protected QueryOptions addSearchTerms(QueryOptions ops, List<String> searchTerms) throws GeneralException {
        QueryOptions newOps = new QueryOptions(ops);
        List<Filter> filters = buildSearchTermFilters(searchTerms);
        newOps.add(filters.toArray(new Filter[filters.size()]));
        return newOps;
    }

    private List<Filter> buildSearchTermFilters(List<String> searchTerms) throws GeneralException {
        List<Filter> filters = new ArrayList<Filter>();
        Filter.MatchMode matchMode = getMatchMode();
        if (!Util.isEmpty(searchTerms)) {
            for (String searchTerm : searchTerms) {
                filters.add(Filter.or(
                        Filter.ignoreCase(Filter.like(UserAccessSearchColumns.COLUMN_APP_NAME, searchTerm, matchMode)),
                        Filter.ignoreCase(Filter.like(UserAccessSearchColumns.COLUMN_DISPLAYABLE_NAME, searchTerm, matchMode))
                ));
            }
        }
        return filters;
    }

    @Override
    public QueryInfo getKeywordQueryInfo() throws GeneralException {
        List<Filter> filters = Util.isEmpty(getObjectTypeOptions().getFilters()) ? new ArrayList<Filter>() :
                new ArrayList<Filter>(getObjectTypeOptions().getFilters());

        // Only return Entitlements
        filters.add(Filter.ne(UserAccessSearchColumns.COLUMN_TYPE, ManagedAttribute.Type.Permission.name()));
        
        // Requestables filters
        Filter requestableFilter = getRequestablesFilter();
        if (requestableFilter != null) {
            filters.add(requestableFilter);
        }

        // LCM Authority filters
        // 1. Application filter
        QueryInfo appQuery = getLCMAuthorityFilter(LCMConfigService.SelectorObject.Application);
        boolean returnNothing = appQuery.isReturnNone();
        if (appQuery.getFilter() != null) {
            // This filter assumes it is querying over applications.  Need to
            // prefix all filters with "application.".
            filters.add(prefixApplication(appQuery.getFilter()));
        }

        //2. Managed Attribute filter -- Don't bother if all the apps are out of scope anyway
        if (!returnNothing) {
            QueryInfo managedAttributeQuery = getLCMAuthorityFilter(LCMConfigService.SelectorObject.ManagedAttribute);
            returnNothing = managedAttributeQuery.isReturnNone();
            if (managedAttributeQuery.getFilter() != null) {
                filters.add(managedAttributeQuery.getFilter());
            }
        }

        if (returnNothing) {
            //bug#25347 -- need to pass null as filter to QueryInfo
            return new QueryInfo(null, returnNothing);
        } else {
            return new QueryInfo(Filter.and(filters), returnNothing);
        }
    }

    /**
     * Perform a keyword based search for entitlements
     * @param searchTerms List of search terms, or null
     * @return UserAccessSearchResults with the results of the search
     * @throws GeneralException
     */
    @Override
    protected UserAccessSearchResults searchKeyword(List<String> searchTerms) throws GeneralException {

        QueryInfo queryInfo = getKeywordQueryInfo();
        if (queryInfo.isReturnNone()) {
            return createEmptyResults();
        }

        return getResults(ManagedAttribute.class, getQueryOptions(queryInfo.getFilter(), UserAccessSearchColumns.COLUMN_DISPLAYABLE_NAME), searchTerms);
    }

    @Override
    protected UserAccessSearchResults searchIdentity() throws GeneralException {

        QueryOptions qo = getLcmConfigService().getRequestableIdentityOptions(
                getSearchOptions().getRequester(),
                null,
                getSearchOptions().getQuickLinkName(),
                getQuickLinkAction());

        // Distinguish between unrestricted access to identities and the case where no identities were in scope
        List<Filter> identityFilters = new ArrayList<Filter>(getObjectTypeOptions().getIdentityFilters());
        boolean noIdentitiesInScope = (null == qo);
        if ((null != qo) && (null != qo.getRestrictions())) {
            identityFilters.addAll(qo.getRestrictions());
        }

        QueryInfo appQuery = getLCMAuthorityFilter(LCMConfigService.SelectorObject.Application);
        if (appQuery.isReturnNone()) {
            return createEmptyResults();
        }

        QueryInfo managedAttrQuery = getLCMAuthorityFilter(LCMConfigService.SelectorObject.ManagedAttribute);
        if (managedAttrQuery.isReturnNone()) {
            return createEmptyResults();
        }
        // Now that we've determined that there could be something to show we'll search
        Filter appFilter = appQuery.getFilter();
        Filter managedAttrFilter = managedAttrQuery.getFilter();

        //Add "requestable" filter unless otherwise specified
        Filter requestableFilter = getRequestablesFilter();
        if (requestableFilter != null) {
            managedAttrFilter = managedAttrFilter == null ? requestableFilter : Filter.and(managedAttrFilter, requestableFilter);
        }

        try {
            if (noIdentitiesInScope) {
                return createEmptyResults();
            }
            ManagedAttributeStatistician stater =
                    new ManagedAttributeStatistician(getContext());
            ManagedAttributeStatistician.PopulationStats stats =
                    stater.crunchPopulationStats(identityFilters, appFilter, managedAttrFilter);

            if (stats == null || stats.getManagedAttributeIds().isEmpty()) {
                return createEmptyResults();
            }
            else {

                // Iterate over managed attributes and create rows for each.
                // Note that since there isn't currently a searchable association
                // between identities and their entitlements we are not using a
                // query.
                // jsl - does this scale?  
                SearchResultsIterator rowsIterator =
                        ObjectUtil.searchAcrossIds(getContext(),
                                ManagedAttribute.class,
                                new ArrayList<String>(stats.getManagedAttributeIds()),
                                null, getFullColumns());
                List<Map<String,Object>> rows = convertRows(rowsIterator, getFullColumns());

                // Add the population statistics to the rows.
                addPopulationStats(rows, identityFilters, stats.getTotalIdentities(), createCounter(stats));

                // Filter the results.
                rows = filterByPercent(rows);

                int total = rows.size();
                rows = sortAndCleanupResults(rows, getIdentitySearchComparator(), true);

                return new UserAccessSearchResults(rows, total);
            }
        }
        catch (ManagedAttributeStatistician.SizeExceededException e) {
            // Too many!  Return an error to be displayed in the UI.
            throw new GeneralException(e);
        }
    }

    @Override
    protected UserAccessSearchResults searchCurrentAccess(List<String> searchTerms) throws GeneralException {
        List<CurrentAccessService.CurrentAccessEntitlement> currentAccessEntitlements = getCurrentAccessEntitlements();
        /* Iterate over the currentAccessEntitlements.  For each entitlement with a matching ManagedAttribute add
         * it to the map.  For each entitlement without a ManagedAttribute add it to the manualReconcile list
         * to be handled later. */
        List<CurrentAccessService.CurrentAccessEntitlement> manualReconcileList = new ArrayList<CurrentAccessService.CurrentAccessEntitlement>();
        HashMap<String, List<CurrentAccessService.CurrentAccessEntitlement>> idEntitlementMap = new HashMap<String, List<CurrentAccessService.CurrentAccessEntitlement>>();
        for (CurrentAccessService.CurrentAccessEntitlement currentAccessEntitlement : currentAccessEntitlements) {
            log.debug("Entitlement value = " + currentAccessEntitlement.getValue() +
                    ", attribute = " + currentAccessEntitlement.getAttribute() +
                    ", applicationName = " + currentAccessEntitlement.getApplicationName());
            // IIQETN-4215: We dont need the whole managed attribute here, just the ID
            Object[] attrs = currentAccessEntitlement.getObjectAttrs(getContext(), Arrays.asList("id"));
            if(attrs == null) {
                manualReconcileList.add(currentAccessEntitlement);
            } else {
                log.debug("Managed Attribute found." + attrs);
                String id = (String)attrs[0];
                if(!idEntitlementMap.containsKey(id)) {
                    idEntitlementMap.put(id, new ArrayList<CurrentAccessService.CurrentAccessEntitlement>());
                }
                List<CurrentAccessService.CurrentAccessEntitlement> caeList = idEntitlementMap.get(id);
                caeList.add(currentAccessEntitlement);
            }
        }

        // Apply the filters to the ManagedAttributes we found to get matching subset
        // Get back the ID and DisplayableName values for the matches
        Map<String,String> filteredEntitlementIds = getFilteredEntitlmentsIds(idEntitlementMap.keySet(), searchTerms);

        List<Map<String, Object>> dirtyResults = new ArrayList<Map<String, Object>>();

        // Filter out of the map items that have ManagedAttributes that match the filters
        Iterator<Map.Entry<String,String>> entryIterator = filteredEntitlementIds.entrySet().iterator();
        //Iterator<Map.Entry<String, List<CurrentAccessService.CurrentAccessEntitlement>>> entryIterator = idEntitlementMap.entrySet().iterator();
        while (entryIterator.hasNext()) {
            Map.Entry<String, String> entry = entryIterator.next();
            String id = entry.getKey();
            // Loop through each currentAccess object for this ID and add it to the results
            for (CurrentAccessService.CurrentAccessEntitlement currentAccess : idEntitlementMap.get(id)) {
                // IIQETN-4215:  if we have an extremely large list of Entitlements, grabing the details of the entitlement
                // at this point will cause an extremely large number of queries on the database for information we probably
                // wont use later on.  Only get the minimum information for each entitlement at this point to significantly
                // reduce the number of database calls needed.
                dirtyResults.add(convertToSimpleResult(id, filteredEntitlementIds.get(id), currentAccess));
            }
        }

        /* We use the MapMatcher to apply the filter CurrentAccessEntitlements without ManagedAttributes */
        MapMatcher matcher = new MapMatcher(Filter.and(getCurrentAccessFilters(searchTerms)));
        /* For the items without managed attributes.  If the currentAccessEntitlement matches the filters add it to the list */
        for (CurrentAccessService.CurrentAccessEntitlement currentAccessEntitlement : manualReconcileList) {
            Map<String, String> matcherMap = buildMatcherTarget(currentAccessEntitlement);
            if(matcher.matches(matcherMap)) {
                dirtyResults.add(convertToSimpleResult(null, null, currentAccessEntitlement));
            }
        }

        int totalCount = dirtyResults.size();

        // Get and save the current value for OPTION_NO_RESULT_CLEANUP and then set to false.
        boolean noResultCleanup = getSearchOptions().getAttributes().getBoolean(UserAccessSearchOptions.OPTION_NO_RESULT_CLEANUP);

        List<Map<String, Object>> cleanResults = null;

        if (noResultCleanup) {
            // We got here from the CombinedSearcher searchCurrentAccess method.  We need to limit the number of
            // returned results based on the maxResultCount searchOptions attribute.
            getSearchOptions().setAttribute(UserAccessSearchOptions.OPTION_NO_RESULT_CLEANUP, false);

            // Sort and Trim up the dirtyResults and only return the maxResultCount specified in the configuration
            getSearchOptions().setAttribute(UserAccessSearchOptions.OPTION_MAXRESULT_CLEANUP_ONLY, true);
            cleanResults = sortAndCleanupResults(dirtyResults, getKeywordSearchComparator(), false);

            // Set OPTION_MAXRESULT_CLEANUP_ONLY to false after the sort and cleanup is done.  Reset the OPTION_NO_RESULT_CLEANUP
            // attribute to its original value that we saved above.
            getSearchOptions().setAttribute(UserAccessSearchOptions.OPTION_MAXRESULT_CLEANUP_ONLY, false);
            getSearchOptions().setAttribute(UserAccessSearchOptions.OPTION_NO_RESULT_CLEANUP, noResultCleanup);
        } else {
            // Sort and Trim up the dirty Results normally using the limit set in the searchOptions attribute
            cleanResults = sortAndCleanupResults(dirtyResults, getKeywordSearchComparator(), false);
        }

        // IIQETN-4215: Now that we have reduced the results to what needs to be returned, we can get the detailed information
        // for each entitlement prior to the return.  This will be at most maxResultCount from the searchOptions.
        return convertToDetailedResults(cleanResults, totalCount);
    }

    private List<CurrentAccessService.CurrentAccessEntitlement> combineCurrentAccessEntitlements(Collection<List<CurrentAccessService.CurrentAccessEntitlement>> entitlementLists) {
        List<CurrentAccessService.CurrentAccessEntitlement> entitlements = new ArrayList<CurrentAccessService.CurrentAccessEntitlement>();
        for (List<CurrentAccessService.CurrentAccessEntitlement> entitlementList : entitlementLists) {
            entitlements.addAll(entitlementList);
        }
        return entitlements;
    }

    /**
     * Takes the passed CurrentAccessEntitlement and generates a map that can be used with MapMatcher and the existing filters
     * to determine if the CurrentAccessEntitlement matches
     * @param target The item to be compared
     * @return Map compatible with filters to pass to MapMatcher
     * @throws GeneralException If unable to get the Application for the target
     */
    private Map<String, String> buildMatcherTarget(CurrentAccessService.CurrentAccessEntitlement target) throws GeneralException {
        Map<String, String> map = new HashMap<String, String>();
        /* The application filter uses application.id.  The searchTerm filters use application.name and displayableName,
         * here played by value as CurrentAccessEntitlement does not have that property.  And the attribute filter uses
         * the attribute property */
        Application application = getContext().getObjectByName(Application.class, target.getApplicationName());
        if(application != null) {
            map.put("application.id", application.getId());
        }
        map.put("application.name", target.getApplicationName());
        map.put("attribute", target.getAttribute());
        map.put("displayableName", target.getValue());
        return map;
    }

    private List<CurrentAccessService.CurrentAccessEntitlement> getCurrentAccessEntitlements() throws GeneralException {
        Identity identity = getSearchOptions().getTargetIdentity();
        CurrentAccessService currentAccessService = new CurrentAccessService(getContext(), identity);
        CurrentAccessStatus status = getSearchOptions().getStatus();
        return currentAccessService.getExceptions(status);
    }

    /**
     * Returns subset of entitlementIds that also match access filters
     *
     * @param entitlementIds Collection of managed attributes ids to lookup
     * @param searchTerms List of search terms
     * @return A Managed Attribute id to displayableName Mapping from entitlementIds that match access filter
     * @throws GeneralException If problems fetching Managed Attributes
     */
    private Map<String, String> getFilteredEntitlmentsIds(Collection<String> entitlementIds, List<String> searchTerms)
            throws GeneralException {
        List<Filter> filters = getCurrentAccessFilters(searchTerms);
        SearchResultsIterator entitlementIterator = ObjectUtil.searchAcrossIds(getContext(), ManagedAttribute.class,
                new ArrayList<String>(entitlementIds), filters, Arrays.asList("id","displayableName"));
        Map<String, String> filteredEntitlementIds = new HashMap<String, String>();
        while (entitlementIterator.hasNext()) {
            Object[] entry = entitlementIterator.next();
            filteredEntitlementIds.put((String)entry[0],(String)entry[1]);
        }
        return filteredEntitlementIds;
    }

    /**
     *
     * @param searchTerms
     * @return a list of filters for current access entitlements. This includes the filters coming from quicklink population removal rules
     * @throws GeneralException
     */
    private List<Filter> getCurrentAccessFilters(List<String> searchTerms) throws GeneralException {
        List<Filter> filters = new ArrayList<Filter>();
        List<Filter> objectFilters = getObjectTypeOptions().getFilters();
        if(!Util.isEmpty(objectFilters)) {
            filters.addAll(objectFilters);
        }
        if(!Util.isEmpty(searchTerms)) {
            filters.addAll(buildSearchTermFilters(searchTerms));
        }
        CurrentAccessService currentAccessService = new CurrentAccessService(getContext(), getSearchOptions().getTargetIdentity());
        Filter ruleFilter = currentAccessService.getEntitlementRuleFilter();
        if (ruleFilter != null) {
            filters.add(ruleFilter);
        }
        return filters;
    }

    /**
     * Convert from a simple result to a detailed result that includes all attributes necessary for the Entitlement
     * @param simpleResult
     * @return A key/value map including all attributes needed for displaying on the UI
     * @throws GeneralException
     */
    protected Map<String, Object> convertToResult(Map<String, Object> simpleResult)
            throws GeneralException {
        CurrentAccessService.CurrentAccessEntitlement currentAccessEntitlement =
                (CurrentAccessService.CurrentAccessEntitlement)simpleResult.get(COLUMN_CURRENT_ACCESS);

        Map<String, Object> result = new HashMap<String, Object>();

        // First build the results from the object attributes.
        List<String> properties = getFullColumns();
        Object[] objAttrs = currentAccessEntitlement.getObjectAttrs(getContext(), properties);
        if (null != objAttrs) {
            for (int i=0; i<properties.size(); i++) {
                result.put(properties.get(i), objAttrs[i]);
            }
        }

        // Add the additional information needed from the currentAccess object
        result.putAll(getCurrentAccessAdditionalResults(currentAccessEntitlement));

        enhanceRow(result);

        if (objAttrs == null) {
            // Not every exception has a ManagedAttribute, so get some common columns from the CurrentAccess 
            result.put(UserAccessSearchColumns.COLUMN_DISPLAYABLE_NAME, currentAccessEntitlement.getValue());
            result.put(UserAccessSearchColumns.COLUMN_ATTRIBUTE, currentAccessEntitlement.getAttribute());
            result.put(UserAccessSearchColumns.COLUMN_VALUE, currentAccessEntitlement.getValue());
            result.put(UserAccessSearchColumns.COLUMN_APP_NAME, currentAccessEntitlement.getApplicationName());
        }

        if (!Util.isNullOrEmpty(currentAccessEntitlement.getInstance())) {
            result.put(UserAccessSearchColumns.COLUMN_INSTANCE, currentAccessEntitlement.getInstance());
        }
        result.put(UserAccessSearchColumns.COLUMN_NATIVE_IDENTITY, currentAccessEntitlement.getNativeIdentity());
        result.put(UserAccessSearchColumns.COLUMN_ACCOUNT_NAME, currentAccessEntitlement.getAccount());
        return result;
    }

    /**
     * Create a minimal Result map containing only the attributes necessary to perform sorting of these objects.
     * @param id
     * @param displayableName
     * @param currentAccess
     * @return A key/value mapping of object attributes
     * @throws GeneralException
     */
    protected Map<String, Object> convertToSimpleResult(String id, String displayableName, CurrentAccessService.CurrentAccess currentAccess)
            throws GeneralException {
        Map<String, Object> result = new HashMap<String, Object>();

        if (Util.isNotNullOrEmpty(id)) {
            result.put(UserAccessSearchColumns.COLUMN_ID, id);
        }

        if (Util.isNotNullOrEmpty(displayableName)) {
            result.put(UserAccessSearchColumns.COLUMN_DISPLAYABLE_NAME, displayableName);
        } else {
            // Get the DISPLAYABLE_NAME from the Entitlement
            CurrentAccessService.CurrentAccessEntitlement currentAccessEntitlement =
                    (CurrentAccessService.CurrentAccessEntitlement)currentAccess;
            result.put(UserAccessSearchColumns.COLUMN_DISPLAYABLE_NAME, currentAccessEntitlement.getValue());
        }

        result.put(COLUMN_CURRENT_ACCESS, currentAccess);

        return result;
    }

    protected UserAccessSearchResults convertToDetailedResults(List<Map<String, Object>> simpleResultsList, int count)
            throws GeneralException {
        if (Util.isEmpty(simpleResultsList)) {
            return createEmptyResults();
        }

        List<Map<String, Object>> results = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> simpleResult : simpleResultsList) {
            results.add(convertToResult(simpleResult));
        }
        return new UserAccessSearchResults(results, count);
    }

    /**
     * Return the population counter using the given stats.
     */
    private PopulationCounter createCounter(final ManagedAttributeStatistician.PopulationStats stats) {
        return new PopulationCounter() {
            public int getCount(Map<String, Object> row) {
                return stats.getCount((String) row.get("id"));
            }
        };
    }

    /**
     * Get a filter for requestable entitlements, unless options says not to
     * @return Filter, or null
     */
    private Filter getRequestablesFilter() {
        if (!getObjectTypeOptions().getAttributes().getBoolean(UserAccessSearchOptions.ObjectTypeOptions.OPTION_INCLUDE_NON_REQUESTABLE_ENTITLEMENTS)) {
            return Filter.eq("requestable", true);
        }

        return null;
    }

    /**
     * Return a copy of the given filter with all properties prefixed with
     * "application.".
     */
    private Filter prefixApplication(Filter f) {
        // Clone it since we're going to be changing it.  Just in case...
        f = Filter.clone(f);

        if (f instanceof Filter.LeafFilter) {
            Filter.LeafFilter leaf = (Filter.LeafFilter) f;
            String prop = leaf.getProperty();
            if (!prop.startsWith(APPLICATION_QUERY_PREFIX)) {
                leaf.setProperty(APPLICATION_QUERY_PREFIX + prop);
            }
        }
        else if (f instanceof Filter.CompositeFilter) {
            List<Filter> modifiedChildren = new ArrayList<Filter>();
            for (Filter child : ((Filter.CompositeFilter) f).getChildren()) {
                modifiedChildren.add(prefixApplication(child));
            }
            if (!modifiedChildren.isEmpty()) {
                ((Filter.CompositeFilter)f).setChildren(modifiedChildren);
            }
        }

        return f;
    }
}
