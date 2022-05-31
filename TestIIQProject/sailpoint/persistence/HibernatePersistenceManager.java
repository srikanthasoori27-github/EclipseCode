/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * An implementation of the PersistenceManager interface
 * that interfaces with Hibernate.
 *
 * Author: Rob, Jeff, Kelly
 *
 */

package sailpoint.persistence;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hibernate.Cache;
import org.hibernate.CacheMode;
import org.hibernate.Criteria;
import org.hibernate.FlushMode;
import org.hibernate.Hibernate;
import org.hibernate.LockMode;
import org.hibernate.LockOptions;
import org.hibernate.Query;
import org.hibernate.ReplicationMode;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.hibernate.boot.Metadata;
import org.hibernate.criterion.Criterion;
import org.hibernate.criterion.Example;
import org.hibernate.criterion.ProjectionList;
import org.hibernate.criterion.Projections;
import org.hibernate.criterion.Restrictions;
import org.hibernate.dialect.Dialect;
import org.hibernate.engine.spi.CollectionKey;
import org.hibernate.engine.spi.EntityKey;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.mapping.PersistentClass;
import org.hibernate.metadata.ClassMetadata;
import org.hibernate.persister.entity.AbstractEntityPersister;
import org.hibernate.stat.SessionStatistics;
import org.hibernate.stat.Statistics;
import org.hibernate.transform.DistinctRootEntityResultTransformer;

import sailpoint.api.ObjectAlreadyLockedException;
import sailpoint.api.ObjectUtil;
import sailpoint.api.PersistenceManager;
import sailpoint.api.SailPointContext;
import sailpoint.object.Certification;
import sailpoint.object.CertificationArchive;
import sailpoint.object.Filter;
import sailpoint.object.Filter.BooleanOperation;
import sailpoint.object.Filter.CompositeFilter;
import sailpoint.object.Filter.LeafFilter;
import sailpoint.object.Filter.LogicalOperation;
import sailpoint.object.Identity;
import sailpoint.object.LockInfo;
import sailpoint.object.PersistenceOptions;
import sailpoint.object.QueryOptions;
import sailpoint.object.QueryOptions.Ordering;
import sailpoint.object.SailPointObject;
import sailpoint.object.TaskResult;
import sailpoint.object.WorkItem;
import sailpoint.object.WorkItemArchive;
import sailpoint.search.ExtendedAttributeVisitor;
import sailpoint.server.InternalContext;
import sailpoint.tools.GeneralException;
import sailpoint.tools.OracleConnectionHandler;
import sailpoint.tools.Reflection;
import sailpoint.tools.Util;

