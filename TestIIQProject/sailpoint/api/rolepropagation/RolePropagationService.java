/* (c) Copyright 2015 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.api.rolepropagation;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.IdIterator;
import sailpoint.api.Identitizer;
import sailpoint.api.ObjectUtil;
import sailpoint.api.Provisioner;
import sailpoint.api.SailPointContext;
import sailpoint.api.Terminator;
import sailpoint.api.WorkflowSession;
import sailpoint.api.Workflower;
import sailpoint.object.AccountItem;
import sailpoint.object.Attributes;
import sailpoint.object.Bundle;
import sailpoint.object.Configuration;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.Partition;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.ProvisioningPlan.AccountRequest;
import sailpoint.object.ProvisioningPlan.AttributeRequest;
import sailpoint.object.ProvisioningPlan.Operation;
import sailpoint.object.ProvisioningProject;
import sailpoint.object.QueryOptions;
import sailpoint.object.Request;
import sailpoint.object.RoleAssignment;
import sailpoint.object.RoleChangeEvent;
import sailpoint.object.RoleChangeEvent.Status;
import sailpoint.object.RoleDetection;
import sailpoint.object.RoleTarget;
import sailpoint.object.TaskResult;
import sailpoint.object.Workflow;
import sailpoint.object.WorkflowLaunch;
import sailpoint.provisioning.PlanEvaluator;
import sailpoint.task.TaskMonitor;
import sailpoint.tools.Compressor;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;
import sailpoint.workflow.IdentityLibrary;

/**
 * RolePropagator is an utility class to propagate RoleChangeEvents for assigned Identities.
 * Previously in 6.4 it used to be part of RoleChangePropagator TaskExecutor itself,
 * in 7.0 we have refactored this into an utility class so that,
 * both sequential and partitioned RolPropagation task would consume it.
 * For sequential execution, RolePropagationTaskExecutor would pass control to this class.
 * For partitioned execution, RolePropagationRequestExecutor would consume different APIs of this class.
 *
 * @author ketan.avalaskar
 */
public class RolePropagationService {

    //////////////////////////////////////////////////////////////////////
    //
    // Constants
    //
    //////////////////////////////////////////////////////////////////////

    private static final Log log = LogFactory.getLog(RolePropagationService.class);
    
    // Request Definition
    public static final String ROLE_PROPAGATION_REQUEST_DEFINITION = "Role Propagation Partition";
    
    // Task argument names
    /**
     * The number of minutes the Role Propagation runs before it stops processing further
     * RoleChangeEvents
     */
    public static final String TASK_ARG_DURATION = "duration";
    
    /**
     * The maximum number of times that the task attempts to process a given event
     * without making any progress.  If this threshold is exceeded, the event
     * is pruned. 
     */
    public static final String TASK_ARG_MAX_FAILED_ATTEMPTS = "maxFailedAttempts";
    
    /**
     * The percentage of Identities that whose propagation is allowed to fail for a given 
     * event before the task stops processing further events altogether
     */
    public static final String TASK_ARG_MAX_FAILURE_THRESHOLD = "maxFailureThreshold";
    
    // Request argument names
    /** 
     * The name of the TaskDefinition for the currently running propagation task
     */
    public static final String REQUEST_ARG_TASK_DEFINITION_NAME = "taskDefinitionName";

    /**
     * The partition that the current request is processing
     */
    public static final String REQUEST_ARG_PARTITION = "partition";

    /**
     * The maximum number of partitions allowed on this task
     */
    public static final String REQUEST_ARG_MAX_PARTITIONS = "maxPartitions";

    /**
     * The time at which this task expires and stops further processing
     */
    public static final String REQUEST_ARG_END_TIME = "endTime";

    /**
     * The number of events that were originally intended to be processed
     */
    public static final String REQUEST_ARG_ORIGINAL_TOTAL_EVENTS = "originalTotalEvents";

    /**
     * The RoleChangeEvent that the current request is processing
     */
    public static final String REQUEST_ARG_ROLE_CHANGE_EVENT = "roleEventObject";

    /**
     * The names of the Requests that were run prior to the current Transition Request
     */
    public static final String REQUEST_NAMES = "requestNames";

    /**
     * The terms, request and partition, are confusingly used interchangeably at times.
     * Even though this refers to the TaskResult associated with a given request, 
     * it's called the "partitionResult"
     */
    public static final String REQUEST_ARG_PARTITION_RESULT = "partitionResult";
    
    /**
     * The new skip list for the event.
     */
    public static final String REQUEST_ARG_EVENT_NEW_SKIP_LIST = "eventNewSkipList";
    

    /**
     * The name given to transition reqeusts
     */
    public static final String TRANSITION_REQUEST_NAME = "Role Change Propagation Transition";

    /**
     * The transition attribute that holds RoleChangeEvent ids to process next.
     *
     * This list is treated like a queue, with an id popped off by getNextEvent at
     * each Transition request.
     */
    public static final String TRANSITION_ATTR_EVENT_IDS = "eventIds";

    /**
     * The name given to finish requests
     */
    public static final String FINISH_REQUEST_NAME = "Finish Role Change Propagation";
    
    // Role Change Propagation Request Phases
    /**
     * The request phase responsible for provisioning
     */
    public static final int PHASE_PROVISION = 1;

    /**
     * The request phase responsible for generating the next set of provisioning,
     * transition, and/or finish requests
     */
    public static final int PHASE_TRANSITION = 2;
    
    /**
     * The request phase responsible for finalizing the task result
     */
    public static final int PHASE_FINISH = 3;
    
    // Task Result Arguments
    /**
     * The total number of identities that were updated
     */
    public static final String RESULT_TOTAL = "total";

    /**
     * The total number of events
     */
    public static final String RESULT_EVENTS_TOTAL = "eventsTotal";

    /**
     * The number of events that were processed
     */
    public static final String RESULT_EVENTS_PROCESSED = "eventsProcessed";

    /**
     * The number of events that are awaiting processing by this task
     */
    public static final String RESULT_EVENTS_PENDING = "eventsPending";

    /**
     * The number of events attempted
     */
    public static final String RESULT_EVENT_TALLY = "eventTally";

    /**
     * The number of events that were failed
     */
    public static final String RESULT_EVENTS_FAILED = "eventsFailed";

    /**
     * The number of events that were succeeded
     */
    public static final String RESULT_EVENTS_SUCCEEDED = "eventsSucceeded";

    /**
     * The number of events that were pruned due to exceed max failure attempts
     */
    public static final String RESULT_EVENTS_PRUNED = "eventsPruned";

