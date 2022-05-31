/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web;

import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.IdentityService;
import sailpoint.api.Localizer;
import sailpoint.api.ObjectUtil;
import sailpoint.api.SailPointContext;
import sailpoint.object.Application;
import sailpoint.object.ApprovalItem.ProvisioningState;
import sailpoint.object.Bundle;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.IdentityRequest;
import sailpoint.object.IdentityRequestItem;
import sailpoint.object.Link;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.ProvisioningPlan.AttributeRequest;
import sailpoint.object.ProvisioningPlan.ObjectOperation;
import sailpoint.object.ProvisioningPlan.Operation;
import sailpoint.object.QueryOptions;
import sailpoint.object.WorkItem;
import sailpoint.service.identityrequest.IdentityRequestItemService;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.util.WebUtil;

/**
 *  IdentityRequestItem decorator class to add some fields for the UI
 * 
 * @author patrick.jeong
 *
 */
public class IdentityRequestItemUI  {
	private static Log log = LogFactory.getLog(IdentityRequestItemUI.class);
	private IdentityRequestItem requestItem;
	
	private boolean isGroupAttribute;
	private String entitlementInfo;
	private String entitlementIcon;
	private String compilationStatus;
    private String roleId;
    private String roleSearchName;
    private String assignmentId;

	private String applicationInfo;
	private SailPointContext context;
	
	private Locale locale;
	private TimeZone timeZone;
    private String linkDisplayName;
	
	static Map<String, String> operationKeyMap = new HashMap<String, String>();
	static {
	    for (Operation op : ProvisioningPlan.Operation.values()) {
	        operationKeyMap.put(op.toString(), op.getMessageKey());
	    }
	    for (ObjectOperation op : ObjectOperation.values()) {
	        operationKeyMap.put(op.toString(), op.getMessageKey());
	    }
	}
	/**
	 * @throws GeneralException 
	 * 
	 */
	public IdentityRequestItemUI(IdentityRequestItem item, 
	        SailPointContext context, 
	        Locale locale, 
	        TimeZone timeZone) throws GeneralException {
		requestItem = item;
		this.context = context;
		this.locale = locale;
		this.timeZone = timeZone;
		init();
	}
	
	private void init() throws GeneralException {
		// load role type and description
		String props = "type,id";
		
		if (requestItem == null) {
			return;
		}
		
		Localizer localizer = new Localizer(context);
		
		if (requestItem.getValue() != null) {
			String roleName = requestItem.getValue().toString();
			//roleSearchName = requestItem.getValue().toString();
			QueryOptions qo = new QueryOptions();
			qo.add(Filter.eq("name", roleName));
			// set the value to the role type
			Iterator<Object[]> it = context.search(Bundle.class, qo, props);
	
			if (it.hasNext()) {
				Object[] record = it.next();
				entitlementIcon =  (String)(record[0]);
				roleId = (String)(record[1]);
                String description = localizer.getLocalizedValue(roleId, Localizer.ATTR_DESCRIPTION, locale); 
                entitlementInfo = description; // load locale description
			}
		}
		
		// load application description
		if (requestItem.getApplication() != null) {
			String appName = requestItem.getApplication();
			QueryOptions appQuery = new QueryOptions();
			appQuery.add(Filter.eq("name", appName));
			
			Iterator<Object[]> itt = context.search(Application.class, appQuery, "id");
	
			if (itt.hasNext()) {
				Object[] record = itt.next();
	            applicationInfo = localizer.getLocalizedValue((String)(record[0]), Localizer.ATTR_DESCRIPTION, locale); 
			}
			
			if (requestItem.getName() != null) {
				isGroupAttribute = WebUtil.isGroupAttribute(appName, requestItem.getName());
			}
		}
		
		if (requestItem.getCompilationStatus() != null) {
			compilationStatus = requestItem.getCompilationStatus().toString();
		}
		else {
			compilationStatus = "Requested";
		}

        // load link Display Name
        // bug #18335 - show link display name in addition to numerical dbID
        IdentityRequest ir = requestItem.getIdentityRequest();
        String ga = requestItem.getApplication();
        IdentityService is = new IdentityService(context);
        String gi = requestItem.getInstance();
        String ni = requestItem.getNativeIdentity();
	
        // instance can be null here...
        if (ir != null && ga != null && is != null && ni != null) {
            String iid = ir.getTargetId();
            Identity reqid = null;
            Application app = null;
            if (iid != null) {
                try {
                    reqid = context.getObjectById(Identity.class, iid);
                    app =   context.getObjectByName(Application.class, ga);
                    if (reqid != null && app != null) {
                        Link link = is.getLink(reqid, app, gi, ni);
                        if (link != null && link.getDisplayName() != null) {
                            this.setLinkDisplayName("(" + link.getDisplayName() + ")");
                        }
                    }
                } catch (GeneralException ge) {
                    log.error(ge);
                }
            }
        }
        
        //assignmentId
        ProvisioningPlan plan = requestItem.getProvisioningPlan();
        if (plan != null) {
            for (ProvisioningPlan.AccountRequest areq : Util.safeIterable(plan.getAccountRequests())) {
                if (ProvisioningPlan.IIQ_APPLICATION_NAME.equalsIgnoreCase(areq.getApplicationName())) {
                    List<AttributeRequest> attreqs = areq.getAttributeRequests();
                    for (AttributeRequest req : Util.safeIterable(attreqs)) {
                        if (!Util.isEmpty(req.getAssignmentId())) {
                            assignmentId = req.getAssignmentId();
                            break;
                        }
                    }
                }
            }
        }

    } 
	
