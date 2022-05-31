/* (c) Copyright 2014 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * A task that iterates over the all events in 'spt_role_change_event' table
 * and applies the provisioning plan in the event to all identities having assigned
 * role as role in the event. After applying the plan successfully, task will delete the event from queue.
 * In case of exception, task will stop and it will not delete event from the queue.
 *
 * author: ikram momin
 */

package sailpoint.task;

import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.Identitizer;
import sailpoint.api.Provisioner;
import sailpoint.api.SailPointContext;
import sailpoint.api.Terminator;
import sailpoint.api.rolepropagation.RolePropagationRequestBuilder;
import sailpoint.api.rolepropagation.RolePropagationService;
import sailpoint.object.Attributes;
import sailpoint.object.Filter;
import sailpoint.object.QueryOptions;
import sailpoint.object.Request;
import sailpoint.object.TaskDefinition;
import sailpoint.object.TaskItemDefinition;
import sailpoint.object.TaskResult;
import sailpoint.object.TaskSchedule;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;

public class RolePropagationTaskExecutor extends AbstractTaskExecutor {

    public static final String TASK_TEMPLATE_NAME = "Propagate Role Changes";

    /**
     * The argument will read the task execution parameter .
     * If the parameter is greater than zero the task will execute for
     * the specified amount in minutes
     */
    public static final String ARG_DURATION = "duration";

    /**
     * If partitioning is enabled on Role Propagation Task
     */
    public static final String ARG_ENABLE_PARTITIONING = "enablePartitioning";
    private static final String ARG_NUMBER_OF_PARTITIONS = "partitions";

    private static Log log = LogFactory.getLog(RolePropagationTaskExecutor.class);

    public RolePropagationTaskExecutor() {
    }

    public void execute(SailPointContext context, TaskSchedule sched,
                        TaskResult result, Attributes<String,Object> args)
        throws Exception {
        log.debug("Launching the role propagation task.");

        abortIfTaskAlreadyRunning(context, result);
        
        Provisioner provisioner = new Provisioner(context);
        Identitizer identitizer = new Identitizer(context);
        Terminator terminator = new Terminator(context);
        RolePropagationService propagationService = new RolePropagationService(context, provisioner, identitizer, terminator);

        List<String> eventIds = propagationService.getEventIds(result);

        int totalEvents = eventIds.size();
        //the task just started, no need to lock the TaskResult 
        result.setAttribute(RolePropagationService.RESULT_EVENTS_TOTAL, totalEvents);
        if (totalEvents > 0) {
            result.setAttribute(RolePropagationService.RESULT_EVENTS_PENDING, totalEvents);
        }
        result.addMessage(new Message(MessageKeys.TASK_ROLE_PROPAGATION_LAUNCHED));
        context.saveObject(result);
        context.commitTransaction();

        Attributes<String, Object> requestArgs = initRequestArgs(context, args, result);

        TaskMonitor taskMonitor = new TaskMonitor(context, result);

        RolePropagationRequestBuilder requestBuilder = 
                new RolePropagationRequestBuilder(context, propagationService, null, taskMonitor, requestArgs);

        Request initialRequest = requestBuilder.buildTransitionRequest(0, true, eventIds);
        
        // Note that since we are always saving a request, all TaskResults,
        // are going to be flagged as partitioned regardless of whether the flag was
        // enabled
        saveRequest(context, result, initialRequest);

        log.debug("The role propagation task has successfully launched.");
    }

    private void abortIfTaskAlreadyRunning(SailPointContext context, TaskResult result) 
        throws GeneralException {
        // Processing of role change event happens without acquiring the lock 
        // on the database row. So, it is very necessary that only one role 
        // propagation task runs at a time.

        boolean isTaskRunning = isRolePropagationAlreadyRunning(context, result);

        if (isTaskRunning) {
            log.info("Another Role Propagation Task already running. Terminating Task: " + result.getName());
            throw new GeneralException("Another Role Propagation Task already running. Terminating this Task...");
        }

    }
    
    /**
     * Return true if any other role propagation task is already running.
     * @param context
     * @param result
     * @return
     * @throws GeneralException
     */
    private boolean isRolePropagationAlreadyRunning(SailPointContext context, TaskResult result)
        throws GeneralException {

        boolean isTaskRunning = false;
        List<TaskDefinition> listDefs = new ArrayList<TaskDefinition>();
        TaskItemDefinition parentDef = result.getDefinition().getParent();
        QueryOptions qo = new QueryOptions(Filter.eq("parent", parentDef));

        // Get all the tasks that share same task template name
        Iterator<TaskDefinition> itDefs = context.search(TaskDefinition.class, qo);
        while (itDefs.hasNext()) {
            TaskDefinition def = itDefs.next();
            // Skip current task ID from the query
            // TODO:  Why are we excluding the current task's definition from this?
            // If two instances of the current task are still running, wouldn't we want
            // to know about it?  --Bernie
            if (!def.getId().equals(result.getDefinition().getId())) {
                listDefs.add(def);
            }
        }

        if (Util.isEmpty(listDefs)) {
            isTaskRunning = false;
        } else {
            qo = new QueryOptions();
            // Get the tasks which are still running
            qo.add(Filter.and(Filter.in("definition", listDefs), Filter.isnull("completed")));
            int numRunningTasks = context.countObjects(TaskResult.class, qo);
            isTaskRunning = numRunningTasks > 0;
        }
        
        return isTaskRunning;
    }

    private int calculatePartitionSize(SailPointContext context, Attributes<String,Object> args) 
        throws GeneralException {
        int partitionSize;
        if (Util.otob(args.get(ARG_ENABLE_PARTITIONING))) {
            partitionSize = getPartitionSize(context, args);
        } else {
            partitionSize = 1;
        }
        return partitionSize;
    }
    
    /**
     * Number of partitions to create.
     */
    private int getPartitionSize(SailPointContext context, Attributes<String,Object> args) throws GeneralException {
        int size = args.getInt(ARG_NUMBER_OF_PARTITIONS);
        if (size <= 0) {
            int suggestedCount = getSuggestedPartitionCount(context, false, RolePropagationService.ROLE_PROPAGATION_REQUEST_DEFINITION);
            size = Math.max(1,  suggestedCount);
        }
        return size;
    }
    
    /*
     * Convert the specified task arguments into an appropriate set of request arguments
     * @param context SailPointContext with which to create the arguments
     * @param args Task arguments on which to base the request arguments
     * @param result The currently running Task's TaskResult
     */
    private Attributes<String, Object> initRequestArgs(SailPointContext context, Attributes<String, Object> taskArgs, TaskResult result)
        throws GeneralException {
        Attributes<String, Object> requestArgs = new Attributes<String, Object>(taskArgs);
        int maxPartitions = calculatePartitionSize(context, taskArgs);
        requestArgs.put(RolePropagationService.REQUEST_ARG_MAX_PARTITIONS, maxPartitions);
        Date creationDate = result.getCreated();
        long creationTime;
        if (creationDate == null) {
            creationTime = System.currentTimeMillis();
        } else {
            creationTime = creationDate.getTime();
        }
        long expiration;
        Long duration = taskArgs.getLong(RolePropagationService.TASK_ARG_DURATION);
        if (duration == null) {
            expiration = -1l;
        } else {
            expiration = creationTime + (duration * 60000l);            
        }
        
        requestArgs.put(RolePropagationService.REQUEST_ARG_END_TIME, expiration);

        return requestArgs;
    }

    public boolean terminate() {
        return false;
    }
}
