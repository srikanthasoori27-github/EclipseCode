/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 *
 */
package sailpoint.web.certification;


import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.faces.context.FacesContext;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.myfaces.custom.tree2.TreeModel;
import org.apache.myfaces.custom.tree2.TreeModelBase;
import org.apache.myfaces.custom.tree2.TreeNode;
import org.apache.myfaces.custom.tree2.TreeNodeBase;
import org.apache.myfaces.custom.tree2.TreeState;
import org.apache.myfaces.custom.tree2.TreeStateBase;

import sailpoint.api.SailPointContext;
import sailpoint.api.SailPointFactory;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.QueryOptions;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.web.BaseBean;
import sailpoint.web.BaseObjectBean;
import sailpoint.web.messages.MessageKeys;

/**
 *
 */
public class ManagerTreeBean extends BaseBean implements Serializable{
    private static final long serialVersionUID = 4070353642459444408L;

    private static Log log = LogFactory.getLog(ManagerTreeBean.class);

    public final static String MANAGER_TREE_STATE = "managerTreeState";
    public final static String MANAGER_TREE_VISIBLE = "managerTreeVisible";
    public final static String MANAGER_TREE_MODEL = "managerTreeModel";
    public final static String CERT_REPORT_CURRENT_MANAGER = "certReportCurrentManager";

    private String _currentManagerId;

    private ManagerTreeModel _treeModel;
    private TreeState _treeState;
    private boolean _treeVisible;

    private boolean _hasError;

    /**
     *
     */
    public ManagerTreeBean() {
        super();
        restore();
    }  // ModelerBean()
    
    private void restore() {
        Object forceLoad = getSessionScope().get(BaseObjectBean.FORCE_LOAD);
        
        if (forceLoad != null && (Boolean)forceLoad) {
            getSessionScope().clear();
        }
        
        _currentManagerId = (String) getSessionScope().get(CERT_REPORT_CURRENT_MANAGER);
        // Update the tree model and tree state
        updateTreeModel();
        updateTreeState();
        
        // If we are editing an object, then set the tree state to the
        // last known state.  If we are not editing an object, then make
        // the tree visible.
        if ( _currentManagerId != null && _currentManagerId.trim().length() > 0) {
            Object treeVisible = getSessionScope().get(MANAGER_TREE_VISIBLE);
            if ( treeVisible != null && treeVisible instanceof Boolean ) {
                _treeVisible = ((Boolean)treeVisible).booleanValue();
            } else {
                _treeVisible = true;
            }
        } else {
            setTreeVisible(true);
        }        
    }

    /**
     *
     * @return
     */
    public boolean isTreeVisible() {
        return _treeVisible;
    }

    /**
     *
     * @param treeVisible
     */
    @SuppressWarnings("unchecked")
    public void setTreeVisible(boolean treeVisible) {
        _treeVisible = treeVisible;
        getSessionScope().put(MANAGER_TREE_VISIBLE, new Boolean(_treeVisible));
    }

    /**
     * @return the _hasError
     */
    public boolean isHasError() {
        return _hasError;
    }

    /**
     * @param error the _hasError to set
     */
    public void setHasError(boolean error) {
        _hasError = error;
    }

    public String getCurrentManagerId() {
        return _currentManagerId;
    }

    @SuppressWarnings("unchecked")
    public void setCurrentManagerId(String managerId) {
        _currentManagerId = managerId;
    }

    /**
     *
     * @return
     * @throws GeneralException
     */
    @SuppressWarnings("unchecked")
    public TreeModel getTreeData() throws GeneralException {
        return _treeModel;
    }  // getTreeModel()
    
