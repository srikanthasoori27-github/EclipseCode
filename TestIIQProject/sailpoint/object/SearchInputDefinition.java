/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
/**
 * @author peter.holcomb
 *
 * A model to drive the widgets on the advanced search pages.
 */

package sailpoint.object;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.Filter.BooleanOperation;
import sailpoint.object.Filter.CompositeFilter;
import sailpoint.search.BaseFilterBuilder;
import sailpoint.search.FilterBuilder;
import sailpoint.tools.GeneralException;
import sailpoint.tools.LogicalOperationGroups;
import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;
import sailpoint.web.messages.MessageKeys;


/**
 * @exclude
 */
public class SearchInputDefinition extends SailPointObject implements Comparable<SearchInputDefinition> {
    private static final Log log = LogFactory.getLog(SearchInputDefinition.class);
    
    private static final long serialVersionUID = 8142895583681517532L;
    
    public static final String SUGGEST_TYPE_NONE = "none";

    @XMLClass(xmlname="SearchInputDefinitionInputType")
    public static enum InputType {

        GreaterThanEqual(MessageKeys.SRCH_INPUT_TYPE_GTE),
        LessThanEqual(MessageKeys.SRCH_INPUT_TYPE_LTE),
        GreaterThan(MessageKeys.SRCH_INPUT_TYPE_GT),
        LessThan(MessageKeys.SRCH_INPUT_TYPE_LT),
        Before(MessageKeys.SRCH_INPUT_TYPE_BEFORE),
        After(MessageKeys.SRCH_INPUT_TYPE_AFTER),
        Like(MessageKeys.SRCH_INPUT_TYPE_LIKE),
        Equal(MessageKeys.SRCH_INPUT_TYPE_EQ),
        NotEqual(MessageKeys.SRCH_INPUT_TYPE_NEQ),
        In(MessageKeys.SRCH_INPUT_TYPE_IN),
        ContainsAll(MessageKeys.SRCH_INPUT_TYPE_CONTAINS_ALL),
        Null(MessageKeys.SRCH_INPUT_TYPE_IS_NULL),
        NotNull(MessageKeys.SRCH_INPUT_TYPE_NOT_NULL),
        None(MessageKeys.SRCH_INPUT_TYPE_NONE);

        private String messageKey;

        InputType(String messageKey) {
            this.messageKey = messageKey;
        }

        public String getMessageKey() {
            return messageKey;
        }

    }

    @XMLClass(xmlname="SearchInputDefinitionPropertyType")
    public static enum PropertyType {
        TimePeriodList,
        StringList,
        None,
        OrdList,
        AndedList,
        String,
        Collection,
        Integer,
        Identity,
        Rule,
        Boolean,
        Date;

        /**
         * Convenience method to try to map this property type to a real class. Note that not all PropertyType 
         * values have a class.
         * @return Class
         */
        public Class getTypeClazz() {
          return propertyTypeToClazzMap.get(this);  
        };
        
        private static Map<SearchInputDefinition.PropertyType, Class> propertyTypeToClazzMap;

        static {
            propertyTypeToClazzMap = new HashMap<SearchInputDefinition.PropertyType, Class>();
            propertyTypeToClazzMap.put(PropertyType.Boolean, Boolean.class);
            propertyTypeToClazzMap.put(PropertyType.Date, Date.class);
            propertyTypeToClazzMap.put(PropertyType.Identity, Identity.class);
            propertyTypeToClazzMap.put(PropertyType.Rule, Rule.class);
            propertyTypeToClazzMap.put(PropertyType.Integer, Integer.class);
            propertyTypeToClazzMap.put(PropertyType.String, String.class);
        }
    }
    /** The hibernate property name to be used when performing a search. For example, if you are searching
     * on identities and want to build a filter using the identity's first name, the propertyName would
     * be firstName. **/
    private String propertyName;

    /** The hibernate propery name to be used when performing an is null search. For example, if the propertyName
     * is manager.name, the nullPropertyName should be manager */
    private String nullPropertyName;

    /** A unique name to identify this input definition **/
    private String name;

    /** An object value that is used to set the value of the filter object when being converted into a filter */
    private Object value;

    /** Whether to ignore the case of the value being compared when performing a search **/
    private boolean ignoreCase;

