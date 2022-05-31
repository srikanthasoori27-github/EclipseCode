/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.modeler;

import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONWriter;

import sailpoint.api.RoleLifecycler;
import sailpoint.api.SailPointContext;
import sailpoint.object.Bundle;
import sailpoint.object.BundleArchive;
import sailpoint.object.Configuration;
import sailpoint.object.ExtState;
import sailpoint.object.Filter;
import sailpoint.object.Filter.MatchMode;
import sailpoint.object.Identity;
import sailpoint.object.ObjectConfig;
import sailpoint.object.Profile;
import sailpoint.object.QueryOptions;
import sailpoint.object.SODConstraint;
import sailpoint.object.Template;
import sailpoint.tools.BrandingServiceFactory;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.URLUtil;
import sailpoint.tools.xml.XMLObjectFactory;
import sailpoint.web.BaseBean;
import sailpoint.web.BaseEditBean;
import sailpoint.web.TemplateDTO;
import sailpoint.web.extjs.SessionStateBean;
import sailpoint.web.extjs.TreeNodeList;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.util.NavigationHistory;

public class RoleViewerBean extends BaseBean implements NavigationHistory.Page {
    private static final Log log = LogFactory.getLog(RoleViewerBean.class);
    
    private static final String SUGGEST_TYPE_DETECTABLE = "detectableRole";
    private static final String SUGGEST_TYPE_ASSIGNABLE = "assignableRole";
    
    private static final String QUERY_PARAM_SUGGEST_TYPE = "suggestType";
    
    private String roleToEdit;
    private String archiveToEdit;
    private String roleToClone;
    private String roleToAddTo;
    private TemplateDTO templateDTO;
    
    private int pageSize;

    public RoleViewerBean() {
        pageSize = Configuration.getSystemConfig().getInt(Configuration.BUNDLES_UI_DISPLAY_LIMIT);
        
        if (pageSize <= 0) {
            pageSize = 10;
        }
    }
    
    /** 
     * @param roleToClone ID of the role that is being cloned.
     */
    public void setRoleToClone(final String roleToClone) {
        this.roleToClone = roleToClone;
    }
    
    public String getRoleToClone() {
        return roleToClone;
    }
    
    /**
     * @param roleToAddTo ID of the role to which a new inheriting
     * role is being added.
     */
    public void setRoleToAddTo(final String roleToAddTo) {
        this.roleToAddTo = roleToAddTo;
    }
   
    public String getRoleToAddTo() {
        return roleToAddTo;
    }
   
    /**
     * @param roleToEdit ID of the role that is being edited next.
     * If the next role being edited is new, then this will be set 
     * to an empty string
     */
    public void setRoleToEdit(final String roleToEdit) {
        this.roleToEdit = roleToEdit;
    }
   
    public String getRoleToEdit() {
        return roleToEdit;
    }

    /**
     * @param archiveToEdit ID of the archive that is being edited.
     * If no archive is being edited this will be set to an empty string
     */
    public void setArchiveToEdit(final String archiveToEdit) {
        this.archiveToEdit = archiveToEdit;
    }
   
    public String getArchiveToEdit() {
        return archiveToEdit;
    }

    
    public ExtState getRoleViewerState() {
        ExtState state;
        
        try {
            Identity currentUser = getLoggedInUser();
            state = (ExtState) getSessionScope().get(
                            currentUser.getId() + ":" + 
                            RoleUtil.ROLE_VIEWER_STATE + ":" + 
                            SessionStateBean.SESSION_STATE);
        } catch (GeneralException e) {
            state = null;
            log.error("Failed to properly load the role viewer state", e);
        }
        
        if(state == null) {
            state = new ExtState();
            state.setName(RoleUtil.ROLE_VIEWER_STATE);
        }
        
        log.debug("Getting state with state: " + state.getState());
        return state;
    }
    
    @SuppressWarnings("unchecked")
    public String editRole() {
        getSessionScope().put("roleToEdit", roleToEdit);
        getSessionScope().put(BaseEditBean.FORCE_LOAD, Boolean.TRUE.toString());
        NavigationHistory.getInstance().saveHistory(this);
        
        return "gotoRoleEditor";
    }
    
    @SuppressWarnings("unchecked")
    public String editArchive() {
        try {
            BundleArchive archive = getContext().getObjectById(BundleArchive.class, archiveToEdit);
            if (archive != null) {
                String roleSessionKey = Bundle.class.getName() + ":" + archive.getSourceId();
                RoleLifecycler lifecycler = new RoleLifecycler(getContext());
                Bundle roleToEdit = lifecycler.rehydrate(archive);
                roleToEdit.load();
                getSessionScope().put(roleSessionKey, roleToEdit);
                getSessionScope().put("roleToEdit", roleToEdit.getId());
            } else {
                throw new GeneralException("Failed to get the archived role with id " + archiveToEdit);
            }
        } catch (GeneralException e) {
            log.error("Failed to edit an archive", e);
        }
        
        return "gotoRoleEditor";        
    }
    
    @SuppressWarnings({ "unchecked" })
    public String addToRole() {
        String outcome;
        
        try {
            Bundle addToRole = getContext().getObjectById(Bundle.class, roleToAddTo);
            getSessionScope().put(BaseEditBean.FORCE_LOAD, Boolean.TRUE.toString());
            RoleEditorBean roleEditor = (RoleEditorBean) getFacesContext().getApplication().createValueBinding("#{roleEditor}").getValue(getFacesContext());
            roleEditor.setObjectId(null);
            Bundle newRole = roleEditor.getObject();
            newRole.setId(null);
            newRole.addInheritance(addToRole);
            newRole.setAssignedScope(addToRole.getAssignedScope());
            newRole.load();
            roleEditor.getMembershipEditor().setReferencedRoles(newRole, Arrays.asList(new Bundle [] { addToRole }));
            getSessionScope().put("roleToEdit", roleToEdit);
            NavigationHistory.getInstance().saveHistory(this);
            outcome = "gotoRoleEditor";
        } catch (GeneralException e) {
            log.error("The role to add to is inaccessible", e);
            outcome = "";
        }
        
        return outcome;
    }
    
