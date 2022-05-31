/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.authorization;

import java.util.List;

import sailpoint.api.SailPointContext;
import sailpoint.object.Capability;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.QueryOptions;
import sailpoint.object.QuickLink;
import sailpoint.service.LCMConfigService;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.UserContext;
import sailpoint.web.messages.MessageKeys;

/**
 * Ensures that the user is able to make an LCM request for the specified user.
 * 
 * @author jeff.upton
 */
public class LcmRequestAuthorizer implements Authorizer {
	
	private Identity _requestedIdentity;

	/**
	 * member variable that holds the action the user is attempting.
	 * This should be the same as a quicklink action.
	 */
	private String _action;

    /**
     * Name of the quicklink calling into RequestAccess
     */
    private String _quickLinkName;

	public LcmRequestAuthorizer(Identity requestedIdentity) {
		_requestedIdentity = requestedIdentity;
	}

	public void authorize(UserContext userContext) throws GeneralException {
        ensureLcmEnabled(userContext);

	    SailPointContext context = userContext.getContext();
		
        Identity requestingIdentity = userContext.getLoggedInUser();
        
        List<Capability> capabilities = userContext.getLoggedInUserCapabilities();        
        if (Capability.hasSystemAdministrator(capabilities)) {
            return;
        }

        /* If we have a quicklink name and an action make sure that they agree */
        if(!Util.isNullOrEmpty(_quickLinkName) && !Util.isNullOrEmpty(_action)) {
            QuickLink ql = context.getObjectByName(QuickLink.class, _quickLinkName);
            if(ql != null && !_action.equals(ql.getAction())) {
                throw new UnauthorizedAccessException(new Message(MessageKeys.UI_QUICKLINK_ACTION_UNAUTHORIZED_ACCESS, _quickLinkName, _action));
            }
        }

        LCMConfigService lcmConfigService = new LCMConfigService(context, userContext.getLocale(), userContext.getUserTimeZone());
        List<String> dynamicScopeNames = userContext.getLoggedInUserDynamicScopeNames();

        if (_requestedIdentity.getId().equals(requestingIdentity.getId())) {
            // If this is a self-service request, check that it's allowed
            boolean isSelfServiceEnabled = lcmConfigService.isSelfServiceEnabled(requestingIdentity, dynamicScopeNames, _quickLinkName, _action);
            if (isSelfServiceEnabled) {
                return;
            }
            throw new UnauthorizedAccessException(new Message(MessageKeys.UI_LCM_SELF_REQUEST_UNAUTHORIZED_ACCESS));
        }
        QueryOptions queryOptions = lcmConfigService.getRequestableIdentityOptions(requestingIdentity, dynamicScopeNames, _quickLinkName, _action);
        if (null != queryOptions) {
        	queryOptions.add(Filter.eq("id", _requestedIdentity.getId()));
            if (context.countObjects(Identity.class, queryOptions) > 0) {
            	return;
            }
        }
        
        throw new UnauthorizedAccessException(new Message(MessageKeys.UI_LCM_REQUEST_UNAUTHORIZED_ACCESS));
	}

	public LcmRequestAuthorizer setAction(String action) {
		_action = action;
		return this;
	}

    public LcmRequestAuthorizer setQuickLinkName(String name) {
        _quickLinkName = name;
        return this;
    }

    private void ensureLcmEnabled(UserContext userContext) throws GeneralException {
        new LcmEnabledAuthorizer().authorize(userContext);
    }

}
