/*
 * A Service that manages a Server object representing each IIQ server instance.
 * The Server is updated with a periodic "heartbeat" so other nodes
 * in the cluster can see if it is running and take failover actions if it 
 * stops.  
 *
 * This is normally not an optional service.
 *
 * Author: Jeff
 * 
 * Timely update of the heartbeat is crucial to prevent false crash detection.
 * Some services, notably ResourceEventService and CacheService can take a 
 * substantial amount of time in their execute() mehtods which can delay
 * the execution of the HeartbeatService.  To prevent this this service
 * will launch its own thread to handle the update of the heartbeat.
 * The execute() method will be used for housekeeping and crash detection
 * that is not time critical.
 *
 */

package sailpoint.server;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import sailpoint.api.ObjectUtil;
import sailpoint.api.RequestManager;
import sailpoint.api.SailPointContext;
import sailpoint.api.SailPointFactory;
import sailpoint.object.Filter;
import sailpoint.object.QueryOptions;
import sailpoint.object.Server;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

public class HeartbeatService extends Service {

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    private static Log log = LogFactory.getLog(HeartbeatService.class);

    /**
     * Name under which this service will be registered.
     * Can be used with Servicer.execute(String) to force service
     * execution by name.
     */
    public static final String NAME = "Heartbeat";

    /**
     * An boolean argument in the ServiceDefinition that can disable
     * the housekeeping phase, where we attempt to detect crashed
     * servers and restart their requests.
     */
    public static final String ARG_NO_CRASH_DETECTION = "noCrashDetection";

    /**
     * An integer argument in the ServiceDefinition that can override
     * the value of DEFAULT_CRASH_DETECTION_CYCLES when calculating
     * the crash detection threshold.
     */
    public static final String ARG_CRASH_DETECTION_CYCLES = "crashDetectionCycles";

    /**
     * The default number of Heartbeat intervals to wait until we consider
     * a server crashed.  Normally 2x the service interval is enough, but
     * under heavy load we've seen the heartbeat thread either starve
     * or block waiting for something.  2x and 3x heartbeat misses are common.
     * Make the threshold relatlvely high. 2 minutes feels reasonable, 
     */
    public static final int DEFAULT_CRASH_DETECTION_CYCLES = 12;

    /**
     * Derived from ARG_NO_CRASH_DETECTION.
     */
    boolean _noCrashDetection;

    /**
     * The number of heartbeat cycles we will tolerate until assuming the server crashed.
     */
    int _crashDetectionCycles;

    /**
     * Thread we manage to update the heartbeat.
     */
    HeartbeatThread _thread;

    //////////////////////////////////////////////////////////////////////
    //
    // Constructor/Interface
    //
    //////////////////////////////////////////////////////////////////////

    public HeartbeatService() {
        _name = NAME;
        _interval = 10;
        _crashDetectionCycles = DEFAULT_CRASH_DETECTION_CYCLES;
    }

    /**
     * This is the only one that must run first.
     * Actually now that HeartbeatThread is its own thread we don't
     * need the concept of priority services any more.
     */
    @Override
    public boolean isPriority() {
        return true;
    }

    /**
     * Called early in the startup sequence, and may be called after
     * startup to reload configuration changes.
     */
    @Override
    public void configure(SailPointContext config) {

        // can this ever not be null?  would simplify some things
        if (_definition != null) {
            // convenient to set this lower for testing
            int ival = _definition.getInterval();
            if (ival > 0)
                _interval = ival;

            // should always be left on unless something bad is happening
            _noCrashDetection = _definition.getBoolean(ARG_NO_CRASH_DETECTION);

            // 3 should be enough now that we've boosted the prioirty
            // of of the Servicer thread
            ival = _definition.getInt(ARG_CRASH_DETECTION_CYCLES);
            if (ival > 0)
                _crashDetectionCycles = ival;
        }

        if (_thread != null) {
            _thread.configure(_interval);
        }
        else {
            // assume the service interval is the same as the heartbeat interval
            _thread = new HeartbeatThread(_interval);
        }
    }

    /**
     * Continuous service interface for starting or resuming the service.
     */
    public void start() throws GeneralException {

        _thread.startProcessing();
        _started = true;
    }

    /**
     * Continuous service interface for suspending the service.
     */
    public void suspend() throws GeneralException {
        _started = false;
        _thread.suspendProcessing();
    }
    
