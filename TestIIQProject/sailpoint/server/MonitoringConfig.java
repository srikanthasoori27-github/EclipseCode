/* (c) Copyright 2017 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.server;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import sailpoint.api.SailPointContext;
import sailpoint.object.MonitoringStatistic;
import sailpoint.object.Server;
import sailpoint.object.ServiceDefinition;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Describes the configuration of statistic monitoring to be followed, usually for a given Server
 */
public class MonitoringConfig {

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    private static Log log = LogFactory.getLog(MonitoringConfig.class);

    private int _retentionPeriodDays;
    private List<MonitoringStatistic> _monitoringStatistics;
    private int _pollingInterval;
    private List<String> _monitoredApplications;
    private List<String> _monitoredModules;

    /**
     * Retention period which means forever i.e. never prune
     */
    public static final int RETENTION_FOREVER = 0; // 0 means retain forever


    /**
     * A factory method used to retrieve the MonitoringConfig for the given Server
     * @param context persistence context from which to retrieve any objects
     * @param server the Server for which to get the monitoring config
     * @param serviceDefinition the ServiceDefinition of the Monitoring service
     * @return the MonitoringConfig of the given Server
     * @throws GeneralException any error occurs
     */
    public static MonitoringConfig build(SailPointContext context, Server server, ServiceDefinition serviceDefinition)
            throws GeneralException {

        MonitoringConfig monitoringConfig = new MonitoringConfig();
        monitoringConfig.load(context, server, serviceDefinition);

        return monitoringConfig;
    }

    // Accessors
    //

    public int getRetentionPeriodDays() {
        return _retentionPeriodDays;
    }

    public List<MonitoringStatistic> getMonitoringStatistics() {
        return _monitoringStatistics;
    }

    public int getPollingInterval() { return _pollingInterval; }

    public List<String> getMonitoredApplications() { return _monitoredApplications; }

    public List<String> getMonitoredModules() { return _monitoredModules; }


    //
    // Constructors
    //

    /**
     * a private constructor used only by the factory method
     */
    private MonitoringConfig() {}

    /**
     * Build the monitoring configuration, following precedence rules:
     *   - first, try Server's "monitoringConfig" map attribute
     *   - otherwise, try MonitorService's "monitorConfig" map attribute
     *   - otherwise, apply hard-coded Java defaults
     *
     * @param context persistence context from which to retrieve any objects
     * @param server the Server for which to get the monitoring config
     * @param serviceDefinition the ServiceDefinition of the Monitoring service
     */
    private void load(SailPointContext context, Server server, ServiceDefinition serviceDefinition)
            throws GeneralException
    {
        Map serverMonitoringConfig = (Map)server.get(Server.ATT_MONITORING_CFG);
        Map serviceMonitoringConfig = (Map)serviceDefinition.get(Server.ATT_MONITORING_CFG);

        _retentionPeriodDays = resolveRetentionPeriod(serverMonitoringConfig, serviceMonitoringConfig);
        _pollingInterval = resolvePollingInterval(serverMonitoringConfig, serviceDefinition);
        _monitoringStatistics = resolveMonitoringStatistics(context, serverMonitoringConfig, serviceMonitoringConfig);
        _monitoredApplications = resolveMonitoredApplications(serverMonitoringConfig, serviceMonitoringConfig);
        _monitoredModules = resolveMonitoredModules(serverMonitoringConfig, serviceMonitoringConfig);
    }

    /**
     * Get the list of MonitoringStatistic configured for the given server, following precedence rules:
     *   - first, if serverMonitoringConfig is not null, retrieve from its "monitoringConfig" map attribute
     *   - otherwise, if serviceMonitoringConfig is not null, retrieve from its "monitoringConfig" map attribute
     *   - otherwise, return empty list
     * @param context persistence context from which to retrieve any objects
     * @param serverMonitoringConfig the "monitoringConfig" attribute of the Server
     * @param serviceMonitoringConfig the "monitoringConfig" attribute of the Monitoring service definition
     */
    private List<MonitoringStatistic> resolveMonitoringStatistics(SailPointContext context, Map serverMonitoringConfig, Map serviceMonitoringConfig)
            throws GeneralException
    {
        List statNameList = null;

        if (serverMonitoringConfig != null) {
            if (serverMonitoringConfig.containsKey(Server.ATT_MONITORING_CFG_STATS)) {
                Object stats = Util.get(serverMonitoringConfig, Server.ATT_MONITORING_CFG_STATS);
                if (stats != null) {
                    if (stats instanceof List) {
                        statNameList = (List)stats;
                    }
                } else {
                    //Empty List
                    statNameList = Collections.emptyList();
                }
            } else {
                //Partial Override
                statNameList = getDefaultMonitoringStatistics(serviceMonitoringConfig);
            }
        }
        else {
            statNameList = getDefaultMonitoringStatistics(serviceMonitoringConfig);
        }
        List<MonitoringStatistic> resultStatList = populateMonitoringStatsFromList(context, statNameList);
        return resultStatList;
    }

