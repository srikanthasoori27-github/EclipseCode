/* (c) Copyright 2015 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.request;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.Identitizer;
import sailpoint.api.Provisioner;
import sailpoint.api.RequestManager;
import sailpoint.api.SailPointContext;
import sailpoint.api.Terminator;
import sailpoint.api.rolepropagation.RolePropagationRequestBuilder;
import sailpoint.api.rolepropagation.RolePropagationService;
import sailpoint.object.Attributes;
import sailpoint.object.Request;
import sailpoint.object.RoleChangeEvent;
import sailpoint.object.RoleChangeEvent.Status;
import sailpoint.object.TaskResult;
import sailpoint.provisioning.PlanEvaluator;
import sailpoint.task.TaskMonitor;
import sailpoint.tools.Compressor;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;

/**
 * A class for role propagation partitioning.
 * A role change event will be processed with help of multiple requests.
 * Requests for next role change event will be created only when
 * current event has been processed and is being deleted.
 *
 * There are two main types of role propagation partition requests.
 *   1 - Head and Tail partition requests which acts as a
 *       Tail for previous event and Head for next event.
 *       Tail part is responsible for
 *         a] Deleting processed event.
 *       Head part is responsible for
 *         b] Creating propagating or child partition requests for an event distributing
 *            identities across these propagating partition requests.
 *         c] Creating Tail partition for this event which will also act as Head for next event.
 *   2 - Propagating partition requests which propagate an event for given set of identities.
 *
 * As per the design of role propagation, if any propagating request
 * fails, task is terminated by canceling its tail partition request.
 * It is the responsibility of a failing request to mark task result with errors.
 *
 * For an event with assigned identities, propagating partition requests are not created.
 * These events are captured in task result separately under - number of events not propagated.
 *
 * Head-tail partition requests does not contain any result, but propagating requests
 * contain identity related result in individual partition requests.
 * Events related result is captured by head-tail requests but updated
 * as master task result and not as an individual partition result.
 */

public class RolePropagationRequestExecutor extends AbstractRequestExecutor {

    private static Log log = LogFactory.getLog(RolePropagationRequestExecutor.class);

    /*
     * Maximum number od error messages allowed in each partition result.
     */
    public static final int MAX_NUM_ERRORS = 5;

    /**
     * The context to use while processing the current request
     */
    private SailPointContext context;
    
    /**
     * The propagation request that is currently being processed
     */
    private Request request;
    
    /**
     * TaskMonitor required for updating progress and terminating master task result.
     */
    private TaskMonitor taskMonitor;

    /**
     * An object of RolePropagator.
     */
    private RolePropagationService propagationService;
    
    /**
     * The number of events attempted
     */
    private int eventTally;
        
    /**
     * The arguments that were passed to this request executor
     */
    private Attributes<String, Object> requestArgs;

    /**
     * Terminate flag, to indicate when you should terminate.
     */
    private boolean terminate;

    /**
     * Read a Role Change Event from database. Create Requests to process it and
     * a Request to delete it. The Role Change Event should be deleted only
     * after all associated identities have been processed.
     */
    public void execute(SailPointContext context, Request request,
                        Attributes<String, Object> args) throws RequestPermanentException {

        if (log.isDebugEnabled()) {
            log.debug("Executing request: " + request.getName() + " [Phase: " + request.getPhase() + "]");
        }
        
        prepare(context, request, args);

        int phase = request.getPhase();
        logPhase(phase);

        try {
            switch (phase) {
                case RolePropagationService.PHASE_TRANSITION:
                    transition();
                    break;
                case RolePropagationService.PHASE_PROVISION:
                    provision();
                    break;
                case RolePropagationService.PHASE_FINISH:
                    finish();
                default:
                    break;
            }
        } catch (GeneralException e) {
            // No need to log here because the various methods already handled that
            throw new RequestPermanentException(e);
        }
    }
    
