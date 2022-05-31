/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.api;

import java.util.List;
import java.util.Map;

import sailpoint.object.Certification;
import sailpoint.object.CertificationItem;
import sailpoint.object.EmailOptions;
import sailpoint.object.Identity;
import sailpoint.object.Notifiable;
import sailpoint.object.WorkItem;
import sailpoint.tools.GeneralException;


/**
 * A NotificationHandler that can remind and escalate CertificationItems.
 *
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */
public class CertificationItemNotificationHandler implements NotificationHandler {

    private SailPointContext context;
    private EmailSuppressor emailSuppressor;
    

    /**
     * Constructor.
     */
    public CertificationItemNotificationHandler(SailPointContext context,
                                                EmailSuppressor emailSuppressor) {
        this.context = context;
        this.emailSuppressor = emailSuppressor;
    }

    
    /**
     * Return the given Notifiable as a CertificationItem, or throw a
     * GeneralException if it is not a CertificationItem.  If the
     * Escalator.getNotificationHandler() method is working properly, this
     * should never throw.
     */
    private CertificationItem getCertificationItem(Notifiable item)
        throws GeneralException {

        if (!(item instanceof CertificationItem)) {
            throw new GeneralException("Expected a certification item: " + item);
        }
        
        // Bug #13033: we need to refetch the certitem because there is a decache 
        // which causes the certItem to get detached. 
        CertificationItem certItem = (CertificationItem) item;
        return context.getObjectById(CertificationItem.class, certItem.getId());
    }
    
    /* (non-Javadoc)
     * @see sailpoint.api.NotificationHandler#addEmailOptions(sailpoint.api.Escalator.NotificationSource, sailpoint.object.EmailOptions)
     */
    public void addEmailOptions(Notifiable item, EmailOptions options)
        throws GeneralException {

        CertificationItem certItem = getCertificationItem(item);
        Certification certification = certItem.getCertification();
        Map<String,Object> optionsMap = options.getVariables();

        optionsMap.put(Escalator.NotificationEmailOptions.created.toString(), certItem.getCreated());

        // Add the requester of the certification work item.
        List<WorkItem> items = certItem.getCertification().getWorkItems();
        if ((null != items) && !items.isEmpty()) {
            Identity requester = items.get(0).getRequester();
            if (null != requester)
                optionsMap.put(Escalator.NotificationEmailOptions.requester.toString(), requester);
        }
        
        if(certification != null) {
        	String certificationName = certification.getName();
	        optionsMap.put(Escalator.NotificationEmailOptions.certification.toString(), certification);
	        optionsMap.put(Escalator.NotificationEmailOptions.certificationName.toString(), certificationName);
        }
    }

    /* (non-Javadoc)
     * @see sailpoint.api.NotificationHandler#executeEscalation(sailpoint.api.Escalator.NotificationSource, sailpoint.object.Identity)
     */
    public void executeEscalation(Notifiable item, Identity newOwner)
        throws GeneralException {

        // What do we do for escalations?  Delegate for now.
        CertificationItem certItem = getCertificationItem(item);

        // Use the notification owner as the requester.
        Identity requester = item.getNotificationOwner(this.context);

        // Forward the delegation if this is already delegated.
        if (certItem.isDelegated()) {
            WorkItem workItem =
                this.context.getObjectById(WorkItem.class, certItem.getDelegation().getWorkItem());
            Workflower workflower = new Workflower(this.context);
            workflower.forward(workItem, requester, newOwner, null, false, Workflower.ForwardType.Escalation);
        }
        else {
            Certification cert = certItem.getCertification();
            certItem.delegate(requester, null, newOwner.getName(),
                              "Escalation of " + certItem.getShortDescription(), null);
            Certificationer certificationer = new Certificationer(this.context);
            certificationer.setEmailSuppressor(this.emailSuppressor);
            certificationer.refresh(cert);
        }
    }

    /* (non-Javadoc)
     * @see sailpoint.api.NotificationHandler#executeExpiration(sailpoint.api.Escalator.NotificationSource)
     */
    public void executeExpiration(Notifiable item) throws GeneralException {

        throw new GeneralException("CertificationItems are not expirable.");
    }
}
