package sailpoint.rest;

import java.util.*;

import sailpoint.api.EntitlementDescriber;
import sailpoint.api.Explanator;
import sailpoint.api.Localizer;
import sailpoint.api.ObjectUtil;
import sailpoint.api.SailPointContext;
import sailpoint.object.*;
import sailpoint.service.CurrentAccessService.CurrentAccessStatus;
import sailpoint.service.LCMConfigService;
import sailpoint.service.ManagedAttributeServiceContext;
import sailpoint.service.classification.ClassificationService;
import sailpoint.service.listfilter.BaseUserAccessFilterContext;
import sailpoint.service.listfilter.CurrentAccessFilterContext;
import sailpoint.service.listfilter.ListFilterContext;
import sailpoint.service.listfilter.ListFilterDTO;
import sailpoint.service.listfilter.ListFilterService;
import sailpoint.service.listfilter.ListFilterValue;
import sailpoint.service.listfilter.UserAccessEntitlementFilterContext;
import sailpoint.service.listfilter.UserAccessIdentityFilterContext;
import sailpoint.service.listfilter.UserAccessPopulationFilterContext;
import sailpoint.service.listfilter.UserAccessRoleFilterContext;
import sailpoint.service.quicklink.QuickLinkOptionsConfigService;
import sailpoint.service.useraccess.UserAccessSearchColumns;
import sailpoint.service.useraccess.UserAccessSearchOptions;
import sailpoint.service.useraccess.UserAccessSearchOptions.ObjectTypes;
import sailpoint.service.useraccess.UserAccessSearchResults;
import sailpoint.service.useraccess.UserAccessSearcher;
import sailpoint.service.useraccess.UserAccessService;
import sailpoint.tools.GeneralException;
import sailpoint.tools.InvalidParameterException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.util.WebUtil;
/**
 * A utility adapter between REST resources and UserAccessService
 */
public class UserAccessUtil {
    /* Constants */
    public static final String ACCESS_TYPE_ROLES = "Role";
    public static final String ACCESS_TYPE_ENTITLEMENT = "Entitlement";
    public static final String PARAM_ACCESS_TYPE = "accessType";
    public static final String PARAM_SEARCH_TYPE= "searchType";
    public static final String SEARCH_TYPE_CURRENT_ACCESS= "CurrentAccess";
    public static final String SEARCH_TYPE_IDENTITY = "Identity";
    public static final String SEARCH_TYPE_KEYWORD = "Keyword";
    public static final String SEARCH_TYPE_POPULATION = "Population";
    public static final String PARAM_IDENTITY_ID = "identityId";
    public static final String PARAM_QUICK_LINK_NAME = "quickLink";
    
    private static final String PARAM_START = "start";
    private static final String PARAM_LIMIT = "limit";
    private static final String PARAM_REQUESTABLE = "requestable";
    private static final String PARAM_QUERY = "query";
    private static final String PARAM_STATUS= "status";
    private static final String PARAM_VALUE_STATUS_ACTIVE = "active";
    private static final String PARAM_VALUE_STATUS_REQUESTED = "requested";
    
    public static final String COLUMN_RISK_SCORE = "riskScore";
    public static final String COLUMN_RISK_SCORE_WEIGHT = UserAccessSearchColumns.COLUMN_RISK_SCORE;
    public static final String COLUMN_RISK_SCORE_COLOR = "riskScoreColor";
    public static final String COLUMN_RISK_SCORE_TEXT_COLOR = "riskScoreTextColor";
    public static final String COLUMN_POP_TOTAL = "IIQ_population_total";
    public static final String COLUMN_POP = "IIQ_population";
    public static final String COLUMN_HAS_HIGH_RISK = "IIQ_has_high_risk";
    public static final String COLUMN_CLASSIFICATIONS = "classifications";

    private static final String COLUMN_ACCESS_TYPE = "accessType";

    /* Default values */
    private static final int DEFAULT_START = 0;
    private static final int DEFAULT_LIMIT = 200;
    private static final CurrentAccessStatus DEFAULT_STATUS = null;

    private SailPointContext spContext;
    private Locale locale;
    private Localizer localizer;
    private IdentitySearchUtil identitySearchUtil;
    private LCMConfigService lcmConfigService;

    public UserAccessUtil(SailPointContext spContext, Locale locale) {
        this.spContext = spContext;
        this.locale = locale;
    }

    /**
     * Setup some initial state.
     * @throws GeneralException If we cannot load object config
     */
    private void init() throws GeneralException {
        identitySearchUtil = new IdentitySearchUtil(spContext);
        localizer = new Localizer(spContext);
        this.lcmConfigService = new LCMConfigService(this.spContext);
    }

