package sailpoint.object;


import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLProperty;

import java.util.List;

public class Module extends SailPointObject
        implements Cloneable {

    //////////////////////////////////////////////////////////////////////
    //
    // Constants
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Configuration attribute (String) holding the name of the Java
     * class to construct to check health.  This is a required attribute.
     */
    public static String ATT_HEALTH_CHECK_CLASS = "healthCheckClass";


    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Configuration and status attributes.
     */
    private Attributes<String,Object> _attributes;

    private String _description;

    //////////////////////////////////////////////////////////////////////
    //
    // Properties
    //
    //////////////////////////////////////////////////////////////////////

    public Module() {
    }

    /**
     * Let the ScopeService know this class does not have a scope.
     */
    @Override
    public boolean hasAssignedScope() {
        return false;
    }

    @XMLProperty(mode= SerializationMode.INLINE)
    public Attributes<String, Object> getAttributes() {
        return _attributes;
    }

    public void setAttributes(Attributes<String, Object> a) {
        _attributes = a;
    }

    public Object getAttribute(String name) {
        return (_attributes != null) ? _attributes.get(name) : null;
    }

    public void setAttribute(String name, Object value) {
        if (value == null) {
            if (_attributes != null)
                _attributes.remove(name);
        }
        else {
            if (_attributes == null)
                _attributes = new Attributes<String,Object>();
            _attributes.put(name, value);
        }
    }

    @XMLProperty
    public String getDescription() {
        return _description;
    }

    public void setDescription(String description) {
        this._description = description;
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
        return (_attributes != null) ? _attributes.getInt(name) : 0;
    }

    public boolean getBoolean(String name) {
        return (_attributes != null) ? _attributes.getBoolean(name) : false;
    }

    public List getList(String name) {
        return (_attributes != null) ? _attributes.getList(name) : null;
    }

    public void remove(String name) {
        if (_attributes != null)
            _attributes.remove(name);
    }

}
