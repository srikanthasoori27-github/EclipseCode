/* (c) Copyright 2009 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.api;

import sailpoint.object.IdentityChangeEvent;
import sailpoint.object.IdentityTrigger;
import sailpoint.object.Request;
import sailpoint.object.RequestDefinition;
import sailpoint.request.IdentityTriggerRequestExecutor;
import sailpoint.tools.GeneralException;

/**
 * An abstract implementation of IdentityTriggerHandler that will handle
 * backgrounding events to the request processor if this trigger handler it
 * to be executed in the background.
 *
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */
public abstract class AbstractIdentityTriggerHandler
    implements IdentityTriggerHandler {

    ////////////////////////////////////////////////////////////////////////////
    //
    // FIELDS
    //
    ////////////////////////////////////////////////////////////////////////////
    
    protected SailPointContext context;
    protected boolean executeInBackground;


    ////////////////////////////////////////////////////////////////////////////
    //
    // CONSTRUCTORS
    //
    ////////////////////////////////////////////////////////////////////////////
    
    /**
     * Constructor.
     */
    public AbstractIdentityTriggerHandler() {
        super();
    }

    
    ////////////////////////////////////////////////////////////////////////////
    //
    // ABSTRACT METHODS
    //
    ////////////////////////////////////////////////////////////////////////////
    
    /**
     * Subclasses should implement this method to execute the desired logic for
     * the given event.
     */
    protected abstract void handleEventInternal(IdentityChangeEvent event,
                                                IdentityTrigger trigger)
        throws GeneralException;
    

    ////////////////////////////////////////////////////////////////////////////
    //
    // IdentityTriggerHandler implementation
    //
    ////////////////////////////////////////////////////////////////////////////
    
    /**
     * Set the SailPointContext to use.
     */
    public void setContext(SailPointContext context) {
        this.context = context;
    }
    
    public void setExecuteInBackground(boolean background) {
        this.executeInBackground = background;
    }

    /**
     * This will queue the event if it is backgrounded, otherwise this just
     * calls the handleEventInternal() template method.
     */
    public void handleEvent(IdentityChangeEvent event, IdentityTrigger trigger)
        throws GeneralException {

        // Queue request if executing in background.
        if (queueEvent(event, trigger)) {
            return;
        }

        this.handleEventInternal(event, trigger);
    }

    /**
     * Queue this event to be run in the background if executeInBackground is
     * true.
     * 
     * @param  evt      The event to possibly queue.
     * @param  trigger  The trigger that was triggered.
     * 
     * @return True if the event was queued, false otherwise.
     */
    protected boolean queueEvent(IdentityChangeEvent evt, IdentityTrigger trigger)
        throws GeneralException {

        if (this.executeInBackground) {
            RequestDefinition def =
                this.context.getObjectByName(RequestDefinition.class,
                                             IdentityTriggerRequestExecutor.DEF_NAME);

            if (null == def) {
                throw new GeneralException("Request definition not found: " +
                                           IdentityTriggerRequestExecutor.DEF_NAME);
            }
            
            Request request = new Request(def);
            request.setAttribute(IdentityTriggerRequestExecutor.ARG_EVENT, evt);
            request.setAttribute(IdentityTriggerRequestExecutor.ARG_TRIGGER_ID,
                                 trigger.getId());
            RequestManager.addRequest(context, request);
        }
        
        return this.executeInBackground;
    }
}
