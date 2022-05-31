/* (c) Copyright 2017 SailPoint Technologies, Inc., All Rights Reserved. */
/*
 * A request executor that supports various operations on tasks 
 * and requests that are running on a specific host.
 * 
 * Author: Jeff
 *
 * In 7.2 this is a generalization of TerminateRequestExecutor that
 * supports terminate, stack capture, and user extensible commands
 * that can be sent to a task thread while it is running.
 *
 * All command requests must include the TaskResult id or name and
 * the request must have been assigned to the correct host.  We use
 * the ExecutorTracker to locate the executor associated with the TaskResult.
 *
 */

package sailpoint.request;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Iterator;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.ObjectUtil;
import sailpoint.api.RequestManager;
import sailpoint.api.SailPointContext;
import sailpoint.api.TaskManager;
import sailpoint.object.Attributes;
import sailpoint.object.BaseExecutor;
import sailpoint.object.Request;
import sailpoint.object.TaskResult;
import sailpoint.server.ExecutorTracker;
import sailpoint.server.ExecutorTracker.ExecutorInfo;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

public class TaskCommandExecutor extends AbstractRequestExecutor {

    private static Log log = LogFactory.getLog(TaskCommandExecutor.class);

    /**
     * Name of our RequestDefinition.
     */
    public static final String REQUEST_DEFINITION = "Task Command";

    /**
     * Standard command for terminating tasks.
     */
    public static final String COMMAND_TERMINATE = "terminate";

    /**
     * Standard command for capturing a stack trace.
     */
    public static final String COMMAND_STACK = "stack";


    /**
     * Standard command for reanimating a task -- which may either
     * terminate a dead unpartitioned task, or bring the task's
     * requests back to life if is a partitioned task with zombied requests
     */
    public static final String COMMAND_REANIMATE = "reanimate";

    /**
     * The name or id of a TaskResult.
     * This is used for non-partitioned tasks.
     */
    public static final String ARG_TASK_RESULT = "TaskCommandExecutor.taskResult";

    /**
     * The id of a Request.
     * This is used for partitioned tasks.
     */
    public static final String ARG_REQUEST_ID = "TaskCommandExecutor.requestId";
    
    /**
     * Throw a permanent exception.  RequestProcessor doesn't log these
     * so add our own logging.
     */
    private void throwError(String msg) throws RequestPermanentException {

        log.error(msg);
        throw new RequestPermanentException(msg);
    }

