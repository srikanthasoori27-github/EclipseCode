/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.object;

import java.util.Date;

import sailpoint.tools.Util;
import sailpoint.tools.xml.AbstractXmlObject;
import sailpoint.tools.xml.IXmlEqualable;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;

/**
 * A class encapsulating information about how an extended
 * attribute got its value.
 *
 * Originally this was designed to track manual modifications to 
 * the attribute made from the UI so they could be preserved
 * on the next aggregation or refresh.
 *
 * It was later extended to track of the source of an attribute
 * in cases where there is more than one AttributeSource
 * (in practice only for identities).
 *
 * These two uses are mutually exclusive, when "userName" 
 * is non-null this represents the state of a manual edit.
 * The "modified" property will have the data of that edit
 * and "lastValue" will hold the previous value.
 *
 * When "userName" is null this means that the attribute was 
 * derived automatically from an AttributeSource and the "source"
 * property will have a description of that source.
 * This is only used in cases where there are multiple sources
 * and you need to know when you can null the value.  With
 * multiple sources a null value is only promoted
 * if the current value was promoted from the same source
 * that is now providing a null value.
 *
 * The value for "source" will be the derived "key" property of the
 * AttributeSource that supplied the value for this attribute.
 *
 */
@XMLClass
public class AttributeMetaData extends AbstractXmlObject implements IXmlEqualable<AttributeMetaData>
{

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * The name of the attribute.
     */
    String _attribute;
  
    /**
     * Keep the last time the attribute was changed for auditing.
     */ 
    Date _modified; 

    /**
     * Keep the name of the user that update the object.
     */ 
    String _userName;

    /**
     * The key of an AttributeSource if the value was not manually
     * assigned.
     */
    String _source;

    /**
     * The value that this attribute had prior to the last update.
     * This will be used by the aggregation process to help 
     * understand if the value returned by a Rule should be overridden
     * or if the value should be kept.
     * There are two rules here:
     *
     * Permanent : The value is never changed
     * 
     * Temporary : The "changed" value is kept until the original value
     *             prior to the update has changed.
     */ 
    Object _lastValue;

    //////////////////////////////////////////////////////////////////////
    //
    // Constructor
    //
    //////////////////////////////////////////////////////////////////////

    public AttributeMetaData() { }

    public AttributeMetaData(String attribute, String user, 
                             Object currentValue) {
        _attribute = attribute; 
        _userName = user;
        setLastValue(currentValue);
    }

    public boolean contentEquals(AttributeMetaData other) {

        return
        Util.nullSafeEq(getAttribute(), other.getAttribute(), true)
        &&
        Util.nullSafeEq(getModified(), other.getModified(), true)
        &&
        Util.nullSafeEq(getSource(), other.getSource(), true)
        &&
        Util.nullSafeEq(getLastValue(), other.getLastValue(), true);
    }
    
    //////////////////////////////////////////////////////////////////////
    //
    // Properties
    //
    //////////////////////////////////////////////////////////////////////

    @XMLProperty
    public void setAttribute(String name) {
        _attribute = name;
    }

    /**
     * The name of the attribute.
     */
    public String getAttribute() {
        return _attribute;
    }

    /**
     * The date the attribute was last modified.
     */ 
    @XMLProperty
    public Date getModified() {
        return _modified;
    }

    public void setModified(Date mod) {
        _modified = mod;
    }

    public void incrementModified() {
        setModified(new Date());
    }

    @XMLProperty
    public void setUser(String userName) {
        _userName = userName;
    }

    /**
     * The name of the user that manually edited the attribute.
     * This will be null if the attribute was updated by the system.
     */ 
    public String getUser() {
        return _userName;
    }

    @XMLProperty
    public void setSource(String s) {
        _source = s;
    }

    /**
     * the name of the user that modified the object.
     */ 
    public String getSource() {
        return _source;
    }

    /**
     * The value that this attribute had prior to the last manual update.
     * This will be used by the aggregation process to help 
     * understand if the value returned by a Rule should be overridden
     * or if the value should be kept.
     * 
     * There are two rules here:
     * <pre>
     * Permanent : The value is never changed
     * 
     * Temporary : The "changed" value is kept until the original value
     *             prior to the update has changed.
     * </pre>
     */ 
    @XMLProperty
    public Object getLastValue() {
        return _lastValue;
    }

    public void setLastValue(Object val) {
        _modified = new Date();
        _lastValue = val;
    }

}


