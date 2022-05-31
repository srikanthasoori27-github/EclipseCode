package sailpoint.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.apache.commons.logging.LogFactory;
import org.apache.commons.logging.Log;

import sailpoint.api.ManagedAttributer;
import sailpoint.api.ObjectUtil;
import sailpoint.api.SailPointContext;
import sailpoint.object.AccountSelection;
import sailpoint.object.Application;
import sailpoint.object.ApprovalItem;
import sailpoint.object.Attributes;
import sailpoint.object.Bundle;
import sailpoint.object.Configuration;
import sailpoint.object.EntitlementGroup;
import sailpoint.object.Entitlements;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.IdentityRequest;
import sailpoint.object.IdentityRequestItem;
import sailpoint.object.ManagedAttribute;
import sailpoint.object.Permission;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.ProvisioningTarget;
import sailpoint.object.QueryInfo;
import sailpoint.object.QueryOptions;
import sailpoint.object.QuickLink;
import sailpoint.object.RoleAssignment;
import sailpoint.object.RoleDetection;
import sailpoint.object.RoleTarget;
import sailpoint.object.SailPointObject;
import sailpoint.object.TaskResult;
import sailpoint.object.WorkflowCase;
import sailpoint.service.LCMConfigService.QuickLinkRuleOption;
import sailpoint.service.LCMConfigService.SelectorObject;
import sailpoint.service.identity.TargetAccountDTO;
import sailpoint.service.identityrequest.IdentityRequestItemService;
import sailpoint.service.identityrequest.IdentityRequestService;
import sailpoint.service.quicklink.QuickLinkOptionsConfigService;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;
import sailpoint.workflow.IdentityRequestLibrary;

/**
 * Class to easily list current access owned by a single identity.
 */
public class CurrentAccessService {

	public static final Log log = LogFactory.getLog(CurrentAccessService.class);
    /**
     * Statuses of current access.
     */
    public static enum CurrentAccessStatus {
        Active("active", MessageKeys.LCM_ROLE_STATUS_ACTIVE),
        Requested("requested", MessageKeys.LCM_ROLE_STATUS_REQUESTED);
        
        private String stringValue;
        private String messageKey;
        
        CurrentAccessStatus(String stringValue, String messageKey) {
            this.stringValue = stringValue;
            this.messageKey = messageKey;
        }

        public String getStringValue() {
            return this.stringValue;
        }
        
        public String getMessageKey() {
            return this.messageKey;
        }
    }

    /**
     * Abstract class to hold common details on current access
     */
    public abstract static class CurrentAccess<T extends SailPointObject> {
        
        private CurrentAccessStatus status;
        private Date sunrise;
        private Date sunset;
        private boolean removeRequestPending = false;

        public CurrentAccess(CurrentAccessStatus status, Date sunrise, Date sunset) {
            this.status = status;
            this.sunrise = sunrise;
            this.sunset = sunset;
        }
        
        public abstract T getObject(SailPointContext context) throws GeneralException;

        public abstract boolean isRemovable(QuickLinkOptionsConfigService configService, Identity requester,
                                            boolean isSelfService, String quickLink)
            throws GeneralException;

        public CurrentAccessStatus getStatus() {
            return status;
        }

        public Date getSunrise() {
            return sunrise;
        }

        public Date getSunset() {
            return sunset;
        }

        public boolean isRemoveRequestPending() {
            return this.removeRequestPending;
        }

        public void setRemoveRequestPending(boolean removePending) {
             this.removeRequestPending = removePending;
        }
    }

    /**
     * Class to hold RoleTarget information
     */
    public class CurrentAccessRoleTarget {
        private String role;
        private String application;
        private String instance;
        private String nativeIdentity;
        private String accountName;
        
        public CurrentAccessRoleTarget(String role, String application, String instance, String nativeIdentity, String accountName) {
            this.role = role;
            this.application = application;
            this.instance = instance;
            this.nativeIdentity = nativeIdentity;
            this.accountName = accountName;
        }
        
        public CurrentAccessRoleTarget(AccountSelection accountSelection) {
            this.role = accountSelection.getRoleName();
            this.application = accountSelection.getApplicationName();
            //DOESN'T EXIST YET
            //this.instance = accountSelection.getInstance();
            this.nativeIdentity = accountSelection.getSelection();
            for (AccountSelection.AccountInfo accountInfo : Util.safeIterable(accountSelection.getAccounts())) {
                if (Util.nullSafeEq(accountInfo.getNativeIdentity(), accountSelection.getSelection())) {
                    this.accountName = accountInfo.getDisplayName();
                }
            }
        }
        
        public CurrentAccessRoleTarget(TargetAccountDTO targetAccount) {
            this(targetAccount.getSourceRole(), targetAccount.getApplication(), targetAccount.getInstance(), targetAccount.getNativeIdentity(), targetAccount.getAccount());
        }
        
        public CurrentAccessRoleTarget(RoleTarget roleTarget) {
            this(roleTarget.getRoleName(), roleTarget.getApplicationName(), roleTarget.getInstance(), roleTarget.getNativeIdentity(), roleTarget.getDisplayableName());
        }

        public String getRole() {
            return role;
        }

        public String getApplication() {
            return application;
        }

        public String getInstance() {
            return instance;
        }

        public String getNativeIdentity() {
            return nativeIdentity;
        }

        public String getAccountName() {
            return accountName;
        }
    }

    /**
     * Class to hold information on current access roles
     */
    public class CurrentAccessRole extends CurrentAccess<Bundle> {
        
        private static final String ROLE_LOCATION_REQUESTED = "requested";
        private static final String ROLE_LOCATION_DETECTED = "detected";
        private static final String ROLE_LOCATION_ASSIGNED = "assigned";
        
        private Bundle role;
        private String roleName;
        private String assignmentId;
        private String assignmentNote;
        private String source;
        private List<CurrentAccessRoleTarget> roleTargets;
        private String roleLocation;

        /**
         * Constructor based on requested role (IdentityRequest)
         * @throws GeneralException
         */
        public CurrentAccessRole(String roleName, IdentityRequestItem identityRequestItem)
                throws GeneralException {
            super(CurrentAccessStatus.Requested, identityRequestItem.getStartDate(), identityRequestItem.getEndDate());

            this.roleName = roleName;
            IdentityRequestService identityRequestService = new IdentityRequestService(context, identityRequestItem.getIdentityRequest());
            IdentityRequestItemService itemService = new IdentityRequestItemService(context, identityRequestItem);
            this.assignmentId = itemService.getAssignmentId();
            
            this.assignmentNote = identityRequestService.getAssignmentNote(identityRequestItem, this.assignmentId);
            this.source = identityRequestItem.getIdentityRequest().getRequesterDisplayName();
            this.roleLocation = ROLE_LOCATION_REQUESTED;
            List<TargetAccountDTO> targetAccounts = identityRequestService.getTargetAccounts(identityRequestItem, identity);
            if (!Util.isEmpty(targetAccounts)) {
                this.roleTargets = new ArrayList<>();
                for (TargetAccountDTO targetAccount : targetAccounts) {
                    this.roleTargets.add(new CurrentAccessRoleTarget(targetAccount));
                }
            }
        }

