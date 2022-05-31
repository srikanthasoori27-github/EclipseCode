/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.authorization;

import sailpoint.object.Identity;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.web.UserContext;
import sailpoint.web.messages.MessageKeys;

/**
 * Ensures that the user has access to the specified {@link sailpoint.object.WebResource}
 *
 * @author tpox.mozambo
 */
public class WebResourceAuthorizer implements Authorizer {

    private String[] urls;

    /**
     * Constructor
     * @param urls URLs to authorize. If any pass, user is authorized.
     */
    public WebResourceAuthorizer(String... urls) {
        this.urls = urls;
    }

    private sailpoint.web.Authorizer getWebAuthorizer() {
        return sailpoint.web.Authorizer.getInstance();
    }

    public void authorize(UserContext userContext) throws GeneralException {
        Identity identity = userContext.getLoggedInUser();
        boolean authorized = false;

        for (String url : this.urls) {
            if (getWebAuthorizer().isAuthorized(url, identity)) {
                authorized = true;
                break;
            }
        }

        if (!authorized) {
            throw new UnauthorizedAccessException(new Message(MessageKeys.UI_WEB_RESOURCE_UNAUTHORIZED_ACCESS));
        }
    }
}
