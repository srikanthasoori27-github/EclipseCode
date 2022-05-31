/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
/**
 * Utility class for lookup and caching of IntegrationConfigs.
 *
 * Author: Jonathan
 *
 */

package sailpoint.provisioning;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.SailPointContext;
import sailpoint.connector.Connector;
import sailpoint.connector.ConnectorFactory;
import sailpoint.object.Application;
import sailpoint.object.Bundle;
import sailpoint.object.Configuration;
import sailpoint.object.Filter;
import sailpoint.object.IntegrationConfig;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.QueryOptions;
import sailpoint.object.TargetSource;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.unstructured.TargetCollector;
import sailpoint.unstructured.TargetCollectorFactory;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;

/**
 * This class provides IntegrationConfig lookup methods.
 * Internally, it performs caching to improve the performance
 * of different types of IntegrationConfig searches.
 * 
 * It is expected that the IntegrationConfigFinder instance will NOT be held
 * for long periods of time. This class holds an instance of the IntegrationConfigCache
 * that is current at the time it's acquired. This allows the IntegrationConfigFinder
 * to maintain the same instance of the cache for the duration of the task for which
 * it is needed which is typically the compilation of a provisioning plan.
 */
public class IntegrationConfigFinder {

    private static Log log = LogFactory.getLog(IntegrationConfigFinder.class);

    private SailPointContext context;

    // An instance of the IntegrationConfigCache obtained at the first
    // call to getCache(). 
    private IntegrationConfigCache _cache;

    /**
    // ------------------------------------------------------------
    //
    //  Constructors
    //
    // ------------------------------------------------------------
    */

    public IntegrationConfigFinder(SailPointContext ctx) {
        this.context = ctx;
    }

    // ------------------------------------------------------------
    //
    //  Find Methods
    //
    // ------------------------------------------------------------

    /**
     * Gets the IntegrationConfig with the given name.
     * This will also search for applications that support provisioning, and generate
     * an IntegrationConfig if the name matches the application's name or the target
     * collector's name.
     *
     * It will generate an IntegrationConfig if the 'name' matches the application's
     * name or unstructured target collector name.
     *
     * @param name The name of the IntegrationConfig
     * @param appName The Application name
     * @return IntegrationConfig if found, else null
     */
    public IntegrationConfig getIntegrationConfig(String name, String appName) throws GeneralException{

        if ( null == name ) {
            return null;
        }

        IntegrationConfig config = getCache().getIntegration(name);

        if ( null == config ) {
            if ( null != appName ) {
                // CONSEALINK-886
                // If 'appName' is non null, meaning 'name' contains either 
                // target collector name or standard/managed integration
                // config name. If 'name' holds standard/managed integration
                // config name, then obtain IntegrationConfig for standard/managed
                // integration. Otherwise, obtain the IntegrationConfig for
                // a target collector.
                if (hasStandardIntegrationConfig(name) || hasManagedIntegrationConfig(name)) {
                    config = getAppIntegration(name);
                } else {
                    // Get an IntegrationConfig object for a target collector defined on application.
                    config = getCollectorIntegration(name, appName);
                }
            }
            else {
                // get Application IntegrationConfig.
                // In this case, 'name' parameter contains application name
                config = getAppIntegration(name);
            }
        }

        return config;
    }

    /**
     * Gets the IntegrationConfig to be used with the supplied request.
     * @param req The request from the provisioning plan
     * @return IntegrationConfig if found, or null.
     */
    public IntegrationConfig getResourceManager(ProvisioningPlan.AbstractRequest req) throws GeneralException {

        if ( null == req ) {
            return null;
        }

        String op = req.getOp() != null ? req.getOp().toString() : null;

        // get IntegrationConfig object for Application or for unstructured
        // target collector defined on Application
        return getIntegration(op,
                              req.getApplication(),
                              //get unstructured target collector name from 'req'
                              req.getTargetCollector(),
                              req.getType());
    }

    /**
     * Get an IntegrationConfig for the given operation and application
     * @param op The operation to search for.
     * @param applicationName Name of the application to search for.
     * @return IntegrationConfig if found, or null.
     */
    public IntegrationConfig getResourceManager(String op, String applicationName)
            throws GeneralException {

        if (applicationName == null)
            return null;

        return getIntegration(op, applicationName, null);
    }

    /**
     * Locate the IntegrationConfigs of IDM systems that manage
     * some aspect of the given role.  Usually there will be none or one,
     * but in complex environments we could have multiple integrations
     * handling different sets of resources.
     * @param role The role we're interested in
     * @return The list of IntegrationConfigs for this role
     */
    public List<IntegrationConfig> getRoleManagers(Bundle role)
            throws GeneralException {

        List<IntegrationConfig> configs = null;

        for (IntegrationConfig integ : getCache().getIntegrations()) {
            if (IntegrationUtil.isManagedRole(context, integ, role)) {
                if (configs == null)
                    configs = new ArrayList<IntegrationConfig>();
                configs.add(integ);
            }
        }

        return configs;
    }

    /**
     * Returns true if any of our IntegrationConfigs support the given
     * operation. Note that this will only search actual IntegrationConfig
     * objects, or application-based IntegrationConfigs that manage
     * other applications.
     * @param operation The operation we're interested in
     * @return true if any IntegrationConfig supports this operation, false if not
     */
    public boolean hasIntegrationSupportingOperation(String operation) throws GeneralException{
        for (IntegrationConfig config : getCache().getIntegrations()) {
            if (config.supportedOperation(operation))
                return true;
        }

        return false;
    }

    /**
     * Returns true if standard IntegrationConfigs exist for given config name.
     * @param integrationConfigName The IntegrationConfig we're looking for
     * @return true if we have it cached, false if not
     */
    public boolean hasStandardIntegrationConfig(String integrationConfigName) throws GeneralException{
        return getCache().integrationExists(integrationConfigName);
    }

    /**
     * Returns true if managed IntegrationConfig exist for given config name.
     * @param integrationConfigName The IntegrationConfig we're looking for
     * @return true if we have it cached, false if not
     */
    public boolean hasManagedIntegrationConfig(String integrationConfigName) throws GeneralException{
        return getCache().managedConfigExists(integrationConfigName);
    }

    // ------------------------------------------------------------
    //
    //  Private Methods
    //
    // ------------------------------------------------------------

