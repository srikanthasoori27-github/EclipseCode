/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 *
 * A hibernate interceptor used to implement triggering and audit logging
 * of object modification.
 * 
 * djs: Renamed this to SailPointInterceptor from TriggerInterceptor 
 *
 * This interceptor is also used for cleanup of ORACLE clob objects when we 
 * are using Oracle.  Oracle CLOB objects stream data to the database
 * so when we are done with them and they have been flushed to the
 * db we have to close them using the freeTemporay. The CLOB objects
 * are registered with the interceptor using the registerLOB method.
 * 
 * It would be nice to chain these interceptors that are functionally
 * different. But there is nothing built into Hibernate to support 
 * this and since these are mutually exclusive it didn't seem worth
 * writing a "interceptor" handler, but it could easily be done if
 * necessary.
 *
 * This must be registered as a Session interceptor so it can save
 * state for a particular thread/session.
 *
 * Author: Jeff
 */

package sailpoint.persistence;

import java.io.Serializable;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hibernate.EmptyInterceptor;
import org.hibernate.EntityMode;
import org.hibernate.HibernateException;
import org.hibernate.Transaction;
import org.hibernate.criterion.ProjectionList;
import org.hibernate.criterion.Projections;
import org.hibernate.criterion.Restrictions;
import org.hibernate.type.Type;

import sailpoint.api.SailPointContext;
import sailpoint.api.SailPointFactory;
import sailpoint.object.Cacheable;
import sailpoint.object.Certification;
import sailpoint.object.CertificationArchive;
import sailpoint.object.Identity;
import sailpoint.object.LockInfo;
import sailpoint.object.PersistenceOptions;
import sailpoint.object.SailPointObject;
import sailpoint.object.WorkItemArchive;
import sailpoint.server.Auditor;
import sailpoint.server.InternalContext;
import sailpoint.tools.GeneralException;
import sailpoint.tools.JdbcUtil;
import sailpoint.tools.JdbcUtil.ClobHandler;
import sailpoint.tools.Util;

import javax.persistence.FlushModeType;
import javax.persistence.PersistenceException;

public class SailPointInterceptor extends EmptyInterceptor {

    /**
	 * generated serial version ID
	 */
	private static final long serialVersionUID = 1449180755579232778L;

	static private Log log = LogFactory.getLog(SailPointInterceptor.class);

    public static boolean TraceTrackers = false;

    public static boolean TraceTransaction = false;

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * The collection of objects being created.
     * We can't actually tell this reliably right now, they will
     * all look like updates.  Needs more thought...
     */
    Map<SailPointObject,SailPointObject> _creates = 
    new HashMap<SailPointObject,SailPointObject>();

    /**
     * The list of flushed modifications we're tracking.
     */
    Map<SailPointObject,SailPointObject> _updates = 
    new HashMap<SailPointObject,SailPointObject>();

    /**
     * The list of objects being deleted.
     */
    Map<SailPointObject,SailPointObject> _deletes = 
    new HashMap<SailPointObject,SailPointObject>();

    /**
     * Kludge to defer tracker warnings when we're commiting
     * a recursive transaction.
     */
    boolean _deferTrackerReset;

    /**
     * Thread local object to store the various CLOBs we 
     * are processing so they can be freed.
     */
    static final ThreadLocal<Set<Object>> _threadLOBs = new ThreadLocal<Set<Object>>();

    /**
     * Handler for removing the oracle clobs, if working in an Oracle environment.
     */
    private static ClobHandler _handler;

    /**
     * When set, this gets called with every call to onLoad() and can be used
     * to determined whether an object has been loaded by the session.
     */
    private LoadDetector _loadDetector;
    
    private HibernatePersistenceManager _hibernatePersistenceManager;

    /**
     * Options to control persistence.
     */
    PersistenceOptions _options;

    /**
     * An empty array returned from findDirty if we want to suppress dirty
     * checking an object.  Saves having to allocate one and GC it every time.
     */
    int[] _emptyPropertyIndexes = new int[0];

    /**
     * Number of dirty objects flushed.
     */
    int _dirtyFlushed;
    int _cleanIgnored;
    int _objectsLoaded;

    //////////////////////////////////////////////////////////////////////
    //
    // Construction
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Set the LoadDetector to be called every time onLoad() is called.  This
     * can be used to help determine if an object is loaded into the session.
     */
    public void setLoadDetector(LoadDetector detector) {
        _loadDetector = detector;
    }