    /** Describes what type of property values will be used in this definition. This is helpful
     * when creating the filter based on the input definition **/
    private PropertyType propertyType;

    /** Describes what type of property values should be used when this definition is entered into
     * a null search */
    private PropertyType nullPropertyType;
    
    /** Indicates what to do if the user enters a list of items for this property. Whether to AND/OR the
     * values **/
    private String listOperation;

    private String description;
    private InputType inputType;
    private Filter.MatchMode matchMode;
    private String searchType;
    private boolean extendedAttribute;
    
    /** Used by the ui to fetch the associated message that goes with this column when it is displayed
     * in the column header
     */
    private String headerKey;
    
    /** Used by the ui to specify the order in which the fields are displayed 
     */
    private String sortIndex;
    
    /** There are times where building a filter out of an input definition can be complex and involve
     * joins or other transformations. The filterBuilder string is the name of the class that performs
     * the building of the actual filter. The builder is the instance that has been instantiated.
     */
    private String filterBuilder;
    private FilterBuilder builder;
    
    /** There are now instances where using a search input definition will require a list of filters that
     * go along with that filter. An example is an input definition that requires a join or joins to the identity
     * in order for the search to work */    
    private List<Filter> requiredFilters;
    
    /** If the search input definition wants to utilize a suggest, specify the type here that correlates with a suggest factory option **/
    private String suggestType;
    
    /** Some searches utilize the list of search inputs to build the 'Fields to Display' column on the search ui 
     * if this boolean is set, the input is excluded from the list of fields to display 
     */
    private boolean excludeDisplayFields;
    
    private int reportColumnWidth;

    /**
     * True to include in the FullTextIndex
     */
    private boolean excludeFullTextFilter;
    
    /** Run-time setting for now, used to set dates */
    private TimeZone timeZone;
    
    public SearchInputDefinition() {
    }

    public SearchInputDefinition (String propertyName) {
        this.propertyName = propertyName;
    }

    @Override
    public boolean isXml() {
        return true;
    }
    
    /** Use this to copy the object into a new object so that when changes are made to the definition in the
     * search, the system's input definitions are not changed.
     * @return
     */
    public SearchInputDefinition copy() {
        SearchInputDefinition newDef = new SearchInputDefinition();
        newDef.setPropertyName(this.propertyName);
        newDef.setNullPropertyName(this.nullPropertyName);
        newDef.setIgnoreCase(this.ignoreCase);
        newDef.setValue(this.value);
        newDef.setNullPropertyType(this.nullPropertyType);
        newDef.setPropertyType(this.propertyType);
        newDef.setListOperation(this.listOperation);
        newDef.setMatchMode(this.matchMode);
        newDef.setDescription(this.description);
        newDef.setInputType(this.inputType);
        newDef.setSearchType(this.searchType);
        newDef.setName(this.name);
        newDef.setRequiredFilters(this.requiredFilters);
        newDef.setFilterBuilder(this.filterBuilder);
        newDef.setHeaderKey(this.headerKey);
        newDef.setExtendedAttribute(this.extendedAttribute);
        newDef.setSuggestType(this.suggestType);
        newDef.setReportColumnWidth(this.reportColumnWidth);
        newDef.setExcludeDisplayFields(this.excludeDisplayFields);
        return newDef;
    }

    public Filter getFilter(Resolver resolver)
        throws GeneralException {
    
        return getFilter(resolver, null);
    }
    
    @SuppressWarnings("unchecked")
    public Filter getFilter(Resolver r, Class<?> scope) throws GeneralException{
        Filter filter = null;   
        
        /** For input definitions that have a property type of None, null filters are returned
         * these definitions act as helpers and not as filters
         */
        if(this.getPropertyType() == null || this.getPropertyType().equals(PropertyType.None))
            return null;
        
        getBuilder(r, scope).setDefinition(this);
        
        if(value instanceof java.util.List) {

            /** Handle Lists with InputType set to "In" or "Contains All" */
            if (getInputType() == InputType.In || getInputType() == InputType.ContainsAll) {
                filter = builder.getFilter();
            }
            
            else {
                List<Object> values = (List<Object>)value;
                List<Filter> filters = new ArrayList<Filter>();
                for(Object val : values) {
                    builder.setValue(val);
                    Filter currentFilter = builder.getFilter();
                    if (currentFilter != null)
                        filters.add(currentFilter);
                }
                
                if (!filters.isEmpty()) {
                    if (filters.size() == 1) {
                        filter = filters.get(0);
                    } else {
                        /** Figure out what to do with the list of items **/
                        if(getListOperation()!=null && getListOperation().equals("OR"))
                        {
                            filter = new CompositeFilter(BooleanOperation.OR, filters);
                        } else {
                            filter = new CompositeFilter(BooleanOperation.AND, filters);
                        }
                    }
                }            	
            }
        } else {
            filter= builder.getFilter();
        }
        return filter;
    }
    
