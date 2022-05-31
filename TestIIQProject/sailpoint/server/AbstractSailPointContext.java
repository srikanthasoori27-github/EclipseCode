/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * An implementation of SailPointContext that provides some
 * default implementations and stubs out the rest to throw exceptions.
 * Usually more convenient to extend this than implement SailPointContext.
 */

package sailpoint.server;

import java.sql.Connection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.SailPointContext;
import sailpoint.connector.ExpiredPasswordException;
import sailpoint.object.Attributes;
import sailpoint.object.Configuration;
import sailpoint.object.EmailOptions;
import sailpoint.object.EmailTemplate;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.PersistenceOptions;
import sailpoint.object.QueryOptions;
import sailpoint.object.Rule;
import sailpoint.object.SailPointObject;
import sailpoint.object.Script;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

abstract public class AbstractSailPointContext implements SailPointContext {

    static private Log log = LogFactory.getLog(AbstractSailPointContext.class);

    //////////////////////////////////////////////////////////////////////
    //
    // SailPointContext
    //
    //////////////////////////////////////////////////////////////////////

    boolean _scopeResults;
    Identity _impersonator;
    Attributes<String,Object> _properties;

    /**
     * Assume by default contexts are reentrant.
     */
    public SailPointContext getContext() {
        return this;
    }

    public void prepare() {
    }

    public Connection getJdbcConnection() throws GeneralException {
        throw new UnsupportedOperationException();
    }

    public Connection getConnection() throws GeneralException {
        return getJdbcConnection();
    }

    public boolean isClosed() {
        return false;
    }

    public void setUserName(String name) {
    }

    public String getUserName() {
        return null;
    }

    public void impersonate(Identity impersonator) {
        _impersonator = impersonator;
    }

    public void setScopeResults(boolean scopeResults) {
        _scopeResults = scopeResults;
    }

    public boolean getScopeResults() {
        return _scopeResults;
    }
    
    public Configuration getConfiguration() throws GeneralException {
        // no cache here
        return getObjectByName(Configuration.class, Configuration.OBJ_NAME);
    }

    public String encrypt(String src) throws GeneralException {
        throw new UnsupportedOperationException();
    }

    public String encrypt(String src, boolean checkForEncrypted) throws GeneralException {
        throw new UnsupportedOperationException();
    }

    public String decrypt(String src) throws GeneralException {
        throw new UnsupportedOperationException();
    }

    public Identity authenticate(String accountId, String password)
            throws GeneralException, ExpiredPasswordException {
        throw new UnsupportedOperationException();
    }
    
    public Identity authenticate(String accountId, Map<String, Object> options)
            throws GeneralException, ExpiredPasswordException {
    throw new UnsupportedOperationException();
}

    public void sendEmailNotification(EmailTemplate template, EmailOptions options)
        throws GeneralException {
        throw new UnsupportedOperationException();
    }

    public Object runRule(Rule rule, Map<String,Object> params)
        throws GeneralException {
        throw new UnsupportedOperationException();
    }

    public Object runRule(Rule rule, Map<String,Object> params, List<Rule> libraries)
        throws GeneralException {
        throw new UnsupportedOperationException();
    }

    public Object runScript(Script script, Map<String,Object> params)
        throws GeneralException {
        throw new UnsupportedOperationException();
    }

    public Object runScript(Script script, Map<String,Object> params, List<Rule> libraries)
        throws GeneralException {
        throw new UnsupportedOperationException();
    }

    //////////////////////////////////////////////////////////////////////
    //
    // PersistenceManager
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Don't support clone by default.
     */
    public Object clone() throws CloneNotSupportedException {
        throw new CloneNotSupportedException();
    }

    /**
     * Utility to resolve a class, throwing a common exception.
     */
    public Class getClass(String cls) throws GeneralException {
        Class c = null;
        try {
            c = Class.forName(cls);
        }
        catch (ClassNotFoundException e) {
            throw new GeneralException(e);
        }
        return c;
    }

    public void startTransaction() throws GeneralException {
        throw new UnsupportedOperationException();
    }

    public void commitTransaction() throws GeneralException {
        throw new UnsupportedOperationException();
    }

    public void rollbackTransaction() throws GeneralException {
        throw new UnsupportedOperationException();
    }

    public void close() throws GeneralException {
        throw new UnsupportedOperationException();
    }

    public <T extends SailPointObject> T getObjectById(Class<T> cls, String id)
        throws GeneralException {
        throw new UnsupportedOperationException();
    }

    public <T extends SailPointObject> T getObjectByName(Class<T> cls, String name)
        throws GeneralException {
        throw new UnsupportedOperationException();
    }

    /**
     * Lookup an object given either an id or a name.
     * Some classes don't have names, others do but the name may not
     * be unique.  It is recommended that subclasses overload this
     * because it will make two calls, and potentially two queries.  This
     * can usually be optimized by combining both comparisons into one query.
     */
    public <T extends SailPointObject> T getObject(Class<T> cls, String idOrName)
        throws GeneralException {

        T obj = null;

        try {
            // sigh, have to instantiate one to call the naming methods
            SailPointObject so = (SailPointObject)cls.newInstance();

            if (so.hasName())
                obj = getObjectByName(cls, idOrName);
           
            if (obj == null)
                obj = getObjectById(cls, idOrName);

        } catch ( Exception e ) {
            throw new GeneralException(e);
        }
        return obj;
    }

    /**
     * Retrieve an object with a long duration lock.
     */
    public <T extends SailPointObject> T lockObjectById(Class<T> cls, String id, 
                                                        Map<String,Object> options)
        throws GeneralException {

        throw new UnsupportedOperationException();
    }

