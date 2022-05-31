package sailpoint.api;

import sailpoint.api.SailPointContext;
import sailpoint.object.Certification;
import sailpoint.object.Notifiable;
import sailpoint.object.WorkItem;
import sailpoint.tools.GeneralException;

public class EscalatorUtil {
 
    /***
     * Some logic to decide whether a reminder is actually needed to be sent from the Escalator
     * 
     * @throws GeneralException
     */
    public static boolean isReminderNeeded(Notifiable item, SailPointContext context) throws GeneralException{
        if (item instanceof WorkItem) 
        {
            return isWorkItemReminderNeeded((WorkItem)item, context);
        }
        else
        {
            return true;
        }
    }
        
    private static boolean isWorkItemReminderNeeded(WorkItem item, SailPointContext context) throws GeneralException{
        
        if (item.isCertificationRelated()) {
            Certification cert = context.getObjectById(Certification.class, item.getCertification());
            CertificationService svc = new CertificationService(context);
            // Bug 8211
            // If cert is complete but not ready for signoff due to challenge period, we dont need to send a reminder, 
            // since there is nothing the cert owner can do.
            // Exceptions: If there are challenged decisions to be acted on, we still send reminder.
            if (cert.isComplete() && (svc.isSignoffBlockedByChallengePeriod(cert) && !svc.isSignoffBlockedByChallengeDecisions(cert))) 
            {
                return false;
            }
        }   
        return true;
    }
}