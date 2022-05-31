/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
/*
 * jsl - thsi design of this is changing significantly in 6.2.  I'm leaving
 * this around for awhile, but it really needs to be merged into 
 * TaskService.
 */

package sailpoint.scheduler;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.core.QuartzSchedulerThread;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import sailpoint.tools.GeneralException;
import sailpoint.object.Configuration;

/**
 * A bean used by Spring to start and shutdown the quartz scheduler.  We could
 * add a couple of methods to QuartzPersistenceManager to startup and shutdown
 * and just have this bean delegate to those, but I just put these in here to
 * keep the startup/shutdown logic separated.
 *
 * jsl - This was changed a bit with the introduction of the Environment
 * and the Scheduler interface.  This is still constructed by Spring
 * and is responsible for shutting down the scheduler, but it no longer
 * starts it automatically.  The sailpoint.server.SystemStarter will
 * now call this indirectly to start the scheduler.  Through the Scheduler
 * interface it may also be started under application control once
 * things are up and running.
 *
 * NOTE: The Scheduler interface implies that you can start and stop
 * the scheduler at will.  Quartz however doesn't allow its scheduler
 * to be reused once it has been stopped.  So rather than stop it
 * we may have to just suspend it?  It isn't critical, but now that
 * we have the startScheduler and stopScheduler methods in the console
 * someone will hit this eventually.
 *
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */
public class QuartzSchedulerStarter {

    /**
     * maximum time we will spend waiting for a thread to die on shutdown.
     */
    private static final int MAX_WAIT_TIME = 2000;

    private static final Log LOG = LogFactory.getLog(QuartzSchedulerStarter.class);

    private Scheduler scheduler;

    /**
     * Constructor.
     * 
     * @param  scheduler  The Scheduler to manage. 
     */
    public QuartzSchedulerStarter(Scheduler scheduler)
        throws SchedulerException {

        this.scheduler = scheduler;

        // wait for the init-method call
        //LOG.info("Starting quartz scheduler...");
        //scheduler.start();
    }

    /**
     * sailpoint.server.Scheduler interface method to pass in 
     * configuration options from the system configuration object.
     * Currently we're requiring all Quartz configuration be done
     * in the Spring init file, but if it made sense we could have it
     * be sensitive to dynamic changes in the system config.
     */
    public void configure(Configuration config) {
    }

    /**
     * sailpoint.server.Scheuler interface method to start the scheduler.
     * This will eventually be called by SystemStarter unless the
     * scheduler is suppress.  It may also be called at any time
     * after system startup if the scheduler was initially suppressed 
     * and the application now decides to run it.  In current
     * practice this only happens with the console.
     */
    public void startScheduler() throws GeneralException {

        try {
            LOG.info("Starting quartz scheduler...");
            scheduler.start();
        }
        catch (SchedulerException se) {
            throw new GeneralException(se);
        }

    }

    /**
     * sailpoint.server.Scheuler interface method to stop the scheduler.
     * In theory this may be called at any time by the application
     * if it decides not to allow scheduling but in practice we
     * usually wait for the Spring destroy-method to be called.
     */
    public void stopScheduler() throws GeneralException {

        try {
            shutdownAndWaitForThread();
        }
        catch (SchedulerException se) {
            throw new GeneralException(se);
        }
    }

    /**
     * sailpoint.server.Scheuler interface method to check scheduler status.
     */
    public boolean isSchedulerRunning() {
        boolean running = false;
        if (scheduler != null) {
            try {
                // Note that isStarted only means that start() was
                // called once, it does not mean the scheduler is stil running.
                // Checking standby mode is more accurate.
                running = !scheduler.isInStandbyMode();
            }
            catch (SchedulerException se) {
                // should this be propagated or should we just assume
                // it isn't running?
            }
        }
        return running;
    }

    /**
     * Interrupt the scheduler if it is waiting and force it into
     * a duty cycle.
     */
    public void interruptScheduler() {
        if (scheduler != null) {

            // TODO: Needed this for RequestProcessor,
            // Is this possible for Quartz?

        }
    }
    
    /**
     * Pause the scheduler with the intention of resuming it without
     * restarting the server.  Use this instead of stopScheduler if
     * you will later be reviving the scheduler.
     */
    public void suspend() throws GeneralException {
        try {
            LOG.info("Suspending quartz scheduler...");
            scheduler.standby();
        }
        catch (SchedulerException se) {
            throw new GeneralException(se);
        }
    }
    
