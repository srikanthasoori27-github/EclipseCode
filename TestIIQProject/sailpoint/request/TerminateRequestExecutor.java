/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
/*
 * Terminate a Quartz task or a RequestHandler thread running on this machine.
 * 
 * Author: Jeff
 * 
 * This was added to support termination of partitioned tasks that can
 * produce multiple request threads on different machines.  When you
 * terminate the task in the UI what happens is we create a termination
 * Request for each partition with the Request host set to the host
 * the partition thread is running on.  Evantually the RP for that
 * host will pick up the event and call here to terminate the thread.
 *
 * This conicidentally also solves the probelm of terminating Quartz
 * tasks since the free version of Quartz we use does not support
 * cluster-aware termination.
 *
 * Starting in 7.2, this is only used to terminate partitioned task
 * requests.  Quartz task termination is now performed by TaskCommandExecutor
 * and ExecutorTracker.  We could simplify this by rewriting the partition
 * terminator to use the new mechanism, but it's a bit compliccated and
 * I don't want to merge the two and retest right now.
 *
 */

package sailpoint.request;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.RequestManager;
import sailpoint.api.TaskManager;
import sailpoint.api.SailPointContext;
import sailpoint.object.Attributes;
import sailpoint.object.Request;
import sailpoint.object.TaskResult;
import sailpoint.tools.Util;

/**
 * A RequestExecutor that terminates Quartz tasks and RequestHandler
 * threads running on this machine.
 */
public class TerminateRequestExecutor extends AbstractRequestExecutor {

    private static final Log log = LogFactory.getLog(TerminateRequestExecutor.class);

    /**
     * The database id of the TaskResult we're trying to terminate.
     * For partitioned tasks we will terminate all partition threads
     * within this result that run on this machine.  There is no mechanism
     * currently to pass a specific partition thread to terminate but
     * we can consider that.
     */
    public static final String ARG_TASK_RESULT_ID = "taskResultId";


    public TerminateRequestExecutor() {
        super();
    }

    public void execute(SailPointContext context, 
                        Request request,
                        Attributes<String, Object> args)
        throws RequestPermanentException, RequestTemporaryException {

        
        try {
            String id = args.getString(ARG_TASK_RESULT_ID);
            if (id == null)
                log.error("Missing task result id");
            else {
                TaskResult result = context.getObjectById(TaskResult.class, id);
                if (result == null) {
                    // in theory this could happen if something deleted it 
                    // before the RP cycle ran
                    log.error("Invalid task result id: " + id);
                }
                else if (!result.isPartitioned()) {
                    // a Quartz task
                    if (log.isInfoEnabled()) {
                        log.info("Terminating Quartz task with result id: " + id + 
                                 ":" + result.getName());
                    }
                    String resultHost = result.getHost();
                    String thisHost = Util.getHostName();
                    if (!thisHost.equals(resultHost))
                        log.error("Processing request for the wrong host: " + resultHost);
                    // I guess we can try even if the host is wrong, it will be ignored
                    TaskManager tm = new TaskManager(context);
                    boolean terminated = tm.terminate(result);
                    if (terminated)
                        log.info("Termination request sent");
                    else
                        log.info("The task is no longer running");
                }
                else {
                    // it's a partitioned task running as one or more requests
                    // this will terminate every partition thread on this machine,
                    // don't have an interface yet to terminate a specific thread
                    // but we're not seeing the need for that
                    RequestManager rm = new RequestManager(context);
                    rm.terminatePartitionThreads(result);
                }
            }
        }
        catch (Throwable t) {
            // nothing is retryable
            log.error(t);
            throw new RequestPermanentException();
        }
    }

}