    /**
     * Transition to the next phase.  The next phase depends on whether or not there 
     * are more valid RoleChangeEvents to process.  If there are, then we transition
     * to a RolePropagationService.PHASE_PROVISION phase.  If there aren't, then we transition
     * to the RolePropagationRequestService.PHASE_FINISH phase.
     * @throws GeneralException
     */
    private void transition() throws GeneralException {
        try {
            RoleChangeEvent previousEvent = (RoleChangeEvent) request.get(RolePropagationService.REQUEST_ARG_ROLE_CHANGE_EVENT),
                            event = null;
            if (log.isDebugEnabled()) {
                log.debug("On transition start -- Incoming events attempted: " + eventTally);
            }

            List<Request> requestsToSpawn = new ArrayList<Request>();
            
            //finish last event, and generate event processing details, which will be stored in master task result 
            Map<String,Object> eventProcessingDetails = finishLastEvent(previousEvent);
                        
            Message updateMessage = null;
            if (previousEvent != null && previousEvent.getStatus() == Status.Pruned) {
                int maxFailedAttempts = requestArgs.getInt(RolePropagationService.TASK_ARG_MAX_FAILED_ATTEMPTS);
                updateMessage = new Message(MessageKeys.TASK_ROLE_PROPAGATION_EVENT_PRUNED, previousEvent.getBundleName(), maxFailedAttempts);
            }

            //we need to add the event details before getNextEvent() to 
            //prevent getNextEvent() add empty event details first.
            if (updateMessage != null || eventProcessingDetails != null) {
                updateTaskResult(updateMessage, eventProcessingDetails);
            }
                        
            if (propagationService.isAboveFailureThreshold(previousEvent, requestArgs)) {
                terminate = true;
                cancelAction(request.getName(), propagationService.getErrors(), new GeneralException("Exceeded failure threshold"));
                terminateTask();
            }
                        
            if (log.isDebugEnabled()) {
                log.debug("After finishing the last event -- Events attempted: " + eventTally);
            }

            // Retrieve eventIds to process. getNextEvent will pull events from the head of the list until it
            // finds a valid event. The remainder will be kept for the next transition request.
            List<String> eventIds = request.getAttributes().getStringList(RolePropagationService.TRANSITION_ATTR_EVENT_IDS);

            if (!terminate) {
                event = propagationService.getNextEvent(request, taskMonitor, eventIds);
            }

            RolePropagationRequestBuilder requestBuilder =
                    new RolePropagationRequestBuilder(context, propagationService, event, taskMonitor, requestArgs);

            Request nextTransition = null;

            // Figure out the type of requests we need to launch
            if (event == null) {
                requestsToSpawn.add(requestBuilder.buildFinishRequest());
            } else {
                requestsToSpawn.addAll(requestBuilder.buildProvisionRequests());
                
                //get NumberOfIdentitiesToProcess for the event
                int numberOfIdentitiesToProcess = requestArgs.getInt(RolePropagationService.RESULT_EVENT_PROCESSING_DETAILS_NUMBER_TO_PROCESS);

                //get compressed newSkipList for the event
                String newSkipList = requestArgs.getString(RolePropagationService.REQUEST_ARG_EVENT_NEW_SKIP_LIST);
                
                List<String> partitionNames = getRequestNames(requestsToSpawn);
                nextTransition = requestBuilder.buildTransitionRequest(eventTally, false, eventIds, partitionNames);
                requestsToSpawn.add(nextTransition);
                
                //carry NumberOfIdentitiesToProcess for the event to transition Request
                nextTransition.put(RolePropagationService.RESULT_EVENT_PROCESSING_DETAILS_NUMBER_TO_PROCESS, numberOfIdentitiesToProcess);

                //carry compressed newSkipList for the event to transition Request
                nextTransition.put(RolePropagationService.REQUEST_ARG_EVENT_NEW_SKIP_LIST, newSkipList);
            }

            // Launch the requests
            for (Request requestToSpawn : requestsToSpawn) {
                RequestManager.addRequest(context, requestToSpawn);
            }
        } catch (Exception e) {
            log.error("The Role Propagation Task failed.", e);
            terminateTask();
            throw new RequestPermanentException(e);
        }
    }
    