    /**
     * Wrapper method for getting the current cache. When we get one it is checked
     * for staleness and reloaded if any Applications or IntegrationConfigs have 
     * changed since the last reload. 
     * @return
     * @throws GeneralException
     */
    private IntegrationConfigCache getCache() throws GeneralException{
        if (null == _cache) {
            if (log.isDebugEnabled()) log.debug("Null IntegrationConfigCache instance. Getting one.");
            _cache = IntegrationConfigCache.getIntegrationConfigCache(context);
        }

        return _cache;
    }

    /**
     * Convert an application into an IntegrationConfig. 
     * Note - Since this method is only used by IntegrationConfigFinder we should
     * already have an instance of IntegrationConfigCache that's been updated. No 
     * need to get it again and check if it's stale.
     * @param appName Name of the application to convert.
     * @return An IntegrationConfig, or null if the app is not found or
     *   if the app does not support provisioning.
     */
    private IntegrationConfig buildIntegrationForApp(String appName) throws GeneralException {

        Application app = context.getObjectByName(Application.class, appName);
        if (app == null)
            return null;

        getCache().cacheAppDetails(app);

        IntegrationConfig config = null;
        boolean hasConnector = true;
        if (app.getConnector() == null) {
            // this is okay as long as there is a proxy
            if (app.getProxy() == null) {
                log.error("Unable to instantiate connector for application: " +
                        app.getName());
                hasConnector = false;
            }
        } else {
            // Check the connector out here so we can keep it off
            // the list if it has been misconfigured.  Defer setting
            // the instance to the Connector which can get it from
            // the AbstractRequests.
            Connector con = null;
            try {
                con = ConnectorFactory.getConnector(app, null);
            } catch (Throwable t) {
                log.error(t);
            }

            if (con == null) {
                log.error("Unable to instantiate connector for application: " +
                        app.getName());
                hasConnector = false;
            }
        }

        if (hasConnector) {
            // wrap it in a fake config/executor
            config = new IntegrationConfig(app);
            config.load();
        }

        getCache().addManagedConfig(appName, config);

        return config;
    }

    /**
     * Convert an unstructured target collector defined on Application
     * into an IntegrationConfig.
     * Note - Since this method is only used by IntegrationConfigFinder we should
     * already have an instance of IntegrationConfigCache that's been updated. No 
     * need to get it again and check if it's stale.
     * @param collectorName   TargetSource name, used in conversion
     * @param  appName        Application name, used in conversion.
     * @return IntegrationConfig or null
     */
    private IntegrationConfig buildIntegrationForCollector(String collectorName, String appName)
        throws GeneralException {

        Application app = context.getObjectByName(Application.class, appName);
        if ( null == app ) {
            log.error("Unable to find application with the name ["+ appName +"]");
            return null;
        }

        getCache().cacheAppDetails(app);

        // get TargetSource Object from Application object
        TargetSource targetSource = app.getTargetSource(collectorName);
        if ( null == targetSource ) {
            log.warn("Unable to find target collector ["+ collectorName +"] defined on " +
                      "application ["+ appName +"]");
            return null;
        }

        // get overriding target collector name
        String overridingAction = targetSource.getOverridingAction();

        // if manual work item is selected then intentionally return
        // null, we don't want to build an IntegrationConfig in this case.
        if ( Util.nullSafeEq(overridingAction , TargetSource.ARG_MANUAL_WORK_ITEM) ) {
            return null;
        }

        // Check unstructured target collector provision is overridden
        if ( Util.isNotNullOrEmpty(overridingAction) ) {
            TargetSource overridingTargetSource = context.getObjectByName(TargetSource.class,
                                                                          overridingAction);
            if ( null != overridingTargetSource ) {
                targetSource = overridingTargetSource;
            }
            else {
                log.error("Unable to load overriding target collector ["+ overridingAction +"] " +
                          "for target collector ["+ collectorName +"]");
                return null;
            }
        }

        boolean hasCollector = true;
        if ( null == targetSource.getCollector() ) {
            // do not log error, it is handled in below flow.
            hasCollector = false;
        }
        else {
            try {
                TargetCollector collector = TargetCollectorFactory.getTargetCollector(targetSource);
                hasCollector = (null == collector)? false : true;
            }
            catch (Throwable t) {
                log.error(t);
                hasCollector = false;
            }
        }

        IntegrationConfig config = null;
        if ( !hasCollector ) {
            log.error("Unable to instantiate target collector ["+ targetSource.getName() +"]");
        }
        else {
            config = new IntegrationConfig(app, targetSource.getName());
            config.load();

            getCache().addTargetCollectorIntegration(collectorName, config);
        }

        return config;
    }

    /**
     * Get an IntegrationConfig for the application. Pull it out of the cache
     * if we already have one, else build it.
     * @param appName The application name
     * @return The IntegrationConfig for that application
     * @throws GeneralException
     */
    private IntegrationConfig getAppIntegration(String appName) throws GeneralException {

        IntegrationConfig conf = null;

        if (getCache().managedConfigExists(appName)) {
            conf = getCache().getManagedConfig(appName);
        } else {
            conf = buildIntegrationForApp(appName);
        }

        return conf;
    }

    /**
     * Get an IntegrationConfig object for target collector defined on Application.
     * Get it from the IntegrationConfigCache using the collectorName if we've got one
     * cached. If it's not cached then build one for the application and cache it. 
     * @param collectorName The unstructured target collector name
     * @param appName The Application name
     * @return The IntegrationConfig for that target collector
     * @throws GeneralException
     */
    private IntegrationConfig getCollectorIntegration(String collectorName, String appName) throws GeneralException {

        IntegrationConfig conf = null;

        if (getCache().targetCollectorIntegrationExists(collectorName)) {
            conf = getCache().getTargetCollectorIntegration(collectorName);
        } else {
            conf = buildIntegrationForCollector(collectorName, appName);
        }

        return conf;
    }

    /**
     * Get an IntegrationConfig object for Application or for unstructured target collector
     * defined on the Application
     * @param operation The operation name
     * @param applicationName The Application name
     * @param collectorName The unstructured target collector name
     * @return The IntegrationConfig
     * @throws GeneralException
     */
    private IntegrationConfig getIntegration(String operation, String applicationName, 
            String collectorName) throws GeneralException {

        return getIntegration(operation, applicationName, collectorName, null);
    }