    /**
     * Set the persistence options to be used in this Hibernate session.
     */
    public void setPersistenceOptions(PersistenceOptions ops) {
        _options = ops;
    }
    
    public void setHibernatePersistenceManager(
    		HibernatePersistenceManager _hibernatePersistenceManager) {
    	this._hibernatePersistenceManager = _hibernatePersistenceManager;
    }

    public PersistenceOptions getPersistenceOptions() {
        return _options;
    }
    
    //////////////////////////////////////////////////////////////////////
    //
    // Interceptor interface
    //
    //////////////////////////////////////////////////////////////////////


	/**
     * From the Hibernate Javadocs:
     * Called when the Hibernate transaction is begun via the transaction API.
     * Will not be called if transactions are being controlled via some
     * other mechanism (CMT, for example).
     *
     * Here we can assume that anything left in our mod list has been
     * abandoned.  It should already have been cleared by the
     * previous afterTransactionCompletion.
     */
    @Override
	public void afterTransactionBegin(Transaction t) {

        // these are annoying because all the threads hit them, 
        //if (log.isInfoEnabled())
        //log.info("afterTransactionBegin");

        if (!_deferTrackerReset)
            resetTrackers(true);
    }


	/**
     * From the Hibernate Javadocs:
     * Instantiate the entity class.  Return null to indiciate that
     * Hibernate should use the default constructor of the class.  The
     * identifier property of the returned instance should be  initialized
     * with the given identifier. 
     *
     * IdentityIQ: 
     * This looks complicated.  For now, blow off trying to track creates.  
     */
    @Override
	public Object instantiate(String entityName, EntityMode entityMode,
                              Serializable id) {

        // make this debug, it's annoying when unit tests poll for TaskResults
        //if (log.isInfoEnabled())
        //log.info("Instantiating " + entityName);

        return null;
    }

    /**
     * Call the LoadDetector onLoad() if one has been set.
     */
    @Override
    public boolean onLoad(Object entity, Serializable id, Object[] state,
                          String[] propertyNames, Type[] types) {

        /* kludge used when debugging Set order problem
        if ("2c9081e03013b847013013b897f10025".equals(id))
            log.warn("Loading Mary");
        */

        if (TraceTransaction) {
            if (entity instanceof SailPointObject) {
                SailPointObject spo = (SailPointObject)entity;
                System.out.println("Loading " + 
                                   entity.getClass().getSimpleName() + 
                                   " " + 
                                   LoadDetector.getName(entity, state, propertyNames));
                _objectsLoaded++;
            }
            else {
                System.out.println("Loading entity: " + entity);
            }
        }

        if (null != _loadDetector) {
            _loadDetector.onLoad(entity, id, state, propertyNames, types);
        }

        return super.onLoad(entity, id, state, propertyNames, types);
    }

    /**
     * From the Hibernate Javadocs:
     * Called before an object is saved. The interceptor may modify the
     * state, which will be used for the SQL INSERT and propagated to
     * the persistent object. 
     */
    @Override
	public boolean onSave(Object entity,
                          Serializable id,
                          Object[] state,
                          String[] propertyNames,
                          Type[] types) {

        boolean modified = false;

        if (entity instanceof SailPointObject) {
            SailPointObject spo = (SailPointObject)entity;

            if (log.isInfoEnabled())
                log.info("Saving " + spo.getClass().getSimpleName() + 
                         (spo.hasName() ? spo.getName() : spo.getId()));

            for (int i = 0 ; i < propertyNames.length ; i++) {
                if ("created".equals( propertyNames[i])) {
                    // HACK: for testing let the creation date be
                    // specified, convenient for fake history generation.
                    // This is also what you would want when importing
                    // objects between systems?
                    if (state[i] == null) {
                        state[i] = new Date();
                        modified = true;
                    }
                    break;
                }
            }

            if (isTrackable(spo)) {
                if (_creates.get(spo) == null) {
                    if (log.isInfoEnabled())
	                    log.info("Adding to create list: " + spo.getName());
                    
                    _creates.put(spo, spo);
                }
            }
        }

        return modified;
    }

