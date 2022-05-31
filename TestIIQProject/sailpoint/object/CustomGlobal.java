/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * A class used to maintain a static Map of custom attributes.
 * This can be used as a way to maintain global variables across
 * calls to custom rules.
 * 
 * Author: Jeff
 *
 */
package sailpoint.object;

import java.util.Set;

/**
 * A class used to maintain a static Map of custom attributes.
 * This can be used as a way to maintain global variables across
 * calls to custom rules.
 */
public class CustomGlobal {

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * The attribute map.
     */
    static private Attributes<String,Object> _attributes;

    //////////////////////////////////////////////////////////////////////
    //
    // Accessors
    //
    //////////////////////////////////////////////////////////////////////

    static public Object get(String name) {
	Object value = null;
	if (_attributes != null)
	    value = _attributes.get(name);
	return value;
    }

    static public void put(String name, Object value) {
	if (_attributes == null)
	    _attributes = new Attributes<String,Object>();
	_attributes.put(name, value);
    }

    static public Object remove(String name) {
	return (_attributes != null) ? _attributes.remove(name) : null;
    }

    static public void clear() {
	if (_attributes != null) _attributes.clear();
    }

    static public int size() {
	return (_attributes != null) ? _attributes.size() : 0;
    }

    static public Set<String> keySet() {
	if (_attributes == null)
	    _attributes = new Attributes<String,Object>();
	return _attributes.keySet();
    }
}
