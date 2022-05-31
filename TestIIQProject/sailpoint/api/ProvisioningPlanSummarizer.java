package sailpoint.api;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import sailpoint.object.ProvisioningPlan;
import sailpoint.object.ProvisioningPlan.AccountRequest;
import sailpoint.object.ProvisioningPlan.AttributeRequest;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Reflection;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.util.WebUtil;

/**
 * Generates a text summary of a provisioning plan. 
 * 
 * TODO: Currently, this duplicates the output of provisioningPlanSummary.xhtml.
 *       Replace provisioningPlanSummary.xhtml with a call to this summarizer.
 *  
 * @author Jeff Upton
 */
public class ProvisioningPlanSummarizer 
{
	ProvisioningPlan _plan;
	Locale _locale;
	TimeZone _timeZone;
	
	/**
	 * Constructs a new summarizer for the specified plan.
	 * @param plan The plan to summarize.
	 */
	public ProvisioningPlanSummarizer(ProvisioningPlan plan, Locale locale, TimeZone timeZone)
	{
		required(plan);
		required(locale);
		required(timeZone);
		
		_plan = plan;
		_locale = locale;
		_timeZone = timeZone;
	}
	
	/**
	 * Gets the text summary of this provisioning plan.
	 * @return The summary.
	 * @throws GeneralException
	 */
	public String getSummary() 
        throws GeneralException 
    {
        StringBuilder sb = new StringBuilder();
        
        sb.append(getAccountRequestSummaries(_plan));
        
        if (!Util.isEmpty(_plan.getObjectRequests())) {
        	sb.append(getObjectRequestSummary(_plan.getObjectRequests().get(0)));
        }

        if (requiresEllipsis()) {
            sb.append("...");
        }

        return sb.toString();
    }

    private String getAccountRequestSummary(ProvisioningPlan.AccountRequest accountRequest)
    {
        StringBuilder sb = new StringBuilder();

        if (accountRequest.getOperation() == AccountRequest.Operation.Modify) {
        	if (!Util.isEmpty(accountRequest.getAttributeRequests())) {
        		sb.append(getAttributeRequestSummaries(accountRequest));
        	}
        	
        	if (!Util.isEmpty(accountRequest.getPermissionRequests())) {
        		sb.append(getPermissionRequestSummary(accountRequest, accountRequest.getPermissionRequests().get(0)));
        	}
        } else {
            sb.append(accountRequest.getOperation());
            sb.append(" ");
            sb.append(accountRequest.getNativeIdentity());
        }
    
        return sb.toString();
    }
    
    private String getAccountRequestSummaries(ProvisioningPlan plan) {
        
        StringBuilder sb = new StringBuilder();
        List<AccountRequest> accountRequests = plan.getAccountRequests();
        if (accountRequests != null) {
            Iterator<AccountRequest> iterator = accountRequests.iterator();
            while (iterator.hasNext()) {
                AccountRequest accountRequest = iterator.next();
                sb.append(getAccountRequestSummary(accountRequest));
                if (iterator.hasNext()) {
                    sb.append(". ");
                }
            }
        }
        return sb.toString();
    }
    
    private String getAttributeRequestSummaries(ProvisioningPlan.AccountRequest accountRequest) {
        
        StringBuilder sb = new StringBuilder();

        List<AttributeRequest> attributeRequests = accountRequest.getAttributeRequests();
        if (attributeRequests != null) {
            Iterator<AttributeRequest> iterator = attributeRequests.iterator();
            while (iterator.hasNext()) {
                AttributeRequest attrRequest = iterator.next();
                sb.append(getAttributeRequestSummary(accountRequest, attrRequest));
                if (iterator.hasNext()) {
                    sb.append(". ");
                }
            }
        }
        return sb.toString();
    }
    
    private String getAttributeRequestSummary(ProvisioningPlan.AccountRequest accountRequest, ProvisioningPlan.AttributeRequest attrRequest)
    {	
        String messageKey = "";
        
        if (attrRequest.getOperation() == ProvisioningPlan.Operation.Add) {                 
            messageKey = MessageKeys.ATTRIBUTE_REQUEST_SUMMARY_ADD;
        } else if (attrRequest.getOperation() == ProvisioningPlan.Operation.Remove) {
            messageKey = MessageKeys.ATTRIBUTE_REQUEST_SUMMARY_REMOVE;
        } else if (attrRequest.getOperation() == ProvisioningPlan.Operation.Set) {
            messageKey = MessageKeys.ATTRIBUTE_REQUEST_SUMMARY_SET;
        }

        return getMessage(
                messageKey,
                WebUtil.commify(attrRequest.getValue()),
                attrRequest.getName(),
                accountRequest.getNativeIdentity());
    }
    
