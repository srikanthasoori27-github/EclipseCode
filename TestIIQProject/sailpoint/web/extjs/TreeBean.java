/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.extjs;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONException;

import sailpoint.object.QueryOptions;
import sailpoint.object.SailPointObject;
import sailpoint.tools.GeneralException;
import sailpoint.web.BaseBean;
import sailpoint.web.util.WebUtil;

import java.util.List;
import java.util.Map;


public abstract class TreeBean<E extends SailPointObject> extends BaseBean {
    private static final Log log = LogFactory.getLog(TreeBean.class);

    public static final String ROOT_ID = "root";


    private int pageSize;
    private Class<E> _scope;

    public TreeBean(Class<E> scope) throws GeneralException {
        super();
        _scope = scope;
        pageSize = getPageSize();
        
        if (pageSize <= 0) {
            pageSize = 25;
        }
    }
    
    /**
     * Return the page size for this tree, or less than or equal to zero to use
     * the default page size.
     */
    protected abstract int getPageSize() throws GeneralException;

    /**
     * @return QueryOptions object that contains the Filter which will
     *         give us the current set of nodes that we want to fetch
     */
    protected abstract QueryOptions getQueryOptionsForChildren(E object)
        throws GeneralException;
    
    protected String getIconClass(E object) {
        return null;
    }

    protected String getName(E object) {
        return object.getName();
    }
    
    /**
     * A generic TreeNodeBuilder object with minimal functionality is 
     * built in.  Provide a custom object if advanced functionality is required
     * @return TreeNodeBuilder object that will build the current TreeNode
     *         to desired specifications
     */
    protected TreeNodeBuilder<E> getTreeNodeBuilder() {
        return new TreeNodeBuilder<E>() {
            public TreeNode buildNode(E obj, Map<String, Object> additionalParameters, int pageSize) {
                String id = obj.getId();
                String text = WebUtil.safeHTML(getName(obj));
                int numChildren;
                
                try {
                    numChildren = countChildrenForObject(obj, getQueryOptionsForChildren(obj));
                } catch (GeneralException e) {
                    log.error(e);
                    numChildren = 0;
                }
                
                TreeNode node = new TreeNode(id, text, getIconClass(obj), numChildren == 0, false, null);
                return node;
            }
        };
    }
    

    /**
     * Additional parameters which may need to be passed to the TreeNodeBuilder
     * @return
     */
    protected Map<String, Object> getAdditionalParameters() {
        return null;
    }

    /**
     * Get the ID of the loading node, or null if this is the root node.
     */
    public String getLoadingNodeId() {
        return getNodeId(getRequestParameter("node"));
    }

    /**
     * Return the ID of the given node, or null if this is the root node.
     */
    protected String getNodeId(String nodeId) {

        String objId = null;

        if (!ROOT_ID.equals(nodeId)) {
            int startIndex = nodeId.lastIndexOf(':') + 1;
            objId = nodeId.substring(startIndex);
        }

        return objId;
    }
    
    public String getNodes() throws JSONException, GeneralException {        
        E parentObject = null;
        
        if (null != getLoadingNodeId()) {
            parentObject = getContext().getObjectById(_scope, getLoadingNodeId());
        }

        // Figure out if and/or how we want to page this
        boolean isPagingUp = false;
        boolean isPagingDown = false;
        
        int currentPage = getCurrentPage();
        if (currentPage > 1) {
            isPagingUp = true;
        }

        QueryOptions optionsForChildren = getQueryOptionsForChildren(parentObject);
        int totalNodeCount = countChildrenForObject(parentObject, optionsForChildren);
        int firstRow = ((currentPage - 1) * pageSize);
        if (firstRow + pageSize < totalNodeCount) {
            isPagingDown = true;
        }
        
        List<E> children = getChildrenForObject(parentObject, firstRow, optionsForChildren);

        TreeNodeList<E> nodes = new TreeNodeList<E>(children, getTreeNodeBuilder(), isPagingUp, isPagingDown, currentPage, pageSize, getAdditionalParameters());
        
        return nodes.toWrappedJSONString();
    }
    
    private List<E> getChildrenForObject(E spObject, int firstRow, QueryOptions optionsForChildren) throws GeneralException {
        
        // Limit the query to the nodes that we are generating
        optionsForChildren.setFirstRow(firstRow);
        optionsForChildren.setResultLimit(pageSize);
        List<E> children = getContext().getObjects(_scope, optionsForChildren);
        
        return children;
    }
    
    private int countChildrenForObject(E spObject, QueryOptions optionsForChildren) throws GeneralException {
        int totalNodeCount = getContext().countObjects(_scope, optionsForChildren);
        return totalNodeCount;
    }
    
    private int getCurrentPage() {
        int currentPage;
        
        String currentPageVal = getRequestParameter("currentPage");
        log.debug("Current page is " + currentPageVal);
        if (currentPageVal == null) {
            currentPage = 1;
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
}
