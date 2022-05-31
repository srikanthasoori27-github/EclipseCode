/* (c) Copyright 2019 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.object;

import java.util.Locale;
import java.util.Map;

import sailpoint.tools.GeneralException;
import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLProperty;

/**
 * Simple SailPoint object that can be used to classify some other object. Currently can be associated
 * with Bundle and ManagedAttribute objects.
 */
public class Classification extends SailPointObject implements Describable {

    /**
     * Display name for the classification.
     */
    private String displayName;

    /**
     * Map of attributes, to hold localized descriptions.
     */
    private Attributes<String, Object> attributes;

    /**
     * Name of the source in which the classification originated
     */
    private String origin;

    /**
     * Type of Classification. This can be used to group Classifications in/across different origins
     */
    private String type;

    public Classification() {

    }

    public void visit(Visitor v) throws GeneralException {
        v.visitClassification(this);
    }

    @XMLProperty
    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    /**
     * Returns the displayName if it is non-null otherwise returns the name.
     * This is a Hibernate pseudo property used to populate a column that will
     * have a reliable non-null value containing the displayName if it is available.
     */
    public String getDisplayableName() {
        if (null == this.displayName) {
            return _name;
        }

        return this.displayName;
    }

    /**
     * @exclude
     * This method does nothing. displayableName is a read-only property
     * used to populate a Hibernate column for searching.
     */
    public void setDisplayableName(String displayableName) {
        return;
    }

    @XMLProperty(mode= SerializationMode.UNQUALIFIED)
    public Attributes<String, Object> getAttributes() {
        return attributes;
    }

    public void setAttributes(Attributes<String, Object> attributes) {
        this.attributes = attributes;
    }

    public void setAttribute(String name, Object value) {
        if (value == null) {
            if (attributes != null)
                attributes.remove(name);
        }
        else {
            if (attributes == null)
                attributes = new Attributes<String,Object>();
            attributes.put(name, value);
        }
    }

    public Object getAttribute(String name) {
        return (attributes != null) ? attributes.get(name) : null;
    }

    @XMLProperty
    public String getOrigin() { return this.origin; }

    public void setOrigin(String s) { this.origin = s; }

    @XMLProperty
    public String getType() { return this.type; }

    public void setType(String t) { this.type = t; }


    /**
     * Let the ScopeService know this class does not have a scope.
     */
    @Override
    public boolean hasAssignedScope() {
        return false;
    }


    //////////////////////////////////////////////////////////////////////
    //
    // Describable interface
    //
    //////////////////////////////////////////////////////////////////////

    @Override
    public Map<String, String> getDescriptions() {
        Map<String,String> map = null;
        Object o = getAttribute(ATT_DESCRIPTIONS);
        if (o instanceof Map)
            map = (Map<String,String>)o;
        return map;
    }

    @Override
    public void setDescriptions(Map<String, String> map) {
        setAttribute(ATT_DESCRIPTIONS, map);
    }

    @Override
    public void addDescription(String locale, String desc) {
        new DescribableObject<>(this).addDescription(locale, desc);
    }

    @Override
    public String getDescription(String locale) {
        return new DescribableObject<>(this).getDescription(locale);
    }

    @Override
    public String getDescription(Locale locale) {
        return new DescribableObject<>(this).getDescription(locale);
    }
}
