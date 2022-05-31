/*
 *  (c) Copyright 2017 SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.service;

import sailpoint.object.Attributes;
import sailpoint.object.MonitoringStatistic;
import sailpoint.object.ServerStatistic;
import sailpoint.web.UserContext;

import java.util.Map;

public class ServerStatisticDTO extends BaseDTO {

    private String name;
    private String value;
    private String displayName;
    private String valueType;
    private String type;
    private String snapshotName;
    private String serverName;
    private String serverId;
    private String target;
    private String targetType;
    private String created;
    private String renderer;

    public ServerStatisticDTO(ServerStatistic object, UserContext context) {

        super(object.getId());

        name = object.getName();
        value = object.getValue();
        valueType = object.getValueType();
        snapshotName = object.getSnapshotName();
        if (object.getHost() != null) {
            serverName = object.getHost().getName();
            serverId = object.getHost().getId();
        }
        target = object.getTarget();
        targetType = object.getTargetType();
        created = object.getCreated() != null ? String.valueOf(object.getCreated().getTime()) : null;

        //Get info from MonitoringStatistic
        if (object.getMonitoringStatistic() != null) {
            displayName = object.getMonitoringStatistic().getDisplayName();
            renderer = object.getMonitoringStatistic().getStringAttributeValue(MonitoringStatistic.ATTR_VALUE_RENDERER);
            type = object.getMonitoringStatistic().getType();
        }
    }

    public ServerStatisticDTO(Map<String, Object> serverStatisticProps, UserContext context) {
        super((String) serverStatisticProps.get("id"));
        name = (String)serverStatisticProps.get("name");
        value = (String)serverStatisticProps.get("value");
        displayName = (String)serverStatisticProps.get("displayName");
        type = (String)serverStatisticProps.get("type");
        valueType = (String)serverStatisticProps.get("valueType");
        snapshotName = (String)serverStatisticProps.get("snapshotName");
        serverName = (String)serverStatisticProps.get("serverName");
        serverId = (String)serverStatisticProps.get("serverId");
        target = (String)serverStatisticProps.get("target");
        targetType = (String)serverStatisticProps.get("targetType");
        created = (String) serverStatisticProps.get("created");
        Attributes atts = (Attributes)serverStatisticProps.get("statisticAttributes");
        if (atts != null) {
            renderer = atts.getString(MonitoringStatistic.ATTR_VALUE_RENDERER);
        }


    }

    //
    // Getters and Setters
    //

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getValueType() {
        return valueType;
    }

    public void setValueType(String valueType) {
        this.valueType = valueType;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getSnapshotName() {
        return snapshotName;
    }

    public void setSnapshotName(String snapshotName) {
        this.snapshotName = snapshotName;
    }

    public String getServerName() {
        return serverName;
    }

    public void setServerName(String serverName) {
        this.serverName = serverName;
    }

    public String getServerId() {
        return serverId;
    }

    public void setServerId(String serverId) {
        this.serverId = serverId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getTarget() {
        return target;
    }

    public void setTarget(String target) {
        this.target = target;
    }

    public String getTargetType() {
        return targetType;
    }

    public void setTargetType(String targetType) {
        this.targetType = targetType;
    }

    public String getCreated() { return created; }

    public void setCreated(String d) {
        this.created = d;
    }

    public String getRenderer() { return renderer; }

    public void setRenderer(String s) { this.renderer = s; }
}
