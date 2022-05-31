package sailpoint.service;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import sailpoint.api.ObjectUtil;
import sailpoint.api.SailPointContext;
import sailpoint.object.Application;
import sailpoint.object.Filter;
import sailpoint.object.QueryOptions;

import sailpoint.object.Server;

import sailpoint.tools.Util;
import sailpoint.web.UserContext;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

public class ServerDTO extends BaseDTO {

    private static Log log = LogFactory.getLog(ServerDTO.class);

    private String name;
    private String description;
    boolean inactive;
    private List<String> includedServices;
    private List<String> excludedServices;
    private List<ServiceState> serviceStates;
    private Date heartbeat;
    String cpuUsage;
    String requestThreads;
    String taskThreads;
    String memoryUsage;
    String memoryUsagePercentage;
    String databaseResponseTime;
    String openFileCount;


    //List of enabled Statistic Names
    List<String> enabledStatistics;
    //Due to the nature of empty array serialization, need to determine if the Server's Monitoring config
    //Contains an empty/null entry for enabledStatistics. If containing empty/null entry, this means config
    //has been overriden to not monitor anything on this particular host
    boolean containsStatistics;
    Integer pollingInterval;
    Integer retention;

    List<SuggestObjectDTO> monitoredApplications;


    //Default values
    List<String> defaultStatistics;
    Integer defaultPollingInterval;
    Integer defaultRetention;

    /**
     * Map with name/value pairs for any extended attributes with value on the managed attribute
     */
    private Map<String, String> extendedAttributes;


    public ServerDTO(Server server, UserContext context) {
        if (server != null) {
            setId(server.getId());
            setName(server.getName());
            setDescription(server.getDescription());
            setInactive(server.isInactive());
            setHeartbeat(server.getHeartbeat());
            setCpuUsage(server.getString(Server.ATT_CPU_USAGE));
            setRequestThreads(server.getString(Server.ATT_REQUEST_THREADS));
            setTaskThreads(server.getString(Server.ATT_TASK_THREADS));
            setMemoryUsage(server.getString(Server.ATT_MEMORY_USAGE));
            setMemoryUsagePercentage(server.getString(Server.ATT_MEMORY_USAGE_PERCENTAGE));
            setDatabaseResponseTime(server.getString(Server.ATT_DATABASE_RESPONSE_TIME));
            setOpenFileCount(server.getString(Server.ATT_OPEN_FILE_COUNT));


            setIncludedServices((List<String>)server.get(Server.ATT_INCL_SERVICES));
            setExcludedServices((List<String>)server.get(Server.ATT_EXCL_SERVICES));

            Map<String, Object> monitoringCfg = (Map)server.getAttribute(Server.ATT_MONITORING_CFG);
            if (monitoringCfg != null) {
                if (monitoringCfg.containsKey(Server.ATT_MONITORING_CFG_STATS)) {
                    setEnabledStatistics(Util.otol(monitoringCfg.get(Server.ATT_MONITORING_CFG_STATS)));
                    setContainsStatistics(true);
                } else {
                    setContainsStatistics(false);
                }

                if (monitoringCfg.containsKey(Server.ATT_MONITORING_CFG_POLL_INTERVAL)) {
                    setPollingInterval(Util.otoi(monitoringCfg.get(Server.ATT_MONITORING_CFG_POLL_INTERVAL)));
                }

                if (monitoringCfg.containsKey(Server.ATT_MONITORING_CFG_RETENTION)) {
                    setRetention(Util.otoi(monitoringCfg.get(Server.ATT_MONITORING_CFG_RETENTION)));
                }

                if (monitoringCfg.containsKey(Server.ATT_MONITORING_CFG_APPS)) {
                    List<String> apps = Util.otol(monitoringCfg.get(Server.ATT_MONITORING_CFG_APPS));
                    for (String s : Util.safeIterable(apps)) {
                        addMonitoredApplication(s, context);
                    }
                }

            } else {
                setContainsStatistics(false);
            }
            
        }
    }

    public ServerDTO() { }


