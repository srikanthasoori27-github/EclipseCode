/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.extjs;

import java.util.HashMap;
import java.util.Map;


/**
 * A convenience class for creating common TreeNodes.  For more specific TreeNodes,
 * Tree implementors should implement the TreeNodeBuilder interface
 * @author bernie.margolis
 */
public class NodeFactory {
    private NodeFactory() {}
    
    public static TreeNode getPageUpNode(int currentPage, int pageSize) {
        Map<String, Object> extensions = new HashMap<String, Object>();
        extensions.put("pageNode", "up");
        extensions.put("currentPage", currentPage);
        return new TreeNode(null, "Previous " + pageSize, "pageUpNodeIcon", true, false, extensions, false, false);
    }
    
    public static TreeNode getPageDownNode(int currentPage, int pageSize) {
        Map<String, Object> extensions = new HashMap<String, Object>();
        extensions.put("pageNode", "down");
        extensions.put("currentPage", currentPage);
        return new TreeNode(null, "Next " + pageSize, "pageDownNodeIcon", true, false, extensions, false, false);
    }
}