    @SuppressWarnings("unchecked")
    public String cloneRole() {
        String outcome;
        
        try {
            RoleEditorBean roleEditor = (RoleEditorBean) getFacesContext().getApplication().createValueBinding("#{roleEditor}").getValue(getFacesContext());
            roleEditor.clearHttpSession();
            roleEditor.setObjectId(null);
            Bundle clonedRole = (Bundle) getContext().getObjectById(Bundle.class, roleToClone);
            // clone the object, then give it a new name and
            // clear the original id
            XMLObjectFactory f = XMLObjectFactory.getInstance();
            Bundle newRole = (Bundle) f.clone(clonedRole, getContext());
            newRole.setName("Copy of " + clonedRole.getName());
            newRole.setId(null);
            newRole.setScorecard(null);
            newRole.setRoleIndex(null);
            // Null out the cloned profiles' IDs too
            List<Profile> clonedProfiles = newRole.getProfiles();
            if (clonedProfiles != null && !clonedProfiles.isEmpty()) {
                // Since these roles are new we need to let the role editor know about the newly cloned profiles.
                // Otherwise the profiles will be attached to the role but the editor won't display them.
                Map<String, ProfileDTO> editedProfiles = new HashMap<String, ProfileDTO>();
                for (Profile clonedProfile : clonedProfiles) {
                    clonedProfile.setId(null);
                    ProfileDTO clonedProfileDTO = new ProfileDTO(clonedProfile);
                    editedProfiles.put(clonedProfileDTO.getId(), clonedProfileDTO);
                }
                Map<String, Object> editState = new HashMap<String, Object>();
                editState.put(RoleEditorBean.EDITED_PROFILES, editedProfiles);
                String editStateKey = roleEditor.getEditStateSessionKey();
                getSessionScope().put(editStateKey, editState);
                // Now that the editor knows about the new profiles it will add them so if
                // we keep them attached to the role we'll get duplicates.  Null out the profiles
                // to avoid this
                newRole.setProfiles(null);
            }
            newRole.load();
            roleEditor.setObject(newRole);
            getSessionScope().put(roleEditor.getSessionKey(), newRole);
            NavigationHistory.getInstance().saveHistory(this);
            outcome = "gotoRoleEditor";
        } catch (GeneralException e) {
            log.error("The role to clone is inaccessible.", e);
            outcome = "";
        }

        return outcome;
    }
    
    public String deleteRole() {
        Bundle roleToDelete = null;
        
        try {
            roleToDelete = getContext().getObjectById(Bundle.class, roleToEdit);
            String id = roleToDelete.getId();

//            boolean error = false;

            // Make sure that no SOD policy violations are referencing this role
            // jsl - Not sure if this is necessary.  We can have the role delete
            // workflow show the approver the policies that still reference
            // the role and let them make the call?  Terminator will clean
            // up the references.  I'm leaving in the SOD usage check but
            // taking out the identity assignment check.  Impact analysis
            // will show you the losers.

            int count = countParents(SODConstraint.class, "leftBundles", id) + 
                        countParents(SODConstraint.class, "rightBundles", id);
            if (count > 0) {
                addMessage(new Message(Message.Type.Error,
                                MessageKeys.ERR_DELETE_FAILED_ROLE_HAS_SOD_REFS, roleToDelete.getName(), count), null);
//                error = true;
            } else if (roleToDelete.getPendingWorkflow() != null &&
                    !roleToDelete.getPendingWorkflow().isAutoCreated()) {
                // If there is a pending workflow, make them cancel it before deleting the role
                addMessage(new Message(Message.Type.Error,
                        MessageKeys.ERR_DELETE_FAILED_PENDING_WORKFLOW, roleToDelete.getName()));
            }
            else {
                RoleLifecycler cycler = new RoleLifecycler(getContext());
                String caseId = cycler.approveDelete(roleToDelete);

                // Bug 22804 - For Delete operation, if Role Propagation is
                //  enabled, role is marked for pendingDelete and disabled.
                // So refetching the Bundle to check if pendingDelete is set to true.
                // We can go to context only if Role Propagation is enabled,
                //  but to determine that we require workflow arguments.
                // Bug 23803 - For now, refetching anyways but using only if it is not null.
                Bundle modifiedRole = getContext().getObjectById(Bundle.class, roleToEdit);
                if (null != modifiedRole) {
                    roleToDelete = modifiedRole;
                }

                if (roleToDelete.isPendingDelete()) {
                    addMessage(new Message(Message.Type.Info,
                            MessageKeys.ROLE_DELETION_PENDING, roleToDelete.getName()), null);
                } else if (caseId != null) {
                    addMessage(new Message(Message.Type.Info, 
                            MessageKeys.ROLE_DELETION_SUBMITTED, roleToDelete.getName()), null);
                } else {
                    addMessage(new Message(Message.Type.Info,
                            MessageKeys.ROLE_DELETED_SUCCESSFULLY, roleToDelete.getName()), null);
                }
                // The tree node is refreshed by the client when the action completes
            }
        } catch (GeneralException e) {
            final String roleName;
            
            if (roleToDelete == null) {
                roleName = "?";
            } else {
                roleName = roleToDelete.getName();
            }
            
            this.addMessage(new Message(Message.Type.Error, MessageKeys.ERR_ROLE_DELETE_FAILED, new Object [] { roleName, e }));
            log.error("Failed to delete a role.", e);
        }
        
        return "";
    }
    
