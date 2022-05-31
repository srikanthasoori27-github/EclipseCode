/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.web.extjs;

import sailpoint.tools.Util;

/**
 * Serializable object representing an individual column in
 * an Ext grid response metadata object.
 *
 * @author <a href="mailto:jonathan.bryant@sailpoint.com">Jonathan Bryant</a>
*/
public class GridField {

    /**
     * Field name
     */
    private String name;

    /**
     * Optional name of the value this field should be populated with.
     */
    private String mapping;

    /**
     * Optional data type of this field.
     */
    private String type;

    /**
     * Default field value
     */
    private Object defaultValue;

    public GridField(String name) {
        this(name, name);
    }
    
    public GridField(String name, String mapping) {
        this.name = Util.getJsonSafeKey(name);
        this.mapping = Util.getJsonSafeKey(mapping);
        this.type = "auto";
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getMapping() {
        return mapping;
    }

    public void setMapping(String mapping) {
        this.mapping = mapping;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

}