    /**
     * Continuous service interface for terminating the service.
     */
    public void terminate() throws GeneralException {
        _started = false;
        _thread.terminate();

        // Getting some weird errors on shutdown if we're currently in the processing cycle
        // shouldn't take that long.  Shouldn't we be doing this for all services?
        // A common interface would allow Servicer to do this.
        boolean waitForIt = false;
        if (waitForIt) {
            for (int i = 0 ; i < 10 ; i++) {
                if (_thread.getState() == Thread.State.TERMINATED) 
                    break;
                else {
                    try {
                        Thread.sleep(100);
                    }
                    catch (Throwable t){}
                }
            }
        }
    }

    /**
     * For continuous services, causes interruption of the wait loop in
     * the internal thread so that the service can process whatever
     * is waiting immediately.
     */
    public void wake() {
    }

    /**
     * For continuous services, this should return true if it can
     * be verified that the internal service thread is responsive.
     */
    public boolean ping() {
        // ignore for now, this isn't used anywhere
        return true;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Execution Interval
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Called each execution interval.
     * Check for crashed servers.
     */
    public void execute(SailPointContext context) throws GeneralException {

        if (_started && !_noCrashDetection) {
            housekeeping(context);
        }

    }

    /**
     * Called each execution interval to check for crashed servers.
     * In theory, if cleanup takes longer than the the refresh interval 
     * (1 minute) it could look like this host is dead.  Could have another
     * service for housekeeping to prevent this, but there really shoulnd't
     * be more than a few tens of objects in play.
     */
    private void housekeeping(SailPointContext context) 
        throws GeneralException {

        String hostname = Util.getHostName();

        // look for crashed servers
        List<String> names = getLateServers(context, _crashDetectionCycles);
        if (names != null) {
            for (String name : names) {
                
                if (hostname.equals(name)) {
                    // We think we crashed.  This can happen on startup
                    // if we do housekeeping before the thread has fully initialized
                    // and set the first heartbneat.  Avoid an error if that happens
                    // by checking to see if any heartbeats have been sent.
                    if (_thread.getHeartbeats() > 0)
                        log.error("Heartbeat thinks its own server crashed! " + name);
                    continue;
                }

                Server server = null;
                try {
                    context.decache();
                    server = ObjectUtil.transactionLock(context, Server.class, name);
                }
                catch (Throwable t) {
                    // In theory this could be a transaction lock timeout
                    // if resetRequests is taking a really long time.
                    log.error("Unable to lock server: " + name);
                }

                if (server != null) {
                    try {
                        if (server.isInactive()) {
                            // another thread got to it first
                            log.info("Another thread deactivated server: " + name);
                        }
                        else {
                            // boost this to a warn so we can see it in the logs
                            log.error("Host: " + hostname + ", deactivating server: " + name);
                            server.setInactive(true);
                            server.put(Server.ATT_CPU_USAGE,0);
                            server.put(Server.ATT_REQUEST_THREADS, 0);
                            server.put(Server.ATT_TASK_THREADS, 0);
                            server.put(Server.ATT_OPEN_FILE_COUNT, 0);
                            context.saveObject(server);
                        }
                    }
                    finally {
                        // releases the transaction lock
                        context.commitTransaction();
                    }

                    // Reset requests owned by this server.  Do this we mark the Server
                    // since it may commit its own transactions which would release
                    // the transaction lock we hold on Server.
                    // !! In theory this could fail and since we've already marked
                    // the server inactive no on else will try to reclaim the requests.
                    // It would be best if we had a periodic housekeeping phase that
                    // looked for inactive=true Servers that still have requests and
                    // reset those.  
                    resetRequests(context, server);
                }
            }
        }

        // Temporary diagnostics: Look for servers that have missed heartbeats and log warnings
        // I'm trying to determine if this is common, might want to remove this if it is common
        // since all nodes will be spraying this every interval.
        names = getLateServers(context, 1);
        if (names != null)
            log.warn("Servers missed a heartbeat: " + names.toString());
    }

    /**
     * Get a list of Server names for servers that have not updated their heartbeat 
     * for the given number of intervals.
     */
    private List<String> getLateServers(SailPointContext context, int cycles) 
        throws GeneralException {

        List<String> names = null;

        // Calculate the length of time in seconds 
        // Add a few seconds of patience in case the interval is small, configurable?
        int padding = 5;
        int seconds = (_interval * cycles) + padding;

        Date now = new Date();
        Calendar cal = Calendar.getInstance();
        cal.setTime(now);
        cal.add(Calendar.SECOND, -seconds);
        Date threshold = cal.getTime();

        QueryOptions ops = new QueryOptions();
        ops.add(Filter.and(Filter.lt("heartbeat", threshold),
                           Filter.eq("inactive", false)));
        
        List<String> props = new ArrayList<String>();
        props.add("name");

        // make sure we have no lingering objects
        context.decache();
        Iterator<Object[]> result = context.search(Server.class, ops, props);
        if (result != null) {
            while (result.hasNext()) {
                Object[] row = result.next();
                if (names == null)
                    names = new ArrayList<String>();
                names.add((String)(row[0]));
            }
        }

        return names;
    }

    /**
     * When a crashed server has been found, look for any Requests
     * that claim to be running on it and reset them.  In theory
     * we could be cleaning up orphan Quartz tasks as well but I don't
     * want this to get too complicated.  If it gets too slow we'll overflow
     * the heartbeat interval.
     */
    private void resetRequests(SailPointContext context, Server server) 
        throws GeneralException {

        RequestManager rm = new RequestManager(context);
        rm.resetRequests(server.getName());
    }

    //////////////////////////////////
    // Util methods
    //////////////////////////////////

    /**
     * If there is a Server object with the given hostName, set its
     * inactive to true.  This is here for SailPointConsole to call
     * when it is shutting down.
     * @param hostName the server object that should be marked inactive
     */
    public static void markServerInactive(String hostName) {
        SailPointContext spc = null;
        try {
            spc = SailPointFactory.createContext("ConsoleShutdown");
            Server server = spc.getObjectByName(Server.class, hostName);
            if (server != null) {
                server.setInactive(true);
                spc.saveObject(server);
                spc.commitTransaction();
            }
        }
        catch (Exception e) {
            log.error(e.getMessage());
        }
        finally {
            if (spc != null) {
                try {
                    spc.close();
                }
                catch (Exception e) {
                    // ignore
                }
            }
        }
    }



    //////////////////////////////////////////////////////////////////////
    //
    // HeartbeatThread
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * High priority thread that updates the Server heartbeat.
     */
    public static class HeartbeatThread extends Thread {

        Service _service;
        boolean _terminate;
        boolean _suspend;
        int _cycleSeconds;

        /**
         * Number of Heartbeats we've sent.  Used to detect a race condition
         * on startup.
         */
        int _heartbeats;

        // experiment to see if DBCP waits are causing us to miss heartbeat updates
        // keep a context around permanently
        SailPointContext _context;

        //////////////////////////////////////////////////////////////////////
        //
        // Service Interface
        //
        //////////////////////////////////////////////////////////////////////

        public HeartbeatThread(int cycleSeconds) {

            super("HeartbeatThread");
            setDaemon(true);

            // It is CRITICAL that the Heartbeat service run reliably
            // or else we may think the host is down.  Make sure this
            // thread cannot starve. 
            setPriority(Thread.MAX_PRIORITY);

            _cycleSeconds = cycleSeconds;
        }

        /**
         * Called whenever changes are detected to the ServiceDefinition
         * which may change the interval.
         */
        public void configure(int cycleSeconds) {
            _cycleSeconds = cycleSeconds;
        }

        /**
         * Start the processor thread.
         * This can be used both when starting for the first time and resuming
         * after a suspend.
         */
        public void startProcessing() {

            _suspend = false;

            if (getState() == Thread.State.NEW) {
                log.info("Host: " + Util.getHostName() + ", Starting HeartbeatThread");
                start();
            }
            else {
                log.info("Host: " + Util.getHostName() + ", Resuming HeartbeatThread");
                // break it out of the wait state so the
                // tests can see the results sooner
                this.interrupt();
            }

            // reset this too since when we come out of suspension execute()
            // needs to know if the thread is ready
            _heartbeats = 0;
        }

        /**
         * Suspend the heartbeat.
         */
        public void suspendProcessing() {
            log.info("Host: " + Util.getHostName() + ", Suspend HeartbeatThread");
            _suspend = true;
        }

        /**
         * Service interface to terminate.
         */
        public void terminate() {
            log.info("Host: " + Util.getHostName() + ", Terminating HeartbeatThread");
            _terminate = true;
            this.interrupt();
        }

        /**
         * Return the number of heartbeats sent by this thread since it was
         * last started.  Used to detect a race condition where the thread
         * hasn't had time to update the heartbeat before the execute() method
         * runs and thinks the server crashed.  If this returns zero we assume
         * the thread is still in the process of starting.
         */
        public int getHeartbeats() {
            return _heartbeats;
        }

        //////////////////////////////////////////////////////////////////////
        //
        // THread Interface
        //
        //////////////////////////////////////////////////////////////////////

        public void run() {

            // Hack for diagnosing missing heartbeats, avoid the DBCP by keeping
            // a dedicated connection.  
            // This didn't work and causes problems if you let the machine
            // sleep and the database disconnects, so leave it off.
            boolean usePermanentContext = false;

            log.info("Host: " + Util.getHostName() + ", HeartbeatThread thread running");

            try {
                while (!_terminate) {
                    try {
                        if (!_suspend) {
                            SailPointContext spc = _context;
                            try {
                                if (spc == null) {
                                    // allocate a new context and optionally save it for the future
                                    spc = SailPointFactory.createContext("HeartbeatService");
                                    if (usePermanentContext)
                                        _context = spc;
                                }

                                updateHeartbeat(spc);
                            }
                            finally {
                                _heartbeats++;
                                if (spc != _context)
                                    releaseContext(spc);
                            }
                        }

                        sleep();
                    }
                    catch (Throwable t) {
                        log.error("Exception during heartbeat cycle: " + t.toString());
                        log.error(Util.stackToString(t));
                        // if there's an error, we still need to sleep.  Otherwise we may near-instantaneously
                        // find ourselves dealing with the same error again, and again, and again.  Without sleeping
                        // at least between exceptions, we could find ourselves in a vicious cycle that doesn't give
                        // the JVM a chance to do other things
                        sleep();
                    }
                }
            }
            finally {
                releaseContext(_context);
            }
                    
            log.info("Host: " + Util.getHostName() + ", HeartbeatThread thread terminating");
        }

        /**
         * Sleep one cycle.
         */
        private void sleep() {
            try {
                if (log.isDebugEnabled())
                    log.debug("Host: " + Util.getHostName() + ", Heartbeat sleeping " + _cycleSeconds + " seconds");
                Thread.sleep(_cycleSeconds * 1000);
            } 
            catch ( InterruptedException ie ) {
                log.info("Host: " + Util.getHostName() + ", Heartbeat sleep interrupted");
            }
        }

        /**
         * Release a context, catching the silly exception we can't do anything about.
         */
        private void releaseContext(SailPointContext con) {
            if (con != null) {
                try {
                    SailPointFactory.releaseContext(con);
                }
                catch (Throwable t) {
                    log.error("Unable to release context", t);
                }
            }
        }

        //////////////////////////////////////////////////////////////////////
        //
        // Server Update
        //
        //////////////////////////////////////////////////////////////////////

        /**
         * Update the Server heartbeat and gather other interesting statistics.
         */
        private void updateHeartbeat(SailPointContext context) throws GeneralException {

            String hostname = Util.getHostName();
            Date now = new Date();

            // Note that we don't get a transaction lock here since we're the only
            // ones that are allowed to be updating this until a crash is detected.
            Server server = context.getObjectByName(Server.class, hostname);

            if (server == null) {
                // First time, bootstrap
                log.info("Bootstrapping Server for " + hostname);
                server = new Server();
                server.setName(hostname);
            }
            else {
                log.info("Server heartbeat for " + hostname);

                // Sanity check on the last timestamp, it should normally
                // be at least as long as the service interval, if not then there
                // is probably another JVM running with the same host name.
                // If we detect that should we could shut this one down...

                Date last = server.getHeartbeat();
                if (last != null) {
                    // Due to timing jitter with Servicer wake up calls 
                    // require that we be well early of the expected time, occaionslly
                    // this early by only a few msecs
                    int jitter = 100;
                    Date expected = new Date(last.getTime() + (1000 * _cycleSeconds) - jitter);
                    if (expected.after(now)) {
                        log.warn("Early heartbeat for server " + hostname);
                        log.warn("Last: " + Util.dateToString(last));
                        log.warn("Now: " + Util.dateToString(now));
                        log.warn("Expected: " + Util.dateToString(expected));
                    }
                }
            }

            try {
                server.setHeartbeat(now);
                server.setInactive(false);

                // determine if we're actually running the Request service
                Environment env = Environment.getEnvironment();
                Service svc = env.getService(RequestService.NAME);
                if (svc != null && svc.isStarted())
                    server.put(Server.ATT_REQUEST_SERVICE_STARTED, "true");
                else
                    server.remove(Server.ATT_REQUEST_SERVICE_STARTED);

                context.saveObject(server);
                context.commitTransaction();
            }
            catch (Throwable t) {
                // In theory if we've got several JVMs comming up on the
                // same host creating a new Server and they didn't set iiq.hostname, 
                // we could get unique constraint violations on the commit.  There 
                // isn't a good recovery mode for this, they must set iiq.hostname
                // for stable operation.  Log the error and continue.
                log.error("Unable to update server heartbeat for: " + hostname);
                log.error(t);
            }
        }

    } // HeartbeatThread


}
