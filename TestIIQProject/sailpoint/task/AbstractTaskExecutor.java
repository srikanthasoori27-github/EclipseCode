/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
/*
 * An implementation of TaskExecutor that provides a set of
 * commopn utilities for executor classes.  You don't have to 
 * subclass this to be a task.  Maybe this should be part
 * of the TaskManager utility class?
 *
 * Author: Jeff
 * 
 * UPDATE: Most of the stuff has been moved to ObjectUtil since
 * we're usually implementing tasks with one or more internal
 * business logic classes that don't extend this class.
 */

package sailpoint.task;

import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.Meter;
import sailpoint.api.RequestManager;
import sailpoint.api.SailPointContext;
import sailpoint.api.TaskManager;
import sailpoint.object.Request;
import sailpoint.object.Server;
import sailpoint.object.TaskExecutor;
import sailpoint.object.TaskItemDefinition;
import sailpoint.object.TaskResult;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;

/**
 * An implementation of TaskExecutor that provides a set of
 * common utilities for executor classes.
 */
public abstract class AbstractTaskExecutor implements TaskExecutor {

	private static Log log = LogFactory.getLog(AbstractTaskExecutor.class);

    //////////////////////////////////////////////////////////////////////
    //
    // Constants
    //
    //////////////////////////////////////////////////////////////////////

    // 
    // Common task arguments
    //

    /**
     * Optional argument to specify how Identity objects are locked
     * during iteration.  This is used in two contexts, while
     * iterating over the Identity table in a refresh task, and 
     * while correlating accounts during an aggregation task.
     * 
     * Normally this will not be set and, by default,
     * a transaction lock is acquired.  It can be set to "none" in case you need
     * to disable locking in an emergency (like it does not work).
     * It can also be set to "persistent" which will acquire persistent
     * locks instead of transaction locks, though this has much more 
     * overhead.
     */
    public static final String ARG_LOCK_MODE = "lockMode";

    /**
     * Argument specifying the maximum number of seconds to wait
     * to acquire a lock when lockMode is "persistent".
     * This is actually a retry count with an implicit one second
     * delay between retries so a timeout of 1 means two attempts
     * with a one second pause before the second attempt.
     */
    public static final String ARG_LOCK_TIMEOUT = "lockTimeout";

    /**
     * Default lock timeout.  When refreshing identities with lots
     * of links (one customer has over 3800 links on some identities)
     * this can take quite awhile to refresh.  30 seconds was too low,
     * not sure where the sweet spot is.
     */
    public static final int DEFAULT_LOCK_TIMEOUT = 60 * 5;

    /**
     * Optional filter string to restrict the identities that get refreshed.
     */
    public static final String ARG_FILTER = "filter";

    /**
     * Enables running trace messages to stdout. 
     */
    public static final String ARG_TRACE = "trace";

    /**
     * Enables profiling messages to stdout.
     */
    public static final String ARG_PROFILE = "profile";

    /**
     * Boolean option to enable task restart.
     */
    public static final String ARG_RESTARTABLE = "restartable";

    //
    // Common task return values
    //

    /**
     * Name of the TaskResult attribute in which the total number
     * of identities refreshed is returned.
     */
    public static final String RET_TOTAL = "total";

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Monitor object for tracking execution progress.
     */
    Monitor _monitor;

    //////////////////////////////////////////////////////////////////////
    //
    // TaskProgress
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Ask the executor to perform a command encapsulated in a Request.
     * This was added to support capturing task stack traces while they are
     * running, but it is general and may have other uses.
     * @ignore
     * Could do terminate() the same way, but we have too much history with
     * an explicit terminate() method.
     */
    public void processCommand(Request cmd) {
        log.warn("Received unsupported task command: " + cmd.getExecutorCommand());
    }

    /**
     * A monitor useda for periodic progress messages.
     */
    public Monitor getMonitor()  {
        return _monitor;
    }

    public void setMonitor(Monitor monitor) {
        _monitor = monitor;
    }

    /**
     * Update the current execution progress. This method requires
     * that the TaskDefinition's ProgressMode is either Percent or
     * String. This method will use the task definitions defined 
     * progressInterval to figure out how often the task result will
     * be modified and written to the database.
     */
    public void updateProgress(SailPointContext ctx, 
                               TaskResult result, 
                               String progressString)
        throws GeneralException {

        updateProgress(ctx, result, progressString, -1);
    }

    /**
     * Update progress and also report the percentage complete from 0 to 100.
     */
    public void updateProgress(SailPointContext ctx, 
                               TaskResult result, 
                               String progressString,
                               int percentComplete) 
        throws GeneralException {

        if ( _monitor == null ) 
            _monitor = new TaskMonitor(ctx, result);

        _monitor.updateProgress(progressString, percentComplete);
    }

    //////////////////////////////////////////////////////////////////////    
    //
    // Partitioned task requests
    //
    //////////////////////////////////////////////////////////////////////    