public class HibernatePersistenceManager
    implements PersistenceManager, Cloneable
{
    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    static private Log log = LogFactory.getLog(HibernatePersistenceManager.class);

    static private Log HQL_LOG = LogFactory.getLog("sailpoint.persistence.hql");

    public static String ORACLE_CONNECTION_HANDLER = "sailpoint.tools.OracleConnectionHandlerImpl";

    public static boolean TraceCommit = false;

    /**
     * Temporary Spring config option to enable lock/thread consistency checking.
     * Eventually this will be the default, but allow it to be disabled in 
     * hibernateBeans.xml for emergencies.  
     * Static so we can get to it easily from SailPointInterceptor.
     */
    static public boolean EnableThreadLockConsistencyCheck = false;

    /**
     * Temporary Spring config option to make thread lock consistency errors to 
     * throw exceptions rather than log a warning.  Static so we can get to it easily
     * from SailPointInterceptor.
     */
    static boolean ThrowOnThreadLockInconsistency = false;

    /**
     * Special prefix found on ids created from unit test init files.
     * These are recognized during the saving of an imported object
     * and converted into a name.  This obscurity is necessary to 
     * prevent duplicating objects whose names are not declared to be unique.
     */
    public static final String TEST_ID_PREFIX = "TEST:";

    protected SessionFactory _sessionFactory;
    private Session _session = null;
    protected boolean _disableOptimizer;
    protected boolean _disableNewLocking;
    protected boolean _enableSqlServerEnhancedLocking;
    private boolean _statistics = false;

    /**
     * A boolean we can use to indicate when we've already closed ther
     * peristense manager.
     */ 
    private boolean _isClosed;

    /**
     * The Hibernate Dialect of the database.
     */
    private static Dialect _dialect;

    /**
     * Derived from the dialect.
     */
    private static boolean _usingOracle;

    private static boolean _usingSqlServerDbLocking;

    private SailPointInterceptor _interceptor;

    /**
     * Oracle specific setting to indicates to Oracle that every time you go to
     * the next item in the underlying ResultSet that it should get the next N 
     * rows worth of results in one roundtrip to the database not just the next 
     * row (which is the default and which is remote and very costly over a WAN).
     * This setting is configurable in the hibernateBeans.xml file
     */
    int _oracleRowPrefetch = -1;

    private boolean _enableWorkgroupEqFalse = false;

    // hack session tracker
    static boolean _traceSessions = false;
    static int _sessionsOpen = 0;

    static synchronized void incSessionsOpen() {
        _sessionsOpen++;
        if (log.isWarnEnabled())
            log.warn("Opening session: " + Util.itoa(_sessionsOpen));
    }
    
    static synchronized void decSessionsOpen() {
        if (log.isWarnEnabled())
            log.warn("Closing session: " + Util.itoa(_sessionsOpen));
        
        _sessionsOpen--;
    }
    
    private static Map<String, Set<String>> _propertyNameCache = new HashMap<String, Set<String>>();
    private static Set<Class> _managedClasses;

    /**
     * Spring settable option to override the transaction isolation level
     * we request for the Connection.
     */
    static int _isolationLevel = 0;

    /**
     * Isolation level setter for Spring.
     */
    public void setIsolationLevel(int level) {
        _isolationLevel = level;
    }

    /**
     * Setters for Spring config.
     */
    public void setEnableThreadLockConsistencyCheck(boolean b) {
        EnableThreadLockConsistencyCheck = b;
    }

    public void setThrowOnThreadLockInconsistency(boolean b) {
        ThrowOnThreadLockInconsistency = b;
    }

    public void setEnableWorkgroupEqFalse(boolean enable) {
        this._enableWorkgroupEqFalse = enable;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Constructors
    //
    //////////////////////////////////////////////////////////////////////

    public HibernatePersistenceManager() {
        _interceptor = new SailPointInterceptor();
        _interceptor.setHibernatePersistenceManager(this);
    }

    /**
     * Obtain a handle to the SessionFactory. To be set by spring.
     */
    public void setSessionFactory(SessionFactory sessionFactory)
    {
        _sessionFactory = sessionFactory;

        // Attempt to figure out what dialect we are dealing with 
        // so if necessary the UserTypes can do special handling
        // i.e. Oracle's CLOB support
        if ( _dialect == null ) {
            _usingOracle = false;
            _usingSqlServerDbLocking = false;
            if ( _sessionFactory instanceof SessionFactoryImplementor ) {
                SessionFactoryImplementor impl = (SessionFactoryImplementor)_sessionFactory;
                _dialect =  impl.getDialect();
                if ( _dialect != null ) {
                    String dialect = _dialect.toString().toLowerCase();
                    // Do a contains here so we don't have to add a new 
                    // mapping if we ever move to an Oracle10Dialect
                    if ( dialect.contains("oracle") ) {
                        _usingOracle = true;
                    } else if (dialect.contains("sqlserver") && (_enableSqlServerEnhancedLocking)) {
                        _usingSqlServerDbLocking = true;
                    }
                }
            }
        }
    }
    
    /**
     * Flag to disable the query optimizer, intended to be set
     * from the Spring config file.  This should only be turned
     * in if you think the optimizer is broken.
     */
    public void setDisableOptimizer(boolean b) {
        _disableOptimizer = b;
    }

    /**
     * Setting to tell oracle how many rows to fetch from a given result set.
     * Defaults to just 1, but can be overridden in the hibernateBeans.xml.
     */
    public void setOracleRowPrefetch(int prefetch) {
        _oracleRowPrefetch = prefetch;
    }

    public void setDisableNewLocking(boolean b) {
        _disableNewLocking = b;
    }

    public void setEnableSqlServerEnhancedLocking(boolean enableSqlServerEnhancedLocking) {
        this._enableSqlServerEnhancedLocking = enableSqlServerEnhancedLocking;
    }

    /**
     * Set options to control how objects are managed in the Hibernate session.
     */
    public void setPersistenceOptions(PersistenceOptions ops) {
        _interceptor.setPersistenceOptions(ops);
    }
    
    public PersistenceOptions getPersistenceOptions() {
        return _interceptor.getPersistenceOptions();
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Cloneable
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Have to support clone because SailPointContext implementations
     * are treated like prototypes, we can derive one from another.
     * InternalContext implements this by cloning all the persistence
     * managers.  You have to remember to copy over all the Spring
     * configuration properies.  Sigh.
     */
    @Override
    public Object clone()
    {
        HibernatePersistenceManager hpm = new HibernatePersistenceManager();
        hpm.setSessionFactory(_sessionFactory);
        hpm.setDisableOptimizer(_disableOptimizer);
        hpm.setDisableNewLocking(_disableNewLocking);
        hpm.setEnableSqlServerEnhancedLocking(_enableSqlServerEnhancedLocking);
        hpm.setEnableWorkgroupEqFalse(_enableWorkgroupEqFalse);
        return hpm;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // PersistenceManager
    //
    //////////////////////////////////////////////////////////////////////

    public static final String PROP_LOG_SESSIONS = "logSessions";

    /**
     * Hack for tracking down a strange duplicate Connection.
     */
    private void logSession(boolean open) {

        // this was only used once to track down something in Identitizer,
        // leave around for awhile
        sailpoint.api.SailPointContext spc = sailpoint.api.SailPointFactory.peekCurrentContext();
        if (spc == null) {
            // how can this happen?
            log.error("Session action without SailPointContext");
        }
        else if (spc.getProperty(PROP_LOG_SESSIONS) != null) {
            // only do this for certain marked contexts
            if (open) {
                log.warn("Opening session for context: " + spc.toString());
            }
            else {
                log.warn("Closing session for context: " + spc.toString());
            }
        }

        // an older thing, hasn't been used in years
        if (_traceSessions) {
            if (open)
                incSessionsOpen();
            else
                decSessionsOpen();
        }
    }
    
    /**
     * Return the Session to use for this HibernatePersistenceManager.
     */
    protected Session getSession() {
        return getSession(false);
    }
    
    protected Session getSession(boolean dirtyReads)
    {
        checkForPreviouslyClosedSession();
        if (null == _session)
        {
            // Originally we set the interceptor with Spring's LocalSessionFactoryBean 
            // but that gets shared by every session, since SailPointInterceptor is
            // stateful we can't do that.
            // old way
            //_session = _sessionFactory.openSession(_interceptor);
            // new way
            _session = _sessionFactory.withOptions()
                    .interceptor(_interceptor)
                    .openSession();
            logSession(true);

            // We require READ_COMMITTED.  
            // The default seems random, at home I usually get REPEATABLE_READ.
            // READ_COMMITTED is necessary so that queries to check to 
            // see if things exist before creating them like we do for
            // ManagedAttributes and AccountGroups immediately see objects
            // committed in other transactions.
            try {

                //TODO: Figure this out!!!
//                session.doWork(new Work() {
//                    @Override
//                    public void execute(Connection connection) throws SQLException {
//                        //connection, finally!
//                    }
//                });
                //TODO: orrrr
///**
// * Ugly kludge to dig the JDBC Connection out of a Session.
// * This was deprecated and removed in 4.x, there is a new doWork
// * interface that might be usable but it is unclear if this gives
// * you the same Connection that the session is using or if this
// * just makes a temporary connection.  We need the actual session
// * connection to set some driver properties, it feels like that would
// * be much better done in Spring config of the connection pool, but
// * we've got that weird Oracle specific thing.
// * This example was scraped off Stackoverflow:
// // http://stackoverflow.com/questions/3526556/session-connection-deprecated-on-hibernate
// */
//                private java.sql.Connection getSessionConnection(Session ses) {
//                    // this may not always work
//                    return ((org.hibernate.internal.SessionImpl)ses).connection();
//                }


                //java.sql.Connection con = _session.connection();
                java.sql.Connection con = ((org.hibernate.internal.SessionImpl)_session).connection();

                // an option for search(), where is this used?
                if (!_usingOracle) {
                    con.setReadOnly(dirtyReads);
                }

                int currentLevel = con.getTransactionIsolation();

                // Hack for SQLServer, if an alternative isolation level was set by Spring
                // use that instead of TRANSACTION_READ_COMMITTED.  This is typically 4096
                // for TRANSACTION_READ_SNAPSHOT
                int desiredLevel = _isolationLevel;
                if (desiredLevel == 0) {
                    desiredLevel = java.sql.Connection.TRANSACTION_READ_COMMITTED;
                }
                
                if (currentLevel != desiredLevel) {
                    if (log.isInfoEnabled()) {
                        log.info("Changing isolation level from " + Util.itoa(currentLevel) +
                                 " to " + Util.itoa(desiredLevel));
                    }
                    con.setTransactionIsolation(desiredLevel);
                }

            }
            catch (Throwable t) {
                log.error("Unable to set isolation level", t);
            }

            // Setting DefaultRowPrefetch tells the driver how many results to bring back from the server at a time.
            // Increasing this should help performance over a WAN see this BUG#4925 for more information
            if ( ( _session != null ) && ( _usingOracle ) && ( _oracleRowPrefetch != -1 ) ) {
                try {
                    //TODO: Still needed? -rap
                    OracleConnectionHandler handler = (OracleConnectionHandler)Util.createObjectByClassName(ORACLE_CONNECTION_HANDLER);
                    if ( handler != null ) {
                        handler.setDefaultRowPrefetch(((org.hibernate.internal.SessionImpl)_session).connection(), _oracleRowPrefetch);
                    }
                } catch(Exception e) {
                    if (log.isErrorEnabled())
                        log.error("Error while attempting to setDefaultRowPrefetch on " + 
                                  "session's Oracle connection: " + e.getMessage(), e);
                }
            }
        } else {
            
            java.sql.Connection con = ((org.hibernate.internal.SessionImpl)_session).connection();
            try {
                int level = con.getTransactionIsolation();
                if(!_usingOracle) {
                    con.setReadOnly(dirtyReads);
                }
                if (level != java.sql.Connection.TRANSACTION_READ_COMMITTED) {
                    con.setTransactionIsolation(java.sql.Connection.TRANSACTION_READ_COMMITTED);
                    log.debug("Uncommitted read session reset to committed");
                }
            }
            catch (Throwable t) {
                log.error("Unable to check isolation level", t);
            }
        }
        return _session;
    }

    /**
     * Begin the Hibernate transaction.
     *
     * UPDATE: The default automatic session flushing on searches causes
     * a number of problems for objects that are edited over several
     * Hibernate sessions (e.g. a web application that does not use
     * the same Hibernate session on each request).  
     *
     * One of them is that you have avoid putting an object with the 
     * same name as an existing object in the cache or you get an 
     * obscure error message "expecting 1 got 2, who knew!".  This
     * can be avoided by being careful not to attach new objects until
     * you check to see if the name has been reserved, but it puts
     * more complexity in the UI.
     *
     * Another is the more annoying problem that unique ids get
     * generated for new child objects if cascacd="all" the moment
     * they are attached, but if you rollback the transaction the ids
     * are not taken away which causes confusing when this object
     * is attached to the next session.  There doesn't appear to be a good
     * solution to that other than manually nulling the ids before
     * attaching the second time.
     * jsl - check this, does disabling flush actually solve this?
     * 
     */
    public void startTransaction() throws GeneralException
    {
        try
        {
            Session session = getSession();
            if (session.isOpen())
            {
                // Don't start a transaction if there is already an active one.
                Transaction t = session.getTransaction();
                if (!t.isActive()) {
                    session.beginTransaction();
                    session.setHibernateFlushMode(FlushMode.COMMIT);
                }
            }
        }
        catch (Throwable e)
        {
            throw new GeneralException(e);
        }
    }

    /**
     * Commit the currently active transaction.
     */
    public void commitTransaction() throws GeneralException
    {
        try
        {
            if (_session != null && _session.isOpen()) {
                
                if (log.isDebugEnabled()) {
                    log.debug("Session before commit...");
                    printStatistics();
                }

                if (TraceCommit)
                    printCommitStatistics();

                // jsl - get errors if you try to commit a dormant transaction
                // so always check for active
                Transaction t = _session.getTransaction();
                if (t.isActive())
                    t.commit();

                // jsl - Always keep a transaction going!  This
                // is necessary for some background tasks where
                // the transaction may be committed as a side effect
                // of updating task progress, but the task still needs
                // to mark objects dirty and commit again.  If you don't
                // do this, the objects modified after the first commit
                // will never be flushed.
                // Use safe startTransaction instead of _session.beginTransaction -rap
                startTransaction();
            }
        }
        catch (Throwable e)
        {
            throw new GeneralException(e);
        }

        // diagnostics
        _interceptor.logTransaction();
    }

    /**
     * Rollback the currently active transaction.
     *
     * Note that Transaction.rollback does not clear the session cache
     * which can lead to surprising behavior.  We force the session
     * cache to be cleared after a rollback.  The consequence of this
     * however is that everying that was in the cache becomes detached, so the
     * application must reattach it if lazy evaluation is to continue.
     *
     * jsl - I'm not sure I like this.  It might make sense to clear
     * the cache (what would Hibernate do if you called getObject() with
     * stale objects in the cache?).  But this has evolved to calling
     * close() which means you can't be iterating over a search result
     * and rolling back.  This seems odd.
     */
    public void rollbackTransaction() throws GeneralException
    {
        try
        {
            if (_session != null) {

                // jsl - get errors if you try to commit a dormant transaction
                if (_session.isOpen()) {
                    Transaction t = _session.getTransaction();
                    if (t.isActive())
                        t.rollback();
                }

                // jsl - rather than just clearing the cache, let's
                // start all over with a fresh session for extra safety,
                // any bad consequences?
                logSession(false);
                _session.close();
                _session = null;
            }
        }
        catch (Throwable e)
        {
            throw new GeneralException(e);
        }

        // diagnostics
        _interceptor.resetTransactionLog();
    }

    /**
     * Close the Session and rollback the transaction if it is active.
     *
     * Closing the Hibernate session will release the JDBC connection, 
     * though the docs are confusing.  If H gets the connection
     * through the configured ConnectionProvider disconnect() will
     * have no effect unless ConnectionReleaseMode.ON_CLOSE is in effect.
     * The difference between close() and disconnect() is unclear.
     *
     * I don't think it is strictly necessary to rollback the transaction
     * but we've done it that way for awhile.
     * 
     */
    public void close() throws GeneralException
    {
        try
        {
            if (_session != null) {

                // jsl - get errors if you try to commit a dormant transaction
                if (_session.isOpen()) {
                    Transaction t = _session.getTransaction();
                    if (t.isActive())
                        t.rollback();

                    logSession(false);
                    _session.close();
                    _session = null;
                }
            }
        }
        catch (Throwable e)
        {
            throw new GeneralException(e);
        }

        // We have historically set this flag which will prevent
        // this object from being reused.  This seems gratuitous, 
        // why not just recreate a session if you try to use it again?
        // Were we looking for a connection leak?
        _isClosed = true;
    }

    /**
     * Retrieve an object by id.
     */
    public <T extends SailPointObject> T getObjectById(Class<T> cls, String id) 
        throws GeneralException 
    {
        T rv = null;
        if (Util.isNotNullOrEmpty(id)) {
            try {
                startTransaction();
                Session session = getSession();
                rv = cls.cast(session.get(cls, id));
            } catch (Throwable e) {
                throw new GeneralException(e);
            }
        }
        return rv;
    }

    /**
     * Retrieve an object by name.
     */
    public <T extends SailPointObject> T getObjectByName(Class<T> cls, String name) 
        throws GeneralException 
    {
        try
        {
            startTransaction();
            Session session = getSession();
            Query q = getByNameQuery(session, cls, name);
            return getSingleResult(cls, q);
        }
        catch (Throwable e)
        {
            throw new GeneralException(e);
        }
    }
    
    /**
     * Return a Query that searches for an object of the given type by name.
     * The results are cached.
     */
    private Query getByNameQuery(Session session,
                                 Class<? extends SailPointObject> cls,
                                 String name) {
        // Directly create the query to avoid the overhead of the Filter -> HQL
        // translation layer.
        Query q =
            session.createQuery("select o from " + cls.getName() + " o where name = :name");
        q.setParameter("name", name);

        // iiqpb-346, caching getByName results causes problems for TaskResult which
        // can be deleted and recreated rapidly in some cases, typically during testing,
        // but could happen normally.  The query cache causes Hibernate to try and load
        // the object using the old id which is now gone.  In theory we have this problem
        // for other classes but the chances are much lower.  To get beyond this bug, ignore
        // this for TaskResult, but we should really explore whether we need this at all.
        // If the perf tests don't change, then just take it out.
        if (!cls.equals(TaskResult.class)) {
            q.setCacheable(true);
        }
        
        return q;
    }

    /**
     * Return a single result from the given query or null if there are no
     * results.  If there is more than one result an exception is thrown.
     */
    @SuppressWarnings("unchecked")
    public <T extends SailPointObject> T getSingleResult(Class<T> cls, Query q)
        throws GeneralException {

        T rv = null;
        List<T> results = q.list();
        if ((null != results) && !results.isEmpty()) {
            if (results.size() > 1) {
                throw new GeneralException("Expected one result.");
            }
            rv = results.get(0);
        }
        return rv;
    }

    /**
     * Find uniqueResult by idOrName. If multiple results, a HibernateException will be thrown
     * NOTE: This will not cache, so use carefully
     */
    public <T extends SailPointObject> T getObject(Class<T> cls, String idOrName)
            throws GeneralException
    {
        try
        {
            startTransaction();
            Session session = getSession();

            Criterion crit;
            if (ObjectUtil.hasName(null, cls)) {
                crit = Restrictions.or(Restrictions.eq("name", idOrName), Restrictions.eq("id", idOrName));
            } else {
                crit = Restrictions.eq("id", idOrName);
            }
            return cls.cast(session.createCriteria(cls)
                    .add(crit)
                    .uniqueResult());
        }
        catch (Throwable e)
        {
            throw new GeneralException(e);
        }
    }

    /**
     * Get the lock type from the options, filtering invalid ones.
     * The filter is necessary to protect from tasks using "none"
     * or something to indiciate that no lock should be obtained.
     * Collapse this to null so we don't have to check everywhere.
     */ 
    private String getLockType(Map<String,Object> options) {

        String lockType = Util.getString(options, LOCK_TYPE);
        if (lockType != null && 
            !lockType.equals(LOCK_TYPE_TRANSACTION) &&
            !lockType.equals(LOCK_TYPE_PERSISTENT))
            lockType = null;

        return lockType;
    }

    /**
     * Retrieve an object and obtain a long duration lock.
     */
    public <T extends SailPointObject> T lockObjectById(Class<T> cls, String id,
                                                        Map<String,Object> options) 
        throws GeneralException {

        T object = null;

        LockParameters params = createLockParametersFromOptions(options);
        params.setColumn("id");
        params.setValue(id);
        
        if (_disableNewLocking) {
            object = lockWithDisableNewLocking(cls, id, true, params, object);
        }
        else {
            object = lockObject(cls, params);
        }
        
        return object;
    }

    /**
     * Retrieve and lock an object by name.
     *
     * Note that this will commit the transaction!  We could try to 
     * do this in another session but that seems like overkill.
     */
    public <T extends SailPointObject> T lockObjectByName(Class<T> cls, String name, 
                                                        Map<String,Object> options) 
        throws GeneralException {
        
        T object = null;

        LockParameters params = createLockParametersFromOptions(options);
        params.setColumn("name");
        params.setValue(name);
        
        if (_disableNewLocking) {
            object = lockWithDisableNewLocking(cls, name, false, params, object);
        }
        else {
            object = lockObject(cls, params);
        }
        
        return object;
    }
    
    /**
     * Creates new LockParameters object from the options map. 
     * There is only one thing now that we dropped LOCK_NAME.
     */
    private LockParameters createLockParametersFromOptions(Map<String, Object> options) {
        
        LockParameters params = new LockParameters();

        String lockType = getLockType(options);

        params.setLockType(lockType);
        
        return params;
    }
    
    /**
     * I believe this method is there to support locking the "old way"
     * @param value is either "id" or "name"
     * @param isId if true it means it is id otherwise it is name.
     */
    private <T extends SailPointObject> T lockWithDisableNewLocking(Class<T> cls, String value, boolean isId, LockParameters params, T object)
            throws GeneralException {

        try {
            startTransaction();
            Session session = getSession();
        
            if (isId) {
                object = obtainObjectForLockingById(cls, value, params.getLockType(), session);
            } else {
                object = obtainObjectForLockingByName(cls, value, params.getLockType(), session);
            }

            if (!params.isTransactionLock()) {
                checkLock(object);
            }
        }
        catch (Throwable e) {
            throw new GeneralException(e);
        }
        return object;
    }

    @SuppressWarnings("deprecation")
    private <T extends SailPointObject> T obtainObjectForLockingById(Class<T> cls, String id, String lockType, Session session) {

        // if there is any kind of lock always start with a transaction lock
        LockMode mode = (lockType != null) ? LockMode.PESSIMISTIC_WRITE : null;
        return cls.cast(session.get(cls, id, mode));
    }
    
    private <T extends SailPointObject> T obtainObjectForLockingByName(Class<T> cls, String name, String lockType, Session session) {

        Criteria crit = session.createCriteria(cls);
        crit.add(Restrictions.eq("name", name));
        crit.setCacheable(true);

        // if there is any kind of lock always start with a transaction lock
        if (lockType != null)
            crit.setLockMode(LockMode.PESSIMISTIC_WRITE);

        return cls.cast(crit.uniqueResult());
    }

    /**
     * Inner object locker supporting either id or name.
     * The technique goes like this:
     *
     * 1) Issue an update statement to set the lock if the current
     *    lock is null.  If this succeeds (result row count == 1) 
     *    then we know we own the lock and can proceed to fetch the object.
     *
     * 2) If the lock update fails then either the object does not exist
     *    or the lock is non-null.  Issue another query to get both
     *    the name and the lock columns.  Use "pessimistic read" to get a 
     *    row lock.
     *
     * 3) If the result of 2 comes back empty then the object does not exist,
     *    return null.
     *
     * 4) If the result of 2 is not empty but the lock column is null, then
     *    there was a small window between 1 and 2 where another transaction
     *    released the lock.  Do the update from step 1 again, since we have
     *    a transaction lock at this point we don't have to check for new locks.
     *
     * 5) If the result of 2 is not empty and the lock column is non-null,
     *    then check to see if we own it or if it has expired.  If we do own
     *    it or if it has expired than issue an update to refresh the lock 
     *    timeout and change the owner.
     *
     * 6) If the result of 2 is not empty, the lock column is non-null and
     *    we do not own it, then throw an object locked exception.
     * 
     */
    public <T extends SailPointObject> T lockObject(Class<T> cls, 
                                                     LockParameters params) 
        throws GeneralException {
        
        T object = null;

        try {
            startTransaction();
            Session session = getSession();

            if (params.isTransactionLock()) {
                // the old way 
                Criteria crit = session.createCriteria(cls);
                crit.add(Restrictions.eq(params.getColumn(), params.getValue()));
                crit.setCacheable(false);
                crit.setLockMode(LockMode.PESSIMISTIC_WRITE);

                // need to ignore the cache if we are locking
                CacheMode prevCacheMode = session.getCacheMode();
                try {
                    session.setCacheMode(CacheMode.IGNORE);
                    object = cls.cast(crit.uniqueResult());
                    // bug#20455 make sure it is fresh
                    session.refresh(object);
                }
                finally {
                    session.setCacheMode(prevCacheMode);
                }
            }
            else {
                // inner lock method can recurse on itself 
                object = lockObject(session, cls, params);
            }
        }
        catch (ObjectAlreadyLockedException e) {
            throw e;
        }
        catch (Throwable e) {
            throw new GeneralException(e);
        }

        return object;
    }


    /**
     * Inner locker take 2.
     *
     * The whole reason for this contortion is to lock an object without
     * fetching the whole damn thing then flushing it.  There is only
     * one important lock column, we can update that directly with HQL
     * and save an object round-trip.  Yes, this is significant.
     *
     * The basic approach is this:
     *
     *    select name,lock from foo where id==? with row lock
     *
     * That either acquires a row lock, or waits until another thread
     * releases it.
     *
     * If the lock is non-null and not expired throw an excception.
     * If the lock is null or expired do an HQL single column update
     * to set the lock and commit.  
     *
     * Finally fetch the object and deal with the cache.
     */
    private <T extends SailPointObject> T lockObject(Session session,
                                                      Class<T> cls,
                                                      LockParameters params)
        throws GeneralException {
        
        T object = null;
        
        Session lockSession = _sessionFactory.openSession();
        logSession(true);
        Transaction t = lockSession.getTransaction();
        if (!t.isActive()) {
            //Ensure we're in a transaction otherwise pessimistic
            //locking will not happen.
            lockSession.beginTransaction();
            lockSession.setHibernateFlushMode(FlushMode.COMMIT);
        }

        try {
            // Acquire a row lock
            String clsName = cls.getSimpleName();
            Query q = null;

            if(usingSqlServerDbLocking()) {
                ClassMetadata metadata = _sessionFactory.getClassMetadata(cls);
                if(metadata instanceof AbstractEntityPersister) {
                    AbstractEntityPersister persister = (AbstractEntityPersister) metadata;
                    String[] lockColNames = persister.getPropertyColumnNames("lock");
                    String[] nameColNames = persister.getPropertyColumnNames("name");
                    if(lockColNames != null && lockColNames.length == 1 &&
                            nameColNames != null && nameColNames.length == 1) {
                        String tableName = persister.getTableName();
                        StringBuilder sqlBuilder = new StringBuilder();
                        sqlBuilder.append("select id1.");
                        sqlBuilder.append(nameColNames[0]);
                        sqlBuilder.append(", id1.");
                        sqlBuilder.append(lockColNames[0]);
                        sqlBuilder.append(" from ");
                        sqlBuilder.append(tableName);
                        sqlBuilder.append(" as id1 with (updlock, rowlock) where ");
                        sqlBuilder.append("id1.");
                        sqlBuilder.append(params.getColumn());
                        sqlBuilder.append(" = :id");

                        q = lockSession.createSQLQuery(sqlBuilder.toString());
                    }
                }
            }

            if(q == null) {
                String hql = "select id1.name, id1.lock from " + clsName + " as id1 where " +
                        "id1." + params.getColumn() + " = :id";

                q = lockSession.createQuery(hql);
            }

            LockOptions lo = new LockOptions();
            lo.setLockMode(LockMode.PESSIMISTIC_WRITE);
            q.setLockOptions(lo);

            // get a transaction lock on this row
            q.setString("id", params.getValue());

            List result = q.list();

            if (result == null || result.size() == 0) {
                // object doesn't exist
                /*
                * IIQETN-300 :- While trying to lock an object, if the result is null we should call rollbackTransaction
                */
                rollbackTransaction(lockSession);
            }
            else if (result.size() > 1) {
                // can't happen, return null
                log.error("Lock query returned more than one row!");
            }
            else {
                Object[] row = (Object[])result.get(0);
                if (row == null) {
                    // what might this mean? assume object doesn't exist
                    log.error("Unexpected lock query result");
                    rollbackTransaction(lockSession);
                }
                else {
                    // check existing lock
                    boolean alreadyLocked = false;
                    String lockName = LockInfo.getThreadLockId();
                    String curlock = (String)row[1];
                    if (curlock != null) {
                        LockInfo lockInfo = new LockInfo(curlock);
                        String lockedBy = lockInfo.getName();
                        if (lockedBy != null && 
                            !lockedBy.equals(lockName) &&
                            !lockInfo.isExpired()) {
                            // someone else owns it
                            rollbackTransaction(lockSession);
                            throw new ObjectAlreadyLockedException("Object is already locked by " +
                                                                   lockedBy);
                        }

                        // Set a flag if we had already aquired the lock
                        // this is used by Terminator and ObjectUtil.unlockIfNecessary to avoid
                        // clearing the lock column of an object previously locked by this thread.
                        // We still update the lock and fetch a fresh version but we have to remember
                        // not to clear the lock when done cleaning up the Terminator dependency.
                        alreadyLocked = (lockedBy != null && lockedBy.equals(lockName));
                    }

                    // commit the lock
                    LockInfo lockInfo = new LockInfo(lockName);
                    if (params.getLockDuration() == 0) {
                        // use default
                        lockInfo.refresh();
                    } else {
                        lockInfo.refresh(params.getLockDuration());
                    }

                    String lock = lockInfo.render();
                    String hql = "update " + clsName + " set lock = :lock where " +
                        params.getColumn() + " = :id";

                    q = lockSession.createQuery(hql);
                    q.setString("lock", lock);
                    q.setString("id", params.getValue());
                    int rowCount = q.executeUpdate();
                    if (rowCount == 0) {
                        // update returned zero, this also shouldn't happen since
                        // we acquired the row lock first
                        log.error("Lock commit found no row!");
                        rollbackTransaction(lockSession);
                    }
                    else if (rowCount > 1) {
                        // this can't happen on the id column, return null
                        log.error("Lock updated more than one row!");
                        // it is important we release the row locks
                        rollbackTransaction(lockSession);
                    }
                    else {
                        // commit the lock update and go get the object
                        t = lockSession.getTransaction();
                        if (t.isActive())
                            t.commit();

                        object = fetchLockedObject(session, cls, params, lock);
                        // set flag we calculated earlier
                        object.setRefreshedExistingLock(alreadyLocked);
                    }
                }
            }
        }
        finally {
            lockSession.close();
            logSession(false);
        }
        
        return object;
    }

    /**
     * After sucesfully updating the lock column, fetch a new version of the object.
     */
    private <T extends SailPointObject> T fetchLockedObject(Session session,
                                                            Class<T> cls,
                                                            LockParameters params,
                                                            String lock)
        throws GeneralException {
        
        T object = null;
        
        LoadDetector detector = new LoadDetector(params.getColumn(), params.getValue());
        _interceptor.setLoadDetector(detector);

        // note that we are getting it using the normal session, not the lockSession
        Criteria crit = session.createCriteria(cls);
        crit.add(Restrictions.eq(params.getColumn(), params.getValue()));
        // need to ignore the cache if we are locking
        // turns out this only works for the 2nd level cache
        CacheMode prevCacheMode = session.getCacheMode();
        try {
            session.setCacheMode(CacheMode.IGNORE);
            object = cls.cast(crit.uniqueResult());
        }
        finally {
            session.setCacheMode(prevCacheMode);
            _interceptor.setLoadDetector(null);
        }

        boolean alreadyInSession = !detector.isTripped();
                
        if (object == null) {
            // something bad happened
            log.error("Lost object after lock update");
        }
        else {
            // Lock needs to be sure that it returns the absolute most
            // recent object.  Another thread could have updated this
            // object and the cached version could be stale.  Evict and
            // reload.  See bug 8019.
            // Is there a way to do this without having to retrieve the
            // object before evicting to avoid a duplicate load.  Or
            // possibly only evicting if the object was in the session
            // before this method was called?
            if (alreadyInSession) {
                try {
                    session.refresh(object);
                    object = cls.cast(crit.uniqueResult());
                } catch (org.hibernate.UnresolvableObjectException | javax.persistence.EntityNotFoundException uoe) {
                    // IIQETN-6487 - We can get this exception when an Identity has had it's
                    // scorecard or entitlement group updated in another thread and this
                    // instance of the Identity has a reference to the old one. For instance, if
                    // a refresh thread and a provisioning thread are modifying the same Identity
                    // and the refresh thread modifies the scorecard or group before the
                    // provisioning thread can lock the Identity then the refresh will fail.
                    // There's a  pretty small window for this to happen but if it does we need
                    // to boot the object out of the session and reload it.
                    if (log.isInfoEnabled()) {
                        log.info("UnresolvableObjectException while refreshing [" +
                                object + "], message - " + uoe.getMessage() + ". Evicting object and reloading.");
                    }
                    String id = object.getId();
                    session.evict(object);
                    object = cls.cast(session.get(cls, id));
                }
            }
            else {
                if (log.isInfoEnabled()) 
                    log.info("WOOHOO!  Avoided a second load for " + object);
                        
            }

            // old code had checks for this, I don't think this can happen
            // anymore since we either did session.refresh or got a completely
            // new one which should have the HQL set lock column

            String curlock = object.getLock();
            if (curlock == null) {
                log.error("Refreshed object didn't have the lock set");
                object.setLock(lock);
            }
            else if (!curlock.equals(lock)) {
                // Someone else would probably be mad that we have stolen their object
                rollbackTransaction(session);
                throw new GeneralException("Unexpected lock string after locking: " + curlock);
            }
        }

        return object;
    }

    /**
     * Rollback our private lock session.
     */
    private void rollbackTransaction(Session s) {

        Transaction t = s.getTransaction();
        if (t.isActive())
            t.rollback();
    }

    /**
     * After fetching an object for locking, check the existing lock.
     */
    private void checkLock(SailPointObject object)
        throws GeneralException {

        String lockName = LockInfo.getThreadLockId();
        
        if (object != null) {
            LockInfo lock = object.getLockInfo();
            if (lock != null) {
                String lockedBy = lock.getName();

                if (lockedBy != null && 
                    !lockedBy.equals(lockName) &&
                    !lock.isExpired()) {
                    // we had a transaction lock, release it
                    // sigh, this makes it impossible to lock more
                    // than one object with different strategies, not likely
                    rollbackTransaction();

                    // should we show the name of the locking user?
                    throw new ObjectAlreadyLockedException("Object is already locked by " +
                                                           lockedBy);
                }

                // here we could leave the existing lock in place,
                // or "refresh" it to have a longer timeout, 
                // assuming refresh is ok
                lock.setName(lockName);
                lock.refresh();
                object.setLock(lock.render());
            }   
            else {
                lock = new LockInfo(lockName);
                lock.refresh();
                object.setLock(lock.render());
            }

            // commit the application lock, and release the transaction lock
            // the object remains attached
            commitTransaction();
        }
    }

    /**
     * Unlock an object, if it is locked.
     * If the object isn't locked it is ignored.
     * Note that this behaves like a saveObject/commitTransaction combo
     * we don't just clear the lock, any other modifications to the object
     * will also be flushed.
     */
    public <T extends SailPointObject> void unlockObject(T object)
        throws GeneralException {

        if (object.getLock() == null) {
            // if you didn't get a lock, this reverts to a save?
            // hmm, we don't commit here which is inconsistent with
            // the case where we do have a lock, but you really shouldn't
            // be calling this without a lock.  Maybe it is best just
            // to ignore the whole thing?
            saveObject(object);
        }
        else {
            // TODO: do we need to get a transaction lock on this?
            // probably not since no one else can lock this object
            object.setLock(null);
            saveObject(object);
            commitTransaction();
        }
    }

    @SuppressWarnings("unchecked")
    public <T extends SailPointObject> T getUniqueObject(T template)
        throws GeneralException 
    {
        T object = null;
        try
        {
            // note that this doesn't support extended attribute mapping,
            // but due to the primitive value problem it is almost
            // useless anyway

            startTransaction();
            Session session = getSession();
            Example example = Example.create(template);
            Class cls = getTheClass(template);
            Criteria criteria = session.createCriteria(cls);
            criteria.add(example);
            List<T> objects = (List<T>)criteria.list();
            if (objects != null && objects.size() == 1)
                object = objects.get(0);
        }
        catch (Throwable e)
        {
            throw new GeneralException(e);
        }
        return object;
    }

    @SuppressWarnings("unchecked")
    public <T extends SailPointObject> T getUniqueObject(Class<T> cls,
                                                         Filter filter)
        throws GeneralException 
    {
        T object = null;
        try
        {
            startTransaction();
            Session session = getSession();

            QueryOptions ops = new QueryOptions();
            ops.add(filter);
            orderAndOptimize(ops, cls, null);

            HQLFilterVisitor v = visitHQLFilter(cls, ops, null);
            String hql = v.getQueryString();
            if (log.isDebugEnabled()) {
                log.debug("HQL: " + hql);
                log.debug("Params: " + v.getParameterMap());
            }

            Query q = getQuery(session, hql, v.getParameterMap(),
                               v.spansMultipleTables(), ops, null);

            List<T> objects = (List<T>) q.list();
            if (objects != null && objects.size() == 1)
                object = objects.get(0);
        }
        catch (Throwable e)
        {
            throw new GeneralException(e);
        }
        return object;
    }

    /**
     * Save an object in the persistent store.
     * This is intended to be called when you are updating an existing
     * object you obtained from a Hibernate session, or are creating
     * a new object for the first time.  This differs from importObject()
     * in that we will not allow you to save a new object that has
     * the same name as an existing object.
     */
    public void saveObject(SailPointObject obj) 
        throws GeneralException 
    {
        saveInternal(obj, false, true);
    }

    /**
     * Save an object in the persistent store.
     * This is intended to be called by the importer when loading 
     * an initialization file.
     */
    public void importObject(SailPointObject obj) 
        throws GeneralException 
    {
        saveInternal(obj, true, true);
    }

    /**
     * Save an object in the persistent store.
     *
     * Note that some operations need to reliably determine if this
     * is a new object being created or an existing object.  The seemingly
     * obvious way (which Hibernate also uses) is to look for a non-null id.
     * Unfortuantely Hibernate will generate ids whenever a new object
     * is attached (with or without flushing), and this id will not be
     * cleared if the object is then detached.  So objects can have ids
     * even if they are not actually persisted.  This causes other problems
     * but we can't fix them at this point because you can't clear out
     * bogus ids after the object has been attached.  It's a long story...
     *
     * I considered using the creation date and adding an Interceptor
     * and set it reliably onSave.  The problem is that like the id, this
     * gets generated when you flush, but if you then roll back the 
     * transaction the date is left behind and we can no longer tell
     * if this is a new object.  In theory we could keep a list of
     * all objects we intercepted onSave and then clear the dates
     * if the transaction is rolled back (I think we can get this
     * reilably from the Interceptor).
     *
     * Since we can't use createData reliably either, assume for now
     * that the UI is careful not to attach or call saveObject for
     * new objects and that FlushMode is COMMIT so we use the id
     * semi-reliably.
     * 
     * This is complicated due to the possibility that we're loading
     * XML serialized objects from files rather than always working 
     * within a Hibernate session.  The special cases we need to
     * catch are:
     *
     * - Object has id, but doesn't exist in Hibernate
     *   UPDATE: We no longer do this!!
     *
     * This happens if you checkout or export an object, delete it,
     * then later import the file.  It can also happen when transfering
     * objects between databases.  Hibernate does not let us specify our
     * own ids for new objects.  If it seens one it expects it to
     * exist and if not generates a "Batch update returned unexpected row
     * count from update: 0 actual row count: 0 expected: 1" exception
     * when the transaction is eventually committed.
     *
     * Since this is a relatively common situation, we'll bypass the exception
     * by always checking to see if an object with that id exists, and if not
     * clearing the id before saving it.  
     * NOTE: If this object references other internal objects that also have
     * ids, we'll have the same problems.  Need to recurse over the children?
     *
     * - Object has id, exists in Hibernate, but as a different Java object
     *
     * This happens if you import a file more than once in the same session.
     * We will create a new object for everything in the file, but when you
     * try to add this to the session you get a "different object with the
     * same identifer value was already associated with the session" exception.
     * Handling this by evicting the existing object before adding the new one.
     * NOTE: Maybe should be using replicate() here instead?
     * 
     * - Object has no id, but an object with this name already exists
     *
     * This happens if you import an initialization file that has object
     * names but not ids.  We look for an existing object with that name
     * and if found, set the id of the incomming object and perform a
     * replicate/overwrite.  This has to be done recursively for
     * all child objects as well.
     * 
     */
    public void saveInternal(SailPointObject obj, boolean importing,
                             boolean hasIndependentLife)
        throws GeneralException {

        try {
            startTransaction();

            // Explicitly calling saveObject implies dirty
            obj.setDirty(true);

            // jsf pages commonly set fields to the empty string
            // rather than null, it is important that we ignore
            // empty strings in the id field
            if (obj.getId() != null && obj.getId().length() == 0)
                obj.setId(null);

            // This should now be handled by the interceptor.  Previously
            // not setting this on cascades.
            //Date now = new Date();
            //obj.setModified(now);

            // promote searchable attributes 
            promoteExtendedAttributes(obj);

            //TODO: need to figure out what we want to do wrt id's
            Session session = getSession();

            if (session.contains(obj)) {
                // Object is already in the session and will
                // be flushed on the next commit.  

                // NOTE: If you have been modifying something that is
                // serialized as XML, it won't persist unless you modify
                // some other property.  Assuming that if you call
                // saveObject you want it flushed, tickle a property
                // to force it dirty.

                // Didn't see anything obvious in the Hibernate docs
                // no Session.flush(obj), I tried assigning a property
                // to itself but that doesn't seem to be enough.
                //obj.setCreated(obj.getCreated());

                // Nor does modifying something then setting it back,   
                // it probably does before/after comparison using equals
                //String orig = obj.getDescription();
                //obj.setDescription("foo");  
                //obj.setDescription(null);

                // Even though we do it again the interceptor, this is
                // enough to trigger the flush
                obj.setModified(new Date());

                // bug#801, be proactive and try to detect renames
                // that will result in a constraint violations on commit.
                // This isn't solving the entire problem, but catches
                // the common cases for the UI.
                // UPDATE: This is too expensive, it adds up to 30% overhead
                // to identity updates in the aggregator.  Since we don't
                // have a way to pass options down from the application we'll
                // push rename checking up to the UI layer which seems reasonable.
                // Applications are now expected to call 
                // ObjectUtil.checkIllegalRename before saving.
                //if (!importing)
                //checkIllegalRename(obj);
            }
            else {

                // if this has already been persisted and there is another
                // object with this id in the cache, evict it
                // NOTE WELL: We did this up until 3.2p1 when I noticed
                // that this does not do a cache probe, it actually fetches
                // the damn thing into the cache which we then evict.  
                // The eviction doesn't always cascade which can cause
                // problems if we're saving something that has tentacles,
                // resulting in "multiple instances of the same collection"
                // and other delights.  Since I can't find a reliable way
                // to do a cache probe, make the caller deal with this.
                // Typically if this object is detached they will already
                // have been using decache().  Hmm, we might have to force
                // this through the getObjectToReplicate process to get things
                // stitched up properly?
                // !! there are several unit test failures if we take this out
                // (some in RoleLifeTests, some in cert tests, some reports).
                // They can arguably all be fixed but I'm worried about possible
                // UI consequences that we don't want to mess with for the
                // 3.2p1 patch.  Reconsider for 4.0.
                if (obj.getId() != null) {
                    Class cls = getTheClass(obj);
                    SailPointObject current = (SailPointObject)
                        session.createCriteria(cls)
                        .add(Restrictions.eq("id", obj.getId())).uniqueResult();
                    if ( (current != null ) && ( current != obj) )
                        session.evict(current);
                }

                // If this is an object that we are importing, save/replicate all
                // child objects and replicate this object.
                SailPointObject toReplicate =
                    getObjectToReplicate(obj, importing, hasIndependentLife);
                if (null != toReplicate) {
                    // Make sure all contained references are first replicated.
                    Set<SailPointObject> references = null;
                    try {
                        references = XMLReferenceChaser.getReferences(obj);
                    }
                    catch (Exception e) {
                        throw new GeneralException(e);
                    }
                    if (references != null) {
                        for (SailPointObject child : references) {
                            // kludge: some object objects may be stored as XML
                            // and should not be saved independently
                            if (child != null && !child.isXml())
                                saveInternal(child, importing, false);
                        }
                    }

                    // TOOD: Use replicate() or merge()???  Merge doesn't leave
                    // the object in the Session, so we'll use replicate for
                    // now.  Note that calling replicate will not cause the
                    // interceptor onFlushDirty to fire so we have to 
                    // manually add it to the update list
                    _interceptor.addUpdate(obj);
                    session.replicate(obj, ReplicationMode.OVERWRITE);
                    //session.merge(obj);
                }
                else {
                    // this should now be set by the interceptor
                    //if (obj.getCreated() == null)
                    //obj.setCreated(now);

                    // Formerly did this but if the id exists Hibernate
                    // expects to do an update which will fail?
                    // we've already probed for existing objects we 
                    // know this has to be a save.  But note that
                    // we have to use our own id generator to preserve
                    // the foreign id.
                    // !! ugh, can only assume save() if importing
                    // and hasIndependentLife are both false, because
                    // getObjectToReplicate will not do the check if 
                    // either are true.  This is a terrible logic dependency,
                    // need to redesign this to make it clearer what
                    // the rules are.
                    if (importing && hasIndependentLife)
                        session.save(obj);
                    else
                        session.saveOrUpdate(obj);
                }
            }
        }
        catch (Throwable e)
        {
            throw new GeneralException(e);
        }
    }

    /**
     * Promote extended attributes from an internal Map to 
     * one of the special columns.  
     *
     * Historically Identity and Link classes have managed
     * these in the Identitizer since modifications were more
     * controlled.  Once we started supporting extended attributes
     * on other classes it was better to have a more automatic way
     * to promote them.  
     *
     * Ideally this would be done as part of our interceptor, but 
     * I'm leery about making too many changes down there, especially
     * if we want to support the "external" attribute table.  Instead
     * we require that you call saveObject, you can't just fetch, mark
     * dirty and commit.   Most of the code calls saveObject so that
     * shouldn't be a difficult requirement.
     *
     * UPDATE: Identity.setAttribute is also maintaining the numbered
     * properties which is wrong and shouldn't be necessary if we
     * do it here.  If we're being called by Identitizer, then it
     * will probably have already done this to gather Statistics, 
     * but it doesn't hurt to do it again as long as it's fast.
     * 
     * !! This isn't enough.  If the attributes are chagned between
     * the time saveObject is called and the flush happens, the changes
     * will not be seen.  This needs to happen during flush, probably
     * in the interceptor onFlushDirty.
     */
    private void promoteExtendedAttributes(SailPointObject obj) 
        throws GeneralException {

        ExtendedAttributeUtil.promote(obj, null);
    }

    /**
     * If you obtain an object handle through lazy evaluation,
     * what you apparently get is a stub object
     *
     * and if you call getClass on this you get:
     *
     *   cls= Class<T> (sailpoint.object.Certification$$EnhancerByCGLIB$$2775ca6d) (id=272)
     * 
     * The delightful thing is that if you try to use this
     * class to build a Criteria using Session.createCriteria, 
     * the search won't return anything, apparently because this
     * isn't a "real" Hibernate class, and Hibernate is too dumb
     * to resolve through the "enhancer" class that it put there.
     * 
     * In those cases where you need the real class, call this.
     *
     * Using HibernateProxyHelper which the docs say is being phased
     * out and that "It is almost always better to use the entity name".
     * Not sure what that means.  This appears often in the forums
     * but didn't see a good workaround.  If you have the instance
     * you can use HibernateProxyHelper, but if you only have the enhanced
     * Class you have to look for "$$EnhancerByGCLIB$$" in the name. 
     * Unbelievable.
     *
     * TODO: Anything to do here now that we aren't using cglib? -rap
     */
    private Class getTheClass(Object obj) {

        // UPDATE: read somewhere that Hibernate.getClass is supposed
        // to do this.  Odd this isn't more prominent in the forums
        //Class cls = obj.getClass();
        //if (obj instanceof HibernateProxy)
        //cls = HibernateProxyHelper.getClassWithoutInitializingProxy(obj);
        //return cls;

        return Hibernate.getClass(obj);
    }
    
    private Object[] lookupInDb(SailPointObject src, Class<?> cls)
    	throws GeneralException
    {
        String[] keys = src.getUniqueKeyProperties();
        String name = src.getName();
        Object[] found = null;

        if (keys != null) {
        	found = lookupByKeys(src, cls, keys);
        }
        else if (name != null && src.isNameUnique()) {
            // jsl - only do this if the name is declared unique
            // I hit this trying to import files with IdentityEntitlements
            // which don't declare unique key properties, we should fix that
            // but either way don't try a name lookup if the name isn't unique
            found = lookupByName(src, cls, name);
        }

        return found;
    }
    
    private Object[] lookupById(SailPointObject src, Class<?> cls, String id)
    	throws GeneralException
    {
        try {
            ProjectionList proj = Projections.projectionList();
            proj.add(Projections.id());
            proj.add(Projections.property("created"));
            
            if (_interceptor.isImmutableObject(src)) {
                proj.add(Projections.property("immutable"));
            }
            
            return (Object[])getSession().createCriteria(cls)
                .add( Restrictions.eq("id", id) )
                .setProjection(proj)
                .uniqueResult();
        }
        catch (Throwable e) {
            throw new GeneralException("Could not find unique " + cls +
                                       " with id " + id, e);
        }
    }
    
    private Object[] lookupByKeys(SailPointObject src, Class<?> cls, String[] keys)
    	throws GeneralException 
    {
        try {
            Criteria criteria = getSession().createCriteria(cls);
            for (int i = 0 ; i < keys.length ; i++) {
                String prop = keys[i];
                Object value = Reflection.getProperty(src, prop);
                // hmm, this isn't handling Lists properly, assume
                // for now that we can only have primitive types
                // as keys
                if (value != null)
                    criteria.add(Restrictions.eq(prop, value));
                else {
                    // do we allow this?
                    criteria.add(Restrictions.isNull(prop));
                }
            }

            ProjectionList proj = Projections.projectionList();
            proj.add(Projections.id());
            proj.add(Projections.property("created"));
            
            if (_interceptor.isImmutableObject(src)) {
                proj.add(Projections.property("immutable"));
            }
            
            criteria.setProjection(proj);

            return (Object[])criteria.uniqueResult();
        }
        catch (Throwable e) {
            throw new GeneralException("Could not find unique " + cls +
                                       " with keys", e);
        }
    }
    
    private Object[] lookupByName(SailPointObject src, Class<?> cls, String name)
    	throws GeneralException
    {
        // NOTE: we originally only did this if isNameUnique
        // was true, but Kelly had to change that for importing
        // scopes.  This means that importing things that don't 
        // have unique names and don't define a UniqueKeyProperties
        // set may not behave as expected when importing id-less
        // XML objects.  Need to think more about this...
        
        try {
            ProjectionList proj = Projections.projectionList();
            proj.add(Projections.id());
            proj.add(Projections.property("created"));
            if (_interceptor.isImmutableObject(src)) {
                proj.add(Projections.property("immutable"));
            }
            return (Object[])getSession().createCriteria(cls)
                .add( Restrictions.eq("name", name) )
                .setProjection(proj)
                .uniqueResult();
        }
        catch (Throwable e) {
            //tqm: shouldn't we be using DuplicateMappingException instead of 
            // the general Throwable
            // We used to not do lookups for objects without unique names,
            // but this caused problems when importing an object that
            // already exists b/c we would create a duplicate object.  Now
            // we'll do the lookup.  The hibernate exception is thrown when
            // there was a non-unique result.  We'll fail in this case.
            throw new GeneralException("Could not find unique " + cls +
                                       " with name " + name, e);
        }
   	}
    
    // tqm: We shouldn't need this anymore now since
    // we are generating our own keys
    // Minor Kludge: some of the unit tests want to import
    // objects that aren't required to have unique names
    // (like RoleMiningResult).  Unfortunately a new object will
    // be created every time the test init file is imported.  To 
    // prevent this, support a special form of id that will be
    // converted to a name that will then be assumed to be unique.
    private void fixTestId(SailPointObject src)
    	throws GeneralException
    {
    	String id = src.getId();
    	if (id != null && id.startsWith(TEST_ID_PREFIX)) {
    		String name = id.substring(TEST_ID_PREFIX.length());
    		id = null;
    		src.setId(null);
    		src.setName(name);
            }
    }
    
    private boolean isImmutableObject(SailPointObject o) {
        return (o instanceof WorkItemArchive) || (o instanceof WorkItem) || (o instanceof Certification) || (o instanceof CertificationArchive);
    }
    
    /**
     * Given an object we're importing, check to see
     * if  an object with this id or name already exists.  
     *
     * On import we assume we will be replicating the object so we transfer the
     * previous id and creation date to the source object.
     * 
     * @param  src                The SailPointObject to modify if it is to be
     *                            replicated.
     * @param  importing          Whether we are importing the object or just
     *                            saving it.
     * @param  hasIndependentLife  True if this object has an independent life
     *                            span.  This is false if the SailPointObject
     *                            is contained (ie - is created and deleted
     *                            with) with another object.
     * 
     * @return The existing SailPointObject with the ID and creation date set
     *         to the corresponding persisted object's ID and creation date if
     *         importing and the object is found in the repo, otherwise null.
     */
    private SailPointObject getObjectToReplicate(SailPointObject src,
                                                 boolean importing,
                                                 boolean hasIndependentLife)
        throws GeneralException 
    {
        SailPointObject obj = null;

        // Only return an object to replicate if we're importing.  Saving
        // objects should always go through the normal saveOrUpdate() method
        // and not require the uniqueness checking here.
        if (!importing) {
            return null;
        }
        
        if (!hasIndependentLife) {
        	return null;
        }

        fixTestId(src);
        
        Object [] result = null;
        
        String id = src.getId();
        Class<?> cls = getTheClass(src);

        if (id != null) {
        	result = lookupById(src, cls, id);
        	
        	if (result == null) {
        		result = lookupInDb(src, cls);
        	}
        } else {
        	result = lookupInDb(src, cls);
        }
        
        if (result != null) {
            if (_interceptor.isImmutableObject(src)) {
                boolean readOnly = (Boolean)result[2] ;
                if (readOnly && !_interceptor.isReadOnlyOverride()) {
                   throw new ImmutableModificationException();
                }
            }
        	src.setId((String)result[0]);
        	src.setCreated((Date)result[1]);
        	obj = src;
        }

        return obj;
    }

    public void removeObject(SailPointObject obj)
        throws GeneralException 
    {
        try
        {
            startTransaction();
            // jsl - not sure if this is necessary, would we even try
            // dirty checking on deleted objects?
            obj.setDirty(true);
            Session session = getSession();
            session.delete(obj);
        }
        catch (Throwable e)
        {
            throw new GeneralException(e);
        } 
    }

    /**
     * Bulk delete objects.
     * This is only supported for certain classes, currently:
     *   
     *   LinkExternalAttribute
     *   IdentityExternalAttribute
     *   JasperPageBucket
     *   TargetAssociation
     *   IdentityEntitlement
     *   FileBucket
     *
     * Formerly we had a subclass, BulkDeletePersistenceManager that enforced this
     * but that created duplicate database connections so the method was moved here.
     * If you try this on a class with complex integrity constraints it will fail with
     * obscure errors.  If this becomes a problem we can hard code and/or Spring configure
     * the list of allowed classes and check them here. -jsl
     *
     */
    public <T extends SailPointObject> void removeObjects(Class<T> cls, QueryOptions options) 
        throws GeneralException {
        try {
            startTransaction();
            Session session = getSession();

            optimize(options);

            HQLFilterVisitor v = visitHQLFilter(cls, options, null);
            String hql = v.getDeleteString();
            if (log.isDebugEnabled()) {
                log.debug("HQL: " + hql);
                log.debug("Params: " + v.getParameterMap());
            }
            Query q = getQuery(session, hql, v.getParameterMap(), 
                               false, null, null);
            q.executeUpdate();
        }
        catch (Throwable e)
        {
            throw new GeneralException(e);
        }
    }

    /**
     * Return all objects of a given class.
     */
    @SuppressWarnings("unchecked")
    public <T extends SailPointObject> List<T> getObjects(Class<T> cls) 
        throws GeneralException 
    {
        try
        {
            startTransaction();
            Session session = getSession();
            if ( cls.equals(Identity.class) ) {
                QueryOptions ops = new QueryOptions();
                ops.add(getIdentityFilter());
                return getObjects(cls, ops);
            }
            return session.createCriteria(cls).list();
        }
        catch (Throwable e)
        {
            throw new GeneralException(e);
        }
    }

    @SuppressWarnings("unchecked")
    public <T extends SailPointObject> List<T> getObjects(Class<T> cls, QueryOptions options)
        throws GeneralException
    {
        List<T> objects = null;
        try
        {
            startTransaction();
            Session session = getSession();
            orderAndOptimize(options, cls, null);

            HQLFilterVisitor v = visitHQLFilter(cls, options, null);
            String hql = v.getQueryString();
            if (log.isDebugEnabled()) {
                log.debug("HQL: " + hql);
                log.debug("Params: " + v.getParameterMap());
            }

            Query q = getQuery(session, hql, v.getParameterMap(),
                               v.spansMultipleTables(), options, null);
            objects = (List<T>) q.list();
        }
        catch (Throwable e)
        {
            throw new GeneralException(e);
        }

        return objects;
    }

    /**
     * Return an iterator over the given object query.  Note that this may
     * contain duplicates even if QueryOptions.setDistinct(true) was called.  If
     * you require distinct results, consider getObjects(Class, QueryOptions).
     */
    @SuppressWarnings("unchecked")
    public <T extends SailPointObject> Iterator<T> search(Class<T> cls, QueryOptions options)
        throws GeneralException
    {
        Iterator<T> iterator = null;

        try
        {
            startTransaction();
            boolean dirtyRead = options != null && options.isDirtyRead();
            Session session = getSession(dirtyRead);
            
            orderAndOptimize(options, cls, null);

            HQLFilterVisitor v = visitHQLFilter(cls, options, null);
            String hql = v.getQueryString();
            if (log.isDebugEnabled()) {
                log.debug("HQL: " + hql);
                log.debug("Params: " + v.getParameterMap());
            }

            Query q = getQuery(session, hql, v.getParameterMap(),
                               v.spansMultipleTables(), options, null);
            iterator = q.iterate();
        }
        catch (Throwable e)
        {
            throw new GeneralException(e);
        }

        if (options != null && options.isCloneResults()) {
            List inMem = new ArrayList<T>();
            while (iterator.hasNext()) {
                inMem.add(iterator.next());
            }
            return inMem.iterator();
        }

        return iterator;
    }

    public <T extends SailPointObject> Iterator<Object[]> search(Class<T> cls, QueryOptions options, String properties)
        throws GeneralException {
        return this.search(cls, options, Util.csvToList(properties));
    }

    public <T extends SailPointObject> Iterator<Object[]> search(Class<T> cls, QueryOptions options, List<String> properties) throws GeneralException {

        Iterator iterator = null;

        try
        {
            startTransaction();
            boolean dirtyRead = options != null && options.isDirtyRead();
            Session session = getSession(dirtyRead);
            
            properties = orderAndOptimize(options, cls, properties);

            HQLFilterVisitor v = visitHQLFilter(cls, options, properties);
            String hql = v.getQueryString();
            if (log.isDebugEnabled()) {
                log.debug("HQL: " + hql);
                log.debug("Params: " + v.getParameterMap());
            }

            Query q = getQuery(session, hql, v.getParameterMap(),
                               (null != options) ? options.isDistinct() : false,
                               options, properties);
            //TODO: Ensure Oracle will return more than 10 rows @see Bug#2327 -rap
            if (iterator == null) {
                // a normal iterator
                iterator = q.iterate();
                iterator = new ProjectionResultIterator(iterator);
            }
        }
        catch (Throwable e)
        {
            throw new GeneralException(e);
        }

        if (options != null && options.isCloneResults()) {
            List inMem = new ArrayList<Object[]>();
            while (iterator.hasNext()) {
                inMem.add(iterator.next());
            }
            return inMem.iterator();
        }

        return iterator;
    }

    public Iterator search(String query, Map<String,Object> args, QueryOptions options) 
        throws GeneralException {

        Iterator result = null;

        if (query == null)
            throw new GeneralException("Unspecified query");

        try {

            startTransaction();
            boolean dirtyRead = options != null && options.isDirtyRead();
            Session session = getSession(dirtyRead);

            if (!query.startsWith("sql:")) {

                Query q = this.getQuery(session, query, args, false, options, null);
                result = q.iterate();
            }
            else {
                // ugh, Hibernate doesn't support iteration over SQL results
                // have to use list() which will bring the whole thing
                // into memory
                Query q = this.getQuery(session, query, args, false, options, null);
                List l = q.list();
                result = l.iterator();
            }
        }
        catch (Throwable e)
        {
            throw new GeneralException(e);
        }

        // search interfaces commonly return an empty iterator rather
        // than a null iterator, be consistent with the other implementations
        if (result == null)
            result = new ArrayList().iterator();


        if (options != null && options.isCloneResults()) {
            List inMem = new ArrayList<>();
            while (result.hasNext()) {
                inMem.add(result.next());
            }
            return inMem.iterator();
        }

        return result;
    }

    public int update(String query, Map<String,Object> args) 
        throws GeneralException {
        
        Session session = getSession();
        Query hibernateQuery = getQuery(session, query, args, false, null, null);

        return hibernateQuery.executeUpdate();
    }

    /**
     * Create an HQLFilterVisitor rooted at the given class using the filters
     * and ordering in the given QueryOptions.
     * 
     * @param  cls         The class that we're searching on.
     * @param  options     The QueryOptions with the filters and ordering.
     * @param  properties  The List of properties to return.
     * 
     * @return An HSQLFilterVisitor that has processed the filters and ordering
     *         in the given options.
     */
    protected HQLFilterVisitor visitHQLFilter(Class cls, QueryOptions options,
                                            List<String> properties)
        throws GeneralException {

        // QueryOptions is expected to be optimized by now
        HQLFilterVisitor v = new HQLFilterVisitor(cls, new HibernateDatabaseCapabilities(_session), _sessionFactory);
        v.setSelectColumns(properties);

        Filter combinedFilter = null;
        List<Filter> filters = null;
        List<Ordering> orderings = null;
        List<String> groupBys = null;

        if ( options != null ) {
            // Make a copy and combine the filters
            List<Filter> restrictions = options.getRestrictions();
            if ( Util.size(restrictions) > 0 ) {
                filters = new ArrayList<Filter>(options.getRestrictions());
            }
            if ((null != filters) && !filters.isEmpty()) {

                if (1 == filters.size()) {
                    combinedFilter  = filters.get(0);
                }
                else {
                    combinedFilter = Filter.and(filters);
                }
            }
            // Make the query distinct if requested.
            v.setDistinct(options.isDistinct());
            v.setIgnoreCase(options.isIgnoreCase());
            orderings = options.getOrderings();
            groupBys = options.getGroupBys();
        }
 
        if ( cls.equals(Identity.class) ) {
            // No filter indicates that we ONLY want User Identities and NOT
            // Workgroups
            if ( combinedFilter == null ) {
                combinedFilter = getIdentityFilter();
            } else {
                combinedFilter = optimizeWorkgroupFilter(combinedFilter);
            }
        }

        if ( combinedFilter != null ) {
            combinedFilter.accept(v);
        }

        if ( Util.size(orderings) > 0 ) {
            for (QueryOptions.Ordering ordering : orderings ) {
                v.addOrderBy(ordering.getColumn(), ordering.isAscending(), ordering.isIgnoreCase());
            }
        }

        v.setGroupBys(groupBys);
        
        return v;
    }

    /*
     * Returns a 'workgroup != true' or alternatively 'workgroup == false'
     * filter to be used for Identity-only filters. Both filters are logically
     * identical. However, the particular flavor may avoid prioritizing
     * the low-cardinality index based on the != operator over the == operator.
     * This, in turn, may avoid long-running Identity queries as other 
     * more relevant indexes are used. This behavior is driven by the 
     * enableWorkgroupEqFalse flag.
     */
    private Filter getIdentityFilter() {
        if (this._enableWorkgroupEqFalse) {
            return Filter.eq(Identity.ATT_WORKGROUP_FLAG, false);
        } else {
            return Filter.ne(Identity.ATT_WORKGROUP_FLAG, true);
        }
    }

    /**
     * Workgroups are special types of Identities. Identities that have a 
     * "workgroup" property equal to true, are workgroups others are users. 
     *
     * To handle backward compatibility assure that queries using the 
     * Identity.class that don't include the workgroup flag only include Identitiy
     * object's that are actually users.
     *
     * Go through the defined filters and look to see if there is a filter
     * that includes the ATT_WORKGROUP_FLAG if not add it explicitly.
     *
     * Otherwise, dig through the filters and see we are filtering on 
     * in[true,false] ( which is how getObject builds its query), OR
     * see if we have workgroup eq=true OR eq=false in the same 
     * query.  In both cases the query can be optimized and the workgroup=
     * parts of the filter can be removed.
     */
    private Filter optimizeWorkgroupFilter(Filter filter) 
        throws GeneralException {

        if ( filter == null ) return filter;

        Filter copy = copy(filter);
        // this will go through and store off the attributes
        // being queried so we can see whats been queried
        // to see if the WORKGROUP flag has already been added
        WorkgroupFilterVisitor wgfv = new WorkgroupFilterVisitor();
        copy.accept(wgfv);

        if ( !wgfv.inPropertyList(Identity.ATT_WORKGROUP_FLAG) ) {
            // default to ONLY User Identity objects
            copy = Filter.and(copy, getIdentityFilter());
        } else {
            // The WORKGROUP flag has been specified in the restrictions. If 
            // there is an OR or an IN property used in the field search 
            // filter out the workgroups query all together since its a bit
            // field and silly to include both in the query 
            // BUG#7020 for more info
            if ( wgfv.canBeOptimized() ) {
                copy = scrubWorkgroupFilters(copy);
            }
        }
        return copy;
    }

    /**
     * Remove any of the LeafFilters that are referencing a workgroup
     * to avoid queries where both bit values are specified in the query.
     *   i.e. workgroup=0 OR workgroup=1, workgroup in (0,1) 
     *
     * The WorkgroupFilterVisitor detects these situations by
     * looking for specific queries.
     */
    private Filter scrubWorkgroupFilters(Filter copy) {
        if ( copy != null ) {
            if ( copy instanceof LeafFilter ) {
                LeafFilter lf = (LeafFilter)copy;
                if ( lf != null ) {
                    if ( isWorkgroupYesNoFilter(lf) ) {
                        return null;
                    }
                }
            }
            else if (copy instanceof CompositeFilter) {
                CompositeFilter cf = (CompositeFilter)copy;
                List<Filter> children = cf.getChildren();
                if (Util.size(children) > 0) {
                    List<Filter> neuChildren = new ArrayList<Filter>();
                    for (Filter child : children) {
                       Filter updated = scrubWorkgroupFilters(child);
                       if ( updated != null ) 
                           neuChildren.add(updated);
                    }
                    cf.setChildren(neuChildren);
                }
            }
        }
        return copy;
    }

    /**
     * 
     * Return true only if workgroup IN (Boolean(true), Boolean(false)); 
     * 
     */
    private boolean isWorkgroupYesNoFilter(LeafFilter lf) {

        if (!lf.getProperty().equals(Identity.ATT_WORKGROUP_FLAG)) {
            return false;
        }
        if (!lf.getOperation().equals(LogicalOperation.IN)) {
            return false;
        }
        
        Object value = lf.getValue();
        if (!(value instanceof List)) {
            return false;
        }
        
        @SuppressWarnings({ "rawtypes" })
        List values = (List) value;
        if (values.size() != 2) {
            return false;
        }
        
        boolean firstValue = getBooleanFromStringOrBoolean(values.get(0));
        boolean secondValue = getBooleanFromStringOrBoolean(values.get(1));
        
        return 
            (firstValue == true && secondValue == false)
                ||
            (firstValue == false && secondValue == true);
        
    }
    
    private static boolean getBooleanFromStringOrBoolean(Object val) {
        
        if (val instanceof Boolean) {
            return ((Boolean) val).booleanValue();
        } else {
            return Boolean.parseBoolean((String)val);
        }
    }

    /**
     * Get a Query using with the given HQL and parameter map.  Apply a distinct
     * result transformer to the query if distinctResults is true.  This also
     * sets properties from the QueryOptions on the Query - such as firstRow,
     * resultLimit, etc...
     * 
     * @return A Query created with the given information.
     */
    protected Query getQuery(Session session, String hql, Map<String,Object> params,
                           boolean distinctResults, QueryOptions options,
                           List<String> properties) {

        if (HQL_LOG.isDebugEnabled()) {
            HQL_LOG.debug(hql);
            HQL_LOG.debug("Query parameters: " + params);
        }
        
        Query q;
        if (!hql.startsWith("sql:"))
            q = session.createQuery(hql);
        else {
            String sql = hql.substring(4);
            q = session.createSQLQuery(sql);
        }

        if (params != null) {
            Iterator<Map.Entry<String,Object>> it = params.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry entry = it.next();
                String name = (String)entry.getKey();
                Object o = entry.getValue();

                // Hibernate will attempt to guess the type based on the
                // context of the parameter or the Java type of the value.
                // This should be ok for most things except perhaps Date
                // which uses our own user type.  We do have to 
                // check for collection parameters though and use
                // a different method.
                if (o instanceof List)
                    q.setParameterList(name, (List)o);
                else
                    q.setParameter(name, o);
            }
        }

        if (options != null) {

            if (options.isFlushBeforeQuery())
                q.setHibernateFlushMode(FlushMode.AUTO);

            if (options.getFirstRow() > 0)
                q.setFirstResult(options.getFirstRow());

            if (options.getResultLimit() > 0)
                q.setMaxResults(options.getResultLimit());

            if (options.isCacheResults()) {
                q.setCacheable(true);
            }

            // orderBy has already been sucked into the HQL by the visitor.

            // The HQL filter visitor will try to execute a distinct query.
            // Only apply the DistinctRootEntityResultTransformer if the query
            // could not use DISTINCT.  Note that this may return duplicates
            // when called from the "iterate" methods - we may need to fix this
            // later.  For now, it will be a limitation that the callers will
            // have to deal with.
            DatabaseCapabilities cap = new HibernateDatabaseCapabilities(_session);
            if (distinctResults && !cap.canUseDistinct(properties)) {
                q.setResultTransformer(DistinctRootEntityResultTransformer.INSTANCE);
            }
            
        }

        return q;
    }
    
    /** 
     * Performs the holy trinity of optimizing, mapping, and tweaking the filters 
     * going into the queryoptions.  Introduced in bug 7539 - PH
     */
    private List<String> orderAndOptimize(QueryOptions ops, Class cls, List<String> properties) 
        throws GeneralException{

        // Before we do anything, copy the filters so that we don't corrupt them.
        // These may have come from an iPop or something that we don't want to
        // change.
        copyFilters(ops);
        
        // Optimize.
        optimize(ops);
        
        // Translate searchable attributes into their real names.
        List<String> props = mapSearchAttributes(cls, ops, properties);
        
        // Need to reorder the query if any CompositeFilters exist with collection
        // conditions in them -- put the collection conditions at the back of the bus
        if (null != ops) {
            ops.setRestrictions(reorderCollectionConditions(ops.getRestrictions()));
        }
        
        return props;
    }

    /**
     * Create copies of all of the filters in the restrictions list and reset
     * the list.  This will prevent optimization, etc... from corrupting any
     * filters that the caller expects won't change.
     */
    private void copyFilters(QueryOptions qo) {
        if ((null != qo) && (null != qo.getRestrictions())) {
            List<Filter> copied = new ArrayList<Filter>();
            for (Filter f : qo.getRestrictions()) {
                copied.add(copy(f));
            }
            qo.setRestrictions(copied);
        }
    }
    
    /**
     * Adjust the names of attributes provided in a Filter or in the
     * projection list.  This is used only for classes that support
     * customer extensible attributes, which at the moment includes 
     * Identity, Link, and CertificationItem.
     *
     * This could be more integrated with translateToCriteria, but Kelly's
     * working on that now so let's try to stay out of the way.
     *
     * Sadly we need to make a copy of the Filter if we have to make any 
     * changes since the given filter could have come out of an iPOP
     * and we don't want to trash it.  The contents of the QueryOptions
     * will change however which is less dangerous but still wrong.  
     * Sure would be nice if the projection list was inside the QueryOptions.
     *
     * I'm going to punt on this for now since we are likely to merge
     * this with the Criteria translator and won't need to trash the
     * source Filter anyway.
     */
    private List<String> mapSearchAttributes(Class cls, QueryOptions ops,
                                             List<String> properties)
        throws GeneralException {

        List<Filter> filters = null;
        List<QueryOptions.Ordering> orderings = null;
        List<String> groupings = null;

        if (ops != null) {
            filters = ops.getRestrictions();
            orderings = ops.getOrderings();
            groupings = ops.getGroupBys();
        }

        if (filters != null || properties != null ||
            (Util.size(orderings) > 0) || (Util.size(groupings) > 0)) {
            try {
                ExtendedAttributeVisitor visitor = new ExtendedAttributeVisitor(cls);

                if (filters != null) {
                    for (Filter f : filters)
                        f.accept(visitor);
                }

                if ((null != orderings) && !orderings.isEmpty()) {
                    List<QueryOptions.Ordering> newOrderings =
                        new ArrayList<QueryOptions.Ordering>();

                    for (QueryOptions.Ordering ordering : orderings) {
                        QueryOptions.Ordering newOrdering = ordering;

                        String mapName = visitor.mapProperty(ordering.getColumn());
                        if (!mapName.equals(ordering.getColumn())) {
                            newOrdering = new QueryOptions.Ordering(mapName, ordering.isAscending());
                        }

                        newOrderings.add(newOrdering);
                    }

                    ops.setOrderings(newOrderings);
                }

                if (Util.size(groupings) > 0) {
                    List<String> newGroupings = new ArrayList<String>();
                    for (String groupBy : groupings) {
                        newGroupings.add(visitor.mapProperty(groupBy));
                    }
                    ops.setGroupBys(newGroupings);
                }
                
                if (properties != null) {
                    // sigh, more garbage
                    // since we punted on copying the Filter we don't really
                    // need to do this either but go through the motions
                    List<String> mappedProperties = new ArrayList<String>();
                    for (String s : properties) {
                        mappedProperties.add(visitor.mapProperty(s));
                    }
                    properties = mappedProperties;
                }
            }
            catch (Throwable t) {
                throw new GeneralException(t);
            }
        }

        return properties;
    }

    /**
     * Move all collection conditions in the given list of filters to the end of
     * the list.  Needed for hibernate to process the joins correctly.  See bug
     * 7539 - PH
     * 
     * @param  filters  The filters to reorder.
     * 
     * @return The reordered list of filters.
     */
    private List<Filter> reorderCollectionConditions(List<Filter> filters) {
        List<Filter> reordered = null;

        if (null != filters) {
            reordered = new ArrayList<Filter>();
            List<Filter> collections = new ArrayList<Filter>();
    
            for(Filter child : filters) {
                if(child instanceof CompositeFilter) {
                    reorderCollectionConditions((CompositeFilter)child);
                    reordered.add(child);
                } else {
                    LeafFilter leaf = (LeafFilter) child;
                    if(leaf.getCollectionCondition()!=null) {
                        collections.add(leaf);
                    } else {
                        reordered.add(leaf);
                    }
                }
            }
            reordered.addAll(collections);
        }

        return reordered;
    }
    
    /**
     * Move the collection conditions (if any) to the end of the child list of
     * the given composite.  See bug 7539.
     */
    private void reorderCollectionConditions(CompositeFilter f) {
        trace("Composite before reordering ",f);
        List<Filter> reordered = reorderCollectionConditions(f.getChildren());
        f.setChildren(reordered);
        trace("Composite after reordering",f);
    }

    
    private static class ProjectionResultIterator implements Iterator<Object []> {
        private Iterator _results;
        
        public ProjectionResultIterator(Iterator results) {
            _results = results;
        }
        
        public boolean hasNext() {
            return _results.hasNext();
        }
        
        public Object [] next() {
            final Object [] retval;
            
            Object next = _results.next();
            
            //hibernate returns single-valued projections as
            //an Object rather than as an Object[] of length 1
            if (next instanceof Object[]) {
                retval = (Object []) next;
            } else {
                retval = new Object [] { next };
            }
            
            return retval;
        }
        
        public void remove() {
            _results.remove();
        }
    }

    /**
     * Return the number of objects that match the criteria.
     *
     * After writing this, I see now that we could have accomplished
     * something similar with the Projection metaphor, but I kind of
     * like the clarity of 
     */
    public int countObjects(Class cls, QueryOptions options)
        throws GeneralException
    {
        int count = 0;

        try
        {
            startTransaction();
            Session session = getSession();

            // kludge: SQL Server doesn't like it if you have ORDER BY
            // in a count(*) query.  Unfortunately, a lot of the UI beans
            // build a QueryOptions object that is used both for counting
            // and for fetching and may contain fetch options like distinct
            // and orderBy that don't make sense for counting.  We have
            // to be careful to only pay attention to the Filter here.
            // Don't trash the original QueryOptions though.
            QueryOptions countOptions = null;
            if (options != null) {
                countOptions = new QueryOptions();
                countOptions.setRestrictions(options.getRestrictions());
                // these have to convey too!
                countOptions.setIgnoreCase(options.isIgnoreCase());
                countOptions.setDistinct(options.isDistinct());

                // These are probably not necessary in normal use
                // with LiveGrid tables since LiveGrid wants to count
                // all the rows before displaying the subset.
                //countOptions.setFirstRow(options.getFirstRow());
                //countOptions.setResultLimit(options.getResultLimit());
            }

            orderAndOptimize(countOptions, cls, null);

            List<String> properties = new ArrayList<String>();
            properties.add("count(*)");
            HQLFilterVisitor v = visitHQLFilter(cls, countOptions, properties);
            String hql = v.getQueryString();
            if (log.isDebugEnabled()) {
                log.debug("HQL: " + hql);
                log.debug("Params: " + v.getParameterMap());
            }

            Query q = getQuery(session, hql, v.getParameterMap(),
                               v.spansMultipleTables(), countOptions, null);
            List result = q.list();

            // I think this has to be set, but be safe
            if (result != null) {
                Object o = result.get(0);
                if (o instanceof Integer)
                    count = ((Integer)o).intValue();
                else if (o instanceof Long)
                    count = (int) ((Long) o).longValue();
            }
        }
        catch (Throwable e)
        {
            throw new GeneralException(e);
        }

        return count;
    }

    /**
     * Attach the given SailPointObject to this context's hibernate session.
     */
    public void attach(SailPointObject object) throws GeneralException
    {
        try
        {
            startTransaction();
            Session session = getSession();
            if (!session.contains(object)) {
                // jsl - originally used lock here, but that apparently only
                // works if the object we're attaching hasn't been modified.
                // Use update instead, which I guess is ok for UI objects
                // hanging off the HttpSession as long as you don't commit.
                // Well, not really, update will generate  ids for new
                // objects with cascade="all" if you do not commit this
                // transaction, these ids confuse the next one when
                // you try to attach the object again!!
                // merge() might be better here, but then we need to return
                // the merged object
            
                //session.lock(object, LockMode.NONE);
                session.saveOrUpdate(object);

                // If the object contains XML blobs, have to attach
                // anything in them since cascade doesn't work for these
                // jsl - this wasn an experiment that I abandoned in favor
                // of using NewXmlType.  It didn't solve the problem anyway
                // but we should revisit this since you may want the referenced
                // objects attached
                /*
                try {
                    Set<SailPointObject> references = XMLReferenceChaser.getHiddenReferences(_sessionFactory, object);
                    if (references != null)  {
                        for (SailPointObject ref : references)
                            attach(ref);
                    }
                }
                catch (Exception e) {
                    throw new GeneralException(e);
                }
                */
            }
        }
        catch (Throwable e)
        {
            throw new GeneralException(e);
        }
    }


    public void decache(SailPointObject object) throws GeneralException
    {
        try
        {
            // avoid creating a session if we don't have one
            if (_session != null)
                getSession().evict(object);
        }
        catch (Throwable e)
        {
            throw new GeneralException(e);
        }
    }

    // hack
    static public ThreadLocal<Boolean> TraceDecache = new ThreadLocal<Boolean>();


    public void decache() throws GeneralException
    {
        if (Util.otob(TraceDecache.get()))
            System.out.println("*** DECACHE ***");

        try
        {
            // NOTE: Originally we closed the session here just to make
            // sure everything was gone.  Unfortunately this will also
            // close any open cursors which means you can't be iterating
            // over a search result and periodically clearing the cache.
            // It is vital for things like the identity refresh task to
            // clear the session cache as it iterates, if not things
            // can "leak" causing the cache to bloat and severly 
            // degrading transaction commit time.  If you think you
            // need to close the session, call close() instead.
            if (_session != null)
                getSession().clear();
        }
        catch (Throwable e)
        {
            throw new GeneralException(e);
        }
    }
    
    /**
     * Clears the second level cache.
     */
    public void clearHighLevelCache()
            throws GeneralException
    {
        try {
            Cache cache = getSession().getSessionFactory().getCache();
            
            cache.evictEntityRegions();
            cache.evictCollectionRegions();
            cache.evictQueryRegions();            
        } catch (Throwable ex) {
            throw new GeneralException(ex);
        }
    }

    /**
     * A method to check that we haven't already closed the session.
     * Otherwise, we always get a new session and there is a good chance
     * that existing connections could be left open and cause us
     * to leak db connections.
     */
    private void checkForPreviouslyClosedSession()  {
        if ( _isClosed ) {
            throw new RuntimeException("Attempting to re-use a previously" 
                + " closed hibernate session.");
        } 
    }

    /**
     * Dump the hibernate session factory statistics at DEBUG level to the
     * logger for this class.
     * 
     * @param  clear  Whether to clear the statistics after printing them.
     */
    public void dumpStatistics(boolean clear) {
        if (log.isDebugEnabled())
            log.debug(_sessionFactory.getStatistics().toString());
        
        if (clear) 
            _sessionFactory.getStatistics().clear();
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Query Optimizer
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Ultra sophisticated query optimizer.
     *
     * This was added after we noticed that the slicer/dicer
     * was searching for identities with accounts more more than
     * one application with a filter like this:
     *
     *   <Filter operation="COLLECTION_CONDITION" property="links">
     *     <CollectionCondition>
     *       <CompositeFilter operation="OR">
     *         <Filter operation="EQ" property="application.id" value="..."/>
     *         <Filter operation="EQ" property="application.id" value="..."/>
     *         <Filter operation="EQ" property="application.id" value="..."/>
     *       </CompositeFilter>
     *     </CollectionCondition>
     *  </Filter>
     *
     * The HQL/SQL generated from this has a lot of outer/inner joins
     * and is very slow.  Here what we really want is this which
     * is much faster:
     *
     * <Filter operation="COLLECTION_CONDITION" property="links">
     *   <CollectionCondition>
     *     <CompositeFilter operation="OR">
     *       <Filter operation="IN" property="application.id">
     *         <Value>
     *           <List>
     *             <String>...</String>
     *             <String>...</String>
     *             <String>...</String>
     *           </List>
     *         </Value>
     *       </Filter>
     *     </CompositeFilter>
     *   </CollectionCondition>
     * </Filter>
     *
     * Rather than changing the slicer/dicer to generate the "right" query
     * we'll try to find the pattern and convert it automatically.  
     *
     * This can be extended to do other optimizations but if it gets
     * too complex factor it out into one or more Optimizer classes.
     */
    protected void optimize(QueryOptions ops) {

        if (!_disableOptimizer && ops != null) {
            List<Filter> filters = ops.getRestrictions();
            if (filters != null) {
                List<Filter> optimized = new ArrayList<Filter>();
                for (Filter f : filters) {
                    if (looksSlow(f)) {
                        trace("Filter before optimization:", f);
                        optimize(f);
                        trace("Filter after optimization:", f);
                    }
                    
                    optimized.add(f);
                }
                
                // !! ugh, are we allowed to modify the options?
                // Need to be careful with Filter since it can
                // come from a GroupDefinition, but require that
                // options be more volatile
                ops.setRestrictions(optimized);
            }
        }
    }

    private void trace(String msg, Filter f) {
        // in theory toXml can throw
        try {
            if (log.isDebugEnabled()) {
                log.debug(msg);
                log.debug(f.toXml());
            }
        }
        catch (Throwable t) {
            log.error(t.getMessage(), t);
        }
    }

    /**
     * Just for my own peace of mind, make a quick sniff test of the
     * filter and return false if it doesn't look like a candidate
     * for optimization.  
     */
    private boolean looksSlow(Filter f) {

        boolean slow = false;

        if (f instanceof LeafFilter) {
            LeafFilter lf = (LeafFilter)f;
            slow = lf.getCollectionCondition() != null;
        }
        else if (f instanceof CompositeFilter) {
            CompositeFilter cf = (CompositeFilter)f;
            List<Filter> children = cf.getChildren();
            if (children != null) {
                for (Filter child : children) {
                    slow = looksSlow(child);
                    if (slow)
                        break;
                }
            }
        }
        return slow;
    }
    
    /**
     * Copy a filter prior to optimization.
     */
    private Filter copy(Filter src) {
        return Filter.clone(src);
    }

    /**
     * Optimize a filter, we can assume the filter is entirely mutable.
     */
    private void optimize(Filter f) {

        if (f instanceof CompositeFilter) {
            CompositeFilter cf = (CompositeFilter)f;
            List<Filter> children = cf.getChildren();
            if (children != null) {
                for (Filter child : children)
                    optimize(child);
            }
        }
        else if (f instanceof LeafFilter) {
            LeafFilter lf = (LeafFilter)f;
            optimizeCollectionCondition(lf);
        }
    }

    private void optimizeCollectionCondition(LeafFilter lf) {
        
        CompositeFilter cc = lf.getCollectionCondition();
        if (cc != null) {
            // usually we'll see an OR here, if not give up
            if (cc.getOperation() == BooleanOperation.OR) {
                // see if the children can collapse
                // This should actually be a safe optimization 
                // for any matching filter, but be conservative 
                // for now and only do this within a 
                // collectionCondition
                lf.setCollectionCondition(checkCollapsableEq(cc));
            }
        }
    }
    
    /**
     * Check to see if a list of filters within an OR 
     * are all EQ for the same property.  If true restructure
     * the query to use an IN with a list of values.
     */
    private CompositeFilter checkCollapsableEq(CompositeFilter cf) {

        boolean collapsable = false;
        boolean ignoreCase = false;
        String property = null;
        List values = new ArrayList();

        List<Filter> children = cf.getChildren();
        if (children != null) {
            collapsable = true;
            Iterator<Filter> it = children.iterator();
            while (collapsable && it.hasNext()) {
                Filter f = it.next();
                if (!(f instanceof LeafFilter))
                    collapsable = false;
                else {
                    LeafFilter lf = (LeafFilter)f;
                    if(lf.isIgnoreCase())
                    	ignoreCase = true;
                    if (lf.getOperation() != LogicalOperation.EQ)
                        collapsable = false;
                    else {
                        String p = lf.getProperty();
                        if (property != null && !property.equals(p))
                            collapsable = false;
                        else {
                            property = p;
                            values.add(lf.getValue());
                        }
                    }
                }
            }
        }

        if (collapsable) {
            Filter in = Filter.in(property, values);
            if (in instanceof LeafFilter) {
                ((LeafFilter)in).setIgnoreCase(ignoreCase);
            } else if (in instanceof CompositeFilter) {
                // apply ignorecase to children.
                // This should only happen when the in filter is so large
                // that it has to be split into multiple ORed INs.  See Filter.in
                applyIgnoreCaseToComposite((CompositeFilter) in, ignoreCase);
            }
            // sigh, COLLECITON_CONDITION requires a CompositeFilter
            // wrapper so make an OR with one term
            cf = (CompositeFilter)Filter.or(in);
        }

        return cf;
    }

    /**
     * if a filter has been split up, then ignore case should be applied to all
     * of its pieces.  recursive function.
     * @param cf The composite filter to process
     * @param ignoreCase boolean value to set ignore case to.
     */
    private void applyIgnoreCaseToComposite(CompositeFilter cf, boolean ignoreCase) {
        List<Filter> children = cf.getChildren();
        for (Filter child : children) {
            if (child instanceof LeafFilter) {
                ((LeafFilter) child).setIgnoreCase(ignoreCase);
            } else if (child instanceof CompositeFilter) {
                applyIgnoreCaseToComposite((CompositeFilter) child, ignoreCase);
            }
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Diagnostics
    //
    //////////////////////////////////////////////////////////////////////

    public void enableStatistics(boolean b) {
        _statistics = b;
    }
    
    public static void println(Object o) {
        System.out.println(o);
    }

    /**
     * Sigh, changed from println to log.info since once we started using 
     * multiple  refresh threads the interleaved stats were hard to tell apart.
     * To see statistics you have to both call this AND enable info trace.
     *
     * Level debug is too granular, don't want all the entry/exit trace.
     * 
     * Changed to warn level.  When you bother to call this, you want to 
     * see it and info level in this class brings in a lot of junk you 
     * don't necessarily need.
     */
    public void printStatistics() {

        try {
            // not using _statistics for anything right now, but use
            // the opportunity to dump session cache stats
            if (_session != null) {
                Thread t = Thread.currentThread();
                
                log.warn("*** Hibernate Session for thread " + t.getName() + " ***");
                
                SessionStatistics stats = _session.getStatistics();
                
                log.warn(Util.itoa(stats.getEntityCount()) + " entities and " + 
                         Util.itoa(stats.getCollectionCount()) + " collections");

                Set entities = stats.getEntityKeys();
                if (entities != null) {
                    log.warn("Entities:");
                    
                    Iterator it = entities.iterator();
                    while (it.hasNext()) {
                        EntityKey key = (EntityKey)it.next();
                        log.warn("  " + key.toString());
                    }
                }

                Set collections = stats.getCollectionKeys();
                if (collections != null) {
                    log.warn("Collections:");
                    
                    Iterator it = collections.iterator();
                    while (it.hasNext()) {
                        CollectionKey key = (CollectionKey)it.next();
                        log.warn("  " + key.toString());
                    }
                }

                Statistics sessionStats = _sessionFactory.getStatistics();
                log.warn(sessionStats.toString());
                log.warn("L2 Hits:   " + sessionStats.getSecondLevelCacheHitCount());
                log.warn("L2 Misses: " + sessionStats.getSecondLevelCacheMissCount());
                log.warn("L2 Puts:   " + sessionStats.getSecondLevelCachePutCount());
                log.warn("QC Hits:   " + sessionStats.getQueryCacheHitCount());
                log.warn("QC Misses: " + sessionStats.getQueryCacheMissCount());
                log.warn("QC Puts:   " + sessionStats.getQueryCachePutCount());

                sessionStats.clear();
            }
        }
        catch (Throwable t) {
            // don't let errors in diagnostic code bring everybody down
            log.error(t.getMessage(), t);
        }

    }

    public void printCommitStatistics() {
        try {
            if (_session != null) {
                SessionStatistics stats = _session.getStatistics();
                println("Commit with " + 
                        Util.itoa(stats.getEntityCount()) + 
                        " entities and " + 
                        Util.itoa(stats.getCollectionCount()) +
                        " collections");
            }
        }
        catch (Throwable t) {
            // don't let errors in diagnostic code bring everybody down
            log.error(t.getMessage(), t);
        }
    }

    /**
     * Recreate the Hibernate session.
     *
     * close() would do what we need but for unknown reasons it sets
     * a funny _isClosed flag that prevents a new session from being opened.
     * Why was that??
     *
     * ky - this used to catch/log Throwable.  But it now throws because
     * callers may need to know that we can't do something as basic as closing
     * a connection -- so that they can terminate, etc.
     */
    public void reconnect() throws GeneralException  {
        close();
        _isClosed = false;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Unit Test Back doors
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Provide some low-level lock primitives for testing concurrency.
     * Once application level locking is fleshed out we won't really need
     * these but they're good to have as examples in case we also want
     * to support transaction locking.
     *
     * TODO: Searching only by name, need to have both id and name 
     * variants or be smarter about id syntax!
     */
    public <T extends SailPointObject> T transactionLock(Class<T> cls, String id) 
        throws GeneralException {

        T result = null;

        try {
            startTransaction();
            Session session = getSession();

            // basically all we do is ask for LockMode.PESSIMISTIC_WRITE
            // This is not cacheable - we always want to go to the database in
            // order to obtain the transaction lock.

            result = cls.cast(session.createCriteria(cls)
                              .add( Restrictions.eq("name", id) )
                              .setCacheable(false)
                              .setLockMode(LockMode.PESSIMISTIC_WRITE)
                              .uniqueResult());
        }
        catch (Throwable e) {
            throw new GeneralException(e);
        }

        return result;
    }

    /**
     * The hibernate Dialect of the database.
     */ 
    public static Dialect getDialect() {
        return _dialect;
    }

    public static HibernatePersistenceManager getHibernatePersistenceManager(SailPointContext context) {
        // All kinds of ugly in here.  Unfortunately, there is not a good way to
        // get at the Hibernate Session and/or class metadata without digging through
        // all sorts of encapsulation that we shouldn't be breaking.
        if (!(context instanceof InternalContext)) {
            throw new IllegalArgumentException("Cannot get HibernatePeristenceManager with odd SailPointContext: " + context);
        }

        InternalContext internalContext = (InternalContext) context;
        PersistenceManager pm = internalContext.getPersistenceManager();
        HibernatePersistenceManager hpm = null;
        
        if (pm instanceof ClassPersistenceManager) {
            ClassPersistenceManager cpm = (ClassPersistenceManager) pm;
            try {
                hpm = (HibernatePersistenceManager) cpm.getManager(Identity.class);
            }
            catch (GeneralException e) {
                throw new RuntimeException(e);
            }
        }
        else if (pm instanceof HibernatePersistenceManager) {
            hpm = (HibernatePersistenceManager) pm;
        }

        if (null == hpm) {
            throw new IllegalStateException("Strange things are afoot at the Circle K: " + pm);
        }
        
        return hpm;
    }
    
    /**
     * Return the hibernate Session being used by the given SailPointContext -
     * 
     * <b>THIS METHOD SHOULD RARELY BE CALLED AND SHOULD NOT BE CONSIDERED A
     * PART OF THE PUBLIC API!  FOR THAT MATTER THIS WHOLE CLASS SHOULDN'T BE A
     * PART OF THE PUBLIC API.</b>
     */
    public static Session getSession(SailPointContext context) {
        // All kinds of ugly in here.  Unfortunately, there is not a good way to
        // get at the session without digging through all sorts of encapsulation
        // that we shouldn't be breaking.
        return getHibernatePersistenceManager(context).getSession();
    }
    
    /**
     * Static way we can tell in our UserType classes if we 
     * using oracle so that we can do special things if  
     * necessary. ( like for the case of LOBs ) 
     */
    public static boolean usingOracle() {
        return _usingOracle;
    }

    public static boolean usingSqlServerDbLocking() {
        return _usingSqlServerDbLocking;
    }

    /**
     * @param clazz
     * @return Unmodifiable Set of hibernate properties for the specified class.  
     *         Note that all properties are lowercased before being returned.
     */
    public Set<String> getProperties(Class clazz) {
        if (clazz == null) {
            return Collections.emptySet();
        }
                
        final String className = clazz.getName();
        Set<String> reservedNames = _propertyNameCache.get(className);
        
        if (reservedNames == null) {
            reservedNames = new HashSet<String>();
            ClassMetadata metadata = _sessionFactory.getClassMetadata(clazz);
            if (metadata != null) {
                String[] propertyNames = metadata.getPropertyNames();
                if (propertyNames != null) {
                    for (int i = 0; i < propertyNames.length; ++i) {
                        if (propertyNames[i] != null) {
                            reservedNames.add(propertyNames[i].toLowerCase());                            
                        }
                    }
                }
                // Don't know why this was excluded from getPropertyNames() but it was
                String idName = metadata.getIdentifierPropertyName();
                if (idName != null) {
                    reservedNames.add(idName.toLowerCase());                    
                }
            }
            synchronized (_propertyNameCache) {
                _propertyNameCache.put(className, reservedNames);
            }
        }
        
        return Collections.unmodifiableSet(reservedNames);
    }
    
    /**
     * @return Set of classes whose persistence is managed by Hibernate
     */
    public Set<Class> getManagedClasses() {
        if (_managedClasses == null) {
            _managedClasses = new HashSet<Class>();
            Metadata meta = HibernateMetadataIntegrator.INSTANCE.getMetadata();
            Iterator<PersistentClass> hibMappings = meta.getEntityBindings().iterator();
            while (hibMappings.hasNext()) {
                PersistentClass pclass = hibMappings.next();
                // skip abstract classes
                Boolean isAbstract = pclass.isAbstract();
                if (isAbstract == null || !isAbstract) {
                    Class clazz = pclass.getMappedClass();
                    // I don't think this can ever be null, but
                    // SchemaGenerator checked for some reason
                    if (clazz != null) {
                        _managedClasses.add(clazz);
                    }
                }
            }
        }
        
        return Collections.unmodifiableSet(_managedClasses);
    }
}
