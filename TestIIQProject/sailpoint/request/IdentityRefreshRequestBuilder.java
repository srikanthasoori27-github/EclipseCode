/*
 * (c) Copyright 2020 SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.request;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.Identitizer;
import sailpoint.api.SailPointContext;
import sailpoint.object.Attributes;
import sailpoint.object.Partition;
import sailpoint.object.Request;
import sailpoint.object.RequestDefinition;
import sailpoint.object.TaskResult;
import sailpoint.task.IdentityRefreshExecutor;
import sailpoint.task.TaskMonitor;
import sailpoint.tools.Compressor;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;

/**
 * This class builds IdentityRefresh Requests for the various phases
 * of partitioned Identity Refresh Tasks.
 * Three phases are currently supported:
 *     PHASE_REFRESH - The phase that refreshes identities and may hold back
 *                     IdentityChangeEvents if thresholds are being checked on IdentityTriggers
 *     PHASE_CHECK_THRESHOLDS - The phase that determines if any thresholds have been met
 *                              if not this phase spawns PHASE_PROCESS_EVENTS requests
 *     PHASE_PROCESS_EVENTS - The phase that fires the IdentityChangeEvents that were
 *                            held for processing in the PHASE_REFRESH requests
 */
public class IdentityRefreshRequestBuilder {

    //////////////////////////////////////////////////////////////////////
    //
    // Constants
    //
    //////////////////////////////////////////////////////////////////////
    /**
     * Task argument key which holds the RequestDefinition for the partition requests.
     */
    private static final String REQUEST_DEFINITION_NAME = "Identity Refresh Partition";


    private RequestDefinition _requestDefinition;
    private TaskMonitor _taskMonitor;
    private String _taskDefinitionName;
    private Attributes<String, Object> _args;

    private static final Log log = LogFactory.getLog(IdentityRefreshRequestBuilder.class);

    public IdentityRefreshRequestBuilder(SailPointContext context, TaskMonitor taskMonitor, Attributes<String, Object> args)
            throws GeneralException {
        this._taskDefinitionName = taskMonitor.getTaskResult().getDefinition().getName();
        this._taskMonitor = taskMonitor;
        this._requestDefinition = getRequestDefinition(context);
        this._args = args;
    }

    /**
     * Gets the request definition used to create partition Requests.
     *
     * @param context The context.
     * @return The request definition for the request that represents the partition.
     * @throws GeneralException
     */
    private RequestDefinition getRequestDefinition(SailPointContext context) throws GeneralException {
        RequestDefinition requestDef = context.getObjectByName(RequestDefinition.class, REQUEST_DEFINITION_NAME);
        if (null == requestDef) {
            throw new GeneralException("Could not find RequestDefinition.");
        }

        return requestDef;
    }

