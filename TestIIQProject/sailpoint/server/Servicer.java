/* (c) Copyright 2011 SailPoint Technologies, Inc., All Rights Reserved. */
/*
 * A singleton thread launched by all IIQ instances to manage Services.
 * Services are similar to background tasks that do various system 
 * housekeeping such as cache invalidation and request processing.
 *
 * Author: Jeff
 *
 * This was added in 5.2 so we could do periodic refresh of a few
 * object caches in a more controlled way than CachedObject had been doing.
 * We have since evolved this into a general way of defining background
 * houskeeping threadss.  There are two types of services: periodic and 
 * continuous.  Periodic services run at regular intervals, continuous
 * services typically launch their own child threads are are doing something
 * all the time.
 *
 * SCHEDULER HOST POOL
 *
 * NOTE: THis section is now obsolete with the introduction of
 * the Task and Request ServiceDefinitions.
 * 
 * In 3.0 we added the rough ability to control which hosts are used
 * for running scheduled tasks.  This isn't true host affinity
 * where a task can name the specific machine that is to run it, 
 * but it solves the most important problem which is getting
 * tasks to run outside of the pool of machines used to 
 * service web UI requests.
 *
 * This is optional and typically specified in the iiq.properties
 * file when you're setting up database connectivity.  The properties are:
 *
 *      environment.taskSchedulerHosts
 *      environemnt.requestSchedulerHosts
 *
 * The values are a CSV containing host names.  The host names
 * must be those returned by the Util.getHostName method, 
 * which uses InetAddress.getLocalHost and InetAddress.getHostName.
 * If either list is non-empty then we only start the scheduler on the
 * named hosts.  Note that this is combined with the two suppression 
 * flags.  If the local host appears in the list but the application
 * sets the scheduler suppression flags, the scheduler will still be 
 * suppressed.
 *
 */

package sailpoint.server;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.SailPointContext;
import sailpoint.api.SailPointFactory;
import sailpoint.object.Configuration;
import sailpoint.object.Filter;
import sailpoint.object.Plugin;
import sailpoint.object.QueryOptions;
import sailpoint.object.SailPointObject;
import sailpoint.object.ServiceDefinition;
import sailpoint.plugin.PluginsUtil;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;

public class Servicer extends Thread {

    //////////////////////////////////////////////////////////////////////  
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////  

    private static Log log = LogFactory.getLog(Servicer.class);

    public static final String SP_CONTEXT_NAME = "Services";

    /**
     * An optional CSV of host names representing hosts that are
     * allowed to run a task scheduler.  This is the orignal way
     * of doing host restirction that we support for backward 
     * compatibility, but new deployments should configure the
     * hosts in the ServiceDefinition for the "Task" service.
     * This is set indirctly by Spring.
     */
    static String _taskSchedulerHosts;

    /**
     * An optional CSV of host names representing hosts that are
     * allowed to run a request scheduler.  Like _taskSchedulerHosts,
     * this is retained for backward compatibility but new 
     * deployments should configure the ServiceDefinition for the
     * "Request" service.
     */
    static String _requestSchedulerHosts;

    /**
     * An optional list of service names that should be
     * allowed to auto-start in this instance.  This is typically
     * set by the command like applications like the console to 
     * minimize the services from running since
     * consoles normally do not have host restrictions.
     *
     * A null value means allow all services to auto-start
     */ 
    static Set<String> _whitelistedServices;

    /**
     * The number of seconds to pause between processing cycles.
     * Can be configured in the system configuration object.
     */
    int _cycleSeconds = 1;

    /**
     * The number of seconds between checks for ServiceDefinition
     * updates.  This shouldn't be too fast since these almost
     * never change and we don't any more gratuituous queries.
     */
    int _definitionRefreshSeconds = 60;

    /**
     * A control field, set to halt the thread.
     */
    boolean _terminate;

