/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.object;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import sailpoint.tools.Util;
import sailpoint.tools.xml.AbstractXmlObject;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;
import sailpoint.tools.xml.SerializationMode;

/**
 * The definition of an object returned from an Application.  
 * This is not a true persistent class, it is transient and the callers
 * typically create other objects out of the data stored 
 * in the ResourceObjects.
 */
@XMLClass
public class ResourceObject extends AbstractXmlObject {

    public static String ATT_IDENTITY_TYPE = "identity_type";

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * For template applications, the identifier of the specific instance
     * where this object resides.
     */
    private String _instance;

    /**
     * Native identity of the object in the Application.
     * 
     */
    private String _identity;

    /**
     * Optional universally unique identifier.
     * This is normally seen only with directories.
     */
    private String _uuid;

    /**
     * The native identity the object has previously.
     * This is used only during delta aggregation and set
     * by connectors that can detect renames. IdentityIQ does not
     * currently support renames, a new link will be added and
     * the old one will be deleted.
     */
    private String _previousIdentity;

    /**
     * Display name for this Application object, represents this object in gui.
     * <p>
     * If this is null, use the id.
     */
    private String _name;

    /**
     * Type of ResourceObject.
     */
    private String _objectType;

    /**
     * A flag indicating that this is an incomplete object.
     * Used in special cases where the connector has to return account
     * information in several phases and the objects might not have a 
     * complete set of all account attributes. The attributes in this
     * object will replace the corresponding attributes in the Link,
     * but no other Link attributes will be changed.
     *
     * UPDATE: SMConnector is no longer setting this.  Why?
     */
    boolean _incomplete;

    /**
     * A flag indicating that this is an incremental change object.
     * This is similar to _incomplete but it also means that
     * the values of of any multi-valued attributes in this object
     * should be merged with the existing values in the Link rather
     * than replacing the existing Link value.
     */ 
    boolean _incremental;

    /**
     * A flag indicating that this object has been deleted.
     * This is set only when doing delta aggregation and the
     * connector supports detection of native deletes.
     */
    boolean _delete;

    /**
     * A flag set indicating that the values in the attributes
     * map represent things to remove rather than things to add.
     * Setting this implies _incremental. The values which
     * are always for multi-valued attributes are removed from
     * the current values.
     */
    boolean _remove;

    /**
     * A list of attribute names that are not included in this object.
     * Normally the ResourceObject is authoritative over all
     * schema attributes and completely replaces the contents
     * of the Link. For agent connectors though, attributes are obtained
     * in two phases, the first with everything but groups, and the second
     * with groups. In the first phase retain the
     * current group list and only update it in the second phase so
     * that if the aggregation fails in the middle
     * a lot of group lists will not be nulled out. In practice this is only used with
     * SMConnector and will only contain "groups".
     * bug 10080
     */
    List<String> _missing;

    /**
     * Attributes of this ResourceObject.
     */
    private Attributes<String,Object> _attributes;

    /**
     * In Aggregation, for sparse object the count for total accounts scanned & 
     * identities updated is not incremented.To increment this count
     * set this flag to true.This flag is used for PE2 connectors.
     */
    private boolean _finalUpdate;

    //////////////////////////////////////////////////////////////////////
    //
    // Constructors
    //
    //////////////////////////////////////////////////////////////////////

    public ResourceObject() {
    }

    /** 
     * Construct a ResourceObject with the specified identity
     * and type.
     */
    public ResourceObject(String identity, String type) {
        _identity = identity;
        _objectType = type;
        _attributes = new Attributes<String,Object>();
    }

    /** 
     * Construct a ResourceObject with the specified identity, name 
     * and type.
     */
    public ResourceObject(String identity, String name, String type) {
        this(identity, type);
        _name = name;
    }

    /** 
     * Construct a ResourceObject with the specified identity, name 
     * and type.
     */
    public ResourceObject(String identity, String name, String type, 
                          Map attributes) {
        this(identity, name, type);
        _attributes = new Attributes(attributes);
    }
    
    public ResourceObject(Map src) { 
    	fromMap(src); 
    } 

    //////////////////////////////////////////////////////////////////////
    //
    // Properties
    //
    //////////////////////////////////////////////////////////////////////

    /** 
     * Returns a string that is used to uniquely identify an instance
     * of a template application.
     */
    @XMLProperty
    public String getInstance() {
        return _instance;
    }

