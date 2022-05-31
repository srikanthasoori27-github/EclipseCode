package sailpoint.api.rolepropagation;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.SailPointContext;
import sailpoint.object.Attributes;
import sailpoint.object.Partition;
import sailpoint.object.Request;
import sailpoint.object.RequestDefinition;
import sailpoint.object.RoleChangeEvent;
import sailpoint.object.TaskItemDefinition.Type;
import sailpoint.object.TaskResult;
import sailpoint.task.TaskMonitor;
import sailpoint.tools.Compressor;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;

/**
 * This class builds RolePropagation Requests for the various phases.  
 * Three phases are currently supported:
 *     PHASE_PROVISION - The phase that provisions the changes
 *     PHASE_TRANSITION - The phase that validates and/or partitions the events
 *     PHASE_FINISH - The phase that finalizes statistics and ties up loose ends
 * @author Bernie
 */
public class RolePropagationRequestBuilder {
    private RoleChangeEvent event;
    private RolePropagationService propagationService;
    private RequestDefinition requestDefinition;
    private TaskMonitor taskMonitor;
    private String taskDefinitionName;
    private Attributes<String, Object> args;
    private int maxPartitions;
    private int eventTally;
    
    private static final String ROLE_PROPAGATION_REQUEST_DEFINITION = "Role Propagation Partition";
    
    private static final String PARTITION_ARG_IDENTITY_LIST = "identityList";
    private static final String PARTITION_ARG_ROLE_CHANGE_EVENT = "roleEventObject";
    private static final String TASK_ARG_TASK_RESULT_PARTITIONS = "taskResultPartitions";
    
    private static final Log log = LogFactory.getLog(RolePropagationRequestBuilder.class);

    /**
     * Constructor for the RolePrpagationRequestBuilder
     * @param context SailPointContext used to build Requests
     * @param propagationService RolePropagationService used to build Requests
     * @param event In the Transition phase, this is the RoleChangeEvent that just completed.
     *              It will be null on the initial transition, but shoudl be non-null on subsequent
     *              transitions.
     *              In the Provision phase, this is the RoleChangeEvent for which Requests are being built. 
     *              This can be null if the builder is being used for Finish requests.
     * @param taskMonitor TaskMonitor for the current task
     * @param args The current Task arguments
     * @throws GeneralException
     */
    public RolePropagationRequestBuilder(SailPointContext context, RolePropagationService propagationService,
            RoleChangeEvent event, TaskMonitor taskMonitor, Attributes<String, Object> args) 
        throws GeneralException {
        this.event = event;
        this.taskDefinitionName = taskMonitor.getTaskResult().getDefinition().getName();
        this.taskMonitor = taskMonitor;
        this.maxPartitions = args.getInt(RolePropagationService.REQUEST_ARG_MAX_PARTITIONS);
        this.requestDefinition = context.getObjectByName(RequestDefinition.class, ROLE_PROPAGATION_REQUEST_DEFINITION);
        this.propagationService = propagationService;
        this.args = args;
        this.eventTally = args.getInt(RolePropagationService.RESULT_EVENT_TALLY);
    }
        
    public List<Request> buildProvisionRequests() throws GeneralException {
        List<Request> requests = new ArrayList<Request>();
        List<Partition> partitions = buildPartitions();

        for (Partition partition : partitions) {
            Request request = buildPropagationRequest(RolePropagationService.PHASE_PROVISION);
            if (log.isDebugEnabled()) {
                log.debug("Provisioning Partition in phase " + request.getPhase() + " has no dependency");
            }
            // TODO:  This begs the question of why requests and partitions are even separate objects
            // We take our name from the partition, and the partition takes its attributes from us.
            // Is there anything on the Partition that couldn't be kept on the Request? --Bernie
            request.setName(partition.getName());
            partition.getAttributes().putAll(request.getAttributes());
            request.put(RolePropagationService.REQUEST_ARG_PARTITION, partition);
            finishPropagationRequest(request, false);
            requests.add(request);
        }

        return requests;
    }

