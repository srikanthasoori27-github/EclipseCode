/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.request;

import sailpoint.api.IdentityTriggerHandler;
import sailpoint.api.SailPointContext;
import sailpoint.object.Attributes;
import sailpoint.object.IdentityChangeEvent;
import sailpoint.object.IdentityTrigger;
import sailpoint.object.Request;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Reflection;


/**
 * This request executor calls and IdentityTriggerHandler with a queued event.
 * The purpose is to allow this processing to be backgrounded when an identity
 * is changed interactively (through the UI for example).  To create a request
 * to be executed by this executor, you just need call
 * setExecuteInBackground(true) on the IdentityTriggerHandler before calling
 * handleEvent().
 *
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */
public class IdentityTriggerRequestExecutor
    extends AbstractRequestExecutor {

    /**
     * The name of the RequestDefinition object in the repository.
     */
    public static final String DEF_NAME = "Identity Trigger Request";
    
    /**
     * The argument on the request that hold the AbstractChangeEvent.
     */
    public static final String ARG_EVENT = "event";

    /**
     * The argument on the request that hold the ID of the IdentityTrigger.
     */
    public static final String ARG_TRIGGER_ID = "identityTriggerId";

    
    /**
     * Default constructor.
     */
    public IdentityTriggerRequestExecutor() {
        super();
    }

    /* (non-Javadoc)
     * @see sailpoint.object.RequestExecutor#execute(sailpoint.api.SailPointContext, sailpoint.object.Request, sailpoint.object.Attributes)
     */
    public void execute(SailPointContext context, Request request,
                        Attributes<String, Object> args)
        throws RequestPermanentException, RequestTemporaryException {

        IdentityChangeEvent evt = (IdentityChangeEvent) args.get(ARG_EVENT);
        String triggerId = (String) args.get(ARG_TRIGGER_ID);
        if (null == evt) {
            throw new RequestPermanentException("Change event was not found in the request.");
        }
        if (null == triggerId) {
            throw new RequestPermanentException("Trigger ID was not found in the request.");
        }

        try {
            // Load the trigger.
            IdentityTrigger trigger =
                context.getObjectById(IdentityTrigger.class, triggerId);

            // Instantiate the handler for the trigger and execute the event.
            String handlerClass = trigger.getHandler();
            IdentityTriggerHandler handler =
                (IdentityTriggerHandler) Reflection.newInstance(handlerClass);
            handler.setContext(context);
            handler.handleEvent(evt, trigger);
        }
        catch (GeneralException e) {
            throw new RequestPermanentException(e);
        }
    }
}
