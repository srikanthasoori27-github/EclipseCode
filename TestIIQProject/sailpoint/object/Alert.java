/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.object;

import sailpoint.tools.Util;
import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLProperty;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 *
 *
 * Created by ryan.pickens on 6/16/16.
 */
public class Alert extends SailPointObject {


    String _displayName;

    Attributes<String,Object> _attributes;

    //Id of the related SailPointObject
    String _targetId;

    //Type of the SailPoint Object. This should be class simple name
    String _targetType;

    String _targetDisplayName;

    Application _source;

    //Type of the Alert. Should this be a defined set of values, or arbitrarily set?
    String _type;

    List<AlertAction> _actions;

    Date _alertDate;

    String _nativeId;

    Date _lastProcessed;

    public Alert() {

    }

    @XMLProperty
    public String getDisplayName() { return _displayName; }

    public void setDisplayName(String s) { _displayName = s; }

    @XMLProperty
    public String getType() { return _type; }

    public void setType(String s) { this._type = s; }

    @XMLProperty(mode=SerializationMode.UNQUALIFIED)
    public Attributes<String, Object> getAttributes() { return _attributes; }

    public void setAttributes(Attributes<String, Object> atts) { _attributes = atts; }

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

    @XMLProperty(mode= SerializationMode.REFERENCE,xmlname="AlertSource")
    public Application getSource() { return _source; }

    public void setSource(Application app) { _source = app; }

    @XMLProperty(mode=SerializationMode.INLINE_LIST_UNQUALIFIED)
    public List<AlertAction> getActions() { return _actions; }

    public void setActions(List<AlertAction> actions) { _actions = actions; }

    public void addAction(AlertAction action) {
        if (getActions() == null) {
            _actions = new ArrayList<AlertAction>();
        }

        _actions.add(action);
    }

    @XMLProperty
    public Date getAlertDate() { return _alertDate; }

    public void setAlertDate(Date d) { _alertDate = d; }

    @XMLProperty
    public String getNativeId() { return _nativeId; }

    public void setNativeId(String id) { _nativeId = id; }

    @XMLProperty
    public String getTargetId() {
        return _targetId;
    }

    public void setTargetId(String _targetId) {
        this._targetId = _targetId;
    }

    @XMLProperty
    public String getTargetType() {
        return _targetType;
    }

    @XMLProperty
    public String getTargetDisplayName() { return _targetDisplayName; }

    public void setTargetDisplayName(String s) { _targetDisplayName = s; }

    public void setTargetType(String _targetType) {
        this._targetType = _targetType;
    }

    @XMLProperty
    public Date getLastProcessed() { return _lastProcessed; }

    public void setLastProcessed(Date d) { _lastProcessed = d; }

    /**
     * Let the ScopeService know this class does not have a scope.
     */
    @Override
    public boolean hasAssignedScope() {
        return false;
    }

    /**
     * This is the accessor required by the generic support for extended
     * attributes in SailPointObject. It is NOT an XML property.
     */
    public Attributes<String, Object> getExtendedAttributes() {
        return getAttributes();
    }

    /**
     * A static configuration cache used by the persistence manager to make
     * decisions about searchable extended attributes.
     */
    static private CacheReference objectConfig;

    public static ObjectConfig getObjectConfig() {

        ObjectConfig config = null;

        if (objectConfig == null)
            objectConfig = ObjectConfig.getCache(Alert.class);

        if (objectConfig != null)
            config = (ObjectConfig)objectConfig.getObject();

        return config;
    }

    @Override
    public void load() {
        if (_actions != null) {
            for (AlertAction action : Util.safeIterable(_actions)) {
                action.load();
            }
        }
        if (getOwner() != null) {
            getOwner().load();
        }
        if (getSource() != null) {
            getSource().load();
        }
    }

}
