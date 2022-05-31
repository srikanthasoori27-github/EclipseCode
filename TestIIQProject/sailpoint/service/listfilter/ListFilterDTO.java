package sailpoint.service.listfilter;

import sailpoint.object.IdentityFilter;
import sailpoint.object.ObjectAttribute;
import sailpoint.object.SearchInputDefinition;
import sailpoint.tools.Message;
import sailpoint.tools.MessageKeyHolder;
import sailpoint.tools.Util;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Representation of a Filter
 */
public class ListFilterDTO {

    /**
     * Supported data types for search filters
     */
    public static enum DataTypes {
        String(SearchInputDefinition.PropertyType.String),
        Number(SearchInputDefinition.PropertyType.Integer),
        Boolean(SearchInputDefinition.PropertyType.Boolean),
        Date(SearchInputDefinition.PropertyType.Date),
        DateRange(null),
        Identity(SearchInputDefinition.PropertyType.Identity),
        // Map these to Strings since classic UI doesn't handle these specially
        Application(SearchInputDefinition.PropertyType.String),
        Attribute(SearchInputDefinition.PropertyType.String),
        // Column is for suggests that use the column value
        Column(SearchInputDefinition.PropertyType.String),
        // Object is for suggests that use object value. In a perfect world we would have introduced this earlier
        // and not had Application type (and maybe others) but leave those for now.
        // Class of the object should be set using ATTR_SUGGEST_CLASS.
        Object(SearchInputDefinition.PropertyType.String);

        private SearchInputDefinition.PropertyType propertyType;

        DataTypes(SearchInputDefinition.PropertyType propertyType) {
            this.propertyType = propertyType;
        }

        /**
         * Get the corresponding PropertyType for legacy code
         * @return PropertyType
         */
        public SearchInputDefinition.PropertyType getPropertyType() {
            return this.propertyType;
        }

        /**
         * Get the DataTypes value that corresponds to the given PropertyType value
         * @param propertyType PropertyType to match
         * @return DataTypes value. Defaults to String.
         */
        public static DataTypes getDataType(SearchInputDefinition.PropertyType propertyType) {
            for (DataTypes type: DataTypes.values()) {
                if (Util.nullSafeEq(type.getPropertyType(), propertyType)) {
                    return type;
                }
            }

            // Default to String
            return DataTypes.String;
        }
    }

    /**
     * A SelectItem is an item with an id and a displayName that can be selected in
     * a field with allowed values.
     */
    public static class SelectItem {

        // The displayName for the item - may be a message key.
        private String displayName;

        // The id for the item.
        private Object id;

        /**
         * Constructor with just the id, which is also used as a label.
         */
        public SelectItem(Object value) {
            this(value.toString(), value);
        }

        /**
         * Constructor with value that implements MessageKeyHolder, useful for enums.
         * @param value MessageKeyHolder value
         */
        public SelectItem(MessageKeyHolder value, Locale locale) {
            this(new Message(value.getMessageKey()).getLocalizedMessage(locale, null), value);
        }
        
        /**
         * Constructor with a value and a label.
         *
         * @param displayName  The displayName for the item - may be a message key.
         * @param id  The value for the item.
         */
        public SelectItem(String displayName, Object id) {
            this.displayName = displayName;
            this.id = id;
        }

        public String getDisplayName() {
            return this.displayName;
        }

        public Object getId() {
            return this.id;
        }
    }


    /**
     * The name of the attribute in the attributes map that contains the
     * suggest context if the filter is a suggest.
     */
    public static final String ATTR_SUGGEST_CONTEXT = "suggestContext";

    /**
     * The name of the attribute in the attributes map that contains the
     * suggest ID if the filter is a suggest.
     */
    public static final String ATTR_SUGGEST_ID = "suggestId";

    /**
     * The name of the attribute with a boolean if the column suggest is LCM or not
     */
    public static final String ATTR_SUGGEST_IS_LCM = "isLcm";

    /**
     * The name of the attributes that contains the LCM Action (Quick Link) to use for scoping
     */
    public static final String ATTR_SUGGEST_LCM_ACTION = "lcmAction";
    
    /**
     * The name of the attributes that contains the name of LCM Application
     */
    public static final String ATTR_SUGGEST_LCM_APPLICATION = "lcmApplication";

    /**
     * The name of the attribute that contains the name of the QuickLink to use for scoping
     */
    public static final String ATTR_SUGGEST_LCM_QUICKLINK = "lcmQuicklink";

    /**
     * The name of the class. This is used both for the suggest, and also for the class for Object types.
     * Its a little misnamed now but I dont want to have to set this in multiple places
     */
    public static final String ATTR_SUGGEST_CLASS = "suggestClass";

