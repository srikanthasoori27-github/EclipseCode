/* (c) Copyright 2012 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.server.upgrade;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import sailpoint.api.ObjectUtil;
import sailpoint.api.SailPointContext;
import sailpoint.api.TaskManager;
import sailpoint.api.Terminator;
import sailpoint.object.Identity;
import sailpoint.object.QueryOptions;
import sailpoint.object.TaskDefinition;
import sailpoint.object.TaskExecutor;
import sailpoint.object.TaskResult;
import sailpoint.object.TaskSchedule;
import sailpoint.server.upgrade.framework.ArgumentDescriptor;
import sailpoint.server.upgrade.framework.BaseUpgrader;
import sailpoint.task.AbstractTaskExecutor;
import sailpoint.task.IdentityRefreshExecutor;
import sailpoint.task.TaskMonitor;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.tools.xml.AbstractXmlObject;

/**
 * 
 * A general upgrader that can launch a task and monitor
 * the progress of the task while its executing. The
 * monitoring uses the normal upgrade FRAMEWORK to
 * output messages.
 * 
 * A TaskDefinition is passed in as the argument to the
 * ImportCommand and used to drive the task behavior.
 * 
 * This upgrader will persist the task definition and then
 * remove it when the upgrader exists.  The persistence
 * is necessary because certain tasks require the definiton
 * to be stored. ( specifically the IdentityRefresh Task )
 *
 * Initially designed in 6.0 so we could execute the 
 * IdentityRefresh task during upgrade to handle promoting 
 * IdentityEntitlements.
 *
 * @author <a href="mailto:dan.smith@sailpoint.com">Dan Smith</a>
 * 
 */
public class TaskBasedUpgrader extends BaseUpgrader {
    
	private List<String> _taskArgs = new ArrayList<String>();
	
	@Override
	protected void addArg(ArgumentDescriptor argDescriptor)
	{
        super.addArg(argDescriptor);
        _taskArgs.add(argDescriptor.getName());
	}
	
    @Override
    public void performUpgrade(Context context) throws GeneralException {
        
        SailPointContext ctx = context.getContext();      
        
        // Create a taskResult
        TaskResult result = new TaskResult();
        String defName = null;
        try {   
            TaskDefinition def = null;           
            //
            // This object comes from the ImportAction definition.
            //
            AbstractXmlObject arg = getArgument();
            if ( arg == null ) {
                throw new GeneralException("Missing task definition, import command must include a task definition.");            
            }   
            
            if ( arg instanceof TaskDefinition ) {                    
                def = (TaskDefinition)arg;
                defName = def.getName();
                if ( defName == null ) {
                    throw new GeneralException("Definition specified in ImportAction must have a name.");
                }

                removeDefinitionIfExists(ctx, defName);
                // re-save, commit..
                // we do this because refresh executor reads the def 
                // from its id of the schedule                
                ctx.saveObject(def);
                ctx.commitTransaction();
                def = ObjectUtil.reattach(ctx,def);
                
            } else {
                throw new GeneralException("Argument supplied to task must be a sailpoint.object.TaskDefinition");
            }          
            // put this in the log to help understand what was executed
            logString("TaskDefinition to execute:" + def.toXml());
            
            // Add required arguments..
            for (String taskArgName : _taskArgs) {
            	def.setArgument(taskArgName, getArg(taskArgName));
            }
            
            handleImports(ctx);
            configureTaskDefinition(def);
            
            // Mock up a schedule
            TaskSchedule schedule = new TaskSchedule();
            schedule.setDefinitionName(def.getName());
            schedule.setDisabled(false);
            // give the result a name based on the def
            result.setName(defName + " " + Util.dateToString(new Date()));

            // jsl - logic to instantiate is now here, don't really like calling the executor
            // directly can't we use runSync?
            TaskManager tm = new TaskManager(ctx);
            TaskExecutor executor = tm.getTaskExecutor(def);
            if ( executor == null )
                throw new GeneralException("Task definiton passed into upgrader does not have an task executor. The executor is required.");
            
            // djs : This is lame, but the Monitor object isn't on the base
            // TaskExecutor interface.
            info("Executing task '"+defName+"'");
            if ( executor instanceof AbstractTaskExecutor ) {
                AbstractTaskExecutor ate = (AbstractTaskExecutor)executor;
                ate.setMonitor(createMonitor(ctx, executor, result));
                ate.execute(ctx, schedule, result, def.getArguments());            
            } else {
                executor.execute(ctx, schedule, result, def.getArguments());                
            }
            
            if (!Util.isEmpty(result.getErrors())) {
            	for (Message errorMessage : result.getErrors()) {
            		info("ERROR: " + errorMessage.getLocalizedMessage());
            	}
            	throw new RuntimeException("Task failed with " + result.getErrors().size() + " error(s)");
            }
            
            info("Task complete.");
                    
        } catch(Exception e ) {
            throw new GeneralException("Error performed while executing task definition ["+defName+"]" + e); 
        } finally {            
            if ( result != null )
                // throw this in the log so its there
                logString("Task Result:" + result.toXml());
            removeDefinitionIfExists(ctx, defName);
            cleanUpImports(ctx);
        }        
    }
    
