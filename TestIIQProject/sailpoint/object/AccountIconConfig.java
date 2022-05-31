/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * @author <a href="mailto:dan.smith@sailpoint.com">Dan Smith</a>
 */
package sailpoint.object;

import sailpoint.tools.xml.AbstractXmlObject;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;

/**
 * A class to encapsulate the configuration to drive the icons that are
 * displayed when displaying accounts in the identities page and certification
 * pages.
 */
@XMLClass
public class AccountIconConfig extends AbstractXmlObject {
    private static final long serialVersionUID = -61384782522229203L;

    /**
     * Icon to display when the value defined in the _value field is found.
     */
    private String _source;

    /**
     * Attribute name from which the value is taken.  This must be an extended
     * link attribute defined in the ObjectConfig for Link.
     */
    private String _attribute;

    /**
     * Attribute value that trigger the icon to be displayed.
     */
    private String _value;

    /**
     * Title that is displayed when the icon is moused over.
     */
    private String _title;

    public AccountIconConfig () {
        _source = null;
        _attribute = null;
        _value = null;
        _title = null;
    }

    /**
     * Icon to display when the value defined in the 
     * <code>value</code> property is found.
     */
    @XMLProperty
    public String getSource() {
        return _source;
    }

    public void setSource(String icon) {
        _source = icon;
    }

    /**
     * Attribute name from which the value is taken.  This must be an extended
     * link attribute defined in the ObjectConfig for Link.
     */
    @XMLProperty
    public String getAttribute() {
        return _attribute;
    }

    public void setAttribute(String attribute) {
        _attribute = attribute;
    }

    /**
     * Title that is displayed when the icon is moused over.
     */
    @XMLProperty
    public String getTitle() {
        return _title;
    }

    public void setTitle(String title) {
        _title = title;
    }

    /**
     * Attribute value that triggers the icon to be displayed.
     */
    @XMLProperty
    public void setValue(String value) {
        _value = value;
    }

    public String getValue() {
        return _value;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;

        final AccountIconConfig other = (AccountIconConfig)obj;

        String otherAttr = other.getAttribute();
        if ( !safelyCheckEquality(_attribute, otherAttr) ) {
            return false;
        } 
        String otherValue = other.getValue();
        if ( !safelyCheckEquality(_value, otherValue) ) {
            return false;
        } 
        String otherTitle = other.getTitle();
        if ( !safelyCheckEquality(_title, otherTitle) ) {
            return false;
        } 
        String otherIcon = other.getSource();
        if ( !safelyCheckEquality(_source, otherIcon) ) {
            return false;
        } 
        return true;
    }

    private boolean safelyCheckEquality(Object o1, Object o2) {
        if ( o1 == null ) {
            if ( o2 != null ) {
                return false;
            }
        } 
        else {
            if ( !o1.equals(o2) ) {
                return false;
            }
        }
        return true;
    }
}
