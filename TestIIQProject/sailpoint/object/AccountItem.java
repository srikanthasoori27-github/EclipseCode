/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
/**
 * A class used to represent a single item (attribute or entitlement)
 * from an account.  These are currently only found within a RoleTarget
 * within a RoleDetection.  It is subclassed by IdentityItem which
 * adds information about the source application so the items for
 * an identity can be kept on a flat list.
 *
 * Author: Jeff
 * 
 * Try not to clutter this up with too much stuff.  It should be lean
 * and mean so it can be used in several places without dragging
 * in dependencies.  This needs to be "archival" so avoid references
 * to SailPointObjects.
 */
package sailpoint.object;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.tools.Util;
import sailpoint.tools.xml.AbstractXmlObject;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;
import sailpoint.tools.xml.SerializationMode;

/**
 * A class used to represent a single item (attribute or entitlement)
 * from an application account.
 */
@XMLClass
public class AccountItem extends AbstractXmlObject {
    
    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    private static final Log log = LogFactory.getLog(AccountItem.class);

    /**
     * True if this item represents a permission, false if this
     * is an attribute.
     * 
     * @ignore
     * TODO: A type field would be more extensible.
     */
    boolean _permission;

    /**
     * Name of an attribute or target of a permission.
     */
    String _name;

    /**
     * Value of an attribute or rights of a permission.
     * Rights are represented as a CVS string.
     */
    Object _value;
    
    /**
     * Display name of the attribute.  Undefined for permissions.
     */
    String _displayName;

    /**
     * Annotation of the permission.  Undefined for attributes.
     */
    String _annotation;

    //////////////////////////////////////////////////////////////////////
    //
    // Constructors
    //
    //////////////////////////////////////////////////////////////////////

    public AccountItem() {
    }

    public AccountItem(String name, Object value) {
        setValue(name, value);
    }

    public AccountItem(Permission perm) {
        setPermission(perm);
    }

    public AccountItem(Permission perm, Object value) {
        setPermission(perm, value);
    }

    /**
     * Special constructor used when downgrading from an IdentityItem to
     * an AccountItem.  This is for conversions only, it is assumed that
     * ownership of this value can be taken.
     */
    public AccountItem(IdentityItem src) {
        _permission = src.isPermission();
        _name = src.getName();
        _value = src.getValue();
        _displayName = src.getDisplayName();
        _annotation = src.getAnnotation();
    }

    public void setValue(String name, Object value) {
        _name = name;
        _value = value;
    }

    public void setPermission(Permission perm) {
        setPermission(perm, null);
    }

    /**
     * Set the permission information.
     * Used in cases where the value list should be overwritten
     * in the Permission, typically with fewer rights.
     */
    public void setPermission(Permission perm, Object value) {
        if (perm != null) {
            _permission = true;
            _name = perm.getTarget();
            _annotation = perm.getAnnotation();
            if (value == null) {
                // copy this so we can modify either side without corruption
                List<String> rights = perm.getRightsList();
                if (rights != null) {
                    List<String> myrights = new ArrayList<String>();
                    myrights.addAll(rights);
                    _value = myrights;
                }
            }
            else {
                _value = value;
            }
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Properties
    //
    //////////////////////////////////////////////////////////////////////

    @XMLProperty
    public boolean isPermission() {
        return _permission;
    }

    public void setPermission(boolean b) {
        _permission = b;
    }
        
    @XMLProperty
    public String getName() {
        return _name;
    }

    public void setName(String s) {
        _name = s;
    }

    public Object getValue() {
        return _value;
    }
        
    public void setValue(Object o) {
        _value = o;
    }
    
    @XMLProperty
    public String getDisplayName() {
        return _displayName;
    }
    
    public void setDisplayName(String displayName) {
        _displayName = displayName;
    }

    @XMLProperty(mode=SerializationMode.ELEMENT)
    public String getAnnotation() {
        return _annotation;
    }

    public void setAnnotation(String s) {
        _annotation = s;
    }

    //
    // Usual XML hackery for terseness in the common case
    //

    /**
     * @exclude
     * @deprecated use {@link #getValue()}
     */
    @Deprecated
    @XMLProperty(xmlname="value")
    public String getXmlValueAttribute() {
        String value = null;
        if (_value instanceof String)
            value = (String)_value;
        else if (_value instanceof Collection) {
            Collection col = (Collection)_value;
            if (col.size() == 1) {
                // ugh, Collection doesn't have get(int)
                Object[] elements = col.toArray();
                Object el = elements[0];
                if (el instanceof String) 
                    value = (String)el;
            }
        }
        return value;
    }

    /**
     * @exclude
     * @deprecated use {@link #setValue(Object)}
     */
    @Deprecated
    public void setXmlValueAttribute(String s) {
        _value = s;
    }

    /**
     * @exclude
     * @deprecated use {@link #getValue()}
     */
    @Deprecated
    @SuppressWarnings("deprecation")
    @XMLProperty(mode=SerializationMode.ELEMENT, xmlname="value")
    public Object getXmlValueElement() {
        Object value = null;
        String svalue = getXmlValueAttribute();
        if (svalue == null)
            value = _value;
        return value;
    }

    /**
     * @exclude
     * @deprecated use {@link #setValue(Object)}
     */
    @Deprecated
    public void setXmlValueElement(Object o) {
        _value = o;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Utilities
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Return true if one item is equal to another except for
     * the value.  Used to compress and filter duplicates in 
     * a ViolationDetails.
     *
     * @ignore
     * Note that the value is NOT included here, so this
     * is really a pretty specific comparator, should give it
     * a better name.
     */
    public boolean isEqual(AccountItem other) {
 
        return (_permission == other.isPermission() &&
                equal(_name, other.getName()) &&
                equal(_annotation, other.getAnnotation())
                );
    }

    public boolean equal(String s1, String s2) {
        boolean eq = false;
        if (s1 == null)
            eq = (s2 == null);
        else if (s2 != null)
            eq = s1.equals(s2);
        return eq;
    }

    /**
     * Promote the current value to a List<String>.
     */
    public List<String> getValueList() {
        List<String> list = null;
        if (_value instanceof List)
            list = (List<String>)_value;
        else if (_value != null) {
            list = new ArrayList<String>();
            if (_value != null)
                list.add(_value.toString());
            _value = list;
        }
        return list;
    }

    private List<String> forceValueList() {
        List<String> list = getValueList();
        if (list == null) {
            list = new ArrayList<String>();
            _value = list;
        }
        return list;
    }

    /**
     * Combine a value with the existing value.
     * The existing value is promoted to a list if it is not already.
     */
    public void assimilate(Object value) {
        if (value instanceof Collection) {
            Collection col = (Collection)value;
            if (col.size() > 0) {
                List<String> current = forceValueList();
                for (Object o : col) {
                    if (o != null) {
                        String s = o.toString();
                        if (!current.contains(s))
                            current.add(s);
                    }
                }
            }
        }
        else if (value != null) {
            List<String> list = forceValueList();
            String s = value.toString();
            if (!list.contains(s))
                list.add(s);
        }
    }

    public String getCsv() {
        return Util.listToCsv(getValueList());
    }

    /**
     * Convert permission information in this item into a Permission object.
     */
    public Permission getPermissionObject() {
        Permission p = new Permission();
        p.setTarget(_name);
        if (_value instanceof List)
            p.setRights(Util.listToCsv((List)_value));
        else if (_value != null)
            p.setRights(_value.toString());
        p.setAnnotation(_annotation); 
        return p;
    }


}
