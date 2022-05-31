/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * An interface that must be implemented by any class to be managed by 
 * the task scheduler.
 * 
 * Implementations of these are normally referenced in 
 * a TaskDefinition object stored in the repository.
 */

package sailpoint.object;

import sailpoint.api.SailPointContext;

public interface TaskExecutor extends BaseExecutor {

    /**
     * Execute the task. The TaskSchedule is normally specified but
     * may be null in a some special cases (old tests only?).  The
     * argument map contains the arguments from the TaskDefinition
     * merged with those from the TaskSchedule.
     *
     * The task might leave things in the TaskResult, it will be automatically
     * saved when the executor returns.
     *
     * Any exception thrown by the task will be caught and added to the
     * TaskResult automatically.
     */
    public void execute(SailPointContext context, 
                        TaskSchedule schedule,
                        TaskResult result,
                        Attributes<String,Object> args)
        throws Exception;

}