    /**
     * Flag set at the bottom of every cycle, used by ping() to
     * determine if the thread is still alive.
     */
    boolean _ping;

    /**
     * The registered services.  Not all of these may be running.
     */
    List<Service> _services;

    /**
     * Name of a service we were asked to run immediately.  
     */
    String _forcedExecution;

    /**
     * List of task scheduler hosts from the ServiceDefinition combined
     * with older Spring injected hosts.  Can't overite _taskSchedulerHosts
     * or else we'll never be able to reconfigure the ServiceDefinition and 
     * remove hosts.  
     */
    String _mergedTaskHosts;

    /**
     * List of request scheduler hosts from the ServiceDefinition combined
     * with older Spring injected hosts.
     */
    String _mergedRequestHosts;

    //////////////////////////////////////////////////////////////////////  
    //
    // Static Configuration Interface
    //
    //////////////////////////////////////////////////////////////////////  

    static private String[] minimizedServices = new String[] { "Cache" };
    
    /**
     * This is a lock that we acquire while processing services so that 
     * the terminate method can know when Services have finished shutting down.
     * @ignore
     */
    static private final Lock PROCESSING_LOCK = new ReentrantLock();

    /**
     * Called indirectly by Environment when there is a 
     * taskSchedulerHosts entry in the iiq.properties file.
     * The properties file is read during Spring startup
     */
    static public void setTaskSchedulerHosts(String csv) {
        _taskSchedulerHosts = csv;
    }

    /**
     * Called indirectly by Environment when there is a 
     * requestSchedulerHosts entry in the iiq.properties file.
     * The properties file is read during Spring startup
     */
    static public void setRequestSchedulerHosts(String csv) {
        _requestSchedulerHosts = csv;
    }

    /**
     * Called by indirectly by SpringStarter, usually
     * from the main() method of some applications, that
     * want to control which automatic services are allowed
     * to auto-start.
     * @param names the set of services to be whitelisted
     */
    static public void setWhitelistedServices(String[] names) {
        minimizeServices();
        _whitelistedServices.addAll(Arrays.asList(names));
    }

    /**
     * Add the given service to the whitelist (of services
     * to be allowed to auto-start)
     *
     * @param name the service to add to the whitelist
     */
    static public void addWhitelistedService(String name) {
        if (_whitelistedServices == null) {
            minimizeServices();
        }
        if (!_whitelistedServices.contains(name)) {
            _whitelistedServices.add(name);
        }
    }

    /**
     * @return true if the service by the given name is whitelisted
     */
    static public boolean isWhitelistedService(String name) {
        boolean isWhiteListed = false;
        if (_whitelistedServices != null) {
            isWhiteListed = _whitelistedServices.contains(name);
        }
        return isWhiteListed;
    }

    /**
     * Set the whitelist of services (to be allowed to auto-start) to the
     * bare minimum, currently that means only Cache service
     */
    static public void minimizeServices() {
        _whitelistedServices = new HashSet<String>(Arrays.asList(minimizedServices));
    }


    //////////////////////////////////////////////////////////////////////  
    //
    // Constructor/Thread Interface
    //
    //////////////////////////////////////////////////////////////////////  
    
    /**
     * This should only be constructured once by Environment.
     * A SailPointContext is not available, must defer configuration until
     * the start() method is called.  This is due to an obscure
     * circular dependency between InternalContext and Servicer.
     * InternalContext needs to register the CacheService during its prepare()
     * method but we have to create the Servicer before that happens.
     */
    public Servicer() {

        // set a name for this thread
        super("ServerThread");

        // make sure that this thread does not block the JVM from exiting
        setDaemon(true);

        // It is CRITICAL that the Heartbeat service run reliably
        // or else we may think the host is down.  Make sure this
        // thread cannot starve. 
        // UPDATE: Now that we have a separate HeartbeatThread this
        // shoudln't matter.
        setPriority(Thread.MAX_PRIORITY);

        // bootstrap the CacheService for InternalContext
        ServiceDefinition def = new ServiceDefinition();
        def.setName(CacheService.NAME);

        CacheService svc = new CacheService();
        // mark it started since we don't formally install it below
        svc.setStarted(true);

        add(svc);
    }

