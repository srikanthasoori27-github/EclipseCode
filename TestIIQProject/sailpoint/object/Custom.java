/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * A class used to store customer-specific data.
 * 
 * Author: Jeff
 *
 */
package sailpoint.object;

import java.util.List;

import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;
import sailpoint.tools.xml.SerializationMode;

/**
 * A class used to store customer-specific data. This just stores a Map
 * as an XML blob. In that way it is similar to <code>Configuration</code>
 * but having a different class ensures that the namespaces will not conflict.
 * Also unlike Configuration, Custom does not require a unique name.
 *
 * The intent is that there not be very many of these so they can
 * be considered "exportable" classes.  Do not use it for things like
 * a custom audit log where you can have thousands of instances.
 */
@XMLClass
public class Custom extends SailPointObject implements Cloneable {

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * The attribute map.
     */
    private Attributes<String,Object> _attributes;

    //////////////////////////////////////////////////////////////////////
    //
    // Constructors
    //
    //////////////////////////////////////////////////////////////////////

    public Custom() {
        _attributes = new Attributes<String, Object>();
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Properties
    //
    //////////////////////////////////////////////////////////////////////

    @XMLProperty(mode=SerializationMode.INLINE)
    public Attributes<String, Object> getAttributes() {
        return _attributes;
    }

    public void setAttributes(Attributes<String, Object> a) {
        _attributes = a;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Convenience accessors
    //
    //////////////////////////////////////////////////////////////////////

    public Object get(String name) {
        return (_attributes != null) ? _attributes.get(name) : null;
    }

    public void put(String name, Object value) {
        if (_attributes == null)
            _attributes = new Attributes<String,Object>();
        _attributes.put(name, value);
    }

    public void put(String name, int value) {
        if (_attributes == null)
            _attributes = new Attributes<String,Object>();
        _attributes.put(name, new Integer(value));
    }

    public String getString(String name) {
        return (_attributes != null) ? _attributes.getString(name) : null;
    }

    public int getInt(String name) {
        return (_attributes != null) ? _attributes.getInt(name) : null;
    }

    public long getLong(String name) {
        return (_attributes != null) ? _attributes.getLong(name) : null;
    }

    public boolean getBoolean(String name) {
        return (_attributes != null) ? _attributes.getBoolean(name) : null;
    }

    public boolean getBoolean(String name, boolean dflt) {
        return (_attributes != null) ? _attributes.getBoolean(name, dflt) : dflt;
    }

    public List getList(String name) {
        return (_attributes != null) ? _attributes.getList(name) : null;
    }

    public void remove(String name) {
        if (_attributes != null)
            _attributes.remove(name);
    }
    
    public boolean containsAttribute(String name) {
        return (_attributes != null) ? _attributes.containsKey(name) : false;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // SailPointObject methods
    //
    //////////////////////////////////////////////////////////////////////

    public Object clone() throws CloneNotSupportedException {
        return super.clone();
    }

}
