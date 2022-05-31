package sailpoint.rest.ui.requestaccess;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;

import sailpoint.authorization.AccessItemAuthorizer;
import sailpoint.authorization.utils.LcmUtils;
import sailpoint.rest.ui.identities.RoleDetailResource;
import sailpoint.rest.ui.managedattribute.ManagedAttributeDetailResource;
import sailpoint.authorization.LcmActionAuthorizer;
import sailpoint.object.AccessRequestAccountInfo;
import sailpoint.object.Bundle;
import sailpoint.object.Configuration;
import sailpoint.object.ManagedAttribute;
import sailpoint.object.QuickLink;
import sailpoint.object.SailPointObject;
import sailpoint.rest.BaseResource;
import sailpoint.service.AdditionalQuestionsContext;
import sailpoint.service.AdditionalQuestionsService;
import sailpoint.service.AdditionalQuestionsService.AdditionalQuestionsInput;
import sailpoint.service.RequestAccessService;
import sailpoint.service.RequestAccessService.UniqueAssignmentInput;
import sailpoint.tools.GeneralException;
import sailpoint.tools.InvalidParameterException;
import sailpoint.tools.Message;
import sailpoint.tools.ObjectNotFoundException;
import sailpoint.tools.Util;
import sailpoint.web.accessrequest.RequestedRole;

/**
 * Resource to interact with a single access item in Request Access
 */
public class AccessItemResource extends BaseAccessItemListResource implements AdditionalQuestionsContext {

    ////////////////////////////////////////////////////////////////////////////
    //
    // INNER CLASSES
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Encapsulates the request body for an additional questions request.
     */
    public static class AdditionalQuestionsRequest implements AdditionalQuestionsInput {

        // Constants for expected attributes in the body.
        public static final String IDENTITY_IDS = "identityIds";
        public static final String PERMITTED_BY_ID = "permittedById";
        public static final String OTHER_ADDED_ROLES = "otherAddedRoles";
        public static final String ASSIGNMENT_ID = "assignmentId";
        public static final String QUICK_LINK = "quickLink";

        private List<String> identityIds;
        private String permittedById;
        private List<RequestedRole> otherAddedRoles;
        private String assignmentId;
        private String quickLinkName;


        /**
         * Constructor for an AdditionalQuestionsRequest from a request body.
         *
         * @param  inputs  The HTTP request body parsed into a Map.
         *
         * @throws InvalidParameterException  If the body has bad data.
         * @throws GeneralException           If the body has bad data.
         */
        public AdditionalQuestionsRequest(Map<String,Object> inputs)
            throws InvalidParameterException, GeneralException {

            this.identityIds = getIdentityIds(inputs);
            this.permittedById = (String) inputs.get(PERMITTED_BY_ID);
            this.otherAddedRoles = getOtherAddedRoles(inputs);
            this.assignmentId = (String) inputs.get(ASSIGNMENT_ID);
            this.quickLinkName = (String) inputs.get(QUICK_LINK);
        }

        @SuppressWarnings("unchecked")
        private static List<String> getIdentityIds(Map<String, Object> inputs) throws InvalidParameterException {
            Object identityIds = inputs.get(IDENTITY_IDS);
            if (identityIds == null || !(identityIds instanceof List)) {
                throw new InvalidParameterException(IDENTITY_IDS);
            }
            for (Object identityId : (List<?>)identityIds) {
                if (!(identityId instanceof String)) {
                    throw new InvalidParameterException(IDENTITY_IDS);
                }
            }

            return (List<String>)identityIds;
        }

        @SuppressWarnings("unchecked")
        private static List<RequestedRole> getOtherAddedRoles(Map<String,Object> inputs)
            throws InvalidParameterException, GeneralException {

            List<RequestedRole> roles = new ArrayList<RequestedRole>();
            Object addedRoles = inputs.get(OTHER_ADDED_ROLES);

            if (null != addedRoles) {
                if (!(addedRoles instanceof List)) {
                    throw new InvalidParameterException(OTHER_ADDED_ROLES);
                }
                for (Object addedRole : (List<?>) addedRoles) {
                    if (!(addedRole instanceof Map)) {
                        throw new InvalidParameterException(OTHER_ADDED_ROLES);
                    }
                    roles.add(new RequestedRole((Map<String,Object>) addedRole));
                }
            }

            return roles;
        }

