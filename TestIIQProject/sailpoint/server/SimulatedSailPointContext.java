/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * An implementation of SailPointContext that can be used in development
 * scenarios where a real server with persistent storage is not required.
 * Especially handy for UI development.
 *
 * The initial contents of the "persistent store" is defined by a file
 * containing the XML serializations of SailPointObjects.  Thereafter
 * objects are maintained in memory and are not written back to the 
 * source file.  If you need a true persistent store that is still 
 * file-based, use the normal InternalContext with FilePersistenceManager.
 */

package sailpoint.server;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import sailpoint.object.SailPointObject;
import sailpoint.tools.GeneralException;

public class SimulatedSailPointContext extends AbstractSailPointContext {

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Object cache, keyed by Class, value is a Map keyed by name.
     */
    Map mCache = new HashMap();

    /**
     * File containing the initial set of objects.
     */
    String mSource;

    //////////////////////////////////////////////////////////////////////
    //
    // Constructors
    //
    //////////////////////////////////////////////////////////////////////

    public SimulatedSailPointContext() {
    }
    
    public SimulatedSailPointContext(String file) {
        setSource(file);
    }

    public void setSource(String file) {
        mSource = file;
        mCache = new HashMap();

        // read it now or defer?
        if (mSource != null) {

            // TODO: assume the file contains a "simple bean" representation
            // of the classes, need to factor this out of 
            // FilePersistenceManager

        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Object Cache
    //
    //////////////////////////////////////////////////////////////////////

    private SailPointObject getCache(Class c, String name) {
        SailPointObject obj = null;

        Map objects = (Map)mCache.get(c);
        if (objects != null) 
            obj = (SailPointObject)objects.get(name);
        
        return obj;
    }

    private List getCache(Class c) {

        List objlist = null;

        Map objects = (Map)mCache.get(c);
        if (objects != null)
            objlist = new ArrayList(objects.values());
        
        return objlist;
    }

    private void removeCache(Class c, String name) {

        Map objects = (Map)mCache.get(c);
        if (objects != null)
            objects.remove(name);
    }

    private void putCache(SailPointObject obj) {

        if (obj != null && obj.getName() != null) {
            Map objects = (Map)mCache.get(obj.getClass());
            if (objects == null) {
                objects = new HashMap();
                mCache.put(obj.getClass(), objects);
            }
            objects.put(obj.getName(), obj);
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // SailPointContext
    //
    //////////////////////////////////////////////////////////////////////
    
    /**
     * Throw an unknown object exception.
     */
    public void throwUnknown(Class c, String name) throws GeneralException {

        String msg = "Unknown object " + c.getName() + ":" + name;
        throw new GeneralException(msg);
    }

    public <T extends SailPointObject> T getObject(Class<T> cls, String name)
        throws GeneralException {

        return getObjectByName(cls, name);
    }

    public <T extends SailPointObject> T getObjectByName(Class<T> cls, String name)
        throws GeneralException {

        SailPointObject obj = getCache(cls, name);
        
        // TODO: Do we want this to throw, or push that up a level?
        //if (obj == null)
        //throwUnknown(c, name);

        return cls.cast(obj);
    }

    public <T extends SailPointObject> T lockObjectById(Class<T> cls, String id)
        throws GeneralException {

        return getObjectByName(cls, id);
    }

    public <T extends SailPointObject> T lockObjectByName(Class<T> cls, String name)
        throws GeneralException {

        return getObjectByName(cls, name);
    }
    
    public <T extends SailPointObject> T lockObject(Class<T> clazz, LockParameters params) 
        throws GeneralException {
        
        if ("name".equals(params.getColumn())) {
            return getObjectByName(clazz, params.getValue());
        } else if ("id".equals(params.getColumn())){
            return getObjectById(clazz, params.getValue());
        } else {
            throw new IllegalStateException("only name or id is supported for now");
        }
    }
    

    public <T extends SailPointObject> void unlockObject(T object)
        throws GeneralException {
    }

    public void saveObject(SailPointObject obj) throws GeneralException {

        // it just goes back into the cache
        // !! technically we're supposed to check for existing objects
        // with the same name
        putCache(obj);
    }

    public void importObject(SailPointObject obj) throws GeneralException {

        putCache(obj);
    }

    public void removeObject(SailPointObject obj) {

        // it just gets removed from the cache
        removeCache(obj.getClass(), obj.getName());
    }

    public List getObjects(Class c) {

        return getCache(c);
    }
}
