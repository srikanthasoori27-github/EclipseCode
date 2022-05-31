/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * Part of the compiled model built by AnnotationSerializer.
 * Represents each of the serializable properties of a class.
 */

package sailpoint.tools.xml;

import java.lang.reflect.Method;

class Property {

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    private boolean _required;
    private String  _name;
    private String  _xmlName;
    private Method  _getter;
    private Method  _setter;
    private boolean _legacy;
    private int     _ordinal;
    private SerializationMode _serializationMode;
    private Class _type;
    private Class _listElementType;
        
    //////////////////////////////////////////////////////////////////////
    //
    // Constructors
    //
    //////////////////////////////////////////////////////////////////////

    public Property() {
    }
        
    //////////////////////////////////////////////////////////////////////
    //
    // Properties
    //
    //////////////////////////////////////////////////////////////////////

    public String getName() {
        return _name;
    }
         
    public void setName(String name) {
        _name = name;
    }
        
    public String getXmlName() {
        return _xmlName;
    }
        
    public void setXmlName(String xmlName) {
        _xmlName = xmlName;
    }
        
    public Method getGetter() {
        return _getter;
    }
        
    public void setGetter(Method getter) {
        _getter = getter;
    }
        
    public Method getSetter() {
        return _setter;
    }
        
    public void setSetter(Method setter) {
        _setter = setter;
    }
        
    public boolean isLegacy() {
        return _legacy;
    }
        
    public void setLegacy(boolean legacy) {
        _legacy = legacy;
    }
         
    public int getOrdinal() {
        return _ordinal;
    }
        
    public void setOrdinal(int ordinal) {
        _ordinal = ordinal;
    }
        
    public SerializationMode getSerializationMode() {
        return _serializationMode;
    }
        
    public void setSerializationMode(SerializationMode mode) {
        _serializationMode = mode;
    }
        
    public Class getType() {
            return _type;
    }
    
    public void setType(Class type) {
        _type = type;
    }
        
    public Class getListElementType() {
        return _listElementType;
    }
        
    public void setListElementType(Class type) {
        _listElementType = type;
    }
        
    public boolean isRequired() {
        return _required;
    }
        
    public void setRequired(boolean required) {
        _required = required;
    }


}
    

