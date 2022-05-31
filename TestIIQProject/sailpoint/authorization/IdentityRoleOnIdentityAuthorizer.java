/* (c) Copyright 2016. SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.authorization;

import sailpoint.object.Bundle;
import sailpoint.object.Identity;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.web.UserContext;
import sailpoint.web.messages.MessageKeys;


/**
 * An authorizer that throws if the identity does not have a given role assigned.
 */
public class IdentityRoleOnIdentityAuthorizer implements Authorizer {

    private Identity identity;
    private String roleId;


    /**
     * Constructor.
     *
     * @param identity  The identity on which to look for the role.
     * @param roleId  The ID of the bundle/role.
     */
    public IdentityRoleOnIdentityAuthorizer(Identity identity, String roleId) {
        this.identity = identity;
        this.roleId = roleId;
    }

    @Override
    public void authorize(UserContext userContext) throws GeneralException {
        if (this.identity == null || this.roleId == null) {
            throw new UnauthorizedAccessException(new Message(MessageKeys.UI_IDENTITY_ROLE_UNAUTHORIZED_ACCESS));
        }

        Bundle role = userContext.getContext().getObjectById(Bundle.class, this.roleId);
        if (null == role) {
            throw new UnauthorizedAccessException(new Message(MessageKeys.UI_IDENTITY_ROLE_UNAUTHORIZED_ACCESS));
        }

        if (!identity.hasRole(role, true)) {
            throw new UnauthorizedAccessException(new Message(MessageKeys.UI_IDENTITY_ROLE_UNAUTHORIZED_ACCESS));
        }
    }
}
