/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.web;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.faces.context.FacesContext;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONException;

import sailpoint.api.EntitlementCorrelator;
import sailpoint.api.Explanator;
import sailpoint.authorization.RoleDetailsAuthorizer;
import sailpoint.authorization.UnauthorizedAccessException;
import sailpoint.object.Application;
import sailpoint.object.Attributes;
import sailpoint.object.Bundle;
import sailpoint.object.CertificationItem;
import sailpoint.object.Configuration;
import sailpoint.object.EntitlementGroup;
import sailpoint.object.Entitlements;
import sailpoint.object.Identity;
import sailpoint.object.IdentityRequest;
import sailpoint.object.IdentityRequestItem;
import sailpoint.object.ManagedAttribute;
import sailpoint.object.Permission;
import sailpoint.object.RoleAssignment;
import sailpoint.object.RoleDetection;
import sailpoint.object.RoleTypeDefinition;
import sailpoint.object.UIPreferences;
import sailpoint.service.identity.TargetAccountDTO;
import sailpoint.service.identity.TargetAccountService;
import sailpoint.service.identityrequest.IdentityRequestService;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Internationalizer;
import sailpoint.tools.JsonHelper;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.identity.IdentityDTO;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.modeler.RoleUtil;

/**
 * @author <a href="mailto:jonathan.bryant@sailpoint.com">Jonathan Bryant</a>
 */
public class RoleDetailsBean extends BaseBean {

    
    private static Log log = LogFactory.getLog(RoleDetailsBean.class);


    // NOTE more docs for the REQUEST_TYPE_ params can be found
    // in the javascript component file RoleDetailPanel.js

    // Hierachy view of the role -ie the role and all it's ancestors
    private static final String REQUEST_TYPE_HIERARCHY = "HIERARCHY";

    // List of roles permitted by this role or one of it's ancestors
    private static final String REQUEST_TYPE_PERMITS = "PERMITS";

    // List of roles required by this role or one of it's ancestors
    private static final String REQUEST_TYPE_REQUIREMENTS = "REQUIREMENTS";

    // This is the inital request type passed when the js component fires up
    private static final String REQUEST_TYPE_ROOT = "ROOT";

    /**
    * Indicates the particular view of the role the user wants to see.
    */
    private String type = REQUEST_TYPE_HIERARCHY;

    // the world's most impressive map
    Map<String, Map<String, Map<String, String>>> attributeDescMap;
    
    private String rootId;
    private String roleId;
    private String identityId;
    private String roleAssignmentId;
    private String assignmentNote;
    private List<String> permittedBy = new ArrayList<String>();

    /**
     * The ID of the certification item in which the role is being certified.
     * This is used in places in the UI where the role and identity id are not
     * easily accessible. In those cases the role and identity can be retrieved
     * from the certification item.
     */
    private String certificationItemId;

    // If true any roles which are required but the identity does
    // not have will be flagged with a menacing icon
    private boolean flagMissingRoles;

    private Bundle role;

    private Identity identity;

    private BusinessRoleBean businessRoleBean;

    /**
     * true if entititlement descriptions should be displayed
     * rather than entitlement names. If true it means we show
     * the description with a small link they can click to show the name.
     *
     * This value can come from user preferences, but it
     * also may be an attribute in a certification. The user preference
     * is given priority though.
     */
    private Boolean displayEntitlementDescription;


    private String refererType;
    private String refererId;
    /**
     * A csv list of the classifications on this Role.
     *
     * Used to list out the classifications on the Role detail panel
     */
    private String classificationNames;