    /**
     * Adapts queryParameters into Filters and uses UserAccessService to perform search
     * @param queryParameters Parameters to apply to search
     * @param columns Map of ColumnConfig lists keyed by access type
     * @param requester Requester identity               
     * @return Search results
     * @throws GeneralException If search fails
     */
    //TODO: this method has quickly become a maintenance disaster, need to break it up to save my sanity
    public UserAccessSearchResults getResults(Map<String, String> queryParameters, Map<String, List<ColumnConfig>> columns, Identity requester) throws GeneralException {
        /* Initialize default values */
        init();
        int start = DEFAULT_START;
        int limit = DEFAULT_LIMIT;
        CurrentAccessStatus status = DEFAULT_STATUS;
        
        // Load start/limit values from parameters
        if(queryParameters.containsKey(PARAM_START)) {
            start = Integer.parseInt(queryParameters.get(PARAM_START), 10);
        }
        if(queryParameters.containsKey(PARAM_LIMIT)) {
            limit = Integer.parseInt(queryParameters.get(PARAM_LIMIT), 10);
        }
        // Grab the status off the filterValue object if there is one
        if(queryParameters.containsKey(PARAM_STATUS)) {
            Object paramStatus = getStatus(queryParameters);
            if (Util.nullSafeEq(PARAM_VALUE_STATUS_ACTIVE, paramStatus)) {
                status = CurrentAccessStatus.Active;
            } else if (Util.nullSafeEq(PARAM_VALUE_STATUS_REQUESTED, paramStatus)) {
                status = CurrentAccessStatus.Requested;
            }
        }
        // Get search type
        UserAccessSearchOptions.SearchTypes searchType = getSearchType(queryParameters);

        // Handle identity search
        boolean isSearchTypeIdentity = searchType.isIdentitySearch();
        List<Filter> identityFilters = getIdentityFilters(queryParameters);
        if (!isSearchTypeIdentity && !Util.isEmpty(identityFilters)) {
            throw new InvalidParameterException(new Message("Identity attribute filters can only be include for Identity search type"));
        }
        Identity targetIdentity = getTargetIdentity(queryParameters);
        String quickLinkName = getQuickLinkName(queryParameters);

        ListFilterContext roleFilterContext = new UserAccessRoleFilterContext(requester, targetIdentity);
        boolean isDeepLinkRoles = roleFilterContext.hasDeepLinksParams(queryParameters);
        ListFilterContext entitlementFilterContext = new UserAccessEntitlementFilterContext(requester, targetIdentity, true);
        boolean isDeepLinkEntitlements = entitlementFilterContext.hasDeepLinksParams(queryParameters);
        // Access type filters of any kind are not created if there exist deep links filters of any kind.       
        boolean isAccessTypeRoles = !(isDeepLinkRoles || isDeepLinkEntitlements) &&
                (!queryParameters.containsKey(PARAM_ACCESS_TYPE) || ACCESS_TYPE_ROLES.equals(queryParameters.get(PARAM_ACCESS_TYPE)));
        boolean isAccessTypeEntitlements = !(isDeepLinkRoles || isDeepLinkEntitlements) &&
                (!queryParameters.containsKey(PARAM_ACCESS_TYPE) || ACCESS_TYPE_ENTITLEMENT.equals(queryParameters.get(PARAM_ACCESS_TYPE)));

        // Get the filters for the object types
        List<Filter> roleFilters = null;
        List<Filter> entitlementFilters = null;
        List<Filter> objectIdentityFilters = null;
        
        // Get the identity/population search filters off first
        if (isSearchTypeIdentity) {
            ListFilterContext identityContext = (UserAccessSearchOptions.SearchTypes.Identity.equals(searchType)) ?
                    new UserAccessIdentityFilterContext() : new UserAccessPopulationFilterContext();

            objectIdentityFilters = getObjectTypeFilters(queryParameters, identityContext);
        }
        
        // Then get the role/entitlement filters
        if (isAccessTypeRoles || isDeepLinkRoles) {
            roleFilters = getObjectTypeFilters(queryParameters, roleFilterContext);
            // support filtering by ID for authentication of RoleDetails
            if (queryParameters.containsKey("id")) {
                Filter idFilter = Filter.eq("id", queryParameters.get("id"));
                roleFilters.add(idFilter);
            }
        }
        if (isAccessTypeEntitlements || isDeepLinkEntitlements) {
            entitlementFilters = getObjectTypeFilters(queryParameters, entitlementFilterContext);
        }

        // If access type entitlement filters exist, can't get access type roles, and vice versa
        isAccessTypeRoles &= Util.isEmpty(entitlementFilters);
        isAccessTypeEntitlements &= Util.isEmpty(roleFilters);
        
        // If no filters of any kind, just return an empty result.
        if (!(isAccessTypeEntitlements || isAccessTypeRoles || isDeepLinkEntitlements || isDeepLinkRoles)) {
            //TODO: log?
            return new UserAccessSearchResults(new ArrayList<Map<String, Object>>(), 0);
        }
        
        // If any identity related filters exist, add them to both role and entitlement filters.
        if (!Util.isEmpty(objectIdentityFilters)) {
            if (roleFilters != null) {
                roleFilters.addAll(objectIdentityFilters);
            }
            if (entitlementFilters != null) {
                entitlementFilters.addAll(objectIdentityFilters);
            }
        }
        
        List<String> roleColumns = getProjectionColumns(columns.get(ACCESS_TYPE_ROLES));
        List<String> entitlementColumns = getProjectionColumns(columns.get(ACCESS_TYPE_ENTITLEMENT));

        // Set up the search options
        UserAccessSearchOptions searchOptions = new UserAccessSearchOptions(start, limit);
        searchOptions.setSearchType(searchType);
        searchOptions.setRequester(requester);
        searchOptions.setStatus(status);
        searchOptions.setQuickLink(quickLinkName);
        
        if (targetIdentity != null) {
            searchOptions.setTargetIdentity(targetIdentity);
        }

        searchOptions.setLocale(this.locale);
        searchOptions.setMaxResultCount(this.lcmConfigService.getSearchMaxResultCount());

        if(isAccessTypeRoles || isDeepLinkRoles) {
            populateSearchOption(ObjectTypes.Role, roleFilters, identityFilters, queryParameters,
                    roleColumns, searchOptions);
        }

        if(isAccessTypeEntitlements || isDeepLinkEntitlements) {
            populateSearchOption(ObjectTypes.Entitlement, entitlementFilters, identityFilters, queryParameters,
                    entitlementColumns, searchOptions);
        }

        List<String> searchTerms = (isSearchTypeIdentity) ? null : getSearchTerms(queryParameters);
        UserAccessService userAccessService = new UserAccessService(spContext);
        UserAccessSearchResults searchResults = userAccessService.search(searchTerms, searchOptions);

        /* Load dynamic columns from results */
        List<Map<String, Object>> results = searchResults.getResults();
        loadCalculatedColumns(results, columns);
        pruneResults(results, requester, targetIdentity, quickLinkName);
        return searchResults;
    }

