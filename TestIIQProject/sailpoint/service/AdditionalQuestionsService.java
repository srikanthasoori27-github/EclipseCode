package sailpoint.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.IdentityService;
import sailpoint.api.Localizer;
import sailpoint.api.ObjectUtil;
import sailpoint.api.RoleUtil;
import sailpoint.object.Application;
import sailpoint.object.Bundle;
import sailpoint.object.Configuration;
import sailpoint.object.Identity;
import sailpoint.object.Link;
import sailpoint.object.ManagedAttribute;
import sailpoint.object.Permission;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.ProvisioningPlan.AccountRequest;
import sailpoint.object.ProvisioningTarget;
import sailpoint.object.QuickLink;
import sailpoint.object.RoleAssignment;
import sailpoint.object.SailPointObject;
import sailpoint.service.LCMConfigService.SelectorObject;
import sailpoint.service.classification.ClassificationService;
import sailpoint.service.quicklink.QuickLinkOptionsConfigService;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.web.accessrequest.RequestedAccessItem;
import sailpoint.web.accessrequest.RequestedRole;
import sailpoint.web.lcm.IdentityAccountSelection;
import sailpoint.web.lcm.LcmAccessRequestHelper;
import sailpoint.web.util.MapListComparator;
import sailpoint.web.util.WebUtil;

/**
 * Service layer to handle fetching any additional questions for an access item
 */
public class AdditionalQuestionsService {

    ////////////////////////////////////////////////////////////////////////////
    //
    // INNER CLASSES
    //
    ////////////////////////////////////////////////////////////////////////////

    public enum RequestedObjectStatus {
        NO_OP,
        ADD_TO_CART,
        PROMPT_ACCOUNT_SELECTION,
        PROMPT_PERMITTED_ROLES,
        PENDING_REQUEST,
        INVALID_REQUESTEES,
        CURRENTLY_ASSIGNED,
        PROMPT_ACCOUNT_SELECTION_ROLE,
        HAS_EXISTING_ASSIGNMENT,
        UNIQUE_ASSIGNMENT,
        PROMPT_ROLE_ASSIGNMENT_SELECTION,
        BULK_ASSIGNED_OR_PENDING
    };

    /**
     * Object to hold additional question data
     */
    public static class AdditionalQuestions {
        private List<Map<String, Object>> permittedRoles;
        private List<IdentityAccountSelection> accountSelections;
        private RequestedObjectStatus status;
        private List<AssignedRole> ambiguousAssignedRoles;
        private List<String> invalidRequestees;

        /**
         * Get any roles that are permitted to be requested along with the 
         * requested access item (role-only)
         * @return List of maps representing permitted roles
         */
        public List<Map<String, Object>> getPermittedRoles() {
            return permittedRoles;
        }

        public void setPermittedRoles(List<Map<String, Object>> permittedRoles) {
            this.permittedRoles = permittedRoles;
        }

        /**
         * Get any account selections that are required to be made for the requested 
         * access item
         * @return List of {@link IdentityAccountSelection} objects
         */
        public List<IdentityAccountSelection> getAccountSelections() {
            return accountSelections;
        }

        public void setAccountSelections(List<IdentityAccountSelection> accountSelections) {
            this.accountSelections = accountSelections;
        }

        /**
         * Allows us to set a status on the requested item which allows the ui to take specific actions
         * such as prompting the user that they cannot request it (if it's already pending for example)
         * @return RequestedObjectStatus
         */
        public RequestedObjectStatus getStatus() {
            return status;
        }

        public void setStatus(RequestedObjectStatus status) {
            this.status = status;
        }

        /**
         * Get a list of AssignedRole objects to choose from when assigning a direct permitted role
         * @return List of {@link AssignedRole} objects
         */
        public List<AssignedRole> getAmbiguousAssignedRoles() {
            return ambiguousAssignedRoles;
        }

        public void setAmbiguousAssignedRoles(List<AssignedRole> ambiguousAssignedRoles) {
            this.ambiguousAssignedRoles = ambiguousAssignedRoles;
        }

        /**
         * Get a list of Identity Names that are invalid for the requested access
         * @return List of String objects
         */
        public List<String> getInvalidRequestees() {
            return invalidRequestees;
        }

        public void setInvalidRequestees(List<String> invalidRequestees) {
            this.invalidRequestees = invalidRequestees;
        }
    }

    /**
     * Object to hold information about an existing RoleAssignment. Used when requesting a permitted 
     * role that is permitted by an ambiguous set of assigned roles. 
     */
    public static class AssignedRole {
        private String assignmentId;
        private String roleId;
        private String name;
        private String assigner;
        private String assignmentNote;
        private Date created;

        /**
         * Constructor
         * @param roleAssignment RoleAssignment to base this AssignedRole on
         */
        public AssignedRole(RoleAssignment roleAssignment) {
            if (roleAssignment != null) {
                this.assignmentId = roleAssignment.getAssignmentId();
                this.roleId = roleAssignment.getRoleId();
                this.name = roleAssignment.getRoleName();
                this.assigner = roleAssignment.getAssigner();
                this.assignmentNote = WebUtil.escapeComment(roleAssignment.getComments());
                this.created = roleAssignment.getDate();
            }
        }

        /**
         * Get the ID of the assignment
         */
        public String getAssignmentId() {
            return assignmentId;
        }

        /**
         * Get the ID of the role
         */
        public String getRoleId() {
            return roleId;
        }

        /**
         * Get the name of the role
         */
        public String getName() {
            return name;
        }

        /**
         * Get the name of the identity who assigned the role
         */
        public String getAssigner() {
            return assigner;
        }

