package sailpoint.service.listfilter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import sailpoint.api.SailPointContext;
import sailpoint.integration.ListResult;
import sailpoint.object.Application;
import sailpoint.object.Attributes;
import sailpoint.object.Certification;
import sailpoint.object.CertificationEntity;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.QueryOptions;
import sailpoint.rest.IdentitySearchUtil;
import sailpoint.service.BaseListResourceColumnSelector;
import sailpoint.service.ManagedAttributeService;
import sailpoint.service.ManagedAttributeServiceContext;
import sailpoint.service.suggest.SuggestHelper;
import sailpoint.tools.GeneralException;
import sailpoint.tools.JsonHelper;
import sailpoint.tools.Util;

/**
 * Service to help with filters for list resources and services
 */
public class ListFilterService {
    private SailPointContext context;
    private Locale locale;
    private ListFilterContext filterContext;
    private IdentitySearchUtil identityUtil;

    /* Allows us to map filter parameters on the GET request to the actual properties they represent */
    private static final Map<String, String> SUPPORTED_FILTER_PARAMS;
    private static final List<String> SUPPORTED_FILTER_PREFIXES;
    public static final String PARAM_FILTER_PREFIX = "filter";
    public static final String PARAM_FILTER_ENTITLEMENT_PREFIX = "filterEntitlement";
    public static final String PARAM_FILTER_ROLE_PREFIX = "filterRole";
    public static final String PARAM_FILTER_POPULATION_PREFIX = "filterPopulation";
    public static final String MANAGED_ATTRIBUTE_TYPE_WITH_APP = "managedAttributeAttrTypeWithApp";
    static {
        SUPPORTED_FILTER_PARAMS = new HashMap<String, String>();
        SUPPORTED_FILTER_PARAMS.put("filterRoleType", "type");
        SUPPORTED_FILTER_PARAMS.put("filterEntitlementApplication", "applicationIds");
        SUPPORTED_FILTER_PARAMS.put("filterEntitlementAttribute", "attributes");
        SUPPORTED_FILTER_PARAMS.put("filterEntitlementOwner", "ownerId");
        SUPPORTED_FILTER_PARAMS.put("filterIdentity", "identityIds");

        SUPPORTED_FILTER_PREFIXES = Arrays.asList(
                PARAM_FILTER_ENTITLEMENT_PREFIX, 
                PARAM_FILTER_ROLE_PREFIX, 
                PARAM_FILTER_POPULATION_PREFIX);
    }

    /**
     * Constructor.
     * @param context SailPointContext
     * @param locale Locale
     * @param filterContext Instantiated ListFilterContext with specialized logic
     */
    public ListFilterService(SailPointContext context, Locale locale, ListFilterContext filterContext) {
        this.context = context;
        this.locale = locale;
        this.filterContext = filterContext;
    }

    /**
     * Constructor
     * @param context SailPointContext
     * @param locale Locale
     * @param filterContext Instantiated ListFilterContext with specialized logic
     * @param identityUtil Instantiated IdentitySearchUtil
     */
    public ListFilterService(SailPointContext context, Locale locale, ListFilterContext filterContext, IdentitySearchUtil identityUtil) {
        this(context, locale, filterContext);
        this.identityUtil = identityUtil;
    }

    /**
     * Get a list of ListFilterDTO based on the ListFilterContext.
     * @param includeDefaults True to include "default" filters along with extended attributes. False to exclude
     * @return List of ListFilterDTO objects
     * @throws GeneralException
     */
    public List<ListFilterDTO> getListFilters(boolean includeDefaults) throws GeneralException {
        List<ListFilterDTO> filterDTOs = new ArrayList<ListFilterDTO>();
        if (this.filterContext.isEnabled(this.context)) {
            if (includeDefaults) {
                List<ListFilterDTO> defaultFilters = this.filterContext.getDefaultFilters(this.context, this.locale);
                if (!Util.isEmpty(defaultFilters)) {
                    filterDTOs.addAll(defaultFilters);
                }
            }

            List<ListFilterDTO> attributeFilters = this.filterContext.getObjectAttributeFilters(this.context, this.locale);
            if (!Util.isEmpty(attributeFilters)) {
                filterDTOs.addAll(attributeFilters);
            }
        }
        return sortFilterList(filterDTOs);
    }

