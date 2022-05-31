/* (c) Copyright 2013 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * Package decl.
 */
package sailpoint.request;

/**
 * Imports.
 */
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashMap;
import java.util.HashSet;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.Identitizer;
import sailpoint.api.RequestManager;
import sailpoint.api.SailPointContext;
import sailpoint.api.Meter;
import sailpoint.object.Attributes;
import sailpoint.object.IdentityTrigger;
import sailpoint.object.Partition;
import sailpoint.object.Request;
import sailpoint.object.RequestState;
import sailpoint.object.Source;
import sailpoint.object.TaskResult;
import sailpoint.task.IdentityRefreshExecutor;
import sailpoint.task.IdentityRefreshExecutor.RefreshWorkerPool;
import sailpoint.task.TaskMonitor;
import sailpoint.tools.Compressor;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;

/**
 * The executor that handles partitioned identity refresh
 * requests created by IdentityRefreshExecutor.
 *
 */
public class IdentityRefreshRequestExecutor extends AbstractRequestExecutor {

    private static Log log = LogFactory.getLog(IdentityRefreshRequestExecutor.class);

    /**
     * The argument key that holds the task definition name. This will be used for
     * the source of the aggregation.
     */
    public static final String ARG_TASK_DEFINITION_NAME = "taskDefinitionName";

    /**
     * The argument key that holds the list of identity ids.
     */
    public static final String ARG_IDENTITY_LIST = "identityList";

    /**
     * The argument key that holds the partition.
     */
    public static final String ARG_PARTITION = "partition";

    /**
     * Task argument key to indicate the task executor has determined that
     * Identity Processing Threshold will be checked.
     * The Identity Refresh Executor makes this determination before spawning
     * the partition requests and passes it in to avoid having to recalculate it
     * in each PHASE_REFRESH.
     */
    public static final String ARG_CHECK_IDENTITY_PROCESSING_THRESHOLD = "checkIdentityProcessingThresholds";

    //////////////////////////////////////////////////////////////////////
    //
    // IdentityRefreshRequestExecutor Phases
    //
    // Added for the new Identity Processing Threshold feature
    //
    //////////////////////////////////////////////////////////////////////
    /**
     * The request phase responsible for refreshing the identities
     * If IdentityRefreshExecutor has determined thresholds should be checked:
     *   The identities will be refreshed but their IdentityChangeEvents will not be processed.
     *   Instead the threshold values will be saved for later comparison
     * If IdentityRefreshExecutor has determined thresholds should not be checked:
     *   The identities will be refreshed and their IdentityChangeEvents will be processed
     *   (if ARG_PROCESS_TRIGGERS is true).
     */
    public static final int PHASE_REFRESH = 1;

    /**
     * The request phase responsible for checking threshold values (if they exist).
     * If any thresholds are reached:
     *   Fail the task with messaging for which thresholds were exceeded
     * If no thresholds are reached:
     *   Partition out the ids needing to have their events processed and spawn
     *   requests for PHASE_PROCESS_EVENTS phase
     */
    public static final int PHASE_CHECK_THRESHOLDS = 2;

    /**
     * The request phase responsible for processing the events of identities that had
     * their IdentityChangeEvents held for threshold checks in the PHASE_REFRESH phase
     */
    public static final int PHASE_PROCESS_EVENTS = 3;

    //////////////////////////////////////////////////////////////////////
    //
    // Identity Processing Threshold Task Result Attributes
    //
    //////////////////////////////////////////////////////////////////////
    /**
     * The argument key that holds the total number of identities that are spread across all the partitions
     * that are processed during PHASE_REFRESH.  This number is needed during the PHASE_CHECK_THRESHOLDS phase
     * to be used as the denominator for calculating if percentage type thresholds have been met or exceeded.
     * This argument is stored in the master task result and populated by the IdentityRefreshExecutor
     */
    public static final String RESULT_IDENTITY_PROCESSING_TOTAL_IDS_FOR_THRESHOLD_PERCENTAGES = "totalIdsForThresholdPercentages";

    /**
     * The argument key that holds the partitions Threshold Map
     */
    public static final String RESULT_IDENTITY_PROCESSING_THRESHOLD_COUNTS = "thresholdCounts";

