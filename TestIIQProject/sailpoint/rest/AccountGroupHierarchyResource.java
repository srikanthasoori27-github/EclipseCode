package sailpoint.rest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;

import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.core.MultivaluedMap;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.authorization.RightAuthorizer;
import sailpoint.object.Filter;
import sailpoint.object.ManagedAttribute;
import sailpoint.object.QueryOptions;
import sailpoint.object.SPRight;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.web.extjs.TreeNode;
import sailpoint.web.extjs.TreeNodeList;
import sailpoint.web.group.AccountGroupDTO;
import sailpoint.web.modeler.AccountGroupNodeBuilder;

/**
 * A resource for building the account group hierarchy tree
 *
 * @author <a href="mailto:bernie.margolis@sailpoint.com">Bernie Margolis</a>
 */
@Path("accountGroupHierarchy")
public class AccountGroupHierarchyResource extends BaseResource {
    private static Log log = LogFactory.getLog(AccountGroupHierarchyResource.class);
        
    @POST
    @Path("initialNode")
    public Map<String, Object> getInitialNode(MultivaluedMap<String, String> form) throws GeneralException {
    	
    	authorize(new RightAuthorizer(SPRight.ManagedAttributePropertyAdministrator));
    	
        String accountGroupId = form.getFirst("id");
        boolean editMode = Util.otob(form.getFirst("editMode"));
        // Note:  newName is only used when creating a new account group
        String newName = form.getFirst("name");
        Map<String, Object> initialTree = new HashMap<String, Object>();
        initialTree.put("id", "root");
        initialTree.put("text", "");
        initialTree.put("leaf", false);
        
        ManagedAttribute editedGroup = null;
        
        if (null != accountGroupId) {
            editedGroup = getContext().getObjectById(ManagedAttribute.class, accountGroupId);
        } 

        int numChildren = 0;
        
        if (editedGroup == null) {
            editedGroup = new ManagedAttribute();
            // jsl - revisit this, should we be setting the display 
            // name or the value?
            // bm - this is labeled displayName but in the new Managed Attribute world it's really the display value
            if (newName != null) {
                editedGroup.setDisplayName(newName);
            } else {
                editedGroup.setDisplayName("");
            }
        } else {
            QueryOptions includingGroupFinder = new QueryOptions(Filter.containsAll("inheritance", Arrays.asList(new ManagedAttribute [] {editedGroup})));
            numChildren = getContext().countObjects(ManagedAttribute.class, includingGroupFinder);
        }

        List<String> parentIds = Util.csvToList(form.getFirst("inheritance"));
        
        Map<String, Object> childParameters = new HashMap<String, Object>();
        Set<String> groupsToExpand = new HashSet<String>();
        if (parentIds != null && !parentIds.isEmpty()) {
            groupsToExpand.addAll(parentIds);            
        }
        childParameters.put(AccountGroupNodeBuilder.ID_PREFIX, "root:");
        childParameters.put(AccountGroupNodeBuilder.GROUPS_TO_EXPAND, groupsToExpand);
        childParameters.put(AccountGroupNodeBuilder.EDITED_NODE_ID, accountGroupId);
        childParameters.put(AccountGroupNodeBuilder.ATTACH_EDITED, true);
        childParameters.put(AccountGroupNodeBuilder.EDITED_NODE_NAME, newName);
        
        List<ManagedAttribute> candidateGroupsForChildNodes;

        if (parentIds != null && !parentIds.isEmpty()) {
            candidateGroupsForChildNodes = new ArrayList<ManagedAttribute>();
            for (String parentId : parentIds) {
                ManagedAttribute parentGroup = getContext().getObjectById(ManagedAttribute.class, parentId);
                candidateGroupsForChildNodes.add(parentGroup);
            }
        } else {
            candidateGroupsForChildNodes = new ArrayList<ManagedAttribute>();
        }
        
        List<Map<String, Object>> jsonMaps;
        
        if (candidateGroupsForChildNodes.isEmpty()) {
            boolean isLeaf = numChildren == 0;
            jsonMaps = new ArrayList<Map<String, Object>>();
            TreeNode groupNode = new TreeNode(accountGroupId, newName, AccountGroupNodeBuilder.ACCOUNT_GROUP_ICON_CLASS, isLeaf, false, childParameters);
            jsonMaps.add(groupNode.toJSONMap());
        } else {
            TreeNodeList<ManagedAttribute> nodes = new TreeNodeList<ManagedAttribute>(getContext(), candidateGroupsForChildNodes, new AccountGroupNodeBuilder(getContext()), childParameters);
            jsonMaps = nodes.toJSONMaps();
        }
        
        // Need to add the edited group to the leaves of the resulting tree
        initialTree.put("children", jsonMaps);
        
        return initialTree;
    }
    