    private void provision() throws GeneralException, RequestPermanentException {
        List<String> identitiesToProvision = propagationService.getIdentitiesFromRequest(request);
        RoleChangeEvent roleChangeEvent = 
                (RoleChangeEvent) request.get(RolePropagationService.REQUEST_ARG_ROLE_CHANGE_EVENT);
        boolean needsIdentityRefresh = isIdentityRefreshEnabled(),
                success = false;
        
        if (log.isDebugEnabled()) {
            log.debug("Provisioning this event:" + roleChangeEvent == null ? "null" : roleChangeEvent.toXml());
            log.debug("Provisioning for these Identities: " + identitiesToProvision.toString());
        }

        try {
            success = propagationService.provisionIdentities(taskMonitor, roleChangeEvent,
                    identitiesToProvision, needsIdentityRefresh);
        } catch (GeneralException e) {
            // Previously we would throw for any error and stop the role propagation.
            // Now, we only throw for errors that aren't specific to provisioning an identity.
            // In these cases, we still "fail fast" since it likely means there's a larger
            // system issue that needs to be resolved before role prop can run successfully.
            cancelAction(request.getName(), propagationService.getErrors(), e);
            terminateTask();
            throw new RequestPermanentException(e);
        }

        // Updating partition result.
        try {
            TaskResult partResult = taskMonitor.lockPartitionResult();
            partResult.setAttribute(RolePropagationService.RESULT_TOTAL,
                    propagationService.getTotalIdentityUpdates());

            if (!success) {
                log.debug("One or more identities failed provisioning.");
                
                // If we failed, also save the list of failed identity IDs to the partition result
                List<String> failedIds = propagationService.getFailedIdentities();
                String idsCsv = Util.listToCsv(failedIds);
                partResult.setAttribute(RolePropagationService.RESULT_FAILED_IDENTITIES,
                        Compressor.compress(idsCsv));

                //add error messages after all identities in the Request have been updated
                List<Message> errors = propagationService.getErrors();
                if (Util.size(errors) > MAX_NUM_ERRORS) {
                    partResult.addMessages(errors.subList(0, MAX_NUM_ERRORS-1));
                    //add Max error reached message
                    partResult.addMessage(Message.error(MessageKeys.TASK_OUT_ERROR_PROPAGATING_MORE,
                            propagationService.getBundleName(roleChangeEvent)));
                } else if (Util.size(errors) > 0) {
                    partResult.addMessages(errors);
                }

                Message error = new Message(Message.Type.Error, MessageKeys.TASK_ROLE_PROPAGATION_IDENTITY_FAILURE,
                        failedIds.size(),
                        propagationService.getBundleName(roleChangeEvent));
                partResult.addMessage(error);
            }

        } finally {
            taskMonitor.commitPartitionResult();
            if (log.isDebugEnabled()) {
                log.debug("Finished provision phase");
            }
        }
    }
    
    private void finish() {
        if (log.isDebugEnabled()) {
            log.debug("On finish -- Incoming events attempted: " + eventTally);
        }
                
        try {
            Message completionMessage = null;
            if (isTaskSuccessful()) {
                completionMessage = new Message(MessageKeys.TASK_ROLE_PROPAGATION_COMPLETE);
                if (log.isDebugEnabled()) {
                    log.debug("completed task result: " + taskMonitor.getTaskResult().toXml());   
                }
            } else {
                completionMessage = new Message(Message.Type.Error, MessageKeys.TASK_ROLE_PROPAGATION_FAILURES);

                if (log.isDebugEnabled()) {
                    log.debug("task result finished with failures: " + taskMonitor.getTaskResult().toXml());
                }
            }

            updateTaskResult(completionMessage, null);
        } catch (GeneralException ex) {
            if (log.isErrorEnabled()) {
                log.error("An exception occurred while trying to terminate task.");
            }
        }
    }
    
    private boolean isTaskSuccessful() {
        boolean isTaskSuccessful = true;
        List <TaskResult> partitionResults = taskMonitor.getTaskResult().getPartitionResults();
        for (TaskResult partitionResult : Util.safeIterable(partitionResults)) {
            // Don't include the current request, since it hasn't completed and doesn't have a status yet.
            if (!request.getName().equals(partitionResult.getName())) {
                isTaskSuccessful &= partitionResult.getCompletionStatus() == TaskResult.CompletionStatus.Success;
            }
        }
        return isTaskSuccessful;
    }
            
