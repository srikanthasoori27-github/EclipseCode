/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.modeler;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.SailPointContext;
import sailpoint.object.Filter;
import sailpoint.object.ManagedAttribute;
import sailpoint.object.QueryOptions;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.web.extjs.TreeNode;
import sailpoint.web.extjs.TreeNodeBuilder;
import sailpoint.web.extjs.TreeNodeList;
import sailpoint.web.group.AccountGroupEditBean;
import sailpoint.web.util.WebUtil;

public class AccountGroupNodeBuilder implements TreeNodeBuilder<ManagedAttribute> {
    private static final Log log = LogFactory.getLog(AccountGroupNodeBuilder.class);
    private SailPointContext context;
    
    public static final String GROUPS_TO_EXPAND = "groupsToExpand";
    public static final String EDITED_NODE_ID = "editedNodeId";
    public static final String EDITED_NODE_NAME = "editedNodeName";
    public static final String ATTACH_EDITED = "attachEdited";
    public static final String ID_PREFIX = "idPrefix";
    public static final String ACCOUNT_GROUP_ICON_CLASS = "accountGroupIcon";
    
    /**
     * @param context SailPointContext under which to build the tree 
     */
    public AccountGroupNodeBuilder(SailPointContext context) {
        this.context = context;
    }

    @SuppressWarnings("unchecked")
    public TreeNode buildNode(ManagedAttribute group, Map<String, Object> additionalParameters, int pageSize) {
        Set<String> groupsToExpand = (Set<String>) additionalParameters.get(GROUPS_TO_EXPAND);
        String editedNodeId = (String) additionalParameters.get(EDITED_NODE_ID);
        String groupId = (group == null || group.getId() == null) ? "new" : group.getId();
        String groupName;
        if (group == null) {
            groupName = (String)additionalParameters.get(EDITED_NODE_NAME);
        } else {
            groupName = group.getDisplayableName();
            if (groupName == null) {
                groupName = group.getDisplayName();
            }
            if (groupName == null) {
                groupName = group.getValue();
            }
        }

        if (groupName == null) {
            groupName = "";
        }
        
        boolean isExpanded = groupsToExpand != null  && groupsToExpand.contains(groupId);
        if (editedNodeId != null) {
            isExpanded |= editedNodeId.equals(groupId);
        }
        
        boolean isAttachEdited = Util.otob(additionalParameters.get(ATTACH_EDITED));
        String idPrefix = (String) additionalParameters.get(ID_PREFIX);
        String nodeId = idPrefix + groupId;

        List<ManagedAttribute> groupsForChildNodes = new ArrayList<ManagedAttribute>();
        try {
            if (isAttachEdited) {
                groupsForChildNodes.add(context.getObjectById(ManagedAttribute.class, editedNodeId));
            } else {
                QueryOptions includingGroupFinder = new QueryOptions(Filter.containsAll("inheritance", Arrays.asList(new ManagedAttribute [] {group})));
                List<ManagedAttribute> candidateGroupsForChildNodes = context.getObjects(ManagedAttribute.class, includingGroupFinder);
                groupsForChildNodes.addAll(candidateGroupsForChildNodes);
            }
        } catch (GeneralException e) {
            log.error("Failed to find children for Account Group " + groupName + " with id " + group.getId(), e);
        }
        
        TreeNode accountGroupNode = new TreeNode(nodeId, WebUtil.escapeHTML(groupName, false), ACCOUNT_GROUP_ICON_CLASS, groupsForChildNodes == null || groupsForChildNodes.isEmpty(), isExpanded, null);
        if (isExpanded) {            
            if (groupsForChildNodes != null && !groupsForChildNodes.isEmpty()) {
                // Need to create a new Map here so as to not impact the siblings' IDs
                Map<String, Object> childParameters = new HashMap<String, Object>();
                childParameters.put(ID_PREFIX, nodeId + ":");
                childParameters.put(GROUPS_TO_EXPAND, groupsToExpand);
                childParameters.put(EDITED_NODE_ID, editedNodeId);
                childParameters.put(EDITED_NODE_NAME, additionalParameters.get(EDITED_NODE_NAME));
                
                Collections.sort(groupsForChildNodes, AccountGroupEditBean.GROUP_COMPARATOR);
                                
                TreeNodeList<ManagedAttribute> children = new TreeNodeList<ManagedAttribute>(context, groupsForChildNodes, this, childParameters, 1, pageSize); 
                accountGroupNode.addChildren(children);
            }
        }
        
        return accountGroupNode;
    }    
}