    /**
     * Minimized version of getResults for authorizing role/entitlements against logged in user.
     * Needs to handle current access type search and keyword search. Target id optional.
     *
     * @param queryParameters
     * @param requester
     * @return int result count
     * @throws GeneralException
     */
    public int getAuthResults(Map<String, String> queryParameters, Identity requester) throws GeneralException {
        /* Initialize default values */
        init();
        int start = DEFAULT_START;
        int limit = DEFAULT_LIMIT;

        // Get search type. this can be a param.
        UserAccessSearchOptions.SearchTypes searchType = getSearchType(queryParameters);

        // Only handling Keyword and CurrentAccess
        if (searchType != UserAccessSearchOptions.SearchTypes.CurrentAccess && searchType != UserAccessSearchOptions.SearchTypes.Keyword) {
            return 0;
        }

        List<Filter> filtersList = new ArrayList<>();

        if (queryParameters.containsKey("id")) {
            // support filtering by ID for authentication of RoleDetails and ManagedAttributeDetails
            Filter idFilter = Filter.eq("id", queryParameters.get("id"));
            filtersList.add(idFilter);
        }

        List<String> columns = Arrays.asList("id");
        Identity targetIdentity = getTargetIdentity(queryParameters);

        // Set up the search options
        UserAccessSearchOptions searchOptions = new UserAccessSearchOptions(start, limit);
        searchOptions.setSearchType(searchType);
        searchOptions.setRequester(requester);
        // this should always be set but fall back to request access if it isn't
        searchOptions.setQuickLink(queryParameters.get("quickLink") != null ? queryParameters.get("quickLink") : QuickLink.QUICKLINK_REQUEST_ACCESS);
        searchOptions.setLocale(this.locale);
        searchOptions.setMaxResultCount(1);
        searchOptions.setTargetIdentity(targetIdentity);

        populateSearchOption(ObjectTypes.Role, filtersList, null, queryParameters, columns, searchOptions);
        populateSearchOption(ObjectTypes.Entitlement, filtersList, null, queryParameters,
                columns, searchOptions);

        UserAccessService userAccessService = new UserAccessService(spContext);
        // pass in empty searchTerms list to force hibernate search
        UserAccessSearchResults searchResults = userAccessService.search(new ArrayList<>(), searchOptions);

        /* Load dynamic columns from results */
        List<Map<String, Object>> results = searchResults.getResults();
        return results.size();
    }