        @Override
        public List<String> getIdentityIds() {
            return this.identityIds;
        }

        @Override
        public String getPermittedById() {
            return this.permittedById;
        }

        @Override
        public List<RequestedRole> getOtherAddedRoles() {
            return this.otherAddedRoles;
        }
        
        @Override
        public String getAssignmentId() {
            return this.assignmentId;
        }

        @Override
        public String getQuickLink() { return this.quickLinkName; }
    }

    /**
     * Encapsulates the request body for unique assignment check
     */
    public static class UniqueAssignmentRequest extends UniqueAssignmentInput {

        public static final String PERMITTED_BY_ID = "permittedById";
        public static final String ACCOUNT_SELECTIONS = "accountSelections";
        public static final String ASSIGNMENT_ID = "assignmentId";
        public static final String QUICKLINK_NAME = "quickLink";
        
        private String accessItemId;
        private String permittedById;
        private String assignmentId;
        private String quickLinkName;
        private List<AccessRequestAccountInfo> accountInfos;

        public UniqueAssignmentRequest(String accessItemId, Map<String, Object> inputs) {
            this.accessItemId = accessItemId;
            // Parse the request information.
            this.permittedById = (String)inputs.get(PERMITTED_BY_ID);
            this.assignmentId = (String)inputs.get(ASSIGNMENT_ID);
            this.quickLinkName = (String)inputs.get(QUICKLINK_NAME);
            @SuppressWarnings("unchecked")
            List<Map<String,String>> selectionMaps = (List<Map<String,String>>) inputs.get(ACCOUNT_SELECTIONS);
            this.accountInfos = new ArrayList<AccessRequestAccountInfo>();
            if (!Util.isEmpty(selectionMaps)) {
                for (Map<String,String> map : selectionMaps) {
                    this.accountInfos.add(new AccessRequestAccountInfo(map));
                }
            }
        }

        @Override
        public List<AccessRequestAccountInfo> getAccountSelections() {
            return this.accountInfos;
        }

        @Override
        public String getRoleId() {
            return this.accessItemId;
        }

        @Override
        public String getPermittedById() {
            return this.permittedById;
        }

        @Override
        public String getAssignmentId() {
            return this.assignmentId;
        }

        public String getQuicklinkName() { return this.quickLinkName; }
    }


    ////////////////////////////////////////////////////////////////////////////
    //
    // CONSTANTS
    //
    ////////////////////////////////////////////////////////////////////////////

    public static final String COLUMNS_KEY_PREFIX = AccessItemListResource.COLUMNS_KEY_PREFIX;
    
    ////////////////////////////////////////////////////////////////////////////
    //
    // FIELDS
    //
    ////////////////////////////////////////////////////////////////////////////

    private String accessItemId;
    private SailPointObject accessItem;


    ////////////////////////////////////////////////////////////////////////////
    //
    // CONSTRUCTOR
    //
    ////////////////////////////////////////////////////////////////////////////

    public AccessItemResource(String accessItemId, BaseResource parent) throws GeneralException {
        super(parent);
        this.accessItemId = accessItemId;
        if (Util.isNullOrEmpty(this.accessItemId)) {
            throw new InvalidParameterException("accessItemId");
        }
    }


    ////////////////////////////////////////////////////////////////////////////
    //
    // METHODS
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Get AdditionalQuestions object for this access item to determine if there are any extra things
     * to ask the user when selecting this access for addition
     * @param inputs Map of inputs 
     *               -- identityIds: List of IDs for identities being targeted
     * @return AdditionalQuestionsService.AdditionalQuestions 
     * @throws GeneralException
     */
    @POST
    @Path("additionalQuestions")
    public AdditionalQuestionsService.AdditionalQuestions getAdditionalQuestions(Map<String, Object> inputs) 
            throws GeneralException {
        SailPointObject accessItem = getAccessItem();
        AdditionalQuestionsRequest request = new AdditionalQuestionsRequest(inputs);
        // right now the AccessItemAuthorizer can only handle up to 1 target identity. For self service to work we need
        // to pass the target when the list has just one id.
        String targetId = null;
        if (!Util.isEmpty(request.getIdentityIds()) && request.getIdentityIds().size() == 1) {
            targetId = request.getIdentityIds().get(0);
        }
        LcmUtils.authorizeTargetedIdentities(request.getIdentityIds(), getContext(), this);
        authorize(new LcmActionAuthorizer(QuickLink.LCM_ACTION_REQUEST_ACCESS), new AccessItemAuthorizer(accessItemId, targetId, request.getQuickLink()));

        AdditionalQuestionsService service = new AdditionalQuestionsService(this);

        AdditionalQuestionsService.AdditionalQuestions questions =
                service.getAdditionalQuestions(accessItem, request);

        // Do some extra column conversion on the permitted roles, if they are there
        if (!Util.isEmpty(questions.getPermittedRoles())) {
           questions.setPermittedRoles(convertRows(questions.getPermittedRoles(), COLUMNS_KEY_PREFIX));  
        }

        return questions;
    }


