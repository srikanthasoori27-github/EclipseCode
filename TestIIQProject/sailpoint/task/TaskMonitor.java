/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
/*
 * A class to help task executors write task progress to the TaskResult
 * object. The main purpose of this class is to keep track of the 
 * last time the progress was written and assure that we only write 
 * the result on the interval defined on the TaskDefinition.
 *
 * Author: Dan 
 *
 * TODO?: We might want to always display the last time the result was
 * written which will require us to push the last update field onto
 * the TaskResult object.
 * 
 * jsl - For 3.2 synchronizing calls to updateProgress so we can
 * use the same monitor with multiple "worker" threads.
 * 
 * Simple Result vs Partitioned Results
 *
 * In 6.2 we added the concept of "partitioned" tasks and results.
 * A partitioned task creates multiple Request objects all sharing the same
 * TaskResult.  This is called a partitioned TaskResult.  The important 
 * characteristic of a partitioned result is that it may be concurrently 
 * accessed by more than one JVM. A partitioned task result contains
 * a List of TaskResult objects inside it, one for each parittion.  
 * A given JVM thread will only be updating one of the partition results
 * so they do not need to be thread safe.  However since multiple JVMs
 * can be updating the same parent TaskResult at the same time we have
 * to use locking to sequence the updates.  This is done with transaction
 * (row) locks since the update duration is short.
 *
 * Simple results don't have these inner results, but they do need
 * to be thread safe.  When we added the ability for a single identity 
 * refresh task to launch multiple worker threads, each thread share
 * the same TaskMonitor and may try to update progress at the same time.
 * This is actually bad, since the displayed progress will be taken
 * from one of the worker threads at random, there isn't a way to see
 * the overall progress of all threads.  Now that we have the conetpt
 * of a partitioned result, it would be better if we redesigned the
 * identity refresh task to use them so each worker thread would have
 * it's own private TaskResult within the parent result.
 *
 * UDPATE 7.3
 *
 * The prior comments are largely incorrect now that we distribute partition
 * results.  Each request will have its own private TaskResult that can
 * be updated without locking.  This dramatically reduces contention on the
 * root result when updating progress.  There is a flag allowing the selection
 * of the old behavior but it is not used and should be removed. - jsl
 *
 */

package sailpoint.task;

import java.util.Date;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.ObjectUtil;
import sailpoint.api.SailPointContext;
import sailpoint.object.Filter;
import sailpoint.object.QueryOptions;
import sailpoint.object.Request;
import sailpoint.object.RequestDefinition;
import sailpoint.object.RequestState;
import sailpoint.object.TaskDefinition;
import sailpoint.object.TaskResult;
import sailpoint.object.TaskItemDefinition.ProgressMode;
import sailpoint.request.AbstractRequestExecutor;
import sailpoint.server.DynamicLoader;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

public class TaskMonitor implements Monitor {

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    private static Log log = LogFactory.getLog(TaskMonitor.class);

    /**
     * Context that will be used to commit the updates to the TaskResults 
     * progress and percentage complete.
     */
    private SailPointContext _context;

    /**
     * The result object where the Taskprogress will be stored.
     */
    private TaskResult _result;

    /**
     * The Request object referencing the TaskREsult, when this
     * is a shared partitioned task result.
     */
    Request _request;

    /**
     * The RequestExecutor used to execute the Request. This may be null in the case of Quartz Tasks
     */
    AbstractRequestExecutor _requestExecutor;

    /**
     * State of the current task
     */
    Object _stateMap;

    /**
     * # of iterations before committing request state
     */
    private int _stateUpdateInterval;

    /**
     * # of times state has been updated
     */
    private int _stateUpdateCount;

    /**
     * Date of last time we updated progress.  
     * NOTE: if we want to show this in the ui we need to push
     * this up to the TaskResult object.
     */
    private Date _lastUpdate;

    private ProgressMode _mode;

    private long _updateInterval;

    //////////////////////////////////////////////////////////////////////
    //
    // Constructor/Properties
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Initialize a task monitor for a normal task result.
     */
    public TaskMonitor(SailPointContext ctx, TaskResult result) {
        init(ctx, result);
    }

    /**
     * Initialize a task monitor for a partitioned request that 
     * references a shared task result.
     */
    public TaskMonitor(SailPointContext ctx, Request request) {
        _request = request;
        init(ctx, request.getTaskResult());
    }

    /**
     * Initialize a task monitor for a partitioned request that
     * references a shared task result, and specify the instance of
     * the requestExecutor.
     * @param ctx persistence context
     * @param request the partitioned Request object
     * @param requestExecutor the AbstractRequestExecutor running the request
     */
    public TaskMonitor(SailPointContext ctx, Request request, AbstractRequestExecutor requestExecutor) {
        this(ctx, request);
        _requestExecutor = requestExecutor;
    }