    /**
     * Get an IntegrationConfig object for Application or for unstructured target collector
     * defined on the Application
     * @param operation The operation name
     * @param applicationName The Application name
     * @param collectorName The unstructured target collector name
     * @param objectType
     * @return The IntegrationConfig
     * @throws GeneralException
     */
    private IntegrationConfig getIntegration(String operation, String applicationName,
                                             String collectorName, String objectType)
        throws GeneralException {

        // determine if this is an account request based on the objectType,
        // mosty likely will be null for an account request but check for
        // "account" string anyway
        boolean isAccountRequest = Util.isNullOrEmpty(objectType) || Application.SCHEMA_ACCOUNT.equals(objectType);

        IntegrationConfig found = null;

        // Get a list of the configs.  We can't just use the collection returned from the
        // cache because that one could be modified as we use it.
        ArrayList<IntegrationConfig> configs = new ArrayList<IntegrationConfig>();
        for (IntegrationConfig config : getCache().getIntegrations()) {
            configs.add(config);
        }

        // pass 1, look for a ManagedResource or a proxy
        for (IntegrationConfig config : configs) {
            if (config.supportedOperation(operation) &&
                    config.getManagedResource(applicationName) != null &&
                    (isAccountRequest || (getCache().allowGroups() && config.isGroupProvisioning(objectType)))) {
                found = config;
            }
            //also look for the bloody proxy
            String proxyApplication = getCache().getProxy(context, applicationName);
            if( Util.isNotNullOrEmpty(proxyApplication) && found == null) {
                if (config.supportedOperation(operation) &&
                        config.getManagedResource(proxyApplication) != null &&
                                (isAccountRequest || (getCache().allowGroups() && config.isGroupProvisioning(objectType)))) {
                    found = config;
                }
            }

            if (found != null)
                break;
        }

        // pass 2 look for self management check to see
        // if the application support provisioning

        // We will have converted Applications with the PROVISIONING feature
        // into IntegrationConfigs with the same name as the application
        if ( found == null ) {
            IntegrationConfigCache.AppDetails details = getCache().getAppDetails(context, applicationName);

            if (details != null && details.isSupportsProvisioning()){
                IntegrationConfig config = null;

                // get IntegrationConfig object for unstructured target collector
                // defined on Application
                if ( Util.isNotNullOrEmpty(collectorName) ) {
                    config = getCollectorIntegration(collectorName, applicationName);
                }
                // get IntegrationConfig object for Application
                else {
                    config = getAppIntegration(applicationName);
                }

                if (config != null &&
                        ((isAccountRequest && details.isSupportsAccountProvisioning()) ||
                        (getCache().allowGroups() && config.isGroupProvisioning(objectType)))) {
                    found = config;
                }
            }
        }

        // pass 3 In 5.2 with provisioning connectors we have another case.
        // If an application has a proxy to a provisioning connector
        // then the provisioning connector manages the first application.
        // Detecting this here is a convoluted because we made the
        // Appication look like an IntegrationConfig.
        // If there is a hard proxy, shouldn't this win over a
        // relatively soft ManagedResource reference?
        if ( found == null ) {
            String proxy = getCache().getProxy(context, applicationName);
            if (proxy != null) {
                // jsl - formerly didn't check to see if the proxy supported provisioning
                // have to do that or else we'll get exceptions calling provision()
                IntegrationConfigCache.AppDetails details = getCache().getAppDetails(context, proxy);
                if (details != null && details.isSupportsProvisioning()) {
                    IntegrationConfig proxyIntegration = getAppIntegration(proxy);
                    if (proxyIntegration != null &&
                        ((isAccountRequest && details.isSupportsAccountProvisioning()) ||
                        (getCache().allowGroups() && proxyIntegration.isGroupProvisioning(objectType))))
                        found = proxyIntegration;
                }
            }
        }

        // pass 4 look for universal managers
        // assume this gets group requests too
        if (found == null) {
            for (IntegrationConfig config : getCache().getIntegrations()) {
                if (config.supportedOperation(operation) && config.isUniversalManager()) {
                    // Prior to 6.0 we bootstrapped ManagedResources when
                    // we reached this case.  I'd rather not do that since they
                    // weren't added manually and this should only be used
                    // for the unit tests.
                    found = config;
                    break;
                }
            }
        }

        return found;
    }

    /**
     * Get the proxy for the Application, if any.
     * @param appName The application name
     * @return The proxy name, if any
     * @throws GeneralException
     */
    public String getProxy(String appName) throws GeneralException {
        return getCache().getProxy(context, appName);
    }


    /**
     * The IntegrationConfigCache is instantiated and held by an instance of the
     * IntegrationConfigFinder which is instantiated from the PlanCompiler. It
     * is expected that the IntegrationConfigFinder instance will NOT be held
     * for long periods of time.
     *
     * The IntegrationConfigCache is maintained as a singleton and if the contents
     * of the cache is current then the same instance of the cache will be returned.
     * When the cache is found to be stale then a new instance of the cache will be
     * created and reloaded to bring it up to date. This allows an instance of the
     * IntegrationConfigFinder to maintain the same instance of the cache for the
     * duration of the task for which it is needed which is typically the compilation
     * of a provisioning plan.
     *
     * The cache is stale when the modification or created date of an IntegrationConfig
     * or Application in the database is found to be more recent than the version we have
     * in the cache maps. The database is the one true source of when objects were last
     * modified due to clustering implications.
     */

    public static class IntegrationConfigCache {
        private static Log log = LogFactory.getLog(IntegrationConfigCache.class);

        /**
         * True once we've initialized.
         */
        private static boolean _initialized;

        /**
         * True if IIQ is configured to allow group
         * provisioning.
         */
        private static boolean allowGroups;

        /**
         * Singleton for the integration config cache
         */
        private static IntegrationConfigCache configCache;

        /**
         * reloadCache is a simple way to determine if we need to reload
         * the cache. Currently, it's used by the InternalContext when an
         * object (like an Application or IntegrationConfig) stored by the 
         * IntegrationConfigCache has changed. If true we know one of those
         * objects has changed since the last reload. If false we still need
         * to query the database to get the modification dates of those objects
         * because they may have been changed by another server in the cluster.
         */
        private static boolean reloadCache = false;

        /**
         * Cached list of the standard integrations. This includes
         * any user-defined IntegrationConfigs, as well as any applications
         * that manage other applications. This group of
         * IntegrationConfigs will be small in number, so
         * will pre-load them when this instance is initialized.
         */
        private Map<String, IntegrationConfig> standardIntegrations;

        /**
         * Cached map of integrations keyed by application name.
         */
        private Map<String, IntegrationConfig> appIntegrations;

