/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
/**
 * Interface that must be implemented by components that provide
 * persistence services within SailPointContext.  
 *
 * SailPointContext itself extends this interface, factored out
 * PersistenceManager for the internal components that don't need to 
 * implement the other things in SailPointContext.
 *
 * There are several levels of stubbing possible:
 *
 *   - Configure SailPointFactory to return a completely custom
 *     SailPointContext implementation.  In this case you don't even need
 *     a PersistenceManager.
 * 
 *   - Configure SailPointFactory to return an InternalContext
 *     then configure Internal Context to have a completely custom
 *     PersistenceManager.  Useful for unit testing server components
 *     with a simple memory or file-based storage manager like 
 *     FilePersistenceManager.
 *
 *   - SailPointFactory has an InternalContext, InternalContext has
 *     a ClassPersistenceManager which in turn has configurable
 *     implementatins of PersistenceManager for each object class.
 *     Implementations can use JDBC, JNDI, Spring, or Hibernate as 
 *     appropriate for each class.
 *
 */

package sailpoint.api;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

import sailpoint.object.Filter;
import sailpoint.object.PersistenceOptions;
import sailpoint.object.QueryOptions;
import sailpoint.object.Resolver;
import sailpoint.object.SailPointObject;

/**
 * Interface implemented by classes that provide persistence services.
 */
public interface PersistenceManager extends Resolver
{

    //////////////////////////////////////////////////////////////////////
    //
    // Constants
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Option for lockObject that specifies the type of lock to 
     * be acquired. The default is a persistent lock.
     */
    public static final String LOCK_TYPE = "lockType";

    /**
     * A value for the LOCK_TYPE option that selects a transaction lock.
     * 
     * A transaction lock is held only for the duration of the current
     * transaction, it corresponds to a Hibernate fetch with 
     * LockMode.UPGRADE, which in turn corresponds to a SQL 
     * select with the "for update" option.
     *
     * If another transaction has locked this object, the calling
     * thread will suspend until the other transaction completes
     * (or the request times out).
     *
     * This type of lock is appropriate for internal background tasks
     * that need exclusive access to an object for a short duration
     * and do not need the overhead of a persistent lock.
     */
    public static final String LOCK_TYPE_TRANSACTION = "transaction";

    /**
     * A value for the LOCK_TYPE option that selects a persistent lock.
     * 
     * A persistent lock is an application-level locking convention
     * that uses a special column in the object table to indicate
     * the lock status. A persistent lock can span transactions.
     * 
     * Persistent locks are appropriate if you need to lock 
     * an object for a long period of time, or keep an object locked
     * across a transaction boundary.  
     *
     * This type of lock is most appropriate for editing sessions in 
     * the UI. Because it must modify the locked object it has more
     * overhead than a transaction lock which makes it less suitable
     * for background tasks that scan many objects.
     */
    public static final String LOCK_TYPE_PERSISTENT = "persistent";
    
    //////////////////////////////////////////////////////////////////////
    //
    // Methods
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Optional clone method. Not necessarily implemented.
     * 
     * @throws CloneNotSupportedException
     */
    public Object clone() throws CloneNotSupportedException;

    /**
     * Begin a transaction. This is implicitly called before any other
     * operation if the transaction has not yet been started.
     */
    public void startTransaction() throws GeneralException;

    /**
     * Commit the current transaction that was started with
     * {@link #startTransaction}.
     */
    public void commitTransaction() throws GeneralException;

    /**
     * Rollback the current transaction that was started with
     * {@link #startTransaction}.
     */
    public void rollbackTransaction() throws GeneralException;

    /**
     * Close this PersistenceManager and release any resources in use. If there
     * is an active transaction, this is rolled back. This is a
     * package-protected method because it should only be called by
     * <code>SailPointFactory.releaseContext()</code>.
     */
    void close() throws GeneralException;

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
     * Retrieve an object with a long-duration lock.
     */
    public <T extends SailPointObject> T lockObjectById(Class<T> cls, String id,
                                                    Map<String,Object> options)
        throws ObjectAlreadyLockedException, GeneralException;

    /**
     * Retrieve an object with a long-duration lock.
     */
    public <T extends SailPointObject> T lockObjectByName(Class<T> cls, String name,
                                                    Map<String,Object> options)
        throws ObjectAlreadyLockedException, GeneralException;

    /**
     * Save and unlock an object.  
     * This will commit the transaction.
     * Note that this method does not care who you are and if
     * you hold the lock, it simply releases the current lock.
     *
     * @ignore
     * Unclear if we need any special exceptions here.  If the object
     * isn't locked it will just be silently ignored.  
     */
    public <T extends SailPointObject> void unlockObject(T object)
        throws GeneralException;

    /**
     * Retrieve an object whose properties match those specified in
     * an example object. If more than one object match the criteria,
     * null is returned.
     *
     * Note that this practically useless because if the class contains
     * boolean or integer fields you have to set them, there is no  
     * "nullness" of primitive types that keeps them out of the criteria.
     * For example searching for ManagedAttribute by value will fail
     * to find anything with the "uncorrelated" property true.
     */
    public <T extends SailPointObject> T getUniqueObject(T example)
        throws GeneralException;

