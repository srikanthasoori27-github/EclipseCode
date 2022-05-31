/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * Implementation of PersistenceManager that distributes requests
 * to registered handlers for each object class.  Class handlers
 * can then use JDBC, JNDI, Spring, or Hibernate as appropriate.
 *
 * You can substitute the entire ClassPersistenceManager for
 * testing with something like FilePersistenceManager.  Or you can substitute
 * class-specific handlers for experimentation without disrupting persistence
 * for other classes.
 */

package sailpoint.persistence;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;


import sailpoint.object.Filter;
import sailpoint.object.PersistenceOptions;
import sailpoint.object.QueryOptions;
import sailpoint.object.SailPointObject;
import sailpoint.tools.GeneralException;

import sailpoint.api.PersistenceManager;

public class ClassPersistenceManager implements PersistenceManager, Cloneable {

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Mapping between Class objects and PersistenceManager implementations
     * for that class.
     */
    Map<Class,PersistenceManager> mManagers;

    /**
     * Optional default implementation. 
     * Somtimes useful for testing where you want to use a common
     * manger like FilePersistenceManager for most things but still
     * experiment with one or more class-specific handlers.
     */
    PersistenceManager mDefault;

    //////////////////////////////////////////////////////////////////////
    //
    // Constructors
    //
    //////////////////////////////////////////////////////////////////////

    public ClassPersistenceManager() {
        mManagers = new HashMap<Class,PersistenceManager>();
    }

    /**
     * Set options to control how objects are managed in the Hibernate session.
     */
    public void setPersistenceOptions(PersistenceOptions ops) {

        if (mDefault != null)
            mDefault.setPersistenceOptions(ops);

        for (PersistenceManager pm : mManagers.values())
            pm.setPersistenceOptions(ops);
    }
    
    public PersistenceOptions getPersistenceOptions()  {
        
        PersistenceOptions ops = null;
        if (mDefault != null)
            return mDefault.getPersistenceOptions();
        
        Collection<PersistenceManager> pms = mManagers.values();
       
        if ( pms != null && pms.size() > 0 ) {
            Iterator<PersistenceManager> it = pms.iterator();
            if ( it != null ) {
                while ( it.hasNext() ) {
                    PersistenceManager pm = it.next();
                    if ( pm != null ) {
                        ops = pm.getPersistenceOptions();
                        break;
                    }
                }
            }
        }
        return ops;
    }

    /**
     * Add a class/manager association.
     * I don't think this can be called from Spring, you have to 
     * use setManagers.
     */
    public void addManager(Class cls, PersistenceManager pm) {
        mManagers.put(cls, pm);
    }

    public void setDefaultManager(PersistenceManager pm) {
        mDefault = pm;
    }

