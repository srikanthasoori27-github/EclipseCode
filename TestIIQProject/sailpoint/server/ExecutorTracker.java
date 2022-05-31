/* Copyright (C) 2017 SailPoint Technologies, Inc., All Rights Reserved. */
/*
 * An object that tracks TaskExecutor and RequestExecutors  currently running on this host.
 * Used to associate TaskResult objects with executors for processing
 * out-of-band commands.
 *
 * Author: Jeff
 *
 * Used by TaskManager for Quartz tasks, TaskRequestExecutor for host-specific tasks,
 * and partitioned task request executors.
 *
 * Call addExecutor immediately before calling TaskExecutor.execute.
 * Caller must have a finally{} block that calls removeExecutor.
 *
 */

package sailpoint.server;

import java.util.HashSet;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;


import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.BaseExecutor;
import sailpoint.object.SailPointObject;
import sailpoint.object.TaskResult;

public class ExecutorTracker {

    private static Log log = LogFactory.getLog(ExecutorTracker.class);

    /**
     * State maintained for each running executor.
     * 
     * BaseExecutor allows us to call terminate() and processCommand().
     * threadName is required for stack capture.
     *
     * The SailPointObject will be a TaskResult for non-partitioned
     * Quartz tasks or a Request for partitioned tasks.  We only really
     * need the id, but having the object name is nice for logging.
     *
     * It is assumed that this is constructed in the thread that will
     * be running the executor so the thread name does not have to 
     * be passed in.
     */
    public static class ExecutorInfo {

        private SailPointObject _owner;
        private BaseExecutor _executor;
        private String _threadName;

        public ExecutorInfo(SailPointObject owner, BaseExecutor exec) {
            _owner = owner;
            _executor = exec;
            _threadName = Thread.currentThread().getName();
        }

        public BaseExecutor getExecutor() {
            return _executor;
        }

        public String getThreadName() {
            return _threadName;
        }

        public TaskResult getTaskResult() {
            return ((_owner instanceof TaskResult) ? (TaskResult)_owner : null);
        }
        
    }

    /**
     * Global cache of running executors.
     */ 
    private static Map<String,ExecutorInfo> _executors = new HashMap<String,ExecutorInfo>();

    /**
     * Add the executor to tracker before it starts executing.
     * Callers must surround the call this with a finally {} block
     * that calls removeExecutor.
     */
    public static synchronized void addExecutor(SailPointObject owner, BaseExecutor exec) {

        String id = owner.getId();
        if (id == null) {
            log.error("Object has no id");
        }
        else {
            if (log.isInfoEnabled()) {
                log.info("Adding executor: " + id + ": " + owner.getName());
            }
            
            // if it is already there replace it and log it
            if (_executors.containsKey(id)) {
                log.error("Executor already in tracker");
            }

            ExecutorInfo info = new ExecutorInfo(owner, exec);
            _executors.put(id, info);
        }
    }
    
    public static synchronized void removeExecutor(SailPointObject owner) {

        String id = owner.getId();
        if (id == null) {
            log.error("Object has no id");
        }
        else if (_executors.containsKey(id)) {
            if (log.isInfoEnabled()) {
                log.info("Removing executor: " + id);
            }
            _executors.remove(id);
        }
        else {
            log.error("Executor is not being tracked: " + id + ": " + owner.getName());
        }
    }

    public static synchronized ExecutorInfo getExecutor(String id) {
        ExecutorInfo info = null;
        if (id == null) {
            log.error("Missing executor id");
        }
        else {
            // this can be null if the task/request ends at about the same time
            // the tracker is called, this is normal
            info = _executors.get(id);
        }
        return info;
    }


    /**
     * Examine the given set of ids (of TaskResult or Request objects), and return the sub-set
     * which are not currently executing on this host.
     * @param ids
     * @return the sub-set of given ids which are NOT currently running on this host.  If none
     * are not running (i.e. all are running), return an empty Set.
     */
    public static synchronized Set<String> findExecutorlessIDs(Set<String> ids) {
        Set<String> executorlessIDs = new HashSet<>();
        if (ids != null) {
            for(String id : ids) {
                if (id != null) {
                    ExecutorInfo info = _executors.get(id);
                    if (info == null) {
                        executorlessIDs.add(id);
                    }
                    else {
                        if (log.isDebugEnabled()) {
                            log.debug("Found taskresult/request " + id + " executing on thread " + info.getThreadName());
                        }
                    }
                }
            }
        }
        return executorlessIDs;
    }

    /**
     * For symmetry with add/remove, allow the owner to be passed in.
     */
    public static ExecutorInfo getExecutor(SailPointObject owner) {
        return getExecutor(owner.getId());
    }

    
}

