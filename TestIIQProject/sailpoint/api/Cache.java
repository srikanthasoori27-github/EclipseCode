/* (c) Copyright 2020 SailPoint Technologies, Inc., All Rights Reserved. */
/*
 * A class extended by some of the Caches maintained by the CacheService.  When this
 * was originally written, those classes were:
 * 
 * sailpoint.api.CorrelationModel
 * sailpoint.api.PolicyCache
 *
 * The purpose of this is to enable those classes to abort time-consuming processes
 * while the system is trying to shut down
 *
 * @author Bernie Margolis
 */
package sailpoint.api;


public abstract class Cache {
    private static boolean _active = true;

    protected Cache() {
        _active = true;
    }
    
    /**
     * This is called when the system is shutting down.  Each Cache is responsible
     * for ceasing whatever work they're doing in a timely manner after this is called
     * so that IdentityIQ can shut down gracefully.
     */
    public static void deactivate() {
        _active = false;
    }
    
    protected static boolean isActive() {
        return _active;
    }
    
}