    /**
     * Set all of the class/manager associations. 
     * So this can be more easily called from spring, we'll let
     * the map keys be either Class objects or Strings containing
     * class names.
     *
     * Look back at the Spring docs, if feels like there should be
     * a better way to do this?
     */
    public void setManagers(Map src) throws GeneralException {
        if (src != null) {
            Iterator it = src.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry ent = (Map.Entry)it.next();
                Object key = ent.getKey();
                PersistenceManager man = (PersistenceManager)ent.getValue();
                if (key instanceof Class)
                    addManager((Class)key, man);
                else if (key != null) {
                    try {
                        String cname = key.toString();
                        if (cname.indexOf(".") < 0)
                            cname = "sailpoint.object." + cname;
                        Class cls = Class.forName(cname);
                        addManager(cls, man);
                    }
                    catch (ClassNotFoundException e) {
                        // ignore or propgate?
                        throw new GeneralException(e);
                    }
                }
            }
        }
    }

    public PersistenceManager getManager(Class cls) throws GeneralException {

        PersistenceManager pm = mManagers.get(cls);
        if (pm == null) {
            pm = mDefault;
            if (pm == null)
                throw new GeneralException("No persistence handler for class: " + 
                                           cls.getName());
        }

        return pm;
    }

    public PersistenceManager getManager(SailPointObject obj) 
        throws GeneralException {

        return getManager(obj.getClass());
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Cloneable
    //
    //////////////////////////////////////////////////////////////////////

    public Object clone() throws CloneNotSupportedException
    {
        ClassPersistenceManager cpm = new ClassPersistenceManager();

        if (null != this.mDefault)
            cpm.mDefault = (PersistenceManager) this.mDefault.clone();

        if (null != this.mManagers)
        {
            for (Map.Entry<Class, PersistenceManager> entry : this.mManagers.entrySet())
            {
                cpm.addManager(entry.getKey(), (PersistenceManager) entry.getValue().clone());
            }
        }
        return cpm;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // PersistenceManager
    //
    //////////////////////////////////////////////////////////////////////

    public void startTransaction() throws GeneralException
    {
        // All PersistenceManagers should participate in the transaction.
        mDefault.startTransaction();
        for (PersistenceManager pm : this.mManagers.values())
        {
            pm.startTransaction();
        }
    }

    public void commitTransaction() throws GeneralException
    {
        // All PersistenceManagers should participate in the transaction.
        mDefault.commitTransaction();
        for (PersistenceManager pm : this.mManagers.values())
        {
            pm.commitTransaction();
        }
    }

    public void rollbackTransaction() throws GeneralException
    {
        // All PersistenceManagers should participate in the transaction.
        mDefault.rollbackTransaction();
        for (PersistenceManager pm : this.mManagers.values())
        {
            pm.rollbackTransaction();
        }
    }

    public void close() throws GeneralException
    {
        // Close all PersistenceManagers.
        // TODO: This could cause problems if the PersistenceManager is shared.
        mDefault.close();
        for (PersistenceManager pm : this.mManagers.values())
        {
            pm.close();
        }
    }

    public <T extends SailPointObject> T lockObjectById(Class<T> cls, String id,
                                                    Map<String,Object> options) 
        throws GeneralException {
        return getManager(cls).lockObjectById(cls, id, options);
    }

    public <T extends SailPointObject> T lockObjectByName(Class<T> cls, String name,
                                                          Map<String,Object> options) 
        throws GeneralException {
        return getManager(cls).lockObjectByName(cls, name, options);
    }
    
    public <T extends SailPointObject> T lockObject(Class<T> clazz, LockParameters params) 
        throws GeneralException {

        return getManager(clazz).lockObject(clazz, params);
    }
    

    public <T extends SailPointObject> void unlockObject(T object)
        throws GeneralException {
        getManager(object.getClass()).unlockObject(object);
    }

    public <T extends SailPointObject> T getObjectById(Class<T> cls, String id)
        throws GeneralException {
        return getManager(cls).getObjectById(cls,id);
    }

    public <T extends SailPointObject> T getObjectByName(Class<T> cls, String name) 
        throws GeneralException {
        return getManager(cls).getObjectByName(cls, name);
    }
    public <T extends SailPointObject> T getObject(Class<T> cls, String idOrName) 
        throws GeneralException {
        return getManager(cls).getObject(cls, idOrName);
    }

    public <T extends SailPointObject> T getUniqueObject(T example)
        throws GeneralException {

        return getManager(example.getClass()).getUniqueObject(example);
    }

    public <T extends SailPointObject> T getUniqueObject(Class<T> cls, Filter f)
        throws GeneralException {

        return getManager(cls).getUniqueObject(cls, f);
    }

    public void saveObject(SailPointObject obj) throws GeneralException {

        getManager(obj).saveObject(obj);
    }

    public void importObject(SailPointObject obj) throws GeneralException {

        getManager(obj).importObject(obj);
    }

    public void removeObject(SailPointObject obj) throws GeneralException {
        getManager(obj).removeObject(obj);
    }

    public <T extends SailPointObject> void removeObjects(Class<T> cls,
                        QueryOptions options) throws GeneralException {

        PersistenceManager manager = getManager(cls);
        getManager(cls).removeObjects(cls, options);
    }


    public <T extends SailPointObject> List<T> getObjects(Class<T> cls) 
        throws GeneralException 
    {
        return getManager(cls).getObjects(cls);
    }

    public <T extends SailPointObject> List<T> getObjects(Class<T> cls, QueryOptions options) 
        throws GeneralException 
    {
        return getManager(cls).getObjects(cls, options);
    }

    public <T extends SailPointObject> Iterator<T> search(Class<T> cls, QueryOptions options)
        throws GeneralException
    {
        return getManager(cls).search(cls, options);
    }

    public <T extends SailPointObject> Iterator<Object[]> search(Class<T> cls, QueryOptions options, String properties) 
        throws GeneralException 
    {
        return getManager(cls).search(cls, options, properties);
    }

    public <T extends SailPointObject> Iterator<Object[]> search(Class<T> cls, QueryOptions options, List<String> properties) 
        throws GeneralException 
    {
        return getManager(cls).search(cls, options, properties);
    }

    public Iterator search(String query, Map<String,Object> args, QueryOptions options)
        throws GeneralException 
    {
        // Here's where the class dispatching breaks down.
        // In theory the query can involve multiple classes, but they
        // would all have to share the same persistence manager.  Rather than
        // requiring a gratuitous class argument, assume that the default
        // manager will deal with all of these.
        return mDefault.search(query, args, options);
    }

    public int update(String query, Map<String,Object> args) 
        throws GeneralException
    {
        return mDefault.update(query, args);
    }
    

    public int countObjects(Class cls, QueryOptions options)
        throws GeneralException
    {
        return getManager(cls).countObjects(cls, options);
    }

    public void attach(SailPointObject obj) throws GeneralException
    {
        getManager(obj).attach(obj);
    }

    public void decache(SailPointObject obj) throws GeneralException
    {
        getManager(obj).decache(obj);
    }

    public void decache() throws GeneralException
    {
        mDefault.decache();
        for (PersistenceManager pm : this.mManagers.values())
        {
            pm.decache();
        }
    }
    
    public void clearHighLevelCache()
        throws GeneralException
    {
        mDefault.clearHighLevelCache();
        for (PersistenceManager pm : mManagers.values()) {
            pm.clearHighLevelCache();
        }    
    }

    public void enableStatistics(boolean b) {
        mDefault.enableStatistics(b);
        for (PersistenceManager pm : this.mManagers.values()) {
            pm.enableStatistics(b);
        }
    }

    public void printStatistics() {
        mDefault.printStatistics();
        for (PersistenceManager pm : this.mManagers.values()) {
            pm.printStatistics();
        }
    }

    public void reconnect() throws GeneralException {
        mDefault.reconnect();
        for (PersistenceManager pm : this.mManagers.values()) {
            pm.reconnect();
        }
    }
}