        /**
         * Constructor based on assigned role (RoleAssignment)
         */
        public CurrentAccessRole(Bundle role, RoleAssignment roleAssignment) {
            super(CurrentAccessStatus.Active, roleAssignment.getStartDate(), roleAssignment.getEndDate());
            this.role = role;
            this.assignmentId = roleAssignment.getAssignmentId();
            this.assignmentNote = roleAssignment.getComments();
            this.source = roleAssignment.getSource();
            if (roleAssignment.getAssigner() != null) {
                this.source = roleAssignment.getAssigner();
            }
            this.roleLocation = ROLE_LOCATION_ASSIGNED;
            if (!Util.isEmpty(roleAssignment.getTargets())) {
                this.roleTargets = new ArrayList<CurrentAccessRoleTarget>();
                for (RoleTarget roleTarget : roleAssignment.getTargets()) {
                    this.roleTargets.add(new CurrentAccessRoleTarget(roleTarget));
                }
            }
        }

        /**
         * Constructor based on detected role (RoleDetection)
         */
        public CurrentAccessRole(Bundle role, RoleDetection roleDetection, String source) {
            super(CurrentAccessStatus.Active, null, null);
            this.role = role;
            this.assignmentId = roleDetection.getFirstAssignmentId();
            this.assignmentNote = null;
            this.source = source;
            this.roleLocation = ROLE_LOCATION_DETECTED;
            if (!Util.isEmpty(roleDetection.getTargets())) {
                this.roleTargets = new ArrayList<CurrentAccessRoleTarget>();
                for (RoleTarget roleTarget : roleDetection.getTargets()) {
                    this.roleTargets.add(new CurrentAccessRoleTarget(roleTarget));
                }
            }
        }
        
        public CurrentAccessRole(ProvisioningPlan.AttributeRequest attributeRequest, List<AccountSelection> accountSelections, String requester) {
            super(CurrentAccessStatus.Requested, attributeRequest.getAddDate(), attributeRequest.getRemoveDate());
            this.roleName = (String)attributeRequest.getValue();
            this.assignmentId = attributeRequest.getAssignmentId();
            this.assignmentNote = attributeRequest.getString(ProvisioningPlan.ARG_ASSIGNMENT_NOTE);
            this.source = requester;
            this.roleLocation = ROLE_LOCATION_DETECTED;

            if (!Util.isEmpty(accountSelections)) {
                this.roleTargets = new ArrayList<CurrentAccessRoleTarget>();
                for (AccountSelection accountSelection : accountSelections) {
                    roleTargets.add(new CurrentAccessRoleTarget(accountSelection));
                }
                
            }
        }

        @Override
        public Bundle getObject(SailPointContext context) throws GeneralException {
            if (role == null && roleName != null) {
                role = context.getObjectByName(Bundle.class, roleName);
            }
            return role;
        }

        @Override
        public boolean isRemovable(QuickLinkOptionsConfigService configService, Identity requester,
                                   boolean isSelfService, String quickLink)
            throws GeneralException {

            return configService.isRequestControlOptionEnabled(
                requester,
                quickLink,
                QuickLink.LCM_ACTION_REQUEST_ACCESS,
                Configuration.LCM_ALLOW_REQUEST_ROLES_REMOVE,
                isSelfService
            );
        }
        
        public String getRoleName() {
            if (this.roleName == null && this.role != null) {
                this.roleName = this.role.getName();
            }
            
            return this.roleName;
        }

        public String getAssignmentId() {
            return assignmentId;
        }

        public String getAssignmentNote() {
            return assignmentNote;
        }

        public List<CurrentAccessRoleTarget> getRoleTargets() {
            return roleTargets;
        }
        
        public String getSource() {
            return source;
        }

        public String getRoleLocation() {
            return roleLocation;
        }

        public boolean isDetected() {
            return ROLE_LOCATION_DETECTED.equals(this.roleLocation);
        }
        
        public boolean isAssigned() {
            return ROLE_LOCATION_ASSIGNED.equals(this.roleLocation);
        }
        
        public boolean isRequested() {
            return ROLE_LOCATION_REQUESTED.equals(this.roleLocation);
        }
    }

    /**
     * Class to hold information on current access exceptions
     */
    public class CurrentAccessEntitlement extends CurrentAccess<ManagedAttribute> {
        private String applicationName;
        private String nativeIdentity;
        private String instance;
        private String account;
        private String attribute;
        private String value;
        private String permissionTarget;
        private String permissionRights;

        /**
         * Constructor based on existing Entitlements on Identity
         * @param entitlement Entitlements object with single attribute/permission
         */
        public CurrentAccessEntitlement(Entitlements entitlement) {
            super(CurrentAccessStatus.Active, null, null);
            this.applicationName = entitlement.getApplicationName();
            this.nativeIdentity = entitlement.getNativeIdentity();
            this.instance = entitlement.getInstance();
            this.account = entitlement.getDisplayName();
            
            Attributes<String,Object> attributes = entitlement.getAttributes();
            List<Permission> permissions = entitlement.getPermissions();

            if (attributes != null && attributes.size() > 0) {
                this.attribute = attributes.getKeys().get(0);
                this.value = Util.otos(attributes.get(this.attribute));
            }
            else if (permissions != null && permissions.size() > 0) {
                Permission permission = permissions.get(0);
                permissionTarget = permission.getTarget();
                permissionRights = permission.getRights();
            }
        }

        /**
         * Constructor based on requested entitlement
         * @param identityRequestItem IdentityRequestItem
         * @throws GeneralException
         */
        public CurrentAccessEntitlement(IdentityRequestItem identityRequestItem)
                throws GeneralException {
            super(CurrentAccessStatus.Requested, identityRequestItem.getStartDate(), identityRequestItem.getEndDate());

            this.applicationName = identityRequestItem.getApplication();
            this.nativeIdentity = identityRequestItem.getNativeIdentity();
            this.instance = identityRequestItem.getInstance();
            this.account = identityRequestItem.getDisplayName();
            ProvisioningPlan provisioningPlan = identityRequestItem.getProvisioningPlan();
            
            // It's unclear if we ever get a provisioning plan on identity request items. We might in the future
            // though if we support permission requests through LCM. So let's check for it and get info off it 
            // if it exists. 
            if (provisioningPlan != null) {
                for (ProvisioningPlan.AccountRequest accountRequest: provisioningPlan.getAccountRequests()) {
                    if (!Util.isEmpty(accountRequest.getAttributeRequests())) {
                        ProvisioningPlan.AttributeRequest attributeRequest = accountRequest.getAttributeRequests().get(0);
                        this.attribute = attributeRequest.getName();
                        this.value = Util.otos(attributeRequest.getValue());
                    }
                    if (!Util.isEmpty(accountRequest.getPermissionRequests())) {
                        ProvisioningPlan.PermissionRequest permissionRequest = accountRequest.getPermissionRequests().get(0);
                        this.permissionTarget = permissionRequest.getTarget();
                        this.permissionRights = permissionRequest.getRights();
                    }
                }
            } else {
                this.attribute = identityRequestItem.getName();
                this.value = identityRequestItem.getStringValue();
            }
            
                
            this.account = new LinkService(context).getAccountDisplayName(identity, this.applicationName, this.instance, this.nativeIdentity);
        }
        
