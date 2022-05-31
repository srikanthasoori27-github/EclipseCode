/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.modeler;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.SailPointContext;
import sailpoint.object.Bundle;
import sailpoint.object.Capability;
import sailpoint.object.Filter;
import sailpoint.object.ObjectConfig;
import sailpoint.object.QueryOptions;
import sailpoint.object.RoleTypeDefinition;
import sailpoint.object.SPRight;
import sailpoint.tools.GeneralException;
import sailpoint.web.Authorizer;
import sailpoint.web.extjs.TreeNode;
import sailpoint.web.extjs.TreeNodeBuilder;
import sailpoint.web.extjs.TreeNodeList;
import sailpoint.web.util.WebUtil;

public class RoleNodeBuilder implements TreeNodeBuilder<Bundle> {
    private static final Log log = LogFactory.getLog(RoleNodeBuilder.class);
    private boolean bottomUp;
    private SailPointContext context;
    private RoleConfig roleConfig;
    
    public static final String PERMITTED_CHILDREN = "permittedChildren";
    public static final String ROLES_TO_EXPAND = "rolesToExpand";
    public static final String SELECTED_NODE_ID = "selectedNodeId";
    public static final String FILTERED_NODE_ID = "filteredNodeId";
    public static final String EDITABLE = "editable";
    public static final String INSCOPE = "inScope";
    public static final String USER_RIGHTS = "userRights";
    public static final String USER_CAPABILITIES = "userCapabilities";
    public static final String ID_PREFIX = "idPrefix";
    
    /**
     * @param bottomUp true if the tree should present roles in a bottom up (specific-role-first)
     * fashion; false if the roles are being presented in a top-down manner
     */
    public RoleNodeBuilder(SailPointContext context, boolean bottomUp) {
        this.context = context;
        this.bottomUp = bottomUp;
        roleConfig = new RoleConfig();
    }