    /**
     * Get the filters for access item search
     * @param queryParameters Parameters containing values
     * @param requester Identity making the request
     * @return List of ListFilterDTO objects
     * @throws GeneralException
     */
    @SuppressWarnings("unchecked")
    public List<ListFilterDTO> getAccessItemFilters(Map<String, String> queryParameters, Identity requester, String suggestUrl)
            throws GeneralException {
        // Don't need full init here, just the identity search util
        this.identitySearchUtil = new IdentitySearchUtil(this.spContext);
        Identity target = getTargetIdentity(queryParameters);
        UserAccessSearchOptions.SearchTypes searchType = getSearchType(queryParameters);
        // Key is filter context, value is string to enhance label with. Order matters. 
        Map<BaseUserAccessFilterContext, String> filterContexts = new LinkedHashMap<BaseUserAccessFilterContext, String>();
        boolean makeIdentityAttribute = false;
        if (UserAccessSearchOptions.SearchTypes.Keyword.equals(searchType)) {
            filterContexts.put(new UserAccessRoleFilterContext(requester, target), MessageKeys.UI_ACCESS_FILTER_ROLE);
            filterContexts.put(new UserAccessEntitlementFilterContext(requester, target), MessageKeys.UI_ACCESS_FILTER_ENTITLEMENT);
        } else if (UserAccessSearchOptions.SearchTypes.Population.equals(searchType)) {
            filterContexts.put(new UserAccessPopulationFilterContext(), null);
            makeIdentityAttribute = true;
        } else if (UserAccessSearchOptions.SearchTypes.Identity.equals(searchType)) {
            filterContexts.put(new UserAccessIdentityFilterContext(), null);
        }

        List<ListFilterDTO> allFilters = new ArrayList<ListFilterDTO>();
        for (Map.Entry<BaseUserAccessFilterContext, String> filterContext : filterContexts.entrySet()) {
            filterContext.getKey().setSuggestUrl(suggestUrl);
            List<ListFilterDTO> filters = enhanceFilters(
                    new ListFilterService(this.spContext, this.locale, filterContext.getKey()).
                            getListFilters(true), filterContext.getValue(), makeIdentityAttribute);
            if (!Util.isEmpty(filters)) {
                allFilters.addAll(filters);
            }
        }
        
        Collections.sort(allFilters, ListFilterDTO.getComparator());
        return allFilters;
    }

    /**
     * This is a function that loops through query parameters on the GET url and tries to convert the value of that
     * parameter into a fleshed-out value that we can provide to a suggest.  As an example, we could be given
     * something like "filterEntitlementApplication=Active_Directory" and return "applicationIds={The actual application}"
     *
     * @param queryParameters The value of the query parameters on the url
     * @param requestor The person making the request
     * @return A converted map that represents something we can hand to the ui's SearchData object
     * @throws GeneralException
     */
    public Map<String,ListFilterValue> getAccessItemFilterValues(Map<String, String> queryParameters, Identity requestor,
                                                        ManagedAttributeServiceContext context)
        throws GeneralException {

        /* Get the list of available filters so we can iterate over them and get their properties */
        List<ListFilterDTO> allFilters = this.getAccessItemFilters(queryParameters, requestor, null);
        Map<String,ListFilterValue> filterValues = new HashMap<String,ListFilterValue>();
        ListFilterService filterService = new ListFilterService(this.spContext, this.locale, null, this.identitySearchUtil);

        /* Loop through all of the query parameters and let the filter service convert the parameter's value into
        an actual value we can give to the suggests on the ui that work (such as a full application
         */
        for(String param : queryParameters.keySet()) {
            ListFilterDTO filter = filterService.findFilterDTOFromDeepLink(param, allFilters);
            if(filter != null) {
                Object value = filterService.getFilterValueFromDeepLink(filter, queryParameters.get(param), context);
                if (value != null) {
                    filterValues.put(filter.getProperty(), new ListFilterValue(value, null));
                }
            }
        }
        return filterValues;
    }

    /**
     * Get the filters for current access search
     * @param queryParameters Parameters containing values
     * @param requester Identity making the request
     * @return List of ListFilterDTO objects
     * @throws GeneralException
     */
    public List<ListFilterDTO> getCurrentAccessFilters(Map<String, String> queryParameters, Identity requester, String suggestUrl)
    throws GeneralException {
        List<ListFilterDTO> filters = getAccessItemFilters(queryParameters, requester, suggestUrl);
        CurrentAccessFilterContext filterContext = new CurrentAccessFilterContext();
        filterContext.setSuggestUrl(suggestUrl);
        List<ListFilterDTO> currentAccessFilters =
                enhanceFilters(new ListFilterService(this.spContext, this.locale, filterContext).getListFilters(true), null);

        List<ListFilterDTO> allFilters = new ArrayList<ListFilterDTO>();
        if (!Util.isEmpty(currentAccessFilters)) {
            allFilters.addAll(currentAccessFilters);
        }
        if (!Util.isEmpty(filters)) {
            allFilters.addAll(filters);
        }

        return allFilters;
    }