        /**
         * Cache of details about applications useful when
         * determining the appropriate IntegrationConfig.
         */
        private Map<String, AppDetails> appDetailCache;

        /**
         * Cached map of integrations keyed by unstructured target
         * collector name.
         */
        private Map<String, IntegrationConfig> targetCollectorIntegrations;

        /**
         * Cache the created/modified date to check against when accessing
         * so that we can reload the cache when it's out of date.
         */
        private Map<String, Long> integrationConfigAge;

        /**
         * We also need to keep track of created/modified dates for
         * applications because some IntegrationConfigs are created
         * dynamically with different options depending on what's
         * been configured on the app.  This maps app names to the
         * created or modified unixtime.
         */
        private Map<String, Long> applicationAge;

        /**
         * The interval at which the IntegrationConfigCache will reload itself
         * if it detects that it is stale.  This represents the number of times
         * that IntegrationConfigCache.refresh will need to be called before
         * we actually perform any work.  CacheService runs once per minute, so
         * this is effectively the number of minutes between which we will check the
         * IntegrationConfigCache for that not-so-fresh feeling.
         * 0 means default.  Default is DEFAULT_REFRESH_INTERVAL.
         */
        private static int refreshInterval;

        /**
         * boolean setting that will force the IntegrationConfigCache to check
         * much more frequently for updated Application objects and IntegrationConfigs,
         * which will result in many more queries, but always up-to-date objects.
         */
        private static boolean aggressiveRefresh;

        private static final int DEFAULT_REFRESH_INTERVAL = 10;

        /**
         * Return an up-to-date instance of the IntegrationConfigCache. This is a temporary
         * cache that must not be saved indefinitely by the caller.
         * If it doesn't exist one or is stale then one will be created and initialized.
         * @param context The context used for the IntegrationConfigCache
         * @return IntegrationConfigCache
         */
        public static IntegrationConfigCache getIntegrationConfigCache(SailPointContext context)
            throws GeneralException {

            if (configCache == null || configCache.isCacheStale(context)) {
                configCache = new IntegrationConfigCache(context);
            } 

            return configCache;
        }

        /**
         * Private ctor for the integration config cache. The cache will be created and loaded.
         * @param context
         * @return IntegrationConfigCache instance
         */
        private IntegrationConfigCache(SailPointContext context) throws GeneralException {
            if (log.isDebugEnabled()) log.debug("Creating a new IntegrationConfigCache and reloading it...");

            this.applicationAge = new HashMap<String, Long>();
            this.appDetailCache = new HashMap<String, AppDetails>();
            this.appIntegrations = new HashMap<String, IntegrationConfig>();
            this.integrationConfigAge = new HashMap<String, Long>();
            this.standardIntegrations = new HashMap<String, IntegrationConfig>();
            this.targetCollectorIntegrations = new HashMap<String, IntegrationConfig>();

            // If this is the first time through then reload everything.
            if (null == configCache) {
                reloadAll(context);
            } else {
                incrementalReload(context);
            }
        }

        /**
         * Set the reloadCache flag to indicate the cache needs reloading. 
         * @param boolean to indicate whether an object in the cache has been
         * modified and the cache needs reloading
         */
        public static void markCacheForReload(boolean reload) throws GeneralException {
            if (log.isDebugEnabled()) log.debug("Marking integration config cache to be reloaded.");
            reloadCache = reload;
        }

        /**
         * Check the IntegrationConfig objects and Application objects to detect whether the cache is out of date.
         * If any of either of those objects have been modified since the last time they were checked, the entire
         * set of caches for the IntegrationConfigCache will be wiped and reloaded.  If the refreshInterval is
         * negative or 0, then this method will do nothing.  If refreshInterval is greater than 1, this method will
         * decrement refreshInterval.  If refreshInterval is 1, then we will perform the check and reload if necessary.
         * @param context
         * @throws GeneralException
         */
        public static void refresh(SailPointContext context) throws GeneralException {

            // If the refresh runs before the cache is created, create it, initialize it
            // and return. The cache won't be stale if we just created it.
            if (null == configCache) {
                getIntegrationConfigCache(context);
                return;
            }

            if (refreshInterval < 0) {
                // just don't check.  Not sure why we wouldn't want to check, but allowing it.
                if (log.isDebugEnabled()) log.debug("Deliberately skipping refresh of IntegrationConfigCache.");
                return;
            }
            else if (0 == refreshInterval) {
                // don't bother doing anything if we haven't initialized because
                // that means no one really needs this yet.
                if (log.isDebugEnabled()) log.debug("Skipping refresh of IntegrationConfigCache.  Don't need it yet.");
                return;
            }
            else if (1 == refreshInterval) {
                if (log.isDebugEnabled()) log.debug("Check if the IntegrationConfigCache is stale and recreate if it is...");

                // Now we need to check for freshness. Recreate and reload if it's stale.
                getIntegrationConfigCache(context);

                // reset the interval back to the configured value
                configCache.setRefreshInterval(context);
            }
            else {
                refreshInterval--;
                if (log.isDebugEnabled()) log.debug(refreshInterval + " minutes to IntegrationConfigCache refresh.");
            }
        }

