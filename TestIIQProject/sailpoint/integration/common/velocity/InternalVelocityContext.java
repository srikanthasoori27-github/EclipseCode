/** Copyright (C) 2017 SailPoint Technologies, Inc. All rights reserved. */
package sailpoint.integration.common.velocity;

import java.util.HashMap;
import java.util.Map;

import org.apache.velocity.VelocityContext;

/**
 * This class will extend the VelocityContect class from Velocity engine. The
 * purpose of this class is to hide the implementation for remove(String) method
 * from the Velocity context class. This method hiding is required due to the
 * remove() method signature change in newer version of Velocity engine package.
 *
 */
public class InternalVelocityContext extends VelocityContext {

    private Map<String, Object> context;

    /**
     * Default Constructor
     */
    public InternalVelocityContext() {
        this(new HashMap<String, Object>());
    }

    /**
     * Parameterized constructor to setup context map object.
     * 
     * @param context - HashMap instance
     */
    public InternalVelocityContext(Map<String, Object> context) {
        super(context);
        this.context = context;
    }

    /* (non-Javadoc)
     * @see org.apache.velocity.context.AbstractContext#remove(java.lang.String)
     */
    public Object remove(String key) {
        if (key == null) {
            return null;
        }
        return this.context.remove(key);
    }

}
