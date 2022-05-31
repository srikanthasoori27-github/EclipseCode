/* (c) Copyright 2016 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.authorization;

import java.util.List;

import sailpoint.object.Capability;
import sailpoint.object.Identity;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.web.UserContext;
import sailpoint.web.messages.MessageKeys;

/**
 * Ensures that the user has at least one of the specified rights.
 * Calls {@link RightAuthorizer} to determine if the userContext
 * has the required rights. Then as a 'second round' of checking
 * ensures the current loggedInUser still has the requested rights.
 * This prevents long term Bearer tokens from obtaining rights which
 * may have been recently revoked on the proxy user.
 */
public class OAuthRightAuthorizer extends RightAuthorizer {

    public OAuthRightAuthorizer(String... rights) {
        super(rights);
    }
    
    @Override
    public void authorize(UserContext userContext) throws GeneralException {
        super.authorize(userContext);
        
        // user or requested Token contains the rights needed, double check
        // rights haven't been removed from User since Token was issued
        Identity loggedInUser = userContext.getLoggedInUser();
        
        if (loggedInUser != null) {
            List<Capability> caps = loggedInUser.getCapabilityManager().getEffectiveCapabilities();
            if (sailpoint.web.Authorizer.hasAccess(caps,
                    loggedInUser.getCapabilityManager().getEffectiveFlattenedRights(), getRequiredRights())) {
                return;
            }
            
            if (Capability.hasSystemAdministrator(caps)) {
                return;
            }
        }
        
        throw new UnauthorizedAccessException(new Message(MessageKeys.UI_WEB_RESOURCE_UNAUTHORIZED_ACCESS));
    }
}
