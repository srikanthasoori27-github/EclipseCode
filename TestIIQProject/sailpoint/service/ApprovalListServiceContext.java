/* (c) Copyright 2015 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.service;

import sailpoint.object.Identity;
import sailpoint.tools.GeneralException;


/**
 * A list context that provides information for listing approvals.
 */
public interface ApprovalListServiceContext extends BaseListServiceContext {

    /**
     * Return the owner of the approvals - either a specified user or the logged in user.
     */
    public Identity getOwner() throws GeneralException;

}
