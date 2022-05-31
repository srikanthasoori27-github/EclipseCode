/* (c) Copyright 2013 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * Package decl.
 */
package sailpoint.request;

/**
 * Imports.
 */
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.Aggregator;
import sailpoint.api.SailPointContext;
import sailpoint.api.TaskManager;
import sailpoint.object.Attributes;
import sailpoint.object.Request;
import sailpoint.object.RequestState;
import sailpoint.object.Source;
import sailpoint.object.TaskResult;
import sailpoint.task.ResourceIdentityScan;
import sailpoint.task.TaskMonitor;
import sailpoint.tools.Compressor;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

import java.util.Date;
import java.util.HashSet;
import java.util.Set;

/**
 * The executor that handles requests to aggregate an
 * application partition created by a connector.
 *
 * @author Dustin Dobervich <dustin.dobervich@sailpoint.com>
 */
public class AggregationRequestExecutor extends AbstractRequestExecutor {

    private static Log log = LogFactory.getLog(AggregationRequestExecutor.class);

    /**
     * The argument key that holds the task definition name. This will be used for
     * the source of the aggregation.
     */
    public static final String ARG_TASK_DEFINITION_NAME = "taskDefinitionName";

    /**
     * The aggregation phase we are to do.  For simplicity we use the same 
     * RequestExecutor to handle all agg phases.
     */
    public static final String ARG_PHASE = "aggregationPhase";

    /**
     * Aggregator what does the work.
     */
    Aggregator _aggregator;

    /**
     * Name of this partition for terminate() logging.
     */
    String _partition;

    /**
     * Performs aggregation on a partition of an application.
     *
     * @param context The context.
     * @param request The request.
     * @param args The arguments.
     * @throws RequestPermanentException
     * @throws RequestTemporaryException
     */
    public void execute(SailPointContext context, Request request, Attributes<String,Object> args)
        throws RequestPermanentException, RequestTemporaryException {

        TaskMonitor taskMonitor = null;

        try {
            String phase = request.getString(ARG_PHASE);
            if (phase == null) {
                // supposed to have set this, assume the primary
                phase = Aggregator.PHASE_AGGREGATE;
            }

            // save this for terminate()
            _partition = request.getName();

            // mimic aggregation task with no partitioning
            _aggregator = new Aggregator(context, args);
            _aggregator.setSource(Source.Task, args.getString(ARG_TASK_DEFINITION_NAME));

            taskMonitor = new TaskMonitor(context, request, this);
            _aggregator.setTaskMonitor(taskMonitor);

            _aggregator.execute(phase);
            _aggregator.finishResults();

            if (log.isInfoEnabled()) {
                context.decache();
                TaskResult result = taskMonitor.getTaskResult();
                result = context.getObjectById(TaskResult.class, result.getId());
                log.info("Final Request result");
                log.info(result.toXml());
            }
        } catch (GeneralException ex) {
            if (log.isErrorEnabled()) {
                log.error("Error encountered while trying to aggregate partition: " + request.getName(), ex);
            }

            throw new RequestPermanentException(ex);
        } finally {
            // if parent task is sequential then check errors in partition result
            // and terminate if necessary
            if (isSequential(args)) {
                terminateTaskOnError(context, taskMonitor.getTaskResult(), request.getName());
            }
        }
    }

    /**
     * Pass the termination request along to the aggregator.
     */
    public boolean terminate() {
        log.info("Received termination request for partition: " + _partition);
        _aggregator.setTerminate(true);
        return true;
    }

    /**
     * Terminates the parent task if an error was encountered in this partition.
     *
     * @param context The context.
     * @param taskResult The task result.
     * @param partitionName The partition name.
     */
    private void terminateTaskOnError(SailPointContext context, TaskResult taskResult, String partitionName) {
        TaskResult partitionTaskResult = getPartitionTaskResult(taskResult, partitionName);

        if (hasErrors(partitionTaskResult)) {
            terminateTask(context, taskResult);
        }
    }

    /**
     * Gets the task result for the specified partition from the parent task result.
     *
     * @param parentTaskResult The task result.
     * @param partitionName The partition name.
     * @return The task result for the partition or null if one can not be found.
     */
    private TaskResult getPartitionTaskResult(TaskResult parentTaskResult, String partitionName) {
        if (null == parentTaskResult) {
            return null;
        }

        return parentTaskResult.getPartitionResult(partitionName);
    }

    /**
     * Determines if the specified task result contains errors.
     *
     * @param taskResult The task result.
     * @return True if the task result contains errors, false otherwise.
     */
    private boolean hasErrors(TaskResult taskResult) {
        return null != taskResult && taskResult.hasErrors();
    }

    /**
     * Determines if the parent task has the sequential argument enabled.
     *
     * @param args The task arguments.
     * @return True if the sequential argument is enabled, false otherwise.
     */
    private boolean isSequential(Attributes<String, Object> args) {
        return args.getBoolean(ResourceIdentityScan.ARG_SEQUENTIAL, false);
    }

