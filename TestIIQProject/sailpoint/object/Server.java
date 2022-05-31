/* (c) Copyright 2013 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * Class used represent an instance of the IIQ server.  One of these will
 * be created automatically as servers are started, once created they
 * will be left in place and can be used to store server-specific 
 * configuration.
 *
 * Author: Jeff
 *
 * SailPointObject._name is the server name.  This will be the name returned by 
 * Util.getHostName. It is usually the host name returned by 
 * InetAddress.getLocalHost but may be overridden by the iiq.hostname 
 * system property if there are more than one servers running ont he 
 * same host.
 *
 * Note that SailPointObject.modified is not the same as the heartbeat.
 * Changes can be flushed to the Server objects by any server that are
 * not related to heartbeat maintenance.  For example setting configuration
 * attributes, or setting the inactive flag.
 *
 */

package sailpoint.object;

import java.util.Arrays;
import java.util.Date;
import java.util.List;

import sailpoint.tools.GeneralException;
import sailpoint.tools.xml.XMLProperty;
import sailpoint.tools.xml.SerializationMode;

/**
 * Class used represent an instance of the IdentityIQ server. One of these will
 * be created automatically as servers are started, once created they
 * will be left in place and can be used to store server-specific 
 * configuration.
 */
public class Server extends SailPointObject
    implements Cloneable {

    //////////////////////////////////////////////////////////////////////
    //
    // Constants
    //
    //////////////////////////////////////////////////////////////////////

    //
    // Configuration arguments
    //

    /**
     * Configuration attribute holding the maximum number of RequestHandler
     * threads to allow on this server. If all servers in the cluster support
     * the same maximum, then set ARG_MAX_THREADS in the ServiceDefinition
     * instead.  
     *
     * This number overrides ServiceDefinition.ARG_MAX_THREADS and can
     * be used if some servers support more or fewer threads than others.
     *
     * If a maximum thread could is not specified, then the request
     * processor will use the thread maximums set in each RequestDefinition.
     */
    public static final String ARG_MAX_REQUEST_THREADS = "maxRequestThreads";

    /**
     * Configuration attribute (map) holding the monitoring configuration
     */
    public static final String ATT_MONITORING_CFG = "monitoringConfig";

    /**
     * Configuration attribute (integer) holding the polling interval in seconds
     * for gathering monitoring statistics.   This is contained within the
     * ATT_MONITORING_CFG map attribute.
     */
    public static final String ATT_MONITORING_CFG_POLL_INTERVAL= "pollingIntervalSecs";

    /**
     * Configuration attribute (integer) holding the retention period
     * in days which the monitoring statistics data should be retained.
     * This is contained within the ATT_MONITORING_CFG map attribute.
     */
    public static final String ATT_MONITORING_CFG_RETENTION = "retentionPeriodDays";

    /**
     * Configuration attribute (string list) holding the list of names of
     * MonitoringStatistic which are to be collected.
     * This is contained within the ATT_MONITORING_CFG map attribute.
     */
    public static final String ATT_MONITORING_CFG_STATS = "monitoringStatistics";

    /**
     * Configuration attribute (string list) holding the names of
     * the applications to monitor.
     * * This is contained within the ATT_MONITORING_CFG map attribute.
     */
    public static final String ATT_MONITORING_CFG_APPS = "monitoredApplications";

    /**
     * Configuration attribute (string list) holding the names of
     * the modules to monitor.
     * * This is contained within the ATT_MONITORING_CFG map attribute.
     */
    public static final String ATT_MONITORING_CFG_MODULES = "monitoredModules";

    /**
     * Configuration attribute (string list) holding the list of
     * services which should run on this Server, overriding the
     * hosts in ServiceDefinition
     */
    public static final String ATT_INCL_SERVICES = "includedServices";

    /**
     * Configuration attribute (string list) holding the list of
     * services which should NOT run on this Server, overriding the
     * hosts in ServiceDefinition
     */
    public static final String ATT_EXCL_SERVICES = "excludedServices";


    //
    // Read-only status attributes
    //
    
    /**
     * Status attribute containing a boolean indicating that this
     * server is running the request processor. This is needed
     * by AbstractTaskExecutor.getSuggestedPartitionCount to 
     * calculate the number of RP threads available in the cluster.
     * For that you need to know which servers are actually allowed
     * to run the RP.  Not doing this for the Task service since
     * it is not needed.
     */
    public static final String ATT_REQUEST_SERVICE_STARTED = 
        "requestServiceStarted";

    /**
     * Status attribute containing the percentage CPU utilization.
     */
    public static final String ATT_CPU_USAGE = "cpuUsage";
    
    /**
     * Status attribute containing the number of active request 
     * processing threads.
     */
    public static final String ATT_REQUEST_THREADS = "requestThreads";

    /**
     * Status attribute containing the number of quartz task threads.
     */
    public static final String ATT_TASK_THREADS = "taskThreads";

    /**
     * Status attribute containing the currently used memory
     */
    public static final String ATT_MEMORY_USAGE = "memoryUsage";

    /**
     * Status attribute containing the current percentage of memory in use
     */
    public static final String ATT_MEMORY_USAGE_PERCENTAGE = "memoryUsagePercentage";

    /**
     * Status attribute containing the response time for database access in milliseconds
     */
    public static final String ATT_DATABASE_RESPONSE_TIME = "databaseResponseTime";

    /**
     * Status attribute containing the number of open file descriptors
     */
    public static final String ATT_OPEN_FILE_COUNT = "openFileCount";

    /**
     * Status attribute containing the monitored applications statuses. Will
     * return Map<String, Boolean> keyed by Application name
     */
    public static final String ATT_APPLICATION_STATUS = "applicationStatus";

    public static final String ATT_APP_STATUS = "status";

    public static final String ATT_APP_STATUS_DATE = "lastPing";

    public static final String ATT_APP_STATUS_ERROR = "statusError";

    /**
     * Status attribute containing the monitored module' statuses. Will
     * return Map<String, Boolean> keyed by Module name
     */
    public static final String ATT_MODULE_STATUS = "moduleStatus";

    public static final String ATT_MOD_STATUS = "status";

    public static final String ATT_MOD_STATUS_DATE = "lastPing";

    public static final String ATT_MOD_STATUS_ERROR = "statusError";

    /**
     * Date in which the statistics were last updated
     */
    public static final String ATT_LAST_STATS_UPDATE = "lastStatisticUpdate";


    /**
     * Status attribute containing the server's last updated state.
     */
    public static final List<String> EXPORTABLE_ATTRIBUTES = Arrays.asList(
            ATT_CPU_USAGE,
            ATT_REQUEST_THREADS,
            ATT_TASK_THREADS,
            ATT_MEMORY_USAGE,
            ATT_MEMORY_USAGE_PERCENTAGE,
            ATT_DATABASE_RESPONSE_TIME,
            ATT_OPEN_FILE_COUNT
    );

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Configuration and status attributes.
     */
    private Attributes<String,Object> _attributes;

    /**
     * Time of last heartbeat.
     */
    Date _heartbeat;

    /**
     * True once this server is believed to be inactive.
     * This is set by HeartbeatService for any server that has not
     * updated its heartbeat within two cycles.
     */
    boolean _inactive;

    //////////////////////////////////////////////////////////////////////
    //
    // Properties
    //
    //////////////////////////////////////////////////////////////////////

    public Server() {
    }

    /**
     * Let the ScopeService know this class does not have a scope.
     */
    @Override
    public boolean hasAssignedScope() {
        return false;
    }

    @XMLProperty(mode=SerializationMode.INLINE)
    public Attributes<String, Object> getAttributes() {
        return _attributes;
    }

    public void setAttributes(Attributes<String, Object> a) {
        _attributes = a;
    }

    public Object getAttribute(String name) {
        return (_attributes != null) ? _attributes.get(name) : null;
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

    @XMLProperty
    public Date getHeartbeat() {
        return _heartbeat;
    }

    public void setHeartbeat(Date d) {
        _heartbeat = d;
    }

    @XMLProperty
    public boolean isInactive() {
        return _inactive;
    }

    public void setInactive(boolean b) {
        _inactive = b;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Convenience accessors
    //
    //////////////////////////////////////////////////////////////////////

    public Object get(String name) {
        return (_attributes != null) ? _attributes.get(name) : null;
    }

    public void put(String name, Object value) {
        if (_attributes == null)
            _attributes = new Attributes<String,Object>();
        _attributes.put(name, value);
    }

    public void put(String name, int value) {
        if (_attributes == null)
            _attributes = new Attributes<String,Object>();
        _attributes.put(name, new Integer(value));
    }

    public String getString(String name) {
        return (_attributes != null) ? _attributes.getString(name) : null;
    }

    public int getInt(String name) {
        return (_attributes != null) ? _attributes.getInt(name) : 0;
    }

    public boolean getBoolean(String name) {
        return (_attributes != null) ? _attributes.getBoolean(name) : false;
    }

    public List getList(String name) {
        return (_attributes != null) ? _attributes.getList(name) : null;
    }

    public void remove(String name) {
        if (_attributes != null)
            _attributes.remove(name);
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

        if (objectConfig == null) {
            objectConfig = ObjectConfig.getCache(Server.class);
        }

        if (objectConfig != null) {
            config = (ObjectConfig) objectConfig.getObject();
        }

        return config;
    }

    ////////////////////////////////////////////////////////////////////////////
    //
    // OVERRIDES
    //
    ////////////////////////////////////////////////////////////////////////////

    @Override
    public void visit(Visitor v) throws GeneralException {
        v.visitServer(this);
    }

}