    /**
     * The list of identity IDs that failed in the current request
     */
    public static final String RESULT_FAILED_IDENTITIES = "failedIdentities";
    
    /**
     * The list of event processing details.
     */
    public static final String RESULT_EVENT_PROCESSING_DETAILS = "eventsProcessingDetails";
    
    /**
     * The Bundle Name in event processing details.
     */
    public static final String RESULT_EVENT_PROCESSING_DETAILS_BUNDLE_NAME = "eventBundle";
    
    /**
     * The number of Identities to Process in event processing details.
     */
    public static final String RESULT_EVENT_PROCESSING_DETAILS_NUMBER_TO_PROCESS = "identitiesToProcess";
    
    /**
     * The number of Identities skipped in event processing details.
     */
    public static final String RESULT_EVENT_PROCESSING_DETAILS_NUMBER_SKIPPED = "identitiesSkipped";
    
    /**
     * The number of Identities succeeded in event processing details.
     */
    public static final String RESULT_EVENT_PROCESSING_DETAILS_NUMBER_SUCCEEDED = "identitiesSucceeded";
    
    /**
     * The number of Identities failed in event processing details.
     */
    public static final String RESULT_EVENT_PROCESSING_DETAILS_NUMBER_FAILED = "identitiesFailed";
    
    /**
     * The event status in event processing details.
     */
    public static final String RESULT_EVENT_PROCESSING_DETAILS_STATUS = "eventStatus";
    
    
    /**
     * The total number of identities that were updated.  This is the copy of the
     * total that is used for debugging purposes.  This is necessary because the 
     * UI automagically aggregates the total from the partition results, so 
     * placing the overall total on the result itself causes the UI to double it.
     * Still, it's convenient to have an aggregated total visible for debugging
     * and tracing purposes, so we're keeping it in a separate attribute for that 
     * purpose
     */
    public static final String RESULT_DEBUG_TOTAL = "debugTotal";

    
    // Partition argument names
    /**
     * A compressed CSV of Identity IDs for the Identities on the partition 
     */
    public static final String PARTITION_ARG_IDENTITY_LIST = "identityList";
    
    
    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    private SailPointContext context;

    // Provisioner to compile and execute the project.
    private Provisioner provisioner;
    // Identitizer for refresh identity.
    private Identitizer identitizer;
    // Terminator to delete the events.
    private Terminator terminator;

    // Terminate the propagation.
    private boolean terminate = false;

    // Few statistics for the execution.
    private int totalIdentityUpdates = 0;

    // List of identities that failed provisioning
    private List<String> failedIdentities;

    private List<Message> errors;

    //////////////////////////////////////////////////////////////////////
    //
    // Constructor
    //
    //////////////////////////////////////////////////////////////////////