    public Filter getJoin(Resolver r) throws GeneralException {
        getBuilder(r).setDefinition(this);
        return builder.getJoin();
    }


    /**
     * @return the description
     */
    @XMLProperty
    public String getDescription() {
        return description;
    }

    /**
     * @param description the description to set
     */
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * @return the name
     */
    @XMLProperty
    public String getName() {
        return name;
    }

    /**
     * @param name the name to set
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * @return the type
     */
    @XMLProperty
    public InputType getInputType() {
        return inputType;
    }

    /**
     * @param iType the type to set
     */
    public void setInputType(InputType iType) {
        inputType = iType;
    }

    @XMLProperty
    public Filter.MatchMode getMatchMode() {
        return matchMode;
    }

    public void setMatchMode(Filter.MatchMode matchMode) {
        this.matchMode = matchMode;
    }

    /** set the type value based on a string **/
    public void setInputTypeValue(String iType) {
        for(InputType t : InputType.values()) {
            if(t.name().equals(iType)) {
                this.inputType = t;
                break;
            }
        }
    }

    public String getInputTypeValue() {
        return inputType.name();
    }

    /**
     * @return the value
     */
    @XMLProperty
    public Object getValue() {
        return value;
    }

    @SuppressWarnings("rawtypes")
    public void setObjectListValue(List value) {
        this.value = value;
    }

    @SuppressWarnings("rawtypes")
    public List getObjectListValue() {
        return (List)value;
    }

    public void setObjectDateValue(Date date) {
        this.value = date;
    }

    public Date getObjectDateValue() {
        if(value==null)
        {
            Calendar cal = Calendar.getInstance();
            value = cal.getTime();
        }
        return (Date)value;
    }

    public void setObjectIdentityValue(Identity ident) {
        this.value = ident;
    }

    public Identity getObjectIdentityValue() {
        return (Identity)value;
    }

    /**
     * @param value the value to set
     */
    public void setValue(Object value) {
        this.value = value;
    }

    @XMLProperty
    public boolean isIgnoreCase() {
        return ignoreCase;
    }

    public void setIgnoreCase(boolean ignoreCase) {
        this.ignoreCase = ignoreCase;
    }

    /**
     * @return the propertyName
     */
    @XMLProperty
    public String getPropertyName() {
        return propertyName;
    }

    /**
     * @param propertyName the propertyName to set
     */
    public void setPropertyName(String propertyName) {
        this.propertyName = propertyName;
    }

    /**
     * @return the propertyType
     */
    @XMLProperty
    public PropertyType getPropertyType() {
        return propertyType;
    }

    /**
     * @param propertyType the propertyType to set
     */
    public void setPropertyType(PropertyType propertyType) {
        this.propertyType = propertyType;
    }

    /**
     * @return the propertyType
     */
    @XMLProperty
    public PropertyType getNullPropertyType() {
        return nullPropertyType;
    }

    /**
     * @param nullPropertyType the propertyType to set
     */
    public void setNullPropertyType(PropertyType nullPropertyType) {
        this.nullPropertyType = nullPropertyType;
    }
    /**
     * @return the searchType
     */
    @XMLProperty
    public String getSearchType() {
        return searchType;
    }

    /**
     * @param searchType the searchType to set
     */
    public void setSearchType(String searchType) {
        this.searchType = searchType;
    }

    /**
     * @return the extendedAttribute
     */
    @XMLProperty
    public boolean isExtendedAttribute() {
        return extendedAttribute;
    }

    /**
     * @param extendedAttribute the extendedAttribute to set
     */
    public void setExtendedAttribute(boolean extendedAttribute) {
        this.extendedAttribute = extendedAttribute;
    }

