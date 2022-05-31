/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */


package sailpoint.object;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;

import sailpoint.tools.Util;
import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;

/**
 *
 * @author <a href="mailto:dan.smith@sailpoint.com">Dan Smith</a>
 *
 * Based on the IdentityItem object, which is an XML Object but
 * not a SailPointObject.
 * 
 * Application is left of the base model, because derivatives
 * that are needed, Application references, or possibly just a string
 * object for archive objects.
 * 
 *
 */
@XMLClass
public class PersistentIdentityItem extends SailPointObject {

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    private static final long serialVersionUID = -61388129821327261L;
    
    /**
     * Instance identifier for template applications.
     */
    String _instance;

    /**
     * Native identity of the application account link.
     */
    String _nativeIdentity;
    
    /**
     * DisplayName of the account that holds this item.
     */
    String _displayName;

    /**
     * Name of an attribute or target of a permission.
     * TODO: Target names for unstructured targets can also
     * have a display name, should we allow that to be used here?
     * Needs coordination between TargetAggregator and Entitlizer.
     */
    String _name;

    /**
     * Value of an attribute or rights of a permission.
     * Rights are represented as a CVS string.
     */
    Object _value;
   
    /**
     * Annotation of the permission. Undefined for attributes.
     */
    String _annotation;

    /**
     * Optional time at which a requested item will be given.
     * Sometimes known as a "sunrise" date.
     * This is only relevant if _operation is "Add".
     */
    Date _startDate;

    /**
     * Optional time at which a requested item will be taken away.
     * Sometimes known as a "sunset" date.
     */
    Date _endDate;
    
    /**
     * A bag of attributes that can store any other interesting information.
     * 
     */
    Attributes<String,Object> _attributes;
    
    //////////////////////////////////////////////////////////////////////
    //
    // Constructors
    //
    //////////////////////////////////////////////////////////////////////

    public PersistentIdentityItem () { }
    
    /**
     * In this case the name field is not unique as
     * it holds the name of the attribute that
     * is being requested.
     */
    public boolean isNameUnique() {
        return false;
    }

    /**
     * Let the ScopeService know this class does not have a scope.
     */
    @Override
    public boolean hasAssignedScope() {
        return false;
    }
    
    //////////////////////////////////////////////////////////////////////
    //
    // Properties
    //
    //////////////////////////////////////////////////////////////////////

    @XMLProperty
    public String getInstance() {
        return _instance;
    }

    public void setInstance(String s) {
        _instance = s;
    }

    @XMLProperty
    public String getNativeIdentity() {
        return _nativeIdentity;
    }

    public void setNativeIdentity(String s) {
        _nativeIdentity = s;
    }
    
    public String getDisplayName() {
        return _displayName;
    }
    
    public void setDisplayName(String name) {
        _displayName = name;
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
    @SuppressWarnings("rawtypes")
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
    public void setXmlValueAttribute(String s) {
        _value = s;
    }

    /**
     * @exclude
     * @deprecated use {@link #getValue()}
     */
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
    public void setXmlValueElement(Object o) {
        _value = o;
    }

    // There is some problem with "isPermission returning a boolean
    // and "getPermission" returning a Permission with some jdks, hence
    // the not-so-good name for this method
    @SuppressWarnings("rawtypes")
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

    @XMLProperty
    public Date getEndDate() {
        return _endDate;
    }

    public void setEndDate(Date d) {
        _endDate = d;
    }

    @XMLProperty
    public Date getStartDate() {
        return _startDate;
    }

    public void setStartDate(Date d) {
        _startDate = d;
    }

    @XMLProperty(mode=SerializationMode.UNQUALIFIED)
    public Attributes<String,Object> getAttributes() {
        return _attributes;
    }

    public void setAttributes(Attributes<String,Object> attrs) {
        _attributes = attrs;
    }
    
    public void setAttribute(String name, Object value) {
        setPseudo(name, value);
    }
    
    public Object getAttribute(String name) {
        return Util.get(_attributes, name);
    }
    
    public String getStringAttribute(String name) {
        return Util.getString(_attributes, name);
    }
    
    
    //////////////////////////////////////////////////////////////////////  
    // 
    // Utilities
    //
    //////////////////////////////////////////////////////////////////////  

    protected void setPseudo(String name, Object value) {
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

    /**
     * Promote the current value to a List<String>.
     */
    @SuppressWarnings("unchecked")
    public List<String> getValueList() {
        List<String> list = null;
        
        if (_value instanceof List)
            list = (List<String>)_value;
        else 
        if (_value != null) {
            list = new ArrayList<String>();
            if (_value != null)
                list.add(_value.toString());            
        }
        return list;
    }

    public String getCsv() {
        String val = null;
        if ( _value != null ) {
            if ( _value instanceof String ) {
                val = (String)_value;    
            } else {
                val = Util.listToCsv(getValueList());
            }
        }
        return val;
    }
    
    /**
     * Returns a CSV of the value.
     * 
     * @return A CSV of the value.
     */
    public String getStringValue() {
        return getCsv();
    }
}
