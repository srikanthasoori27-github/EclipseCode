package sailpoint.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.stream.Collectors;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.IdentityLifecycler;
import sailpoint.api.IncrementalObjectIterator;
import sailpoint.api.ObjectUtil;
import sailpoint.api.SailPointContext;
import sailpoint.api.SearchResultsIterator;
import sailpoint.api.WorkflowSession;
import sailpoint.object.AccessRequestAccountInfo;
import sailpoint.object.AccountSelection;
import sailpoint.object.Application;
import sailpoint.object.ApprovalItem;
import sailpoint.object.ApprovalSet;
import sailpoint.object.Attachment;
import sailpoint.object.Attributes;
import sailpoint.object.Bundle;
import sailpoint.object.Configuration;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.IdentityEntitlement;
import sailpoint.object.IdentityRequestItem;
import sailpoint.object.ManagedAttribute;
import sailpoint.object.ObjectConfig;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.ProvisioningPlan.AccountRequest;
import sailpoint.object.ProvisioningPlan.AttributeRequest;
import sailpoint.object.ProvisioningPlan.ObjectOperation;
import sailpoint.object.ProvisioningPlan.Operation;
import sailpoint.object.ProvisioningTarget;
import sailpoint.object.QueryOptions;
import sailpoint.object.QuickLink;
import sailpoint.object.RoleAssignment;
import sailpoint.object.RoleDetection;
import sailpoint.object.RoleTypeDefinition;
import sailpoint.object.Rule;
import sailpoint.object.Source;
import sailpoint.object.TaskResult;
import sailpoint.object.WorkItem;
import sailpoint.object.Workflow;
import sailpoint.object.WorkflowCase;
import sailpoint.object.WorkflowLaunch;
import sailpoint.provisioning.PlanUtil;
import sailpoint.service.quicklink.QuickLinkOptionsConfigService;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.UserContext;
import sailpoint.web.accessrequest.AccessItem;
import sailpoint.web.accessrequest.AccessRequest;
import sailpoint.web.accessrequest.RemovedEntitlement;
import sailpoint.web.accessrequest.RemovedRole;
import sailpoint.web.accessrequest.RequestedEntitlement;
import sailpoint.web.accessrequest.RequestedRole;
import sailpoint.web.lcm.IdentityAccountSelection;
import sailpoint.web.lcm.LcmAccessRequestHelper;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.workitem.ViolationReviewWorkItemDTO;
import sailpoint.workflow.IdentityRequestLibrary;

/**
 * A service layer object for fielding access request submits in the mobile UI.
 */
public class RequestAccessService implements AdditionalQuestionsContext
{
    /**
     * Parameter that is included on the url when requests are made from
     * an external management system like BMC SRM or ServiceNow launcher.
     *
     * The idea here is to allow these systems to pass the name
     * of the application they want to manage tickets for
     * request data synchonrization purposes.  When configuered
     * this attribute be set on the user's the session and 
     * used when workflows are launched through LCM.
     */
    public static final String ATT_TICKET_MANAGEMENT_APPLICATION = "ticketManagementApplication";
    /**
     * Parameter that is included on the url when requests are made from
     * an external management system like BMC SRM or ServiceNow launcher.
     *
     * The idea here is to allow them to pass the source we want fed
     * into the workflows when they are launched to help give 
     * extra context to the request. 
     */
    public static final String ATT_EXTERNAL_SOURCE = "externalSource";

    /**
     * Access Request flow config name.
     */
    public static final String FLOW_CONFIG_NAME = "AccessRequest";

    ////////////////////////////////////////////////////////////////////////////
    //
    // INNER CLASSES
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Abstract class to get inputs necessary for unique assignment check
     */
    public abstract static class UniqueAssignmentInput {

        /**
         * Get the ID of the role
         */
        public abstract String getRoleId();

        /**
         * Get the ID of the role permitting this one, or null.
         */
        public abstract String getPermittedById();

        /**
         * Get the assignment ID selected to assign this role, or null
         */
        public abstract String getAssignmentId();

        /**
         * Get the account selection information 
         */
        public abstract List<AccessRequestAccountInfo> getAccountSelections();

        /**
         * Get the Identity to validate this account selections against
         * @param context SailPointContext
         * @return Identity object               
         */
        public Identity getIdentity(SailPointContext context) throws GeneralException {
            Identity identity = null;
            if (!Util.isEmpty(getAccountSelections())) {
                String identityId = getAccountSelections().get(0).getIdentityId();
                identity = context.getObjectById(Identity.class, identityId);
            }
            return identity;
        }
        
        /**
         * Get the top level assignable role name from the UniqueAssignmentInput.
         * @param context SailPointContext
         * @return String role name
         * @throws GeneralException If any role cannot be found or role name cannot be determined.
         */
        public String getRoleName(SailPointContext context) throws GeneralException {
            Bundle role = getRole(context, getRoleId());

            String roleName = null;
            if (role.getRoleTypeDefinition().isManuallyAssignable()) {
                // Not a permitted role, just use the role we got
                roleName = role.getName();
            } else {
                if (getPermittedById() != null) {
                    // Permitted by specified from permitting role as part of the request, load up the role name
                    Bundle permittingRole = getRole(context, getPermittedById());
                    roleName = permittingRole.getName();
                } else {
                    Identity identity = getIdentity(context);
                    if (getAssignmentId() != null) {
                        // Assignment ID specified from assignment selection, find the assignment
                        RoleAssignment assignment = identity.getRoleAssignmentById(getAssignmentId());
                        if (assignment != null) {
                            roleName = assignment.getRoleName();
                        }
                    } else {
                        // If we get here, should be only a single assignment, otherwise we would
                        // have had an assignment ID after selection
                        // IIQSR-53 - Don't include hard permits when checking available assignments;
                        // otherwise we'll conflict with previous processing and may get more than one returned.
                        List<RoleAssignment> assignments =
                                LcmAccessRequestHelper.findAssignmentsPermittingRole(context, identity, role);
                        if (Util.size(assignments) == 1) {
                            roleName = assignments.get(0).getRoleName();
                        } else if (Util.size(assignments) == 0) {
                            /* No assignments permit this role. Shouldn't ever get here, role should
                             * never be available for selection. Just throw. */
                            throw new GeneralException("No permitting RoleAssignments exist for direct permitted role request");
                        }
                    }
                }
            }

            // We can't do anything without a role name, so throw
            if (roleName == null) {
                throw new GeneralException("Unable to find role name from inputs");
            }

            return roleName;
        }

        private Bundle getRole(SailPointContext context, String id) throws GeneralException {
            Bundle bundle = context.getObjectById(Bundle.class, id);
            if (bundle == null) {
                throw new GeneralException("Unable to find bundle with ID: " + id);
            }
            return bundle;
        }
    }


    /**
     * Base class for exceptions thrown when submitting a request that require special handling by the UI.
     */
    @SuppressWarnings("serial")
    public static class RequestAccessSubmitException extends GeneralException {

        private String type;
        
        /**
         * Constructor.
         *
         * @param type the type.
         */
        public RequestAccessSubmitException(String type) {
            this.type = type;
        }

        /**
         * @return The type.
         */
        public String getType() {
            return this.type;
        }
    }

    /**
     * Exception that is thrown when submitting a request that has missing account
     * selections that are required.
     */
    @SuppressWarnings("serial")
    public static class MissingAccountSelectionsException extends RequestAccessSubmitException {

        static final String TYPE = "missingAccountSelectionsException";
        
        private Map<String,List<IdentityAccountSelection>> accountSelections;

        /**
         * Constructor.
         *
         * @param  selections  The missing selections, keyed by access item ID.
         */
        public MissingAccountSelectionsException(Map<String,List<IdentityAccountSelection>> selections) {
            super(TYPE);
            this.accountSelections = selections;
        }

        /**
         * @return A Map of missing account selections that maps role or managed
         * attribute ID to a list of account selections that are missing for that
         * access item.
         */
        public Map<String,List<IdentityAccountSelection>> getAccountSelections() {
            return this.accountSelections;
        }
    }

    /**
     * Exception that is thrown when submitting a request that has items that are already assigned or pending.
     */
    @SuppressWarnings("serial")
    public static class DuplicateAssignmentException extends RequestAccessSubmitException {

        static final String TYPE = "duplicateAssignmentException";

        private List<String> requestItemNames;

        /**
         * Constructor.
         *
         * @param requestItemNames The duplicate item names.
         */
        public DuplicateAssignmentException(List<String> requestItemNames) {
            super(TYPE);
            this.requestItemNames = requestItemNames;
        }

        /**
         * @return A List of names of duplicate assigned items.
         */
        public List<String> getRequestItemNames() {
            return this.requestItemNames;
        }
    }

    /**
     * Exception that is thrown when a remove access request is invalid due to current permissions.
     */
    public static class InvalidRemoveException extends RequestAccessSubmitException {

        static final String TYPE = "invalidRemoveException";