    private void logPhase(int phase) {        
        if (log.isDebugEnabled()) {
            String phaseString;
            switch (phase) {
                case RolePropagationService.PHASE_TRANSITION:
                    phaseString = "Transition";
                    break;
                case RolePropagationService.PHASE_PROVISION:
                    phaseString = "Provision";
                    break;
                case RolePropagationService.PHASE_FINISH:
                    phaseString = "Finish";
                default:
                    phaseString = "Invalid";
                    break;
            }

            log.debug("Processing a request in the " + phaseString + " phase: " + request.getName());
        }
    }
    
    /**
     * Returns the processing details for the event. 
     */
    private Map<String, Object> finishLastEvent(RoleChangeEvent previousEvent) throws GeneralException, RequestPermanentException {

        if (previousEvent != null) {
            Set<String> failedIdentities = new HashSet<>();
            List<String> requestNames = (List<String>) request.getAttribute(RolePropagationService.REQUEST_NAMES);
            int identityUpdateForThisEvent = 0;
            
            if (!Util.isEmpty(requestNames)) {
                // Get the latest TaskResult and inspect it for results from the last batch of requests.
                // If any contain failed identities, add them to a single combined list.
                TaskResult result = taskMonitor.getTaskResult();
                context.decache(result);
                result = context.getObjectById(TaskResult.class, result.getId());
                List<TaskResult> partitionResults = result.getPartitionResults();
                for (TaskResult partitionResult : partitionResults) {
                    if (requestNames.contains(partitionResult.getName())) {
                        String compressedFailedIdentities =
                                (String) partitionResult.getAttribute(RolePropagationService.RESULT_FAILED_IDENTITIES);
                        List<String> failedPartitionIdentities = Util.csvToList(Compressor.decompress(compressedFailedIdentities));
                        failedIdentities.addAll(failedPartitionIdentities);
                        identityUpdateForThisEvent += partitionResult.getInt(RolePropagationService.RESULT_TOTAL);
                    }
                }
            }
            
            //get new skipped list from transition request
            String compressedIdentities = request.getString(RolePropagationService.REQUEST_ARG_EVENT_NEW_SKIP_LIST);
            List<String> newSkipList = Collections.emptyList();
            if (!Util.isEmpty(compressedIdentities)) {
                String identitiesCsv = Compressor.decompress(compressedIdentities);
                newSkipList = Util.csvToList(identitiesCsv);
            }            

            //Calculate numberToProcess
            int numberToProcess = 0;
            if (Status.Pending.equals(previousEvent.getStatus())) {
                //Pending event, numberToProcess is the total identities
                numberToProcess = request.getInt(RolePropagationService.RESULT_EVENT_PROCESSING_DETAILS_NUMBER_TO_PROCESS)
                        + Util.size(newSkipList);
            } else if (Status.Failed.equals(previousEvent.getStatus())) {
                //Failed event, numberToProcess is the size of retry list
                numberToProcess = Util.size(previousEvent.getSkippedIdentityIds()) 
                        + Util.size(previousEvent.getFailedIdentityIds());
            }

            boolean eventSuccess = false;
            
            // If no failed or skipped identities, we're done and can safely delete the RoleChangeEvent.
            // If any identities failed, then update the RCE with the complete list.
            if (failedIdentities.isEmpty() && Util.isEmpty(newSkipList)) {
                propagationService.deleteEvent(previousEvent);
                eventSuccess = true;
            } else {
                int numPreviousFailures = Util.size(previousEvent.getFailedIdentityIds());
                int numPreviousSkips = Util.size(previousEvent.getSkippedIdentityIds());
                
                previousEvent.setFailedIdentityIds(failedIdentities);
                previousEvent.setSkippedIdentityIds(new HashSet<String>(newSkipList));

                if (isAttemptFailed(previousEvent, numPreviousFailures, numPreviousSkips)) {
                    previousEvent.incrementFailedAttempts();
                } else {
                    previousEvent.resetFailedAttempts();
                }
                
                previousEvent.setStatus(RoleChangeEvent.Status.Failed);
                context.saveObject(previousEvent);
                context.commitTransaction();
            }
                                    
            // At this stage, the request is no longer pending. It has either succeeded for failed.
            request.addInt(RolePropagationService.RESULT_EVENT_TALLY, 1);
            requestArgs.put(RolePropagationService.RESULT_EVENT_TALLY, request.get(RolePropagationService.RESULT_EVENT_TALLY));
            eventTally++;
            
            if (propagationService.isEventPrunable(previousEvent, requestArgs)) {
                previousEvent.setStatus(Status.Pruned);
                propagationService.deleteEvent(previousEvent);
            }
            
            //Create eventProcessingDetails, which will be stored in master result.            
            Map<String, Object> eventProcessingDetails = new HashMap<String, Object>();
            eventProcessingDetails.put(RolePropagationService.RESULT_EVENT_PROCESSING_DETAILS_BUNDLE_NAME, 
                    propagationService.getBundleName(previousEvent));
            eventProcessingDetails.put(RolePropagationService.RESULT_EVENT_PROCESSING_DETAILS_NUMBER_TO_PROCESS, 
                    numberToProcess);
            eventProcessingDetails.put(RolePropagationService.RESULT_EVENT_PROCESSING_DETAILS_NUMBER_SUCCEEDED, 
                    identityUpdateForThisEvent);
            
            if (eventSuccess) {
                eventProcessingDetails.put(RolePropagationService.RESULT_EVENT_PROCESSING_DETAILS_NUMBER_FAILED, 0);
                eventProcessingDetails.put(RolePropagationService.RESULT_EVENT_PROCESSING_DETAILS_NUMBER_SKIPPED, 0);
                eventProcessingDetails.put(RolePropagationService.RESULT_EVENT_PROCESSING_DETAILS_STATUS,
                        RoleChangeEvent.Status.Success);
            } else {
                eventProcessingDetails.put(RolePropagationService.RESULT_EVENT_PROCESSING_DETAILS_NUMBER_FAILED, 
                        Util.size(previousEvent.getFailedIdentityIds()));
                eventProcessingDetails.put(RolePropagationService.RESULT_EVENT_PROCESSING_DETAILS_NUMBER_SKIPPED,
                        Util.size(previousEvent.getSkippedIdentityIds()));
                eventProcessingDetails.put(RolePropagationService.RESULT_EVENT_PROCESSING_DETAILS_STATUS,
                        previousEvent.getStatus());
            }
            
            //log and store skipped message
            Message skippedMessage = null;

            if (!eventSuccess) {
                if (!Util.isEmpty(previousEvent.getSkippedIdentityIds())) {
                    String bundleName = propagationService.getBundleName(previousEvent);
                    skippedMessage = new Message(Message.Type.Error, MessageKeys.TASK_ROLE_PROPAGATION_IDENTITY_SKIPPED,
                            bundleName, previousEvent.getSkippedIdentityIds().size());
                    log.warn(skippedMessage);
                    
                    try {
                        TaskResult partResult = taskMonitor.lockPartitionResult();
                        partResult.addMessage(skippedMessage);

                    } finally {
                        taskMonitor.commitPartitionResult();
                    }
                }
            }

            return eventProcessingDetails;
        } else {
            return null;
        }
    }