    public String getJsonForNode() throws JSONException, GeneralException {
        String mode = getRequestParameter("mode");
        String selectedRoleId = getRequestParameter("selectedRoleId");
        String selectedTopDownNodeId = getRequestParameter("selectedTopDownNodeId");
        String selectedBottomUpNodeId = getRequestParameter("selectedBottomUpNodeId");
        log.debug("requestParams: " + getRequestParam().toString());
        // selectedNodeId is the id of the node that is currently selected within the tree
        // nodeId is the ID of the node that is currently being expanded (if any)
        String nodeId = getRequestParameter("node");
        
        if (nodeId != null && mode == null && selectedRoleId == null && nodeId.equals("source")) {
            ExtState currentState = getRoleViewerState();
            if (currentState != null && currentState.getState() != null) {
                String state = currentState.getState();
                mode = RoleUtil.extractValueFromState(state, "currentView");
                selectedRoleId = RoleUtil.extractValueFromState(state, "selectedRoleId");
                selectedTopDownNodeId = RoleUtil.extractValueFromState(state, "selectedTopDownNodeId");
                selectedBottomUpNodeId = RoleUtil.extractValueFromState(state, "selectedBottomUpNodeId");
            } 
        } 

        // Regardless of where node ids came from, they may need to be url decoded.
        if (selectedTopDownNodeId != null && !selectedTopDownNodeId.isEmpty()) {
            selectedTopDownNodeId = URLUtil.decodeUTF8(selectedTopDownNodeId);
        }
        if (selectedBottomUpNodeId != null && !selectedBottomUpNodeId.isEmpty()) {
            selectedBottomUpNodeId = URLUtil.decodeUTF8(selectedBottomUpNodeId);
        }

        final String json;
        QueryOptions scoping = new QueryOptions();
        scoping.setScopeResults(false);
        scoping.addOwnerScope(super.getLoggedInUser());

        log.debug("Mode is " + mode + 
                  " and selectedRoleId is " + selectedRoleId + 
                  " and selectedTopDownNodeId is " + selectedTopDownNodeId + 
                  " and selectedBottomUpNodeId is " + selectedBottomUpNodeId);
        
        if ("bottomUp".equals(mode)) {
            log.debug("Getting JSON for bottom-up mode");
            json = getJsonForBottomUpNode(scoping, selectedBottomUpNodeId);
        } else {
            log.debug("Getting JSON for top-down mode");
            json = getJsonForTopDownNode(scoping, selectedTopDownNodeId);
        }
        
        return json;
    }
    
//    private Bundle getRoleToPageTo(String selectedNodeId) throws GeneralException {
//        final Bundle roleToPageTo;
//        if (selectedNodeId == null || selectedNodeId.trim().length() == 0) {
//            roleToPageTo = null;
//        } else {
//            final int endOfFirstNode = selectedNodeId.indexOf(":");
//    
//            if (endOfFirstNode > -1) {
//                roleToPageTo = getContext().getObjectById(Bundle.class, selectedNodeId.substring(0, endOfFirstNode));
//            } else {
//                roleToPageTo = getContext().getObjectById(Bundle.class, selectedNodeId);
//            } 
//        }
//        
//        return roleToPageTo;
//    }
    
    private String getJsonForBottomUpNode(QueryOptions scoping, String selectedNodeId) throws JSONException, GeneralException {
        // nodeId is the ID of the node that is currently being expanded (if any)
        String nodeId = getRequestParameter("node");
        // filterOnNode is the id of the role that was selected in the role filter
        String filterOnNode = getRequestParameter("filterOnNode");
        log.debug("node = " + nodeId + ", selectedNodeId: " + selectedNodeId + ", filterOnNode = " + filterOnNode);
        int startIndex = nodeId.lastIndexOf(':') + 1;
        String roleId = nodeId.substring(startIndex);
        Bundle selectedRole = getContext().getObjectById(Bundle.class, roleId);
        
        String pageParam = getRequestParameter("currentPage");
        
        Set<Bundle> rolesInFilter;
        Set<Bundle> permittedChildren;
        
        if (filterOnNode != null) {
            Bundle filter = getContext().getObjectById(Bundle.class, filterOnNode);
            if (filter != null) {
                rolesInFilter = RoleUtil.getRolesInHierarchy(filter, getContext(), true);
                permittedChildren = RoleUtil.getRolesInHierarchy(filter, getContext(), false);
            } else {
                // The filter is no longer valid so don't use it
                rolesInFilter = null;
                permittedChildren = null;
                filterOnNode = null;
            }
        } else {
            rolesInFilter = null;
            permittedChildren = null;
        }
        
        TreeNodeList<Bundle> roleNodes;
        Map<String, Object> additionalParams = new HashMap<String, Object>();
        Map<String, Bundle> bottomLevelRoles;
        
        if ("source".equals(nodeId)) {
            bottomLevelRoles = getBottomLevelRoles(scoping);
            additionalParams.put(RoleNodeBuilder.ID_PREFIX, "");
        } else {
            bottomLevelRoles = new HashMap<String, Bundle>();
            List<Bundle> inheritedRoles = selectedRole.getInheritance();
            for (Bundle inheritedRole : inheritedRoles) {
                if (pageParam == null  || rolesInFilter == null || rolesInFilter.contains(inheritedRole) || permittedChildren.contains(inheritedRole))
                    bottomLevelRoles.put(inheritedRole.getId(), inheritedRole);
            }
            additionalParams.put(RoleNodeBuilder.ID_PREFIX, nodeId + ":");
        }

        if (rolesInFilter != null) {
            // We don't know if we're expanding children of a filtered elements or fetching the 
            // filtered elements.  If we end up filtering everything out the first case applies.
            Map<String, Bundle> potentialChildren = new HashMap<String, Bundle>();
            potentialChildren.putAll(bottomLevelRoles);
            filterForNode(filterOnNode, bottomLevelRoles, rolesInFilter, true);
            if (bottomLevelRoles.isEmpty()) {
                bottomLevelRoles = potentialChildren;
            }
        }
        
        RoleTypeComparator typeComparator = new RoleTypeComparator(ObjectConfig.getObjectConfig(Bundle.class));
        if (selectedNodeId != null && selectedNodeId.trim().length() > 0 && !selectedNodeId.contains(":")) {
            // This ID is either a leaf or deliberately invalid.  Let's find out which
            if (!bottomLevelRoles.containsKey(selectedNodeId)) {
                // It's invalid.  We will fail if we try to use this, so let's generate a valid node id
                Bundle nonBottomLevelRole = getContext().getObjectById(Bundle.class, selectedNodeId);
                
                if (nonBottomLevelRole != null) {
                    // Work our way to the bottom, constructing an ID as we go
                    Stack<String> hierarchy = new Stack<String>();
                    hierarchy.push(selectedNodeId);
                    QueryOptions inheritingRoleFinder = new QueryOptions(Filter.containsAll("inheritance", Arrays.asList(new Bundle [] {nonBottomLevelRole})));
                    List<Bundle> inheritingRoles = getContext().getObjects(Bundle.class, inheritingRoleFinder);
                    while(inheritingRoles != null && !inheritingRoles.isEmpty()) {
                        Collections.sort(inheritingRoles, typeComparator);
                        Bundle nextRole = inheritingRoles.get(0);
                        hierarchy.push(nextRole.getId());
                        inheritingRoleFinder = new QueryOptions(Filter.containsAll("inheritance", Arrays.asList(new Bundle [] {nextRole})));
                        inheritingRoles = getContext().getObjects(Bundle.class, inheritingRoleFinder);
                    }
                    // After that whole mess our stack has the makings of a valid node id
                    StringBuilder nodeIdBuilder = new StringBuilder();
                    nodeIdBuilder.append(hierarchy.pop());
                    while (!hierarchy.isEmpty()) {
                        nodeIdBuilder.append(":").append(hierarchy.pop());
                    }
                    selectedNodeId = nodeIdBuilder.toString();
                } else {
                    // This role doesn't even exist anymore.  Just whack the ID.  We can't restore the state
                    selectedNodeId = "";
                }
            }
        }

        List<Bundle> roleList = new ArrayList<Bundle>(bottomLevelRoles.values());
        Collections.sort(roleList, typeComparator);

        additionalParams.put(RoleNodeBuilder.ROLES_TO_EXPAND, rolesInFilter);
        additionalParams.put(RoleNodeBuilder.PERMITTED_CHILDREN, permittedChildren);
        if (selectedNodeId != null)
            additionalParams.put(RoleNodeBuilder.SELECTED_NODE_ID, selectedNodeId);
        if (filterOnNode != null)
            additionalParams.put(RoleNodeBuilder.FILTERED_NODE_ID, filterOnNode);
        int currentPage = getCurrentPage(selectedNodeId, roleList);
        additionalParams.put(RoleNodeBuilder.USER_CAPABILITIES, getLoggedInUserCapabilities());
        additionalParams.put(RoleNodeBuilder.USER_RIGHTS, getLoggedInUserRights());
        
        roleNodes = new TreeNodeList<Bundle>(getContext(), roleList, new RoleNodeBuilder(getContext(), true), additionalParams, currentPage, pageSize);
        

        return roleNodes.toWrappedJSONString("children");
    }
    
