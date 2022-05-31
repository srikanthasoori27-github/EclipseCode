/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.authorization;

import sailpoint.object.Capability;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.web.UserContext;
import sailpoint.web.messages.MessageKeys;

/**
 * Ensures that the user has at least one of the specified rights.
 * If the list of rights is empty, authorization is successful.
 *
 * @author jeff.upton
 */
public class RightAuthorizer implements Authorizer {

    private String[] _rights;

    public RightAuthorizer(String... rights) {
        _rights = rights;
    }

    /**
     * Authorize the user context against a list of expected rights
     * @param userContext UserContext
     * @param rights List of rights to check
     * @return True if authorized, otherwise false.
     */
    public static boolean isAuthorized(UserContext userContext, String... rights) {
        if (sailpoint.web.Authorizer.hasAccess(userContext.getLoggedInUserCapabilities(),
                userContext.getLoggedInUserRights(), rights)) {
            return true;
        }

        if (Capability.hasSystemAdministrator(userContext.getLoggedInUserCapabilities())) {
            return true;
        }

        return false;
    }

    @Override
    public void authorize(UserContext userContext) throws GeneralException {
        if (!isAuthorized(userContext, _rights)) {
            throw new UnauthorizedAccessException(new Message(MessageKeys.UI_WORK_ITEM_UNAUTHORIZED_ACCESS));
        }
    }

    protected String[] getRequiredRights() {
        return _rights;
    }

}
