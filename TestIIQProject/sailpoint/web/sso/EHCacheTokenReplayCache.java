/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 * 
 * This file originated from https://apache.googlesource.com/cxf-fediz/+/1.0.x-fixes/plugins/core/src/main/java/org/apache/cxf/fediz/core/EHCacheTokenReplayCache.java
 */
package sailpoint.web.sso;

import java.io.IOException;
import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.function.Supplier;

import org.ehcache.Cache;
import org.ehcache.CacheManager;
import org.ehcache.config.builders.CacheConfigurationBuilder;
import org.ehcache.config.builders.CacheManagerBuilder;
import org.ehcache.config.builders.ResourcePoolsBuilder;
import org.ehcache.expiry.ExpiryPolicy;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * An in-memory EHCache implementation of the TokenReplayCache interface.
 * The default TTL is 60 minutes and the max TTL is 12 hours.
 */
public class EHCacheTokenReplayCache implements TokenReplayCache<String> {

    private static final Log log = LogFactory.getLog(EHCacheTokenReplayCache.class);

    public static final String SAML_TOKEN_CACHE = "SAMLTokenCache";
    private Cache<String, EHCacheTokenValue> _cache;

    // Ehcache v3 does not have a built in singleton.
    private static CacheManager _cacheManager;

    public EHCacheTokenReplayCache() {
        createCache();
    }

    private void createCache() {
        if (null == _cacheManager) {
            synchronized (EHCacheTokenReplayCache.class) {
                log.debug("Building CacheManager...");
                _cacheManager = CacheManagerBuilder.newCacheManagerBuilder()
                        .withCache(SAML_TOKEN_CACHE,
                                CacheConfigurationBuilder.newCacheConfigurationBuilder(String.class, EHCacheTokenValue.class,
                                        ResourcePoolsBuilder.heap(100000)).withExpiry(new EHCacheTokenExpiryPolicy()))
                        .build(true);
            }
        }

        _cache = _cacheManager.getCache(SAML_TOKEN_CACHE, String.class, EHCacheTokenValue.class);
        if (null != _cache) {
            log.debug("Cache [" + SAML_TOKEN_CACHE + "] created.");
        } else { 
            log.error("Unable to create cache [" + SAML_TOKEN_CACHE + "]");
        }
    }

    /**
     * Add the given identifier to the cache. It will be cached for a default amount of time.
     * @param id The identifier to be added
     */
    @Override
    public void putId(String id) {
        Instant defaultExpiry = Instant.now().plusSeconds(EHCacheTokenExpiryPolicy.DEFAULT_TTL);
        putId(id, defaultExpiry);
    }

    /**
     * Add the given identifier to the cache.
     * @param id The identifier to be added
     * @param timeToLive The length of time to cache the Identifier in seconds
     */
    @Override
    public void putId(String id, Instant expiry) {
        if (id == null || "".equals(id)) {
            log.debug("Unable to cache null or empty id.");
            return;
        }

        // If the saml cache is null then there was a problem creating it and the error already logged.
        if (null == _cache) {
            log.debug("Cache [" + SAML_TOKEN_CACHE + "] is null.");
            return;
        }

        String expiryValue = (null == expiry) ? "null" : expiry.toString();

        // IIQMAG-3332 - Use the EHCacheTokenValue class to store the token expiration value in the cache.
        // The EHCacheTokenValue allows a expiration Instant to be stored for each id in the cache.
        _cache.put(id, new EHCacheTokenValue(id, expiry));
        log.debug("Adding id [" + id + " to cache with expiry " + expiryValue);
    }

    /**
     * Return true if the given identifier is contained in the cache
     * @param identifier The identifier to check
     */
    public boolean contains(String id) {
        // If the saml cache is null then there was a problem creating it and the error already logged.
        if (null == _cache) {
            log.debug("Cache [" + SAML_TOKEN_CACHE + "] is null.");
            return false;
        }

        EHCacheTokenValue cacheEntry = _cache.get(id);
        return cacheEntry != null;
    }