    /**
     * Continue processing requests after having used suspend to pause
     * the scheduling process.
     */
    public void resume() throws GeneralException {
        try {
            LOG.info("Resuming quartz scheduler...");
            scheduler.start();
        }
        catch (SchedulerException se) {
            throw new GeneralException(se);
        }
    }

    /**
     * This is the Spring destroy-method.  This will shutdown the Scheduler and
     * wait for the QuartzSchedulerThread to die.  Spring's
     * SchedulerFactoryBean.destroy() method simply calls Scheduler.shutdown(),
     * which tells the thread to halt but does not wait on it to complete.  This
     * was causing intermittent errors during shutdown because the dataSource
     * was getting closed before the thread had fully halted and the database
     * connection pool was throwing errors saying
     * "java.lang.IllegalStateException: Pool not open".
     */
    public void shutdownAndWaitForThread() throws SchedulerException {
    
        LOG.info("Shutting down quartz scheduler and waiting for thread...");
        // argument has been true for years, but we started getting build hangs
        // toward the end of 7.0, setting it to false temporarily to see if 
        // that clears it
        scheduler.shutdown(false);

        if (LOG.isDebugEnabled()) {
            listAllThreads();
        }
        LOG.debug("removing scheduler threads.");
        for (Thread thread : getAllSchedulerThreads()) {
            waitOnThread(thread);
        }
        LOG.debug("done removing scheduler threads.");
    }

    private void waitOnThread(Thread thread) {
        
        if (thread == null) {
            LOG.debug("unable to wait on null thread");
            return;
        }
        
        long start = System.currentTimeMillis();
        try {
            thread.join(MAX_WAIT_TIME);
            LOG.debug("Waited " + (System.currentTimeMillis() - start)
                    + " ms for thread to stop: " + thread + "; state = "
                    + thread.getState());
        } catch (InterruptedException e) {
            // ignore
            LOG.info("Interrupted while waiting for quartz thread: " + thread.getName() + " to die.");
        }
    }

    /**
     * tqm: this was being used earlier. I think we just 
     * had one thread at that time. 
     * TODO: remove
     * Return the QuartzSchedulerThread if we can get it.
     * 
     * @return The QuartzSchedulerThread if we can get it, or null if it cannot
     *         be found or we cannot access the threads.
     */
    private static Thread getSchedulerThread() {

        Thread found = null;

        try {
            // This is the only way I found to get a list of all threads.
            Map<Thread, StackTraceElement[]> threads = Thread.getAllStackTraces();
            for (Thread t : threads.keySet()) {
                if (t instanceof QuartzSchedulerThread) {
                    found = t;
                    break;
                }
            }
        }
        catch (SecurityException se) {
            // Can't get the threads ... just ignore.
            LOG.info("Couldn't get list of threads - not waiting for scheduler.");
        }

        return found;
    }
    
    /**
     * tqm: the following thread listing methods are probably more reliabble
     * in getting the scheduler threads.
     */
    private void listAllThreads() {
    
        for (Thread thread : getAllSchedulerThreads()) {
            LOG.debug("Thread: " + thread.getName());
        }
    }
    
    private Thread[] getAllSchedulerThreads() {
        
        List<Thread> schedulerThreads = new ArrayList<Thread>();
        
        Thread[] threads = getAllThreads();
        for (Thread thread : threads) {
            if (thread.getName().toLowerCase().indexOf("quartzscheduler") > -1) {
                schedulerThreads.add(thread);
            }
        }
        
        return schedulerThreads.toArray(new Thread[schedulerThreads.size()]);
    }
    
    private Thread[] getAllThreads() {
        
        final ThreadGroup root = getRootThreadGroup();
        final ThreadMXBean thbean = ManagementFactory.getThreadMXBean();
        int nAlloc = thbean.getThreadCount();
        int n = 0;
        Thread[] threads;
        do {
            nAlloc *= 2;
            threads = new Thread[nAlloc];
            n = root.enumerate(threads, true);
        } while (n == nAlloc);
        return copyOf(threads, n);
    }    
    
    private Thread[] copyOf(Thread[] threads, int length) {
        
        Thread[] copy = new Thread[length];
        System.arraycopy(threads, 0, copy, 0, Math.min(threads.length, length));
        return copy;
    }
    
    private ThreadGroup getRootThreadGroup( ) {
        
        ThreadGroup threadGroup = Thread.currentThread( ).getThreadGroup( );
        ThreadGroup parentThreadGroup;
        while ( (parentThreadGroup = threadGroup.getParent( )) != null ) {
            threadGroup = parentThreadGroup;
        }
        return threadGroup;
    }    

}
