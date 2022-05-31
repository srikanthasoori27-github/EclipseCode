/* (c) Copyright 2019 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.api;

import java.io.IOException;
import java.io.Serializable;
import java.time.Duration;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.ehcache.Cache;
import org.ehcache.CacheManager;
import org.ehcache.config.builders.CacheConfigurationBuilder;
import org.ehcache.config.builders.CacheManagerBuilder;
import org.ehcache.config.builders.ExpiryPolicyBuilder;
import org.ehcache.config.builders.ResourcePoolsBuilder;
import org.ehcache.expiry.ExpiryPolicy;
import sailpoint.object.Application;
import sailpoint.object.ManagedAttribute;
import sailpoint.tools.GeneralException;

/**
 * CachedManagedAttributer uses ehcache to cache requests for ManagedAttributes.
 */
public class CachedManagedAttributer {

    private static final String CACHE_NAME = "CachedManagedAttributer";
    private static final long DEFAULT_TTL = 300;

    private SailPointContext _context;
    private Cache<String, CachedMAValue> _cache;

    // Ehcache v3 does not have a built in singleton.
    private static CacheManager _cacheManager;
    private Duration timeToLive = Duration.ofSeconds(DEFAULT_TTL);
    private Duration timeToIdle = Duration.ofSeconds(DEFAULT_TTL);

    private static Log log = LogFactory.getLog(CachedManagedAttributer.class);

    /**
     * Constructor. Uses an existing cache if one exists.
     * @param context SailPointContext
     */
    public CachedManagedAttributer(SailPointContext context) {
        _context = context;
        _cache = retrieveCache();
    }

    /**
     * Retrieve an existing ehcache Cache instance, or create one if it doesn't exist yet.
     * @return The ehcache object to use.
     */
    private Cache<String, CachedMAValue> retrieveCache() {

        if (null == _cacheManager) {
            synchronized (CachedManagedAttributer.class) {
                ExpiryPolicy<Object, Object> expiryPolicy = ExpiryPolicyBuilder.expiry().create(timeToLive)
                        .access(timeToIdle).build();
                log.debug("Building CacheManager...");
                _cacheManager = CacheManagerBuilder.newCacheManagerBuilder()
                        .withCache(CACHE_NAME,
                                CacheConfigurationBuilder.newCacheConfigurationBuilder(String.class,
                                        CachedMAValue.class, ResourcePoolsBuilder.heap(5000))
                                        .withExpiry(expiryPolicy))
                        .build(true);
            }
        }

        // Retrieve the cache from the cache manager. 
        _cache = _cacheManager.getCache(CACHE_NAME, String.class, CachedMAValue.class);
        if (null != _cache) {
            log.debug("Returning cache [" + CACHE_NAME + "].");
        } else {
            log.error("Unable to create cache [" + CACHE_NAME + "]");
        }
        return _cache;
    }

    /**
     * Retrieve the ManagedAttribute that matches the provided information.
     *
     * @param app Application for this ManagedAttribute
     * @param permission True if this ManagedAttribute is a permission
     * @param name Name of this ManagedAttribute
     * @param value Value of this ManagedAttribute
     * @param objectType Type of this ManagedAttribute
     * @return ManagedAttribute if a match is found, otherwise null
     * @throws GeneralException
     */
    public ManagedAttribute get(Application app, boolean permission, String name, String value, String objectType)
            throws GeneralException {

        String appid = (app != null) ? app.getId() : null;
        return get(appid, permission, name, value, objectType);
    }

    /**
     * Retrieve the ManagedAttribute that matches the provided information.
     *
     * @param appId ID of the Application for this ManagedAttribute
     * @param name Name of this ManagedAttribute
     * @param value Value of this ManagedAttribute
     * @return ManagedAttribute if a match is found, otherwise null
     * @throws GeneralException
     */
    public ManagedAttribute get(String appId, String name, String value)
            throws GeneralException {

        return get(appId, false, name, value, null);
    }

