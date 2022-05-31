/* (c) Copyright 2014 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.extjs;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import sailpoint.api.SailPointContext;
import sailpoint.object.Application;
import sailpoint.object.Attributes;
import sailpoint.object.Schema;
import sailpoint.tools.GeneralException;
import sailpoint.tools.JsonHelper;

/**
 * This class is responsible for serialization of the ApplicationSchemaSelector hidden field 
 * hiddenSchemasId. The code has been refactored out of sailpoint.web.task.GroupSchemaCustomSerializer. 
 * See the method level javadoc on usage and what the Map and JSON String objects 
 * look like in order for serialization to properly occur.
 * @author chris.annino
 *
 */
public class ApplicationSchemaSerializer {

    
    /**
     * Val comes in as
     *    <Map>
     *         <entry key="ApplicationName">
     *             <value>
     *                 <List>
     *                     <String>schema1</String>
     *                     <String>schema2</String>
     *                 </List>
     *             </value>
     *         </entry>
     *     </Map>
     *
     *     We need to serialize this into JsonString such as
     *     [{
     *         id: 'xxxxxx',
     *         name: 'App1',
     *         schemas: [{id: 'xxx', name: 'xxx'}, ....]
     *     },{
     *         id: 'xxxxxx',
     *         name: 'App2',
     *         schemas: [{id: 'xxx', name: 'xxx'}, ....]
     *     }, ...]

     * @param val a map in the format described above
     *
     * @return serialized json as described above
     */
    public static String serialize(Object val, SailPointContext context) throws GeneralException {
        Map<String, Object> inputAttributes = (Map<String, Object>) val;
        if (inputAttributes == null) {
            return "[]";
        }

        List<Map<String, Object>> transformed = new ArrayList<Map<String, Object>>();

        for (String key : inputAttributes.keySet()) {
            Map<String, Object> oneItem = new HashMap<String, Object>();
            transformed.add(oneItem);
            Application application = context.getObjectByName(Application.class, key);
            oneItem.put("id", application.getId());
            oneItem.put("name", key);

            List<Map<String, Object>> schemasList = new ArrayList<Map<String, Object>>();
            oneItem.put("schemas", schemasList);
            List<String> schemaNames = (List<String>) inputAttributes.get(key);
            for (String schemaName : schemaNames) {
                Schema schema = application.getSchema(schemaName);
                Map<String, Object> schemaMap = new HashMap<String, Object>();
                schemasList.add(schemaMap);
                schemaMap.put("id", schema.getId());
                schemaMap.put("name", schema.getObjectType());
            }
        }

        return JsonHelper.toJson(transformed);
    }

    /**
     * Val comes in as the serialized string
     *     [{
     *         id: 'xxxxxx',
     *         name: 'App1',
     *         schemas: [{id: 'xxx', name: 'xxx'}, ....]
     *     },{
     *         id: 'xxxxxx',
     *         name: 'App2',
     *         schemas: [{id: 'xxx', name: 'xxx'}, ....]
     *     }, ...]
     *
     *     We need to deserialize this into the Attributes object like below
     *    <Map>
     *         <entry key="ApplicationName">
     *             <value>
     *                 <List>
     *                     <String>schema1</String>
     *                     <String>schema2</String>
     *                 </List>
     *             </value>
     *         </entry>
     *     </Map>

     * @param val a json string in the format described above
     *
     * @return the Attributes object as described above
     */
    public static Object deserialize(String val, SailPointContext context) throws GeneralException {
        if (val == null) {
            return new Attributes<String, Object>();
        }

        Attributes<String, Object> transformed = new Attributes<String, Object>();
        List<Map<String, Object>> items = JsonHelper.listOfMapsFromJson(String.class, Object.class, val);
        for (Map<String, Object> item : items) {
            List<String> schemaNames = new ArrayList<String>();
            List<Map<String, Object>> schemas = (List<Map<String, Object>>) item.get("schemas");
            for (Map<String, Object> schema : schemas) {
                schemaNames.add((String) schema.get("name"));
            }
            transformed.put((String) item.get("name"), schemaNames);
        }

        return transformed;
    }
}