        /**
         * Get the assignment note
         */
        public String getAssignmentNote() {
            return assignmentNote;
        }

        /**
         * Get the date the assignment was created
         */
        public Date getCreated() {
            return created;
        }
    }


    /**
     * Parameters to ask for additional questions.  These are in an interface to
     * prevent an excessive number of parameters to the getAdditionalQuestions()
     * method, and to provide a nice way for the client to use an object to
     * gather the inputs.
     */
    public static interface AdditionalQuestionsInput {
        /**
         * Return the IDs of the identities that are being requested.
         */
        public List<String> getIdentityIds();

        /**
         * Return the ID of the role that permits the role which is being
         * requested.  This is null if the role being requested is directly
         * assignable (ie - not permitted by another role).
         */
        public String getPermittedById();

        /**
         * Return all other roles that have been requested to be added and are
         * related to the current role (either the permitting role or any other
         * sibling permitted roles) - along with the account selections that
         * have been made.
         */
        public List<RequestedRole> getOtherAddedRoles();

        /**
         * Return the assignment id for the selected AssignedRole for a permitted
         * role ambiguous role assignment. If non-null, the check for ambiguous assigned 
         * roles will be skipped as part of calculating status
         */
        public String getAssignmentId();

        /**
         * Return the name of the quicklink used to drive the request authorities for the request
         * @return
         */
        public String getQuickLink();
    }


    ////////////////////////////////////////////////////////////////////////////
    //
    // CONSTANTS
    //
    ////////////////////////////////////////////////////////////////////////////

    public static final String ACCESS_TYPE_COLUMN = "accessType";
    public static final String ACCESS_TYPE_ROLE = "Role";
    public static final String ID_COLUMN = "id";
    public static final String DISPLAYABLE_NAME_COLUMN = "displayableName";
    public static final String CLASSIFICATIONS_COLUMN = "classifications";

    public static final String ACCOUNT_REQUEST_TYPE_ROLE = "role";
    public static final String ACCOUNT_REQUEST_TYPE_ENTITLEMENT = "entitlement";


    ////////////////////////////////////////////////////////////////////////////
    //
    // FIELDS
    //
    ////////////////////////////////////////////////////////////////////////////

    private AdditionalQuestionsContext additionalQuestionsContext;
    
    //Runtime variable, initialized in every call to getAdditionalQuestions
    private LcmAccessRequestHelper lcmAccessRequestHelper;

    private static Log log = LogFactory.getLog(AdditionalQuestionsService.class);

    ////////////////////////////////////////////////////////////////////////////
    //
    // CONSTRUCTOR
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Constructor
     * @param context AdditionalQuestionsContext
     */
    public AdditionalQuestionsService(AdditionalQuestionsContext context) {
        this.additionalQuestionsContext = context;
    }


    ////////////////////////////////////////////////////////////////////////////
    //
    // METHODS
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Get the AdditionalQuestions posed by the passed access item for the given identities
     * @param accessItem SailPointObject, either Bundle or Role
     * @param input The input for determining the additional questions.
     * @return AdditionalQuestions object loading with any necessary values
     * @throws GeneralException
     */
    public AdditionalQuestions getAdditionalQuestions(SailPointObject accessItem, AdditionalQuestionsInput input)
        throws GeneralException {

        if (!isRole(accessItem) && !isEntitlement(accessItem)) {
            throw new GeneralException("accessItem must be either Bundle or ManagedAttribute");
        }
        if (null == input) {
            throw new GeneralException("Missing required input.");
        }

        if (Util.isEmpty(input.getIdentityIds())) {
            throw new GeneralException("identityIds cannot be empty");
        }

        // Initialize. 
        this.lcmAccessRequestHelper = null;        
        AdditionalQuestions questions = new AdditionalQuestions();

        // First, check if this is a request for something the user already has.  If so, then
        // don't do anything else because we are going to show an error.
        RequestedObjectStatus status = calculateStatus(accessItem, input, questions);

        questions.setStatus(status);

        // can return here if we cant add to the cart
        if (!questions.getStatus().equals(RequestedObjectStatus.ADD_TO_CART)) {
            return questions;
        }

        // If any Identity is not authorized to have the requested access, set the status and return with the identity
        // names set.
        Set<String> invalidIdentities;
        LCMConfigService lcmService = new LCMConfigService(additionalQuestionsContext.getContext());
        lcmService.setQuickLinkName(additionalQuestionsContext.getQuickLink());
        if (isRole(accessItem)) {
            invalidIdentities = lcmService.getInvalidRequestees(additionalQuestionsContext.getLoggedInUser(),
                    input.getIdentityIds(),accessItem.getId(), SelectorObject.Role);
        } else {
            invalidIdentities = lcmService.getInvalidRequestees(additionalQuestionsContext.getLoggedInUser(),
                    input.getIdentityIds(),accessItem.getId(), SelectorObject.ManagedAttribute);
        }
        if (invalidIdentities != null && invalidIdentities.size() > 0) {
            questions.setStatus(RequestedObjectStatus.INVALID_REQUESTEES);
            questions.setInvalidRequestees(new ArrayList<String>(invalidIdentities));
            return questions;
        }

        if (isRole(accessItem)) {
            // If assignment id is set, we have already done selection, so can skip it.
            if (input.getAssignmentId() == null) {
                questions.setAmbiguousAssignedRoles(
                        getAmbiguousAssignedRoles((Bundle)accessItem, input.getIdentityIds(), input.getPermittedById()));
                // If we need to select a role assignment, no need to do anything else yet. 
                // Just set the status and return. 
                if (!Util.isEmpty(questions.getAmbiguousAssignedRoles())) {
                    questions.setStatus(RequestedObjectStatus.PROMPT_ROLE_ASSIGNMENT_SELECTION);
                    return questions;
                }
            }
            questions.setPermittedRoles(getPermittedRoles((Bundle)accessItem, input.getIdentityIds()));
        }

        questions.setAccountSelections(getAccountSelections(accessItem, input));

        return questions;
    }

