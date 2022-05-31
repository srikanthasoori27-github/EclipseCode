/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.persistence;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.beanutils.BeanUtils;

import sailpoint.api.PersistenceManager;
import sailpoint.object.Filter;
import sailpoint.object.PersistenceOptions;
import sailpoint.object.QueryOptions;
import sailpoint.object.SailPointObject;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

abstract public class AbstractPersistenceManager implements PersistenceManager, Cloneable
{
    // ignore persistence options
    public void setPersistenceOptions(PersistenceOptions ops) {
    }
    
    public PersistenceOptions getPersistenceOptions() {
        throw new UnsupportedOperationException();
    }

    // By default, transaction methods will be a no-op.
    public void startTransaction() {}

    public void commitTransaction() {}

    public void rollbackTransaction() {}

    public void close() {}

    public Object clone() throws CloneNotSupportedException {
        return super.clone();
    }

    public <T extends SailPointObject> T getObjectById(Class<T> cls, String id)
        throws GeneralException
    {
        throw new UnsupportedOperationException();
    }
    public <T extends SailPointObject> T getObjectByName(Class<T> cls, String name)
        throws GeneralException
    {
        throw new UnsupportedOperationException();
    }
    public <T extends SailPointObject> T getObject(Class<T> cls, String idOrName)
        throws GeneralException
    {
        // assume name is more common
        T obj = getObjectByName(cls, idOrName);
        if (obj == null)
            obj = getObjectById(cls, idOrName);
        return obj;
    }

    public void saveObject(SailPointObject obj) 
        throws GeneralException
    {
        throw new UnsupportedOperationException();
    }

    public void removeObject(SailPointObject obj)
        throws GeneralException
    {
        throw new UnsupportedOperationException();        
    }

    public <T extends SailPointObject> List<T> getObjects(Class<T> cls) 
        throws GeneralException
    {
        throw new UnsupportedOperationException();                
    }

    public <T extends SailPointObject> List<T> getObjects(Class<T> cls, QueryOptions options) 
        throws GeneralException
    {
        throw new UnsupportedOperationException();                
    }

    public <T extends SailPointObject> List<T> getObjects(T example) 
        throws GeneralException
    {
        throw new UnsupportedOperationException();                
    }

    public int countObjects(Class cls, QueryOptions ops) 
        throws GeneralException
    {
        throw new UnsupportedOperationException();                
    }

    public <T extends SailPointObject> Iterator<T> search(Class<T> cls, QueryOptions options)
        throws GeneralException
    {
        throw new UnsupportedOperationException();                
    }

    public <T extends SailPointObject> Iterator<Object[]> search(Class<T> cls, QueryOptions options, String properties) 
        throws GeneralException 
    {
        return this.search(cls, options, Util.csvToList(properties));
    }

    public <T extends SailPointObject> Iterator<Object[]> search(Class<T> cls, QueryOptions options, List<String> properties) 
        throws GeneralException 
    {
        throw new UnsupportedOperationException();                
    }

    public Iterator search(String query, Map<String,Object> args, QueryOptions options)
        throws GeneralException 
    {
        throw new UnsupportedOperationException();
    }
    
    public int update(String query, Map<String,Object> args) 
    throws GeneralException
    {
        throw new UnsupportedOperationException();
    }

    public void attach(SailPointObject obj) throws GeneralException
    {
    }

    // don't throw here, the default is to just pretend we don't have a cache

    public void decache(SailPointObject obj) throws GeneralException
    {
    }

    public void decache() throws GeneralException
    {
    }

    public void clearHighLevelCache()
        throws GeneralException
    {    
    }
    
    //////////////////////////////////////////////////////////////////////
    //
    // Query by Example
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Return an object whose properties match those of an example object.
     * I'm trying not to push this down into PersistenceManager since
     * it can be implemented generically out here.
     *
     * This actually sucks because BeanUtils isn't smart about what's
     * really a property, anything starting with "get" comes back.
     * 
     */
    @SuppressWarnings("unchecked")
    public <T extends SailPointObject> T getUniqueObject(T example)
        throws GeneralException {

        T object = null;
        QueryOptions ops = new QueryOptions();

        // hmm, this is going to return everything that has a read method, 
        // what I'd like is only things that have a read/write pair
        Map props = null;
        try {
            props = BeanUtils.describe(example);
        }
        catch (java.lang.Exception e) {
            // this can throw IllegalAccessException, InvocationTargetException
            throw new GeneralException(e);
        }

        if (props != null) {
            Iterator it = props.entrySet().iterator();
            while (it.hasNext()) {  
                Map.Entry ent = (Map.Entry)it.next();
                String name = (String)ent.getKey();
                Object value = ent.getValue();
                if (name != null && !name.equals("class") && 
                    value != null) {
                        
                    ops.add(Filter.eq(name, value));
                }
            }
        }

        // hmm, what's worse running the query twice or getting
        // a potentially large result set and throwing it away
        // if (countObjects(example.getClass(), ops) == 1) {

        Iterator<T> it = (Iterator<T>) search(example.getClass(), ops);
        if (it.hasNext()) {
            object = it.next();
            if (it.hasNext()) {
                // oops, too many
                object = null;
            }
        }

        return object;
    }

    @SuppressWarnings("unchecked")
    public <T extends SailPointObject> T getUniqueObject(Class<T> cls,
                                                         Filter filter)
        throws GeneralException {

        T object = null;
        QueryOptions ops = new QueryOptions();
        ops.add(filter);

        // hmm, what's worse running the query twice or getting
        // a potentially large result set and throwing it away
        // if (countObjects(example.getClass(), ops) == 1) {

        Iterator<T> it = (Iterator<T>) search(cls, ops);
        if (it.hasNext()) {
            object = it.next();
            if (it.hasNext()) {
                // oops, too many
                object = null;
            }
        }

        return object;
    }

    public void enableStatistics(boolean b) {
    }

    public void printStatistics() {
    }

    public void reconnect() throws GeneralException {
    }

    public <T extends SailPointObject> void removeObjects(Class<T> cls,
                        QueryOptions options) throws GeneralException {

        throw new UnsupportedOperationException();
    }

    public <T extends SailPointObject> T lockObject(Class<T> clazz, LockParameters params) throws GeneralException {

        throw new UnsupportedOperationException();
    }
    
}