    @SuppressWarnings("unchecked")
    private void updateTreeModel() {
        _treeModel = (ManagerTreeModel) getSessionScope().get(MANAGER_TREE_MODEL);

        if (_treeModel == null) {
            // Every tree needs a root.  It would be great if ours was not
            // displayed, but a bug in how JSF processes actions when the
            // root node is not displayed causes us to be forced to show it
            TreeNode treeData = new TreeNodeBase("root", "Managers", "managerRootId", false);
            _treeModel = new ManagerTreeModel(treeData);
            getSessionScope().put(MANAGER_TREE_MODEL, _treeModel);
        }
        
        final String currentManagerId = getCurrentManagerId(); 
        
        try {
            if (currentManagerId != null && currentManagerId.trim().length() > 0) {
                // Expand this manager and 
                //   create a node for its children
                //   create children of the manager node
                ManagerTreeNode currentNode = (ManagerTreeNode) _treeModel.getNodeByIdentifier(getCurrentManagerId());
                if (currentNode != null) {
                    expandManagerNode(currentNode);
                }
            } else {
                TreeNode rootNode = _treeModel.getNodeByIdentifier("managerRootId");

                if (rootNode.getChildCount() == 0) {
                    // Create top-level nodes and add them to the root
                    QueryOptions ops = new QueryOptions().add(new Filter[] {Filter.isnull("manager"), Filter.eq("managerStatus", true)});
                    List<Identity> topLevelManagers = getContext().getObjects(Identity.class, ops);

                    for (Identity manager : topLevelManagers) {
                        boolean isNodeExpandable = hasManagerSubordinates(manager);
                        ManagerTreeNode node = 
                            new ManagerTreeNode(
                                manager.getLastname() + ", " + manager.getFirstname(), 
                                manager.getId(), 
                                !isNodeExpandable, 
                                null);
                        rootNode.getChildren().add(node);
                        _treeModel.addNode(node);
                        
                        if(isNodeExpandable) {
                            expandManagerNode(node);
                        }
                    }
                    
                } 
            }
        } catch (GeneralException e) {
            Message errMsg = (new Message(Message.Type.Error,
                    MessageKeys.ERR_TREE_MODEL_UPDATE_FAILED));            
            log.error(errMsg.getMessage(), e);
            addMessage(errMsg, null);
            setHasError(true);
        }
    }
    
    @SuppressWarnings("unchecked")
    private void updateTreeState() {
        // Look for our tree state on the HTTP session.  If it is not
        // there, then create a new state, initialize it, and place it
        // on the session
        if ( _treeState == null ) {
            _treeState = (TreeState)getSessionScope().get(MANAGER_TREE_STATE);
            if ( _treeState == null ) {
                _treeState = new ManagerTreeState();
                _treeState.setTransient(true);
                String rootPath[] = { "0" };
                _treeState.expandPath(rootPath);
                getSessionScope().put(MANAGER_TREE_STATE, _treeState);
            }
        }

        _treeModel.setTreeState(_treeState);
    }
    
    @SuppressWarnings("unchecked")
    private void expandManagerNode(ManagerTreeNode node) throws GeneralException {
        if (!node.isExpanded()) {
            node.setExpanded(true);
            
            // This may conceivably be called by our inner classes across requests, so we need
            // to get the current context (the getContext() context may be obsolet when this is called)
            SailPointContext ctx = SailPointFactory.getCurrentContext();
            Identity currentManager = ctx.getObjectById(Identity.class, node.getIdentifier());
            QueryOptions qo = new QueryOptions().add(new Filter [] { Filter.eq("manager", currentManager), Filter.eq("managerStatus", true)});
            List<Identity> subordinateManagers = ctx.getObjects(Identity.class, qo);

            for (Identity subordinate : subordinateManagers) {
                ManagerTreeNode childNode = 
                    new ManagerTreeNode(
                        subordinate.getLastname() + ", " + subordinate.getFirstname(), 
                        subordinate.getId(),
                        !hasManagerSubordinates(subordinate),
                        node.getIdentifier());
                node.getChildren().add(childNode);
                _treeModel.addNode(childNode);
            }
        } 
    }
    
    // Test whether the specified user has subordinates or not
    private boolean hasManagerSubordinates(Identity user) throws GeneralException {
        QueryOptions qo = new QueryOptions().add(new Filter [] { Filter.eq("manager", user), Filter.eq("managerStatus", true)});
        final int numSubordinates = getContext().countObjects(Identity.class, qo);
        return numSubordinates > 0;
    }

    /**
     *
     * @param id
     */
    public void setSelectedNode(String id) {
        if ( _treeModel == null ) return;

        // "0" is a convention for the root node
        TreeNode node = _treeModel.getNodeById("0");
        setSelectedNode(node, id);
        if (id == null || id.trim().length() == 0 || "managerRootId".equals(id)) {
            getAjaxSafeSessionScope().remove(CERT_REPORT_CURRENT_MANAGER);
        }
    }  // setSelectedNode(String)

    @SuppressWarnings("unchecked")
    public void viewManagerAction() {
        String viewedManager = (String) getRequestParam().get("dashboardForm:currentManagerId");
        getSessionScope().put(CERT_REPORT_CURRENT_MANAGER, viewedManager);
        setSelectedNode(viewedManager);
    }
    
