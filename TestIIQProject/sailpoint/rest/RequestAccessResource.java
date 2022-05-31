/**
 * 
 */
package sailpoint.rest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.servlet.http.HttpSession;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.Explanator;
import sailpoint.authorization.AllowAllAuthorizer;
import sailpoint.authorization.LcmActionAuthorizer;
import sailpoint.authorization.utils.LcmUtils;
import sailpoint.integration.ListResult;
import sailpoint.object.AccessRequestAccountInfo;
import sailpoint.object.AccountSelection;
import sailpoint.object.Bundle;
import sailpoint.object.Configuration;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.Link;
import sailpoint.object.ProvisioningPlan.AccountRequest;
import sailpoint.object.ProvisioningPlan.AttributeRequest;
import sailpoint.object.ProvisioningTarget;
import sailpoint.object.QueryOptions;
import sailpoint.object.QuickLink;
import sailpoint.object.RoleAssignment;
import sailpoint.object.RoleDetection;
import sailpoint.object.RoleTarget;
import sailpoint.object.RoleTypeDefinition;
import sailpoint.rest.ui.requestaccess.AccessItemListResource;
import sailpoint.service.AdditionalQuestionsContext;
import sailpoint.service.AdditionalQuestionsService;
import sailpoint.service.AdditionalQuestionsService.RequestedObjectStatus;
import sailpoint.service.LCMConfigService;
import sailpoint.service.LCMConfigService.SelectorObject;
import sailpoint.service.RequestAccessService;
import sailpoint.service.quicklink.QuickLinkOptionsConfigService;
import sailpoint.tools.GeneralException;
import sailpoint.tools.JsonHelper;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.identity.RoleAssignmentUtil;
import sailpoint.web.lcm.IdentityAccountSelection;
import sailpoint.web.lcm.LcmAccessRequestHelper;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.util.MapListComparator;

/**
 * This resource provides data to the grid on lcm/requestAccess.jsf
 * This resource build a list of requestable objects (roles/entitlements) based on the
 * 'type' passed in and does transformation of the data before passing it to the grid
 * @author peter.holcomb
 *
 */
@Path("requestAccess")
public class RequestAccessResource extends BaseListResource implements AdditionalQuestionsContext {

    private static final Log log = LogFactory.getLog(RequestAccessResource.class);

    public static final String TYPE_ROLE = "role";
    public static final String TYPE_ENTITLEMENT = "entitlement";
    public static final String FIELD_SELECTED = "IIQ_Selected";

    private static final String DEFAULT_APP_ICON = "requestEntitlement";

    public static int ACTION_CHOOSE_INSTANCE = 1;
    public static int ACTION_CHOOSE_NATIVE_IDENTITY = 2;
    public static int ACTION_CREATE_ACCOUNT = 3;



    /** returns either a list of assigned roles or entitlements for the specified identity **/
    @GET @Path("assigned")
    public ListResult getAssignmentsForIdentity(
            @QueryParam("identityId") String identityId)
    throws GeneralException {
    	
        ListResult result = null;
        LcmUtils.authorizeTargetIdentity(identityId, getContext(), this);

        ExceptionsResource resource = new ExceptionsResource(identityId, this);
        result = resource.getExceptions(null, null, true, false, false);  
        convertResultFields(result, TYPE_ENTITLEMENT, identityId);

        RoleListResource roleListResource = new RoleListResource(this);
        ListResult roleResult = roleListResource.getGridRolesForIdentity(identityId, false);  
        convertResultFields(roleResult, TYPE_ROLE, identityId);
        addAccountInfoToRoleResults(roleResult, identityId);

        // can't use addObject here since the roleResult objects size might not be equal to 
        // count of roleResult after getGridRolesForIdentity call
        result.mergeListResult(roleResult);

        /** Trim and sort **/
        List<Map<String,Object>> objects = result.getObjects();
        objects = trimAndSortResults(objects);
        
        /** Get the cart off of the session and try to mark any objects that are being removed **/
        flagItemsInCart(objects);
        //set trimed and sorted objects back to result
        result.setObjects(objects);
        
        return result;
    }

