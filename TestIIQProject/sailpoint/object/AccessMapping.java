/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
/*
 * @author <a href="mailto:dan.smith@sailpoint.com">Dan Smith</a>
 */

package sailpoint.object;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import sailpoint.tools.Util;
import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;

@XMLClass
public class AccessMapping {

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////
    
    private static final long serialVersionUID = 1L;

    /**
     * The database id. This will be automatically set by the persistent
     * store upon saving.
     */
    private String _id;

    /**
     * List of native object ids.
     */
    private List<String> _nativeObjectIds;

    /**
     * CSV List of Rights assigned to the associated object.
     */
    private String _rights;

    /**
     * boolean used to determine if the rights are inherited
     */
    private boolean _inherited;

    /**
     * boolean representing allow Permission vs deny Permission
     */
    private boolean _allow;

    /**
     * Effectiveness of the permissions
     * 0 - ineffective
     * 1 - partially effective
     * 2 - fully effective
     */
    private int _effective = 2;

    /**
     * collectorId
     */

    /**
     * Transient map that holds metadata that can be used in transforming 
     * this association into a permission object.
     */
     private Map _attributes;   

    //////////////////////////////////////////////////////////////////////
    //
    // Constructors
    //
    //////////////////////////////////////////////////////////////////////

    public AccessMapping() {
        _id = null;
        _nativeObjectIds = null;
        _rights = null;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Properties
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * The database id. This is automatically set by the persistent
     * store upon saving.
     */
    @XMLProperty
    public String getId() {
        return _id;
    }

    public void setId(String id) {
        _id = id;
    }

    /**
     * CSV List of Rights assigned to the associated object.
     */
    @XMLProperty(xmlname="rights")
    public String getRights() {
        return _rights;
    }

    public void setRights(String rights) {
        _rights = rights;
    }

    public List<String> getRightsList() {
        List<String> rights = null;
        if ( _rights != null) {
            rights = Util.csvToList(_rights);
        }
        return rights;
    } 
 
    public void setRightsList(List<String> rights) {
        _rights = null;
        if ( rights != null ) {
            _rights = Util.listToCsv(rights);
        }
    }

    /**
     * List of native object ids.
     */
    @XMLProperty(mode=SerializationMode.CANONICAL)
    public List<String> getNativeIds() {
        return _nativeObjectIds;
    }

    public void setNativeIds(List<String> nativeIds) {
        _nativeObjectIds = nativeIds;
    }

    public void addNativeId(String id) {
        if (getNativeIds() == null) {
            setNativeIds(new ArrayList<String>());
        }
        getNativeIds().add(id);
    }

    @XMLProperty(xmlname="AccessAttributes")
    @SuppressWarnings("unchecked")
    public Map<String,Object> getAttributes() {
        return _attributes;
    }

    public void setAttributes(Map<String,Object> attrs) {
        _attributes = attrs;
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

    public Object getAttribute(String name) {
        return (_attributes != null) ? _attributes.get(name) : null;
    }

    @XMLProperty
    public boolean isInherited() {
        return _inherited;
    }

    public void setInherited(boolean _inherited) {
        this._inherited = _inherited;
    }

    @XMLProperty
    public int getEffective() {
        return _effective;
    }

    public void setEffective(int _effective) {
        this._effective = _effective;
    }

    @XMLProperty
    public boolean isAllow() {
        return _allow;
    }

    public void setAllow(boolean _isAllow) {
        this._allow = _isAllow;
    }

}