    private void init(SailPointContext ctx, TaskResult result) {

        _context = ctx;
        _result = result;
        _updateInterval = 500;
        
        // TODO: If we're here from a Request, should we allow Request arguments
        // to override this?  Probably not since we're still pointing back to the
        // original TaskDefinition.
        TaskDefinition def = result.getDefinition();
        if (def != null) {
            _mode = def.getEffectiveProgressMode();
            int interval = def.getEffectiveProgressInterval();
            if (interval > 0) {
                _updateInterval = interval;
            }
            int stateUpdateInterval = def.getEffectiveStateUpdateInterval();
            if (stateUpdateInterval > 0) {
                _stateUpdateInterval = stateUpdateInterval;
            }
        }
    }

    /**
     * Reattach the TaskResult to this monitor.  This can be called if the
     * previous TaskResult is no longer valid if it has been decached.
     * @param result
     */
    public void reattach(TaskResult result) {
        _result = result;
    }
    
    public TaskResult getTaskResult() {
        return _result;
    }
    
    public boolean isPartitioned() {
        return (_request != null);
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Progress
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Update the progress string of a task.
     */
    public void updateProgress( String progressString) {

        updateProgress(progressString, -1, false);
    }

    public void forceProgress(String progressString) {

        updateProgress(progressString, -1, true);
    }

    /**
     * Update the progress string and percent compelte of a task.
     */
    public void updateProgress( String progressString,
                                int percentComplete) {

        updateProgress(progressString, percentComplete, false);
    }

    /**
     * Update the progress string and percent compelte of a task.
     * This method will save and commit the TaskResult if the
     * progressInterval has been exceeded.
     */
    public void updateProgress(String progressString, int percentComplete,
                               boolean forceUpdate) {

        try {
            // check to see if we should update progress
            if (_mode != ProgressMode.Percentage &&
                _mode != ProgressMode.String)
                return;

            // Writing the result is expensive, only update if its been longer 
            // then the specifed progress interval.

            Date now = new Date();
            long lastUpdateDiff = -1;
            if ( _lastUpdate != null )
                lastUpdateDiff = Util.computeDifferenceMilli(_lastUpdate, now);

            if ( ( _lastUpdate == null ) || 
                 ( lastUpdateDiff > _updateInterval ) ||
                 ( forceUpdate ) ) {

                if (_request == null) {
                    // this is a simple request, we have to synchronize since multiple
                    // refresh threads can be hitting the same result
                    synchronized (this) {
                        updateProgress(_result, progressString, percentComplete);
                        _context.saveObject(_result);
                        _context.commitTransaction();
                    }    
                }
                else {
                    // this is a shared result, we don't have to be thread safe, but we
                    // do have to acquire a transaction lock and refetch the result
                    TaskResult result = lockPartitionResult();
                    try {
                        // TODO: should also try to aggregate overall completion status
                        // in the parent result
                        updateProgress(result, progressString, percentComplete);
                    }
                    finally {
                        commitPartitionResult();
                    }
                }

                _lastUpdate = now;
            }
        }
        catch(Throwable e) {
            log.warn("Error updating task progress. " + e.toString());
        }    
    }

    /**
     * Inner logic to update the result progress.
     */
    public void updateProgress(TaskResult result, String progressString, int percentComplete) {

        if ( progressString != null ) {
            String progress = truncateIfNecessary(progressString);
            result.setProgress(progress);
        }

        // -1 indicates that we aren't updating the percentage complete
        if ( percentComplete != -1 ) {
            result.setPercentComplete(percentComplete);
        }
    }    

    /**
     * Render an object as a string, trunicating and adding elipses
     * if it is "too long".
     */
    static protected String truncateIfNecessary(String str) {

        if (str != null) {
            if (str.length() > MAX_PROGRESS_LENGTH ) {
                str = str.substring(0, (MAX_PROGRESS_LENGTH-ELLIPSE.length())) 
                      + ELLIPSE;
            }
        }
        return str;
    }

    /**
     * @ignore
     * jsl - what is this for?  It was commented out.
     */
    public void completed() {
        // updateProgress("Complete", 100, true);
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Partitioned Request
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * For logging purposes, get the name of the partitioned Request
     * @return if this is a a request of a partitioned task, return the name of the partition Request.
     * Otherwise, return null.
     */
    public String getPartitionedRequestName() {
        return (_request != null) ? _request.getName() : null;
    }

    /**
     * If this a partitioned request, return the existing RequestState associated
     * with the Request.  If none currently exists for the partitioned Request, instantiate
     * a new one.  Will return null is not a partitioned Request.
     * @return the associated RequestState object, or null if not a partitioned Request
     * @throws GeneralException upon database error
     */
    public RequestState getPartitionedRequestState() throws GeneralException {
        RequestState requestState = null;

        if (isPartitioned()) {
            QueryOptions ops = new QueryOptions();
            ops.add(Filter.eq("request.id", _request.getId()));
            List<RequestState> objs = _context.getObjects(RequestState.class, ops);
            if (!Util.isEmpty(objs)) {
                requestState = objs.get(0);
            }

            if (requestState == null) {
                requestState = new RequestState(_request);
            }
        }

        return requestState;
    }

    public AbstractRequestExecutor getRequestExecutor() {
        if (_requestExecutor == null) {
            if (_request != null) {
                RequestDefinition def = _request.getDefinition();
                if (def != null) {
                    try {
                        String className = def.getEffectiveExecutor();

                        _requestExecutor = (AbstractRequestExecutor) DynamicLoader.instantiate(_context, className);
                    } catch (GeneralException ge) {
                        log.warn("Error getting RequestExecutor" + ge);
                    }
                }
            }
        }
        return _requestExecutor;
    }

    /**
     * Calling this method means that caller has changed the state. The requestExecutor
     * will be asked to save the state after every N calls -- where
     * N is defined by lossLimit property set in taskDefinition.
     *
     * @param state the new value of the state
     * @throws GeneralException
     */
    public synchronized void updateTaskState(Object state) throws GeneralException {
        updateTaskState(state, false);
    }

    /**
     * Calling this method means that caller has changed the state. If forceCommit is false,
     * then the requestExecutor will be asked to save the state after every N calls -- where
     * N is defined by lossLimit property set in taskDefinition.  If forceCommit is true,
     * then the requestExecutor will be asked unconditionally to save the state.
     * @param state
     * @param forceCommit
     * @throws GeneralException
     */
    public synchronized void updateTaskState(Object state, boolean forceCommit) throws GeneralException {

        this._stateMap = state;

        if (forceCommit) {
            // force a commit, but leave the stateUpdateCount alone since we may want to log
            // statistics about it eventually
            commitTaskState();
        } else {
            _stateUpdateCount++;
            if (_stateUpdateInterval > 0 && (_stateUpdateCount % _stateUpdateInterval == 0)) {
                commitTaskState();
            }
        }
    }

    private void commitTaskState() throws GeneralException {
        RequestState reqState = getPartitionedRequestState();
        if (reqState != null) {
            if (getRequestExecutor() != null) {
                getRequestExecutor().saveState(this, reqState, _context, _stateMap);
            }
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Partitioned Results
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Lock and return the master result if we're partitioning,
     * if not partitioning just return the result without locking.
     */
    public TaskResult lockMasterResult() throws GeneralException {

        if (_request != null) {
            // have to refetch this every time
            // kludge: we don't seem to be deacching this if it's already
            // there, a problem somewhere...
            _context.decache(_result);
            _result = ObjectUtil.transactionLock(_context, TaskResult.class, _result.getId());
        }

        return _result;
    }
    
    /**
     * Always call this in a finally block after calling
     * lockMasterResult.
     */
    public void commitMasterResult() throws GeneralException {
        _context.saveObject(_result);
        _context.commitTransaction();
    }

    /**
     * Called by code to get a locked partition result.
     * The caller can then add messages or result attributes but
     * it must enter a try/finally statmeent with the finally block
     * calling commitPartitionResult to release the lock. 
     *
     * For consistency this can be called for both partitioned and regular
     * results, though for the IdentityRefresh task it actually
     * isn't enough due to synchronization issues with the multiple
     * refresh worker threads.  See how we handle the progress
     * string above.  Will need to address this when we get around
     * to redesigning RefreshIdentityScan to use partitioning.
     */
    public TaskResult lockPartitionResult() throws GeneralException {

        TaskResult result = null;

        if (_request == null) {
            // not partitioned so not locked
            result = _result;
        }
        else if (!_result.isConsolidatedPartitionResults()) {
            // no locking required, result is owned by the Request
            // this is one of the few places that are allowed to modify the Request
            // since we are in the RP thread processing this Request
            result = _request.bootstrapPartitionResult();
        }
        else {
            // have to refetch this every time
            // kludge: we don't seem to be deacching this if it's already
            // there, a problem somewhere...
            _context.decache(_result);
            _result = ObjectUtil.transactionLock(_context, TaskResult.class, _result.getId());
            result = _result.getPartitionResult(_request.getName());
        }

        return result;
    }

    public void commitPartitionResult() throws GeneralException {

        if (_request != null) {
            if (!_result.isConsolidatedPartitionResults()) {
                // we own this while the RequestExecutor is running
                _context.saveObject(_request);
                _context.commitTransaction();
            }
            else {
                _context.saveObject(_result);
                _context.commitTransaction();
            }
        }
        else {
            // make sure the parent is marked dirty
            _context.saveObject(_result);
            _context.commitTransaction();
        }
    }

}
