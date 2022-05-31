/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * A workflow library with methods related to LCM workflows.
 *
 * Author: Dan
 *
 * Categories of services:
 *
 * LCM Audit Events
 *    specially constructed audit events for use in the request status 
 *    dashboard and reports
 *
 * UPDATE: The auditing stuff was needed by the approval set methods
 * over in IdentityLibrary so I moved them all over there.  There is
 * nothing left here, but keep it around for awhile.
 * 
 */

package sailpoint.workflow;


import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

@Deprecated
/**
 * @exclude
 * @deprecated 
 */
public class LCMLibrary {

    private static Log log = LogFactory.getLog(LCMLibrary.class);
    
    @Deprecated
    /**
     * @exclude
     * @deprecated 
     */
    public LCMLibrary() {
    }


}