    /**
     * Get the LcmAccessRequestHelper. If not initialized, this will initialize it and compile the targets
     */
    private LcmAccessRequestHelper getLcmAccessRequestHelper(SailPointObject accessItem, AdditionalQuestionsInput input)
            throws GeneralException {
        if (this.lcmAccessRequestHelper == null) {
            this.lcmAccessRequestHelper = initializeLcmAccessRequestHelper(accessItem, input);
        }

        return this.lcmAccessRequestHelper;
    }

    private boolean isRole(SailPointObject accessItem) {
        return accessItem instanceof Bundle;
    }

    private boolean isEntitlement(SailPointObject accessItem) {
        return accessItem instanceof ManagedAttribute;
    }


    ////////////////////////////////////////////////////////////////////////////
    //
    // CHECK FOR DUPLICATE ASSIGNMENTS METHODS
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Calculate the RequestedObjectStatus for requesting the given item - primarily
     * related to whether the item is already assigned to the identity and cannot
     * be assigned again. If 'new account' is enabled we allow requesting again.
     */
    private RequestedObjectStatus calculateStatus(SailPointObject accessItem,  AdditionalQuestionsInput input,
                                                  AdditionalQuestions questions)
        throws GeneralException {

        List<String> identityIds = input.getIdentityIds();
        AccountRequest request = isRole(accessItem) ? createAccountRequest((Bundle) accessItem, input.getPermittedById())
                : createAccountRequest((ManagedAttribute) accessItem);
        List<String> invalidRequestees = new ArrayList<String>();
        RequestedObjectStatus status = calculateStatus(request, identityIds, getLcmAccessRequestHelper(accessItem, input), invalidRequestees);
        if (!Util.isEmpty(invalidRequestees)) {
            questions.setInvalidRequestees(invalidRequestees);
        }

        return status;
    }

    /**
     * Calculate the RequestedObjectStatus for the AccountRequest, considering available accounts as well
     * @param request AccountRequest
     * @param identityIds List of identity ids
     * @param accessRequestHelper Initialized and compiled LcmAccessRequestHelper to find the account selections
     * @param invalidRequestees Empty, non-null list of strings to hold the invalid requestees, if any are found.
     * @return RequestedObjectStatus
     * @throws GeneralException
     */
    protected RequestedObjectStatus calculateStatus(AccountRequest request, List<String> identityIds, LcmAccessRequestHelper accessRequestHelper, List<String> invalidRequestees)
            throws GeneralException {
        if (invalidRequestees == null || !Util.isEmpty(invalidRequestees)) {
            throw new GeneralException("invalidRequestees must be non-null and empty");
        }

        RequestedObjectStatus status = RequestedObjectStatus.ADD_TO_CART;
        boolean isBulkRequest = identityIds.size() > 1;

        for(String id : identityIds) {

            Identity identity = this.additionalQuestionsContext.getContext().getObjectById(Identity.class, id);
            status = getRequestedObjectStatus(request, identity, false);

            if (status != RequestedObjectStatus.ADD_TO_CART && isBulkRequest) {
                invalidRequestees.add(identity.getDisplayableName());
            }
        }

        if (invalidRequestees.size() > 0) {
            status = RequestedObjectStatus.BULK_ASSIGNED_OR_PENDING;
        }
        
        // Do not add to the cart any direct permitted role request when the role is a PermittedRoleAssignment for every permitting assignment, 
        // regardless of multiple assignment settings.
        boolean isDirectPermitAssigned = LcmAccessRequestHelper.isDirectPermittedRoleRequest(request) && status == RequestedObjectStatus.CURRENTLY_ASSIGNED;

        // If this is a role request that we think is pending or assigned, but allows multiple
        // assignment of the role, check the provisioning targets to see if we have extra accounts or
        // can create a new account. If so, we can add it to the cart. 
        if (status != RequestedObjectStatus.ADD_TO_CART &&
                LcmAccessRequestHelper.isRoleRequest(request) &&
                isMultipleAssignmentAllowed(getRoleId(request)) &&
                !isDirectPermitAssigned) {
            // Get the targets even if answered, since we have to inspect in any case. 
            List<IdentityAccountSelection> accountSelections = accessRequestHelper.getIdentityAccountSelections(true);
            List<String> newInvalidRequestees = getInvalidRequestees(accountSelections, LcmAccessRequestHelper.getRoleName(request));

            // Noone was invalid, so we can add it. Yay!
            if (Util.isEmpty(newInvalidRequestees)) {
                status = RequestedObjectStatus.ADD_TO_CART;
            }

            // Remove requestees that are no longer valid
            if (!Util.isEmpty(invalidRequestees)) {
                Iterator<String> iterator = invalidRequestees.iterator();
                while (iterator.hasNext()) {
                    if (!newInvalidRequestees.contains(iterator.next())) {
                        iterator.remove();
                    }
                }
            }
        }

        return status;
    }