    /**
     * Sets the instance of the template application.  
     */
    public void setInstance(String ins) {
        _instance = ins;
    }

    /** 
     * 	Returns a string that is used to uniquely identify an object on
     *  the application.
     */
    @XMLProperty
    public String getIdentity() {
        return _identity;
    }

    /**
     * Sets the identity for this object, this is the value that will be used
     * to fetch this object natively. This identifier must be unique and SHOULD
     * be a GUID if supported by the underlying application.
     */
    public void setIdentity(String identity) {
        _identity = identity;
    }

    /** 
     * Return the universally unique identifier. This is typically set
     * only for directories.
     */
    @XMLProperty
    public String getUuid() {
        return _uuid;
    }

    public void setUuid(String id) {
        _uuid = id;
    }

    /** 
     * Returns the previous identity of this object if it has been renamed since
     * the last aggregation. This is supported only during delta aggregation.
     */
    @XMLProperty
    public String getPreviousIdentity() {
        return _previousIdentity;
    }

    public void setPreviousIdentity(String identity) {
        _previousIdentity = identity;
    }

    /** 
     * Returns an optional alternate display name. When set this is
     * normally more readable than the native identity. It is not necessarily
     * unique (true?).
     */
    @XMLProperty
    public String getDisplayName() {
        return _name;
    }
    
    /** 
     * 	Returns string that represents a name that can be displayed 
     *  to represent object.
     */
    public void setDisplayName(String name) {
        _name = name;
    }

    /**
     * Return the nicest name we can.
     */
    public String getNameOrId() {
        String id = _name;
        if (Util.isNullOrEmpty(id))
            id = _identity;
        if (Util.isNullOrEmpty(id))
            id = _uuid;
        return id;
    }

    /** 
     * 	Returns a string which represents the objectType. This should be an
     *  identifier for the schema, something like Group, User, or Permission.
     */
    @XMLProperty
    public String getObjectType() {
        return _objectType;
    }

    /** 
     * 	Sets a string which represents this object's type. This should be an
     *  identifier for the schema, something like Group, User, or Permission.
     */
    public void setObjectType(String objectType) {
        _objectType = objectType;
    }

    /** 
     * Set a flag indicating that this is an incomplete object.
     * Used in special cases where the connector has to return account
     * information in several phases and the objects may not have a 
     * complete set of all account attributes. The attributes in this
     * object will replace the corresponding attributes in the Link,
     * but no other Link attributes will be changed.
     */
    @XMLProperty
    public void setIncomplete(boolean b) {
        _incomplete = b;
    }

    public boolean isIncomplete() {
        return _incomplete;
    }

    /**
     * Set a flag indicating that this is an incremental change object.
     * This is similar to "incomplete" but it also means that
     * the values of of any multi-valued attributes in this object
     * should be merged with the existing values in the Link rather
     * than replacing the existing Link value.
     */ 
    @XMLProperty
    public void setIncremental(boolean b) {
        _incremental = b;
    }

    public boolean isIncremental() {
        return _incremental;
    }

    /**
     * Set a flag indicating that this object has been deleted.
     * This is used only during delta aggregation when the connector
     * is able to detect deleted accounts.
     */ 
    @XMLProperty
    public void setDelete(boolean b) {
        _delete = b;
    }

    public boolean isDelete() {
        return _delete;
    }

    /**
     * Set a flag indicating that this object contains values
     * that have been removed from the account. This is used only
     * during delta aggregation.
     */ 
    @XMLProperty
    public void setRemove(boolean b) {
        _remove = b;
    }

    public boolean isRemove() {
        return _remove;
    }

    @XMLProperty
    public List<String> getMissing() {
        return _missing;
    }

    public void setMissing(List<String> names) {
        _missing = names;
    }

    /**
     * Set this flag when object is Sparse and in case of aggregation to
     * increase the count of accounts scanned and identities updated.
     */
    public void setFinalUpdate(boolean finalUpdate) {
        _finalUpdate = finalUpdate;
    }

    /**
     * Return true if the object is considered sparse, which means that
     * not all of the account attributes might be present.
     * This is a combination of the other options and cannot be set.
     *
     * Note that this does NOT factor in the _missing list.
     * If you think you need to change this, look carefully
     * at the Aggregator code. An RO with a missing list
     * is treated as if it contained all of the current values
     * from the Link and is therefore not sparse.
     */
    public boolean isSparse() {
        return _incomplete || _incremental || _remove || _delete;
    }

