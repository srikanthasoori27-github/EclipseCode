
/**
 * Package decl.
 */
package sailpoint.authorization;

/**
 * Imports.
 */
import sailpoint.Version;
import sailpoint.object.QuickLink;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.web.UserContext;
import sailpoint.web.messages.MessageKeys;

/**
 * This authorizer checks to make sure that PAM is enabled.
 *
 * @author Peter Holcomb <peter.holcomb@sailpoint.com>
 */
public class PamAuthorizer extends QuickLinkLaunchAuthorizer
{
    /**
     * Constructor.
     *
     * @param  quickLink  The quick link to authorize.
     * @param  selfService  Whether this is a self-service request or not.
     */
    public PamAuthorizer(QuickLink quickLink, boolean selfService) {
        super(quickLink, selfService);
    }
    /**
     * {@inheritDoc}
     */
    public void authorize(UserContext userContext) throws GeneralException
    {
        if (!Version.isPAMEnabled()) {
            throw new UnauthorizedAccessException(new Message(MessageKeys.ERR_PAM_NOT_ENABLED));
        }
        super.authorize(userContext);
    }
}
