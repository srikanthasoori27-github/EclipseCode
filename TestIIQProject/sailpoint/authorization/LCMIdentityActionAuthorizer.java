package sailpoint.authorization;

import sailpoint.api.SailPointContext;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.QueryOptions;
import sailpoint.service.LCMConfigService;
import sailpoint.service.quicklink.QuickLinkOptionsConfigService;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.web.UserContext;
import sailpoint.web.messages.MessageKeys;

import java.util.Arrays;

/**
 * LCMIdentityActionAuthorizer ensures that the current logged in user can perform at least one of the provided actions
 * on an identity.
 */
public class LCMIdentityActionAuthorizer implements Authorizer {

    private final String requesteeId;
    private final String[] actions;

    /**
     * Construct an LCMIdentityActionAuthorizer that will validate the current user's authorization
     * to perform at least one of the provided actions with the provided user
     *
     * @param requesteeId The ID of the Identity being requested for
     * @param actions Array of possible actions
     */
    public LCMIdentityActionAuthorizer(String requesteeId, String[] actions) {
        this.requesteeId = requesteeId;
        this.actions = actions;
    }

    /**
     * The logged in user is authorized if able to perform any of the actions on the requestee
     * @param userContext The UserContext to authorize.
     * @throws GeneralException If the user is not authorized
     */
    @Override
    public void authorize(UserContext userContext) throws GeneralException {
        SailPointContext context = userContext.getContext();
        LCMConfigService lcmConfigService = new LCMConfigService(context, userContext.getLocale(), userContext.getUserTimeZone());

        if(actions == null) {
            throw new GeneralException("No actions specified.");
        }

        /* Self service and requests for other have to be handled differently. */
        Identity requestor = userContext.getLoggedInUser();
        if(requestor.getId().equals(requesteeId)) {
            QuickLinkOptionsConfigService quickLinkOptionsService = new QuickLinkOptionsConfigService(userContext.getContext(), userContext.getLocale(), userContext.getUserTimeZone());
            for (String action : actions) {
                boolean canSelfServe = quickLinkOptionsService.canRequestForSelf(requestor, null, action);
                if (canSelfServe) {
                    return;
                }
            }
        } else {
            for (String action : actions) {
                QueryOptions requesteeOptions = lcmConfigService.getRequestableIdentityOptions(
                        userContext.getLoggedInUser(), userContext.getLoggedInUserDynamicScopeNames(), null, action);
                if (requesteeOptions != null) {
                    requesteeOptions.add(Filter.eq("id", requesteeId));
                    int count = context.countObjects(Identity.class, requesteeOptions);
                    if (count > 0) {
                        return;
                    }
                }
            }
        }

        throw new UnauthorizedAccessException(new Message(MessageKeys.UI_LCM_IDENTITY_ACTION_UNAUTHORIZED_ACCESS, Arrays.asList(actions).toString(), requesteeId));
    }
}
