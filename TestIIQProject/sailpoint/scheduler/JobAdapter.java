/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * An adapter between the Quartz Job interface and an instance of
 * one of our JobExecutors.  Quartz does not directly instantiate
 * Job classes, instead we give it a JobFactory that creates
 * instances of JobAdapter wrapping the appropriate JobExecutor.
 * 
 * The effect is similar to Spring's MethodInvokingJobDetailFactoryBean
 * but we can't use that here because it creates JobDetails implementations
 * that are not serializable and hence cannot be used with the Quartz
 * JDBC job store.
 * 
 * Author: Jeff
 *
 * We implement StatefulJob to prevent reentrancy.  For long running tasks
 * this will prevent Quartz from running the job again if the last run takes
 * longer than the schedule time.  This also causes the JobDataMap to be
 * repersisted after the job ends, which I don't think we need.
 *
 * Note that a side of this is that the row in the qrtz_triggers
 * table will enter the BLOCKED state until the task finishes, and if the
 * server goes down that row will remain blocked and the task will never
 * run again.  
 *
 */

package sailpoint.scheduler;

import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.quartz.*;

import static org.quartz.JobKey.*;

import sailpoint.api.SailPointContext;
import sailpoint.api.SailPointFactory;
import sailpoint.api.TaskManager;
import sailpoint.api.Terminator;
import sailpoint.object.TaskSchedule;
import sailpoint.tools.Util;

@PersistJobDataAfterExecution
public class JobAdapter implements InterruptableJob {

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    private static Log log = LogFactory.getLog(JobAdapter.class);

    /**
     * The scheduler object that is launching this job.
     * This is constructed from the Quartz JobDetail by
     * SailPointJobFactory.
     */
    TaskSchedule _schedule;

    /**
     * Object handling the task execution, saved so we can
     * send it async terminate requests.
     */
    TaskManager _taskManager;

    //////////////////////////////////////////////////////////////////////
    //
    // Execution
    //
    //////////////////////////////////////////////////////////////////////

    public JobAdapter(TaskSchedule sched) {
        _schedule = sched;
    }

    /**
     * Quartz InterruptableJob method.
     * We're allowed to throw an execption if the job
     * doesn't allow itself to be executed, not sure if that's
     * necessary, just ignore it?
     */
    public void interrupt() throws UnableToInterruptJobException {

        if (_taskManager != null && !_taskManager.terminate()) {
            // job doesn't allow interruption, throw?
            //throw new UnableToInterruptJobException();
        }
    }

    /**
     * Called by Quartz when we're ready to execute.
     * _schedule may be null here if we created a dummy adapter
     * just to keep Quartz going after some obscure initialization errors.
     */
    public void execute(JobExecutionContext context)
        throws JobExecutionException {

        if (_schedule != null && !isSuspended()) {
            SailPointContext spcon = null;
            try {
                if (log.isInfoEnabled()) {
                    log.info("JobAdapter starting task: " + _schedule.getName());
                }

                // we're at the top of the thread so we manage the context
                spcon = SailPointFactory.createContext("Scheduler");

                Trigger t = context.getTrigger();
                //if (t instanceof SimpleTrigger)
                //log.error("Job triggered manually");

                // Formerly flattened arguments here, now this is 
                // done in TaskManager.  Note that we have historically
                // obtain the schedule arguments by calling 
                // context.getMergedJobDataMap().  I'm not sure if this
                // is always the same as TaskSchedule._arguments so continue
                // this way until someone is brave enough to verify.

                Map schedArgs = context.getMergedJobDataMap();

                _taskManager = new TaskManager(spcon);
                _taskManager.runSync(_schedule, schedArgs);
                
                if ((_schedule.getCronExpressions() == null || isScheduleFinished()) &&
                    _schedule.isDeleteOnFinish()) {
                    Terminator terminator = new Terminator(spcon);
                    terminator.deleteObject(_schedule);
                }
            }
            catch (Throwable t) {
                // Quartz will generally eat these, so do our own logging
                log.error("The " + _schedule.getName() + " task failed to execute", t);
                // even though we're allowed don't propagate exceptions,
                // Quartz seems to let scheduled jobs go into limbo if the
                // job throws
                //throw new JobExecutionException(t);
            }
            finally {
                if (spcon != null) {
                    try {
                        // if we had an expired resume date, clear it
                        // do this after executing the task to avoid any
                        // Quartz side effects of jiggling the job table while
                        // we're trying to run it, paranoia
                        if (_schedule.getResumeDate() != null) {
                            if (log.isInfoEnabled()) {
                                log.info("Clearing resume date on task: " + _schedule.getName());
                            }
                            _schedule.setResumeDate(null);
                            try {
                                spcon.saveObject(_schedule);
                            }
                            catch (Throwable t) {
                                log.error("Unable to clear schedule resume date");
                            }
                        }
                        SailPointFactory.releaseContext(spcon);
                    }
                    catch (Throwable t) {
                        // well we tried, nothing Quartz can do about it...
                        log.error("Unable to release context");
                    }
                }
            }
        }
    }