    /**
     * Default constructor.
     */
    public RoleDetailsBean() throws UnauthorizedAccessException {

        refererType = getRequestParameter("refererType");
        refererId = getRequestParameter("refererId");
        identityId = getRequestParameter("identityId");
        certificationItemId = getRequestParameter("certItemId");
        rootId = getRequestParameter("rootId");
        roleAssignmentId = getRequestParameter("assignmentId");
        roleId = getRequestParameter("roleId");
        if (roleId == null || "".equals(roleId))
            roleId = getRequestParameter("rootId");

        if ("true".equals(getRequestParameter("flagMissingRoles")))
            flagMissingRoles = true;

        String node = getRequestParameter("node");
        if (REQUEST_TYPE_PERMITS.equals(node) || REQUEST_TYPE_REQUIREMENTS.equals(node)
                || REQUEST_TYPE_ROOT.equals(node)){
            type=node;
        } else {
            type = REQUEST_TYPE_HIERARCHY;
        }

        try {
            if (roleId != null && !"".equals(roleId)) {
                role = getContext().getObjectById(Bundle.class, roleId);
            } else if (certificationItemId != null && !"".equals(certificationItemId)){
                CertificationItem item = getContext().getObjectById(CertificationItem.class, certificationItemId);
                if (item != null){
                    role = item.getBundle(getContext());
                    identity = item.getIdentity(getContext());
                }
            }

            authorizeRole();

            if (identityId != null && !"".equals(identityId))
                identity = getContext().getObjectById(Identity.class, identityId);

            //populate assignment note
            if (null != identity && Util.nullSafeEq(rootId, roleId)) {
                
                //get assignment from identity first, in case role removal
                RoleAssignment assignment = null;
                if (!Util.isEmpty(roleAssignmentId)) {
                    assignment = identity.getRoleAssignmentById(roleAssignmentId);                
                }
                if (null != assignment ) {
                    //do not show note for detected role
                    if (Util.nullSafeEq(assignment.getRoleId(), roleId)) {
                        assignmentNote = assignment.getComments();
                    }
                } else {
                    //get assignment note from identity request
                    String windowType = getRequestParameter("windowType");
                    
                    if ("approvalItem".equalsIgnoreCase(windowType)) {
                        assignmentNote = getAssignmentNoteForApprovalItem();
                    }
                    else if ("accessRequestItem".equalsIgnoreCase(windowType)) {
                        assignmentNote = getAssignmentNoteForRequestItem();
                    } else {
                        assignment = identity.getRoleAssignment(role);
                        if (assignment != null) {
                            assignmentNote = assignment.getComments();
                        }
                    }
                }
            }
            
            //permitted by for detected role
            if (null != identity) {
                if (!Util.isEmpty(roleAssignmentId)) {
                    List<RoleDetection> roleDetections = identity.getRoleDetections();
                    for (RoleDetection roleDetection : Util.safeIterable(roleDetections)) {
                        if (Util.nullSafeEq(roleId, roleDetection.getRoleId()) 
                                && roleDetection.getAssignmentIdList().contains(roleAssignmentId)) {
                            List<String> assignmentIdList = roleDetection.getAssignmentIdList();
                            for (String assignmentId : Util.safeIterable(assignmentIdList)) {
                                RoleAssignment assignment = identity.getRoleAssignmentById(assignmentId); 
                                if (null != assignment) {
                                    permittedBy.add(assignment.getRoleName()); 
                                }
                            }
                        }
                    }
                }
            }

            if (role != null) {
                List<String> classificationDisplayNames = role.getClassificationDisplayNames();
                if (!Util.isEmpty(classificationDisplayNames)) {
                    this.classificationNames = Util.listToCsv(classificationDisplayNames);
                }
            }

            initDisplayPreferences();

        } catch (UnauthorizedAccessException ue) {
            throw ue;
        }
        catch (GeneralException e) {
            log.error(e);
        }
    }

    private void authorizeRole() throws GeneralException {
        String authorizedIdentity = (String)getSessionScope().get(IdentityDTO.VIEWABLE_IDENTITY);
        Bundle rootRole = getContext().getObjectById(Bundle.class, rootId);

        authorize(new RoleDetailsAuthorizer(role, rootRole, refererType, refererId, authorizedIdentity));
    }
    
    protected String getAssignmentNoteForApprovalItem() throws GeneralException {
        String requestName = getRequestParameter("requestId");
        String roleName = getRequestParameter("id2");

        IdentityRequest request = getContext().getObjectById(IdentityRequest.class, requestName);
        if (request != null) {
            IdentityRequestService service = new IdentityRequestService(getContext(), request);
            IdentityRequestItem item = service.findIdentityRequestItemByValue(roleName);
            return service.getAssignmentNote(item, roleAssignmentId);
        }
        return null;
    }

    protected String getAssignmentNoteForRequestItem() throws GeneralException {
        String requestId = getRequestParameter("requestId");
        if (!Util.isEmpty(requestId)) {
            //get assignment note from request item
            String requestItemId = getRequestParameter("id2");
            //Don' think this is used anymore after angular refactor -rap
            IdentityRequest request = getContext().getObjectById(IdentityRequest.class, requestId);
            if (request != null) {
                IdentityRequestService service = new IdentityRequestService(getContext(), request);
                IdentityRequestItem item = service.findIdentityRequestItemById(requestItemId);
                return service.getAssignmentNote(item, roleAssignmentId);
            }
        }
        return null;
    }