    /**
     * Configure the services and start the master service thread.
     * This is called by Environment after a SailPointContext is possible.
     */
    public void start(SailPointContext con) 
        throws GeneralException {

        // read ServiceDefinitions and decide which ones to run
        configure(con);

        // start our periodic service notification thread
        start();
    }

    /**
     * Configure the services.  All ServiceDefinitions are loaded and installed.
     * Configuration options determine whether they are started.
     */
    public void configure(SailPointContext con) 
        throws GeneralException {

        // TODO: Get cycle time, some of the registered servivces?
        log.info("Servicer initializing");

        List<ServiceDefinition> definitions = ServicerUtil.getDefinitions(con);
        for (ServiceDefinition def : definitions) {
            // always configure and add to the list
            install(con, def);
        }

        // then auto start the ones we can
        if (_services != null) {
            for (Service service : _services) {
                if (isAutoStart(con, service)) {
                    log.info("Auto starting service: " + service.getName());
                    service.start();
                }
            }
        }
    }

    /**
     * Configures a single service given the definition. The service is
     * started if configuration allows.
     *
     * @param context The context.
     * @param serviceDefinition The service definition.
     * @throws GeneralException
     */
    public void configure(SailPointContext context, ServiceDefinition serviceDefinition)
        throws GeneralException {

        log.info("Installing service: " + serviceDefinition.getName());

        Service service = install(context, serviceDefinition);
        if (service != null && isAutoStart(context, service)) {
            log.info("Auto starting service: " + service.getName());
            service.start();
        }
    }


    /**
     * Configure one service from a definition.
     */
    private Service install(SailPointContext con, ServiceDefinition def)
        throws GeneralException {

        Service service = null;

        try {
            service = instantiateService(con, def);
            if (service == null) {
                log.error("Unable to install service " + def.getName() + ". Could not instantiate executor " + def.getExecutor());
                return service;
            }

            service.setDefinition(def);
            add(service);

            // we have historically done this, it should really
            // be Service.configure()'s responsibility but I don't
            // want to change all those now
            if (def.getInterval() != 0)
                service.setInterval(def.getInterval());

            // let the service decide some things
            service.configure(con);
        }
        catch (Throwable t) {
            log.error("Unable to install service: " + def.getName(), t);
        }

        return service;
    }

    /**
     * Uninstalls a service. If the service is started, sends a terminate
     * request and then pings it every so often to check for termination
     * until the timeout is reached. The service will be removed once it
     * has terminated or after the timeout has elapsed.
     *
     * @param serviceDefinition The service definition.
     * @param timeout The amount of time to let a service terminate in seconds.
     */
    public void uninstall(ServiceDefinition serviceDefinition, int timeout) {
        Service service = getService(serviceDefinition.getName());
        if (service == null) {
            log.warn("Tried to uninstall non-existent service: " + serviceDefinition.getName());
        } else {
            try {
                if (service.ping()) {
                    service.terminate();

                    // if still alive sleep and wait until service termination
                    // or timeout is reached
                    if (service.ping()) {
                        boolean terminated = false;

                        // sleeping a fifth of a second so loop five times the timeout value
                        try {
                            for (int i = 0; i < timeout * 5; ++i) {
                                Thread.sleep(200);

                                // check to see if the service has terminated
                                if (!service.ping()) {
                                    terminated = true;
                                    break;
                                }
                            }
                        } catch (InterruptedException e) {
                            log.info("Interrupted while uninstalling service: " + serviceDefinition.getName());
                            Thread.currentThread().interrupt();
                        }

                        if (!terminated) {
                            log.warn("Service did not terminate before timeout: " + serviceDefinition.getName());
                        }
                    }
                }

                // service may or may not have stopped but we did our
                // best so go ahead and remove it from the list
                _services.remove(service);
            } catch (Throwable e) {
                log.error("Unable to uninstall service" + serviceDefinition.getName(), e);
            }
        }
    }

