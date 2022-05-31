/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.extjs;

import java.io.StringWriter;
import java.io.Writer;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONException;
import org.json.JSONString;
import org.json.JSONWriter;

public class TreeNode implements JSONString {
    private static final Log log = LogFactory.getLog(TreeNode.class);
    private String id;
    private String text;
    private String iconCls;
    private boolean leaf;
    private boolean expanded;
    private TreeNodeList children;
    private Map<String, Object> extensions;
    //Default to true as this is the ExtJS behavior
    private boolean allowDrag = true;
    private boolean allowDrop = true;

    public TreeNode(String id, String text, String iconCls, boolean leaf){
        this.id = id;
        this.text = text;
        this.iconCls = iconCls;
        this.leaf = leaf;
    }

    public TreeNode(String id, String text, String iconCls, boolean leaf, boolean expanded, Map<String, Object> extensions) {
        this(id, text, iconCls, leaf);
        this.expanded = expanded;
        this.extensions = extensions;
        this.children = null;
    }
    
    public TreeNode(String id, String text, String iconCls, boolean leaf, boolean expanded, Map<String, Object> extensions, boolean allowDrag, boolean allowDrop) {
        this(id, text, iconCls, leaf, expanded, extensions);
        this.allowDrag = allowDrag;
        this.allowDrop = allowDrop;
    }
    public void addChildren(TreeNodeList childrenToAdd) {
        this.children = childrenToAdd;
    }

    public boolean isAllowDrag() {
        return allowDrag;
    }

    public void setAllowDrag(boolean allowDrag) {
        this.allowDrag = allowDrag;
    }

    public boolean isAllowDrop() {
        return allowDrop;
    }

    public void setAllowDrop(boolean allowDrop) {
        this.allowDrop = allowDrop;
    }

    public void setExtensions(Map<String,Object> extensions) {
        this.extensions = extensions;
    }

    public String toJSONString() {
        Writer jsonString = new StringWriter();
        JSONWriter writer = new JSONWriter(jsonString);
        try {
            writer.object();
            
            if (id != null) {
                writer.key("id");
                writer.value(id);
            }
            
            writer.key("text");
            writer.value(text);
            
            if (iconCls != null) {
                writer.key("iconCls");
                writer.value(iconCls);
            }
            
            writer.key("leaf");
            writer.value(leaf);
            
            writer.key("expanded");
            writer.value(expanded);
            
            writer.key("allowDrag");
            writer.value(this.allowDrag);
            
            writer.key("allowDrop");
            writer.value(this.allowDrop);
            
            if (children != null) {
                writer.key("children");
                writer.value(children);
            }
            
            if (extensions != null && !extensions.isEmpty()) {
                for (String extensionProperty : extensions.keySet()) {
                    writer.key(extensionProperty);
                    writer.value(extensions.get(extensionProperty));
                }
            }
            
            writer.endObject();
        } catch (JSONException e) {
            log.error("Failed to write JSON for a tree node with ID " + id);
        }
        
        return jsonString.toString();
    }
    
    public Map<String, Object> toJSONMap() {
        Map<String, Object> jsonMap = new HashMap<String, Object>();
       
        if (id != null) {
            jsonMap.put("id", id);
        }
        
        jsonMap.put("text", text);
        
        if (iconCls != null) {
            jsonMap.put("iconCls", iconCls);
        }
        
        jsonMap.put("leaf", leaf);
        jsonMap.put("expanded", expanded);
        
        jsonMap.put("allowDrag", this.allowDrag);
        jsonMap.put("allowDrop", this.allowDrop);
        
        if (children != null) {
            jsonMap.put("children", children.toJSONMaps());
        }
        
        if (extensions != null && !extensions.isEmpty()) {
            for (String extensionProperty : extensions.keySet()) {
                jsonMap.put(extensionProperty, extensionProperty);
            }
        }
                
        return jsonMap;
    }

}