        public CurrentAccessEntitlement(ProvisioningPlan.AccountRequest accountRequest, ProvisioningPlan.GenericRequest genericRequest) throws GeneralException {
            super(CurrentAccessStatus.Requested, genericRequest.getAddDate(), genericRequest.getRemoveDate());
            this.applicationName = accountRequest.getApplicationName();
            this.nativeIdentity = accountRequest.getNativeIdentity();
            this.instance = accountRequest.getInstance();
           
            if (genericRequest instanceof ProvisioningPlan.PermissionRequest) {
                ProvisioningPlan.PermissionRequest permissionRequest = (ProvisioningPlan.PermissionRequest)genericRequest;
                this.permissionTarget = permissionRequest.getTarget();
                this.permissionRights = permissionRequest.getRights();
            } else {
                this.attribute = genericRequest.getName();
                this.value = (String)genericRequest.getValue();
            }
            this.account = new LinkService(context).getAccountDisplayName(identity, this.applicationName, this.instance, this.nativeIdentity);
        }
                
        @Override
        public ManagedAttribute getObject(SailPointContext context) throws GeneralException {
            String appId = ObjectUtil.getId(context, Application.class, this.applicationName);
            String attrValue = this.isAttribute() ? this.value : this.permissionRights;
            String attrName = this.isAttribute() ? this.attribute : this.permissionTarget;
            return ManagedAttributer.get(context, appId, !isAttribute(), attrName, attrValue, null);
        }

        /**
         * Get only the object attributes specified in the list of properties provided
         * @param context
         * @param properties - List of object attributes names to return
         * @return Array of Object values based on the properties specified
         * @throws GeneralException
         */
        public Object[] getObjectAttrs(SailPointContext context, List<String> properties) throws GeneralException {
            String appId = ObjectUtil.getId(context, Application.class, this.applicationName);
            String attrValue = this.isAttribute() ? this.value : this.permissionRights;
            String attrName = this.isAttribute() ? this.attribute : this.permissionTarget;
            return ManagedAttributer.getAttr(context, appId, !isAttribute(), attrName, attrValue, properties);
        }

        @Override
        public boolean isRemovable(QuickLinkOptionsConfigService configService, Identity requester,
                                   boolean isSelfService, String quickLink)
            throws GeneralException {

            return configService.isRequestControlOptionEnabled(
                requester,
                quickLink,
                QuickLink.LCM_ACTION_REQUEST_ACCESS,
                Configuration.LCM_ALLOW_REQUEST_ENTITLEMENTS_REMOVE,
                isSelfService
            );
        }
        
        public String getApplicationName () {
            return this.applicationName;
        }
        
        public String getNativeIdentity() {
            return this.nativeIdentity;
        }
        
        public String getInstance() {
            return this.instance;
        }
        
        public String getAccount() {
            return this.account;
        }
        
        public boolean isPermission() {
            return this.permissionTarget != null;
        }
        
        public boolean isAttribute() {
            return this.attribute != null;
        }
        
        public String getAttribute() {
            return this.attribute;
        }
        
        public String getValue() {
            return this.value;
        }
        
        public String getPermissionTarget() {
            return this.permissionTarget;
        }
        
        public String getPermissionRights() {
            return this.permissionRights;
        }
    }

    /**'
     * Internal helper class to hold necessary info for a pending request and 
     * get the CurrentAccess for roles and entitlements based on what we can find
     */
    private class PendingRequest {
        private ProvisioningPlan provisioningPlan;
        private IdentityRequest identityRequest;

        public PendingRequest (ProvisioningPlan provisioningPlan, IdentityRequest identityRequest) {
            this.provisioningPlan = provisioningPlan;
            this.identityRequest = identityRequest;
        }

        public ProvisioningPlan getProvisioningPlan() {
            return this.provisioningPlan;
        }

        public IdentityRequest getIdentityRequest() {
            return this.identityRequest;
        }
        
        public List<CurrentAccessRole> getRoles() throws GeneralException {
            if (this.identityRequest != null) {
                return getRolesFromIdentityRequest(identityRequest);
            } else {
                return getRolesFromProvisioningPlan(provisioningPlan);
            }
        }
        
        public List<CurrentAccessEntitlement> getEntitlements(boolean excludePermissions) throws GeneralException {
            if (this.identityRequest != null) {
                return getEntitlementsFromIdentityRequest(identityRequest, excludePermissions);
            } else {
                return getEntitlementsFromProvisioningPlan(provisioningPlan, excludePermissions);
            }
        }
    }

    private static final List<String> ROLE_NAMES = Arrays.asList(ProvisioningPlan.ATT_IIQ_DETECTED_ROLES, ProvisioningPlan.ATT_IIQ_ASSIGNED_ROLES);
    private static final List<String> FLOW_NAMES = Arrays.asList(RequestAccessService.FLOW_CONFIG_NAME, IdentityRequest.ROLES_REQUEST_FLOW_CONFIG_NAME);
    public static final String WORKFLOW_ATT_PROVISIONING_PLAN = "plan";
    public static final String WORKFLOW_ATT_FLOW_NAME = "flow";            

    private SailPointContext context;
    private Identity identity;
    private List<PendingRequest> pendingRequests;
    
    public CurrentAccessService(SailPointContext context, Identity identity) {
        this.context = context;
        this.identity = identity; 
    }

    /**
     * Get all the roles requested, assigned and detected for the given Identity
     * @return List of CurrentAccessRole objects
     * @throws GeneralException
     */
    public List<CurrentAccessRole> getRoles() throws GeneralException {
        return getRoles(null);
    }

    /**
     * Conditionally get the roles requested, assigned and detected for the given Identity
     * @param status Status of roles to return.  If Requested then only pending.  If Active detected and assigned.  If
     *               null then returns all.
     * @return List of CurrentAccessRole objects
     * @throws GeneralException
     */
    @SuppressWarnings("unchecked")
    public List<CurrentAccessRole> getRoles(CurrentAccessStatus status) throws GeneralException {

        // get any pending requests
        List<CurrentAccessRole> pendingRoles = null;
        if(isRequestedStatus(status)) {
            pendingRoles = getPendingRoles();
        }

        // get detected and assigned
        List<CurrentAccessRole> detectedRoles = null;
        List<CurrentAccessRole> assignedRoles = null;
        if(isActiveStatus(status)) {
            detectedRoles = getDetectedRoles();
            assignedRoles = getAssignedRoles(detectedRoles);
        }

        // Mark the CurrentAccessRoles with a flag that indicates it also has a remove request pending
        markRolesForRemovePending(detectedRoles, assignedRoles);

        return combineLists(pendingRoles, detectedRoles, assignedRoles);
    }