    /**
     * Recursive function to walk a tree of TreeNode's selecting a node with
     * the specified id and deselecting the other nodes.
     *
     * @param node
     * @param id
     */
    @SuppressWarnings("unchecked")
    private void setSelectedNode(TreeNode node, String id) {
        if ( node instanceof ManagerTreeNode ) {
            if ( node.getIdentifier().equals(id) ) {
                ((ManagerTreeNode)node).setSelected(true);
                setCurrentManagerId(id);
                getAjaxSafeSessionScope().put(CERT_REPORT_CURRENT_MANAGER, id);
            } else {
                ((ManagerTreeNode)node).setSelected(false);
            }
        }

        List children = node.getChildren();
        if ( children != null ) {
            for ( Object child : children ) {
                if ( child instanceof TreeNode ) {
                    setSelectedNode((TreeNode)child, id);
                }
            }
        }
    }  // setSelectedNode(TreeNode, String)
    
    /*
     * This is an alternative to the BaseBean.getSessionScope() method.  Under normal
     * circumstances that method is superior, but when we make ajax requests, the returned
     * scope may be cached from a context that is no longer valid.  This method gets a 
     * fresh session scope out of the current context every time. 
     */
    private Map getAjaxSafeSessionScope() {
        return FacesContext.getCurrentInstance().getExternalContext().getSessionMap();
    }

    //  Inner classes


    /**
     * 
     */
    public class ManagerTreeNode extends TreeNodeBase {
        private static final long serialVersionUID = 3176336720522301916L;
        private boolean _selected;
        private boolean _expanded;
        public boolean isSelected() { return _selected; }
        public void setSelected(boolean selected) { _selected = selected; }
        public boolean isExpanded() { return _expanded; }
        public void setExpanded(boolean expanded) { _expanded = expanded; }
        
        public ManagerTreeNode(String description, String identifier, boolean leaf, String parentId) {
            super("mgr", description, identifier, leaf);
            _expanded = false;
        }
    }  // class SelectableTreeNode

    /**
     * TreeModel has a getNodeById, but it's keyed off path information that is maintained
     * by the HTMLTree.  Since we have no insight into that mode of identification, we have
     * to create and maintain our own node map.  This class helps us do that
     */
    private class ManagerTreeModel extends TreeModelBase {
        private static final long serialVersionUID = -5149804731786735189L;
        private Map<String, TreeNode> nodeMap;
        
        private ManagerTreeModel(TreeNode rootNode) {
            super(rootNode);
            nodeMap = new HashMap<String, TreeNode>();
            nodeMap.put(rootNode.getIdentifier(), rootNode);
        }
        
        /**
         * TreeModel has a getNodeById, but it's keyed off path information that is maintained
         * by the HTMLTree.  Since we have no insight into that mode of identification, we have
         * to create and maintain our own node map.
         * @param identifier The identifier that we assigned to the node
         * @return The TreeNode with the given identifier
         */
        private TreeNode getNodeByIdentifier(String identifier) {
            return nodeMap.get(identifier);
        }
        
        /**
         * The TreeModel only tracks nodes by their ancestry.  We want to be able
         * to grab them directly if needed, so we are also keeping them in a Map.
         * This method provides a means of adding nodes to this map.
         * @param node Node that we want to add to the map
         */
        private void addNode(ManagerTreeNode node) {
            nodeMap.put(node.getIdentifier(), node);
        }
    }
    
    private class ManagerTreeState extends TreeStateBase {
        private static final long serialVersionUID = 3991013637726926293L;

        @Override
        public void setSelected(String nodeId) {
            super.setSelected(nodeId);
            TreeNode node = _treeModel.getNodeById(nodeId);
            if (node != null) {
                setSelectedNode(node.getIdentifier());
            }
        }

        @SuppressWarnings("unchecked")
        @Override
        public void toggleExpanded(String nodeId) {
            super.toggleExpanded(nodeId);
            TreeNode node = _treeModel.getNodeById(nodeId);
            if (isNodeExpanded(nodeId)) {
                if (node instanceof ManagerTreeNode) {
                    try {
                        expandManagerNode((ManagerTreeNode) node);
                        List<TreeNode> children = node.getChildren();
                        for (TreeNode child : children) {
                            expandManagerNode((ManagerTreeNode) child);
                        }
                    } catch (GeneralException e) {
                        log.error("Failed to expand a manager node.", e);
                    }
                }
            }
            
            // Select the node that's being expanded
            setSelected(nodeId);
        }
    }

}  // class ModelerBean