    private String getJsonForTopDownNode(QueryOptions scoping, String selectedNodeId) throws JSONException, GeneralException {
        // nodeId is the ID of the node that is currently being expanded (if any)
        String nodeId = getRequestParameter("node");
        // For whatever reason, a test browser is not sending this across correctly on the initial page load,
        // so we correct for it with a default here
        if (nodeId == null)
            nodeId = "source";
        // filterOnNode is the id of the role that was selected in the role filter
        String filterOnNode = getRequestParameter("filterOnNode");
        log.debug("node = " + nodeId + ", selectedNodeId: " + selectedNodeId + ", filterOnNode = " + filterOnNode);
        int startIndex = nodeId.lastIndexOf(':') + 1;
        String roleId = nodeId.substring(startIndex);
        Bundle selectedRole = getContext().getObjectById(Bundle.class, roleId);
        String pageParam = getRequestParameter("currentPage");
        
        TreeNodeList<Bundle> roleNodes;
        Map<String, Object> additionalParams = new HashMap<String, Object>();

        Set<Bundle> rolesInFilter;
        Set<Bundle> permittedChildren;
        
        if (filterOnNode != null) {
            Bundle filter = getContext().getObjectById(Bundle.class, filterOnNode);
            if (filter != null) {
                rolesInFilter = RoleUtil.getRolesInHierarchy(filter, getContext(), false);
                permittedChildren = RoleUtil.getRolesInHierarchy(filter, getContext(), true);
            } else {
                // The filter is no longer valid so don't use it
                rolesInFilter = null;
                permittedChildren = null;
                filterOnNode = null;
            }
        } else {
            rolesInFilter = null;
            permittedChildren = null;
        }

        Map<String, Bundle> topLevelRoles;
        
        if ("source".equals(nodeId)) {
            topLevelRoles = getTopLevelRoles(scoping);
            additionalParams.put(RoleNodeBuilder.ID_PREFIX, "");
        } else {
            QueryOptions includingRoleFinder = new QueryOptions(Filter.containsAll("inheritance", Arrays.asList(new Bundle [] {selectedRole})));
            List<Bundle> inheritingRoles = getContext().getObjects(Bundle.class, includingRoleFinder);
            topLevelRoles = new HashMap<String, Bundle>();
            for (Bundle inheritingRole : inheritingRoles) {
                if (pageParam == null  || rolesInFilter == null || rolesInFilter.contains(inheritingRole) || permittedChildren.contains(inheritingRole))
                    topLevelRoles.put(inheritingRole.getId(), inheritingRole);
            }
            additionalParams.put(RoleNodeBuilder.ID_PREFIX, nodeId + ":");
        }
        
        if (rolesInFilter != null) {
            // We don't know if we're expanding children of a filtered elements or fetching the 
            // filtered elements.  If we end up filtering everything out the first case applies. 
            Map<String, Bundle> potentialChildren = new HashMap<String, Bundle>();
            potentialChildren.putAll(topLevelRoles);
            filterForNode(filterOnNode, topLevelRoles, rolesInFilter, false);
            if (topLevelRoles.isEmpty()) {
                topLevelRoles = potentialChildren;
            }
        }

        RoleTypeComparator typeComparator = new RoleTypeComparator(ObjectConfig.getObjectConfig(Bundle.class));
        if (selectedNodeId != null && selectedNodeId.trim().length() > 0 && !selectedNodeId.contains(":")) {
            // This ID is either a leaf or deliberately invalid.  Let's find out which
            if (!topLevelRoles.containsKey(selectedNodeId)) {
                // It's invalid.  We will fail if we try to use this, so let's generate a valid node id
                Bundle nonTopLevelRole = getContext().getObjectById(Bundle.class, selectedNodeId);
                if (nonTopLevelRole != null) {
                    // Work our way to the bottom, constructing an ID as we go
                    Stack<String> hierarchy = new Stack<String>();
                    hierarchy.push(selectedNodeId);
                    List<Bundle> inheritedRoles = nonTopLevelRole.getInheritance();
                    while(inheritedRoles != null && !inheritedRoles.isEmpty()) {
                        Collections.sort(inheritedRoles, typeComparator);
                        Bundle nextRole = inheritedRoles.get(0);
                        hierarchy.push(nextRole.getId());
                        inheritedRoles = nextRole.getInheritance();
                    }
                    // After that whole mess our stack has the makings of a valid node id
                    StringBuilder nodeIdBuilder = new StringBuilder();
                    nodeIdBuilder.append(hierarchy.pop());
                    while (!hierarchy.isEmpty()) {
                        nodeIdBuilder.append(":").append(hierarchy.pop());
                    }
                    
                    selectedNodeId = nodeIdBuilder.toString();
                } else {
                    // This role doesn't even exist anymore.  Just whack the ID.  We can't restore the state
                    selectedNodeId = "";
                }
            }
        }

        List<Bundle> roleList = new ArrayList<Bundle>(topLevelRoles.values());
        Collections.sort(roleList, typeComparator);
        additionalParams.put(RoleNodeBuilder.ROLES_TO_EXPAND, rolesInFilter);
        additionalParams.put(RoleNodeBuilder.PERMITTED_CHILDREN, permittedChildren);
        if (selectedNodeId != null)
            additionalParams.put(RoleNodeBuilder.SELECTED_NODE_ID, selectedNodeId);
        if (filterOnNode != null)
            additionalParams.put(RoleNodeBuilder.FILTERED_NODE_ID, filterOnNode);
        int currentPage = getCurrentPage(selectedNodeId, roleList);
        additionalParams.put(RoleNodeBuilder.USER_CAPABILITIES, getLoggedInUserCapabilities());
        additionalParams.put(RoleNodeBuilder.USER_RIGHTS, getLoggedInUserRights());
        
        roleNodes = new TreeNodeList<Bundle>(getContext(), roleList, new RoleNodeBuilder(getContext(), false), additionalParams, currentPage, pageSize);
        
        final String jsonString = roleNodes.toWrappedJSONString("children");
        log.debug("Returning: " + jsonString);
        return jsonString;
    }
    
    
    /*
     * Return a Map of bottom level roles keyed by id
     */
    @SuppressWarnings("unchecked")
    private Map<String, Bundle> getBottomLevelRoles(QueryOptions scoping) throws GeneralException {
        Iterator<String> bottomLevelRoleIdIterator = 
            (Iterator<String>) getContext().search("sql: select id from " + BrandingServiceFactory.getService().brandTableName( "spt_bundle" ) + " where id not in (select child from " + BrandingServiceFactory.getService().brandTableName( "spt_bundle_children" ) + ")", null, scoping);
        // Our Filters don't do well with large sets.
        // In the ideal world type is a column that we can join on so we could limit our result set and not have to worry about this.
        // In the current world RoleTypeDefinition is embedded in the SystemConfig.  As a consequence we have no enumerable property 
        // to sort by type with and we're stuck doing in-memory sorting.  In order to do that we have to bring all the roles into memory.
        // Hibernate doesn't like doing big 'in' queries so we're stuck breaking this up into a bunch of smaller queries in order to bring
        // everything in.
        List<List<String>> bottomLevelRoleIdsList = new ArrayList<List<String>>();
        final int MAX_IN_FILTER_SIZE = 100; // Filter.MAX_IN_FILTER_SIZE
        while (bottomLevelRoleIdIterator.hasNext()) {
            List<String> bottomLevelRoleIds = new ArrayList<String>();
            while (bottomLevelRoleIds.size() < MAX_IN_FILTER_SIZE && bottomLevelRoleIdIterator.hasNext()) {
                bottomLevelRoleIds.add(bottomLevelRoleIdIterator.next());
            }
            bottomLevelRoleIdsList.add(bottomLevelRoleIds);
        }

        Map<String, Bundle> bottomLevelRoles = new HashMap<String, Bundle>();
        
        if (!bottomLevelRoleIdsList.isEmpty()) {
            for (List<String> bottomLevelRoleIds : bottomLevelRoleIdsList) {
                QueryOptions scopedRoleQuery = new QueryOptions(Filter.in("id", bottomLevelRoleIds));
                scopedRoleQuery.setScopeResults(scoping.getScopeResults());
                List<Filter> scopeExtensions = scoping.getScopeExtensions();
                
                if (scopeExtensions != null && !scopeExtensions.isEmpty()) {
                    for (Filter extension : scopeExtensions) {
                        scopedRoleQuery.extendScope(extension);
                    }
                }
                
                List<Bundle> bottomLevelRoleList = getContext().getObjects(Bundle.class, scopedRoleQuery);
                
                
                if (bottomLevelRoleList != null && !bottomLevelRoleList.isEmpty()) {
                    for (Bundle bottomLevelRole : bottomLevelRoleList)
                        bottomLevelRoles.put(bottomLevelRole.getId(), bottomLevelRole);
                }                
            }
        }
        
        return bottomLevelRoles;
    }
    