    /**
     * The argument key that holds the partitions set of identities ids that
     * generated an IdentityChangeEvent during the PHASE_REFRESH phase.
     */
    public static final String RESULT_IDENTITY_PROCESSING_IDS_WITH_EVENTS = "idsWithEvents";

    /**
     * The terms, request and partition, are confusingly used interchangeably at times.
     * Even though this refers to the TaskResult associated with a given request,
     * it's called the "partitionResult"
     */
    public static final String REQUEST_ARG_PARTITION_RESULT = "partitionResult";

    private static final String TASK_ARG_TASK_RESULT_PARTITIONS = "taskResultPartitions";

    /**
     * Pool of RefreshWorkers that we feed identity ids to refresh
     * in parallel.
     */
    RefreshWorkerPool _pool;

    /**
     * Termination flag.  Unlike other requests, partitioned tasks are expected
     * to run long and support termination.
     */
    boolean _terminate;

    IdentityRefreshExecutor.RefreshState _refreshState;

    TaskMonitor _taskMonitor;

    /**
     * The total number of threshold counts for this request.
     * Occasionally referred to as the threshold map.
     *
     * In a PHASE_REFRESH request:
     *   map of IdentityTrigger Ids (key) paired to their number of
     *   identities matching that trigger (value) populated from the
     *   RefreshWorkerPool after identities have been refreshed.
     * In a PHASE_CHECK_THRESHOLD request:
     *   map of IdentityTrigger Ids (key) paired to their number of
     *   identities matching that trigger (value) populated from the
     *   partitioned results populated by the PHASE_REFRESH executions.
     */
    Map<String, Integer> _totalThresholdCounts;

    /**
     * The list of Identity ids that had events held in PHASE_REFRESH request.
     *
     * In a PHASE_REFRESH request:
     *   populated from the RefreshWorkerPool after identities have been refreshed.
     * In a PHASE_CHECK_THRESHOLD request:
     *   populated from the partitioned results populated by the PHASE_REFRESH executions.
     *   Used as a list of identities to partition out into spawned requests for PHASE_PROCESS_EVENTS phase
     */
    Set<String> _totalIdsWithEvents;


    //////////////////////////////////////////////////////////////////////
    // RequestState map attributes
    //////////////////////////////////////////////////////////////////////


    /**
     * Performs various phases of a partitioned Identity Refresh Task.
     *
     * @param context The context.
     * @param request The request.
     * @param args The arguments.
     * @throws RequestPermanentException
     * @throws RequestTemporaryException
     */
    public void execute(SailPointContext context, Request request, Attributes<String,Object> args)
        throws RequestPermanentException, RequestTemporaryException {

        _taskMonitor = new TaskMonitor(context, request);

        int phase = request.getPhase();
        try {
            switch (phase) {
                case PHASE_REFRESH:
                    refresh(context, request, args);
                    break;
                case PHASE_CHECK_THRESHOLDS:
                    checkTaskThresholds(context, args);
                    break;
                case PHASE_PROCESS_EVENTS:
                    processEventsOnly(context, request, args);
                    break;
                default:
                    break;
            }
        } catch (GeneralException e) {
            // No need to log here because the various methods already handled that
            throw new RequestPermanentException(e);
        }
    }