    /**
     * Return the given identifier if it is contained in the cache, otherwise null.
     * @param id The identifier to check
     */
    @Override
    public String getId(String id) {
        if (contains(id)) {
            log.debug("id [" + id + "] found in cache");
            return id;
        }

        log.debug("id [" + id + "] has expired and is no longer in cache");
        return null;
    }

    /**
     * Return the given identifier if it is contained in the cache, otherwise null.
     * @param id The identifier to check
     */
    @Override
    public Instant getExpiry(String id) {
        EHCacheTokenValue cacheEntry = null;

        // If the saml cache is null then there was a problem creating it and the error already logged.
        if (null == _cache) {
            log.debug("Cache [" + SAML_TOKEN_CACHE + "] is null.");
            return null;
        }

        if (contains(id)) {
            cacheEntry = _cache.get(id);
            log.debug("id [" + id + "] found in cache, returning expiry instant [" + cacheEntry.getExpiry() + "]");
            return cacheEntry.getExpiry();
        }

        log.debug("id [" + id + "] has expired and is no longer in cache");
        return null;
    }

    public void close() throws IOException {
        if (_cacheManager != null) {
            _cacheManager.removeCache(SAML_TOKEN_CACHE);
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
     * A cache value for EHCache. It contains the identifier to be cached as well as a custom expiry.
     */
    public class EHCacheTokenValue implements Serializable {

        // IIQMAG-3332 - EHCacheTokenValue contains a custom expiration Instant for the id. In ehcache v2
        // this was implemented using an Element class but that class doesn't exist in ehcache v3. This
        // class was implemented as a replacement for the v2 Element class.
        private static final long serialVersionUID = 1L;

        private String id;
        private Instant expiry;

        public EHCacheTokenValue(String identifier, Instant expiry) {
            this.id = identifier;
            this.expiry = expiry;
        }

        public String getIdentifier() {
            return id;
        }

        public Instant getExpiry() {
            return expiry;
        }
    }


    /**
     * A custom Expiry implementation for EhCache. It uses the supplied expiry which is part of the cache value.
     * If it doesn't exist, it falls back to the default value (3600 seconds).
     */
    public class EHCacheTokenExpiryPolicy implements ExpiryPolicy<String, EHCacheTokenValue> {

        /**
         * The default time to live in seconds (60 minutes)
         */
        public static final long DEFAULT_TTL = 3600L;

        /**
         * The max time to live in seconds (12 hours)
         */
        public static final long MAX_TTL = DEFAULT_TTL * 12L;

        @Override
        public Duration getExpiryForCreation(String s, EHCacheTokenValue ehCacheTokenValue) {
            // IIQMAG-3332 - The ehcache v3 implementation uses an expiriation policy class where
            // each item in the cache can have a different expiration value. This method will be called
            // when items are added to the cache. It the expiration Instant is invalid then a default
            // of 60 seconds will be used.
            Instant expiry = ehCacheTokenValue.getExpiry();
            Instant now = Instant.now();

            if (expiry == null || expiry.isBefore(now) || expiry.isAfter(now.plusSeconds(MAX_TTL))) {
                log.debug("Returning default duration of 60 minutes");
                return Duration.of(DEFAULT_TTL, ChronoUnit.SECONDS);
            }

            log.debug("Returning duration of [" + (expiry.toEpochMilli() - now.toEpochMilli()) + "] in milliseconds");
            return Duration.of(expiry.toEpochMilli() - now.toEpochMilli(), ChronoUnit.MILLIS);
        }

        @Override
        public Duration getExpiryForAccess(String s, Supplier<? extends EHCacheTokenValue> supplier) {
            return null;
        }

        @Override
        public Duration getExpiryForUpdate(String s, Supplier<? extends EHCacheTokenValue> supplier, EHCacheTokenValue ehCacheValue) {
            return null;
        }
    }
}
