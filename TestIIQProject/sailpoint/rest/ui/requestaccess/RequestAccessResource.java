/* (c) Copyright 2014 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.rest.ui.requestaccess;

import java.util.List;
import java.util.Map;

import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.authorization.LcmActionAuthorizer;
import sailpoint.authorization.utils.LcmUtils;
import sailpoint.object.QuickLink;
import sailpoint.rest.BadRequestDTO;
import sailpoint.rest.BaseResource;
import sailpoint.rest.HttpSessionStorage;
import sailpoint.service.AccessRequestResultItem;
import sailpoint.service.RequestAccessService;
import sailpoint.service.RequestAccessService.MissingAccountSelectionsException;
import sailpoint.tools.GeneralException;
import sailpoint.web.accessrequest.AccessRequest;

/**
 * Resource to handle LCM access requests
 *
 * @author: michael.hide
 * Created: 10/8/14 12:25 PM
 */
@Path("requestAccess")
public class RequestAccessResource extends BaseResource {

    private static Log log = LogFactory.getLog(RequestAccessResource.class);

    @Path("identities")
    public IdentityListResource getIdentitiesResource() {
        return new IdentityListResource(this);
    }

    @Path("selectedIdentities")
    public SelectedIdentityListResource getSelectedIdentitiesResource() {
        return new SelectedIdentityListResource(this);
    }

    @Path("identityIdNames")
    public IdentityIdNameListResource getIdentityIdNamesResource() {
        return new IdentityIdNameListResource(this);
    }

    @Path("accessItems")
    public AccessItemListResource getAccessItemsResource() {
        return new AccessItemListResource(this);
    }

    @Path("currentAccessItems")
    public CurrentAccessItemListResource getCurrentAccessItemsResource() {
        return new CurrentAccessItemListResource(this);
    }

    public static String PARAM_QUICK_LINK = "quickLink";
    /**
     * Submits a request for modifying user access to the RequestAccessService service layer
     * and returns a response with a list of SubmitResultItem(s) or a BadRequestDTO if the
     * submission fails.
     *
     * @param data  The AccessRequest data.
     *
     * @return A Response that is returns a 200 with a list of SubmitResultItems on success, or a
     *     400 with a BadRequestDTO if the submission failed due to bad data.
     */
    @POST
    public Response submitRequest(Map<String, Object> data) throws GeneralException {

        List<AccessRequestResultItem> resultItems = null;

        // Ensure current user has permission to request access
        authorize(new LcmActionAuthorizer(QuickLink.LCM_ACTION_REQUEST_ACCESS));

        // Loop through identities and ensure current user has permission to request for each
        if (data != null) {
            LcmUtils.authorizeTargetedIdentities(data, getContext(), this);

            RequestAccessService service = new RequestAccessService(this);
            service.setQuickLink((String) data.get(PARAM_QUICK_LINK));
            try {
                AccessRequest accessRequest = service.createAndValidateAccessRequest(data);
                resultItems = service.submitRequest(accessRequest, new HttpSessionStorage(getSession()));
            }
            catch (MissingAccountSelectionsException ex) {
                BadRequestDTO dto = new BadRequestDTO(ex.getType(), ex.getAccountSelections());
                return Response.status(Status.BAD_REQUEST).entity(dto).build();
            }
            catch (RequestAccessService.DuplicateAssignmentException ex) {
                BadRequestDTO dto = new BadRequestDTO(ex.getType(), ex.getRequestItemNames());
                return Response.status(Status.BAD_REQUEST).entity(dto).build();
            }
            catch (RequestAccessService.InvalidRemoveException | RequestAccessService.AttachmentRequiredException |
                    RequestAccessService.CommentRequiredException ex) {
                BadRequestDTO dto = new BadRequestDTO(ex.getType(), null);
                return Response.status(Status.BAD_REQUEST).entity(dto).build();
            }
        }

        return Response.ok(resultItems).build();
    }
    

}