	@Override
	public int[] findDirty(Object entity,
                           Serializable id,
                           Object[] currentState,
                           Object[] previousState,
                           String[] propertyNames,
                           Type[] types) {

        // according to the interwebz, the return values are
        // null - use default algorithm
        // empty array - entity is not dirty
        // array of property indecies - entity is dirty
        int[] retval = null;

        // TODO: can we handle Collections too?
        if (entity instanceof SailPointObject) {
            SailPointObject spo = (SailPointObject)entity;
            if (_options != null && _options.isExplicitSaveMode() && !spo.isDirty()) {
                retval = _emptyPropertyIndexes;
                _cleanIgnored++;
                if (log.isInfoEnabled()) {
                    if (spo.hasName())
                        log.info("Suppressing flush of non-dirty object: " + 
                                 spo.getClass().getSimpleName() + ": " + 
                                 spo.getName());
                    else
                        log.info("Suppressing flush of non-dirty object: " + 
                                 spo.toString());
                }
            }
            else {
                _dirtyFlushed++;
                if (log.isInfoEnabled()) {
                    if (spo.hasName())
                        log.info("Allowing flush of dirty object: " + 
                                 spo.getClass().getSimpleName() + ": " + 
                                 spo.getName());
                    else
                        log.info("Allowing flush of dirty object: " + 
                                 spo.toString());
                }
            }
        }

        return retval;
	}

    /**
     * From the Hibernate Javadocs:
     * Called when an object is detected to be dirty, during a flush.
     * The interceptor may modify the detected currentState, which will
     * be propagated to both the database and the persistent object. 
     * Note that not all flushes end in actual synchronization with the
     * database, in which case the new currentState will be propagated to
     * the object, but not necessarily (immediately) to the database. 
     * It is strongly recommended that the interceptor not modify the
     * previousState. 
     */
    @Override
	public boolean onFlushDirty(Object entity,
                                Serializable id,
                                Object[] currentState,
                                Object[] previousState,
                                String[] propertyNames,
                                Type[] types) {

        boolean modified = false;

        if (entity instanceof SailPointObject) {
            SailPointObject spo = (SailPointObject)entity;

            if (log.isInfoEnabled())
                log.info("Flushing " + spo.getClass().getSimpleName() + " " + 
                         (spo.getName() != null ? spo.getName() : spo.getId()));

            // set the modification date
            for (int i = 0 ; i < propertyNames.length; i++) {
                if ("modified".equals(propertyNames[i])) {
                    currentState[i] = new Date();
                    modified = true;
                    break;
                }
            }

            if (isTrackable(spo)) {
                // not sure if we can get here on a create, I think so
                if (_updates.get(spo) == null &&
                    _creates.get(spo) == null) {
                    if (log.isInfoEnabled())
                        log.info("Adding to update list: " + spo.getName());
                    
                    _updates.put(spo, spo);
                }
            }
            
            checkReadOnly(spo, previousState, propertyNames);

            // usually off, can be enabled with Spring
            if (HibernatePersistenceManager.EnableThreadLockConsistencyCheck) {
                checkLockConsistency(spo, previousState, propertyNames);
            }

            // diagnostic hack for JPMC
            // this is a really terrible violation of package layering, but
            // doing it cleanly is much more invasive, revisit later
            if (spo instanceof Identity) {
                sailpoint.api.LockTracker.checkLockRelease((Identity)spo);
            }
            
        }
        else {
            if (log.isInfoEnabled())
                log.info("Flushing: " + entity);
        }

        return modified;
    }

    /**
     * Try to detect when an object is being flushed without properly
     * locking it within the current thread.
     */
    private void checkLockConsistency(SailPointObject spo,
                                      Object[] previousState,
                                      String[] propertyNames) {

        String lock = spo.getLock();
        String expected = LockInfo.peekThreadLockId();

        if (lock != null) {
            if (expected == null) {
                lockWarning(spo, "No thread lock id allocated");
            }
            else if (!lock.startsWith(expected)) {
                lockWarning(spo, "Mismatched thread lock ids");
            }
        }
        else if (spo instanceof Identity) {
            // finding properties this way is dumb
            String previousLock = null;
            if (previousState != null) {
                for (int i = 0 ; i < propertyNames.length; i++) {
                    if ("lock".equals(propertyNames[i])) {
                        Object o = previousState[i];
                        // could this ever not be String?
                        if (o != null) {
                            previousLock = o.toString();
                        }
                    }
                }
            }
            
            if (previousLock == null && previousState != null) {
                // would be nice to warn here, unfortunately unlocked
                // Identities get flushed all the time in testing, import,
                // debug page, etc.
                /*
                if (log.isInfoEnabled()) {
                    log.info("Flushing unlocked Identity: " + spo.getName());
                }
                */
                // Temporary curiosity
                log.info("Flushing unlocked Identity: " + spo.getName());
            }
            else if (expected == null) {
                lockWarning(spo, "No thread lock id allocated");
            }
            else if (previousLock != null && !previousLock.startsWith(expected)) {
                lockWarning(spo, "Unlocking with a mismatched lock id");
            }
        }
    }
    