    public RolePropagationService(SailPointContext context, Provisioner provisioner, 
            Identitizer identitizer, Terminator terminator) {
        this.context = context;
        this.provisioner = provisioner;
        this.identitizer = identitizer;
        this.terminator = terminator;
        this.errors = new ArrayList<Message>();
        this.failedIdentities = new ArrayList<>();
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Getters / Setters
    //
    //////////////////////////////////////////////////////////////////////

    public int getTotalIdentityUpdates() {
        return totalIdentityUpdates;
    }

    public List<String> getFailedIdentities() {
        return failedIdentities;
    }

    public void setTerminate(boolean terminate) {
        this.terminate = terminate;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // API Methods
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Get Identity Iterator for passed role change event.
     */
    public IdIterator getIdIterator(RoleChangeEvent event) throws GeneralException {
        IdIterator idIterator = null;
        if (null != event) {
            List<Bundle> roleList = new ArrayList<Bundle>();

            Bundle roleObj = context.getObjectById(Bundle.class, event.getBundleId());
            if (roleObj == null && !Util.isEmpty(event.getBundleName())) {
                // Fall back on the name if the ID isn't there
                // This makes it possible to import RoleChangeEvents via XML files
                // for unit testing
                roleObj = context.getObjectByName(Bundle.class, event.getBundleName());
            }

            if (null == roleObj) {
                log.warn("Attempted to build partitions for a RoleChangeEvent with role ID " + event.getBundleId() + 
                        " and role name " + event.getBundleName() + 
                        ", but the role could not be found.");
            } else {
                roleList.add(roleObj);
                // Get all the identities to which the role is assigned.
                QueryOptions ops = new QueryOptions();
                // For delete role event, set filter for detected roles else set it for assigned roles.
                if (event.isBundleDeleted() && event.getProvisioningPlan() != null) {
                    ops.add(Filter.containsAll(Identity.ATT_BUNDLES, roleList));
                } else {
                    ops.add(Filter.containsAll(Identity.ATT_ASSIGNED_ROLES, roleList));
                }

                Iterator<Object[]> it = context.search(Identity.class, ops , "id");
                idIterator = new IdIterator(context, it);
                idIterator.setCacheLimit(1);
            }
        }
        
        if (idIterator == null) {
            List<Object[]> emptyList = Collections.emptyList();
            idIterator = new IdIterator(context, emptyList.iterator());
        }
        return idIterator;
    }

    /**
     * Pull all the RoleChangeEvent ids that we need to process during this task run.
     *
     * @param result The TaskResult for this run.
     * @return List of RoleChangeEvent ids to process.
     * @throws GeneralException
     */
    public List<String> getEventIds(TaskResult result) throws GeneralException {
        // Only consider events that were created before we started running
        QueryOptions ops = new QueryOptions(Filter.lt("created", result.getCreated()));
        ops.addOrdering("created", true);
        ops.addOrdering("id", true);

        Iterator<Object[]> it = context.search(RoleChangeEvent.class, ops , "id");
        IdIterator eventIdIterator = new IdIterator(context, it);
        return eventIdIterator.getIds();
    }

    /**
     * Iterate over the events, starting with the oldest.
     * This takes in a list of eventIds, which is treated like a queue:
     * ids are removed from the head of the list one at a time until a valid event is found.
     * The remaining ids are kept until the next transition request.
     *
     * Delete the event if:
     * 1. The role the event is associated with is gone
     * 2. No Identities are impacted by the change
     *
     * The first event not meeting one of these criteria is returned
     *
     * @param request Request (in the transition phase) that is attempting to find the
     *                next validRoleChangeEvent
     * @param monitor TaskMonitor with which to update task progress
     * @param eventIds List of RoleChangeEvent ids that have yet to be processed.
     * @return RoleChangeEvent for the oldest event that requires updates to Identities
     *         None if no further events are available
     */
    public RoleChangeEvent getNextEvent(Request request, TaskMonitor monitor, List<String> eventIds)
            throws GeneralException {

        RoleChangeEvent nextEvent = null;

        while (!terminate && nextEvent == null && !Util.isEmpty(eventIds) && !isTimedOut(request)) {
            String eventId = eventIds.remove(0);
            RoleChangeEvent event = context.getObjectById(RoleChangeEvent.class, eventId);

            if (event == null) {
                log.warn("The RoleChangeEvent with id " + eventId + " was deleted during execution.");
            } else {
                if (validateEvent(event, monitor)) {
                    nextEvent = event;
                } else {
                    //before deleting the event, add process details to result.
                    addEmptyEventDetailsToTaskResult(monitor, event);

                    //delete the event from the queue because no Identities are associated with it
                    deleteEvent(event);
                }
            }
        }

        if (isTimedOut(request)) {
            handleTimeOut(monitor.getTaskResult(), request.getInt(TASK_ARG_DURATION));
        }

        return nextEvent;
    }

    /**
     * Update task result with events stats.
     * Note that this will lock and commit the result
     * @param taskMonitor TaskMonitor with which to update the result
     * @param msg Message to add to the TaskResult
     * @param eventProcessingDetails Event Processing Details
     */
    public void updateTaskResult(TaskMonitor taskMonitor, Message msg, Map<String,Object> eventProcessingDetails)
            throws GeneralException {
        if (log.isDebugEnabled()) {
            String detailString = Util.otos(eventProcessingDetails);            
            log.debug("Updating TaskResult with the following processing details: " + detailString);
        }

        if (null != taskMonitor && null != context) {
            try {
                TaskResult result = taskMonitor.lockMasterResult();

                if (null != msg) {
                    result.addMessage(msg);
                }
                
                if (null != eventProcessingDetails) {
                    List<Map<String, Object>> eventsDetails = 
                            (List<Map<String,Object>>) result.getAttribute(RESULT_EVENT_PROCESSING_DETAILS);
                    if (eventsDetails == null) {
                        eventsDetails = new ArrayList<Map<String, Object>>();
                        result.addAttribute(RESULT_EVENT_PROCESSING_DETAILS, eventsDetails);
                    }
                    eventsDetails.add(eventProcessingDetails);
                    
                    boolean hasValidStatus = false;
                    //update stats
                    RoleChangeEvent.Status eventStatus = (Status) eventProcessingDetails.get(RESULT_EVENT_PROCESSING_DETAILS_STATUS);
                    if (Status.Failed.equals(eventStatus)
                          || Status.Pruned.equals(eventStatus) 
                          || Status.Success.equals(eventStatus)) {
                        result.addInt(RESULT_EVENTS_PROCESSED, 1);
                        result.addInt(RESULT_EVENTS_PENDING, -1);
                        hasValidStatus = true;
                    }
                    
                    if (Status.Failed.equals(eventStatus)) {
                        if (log.isDebugEnabled()) {
                            log.debug("Marking the event with the following details failed:  " + eventProcessingDetails.toString());
                        }
                        result.addInt(RESULT_EVENTS_FAILED, 1);
                    } else if (Status.Pruned.equals(eventStatus)) {
                        result.addInt(RESULT_EVENTS_PRUNED, 1);
                    } else if (Status.Success.equals(eventStatus)) {
                        result.addInt(RESULT_EVENTS_SUCCEEDED, 1);
                    } 

                    if (!hasValidStatus) {
                        log.warn("Invalid RoleChangeStatus in processing details:" + eventProcessingDetails);
                    }

                    // The RESULT_TOTAL itself is automagically aggregated from the 
                    // partition results by the TaskBean, so we don't need to add it here.
                    // Still, it's convenient to have a value for debugging and unit testing purposes
                    int updatesForTheEvent = Util.getInt(eventProcessingDetails, RESULT_EVENT_PROCESSING_DETAILS_NUMBER_SUCCEEDED);
                    result.addInt(RESULT_DEBUG_TOTAL, updatesForTheEvent);
                }
                if (log.isDebugEnabled()) {
                    log.debug("On task result update -- TaskResult: " + result.toXml());
                }
            } finally {
                taskMonitor.commitMasterResult();
            }
        }
    }

    public void addEmptyEventDetailsToTaskResult(TaskMonitor taskMonitor, RoleChangeEvent emptyEvent)
            throws GeneralException {

        HashMap<String,Object> eventProcessingDetails = new HashMap<String, Object>();
        eventProcessingDetails.put(RolePropagationService.RESULT_EVENT_PROCESSING_DETAILS_BUNDLE_NAME, getBundleName(emptyEvent));
        eventProcessingDetails.put(RolePropagationService.RESULT_EVENT_PROCESSING_DETAILS_NUMBER_TO_PROCESS, 0);
        eventProcessingDetails.put(RolePropagationService.RESULT_EVENT_PROCESSING_DETAILS_NUMBER_FAILED, 0);
        eventProcessingDetails.put(RolePropagationService.RESULT_EVENT_PROCESSING_DETAILS_NUMBER_SKIPPED, 0);
        eventProcessingDetails.put(RolePropagationService.RESULT_EVENT_PROCESSING_DETAILS_NUMBER_SUCCEEDED, 0);
        eventProcessingDetails.put(RolePropagationService.RESULT_EVENT_PROCESSING_DETAILS_STATUS,
                RoleChangeEvent.Status.Success);

        updateTaskResult(taskMonitor, null, eventProcessingDetails);
    }
    

    /*
     * This method verifies that the RoleChangeEvent in question is still valid.
     * The RoleChangeEvent could be invalid in two cases:
     * 
     * 1. The role the event is associated with is gone or flagged for deletion
     * 2. No Identities are impacted by the change
     * 
     * Note also that if a role is flagged for deletion, this method deletes
     * 
     * @param event RoleChangeEvent that is being validated
     * @param monitor TaskMonitor with which to update progress
     * @return true if the specified RoleChangeEvent is valid; false otherwise
     */
    private boolean validateEvent(RoleChangeEvent event, TaskMonitor monitor) 
        throws GeneralException {
        Bundle role = context.getObjectById(Bundle.class, event.getBundleId());
        // Try the name if we can't find it by ID
        if (role == null) {
            role = context.getObjectByName(Bundle.class, event.getBundleName());
        }
        
        // Log the plan while we have the role available
        if (role != null && log.isDebugEnabled()) {
            log.debug("Role name: " + role.getName());
            if (null != event.getProvisioningPlan()) {
                ProvisioningPlan logPlan = ProvisioningPlan.getLoggingPlan(event.getProvisioningPlan());
                if (null != logPlan) {
                    log.debug("Provisioning Plan: " + logPlan.toXml());
                }
            }
        }

        String roleName = getBundleName(event);
        boolean isRoleInvalid = role == null || isRoleInvalid(event);
        boolean isIdentityInvalid = true;
        if (isRoleInvalid) {
            // Clean up the deleted role
            if (role != null) {
                monitor.updateProgress("Deleting event for Bundle: " + roleName);
                log.debug("The propagator task is deleting the role named " + 
                        role.getName() + 
                        " because the event indicates that it's flagged for deletion.");
                terminator.deleteObject(role);
            }
        } else {
            isIdentityInvalid = isIdentityInvalid(event, roleName);
            if (isIdentityInvalid) {
                monitor.updateProgress("No identity assignments for Bundle: " + roleName);
            }            
        }
        
        return !isRoleInvalid && !isIdentityInvalid;
    }
    
    /*
     * This looks confusing because it is.  isBundleDeleted() means what you would expect.
     * The role has been flagged for deletion.  However, it can, surprisingly, mean that 
     * a detected role assignment has been removed.  Consider fixing this --Bernie
     * The full, confusing explanation follows.
     * For delete events, 4 event states are possible:
     *   1] Disable role - isBundleDeleted = false and plan != null
     *   2] Remove assignments of assigned roles - isBundleDeleted = false and plan != null
     *   3] Remove assignments of detected roles - isBundleDeleted = true and plan != null
     *   4] Delete role - isBundleDeleted = true and plan = null
     * @param event RoleChangeEvent being validated
     * @return true if the RoleChangeEvent should be skipped, false if it should be processed
     */
    private boolean isRoleInvalid(RoleChangeEvent event) {
        boolean isRoleInvalid = event.isBundleDeleted() && null == event.getProvisioningPlan();
        return isRoleInvalid;
    }
    
    private boolean isIdentityInvalid(RoleChangeEvent event, String roleName) throws GeneralException {
        boolean isIdentityInvalid;

        IdIterator identityIterator = getIdIterator(event);
        isIdentityInvalid = Util.isEmpty(identityIterator);
        if (isIdentityInvalid) {
            if (log.isDebugEnabled()) {
                log.warn("Identity id iterator is empty. Deleting event. ID- "
                        + event.getId() + " Related Role - " + roleName);
            }
        }
        
        return isIdentityInvalid;
    }
    
    /**
     * Return bundle name for the Event. 
     * If the bundle name in the event is null, 
     * then use the bundle id to lookup the bundle name.
     * Bundle name will be null only in case of upgrade from 6.4 - 7.0.
     */
    public String getBundleName(RoleChangeEvent eventCurrentRequest) {
        if (!Util.isNullOrEmpty(eventCurrentRequest.getBundleName())) {
            return eventCurrentRequest.getBundleName();
        } else {
            String id = eventCurrentRequest.getBundleId();
            return (String) ObjectUtil.convertIdsToNames(context, Bundle.class, id);
        }
    }    
        
    /*
     * IIQETN-1275: Determines if there's a configured Workflow for
     * role propagation otherwise it updates the task result accordingly.
     */
    private boolean isRolePropagationWorkflowAvailable(String workflowName,
            TaskMonitor monitor, RoleChangeEvent event, Identity identity)
            throws GeneralException {
        if (workflowName != null) {
            if (null == context.getObjectByName(Workflow.class, workflowName)) {
                Bundle roleObj = context.getObjectById(Bundle.class, event.getBundleId());
                TaskResult result = monitor.getTaskResult();
                result.addMessage(Message.warn(MessageKeys.TASK_OUT_WARN_MISSING_PROPAGATION_WORKFLOW, roleObj.getName(), workflowName, identity.getName()));
                if (log.isWarnEnabled()) {
                    // Log this at warn because the configuration is probably out of date, and customers should know
                    log.warn("Since the configured role propagation workflow, " + workflowName + ", does not exist, we will be using the built-in provisioning logic.");
                }
                return false;
            } else {
                return true;
            }
        }
        if (log.isDebugEnabled()) {
            // We don't need to log this at warn because it's always worked this way
            log.debug("Since there is no configured role propagation workflow we will be using the built-in provisioning logic.");
        }
        return false;
    }

    private boolean launchRolePropagationWorkflow(Identity identity,
            TaskMonitor monitor, String workflowName, RoleChangeEvent event,
            ProvisioningPlan masterPlan) throws GeneralException {

        // IIQETN-6382 - We need to know when the role changes can't be propagated so we can keep the
        // RoleChangeEvent from getting deleted when a failure happens.
        boolean success = true;

        if (log.isDebugEnabled()) {
            log.debug("Running role propagation workflow: " + workflowName);
        }

        WorkflowLaunch wfl = new WorkflowLaunch();
        wfl.setSessionOwner(context.getUserName());
        wfl.setWorkflowRef(workflowName);
        wfl.setCaseName(workflowName + " - " + identity.getName());
        wfl.setLauncher(context.getUserName());

        Attributes<String,Object> vars = new Attributes<String,Object>();
        vars.put(IdentityLibrary.VAR_IDENTITY_NAME, identity.getName());
        vars.put(IdentityLibrary.VAR_PLAN, masterPlan);
        vars.put(PlanEvaluator.ARG_NO_RETRY_REQUEST, true);

        wfl.setVariables(vars);

        // launch a session
        Workflower wf = new Workflower(context);
        WorkflowSession ses = wf.launchSession(wfl);
        WorkflowLaunch launch = ses.getWorkflowLaunch();

        if (launch == null || launch.isFailed()) {
            //Role Propagation Task is partitioned now, 
            //so we put errors on service, then copy to partitioned result.
            addError(Message.error(MessageKeys.TASK_OUT_ERROR_PROPAGATING,
                    getBundleName(event), identity.getName()));
            success = false;
        }

        return success;
    }

    private boolean executeProvisioningProject(Identity identity,
                                            TaskMonitor monitor,
                                            ProvisioningProject proj,
                                            RoleChangeEvent event)
            throws GeneralException {

        // IIQETN-6382 - We need to know when the role changes can't be propagated so we can keep the
        // RoleChangeEvent from getting deleted when a failure happens.
        boolean success = true;

        provisioner.execute(proj);

        if (!proj.isFullyCommitted()) {
            if (!Util.isEmpty(proj.getErrorMessages())) {
                log.error("Error provisioning RoleChangeEvent to Identity: " + identity.getName() 
                        + "\nerrors: " + proj.getErrorMessages());
            }
            //Role Propagation Task is partitioned now, 
            //so we put errors on service, then copy to partitioned result.
            addError(Message.error(MessageKeys.TASK_OUT_ERROR_PROPAGATING,
                    getBundleName(event), identity.getName()));
            success = false;
        }

        return success;
    }
    
    /**
     * Helper method to get the List of Identities from a Request in the 
     * PHASE_PROVISION phase
     * @param provisionRequest Request in the PHASE_PROVISION phase
     * @return List<String> containing the ids of the Identities in the specified
     *         Request's partition
     */
    public List<String> getIdentitiesFromRequest(Request provisionRequest) 
        throws GeneralException {
        Partition partition = (Partition) provisionRequest.get(REQUEST_ARG_PARTITION);
        String compressedIdentities = null;
        if (partition != null) {
            compressedIdentities = partition.getString(PARTITION_ARG_IDENTITY_LIST);
        }

        List<String> identitiesList;
        if (!Util.isEmpty(compressedIdentities)) {
            String identitiesCsv = Compressor.decompress(compressedIdentities);
            identitiesList = Util.csvToList(identitiesCsv);
        } else {
            identitiesList = Collections.emptyList();
        }
        return identitiesList;
    }
    
    /**
     * Compile and execute ProvisioningProject for each Identity in IdIterator.
     *
     * @param monitor - Task monitor to update progress.
     * @param event - Role change event containing provisioning plan.
     * @param idList - List of Identity ids.
     * @param refreshIdentity - Refresh identity for policy violations or not.
     *
     * @return - False if project execution fails else true.
     */
    public boolean provisionIdentities(TaskMonitor monitor, RoleChangeEvent event,
            List<String> idList, boolean refreshIdentity) throws GeneralException {
        if (Util.isEmpty(idList)) {
            return true;
        }
        // Provide page counters for status update to
        // taskResult telling what's up in the UI.
        // Pending count should be total number of events in DB
        // less than one as one event will be in processing.
        int numIdentitiesOnThisEvent = idList.size();
        int currentIdentityOnThisEvent = 0;

        // IIQETN-6382 - We need to know when the role changes can't be propagated so we can keep the
        // RoleChangeEvent from getting deleted in RoleChangePropagator when a failure happens. The task
        // result in the Monitor contains a running list of messages across all events.
        boolean success = true;

        for (String id : idList) {
            if (terminate) {
                log.info("Terminate message received. Halting role propagation.");
                throw new GeneralException("Role propagation task has been terminated");
            }
            Identity identity = context.getObjectById(Identity.class, id);
            if (log.isDebugEnabled()) {
                log.debug("Updating identity -> " + identity.getName());
            }
            if (null != identity) {
                currentIdentityOnThisEvent++;

                StringBuilder progressMsg = new StringBuilder();
                progressMsg.append("Processing Identity [").append(identity.getName()).
                        append("] (").append(currentIdentityOnThisEvent).append(" of ").
                        append(numIdentitiesOnThisEvent).append(").");

                monitor.updateProgress(progressMsg.toString());

                ProvisioningPlan plan = event.getProvisioningPlan();
                ProvisioningPlan masterPlan = (ProvisioningPlan) plan.deepCopy(context);

                List<RoleAssignment> assignmentList = identity.getRoleAssignments();
                List<RoleDetection> detectionList = identity.getRoleDetections();
                addRoleRequests(assignmentList, masterPlan, event);

                masterPlan.setIdentity(identity);

                // Set the nativeIdentities required when identity has same role multiple times.
                if (!Util.isEmpty(assignmentList)) {
                    setNativeIdAssignments(masterPlan, assignmentList, detectionList, event);
                    if (log.isDebugEnabled()) {
                        log.debug("Plan after setting nativeIds - ");
                        log.debug(masterPlan.toXml());
                    }
                }

                ProvisioningProject proj = provisioner.compile(masterPlan, null);

                String workflowName = (String) Configuration.getSystemConfig().get(Configuration.WORKFLOW_LCM_ROLE_PROPAGATION);

                /*
                 * IIQETN-1275: If there's a Workflow configured for Role Propagation it is launched.
                 * If not, the built-in provisioning logic is used.
                 */
                try {
                    if (isRolePropagationWorkflowAvailable(workflowName, monitor, event, identity)) {
                        success = launchRolePropagationWorkflow(identity, monitor, workflowName, event, masterPlan);
                    } else {
                        success = executeProvisioningProject(identity, monitor, proj, event);
                    }
                } catch (GeneralException ge) {
                    log.error("Error while attempting to propagate role changes to identity:" + identity.getName(), ge);
                    addError(Message.error(MessageKeys.TASK_OUT_ERROR_PROPAGATING,
                            getBundleName(event), identity.getName()));
                    success = false;
                }

                // IIQETN-6382 - refresh the identity if the role change was propagated successfully. If it
                // wasn't, add this identity to the failure list and proceed to the next identity.
                if (success) {
                    totalIdentityUpdates++;
                    // Refresh identity for policy checks.
                    if (refreshIdentity) {
                        //Role Propagation Task is partitioned now, 
                        // For partition request, save partition result.
                        try {
                            TaskResult partResult = monitor.lockPartitionResult();
                            refreshIdentity(partResult, identity);
                            
                        } finally {
                            monitor.commitPartitionResult();
                        }
                    }
                } else {
                    failedIdentities.add(identity.getId());
                    log.error( Message.error(MessageKeys.TASK_OUT_ERROR_PROPAGATING,
                            getBundleName(event), identity.getName()));

                    //error messages will be added in RolePropagationRequestExecutor.provision() at once,
                    //this will avoid lock task result for each failed update.
                } 
            }
            context.decache();
        }

        // failedIdentities will be empty if all identities succeed.
        // i.e. if isEmpty == true then 100% success and no need to record failures.
        return failedIdentities.isEmpty();
    }
    
    /**
     * Updates the failed attempts counter for the event and returns true if it can be pruned. 
     * It's assumed that this is run after the event has been processed and updated with the 
     * current results.
     * @param event the role change event
     * @param args the task arguments
     * @returns true if event is prunable
     */
    public boolean isEventPrunable(RoleChangeEvent event, Attributes<String, Object> args) {
        
        int maxFailedAttempts = args.getInt(RolePropagationService.TASK_ARG_MAX_FAILED_ATTEMPTS);
        // If MAX_FAILED_ATTEMPTS is not set, do not prune, ever.
        boolean isPrunable = event != null &&
                maxFailedAttempts > 0 && 
                event.getFailedAttempts() >= maxFailedAttempts;

        return isPrunable;
    }
    
    /**
     * Returns true if the event has exceeded the max failure threshold. It's assumed that 
     * this is run after the event has been processed and updated with the current results.
     * @param event the role change event
     * @param args the task arguments
     * @returns true if threshold exceeded
     */
    public boolean isAboveFailureThreshold(RoleChangeEvent event, Attributes<String, Object> args) {
        boolean isAboveThreshold = false;
        
        int maxFailureThreshold = args.getInt(RolePropagationService.TASK_ARG_MAX_FAILURE_THRESHOLD);
        // If MAX_FAILURE_THRESHOLD is not set, we don't need to check this.
        if (event != null && maxFailureThreshold > 0) {
            int numFailedIdentities =  Util.size(event.getFailedIdentityIds());
            int affectedIdentities = event.getAffectedIdentityCount();
            if (affectedIdentities > 0) {
                double failurePct = (numFailedIdentities * 100) / affectedIdentities;
                isAboveThreshold = failurePct >= maxFailureThreshold;
            } // Otherwise, the previous event was skipped
        }

        return isAboveThreshold;
    }
        
    /**
     * Delete the event from the queue.
     */
    public void deleteEvent(RoleChangeEvent event)
            throws GeneralException {
        // Make sure the event hasn't been pruned out from under us
        boolean stillExists = 
            context.countObjects(RoleChangeEvent.class, new QueryOptions(Filter.eq("id", event.getId()))) > 0;
        if (stillExists) {
            terminator.deleteObject(event);
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Private Utility Methods
    //
    //////////////////////////////////////////////////////////////////////


    /**
     * Add error to current partition.
     */
    private void addError(Message error) {
        this.errors.add(error);
    }

    /**
     * Return errors of current partition.
     */
    public List<Message> getErrors() {
        return this.errors;
    }

    /**
     * Add an AttributeRequest for a role assignment.
     * This is same as Provisioner.addRoleRequest except, for
     * entitlement requests it sets the operation as Retain.
     */
    private void addRoleRequests(List<RoleAssignment> assignmentList, ProvisioningPlan plan, RoleChangeEvent event) {

        if (!Util.isEmpty(assignmentList)) {
            // Get operation for Role related AttributeRequest.
            Operation roleOp = getRoleOperation(plan);

            AccountRequest request = getOrCreateIIQAccountRequest(plan);
            // 24695 List of role ids to be removed.
            List<String> roleIdsToRemove = new ArrayList<String>();

            if (roleOp == Operation.Remove) {
                for (AttributeRequest attrReq : Util.iterate(request.getAttributeRequests())) {
                    if (attrReq.getValue() instanceof List) {
                        roleIdsToRemove.addAll((List)attrReq.getValue());
                    } else {
                        roleIdsToRemove.add((String) attrReq.getValue());
                    }
                }
            }
            // Bug 23429 - Delete Role issue with multiple accounts identity.
            // We will be adding AttributeRequest for each RoleAssignment, so removing
            // existing AttributeRequest which does not have RoleAssignment.
            request.setAttributeRequests(null);

            for (RoleAssignment assignment : Util.iterate(assignmentList)) {
                //If not negative, and not the role in the event, retain
                if (!assignment.isNegative()) {

                    AttributeRequest att = new AttributeRequest();
                    att.setName(ProvisioningPlan.ATT_IIQ_ASSIGNED_ROLES);
                    att.setValue(assignment.getRoleName());
                    att.setAssignmentId(assignment.getAssignmentId());

                    // Must preserve sunrise/sunset dates if we have them.
                    att.setAddDate(assignment.getStartDate());
                    att.setRemoveDate(assignment.getEndDate());

                    if (!(Util.nullSafeCaseInsensitiveEq(event.getBundleId(), assignment.getRoleId()) ||
                            Util.nullSafeCaseInsensitiveEq(event.getBundleName(), assignment.getRoleName()))) {
                        //Not the role in the event. Retain
                        att.setOperation(Operation.Retain);
                    } else {
                        //Keep the op. This helps with multiple assignments
                        if (roleOp != null) {
                            att.setOperation(roleOp);
                        } else {
                            //Null op, either not in Plan, or no Op set. default to Retain
                            att.setOperation(Operation.Retain);
                        }
                    }

                    request.add(att);
                }
            }
        } // If Role Assignments
    }

    /**
     * Return Operation for assignedRoles AttributeRequest, null otherwise.
     */
    private Operation getRoleOperation(ProvisioningPlan plan) {
        Operation op = null;
        AccountRequest request = plan.getIIQAccountRequest();
        if (null != request) {
            AttributeRequest atrRequest =
                    request.getAttributeRequest(ProvisioningPlan.ATT_IIQ_ASSIGNED_ROLES);
            if (null != atrRequest) {
                op = atrRequest.getOp();
            }
        }
        return op;
    }

    /**
     * Return the IdentityIQ account request in the given plan,
     * creating and adding one if necessary.
     */
    private AccountRequest getOrCreateIIQAccountRequest(ProvisioningPlan plan) {
        AccountRequest account = plan.getIIQAccountRequest();
        if (null == account) {
            account = new AccountRequest();
            account.setOperation(ProvisioningPlan.AccountRequest.Operation.Modify);
            account.setApplication(ProvisioningPlan.APP_IIQ);
            plan.add(account);
        }
        return account;
    }

    /**
     * Find the corresponding RoleTarget for that Application in the RoleAssignment
     * and set the native identity for each AccountRequest in the plan.
     * Duplicate the AccountRequest for each RoleAssignment for the role being propagated.
     *
     * @param plan - a plan that has a fragment of the request
     * @param assigns - list of RoleAssignments
     * @param detections - list of RoleDetections
     * @param event - the RoleChangeEvent
     */
    public static void setNativeIdAssignments(ProvisioningPlan plan, List<RoleAssignment> assigns, List<RoleDetection> detections, RoleChangeEvent event)
            throws GeneralException {
        if (null != plan) {
            // Set to hold  requests, one for each RoleAssignment.
            Set<AccountRequest> newActReqList = new HashSet<ProvisioningPlan.AccountRequest>();
            for (AccountRequest request : Util.iterate(plan.getAccountRequests())) {
                //Container for the potential splitting of the request due to account
                //selection
                HashMap<String, AccountRequest> requestMapByNativeIdentity = new HashMap<String, AccountRequest>();
                HashSet<String> duplicateRequestSet = new HashSet<String>();

                if (ProvisioningPlan.isIIQ(request.getApplication())) {
                    if (log.isDebugEnabled()) {
                        log.debug("Adding request to list: " + request.toXml());
                    }
                    newActReqList.add(request);
                    continue;
                }

                // For each RoleAssignment duplicate the AccountRequest.
                HashSet<String> targetsProcessed = new HashSet<String>();
                for (RoleAssignment assignment : Util.iterate(assigns)) {
                    // IIQPRITHVI-383 - Create AccountRequest for particular RoleAssignment only.
                    if ((Util.nullSafeCaseInsensitiveEq(event.getBundleId(), assignment.getRoleId()) ||
                            Util.nullSafeCaseInsensitiveEq(event.getBundleName(), assignment.getRoleName())) && !assignment.isNegative()) {

                        if (log.isDebugEnabled()) {
                            log.debug("Getting role targets for assignment - " +
                                    assignment.toXml());
                        }

                        String nativeIdentity = null;
                        for (AttributeRequest attrRequest : Util.iterate(request.getAttributeRequests())) {
                            Object attrVal = attrRequest.getValue();
                            //Search through the role detections with the same assignment id, etc to determine
                            //native identity, fall back to duplicating account requests across all targets otherwise
                            if (attrVal instanceof List) {
                                for (Object attrValMember : Util.iterate((List)attrVal)) {
                                    nativeIdentity = getNativeIdentityForDetection(detections, assignment.getAssignmentId(), request.getApplication(), attrRequest.getName(), attrValMember);
                                    addAttributeValueToAttributeRequest(requestMapByNativeIdentity, duplicateRequestSet, request.getApplication(), attrRequest.getOperation(), nativeIdentity, attrRequest.getName(), attrValMember, true);
                                }
                            } else {
                                nativeIdentity = getNativeIdentityForDetection(detections, assignment.getAssignmentId(), request.getApplication(), attrRequest.getName(), attrVal);
                                addAttributeValueToAttributeRequest(requestMapByNativeIdentity, duplicateRequestSet, request.getApplication(), attrRequest.getOperation(), nativeIdentity, attrRequest.getName(), attrVal, false);
                            }

                            //Fall back to the brute force method
                            if (nativeIdentity == null) {
                                List<RoleTarget> targets = assignment.getTargets();
                                // Bug 23365 - Entitlements issue with disabled role.
                                // When disabled role is assigned to identity, Role is assigned
                                // to the identity but without any entitlement.
                                // In this RoleTargets are null, and we need to treat this as role assignment.
                                if (Util.isEmpty(targets)) {
                                    newActReqList.add(request);
                                    continue;
                                }
                                for (RoleTarget target : Util.iterate(targets)) {
                                    if (log.isDebugEnabled()) {
                                        log.debug("Role target - " + target.toXml());
                                    }
                                    // Find the corresponding RoleTarget for that Application
                                    // in the AccountRequest and set the native identity.
                                    if (request.getApplication().equals(target.getApplicationName())) {
                                        log.debug("Found nativeIdentity - " + target.getNativeIdentity() +
                                                " for application - " + request.getApplication());
                                        if (targetsProcessed.contains(request.getApplication())) {
                                            log.warn("Multiple role targets using the same application, account requests will be duplicated across all accounts.");
                                        } else {
                                            targetsProcessed.add(request.getApplication());
                                        }

                                        AccountRequest newReq = new AccountRequest(request);
                                        newReq.setNativeIdentity(target.getNativeIdentity());
                                        newActReqList.add(newReq);
                                    }
                                }
                                break;
                            }
                        }

                        for (AccountRequest newRequest : Util.iterate(requestMapByNativeIdentity.values())) {
                            newActReqList.add(newRequest);
                        }
                    }
                }
            }
            // Overwrite the plan with duplicate AccountRequests
            plan.setAccountRequests(Util.asList(newActReqList));
        }
    }

    /*
     * Add an attribute value to a request indicating whether or not the value is part of a list
     * @param requestMapByNativeIdentity
     * @param application
     * @param operation
     * @param nativeIdentity
     * @param attrName
     * @param attrVal
     * @param listValue
     */
    private static void addAttributeValueToAttributeRequest(HashMap<String, AccountRequest> requestMapByNativeIdentity, HashSet<String> duplicateRequestSet, String application, ProvisioningPlan.Operation operation, String nativeIdentity, String attrName, Object attrVal, boolean listValue) {
        if (nativeIdentity != null) {
            log.debug("Via role detection targets, found nativeIdentity - " + nativeIdentity +
                    " for application - " + application);
            AccountRequest toBeAdded = requestMapByNativeIdentity.get(nativeIdentity);
            if (toBeAdded == null) {
                toBeAdded = new AccountRequest();
                toBeAdded.setNativeIdentity(nativeIdentity);
                toBeAdded.setApplication(application);
                toBeAdded.setOperation(AccountRequest.Operation.Modify);
                requestMapByNativeIdentity.put(nativeIdentity, toBeAdded);
            }

            AttributeRequest newAttrRequest = toBeAdded.getAttributeRequest(attrName);
            if (newAttrRequest == null) {
                newAttrRequest = new AttributeRequest();
                newAttrRequest.setName(attrName);
                newAttrRequest.setOperation(operation);
                toBeAdded.add(newAttrRequest);
            }

            //Allow us to detect duplicate attr requests (in case there are two targets with the same attributes).
            String attrDetails = application + nativeIdentity + attrName + attrVal.toString();

            if (listValue) {
                List<Object> newAttrRequestVal = (List<Object>)(newAttrRequest.getValue());
                if (newAttrRequestVal == null) {
                    newAttrRequestVal = new ArrayList<Object>();
                    newAttrRequest.setValue(newAttrRequestVal);
                }
                newAttrRequestVal.add(attrVal);
            } else {
                newAttrRequest.setValue(attrVal);
            }

            if (duplicateRequestSet.contains(attrDetails)) {
                log.warn("Duplicate attribute requests across multiple role targets, your role should be changed to remove this redundancy.");
            } else {
                duplicateRequestSet.add(attrDetails);
            }
        }
    }


    /*
     * Look for native identity within the detected roles account items.
     * @param detections
     * @param assignmentId
     * @param application
     * @param attrName
     * @param attrVal
     */
    private static String getNativeIdentityForDetection(List<RoleDetection> detections, String assignmentId, String application, String attrName, Object attrVal) {
        String nativeIdentity =  null;
        for (RoleDetection detection : Util.iterate(detections)) {
            //Match assignment id
            if (detection.getAssignmentIds() != null &&
                detection.getAssignmentIds().contains(assignmentId)) {
                for (RoleTarget detectTarget : Util.iterate(detection.getTargets())) {
                    //Match application
                    if (detectTarget.getApplicationName().equals(application)) {
                        for (AccountItem item : Util.iterate(detectTarget.getItems())) {
                            //Match attribute name
                            if (Util.nullSafeEq(attrName, item.getName())) {
                                //Look for a contains or equality of the attribute value
                                if (attrVal instanceof List) {
                                    if (item.getValue() instanceof Set) {
                                        for (Object value : Util.iterate(((List)(attrVal)))) {
                                            Set itemValue = (Set)item.getValue();
                                            if (itemValue.contains(value)) {
                                                nativeIdentity = detectTarget.getNativeIdentity();
                                                break;
                                            }
                                        }
                                    } else {
                                        if ( ((List)(attrVal)).contains(item.getValue()) ) {
                                            nativeIdentity = detectTarget.getNativeIdentity();
                                            break;
                                        }
                                    }
                                } else {
                                    if (item.getValue().equals(attrVal)) {
                                        nativeIdentity = detectTarget.getNativeIdentity();
                                        break;
                                    }
                                }
                            }
                        }
                    }
                    if (nativeIdentity != null) {
                        break;
                    }
                }
            }
        }
        return nativeIdentity;
    }

    /**
     * Identity refresh for passed in arguments.
     *
     * @param result - TaskResult needs to be updated as per RefreshResult
     * @param identity - identity to refresh
     */
    private void refreshIdentity(TaskResult result, Identity identity)
            throws GeneralException {

        if (null != identitizer && null != identity) {
            log.info("Refreshing identity - " + identity.getDisplayableName());
            identitizer.refresh(identity);
            identitizer.saveResults(result);
        }
    }
    
    private boolean isTimedOut(Request request) {
        long timeout = Util.otolo(request.get(REQUEST_ARG_END_TIME));
        boolean isTimedOut;
        if (timeout == -1) {
            isTimedOut = false;
        } else {
            long currentTime = Calendar.getInstance().getTimeInMillis();
            isTimedOut = currentTime > timeout;
        }
        return isTimedOut;
    }

    /**
     * Logs a message and annotates the task result when a timeout occurs
     * @param result TaskResult for the currently running propagation task
     * @param taskExecMins Time in minutes that the task was configured to run before timing out
     */
    private void handleTimeOut(TaskResult result, long taskExecMins) {
        Message msg = Message.info(MessageKeys.TASK_OUT_EXECUTION_PERIOD, taskExecMins);
        log.info(msg);
        result.addMessage(msg);
    }
    
    
    /**
     * Retrieves all failed identity IDs for events prior to current event.
     * 
     * @param currentEvent The current RoleChangeEvent object 
     * @return all failed identity IDs for events prior to current event
     * @throws GeneralException
     */
    public Set<String> getAccumulatedFailedIdentityIds(RoleChangeEvent currentEvent) throws GeneralException {
        Set<String> ids = new HashSet<String>();
        
        if (currentEvent == null) {
            return ids;
        }
        
        QueryOptions ops = new QueryOptions();
        ops.add(Filter.le("created", currentEvent.getCreated()));
        ops.add(Filter.eq("status", Status.Failed));
        ops.add(Filter.ne("id", currentEvent.getId()));
        
        Iterator<Object[]> it = context.search(RoleChangeEvent.class, ops , "id");
        IdIterator eventIdIterator = new IdIterator(context, it);

        while (eventIdIterator != null && eventIdIterator.hasNext()) {
            String eventId = eventIdIterator.next();
            RoleChangeEvent event = context.getObjectById(RoleChangeEvent.class, eventId);
            if (!Util.isEmpty(event.getFailedIdentityIds())) {
                ids.addAll(event.getFailedIdentityIds());
            }
        }

        return ids;
    }
    
    /**
     * Returns the List of Identity Ids to process the RoleChangeEvent.
     * If the event's status is Pending, then all associated Identities that not failed in previous events will be returned; 
     * and those failed in previous events will be added to event's new skip list.
     * If the event's status is Failed, then the to process list will contain its failed list and identities no longer need to skip;
     * also those identities no longer need to skip will be removed from event's new skip list. 
     * 
     * @param event The RoleChangeEvent to process
     * @param args 
     * @return List of Identity Ids to process the RoleChangeEvent
     * @throws GeneralException
     */
    public List<String> prepareIdentityListToProcess(RoleChangeEvent event, Attributes<String,Object> args) throws GeneralException {
        List<String> idsToProcess = new ArrayList<String>();
        if (event == null) {
            return idsToProcess;
        }
        
        //make a copy of skipped list
        List<String> newSkippedList = new ArrayList<String>();
        if (!Util.isEmpty(event.getSkippedIdentityIds())) {
            newSkippedList.addAll(event.getSkippedIdentityIds());
        }
        
        Set<String> priorFailedList = getAccumulatedFailedIdentityIds(event);
        if (Status.Pending.equals(event.getStatus())) {
            //for Pending event, get all associated Identities, 
            //then filter by accumulated failed list.
            IdIterator idit = getIdIterator(event);
            
            if (!Util.isEmpty(idit)) {
                
                while (idit.hasNext()) {
                    String id = idit.next();
                    if (priorFailedList.contains(id)) {
                        //if failed in prior events, then add to skipped list
                        newSkippedList.add(id);
                    } else {
                        idsToProcess.add(id);
                    }
                }
            }
            
            // Set this value the first time around so we can use it from then on
            if (event.getAffectedIdentityCount() == 0) {
                event.setAffectedIdentityCount(Util.size(idsToProcess));
            }
        } else if (Status.Failed.equals(event.getStatus())) {
            //add all Failed for re-process, and clear Failed list
            if (!Util.isEmpty(event.getFailedIdentityIds())) {
                idsToProcess.addAll(event.getFailedIdentityIds());
            }
            
            //add identity that no longer needs to skip for process
            if (!Util.isEmpty(event.getSkippedIdentityIds())) {
                Iterator<String> it = event.getSkippedIdentityIds().iterator();
                while (it.hasNext()) {
                    String id = it.next();
                    if (!priorFailedList.contains(id)) {
                        //no longer in prior failed list, ready to process and remove from skipped list
                        idsToProcess.add(id);
                        newSkippedList.remove(id);
                    }
                }
            }
        }

        // compress the skip list and put in args so it can be carried over to Transition Request
        String identitiesToCompress = Util.listToCsv(newSkippedList);
        String compressed = Compressor.compress(identitiesToCompress);
        args.put(RolePropagationService.REQUEST_ARG_EVENT_NEW_SKIP_LIST, compressed);
        
        return idsToProcess;
    }
}