    /**
     * Get a list of search terms based on query parameters
     * @param queryParameters The map containing query parameters
     * @return List of search terms for queries
     */
    public List<String> getSearchTerms(Map<String, String> queryParameters) {
        String queryParam = Util.getString(queryParameters, PARAM_QUERY);
        List<String> queries = new ArrayList<String>();
        if (!Util.isNullOrEmpty(queryParam)) {
            queries = splitQuery(queryParam);
        }
        return queries;
    }

    /**
     * Convert the population statistics from object model to individual row values
     * that classic UI expects
     * @param row Row result. Will be modified. 
     */
    public static void convertPopulationStatistics(Map<String, Object> row) {
        // Convert population statistics to what we expect, if they are there
        if (row != null && row.containsKey(UserAccessSearchColumns.COLUMN_POP_STATS)) {
            UserAccessSearcher.PopulationStatistics statistics = (UserAccessSearcher.PopulationStatistics)row.get(UserAccessSearchColumns.COLUMN_POP_STATS);
            if (statistics != null) {
                row.put(COLUMN_POP, statistics.getCount());
                row.put(COLUMN_POP_TOTAL, statistics.getTotal());
                row.put(COLUMN_HAS_HIGH_RISK, statistics.isHasHighRisk());
            }
            row.remove(UserAccessSearchColumns.COLUMN_POP_STATS);
        }
    }

    /**
     * Get the identity filters from the query parameters. 
     * This will remove the values from the query parameter map
     * 
     * @param queryParameters The map of query parameters. this will be modified to remove any values used to 
     *                        generate identity filters.
     *                        
     * @return Possibly null List of Filter objects
     */
    public List<Filter> getIdentityFilters(Map<String, String> queryParameters) throws GeneralException {
        
        // If called directly, we might not have initialized yet.
        if (this.identitySearchUtil == null) {
            init();
        }
        
        Map<String, String> identityParameters = this.identitySearchUtil.separateIdentityParameters(queryParameters);
        List<Filter> identityFilters = null;
        if (!Util.isEmpty(identityParameters)) {
            identityFilters = new ArrayList<Filter>();
            identityFilters.addAll(new ListFilterService(this.spContext, this.locale, new UserAccessIdentityFilterContext()).
                    convertQueryParametersToFilters(identityParameters, true));
            identityFilters.addAll(new ListFilterService(this.spContext, this.locale, new UserAccessPopulationFilterContext()).
                    convertQueryParametersToFilters(identityParameters, true));
        }
        return identityFilters;
    }
    
    /**
     * Split a query string into component search terms. Quotes are preserved and will
     * surround a single search term with multiple words.
     * @param query String query to split into terms
     * @return List of search terms
     */
    private List<String> splitQuery(String query) {
        if (query == null) {
            return null;
        }

        /* Group quoted terms preserving quotes.  Do not barf on unbalanced
         * quotes. The result of this is that you can search and find "Quote"
         * with "Q in a hibernate search.  The same search with full text
         * enabled will return all results containing Q.  Additionally
         * hibernate search does not support term grouping i.e.
         * "Accounting General" will not find Accounting General in a
         * hibernate search, but will in a full text search. */
        char[] queryChars = query.toCharArray();
        StringBuilder currentToken = new StringBuilder();
        List<String> tokens = new ArrayList<String>();
        boolean inQuote = false;
        for (char queryChar : queryChars) {
            if(queryChar == ' ') {
                if(inQuote) {
                    currentToken.append(queryChar);
                } else {
                    String token = currentToken.toString().trim();
                    if(token.length() > 0) {
                        tokens.add(token);
                    }
                    currentToken = new StringBuilder();
                }
            } else if (queryChar == '\"') {
                if(inQuote) {
                    currentToken.append(queryChar);
                    tokens.add(currentToken.toString());
                    currentToken = new StringBuilder();
                } else {
                    String token = currentToken.toString().trim();
                    if(token.length() > 0) {
                        tokens.add(token);
                    }
                    currentToken = new StringBuilder();
                    currentToken.append(queryChar);
                }
                inQuote = !inQuote;
            } else {
                currentToken.append(queryChar);
            }
        }
        String token = currentToken.toString().trim();
        if(token.length() > 0) {
            tokens.add(token);
        }
        return tokens;
    }

    /**
     * Make changes to ListFilterDTO to line them up with what we expect
     */
    private List<ListFilterDTO> enhanceFilters(List<ListFilterDTO> filterDTOs, String messageKey) {
        return enhanceFilters(filterDTOs, messageKey, false);
    }

    /**
     * Make changes to ListFilterDTO to line them up with what we expect
     */
    private List<ListFilterDTO> enhanceFilters(List<ListFilterDTO> filterDTOs, String messageKey, boolean makeIdentityAttribute) {
        for (ListFilterDTO filterDTO : filterDTOs) {
            // This will add the "q_" or "date_" prefix as necessary.
            filterDTO.setProperty(this.identitySearchUtil.getQueryParameterName(filterDTO, makeIdentityAttribute));
            if (messageKey != null) {
                filterDTO.setLabel(new Message(messageKey, filterDTO.getLabel()).getLocalizedMessage(this.locale, null));
            }
        }
        return filterDTOs;
    }

