/**
 * 
 */
package sailpoint.api.certification;

import sailpoint.api.SailPointContext;
import sailpoint.object.BaseConstraint;
import sailpoint.object.Certification;
import sailpoint.object.CertificationAction;
import sailpoint.object.CertificationEntity;
import sailpoint.object.CertificationItem;
import sailpoint.object.PolicyViolation;
import sailpoint.object.Profile;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.web.certification.ProvisioningPlanEditDTO;
import sailpoint.web.messages.MessageKeys;

/**
 * @author peter.holcomb
 *
 */
public class CertificationActionDescriber {

    CertificationItem item;

    CertificationAction.Status status;
    
    SailPointContext context;

    public CertificationActionDescriber(CertificationAction.Status status, SailPointContext context) {
        this.status = status;
        this.context = context;
    }

    public CertificationActionDescriber(CertificationItem item, CertificationAction.Status status, SailPointContext context) {
        this(status, context);
        this.item = item;
    }

    public String getDefaultRemediationDescription(ProvisioningPlanEditDTO remediationDetails, CertificationEntity entity)
    throws GeneralException{

        String entityName = entity.getFullname();

        boolean editable = false;
        if (null != remediationDetails) {
            editable = remediationDetails.hasEditableEntitlements();
        }

        if (item != null){
            switch(item.getType()) {
            case AccountGroupMembership:
                entityName = item.getIdentity();
                String[] messageKeyArgs = new String[] {item.getExceptionEntitlements().getApplication(), entityName};
                return getRemediationDescription(editable, MessageKeys.REMED_MEMBERSHIP_DESC, 
                        MessageKeys.REMOVE_MEMBERSHIP_DESC, messageKeyArgs, messageKeyArgs);
            case Exception:
                String[] revokeAccountKeyArgs = new String[] {item.getExceptionEntitlements().getApplication(), entityName};
                String[] entMessageKeyArgs = new String[] {item.getExceptionEntitlements().getApplication(), entity.getSchemaObjectType(), entityName};
                return getRemediationDescription(editable, MessageKeys.REMED_ENTITLEMENT_DESC, 
                        MessageKeys.REMOVE_ENTITLEMENT_DESC, revokeAccountKeyArgs, entMessageKeyArgs);
            case Bundle:
                if (CertificationItem.SubType.AssignedRole.equals(item.getSubType()))
                    return getMessage(MessageKeys.REMOVE_ENTITY_DESC, entityName);
                else
                    return getMessage(MessageKeys.REMED_ENTITY_DESC, entityName);
            case BusinessRoleHierarchy:
                return  getMessage(MessageKeys.REMED_CHILD_ROLE_DESC,  item.getTargetName(), entityName);
            case BusinessRoleRequirement:
                return  getMessage(MessageKeys.REMED_REQUIREMENT_DESC,  item.getTargetName(), entityName);
            case BusinessRolePermit:
                return  getMessage(MessageKeys.REMED_PERMIT_DESC,  item.getTargetName(), entityName);
            case BusinessRoleGrantedCapability:
                return  getMessage(MessageKeys.REMED_GRANTED_CAP_DESC,  item.getTargetName(), entityName);
            case BusinessRoleGrantedScope:
                return  getMessage(MessageKeys.REMED_GRANTED_SCOPE_DESC,  item.getTargetName(), entityName);   
            case BusinessRoleProfile:
                // For a profile in a workitem UI, we need to use a friendly name,
                // since profiles don't have names in the UI.
                Profile profile = (Profile) item.getTargetObject(getContext());
                String app = getMessage(MessageKeys.UNKNOWN);
                if (null != profile) {
                    if (null != profile.getApplication()) {
                        app = profile.getApplication().getName();
                    }
                }
                return getMessage(MessageKeys.REMED_PROFILE_DESC, app, entityName);
            case PolicyViolation:
                String violationName = getViolatioName(item);
                if (violationName != null)
                    return getMessage(MessageKeys.REMED_VIOLATION_DESC, violationName, entityName);
            }
        }

        String key = (editable) ? MessageKeys.REMED_ENTITY_DESC
                : MessageKeys.REMOVE_ENTITY_DESC;
        return getMessage(key, entityName);
    }

