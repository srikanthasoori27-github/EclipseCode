/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/*
 * An object providing task management services.
 *
 * Author: Jeff
 * 
 * Currently making this a concrete class rather than an interface,
 * since it seems doubtful we will need more than one of these, and
 * the code in it sits nicely on top of SailPointContext.
 *
 * I suppose we could encapsulate implementation differences (Quartz vs. ?)
 * in different implementations of a TaskManager interface, but we'll
 * still have a set of common task utilities that need to go somewhere
 * so let's start here and refactor a TaskManager/TaskUtil later if necessary.
 *
 * NOTE WELL: Dealing with the TaskDefinition Hibernate issues are
 * just unbelievably complicated due to the various contexts we can be in
 * when we launch taasks: synchronous, via Quartz, unusual unit tests etc.
 * The bottom line is that we can NEVER trust a TaskDefinition passed
 * into any of the public methods, it must be loaded fresh from the
 * database, fully load()ed and never attached again.  The
 * WorkItemConfig used for signoff has a habit of getting dirty and 
 * losing its owner list if you decache the TaskDefinition without also
 * decaching the WorkItemConfig.  Not sure why that is, but life is
 * just so much easier if we keep the TaskDefinition as far away
 * from Hibernate as possible.  This is why you will see what appear
 * to be redundant fetches of the TaskDefinition.  Do not try to optimize
 * this.
 *
 * Task Termination
 * 
 * Termination is convoluted.  With the introduction of task commands (see below)
 * it could be redesigned, but leaving it as it has been.  
 *
 * Termination is initiated by the UI which calls TaskManager.terminate(TaskResult)
 * There are three types of results that can be terminated: normal, partitioned, and workflow.
 *
 * For normal results we call TaskManager.terminate(TaskSchedule).
 * This sets a special TaskSchedule.State.Terminated property and saves it
 * which will pass control to QuartzPersistenceManager.  QPM sees this and converts
 * it to a call to Scheduler.interrupt with a job key.  This eventually calls
 * our JobAdapter.interrupt() method on the instance Quartz created when the task
 * was started.  JobAdapter created an instance of TaskManager for that thread
 * and saved it in a field.  TaskManager.terminate() with no args is called.
 * TaskManager will have saved the TaskExecutor instance that is being run.
 * TaskExecutor.terminate is called.  
 *
 * This requires that the machine handling the UI request and the machine
 * running the Quartz task be the same. If not, Quartz will be unable to locate
 * the JobAdapter in some memory cache and nothing happens.  If the UI machine
 * is different than the task machine a Request is made and assigned to the task host.
 * The RequestProcessor on that host will eventually pick it up and call back
 * to TaskManager.terminate(TaskResult).
 *
 * For partitioned or transferred tasks, Quartz is no longer involved so Requests are
 * created for the hosts running the task logic under the RequestProcessor.
 *
 * For workflow results, they are not necessarily executing in a thread.
 * We forward the request to Workflower.terminate which completes the case.
 *
 * Task Commands
 *
 * This mechanism was added for capturing the stack trace of a running task but
 * it is general and could have other uses.  TaskManager.command(TaskResult)
 * determines the host that the task (or request if partitioned) is running on.  
 * It creates a Request using TaskCommandExecutor on that host.  The Request
 * contains the command to execute and other supporting information.
 *
 * When TaskCommandExecutor runs, it uses sailpoint.server.ExecutorTracker
 * to locate the TaskExecutor associated with the TaskResult.  
 */

package sailpoint.api;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.Attributes;
import sailpoint.object.AuditEvent;
import sailpoint.object.Configuration;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.LockInfo;
import sailpoint.object.Plugin;
import sailpoint.object.QueryOptions;
import sailpoint.object.Request;
import sailpoint.object.RequestDefinition;
import sailpoint.object.Rule;
import sailpoint.object.Scope;
import sailpoint.object.Server;
import sailpoint.object.ServiceDefinition;
import sailpoint.object.Signoff;
import sailpoint.object.Signoff.Signatory;
import sailpoint.object.TaskDefinition;
import sailpoint.object.TaskDefinition.ResultAction;
import sailpoint.object.TaskEvent;
import sailpoint.object.TaskExecutor;
import sailpoint.object.TaskResult;
import sailpoint.object.TaskResult.CompletionStatus;
import sailpoint.object.TaskSchedule;
import sailpoint.object.WorkItem;
import sailpoint.object.WorkflowCase;
import sailpoint.plugin.PluginsUtil;
import sailpoint.request.TaskCommandExecutor;
import sailpoint.server.Auditor;
import sailpoint.server.DynamicLoader;
import sailpoint.server.Environment;
import sailpoint.server.ExecutorTracker;
import sailpoint.server.RequestService;
import sailpoint.server.Service;
import sailpoint.server.Servicer;
import sailpoint.server.ServicerUtil;
import sailpoint.server.TaskService;
import sailpoint.server.WorkItemHandler;
import sailpoint.task.RestrictedTaskLauncher;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;

/**
 * A utility class providing task management services.
 */
public class TaskManager implements WorkItemHandler
{

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    private static Log log = LogFactory.getLog(TaskManager.class);

    /**
     * Maximum allowed length of a task name.
     */
    public static final int MAX_TASK_NAME_LEN = 255;

    /**
     * Special description string used for TaskSchedule objects
     * created just to run an immediate task. The creation of these
     * will not be audited.  
     */
    public static final String IMMEDIATE_SCHEDULE = "Immediate task runner";

    /*
     * Defines the maximum number of times the manager will attempt to create a
     * "qualified name" for the task result
     */
    private static final int MAX_RESULT_RETRY = 20;

    /**
     * This is a back door for the unit tests to disable computation of 
     * task run lengths.  Run lengths are variable and need to be supressed,
     * but it's a pain to regenerate a bunch of test files to add them 
     * Intended to be set only by SailPointTest.createContext
     */
    public static boolean DisableTaskRunStatistics;

    /*
     * This lock is used to limit the number name collisions on a single JVM 
     */
    private static final ReentrantLock CREATION_LOCK = new ReentrantLock(true);
    private static final ReentrantLock WORKFLOW_CREATION_LOCK = new ReentrantLock(true);

    /**
     * The context providing persistence services.
     */
    SailPointContext _context;

    /**
     * Optional launcher name to put into TaskSchedule objects that are create.
     * Easier to use than passing TaskSchedule.ARG_LAUNCHER 
     * in the argument map.
     *
     * @ignore
     * jsl - In retrospect I don't like this.  Several things
     * now expect ARG_LAUNCHER to be the standard way to convey
     * things down complex class stacks like Identitizer, we may
     * as well reinforce this here.
     */
    String _launcher;

    /**
     * Transient executor maintained during runSync().
     * Maintained so that you can pass terminate requests sent
     * from Quartz that will come in on another thread.
     */
    TaskExecutor _executor;
    
    /**
     * Test option that causes the terminate() method to always schedule
     * a Request to handle the termination even if the task is already
     * running in this JVM.
     */
    boolean _useRequestForTermination;
    
    /**
     * Task result of launched task to store for potential termination
     */
    TaskResult _taskResult;

    //////////////////////////////////////////////////////////////////////
    //
    // Constructor
    //
    //////////////////////////////////////////////////////////////////////
    
    /**
     * This is only to be used when constructing a WorkItemHandler.
     */
    public TaskManager() {
    }

    /**
     * This is the constructor that should be used in most cases.
     */
    public TaskManager(SailPointContext con) {
        _context = con;
    }

    /**
     * Set an optional "launcher" name to be used for things like the
     * task result owner and workflow case launcher. This must
     * be the name of an Identity.
     */
    public void setLauncher(String s) {
        _launcher = s;
    }

    /**
     * Unit test option.
     * @exclude
     */
    public void setUseRequestForTermination(boolean b) {
        _useRequestForTermination = b;
    }

