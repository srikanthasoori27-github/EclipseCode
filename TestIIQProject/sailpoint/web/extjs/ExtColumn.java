/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.extjs;

import org.json.JSONException;
import org.json.JSONWriter;

import java.io.Writer;
import java.io.StringWriter;
import java.util.Map;
import java.util.HashMap;


/**
 * Models a field definition included in the grid metadata
 * returned as a part of a json response to an ext grid.
 * Note that we have added additional keys so that grids
 * can dynamically refresh their column model- header, width
 * and sortable.
 *
 * @author <a href="mailto:jonathan.bryant@sailpoint.com">Jonathan Bryant</a>
 */
public class ExtColumn implements JSONSerializable {

    private final static String FIELD_NAME_KEY = "name";
    private final static String FIELD_DATA_IDX_KEY = "dataIndex";
    private final static String FIELD_HEADER_KEY = "header";
    private final static String FIELD_WIDTH_KEY = "width";
    private final static String FIELD_SORTABLE_KEY = "sortable";
    private final static String FIELD_EDITOR_KEY = "editorClass";
    private final static String FIELD_RENDERER_KEY = "renderer";
    private final static String FIELD_PLUGIN_CLASS_KEY = "pluginClass";

    private String name;
    private String header;
    private Integer width;
    private Boolean sortable;
    private String editorClass;
    private String renderer;
    private String pluginClass;

    private Map<String, String> additionalAttributes = new HashMap<String, String>();

    public ExtColumn(String name) {
        this.name = name;
    }

    public ExtColumn(String name, String header, Integer width, Boolean sortable) {
        this.header = header;
        this.name = name;
        this.sortable = sortable;
        this.width = width;
    }

    public void addAdditionalAttribute(String name, String value){
        if (name != null && name.length() > 0)
            additionalAttributes.put(name, value);
    }

    public void getJson(JSONWriter writer) throws JSONException {
        writer.object();
        writer.key(FIELD_NAME_KEY);
        writer.value(name.replace(".","_"));
        writer.key(FIELD_DATA_IDX_KEY);
        writer.value(name.replace(".","_"));

        if (header != null) {
            if (width != null){
                writer.key(FIELD_WIDTH_KEY);
                writer.value(width);
            }

            if (header != null){
                writer.key(FIELD_HEADER_KEY);
                writer.value(header);
            }

            if (sortable != null){
                writer.key(FIELD_SORTABLE_KEY);
                writer.value(sortable);
            }

            if (editorClass != null){
                writer.key(FIELD_EDITOR_KEY);
                writer.value(editorClass);
                writer.key("editor");
                writer.object();
                writer.key("xtype");
                writer.value(editorClass);
                writer.endObject();
            }

            // sets the xtype as well so that when ext builds the plugin object
            // it has an xtype property to work with
            if (pluginClass != null){
                writer.key(FIELD_PLUGIN_CLASS_KEY);
                writer.value(pluginClass);
                writer.key("xtype");
                writer.value(pluginClass);
            }


            if (!additionalAttributes.isEmpty()){
                for(String key : additionalAttributes.keySet()){
                    String val = additionalAttributes.get(key);
                    writer.key(key);
                    writer.value(val!=null?val:"");
                }
            }

           if (renderer != null){
                writer.key(FIELD_RENDERER_KEY);
                writer.value(renderer);
            }
        }
        writer.endObject();
    }


    public void setRenderer(String renderer) {
        this.renderer = renderer;
    }


    public void setEditorClass(String editorClass) {
        this.editorClass = editorClass;
    }


    public void setPluginClass(String pluginClass) {
        this.pluginClass = pluginClass;
    }

    public String getJson() throws JSONException {
        Writer jsonString = new StringWriter();
        JSONWriter writer = new JSONWriter(jsonString);
        getJson(writer);
        return jsonString.toString();
    }
}