    /**
     * Retrieve an object matching a Filter. If more than one object matches
     * the filter null is returned.
     */
    public <T extends SailPointObject> T getUniqueObject(Class<T> cls, Filter f)
        throws GeneralException;

    /**
     * Save an object in the persistent store.
     */
    public void saveObject(SailPointObject obj) 
        throws GeneralException;

    /**
     * Import an object into the persistent store.
     * This is intended to be used only from the Importer.
     * It differs from saveObject in that it will allow the overwriting
     * of an existing object with the same name.
     */
    public void importObject(SailPointObject obj) 
        throws GeneralException;

    /**
     * Remove an object from the persistent store.
     */
    public void removeObject(SailPointObject obj)
        throws GeneralException;

    /**
     * Bulk remove object based on the Filter defined in the 
     * QueryOptions.
     */
    public <T extends SailPointObject> void removeObjects(Class<T> cls, 
                                                          QueryOptions ops)
        throws GeneralException;

    /**
     * Return all objects of a given class. Useful for things like
     * Reports and JobSchedules, but be careful with objects that can
     * have lots of instances.  
     */
    public <T extends SailPointObject> List<T> getObjects(Class<T> cls)
        throws GeneralException;

    /**
     * Return all objects of a given class, constrained by Filters
     * in the QueryOptions. Note that although you can specify them,
     * Projections in the QueryOptions are not supported for this 
     * method. If you need to use Projections, use the search method.
     */
    public <T extends SailPointObject> List<T> getObjects(Class<T> cls,
                                                          QueryOptions ops) 
        throws GeneralException;

    /**
     * Counts the number of objects that match a criteria.
     * 
     * @ignore
     * After writing this, I see now that we could have accomplished
     * something similar with the Projection metaphor, but I kind of
     * like the clarity of an explicit method, and I'd rather not drag
     * in the complexity of Projections until we're sure we need that
     * level of generality.
     */
    public int countObjects(Class cls, QueryOptions options)
        throws GeneralException;

    /**
     * Return a set of objects matching the query options.
     * NOTE: Although this returns an iterator, implementations are
     * actually fetching all the objects into memory so use this with
     * care.
     * OTHER NOTE: This non-projection variation of search() does not 
     * honor distinct=true on the QueryOptions. However, getObjects() does.
     * Consider using that instead. 
     * @see #getObjects(Class, QueryOptions)
     */
    public <T extends SailPointObject> Iterator<T> search(Class<T> cls, QueryOptions options)
        throws GeneralException;

    /**
     * Perform a projection search for selected object properties and return
     * an iterator. This will return an iterator that encapsulates a database
     * cursor so it will fetch gradually. This is what you should use if
     * you have a potentially large set of results. This is similar to
     * {@link #search(Class, QueryOptions, List)} but accepts the properties as
     * a comma-separated string rather than a list.
     * 
     * NOTE: If iterators are not exhausted, they can result in a leak of open cursor
     * objects.  To prevent this leak, call {@link Util#flushIterator(java.util.Iterator)} 
     * after a search is performed (following the iteration process).  This ensures 
     * that the iterator is closed out correctly and the database cursor is released. 
     * 
     * @param  cls         The Class to search over.
     * @param  options     The QueryOptions to use.
     * @param  properties  A comma-separated list of properties to return.
     */
    public <T extends SailPointObject> Iterator<Object []> search(Class<T> cls, QueryOptions options, String properties)
        throws GeneralException;

    /**
     * Perform a projection search for selected object properties and return
     * an iterator. This will return an iterator that encapsulates a database
     * cursor so it will fetch gradually. This is what you should use if
     * you have a potentially large set of results.
     *
     * NOTE: If iterators are not exhausted, they can result in a leak of open cursor
     * objects.  To prevent this leak, call {@link Util#flushIterator(java.util.Iterator)}
     * after a search is performed (following the iteration process).  This ensures 
     * that the iterator is closed out correctly and the database cursor is released.
     */
    public <T extends SailPointObject> Iterator<Object []> search(Class<T> cls, QueryOptions options, List<String> properties)
        throws GeneralException;

    /**
     * 
     * @param query It can be either hql or sql. If it is sql it should be prepended with "sql:"
     * @param args Parameters for the query
     * @return number of rows updated
     */
    public int update(String query, Map<String,Object> args) throws GeneralException;
    
    /**
     * Perform a search specified in an HQL or SQL string.
     * By default the query string is parsed as HQL. If you
     * want to use SQL, prefix the string with "sql:".
     * 
     * What you get back from the iterator depends on the query.
     * For HQL queries that return objects it will be an Iterator<SailPointObject>.
     * For SQL queries or HQL queries that return projections it will be Iterator<Object[]>.
     */
    public Iterator search(String query, Map<String,Object> args, QueryOptions ops)
        throws GeneralException;