    /**
     * processEventsOnly is used to handle the requests spawned by the PHASE_CHECK_THRESHOLDS phase.
     * The identities should have already been refreshed and the RefreshWorkerPool will use the
     * identitizer to only process their events via its doTriggers method
     *
     * The expectations of how this method would be used require the following attributes to be preconfigured as:
     * processTriggers = true
     * processEventsOnly = true
     * holdEventProcessing = false
     * @throws RequestPermanentException
     */
    private void processEventsOnly(SailPointContext context, Request request, Attributes<String,Object> args) throws RequestPermanentException {
        String taskDefinitionName = request.getString(ARG_TASK_DEFINITION_NAME);
        Partition partition = (Partition) request.getAttribute(ARG_PARTITION);
        if (partition == null) {
            if (log.isErrorEnabled()) {
                log.error("Error encountered while trying to process events partition: " + request.getName() + ", null partition.");
            }
            return;
        }

        //Initialize refreshState
        _refreshState = initializeState(_taskMonitor);
        if (_refreshState == null) {
            //Something went wrong
            return;
        }

        boolean _processTriggers = args.getBoolean(Identitizer.ARG_PROCESS_TRIGGERS);
        boolean _processEventsOnly = args.getBoolean(IdentityRefreshExecutor.ARG_PROCESS_EVENTS_ONLY);
        boolean _holdEventProcessing = args.getBoolean(Identitizer.ARG_HOLD_EVENT_PROCESSING);

        if (_processTriggers && _processEventsOnly && !_holdEventProcessing) {
            try {
                String idStringCompressed = (String) partition.getAttribute(ARG_IDENTITY_LIST);
                String idListString = Compressor.decompress(idStringCompressed);
                List<String> idList = Util.csvToList(idListString);

                _pool = new RefreshWorkerPool(context, _taskMonitor, args, Source.Task,
                        taskDefinitionName, _refreshState);

                for (String id : idList) {
                    if (isTerminated()) {
                        break;
                    } else {

                        try {
                            if (_refreshState != null && !Util.isEmpty(_refreshState.getProcessedIds())
                                    && _refreshState.getProcessedIds().contains(id)) {
                                //Previously processed.
                                if (log.isDebugEnabled()) {
                                    log.debug("ID[" + id + "] was previously processed. Skipping");
                                }
                                continue;
                            }

                            if (log.isDebugEnabled()) {
                                log.debug("Queueing id '" + id + "' for event only processing");
                            }

                            // This may block and throw if the maximum number
                            // of exceptions is reached in the threads.
                            _pool.queue(id);
                        } catch (GeneralException e) {
                            // go through a normal termination so we can
                            // get the error messages into the result.
                            Message msg = e.getMessageInstance();
                            msg.setType(Message.Type.Error);

                            TaskResult result = _taskMonitor.getTaskResult();
                            result.addMessage(msg);

                            _pool.terminate();
                            break;
                        }
                    }
                }

                _pool.shutdown();

                // merge the meters of the main thread with the worker threads
                Meter.MeterSet meters = Meter.getThreadMeters();
                _pool.publishMeters(meters);

                log.debug("=== finishing request + " + request.getName());
                TaskResult result = _taskMonitor.lockPartitionResult();

                try {
                    if (isTerminated()) {
                        if (_pool != null) {
                            _pool.terminate();
                        }
                        result.addMessage(Message.warn(MessageKeys.TASK_MSG_TERMINATED));
                        result.setTerminated(true);
                    } else {
                        if (_pool != null) {
                            _pool.saveResults(result, true);
                        }
                    }
                } finally {
                    _taskMonitor.commitPartitionResult();
                }

            } catch (GeneralException ex) {
                if (log.isErrorEnabled()) {
                    log.error("Error encountered while trying to complete PHASE_PROCESS_EVENTS Phase: " + request.getName(), ex);
                }
                throw new RequestPermanentException(ex);
            }
        } else {
            if (log.isErrorEnabled()) {
                log.error("Misconfiguration encountered while trying to complete PHASE_PROCESS_EVENTS Phase: " + request.getName());
            }
        }
    }

