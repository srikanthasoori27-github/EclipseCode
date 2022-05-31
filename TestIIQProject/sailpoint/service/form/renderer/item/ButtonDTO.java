/* (c) Copyright 2009 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.service.form.renderer.item;

import sailpoint.object.Form;

/**
 * Models a basic form button with some additional properties
 * used for forms backed by a sailpoint FormBean.
 *
 * @author: jonathan.bryant@sailpoint.com
 */
public class ButtonDTO {

    //////////////////////////////////////////////////////////////////////
    //
    // Standard Button Properties
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Button text.
     */
    private String text;


    //////////////////////////////////////////////////////////////////////
    //
    // SailPoint FormBean Properties
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Action parameter passed to the backing FormBean instance.
     */
    private String action;

    /**
     * Additional action parameter passed to the backing
     * sailpoint FormBean instance.
     */
    private String actionParameter;

    private String actionParameterValue;

    /**
     * Whether to skip validation when this button is clicked
     */
    private boolean skipValidation;

    public ButtonDTO(Form.Button button) {
        this.text = button.getLabel();
        this.action = button.getAction();
        this.actionParameter = button.getParameter();
        this.actionParameterValue = button.getValue();
        this.skipValidation = button.getSkipValidation();
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getActionParameter() {
        return actionParameter;
    }

    public void setActionParameter(String actionParameter) {
        this.actionParameter = actionParameter;
    }

    public String getActionParameterValue() {
        return actionParameterValue;
    }

    public void setActionParameterValue(String actionParameterValue) {
        this.actionParameterValue = actionParameterValue;
    }

    public boolean getSkipValidation() {
        return this.skipValidation;
    }

    public void setSkipValidation(boolean b) {
        this.skipValidation = b;
    }
}
