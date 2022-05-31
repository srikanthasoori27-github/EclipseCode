/*
 *  (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.task;

import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.alert.AlertProcessor;
import sailpoint.alert.AlertService;
import sailpoint.api.AlertAggregator;
import sailpoint.api.IdIterator;
import sailpoint.api.SailPointContext;
import sailpoint.object.Alert;
import sailpoint.object.AlertDefinition;
import sailpoint.object.Attributes;
import sailpoint.object.Filter;
import sailpoint.object.QueryOptions;
import sailpoint.object.TaskResult;
import sailpoint.object.TaskSchedule;
import sailpoint.tools.GeneralException;

/**
 * Created by ryan.pickens on 7/8/16.
 *
 * @ignore
 * We might want to think about some parallelization for this
 */
public class AlertAggregationTask extends AbstractTaskExecutor {

    Log _log = LogFactory.getLog(AlertAggregationTask.class);

    AlertAggregator _aggregator;
    TaskMonitor _monitor;
    Attributes<String, Object> _args;
    SailPointContext _context;
    AlertProcessor _processor;

    ////////////////////////////////////////////////////////////
    // ARGS
    ////////////////////////////////////////////////////////////
    public static final String ARG_PROCESS_ALERTS = "processAlerts";
    public static final String ARG_ALERT_DEF_NAMES = "alertDefinitionNames";


    ////////////////////////////////////////////////////////////
    // METRICS
    ////////////////////////////////////////////////////////////

    ///////////////////////////////////////////////////////////////////
    /// Returns
    //////////////////////////////////////////////////////////////////
    public static final String RET_SOURCES = "sources";
    public static final String RET_AGG_TOTAL = "totalAlerts";
    public static final String RET_ALERT_CREATED = "alertsCreated";
    public static final String RET_ALERT_IGNORED = "alertsIgnored";
    public static final String RET_ALERT_PROCESSED = "alertsProcessed";
    public static final String RET_ALERT_ACTION_CREATED = "actionsCreated";

    Date _startTime;

    @Override
    public void execute(SailPointContext context, TaskSchedule schedule, TaskResult result, Attributes<String, Object> args) throws Exception {

        _monitor = new TaskMonitor(context, result);
        _args = args;
        _context = context;

        _startTime = new Date();
        if (args != null) {

            phaseAggregate(args);


            if (args.getBoolean(ARG_PROCESS_ALERTS)) {
                _monitor.updateProgress("Beginning Alert Processing");
                phaseProcess(args);
            }

        } else {
            _log.warn("No Arguments supplied for Aggregation");

        }

        saveResults();


    }

    @Override
    public boolean terminate() {
        _aggregator.setTerminate(true);
        _processor.setTerminate(true);
        return true;
    }

    private void phaseAggregate(Attributes<String, Object> args ) throws GeneralException {
        _aggregator = new AlertAggregator(_context, args);
        _aggregator.setMonitor(_monitor);

        _aggregator.execute();

        updateAggregationResults();

    }



    private void phaseProcess(Attributes<String, Object> args ) throws GeneralException {

        //Get the AlertDefintions
        List<AlertDefinition> alertDefs = getAlertDefinitions();
        //Fully load the defs
        for (AlertDefinition def : sailpoint.tools.Util.safeIterable(alertDefs)) {
            def.load();
        }


        //Get the Alerts
        IdIterator iter = getAggregatedAlerts();
        while (iter.hasNext()) {
            String alertId = iter.next();
            _processor = new AlertProcessor(alertId, alertDefs, _context, _args);
            _processor.setMonitor(_monitor);
            _processor.run();
            //Update results after each Alert is processed since we don't have a centralized metrics collector
            updateProcessResults();
        }



    }

    private void updateAggregationResults() throws GeneralException {
        //If we get partitioned result, will need to commit after updating. No Partitioning here for now, so no need. -rap
        TaskResult result = _monitor.getTaskResult();

        //Add Metrics
        result.setAttribute(RET_SOURCES, _aggregator._aggregatedSources);
        result.setInt(RET_AGG_TOTAL, _aggregator._total);
        result.setInt(RET_ALERT_CREATED, _aggregator._created);
        result.setInt(RET_ALERT_IGNORED, _aggregator._ignored);

        //Add Messages
        if (_aggregator.getMessageRepo() != null)
            result.addMessages(_aggregator.getMessageRepo().getMessages());

        result.setTerminated(_aggregator._terminate);
    }

    private void updateProcessResults() {
        //If we get partitioned result, will need to commit after updating. No Partitioning here for now, so no need. -rap
        TaskResult result = _monitor.getTaskResult();

        result.addInt(RET_ALERT_PROCESSED, 1);
        result.addInt(RET_ALERT_ACTION_CREATED, _processor._actionsCreated);


        if (_processor.getMessageRepo() != null) {
            result.addMessages(_processor.getMessageRepo().getMessages());
        }
    }

    private void saveResults() throws GeneralException {
        _monitor.commitMasterResult();
    }

    private List<AlertDefinition> getAlertDefinitions() throws GeneralException {
        List<String> argDefs = _args.getStringList(ARG_ALERT_DEF_NAMES);
        AlertService service = new AlertService(_context);
        return service.getAlertDefinitions(argDefs);
    }

    /**
     * Return a list of Alert Id's with a createdDate  greater than the startDate and from the
     * same source
     * @return
     */
    private IdIterator getAggregatedAlerts() throws GeneralException {
        List<String> aggregationSources = _args.getStringList(AlertAggregator.ARG_SOURCES);

        QueryOptions ops = new QueryOptions();
        ops.add(Filter.in("source.name", aggregationSources));
        ops.add(Filter.gt("created", _startTime));

        Iterator<Object[]> it = _context.search(Alert.class, ops, Arrays.asList("id"));
        return new IdIterator(it);
    }

}
