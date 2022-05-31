/*
 * (c) Copyright 2017. SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.authorization.identityrequest;

import sailpoint.authorization.Authorizer;
import sailpoint.authorization.UnauthorizedAccessException;
import sailpoint.object.IdentityRequest;
import sailpoint.tools.GeneralException;
import sailpoint.web.IdentityRequestAuthorizer;
import sailpoint.web.UserContext;
import sailpoint.web.messages.MessageKeys;

/**
 * Authorizer for allowing access to identity request objects
 */
public class IdentityRequestAccessAuthorizer implements Authorizer {
    private IdentityRequest identityRequest;

    /**
     * Constructor
     *
     * @param identityRequest
     */
    public IdentityRequestAccessAuthorizer(IdentityRequest identityRequest) {
        this.identityRequest = identityRequest;
    }

    /**
     * Authorize for access to identity request
     *
     * @param userContext The UserContext to authorize.
     *
     * @throws GeneralException
     */
    @Override
    public void authorize(UserContext userContext) throws GeneralException {
        if (!IdentityRequestAuthorizer.isAuthorized(this.identityRequest, userContext)) {
            throw new UnauthorizedAccessException(MessageKeys.UI_IDENTITY_REQUEST_UNAUTHORIZED_ACCESS);
        }
    }
}
