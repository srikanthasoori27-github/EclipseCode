package sailpoint.service.useraccess;

import sailpoint.object.Attributes;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.tools.GeneralException;
import sailpoint.web.messages.MessageKeys;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

import static sailpoint.service.CurrentAccessService.CurrentAccessStatus;

/**
 * Class encapsulating search options for a user access search
 * Used by {@link UserAccessService#search(UserAccessSearchOptions)}
 */
public class UserAccessSearchOptions {

    /**
     * Option holding the search type. See {@link SearchTypes}
     */
    public static final String OPTION_SEARCH_TYPE = "searchType";

    /**
     * Option holding the maximum result count. Total results are capped at this number.
     */
    public static final String OPTION_MAX_RESULT_COUNT = "maxResultCount";

    /**
     * Option holding target identity, if targeting a single identity.
     */
    public static final String OPTION_TARGET_IDENTITY = "identity";

    /**
     * Option holding the identity making the request (i.e. logged in user)
     */
    public static final String OPTION_REQUESTER = "requester";

    /**
     * Locale of requester
     */
    public static final String OPTION_LOCALE = "locale";

    /**
     * Timezone of requester
     */
    public static final String OPTION_TIMEZONE = "timeZone";

    /**
     * Option to not cleanup population results. Useful to defer sorting and such
     * of individual types when doing combined search.
     */
    public static final String OPTION_NO_RESULT_CLEANUP = "noResultCleanup";

    /**
     * Option to only cleanup and return a list limited by the getMaxResultCount() method.
     * Added for bug IIQETN-4215.
     */
    public static final String OPTION_MAXRESULT_CLEANUP_ONLY = "maxResultCleanupOnly";

    public static final String OPTION_STATUS = "status";

    /**
     * Name of the quicklink used to drive the RequestAuthorities.
     */
    public static final String OPTION_QUICKLINK_NAME = "quickLink";

    /**
     * Supported search object types. Currently just Entitlement and Role.
     */
    public static enum ObjectTypes {
        Entitlement(MessageKeys.UI_ACCESS_TYPE_ENTITLEMENT),
        Role(MessageKeys.UI_ACCESS_TYPE_ROLE);
        
        private String messageKey;

        private ObjectTypes(String messageKey) {
            this.messageKey = messageKey;
        }

        public String getMessageKey() {
            return this.messageKey;
        }
    };

    /**
     * Support search types.
     */
    public static enum SearchTypes {

        /**
         * Keyword search uses simple filters to find objects
         */
        Keyword,

        /**
         * Identity and Population search requires Identity joins and special logic
         * to find population information for results
         */
        Identity,
        Population,

        /**
         * Current Access lists roles and/or entitlements owned by a given
         * Identity
         */
        CurrentAccess;

        /**
         * Return true if this is an identity-based search, either Identity or Population
         */
        public boolean isIdentitySearch() {
            return SearchTypes.Identity.equals(this) || SearchTypes.Population.equals(this);
        }
    }
    
    /**
     * Private class to hold all the options related to a single search type
     */
    public static class ObjectTypeOptions {

        /**
         * For Identity population searches, this allows to filter the results to those object
         * above a minimum percentage of population
         */
        public static final String OPTION_POPULATION_MINIMUM = "populationMinimum";

        /**
         * Boolean option to include non-requestable entitlements in search.
         */
        public static final String OPTION_INCLUDE_NON_REQUESTABLE_ENTITLEMENTS = "includeNonRequestableEntitlements";
        
        boolean include;
        List<Filter> filters;
        List<String> columns;
        List<Filter> identityFilters;
        Attributes<String, Object> attributes;
       

        /**
         * Constructor
         * @param include Boolean whether to include this object type or not
         */
        public ObjectTypeOptions(boolean include) {
            this.include = include;
            this.attributes = new Attributes<String, Object>();
        }

        /**
         * Constructor for including an object type.
         * @param include Boolean whether to include this object type 
         * @param filters List of filters for searching this object type
         * @param columns List of columns to return in results for this object type
         */
        public ObjectTypeOptions(boolean include, List<Filter> filters, List<String> columns) {
            this(include);
            this.filters = filters;
            this.columns = columns;
        }

