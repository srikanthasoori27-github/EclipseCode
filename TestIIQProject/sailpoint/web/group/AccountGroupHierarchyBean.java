package sailpoint.web.group;

import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONException;
import org.json.JSONWriter;

import sailpoint.object.Filter;
import sailpoint.object.ManagedAttribute;
import sailpoint.object.QueryOptions;
import sailpoint.tools.GeneralException;
import sailpoint.web.BaseBean;

/**
 * 
 * @author bernie.margolis
 */
public class AccountGroupHierarchyBean extends BaseBean {
    private static Log log = LogFactory.getLog(AccountGroupHierarchyBean.class);

    public String getNode() {
        String id = (String) getRequestParam().get("id");
        String nodeId = (String) getRequestParam().get("nodeId");
        StringTokenizer tokenizer = new StringTokenizer(nodeId, ":");
        String accountGroupForNode = null;
        
        while (tokenizer.hasMoreTokens()) {
            accountGroupForNode = tokenizer.nextToken();
        }
        
        List<Map<String, Object>> childNodes = new ArrayList<Map<String, Object>>();
        
        if (accountGroupForNode != null) {
            try {
                List<ManagedAttribute> children = getContext().getObjects(ManagedAttribute.class, 
                        new QueryOptions(Filter.containsAll("inheritance", Arrays.asList(new String [] {accountGroupForNode}))));
                if (children != null && !children.isEmpty()) {
                    for (ManagedAttribute child : children) {
                        int numGrandchildren = getContext().countObjects(ManagedAttribute.class, 
                                new QueryOptions(Filter.containsAll("inheritance", Arrays.asList(new String [] {child.getId()}))));
                        boolean leaf = (numGrandchildren == 0);
                        String text = child.getDisplayableName();
                        String childId = nodeId + ":" + child.getId();
                        Map<String, Object> childNode = new HashMap<String, Object>();
                        childNode.put("id", childId);
                        childNode.put("text", text);
                        childNode.put("leaf", leaf);
                        childNode.put("expandable", !leaf);
                        childNode.put("editable", false);
                        childNodes.add(childNode);
                    }
                }
            } catch (GeneralException e) {
                log.error("Failed to find the account group corresponding to the node with id " + nodeId, e);
            }
        }
        
        final Writer jsonString = new StringWriter();
        final JSONWriter jsonWriter = new JSONWriter(jsonString);
        
        String result;
        try {
            jsonWriter.object();
            jsonWriter.key("node");
            jsonWriter.value(childNodes);
            jsonWriter.endObject();
            result = jsonString.toString();
        } catch (JSONException e) {
            log.error("Failed to write JSON for the account group corresponding to the node with id " + nodeId, e);
            result = "{children: []}";
        }
        
        return result;
    }
    
    /*
     */
}
