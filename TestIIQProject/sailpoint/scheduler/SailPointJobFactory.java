/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * A replacement JobFactory Quartz will call when it is ready
 * to launch a job thread.  Normally, Quartz will simply create
 * an instance of the class named in the JobDetails object and pass 
 * runtime arguments through a JobExecutionContext.    Our factory
 * instead returns a JobAdapter class which hides the Quartz model
 * from our TaskExecutors.  
 * 
 * Implementing the StatefulJob interface just so we can register this class
 * as the job class for the JobDetails we create.  This will cause errors
 * that are stored in the JobDataMap on launch failure to be persisted.
 * 
 */

package sailpoint.scheduler;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.quartz.*;
import org.quartz.spi.JobFactory;
import org.quartz.spi.TriggerFiredBundle;

import sailpoint.api.SailPointContext;
import sailpoint.api.SailPointFactory;
import sailpoint.object.TaskSchedule;
import sailpoint.tools.GeneralException;

@PersistJobDataAfterExecution
public class SailPointJobFactory implements Job, JobFactory {

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    private static Log log = LogFactory.getLog(SailPointJobFactory.class);

    //////////////////////////////////////////////////////////////////////
    //
    // Constructors
    //
    //////////////////////////////////////////////////////////////////////

    public SailPointJobFactory() {
    }

    /**
     * Kludge: The version of Spring we're using does not
     * support a "jobFactory" property in SchedulerFactoryBean.
     * Instead we pass the initialized scheduler as a property
     * and wire them up this way.
     */
    public void setScheduler(Scheduler sched) {
        try {
            sched.setJobFactory(this);
        }
        catch (SchedulerException e) {
            log.error(e);
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Quartz methods
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Convert the Quartz model into a TaskSchedule and wrap
     * it in our JobAdapter for execution.
     */
    public Job newJob(TriggerFiredBundle bundle, Scheduler scheduler)
        throws SchedulerException {

        Job job = null;
        JobDetail detail = bundle.getJobDetail();
        JobDataMap args = detail.getJobDataMap();

        // TaskDefinition name should come in as an argument
        String defname = null;
        if (args != null)
            defname = args.getString(TaskSchedule.ARG_EXECUTOR);
        
        // If not set, assume the name of the JobDetail?
        // This was an old convention, do we really need to continue
        // with this, seems confusing..
        if (defname == null)
            defname = detail.getKey().getName();

        if (defname != null) {
            SailPointContext context = null;
            try {

                if (log.isInfoEnabled()) {
                    log.info("SailPointJobFactory: " + defname);
                }

                context = SailPointFactory.createContext("Scheduler");
                
                // Turn the detail into a TaskSchedule so we pass it on through
                // to the executor.  This is a round about way of building it, 
                // but the JobDetail we're passed is the backing object for
                // the TaskSchedule that QuartzPersistenceManager builds.
                TaskSchedule sched = context.getObjectByName(TaskSchedule.class, detail.getKey().getName());

                if (sched == null) {
                    // hmm, can this happen?  fake one up so we can pass the
                    // definition through consistently
                    sched = new TaskSchedule();
                }
        
                // wrap it in an adapter
                job = new JobAdapter(sched);
            }
            catch (Throwable t) {
                log.error(t);

                // We occasionally get obscure errors if we're trying to 
                // schedule a task before Spring has finished wiring everything
                // together (though the SpringStarter) should be preventing 
                // this.  Throwing exceptions in the factory seems to 
                // make recurring jobs get "stuck" and they won't run
                // again so just eat the exception to avoid alarming
                // Quartz.  Return an empty JobAdapter that will do nothing
                // when it is executed.

                // Put the error in the JobDataMap.  This will mark the map as
                // dirty and cause it to be saved, so we can later retrieve it
                // through the TaskSchedule.
                //args.put(QuartzPersistenceManager.ERROR_ATTRIBUTE, t.getLocalizedMessage());
                //throw new SchedulerException(t.toString());

                job = new JobAdapter(null);
            }   
            finally {
                try {
                    SailPointFactory.releaseContext(context);
                } catch (GeneralException e) {
                    if (log.isWarnEnabled())
                        log.warn("Failed releasing SailPointContext: "
                                 + e.getLocalizedMessage(), e);
                }
            }
        }

        return job;
    }

    
    /**
     * The Job execution method.
     * This isn't supposed to be called if we're creating the JobAdapters
     * correctly.
     */
    public void execute(JobExecutionContext context)
        throws JobExecutionException {

        log.error("Attempted to execute SailPointJobFactory!");
    }

}