        /**
         * Constructor for including an object type.
         * @param include Boolean whether to include this object type 
         * @param filters List of filters for searching this object type
         * @param identityFilters List of filter to apply to Identity search
         * @param columns List of columns to return in results for this object type
         */
        public ObjectTypeOptions(boolean include, List<Filter> filters, List<String> columns, List<Filter> identityFilters) {
            this(include, filters, columns);
            this.identityFilters = identityFilters;
        }

        /**
         * Get if this object type should be included or not 
         * @return True if include, otherwise false. 
         */
        public boolean isIncluded() {
            return this.include;
        }

        /**
         * Get the filters for this object type
         * @return List of Filter objects, or null
         */
        public List<Filter> getFilters() {
            return this.filters;
        }

        /**
         * Get the columns for this object type
         * @return List of string column names, or null
         */
        public List<String> getColumns() {
            return this.columns;
        }

        /**
         * Get the identity filters for Identity search 
         * @return List of Filter objects, or null
         */
        public List<Filter> getIdentityFilters() {
            return this.identityFilters;
        }

        /**
         * Get the attributes that apply only to this type
         * @return Attributes
         */
        public Attributes<String, Object> getAttributes() {
            return this.attributes;
        }
    }

    private Map<ObjectTypes, ObjectTypeOptions> objectTypeOptionsMap;
    private Attributes<String, Object> attributes;

    private int start;
    private int limit;

    /**
     * Default constructor
     */
    public UserAccessSearchOptions() throws GeneralException {
        this(0, 0);
    }
    
    /**
     * Constructor
     * @param start Integer indicating index to start results at
     * @param limit Integer indicating how many results to include
     */
    public UserAccessSearchOptions(int start, int limit) throws GeneralException {
        if (start < 0) {
            throw new GeneralException("Invalid value for start");
        }
        if (limit < 0) {
            throw new GeneralException("Invalid value for limit");
        }
        
        this.start = start;
        this.limit = limit;
        this.attributes = new Attributes<String, Object>();
        this.objectTypeOptionsMap = new HashMap<ObjectTypes, ObjectTypeOptions>();
        
        // Start with nothing included
        excludeType(ObjectTypes.Entitlement);
        excludeType(ObjectTypes.Role);
    }

    /**
     * Get the starting index
     */
    public int getStart() {
        return this.start;
    }

    /**
     * Set the starting index
     */
    public void setStart(int start) {
        this.start = start;
    }

    /**
     * Get the limit of number of results to query
     */
    public int getLimit() {
        return this.limit;
    }

    /**
     * Set the limit of number of results to query
     */
    public void setLimit(int limit) {
        this.limit = limit;
    }

    /**
     * Set an attribute value for additional options. Use OPTION_* class constants for names.
     * @param name Name of attribute
     * @param value Value of attribute
     */
    public void setAttribute(String name, Object value) {
        this.attributes.put(name, value);
    }

    /**
     * Get the attributes map with additional options.
     */
    public Attributes<String, Object> getAttributes() {
        return this.attributes;
    }

    /**
     * Set the maximum result count
     */
    public void setMaxResultCount(int maxResultCount) {
        setAttribute(OPTION_MAX_RESULT_COUNT, maxResultCount);
    }

    /**
     * Get the maximum result count
     */
    public int getMaxResultCount() {
        return this.attributes.getInt(OPTION_MAX_RESULT_COUNT);
    }

    /**
     * Set the search status. See {@link CurrentAccessStatus}
     */
    public void setStatus(CurrentAccessStatus status) {
        setAttribute(OPTION_STATUS, status);
    }

    /**
     * Get the search status. Defaults to null.
     */
    public CurrentAccessStatus getStatus() {
        //Default to null
        if (this.attributes.containsKey(OPTION_STATUS)) {
            return (CurrentAccessStatus)this.attributes.get(OPTION_STATUS);
        } else {
            return null;
        }
    }

    /**
     * Set the search type. See {@link SearchTypes}
     */
    public void setSearchType(SearchTypes type) {
        setAttribute(OPTION_SEARCH_TYPE, type);
    }

    /**
     * Get the search type. Defaults to Keyword if none is set.
     */
    public SearchTypes getSearchType() {
        //Default to Keyword
        if (this.attributes.containsKey(OPTION_SEARCH_TYPE)) {
            return (SearchTypes)this.attributes.get(OPTION_SEARCH_TYPE);
        } else {
            return SearchTypes.Keyword;
        }
    }