    private String getPermissionRequestSummary(ProvisioningPlan.AbstractRequest abstractRequest, ProvisioningPlan.PermissionRequest permRequest) 
    {	
        String messageKey = "";
        
        if (permRequest.getOperation() == ProvisioningPlan.Operation.Add) {                 
            messageKey = MessageKeys.PERMISSION_REQUEST_SUMMARY_ADD;
        } else if (permRequest.getOperation() == ProvisioningPlan.Operation.Remove) {
            messageKey = MessageKeys.PERMISSION_REQUEST_SUMMARY_REMOVE;
        } else if (permRequest.getOperation() == ProvisioningPlan.Operation.Set) {
            messageKey = MessageKeys.PERMISSION_REQUEST_SUMMARY_SET;
        }

        return getMessage(
                messageKey,
                WebUtil.commify(permRequest.getRightsList()),
                permRequest.getTarget(),
                abstractRequest.getType(),
                abstractRequest.getNativeIdentity());
    }

    private String getObjectRequestSummary(ProvisioningPlan.ObjectRequest objectRequest)
        throws GeneralException 
    {
        if (!Util.isEmpty(objectRequest.getAttributeRequests())) {
            ProvisioningPlan.AttributeRequest attrRequest = objectRequest.getAttributeRequests().get(0);

            if (attrRequest != null) {
                if (ProvisioningPlan.ATT_IIQ_ROLE_PROFILES.equals(attrRequest.getName())) {
                    return getMessage(
                            MessageKeys.REMED_REMOVE_PROFILE_FROM_ROLE,
                            attrRequest.getDisplayValue(),
                            objectRequest.getNativeIdentity());
                } else if (ProvisioningPlan.ATT_IIQ_ROLE_CHILD.equals(attrRequest.getName())) {
                    return getMessage(MessageKeys.REMED_REMOVE_CHILD_FROM_ROLE,
                            getName(attrRequest.getValue()),
                            objectRequest.getNativeIdentity());
                } else if (ProvisioningPlan.ATT_IIQ_ROLE_PERMIT.equals(attrRequest.getName())) {
                    return getMessage(MessageKeys.REMED_REMOVE_PERMITTED_ROLE,
                            getName(attrRequest.getValue()),
                            objectRequest.getNativeIdentity());
                } else if (ProvisioningPlan.ATT_IIQ_ROLE_REQUIREMENT.equals(attrRequest.getName())) {
                    return getMessage(MessageKeys.REMED_REMOVE_REQUIRED_ROLE,
                            getName(attrRequest.getValue()),
                            objectRequest.getNativeIdentity());
                } else if (ProvisioningPlan.ATT_IIQ_ROLE_GRANTED_SCOPE.equals(attrRequest.getName())) {
                    return getMessage(
                            MessageKeys.REMED_REMOVE_GRANTED_SCOPE_FROM_ROLE,
                            getName(attrRequest.getValue()),
                            objectRequest.getNativeIdentity());
                } else if (ProvisioningPlan.ATT_IIQ_ROLE_GRANTED_CAPABILITY.equals(attrRequest.getName())) {
                    return getMessage(MessageKeys.REMED_REMOVE_GRANTED_CAPABILITY,
                            getName(attrRequest.getValue()));
                }
                else {
                    // fallback summary for attribute requests.
                    String value = !Util.isNullOrEmpty(attrRequest.getDisplayValue()) ? attrRequest.getDisplayValue() :
                            (String)attrRequest.getValue();
                    return getMessage(MessageKeys.REMOVE_ENTITLEMENT_DEFAULT_DESC, 
                            value, 
                            objectRequest.getNativeIdentity());
                }
            }
        } else if (!Util.isEmpty(objectRequest.getPermissionRequests())) {
            /* Account Group Membership certs will have a PermissionRequest living on the ObjectRequest
               (see US2487) so we need to get a summary from those if they exist */
            ProvisioningPlan.PermissionRequest permissionRequest = objectRequest.getPermissionRequests().get(0);
            return getPermissionRequestSummary(objectRequest, permissionRequest);
              
        }
        
        return "";
    }
    
    private String getMessage(String key, Object... args)
    {
    	Message msg = new Message(key, args);
        return msg.getLocalizedMessage(_locale, _timeZone);
    }
    
    private String getName(Object o) 
        throws GeneralException 
    {	
    	return (String)Reflection.getProperty(o, "name");
    }
    
    private boolean requiresEllipsis()
    {	
    	return moreThanOne(_plan.getAccountRequests()) ||
    	       (!Util.isEmpty(_plan.getAccountRequests()) && moreThanOne(_plan.getAccountRequests().get(0).getAttributeRequests())) ||
    	       (!Util.isEmpty(_plan.getAccountRequests()) && moreThanOne(_plan.getAccountRequests().get(0).getPermissionRequests())) ||
    	       moreThanOne(_plan.getObjectRequests()) ||
    	       (!Util.isEmpty(_plan.getObjectRequests()) && moreThanOne(_plan.getObjectRequests().get(0).getAttributeRequests()));
    }
    
    private boolean moreThanOne(Collection<?> c) 
    {
    	return Util.size(c) > 1;
    }
    
    private void required(Object o)
    {
    	if (o == null) {
    		throw new IllegalArgumentException("Value is required");
    	}
    }
}