    /**
     * If provided, store the requests run prior to this transition request so that
     * we can grab their results and compile a list of failed identities (if any).
     */
    public Request buildTransitionRequest(int eventTally, boolean isInitial, List<String> eventIds,
                                          List<String> requestNames) throws GeneralException {
        Request request = buildTransitionRequest(eventTally, isInitial, eventIds);
        request.setAttribute(RolePropagationService.REQUEST_NAMES, requestNames);
        return request;
    }

    /**
     * @param eventTally The number of RoleChangeEvents attempted thus far
     * @param isInitial true if this is the first Transition request that's being created; false otherwise
     *                  This is necessary in order to distinguish between the first two transitions, since
     *                  both are being created in a state where zero events have been processed
     * @param eventIds List of RoleChangeEvent ids that we still need to process.
     * @return Request that is capable of effecting a transition
     * @throws GeneralException
     */
    public Request buildTransitionRequest(int eventTally, boolean isInitial, List<String> eventIds)
            throws GeneralException {
        if (log.isDebugEnabled()) {
            log.debug("Building transition request for " + eventTally + " isInitial = " + isInitial);
        }
        
        Request request = buildPropagationRequest(RolePropagationService.PHASE_TRANSITION);
        int nameSuffix = isInitial ? (eventTally + 1) : (eventTally + 2);
        request.setName(RolePropagationService.TRANSITION_REQUEST_NAME + " - " + nameSuffix);
        request.setInt(RolePropagationService.REQUEST_ARG_MAX_PARTITIONS, maxPartitions);
        request.setAttribute(RolePropagationService.TRANSITION_ATTR_EVENT_IDS, eventIds);
        if (isInitial) {
            // The initial transition has no dependency
            request.setDependentPhase(Request.PHASE_NONE);
        } else  {
            // Each transition depends on the provisioning events being complete
            request.setDependentPhase(RolePropagationService.PHASE_PROVISION);            
        }
        if (log.isDebugEnabled()) {
            log.debug("Transition request in phase " + request.getPhase() + 
                    " depends on provisioning request in phase " + request.getDependentPhase());
        }
        finishPropagationRequest(request, true);
        return request;
    }
    
    public Request buildFinishRequest() throws GeneralException {
        Request request = buildPropagationRequest(RolePropagationService.PHASE_FINISH);
        request.setName(RolePropagationService.FINISH_REQUEST_NAME);
        request.setDependentPhase(RolePropagationService.PHASE_TRANSITION);
        if (log.isDebugEnabled()) {
            log.debug("Finish request in phase " + request.getPhase() + 
                " depends on transition request in phase " + request.getDependentPhase());
        }
        finishPropagationRequest(request, true);
        return request;
    }
    
    /*
     * Build a request for the specified phase.  Valid phase values are:
     *   1 - RoleChangePropagationService.PHASE_PROVISION 
     *   2 - RoleChangePropagationService.PHASE_TRANSITION
     *   3 - RoleChangePropagationService.PHASE_FINISH
     * @param phase The phase that we're currently in
     * @return Request that matches the given phase
     * @throws GeneralException
     * @throws IllegalArgumentException
     */
    private Request buildPropagationRequest(int phase) throws GeneralException, IllegalArgumentException {
        validatePhase(phase);
        
        Request request = new Request(requestDefinition);
        Attributes<String, Object> argsForRequest = new Attributes<String, Object>(args);
        request.setAttributes(argsForRequest);
        request.put(RolePropagationService.REQUEST_ARG_TASK_DEFINITION_NAME, taskDefinitionName);
        request.setType(Type.RolePropagation);
        request.setPhase(phase);
        // This is just the default.  The caller is expected to adjust dependencies as needed.
        request.setDependentPhase(Request.PHASE_NONE);
        TaskResult result = taskMonitor.getTaskResult();
        request.setLauncher(result.getLauncher());
        argsForRequest.put(RolePropagationService.REQUEST_ARG_ROLE_CHANGE_EVENT, event);
                
        return request;
    }
    
