/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.api;

import sailpoint.object.Attributes;
import sailpoint.object.Certification;
import sailpoint.object.EmailOptions;
import sailpoint.object.Identity;
import sailpoint.object.Notifiable;
import sailpoint.object.WorkItem;
import sailpoint.tools.GeneralException;

import java.util.Map;


/**
 * A NotificationHandler that can remind, escalate, and expire WorkItems.
 *
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */
public class WorkItemNotificationHandler implements NotificationHandler {

    private SailPointContext context;
    

    /**
     * Constructor.
     */
    public WorkItemNotificationHandler(SailPointContext context) {
        this.context = context;
    }

    
    /**
     * Return the given Notifiable as a WorkItem, or throw a GeneralException if
     * it is not a WorkItem.  If the Escalator.getNotificationHandler() method
     * is working properly, this should never throw.
     */
    private WorkItem getWorkItem(Notifiable item) throws GeneralException {

        if (!(item instanceof WorkItem)) {
            throw new GeneralException("Expected a work item: " + item);
        }

        return (WorkItem) item;
    }
    
    /* (non-Javadoc)
     * @see sailpoint.api.NotificationHandler#addEmailOptions(sailpoint.api.Escalator.NotificationSource, sailpoint.object.EmailOptions)
     */
    public void addEmailOptions(Notifiable item, EmailOptions options)
        throws GeneralException {

        WorkItem workItem = getWorkItem(item);
        String notname = workItem.getNotificationName();
        Certification certification = workItem.getCertification(context);       
        Map<String,Object> optionsMap = options.getVariables();

        // Escalator historically passes "item" and "itemName"
        // We add "workItem" and "workItemName" which is what everyone expects
        optionsMap.put(Escalator.NotificationEmailOptions.workItem.toString(), workItem);     
        optionsMap.put(Escalator.NotificationEmailOptions.workItemName.toString(), notname);     
        optionsMap.put(Escalator.NotificationEmailOptions.created.toString(), workItem.getCreated());
        
        if(certification != null) {
        	String certificationName = certification.getName();
	        optionsMap.put(Escalator.NotificationEmailOptions.certification.toString(), certification);
	        optionsMap.put(Escalator.NotificationEmailOptions.certificationName.toString(), certificationName);
        }
        
        Identity requester = workItem.getRequester();
        if (requester != null)
            optionsMap.put(Escalator.NotificationEmailOptions.requester.toString(), requester);

        // Coerce all the attributes into String form
        // !! this is what we had to do before EmailOptions supported maps
        // of any object.  We probably don't need this any more but I'm not
        // sure what existing templates are expecting
        // UPDATE: Now that we have Velocity templates, we don't need this at all!

        Attributes<String, Object> attributes = workItem.getAttributes();
        if (attributes != null) {
            for (String key : attributes.keySet()) {
                Object attribute = workItem.getAttribute(key);
                if ( attribute != null ) 
                    optionsMap.put(key, attribute.toString());
            }
        }
    }

    /* (non-Javadoc)
     * @see sailpoint.api.NotificationHandler#executeEscalation(sailpoint.api.Escalator.NotificationSource, sailpoint.object.Identity)
     */
    public void executeEscalation(Notifiable item, Identity newOwner)
        throws GeneralException {

        WorkItem workItem = getWorkItem(item);
        Workflower workflower = new Workflower(this.context);
        workflower.forward(workItem, null, newOwner, null, false, Workflower.ForwardType.Escalation);
    }

    /* (non-Javadoc)
     * @see sailpoint.api.NotificationHandler#executeExpiration(sailpoint.api.Escalator.NotificationSource)
     */
    public void executeExpiration(Notifiable item)
        throws GeneralException {

        WorkItem workItem = getWorkItem(item);
        workItem.setState(WorkItem.State.Expired);

        // if we expired an item, this will process side effects 
        // and commit
        Workflower workflower = new Workflower(this.context);

        // We need to pass a foreground/background indicator here to 
        // control how far the case advances.  Assuming we have to be in the
        // background since we're only used by Escalator which is only
        // used by WorkitemExpirationScanner? 
        // !! If this isn't true we've got potential problems where the
        // case could do something expsensive and hang a UI thread! - jsl
        
        workflower.process(workItem, false);
    }
}