    /**
     * Used to import any Objects the upgrader may need.
     * Subclasses can override if needed.
     * @param The context to handle the import
     */
    protected void handleImports(SailPointContext ctx) throws GeneralException { }
    
    /**
     * Used to clean up any imports made during handleImports
     * @param ctx The context used to clean up
     */
    protected void cleanUpImports(SailPointContext ctx) throws GeneralException { }
    
    /**
     * Subclasses can override this method to make changes to
     * the TaskDefinition before it is executed.
     * @param def The definition to be executed.
     */
    protected void configureTaskDefinition(TaskDefinition def) throws GeneralException { }
    
    /**
     * Allows sub classes to swap in their own Monitor 
     * @param ctx SailPointContext in which the upgrade is running
     * @param executor TaskExecutor that is running the upgrade
     * @param result TaskResult for the task that is running the upgrade
     * @return TaskMonitor to use for the upgrade task
     * @throws GeneralException
     */
    protected TaskMonitor createMonitor(SailPointContext ctx, TaskExecutor executor, TaskResult result) throws GeneralException{
        return new TaskBasedUpgradeMonitor(ctx, executor, result);
    }
        
    /**
     * Nuke our temporary definition we stored to execute the task. 
     * 
     * @param ctx
     * @param defName
     * @throws GeneralException
     */
    private void removeDefinitionIfExists(SailPointContext ctx, String defName) 
        throws GeneralException {
        TaskDefinition existing = null; 
        if ( defName != null )
            existing = ctx.getObjectByName(TaskDefinition.class, defName);
        if ( existing != null ) {
            new Terminator(ctx).deleteObject(existing);            
            ctx.commitTransaction();
            ctx.decache(existing);
        }
    }

    /***
     * This is a monitor that can be handed to the RefreshTask. It extends TaskMonitor
     * because the refresh task has a special monitor that's thread safe. 
     * 
     * It'll return progress to the upgrader so we can see exactly which identity
     * we are processing.
     * 
     * This monitor will write to the upgrade log so we can keep track of exactly
     * what we've been dig.
     *
     * @author dan.smith
     *
     */
    private class TaskBasedUpgradeMonitor extends TaskMonitor {
        
        /**
         * Counter so we don't spam with updates comming from
         * task monitors.
         */
        int counter = 0;
        
        /**
         * Counter we increment as we are called to indicate
         * the number of objects that have already been 
         * processed.
         */
        int totalObjectCounter = 0;        
        
        /**
         * Where supported the total number of objects that will be processed. 
         * 
         */
        int totalObjects = 0;
        
        boolean isRefresh = false;
        
        public TaskBasedUpgradeMonitor(SailPointContext ctx,
                                       TaskExecutor executor,
                                       TaskResult result) 
           throws GeneralException {
            
           super(ctx, result); 
           
           // For the case of the refresh executor we can make some assumptions
           // around the call patterns to the monitors indicating that 
           // identites have been completed. For other tasks that may not
           // be possible so use this kludge to special case the RefreshExector
           if ( executor instanceof IdentityRefreshExecutor ) {
               totalObjects = ctx.countObjects(Identity.class, new QueryOptions());    
               isRefresh = true;          
           }
        }

        public void updateProgress(String progressString) {
            updateProgress(progressString, -1, true);  
        }
       
        public void updateProgress(String progressString, int percentComplete) {
            updateProgress(progressString, -1, true);  
        }

        /**
         * Only implement this one because we can't make any guesses about
         * the different types being passed around. This has to be synchronized
         * so that different threads calling don't collide.
         * 
         * Log all progress but only selectively print something out to info
         */
        public void updateProgress(String progressString, int percentComplete, boolean forceUpdate) {
            synchronized(this) {
                // bug#25063, use logString here since the progress string
                // does not have to obey MessageFormat pattern syntax
                logString(progressString);
                if ( totalObjects > 0 ) {  
                    if ( isRefresh ) { 
                        if ( progressString != null && progressString.startsWith("Refreshing ") )  {
                            totalObjectCounter++;
                        }
                    } else {
                        totalObjectCounter++;
                    }
                    // no need to report the same number 
                    if ( totalObjectCounter < totalObjects )
                        reportProgress(totalObjectCounter, totalObjects);
                } else {                
                    if ( counter++ == 0 ) {
                        info(progressString);
                    } else {
                        if ( counter == 100 ) {                        
                            counter = 0;
                        }                    
                    }
                }
            }
        }       

        public void completed() {
           // info("Task Complete");            
        }        
    }
}
