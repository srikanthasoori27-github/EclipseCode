/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
/*
 * A system thread that processes Request objects.
 * One of these is normally running in every IIQ instance.  It is disabled
 * by default int he console but may be turned on for testing.
 *
 * Author: David, Jeff, and a cast of thousands
 *
 * This was substantially rewritten in 6.2 to support partitioned tasks.
 *
 */

package sailpoint.request;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.ObjectUtil;
import sailpoint.api.SailPointContext;
import sailpoint.api.SailPointFactory;
import sailpoint.api.TaskManager;
import sailpoint.api.Terminator;
import sailpoint.object.*;
import sailpoint.object.TaskResult.CompletionStatus;
import sailpoint.server.Service;
import sailpoint.server.ServicerUtil;
import sailpoint.tools.DateUtil;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.workflow.IdentityLibrary;

public class RequestProcessor extends Thread {

    private static Log log = LogFactory.getLog(RequestProcessor.class);

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Argument in the ServiceDefinition that specifies the 
     * cyle seconds.
     */
    public static final String ARG_CYCLE_SECONDS = "cycleSeconds";

    /**
     * Argument in the ServiceDefinition that specifies the number
     * of seconds between refreshes of the configuration.
     */
    public static final String ARG_CONFIGURATION_REFRESH_SECONDS = 
        "configurationRefreshSeconds";

    /**
     * Argument in the ServiceDefinition that specifies th maximum
     * number of threads to allow.  If set, this is the default
     * for all servers in the cluster.  It may be overridden
     * by setting Server.ARG_MAX_REQUEST_THREADS for each server.
     */
    public static final String ARG_MAX_THREADS = "maxThreads";

    /**
     * Default number of seconds on one cycle.
     * Can be overridden with ARG_CYCLE_SECONDS.
     */
    public static final int DEFAULT_CYCLE_SECONDS = 10;

    /**
     * Default number of seconds between refresh of the configuraiton.
     */
    public static final int DEFAULT_CONFIGURATION_REFRESH_SECONDS = 60;

    /**
     * The number of seconds between subsequent retries.
     * Defaults to 1 hour
     */
    public static final int DEFAULT_RETRY_INTERVAL = 3600;

    public static final int DEFAULT_RETRY_MAX = 20;

    public static final String APPLICATION_PROPERTY = "integration";

    /**
     * Singleton instance of the RequestProcessor.
     * This is a unit test kludge so we can dynamiically change
     * aspects of the thread pool without having to poke holes
     * in the Service interface for random things.  If we start doing
     * this a lot consider generalizing this in Service so we can just
     * ask Enviroment for a nemd Service and pass it options.
     */
    static RequestProcessor _singleton;

    /**
     * A flag set once we've finished the one-time deferred initialization
     * when the RP thread starts running.
     */
    boolean _initialized;

    /**
     * The Service object managing the request processor.
     * This gives us a handle to the ServiceDefinition where some of
     * our configuration can be stored.
     */
    Service _service;

    /**
     * Maximum number of request threads to allow to be running.  This 
     * is an overall governor that restricts the thread pools which heave their
     * own maximums.
     */
    int _maxThreads;
    
    /**
     * A ThreadPool for each RequestDefinition.
     */
    Map<String,ThreadPool> _pools;

    /**
     * Flag indicating that we are in restricted mode.  This means that the
     * processor will be allowed to process Requests only if Request.host
     * is set specifically to the current host.  It can only process other
     * requests if this host is listed in the ServiceDefinition.hosts list.
     */
    boolean _restricted;

    /**
     * The number of seconds to pause between processing cycles
     */
    int _cycleSeconds;

    /**
     * The nubmer of seconds since the last configuration refresh.
     */
    int _configurationAge;

    /**
     * The number of seconds between configuration refresh.
     */
    int _configurationRefreshSeconds;
    
    /**
     * A signal to suspend processing requests.
     */
    boolean _suspend;

    /**
     * A signal to stop the processor thread.
     */
    boolean _terminate;

    /**
     * Flag set at the bottom of every cycle, used by ping() to
     * determine if the thread is still alive.
     */
    boolean _ping;

    /**
     * Kludgey flag to allow the RP to execute Requests with PHASE_PENDING.
     * This is used to control the execution of some special requests.
     */
    boolean _allowPendingRequests;

    /**
     * Number of cycles we've processed.
     */
    int _elapsedCycles;

    /**
     * Optional map containing RequestDefinition host restrictions.
     * Set during configure()
     * The map is keyed by RequestDefinition name.  If the entry is null
     * or Boolean(true) it means the request is allowed on this host.
     * If the entry is non-null and Boolean(false) the request is not allowed.
     */
    Map<String,Boolean> _hostRestrictions;

    //////////////////////////////////////////////////////////////////////
    //
    // Constructor
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Create and configure the request processing thread but do not
     * start it yet.  Note that unlike many classes we're not allowed to 
     * keep the SailPointContext, it is only used to read the initial
     * configuration.
     *
     * @ignore
     * jsl - I'm trying to evolve this so that everything it needs comes
     * from the ServiceDefinition, but we'll support some backward compatibility
     * settings in the system config.
     */
    public RequestProcessor(SailPointContext context, Service service) 
        throws GeneralException {

        // set a name for this thread
        super("RequestProcessor");

        // make sure that this thread does not block the JVM from exiting
        setDaemon(true);

        // this better not be defined by now, mayhem ensues
        if (_singleton != null)
            log.error("Creating more than one RequestProcessor");
        _singleton = this;
        
        // so we can get back to it for configuration information
        _service = service;

        refreshConfiguration(context);
    }

    /**
     * Called during construction and on execution cycles
     * when the configuration refresh threshold has been reached.
     * Don't let this throw, we don't want problems reading configuration
     * to mess up execution.
     */
    private void refreshConfiguration(SailPointContext context) {
        try {
            // can use the same one Servicer uses when it detects
            // changes to the ServiceDefinition
            configure(context);
        }
        catch (Throwable t) {
            log.error("Unable to initialize RequestProcessor");
            log.error(t);
        }
    }

    //
    // Logging utilities to emit things in a standard way.
    //

    static private String getLogMessage(String msg) {
        return "Host:" + Util.getHostName() + ": " + msg;
    }

    static private void logInfo(String msg) {
        if (log.isInfoEnabled())
            log.info(getLogMessage(msg));
    }
    
    static private void logWarn(String msg) {
        log.warn(getLogMessage(msg));
    }

    static private void logWarn(String msg, Throwable t) {
        if (log.isDebugEnabled()) {
            // if debug is enabled, let's warn with the full stack
            log.warn(msg, t);
        } else {
            // normally, warn without trying to alarm anybody
            logWarn(msg);
        }
    }
    
    static private void logError(String msg) {
        log.error(getLogMessage(msg));
    }
    
    /*
     * Errors from exceptions should always include the exception 
     */
    static private void logError(String msg, Throwable t) {
        log.error(msg, t);
    }

    static private String getLogMessage(Request req, String msg) {
        return "Host: " + Util.getHostName() + 
            ", Request " + req.getId() + ":" + req.getName() + 
            ": " +  msg;
    }
        
    // make these protected so RequestHandler can use them
    
    static protected void logInfo(Request req, String msg) {
        if (log.isInfoEnabled()) 
            log.info(getLogMessage(req, msg));
    }

    static protected void logWarn(Request req, String msg) {
        log.warn(getLogMessage(req, msg));
    }

    static protected void logError(Request req, String msg) {
        log.error(getLogMessage(req, msg));
    }

    /**
     * Called both during initial configuration, and afterward by Servicer
     * as changes are detected to the ServiceDefinition.
     *
     * For thread pools we have to be sensitive to three objects.
     * 
     *  ServiceDefinition
     *    - can change ARG_MAX_REQUEST_THREADS
     * 
     *  Server
     *    - can change ARG_MAX_THREADS
     *
     * RequestDefinition
     *    - can change ARG_MAX_THREADS and ARG_MAX_QUEUE
     *
     * ServiceDefinition changes we get automatically because Servicer
     * monitors changes and calls the configure() method.  We don't
     * have change hooks for the other two though, and wouldn't want it
     * for Server anyway since the HeartbeatService updates it all the time.
     * RequestProcessor will therefore have to keep it's own schedule
     * for refreshing thread configuration.
     */
    public void configure(SailPointContext context) throws GeneralException {

        int oldCycleSeconds = _cycleSeconds;

        _cycleSeconds = DEFAULT_CYCLE_SECONDS;

        // old way of specifying the cycle time
        Configuration config = context.getConfiguration();
        int secs = config.getInt(Configuration.REQUEST_PROCESSOR_CYCLE_SECONDS);
        if (secs > 0)
            _cycleSeconds = secs;

        // new way of specifying the cycle time
        ServiceDefinition svc = _service.getDefinition();
        secs = svc.getInt(ARG_CYCLE_SECONDS);
        if (secs > 0)
            _cycleSeconds = secs;

        if (log.isInfoEnabled() && oldCycleSeconds != _cycleSeconds)
            logInfo("RequestProcessor cycle seconds: " + _cycleSeconds);

        // controls how often we refresh configuration, might want to 
        // crank down for testing
        secs = svc.getInt(ARG_CONFIGURATION_REFRESH_SECONDS);
        if (secs > 0)
            _configurationRefreshSeconds = secs;
        else 
            _configurationRefreshSeconds = DEFAULT_CONFIGURATION_REFRESH_SECONDS;

        // this is the default for all Servers
        int oldMaxThreads = _maxThreads;
        _maxThreads = svc.getInt(ARG_MAX_THREADS);

        // each Server can override it
        Server server = context.getObjectByName(Server.class, Util.getHostName());
        if (server != null) {
            int ival = server.getInt(Server.ARG_MAX_REQUEST_THREADS);
            if (ival > 0)
                _maxThreads = ival;
        }
        
        if (log.isInfoEnabled() && oldMaxThreads != _maxThreads)
            logInfo("RequestProcessor max threads: " + _maxThreads);


        // thread pools
        // assuming not many of these...
        if (_pools == null)
            _pools = new HashMap<String,ThreadPool>();

        // TODO: could monitor the last modification date on the RequestDefinition
        // to avoid fetching these every time
        List<RequestDefinition> defs = context.getObjects(RequestDefinition.class);
        if (defs != null) {
            // iiqsaw-3355 Allow RequestDefinitions to specify a list
            // of hosts that are the only ones allowed to process requests
            // of that type
            _hostRestrictions = new HashMap<String,Boolean>();
            
            for (RequestDefinition def : defs) {
                String name = def.getName();
                if (name == null)
                    log.error("RequestDefinition with no name");
                else {
                    ThreadPool tp = _pools.get(name);
                    if (tp == null) {
                        tp = new ThreadPool(this, def);
                        _pools.put(name, tp);
                    }
                    tp.configure(def, server);

                    List<String> hosts = Util.csvToList(def.getString(RequestDefinition.ARG_HOSTS));
                    if (hosts != null && hosts.size() > 0) {
                        String me = Util.getHostName();
                        Boolean allowed = new Boolean(hosts.contains(me));
                        _hostRestrictions.put(name, allowed);
                    }
                }
            }
        }

        // Part of 22126, restricted mode means we can only process Requests
        // that are targeted for this specific host.
        //
        // If the Request service is present in this server's 'includedServices' list,
        // or is in the list of hosts in the Request service definition, we treat the Request
        // service as unrestricted -- which means it can process any untargeted request.
        //
        // If the Request service is present in this server's 'excludedServices' list,
        // or is not set in server and is also not in the list of hosts in the Request service
        // definition, we treat the Request service as restricted -- which means it can
        // only process requests that are targeted for this specific host.
        _restricted = !ServicerUtil.isServiceAllowedOnServer(context, svc, Util.getHostName());

        if (log.isDebugEnabled()) {
            log.debug("RequestProcessor restricted mode = " + _restricted);
        }

    }