    /**
     * Log a lock warning.
     * Optionally throw an exception if configured, throwing is nice during
     * testing so we know right away.  In deployment throwing should not
     * be enabled.
     */
    private void lockWarning(SailPointObject spo, String warning) {
        // todo: make this Spring configurable?
        String msg = spo.getClass().getName() + ":" + spo.getName() +
            "  " + warning;

        if (HibernatePersistenceManager.ThrowOnThreadLockInconsistency) {
            throw new RuntimeException(msg);
        }
        else {
            log.warn(msg);
            log.warn(Util.stackToString(new RuntimeException()));
        }
    }

    /**
     * Called before an object is deleted. It is not recommended that the
     * interceptor modify the state. 
     */
    @Override
	public void onDelete(Object entity,
                         Serializable id,
                         Object[] state,
                         String[] propertyNames,
                         Type[] types) {

        if (entity instanceof SailPointObject) {
            SailPointObject spo = (SailPointObject)entity;

            if (log.isInfoEnabled())
                log.info("Deleting " + spo.getClass().getSimpleName() + 
                         (spo.hasName() ? spo.getName() : spo.getId()));

            if (isTrackable(spo)) {
                if (_deletes.get(spo) == null) {
                    if (log.isInfoEnabled())
                        log.info("Adding to delete list: " + spo.getName());
                    
                    _deletes.put(spo,spo);
                }
            }
            
            if (spo.isImmutable() && !isReadOnlyOverride()) {
            	throw new ImmutableModificationException();
            }
        }
    }
   
