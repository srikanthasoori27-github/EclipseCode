/* (c) Copyright 2015 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.service;

import java.beans.PropertyDescriptor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.beanutils.PropertyUtils;

import sailpoint.object.ColumnConfig;
import sailpoint.object.SailPointObject;
import sailpoint.tools.Util;


/**
 * A base class for DTOs in the service layer.  This has some default behavior for populating DTO
 * fields from a map representation of an object (eg - from results from a BaseListService).  This
 * can also be used without a map representation of the object, but then it is up to the subclass
 * constructor or the caller to initialize all of the data on the DTO.
 */
public class BaseDTO {

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //
    // PROPERTIES
    //
    ////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * The ID of the object represented by this DTO.
     */
    private String id;

    /**
     * A map of extra attributes for the approval that aren't modeled on the DTO but may be included
     * through column configs.
     */
    private Map<String,Object> attributes;


    ////////////////////////////////////////////////////////////////////////////////////////////////
    //
    // CONSTRUCTORS
    //
    ////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Default constructor
     */
    public BaseDTO() {}

    /**
     * Constructor that accepts the ID.
     *
     * @param  id  The ID of the object represented by this DTO.
     */
    public BaseDTO(String id) {
        this.id = id;
    }

    /**
     * Constructor based on a map representation of the object.  This automatically copies
     * properties from the object onto this DTO if there is a corresponding setter for the
     * property.  Any simple properties that aren't stored on this object are saved in the
     * attributes map.
     *
     * @param  object  The map representation of the object.
     * @param  cols    The ColumnConfigs used to build the object.
     */
    public BaseDTO(Map<String,Object> object, List<ColumnConfig> cols) {
        assimilate(object, cols, null);
    }
    
    /**
     * Constructor based on a map representation of the object.  This automatically copies
     * properties from the object onto this DTO if there is a corresponding setter for the
     * property.  Any simple properties that aren't stored on this object are saved in the
     * attributes map.
     *
     * @param  object  The map representation of the object.
     * @param  cols    The ColumnConfigs used to build the object.
     * @param  additionalColumns Additional columns, usually internal data with no column config                
     */
    public BaseDTO(Map<String,Object> object, List<ColumnConfig> cols, List<String> additionalColumns) {
        assimilate(object, cols, additionalColumns);
    }


    ////////////////////////////////////////////////////////////////////////////////////////////////
    //
    // ASSIMILATING MAPS ONTO THE DTO
    //
    ////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Copy the given values from the map representation of the object onto this DTO.  For all
     * ColumnConfigs, we will first try to set the value from the map onto this DTO directly if
     * there is a public setter.  If there is not a public setter and the value is simple (ie - not
     * a SailPointObject) the value will be set in the attributes map.
     *
     * @param  object  The Map representation of the object.
     * @param  cols    The ColumnConfigs that define which properties belong in this object.
     * @param  additionalColumns Names of additional columns in the object, for internal values                
     */
    private void assimilate(Map<String,Object> object, List<ColumnConfig> cols, List<String> additionalColumns) {
        if (!Util.isEmpty(object)) {
            
            // First write these internal columns, but skip the attributes, subclasses should be handling these non-properties themselves
            for (String col : Util.safeIterable(additionalColumns)) {
                write(col, object, false);
            }
            
            for (ColumnConfig col : Util.safeIterable(cols)) {
                String prop = col.getDataIndex();

                // Shouldn't happen, but let's be safe anyway.
                if (null == prop) {
                    continue;
                }

                write(prop, object, true);
            }
        }
    }

    /**
     * Attempts to write the value to the DTO
     * @param prop Name of the property
     * @param object Map representation of the object
     * @param writeAttribute True if should write to attribute map if no property found, false to skip
     */
    protected void write(String prop, Map<String, Object> object, boolean writeAttribute) {
        // The map building process creates a map with JSON-safe keys.  Use this for lookup.
        String mapProp = Util.getJsonSafeKey(prop);
        Object val = object.get(mapProp);

        // If the value is null, we have nothing to do.
        if (null != val) {
            // First, try to store this value as a top-level property on the DTO.
            boolean stored = writeProperty(prop, val);

            // If the value wasn't stored on the DTO, try to save it in the attributes map.
            if (!stored && writeAttribute) {
                writeAttribute(prop, val);
            }
        }
    }

    /**
     * Attempt to write the property with the given name and value on this DTO.  The only works if
     * there is a public setter for the property.
     *
     * @param  name   The name of the property to write.
     * @param  value  The value to write.
     *
     * @return True if the property was writeable, false otherwise.
     */
    protected boolean writeProperty(String name, Object value) {
        boolean wrote = false;

        if (isWriteable(name, value)) {
            try {
                // All exceptions will just leave "wrote" set to false.
                PropertyUtils.setProperty(this, name, value);
                wrote = true;
            }
            catch (IllegalAccessException iae) { /* no-op */ }
            catch (NoSuchMethodException nsme) { /* no-op */ }
            catch (InvocationTargetException ite) { /* no-op */ }
        }

        return wrote;
    }

    /**
     * Return whether the property with the given name is writeable on this DTO.
     *
     * @param  name   The name of the property to write.
     * @param  value  The value to be written.
     *
     * @return True if the property is writeable with the given value, false otherwise.
     */
    private boolean isWriteable(String name, Object value) {
        boolean writeable = false;

        try {
            // If getting the property descriptor fails, just return false.
            PropertyDescriptor pd = PropertyUtils.getPropertyDescriptor(this, name);
            if ((null != pd) && (null != pd.getWriteMethod())) {
                // If the value is null, we can't compare the value type to the setter's parameter type.
                // When this is the case, assume that we can write it.
                if (null == value) {
                    writeable = true;
                }
                else {
                    // If we have a value ... make sure that the type matches the type of the setter.
                    Method method = pd.getWriteMethod();
                    Class<?>[] paramTypes = method.getParameterTypes();
                    if ((paramTypes.length == 1) && (paramTypes[0].isAssignableFrom(value.getClass()))) {
                        writeable = true;
                    }
                }
            }
        }
        catch (IllegalAccessException iae) { /* no-op */ }
        catch (NoSuchMethodException nsme) { /* no-op */ }
        catch (InvocationTargetException ite) { /* no-op */ }
        catch (IllegalArgumentException iae) { /* no-op */ }

        return writeable;
    }

    /**
     * Write the given attribute into the attributes map if it is a simple object (ie - not a
     * SailPointObject).
     *
     * @param  name   The name of the attribute.
     * @param  value  The value to write.
     */
    private void writeAttribute(String name, Object value) {
        // Only write this if there is actually a value and it is a simple object.
        if ((null != value) && !(value instanceof SailPointObject)) {
            if (null == this.attributes) {
                this.attributes = new HashMap<String,Object>();
            }
            this.attributes.put(name, value);
        }
    }


    ////////////////////////////////////////////////////////////////////////////////////////////////
    //
    // GETTERS AND SETTERS
    //
    ////////////////////////////////////////////////////////////////////////////////////////////////

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Map<String, Object> getAttributes() {
        return attributes;
    }

    public void setAttributes(Map<String, Object> attributes) {
        this.attributes = attributes;
    }
    
    public void setAttribute(String name, Object value) {
        if (this.attributes == null) {
            this.attributes = new HashMap<>();
        }
        this.attributes.put(name, value);
    }
}