    /**
     *  checkTaskThresholds should be called by a single request after all of the PHASE_REFRESH requests have completed
     *  The PHASE_CHECK_THRESHOLDS request will aggregate (sum up) all of the Identity Processing Threshold data
     *  generated by the PHASE_REFRESH requests and determine if any thresholds have been met.
     *    If they have a message for each Threshold met will be added along with a final error message
     *    If none have, the PHASE_PROCESS_EVENTS requests are spawned (if any are needed).
     */
    public void checkTaskThresholds(SailPointContext context, Attributes<String,Object> args) {
        boolean _thresholdExceeded = false;

        TaskResult result = _taskMonitor.getTaskResult();
        try {
            //Lets see what is all inside this TaskResult
            Map<String, Object> resultAttributes = result.getAttributes();
            int totalForThresholdPercentages = Util.getInt(resultAttributes, IdentityRefreshRequestExecutor.RESULT_IDENTITY_PROCESSING_TOTAL_IDS_FOR_THRESHOLD_PERCENTAGES);

            if (totalForThresholdPercentages != 0) {
                // sum the Identity Processing Threshold data saved in PHASE_REFRESH executions
                // this saves the data into the _totalIdsWithEvents and _totalThresholdCounts
                sumIdentityProcessingThresholdResults(result);

                if (_totalThresholdCounts != null) {
                    // check for any threshold that was exceeded
                    for (Map.Entry<String, Integer> entry : _totalThresholdCounts.entrySet()) {
                        // for each IdentityTrigger verify the count is not equal or over its threshold
                        String triggerId = entry.getKey();
                        IdentityTrigger trigger = context.getObjectById(IdentityTrigger.class, triggerId);
                        String thresholdMaxStr = trigger.getIdentityProcessingThreshold();
                        if(!Util.isNullOrEmpty(thresholdMaxStr)) {
                            if(Util.isNumeric(thresholdMaxStr)) {
                                float thresholdMax = Util.atof(thresholdMaxStr);
                                float thresholdFound = _totalThresholdCounts.get(triggerId);
                                String messageKey = MessageKeys.TASK_MSG_WARN_IDENTITY_REFRESH_THRESHOLD_EXCEEDED_FIXED;
                                // the thresholdFound needs to be adjusted for percentage based IdentityTriggers
                                if (Util.nullSafeCaseInsensitiveEq(trigger.getIdentityProcessingThresholdType(), "percentage")) {
                                    thresholdFound = (((float) _totalThresholdCounts.get(triggerId) / (float) totalForThresholdPercentages) * 100);
                                    messageKey = MessageKeys.TASK_MSG_WARN_IDENTITY_REFRESH_THRESHOLD_EXCEEDED_PERCENT;
                                }
                                if (thresholdMax <= thresholdFound) {
                                    _thresholdExceeded = true;
                                    Message msg = new Message(Message.Type.Warn, messageKey,
                                            _totalThresholdCounts.get(triggerId), totalForThresholdPercentages,
                                            trigger.getName(), thresholdMax);
                                    result.addMessage(msg);
                                    if (log.isDebugEnabled()) {
                                        log.debug("Threshold check for " + trigger.getName() + " met or exceeded limit of " + thresholdMax);
                                    }
                                }
                            } else {
                                Message msg = new Message(Message.Type.Error,
                                        MessageKeys.TASK_MSG_ERROR_IDENTITY_REFRESH_THRESHOLD_INVALID_VALUE,
                                        trigger.getName());
                                result.addMessage(msg);
                            }
                        }
                    }
                }
            }

            TaskResult partitionResult = _taskMonitor.lockPartitionResult();

            try {
                if (isTerminated()) {
                    partitionResult.addMessage(Message.warn(MessageKeys.TASK_MSG_TERMINATED));
                    partitionResult.setTerminated(true);
                } else {
                    if (_thresholdExceeded) {
                        partitionResult.addMessage(Message.error(MessageKeys.TASK_MSG_ERROR_IDENTITY_REFRESH_THRESHOLD_EXCEEDED));
                    } else {
                        if (log.isDebugEnabled()) {
                            log.debug("Thresholds checked. None exceeded. Spawning process events requests");
                        }
                        spawnProcessEventsRequests(context, args);
                    }
                }
            }
            finally {
                _taskMonitor.commitPartitionResult();
            }
        }
        catch (Throwable t) {
            // something seriously wrong with Hibernate
            t.printStackTrace();
            log.error("Unable to evaluate thresholds in TaskResult: " + result.getName());
            log.error(t);
        }
    }