    /**
     * From the Hibernate Javadocs:
     * Called before a transaction is committed (but not before rollback).
     *
     * UGH: I wanted to handle auditing by forwarding the change
     * lists to InternalSession and letting it drop AuditEvent objects
     * into this transaction.  Unfortunately that doesn't work, Hibernate
     * is in some state right now where any further updates will not be
     * flushed and committed.  We have to wait till 
     * afterTransactionCompletion and let it start a new transaction
     * to contain the AuditEvents and commit a second time.  
     * 
     * We might be able to save the second commit by generating
     * the AuditEvent as onFlushDirty is called?
     */
    @Override
	public void beforeTransactionCompletion(Transaction t) {

        // these are annoying because all the threads hit them
        //if (log.isInfoEnabled())
        //log.info("beforeTransactionCompletion");

        try {
            // this is a terrible violation of encapsulation but 
            // I don't have time to do it right, revisit after the ATT demo
            SailPointContext con = SailPointFactory.getCurrentContext();
            if (con instanceof InternalContext) {
                InternalContext ic = (InternalContext)con;
                ic.beforeCommit(_creates.keySet(), 
                                _updates.keySet(), 
                                _deletes.keySet());
            }

            //TODO: We could do this here. Synchronization will keep track of Transaction status. However, this will not get called
            // if the commit/rollback fails -rap
//            t.registerSynchronization(new Synchronization() {
//                @Override
//                public void beforeCompletion() {
//                    //Already done
//                }
//
//                /**
//                 * We use this as a hook for auditing object modifications.
//                 * We pass the sets for creates, updates, and deletes to
//                 * InternalContext which will check which ones need to be audited
//                 * and may create AuditLog objects.  We have to commit after this
//                 * to get the audit records created.  Since this commit will cause
//                 * another before/after call to the interceptor we set a flag
//                 * to prevent recursion.  In theory as long as ic.afterCommit returned
//                 * false we wouldn't do the extra commit and would only have one
//                 * level of recursion but occasionally there are cases where
//                 * an object in the session is continually dirty, probably due
//                 * to an incorrect equals() method on a user type.  When that happens
//                 * we always attempt to audit this object (which more than likely
//                 * isn't audited)  but each time we call back to afterCommt() it
//                 * returns true and we commit again.  To prevent this we have to
//                 * check the deferTrackerReset flag up front.
//                */
//                @Override
//                public void afterCompletion(int status) {
//                    if (!_deferTrackerReset) {
//
//                        // the afterCommit method may create AuditEvent objects
//                        // which will start a new transaction, which will recusively
//                        // call afterTransactionBegin.  This normally resets the
//                        // tracker state but we don't want to do that here as it will
//                        // corrupt the keySet collections.
//                        _deferTrackerReset = true;
//                        try {
//                            // is this reliable?
//                            if (status == 3) {
//
//                                // this is a terrible violation of encapsulation but
//                                // I don't have time to do it right, revisit after the ATT demo
//                                SailPointContext con = SailPointFactory.getCurrentContext();
//                                if (con instanceof InternalContext) {
//                                    InternalContext ic = (InternalContext)con;
//                                    // this may also start a transaction to reload object caches
//                                    if (ic.afterCommit(_creates.keySet(),
//                                            _updates.keySet(),
//                                            _deletes.keySet())) {
//
//                                        // Returning true means that other things were saved,
//                                        // such as AuditEvents.  We're supposed to commit
//                                        // again.  Flush the trackers before we do that
//                                        // so we don't hit them again when we recurse
//                                        // during the commit.  Are we even allowed to
//                                        // commit here?
//                                        resetTrackers(false);
//                                        ic.commitTransaction();
//                                    }
//                                }
//                            }
//
//                            // commit or rollback, these go away
//                            resetTrackers(false);
//                        }
//                        catch (Throwable e) {
//                            log.error("Error occurred in afterTransactionCompletion()", e);
//                        }
//                        finally {
//                            _deferTrackerReset = false;
//                            _synchronizerAfterTransactionRun = true;
//                        }
//                    }
//                }
//            });
        }
        catch (Throwable e) {
            log.error("Error occurred in beforeTransactionCompletion()", e);
        }

    }

    /**
     * From the Hibernate Javadocs:
     * Called after the transaction is committed or rolled back.
     *
     * After Upgrade to Hibernate 5.3.3, this no longer carries the actual Transaction status with it. Therefore, we can't
     * tell if the transaction was successfully committed or rolled back.
     * We use this as a hook for auditing object modifications.
     * We pass the sets for creates, updates, and deletes to
     * InternalContext which will check which ones need to be audited
     * and may create AuditLog objects.  We have to commit after this
     * to get the audit records created.  Since this commit will cause
     * another before/after call to the interceptor we set a flag
     * to prevent recursion.  In theory as long as ic.afterCommit returned
     * false we wouldn't do the extra commit and would only have one
     * level of recursion but occasionally there are cases where
     * an object in the session is continually dirty, probably due
     * to an incorrect equals() method on a user type.  When that happens
     * we always attempt to audit this object (which more than likely
     * isn't audited)  but each time we call back to afterCommt() it
     * returns true and we commit again.  To prevent this we have to
     * check the deferTrackerReset flag up front.
     *
     */
    @Override
	public void afterTransactionCompletion(Transaction t) {
        
//        if (log.isInfoEnabled()) {
//            log.info("afterTransactionCompletion");
//        }

//         since auditing will commit another transaction, check
//         this to prevent recursion
        if (!_deferTrackerReset) {

            // the afterCommit method may create AuditEvent objects
            // which will start a new transaction, which will recusively
            // call afterTransactionBegin.  This normally resets the
            // tracker state but we don't want to do that here as it will
            // corrupt the keySet collections.
            _deferTrackerReset = true;
            try {
                // is this reliable?
                // Transaction no longer carries the actual status into afterTransactionCompletion. Will always be NOT_ACTIVE here now
                // Go ahead and call down anyways. Shouldn't cause much overhead in the case of rollback. Other option is to register a
                // synchronizer to the Transaction, but this won't work if Rollback/Commit fails. Pick your poison -rap
//                if (t.getStatus() == TransactionStatus.COMMITTED) {

                    // this is a terrible violation of encapsulation but
                    // I don't have time to do it right, revisit after the ATT demo
                    SailPointContext con = SailPointFactory.getCurrentContext();
                    if (con instanceof InternalContext) {
                        InternalContext ic = (InternalContext)con;
                        // this may also start a transaction to reload object caches
                        if (ic.afterCommit(_creates.keySet(),
                                _updates.keySet(),
                                _deletes.keySet())) {

                            // Returning true means that other things were saved,
                            // such as AuditEvents.  We're supposed to commit
                            // again.  Flush the trackers before we do that
                            // so we don't hit them again when we recurse
                            // during the commit.  Are we even allowed to
                            // commit here?
                            resetTrackers(false);
                            ic.commitTransaction();
                        }
                    }
//                }

                // commit or rollback, these go away
                resetTrackers(false);
            }
            catch (Throwable e) {
                log.error("Error occurred in afterTransactionCompletion()", e);
            }
            finally {
                _deferTrackerReset = false;
            }
        }
    }

