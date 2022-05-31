/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * This is the "root" class of the IIQ server, it starts life being
 * created by Spring and recieves handles to various objects also created
 * from the Spring config file.  When Spring finally "starts" it, 
 * it does a few core initialization steps like preparing the first 
 * SailPointContext and starting the system services.  It then is
 * used to gain access to the Spring created objects by other system
 * objects that aren't created by Spring.
 * 
 * Author: Jeff
 * 
 * Part of the motivation for this was to avoid having to copy
 * a bunch of stuff when we cloned an InternalContext, but the
 * prime motivator was needing more control over the operation of
 * the Quartz scheduler and Request Processor threads.  We can't
 * let Spring unconditionally start the scheduler and it is awkward
 * to conditionalize this with different Spring config files
 * for each of our applications (mainly the webapp and the console).
 *
 * You can set the scheduler suppression properties in the 
 * Spring initialization file but it is more common right now
 * to have the applications save these preferences in static fields
 * of the sailpoint.spring.SpringStarter class.  See commentary in
 * that class for why this is so.  
 *
 * Here we don't care which method is used.  We'll pay attention
 * to our local fields as well as the SpringStarter fields.
 * 
 * TODO: 
 *
 * See if we can rewire the stuff being done in InternalContext.prepare
 * down here so we don't have to be dependent on interaction with
 * SailPointFactory.  
 *
 */

package sailpoint.server;

import java.util.List;

import javax.sql.DataSource;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hibernate.SessionFactory;

import org.hibernate.internal.SessionFactoryImpl;
import org.hibernate.service.ServiceRegistry;
import sailpoint.api.PersistenceManager;
import sailpoint.api.SailPointContext;
import sailpoint.api.SailPointFactory;
import sailpoint.object.RuleRunner;
import sailpoint.persistence.ExtendedAttributeUtil;
import sailpoint.plugin.ConcurrentMapCache;
import sailpoint.plugin.PluginsCache;
import sailpoint.service.plugin.PluginsService;
import sailpoint.scheduler.QuartzSchedulerStarter;
import sailpoint.tools.GeneralException;

public class Environment {

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////
    
    private static final Log log = LogFactory.getLog(Environment.class);

    /**
     * Handle to the singleton so we can get to it after Spring has gone.
     */
    static Environment _singleton;

    /**
     * Set by Spring.
     * Direct handle to a DataSource for the underlying database.
     * Used in a few cases where we need to go around Hibernate.
     * Try not to over use this.
     */
    DataSource _dataSource;

    /**
     * Set by Spring.
     * The SessionFactory used by Hibernate, as configured by Spring.
     */
    SessionFactory _sessionFactory;
    
    /**
     * Set by Spring.
     * The implementation of the persistent storage manager.
     * This is normally a ClassPersistenceManager which delegates
     * to HibernatePersistenceManager for all things except TaskSchedule
     * which goes to QuartzPersistenceManager.
     */
    PersistenceManager _persistenceManager;

    /**
     * Set by Spring.
     * Handle to the rule runner.
     * This is always a BSFRuleRunner, I'm not why we would want this
     * configurable.
     */
    RuleRunner _ruleRunner;
    
    /**
     * Set by Spring.
     * An object that holds the org.quartz.Scheduler created by
     * Spring.  This is an old interfac that we're still trying
     * to evolve away and use the new Service design.  We're keeping
     * this part around just for the Spring destroy-method that
     * it implements to shut down Quartz.  This should eventually
     * be handled by Environment.stop().
     */
    QuartzSchedulerStarter _taskScheduler;

    /**
     * System housekeeping thread for background services.
     */
    Servicer _servicer;

    /**
     * Service that manages cache invalidation.
     * This is a funny one because there is a circular dependency between
     * it and InternalContext.  InternalContext needs to use it to register
     * some caches, but we can't read ServiceDefinitions until InternalContext
     * has finished initialzing.  We create one of these unconditionally in 
     * in memory before initializing InternalContext.  This will be installed
     * inside Servicer but we need it so freqently keep a cached copy out here
     * so we don't have to search the Service list.
     */
    CacheService _cacheService;

    /**
     * The brand identifier for this instance.
     */
    String _branding = "iiq";

    /**
     * Set by Spring. Contains the global plugins configuration, including
     * whether or not plugins are enabled and the plugins data source.
     */
    PluginsConfiguration _pluginsConfiguration;

    /**
     * The plugins cache that is used to serve plugins.
     */
    PluginsCache _pluginsCache;

    //////////////////////////////////////////////////////////////////////
    //
    // Constructor/Properties
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Provide an easy way to get to this.  Not for general use.
     * Unlike most singletons we don't bootstrap one, the only thing
     * allowed to construct one of these is Spring, we simply remember it.
     */
    static public Environment getEnvironment() {
        return _singleton;
    }

    public Environment() {
        log.info("Created Environment");
        _singleton = this;

        // Install the service manager immediately wo we can
        // pass some Spring initialization options to it
        _servicer = new Servicer();

        // set the plugins cache
        _pluginsCache = new ConcurrentMapCache();
    }

    public void setDataSource(DataSource ds) {
        _dataSource = ds;
    }

    /** 
     * jsl - changed the name of this so we can more easily find out
     * where it is called.  Reports have a bazilion calls to getDataSource()
     */
    public DataSource getSpringDataSource() {
        return _dataSource;
    }

    /**
     * Hack to get the number of active connections from DBCP.
     * _dataSource is usually an org.apache.commons.dbcp.BasicDataSource
     * unless iiqBeans.xml was configured to use a JNDI data source.
     */
    public int getActiveConnections() {
        int active = 0;

        if (_dataSource instanceof org.apache.commons.dbcp2.BasicDataSource) {
            active = ((org.apache.commons.dbcp2.BasicDataSource)_dataSource).getNumActive();
        }

        return active;
    }


