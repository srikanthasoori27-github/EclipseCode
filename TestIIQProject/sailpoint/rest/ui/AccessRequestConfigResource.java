/*
 * (c) Copyright 2020 SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.rest.ui;

import java.util.List;
import java.util.Map;

import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;

import org.apache.commons.collections.MapUtils;

import sailpoint.authorization.LcmActionAuthorizer;
import sailpoint.authorization.utils.LcmUtils;
import sailpoint.object.QuickLink;
import sailpoint.object.Rule;
import sailpoint.rest.BadRequestDTO;
import sailpoint.rest.BaseResource;
import sailpoint.service.AttachmentService;
import sailpoint.service.AccessRequestConfigDTO;
import sailpoint.service.AccessRequestConfigService;
import sailpoint.service.RequestAccessService;
import sailpoint.tools.GeneralException;
import sailpoint.tools.InvalidParameterException;
import sailpoint.tools.Message;
import sailpoint.web.accessrequest.AccessRequest;

/**
 * Resource that deals with getting configuration information for specific access requests
 */
public class AccessRequestConfigResource extends BaseResource {
    private static final String ATTACHMENTS_DISABLED = "attachments_disabled";

    public AccessRequestConfigResource(BaseResource parent) {
        super(parent);
    }

    /**
     * Get the comment configs for the request.
     *
     * @param data access request data map
     * @return Map the list of applicable AccessRequestConfigDTOs keyed by the item id
     * @throws GeneralException
     */
    @Path("comment")
    @POST
    public Map<String, List<AccessRequestConfigDTO>> getCommentConfigs(Map<String, Object> data) throws GeneralException {
        authorizeAndValidate(data);
        AccessRequest accessRequest = createAndValidateAccessRequest(data);
        AccessRequestConfigService accessRequestConfigService = new AccessRequestConfigService(this.getContext());
        return accessRequestConfigService.getConfigsForRequest(this.getLoggedInUser(), accessRequest, Rule.Type.CommentConfig);
    }

    /**
     * Get the attachment configs for the request.
     *
     * @param data access request data map
     * @return Map the list of applicable AccessRequestConfigDTOs keyed by the item id
     * @throws GeneralException
     */
    @Path("attachment")
    @POST
    public Map<String, List<AccessRequestConfigDTO>> getAttachmentConfigs(Map<String, Object> data) throws GeneralException {
        // Authorization requires that the user is authorized to request access for all identities in the request.
        authorizeAndValidate(data);

        AttachmentService attachmentService = new AttachmentService(this);

        // 403 if attachments are not enabled.
        if (!attachmentService.isAttachmentsEnabled()) {
            String err = new Message(ATTACHMENTS_DISABLED).getLocalizedMessage();
            throw new WebApplicationException(err, Response.Status.FORBIDDEN);
        }

        AccessRequest accessRequest = createAndValidateAccessRequest(data);

        try {
            AccessRequestConfigService accessRequestConfigService = new AccessRequestConfigService(this.getContext());
            Map<String, List<AccessRequestConfigDTO>> attachmentConfigs =
                    accessRequestConfigService.getConfigsForRequest(this.getLoggedInUser(), accessRequest, Rule.Type.AttachmentConfig);
            if (accessRequest.getIdentityIds() != null && accessRequest.getIdentityIds().size() > 1
                    && accessRequestConfigService.configsRequire(attachmentConfigs)) {
                // Multi user requests with required attachments are not supported at this time.
                throw new RequestAccessService.AttachmentRequiredException();
            }
            return attachmentConfigs;
        } catch (RequestAccessService.AttachmentRequiredException ex) {
            BadRequestDTO dto = new BadRequestDTO(ex.getType(), null);
            Response response = Response.status(Response.Status.BAD_REQUEST).entity(dto).build();
            throw new WebApplicationException(ex, response);
        }
    }

    /**
     * Create and validate the data map
     * @param data access request data map
     * @return AccessRequest object converted from data map
     * @throws GeneralException
     */
    private AccessRequest createAndValidateAccessRequest(Map<String, Object> data) throws GeneralException {
        try {
            RequestAccessService requestAccessService = new RequestAccessService(this);
            AccessRequest accessRequest = requestAccessService.createAndValidateAccessRequest(data);
            return accessRequest;
        } catch (RequestAccessService.MissingAccountSelectionsException ex) {
            BadRequestDTO dto = new BadRequestDTO(ex.getType(), ex.getAccountSelections());
            Response response = Response.status(Response.Status.BAD_REQUEST).entity(dto).build();
            throw new WebApplicationException(ex, response);
        } catch (RequestAccessService.DuplicateAssignmentException ex) {
            BadRequestDTO dto = new BadRequestDTO(ex.getType(), ex.getRequestItemNames());
            Response response = Response.status(Response.Status.BAD_REQUEST).entity(dto).build();
            throw new WebApplicationException(ex, response);
        } catch (RequestAccessService.InvalidRemoveException ex) {
            BadRequestDTO dto = new BadRequestDTO(ex.getType(), null);
            Response response = Response.status(Response.Status.BAD_REQUEST).entity(dto).build();
            throw new WebApplicationException(ex, response);
        }
    }

    /**
     * Validate data. Authorize requester permissions and against target identities.
     * @param data access request data map
     * @throws GeneralException
     */
    private void authorizeAndValidate(Map<String, Object> data) throws GeneralException {
        if (MapUtils.isEmpty(data)) {
            throw new InvalidParameterException("data");
        }

        // Ensure current user has permission to request access for all identities in the request.
        authorize(new LcmActionAuthorizer(QuickLink.LCM_ACTION_REQUEST_ACCESS));
        LcmUtils.authorizeTargetedIdentities(data, getContext(), this);
    }
}