    /**
     * Schedule a set of requests that will all share the same task result.
     * This is intended to be called from TaskExecutors that want to 
     * convert themselves to a partitioned task using Request objects.
     * This is a new 6.2 feature used to achieve multi-machine parallelism.
     *
     * This method does not do anything that that the TaskExecutor could not
     * do but it is a set of steps that always need to be done so it was
     * factored it into a RequestManager utility method for sharing.
     *
     * The important thing is that once a Request object has been committed
     * to the database the TaskResult must be marked as "partitioned". If
     * for some reason any request cannot be launched, do not mark
     * the task result so the TaskManager can mark it completed.
     */
    public void launchPartitions(SailPointContext context, TaskResult result, 
                                 List<Request> requests)
        throws GeneralException {

        String meterName = getClass().getSimpleName() + ".launchPartitions";
        Meter.enterByName(meterName);
        
       // let the TaskManager know that this result is to be left alive
        int requestsCreated = 0;
        result.setPartitioned(true);
        
        //add partitioned requests to the parent task result
        //might be better to use result.addPartitionedRequest().
        for (Request req : requests) {
            result.getPartitionResult(req.getName());
        }
        context.saveObject(result);

        // !! race condition, if the Heartbeat cleaner wakes up before
        // we have a chance to commit Request objects it could think this
        // result is done.  But if we save Requst objectsd and they start running
        // before the TaskResult is marked partitioned, it might confuse something?
        // update: I believe this is untrue, I don't see why Heartbeat cleaner would
        // care as long as we're updating our heartbeat, and RequestManager.addRequest
        // is going to commit anyway
        //context.commitTransaction();

        try {
            for (Request req : requests) {

                // Request must point back to the TaskResult
                req.setTaskResult(result);

                // though not required it's a good idea to carry
                // over the task launcher
                req.setLauncher(result.getLauncher());

                // do we really need this?  I wish this wasn't an enum
                req.setType(TaskItemDefinition.Type.Partition);

                RequestManager.addRequest(context, req);
                requestsCreated++;
            }
        }
        catch (Throwable t) {
            log.error("Exception trying to launch partitioned requests!");
            log.error(t);
            if (requestsCreated == 0) {
                // If we were not able to create any requests, then terminate without
                // creating a shared result.  
                result.setPartitioned(false);
            }
            else {
                // At least one request is out there and may be running, have
                // to ask for an orderly shutdown.
                TaskManager tm = new TaskManager(context);
                tm.terminate(result);
            }
            
            // update the result to reflect the error.
            result.addMessage(Message.error(t.getLocalizedMessage()));
        }

        Meter.exitByName(meterName);
    }
    //////////////////////////////////////////////////////////////////////
    //
    // Partitioned task requests
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Save a Request and associate it with given task result.
     * This is intended to be used for saving requests as they are 
     * created during aggregator partitioning.
     * This is an extension to 6.2 multi-machine parallelism feature.
     */
    public void saveRequest(SailPointContext context, TaskResult result, 
                                   Request request)
        throws GeneralException {

        String meterName = getClass().getSimpleName() + ".saveRequest";
        Meter.enterByName(meterName);
        
       // let the TaskManager know that this result is to be left alive
        boolean requestsCreated = false;
        result.setPartitioned(true);
        
        //add partitioned requests to the parent task result
        //might be better to use result.addPartitionedRequest().

        result.getPartitionResult(request.getName());

        // !! race condition, if the Heartbeat cleaner wakes up before
        // we have a chance to commit Request objects it could think this
        // result is done.  But if we save Requst objectsd and they start running
        // before the TaskResult is marked partitioned, it might confuse something?
        //context.commitTransaction();

        try {
                // Request must point back to the TaskResult
                request.setTaskResult(result);

                // though not required it's a good idea to carry
                // over the task launcher
                request.setLauncher(result.getLauncher());

                // do we really need this?  I wish this wasn't an enum
                request.setType(TaskItemDefinition.Type.Partition);

                RequestManager.addRequest(context, request);
                requestsCreated = true;
        }
        catch (Throwable t) {
            log.error("Exception trying to launch partitioned requests!");
            log.error(t);
            if (requestsCreated == false) {
                // If we were not able to create any requests, then terminate without
                // creating a shared result.  
                result.setPartitioned(false);
            }
            else {
                // At least one request is out there and may be running, have
                // to ask for an orderly shutdown.
                TaskManager tm = new TaskManager(context);
                tm.terminate(result);
            }
        }
        Meter.exitByName(meterName);
    }

    /**
     * Calculates the suggested partition count based on the registered Server objects and their
     * request threads count.
     * This is just a wrapper for calling this function without the need for populating active servers
     * @param context The context.
     * @param includeInactive True if inactive servers should be included in the count.
     * @return The sum of the request threads of the configured servers.
     * @throws GeneralException
     */
    protected int getSuggestedPartitionCount(SailPointContext context, boolean includeInactive, String requestDefinitionName) 
        throws GeneralException {
        return getSuggestedPartitionCount(context, includeInactive, requestDefinitionName, null);
    }
    
    /**
     * Calculates the suggested partition count based on the registered Server objects and their
     * request threads count.
     *
     * @param context The context.
     * @param includeInactive True if inactive servers should be included in the count.
     * @param o_servers list where servers are to be populated
     * @return The sum of the request threads of the configured servers.
     * @throws GeneralException
     */
    protected int getSuggestedPartitionCount(SailPointContext context, boolean includeInactive, String requestDefinitionName, List<Server> o_servers) 
        throws GeneralException {

        // jsl - moved this to TaskManager so we could use it outside task executors
        // also dropped support for includeInactive since no one ever used it and it
        // isn't useful
        // maintaining support for the mysterious returned servers list which is only
        // used by multi-forest AD, and probably incorrectly

        TaskManager tm = new TaskManager(context);
        return tm.getSuggestedPartitionCount(requestDefinitionName, o_servers);
    }
}