    /**
     * Attach the given SailPointObject possibly loaded by a different
     * SailPointContext to this SailPointContext.
     */
    public void attach(SailPointObject object) throws GeneralException; 

    /**
     * Remove the object from the cache.
     * Necessary if you are processing large numbers of objects in the
     * same session.
     */
    public void decache(SailPointObject obj) throws GeneralException;

    /**
     * Remove all objects the cache.
     */
    public void decache() throws GeneralException;

    /**
     * Set options to control how objects are managed in the Hibernate session.
     */
    public void setPersistenceOptions(PersistenceOptions ops);
    
    /**
     * Get current options that control how objects are managed in the Hibernate session.
     */
    public PersistenceOptions getPersistenceOptions();

    //
    // Various diagnostics, try not to get too carried away with these
    //
    
    /**
     * Enable or disable collection of statistics.
     */
    public void enableStatistics(boolean b);

    /**
     * Print the collected statistics.
     */
    public void printStatistics();

    /**
     * Reconnect the PersistenceManager to the persistent store (for example, by
     * reconnecting to the database). This should almost never be used.
     * Consider using {@link #decache()} instead.
     */
    public void reconnect() throws GeneralException;
    
    /**
     * Clears a "high level" cache. This method has the effect of removing objects
     * from a cache (if one exists) that is above the session level. For Hibernate, this
     * method clears the second level cache.
     */
    public void clearHighLevelCache() throws GeneralException;
    
    /**
     * Encapsulation of lock parameters.
     *
     * @ignore
     * TODO: move to separate file
     */
    public static class LockParameters {
        
        private static String COL_ID = "id";
        private static String COL_NAME = "name";

        private String column;
        private String value;
        private String lockType;
        // Time in minutes
        private int lockDuration;
        // time in seconds to stop trying to get the lock
        private int lockTimeout;
        
        /**
         * Return the column that is used to find the object to lock - typically
         * either name or id.
         */
        public String getColumn() {
            return this.column;
        }
        public void setColumn(String val) {
            this.column = val;
        }

        /**
         * Return the value to use to look up the object to lock. The value is
         * typically an object ID or name depending on the getColumn().
         */
        public String getValue() {
            return this.value;
        }
        public void setValue(String val) {
            this.value = val;
        }

        /**
         * Return the type of lock - one of {@link PersistenceManager#LOCK_TYPE_PERSISTENT}
         * or {@link PersistenceManager#LOCK_TYPE_TRANSACTION}.
         */
        public String getLockType() {
            return this.lockType;
        }
        public void setLockType(String val) {
            this.lockType = val;
        }
        
        /**
         * Return the duration in minutes of the lock.
         */
        public int getLockDuration() {
            return this.lockDuration;
        }
        public void setLockDuration(int val) {
            this.lockDuration = val;
        }
        
        /**
         * Return the timeout in seconds before trying to obtain a lock fails.
         */
        public int getLockTimeout() {
            return lockTimeout;
        }
        public void setLockTimeout(int val) {
            lockTimeout = val;
        }

        /**
         * Set the ID of the object to lock.
         */
        public void setId(String id) {
            this.column = COL_ID;
            this.value = id;
        }

        /**
         * Set the name of the object to lock.
         */
        public void setName(String name) {
            this.column = COL_NAME;
            this.value = name;
        }

        /**
         * Constructor.
         */
        public LockParameters() {
        }

        /**
         * Constructor from a SailPointObject, that creates a persistent lock
         * with a generated lock name.
         *
         * @param obj  The SailPointObject for which to create the LockParameters.
         */
        public LockParameters(SailPointObject obj) {

            if (obj.getId() != null)
                setId(obj.getId());
            else
                setName(obj.getName());

            setLockType(LOCK_TYPE_PERSISTENT);
        }

        /**
         * Convenience method to create default locking object that locks by ID
         * with a generated lock name and the given ID and type.
         *
         * @ignore
         * TODO: change lockType to ENUM
         */
        public static LockParameters createById(String id, String lockType) {
            LockParameters params = new LockParameters();
            params.setColumn("id");
            params.setValue(id);
            params.setLockType(lockType);
            return params;
        }

        /**
         * Convenience method to create default locking object that locks by
         * name with a generated lock name and the given name and type.
         *
         * @ignore
         * TODO: change lockType to ENUM
         */
        public static LockParameters createByName(String name, String lockType) {
            LockParameters params = new LockParameters();
            params.setColumn("name");
            params.setValue(name);
            params.setLockType(lockType);
            return params;
        }
        
        /**
         * Return whether this is a transaction lock.
         */
        public boolean isTransactionLock() {

            return getLockType() != null && getLockType().equals(LOCK_TYPE_TRANSACTION);
        }
    }
    
    /**
     * This method should be the entry point for all public locking calls.
     * Other locking methods are there for backward compatibility and will delegate to this.
     * @param clazz The sailpoint class type of the object 
     * @param params contains all the information pertinent to locking.
     * @return the locked object
     * @throws GeneralException 
     */
    public <T extends SailPointObject> T lockObject(Class<T> clazz, LockParameters params) throws GeneralException;
}