    /**
     * Instantiates the service class specified in the definition.
     *
     * @param context The context.
     * @param serviceDefinition The service definition.
     * @return The service instance.
     * @throws GeneralException
     */
    private Service instantiateService(SailPointContext context, ServiceDefinition serviceDefinition)
        throws GeneralException {

        String executor = serviceDefinition.getExecutor();
        if (executor == null) {
            // they all follow this convention
            executor = "sailpoint.server." + serviceDefinition.getName() + "Service";
        }

        log.debug("Attempting to load Service class: " + executor);

        // if the def contains a plugin name then load the executor from the
        // plugin class loader
        String pluginName = serviceDefinition.getString(ServiceDefinition.ARG_PLUGIN_NAME);
        if (Util.isNullOrEmpty(pluginName)) {
            // allow custom dynamically loaded services
            return (Service) DynamicLoader.instantiate(context, executor);
        } else {
            return PluginsUtil.instantiate(pluginName, executor, Plugin.ClassExportType.SERVICE_EXECUTOR);
        }
    }

    /**
     * Add a service to the list.
     */
    public void add(Service service) {
        if (service != null) {
            log.info("Adding service: " + service.getName());
            if (_services == null) {
                // rather than synchronize for every traversal and update of the services list use
                // a CopyOnWriteArrayList, this class has some overhead when the list is modified
                // but it should provide better performance than synchronizing in our case since
                // it will be traversed a very large amount more than written to, it will only be
                // written to at system startup and possibly when a plugin has a state change
                _services = new CopyOnWriteArrayList<>();
            }
            _services.add(service);
        }
    }

