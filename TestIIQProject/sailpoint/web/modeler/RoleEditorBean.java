/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.modeler;

import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.faces.application.FacesMessage;
import javax.faces.event.ActionEvent;
import javax.faces.model.SelectItem;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONWriter;

import sailpoint.api.Describer;
import sailpoint.api.ObjectUtil;
import sailpoint.api.RoleLifecycler;
import sailpoint.api.RoleOverlapper;
import sailpoint.api.RoleOverlapper.Expression;
import sailpoint.api.SailPointContext;
import sailpoint.authorization.WorkItemAuthorizer;
import sailpoint.integration.JsonUtil;
import sailpoint.object.ActivityConfig;
import sailpoint.object.Application;
import sailpoint.object.ApplicationAccountSelectorRule;
import sailpoint.object.Attributes;
import sailpoint.object.Bundle;
import sailpoint.object.BundleArchive;
import sailpoint.object.Capability;
import sailpoint.object.Certification;
import sailpoint.object.Classification;
import sailpoint.object.Configuration;
import sailpoint.object.Describable;
import sailpoint.object.ExtState;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.IdentitySelector;
import sailpoint.object.ImpactAnalysis;
import sailpoint.object.ImpactAnalysis.PolicyConflict;
import sailpoint.object.ObjectAttribute;
import sailpoint.object.ObjectClassification;
import sailpoint.object.ObjectConfig;
import sailpoint.object.Profile;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.ProvisioningPlan.AccountRequest;
import sailpoint.object.ProvisioningPlan.AttributeRequest;
import sailpoint.object.ProvisioningPlan.Operation;
import sailpoint.object.QueryOptions;
import sailpoint.object.Resolver;
import sailpoint.object.RoleTypeDefinition;
import sailpoint.object.Rule;
import sailpoint.object.SPRight;
import sailpoint.object.SailPointObject;
import sailpoint.object.Scope;
import sailpoint.object.Source;
import sailpoint.object.UIConfig;
import sailpoint.object.WorkItem;
import sailpoint.object.WorkflowCase;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.AttributeEditBean;
import sailpoint.web.Authorizer;
import sailpoint.web.BaseEditBean;
import sailpoint.web.EventBean;
import sailpoint.web.FormEditBean;
import sailpoint.web.UserRightsEditValidator;
import sailpoint.web.WorkItemBean;
import sailpoint.web.extjs.DescriptionData;
import sailpoint.web.extjs.SessionStateBean;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.policy.IdentitySelectorDTO;
import sailpoint.web.util.NavigationHistory;
import sailpoint.web.util.SelectItemByLabelComparator;
import sailpoint.web.util.WebUtil;

public class RoleEditorBean extends BaseEditBean<Bundle> implements UserRightsEditValidator.UserRightsEditValidatorContext {
    private static final Log log = LogFactory.getLog(RoleEditorBean.class);

    public static final String ATT_WORK_ITEM_ID = "workItemId";
    public static final String ATT_WORKFLOW_CASE_ID = "workflowCaseId";
    public static final String ATT_ROLE_TO_EDIT = "roleToEdit";

    private static final String ROLE_EDIT_MODE = "roleEditMode";
    private static final String ROLE_ATTRIBUTE_EDIT = "roleAttributeEdit";
    private static final String ROLE_MEMBERSHIP_EDIT = "roleMembershipEdit";
    private static final String ROLE_EVENTS = "roleEvents";
    private static final String ROLE_FORM_EDIT = "roleFormEdit";
    private static final String ROLE_PERMITS_EDIT = "rolePermitsEdit";
    private static final String ROLE_REQUIREMENTS_EDIT = "roleRequirementsEdit";
    private static final String PROFILE_EDIT = "profileEdit";
    private static final String ENTITLEMENT_MINING_EDIT = "entitlementMiningEdit";
    public static final String EDITED_PROFILES = "editedProfiles";
    private static final String EDITED_SELECTOR = "editedSelector";
    private static final String QUICK_IMPACT_ANALYSIS = "quickImpactAnalysis";
    private static final String ACTIVITY_ENABLED = "activityEnabled";
    private static final String MERGE_TEMPLATES = "mergeTemplates";
    private static final String ALLOW_DUPLICATE_ACCOUNTS = "allowDuplicateAccounts";
    private static final String ALLOW_MULTIPLE_ASSIGNMENTS = "allowMultipleAssignments";
    // Bug 27588
    private static final String ROLE_ACCOUNT_SELECTOR_RULES_EDIT = "roleAccountSelectorRulesEdit";

    public static final String ACTIVATION_CREATOR = "activationCreator";
    public static final String DEACTIVATION_CREATOR = "deactivationCreator";

    private String editedProfileId;
    private String seletedRoleIncludeOption;
    private String selectedArchive;
    private EditMode editMode;
    private boolean activityEnabled;
    private boolean mergeTemplates;
    private boolean allowDuplicateAccounts;
    private boolean allowMultipleAssignments;
    private int pageSize;
    private RoleAttributeEditBean attributeEditBean;
    private RoleMembershipDTO roleMembership;
    private RolePermitsDTO rolePermits;
    private RoleRequirementsDTO roleRequirements;
    private ProfileEditBean profileEditBean;
    private EntitlementProfileMiningBean entitlementMiningProfileEditBean;
    private Map<String, ProfileDTO> editedProfiles;
    private IdentitySelectorDTO editedSelector;
    private ImpactAnalysis analysisResults;
    private WorkflowCase pendingWorkflowCase;
    private FormEditBean formEditBean;

    /* List to hold events on the role **/
    private List<EventBean> events;
    private Date eventDate;
    private String eventAction;
    private Map<String, Boolean> eventSelections;
    private transient Set<String> loadedObjs;
    private DescriptionData descriptionData;
    private Boolean simpleRole;

    private String simpleEntitlementToDelete;

    private List<ApplicationAccountSelectorRuleDTO> targetAccountselectorRules;

    /**
     * Capabilities that should be assigned (during provisioning ) to anyone
     * having this role.
     */
    List<String> capNames;

    /**
     * Controlled Scopes that should be assigned ( during provisioning) to anyone
     * having this role.
     */
    List<Scope> controlledScopes;

    /** List of Classification ids **/
    List<String> classificationIds;


    private enum EditMode {
        Plain,
        Profile,
        EntitlementMining,
        SimpleEntitlement
    };

    /**
     * Simple implemenation of USerRightsEditValidatorContext to check an existing role for unmatched rights
     * Used in validateUserRights for permitted/required/inherted roles.
     */
    private static class RoleUserRightEditContext implements UserRightsEditValidator.UserRightsEditValidatorContext {

        private Bundle role;

        public RoleUserRightEditContext(Bundle role) {
            this.role = role;
        }

        @Override
        public List<String> getNewCapabilities() throws GeneralException {
            List<String> existingCapabilities = null;
            Map<String, List<String>> values = RoleUtil.getValuesFromProvisioningPlan(role);
            if (values != null) {
                existingCapabilities = values.get(Certification.IIQ_ATTR_CAPABILITIES);
            }

            return existingCapabilities;
        }

        @Override
        public List<String> getNewControlledScopes() throws GeneralException {
            return ObjectUtil.getObjectIds(RoleUtil.getControlledScopesFromProvisioningPlan(role));
        }

        @Override
        public List<String> getExistingCapabilities() throws GeneralException {
            return null;
        }

        @Override
        public List<String> getExistingControlledScopes() throws GeneralException {
            return null;
        }

        @Override
        public String getCapabilityRight() {
            return null;
        }

