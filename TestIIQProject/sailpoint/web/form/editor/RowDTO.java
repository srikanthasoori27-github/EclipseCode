/* (c) Copyright 2016 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.form.editor;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONException;
import org.json.JSONObject;
import sailpoint.web.BaseDTO;
import sailpoint.web.FieldDTO;
import sailpoint.web.util.WebUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * DTO which is used by Form Editor UI to group together fields in a same row.
 *
 * Created by ketan.avalaskar.
 */
@SuppressWarnings("serial")
public class RowDTO extends BaseDTO {

    private static final Log log = LogFactory.getLog(RowDTO.class);


    ////////////////////////////////////////////////////////////////////////////
    //                                                                        //
    //  Fields                                                                //
    //                                                                        //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Label of the Row [ Row <n> ].
     */
    private String text;

    /**
     * Number of columns / field contained in this row of a section.
     */
    private int columns;

    /**
     * List of Field DTOs.
     */
    private List<FieldDTO> fieldDTOs;


    ////////////////////////////////////////////////////////////////////////////
    //                                                                        //
    //  Constructor                                                           //
    //                                                                        //
    ////////////////////////////////////////////////////////////////////////////

    public RowDTO() { }

    /**
     * Constructor that sets the text of the Row.
     * @param id - Index of this Row in a parent Section
     */
    public RowDTO(int id) {
        this.text = "Row " + id;
    }

    /**
     * Builds a RowDTO out of a JSON object.
     *
     * @param rowJSON - A JSONObject containing form data
     */
    public RowDTO(JSONObject rowJSON) throws JSONException {
        setText(WebUtil.getJSONString(rowJSON, "text"));
        setColumns(WebUtil.getJSONInt(rowJSON, "columns"));
    }

    ////////////////////////////////////////////////////////////////////////////
    //                                                                        //
    //  getter and setters                                                    //
    //                                                                        //
    ////////////////////////////////////////////////////////////////////////////

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public int getColumns() {
        return columns;
    }

    public void setColumns(int columns) {
        this.columns = columns;
    }

    public List<FieldDTO> getFieldDTOs() {
        return fieldDTOs;
    }

    public void setFieldDTOs(List<FieldDTO> fieldDTOs) {
        this.fieldDTOs = fieldDTOs;
    }


    ////////////////////////////////////////////////////////////////////////////
    //
    // Utilities
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Add FieldDTO to fieldDTOs List.
     */
    public void addField(FieldDTO field) {
        if (null == fieldDTOs) {
            fieldDTOs = new ArrayList<FieldDTO>();
        }
        fieldDTOs.add(field);
        // Update columns
        columns = fieldDTOs.size();
    }

    /**
     * Serialize RowDTO to JSON.
     */
    public JSONObject getJSON() {
        JSONObject rowJSON = new JSONObject();
        try {
            rowJSON.put("text", text);
            rowJSON.put("columns", columns);
        } catch (JSONException jsoe) {
            log.error("Unable to serialize RowDTO to json: " + jsoe.getMessage());
        }
        return rowJSON;
    }
}
