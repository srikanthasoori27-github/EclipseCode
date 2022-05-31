/* (c) Copyright 2017 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.authorization.identityrequest;

import sailpoint.authorization.Authorizer;
import sailpoint.authorization.UnauthorizedAccessException;
import sailpoint.object.Capability;
import sailpoint.object.Identity;
import sailpoint.object.IdentityRequest;
import sailpoint.object.SPRight;
import sailpoint.tools.GeneralException;
import sailpoint.web.UserContext;
import sailpoint.web.messages.MessageKeys;

import java.util.Collection;
import java.util.List;

/**
 * Authorizer for canceling identity requests
 */
public class IdentityRequestCancelAuthorizer implements Authorizer {

    private IdentityRequest identityRequest;

    public IdentityRequestCancelAuthorizer(IdentityRequest identityRequest) {
        this.identityRequest = identityRequest;
    }

    @Override
    public void authorize(UserContext userContext) throws GeneralException {
        if (!isAuthorized(this.identityRequest, userContext)) {
            throw new UnauthorizedAccessException(MessageKeys.UI_IDENTITY_REQUEST_UNAUTHORIZED_ACCESS_CANCEL);
        }
    }

    public static boolean isAuthorized(IdentityRequest identityRequest, UserContext userContext) throws GeneralException {
        if (identityRequest == null) {
            return true;
        }

        List<Capability> capabilities = userContext.getLoggedInUserCapabilities();
        Collection<String> rights = userContext.getLoggedInUserRights();

        // allow sysadmin access
        if (Capability.hasSystemAdministrator(capabilities)) {
            return true;
        }

        // allow access based on "full access" right
        if (sailpoint.web.Authorizer.hasAccess(capabilities, rights, SPRight.FullAccessIdentityRequest)) {
            return true;
        }

        // allow owner access
        Identity owner = identityRequest.getOwner();
        Identity loggedInUser = userContext.getLoggedInUser();
        if (owner != null && (owner.getId().equals(loggedInUser.getId()) || loggedInUser.isInWorkGroup(owner))) {
            return true;
        }

        // allow requester access
        String requesterId = identityRequest.getRequesterId();
        if (requesterId != null && requesterId.equals(loggedInUser.getId())) {
            return true;
        }

        return false;
    }
}
