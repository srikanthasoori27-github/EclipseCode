/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * Interceptor based on the example from the Hibernate3 documentation.
 *
 * This is the SessionFactory interceptor so it is used by all sessions.
 *
 * Author: Jeff
 *
 * UPDATE: This is no longer used, we started using a session-specific
 * interceptor to track changes for auditing.  You apparently can't
 * have both a session interceptor and a factory interceptor active
 * at the same time.
 */

package sailpoint.persistence;

import java.util.Date;
import java.io.Serializable;

import org.hibernate.EmptyInterceptor;
import org.hibernate.type.Type;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.SailPointObject;

/**
 * Interceptor based on the example from the Hibernate3 documentation.
 * Iterating over property names rather sucks, but I read somewhere that
 * you can't modify the object directly as it may be transient?
 */
public class Interceptor extends EmptyInterceptor {

    static private Log log = LogFactory.getLog(Interceptor.class);

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

                log.debug("Loaded " + cname + " '" + oname + "'");
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
        }
        return modified;
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

        if (entity instanceof SailPointObject) {
            for (int i = 0 ; i < propertyNames.length; i++) {
                if ("modified".equals(propertyNames[i])) {
                    currentState[i] = new Date();
                    modified = true;
                    trace(entity, "Modified");
                    break;
                }
            }


        }

        return modified;
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

        if (entity instanceof SailPointObject) {
            trace(entity, "Deleted");
        }

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
            
            if (log.isDebugEnabled())
                log.debug(action + " " + cname + " '" + oname + "'");
        }
    }

}