    /**
     * Performs a refresh on the identities that are part of this partition.
     * If Identity Processing Threshold is to be checked the results from the RefreshWorkerPool are
     * stored in the task results for later usage in the PHASE_CHECK_THRESHOLD phase.
     * If Identity Processing Threshold is not to be checked this will be the only phase of execution for
     * each of the partitions calling IdentityRefreshRequestExecutor.
     * @throws RequestPermanentException
     */
    private void refresh(SailPointContext context, Request request, Attributes<String,Object> args) throws RequestPermanentException {
        String taskDefinitionName = request.getString(ARG_TASK_DEFINITION_NAME);
        Partition partition = (Partition) request.getAttribute(ARG_PARTITION);

        if (partition == null) {
            if (log.isErrorEnabled()) {
                log.error("Error encountered while trying to refresh partition: " + request.getName() + ", null partition.");
            }
            return;
        }

        //Initialize refreshState
        _refreshState = initializeState(_taskMonitor);
        if (_refreshState == null) {
            //Something went wrong
            return;
        }

        boolean _checkIdentityProcessingThresholds = args.getBoolean(ARG_CHECK_IDENTITY_PROCESSING_THRESHOLD);

        try {
            String idStringCompressed = (String)partition.getAttribute(ARG_IDENTITY_LIST);
            String idListString = Compressor.decompress(idStringCompressed);
            List<String> idList = Util.csvToList(idListString);

            _pool = new RefreshWorkerPool(context, _taskMonitor, args, Source.Task,
                                          taskDefinitionName, _refreshState);

            for (String id : idList) {
                if (isTerminated()) {
                    break;
                } else {

                    try {
                        if (_refreshState != null && !Util.isEmpty(_refreshState.getProcessedIds())
                                && _refreshState.getProcessedIds().contains(id)) {
                            //Previously processed.
                            if (log.isDebugEnabled()) {
                                log.debug("ID[" + id +"] was previously processed. Skipping");
                            }
                            continue;
                        }

                        if ( log.isDebugEnabled()) {
                            log.debug("Queueing id '" + id + "' for refresh processing");
                        }

                        // This may block and throw if the maximum number
                        // of exceptions is reached in the threads.
                        _pool.queue(id);
                    }
                    catch (GeneralException e) {
                        // go through a normal termination so we can
                        // get the error messages into the result.
                        Message msg = e.getMessageInstance();
                        msg.setType(Message.Type.Error);
    
                        TaskResult result = _taskMonitor.getTaskResult();
                        result.addMessage(msg);
                        
                        _pool.terminate();
                        break;
                    }
                }
            }

            _pool.shutdown();

            if (_checkIdentityProcessingThresholds) {
                _totalThresholdCounts = new HashMap<>();
                _totalIdsWithEvents = new HashSet<>();

                // sum ids with events from pool workers
                _totalIdsWithEvents = _pool.getIdsWithEvents();

                // sum threshold from pool workers
                _totalThresholdCounts = _pool.getThresholdCountTotals();
            }

            // merge the meters of the main thread with the worker threads
            Meter.MeterSet meters = Meter.getThreadMeters();
            _pool.publishMeters(meters);


            log.debug("=== finishing request + " + request.getName());
            TaskResult result = _taskMonitor.lockPartitionResult();
            int total = partition.getSize();

            try {
                if (isTerminated()) {
                    if (_pool != null) {
                        _pool.terminate();
                    }
                    result.addMessage(Message.warn(MessageKeys.TASK_MSG_TERMINATED));
                    result.setTerminated(true);
                } else {
                    result.setAttribute(IdentityRefreshExecutor.RET_TOTAL, Util.itoa(total));
                    if (_pool != null) {
                        _pool.saveResults(result, true);
                        // if Identity Processing Threshold is true, see if there are any ids with events and threshold counts
                        if (_checkIdentityProcessingThresholds) {
                            if (Util.size(_totalIdsWithEvents) > 0) {
                                result.setAttribute(RESULT_IDENTITY_PROCESSING_IDS_WITH_EVENTS, _totalIdsWithEvents);
                                result.setAttribute(RESULT_IDENTITY_PROCESSING_THRESHOLD_COUNTS, _totalThresholdCounts);
                            }
                        }
                    }
                }
            }
            finally {
                _taskMonitor.commitPartitionResult();
            }

        } catch (GeneralException ex) {
            if (log.isErrorEnabled()) {
                log.error("Error encountered while trying to complete PHASE_REFRESH of identity refresh partition: " + request.getName(), ex);
            }
            throw new RequestPermanentException(ex);
        }
    }

