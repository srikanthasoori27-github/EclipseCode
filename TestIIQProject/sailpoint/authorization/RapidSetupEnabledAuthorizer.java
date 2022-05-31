
/**
 * (c) Copyright 2020 SailPoint Technologies, Inc., All Rights Reserved.
 */
package sailpoint.authorization;

import sailpoint.object.RapidSetupConfigUtils;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.web.UserContext;
import sailpoint.web.messages.MessageKeys;

/**
 * This authorizer checks to make sure that RapidSetup is enabled.
 *
 */
public class RapidSetupEnabledAuthorizer implements Authorizer
{
    public void authorize(UserContext userContext) throws GeneralException
    {
        if (!RapidSetupConfigUtils.isEnabled()) {
            throw new UnauthorizedAccessException(new Message(MessageKeys.UI_RS_UNAUTHORIZED_ACCESS));
        }
    }
}