    /**
     * Given a list of AccountSelections, go through and see which identities have neither an account
     * without an existing assignment nor can have a new account created
     */
    private List<String> getInvalidRequestees(List<IdentityAccountSelection> accountSelections, String roleName) throws GeneralException {
        List<String> invalidRequestees = new ArrayList<String>();
        for (IdentityAccountSelection identityAccountSelection : Util.safeIterable(accountSelections)) {
            List<IdentityAccountSelection.ProvisioningTarget> targets = new ArrayList<IdentityAccountSelection.ProvisioningTarget>();
            for (IdentityAccountSelection.ProvisioningTarget target : Util.safeIterable(identityAccountSelection.getProvisioningTargets())) {
                if (Util.nullSafeEq(target.getTargetRole(), roleName)) {
                    targets.add(target);
                }
            }
            
            boolean allSame = false;
            if (Util.nullSafeSize(targets) > 1) {
                //We can have additional targets due to always calculating retains, if the targets are for the same app, we
                //have duplicate requests.
                String lastAppName = "";
                for (IdentityAccountSelection.ProvisioningTarget target : Util.iterate(targets)) {
                    String appName = target.getApplicationName();
                    if (Util.isNullOrEmpty(lastAppName)) {
                        lastAppName = appName;
                    }
                      
                    if (!Util.nullSafeCaseInsensitiveEq(appName, lastAppName)) {
                        allSame = false;
                        break;
                    } else {
                        allSame = true;
                    }
                }
            }

            // We only know if we can block it if there is a single application to choose from
            if (Util.size(targets) == 1 || allSame) {
                if (!allowAddToCart(targets)) {
                    invalidRequestees.add(identityAccountSelection.getIdentityName());
                }
            }
        }
        
        // Swap out displayable name
        for (int i = 0; i < invalidRequestees.size(); i++) {
            Identity identity = this.additionalQuestionsContext.getContext().getObjectByName(Identity.class, invalidRequestees.get(i));
            invalidRequestees.set(i, identity.getDisplayableName());
        }

        return invalidRequestees;
    }

