/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.authorization;

import java.util.List;

import sailpoint.api.ObjectUtil;
import sailpoint.api.SailPointContext;
import sailpoint.object.Capability;
import sailpoint.object.Filter;
import sailpoint.object.IdentityRequest;
import sailpoint.object.QueryOptions;
import sailpoint.object.SPRight;
import sailpoint.object.WorkItem;
import sailpoint.object.WorkItemArchive;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.web.UserContext;
import sailpoint.web.messages.MessageKeys;

/**
 *  Authorizer for IdentityRequestListResource
 *  
 *  1. System Admin is authorized
 *  2. Any capability with MonitorIdentityEvents or FullAccessIdentityRequest SPRight is authorized
 *  
 *  When requestee param is left empty query will automatically adjust to get requester's items only.
 * 
 * @author patrick.jeong
 */
public class IdentityRequestAuthorizer implements Authorizer {

    private String _requester;
    private String _requestee;

    public IdentityRequestAuthorizer(String requester, String requestee) {
        _requester = requester;
        _requestee = requestee;
    }

    public void authorize(UserContext userContext) throws GeneralException {
        SailPointContext context = userContext.getContext();
        List<Capability> capabilities = userContext.getLoggedInUserCapabilities();
        
        if (Capability.hasSystemAdministrator(capabilities)) {
            return;
        }
        
        if (sailpoint.web.Authorizer.hasAccess(userContext.getLoggedInUserCapabilities(), userContext.getLoggedInUserRights(),  SPRight.ViewIdentity, SPRight.MonitorIdentityEvents)) {
            return;
        }
        
        // nothing to check will get default data
        if (_requester == null && _requestee == null) {
            return;
        }
        
        // now check the requester, requestee, if either one is not explicitly set they will automatically get set in the list resource.
        // bug #21567 - need to compare either the logged in user id or the name since there are cases where the _requester 
        // and _requestee are ids or names. 
        String loggedInUserId = userContext.getLoggedInUser().getId();
        String loggedInUserName = userContext.getLoggedInUserName();
        
        if (_requester != null && _requester.equals(loggedInUserId) || _requestee != null && _requestee.equals(loggedInUserId)
                || _requester != null && _requester.equals(loggedInUserName) || _requestee != null && _requestee.equals(loggedInUserName)) {
            return;
        }

        // bug #21567 - There's an edge case where if there is a requester but no requestee then assume the requestee is the
        // logged in user. The logged in user should be able to see their requests for that requester.
        if (_requester != null && _requestee == null) {
            return;
        }

        // check if logged in user owns any work items or work item archives
        QueryOptions wiaop = new QueryOptions();
        wiaop.add(ObjectUtil.getOwnerFilterForIdentity(userContext.getLoggedInUser()));
        if (context.countObjects(WorkItem.class, wiaop) > 0 ||
                context.countObjects(WorkItemArchive.class, wiaop) > 0) {
            return;
        }
        
        // check if logged in user is the requester of any work items
        wiaop = new QueryOptions();
        wiaop.add(Filter.eq("requesterId", loggedInUserId));
        if (context.countObjects(IdentityRequest.class, wiaop) > 0 ) {
            return;
        }
        
        throw new UnauthorizedAccessException(new Message(MessageKeys.UI_LCM_REQUEST_UNAUTHORIZED_ACCESS));
	}

}
