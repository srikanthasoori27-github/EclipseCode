/* (c) Copyright 2009 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.service.form.renderer.item;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import sailpoint.object.Attributes;

/**
 * DTO object used to pass field definitions to the client. This object includes a number
 * of standard properties as well as 'custom' items that will only be handled if your form
 * is rendered by the sailpoint.form.FormPanel javascript class.
 * <p/>
 * Any additional configuration parameters may be added to the attributes object. When serialization
 * occurs, any entries in the attributes map will be included in the JSON sent to the client.
 *
 * @author: jonathan.bryant@sailpoint.com
 */
public class FormItemDTO {

    public enum FormItemType {
        STRING("textfield"),
        NUMBER("number"),
        DATE("date"),
        IDENTITY("identity"),
        SELECT("select"),
        CHECKBOX("checkbox"),
        CHECKBOX_GROUP("checkboxgroup"),
        RADIO_GROUP("radiogroup"),
        TEXTAREA("textarea"),
        MULTISUGGEST("multisuggest"),
        MULTISELECT("multiselect"),
        MULTITEXT("multitext"),
        LABEL("label"),
        DATATABLE("datatable"),
        SECRET("secret"),
        SUGGEST("suggest"),
        MANAGED_ATTRIBUTE("managedattribute");

        private String _fieldName;

        private FormItemType(String fieldName) {
            this._fieldName = fieldName;
        }

        public String getFieldName() {
            return this._fieldName;
        }
    }


    /**
     * Custom field attributes. All attributes will be added as configuration
     * items to the field json. Check the EXT documentation for available
     * configuration options.
     * <p/>
     * Note that we will not override any of the member properties
     * such as name, fieldLable, xtype, etc. when performing serialization.
     * So if you set those properties in the attributes map, they will
     * be ignored.
     */
    private Attributes attributes;

    /**
     * The type of field that this is
     */
    private String type;


    //////////////////////////////////////////////////////////////////////
    //
    // Standard Ext Properties
    //
    //////////////////////////////////////////////////////////////////////

    private String itemId;
    private String name;
    private String fieldLabel;
    private boolean disabled;
    private String valueField;
    private Object value;
    private Object previousValue;

    //////////////////////////////////////////////////////////////////////
    //
    // Custom SailPoint Properties - use the javscript class
    //   sailpoint.form.FormPanel if you want these attributes.
    //
    //////////////////////////////////////////////////////////////////////


    /**
     * Helptext to be rendered with the field. If present a ? icon
     * will be added to the fieldLabel along with a Tooltip with the
     * given text.
     */
    private String helpText;

    /**
     * Available values list for the combobox, radio button group or
     * checkbox group. This will be converted into an Ext SimpleStore.
     */
    private List allowedValues;

    /**
     * Datasource for the combobox.
     */
    private String datasourceUrl;


    /**
     * Additional base parameters for a combobox
     */
    private Map<String, Object> baseParams;

    /**
     * True if this component's values should be sortable.
     * For comboboxes this will enable the sort buttons.
     * See MultiSelect.js for implementation details.
     */
    private boolean sortable;

    /**
     * Name of the class that is selectable
     * from this widget. This will drive the
     * template used to display list items.
     */
    private String suggestClass;

    /**
     * Additional filter string to be passed to the backing
     * JsonStore. This is only applicable to Suggest components.
     */
    private String filter;

    /**
     * True if this is a required field. If true an '*' will
     * be added to the fieldLabel.
     */
    private boolean required;

    private boolean hidden;

    private int columnSpan;

    private boolean postBack;

    //notes if the date is an end date so that hours and seconds may be set to max

    private boolean endDate;

    private boolean allowValueClick;

    /**
     * The height of the field. Allowed in ExtJS, but only used for textarea when
     * rendering forms in the new UI.
     */
    private Integer height;

    /**
     * Whether this field has dynamic values and will gather them depending on inputs in the
     * form
     */
    private boolean dynamic;

    public FormItemDTO() {
    }

    /**
     * id will get interpreted as a DOM node id, itemId will be
     * rendered in a more unique fashion.
     *
     * @return the item id
     * @see #getItemId()
     */
    @Deprecated
    public String getId() {
        return this.getItemId();
    }

    /**
     * @param id the item id
     * @see #setItemId(String)
     */
    @Deprecated
    public void setId(String id) {
        this.setItemId(id);
    }

    public String getItemId() {
        return itemId;
    }