    /**
     * After a flush, free any LOBs that have 
     * been registered.
     */
    @Override
    @SuppressWarnings(value = "rawtypes")
	public void postFlush(Iterator entities) {

        // clear dirty flags 
        // doing this here rather than on onFlushDirty because now we're
        // sure it is in the database
        // TODO: will this work for collections?
        while (entities.hasNext()) {
            Object obj = entities.next();
            if (obj instanceof SailPointObject) {
                SailPointObject spo = (SailPointObject)obj;
                spo.setDirty(false);
            }
        }

        Set<Object> tempLobs = _threadLOBs.get();
        if (tempLobs != null) {
            try {
                // there is no base class shared by oracle.sql.CLOB and
                // oracle.sql.BLOB so we have to dig into the object
                // and get the freeTemporary method.
                for (Iterator<Object> iter = tempLobs.iterator(); iter.hasNext();) {
                    Object lob = iter.next();
                    if ( _handler == null ) {
                        _handler = (ClobHandler)Util.createObjectByClassName(JdbcUtil.ORACLE_CLOB_HANDLER);
                    }
                    _handler.free(lob);
                }
            } catch(GeneralException e) {
                if (log.isErrorEnabled())
                    log.error("Error freeing CLOB value:" + e.getMessage(), e);
            } finally {
                // null out the clobs
                tempLobs.clear();
                tempLobs = null;
                _threadLOBs.set(null);
            }
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Trackers
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Return true if this object is interesting to track.
     *
     * Prior to 5.2 this returned the result of Auditor.isAudited(o)
     * since the only thing we used the interceptor for was auditing.
     * Starting with 5.2 we also now use this for cache invalidation 
     * etc.
     * 
     * Note: It is important that this be kept in sync with the code 
     * in InternalContext.
     * 
     * TODO: add a registration mechanism where the owner of the 
     * HibernatePersistenceManager can tell it which classes it wants to track
     */
    private boolean isTrackable(SailPointObject o) {

        // Application and IntegrationConfigs were added so that the IntegrationConfigCache
        // could remain up-to-date when modified.
        return (Auditor.isAudited(o) ||
                (o instanceof Cacheable) ||
                (o instanceof sailpoint.object.Rule) ||
                (o instanceof sailpoint.object.Application) ||
                (o instanceof sailpoint.object.IntegrationConfig));
    }

	private void checkReadOnly(SailPointObject spo, Object[] previousState,
			String[] propertyNames) {
    	
		if (isImmutableObject(spo)) {
			boolean readOnly = false;
			
			// first catch objects where immutable is true
			for (int i = 0; i < propertyNames.length; i++) {
				if ("immutable".equals(propertyNames[i])) {
					// previous state is null when the object is detached from the session
					// fetch the previous state from the db directly
					if (null == previousState) {
						readOnly = getImmutableFromDB(spo.getClass(), spo.getId());
					}
					else if (null != previousState[i]) {
						readOnly = (Boolean)previousState[i];
					}
					break;
				}
			}
			
			// now check existing objects endorsed and persistence options not set
			if (readOnly && !isReadOnlyOverride()) {
				throw new ImmutableModificationException();
			}
		}
	}
	
	private boolean getImmutableFromDB(Class<?> cls, String id) {
		boolean retValue = false;
        
        try {
            if (_hibernatePersistenceManager.getSession().getFlushMode() == FlushModeType.AUTO)
            {
                throw new HibernateException("Flush mode is set to auto.");
            }
            
            ProjectionList proj = Projections.projectionList();
            proj.add(Projections.property("immutable"));
            
            Object result = _hibernatePersistenceManager.getSession().createCriteria(cls)
                .add( Restrictions.eq("id", id) )
                .setProjection(proj)
                .uniqueResult();
            
            if (Boolean.TRUE.equals(result)) {
            	retValue = true;
            }
        }
        catch (PersistenceException e) {
            log.error(String.format("unable to obtain previous state for class %s with id %s, potentially overriding immutable object!!!", cls.getName(), id) ,e);
        }
        
        log.info("Retrieved immutable  value: " + retValue + " from db for id: " + id + " class: " + cls.getSimpleName());
        return retValue;
	}
    
    boolean isReadOnlyOverride() {
    	return null != getPersistenceOptions() && getPersistenceOptions().isAllowImmutableModifications();
    }

	boolean isImmutableObject(SailPointObject o) {
    	return (o instanceof WorkItemArchive) || (o instanceof Certification) || (o instanceof CertificationArchive);
    }
    
    /**
     * Flush our tracking lists.
     */
    private void resetTrackers(boolean warn) {

        if (warn) {
            if (_creates.size() > 0)
                log.error("Transaction create list not empty");

            if (_updates.size() > 0)
                log.error("Transaction update list not empty");

            if (_deletes.size() > 0)
                log.error("Transaction delete list not empty");
        }

        if (TraceTrackers) {
            if (_creates.size() > 0 ||
                _updates.size() > 0 ||
                _deletes.size() > 0) {
                System.out.println(Util.itoa(_creates.size()) + " creates, " + 
                                   Util.itoa(_updates.size()) + " updates, " + 
                                   Util.itoa(_deletes.size()) + " deletes");
            }
        }

        _creates.clear();
        _updates.clear();
        _deletes.clear();
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Extended Interface
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * This method is called by our custom types MapType
     * and XMLType when we are dealing with an Oracle
     * datasource.
     */
    public static void registerLOB(Object lob) {
        if ( lob != null ) {
            getLOBs().add(lob);
        }
    }

    public static Set<Object> getLOBs() {
        Set<Object> tempLobs = _threadLOBs.get();
        if (tempLobs == null) {
            tempLobs = new HashSet<Object>();
            _threadLOBs.set(tempLobs);
        }
        return tempLobs;
    }

    /**
     * This is intended to be called by HibernatePersistenceManager whenever
     * it calls session.replicate rather than session.save or 
     * session.saveOrUpdate.  This happens when we call 
     * SailPointContext.importObject rather than SailPointContext.saveObject
     * or simply let dirty checking find dirty objects.  
     *
     * For unknown reasons, there is logic down in hibernate that won't
     * call the interceptor if the entity is being replicated.  This means
     * that onFlushDirty will not be called and we won't be able to track
     * the update to this object which is necessary later in 
     * afterTransactionCommit for auditing and cache invalidation.
     *
     * Since we control when session.repliate is called, the code doing
     * that must call this interceptor method to add the object to the
     * update list.
     *
     */
    public void addUpdate(SailPointObject spo) {

        if (log.isInfoEnabled())
            log.info("Adding update: " + spo.getClass().getSimpleName() + " " + 
                     (spo.getName() != null ? spo.getName() : spo.getId()));

        // set the modification date
        spo.setModified(new Date());

        if (isTrackable(spo)) {
            if (_updates.get(spo) == null &&
                _creates.get(spo) == null) {
                _updates.put(spo, spo);
            }
        }
        else {
            log.info("Update not trackable");
        }
    }

    /**
     * Reset the statistics kept between commits.
     */
    public void resetTransactionLog() {
        _dirtyFlushed = 0;
        _cleanIgnored = 0;
        _objectsLoaded = 0;
    }

    public void logTransaction() {
        if (TraceTransaction) {
            System.out.println("*** Transaction Statistics ***");
            System.out.println(Util.itoa(_dirtyFlushed) + " dirty objects flushed, " + 
                               Util.itoa(_cleanIgnored) + " clean objects ignored, " +
                               Util.itoa(_objectsLoaded) + " objects loaded.");
        }
        resetTransactionLog();
    }

}