    /**
     * Set the requester for this search.
     */
    public void setRequester(Identity requester) {
        setAttribute(OPTION_REQUESTER, requester);
    }

    /**
     * Get the requester for this search.
     */
    public Identity getRequester() {
        return (Identity)this.attributes.get(OPTION_REQUESTER);
    }

    /**
     * Set the target identity for this search.
     */
    public void setTargetIdentity(Identity target) {
        setAttribute(OPTION_TARGET_IDENTITY, target);
    }

    public void setQuickLink(String ql) {
        setAttribute(OPTION_QUICKLINK_NAME, ql);
    }

    public String getQuickLinkName() {
        return (String)this.attributes.get(OPTION_QUICKLINK_NAME);
    }

    /**
     * Get the target identity for this search.
     */
    public Identity getTargetIdentity() {
        return (Identity)this.attributes.get(OPTION_TARGET_IDENTITY);
    }
    
    public void setLocale(Locale locale) {
        setAttribute(OPTION_LOCALE, locale);
    }
    
    public Locale getLocale() {
        return (Locale)this.attributes.get(OPTION_LOCALE);
    }
    
    public void setTimeZone(TimeZone timeZone) {
        setAttribute(OPTION_TIMEZONE, timeZone);
    }
    
    public TimeZone getTimeZone() {
        return (TimeZone)this.attributes.get(OPTION_TIMEZONE);
    }

    /**
     * Check if this is a combined search (multiple object types included)
     * @return True if combined search, otherwise false
     */
    public boolean isCombinedSearch() {
        return isIncluded(ObjectTypes.Role) && isIncluded(ObjectTypes.Entitlement);
    }


    /**
     * Include a particular type in this search results
     * @param type UserAccessSearchType this applies to
     * @param filters List of filters to apply to only this type 
     * @param columns List of projection columns to return for results of this type 
     */
    public void includeObjectType(ObjectTypes type, List<Filter> filters, List<String> columns) {
        ObjectTypeOptions newOptions = new ObjectTypeOptions(true, filters, columns);
        this.objectTypeOptionsMap.put(type, newOptions);
    }

    /**
     * Include a particular type in this search results. Use this for identity search.
     * @param type UserAccessSearchType this applies to
     * @param filters List of filters to apply to only this type 
     * @param columns List of projection columns to return for results of this type
     * @param identityFilters List of identity filters               
     */
    public void includeObjectType(ObjectTypes type, List<Filter> filters, List<String> columns, List<Filter> identityFilters) {
        ObjectTypeOptions newOptions = new ObjectTypeOptions(true, filters, columns, identityFilters);
        this.objectTypeOptionsMap.put(type, newOptions);
    }

    /**
     * Exclude a particular type from search results. Will wipe out previous filters and columns 
     * set by {@link #includeObjectType(UserAccessSearchOptions.ObjectTypes, java.util.List, java.util.List)}
     * @param type UserAccessSearchType to exclude
     */
    public void excludeType(ObjectTypes type) {
        ObjectTypeOptions newOptions = new ObjectTypeOptions(false);
        this.objectTypeOptionsMap.put(type, newOptions);
    }

    /**
     * Check if this type should be included in search or not
     * @param type UserAccessSearchType to check
     * @return True if type should be included, otherwise false
     */
    public boolean isIncluded(ObjectTypes type) {
        ObjectTypeOptions typeOptions = getObjectTypeOptions(type);
        return (typeOptions != null) ? typeOptions.isIncluded() : false;
    }

    /**
     * Get the options for a particular object type
     * @param type ObjectTypes 
     * @return ObjectTypeOptions
     */
    public ObjectTypeOptions getObjectTypeOptions(ObjectTypes type) {
        if (this.objectTypeOptionsMap.containsKey(type)) {
            return this.objectTypeOptionsMap.get(type);
        }
        return null;
    }

    /**
     * Set an attribute value for a particular object type
     * @param type ObjectTypes to apply this attribute
     * @param key Attribute key
     * @param value Attribute value
     * @throws GeneralException
     */
    public void setObjectTypeAttribute(ObjectTypes type, String key, Object value) throws GeneralException {
        ObjectTypeOptions typeOptions = getObjectTypeOptions(type);
        if (typeOptions == null) {
            throw new GeneralException("type must be included before setting attributes");
        
        }
        
        typeOptions.getAttributes().put(key, value);
    }
}
