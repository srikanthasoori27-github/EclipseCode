/* (c) Copyright 2011 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.persistence;

import java.io.Serializable;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hibernate.type.Type;

/**
 * The LoadDetector helps to detect if a hibernate session has to load an
 * object.  This gets called by the SailPointInterceptor.onLoad().  If an
 * object being loaded by the session matches the property/value setup for
 * this detector, then it is tripped.  This is used to determine if an object
 * was already in the hibernate session before attempting to load it or not.
 *
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */
public class LoadDetector {

    private static final Log log = LogFactory.getLog(LoadDetector.class);

    ////////////////////////////////////////////////////////////////////////////
    //
    // FIELDS
    //
    ////////////////////////////////////////////////////////////////////////////

    private String property;
    private String value;
    
    private Boolean tripped;
    
    
    ////////////////////////////////////////////////////////////////////////////
    //
    // CONSTRUCTORS
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Constructor.
     * 
     * @param  property  The name of the property we are looking for.
     * @param  value     The value of the property that we are looking for.
     */
    public LoadDetector(String property, String value) {
        if ((null == property) || (null == value)) {
            throw new IllegalArgumentException("Property and value are both required.");
        }
        this.property = property;
        this.value = value;
    }

    
    ////////////////////////////////////////////////////////////////////////////
    //
    // METHODS
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Called when an object is loaded.  This will trip the load detector if the
     * object matches what we are looking for.
     */
    public void onLoad(Object entity, Serializable id, Object[] state,
                       String[] propertyNames, Type[] types) {

        if ("id".equals(this.property)) {
            if (log.isDebugEnabled())
                log.debug("checking object with ID = " + id);
            
            this.tripped = this.value.equals(id);
            
            if (log.isDebugEnabled())
                log.debug("checked against " + this.value + " ... tripped = " + this.tripped);
        }
        else if ("name".equals(this.property)) {
            String name = getName(entity, state, propertyNames);
            if (null == name) {
                if (log.isWarnEnabled())
                    log.warn("Could not retrieve name when trying to check load by name: " + entity);
            }
            else {
                this.tripped = this.value.equals(name);
            }
        }
    }
    
    /**
     * Return the name of the given entity.
     */
    static public String getName(Object entity, Object[] state, String[] propertyNames) {
        if (null != propertyNames) {
            for (int i=0; i<propertyNames.length; i++) {
                if ("name".equals(propertyNames[i])) {
                    Object name = state[i];
                    if (null != name) {
                        if (name instanceof String) {
                            return (String) name;
                        }
                        else {
                            if (log.isWarnEnabled())
                                log.warn("Name (" + name + ") isn't a string for entity: " + entity);
                        }
                    }
                    break;
                }
            }
        }

        return null;
    }

    /**
     * Return true if the object that we are looking for was loaded.
     */
    public boolean isTripped() {
        return (null != this.tripped) && this.tripped;
    }
}