    //
    // Getters /Setters
    //

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String name) {
        this.description = name;
    }

    public List<String> getIncludedServices() {
        return includedServices;
    }

    public void setIncludedServices(List<String> includedServices) {
        this.includedServices = includedServices;
    }

    public List<String> getExcludedServices() {
        return excludedServices;
    }

    public void setExcludedServices(List<String> excludedServices) {
        this.excludedServices = excludedServices;
    }

    public boolean isInactive() {
        return inactive;
    }

    public void setInactive(boolean inactive) {
        this.inactive = inactive;
    }

    public List<ServiceState> getServiceStates() {
        return serviceStates;
    }

    public void setServiceStates(List<ServiceState> serviceStates) {
        this.serviceStates = serviceStates;
    }

    public Date getHeartbeat() {
        return heartbeat;
    }

    public void setHeartbeat(Date d) {
        this.heartbeat = d;
    }

    public String getCpuUsage() { return cpuUsage; }

    public void setCpuUsage(String s) { this.cpuUsage = s; }

    public String getRequestThreads() { return requestThreads; }

    public void setRequestThreads(String s) { this.requestThreads = s; }

    public String getTaskThreads() { return taskThreads; }

    public void setTaskThreads(String s) { this.taskThreads = s; }

    public String getMemoryUsage() { return memoryUsage; }

    public void setMemoryUsage(String s) { this.memoryUsage = s; }

    public String getMemoryUsagePercentage() { return memoryUsagePercentage; }

    public void setMemoryUsagePercentage(String s) { this.memoryUsagePercentage = s; }

    public String getDatabaseResponseTime() {
        return databaseResponseTime;
    }

    public void setDatabaseResponseTime(String databaseResponseTime) {
        this.databaseResponseTime = databaseResponseTime;
    }

    public String getOpenFileCount() { return openFileCount; }

    public void setOpenFileCount(String s) { this.openFileCount = s; }

    /**
     * @return Map with name/value pairs for any extended attributes with value on the managed attribute
     */
    public Map<String, String> getExtendedAttributes() {
        return extendedAttributes;
    }

    public void setExtendedAttributes(Map<String, String> extendedAttributes) {
        this.extendedAttributes = extendedAttributes;
    }

    public List<String> getEnabledStatistics() {
        return enabledStatistics;
    }

    public void setEnabledStatistics(List<String> enabledStatistics) {
        this.enabledStatistics = enabledStatistics;
    }

    public boolean isContainsStatistics() { return this.containsStatistics; }

    public void setContainsStatistics(boolean b) { this.containsStatistics = b; }

    public Integer getPollingInterval() {
        return pollingInterval;
    }

    public void setPollingInterval(Integer pollingInterval) {
        this.pollingInterval = pollingInterval;
    }

    public Integer getRetention() {
        return retention;
    }

    public void setRetention(Integer retention) {
        this.retention = retention;
    }

    public List<String> getDefaultStatistics() {
        return defaultStatistics;
    }

    public void setDefaultStatistics(List<String> defaultStatistics) {
        this.defaultStatistics = defaultStatistics;
    }

    public Integer getDefaultPollingInterval() {
        return defaultPollingInterval;
    }

    public void setDefaultPollingInterval(Integer defaultPollingInterval) {
        this.defaultPollingInterval = defaultPollingInterval;
    }

    public Integer getDefaultRetention() {
        return defaultRetention;
    }

    public void setDefaultRetention(Integer defaultRetention) {
        this.defaultRetention = defaultRetention;
    }

    public List<SuggestObjectDTO> getMonitoredApplications() { return monitoredApplications; }

    public void addMonitoredApplication(String appName, UserContext userContext)  {
        if (monitoredApplications == null) {
            monitoredApplications = new ArrayList<SuggestObjectDTO>();
        }

        // this sucks, but we need to get the id of the application if possible
        // so that the UI can properly distinguish between existing and new application
        // selections.
        String appId = getApplicationIdFromName(appName, userContext);
        if (appId == null) {
            if (log.isWarnEnabled()) {
                log.warn("Monitored application '" + appName + "' not found");
            }
            appId = appName;
        }

        monitoredApplications.add(new SuggestObjectDTO(appId, appName, appName));
    }

    /**
     * Get the id of an Application by name
     * @param appName the name of the application to lookup
     * @param userContext
     * @return the id the Application with the name appName.  null if application not found by name.
     */
    private String getApplicationIdFromName(String appName, UserContext userContext) {
        String appId = null;

        try {
            SailPointContext context = userContext.getContext();
            if (context != null) {
                QueryOptions ops = new QueryOptions();
                ops.add(Filter.eq("name", appName));

                List<String> ids = ObjectUtil.getObjectIds(context, Application.class, ops);
                if (!Util.isEmpty(ids)) {
                    appId = ids.get(0);
                }
            }
        }
        catch (Exception e) {
            if (log.isWarnEnabled()) {
                log.warn("Exception while loading application '" + appName + "'", e);
            }
        }

        return appId;
    }

    public void setMonitoredApplications(List<SuggestObjectDTO> apps) { this.monitoredApplications = apps; }

    /**
     * Bean to hold the state of a service with respect to a Server
     */
    public static class ServiceState {

        private String name;   // service name
        private String state;  // service state

        public static final String STATE_INCLUDE     = "INCLUDE";
        public static final String STATE_EXCLUDE     = "EXCLUDE";
        public static final String STATE_DEFER_ON    = "DEFER_ON";
        public static final String STATE_DEFER_OFF   = "DEFER_OFF";

        public ServiceState() {}

        public ServiceState(String serviceName, String state) {
            this.name = serviceName;
            this.state = state;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getState() {
            return state;
        }

        public void setState(String state) {
            this.state = state;
        }
    }

}
