/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * Interceptor based on EmptyInterceptor with hooks for tracing.
 *
 * Author: Jeff
 *
 * This was used in some early experimentation but is no longer used.
 * It is required that normal installations of IIQ use 
 * SailPointInterceptor which also provides logging.  Kept around to use
 * as the basis for other interceptor experments.
 *
 * Interceptor based on the example from the Hibernate3 documentation.
 * Iterating over property names rather sucks, but I read somewhere that
 * you can't modify the object directly as it may be transient?
 */

package sailpoint.persistence;

import java.util.Date;
import java.io.Serializable;
import java.util.Iterator;

import org.hibernate.CallbackException;
import org.hibernate.EntityMode;
import org.hibernate.Transaction;
import org.hibernate.type.Type;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.SailPointObject;

public class DebugInterceptor implements org.hibernate.Interceptor {

    static private Log log = LogFactory.getLog(DebugInterceptor.class);

    private String getThreadName() {
        
        Thread t = Thread.currentThread();
        String name = t.getName();
        if (name == null)
            name = "???";
        return name;
    }

    private void trace(Object entity, String action) {

        if (log.isDebugEnabled()) {
            SailPointObject spo = (SailPointObject)entity;
            String cname = spo.getClass().getName();
            String oname = spo.getName();
            if (oname == null || !spo.isNameUnique()) {
                // could use description?
                oname = spo.getId();
            }
            
            trace(getThreadName() + ": " + action + " " + cname + " '" + oname + "'");
        }
    }

    @SuppressWarnings("unused")
    private void ttrace(String msg) {

        trace(getThreadName() + ": " + msg);
    }

    private void trace(String msg) {

        // just dump it to avoid annoying log4j prefixes
        //log.debug(msg);
        System.out.println(msg);
    }

    /**
     * Called before an object is deleted. It is not recommended that the
     * interceptor modify the state. 
     */
	public void onDelete(Object entity, 
                         Serializable id, 
                         Object[] state, 
                         String[] propertyNames, 
                         Type[] types) {

        //log.debug("onDelete");

        if (entity instanceof SailPointObject) {
            trace(entity, "Deleted");
        }
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
	public boolean onFlushDirty(Object entity, 
                                Serializable id, 
                                Object[] currentState, 
                                Object[] previousState, 
                                String[] propertyNames, 
                                Type[] types) {


        boolean modified = false;
        
        //log.debug("onFlushDirty");

        if (entity instanceof SailPointObject) {
            for (int i = 0 ; i < propertyNames.length; i++) {
                if ("modified".equals(propertyNames[i])) {
                    Date now = new Date();
                    currentState[i] = now;
                    modified = true;
                    break;
                }
            }

            trace(entity, "Flushed");
        }

        return modified;
	}

    /**
     * From the Hibernate Javadocs:
     * Called just before an object is initialized. The interceptor may
     * change the state, which will be propagated to the persistent object.
     * Note that when this method is called, entity will be an empty
     * uninitialized instance of the class. 
     */
	public boolean onLoad(Object entity, 
                          Serializable id, 
                          Object[] state, 
                          String[] propertyNames, 
                          Type[] types) {

        //log.debug("onLoad");

        if (entity instanceof SailPointObject) {

            if (log.isDebugEnabled()) {

                // id is apparently set, but not name
                SailPointObject spo = (SailPointObject)entity;
                String cname = spo.getClass().getName();
                Object oname = null;

                for (int i = 0 ; i < propertyNames.length ; i++) {
                    if ("name".equals( propertyNames[i])) {
                        oname = state[i];
                       break;
                    }
                }

                if (oname == null || !spo.isNameUnique())
                    oname = spo.getId();

                trace(getThreadName() + ": Loaded " + cname + " '" + oname + "'");
            }
        }

		return false;
	}

    /**
     * From the Hibernate Javadocs:
     * Called before an object is saved. The interceptor may modify the
     * state, which will be used for the SQL INSERT and propagated to
     * the persistent object. 
     */
	public boolean onSave(Object entity, 
                          Serializable id, 
                          Object[] state, 
                          String[] propertyNames, 
                          Type[] types) {

        boolean modified = false;

        //log.debug("onSave");

        if (entity instanceof SailPointObject) {
            for (int i = 0 ; i < propertyNames.length ; i++) {
                if ("created".equals( propertyNames[i])) {

                    trace(entity, "Created");
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

            trace(entity, "Saved");

        }
        return modified;
    }

	public void postFlush(Iterator entities) {
        //log.debug("onPostFlush");
    }

	public void preFlush(Iterator entities) {
        //log.debug("onPreFlush");
    }

	public Boolean isTransient(Object entity) {
        //log.debug("isTransient");
		return null;
	}

	public Object instantiate(String entityName, EntityMode entityMode, Serializable id) {
        //log.debug("instantiate");
		return null;
	}

	public int[] findDirty(Object entity,
                           Serializable id,
                           Object[] currentState,
                           Object[] previousState,
                           String[] propertyNames,
                           Type[] types) {

        //log.debug("findDirty");
		return null;
	}

	public String getEntityName(Object object) {
        //log.debug("getEntityName");
		return null;
	}

	public Object getEntity(String entityName, Serializable id) {
        //log.debug("getEntity");
		return null;
	}

	public void afterTransactionBegin(Transaction tx) {
        //log.debug("afterTransactionBegin");
    }

	public void afterTransactionCompletion(Transaction tx) {
        //ttrace("afterTransactionCompletion");
    }

	public void beforeTransactionCompletion(Transaction tx) {
        //ttrace("beforeTransactionCompletion");
    }

	public String onPrepareStatement(String sql) {
        // get the same thing from show-ql
        //if (log.isDebugEnabled()) {
        //log.debug("onPrepareStatement: " + sql);
        //}
		return sql;
	}

	public void onCollectionRemove(Object collection, Serializable key) throws CallbackException {
        //log.debug("onCollectionRemove");
    }

	public void onCollectionRecreate(Object collection, Serializable key) throws CallbackException {
        //log.debug("onCollectionRecreate");
    }

	public void onCollectionUpdate(Object collection, Serializable key) throws CallbackException {
        //log.debug("onCollectionUpdate");
    }

}