    public static void setDisableTaskRunStatistics(boolean b) {
        DisableTaskRunStatistics = b;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Scheduler Control
    //
    // This is here mostly for the console, ordinarilly the scheduler
    // is always running.  Applications can actually get the Scheduler from
    // the SailPointContext directly but I'd like to always funnel this
    // through the TaskManager.  I don't really like exposing things
    // from the Environment to applications, think...
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Get the task service instance.
     */
    private Service getTaskService() {
        Environment env = Environment.getEnvironment();
        return env.getService(TaskService.NAME);
    }

    /**
     * Start the task scheduler if it is not running.
     */
    public void startScheduler() throws GeneralException {
        Environment env = Environment.getEnvironment();
        Servicer servicer = env.getServicer();
        // Need to add this to the services whitelist
        servicer.addWhitelistedService(TaskService.NAME);

        // run the Task service now
        Service svc = getTaskService();
        if (svc != null)
            svc.start();
    }

    /**
     * Pause the scheduler with the intention of resuming it without
     * restarting the server.
     */
    public void suspendScheduler() throws GeneralException {
        Service svc = getTaskService();
        if (svc != null)
            svc.suspend();
    }
    
    /**
     * Return true if the scheduler is running.
     */
    public boolean isSchedulerRunning() {
        boolean running = false;
        Service svc = getTaskService();
        if (svc != null)
            running = svc.isStarted();
        return running;

    }

    //////////////////////////////////////////////////////////////////////
    //
    // Housekeeping
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * @exclude
     * Look for orphaned TaskResults that think they're still running on this 
     * machine.  This should ONLY be called when you're sure no tasks
     * are currently running.  It is currently called by Environment during
     * the startup process before the scheduler is running.
     *
     * We should really find a way to ping the Scheduler to see if a 
     * given TaskResult is actually running, then we could do orphan
     * clean up without rebooting.  (ky - this is done now by ReanimatorService)
     *
     * Workflow tasks won't set a host so they're invisible here.
     * Partitioned tasks won't have a host either in the outer shared result.
     */
    public void terminateOrphanTasks() throws GeneralException {

        String host = Util.getHostName();
        QueryOptions ops = new QueryOptions();
        ops.add(Filter.and(Filter.eq("host", host),
                           Filter.isnull("completed")));

        List<String> props = new ArrayList<String>();
        props.add("id");

        Set<String> taskResultIds = new HashSet<>();
        Iterator<Object[]> result = _context.search(TaskResult.class, ops, props);
        while (result.hasNext()) {
            Object[] row = result.next();
            String id = (String) row[0];
            taskResultIds.add(id);
        }

        terminateTasks(taskResultIds);

    }

    /**
     * Mark the given set of TaskResult objects as terminated.  We will only
     * mark terminated if the TaskResults are not partitioned. This should only
     * be called when we know the task is not running -- such
     * as from terminateOrphanTasks() above, or from the ReanimatorService.
     */
    public void terminateTasks(Set<String> taskResultIds) throws GeneralException {
        if (taskResultIds == null) {
            return;
        }

        for(String taskResultId : taskResultIds) {
            TaskResult taskres = _context.getObjectById(TaskResult.class, taskResultId);
            if (taskres != null) {

                // NOTE: Partitioned tasks aren't really running anywhere,
                // the child Requests objects are running on a random set of
                // machines.  We may want to remove the host name once the
                // partitions start?
                if (!taskres.isPartitioned()) {

                    if (log.isWarnEnabled())
                        log.warn("Terminating orphaned task result: " + taskres.getName());

                    taskres.setCompleted(new Date());
                    taskres.setCompletionStatus(TaskResult.CompletionStatus.Terminated);
                    taskres.addMessage(Message.warn(MessageKeys.TASK_MSG_TERMINATED));
                    taskres.setLive(false);
                    _context.saveObject(taskres);
                    _context.commitTransaction();
                    _context.decache(taskres);

                    unblockQuartzTriggers(taskres);
                }
            }
        }
    }

    /**
     * Bug#16685 If a scheduled task was running and crashed, 
     * the entry in the qrtz_triggers table can be left in the
     * BLOCKED state which will prevent the task from being triggered again.
     * To keep the Quartz encapsulated, let QuartzPersistenceManager
     * figure this out during save.
     */
    private void unblockQuartzTriggers(TaskResult result)
        throws GeneralException {
        
        String schedName = result.getSchedule();
        if (schedName != null) {
            TaskSchedule sched = _context.getObjectByName(TaskSchedule.class, schedName);
            if (sched != null) {
                _context.saveObject(sched);
            }
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Asynchronous Execution
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Force immediate execution of a previously scheduled task.
     * The task will remain scheduled.
     */
    public void runNow(TaskSchedule sched) throws GeneralException {

        if (sched.getState() != null)
            throw new GeneralException(new Message(Message.Type.Error,
                    MessageKeys.TASK_SCHED_ALREADY_RUNNING, sched.getName()));

        // throw a friendly exception now rather than getting a jdbc exception
        if (sched.getName().length() > MAX_TASK_NAME_LEN)
            throw new GeneralException(Message.error(MessageKeys.TASK_MGR_EXP_TASK_NAME_TOO_LONG,
                    sched.getName(), MAX_TASK_NAME_LEN));


        // convey the launcher if set
        saveLauncher(sched);

        // this triggers execution of the task 
        sched.setNewState(TaskSchedule.State.Executing);
        _context.saveObject(sched);

        // avoid committing since we didn't touch Hibernate objects
    }

    /**
     * Store the name of the user that launched the task in the
     * schedule. Be careful not to overwrite the current value if
     * there is one.  
     *
     * Originally it was required that the _launcher field be set
     * and it would always be authoritative. It is convenient and
     * more obvious for this to default to the owner of the SailPointContext
     * so that is supported now too. But the context owner is only used
     * if the TaskSchedule does not already have a launcher.
     */
    private void saveLauncher(TaskSchedule sched) {

        if (_launcher != null)
            sched.setLauncher(_launcher);

        else if (sched.getLauncher() == null)
            sched.setLauncher(_context.getUserName());
    }
    
    /**
     * Launch a task immediately, with TaskDefinition specified by name.
     */
    public void run(String name, Attributes<String,Object> args) 
        throws GeneralException {

        run(getTaskDefinition(name), args);
    }

    /**
     * Utility to lookup a TaskDefinition by name throwing if it is not found.
     */
    public TaskDefinition getTaskDefinition(String name) 
        throws GeneralException {

        TaskDefinition def = _context.getObjectByName(TaskDefinition.class, name);
        if (def == null)
            throw new GeneralException(Message.error(MessageKeys.UNKNOWN_TASK_DEF, name));

        return def;
    }

    /**
     * Launch a task immediately.
     * To get something running you have to create a TaskSchedule object which
     * becomes an entry in the Quartz job_details table. These objects must
     * have unique names. 
     *
     * In this interface, it is assumed that the name will be the same as
     * the TaskDefinition name. If a schedule object with this name does
     * not exist, create one but do not give it a cron expression.  Then
     * set the newState property to Executing which is a convention
     * used by QuartzPersistenceManager to run the job immediately.
     * 
     * The task executes in a different thread, it does not appear that
     * there is a way to make Quartz execute in the request thread.  If you
     * need synchronous tasks, you have to enter a monitoring loop, and
     * wait for the job to complete.
     *
     * The TaskSchedule object is deleted automatically by JobAdapter
     * when the execution completes.
     *
     * If you find an existing TaskSchedule object with the TaskDefinition name
     * and it is marked as executing, throw an error. If the
     * existing TaskSchedule object has a non-null cron expression throw
     * an error. You should follow the convention that a TaskSchedule
     * with the same name as a TaskDefinition is used for one-shot executions
     * and if you need to schedule something for repeated execution, it
     * must be given a different name.
     *
     * Currently QuartzPersistenceManager will only return the task state
     * as Executing or Suspended. The Suspended state is set when the 
     * job_trigger is in a "paused" state which can be requested by setting
     * the newState to Suspended and saving the object. If the task is just
     * waiting for the next execution, the state will be null.  
     */
    public TaskSchedule run(TaskDefinition def, Map<String,Object> args) 
        throws GeneralException {

        TaskSchedule sched = createImmediateSchedule(def, args);

        // Since QuartzPersistenceManager doesn't maintain transactions
        // we don't need to call commitTransaction here
        _context.saveObject(sched);

        return sched;
    }

    /**
     * Shared method to create a temporary TaskSchedule used
     * for immediate launches.  This is package protected rather than private
     * for purposes of unit testing.
     */
    TaskSchedule createImmediateSchedule(TaskDefinition def,
                                                 Map<String,Object> args) 
        throws GeneralException {
    	
    	/** check to see if the schedule already has an existing running task **/
    	String name = def.getName();
    	TaskResult existing = _context.getObjectByName(TaskResult.class, name);
        if (existing != null) {
            if (existing.getCompleted() == null) {
                if (!def.isConcurrent()) {
                    throw new GeneralException(Message.error(MessageKeys.TASK_INSTANCE_ALREADY_RUNNING,
                            def.getName()));
                }
            }
        }

        // The TaskSchedule is transient so it doesn't really matter
        // what the name is.  We could try to check for the various
        // ResultActions out here, but since this method is not required
        // to throw if Quartz will eventually decide not to run it, we can
        // just push it immediately into the background.

        TaskSchedule sched = new TaskSchedule();
        sched.setName(Util.uuid());
        sched.setDescription(IMMEDIATE_SCHEDULE);
        sched.setArguments(args);

        // set the definition after setting the arguments since it
        // is just put into the arg map
        sched.setDefinitionName(def.getName());
        
        saveLauncher(sched);

        // this is the magic bean that gets QuartzPersistenceManager
        // to immediately setup a trigger
        sched.setNewState(TaskSchedule.State.Executing);

        return sched;
    }

    /**
     * Launch a task immediately, pre-creating a TaskResult.
     * This is a newer launch option for use in places where you
     * need to store the id of the result somewhere (typically a WorkItem)
     * to make it easier to monitor the execution of the task.
     *
     * @ignore
     * The alternative would be store the TaskSchedule id and poll to 
     * see when the result is eventually created but that's ugly.
     *
     * Now that we have this, it could be the default behavior but
     * that change needs to be done carefully.
     */
    public TaskResult runWithResult(TaskDefinition def, 
                                    Map<String,Object> args)
        throws GeneralException {

        // rule #1 - don't trust the state of the definition
        def = _context.getObjectById(TaskDefinition.class, def.getId());

        TaskSchedule sched = createImmediateSchedule(def, args);
        if (null != sched) {
            if (log.isDebugEnabled()) log.debug("created task schedule:  " + sched.toXml());
        }
        
        // sigh, we need the flat arguments when creating the results
        // in case we have result name overrides
        Attributes<String,Object> flatArgs = flattenArguments(def, args);

        // be sure to use the flattened args when creating the result
        TaskResult result = createResult(sched, def, flatArgs, false);
        if (null != result) {
            if (log.isDebugEnabled()) log.debug("created task result: " + result.toXml());
        }

        if (result == null) {
            // the result options in the TaskDefinition don't allow it
            // to run concurrently or use qualified names, which is
            // designer error
            throw new GeneralException(Message.error(MessageKeys.TASK_INSTANCE_ALREADY_RUNNING,
                    def.getName()));
        }
        else {
            sched.setArgument(TaskSchedule.ARG_RESULT_ID, result.getId());
            _context.saveObject(sched);
        }

        return result;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Synchronous Execution
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Run a task synchronously.  This bypasses the Quartz scheduler
     * so you can get the result back in the same thread.
     *
     * If you want the name of the launching user in the TaskResult, it
     * must be passed in the args map as TaskSchedule.ARG_LAUNCHER.
     */
    public TaskResult runSync(String defName, Map<String,Object> args)
        throws GeneralException {

        // Fake up a TaskSchedule to make it look the same as Quartz
        // Note that we're only passing the TaskDefinition by id 
        // and runSync will fetch it again.  While the fetch may be 
        // redundant it makes it consistent and less likely to cause
        // bizarre Hibernate cache problems.

        // we don't save a TaskSchedule, but the executor needs one
        TaskSchedule sched = new TaskSchedule();
        sched.setArguments(args);
        sched.setDefinitionName(defName);
        saveLauncher(sched);

        TaskResult result = runSync(sched, args);
        
        if (result == null) {
            // this can only happen if there was already a task running
            // and the definition was not declared concurrent

            throw new GeneralException(Message.error(MessageKeys.TASK_INSTANCE_ALREADY_RUNNING,
                    defName));
        } else {
            _taskResult = result;
        }
        
        return result;
    }

    /**
     * Run the given TaskDefinition synchronously.
     *
     * @see #runSync(String, Map)
     */
    public TaskResult runSync(TaskDefinition def, Map<String,Object> args)
        throws GeneralException {

        if (def == null)
            throw new GeneralException(MessageKeys.UNRESOLVED_TASK_DEF);

        // rule #1 - don't trust the TaskDefinition
        return runSync(def.getName(), args);
    }

    /**
     * This is used in two contexts, JobAdapter calls it with a 
     * real TaskSchedule from the Quartz repository, and runSync
     * above calls it with a fake one.
     *
     * From Quartz, arguments of the TaskSchedule and TaskDefinition have not
     * yet been flattened.  It is confusing but we don't use the
     * arguments from the TaskSchedule object here, they are passed
     * explictly by the caller.  This is because the Quartz JobAdapter
     * is getting them in a special way and I can't tell if this is
     * always the same as TaskSchedule.getArguments or if there
     * is additional magic.  We've done it this way for awhile now
     * so be careful.
     * 
     * We allow a TaskResult to be created before Quartz processes the
     * trigger.  This is useful in places where we want to launch a background
     * task but immediately get the result object id to save in an 
     * object (such as a work item) for later monitoring.
     */
    public TaskResult runSync(TaskSchedule schedule,
                              Map<String,Object> schedArgs)
        throws GeneralException {

        TaskDefinition def = getTaskDefinition(schedule);

        Attributes<String,Object> flatArgs = flattenArguments(def, schedArgs);

        // look for a pre-created result
        TaskResult result = activateResult(schedule);
        
        if (result == null) {
            result = createResult(schedule, def, flatArgs, true);
        }

        // this may return null if the task is running and we're
        // not allowed to run again, let the caller decide if this
        // is an exception
        if (result != null) {
            result = runSync(schedule, def, result, flatArgs);
        }

        return result;
    }
   
    /**
     * Fetch the TaskDefinition associated with a TaskSchedule.
     */
    public TaskDefinition getTaskDefinition(TaskSchedule sched) 
        throws GeneralException {

        TaskDefinition def = null;

        String definitionName = sched.getDefinitionName();
        if (definitionName == null)
            throw new GeneralException(MessageKeys.UNRESOLVED_TASK_DEF);

        // As of 8.1, this method will be getting the task definition by name going forward.
        // Previously this method would get by ID
        def = _context.getObjectByName(TaskDefinition.class, definitionName);
        if (def == null)
            throw new GeneralException(Message.error(MessageKeys.UNKNOWN_TASK_DEF, definitionName));
        return def;
    }

    /**
     * @exclude
     * This is public so it can be called by CertificationBuilderFactory
     * not sure why...
     */
    public Attributes<String,Object> flattenArguments(TaskSchedule sched) 
        throws GeneralException {

        TaskDefinition def = getTaskDefinition(sched);
        return flattenArguments(def, sched.getArguments());
    }

    /**
     * @exclude
     * Build a flattened Map of arguments first comming from the definition,
     * and overlayed with the args passed into this method, which typically
     * comes from the TaskSchedule object.
     * The TaskDefinition must be attached to the currrent Hibernate session
     * so we can follow the parent list.
     *
     * jsl - why is this public?
     */
    public Attributes<String,Object> flattenArguments(TaskDefinition def, 
                                                      Map<String,Object> args)
        throws GeneralException {

        Attributes<String,Object> flatArgs = def.getEffectiveArguments();

        // then override with the values from the TaskSchedule
        if (args != null) {
            Iterator<Map.Entry<String,Object>> it = args.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry entry = it.next();
                String key = (String)entry.getKey();
                // don't need this?
                if (key.equals(TaskSchedule.ARG_EXECUTOR))
                    continue;
                Object val = entry.getValue();
                flatArgs.put(key, val);
            }
        }
        return flatArgs;
    }
    
    /**
     * Look for a task result that was created early.
     */
    private TaskResult activateResult(TaskSchedule sched)
        throws GeneralException {

        TaskResult result = null;
        String id = sched.getArgument(TaskSchedule.ARG_RESULT_ID);
        if (id != null) {
            result = _context.getObjectById(TaskResult.class, id);
            if (result != null) {
                // Assuming the TaskDefinition and TaskSchedule id stored
                // in the result are still valid but we could double check.

                result.setLaunched(new Date());
                result.setHost(Util.getHostName());
                _context.saveObject(result);
                _context.commitTransaction();
            }
        }

        return result;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Core Synchronous Execution
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Core synchronous task runner used by the other public runSync
     * methods and called directly by TaskExecuteExecutor.
     *
     * TaskSchedule may be from Quartz or a stub created by the other
     * methods. The only things we need it for here is to pass
     * the launcher name to createResult or to check for task
     * host assignment in getTaskExecutor.
     *
     * TaskDefinition must be a valid persistent definition.
     *
     * TaskResult must be a valid persistent result.  It was either
     * created by one of the other runSync or passed in from 
     * TaskExecuteExecutor.
     *
     * The args map must contain the flattened merger of the TaskSchedule
     * and TaskDefinition arguments.
     */
    public TaskResult runSync(TaskSchedule schedule,
                              TaskDefinition definition,
                              TaskResult result, 
                              Attributes<String,Object> args)
        throws GeneralException {

        try {
            // jsl - experiment to add context to locks obtained during
            // this task
            // might be more useful to have the result name?
            LockInfo.setThreadLockContext(definition.getName());
            
            // Executor is saved in a field so terminate(TaskSchedule) called
            // from quartz can ask it to stop
            _executor = getTaskExecutor(definition, args);

            // note that we have to pass the name, not the object or
            // else Auditor will think this is an object-specific event,
            // need to fix that!
            // Also note that we have to pass down the launcher (source)
            // name or else it will be owned by "Scheduler"
            String launcher = result.getLauncher();
            if (launcher == null)
                Auditor.log(AuditEvent.ActionRun, definition.getName());
            else
                Auditor.logAs(launcher, AuditEvent.ActionRun, definition.getName(),
                              null, null, null, null);

            // pjeong: sometimes the AuditEvent gets lost 
            // commit the AuditEvent here
            // jsl: also gets the thread name in the task result
            _context.commitTransaction();
                
            // It is important for some of the more complex executor stacks
            // (Identitizer) to have the launcher in the arg map so it can
            // pass it down consistently.  This is used for things like
            // WorkItem owners, so favor the resolved TaskResult owner
            // rather than the launcher arg which may be abstract.  
            // We might need to seperate the concepts into an abstract
            // "launcher" (any old string) and a concrete "originator" 
            // (an Identity) but it seems enough to make launcher be
            // an Identity name.
            String owner = result.getOwner().getName();
            //if (launcher != null && !launcher.equals(owner))
            //log.warn("Launcher/owner don't match: " + launcher + ", " + owner);

            String argLauncher = args.getString(TaskSchedule.ARG_LAUNCHER);
            if (argLauncher != null && !argLauncher.equals(owner)) {
                //log.warn("Overriding launcher argument: " + 
                //argLauncher + " to " + owner);
                args.put(TaskSchedule.ARG_LAUNCHER, owner);
            }

            // sigh, this has to be passed through the TaskSchedule
            // wish it were an argument but too late now...
            schedule.setTaskDefinition(definition);
            _taskResult = result;

            try {
                ExecutorTracker.addExecutor(result, _executor);

                // indicate that the TaskResult is actually executing now, not just queued for running.
                // The Reanimator will be paying careful attention to this TaskResult now.
                result.setLive(true);
                _context.saveObject(result);
                _context.commitTransaction();

                _executor.execute(_context, schedule, result, args);
            }
            finally {
                try {
                    if (result.isLive()) {
                        // indicate that the TaskResult is not running.  The Reanimator
                        // will no longer try to terminate this TaskResult.
                        result.setLive(false);
                        _context.saveObject(result);
                        _context.commitTransaction();
                    }
                }
                finally {
                    ExecutorTracker.removeExecutor(result);
                }
            }
        }
        catch (Throwable t) {
            // TaskExecutor or something in prep-work failed
            // Put an error message in the result, don't need to
            // propagate this back to Quartz since it just ignores it
            String message = t.getMessage();
            if ( message == null ) 
                message = t.toString();
                	
            log.error("Exception: [" + message + "]", t);

            // If it's one of ours, take off the class prefix to make
            // message in the UI look cleaner.
            if (t instanceof GeneralException) {
                Message msg = ((GeneralException)t).getMessageInstance();
                msg.setType(Message.Type.Error); // Override the type set when the excp was created
                result.addMessage(msg);
            }
            else {
                result.addMessage(new Message(Message.Type.Error,
                                              MessageKeys.ERR_EXCEPTION, t));
            }
                
            // stack can be helpful
            result.setStack(Util.stackToString(t));
        }
        finally {
            // jsl - don't let this linger, in case we're not in a
            // Quartz thread
            LockInfo.setThreadLockContext(null);

            // if the result was transferred to a Request, we have to leave
            // it alone until TaskExecuteExecutor calls back to us
            if (!result.isTransferred()) {

                boolean finalize = true;

                // jsl - logic was added for IIQETN-4185 to finalize even if partitioned
                // flag was on
                // 
                // "if aggregation task is partitioned and there was an exception with the connector,
                // the startRequest will be in a pending status hence it won't be handled
                // by RequestProcessor or HeartbeatService we need to make sure to process postActions."
                //
                // Apparently if there was a failure during the creation of the partitions
                // the task sets the completion status but it leaves the partition flag on.
                // Seems like we need a more obvious way of indicating that.

                if (result.isPartitioned()) {
                    // original logic looks over complicated
                    // it shouldn't matter whether status was Error or not
                    // if (result.isPartitioned() && result.getCompletionStatus()!=null && result.isComplete() &&
                    //    result.getCompletionStatus()==CompletionStatus.Error) {

                    finalize = (result.getCompletionStatus() != null);
                }

                if (finalize) {
                    result = finalizeTask(result);
                }
            }
        }

        // TODO: Need TaskDefinition options that delete the result
        // after execution.

        _executor = null;

        return result;
    }

    /**
     * Instantiate the TaskExecutor.
     * 
     * jsl - This is currently used by TaskResultBean to check for
     * special Jasper handling.  That will probably break if you
     * try to assign a host to a report task.  Try to get TRB
     * to figure it out a different way.
     *
     * Also: TaskBasedUpgrader, TaskDefinitionBean
     */
    public TaskExecutor getTaskExecutor(TaskDefinition def)
        throws GeneralException {

        return getTaskExecutor(def, null);
    }

    /**
     * Instantiate the TaskExecutor.
     * This is normally taken from the TaskDefinition.
     * If a specific execution host (other than this host)
     * is specified, we instead create an instance of RestrictedTaskLauncher.
     *
     * The host check is taken from the args map, not the TaskDefinition.
     * This allows RestrictedTaskExecutor to remove the host from
     * the Map of arguments passed to the Request, so we don't get into
     * a loop.  If we went back to the TaskSchedule or TaskDefinition the
     * host would still be there.
     */
    public TaskExecutor getTaskExecutor(TaskDefinition def,
                                        Attributes<String,Object> args)
        throws GeneralException {

        TaskExecutor exec = null;

        String host = null;
        if (args != null) {
            host = args.getString(TaskSchedule.ARG_HOST);
            // if this is from a Quartz TaskSchedule, empty string is possible due
            // to the JobDataMap transformation
            host = Util.trimnull(host);

            if (Util.getHostName().equalsIgnoreCase(host)) {
                // No need to invoke the restricted task launcher, because this is
                // already the right host to execute the task on.  So set the
                // host back to null so that we execute locally.
                host = null;
            }
        }

        if (host != null) {
            // redirect to a special executor, this doesn't have a TaskDefinition
            // which is nice to keep it out of the UI, but that might be a good
            // place to store configuration, have to use sysconfig instead
            exec = (TaskExecutor)DynamicLoader.instantiate(_context, RestrictedTaskLauncher.class.getName());
        }
        else {
            // Allow this to be dynamically loaded
            String className = def.getEffectiveExecutor();
            if (className == null)
                throw new GeneralException("Missing task executor for " + def.getName());

            // if the task def contains a plugin name then use the plugin class loader
            // to load the executor
            String pluginName = def.getString(TaskDefinition.ARG_PLUGIN_NAME);
            if (Util.isNullOrEmpty(pluginName)) {
                exec = (TaskExecutor) DynamicLoader.instantiate(_context, className);
            }
            else {
                exec = PluginsUtil.instantiate(pluginName, className, Plugin.ClassExportType.TASK_EXECUTOR);
            }
        }
        
        // I don't think we can get here without throwing for some other reason
        if (exec == null)
            throw new GeneralException("Unable to construct executor for " + def.getName());

        return exec;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // TaskResult Creation
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Create a new result object, with various options for handling
     * previous results from this task.
     *
     * This is used in two contexts: when creating an "early result"
     * for a schedule task that has not launched yet, and when creating
     * a result for a task being launched now by Quartz.  In the early
     * result case, a launch date is not set.
     *
     * TaskDefinition is trusted.
     * 
     * Made public so that it's callable from tests
     */
    public TaskResult createResult(TaskSchedule sched, 
                                    TaskDefinition def,
                                    Attributes<String,Object> args,
                                    boolean launching)
        throws GeneralException {

        TaskResult result = null;

        // result name ideally passed in as an argument
        String name = (String)args.get(TaskSchedule.ARG_RESULT_NAME);
        if (name == null) {
            // then fall back to the schedule name, but only if it isn't
            // one of our temporary objects
            if (sched.getCronExpressions() != null)
                name = sched.getName();

            if (name == null) {
                // and finally the definition name
                name = def.getName();
            }
        }

        if (name == null)
            throw new GeneralException(new Message(Message.Type.Error,
                    MessageKeys.UNDEFINED_TASK_RESULT_NAME));

        // Limit the creation process to one thread at a time to avoid
        // excessive name collisions
        CREATION_LOCK.lock();

        try{
            // flesh out a template result, then try to save it under various names
            TaskResult newResult = new TaskResult();
            newResult.setName(name);
            newResult.setTaskSchedule(sched);
            newResult.setDefinition(def);
            newResult.setRunLengthAverage(def.getRunLengthAverage());

            // copy over this special argument from the definition so we don't have
            // to keep going back to the definition
            newResult.setConsolidatedPartitionResults(args.getBoolean(TaskResult.ATT_CONSOLIDATED_PARTITION_RESULTS));

            // setting the result launcher is important because it is used
            // used elsewhere to determine the origin of the task
            // these can be abstract names (not Identity names)
            String launcher = args.getString(TaskSchedule.ARG_LAUNCHER);
            if (launcher == null) {
                // if the args were flattened correctly, this would be non-null too
                launcher = sched.getLauncher();
            }
            if (launcher == null)
                launcher = "System";
            newResult.setLauncher(launcher);

            // need to set owner too for scoping, this must be an Identity
            Identity owner = ObjectUtil.getOriginator(_context, launcher);
            newResult.setOwner(owner);
            // Result scopes apply to reports
            Object scopeArg = def.getArgument(TaskDefinition.TASK_DEFINITION_RESULT_SCOPE);
            if (scopeArg == null) {
                // Task scopes apply to tasks
                scopeArg = def.getArgument(TaskDefinition.ARG_TASK_SCOPE);
            }
            if(scopeArg!=null) {
                Scope scope = null;
                if (scopeArg instanceof Scope){
                    scope = (Scope)scopeArg;
                } else if(scopeArg instanceof String){
                    //Scope doesn't have unique name, so we should always use ID. However, i don't think
                    //we use id all the time -rap
                    scope = this._context.getObjectById(Scope.class, (String)scopeArg);
                }
                newResult.setAssignedScope(scope);
            }

            // set only when we're ready to start
            if (launching) {
                newResult.setLaunched(new Date());
                newResult.setHost(Util.getHostName());
            }

            newResult.setType(def.getEffectiveType());

            // check for existing results
            boolean tryUnqualifiedName = true;

            TaskResult existing = _context.getObjectByName(TaskResult.class, name);
            if (existing != null) {
                if (existing.getCompleted() == null) {
                    // still running, this could be an orphaned task
                    // but Environmenet is responsible for cleaning those up
                    // on startup.
                    if (!def.isConcurrent()) {
                        // concurrent execution not allowed, if we're here
                        // from a repetitive Quartz task, it should just
                        // be silently ignored.
                        if (log.isInfoEnabled())
                            log.info("Ignoring concurrent run of task: " + def.getName());
                        
                        newResult = null;
                        
                        throw new GeneralException(new Message(Message.Type.Error,
                                                                        MessageKeys.TASK_INSTANCE_ALREADY_RUNNING, def.getName()));
                    }
                }

                ResultAction action = def.getResultAction();

                // if not specified treat like delete
                if (action == null) action = ResultAction.Delete;

                // If the existing task was signed we make the Delete
                // action behave like Cancel.  Enabling signoff and
                // having the Delete option should have been prevented in 
                // the UI.
                if (existing.getSignoff() != null && action == ResultAction.Delete)
                    action = ResultAction.Cancel;
                
                if (action == ResultAction.Delete) {
                    // Use the terminator so it unravels all of the
                    // TaskResult stuff like JasperResult and
                    // JasperPageBuckets
                    if (log.isDebugEnabled()) log.debug("deleting previous task result: " + existing.toXml());
                    Terminator ahnold = new Terminator(_context);
                    ahnold.deleteObject(existing);
                    _context.commitTransaction();
                }
                else if (action == ResultAction.Rename) {
                    // Not really a rename, since name qualification
                    // requires the creation of a new object.  Having
                    // trouble detaching and reusing the object, 
                    // "multiple representations.." and all that.  So do
                    // a full clone before removing.

                    /*
                    XMLObjectFactory f = XMLObjectFactory.getInstance();
                    TaskResult clone = (TaskResult)f.clone(existing, _context);
                    clone.setId(null);
                    _context.removeObject(existing);
                    _context.commitTransaction();
                    _context.decache(existing);
                    */

                    if (log.isDebugEnabled()) {
                        log.debug("attempt to save result by renaming the existing one: " + existing.toXml());
                    }
                    saveQualifiedResult(existing, false, true);
                } 
                else if (action == ResultAction.RenameWithUID) {
                    String qualifier = getUIDQualifier(result);
                    saveUniquelyQualifiedResult(existing, qualifier, false);
                }
                else if (action == ResultAction.RenameWithTimestamp) {
                    String qualifier = getTimestampQualifier(existing);
                    saveUniquelyQualifiedResult(existing, qualifier, true);
                }
                else if (action == ResultAction.Cancel) {
                    if (log.isInfoEnabled())
                        log.info("Ignoring concurrent run of task: " + def.getName());
                    
                    newResult = null;
                    String msg = "A result for a previous execution of task '" + 
                        def.getName() + "' still exists.";
                    throw new TaskResultExistsException(msg);
                }
                else if (action == ResultAction.RenameNew) {
                    // we already know there is an existing object
                    // so don't try it again to avoid an exception in the log
                    if (log.isDebugEnabled()) {
                        log.debug("existing result with def action RenameNew, so don't try it again to avoid an exception in the log.");
                    }
                    tryUnqualifiedName = false;
                } 
                else if (action == ResultAction.RenameNewWithUID) {
                    String qualifier = getUIDQualifier(result);
                    result = saveUniquelyQualifiedResult(newResult, qualifier, false);
                    // Null out the newly saved result so we don't try to resave it later
                    newResult = null;
                }
                else if (action == ResultAction.RenameNewWithTimestamp) {
                    String qualifier = getTimestampQualifier(newResult);
                    result = saveUniquelyQualifiedResult(newResult, qualifier, true);
                    // Null out newly saved result so we don't try to resave it later
                    newResult = null;
                }
            }

            if (newResult != null) {
                if (log.isDebugEnabled()) log.debug("attempt to save result: " + newResult.toXml());
                saveQualifiedResult(newResult, tryUnqualifiedName, true);
                result = newResult;
            }
        } finally {
            CREATION_LOCK.unlock();
        }

        return result;
    }

    /**
     * @return String UID that is usable as a qualifer for the given TaskResult
     */
    private String getUIDQualifier(TaskResult result) {
        return Util.uuid();
    }

    /**
     * @return String timestamp that is usable as a qualifer for the given TaskResult.
     * The timestamp is based on the time at which the task was originally launched
     */
    private String getTimestampQualifier(TaskResult result) {
        Date time = result.getLaunched();
        Timestamp timestamp = new Timestamp(time.getTime());
        return timestamp.toString();
    }

    /**
     * @exclude
     * 
     * Attempt to save a TaskResult, adding qualifications to the 
     * name if necessary.  If the "tryUnqualified" flag is set we
     * we first try to save it with the current name, then add
     * qualifications if necessary.
     *
     * In theory this could result in a lot of thrashing if we were
     * in a high concurrency environment with the same tasks being
     * run all the time.  In practice, tasks should rarely have name
     * collisions so we're not bothering to be particularly nice
     * to the database.
     *
     * UPDATE: Geez this is a lot of work to get a qualifier and it
     * isn't particularly reliable.  Consider just sticking a utime 
     * on the end.  Not as pretty but it works.
     *
     * Made this public so we could use it in Workflower
     *
     * NOTE: This may commit and do a full decache.  You need to 
     * have saved other things if appropriate before calling this.
     */
    public void saveQualifiedResult(TaskResult result, 
                                    boolean tryUnqualified)
        throws GeneralException {

        this.saveQualifiedResult(result, tryUnqualified, false);
    }

    public void saveQualifiedResult(TaskResult result,
                                    boolean tryUnqualified, boolean useNewContext)
        throws GeneralException {

        boolean saved = false;
        boolean newObject = (result.getId() == null);
        String origname = result.getName();

        SailPointContext neuCtx = null;
        SailPointContext ctx = this._context;

        try {
            if (useNewContext) {
                //IIQPB-898 - Use new context in case of AlreadyExistsException.
                if (log.isInfoEnabled()) {
                    log.info("Creating private context to bootstrap ManagedAttribute");
                }
                neuCtx = SailPointFactory.createPrivateContext();
                ctx = neuCtx;
                if (!newObject) {
                    //Reload object into session to prevent hibernate proxy association error
                    result = ctx.getObjectById(TaskResult.class, result.getId());
                }
            }

            if (tryUnqualified) {
                try {
                    WORKFLOW_CREATION_LOCK.lock();
                    // if an object exists, saving it will cause a scary
                    // Hibernate/JDBC unique constraint exception in the logs.
                    // Try to avoid this by probing first.
                    QueryOptions ops = new QueryOptions();
                    ops.add(Filter.eq("name", origname));
                    int count = ctx.countObjects(TaskResult.class, ops);
                    if (count == 0) {
                        ctx.saveObject(result);
                        ctx.commitTransaction();
                        saved = true;
                    }
                } catch (Throwable t) {
                    // Sigh, bug 2035 doesn't like seeing the expected
                    // "object already exists" message in the log
                    if (!Util.isAlreadyExistsException(t)) {
                        if (log.isWarnEnabled())
                            log.warn("Unable to save task result: " + t.getMessage(), t);
                    }

                    // have to keep the cache clean
                    try {
                        ctx.decache(result);
                        if (newObject)
                            result.setId(null);
                    } catch (Throwable t2) {
                        log.error("Could not decache result", t2);
                    }
                } finally {
                    WORKFLOW_CREATION_LOCK.unlock();
                }
            }

            String name = unqualify(result.getName());
            if (!saved) {
                int qual = getNextQualifier(name);

                // let the kludging begin!
                int maxAttempts = MAX_RESULT_RETRY;
                Random r = new Random();
                for (int i = 0; i < maxAttempts && !saved; i++) {
                    if ((i + 1) % 4 == 0) { // every 4th attempt, considering a 0 based iteration, sleeps
                        // sleep is randomly between 100 and 5000 ms
                        long sleep = r.nextInt(4900) + 100;
                        try {
                            Thread.sleep(sleep);
                        } catch (InterruptedException e) {
                            log.info("Interrupting qualified naming sleep");
                        }
                    }
                    String newName = qualify(name, qual);
                    result.setName(newName);

                    try {
                        if (tryUnqualified) {
                            WORKFLOW_CREATION_LOCK.lock();
                        }
                        QueryOptions ops = new QueryOptions();
                        ops.add(Filter.eq("name", newName));
                        int count = ctx.countObjects(TaskResult.class, ops);
                        if (count == 0) {
                            ctx.saveObject(result);
                            ctx.commitTransaction();
                            saved = true;
                        } else {
                            qual++;
                        }
                    } catch (Throwable t) {
                        // this may be a name collision but it is often
                        // something more severe that we need to know
                        if (!Util.isAlreadyExistsException(t)) {
                            if (log.isWarnEnabled())
                                log.warn("Unable to save task result: " + t.getMessage(), t);
                        }

                        try {
                            // have to reconnect to work around
                            // Hibernate not decaching collections
                            //_context.decache();
                            ctx.reconnect();
                            if (newObject)
                                result.setId(null);
                        } catch (Throwable t2) {
                            // got here during testing, represents a
                            // fundamental error, don't let it leak
                            i = maxAttempts;
                        }
                        // !! should try to look for the specific Hibernate
                        // error that indicates a unique constraint violation,
                        // or better yet, try to catch that in
                        // HibernatePersistenceManager and throw an identifiable
                        // exception
                        qual++;
                    } finally {
                        if (tryUnqualified) {
                            WORKFLOW_CREATION_LOCK.unlock();
                        }
                    }
                }
            }

            if (!saved) {
                try {
                    // one last attempt
                    result.setName(name + " " + Util.uuid());
                    ctx.saveObject(result);
                    ctx.commitTransaction();
                } catch (Throwable t) {
                    log.error("Unable to save task result with unique value: " + t.getMessage(), t);
                    throw new GeneralException(new Message(Message.Type.Error,
                            MessageKeys.CANT_CREATE_TASK_RESULT, origname));
                }
            }
        } finally {
            if (neuCtx != null) {
                SailPointFactory.releasePrivateContext(neuCtx);
                if (result != null && Util.isNotNullOrEmpty(result.getId())) {
                    ObjectUtil.reattach(this._context, result);
                }
            }
        }
    }

    /**
     * Update the given result with the specified qualifer, and save it.
     * This method is a lighter weight version of saveQualifiedResult 
     * that operates under the assumption that the qualifier is 
     * not as susceptible to name collisions
     * @param isTimestamp For now, this is a boolean because there are only two modes:  
     * Timestamp and UID.  If we ever come up with more ways to uniquely qualify
     * TaskResults we need to revisit this
     */
    private TaskResult saveUniquelyQualifiedResult(TaskResult result, String qualifier, boolean isTimestamp) throws GeneralException {
        boolean isSaved = false;
        boolean saveFailed = false;
        boolean isNewObject = Util.isNullOrEmpty(result.getId());
        String unqualified = unqualify(result.getName());
        int retries = 0;
        while (!isSaved && retries < MAX_RESULT_RETRY) {
            if (saveFailed) {
                // Offset by a random interval between now and a second for the retry
                // to improve our chances of getting a unique name.  This is not fool-proof;
                // it just decreases the collision rate.  To be absolutely fool-proof we would 
                // have to manage this through a database trigger because we have no control
                // over other instances in the cluster.
                if (isTimestamp && retries < 19) {
                    Timestamp timestamp = Timestamp.valueOf(qualifier);
                    long time = timestamp.getTime();
                    time += Math.round(Math.random()) % 1000l;
                    timestamp.setTime(time);
                    qualifier = timestamp.toString();
                } else {
                    qualifier = Util.uuid();
                }
            }
            String qualified = unqualified + QUALIFICATION_DELIMITER + qualifier;
            result.setName(qualified);
            try {
                _context.saveObject(result);
                _context.commitTransaction();
                isSaved = true;
            } catch (Throwable t) {
                try {
                    _context.reconnect();
                    if (isNewObject) {
                        result.setId(null);
                    }
                    saveFailed = true;
                    retries++;
                } catch(Throwable t2) {
                    // got here during testing, represents a
                    // fundamental error, don't let it leak
                    retries = 20;
                }
            }
        }

        return result;
    }

    /**
     * Find all similar names, and examine the existing qualifiers.
     * Derive the next qualifier that can be used to generate a unique name.
     * Note that this is only a best guess since the 
     * table is not being locked and someone else can sneak in and 
     * claim the name while IdentityIQ is processing.
     * Caller still needs to probe to verify that it 
     * can be created.
     * !! It would be a lot easier if we could just atomically search
     * for the qualifiers and create the object.  Explore keeping
     * a transaction open and getting a table lock someday...
     */  
    private int getNextQualifier(String name) 
        throws GeneralException {

        int qualifier = 1;

        name = unqualify(name);

        // before we start probing, try to get into the right range
        List<String> proj = new ArrayList<String>();
        proj.add("name");
        QueryOptions ops = new QueryOptions();
        ops.add(Filter.like("name", name, Filter.MatchMode.START));
        // Only get the most recently modified tasks
        ops.addOrdering("created", false);
        ops.setResultLimit(10);
        Iterator<Object []> result = 
            _context.search(TaskResult.class, ops, proj);

        if (result != null) {
            while (result.hasNext()) {
                String s = (String)(result.next()[0]);
                int q = getQualifier(s);
                if (q >= qualifier) {
                    qualifier = q + 1;
                }
            }
        }

        return qualifier;
    }

    /**
     * @exclude
     * Delimiter used for numeric qualification.
     * This needs to be unlikely to be used in a task definition or schedule
     * name.
     * TODO: Could allow the delimiter to be confiurable.
     */
    public static final String QUALIFICATION_DELIMITER = " - ";

    /**
     * Remove the numeric qualifier from a name if one exists.
     * This is a guess since we don't control the syntax of the name,
     * but if we use an unusual delimiter it should be reasonably safe.
     *
     * TODO: Could allow the delimiter to be confiurable.
     */
    private String unqualify(String name) {
        
        int delim = name.lastIndexOf(QUALIFICATION_DELIMITER);
        if (delim > 0) {
            int dlen = QUALIFICATION_DELIMITER.length();
            String remainder = name.substring(delim + dlen);

            // If this parses to a non-zero integer, chances are good
            // its one of our qualifiers, in theory there could be other
            // tokens on the line so ideally we should examine each 
            // character of the remainder...
            int qualifier = Util.atoi(remainder);
            if (qualifier > 0)
                name = name.substring(0, delim);
        }
        return name;
    }

    /**
     * Format a qualified name.
     */
    private String qualify(String name, int number) {

        return name + QUALIFICATION_DELIMITER + Util.itoa(number);
    }

    /**
     * Extract the numeric qualifier from a name.  Return zero if
     * the name is not qualified.
     */
    private int getQualifier(String name) {

        int qualifier = 0;

        int delim = name.lastIndexOf(QUALIFICATION_DELIMITER);
        if (delim > 0) {
            int dlen = QUALIFICATION_DELIMITER.length();
            String remainder = name.substring(delim + dlen);
            qualifier = Util.atoi(remainder);
        }
        return qualifier;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Waiting
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Returns true if a task with the specified name is scheduled in the executing state or 
     * if the task result with the specified name already exists and is not complete.
     * @param taskName Name of the task that is being checked
     * @param resultName Name of the result of the task that is being checked
     * @return true if a task with the specified name is scheduled in the executing state or 
     * if the task result with the specified name already exists and is not complete
     * @throws GeneralException thrown if the schedule or result cannot be retrieved
     */
    public boolean isTaskRunning(String taskName, String resultName) throws GeneralException {
        boolean isTaskRunning = false;

        TaskSchedule existing = null;

        // if there is a quartz task name to check
        if (Util.isNotNullOrEmpty(taskName))  {
            existing = _context.getObjectByName(TaskSchedule.class, taskName);
        }

        if (existing == null) {
            // Check for a result
            TaskResult existingResult = _context.getObjectByName(TaskResult.class, resultName);
            if (existingResult != null && !existingResult.isComplete()) {
                isTaskRunning = true;
            }
        } else {
            if (existing.getState() == TaskSchedule.State.Executing) {
                isTaskRunning = true;
            }
        }

        return isTaskRunning;
    }

    /**
     * Wait for a background task to complete within a given number of seconds.
     * Due to the nature of the Quartz model, it is difficult to reliably 
     * determine when the task ends by monitoring the TaskSchedule object.
     * We can reliably know if the task is executing by the presence of
     * a JobExecutionContext, but once the task is finished it is unclear
     * if we can tell the difference between "finished" and "not yet started".
     * Probably there is if we dig into the SimpleTrigger model more, but
     * it is easier to rely on our own TaskResult conventions.  
     *
     * To wait for a task, the executor must produce a TaskResult.
     * And the completion date must be stored in the TaskResult before the
     * executor terminates.
     * 
     * @ignore
     * 
     * NOTE: We still have some modeling problems that prevent us from
     * reliably knowing the name of the TaskResult that will be produced
     * when running a task.  Currently the name is up to the executor,
     * by default it is the same name as TaskSchedule name, but may
     * be modified with an argument in either the TaskSchedule or
     * TaskDefinition.  Assuming TaskSchedule name is fine for unit tests,
     * but naming of the TaskResult probably needs to be implemented
     * in the TaskManager and returned from the run() method rather
     * than the TaskSchedule.  In effect, TaskResult then becomes
     * like the TaskInstance in waveset.
     * 
     * KG: This was in SailPointTest but has been moved out because a couple
     * of our demo tasks needed to be able to launch other tasks and wait for
     * them.
     */
    public TaskResult awaitTask(TaskSchedule sched, int seconds)
        throws Exception {
        // First, look to see if the task result name is specified.  If not
        // explicitly specified, use the name of the task definition.
        // Note that the TaskSchedule may be deleted when the task finishes
        // so get the result name quickly!
        String resultName = sched.getArgument(TaskSchedule.ARG_RESULT_NAME);
        if (null == resultName) {
            resultName = sched.getDefinition().getName();
        }
        return awaitTask(resultName, seconds);
    }

    /**
     * See awaitTask that takes TaskSchedule for documentation.
     * 
     * CH:Split this out for when you have the result but not the full schedule.
     */
    public TaskResult awaitTask(String resultName, int seconds) throws Exception {
        TaskResult result = null;
        int iterations;

        for (iterations = 0 ; iterations < seconds ; iterations++) {
            pause(1);

            // NOTE: The Hibernate session cache appears to not track
            // changes made in other threads within the same JVM.  To
            // accurately poll for the result object, we have to clear
            // the cache.  
            // NOTE2: I was unable to make this work simply by calling
            // Session.clear, I had to close the session as well.  Calling
            // evict on the individual object didn't work either.  Possibly
            // a 2nd level cache issue?

            // doesn't work
            //if (result != null)
            //context.decache(result);

            // neither does this
            //context.decache();

            // reopen the session, geez what a crock
            _context.reconnect();

            // this may be null if Quartz hasn't started the job yet
            // Unit tests are using ID, src code is using name. Need to fix unit test -rap
            result = _context.getObjectByName(TaskResult.class, resultName);
            /*
            if (result == null)
                println("TaskResult '" + resultName + "' does not exist.");
            else if (!result.isComplete()) {

                println("Read result " + result + " from context " + context);
                //println("TaskResult '" + resultName + "' is not complete.");
                println(result.toXml());
            }
            */

            if (result != null && result.isComplete()) {
                // KLUDGE: A few tests will want to schedule another task
                // with name immediately after this one finishes.  TaskManager
                // will throw an error if it finds a TaskSchedule object
                // with a non-null State property which indiciates that it is
                // still executing.  Unfortunately there is a small window between
                // when the result is marked complete and the TaskSchedule state is
                // cleared.  Technically we probably should be polling on the TaskSchedule
                // rather than the TaskResult, but I'm not sure that's right either.
                // Shouldn't we be able to have several tasks triggered from the
                // same schedule?
                pause(1);
                break;
            }
        }

        if (iterations >= seconds)
            throw new GeneralException("Timeout waiting for task completion: " + resultName);

        return result;
    }

    /**
     * Helper method to pause for some number of seconds.
     * Used when waiting for the scheduler to do something.
     */
    public static void pause(int seconds) {

        try {
            Thread.sleep(1000 * seconds);
        }
        catch (InterruptedException e) {
            // ignore
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Terminate & Restart
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * @exclude
     * Called by Quartz when it receives an "interrupt" signal.
     * Should only see this if we've got a transient _executor
     * set by runSync.  
     *
     * Starting in 7.2 this is only used when terminating tasks
     * that are running on the local machine.  terminate(result) will
     * set a flag in the TaskSchedule which will indirectly cause Quartz
     * to interrupt the thread, which calls this.  It is no longer used
     * for cross host termination requests.  This could be simplified
     * to use the ExecutorTracker.
     */
    public boolean terminate() {
        boolean terminated = false;
        if(_taskResult != null) {
            _taskResult.setTerminateRequested(true);
        }
        if (_executor != null)
            terminated = _executor.terminate();
        return terminated;
    }

    /**
     * Terminate a background task.
     * The task is identified by its TaskResult.
     * Another methods allows the task to be terminated by TaskSchedule
     * but since these are normally generated and not seen in the UI
     * it is more likely to terminate from the result.
     * 
     * Note that there is no guarantee that the TaskExecutor will
     * respond to a termination request, and it remains to be seen
     * if Quartz will deliver interrupt requests across a cluster.
     */
    public boolean terminate(TaskResult result) throws GeneralException {

        boolean terminated = false;
        String host = result.getHost();

        if (result.getType() == TaskDefinition.Type.Workflow) {
            // these aren't Quartz tasks, forward to the Workflower
            WorkflowCase wfc = null;
            String cid = result.getString(WorkflowCase.RES_WORKFLOW_CASE);
            if (cid == null)
                log.error("Terminating workflow task with no case id");
            else {
                wfc = _context.getObjectById(WorkflowCase.class, cid);
                if (wfc == null)
                    log.error("Terminating workflow task with missing case");
            }

            if (wfc != null) {
                Workflower wf = new Workflower(_context);
                wf.terminate(wfc);
            }
            else {
                // invalid, just mark it terminated, should we delete it?
                result.setTerminated(true);
                _context.saveObject(result);
                _context.commitTransaction();
            }
            terminated = true;
        }
        else if (result.isPartitioned()) {
            // it is being handled by Requests on multiple machines
            RequestManager rm = new RequestManager(_context);
            rm.terminatePartitionedTask(result);
        }
        else if ((host != null && !Util.getHostName().equals(host)) ||
                 _useRequestForTermination) {

            sendCommand(result, TaskCommandExecutor.COMMAND_TERMINATE, null);
        }
        else {
            // jsl - starting in 7.2 this is only used to terminate tasks
            // running on the same machine, this could be simplified to
            // use ExecutorTracker like TaskCommandExecutor
            TaskSchedule sched = null;

            String schedId = result.getSchedule();
            if (schedId != null)
                sched = _context.getObjectById(TaskSchedule.class, schedId);

            // If there was no id it means the task is not running
            // if we couldn't find the TaskSchedule it could mean that
            // Quartz is in the process of deleting it.  In both cases
            // return false to indiciate that the task is not running.
            // !! May want to check the completion status on the
            // TaskResult too for consistency.  This would be a good time
            // to detect dummy results that were left in an executing state
            // after a crash...

            if (sched != null) {
                terminate(sched);
                terminated = true;
            }
        }

        return terminated;
    }

    /**
     * Terminate a Quartz task.
     * When the TaskResult is saved QuartzPersistenceManager will convert
     * the TaskSchedule.State.Terminated property into Quartz API calls to 
     * interrupt the task.  This will end up in terminate() above for the
     * TaskManager instance created by the JobAdapter.  See class comments for more.
     */
    public void terminate(TaskSchedule sched) throws GeneralException {

        if (sched != null) {
            // the mechanism for this is to save desired state
            sched.setNewState(TaskSchedule.State.Terminated);
            _context.saveObject(sched);
        }
    }

    /**
     * Restart a completed task.
     * This was designed for use with partitioned tasks where some partitions
     * succeeded and some failed, but this could be made to work
     * for normal tasks.  Would that ever be necessary?  You can always just
     * relaunch the TaskDefinition.
     *
     * UI should call result.canRestart() before calling this.
     */
    public void restart(TaskResult result) throws GeneralException {

        if (!result.isRestartable())
            throw new GeneralException("Task result '" + result.getName() + "' is not marked as restartable");

        CompletionStatus status = result.getCompletionStatus();
        if (status == null)
            throw new GeneralException("Task is still running");

        if (status != CompletionStatus.Error && 
            status != CompletionStatus.Terminated) 
            throw new GeneralException("Task did not complete with errors");

        // could handle both of these eventually, throw specific errors
        if (result.getType() == TaskDefinition.Type.Workflow) {
            throw new GeneralException("Workflow tasks are not restartable");
        }
        else if (!result.isPartitioned()) {
            throw new GeneralException("Non-partitioned tasks are not restartable");
        }
        else {
            // RequestProcessor does the work, but we're using RequestManager
            // as a fascade API
            RequestManager rm = new RequestManager(_context);
            rm.restart(result);
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Commands
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Schedule a task command.
     * 
     * It would be nice to avoid scheduling the same command more than
     * once, but for the current set of commands it doesn't really matter.
     * Order of multiple commands is also random which is okay until we
     * have pairs of start/end commands, in which case request dependencies
     * is probably the best way to handle that.
     *
     * TODO: Could detect if this host is running the task and bypass the 
     * request, but I want to get all the machinery working first.
     */
    public void sendCommand(TaskResult result, String command, Attributes<String,Object> args)
        throws GeneralException {

        RequestDefinition def = _context.getObjectByName(RequestDefinition.class, TaskCommandExecutor.REQUEST_DEFINITION);
        if (def == null)
            throw new GeneralException("Unable to send command, missing RequestDefinition: " +
                                       TaskCommandExecutor.REQUEST_DEFINITION);

        if (!result.isPartitioned()) {
            String host = result.getHost();
            if (host == null)
                throw new GeneralException("Can't schedule task command without a host");

            Request req = new Request(def);
            req.setHost(host);
            req.setName("Task: " + result.getName() + ", Command: " + command + ", Host: " + host);
            req.setAttributes(args);
            req.setExecutorCommand(command);
            req.put(TaskCommandExecutor.ARG_TASK_RESULT, result.getId());
            _context.saveObject(req);   
            _context.commitTransaction();
        }
        else {
            // get the running partition Request objects
            // in theory some could start some while we're doing this, we don't have a
            // way to do a cluster-wide suspension of request handling, user will just
            // have to send the command again if some leak through
            QueryOptions ops = new QueryOptions();
            ops.add(Filter.eq("taskResult", result));
            List<String> props = new ArrayList<String>();
            props.add("id");
            props.add("name");
            props.add("host");
            Iterator<Object[]> requests = _context.search(Request.class, ops, props);
            while (requests.hasNext()) {
                Object[] row = requests.next();
                String id = (String)(row[0]);
                String name = (String)(row[1]);
                String host = (String)(row[2]);

                if (host == null) {
                    log.warn("Missing host name for partition request: " + name);
                }
                else {
                    Request req = new Request(def);
                    req.setHost(host);
                    req.setName("Task: " + result.getName() + ", Partition: " + name + ", Command: " + command + ", Host: " + host);
                    req.setAttributes(args);
                    req.setExecutorCommand(command);
                    req.put(TaskCommandExecutor.ARG_REQUEST_ID, id);
                    _context.saveObject(req);   
                    _context.commitTransaction();
                }
            }
        }
    }

    /**
     * Send a terminate command to a task.
     */
    public void sendTerminateCommand(TaskResult result)
        throws GeneralException {

        sendCommand(result, TaskCommandExecutor.COMMAND_TERMINATE, null);
    }

    /**
     * Send a stack capture command to a task.
     */
    public void sendStackCommand(TaskResult result)
        throws GeneralException {

        // TODO: Could detect if the host is this machine and bypass the
        // Request, but I want to get all the machinery working first
        sendCommand(result, TaskCommandExecutor.COMMAND_STACK, null);
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Finalization
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Finalization is the process of saving the final TaskResult after
     * execution has completed, and then taking various post-execution
     * actions.
     * 
     * Closing the TaskResult is the most important and must complete.
     * Actions may fail and are not currently retried.
     *
     * Public so it can be called by RequestProcessor for the finalization
     * of partitioned tasks.
     *
     * This may decache one or more times, so the passed result should
     * be  replaced with the return value if the caller needs to do 
     * more with the result.
     */
    public TaskResult finalizeTask(TaskResult result) {

        boolean resultClosed = false;
        int runLength = -1;
        try {
            // formerly nulled the host name, but starting in 7.2
            // we leave it for diagnostics
            result.setCompleted(new Date());
            result.setProgress(null);

            // sets CompletionStatus and terminated flag
            calculateCompletionStatus(result);

            // Put a consistnet message in the result if we were terminated
            // mt - As of 6.0, each task executor should set terminated flag 
            // in result (Bug #11256).  So we can check that to add the warning. 
            if (result.isTerminated()) {
                result.addMessage(Message.warn(MessageKeys.TASK_MSG_TERMINATED));
            }

            runLength = saveRunLength(result);

            // The TaskResult and Hibernate session are in an unknown state.
            // Normally a simple decache() and reattach are enough, but old comments
            // have some superstitons about using reconnect().  Not sure how
            // accurate this is, but it's been this way for a long time...
            // [begin old comments]
            // Note that we use reconnect here rather than decache due
            // to some obscure Hibernate issue decaching  collections.
            // The TaskResult._errors collection appears
            // to remain in the cache after calling Session.clear
            // so when we reattach it and commit we get the
            // "found two representations" exception.  This may have
            // been a bug in the older version of Hibernate we started
            // with, but whatever the reason start  absolutely fresh here
            // because it's important that the result be saved.
            _context.reconnect();
            _context.saveObject(result);
            _context.commitTransaction();
            resultClosed = true;
        }
        catch (Throwable t) {
            // something seriously wrong with Hibernate
            log.error("Unable to close TaskResult: " + result.getName());
            log.error(t);
        }

        // only perform post actions if we successfully closed the result
        if (resultClosed) {
            try {
                if (runLength >= 0) {
                    addAverageRunLength(result, runLength);
                }

                // addAverageRunLength is leaving the Hibernate session
                // in a funky state, apparently because it does a transaction
                // lock on the TaskResult->TaskDefinition, this evicts it,
                // but does not replace the reference in the TaskResult?
                // Whatever the cause the TaskDefinition accessible through the TaskResult
                // is not attached and will get errors checking signoffs, get a clean copy
                _context.decache();
                result = _context.getObjectById(TaskResult.class, result.getId());

                // open signoff work items if configured
                if (result.getErrors() == null) {
                    checkSignoffs(result);
                }

                // At this moment, the TaskResult is in the session, once
                // we start calling Rules though they can trash the place.
                // It is currently assumed that they won't, and this hasn't
                // been a problem, but could be more tolerant from here down.
            
                // run a configured rule which by default sends an email
                runCompletionRule(result);

                // process TaskEvents, a more flexible replacement for the completion rule
                processTaskEvents(result);
            }
            catch (Throwable t) {
                log.error("Unchecked exception running task finalization");
                log.error(t);
            }
        }

        return result;
    }

    /**
     * Calculate the final completion status for the result.
     *
     * See the weirdness for partitioned tasks that set the completion
     * status in the root result for IIQETN-4185.  Should we just assume
     * that all tasks are allowed to set their own completion status
     * in advance?
     */
    private void calculateCompletionStatus(TaskResult result) {

        CompletionStatus status = null;
        
        if (!result.isPartitioned()) {
            // looks at terminate flag and message lists
            result.setCompletionStatus(result.calculateCompletionStatus());
        }
        else if (result.getCompletionStatus() == null) {
            // allow pre-emptive completion if the partition creation process failed
            int total = 0;
            int complete = 0;
            int successes = 0;
            int terminations = 0;
            int warnings = 0;
            int errors = 0;

            List<TaskResult> partitions = result.getPartitionResults();
            if (partitions != null) {
                for (TaskResult partition : partitions) {
                    total++;
                        
                    // note that we check the completion date not the status
                    // retryable partitions will have no date but an Error status
                    if (partition.getCompleted() != null)
                        complete++;

                    CompletionStatus compstat = partition.getCompletionStatus();
                    if (compstat == CompletionStatus.Success)
                        successes++;
                    else if (compstat == CompletionStatus.Warning)
                        warnings++;
                    else if (compstat == CompletionStatus.Error)
                        errors++;
                    else if (compstat == CompletionStatus.Terminated)
                        terminations++;
                }

                if (complete != total) {
                    // RequestProcessor should only have called finalize
                    // if all partitions were done
                    log.error("Finalizing partitioned result with incomplete partition completion status");
                }

                // who wins int the battle between error and termination?
                // original code in RequestProcessor checked errors first, seems wrong?

                // errors can be in the partition result or the root result
                if (errors > 0 || result.hasErrors()) {
                    result.setCompletionStatus(CompletionStatus.Error);
                }
                else if (successes == total) {
                    // we're all winners here
                    result.setCompletionStatus(CompletionStatus.Success);
                }
                else if (terminations > 0) {
                    // if any one is marked terminated assume the others were too, 
                    // though they may have Error if they died trying to terminate
                    result.setCompletionStatus(CompletionStatus.Terminated);
                    // sigh, have to keep these in sync
                    result.setTerminated(true);
                }
                else {
                    result.setCompletionStatus(CompletionStatus.Warning);
                }
            }
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Finalization: Run Length
    //
    //////////////////////////////////////////////////////////////////////
    
    /**
     * Save run length statistics in the TaskResult.
     * This is part of the result closing phase that must complete.
     * Updating run length averages in the TaskDefinition is done in a later phase.
     * 
     * Return value is the number of run seconds to add to the average. Negative
     * indicates that something was wrong so the average should not be updated.
     */
    private int saveRunLength(TaskResult result) {

        int seconds = -1;
        Date start = result.getLaunched();
        Date end = result.getCompleted();
        
        // all of these are supposed to be set by now
        if (start == null || end == null) {
            // not supposed to happen
            log.error("Missing launched/completed time in TaskResult");
        }
        else if (!DisableTaskRunStatistics) {
            // let this be disabled for unit tests
            long millis = end.getTime() - start.getTime();
            seconds = (int)(millis / 1000);

            // I guess set the run time even if the task failed?
            result.setRunLength(seconds);

            int average = result.getRunLengthAverage();
            int deviation = 0;
            if (average > 0) {
                deviation = (int)(((seconds - average) / (float)average) * 100.0f);
            }
            else {
                // not supposed to be negative but could be zero for a very short task
                // actually, leave this zero, if we have never ran before it will
                // show up as a massive deviation the first time, 
                // deviation = seconds * 100;
            }
            result.setRunLengthDeviation(deviation);
        }

        return seconds;
    }

    /**
     * Add a task run length to the average in the TaskDefinition.
     * This is part of the post-execution action phase so it does not interfere
     * with closing the TaskResult.
     * 
     * This will commit, context must be clean.
     */
    private void addAverageRunLength(TaskResult result, int seconds) {
    
        try {
            // assuming completion with warnings is still enough to
            // effect the running average, needs discussion
            CompletionStatus status = result.getCompletionStatus();
            if (status == CompletionStatus.Success ||
                status == CompletionStatus.Warning) {

                TaskDefinition def = result.getDefinition();
                if (def == null) {
                    log.error("TaskResult missing TaskDefinition");
                }
                else {
                    def = ObjectUtil.transactionLock(_context, TaskDefinition.class, def.getId());
                    if (def != null) {
                        def.addRun(seconds);
                        _context.saveObject(def);
                        _context.commitTransaction();
                    }
                }
            }
        }
        catch (Throwable t) {
            log.error("Unable to update TaskDefinition run length average");
            log.error(t);
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Finalization: Completion Rule
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Run a single configured rule after task completion.
     * The default rule sends an email notification.
     * 
     * Prior to 7.2 this was called the "post action".
     *
     * jsl - I think this is an older mechanism for task completion 
     * side effects.  It looks like it has been replaced by the more flexible
     * TaskEvent of which there can be multiple associated with
     * the task.  
     */
    private void runCompletionRule(TaskResult result) {
        
        if (log.isInfoEnabled()) {
            log.info("Checking completion rule: " + result.getName());
        }

        try {
            TaskDefinition taskDefinition = result.getDefinition();
            String ruleName = taskDefinition.getString(Configuration.TASK_COMPLETION_RULE);

            if (ruleName != null) {
                if (log.isInfoEnabled()) {
                    log.info("Rule found in TaskDefinition: " + ruleName);
                }
            } 
            else {
                Configuration sysConfig = _context.getConfiguration();
                ruleName = sysConfig.getString(Configuration.TASK_COMPLETION_RULE);
                if (ruleName != null) {
                    if (log.isInfoEnabled()) {
                        log.info("Rule found in system configuration: " + ruleName);
                    }
                }
            }

            if (ruleName != null) {
                Rule rule = _context.getObjectByName(Rule.class, ruleName);
                if (rule == null) {
                    log.error("Invalid task completion rule: " + ruleName);
                }
                else {
                    Map<String, Object> ruleContext = new HashMap<String, Object>();
                    ruleContext.put("result", result);
                    _context.runRule(rule, ruleContext);
                }
            }
        }
        catch (Throwable t) {
            log.error("Exception during task post action: "+ t.getMessage(), t);
        }
    }
    
    //////////////////////////////////////////////////////////////////////
    //
    // Finalization: Task Events
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Process any TaskEvents that have been associate with the given TaskResult
     * with phase set to PHASE_COMPLETION.
     *
     * jsl - I'm unsure of the history of this but it looks like a more flexible
     * alternative to the single completion rule.  I guess we kept the other
     * for backward compatibility.  
     *
     * Each TaskEvent has a reference to a Rule that will be called.  The Rule is
     * allowed to make changes to the  TaskResult.  This is indiciated by the rule 
     * returning a Map that contains the "taskResult" key.  The value of that map 
     * entry is a TaskResult that is to be saved.
     *
     * It doeesn't look like we've ever deleted the TaskEvents, Terminator will
     * eventually but it seems odd that we would just let the hang here.
     */
    private void processTaskEvents(TaskResult result) {

        try {
            // Look for any completion events on this TaskResult
            QueryOptions ops = new QueryOptions(Filter.eq("taskResult", result));
            ops.add(Filter.eq("phase", TaskEvent.PHASE_COMPLETION));
            IncrementalObjectIterator<TaskEvent> events =
                new IncrementalObjectIterator<TaskEvent>(_context, TaskEvent.class, ops);

            while (events.hasNext()) {
                TaskEvent event = events.next();
                Rule rule = event.getRule();
                if (rule == null) {
                    // not supposed to happen right?
                    log.warn("TaskEvent without rule reference: " + event.getId());
                }
                else {
                    Map<String, Object> args = new HashMap<String, Object>();
                    args.put("taskResult", result);
                    args.put("event", event);
                    boolean requiresUpdate = false;

                    // Make sure an error in one rule doesn't cause other events
                    // to not run
                    try {
                        Map ruleResult = (Map)_context.runRule(rule, args);
                        if (ruleResult != null) {
                            Object altResult = ruleResult.get(TaskEvent.RULE_RETURN_TASK_RESULT);
                            if (altResult instanceof TaskResult) {
                                result = (TaskResult)altResult;
                                requiresUpdate = true;
                            }
                        }
                    }
                    catch (Throwable t) {
                        log.error("Exception running task event rule: " + rule.getName());
                        log.error(t);
                    }

                    if (requiresUpdate) {
                        // Persist any changes made by the event
                        _context.saveObject(result);
                        _context.commitTransaction();
                        _context.decache();
                        _context.attach(result);
                    }
                }
            }
        }
        catch (Throwable t) {
            log.error("Exception during task event processing: "+ t.getMessage(), t);
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Finalization: Signoffs
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * After a task completes check to see if it requires signoff.
     * Part of the post-execution action phase, TaskResult has already
     * been saved and is in the session.
     *
     * Signoffs work items are only created if the task completes without error.
     */
    private void checkSignoffs(TaskResult result) {

        if (result.getErrors() == null) {
            try {
                TaskDefinition def = result.getDefinition();
                TaskSignoffGenerator signoffGenerator = new TaskSignoffGenerator(_context, def, result, _launcher);
                if (signoffGenerator.isSignoffRequired()) {
                    signoffGenerator.generateSignoffs();
                    _context.saveObject(result);
                    _context.commitTransaction();
                }
            }
            catch (Throwable t) {
                Message msg = new Message(Message.Type.Error, MessageKeys.ERR_EXCEPTION, t);
                log.error(msg.getMessage(), t);
            
                // we have traditionally added this to the TaskResult too,
                // in 7.2 we don't since we're past closing the result and signoff
                // generation errors aren't really "in" the result which
                // would effect the completion status, the log is enough
            }
        }
    }
    
    //////////////////////////////////////////////////////////////////////
    //
    // WorkItemHandler for Signoffs
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * @exclude
     * 
     * Called via Workflower whenever ownership changes.  This should
     * only update any associated models to reflect the change, generic
     * operations like notifications and commenting have already
     * been done.
     *
     * Do not commit the transaction.
     */
    public void forwardWorkItem(SailPointContext context, WorkItem item,
                                Identity newOwner)
        throws GeneralException {

        _context = context;

        WorkItem.Type type = item.getType();
        String resultId = item.getTargetId();

        if (type != WorkItem.Type.Signoff) {
            // how did this get here?
            if (log.isErrorEnabled())   
                log.error("Unsupported work item type " + type.toString());
        }
        else {
            TaskResult result = _context.getObjectById(TaskResult.class, resultId);
            if (result == null) {
                // someone must have deleted the result out from under us
                // Workflower still wants the WorkItem to be valid after this
                // method returns so we can't delete it now.  Wait for the
                // process() call.
                log.warn("Signoff result disappeared!");
            }
            else {
                Signoff signoff = result.getSignoff();
                if (signoff == null) {
                    // hmm, this should have been fleshed out by now
                    log.error("Lost task signoff state");
                    signoff = new Signoff();
                    result.setSignoff(signoff);
                }
                
                Signatory sig = signoff.find(item);
                if (sig == null) {
                    // somehow we lost a matching signatory
                    log.error("Lost a Signatory");
                    sig = signoff.add(item);
                }

                if (sig != null) {
                    sig.setOwner(newOwner);
                }

                _context.saveObject(result);
                // Workflower will commit
            }
        }
    }

    /**
     * @exclude
     * Validate modifications to a work item before it is persisted.
     * This is called indirectly by Workflower which must be used
     * by WorkItemBean and other places where we want to persist
     * changes to work items and process the side effects.
     *
     * You should only throw here for errors the user can do something
     * about, in this case failing to assign a business process.
     *
     * Work items that have been damanged should not throw, let the flow
     * continue on to handleWorkItem so they can be cleaned up.
     */
    public void validateWorkItem(SailPointContext con, WorkItem item)
        throws GeneralException {

        WorkItem.State state = item.getState();

        // comments only necessary on reject, the UI bean has historically
        // use Return to mean Reject so consider them the same 

        if (state == WorkItem.State.Rejected || state == WorkItem.State.Returned) {
            String comments = item.getCompletionComments();

            if (comments == null || comments.trim().length() == 0)
                throw new GeneralException(new Message(Message.Type.Error,
                        MessageKeys.SIGNOFF_COMMENTS_REQ));
        }
    }

    /**
     * @exclude
     * Post-process the modification of the work item.
     * 
     * If the item is not marked finished then we let it live.
     *
     * We don't care about the difference between approve and reject,
     * the completion comments are simply copied into the TaskResult.
     */
    public void handleWorkItem(SailPointContext con, WorkItem item,
                               boolean foreground) 
        throws GeneralException {

        _context = con;

        WorkItem.State state = item.getState();
        WorkItem.Type type = item.getType();
        String resultId = item.getTargetId();

        if (type != WorkItem.Type.Signoff) {
            // invalid handler, just delete when done
            if (log.isErrorEnabled())
                log.error("Unsupported work item type " + type.toString());
        }
        else {
            TaskResult result = _context.getObjectById(TaskResult.class, resultId);
            if (result == null) {
                // someone must have deleted the result out from under us,
                // silently remove the WorkItem
                log.warn("Signoff result disappeared!");
                deleteWorkItem(item);
            }
            else if (state != null) {
                // According to the original requirement, rejects require
                // a comment, but approvals do not.  Expirations will be treated
                // as a reject, but we'll supply a comment.  

                String comments = item.getCompletionComments();

                // should have caught this during validation, if not then
                // reopen the item
                if (comments == null && 
                    (state == WorkItem.State.Rejected ||
                     state == WorkItem.State.Returned)) {
                    log.warn("Reopening signoff item with no completion comments!");
                    item.setState(null);
                    // !! do we need to reset the expiration state?
                    _context.saveObject(item);
                    _context.commitTransaction();
                }
                else {
                    Signoff signoff = result.getSignoff();
                    if (signoff == null) {
                        log.error("Lost task signoff state");
                        signoff = new Signoff();
                        result.setSignoff(signoff);
                    }

                    // if this wasn't handled normally add completion
                    // comments that will convey to the Signoff
                    // I18N!
                    if (comments == null) {
                        comments = "Signoff work item expired";
                        item.setCompletionComments(comments);
                    }

                    signoff.finish(item);

                    // decrement the pending count
                    int pending = result.getPendingSignoffs();
                    if (pending > 0)
                        result.setPendingSignoffs(pending - 1);
                    else 
                        log.error("Pending signoff underflow!");

                    // copy over the eSignature
                    // !! major issue if there are multiple signoff
                    // work items, we only store the last esig
                    //This will need to be refactored when we work on TaskResult signatures
                    //result.setElectronicSignature(item.getElectronicSignature());
                    // todo: may need a top-level esig date for the list page?


                    _context.saveObject(result);
                    // TODO: If we ever add a reference from the TaskResult
                    // to the WorkItem will need to clear it here
                    // this will do the commit
                    deleteWorkItem(item);

                    // In theory, if people were deleting
                    // Work items out from under us the count could be off.
                    // We could try to catch that here, but it would
                    // be more reliable to have the housekeeping task
                    // scan TaskResults to detect this.
                    boolean reQueryPending = false;
                    if (reQueryPending) {
                        QueryOptions ops = new QueryOptions();
                        ops.add(Filter.eq("targetId", result.getId()));
                        pending = _context.countObjects(WorkItem.class, ops);
                        if (pending != result.getPendingSignoffs()) {
                            result.setPendingSignoffs(pending);
                            _context.saveObject(result);
                            _context.commitTransaction();
                        }
                    }
                }
            }
        }
    }

    /**
     * Delete a signoff work item no longer needed.
     */
    private void deleteWorkItem(WorkItem item) throws GeneralException {

        try {
            Workflower wf = new Workflower(_context);
            wf.archiveIfNecessary(item);

            _context.removeObject(item);
            _context.commitTransaction();
        }
        catch (GeneralException e) {
            // let this try, since this is an odd case and
            // is more expensive don't use Terminator unless
            // we have to
            Terminator ahnold = new Terminator(_context);
            ahnold.deleteObject(item);
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Partition Balancing
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Calculate the minimum number of partitions for a given request type
     * that will use all available threads on all active request hosts.
     * 
     * Formerly in AbstractTaskExecutor but moved here so it can be used 
     * outside of TaskExecutors.  
     *
     * The returnServers list is a return list of active server objects.
     * This is then passed to Connector.getIteratorPartitions for some
     * obscure reason related to multi-forest Active Directory.
     * It appears to be making assumptions about server thread limits
     * that are probably wrong.
     */
    public int getSuggestedPartitionCount(String requestDefinitionName, List<Server> returnServers)
        throws GeneralException {

        int suggestedPartitionCount = 0;
        RequestDefinition requestDef = _context.getObjectByName(RequestDefinition.class, requestDefinitionName);

        int maxThreads = requestDef.getInt("maxThreads");
        maxThreads = (maxThreads <= 0) ? 1 : maxThreads;

        /*
         * Bug 24755 - the RequestService will always be running under normal circumstances. We need to know if it is
         * running in restricted mode, in which case its threads are not counted in calculating a suggested partition
         * count. So, we get the service definition for the RequestService and strengthen the test below to include a
         * call to reqSvcDef.isHostAllowed(server.getName()).
         */
        ServiceDefinition reqSvcDef = _context.getObjectByName(ServiceDefinition.class, RequestService.NAME);

        QueryOptions queryOptions = new QueryOptions();
        queryOptions.add(Filter.eq("inactive", false));
        List<Server> servers = _context.getObjects(Server.class, queryOptions);
        
        if (servers != null) {

            if (returnServers != null) {
                returnServers.addAll(servers);
            }
            
            Map<String,String> serverDebugInfoMap = new HashMap<String,String>();

            for (Server server : servers) {

               String debugInfo = "unknown";

                // Only consider servers which have request service started, and are allowed
                // to process requests with the Request service

                if (reqSvcDef != null && server.getBoolean(Server.ATT_REQUEST_SERVICE_STARTED)) {
                    String serverName = server.getName();
                    boolean viableServer = ServicerUtil.isServiceAllowedOnServer(_context, reqSvcDef, serverName);

                    if (viableServer) {
                        // server may provide a governor on request threads
                        int actualMax = maxThreads;
                        int maxServerThreads = server.getInt(Server.ARG_MAX_REQUEST_THREADS);
                        if (maxServerThreads > 0 && maxServerThreads < maxThreads)
                            actualMax = maxServerThreads;

                        suggestedPartitionCount += actualMax;

                        debugInfo = "can supply " + actualMax + " request thread(s)";
                    }
                    else {
                        debugInfo = "cannot process requests";
                    }
                }
                else {
                    debugInfo = "is not available";
                }

                if (log.isDebugEnabled()) {
                    serverDebugInfoMap.put(server.getName(), debugInfo);
                }

            }

            if (log.isDebugEnabled()) {
                log.debug("Suggested partition count: " + suggestedPartitionCount);
                for (String serverName: serverDebugInfoMap.keySet()) {
                    log.debug("Server " + serverName + " : " + serverDebugInfoMap.get(serverName));
                }
            }

        }

        return suggestedPartitionCount;
    }

    /**
     * Simplified interface for the times you don't need the goofy return servers list.
     */
    public int getSuggestedPartitionCount(String requestDefinitionName)
        throws GeneralException {

        return getSuggestedPartitionCount(requestDefinitionName, null);
    }
    
    
}