    /**
     * 
     * @return role filters from quicklink population remove rules
     * @throws GeneralException
     */
    public Filter getRoleRuleFilter() throws GeneralException {
        LCMConfigService lcmService = new LCMConfigService(context);
        String requesteeId = identity.getId();
        String requestorName = context.getUserName();
        Identity requestor = context.getObjectByName(Identity.class, requestorName);
        boolean isSelfService =
                (requesteeId != null && requestor != null && requesteeId.equals(requestor.getId()));
        QueryInfo qInfo = lcmService.getSelectorQueryInfo(requestor, identity, SelectorObject.Role, isSelfService, QuickLinkRuleOption.Remove);
        if (!qInfo.isReturnNone()) {
            return qInfo.getFilter();
        }
        return null;
    }

    /**
     * Get all the exceptions, excluding permissions and include pending, for the given identity
     * @return List of CurrentAccessEntitlement
     * @throws GeneralException
     */
   
    public List<CurrentAccessEntitlement> getExceptions() throws GeneralException {
        // Normally we want to exclude permissions and include pending
        return getExceptions(null);
    }

    /**
     * Get all the exceptions, optionally excluding permissions and/or pending requests.
     * @param excludePermissions If true, exclude permissions from results
     * @param excludePending If true, exclude pending requests from results
     * @return List of CurrentAccessEntitlement
     * @throws GeneralException
     */
    public List<CurrentAccessEntitlement> getExceptions(boolean excludePermissions, boolean excludePending) throws GeneralException {
        return getExceptions(null, excludePermissions, excludePending);
    }

    /**
     * Conditionally get the exceptions, excluding permissions and include pending, for the given identity
     * @param status Status of exceptions to return.  If Requested then only pending.  If Active then current exceptions.
     *               If null then returns all.
     * @return List of CurrentAccessEntitlement
     * @throws GeneralException
     */
    public List<CurrentAccessEntitlement> getExceptions(CurrentAccessStatus status) throws GeneralException {
        return getExceptions(status, true, false);
    }

    /**
     * Conditionally get the exceptions, optionally excluding permissions and/or pending requests.
     * @param status Status of exceptions to return.  If Requested then only pending.  If Active then current exceptions.
     *               If null then returns all.
     * @param excludePermissions If true, exclude permissions from results
     * @param excludePending If true, exclude pending requests from results
     * @return List of CurrentAccessEntitlement
     * @throws GeneralException
     */
    @SuppressWarnings("unchecked")
    public List<CurrentAccessEntitlement> getExceptions(CurrentAccessStatus status, boolean excludePermissions, boolean excludePending) throws GeneralException {
        // get any pending requests
        List<CurrentAccessEntitlement> pending = null;
        if(isRequestedStatus(status)) {
            pending = (excludePending) ? new ArrayList<CurrentAccessEntitlement>() :
                getPendingEntitlements(excludePermissions);
        }

        // get any exceptions
        List<CurrentAccessEntitlement> exceptions = null;
        if(isActiveStatus(status)) {
            exceptions = getIdentityExceptions(excludePermissions);
        }

        // Mark the CurrentAccessEntitlements with a flag that indicates it also has a remove request pending
        if (!Util.isEmpty(exceptions)) {
            markEntitlementsForRemovePending(exceptions);
        }

        return combineLists(pending, exceptions);
    }

    /**
     * @return filter from entitlement removal rules
     * @throws GeneralException
     */
    public Filter getEntitlementRuleFilter() throws GeneralException {
        LCMConfigService lcmService = new LCMConfigService(context);
        String requesteeId = identity.getId();
        String requestorName = context.getUserName();
        Identity requestor = context.getObjectByName(Identity.class, requestorName);
        boolean isSelfService =
                (requesteeId != null && requestor != null && requesteeId.equals(requestor.getId()));
        QueryInfo qInfoApp = lcmService.getSelectorQueryInfo(requestor, identity, SelectorObject.Application, isSelfService, QuickLinkRuleOption.Remove);
        QueryInfo qInfoEnt = lcmService.getSelectorQueryInfo(requestor, identity, SelectorObject.ManagedAttribute, isSelfService, QuickLinkRuleOption.Remove);
        Filter filterApp = null;
        Filter filterEnt = null;
        if (!qInfoApp.isReturnNone()) {
            filterApp = qInfoApp.getFilter();
        }
        if (!qInfoEnt.isReturnNone()) {
            filterEnt = qInfoEnt.getFilter();
        }
        
        //Filter.and doesn't like null filter
        if(filterApp == null) {
            return filterEnt;
        }
        else if (filterEnt == null) {
            return filterApp;
        }
        else {
            return Filter.and(filterApp, filterEnt);
        }
    }

    /**
     * Mark the CurrentAccessEntitlements as remove pending if we find an active request to remove the role
     * @param entitlements The detected CurrentAccessEntitlements 
     * @throws GeneralException
     */
    private void markEntitlementsForRemovePending(List<CurrentAccessEntitlement> entitlements) throws GeneralException {
        List<PendingRequest> pendingRemoves = getPendingRemoveEntitlementRequests();

        if (!pendingRemoves.isEmpty()) {
            for (CurrentAccessEntitlement entitlement : entitlements) {
                for (PendingRequest request : pendingRemoves) {
                    boolean foundMatch = false;
                    if (request.identityRequest != null) {
                        foundMatch = entitlementMatches(entitlement, request.identityRequest);
                    } else {
                        for (ProvisioningPlan.AccountRequest accountRequest : Util.safeIterable(request.provisioningPlan.getAccountRequests())) {
                            foundMatch = entitlementMatches(entitlement, accountRequest);
                            if (foundMatch) {
                                break;
                            }
                        }
                    }

                    // Mark the entitlement as having a pending remove and move on to the next one
                    if (foundMatch) {
                        entitlement.setRemoveRequestPending(true);
                        break;
                    }
                }
            }
        }
    }

    /**
     * Returns true if status is null or Requested
     * @param status The status to test
     * @return true if status is null or Requested
     */
    private boolean isRequestedStatus(CurrentAccessStatus status) {
        return status == null || status.equals(CurrentAccessStatus.Requested);
    }

    /**
     * Returns true if status is null or Active
     * @param status The status to test
     * @return true if status is null or Active
     */
    private boolean isActiveStatus(CurrentAccessStatus status) {
        return status == null || status.equals(CurrentAccessStatus.Active);
    }

    @SuppressWarnings("unchecked")
    private <T> List<T> combineLists(List<T>... lists) {
        List<T> combinedList = new ArrayList<T>();
        if (lists != null) {
            for (List<T> list : lists) {
                if (list != null) {
                    combinedList.addAll(list);
                }
            }
        }
        return combinedList;
    }

    ////////////////////////////////////////////////////////////////
    // ROLES
    ////////////////////////////////////////////////////////////////

    /**
     * Get the pending CurrentAccessRoles 
     */
    private List<CurrentAccessRole> getPendingRoles() throws GeneralException {
        List<CurrentAccessRole> pendingRoles = new ArrayList<CurrentAccessRole>();
        for (PendingRequest pendingRequest : Util.safeIterable(getPendingRequests())) {
            List<CurrentAccessRole> currentRoles = pendingRequest.getRoles();
            if (!Util.isEmpty(currentRoles)) {
                pendingRoles.addAll(currentRoles);
            }
        }
        return pendingRoles;
    }