    /**
     * Determine if this service should be automatically started.
     * This is quite kludgey due to support for old host restriction
     * options and the need to restrict services within certain
     * command line applications.
     *
     * If the old taskSchedulerHosts and requestSchedulerHosts
     * options are set, they have priority over what is in the
     * ServiceDefinition, though I suppose we could OR them.
     *
     * @param context
     * @param service the Service to be examined for auto start
     * @return true if the specified service should be auto-started, otherwise false
     */
    boolean isAutoStart(SailPointContext context, Service service) {

        ServiceDefinition def = service.getDefinition();
        boolean runit = true;

        // if no definition, assume run, currently only CacheService
        if (def != null) {

            String serviceName = def.getName();

            // First, check if the Server has explictly included or excluded this service
            Boolean allowedByServer = null;
            try {
                allowedByServer = ServicerUtil.isServiceAllowedByServer(context, serviceName, Util.getHostName());
            } catch (GeneralException e) {
                if (log.isDebugEnabled()) {
                    log.debug("Failed to fetch Server for local host", e);
                }
            }

            if (allowedByServer != null) {
                // the service was indeed explicitly included or excluded by the Server
                runit = allowedByServer;
                if (!runit && log.isDebugEnabled()) {
                    String msg = new Message(MessageKeys.WARN_SERVICE_EXCLUDED_BY_HOST, serviceName, Util.getHostName()).getLocalizedMessage();
                    log.debug(msg);
                }
            }
            else {
                // migrate old options for specifying hosts
                // also check two Eclipose debugging hacks that disable
                // background things
                if (TaskService.NAME.equals(def.getName())) {
                    if (log.isInfoEnabled()) {
                        log.info("Initial task hosts: " + def.getHosts());
                        log.info("Spring task hosts: " + _taskSchedulerHosts);
                    }

                    def.addHosts(_taskSchedulerHosts);
                    // save the merged list to return to the debug page
                    _mergedTaskHosts = def.getHosts();
                    if (log.isInfoEnabled()) {
                        log.info("Merged task hosts: " + _mergedTaskHosts);
                    }

                    // an old hack for Eclipse debugging
                    if (System.getProperty("iiq.noTaskScheduler") != null)
                        runit = false;
                }

                if (RequestService.NAME.equals(def.getName())) {
                    def.addHosts(_requestSchedulerHosts);
                    _mergedRequestHosts = def.getHosts();
                    if (System.getProperty("iiq.noRequestScheduler") != null)
                        runit = false;
                }

                if (runit) {
                    // kludge for 22126
                    // The Request service always needs to run so it can
                    // process requests targeted at a specific host.  The host
                    // list on the ServiceDefinition tells it whether it can
                    // process other anonymous requests.  This is called "restricted"
                    // mode.  It would bet best if the ServiceDefinition could
                    // say this but that requires another column that I don't want to mess with.
                    if (!RequestService.NAME.equals(serviceName)) {
                        runit = def.isThisHostAllowed();
                        if (!runit && log.isDebugEnabled()) {
                            // Log an informational message if we're not going to start the service to
                            // help customers detect unintentional mismatches between their specified hosts
                            // and their current host.
                            String msg;
                            String hosts = def.getHosts();
                            if (Util.isNullOrEmpty(hosts)) {
                                msg = new Message(MessageKeys.WARN_SERVICE_NO_HOST, serviceName, Util.getHostName()).getLocalizedMessage();
                            } else {
                                msg = new Message(MessageKeys.WARN_SERVICE_INVALID_HOST, serviceName, def.getHosts(), Util.getHostName()).getLocalizedMessage();
                            }
                            log.debug(msg);
                        }
                    }
                }
            }


            // not so fast!
            // Console uses _whitelistedServices to control which services to allow
            if (runit && _whitelistedServices != null) {
                boolean isWhitelisted = _whitelistedServices.contains(serviceName);
                if (!isWhitelisted) {
                    runit = false;
                    if (log.isDebugEnabled()) {
                        String msg = new Message(MessageKeys.WARN_SERVICE_SUPPRESSED, serviceName).getLocalizedMessage();
                        log.debug(msg);
                    }
                }
            }
        }

        return runit;
    }

    /**
     * Return the list of all services.
     */
    public List<Service> getServices() {
        return _services;
    }

    /**
     * Lookup a service by name.
     */
    public Service getService(String name) {
        Service found = null;
        if (name != null && _services != null)  {
            for (Service service : _services) {
                // make this case insensntive for the console
                if (name.equalsIgnoreCase(service.getName())) {
                    found = service;    
                    break;
                }
            }
        }
        return found;
    }

    /**
     * Called by DebugBean to get the list of hosts allowed to run
     * the task scheduler.  Note that we return _mergedTaskHosts
     * which has a combination of the older _taskSchedulerHosts
     * and the newer ServiceDefinition.hosts.  This makes 
     * taskSchedulerHosts property unusual in that the setter and
     * getter don't do touch the same field, but only Spring and the
     * debug page use them.
     */
    public String getTaskSchedulerHosts() {

        return _mergedTaskHosts;
    }

