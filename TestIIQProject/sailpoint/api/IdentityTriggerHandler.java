/* (c) Copyright 2009 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.api;

import sailpoint.object.IdentityChangeEvent;
import sailpoint.object.IdentityTrigger;
import sailpoint.tools.GeneralException;


/**
 * An interface for classes that want to handle events generated from identity
 * triggers.  These are required to have a public default constructor.
 *
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */
public interface IdentityTriggerHandler {

    /**
     * Set the SailPointContext for this handler to use.
     */
    public void setContext(SailPointContext context);

    /**
     * Set whether this trigger handler should handle event asynchronously in
     * the background rather than synchronously.
     * 
     * @param  background  True if events should be handled asynchronously.
     */
    public void setExecuteInBackground(boolean background);
    
    /**
     * Handle the given IdentityChangeEvent that was created in response to the
     * given IdentityTrigger.
     */
    public void handleEvent(IdentityChangeEvent event, IdentityTrigger trigger)
        throws GeneralException;

    /**
     * Generate a description about what this handler is doing for the given
     * event.
     */
    public String getEventDescription(IdentityChangeEvent event)
        throws GeneralException;
}