    /**
     * Get the filters from the query parameters for a given object type. 
     * This will remove the values from the query parameter map
     */
    private List<Filter> getObjectTypeFilters(Map<String, String> queryParameters, ListFilterContext listFilterContext) 
            throws GeneralException {
        return new ListFilterService(this.spContext, this.locale, listFilterContext).
                        convertQueryParametersToFilters(queryParameters, true); 
    }

    /**
     * Get the filters from the query parameters for a given object type. 
     * This will remove the values from the query parameter map
     */
    private ListFilterService getObject(ObjectTypes objectType, Identity requester, Identity target)
            throws GeneralException {
        ListFilterContext listFilterContext = (ObjectTypes.Role.equals(objectType)) ?
                new UserAccessRoleFilterContext(requester, target) :
                // Have to include "value" filter for posterity
                new UserAccessEntitlementFilterContext(requester, target, true);

        return new ListFilterService(this.spContext, this.locale, listFilterContext);
    }

    /**
     * Updates the passed UserAccessSearchOptions to include specified object type
     */
    private void populateSearchOption(ObjectTypes objectType,
                                      List<Filter> filters,
                                      List<Filter> identityFilters,
                                      Map<String, String> queryParameters,
                                      List<String> columns,
                                      UserAccessSearchOptions searchOptions)
            throws GeneralException {
        if (!Util.isEmpty(identityFilters)) {
            searchOptions.includeObjectType(objectType, filters, columns, identityFilters);
            searchOptions.setObjectTypeAttribute(
                    objectType,
                    UserAccessSearchOptions.ObjectTypeOptions.OPTION_POPULATION_MINIMUM,
                    this.lcmConfigService.getPopulationMinimum(ObjectTypes.Role.equals(objectType)));
        } else {
            searchOptions.includeObjectType(objectType, filters, columns);
        }

        if (ObjectTypes.Entitlement.equals(objectType)) {
            String requestableParam = Util.getString(queryParameters, PARAM_REQUESTABLE);
            if (!Util.isNullOrEmpty(requestableParam) && Boolean.parseBoolean(requestableParam) == false) {
                searchOptions.setObjectTypeAttribute(objectType, UserAccessSearchOptions.ObjectTypeOptions.OPTION_INCLUDE_NON_REQUESTABLE_ENTITLEMENTS, true);
            }
        }
    }

    /**
     * Adds columns that must be calculated from the search results.
     * @param results List of search results
     * @param columns Map of ColumnConfig lists keyed by access type
     * @throws GeneralException If localization fails
     */
    private void loadCalculatedColumns(List<Map<String, Object>> results, Map<String, List<ColumnConfig>> columns) throws GeneralException {
        if (!Util.isEmpty(results)) {
            Boolean classificationsEnabled = Configuration.getSystemConfig().getBoolean(Configuration.DISPLAY_CLASSIFICATIONS_IN_ACCESS_REQUEST);
            for (Map<String, Object> result : results) {
                loadCalculatedColumns(result, columns);
                if (classificationsEnabled) {
                    loadClassifications(result);
                }
            }
        }
    }
    
    private void pruneResults(List<Map<String, Object>> results, Identity requester, Identity targetIdentity, String quickLinkName)
    throws GeneralException {
        QuickLinkOptionsConfigService configService = new QuickLinkOptionsConfigService(this.spContext);
        boolean allowRoleShowPopulation = configService.isRequestControlOptionEnabled(requester,
                quickLinkName,
                QuickLink.LCM_ACTION_REQUEST_ACCESS,
                Configuration.LCM_ALLOW_REQUEST_ROLES_SHOW_POPUPLATION,
                isSelfService(requester, targetIdentity));
        
        boolean allowEntitlementShowPopulation = configService.isRequestControlOptionEnabled(requester,
                quickLinkName,
                QuickLink.LCM_ACTION_REQUEST_ACCESS,
                Configuration.LCM_ALLOW_REQUEST_ENTITLEMENTS_SHOW_POPUPLATION,
                isSelfService(requester, targetIdentity));
        
        for (Map<String, Object> result: Util.safeIterable(results)) {
            // Strip out population info if not allowed
            if ((ObjectTypes.Role.equals(getObjectType(result)) && !allowRoleShowPopulation) ||
                    (ObjectTypes.Entitlement.equals(getObjectType(result)) && !allowEntitlementShowPopulation)) {
                result.remove(UserAccessSearchColumns.COLUMN_POP_STATS);
            }
            
            // Since we have converted to accessType, no longer need internal objectType value
            result.remove("objectType");
        }
    }