    /**
     * Returns true if the passed data represents a unique assignment.
     * @param data Map contains
     *             permittedBy: id of permitting role if applicable
     *             accountSelections: JSON that can be deserialized into a List of AccessRequestAccountInfos
     *             assignmentId: id of the assignment if applicable
     *             quickLinkName: the name of the quickLink this endpoint was called from
     * @return true if the passed data represents a unique assignment otherwise false
     */
    @POST
    @Path("checkUniqueAssignment")
    public Response checkUniqueAssignment(Map<String, Object> data) throws GeneralException {
        //We dont need the access item but this will check for a 404 condition
        getAccessItem();

        // Authorize.
        Configuration configuration = getContext().getConfiguration();
        UniqueAssignmentRequest uniqueAssignmentRequest = new UniqueAssignmentRequest(this.accessItemId, data);
        List<AccessRequestAccountInfo> accountInfos = uniqueAssignmentRequest.getAccountSelections();
        // There can only be one target identity here. We validate this later as well
        String targetIdentity = null;
        if (!Util.isEmpty(accountInfos)) {
            targetIdentity = accountInfos.get(0).getIdentityId();
        }

        boolean isSelfServicePermittedEnabled = configuration.getAttributes().getBoolean("requestRolesPermittedSelfEnabled");
        boolean isSelfServiceAssignedEnabled = configuration.getAttributes().getBoolean("requestRolesAssignableSelfEnabled");
        authorize(new LcmActionAuthorizer(QuickLink.LCM_ACTION_REQUEST_ACCESS, isSelfServiceAssignedEnabled || isSelfServicePermittedEnabled),
                new AccessItemAuthorizer(accessItemId, targetIdentity, (String) uniqueAssignmentRequest.getQuicklinkName()));

        Response.Status status = Response.Status.OK;

        /* Process all accountInfos and assure they are all for the same identity */
        boolean isVerified = verifyAccountInfos(uniqueAssignmentRequest.getAccountSelections());
        if (isVerified) {
            boolean uniqueAssignment = new RequestAccessService(this).isUniqueAssignment(uniqueAssignmentRequest);
            if (!uniqueAssignment) {
                status = Response.Status.CONFLICT;
            }
        } else {
            status = Response.Status.INTERNAL_SERVER_ERROR;
        }
        return Response.status(status).build();
    }

    /**
     * Gets the role details for the role represented by the access item.
     * Passes through to the RoleDetailsResource.
     * @return RoleDetailResource whose main getter will return a RoleDetailDTO
     * @param quickLink The name of the quicklink this is called from. This is required.
     * @param identityId The id of the target identity for the access item. This is required.
     * @param assignmentId
     * @throws GeneralException
     */
    @Path("roleDetails")
    public RoleDetailResource getRoleDetails(@QueryParam("quickLink") String quickLink, @QueryParam("identityId") String identityId,
                                             @QueryParam("assignmentId") String assignmentId) throws GeneralException {
        authorize(new LcmActionAuthorizer(QuickLink.LCM_ACTION_REQUEST_ACCESS), new AccessItemAuthorizer(accessItemId, identityId, quickLink));

        Bundle role = getContext().getObjectById(Bundle.class, accessItemId);
        if (role == null) {
            throw new ObjectNotFoundException();
        }

        Boolean classificationsEnabled = Configuration.getSystemConfig().getBoolean(Configuration.DISPLAY_CLASSIFICATIONS_IN_ACCESS_REQUEST);
        return new RoleDetailResource(accessItemId, assignmentId, identityId, classificationsEnabled, this);
    }