        public InvalidRemoveException() {
            super(TYPE);
        }
    }

    /**
     * Exception thrown when an attachment is required but its not possible to provide one
     */
    public static class AttachmentRequiredException extends  RequestAccessSubmitException {

        static final String TYPE = "attachmentRequiredException";

        public AttachmentRequiredException() { super(TYPE); }
    }

    /**
     * Exception thrown when a comment is required but its not possible to provide one
     */
    public static class CommentRequiredException extends RequestAccessSubmitException {
        static final String TYPE = "commentRequiredException";
        public CommentRequiredException() { super(TYPE); }
    }

    ////////////////////////////////////////////////////////////////////////////
    //
    // VARIABLES
    //
    ////////////////////////////////////////////////////////////////////////////

    private static Log log = LogFactory.getLog(RequestAccessService.class);

    private UserContext userContext;

    private String quickLinkName;


    ////////////////////////////////////////////////////////////////////////////
    //
    // CONSTRUCTOR
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Create a request access service.
     * @param userContext the user context
     */
    public RequestAccessService(UserContext userContext)
    {
        this.userContext = userContext;
    }


    ////////////////////////////////////////////////////////////////////////////
    //
    // METHODS
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Submit an access request represented by request map.
     *               
     * @param accessRequest the {@link AccessRequest} object
     *
     * @return A list containing the SubmitResultItems - one per user in the request.
     *
     * @throws MissingAccountSelectionsException  If there are any missing account
     *    selections.
     * @throws GeneralException  If there are any unhandled problems.
     */
    public List<AccessRequestResultItem> submitRequest(AccessRequest accessRequest, SessionStorage storage) throws GeneralException
    {
        LcmAccessRequestHelper requestHelper = null;

        // Make sure our attachments are in order before we get started.
        validateAttachments(accessRequest);

        // Make sure we have required comments
        validateRequiredComments(accessRequest);

        // 1. Convert add requests to AccountRequests once
        List<AccountRequest> addRoleRequests = createAddRoleRequests(accessRequest);
        List<AccountRequest> addEntitlementRequests = createAddEntitlementRequests(accessRequest);
        List<AccountRequest> allAddRequests = new ArrayList<AccountRequest>();
        allAddRequests.addAll(addRoleRequests);
        allAddRequests.addAll(addEntitlementRequests);
        boolean hasAddRequests = !Util.isEmpty(allAddRequests);

        if (hasAddRequests) {
            // Set up request helper; duplicate assignments and missing account selections will be checked here.
            requestHelper = createRequestHelper(accessRequest, allAddRequests);
        }

        // If this is a single identity request, we need to get the external integration variables (for things like
        // ServiceNow) to set in the workflow args. Ideally we'd want these to get inserted into the AccessRequest 
        // to avoid having to go to the session, but these get pulled out of the index URL in the 
        // PageAuthenticationFilter when a remote login token exists and there's no other way to do it.
        Attributes<String,Object> args = new Attributes<String,Object>();
        if (accessRequest.getIdentityIds().size() == 1 && storage != null) {

            // These will come along during the login process when we are launch from
            // external management system like BMC SRM.
            String externalSource = (String)storage.get(ATT_EXTERNAL_SOURCE);
            if ( externalSource != null ) {
                args.put(ATT_EXTERNAL_SOURCE, externalSource);
            }
            String ticketApp = (String)storage.get(ATT_TICKET_MANAGEMENT_APPLICATION);
            if ( ticketApp != null ) {
                args.put(ATT_TICKET_MANAGEMENT_APPLICATION, ticketApp);
            }
        }

        List<AccessRequestResultItem> resultItems = new ArrayList<AccessRequestResultItem>();
        IncrementalObjectIterator<Identity> identityIterator = new IncrementalObjectIterator<Identity>(getContext(), Identity.class, accessRequest.getIdentityIds());

        while (identityIterator.hasNext()) {
            Identity identity = identityIterator.next();

            List<AccountRequest> accountRequests = createRemoveRequests(accessRequest, identity);
            List<ProvisioningTarget> provisioningTargets = null;
            if (hasAddRequests) {
                accountRequests.addAll(addRoleRequests);
                accountRequests.addAll(requestHelper.getEntitlementAccountRequestsWithSelections(identity.getId()));
                provisioningTargets = requestHelper.getProvisioningTargetsForIdentity(identity.getId());
            }
            
            ProvisioningPlan plan = createPlan(identity, /*comments*/null, accountRequests);
            if (plan == null) {
                continue;
            }
            plan.setProvisioningTargets(provisioningTargets);

            String workflow = ObjectUtil.getLCMWorkflowName(getContext(), FLOW_CONFIG_NAME);
            
            args.put("flow", FLOW_CONFIG_NAME);
            args.put(Workflow.ARG_WORK_ITEM_PRIORITY, accessRequest.getPriority());

            if(log.isDebugEnabled()) {
                log.debug("Executing workflow for plan: " + plan.toMap());
            }

            WorkflowSession wfSession = runWorkflow(workflow, args, plan);
            
            // Assuming no synchronous forms for now.
            if (null != storage) {
                WorkflowSessionService sessionService = 
                        new WorkflowSessionService(userContext.getContext(), storage, wfSession);
                sessionService.save();
            }
            wfSession.save(getContext());
            resultItems.add(getResultItem(wfSession));
            getContext().decache();
        }

        return resultItems;
    }

    /**
     * Check for duplicate assignments, set up request helper, and check for missing account selections.
     * @param accessRequest the access request
     * @param allAddRequests list of add requests
     * @return the request helper
     */
    public LcmAccessRequestHelper createRequestHelper(AccessRequest accessRequest, List<AccountRequest> allAddRequests) throws GeneralException {
        LcmAccessRequestHelper requestHelper = null;
        if (!Util.isEmpty(allAddRequests)) {
            Map<String, List<ProvisioningTarget>> requestTargets = accessRequest.getAccountSelections(getContext());

            // This will check request target accounts for validity as well as check for missing account selections
            requestHelper = compileRequestTargets(accessRequest, allAddRequests, requestTargets);

            checkDuplicateAddRequests(accessRequest.getIdentityIds(), allAddRequests, requestHelper);
        }
        return requestHelper;
    }

    /**
     * Generates a SubmitResultItem from the WorkflowSession data.
     *
     * @param wfSession The session to pull data from
     * @return SubmitResultItem with data populated from the WorkflowSession
     * @throws GeneralException
     */
    public AccessRequestResultItem getResultItem(WorkflowSession wfSession) throws GeneralException {

        if (wfSession != null) {
            WorkflowLaunch wfLaunch = wfSession.getWorkflowLaunch();
            WorkflowCase wfCase = wfSession.getWorkflowCase();
            WorkItem wfWorkItem = wfSession.getWorkItem();

            String status = wfLaunch == null ? null : wfLaunch.getStatus();
            String requestId = getIdentityRequestId(wfCase);
            List<Message> messages = wfSession.getLaunchMessages();
            /* Values that depend on existing workitem default to null */
            String workItemType = null;
            String workItemId = null;
            ApprovalSet wfApprovalSet = null;
            boolean allowViolations = false;
            boolean requireViolationComment = false;
            if (wfWorkItem != null) {
                workItemType = wfWorkItem.getType().toString();
                workItemId = wfWorkItem.getId().toString();
                wfApprovalSet = wfWorkItem.getApprovalSet();
                allowViolations = wfWorkItem.getAttributes().getBoolean(ViolationReviewWorkItemDTO.REVIEW_ITEM_ALLOW_VIOLATIONS);
                requireViolationComment = wfWorkItem.getAttributes().getBoolean(ViolationReviewWorkItemDTO.REQUIRE_VIOLATION_COMMENT);
            }

            return new AccessRequestResultItem(status, requestId, workItemType, workItemId, allowViolations, requireViolationComment,
                    getApprovalItems(wfApprovalSet), messages);

        }
        return null;
    }

    /**
     * Returns a list of maps where each map contains an approvalItemId/requestItemId pairing
     *
     * @param wfApprovalSet approval set from workitem
     * @return list of maps where each map contains an approvalItemId/requestItemId pairing
     */
    private List<Map<String, String>> getApprovalItems(ApprovalSet wfApprovalSet)
    {
        List<Map<String, String>> approvalItems = new ArrayList<Map<String, String>> ();
        if (wfApprovalSet != null) {
            List<ApprovalItem> items = wfApprovalSet.getItems();
            for (ApprovalItem item :  Util.safeIterable(items)) {
                String approvalItemId = item.getId();
                String requestItemId = item.getAttribute("id") != null ? item.getAttribute("id").toString() : "";
                Map<String, String> approvalItem = new HashMap<String, String> ();
                approvalItem.put("approvalItemId", approvalItemId);
                approvalItem.put("requestItemId", requestItemId);
                // bug26554 - if there is no requestItemId don't add it
                if (Util.isNotNullOrEmpty(requestItemId)) {
                    approvalItems.add(approvalItem);
                }
             }
        }
        return approvalItems;
    }
    
