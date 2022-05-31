/*
 *  (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved.
 *
 *
 *  Configuration object to define a statistic that can be monitored on a given Server
 *
 */

package sailpoint.object;

import sailpoint.tools.GeneralException;
import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;

import java.util.List;

@XMLClass
public class MonitoringStatistic extends SailPointObject {

    //Entry to provide a List of Libraries to use for reconciling calls
    public static final String ATTR_CALL_LIBS = "callLibraries";

    //Entry to provide a List of Rule Libraries to aid in rule evaluation
    public static final String ATTR_RULE_LIBS = "ruleLibraries";

    //Entry to provide the name of the application in reference
    public static final String ATTR_REFERENCED_OBJECT = "referencedObject";

    public static final String ATTR_REF_OBJECT_TYPE = "referencedObjectType";

    //Name of angular filter used to render the value.
    public static final String ATTR_VALUE_RENDERER = "valueRenderer";


    //Name to be shown when displaying
    String displayName;

    /*
        Scriptlet text for how to obtain value. This will be converted to a Scriptlet
        This should be in the form:
        call:<methodName>
        script:<scriptCode>
        rule:<ruleName>
     */
    String value;

    // Parsed value field into Scriptlet
    Scriptlet valueSource;

    // Type of object returned when valueSource evaluated.
    String valueType;

    //Type of Statistic. Default list defined in StatisticType
    String type;

    // List of tags associated to this Statistic
    List<Tag> tags;

    Attributes attributes;

    //True if this is a template that requires customization
    boolean template;


    enum StatisticType {
        Server,
        JVM,
        Application
    };

    @XMLProperty
    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    @XMLProperty
    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public Scriptlet getValueSource() {
        if (valueSource == null) {
            valueSource = new Scriptlet(value, Scriptlet.METHOD_STRING, null);
        }
        return valueSource;

    }

    public void setValueSource(Scriptlet valueSource) {
        this.valueSource = valueSource;
    }

    @XMLProperty
    public String getValueType() {
        return valueType;
    }

    public void setValueType(String valueType) {
        this.valueType = valueType;
    }

    @XMLProperty
    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    @XMLProperty(mode= SerializationMode.REFERENCE_LIST)
    public List<Tag> getTags() {
        return tags;
    }

    public void setTags(List<Tag> tags) {
        this.tags = tags;
    }

    @XMLProperty(mode=SerializationMode.UNQUALIFIED)
    public Attributes getAttributes() {
        return attributes;
    }

    /**
     * Retrieve a String valued configuration setting.
     */
    public String getStringAttributeValue(String name) {
        return attributes != null ? attributes.getString(name) : null;
    }

    public void setAttributes(Attributes attributes) {
        this.attributes = attributes;
    }

    public Object getAttribute(String name) {
        return (attributes != null) ? attributes.get(name) : null;
    }

    @XMLProperty
    public boolean isTemplate() {
        return template;
    }

    public void setTemplate(boolean b) {
        template = b;
    }


    @Override
    public void visit(Visitor v) throws GeneralException {
        v.visitMonitoringStatistic(this);
    }


}