    /**
     * Get a list of ListFilterDTO based on the ListFilterContext.
     * This method is an overloading of getListFilters(boolean includeDefaults)
     * @return List of ListFilterDTO objects ordered by label
     * @throws GeneralException
     */
    public List<ListFilterDTO> getListFilters() throws GeneralException {
        List<ListFilterDTO> filterDTOs = getListFilters(true);
        return sortFilterListByLabel(filterDTOs);
    }

    /**
     * Convert a map of query parameters to a list of Filters. This will pull any parameters
     * that match a FilterDTO out of the parameter map and convert it to a Filter object.
     * @param queryParameters Map of query parameters. Will be modified as we remove any parameters that are converted.
     * @param includeDefaults True to include "default" filters along with extended attribute. False to exclude.
     * @return List of Filter objects
     * @throws GeneralException
     */
    public List<Filter> convertQueryParametersToFilters(Map<String, String> queryParameters, boolean includeDefaults)
            throws GeneralException {
        List<Filter> filters = new ArrayList<Filter>();

        // First convert the default filters and extended attribute filters.
        Map<String, ListFilterDTO> filterDTOMap = getFilterDTOMap(includeDefaults);
        Map<String, ListFilterValue> filterValueMap = getFilterValueMap(queryParameters);
        Iterator<Map.Entry<String, ListFilterValue>> filterValueIterator = filterValueMap.entrySet().iterator();
        while (filterValueIterator.hasNext()) {
            Map.Entry<String, ListFilterValue> filterValueEntry = filterValueIterator.next();
            String property = filterValueEntry.getKey();
            ListFilterValue filterValue = filterValueEntry.getValue();
            Filter filter;
            if (filterDTOMap.containsKey(property)) {
                filter = this.filterContext.convertFilter(filterDTOMap.get(property), filterValue, this.context);
            } else {
                filter = this.filterContext.convertFilter(property, filterValue, this.context);
            }
            if (filter != null) {
                filters.add(filter);
                filterValueIterator.remove();
            }
        }

        // Then convert any deep links parameters into filters (in case this is a deep links request).
        List<Filter> deepLinksFilters = this.filterContext.convertDeepLinksFilters(filterValueMap, this.context);
        if (deepLinksFilters != null) {
            filters.addAll(deepLinksFilters);
        }
        
        //Remove anything not left in the filter value map from the query parameter map
        Iterator<Map.Entry<String, String>> queryParameterIterator = queryParameters.entrySet().iterator();
        while (queryParameterIterator.hasNext()) {
            Map.Entry<String, String> queryParameter = queryParameterIterator.next();
            if (!filterValueMap.containsKey(queryParameter.getKey())) {
                queryParameterIterator.remove();
            }
        }

        // Delegate any final post-processing (this usually won't do anything but some things like CertificationEntity
        // searches need a little extra love).
        filters = this.filterContext.postProcessFilters(filters);

        return filters;
    }

    /**
     * Convert a list of ListFilterValue to a single filter that ANDs them all together.
     * All ListFilterValues should have the property defined.
     * @param filterValues List of ListFilterValues
     * @return Filter with all the converted filters ANDed together.
     * @throws GeneralException
     */
    public Filter getFilter(List<ListFilterValue> filterValues) throws GeneralException {
        List<Filter> filters = new ArrayList<>();
        Map<String, ListFilterDTO> filterDTOMap = getFilterDTOMap(true);
        List<ListFilterValue> filterValuesCopy = new ArrayList<>();

        // Clone and collapse to get the right values from suggests without altering originals
        for (ListFilterValue listFilterValue : Util.iterate(filterValues)) {
            filterValuesCopy.add(new ListFilterValue(listFilterValue));
        }

        collapseFilterValues(filterValuesCopy);

        for (ListFilterValue filterValue : Util.iterate(filterValuesCopy)) {
            Filter filter;
            String property = filterValue.getProperty();
            if (filterDTOMap.containsKey(property)) {
                filter = this.filterContext.convertFilter(filterDTOMap.get(property), filterValue, this.context);
            } else {
                filter = this.filterContext.convertFilter(property, filterValue, this.context);
            }
            if (filter != null) {
                filters.add(filter);
            }
        }

        // Delegate any final post-processing
        filters = this.filterContext.postProcessFilters(filters);

        if (Util.size(filters) == 1) {
            return filters.get(0);
        } else {
            return Util.isEmpty(filters) ? null : Filter.and(filters);
        }
    }