    /*
     * @param updatedEvent RoleChangeEvent containing updated information about the last attempt
     * @param numPreviousFailures Number of Identities that previous failed to provision
     * @param numPreviousSkips Number of Identities that were held up by other events on the previous run
     * @return true if the partitions pertaining to the current transition phase 
     * indicate that the last event made no progress
     */
    private boolean isAttemptFailed(RoleChangeEvent updatedEvent, int numPreviousFailures, int numPreviousSkips) {
        // If we are still being blocked by previous events, then we can't tell whether or not the attempt really 
        // would have failed, so optimistically assume it would have succeeded
        boolean isFailed;
        int numSkipped = Util.size(updatedEvent.getSkippedIdentityIds());
        if (numSkipped > 0) {
            isFailed = false;
        } else {
            int numCurrentFailures = Util.size(updatedEvent.getFailedIdentityIds());
            int numPrevious = numPreviousFailures + numPreviousSkips;
            // If the currentFailures match the number of pending Identities, 
            // then we've failed to make progress.
            isFailed = numCurrentFailures == numPrevious;

            // The logic above does not sufficiently address the situation where
            // we failed out of the gate, so on the initial go around, we call 
            // the attempt failed if every affected identity failed
            if (!isFailed && numPreviousFailures == 0) {
                isFailed = numCurrentFailures == updatedEvent.getAffectedIdentityCount();
            }
        }
                
        return isFailed;
    }
    
