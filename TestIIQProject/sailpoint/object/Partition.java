/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.object;

import org.json.JSONString;

import sailpoint.tools.JsonHelper;
import sailpoint.tools.Util;
import sailpoint.tools.xml.AbstractXmlObject;
import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLProperty;

/**
 * A Partition is an XML/JSON object that is used by the 
 * connector interface to describe slices of data that
 * can be split up.
 * 
 * @see sailpoint.connector.Connector#getIteratorPartitions(String, int, sailpoint.object.Filter, java.util.Map)
 * 
 * @author <a href="mailto:Dan.Smith@sailpoint.com">Dan Smith</a>
 */
public class Partition extends AbstractXmlObject implements JSONString {

    private static final long serialVersionUID = 1L;

    private static final String ATT_OBJECT_TYPE = "objectType";
    
    /**
     * Name of the partition. The name can be set by the 
     * connector if the name can be determined or the
     * caller can name the partition.
     */
    private String name;
    
    /**
     * How many objects in the partition.  
     */
    private int size;
    
    /**
     * The attribute necessary to configure an Iterator
     * so it can handle a single partition of work.
     * 
     */
    private Attributes<String,Object> attributes;
    
    /**
     * Default constructor.
     */
    public Partition() {
    }
    
    /**
     * Partitions returned from Connectors are required to set both the 
     * name and objectType.
     * 
     * @param name The name of the partition
     * @param objectType For Connector's this is either account or group
     */
    public Partition(String name, String objectType) {
        this.name = name;
        setObjectType(objectType);
    }
    
    @XMLProperty
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @XMLProperty
    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public String toJSONString() {
       return JsonHelper.toJson(this);
    }
    
    @XMLProperty(mode=SerializationMode.UNQUALIFIED)
    public Attributes<String, Object> getAttributes() {
        return attributes;
    }

    public void setAttributes(Attributes<String, Object> attributes) {
        this.attributes = attributes;
    }

    public void setAttribute(String name, Object value) {
        if ( attributes == null ) {
            attributes = new Attributes<String,Object>();
        }
        attributes.put(name, value);
    }
    
    public Object getAttribute(String name) {
        return Util.get(attributes, name); 
    }

    public int getInteger(String name) {
        return Util.getInt(attributes,  name);
    }
    
    public String getString(String name) {
        return Util.getString(attributes, name);
    }

    public Object get(String name) {
        return getAttribute(name);
    }

    public void put(String name, Object value) {
        setAttribute(name, value);
    }
    
    public void setObjectType(String type) {
        setAttribute(ATT_OBJECT_TYPE, type);
    }
    
    public String getObjectType() {
        return (String)getAttribute(ATT_OBJECT_TYPE);
    }
}
