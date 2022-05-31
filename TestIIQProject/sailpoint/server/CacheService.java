/* (c) Copyright 2010 SailPoint Technologies, Inc., All Rights Reserved. */
/*
 *
 * A Service that does periodic invalidation of system caches.
 *
 * Author: Jeff
 *
 * Caches are managed generically as a list of CacheReference objects.
 * Each service cycle we will query the modification dates for each
 * cached object and update the CacheReference when the mod dates change.
 * 
 * NOTE: We could reduce the number of mod date queries we run
 * by doing all of the ObjectConfig objects in one SELECT rather than 
 * one per object.  This really shouldn't be that significant though.
 *
 * UPDATE: In 6.0 we add the ability to refresh the Explanator cache
 * of ManagedAttibute descriptions.  These are not managed as CacheReference
 * objects, we just ping Explanator whenever we wake up.
 *
 * CacheService is a "standard" service meaning it runs in all JVMs
 * unless explicitly suppressed as in the console.  This is the only
 * service without a ServiceDefinition.  It is created at runtime
 * by InternalContext.
 * 
 */

package sailpoint.server;

import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.Cache;
import sailpoint.api.CorrelationModel;
import sailpoint.api.Explanator;
import sailpoint.api.PolicyCache;
import sailpoint.api.SailPointContext;
import sailpoint.object.Cacheable;
import sailpoint.object.CacheReference;
import sailpoint.object.Configuration;
import sailpoint.object.Filter;
import sailpoint.object.QueryOptions;
import sailpoint.object.SailPointObject;
import sailpoint.provisioning.IntegrationConfigFinder;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.web.Authorizer;


public class CacheService extends Service {

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    private static Log log = LogFactory.getLog(CacheService.class);

    /**
     * Name under which this service will be registered.
     * Can be used with Servicer.execute(String) to force service
     * execution by name.
     */
    public static final String NAME = "Cache";

    /**
     * List of caches we maintain.  
     */
    List<CacheReference> _caches;

    //////////////////////////////////////////////////////////////////////
    //
    // Constructor/Interface
    //
    //////////////////////////////////////////////////////////////////////

    public CacheService() {
        _name = NAME;
        _interval = 60 * 1;
    }

    /**
     * Called early in the startup sequence, and may be called after
     * startup to reload configuration changes.
     */
    @Override
    public void configure(SailPointContext context) 
        throws GeneralException {

        // TODO: If we got the interval from the ServiceDefinition
        // we can track changes.  If you do change the sysconfig,
        // you'll have to bump the mode time on the ServiceDefinition
        // to get it applied.

        Configuration config = context.getConfiguration();
        int duration = config.getInt(Configuration.CACHED_OBJECT_DURATION);
        if (duration > 0) {
            log.info("CacheService interval changed to " + 
                     Util.itoa(duration) + " seconds");
            _interval = duration;
        }
        else {
            log.info("CacheService interval defaulted to " + 
                     Util.itoa(_interval) + " seconds");
        }
    }