    /**
     * Terminates the task.
     *
     * @param context The context.
     * @param taskResult The task result.
     */
    private void terminateTask(SailPointContext context, TaskResult taskResult) {
        try {
            TaskManager taskManager = new TaskManager(context);
            taskManager.terminate(taskResult);
        } catch (GeneralException ex) {
            if (log.isErrorEnabled()) {
                log.error("An exception occurred while trying to terminate task.");
            }
        }
    }


    /**
     * Called by TaskMonitor to persist the currentState into the RequestState state
     * @param monitor the calling TaskMonitor
     * @param state the RequestState to be updated from the currentState, and persisted
     * @param context the database context
     * @param currentState the AccountAggregationState object, to be copied into state
     * @throws GeneralException
     */
    @Override
    public void saveState(TaskMonitor monitor, RequestState state, SailPointContext context, Object currentState)
            throws GeneralException {

        AccountAggregationState currState = (AccountAggregationState)currentState;
        if (currState != null) {
            Set<String> currentIds = currState.getCompletedIds();
            String compressed = Compressor.compress(Util.setToCsv(currentIds));
            state.setAttribute(Aggregator.ATT_COMPRESSED_COMPLETED_IDS, compressed);
            state.setAttribute(Aggregator.ATT_LAST_HOSTNAME, Util.getHostName());
            state.setAttribute(Aggregator.ATT_LAST_UPDATE_TIME, new Date());
            state.setAttribute(Aggregator.ATT_UPDATED_ID_COUNT, currState.getNumUpdatedIds());

            context.saveObject(state);

            if (log.isDebugEnabled()) {
                log.debug("Updating restartInfo of req '" + monitor.getPartitionedRequestName() + "'.  Total saved size is now " + currState.getCompletedIds().size() + " finished identities");
            }

            try {
                TaskResult result = monitor.lockPartitionResult();
                if (_aggregator != null) {
                    _aggregator.updateResults(result);
                }
            } catch (GeneralException e) {
                log.error("Error updating taskResult" + e);
            } finally {
                monitor.commitPartitionResult();
            }

            context.commitTransaction();
        }

    }

    /**
     * This class represents the checkpoint data needed to properly resume
     * an account aggregation Request after an unexpected termination of
     * the host or the requests's thread.
     */
    public static class AccountAggregationState {

        /**
         * the set of native identities that have been updated or created
         * during this aggregation request
         */
        Set _completedIds;

        /**
         * How many ids were updated, instead of created
         */
        int _numUpdatedIds;

        /**
         * When was this last saved
         */
        Date _lastUpdate;

        /**
         * Which host was the request running on
         */
        String _lastHostName;


        public AccountAggregationState() {
            _completedIds = new HashSet<String>();
            _lastHostName = Util.getHostName();
        }

        /**
         * Derserialize from a RequestState
         * @param reqState the RequestState from which to deserialize this object
         */
        public AccountAggregationState(RequestState reqState) {
            this();
            if (reqState != null) {
                //Update processedIds with RequestState
                String compressedIds = Util.otos(reqState.get(Aggregator.ATT_COMPRESSED_COMPLETED_IDS));
                if (Util.isNotNullOrEmpty(compressedIds)) {
                    try {
                        String rawStr = Compressor.decompress(compressedIds);
                        if (Util.isNotNullOrEmpty(rawStr)) {
                            _completedIds = Util.csvToSet(rawStr, true);
                        }
                    } catch (GeneralException ge) {
                        log.error("Error Decompressing Completed Ids" + ge);
                    }
                }
                else {
                    // only expected during unit tests
                    String completedIdsUncompressed = Util.otos(reqState.get(Aggregator.ATT_UNCOMPRESSED_COMPLETED_IDS));
                    if (!Util.isNullOrEmpty(completedIdsUncompressed)) {
                        String rawStr = completedIdsUncompressed;
                        if (!Util.isNullOrEmpty(rawStr)) {
                            Set<String> s = Util.csvToSet(rawStr, true);
                            _completedIds = s;
                        }
                    }
                }
                _numUpdatedIds = reqState.getInt(Aggregator.ATT_UPDATED_ID_COUNT);
                _lastHostName = (String)reqState.get(Aggregator.ATT_LAST_HOSTNAME);
                _lastUpdate = (Date)reqState.get(Aggregator.ATT_LAST_UPDATE_TIME);
            }
        }

        public void addCompletedId(String id) {
            _completedIds.add(id);
        }

        public void incrementUpdated(int increment) {
            _numUpdatedIds = _numUpdatedIds + increment;
        }

        public Set<String> getCompletedIds() { return _completedIds; }
        public int getNumUpdatedIds() { return _numUpdatedIds; }
        public String getLastHostName() { return _lastHostName; }
        public Date getLastUpdate() { return _lastUpdate; }
    }



}