    /**
     * Retrieve the ManagedAttribute that matches the provided information.
     *
     * @param app Application for this ManagedAttribute
     * @param name Name of this ManagedAttribute
     * @param value Value of this ManagedAttribute
     * @return ManagedAttribute if a match is found, otherwise null
     * @throws GeneralException
     */
    public ManagedAttribute get(Application app, String name, String value) throws GeneralException {

        String appId = (app != null) ? app.getId() : null;
        return get(appId, false, name, value, null);
    }

    /**
     * Retrieve the ManagedAttribute that matches the provided information.
     *
     * @param appId ID of the Application for this ManagedAttribute
     * @param permission True if this ManagedAttribute is a permission
     * @param name Name of this ManagedAttribute
     * @param value Value of this ManagedAttribute
     * @param objectType Type of this ManagedAttribute
     * @return ManagedAttribute if a match is found, otherwise null
     * @throws GeneralException
     */
    public ManagedAttribute get(String appId, boolean permission, String name, String value, String objectType)
            throws GeneralException {

        ManagedAttribute attr = null;
        String hash = getHash(appId, name, value, permission);

        if (null == _cacheManager || null == _cache) {
            retrieveCache();
        }

        if (null == _cache) {
            // Error already logged, just return null
            return attr;
        }

        if (!_cache.containsKey(hash)) {
            // If this is not a managed attribute then ManagedAttributer.get() will return null
            attr = ManagedAttributer.get(_context, appId, permission, name, value, objectType);
            String attrName = (null == attr) ? "null" : attr.getDisplayableName();

            // IIQMAG-3332 - Use the CachedMAValue class to store the managed attribute in the cache.
            // The CachedMAValue allows a null managed attribute value to be stored in the cache.
            _cache.put(hash, new CachedMAValue(hash, attr));
            log.debug("Adding ManagedAttribute [" + attrName + "] to cache using hash [" + hash + "]");
        } else {
            attr = _cache.get(hash).getManagedAttribute();
        }

        return attr;
    }

    /* Defer to ManagedAttributer to get a unique hash to key the cache. */
    private static String getHash(String appId, String name, String value, boolean permission) {
        String type = permission ? ManagedAttribute.Type.Permission.name() : ManagedAttribute.Type.Entitlement.name();
        return ManagedAttributer.getHash(appId, type, name, value);
    }

    public void close() throws IOException {
        if (_cacheManager != null) {
            _cacheManager.removeCache(CACHE_NAME);
            _cacheManager.close();
            _cacheManager = null;
            _cache = null;
        }
    }
    
    public void clear() {
        if (null != _cache) {
            _cache.clear();
        }
    }

    /**
     * A cache value for CachedManagedAttributer. It contains the hash and the ManagedAttribute.
     */
    public class CachedMAValue implements Serializable {

        // IIQMAG-3332 - The previous implementation that used ehcache v2 used the Element class
        // to store the cached managed attribute and the value of the Element could be null. Ehcache v3
        // eliminated the Element class and replaced it with a simple Map like interface that uses a
        // put() for a name/value pair. However, this v3 implementation does not allow a null value. Since
        // not all attributes are managed attributes the query to get the managed attribute can return null.
        //
        // The CachedMAValue was added so that we could store a null managed attribute in the cache. If we didn't
        // allow the ability to store a null value in the cache it would result in repeated queries for the
        // same managed attribute, all of which would return null. It's not clear what those repeated queries
        // would cost in terms of performance but it seemed reasonable to follow the previous implementation
        // and find a way to store a null managed attribute in the cache.
        private static final long serialVersionUID = 1L;

        private String hash;
        private ManagedAttribute attr;

        public CachedMAValue(String hash, ManagedAttribute attr) {
            this.hash = hash;
            this.attr = attr;
        }

        public String getHash() {
            return hash;
        }

        public ManagedAttribute getManagedAttribute() {
            return attr;
        }
    }
}