    public void setItemId(String itemId) {
        this.itemId = itemId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getFieldLabel() {
        return fieldLabel;
    }

    public void setFieldLabel(String fieldLabel) {
        this.fieldLabel = fieldLabel;
    }

    public boolean isDisabled() {
        return disabled;
    }

    public void setDisabled(boolean disabled) {
        this.disabled = disabled;
    }

    public String getValueField() {
        return valueField;
    }

    public void setValueField(String valueField) {
        this.valueField = valueField;
    }

    public String getHelpText() {
        return helpText;
    }

    public void setHelpText(String helpText) {
        this.helpText = helpText;
    }

    public List getAllowedValues() {
        return allowedValues;
    }

    public void setAllowedValues(List allowedValues) {
        this.allowedValues = allowedValues;
    }

    public void addAllowedValue(Object value) {
        if (value == null)
            return;

        if (this.allowedValues == null)
            this.allowedValues = new ArrayList();

        this.allowedValues.add(value);
    }

    public String getDatasourceUrl() {
        return datasourceUrl;
    }

    public void setDatasourceUrl(String datasourceUrl) {
        this.datasourceUrl = datasourceUrl;
    }

    public Map<String, Object> getBaseParams() {
        return baseParams;
    }

    public void setBaseParams(Map<String, Object> baseParams) {
        this.baseParams = baseParams;
    }

    public Object getValue() {
        return value;
    }

    public void setValue(Object value) {
        this.value = value;
    }

    public boolean isSortable() {
        return sortable;
    }

    public void setSortable(boolean sortable) {
        this.sortable = sortable;
    }

    public String getSuggestClass() {
        return suggestClass;
    }

    public void setSuggestClass(String suggestClass) {
        this.suggestClass = suggestClass;
    }

    public String getFilter() {
        return filter;
    }

    public void setFilter(String filter) {
        this.filter = filter;
    }

    public Attributes getAttributes() {
        return attributes;
    }

    public void setAttributes(Attributes attributes) {
        this.attributes = attributes;
    }

    public void addAttribute(String name, Object value) {
        if (attributes == null)
            attributes = new Attributes();
        attributes.put(name, value);
    }

    public boolean isRequired() {
        return required;
    }

    public void setRequired(boolean required) {
        this.required = required;
    }

    public Object getPreviousValue() {
        return previousValue;
    }

    public void setPreviousValue(Object previousValue) {
        this.previousValue = previousValue;
    }

    public boolean isHidden() {
        return hidden;
    }

    public void setHidden(boolean hidden) {
        this.hidden = hidden;
    }

    public int getColumnSpan() {
        return columnSpan;
    }

    public void setColumnSpan(int columnSpan) {
        this.columnSpan = columnSpan;
    }

    public boolean isPostBack() {
        return postBack;
    }

    public void setPostBack(boolean postBack) {
        this.postBack = postBack;
    }

    public boolean isEndDate() {
        return endDate;
    }

    public void setEndDate(boolean val) {
        endDate = val;
    }

    public void setAllowValueClick(boolean allowValueClick) {
        this.allowValueClick = allowValueClick;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Integer getHeight() {
        return height;
    }

    public void setHeight(Integer height) {
        this.height = height;
    }

    /**
     * Json serializer. This allows us to include the attributes
     * map as field object properties.
     *
     * @return
     */
    public Map toMap() {

        Map map = new HashMap();

        map.put("itemId", itemId);
        map.put("type", type);
        map.put("name", name);
        map.put("fieldLabel", fieldLabel);
        map.put("value", value);
        map.put("sortable", sortable);
        map.put("filter", filter);
        map.put("required", required);
        map.put("previousValue", previousValue);
        map.put("hidden", hidden);
        map.put("columnSpan", columnSpan);
        map.put("postBack", postBack);
        map.put("suggestClass", suggestClass);
        map.put("allowValueClick", allowValueClick);
        map.put("disabled", disabled);
        map.put("dynamic", dynamic);
        if (helpText != null)
            map.put("helpText", helpText);
        if (allowedValues != null)
            map.put("allowedValues", allowedValues);
        if (datasourceUrl != null)
            map.put("datasourceUrl", datasourceUrl);
        if (baseParams != null) {
            map.put("baseParams", baseParams);
        }
        if (valueField != null) {
            map.put("valueField", valueField);
        }
        if (endDate != false) {
            map.put("endDate", endDate);
        }

        if (height != null) {
            map.put("height", height);
        }

        // add any attributes that have not been explicitly set already
        if (getAttributes() != null) {
            for (Object key : getAttributes().getKeys()) {
                if (!map.containsKey(key)) {
                    map.put(key, getAttributes().get(key));
                }
            }
        }

        return map;
    }

    public boolean isDynamic() {
        return dynamic;
    }

    public void setDynamic(boolean dynamic) {
        this.dynamic = dynamic;
    }
}
