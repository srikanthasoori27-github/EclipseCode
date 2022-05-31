/*
 *  (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.request;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import sailpoint.alert.AlertProcessor;
import sailpoint.api.SailPointContext;
import sailpoint.object.AlertDefinition;
import sailpoint.object.Attributes;
import sailpoint.object.Filter;
import sailpoint.object.Partition;
import sailpoint.object.QueryOptions;
import sailpoint.object.Request;
import sailpoint.object.TaskResult;
import sailpoint.task.ActivityAlertProcessorExecutor;
import sailpoint.task.TaskMonitor;
import sailpoint.tools.Compressor;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Created by ryan.pickens on 7/18/16.
 */
public class AlertProcessorRequestExecutor extends AbstractRequestExecutor {

    Log _log = LogFactory.getLog(AlertProcessorRequestExecutor.class);

    public static final String ARG_ALERT_ID_LIST = "alertIdList";
    public static final String ARG_TASK_DEF_NAME = "taskDefinitionName";
    public static final String ARG_PARTITION = "partition";
    public static final String ARG_ALERT_DEF_FILTER = "alertDefinitionFilter";

    ///////////////////////////////////////////////////////////////////
    /// Returns
    //////////////////////////////////////////////////////////////////
    public static final String RET_ALERT_PROCESSED = "alertsProcessed";

    private boolean _terminated;

    SailPointContext _context;

    Partition _partition;
    Request _request;
    TaskResult _result;
    List<TaskResult> _partitionedResults;


    @Override
    public void execute(SailPointContext context, Request request, Attributes<String, Object> args) throws RequestPermanentException, RequestTemporaryException {

        _context = context;
        _request = request;
        _result = request.getTaskResult();

        _partition = (Partition) request.getAttribute(ARG_PARTITION);

        if (_partition == null) {
            _log.error("Error encountered processing partition[" + request.getName() + "]");
        }

        TaskMonitor monitor = new TaskMonitor(context, request);

        monitor.updateProgress("Starting Partition[" + request.getName() + "]");

        try {
            String compressedIds = _partition.getString(ARG_ALERT_ID_LIST);
            String idListString = Compressor.decompress(compressedIds);
            List<String> idList = Util.csvToList(idListString);

            List<AlertDefinition> alertDefs = getAlertDefs();

            if (Util.isEmpty(alertDefs)) {
                _log.error("Not Alert Definitions found");
                _result.addMessage("No Alert Definitions Found for request[" + request.getName() + "]");
                return;
            }

            Iterator<String> it = idList.iterator();
            while (it.hasNext()) {
                String id = it.next();
                it.remove();
                AlertProcessor processor = new AlertProcessor(id, alertDefs, _context, args);
                processor.setMonitor(monitor);
                processor.run();
                //Update results after each Alert is processed since we don't have a centralized metrics collector
                saveResults(processor);
                //Update the remaining ids in case partition needs to be restarted
                //TODO: Should we only do this if the request is marked restartable?
                updatePartitionIdList(idList);
            }

            mergePartitionedResults(monitor.lockPartitionResult());
            monitor.updateProgress("Finished Partition[" + request.getName() + "]");

        } catch (GeneralException g) {

        } finally {
            try {
                // Persist the Request
                monitor.commitPartitionResult();
            } catch (GeneralException ge) {
                _log.error("Error saving partition result", ge);
            }

        }
    }

    private void saveResults(AlertProcessor processor) {
        TaskResult partitionedResult = new TaskResult();
        partitionedResult.addInt(ActivityAlertProcessorExecutor.RET_TOTAL_PROCESSED, 1);
        partitionedResult.addInt(ActivityAlertProcessorExecutor.RET_ACTIONS_CREATED, processor._actionsCreated);
        if (processor._actionsCreated > 0) {
            partitionedResult.addInt(ActivityAlertProcessorExecutor.RET_MATCHED_ALERTS, 1);
        } else {
            partitionedResult.addInt(ActivityAlertProcessorExecutor.RET_UNMATCHED_ALERTS, 1);
        }

        if (processor.getMessageRepo() != null) {
            partitionedResult.addMessages(processor.getMessageRepo().getMessages());
        }

        if (_partitionedResults == null) {
            _partitionedResults = new ArrayList<TaskResult>();
        }
        _partitionedResults.add(partitionedResult);
    }

    private void mergePartitionedResults(TaskResult masterResult) {
        masterResult.assimilateResults(_partitionedResults);
    }

    private void updatePartitionIdList(List<String> remainingAlertIds) throws GeneralException {
        String idString = sailpoint.tools.Util.listToCsv(remainingAlertIds);
        String compressed = Compressor.compress(idString);
        _partition.setAttribute(AlertProcessorRequestExecutor.ARG_ALERT_ID_LIST, compressed);

    }

    @Override
    public boolean terminate() {
        this._terminated = true;

        return true;
    }

    private List<AlertDefinition> getAlertDefs() throws GeneralException {
        Filter f = null;
        f = (Filter) _request.getAttribute(ARG_ALERT_DEF_FILTER);
        QueryOptions ops = new QueryOptions();
        ops.add(f);
        List<AlertDefinition> defs = _context.getObjects(AlertDefinition.class, ops);
        //Fully load the alertDefinitions so decache won't cause destruction
        for (AlertDefinition d : Util.safeIterable(defs)) {
            d.load();
        }

        return defs;
    }
}
