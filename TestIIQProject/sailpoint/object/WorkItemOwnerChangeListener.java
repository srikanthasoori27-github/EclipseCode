/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.object;

import sailpoint.tools.GeneralException;

/**
 * A listener interface that classes can implement to be able to handle work
 * items changing owners. This is not a typical listener or observer pattern
 * in that the listener cannot register interest in the work item. The work
 * item holds weak references (by ID) to the objects that can serve as
 * listeners, so it must explicitly enumerate all possible listeners. If you
 * implement this interface, you will need to modify some code around
 * WorkItem.notifyOwnerChangeListeners() so that your class is notified when
 * a work item owner is changed.
 *
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */
public interface WorkItemOwnerChangeListener {

    /**
     * Receive and handle a notification that the owner was changed on the given
     * WorkItem.
     * 
     * @param  resolver       The Resolver to use.
     * @param  item           The WorkItem that had its owner changed.      
     * @param  newOwner       The new owner of the work item.
     * @param  previousOwner  The previous owner of the work item.
     */
    public void workItemOwnerChanged(Resolver resolver, WorkItem item,
                                     Identity newOwner, Identity previousOwner)
        throws GeneralException;
}