    @SuppressWarnings("unchecked")
    public TreeNode buildNode(Bundle role, Map<String, Object> additionalParameters, int pageSize) {
        Set<Bundle> rolesToExpand = (Set<Bundle>) additionalParameters.get(ROLES_TO_EXPAND);
        Set<Bundle> permittedChildren = (Set<Bundle>) additionalParameters.get(PERMITTED_CHILDREN);
        String selectedNodeId = (String) additionalParameters.get(SELECTED_NODE_ID);
        String filteredNodeId = (String) additionalParameters.get(FILTERED_NODE_ID);
        boolean isExpanded = rolesToExpand != null && rolesToExpand.contains(role);
        if (selectedNodeId != null)
            isExpanded |= selectedNodeId.contains(role.getId());

        String idPrefix = (String) additionalParameters.get(ID_PREFIX);
        String nodeId = idPrefix + role.getId();
        
        List<Bundle> candidateRolesForChildNodes = new ArrayList<Bundle>();
        
        if (bottomUp) {
            candidateRolesForChildNodes = role.getInheritance();
        } else {
            try {
                QueryOptions includingRoleFinder = new QueryOptions(Filter.containsAll("inheritance", Arrays.asList(new Bundle [] {role})));
                candidateRolesForChildNodes = context.getObjects(Bundle.class, includingRoleFinder);
            } catch (GeneralException e) {
                log.error("Could not find parents for the role " + role.getName(), e);
            }
        }
        
        List<Bundle> rolesForChildNodes = new ArrayList<Bundle>();
        
        if (filteredNodeId != null && filteredNodeId.endsWith(role.getId())) {
            // Add all the children of the filtered role
            rolesForChildNodes.addAll(candidateRolesForChildNodes);
        } else if (filteredNodeId != null) {
            // Exclude sibling roles within the hierarchy
            for (Bundle candidateRoleForChildNode : candidateRolesForChildNodes) {
                if (rolesToExpand.contains(candidateRoleForChildNode) || 
                    filteredNodeId.contains(candidateRoleForChildNode.getId()) || 
                    permittedChildren.contains(candidateRoleForChildNode)) {
                    rolesForChildNodes.add(candidateRoleForChildNode);
                }
            }
        } else {
            // If there's no filter, just add it all
            rolesForChildNodes.addAll(candidateRolesForChildNodes);
        }
        
        String iconCls = getIconClassForType(role.getType());
        
        // Determine and add role-specific node information
        String textClass;
        
        if (role.isPendingDelete() ||
                (role.getPendingWorkflow() != null && !role.getPendingWorkflow().isAutoCreated())) {
            textClass = "pendingNodeCls";
        } else if (role.isDisabled()) {
            textClass = "disabledNodeCls"; 
        } else {
            textClass = null;
        }
        
        Map<String, Object> extensions = new HashMap<String, Object>();
        if (textClass != null) {
            extensions.put("cls", textClass);
        }
        
        boolean inScope = true;
        try {
            inScope = Authorizer.hasScopeAccess(role, context);
        } catch (GeneralException e) {
            log.warn("Failed to determine scope access for role " + role.getName(), e);
        }

        extensions.put(EDITABLE, isEditable(role, (List<Capability>)additionalParameters.get(USER_CAPABILITIES), (Collection<String>)additionalParameters.get(USER_RIGHTS))
                && inScope);
        extensions.put(INSCOPE, inScope);

        TreeNode roleNode = new TreeNode(nodeId, WebUtil.escapeHTML(role.getDisplayableName(), false), iconCls, candidateRolesForChildNodes == null || candidateRolesForChildNodes.size() == 0, isExpanded, extensions);
        if (isExpanded) {            
            if (rolesForChildNodes != null && !rolesForChildNodes.isEmpty()) {
                // Need to create a new Map here so as to not impact the siblings' IDs
                Map<String, Object> childParameters = new HashMap<String, Object>();
                childParameters.put(ID_PREFIX, nodeId + ":");
                childParameters.put(ROLES_TO_EXPAND, rolesToExpand);
                childParameters.put(PERMITTED_CHILDREN, permittedChildren);
                childParameters.put(SELECTED_NODE_ID, selectedNodeId);
                childParameters.put(FILTERED_NODE_ID, filteredNodeId);
                childParameters.put(USER_CAPABILITIES, additionalParameters.get(USER_CAPABILITIES));
                childParameters.put(USER_RIGHTS, additionalParameters.get(USER_RIGHTS));
                
                RoleTypeComparator typeComparator = new RoleTypeComparator(ObjectConfig.getObjectConfig(Bundle.class));
                Collections.sort(rolesForChildNodes, typeComparator);
                
                int childPage = 1;
                
                // Page to the portion containing the child role on the selection path if necessary
                if (selectedNodeId.contains(nodeId)) {
                    if (selectedNodeId != null) {
                        String remainingPath;
                        
                        if (nodeId == null || nodeId.trim().length() == 0) {
                            remainingPath = "";
                        } else {
                            if (selectedNodeId.equals(nodeId))
                                remainingPath = selectedNodeId;
                            else
                                remainingPath = selectedNodeId.substring(nodeId.length() + 1);
                        }
                        int endOfNextNode = remainingPath.indexOf(":");
                        final String selectedRoleId;
                        if (endOfNextNode == -1)
                            selectedRoleId = remainingPath;
                        else 
                            selectedRoleId = remainingPath.substring(0, endOfNextNode);
                        
                        if (selectedRoleId != null && selectedRoleId.trim().length() > 0) {
                            try {
                                Bundle roleForPage = context.getObjectById(Bundle.class, selectedRoleId);
                                childPage = RoleUtil.getPageForRole(roleForPage, rolesForChildNodes, pageSize);
                            } catch (GeneralException e) {
                                log.error("Could not find a role to page to", e);
                            }
                        }
                    }
                }
                
                TreeNodeList<Bundle> children = new TreeNodeList<Bundle>(context, rolesForChildNodes, this, childParameters, childPage, pageSize); 
                roleNode.addChildren(children);
            }
        }
        
        return roleNode;
    }
    
    private String getIconClassForType(String roleType) {
        String iconClass = null;
        
        RoleTypeDefinition roleTypeDef = roleConfig.getRoleTypeDefinition(roleType);
        if (roleTypeDef != null) {
            iconClass = roleTypeDef.getIcon();
        }
        
        return iconClass;
    }

    private boolean isEditable(Bundle role, List<Capability> userCapabilities, Collection<String> userRights) {
        if (role == null) {
            return false;
        } else if (role.isPendingDelete()) {
            return false;
        } else {
            ObjectConfig roleConfig = ObjectConfig.getObjectConfig(Bundle.class);
            RoleTypeDefinition roleTypeDef = roleConfig.getRoleType(role);
            if (null != roleTypeDef) {
                List<SPRight> requiredRights = roleTypeDef.getRights();
                return Authorizer.hasAccess(userCapabilities, userRights, requiredRights);
            } else {
                return false;
            }
        }
    }    
}
