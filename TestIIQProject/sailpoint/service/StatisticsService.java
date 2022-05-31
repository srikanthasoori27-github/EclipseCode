/* (c) Copyright 2017 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.service;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import sailpoint.api.SailPointContext;
import sailpoint.api.Terminator;
import sailpoint.environmentMonitoring.MonitoringScriptletContext;
import sailpoint.environmentMonitoring.MonitoringUtil;
import sailpoint.object.AttributeSource;
import sailpoint.object.Attributes;
import sailpoint.object.Filter;
import sailpoint.object.MonitoringStatistic;
import sailpoint.object.ObjectAttribute;
import sailpoint.object.ObjectConfig;
import sailpoint.object.QueryOptions;
import sailpoint.object.Request;
import sailpoint.object.RequestDefinition;
import sailpoint.object.Rule;
import sailpoint.object.Server;
import sailpoint.object.ServerStatistic;
import sailpoint.request.ApplicationStatusRequestExecutor;
import sailpoint.request.ModuleStatusRequestExecutor;
import sailpoint.server.MonitoringConfig;
import sailpoint.server.ScriptletEvaluator;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StatisticsService {

    private static final Log log = LogFactory.getLog(StatisticsService.class);

    SailPointContext _context = null;

    /**
     * Flag to determine whether or not the service should commit the
     * transaction. Default to true.
     */
    private boolean commit = true;

    /**
     *
     * @param context the persistent api to used for lifetime of this instance
     */
    public StatisticsService(SailPointContext context) {
        _context = context;
    }

    /**
     *
     * @param server the server for which to update statistics
     * @param monitoringConfig the configuration to drive the statistics calculation
     * @param timestamp the timestamp to record for all statistics written during this invocation
     */
    public void updateStats(Server server, MonitoringConfig monitoringConfig, Date timestamp) {

        if (monitoringConfig != null) {
            for (MonitoringStatistic stat : Util.safeIterable(monitoringConfig.getMonitoringStatistics())) {
                if (!stat.isTemplate()) {
                    try {
                        ServerStatistic statistic = getStat(stat);
                        if (statistic != null) {
                            statistic.setHost(server);
                            //Should we have a hook to calculate snapshot name? -rap
                            statistic.setSnapshotName(String.valueOf(timestamp.getTime()));
                            //Check for Object ref
                            if (Util.isNotNullOrEmpty(stat.getStringAttributeValue(MonitoringStatistic.ATTR_REFERENCED_OBJECT))) {
                                statistic.setTarget(stat.getStringAttributeValue(MonitoringStatistic.ATTR_REFERENCED_OBJECT));
                            }
                            //Check for Object type
                            if (Util.isNotNullOrEmpty(stat.getStringAttributeValue(MonitoringStatistic.ATTR_REF_OBJECT_TYPE))) {
                                statistic.setTargetType(stat.getStringAttributeValue(MonitoringStatistic.ATTR_REF_OBJECT_TYPE));
                            }

                            _context.saveObject(statistic);
                        }
                    } catch (GeneralException ge) {
                        log.warn("Error evaluating MonitoringStatistic[" + stat.getName() + "] Skipping. " + ge);
                    }
                } else {
                    log.error("Cannot evaluate Template Statistic[" + stat.getName() + "]");
                }
            }
            try {
                if (commit) {
                    //Shouldn't be too many ServerStatistics to worry with iterative commits. Commit once at the end
                    _context.commitTransaction();
                }
            } catch (GeneralException e) {
                log.warn("Error committing transaction" + e);
            }
        }

    }

    /**
     * Update the Server object with a sub-set of the statistics
     * @param server the Server to update
     * @return the updated Server object
     * @throws GeneralException
     */
    public Server updateServerAttributes(Server server, MonitoringConfig cfg) throws GeneralException {

        // Some monitoring values we still store directly into Server attributes
        // to prevent some unwanted joining with serverStatistics later

        server.put(Server.ATT_CPU_USAGE, MonitoringUtil.getCpuUsage().toString());
        server.put(Server.ATT_REQUEST_THREADS, MonitoringUtil.getRequestProcessorThreads());
        server.put(Server.ATT_TASK_THREADS, MonitoringUtil.getQuartzThreads());
        server.put(Server.ATT_MEMORY_USAGE, Long.valueOf(MonitoringUtil.getUsedMemory()).toString());
        server.put(Server.ATT_MEMORY_USAGE_PERCENTAGE, MonitoringUtil.getMemoryUsagePercentage().toString() );
        server.put(Server.ATT_DATABASE_RESPONSE_TIME, Long.valueOf(MonitoringUtil.getDbResponseTime(_context)).toString() );
        server.put(Server.ATT_OPEN_FILE_COUNT, Long.toString(MonitoringUtil.getOpenFileCount()));

        updateApplicationsStatus(server, cfg);

        updateModulesStatus(server, cfg);

        promoteExtendedAttributes(server);

        server.put(Server.ATT_LAST_STATS_UPDATE, new Date());

        _context.saveObject(server);
        if (commit) {
            _context.commitTransaction();
        }

        return server;
    }

    //////////////////////////////////////
    // Application Status
    //////////////////////////////////////


    public Server updateApplicationsStatus(Server server, MonitoringConfig cfg) {
        if (server != null && cfg != null) {

            Map<String, Object> captured = new HashMap<String, Object>();

            for (String app : Util.safeIterable(cfg.getMonitoredApplications())) {
                Map<String, Object> appStatus = getAppStatus(app);
                captured.put(app, appStatus);
            }

            server.put(Server.ATT_APPLICATION_STATUS, captured);
        }
        return server;
    }

    /**
     * Update the status for a given app on a specific Server
     * @param server
     * @param appName
     * @return
     */
    public Map getAppStatus(String appName) {

        Map<String, Object> appStatus = new HashMap<>();
        if (Util.isNotNullOrEmpty(appName)) {
            appStatus.put(Server.ATT_APP_STATUS_DATE, new Date());
            try {
                MonitoringUtil.getAppResponseTime(appName, _context);
                //Successful
                appStatus.put(Server.ATT_APP_STATUS, true);
            } catch (GeneralException e) {
                //App must be down
                if (log.isInfoEnabled()) {
                    log.info("Application reporting exception testing connection. " + e);
                }
                appStatus.put(Server.ATT_APP_STATUS, false);
                appStatus.put(Server.ATT_APP_STATUS_ERROR, e.getMessage());
            }
        }
        return appStatus;

    }


    /**
     * Request an Application Status update for a given Application on a specific Host
     * @param appName
     * @param hostName
     * @throws GeneralException
     */
    public void requestApplicationStatus(String appName, String hostName) throws GeneralException {
        if (Util.isNotNullOrEmpty(appName) && Util.isNotNullOrEmpty(hostName)) {
            RequestDefinition def = _context.getObjectByName(RequestDefinition.class, ApplicationStatusRequestExecutor.DEFINITION_NAME);

            if (def == null) {
                throw new GeneralException("Missing RequestDefintiion[" + ApplicationStatusRequestExecutor.DEFINITION_NAME + "]");
            }

            Request req = new Request(def);
            req.setHost(hostName);
            req.setName(String.format(ApplicationStatusRequestExecutor.REQUEST_NAME, appName, hostName));
            Attributes atts = new Attributes();
            atts.put(ApplicationStatusRequestExecutor.ARG_APP_NAME, appName);
            req.setAttributes(atts);
            _context.saveObject(req);
            _context.commitTransaction();
        }
    }

    //////////////////////////////////////
    // Module Status
    //////////////////////////////////////


    public Server updateModulesStatus(Server server, MonitoringConfig cfg) {
        if (server != null && cfg != null) {

            Map<String, Object> captured = new HashMap<String, Object>();

            for (String moduleName : Util.safeIterable(cfg.getMonitoredModules())) {
                Map<String, Object> moduleStatus = getModuleStatus(moduleName);
                captured.put(moduleName, moduleStatus);
            }

            server.put(Server.ATT_MODULE_STATUS, captured);
        }
        return server;
    }


    /**
     * Update the status for a given module on a specific Server
     * @param server
     * @param moduleName
     * @return
     */
    public Map getModuleStatus(String moduleName) {

        Map<String, Object> appStatus = new HashMap<>();
        if (Util.isNotNullOrEmpty(moduleName)) {
            appStatus.put(Server.ATT_MOD_STATUS_DATE, new Date());
            try {
                MonitoringUtil.checkModuleHealth(moduleName, _context);
                //Successful
                appStatus.put(Server.ATT_MOD_STATUS, true);
            } catch (GeneralException e) {
                //App must be down
                if (log.isInfoEnabled()) {
                    log.info("Module reporting exception testing connection. " + e);
                }
                appStatus.put(Server.ATT_MOD_STATUS, false);
                appStatus.put(Server.ATT_MOD_STATUS_ERROR, e.getMessage());
            }
        }
        return appStatus;

    }


    /**
     * Request a Module Status update for a given Module on a specific Host
     * @param moduleName
     * @param hostName
     * @throws GeneralException
     */
    public void requestModuleStatus(String moduleName, String hostName) throws GeneralException {
        if (Util.isNotNullOrEmpty(moduleName) && Util.isNotNullOrEmpty(hostName)) {
            RequestDefinition def = _context.getObjectByName(RequestDefinition.class, ModuleStatusRequestExecutor.DEFINITION_NAME);

            if (def == null) {
                throw new GeneralException("Missing RequestDefintiion[" + ModuleStatusRequestExecutor.DEFINITION_NAME + "]");
            }

            Request req = new Request(def);
            req.setHost(hostName);
            req.setName(String.format(ModuleStatusRequestExecutor.REQUEST_NAME, moduleName, hostName));
            Attributes atts = new Attributes();
            atts.put(ModuleStatusRequestExecutor.ARG_MODULE_NAME, moduleName);
            req.setAttributes(atts);
            _context.saveObject(req);
            _context.commitTransaction();
        }
    }


    /**
     *
     * @param server the server for which we should prune statistics
     * @param pruneBefore all statistics before this Date should be pruned
     */
    public void pruneStats(Server server, Date pruneBefore) throws GeneralException {

        if (pruneBefore != null) {
            QueryOptions ops = new QueryOptions();
            ops.add(Filter.le("created", pruneBefore));
            ops.add(Filter.eq("host", server));

            Terminator term = new Terminator(_context);
            term.deleteObjects(ServerStatistic.class, ops);

        } else {
            log.warn("No pruneBefore date. Skipping pruning");
        }

    }

    /**
     * Create a ServerStatistic from the given MonitoringStatistic
     * @param cfg
     * @return
     */
    public ServerStatistic getStat(MonitoringStatistic cfg) {
        ServerStatistic stat = null;

        try {
            MonitoringScriptletContext ctx = new MonitoringScriptletContext(cfg, _context);
            Object value = ScriptletEvaluator.evalSource(_context, ctx, cfg.getValueSource());
            stat = new ServerStatistic(cfg);
            //Is toString enough here? - rap
            stat.setValue(value.toString());

        } catch (GeneralException ge) {
            log.warn("Error evaluating Scriptlet for MonitoringStatistic[" + cfg.getName() + "]");
        }

        return stat;

    }

    public static final String RULE_ARG_SERVER = "server";
    public static final String RULE_ARG_OBJ_ATT = "objectAttribute";
    public static final String RULE_ARG_ATT_SRC = "attributeSource";
    /**
     *
     * @param server the server for which to promote statistics-related extended attributes
     */
    private Server promoteExtendedAttributes(Server server) throws GeneralException {

        Attributes<String,Object> newatts = server.getAttributes();
        if (newatts == null) {
            // shouldn't happen but we must have a target map for
            // transfer of existing system attributes
            newatts = new Attributes<String,Object>();
        }

        //extended attributes
        ObjectConfig config = Server.getObjectConfig();
        if (config != null) {
            List<ObjectAttribute> extended = config.getObjectAttributes();
            if (extended != null) {
                for (ObjectAttribute ext : Util.safeIterable(extended)) {

                    List<AttributeSource> sources = ext.getSources();
                    if (!Util.isEmpty(sources)) {
                        int max = (sources != null) ? sources.size() : 0;
                        for (int i = 0 ; i < max ; i++) {
                            AttributeSource src = sources.get(i);
                            Object val = null;
                            if (Util.isNotNullOrEmpty(src.getName())) {
                                val = server.getAttribute(src.getName());

                            } else if (src.getRule() != null){
                                Rule rule = src.getRule();
                                Map<String,Object> args = new HashMap<String,Object>();
                                args.put(RULE_ARG_SERVER, server);
                                args.put(RULE_ARG_ATT_SRC, src);
                                args.put(RULE_ARG_OBJ_ATT, ext);
                                val = _context.runRule(rule, args);

                            } else {
                                if (log.isWarnEnabled())
                                    log.warn("AttributeSource with nothing to do: " +
                                            src.getName());
                            }
                            if (val != null) {
                                newatts.put(ext.getName(), val);
                                break;
                            }
                        }

                    }
                }
            }
        }

        server.setAttributes(newatts);

        return server;
    }

}