    /**
     * The name of the column 
     */
    public static final String ATTR_SUGGEST_COLUMN = "suggestColumn";

    /**
     * "ASC" or "DESC" -- the direction of the sorting
     */
    public static final String ATTR_SUGGEST_DIRECTION = "dir";

    /**
     * The target identity in case of lcm so we can match useraccess filters for our column values."
     */
    public static final String ATTR_SUGGEST_TARGET_ID = "targetIdentity";

    /**
     * The filterString to include in the suggest
     */
    public static final String ATTR_SUGGEST_FILTER_STRING = "filterString";

    /**
     * The relative URL to the suggest resource to use when looking up suggest results
     */
    public static final String ATTR_SUGGEST_URL = "suggestUrl";

    /**
     * Holds a map with category information, such as "label" (group name) and "index" (ordering)
     */
    public static final String ATTR_CATEGORY = "category";
    
    public static final String CATEGORY_LABEL = "label";
    public static final String CATEGORY_INDEX = "index";

    /**
     * Name of the external attribute table object, i.e. IdentityExternalAttribute
     * This will serve as a flag for this being source from a "multi" object attribute
     * where the value is stored in the external table and needs special handling.
     */
    public static final String ATTR_EXTERNAL_ATTRIBUTE_TABLE = "externalAttributeTable";

    /**
     * The name of the property being searched.
     */
    private String property;

    /**
     * The message key for the label to display when rendering the filter.
     */
    private String label;

    /**
     * Whether the filter allows searching for multiple values.
     */
    private boolean multiValued;

    /**
     * The type of filter to display
     */
    private DataTypes dataType;

    /**
     *  An optional array of allowed values that can be displayed in a drop-down when rendering filters.
     */
    private List<SelectItem> allowedValues;

    /**
     * Marks if this is a "standard" attribute or not, more important! 
     */
    private boolean standard;

    /**
     * Marks if this is a default filter or not.
     */
    private boolean isDefault;

    /**
     * Additional attributes that describe how this filter works.  These typically
     * use the ATTR_* constants as the keys.
     */
    private Map<String,Object> attributes;

    /**
     * Allowed ListFilterValue.Operations for this filter value. Stored as SelectItems for the UI dropdown.
     */
    private List<SelectItem> allowedOperations;

    /**
     * True if the filter is additional (not a top-level filter, which is determined by the filter context)
     * or false otherwise
     */
    private boolean additional;

    /**
     * True if the filter value is editable, meaning the value can be typed in or a suggest item.
     */
    private boolean editable;

    /**
     * Private base constructor.
     */
    private ListFilterDTO() {
        this.allowedOperations = new ArrayList<SelectItem>();
        this.allowedOperations.add(new SelectItem(ListFilterValue.Operation.Equals.getMessageKey(), ListFilterValue.Operation.Equals));
    }

    /**
     * Constructor based on ObjectAttribute                     
     */
    public ListFilterDTO(ObjectAttribute objectAttribute, Locale locale, String suggestUrl) {
        this();
        
        this.property = objectAttribute.getName();
        this.label = objectAttribute.getDisplayableName(locale);
        if (!Util.isEmpty(objectAttribute.getAllowedValues())) {
            this.allowedValues = new ArrayList<SelectItem>();
            for (Object value : objectAttribute.getAllowedValues()) {
                this.allowedValues.add(new SelectItem(value));
            }
        }
        this.dataType = DataTypes.getDataType(objectAttribute.getPropertyType());
        this.standard = objectAttribute.isStandard();
        this.isDefault = false;

        // If this is an identity filter, configure the suggest.
        if (DataTypes.Identity.equals(this.dataType)) {
            String context = IdentityFilter.GLOBAL_FILTER;
            // Manager filter
            if (this.property.equalsIgnoreCase("manager")) {
                context = "Manager";
            }
            this.configureSuggest(context, "suggest_" + this.property, suggestUrl);
        }

    }

    /**
     * Constructor based on values
     */
    public ListFilterDTO(String property, String label, boolean multiValued, DataTypes dataType, Locale locale) {
        this(property, label, multiValued, dataType, locale, true);
    }

    /**
     * Constructor based on values
     */
    public ListFilterDTO(String property, String label, boolean multiValued, DataTypes dataType, Locale locale, boolean isDefault) {
        this();
        
        this.property = property;
        this.label = new Message(label).getLocalizedMessage(locale, null);
        this.multiValued = multiValued;
        this.dataType = dataType;
        this.standard = false;
        this.isDefault = isDefault;
    }

    /**
     * The name of the property being searched.
     */
    public String getProperty() {
        return property;
    }

    public void setProperty(String property) {
        this.property = property;
    }

