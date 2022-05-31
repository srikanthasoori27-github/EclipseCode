/* (c) Copyright 2017 SailPoint Technologies, Inc., All Rights Reserved. */
/*
 * A Service used for gathering server statistics
 *
 */

package sailpoint.server;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import sailpoint.api.SailPointContext;
import sailpoint.object.Server;
import sailpoint.service.StatisticsService;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

import java.util.Calendar;
import java.util.Date;
import java.util.Map;


/**
 * The MonitoringService is responsible for gathering, persisting, and pruning
 * monitor statistics for the server
 */
@SuppressWarnings("unused")
public class MonitoringService extends Service {

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    private static Log log = LogFactory.getLog(MonitoringService.class);

    /**
     * Name under which this service will be registered.
     */
    public static final String NAME = "Monitoring";

    //
    // Default configuration default values
    //

    public static final int DEFAULT_INTERVAL = 60 * 5; // 5 minutes

    //////////////////////////////////////////////////////////////////////
    //
    // Constructor/Interface
    //
    //////////////////////////////////////////////////////////////////////

    public MonitoringService() {
        _name = NAME;

        // initialize to default.  Likely overwritten by call to configure().
        _interval = DEFAULT_INTERVAL;
    }

    /**
     * Called early in the startup sequence, and may be called after
     * startup to reload configuration changes.
     */
    @Override
    public void configure(SailPointContext context) throws GeneralException {

        int serverDefinedInterval = getServerDefinedInterval(context);
        if (serverDefinedInterval > 0) {
            // let the server-specific interval override the interval, ignoring the service definition
            _interval = serverDefinedInterval;

            if (log.isDebugEnabled()) {
                log.debug("Server-specific monitoring interval of '" + _interval + "' seconds being used");
            }
        } else {

            // let the definition override the interval
            int ival = _definition.getInterval();
            if (ival > 0) {
                if (log.isDebugEnabled()) {
                    log.debug("Service default monitoring interval of '" + _interval + "' seconds being used");
                }
                _interval = ival;
            } else {
                _interval = DEFAULT_INTERVAL;
                if (log.isDebugEnabled()) {
                    log.debug("Service default monitoring interval not defined.  Using interval of '" + _interval + "'");
                }

            }
        }

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void start() throws GeneralException {
        _started = true;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Execution Interface
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Called each execution interval.
     */
    public void execute(SailPointContext context) throws GeneralException {

        String hostname = Util.getHostName();

        Server server = context.getObjectByName(Server.class, hostname);
        if (server == null) {
            log.info("Cannot load server " + hostname + " for monitoring.");
            return;
        }

        // Build the MonitoringConfig each execution.   We may need to optimize this
        // because it could be moderately expensive... however, doing each time
        // allows any config changes to definitely be respected ASAP.
        MonitoringConfig monitoringConfig = MonitoringConfig.build(context, server, _definition);

        // We will be delegating all the real work to the StatisticsService.
        StatisticsService statsService = new StatisticsService(context);

        // Capture the time to be used for all statistics written during this execution
        Date timestamp = new Date();

        // Finally do what we came here to do
        updateStats(statsService, server, monitoringConfig, timestamp);
        updateServerAttributes(statsService, server, monitoringConfig);
        pruneStats(statsService, server, monitoringConfig, timestamp);

        if (monitoringConfig.getPollingInterval() != _interval) {
            // the polling interval has been changed, probably by an edit to the
            // the Server object's monitoringConfig
            forceReconfigure(context);
        }
    }

    /**
     * Let the Servicer know it needs to respond to a change in polling interval
     * @param context
     */
    private void forceReconfigure(SailPointContext context) {
        Environment env = Environment.getEnvironment();
        if (env != null) {
            Servicer servicer = env.getServicer();
            if (servicer != null) {
                servicer.reconfigure(context, _definition);
            }
            else {
                log.warn("Could not reconfigure " + _definition.getName() + " service.  Servicer not available.");
            }
        }
        else {
            log.warn("Could not reconfigure  " + _definition.getName() + " service. Environment not available.");
        }
    }

    /**
     * Delegate the statistics update to the StatisticsService
     */
    private void updateStats(StatisticsService statsService, Server server, MonitoringConfig monitoringConfig, Date timestamp) {

        if (log.isDebugEnabled()) {
            log.info("Updating statistics for server " + server.getName());
        }

        statsService.updateStats(server, monitoringConfig, timestamp);
    }

    /**
     * Calculate the pruning threshold, and delegate to StatisticsService if pruning is enabled
     */
    private void pruneStats(StatisticsService statsService, Server server, MonitoringConfig monitoringConfig, Date timestamp) {

        int periodRetentionDays = monitoringConfig.getRetentionPeriodDays();
        if (periodRetentionDays < 1) {
            if (log.isDebugEnabled()) {
                log.debug("No statistics pruned for server " + server.getName() + " because pruning is disabled.");
            }
            // pruning is disabled, nothing to do
            return;
        }

        // The pruneBeforeDate is the time before which all statistics should be pruned
        Calendar cal = Calendar.getInstance();
        cal.setTime(timestamp);
        cal.add(Calendar.DATE, -periodRetentionDays); // subtract retention days from timestamp
        Date pruneBeforeDate = cal.getTime();

        if (log.isDebugEnabled()) {
            log.debug("Pruning statistics for server " + server.getName() + " prior to " + pruneBeforeDate.toString());
        }

        try {
            statsService.pruneStats(server, pruneBeforeDate);
        } catch (GeneralException ge) {
            log.error("Error Pruning Stats " + ge);
        }
    }

    /**
     * Delegate the Server attributes updates to the StatisticsService
     */
    private void updateServerAttributes(StatisticsService statsService, Server server, MonitoringConfig cfg)  {
        if (log.isDebugEnabled()) {
            log.debug("Updating statistics attributes for server " + server.getName());
        }

        try {
            statsService.updateServerAttributes(server, cfg);
        } catch (GeneralException ge) {
            log.warn("Error updating statistics attributes " + ge);
        }
    }

    /**
     * Determine the polling interval for this server
     *
     * @return the monitoring interval configured for this server, or 0 if not available
     * @throws GeneralException any error occurs
     */
    private int getServerDefinedInterval(SailPointContext context) throws GeneralException {
        String hostname = Util.getHostName();

        Server server = context.getObjectByName(Server.class, hostname);
        if (server != null) {
            Map monitoringConfig = (Map) server.get(Server.ATT_MONITORING_CFG);
            return Util.getInt(monitoringConfig, Server.ATT_MONITORING_CFG_POLL_INTERVAL);
        }

        return 0;
    }

    public MonitoringConfig getMonitoringConfig(Server server, SailPointContext ctx) throws Exception {
        return MonitoringConfig.build(ctx, server, this._definition);
    }

}