    public static List<String> getDefaultMonitoringStatistics(Map serviceMonitoringConfig) {
        List statNameList = null;
        Object stats = Util.get(serviceMonitoringConfig, Server.ATT_MONITORING_CFG_STATS);
        if (stats != null) {
            if (stats instanceof List) {
                statNameList = (List)stats;
            }
        }
        return statNameList;
    }

    /**
     * Given a list of names, return the list of the named MonitoringStatistic objects.  If there is
     * no MonitoringStatistic object for a name, then that that name is skipped.
     * @param context persistence context from which to retrieve any objects
     * @param nameList the list of names of MonitoringStatistic objects to fetch
     * @return the list of the named MonitoringStatistic objects
     * @throws GeneralException any error occurs
     */
    private List<MonitoringStatistic> populateMonitoringStatsFromList(SailPointContext context, List nameList) throws GeneralException {
        List<MonitoringStatistic> resultStatList = new ArrayList<MonitoringStatistic>();
        if (nameList != null) {
            for(Object nameObj : nameList) {
                if (nameObj != null) {
                    String name = nameObj.toString();
                    MonitoringStatistic stat = context.getObjectByName(MonitoringStatistic.class, name);
                    if (stat != null) {
                        resultStatList.add(stat);
                    }
                    else {
                        log.warn("Unknown monitoring statistic '" + name + "' ignored.");
                    }
                }
            }
        }
        return resultStatList;
    }

    private int resolveRetentionPeriod(Map serverMonitoringConfig, Map serviceMonitoringConfig) {
        int period;
        int retentionServer = Util.getInt(serverMonitoringConfig, Server.ATT_MONITORING_CFG_RETENTION);
        if (retentionServer > 0) {
            // found in Server
            period = retentionServer;
        }
        else {
            period = getDefaultRetentionPeriod(serviceMonitoringConfig);
        }

        return period;
    }

    public static int getDefaultRetentionPeriod(Map serviceMonitoringConfig) {
        int retentionService = Util.getInt(serviceMonitoringConfig, Server.ATT_MONITORING_CFG_RETENTION);
        if (retentionService > 0) {
            // found in servicedefinition
            return retentionService;
        }
        else {
            // not found.  Use default
            return RETENTION_FOREVER;
        }
    }

    private int resolvePollingInterval(Map serverMonitoringConfig, ServiceDefinition serviceDef) {
        int interval;
        int intervalServer = Util.getInt(serverMonitoringConfig, Server.ATT_MONITORING_CFG_POLL_INTERVAL);
        if (intervalServer > 0) {
            // found in Server
            interval = intervalServer;
        }
        else {
            interval = getDefaultPollingInterval(serviceDef);
        }

        return interval;
    }

    public static int getDefaultPollingInterval(ServiceDefinition serviceDef) {
        int intervalService = serviceDef.getInterval();
        if (intervalService > 0) {
            // found in servicedefinition
            return intervalService;
        }
        else {
            // not found.  Use default
            return MonitoringService.DEFAULT_INTERVAL;
        }
    }

    private List<String> resolveMonitoredApplications(Map serverMonitoringConfig, Map serviceMonitoringConfig) {
        List<String> monitoredApps = new ArrayList<String>();
        if (serverMonitoringConfig!= null && serverMonitoringConfig.containsKey(Server.ATT_MONITORING_CFG_APPS)) {
            monitoredApps = Util.getStringList(serverMonitoringConfig, Server.ATT_MONITORING_CFG_APPS);
        } else {
            //Not Found on server. Check serviceDef attributes
            monitoredApps = getDefaultMonitoredApplications(serviceMonitoringConfig);

        }
        return monitoredApps;
    }

    public static List<String> getDefaultMonitoredApplications(Map serviceConfig) {
        List<String> monitoredApps = new ArrayList<>();
        List<String> serviceApps = Util.getStringList(serviceConfig, Server.ATT_MONITORING_CFG_APPS);
        if (serviceApps != null) {
            monitoredApps = serviceApps;
        }
        return monitoredApps;
    }

    private List<String> resolveMonitoredModules(Map serverMonitoringConfig, Map serviceMonitoringConfig) {
        List<String> monitoredModules = new ArrayList<String>();
        if (serverMonitoringConfig!= null && serverMonitoringConfig.containsKey(Server.ATT_MONITORING_CFG_MODULES)) {
            monitoredModules = Util.getStringList(serverMonitoringConfig, Server.ATT_MONITORING_CFG_MODULES);
        } else {
            //Not Found on server. Check serviceDef attributes
            monitoredModules = getDefaultMonitoredModules(serviceMonitoringConfig);

        }
        return monitoredModules;
    }

    public static List<String> getDefaultMonitoredModules(Map serviceConfig) {
        List<String> monitoredModules = new ArrayList<>();
        List<String> serviceModules = Util.getStringList(serviceConfig, Server.ATT_MONITORING_CFG_MODULES);
        if (serviceModules != null) {
            monitoredModules = serviceModules;
        }
        return monitoredModules;
    }

}
