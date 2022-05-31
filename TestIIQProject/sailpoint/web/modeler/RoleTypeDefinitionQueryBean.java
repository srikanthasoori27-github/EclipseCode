/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.modeler;

import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONWriter;

import sailpoint.object.Bundle;
import sailpoint.object.ObjectConfig;
import sailpoint.object.RoleTypeDefinition;

public class RoleTypeDefinitionQueryBean {
    private static final Log log = LogFactory.getLog(RoleTypeDefinitionQueryBean.class);
    
    public String getRoleTypeDefinitionsAsJson() {
        final Writer jsonString = new StringWriter();
        final JSONWriter jsonWriter = new JSONWriter(jsonString);

        try {
            ObjectConfig roleConfig = ObjectConfig.getObjectConfig(Bundle.class);
            Collection<RoleTypeDefinition> typeDefs = roleConfig.getRoleTypesList();

            // Start definitions object
            jsonWriter.object();
            jsonWriter.key("definitions");
            List<JSONObject> typeDefList = new ArrayList<JSONObject>();
            
            if (typeDefs != null && !typeDefs.isEmpty()) {
                for (RoleTypeDefinition typeDef : typeDefs) {
                    // Start a RoleTypeDefinition object
                    Map<String, Object> typeDefJsonObj = new HashMap<String, Object>();
                    
                    typeDefJsonObj.put("name", typeDef.getName());
                    typeDefJsonObj.put("displayName", typeDef.getDisplayName());
                    typeDefJsonObj.put("icon", typeDef.getIcon());
                    typeDefJsonObj.put("noAssignmentSelector", typeDef.isNoAssignmentSelector());
                    typeDefJsonObj.put("noAutoAssignment", typeDef.isNoAutoAssignment());
                    typeDefJsonObj.put("noManualAssignment", typeDef.isNoManualAssignment());
                    typeDefJsonObj.put("noDetection", typeDef.isNoDetection());
                    typeDefJsonObj.put("noPermits", typeDef.isNoPermits());
                    typeDefJsonObj.put("noProfiles", typeDef.isNoProfiles());
                    typeDefJsonObj.put("noRequirements", typeDef.isNoRequirements());
                    typeDefJsonObj.put("noSubs", typeDef.isNoSubs());
                    typeDefJsonObj.put("noSupers", typeDef.isNoSupers());
                    typeDefJsonObj.put("notPermittable", typeDef.isNotPermittable());
                    typeDefJsonObj.put("notRequired", typeDef.isNotRequired());
                    typeDefJsonObj.put("noIIQ", typeDef.isNoIIQ());
                    
                    // End a RoleTypeDefinition object
                    typeDefList.add(new JSONObject(typeDefJsonObj));
                }
            }
            jsonWriter.value(new JSONArray(typeDefList));
            
            jsonWriter.key("numDefinitions");
            int numDefinitions = typeDefs == null ? 0 : typeDefs.size();        
            jsonWriter.value(numDefinitions);
            
            jsonWriter.endObject();
            // End definitions object
        } catch (JSONException e) {
            log.error("Role type definitions aren't available right now.", e);
        }
        
        return jsonString.toString();

    }
}