    private Map<String, Bundle> getTopLevelRoles(QueryOptions scoping) throws GeneralException {
        scoping.add(Filter.isempty("inheritance"));

        List<Bundle> topLevelRoleList = getContext().getObjects(Bundle.class, scoping);
        
        Map<String, Bundle> topLevelRoles = new HashMap<String, Bundle>();
        for (Bundle topLevelRole : topLevelRoleList) {
            topLevelRoles.put(topLevelRole.getId(), topLevelRole);
        }
        
        return topLevelRoles;
    }
    
    private void filterForNode(String filterNodeId, Map<String, Bundle> rolesToFilter, Set<Bundle> rolesToKeep, boolean bottomUpMode) 
        throws GeneralException {
        List<Bundle> filteredRoles = new ArrayList<Bundle>(rolesToFilter.values());
        
        for(Bundle candidateForRemoval : filteredRoles) {
            if (!rolesToKeep.contains(candidateForRemoval)) {
                rolesToFilter.remove(candidateForRemoval.getId());
            }
        }
    }

    public String getJsonForRole() {
        String nodeId = getRequestParameter("roleNodeId");
        log.debug("nodeId is " + nodeId);
        int startIndex = nodeId.lastIndexOf(':') + 1;
        String roleId = nodeId.substring(startIndex);
        
        String roleString;
        
        try {
            Bundle role = getContext().getObjectById(Bundle.class, roleId);
            // Keep in mind that the role viewer displays the role in its persisted state and will not necessarily match
            // the contents of the role editor because it may display information that is pending approval.
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
    
    @SuppressWarnings("unchecked")
    public String getRoleGridInfo() {
        Map requestParams = getRequestParam();
        SailPointContext ctx = getContext();
    
        log.debug("getRoleGridInfo() Request params: " + requestParams.toString());
        String filteredNodeId = (String) requestParams.get("filteredNodeId");
        String roleId = (String) requestParams.get("roleNodeId");
        String orderBy = (String) requestParams.get("sort");
        String orderBy2;
        
        if ("roleType".equals(orderBy)) {
            // Workaround: 'type' conflicted with a built-in ExtJS property, so 
            // we have to use 'roleType' instead on the browser and make a 
            // conversion here -- Bernie
            orderBy = "type";
            orderBy2 = "name";
        } else {
            orderBy2 = "type";
        }
        
        String sortDir = (String) requestParams.get("dir");
        int limit = getResultLimit();
        
        Writer jsonString = new StringWriter();
        JSONWriter jsonWriter = new JSONWriter(jsonString);

        try {
            QueryOptions ops = new QueryOptions();
            
            Filter roleFilter = getRoleFilter(filteredNodeId, ctx);
            if (roleFilter != null)
                ops.add(roleFilter);
                
            ops.setScopeResults(true);
            ops.addOwnerScope(super.getLoggedInUser());

            if (orderBy != null) {
                ops.addOrdering(orderBy, sortDir == null || "ASC".equals(sortDir));
                ops.addOrdering(orderBy2, sortDir == null || "ASC".equals(sortDir));
            }
            
            ops.setFirstRow(0);
            
            Iterator<Object[]> queriedRoleIds = ctx.search(Bundle.class, ops, Arrays.asList(new String [] {"id"}));
            int start = 0;
            int roleIndex = 0;
            boolean isRoleFound = false;
            
            if (queriedRoleIds != null) {
                while (queriedRoleIds.hasNext() && !isRoleFound) {
                    Object[] roleInfo = queriedRoleIds.next();
                    String currentRoleId = (String) roleInfo[0];
                    if (currentRoleId != null && currentRoleId.equals(roleId)) {
                        isRoleFound = true;
                        log.debug("Found role at index " + roleIndex);
                    } else {
                        roleIndex++;
                    }
                }
            } 

            if (isRoleFound) {
                // Get the start of the page.  Note that we rely on the truncation that occurs
                // during integer division to make the proper calculation.
                int page = roleIndex / limit;
                start = page * limit;
            }

            jsonWriter.object();
            jsonWriter.key("start");
            jsonWriter.value(start);
            jsonWriter.key("limit");
            jsonWriter.value(limit);
            jsonWriter.key("roleNodeId");
            jsonWriter.value(roleId);
            jsonWriter.key("filteredNodeId");
            jsonWriter.value(filteredNodeId);

            jsonWriter.endObject();
            
        } catch (GeneralException e) {
            log.error("Could not fetch the roles at this time.", e);
        } catch (JSONException e) {
            log.error("Could not generate JSON for roles", e);
        }
        
        log.debug("gridInfoResponse: " + jsonString.toString());
        return jsonString.toString();
    }
    
    private Filter getRoleFilter(String filteredNodeId, SailPointContext ctx) {
        List<String> displayableRoleIds = new ArrayList<String>();
        Filter roleFilter = null;
        
        if (filteredNodeId != null && filteredNodeId.trim().length() > 0) {
            try {
                int startIndex = filteredNodeId.lastIndexOf(":");
                String filteredRoleId = filteredNodeId.substring(startIndex + 1);
                Bundle filteredRole = ctx.getObjectById(Bundle.class, filteredRoleId);
                if (filteredRole != null) {
                    Set<Bundle> displayableRoles = RoleUtil.getAllRolesInHierarchy(ctx, filteredRole);
                    if (displayableRoles != null && !displayableRoles.isEmpty()) {
                        for (Bundle displayableRole : displayableRoles) {
                            displayableRoleIds.add(displayableRole.getId());
                        }
                    }
                }
            } catch (GeneralException e) {
                log.error("Failed to filter roles for grid", e);
            }
        }
        
        if (!displayableRoleIds.isEmpty()) {
            roleFilter = Filter.in("id", displayableRoleIds);
        }
        
        return roleFilter;
    }


    @SuppressWarnings("unchecked")
    public String getRoleByQuery() {
        Map requestParams = getRequestParam();
        SailPointContext ctx = getContext();
    
        log.debug("getRoleByQuery() Request params: " + requestParams.toString());
        String filteredNodeId = (String) requestParams.get("filteredNodeId");
        String queryVal = (String) requestParams.get("query");
        String orderBy = (String) requestParams.get("sort");
        String orderBy2;
        if ("roleType".equals(orderBy)) {
            // Workaround: 'type' conflicted with a built-in ExtJS property, so 
            // we have to use 'roleType' instead on the browser and make a 
            // conversion here -- Bernie
            orderBy = "type";
            orderBy2 = "displayableName";
        } else {
            orderBy2 = "type";
        }
        String sortDir = (String) requestParams.get("dir");
        int start = -1;
        String startString = (String) requestParams.get("start");
        if (startString != null) {
            start = Integer.parseInt(startString);
        }
        int limit = getResultLimit();
        
        ObjectConfig roleConfig = ObjectConfig.getObjectConfig(Bundle.class);
                
        String jsonString;

        try {
            QueryOptions ops = new QueryOptions();
            
            Filter roleFilter = getRoleFilter(filteredNodeId, ctx);
            if (roleFilter != null)
                ops.add(roleFilter);
            
            ops.setScopeResults(true);
            ops.addOwnerScope(super.getLoggedInUser());

            // Do a like query if a name is specified from the filter
            if (queryVal != null) {
                ops.add(Filter.or(
                    Filter.ignoreCase(Filter.like("name", queryVal, MatchMode.START)),
                    Filter.ignoreCase(Filter.like("displayableName", queryVal, MatchMode.START))
                ));
            }
            
            // if this query is coming from a SailPoint.RoleSuggest then filter 
            // by assignable or detectable if necessary
            if (requestParams.containsKey(QUERY_PARAM_SUGGEST_TYPE)) {
                String suggestType = (String) requestParams.get(QUERY_PARAM_SUGGEST_TYPE);
                if (SUGGEST_TYPE_ASSIGNABLE.equals(suggestType)) {
                    ops.add(Filter.and(
                        Filter.in("type", new RoleConfig().getAssignableRoleTypes()),
                        Filter.eq("disabled", false)
                    ));
                } else if (SUGGEST_TYPE_DETECTABLE.equals(suggestType)) {
                    ops.add(Filter.in("type", new RoleConfig().getDetectableRoleTypes()));
                }
            }

            if (orderBy != null) {
                ops.addOrdering(orderBy, sortDir == null || "ASC".equals(sortDir));
                ops.addOrdering(orderBy2, sortDir == null || "ASC".equals(sortDir));
            }
            
            // Get the total result count before applying a limit
            int numRoleResults = ctx.countObjects(Bundle.class, ops);
            
            if (limit > 0) {
                ops.setResultLimit(limit);
            }

            // Find a proper start value if we have a name to go from (used for grid selection)
            ops.setFirstRow(start);
                        
            Iterator<Object[]> queriedRoles = ctx.search(Bundle.class, ops, Arrays.asList(new String [] {"id", "name", "type", "displayableName"}));
            
            jsonString = RoleUtil.getGridJsonForRoles(
                                    roleConfig, 
                                    queriedRoles, 
                                    numRoleResults, 
                                    ctx);
        } catch (GeneralException e) {
            log.error("Could not fetch the roles at this time.", e);
            jsonString = "{}";
        }

        log.debug("Returning " + jsonString);
        return jsonString;
    }
    
    @SuppressWarnings("unchecked")
    public String getInheritedRoles() {
        String jsonString;
        Map requestParams = getRequestParam();
        String archiveId = (String) requestParams.get("archiveId");
        String roleId = (String) requestParams.get("roleId");
        
        try {
            Bundle role = getRoleForReferenceGrid(roleId, archiveId);
            List<Bundle> inheritedRoles = role.getInheritance();
            jsonString = getJsonForRoleList(inheritedRoles);
        } catch (GeneralException e) {
            jsonString = handleFailureToGetList(roleId);
        }
        
        return jsonString;
    }
    
    @SuppressWarnings("unchecked")
    public String getPermittedRoles() {
        String jsonString;
        Map requestParams = getRequestParam();
        String archiveId = (String) requestParams.get("archiveId");
        String roleId = (String) requestParams.get("roleId");
        
        try {
            Bundle role = getRoleForReferenceGrid(roleId, archiveId);
            List<Bundle> permittedRoles = role.getPermits();
            jsonString = getJsonForRoleList(permittedRoles);
        } catch (GeneralException e) {
            jsonString = handleFailureToGetList(roleId);
        }
        
        return jsonString;
    }
    
    @SuppressWarnings("unchecked")
    public String getRequiredRoles() {
        String jsonString;
        Map requestParams = getRequestParam();
        String archiveId = (String) requestParams.get("archiveId");
        String roleId = (String) requestParams.get("roleId");
        
        try {
            Bundle role = getRoleForReferenceGrid(roleId, archiveId);
            List<Bundle> requiredRoles = role.getRequirements();
            jsonString = getJsonForRoleList(requiredRoles);
        } catch (GeneralException e) {
            jsonString = handleFailureToGetList(roleId);
        }
        
        return jsonString;
    }
    
    private Bundle getRoleForReferenceGrid(String roleId, String archiveId) throws GeneralException {
        Bundle role;
        if (archiveId != null && archiveId.trim().length() > 0) {
            BundleArchive roleArchive = getContext().getObjectById(BundleArchive.class, archiveId);
            if (roleArchive == null) {
                throw new GeneralException("Could not find the role archive with id " + archiveId);
            }
            RoleLifecycler lifecycler = new RoleLifecycler(getContext());
            role = lifecycler.rehydrate(roleArchive);
        } else {
            role = getContext().getObjectById(Bundle.class, roleId);
        }
        
        return role;
    }
    
    @SuppressWarnings("unchecked")
    private String getJsonForRoleList(List<Bundle> roleList) {
        String results;
        Map requestParams = getRequestParam();
        String orderBy = (String) requestParams.get("sort");
        String sortDir = (String) requestParams.get("dir");
        if (null == sortDir || "" == sortDir) {
            try{
                JSONArray sortArray = new JSONArray(orderBy);
                JSONObject sortObject = sortArray.getJSONObject(0);
                orderBy = sortObject.getString("property");
                sortDir = sortObject.getString("direction");
            }catch(Exception e){
                log.debug("Invalid sort input.");
            }
        }
        if ("roleType".equals(orderBy)) {
            // Workaround: 'type' conflicted with a built-in ExtJS property, so 
            // we have to use 'roleType' instead on the browser and make a 
            // conversion here -- Bernie
            orderBy = "type";
        }
        int start = -1;
        String startString = (String) requestParams.get("start");
        if (startString != null) {
            start = Integer.parseInt(startString);
        }
        int limit = getResultLimit();

        String roleId = (String) requestParams.get("roleId");
        
        List<Object[]> roleInfoToReturn = new ArrayList<Object[]>();
        int listCount = 0;
        
        ObjectConfig roleConfig = ObjectConfig.getObjectConfig(Bundle.class);
        if (roleConfig == null) {
            results = handleFailureToGetList(roleId);
        } else {
            listCount = roleList.size();
            
            if (roleList != null && !roleList.isEmpty()) {
                RoleTypeComparator typeComparator = new RoleTypeComparator(ObjectConfig.getObjectConfig(Bundle.class));
                Collections.sort(roleList, typeComparator);
                
                if (sortDir != null && sortDir.equals("DESC")){
                    Collections.reverse(roleList);
                }
                
                int end = start + limit;
                if (end > roleList.size())
                    end = roleList.size();
                
                List<Bundle> rolesToReturn = roleList.subList(start, end);
                for (Bundle roleToReturn : rolesToReturn) {
                    Object[] info = new Object[] {
                    	roleToReturn.getId(),
                        roleToReturn.getName(),
                        roleToReturn.getType(),
                        roleToReturn.getDescription()
                    };
                    roleInfoToReturn.add(info);
                }
            }
            
            results = RoleUtil.getGridJsonForRoles(
                                    roleConfig, 
                                    roleInfoToReturn.iterator(), 
                                    listCount, 
                                    getContext());
        }
        
        return results;
    }

    public String getSimpleEntitlements() {
        Map requestParams = getRequestParam();
        Bundle role = null;
        String jsonString;

        try {
            //defect 24161 when we are dealing with archived roles we need to get the role from the archive
            if(requestParams.containsKey("prefix") && requestParams.get("prefix").equals("archive")){
                role = getArchiveBundle((String)requestParams.get("archiveId"));
            } else {
                role = getContext().getObjectById(Bundle.class, (String)requestParams.get("roleId"));
            }  
            // Keep in mind that the role viewer displays the role in its persisted state and will not necessarily match
            // the contents of the role editor because it may display information that is pending approval.
            if (role != null)
                jsonString = RoleUtil.getReadOnlySimpleEntitlementsJson(role, getRequestParam(), null, false, getLocale(), new RoleUtil.StrictSimpleEntitlementCriteria());
            else
                jsonString = "{}";
        } catch (GeneralException e) {
            log.error(e);
            jsonString = "{}";
        } catch (JSONException e) {
            log.error(e);
            jsonString = "{}";
        }

        log.debug("getSimpleEntitlements returning: " + jsonString );

        return jsonString;
    }

    public String getSimpleIncludedEntitlements() {
        Map requestParams = getRequestParam();
        Bundle role = null;

        String jsonString;

        try {
            //defect 24161 when we are dealing with archived roles we need to get the role from the archive
            if(requestParams.containsKey("prefix") && requestParams.get("prefix").equals("archive")){
                role = getArchiveBundle((String)requestParams.get("archiveId"));
            } else {
                role = getContext().getObjectById(Bundle.class, (String)requestParams.get("roleId"));
            }  
            // Keep in mind that the role viewer displays the role in its persisted state and will not necessarily match
            // the contents of the role editor because it may display information that is pending approval.
            if (role != null)
                jsonString = RoleUtil.getReadOnlySimpleIncludedEntitlementsJson(role, getRequestParam(), getContext(), getLoggedInUser(), getUserTimeZone(), getLocale(), new RoleUtil.StrictSimpleEntitlementCriteria());
            else
                jsonString = "{}";
        } catch (GeneralException e) {
            log.error(e);
            jsonString = "{}";
        } catch (JSONException e) {
            log.error(e);
            jsonString = "{}";
        }

        log.debug("getSimpleEntitlements returning: " + jsonString );

        return jsonString;
    }
    private Bundle getArchiveBundle(String archiveId){
        Bundle role = null;
        try {
            BundleArchive bundleArchive = getContext().getObjectById(BundleArchive.class, archiveId);
            String archivedRoleXml = bundleArchive.getArchive();
            XMLObjectFactory xmlDeserializer = XMLObjectFactory.getInstance();
            role = (Bundle)xmlDeserializer.parseXml(getContext(), archivedRoleXml, false);
            Bundle test = role;
        } catch (GeneralException e) {
            log.error("Unable to get role archives", e);
        }
        return role;
    }

    private String handleFailureToGetList(String roleId) {
        log.error("Failed to get a role with id " + roleId);
        
        Writer jsonString = new StringWriter();
        JSONWriter jsonWriter = new JSONWriter(jsonString);

        try {
            jsonWriter.object();
            jsonWriter.key("roles");
            List<JSONObject> roleList = new ArrayList<JSONObject>(); 
            jsonWriter.value(new JSONArray(roleList));
            jsonWriter.key("numRoleResults");
            jsonWriter.value(0);
            jsonWriter.endObject();
        } catch (JSONException je) {
            log.error("Could not convert the roles to JSON.", je);
        }

        return jsonString.toString();
    }
        
    private int getCurrentPage(String selectedNodeId, List<Bundle> roleList) throws GeneralException {
        int currentPage;
        
        String currentPageVal = getRequestParameter("currentPage");
        log.debug("Current page is " + currentPageVal);
        if (currentPageVal == null) {
            // This is an initial load.  Apply the algorithm to detect an appropriate page
            if (selectedNodeId != null && selectedNodeId.trim().length() > 0) {
                int endOfTreeTopId = selectedNodeId.indexOf(":");
                String selectedTreeTopId = null;
                if (endOfTreeTopId > 0)
                    selectedTreeTopId = selectedNodeId.substring(0, endOfTreeTopId);
                else
                    selectedTreeTopId = selectedNodeId;
                
                Bundle selectedTreeTop = getContext().getObjectById(Bundle.class, selectedTreeTopId);
                currentPage = RoleUtil.getPageForRole(selectedTreeTop, roleList, pageSize);
            } else {
                currentPage = 1;
            }
        } else {
            currentPage = Integer.parseInt(currentPageVal);
            String pagingDirectionVal = getRequestParameter("pagingDirection");
            log.debug("pagingDirectionVal is " + pagingDirectionVal);
            if ("up".equals(pagingDirectionVal)) {
                currentPage--;
            } else {
                currentPage++;
            }
        }

        return currentPage;
    }

    /**
     * List of templates.
     *
     * @ignore
     * Where is this used?
     * sigh, even not sure which xhtml uses it.
     * Seems superfluous, hence no benefit to convert it to Form.
     */
    public TemplateDTO getTemplateDTO() throws GeneralException{
        if(templateDTO==null) {
            Map requestParams = getRequestParam();
            String roleId = (String) requestParams.get("roleId");
            if(roleId!=null) {
                Bundle role = getRoleForReferenceGrid(roleId, null);
                
                List<Template> templates = role.getOldTemplates();
                if(templates==null || templates.isEmpty()) {
                    templateDTO = new TemplateDTO(new Template());
                } else {
                    templateDTO = new TemplateDTO(templates.get(0));
                }
            }
        }
        return templateDTO;
    }

    ////////////////////////////////////////////////////////////////////////////
    //
    // NavigationHandler.Page interface
    //
    ////////////////////////////////////////////////////////////////////////////

    public String getPageName() {
        return "Role Modeler";
    }

    public String getNavigationString() {
        return "viewModeler";
    }

    public Object calculatePageState() {
        return null;
    }

    public void restorePageState(Object state) {
 
    }
}