    /**
     * Used by PHASE_CHECK_THRESHOLD to aggregate all of the Identity Processing Threshold data
     * from the earlier PHASE_REFRESH executions.
     * This method builds up the list of all Identity ids that had IdentityChangeEvents held back in PHASE_REFRESH
     * and also builds up the threshold map to determine if any thresholds were met
     * @param result
     */
    private void sumIdentityProcessingThresholdResults(TaskResult result) {
        Set<String> aggregatedIdsWithEvents = new HashSet<>();
        Map<String, Integer> aggregatedThresholdCounts = new HashMap<>();

        // The TaskResult result potentially holds IdentityProcessingThreshold results
        List<TaskResult> partitionResults = result.getPartitionResults();
        if (partitionResults == null || partitionResults.size() == 0) {
            // nothing to do here, no IdentityProcessingThreshold results to aggregate
            if (log.isDebugEnabled()) {
                log.debug("No threshold results available to aggregate");
            }
        }
        else {
            for (TaskResult pResult : partitionResults) {
                // sum up the ids with events.
                Set<String> partitionIdsWithEvents = new HashSet<>();
                partitionIdsWithEvents = (Set<String>)pResult.getAttribute(RESULT_IDENTITY_PROCESSING_IDS_WITH_EVENTS);
                if (!Util.isEmpty(partitionIdsWithEvents)) {
                    aggregatedIdsWithEvents.addAll(partitionIdsWithEvents);
                }
                // sum up all the threshold counts
                Map<String, Integer> partitionThresholdCounts = new HashMap<>();
                partitionThresholdCounts = (Map<String, Integer>)pResult.getAttribute(RESULT_IDENTITY_PROCESSING_THRESHOLD_COUNTS);
                if (!Util.isEmpty(partitionThresholdCounts)) {
                    // merge the partitions threshold counts of the aggregated map
                    for (Map.Entry<String, Integer> entry : Util.safeIterable(partitionThresholdCounts.entrySet())) {
                        String triggerId = entry.getKey();
                        Integer triggerCount = entry.getValue();
                        Integer count = aggregatedThresholdCounts.containsKey(triggerId) ? aggregatedThresholdCounts.get(triggerId) : 0;
                        aggregatedThresholdCounts.put(triggerId, count + triggerCount);
                    }
                }
            }
        }

        // save the aggregated Identity Processing Threshold data for CheckTaskThreshold to examine
        _totalIdsWithEvents = aggregatedIdsWithEvents;
        _totalThresholdCounts = aggregatedThresholdCounts;
        if (log.isDebugEnabled()) {
            log.debug("Finished Aggregating threshold results");
        }
    }

    /**
     * This method is used during the PHASE_CHECK_THRESHOLD phase
     * If no thresholds were met that phase spawns PHASE_PROCESS_EVENTS requests using this method.
     * @throws GeneralException
     */
    private void spawnProcessEventsRequests(SailPointContext context, Attributes<String,Object> args) throws GeneralException {
        // attempt to spawn the same number of partitions as the original task or fewer if needed
        // This number is off by one since the "PHASE_CHECK_THRESHOLDS" is a partition of the original result
        TaskResult result = _taskMonitor.getTaskResult();
        List<TaskResult> partitionResults = result.getPartitionResults();
        int partitionSize = partitionResults.size();
        // this number SHOULD be greater than 1
        if (partitionSize > 1) {
            partitionSize--; // to get rid of the PHASE_CHECK_THRESHOLDS partition from the count
        }

        List<Request> requestsToSpawn = new ArrayList<Request>();
        requestsToSpawn.addAll(buildProcessEventsRequests(context, args, partitionSize));
        // Launch the requests
        for (Request requestToSpawn : requestsToSpawn) {
            RequestManager.addRequest(context, requestToSpawn);
        }
    }

    /**
     * Used by spawnProcessEventsRequests to build PHASE_PROCESS_EVENTS request
     * in a manner that is similar to how IdentityRefreshExecutor creates the
     * original PHASE_REFRESH requests (using the IdentityRefreshRequestBuilder)
     * @param partitionSize
     * @return
     * @throws GeneralException
     */
    private List<Request> buildProcessEventsRequests(SailPointContext context, Attributes<String,Object> args, int partitionSize) throws GeneralException {
        List<Request> requests = new ArrayList<Request>();
        List<String> idList = new ArrayList<>(_totalIdsWithEvents);

        IdentityRefreshRequestBuilder requestBuilder = new IdentityRefreshRequestBuilder(context, _taskMonitor, args);

        List<Partition> partitions = requestBuilder.createPartitions(idList, partitionSize, "process events", MessageKeys.TASK_IDENTITY_REFRESH_PROCESS_EVENTS_PARTITION_NAME);

        for (Partition partition : partitions) {
            Request request = requestBuilder.createProcessEventRequest();
            request.setName(partition.getName());
            partition.getAttributes().putAll(request.getAttributes());
            request.put(ARG_PARTITION, partition);
            finishProcessEventsRequest(request);
            requests.add(request);
        }

        return requests;
    }

