/* (c) Copyright 2015 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.authorization;

import java.util.List;

import sailpoint.object.Capability;
import sailpoint.object.QuickLink;
import sailpoint.service.quicklink.QuickLinkLauncher;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.web.UserContext;
import sailpoint.web.messages.MessageKeys;

/**
 * An authorizer that checks whether the logged in user is allowed to launch a quick link (either
 * for self or for others).
 */
public class QuickLinkLaunchAuthorizer implements Authorizer {

    private QuickLink quickLink;
    private boolean selfService;


    /**
     * Constructor.
     *
     * @param  quickLink  The quick link to authorize.
     * @param  selfService  Whether this is a self-service request or not.
     */
    public QuickLinkLaunchAuthorizer(QuickLink quickLink, boolean selfService) {
        this.quickLink = quickLink;
        this.selfService = selfService;
    }

    /* (non-Javadoc)
     * @see sailpoint.authorization.Authorizer#authorize(sailpoint.web.UserContext)
     */
    @Override
    public void authorize(UserContext userContext) throws GeneralException {
        // He's sys admin ... he can do whatever the heck he wants...
        List<Capability> capabilities = userContext.getLoggedInUserCapabilities();
        if (Capability.hasSystemAdministrator(capabilities)) {
            return;
        }

        QuickLinkLauncher launcher =
            new QuickLinkLauncher(userContext.getContext(), userContext.getLoggedInUser());
        if (!launcher.validateQuickLink(this.quickLink, this.selfService, userContext.getLoggedInUserDynamicScopeNames())) {
            throw new UnauthorizedAccessException(new Message(MessageKeys.UI_QUICK_LINK_LAUNCH_UNAUTHORIZED_ACCESS, this.quickLink.getName()));
        }
    }
}