    private void flagItemsInCart(List<Map<String, Object>> objects) {
        List<AccountRequest> currentRequests = (List<AccountRequest>)request.getSession().getAttribute(RoleListResource.ATT_REQUEST_REQUESTS);
        if(!Util.isEmpty(currentRequests)) {
            for(AccountRequest request : currentRequests) {
                for(Map<String, Object> object : objects) {
                    if(Util.nullSafeEq(object.get("id"), request.getArgument("id"))) {
                        object.put(FIELD_SELECTED, true);
                    }
                }
            }
        }
    }

    /** Does some conversion on the result being returned by the rolelistresource or the managedattributeresource
     * to return the fields in their correct spot
     * @param result
     */
    protected void convertResultFields(ListResult result, String type, String identityId)
        throws GeneralException {

        boolean isSelfService = isSelfService(identityId);

        for(Map<String,Object> row : (List<Map<String,Object>>)result.getObjects()) {
            
            if(type.equals(TYPE_ENTITLEMENT)) {
                String name = (String)row.get("displayableName");
                if(name==null) {
                    name = (String)row.get("displayValue");
                    if(name!=null) {
                        row.put("displayableName", name);
                    }
                }
                
                if(row.get("application")!=null) {
                    row.put(Util.getJsonSafeKey("application.name"), row.get("application"));
                }

                Message msg = new Message(MessageKeys.USR_STATUS_ACTIVE);
                row.put(RoleListResource.COL_STATUS, msg.getLocalizedMessage(getLocale(), getUserTimeZone()));
                row.put("name", name);
                row.put("IIQ_type",  localize(MessageKeys.ENTITLEMENT));
                row.put("IIQ_raw_type", TYPE_ENTITLEMENT);
                
                String appId = (String) row.get(Util.getJsonSafeKey("application.id"));
                String attribute = (String) row.get("attribute");
                String entValue = (String) row.get("value");
                
                String description = Explanator.getDescription(appId, attribute, entValue, getLocale());
                if (null != Util.getString(description)) {
                    row.put("description", description);
                }
                
                Object account = row.get("displayName");
                if (null == account) {
                    account = row.get("nativeIdentity");
                }
                
                row.put("account", account);
                
                // set default icon if one does not exist on the app
                if (null == row.get(Util.getJsonSafeKey("application.icon"))) {
                    row.put(Util.getJsonSafeKey("application.icon"), DEFAULT_APP_ICON);
                }

                row.put("removable", hasRequestControl(Configuration.LCM_ALLOW_REQUEST_ENTITLEMENTS_REMOVE, isSelfService));
            } else {
                row.put("IIQ_icon", row.get("roleTypeIcon"));
                //row.put("type", row.get("roleTypeName"));
                row.put("typeName", row.get("roleTypeName"));
                row.put("IIQ_type",  localize(MessageKeys.ROLE));
                row.put("IIQ_raw_type", TYPE_ROLE);
                row.put("instance", "");
                row.put("removable", hasRequestControl(Configuration.LCM_ALLOW_REQUEST_ROLES_REMOVE, isSelfService));
            }
        }
    }

    private boolean hasRequestControl(String control, boolean isSelfService) throws GeneralException {
        QuickLinkOptionsConfigService configService = new QuickLinkOptionsConfigService(getContext());

        return configService.isRequestControlOptionEnabled(getLoggedInUser(), getLoggedInUserDynamicScopeNames(), null, QuickLink.LCM_ACTION_REQUEST_ACCESS, control, isSelfService);
    }