    /**
     * Check the IdentityAccountSelection and see if it will allow anything to be added to the cart. 
     * If there is an account without an existing assignment or if it allows a new account to b created,
     * we will allow it
     */
    private boolean allowAddToCart(List<IdentityAccountSelection.ProvisioningTarget> targets) {
        for (IdentityAccountSelection.ProvisioningTarget target : Util.safeIterable(targets)) {
            if (target.isAllowCreate()) {
                return true;
            }

            // If no accounts exist, we will force a create, so let it through.
            if (Util.isEmpty(target.getAccountInfos())) {
                return true;
            }

            for (IdentityAccountSelection.ProvisioningTarget.AccountInfo accountInfo : Util.safeIterable(target.getAccountInfos())) {
                if (Util.isNullOrEmpty(accountInfo.getExistingAssignment())) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Refactored from the classic ui.  This looks over the identity and the request to see if the user can
     * request the item or not.
     * @param request (AccountRequest) The request being looked at to determine the status
     * @param identity (Identity) The identity we are requesting for (this is not a bulk request at this point)
     * @return (RequestedObjectStatus) Indicates whether the user can request the object or not
     * @throws GeneralException
     */
    public RequestedObjectStatus getRequestedObjectStatus(ProvisioningPlan.AccountRequest request, Identity identity) throws GeneralException {
        return getRequestedObjectStatus(request, identity, true);
    }

    /**
     * Refactored from the classic ui.  This looks over the identity and the request to see if the user can
     * request the item or not.
     * @param request (AccountRequest) The request being looked at to determine the status
     * @param identity (Identity) The identity we are requesting for (this is not a bulk request at this point)
     * @param checkAllowMultipleAssignment If true, if role allows multiple assignment, do not mark as CURRENTLY_ASSIGNED
     * @return (RequestedObjectStatus) Indicates whether the user can request the object or not
     * @throws GeneralException
     */
    public RequestedObjectStatus getRequestedObjectStatus(AccountRequest request, Identity identity, boolean checkAllowMultipleAssignment)
        throws GeneralException {
        RequestedObjectStatus status = RequestedObjectStatus.ADD_TO_CART;

        boolean isRoleRequest = LcmAccessRequestHelper.isRoleRequest(request);

        // If there can be a new account request, we should always provide
        // the choice of letting the entitlement apply to a new account
        boolean canRequestAccountForEntitlement =
                isAdditionalAccountAllowed(identity.getId()) &&
                        isAdditionalAccountSupported(request) &&
                        Util.isNullOrEmpty(request.getNativeIdentity());

        // -- If this is an entitlement request
        //    AND
        //    this can't be a new account request
        //    then find out if it is assigned
        // OR
        // -- If this is not an entitlement request
        //    then find out if the role is assigned.
        //
        // Because if this is an entitlement request and it can apply to multiple accounts,
        // they'll need to choose the account first and then we'll check again.
        if ((!isRoleRequest && !canRequestAccountForEntitlement &&
                isEntitlementCurrentlyAssigned(request, identity)) ||
                (isRoleRequest && isRoleCurrentlyAssigned(getRoleId(request), identity, checkAllowMultipleAssignment))) {
            status = RequestedObjectStatus.CURRENTLY_ASSIGNED;
        }
        else if (!canRequestAccountForEntitlement && !isRoleRequest &&
                hasPendingEntitlementRequest(request, identity)) {
            status = RequestedObjectStatus.PENDING_REQUEST;
        }
        else if (isRoleRequest && hasPendingRoleRequest(identity, request)) {
            status = RequestedObjectStatus.PENDING_REQUEST;
        }
        else if (isRoleRequest && LcmAccessRequestHelper.isDirectPermittedRoleRequest(request)) {
            Bundle permittedRole = this.additionalQuestionsContext.getContext().getObjectById(Bundle.class, getRoleId(request));
            // For a direct permitted role request, check existing assignments (there would be at least one) for an existing PermittedRoleAssignment.
            // If there are no assignments that permit this role (i.e.- allow the permit and have no PermittedRoleAssignment), 
            //   then the role is already a PermittedRoleAssignment for all permitting assignments.
            if (Util.isEmpty(LcmAccessRequestHelper.findAssignmentsPermittingRole(this.additionalQuestionsContext.getContext(), identity, permittedRole)))
                status = RequestedObjectStatus.CURRENTLY_ASSIGNED;
        }

        return status;
    }

    /**
     * Determines if requesting additional accounts is allowed
     * for the current user's population. For entitlement requests only.
     * @param identityId Identity making the request
     * @return true if requesting additional accounts is allowed, false otherwise
     * @throws GeneralException
     */
    private boolean isAdditionalAccountAllowed(String identityId) throws GeneralException {
        QuickLinkOptionsConfigService svc = new QuickLinkOptionsConfigService(this.additionalQuestionsContext.getContext());
        return svc.isRequestControlOptionEnabled(this.additionalQuestionsContext.getLoggedInUser(),
                this.additionalQuestionsContext.getLoggedInUserDynamicScopeNames(),
                this.additionalQuestionsContext.getQuickLink(),
                QuickLink.LCM_ACTION_REQUEST_ACCESS,
                Configuration.LCM_ALLOW_REQUEST_ENTITLEMENTS_ADDITIONAL_ACCOUNT_REQUESTS,
                isSelfService(identityId));
    }

    /**
     * Determines whether the application in the account request can have
     * additional accounts requested on it.
     * @param request AccountRequest in question.
     * @return true if the application in the AccountRequest supports additional account requests.
     * @throws GeneralException
     */
    private boolean isAdditionalAccountSupported(ProvisioningPlan.AccountRequest request)
            throws GeneralException {
        boolean supported = false;
        // Only return true if additional accounts were requested.
        Application app = request.getApplication(this.additionalQuestionsContext.getContext());
        if (app != null) {
            supported = app.isSupportsAdditionalAccounts();
        }
        return supported;
    }

    /**
     * Determines if the role is currently assigned to the identity or not. This is
     * not only driven by the roleAssignments list but also by the multiple role
     * assignment system configuration.
     *
     * @param roleId The role id.
     * @param identity The identity.
     * @return True if the identity has the role, false otherwise.
     * @throws GeneralException
     */
    private boolean isRoleCurrentlyAssigned(String roleId, Identity identity, boolean checkAllowMultipleAssignment)
            throws GeneralException {

        if (null == roleId || null == identity) {
            return false;
        }

        //We need the role object to use identity.getActiveRoleAssignments(role).
        Bundle role = this.additionalQuestionsContext.getContext().getObjectById(Bundle.class, roleId);
        
        // if active (non-negative) roleAssignments does not contain the role
        // then it is not currently assigned
        if (Util.isEmpty(identity.getActiveRoleAssignments(role))) {
            return false;
        }
        
        // If we don't want to consider multiple assignment, then this is assigned.
        // Otherwise it is only assigned if multiple assignment is disabled
        return !checkAllowMultipleAssignment || !isMultipleAssignmentAllowed(roleId);
    }

    /**
     * Check system and role configuration to see if multiple assignment of this role is allowed
     * @param roleId Role ID
     * @return True if multiple assignment is allowed, otherwise false.
     * @throws GeneralException
     */
    private boolean isMultipleAssignmentAllowed(String roleId) throws GeneralException {
        // contained in the assigned roles list so check configuration
        Configuration systemConfig = Configuration.getSystemConfig();

        // first check global flag, which overrides any per role setting
        boolean globalMultipleAssignment = systemConfig.getBoolean(Configuration.ALLOW_MULTIPLE_ROLE_ASSIGNMENTS, false);
        if (globalMultipleAssignment) {
            return true;
        }

        // global is not turned on so delegate to the role
        Bundle role = this.additionalQuestionsContext.getContext().getObjectById(Bundle.class, roleId);

        return role != null && role.isAllowMultipleAssignments();
    }

    /**
     * Determines if the identity has the specified entitlement.
     * Only true if the entitlement is assigned on all accounts.
     *
     * @param request The account request.
     * @param identity The identity id.
     * @return True if the identity has the entitlement, false otherwise.
     */
    private boolean isEntitlementCurrentlyAssigned(ProvisioningPlan.AccountRequest request, Identity identity) throws GeneralException {
        if (null == identity) {
            return false;
        }

        // Account creation requests are never duplicates.
        if (ProvisioningPlan.AccountRequest.Operation.Create.equals(request.getOperation())) {
            return false;
        }

        boolean isDuplicate = false;
        IdentityService idSvc = new IdentityService(this.additionalQuestionsContext.getContext());

        // The account request could have the native identity, if so, use that,
        // else look through all the links (possibly expensive, but not intractable).
        if (request.getNativeIdentity() != null) {
            Link l = idSvc.getLink(identity,
                    request.getApplication(this.additionalQuestionsContext.getContext()),
                    request.getInstance(),
                    request.getNativeIdentity());
            if (null != l) {
                isDuplicate = checkLinkForRequest(l, request);
            }
        } else {

            List<Link> links = idSvc.getLinks(identity,
                    request.getApplication(this.additionalQuestionsContext.getContext()),
                    request.getInstance());

            if (null != links && links.size() > 0) {
                isDuplicate = true;
                for (Link link : links) {
                    isDuplicate &= checkLinkForRequest(link, request);
                }
            }
        }

        return isDuplicate;
    }

    /**
     * Check the given link to see if it matches any of the attribute requests
     * inside of the given account requests.  If so return true, else false.
     * @param link (Link) The link we are looking at to see if it has matching attribute requests
     * @param acctReq (AccountRequest) the account request with the information about what is being requested
     * @return True if any of the attribute requests exist in the link
     * @throws GeneralException
     */
    boolean checkLinkForRequest(Link link, ProvisioningPlan.AccountRequest acctReq) throws GeneralException {
        List<ProvisioningPlan.AttributeRequest> attrReqs = acctReq.getAttributeRequests();
        if (null != attrReqs) {
            for (ProvisioningPlan.AttributeRequest attrReq : attrReqs) {
                if (ProvisioningPlan.Operation.Add.equals(attrReq.getOperation())) {
                    List linkVals = Util.asList(link.getAttribute(attrReq.getName()));
                    if (null != linkVals) {
                        List reqVals = Util.asList(attrReq.getValue());
                        if (linkVals.containsAll(reqVals)) {
                            return true;
                        }
                    }
                }
            }
        }

        List<ProvisioningPlan.PermissionRequest> permReqs = acctReq.getPermissionRequests();
        if (null != permReqs) {
            for (ProvisioningPlan.PermissionRequest permReq : permReqs) {
                if (ProvisioningPlan.Operation.Add.equals(permReq.getOperation())) {
                    List<Permission> linkPerms = link.getPermissions();
                    if (null != linkPerms) {
                        // Gather the rights from the link for the same target.
                        String reqTarget = permReq.getTarget();
                        List<String> linkRights = new ArrayList<String>();
                        for (Permission perm : linkPerms) {
                            if ((null != reqTarget) && reqTarget.equals(perm.getTarget())) {
                                linkRights.addAll(perm.getRightsList());
                            }
                        }

                        List<String> reqRights = permReq.getRightsList();
                        if (linkRights.containsAll(reqRights)) {
                            return true;
                        }
                    }
                }
            }
        }

        return false;
     }

    /**
     * Determines if the identity has a pending request for the specified role.
     *
     * @param identity The identity.
     * @param request  The AccountRequest.
     * @return True if there is a pending request, false otherwise.
     */
    private boolean hasPendingRoleRequest(Identity identity, AccountRequest request) throws GeneralException {
        if (null == request || null == identity) {
            return false;
        }

        CurrentAccessService currentAccessService =
                new CurrentAccessService(this.additionalQuestionsContext.getContext(), identity);
        return currentAccessService.hasPendingRoleRequest(request);
        
        // NOTE: Pending request check has historically not considered multiple assignment,
        //       and we will check it later for new UI, so no check is needed.
    }

    /**
     * Determines if the identity has a pending request for the specified
     * entitlement.
     *
     * @param request The account request.
     * @param identity The identity.
     * @return True if there is a pending request, false otherwise.
     */
    private boolean hasPendingEntitlementRequest(ProvisioningPlan.AccountRequest request, Identity identity)
            throws GeneralException {
        if (null == identity) {
            return false;
        }

        CurrentAccessService currentAccessService =
                new CurrentAccessService(this.additionalQuestionsContext.getContext(), identity);

        return currentAccessService.hasPendingEntitlementRequest(request);
    }

    /**
     * Determines if this is a self service request.
     *
     * @param identityId The identity id.
     * @return True if a self service request, false otherwise.
     * @throws GeneralException
     */
    public boolean isSelfService(String identityId) throws GeneralException {
        return (null != identityId) && identityId.equals(this.additionalQuestionsContext.getLoggedInUser().getId());
    }


    ////////////////////////////////////////////////////////////////////////////
    //
    // PERMITTED ROLE METHODS
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Get the permitted roles for the given role, if permitted roles are enabled.
     * @param role Role to find permits for
     * @param identityIds List of identity ids we are targeting
     * @return List of maps representing permitted roles, or null if not enabled 
     * @throws GeneralException
     */
    private List<Map<String, Object>> getPermittedRoles(Bundle role, List<String> identityIds) throws GeneralException {
        if (!isRequestPermittedRolesAllowed(identityIds)) {
            return null;
        }
        
        List<Bundle> permits = null;
        permits = RoleUtil.getFlattenedPermittedRoles(additionalQuestionsContext.getContext(), role, permits);
        List<Bundle> activePermits = new ArrayList<Bundle>();
        // Remove any disabled permitted roles
        if (!Util.isEmpty(permits)) {
            for (Bundle permit: permits) {
                if (!permit.isDisabled()) {
                    activePermits.add(permit);
                }
            }
        }

        Boolean classificationsEnabled = Configuration.getSystemConfig().getBoolean(Configuration.DISPLAY_CLASSIFICATIONS_IN_ACCESS_REQUEST);
        ClassificationService classificationService = null;
        if (classificationsEnabled) {
            classificationService = new ClassificationService(additionalQuestionsContext.getContext());
        }
        List<String> permittedRoleProperties = ensurePermittedRoleProperties(ID_COLUMN, DISPLAYABLE_NAME_COLUMN);


        List<Map<String, Object>> permitMaps = 
                ObjectUtil.objectsToMap(activePermits, permittedRoleProperties);

        // Add some extra stuff we always want, like description
        if (permitMaps != null) {
            Localizer localizer = new Localizer(this.additionalQuestionsContext.getContext());
            for (Map<String, Object> roleMap: permitMaps) {
                roleMap.put(ACCESS_TYPE_COLUMN, ACCESS_TYPE_ROLE);
                String id = (String)roleMap.get(ID_COLUMN);
                roleMap.put(Localizer.ATTR_DESCRIPTION,
                        localizer.getLocalizedValue(id, Localizer.ATTR_DESCRIPTION, this.additionalQuestionsContext.getLocale()));
                if (classificationsEnabled) {
                    List<String> displayNames = classificationService.getClassificationNames(Bundle.class, id);
                    roleMap.put(CLASSIFICATIONS_COLUMN, displayNames);
                }
            }
            Collections.sort(permitMaps, new MapListComparator(DISPLAYABLE_NAME_COLUMN, true));
        }
        
        return permitMaps;
    }

    /**
     * Make sure columns are in the permitted role columns list
     * @param additionalColumns Columns to ensure
     * @return List of properties including additional columns                         
     */
    private List<String> ensurePermittedRoleProperties(String... additionalColumns) throws GeneralException {
        List<String> properties = new ArrayList<String>(this.additionalQuestionsContext.getPermittedRoleProperties());
        for (String additionalColumn: additionalColumns) {
            if (!properties.contains(additionalColumn)) {
                properties.add(additionalColumn);
            }
        }
        return properties;
    }

    /**
     * Check LCM configuration to see if permitted roles are enabled based on the 
     * logged in user and target identity ids.
     * @param identityIds List of identity ids
     * @return True if enabled, otherwise false.
     * @throws GeneralException
     */
    private boolean isRequestPermittedRolesAllowed(List<String> identityIds) throws GeneralException {
        boolean isSelfService = (Util.size(identityIds) == 1) &&
                Util.nullSafeEq(identityIds.get(0), this.additionalQuestionsContext.getLoggedInUser().getId());

        return new LCMConfigService(this.additionalQuestionsContext.getContext()).isRequestPermittedRolesAllowed(isSelfService);
    }


    ////////////////////////////////////////////////////////////////////////////
    //
    // ACCOUNT SELECTION METHODS
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Initialize an LcmAccessRequestHelper based on the item and input
     */
    private LcmAccessRequestHelper initializeLcmAccessRequestHelper(SailPointObject accessItem, AdditionalQuestionsInput input) 
            throws GeneralException {
        boolean isRole = isRole(accessItem);

        String permittedById = null;
        Map<String,List<ProvisioningTarget>> targetsById = null;
        if (isRole) {
            permittedById = input.getPermittedById();
            targetsById = RequestedAccessItem.getProvisioningTargets(this.additionalQuestionsContext.getContext(), input.getOtherAddedRoles());
        }

        // Create the request for the item being requested.
        List<ProvisioningPlan.AccountRequest> accountRequests = new ArrayList<ProvisioningPlan.AccountRequest>();
        accountRequests.add(isRole ? createAccountRequest((Bundle)accessItem, permittedById, input.getAssignmentId()) : createAccountRequest((ManagedAttribute)accessItem));

        // Add account requests for all otherAddedRoles.
        accountRequests.addAll(createOtherAddedRoleAccountRequests(input));

        LcmAccessRequestHelper lcmAccessRequestHelper = new LcmAccessRequestHelper(this.additionalQuestionsContext.getContext(),
                this.additionalQuestionsContext.getLoggedInUser());
        lcmAccessRequestHelper.setIdentityIds(input.getIdentityIds());
        lcmAccessRequestHelper.setAccountRequests(accountRequests);
        lcmAccessRequestHelper.setExistingTargets(targetsById);
        lcmAccessRequestHelper.setQuickLink(input.getQuickLink());

        lcmAccessRequestHelper.compileProvisioningTargets();
        
        return lcmAccessRequestHelper;
    }
    
    /**
     * Get any required account selections for the role
     * @param accessItem Item being requested
     * @param input AdditionalQuestionsInput
     * @return List of IdentityAccountSelection items
     * @throws GeneralException
     */
    private List<IdentityAccountSelection> getAccountSelections(SailPointObject accessItem, AdditionalQuestionsInput input) 
            throws GeneralException {

        boolean isRole = isRole(accessItem);        
        List<IdentityAccountSelection> accountSelections = getLcmAccessRequestHelper(accessItem, input).getIdentityAccountSelections();
        if (!isRole) {
            // Need to fix the existing assignment to managed attribute display name from attribute value
            fixExistingAssignmentNames((ManagedAttribute)accessItem, accountSelections);
        }

        return accountSelections;
    }

    /**
     * Return a non-null List of AccountRequests for all of the otherAddedRoles in the given
     * input.
     */
    private List<AccountRequest> createOtherAddedRoleAccountRequests(AdditionalQuestionsInput input)
        throws GeneralException {

        List<AccountRequest> requests = new ArrayList<AccountRequest>();

        if (!Util.isEmpty(input.getOtherAddedRoles())) {
            for (RequestedRole requestedRole : input.getOtherAddedRoles()) {
                Bundle role =
                    this.additionalQuestionsContext.getContext().getObjectById(Bundle.class, requestedRole.getId());
                if (null == role) {
                    throw new GeneralException("Could not find role with ID - " + requestedRole.getId());
                }

                AccountRequest acctReq = createAccountRequest(role, requestedRole.getPermittedById());
                requests.add(acctReq);
            }
        }

        return requests;
    }

    /**
     * Create an AccountRequest from a requested Role. Required for LcmAccessRequestHelper
     * @param role Bundle being requested
     * @param permittedById The ID of the role that permits this role.
     * @return AccountRequest representing the role
     */
    private ProvisioningPlan.AccountRequest createAccountRequest(Bundle role, String permittedById) throws GeneralException {
        return createAccountRequest(role, permittedById, null);
    }
    
    /**
     * Create an AccountRequest from a requested Role. Required for LcmAccessRequestHelper
     * @param role Bundle being requested
     * @param permittedById The ID of the role that permits this role.
     * @param assignmentId The assignment ID, or null                     
     * @return AccountRequest representing the role
     */
    private ProvisioningPlan.AccountRequest createAccountRequest(Bundle role, String permittedById, String assignmentId) throws GeneralException {
        String name = role.getRoleTypeDefinition().isManuallyAssignable() ? ProvisioningPlan.ATT_IIQ_ASSIGNED_ROLES :
                ProvisioningPlan.ATT_IIQ_DETECTED_ROLES;
        AccountRequest request =
                createAccountRequest(ProvisioningPlan.APP_IIQ, ACCOUNT_REQUEST_TYPE_ROLE, name, 
                        role.getName(), permittedById, assignmentId);
        setRoleId(request, role.getId());
        return request;
    }

    /**
     * Create an AccountRequest from a requested Entitlement.
     * @param entitlement ManagedAttribute being requested
     * @return AccountRequest representing the entitlement
     */
    private ProvisioningPlan.AccountRequest createAccountRequest(ManagedAttribute entitlement) throws GeneralException {
       return createAccountRequest(entitlement.getApplication().getName(), ACCOUNT_REQUEST_TYPE_ENTITLEMENT, 
               entitlement.getAttribute(), entitlement.getValue(), null, null);
    }
    
    /**
     * A generic way of creating an AccountRequest (either role or entitlement)
     * @param application (String) The name of the applicatin the request is for.  (Set to IIQ for roles)
     * @param type (String) The type of request this is (either role or entitlement)
     * @param name (String) The name of the role or entitlement being requested
     * @param value (String) The value of the entitlement or name of the role being requested
     * @param permittedById The ID of the role permitting this role, or null if not applicable
     * @param assignmentId The assignment ID, or null                     
     * @return AccountRequest 
     */
    private ProvisioningPlan.AccountRequest createAccountRequest(String application, String type, String name, String value, 
                                                                 String permittedById, String assignmentId)
        throws GeneralException {

        ProvisioningPlan.AccountRequest accountRequest = new ProvisioningPlan.AccountRequest();
        accountRequest.setApplication(application);
        accountRequest.setOperation(ProvisioningPlan.AccountRequest.Operation.Modify);
        accountRequest.setTrackingId(Util.uuid());
        accountRequest.setType(type);

        ProvisioningPlan.AttributeRequest attributeRequest = new ProvisioningPlan.AttributeRequest();
        attributeRequest.setName(name);
        attributeRequest.setValue(value);
        attributeRequest.setOperation(ProvisioningPlan.Operation.Add);
        attributeRequest.setAssignmentId(assignmentId);

        if (null != permittedById) {
            Bundle role = this.additionalQuestionsContext.getContext().getObjectById(Bundle.class, permittedById);
            if (null == role) {
                throw new GeneralException("Expected to find a role for " + permittedById);
            }
            attributeRequest.put(ProvisioningPlan.ARG_PERMITTED_BY, role.getName());
        }

        accountRequest.add(attributeRequest);
        return accountRequest;
    }

    /**
     * Set the existing assignment names on accounts to the managed attribute display name
     * @param accessItem ManagedAttribute 
     * @param accountSelections List of IdentityAccountSelection objects
     */
    private void fixExistingAssignmentNames(ManagedAttribute accessItem, List<IdentityAccountSelection> accountSelections) {
        for (IdentityAccountSelection accountSelection : Util.safeIterable(accountSelections)) {
            accountSelection.setExistingAssignmentValues(accessItem.getDisplayableName());
        }
    }

    /**
     * Convenience method for getting the role id off of the account request.
     * @return (String) The id of the role
     */
    private static String getRoleId(AccountRequest request) {
        if (request!=null) {
            return (String) request.getArgument("id");
        }
        return null;
    }

    private static void setRoleId(AccountRequest request, String roleId) {
        if (request!=null) {
            request.addArgument("id", roleId);
        }
    }

    ////////////////////////////////////////////////////////////////////////////
    //
    // AMBIGUOUS ROLE ASSIGNMENT METHODS
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Get a list of ambiguous assigned roles that need to be selected from by the requesting user.
     * If this is not a direct permitted role request, or there is only a single permitting role assignment
     * on the identity, no roles will be returned, since no selection is necessary.
     * @param role Permitted role being selected
     * @param identityIds List of identityIds being targeted
     * @param permittedById The ID of the role that permits this role, or null
     * @return List of AssignedRole objects if selection is needed, or null
     */
    private List<AssignedRole> getAmbiguousAssignedRoles(Bundle role, List<String> identityIds, String permittedById)
            throws GeneralException {
        
        // Nothing to do for bulk requests, top level permitted roles wont show up anyway
        if (role == null || Util.size(identityIds) != 1) {
            return null;
        }

        AccountRequest accountRequest = createAccountRequest(role, permittedById);
        if (!LcmAccessRequestHelper.isDirectPermittedRoleRequest(accountRequest)) {
            return null;
        }

        Identity identity =
                this.additionalQuestionsContext.getContext().getObjectById(Identity.class, identityIds.get(0));

        // We want to exclude hard permits here, since only permitting roles with no existing hard permit
        // should be returned.
        List<RoleAssignment> permittingAssignments =
                LcmAccessRequestHelper.findAssignmentsPermittingRole(this.additionalQuestionsContext.getContext(),
                        identity, role);

        List<AssignedRole> assignedRoles = null;
        if (Util.size(permittingAssignments) > 1) {
            assignedRoles = new ArrayList<AssignedRole>();
            for (RoleAssignment roleAssignment: permittingAssignments) {
                assignedRoles.add(new AssignedRole(roleAssignment));
            }
        } else if (Util.size(permittingAssignments) == 0) {
            /* No assignments permit this role. Shouldn't ever get here, role should
             * never be available for selection.*/
            log.warn("No permitting RoleAssignments exist for direct permitted role: " + role.getDisplayableName());
        }
        
        return assignedRoles;
    }
}