    /**
     * Performs actions on the request that could not be completed before the specific
     * request building methods applied their specific actions
     * @param request The request that's being built
     * @param hideResult True to hide the request's results from the UI; false otherwise
     */
    private void finishPropagationRequest(Request request, boolean hideResult) throws GeneralException {
        TaskResult result = taskMonitor.getTaskResult();
        // This is thoroughly confusing, but the getter in this case is also a setter
        TaskResult partitionResult = result.getPartitionResult(request.getName());
        partitionResult.addAttribute(TaskResult.ATT_HIDDEN_PARTITION, hideResult);
        request.setTaskResult(result);
        request.addAttribute(RolePropagationService.REQUEST_ARG_PARTITION_RESULT, partitionResult);
        addPartitionResultToTaskResult(partitionResult);
    }
    
    private void addPartitionResultToTaskResult(TaskResult partitionResult) throws GeneralException {
        TaskResult result = taskMonitor.lockMasterResult();
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
            taskMonitor.commitMasterResult();
        }
    }    

    private void validatePhase(int phase) throws IllegalArgumentException {
        if (phase < RolePropagationService.PHASE_PROVISION || 
                phase > RolePropagationService.PHASE_FINISH) {
            throw new IllegalArgumentException("The current phase, " + phase + 
                ", does not meet expectations.  Valid phase values are " +
                "RolePropagationService.PHASE_PROVISION(1), " +
                "RolePropagationService.PHASE_TRANSITION(2), " + 
                "and RolePropagationService.PHASE_FINISH(3).");
        }
    }
    
    private List<Partition> buildPartitions() throws GeneralException {
        List<Partition> partitions = new ArrayList<Partition>();
        List<String> identitiesToPartition = propagationService.prepareIdentityListToProcess(event, this.args);

        int totalIdentities = identitiesToPartition.size();
        if (log.isDebugEnabled()) {
            log.debug("Total associated identities=" + totalIdentities);
        }

        int startOfCurrentPartition = 0;
        int numberOfPartitions = Math.min(totalIdentities, maxPartitions);
        if (log.isDebugEnabled()) {
            log.debug("Max partitions: " + maxPartitions + ", partitions to use: " + numberOfPartitions);
        }
        //Store number of identities to process
        this.args.put(RolePropagationService.RESULT_EVENT_PROCESSING_DETAILS_NUMBER_TO_PROCESS, totalIdentities);

        if (numberOfPartitions > 0) {
            int partitionSize = totalIdentities / numberOfPartitions;
            int remainder = totalIdentities % numberOfPartitions;
            
            for (int i = 0; i < numberOfPartitions; ++i) {
                int endOfCurrentPartition = startOfCurrentPartition + partitionSize;
                // Distribute the remainder as evenly among the partitions as possible
                if (remainder > 0) {
                    endOfCurrentPartition++;
                    remainder--;
                }
    
                endOfCurrentPartition = Math.min(totalIdentities, endOfCurrentPartition);
    
                List<String> identitiesInPartition = 
                    identitiesToPartition.subList(startOfCurrentPartition, endOfCurrentPartition);
    
                String partitionName = getPartitionName(startOfCurrentPartition, endOfCurrentPartition);
                Partition partition = buildPartition(partitionName, identitiesInPartition);
                partitions.add(partition);
                
                startOfCurrentPartition = endOfCurrentPartition;
            }
        }

        return partitions;
    }
    
    private Partition buildPartition(String partitionName, List<String> identitiesInPartition) 
        throws GeneralException {
        Partition partition = new Partition();
        partition.setName(partitionName);
        partition.setSize(identitiesInPartition.size());

        // compress the id list
        String identitiesToCompress = Util.listToCsv(identitiesInPartition);
        String compressed = Compressor.compress(identitiesToCompress);
        partition.put(PARTITION_ARG_IDENTITY_LIST, compressed);
        partition.put(PARTITION_ARG_ROLE_CHANGE_EVENT, event);
        if (log.isDebugEnabled()) {
            log.debug("Partition created: " + partition.getName());
            log.debug("Identities in partition: " + identitiesToCompress);
        }
        return partition;
    }
    
    private String getPartitionName(int startOfPartition, int endOfPartition) {
        Message msg = new Message(
                MessageKeys.TASK_ROLE_PROPAGATION_PARTITION_NAME,
                eventTally + 1, startOfPartition + 1, endOfPartition);
        String partitionName = msg.getLocalizedMessage();
        return partitionName;
    }
}