    /**
     * Whether the filter allows searching for multiple values.
     */
    public boolean isMultiValued() {
        return multiValued;
    }

    public void setMultiValued(boolean multiValued) {
        this.multiValued = multiValued;
    }

    /**
     * The message key for the label to display when rendering the filter.
     */
    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    /**
     * The type of filter to display
     */
    public DataTypes getDataType() {
        return dataType;
    }

    public void setDataType(DataTypes dataType) {
        this.dataType = dataType;
    }

    /**
     *  An optional array of allowed values that can be displayed in a drop-down when rendering filters.
     */
    public List<SelectItem> getAllowedValues() {
        return allowedValues;
    }

    public void setAllowedValues(List<SelectItem> allowedValues) {
        this.allowedValues = new ArrayList<SelectItem>(allowedValues);
    }

    /**
     * Return true if any allowed values exist
     */
    public boolean hasAllowedValues() {
        return Util.size(this.allowedValues) > 0;
    }

    /**
     * Marks if this is a "standard" attribute or not, more important! 
     */
    public boolean isStandard() {
        return standard;
    }

    public void setStandard(boolean standard) {
        this.standard = standard;
    }

    /**
     * Marks if this is a default filter or not.
     */
    public boolean isDefault() {
        return isDefault;
    }

    public void setDefault(boolean aDefault) {
        isDefault = aDefault;
    }

    /**
     * Return a map of attributes that can affect the behavior of the filter.
     */
    public Map<String,Object> getAttributes() {
        return this.attributes;
    }

    public void setAttributes(Map<String,Object> attributes) {
        this.attributes = attributes;
    }

    /**
     * Set an attribute on this filter.
     *
     * @param  attr   The name of the attribute to set.
     * @param  value  The value to set for the attribute.
     */
    public void setAttribute(String attr, Object value) {
        if (null == this.attributes) {
            this.attributes = new HashMap<String,Object>();
        }
        this.attributes.put(attr, value);
    }

    /**
     * Get an attribute from this filter
     * @param attr Name of the attribute
     * @return Attribute value, or null if not set.
     */
    public Object getAttribute(String attr) {
        return (this.attributes == null) ? null : this.attributes.get(attr);
    }

    /**
     * @return List of SelectItem containing ListFilterValue.Operations available for this filter.
     */
    public List<SelectItem> getAllowedOperations() {
        return allowedOperations;
    }

    public void setAllowedOperations(List<SelectItem> allowedOperations) {
        this.allowedOperations = allowedOperations;
    }

    /**
     * Add the allowed operation to this list
     * @param operation Operation value to add.
     */
    public void addAllowedOperation(ListFilterValue.Operation operation, Locale locale) {
        if (this.allowedOperations == null) {
            this.allowedOperations = new ArrayList<SelectItem>();
        }
        this.allowedOperations.add(new SelectItem(operation, locale));
    }
    
    /**
     * Set the suggest context and suggestId on this filter.
     */
    public void configureSuggest(String context, String suggestId, String suggestUrl) {
        this.setAttribute(ListFilterDTO.ATTR_SUGGEST_CONTEXT, context);
        this.setAttribute(ListFilterDTO.ATTR_SUGGEST_ID, suggestId);
        if (suggestUrl != null) {
            this.setAttribute(ListFilterDTO.ATTR_SUGGEST_URL, suggestUrl);
        }
    }

    /**
     * Set necessary attributes for suggest for Object type filters
     * @param suggestClass Simple class name
     * @param suggestUrl Relative URL to suggest endpoint
     */
    public void configureObjectSuggest(String suggestClass, String suggestUrl) {
        this.setAttribute(ListFilterDTO.ATTR_SUGGEST_CLASS, suggestClass);
        this.setAttribute(ListFilterDTO.ATTR_SUGGEST_URL, suggestUrl);
    }

    /**
     * Set the necessary attributes for a column suggest using this filter and LCM settings
     * @param suggestClass Simple class name
     * @param suggestColumn Column name
     * @param lcmAction LCM Action/Quick Link name
     * @param lcmQuickLink LCM Quicklink
     * @param suggestUrl The relative URL to suggest endpoint
     */
    public void configureLcmColumnSuggest(String suggestClass, String suggestColumn, String lcmAction, String lcmQuickLink, String suggestUrl) {
        this.setDataType(DataTypes.Column);
        this.configureColumnSuggest(suggestClass, suggestColumn, null, suggestUrl);
        this.setAttribute(ListFilterDTO.ATTR_SUGGEST_IS_LCM, true);
        this.setAttribute(ListFilterDTO.ATTR_SUGGEST_LCM_ACTION, lcmAction);
        this.setAttribute(ListFilterDTO.ATTR_SUGGEST_LCM_QUICKLINK, lcmQuickLink);
    }