    /**
     * Adds calculated columns to result map.
     * @param result Single search result
     * @param columns Map of ColumnConfig lists keyed by access type
     * @throws GeneralException If localization fails
     */
    private void loadCalculatedColumns(Map<String, Object> result, Map<String, List<ColumnConfig>> columns) throws GeneralException {
        // Convert object type to access type and localized name
        convertType(result);
        /* Description */
        String description = calculateDescription(result);
        result.put(Localizer.ATTR_DESCRIPTION, description);
        
        // Risk score
        String accessType = (String)result.get(COLUMN_ACCESS_TYPE);
        if (ACCESS_TYPE_ENTITLEMENT.equals(accessType)) {
            String missingRiskColumn = null;
            if (!result.containsKey(COLUMN_RISK_SCORE_WEIGHT) &&
                    hasColumn(COLUMN_RISK_SCORE_WEIGHT, accessType, columns)) {
                missingRiskColumn = COLUMN_RISK_SCORE_WEIGHT;
            } else if (!result.containsKey(COLUMN_RISK_SCORE) &&
                    hasColumn(COLUMN_RISK_SCORE, accessType, columns)) {
                missingRiskColumn = COLUMN_RISK_SCORE;
            }
            
            if (missingRiskColumn != null) {
                result.put(missingRiskColumn, getEntitlementRiskScore(result));
            }
        } 

        // Identity extended attributes on roles and entitlements are stored as the ID.  If there
        // are any of these load the identities' display names.
        loadExtendedIdentities(result, accessType);

        /* Risk Score Color */
        if (result.containsKey(COLUMN_RISK_SCORE_WEIGHT)) {
            int riskScore = Util.getInt(result, COLUMN_RISK_SCORE_WEIGHT);
            result.put(COLUMN_RISK_SCORE_COLOR, WebUtil.getScoreColor(riskScore, false));
            result.put(COLUMN_RISK_SCORE_TEXT_COLOR, WebUtil.getScoreTextColor(riskScore, false));
        }
    }

    /**
     * Load the classification names.
     * @param result
     */
    private void loadClassifications(Map<String, Object> result) throws GeneralException {
        String accessType = (String) result.get(COLUMN_ACCESS_TYPE);
        String id = Util.getString(result, UserAccessSearchColumns.COLUMN_ID);
        ClassificationService classificationService = new ClassificationService(this.spContext);
        List<String> displayNames = classificationService.getClassificationNames(ACCESS_TYPE_ENTITLEMENT.equals(accessType) ? ManagedAttribute.class : Bundle.class, id);
        result.put(COLUMN_CLASSIFICATIONS, displayNames);
    }

    /**
     * Calculates the description for the search results
     * @param result The result to calculate the description from
     * @return description for result
     */
    private String calculateDescription(Map<String, Object> result) throws GeneralException {
        String id = Util.getString(result, UserAccessSearchColumns.COLUMN_ID);
        String description = null;
        /* For role description use Localizer otherwise use Explanator */
        if(getObjectType(result).equals(ObjectTypes.Role)) {
            description = localizer.getLocalizedValue(id, Localizer.ATTR_DESCRIPTION, locale);
        } else {
            String applicationName = Util.getString(result, "application.name");
            if(!Util.isNullOrEmpty(applicationName)) {
                Application application = spContext.getObjectByName(Application.class, applicationName);
                if(application != null) {
                    String attribute = Util.getString(result, "attribute");
                    String value = Util.getString(result, "value");
                    description = Explanator.getDescription(application.getId(), attribute, value, locale);
                }
            }
        }
        if(description == null) {
            description  = "";
        }
        return description;
    }

    /**
     * If the result contains any extended attributes of type identity, replace the identity IDs with
     * display names.
     *
     * @param  result  The result to modify.
     * @param  accessType  The access type for this result - one of the ACCESS_TYPE_* constants.
     */
    private void loadExtendedIdentities(Map<String,Object> result, String accessType)
        throws GeneralException {

        List<String> identityCols = getExtendedIdentityColumns(accessType);

        for (String identityCol : identityCols) {
            Object identityId = result.get(identityCol);
            if (null != identityId) {
                Identity ident = ObjectUtil.getIdentityOrWorkgroup(this.spContext, (String) identityId);
                if (null != ident) {
                    result.put(identityCol, ident.getDisplayableName());
                }
            }
        }
    }

    /**
     * Return the names of the attributes for the given type that are identity extended attributes,
     * or an empty list if there are no identity extended attributes for the requested type.
     */
    private List<String> getExtendedIdentityColumns(String accessType) {
        List<String> identityCols = new ArrayList<String>();

        Class<? extends SailPointObject> clz = null;
        if (ACCESS_TYPE_ENTITLEMENT.equals(accessType)) {
            clz = ManagedAttribute.class;
        }
        else if (ACCESS_TYPE_ROLES.equals(accessType)) {
            clz = Bundle.class;
        }

        if (null != clz) {
            ObjectConfig config = ObjectConfig.getObjectConfig(clz);
            if (null != config) {
                for (ObjectAttribute attr : Util.iterate(config.getObjectAttributes())) {
                    if (attr.isIdentity()) {
                        identityCols.add(attr.getName());
                    }
                }
            }
        }

        return identityCols;
    }