    /**
     * Get the CurrentAccessRoles from an identity request
     */
    private List<CurrentAccessRole> getRolesFromIdentityRequest(IdentityRequest identityRequest)
            throws GeneralException {

        List<CurrentAccessRole> roles = new ArrayList<CurrentAccessRole>();
        for(IdentityRequestItem identityRequestItem: Util.safeIterable(identityRequest.getItems())) {
            IdentityRequestItemService itemService = new IdentityRequestItemService(this.context, identityRequestItem);
            if (isPendingRequest(identityRequestItem) &&
                    itemService.isRole()) {
                Bundle role = itemService.getAccessRole();
                if (role != null) {
                    roles.add(new CurrentAccessRole(identityRequestItem.getStringValue(), identityRequestItem));
                }
            }
        }

        return roles;
    }

    /**
     * Get the CurrentAccessRoles from a ProvisioningPlan when no identity requests exist
     */
    private List<CurrentAccessRole> getRolesFromProvisioningPlan(ProvisioningPlan plan) throws GeneralException {
        List<CurrentAccessRole> roles = new ArrayList<CurrentAccessRole>();
        String requester = plan.getString(ProvisioningPlan.ARG_REQUESTER);
        for (ProvisioningPlan.AccountRequest accountRequest : Util.safeIterable(plan.getAccountRequests())) {
            if (Util.nullSafeEq(accountRequest.getApplication(), ProvisioningPlan.APP_IIQ, false)) {
                for (ProvisioningPlan.AttributeRequest attributeRequest : Util.safeIterable(accountRequest.getAttributeRequests())) {
                    if (ProvisioningPlan.Operation.Add.equals(attributeRequest.getOperation()) &&
                            Util.nullSafeContains(ROLE_NAMES, attributeRequest.getName())) {
                        String roleName = (String)attributeRequest.getValue();
                        boolean checkSourceRole = false;
                        if (ProvisioningPlan.ATT_IIQ_DETECTED_ROLES.equals(attributeRequest.getName())) {
                            roleName = attributeRequest.getString(ProvisioningPlan.ARG_PERMITTED_BY);
                            checkSourceRole = true;
                        }
                        List<AccountSelection> accounts = findAccounts(roleName, checkSourceRole, accountRequest.getSourceRole(), plan.getProvisioningTargets());
                        roles.add(new CurrentAccessRole(attributeRequest, accounts, requester));
                    }
                }
            }
        }

        return roles;
    }

    /**
     * Mark the CurrentAccessRoles as remove pending if we find an active request to remove the role
     * @param detectedRoles The detected CurrentAccessRoles 
     * @param assignedRoles The assigned CurrentAccessRoles 
     * @throws GeneralException
     */
    @SuppressWarnings("unchecked")
    private void markRolesForRemovePending(List<CurrentAccessRole> detectedRoles, List<CurrentAccessRole> assignedRoles) throws GeneralException {
        List<PendingRequest> pendingRemoves = getPendingRemoveRoleRequests();
        
        List<CurrentAccessRole> allRoles = combineLists(detectedRoles, assignedRoles);
        
        if (!pendingRemoves.isEmpty()) {
            for (CurrentAccessRole role : allRoles) {
                for (PendingRequest request : pendingRemoves) {
                    boolean foundMatch = false;
                    if (request.identityRequest != null) {
                        foundMatch = roleMatches(role, request.identityRequest);
                    } else {
                        for (ProvisioningPlan.AccountRequest accountRequest : Util.safeIterable(request.provisioningPlan.getAccountRequests())) {
                            foundMatch = roleMatches(role, accountRequest);
                            if (foundMatch) {
                                break;
                            }
                        }
                    }

                    // Mark the role as having a pending remove and move on to the next one
                    if (foundMatch) {
                        role.setRemoveRequestPending(true);
                        break;
                    }
                }
            }
        }
    }

    /**
     * Find the AccountSelections that correspond to the provided role information
     */
    private List<AccountSelection> findAccounts(String role, boolean checkSourceRole, String sourceRole, List<ProvisioningTarget> provisioningTargets) {
        List<AccountSelection> accountSelections = new ArrayList<AccountSelection>();

        for (ProvisioningTarget target : Util.safeIterable(provisioningTargets)) {
            if (Util.nullSafeEq(target.getRole(), role)) {
                for (AccountSelection accountSelection : Util.safeIterable(target.getAccountSelections())) {
                    if (!checkSourceRole || Util.nullSafeEq(accountSelection.getRoleName(), sourceRole, true)) {
                        accountSelections.add(accountSelection);
                    }
                }
            }
        }

        return accountSelections;
    }

    /**
     * Finds the list of detected roles .
     * @return The list of detected roles.
     * @throws GeneralException
     */
    private List<CurrentAccessRole> getDetectedRoles() throws GeneralException {
        List<CurrentAccessRole> roles = new ArrayList<CurrentAccessRole>();

        List<RoleDetection> roleDetections = this.identity.getRoleDetections();
        for (RoleDetection roleDetection : Util.safeIterable(roleDetections)) {
            Bundle role = roleDetection.getRoleObject(this.context);

            // check if this role is required by any of our assigned roles
            if (role == null || isRequiredByAssignedRole(role)) {
                continue;
            }

            String permittedBy = findPermittedByAssignedRoleName(role);
            roles.add(new CurrentAccessRole(role, roleDetection, permittedBy));
        }

        return roles;
    }

    /**
     * Determines if the specified role is required by an assigned role
     * on the identity.
     * @param role The role.
     * @return True if required by assigned role, false otherwise.
     * @throws GeneralException
     */
    private boolean isRequiredByAssignedRole(Bundle role) throws GeneralException {
        for (RoleAssignment roleAssignment : Util.safeIterable(this.identity.getRoleAssignments())) {
            Bundle assignment = roleAssignment.getRoleObject(this.context);
            if (!roleAssignment.isNegative() && assignment != null) {
                if (assignment.requires(role)) {
                    return true;
                }

                for (Bundle parent : Util.safeIterable(assignment.getInheritance())){
                    if (parent.requires(role)){
                        return true;
                    }
                }
            }
        }

        return false;
    }

    /**
     * Finds the name of the assigned role which permits the specified role if
     * one exists.
     * @param role The role.
     * @return The role name or null.
     * @throws GeneralException
     */
    private String findPermittedByAssignedRoleName(Bundle role) throws GeneralException {
        for (RoleAssignment roleAssignment : Util.safeIterable(this.identity.getRoleAssignments())) {
            Bundle assignedRole = roleAssignment.getRoleObject(this.context);
            if (role != null && assignedRole != null) {
                if (assignedRole.permits(role)) {
                    return assignedRole.getName();
                }
            }
        }

        return null;
    }

