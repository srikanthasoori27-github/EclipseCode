/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.task;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.SailPointContext;
import sailpoint.api.TaskManager;
import sailpoint.object.Attributes;
import sailpoint.object.Filter;
import sailpoint.object.QueryOptions;
import sailpoint.object.TaskDefinition;
import sailpoint.object.TaskResult;
import sailpoint.object.TaskResult.CompletionStatus;
import sailpoint.object.TaskSchedule;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;

public class SequentialTaskExecutor extends AbstractTaskExecutor {

    // ////////////////////////////////////////////////////////////////////
    //
    // Fields/
    //
    // ////////////////////////////////////////////////////////////////////

    private static Log log = LogFactory.getLog(SequentialTaskExecutor.class);

    public static final String ARG_EXIT_ON_ERROR = "exitOnError";
    
    public static final String ARG_TASK_TIME_OUT = "taskTimeOut";
    /**
     * Set by the terminate method to indicate that we should stop when
     * convenient.
     */
    private volatile boolean terminate;
    
    boolean trace;
    
    boolean exitOnError;
    
    TaskMonitor monitor;

    private int taskTimeout;

    // ////////////////////////////////////////////////////////////////////
    //
    // TaskExecutor Interface ///
    //
    // ////////////////////////////////////////////////////////////////////

    /**
     * Terminate at the next convenient point.
     */
    public boolean terminate() {
        this.terminate = true;
        return true;
    }
    
    private void trace(String msg) {
        log.info(msg);
        if (trace)
            System.out.println(msg);
    }

    // ////////////////////////////////////////////////////////////////////
    //
    // execute
    //
    // ////////////////////////////////////////////////////////////////////

    public void execute(SailPointContext ctx, TaskSchedule sched,
            TaskResult result, Attributes<String, Object> args)
            throws Exception {

        // create a TaskManager for managing tasks
        TaskManager tm = new TaskManager(ctx);
        if(result.getOwner()!=null)
            tm.setLauncher(result.getOwner().getName());
        
        
        monitor = new TaskMonitor(ctx, result);
        trace = args.getBoolean(ARG_TRACE);
        exitOnError = args.getBoolean(ARG_EXIT_ON_ERROR);
        taskTimeout = args.getInt(ARG_TASK_TIME_OUT, 0);

        //Note: this must always been task names
        String taskString = args.getString("taskList");
        List<String> theList = Util.csvToList(taskString);
        String runResults =new String();

        for (String taskName : theList) {
            if(this.terminate)
                break;

            // build the launch arguments
            // you may not have any of these depending on the task
            Attributes<String, Object> moreArgs = new Attributes<String, Object>();
            //moreArgs.put("trace", "true");

            TaskDefinition task = ctx.getObjectByName(TaskDefinition.class, taskName);
            if (task == null) {
                // misconfigured task list, assuming that it's okay
                // to die since the other tasks in the list
                // may be dependent on the missing one
                throw new GeneralException("Missing Task Definition: name[" + taskName + "]");
            }

            
            trace(taskName + ": Running");
            monitor.updateProgress(taskName + ": Running");
            
            runResults = runResults.concat(taskName + ": Starting");
            result.setAttribute("tasksRun", runResults);
            
            // run the task in the background with a pre-created result
            TaskResult res = null;
            String resName = null;
            boolean threwOnLaunch = false;
            
            try {
                res = tm.runWithResult(task, moreArgs);
                resName = res.getName();


                int iterations = 0;
                int threadSleep = 5000;
                
                // jsl - while the old code had a check for 20 iterations
                // the count was never incremented, so in effect it will wait 
                // forever
                //for (iterations = 0 ; iterations < maxIterations && !this.terminate ; iterations++) {
                while (!this.terminate) {
                    
                    Thread.sleep(threadSleep);
                    ctx.decache();
                    res = ctx.getObjectById(TaskResult.class, res.getId());
                    
                    if (res == null) {
                        throw new GeneralException("TaskResult evaporated!: " + resName);
                    }
    
                    if (res.isComplete())
                        break;
                    
                    // tasktimeout * 1000 / threadSleep is the number of iterations until timeout
                    if (taskTimeout > 0 && iterations >= (taskTimeout * 1000) / threadSleep) { 
                        throw new GeneralException("Timeout waiting for task completion: " + res.getName());
                    }
                    iterations++;
                }
            } catch (GeneralException ge) {
                log.error(ge);
                threwOnLaunch = true;
            }

            // If the child task had an error, bail out on this one
            if (threwOnLaunch || hasError( ctx, res.getName() ) ) {
                trace(taskName + ": Encountered an Error.");
                runResults = runResults.concat("\n" + taskName + ": Error.\n");
                monitor.updateProgress(taskName + ": Encountered an Error.");
                
                if(exitOnError) {
                    runResults = runResults.concat("\n" + taskName + ": Error.  Exiting.\n");
                    result.setAttribute("tasksRun", runResults);                
                    result.addMessage(new Message(Message.Type.Error, MessageKeys.TASK_SEQUENTIAL_ERROR_ENCOUNTERED, taskName));
                
                    ctx.saveObject(result);
                    ctx.commitTransaction();
                    return;
                }
            }
            else {           
                trace(taskName + ": Complete");
                runResults = runResults.concat("\n" + taskName + ": Complete" + "\n\n");
            }
            result.setAttribute("tasksRun", runResults);
        }
        
        trace("Sequential Task complete.");
        result.setTerminated(terminate);
        result.setAttribute("tasksRun", runResults);
        ctx.saveObject(result);
        ctx.commitTransaction();
    }
    
    /** Determines if the task that just completed had an error or not **/
    private boolean hasError(SailPointContext ctx, String resultName) throws GeneralException{
        List<String> props = Arrays.asList("messages", "completionStatus");
        QueryOptions qo = new QueryOptions();
        qo.add(Filter.eq("name", resultName));
        Iterator<Object[]> rows = ctx.search(TaskResult.class, qo, props);
        if(rows.hasNext()) {
            Object[] obj = rows.next();
            CompletionStatus status = (CompletionStatus) obj[1];
            if (status == TaskResult.CompletionStatus.Error) {
                return true;
            }
            List<Message> messages = (List<Message>)obj[0];
            if(messages!=null) {
                for(Message msg : messages) {
                    if(msg.getType().equals(Message.Type.Error)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
