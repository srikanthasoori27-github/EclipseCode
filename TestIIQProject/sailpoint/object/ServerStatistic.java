/*
 *  (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved.
 *
 *  Value of a captured statistic for a specific host at a given point in time
 *
 */

package sailpoint.object;

import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;

@XMLClass
public class ServerStatistic extends SailPointObject {


    public ServerStatistic() {

    }

    public ServerStatistic(MonitoringStatistic cfg) {
        setValueType(cfg.getValueType());
        setName(cfg.getName());
        setMonitoringStatistic(cfg);
    }

    //Name of the snapshot this was captured with. Used to group statistics
    String snapshotName;

    //Host in which this statistic was captured
    Server host;

    Attributes attributes;

    //String representation of the captured value
    String value;

    //Type of the value. Copied from the MonitoringStatistic at creation time
    String valueType;

    //Type of Statistic. Copied from the MonitoringStatistic at creation time
    String type;

    //Name/ID of the object referenced. Usually name, but some objects don't have unique names
    String target;

    //Type of Object referenced
    String targetType;

    //TODO: Tags, do we copy them over, or just join through the MonitoringStatistic? -rap

    MonitoringStatistic monitoringStatistic;

    @XMLProperty
    public String getSnapshotName() {
        return snapshotName;
    }

    public void setSnapshotName(String snapshotName) {
        this.snapshotName = snapshotName;
    }

    @XMLProperty( mode= SerializationMode.REFERENCE)
    public Server getHost() {
        return host;
    }

    public void setHost(Server host) {
        this.host = host;
    }

    @XMLProperty(mode=SerializationMode.UNQUALIFIED)
    public Attributes getAttributes() {
        return attributes;
    }

    public void setAttributes(Attributes attributes) {
        this.attributes = attributes;
    }

    @XMLProperty
    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    @XMLProperty
    public String getValueType() {
        return valueType;
    }

    public void setValueType(String valueType) {
        this.valueType = valueType;
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

    @XMLProperty( mode= SerializationMode.REFERENCE, xmlname = "MonitoringStatisticRef")
    public MonitoringStatistic getMonitoringStatistic() {
        return monitoringStatistic;
    }

    public void setMonitoringStatistic(MonitoringStatistic stat) {
        this.monitoringStatistic = stat;
    }

    /**
     * Let the ScopeService know this class does not have a scope.
     */
    @Override
    public boolean hasAssignedScope() {
        return false;
    }

    @Override
    public boolean isNameUnique() { return false; }

}