    /**
     * Returns true if the passed assignment is unique
     * @param input UniqueAssignmentInput with role information
     * @return True if assignment is unique otherwise false
     */
    public boolean isUniqueAssignment(UniqueAssignmentInput input) throws GeneralException {
        return !hasExistingAssignment(input.getIdentity(getContext()), 
                input.getAccountSelections(), input.getRoleName(getContext()));
    }

    /**
     * This is called from isUniqueAssignment.  There should only ever be one AttributeRequest on the
     * passed AccountRequest, but just to be sure iterate through the list
     * @param createdRequest Request to get the role name from
     * @return Name of the role or null if role name not found
     */
    private String getAssignedRoleNameFromAccountRequest(AccountRequest createdRequest) {
        String roleName = null;
        for (AttributeRequest attributeRequest : createdRequest.getAttributeRequests()) {
            if(attributeRequest.getName().equals(ProvisioningPlan.ATT_IIQ_ASSIGNED_ROLES)) {
                roleName = (String) attributeRequest.getValue();
            }
        }
        return roleName;
    }

    /**
     * Checks that the passed assignment is unique.  Used by classic UI
     * @param createdRequests Account requests
     * @param accountInfoList Account Info for selected accounts
     * @return list of identities with non-unique assignments
     */
    public List<String> checkUniqueAssignment(List<AccountRequest> createdRequests, List<AccessRequestAccountInfo> accountInfoList) throws GeneralException {
        List<String> dups = new ArrayList<String>();

        /* If no requests or account selections then there are no duplicates */
        if (Util.isEmpty(accountInfoList) || Util.isEmpty(createdRequests)) {
            return dups;
        }

        /* Building a map of identity ids to list of associated account infos so that later we can
         * determine on a per identity case if an assignement is unique or not. */
        Map<String, List<AccessRequestAccountInfo>> acctInfoMap = new HashMap<String, List<AccessRequestAccountInfo>>();
        for (AccessRequestAccountInfo acctInfo : accountInfoList) {
            String id = acctInfo.getIdentityId();
            List<AccessRequestAccountInfo> value = acctInfoMap.get(id);
            if (value == null) {
                value = new ArrayList<AccessRequestAccountInfo>();
                acctInfoMap.put(id, value);
            }
            value.add(acctInfo);
        }

        /* Get the assigned role's name for this request */
        String assignedRoleName = getAssignedRoleName(createdRequests);

        /* Iterate over the identity ids in the map and determine if existing assignments exist */
        for (String identityId : acctInfoMap.keySet()) {
            List<AccessRequestAccountInfo> acctInfos = acctInfoMap.get(identityId);
            Identity identity = getContext().getObjectById(Identity.class, identityId);
            boolean hasSameAssignment =
                    hasExistingAssignment(identity, acctInfos, assignedRoleName) &&
                            hasRequestedDetectedRole(createdRequests, identity.getRoleAssignments(assignedRoleName));
            if (hasSameAssignment) {
                dups.add(identity.getDisplayableName());
            }
        }

        return dups;
    }

    /**
     * Returns first assigned role name from the passed request
     * @param createdRequests Requests to inspect
     * @return role name or null if no assignedRoles request found
     */
    private String getAssignedRoleName(List<AccountRequest> createdRequests) {
        for (AccountRequest acctReq : createdRequests) {
            return getAssignedRoleNameFromAccountRequest(acctReq);
        }
        return null;
    }

    /**
     * Returns true is an existing role assignment for the passed selections exists
     * @param identity The identity to inspect
     * @param accountInfos The account selections
     * @param roleName The role requested
     * @return True is match assignement exists on identity.  Otherwise false.
     */
    private boolean hasExistingAssignment(Identity identity, List<AccessRequestAccountInfo> accountInfos, String roleName)
            throws GeneralException {
        /* Returning false here for what seems like an error condition for compatibility reasons */
        if (identity == null || roleName == null) {
            return false;
        }

        CurrentAccessService currentAccessService = new CurrentAccessService(getContext(), identity);

        // only get the active role assignments, i.e. those that are not negative
        List<RoleAssignment> assignments = identity.getActiveRoleAssignments(roleName);

        /* Returning false if for any of the account selections no matching assignment or pending request is found */
        for (AccessRequestAccountInfo acctInfo : accountInfos) {
            if (Util.isEmpty(assignments) || !acctInfo.isInAssigments(assignments)) {
                // Check pending. 
                List<String> pendingAccounts = currentAccessService.getPendingRoleRequestAccounts(roleName, acctInfo.getApplicationName(), acctInfo.getRoleName());
                if (!pendingAccounts.contains(acctInfo.getNativeIdentity())) {
                    return false;
                }
            }
        }
        /* All account selections found in either an assignment or a pending request */
        return true;
    }
    
