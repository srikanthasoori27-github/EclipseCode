package sailpoint.authorization;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import sailpoint.authorization.utils.LcmUtils;
import sailpoint.object.*;
import sailpoint.service.RequestAccessService;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.IdentityRequestAuthorizer;
import sailpoint.web.UserContext;
import sailpoint.web.accessrequest.AccessRequest;

/**
 * Ensures that the user is the attachment owner, or the approver / request item owner.
 *
 * Used for validating download access to attachments.
 */
public class AttachmentsAuthorizer implements Authorizer {
    Attachment attachment;

    private static final String ATTACHMENT_CONTEXT_ACCESS_REQUEST = "accessRequest";
    private static final String ATTACHMENT_CONTEXT_TYPE = "type";

    public AttachmentsAuthorizer(Attachment attachment) { this.attachment = attachment; }

    /**
     * Validate user has download access
     *
     * @param userContext The UserContext to authorize.
     * @throws GeneralException
     */
    @Override
    public void authorize(UserContext userContext) throws GeneralException {

        boolean authorized = false;
        // authorize attachment owner, approver, identity request item owner
        if (attachment != null) {
            if (attachment.getOwner().equals(userContext.getLoggedInUser())) {
                authorized = true;
            } else {
                // if not the owner check if user is the approver
                if (attachment.isInUse(userContext.getContext())) {
                    QueryOptions qo = new QueryOptions();
                    List<Filter> filters = new ArrayList<Filter>();
                    filters.add(Filter.eq("id", attachment.getId()));
                    qo.add(Filter.collectionCondition("attachments", Filter.or(filters)));
                    List<IdentityRequestItem> items = userContext.getContext().getObjects(IdentityRequestItem.class, qo);
                    for (IdentityRequestItem item : Util.safeIterable(items)) {
                        IdentityRequest identityRequest = item.getIdentityRequest();
                        if (IdentityRequestAuthorizer.isAuthorized(identityRequest, userContext)) {
                            authorized = true;
                            break;
                        }
                    }
                }
            }
        }
        if (!authorized) {
            String err = new Message("attachments_unauthorized").getLocalizedMessage();
            throw new UnauthorizedAccessException(err);
        }
    }

    /**
     * Authorize user for uploading. Only users authorized for lcm actions for specific identities will be authorized.
     *
     * @param userContext
     * @param attachmentContext
     * @throws GeneralException
     */
    public static void authorizeUpload(UserContext userContext, Map<String, Object> attachmentContext) throws GeneralException {
        if (attachmentContext == null) {
            String err = new Message("attachments_auth_context_required").getLocalizedMessage();
            throw new UnauthorizedAccessException(err);
        }
        // make sure user can make requests
        String action = (String)attachmentContext.get(ATTACHMENT_CONTEXT_TYPE);
        if (Util.isNullOrEmpty(action)) {
            // context type required
            String err = new Message("attachments_auth_context_required").getLocalizedMessage();
            throw new UnauthorizedAccessException(err);
        }

        if (action.equals(QuickLink.LCM_ACTION_REQUEST_ACCESS)) {
            // The attachment context contains the access request containing the request item being attached to; if the
            // user isn't authorized to make this request, then we throw without allowing file upload.

            Map<String, Object> accessRequestData = (Map<String, Object>) attachmentContext.get(ATTACHMENT_CONTEXT_ACCESS_REQUEST);
            LcmUtils.authorizeTargetedIdentities(accessRequestData,userContext.getContext(), userContext);
            RequestAccessService requestAccessService = new RequestAccessService(userContext);
            AccessRequest accessRequest = requestAccessService.createAndValidateAccessRequest(accessRequestData);
            if (accessRequest.getIdentityIds().size() > 1) {
                String err = new Message("attachments_button_disabled_aria_label").getLocalizedMessage();
                throw new UnauthorizedAccessException(err);
            }

            List<ProvisioningPlan.AccountRequest> allAddRequests = new ArrayList<>();
            allAddRequests.addAll(requestAccessService.createAddRoleRequests(accessRequest));
            allAddRequests.addAll(requestAccessService.createAddEntitlementRequests(accessRequest));

            if (!Util.isEmpty(allAddRequests)) {
                // Set up request helper; we don't need the helper but this will also check duplicate assignments and
                // missing account selections and throw if things aren't in order.
                requestAccessService.createRequestHelper(accessRequest, allAddRequests);
            }
        } else {
            String err = new Message("attachments_invalid_type", action).getLocalizedMessage();
            throw new UnauthorizedAccessException(err);
        }
    }
}

