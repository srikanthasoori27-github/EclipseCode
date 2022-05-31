/* (c) Copyright 2017 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.rest.ui.identityrequest;

import java.util.Map;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;

import sailpoint.authorization.LcmEnabledAuthorizer;
import sailpoint.authorization.identityrequest.IdentityRequestAccessAuthorizer;
import sailpoint.authorization.identityrequest.IdentityRequestCancelAuthorizer;
import sailpoint.integration.ListResult;
import sailpoint.integration.RequestResult;
import sailpoint.object.IdentityRequest;
import sailpoint.rest.BaseResource;
import sailpoint.service.identityrequest.IdentityRequestDTO;
import sailpoint.service.identityrequest.IdentityRequestService;
import sailpoint.tools.GeneralException;
import sailpoint.tools.InvalidParameterException;
import sailpoint.tools.ObjectNotFoundException;
import sailpoint.tools.Util;

/**
 * Resource to interact with a single IdentityRequest object
 */
public class IdentityRequestResource extends BaseResource {

    public static String INPUT_COMMENTS = "comments";

    private String identityRequestId;

    /**
     * Constructor
     *
     * @param parent Parent resource
     * @param identityRequestId ID of the IdentityRequest object. Both object ID and request ID (name) are supported.
     * @throws GeneralException
     */
    public IdentityRequestResource(BaseResource parent, String identityRequestId) throws GeneralException {
        super(parent);

        if (identityRequestId == null) {
            throw new InvalidParameterException("identityRequestId");
        }

        this.identityRequestId = identityRequestId;
    }

    /**
     * Get a IdentityRequestDTO representation of the identity request
     * @return ObjectResult containing IdentityRequestDTO
     * @throws GeneralException
     */
    @GET
    public IdentityRequestDTO getIdentityRequest() throws GeneralException {
        IdentityRequest identityRequest = loadIdentityRequest();
        authorize(new IdentityRequestAccessAuthorizer(identityRequest));
        return new IdentityRequestService(getContext(), identityRequest).getDto(this);
    }

    /**
     * Cancel the workflow associated with this identity request
     * @param inputs Map of inputs
     * @return RequestResult with status and any error message
     * @throws GeneralException
     */
    @POST
    @Path("cancel")
    public RequestResult cancelRequest(Map<String, Object> inputs) throws GeneralException {
        authorize(new IdentityRequestCancelAuthorizer(loadIdentityRequest()), new LcmEnabledAuthorizer());
        String comments = Util.getString(inputs, INPUT_COMMENTS);
        return new IdentityRequestService(getContext(), loadIdentityRequest()).cancelWorkflow(this, comments);
    }

    /**
     * Get the list resource for identity request items.
     *
     * @return IdentityRequestItemListResource identity request item list resource
     * @throws GeneralException
     */
    @Path("items")
    public IdentityRequestItemListResource getIdentityRequestItemListResource() throws GeneralException {
        IdentityRequest identityRequest = loadIdentityRequest();

        authorize(new IdentityRequestAccessAuthorizer(identityRequest));

        return new IdentityRequestItemListResource(identityRequest, this);
    }

    /**
     * Get the list resource for identity request change items.
     *
     * @return IdentityRequestChangeItemListResource identity request change item list resource
     * @throws GeneralException
     */
    @Path("changeItems")
    public IdentityRequestChangeItemListResource getIdentityRequestChangeItemListResource() throws GeneralException {
        IdentityRequest identityRequest = loadIdentityRequest();
        authorize(new IdentityRequestAccessAuthorizer(identityRequest));
        return new IdentityRequestChangeItemListResource(identityRequest, this);
    }

    /**
     * Get the list of interactions for the identity request.
     *
     * @param sort sort by
     * @param start start index
     * @param limit limit per page
     * @return ListResult list result of ApprovalSummaryDTOs
     * @throws GeneralException
     */
    @GET
    @Path("interactions")
    public ListResult getInteractions(@QueryParam("sort") String sort,
                                      @QueryParam("start") String start,
                                      @QueryParam("limit") String limit) throws GeneralException {
        IdentityRequest identityRequest = loadIdentityRequest();

        authorize(new IdentityRequestAccessAuthorizer(identityRequest));

        IdentityRequestService identityRequestService = new IdentityRequestService(this.getContext(), identityRequest);

        return identityRequestService.getInteractions(this, sort, Util.atoi(start, 0), Util.atoi(limit, 5));
    }

    /**
     * Get the error, warning and info type messages.
     *
     * @param sort sort by
     * @param start start index
     * @param limit limit per page
     * @return ListResult list result with list of messages
     * @throws GeneralException
     */
    @GET
    @Path("messages")
    public ListResult getMessages(@QueryParam("sort") String sort,
                                  @QueryParam("start") String start,
                                  @QueryParam("limit") String limit) throws GeneralException {
        IdentityRequest identityRequest = loadIdentityRequest();

        authorize(new IdentityRequestAccessAuthorizer(identityRequest));

        IdentityRequestService identityRequestService = new IdentityRequestService(this.getContext(), identityRequest);

        return identityRequestService.getMessages(this, sort, Util.atoi(start, 0),
                Util.atoi(limit, 0));
    }

    @Path("email")
    public IdentityRequestEmailResource getEmailResource() {
        return new IdentityRequestEmailResource(this, this.identityRequestId);
    }

    /**
     * Loads the IdentityRequest object from the name
     * @return IdentityRequest The IdentityRequest object
     * @throws GeneralException
     */
    private IdentityRequest loadIdentityRequest() throws GeneralException {
        IdentityRequest identityRequest = getContext().getObjectByName(IdentityRequest.class, this.identityRequestId);
        if (identityRequest == null) {
            throw new ObjectNotFoundException(IdentityRequest.class, this.identityRequestId);
        }

        return identityRequest;
    }
}