    private static Map<Filter.LogicalOperation, InputType> typeMap = new HashMap<Filter.LogicalOperation, InputType>();
    private static Map<InputType, Filter.LogicalOperation> opMap = new HashMap<InputType, Filter.LogicalOperation>();

    static {
        typeMap.put(Filter.LogicalOperation.GE, InputType.GreaterThanEqual);
        opMap.put(InputType.GreaterThanEqual, Filter.LogicalOperation.GE);
        typeMap.put(Filter.LogicalOperation.LE, InputType.LessThanEqual);
        opMap.put(InputType.LessThanEqual, Filter.LogicalOperation.LE);
        typeMap.put(Filter.LogicalOperation.GT, InputType.GreaterThan);
        opMap.put(InputType.GreaterThan, Filter.LogicalOperation.GT);
        typeMap.put(Filter.LogicalOperation.LT, InputType.LessThan);
        opMap.put(InputType.LessThan, Filter.LogicalOperation.LT);
        typeMap.put(Filter.LogicalOperation.EQ, InputType.Equal);
        opMap.put(InputType.Equal, Filter.LogicalOperation.EQ);
        typeMap.put(Filter.LogicalOperation.NE, InputType.NotEqual);
        opMap.put(InputType.NotEqual, Filter.LogicalOperation.NE);
        typeMap.put(Filter.LogicalOperation.IN, InputType.In);
        opMap.put(InputType.In, Filter.LogicalOperation.IN);
        typeMap.put(Filter.LogicalOperation.LIKE, InputType.Like);
        opMap.put(InputType.Like, Filter.LogicalOperation.LIKE);
        typeMap.put(Filter.LogicalOperation.CONTAINS_ALL, InputType.ContainsAll);
        opMap.put(InputType.ContainsAll, Filter.LogicalOperation.CONTAINS_ALL);
        typeMap.put(Filter.LogicalOperation.ISNULL, InputType.Null);
        opMap.put(InputType.Null, Filter.LogicalOperation.ISNULL);
        typeMap.put(Filter.LogicalOperation.NOTNULL, InputType.NotNull);
        opMap.put(InputType.NotNull, Filter.LogicalOperation.NOTNULL);
        typeMap.put(Filter.LogicalOperation.ISEMPTY, InputType.None);
        opMap.put(InputType.None, Filter.LogicalOperation.ISEMPTY);
    }

    /**
     * Gets LogicalOperations allowed for this attribute, based on the type and
     * whether or not the attribute is multi-valued.
     *
     * @return List of LogicalOperations allowed for this attribute.
     */
    public List<Filter.LogicalOperation> getAllowedOperations(){
        if (propertyType == PropertyType.TimePeriodList || propertyType == PropertyType.StringList) {
            return LogicalOperationGroups.MultiValuedOps;

        } else if (propertyType == PropertyType.OrdList || propertyType == PropertyType.AndedList 
                || propertyType == PropertyType.Collection) {
            return LogicalOperationGroups.BooleanOps;

        } else if (propertyType == PropertyType.Integer || propertyType == PropertyType.Date) {
            return LogicalOperationGroups.NumericalOps;

        } else if (propertyType == PropertyType.String) {
            return LogicalOperationGroups.StringOps;

        } else if(propertyType == PropertyType.Boolean) {
            return LogicalOperationGroups.EqualOp;
        }

        else {
            return LogicalOperationGroups.StandardOps;
        }
    }  // getAllowedOperations()

    public PropertyInfo getPropertyInfo() {
        final PropertyInfo retval = new PropertyInfo();
        retval.setAllowedOperations(getAllowedOperations());
        retval.setDescription(getDescription());
        return retval;
    }

    public static InputType getInputType(Filter.LogicalOperation op) {
        return typeMap.get(op);
    }

    /**
     * Find the SearchInputDefinition based on the name
     * @param config Configuration object with SearchInputDefinitions
     * @param name Name of the SearchInputDefiniton
     * @param copy If true, return a copy of the SearchInputDefinition. Used
     *             in case the caller will need to modify it.
     * @return SearchInputDefinition
     * @throws GeneralException
     */
    @SuppressWarnings("unchecked")
    public static SearchInputDefinition getInputByName(Configuration config, String name, boolean copy) 
            throws GeneralException {
        
        List<SearchInputDefinition> inputDefinitions = (List<SearchInputDefinition>)
                config.get(Configuration.SEARCH_INPUT_DEFINITIONS);
        for (SearchInputDefinition input : inputDefinitions) {
            if (input.getName().equals(name)) {
                return (copy) ? input.copy() : input;
            }
        }
        
        return null;
    }