    /**
     * Adds account information to the role result rows.
     * @param result The results.
     * @param identityId The identity id.
     * @throws GeneralException
     */
    private void addAccountInfoToRoleResults(ListResult result, String identityId) throws GeneralException {
        Identity identity = null;
        if (!Util.isNullOrEmpty(identityId)) {
            identity = getContext().getObjectById(Identity.class, identityId);
        }

        if (identity == null) {
            return;
        }

        for (Map<String,Object> row : (List<Map<String,Object>>) result.getObjects()) {
            List<RoleTarget> targets = null;

            String assignmentId = (String) row.get("assignmentId");
            String roleId = (String) row.get("roleId");
            
            if (Util.isEmpty(roleId)) {
                continue;
            }
            
            Bundle role = getContext().getObjectById(Bundle.class, roleId);
            if (role != null) {
                RoleTypeDefinition typeDefinition = role.getRoleTypeDefinition();

                // have to handle detections in a special way
                if (typeDefinition != null && !typeDefinition.isAssignable()) {
                    // if no assignment id then look for an unassigned detection for the role
                    if (Util.isNullOrEmpty(assignmentId)) {
                        RoleDetection roleDetection = identity.getUnassignedRoleDetection(role);
                        if (roleDetection != null) {
                            targets = roleDetection.getTargets();
                        }
                    } else {
                        // we have an assignment id so we need to find the detection for the role
                        // that that contains the assignment id
                        for (RoleDetection roleDetection : Util.iterate(identity.getRoleDetections())) {
                            if (roleDetection.hasAssignmentId(assignmentId) &&
                                roleDetection.getRoleId().equals(roleId)) {
                                targets = roleDetection.getTargets();

                                break;
                            }
                        }
                    }
                }
            }

            // if we don't have targets yet and assignment id is set then get them from
            // the assignment
            if (targets == null && assignmentId != null) {
                RoleAssignment roleAssignment = identity.getRoleAssignmentById(assignmentId);
                if (roleAssignment != null) {
                    targets = roleAssignment.getTargets();
                }
            }

            if (targets != null) {
                RoleAssignmentUtil.addRoleAssignmentInfoToListResultRow(row, targets, Util.getJsonSafeKey("application.name"), "account");
            }
        }
    }
    
