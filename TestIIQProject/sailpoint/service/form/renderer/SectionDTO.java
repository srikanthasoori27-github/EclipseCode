/* (c) Copyright 2009 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.service.form.renderer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnore;

import sailpoint.object.Attributes;
import sailpoint.object.Form;
import sailpoint.service.form.renderer.item.FormItemDTO;
import sailpoint.tools.Util;

/**
 * DTO which can be serialized into a Ext FieldSet object.
 *
 * Note that some extended properties on the FormItemDTO object are
 * require the use of the Ext class sailpoint.form.FormPanel rather
 * than the default ext FormPanel implementation.
 *
 * @author: jonathan.bryant@sailpoint.com
 */
public class SectionDTO {

    public static String SECTION_TYPE_DEFAULT = "fieldset";

    /**
     * FieldSet title. If set the title will be embedded in the
     * FielSet border.
     */
    private String title;
    
    /**
     * The description after the fieldset title above.
     */
    private String subtitle;

    /**
     * List of fields. This is actually serialzed as 'items'
     * to comply with Ext's convention.
     */
    private List<FormItemDTO> fields;

    private int columns;

    protected String type;
    
    private Attributes<String, Object> attributes;

    public SectionDTO() {}

    public SectionDTO(Form.Section section) {
        this.title = section.getLabel();
        this.type = section.getType() == null ? this.SECTION_TYPE_DEFAULT : section.getType();
        this.columns = section.getColumns();
        this.attributes = new Attributes<String, Object>();
        Attributes<String, Object> sectionAttributes = section.getAttributes();
        if (sectionAttributes != null) {
            this.attributes.putAll(section.getAttributes());            
            coerce(this.attributes);
        }
    }

    public void addField(FormItemDTO field) {
        if (fields == null)
            fields = new ArrayList<FormItemDTO>();
        fields.add(field);
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public int getColumns() {
        return columns;
    }

    public void setColumns(int columns) {
        this.columns = columns;
    }

    public String getSubtitle() {
        return subtitle;
    }

    public void setSubtitle(String subtitle) {
        this.subtitle = subtitle;
    }

    public Attributes<String, Object> getAttributes() {
        return attributes;
    }

    public void setAttributes(Attributes<String, Object> attributes) {
        this.attributes = attributes;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    /**
     * Ext expects the fields to belong to the 'items' collection.
     * We also need to customize the serialization of our Field
     * objects so we'll convert them into maps before serialization 
     * occurs.
     * @return
     */
    public List<Map> getItems() {
        List<Map> out = new ArrayList<Map>();

        if (fields != null) {
            for (FormItemDTO dto : fields) {
                out.add(dto.toMap());
            }
        }

        return out;
    }

    /**
     * This is not serialized as JSON since we need to return the
     * field list as 'items'. Serialization will occur on the
     * getItems method.
     * @return
     */
    @JsonIgnore
    public List<FormItemDTO> getFields() {
        return fields;
    }

    public void setFields(List<FormItemDTO> fields) {
        this.fields = fields;
    }
    
    public Map<String, Object> toMap() {
        Map<String, Object> sectionMap = new HashMap<String, Object>();
        if (attributes != null) {
            sectionMap.putAll(attributes);
        }
        sectionMap.put("title", getTitle());
        sectionMap.put("columns", getColumns());
        // Items are Fields in Map form, so there's no need to get Fields too
        sectionMap.put("items", getItems());
        sectionMap.put("type", getType());
        sectionMap.put("subtitle", subtitle);
        return sectionMap;
    }

    
    ////////////////////////////////////////////////////////////////////////////
    //
    // Utilities
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Coerce any values in the attributes map that we understand.  This looks
     * for some standard ExtJS configs and ensures that they are the correct
     * data type and format.  The map that is passed in gets modified by this
     * method.
     */
    static void coerce(Attributes<String,Object> attrs) {
        if (null != attrs) {
            Boolean disabledVal = null;

            for (Map.Entry<String,Object> entry : attrs.entrySet()) {
                String key = entry.getKey();
                
                // Ext expects hidden to be a boolean instead of a string.
                if ("hidden".equals(key)) {
                    entry.setValue(Util.otob(entry.getValue()));
                }
                
                // We typically use "readOnly" when we want things to be
                // read-only, but ExtJS uses the "disabled" config option.
                // Promote this to the "disabled" config and make sure it
                // is a boolean.  Note that we prefer the "disabled" config
                // over "readOnly" since this is what ExtJS expects.
                if ("disabled".equals(key) ||
                    ("readOnly".equals(key) && (null == disabledVal))) {
                    disabledVal = Util.otob(entry.getValue());
                }
            }

            // If we found this value, put it into the attributes map.
            if (null != disabledVal) {
                attrs.put("disabled", disabledVal.booleanValue());
            }
        }
    }
}