    /**
     * @return the nullPropertyName
     */
    @XMLProperty
    public String getNullPropertyName() {
        return nullPropertyName;
    }

    /**
     * @param nullPropertyName the nullPropertyName to set
     */
    public void setNullPropertyName(String nullPropertyName) {
        this.nullPropertyName = nullPropertyName;
    }

    /**
     * @return the requiredFilters
     */
    @XMLProperty(mode=SerializationMode.LIST)
    public List<Filter> getRequiredFilters() {
        return requiredFilters;
    }

    /**
     * @param requiredFilters the requiredFilters to set
     */
    public void setRequiredFilters(List<Filter> requiredFilters) {
        this.requiredFilters = requiredFilters;
    }

    @XMLProperty
    public String getFilterBuilder() {
        return filterBuilder;
    }

    public void setFilterBuilder(String filterBuilder) {
        this.filterBuilder = filterBuilder;
    }

    public FilterBuilder getBuilder(Resolver r) {
        return getBuilder(r, null);
    }
    
    public FilterBuilder getBuilder(Resolver r, Class<?> scope) {
        if(builder==null) {
             if(filterBuilder==null) {
                 builder = new BaseFilterBuilder(this, r);        	
             } else {
                 try {
                     Class<?> cls = Class.forName(filterBuilder);
                     builder = (FilterBuilder)cls.newInstance();
                     
                     builder.setDefinition(this);
                 } catch (Exception e) {
                     builder = new BaseFilterBuilder(this, r);
                     
                     log.warn(e.getMessage(), e);        		
                 }
             }
         }
        builder.setResolver(r);
        builder.setScope(scope);
        return builder;
    }

    @XMLProperty
    public String getHeaderKey() {
        if(headerKey==null) {
            return getName();
        }
        return headerKey;
    }

    public void setHeaderKey(String headerKey) {
        this.headerKey = headerKey;
    }

    @XMLProperty
    public String getSortIndex() {
        return this.sortIndex;
    }

    public void setSortIndex(String index) {
        this.sortIndex = index;
    }

    @XMLProperty
    public String getListOperation() {
        return listOperation;
    }

    public void setListOperation(String listOperation) {
        this.listOperation = listOperation;
    }
    
    @XMLProperty
    public String getSuggestType() {
        return suggestType;
    }

    public void setSuggestType(String suggestType) {
        this.suggestType = suggestType;
    }

    @XMLProperty
    public boolean isExcludeDisplayFields() {
        return excludeDisplayFields;
    }

    public void setExcludeDisplayFields(boolean excludeDisplayFields) {
        this.excludeDisplayFields = excludeDisplayFields;
    }

    @XMLProperty
    public int getReportColumnWidth() {
        return reportColumnWidth;
    }

    public void setReportColumnWidth(int reportColumnWidth) {
        this.reportColumnWidth = reportColumnWidth;
    }
    
    public TimeZone getTimeZone() {
        return timeZone;
    }
    
    public void setTimeZone(TimeZone timeZone) {
        this.timeZone = timeZone;
    }

    @XMLProperty
    public boolean isExcludeFullTextFilter() { return excludeFullTextFilter; }

    public void setExcludeFullTextFilter(boolean b) { this.excludeFullTextFilter = b; }

    /**
     * Allows SearchInputDefinitions to be sorted according to the value of their
     * sortIndex attributes. If either definition has a null sortIndex, return
     * 0 (equal) so that no further sorting is attempted. Likewise, return 0 if
     * a NumberFormatException is thrown while trying to convert the sortIndex
     * into an Integer.
     */
    public int compareTo(SearchInputDefinition def) {
        // don't try to sort if one or the other definitions being compared
        // has a null sortIndex
        if ((this.sortIndex == null) || (def.getSortIndex() == null)) {
            return 0;	        
        }
        else {
            try {
                Integer int1 = new Integer(this.sortIndex);
                Integer int2 = new Integer(def.getSortIndex());
                return int1.compareTo(int2);
            } catch (NumberFormatException e) {
                return 0;
            }
        }	        
    }
}
