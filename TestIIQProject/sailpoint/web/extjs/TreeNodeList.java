/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.extjs;

import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONString;
import org.json.JSONWriter;

import sailpoint.api.SailPointContext;
import sailpoint.object.SailPointObject;

/**
 * Creates a list of TreeNodes given a List of SailPoint objects and their NodeTreeBuilder
 * @author bernie.margolis
 * @param <E> class that extends SailPointObject and comprises the list for which we are constructing nodes
 */
public class TreeNodeList<E extends SailPointObject> implements JSONString {
    private static final Log log = LogFactory.getLog(TreeNodeList.class);
    private List<TreeNode> treeNodes;

    public TreeNodeList(List<TreeNode> nodes) {
        this.treeNodes = nodes;
    }
    /**
     * This version of the TreeNodeList constructor assumes that the list of objects to convert
     * has been pre-trimmed.
     * @param objectsToConvert List of SailPoint objects that are being used to build the tree nodes
     * @param builder TreeNodeBuilder that knows how to construct nodes for the specified object type
     * @param isPagingUp true if a "page up" node needs to be generated; false otherwise
     * @param isPagingDown true if a "page down" node needs to be generated; false otherwise
     * @param currentPage The page that this node is currently displaying
     * @param pageSize The configured page size
     * @param additionalParameters additional parameters which may be required by the TreeNodeBuilder implementations
     */
    public TreeNodeList(List<E> objectsToConvert, TreeNodeBuilder<E> builder, boolean isPagingUp, boolean isPagingDown, int currentPage, int pageSize, Map<String, Object> additionalParameters) {
        initTreeNodes(objectsToConvert, builder, isPagingUp, isPagingDown, currentPage, pageSize, additionalParameters);
    }
    
    public TreeNodeList(SailPointContext ctx, List<E> objs, TreeNodeBuilder<E> builder, Map<String, Object> additionalParameters) {
        // Build the treeNodes
        this(ctx, objs, builder, additionalParameters, -1, -1);
        
    }
    
    public TreeNodeList(SailPointContext ctx, List<E> objs, TreeNodeBuilder<E> builder, Map<String, Object> additionalParameters, int currentPage, int pageSize) {
        // Build the treeNodes
        List<E> objectsToConvert;
        int numObjs = objs.size();
        boolean isPagingUp = false;
        boolean isPagingDown = false;
        if (pageSize > 0 && numObjs > pageSize) {
            // Find our place
            if (currentPage > 1) {
                isPagingUp = true;
            }
            int startIndex = (currentPage-1) * pageSize;
            int endIndex = (currentPage) * pageSize;

            if (endIndex > numObjs) {
                endIndex = numObjs;
            } else {
                isPagingDown = true;
            }
            
            objectsToConvert = objs.subList(startIndex, endIndex);
        } else {
            objectsToConvert = objs;
        }

        initTreeNodes(objectsToConvert, builder, isPagingUp, isPagingDown, currentPage, pageSize, additionalParameters);
    }
    
    public String toJSONString() {
        Writer jsonString = new StringWriter();
        JSONWriter writer = new JSONWriter(jsonString);
        try {
            writer.array();
            
            for (TreeNode treeNode : treeNodes) {
               writer.value(treeNode);
            }
            
            writer.endArray();
        } catch (JSONException e) {
            log.error(e);
        }
        
        return jsonString.toString();
    }
    
    public List<Map<String, Object>> toJSONMaps() {
        List<Map<String, Object>> jsonMaps = new ArrayList<Map<String, Object>>();
           
        for (TreeNode treeNode : treeNodes) {
           jsonMaps.add(treeNode.toJSONMap());
        }
                    
        return jsonMaps;
    }
    
    /**
     * This method was written to work around an exploit that allows third party javascript on some browsers 
     * to inspect objects contained in arrays.  We wrap our array in an object to avoid vulnerability to this
     * exploit.
     * @param key The key to use for the root node
     * @return String representation of this node wrapped in an object with a single property named by the key parameter
     */
    public String toWrappedJSONString(String key) {
        Writer jsonString = new StringWriter();
        JSONWriter writer = new JSONWriter(jsonString);
        try {
            writer.object();
            writer.key(key);
            JSONArray nodes = new JSONArray(treeNodes);
            writer.value(nodes);
            writer.endObject();
        } catch (JSONException e) {
            log.error(e);
        }
        
        return jsonString.toString();
    }
    
    /**
     * This method was written to work around an exploit that allows third party javascript on some browsers 
     * to inspect objects contained in arrays.  We wrap our array in an object to avoid vulnerability to this
     * exploit.
     * @return String representation of this node wrapped in an object with a single property called 'node'.
     */
    public String toWrappedJSONString() {
    	return toWrappedJSONString("node");
    }
    
    /**
     * This method was written to work around an exploit that allows third party javascript on some browsers 
     * to inspect objects contained in arrays.  We wrap our array in an object to avoid vulnerability to this
     * exploit.
     * @return Map<String, Object> containing a single Object whose sole proprety is the array of JSON objects represented by this list 
     */
    public Map<String, Object> toWrappedJsonMap() {
        Map<String, Object> wrapper = new HashMap<String, Object>();
        wrapper.put("node", toJSONMaps());
        return wrapper;
    }
    
    private void initTreeNodes(List<E> objectsToConvert, TreeNodeBuilder<E> builder, boolean isPagingUp, boolean isPagingDown, int currentPage, int pageSize, Map<String, Object> additionalParameters) {
        treeNodes = new ArrayList<TreeNode>();
        
        if (isPagingUp) {
            treeNodes.add(NodeFactory.getPageUpNode(currentPage, pageSize));
        }
        
        for (E spObject : objectsToConvert) {
            treeNodes.add(builder.buildNode(spObject, additionalParameters, pageSize));
        }

        if (isPagingDown) {
            treeNodes.add(NodeFactory.getPageDownNode(currentPage, pageSize));
        }
    }
}
