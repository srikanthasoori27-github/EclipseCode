package sailpoint.plugin;

import java.util.List;

import sailpoint.tools.Util;
import sailpoint.tools.xml.AbstractXmlObject;
import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;

/**
 * Plugin setting model to be used to represent a single setting on the settings/configuration page for a Plugin.
 * Each one of these objects is used to represent a single configurable setting on the settings page and will be rendered depended on the options.
 * @author brian.li
 *
 */
@XMLClass
public class Setting extends AbstractXmlObject {

    private static final long serialVersionUID = -5130441749120358903L;

    public static final String TYPE_BOOLEAN="boolean";
    public static final String TYPE_DATE="date";
    public static final String TYPE_INTEGER="int";
    public static final String TYPE_LONG="long";
    public static final String TYPE_IDENTITY="identity";
    public static final String TYPE_MANAGED_ATTRIBUTE="managedAttribute";
    public static final String TYPE_SECRET="secret";
    public static final String TYPE_STRING="string";
    public static final String TYPE_APPLICATION="application";
    public static final String TYPE_BUNDLE="bundle";

    /**
     * Name of the current setting
     */
    private String name;

    /**
     * The type that this setting is
     * Ex. "string" or "int"
     */
    private String dataType;

    /**
     * The current set value for the setting
     */
    private String value;

    /**
     * Label to be displayed for the setting
     */
    private String label;

    /**
     * Associated help text for the setting
     */
    private String helpText;

    /**
     * List of allowed values to populate for a dropdown if the setting requires strict values
     */
    private List<Object> allowedValues;

    /**
     * Default value to populate for the setting
     */
    private String defaultValue;

    /**
     * Multiple values allowed.  This is only valid for Strings, and Sailpoint objects.
     */
    private boolean multiValue;

    @XMLProperty
    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }

    @XMLProperty
    public String getDataType() {
        return dataType;
    }
    public void setDataType(String dataType) {
        this.dataType = dataType;
    }

    @XMLProperty
    public String getValue() {
        return value;
    }
    public void setValue(String value) {
        this.value = value;
    }

    /**
     * Return the value in its native type, based on the value of dataType.
     * @return Value as dataType
     */
    public Object getParsedValue() {
        if (!Util.isNullOrEmpty(this.value)) {
            if (this.dataType.equals(TYPE_INTEGER)) {
                if (!Util.isNullOrEmpty(this.defaultValue)) {
                    return Util.atoi(this.value, (int) this.getParsedDefaultValue());
                } else {
                    return Util.atoi(this.value);
                }
            } else if (this.dataType.equals(TYPE_BOOLEAN)) {
                return Boolean.parseBoolean(this.value);
            }
        }

        return this.value;
    }

    @XMLProperty
    public String getLabel() {
        return label;
    }
    public void setLabel(String label) {
        this.label = label;
    }

    @XMLProperty
    public String getHelpText() {
        return helpText;
    }
    public void setHelpText(String helpText) {
        this.helpText = helpText;
    }

    @XMLProperty(mode=SerializationMode.LIST)
    public List<Object> getAllowedValues() {
        return allowedValues;
    }
    public void setAllowedValues(List<Object> allowedValues) {
        this.allowedValues = allowedValues;
    }

    @XMLProperty
    public String getDefaultValue() {
        return defaultValue;
    }
    public void setDefaultValue(String defaultValue) {
        this.defaultValue = defaultValue;
    }

    /**
     * Return the default value in its native type, based on the value of dataType.
     * @return Default value as dataType
     */
    public Object getParsedDefaultValue() {
        if (!Util.isNullOrEmpty(this.defaultValue)) {
            if (this.dataType.equals(TYPE_INTEGER)) {
                return Util.atoi(this.defaultValue);
            } else if (this.dataType.equals(TYPE_BOOLEAN)) {
                return Boolean.parseBoolean(this.defaultValue);
            }
        }

        return this.defaultValue;
    }

    @XMLProperty
    public boolean isMultiValue() {
        return multiValue;
    }
    public void setMultiValue(boolean multiValue) {
        this.multiValue = multiValue;
    }

    public static boolean multiValueSupportedForType(Setting setting) {
       switch(setting.getDataType()) {
           case TYPE_STRING:
           case TYPE_APPLICATION:
           case TYPE_BUNDLE:
           case TYPE_MANAGED_ATTRIBUTE:
           case TYPE_IDENTITY:
               return true;

           default:
               return false;
       }
    }
}
