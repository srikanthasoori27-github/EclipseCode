
/**
 * Package decl.
 */
package sailpoint.authorization;

/**
 * Imports.
 */
import sailpoint.Version;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.web.UserContext;
import sailpoint.web.messages.MessageKeys;

/**
 * This authorizer checks to make sure that LCM is enabled.
 *
 * @author Dustin Dobervich <dustin.dobervich@sailpoint.com>
 */
public class LcmEnabledAuthorizer implements Authorizer
{
    /**
     * {@inheritDoc}
     */
    public void authorize(UserContext userContext) throws GeneralException
    {
        if (!Version.isLCMEnabled()) {
            throw new UnauthorizedAccessException(new Message(MessageKeys.UI_LCM_UNAUTHORIZED_ACCESS));
        }
    }
}
