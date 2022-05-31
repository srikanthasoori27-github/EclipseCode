package sailpoint.object;


import java.util.List;

import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLProperty;

public class RecommenderDefinition extends SailPointObject
        implements Cloneable {

    //////////////////////////////////////////////////////////////////////
    //
    // Constants
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Configuration attribute (String) holding the name of the Java
     * class to construct.  This is a required attribute.
     */
    public static String ATT_CLASSNAME = "class";

    /**
     * Configuration attribute (String) holding the name of the Plugin
     * which contains the recommender (if the recommender is defined in
     * a plugin).  May be null;
     */
    public static String ATT_PLUGINNAME = "plugin";

    /**
     * Configuration attribute (boolean) set to true if this recommender
     * definition points to the Identity AI recommender.
     */
    public static String ATT_IS_IAI_RECOMMENDER = "isIAIPlugin";

    public static String ATT_SUPPORTED_TYPES = "supportedRequestTypes";
    
    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Configuration and status attributes.
     */
    private Attributes<String,Object> _attributes;

    //////////////////////////////////////////////////////////////////////
    //
    // Properties
    //
    //////////////////////////////////////////////////////////////////////

    public RecommenderDefinition() {
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
