/* (c) Copyright 2017 SailPoint Technologies, Inc., All Rights Reserved. */
/*
 * A special task executor that creates a Request to run a task on
 * a specific host.
 *
 * Author: Jeff
 *
 * This does not have a corresponding TaskDefinition, it is created
 * by the TaskManager as an alternative executor for tasks that
 * have a host assignment.  
 * 
 * A Reuqest is created and given the flattened task arguments
 * and assigned to a host.  There is no run date so it will be
 * picked up on the next RequestProcessor cycle.  We wait for this
 * to happen to detect misconfigured host names, or hosts that are 
 * not active.
 *
 * Control of the TaskResult is passed to the TaskExecuteExecutor
 * using a special option that prevents TaskManager from marking
 * it complete when we finish.
 */

package sailpoint.task;

import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.ObjectUtil;
import sailpoint.api.RequestManager;
import sailpoint.api.SailPointContext;
import sailpoint.object.Attributes;
import sailpoint.object.Request;
import sailpoint.object.RequestDefinition;
import sailpoint.object.Server;
import sailpoint.object.TaskDefinition;
import sailpoint.object.TaskResult;
import sailpoint.object.TaskSchedule;
import sailpoint.request.TaskExecuteExecutor;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;

public class RestrictedTaskLauncher extends AbstractTaskExecutor {

	private static Log log = LogFactory.getLog(RestrictedTaskLauncher.class);
    
    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    SailPointContext _context;
    TaskResult _result;
    Request _request;
    boolean _terminate;

    //////////////////////////////////////////////////////////////////////
    //
    // TaskExecutor Interface
    //
    //////////////////////////////////////////////////////////////////////

    public RestrictedTaskLauncher() {
    }

    public boolean terminate() {
        _terminate = true;
        return true;
    }
    
    /**
     * Schedule a request to run a task and wait for it to be picked up
     * by the request processor.  Validate that the requested host is
     * up by checking the server heartbeat.
     */
    public void execute(SailPointContext context, 
                        TaskSchedule sched, TaskResult result,
                        Attributes<String,Object> args)
        throws Exception {

        _context = context;
        _result = result;

        // host list can come in from either the TaskSchedule or
        // the TaskDefinition, if both prefer TaskSchedule
        // Hostspec can be the empty string due to the JobDataMap
        // transformation, but this should have been caught
        // by now in TaskManager

        String hostspec = Util.trimnull(sched.getHost());
        if (hostspec == null) {
            TaskDefinition def = result.getDefinition();
            hostspec = Util.trimnull(def.getHost());
            if (hostspec == null) {
                throw new GeneralException("Missing host list");
            }
        }
        
        if (log.isInfoEnabled()) {
            log.info("Parsing host list: " + hostspec);
        }

        // TODO: Could try to support regular expressions
        // favor hosts that have a lower cpu usage percentage.
        // the value we store in the server is a double multipled by 100,
        // so 100 is the highest possible value
        float cpuLoad = 101.0f;
        String host = null;
        
        List<String> hosts = Util.csvToList(hostspec);
        int found = 0;
        for (String name : hosts) {
            Server server = _context.getObjectByName(Server.class, name);
            if (server != null) {
                found++;
                if (!server.isInactive()) {
                    float load = 0.0f;
                    // supposed to always be a string
                    Object o = server.get(Server.ATT_CPU_USAGE);
                    if (o != null) {
                        load = Util.atof(o.toString());
                    }
                    if (load < cpuLoad) {
                        host = name;
                        cpuLoad = load;
                    }
                }
                else {
                    if (log.isInfoEnabled()) {
                        log.info("Candidate server was inactive: " + host);
                    }
                }
            }
            else {
                // it could be that the server hasn't been started yet, but
                // after initial deployment a Server object normally exists
                if (log.isInfoEnabled()) {
                    log.info("No Server object for host: " + host);
                }
            }
            
        }
        
        if (host == null) {
            // technically we could have a different message for
            // each server, some may not exist, some may be inactive
            String msg;
            if (found == 0) {
                msg = "Servers not found: " + hostspec;
            }
            else {
                msg = "Servers not active: " + hostspec;
            }

            addError(msg, null);
        }
        else {
            _request = scheduleRequest(result, args, host);
            if (waitForIt()) {
                // here if we successfully started the Request, set
                // this transient flag so TaskManager knows that ownership has
                // been transfered
                result.setTransferred(true);
            }
        }
    }

    /**
     * Create a Request to run the task.
     * Pass the already flattened task arguments are Request arguments.
     */
    private Request scheduleRequest(TaskResult result, Attributes<String,Object> args, String host)
        throws GeneralException {

        String reqname = TaskExecuteExecutor.REQUEST_DEFINITION;
        RequestDefinition def = _context.getObjectByName(RequestDefinition.class, reqname);
        if (def == null)
            throw new GeneralException("Missing RequestDefinition: " + reqname);

        // be sure to take the host out so we don't get into a recursive loop
        args.remove(TaskSchedule.ARG_HOST);

        // When we commit the transaction in the call to rm.addRequest below,
        // the Executor of the task has completed its job, and life is intended to carry on in the
        // the new Request to be run on another host
        result.setLive(false);

        Request req = new Request();
        req.setDefinition(def);
        req.setAttributes(args);
        req.setHost(host);
        req.setAttribute(TaskExecuteExecutor.ARG_TASK_RESULT, result.getName());
        
        RequestManager rm = new RequestManager(_context);
        rm.addRequest(req);

        if (log.isInfoEnabled()) {
            log.info("Scheduled request: " + req.getId());
        }

        return req;
    }

