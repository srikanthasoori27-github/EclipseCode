/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.object;

import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;

import sailpoint.tools.Util;

/**
 * @author <a href="mailto:dan.smith@sailpoint.com">Dan Smith</a>
 * 
 * As of 5.2 this class is deprecated and has been replaced 
 * with storing a List of Application template objects.
 * The template objects are stored under the ConnectorRegistry
 * in the key templateApplications.
 */
@XMLClass
@Deprecated
public class ConnectorConfig implements Cloneable {

    /**
     * Display Name
     */
    private String _displayName;

    /**
     * Class for the connector
     */
    private String _class;

    /**
     * xhtml used to render attribute configuration
     */
    private String _attrForm;

    public ConnectorConfig() {
        _class = null;
        _attrForm = null;
        _displayName = null;
    }

    public ConnectorConfig(String displayName, String clazz) {
        _displayName = displayName;
        _class = clazz;
    }

    @XMLProperty
    public String getClassName() {
        return _class;
    }

    public void setClassName(String clazz) {
        _class = clazz;
    }

    @XMLProperty
    public String getAttributesForm() {
        return _attrForm;
    }

    public void setAttributesForm(String attrForm) {
        _attrForm = attrForm;
    }

    @XMLProperty
    public String getDisplayName() {
        return _displayName;
    }

    public void setDisplayName(String displayName) {
        _displayName = displayName;
    }

    public Object clone() {
        Object buddy = null;
        try {
            buddy = super.clone();
        } catch (CloneNotSupportedException cnfe) { }
        return buddy;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;

        final ConnectorConfig other = (ConnectorConfig)obj;

        String clazz = other.getClassName();
        if ( !Util.nullSafeEq(_class, clazz, true) ) {
            return false;
        } 
        String displayName = other.getDisplayName();
        if ( !Util.nullSafeEq(_displayName, displayName, true) ) {
            return false;
        } 
        String form = other.getAttributesForm();
        if ( !Util.nullSafeEq(_attrForm, form, true) ) {
            return false;
        } 
        return true;
    }
}