    /** 
     * A really simple ajax call that tells the unified cart whether the item being added to the cart requires
     * follow up information, has a pending request or is already assigned.
     *
     * @return The map of values representing the response.
     * @throws GeneralException
     */
    @POST @Path("additionalQuestions")
    public Map<String, Object> additionalQuestions(
    		@FormParam("request") String accountRequestsJSON,
    		@FormParam("identityId") String identityId,
            @FormParam("allowPermitted") String allowPermitted,
            @FormParam("skipPermittedRoles") boolean skipPermittedRoles,
            @FormParam("skipAssignments") boolean skipAssignments,
            @FormParam("quickLink") String quickLinkName)
            throws GeneralException {

        LcmUtils.authorizeOptionalTargetIdentity(identityId, getContext(), this);

        List<AccountRequest> requests = JsonHelper.listFromJson(AccountRequest.class, accountRequestsJSON);
        AdditionalQuestionsService additionalQuestionsService = new AdditionalQuestionsService(this);
        Map<String, Object> resultMap = new HashMap<String, Object>();
        RequestedObjectStatus status = RequestedObjectStatus.NO_OP;
        Identity identity = null;
        List<String> identityIds;
        
        boolean isBulk = true;
        if (!Util.isNullOrEmpty(identityId)) {
            isBulk = false;
            identity = getContext().getObjectById(Identity.class, identityId);
            identityIds = Arrays.asList(identityId);
        } else {
            identityIds = getRequesteeIds();
        }

        if (!Util.isEmpty(requests)) {
            AccountRequest request = requests.get(0);
            String objectId = (String)request.getArguments().get("id");

            // by default we will add to cart if we have a request
            status = RequestedObjectStatus.ADD_TO_CART;
            
            // if not bulk then check to see if currently assigned or if 
            // a pending request exists
            if (!isBulk) {
                // Maintain existing behavior that allowMultipleAssignment setting allows assigned roles to 
                // be added again always. 
                status = additionalQuestionsService.getRequestedObjectStatus(request, identity, true);
            } else {
                LCMConfigService lcmConfigService = new LCMConfigService(getContext());
                /** This is a bulk request - validate the requestees **/
                if(isRoleRequest(request)) {
                    Set<String> invalidRequestees = lcmConfigService.getInvalidRequestees(getLoggedInUser(),
                            getRequesteeIds(), objectId, SelectorObject.Role);
                    if(invalidRequestees!=null && !invalidRequestees.isEmpty()) {
                        status = RequestedObjectStatus.INVALID_REQUESTEES;
                        resultMap.put("invalidObject", localize(MessageKeys.ROLE));
                        resultMap.put("invalidRequestees", Util.setToCsv(invalidRequestees));
                    }
                } else {
                    Set<String> invalidRequestees = lcmConfigService.getInvalidRequestees(
                            getLoggedInUser(), getRequesteeIds(), objectId,
                            SelectorObject.ManagedAttribute);
                    if(invalidRequestees!=null && !invalidRequestees.isEmpty()) {
                        status = RequestedObjectStatus.INVALID_REQUESTEES;
                        resultMap.put("invalidObject", localize(MessageKeys.ENTITLEMENT));
                        resultMap.put("invalidRequestees", Util.setToCsv(invalidRequestees));
                    }
                }
            }
            
            // check to see if we need to prompt for permitted roles or account selection
            if (isDefaultStatus(status)) {
                if (isRoleRequest(request)) {
                    if (!skipPermittedRoles && hasPermittedRoles(objectId, allowPermitted)) {
                        status = RequestedObjectStatus.PROMPT_PERMITTED_ROLES;
                    } else {
                        // check to see if we need to make a role assignment selection, this applies when
                        // a permitted role is directly requested
                        if (!skipAssignments && LcmAccessRequestHelper.isDirectPermittedRoleRequest(request)) {
                            Bundle permittedRole = getContext().getObjectById(Bundle.class, objectId);
                            List<RoleAssignment> permittingAssignments = 
                                    LcmAccessRequestHelper.findAssignmentsPermittingRole(getContext(), identity, permittedRole);
                            if (Util.size(permittingAssignments) > 1) {
                                status = RequestedObjectStatus.PROMPT_ROLE_ASSIGNMENT_SELECTION;
                            } else if (!Util.isEmpty(permittingAssignments)) {
                                // we know only one assignment permits this role so set the assignment id
                                RoleAssignment roleAssignment = permittingAssignments.get(0);
                                AttributeRequest attrRequest = request.getAttributeRequests().get(0);

                                attrRequest.setAssignmentId(roleAssignment.getAssignmentId());

                                // send assignmentId back because requests are created on client side
                                resultMap.put("assignmentId", roleAssignment.getAssignmentId());
                            } else {
                                /* No assignments permit this role. Shouldn't ever get here, role should
                                 * never be available for selection. Just throw. */
                                throw new GeneralException("No permitting RoleAssignments exist for direct permitted role request");
                            }
                        }

                        // no assignment selection so proceed with checking for account selections
                        if (isDefaultStatus(status)) {

                            LcmAccessRequestHelper helper = initializeHelper(requests, identityIds, quickLinkName);
                            if (helper.hasAccountSelections()) {
                                status = RequestedObjectStatus.PROMPT_ACCOUNT_SELECTION_ROLE;

                                resultMap.put("requesteeSelections",
                                        convertIdentityAccountSelections(helper.getIdentityAccountSelections()));
                            }
                        }
                    }

                    // pass back whether the role being requested is assignable or not
                    resultMap.put("isAssignable", RoleAssignmentUtil.isRoleAssignable(getContext(), objectId));
                } else if (isEntitlementRequest(request)) {
                    int instanceCount = getInstanceCount(request.getApplication());
                    boolean hasInstances = instanceCount > 0;
                    LcmAccessRequestHelper helper = initializeHelper(requests, identityIds, quickLinkName);

                    if (!isBulk) {
                        int actionType = determineActionForEntitlement(helper, identityId, instanceCount);
                        if (actionType > 0) {
                            status = RequestedObjectStatus.PROMPT_ACCOUNT_SELECTION;
                            resultMap.put("actionType", actionType);
                            resultMap.put("hasInstances", hasInstances);
                        }
                    }   else {
                        // For bulk requests, check each identity for additional questions
                        // and send maps of names and actions back 
                        List<String> incompleteIdentities = new ArrayList<String>();
                        Map<String, Integer> requesteeActionMap = new HashMap<String, Integer>();

                        for (String bulkIdentityId : identityIds) {
                            int actionType = determineActionForEntitlement(helper, bulkIdentityId, instanceCount);
                            if (actionType > 0) {
                                status = RequestedObjectStatus.PROMPT_ACCOUNT_SELECTION;
                                incompleteIdentities.add(bulkIdentityId);
                                requesteeActionMap.put(bulkIdentityId, actionType);
                                resultMap.put("hasInstances", hasInstances);
                            }
                        }

                        if (!Util.isEmpty(incompleteIdentities)) {
                            resultMap.put("requesteeNameMap", getRequesteeMap(incompleteIdentities));
                            resultMap.put("requesteeActionMap", requesteeActionMap);
                        }
                    }
                }
            }
        }
        
        resultMap.put("status", status);
       
        return resultMap;
    }

