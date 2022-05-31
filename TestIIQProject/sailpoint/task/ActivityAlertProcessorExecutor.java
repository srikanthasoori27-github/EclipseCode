/*
 *  (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.task;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import sailpoint.alert.AlertProcessor;
import sailpoint.api.IdIterator;
import sailpoint.api.SailPointContext;
import sailpoint.integration.Util;
import sailpoint.object.Alert;
import sailpoint.object.AlertDefinition;
import sailpoint.object.Attributes;
import sailpoint.object.Filter;
import sailpoint.object.Partition;
import sailpoint.object.QueryOptions;
import sailpoint.object.Request;
import sailpoint.object.RequestDefinition;
import sailpoint.object.TaskResult;
import sailpoint.object.TaskSchedule;
import sailpoint.request.AlertProcessorRequestExecutor;
import sailpoint.tools.Compressor;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.web.messages.MessageKeys;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

/**
 * Created by ryan.pickens on 6/29/16.
 */
public class ActivityAlertProcessorExecutor extends AbstractTaskExecutor {

    private static Log _log = LogFactory.getLog(ActivityAlertProcessorExecutor.class);

    //////////////////////////////////////////////////////////////////////
    //
    // Arguments
    //
    //////////////////////////////////////////////////////////////////////

    public static final String ARG_ALERT_FILTER_STRING = "alertFilterString";

    public static final String ARG_ALERT_DEF_FILTER = "alertDefFilterString";

    public static final String ARG_UNPROCESSED_ALERTS = "unprocessedAlerts";

    public static final String ARG_ENABLE_PARTITIONING = "enablePartitioning";

    public static final String ARG_DECACHE_COUNT = "decacheCount";

    /**
     * Task argument key which holds the RequestDefinition for the partitioned requests.
     */
    private static final String PARTITIONED_REQUEST_DEF_NAME = "Partitioned Alert Processing";


    //////////////////////////////////////////////////////////////////////
    //
    // Results
    //
    //////////////////////////////////////////////////////////////////////

    public static final String RET_TOTAL_PROCESSED = "totalProcessed";
    public static final String RET_MATCHED_ALERTS = "matchedAlerts";
    public static final String RET_UNMATCHED_ALERTS = "unmatchedAlerts";
    public static final String RET_DEFINITION_COUNT = "definitionCount";
    public static final String RET_ACTIONS_CREATED = "actionsCreated";

    boolean _terminate;

    SailPointContext _context;

    TaskMonitor _monitor;

    TaskResult _result;
    TaskSchedule _schedule;

    int _decacheCount = 100;

    @Override
    public void execute(SailPointContext context, TaskSchedule schedule, TaskResult result, Attributes<String, Object> args) throws Exception {

        if (context == null) {
            throw new GeneralException("No Context defined");
        }

        _context = context;
        _result = result;
        _schedule = schedule;

        configureMonitor(context, result);

        Filter filter = getAlertFilter(args);

        IdIterator alertIterator = getAlertIdIterator(filter);

        if (isPartitioningEnabled(args)) {
            doPartitioned(alertIterator, args);
        } else {
            doUnpartitioned(alertIterator, args);
        }



    }

    private void configureMonitor(SailPointContext ctx, TaskResult result) {
        Monitor baseMonitor = getMonitor();
        if ( baseMonitor != null ) {
            if ( baseMonitor instanceof TaskMonitor ) {
                _monitor = (TaskMonitor)baseMonitor;
            }
        }

        if ( _monitor == null ) {
            _monitor = new TaskMonitor(ctx, result);
        }
    }

    private Filter getAlertFilter(Attributes<String, Object> attributes) throws GeneralException {
        //Check for compiled filter
        Filter filter = null;
        try {
            if (Util.isNotNullOrEmpty(attributes.getString(ARG_ALERT_FILTER_STRING))) {
                String filterString = attributes.getString(ARG_ALERT_FILTER_STRING);
                filter = Filter.compile(filterString);
            }

            // If unprocessed alerts checked, only get alerts not previously processed
            if (attributes.getBoolean(ARG_UNPROCESSED_ALERTS)) {
                if (filter != null) {
                    filter = Filter.and(filter, Filter.isnull("lastProcessed"));
                } else {
                    filter = Filter.isnull("lastProcessed");
                }
            }
        } catch (Exception e) {
            _log.error("Error getting Alert Filter", e);
            throw new GeneralException("Error getting AlertFilter", e);
        }

        return filter;
    }

