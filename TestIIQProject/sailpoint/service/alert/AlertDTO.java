/*
 *  (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.service.alert;

import sailpoint.object.Alert;
import sailpoint.object.AlertAction;
import sailpoint.object.Application;
import sailpoint.object.AttributeDefinition;
import sailpoint.object.Attributes;
import sailpoint.object.ObjectAttribute;
import sailpoint.object.ObjectConfig;
import sailpoint.object.Schema;
import sailpoint.service.alert.AlertActionDTO;
import sailpoint.service.alert.AlertAttributeDTO;
import sailpoint.tools.Util;
import sailpoint.service.BaseDTO;
import sailpoint.web.UserContext;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Created by ryan.pickens on 8/15/16.
 */
public class AlertDTO extends BaseDTO {

    String _name;

    String _displayName;

    String _targetId;

    String _targetType;

    String _targetDisplayName;

    String _source;

    String _type;

    Date _alertDate;

    Date _created;

    String _nativeId;

    Date _lastProcessed;

    List<AlertAttributeDTO> _alertAttributes;

    List<AlertActionDTO> _actions;

    public AlertDTO(Map<String, Object> alertProps, UserContext context) {
        super((String) alertProps.get("id"));
        String name = (String) alertProps.get("name");
        _name = Util.stripLeadingChar(name, '0');
        _displayName = (String) alertProps.get("displayName");
        _targetId = (String) alertProps.get("targetId");
        _targetType = (String) alertProps.get("targetType");
        _targetDisplayName = (String) alertProps.get("targetDisplayName");
        _source = (String) alertProps.get("source");
        _type = (String) alertProps.get("type");
        _alertDate = (Date) alertProps.get("alertDate");
        _created = (Date) alertProps.get("created");
        _nativeId = (String) alertProps.get("nativeId");
        _lastProcessed = (Date) alertProps.get("lastProcessed");
    }

    public AlertDTO(Alert object, UserContext context) {
        super(object.getId());
        _name = Util.stripLeadingChar(object.getName(), '0');
        _displayName = object.getDisplayName();
        _targetId = object.getTargetId();
        _targetType = object.getTargetType();
        _targetDisplayName = object.getTargetDisplayName();
        _source = object.getSource() != null ? object.getSource().getName() : "";
        _type = object.getType();
        _alertDate = object.getAlertDate();
        _created = object.getCreated();
        _nativeId = object.getNativeId();
        _lastProcessed = object.getLastProcessed();

        _alertAttributes = getAttributeDtos(object, context);

        _actions = getActionDtos(object, context);
    }

    public String getName() {
        return _name;
    }

    public void setName(String _name) {
        this._name = _name;
    }

    public String getDisplayName() {
        return _displayName;
    }

    public void setDisplayName(String _displayName) {
        this._displayName = _displayName;
    }

    public String getTargetId() {
        return _targetId;
    }

    public void setTargetId(String _targetId) {
        this._targetId = _targetId;
    }

    public String getTargetType() {
        return _targetType;
    }

    public void setTargetType(String _targetType) {
        this._targetType = _targetType;
    }

    public String getTargetDisplayName() {
        return _targetDisplayName;
    }

    public void setTargetDisplayName(String _targetDisplayName) {
        this._targetDisplayName = _targetDisplayName;
    }

    public String getSource() {
        return _source;
    }

    public void setSource(String _source) {
        this._source = _source;
    }

    public String getType() {
        return _type;
    }

    public void setType(String _type) {
        this._type = _type;
    }

    public Date getAlertDate() {
        return _alertDate;
    }

    public void setAlertDate(Date _alertDate) {
        this._alertDate = _alertDate;
    }

    public String getNativeId() {
        return _nativeId;
    }

    public void setNativeId(String _nativeId) {
        this._nativeId = _nativeId;
    }

    public Date getLastProcessed() {
        return _lastProcessed;
    }

    public void setLastProcessed(Date _lastProcessed) {
        this._lastProcessed = _lastProcessed;
    }

    public Date getCreated() { return _created; }

    public void setCreated(Date s) { _created = s; }

    public List<AlertAttributeDTO> getAlertAttributes() { return _alertAttributes; }

    public void setAlertAttributes(List<AlertAttributeDTO> atts) { _alertAttributes = atts; }

    public List<AlertActionDTO> getActions() { return _actions; }

    public void setActions(List<AlertActionDTO> actions) { _actions = actions; }

    protected List<AlertAttributeDTO> getAttributeDtos(Alert a,  UserContext context) {
        List<AlertAttributeDTO> dtos = new ArrayList<>();

        Application app = a.getSource();
        Schema schema = null;
        if (app != null) {
            schema = app.getSchema(a.getType());
            if (schema == null) {
                //Fall back to alert schema
                schema = app.getSchema(Schema.TYPE_ALERT);
            }
        }

        ObjectConfig config = ObjectConfig.getObjectConfig(Alert.class);

        if (a.getAttributes() != null) {
            Attributes<String, Object> atts = a.getAttributes();
            for (String key : Util.safeIterable(atts.keySet())) {
                AlertAttributeDTO dto;
                boolean multiValued = false;
                boolean extendedAtt = false;
                boolean schemaAtt = false;

                String displayName = key;

                String description = "";

                //See if this is an extended Attribute
                if (config != null && config.hasObjectAttribute(key)) {
                    ObjectAttribute objAtt = config.getObjectAttribute(key);
                    extendedAtt = true;
                    multiValued = objAtt.isMulti();
                    displayName = objAtt.getDisplayableName(context.getLocale());
                    description = objAtt.getDescription();
                }

                if (schema != null && schema.getAttributeDefinition(key) != null) {
                    schemaAtt = true;

                    AttributeDefinition attDef = schema.getAttributeDefinition(key);
                    if (!extendedAtt) {
                        multiValued = attDef.isMulti();
                        displayName = attDef.getDisplayableName(context.getLocale());
                    }

                    if (Util.isNullOrEmpty(description)) {
                        description = attDef.getDescription();
                    }
                }

                dto = new AlertAttributeDTO(key, atts.get(key));
                dto.setDisplayName(displayName);
                dto.setFoundInSchema(schemaAtt);
                dto.setExtendedAttribute(extendedAtt);
                dto.setDescription(description);
                dtos.add(dto);

            }
        }

        return dtos;
    }

    protected List<AlertActionDTO> getActionDtos(Alert a, UserContext context) {
        List<AlertActionDTO> actions = new ArrayList<>();

        for (AlertAction action : Util.safeIterable(a.getActions())) {
            actions.add(new AlertActionDTO(action, context));
        }

        return actions;
    }
}
