/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.extjs;

import org.json.JSONException;
import org.json.JSONWriter;

import java.util.Map;
import java.util.HashMap;
import java.io.Writer;
import java.io.StringWriter;

/**
 * @author <a href="mailto:jonathan.bryant@sailpoint.com">Jonathan Bryant</a>
 */
public class GenericJSONObject implements JSONSerializable {

    private Map<String, Object> properties = new HashMap<String, Object>();


    public GenericJSONObject() {
    }

    public void set(String name, Object value){
        properties.put(name, value);
    }

    public Object get(String name){
        if (properties.containsKey(name))
            return properties.get(name);
        return null;
    }

    public String getJson() throws JSONException {
        Writer jsonString = new StringWriter();
        JSONWriter writer = new JSONWriter(jsonString);
        getJson(writer);
        return jsonString.toString();
    }

    public void getJson(JSONWriter writer) throws JSONException {
        writer.object();

        if (properties != null){
            for(String key : properties.keySet()){
                Object val = properties.get(key);
                writer.key(key);
                if (val == null){
                    writer.value("");       
                }else if (JSONSerializable.class.isAssignableFrom(val.getClass())){
                     ((JSONSerializable)val).getJson(writer);
                } else{
                    writer.value(val);
                }
            }
        }

        writer.endObject();
    }
}