    /**
     * Set the necessary attributes for a column suggest using this filter
     * @param suggestClass Simple class name
     * @param suggestColumn Column name
     * @param filterString The optional filter string
     * @param suggestUrl The relative URL to suggest endpoint
     */                      
    public void configureColumnSuggest(String suggestClass, String suggestColumn, String filterString, String suggestUrl) {
        this.setDataType(DataTypes.Column);
        this.setAttribute(ListFilterDTO.ATTR_SUGGEST_CLASS, suggestClass);
        this.setAttribute(ListFilterDTO.ATTR_SUGGEST_COLUMN, suggestColumn);
        if (filterString != null) {
            this.setAttribute(ListFilterDTO.ATTR_SUGGEST_FILTER_STRING, filterString);
        }
        if (suggestUrl != null) {
            this.setAttribute(ListFilterDTO.ATTR_SUGGEST_URL, suggestUrl);
        }
    }

    /**
     * Set the properties for additional filter
     * @param categoryName Label of the category
     * @param categoryIndex Index of the category
     */
    public void configureCategory(String categoryName, int categoryIndex) {
        Map<String, Object> categoryMap = new HashMap<>();
        categoryMap.put(CATEGORY_LABEL, categoryName);
        categoryMap.put(CATEGORY_INDEX, categoryIndex);
        this.setAttribute(ListFilterDTO.ATTR_CATEGORY, categoryMap);
    }
    
    /**
     * Set the necessary attributes for a column suggest using this filter
     * @param suggestClass Simple class name
     * @param suggestColumn Column name
     * @param isLcm True if this is LCM based filter, otherwise false
     * @param lcmAction LCM Action/Quick Link name
     * @param targetIdentityId The id of the target identity if this is used in user access
     * @param suggestUrl The relative URL to suggest endpoint
     */
   public void configureColumnSuggest(String suggestClass, String suggestColumn, Boolean isLcm, String lcmAction, String quickLinkName, String targetIdentityId, String suggestUrl) {
       this.setDataType(DataTypes.Column);
       configureColumnSuggest(suggestClass, suggestColumn, null, suggestUrl);
       this.setAttribute(ListFilterDTO.ATTR_SUGGEST_IS_LCM, isLcm);
       this.setAttribute(ListFilterDTO.ATTR_SUGGEST_LCM_ACTION, lcmAction);
       this.setAttribute(ListFilterDTO.ATTR_SUGGEST_LCM_QUICKLINK, quickLinkName);
       this.setAttribute(ListFilterDTO.ATTR_SUGGEST_TARGET_ID, targetIdentityId);
   }

    /**
     * Get a comparator that can be used to sort, with default
     * filters on top, followed by standard attribute filters, 
     * followed by others.
     */
    public static Comparator<ListFilterDTO> getComparator() {
        return new Comparator<ListFilterDTO>() {
            @Override
            public int compare(ListFilterDTO a, ListFilterDTO b) {
                int val = compare(a.isDefault, b.isDefault);
                if (val == 0) {
                    val = compare(a.standard, b.standard);
                }
                return val;
            }

            /**
             * Compares two booleans for sorting 
             * If a is true and b is false, return 1
             * If a is false and b is true, return -1
             * If both match, return 0
             */
            private int compare(boolean a, boolean b) {
                return (a ^ b) ? ((a ^ true) ? 1 : -1) : 0;
            }
        };
    }

    /**
     * Get a comporator that can be used to sort based on filter labels.
     * @param locale Locale
     * @return Comparator for sorting filters by label name
     */
    public static Comparator<ListFilterDTO> getLabelComparator(final Locale locale) {
        return new Comparator<ListFilterDTO>() {
            @Override
            public int compare(ListFilterDTO a, ListFilterDTO b) {
                Collator collator = Collator.getInstance(locale);
                collator.setStrength(Collator.PRIMARY);
                return collator.compare(a.getLabel(), b.getLabel());
            }
        };
    }

    /** 
     * Return true if the filter is additional (not a top-level filter), false otherwise.
     */
    public boolean isAdditional() {
        return additional;
    }

    public void setAdditional(boolean isAdditional) {
        this.additional = isAdditional;
    }

    /**
     * @return True if this filter is using multi external attribute table
     */
    public boolean isMultiExternalAttribute() {
        return getAttribute(ATTR_EXTERNAL_ATTRIBUTE_TABLE) != null;
    }

    /**
     * @return True if this filter should allow editing of values (i.e. user can type in custom values as well as
     * choose a value from the suggest). This field has no effect if the value isn't a suggest.
     */
    public boolean getEditable() { return editable; }

    public void setEditable(boolean isEditable) { this.editable = isEditable; }
}