    /**
     * Finds the assigned roles. Removes the role from the detected
     * role list if it exists.
     * @param detectedRoles The detected roles.
     * @return The list of roles
     * @throws GeneralException
     */
    private List<CurrentAccessRole> getAssignedRoles(List<CurrentAccessRole> detectedRoles)
            throws GeneralException {

        List<CurrentAccessRole> roles = new ArrayList<CurrentAccessRole>();

        // only get active role assignments, i.e. those that are not negative
        List<RoleAssignment> roleAssignments = this.identity.getActiveRoleAssignments();
        for (RoleAssignment roleAssignment : Util.safeIterable(roleAssignments)) {
            Bundle role = roleAssignment.getRoleObject(this.context);
            if (role != null) {
                findDetectedRole(role, detectedRoles);
                removeInheritedRole(role, detectedRoles);
                //removeManuallyAssignedRoles(detectedRoles);
                roles.add(new CurrentAccessRole(role, roleAssignment));
            }
        }

        return roles;
    }

    /**
     * Finds the CurrentAccessRole generated from the detected role list that matches the
     * specified role.
     * @param role The role.
     * @param roles The detected roles.
     * @return The CurrentAccessRole or null if not found.
     */
    private CurrentAccessRole findDetectedRole(Bundle role, List<CurrentAccessRole> roles) throws GeneralException {
        if (role == null || Util.isEmpty(roles)) {
            return null;
        }

        // use iterator for easy removal
        Iterator<CurrentAccessRole> itr = roles.iterator();
        while (itr.hasNext()) {
            CurrentAccessRole detectedRole = itr.next();
            if (detectedRole.getObject(this.context).getId().equals(role.getId())) {
                itr.remove();
                return detectedRole;
            }
        }

        return null;
    }

    private void removeInheritedRole(Bundle role, List<CurrentAccessRole> roles)
            throws GeneralException {
        if (role == null || Util.isEmpty(roles)) {
            return;
        }

        List<Bundle> inheritedRoles = role.getInheritance();

        // step through each inherited role
        for(Bundle inheritedRole : Util.safeIterable(inheritedRoles)) {
            // first, see if the inherited role has inherited roles, and remove them.
            removeInheritedRole(inheritedRole, roles);

            // now check each role in the roles list and remove the inherited role
            // if it's there.
            Iterator<CurrentAccessRole> itr = roles.iterator();
            while (itr.hasNext()) {
                CurrentAccessRole currentRole = itr.next();
                if (currentRole.getObject(this.context).getId().equals(inheritedRole.getId())) {
                    itr.remove();
                }
            }
        }
    }
    ////////////////////////////////////////////////////////////////
    // ENTITLEMENTS
    ////////////////////////////////////////////////////////////////

    /**
     * Get the pending CurrentAccessEntitlements
     */
    private List<CurrentAccessEntitlement> getPendingEntitlements(boolean excludePermissions) throws GeneralException {
        List<CurrentAccessEntitlement> pendingEntitlements = new ArrayList<CurrentAccessEntitlement>();
        for (PendingRequest pendingRequest : Util.safeIterable(getPendingRequests())) {
            List<CurrentAccessEntitlement> currentEntitlements = pendingRequest.getEntitlements(excludePermissions);
            if (!Util.isEmpty(currentEntitlements)) {
                pendingEntitlements.addAll(currentEntitlements);
            }
        }
        return pendingEntitlements;
    }

    /**
     * Get the CurrentAccessEntitlements from an IdentityRequest
     */
    private List<CurrentAccessEntitlement> getEntitlementsFromIdentityRequest(IdentityRequest identityRequest, boolean excludePermissions) throws GeneralException {
        List<CurrentAccessEntitlement> entitlements = new ArrayList<CurrentAccessEntitlement>();

        for(IdentityRequestItem identityRequestItem: Util.safeIterable(identityRequest.getItems())) {
            IdentityRequestItemService itemService = new IdentityRequestItemService(this.context, identityRequestItem);
            if (isPendingRequest(identityRequestItem) && itemService.isEntitlement()) {
                CurrentAccessEntitlement newEntitlement = new CurrentAccessEntitlement(identityRequestItem);
                if (!excludePermissions || newEntitlement.isAttribute()) {
                    entitlements.add(newEntitlement);
                }
            }
        }
        return entitlements;
    }

    /**
     * Get the CurrentAccessEntitlements from a provisioning plan when no identity request exists
     */
    private List<CurrentAccessEntitlement> getEntitlementsFromProvisioningPlan(ProvisioningPlan plan, boolean excludePermissions) throws GeneralException{
        List<CurrentAccessEntitlement> entitlements = new ArrayList<CurrentAccessEntitlement>();
        for (ProvisioningPlan.AccountRequest accountRequest : Util.safeIterable(plan.getAccountRequests())) {
            if (!Util.nullSafeEq(accountRequest.getApplication(), ProvisioningPlan.APP_IIQ, false)) {
                for (ProvisioningPlan.AttributeRequest attributeRequest : Util.safeIterable(accountRequest.getAttributeRequests())) {
                    if (ProvisioningPlan.Operation.Add.equals(attributeRequest.getOperation()) &&
                            !Util.nullSafeContains(ROLE_NAMES, attributeRequest.getName())) {
                        entitlements.add(new CurrentAccessEntitlement(accountRequest, attributeRequest));
                    }
                }
                if (!excludePermissions) {
                    for (ProvisioningPlan.PermissionRequest permissionRequest : Util.safeIterable(accountRequest.getPermissionRequests())) {
                        if (ProvisioningPlan.Operation.Add.equals(permissionRequest.getOperation())) {
                            entitlements.add(new CurrentAccessEntitlement(accountRequest, permissionRequest));
                        }
                    }
                }
            }
        }


        return entitlements;
    }
    
    private List<CurrentAccessEntitlement> getIdentityExceptions(boolean excludePermissions) throws GeneralException {
        List<CurrentAccessEntitlement> entitlements = new ArrayList<CurrentAccessEntitlement>();
        // Unfortunately, EntitlementGroup doesn't have a back-pointer to
        // identity currently.  Instead, we'll just get the list off of the
        // identity directly.
        List<EntitlementGroup> exceptions = this.identity.getExceptions();
        if (exceptions == null) {
            exceptions = new ArrayList<EntitlementGroup>();
        }

        // Split these up so that there is one EntitlementGroup per value.
        List<Entitlements> foundEntitlements = EntitlementGroup.splitToValues(exceptions, excludePermissions);

        for (Entitlements entitlement : foundEntitlements) {
            entitlements.add(new CurrentAccessEntitlement(entitlement));
        }
        return entitlements;
    }

    /**
     * Determine if this IdentityRequestItem is a pending add request
     * @param item The IdentityRequestItem 
     * @return true if the item has a pending add request, false if not
     */
    private boolean isPendingRequest(IdentityRequestItem item) {
        // Valid request items are Add operation, not expansions, not provisioned and not rejected
        return ProvisioningPlan.Operation.Add.toString().equals(item.getOperation()) &&
                !item.isExpansion() &&
                (item.getProvisioningState() == null ||
                        Util.nullSafeEq(item.getProvisioningState(), ApprovalItem.ProvisioningState.Pending)) &&
                !item.isRejected();
    }