    /**
     * Return true if object is Sparse and to increase the count
     * of total accounts scanned and identities updated 
     * during aggregation.
     */
    public boolean isFinalUpdate() {
        return _finalUpdate;
    }

    /** 
     * Returns all of the names in the attribute map as a collection of Strings
     */
    public Collection<String> getAttributeNames() {
        return _attributes.keySet();
    }

    /**
     * Returns Attributes (which is a Map) of the object's attributes 
     */
    @XMLProperty(mode=SerializationMode.UNQUALIFIED)
    public Attributes<String,Object> getAttributes() {
        return _attributes;
    }

    /**
     * Sets the object's attributes 
     */
    public void setAttributes(Attributes<String,Object> attributes) {
        _attributes = attributes;
    }

    /**
     * Sets a named attribute to a supplied value.
     */
    public void setAttribute(String key, Object value) {
        if (key != null) {
            if (_attributes == null)
                _attributes = new Attributes<String,Object>();
            // TODO: if value is null we usually remove the entry,
            // but I don't want to mess up the test files - jsl
            _attributes.put(key,value);
        }
    }

    public Object getAttribute(String key) {
        return (_attributes != null) ? _attributes.get(key) : null;
    }

    //
    // jsl - Alternate mapish attribute accessors.  We use
    // these inconsistently in the other classes, make it easier for the
    // rule writers and provide both.
    //

    public Object get(String key) {
        return getAttribute(key);
    }

    public void put(String key, Object value) {
        setAttribute(key, value);
    }

    public void remove(String key) {
        if (key != null && _attributes != null)
            _attributes.remove(key);
    }

    /**
     * Returns the value of an attribute as a String.
     * This is the older signature, most code uses getString() instead.
     */
    public String getStringAttribute(String name) {
        return ((_attributes != null) ? _attributes.getString(name) : null);
    }

    /**
     * Returns the value of an attribute as a String.
     */
    public String getString(String name) {
        return getStringAttribute(name);
    }

    /**
     * Returns the value of an attribute as a List of String.
     */
    public List<String> getStringList(String name) {
        List<String> list = null;
        Object o = get(name);
        if (o instanceof List)
            list = (List)o;
        else if (o != null) {
            list = new ArrayList<String>();
            list.add(o.toString());
        }
        return list;
    }

    /**
     * Returns the boolean primitive value for the supplied attribute name.
     * <p>
     * If the value is not a boolean it will be coerced into one.
     */
    public boolean getBoolAttribute(String name) {
        return ((_attributes != null) ? _attributes.getBoolean(name) : false);
    }

    /**
     * Returns a cloned copy of this ResourceObject. This method 
     * will return a new object with a deep copy of the 
     * attributes.
     */
    public Object clone() throws CloneNotSupportedException {
        ResourceObject ro = 
            new ResourceObject(getIdentity(),getDisplayName(),getObjectType());
        Attributes<String,Object> origAttrs = getAttributes();
        if ( origAttrs != null ) {
            Attributes<String,Object> attrs = new Attributes(origAttrs);
            ro.setAttributes(attrs);
        }
        return ro;
    }
    
    public Map<String, Object> toMap() {
    	Map<String,Object> objMap = new HashMap<String,Object>();
    	objMap.put("instance", _instance);
    	objMap.put("identity", _identity);
    	objMap.put("uuid", _uuid);
    	objMap.put("name", _name);
    	objMap.put("objectType", _objectType);
    	objMap.put("incomplete", new Boolean(_incomplete));
    	objMap.put("incremental", new Boolean(_incremental));
    	objMap.put("delete", new Boolean(_delete));
    	objMap.put("remove", new Boolean(_remove));
    	if(_attributes != null)
    		objMap.put("attributes", _attributes.getMap());
    	return objMap;	
    }
    
    public void fromMap(Map map) {
    	if (map != null) {
            _identity = (String )map.get("identity");
            _uuid = (String )map.get("uuid");
            _name = (String )map.get("name");
            _objectType = (String )map.get("objectType");
            _instance = ((String )map.get("instance") );

            Object o = map.get("attributes");
            if (o != null && o instanceof Map) {
                _attributes = new Attributes<String,Object>();
                _attributes.putAll((Map)o);
            }

            _incomplete = Util.otob(map.get("incomplete"));
            _incremental = Util.otob(map.get("incremental"));
            _delete = Util.otob(map.get("delete"));
            _remove = Util.otob(map.get("remove"));
        }
    }

}
