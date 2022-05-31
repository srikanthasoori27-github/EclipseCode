/*
 * (c) Copyright 2020 SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.authorization.identityrequest;

import sailpoint.authorization.Authorizer;
import sailpoint.authorization.UnauthorizedAccessException;
import sailpoint.object.IdentityRequestItem;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.UserContext;
import sailpoint.web.messages.MessageKeys;

public class IdentityRequestItemAccessAuthorizer implements Authorizer {
    private String itemId;
    // actually the identity request name
    private String identityRequestId;

    public IdentityRequestItemAccessAuthorizer(String itemId, String identityRequestId) {
        this.itemId = itemId;
        this.identityRequestId = identityRequestId;
    }

    /**
     * Authorize itemId against the identity request. Make sure it is actually one of the request items.
     * @param userContext The UserContext to authorize.
     *
     * @throws GeneralException
     */
    @Override
    public void authorize(UserContext userContext) throws GeneralException {
        if (Util.isNullOrEmpty(this.itemId) || Util.isNullOrEmpty(this.identityRequestId)) {
            throw new UnauthorizedAccessException(new Message(MessageKeys.UI_UNAUTHORIZED_IDENTITY_REQUEST_ITEM));
        }

        IdentityRequestItem item = userContext.getContext().getObjectById(IdentityRequestItem.class, itemId);

        if (item == null) {
            throw new UnauthorizedAccessException(new Message(MessageKeys.UI_UNAUTHORIZED_IDENTITY_REQUEST_ITEM));
        }

        // Check if the request name is the same.
        if (!item.getIdentityRequest().getName().equals(identityRequestId)) {
            throw new UnauthorizedAccessException(new Message(MessageKeys.UI_UNAUTHORIZED_IDENTITY_REQUEST_ITEM));
        }
    }
}