    /**
     * Determine if this IdentityRequestItem is a pending remove request
     * @param item The IdentityRequestItem 
     * @return true if the item has a pending remove request, false if not
     */
    private boolean isPendingRemoveRequest(IdentityRequestItem item) {
        // Valid request items are Remove operation, not expansions, not provisioned and not rejected
        return ProvisioningPlan.Operation.Remove.toString().equals(item.getOperation()) &&
                !item.isExpansion() &&
                (item.getProvisioningState() == null ||
                        Util.nullSafeEq(item.getProvisioningState(), ApprovalItem.ProvisioningState.Pending)) &&
                !item.isRejected();
    }

    ////////////////////////////////////////////////////////////////
    // 
    // Pending Request methods
    //
    ////////////////////////////////////////////////////////////////
    
    /**
     * Get the pending requests by inspecting task results for either identity requests or workflow case provisioning plan
     * @return List of PendingRequest objects
     * @throws GeneralException
     */
    private List<PendingRequest> getPendingRequests() throws GeneralException {
        if (this.pendingRequests == null) {
            this.pendingRequests = new ArrayList<PendingRequest>();
            QueryOptions qo = new QueryOptions();
            qo.add(Filter.eq("targetId", identity.getId()));
            qo.add(Filter.isnull("completed"));

            Iterator<TaskResult> results = context.search(TaskResult.class, qo);
            while(results.hasNext()) {
                TaskResult result = results.next();
                String identityRequestId = (String) result.get(IdentityRequestLibrary.ARG_IDENTITY_REQUEST_ID);
                if (identityRequestId != null) {
                    IdentityRequest identityRequest = this.context.getObjectByName(IdentityRequest.class, identityRequestId);
                    if (null != identityRequest) {
                        if (Util.nullSafeContains(FLOW_NAMES, identityRequest.getType())) {
                            pendingRequests.add(new PendingRequest(null, identityRequest));
                        }
                    } else {
                        log.warn("An IdentityRequest object with id: " + identityRequestId + " could not be found");
                        // Can't do anything without a workflow case, skip it
                        continue;
                    }
                } else {
                    String workflowCaseId = (String) result.get(WorkflowCase.RES_WORKFLOW_CASE);
                    if (workflowCaseId == null) {
                        // Can't do anything without a workflow case, skip it
                        continue;
                    }
                    WorkflowCase workflowCase = this.context.getObjectById(WorkflowCase.class, workflowCaseId);
                    if (workflowCase == null) {
                        // Can't do anything without a workflow case, skip it
                        continue;
                    }
                    if (!Util.nullSafeContains(FLOW_NAMES, workflowCase.get(WORKFLOW_ATT_FLOW_NAME))) {
                        continue;
                    }
                    ProvisioningPlan provisioningPlan = (ProvisioningPlan)workflowCase.get(WORKFLOW_ATT_PROVISIONING_PLAN);
                    if (provisioningPlan == null) {
                        // Can't do anything without a provisioning plan, skip it
                        continue;
                    }

                    pendingRequests.add(new PendingRequest(provisioningPlan, null));
                }

            }
        }

        return pendingRequests;
    }

    /**
     * Get the pending entitlement remove requests by inspecting task results for either identity requests or workflow case provisioning plan
     * @return List of PendingRequest objects for removing entitlements 
     * @throws GeneralException
     */
    public List<PendingRequest> getPendingRemoveEntitlementRequests() throws GeneralException {
        List<PendingRequest> pendingRemoves = new ArrayList<PendingRequest>();
        List<PendingRequest> pendingRequests = getPendingRequests();

        // Filter out the pending remove requests 
        for (PendingRequest pendingRequest : pendingRequests) {
            if (pendingRequest.getIdentityRequest() != null) {
                IdentityRequest identityRequest = pendingRequest.getIdentityRequest();

                for(IdentityRequestItem identityRequestItem: Util.safeIterable(identityRequest.getItems())) {
                    IdentityRequestItemService itemService = new IdentityRequestItemService(this.context, identityRequestItem);
                    if (isPendingRemoveRequest(identityRequestItem)  && itemService.isEntitlement()) {
                        pendingRemoves.add(pendingRequest);
                    }
                }
            } else {
                ProvisioningPlan plan = pendingRequest.getProvisioningPlan();

                for (ProvisioningPlan.AccountRequest accountRequest : Util.safeIterable(plan.getAccountRequests())) {
                    if (!Util.nullSafeEq(accountRequest.getApplication(), ProvisioningPlan.APP_IIQ, false)) {
                        for (ProvisioningPlan.AttributeRequest attributeRequest : Util.safeIterable(accountRequest.getAttributeRequests())) {
                            if (ProvisioningPlan.Operation.Remove.equals(attributeRequest.getOperation())  &&
                                    !Util.nullSafeContains(ROLE_NAMES, attributeRequest.getName())) {
                                pendingRemoves.add(pendingRequest);
                            }
                        }
                    }
                }
            }
        }

        return pendingRemoves;
    }

    /**
     * Get the pending role remove requests by inspecting task results for either identity requests or workflow case provisioning plan
     * @return List of PendingRequest objects for removing roles 
     * @throws GeneralException
     */
    public List<PendingRequest> getPendingRemoveRoleRequests() throws GeneralException {
        List<PendingRequest> pendingRemoves = new ArrayList<PendingRequest>();
        List<PendingRequest> pendingRequests = getPendingRequests();

        // Filter out the pending remove requests 
        for (PendingRequest pendingRequest : pendingRequests) {
            if (pendingRequest.getIdentityRequest() != null) {
                IdentityRequest identityRequest = pendingRequest.getIdentityRequest();

                for(IdentityRequestItem identityRequestItem: Util.safeIterable(identityRequest.getItems())) {
                    IdentityRequestItemService itemService = new IdentityRequestItemService(this.context, identityRequestItem);
                    if (isPendingRemoveRequest(identityRequestItem)  && itemService.isRole()) {
                        Bundle role = itemService.getAccessRole();
                        if (role != null) {
                            pendingRemoves.add(pendingRequest);
                        }
                    }
                }
            } else {
                ProvisioningPlan plan = pendingRequest.getProvisioningPlan();

                for (ProvisioningPlan.AccountRequest accountRequest : Util.safeIterable(plan.getAccountRequests())) {
                    if (Util.nullSafeEq(accountRequest.getApplication(), ProvisioningPlan.APP_IIQ, false)) {
                        for (ProvisioningPlan.AttributeRequest attributeRequest : Util.safeIterable(accountRequest.getAttributeRequests())) {
                            if (ProvisioningPlan.Operation.Remove.equals(attributeRequest.getOperation()) &&
                                    Util.nullSafeContains(ROLE_NAMES, attributeRequest.getName())) {
                                pendingRemoves.add(pendingRequest);
                            }
                        }
                    }
                }
            }
        }

        return pendingRemoves;
    }