    public <T extends SailPointObject> T lockObjectByName(Class<T> cls, String name, 
                                                        Map<String,Object> options)
        throws GeneralException {

        throw new UnsupportedOperationException();
    }
    
    public <T extends SailPointObject> T lockObject(Class<T> clazz, LockParameters params) 
        throws GeneralException {
        
        throw new UnsupportedOperationException();
    }
    

    public <T extends SailPointObject> void unlockObject(T object)
        throws GeneralException {

        throw new UnsupportedOperationException();
    }

    public <T extends SailPointObject> T getUniqueObject(T example)
        throws GeneralException
    {
        throw new UnsupportedOperationException();
    }

    public <T extends SailPointObject> T getUniqueObject(Class<T> cls,
                                                         Filter f)
        throws GeneralException
    {
        throw new UnsupportedOperationException();
    }

    public <T extends SailPointObject> List<T> getObjects(Class<T> cls) 
        throws GeneralException {
        throw new UnsupportedOperationException();
    }

    @SuppressWarnings("unchecked")
    public List getObjects(String cname) throws GeneralException {

        return getObjects(getClass(cname));
    }

    public <T extends SailPointObject> List<T> getObjects(Class<T> cls, QueryOptions options) 
        throws GeneralException {
        throw new UnsupportedOperationException();
    }

    public <T extends SailPointObject> Iterator<T> search(Class<T> cls, QueryOptions options)
        throws GeneralException {
        throw new UnsupportedOperationException();
    }
    
    public <T extends SailPointObject> Iterator<Object[]> search(Class<T> cls, QueryOptions options, String properties)
        throws GeneralException {
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

    public int countObjects(Class cls, QueryOptions ops)   
        throws GeneralException {
        throw new UnsupportedOperationException();
    }

    public <T extends SailPointObject> void removeObjects(Class<T> cls,
                        QueryOptions options) throws GeneralException {
        throw new UnsupportedOperationException();
    }

    // don't throw here, the default is to pretend we don't have a cache
    public void attach(SailPointObject object) throws GeneralException {
    }

    public void decache(SailPointObject object) throws GeneralException {
    }

    public void decache() throws GeneralException {
    }
    
    public void clearHighLevelCache()
            throws GeneralException
    {    
    } 

    //////////////////////////////////////////////////////////////////////
    //
    // XMLReferenceResolver
    //
    //////////////////////////////////////////////////////////////////////

    @SuppressWarnings("unchecked")
    public Object getReferencedObject(String className, String id, String name) 
        throws GeneralException {

        if (className == null || className.length() == 0)
            throw new GeneralException("Missing class name");

        // convenience for hand written files
        if (className.indexOf(".") < 0)
            className = "sailpoint.object." + className;

        //log.info("Resolving id " + id + " name " + name);

        SailPointObject obj = null;
        Class cls = getClass(className);

        if (id != null) {
            obj = getObjectById(cls, id);
            if (obj != null && name != null && !name.equals(obj.getName())) {
                // assume a rename and ignore...
                // log something!
            }
        }

        if (obj == null && name != null) {
            if (supportsLookupByName(cls)) {
                obj = getObjectByName(cls, name);
            }
            else {
                if (log.isDebugEnabled()) {
                    String idVal = " is undefined";
                    if (!Util.isNullOrEmpty(id)) {
                        idVal = " = '" + id + "'";
                    }
                    log.debug("Not allowed to resolve a reference to a "+ className +
                            " by name (id" + idVal + ", name = '" + name + "')");
                }
            }
            if (obj != null && id != null) {
                // didn't find it by id, but found it by name,
                // this is more troublesome...
                // log something!
            }
        }

        if (log.isInfoEnabled()) {
            if (obj != null)
                log.info("Resolved " + obj);
            else
                log.info("Unresolved!!");
        }

        return obj;
    }

    /**
     * Based on the given class, determine if the class is a SailPointObject subclass
     * and that it has a unique name that can be used for lookup.
     *
     * @param clazz the class to examine
     * @return true if the class is a subclass of SailPointObject, and if the
     * isNameUnique() method returns true. Otherwise, returns false.
     */
    public static boolean supportsLookupByName(Class clazz) {

        boolean supportsNameLookup = false;

        // NOTE: This implementation unfortunately violates layering because it uses
        // sailpoint.object.SailPointObject.

        if (SailPointObject.class.isAssignableFrom(clazz)) {
            try {
                SailPointObject obj = (SailPointObject)clazz.newInstance();
                supportsNameLookup = obj.hasName() && obj.isNameUnique();
            } catch (Exception e) {
                supportsNameLookup = false;
            }
        }

        return supportsNameLookup;
    }

    ////////////////////////////////////////////////////////////////////////////////
    //
    // Diagnostics
    //
    ////////////////////////////////////////////////////////////////////////////////
    
    public void enableStatistics(boolean b) {
    }

    public void printStatistics() {
    }

    public void reconnect() throws GeneralException {
    }

    public void setPersistenceOptions(PersistenceOptions ops) {
    }
    
    public PersistenceOptions getPersistenceOptions() {
        return null;
    }

    public void setProperty(String name, Object value) {
        if (name != null) {
            if (value != null) {
                if (_properties == null)
                    _properties = new Attributes<String,Object>();
                _properties.put(name, value);
            }
            else if (_properties != null) {
                _properties.remove(name);
            }
        }
    }

    public Object getProperty(String name) {
        return (_properties != null) ? _properties.get(name) : null;
    }


}