    public String getBulkActionDescription(Certification.Type entityType, CertificationAction.Status status){

        // currently, we only need descriptions for remediations and delegations
        if (!CertificationAction.Status.Remediated.equals(status) &&
            !CertificationAction.Status.Delegated.equals(status) &&
            !CertificationAction.Status.RevokeAccount.equals(status))
            return null;

        String remediationMessage = null;
        String revokeAccountMessage = null;
        String delegateMessage = null;
        switch(entityType){
            case AccountGroupMembership: case AccountGroupPermissions:
                remediationMessage=getMessage(MessageKeys.PLZ_REMEDIATE_ACT_GRPS);
                revokeAccountMessage = getMessage(MessageKeys.PLZ_REVOKE_ACCOUNTS);
                delegateMessage=getMessage(MessageKeys.PLZ_CERTIFY_ACT_GRPS);
                break;
            case BusinessRoleMembership: case BusinessRoleComposition:
                remediationMessage=getMessage(MessageKeys.PLZ_REMEDIATE_ROLES);
                delegateMessage=getMessage(MessageKeys.PLZ_CERTIFY_ROLES);
                break;
            default:
                remediationMessage = getMessage(MessageKeys.PLZ_REMEDIATE_IDENTITIES);
                revokeAccountMessage = getMessage(MessageKeys.PLZ_REVOKE_ACCOUNTS);
                delegateMessage = getMessage(MessageKeys.PLZ_CERTIFY_IDENTITIES);
        }


       switch(status){
           case RevokeAccount:
               return revokeAccountMessage;
           case Remediated:
               return remediationMessage;
           case Delegated:
               return delegateMessage;
       }

        return null;
    }

    public String getDefaultDelegationDescription(CertificationEntity entity) throws GeneralException{
        String entityName = entity.getFullname();

        if (item != null){
            switch(item.getType()) {
            case AccountGroupMembership:
                entityName = item.getIdentity();
            case Account:
                return getMessage(MessageKeys.CERTIFY_ACCOUNT_DESC,
                        item.getExceptionEntitlements().getApplication(),
                        item.getExceptionEntitlements().getDisplayableName());
            case Exception:
                return getMessage(MessageKeys.CERTIFY_ENTITLEMENT_DESC,
                        item.getExceptionEntitlements().getApplication(), entityName);
            case Bundle:
                return  getMessage(MessageKeys.CERTIFY_ROLE_DESC, item.getBundle(), entityName);
            case BusinessRoleHierarchy:
                return getMessage(MessageKeys.CERTIFY_CHILD_ROLE_DESC, item.getTargetName(), entityName);
            case BusinessRoleRequirement:
                return getMessage(MessageKeys.CERTIFY_REQUIRED_ROLE_DESC, item.getTargetName(), entityName);
            case BusinessRolePermit:
                return getMessage(MessageKeys.CERTIFY_PERMITTED_ROLE_DESC, item.getTargetName(), entityName);
            case BusinessRoleGrantedCapability:
                return getMessage(MessageKeys.CERTIFY_ROLE_GRANT_CAPABILITY_DESC, item.getTargetName(), entityName);
            case BusinessRoleGrantedScope:
                return getMessage(MessageKeys.CERTIFY_ROLE_GRANT_SCOPE_DESC, item.getTargetName(), entityName);
            case BusinessRoleProfile:
                // For a profile in a workitem UI, we need to use a friendly name,
                // since profiles don't have names in the UI.
                Profile profile = (Profile) item.getTargetObject(getContext());
                String app = getMessage(MessageKeys.UNKNOWN);
                if (null != profile) {
                    if (null != profile.getApplication()) {
                        app = profile.getApplication().getName();
                    }
                }
                return getMessage(MessageKeys.CERTIFY_PROFILE_DESC, app, entityName);
            case PolicyViolation:
                String violationName = getViolatioName(item);
                if (violationName != null)
                    return getMessage(MessageKeys.CERTIFY_VIOLATION_DESC, violationName, entityName);
            }
        }
        return getMessage(MessageKeys.CERTIFY_ENTITY_DESC, entityName);
    }
    
    private String getRemediationDescription(boolean editable, String remediateDescKey, String removeDescKey, String[] messageKeyArgs, 
                                             String[] entMessageKeyArgs) {
        if (CertificationAction.Status.RevokeAccount.equals(this.status)) {
            return getMessage(MessageKeys.REMED_REVOKE_ACCT_DESC, (Object[])messageKeyArgs);
        }
        
        // If we're editing, use the more appropriate "remediate".
        String key = (editable) ? remediateDescKey : removeDescKey;
        return getMessage(key, (Object[])entMessageKeyArgs);
    }
    
    private String getViolatioName(CertificationItem item) throws GeneralException{
        BaseConstraint con = item.getPolicyViolation().getConstraint(getContext());
        if (null != con) {
            return con.getDisplayableName();
        }
        else {
            PolicyViolation pv = item.getPolicyViolation();
            if (pv != null)
                return pv.getDisplayableName();
        }

        return null;
    }

    public String getMessage(String key, Object... args) {
        Message msg = new Message(key, args);
        return msg.getLocalizedMessage();
    }
    
    public CertificationItem getItem() {
        return item;
    }

    public void setItem(CertificationItem item) {
        this.item = item;
    }

    public CertificationAction.Status getStatus() {
        return status;
    }

    public void setStatus(CertificationAction.Status status) {
        this.status = status;
    }

    public SailPointContext getContext() {
        return context;
    }

    public void setContext(SailPointContext context) {
        this.context = context;
    }
}