    /**
     * Determine whether the user wants to see entitlement descriptions
     * or entitlement names.
     *
     * @throws GeneralException
     */
    private void initDisplayPreferences() throws GeneralException{

        if (getRequestParameter("displayEntitlementDescription") != null &&
                !"".equals(getRequestParameter("displayEntitlementDescription"))){
            displayEntitlementDescription = Boolean.parseBoolean(getRequestParameter("displayEntitlementDescription"));
            return;
        }

        Identity user = getLoggedInUser();
        Object pref = user.getUIPreference(UIPreferences.PRF_DISPLAY_ENTITLEMENT_DESC);
        if (null != pref) {
            displayEntitlementDescription = Util.otob(pref);
        } else if (certificationItemId != null){
            // if there's no usr preference and we're in a cert, check the cert's attributes
            CertificationItem item = getContext().getObjectById(CertificationItem.class, certificationItemId);
            displayEntitlementDescription = item != null ?
                    item.getCertification().getDisplayEntitlementDescription() : null;
        }

        if (displayEntitlementDescription == null)
            displayEntitlementDescription = Configuration.getSystemConfig().getBoolean(Configuration.DISPLAY_ENTITLEMENT_DESC);
    }

    /**
     * Generates a role bean we can use to list all the attributes and
     * permissions that get the user the role.
     *
     * todo performance wise, this operation is very expensive!! We need to
     * change the model so we don't have to correlate here
     *
     * jsl - note that this uses the getEntitlementMappings method
     * that takes a "flatted" argument which is false.  This means that the
     * results will not include matches from inherted roles.  I don't remember
     * why this is but that feels wrong.
     * 
     * @return
     */
    public BusinessRoleBean getBusinessRoleBean() {

        if (role == null || identity == null)
            return null;

        try {
            if (businessRoleBean == null) {
                List<EntitlementGroup> ents = null;
                Map<Bundle, List<EntitlementGroup>> entitlements = new HashMap<Bundle, List<EntitlementGroup>>();
                roleAssignmentId = getRequestParameter("assignmentId");
                RoleAssignment assignment = null;
                if (!Util.isEmpty(roleAssignmentId)) {
                    assignment = identity.getRoleAssignmentById(roleAssignmentId);                
                }
                
                // slow, full correlation but it uses the doNotFlatten
                // option which we can't do in the cached model
                EntitlementCorrelator ec = new EntitlementCorrelator(getContext());
                ents = ec.getContributingEntitlements(identity, assignment, role, false);
                entitlements.put(role, ents);
                businessRoleBean = new BusinessRoleBean(identity, role, ents, entitlements, null, getContext());
            }
            return businessRoleBean;
        } catch (Exception e) {
            log.error(e);
        }

        return null;
    }

    public boolean isShowRuleDescription(){

        if (identity == null)
            return true;

        //bug#23447
        //show role entitlements description or grid if there are no contributing entitlements. 
        BusinessRoleBean busRoleBean = this.getBusinessRoleBean();
        List<? extends Entitlements> ents = busRoleBean == null ? null : busRoleBean.getEntitlements();
        return ents == null || ents.size() == 0;
    }

    /**
     * Get a DTO for the current role.
     * @return
     */
    public RoleDetailDTO getDetails(){
        return createDTO(this.role);
    }

    /**
     * Get the role data and serialize it as json. This is consume by the
     * SailPoint.RoleDetailPanel component.
     * @return
     */
    public String getDetailsJson() {

        if (role == null)
            return JsonHelper.failure("Could not find the specified role.");

        List<RoleDetailDTO> roles = new ArrayList<RoleDetailDTO>();

        boolean displayEmptymsg = false;

        // In the requirements and permits trees, the first tier is made up of permits
        // or reqs. Once we start browsing the permits and requests however, we return a
        // plain old hierarchy.
        if (REQUEST_TYPE_ROOT.equals(type)){
            roles.add(createDTO(role));
        } else if (REQUEST_TYPE_HIERARCHY.equals(type)){
            roles = getInterestingRoles(role.getInheritance());
        } else {
            roles = getInterestingRoles(getFlattenedList(role, type));
            displayEmptymsg = true;
        }

        // if we're looking at the permits or requirements and no roles
        // were found display a messgage
        if (displayEmptymsg && (roles == null || roles.isEmpty())) {
            String msg = Internationalizer.getMessage(MessageKeys.ROLE_DETAIL_TREE_NODE_NO_ROLES_FOUND, getLocale());
            Map<String,Object> noResult = new HashMap<String,Object>();
            noResult.put("text", msg);
            noResult.put("id", "");
            noResult.put("iconCls", "noResultsNode");
            noResult.put("leaf", true);
            List<Map<String,Object>> list = new ArrayList<Map<String,Object>>();
            list.add(noResult);
            return JsonHelper.toJson(list);
        }

        return JsonHelper.toJson(roles);
    }