        @Override
        public String getControlledScopeRight() {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    public RoleEditorBean() throws GeneralException {
        super();
        //IIQTC-51 :- Since we are saving RoleViewerBean in the navigation history before arriving to RoleEditor,
        //we have to make sure to remove that saved item from the stack to avoid to go back to the wrong page in
        //the home page using widgets. The purpose of calling "back" is to make sure to call pop from the stack.
        NavigationHistory.getInstance().back();
        boolean isForceLoad = Boolean.parseBoolean((String)getSessionScope().remove(FORCE_LOAD));
        loadedObjs = new HashSet<String>();
        if (isForceLoad) {
            partialClearHttpSession();
        }
        Map session = getSessionScope();
        initObjectId();

        if (log.isDebugEnabled()) {
            log.debug("RoleEditorBean Session params: " + session.toString());
            log.debug("RoleEditorBean Request params: " + getRequestParam().toString());
        }
        pageSize = Configuration.getSystemConfig().getInt(Configuration.BUNDLES_UI_DISPLAY_LIMIT);

        if (pageSize <= 0) {
            pageSize = 50;
        }

        editedSelector = (IdentitySelectorDTO) getEditState(EDITED_SELECTOR);

        editMode = (EditMode) getEditState(ROLE_EDIT_MODE);

        if (editMode == null) {
            editMode = EditMode.Plain;
            addEditState(ROLE_EDIT_MODE, editMode);
        }

        Bundle editedRole = null;
        String workflowCaseId = (String) session.get(ATT_WORKFLOW_CASE_ID);

        if (workflowCaseId == null || workflowCaseId.trim().length() == 0) {
            // This is a normal edit
            editedRole = getObject();
        } else {
            // This is an edit coming off of a work item
            pendingWorkflowCase = getContext().getObjectById(WorkflowCase.class, workflowCaseId);
        }

        if (editedRole != null) {
            // This is a normal edit on a role with a pending workflow
            pendingWorkflowCase = editedRole.getPendingWorkflow();
        }

        if (pendingWorkflowCase != null) {
            if (pendingWorkflowCase.isAutoCreated()) {
                // In the case of import/export, a stubbed out WorkflowCase will be created if one cannot be found. This
                // will not contain an approvalObject.
                log.warn("Could not find ApprovalObject on WorkflowCase. Using existing Bundle and clearing " +
                        "the pending workflow.");
                editedRole = getObject();
                editedRole.setPendingWorkflow(null);
                pendingWorkflowCase = null;
            } else {
                // The logic here may seem confusing.  The first time we get a role
                // for editing, we may find that it has a pending work item.  If so,
                // that's what we want to edit, so we set the editedRole and edit state
                // accordingly.  On subsequent visits, the state and object will already
                // be appropriately set, so this block will be ignored.
                editedRole = (Bundle) pendingWorkflowCase.getApprovalObject();
                setObject(editedRole);
                setObjectId(editedRole.getId());
            }
        }

        editedProfiles = (Map<String, ProfileDTO>) getEditState(EDITED_PROFILES);
        if (editedProfiles == null) {
            editedProfiles = new HashMap<String, ProfileDTO>();
                /* Initialize editedProfiles with any non-persisted profiles
                 * on the role */
            if( editedRole != null && editedRole.getProfiles() != null ) {
                for( Profile profile : editedRole.getProfiles() ) {
                    if( profile.getId() == null ) {
                        ProfileDTO profileDto = new ProfileDTO( profile );
                        editedProfiles.put( profileDto.getId(), profileDto );
                    }
                }
            }
            addEditState(EDITED_PROFILES, editedProfiles);
        }

            /* Remove new profiles from role to prevent duplication when saving */
        if( editedRole != null && editedRole.getProfiles() != null ) {
            Iterator<Profile> iterator = editedRole.getProfiles().iterator();
            while( iterator.hasNext() ) {
                Profile profile = iterator.next();
                if( profile.getId() == null ) {
                    iterator.remove();
                }
            }
        }

        // The actual activityEnabled settings are contained in the ActivityConfig.
        // Since we are not ready to attach just yet, the RoleEditorBean will keep
        // track of this until it is ready to be persisted
        Boolean activityEnabled = (Boolean) getEditState(ACTIVITY_ENABLED);
        if (activityEnabled == null) {
            ActivityConfig activityConfig = editedRole.getActivityConfig();
            if (activityConfig == null) {
                this.activityEnabled = false;
            } else {
                this.activityEnabled = activityConfig.enabled();
            }
        } else {
            this.activityEnabled = activityEnabled.booleanValue();
        }

        Boolean b = (Boolean) getEditState(MERGE_TEMPLATES);
        if (b == null)
            this.mergeTemplates = editedRole.isMergeTemplates();
        else
            this.mergeTemplates = b.booleanValue();

        Boolean allowDuplicates = (Boolean) getEditState(ALLOW_DUPLICATE_ACCOUNTS);
        if (allowDuplicates == null) {
            this.allowDuplicateAccounts = editedRole.isAllowDuplicateAccounts();
        }
        else {
            this.allowDuplicateAccounts = allowDuplicates.booleanValue();
        }

        Boolean allowMultiple = (Boolean) getEditState(ALLOW_MULTIPLE_ASSIGNMENTS);
        if (allowMultiple == null) {
            this.allowMultipleAssignments = editedRole.isAllowMultipleAssignments();
        } else {
            this.allowMultipleAssignments = allowMultiple.booleanValue();
        }

        attributeEditBean = (RoleAttributeEditBean) getEditState(ROLE_ATTRIBUTE_EDIT);

        if (attributeEditBean == null) {
            resetAttributeEditBean(editedRole);
        }

        analysisResults = (ImpactAnalysis) getEditState(QUICK_IMPACT_ANALYSIS);

        events = (List<EventBean>) getEditState(ROLE_EVENTS);
        if (events == null) {
            buildEvents();
        }

        formEditBean = (FormEditBean) getEditState(ROLE_FORM_EDIT);
        if(formEditBean == null) {
            buildFormEditBean();
        }

        initTargetAccountSelectorRules();

        /** Load descriptions **/
        if(editedRole != null) {
            descriptionData = new DescriptionData(editedRole.getDescriptions(), getDefaultLanguage());
        }

        //load classification ids
        if(editedRole != null) {
            classificationIds = new ArrayList<String>();
            for (ObjectClassification cls : Util.safeIterable(editedRole.getClassifications())) {
                classificationIds.add(cls.getClassification().getId());
            }
        }

        roleMembership = (RoleMembershipDTO) getEditState(ROLE_MEMBERSHIP_EDIT);
        if (roleMembership == null) {
            roleMembership = new RoleMembershipDTO(editedRole);
            addEditState(ROLE_MEMBERSHIP_EDIT, roleMembership);
        }

        rolePermits = (RolePermitsDTO) getEditState(ROLE_PERMITS_EDIT);
        if (rolePermits == null) {
            rolePermits = new RolePermitsDTO(editedRole);
            addEditState(ROLE_PERMITS_EDIT, rolePermits);
        }

        roleRequirements = (RoleRequirementsDTO) getEditState(ROLE_REQUIREMENTS_EDIT);
        if (roleRequirements == null) {
            roleRequirements = new RoleRequirementsDTO(editedRole);
            addEditState(ROLE_REQUIREMENTS_EDIT, roleRequirements);
        }

        if (editMode == EditMode.Profile) {
//                if (editMode == EditMode.Profile || editMode == EditMode.SimpleEntitlement) {
            profileEditBean = (ProfileEditBean) getEditState(PROFILE_EDIT);
            if (profileEditBean == null) {
                log.error("Expected a profileEditBean but got nothing instead.");
            } else {
                editedProfileId = profileEditBean.getObject().getUid();
            }
        } else {
            // Dummy one up because a4j won't let us leave it blank if we want to submit things later,
            // but don't persist it
            profileEditBean = new ProfileEditBean(editedRole, new ProfileDTO(new Profile()));
        }

        if (editMode == EditMode.EntitlementMining) {
            entitlementMiningProfileEditBean = (EntitlementProfileMiningBean) getEditState(ENTITLEMENT_MINING_EDIT);
            if (entitlementMiningProfileEditBean == null) {
                entitlementMiningProfileEditBean = new EntitlementProfileMiningBean(editedRole, editedProfiles);
                addEditState(ENTITLEMENT_MINING_EDIT, entitlementMiningProfileEditBean);
            }
        } else {
            // Dummy one up because a4j won't let us leave it blank if we want to submit things later,
            // but don't persist it
            entitlementMiningProfileEditBean = new EntitlementProfileMiningBean(editedRole, editedProfiles);
        }
    }

    @Override
    public boolean isStoredOnSession() {
        return true;
    }

    @Override
    protected Class<Bundle> getScope() {
        return Bundle.class;
    }

    @Override
    public Bundle getObject() throws GeneralException {
        Bundle editedObj = super.getObject();
        // This method is being called a lot.  Instead of loading the role and its
        // hierarchy every time let's just do it once.  Ideally we could just do
        // it only when the role is null but unfortunately we sometimes get stale objects on the
        // session that are replaced and we always want to load new stuff.  So we're
        // forced keep track of what the bean has currently loaded in the loadedObjs set. --Bernie
        if (editedObj.getId() != null && !loadedObjs.contains(editedObj.getId())) {
            editedObj.load();
        }

        if (editedObj.getId() != null) {
            loadedObjs.add(editedObj.getId());
        }

        // If this is a new role, initialize the attributes with default values
        if (editedObj.getAttributes() == null || editedObj.getAttributes().isEmpty()) {
            if (isNewRole()) {
                ObjectConfig config = ObjectConfig.getObjectConfig(Bundle.class);
                setDefaultAttributeValuesForNewRole(editedObj, config);
            } else {
                editedObj.setAttributes(new Attributes<String, Object>());
            }
        }

        log.debug("Returning " + editedObj + " with supers: " + editedObj.getInheritance());

        return editedObj;
    }

    boolean isNewRole()
    {
        if (getObjectId() == null)
            initObjectId();
        return getObjectId() == null || getObjectId().equals("new");
    }


    protected boolean isAuthorized(SailPointObject object) throws GeneralException {

        String workItemId = (String) getSessionScope().get(ATT_WORK_ITEM_ID);
        if (workItemId != null){
            WorkItem item = getContext().getObjectById(WorkItem.class, workItemId);
            if (item != null && WorkItemAuthorizer.isAuthorized(item, this, true)) {
                return true;
            }
        }

        // If we were not refered here from an authorized work item, only ManageRole rights should let you in.
        if (!Authorizer.hasAccess(getLoggedInUserCapabilities(), getLoggedInUserRights(), SPRight.ManageRole)) {
            return false;
        }

        return super.isAuthorized(object);
    }

    public String getRoleType() {
        String type = null;

        try {
            Bundle role = getObject();
            type = role.getType();
        } catch (GeneralException e) {
            log.error("No role is available to edit right now.", e);
        }

        // Default the role type select box to the first available role type
        // The intention here is to set a default so that more of the UI is enabled
        // when initially viewed
        if (type == null) {
            type = getDefaultRoleType();
        }

        return type;
    }

    public List<String> getClassificationIds() {
        return classificationIds;
    }

    public void setClassificationIds(List<String> ids) {
        classificationIds = ids;
    }

    private String getDefaultRoleType() {

        List<SelectItem> availableTypes = getAvailableRoleTypes();
        if (!Util.isEmpty(availableTypes)) {
            return (String)availableTypes.get(0).getValue();
        }
        return null;
    }

    public void setRoleType(String type) {
        if (type != null) {
            try {
                Bundle role = getObject();
                role.setType(type);
                if (isNewRole()) {
                    setDefaultAttributeValuesForNewRole(role, ObjectConfig.getObjectConfig(Bundle.class));
                }
            } catch (GeneralException e) {
                log.error("No role is available to edit right now.", e);
            }
        }
    }

    public String getEditMode() {
        return editMode.name();
    }

    public void setEditMode(String editMode) {
        this.editMode = EditMode.valueOf(editMode);
    }

    public String getEditedProfileId() {
        return editedProfileId;
    }

    public void setEditedProfileId(String editedProfileId) {
        this.editedProfileId = editedProfileId;
    }

    public AttributeEditBean getAttributeEditor() {
        return attributeEditBean;
    }

    public void setAttributeEditor(RoleAttributeEditBean config) {
        attributeEditBean = config;
    }

    public ProfileEditBean getProfileEditor() {
        return profileEditBean;
    }

    public void setProfileEditor(ProfileEditBean config) {
        profileEditBean = config;
    }

    public EntitlementProfileMiningBean getProfileMiningEditor() {
        return entitlementMiningProfileEditBean;
    }

    public void setProfileMiningEditor(EntitlementProfileMiningBean config) {
        entitlementMiningProfileEditBean = config;
    }

    public boolean isActivityEnabled() {
        return activityEnabled;
    }

    public void setActivityEnabled(boolean value) {
        activityEnabled = value;
        addEditState(ACTIVITY_ENABLED, Boolean.valueOf(activityEnabled));
    }

    public boolean isMergeTemplates() {
        return mergeTemplates;
    }

    public void setMergeTemplates(boolean value) {
        mergeTemplates = value;
        addEditState(MERGE_TEMPLATES, Boolean.valueOf(mergeTemplates));
    }

    public boolean isAllowDuplicateAccounts() {
        return allowDuplicateAccounts;
    }

    public void setAllowDuplicateAccounts(boolean value) {
        allowDuplicateAccounts = value;
        addEditState(ALLOW_DUPLICATE_ACCOUNTS, Boolean.valueOf(allowDuplicateAccounts));
    }

    public boolean isAllowMultipleAssignments() {
        return allowMultipleAssignments;
    }

    public void setAllowMultipleAssignments(boolean value) {
        allowMultipleAssignments = value;
        addEditState(ALLOW_MULTIPLE_ASSIGNMENTS, Boolean.valueOf(allowMultipleAssignments));
    }

    public boolean isAllowMultipleAssignmentsVisible() {
        Configuration systemConfig = Configuration.getSystemConfig();

        boolean systemMultipleAssignmentEnabled = systemConfig.getBoolean(Configuration.ENABLE_MODELER_MULTIPLE_ASSIGNMENTS, false);
        boolean systemGlobalMultipleAssignmentEnabled = systemConfig.getBoolean(Configuration.ALLOW_MULTIPLE_ROLE_ASSIGNMENTS, false);
        boolean roleEditorAllowMultipleAssignments = isAllowMultipleAssignments();

        // make sure role type is assignable and multiple assigment is enabled, if
        // global flag is true then no need to show
        return isRoleTypeAssignable() && (systemMultipleAssignmentEnabled || roleEditorAllowMultipleAssignments) && !systemGlobalMultipleAssignmentEnabled;
    }

    public boolean isAllowMultipleApplicationAccountsVisible() {
        Configuration systemConfig = Configuration.getSystemConfig();
        boolean systemAllowDuplicateAccounts = systemConfig.getBoolean(Configuration.ALLOW_DUPLICATE_ACCOUNTS, false);
        boolean roleTypeAllowDuplicateAccounts = getRoleTypeDefinition() != null && getRoleTypeDefinition().isAllowDuplicateAccounts();
        boolean roleEditorAllowDuplicateAccounts = isAllowDuplicateAccounts();

        return roleTypeAllowDuplicateAccounts && (systemAllowDuplicateAccounts || roleEditorAllowDuplicateAccounts);
    }

    public boolean isRoleTypeAssignable() {
        return getRoleTypeDefinition() != null && getRoleTypeDefinition().isAssignable();
    }


    public List<PolicyConflict> getQuickAnalysisResults() {
        List<PolicyConflict> conflicts = new ArrayList<PolicyConflict>();

        if (analysisResults != null) {
            List<PolicyConflict> policyConflicts = analysisResults.getPolicyConflicts();
            if (policyConflicts != null)
                conflicts.addAll(policyConflicts);
        }

        return conflicts;
    }

    public boolean isImpactAnalysisEnabled() {
        return !UIConfig.getUIConfig().isRoleEditorImpactAnalysisDisabled();
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Role suggest filters
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Generic method to return a list of roles with certain properties
     * rendered in JSON for the RoleFilter suggest component.
     *
     * This is based on RoleViewerBean.getRoleByQuery but that is also used
     * for grids and we don't need everything.  We also add more filters.
     * Consider merging these someday...
     */
    @SuppressWarnings("unchecked")
    public String getAllowedRolesJson(List<String> types, Filter[] additionalRestrictions) {

        String json = null;

        Map requestParams = getRequestParam();
        log.debug("Request params: " + requestParams.toString());
        String queryVal = (String) requestParams.get("query");
        String orderBy = (String) requestParams.get("sort");
        if (orderBy == null)
            orderBy = "name";

        if ("roleType".equals(orderBy)) {
            // Workaround: 'type' conflicted with a built-in ExtJS property, so
            // we have to use 'roleType' instead on the browser and make a
            // conversion here -- Bernie
            orderBy = "type";
        }
        String sortDir = (String) requestParams.get("dir");
        int start = -1;
        String startString = (String) requestParams.get("start");
        if (startString != null) {
            start = Integer.parseInt(startString);
        }
        int limit = getResultLimit();

        try {
            SailPointContext ctx = getContext();
            QueryOptions ops = new QueryOptions();

            ops.setScopeResults(true);
            ops.addOwnerScope(super.getLoggedInUser());

            // Do a like query if a name is specified from the filter
            if (queryVal != null) {
                ops.add(Filter.ignoreCase(Filter.like("name", queryVal)));
            }

            if (types != null && types.size() > 0) {
                ops.add(Filter.or(Filter.isnull("type"),
                                  Filter.in("type", types)));
            }

            if (additionalRestrictions != null && additionalRestrictions.length > 0) {
                for (int i = 0; i < additionalRestrictions.length; ++i) {
                    if (additionalRestrictions[i] != null) {
                        ops.add(additionalRestrictions[i]);
                    }
                }
            }

            // Filter out existing roles
            String rolesToExcludeString = (String) requestParams.get("existingRoleIds");

            if (rolesToExcludeString != null && rolesToExcludeString.trim().length() > 0) {
                List<String> rolesToExcludeIds = Util.csvToList(rolesToExcludeString);
                ops.add(Filter.not(Filter.in("id", rolesToExcludeIds)));
            }


            int numRoleResults = ctx.countObjects(Bundle.class, ops);

            if (orderBy != null) {
                ops.addOrdering(orderBy, sortDir == null || "ASC".equals(sortDir));
            }

            if (limit > 0) {
                ops.setResultLimit(limit);
            }

            ops.setFirstRow(start);

            Iterator<Object[]> queriedRoles = ctx.search(Bundle.class, ops, Arrays.asList(new String [] {"id", "name", "type", "displayableName"}));

            json = RoleUtil.getGridJsonForRoles(
                                ObjectConfig.getObjectConfig(Bundle.class),
                                queriedRoles,
                                numRoleResults,
                                getContext());
        } catch (GeneralException e) {
            log.error("Could not fetch the roles at this time.", e);
            json = "{}";
        }

        log.debug("Returning " + json);
        return json;


    }

    /**
     * Sigh this would be easier with reflection or a generic
     * property accessor.
     */
    @SuppressWarnings("unchecked")
    public String getAllowedInheritedJson() {

        List<String> allowedTypes = new ArrayList<String>();
        try {
            RoleConfig rc = new RoleConfig();
            Collection<RoleTypeDefinition> types = rc.getRoleTypeDefinitionsList();
            if (types != null) {
                for (RoleTypeDefinition type : types) {
                    if (!type.isNoSubs())
                        allowedTypes.add(type.getName());
                }
            }
        }
        catch (Throwable t) {
            log.error(t);
        }

        // Build a filter to prevent us from returning roles that are already in the hierarchy
        Map requestParams = getRequestParam();
        String editedRoleId = (String) requestParams.get("editedRoleId");
        Set<String> roleIdsToExclude = new HashSet<String>();
        roleIdsToExclude.add(editedRoleId);

        try {
            Set<Bundle> rolesToExclude;

            if (editedRoleId != null && (editedRoleId.trim().length() > 0) && !"new".equals(editedRoleId)) {
                Bundle editedRole = getContext().getObjectById(Bundle.class, editedRoleId);
                rolesToExclude = RoleUtil.getAllRolesInHierarchy(getContext(), editedRole);
            } else {
                rolesToExclude = null;
            }

            if (rolesToExclude != null && !rolesToExclude.isEmpty()) {
                for (Bundle roleToExclude : rolesToExclude) {
                    roleIdsToExclude.add(roleToExclude.getId());
                }
            }
        } catch (GeneralException e) {
            log.error("Failed to find the roles to exclude ", e);
        }

        Filter[] excludeRolesInHierarchy = new Filter[] { Filter.not(Filter.in("id", roleIdsToExclude)) };

        return getAllowedRolesJson(allowedTypes, excludeRolesInHierarchy);
    }

    public String getAllowedPermitsJson() {

        List<String> allowedTypes = new ArrayList<String>();
        try {
            RoleConfig rc = new RoleConfig();
            Collection<RoleTypeDefinition> types = rc.getRoleTypeDefinitionsList();
            if (types != null) {
                for (RoleTypeDefinition type : types) {
                    if (!type.isNotPermittable())
                        allowedTypes.add(type.getName());
                }
            }
        }
        catch (Throwable t) {
            log.error(t);
        }
        return getAllowedRolesJson(allowedTypes, null);
    }

    public String getAllowedRequirementsJson() {

        List<String> allowedTypes = new ArrayList<String>();
        try {
            RoleConfig rc = new RoleConfig();
            Collection<RoleTypeDefinition> types = rc.getRoleTypeDefinitionsList();
            if (types != null) {
                for (RoleTypeDefinition type : types) {
                    if (!type.isNotRequired())
                        allowedTypes.add(type.getName());
                }
            }
        }
        catch (Throwable t) {
            log.error(t);
        }
        return getAllowedRolesJson(allowedTypes, null);
    }

    /**
     * This isn't used during editing but we'll need it in the
     * Identity pages.
     */
    public String getAllowedManualJson() {

        List<String> allowedTypes = new ArrayList<String>();
        try {
            RoleConfig rc = new RoleConfig();
            Collection<RoleTypeDefinition> types = rc.getRoleTypeDefinitionsList();
            if (types != null) {
                for (RoleTypeDefinition type : types) {
                    if (!type.isNoManualAssignment())
                        allowedTypes.add(type.getName());
                }
            }
        }
        catch (Throwable t) {
            log.error(t);
        }
        return getAllowedRolesJson(allowedTypes, null);
    }

    public boolean isHasArchives() {
        final boolean hasArchives;

        String roleId = this.getObjectId();
        if (roleId == null || roleId.trim().length() == 0) {
            hasArchives = false;
        } else {
            List<BundleArchive> archives;
            try {
                archives = getContext().getObjects(BundleArchive.class, new QueryOptions(Filter.eq("sourceId", roleId)));
            } catch (IllegalArgumentException e) {
                archives = new ArrayList<BundleArchive>();
                log.error("Unable to get role archives", e);
            } catch (GeneralException e) {
                archives = new ArrayList<BundleArchive>();
                log.error("Unable to get role archives", e);
            }

            hasArchives = !archives.isEmpty();
        }

        return hasArchives;
    }

    public void setHasArchives(final boolean hasArchives) {
        // Do nothing.  This is a dummy method whose sole purpose is to make JSF happy
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Assignment Selector
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Get the type definition of the currently selected type.
     * !! this needs to be cached so we don't keep reading
     * the ObjectConfig:Bundle.
     */
    public RoleTypeDefinition getRoleTypeDefinition() {
        RoleTypeDefinition typedef = null;
        try {
            Bundle role = getObject();
            String type = role.getType();
            if (type != null) {
                RoleConfig roleConfig = new RoleConfig();
                typedef = roleConfig.getRoleTypeDefinition(type);
            }
        }
        catch (GeneralException e) {
            addMessage(e);
        }
        return typedef;
    }

    /**
     * Return a non-null selector DTO if this role
     * already has a selector or the role type says
     * it is allowed to have one.
     */
    public IdentitySelectorDTO getAssignmentSelector() {

        try {
            if (editedSelector == null) {
                Bundle role = getObject();
                IdentitySelector sel = role.getSelector();
                if (sel != null && !sel.isEmpty()) {
                    // doesn't matter what the type says, always display
                    // should add a warning message somewhere though...
                    editedSelector = new IdentitySelectorDTO(sel);
                }
                else {
                    editedSelector = new IdentitySelectorDTO();
                    editedSelector.setType(IdentitySelectorDTO.SELECTOR_TYPE_NONE);
                }
                if (editedSelector != null) {
                    editedSelector.setAllowTypeNone(true);
                    addEditState(EDITED_SELECTOR, editedSelector);
                }
            }
        }
        catch (GeneralException e) {
            addMessage(e);
        }

        return editedSelector;
    }

    /**
     * Return true if we should display an assignment selector section.
     * Will also bootstrap one for editing.
     */
    public boolean isAssignable() {
        return (getAssignmentSelector() != null);
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Permits List
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Return a non-null permits list DTO if this role
     * already has a permits list or the role type says
     * it is allowed to have one.
     */
    public RolePermitsDTO getPermitsEditor() {

        try {
            if (rolePermits == null) {
                Bundle role = getObject();
                List<Bundle> permits = role.getPermits();
                if (permits != null && permits.size() > 0) {
                    // doesn't matter what the type says, always display
                    // should add a warning message somewhere though...
                    rolePermits = new RolePermitsDTO(role);
                }
                else {
                    RoleTypeDefinition type = getRoleTypeDefinition();
                    if (type == null || !type.isNoPermits())
                        rolePermits = new RolePermitsDTO(role);
                }
                if (rolePermits != null)
                    addEditState(ROLE_PERMITS_EDIT, rolePermits);
            }
        }
        catch (GeneralException e) {
            addMessage(e);
        }

        return rolePermits;
    }

    /**
     * Return true if we should display a permits list editor.
     */
    public boolean isPermitsAllowed() {
        return (getPermitsEditor() != null);
    }

    public List<RoleInfo> getPermittedRoles() {
        List<RoleInfo> roleInfo = null;
        RoleReferenceDTO editor = getPermitsEditor();
        if (editor != null)
            roleInfo = editor.getCurrentRoleInfos();
        return roleInfo;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Requirements List
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Return a non-null requirements list DTO if this role
     * already has a requiremnts list or the role type says
     * it is allowed to have one.
     */
    public RoleRequirementsDTO getRequirementsEditor() {

        try {
            if (roleRequirements == null) {
                Bundle role = getObject();
                List<Bundle> permits = role.getRequirements();
                if (permits != null && permits.size() > 0) {
                    // doesn't matter what the type says, always display
                    // should add a warning message somewhere though...
                    roleRequirements = new RoleRequirementsDTO(role);
                }
                else {
                    RoleTypeDefinition type = getRoleTypeDefinition();
                    // TODO: we may want !type.isNoPermits to control both the
                    // permits and requirements list display?
                    if (type == null || !type.isNoRequirements())
                        roleRequirements = new RoleRequirementsDTO(role);
                }
                if (roleRequirements != null)
                    addEditState(ROLE_REQUIREMENTS_EDIT, roleRequirements);
            }
        }
        catch (GeneralException e) {
            addMessage(e);
        }

        return roleRequirements;
    }

    /**
     * Return true if we should display a requirements list editor.
     */
    public boolean isRequirementsAllowed() {
        return (getRequirementsEditor() != null);
    }

    public List<RoleInfo> getRequiredRoles() {
        List<RoleInfo> roleInfo = null;
        RoleReferenceDTO editor = getRequirementsEditor();
        if (editor != null)
            roleInfo = editor.getCurrentRoleInfos();
        return roleInfo;
    }

    /**
     * The JSON data source for the RoleFilter suggest component
     * that returns only roles that can be allowed on a permits list.
     */

    /**
     * The JSON data source for the RoleFilter suggest component
     * that returns only roles that can be allowed on a permits list.
     */

    //////////////////////////////////////////////////////////////////////
    //
    // Inheritance (membership) List
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Return a non-null membership list DTO if this role
     * already has inheritance list or the role type says
     * it is allowed to have one.
     */
    public RoleMembershipDTO getMembershipEditor() {

        try {
            if (roleMembership == null) {
                Bundle role = getObject();
                List<Bundle> supers = role.getInheritance();
                if (supers != null && supers.size() > 0) {
                    // doesn't matter what the type says, always display
                    // should add a warning message somewhere though...
                    roleMembership = new RoleMembershipDTO(role);
                }
                else {
                    RoleTypeDefinition type = getRoleTypeDefinition();
                    if (type == null || !type.isNoSupers())
                        roleMembership = new RoleMembershipDTO(role);
                }
                if (roleMembership != null)
                    addEditState(ROLE_MEMBERSHIP_EDIT, roleMembership);
            }
        }
        catch (GeneralException e) {
            addMessage(e);
        }

        return roleMembership;
    }

    /**
     * Return true if we should display a membership list editor.
     */
    public boolean isMembershipAllowed() {
        return (getMembershipEditor() != null);
    }

    public List<RoleInfo> getMemberOfRoles() {
        List<RoleInfo> roleInfo = null;
        RoleMembershipDTO editor = getMembershipEditor();
        if (editor != null)
            roleInfo = editor.getCurrentRoleInfos();
        return roleInfo;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Entitlement Profiles
    //
    //////////////////////////////////////////////////////////////////////


    //////////////////////////////////////////////////////////////////////
    //
    // Misc
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * @return true if the logged in user is a System Administrator or the role's owner
     */
    public boolean isCancelWorkflowAllowed() {
        boolean cancelAllowed;

        try {
            if (pendingWorkflowCase == null) {
                cancelAllowed = false;
            } else {
                cancelAllowed = getLoggedInUser().getCapabilityManager().hasCapability(Capability.SYSTEM_ADMINISTRATOR);
                if (!cancelAllowed) {
                    String launcherName = pendingWorkflowCase.getLauncher();
                    if (launcherName != null)
                        cancelAllowed = launcherName.equals(getLoggedInUser().getName());
                }
            }
        } catch (GeneralException e) {
            cancelAllowed = false;
            log.error(new Message(Message.Type.Error, MessageKeys.ERR_EXCEPTION, e), e);
        }

        return cancelAllowed;
    }

    @Override
    protected void initObjectId() {
        if (_objectId == null) {
            String objectId = getRequestOrSessionParameter("editForm:roleToEdit");

            if (objectId == null || objectId.trim().length() == 0) {
                objectId = getRequestOrSessionParameter("roleToEdit");
            }

            if (objectId == null || objectId.trim().length() == 0) {
                super.initObjectId();
            } else {
                setObjectId(objectId);
            }
        }
    }

    @Override
    public Bundle createObject() {
        setObjectId("new");
        return new Bundle();
    }

    /**
     * Flush any HttpSession state when we're logically
     * done with an object.  May be overloaded if the subclass
     * has its own attributes.
     */
    @SuppressWarnings("unchecked")
    @Override
    public void clearHttpSession() {
        super.clearHttpSession();
        Map session = getSessionScope();
        session.remove("roleToEdit");
        session.remove(ATT_WORK_ITEM_ID);
        session.remove(ATT_WORKFLOW_CASE_ID);
        loadedObjs = new HashSet<String>();
    }

    private void partialClearHttpSession() {
        // Don't call the local version because that will whack our editedRoleId too and we
        // don't want to do that
        super.clearHttpSession();
        super.clearEditState();
        Map session = getSessionScope();
        session.remove(ATT_WORK_ITEM_ID);
        session.remove(ATT_WORKFLOW_CASE_ID);

    }

    public void setSelectedRoleIncludeOption(final String selectedRoleIncludeOption) {
        this.seletedRoleIncludeOption = selectedRoleIncludeOption;
    }

    public String getSelectedRoleIncludeOption() {
        return seletedRoleIncludeOption;
    }

    public void setSelectedArchive(final String selectedArchive) {
        this.selectedArchive = selectedArchive;
    }

    public String getSelectedArchive() {
        return selectedArchive;
    }

    public String getRoleOwner() throws GeneralException {
        Identity owner = getObject().getOwner();
        if (owner == null) {
            owner = getLoggedInUser();
        }

        String ownerId = null;
        if (owner != null)
            ownerId = owner.getId();
        return ownerId;
    }

    public void setRoleOwner(String roleOwner) throws GeneralException {
        Identity owner;

        if (roleOwner != null && roleOwner.trim().length() > 0) {
            owner = getContext().getObjectById(Identity.class, roleOwner);
            getObject().setOwner(owner);
        }
    }

    public String getRoleOwnerName() {
        String ownerName = null;
        try {
            Identity roleOwner = getObject().getOwner();

            if (roleOwner == null) {
                roleOwner = getLoggedInUser();
            }

            if (roleOwner != null) {
                //IIQETN-6257 :- Escaping the given role owner name to replace single-quotes
                //with escaped single quotes.
                ownerName = WebUtil.escapeJavascript(roleOwner.getDisplayableName());
            }
        } catch (GeneralException e) {
            ownerName = null;
        }

        return ownerName;
    }

    public List<SelectItem> getAvailableRoleTypes() {
        List<SelectItem> availableRoleTypes = new ArrayList<SelectItem>();
        try {
            RoleConfig roleConfig = new RoleConfig();

            List<RoleTypeDefinition> roleTypeDefs = roleConfig.getRoleTypeDefinitionsList();

            for (RoleTypeDefinition roleTypeDef : roleTypeDefs) {
                if (RoleUtil.isRoleManagementPermitted(roleTypeDef, getLoggedInUser())) {
                    availableRoleTypes.add(new SelectItem(roleTypeDef.getName(), roleTypeDef.getDisplayableName()));
                }
            }
        } catch (GeneralException e) {
            log.error(e);
        }

        if (availableRoleTypes.size() == 0 && _object != null && _object.getType() != null) {
            RoleTypeDefinition currentTypeDefintion = getRoleTypeDefinition();
            if (currentTypeDefintion != null) {
                availableRoleTypes.add(new SelectItem(currentTypeDefintion.getName(), currentTypeDefintion.getDisplayableName()));
            }
        }

        return availableRoleTypes;
    }

    /**
     * Helper methopd to return application selector rules map
     *
     * @return
     * @throws GeneralException
     */
    private Map<String, Rule> getTargetAccountSelectorRuleNameMap() throws GeneralException
    {
        List<ApplicationAccountSelectorRule> rules = getObject().getApplicationAccountSelectorRules();

        Map<String, Rule> ruleNameMap = new HashMap<String, Rule>();
        // Add app names that have selector rule
        if (rules != null)
        {
            for (ApplicationAccountSelectorRule rule : rules)
            {
                if (rule != null)
                {
                    ruleNameMap.put(rule.getApplication().getName(), rule.getRule());
                }
            }
        }

        return ruleNameMap;
    }

    /**
     * List may initially be empty but we want to show all the applications that may
     * potentially have target account selector rules
     *
     * @return
     * @throws GeneralException
     */
    public List<ApplicationAccountSelectorRuleDTO> getProvisioningTargetAccountSelectorRules() throws GeneralException
    {
        if (targetAccountselectorRules == null)
        {
            initTargetAccountSelectorRules();
        }

        return targetAccountselectorRules;
    }

    /**
     * This will just set the whole rules list
     *
     * @throws GeneralException
     */
    public void saveProvisioningTargetAccountSelectorRules(Bundle role) throws GeneralException
    {
        if (targetAccountselectorRules == null)
        {
            return;
        }

        // Transfer DTO into real
        List<ApplicationAccountSelectorRule> applicationAccountSelectorRuleList = new ArrayList<ApplicationAccountSelectorRule>();

        for (ApplicationAccountSelectorRuleDTO dto : targetAccountselectorRules)
        {
            if (dto == null)
            {
                continue;
            }
            
            String appName = dto.getApplicationName();
            
            if (appName != null && appName.equals(getMessage(MessageKeys.ROLE_TARGET_RULE)))
            {
                Rule rule = getContext().getObjectByName(Rule.class, dto.getRuleName());
                role.setAccountSelectorRule(rule);
                continue;
            }
            
            Application app = getContext().getObjectByName(Application.class, appName);
            Rule rule = getContext().getObjectByName(Rule.class, dto.getRuleName());
            if (rule != null) {
                ApplicationAccountSelectorRule applicationAccountSelectorRule = new ApplicationAccountSelectorRule(app, rule);

                applicationAccountSelectorRuleList.add(applicationAccountSelectorRule);
            }
        }

        role.setApplicationAccountSelectorRules(applicationAccountSelectorRuleList);
        attributeEditBean.getAttributes().put(Bundle.ATT_ACCOUNT_SELECTOR_RULES, role.getAccountSelectorRules());
    }

    /**
     * Initialize target account selector rules list
     * Make sure to include editedProfiles list to include those that were added/deleted during edit session
     * 
     * @throws GeneralException
     */
    private void initTargetAccountSelectorRules() throws GeneralException
    {
        // setup target account selector rules
        Map<String, Rule> ruleNameMap = getTargetAccountSelectorRuleNameMap();

        targetAccountselectorRules = (List<ApplicationAccountSelectorRuleDTO>) getEditState(ROLE_ACCOUNT_SELECTOR_RULES_EDIT);

        if (targetAccountselectorRules == null) {
        targetAccountselectorRules = new ArrayList<ApplicationAccountSelectorRuleDTO>();

        // Add general rule for bundle
        Rule bundleRule = getObject().getAccountSelectorRule();
        String bundleRuleName = bundleRule != null ? bundleRule.getName() : null;
        targetAccountselectorRules.add(new ApplicationAccountSelectorRuleDTO(getMessage(MessageKeys.ROLE_TARGET_RULE), bundleRuleName));
            addEditState(ROLE_ACCOUNT_SELECTOR_RULES_EDIT, targetAccountselectorRules);
        }

        List<ProfileDTO> profs = getCurrentProfiles();
        Set<String> appSet = new HashSet<String>();

        for (ApplicationAccountSelectorRuleDTO existingSelectorRule : targetAccountselectorRules) {
            appSet.add(existingSelectorRule.getApplicationName());
        }

        for (ProfileDTO prof : profs) 
        {
            Rule rule = null;

            Application app = prof.getApplication();

            // don't add duplicate apps
            if (appSet.contains(app.getName())) 
            {
                continue;
            }

            if (ruleNameMap.containsKey(app.getName())) 
            {
                rule = ruleNameMap.get(app.getName());
            }

            String ruleName = (rule != null ? rule.getName() : null);

            targetAccountselectorRules.add(new ApplicationAccountSelectorRuleDTO(app.getName(), ruleName));

            appSet.add(app.getName());
        }
    }

    /**
     * Return a list of account selector rules
     *
     * @return
     * @throws GeneralException
     */
    public List<SelectItem> getAccountSelectorRules() throws GeneralException {
        String ruleName = getObject().getAccountSelectorRule() != null ? getObject().getAccountSelectorRule().getName() : null;
        return WebUtil.getRulesByType(getContext(), Rule.Type.AccountSelector, true, false, ruleName);
    }

    @SuppressWarnings("unchecked")
    public String rollbackAction() {
        String archiveId = getSelectedArchive();
        // I don't know why this isn't working.  There's nothing to validate on the stupid rollback form.
        // Hack around it for now. --Bernie
        if (archiveId == null) {
            archiveId = (String) getRequestParam().get("rollbackForm:selectedArchive");
        }

        if (archiveId != null && archiveId.trim().length() > 0) {
            try {
                BundleArchive archive = getContext().getObjectById(BundleArchive.class, archiveId);
                if (archive != null) {
                    RoleLifecycler lifecycler = new RoleLifecycler(getContext());
                    Bundle roleToEdit = lifecycler.rehydrate(archive);
                    roleToEdit.load();
                    getSessionScope().put(getSessionKey(), roleToEdit);
                    setObject(roleToEdit);
                    descriptionData = new DescriptionData(roleToEdit.getDescriptions(), getDefaultLanguage());
                    resetAttributeEditBean(roleToEdit);
                    roleMembership = new RoleMembershipDTO(roleToEdit);
                    addEditState(ROLE_MEMBERSHIP_EDIT, roleMembership);
                    rolePermits = new RolePermitsDTO(roleToEdit);
                    addEditState(ROLE_PERMITS_EDIT, rolePermits);
                    roleRequirements = new RoleRequirementsDTO(roleToEdit);
                    addEditState(ROLE_REQUIREMENTS_EDIT, roleRequirements);
                    ActivityConfig activityConfig = roleToEdit.getActivityConfig();
                    if (activityConfig == null) {
                        activityEnabled = false;
                    } else {
                        activityEnabled = activityConfig.enabled();
                    }
                    addEditState(ACTIVITY_ENABLED, activityEnabled);
                    addEditState(MERGE_TEMPLATES, roleToEdit.isMergeTemplates());
                }
            } catch (GeneralException e) {
                log.error("Failed to perform a rollback on the role editor", e);
            }
        }

        return "";
    }

    public String cancelAction() {
        boolean isFromViewer = isFromViewer();
        String workItemId = (String) getSessionScope().get(ATT_WORK_ITEM_ID);
        clearHttpSession();

        String result;

        if (isFromViewer) {
            result = "cancel";
        } else {
            getSessionScope().put(WorkItemBean.ATT_ITEM_ID, workItemId);
            result = "backToWorkItem";
        }

        return result;
    }

    public String getEntitlementsView() throws JSONException {
        JSONArray entitlements;

        try {
            Bundle role = getObject();
            if (role != null) {
                entitlements = new JSONArray(RoleUtil.getDirectEntitlementsJson(role, null, true));
            } else {
                entitlements = new JSONArray();
            }
        } catch (GeneralException e) {
            log.error(e);
            entitlements = new JSONArray();
        }

        log.debug("getEntitlementsView returning: " + entitlements.toString());

        return entitlements.toString();
    }

    public String getEntitlements() {

        Bundle role = null;
        try {
            role = getObject();
        } catch (GeneralException e) {
            log.error(e);
        }
        if(null == role){
            return "{}";
        }
        Writer jsonString = new StringWriter();
        try{
            JSONWriter jsonWriter = new JSONWriter(jsonString);

            jsonWriter.object();

            jsonWriter.key("entitlements");

            int numEntitlements;

            List<ProfileDTO> currentProfiles = getCurrentProfiles();
            jsonWriter.value(RoleUtil.getDirectEntitlementsJson(role, currentProfiles, true));

            if (Util.isEmpty(currentProfiles)) {
                numEntitlements = 0;
            } else {
                numEntitlements = currentProfiles.size();
            }

            jsonWriter.key("numEntitlements");

            jsonWriter.value(numEntitlements);

            jsonWriter.endObject();
        } catch (JSONException e) {
            log.error(e);
        } catch (GeneralException e) {
            log.error(e);
        }

        log.debug("getEntitlements returning: " + jsonString.toString());
        return jsonString.toString();
    }

    /*
     * Get all the profiles as they currently exist within the editor.
     * This includes profiles edited within the session as well as previously
     * existing profiles that have not yet been edited. 
     */
    private List<ProfileDTO> getCurrentProfiles() throws GeneralException {
        Bundle role = getObject();
        List<Profile> profiles = role.getProfiles();
        List<ProfileDTO> currentProfiles = new ArrayList<ProfileDTO>();
        currentProfiles.addAll(editedProfiles.values());
        if (profiles != null) {
            for (Profile profile : profiles) {
                String profileId = profile.getId();
                if (profileId != null && !editedProfiles.containsKey(profileId)) {
                    currentProfiles.add(new ProfileDTO(profile));
                }
            }
        }
        return currentProfiles;
    }
    
    public boolean isSimpleRole(){
        try{
            Bundle role = getObject();

            List<ProfileDTO> currentProfiles = getCurrentProfiles();
            return RoleUtil.hasSimpleEntitlements(role, currentProfiles);

        } catch(Exception e){
            log.error(e);
            return false;
        }
    }

    public void setSimpleRole(Boolean value){
        simpleRole = value;
    }

    public String getSimpleEntitlements() {

        Bundle role = null;
        try {
            role = getObject();
        } catch (GeneralException e) {
            log.error(e);
        }
        if(null == role){
            return "{}";
        }
        //if(RoleUtil.hasSimpleEntitlements(role)){
            try{
                List<ProfileDTO> currentProfiles = getCurrentProfiles();
                String json = RoleUtil.getReadOnlySimpleEntitlementsJson(role, getRequestParam(), currentProfiles, true, getLocale(), new RoleUtil.StrictSimpleEntitlementCriteria());
                return json;
            } catch (JSONException e) {
                log.error(e);
            } catch (Exception e) {
                log.error(e);
            }
        //}
        //if you got here something bad happened
        return "{}";
    }

    @SuppressWarnings("unchecked")
    @Override
    public String saveAction() {
        String outcome = null;
        String roleName = null;

        try {
            //save existing profile ids for later comparison
            List<String> existingProfileIds = new ArrayList<String>();
            Bundle oldRole = getObject();
            if (oldRole != null && oldRole.getProfiles() != null) {
                for (Profile p : oldRole.getProfiles()) {
                    existingProfileIds.add(p.getId());
                }
            }

            Bundle role = getRoleReadyForWorkflow();
            if (role != null) {
                roleName = role.getName();
                String workflowName = (String) Configuration.getSystemConfig().get(Configuration.WORKFLOW_ROLE_APPROVAL);

                if (workflowName == null) {
                    outcome = super.saveAction();
                    // When there is no workflow we need to persist descriptions to localized attributes immediately
                    Describer describer = new Describer((Describable)role);
                    SailPointContext ctx = getContext();
                    describer.saveLocalizedAttributes(ctx);
                    ctx.commitTransaction();
                    addMessageToSession(new Message(Message.Type.Info, MessageKeys.ROLE_SAVED_SUCCESSFULLY, new Object [] {roleName}));
                } else {
                    if (null != attributeEditBean) {
                        // Need to do this in case there is an exception thrown from the workflow;
                        // we still have some context for the attributes.
                        attributeEditBean.translateAttributes(role.getAttributes());
                    }

                    // For some reason the profiles are given ids even if they don't actually have them yet.
                    // The path that leads here is extremely convoluted so the source of the problem is difficult
                    // to pinpoint.  Just work around the problem by nulling out bad IDs instead.  --Bernie
                    List<Profile> profiles = role.getProfiles();
                    if (profiles != null) {
                        for (Profile profile : profiles) {
                            String id = profile.getId();
                            if (id != null &&
                                    !existingProfileIds.contains(id) &&
                                    getContext().countObjects(Profile.class, new QueryOptions(Filter.eq("id", id))) == 0) {
                                profile.setId(null);
                            }
                        }
                    }

                    addMessageToSession(RoleUtil.launchWorkflow(role, pendingWorkflowCase, this));

                    if (isFromViewer()) {
                        outcome = "save";
                    } else {
                        String workItemId = (String) getSessionScope().get(ATT_WORK_ITEM_ID);
                        clearHttpSession();
                        getSessionScope().put(WorkItemBean.ATT_ITEM_ID, workItemId);
                        outcome = "backToWorkItem";
                    }
                }
            } else  {
                // if the role is null validation failed so abort
                return "";
            }
        } catch (Exception e) {
            String msg = "Unable to save object with id '" + getObjectId() + "': " + e;
            log.error(msg, e);
            addMessage(new Message(Message.Type.Error, MessageKeys.ERR_EXCEPTION, e), null);
            outcome = "";
        }

        if ("save".equals(outcome)) {
            // If this was saved, set the viewer state to show the saved role.
            // This is primarily done for the sake of new roles
            if (roleName != null) {
                try {
                    Bundle savedRole = getContext().getObjectByName(Bundle.class, roleName);
                    if (savedRole != null) {
                        String roleId = savedRole.getId();
                        // Bug 14974 - Don't commit the attributes here.  If no workflow is in place we're going to commit this above.
                        // localizer.commitAttributes(roleId);
                        // jsl - getLoggedInUser returns a cached object that may not be
                        // in the current session, just refresh it!
                        Identity user = getLoggedInUser();
                        user = (Identity)refresh(user);
                        String stateKey = user.getId() + ":" + RoleUtil.ROLE_VIEWER_STATE + ":" + SessionStateBean.SESSION_STATE;
                        Map session = getSessionScope();
                        ExtState stateObj = (ExtState) session.get(stateKey);

                        if(stateObj == null) {
                            stateObj = new ExtState();
                            stateObj.setName(RoleUtil.ROLE_VIEWER_STATE);
                        }

                        RoleUtil.updateViewerState(stateObj, user, "selectedTopDownNodeId", roleId);
                        RoleUtil.updateViewerState(stateObj, user, "selectedRoleId", roleId);
                        RoleUtil.updateViewerState(stateObj, user, "selectedBottomUpNodeId", roleId);

                        session.put(stateKey, stateObj);
                    }
                } catch (GeneralException e) {
                    log.error("Failed to update viewer state", e);
                }
            }

            clearHttpSession();
        }

        return outcome;
    }

    public boolean isFromViewer() {
        return (getWorkItemId() == null);
    }

    public String getWorkItemId() {
        return (String)getSessionScope().get(ATT_WORK_ITEM_ID);
    }

    public void setWorkItemId(String workItemId) {
        //Make JSF happy but dont do anything
    }

    /**
     * Get the modified Bundle ready to pass into a workflow.
     * Used for both saveAction and impactAnalysisAction.
     *
     * All the various editing beans and DTOs have to flush changes
     * into the role.  Since we have not been keeping the role attached
     * we also have to walk it and reresolve all references to remove
     * duplicates and make sure all referenced objects are in the
     * current Hibernate session.
     */
    private Bundle getRoleReadyForWorkflow()
        throws GeneralException {

        // Do not attach! This will assign a Hibernate ID
        // and the workflow may not decide to save it right away
        // then we'll have the stale id problem.  We do have to walk
        // references though and make sure we don't have duplicate
        // references to Applications in case workflow does decide
        // to commit within  the same Hibernate session.
        Bundle role = getObject();

        // First attach the profiles
        Collection<ProfileDTO> editedProfileDTOs = editedProfiles.values();
        for (ProfileDTO editedProfileDTO : editedProfileDTOs) {
            String profileId = editedProfileDTO.getUid();
            Profile editedProfile = getOrCreateProfileFromParent(profileId);
            editedProfileDTO.applyChanges(editedProfile);
        }

        // this returns "" to mean no error and null to mean
        // that errors were left in the context
        String outcome = validateRole(role);
        if (outcome == null) {
            // we'll return null to mean validation errors
            role = null;
        }
        else {
            boolean needToRecache = false;
            if ("new".equals(role.getId())) {
                role.setId(null);
            }
            // Save target account selector rules
            saveProvisioningTargetAccountSelectorRules(role);

            /** Save the Role provisioning policy forms**/
            saveForms();

            applyActivityMonitoring();
            attributeEditBean.applySpecialValues();
            Attributes<String, Object> updatedAttributes = new Attributes<String, Object>();
            if (attributeEditBean.getAttributes() != null) {
                //IIQETN-5923 :- Wiping out any dangerous javascript for extended attributes
                for (Map.Entry<String, Object> entry : attributeEditBean.getAttributes().entrySet()) {
                    Object obj = entry.getValue();
                    if (obj != null && obj instanceof String) {
                        entry.setValue(WebUtil.sanitizeHTML(obj.toString()));
                    }
                }

                /*
                 *  Filter out disallowed values here because the UI only hides them when they're
                 *  unavailable, so residual values may have been submitted if the type was switched
                 *  during editing
                 */
                updatedAttributes.putAll(filterDisallowedValues(attributeEditBean.getAttributes()));
            }
            role.setAttributes(updatedAttributes);

            //classifications
            Map<String, Classification> clsMap = new HashMap<>();
            for (ObjectClassification ocls : Util.safeIterable(role.getClassifications())) {
                clsMap.put(ocls.getClassification().getId(), ocls.getClassification());
            }

            //for add classifications
            for (String newId : Util.safeIterable(this.getClassificationIds())) {
                if (!clsMap.containsKey(newId)) {
                    Classification cls = getContext().getObjectById(Classification.class, newId);
                    if (cls != null) {
                        role.addClassification(cls, Source.UI.name(), false);
                    }
                }
            }

            //for remove classifications
            for (String existId : clsMap.keySet()) {
                if (this.getClassificationIds() == null || !this.getClassificationIds().contains(existId)) {
                    role.removeClassification(clsMap.get(existId));
                }
            }

            
            // jsl - Note that since this is stored in the attributes map
            // it must be set AFTER replacing it with updatedAttributes.  Not
            // crazy about this...
            role.setMergeTemplates(mergeTemplates);

            role.setAllowDuplicateAccounts(allowDuplicateAccounts);
            role.setAllowMultipleAssignments(allowMultipleAssignments);

            role.setActivationDate(null);
            role.setDeactivationDate(null);
            // set role events on the role
            if(events!=null && !events.isEmpty()) {
                for(EventBean event : events) {
                    Date dueDate = event.getDue();
                    String name = event.getName();
                    String creator = event.getCreator();

                    if(name.equals(EventBean.ACTIVATION)) {
                        role.setActivationDate(dueDate);
                        role.setAttribute(ACTIVATION_CREATOR, creator);
                    } else if(name.equals(EventBean.DEACTIVATION)) {
                        role.setDeactivationDate(dueDate);
                        role.setAttribute(DEACTIVATION_CREATOR, creator);
                    }
                }
            }

            // ask each of the reference list DTOs to commit if it's
            // appropriate to do so
            if (rolePermits != null)
                needToRecache |= rolePermits.commit(role);

            if ( roleRequirements != null)
                needToRecache |= roleRequirements.commit(role);

            if (roleMembership != null)
                needToRecache |= roleMembership.commit(role);

            if (needToRecache) {
                getContext().decache();
                role = (Bundle) ObjectUtil.recache(getContext(), role, true);
            }

            // At this point we can traverse the role references and check for any with capabilities
            // or scopes that are not shared with the logged in user, to prevent elevation of privileges
            List<Message> errors = new ArrayList<>();

            if (!isCircularReferencesDetected(role)) {
                errors = validateUserRights(role);
            } else {
                errors.add(Message.error(MessageKeys.ROLE_CIRCULAR_REFERENCE_FOUND));
            }

            if (!Util.isEmpty(errors)) {
                for (Message error : errors) {
                    addMessage(error);
                }

                // Null role means validation errors
                role = null;
            }

            if (role != null) {

                if (editedSelector != null && !editedSelector.isPlaceholder()) {
                    // validateRole should have caught any errors
                    IdentitySelector sel = editedSelector.convert();
                    if (sel != null && !sel.isEmpty())
                        role.setSelector(sel);
                    else
                        role.setSelector(null);
                } else {
                    role.setSelector(null);
                }

                // jsl - since we've not been attaching the Bundle as
                // we edit there can be multiple references to the
                // same Application in the Bundle model.  If there
                // is more than one profile for the same application, or
                // if any profile application is also used in the IdentitySelctor,
                // we'll get the "a different object with the same identitifier"
                // Hibernate error.  To avoid this we have to refresh
                // the profile applications with fresh ones from the current
                // Hibernate session.  This could be avoided if we used
                // a pure DTO approach for profile editing, but I guess
                // that's hard for the filter builder.
                List<Profile> profiles = role.getProfiles();
                if (profiles != null) {
                    for (Profile p : profiles) {
                        Application freshApp = (Application) refresh(p.getApplication());
                        if (freshApp != null)
                            p.setApplication(freshApp);
                        else {
                            // someone must have deleted it out from under us
                            // should we tolerate this?
                            // Use role name for exception since application is null and profile may not have name or id yet.
                            throw new GeneralException("Invalid application in profile for role: " + role.getName());
                        }

                        // The demodata still has lots of profiles with owners,
                        // need to prune these as we edit.  These are typically
                        // unattached unloaded proxies so we can't refresh them
                        // anyway.
                        p.setOwner(null);
                    }
                }

                // refresh owner
                Identity owner = role.getOwner();
                if (owner != null)
                    role.setOwner((Identity) refresh(owner));

                /*
                 * Keep the existing localized descriptions and store the changes on the object that we're passing to the workflow
                 */
                if (descriptionData == null) {
                    role.setDescriptions(null);
                } else {
                    Map<String, String> descriptions = descriptionData.getDescriptionMap();
                    if (Util.isEmpty(descriptions)) {
                        role.setDescriptions(null);
                    } else {
                        role.setDescriptions(descriptions);
                    }
                }
                updateProvisioningPlan(role);

                // NOTE WELL: It is important that we reload the object now
                // that we have replaced references to things like inherited roles
                // with fresh partially loaded copies from the database.  If you
                // don't do this here, then the load() call in our getObject()
                // method will get a lazy evaluation as it traverses through the
                // role relationships.  We probably don't need getObject() to call
                // load() but it's safer if we can assume that the object will
                // always be fully loaded.
                role.load();
            }
        }

        return role;

    }

    public String impactAnalysisAction() {
        String outcome = null;
        Bundle role = null;
        try {
            role = getRoleReadyForWorkflow();

            if (role != null) {
            	// We used to copy localized descriptions to the role here but now this happens in the "getRoleReadyForWorkflow" method

                RoleLifecycler rlc = new RoleLifecycler(getContext());

                WorkflowCase existingCase = pendingWorkflowCase;

                if (existingCase == null) {
                    rlc.impactAnalysis(role, getLoggedInUser());
                    outcome = "save";
                } else {
                    Identity caseOwner = existingCase.getOwner();
                    String caseOwnerName;
                    if (caseOwner == null) {
                        caseOwnerName = existingCase.getLauncher();
                    } else {
                        caseOwnerName = caseOwner.getName();
                    }

                    Identity currentUser = getLoggedInUser();
                    final boolean replaceWorkflow =
                        (caseOwnerName.equals(currentUser.getName())) || getLoggedInUser().getCapabilityManager().hasCapability(Capability.SYSTEM_ADMINISTRATOR);
                    if (replaceWorkflow) {
                        rlc.cancelApproval(existingCase);
                        rlc.impactAnalysis(getObject(), getLoggedInUser());
                        outcome = "save";
                    } else {
                        addMessageToSession(new Message(Message.Type.Error, MessageKeys.WORKFLOW_ROLE_WORKITEM_PENDING, new Object [] {getObject().getName(), caseOwnerName}));
                        outcome = "";
                    }
                }
            }
        } catch (GeneralException e) {
            String msg = "Unable to launch work item with id '" + getObjectId() + "': " + e;
            log.error(msg, e);
            addMessage(new Message(Message.Type.Error, MessageKeys.ERR_EXCEPTION, e), null);
            outcome = "";
        }

        String workItemId = null;

        if ("save".equals(outcome) && role != null) {
            if (!isFromViewer()) {
                workItemId = (String) getSessionScope().get(ATT_WORK_ITEM_ID);
                outcome = "backToWorkItem";
            }
            clearHttpSession();
            if (outcome.equals("backToWorkItem")) {
                getSessionScope().put(WorkItemBean.ATT_ITEM_ID, workItemId);
            }
            addMessageToSession(new Message(Message.Type.Info, MessageKeys.ROLE_IMPACT_ANALYSIS_LAUNCHED, new Object [] {role.getName()}));
        }

        return outcome;
    }

    public String quickImpactAnalysisAction() {
        try {
            Bundle role = getRoleReadyForWorkflow();
            if (role != null) {
                RoleLifecycler rlc = new RoleLifecycler(getContext());
                role = (Bundle)role.deepCopy((Resolver)getContext());
                role.setId(null);
                analysisResults = rlc.quickImpactAnalysis(role);
                addEditState(QUICK_IMPACT_ANALYSIS, analysisResults);
            }
        } catch (GeneralException e) {
            addMessage(new Message(Message.Type.Error, MessageKeys.NO_IMPACT_ANALYSIS_RESULT_FOUND));
            log.error(new Message(Message.Type.Error, MessageKeys.ERR_EXCEPTION, e), e);
        }

        return "";
    }

    public String cancelPendingWorkflow() {
        boolean isFromViewer = isFromViewer();
        String workItemId = (String) getSessionScope().get(ATT_WORK_ITEM_ID);

        try {
            // Clear the crud out of the session so we don't commit any LocalizedAttribute changes by mistake
            getContext().decache();
            pendingWorkflowCase = (WorkflowCase)ObjectUtil.reattach(getContext(), pendingWorkflowCase);
            Identity caseOwner = pendingWorkflowCase.getOwner();
            String caseOwnerName;

            if (caseOwner == null) {
                caseOwnerName = pendingWorkflowCase.getLauncher();
            } else {
                caseOwnerName = caseOwner.getName();
            }

            if ( caseOwnerName.equals(getLoggedInUser().getName()) || getLoggedInUser().getCapabilityManager().hasCapability(Capability.SYSTEM_ADMINISTRATOR)) {
                RoleLifecycler rlc = new RoleLifecycler(getContext());
                rlc.cancelApproval(pendingWorkflowCase);
                addMessageToSession(new Message(Message.Type.Info, MessageKeys.APPROVAL_CANCELED));
                clearHttpSession();
            } else {
                Message msg = new Message(Message.Type.Error, MessageKeys.WORKFLOW_ROLE_CANNOT_CANCEL, caseOwnerName);
                addMessageToSession(msg, null);
                return "";
            }
        } catch (GeneralException e) {
            Message msg = new Message(Message.Type.Error, MessageKeys.ERR_EXCEPTION, e);
            addMessage(msg, null);
            log.error(msg, e);
        }

        String result;

        if (isFromViewer) {
            result = "cancel";
        } else {
            getSessionScope().put(WorkItemBean.ATT_ITEM_ID, workItemId);
            result = "backToWorkItem";
        }

        return result;
    }

    public void editProfile() {
        ProfileDTO profileToEdit = null;

        try {
            Bundle editedRole = getObject();

            // The reasoning behind a ProfileDTO requires some explanation.  Hibernate,
            // to put it briefly, does not handle IDs very nicely for new objects.
            // Therefore, we have to leave the object ids null until we're ready
            // to commit.  This works out fine until you're trying to add multiple
            // instances of new objects of the same type.  With null ids, you have
            // no way to track them (names don't do well as IDs in HTML because any
            // special characters in them will cause trouble).  The DTO has its own
            // UID that we can use to correlate the profiles until they are actually
            // ready to be saved and committed.
            if (editedProfileId == null || editedProfileId.trim().length() == 0) {
                // This is a new profile
                profileToEdit = new ProfileDTO(new Profile());
                editedProfileId = profileToEdit.getUid();
            } else {
                // This is an existing profile
                ProfileDTO profileFromSession = editedProfiles.get(editedProfileId);
                if (profileFromSession == null) {
                    // This is an existing profile that has not been edited yet in this session
                    List<Profile> profiles = editedRole.getProfiles();
                    for (Profile profile : profiles) {
                        if (editedProfileId.equals(profile.getId())) {
                            profileToEdit = new ProfileDTO(profile);
                        }
                    }
                } else {
                    profileToEdit = profileFromSession.copy();
                    profileToEdit.setUid(profileFromSession.getUid());
                }
            }

            if (profileToEdit != null) {
                profileEditBean = new ProfileEditBean(editedRole, profileToEdit);
                addEditState(PROFILE_EDIT, profileEditBean);
                addEditState(ROLE_EDIT_MODE, EditMode.Profile);
            } else {
            }
        } catch (GeneralException e) {
            log.error("No profile can be edited at this time because there is no role to attach it to.", e);
        }

    }

    public String saveProfile() {
        String result;

        try {
            if (profileEditBean != null && profileEditBean.prepareToCommit()) {

                // If there is no filter and this is a simple entitlement, create one. If filter creation fails,
                // return null so that the profile is not created.
                if (editMode == EditMode.SimpleEntitlement) {
                    ProfileConstraints constraints = (ProfileConstraints)profileEditBean.getProfileConstraints();
                    if (constraints != null && constraints.getFilters().isEmpty()) {
                        String filterResult = constraints.newFilter(true);
                        if (filterResult == null) {
                            return null;
                        }
                    }
                }
                
                //Duplicate profile detection and prevention
                boolean foundDuplicate = false;

                //fetching profiles from session and profiles already committed to DB
                List<ProfileDTO> allProfiles = getCurrentProfiles();

                //fetching the current profile
                ProfileDTO neu = profileEditBean.getProfile();

                //Let's go and compare the expressions
                RoleOverlapper overlapper = new RoleOverlapper(getContext(), new Attributes<String, Object>());
                for (ProfileDTO p : allProfiles) {
                    Expression oldExpression = overlapper.getProfileExpression(p.getProfile());
                    Expression newExpression = overlapper.getProfileExpression(neu.getProfile());
                    if (oldExpression != null && newExpression != null) {
                        //IIQETN-6119 :- if current and old profile are the same, it means that we need to update the profile.
                        if (oldExpression.isMatch(newExpression) && !Util.nullSafeEq(p.getId(), neu.getId(), true, true)) {
                            foundDuplicate = true;
                            break;
                        }
                    }
                }

                if (!foundDuplicate) {
                    String editedProfileId = profileEditBean.getProfile().getId();
                    editedProfiles.put(editedProfileId, profileEditBean.getProfile());
                    addEditState(ROLE_EDIT_MODE, EditMode.Plain);
                    result = "saveProfile";
                } else {
                    result = null;
                }
            } else {
                result = null;
            }
        } catch (GeneralException e) {
            log.error("The profile could not be saved because no role is in the session.", e);
            result = null;
        }

        return result;
    }


    public String deleteProfile() {
        try {
            Bundle parent = getObject();
            initProfileEditBean(parent);
            if (profileEditBean != null) {
                Profile profileToRemove = profileEditBean.getObject().getProfile();
                parent.remove(profileToRemove);
                editedProfiles.remove(profileEditBean.getObject().getUid());
            }
        } catch (GeneralException e) {
            log.error("No role object was found to delete the profile from.", e);
        }

        return "";
    }

    public void deleteSimpleEntitlement() {
        try {
            Bundle bundle = getObject();

            Map data = (Map) JsonUtil.parse(this.simpleEntitlementToDelete);

            String appName = (String) data.get("applicationName");
            String property = (String) data.get("property");
            String value = (String) data.get("value");

            //System.out.println("entitlment to delete = " + data);

            List<ProfileDTO> currentProfiles = getCurrentProfiles();
            Set<String> profilesToDelete = new HashSet<String>();

            Iterator<ProfileDTO> profileIt = currentProfiles.iterator();
            while (profileIt.hasNext()) {
                ProfileDTO profile = profileIt.next();
                if (!appName.equals(profile.getApplication().getName())) {
                    continue;
                }

                List<Filter> filtersToAdd = new ArrayList<Filter>();

                Iterator<Filter> filterIt = profile.getConstraints().iterator();
                while (filterIt.hasNext()) {
                    Filter filter = filterIt.next();
                    if (filter instanceof Filter.LeafFilter) {
                        boolean removeFilter = removeValueFromFilter((Filter.LeafFilter) filter, property, value);
                        if (removeFilter) {
                            filterIt.remove();
                        }
                    } else if (filter instanceof Filter.CompositeFilter) {
                        Iterator<Filter> childFilterIt = ((Filter.CompositeFilter)filter).getChildren().iterator();
                        while (childFilterIt.hasNext()) {
                            Filter childFilter = childFilterIt.next();
                            boolean removeChildFilter = removeValueFromFilter((Filter.LeafFilter) childFilter, property, value);
                            if (removeChildFilter) {
                                childFilterIt.remove();
                            }
                        }
                        Collection childFilters = ((Filter.CompositeFilter)filter).getChildren();
                        //check if the composite filter is empty?
                        if (Util.isEmpty(childFilters)) {
                            filterIt.remove();
                        } else if (childFilters.size() == 1) {
                            //delete the composite filter and add the leaf filter
                            filtersToAdd.addAll(childFilters);
                            filterIt.remove();
                        }
                    }
                }

                //add back filters from composite filters
                if (!filtersToAdd.isEmpty()) {
                    for (Filter filter : filtersToAdd) {
                        profile.addConstraint(filter);
                    }
                }

                //remove the profile if it has no filters
                if (profile.getConstraints().isEmpty()) {
                    profilesToDelete.add(profile.getId());
                }
            }

            //remove from bundle
            List<Profile> profiles = bundle.getProfiles();
            if(profiles != null) {
                Iterator<Profile> exProfileIt = profiles.iterator();
                while (exProfileIt.hasNext()) {
                    Profile profile = exProfileIt.next();
                    if (profilesToDelete.contains(profile.getId())) {
                        exProfileIt.remove();
                    }
                }
            }
            //remove from editd profiles
            for (String key : profilesToDelete) {
                editedProfiles.remove(key);
            }

        } catch (Exception e) {
            log.error("Error when deleting simple entitlement: " + simpleEntitlementToDelete, e);
        }
    }

    /**
     * Returns true if the filter is empty and should be removed from the profile.
     * @param filter
     * @param property
     * @param value
     * @return
     */
    public boolean removeValueFromFilter(Filter.LeafFilter filter, String property, String value) {
        String exProperty = filter.getProperty();

        if (property.equals(exProperty)) {
            Object exValue = filter.getValue();
            if (filter.getOperation() == Filter.LogicalOperation.EQ) {
                if (exValue.equals(value)) {
                    filter.setValue(null);
                    return true;
                }
            } else if (filter.getOperation() == Filter.LogicalOperation.CONTAINS_ALL) {
                Collection colValue = (Collection) exValue;
                colValue.remove(value);
                if (colValue.isEmpty()) {
                    return true;
                }
            }
        }

        return false;
    }

    public String cancelProfileEditAction() {
        removeEditState(PROFILE_EDIT);
        addEditState(ROLE_EDIT_MODE, EditMode.Plain);
        editedProfileId = null;
        return "";
    }

    public String beginEntitlementMining() {
        addEditState(ROLE_EDIT_MODE, EditMode.EntitlementMining);
        // Reset the search
        entitlementMiningProfileEditBean.clearHttpSession();
        return "";
    }

    public String cancelEntitlementMining() {
        addEditState(ROLE_EDIT_MODE, EditMode.Plain);
        // Reset the search
        entitlementMiningProfileEditBean.clearHttpSession();
        return "";
    }

    public String saveMinedProfile() {
        String result = entitlementMiningProfileEditBean.saveAction();
        if ("successfullyCommittedChanges".equals(result)) {
            addEditState(ROLE_EDIT_MODE, EditMode.Plain);
            entitlementMiningProfileEditBean.clearHttpSession();
        }

        return result;
    }

    @Override
    protected String getRequestOrSessionParameter(String name) {
        String param = getRequestParameter(name);
        if (null == param)
        {
            param = (String) getSessionScope().get(name);
        }
        return Util.getString(param);
    }

    private void initProfileEditBean(Bundle editedRole) {
        if (editedProfileId == null) {
            editedProfileId = (String) getRequestParam().get("editForm:editedProfileId");
        }

        ProfileDTO editedProfile = editedProfiles.get(editedProfileId);
        if (editedProfile == null) {
            // If it's newly edited look for it in the role
            for (Profile potentialEdit : editedRole.getProfiles()) {
                if (potentialEdit.getId().equals(editedProfileId)) {
                    editedProfile = new ProfileDTO(potentialEdit);
                    break;
                }
            }
        }

        profileEditBean = new ProfileEditBean(editedRole, editedProfile);
    }

    /*
     * This method returns "" if the role is valid and null otherwise
     */
    private String validateRole(Bundle role) throws GeneralException {
        String outcome = "";

        @SuppressWarnings("unchecked")
        List<Message> errors = RoleUtil.validateBasicRole(role, (Attributes<String, Object>)this.attributeEditBean.getAttributes(), (List<ObjectAttribute>)this.attributeEditBean.getAttributeDefinitions(), this);
        if (!errors.isEmpty()) {
            for (Message error : errors)
                addMessageToSession(error);
            outcome = null;
        }
        RoleConfig rc = new RoleConfig();
        RoleTypeDefinition roleTypeDef = rc.getRoleTypeDefinition(role.getType());

        if (editedSelector != null && roleTypeDef != null && !roleTypeDef.isNoAssignmentSelector() && rc.getAssignableRoleTypes().contains(role.getType())) {
            try {
                editedSelector.validate();
            } catch (GeneralException e) {
                addMessage(e);
                outcome = null;
            }
        }

        UserRightsEditValidator validator = new UserRightsEditValidator(this, this);
        errors = validator.validate();
        if (!Util.isEmpty(errors)) {
            for (Message error : errors) {
                addMessage(error);
            }

            outcome = null;
        }

        return outcome;
    }

    /**
     * Checks the role for any user rights validation issues. Basically this is to ensure that the logged in user
     * cannot make changes to the current role in case any referenced role might grant capabilities or controlled scopes
     * that she does not already have (see IIQSAW-1848)
     *
     * Note this pulls flattened lists of roles into the cache, and assumes that the permits, requirements and inheritance is loaded
     * already.
     *
     * @param role Current loaded role
     * @return List of messages for any error, or empty list for no error
     * @throws GeneralException
     */
    private List<Message> validateUserRights(Bundle role) throws GeneralException {
        List<Message> errors = new ArrayList<>();
        // Check the current role based on current edits.
        UserRightsEditValidator validator = new UserRightsEditValidator(this, this);
        if (validator.hasUnmatchedPrivileges()) {
            errors.add(Message.error(MessageKeys.ERR_USER_RIGHTS_CURRENT_ROLE));
        }

        List<String> badRoleNames = new ArrayList<>();
        validateUserRights(role, badRoleNames);

        if (!Util.isEmpty(badRoleNames)) {
            errors.add(Message.error(MessageKeys.ERR_USER_RIGHTS_ROLE_REFERENCE, Util.listToCsv(badRoleNames)));
        }

        return errors;
    }

    /**
     * Provides the information about if a role has circular references in its relationships.
     *
     * @param role The root Role that will be analyzed.
     * @return A boolean values that indicates if a circular reference exists.
     */
    private boolean isCircularReferencesDetected(Bundle role) {
        return detectCircularReference(role, new HashSet<Bundle>());
    }

    /**
     * Recursive method that finds circular references between roles
     *
     * @param role The root Role that will be analyzed.
     * @param reviewedRoles The set to store the information about already reviewed roles.
     * @return A boolean values that indicates if a circular reference exists.
     */
    private boolean detectCircularReference(Bundle role, Set<Bundle> reviewedRoles) {
        boolean circularReferenceDetected = false;
        if (reviewedRoles.contains(role)) {
            circularReferenceDetected = true;
        } else {
            reviewedRoles.add(role);
            for (Bundle linkedRole : Util.iterate(getRelatedRoles(role))) {
                // Recurse
                if (circularReferenceDetected = detectCircularReference(linkedRole, new HashSet<>(reviewedRoles))) {
                    break;
                }
            }
        }
        return circularReferenceDetected;
    }

    private void validateUserRights(Bundle roleToCheck, List<String> roleNames)
            throws GeneralException {
        for (Bundle linkedRole : Util.iterate(getRelatedRoles(roleToCheck))) {
            RoleUserRightEditContext roleUserRightEditContext = new RoleUserRightEditContext(linkedRole);
            UserRightsEditValidator validator = new UserRightsEditValidator(this, roleUserRightEditContext);
            if (validator.hasUnmatchedPrivileges()) {
                roleNames.add(linkedRole.getDisplayableName());
            }

            // Recurse
            validateUserRights(linkedRole, roleNames);
        }
    }

    /**
     * Consolidates in a single list the related roles to check
     *
     * @param roleToCheck
     *            Role to validate
     * @return A collection of unique bundles
     */
    private Set<Bundle> getRelatedRoles(Bundle roleToCheck) {
        Set<Bundle> rolesToCheck = new HashSet<>();
        if(!Util.isEmpty(roleToCheck.getInheritance())) {
            rolesToCheck.addAll(roleToCheck.getInheritance());
        }
        if(!Util.isEmpty(roleToCheck.getPermits())) {
            rolesToCheck.addAll(roleToCheck.getPermits());
        }
        if(!Util.isEmpty(roleToCheck.getRequirements())) {
            rolesToCheck.addAll(roleToCheck.getRequirements());
        }
        return rolesToCheck;
    }

    private Profile getOrCreateProfileFromParent(String profileId) throws GeneralException {
        Bundle parent = getObject();
        Profile profileToReturn = null;

        List<Profile> profiles = parent.getProfiles();
        if (profiles == null)
            profiles = new ArrayList<Profile>();

        for (Profile profile : profiles) {
            if (profile.getId() != null && profile.getId().equals(profileId)) {
                profileToReturn = profile;
            }
        }

        if (profileToReturn == null) {
            profileToReturn = new Profile();
            profiles.add(profileToReturn);
        }

        parent.assignProfiles(profiles);

        return profileToReturn;
    }

    /**
     * Configure activity monitoring for a business role.
     * Today we expose a simple boolean flag to indicate
     * if we should enable monitoring.  The configuration
     * model supports a more granular application level
     * enablement that we'll proably want to move toward.
     * The UI will get a bit more busy if we do this..
     *
     * To think about:
     *   What if a role doesn't have any activity enabled apps?
     *   What if a role doesn't have any profiles at all?
     *   When we go granular, If we add a new profile to a role and
     *        the app in the profile supports
     *        activity should we automatically enable it too?
     *
     */
    private void applyActivityMonitoring() throws GeneralException {
        Bundle role = getObject();

        ActivityConfig activityConfig = role.getActivityConfig();

        if (activityEnabled) {
            if (activityConfig == null) {
                activityConfig = new ActivityConfig();
                role.setActivityConfig(activityConfig);
            }

            activityConfig.clearApplications();

            // For now don't worry about the application
            // level enablement just use a global flag
            // that will enable all applications.
            activityConfig.setAllEnabled(true);
        } else {
            if (activityConfig != null) {
                activityConfig.clearApplications();
                activityConfig.setAllEnabled(false);
            }
            role.setActivityConfig(null);
        }
    }

    // TODO: Need to account for capabilities (not sure what those will look like yet)

    //////////////////////////////////////////////////////////////////////
    //
    // selector.xhtml action listeners
    //
    //////////////////////////////////////////////////////////////////////

    public void addSelectorAttribute(ActionEvent e) {
        // javax.faces.component.UIComponent uic = e.getComponent();
        // String id = uic.getId();
        try {
            if (editedSelector != null)
                editedSelector.addMatchTerm(false, IdentitySelector.MatchTerm.Type.Entitlement.name());
        }
        catch (Throwable t) {
            addMessage(t);
        }
    }

    public void addSelectorIdentityAttribute(ActionEvent e) {
        // javax.faces.component.UIComponent uic = e.getComponent();
        // String id = uic.getId();
        try {
            if (editedSelector != null) {
                editedSelector.setApplication(IdentitySelectorDTO.IIQ_APPLICATION_ID);
                editedSelector.addMatchTerm(false, IdentitySelector.MatchTerm.Type.IdentityAttribute.name());
            }
        }
        catch (Throwable t) {
            addMessage(t);
        }
    }

    public void addSelectorPermission(ActionEvent e) {
        try {
            if (editedSelector != null)
                editedSelector.addMatchTerm(false, IdentitySelector.MatchTerm.Type.Permission.name());
        }
        catch (Throwable t) {
            addMessage(t);
        }
    }
    
    public void addSelectorRoleAttribute(ActionEvent e) {
        try {
            if (editedSelector != null) {
                editedSelector.setApplication(IdentitySelectorDTO.IIQ_APPLICATION_ID);
                editedSelector.addMatchTerm(false, IdentitySelector.MatchTerm.Type.RoleAttribute.name());
            }
        }
        catch (Throwable t) {
            addMessage(t);
        }
    }

    public void addSelectorEntitlementAttribute(ActionEvent e) {
        try {
            if (editedSelector != null) {
                editedSelector.setApplication(IdentitySelectorDTO.IIQ_APPLICATION_ID);
                editedSelector.addMatchTerm(false, IdentitySelector.MatchTerm.Type.EntitlementAttribute.name());
            }
        }
        catch (Throwable t) {
            addMessage(t);
        }
    }



    public void deleteSelectorTerms(ActionEvent e) {
        try {
            if (editedSelector != null)
                editedSelector.deleteSelectedTerms();
        }
        catch (Throwable t) {
            addMessage(t);
        }
    }

    public void groupSelectedTerms(ActionEvent e) {
        try {
            if (editedSelector != null)
                editedSelector.groupSelectedTerms();
        }
        catch (Throwable t) {
            addMessage(t);
        }
    }

    public void ungroupSelectedTerms(ActionEvent e) {
        try {
            if (editedSelector != null)
                editedSelector.ungroupSelectedTerms();
        }
        catch (Throwable t) {
            addMessage(t);
        }
    }

    private void resetAttributeEditBean(Bundle editedRole) {
        ObjectConfig config = ObjectConfig.getObjectConfig(Bundle.class);
        if (editedRole.getAttributes() == null || editedRole.getAttributes().isEmpty()) {
            if (isNewRole()) {
                setDefaultAttributeValuesForNewRole(editedRole, config);
            } else {
                editedRole.setAttributes(new Attributes<String, Object>());
            }
        }
        attributeEditBean = new RoleAttributeEditBean(this, config, null, editedRole.getAttributes());
        addEditState(ROLE_ATTRIBUTE_EDIT, attributeEditBean);
    }

    /*
     * A little note about how this is being called:  We're incorrectly setting defaults on
     * the role every time we change types so it may seem like we're incorrectly overriding
     * set values, but the attribute editor comes along behind us and overwrites
     * the defaults as needed so we're still OK.
     */
    private void setDefaultAttributeValuesForNewRole(Bundle role, ObjectConfig config) {

        String roleType = role.getType();
        if (Util.isNullOrEmpty(roleType)) {
            roleType = getDefaultRoleType();
        }

        Attributes<String, Object> defaultValues = config.getDefaultValues();
        if (!Util.isNullOrEmpty(roleType) && defaultValues != null) {
            RoleTypeDefinition typeDefinition = config.getRoleType(roleType);
            if (!Util.isEmpty(typeDefinition.getDisallowedAttributes())) {
                for (String disallowedAttributeName : typeDefinition.getDisallowedAttributes()) {
                    if (defaultValues.containsKey(disallowedAttributeName)) {
                        defaultValues.remove(disallowedAttributeName);
                    }
                }
            }
        }
        role.setAttributes(defaultValues);
        role.setType(roleType);
    }

    public String addEvent() {
        EventBean event = new EventBean();
        try {
            event.setCreator(getLoggedInUserName());
        }catch (GeneralException ge) {
            log.warn("Unable to set creator name on new " + eventAction + " event.  Exception: " + ge.getMessage());
        }
        event.setDue(eventDate);
        event.setName(eventAction);

        events.add(event);
        Collections.sort(events,
                new EventBean.EventBeanComparator());
        addEditState(ROLE_EVENTS, events);

        return "";
    }

    public String removeEvent() {
        Map<String,Boolean> selections = getEventSelections();
        if (selections != null && getEvents()!=null) {
            for (String key : selections.keySet()) {
                if (selections.get(key)) {

                    for(Iterator<EventBean> eventIter = getEvents().iterator(); eventIter.hasNext();) {
                        EventBean event = eventIter.next();
                        if(event.getId().equals(key)) {
                            eventIter.remove();
                        }
                    }

                }
            }
            Collections.sort(events,
                    new EventBean.EventBeanComparator());
        }
        return "";
    }

    public List<EventBean> getEvents() {
        return events;
    }

    public void setEvents(List<EventBean> events) {
        this.events = events;
    }

    public Date getEventDate() {
        return eventDate;
    }

    public void setEventDate(Date eventDate) {
        this.eventDate = eventDate;
    }

    public String getEventAction() {
        return eventAction;
    }

    public void setEventAction(String eventAction) {
        this.eventAction = eventAction;
    }

    private void buildEvents() {
        events = new ArrayList<EventBean>();

        try {
            Bundle role = getObject();
            if(role!=null) {
                if(role.getActivationDate()!=null && role.getActivationDate().after(new Date())) {
                    EventBean event = new EventBean();
                    event.setCreator((String)role.getAttribute(ACTIVATION_CREATOR));
                    event.setName(EventBean.ACTIVATION);
                    event.setDue(role.getActivationDate());
                    events.add(event);
                }

                if(role.getDeactivationDate()!=null && role.getDeactivationDate().after(new Date())) {
                    EventBean event = new EventBean();
                    event.setCreator((String)role.getAttribute(DEACTIVATION_CREATOR));
                    event.setName(EventBean.DEACTIVATION);
                    event.setDue(role.getDeactivationDate());
                    events.add(event);
                }
            }
        } catch(GeneralException ge) {
            log.warn("Unable to build events list: " + ge.getMessage());
        }
        Collections.sort(events,
                new EventBean.EventBeanComparator());
        addEditState(ROLE_EVENTS, events);
    }

    public Map<String, Boolean> getEventSelections() {
        if (eventSelections == null)
            eventSelections = new HashMap<String,Boolean>();
        return eventSelections;
    }

    public void setEventSelections(Map<String, Boolean> eventSelections) {
        this.eventSelections = eventSelections;
    }

    private void saveForms() {
        try {
            getObject().setProvisioningForms(formEditBean.commitForms());
        } catch (GeneralException ge) {
            log.warn("Unable to save form edit bean: " + ge.getMessage());
        }
    }

    private void buildFormEditBean() {
        try {
            formEditBean = new FormEditBean(getObject().getProvisioningForms());
        } catch (GeneralException ge) {
            log.warn("Unable to build form edit bean: " + ge.getMessage());
        }
        addEditState(ROLE_FORM_EDIT, formEditBean);
    }

    ///////////////////////////////////////////////////////////////////////////
    //
    // Capabilities and Scope assignment to roles that will be applied
    // when provisioning is executed
    //
    ///////////////////////////////////////////////////////////////////////////

    public SelectItem[] getAllCapabilities() throws GeneralException {

        QueryOptions qo = new QueryOptions();
        qo.setScopeResults(true);
        Iterator<Capability> caps = getContext().search(Capability.class, qo);
        List<SelectItem> items = new ArrayList<SelectItem>();
        if (caps != null) {
            while (caps.hasNext()) {
                Capability cap = caps.next();
                items.add(new SelectItem(cap.getName(), super.getMessage(cap.getDisplayableName())));
            }
        }

        // Sort in memory since the labels are i18n'ized.
        Collections.sort(items, new SelectItemByLabelComparator(getLocale()));

        return items.toArray(new SelectItem[items.size()]);
    }

    public List<String> getCapabilities() throws GeneralException {
        if ( ( capNames == null )  && ( getObject() != null) ) {
            Map<String,List<String>> values = RoleUtil.getValuesFromProvisioningPlan(getObject());
            if ( values != null ) {
               capNames = values.get(Certification.IIQ_ATTR_CAPABILITIES);
            }
        }
        return capNames;
    }

    /**
     * Assign capabilities expressed as an array of strings
     * to the Identity after converting to a List of Capability.
     */
    public void setCapabilities(List<String> capnames) throws GeneralException {
        capNames = capnames;
    }

    public List<Capability> buildCapabilities() throws GeneralException {
        List<Capability> capabilities = new ArrayList<Capability>();
        if ( capNames != null) {
            for (String capname : capNames) {
                Capability cap = getContext().getObjectByName(Capability.class, capname);
                if (cap != null)
                    capabilities.add(cap);
            }
        }
        return capabilities;
    }

    public List<Scope> getControlledScopes() throws GeneralException {
        if ( ( controlledScopes == null )  && ( getObject() != null) ) {
            controlledScopes = RoleUtil.getControlledScopesFromProvisioningPlan(getObject());
        }
        return controlledScopes;
    }

    public void setControlledScopes(List<Scope> controlledScopes)
        throws GeneralException {

        this.controlledScopes = controlledScopes;
    }

    public FormEditBean getFormEditor() {
        return formEditBean;
    }

    public void setFormEditor(FormEditBean formEditBean) {
        this.formEditBean = formEditBean;
    }

    /**
     * Using fields on the RoleEditor update any provisioning plans
     * that are updating capabilties or scopes.
     */
    private void updateProvisioningPlan(Bundle role) throws GeneralException {

        ProvisioningPlan plan = role.getProvisioningPlan();
        if ( ( plan == null ) && ( Util.size(capNames) == 0 ) && (Util.size(controlledScopes) == 0))
            // nothing todo return
            return;

        if ( plan == null )
            plan = new ProvisioningPlan();

        AccountRequest iiq = plan.getIIQAccountRequest();
        if ( iiq == null ) {
            iiq = createAccountRequest(ProvisioningPlan.APP_IIQ);
        } else {
            // remove it we will recreate it
            plan.remove(iiq);
        }

        List<AttributeRequest> attributeRequests = iiq.getAttributeRequests();
        if ( attributeRequests == null )
            attributeRequests = new ArrayList<AttributeRequest>();

        // Since we are going to be over-riding any existing values
        // go through and remove any existing Add request for either
        // Capabilties or Scopes.
        Iterator<AttributeRequest> it = attributeRequests.iterator();
        while ( it.hasNext() ) {
            AttributeRequest attrRequest = it.next();
            if ( attrRequest != null ) {
                String name = attrRequest.getName();
                if ( attrRequest.getOperation().equals(Operation.Add) ) {
                    if ( ( name.equals(Certification.IIQ_ATTR_CAPABILITIES) ) ||
                         ( name.equals(Certification.IIQ_ATTR_SCOPES) ) ) {
                        it.remove();
                    }
                }
            }
        }

        if ( Util.size(capNames) > 0 ) {
            iiq.add(new AttributeRequest(Certification.IIQ_ATTR_CAPABILITIES, Operation.Add, capNames));
        }

        //TODO: Would be nice to use path instead of ID -rap
        if ( Util.size(controlledScopes) > 0 ) {
            List<String> nameList = new ArrayList<String>();
            for ( Scope scope : controlledScopes ) {
                // we have to store the id here, because name is not unique
                nameList.add(scope.getId());
            }
            if ( Util.size(nameList) > 0 )
                iiq.add(new AttributeRequest(Certification.IIQ_ATTR_SCOPES, Operation.Add, nameList));
        }
        plan.add(iiq);

        // Since we've added a plan, Profiles are going to be ignored.  We need to incorporate any profiles
        // on the role into our newly generated plan
        mergeProfilesIntoPlan(role, plan);

        role.setProvisioningPlan(plan);
    }

    private AccountRequest createAccountRequest(String appName) {
        AccountRequest accountRequest = new AccountRequest();
        accountRequest.setApplication(appName);
        accountRequest.setTargetIntegration(appName);
        return accountRequest;
    }

    private void mergeProfilesIntoPlan(Bundle role, ProvisioningPlan plan) throws GeneralException {
        // Clear all non-IIQ AccountRequests from the plan because we are about to regenerate them
        List<AccountRequest> existingRequests = new ArrayList<AccountRequest>(plan.getAccountRequests());
        for (AccountRequest candidateForRemoval : Util.iterate(existingRequests)) {
            if (!candidateForRemoval.getApplicationName().equals(ProvisioningPlan.IIQ_APPLICATION_NAME)) {
                plan.remove(candidateForRemoval);
            }
        }

        List<ProfileDTO> profilesToMerge = getCurrentProfiles();
        if (Util.isEmpty(profilesToMerge)) {
            // No profiles to merge
            return;
        }

        Map<String, AccountRequest> accountRequestMap = new HashMap<String, AccountRequest>();
        ProfilePlanner planner = new ProfilePlanner();
        for (ProfileDTO profile : Util.iterate(profilesToMerge)) {
            Application app = profile.getApplication();
            String appName = app.getName();

            // Find or create the account request associated with this profile
            AccountRequest accountRequest = accountRequestMap.get(appName);
            if (accountRequest == null) {
                accountRequest = createAccountRequest(appName);
                accountRequestMap.put(appName, accountRequest);
            }

            // Add AttributeRequests corresponding to this Profile's constraints to the AccountRequest
            List<Filter> constraints = profile.getConstraints();
            for (Filter constraint : Util.iterate(constraints)) {
                planner.reset();
                constraint.accept(planner);
                accountRequest.addAll(planner.getRequests());
            }
        }

        // Add the account requests to the plan
        for (AccountRequest accountRequest : Util.iterate(accountRequestMap.values())) {
            plan.add(accountRequest);
        }
    }

    public String getDescriptionsJSON() {
        if (descriptionData == null) {
            descriptionData = new DescriptionData("");
        }
        return descriptionData.getDescriptionsJson();
    }

    public void setDescriptionsJSON(String descriptionsJSON) throws GeneralException {
        if (descriptionData == null) {
            descriptionData = new DescriptionData("");
        }
        descriptionData.setDescriptionsJson(descriptionsJSON);
    }

    public String getSimpleEntitlementToDelete() {
        return simpleEntitlementToDelete;
    }

    public void setSimpleEntitlementToDelete(String simpleEntitlementToDelete) {
        this.simpleEntitlementToDelete = simpleEntitlementToDelete;
    }

    public boolean getHasError() {
        List<FacesMessage> msgs = getMessages();
        boolean hasError = !Util.isEmpty(msgs);
        if (profileEditBean != null) {
            if (profileEditBean.getProfile() != null) {
                hasError |= profileEditBean.getProfile().getHasErrors();
            }
            if (profileEditBean.getProfileConstraints() != null) {
                hasError |= !Util.isEmpty(profileEditBean.getProfileConstraints().getFilterError());
            }
        }
        return hasError;
    }
 
    public void setHasError(boolean error) {
        //no-op setter to make hasError a writable type 
    }

    /*
     * Filter any attributes that are disallowed in the role's current type out of the specified
     * attributes map.
     */
    private Map<String, Object> filterDisallowedValues(Map<String, Object> valuesToFilter) {
        Map<String, Object> filteredValues = new HashMap<String, Object>();
        filteredValues.putAll(valuesToFilter);
        ObjectConfig roleConfig = ObjectConfig.getObjectConfig(Bundle.class);
        if (roleConfig != null) {
            String currentType = getRoleType();
            if (!Util.isNullOrEmpty(currentType)) {
                RoleTypeDefinition typeDef = roleConfig.getRoleType(currentType);
                if (typeDef != null) {
                    List<String> disallowedList = typeDef.getDisallowedAttributes();
                    if (!Util.isEmpty(disallowedList)) {
                        for (String disallowedAttribute : disallowedList) {
                            filteredValues.remove(disallowedAttribute);
                        }
                    }
                }
            }
        } // else anything flies if we don't have a role config or type to go off of

        return filteredValues;
    }

    //////////////////////////////////////////////////////////////////////
    // UserRightsEditValidatorContext
    //////////////////////////////////////////////////////////////////////

    @Override
    public List<String> getNewCapabilities() throws GeneralException {
        return this.capNames;
    }

    @Override
    public List<String> getNewControlledScopes() throws GeneralException {
        return ObjectUtil.getObjectIds(this.controlledScopes);
    }

    @Override
    public List<String> getExistingCapabilities() throws GeneralException {
        List<String> existingCapabilities = null;
        if (getObject() != null) {
            Map<String, List<String>> values = RoleUtil.getValuesFromProvisioningPlan(getObject());
            if (values != null) {
                existingCapabilities = values.get(Certification.IIQ_ATTR_CAPABILITIES);
            }
        }
        return existingCapabilities;
    }

    @Override
    public List<String> getExistingControlledScopes() throws GeneralException {
        List<String> scopes = null;
        if (getObject() != null) {
            scopes = ObjectUtil.getObjectIds(RoleUtil.getControlledScopesFromProvisioningPlan(getObject()));
        }
        return scopes;
    }

    @Override
    public String getCapabilityRight() {
        return null;
    }

    @Override
    public String getControlledScopeRight() {
        return null;
    }
}