    /**
     * createPartitions is used by both the IdentityRefreshExecutor and the IdentityRefreshRequest Executor
     * The IdentityRefreshExecutor uses this to divide the ids from its idIterator into equally
     * distributed lists for refreshing.
     * The IdentityRefreshRequestExecutor uses this to divide the ids from the list of Identities with
     * IdentityChangeEvents held for processing after the thresholds have been checked, when spawning
     * PHASE_PROCESS_EVENTS requests.
     * @param idList the identity ids to distribute into the requested number of partitions
     * @param partitionSize the requested partition size (may be less if not enough identities exist)
     * @param step represents what phase this is being used for (ex: refresh vs process events)
     * @param messageKey the messageKey used to name the partition (ex: refresh and process events)
     * @return
     * @throws GeneralException
     */
    @SuppressWarnings("rawtypes")
    public List<Partition> createPartitions(List<String> idList, int partitionSize,
                                            String step, String messageKey) throws GeneralException {
        List<Partition> partitions = new ArrayList<Partition>();

        if (log.isDebugEnabled()) {
            log.debug("Total identities to " + step + " =" + idList.size());
        }

        if (Util.size(idList) > 0) {
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

                // Do not create empty partition request.
                if (start >= end) {
                    break;
                }

                List<String> subList = idList.subList(start, end);
                Message msg = new Message(messageKey, start+1, end);
                String partitionName = msg.getLocalizedMessage();

                start = end;

                Partition partition = new Partition();
                partition.setName(partitionName);
                partition.setSize(subList.size());

                //compress the id list
                String idString = Util.listToCsv(subList);
                String compressed = Compressor.compress(idString);
                partition.setAttribute(IdentityRefreshRequestExecutor.ARG_IDENTITY_LIST, compressed);
                if (log.isDebugEnabled()) {
                    log.debug("Partition created: " + partition.getName());
                    log.debug("Identities in partition: " + idString);
                }
                partitions.add(partition);
            }
        }
        return partitions;
    }

    /**
     * Creates partitioned request with a setPhase PHASE_REFRESH.
     * The partitions used by PHASE_REFRESH requests are previously created
     * and the IdentityRefreshExecutor builds one request per partition it created.
     *
     * @param partition
     * @param args
     * @param taskDefinitionName
     * @return
     */
    public Request createRefreshRequest(Partition partition,
                                         Attributes<String,Object> args,
                                         String taskDefinitionName) {
        if (log.isDebugEnabled()) {
            log.debug("Building refresh request");
        }
        Request request = new Request(_requestDefinition);
        request.setName(partition.getName());
        request.setAttributes(args);
        request.put(IdentityRefreshRequestExecutor.ARG_TASK_DEFINITION_NAME, taskDefinitionName);
        request.put(IdentityRefreshRequestExecutor.ARG_PARTITION, partition);
        request.setPhase(IdentityRefreshRequestExecutor.PHASE_REFRESH);

        return request;
    }

    /**
     * Creates partitioned request with a setPhase PHASE_CHECK_THRESHOLDS,
     * That is dependent on PHASE_REFRESH.
     * A single instance of this request is created by the IdentityRefreshExecutor
     * if the Identity Processing Threshold feature is enabled.
     * This request runs after all of the PHASE_REFRESH phases have completed
     * and is used to determine if any thresholds have been met and if additional
     * PHASE_PROCESS_EVENTS requests should be created.
     *
     * @param args
     * @param taskDefinitionName
     * @return
     */
    public Request createCheckThresholdRequest (Attributes<String,Object> args,
                                                 String taskDefinitionName) {
        if (log.isDebugEnabled()) {
            log.debug("Building check threshold request");
        }
        Message msg = new Message(MessageKeys.TASK_IDENTITY_REFRESH_CHECK_THRESHOLD_PARTITION_NAME);
        String partitionName = msg.getLocalizedMessage();
        Partition partition = new Partition();
        partition.setName(partitionName);

        Request request = new Request(_requestDefinition);
        request.setName(partition.getName());
        request.setAttributes(args);
        request.put(IdentityRefreshRequestExecutor.ARG_TASK_DEFINITION_NAME, taskDefinitionName);
        request.put(IdentityRefreshRequestExecutor.ARG_PARTITION, partition);
        request.setPhase(IdentityRefreshRequestExecutor.PHASE_CHECK_THRESHOLDS);
        request.setDependentPhase(IdentityRefreshRequestExecutor.PHASE_REFRESH);

        return request;
    }

    /**
     * Creates partitioned request with a setPhase PHASE_PROCESS_EVENTS,
     * that is dependent on PHASE_CHECK_THRESHOLDS.
     * The IdentityRefreshRequestExecutor creates these while executing the
     * PHASE_CHECK_THRESHOLDS and they are used to process the events of identities
     * that had their IdentityChangeEvents held back to see if any thresholds were met.
     * The ARG_PROCESS_EVENTS_ONLY (set to true) tells the RefreshWorker to only process events
     * The ARG_HOLD_EVENT_PROCESSING (set to false) tells the identitizer to process the events
     *
     * @return
     * @throws GeneralException
     */
    public Request createProcessEventRequest() throws GeneralException {
        if (log.isDebugEnabled()) {
            log.debug("Building process events request");
        }
        Request request = new Request(_requestDefinition);
        Attributes<String, Object> argsForRequest = new Attributes<String, Object>(_args);
        argsForRequest.putClean(IdentityRefreshExecutor.ARG_PROCESS_EVENTS_ONLY, Boolean.TRUE);
        argsForRequest.putClean(Identitizer.ARG_HOLD_EVENT_PROCESSING, Boolean.FALSE);
        request.setAttributes(argsForRequest);
        request.put(IdentityRefreshRequestExecutor.ARG_TASK_DEFINITION_NAME, _taskDefinitionName);
        request.setPhase(IdentityRefreshRequestExecutor.PHASE_PROCESS_EVENTS);
        request.setDependentPhase(IdentityRefreshRequestExecutor.PHASE_CHECK_THRESHOLDS);
        TaskResult result = _taskMonitor.getTaskResult();
        request.setLauncher(result.getLauncher());

        return request;
    }
}