        /**
         * Unconditionally populate the cache with objects gathered from the perisistence layer using the context.
         * Also sets the cache to initialized.
         * @param context The context is necessary to perform queries on the persistence layer.
         * @throws GeneralException
         */
        private void reloadAll(SailPointContext context) throws GeneralException {

            updateGlobals(context);

            this.applicationAge = reloadApplicationAges(context);

            // List the integration configs sorted by name so we have a
            // predictable provisioning order for the tests.
            QueryOptions ops = new QueryOptions();
            ops.setOrderBy("name");

            if (log.isDebugEnabled()) log.debug("Loading IntegrationConfigs...");
            List<IntegrationConfig> configs = context.getObjects(IntegrationConfig.class, ops);
            if (log.isDebugEnabled()) log.debug("...Done loading IntegrationConfigs.");

            if (configs != null) {
                for (IntegrationConfig config : configs) {
                    config.load();
                    if (log.isDebugEnabled()) log.debug("Caching IntegrationConfig " + config.getName());
                    this.standardIntegrations.put(config.getName(), config);

                    Long age = getAge(config.getCreated(), config.getModified());

                    if (log.isDebugEnabled()) log.debug("Caching IntegrationConfig " + config.getName() + " with age " + age);
                    this.integrationConfigAge.put(config.getName(), age);
                }
            }

            // Now look up all the applications that manage
            // some other application through their ProvisioningConfig.
            // Create IntegrationConfigs for those applications
            // Performance note: We are assuming the total set of these
            // apps will be small. If that's not the case, we might be
            // in trouble here because decaching is difficult
            ops = new QueryOptions();
            ops.setOrderBy("name");
            ops.add(Filter.eq("managesOtherApps", true));
            List<Application> managingApps = context.getObjects(Application.class, ops);

            for (Application app : managingApps) {
                if (log.isDebugEnabled()) log.debug("Processing managing application " + app.getName());

                // Cache application details while we're here
                addAppToDetailsMap(this.appDetailCache, app);

                // Check the connector out here so we can keep it off
                // the list if it has been misconfigured.  Defer setting
                // the instance to the Connector which can get it from
                // the AbstractRequests.
                Connector con = null;
                try {
                    con = ConnectorFactory.getConnector(app, null);
                } catch (Throwable t) {
                    log.error(t);
                }

                if (con == null) {
                    log.error("Unable to instantiate connector for application: " + app.getName());
                }

                if (con != null) {
                    IntegrationConfig config = new IntegrationConfig(app);
                    config.load();

                    if (log.isDebugEnabled()) log.debug("Caching IntegrationConfig " + config.getName() + " based on application " + app.getName());
                    this.standardIntegrations.put(app.getName(), config);

                    // Store this in the application cache as well.
                    this.appIntegrations.put(app.getName(), config);
                }
            }

            reloadCache = false;
            _initialized = true;
        }

        /**
         * Update the variables that apply to all of the instances of
         * IntegrationConfigCache.
         * @param context
         * @throws GeneralException
         */
        private void updateGlobals(SailPointContext context) throws GeneralException {
            Configuration sys = context.getConfiguration();
            allowGroups = sys.getBoolean(Configuration.LCM_MANAGE_ACCOUNTS_ALLOW_GROUP_MANAGEMENT);
            setRefreshInterval(context);
            setAggressiveRefresh(context);
        }

        /**
         * Incrementally update the caches with objects gathered from the perisistence layer 
         * using the context. This method will use existing cache maps to copy unchanged 
         * objects into the new ones.
         * Also sets the cache to initialized.
         * @param context The context is necessary to perform queries on the persistence layer.
         * @throws GeneralException
         */
        private void incrementalReload(SailPointContext context) throws GeneralException {

            if (log.isDebugEnabled()) log.debug("Reloading the IntegrationConfigCache...");

            updateGlobals(context);

            // unconditionally reset the caches
            Map<String, Long> newIntegrationConfigAge = new HashMap<String, Long>();
            Map<String, Long> newApplicationAge = new HashMap<String, Long>();
            Map<String, IntegrationConfig> newAppIntegrations = new HashMap<String, IntegrationConfig>();
            Map<String, AppDetails> newAppDetailCache = new HashMap<String, AppDetails>();
            Map<String, IntegrationConfig> newStandardIntegrations = new HashMap<String, IntegrationConfig>();
            Map<String, IntegrationConfig> newTargetCollectorIntegrations = new HashMap<String, IntegrationConfig>();

            // Get the old config cache so we can use it to copy unchanged objects. If we have the
            // case where some other thread is also creating a new instance of the config cache
            // then this should allow us to continue using the cache maps from this one even if
            // it gets overwritten by another thread before we are done using it.
            IntegrationConfigCache oldCache = configCache;

            copyAndReloadConfigs(context, oldCache, newIntegrationConfigAge, newStandardIntegrations);

            copyAndReloadApps(context, oldCache, newApplicationAge, 
                    newAppIntegrations, newStandardIntegrations, newAppDetailCache);

            this.integrationConfigAge = newIntegrationConfigAge;
            this.applicationAge = newApplicationAge;
            this.appIntegrations = newAppIntegrations;
            this.appDetailCache = newAppDetailCache;
            this.standardIntegrations = newStandardIntegrations;
            this.targetCollectorIntegrations = newTargetCollectorIntegrations;

            reloadCache = false;
            _initialized = true;
        }

        /**
         * Check to see which IntegrationConfigs need to be reloaded and which ones can
         * be copied from the old cache. 
         * @param context
         * @param oldCache a reference to the old config cache
         * @param newConfigAge the new integrationConfigAge map
         * @param newStdIntegrations the new standardIntegrations map
         * @throws GeneralException
         */
        private void copyAndReloadConfigs(SailPointContext context, IntegrationConfigCache oldCache, 
                Map<String,Long> newConfigAge, Map<String,IntegrationConfig> newStdIntegrations) throws GeneralException {

            // Get all of the IntegrationConfigs with their created and modified dates.
            // If the age hasn't changed since the last reload then copy the IntegrationConfig
            // from the old caches to the new one. If one has changed we need to get the object,
            // reload it and put it in the caches.
            // This should handle cases where an IntegrationConfig that we've previously cached has
            // been deleted since we only look at the ones returned from the search.

            // List the integration configs sorted by name so we have a predictable provisioning 
            // order for the tests.
            QueryOptions ops = new QueryOptions();
            ops.setOrderBy("name");

            Iterator<Object []> configs = context.search(IntegrationConfig.class, ops, Arrays.asList("name", "created", "modified"));

            while (configs.hasNext()) {
                Object [] config = configs.next();
                String configName = (String) config[0];

                // config[1] == created, config[2] == modified
                Long newAge = getAge((Date) config[1], (Date) config[2]);
                Long oldAge = oldCache.integrationConfigAge.get(configName);

                if (log.isDebugEnabled()) log.debug("IntegrationConfig " + configName + ", cached age: " + oldAge + ", new age: " + newAge);

                //IIQETN-6497 :- Reloading IntegrationConfig when created date equals null
                if (null == newAge || null == oldAge || newAge > oldAge) {
                    // Cache miss because it's new, or if the one we've cache is stale
                    if (log.isDebugEnabled()) log.debug("Out of date or new IntegrationConfig to reload: " + configName);
                    IntegrationConfig configToReload = context.getObjectByName(IntegrationConfig.class, configName);

                    configToReload.load();
                    newStdIntegrations.put(configToReload.getName(), configToReload);

                    if (log.isDebugEnabled()) 
                        log.debug("Caching IntegrationConfig " + configToReload.getName() + " with age " + newAge);
                    newConfigAge.put(configToReload.getName(), newAge);
                } else {
                    // The dates are the same so the integration config hasn't changed. Copy it 
                    // from the previous caches to the new ones.
                    if (log.isDebugEnabled()) log.debug("Copying IntegrationConfig to new caches: " + configName);

                    if (oldCache.integrationConfigAge.containsKey(configName)) {
                        newConfigAge.put(configName, oldCache.integrationConfigAge.get(configName));
                    }

                    if (oldCache.standardIntegrations.containsKey(configName)) {
                        newStdIntegrations.put(configName, oldCache.standardIntegrations.get(configName));
                    }
                }
            }
        }

