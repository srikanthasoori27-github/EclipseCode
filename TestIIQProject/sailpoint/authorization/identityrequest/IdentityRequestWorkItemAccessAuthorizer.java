/*
 * (c) Copyright 2020 SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.authorization.identityrequest;

import java.util.Set;
import java.util.stream.Collectors;

import sailpoint.authorization.Authorizer;
import sailpoint.authorization.UnauthorizedAccessException;
import sailpoint.object.IdentityRequest;
import sailpoint.service.identityrequest.IdentityRequestService;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.UserContext;
import sailpoint.web.messages.MessageKeys;

/**
 * Authorize work item id against the identity request.
 */
public class IdentityRequestWorkItemAccessAuthorizer implements Authorizer {

    private String workItemId;
    // actually the identity request name property
    private String identityRequestId;

    public IdentityRequestWorkItemAccessAuthorizer(String workItemId, String identityRequestId) {
        this.workItemId = workItemId;
        this.identityRequestId = identityRequestId;
    }

    @Override
    public void authorize(UserContext userContext) throws GeneralException  {
        if (Util.isNullOrEmpty(this.workItemId) || Util.isNullOrEmpty(this.identityRequestId)) {
            throw new UnauthorizedAccessException(new Message(MessageKeys.UI_UNAUTHORIZED_IDENTITY_REQUEST_WORK_ITEM));
        }

        IdentityRequest identityRequest = userContext.getContext().getObjectByName(IdentityRequest.class, this.identityRequestId);

        if (identityRequest == null) {
            throw new UnauthorizedAccessException(new Message(MessageKeys.UI_UNAUTHORIZED_IDENTITY_REQUEST_WORK_ITEM));
        }

        IdentityRequestService service = new IdentityRequestService(userContext.getContext(), identityRequest);

        Set<String> workItemIdSet = service.getApprovalSummaryList().stream()
                .map(x -> x.getWorkItemId()).collect(Collectors.toSet());

        if (!workItemIdSet.contains(this.workItemId)) {
            throw new UnauthorizedAccessException(new Message(MessageKeys.UI_UNAUTHORIZED_IDENTITY_REQUEST_WORK_ITEM));
        }
    }
}
