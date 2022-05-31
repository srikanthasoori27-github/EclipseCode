/* (c) Copyright 2016 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.form.editor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONException;
import org.json.JSONObject;

import sailpoint.object.Attributes;
import sailpoint.object.Field;
import sailpoint.object.Form.Section;
import sailpoint.object.FormItem;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.web.BaseDTO;
import sailpoint.web.FieldDTO;
import sailpoint.web.util.WebUtil;

@SuppressWarnings("serial")
public class SectionDTO extends BaseDTO {

    private static final Log log = LogFactory.getLog(SectionDTO.class);

    /**
     * Maximum columns allowed in a Section.
     */
    public static final int MAX_COLS = 4;

    /**
     * The internal name for the section.
     */
    private String name;

    /**
     * Label of section.
     */
    private String label;

    /**
     * Type of Section - It is optional.
     */
    private String type;

    /**
     * Number of columns contained in the form section.
     */
    private int columns;

    /**
     * This was in the original model but it has never been used. Now that there
     * is a _type this might never be used, but it will be kept around for
     * awhile. The intent was this be used for simple qualifications to the
     * type like maybe "vertical" or "horizontal" for simple field collections.
     */
    private String layout;

    /**
     * An optional priority that is used during form assembly to influence the
     * order of fields.
     */
    private int priority;

    /**
     * Extended attributes that can influence the renderer.
     */
    private Attributes<String,Object> attributes;

    /**
     * List of FieldDTOs and RowDTOs.
     * We need a single List to maintain order of items.
     */
    private List<BaseDTO> formItemDTOs;

    /**
     * Extended attributes that can influence the renderer.
     */

    private DynamicValueBooleanHolder readOnly = DynamicValueBooleanHolder.createDefault();
    private DynamicValueBooleanHolder hidden =  DynamicValueBooleanHolder.createDefault();
    private DynamicValueBooleanHolder hideNulls =  DynamicValueBooleanHolder.createDefault();

    private String subTitle;

    ////////////////////////////////////////////////////////////////////////////
    //                                                                        //
    //  Constructor                                                           //
    //                                                                        //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Builds an empty SectionDTO
     */
    public SectionDTO() {
    }

    /**
     * Builds a SectionDTO having name defined
     */
    public SectionDTO(String secname) {
        name = secname;
    }

    /**
     * Builds a SectionDTO out of a Section object
     * @param section A Section object
     */
    public SectionDTO(Section section) {
        name = section.getName();
        label = section.getLabel();
        type = section.getType();
        columns = section.getColumns();
        layout = section.getLayout();
        priority = section.getPriority();

        // Check for extended attributes object and clone it
        // So that original Attributes object from the source form object won't disturb.
        attributes = (null != section.getAttributes()) ? section.getAttributes().mediumClone() : new Attributes<String,Object>();

        // Get dynamic value of extended attribute based on its type
        try {
            readOnly = DynamicValueBooleanHolder.createFromDynamicValueOrDefault(section.getReadOnlyDefinition(getContext()));
            hidden = DynamicValueBooleanHolder.createFromDynamicValueOrDefault(section.getHiddenDefinition(getContext()));
            hideNulls = DynamicValueBooleanHolder.createFromDynamicValueOrDefault(section.getHideNullsDefinition(getContext()));

            attributes.put(Section.ATT_READ_ONLY, readOnly.convertToJson());
            attributes.put(Section.ATT_HIDDEN, hidden.convertToJson());
            attributes.put(Section.ATT_HIDE_NULLS, hideNulls.convertToJson());
        } catch (JSONException jsoe) {
              log.warn("Unable to serialize SectionDTO to json: " + jsoe.getMessage());
        } catch (GeneralException ex) {
              log.warn("Unable to serialize SectionDTO to json: " + ex.getMessage());
        }

        // Add Fields and Rows if columns is set
        addFormItems(section);
    }

    /**
     * Builds a SectionDTO out of a JSON object
     * @param sectionJSON A JSON sting containing form data
     */
    public SectionDTO(JSONObject sectionJSON) {
        try {
            this.setName(WebUtil.getJSONString(sectionJSON, "name"));
            this.setLabel(WebUtil.getJSONString(sectionJSON, "label"));
            this.setType(WebUtil.getJSONString(sectionJSON, "type"));
            this.setColumns(WebUtil.getJSONInt(sectionJSON, "columns"));
            this.setLayout(WebUtil.getJSONString(sectionJSON, "layout"));
            this.setPriority(WebUtil.getJSONInt(sectionJSON, "priority"));

            // Convert JSON to Dynamic object for extended attributes
            JSONObject attributesJSON = WebUtil.getJSONObject(sectionJSON, "attributes");

            if(null != attributesJSON) {
                JSONObject readOnlyJSONObject = WebUtil.getJSONObject(attributesJSON, "readOnly");
                JSONObject hiddenJSONObject = WebUtil.getJSONObject(attributesJSON, "hidden");
                JSONObject hideNullsJSONObject = WebUtil.getJSONObject(attributesJSON, "hideNulls");

                readOnly = DynamicValueBooleanHolder.createFromJsonObjectOrDefault(readOnlyJSONObject);
                hidden = DynamicValueBooleanHolder.createFromJsonObjectOrDefault(hiddenJSONObject);
                hideNulls = DynamicValueBooleanHolder.createFromJsonObjectOrDefault(hideNullsJSONObject);
            }

            // Extended attributes
            this.setAttributes(WebUtil.getJSONMap(sectionJSON, "attributes"));

        } catch (JSONException jsoe) {
            if (log.isWarnEnabled()) {
                log.warn("Exception during section constructor: " + jsoe.getMessage(), jsoe);
            }
        }
    }

    ////////////////////////////////////////////////////////////////////////////
    //                                                                        //
    //  getter and setters                                                    //
    //                                                                        //
    ////////////////////////////////////////////////////////////////////////////

    public void addField(FieldDTO fieldDTO) {
        if (formItemDTOs == null) {
            formItemDTOs = new ArrayList<BaseDTO>();
        }
        formItemDTOs.add(fieldDTO);
    }

    /**
     * Add RowDTO to rowDTOs List.
     */
    private void addRow(RowDTO row) {
        if (null == formItemDTOs) {
            formItemDTOs = new ArrayList<BaseDTO>();
        }
        formItemDTOs.add(row);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public int getColumns() {
        return columns;
    }

    public void setColumns(int columns) {
        this.columns = columns;
    }

    public String getLayout() {
        return layout;
    }

    public void setLayout(String layout) {
        this.layout = layout;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public Attributes<String,Object> getAttributes() {
        return attributes;
    }

    public void setAttributes(Attributes<String,Object> attributes) {
        this.attributes = attributes;
    }

    /**
     * Return flatten out fields.
     * For editor, Section can contain Row / Field, but other
     * classes than editor, are interested only in fields.
     */
    public List<FieldDTO> getFieldDTOs() {
        List<FieldDTO> fields = new ArrayList<FieldDTO>();
        for (BaseDTO dto : Util.safeIterable(formItemDTOs)) {
            if (dto instanceof RowDTO) {
                List<FieldDTO> rowFields = ((RowDTO) dto).getFieldDTOs();

                // Check for non-empty rowFields
                if(!Util.isEmpty(rowFields)) {
                    fields.addAll(rowFields);
                }
            } else if (dto instanceof FieldDTO) {
                fields.add((FieldDTO) dto);
            }
        }
        return fields;
    }

    public List<BaseDTO> getFormItemDTOs() {
        return formItemDTOs;
    }

    public void setFormItems(List<BaseDTO> formItemDTOs) {
        this.formItemDTOs = formItemDTOs;
    }

    public String getSubTitle() {
        return subTitle;
    }

    public void setSubTitle(String subTitle) {
        this.subTitle = subTitle;
    }

    /**
     * Add fields and rows.
     * Transform fields into rows for column-based editing.
     *
     * If Section.columns is not specified Fields are added directly in Section.
     * If Field.columnSpan is greater or equal to Section.columns,
     * Field is added directly in Section.
     * Remaining Fields are added in Rows as per columnSpans maintaining order of Items.
     */
    private void addFormItems(Section section) {
        int span;
        int cols = (0 != columns) ? Math.min(columns, MAX_COLS) : MAX_COLS;
        int accum = 0;
        int rowId = 1;
        RowDTO currentRow = new RowDTO(rowId);

        for (FormItem item : Util.iterate(section.getItems())) {
            if (item instanceof Field) {
                FieldDTO field = new FieldDTO((Field) item);

                // No columns specified on the section so put this field directly in section.
                if (0 == columns) {
                    addField(field);
                    continue;
                }

                // Span is greater or equal to section.columns so put this field outside row.
                if (field.getColumnSpan() >= columns) {
                    addField(field);

                    // Field outside row is added so reset current row to maintain order
                    if (!Util.isEmpty(currentRow.getFieldDTOs())) {
                        currentRow = new RowDTO(++rowId);
                        accum = 0;
                    }
                    continue;
                }

                // Push current row if it is empty,
                // so to maintain order with fields outside of row.
                if (Util.isEmpty(currentRow.getFieldDTOs())) {
                    addRow(currentRow);
                }

                // A span greater than zero was specified on the field so use the min value
                // of field.columnSpan and section.columns in case of misconfiguration.
                if (0 != field.getColumnSpan()) {
                    span = Math.min(field.getColumnSpan(), cols);
                } else {
                    // Default the span to one column.
                    span = 1;
                }

                if (accum + span <= cols) {
                    currentRow.addField(field);
                    accum += span;
                } else {
                    // Reset
                    currentRow = new RowDTO(++rowId);
                    currentRow.addField(field);
                    accum = span;

                    // Push current row
                    addRow(currentRow);
                }
            }
        }

        // Second pass to remove single field rows.
        removeSingleFieldRow();
    }

    /**
     * Replace a single field row in formItemDTOs with that field.
     */
    private void removeSingleFieldRow() {
        for (BaseDTO dto : Util.safeIterable(formItemDTOs)) {
            if (dto instanceof RowDTO) {
                List<FieldDTO> fields = ((RowDTO) dto).getFieldDTOs();
                if (1 == Util.nullSafeSize(fields)) {
                    formItemDTOs.set(formItemDTOs.indexOf(dto), fields.get(0));
                }
            }
        }
    }

    ////////////////////////////////////////////////////////////////////////////
    //
    // important checks
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Return true if this is a wrapper section, meaning it doesn't define
     * any significant properties that will influence a renderer.
     *
     * This is used wrap all fields which don't define section attribute.
     */
    public boolean isWrapper() {

        // most important is label,
        // name never used in the renderer,
        // layout was never used and I don't think type was either,
        // columns is significant,
        // no priority
        return (name == null &&
                label == null &&
                type == null &&
                columns == 0 &&
                priority == 0);
    }

    ////////////////////////////////////////////////////////////////////////////
    //
    // Utilities
    //
    ////////////////////////////////////////////////////////////////////////////

    static public String uuid() {
        return Util.uuid();
    }

    /**
     * Coerce any values in the attributes map that we understand. This looks
     * for some standard ExtJS configs and ensures that they are the correct
     * data type and format. The map that is passed in gets modified by this
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
                // is a boolean. Note that we prefer the "disabled" config
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

    public Section commit(Section section) {
        if (section == null) {
            section = new Section();
        }
        try {
            section.setName((String) Util.nullify(this.getName()));
            section.setLabel((String) Util.nullify(this.getLabel()));
            section.setLayout((String) Util.nullify(this.getLayout()));
            section.setType((String) Util.nullify(this.getType()));
            section.setColumns((Integer) Util.nullify(this.getColumns()));
            section.setPriority((Integer) Util.nullify(this.getPriority()));
            section.setAttributes(this.getAttributes());

            // Extended attributes
            section.setAttributes(this.getAttributes());

            // Store dynamic value to section's extended attribute map
            section.setHiddenDefinition(hidden.convertToDynamicValue(getContext()));
            section.setReadOnlyDefinition(readOnly.convertToDynamicValue(getContext()));
            section.setHideNullsDefinition(hideNulls.convertToDynamicValue(getContext()));
        } catch (GeneralException ge) {
            log.warn("Unable to commit section: " + ge.getMessage());
        }
        return section;
    }

    public JSONObject getJSON() {
        JSONObject sectionJSON = new JSONObject();
        try {
            sectionJSON.put("id", uuid());
            sectionJSON.put("label", this.getLabel());
            sectionJSON.put("name", this.getName());
            sectionJSON.put("type", this.getType());
            sectionJSON.put("columns", this.getColumns());
            sectionJSON.put("layout", this.getLayout());
            sectionJSON.put("priority", this.getPriority());
            sectionJSON.put("attributes", this.getAttributes());
        } catch (JSONException jsoe) {
            log.error("Unable to serialize SectionDTO to json: " + jsoe.getMessage());
        }
        return sectionJSON;
    }

    public Map<String,Object> toMap() {
        Map<String,Object> sectionMap = new HashMap<String,Object>();

        sectionMap.put("name", this.getName());
        sectionMap.put("label", this.getLabel());
        sectionMap.put("type", this.getType());
        sectionMap.put("columns", this.getColumns());
        sectionMap.put("layout", this.getLayout());
        sectionMap.put("priority", this.getPriority());
        sectionMap.put("attributes", this.getAttributes());

        return sectionMap;
    }
}