    //Duplicated from RoleViewerBean so we can authorize details differently from full role acccess
    public String getJsonForRole() {
        String roleString;

        try {
            if (role != null)
                roleString = RoleUtil.getReadOnlyRoleJson(role, getContext(), getLoggedInUser(), getUserTimeZone(), getLocale());
            else
                roleString = "{}";
        } catch (GeneralException e) {
            log.error(e);
            roleString = "{}";
        } catch (JSONException e) {
            log.error(e);
            roleString = "{}";
        }

        log.debug("getJsonForRole returning: " + roleString);

        return roleString;
    }

    /**
     * Creates a list of RoleDetailDTO objects given a collection of roles.
     * The RoleDetailDTO is populated with some extra UI display metadata, and
     * roles which are not interesting to the user are filtered.
     *
     * @param roles
     * @return
     */
    private List<RoleDetailDTO> getInterestingRoles(Collection<Bundle> roles) {
        List<RoleDetailDTO> out = new ArrayList<RoleDetailDTO>();
        if (roles != null) {
            for (Bundle b : roles) {
                if (!role.equals(b)) {
                    if (isRoleInteresting(b)) {

                        RoleDetailDTO role = createDTO(b);
                        if (identity == null || identity.hasRole(b, true))
                            role.setIdentityHasRole(true);

                        // don't display permits the identity does not have
                        if (!role.isIdentityHasRole() && REQUEST_TYPE_PERMITS.equals(type)) {
                            // dont include permits the user doesnt have
                        } else if (!role.isIdentityHasRole() && flagMissingRoles) {
                            role.setIconCls("missingRoleIcon");
                            out.add(role);
                        } else {
                            out.add(role);
                        }
                    }
                }
            }
        }
        return out;
    }

    /**
     * Creates a flat list of all requirments or permits for a given role
     * as well as all it's ancestors.
     *
     * @param role
     * @param type
     * @return
     */
    private Collection<Bundle> getFlattenedList(Bundle role, String type){

        Set<Bundle> reqs = new HashSet<Bundle>();

        if (role == null)
            return reqs;

        boolean isRequirements = REQUEST_TYPE_REQUIREMENTS.equals(type);

        if (isRequirements && role.getRequirements() != null)
            reqs.addAll(role.getRequirements());
        else if (!isRequirements && role.getPermits() != null)
            reqs.addAll(role.getPermits());

        if (role.getInheritance() != null){
            for (Bundle parent : role.getInheritance()){
                reqs.addAll(getFlattenedList(parent, type));
            }
        }

        return reqs;
    }

    private RoleDetailDTO createDTO(Bundle b) {

        boolean hasInheritance = false;
        if (b.getInheritance() != null && !b.getInheritance().isEmpty()) {
            for (Bundle inheritedRole : b.getInheritance()) {
                if (isRoleInteresting(inheritedRole)) {
                    hasInheritance = true;
                    break;
                }
            }
        }

        RoleDetailDTO role = new RoleDetailDTO(b, hasInheritance);
        if (identity == null || identity.hasRole(b, true))
            role.setIdentityHasRole(true);

        return role;
    }