        /**
         * Check to see which Applications need to be reloaded and which ones can
         * be copied from the old cache. 
         * 
         * @param context
         * @param oldCache a reference to the old config cache
         * @param newAppAge the new applicationAge map
         * @param newAppIntegrations the new appIntegrations map
         * @param newStdIntegrations the new standardIntegrations map
         * @param newAppDetails the new appDetailsCache map
         * @throws GeneralException
         */
        private void copyAndReloadApps(SailPointContext context, IntegrationConfigCache oldCache, 
                Map<String,Long> newAppAge, Map<String,IntegrationConfig> newAppIntegrations,
                Map<String,IntegrationConfig> newStdIntegrations, Map<String, AppDetails> newAppDetails) throws GeneralException {

            // Get all of the Applications with their created and modified dates.
            // If the age hasn't changed since the last reload then copy the Application
            // from the old caches to the new ones. If one has changed we need to get the object,
            // reload it and put it in the caches.
            // This should handle cases where an Application that we've previously cached has
            // been deleted since we only add the ones returned from the search to the new
            // caches.

            // List the applications sorted by name so we have a predictable provisioning 
            // order for the tests.
            QueryOptions ops = new QueryOptions();
            ops.setOrderBy("name");

            Iterator<Object []> apps = context.search(Application.class, ops, Arrays.asList("name", "created", "modified", "managesOtherApps"));

            while (apps.hasNext()) {
                Object [] app = apps.next();
                String appName = (String) app[0];

                // app[1] == created, app[2] == modified
                Long newAge = getAge((Date) app[1], (Date) app[2]);
                Long oldAge = oldCache.applicationAge.get(appName);
                if (log.isDebugEnabled()) log.debug("Application " + appName + ", cached age: " + oldAge + ", new age: " + newAge);

                boolean managesOtherApps = (Boolean)app[3];

                //IIQETN-6497 :- Reloading IntegrationConfig when created date equals null
                if (null == newAge || null == oldAge || newAge > oldAge) {
                    // Cache miss because it's new, or this Application is stale when compared
                    // to the cache. Update it if it manages other applications
                    if (managesOtherApps) {
                        Application application = context.getObjectByName(Application.class, appName);
                        if (log.isDebugEnabled()) log.debug("Out of date or new managing Application to reload: " + appName);

                        // Cache application details while we're here
                        addAppToDetailsMap(newAppDetails, application);

                        // Check the connector out here so we can keep it off
                        // the list if it has been misconfigured.  Defer setting
                        // the instance to the Connector which can get it from
                        // the AbstractRequests.
                        Connector con = null;
                        try {
                            con = ConnectorFactory.getConnector(application, null);
                        } catch (Throwable t) {
                            log.error(t);
                        }

                        if (con == null) {
                            log.error("Unable to instantiate connector for application: " + application.getName());
                        }

                        if (con != null) {
                            IntegrationConfig config = new IntegrationConfig(application);
                            config.load();

                            if (log.isDebugEnabled())
                                log.debug("Caching IntegrationConfig " + config.getName()
                                        + " based on application " + application.getName());
                            newStdIntegrations.put(application.getName(), config);

                            // Store this in the application cache as well.
                            newAppIntegrations.put(application.getName(), config);
                        }
                    }

                    if (log.isDebugEnabled()) 
                        log.debug("Caching Application " + appName + " with age " + newAge);

                    // Update the application age regardless of whether it manages other applications.
                    newAppAge.put(appName, newAge);

                } else {
                    // The application hasn't changed. Copy it from the previous caches to the
                    // new ones.
                    if (log.isDebugEnabled()) log.debug("Copying Application to new caches: " + appName);
                    newAppAge.put(appName, oldCache.applicationAge.get(appName));

                    // Copy the integration configs to the new caches.
                    if (oldCache.appIntegrations.containsKey(appName)) {
                        newAppIntegrations.put(appName, oldCache.appIntegrations.get(appName));
                    }

                    if (oldCache.standardIntegrations.containsKey(appName)) {
                        newStdIntegrations.put(appName, oldCache.standardIntegrations.get(appName));
                    }

                    // Copy the AppDetails for this application and it's proxy.
                    AppDetails appDetail = oldCache.appDetailCache.get(appName);
                    if (null != appDetail) {
                        newAppDetails.put(appName, appDetail);

                        // Copy the proxy entry if there is one
                        String proxyName = appDetail.getProxy();
                        if (null != proxyName && null != oldCache.appDetailCache.get(proxyName)) {
                            newAppDetails.put(proxyName, oldCache.appDetailCache.get(proxyName));
                        }
                    }
                }
            }
        }

        /**
         * Set the refresh interval from the configuration.  We do this every time we refresh.
         * @param context
         * @throws GeneralException
         */
        private void setRefreshInterval(SailPointContext context) throws GeneralException {
            Configuration sys = context.getConfiguration();
            refreshInterval = sys.getInt(Configuration.INTEGRATION_CONFIG_CACHE_REFRESH_INTERVAL);
            if (0 == refreshInterval) {
                // default
                refreshInterval = DEFAULT_REFRESH_INTERVAL;
            }
        }

        /**
         * Get the aggressive refresh value from the configuration and set it locally
         * @param context the context to use to get the value
         * @throws GeneralException
         */
        private void setAggressiveRefresh(SailPointContext context) throws GeneralException {
            Configuration sys = context.getConfiguration();
            aggressiveRefresh = sys.getBoolean(Configuration.INTEGRATION_CONFIG_CACHE_AGGRESIVE_REFRESH);
        }

        /**
         * Set the aggressive refresh flag and add it to the configuration.
         * @param context the context to use to set the value
         * @param b boolean true or false
         * @throws GeneralException
         */
        public void setAggressiveRefresh(SailPointContext context, boolean b) throws GeneralException {
            aggressiveRefresh = b;
            Configuration sys = context.getConfiguration();
            sys.put(Configuration.INTEGRATION_CONFIG_CACHE_AGGRESIVE_REFRESH, b);
            context.saveObject(sys);
            context.commitTransaction();
        }

