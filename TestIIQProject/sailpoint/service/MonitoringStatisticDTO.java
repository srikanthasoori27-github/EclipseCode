/*
 *  (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.service;

import sailpoint.tools.Util;
import sailpoint.web.UserContext;

import java.util.Map;

public class MonitoringStatisticDTO extends BaseDTO {

    String _name;

    String _displayName;

    String _displayableName;

    String _description;

    boolean _template;

    String _type;


    public MonitoringStatisticDTO(Map<String, Object> props, UserContext context) {
        super((String) props.get("id"));
        _name = (String) props.get("name");
        _displayName = (String) props.get("displayName");
        _displayableName = Util.isNotNullOrEmpty(_displayName) ? _displayName : _name;
        _description = (String) props.get("description");
        _template = (Boolean)props.get("template");
        _type = (String) props.get("type");
        //TODO: Attributes? Can these be included in projection search?
    }

    public String getName() { return _name; }
    public void setName(String s) { _name = s; }

    public String getDisplayName() { return _displayName; }
    public void setDisplayName(String s) { _displayName = s; }

    public String getDisplayableName() { return _displayableName; }
    public void setDisplayableName(String s) { _displayableName = s; }

    public String getDescription() { return _description; }
    public void setDescription(String s) { _description = s; }

    public boolean isTemplate() { return _template; }
    public void setTemplate(boolean b) { this._template = b; }

    public String getType() { return _type; }
    public void setType(String s) { this._type = s; }

}