    /**
     * Process an out-of-band command.
     */
    public void execute(SailPointContext context, Request commandRequest, Attributes<String,Object> args)
        throws RequestPermanentException, RequestTemporaryException {

        String command = commandRequest.getExecutorCommand();
        String taskResultId = args.getString(ARG_TASK_RESULT);
        String requestId = args.getString(ARG_REQUEST_ID);
        
        if (command == null)
            throwError("Missing command");

        if (taskResultId == null && requestId == null)
            throwError("Missing TaskResult and Request id");


        try {
            if (taskResultId != null) {
                TaskResult result = context.getObjectById(TaskResult.class, taskResultId);
                if (result == null) {
                    // this could happen normally if the task ends at the moment we're processing
                    // the command request, it's a very small window though 
                    log.warn("Unknown TaskResult: " + taskResultId);
                }
                else {
                    if (log.isInfoEnabled()) {
                        log.info("Sending command: " + command + " to task " + taskResultId);
                    }

                    ExecutorInfo info = ExecutorTracker.getExecutor(result);

                    if (COMMAND_REANIMATE.equals(command)) {
                        // there isn't necessarily a ExecutorInfo present
                        doReanimateTaskResult(context, result, info);
                    }
                    else {
                        if (info == null) {
                            if (log.isInfoEnabled()) {
                                log.info("Unable to find executor for: " + taskResultId);
                            }
                        } else if (COMMAND_TERMINATE.equals(command)) {
                            doTerminate(context, result, info);
                        } else if (COMMAND_STACK.equals(command)) {
                            doStackCapture(context, result, info);
                        } else {
                            // shouldn't need to pass the TaskResult?
                            // BaseExecutor has it's own copy
                            BaseExecutor exec = info.getExecutor();
                            exec.processCommand(commandRequest);
                        }
                    }
                }
            }
            else {
                Request targetRequest = context.getObjectById(Request.class, requestId);
                if (targetRequest == null) {
                    // this could happen normally if the partition ends at the moment we're processing
                    // the command request, it's a very small window though 
                    log.warn("Unknown Request: " + requestId);
                }
                else {
                    if (log.isInfoEnabled()) {
                        log.info("Sending command: " + command + " to partition " + targetRequest.getName());
                    }

                    ExecutorInfo info = ExecutorTracker.getExecutor(requestId);

                    if (COMMAND_REANIMATE.equals(command)) {
                        // there isn't necessarily a ExecutorInfo present
                        doReanimateRequest(context, targetRequest, info);
                    }
                    else {
                        if (info == null) {
                            if (log.isInfoEnabled()) {
                                log.info("Unable to find executor for partition: " + requestId);
                            }
                        } else if (COMMAND_TERMINATE.equals(command)) {
                            // not handling terminate this way yet
                            // doTerminate(context, result, info);
                            log.warn("Ignoring partition terminate command");
                        } else if (COMMAND_STACK.equals(command)) {
                            doStackCapture(context, targetRequest, info);
                        } else {
                            BaseExecutor exec = info.getExecutor();
                            exec.processCommand(commandRequest);
                        }
                    }
                }
            }
        }
        catch (Throwable t) {
            throw new RequestPermanentException(t);
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Termination
    // 
    //////////////////////////////////////////////////////////////////////

    /**
     * Tell the task to terminate.
     */
    private void doTerminate(SailPointContext context, TaskResult result, ExecutorInfo info)
        throws GeneralException {

        // most tasks will poll the terminate flag
        BaseExecutor exec = info.getExecutor();
        exec.terminate();

        // rule based tasks don't have a handle to the TaskExecutor,
        // they will look in the TaskResult
        TaskResult liveResult = info.getTaskResult();
        if (liveResult != null) {
            liveResult.setTerminateRequested(true);
        }
    }

    /**
     * If the task is unpartitioned, not completed, and has no executorInfo, then
     * it is a zombie, and needs to be marked as terminated.
     * @param context
     * @param result the taskresult we are examining
     * @param info the ExecutorInfo running for the taskresult
     * @throws GeneralException
     */
    private void doReanimateTaskResult(SailPointContext context, TaskResult result, ExecutorInfo info) throws GeneralException  {

        if (!result.isPartitioned()) {
            if (info == null  && result.getCompleted() == null) {

                // this zombie unpartitioned tasks will be finally recorded as dead
                Set<String> zombieTaskIds = new HashSet<String>();
                zombieTaskIds.add(result.getId());

                if (log.isInfoEnabled()) {
                    log.info("User-initiated reanimate command will terminate orphaned task : " + result.getName());
                }

                TaskManager taskManager = new TaskManager(context);
                taskManager.terminateTasks(zombieTaskIds);
            }
        }
    }

    /**
     * If the Request is uncompleted and has no executorInfo, then it is a zombie, so
     * let's reanimate it.
     * @param context
     * @param request the Request to examine, and possibly reanimate
     * @param info the ExecutorInfo for the Request
     * @throws GeneralException
     */
    private void doReanimateRequest(SailPointContext context, Request request, ExecutorInfo info) throws GeneralException  {

        if (info == null && request.getCompleted() == null) {

            // this is a zombie request, so let's reanimate it
            Set<String> zombieRequestIDs = new HashSet<String>();
            zombieRequestIDs.add(request.getId());

            if (log.isInfoEnabled()) {
                log.info("User-initiated reanimate command will reset partition: " + request.getName());
            }

            RequestManager rm = new RequestManager(context);
            rm.resetRequests(zombieRequestIDs);
        }
    }
    
    //////////////////////////////////////////////////////////////////////
    //
    // Stack Capture
    // 
    //////////////////////////////////////////////////////////////////////

    /**
     * Capture the stack trace of a running task and save it in the TaskResult.
     */
    private void doStackCapture(SailPointContext context, TaskResult result, ExecutorInfo info)
        throws GeneralException {

        String stack = getStackTrace(info.getThreadName());
        if (stack != null) {
            // note that we must pass the executor's tracked
            // copy of the TaskResult so we can modify it outside
            // of the executor thread
            saveStack(context, info, stack);            
        }
    }

    /**
     * Given a thread name, format a single string representation of the 
     * stack trace.
     */
    private String getStackTrace(String threadName) {

        String stack = null;

        if (log.isInfoEnabled()) {
            log.info("Looking for thread: " + threadName);
        }
        
        // gak, what an awkward interface, why not just Thread.getThread(name)?
        Thread found = null;
        Map<Thread,StackTraceElement[]> threadMap = Thread.getAllStackTraces();
        Iterator<Thread> it = threadMap.keySet().iterator();
        while (it.hasNext()) {
            Thread t = it.next();
            if (threadName.equals(t.getName())) {
                found = t;
                break;
            }
        }

        if (found == null) {
            // normal if the task/request terminated just now but rare
            log.warn("No thread named: " + threadName);
        }
        else {
            StackTraceElement[] stackElements = threadMap.get(found);
            if (stackElements == null) {
                // better be there 
                log.error("Stack trace not found: " + found.getName());
            }
            else {
                StringBuilder sb = new StringBuilder();
                for (StackTraceElement s : stackElements) {
                    sb.append(s.toString());
                    sb.append("\n");
                }

                stack = sb.toString();
                
                if (log.isInfoEnabled()) {
                    log.info(stack);
                }
            }
        }

        return stack;
    }

    /**
     * Save the stack trace in the executor's TaskResult.
     * 
     * The executor is considered exclusive owner of the TaskResult and may
     * modify it at any time to set attributes or update progress.
     *
     * Modifying the TaskResult that we fetched from Hibernate won't work
     * because any changes we make will be overwritten when the task 
     * thread ends and it saves its copy of the result.
     *
     * The ExecutorTracker will keep a handle to the task thread's
     * TaskResult.  We can set Java properties on that safely, but
     * adding things to the attributes map may cause concurrent
     * modification exceptions if the task thread is making changes
     * at the same time.  For saving stack that won't matter since
     * we have a Java property, but if we get to a point where other
     * changes need to be made we'll need a synchronization mechanism 
     * for the attributes map, message list, etc.
     *
     * Persisting the stack trace so it can be immediately seen is 
     * tricky.  We can't call saveObject on the task thread's TaskResult
     * because it is in a different Hibernate session.  We can wait
     * for the task to update progress and flush the change, but that may
     * take awhile and not all tasks udadte progress.
     *
     * Instead we'll use HQL to directly set the property column in 
     * the database.
     */
    private void saveStack(SailPointContext context, ExecutorInfo info, String stack)
        throws GeneralException {

        // If this is a partitioned task then it won't have a TaskResult
        // we should not have scheduled it this way
        TaskResult result = info.getTaskResult();
        if (result == null) {
            log.error("Unable to capture stack trace for executor with no TaskResult");
        }
        else {
            // since we're using a literal string with hql, filter
            // out single quotes, I've never seen them in a stack trace but
            // be safe
            if (stack != null) {
                stack = stack.replace('\'', ' ');
            }
            
            result.setStack(stack);

            // todo: may need to escape this? embedded single quotes?
            String hql = "update TaskResult set stack = '" + stack +
                "' where id = '" + result.getId() + "'";
            
            // result can only be zero or one, zero is expected if the task finishes
            // and deletes the result at the same time
            context.update(hql, null);
            context.commitTransaction();
        }
    }
    
    //////////////////////////////////////////////////////////////////////
    //
    // Partitioned Task Stack Capture
    // 
    //////////////////////////////////////////////////////////////////////

    /**
     * Capture the stack trace of a running task or request and save
     * it in the TaskResult.
     */
    private void doStackCapture(SailPointContext context, Request partition, ExecutorInfo info)
        throws GeneralException {

        String threadName = info.getThreadName();
        if (threadName == null) {
            log.error("No thread name stored in ExecutorInfo for partition: " +  partition.getName());
        }
        else {
            // might be gone by now
            String stack = getStackTrace(threadName);
            if (stack != null) {
                TaskResult result = partition.getTaskResult();
                if (result == null) {
                    log.error("Partition request has no TaskResult");
                }
                else {
                    // lock the root result
                    context.decache();
                    result = ObjectUtil.transactionLock(context, TaskResult.class, result.getId());
                    try {
                        if (result == null) {
                            // very small window where the executor thread ended and
                            // the result deleted, unlikely
                            log.warn("TaskResult evaporated after locking");
                        }
                        else {
                            String stacks = formatPartitionStacks(result, partition, stack);
                            result.setStack(stacks);
                            if (log.isInfoEnabled()) {
                                log.info("Merged root stack set:");
                                log.info(stacks);
                            }
                            context.saveObject(result);
                        }
                    }
                    finally {
                        // always be sure the transaction lock is released
                        context.commitTransaction();
                    }
                }
            }
        }
    }

    /**
     * Update a partition stack trace and rebuild the merged root result
     * set of stack traces.  
     * 
     * For simplicity we'll store the trace in the nested partition result, 
     * then rebuild the entire root stack set.  We could avoid the duplication
     * by parsing the root stack and splicing out just the section for that 
     * partition.
     */
    private String formatPartitionStacks(TaskResult rootResult, Request partition, String partstack)
        throws GeneralException {

        TaskResult presult = rootResult.getPartitionResult(partition.getName());
        presult.setStack(partstack);

        StringBuilder sb = new StringBuilder();
        List<TaskResult> partResults = rootResult.getPartitionResults();
        for (TaskResult partResult : Util.iterate(partResults)) {

            sb.append("---------------------------------------------------------------------------\n");
            sb.append(partResult.getName());
            sb.append("\n\n");
            sb.append(partResult.getStack());
            sb.append("\n");
        }

        return sb.toString();
    }

}
