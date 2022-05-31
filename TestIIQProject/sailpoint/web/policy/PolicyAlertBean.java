/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * Extension of WorkItemConfigBean to expose the extra
 * fields in PolicyAlert.
 *
 * Author: Jeff
 * 
 * Not much to do here but provide a way to get to 
 * the PolicyAlert.  I guess we could have provided a
 * getConfig accessor to WorkItemConfigBean but there
 * might be more we want to do here.
 *
 */
package sailpoint.web.policy;

import sailpoint.object.PolicyAlert;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.web.WorkItemConfigBean;

public class PolicyAlertBean extends WorkItemConfigBean {

    PolicyAlert _alert;

    public PolicyAlertBean(PolicyAlert alert) {
        super(alert);
        _alert = alert;
    }

    // don't mess with pass through properties, just go direct
    public PolicyAlert getAlert() {
        return _alert;
    }

    /**
     * Build a fresh PolicyAlert from the editing state.
     * If there are any references to other objects, they referenced
     * objects must be in the current Hibernate session.
     *
     * Since this isn't a true DTO, we just return the original
     * PolicyAlert object but we have to refetch all of the references!
     */
    public PolicyAlert commit() throws GeneralException {

        // this may throw
        super.commit(true);
        return _alert;
    }
    
    /**
     * No need to check for owners here
     */
    @Override
    protected Message checkOwners() {
        return null;
    }
    
}
    
