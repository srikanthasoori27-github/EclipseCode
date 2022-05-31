package sailpoint.service.listfilter;

import sailpoint.api.SailPointContext;
import sailpoint.object.Application;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.ManagedAttribute;
import sailpoint.object.ObjectAttribute;
import sailpoint.object.ObjectConfig;
import sailpoint.service.suggest.SuggestHelper;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Base implementation of a ListFilterContext with some default behaviors. Should
 * not be instantiated
 */
public abstract class BaseListFilterContext implements ListFilterContext {

    private static final String EXTERNAL_ATTRIBUTE_NAME = "attributeName";
    private static final String EXTERNAL_ATTRIBUTE_VALUE = "value";
    private static final String EXTERNAL_ATTRIBUTE_ID = "objectId";

    /**
     * ObjectConfig for this list
     */
    private ObjectConfig objectConfig;

    /**
     * String URL for the suggest endpoint, will be configured on the filters with suggests.
     */
    private String suggestUrl;

    /**
     * Constructor
     * @param objectConfig ObjectConfig (possibly null) to use for this list
     */
    public BaseListFilterContext(ObjectConfig objectConfig) {
        this.objectConfig = objectConfig;
    }

    /**
     * ObjectConfig for this list
     */
    protected ObjectConfig getObjectConfig() {
        return objectConfig;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<ListFilterDTO> getObjectAttributeFilters(SailPointContext context, Locale locale) throws GeneralException {
        List<ObjectAttribute> attributes = getFilterObjectAttributes(context);
        List<ListFilterDTO> filterDTOs = new ArrayList<ListFilterDTO>();
        for (ObjectAttribute attribute : Util.safeIterable(attributes)) {
            filterDTOs.add(createListFilterDTO(attribute, locale));
        }
        return filterDTOs;
    }

    /**
     * Get the ObjectAttributes that should be filters
     * @param context SailPointContext
     * @return List of ObjectAttributes
     * @throws GeneralException
     */
    protected List<ObjectAttribute> getFilterObjectAttributes(SailPointContext context) throws GeneralException {
        List<ObjectAttribute> attributes = (this.objectConfig != null) ?
                new ArrayList<>(this.objectConfig.getSearchableAttributes()) :
                new ArrayList<ObjectAttribute>();

        if (includeMultiAttributes()) {
            attributes.addAll(new ArrayList<>(this.objectConfig.getMultiAttributeList()));
        }

        return attributes;
    }

    /**
     * Create a ListFilterDTO from an ObjectAttribute. Subclasses can override this to do custom things
     * like configuring a suggest. 
     * @param attribute ObjectAttribute
     * @param locale Locale
     * @return ListFilterDTO initialized correctly
     */
    protected ListFilterDTO createListFilterDTO(ObjectAttribute attribute, Locale locale) {
        ListFilterDTO filterDTO = new ListFilterDTO(attribute, locale, getSuggestUrl());
        // These attributes use external attribute table, so set the table name to be used later.
        if (attribute.isMulti()) {
            filterDTO.setAttribute(ListFilterDTO.ATTR_EXTERNAL_ATTRIBUTE_TABLE, getExternalAttributeTable());
        }

        if (ListFilterDTO.DataTypes.Number.equals(filterDTO.getDataType()) ||
                ListFilterDTO.DataTypes.Date.equals(filterDTO.getDataType())) {
            // Equals is always allowed, and non-equality filters for numbers and dates.
            filterDTO.addAllowedOperation(ListFilterValue.Operation.NotEquals, locale);
            filterDTO.addAllowedOperation(ListFilterValue.Operation.LessThan, locale);
            filterDTO.addAllowedOperation(ListFilterValue.Operation.GreaterThan, locale);
        }
        return filterDTO;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<ListFilterDTO> getDefaultFilters(SailPointContext context, Locale locale) throws GeneralException {
        return new ArrayList<ListFilterDTO>();
    }

    /**
     * {@inheritDoc}
     */
    @Override 
    public boolean isEnabled(SailPointContext context) throws GeneralException {
        return true;
    }

    public String getSuggestUrl() {
        return this.suggestUrl;
    }

    public void setSuggestUrl(String suggestUrl) {
        this.suggestUrl = suggestUrl;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Filter convertFilter(ListFilterDTO filterDTO, ListFilterValue filterValue, SailPointContext context) throws GeneralException {
        Filter filter;
        if (filterDTO.isMultiValued()) {
            List<Filter> subFilters = new ArrayList<Filter>();
            List<String> allValues = getValueStringList(filterValue);
            for (String singleValue : allValues) {
                subFilters.add(convertFilterSingleValue(filterDTO, singleValue, filterValue.getOperation(), context));
            }
            if (Util.size(subFilters) == 1) {
                filter = subFilters.get(0);
            } else {
                filter = ListFilterValue.Operation.NotEquals.equals(filterValue.getOperation()) ? Filter.and(subFilters) : Filter.or(subFilters);
            }
        } else {
            filter = convertFilterSingleValue(filterDTO, getValueString(filterValue), filterValue.getOperation(), context);
        }

        filter = checkExternalAttributeFilter(filter, filterDTO);

        return filter;
    }

    /**
     * Convert a single value to a single filter. Useful in case of multivalues properties
     */
    protected Filter convertFilterSingleValue(ListFilterDTO filterDTO, String value, ListFilterValue.Operation operation, SailPointContext context) throws GeneralException {
        Filter filter;
        String property = checkExternalAttributeProperty(filterDTO);

        if (filterDTO.hasAllowedValues()) {
            // Should be exact match with allowed values
            filter = getSimpleFilter(property, value, operation);
        } else {
            switch (filterDTO.getDataType()) {
                case Identity:
                    // IIQTC-259: Identity filtered using a exact word, avoiding the 'starts with' retrieval
                    filter = getIdentityFilter(property, value, operation);
                    break;
                case Boolean:
                    filter = getBooleanFilter(property, Util.otob(value), operation);
                    break;
                case Date:
                    filter = getDateFilter(property, value, operation);
                    break;
                case DateRange:
                    // Ignore operation, does not make any sense with date range filters
                    filter = getDateRangeFilter(property, value);
                    break;
                case Number:
                    filter = getSimpleFilter(property, Util.otoi(value), operation);
                    break;
                case Column:
                    filter = getColumnFilter(property, value, operation);
                    break;
                default:
                    filter = getDefaultFilter(property, value, operation, getMatchMode(context));
                    break;
            }
        }

        return filter;
    }

    /**
     * Create a filter for an Identity based filter value. 
     * Supported Operations: Equals, NotEquals
     * @param property Property name
     * @param value Value of the filter
     * @param operation Operation
     * @return Filter object, or null if operation is not supported
     */
    protected Filter getIdentityFilter(String property, String value, ListFilterValue.Operation operation) {
        // Filter on name
        // IIQTC-259: Identity filtered using a exact word, avoiding the 'starts with' retrieval
        Filter nameFilter = null;
        String propertyName = property + ".name";

        if (Util.nullSafeEq(operation, ListFilterValue.Operation.Equals)) {
            nameFilter = Filter.eq(propertyName, value);
        } else if (Util.nullSafeEq(operation, ListFilterValue.Operation.NotEquals)) {
            nameFilter = Filter.ne(propertyName, value);
        }

        return nameFilter;
    }

    /**
     * Create a filter for an boolean based filter value. 
     * Supported Operations: Equals, NotEquals
     * @param property Property name
     * @param value Value of the filter
     * @param operation Operation
     * @return Filter object, or null if operation is not supported
     */
    protected Filter getBooleanFilter(String property, Boolean value, ListFilterValue.Operation operation) {
        Filter filter = null;
        switch (operation) {
            case Equals:
                filter = Filter.eq(property, value);
                break;
            case NotEquals:
                filter = Filter.ne(property, value);
                break;
        }
        
        return filter;
    }

    private Date getDate(String dateString) {
        return Util.isNothing(dateString) ? null : new Date(Util.atol(dateString));
    }

    /**
     * Create a filter for a Date based filter value. 
     * Supported Operations: Equals, NotEquals, LessThan, GreaterThan
     * @param property Property name
     * @param value The original string value, which is in "pipe-separated" format
     * @param operation Operation
     * @return Filter object, or null if operation is not supported
     */
    protected Filter getDateFilter(String property, String value, ListFilterValue.Operation operation) {

        // Special Case! Between operation renders as a date range, so process that correctly.
        if (ListFilterValue.Operation.Between.equals(operation)) {
            return getDateRangeFilter(property, value);
        }

        // Date filters are formatted as "startlong|endlong" which correspond
        // to 00:00:00 and 23:59:59 of the selected date.
        String[] dates = value.split("\\|", 2);
        Date start = getDate(dates[0]);
        Date end = getDate(dates[1]);
        Filter filter = null;
        switch (operation) {
            case Equals:
                filter = Filter.and(Filter.ge(property, start), Filter.le(property, end));
                break;
            case NotEquals:
                filter = Filter.or(Filter.lt(property, start), Filter.gt(property, end));
                break;
            case LessThan:
                filter = Filter.lt(property, start);
                break;
            case GreaterThan:
                filter = Filter.gt(property, end);
                break;
        }
        
        return filter;
    }

    /**
     * Create a filter for a DateRange based filter value.
     * @param property Property name
     * @param value The original string value, which is in "pipe-separated" format
     * @return Filter object
     */
    protected Filter getDateRangeFilter(String property, String value) {
        Filter filter;

        // DateRange filters are formatted as "lowStart|lowEnd|highStart|highEnd" which correspond
        // to 00:00:00 and 23:59:59 of the selected low and high dates defining the range.
        String[] dates = value.split("\\|", 4);
        Date lowStart = getDate(dates[0]);
        Date highStart = getDate(dates[2]);
        Date highEnd = getDate(dates[3]);
        if (lowStart == null && highStart != null) {
            // Only end date set
            filter = Filter.le(property, highEnd);
        } else if (lowStart != null && highStart == null) {
            // Only start date set
            filter = Filter.ge(property, lowStart);
        } else {
            // Otherwise its a "between"
            filter = Filter.and(Filter.ge(property, lowStart), Filter.le(property, highEnd));
        }

        return filter;
    }

    /**
     * Create a simple filter for a generic value. 
     * Supported Operations: Equals, NotEquals, LessThan, GreaterThan
     * @param property Property name
     * @param value Value of the filter
     * @param operation Operation
     * @return Filter object, or null if operation is not supported
     */
    protected Filter getSimpleFilter(String property, Object value, ListFilterValue.Operation operation) {
        Filter filter = null;
        switch (operation) {
            case Equals:
                filter = Filter.eq(property, value);
                break;
            case NotEquals:
                filter = Filter.ne(property, value);
                break;
            case LessThan:
                filter = Filter.lt(property, value);
                break;
            case GreaterThan:
                filter = Filter.gt(property, value);
                break;
        }
        
        return filter;
    }

    /**
     * Create a filter for a default data type. 
     * Supported Operations: Equals, NotEquals
     * @param property Property name
     * @param value Value of the filter
     * @param operation Operation
     * @param matchMode System level match mode
     * @return Filter object, or null if operation is not supported
     */
    protected Filter getDefaultFilter(String property, String value, ListFilterValue.Operation operation, Filter.MatchMode matchMode) {
        Filter filter = null;
        switch (operation) {
            case Equals: 
                filter = Filter.ignoreCase(Filter.like(property, value, matchMode));
                break;
            case NotEquals:
                filter = Filter.ignoreCase(Filter.not(Filter.like(property, value, matchMode)));
                break;
        }
        return filter;
    }

    /**
     * Create a filter for a Column type.
     * Supported operations: Equals, NotEquals, StartsWith
     * @param property Property name
     * @param value Value of the filter
     * @param operation Operation
     * @return Filter object
     */
    protected Filter getColumnFilter(String property, String value, ListFilterValue.Operation operation) {
        Filter filter = null;
        switch (operation) {
            case Equals:
            case NotEquals:
                filter = getSimpleFilter(property, value, operation);
                break;
            case StartsWith:
                filter = Filter.ignoreCase(Filter.like(property, value, Filter.MatchMode.START));
                break;
        }

        return filter;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Filter convertFilter(String propertyName, ListFilterValue value, SailPointContext context) throws GeneralException {
        return null;
    }

    /**
     * Get the MatchMode to use for filter.likes
     */
    protected Filter.MatchMode getMatchMode(SailPointContext context) throws GeneralException {
        return Filter.MatchMode.START;
    }

    @Override
    public List<Filter> convertDeepLinksFilters(Map<String, ListFilterValue> filterValueMap, SailPointContext context) throws GeneralException {
        return Collections.emptyList();
    }

    @Override
    public boolean hasDeepLinksParams(Map<String, String> queryParameters) throws GeneralException {
        return false;
    }
    
    @Override
    public Comparator<ListFilterDTO> getFilterComparator(Locale locale) {
        return ListFilterDTO.getComparator();
    }

    @Override
    public Comparator<ListFilterDTO> getFilterLabelComparator(Locale locale) {
        return ListFilterDTO.getLabelComparator(locale);
    }

    @Override
    public List<Filter> postProcessFilters(List<Filter> filters) throws GeneralException {
        // By default this does nothing.
        return filters;
    }

    /**
     * @return the value of FilterValue as a string 
     */
    protected String getValueString(ListFilterValue filterValue) {
        return getValueString(filterValue, "id");
    }

    protected String getValueString(ListFilterValue filterValue, String mapKey) {
        if (filterValue == null || filterValue.getValue() == null) {
            return null;
        }

        if (filterValue.getValue() instanceof List) {
            List<String> stringList = new ArrayList<>();
            for (Object val : Util.iterate((List)filterValue.getValue())) {
                stringList.add(getSingleStringValue(val, mapKey));
            }
            return Util.listToCsv(stringList);
        } else {
            return getSingleStringValue(filterValue.getValue(), mapKey);
        }
    }

    /**
     * @return the value of FilterValue as a List of strings
     */
    @SuppressWarnings("unchecked")
    protected List<String> getValueStringList(ListFilterValue filterValue) {
        if (filterValue == null || filterValue.getValue() == null) {
            return null;
        }

        if (filterValue.getValue() instanceof List) {
            List<String> newList = new ArrayList<String>();
            for (Object val : Util.iterate((List)filterValue.getValue())) {
                newList.add(getSingleStringValue(val));
            }
            return newList;
        } else {
            return Util.csvToList(getSingleStringValue(filterValue.getValue()));
        }
    }

    /**
     * Gets the string value of a value inside a ListFilterValue.
     * Handles the case of a Map, which can happen when the ListFilterValue is not
     * transformed client side, for example in certification schedule filters.
     *
     * For now assumes the value is in the "id" property of the map, may be expanded later as necessary.
     * @param fullValue Value in the ListFilterValue
     * @return String value
     */
    protected String getSingleStringValue(Object fullValue) {
        return getSingleStringValue(fullValue, "id");
    }

    /**
     * Gets the string value of a value inside a ListFilterValue.
     * Handles the case of a Map, which can happen when the ListFilterValue is not
     * transformed client side, for example in certification schedule filters.
     *
     * @param fullValue Value in the ListFilterValue
     * @param mapKey The key of the identifier in the map
     * @return String value
     */
    protected String getSingleStringValue(Object fullValue, String mapKey) {
        if (fullValue instanceof Map) {
            Object mapValue = ((Map)fullValue).get(mapKey);
            return (mapValue == null) ? null : mapValue.toString();
        } else {
            return fullValue.toString();
        }
    }

    /**
     * Convert the value(s) in the FilterValue to a list of objects matching the type of the property.
     * NOTE: CSV list of values is NOT supported by this method.
     * @param filterValue FilterValue object
     * @param propertyName Name of the property, chained properties are supported
     * @param clazz Class of the object being queried
     * @return List of objects, converted to correct type.
     * @throws GeneralException
     */
    protected List<Object> getValueList(ListFilterValue filterValue, String propertyName, Class clazz) throws GeneralException {
        if (filterValue == null || filterValue.getValue() == null) {
            return null;
        }

        List valueList = Util.asList(filterValue.getValue());
        List<Object> castList = new ArrayList<>();
        for (Object valueObject : valueList) {
            castList.add(Util.convertValue(clazz, propertyName, valueObject));
        }
        
        return castList;
    }

    /**
     * @return String name of the class for the external attribute table. Useful only for Identity and Link, which
     * are where "multi" attributes are supported.
     */
    protected String getExternalAttributeTable() {
        return null;
    }

    /**
     * @return True to return "multi" attributes in the object attribute, false to not. Sub classes should override
     * to include multi attributes.
     */
    protected boolean includeMultiAttributes() {
        return false;
    }

    /**
     * Helper method to configure a column suggest. Has special case handling to use external attribute table when neccesary
     */
    protected void configureColumnSuggest(ListFilterDTO filterDTO, String suggestClass, String suggestColumn, String filterString) {
        if (filterDTO.isMultiExternalAttribute()) {
            suggestClass = (String)filterDTO.getAttribute(ListFilterDTO.ATTR_EXTERNAL_ATTRIBUTE_TABLE);
            filterString = Filter.eq(EXTERNAL_ATTRIBUTE_NAME, suggestColumn).toString();
            suggestColumn = EXTERNAL_ATTRIBUTE_VALUE;
        }

        filterDTO.configureColumnSuggest(suggestClass, suggestColumn, filterString, getSuggestUrl());
    }

    /**
     * Checks if the filter is an external "multi" filter, and if so add the necessary join and attribute name filters
     * @param filter The constructed filter
     * @param filterDTO The filter DTO
     * @return The combined filter, if necessary, or the original filter
     */
    protected Filter checkExternalAttributeFilter(Filter filter, ListFilterDTO filterDTO) {
        if (filterDTO.isMultiExternalAttribute()) {
            return Filter.and(
                    Filter.join("id", filterDTO.getAttribute(ListFilterDTO.ATTR_EXTERNAL_ATTRIBUTE_TABLE) + "." + EXTERNAL_ATTRIBUTE_ID),
                    Filter.eq(filterDTO.getAttribute(ListFilterDTO.ATTR_EXTERNAL_ATTRIBUTE_TABLE) + "." + EXTERNAL_ATTRIBUTE_NAME, filterDTO.getProperty()),
                    filter
            );
        }

        return filter;
    }

    /**
     * Checks if the filter is an external "multi" filter, and if so use the external attribute table for the property
     * @param filterDTO The filter
     * @return The property to filter on, either the filter property or special case for external attribute
     */
    protected String checkExternalAttributeProperty(ListFilterDTO filterDTO) {
        if (filterDTO.isMultiExternalAttribute()) {
            return filterDTO.getAttribute(ListFilterDTO.ATTR_EXTERNAL_ATTRIBUTE_TABLE) + "." + EXTERNAL_ATTRIBUTE_VALUE;
        }

        return filterDTO.getProperty();
    }

    @Override
    public void collapseFilterValue(ListFilterDTO listFilterDTO, ListFilterValue filterValue) {
        if (!isSuggest(listFilterDTO, filterValue)) {
            return;
        }

        // We want to store the name when possible, but for column suggest and some others that is stored in 'id'. Confusing, I know.
        String valueProperty = "name";
        if (ListFilterDTO.DataTypes.Column.equals(listFilterDTO.getDataType()) ||
                ListFilterDTO.DataTypes.Attribute.equals(listFilterDTO.getDataType())) {
            valueProperty = "id";
        }

        filterValue.setValue(getValueString(filterValue, valueProperty));
    }

    @Override
    public void expandFilterValue(ListFilterDTO listFilterDTO, ListFilterValue filterValue, SailPointContext context, Locale locale) throws GeneralException {
        if (!isSuggest(listFilterDTO, filterValue)) {
            return;
        }
        if (listFilterDTO.isMultiValued()) {
            List<String> valueList = getValueStringList(filterValue);
            List<Map<String, Object>> suggestValueList = new ArrayList<>();
            for (String id : Util.iterate(valueList)) {
                suggestValueList.add(getSuggestObject(listFilterDTO, id, context, locale));
            }
            filterValue.setValue(suggestValueList);
        } else {
            String id = getValueString(filterValue);
            filterValue.setValue(getSuggestObject(listFilterDTO, id, context, locale));
        }

    }

    /**
     * Create a map representation of a suggest object for the given filter value
     * @param listFilterDTO ListFilterDTO for the value
     * @param id String identifier, can be name or id.
     * @param context SailPointContext
     * @param locale Locale
     * @return Map suggest object, or null if clazz cannot be determined.
     * @throws GeneralException
     */
    protected Map<String, Object> getSuggestObject(ListFilterDTO listFilterDTO, String id, SailPointContext context, Locale locale) throws GeneralException {
        Class clazz = null;
        Map<String, Object> suggestResult = null;
        if (listFilterDTO != null) {
            if (ListFilterDTO.DataTypes.Identity.equals(listFilterDTO.getDataType())) {
                clazz = Identity.class;
            } else if (ListFilterDTO.DataTypes.Application.equals(listFilterDTO.getDataType())) {
                clazz = Application.class;
            } else if (ListFilterDTO.DataTypes.Attribute.equals(listFilterDTO.getDataType())) {
                clazz = ManagedAttribute.class;
            } else if (!Util.isNothing((String)listFilterDTO.getAttribute(ListFilterDTO.ATTR_SUGGEST_CLASS))) {
                try {
                    clazz = Util.getSailPointObjectClass((String) listFilterDTO.getAttribute(ListFilterDTO.ATTR_SUGGEST_CLASS));
                } catch (Exception ex) {
                    throw new GeneralException(ex);
                }
            }
        }

        if (clazz == null) {
            return null;
        }

        String column = (String)listFilterDTO.getAttribute(ListFilterDTO.ATTR_SUGGEST_COLUMN);
        if (!Util.isNothing(column)) {
            suggestResult = SuggestHelper.getSuggestColumnValue(clazz, column, id, context, locale);
        } else {
            suggestResult = SuggestHelper.getSuggestObject(clazz, id, context);
        }

        return suggestResult;
    }

    /**
     * Helper method to determine if this is normally using a suggest for its value.
     *
     */
    protected boolean isSuggest(ListFilterDTO listFilterDTO, ListFilterValue listFilterValue) {
        return (ListFilterDTO.DataTypes.Identity.equals(listFilterDTO.getDataType()) ||
                ListFilterDTO.DataTypes.Application.equals(listFilterDTO.getDataType()) ||
                ListFilterDTO.DataTypes.Attribute.equals(listFilterDTO.getDataType()) ||
                !Util.isNothing((String)listFilterDTO.getAttribute(ListFilterDTO.ATTR_SUGGEST_CLASS))) &&
                (ListFilterValue.Operation.Equals.equals(listFilterValue.getOperation()) ||
                        ListFilterValue.Operation.NotEquals.equals(listFilterValue.getOperation()));
    }
}