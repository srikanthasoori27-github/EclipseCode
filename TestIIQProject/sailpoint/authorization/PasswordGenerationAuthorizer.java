package sailpoint.authorization;

import sailpoint.object.Configuration;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.web.UserContext;
import sailpoint.web.messages.MessageKeys;

/**
 * Authorizer to check if password generation is allowed
 */
public class PasswordGenerationAuthorizer implements Authorizer {

    public void authorize(UserContext userContext) throws GeneralException {
        Configuration configuration = userContext.getContext().getConfiguration();
        if (!configuration.getBoolean(Configuration.LCM_ALLOW_GENERATE_PASSWORD_DELEGATION)) {
            throw new UnauthorizedAccessException(new Message(MessageKeys.LCM_MANAGE_PASSWORDS_GENERATION_DISABLED));
        }
    }
}
