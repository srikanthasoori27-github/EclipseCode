/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * The interface of an object that supports global cache management
 * using CacheReference, and CacheService.
 *
 * Implementors of this interface are expected to maintain static fields
 * containing one or more CacheReferences.  
 * 
 * Author: Jeff
 *
 */

package sailpoint.object;

public interface Cacheable {

    /**
     * Return true if this object instance is cacheable.
     */
    public boolean isCacheable(SailPointObject obj);

    /**
     * Assign a cache reference to the class.  This is expected to be stored
     * in a static field and used by other methods on the class
     * to return the cached object.
     */
    public void setCache(CacheReference obj);

}
