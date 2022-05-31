/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.server;

import sailpoint.api.SailPointContext;
import sailpoint.object.Identity;
import sailpoint.object.WorkItem;
import sailpoint.tools.GeneralException;

/**
 * The interface of a class that can be registered to handle
 * modification to work items. Handlers will be called
 * automatically as changes to work items are committed.
 *
 * The handler might have side effects such as deleting
 * a completed work item, changing ownership, enabling
 * a pending object, etc.
 *
 * Currently it is necessary for system components to 
 * use a Workflower object to call the handlers. Handlers
 * should not be called directly, nor are they 
 * called as a side effect of committing a change.
 * 
 * @author Jeff
 */
public interface WorkItemHandler {

    /**
     * Called by the system when ownership changes.
     */
    public void forwardWorkItem(SailPointContext con, WorkItem item,
                                Identity newOwner)
        throws GeneralException;

    /**
     * Allows the work item handler to hook in to the forward checks to do its own thing, if needed.
     * For now only used by Certificationer.
     */
    default Identity checkForward(SailPointContext context, WorkItem item, Identity src, boolean audit, Identity requester)
        throws GeneralException {

        // Do nothing by default.
        return src;
    }

    /**
     * Called by the system before a work item is about to be stored.
     * This can throw validation exceptions.
     */
    public void validateWorkItem(SailPointContext con, WorkItem item)
        throws GeneralException;

    /**
     * Called by the system after a modification to the work item
     * has been stored. Foreground should be true if this is a UI
     * request thread or any other thread that does not want to 
     * give up control for an indefinite period of time wile the
     * case advances. Usually the advance is fast, but you cannot
     * assume that.
     */
    public void handleWorkItem(SailPointContext con, WorkItem item,
                               boolean foreground)
        throws GeneralException;

}