    private Filter getAlertDefFilter(Attributes<String, Object> attributes) {
        Filter f = null;

        String filterString = attributes.getString(ARG_ALERT_DEF_FILTER);

        if (Util.isNotNullOrEmpty(filterString)) {
            f = Filter.compile(filterString);
        }

        return f;
    }

    private List<AlertDefinition> getAlertDefinitions(Attributes<String, Object> attributes)
            throws GeneralException {
        List<AlertDefinition> defs;
        Filter f = getAlertDefFilter(attributes);
        QueryOptions ops = new QueryOptions();
        ops.add(f);

        defs = _context.getObjects(AlertDefinition.class, ops);

        for (AlertDefinition def : sailpoint.tools.Util.safeIterable(defs)) {
            def.load();
        }

        return defs;
    }

    private int getDecacheCount(Attributes<String, Object> args) throws GeneralException {
        if (args.containsKey(ARG_DECACHE_COUNT)) {
            int cnt = args.getInt(ARG_DECACHE_COUNT);
            if (cnt > 0) {
                _decacheCount = cnt;
            } else {
                _log.warn("Decache count must be > 0. Defaulting to " + _decacheCount);
            }
        }

        return _decacheCount;
    }

    @Override
    public boolean terminate() {
        _terminate = true;

        return _terminate;
    }


    private List<Partition> createPartitions(IdIterator it, int partitionSize) throws GeneralException {

        List<Partition> partitions = new ArrayList<Partition>();

        _log.debug("Total alerts to process = " + it.size());

        // since we're not fetching Identity objects, don't really need to use the
        // iterator for deacche, just get the list directly
        List<String> idList = it.getIds();
        if (sailpoint.tools.Util.size(idList) > 0) {
            int start = 0;
            int remainder = idList.size() % partitionSize;
            for (int i = 0; i < partitionSize; ++i) {
                //get sub list for the partition
                int stepSize = idList.size()/partitionSize;
                int end = start + stepSize;
                if (remainder > 0) {
                    end ++;
                    remainder --;
                }

                if (end > idList.size()) {
                    end = idList.size();
                }
                List<String> subList = idList.subList(start, end);

                Message msg = new Message(MessageKeys.TASK_ALERT_PROCESSING_PARTITION_NAME, start+1, end);
                String partitionName = msg.getLocalizedMessage();

                start = end;

                Partition partition = new Partition();
                partition.setName(partitionName);
                partition.setSize(subList.size());

                //compress the id list
                String idString = sailpoint.tools.Util.listToCsv(subList);
                String compressed = Compressor.compress(idString);
                partition.setAttribute(AlertProcessorRequestExecutor.ARG_ALERT_ID_LIST, compressed);

                partitions.add(partition);
            }
        }

        return partitions;
    }

    private IdIterator getAlertIdIterator(Filter filter) throws GeneralException {
        IdIterator idIt = null;
        QueryOptions ops = null;

        if (filter == null)
            _monitor.updateProgress("Getting Alert Iterator");
        else {
            _monitor.updateProgress("Getting Alert Iterator with filter " + filter.toString());

            ops = new QueryOptions(filter);
        }

        Iterator<Object[]> it = _context.search(Alert.class, ops, Arrays.asList("id"));

        idIt = new IdIterator(it);

        return idIt;
    }

    private void doUnpartitioned(IdIterator alertIter, Attributes<String, Object> atts) throws GeneralException {
        _monitor.updateProgress("Starting Unpartitioned Processing");

        initializeProcessResults();

        List<String> idList = alertIter.getIds();
        List<AlertDefinition> alertDefs = getAlertDefinitions(atts);
        if (Util.isEmpty(idList)) {
            _log.warn("No Alerts to process");
        }

        if (Util.isEmpty(alertDefs)) {
            _log.warn("No Alert Definitions found");
        } else {
            _result.setAttribute(RET_DEFINITION_COUNT, Util.size(alertDefs));
        }

        int decacheCnt = getDecacheCount(atts);

        int cnt = 0;
        for (String id : sailpoint.tools.Util.safeIterable(idList)) {
            long startTime = System.currentTimeMillis();
            cnt++;
            AlertProcessor processor = new AlertProcessor(id, alertDefs, _context, atts);
            processor.setMonitor(_monitor);
            processor.run();
            //Update results after each Alert is processed since we don't have a centralized metrics collector
            updateProcessResults(processor);
            if (_log.isDebugEnabled()) {
                _log.debug("Time to process alert[" + (System.currentTimeMillis() - startTime) + "ms]");
            }

            if (cnt % decacheCnt == 0) {
                //Full decache in order to clean up lingering collections
                _context.decache();
            }
        }


        _monitor.updateProgress("Finished Unpartitioned Processing");

    }