    /**
     * Called by DebugBean to get the list of hosts allowed to run
     * the request scheduler.
     */
    public String getRequestSchedulerHosts() {

        return _mergedRequestHosts;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Main Loop
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Thread main loop.    
     */
    public void run() {

        log.info("Starting service thread");

        int definitionAge = 0;

        while (!_terminate) {
            try {
                log.debug("Starting service thread cycle");

                boolean refreshDefinitions = false;
                if (definitionAge >= _definitionRefreshSeconds) {
                    refreshDefinitions = true;
                    definitionAge = 0;
                }

                try {
                    log.debug("Starting to process services");
                    // Acquire the lock while the services run so that the terminate
                    // method can be aware of when its work is done
                    PROCESSING_LOCK.lock();
                    processServices(refreshDefinitions);
                    log.debug("Finished processing services");
                } finally {
                    PROCESSING_LOCK.unlock();
                }

                // put the sleep after processing so we can 
                // interrupt out of sleep and process immediately
                try {
                    Thread.sleep(_cycleSeconds * 1000);
                } 
                catch (InterruptedException ie) {
                    // this is normal if we set _forcedExecution
                    log.info("Continuing after main cycle interruption");
                }

                definitionAge += _cycleSeconds;

                // if we had a ping request, turn it off
                _ping = true;
            }
            catch (InterruptedException ie) {
                if (log.isInfoEnabled()) {
                    log.info("Process Interrupted." + ie);
                }
            }
            catch (Throwable t) {
                log.error("Exception during cycle: " + t.toString(), t);
            }
        }

        // Technically, we should loop over the Services and wait for them to finish too
        // so that the Thread.join() that Environment calls on us can be 
        // sure that the RequestProcessor thread has finished.  May need another
        // Service interface for that though...

        log.info("Stopping service thread");
    }

    /**
     * Send a signal to the master thread asking it to terminate.
     */
    public void terminate() {

        _terminate = true;

        // let the services that maintian their own threads know
        if (_services != null) {
            for (Service service : _services) {
                try {
                    service.terminate();
                }
                catch (Throwable t) {
                    log.error("Unable to terminate service: " + service.getName(), t);
                }
            }
        }

        interrupt();

        // Make sure that ths services have finished terminating before we return
        PROCESSING_LOCK.lock();
        PROCESSING_LOCK.unlock();
        
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

    /**
     * Force an immediate (well, as soon as possible) execution
     * of the named service.  This is only used in special cases
     * like InternalContext.refreshObjectConfigs.  Ideally this
     * should be multi-threaded and maintain some kind of queue.
     *
     * jsl - this hasn't been used for awhile, if we can't find
     * a need remove this...
     */
    /*
    public void execute(String name) {

        // leave this here so we know it's a targeted execution
        // really needs to be more of an event queue
        _forcedExecution = name;

        // break out of the wait
        this.interrupt();
    }
    */

    /**
     * Called by InternalContext when it commits a change to 
     * the sytsem configuration.  We have historically had a hook
     * here to reconfigure the Services so they can immediately
     * respond to config changes imported by the unit tests.
     * Schedulers may change cycle times and other things.
     *
     * jsl - I'm actually not liking this any more since
     * some services may have expensive configuration.  But I guess
     * that system config changes don't happen often.
     *
     * This is also obviously not cluster aware, if this needs to be
     * supported it needs to broadcast machine-specific service Requests
     * that can be picked up by each machine.
     */
    public void reconfigure(SailPointContext context, Configuration config) {

        // We actually don't need the Configuration passed in.  There
        // aren't any global settings any more and the Services will
        // read it for themselves.
        try {
            if (log.isInfoEnabled())
                log.info("Servicer reconfiguring after system configuration change");

            if (_services != null) {
                for (Service service : _services)
                    service.configure(context);
            }
        }
        catch (Throwable t) {
            log.error("Unable to reconfigure Services after Configuration change", t);
        }
    }

    /**
     * Called by InternalContext when it commits a change to 
     * a ServiceDefinition.  Like the hook for the system Configuration
     * have the Service reconfigure itself so we can immediately
     * respond to changes imported by the unit tests.
     *
     * Besides Service specific configuraiton, we also pay attention
     * to the host assignments which can start services.
     */
    public void reconfigure(SailPointContext context, ServiceDefinition def) {

        try {
            if (_services != null) {
                for (Service service : _services) {
                    if (service.getName().equals(def.getName())) {
                        if (log.isInfoEnabled())
                            log.info("Servicer reconfiguring " + def.getName() + 
                                     " service after ServiceDefinition change");
                        reconfigure(context, def, service);
                    }
                }
            }
        }
        catch (Throwable t) {
            log.error("Unable to reconfigure Service " + def.getName() + 
                      " after ServiceDefinition change", t);
        }

    }

    //////////////////////////////////////////////////////////////////////  
    //
    // Service Execution
    //
    //////////////////////////////////////////////////////////////////////  

    /**
     * Process all services, executing the ones that are ready.
     */
    private void processServices(boolean refreshDefinitions) throws InterruptedException {
        
        SailPointContext context = null;
        try {
            if (Thread.interrupted()) {
                throw new InterruptedException();
            }
            context = SailPointFactory.createContext(SP_CONTEXT_NAME);

            if (refreshDefinitions) {
                applyServerOverrides(context);
                refreshDefinitions(context);
            }

            if (_forcedExecution != null) {
                // ?? should this pay attention to _started?
                Service service = getService(_forcedExecution);
                if (service != null) {
                    log.info("Forcing execution of service: " +
                             service.getName());
                    execute(context, service);
                }
                else {
                    log.error("Unable to force execution of unknown service: " +
                              _forcedExecution);
                }
                _forcedExecution = null;
            }
            else if (_services != null) {

                List<Service> ready = new ArrayList();

                for (Service service : _services) {
                    boolean runit = false;

                    if (service.isStarted()) {

                        Date last = service.getLastExecute();
                        if (last == null) {
                            // assume these always start immediately, 
                            // maybe we should stagger these?
                            runit = true;
                        }
                        else {
                            int interval = service.getInterval();
                            Date now = new Date();
                            Date then = new Date(last.getTime() + (interval * 1000));
                            runit = (now.compareTo(then) >= 0);
                        
                            if (!runit && log.isDebugEnabled())
                                log.debug("Service " + service.getName() + 
                                          " waiting until " + Util.dateToString(then));
                        }
                    }

                    if (runit)
                        ready.add(service);
                }

                // kludge: do the high priority ones first, currently
                // this is just Heartbeat which has to run on a very regular
                // cycle and not get delayed by other service execution methods
                for (Service svc : ready) {
                    if (Thread.interrupted()) {
                        throw new InterruptedException();
                    }
                    if (svc.isPriority())
                        execute(context, svc);
                }
                
                // and the common folk
                for (Service svc : ready) {
                    if (Thread.interrupted()) {
                        throw new InterruptedException();
                    }
                    if (!svc.isPriority())
                        execute(context, svc);
                }

            }
        }
        catch (InterruptedException ie) {
            if (log.isInfoEnabled()) {
                log.info("Servicer Interrupted");
            }
            throw ie;
        }
        catch (Throwable t) {
            log.error("Unable to initialize service context", t);
        }
        finally {
            try {
                if (context != null)
                    SailPointFactory.releaseContext(context);
            }
            catch (Throwable t) {
                // we tried, nothing we can do about it now
                log.error(t.getMessage(), t);
            }
        }
    }

    private void applyServerOverrides(SailPointContext context) {
        for (Service service : _services) {
            try {
                manageLife(context, service);
            }
            catch (Throwable t) {
                log.error("Exception during service lifeycle management", t);
            }
        }

    }
    /**
     * Execute the service, setting the start and end dates.
     */
    private void execute(SailPointContext context, Service service) 
        throws Exception {
        
        try {
            // don't let junk in the context leak
            // between services
            context.decache();

            service.setLastExecute(new Date());
            service.execute(context);
        }
        catch (Throwable t) {
            log.error("Unable to execute service: " + service.getName(), t);
        }
        finally {
            service.setLastEnd(new Date());
        }

        // TODO: make sure the execution time didn't exceed the interval?
        
    }

    /**
     * Refresh ServiceDefinitions.
     * Don't let this throw, so in case something is wrong we don't prevent
     * services from running.
     */
    private void refreshDefinitions(SailPointContext context) {
        
        try {
            for (Service service : _services) {

                boolean needsRefresh = false;
                ServiceDefinition def = service.getDefinition();
                if (def == null) {
                    // CacheService doesn't need a definition, though
                    // we should add one and move the CACHED_OBJECT_DURATION    
                    // from system config over there
                }
                else {
                    Date newmod = getModificationDate(context, def);

                    // we'll have already logged if newmod came back null
                    if (newmod != null) {
                        Date oldmod = def.getModified();
                        if (oldmod == null)
                            oldmod = def.getCreated();

                        needsRefresh = (oldmod == null || !oldmod.equals(newmod));
                    }

                    if (needsRefresh) {
                        if (log.isInfoEnabled()) 
                            log.info("Refreshing ServiceDefinition for " + def.getName());

                        def = context.getObjectByName(ServiceDefinition.class, def.getName());

                        reconfigure(context, def, service);
                    }
                }
            }
        }
        catch (Throwable t) {
            log.error("Exception during refreshDefinition", t);
        }
    }

    /**
     * Given a new ServiceDefinition, reconfigure the service and
     * check the start status.
     */
    private void reconfigure(SailPointContext context, ServiceDefinition def, Service service)
        throws GeneralException {

        service.setDefinition(def);
        service.configure(context);

        manageLife(context, service);
    }

    /**
     * If this service is allowed to run based on the ServiceDefinition and Server configuration,
     * and it is whitelisted to allow auto-start, then make sure it is running.  Otherwise, make
     * sure it isn't running - including suspending if it is running.
     * @param context
     * @param service the service to manage the life of
     * @throws GeneralException
     */
    public void manageLife(SailPointContext context, Service service) throws GeneralException {
        // if the hosts change, we may need to start or stop each service
        // !! what about manual start/stop?  In practice that only happens
        // in the console, should we try to remember manual control and not
        // track ServiceDefinition changes?
        if (isAutoStart(context, service)) {
            if (!service.isStarted()) {
                if (log.isInfoEnabled())
                    log.info("Host " + Util.getHostName() + " starting service " + service.getName() +
                            ", it is now enabled in the ServiceDefinition or Server");
                service.start();
            }
        }
        else if (service.isStarted()) {
            if (log.isInfoEnabled())
                log.info("Host " + Util.getHostName() + " stopping service " + service.getName() +
                        ", it is no longer allowed by the ServiceDefinition or Server");
            service.suspend();
        }
    }

    /**
     * Return the modification dates for one object.
     * Made a public utility method so CacheService and others can use it.
     */
    static public <T extends SailPointObject> Date getModificationDate(SailPointContext context,  
                                                                       T src) {

        Date result = null;

        QueryOptions ops = new QueryOptions();
        ops.add(Filter.eq("name", src.getName()));

        List<String> props = new ArrayList<String>();
        props.add("created");
        props.add("modified");

        try {
            Iterator<Object[]> it = context.search(src.getClass(), ops, props);
            if (it.hasNext()) {
                Object[] row = it.next();
                if (row == null)
                    log.error("Null row!");
                else {
                    // use mode date if we have it, fall back to create date
                    if (row.length > 1)
                        result = (Date)row[1];
                    if (result == null && row.length > 0)
                        result = (Date)row[0];
                }
            }

            // lower this from warn to info, this can happen
            // when running the console on a fresh database before
            // init.xml is imported
            if (result == null && log.isInfoEnabled())
                log.info("No modification date for: " + 
                         src.getClass().getSimpleName() + ":" + 
                         src.getName());
        }
        catch (Throwable t) {
            // shouldn't happen, leave the old one in the cache
            String msg = "Exception tring to get modification date for: " + 
                      src.getClass().getSimpleName() + ":" + 
                      src.getName();
            log.error(msg, t);
        }

        return result;
    }

}
