package sailpoint.web;


import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONException;
import org.json.JSONObject;

import sailpoint.object.Attributes;
import sailpoint.object.Form.Button;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.web.util.WebUtil;

public class ButtonDTO extends BaseDTO {
    private static final Log log = LogFactory.getLog(ButtonDTO.class);
    
    public static String TYPE_BUTTON = "button";    
    /** sailpoint.object.Form.Button stuff **/
    String label;
    String action;
    String name;
    String type;
    String parameter;
    String value;
    boolean readOnly;
    boolean clicked;
    boolean skipValidation;
    Attributes<String, Object> attributes;

    public ButtonDTO() {}
    
    public ButtonDTO(Button src) {
        super();
        this.name = src.getAction();
        this.action = src.getAction();
        this.label = src.getLabel();
        this.type = TYPE_BUTTON;
        this.parameter = src.getParameter();
        this.value = src.getValue();
        this.readOnly = src.isReadOnly();
        this.clicked = src.isClicked();
        this.attributes = src.getAttributes();
        this.skipValidation = src.getSkipValidation();
    }

    /**
     * Builds a ButtonDTO out of a json string
     *
     */
    public ButtonDTO(JSONObject buttonJSON) {
        try {
            this.setLabel(WebUtil.getJSONString(buttonJSON, "label"));
            this.setAction(WebUtil.getJSONString(buttonJSON, "action"));
            this.setParameter(WebUtil.getJSONString(buttonJSON, "parameter"));
            this.setReadOnly(WebUtil.getJSONBoolean(buttonJSON, "readOnly"));
            this.setSkipValidation(WebUtil.getJSONBoolean(buttonJSON, "skipValidation"));
            this.setValue(WebUtil.getJSONString(buttonJSON, "value"));
        } catch (JSONException jsoe) {
            if (log.isWarnEnabled())
                log.warn("Exception during button constructor: " + jsoe.getMessage(), jsoe);
        } 
    }
    
    public ButtonDTO(ButtonDTO src) {
        this.setUid(src.getUid());
        this.name = src.getName();
        this.action = src.getAction();
        this.label = src.getLabel();
        this.type = src.getType();
        this.parameter = src.getParameter();
        this.value = src.getValue();
        this.readOnly = src.isReadOnly();
        this.clicked = src.isClicked();
        this.attributes = src.getAttributes();
        this.skipValidation = src.getSkipValidation();
    }
    
    public JSONObject getJSON() {
        JSONObject fieldJSON = new JSONObject();
        try {
            fieldJSON.put("id", this.getUid());
            fieldJSON.put("label",  this.getLabel());
            fieldJSON.put("action",  this.getAction());   
            fieldJSON.put("type",  TYPE_BUTTON);
            fieldJSON.put("name",  this.getName());
            fieldJSON.put("parameter", this.getParameter());
            fieldJSON.put("readOnly", this.isReadOnly());
            fieldJSON.put("skipValidation", this.getSkipValidation());
            fieldJSON.put("value", this.getValue());
            String displayName = this.getLabel();
            if (Util.isNullOrEmpty(displayName)) {
                displayName = this.getName();
            }
            fieldJSON.put("displayName", displayName);
         
        } catch (JSONException jsoe) {
            log.warn("Unable to serialize ButtonDTO to json: " + jsoe.getMessage());
        }
        return fieldJSON;
    }
    
    public Button commit(Button button) {
        if(button==null) {
            button = new Button();
        }

        //We only allow changing label/action via UI. We should reconsider this
        button.setAction((String)Util.nullify(this.getAction()));
        button.setLabel((String)Util.nullify(this.getLabel()));
        button.setParameter((String)Util.nullify(this.getParameter()));
        button.setValue((String)Util.nullify(this.getValue()));
        button.setReadOnly(Boolean.valueOf(this.isReadOnly()));
        button.setClicked(Boolean.valueOf(this.isClicked()));
        button.setSkipValidation(Boolean.valueOf(this.getSkipValidation()));
        button.setAttributes(this.getAttributes());
        
        return button;
    }

    public static List<Button> commit(List<ButtonDTO> dtos, List<Button> buttonObjs) 
    throws GeneralException {

        List<Button> buttons = null;
        if (dtos != null && dtos.size() > 0) {
            buttons = new ArrayList<Button>();
            for (ButtonDTO dto : dtos) {
                Button button = null;
                
                if(buttonObjs!=null) {
                    for(Button f : buttonObjs) {
                        if(f.getAction().equals(dto.getAction())) {
                            button = f;
                            break;
                        }
                    }
                }
                
                buttons.add(dto.commit(button));
            }
        }
        return buttons;
    }        

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getParameter() {
        return parameter;
    }

    public void setParameter(String parameter) {
        this.parameter = parameter;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public boolean isReadOnly() {
        return readOnly;
    }

    public void setReadOnly(boolean readOnly) {
        this.readOnly = readOnly;
    }

    public Attributes<String, Object> getAttributes() {
        return attributes;
    }

    public void setAttributes(Attributes<String, Object> attributes) {
        this.attributes = attributes;
    }

    public boolean isClicked() {
        return clicked;
    }

    public void setClicked(boolean clicked) {
        this.clicked = clicked;
    }
    
    public boolean getSkipValidation() {
        return this.skipValidation;
    }

    public void setSkipValidation(boolean b) {
        this.skipValidation = b;
    }
}