    /*
     * @return true if we need to refresh the identity for policy checks.
     */
    private boolean isIdentityRefreshEnabled() {
        Attributes<String, Object> requestArgs = request.getAttributes();
        return (requestArgs.getBoolean(Identitizer.ARG_CHECK_POLICIES) ||
                Util.isNotNullOrEmpty((String) requestArgs.get(Identitizer.ARG_POLICIES)));
    }
    
    /**
     * @param terminate the terminate to set
     */
    public void setTerminate(boolean terminate) {
        this.terminate = terminate;
    }

    /**
     * @return the terminate
     */
    public boolean isTerminate() {
        return terminate;
    }

    private void prepare(SailPointContext context, Request request, Attributes<String, Object> requestArgs) {
        this.context = context;
        this.request = request;
        this.taskMonitor = new TaskMonitor(context, request);
        this.requestArgs = requestArgs;

        Attributes<String, Object> provisioningArgs = new Attributes<String, Object>();
        // We do not support retry
        provisioningArgs.put(PlanEvaluator.ARG_NO_RETRY_REQUEST, true);
        Provisioner provisioner = new Provisioner(context, provisioningArgs);
        Identitizer identitizer = new Identitizer(context, requestArgs);
        Terminator terminator = new Terminator(context);
        this.propagationService = new RolePropagationService(context, provisioner, identitizer, terminator);
        
        this.eventTally = request.getInt(RolePropagationService.RESULT_EVENT_TALLY);
    }

    /**
     * Update task result with events stats.
     * @param eventProcessingDetails 
     */
    private void updateTaskResult(Message msg, Map<String,Object> eventProcessingDetails) throws GeneralException {
        if (log.isDebugEnabled()) {
            log.debug("On task update -- Incoming events attempted: " + eventTally);
        }
        propagationService.updateTaskResult(taskMonitor, msg, eventProcessingDetails);
    }
    
    /**
     * It will be invoked by IIQ when task termination needs to be done.
     */
    @Override
    public boolean terminate() {
        if (null != propagationService) {
            propagationService.setTerminate(true);
        }
        setTerminate(true);
        return true;
    }

    
    /**
     * Set current partition result to terminated and set master result as
     * well to terminated if it wasn's yet set to terminated.
     * @param partitionName
     * @param messages
     * @param exception
     * @throws GeneralException
     */
    private void cancelAction(String partitionName, List<Message> messages, GeneralException exception) throws GeneralException {
        log.error("The Role Propagation Task failed, check partition requests for further details.", exception);

        cancelCurrentPartition(messages, exception);

        // Terminate parent task result if no other thread has already done that
        TaskResult result = taskMonitor.getTaskResult();
        
        if (!result.isTerminated()) {
            result = taskMonitor.lockMasterResult();
            try {
                result.addMessage(Message.error(MessageKeys.TASK_OUT_PARTITION_FAILED, partitionName));
                result.setTerminated(true);
            } finally {
                taskMonitor.commitMasterResult();
            }
        }
    }
    
    /**
     * Set current running partition result to terminated.
     * @param messages messages to add in the result
     * @param exception The exception that caused the partition to be canceled
     * @throws GeneralException
     */
    private void cancelCurrentPartition(List<Message> messages, GeneralException exception) throws GeneralException {
        try {
            TaskResult partitionResult = taskMonitor.lockPartitionResult();
            partitionResult.setTerminated(true);
            partitionResult.addException(exception);
            partitionResult.addMessages(messages);
        } catch (GeneralException e) {
            throw new RequestPermanentException(e);
        }
        finally {
            taskMonitor.commitPartitionResult();
        }
    }

    /**
     * Terminates the task. It will call terminate method
     * for all running partitions.
     */
    private void terminateTask() throws GeneralException {
        RolePropagationRequestBuilder requestBuilder = 
                new RolePropagationRequestBuilder(context, propagationService, null, taskMonitor, requestArgs);

        Request finishRequest = requestBuilder.buildFinishRequest();        
        RequestManager.addRequest(context, finishRequest);            
    }

    /**
     * Get the names of the provided list of requests
     * @param requests Requests to retrieve the names from
     * @return List of names
     */
    private List<String> getRequestNames(List<Request> requests) {
        return requests.stream().map(Request::getName).collect(Collectors.toList());
    }
}