    /**
     * Check for requested detected role that do not have account selections
     *
     * Original comment:
     * check any new account which does not have account info this happens
     * when a permitted role requires a new account, but there is no accountInfo
     *
     * @param createdRequests Account reqeusts
     * @param assignments Assignments to search
     * @return True if no assignment for detected role is present, otherwise false
     */
    private boolean hasRequestedDetectedRole(List<AccountRequest> createdRequests, List<RoleAssignment> assignments) {
        for (AccountRequest acctReq : createdRequests) {
            if (!Util.isEmpty(acctReq.getAttributeRequests())) {
                AttributeRequest attr = acctReq.getAttributeRequests().get(0);

                String name = attr.getName();
                if ("detectedRoles".equalsIgnoreCase(name)) {
                    String detectedRoleName = (String) attr.getValue();
                    boolean hasPermittedRoleTarget = hasPermittedRoleTarget(detectedRoleName, assignments);
                    if (!hasPermittedRoleTarget) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private boolean hasPermittedRoleTarget(String detectedRoleName, List<RoleAssignment> assignments) {
        if (Util.isEmpty(assignments)) {
            return false;
        }

        for (RoleAssignment assignment : assignments) {
            for (RoleAssignment permitted : Util.safeIterable(assignment.getPermittedRoleAssignments())) {
                if (Util.nullSafeEq(detectedRoleName, permitted.getRoleName())) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Creates a list of Account Requests for the specified roles and operation.
     * @param roles list of requested roles
     * @param op the operation
     * @return the list of account requests
     * @throws GeneralException
     */
    protected List<AccountRequest> createRoleAccountRequests(List<RequestedRole> roles, Operation op) throws GeneralException {
        List<AccountRequest> accountRequests = new ArrayList<AccountRequest>();
        if (Util.isEmpty(roles)) {
            return accountRequests;
        }
        
        /* Mapping requested role id to requested role */
        Map<String, RequestedRole> requestedRoleMap = new HashMap<String, RequestedRole>();
        /* Mapping permitted role ids to the id of the roles that permit them*/
        Map<String, String> permissionMap = new HashMap<String, String>();
        /* Mapping the ids of the permitting roles to respective name*/
        Map<String, String> permittingNameMap = new HashMap<String, String>();

        /* Build up a list of roles we are requesting.  If the role is permitted add it and
         * the permitting role to the permission map. */
        for (RequestedRole role : roles) {
            requestedRoleMap.put(role.getId(), role);
            if (role.getPermittedById() != null) {
                permissionMap.put(role.getId(), role.getPermittedById());
            }
        }

        /* Fetch all the permitting role names up front.  It is not guaranteed that
         * permitting roles will come before the roles they permit nor that the
         * permitting role is even part of this request */
        List<String> properties = Arrays.asList("id", "name");
        SearchResultsIterator permittingRolesIter = ObjectUtil.searchAcrossIds(getContext(), Bundle.class,
                 new ArrayList<String>(permissionMap.values()), null, properties);

        while(permittingRolesIter.hasNext()) {
            Object[] permittingRolesItems= permittingRolesIter.next();
            permittingNameMap.put((String) permittingRolesItems[0], (String) permittingRolesItems[1]);
        }
        /* Fetch the ObjectConfig for Roles so that we can determine if this is a
         * assignable role or a detected role. */
        ObjectConfig roleConfig = getContext().getObjectByName(ObjectConfig.class, ObjectConfig.ROLE);

        /* The meat of the matter.  Get id, name, and type for each requested role.  Then call down to
         * createRoleAccountRequest with appropriate parameters  */
        properties = Arrays.asList("id", "name", "type");
        SearchResultsIterator iter = 
                ObjectUtil.searchAcrossIds(getContext(), Bundle.class, new ArrayList<String>(requestedRoleMap.keySet()), null, properties);
        while (iter.hasNext()) {
            Object[] row = iter.next();
            String roleId = (String) row[0];
            String roleName = (String) row[1];
            String typeName = (String) row[2];
            String permittingRoleName = permittingNameMap.get(permissionMap.get(roleId));
            RoleTypeDefinition roleType = roleConfig.getRoleType(typeName);
            String requestType = roleType.isManuallyAssignable() ? ProvisioningPlan.ATT_IIQ_ASSIGNED_ROLES : ProvisioningPlan.ATT_IIQ_DETECTED_ROLES;

            RequestedRole requestedRole = requestedRoleMap.get(roleId);
            accountRequests.add(createRoleAccountRequest(requestedRole, null, requestType, roleName, op, /*identityId*/null,
                    permittingRoleName, requestedRole.getComment()));
        }
        return accountRequests;
    }

    /**
     * Creates a list of Remove Role Account Requests for the specified role assignments and identity.
     * @param removedRoleRequests the list of role removal requests
     * @param identityId the identity
     * @return the list of account requests
     * @throws GeneralException
     */
    protected List<AccountRequest> createRemoveRoleAccountRequests(List<RemovedRole> removedRoleRequests, String identityId) throws GeneralException
    {
        // Get the affected role assignments from the identity.
        Identity identity = getContext().getObjectById(Identity.class, identityId);

        List<AccountRequest> accountRequests = new ArrayList<AccountRequest>();
        for (RemovedRole removedRole : removedRoleRequests ) {
            String roleLocation = removedRole.getRoleLocation();
            String assignmentId = removedRole.getAssignmentId();
            String roleId = removedRole.getId();
            String roleName = null;
            
            if (assignmentId == null) {
                // If this is an assigned role removal, the assignment id is required so this is an error
                if (RemovedRole.ASSIGNED_LOCATION.equals(roleLocation)) {
                    throw new GeneralException("Cannot have null assignment id when removing an assigned role.");
                }
                
                // Otherwise his is possibly a soft permit, so get the role name straight from the bundle.
                roleName = ObjectUtil.getName(getContext(), Bundle.class, roleId);
            }
            else {
                // This is a normal role assignment or role detection, so get the name from there.
                if (RemovedRole.ASSIGNED_LOCATION.equals(roleLocation)) {
                    RoleAssignment assignment = identity.getRoleAssignmentByAssignmentAndRoleId(assignmentId, roleId);
                    if (assignment == null) {
                        throw new GeneralException("Assignment " + assignmentId + " not found for identity " + identity);
                    }

                    // IIQCB-1864 go look up the role to get the name instead of using the
                    // role assignment/detection in case the role name has changed.
                    roleName = ObjectUtil.getName(getContext(), Bundle.class, roleId);
                    removedRole.setNeedsNegativeAssignment(!assignment.isManual());
                }

                if (RemovedRole.DETECTED_LOCATION.equals(roleLocation)) {
                    RoleDetection detection = identity.getRoleDetection(assignmentId, roleId);
                    if (detection == null) {
                        throw new GeneralException("Detection " + assignmentId + " for role " + roleId + " not found for identity " + identity);
                    }

                    // IIQCB-1864 go look up the role to get the name instead of using the
                    // role assignment/detection in case the role name has changed.
                    roleName = ObjectUtil.getName(getContext(), Bundle.class, roleId);
                }
            }

            // Create the account request to remove the role.
            accountRequests.add(createRoleAccountRequest(null, removedRole, getAttributeRequestName(roleLocation), roleName,
                    Operation.Remove, assignmentId, /*permittingName*/null, removedRole.getComment()));
        }

        return accountRequests;
    }


    /**
     * Creates a single Account Requests for the specified role and operation.
     * @param requestedRole the role request; can be null for remove requests 
     * @param requestName the request name, either ProvisioningPlan.ATT_IIQ_ASSIGNED_ROLES or ProvisioningPlan.ATT_IIQ_DETECTED_ROLES
     * @param roleName the role name
     * @param op the operation
     * @param assignmentId the role assignment id; only required for Remove operations, otherwise it is ignored and can be null
     * @param permittingName the name of the role that permits this role.  Ignored if null.
     * @param comment the comment; can be null                      
     * @return the account request
     * @throws GeneralException
     */
    protected AccountRequest createRoleAccountRequest(RequestedRole requestedRole, RemovedRole removedRole, String requestName, String roleName,
                                                      Operation op, String assignmentId, String permittingName, String comment) throws GeneralException
    {
        AccountRequest accountRequest = new AccountRequest();
        accountRequest.setOp(ObjectOperation.Modify);
        accountRequest.setApplication(ProvisioningPlan.APP_IIQ);

        boolean needsNegativeAssignment = removedRole != null ? removedRole.getNeedsNegativeAssignment() : false;

        AttributeRequest attrRequest = createRoleAttributeRequest(requestName, roleName, op, assignmentId,
                permittingName, comment, needsNegativeAssignment);
        if (requestedRole != null) {
            attrRequest.setAddDate(requestedRole.getSunrise());
            attrRequest.setRemoveDate(requestedRole.getSunset());
            attrRequest.setAssignmentId(requestedRole.getAssignmentId());
            if (Operation.Add.equals(op)) {
                attrRequest.put(ProvisioningPlan.ARG_ASSIGNMENT_NOTE, requestedRole.getAssignmentNote());
            }
            accountRequest.addArgument("id", requestedRole.getId());
            accountRequest.addArgument(AccountRequest.ATTACHMENTS, requestedRole.getAttachments());
            accountRequest.addArgument(AccountRequest.ATTACHMENT_CONFIG_LIST, requestedRole.getAttachmentConfigList());
        } else if (removedRole != null) {
            accountRequest.addArgument("id", removedRole.getId());
            attrRequest.setRemoveDate(removedRole.getSunset());
            accountRequest.addArgument(AccountRequest.ATTACHMENTS, removedRole.getAttachments());
            accountRequest.addArgument(AccountRequest.ATTACHMENT_CONFIG_LIST, removedRole.getAttachmentConfigList());
        }
        accountRequest.add(attrRequest);

        // if configured add argument to remove assigned entitlements
        // when removing a detected role
        PlanUtil.addDeassignEntitlementsArgument(accountRequest);
        
        return accountRequest;
    }

    /**
     * Creates a single Attribute Request for the specified role and operation.
     * @param requestName the request name, either ProvisioningPlan.ATT_IIQ_ASSIGNED_ROLES or ProvisioningPlan.ATT_IIQ_DETECTED_ROLES
     * @param roleName the role name
     * @param op the operation
     * @param assignmentId the role assignment id; only required for Remove operations, otherwise it is ignored and can be null
     * @param permittingName the name of the role permitting this role. Ignored if null
     * @param comment the comment, which may be null
     * @param includeNegativeAssignment true to add negative assignment to the attributeRequest
     * @return the attribute request
     */
    protected AttributeRequest createRoleAttributeRequest(String requestName, String roleName, Operation op, String assignmentId,
                                                          String permittingName, String comment, boolean includeNegativeAssignment) throws GeneralException
    {
        if (Util.isNullOrEmpty(roleName)) {
            throw new GeneralException("Role name cannot be null");
        }

        AttributeRequest attrRequest = new AttributeRequest(requestName, op, roleName);
        attrRequest.setComments(comment);

        if (Operation.Remove.equals(op)) {
            if (!Util.isNullOrEmpty(assignmentId)) {
                attrRequest.setAssignmentId(assignmentId);
            }

            // if this is an assigned role we are removing sourced from a rule, then request that
            // a negative assignment be created so it won't return next time auto assignment rules are run
            if (ProvisioningPlan.ATT_IIQ_ASSIGNED_ROLES.equals(requestName) && includeNegativeAssignment) {
                attrRequest.put(ProvisioningPlan.ARG_NEGATIVE_ASSIGNMENT, true);
            }
        }

        if (permittingName != null) {
            attrRequest.put(ProvisioningPlan.ARG_PERMITTED_BY, permittingName);
        }
        
        return attrRequest;
    }


    /**
     * Creates a list of Account Requests for the specified roles and operation.
     * @param requestedEntitlements the list of entitlements
     * @param op the operation
     * @return the list of account requests
     * @throws GeneralException
     */
    protected List<AccountRequest> createEntitlementAccountRequests(List<RequestedEntitlement> requestedEntitlements, Operation op) throws GeneralException
    {
        List<AccountRequest> accountRequests = new ArrayList<AccountRequest>();
        if (Util.isEmpty(requestedEntitlements)) {
            return accountRequests;
        }

        // Index the entitlements by id; we'll need this later.
        Map<String, RequestedEntitlement> entitlementMap = new HashMap<String, RequestedEntitlement>();
        for (RequestedEntitlement entitlement : requestedEntitlements) {
            entitlementMap.put(entitlement.getId(), entitlement);
        }

        // Search for entitlements.
        List<String> properties = Arrays.asList("id", "value", "attribute", "application" ,"displayName");
        SearchResultsIterator iter = ObjectUtil.searchAcrossIds(getContext(), ManagedAttribute.class, 
                new ArrayList<String>(entitlementMap.keySet()), null, properties);

        // Create account requests for the returned managed attributes.
        while (iter.hasNext()) {
            Object[] managedAttr = iter.next();
            String id = (String)managedAttr[0];
            String value = (String)managedAttr[1];
            String name = (String)managedAttr[2];
            String appName = ((Application)managedAttr[3]).getName();
            String displayValue = (String)managedAttr[4];
            String nativeIdentity = null; // this is always null for now; may change one we support account selection
            String instance = null; // this is always null since we don't set it from the UI
            RequestedEntitlement entitlement = entitlementMap.get(id);
            accountRequests.add(createEntitlementAccountRequest(entitlement, null, value, name, appName, nativeIdentity, instance,
                    op, entitlement.getComment(), displayValue));
        }

        return accountRequests;
    }

    /**
     * Creates a list of Remove Entitlement Account Requests for the specified role assignments and identity.
     * @param accessRequestEntitlements the list of entitlements
     * @param identityId the identity
     * @return the list of account requests
     * @throws GeneralException
     */
    protected List<AccountRequest> createRemoveEntitlementAccountRequests(List<RemovedEntitlement> accessRequestEntitlements, String identityId)
            throws GeneralException
    {
        List<AccountRequest> accountRequests = new ArrayList<AccountRequest>();
        if (Util.isEmpty(accessRequestEntitlements)) {
            return accountRequests;
        }
        
        // First get the managed attributes entitlements.
        // Note that some entitlements may not have a matching managed attribute
        List<String> managedAttrIdList = new ArrayList<String>();
        for (RemovedEntitlement entitlement : accessRequestEntitlements) {
            if (!Util.isNullOrEmpty(entitlement.getId())) {
                managedAttrIdList.add(entitlement.getId());
            }
        }

        List<ManagedAttribute> managedAttrs = null;
        if (!Util.isEmpty(managedAttrIdList)) {
            QueryOptions queryOps = new QueryOptions(Filter.in("id", managedAttrIdList));
            managedAttrs = getContext().getObjects(ManagedAttribute.class, queryOps);
        }

        // Put the managed attributes in map indexed by id for easy handling.
        Map<String, ManagedAttribute> managedAttributeMap = new HashMap<String, ManagedAttribute>();
        for (ManagedAttribute attr : Util.safeIterable(managedAttrs)) {
            managedAttributeMap.put(attr.getId(), attr);
        }

        // Get only the entitlements being deleted, one at a time, since getting all IdentityEntitlements for an 
        // identity doesn't scale. We go to all this trouble not because it is easy, but because it is hard.
        for (RemovedEntitlement entitlement : Util.safeIterable(accessRequestEntitlements)) {
            List<Filter> filters = new ArrayList<Filter>();

            String managedAttrId = entitlement.getId();
            ManagedAttribute managedAttr = managedAttributeMap.get(managedAttrId);
            // If there is no managed attribute for this entitlement, get the 
            // filter values directly from the entitlement properties
            String applicationName = (managedAttr != null) ? managedAttr.getApplication().getName() : entitlement.getApplication(); 
            String attributeName = (managedAttr != null) ? managedAttr.getAttribute() : entitlement.getAttribute();
            String value = (managedAttr != null) ? managedAttr.getValue() : entitlement.getValue();
            String nativeIdentity = entitlement.getNativeIdentity();
            String instance = Util.getString(entitlement.getInstance());

            // TODO: I don't really know why we are looking up the IdentityEntitlement, don't we have the info we need already?
            filters.add(Filter.eq("application.name", applicationName));
            filters.add(Filter.eq("name", attributeName));
            filters.add(Filter.eq("value", value));
            filters.add(Filter.ignoreCase(Filter.eq("nativeIdentity", nativeIdentity)));
            if (instance == null) {
                filters.add(Filter.isnull("instance"));
            } else {
                filters.add(Filter.ignoreCase(Filter.eq("instance", instance)));
            }
            
            filters.add(Filter.eq("identity.id", identityId));

            QueryOptions queryOps = new QueryOptions(Filter.and(filters));
            List<IdentityEntitlement> idEntitlements = getContext().getObjects(IdentityEntitlement.class, queryOps);
            IdentityEntitlement idEntitlement = null;
            // Should only be one.
            if (Util.isEmpty(idEntitlements)) {
                logWarning("IdentityEntitlement not found", applicationName, attributeName, value, nativeIdentity);
            } else if (idEntitlements.size() > 1) {
                logWarning("IdentityEntitlement query returned too many rows", applicationName, attributeName, value, nativeIdentity);
            } else {
                idEntitlement = idEntitlements.get(0);
            }
            
            if (idEntitlement != null) {
                // Create account request to remove the entitlement.
                value = idEntitlement.getStringValue();
                attributeName = idEntitlement.getName();
                applicationName = idEntitlement.getAppName();
                nativeIdentity = idEntitlement.getNativeIdentity();
                instance = idEntitlement.getInstance();
            }

            accountRequests.add(createEntitlementAccountRequest(/*entitlement*/null, entitlement, value, attributeName, applicationName, nativeIdentity,
                    instance, Operation.Remove, entitlement.getComment(), null));
        }

        return accountRequests;
    }

    /**
     * Helper method log issue with entitlement add/remove requests
     */
    private void logWarning(String msg, String applicationName, String attributeName, String value, String nativeIdentity) {
        log.warn(msg + " - Identity: " + nativeIdentity + ", Application: " + applicationName +
                ", Attribute: " + attributeName + ", Value: " + value);
    }

    /**
     * Creates a single Account Requests for the specified entitlement and operation.
     * @param entitlement the requested entitlement; can be null for remove requests
     * @param value the value
     * @param name name
     * @param appName the application name
     * @param instance the application instance
     * @param op the operation
     * @param comment the comment, which may be null
     * @param displayValue the displayValue, which may be null
     * @return the account request
     */
    protected AccountRequest createEntitlementAccountRequest(RequestedEntitlement entitlement, RemovedEntitlement removedEntitlement, String value, String name,
            String appName, String nativeIdentity, String instance, Operation op, String comment, String displayValue) 
    {
        AccountRequest accountRequest = new AccountRequest();
        accountRequest.setOp(ObjectOperation.Modify);
        accountRequest.setApplication(appName);
        accountRequest.setNativeIdentity(nativeIdentity);
        accountRequest.setInstance(instance);

        AttributeRequest attrRequest = createEntitlementAttributeRequest(value, name, op, comment, displayValue);
        if (entitlement != null) {
            accountRequest.addArgument("id", entitlement.getId());
            attrRequest.setAddDate(entitlement.getSunrise());
            attrRequest.setRemoveDate(entitlement.getSunset());
            accountRequest.addArgument(AccountRequest.ATTACHMENTS, entitlement.getAttachments());
            accountRequest.addArgument(AccountRequest.ATTACHMENT_CONFIG_LIST, entitlement.getAttachmentConfigList());
        } else if (removedEntitlement != null) {
            accountRequest.addArgument("id", removedEntitlement.getId());
            attrRequest.setRemoveDate(removedEntitlement.getSunset());
            accountRequest.addArgument(AccountRequest.ATTACHMENTS, removedEntitlement.getAttachments());
            accountRequest.addArgument(AccountRequest.ATTACHMENT_CONFIG_LIST, removedEntitlement.getAttachmentConfigList());
        }
        accountRequest.add(attrRequest);

        return accountRequest;
    }

    /**
     * Creates a single Attribute Request for the specified role and operation.
     * @param value the value
     * @param name the name
     * @param op the operation
     * @param comment the comment, which may be null
     * @return the attribute request
     */
    protected AttributeRequest createEntitlementAttributeRequest(String value, String name, Operation op, String comment) 
    {
        return createEntitlementAttributeRequest(value, name, op, comment, null);
    }
    
    /**
     * Creates a single Attribute Request for the specified role and operation.
     * @param value the value
     * @param name the name
     * @param op the operation
     * @param comment the comment, which may be null
     * @param displayValue, the displayValue, which may be null
     * @return the attribute request
     */
    protected AttributeRequest createEntitlementAttributeRequest(String value, String name, Operation op, String comment, String displayValue) 
    {
        AttributeRequest attrRequest = new AttributeRequest(name, op, value);
        attrRequest.setComments(comment);
        
        if(null != displayValue) {
            attrRequest.setDisplayValue(displayValue);
        }

        // add flag so that AttributeAssignments are processed with this request
        attrRequest.put(ProvisioningPlan.ARG_ASSIGNMENT, "true");

        if (Operation.Add.equals(op)) {
            //If adding new Entitlement, give it an assignmentId
            if (Util.isNullOrEmpty(attrRequest.getAssignmentId())) {
                attrRequest.setAssignmentId(Util.uuid());
            }
        }
        return attrRequest;
    }

    protected List<Identity> getIdentities(List<String> identityIds) throws GeneralException
    {
        QueryOptions ops = new QueryOptions(Filter.in("id", identityIds));
        return getContext().getObjects(Identity.class, ops);
    }

    /**
     * Creates an AccessRequest from the map and does some sanity checking on it.
     * @param requestBody map from the request body
     * @return valid AccessRequest
     * @throws GeneralException
     */
    public AccessRequest createAndValidateAccessRequest(Map<String, Object> requestBody) throws GeneralException
    {
        AccessRequest accessRequest = new AccessRequest(requestBody);

        // Sanity check access request before we proceed.
        if (Util.isEmpty(accessRequest.getIdentityIds())) {
            throw new GeneralException("No identities found in access request");
        }

        Identity loggedInUser = userContext.getLoggedInUser();
        if (accessRequest.getIdentityIds().size() > 1) {
            if (!Util.isEmpty(accessRequest.getRemovedEntitlements()) || !Util.isEmpty(accessRequest.getRemovedRoles())) {
                throw new GeneralException("Multiple identities cannot be specified for remove access requests");
            }

            // Validate bulk requests are allowed
            if (!new QuickLinkOptionsConfigService(getContext()).isBulkEnabled(getLoggedInUserDynamicScopeNames(), QuickLink.LCM_ACTION_REQUEST_ACCESS, getQuickLink())) {
                throw new GeneralException("User cannot make bulk requests");
            }
        }
        else {
            // only validate removes in non-bulk case
            LcmAccessRequestHelper helper = new LcmAccessRequestHelper(getContext(), loggedInUser);
            helper.setQuickLink(getQuickLink());
            for (Identity identity : getIdentities(accessRequest.getIdentityIds())) {
                boolean isSelfService = loggedInUser.getId().equals(identity.getId());

                List<AccountRequest> removeRequests = createRemoveRequests(accessRequest, identity);
                if (!helper.validateRemoveRequests(removeRequests, isSelfService, identity)) {
                    throw new InvalidRemoveException();
                }
            }
        }

        // Last chance to validate requestees against roles requested.
        LCMConfigService lcmConfigService = new LCMConfigService(getContext());

        if (!Util.isEmpty(accessRequest.getAddedRoleIds())) {
            log.debug("Validating roles: " + accessRequest.getAddedRoleIds());
            Set<String> invalidRequestees = lcmConfigService.getInvalidRequestees(loggedInUser,
                    accessRequest.getIdentityIds(), new HashSet<String>(accessRequest.getAddedRoleIds()), LCMConfigService.SelectorObject.Role);
            if (!invalidRequestees.isEmpty()) {
                Message msg = new Message(Message.Type.Error,
                        MessageKeys.UI_ACCESS_REQUEST_USERS_CANNOT_REQUEST_ROLE, Util.setToCsv(invalidRequestees));
                throw new GeneralException(msg);
            }
        }

        if (!Util.isEmpty(accessRequest.getAddedEntitlementIds())) {
            log.debug("Validating entitlements: " + accessRequest.getAddedEntitlementIds());
            Set<String> invalidRequestees = lcmConfigService.getInvalidRequestees(loggedInUser,
                    accessRequest.getIdentityIds(), new HashSet<String>(accessRequest.getAddedEntitlementIds()), LCMConfigService.SelectorObject.ManagedAttribute);
            if (!invalidRequestees.isEmpty()) {
                Message msg = new Message(Message.Type.Error,
                        MessageKeys.UI_ACCESS_REQUEST_USERS_CANNOT_REQUEST_ENTITLEMENT, Util.setToCsv(invalidRequestees));
                throw new GeneralException(msg);
            }
        }

        return accessRequest;
    }

    /**
     * Validates the AccountRequests for the Identity.
     * @param identityIds the identities
     * @param accountRequests list of Add account requests
     * @param helper initialized and compiled LcmAccessRequestHelper
     *
     * @throws GeneralException
     */
    protected void checkDuplicateAddRequests(List<String> identityIds, List<AccountRequest> accountRequests, LcmAccessRequestHelper helper) throws GeneralException
    {
        final List<AdditionalQuestionsService.RequestedObjectStatus> DUPLICATE_STATUSES = Arrays.asList(
                AdditionalQuestionsService.RequestedObjectStatus.PENDING_REQUEST,
                AdditionalQuestionsService.RequestedObjectStatus.CURRENTLY_ASSIGNED
        );
        List<String> duplicateAddNames = new ArrayList<String>();
        AdditionalQuestionsService service = new AdditionalQuestionsService(this);
        AdditionalQuestionsService.RequestedObjectStatus status;
        // This validation is really only for deep links. We shouldn't be able get invalid requests here any other way. 
        for (AccountRequest request : accountRequests) {
            status = service.calculateStatus(request, identityIds, helper, new ArrayList<String>());
            if (DUPLICATE_STATUSES.contains(status)) {
                if (Util.size(request.getAttributeRequests()) > 0) {
                    AttributeRequest attributeRequest = request.getAttributeRequests().get(0);
                    duplicateAddNames.add((String)attributeRequest.getValue());
                }
            }
        }

        if (!Util.isEmpty(duplicateAddNames)) {
            throw new DuplicateAssignmentException(duplicateAddNames);
        }
    }

    /**
     * Helper method to sanitize requests for the LcmAccessRequestHelper
     * @param allAddRequests list of requests to be sanitized
     * @return List list of sanitized AccountRequest objects
     */
    private List<AccountRequest> setupAddRequests(List<AccountRequest> allAddRequests) {
        List<AccountRequest> requestsList = allAddRequests.stream().map(accountRequest -> {
            ProvisioningPlan.AccountRequest accountRequestCopy = new ProvisioningPlan.AccountRequest();
            accountRequestCopy.setApplication(accountRequest.getApplication());
            accountRequestCopy.setOperation(ProvisioningPlan.AccountRequest.Operation.Modify);
            accountRequestCopy.setTrackingId(Util.uuid());
            accountRequestCopy.setType(accountRequest.getType());

            List<AttributeRequest> attributeRequestListCopy = accountRequest.getAttributeRequests().stream().map(attributeRequest -> {
                ProvisioningPlan.AttributeRequest attributeRequestCopy = new ProvisioningPlan.AttributeRequest();
                attributeRequestCopy.setName(attributeRequest.getName());
                attributeRequestCopy.setValue(attributeRequest.getValue());
                attributeRequestCopy.setOperation(ProvisioningPlan.Operation.Add);
                attributeRequestCopy.setAssignmentId(attributeRequest.getAssignmentId());
                String permittedById = attributeRequest.getString(ProvisioningPlan.ARG_PERMITTED_BY);
                if (Util.isNotNullOrEmpty(permittedById)) {
                    attributeRequestCopy.put(ProvisioningPlan.ARG_PERMITTED_BY, permittedById);
                }
                return attributeRequestCopy;
            }).collect(Collectors.toList());

            accountRequestCopy.addAll(attributeRequestListCopy);
            return accountRequestCopy;
        }).collect(Collectors.toList());

        return requestsList;
    }

    /**
     * Create the LcmAccessRequestHelper and compile the provisioning targets, checking along the way that the account selection included with the request
     * are valid.
     * For validating provisioning targets, the LcmAccessRequestHelper needs to be setup like it is in the AdditionalQuestionsService.
     * It should not have the request targets set and account requests should not contain sunrise/sunset date args.
     * The LcmAccessRequestHelper should get setup with current request targets and accounts request before being returned.
     */
    private LcmAccessRequestHelper compileRequestTargets(AccessRequest accessRequest, List<AccountRequest> allAddRequests, Map<String, List<ProvisioningTarget>> requestTargets) throws GeneralException {

        LcmAccessRequestHelper requestHelper = new LcmAccessRequestHelper(getContext(), this.userContext.getLoggedInUser());
        requestHelper.setIdentityIds(accessRequest.getIdentityIds());
        List<AccountRequest> requestsList = setupAddRequests(allAddRequests);
        requestHelper.setAccountRequests(requestsList);
        requestHelper.setQuickLink(getQuickLink());

        // No need to validate requestTargets if empty. Don't compile if no requestTargets
        if (!Util.isEmpty(requestTargets)) {
            // Compile first with none of the included request targets, so we can compare the original account selection guidelines with what is included in the request
            requestHelper.compileProvisioningTargets();

            // Get the accountSelections from provisioning plan to validate requestTargets
            List<IdentityAccountSelection> accountSelections = requestHelper.getIdentityAccountSelections(true);

            for (String identId : Util.safeIterable(requestTargets.keySet())) {
                List<ProvisioningTarget> targs = requestTargets.get(identId);
                //Verify each ProvTarg
                for (ProvisioningTarget targ : Util.safeIterable(targs)) {
                    //Verify selections
                    for (AccountSelection sel : Util.safeIterable(targ.getAccountSelections())) {
                        if (sel.isDoCreate()) {
                            //Verify Create is allowed
                            boolean match = false;
                            for (IdentityAccountSelection accountSelection : Util.iterate(accountSelections)) {
                                if (match) {
                                    break;
                                }
                                if (Util.nullSafeEq(identId, accountSelection.getIdentityId(), true)) {
                                    for (IdentityAccountSelection.ProvisioningTarget target : Util.iterate(accountSelection.getProvisioningTargets())) {
                                        if (match) {
                                            break;
                                        }
                                        //Role Matching
                                        if (matchesRequest(sel, targ, target)) {

                                            // If a request is sent indicating create account, ensure that should be allowed
                                            if (!target.isAllowCreate()) {
                                                // This should never happen in the normal course of events, so if it does just throw.
                                                throw new GeneralException("Invalid account selection, create account is not allowed");
                                            } else {
                                                match = true;
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            //Verify nativeIdentities match
                            for (String nativeIdentity : Util.iterate(sel.getSelectedNativeIdentities())) {
                                boolean match = false;
                                for (IdentityAccountSelection accountSelection : Util.iterate(accountSelections)) {
                                    if (match) {
                                        break;
                                    }
                                    if (Util.nullSafeEq(identId, accountSelection.getIdentityId(), true)) {
                                        for (IdentityAccountSelection.ProvisioningTarget target : Util.iterate(accountSelection.getProvisioningTargets())) {
                                            if (match) {
                                                break;
                                            }
                                            if (matchesRequest(sel, targ, target)) {

                                                for (IdentityAccountSelection.ProvisioningTarget.AccountInfo accountInfo : target.getAccountInfos()) {
                                                    if (Util.nullSafeEq(accountInfo.getNativeIdentity(), nativeIdentity)) {
                                                        match = true;
                                                        break;
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                if (!match) {
                                    throw new GeneralException("Invalid account selection " + nativeIdentity);
                                }
                            }
                        }
                    }
                }
            }

            // Now that we have determined all the request targets are valid, recompile with the request targets included
            requestHelper.clearProvisioningTargets();
        }

        // reset the add requests
        requestHelper.setAccountRequests(allAddRequests);
        // set the actual request targets
        requestHelper.setExistingTargets(requestTargets);
        requestHelper.compileProvisioningTargets();

        // If there are any account selections, this means that the request did
        // not include everything that was needed.  Throw!
        if (requestHelper.hasAccountSelections()) {
            Map<String,List<IdentityAccountSelection>> missing = requestHelper.getIdentityAccountSelectionsByItemId();
            throw new MissingAccountSelectionsException(missing);
        }

        return requestHelper;
    }

    /**
     *
     * @param sel AccoutnSelection from requestTarget
     * @param targ ProvisioningTarget from requestTarget
     * @param target ProvisioningTarget from Compilation
     * @return True if the requesTarget matches the Compiled ProvisioningTarget
     *
     * If RoleName is set on the AccountSelection, that means AMEX option is enabled, and that Role gets it's own AccountSelection. The compiled
     * ProvisioningTarget should have the roleName set as well
     * If no roleName set, the selection applies across the entire Request for all entitlements on that app not falling into an individual AccountSelection
     * and the RoleName lives in the ProvisioningTarget. The compiled ProvisioningTarget will not have a roleName set, but rather a targetRole
     *
     */
    private boolean matchesRequest(AccountSelection sel, ProvisioningTarget targ, IdentityAccountSelection.ProvisioningTarget target) {
        return (((Util.isNotNullOrEmpty(sel.getRoleName()) && Util.nullSafeEq(sel.getRoleName(), target.getRoleName())) ||
                (Util.isNullOrEmpty(sel.getRoleName()) && Util.isNullOrEmpty(target.getRoleName()) && Util.nullSafeEq(target.getTargetRole(), targ.getRole(), true))) &&
                Util.nullSafeEq(target.getTargetName(), targ.getAttribute(), true) &&
                Util.nullSafeEq(target.getTargetValue(), targ.getValue(), true) &&
                Util.nullSafeEq(sel.getApplicationId(), target.getApplicationId(), true));
    }


    /**
     * Create the account requests for role additions from the AccessRequest.
     * Assumes a validated AccessRequest.
     * @param accessRequest AccessRequest object
     * @return Non-null list of AccountRequest objects
     * @throws GeneralException
     */
    public List<AccountRequest> createAddRoleRequests(AccessRequest accessRequest) throws GeneralException {
       return createRoleAccountRequests(accessRequest.getAddedRoles(), Operation.Add);
    }

    /**
     * Create the accounts requests for entitlement additions from the AccessRequest.
     * Assumes a validated AccessRequest.
     * @param accessRequest AccessRequest object
     * @return Non-null list of AccountRequest objects
     * @throws GeneralException
     */
    public List<AccountRequest> createAddEntitlementRequests(AccessRequest accessRequest) throws GeneralException {
        return createEntitlementAccountRequests(accessRequest.getAddedEntitlements(), Operation.Add);
    }
    
    /**
     * Create all removal AccountRequests specified by the AccessRequest for a single identity. 
     * Assumes a validated AccessRequest.
     * @param accessRequest AccessRequest object
     * @param identity Identity object
     * @return Non-null list of AccountRequest objects
     *
     */
    protected List<AccountRequest> createRemoveRequests(AccessRequest accessRequest, Identity identity) throws GeneralException
    {
        List<AccountRequest> accountRequests = new ArrayList<AccountRequest>();
        accountRequests.addAll(createRemoveRoleAccountRequests(accessRequest.getRemovedRoles(), identity.getId()));
        accountRequests.addAll(createRemoveEntitlementAccountRequests(accessRequest.getRemovedEntitlements(), identity.getId()));
        return accountRequests;
    }

    /**
     * Create a provisioning plan for the identity and set of requests
     * @param identity the identity
     * @param comments comments (not currently supported)
     * @param requests the account requests
     * @return the provisioning plan
     * @throws GeneralException
     */
    protected ProvisioningPlan createPlan(Identity identity, String comments, List<AccountRequest> requests) throws GeneralException
    {
        ProvisioningPlan plan = new ProvisioningPlan();
        plan.setIdentity(identity);
        plan.setSource(Source.LCM);

        if (identity != null) {
            for (AccountRequest request : requests) {
                plan.add(request);
            }
        }

        if (!Util.isNullOrEmpty(comments)) {
            plan.setComments(comments);
        }
        return plan;
    }

    
    /**
     * Creates a workflow out of the ProvisioningPlan, launches it, and returns the WorkflowSession.
     * @param name the workflow name
     * @param ops workflow options
     * @param plan provisioning plan
     * @return the launched workflow session
     * @throws GeneralException
     */
    private WorkflowSession runWorkflow(String name, Attributes<String,Object> ops,ProvisioningPlan plan)
            throws GeneralException 
    {
        IdentityLifecycler cycler = new IdentityLifecycler(getContext());
        // first arg is owner
        WorkflowSession wfSession = cycler.launchUpdate(null, plan.getIdentity(), plan, name, ops);
        
        return wfSession;
    }

    /**
     * Returns localized messages associated with a workflow launch.
     * @param launch the workflow launch
     * @return the list of string messages, or an empty list if none were found.
     */
    public List<String> getMessages(WorkflowLaunch launch) {
        List<Message> messages = null;
        if ( launch != null ) {
            messages = launch.getMessages();
        }

        // If we got messages, localize them into strings based on user locale and timezone.
        List<String> localizedMsgs = null;
        if (messages != null) {
            localizedMsgs = new ArrayList<String>();
            Locale locale = userContext.getLocale();
            TimeZone timeZone = userContext.getUserTimeZone();
            for (Message msg : messages) {
                localizedMsgs.add(msg.getLocalizedMessage(locale, timeZone));
            }
        }

        return localizedMsgs == null ? Collections.<String>emptyList() : localizedMsgs;
    }

    /**
     * Dig into the workflow case to find the request ID.
     *
     * @param wfCase the workflow case
     * @throws GeneralException
     */
    public String getIdentityRequestId(WorkflowCase wfCase) throws GeneralException
    {
        String requestId = null;

        if (wfCase != null) {
            requestId = (String)wfCase.get(IdentityRequestLibrary.ARG_IDENTITY_REQUEST_ID);
            
            if (Util.isNullOrEmpty(requestId)) {
                TaskResult result = wfCase.getTaskResult(getContext());
                if (result != null) {
                    requestId = (String)result.getAttribute(IdentityRequestLibrary.ARG_IDENTITY_REQUEST_ID);
                }
            }
        }
        
        if (!Util.isNullOrEmpty(requestId)) {
            requestId = Util.stripLeadingChar(requestId, '0');
        }
        
        return requestId;
    }

    /**
     * @param roleLocation the role location
     * @return AttributeRequest matching the specified roleLocation
     */
    private String getAttributeRequestName(String roleLocation) throws GeneralException
    {
        if (RemovedRole.ASSIGNED_LOCATION.equals(roleLocation)) {
            return ProvisioningPlan.ATT_IIQ_ASSIGNED_ROLES;
        }

        if (RemovedRole.DETECTED_LOCATION.equals(roleLocation)) {
            return ProvisioningPlan.ATT_IIQ_DETECTED_ROLES;
        }
        
        throw new GeneralException("invalid roleLocation " + roleLocation);
    }

    /**
     * Validate request contains required comments
     * @param accessRequest Access request
     * @throws GeneralException
     */
    protected void validateRequiredComments(AccessRequest accessRequest) throws GeneralException {
        Configuration systemConfig = Configuration.getSystemConfig();
        Boolean requireAccessRequestCommentsAll = systemConfig.getBoolean(Configuration.REQUIRE_ACCESS_REQUEST_COMMENTS_ALL, false);

        List<AccessItem> accessItems = accessRequest.getAccessItems();

        if (!requireAccessRequestCommentsAll) {
            AccessRequestConfigService accessRequestConfigService = new AccessRequestConfigService(getContext());
            Map<String, List<AccessRequestConfigDTO>> commentConfigs =
                    accessRequestConfigService.getConfigsForRequest(getLoggedInUser(), accessRequest, Rule.Type.CommentConfig);

            for (AccessItem item : accessItems) {
                // does item require comment?
                List<AccessRequestConfigDTO> configDTOS = commentConfigs.get(item.getId());
                for (AccessRequestConfigDTO config : configDTOS) {
                    if (config.isRequired() && Util.isNullOrEmpty(item.getComment())) {
                        throw new CommentRequiredException();
                    }
                }
            }
        } else {
            // all items require comments
            boolean missingComment = accessItems.stream().anyMatch(accessItem -> Util.isNullOrEmpty(accessItem.getComment()));
            if (missingComment) {
                throw new CommentRequiredException();
            }
        }
    }

    /**
     * Verifies that the attachments in the request are as needed: no attachments if attachments are not allowed,
     * attachments provided everywhere they are required, etc.
     *
     * @param accessRequest The access request
     * @throws GeneralException
     */
    protected void validateAttachments(AccessRequest accessRequest) throws GeneralException {
        AttachmentService attachmentService = new AttachmentService(userContext);
        boolean attachmentsEnabled = attachmentService.isAttachmentsEnabled();

        // Don't allow attachments when the feature is disabled or for multiuser requests
        if (!attachmentsEnabled || accessRequest.getIdentityIds().size() > 1) {
            verifyNoAttachments(accessRequest.getAccessItems());
            if (attachmentsEnabled && attachmentService.requestRequiresAttachment(accessRequest)) {
                throw new AttachmentRequiredException();
            }
        }
        else {
            // First make sure no attachments are referenced more than once in the request.
            verifyUniqueAttachments(accessRequest);

            // For a single user request, make sure every item which requires an attachment has one. This means
            // going to the AttachmentService and running the rules again.
            AccessRequestConfigService accessRequestConfigService = new AccessRequestConfigService(getContext());
            Map<String, List<AccessRequestConfigDTO>> attachmentConfigs =
                    accessRequestConfigService.getConfigsForRequest(getLoggedInUser(), accessRequest, Rule.Type.AttachmentConfig);

            verifyRequiredAttachments(accessRequest.getAccessItems(), attachmentConfigs);
        }
    }

    /**
     * Throws an exception if any of the items has an attachment.
     * @param items the request items
     * @throws GeneralException
     */
    protected void verifyNoAttachments(List<AccessItem> items) throws GeneralException {
        for (AccessItem item : items) {
            int size = Util.nullSafeSize(item.getAttachments());
            if (size > 0) {
                throw new GeneralException("Attachments are not permitted on item.");
            }
        }
    }

    /**
     * Throws an exception if more than one item within the request refers to the same attachment.
     * @param accessRequest the request
     * @throws GeneralException
     */
    protected void verifyUniqueAttachments(AccessRequest accessRequest) throws GeneralException {
        List<String> attachmentIds = getAttachmentIds(accessRequest.getAccessItems());

        // Put the ids in a set, which contains no duplicates. If set size != list size, list contained duplicates.
        Set<String> attachmentIdSet = new HashSet<>(attachmentIds);
        if (attachmentIds.size() != attachmentIdSet.size()) {
            throw new GeneralException("Multiple access request items refer to the same attachment");
        }
    }

    /**
     * Returns all attachment ids for the access items.
     * @param items the request items
     * @returns the list of attachment ids
     * @throws GeneralException
     */
    protected List<String> getAttachmentIds(List<AccessItem> items) {
        List<String> attachmentIds = new ArrayList<>();
        for (AccessItem item : items) {
            List<AttachmentDTO> attachments = item.getAttachments();
            for (AttachmentDTO attachment : Util.iterate(attachments)) {
                attachmentIds.add(attachment.getId());
            }
        }
        return attachmentIds;
    }

    /**
     * Throws an exception if any of the items lacks a required attachment according to the list of attachment configs
     * for that item.
     * @param items the request items
     * @param configMap maps item id to a list of its attachment configs
     * @throws GeneralException
     */
    protected void verifyRequiredAttachments(List<AccessItem> items, Map<String, List<AccessRequestConfigDTO>> configMap) throws GeneralException {
        for (AccessItem item : items) {
            // Get the id, list of attachments, and list of attachment configs for the item.
            String id = item.getId();
            List<AttachmentDTO> attachments = item.getAttachments();
            List<AccessRequestConfigDTO> attachmentConfigs = configMap.get(id);

            // If the list of attachments is empty but there are attachment configs, make sure no attachments were required.
            if (Util.isNotNullOrEmpty(id) && Util.isEmpty(attachments) && !Util.isEmpty(attachmentConfigs)) {
                for (AccessRequestConfigDTO config : attachmentConfigs) {
                    if (config.isRequired()) {
                        throw new GeneralException("Item is missing required attachments.");
                    }
                }
            }
            verifyAttachmentsExist(attachments);
            verifyAttachmentsNotInUse(attachments);
        }
    }

    /**
     * Throws an exception if any of the given attachments are already referenced by an IdentityRequestItem
     * @param attachments
     * @throws GeneralException
     */
    protected void verifyAttachmentsNotInUse(List<AttachmentDTO> attachments) throws GeneralException {
        List<String> attachmentIds = new ArrayList<>();
        for (AttachmentDTO attachment : Util.safeIterable(attachments)) {
            attachmentIds.add(attachment.getId());
        }
        if (!Util.isEmpty(attachmentIds)) {
            SailPointContext context = getContext();
            QueryOptions qo = new QueryOptions();
            List<Filter> filters = new ArrayList<Filter>();
            filters.add(Filter.in("id", attachmentIds));
            qo.add(Filter.collectionCondition("attachments", Filter.or(filters)));
            if (context.countObjects(IdentityRequestItem.class, qo) > 0) {
                throw new GeneralException("Attachment is already in use.");
            }
        }
    }

    /**
     * Throws an exception if any of the given attachments have an ID that doesn't exist in the database.
     * @param attachments
     * @throws GeneralException
     */
    protected void verifyAttachmentsExist(List<AttachmentDTO> attachments) throws GeneralException {
        SailPointContext context = getContext();
        for (AttachmentDTO attachmentDTO : Util.safeIterable(attachments)) {
            Attachment attachmentObject = context.getObjectById(Attachment.class, attachmentDTO.getId());
            if(attachmentObject == null) {
                throw new GeneralException("Attachment with id " + attachmentDTO.getId() + " does not exist");
            }

            // make certain we have the correct file name and other details
            copyAttachmentFields(attachmentDTO, attachmentObject);
        }
    }

    /*
     * This method will copy all of the attributes of an attachment into the attachment DTO.  The only
     * thing that needs to be present in the DTO is the id, and the rest of the attributes will get populated
     * directly from the attributes table.  The motivation for adding this method is that previously someone
     * could change the filename of a file in an identity request by putting in a name (such as my.exe), and that
     * name will be presented in the UI when viewing the request.  This method prevents someone from crafting
     * requests that will effectively allow for non-whitelisted extensions to be shown in the request.
     */
    private void copyAttachmentFields(AttachmentDTO attachmentDTO, Attachment attachment) throws GeneralException {
        attachmentDTO.setName(attachment.getName());
        attachmentDTO.setDescription(attachment.getDescription());
        attachmentDTO.setModified(attachment.getModified());
        attachmentDTO.setCreated(attachment.getCreated());
        attachmentDTO.setOwner(new IdentitySummaryDTO(attachment.getOwner()));
    }

    @Override
    public  SailPointContext getContext()
    {
        return this.userContext.getContext();
    }

    @Override
    public Locale getLocale() {
        return userContext.getLocale();
    }

    @Override
    public Identity getLoggedInUser() throws GeneralException {
        return userContext.getLoggedInUser();
    }
    
    @Override
    public List<String> getLoggedInUserDynamicScopeNames() throws GeneralException {
        return userContext.getLoggedInUserDynamicScopeNames();
    }

    @Override
    public List<String> getPermittedRoleProperties() throws GeneralException {
        return Collections.emptyList();
    }

    @Override
    public String getQuickLink() {
        return quickLinkName;
    }

    public void setQuickLink(String s) { this.quickLinkName = s; }

}
