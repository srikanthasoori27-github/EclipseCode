/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
/*
 * A thread that processes one Request.
 * A list of these is maintained by a ThreadPoll object, and we have one
 * pool for each RequestDefinition.
 *
 * Author: Jeff
 *
 * The thread is responsible for calling the RequestExecutor then it MUST call
 * back to RequestProcessor.finish when it is done.  It must not throw
 * or otherwise terminate without calling back to RequestProcessor.
 *
 * The Request object passed to the constructor will be refeteched so that 
 * it is attached to this thread's SailPointContext.  This Request may
 * be modified to contain results and error messages but is not necessarily
 * committed.  When we call back to RequestProcessor.finish, it will 
 * call getRequest() to get the final Request, mark it completed, and
 * either save or delete it.
 *
 * While this thread is running the Request is owned by this thread and
 * cannot be modified outside of this thread.  Any changes will be lost.
 */

package sailpoint.request;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.SailPointContext;
import sailpoint.api.SailPointFactory;
import sailpoint.object.Attributes;
import sailpoint.object.LockInfo;
import sailpoint.object.Request;
import sailpoint.object.RequestDefinition;
import sailpoint.object.RequestExecutor;
import sailpoint.object.TaskResult;
import sailpoint.object.TaskResult.CompletionStatus;
import sailpoint.server.DynamicLoader;
import sailpoint.server.ExecutorTracker;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;

public class RequestHandler extends Thread {

    private static Log log = LogFactory.getLog(RequestHandler.class);

    /**
     * Pointer back to the RequestProcessor we notify when we're done.
     */
    RequestProcessor _requestProcessor;

    /**
     * The ThreadPool that we're in.
     */
    RequestProcessor.ThreadPool _pool;

    /**
     * The Request we're processing.  Once we get into the thread we can assume
     * this has been locked.
     */
    Request _request;

    /**
     * The TaskResult associated with the _request.  This is loaded when the thread
     * starts and is used to support termination of all threads with a particular 
     * TaskResult.  Saves having to fetch it during termination.
     */
    String _resultId;

    /**
     * Executor we're running.
     */
    RequestExecutor _executor;

    /**
     * True if we received a termination request.
     */
    boolean _terminated;

    /**
     * Build a new request processor thread to handle one Request.
     */
    public RequestHandler(RequestProcessor rp, RequestProcessor.ThreadPool pool, 
                          Request request) {
        setDaemon(true);
        _requestProcessor = rp;
        _pool = pool;
        _request = request;
    }

    public Request getRequest() {
        return _request;
    }

    public String getResultId() {
        return _resultId;
    }

    /**
     * Run method.
     */
    public void run() {

        SailPointContext context = null;
        CompletionStatus status = CompletionStatus.Success;

        try {
            RequestProcessor.logInfo(_request, "Starting thread");

            // note that we need a name for auditing
            // might want to name these from the RequestDefinition.name for more context?
            context = SailPointFactory.createContext("RequestHandler");

            // Refetch the object in this context.  We might be able to avoid this but
            // since we need to get to the RequestDefinition and the shared TaskResult
            // it's safer if we start over.  The RequestExecutor will expect this
            // to be in its session.
            _request = context.getObjectById(Request.class, _request.getId());

            // while we have it in the cache, fetch the TaskResult to support terminate()
            TaskResult result = _request.getTaskResult();
            if (result != null)
                _resultId = result.getId();

            RequestDefinition def = _request.getDefinition();
            if (def == null)
                throw new GeneralException(new Message(Message.Type.Error,
                                                       MessageKeys.REQ_HAS_NO_DEF, _request.getName()));


            // Add lock context, start with the RequestDefinition name.
            // If there is a TaskResult, this would be a partition name
            // which may help identify the phase.
            // Don't bother trying to remove this, just let the thread end.
            String lockContext = def.getName();
            if (result != null) {
                lockContext += "/" + result.getName();
            }
            LockInfo.setThreadLockContext(lockContext);

            String className = def.getEffectiveExecutor();
            if (className == null)
                throw new GeneralException("No executor class for request: " + def.getName());

            _executor = (RequestExecutor)DynamicLoader.instantiate(context, className);

            // merge arguments from the RequestDefinition and the Request
            // jsl - why are we doing this now?  It seems safer to do it
            // when the request was scheduled?  Tasks do it this way too.
            Attributes<String,Object> args = flattenArguments(_request);

            try {
                ExecutorTracker.addExecutor(_request, _executor);

                // indicate that the request is actually executing now, not just queued for running.
                // The Reanimator will be paying careful attention to this request now.
                _request.setLive(true);
                context.saveObject(_request);
                context.commitTransaction();

                _executor.execute(context, _request, args);
            }
            finally {
                try {
                    // indicate that the request is not running.  The Reanimator
                    // will no longer try to revive this request.
                    _request.setLive(false);
                    context.saveObject(_request);
                    context.commitTransaction();
                }
                finally {
                    ExecutorTracker.removeExecutor(_request);
                }
            }

            RequestProcessor.logInfo(_request, "Finished request");

            // if we terminated gracefully change the ExecStatus
            if (_terminated)
                status = CompletionStatus.Terminated;
        } 
        catch (Throwable t) {
            // What if we threw after a terminate request?
            // Should the status be FAILURE or TERMINATED?
            // Since we're supposed to shut down cleanly, 
            // let it be FAILURE.
            status = CompletionStatus.Error;
            String stack = Util.stackToString(t);

            if (t instanceof RequestTemporaryException) {
                // old convention to conventy retyable errors
                // if we had a termination request, do not
                // let this enter retry, treat as permanent
                if (!_terminated)
                    status = CompletionStatus.TempError;

                // these are expected, lower the log level
                if (log.isInfoEnabled()) {
                    log.info(t);
                }
            }
            else if (t instanceof RequestPermanentException) {
                // it is still ours, warn
                if (log.isWarnEnabled()) {
                    // stackToString already has the Throwable name
                    //log.warn(t);
                    log.warn(stack);
                }
            } 
            else {
                // not ours, it is error
                log.error(stack);
            }
            
            // jsl - after simplifying all the exception handling logic from 
            // pre 6.2, this is what we're left with.  Is it really necessary
            // to have two styles of message?
            if (t instanceof GeneralException)
                _request.addMessage(((GeneralException)t).getMessageInstance());
            else 
                _request.addMessage(new Message(Message.Type.Error, MessageKeys.ERR_EXCEPTION, t));

            // saving exception stack in the request for later
            // transfer to the TaskResult if there is one
            // useful in debugging
            _request.setStack(stack);
        }
        finally {
            // this is not allowed to throw
            _requestProcessor.finish(context, _pool, this, _request, status);
        }
    }

    /**
     * Build a flattened Map of arguments first comming from the RequestDefinition
     * then overlayed with the args passed in the Request.
     */
    private Attributes<String,Object> flattenArguments(Request request) {

        Attributes<String,Object> flatArgs = new Attributes<String,Object>();

        // start with definition arguments
        RequestDefinition def = request.getDefinition();
        if (def != null)
            flatArgs.putAll(def.getEffectiveArguments());

        // then override with the values from the Request
        // jsl - isn't putAll the same thing?
        Attributes<String,Object> reqArgs = request.getAttributes();
        if (reqArgs != null)
            flatArgs.putAll(reqArgs);

        return flatArgs;
    }

    /**
     * Called asynchronously by RequestProcessor to request the termation of the request.
     */
    public boolean terminate() {
        _terminated = true;
        return _executor.terminate();
    }

}