        /**
         * Populate the applicationAge cache.  Gathers the created or modified dates
         * of all applications in order to test for cache validation.
         * @param context
         * @throws GeneralException
         */
        private Map<String, Long> reloadApplicationAges(SailPointContext context) throws GeneralException {
            Map<String, Long> newApplicationAge = new HashMap<String, Long>();
            Iterator<Object []> appIter = context.search(Application.class, null, Arrays.asList("name", "created", "modified"));
            while (appIter.hasNext()) {
                Object [] app = appIter.next();
                String name = (String) app[0];
                Long newAge = getAge((Date) app[1], (Date) app[2]);
                newApplicationAge.put(name, newAge);
            }

            return newApplicationAge;
        }

        /**
         * Has the cache been initialized?
         * @return true if yes, false if not
         */
        private boolean isInitialized() {
            return null != this.standardIntegrations &&
                   null != this.appIntegrations &&
                   null != this.appDetailCache &&
                   null != this.integrationConfigAge &&
                   null != this.applicationAge &&
                   null != this.targetCollectorIntegrations &&
                   _initialized;
        }

        /**
         * Check for a cache that is out of date.  If we find any IntegrationConfigs that are out of date
         * return true, else false.
         * @param context
         * @return true if any part of the cache is out of date, meaning an Application or IntegrationConfig has changed
         * since the last time it was checked.
         * @throws GeneralException
         */
        private boolean isCacheStale(SailPointContext context) throws GeneralException {

            if (!isInitialized() || aggressiveRefresh) {
                // we haven't been initialized.
                return true;
            }

            // If reloadCache == true then we know there's been a change to either an Application
            // or an IntegrationConfig from this server making the cache stale. This flag will only
            // indicate if this server made a change to one of those objects and doesn't account
            // for servers in a clustered environment.
            if (reloadCache) {
                if (log.isDebugEnabled()) 
                    log.debug("Cache is stale. Change in an application or integration config was triggered.");
                return true;
            }

            if (log.isDebugEnabled()) log.debug("Checking if integrationConfigAge cache is stale.");

            if (isAgeCacheStale(context, integrationConfigAge, IntegrationConfig.class)) {
                return true;
            }

            if (log.isDebugEnabled()) log.debug("Checking if applicationAge cache is stale.");

            if (isAgeCacheStale(context, applicationAge, Application.class)) {
                return true;
            }

            if (log.isDebugEnabled()) log.debug("Caches are up to date.");

            return false;
        }

        /**
         * Find the highest modification date in the ageCache and return it.
         * @param the age cache to use
         * @return the timestamp of the highest or last modification date 
         * entry in the cache
         */
        private long getHighestModDate(Map<String,Long> ageCache) {
            // Get the highest mod date for the ageCache. We can use this as a
            // quick way to determine if anything was changed.
            long lastDate = -1;

            if (null == ageCache || ageCache.isEmpty()) {
                return lastDate;
            }

            for (Long age : ageCache.values()) {
                // If an age is null that means there's an entry in the cache
                // without a date.
                if (age == null) {
                    lastDate = -1;
                    break;
                } else if (age.longValue() > lastDate) {
                    lastDate = age.longValue();
                }
            }

            return lastDate;
        }

        /**
         * Check the age cache to see the cached objects have changed since the last time it was checked.
         * @param context
         * @return true if a cached object was modified or added since the last time it was checked.
         * @throws GeneralException
         */
        private boolean isAgeCacheStale(SailPointContext context, Map<String,Long> ageCache, Class<?> cacheClass) 
                throws GeneralException {
            if (null == ageCache)
                return true;

            long lastTimestamp = getHighestModDate(ageCache);

            // If lastDate == -1 then we have an entry in the cache without a date. 
            // That shouldn't happen and probably means the cache needs to be rebuilt.
            if (!ageCache.isEmpty() && lastTimestamp == -1) {
                if (log.isDebugEnabled()) 
                    log.debug("The age cache for " + cacheClass.getSimpleName() + " is stale and needs reloading.");
                return true;
            }

            QueryOptions ops = new QueryOptions();

            // If we don't have any Applications cached then we still have to check
            // if some were created since we last reloaded the cache. If we have one cached
            // then we can apply a date filter and get the ones since the last reload.
            if (lastTimestamp > -1) {
                // Return the Applications with a created or modified date
                // later than the highest age of the Applications in the cache. 
                // If we find one then the cache is stale, if not then it's up to date.
                Date lastModDate = new Date(lastTimestamp);
                ops.add(Filter.or(Filter.gt("modified", lastModDate), Filter.gt("created", lastModDate)));
            }

            int count = context.countObjects(cacheClass, ops);

            if (count > 0) {
                if (log.isDebugEnabled()) 
                    log.debug("Cache is stale. Number of " + cacheClass.getSimpleName() + " objects which need reloading = " + count);
                return true;
            }

            return false;
        }

        /**
         * Get the created or modified date as a Long representing unixtime.  If the modified date does not exist, the
         * created date will be used.
         * @param createdDate
         * @param modifiedDate
         * @return
         */
        private static Long getAge(Date createdDate, Date modifiedDate) {
            if (null == createdDate) {
                return null;
            }

            return getAge(createdDate.getTime(),
                          (null == modifiedDate) ? null : modifiedDate.getTime());
        }

        /**
         * Get the created or modified date as a Long representing unixtime.  If the modified date does not exist, the
         * created date will be used.
         * @param created
         * @param modified
         * @return
         */
        private static Long getAge(Long created, Long modified) {
            if (null == created) {
                return null;
            }

            return (null == modified) ? created : modified;
        }

        /**
         * Search the cache for the IntegrationConfig by name and return it.
         * @param integrationConfigName the name
         * @return the IntegrationConfig from the cache or null
         */
        private IntegrationConfig getIntegration(String integrationConfigName) {
            return standardIntegrations.get(integrationConfigName);
        }

        /**
         * Determine if the IntegrationConfig identified by the name exists.
         * @param integrationConfigName the name
         * @return true if it exists, false if not
         */
        private boolean integrationExists(String integrationConfigName) {
            return standardIntegrations.containsKey(integrationConfigName);
        }

        /**
         * Get all the IntegrationConfigs.
         * @return the IntegrationConfigs
         */
        private Collection<IntegrationConfig> getIntegrations(){
            return standardIntegrations.values();
        }