    /**
     * Convert the objectType to accessType for UI layer
     * @param result The result to modify
     */
    private void convertType(Map<String, Object> result) throws GeneralException {
        ObjectTypes objectType = getObjectType(result);
        if (objectType == null) {
            throw new GeneralException("missing objectType in search results");
        }

        result.put(COLUMN_ACCESS_TYPE, objectType.toString());
        result.put("displayableAccessType",
                new Message(getObjectType(result).getMessageKey()).getLocalizedMessage(this.locale, null));
    }

    /**
     * Get the ObjectTypes value from the result map
     * @param result Result map
     * @return ObjectTypes value 
     */
    private ObjectTypes getObjectType(Map<String, Object> result) {
        return  (ObjectTypes) result.get(UserAccessSearchColumns.COLUMN_OBJECT_TYPE);
    }
    
    private boolean isSelfService(Identity requester, Identity target) {
        return (requester != null && target != null && Util.nullSafeEq(requester.getId(), target.getId()));
    }
    
    private Identity getTargetIdentity(Map<String, String> queryParameters) throws GeneralException {
        Identity targetIdentity = null;
        if (queryParameters.containsKey(PARAM_IDENTITY_ID)) {
            targetIdentity = this.spContext.getObjectById(Identity.class, queryParameters.get(PARAM_IDENTITY_ID));
        }
        return targetIdentity;
    }

    private UserAccessSearchOptions.SearchTypes getSearchType(Map<String, String> queryParameters) throws GeneralException {
        // Default to Keyword if not set
        UserAccessSearchOptions.SearchTypes searchType = UserAccessSearchOptions.SearchTypes.Keyword;
        if (queryParameters.containsKey(PARAM_SEARCH_TYPE)) {
            searchType = Enum.valueOf(UserAccessSearchOptions.SearchTypes.class, queryParameters.get(PARAM_SEARCH_TYPE));
        }
        return searchType;
    }

    private String getQuickLinkName(Map<String, String> queryParams) {
        String ql = null;
        if (queryParams != null && queryParams.containsKey(PARAM_QUICK_LINK_NAME)) {
            ql = queryParams.get(PARAM_QUICK_LINK_NAME);
        }
        return ql;
    }
    
    private List<String> getProjectionColumns(List<ColumnConfig> columns) {
        List<String> projectionColumns = new ArrayList<String>();
        for (ColumnConfig column : Util.safeIterable(columns)) {
            if(column.getProperty() != null) {
                projectionColumns.add(column.getProperty());
            }
        }
        return projectionColumns;
    }
    
    private boolean hasColumn(String name, String accessType, Map<String, List<ColumnConfig>> columns) {
        if (name != null && accessType != null && columns != null) {
            List<ColumnConfig> accessColumns = columns.get(accessType);
            for (ColumnConfig column : Util.safeIterable(accessColumns)) {
                if (name.equals(column.getDataIndex()) || name.equals(column.getProperty())) {
                    return true;
                }
            }
        }
        
        return false;
    }

    private int getEntitlementRiskScore(Map<String, Object> entitlementResult) throws GeneralException {
        EntitlementDescriber describer = new EntitlementDescriber();
        return describer.getEntitlementScore(this.spContext,
                (String) entitlementResult.get(UserAccessSearchColumns.COLUMN_APP_NAME),
                (String) entitlementResult.get(UserAccessSearchColumns.COLUMN_ATTRIBUTE),
                (String) entitlementResult.get(UserAccessSearchColumns.COLUMN_VALUE),
                // We might not have type if this came from Current Access search and was not a ManagedAttribute
                entitlementResult.get(UserAccessSearchColumns.COLUMN_TYPE) != null ?
                        entitlementResult.get(UserAccessSearchColumns.COLUMN_TYPE).toString() : null);
    }

    /**
     * Helper method to grab the status filter value off the query params.
     * The "status" parameter is in the form of a FilterValue.
     *
     * @param queryParams The map of query parameters
     * @return The Object value of the status FilterValue
     */
    private Object getStatus(Map<String, String> queryParams) throws GeneralException {
        String status = null;
        Map<String, ListFilterValue> filters = ListFilterService.getFilterValueMap(queryParams);
        if (!Util.isEmpty(filters)) {
            ListFilterValue statusFilter = filters.get(PARAM_STATUS);
            if (statusFilter != null) {
                return statusFilter.getValue();
            }
        }
        return status;
    }
}