    private void initializeProcessResults() throws GeneralException {
        _result.setAttribute(RET_TOTAL_PROCESSED, sailpoint.tools.Util.itoa(0));
        _result.setAttribute(RET_MATCHED_ALERTS, sailpoint.tools.Util.itoa(0));
        _result.setAttribute(RET_UNMATCHED_ALERTS, sailpoint.tools.Util.itoa(0));
    }

    /**
     * Save the TaskResult
     * @param processor
     * @throws GeneralException
     */
    private void updateProcessResults(AlertProcessor processor) throws GeneralException {
        //If we get partitioned result, will need to commit after updating. No Partitioning here for now, so no need. -rap
        TaskResult result = _monitor.getTaskResult();
        result.addInt(RET_TOTAL_PROCESSED, 1);
        result.addInt(RET_ACTIONS_CREATED, processor._actionsCreated);
        if (processor._actionsCreated > 0) {
            result.addInt(RET_MATCHED_ALERTS, 1);
        } else {
            result.addInt(RET_UNMATCHED_ALERTS, 1);
        }

        if (processor.getMessageRepo() != null) {
            result.addMessages(processor.getMessageRepo().getMessages());
        }
    }

    private void doPartitioned(IdIterator alertIter, Attributes<String, Object> atts) throws GeneralException {
        _monitor.updateProgress("Starting Partitioned Processing");

        List<Partition> partitions = createPartitions(alertIter, getPartitonSize(atts));

        if (Util.size(partitions) == 0) {
            //No Partitions, complete task
            _monitor.updateProgress("Alert Processing task complete");
            _result.setAttribute(RET_TOTAL_PROCESSED, sailpoint.tools.Util.itoa(0));
        } else {
            //Create request for each partition

            RequestDefinition def = getRequestDefintiion(_context);
            String taskDefName = _schedule.getTaskDefinitionName(_context);

            List<Request> requests = new ArrayList<Request>();

            for (Partition partition : partitions) {
                Attributes<String, Object> partitionArgs = copyTaskArgsForPartition(atts);
                Request request = createRequestForPartition(partition, def, partitionArgs, taskDefName);
                requests.add(request);
            }

            launchPartitions(_context, _result, requests);

        }

    }

    private Attributes<String, Object> copyTaskArgsForPartition(Attributes<String, Object> args) {
        Attributes<String, Object> copiedAttributes = new Attributes<String, Object>();
        for (String key : args.keySet()) {
            copiedAttributes.put(key, args.get(key));
        }

        return copiedAttributes;
    }

    private Request createRequestForPartition(Partition partition, RequestDefinition requestDef,
                                              Attributes<String,Object> args,
                                              String taskDefinitionName) throws GeneralException {
        Request request = new Request(requestDef);
        request.setName(partition.getName());

        request.setAttributes(args);
        request.put(AlertProcessorRequestExecutor.ARG_TASK_DEF_NAME, taskDefinitionName);
        request.put(AlertProcessorRequestExecutor.ARG_PARTITION, partition);

        return request;
    }

    private RequestDefinition getRequestDefintiion(SailPointContext ctx) throws GeneralException {
        RequestDefinition def = null;

        def = ctx.getObjectByName(RequestDefinition.class, PARTITIONED_REQUEST_DEF_NAME);

        return def;
    }

    private boolean isPartitioningEnabled(Attributes<String, Object> atts) {
        return atts.getBoolean(ARG_ENABLE_PARTITIONING);
    }

    private int getPartitonSize(Attributes<String, Object> args) throws GeneralException {
        //Hidden argument to manually set number of partitions.
        int size = args.getInt("partitions");
        if (size > 0) {
            return size;
        } else {
            return getSuggestedPartitionCount(_context, false, PARTITIONED_REQUEST_DEF_NAME);
        }
    }

}
