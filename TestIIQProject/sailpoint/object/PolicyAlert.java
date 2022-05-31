/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * An object holding options for notification of policy alerts.  
 * Most of the options are inerhteted from WorkItemConfig.
 *
 * Author: Jeff
 *
 * Although we inherit from WorkItemConfig, this won't have a 
 * Hibernate mapping.  Policy will store these as XML.
 * 
 */

package sailpoint.object;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;

/**
 * An object holding options for notification of policy alerts.  
 */
@XMLClass
public class PolicyAlert extends WorkItemConfig
{

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    private static final Log log = LogFactory.getLog(PolicyAlert.class);

    // The _disable flag inherited from SailPointObject is used to 
    // disable all alerts.

    /**
     * When true a PolicyViolation object will not be persisted to 
     * represent the violation. Only notifications or work items
     * will be generated.
     */
    boolean _noPolicyViolation;

    //////////////////////////////////////////////////////////////////////
    //
    // Constructor/Properties
    //
    //////////////////////////////////////////////////////////////////////

    public PolicyAlert() {
    }

    /**
     * Let Hibernate know this is not a real object.
     */
    public boolean isXml() {
        return true;
    }

    @XMLProperty
    public void setNoPolicyViolation(boolean b) {
        _noPolicyViolation = b;
    }

    /**
     * When true a PolicyViolation object will not be persisted to 
     * represent the violation. Only notifications or work items
     * will be generated.
     */
    public boolean isNoPolicyViolation() {
        return _noPolicyViolation;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Utilities
    //
    //////////////////////////////////////////////////////////////////////

}