    /**
     * Create and compile an LcmAccessRequestHelper 
     */
    private LcmAccessRequestHelper initializeHelper(List<AccountRequest> requests, List<String> identityIds, String quickLinkName)
            throws GeneralException {
        LcmAccessRequestHelper helper = new LcmAccessRequestHelper(getContext(), getLoggedInUser());
        helper.setIdentityIds(identityIds);
        helper.setAccountRequests(requests);
        helper.setQuickLink(quickLinkName);
        helper.compileProvisioningTargets();
        
        return helper;
    }

    @GET @Path("types")
    public ListResult getTypes() throws GeneralException {
        // Anyone with LCM has access to the Manage -> Access Requests page
        authorize(new AllowAllAuthorizer());
        List<Map<String, Object>> requestTypesAsMaps = new ArrayList<Map<String, Object>>();
        Configuration systemConfig = Configuration.getSystemConfig();
        LCMConfigService lcmConfigSvc = new LCMConfigService(getContext());
        List<String> requestTypes = (List<String>) systemConfig.get(Configuration.ACCESS_REQUEST_TYPES);
        if (!Util.isEmpty(requestTypes)) {
            for (String requestType : requestTypes) {
                String displayName = lcmConfigSvc.getRequestTypeMessage(requestType, getLocale());
                Map<String, Object> requestTypeAsMap = new HashMap<String, Object>();
                requestTypeAsMap.put("name", requestType);
                requestTypeAsMap.put("displayName", displayName);
                requestTypesAsMaps.add(requestTypeAsMap);
            }
        }

        Comparator<Map<String, Object>> comparator = new MapListComparator("displayName", true);
        Collections.sort(requestTypesAsMaps, comparator);

        ListResult result = new ListResult(requestTypesAsMaps, requestTypesAsMaps.size());

        return result;
    }
    
    private boolean hasInstances(String applicationName) throws GeneralException {
        return getInstanceCount(applicationName) > 0;
    }
    
    private int getInstanceCount(String applicationName) throws GeneralException {
        QueryOptions qo = new QueryOptions();
        qo.add(Filter.eq("application.name", applicationName));
        qo.add(Filter.notnull("instance"));
        qo.setDistinct(true);
        qo.addGroupBy("instance");

        return getContext().countObjects(Link.class, qo);
    }
    
    /**
     * Determines the action type that needs to be taken for the entitlement request.
     * 
     * @param helper LcmAccessRequestHelper initialized and compiled.
     * @param identityId The identity id.
     * @param instanceCount Count of instances
     * @return The type of action necessary.
     * @throws GeneralException
     */
    private int determineActionForEntitlement(LcmAccessRequestHelper helper, String identityId, int instanceCount)
        throws GeneralException {
        int action = 0;
        
        if (instanceCount > 1) {
            action = ACTION_CHOOSE_INSTANCE;
        } else if (helper.hasAccountSelections(identityId)) {
            //Should be a single provisioning target with a single account selection
            List<ProvisioningTarget> targets = helper.getProvisioningTargetsForIdentity(identityId);
            if (Util.size(targets) != 1 || targets.get(0) == null || Util.size(targets.get(0).getAccountSelections()) != 1) {
                log.debug("Incorrect provisioning target and account selections generated for identity: " + identityId);
            } else {
                AccountSelection accountSelection = targets.get(0).getAccountSelections().get(0);
                if (accountSelection != null && !accountSelection.isAnswered()) {
                    if (accountSelection.isAllowCreate()) {
                        action = ACTION_CREATE_ACCOUNT;
                    } else {
                        action = ACTION_CHOOSE_NATIVE_IDENTITY;
                    }
                }
            }
        }

        return action;
    }
    
