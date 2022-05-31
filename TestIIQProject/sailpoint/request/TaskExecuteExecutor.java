/* (c) Copyright 2017 SailPoint Technologies, Inc., All Rights Reserved. */
/*
 * A request executor that runs a task on a specific host.
 * 
 * Author: Jeff
 *
 * The task to run may be specified in two ways:
 *
 * TaskResult id
 *
 * This is used for host-specific tasks where we first run a special
 * RestrictedTaskLauncher task to create the Request, this creates
 * a TaskResult that the Request eventualy takes ownership of.
 *
 * TaskDefinition name
 *
 * This is for launching tasks normally.  The TaskResult is created
 * by TaskManager.  
 *
 * In both cases all of the Request arguments are passed as task arguments
 * to the TaskManager, except for the few that this request executor uses.
 *
 * To execute the task, TaskManager.runSync is used, in much the same way
 * as the Quartz JobAdapter does.
 *
 */

package sailpoint.request;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.SailPointContext;
import sailpoint.api.TaskManager;
import sailpoint.object.Attributes;
import sailpoint.object.Request;
import sailpoint.object.TaskDefinition;
import sailpoint.object.TaskResult;
import sailpoint.object.TaskSchedule;
import sailpoint.tools.GeneralException;

public class TaskExecuteExecutor extends AbstractRequestExecutor {

    private static Log log = LogFactory.getLog(TaskExecuteExecutor.class);

    /**
     * Name of our RequestDefinition.
     * There are no interesting options at this time.
     */
    public static final String REQUEST_DEFINITION = "Task Execute";

    /**
     * The name or id of a TaskResult used when launching a host-specific task.
     * We take ownership of the result.
     */
    public static final String ARG_TASK_RESULT = "TaskExecuteExecutor.taskResult";

    /**
     * The name or id of a TaskDefinition used when launching ordinary tasks.
     * Might want additional options to specify the task result name rather
     * than always deriving it from the TaskDefinition.
     */
    public static final String ARG_TASK_DEFINITION = "TaskExecuteExecutor.taskDefinition";

    /**
     * The name of the Identity considiered to be the launcher of this task.
     * This is necessary only when using ARG_TASK_DEFINITION, we pass it in through
     * the TaskSchedule which is used by TaskManager.createResult.
     * When ARG_TASK_RESULT is used, we will already have set the launcher.
     */
    public static final String ARG_LAUNCHER = "TaskExecuteExecutor.launcher";

    /**
     * Execute a new task. 
     */
    public void execute(SailPointContext context, Request request, Attributes<String,Object> args)
        throws RequestPermanentException, RequestTemporaryException {

        if (log.isInfoEnabled()) {
            log.info("TaskExecuteExecutor starting");
        }
        // task args were all passed as Request args
        // remove the request-specific args
        String taskResultName = args.getString(ARG_TASK_RESULT);
        String taskDefinitionName = args.getString(ARG_TASK_DEFINITION);
        String launcher = args.getString(ARG_LAUNCHER);

        args.remove(Request.ATT_EXECUTOR_COMMAND);
        args.remove(ARG_TASK_RESULT);
        args.remove(ARG_TASK_DEFINITION);
        args.remove(ARG_LAUNCHER);
                               
        if (log.isInfoEnabled()) {
            if (taskResultName != null)
                log.info("Starting task with result: " + taskResultName);

            if (taskDefinitionName != null)
                log.info("Starting task with definition:" + taskDefinitionName);
        }

        // TaskManager expects one, fake it
        // The args have already been flattened so the TaskSchedule doesn't
        // need those.  Launcher is expected to be in the TaskSchedule though
        // when using ARG_TASK_DEFINITION
        TaskSchedule schedule = new TaskSchedule();
        schedule.setArguments(args);
        schedule.setLauncher(launcher);

        TaskDefinition definition = null;
        TaskResult result = null;

        try {
            if (taskResultName != null) {
                result = context.getObjectByName(TaskResult.class, taskResultName);

                if (result == null)
                    throwError("Invalid TaskResult name: " + taskResultName);

                definition = result.getDefinition();

                if (definition == null)
                    throwError("TaskResult without a TaskDefinition: " + taskResultName);

                // result will have the host name that ran the RestrictedTaskExecutor
                // have to change it to the request processor host
                result.setHost(request.getHost());

                // also reset the message list so we don't keep the "Waiting for scheduled request..."
                // message that RestrictedTaskExecutor left there
                result.setMessages(null);
                
                context.saveObject(result);
                context.commitTransaction();
            }
            else {
                if (taskDefinitionName == null)
                    throwError("Missing TaskDefinition name");

                definition = context.getObjectByName(TaskDefinition.class, taskDefinitionName);
            
                if (definition == null)
                    throwError("Invalid TaskDefinition name: " + taskDefinitionName);
            }
        }
        catch (GeneralException ge) {
            // must have been a Hibernate error
            // sigh, have to wrap it
            log.error(ge);
            throwError(ge.toString());
        }
        
        // I don't think this is required, but old TaskManager code does it
        schedule.setDefinitionName(definition.getName());

        try {
            if (log.isInfoEnabled())
                log.info("Starting task");

            // TaskManager will handle maintenance of the ExecutorTracker
            TaskManager tm = new TaskManager(context);
            tm.runSync(schedule, definition, result, args);

            if (log.isInfoEnabled())
                log.info("Task execution complete");
        }
        catch (Throwable t) {
            // JobAdapter logs these, I don't think we can get
            // here normally since TaskManager.runSync catches them and
            // adds them to the TaskResult
            log.error("Task failed to execute: " + definition.getName());
        }
    }

    /**
     * Throw a permanent exception.  RequestProcessor doesn't log these
     * so add our own logging.
     */
    private void throwError(String msg) throws RequestPermanentException {

        log.error(msg);
        throw new RequestPermanentException(msg);
    }

}