    /**
     * For the debug page.
     */
    public List<CacheReference> getCaches() {
        return _caches;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Registration/Update
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Add a cache reference.
     * This does two things, first it adds the reference to 
     * the global list, next it gives the reference to the object's
     * class so that it may be stored in a static field that is
     * easier to get to by the rest of the system code.
     */
    public void register(SailPointObject obj) {
        if (obj != null) {
            if (!(obj instanceof Cacheable)) {
                log.error("Can't cache object that doesn't implement Cacheable");
            }
            else {
                log.info("Registering cached object: " + 
                         obj.getClass().getSimpleName() + ":" + 
                         obj.getName());

                CacheReference ref = new CacheReference(obj);
                if (_caches == null)
                    _caches = new ArrayList<CacheReference>();
                _caches.add(ref);

                Cacheable cb = (Cacheable)obj;
                cb.setCache(ref);
            }
        }
    }

    /**
     * Update one object without checking mod date.
     * This is inteded to be called by InternalContext after we commit
     * changes to an object.  The object is supposed to implement
     * Cacheable and will have had the isCacheable method return true
     * so we only cahce things that the class wants.
     * This can be called from multiple threads.
     *
     * Register this if we don't already have it.
     * this is necessary because during the initial system bootstrapping
     * we will be importing the cached objects for the first time so we
     * couldn't register them in InternalContext.prepare.  
     * Hmm, maybe we should just have update() and not bother with
     * the distriction between it and register()?
     */
    public synchronized void update(SailPointObject obj) {

        boolean updated = false;
        if (obj != null && _caches != null) {
            for (CacheReference cache : _caches) {
                // Note that we have to get the *original* object
                // not the override object.  What we're doing is tracking
                // changes to the original object, the override stays in place
                // until the original is restored.  This is important because
                // we may see flushes to the override object that have a different
                // name and we can end up trashing the original.
                SailPointObject cached = cache.getOriginalObject();
                if (obj.equals(cached)) {
                    if (log.isInfoEnabled()) {
                        log.info("Updating cached object: " +
                                 obj.getClass().getSimpleName() + ":" +
                                 obj.getName());
                    }
                    cache.update(obj);
                    updated = true;
                    break;
                }
            }
        }

        if (!updated) {
            // only supposed to call this for things we want cached
            // so register it
            register(obj);
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Refresh
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Called each execution interval.
     */
    public void execute(SailPointContext context) throws GeneralException {

        // let this be turned off for debugging
        Configuration config = context.getConfiguration();
        if (config.getBoolean(Configuration.CACHED_OBJECT_NO_EXPIRATION)) {
            log.info("Cache expiration disabled by sytem configuration");
            return;
        }

        if (System.getProperty("iiq.noCacheService") != null) {
            log.info("Cache expiration disabled by system property");
            return;
        }

        log.info("CacheService executing");
        refresh(context);

        // Refresh various other system caches
        // Some of these can take a long time 

        // ManagedAttribute cache
        Explanator.checkRefresh(context);

        // Policy cache
        PolicyCache.refresh(context);

        // Bundle cache
        CorrelationModel.refresh(context);

        // IntegrationConfigCache
        IntegrationConfigFinder.IntegrationConfigCache.refresh(context);

        // refresh webresources into Authorizer singleton
        Authorizer.checkRefresh(context);
    }

    /**
     * Refresh the cached objects that have been modified.
     * May be called from outside the service thread to force
     * refresh of all registered caches.
     * TODO: Need some synchronization with the service thread!!
     * Now that we manage other caches in execute() should
     * we be refreshing them here too?
     */
    public void refresh(SailPointContext context) {

        refresh(context, false);
    }

    /**
     * Refresh all cached objects.
     * This is used by the debug page.
     */
    public void forceRefresh(SailPointContext context) {

        forceRefreshObjects(context);
        
        // sigh, Explanator has it's own
        // "Refresh Managed Attribute Cache" button
        // on the debug page which also does this,
        // so we could remove the button
        Explanator.refresh(context);

        PolicyCache.forceRefresh(context);
        CorrelationModel.forceRefresh(context);
        Authorizer.forceRefresh();
    }

    /**
     * Refresh all cached objects, ignoring their modification date.
     * This is intended for use in a few special situations where
     * objects referenced inside the cached object may have changed.
     * In practice this only applies to ObjectConfig.  Called
     * by InternalContext whenever you check in a Rule.
     */
    public void forceRefreshObjects(SailPointContext context) {
        refresh(context, true);
    }
    
    @Override
    public void terminate() throws GeneralException {
        super.terminate();
        Cache.deactivate();
    }

    /**
     * Inner refresher.
     * This can be called from multiple threads.
     */
    private synchronized void refresh(SailPointContext context, boolean force) {

        try {
            List<String> cachesToRegister = new ArrayList<String>();
            cachesToRegister.add(Configuration.RAPIDSETUP_CONFIG);
            cachesToRegister.add(Configuration.IAI_CONFIG);
            cachesToRegister.add(Configuration.FAM_CONFIG);

            if (_caches != null) {
                for (CacheReference cache : _caches) {
                    SailPointObject obj = cache.getOriginalObject();
                    if (obj != null && obj instanceof Configuration) {
                        cachesToRegister.remove(obj.getName());
                    }
                    refresh(context, cache, force);
                }

                if (!cachesToRegister.isEmpty()) {
                    for (String cacheToRegister : cachesToRegister) {
                        Configuration configuration = getOptionalCacheSetupConfig(context, cacheToRegister);
                        if (null != configuration) {
                            register(configuration);
                        }
                    }
                }
            }
        }
        catch (Throwable t) {
            log.error(t);
        }
    }

    /**
     * get optionalCacheSetupConfig
     * Using context.search since getObjectByName sometimes has a delay when finding
     * new objects from a console import.
     * @param context
     * @return Configuration object
     */
    private Configuration getOptionalCacheSetupConfig(SailPointContext context, String config) {
        Configuration cfg = null;
        QueryOptions ops = new QueryOptions();
        ops.add(Filter.eq("name", config));

        try {
            Iterator<Configuration> it = context.search(Configuration.class, ops);
            if (it.hasNext()) {
                cfg = it.next();
            }
        } catch (Throwable t) {
            log.error("Exception getting " + config, t);
        }

        return cfg;
    }

    /**
     * Refresh one cached object.
     */
    private void refresh(SailPointContext context, CacheReference cache, 
                         boolean force) {
        
        // note that you have to use getOriginalObject here, we do not want
        // to refresh and permanently set the object override if present
        SailPointObject obj = cache.getOriginalObject();
        if (obj != null) {
            String className = obj.getClass().getSimpleName();
            String name = obj.getName();

            boolean needsRefresh = force;
            if (!needsRefresh) {
                // compare mod dates
                Date newmod = Servicer.getModificationDate(context, obj);
                Date oldmod = obj.getModified();
                if (oldmod == null)
                    oldmod = obj.getCreated();

                if (newmod == null) {
                    log.error("No modification date for: " + 
                              className + ":" + name);
                }
                else if (oldmod == null || !oldmod.equals(newmod)) {
                    needsRefresh = true;
                }
            }

            if (needsRefresh) {
                try {
                    SailPointObject neu =  context.getObjectByName(obj.getClass(), name);
                    if (neu != null) {
                        if (log.isInfoEnabled()) {
                            log.info("Refreshing cached object: " +
                                     className + ":" + name);
                        }
                        neu.load();
                        cache.update(neu);
                    }
                    else {
                        // for ObjectConfigs this might mean that one
                        // was deleted before saving a new one, 
                        // should be a temporary condition
                        log.warn("Unable to refresh cached object: " +
                                 className + ":" + name);
                    }
                }
                catch (Throwable t) {
                    // problem with the DB, ignore for now
                    log.error(t);
                }

            }
            else {
                if (log.isDebugEnabled()) {
                    log.debug("Cached object has not changed: " +
                             className + ":" + name);
                }
            }
        }
    }

}