    @POST
    @Path("childNode")
    public Map<String, Object> getChildNode(MultivaluedMap<String, String> form) throws GeneralException {
    	
    	authorize(new RightAuthorizer(SPRight.ManagedAttributePropertyAdministrator));
    	
        String nodeId = form.getFirst("nodeId");
        StringTokenizer tokenizer = new StringTokenizer(nodeId, ":");
        String accountGroupForNode = null;
        
        while (tokenizer.hasMoreTokens()) {
            accountGroupForNode = tokenizer.nextToken();
        }

        List<Map<String, Object>> children = new ArrayList<Map<String, Object>>();
        try {
            ManagedAttribute group = getContext().getObjectById(ManagedAttribute.class, accountGroupForNode);
            Iterator<ManagedAttribute> childrenGroups = getContext().search(ManagedAttribute.class, new QueryOptions(Filter.containsAll("inheritance", Arrays.asList(new ManagedAttribute[] {group}))));
            while (childrenGroups != null && childrenGroups.hasNext()) {
                ManagedAttribute childGroup = childrenGroups.next();
                children.add(getSingleChild(nodeId, childGroup));
            }
        } catch (Exception e) {
            log.error("Could not get children for the node with id " + nodeId, e);
            children.clear();
        }
        
        Map<String, Object> node = new HashMap<String, Object>();
        node.put("node", children);
        return node;
    }
    
    /*
     * Adds the Ids representing the "roots" of the hierarchy to the specified set and tracks the IDs belonging to the 
     * specified Account Group's ancestors in order to detect recursive hierarchies
     */
    private void getRootIds(Set<String> rootIds, Set<String> ancestorIds, String currentId, String groupId, List<ManagedAttribute> parents) {
        // If we detect a recursive hierarchy we consider this group to be a root.
        // Otherwise if this group has no parents we consider it to be a root
        if (ancestorIds.contains(groupId) || parents == null || parents.isEmpty()) {
            rootIds.add(currentId);
        } else {
            for (ManagedAttribute parent : parents) {
                ancestorIds.add(parent.getId());
                getRootIds(rootIds, ancestorIds, parent.getId(), groupId, parent.getInheritance());
            }
        }
    }
    
    /*
     * Get nodes for all ancestors starting from the specified roots going down to the group
     */
    private Map<String, Object> getAncestors(String idPrefix, String rootGroupId, Set<String> ancestorIds, String editedGroupId) throws GeneralException {
        Map<String, Object> node = new HashMap<String, Object>();
        ManagedAttribute group = getContext().getObjectById(ManagedAttribute.class, rootGroupId);
        String nodeId;
        if (idPrefix.length() > 0) {
            nodeId = idPrefix + ":" + group.getId();
        } else {
            nodeId = group.getId();
        }
        
        node.put("id", nodeId);
        node.put("text", group.getDisplayableName());
        Iterator<Object[]> childrenIds = getContext().search(ManagedAttribute.class, new QueryOptions(Filter.containsAll("inheritance", Arrays.asList(new ManagedAttribute[] {group}))), "id");
        boolean isLeaf = childrenIds == null || !childrenIds.hasNext();
        // Only fetch children when this node is not a leaf.  If we reach the edited group we want to stop there too
        if (!isLeaf && (!group.getId().equals(editedGroupId))) {
            List<Map<String, Object>> childNodes = new ArrayList<Map<String, Object>>();
            while (childrenIds != null && childrenIds.hasNext()) {
                String childId = (String) childrenIds.next()[0];
                if (ancestorIds.contains(childId)) {
                    childNodes.add(getAncestors(nodeId, childId, ancestorIds, editedGroupId));
                }
            }
            node.put("leaf", false);
            node.put("children", childNodes);
        } else if (group.getId().equals(editedGroupId)) {
            node.put("leaf", isLeaf);
        } else if (!group.getId().equals(editedGroupId)) {
            // Infer that this is an inherited group that was added manually
            node.put("leaf", false);
            List<Map<String, Object>> childNodes = new ArrayList<Map<String, Object>>();
            Map<String, Object> editedGroupNode = new HashMap<String, Object>();
            AccountGroupDTO editedGroup = getAccountGroupDTO(editedGroupId);
            editedGroupNode.put("id", nodeId + ":" + editedGroup.getId());
            editedGroupNode.put("name", editedGroup.getDisplayableValue(getContext()));
            editedGroupNode.put("leaf", editedGroup.getInheritingAccountGroups() == null || editedGroup.getInheritingAccountGroups().isEmpty());
            childNodes.add(editedGroupNode);
        }
        
        return node;
    }
    
    private Map<String, Object> getSingleChild(String nodePrefix, ManagedAttribute group) throws GeneralException {
        Map<String, Object> child = new HashMap<String, Object>();
        child.put("id", nodePrefix + ":" + group.getId());
        child.put("text", group.getDisplayableName());
        int numGrandChildren = getContext().countObjects(ManagedAttribute.class, new QueryOptions(Filter.containsAll("inheritance", Arrays.asList(new ManagedAttribute[] {group}))));
        child.put("leaf", numGrandChildren == 0);
        return child;
    }
    
    private AccountGroupDTO getAccountGroupDTO(String accountGroupId) {
        String sessionKey;
        if (accountGroupId == null || accountGroupId.trim().length() == 0) {
            sessionKey = ManagedAttribute.class.getName() + ":new_DTO";
        } else {
            sessionKey = ManagedAttribute.class.getName() + ":" + accountGroupId + "_DTO";                    
        }
        
        AccountGroupDTO dto = (AccountGroupDTO) getSession().getAttribute(sessionKey);
        
        return dto;
    }
}