    /**
     * Get the list of accounts that there are pending entitlement requests on
     *
     * @param request the request that has the entitlement being searched for
     * @return list of native identities
     */
    public List<String> getPendingEntitlementRequestAccounts(ProvisioningPlan.AccountRequest request) throws GeneralException {
        if (null == request) {
            return null;
        }

        List<String> accts = new ArrayList<String>();

        List<CurrentAccessEntitlement> pendingEntitlements = getPendingEntitlements(true);
        for (CurrentAccessEntitlement entitlement : pendingEntitlements) {
            if (entitlementMatches(entitlement, request)) {
                accts.add(entitlement.getNativeIdentity());
            }
        }

        return accts;
    }

    /**
     * Returns a list of accounts that there are pending role requests for the roleName for the identity
     *
     * @param roleName Name of the role on the request
     * @param application Name of the application                
     * @param sourceRole Source role name, for amex option. Or null.                
     * @return list of native identities
     */
    public List<String> getPendingRoleRequestAccounts(String roleName, String application, String sourceRole) throws GeneralException {
        List<CurrentAccessRole> pendingRoles = getPendingRoles();
        Set<String> accounts = new HashSet<String>();

        for (CurrentAccessRole role : pendingRoles) {
            if (Util.nullSafeEq(role.getRoleName(), roleName, false)) {
                for (CurrentAccessRoleTarget roleTarget : Util.safeIterable(role.getRoleTargets())) {
                    if (Util.nullSafeEq(sourceRole, roleTarget.getRole(), true) &&
                            Util.nullSafeEq(application, roleTarget.getApplication(), false)) {
                        accounts.add(roleTarget.getNativeIdentity());
                    }
                }
            }
        }
        
        return new ArrayList<String>(accounts);
    }

    /**
     * Determines if a pending role request exists for the specified identity.
     *
     * @param request The request that has the role we are searching for. It is expected the 
     *                request will have a single attribute request with the role information.
     * @return True if a pending request exists, false otherwise.
     */
    public boolean hasPendingRoleRequest(ProvisioningPlan.AccountRequest request) throws GeneralException {
        if (request != null && !Util.isEmpty(request.getAttributeRequests())) {
            String roleName = (String)request.getAttributeRequests().get(0).getValue();
            List<CurrentAccessRole> pendingRoles = getPendingRoles();
            for (CurrentAccessRole role : pendingRoles) {
                if (Util.nullSafeEq(roleName, role.getRoleName())) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Determines if a pending entitlement request exists for the specified identity and entitlement
     *
     * @param request  The request that has the entitlement we are searching for. It is expected the 
     *                request will have a single attribute request with the entitlement information.
     * @return True if a pending entitlement request exists.
     */
    public boolean hasPendingEntitlementRequest(ProvisioningPlan.AccountRequest request) throws GeneralException {
        if (null != request) {
            List<CurrentAccessEntitlement> pendingEntitlements = getPendingEntitlements(true);
            for (CurrentAccessEntitlement entitlement : pendingEntitlements) {
               if (entitlementMatches(entitlement, request)) {
                   return true;
               }
            }
        }
        
        return false;
    }

    /**
     * Determine if the entitlement matches the one in the AccountRequest
     * @param entitlement The entitlement to match
     * @param request The AccountRequest to search
     * @return true if the entitlement matches, false if not
     */
    private boolean entitlementMatches(CurrentAccessEntitlement entitlement, ProvisioningPlan.AccountRequest request) {
        ProvisioningPlan.AttributeRequest attrRequest = request.getAttributeRequests().get(0);
        
        /* Only compare the native identity if the nativeIdentity is actually set on the request */
        return (Util.isNullOrEmpty(request.getNativeIdentity()) || Util.nullSafeEq(request.getNativeIdentity(), entitlement.getNativeIdentity(), true))
                && Util.nullSafeEq(request.getApplication(), entitlement.getApplicationName(), true)
                && (Util.isNullOrEmpty(request.getInstance()) || Util.nullSafeEq(request.getInstance(), entitlement.getInstance(), true))
                && Util.nullSafeEq(attrRequest.getName(), entitlement.getAttribute())
                && Util.nullSafeEq(attrRequest.getValue(), entitlement.getValue());
    }

    /**
     * Determine if the entitlement matches one found in the IdentityRequest
     * @param entitlement The entitlement to match
     * @param identityRequest The IdentityRequest to search
     * @return true if the entitlement matches, false if not
     */
    private boolean entitlementMatches(CurrentAccessEntitlement entitlement, IdentityRequest identityRequest) {
        boolean foundMatch = false;

        if (null != identityRequest && null != entitlement) {
            for (IdentityRequestItem identityRequestItem : Util.safeIterable(identityRequest.getItems())) {
                if ((Util.isNullOrEmpty(identityRequestItem.getNativeIdentity()) || Util.nullSafeEq(identityRequestItem.getNativeIdentity(), entitlement.getNativeIdentity(), true))
                        && Util.nullSafeEq(identityRequestItem.getApplication(), entitlement.getApplicationName(), true)
                        && (Util.isNullOrEmpty(identityRequestItem.getInstance()) || Util.nullSafeEq(identityRequestItem.getInstance(), entitlement.getInstance(), true))
                        && Util.nullSafeEq(identityRequestItem.getName(), entitlement.getAttribute())
                        && Util.nullSafeEq(identityRequestItem.getStringValue(), entitlement.getValue())) {
                    foundMatch = true;
                    break;
                }
            } 
        }
        return foundMatch;
    }

    /**
     * Determine if the role matches one found in the IdentityRequest
     * @param role The role to match
     * @param identityRequest The IdentityRequest to search
     * @return true if the role matches, false if not
     */
    private boolean roleMatches(CurrentAccessRole role, IdentityRequest identityRequest) throws GeneralException {
        boolean foundMatch = false;

        if (null != identityRequest && null != role) {
            for (IdentityRequestItem identityRequestItem : Util.safeIterable(identityRequest.getItems())) {
                IdentityRequestItemService itemService = new IdentityRequestItemService(context, identityRequestItem);

                if ((Util.nullSafeEq(identityRequestItem.getStringValue(), role.getRoleName(), true))
                        && Util.nullSafeEq(itemService.getAssignmentId(), role.getAssignmentId(), true)) {
                    foundMatch = true;
                    break;
                }
            } 
        }
        return foundMatch;
    }

    /**
     * Determine if the role matches one in the AccountRequest
     * @param role The role to match
     * @param accountRequest The AccountRequest to search
     * @return true if the role matches, false if not
     */
    private boolean roleMatches(CurrentAccessRole role, ProvisioningPlan.AccountRequest accountRequest) {
        boolean foundMatch = false;

        for (ProvisioningPlan.AttributeRequest attributeRequest : Util.safeIterable(accountRequest.getAttributeRequests())) {
            // We already know this is a pending request to remove a role so we can just compare the relevant attributes
            // to those of the current access role we passed in.
            String roleName = (String)attributeRequest.getValue();

            if ((Util.nullSafeEq(roleName, role.getRoleName(), true))
                    && Util.nullSafeEq(attributeRequest.getAssignmentId(), role.getAssignmentId(), true)) {
                foundMatch = true;
                break;
            }
        }

        return foundMatch;
    }
}