    /**
     * Used to finish PHASE_PROCESS_EVENTS requests for parts the builder could not do.
     * @param request
     * @throws GeneralException
     */
    private void finishProcessEventsRequest(Request request) throws GeneralException {
        TaskResult result = _taskMonitor.getTaskResult();
        // This is thoroughly confusing, but the getter in this case is also a setter
        TaskResult partitionResult = result.getPartitionResult(request.getName());
        request.setTaskResult(result);
        request.addAttribute(REQUEST_ARG_PARTITION_RESULT, partitionResult);
        addPartitionResultToTaskResult(partitionResult);
    }

    /**
     * Used by finishProcessEventsRequest to lock the master result of the task and add
     * the PHASE_PROCESS_EVENTS requests partition results to the master.
     * @param partitionResult
     * @throws GeneralException
     */
    private void addPartitionResultToTaskResult(TaskResult partitionResult) throws GeneralException {
        TaskResult result = _taskMonitor.lockMasterResult();
        try {
            List<TaskResult> partitionResults = (List<TaskResult>)result.get(TASK_ARG_TASK_RESULT_PARTITIONS);
            if (partitionResults == null) {
                partitionResults = new ArrayList<TaskResult>();
                result.addAttribute(TASK_ARG_TASK_RESULT_PARTITIONS, partitionResults);
            }
            // Don't overwrite existing partition results
            boolean alreadyExists = false;
            String partitionName = partitionResult.getName();
            for (TaskResult existingResult : partitionResults) {
                alreadyExists |= Util.nullSafeEq(existingResult.getName(), partitionName);
            }
            if (!alreadyExists) {
                partitionResults.add(partitionResult);
            }
        } finally {
            _taskMonitor.commitMasterResult();
        }
    }
    
    @Override
    public boolean terminate() {
        _terminate = true;
        return true;
    }

    /**
     * Provide an accessor for test classes not in the request package.
     */
    public boolean isTerminated() {
        return _terminate;
    }

    @Override
    public void saveState(TaskMonitor monitor, RequestState state, SailPointContext context, Object currentState)
            throws GeneralException {

        //Compress the completed list and copy to RequestState
        IdentityRefreshExecutor.RefreshState currState = (IdentityRefreshExecutor.RefreshState)currentState;
        if (currState != null) {
            Set<String> currentIds = currState.getProcessedIds();
            String compressed = Compressor.compress(Util.setToCsv(currentIds));
            state.setAttribute(IdentityRefreshExecutor.STATE_ARG_COMPRESSED_COMPLETED, compressed);
            state.setAttribute(IdentityRefreshExecutor.STATE_LAST_HOSTNAME, Util.getHostName());
            state.setAttribute(IdentityRefreshExecutor.STATE_LAST_UPDATE_TIME, new Date());

            context.saveObject(state);
            try {
                TaskResult result = monitor.lockPartitionResult();
                if (_pool != null) {
                    _pool.saveResults(result, false);
                }
            } catch (GeneralException e) {
                log.error("Error saving taskResult" + e);
            } finally {
                monitor.commitPartitionResult();
            }

            context.commitTransaction();
        }

    }

    protected IdentityRefreshExecutor.RefreshState initializeState(TaskMonitor monitor) {
        IdentityRefreshExecutor.RefreshState state = null;
        try {
            if (monitor != null) {
                state = new IdentityRefreshExecutor.RefreshState(monitor.getPartitionedRequestState(), monitor);
            } else {
                log.error("No TaskMonitor provided");
            }
        } catch (GeneralException ge) {
            log.error("Error initializing RequestState");
        }
        return state;
    }


}