    /**
     * Wait for the Request to be picked up by the RequestProcessor.
     * This is done to ensure that the RequestProcessor is
     * actually running on that host.  If not, the Request would just
     * sit there and it look like the task was hung.
     *
     * While we wait, we also have to respond to termination requests
     * from the UI.
     *
     * If the Request executes successfully return true to indiciate that
     * ownership of the TaskResult has been taked by the RequestExecutor
     * and that the TaskManager should leave it alone.
     *
     * I'm not seeing much need to configure the wait parameters.  5 minutes
     * should be more than enough for an RP to pick up the request.  Polling
     * interval should be high enough that we don't hit the DB too often, 
     * but short enough that it is responsive for demos, this one may make
     * more sense to have configurable.  
     *
     * Termination can fail if the RP sneaks in and starts running it before
     * we can get a transaction lock, or less likely if it runs to completion.
     * In both cases we have to assume that ownersip of the TaskResult has
     * transferred to the request.
     */
    private boolean waitForIt() throws GeneralException {
        
        boolean success = false;

        // TODO: localize
        String msg = "Waiting for scheduled request to be started";
        _result.addMessage(msg);
        _context.saveObject(_result);
        _context.commitTransaction();

        int maxWait = 60 * 5;
        int pollInterval = 5;
        int totalWait = 0;
        
        while (!_terminate) {

            if (log.isInfoEnabled()) {
                log.info("Sleeping...");
            }
            Util.sleep(pollInterval * 1000);

            if (_terminate) {
                if (log.isInfoEnabled()) {
                    log.info("Termination requested during sleep");
                }
                if (!terminateRequest()) {
                    // termination failed, have to assume TaskResult
                    // ownership transfers
                    success = true;
                }
            }
            else if (isRequestRunning()) {
                if (log.isInfoEnabled()) {
                    log.info("Request is now running");
                }
                success = true;
                break;
            }
            else {
                totalWait += pollInterval;
                if (totalWait >= maxWait) {
                    // TODO: localize
                    msg = "Timeout waiting for request on host: " + _request.getHost();
                    log.error(msg);
                    addError(msg, null);
                    if (!terminateRequest()) {
                        // termination failed, TaskResult ownership
                        // must still be transferred
                        success = true;
                    }
                    break;
                }
            }
        }

        return success;
    }
    
    /**
     * Check to see of the request we scheduled has been started.
     */
    private boolean isRequestRunning() {

        boolean running = false;

        try {
            // must decache to see deleted requests
            _context.decache();
            Request req = _context.getObjectById(Request.class, _request.getId());
            if (req == null) {
                // in theory it could be been run and finished almost immediately
                // go ahead and terminate
                running = true;
            }
            else if (req.getLaunched() != null) {
                // normal execution
                running = true;
            }
        }
        catch (Throwable t) {
            // if we couldn't fetch the request something major is wrong
            // log it and wait for the next cycle
            log.error("Unable to check request status");
            log.error(t);
        }
        return running;
    }
    
    /**
     * Delete the Request object before it has started running.
     * Can be called in two contexts: when we get a termination
     * request from the UI, and after waiting the maximum time
     * for the Request Processor to pick up the Request.
     *
     * In both cases it is possible for the RP to have started
     * the request before we get around to deleting it.  This
     * is more likely for UI termination since we will just have
     * fallen out of a sleep.  We could schedule a termination
     * request, but the window for this is so small, just ignore
     * it and make them terminate again.
     *
     * Return true if we succefully deleted the request.  If we
     * couldn't then either it finished or it is still running.
     * In both cases we have to assume that ownership of the
     * TaskResult has transferred.
     */
    private boolean terminateRequest() {

        boolean success = false;

        if (log.isInfoEnabled()) {
            log.info("Terminating request");
        }

        try {
            Request req = ObjectUtil.transactionLock(_context, Request.class, _request.getId());
            if (req != null) {
                try {
                    if (req.getLaunched() != null) {
                        // it got picked up before we locked it,
                        // we could schedule a termination request, but it's such
                        // a small window, just ignore and make them click again
                        log.warn("Unable to terminate directed task, Request already started");
                    }
                    else {
                        _context.removeObject(req);
                        _context.commitTransaction();
                        success = true;
                        if (log.isInfoEnabled()) {
                            log.info("Request deleted");
                        }
                    }
                }
                finally {
                    // besure the lock is released if anything went wrong
                    _context.rollbackTransaction();
                }
            }
            else {
                if (log.isInfoEnabled()) {
                    log.info("Request was already deleted");
                }
            }
        }
        catch (Throwable t) {
            // problem locking or removing the object, unusual
            // make sure this is in the TaskResult but go ahead and terminate
            // the launcher normally
            addError("Exception trying to terminate request", t);
        }

        return success;
    }

    /**
     * Add an error to the TaskResult and persist it.
     */
    private void addError(String key, Throwable t) {

        Message msg;
        if (t != null)
            msg = new Message(Message.Type.Error, key, t);
        else
            msg = new Message(Message.Type.Error, key);
            
        try {
            _result.addMessage(msg);
            _context.saveObject(_result);
            _context.commitTransaction();
        }
        catch (Throwable t2) {
            // if we couldn't even update the task reslt, just
            // log and move on
            log.error(msg);
            log.error(t);
            log.error(t2);
        }
    }
    
}

