/* (c) Copyright 2010 SailPoint Technologies, Inc., All Rights Reserved. */
/*
 * A Service used for controlling the Quartz scheduler.
 *
 * Author: Jeff
 *
 * This was added in 6.2 to give all background subsysstems a common
 * Service interface.
 *
 */

package sailpoint.server;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.SailPointContext;
import sailpoint.api.SailPointFactory;
import sailpoint.api.TaskManager;
import sailpoint.scheduler.QuartzSchedulerStarter;
import sailpoint.tools.GeneralException;


public class TaskService extends Service {

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    private static Log log = LogFactory.getLog(TaskService.class);

    /**
     * Name under which this service will be registered.
     * Can be used with Servicer.execute(String) to force service
     * execution by name.
     */
    public static final String NAME = "Task";

    /**
     * QuartzScheduleStarter given to us indirectly by Spring.
     * This is a VERY awkward flow of control but I don't want
     * to rip this part out just yet.
     */
    QuartzSchedulerStarter _quartz;

    /**
     * True once we've been started at least once.
     */
    boolean _initialized;

    //////////////////////////////////////////////////////////////////////
    //
    // Constructor/Interface
    //
    //////////////////////////////////////////////////////////////////////

    public TaskService() {
        _name = NAME;
    }

    /**
     * Configure the service before it is started.
     */
    @Override
    public void configure(SailPointContext context) {
        // we'll always create one of these now so it can configure
        // itself but it won't necessarily be started
        if (_quartz == null) {
            // This gets left here by Spring.  I hate the control flow
            // but one thing at a time...
            Environment env = Environment.getEnvironment();
            _quartz = env.getTaskScheduler();
        }
        else {
            // Called every time the ServiceDefinition changes.
            // We don't support dynamic Quartz reconfiguration.
        }

    }

    /**
     * Continuous service interface for starting or resuming the service.
     * This is not where host restrictions are checked, if a call to this
     * is made assume it is okay to start the RequestProcessor, either 
     * during startup by Environment or manually as done by the console.
     *
     * The first time we run we check for orphaned Requets that claim to be
     * running on this host.  We can't do this after a resume since there may
     * still be RequestHandler threads left running after the suspend.
     */
    public void start() throws GeneralException {
        _started = false;
        if (_quartz != null) {
            if (!_initialized) {
                // the first time we start, look for orphans
                _initialized = true;
                terminateOrphanTasks();
            }
            _quartz.startScheduler();
            _started = true;
        }
    }

    /**
     * Continuous service interface for suspending the service.
     */
    public void suspend() throws GeneralException {
        _started = false;
        if (_quartz != null)
            _quartz.suspend();
    }
    
    /**
     * Continuous service interface for terminating the service.
     */
    public void terminate() throws GeneralException {
        _started = false;
        if (_quartz != null)
            _quartz.stopScheduler();
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Orphans
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * This is called the first time the task service is started in
     * this JVM.  Since we know nothing can be running yet, look for
     * TaskResults that claim to be running on this host and terminate them.
     *
     * Interface is awkward here, since start() doesn't require a 
     * SailPointContext.  We are usually called from a thread that
     * already has a context so reuse that one.
     */
    private void terminateOrphanTasks() {

        log.info("Terminating orphan tasks");

        // Sigh, since we can't assume we have a context in 
        // the thread local (SailPointContext won't for example)
        // have to push one and restore.

        SailPointContext save = null;
        try {
            save = SailPointFactory.pushContext();
            SailPointContext ours = SailPointFactory.getCurrentContext();
            TaskManager tm = new TaskManager(ours);
            tm.terminateOrphanTasks();
        }
        catch (Throwable t) {
            log.error(t);
        }
        finally {
            // note that we have to do this even if save is null
            // since we may not have started with one
            try {
                SailPointFactory.popContext(save);
            }
            catch (Throwable t2) {}
        }
    }

}