	public String getLinkDisplayName() {
            return linkDisplayName;
	}

	public void setLinkDisplayName(String ldn) {
            this.linkDisplayName = ldn;
	}
	
	// unique calls for this object
	public String getEntitlementInfo() {
		return entitlementInfo;
	}

	public void setEntitlementInfo(String entitlementInfo) {
		this.entitlementInfo = entitlementInfo;
	}
	
	//defect22074 holds the value for searching
    public String getRoleSearchName() {
        return roleSearchName;
    }

    public void setgetRoleSearchName(String roleSearchName) {
        this.roleSearchName = roleSearchName;
    }

	public String getEntitlementIcon() {
		return entitlementIcon;
	}

	public void setEntitlementIcon(String entitlementIcon) {
		this.entitlementIcon = entitlementIcon;
	}

	public String getApplicationInfo() {
		return applicationInfo;
	}

	public void setApplicationInfo(String applicationInfo) {
		this.applicationInfo = applicationInfo;
	}
	
	public IdentityRequest getIdentityRequest() {
		return requestItem.getIdentityRequest();
	}

	// pass through calls
	public String getOperation() {
	    String op = requestItem.getOperation();
	    if (operationKeyMap.containsKey(op)) {
	        return getMessage(operationKeyMap.get(op));
	    } else {
	        return op;
	    }

	}
	//defect 22074  it seemed to make sense to make this return the value and not the displayableValue
	public String getValue(){
	    String val = "";
        if ( requestItem != null ) {
            if ( ObjectUtil.isSecret(requestItem) ) {
                val = "****";
            }
            
            if (null == val || "" == val) {
                val = requestItem.getStringValue();
            }
        }
        
        return val;
	}
	
    public String getDisplayableValue() {
        return new IdentityRequestItemService(this.context, this.requestItem).getDisplayableValue();
    }

	public String getNativeIdentity() {
		return requestItem.getNativeIdentity();
	}
	
	public String getName() {
		return requestItem.getName();
	}
	
	public String getInstance() {
		return requestItem.getInstance();
	}
	
	public String getNameValue() {
		String nv = "";
		
		if (getValue() != null && getValue().length() > 0) {
			nv = getName() + " = " + getValue();
		}
		
		return nv;
	}
	
	public void setNameValue() {}
	
    public String getApplicationName() {
    	String application = requestItem.getApplication();
    	if (requestItem.isIIQ()) {
    		return getMessage(MessageKeys.REQUEST_BEAN_IIQ);
    	}
    	else if (application == null) {
    		return getMessage(MessageKeys.REQUEST_BEAN_MANUAL);
    	}
        return application;
    }
    
    public void setApplicationName(String name) {
    	return;
    }
    
	public String getApprovalState() {
		WorkItem.State state = requestItem.getApprovalState();
		String stateKey = null;
		if (state != null) {
		    stateKey = state.getMessageKey();
		} else if (state == null && requestItem.getIdentityRequest().getEndDate() == null) {
		    stateKey = MessageKeys.WORK_ITEM_STATE_PENDING;
		} else {
		    // the request has completed but the state didn't change
	        // assume that the approvalScheme is none
		}
		
		if (stateKey != null) {
		    return getMessage(stateKey);
		} else {
		    return "";
		}
	}
	
	public String getProvisioningState() {
	    ProvisioningState state = null;

	    // we don't want to show pending when the approval is rejected
	    if (requestItem.getApprovalState() != WorkItem.State.Rejected) {
	        state = requestItem.getProvisioningState();
	        if (state == null && requestItem.getIdentityRequest().getEndDate() == null) {
	            state = ProvisioningState.Pending;
	        }
	    }
	    
	    if (state != null) {
	        return getMessage(state.getMessageKey());
	    } else {
	        return "";
	    }		
	}
	
	public boolean isExpansion() {
		return requestItem.isExpansion();
	}
	
	public boolean isGroupAttribute() {
		return isGroupAttribute;
	}
	
	public String getRequesterComments() {
		return requestItem.getRequesterComments();
	}
	
	public String getCompilationStatus() {
		return compilationStatus;
	}

	public void setCompilationStatus(String compilationStatus) {
		this.compilationStatus = compilationStatus;
	}
	
	public int getRetries() {
		return requestItem.getRetries();
	}

	public String getProvisioningRequestId() {
		return requestItem.getProvisioningRequestId();
	}
	
	public String getRoleId() {
		return roleId;
	}

	public void setRoleId(String roleId) {
		this.roleId = roleId;
	}
	
	public Date getStartDate() {
		return requestItem.getStartDate();
	}
	
	public Date getEndDate() {
		return requestItem.getEndDate();
	}
	
    private String getMessage(String key, Object... args) {
        Message msg = new Message(key, args);
        return msg.getLocalizedMessage(this.locale, this.timeZone);
    }

    public String getRequestItemId() {
        return requestItem.getId();
    }

    public String getRequestId() {
        return requestItem.getIdentityRequest().getId();
    }

    public String getTargetId() {
        return requestItem.getIdentityRequest().getTargetId();
    }

    public Object getAssignmentId() {
        return assignmentId;
    }

}
