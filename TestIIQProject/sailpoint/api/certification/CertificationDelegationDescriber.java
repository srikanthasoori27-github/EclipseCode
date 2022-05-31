/**
 * 
 */
package sailpoint.api.certification;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

import sailpoint.api.SailPointContext;
import sailpoint.object.AbstractCertificationItem.Status;
import sailpoint.object.CertificationEntity;
import sailpoint.object.Identity;
import sailpoint.tools.BrandingServiceFactory;
import sailpoint.tools.GeneralException;
import sailpoint.tools.JsonHelper;
import sailpoint.tools.Message;
import sailpoint.web.messages.MessageKeys;

/**
 * @author peter.holcomb
 *
 */
public class CertificationDelegationDescriber {
    
    SailPointContext context;
    
    Locale locale;
    
    TimeZone timeZone;
    
    CertificationEntity entity;
    
    Map<String,Object> status;
    
    public CertificationDelegationDescriber(SailPointContext context, CertificationEntity entity, Locale locale, TimeZone timeZone) {
        this.context = context;
        this.locale = locale;
        this.timeZone = timeZone;
        this.entity = entity;
    }
    
    public Map getStatus() throws GeneralException{
        
        if(status==null) {
            status = new HashMap<String,Object>();
            status.put("hasComments", false);
            
            CertificationEntity.Status delegationStatus = entity.getEntityDelegationStatus();
            if(delegationStatus!=null) {
                Identity owner = getDelegationOwner();
                if(delegationStatus.equals(Status.Returned)) {
                    Message msg = new Message(MessageKeys.CERTIFICATION_DELEGATION_RETURNED, owner.getDisplayableName());
                    status.put("description", msg.getLocalizedMessage(locale, timeZone));
                } else if(entity.getDelegation().getCompletionComments()!=null) { 
                    Message msg = new Message(MessageKeys.CERTIFICATION_DELEGATION_PREVIOUS, owner.getDisplayableName());
                    status.put("description", msg.getLocalizedMessage(locale, timeZone));
                } else {
                    Message msg = new Message(MessageKeys.CERTIFICATION_DELEGATION_CURRENT, owner.getDisplayableName());
                    status.put("description", msg.getLocalizedMessage(locale, timeZone));
                }
                
                status.put("workItemId",entity.getDelegation().getWorkItem());
                status.put("delegationId",entity.getDelegation().getId());
                status.put("hasComments", entity.getDelegation().getCompletionComments()!=null);
                status.put("delegateName", entity.getDelegation().getOwnerName());
            }
        }
        status.put("entityId", entity.getId());
        status.put("isDelegated", entity.isEntityDelegated());
        return status;
    }

    private Identity getDelegationOwner() throws GeneralException {
        
        Identity owner = context.getObjectByName(Identity.class, entity.getDelegation().getOwnerName());
        if (owner == null) {
            String adminName = BrandingServiceFactory.getService().getAdminUserName();
            owner = context.getObjectByName(Identity.class, adminName );
        }
        return owner;
    }
    
    public String getStatusJson() throws GeneralException {
        return JsonHelper.toJson(getStatus());
    }

}