    /**
     * Backdoor for unit tests.
     * Override the maximum threads for one of the pools.  
     * TODO: I'd rather do this sort of thing with magic
     * arguments in the RequestDefinition but that would require
     * fetching it every time in isAllowed()
     *
     * !! Revisit this now that we have support for dynamic
     * configuration change detection.
     */
    static public void setMaxThreads(String name, int max) {
        if (_singleton == null) {
            log.error("Singleton RequestProcessor not initialized");
        }
        else {
            ThreadPool pool = _singleton._pools.get(name);
            if (pool == null)
                log.error("Unknown ThreadPool: " + name);
            else {
                logInfo("Overriding max threads in pool " + name + " to " + Util.itoa(max));
                pool.setMaxThreads(max);
            }
        }
    }

    /**
     * Backdoor for unit tests.
     * Set the flag that allows the RP to start processing pending requests.
     */
    static public void setAllowPendingRequests(boolean b) {
        if (_singleton == null) {
            log.error("Singleton RequestProcessor not initialized");
        }
        else {
            _singleton._allowPendingRequests = b;
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Service Interface
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Start the processor thread.
     * This can be used both when starting for the first time and resuming
     * after a suspend.
     */
    public void startProcessing() {

        _suspend = false;

        if (getState() == Thread.State.NEW) {
            logInfo("Starting RequestProcessor thread");
            start();
        }
        else {
            logInfo("Resuming RequestProcessor");
            // break it out of the wait state so the
            // tests can see the results sooner
            this.interrupt();
        }
    }

    /**
     * Suspend the processor.  This won't cancel Request threads
     * currently in progress, it just prevents launching new ones.
     * Sadly, suspend() is taken by Thread.
     */
    public void suspendProcessing() {
        logInfo("Suspend RequestProcessor");
        _suspend = true;
    }

    /**
     * Send a signal to the request processor asking it to stop.
     * The processor cannot be resumed once stopped.
     */
    public void terminateProcessing() {
        logInfo("Terminating RequestProcessor");
        _terminate = true;
        this.interrupt();
    }
    
    /**
     * Cause immediate execution of the cycle.
     */
    public void wake() {
        this.interrupt();
    }


    //////////////////////////////////////////////////////////////////////
    //
    // RequestManager Interface
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Thread main loop.
     *
     * This is what gets called when the request processor is initialized
     * and contains the main loop for looking for requests to process.
     */
    public void run() {

        logInfo("RequestProcessor thread running");
        _configurationAge = 0;

        while (!_terminate) {
            try {
                if (!_suspend) {
                    log.debug("Starting request processing cycle");
                    processRequests();
                    logThreads();
                }

                if(_terminate) {
                    break;
                }
                sleep();
                // if we had a ping request, turn it off
                _ping = true;

            }
            catch (Throwable t) {
                log.error("Exception during cycle: " + t.toString(), t);
                // if there's an error, we still need to sleep.  Otherwise we may near-instantaneously
                // find ourselves dealing with the same error again, and again, and again.  Without sleeping
                // at least between exceptions, we could find ourselves in a vicious cycle that doesn't give
                // the JVM a chance to do other things
                if(!_terminate) {
                    sleep();
                }
            }

        }

        stopRequestProcessing();
    }
    
    /**
     * Sleep one cycle for the run() loop.
     */
    private void sleep() {
        try {
            if (log.isDebugEnabled())
                log.debug("Sleeping " + _cycleSeconds + " seconds");
            Thread.sleep(_cycleSeconds * 1000);
            _configurationAge += _cycleSeconds;
        } 
        catch ( InterruptedException ie ) {
            if (log.isInfoEnabled())
                logInfo("Sleep interrupted");
        }
    }
    
    /**
     * Log statistics about request threads.
     * There are several variantst we might want here...
     */
    public void logThreads() {
        if (log.isInfoEnabled()) {
            int running = 0;
            int queued  = 0;
            if (_pools != null) {
                Iterator<ThreadPool> it = _pools.values().iterator();
                while (it.hasNext()) {
                    ThreadPool tp = it.next();
                    running += tp.getRunning();
                    queued += tp.getQueued();
                }
            }

            logInfo(Util.itoa(running) + " threads running, " + 
                    Util.itoa(queued) + " threads queued");
        }
    }
    
    /**
     * @return returns the number of running threads
     */
    public int getRunningThreads(){
        int total = 0;
        if (_pools != null) {
            Iterator<ThreadPool> it = _pools.values().iterator();
            while (it.hasNext()) {
                ThreadPool tp = it.next();
                total += tp.getRunning();
            }
        }
        return total;
    }

    /**
     * Shut down all of the request processing threads if we can.
     */
    private void stopRequestProcessing() {

        logInfo("Stopping request processor threads");
        if (_pools != null) {
            Iterator<ThreadPool> it = _pools.values().iterator();
            while (it.hasNext()) {
                ThreadPool tp = it.next();
                tp.terminateAndWait(2);
            }
        }
    }

    /**
     * Interrupt the processor thread if it is waiting and make it
     * respond to verify that it is still alive.
     */
    public boolean ping() {

        _ping = false;
        this.interrupt();

        // it better respond in under 10 seconds
        for (int i = 0 ; i < 10 && !_ping ; i++) {
            try {
                Thread.sleep(1000);
            }
            catch (InterruptedException ie) {}
        }

        return _ping;
    }

    //////////////////////////////////////////////////////////////////////
    // 
    // Processing Cycle
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Called at the start of each cycle to do deferred initialization
     * after the Service starts.
     * 
     * For the Request servivce what this does is look for orphaned 
     * Request objects that claim to be running on this host.  This
     * only happens once the first time the service starts.
     */
    private void initialize(SailPointContext context) {
        if (!_initialized) {
            _initialized = true;
            try {
                cleanupOrphanRequests(context);
            }
            catch (Throwable t) {
                log.error("Unable to cleanup orphan requests");
                log.error(t);
            }
        }
    }


    /**
     * Loop through all of the available requests and process them.
     *
     * Make sure there is a finally block after creating a SailPointContext
     * to ensure that it is released.
     */
    public void processRequests() {
        
        SailPointContext context = null;
        int requestsProcessed = 0;

        try {
            _elapsedCycles++;
            context = SailPointFactory.createContext("RequestProcessor");
            initialize(context);

            if (_configurationAge > _configurationRefreshSeconds && context != null) {
                _configurationAge = 0;
                refreshConfiguration(context);
            }

            QueryOptions ops = new QueryOptions();
            Filter filter = createRequestsFilter();
            ops.add(filter);

            // Host isn't indexed so filter after the query, check the speed of this with the
            // definition.name join. If it's just at cheap to fetch the whole thing do it because
            // we need it in some cases anyway.
            List<String> props = new ArrayList<String>();
            props.add("id");
            props.add("name");
            props.add("definition.name");
            props.add("host");
            props.add("phase");
            props.add("dependentPhase");

            if (context != null) {
                Iterator<Object[]> result = context.search(Request.class, ops, props);
                // fully load the result before we start processing, seeing
                // some odd lock timeouts under stress
                List<Object[]> rows = new ArrayList<Object[]>();
                while (result.hasNext()) {
                    rows.add(result.next());
                }
                // getting some lock escalation on SQL Server, make sure the cursor is closed
                context.rollbackTransaction();
    
                // lower this to debug eventually
                if (log.isInfoEnabled() && !_terminate) {
                    StringBuilder b = new StringBuilder();
                    b.append("Cycle ");
                    b.append(Util.itoa(_elapsedCycles));
                    b.append(" [");
                    int start = b.length();
                    for (Object[] row : rows) {
                        String id = (String)(row[0]);
                        String name = (String)(row[1]);
                        if (b.length() > start) b.append(", ");
                        b.append(id);
                        b.append(":");
                        b.append(name);
                    }
                    b.append("]");
                    log.info(b.toString());
                }
    
                for (Object[] row : rows) {
                    if(_terminate) {
                        break;
                    }
                    String id = (String)(row[0]);
                    String name = (String)(row[1]);
                    String definition = (String)(row[2]);
                    String host = (String)(row[3]);
                    int phase = Util.otoi(row[4]);
                    int dependentPhase = Util.otoi(row[5]);
    
                    ThreadPool pool = _pools.get(definition);
                    if (pool == null)
                        pool = bootstrapThreadPool(context, definition);
    
                    if (log.isInfoEnabled())
                        logInfo("Considering " + id + ":" + name);
    
                    if (isAllowed(context, id, pool, host, phase, dependentPhase)) {
    
                        processRequest(context, pool, id);
                    }
    
                    // there is no meaningful carryover state, decache
                    // every time to keep things clean
                    context.rollbackTransaction();
                    context.decache();
                }
            }
        } 
        catch ( Throwable t ) {
            log.error("Unable to process requests: " + t.getLocalizedMessage(), t);
        } 
        finally {
            try {
                if ( context != null )
                    SailPointFactory.releaseContext(context);
            } 
            catch (Throwable t) {
                if (log.isWarnEnabled())
                    log.warn("Failed releasing SailPointContext: "
                             + t.getLocalizedMessage(), t);
            }
        }
    }

    /**
     * Build the filter to do the initial selection of Requests to process.
     * This may be furthered filtered by isAllowed().
     */
    private Filter createRequestsFilter() {
        
        Filter queryFilter =
            Filter.and(
                       new Filter[] {
                           Filter.or(
                                     new Filter[] {
                                         Filter.isnull("completed"),
                                         Filter.eq("completed", "")
                                     }),
                           Filter.or(
                                     new Filter[] {
                                         Filter.le("nextLaunch", DateUtil.getCurrentDate()),
                                         Filter.isnull("nextLaunch")
                                     }),
                           Filter.or(
                                     new Filter[] {
                                         Filter.isnull("launched"),
                                         Filter.eq("launched", "")
                                     })
                       });

        if (_restricted) {
            // only those targeted for this host
            // ugh, don't like the Filter API, have to downcast
            ((Filter.CompositeFilter)queryFilter).add(Filter.eq("host", Util.getHostName()));
        }

        return queryFilter;
    }

    /**
     * Called if we find a Request with a definition that isn't
     * in the thread pool.  This can happen with the unit tests 
     * when they import RequestDefionitions after the RP thread has started
     * and cached the _pools.  
     */
    private ThreadPool bootstrapThreadPool(SailPointContext context, String name)
        throws GeneralException {

        ThreadPool pool = null;

        if (log.isInfoEnabled())
            logInfo("Bootstrapping ThreadPool for " + name);

        RequestDefinition def = context.getObjectByName(RequestDefinition.class, name);
        if (def == null) {
            // odd, how could we get here?  just boostrap one so we can create the pool
            log.error("ReauestDefinition not found for " + name);
            def = new RequestDefinition();
            def.setName(name);
        }

        pool = new ThreadPool(this, def);
        // in theory we should be synchonizing this, but it's only unit tests
        _pools.put(name, pool);

        // don't have a Server to pass in, could get one but it isn't used yet anyway
        pool.configure(def, null);

        return pool;
    }

    /**
     * Do further request filtering that couldn't be done with SQL.
     * "phase" is the phase number of the Request.  
     * "dependentPhase" is an optional explicit phase number.  
     *
     * If dependentPhase is -1 it means to start immediately.
     * If dependentPhase is > 0 it means to wait for requests
     * with that specific phase to complete.
     * If dependentphase is == 0 it menans to wait for all requests
     * with phase numbers less than this requests phase to complete.
     *
     * Restart subtlety:
     * 
     * When a task is not restartable, we normally take a "do as much as we can"
     * approach.  If the task involves phases this means that the phases will
     * run even if one or more of the previous phases failed.  It is up to the
     * previous phases to leave control information in the result so that the
     * later phase can adjust their behavior.  But unless the RequestDefiniiton
     * has errorAction='terminate' we will keep on running phases.
     *
     * If the task is restartable it's more complicated.  Say a task
     * has two phases, 1 and 2 and one of the 1 partitionsl fails.  You
     * correct the problem and restart the task.  But in the "do as much as we can"
     * mode phase 2 would have been finished and we have one lingering phase 1
     * partition that can run but it will have no phase 2 follow up.  
     *
     * When a task is restartable we have to prevent phases from running until
     * ALL of the prior phases complete with success.  The behavior is similar
     * to errorAction='terimate' except we only need to terminate the phases
     * following the ones that failed.  In the multi-app aggregation case
     * we can continue doing applications that didn't fail we don't have
     * to terminate the entire thing.
     *
     */
    private boolean isAllowed(SailPointContext context, String id, ThreadPool pool,
                              String host, int phase, int dependentPhase) {

        boolean allowed = true;

        // don't let this throw, move on to the next one
        try {
            // check max thread threshold
            // kludge: Terminate requests must always be allowed to run even
            // if the threads are maxed out.  Ideally we would have an option
            // in the RequestDefinition rather than hard coding this but I want
            // to avoid having to fetch the entire Request and RequestDefinition 
            // just to check that.  If we start having more of these than add
            // a boolean column that can be returned in the initial query.
            // ugh, the same applies to execute and command requests, need a better way
            if (!RequestDefinition.REQ_TERMINATE.equals(pool.getName()) &&
                !TaskExecuteExecutor.REQUEST_DEFINITION.equals(pool.getName()) &&
                !TaskCommandExecutor.REQUEST_DEFINITION.equals(pool.getName())) {
                                                        
                int threads = getRunningThreads();
                if (_maxThreads > 0 && threads >= _maxThreads) {
                    allowed = false;
                    if (log.isInfoEnabled())
                        logInfo("Ignoring " + id + ": maximum of " + 
                                 Util.itoa(_maxThreads) + " allowed threads running");
                }
            }

            // check host affinity
            // Potential conflicts with the restricted host list and 
            // a host stored directly on the Request.  Assume Request wins.
            if (allowed) {
                if (host != null) {
                    String me = Util.getHostName();
                    if (!host.equals(me)) {
                        allowed = false;
                        if (log.isInfoEnabled())
                            logInfo("Ignoring " + id + ": target host is " + host);
                    }
                }
                else if (_hostRestrictions != null) {
                    Boolean status = _hostRestrictions.get(pool.getName());
                    if (status != null && !status) {
                        allowed = false;
                        if (log.isInfoEnabled()) {
                            logInfo("Ignoring " + id + ": " + Util.getHostName() + 
                                    " is not on the allowed host list for " + pool.getName());
                        }
                    }
                }
            }
            
            // check pending requests
            if (allowed && dependentPhase == Request.PHASE_PENDING) {
                // Special hack for the unit tests to allow execution of PHASE_PENDING
                // requests.  This is so we can clean up requests while the 
                // test is starting and have control over when they execute
                if (_allowPendingRequests) {
                    if (log.isInfoEnabled())
                        logInfo("Checking " + id + ": request even though it is pending");
                }
                else {
                    if (log.isInfoEnabled())
                        logInfo("Ignoring " + id + ": request is pending");
                    allowed = false;
                }
            }

            // check dependent phases
            // we have always required phase > 0 to check depednentPhase,
            // any reason for that?
            if (allowed && phase > 0) {
                // Sigh, we need the TaskResult to make the query which means we have to fetch it
                // not ideal but we shouldn't have many phased requests.  I
                Request req = context.getObjectById(Request.class, id);
                if (req == null) {
                    // someone must have claimed it
                    allowed = false;
                }
                else if (req.getTaskResult() == null) {
                    // not supposed to have phased requests without TaskResults
                    // assume it's okay to start
                    log.warn("Request " + id + " is phased but without a task result!");
                }
                else if (dependentPhase == Request.PHASE_NONE) {
                    // can execute immediately, ignore lower phases
                }
                else {
                    // select Requests with the same TaskResult
                    TaskResult result = req.getTaskResult();
                    //Need to do this here because we rollback later
                    boolean isRestartable = result.isRestartable();
                    QueryOptions ops = new QueryOptions();
                    ops.add(Filter.eq("taskResult", result));
                    
                    if (dependentPhase > 0) {
                        // if there is a dependent phase then make sure that all Requests
                        // that have phase value equal to the dependent phase are completed
                        ops.add(Filter.eq("phase", dependentPhase));
                    } else {
                        // otherwise check that Requests with a lower phase are completed
                        ops.add(Filter.lt("phase", phase));
                    }

                    List<String> props = new ArrayList<String>();
                    props.add("completionStatus");

                    Iterator<Object[]> preRequests = context.search(Request.class, ops, props);
                    List<CompletionStatus> statuses = new ArrayList<CompletionStatus>();
                    while (preRequests.hasNext()) {
                        Object[] row = preRequests.next();
                        // will null always come back or is the row just empty?
                        if (row.length > 0)
                            statuses.add((CompletionStatus)row[0]);
                        else
                            statuses.add(null);
                    }
                    // having deadlocks on SQL Server, make sure the transaction is clean
                    context.rollbackTransaction();
                    if (statuses.size() == 0) {
                        // allowed if there are no previous phases
                        allowed = true;
                    }
                    else if (!isRestartable) {
                        // allowed if they completed at all
                        int completes = 0;
                        for (CompletionStatus status : statuses) {
                            if (status != null)
                                completes++;
                        }
                        allowed = (completes == statuses.size());
                    }
                    else {
                        // allowed only if they all completed sucessfully
                        int successes = 0;
                        int failures = 0;
                        for (CompletionStatus status : statuses) {
                            if (status == CompletionStatus.Success ||
                                status == CompletionStatus.Warning)
                                successes++;
                            else if (status != null)
                                failures++;
                        }

                        // can proactively terminate dependent requests
                        // if one of their dependencies fails
                        if (failures > 0) {
                            terminateDependentRequest(context, req);
                            allowed = false;
                        }
                        else {
                            allowed = (successes == statuses.size());
                        }
                    }
                    
                    if (!allowed && log.isInfoEnabled()) {
                        logInfo("Ignoring " + id + ":" + req.getName() + ": phase dependencies");
                    }
                }
            }

            // check thread constraints
            if (allowed) {
                if (!pool.isReady()) {
                    if (log.isInfoEnabled())
                        logInfo("Ignoring " + id + ": " + pool.getName() + " thread pool is full");
                    allowed = false;
                }
            }
        }
        catch (Throwable t) {
            log.error("Unable to finish filtering logic on request: " + id);
            log.error(t);
            log.error(Util.stackToString(t));
            allowed = false;
        }
        return allowed;
    }

    /**
     * If we encounter a partition request that has phase dependencies on
     * previous partitions that failed, mark them as terminated IF the 
     * entire task is marked as restartable.  This ensures that the
     * later phases won't be executed until after the restart.
     *
     * Don't throw since we're part of the outer loop, move on to the next one.
     * 
     * It's kind of funny to be doing this here, but it's a convenient place
     * to detect the issue.  We could also be proactive and terminate the
     * later phases in finish() when we notice the failure for the first time,
     * but this is less reliable.  If the termination fails for some reason
     * it will still run on the next cycle.  Here if the termination fails we'll
     * at least keep retrying it on every cycle.
     *
     * Note that we only terminate this one request, there may be peers dependent
     * on the same failed request, and other phases later than this one.  Those
     * will be handled in the outer loop.
     *
     * After each termination checkTaskCompletion is called, once all partitions
     * have been completed, this will call back to TaskManager.finalizeTask for
     * side effects.
     */
    private void terminateDependentRequest(SailPointContext context, Request request) {

        String host = Util.getHostName();

        try {
            context.decache();
            request = ObjectUtil.transactionLock(context, Request.class, request.getId());
            if (request != null) {

                logInfo(request, "Obtained transaction lock");
                
                // another thread may have already been here first
                if (request.getCompleted() != null) {
                    logInfo(request, "Already terminated by another thread");
                }
                else if (request.getLaunched() != null) {
                    // since all threads should be doing the same dependency check 
                    // nothing should have launched this
                    logError(request, "Was launched before it could be terminated!");
                }
                else {
                    logInfo(request, "Terminating dependent phase request");
                    
                    // hmm, it has to be left behind so other dependent phases
                    // know things upstream terminated?
                    Date now = new Date();
                    request.setCompleted(now);
                    request.setCompletionStatus(CompletionStatus.Terminated);
                    context.saveObject(request);
                    context.commitTransaction();
                    
                    // now update the root result
                    TaskResult rootResult = request.getTaskResult();
                    if (rootResult != null) {
                        // shared result, must lock
                        context.decache();
                        rootResult = ObjectUtil.transactionLock(context, TaskResult.class, rootResult.getId());
                        if (rootResult != null) {
                            // Request never ran so we don't have to worry about copying up
                            // the Request's TaskResult
                            TaskResult partResult = rootResult.getPartitionResult(request.getName());
                            partResult.setCompleted(now);
                            partResult.setCompletionStatus(CompletionStatus.Terminated);
                            // ugh, shouldn't be requiring this but there may be assumptions
                            // that they be in sync in the UI and elsewhere
                            partResult.setTerminated(true);

                            // this will save and commit
                            checkTaskCompletion(context, rootResult);
                        }
                    }
                }
            }
        }
        catch (Throwable t) {
            // don't let exceptions in one request prevent the others from going
            // this could just be a lock timeout so don't be too alarming?
            logError(request, "Unable to terminate request");
        }
        finally {
            // rollback any dangling locks
            try {
                context.rollbackTransaction();
            }
            catch (Throwable t2) {}
        }
    }
    

    /**
     * Lock a request, and launch a thread.
     *
     * Hibernate BS: The relationship between the Request and the shared TaskResult
     * is touchy because both of them need to have transaction locks.  But it seems
     * that the once you lock the Request, the lazy loaded TaskResult remains
     * in the cache even if you try to transaction lock it later, see 
     * HibernatePersistenceManager.transactionLock, setCachable(false) is supposed
     * to prevent this AFAIK but it isn't working.  
     *
     * This will need to be done in two phases, first update the Request in it's own
     * transaction, then decache/refetch the TaskResult and update that in a second
     * transaction.  It is important to commit the Request first so that the
     * other RP's know it is permanently locked.
     */
    private void processRequest(SailPointContext context, ThreadPool pool, String id) {
        
        String host = Util.getHostName();

        try {
            // have to lock since there can be more than one processor
            context.decache();
            Request request = ObjectUtil.transactionLock(context, Request.class, id);
            if (request != null) {
                logInfo(request, "Obtained transaction lock");

                // bug #4075: make sure after obtaining the lock
                // another thread hasn't already processed the request
                if (request.getLaunched() != null || 
                    request.getCompleted() != null) {
                    logInfo(request, "Already processed by another thread");
                    // commit to release lock
                    context.commitTransaction();
                }
                else {
                    logInfo(request, "Processing");

                    Date now = DateUtil.getCurrentDate();

                    // immediately set the launch date, this is what "locks" the Request
                    request.setLaunched(now);
                    // set this for orphan detection
                    request.setHost(host);
                    request.setNextLaunch(null);
                    context.saveObject(request);
                    context.commitTransaction();

                    try {
                        // If we have a shared partition TaskResult, that has to be locked too
                        // sigh, Hibernate BS requires us to decache to transaction lock the
                        // child object, or else it isn't being updated, something is wrong
                        // in HibernatePersistenceManager.transactinoLock or below...

                        TaskResult res = request.getTaskResult();
                        if (res != null) {
                            if (res.isConsolidatedPartitionResults()) {
                                // have to decache first!
                                context.decache();
                                res = ObjectUtil.transactionLock(context, TaskResult.class, res.getId());
                                if (res != null) {
                                    // this will auto-bootstrap
                                    TaskResult pres = res.getPartitionResult(request.getName());
                                    pres.setLaunched(now);
                                    pres.setHost(host);
                                    context.saveObject(res);
                                    context.commitTransaction();
                                }
                                else {
                                    // Something deleted it out from under us or the lock timed out
                                    // not much we can do at this point besides try again
                                    logError(request, "TaskResult evaporated during launch!");
                                }
                            }
                            else {
                                // Request owns the result, no additional locking
                                TaskResult pres = request.bootstrapPartitionResult();
                                pres.setLaunched(now);
                                pres.setHost(host);
                                context.saveObject(request);
                                context.commitTransaction();
                            }
                        }

                    } catch (Throwable t) {
                        // Something went wrong with processing the request. Before we
                        // let our outer catch handle the exception, try and unlaunch
                        // the request first. If there's a problem there, let that
                        // exception be caught here and logged and let our original
                        // exception be the thrown exception.
                        try {
                            request = context.getObjectById(Request.class, id);
                            if (request != null) {
                                request.setLaunched(null);
                                context.saveObject(request);
                                context.commitTransaction();
                            }
                        } catch (Throwable tInner) {
                            logError("Error recovering orphaned request: " + id, tInner);
                        }
                        throw t;
                    }
                    // don't start the thread unless we were able to start both objects
                    pool.add(request);
                }
            }
        }
        catch (Throwable t) {
            // don't let exceptions in one request prevent the others from going
            // this could just be a lock timeout so don't be too alarming?
            logWarn("Unable to process request: " + id);
            logWarn(t.getMessage(), t);
            if (log.isInfoEnabled()) {
                log.info(Util.stackToString(t));
            }
            // shouldn't have a dangling lock but rollback just to be sure
            try {
                context.rollbackTransaction();
            }
            catch (Throwable t2) {}
            // TODO: potentially some cleanup if we threw during the TaskResult
            // update and left the Request looking like it was running...
            

        }
    }

    //////////////////////////////////////////////////////////////////////
    // 
    // Request Completion
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * This must be called by every RequestHandler thread when it has completed
     * processing the Request.  We are given the SailPointContext created for that
     * thread and can use that to save the final status of the Request.  The given
     * Request object may contain changes that need to be saved.  Nothing can 
     * be assumed about the Hibernate session or the Request object being attached.
     * 
     * We take ownership of the context and release it.
     * 
     * It is important that this first reclaim the RequestHandler so that
     * other threads can be launched.  
     *
     * To avoid the usual bizarre Hibernate cache issues, it is safest to refetch
     * the Request object and copy over just the things that may have changed, notably
     * the Message list, the exception stack, and the attributes.  The alternative is
     * reswizzling references to the RequestDefinition and the TaskResult which we have
     * to lock and update fresh anyway.
     * jsl - I'm not following the previous comment.  Why not just decache and reattach?
     *
     * TEST KLUDGE: We used to have the concept of RequestCallback that was only used
     * by the unit tests to get the completion status stored in the Request for 
     * testing the operation  of the RP.  This was removed dudring the 6.2 rewrite,
     * but we still want a way to verify that the exceptions were handled correctly.
     * We could do this by looking for an excpetion in the message list (permanent
     * and temporary) and a non-null nextLaunch date.  But I don't want to mess
     * with rewriting the tests so just check for a magic Request argument
     * and save the final status like we used to with the callback.
     */
    public void finish(SailPointContext context,  ThreadPool pool, RequestHandler handler, 
                       Request finalRequest, CompletionStatus status) {

        String host = Util.getHostName();

        try {
            // First take RequestHandler out of the pool!  Don't ever let this
            // not happen or else the pool will fill with garbage.
            pool.finish(handler);

            if (context == null) {
                // Threw trying to create a context, not much we can do here so just
                // give up
                logError("Ignoring request finalization, no context");
            }
            else {
                // Note that the passed CompletionStatus may be incorrect
                // we have to wait and reread the TaskResult to get the
                // accurate one since RequestExecutor don't always throw
                // to indicate error
                logInfo(finalRequest, Util.otoa(status));

                // Sigh, getting some typical Hibernate BS related to needing
                // to get a transaction lock PESSIMISTIC_WRITE on the TaskResult
                // after it has been lazy loaded from the Request.  This does
                // not seem to be refetching the object, which is disturbing.
                // Until this is sorted do this in two transactions with full
                // decache in between to make sure we're starting fresh.
                // This would be so much simpler if the Request could
                // own it's own TaskResult with implicit locking.
                // UPDATE: This is believed to be fixed in 6.3

                // phase 1: update the TaskResult
                context.decache();
                Request request = context.getObjectById(Request.class, finalRequest.getId());
                boolean restartable = false;
                if (request != null) {

                    if (request.getLaunched() == null) {
                        logError(finalRequest, "Lost launched date after refetch");
                    }
                    
                    TaskResult taskResult = request.getTaskResult();
                    if (taskResult != null) {
                        // save this for later
                        restartable = taskResult.isRestartable();

                        // copy over final statistics from the detached object so
                        // we can store them in the TaskResult, sigh have to do this
                        // again below after refetching
                        // jsl - returning Request attributes doesn't work, they will
                        // have to have been left in the inner TaskResult
                        request.setAttributes(finalRequest.getAttributes());
                        request.setMessages(finalRequest.getMessages());
                        request.setStack(finalRequest.getStack());
                        // technially we only need to do this if we're going to 
                        // delete the Request (which we usually do)
                        request.setPartitionResult(finalRequest.getPartitionResult());

                        // this may adjust the status after we fetch the result
                        status = updateRootResult(context, request, status);

                        // Due to Hibernate BS the last call will have
                        // decached so fetch the Request again 
                        context.decache();
                        request = context.getObjectById(Request.class, finalRequest.getId());
                    }
                }

                // phase 2: update the Request
                if (request == null) {
                    logError(finalRequest, "Request evaporated!");
                }
                else {
                    RequestDefinition def = request.getDefinition();

                    // determine whether the request needs to be retried
                    boolean retry = false;
                    int retries = request.getRetryCount();
                    if (def != null && status == CompletionStatus.TempError) {
                        int retryMax = getRetryMax(request, context);
                        if (retries >= retryMax) {
                            logInfo(request, "Max retries reached");
                        } 
                        else {
                            // we'll retry
                            retry = true;
                        }
                    }

                    // determine if it can be kept for restart
                    boolean restart = false;
                    if (restartable) {
                        // only if it failed
                        restart = (status == CompletionStatus.Error ||
                                   status == CompletionStatus.Terminated);
                    }

                    // Decide whether to keep the Request for later or delete it
                    if (!retry && !restart && isDeletable(context, request, def, status)) {
                        try {
                            logInfo(request, "Deleting");

                            Terminator t = new Terminator(context);
                            t.deleteObject(request);

                            context.commitTransaction();
                        }
                        catch (Throwable t) {
                            log.error("Unable to delete request", t);
                        }
                    }
                    else {
                        // set launched to null indicating that we are no longer running
                        request.setLaunched(null);

                        // assume we don't want retries
                        request.setNextLaunch(null);
                        request.setCompleted(DateUtil.getCurrentDate());

                        // copy over final status from the detached object
                        // I know it looks like we already did this above, but that
                        // only happens if we're part of a partitioned task,
                        // here we have to do the same for all requests
                        request.setAttributes(finalRequest.getAttributes());
                        request.setMessages(finalRequest.getMessages());
                        request.setStack(finalRequest.getStack());
                        
                        // save the final adjusted completion status
                        request.setCompletionStatus(status);

                        // set expiration or next launch date
                        if (retry) {
                            // this must be null for retries
                            request.setCompleted(null);
                            request.setRetryCount(retries + 1);

                            int delay = getRetryTime(request, context);
                            if (log.isInfoEnabled())
                                logInfo(request, "Retry in " + delay + " seconds");

                            Calendar nextLaunch = Calendar.getInstance();
                            nextLaunch.add(Calendar.SECOND, delay);
                            request.setNextLaunch(nextLaunch.getTime());
                        }
                        else if (!restart) {
                            // if it is restartable it is owned by the TaskResult
                            // which will clean it up, otherwise set an expiration
                            Date exp = getExpiration(request, def);
                            if (log.isInfoEnabled()){
                                if (exp != null)
                                    logInfo(request, "Setting expiration date: " + Util.dateToString(exp));
                                else
                                    logInfo(request, "Leaving expiration date null");
                            }
                            request.setExpiration(exp);
                        }
                        else {
                            logInfo(request, "Leaving Request for restart");
                        }

                        try {
                            context.saveObject(request);
                            context.commitTransaction();
                            logInfo(request, "Updated status");
                        } 
                        catch (Throwable t) {
                            log.error("Unable to update request object" , t);
                        }
                    }

                    // We will interrupt the mail loop unless a flag is set to
                    // let the main loop sleep.
                    if (request.getBoolean(RequestDefinition.ARG_REQUEST_PROCESSOR_NO_INTERRUPT) == false) {
                        this.interrupt();
                    }
                }
            }
        }
        catch (Throwable t) {
            // not many ways to get here
            log.error("Unable to finish request", t);
        }
        finally {
            if (context != null) {
                try {
                    SailPointFactory.releaseContext(context);
                } 
                catch (Throwable t) {
                    log.warn("Failed releasing SailPointContext: ", t);
                }
            }
        }
    }

    /**
     * Determine whether we should delete a completed Request object of
     * keep it for analysis.
     *
     * Originally we always kept these around and waited for Housekeeper
     * to prune them.  Since none of the RequestDefinitions set resultExpiration
     * we would rely on the sysconfig parameter REQUEST_MAX_AGE which 
     * defaulted to zero (never expire).  So unless someone manually set
     * REQUEST_MAX_AGE Requests would never be deleted.
     *
     * When Type.Event requests were added we had logic to always delete
     * them if they were successful.  
     *
     * In 6.2 this is configurable as it always should have been.  First
     * We'll pay attention to resultExpiration and leave them with expiration
     * dates.  Unlike TaskDefinitions though if the expiration is it means
     * to delete them immediately, not live forever.
     *
     * May want a "preserve if errors" option?
     */
    private boolean isDeletable(SailPointContext context, Request req, RequestDefinition def, CompletionStatus status) 
        throws GeneralException {

        boolean doit = true;

        // tolerate missing RequestDefinition corruption, always delete
        if (def != null) {
            int expiration = def.getResultExpiration();
            if (expiration != 0 && expiration != -1)
                doit = false;
        }

        if (status == CompletionStatus.Error) {
            boolean preserve = context.getConfiguration().getBoolean(Configuration.REQUEST_PRESERVE_IF_ERRORS, false);
            if (preserve) {
                doit = false;
            }
        }
        
        return doit;
    }

    /**
     * For Requests that are handling a partition with a shared TaskResult
     * update the TaskResult to reflect the completion of this partition.   
     * Don't let this throw so we can continue to clean up the Request object.
     * This should be done first in case the Request will be thrown back for
     * retry.
     *
     * This may eventually call into TaskManager.finalizeTask if this is the 
     * last partition to complete.
     *
     * Due to Hibernate BS, decache and fetch everything fresh to make sure
     * we're getting updates.  The passed Request is currently in the session
     * so we can get to the TaskResult.
     *
     * Note that the RequestExecutor may not have set the partition result
     * completionStatus properly, so check for error messages and assume failure.
     */
    private CompletionStatus updateRootResult(SailPointContext context, Request req, CompletionStatus status) {
        try {
            TaskResult result = req.getTaskResult();
            if (result != null) {
                // start clean and lock the result
                context.decache();
                try {
                    result = ObjectUtil.transactionLock(context, TaskResult.class, result.getId());
                    if (result == null)
                        throw new GeneralException("TaskResult evaporated during finish!");

                    // get partition result from the root or the Request 
                    TaskResult partResult = result.getPartitionResult(req.getName());
                    // this is never on now, will always use the partition result from the Request
                    if (!result.isConsolidatedPartitionResults()) {
                        // may have had partResult in the master task result
                        // if this request was retried or restarted, the latest
                        // result replaces it
                        partResult = req.bootstrapPartitionResult();
                        result.replacePartitionResult(req.getName(), partResult);
                    }
                    
                    // this can never be non-null now
                    if (partResult != null) {

                        // Error messages may have been left in the Request itself
                        // copy them over.  In theory the request could have left result
                        // attributes but we don't have a good way to distinguish those
                        // from random input attributes so don't clutter up the TaskResult
                        // until we have a way to declare them
                        partResult.addMessages(req.getMessages());

                        // we've only been using this for running stack requests
                        // but it is also convenient to store exception stacks
                        // RequestHandler will set it if it catches something
                        partResult.setStack(req.getStack());
                        
                        if (status == CompletionStatus.Terminated) {
                            partResult.setCompletionStatus(status);
                            // We have historically left this set since it's how
                            // TaskExecutors communicate to TaskManager they were
                            // terminated, unfortuntaely this has been stored and the
                            // UI code expects it too.
                            partResult.setTerminated(true);
                        }
                        else if (status == CompletionStatus.Error) {
                            partResult.setCompletionStatus(CompletionStatus.Error);
                        }
                        else if (status == CompletionStatus.TempError) {
                            // This was added in 6.2, we have not histoically persited
                            // this it was only used to indiciate to RequestProcessor
                            // to set up a request retry.  From the TaskResult perspective
                            // it's just an Error
                            partResult.setCompletionStatus(CompletionStatus.Error);
                        }
                        else {
                            // The RequestExecutor returned Success but may have left
                            // error messages in the result which get promoted to Error.
                            CompletionStatus actual = partResult.calculateCompletionStatus();
                            partResult.setCompletionStatus(actual);
                            if (actual != status) {
                                if (log.isInfoEnabled())
                                    log.info("Correcting status for final partition result to " + actual);
                                status = actual;
                            }
                        }
                        
                        // temp failure does not set completion date
                        // this is the magic that makes checkTaskCompletion know the task
                        // isn't complete, everything may have a CompletionStatus but the date
                        // needs to be set to finalize the task
                        if (status != CompletionStatus.TempError) {
                            if (partResult.getCompleted() == null)
                                partResult.setCompleted(new Date());
                        }
                    }

                    // this will save and commit
                    checkTaskCompletion(context, result);
                }
                finally {
                    // normally checkTaskCompletion will commit, but in case something
                    // else threw be sure the transaction lock is released
                    context.commitTransaction();
                }
            }
        }
        catch (Throwable t) {
            log.error("Unable to update shared TaskResult for Request");
            log.error(t);
        }
        return status;
    }

    /**
     * Given a locked master TaskResult, finalize the task if all partitions 
     * have completed.  The result is saved and the transaction committed.
     * This may have side effects such as completion rules, email notifications, 
     * signoffs, etc.  Side effects are managed by TaskManager.
     *
     * Called after a partition completes or has terminated from three paths:
     *
     *    finish/updateTaskResult
     *    isAllowed/terminateDependentrequest
     *    terminatePartitionedTask/deletePartitionedRequests
     *
     */
    private void checkTaskCompletion(SailPointContext context, TaskResult result)
        throws GeneralException {

        int total = 0;
        int complete = 0;

        List<TaskResult> partitions = result.getPartitionResults();
        if (partitions == null || partitions.size() == 0) {
            // Should always have at least one since these are precreated
            // by AbstractTaskExecutor.launchPartition.  
            log.error("Missing partition TaskResult list");
        }
        else {
            for (TaskResult partition : partitions) {
                total++;
                // note that we check the completion date not the status
                // retryable partitions will have no date but an Error status
                if (partition.getCompleted() != null)
                    complete++;
            }
        }

        if (complete == total) {
            if (log.isInfoEnabled())
                log.info("Task completed: " + result.getName());

            TaskManager tm = new TaskManager(context);
            tm.finalizeTask(result);
        }
        else {
            // still waiting, save changes to the partition results

            // need to think about differnt ways to have consolodated
            // progress, should let the RequestExecutor handle this?
            // I18N!
            String progress = "Completed " + Util.itoa(complete) + 
                " of " + Util.itoa(total) + " partitions";
            result.setProgress(progress);

            if (log.isInfoEnabled())
                log.info("Task " + result.getName() + " progress: " + progress);
            
            context.saveObject(result);
            context.commitTransaction();
        }
    }

    /**
     * Calculate the expiration date for this Request.
     * As of 6.2 we won't usually get here since the default is now to delete
     * it automatically when RequestDefinition.resultExpiration is zero.
     * But if someone may bother to set this for diagnostics.
     */
    private Date getExpiration(Request req, RequestDefinition def) {

        Date expiration = null;
        Date now = new Date();
        int units = def.getResultExpiration();

        // negative expirations increment by seconds for demos
        if (units > 0)
            expiration = Util.incrementDateByDays(now, units);
        else if (units < 0)
            expiration = Util.incrementDateBySeconds(now, -units);

        return expiration;
    }

    /**
     * Calculate the number of seconds until the next retry.
     * This can be set in the RequestDefinition or individually
     * in each Request.  If not specified there the default is 
     * one hour.
     *
     * Prior to 6.2 (and I thought we had taken it out long before 
     * that) the delay time was multipled by the number of retries to
     * prevent retrying to soon.  I practice this quickly became useless
     * as the delay time could grow impossibly long.  If we still want to 
     * support decay, add something to the RequestDefinition.
     *
     */
    private int getRetryTime(Request request, SailPointContext context) throws GeneralException {
        int appRetryTime = getRequestApplicationAttribute(request, context, IdentityLibrary.ATT_RETRY_THRESHOLD);
        if (appRetryTime > -1) {
            // units here are seconds but the app setting is minutes
            return appRetryTime * 60;
        }
        int interval = request.getRetryInterval();
        if (interval == 0) {
            RequestDefinition def = request.getDefinition();
            interval = def.getRetryInterval();
            if (interval == 0)
                interval = DEFAULT_RETRY_INTERVAL;
        }

        return interval;
    }

    private int getRetryMax(Request request, SailPointContext context) throws GeneralException {
        int retryMax = DEFAULT_RETRY_MAX;
        int appRetryMax = getRequestApplicationAttribute(request, context, IdentityLibrary.ATT_MAX_RETRIES);
        if (appRetryMax > -1) {
            return appRetryMax;
        }
        RequestDefinition def = request.getDefinition();
        if (def != null)  {
            return def.getRetryMax();
        }
        return retryMax;
    }

    private int getRequestApplicationAttribute(Request request, SailPointContext context, String keyName) throws GeneralException {
        int attrValue = -1;
        String appName = (String) request.getAttribute(APPLICATION_PROPERTY);
        if (Util.isNotNullOrEmpty(appName)) {
            Application app = context.getObjectByName(Application.class, appName);
            if (app != null) {
                Attributes<String,Object> config = app.getAttributes();
                if ( config == null || ( config != null && !config.containsKey(keyName) ) ) {
                    Application proxy = app.getProxy();
                    if ( proxy != null ) {
                        Attributes<String,Object> proxyConfig = proxy.getAttributes();
                        if ( proxyConfig != null && proxyConfig.containsKey(keyName) ) {
                            config = proxyConfig;
                        }
                    }
                }

                // check the config
                if ( config != null ) {
                    Integer configVal = config.getInteger(keyName);
                    if ( configVal != null ) {
                        attrValue = configVal;
                    }
                }
            }
        }
        return attrValue;
    }
    
    //////////////////////////////////////////////////////////////////////
    //
    // Request Termination
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Terminate a partitioned task.
     * This is called by TaskManager.terminate() when it sees that the
     * task is partitioned.
     * 
     * We first find all partition Requests that have not been started and
     * delete them.  Next we create TerminateRequestExecutor requests for 
     * each host that is running the other Requests.
     *
     * TerminateRequestExecutor will eventually call terminateThreads
     * below to interrupt the RequestHandler threads running on this host.
     *
     * jsl - Starting in 7.2, this can all be rewritten to use TaskCommandExecutor
     * and ExecutorTracker.  
     *
     * If we are able to delete all pending requests, TaskManager.finalizeTask
     * is eventually called.
     */
    public void terminatePartitionedTask(SailPointContext context, TaskResult result)
        throws GeneralException {

        List<String> hosts = new ArrayList<String>();
        List<String> pending = new ArrayList<String>();
        List<TaskResult> partitions = result.getPartitionResults();
        if (partitions != null) {
            for (TaskResult part : partitions) {
                String host = part.getHost();
                if (host == null)
                    pending.add(part.getName());
                else if (!hosts.contains(host))
                    hosts.add(host);
            }
        }

        // if we have any pending results, attempt to delete them
        // note that this may modify the hosts list if a host
        // picks up a pending request while we're trying to lock it
        if (pending.size() > 0)
            deletePartitionRequests(context, result, pending, hosts);

        // Note that at this point the TaskResult argument may have been
        // evicted due to locking so you can't modify it or assume it is 
        // in the session.  In theory we can fail to save the Requets,
        // the user must then manually terminate again.

        for (String host : hosts) {
            // Thread not running on this host, have to post a request
            if (log.isInfoEnabled())
                log.info("Host: " + Util.getHostName() + ", Scheduling termination request for: " + result.getName() + " on " + host);
            String defname = RequestDefinition.REQ_TERMINATE;
            RequestDefinition def = context.getObjectByName(RequestDefinition.class, defname);
            Request req = new Request(def);
            req.setName("Terminating " + result.getId() + ":" + result.getName() + " on " + host);
            req.put(TerminateRequestExecutor.ARG_TASK_RESULT_ID, result.getId());
            req.setHost(host);
            // TODO: launcher?
            context.saveObject(req);   
            context.commitTransaction();
        }
    }

    /**
     * Helper for terminatePartitionedTask.
     * Given a TaskResult for a partitioned task and a list of partitions
     * that haven't yet been started, try to delete the Requests and update
     * the TaskResult.  This has some subtle locking implications.
     *
     * Since there can be multiple threads competing to lock the same Request
     * and the same TaskResult the potential for deadlock is high.  To eliminate
     * this do not create a transaction that locks both the TaskResult and a Request
     * at the same time.  If we're very careful with order it woulnd't be a problem 
     * (always lock Request first then TaskResult in all threads) but it's subtle 
     * and hard to maintain.  Instead we'll do this in two phases, first lock and
     * delete the Requests, then lock and update the TaskResult.
     *
     * In theory we can have exceptions trying to delete the Requests, though this
     * would most likely be due to serious database problems.  If one or more Requests
     * can't be locked and deleted we will not mark the partitioned TaskResults as
     * completed.  The overall termination will be incomplete and  the user can try
     * it again after the issue has been resolved.  
     *
     * In theory we can fail to lock the TaskResult after we've deleted the Requests
     * because we're splitting this into two transactions to avoid deadlock.  Log
     * an error and leave the TaskResult alone.  The result will still look pending
     * even though the Requests aren't there and the user can try to manually
     * delete it again.
     *
     * UPDATE: If the task ks marked restrtable, we don't delete the pending partitions,
     * we mark them terminated.
     */
    private void deletePartitionRequests(SailPointContext context, TaskResult master, 
                                         List<String> partitions, 
                                         List<String> hosts)
        throws GeneralException {

        String hostname = Util.getHostName();

        // phase 1: delete the Requests
        // do NOT let this throw so we can always update the TaskResult
        List<String> processed = new ArrayList<String>();
        try {
            for (int i = 0 ; i < partitions.size() ; i++) {
                String name = partitions.get(i);

                QueryOptions ops = new QueryOptions();
                ops.add(Filter.eq("taskResult", master));
                ops.add(Filter.eq("name", name));
                Iterator<Request> searchResult = context.search(Request.class, ops);
                if (searchResult.hasNext()) {
                    Request pending = searchResult.next();
                    if (searchResult.hasNext())
                        log.error("More than one matching Request found during termination");
                    try {
                        // have to lock since there can be more than one processor
                        context.decache();
                        pending = ObjectUtil.transactionLock(context, Request.class, pending.getId());
                        if (pending != null) {
                            String host = pending.getHost();
                            if (host != null) {
                                // someone claimed it while we queried, add it to the host list
                                logInfo(pending, "Request claimed while trying to delete it");
                                if (!hosts.contains(host))
                                    hosts.add(host);
                            }
                            else {
                                if (master.isRestartable()) {
                                    // another thread may have marked it already, don't commit again
                                    if (pending.getCompleted() == null) {
                                        logInfo(pending, "Terminating pending restartable partition requst");
                                        // the child TaskResult is where all the interesting stuff happens,
                                        // here we just make it look like it finished, is this really necessary?
                                        pending.setCompleted(new Date());
                                        context.commitTransaction();
                                    }
                                }
                                else {
                                    logInfo(pending, "Deleting pending partition requst");
                                    context.removeObject(pending);
                                    context.commitTransaction();
                                }
                                processed.add(name);
                            }
                        }
                    }
                    finally {
                        // always relase the lock if we threw somewhere
                        try {
                            context.rollbackTransaction();
                        }
                        catch (Throwable t2) {}
                    }
                }
            }
        }
        catch (Throwable t) {
            log.error("Exception trying to delete pending partition requests");
            log.error(t);
        }

        // phase 2: update the TaskResult
        if (processed.size() > 0) {
            context.decache();
            master = ObjectUtil.transactionLock(context, TaskResult.class, master.getId());
            if (master != null) {
                for (int i = 0 ; i < processed.size() ; i++) {
                    String name = processed.get(i);
                    TaskResult part = master.getPartitionResult(name);
                    part.setCompleted(new Date());
                    part.setCompletionStatus(CompletionStatus.Terminated);
                    // sigh have to keep these in sync
                    part.setTerminated(true);
                }

                // finalize task if all terminated
                checkTaskCompletion(context, master);
            }
            else {
                log.error("Master TaskResult evaporated");
            }
        }
    }

   /**
     * Terminate partitioned task requests running on this machine.
     */
    public void terminatePartitionThreads(TaskResult res) throws GeneralException {

        int count = 0;

        // could be smarter about finding the right pool
        if (_pools != null) {
            Iterator<ThreadPool> it = _pools.values().iterator();
            while (it.hasNext()) {
                ThreadPool tp = it.next();
                count += tp.terminate(res);
            }
        }
        
        if (log.isInfoEnabled()) {
            // here there can be several threads
            log.info("Host: " + Util.getHostName() + ", Termination event posted to " + Util.itoa(count) + 
                     " threads for task: " + res.getName());
        }
    }

   /**
     * Terminate a RequestHandler thread running on this machine.
     */
    public void terminate(Request req) throws GeneralException {

        int count = 0;

        // could be smarter about finding the right pool
        if (_pools != null) {
            Iterator<ThreadPool> it = _pools.values().iterator();
            while (it.hasNext()) {
                ThreadPool tp = it.next();
                count += tp.terminate(req);
            }
        }
        
        if (log.isInfoEnabled()) {
            String reqid = req.getId() + ":" + req.getName();
            if (count == 0)
                logInfo(req, "No request threads");
            else {
                // there should only be one per Request
                logInfo(req, "Termination event posted to thread");
            }
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Restart
    //
    //////////////////////////////////////////////////////////////////////
    
    /**
     * Restart a failed partitioned task.
     *
     * As usual we have some transaction subtleties here.
     * 
     * As soon as we start resetting Requests, the RP may start
     * picking them up and attempting to update the TaskResult.  
     * So the TaskResult needs to be in the state we want it and
     * committed before we begin saving Requests.  In theory if there
     * are any database problems saving one or more Requests, then
     * the TaskResult and the Requests could be inconsistent, the TR
     * will think the partitions have been restarted, but the Requests
     * won't be.  
     *
     * Since we expect to be dealing with a modest number of of Requests
     * we can update the TaskResult and the Request list in a single 
     * database transaction to ensure they are consistent.
     *
     * We transaction lock the TaskResult just to prevent two
     * threads from attempting to restart the result at the same time.
     * One potential problem is Housekeeper which can delete
     * TaskResults that have passed their expiration date and it
     * doesn't lock.  The possibility for that is extremely small and
     * if the TR gets deleted out from under us then we'll throw
     * an obscure Hibernate error.
     *
     */
    public void restart(SailPointContext context, TaskResult result) 
        throws GeneralException {

        if (!result.isPartitioned())
            throw new GeneralException("Non-partitioned tasks are not restartable");

        CompletionStatus status = result.getCompletionStatus();
        if (status != CompletionStatus.Error && status != CompletionStatus.Terminated) 
            throw new GeneralException("Task is not restartable");

        // TODO: check to see if this particular type of task is restartable?
        // TaskManager should have done that by now

        // we may build a list of dangling Requests to delete later
        List<Request> danglingRequests = new ArrayList<Request>();

        // lock the result
        context.decache();
        result = ObjectUtil.transactionLock(context, TaskResult.class, result.getId());
        if (result != null) {
            try {
                if (result.getCompletionStatus() == null) {
                    // another thread already restarted it
                    if (log.isInfoEnabled())
                        log.info("Another thread already restarted task: " + result.getName());
                }
                else {
                    // get the list of failed partitions
                    List<TaskResult> failedResults = new ArrayList<TaskResult>();
                    List<TaskResult> partitions = result.getPartitionResults();
                    if (partitions != null) {
                        for (TaskResult part : partitions) {
                            status = part.getCompletionStatus();
                            if (status == CompletionStatus.Error || status == CompletionStatus.Terminated)
                                failedResults.add(part);
                        }
                    }

                    // find all requests associated with this result
                    // assuming a modest number so can use getObjects
                    // there should only be objects for failed partitions
                    // jsl - rethink this, for things like misconfigured rules, all
                    // partitions may have failed
                    QueryOptions ops = new QueryOptions();
                    ops.add(Filter.eq("taskResult", result));
                    List<Request> reqlist = context.getObjects(Request.class, ops);
                    Map<String,Request> reqmap = new HashMap<String,Request>();
                    if (reqlist != null) {
                        for (Request req : reqlist)
                            reqmap.put(req.getName(), req);
                    }
        
                    // match the failed results with requests
                    for (TaskResult part : failedResults) {
                        String name = part.getName();
                        Request req = reqmap.get(name);
                        if (req == null) {
                            // missing request, can't restart this partition
                            // not expecting this
                            String msg = "Unable to restart partition, missing Request: " + name;
                            log.error(msg);
                            // also leave something in the TaskResult, should have a key
                            // if we try to restart several times the messages will stack,
                            // could try to filter
                            part.addMessage(new Message(Message.Type.Error, msg));
                        }
                        else {
                            // remove it for later dangle detection
                            reqmap.remove(name);

                            // clear out old fields in the result and request
                            // ? shouldn't we just start over from scratch, what if there
                            // were result attributes left in there?
                            // remember to get the one inside the Request
                            resetTaskResult(req.getPartitionResult());

                            // remove the one that was left in the root result
                            result.replacePartitionResult(part.getName(), null);

                            // some request types must remain on the same host
                            boolean keepHost = req.getDefinition().isHostSpecific();
                            resetRequest(req, keepHost);

                            // Keep a restart count in the Request, 
                            // could be doing this for the TR instead.
                            // This might be interesting information and is used
                            // by the unit tests.
                            int restarts = req.getInt(Request.ATT_RESTARTS);
                            req.put(Request.ATT_RESTARTS, Util.itoa(restarts + 1));

                            context.saveObject(req);
                        }
                    }

                    // anything remainig in the map shouldn't be there because
                    // the result wasn't marked failed, delete now?
                    Iterator<Request> it = reqmap.values().iterator();
                    while (it.hasNext()) {
                        danglingRequests.add(it.next());
                    }

                    // reset completion status in the master result
                    resetTaskResult(result);

                    // mark it launched since Quartz isn't involved
                    result.setLaunched(new Date());

                    // commit after all changes have been made
                    context.saveObject(result);
                    context.commitTransaction();
                }
            }
            finally {
                // always relase the lock if we threw somewhere
                context.rollbackTransaction();
            }
        }

        // if we're left with any dangling requests, delete them
        // consider having Housekeeper or something do this periodically
        for (Request dangle : danglingRequests) {
            log.warn("Removing dangling request, task " + result.getName() + 
                     " partition " + dangle.getName());
            context.removeObject(dangle);  
            context.commitTransaction();
        }
    }

    /**
     * Given a Request that has either completed, or was orphaned,
     * put it in a state that will allow it to be restarted.
     *
     * The crucial parts are to clear the completed, launched, and
     * nextLaunch dates since that is what the RP main thread uses
     * to look for ready requests.  Beyond those there may be lingering
     * status and results from the last run that are no longer relevant.
     * This does NOT save or commit, it only modifies the object.
     */
    private void resetRequest(Request req, boolean keepHost) {

        // TODO: Might want to bump a reset counter so we
        // can see how many times this happened

        // this gets launched, completed, host, etc.
        resetTaskItem(req, keepHost);

        // things specific to Request
        req.setNextLaunch(null);

        // these can't currently be set for partitioned requests but be safe
        req.setRetryCount(0);
    }

    /**
     * Given a TaskResult, reset the state from the last run so that
     * it may be restarted.  Not all of these are used by
     * partitioned tasks but be thorough for the future.
     */
    private void resetTaskResult(TaskResult res) {
        if (res != null) {
            // this gets launched, completed, host, etc.
            resetTaskItem(res, false);

            // things specific to TaskResult
            // UPDATE: now that we moved CompletetionStatus and Terminated
            // down to TaskItem we don't have anything more to do
        }
    }

    /**
     * Given a TaskResult or a Request, reset the parts inherited
     * from the TaskItem.  Results need to have runtime state
     * like messages and progress reset.  Requests don't currently 
     * but they might so go ahead and handle both consistently.
     */
    private void resetTaskItem(TaskItem item, boolean keepHost) {

        // what about launcher?
        item.setLaunched(null);
        item.setCompleted(null);
        item.setCompletionStatus(null);
        item.setTerminated(false);
        item.setLive(false);

        if (!keepHost)
            item.setHost(null);

        item.setMessages(null);
        item.setStack(null);
        item.setProgress(null);
        item.setPercentComplete(0);

        // requests and partitioned resultse don't use these
        // but be complete
        item.setExpiration(null);
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Housekeeping
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Look for orphaned Requests that think they're still running on this 
     * machine.  This MUST ONLY be called when you're sure no requests
     * are currently running.  
     *
     * It is called by the run() method the first time we start running in 
     * this JVM.  
     *
     * Requests differ from task results in that we can terminate
     * them or restart them.  Since these are intended to be short
     * and not have lots of side effects, restarting seems okay but
     * may need some control.
     */
    private void cleanupOrphanRequests(SailPointContext con) 
        throws GeneralException {

        resetRequests(con, Util.getHostName());
    }

    /**
     * Look for Requests that claim to be running on a crashed host
     * and reset them.  You have to be very careful with the transaction
     * here since we're dealing with two objects, a Request and the
     * shared TaskResult.  The Request must not be accessible to the
     * main RP thread or to other HeartbeatServices while we're 
     * resetting it. Most of the code in RP handles the Request
     * and TaskResult in different transactions but we can't do that 
     * here.  Cleaning up the Request makes it accessible to the main RP
     * loop which may modify the TaskResult before we are able to and
     * we'll trash it.  Have to acquire locks on both.  I don't think
     * this can happen but in rare situations this may cause a DB deadlock
     * if something else is locking these objects, in that case just log
     * and exit and let another HeartbeatService try to clean it up later.
     */
    public void resetRequests(SailPointContext con, String host) 
        throws GeneralException {

        String thisHost = Util.getHostName();

        log.info("Host: " + thisHost + ", Resetting orphan requests for " + host);

        QueryOptions ops = new QueryOptions();
        ops.add(Filter.and(Filter.eq("host", host),
                           Filter.isnull("completed")));

        List<String> props = new ArrayList<String>();
        props.add("id");

        // Fully fetch the id list so we can rollback without
        // losing the cursor
        Set<String> ids = new HashSet<>();
        Iterator<Object[]> rows = con.search(Request.class, ops, props);
        while (rows.hasNext()) {
            Object[] row = rows.next();
            ids.add((String)row[0]);
        }

        resetRequests(con, ids);
    }

    public void resetRequests(SailPointContext con, Set<String> requestIds)
            throws GeneralException {

        for (String id : Util.safeIterable(requestIds)) {
            // lock it
            con.decache();
            Request request = ObjectUtil.transactionLock(con, Request.class, id);
            if (request != null) {
                TaskResult result = null;
                try {
                    if (request.getHost() == null) {
                        logInfo(request, "Already reset by another thread");
                        con.rollbackTransaction();
                    }
                    else if (request.isComplete()) {
                        logInfo(request, "Already completed, aborting reset");
                        con.rollbackTransaction();
                    }
                    else {
                        // todo: need a sysconfig option?
                        boolean restart = true;

                        // if "errorAction" is set to terminate
                        // then entire task needs to be terminated.
                        // this applies specifically to partitioned tasks
                        boolean terminate = false;

                        // some request types must remain on the same host
                        boolean keepHost = false;

                        String defname = "???";
                        // save this in case we have to delete the Request
                        String partName = request.getName();

                        result = request.getTaskResult();
                        RequestDefinition def = request.getDefinition();
                        if (def != null) {
                            defname = def.getName();
                            keepHost = def.isHostSpecific();
                            Object o = def.getArgument(RequestDefinition.ARG_ORPHAN_ACTION);
                            if (RequestDefinition.ORPHAN_ACTION_DELETE.equals(o)) {
                                restart = false;
                            }
                            if (RequestDefinition.ERROR_ACTION_TERMINATE.equals(def.getArgument(RequestDefinition.ARG_ERROR_ACTION))) {
                                terminate = true;
                            }
                        }

                        // bump the logs to warn so we can see them
                        if (restart) {
                            logWarn(request, "Resetting crashed request");
                            resetRequest(request, keepHost);
                        }
                        else {
                            // can commit this now
                            logWarn(request, "Deleting crashed request");
                            // unlike TaskResults we don't leave these behind
                            con.removeObject(request);
                            con.commitTransaction();
                            request = null;
                        }

                        // potential Hibernate suckage...
                        // we've seen cases where if an object is in the cache locking
                        // it doesn't get the fresh content, this will detach the Request
                        if (result != null) {
                            con.decache();
                            result = ObjectUtil.transactionLock(con, TaskResult.class, result.getId());

                            TaskResult pres = result.getPartitionResult(partName);
                            if (!restart) {
                                if (terminate) {
                                    terminatePartitionedTask(con, result);
                                } else {
                                    // formerly nulled the host, but in 7.2
                                    // we save it for diagnostics
                                    pres.setCompleted(new Date());
                                    pres.setCompletionStatus(CompletionStatus.Error);
                                    pres.addMessage(new Message(Message.Type.Error, "Detected abnormal host termination"));
                                }
                            }
                            else {
                                resetTaskResult(pres);
                                // will this display nicely?
                                //pres.addMessage(new Message(Message.Type.Info, "Restarted after failure"));
                            }

                            con.saveObject(result);
                        }

                        // have to reattach if locking the TaskResult detached it
                        // ugh, feels like potential "multiple objects" noise
                        if (request != null) {
                            con.saveObject(request);
                        }

                        con.commitTransaction();
                    }
                }
                finally {
                    // if we threw trying to process don't commit partial results
                    con.rollbackTransaction();
                }
            }
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // ThreadPool
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Class encapsulating information about the thread pool for one 
     * RequestDefinition.  
     */
    class ThreadPool {
        
        /**
         * Name of the RequestDefinition, useful for trace.
         */
        String _name;

        /**
         * Parent RequestProcessor, necessary for the RequestThread 
         * callback.
         */
        RequestProcessor _processor;

        /**
         * Maximum threads from the RequestDefinition or Server.
         * For the pool, a negative number means unbounded which
         * we started needing for command requests like terminate
         * that must be allowed to run.
         */
        int _maxThreads;

        /**
         * Maximum queue size from the RequestDefinition or Server.
         */
        int _maxQueue;
        
        /**
         * The list of running threads. 
         */
        List<RequestHandler> _running = new ArrayList<RequestHandler>();;
        
        /**
         * The list of pooled threads.
         * Could also have just one list with a running boolean.
         * Do we really need to pool these?  There is no meaingful 
         * IIQ state to cache, how expensive is a Thread to create?
         */
         List<RequestHandler> _pool;
         
        /**
         * List of Requests queued for later processing.
         */
        List<Request> _queue = new ArrayList<Request>();

        //////////////////////////////////////////////////////////////////////
        // 
        // Properties
        //
        //////////////////////////////////////////////////////////////////////

        public ThreadPool(RequestProcessor rp, RequestDefinition def) {
            _processor = rp;
            _name = def.getName();
        }

        /**
         * Configure the pool threads.
         * Usually this comes from the RequestDefinition but each Server
         * may override that.  
         *
         * We currnetly do not have configuration overrides in the Server.
         * This can be called multiple times.a
         */
        public void configure(RequestDefinition req, Server server) {

            int oldMaxThreads = _maxThreads;
            int oldMaxQueue = _maxQueue;

            _maxThreads = req.getInt(RequestDefinition.ARG_MAX_THREADS);
            _maxQueue = req.getInt(RequestDefinition.ARG_MAX_QUEUE);

            // unless explicitly set in the RequestDefinition, the default
            // maximum threads is always 1
            if (_maxThreads == 0 && !req.containsKey(RequestDefinition.ARG_MAX_THREADS))
                _maxThreads = 1;

            if (server != null) {
                // TODO: how to do server overrides, a List<Map> with 
                // the RequestDefinition name in the Map?
            }

            // this can be called repeatedly so don't clutter up the trace
            // if nothing changed
            if (log.isInfoEnabled() && 
                (oldMaxThreads != _maxThreads || oldMaxQueue != _maxQueue)) {
                log.info("Host: " + Util.getHostName() + ", ThreadPool " + req.getExecutor() + 
                         ": max threads " + Util.itoa(_maxThreads) + 
                         ", max queue " + Util.itoa(_maxQueue));
            }
        }

        /**
         * Unit test backdoor
         */
        public void setMaxThreads(int max) {
            _maxThreads = max;
        }

        public String getName() {
            return _name;
        }

        public int getRunning() {
            return _running.size();
        }

        public int getQueued() {
            return _queue.size();
        }

        /**
         * Return true if we have enough run threads.
         * A negative max means it is unbounded.  
         * Ideally zero would be used for that, but we've long defaulted
         * zero to mean 1.
         * Must be called within a synchronized method.
         */
        private boolean isRunnable() {

            return (_maxThreads < 0 || (_running.size() < _maxThreads));
        }

        /**
         * Return true if we have enough slots to queue a request.
         * Not supporting the negative convention here.  If you want
         * unlimited just use maxThreads.
         * Must be called within a synchronized method.
         */
        private boolean isQueueable() {
            return (_queue.size() < _maxQueue);
        }

        /**
         * Return true if the pool can take another request, 
         * either to run or queue.
         */
        public synchronized boolean isReady() {

            return (isRunnable() || isQueueable());
        }
        
        /**
         * Add a Request to the queue.  Must have alraedy called
         * and passed isReady().
         */
        public synchronized void add(Request req) {

            if (isRunnable()) {
                run(req);
            }
            else {
                logInfo(req, "Adding to queue");
                _queue.add(req);
            }
        }

        private void run(Request req) {
            // log in the RequestHandler thread so we can see the thread name
            //logInfo(req, "Starting thread");
            RequestHandler thread = new RequestHandler(_processor, this, req);
            // can only be called from other synchronized methods
            _running.add(thread);
            thread.start();
        }

        /**
         * Remove a RequestHandler that has finished execution.
         */
        public synchronized void finish(RequestHandler thread) {
            
            if (!_running.contains(thread)) {
                log.error("Mismatched thread pools!");
            }
            else {
                _running.remove(thread);
                // TODO: do we want a pool of these?

                // promote from the queue if we have something
                if (isRunnable() && _queue.size() > 0) {
                    Request req = _queue.remove(0);
                    logInfo(req, "Promoting from queue");
                    run(req);
                }
            }
        }

        /**
         * Send termination events to the thread processing a Request.
         * There should only be one of these.
         */
        public synchronized int terminate(Request req) {

            int count = 0;

            if (req != null && req.getId() != null) {
                for (RequestHandler thread : _running) {
                    // these are not necessarily the same, compare by id
                    if (req.getId().equals(thread.getRequest().getId())) {
                        thread.terminate();
                        count++;
                    }
                }
            }

            return count;
        }

        /**
         * Send termination events to all threads executing Requests
         * associated with a given task result.  There may be
         * several of these.
         */
        public synchronized int terminate(TaskResult res) {

            int count = 0;

            if (res != null && res.getId() != null) {
                for (RequestHandler thread : _running) {
                    // these are not necessarily the same, compare by id
                    if (res.getId().equals(thread.getResultId())) {
                        thread.terminate();
                        count++;
                    }
                }
            }

            return count;
        }

        /**
         * Send shutdown signals to all executing requests.
         * !! Should have a flag that prevents add() after
         * this point?
         */
        public synchronized void terminate() {
            for (RequestHandler thread : _running)
                thread.terminate();
        }

        /**
         * Terminate requests, and wait for them to finish.
         */
        public void terminateAndWait(int seconds) {

            terminate();

            for (int i = 0 ; i < seconds ; i++) {
                if (_running.size() == 0)
                    break;
                else {
                    try {
                        Thread.sleep(1000);
                    } 
                    catch ( InterruptedException ie ) {
                        logInfo("Termination sleep interrupted");
                    }
                }
            }

            if (_running.size() > 0)
                logInfo("Timeout waiting for thread termniation: " + _name);
        }
    }

}