    /**
     * @return a list of identities that have role or entitlement and match query params.
     * @param quickLink The name of the quicklink this is called from. This is required.
     * @param identityId The id of the target identity for the access item.
     */
    @Path("population")
    public AccessItemPopulationResource getAccessItemPopulationResource(@QueryParam("quickLink") String quickLink, @QueryParam("identityId") String identityId) throws GeneralException {
        SailPointObject accessItem = getAccessItem();
        authorize(new LcmActionAuthorizer(QuickLink.LCM_ACTION_REQUEST_ACCESS), new AccessItemAuthorizer(accessItemId, identityId, quickLink));
        return new AccessItemPopulationResource(this, accessItem);
    }

    /**
     * Gets the managed attribute details.
     * Passes through to the ManagedAttributeDetailsResource. Pass in classificationsEnabled flag for disabling
     * classifications endpoint.
     * @param quickLink The name of the quicklink this is called from. This is required.
     * @param identityId The id of the target identity for the access item. This is required.
     * @return ManagedAttributeDetailResource whose main getter will return a ManagedAttributeDetailDTO
     * @throws GeneralException
     */
    @Path("managedAttributeDetails")
    public ManagedAttributeDetailResource getManagedAttributeDetails(@QueryParam("quickLink") String quickLink,
                                                                     @QueryParam("identityId") String identityId) throws GeneralException {
        authorize(new LcmActionAuthorizer(QuickLink.LCM_ACTION_REQUEST_ACCESS), new AccessItemAuthorizer(accessItemId, identityId, quickLink));

        ManagedAttribute ma = getContext().getObjectById(ManagedAttribute.class, accessItemId);
        if (ma == null) {
            throw new ObjectNotFoundException();
        }

        Boolean classificationsEnabled = Configuration.getSystemConfig().getBoolean(Configuration.DISPLAY_CLASSIFICATIONS_IN_ACCESS_REQUEST);
        return new ManagedAttributeDetailResource(getAccessItemManagedAttribute(), classificationsEnabled, this);
    }

    /**
     * Returns true if all accountInfos use same identityId.  Otherwise false
     * @param accountInfos The AccountInfos to verify
     * @return true if all accountInfos use same identityId.  Otherwise false
     */
    private boolean verifyAccountInfos(List<AccessRequestAccountInfo> accountInfos) {
        String identityId = null;
        boolean sameIdentity = true;
        for (AccessRequestAccountInfo accountInfo : accountInfos) {
            if (identityId == null) {
                identityId = accountInfo.getIdentityId();
            }
            if (!identityId.equals(accountInfo.getIdentityId())) {
                sameIdentity = false;
                break;
            }
        }
        return sameIdentity;
    }

    /**
     * Get the access item represented by the ID. Should be either a Bundle or a ManagedAttribute
     * @return SailPointObject, either a Bundle or ManagedAttribute
     * @throws GeneralException if accessItemId does not refer to valid Bundle or ManagedAttribute
     */
    private SailPointObject getAccessItem() throws GeneralException {
        if (this.accessItem == null) {
            this.accessItem = getContext().getObjectById(Bundle.class, this.accessItemId);
            if (this.accessItem == null) {
                this.accessItem = getContext().getObjectById(ManagedAttribute.class, this.accessItemId);
            }
            if (this.accessItem == null) {
                throw new ObjectNotFoundException(new Message("Unable to find matching Bundle or ManagedAttribute for ID: " + this.accessItemId));
            }
        }

        return this.accessItem;
    }

    /**
     * Get the access item represented by the ID, assuming it's a ManagedAttribute.
     * @return ManagedAttribute
     * @throws GeneralException if accessItemId does not refer to a valid ManagedAttribute
     */
    private ManagedAttribute getAccessItemManagedAttribute() throws GeneralException {
        SailPointObject obj = getAccessItem();
        if (!(obj instanceof ManagedAttribute)) {
            throw new ObjectNotFoundException(new Message("Unable to find matching ManagedAttribute for ID: " + this.accessItemId));
        }
        return (ManagedAttribute)obj;
    }

    @Override
    public List<String> getPermittedRoleProperties() throws GeneralException {
        return getProjectionColumns(COLUMNS_KEY_PREFIX + "Role");
    }

    @Override
    public String getQuickLink() {
        return _quickLink;
    }
}
