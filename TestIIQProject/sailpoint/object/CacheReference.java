/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * A wrapper around a cached SailPointObject.  This allows the
 * cached object to be replaced without needing to modify all of the
 * places that reference the object.
 *
 * Author: Jeff
 *
 * This grew out of the older CachedObject.java after we redesigned
 * cache invalidation to use CacheService rather than the CachedObject timeout.
 * 
 *
 * I tried using generics here so we wouldn't have to downcase in the
 * cache accessor methods (e.g. Configuration.getSystemConfiguration).
 * But the way CacheService works it is had to maintain generics
 * all the way through so just let them be SailPointObject containers.
 */

package sailpoint.object;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * @exclude
 */
public class CacheReference {

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    private static Log log = LogFactory.getLog(CacheReference.class);

    /**
     * Object we're caching.
     */
    SailPointObject _object;

    /**
     * Saved object if we're overriding. 
     * This is a hack for unit tests that like to temporarily override
     * ObjectConfigs for the duration of the test.
     */
    SailPointObject _save;

    //////////////////////////////////////////////////////////////////////
    //
    // Methods
    //
    //////////////////////////////////////////////////////////////////////

    public CacheReference(SailPointObject obj) {
        _object = obj;
    }

    public SailPointObject getObject() {
        return _object;
    }

    public void setObject(SailPointObject obj) {
        _object = obj;
    }

    /**
     * Called by CacheService when you need to get the originally cached
     * object, not the override.
     */
    synchronized public SailPointObject getOriginalObject() {
        return (_save != null) ? _save : _object;
    }

    /**
     * This is intended for unit tests that want to temporarily swap
     * in a different object and keep it from expiring.
     * Technically we should synchronize but it's enough for the unit tests.
     */
    synchronized public void override(SailPointObject other) {
        // can't push more than one

        if (other != null) {
            trace("Overriding cached object:", other);

            if (_save != null)
                log.error("Cache already overridden!");
            else {
                _save = _object;
                _object = other;
            }
        }
    }

    synchronized public void restore() {
        if (_save == null)
            log.error("Cache not overridden!");
        else {
            _object = _save;
            _save = null;

            trace("Restoring cached object:", _object);
        }
    }

    /**
     * Used by CacheService when it wants to update the object.
     * Be careful not to trash an override if we have one.
     */
    synchronized public void update(SailPointObject obj) {
        if (obj != null) {
            if (_save == null)  {
                _object = obj;
            }
            else {  
                trace("Updating overridden cached object:", obj);

                _save = obj;
            }
        }
    }

    private void trace(String msg, SailPointObject obj) {
        boolean doTrace = false;
        if (doTrace) {
            try {
                System.out.println(msg);
                if (obj != null)
                    System.out.println(obj.toXml());
            }
            catch (Throwable t) {
                log.error(t.getMessage(), t);
            }
        }
    }


}