        /**
         * Get the IntegrationConfig managed by this application
         * @param appName application name
         * @return the managed IntegrationConfig or null
         */
        private IntegrationConfig getManagedConfig(String appName) {
            return appIntegrations.get(appName);
        }

        /**
         * Does the managed IntegrationConfig exist for this application.
         * @param appName application name
         * @return true if it exists, false if not
         */
        private boolean managedConfigExists(String appName) {
            return appIntegrations.containsKey(appName);
        }

        /**
         * Get the IntegrationConfig for this collector by name
         * @param collectorName collector name
         * @return the collector IntegrationConfig or null
         */
        private IntegrationConfig getTargetCollectorIntegration(String collectorName) {
            return targetCollectorIntegrations.get(collectorName);
        }

        /**
         * Does the IntegrationConfig exist for this collector name.
         * @param collectorName collector name
         * @return true if it exists, false if not
         */
        private boolean targetCollectorIntegrationExists(String collectorName) {
            return targetCollectorIntegrations.containsKey(collectorName);
        }

        /**
         * Adds the IntegrationConfig to the cache by name
         * @param collectorName collector name to identity this IntegrationConfig
         * @param config the IntegrationConfig
         */
        private void addTargetCollectorIntegration(String collectorName, IntegrationConfig config) {
            targetCollectorIntegrations.put(collectorName, config);
        }

        /**
         * Is group provisioning allowed?
         * @return true if yes, false if not
         */
        private boolean allowGroups() {
            return allowGroups;
        }

        /**
         * Add a managed IntegrationConfig to the cache using the application name
         * to identify it.
         * @param appName application name
         * @param config the IntegrationConfig
         */
        private void addManagedConfig(String appName, IntegrationConfig config) {
            appIntegrations.put(appName, config);
        }

        /**
         * Get the proxy application for the given application name.
         * @param context the context to use when searching for the proxy
         * @param appName the application name
         * @return the application proxy, if any
         * @throws GeneralException
         */
        private String getProxy(SailPointContext context, String appName) throws GeneralException {
            AppDetails details = getAppDetails(context, appName);
            return details != null ? details.getProxy() : null;
        }

        /**
         * Get the details for a particular application.  If we find that our version of the details is older
         * than the one stored in the DB, then we'll reload it.
         * @param context the context to use when searching for the application
         * @param appName the application name
         * @return the AppDetails object for the application
         * @throws GeneralException
         */
        private AppDetails getAppDetails(SailPointContext context, String appName) throws GeneralException {

            AppDetails details = null;
            if (appName != null) {
                details = appDetailCache.get(appName);

                if (null != details && aggressiveRefresh) {
                    // now make sure our app info is current
                    QueryOptions qo = new QueryOptions();
                    qo.add(Filter.eq("name", appName));
                    Iterator<Object []> apps = context.search(Application.class, qo, Arrays.asList("created", "modified"));
                    if (apps.hasNext()) {
                        if (log.isDebugEnabled()) log.debug("Verifying cache for application " + appName);

                        // Let's assume there's only one result if any
                        Object [] app = apps.next();
                        Long newAge = getAge((Date) app[0], (Date) app[1]);
                        Long oldAge = details.getAge();

                        if (log.isDebugEnabled()) log.debug("Application " + appName + "   cached age: " + oldAge + "    new age: " + newAge);

                        // if invalid object, missing info, or stale cache
                        if (null == newAge || null == oldAge || newAge > oldAge) {
                            if (log.isDebugEnabled()) log.debug("getAppDetails:  Cache miss or stale cache evaluating Application:  " + appName);
                            // trigger a reload of the application.
                            details = null;
                            appDetailCache.remove(appName);
                        }
                    }
                    else {
                        // The app was deleted?
                        if (log.isDebugEnabled()) log.debug("Was the application " + appName + " deleted?");
                        appDetailCache.remove(appName);
                    }
                }

                if (null == details) {
                    // cache it and give it back
                    Application application = context.getObjectByName(Application.class, appName);
                    if (null == application) {
                        if (log.isWarnEnabled()) log.warn("Could not get AppDetails for a non-existent application: " + appName);
                    } else {
                        cacheAppDetails(application);
                        details = appDetailCache.get(appName);
                    }
                }
            }

            return details;
        }

        /**
         * Cache certain details of the app that are often requested through the IntegrationConfigFinder.
         * @param application the application
         * @throws GeneralException
         */
        private void cacheAppDetails(Application application) throws GeneralException{

            AppDetails details = null;
            if (application != null) {
                details = appDetailCache.get(application.getName());
                if (details == null) {
                    if (log.isDebugEnabled()) log.debug("AppDetails are null for Application " + application.getName());

                    addAppToDetailsMap(appDetailCache, application);
                }
            }
        }

        /**
         * Add an application to a map of type <String, AppDetails>.  In practice, this is used to
         * both populate the existing appDetailCache as well as when completely reloading for a new
         * appDetailCache.
         * @param map The map to which you will put the AppDetails
         * @param app The Application object on which the AppDetails will be based
         */
        private void addAppToDetailsMap(Map<String, AppDetails> map, Application app) {
            map.put(app.getName(), new AppDetails(app));
            // Since we're here, load the proxy details as well
            if (app.getProxy() != null)
                map.put(app.getProxy().getName(), new AppDetails(app.getProxy()));
        }

        /**
         * AppDetails is a wrapper class that encapsulates an application's provisioning 
         * information in a lightweight form. It is cached in the appDetailsCache using the 
         * application name for quick reference.
         */
        private static class AppDetails {

            private String proxy;
            private boolean supportsGroupProvisioning;
            private boolean supportsAccountProvisioning;
            private boolean supportsProvisioning;
            private Long age;

            private AppDetails(Application app) {
                proxy = app.getProxy() != null ? app.getProxy().getName() : null;
                supportsGroupProvisioning = app.isSupportsGroupProvisioning();
                supportsProvisioning = app.isSupportsProvisioning();
                //Feature string for account provisioning still lives on the App Feature String
                supportsAccountProvisioning = app.supportsFeature(Application.Feature.PROVISIONING);
                age = IntegrationConfigCache.getAge(app.getCreated(), app.getModified());
            }

            private String getProxy() {
                return proxy;
            }

            private boolean isSupportsGroupProvisioning() {
                return supportsGroupProvisioning;
            }

            private boolean isSupportsAccountProvisioning() {
                return supportsAccountProvisioning;
            }

            private boolean isSupportsProvisioning() {
                return supportsProvisioning;
            }

            private Long getAge() {
                return age;
            }
        }
    }
}