    /**
     * Determines if the specified role has permitted roles. If permitted roles 
     * are not allowed then false will always be returned.
     * 
     * @param roleId The role id.
     * @param allowPermitted Whether or not permitted roles are allowed.
     * @return True if the role has permitted roles, false otherwise.
     * @throws GeneralException
     */
    private boolean hasPermittedRoles(String roleId, String allowPermitted)
        throws GeneralException {
        
        if (!Util.atob(allowPermitted)) {
            return false;
        }
        
        RoleListResource rResource = new RoleListResource(this);
        ListResult result = rResource.getGridPermitsForRole(roleId);
        
        return null != result && result.getCount() > 0;
    }
    
    /**
     * Determines if the request is for a role.
     * 
     * @param request The request.
     * @return True if a role request, false otherwise.
     */
    private boolean isRoleRequest(AccountRequest request) {
        return TYPE_ROLE.equals(request.getType());
    }
    
    /**
     * Determines if the request is for an entitlement.
     * 
     * @param request The request.
     * @return True if a entitlement request, false otherwise.
     */
    private boolean isEntitlementRequest(AccountRequest request) {
        return TYPE_ENTITLEMENT.equals(request.getType());
    }

    /**
     * Determines if the specified status is the default add to cart.
     * 
     * @param status The status.
     * @return True if the status is add to cart, false otherwise.
     */
    private boolean isDefaultStatus(RequestedObjectStatus status) {
        return RequestedObjectStatus.ADD_TO_CART.equals(status);
    }

    /**
     * Determines if this is a self service request.
     *
     * @param identityId The identity id.
     * @return True if a self service request, false otherwise.
     * @throws GeneralException
     */
    public boolean isSelfService(String identityId) throws GeneralException {
        return (null != identityId) && identityId.equals(getLoggedInUser().getId());
    }
    
    public List<String> getRequesteeIds() {
        HttpSession httpSession = request.getSession(true);
        Set<String> requesteeIds = (Set<String>)httpSession.getAttribute(IdentityListResource.ATT_IDENTITY_IDS);
        if(requesteeIds!=null) {
            return new ArrayList(requesteeIds);
        }
        return null;
    }

    /**
     * Get the display names for some identity ids
     */
    public Map<String, String> getRequesteeMap(List<String> requesteeIds) throws GeneralException {
        Map<String, String> identities = new HashMap<String, String>();
        if (!Util.isEmpty(requesteeIds)) {

            QueryOptions ops = new QueryOptions();
            ops.add(Filter.in("id", requesteeIds));
            Iterator<Object[]> results = getContext().search(Identity.class, ops, Util.stringToList("id, displayName"));
            while(results.hasNext()) {
                Object[] result = results.next();
                identities.put((String)result[0], (String)result[1]);
            }

        }
        return identities;
    }

    /**
     * Convert IdentityAccountSelections into a representation expected by classic UI
     * @param selections List of IdentityAccountSelection objects
     * @return List of maps 
     */
    public List<Map<String, Object>> convertIdentityAccountSelections(List<IdentityAccountSelection> selections) throws GeneralException {

        List<Map<String, Object>> results = new ArrayList<Map<String, Object>>();
        for (IdentityAccountSelection identityAccountSelection : Util.safeIterable(selections)) {
            Map<String, Object> result = new HashMap<String, Object>();
            result.put("identityId", identityAccountSelection.getIdentityId());
            result.put("identityName", identityAccountSelection.getIdentityName());
            result.put("targets", convertProvisioningTargets(identityAccountSelection.getProvisioningTargets()));
            results.add(result);
        }
        
        return results;
    }

