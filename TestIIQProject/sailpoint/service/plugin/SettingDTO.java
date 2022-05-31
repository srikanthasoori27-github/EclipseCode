/* (c) Copyright 2018 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.service.plugin;

import java.util.List;
import java.util.Map;

/**
 * This is a DTO for plugin settings.  See Setting.java for the object that this represents.
 */
public class SettingDTO {

    /**
     * The name of the setting
     */
    private String name;

    /**
     * The dataType of the setting
     */
    private String dataType;

    /**
     * The value (as a string) of the setting.  For multi-valued objects, this will
     * be a csv.  For sailpoint objects, it will be the name or id.
     */
    private String value;

    /**
     * The label value for the setting
     */
    private String label;

    /**
     * The help text for the setting
     */
    private String helpText;

    /**
     * The allowed values for the setting
     */
    private List<Object> allowedValues;
    
    /**
     * The default value of the setting
     */
    private String defaultValue;

    /**
     * The data needed to display the referenced object (for sailpoint object).
     * This gets populated by looking up the object by name or id that is in
     * the value
     */
    private Map<String, Object> referencedObject;

    /**
     * True if this setting allows multiple values.
     */
    private boolean multiValue;

    /**
     * The list of values for multiValued settings.
     */
    private List<Object> multiValueList;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDataType() {
        return dataType;
    }

    public void setDataType(String dataType) {
        this.dataType = dataType;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getHelpText() {
        return helpText;
    }

    public void setHelpText(String helpText) {
        this.helpText = helpText;
    }

    public List<Object> getAllowedValues() {
        return allowedValues;
    }

    public void setAllowedValues(List<Object> allowedValues) {
        this.allowedValues = allowedValues;
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    public void setDefaultValue(String defaultValue) {
        this.defaultValue = defaultValue;
    }

    public Map<String, Object> getReferencedObject() {
        return referencedObject;
    }

    public void setReferencedObject(Map<String, Object> referencedObject) {
        this.referencedObject = referencedObject;
    }

    public List<Object> getMultiValueList() {
        return multiValueList;
    }

    public void setMultiValueList(List<Object> multiValueList) {
        this.multiValueList = multiValueList;
    }

    public boolean isMultiValue() {
        return multiValue;
    }

    public void setMultiValue(boolean multiValue) {
        this.multiValue = multiValue;
    }
}
