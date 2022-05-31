/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * A task that calculates risk scores for applications.
 * This assumes that application links have already been aggregated.
 * 
 * The work is encapsulated in the ScoreKeeper class, all we do
 * is provide the adapter for the task management system.
 *
 * Author: Jeff
 *
 * By default we refresh the scores for all applications in the sytem.
 * ARG_APPLICATIONS may be passed to limit the applications we refresh.
 *
 */

package sailpoint.task;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.ScoreKeeper;
import sailpoint.api.SailPointContext;
import sailpoint.object.Attributes;
import sailpoint.object.TaskResult;
import sailpoint.object.TaskSchedule;

public class ApplicationScorer extends AbstractTaskExecutor {

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    private static Log log = LogFactory.getLog(ApplicationScorer.class);

    ScoreKeeper _scoreKeeper;

    //////////////////////////////////////////////////////////////////////
    //
    // TaskExecutor Interface
    //
    //////////////////////////////////////////////////////////////////////

    public ApplicationScorer() {
    }

    /**
     * Terminate at the next convenient point.
     */
    public boolean terminate() {
        _scoreKeeper.setTerminate(true);
        return true;
    }

    /**
     * Exceptions we throw here will turn into Quartz JobExecutionExceptions,
     * but those just get logged and dissappear.  Try to create a TaskResult
     * object early so we can save errors into it.
     */
    public void execute(SailPointContext context, 
                        TaskSchedule sched, TaskResult result,
                        Attributes<String,Object> args)
        throws Exception {

        _scoreKeeper = new ScoreKeeper(context, args);

        TaskMonitor monitor = new TaskMonitor(context, result);
        _scoreKeeper.setTaskMonitor(monitor);

        _scoreKeeper.prepare();
        _scoreKeeper.scoreApplications();

        _scoreKeeper.saveResults(result);
        result.setTerminated(_scoreKeeper.isTerminated());
    }


}