    /**
     * Get a Map of ListFilterDTOs keyed by property
     */
    private Map<String, ListFilterDTO> getFilterDTOMap(boolean includeDefaults) throws GeneralException {
        // First convert the default filters and extended attribute filters.
        List<ListFilterDTO> filterDTOs = getListFilters(includeDefaults);
        Map<String, ListFilterDTO> filterDTOMap = new HashMap<String, ListFilterDTO>();
        for (ListFilterDTO filterDTO : Util.safeIterable(filterDTOs)) {
            filterDTOMap.put(filterDTO.getProperty(), filterDTO);
        }

        return filterDTOMap;
    }

    /**
     * Responsible for taking a request parameter and finding the ListFilterDTO that corresponds to it.
     * It does this by comparing the request parameter to the property of the filter after first doing any
     * necessary conversion on the request parameter to match it up (all request parameters that we are interested
     * in will start with "filter"
     * @param param The request parameter we are looking for
     * @param filters The list of available ListFilterDTO objects
     * @return ListFilterDTO The object we are trying to find
     */
    public ListFilterDTO findFilterDTOFromDeepLink(String param, List<ListFilterDTO> filters) {
        if (param.startsWith(PARAM_FILTER_PREFIX)) {
            for (ListFilterDTO filter : filters) {

                /* If this is a standard (default) filter, just convert it from the supported filter params map */
                String property = SUPPORTED_FILTER_PARAMS.get(param);
                if (property != null && filter.getProperty().equals(property)) {
                    return filter;
                }

                /* Convert the filter property from q_XXX or date_XXX to just XXX */
                if (this.identityUtil != null) {
                    property = this.identityUtil.getFilterPropertyName(filter.getProperty(), true).toLowerCase();
                    for (String prefix : SUPPORTED_FILTER_PREFIXES) {
                        if (param.startsWith(prefix)) {
                            String shortenedParam = param.substring(prefix.length()).toLowerCase();
                            if (shortenedParam.equals(property)) {
                                return filter;
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    /**
     * This returns fleshed-out object for the property that is passed-in on the deep link.  The property
     * will be sent down to either the suggest helpers or other services that work with the suggest
     * components in order to expand properties on it.
     * @param filter The ListFilterDTO object that corresponds to this request parameter
     * @param parameterValue The value of the request parameter
     * @param context The manage attribute context we use to search for managed attributes
     * @return Object the expanded object (could be an application, identity, etc...) that corresponds to the parameter
     * @throws GeneralException
     */
    public Object getFilterValueFromDeepLink(ListFilterDTO filter, String parameterValue,
                                             ManagedAttributeServiceContext context)
            throws GeneralException {
        /* Convert the parameterValue into an array if it is csv */
        List<String> parameterValues = Util.csvToList(parameterValue);
        List<Object> values = new ArrayList<Object>();

        for(String val : parameterValues) {
            Object convertedValue = null;

            /* Handle application style */
            if (filter.getDataType().equals(ListFilterDTO.DataTypes.Application)) {
                QueryOptions ops = new QueryOptions();
                ops.add(Filter.or(Filter.eq("id", val), Filter.eq("name", val)));
                convertedValue = getSuggestValue(ops, Application.class);
            }
            /* Handle identity */
            else if (filter.getDataType().equals(ListFilterDTO.DataTypes.Identity)) {
                QueryOptions ops = new QueryOptions();
                /* Support id or name */
                ops.add(Filter.or(Filter.eq("id", val), Filter.eq("name", val)));
                convertedValue = getSuggestValue(ops, Identity.class);
            }
            /* Handle attributes */
            else if (filter.getDataType().equals(ListFilterDTO.DataTypes.Attribute)) {
                ManagedAttributeService service = new ManagedAttributeService(this.context, context,
                        new BaseListResourceColumnSelector(MANAGED_ATTRIBUTE_TYPE_WITH_APP));
                // IIQTC-138: Changing the call to the method 'getRequestableQueryOptions', as
                // it requires the 'start' value. It is hardcoded with a '0' to avoid any
                // different behavior compared with the one it used to have.
                QueryOptions qo = service.getRequestableQueryOptions(val, 0, 1, null);
                ListResult result = service.getResults(qo, false);
                if(result.getCount()>0) {
                    convertedValue = result.getObjects().get(0);
                }
            } else if(filter.getDataType().equals(ListFilterDTO.DataTypes.Column)) {
                convertedValue = SuggestHelper.getSuggestColumnValue(
                        Identity.class, filter.getProperty(), val, this.context);
            } else {
                /* If the filter has allowed values, try to match it based on string comparison to lower case */
                if(filter.hasAllowedValues()) {
                    for(ListFilterDTO.SelectItem allowedValue : filter.getAllowedValues()) {
                        if(allowedValue.getId()!=null &&
                                allowedValue.getId().toString().toLowerCase().equals(val.toLowerCase())) {
                            val = allowedValue.getId().toString();
                        }
                    }
                }

                /* Need to cast these to their proper types or angular won't see them as boolean/int values
                when it tries to set the filters on the ui
                 */
                if(filter.getDataType().equals(ListFilterDTO.DataTypes.Number)) {
                    values.add(new Integer(val));
                } else if(filter.getDataType().equals(ListFilterDTO.DataTypes.Boolean)) {
                    values.add(new Boolean(val));
                } else {
                    values.add(val);
                }
            }

            if(convertedValue!=null) {
                values.add(convertedValue);
            }
        }

        /* If there was no values found, just return null */
        if(values.size()<1) {
            return null;
        }

        /* If the filter is multi-valued, return the entire list, otherwise, just return the first value */
        return filter.isMultiValued() ? values : values.get(0);
    }

    /**
     * Utility function for calling down to the suggest helper to return the expanded value for the
     * provided queryoptions
     * @param ops QueryOptions that will allow us to find the value
     * @param spClass The Classname of the sailpoint object we are searching for
     * @return Object The value we are looking for (usually a sailpoint object or scaled-down version of one)
     * @throws GeneralException
     */
    public Object getSuggestValue(QueryOptions ops, Class spClass)
        throws GeneralException{
        List<Map<String, Object>> results = SuggestHelper.getSuggestResults(spClass, ops, this.context);
        if (Util.isEmpty(results)) {
            return null;
        } else {
            return results.get(0);
        }
    }

    /**
     * Sort the filter list to move all "default" and "standard" attributes to the top
     */
    private List<ListFilterDTO> sortFilterList(List<ListFilterDTO> filterDTOs) {
        Collections.sort(filterDTOs, this.filterContext.getFilterComparator(this.locale));

        return filterDTOs;
    }

    /**
     * Sort the filter list in natural order by label
     */
    private List<ListFilterDTO> sortFilterListByLabel(List<ListFilterDTO> filterDTOs) {
        Collections.sort(filterDTOs, this.filterContext.getFilterLabelComparator(locale));

        return filterDTOs;
    }

    /**
     * Convert the query parameter map into a map of ListFilterValue objects, keyed by same property,
     * which will iterate in same order as query parameter map
     */
    public static Map<String, ListFilterValue> getFilterValueMap(Map<String, String> queryParameters) throws GeneralException {
        // Keep the order the same as query parameters
        Map<String, ListFilterValue> filterValueMap = new LinkedHashMap<String, ListFilterValue>();
        for (Map.Entry<String, String> queryParameter : queryParameters.entrySet()) {
            ListFilterValue filterValue;
            String parameterValue = queryParameter.getValue();
            Map<String, Object> maps = null;
            Pattern objectPattern = Pattern.compile("\\{(.*)\\}");
            Matcher matcher = objectPattern.matcher(parameterValue);
            if (matcher.matches()) {
                maps = JsonHelper.mapFromJson(String.class, Object.class, matcher.group());
                filterValue = new ListFilterValue(maps);
            } else if (SmellsMapLike(parameterValue)) {
                maps = stringToMap(parameterValue);
                filterValue = new ListFilterValue(maps);
            } else {
                filterValue = new ListFilterValue(parameterValue, null);
            }
            filterValueMap.put(queryParameter.getKey(), filterValue);
        }

        return filterValueMap;
    }

    static private boolean SmellsMapLike(String value){
    	Object map = stringToMap(value);

		if (map != null && map instanceof Map){
			Map mp = (Map)map;
			if(!mp.isEmpty()) {
				return true;
			}
		}
    	return false;
    }
    
    public static Map<String, Object> stringToMap(String value){
        Map<String, Object> map = new HashMap<>();

        if (null != Util.getString(value)) {
        	String modValue = value;
        	
        	//first lets trim off some stuff
        	if (modValue.substring(0, 1).equals("\"")) {
        		modValue = modValue.substring(1, modValue.length() - 1);
        	}
        	
        	if (modValue.substring(0, 1).equals("{")) {
        		modValue = modValue.substring(1, modValue.length() - 1);
        	}
        	
        	//Now split it up into map pairs
        	String[] parts = modValue.split(", ");
        	
        	for (int i=0; i<parts.length; i++)
            {
        		String[] part = null;
        		if (parts[i].contains("{")) {
        			//this part contains a JSON object and we need to deal with that
        			part = parts[i].trim().split("=\\{");
        			if (null != part[1]) {
        				part[1] = "{" + part[1];
        			}
        		} else {
        			part = parts[i].trim().split("=");
        		}
                if (part.length == 2 && part[0].length() > 0 && part[1].length() > 0) {
                    map.put(part[0], part[1]);
                }
        	}
        }
    	return map;
    }

    /**
     * Helper method to get real ListFilterValues from the map stored in the attributes
     */
    public static List<ListFilterValue> getFilterValues(Map<String, Object> attributes, String key) {
        Attributes<String, Object> attrs = new Attributes<>(attributes);
        return getFilterValues(attrs, key);
    }

    /**
     * Helper method to get real ListFilterValues from the map stored in the attributes
     */
    @SuppressWarnings("unchecked")
    public static List<ListFilterValue> getFilterValues(Attributes<String, Object> attributes, String key) {
        List filterValuesMaps = attributes.getList(key);
        List<ListFilterValue> filterValues = new ArrayList<>();
        for (Object filterValueMap : Util.iterate(filterValuesMaps)) {
            if (filterValueMap instanceof Map) {
                filterValues.add(new ListFilterValue((Map<String, Object>) filterValueMap));
            } else {
                filterValues.add((ListFilterValue)filterValueMap);
            }
        }

        return filterValues;
    }

    /**
     * Get the correct filter implementing the query string based on the cert type.
     * @param query query string
     * @return the filter
     */
    public Filter getFilterByQuery(Certification.Type certificationType, String query) {
        /* Role Comp is not an identity type, but it still needs to be searched by targetDisplayName */
        if (certificationType.isObjectType() && !certificationType.getEntityType().equals(CertificationEntity.Type.BusinessRole)) {
            return Filter.ignoreCase(Filter.like("ManagedAttribute.displayableName", query, Filter.MatchMode.START));
        }
        else {
            return (Filter.or(Filter.ignoreCase(Filter.like("targetDisplayName", query, Filter.MatchMode.START)),
                    Filter.ignoreCase(Filter.like("firstname", query, Filter.MatchMode.START)),
                    Filter.ignoreCase(Filter.like("lastname", query, Filter.MatchMode.START))));
        }
    }

    /**
     * Collapse the values in the ListFilterValues to their string identifiers. This is useful in the values
     * can come from the client as suggest objects, but we dont want to handle those.
     * @param filterValues The ListFilterValues
     * @throws GeneralException
     */
    public void collapseFilterValues(List<ListFilterValue> filterValues) throws GeneralException {
        Map<String, ListFilterDTO> filterDTOMap = getFilterDTOMap(true);
        for (ListFilterValue filterValue : Util.iterate(filterValues)) {
            if (filterDTOMap.containsKey(filterValue.getProperty())) {
                this.filterContext.collapseFilterValue(filterDTOMap.get(filterValue.getProperty()), filterValue);
            }
        }

    }

    /**
     * Expand the values in the ListFilterValues from their string identifiers to suggest objects.
     * This is useful in the values can come from the client as suggest objects, but we dont want to handle those.
     * @param filterValues The ListFilterValues
     * @throws GeneralException
     */
    public void expandFilterValues(List<ListFilterValue> filterValues) throws GeneralException {
        Map<String, ListFilterDTO> filterDTOMap = getFilterDTOMap(true);
        for (ListFilterValue filterValue : Util.iterate(filterValues)) {
            if (filterDTOMap.containsKey(filterValue.getProperty())) {
                this.filterContext.expandFilterValue(filterDTOMap.get(filterValue.getProperty()), filterValue, this.context, this.locale);
            }
        }
    }
}