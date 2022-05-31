package sailpoint.web;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONException;
import org.json.JSONObject;

import sailpoint.object.FormRef;
import sailpoint.tools.xml.XMLProperty;

/**
 * FormReferenceDTO object to represent a FormRef object
 * @author alevi.d'costa
 */
@SuppressWarnings("serial")
public class FormReferenceDTO extends BaseDTO {

    private static final Log log = LogFactory.getLog(FormReferenceDTO.class);

    /**
     * id of the referenced form within FormRef object
     */
    private String _refId;
    /**
     * name of the referenced form withing FormRef object
     */
    private String _refName;

    public String getReferenceId() {
        return _refId;
    }

    @XMLProperty
    public void setReferenceId(String id) {
        _refId = id;
    }

    public String getReferenceName() {
        return _refName;
    }

    @XMLProperty
    public void setReferenceName(String name) {
        _refName = name;
    }

    public FormReferenceDTO() {}

    public FormReferenceDTO(String frmRefId,String frmRefName) {
        _refId = frmRefId;
        _refName = frmRefName;
    }

    public FormReferenceDTO(FormReferenceDTO frmRefDTO) {
        _refId = frmRefDTO.getReferenceId();
    }

    /**
     * Return the JSON representation of FormRef object
     * @return
     */
    public JSONObject getJSON() {
        JSONObject fieldJSON = new JSONObject();
        try {
            fieldJSON.put("id", getReferenceId());
            fieldJSON.put("name", getReferenceName());
        } catch (JSONException jsoe) {
            log.warn("Unable to serialize FormRef to json: " + jsoe.getMessage());
        }
        return fieldJSON;
    }

    /**
     * Return the FormRef object the DTO represents
     * @return
     */
    public FormRef getFormRef() {
        return new FormRef(_refId,_refName);
    }
}