    /**
     * Returns an unweildy map that's convenient for use in JSF. The map is
     * Application->AttributesName->AttributeValue->Description. So for example:
     * Oracle_DB->groupMbr->SysAdmin->System Administrator
     *
     * @return
     * @throws GeneralException
     */
    public Map<String, Map<String, Map<String, String>>> getEntitlementDescriptionMap() throws GeneralException{

        if (attributeDescMap != null)
            return attributeDescMap;

        attributeDescMap = new HashMap<String, Map<String, Map<String, String>>>();

        // Gather up all the attributes grouped by application
        Map<Application, Attributes<String, Object>> appAttributes = new HashMap<Application, Attributes<String, Object>>();
        List<EntitlementGroup> groups = businessRoleBean.getEntitlementsByRole().get(role);
        
        Map<String, Map<String, String>> appTargets = new HashMap<String, Map<String, String>>();
        
        if (groups != null){
            for(EntitlementGroup grp : groups){
                if (!appAttributes.containsKey(grp.getApplication()) && grp.getAttributes() != null)
                    appAttributes.put(grp.getApplication(), grp.getAttributes());
                else if (grp.getAttributes() != null)
                    appAttributes.get(grp.getApplication()).putAll(grp.getAttributes());
                
                
                if(grp.getPermissions()!=null) {
                    Map<String, String> permsMap = appTargets.get(grp.getApplicationName());
                	if(permsMap==null)
                		permsMap = new HashMap<String, String>();
                	
                	for(Permission perm : grp.getPermissions()) {
                		if(perm.getTarget()!=null) {
                            String desc = Explanator.getPermissionDescription(grp.getApplication(), perm.getTarget(), getLocale());
                			permsMap.put(perm.getTarget(), desc);
                		}
                	}
                	appTargets.put(grp.getApplicationName(), permsMap);
                }
            }
        }

        // get the attribute descriptions for each application
        // jsl - I'm not sure how this is used so we're retaining the
        // weird ManagedAttribute.PERMISSION_ATTRIBUTE thing until
        // someone who understands this better can redesign it

        for(Application app : appAttributes.keySet()){

            Map<String, Map<String, String>> descriptions = Explanator.getDescriptions(app, appAttributes.get(app), getLocale());
            
            Map<String, String> targetDescriptions = appTargets.get(app.getName());
            if(targetDescriptions!=null) {
            	descriptions.put(ManagedAttribute.OLD_PERMISSION_ATTRIBUTE, targetDescriptions);
            }
            
            attributeDescMap.put(app.getName(), descriptions);
        }
        
        for(String appName : appTargets.keySet()) {
        	if(!attributeDescMap.containsKey(appName)) {
        		Map<String, Map<String, String>> targetDescriptions = new HashMap<String, Map<String, String>>();
        		targetDescriptions.put(ManagedAttribute.OLD_PERMISSION_ATTRIBUTE, appTargets.get(appName));
        		attributeDescMap.put(appName, targetDescriptions);
        	}
        }
        return attributeDescMap;
    }


    /**
     * check to see if the role is interesting in this context. In this case that
     * means the role is not something like an organizational role.
     *
     * @param role
     * @return
     */
    protected static boolean isRoleInteresting(Bundle role) {
        RoleTypeDefinition typeDef = role.getRoleTypeDefinition();
        if (typeDef != null) {
            return !"organizational".equals(typeDef.getName());
        }
        return true;
    }

    public String getIdentityId() {
        return identityId;
    }

    public String getRootId() {
        return rootId;
    }

    public String getRoleId() {
        return roleId;
    }

    public Bundle getRole() {
        return role;
    }

    public void setRole(Bundle role) {
        this.role = role;
    }

    public String getCertificationItemId() {
        return certificationItemId;
    }

    public boolean isDisplayEntitlementDescription() {
        return displayEntitlementDescription;
    }

    public void setDisplayEntitlementDescription(boolean displayEntitlementDescription) {
        this.displayEntitlementDescription = displayEntitlementDescription;
    }
    
    public String getAcquired() {
        String msg;
        
        if (null != identity && null != role) {
            boolean detected = identity.getDetectedRole(role.getId()) != null;
            boolean assigned = isAssigned();
            
            if (detected && assigned) {
                msg = Message.localize(MessageKeys.ASSIGNED_DETECTED).toString();
            } else if (detected && !assigned) {
                msg = Message.localize(MessageKeys.DETECTED).toString();
            } else if (assigned) {
                msg = Message.localize(MessageKeys.ASSIGNED).toString();
            } else {
                msg = "";
            }
        } else {
            msg = Message.localize(MessageKeys.UNKNOWN).toString();
        }
        
        return msg;
    }
    
    private boolean isAssigned() {
        boolean assigned = false;
        // It's considered assigned if it is assigned or permitted by an assignment
        List<Bundle> assignments = identity.getAssignedRoles();
        String roleId = role.getId();
        for (Bundle assignment : assignments) {
            if (roleId.equals(assignment.getId())) {
                assigned = true;
                break;
            } else {
                Collection<Bundle> permits = assignment.getFlattenedPermits();
                for (Bundle permit : permits) {
                    if (roleId.equals(permit.getId())) {
                        assigned = true;
                        break;
                    }
                }
            }
        }

        return assigned;
    }

    
    /**
     * Exposes basic role stuff to the UI, while additionally carrying some UI metadata.
     */
    public static class RoleDetailDTO extends BundleDTO {
        private boolean hasInheritance;
        private boolean identityHasRole;
        private String displayableOwnerName;

