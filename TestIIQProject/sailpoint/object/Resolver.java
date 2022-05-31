/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * The interface of an object capable of resolving references
 * to SailPointObjects.  Used by a few classes that normally
 * store references by name, but provide conveinence methods to
 * resolve the object.
 *
 * Try to keep this simple, trying to remove dependency
 * on PersistenceManager and SailPointContext from the object package.
 *
 */

package sailpoint.object;

import java.util.List;
import sailpoint.tools.GeneralException;

/**
 * The interface of an object capable of resolving references
 * to SailPointObjects. Used by a few classes that normally
 * store references by name, but provide convenience methods to
 * resolve the object.
 */
public interface Resolver {

    /**
     * Retrieve an object by id.
     */
    public <T extends SailPointObject> T getObjectById(Class<T> cls, String id)
        throws GeneralException;

    /**
     * Retrieve an object by name.
     */
    public <T extends SailPointObject> T getObjectByName(Class<T> cls, String name)
        throws GeneralException;

    /**
     * Retrieve an object by id or name.
     */
    public <T extends SailPointObject> T getObject(Class<T> cls, String idOrName)
        throws GeneralException;

    /**
     * Get a list of objects matching the query options.
     */
    public <T extends SailPointObject> List<T> getObjects(Class<T> cls,
                                                          QueryOptions ops)
        throws GeneralException;

    
    public <T extends SailPointObject> int countObjects(Class<T> cls, QueryOptions ops)
        throws GeneralException;
};