    /**
     * Convert ProvisioningTarget objects into representations of targets expected by classic UI
     * @param targets List of IdentityAccountSelection.ProvisioningTarget objects
     * @return List of maps
     * @throws GeneralException
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> convertProvisioningTargets(List<IdentityAccountSelection.ProvisioningTarget> targets) throws GeneralException {
        // Key role name mapping Map result to a role
        Map<String, Map<String, Object>> roleTargets = new HashMap<String, Map<String, Object>>();
        
        for (IdentityAccountSelection.ProvisioningTarget target : targets) {
            String roleName = target.getTargetRole();
            List<Map<String, Object>> selections;
            if (!roleTargets.containsKey(roleName)) {
                selections = new ArrayList<Map<String, Object>>();
                Map<String, Object> roleMap = new HashMap<String, Object>();
                roleMap.put("roleName", roleName);
                roleMap.put("selections", selections);
                roleTargets.put(roleName, roleMap);
            } else {
                selections = (List<Map<String, Object>>) roleTargets.get(roleName).get("selections");
            }

            Map<String, Object> selectionMap = new HashMap<String, Object>();
            selectionMap.put("applicationId", target.getApplicationId());
            selectionMap.put("applicationName", target.getApplicationName());
            selectionMap.put("roleName", target.getRoleName());
            selectionMap.put("hasInstances", hasInstances(target.getApplicationName()));
            selectionMap.put("allowCreate", target.isAllowCreate());

            List<Map<String, Object>> accountsList = new ArrayList<Map<String, Object>>();
            for (IdentityAccountSelection.ProvisioningTarget.AccountInfo acctInfo : Util.safeIterable(target.getAccountInfos())) {
                Map<String, Object> infoMap = new HashMap<String, Object>();
                infoMap.put("displayName", acctInfo.getDisplayName());
                infoMap.put("nativeIdentity", acctInfo.getNativeIdentity());

                accountsList.add(infoMap);
            }

            selectionMap.put("accounts", accountsList);
            
            selections.add(selectionMap);
        }
        
        return new ArrayList<Map<String, Object>>(roleTargets.values());
    }
    
    /** 
     * Checks for existing assignment. Returns error message in the map if accountInfo 
     * selection matches existing assignment for the identity.
     *
     * @return The map of values representing the response.
     * @throws GeneralException
     */
    @POST @Path("checkUniqueAssignment")
    public Map<String, Object> checkUniqueAssignment(
            @FormParam("requests") String requestsJSON,
            @FormParam("accountInfos") String accountInfosJSON)
            throws GeneralException {

        Configuration configuration = getContext().getConfiguration();
        Boolean isSelfServicePermittedEnabled = configuration.getAttributes().getBoolean("requestRolesPermittedSelfEnabled");
        Boolean isSelfServiceAssignedEnabled = configuration.getAttributes().getBoolean("requestRolesAssignableSelfEnabled");
        authorize(new LcmActionAuthorizer(QuickLink.LCM_ACTION_REQUEST_ACCESS, isSelfServiceAssignedEnabled || isSelfServicePermittedEnabled));
        List<AccountRequest> requests = JsonHelper.listFromJson(AccountRequest.class, requestsJSON);
        List<AccessRequestAccountInfo> accountInfoList = JsonHelper.listFromJson(AccessRequestAccountInfo.class, accountInfosJSON);

        Map<String, Object> resultMap = new HashMap<String, Object>();
        RequestedObjectStatus status = RequestedObjectStatus.UNIQUE_ASSIGNMENT;

        if(!Util.isEmpty(requests)) {
            List<String> namesWithDuplicatedAssignments = new RequestAccessService(this).checkUniqueAssignment(requests, accountInfoList);
            
            if (!Util.isEmpty(namesWithDuplicatedAssignments)) {
                status = RequestedObjectStatus.HAS_EXISTING_ASSIGNMENT;
                resultMap.put("errorMessage", 
                        localize(MessageKeys.LCM_REQUEST_EXISTING_ASSIGNMENT_MESSAGE, 
                                Util.listToCsv(namesWithDuplicatedAssignments)));
            }
        }
        
        resultMap.put("status", status);
        return resultMap;
    }

    /**
     * To allow us to implement AdditionalQuestionsContext -- not currently used
     * @return
     * @throws GeneralException
     */
    @Override
    public List<String> getPermittedRoleProperties() throws GeneralException {
        return getProjectionColumns(AccessItemListResource.COLUMNS_KEY_PREFIX + "Role");
    }

    @Override
    public String getQuickLink() {
        return null;
    }
}