    /**
     * Return true if this schedule is in a suspended state.
     * This is indiciated when the TaskSchedule has a non-null
     * resumeDate and that date has not yet been reached.
     */
    private boolean isSuspended() {
        boolean suspended = false;
        Date resume = _schedule.getResumeDate();
        if (resume != null) {
            Date now = new Date();
            suspended = resume.after(now);
            if (suspended && log.isInfoEnabled()) {
                log.info("Task suspended: " + _schedule.getName());
                log.info("Resumes on: " + Util.dateToString(resume));
            }
        }
        return suspended;
    }

    /**
     * Check to see if there are any future executions of this job.
     * This is determined by looking at all of the remaining Trigger objects    
     * associated with the JobDetail.
     * 
     * Since we don't expose the Quartz Trigger model through the SailPointContext
     * we have to get a handle to a Schedule object and call the Quartz API.  The
     * Schedule is a singleton that by now has been injected into a 
     * QuartzPersistenceManager via Spring.  We'll store that in a static so we can
     * easilly get to it here.  
     */
    private boolean isScheduleFinished() {

        // if something goes wrong, assume we have to keep it around
        boolean finished = false;

        try {
            
            Scheduler scheduler = QuartzPersistenceManager.getGlobalScheduler();
            if (scheduler != null) {

                // can now assume finished, and make the triggers turn it back on
                finished = true;

                List<Trigger> jdt = (List<Trigger>)scheduler.getTriggersOfJob(jobKey(_schedule.getName()));

                if (jdt.size() > 0) {
                    Iterator<Trigger> trgit = jdt.iterator();

                    while (trgit.hasNext()) {
                        Trigger t = trgit.next();

                        // if this is off I assume we can trust it, but if it is on
                        // it may be a false positive since we're not really done
                        // processing the last trigger until the JobAdapter returns
                        if (t.mayFireAgain()) {

                            if (t instanceof CronTrigger) {
                                CronTrigger trig = (CronTrigger)t;
                                // TODO: Have to dig into the cron expression to see if there
                                // are any repetitions, and if not assume we really can't fire again
                                finished = false;
                            }   
                            else {
                                // A simple trigger may just have a fire time?
                                // SimpleTrigger doesn't appear to have a fire time, but
                                // it will have a start time.  
                                // Is mayFireAgain relevant here? Since we're not technically
                                // done processing the last trigger that may still return true?
                                Date now = new Date();
                                Date next = t.getNextFireTime();
                                if (next == null)
                                    next = t.getStartTime();

                                if (next != null || next.after(now))
                                    finished = false;
                            }
                        }
                    }
                }
            }
        }
        catch (Throwable t) {
            // since this is really just a convenience to remove
            // clutter, it doesn't have to be a fatal Job error,
            // Quartz would just eat the exception anyway
        }

        return finished;
    }
}
