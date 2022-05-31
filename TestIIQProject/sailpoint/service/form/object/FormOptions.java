/* (c) Copyright 2015 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.service.form.object;

import java.util.Map;

import sailpoint.integration.JsonUtil;
import sailpoint.object.ElectronicSignature;
import sailpoint.tools.GeneralException;
import sailpoint.tools.JsonHelper;

/**
 * Class to hold the data sent from the client used to
 * instantiate the form related classes.
 */
public class FormOptions {

    /**
     * An object representation of the current values
     * bound to the form.
     */
    private Map<String, Object> formData;

    /**
     * The form bean class name.
     */
    private String formBeanClass;

    /**
     * The form bean state JSON string used to instantiate the class.
     */
    private String formBeanStateString;

    /**
     * The form id.
     */
    private String formId;

    /**
     * The parsed form bean state.
     */
    private Map<String, Object> formBeanState;

    /**
     * The configured action of the clicked button.
     */
    private String action;

    /**
     * The action parameter of the clicked button.
     */
    private String actionParameter;

    /**
     * The parameter value of the clicked button.
     */
    private String actionParameterValue;

    /**
     * A map for holding the electronic signature details
     */
    private ElectronicSignature electronicSignature;

    /**
     * Default constructor for FormOptions.
     */
    public FormOptions() {}

    /**
     * Constructs an instance of FormOptions from the specified map.
     *
     * The map should contain the following fields:
     *  - fieldName
     *  - data
     *  - formBeanClass
     *  - formBeanState
     *  - formId
     *  - action
     *  - actionParameter
     *  - actionParameterValue
     *
     * @param data The data used to populate the form options.
     */
    public FormOptions(Map<String, Object> data) {
        // data and formBeanState must be strings
        Object stateObj = data.get("formBeanState");
        if (!(stateObj instanceof String)) {
            stateObj = JsonHelper.toJson(stateObj);
        }

        setFormData((Map)data.get("data"));
        setFormBeanClass((String) data.get("formBeanClass"));
        setFormBeanStateString((String) stateObj);
        setFormId((String) data.get("formId"));

        if (data.containsKey("button")) {
            Map<String, Object> buttonMap = (Map<String, Object>) data.get("button");

            setAction((String) buttonMap.get("action"));
            setActionParameter((String) buttonMap.get("actionParameter"));
            setActionParameterValue((String) buttonMap.get("actionParameterValue"));
        }

        // handle electronic signatures
        if(data.containsKey("electronicSignature")) {
            Map<String,Object> signatureMap = (Map<String, Object>) data.get("electronicSignature");
            String username = (String)signatureMap.get("username");
            String password = (String)signatureMap.get("password");
            setElectronicSignature(new ElectronicSignature(username, password));
        }
    }

    /**
     * Gets the form data JSON string.
     *
     * @return The form data.
     */
    public Map<String, Object> getFormData() {
        return formData;
    }

    /**
     * Sets the form data JSON string.
     *
     * @param formData The form data.
     */
    public void setFormData(Map<String, Object> formData) {
        this.formData = formData;
    }

    public void setFormData(String formDataJson) throws GeneralException {
        try {
            this.formData = (Map) JsonUtil.parse(formDataJson);
        } catch (Exception e) {
            throw new GeneralException("Could not parse json", e);
        }
    }

    /**
     * Gets the form bean class name.
     *
     * @return The form bean class.
     */
    public String getFormBeanClass() {
        return formBeanClass;
    }

    /**
     * Sets the form bean class name.
     *
     * @param formBeanClass The form bean class.
     */
    public void setFormBeanClass(String formBeanClass) {
        this.formBeanClass = formBeanClass;
    }

    /**
     * Gets the form bean state JSON string.
     *
     * @return The form bean state JSON string.
     */
    public String getFormBeanStateString() {
        return formBeanStateString;
    }

    /**
     * Sets the form bean state JSON string.
     *
     * @param formBeanStateString The form bean state JSON string.
     */
    public void setFormBeanStateString(String formBeanStateString) {
        this.formBeanStateString = formBeanStateString;
    }

    /**
     * Gets the parsed form bean state.
     *
     * @return The form bean state.
     * @throws Exception
     */
    public Map<String, Object> getFormBeanState() throws Exception {
        if (formBeanState == null) {
            formBeanState = (Map<String, Object>) JsonUtil.parse(getFormBeanStateString());
        }

        return formBeanState;
    }

    /**
     * Gets the form id.
     *
     * @return The id.
     */
    public String getFormId() {
        return formId;
    }

    /**
     * Sets the form id.
     *
     * @param formId The form id.
     */
    public void setFormId(String formId) {
        this.formId = formId;
    }

    /**
     * Determines if form state exists.
     *
     * @return True if form state exists, false otherwise.
     */
    public boolean hasFormBeanState() throws Exception {
        return getFormBeanState() != null;
    }

    /**
     * Gets the configured action of the clicked button.
     *
     * @return The action.
     */
    public String getAction() {
        return action;
    }

    /**
     * Sets the clicked button action.
     *
     * @param action The action.
     */
    public void setAction(String action) {
        this.action = action;
    }

    /**
     * Gets the action parameter of the clicked button.
     *
     * @return The action parameter.
     */
    public String getActionParameter() {
        return actionParameter;
    }

    /**
     * Sets the clicked button action parameter.
     *
     * @param actionParameter The action parameter.
     */
    public void setActionParameter(String actionParameter) {
        this.actionParameter = actionParameter;
    }

    /**
     * Gets the action parameter value of the clicked button.
     *
     * @return The action parameter value.
     */
    public String getActionParameterValue() {
        return this.actionParameterValue;
    }

    /**
     * Sets the clicked button action parameter value.
     *
     * @param actionParameterValue The action parameter value.
     */
    public void setActionParameterValue(String actionParameterValue) {
        this.actionParameterValue = actionParameterValue;
    }

    public ElectronicSignature getElectronicSignature() {
        return electronicSignature;
    }

    public void setElectronicSignature(ElectronicSignature electronicSignature) {
        this.electronicSignature = electronicSignature;
    }
}