    public void setSessionFactory(SessionFactory sf) {
        _sessionFactory = sf;
    }

    public SessionFactory getSessionFactory() {
        return _sessionFactory;
    }

    /**
     * With Spring, the SessionFactory's ServiceRegistry is the SpringRegistry, and the parent is the HibernateRegistry
     * @return
     */
    public ServiceRegistry getServiceRegistry() {
        ServiceRegistry reg = null;
        SessionFactoryImpl factory = (SessionFactoryImpl)getSessionFactory();
        if (factory != null) {
            ServiceRegistry springRegistry = factory.getServiceRegistry();
            if (springRegistry != null) {
                reg = springRegistry.getParentServiceRegistry();
            }
            if (reg == null) {
                //No parent, return child
                return springRegistry;
            }
        }
        return reg;
    }

    public void setPersistenceManager(PersistenceManager pm) {
        _persistenceManager = pm;
    }

    public PersistenceManager getPersistenceManager() {
        return _persistenceManager;
    }

    public void setRuleRunner(RuleRunner rr) {
        _ruleRunner = rr;
    }

    public RuleRunner getRuleRunner() {
        return _ruleRunner;
    }

    public void setTaskScheduler(QuartzSchedulerStarter s) {
        _taskScheduler = s;
    }

    public QuartzSchedulerStarter getTaskScheduler() {
        return _taskScheduler;
    }

    public String getBranding() {
        return _branding;
    }
    
    public void setBranding(String brand) {
        _branding = brand;
    }
    
    public void setTaskSchedulerHosts(String csv) {
        // forward to Servicer which handles all Service startup options
        _servicer.setTaskSchedulerHosts(csv);
    }

    public void setRequestSchedulerHosts(String csv) {
        // forward to Servicer which handles all Service startup options
        _servicer.setRequestSchedulerHosts(csv);
    }

    public Servicer getServicer() {
        return _servicer;
    }

    public CacheService getCacheService() {
        return _cacheService;
    }

    /**
     * Convenience method so we don't have to expose Servicer everywhere.
     */
    public Service getService(String name) {
        return _servicer.getService(name);
    }

    public List<Service> getServices() {
        return _servicer.getServices();
    }

    public Service getTaskService() {
        return getService(TaskService.NAME);
    }

    public Service getRequestService() {
        return getService(RequestService.NAME);
    }

    /**
     * Gets the plugins configuration that is bootstrapped by Spring.
     *
     * @return The plugins configuration.
     */
    public PluginsConfiguration getPluginsConfiguration() {
        return _pluginsConfiguration;
    }

    /**
     * Used by Spring to set the plugins configuration.
     *
     * @param pluginsConfiguration The plugins configuration.
     */
    public void setPluginsConfiguration(PluginsConfiguration pluginsConfiguration) {
        _pluginsConfiguration = pluginsConfiguration;
    }

    /**
     * Gets the plugins cache.
     *
     * @return The plugins cache.
     */
    public PluginsCache getPluginsCache() {
        return _pluginsCache;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Spring Start/Stop
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Eventually called during Spring initialization when we're
     * sure everything has been wired together and we're ready to bring
     * up the system.  This is not the Spring init-method on the
     * Environment bean, we are called by the init-method on SystemStarter.
     *
     * The schedulers can be suppressed using two methods, flags on this
     * class set in the spring config and flags set in SpringStarter
     * at runtime by the application.  See SpringStarter for more 
     * background.
     *
     * This also serves as a convenient point to do some housekeeping
     * like finding dangling TaskResults from crashed executors that
     * think they're still executing.  
     */
    public void start() throws GeneralException {

        log.info("Starting Environment");


        // Cache a handle to the _cacheService, see comments
        // above _cacheService for why this one is weird
        _cacheService = (CacheService)_servicer.getService(CacheService.NAME);

        // This is our first official use of SailPointFactory, 
        // It will call the InternalContext.prepare method to load
        // some caches.  !! Try to retool this so we are in control 
        // of the caches, it would make the control flow a lot cleaner.

        log.info("Creating the first SailPointContext");
        SailPointContext con = SailPointFactory.createContext("system");
        try {
            // do consistency checking on the mapping files
            ExtendedAttributeUtil.validateAndLog(con);

            // initialize plugins if enabled before starting services since
            // a service definition could be shipped with a plugin
            if (isPluginsEnabled()) {
                log.info("Initializing plugins");
                PluginsService pluginsService = new PluginsService(con);
                pluginsService.initializePlugins(_pluginsCache);
            }

            // start the services
            log.info("Starting services");
            _servicer.start(con);
        }
        finally {
            if (con != null)
                SailPointFactory.releaseContext(con);
        }

        log.info("Environment started");
    }

    /**
     * Called by Spring during shutdown.
     */
    public void stop() {
    
        _servicer.terminate();

        long start = System.currentTimeMillis();
        try {
            _servicer.join(2000);
            log.debug("Waited " + (System.currentTimeMillis() - start) +
                      " ms for thread to stop: " + _servicer + "; state = " +
                      _servicer.getState());
        }
        catch (InterruptedException e) {
            // ignore
            log.info("Interrupted while waiting for servicer.");
        }

        _pluginsCache.clear();
    }

    /**
     * Determines if plugins are enabled.
     *
     * @return True if enabled, false otherwise.
     */
    private boolean isPluginsEnabled() {
        return _pluginsConfiguration != null && _pluginsConfiguration.isEnabled();
    }
    
}