        public RoleDetailDTO(Bundle role, boolean hasInheritance) {
            super(role);
            if (role.getOwner() == null) {
                this.displayableOwnerName = Internationalizer.getMessage(MessageKeys.NONE, FacesContext.getCurrentInstance().getViewRoot().getLocale());
            } else {
                this.displayableOwnerName = role.getOwner().getDisplayableName();
            }
            this.hasInheritance = hasInheritance;
        }

        public boolean isIdentityHasRole() {
            return identityHasRole;
        }

        public void setIdentityHasRole(boolean identityHasRole) {
            this.identityHasRole = identityHasRole;
        }

        public boolean isHasInheritance() {
            return hasInheritance;
        }

        public void setHasInheritance(boolean hasInheritance) {
            this.hasInheritance = hasInheritance;
        }

        public boolean isLeaf() {
            return !hasInheritance;
        }
        
        public String getDisplayableOwnerName() {
            return this.displayableOwnerName;
        }
    }
    
    
    public List<Map<String, String>> getAccountDetails() throws GeneralException {
        List<TargetAccountDTO> targetAccountDTOs = null;
        String windowType = getRequestParameter("windowType");
        String identityId = getRequestParameter("identityId");
        String roleId = this.role.getId();
        String roleAssignmentId = getRequestParameter("assignmentId");
        String requestId = getRequestParameter("requestId");
        String requestItemId = getRequestParameter("id2");
        String certItemId = getRequestParameter("certItemId");
        
        TargetAccountService accountService = new TargetAccountService(getContext(), identityId);

        if ("approvalItem".equalsIgnoreCase(windowType)) {
            targetAccountDTOs = accountService.getTargetsForApprovalItem(requestId, this.role.getName(), roleAssignmentId);
        }
        else if ("accessRequestItem".equalsIgnoreCase(windowType)) {
            targetAccountDTOs = accountService.getTargetsForRequestItem(requestId, requestItemId);
        }
        else if (!Util.isNothing(certItemId)) {
            CertificationItem item = getContext().getObjectById(CertificationItem.class, certItemId);
            targetAccountDTOs = accountService.getTargetsForCertificationItem(item);
        } else {
            String roleType = getRequestParameter("roleType");
            if ("detectedRoles".equalsIgnoreCase(roleType)) {
                targetAccountDTOs = accountService.getTargetsForRoleDetection(roleId, roleAssignmentId); 
            } else {
                targetAccountDTOs = accountService.getTargetsForIdentity(roleId, roleAssignmentId);
            }
        }
        
        return getAccountDetailsMaps(targetAccountDTOs);
    }
    
    //returns list of account details map from the request the item
    private List<Map<String, String>> getAccountDetailsMaps(List<TargetAccountDTO> targetAccounts) {
        List<Map<String, String>> list = new ArrayList<Map<String, String>>();
        for (TargetAccountDTO targetAccount : targetAccounts) {
            Map<String, String> detail = new HashMap<String, String>();
            String roleName = targetAccount.getSourceRole();
            if (roleName == null) {
                roleName = targetAccount.getValue();
            }
            detail.put("roleName", roleName);
            detail.put("appName", targetAccount.getApplication());
            String accountName = targetAccount.getAccount();
            if (Util.isNullOrEmpty(accountName)) {
                if (Util.isNullOrEmpty(targetAccount.getNativeIdentity())) {
                    accountName = Message.localize(MessageKeys.ROLE_DETAIL_NEW_ACCOUNT).toString();
                } else {
                    accountName = targetAccount.getNativeIdentity();
                }
            }
            detail.put("accountName", accountName);

            list.add(detail);
        }

        return list;
    }

    public String getRoleAssignmentId() {
        return roleAssignmentId;
    }

    public void setRoleAssignmentId(String roleAssignmentId) {
        this.roleAssignmentId = roleAssignmentId;
    }

    public String getAssignmentNote() {
        return this.assignmentNote;
    }

    public String getPermittedBy() {
        return Util.listToCsv(permittedBy);
    }

    public String getClassificationNames() {
        return classificationNames;
    }

    public void setClassificationNames(String classificationNames) {
        this.classificationNames = classificationNames;
    }

}
