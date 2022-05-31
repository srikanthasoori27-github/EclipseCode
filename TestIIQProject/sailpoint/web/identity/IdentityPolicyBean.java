/* (c) Copyright 2010 SailPoint Technologies, Inc., All Rights Reserved. */
/**
 * 
 */
package sailpoint.web.identity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.Terminator;
import sailpoint.object.Configuration;
import sailpoint.object.Field;
import sailpoint.object.Form;
import sailpoint.object.Form.Usage;
import sailpoint.object.IntegrationConfig;
import sailpoint.object.ObjectAttribute;
import sailpoint.object.ObjectConfig;
import sailpoint.object.PropertyInfo;
import sailpoint.object.UIConfig;
import sailpoint.tools.GeneralException;
import sailpoint.tools.JsonHelper;
import sailpoint.tools.Message;
import sailpoint.tools.Message.Type;
import sailpoint.tools.Util;
import sailpoint.web.BaseBean;
import sailpoint.web.BaseObjectBean;
import sailpoint.web.FormEditBean;
import sailpoint.web.form.editor.FormDTO;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.util.MapListComparator;

/**
 * @author peter.holcomb
 *
 */
public class IdentityPolicyBean extends BaseBean {
    private static Log log = LogFactory.getLog(IdentityPolicyBean.class);

    private FormEditBean formEditor;
    public static final String ATT_FORM_EDITOR = "identityPolicyFormEditor";
    public static final String ATT_PASSWORD_DISPLAY = "Password";
    public static final String ATT_PASSWORD_CONFIRM_DISPLAY = "Password Confirmation";
    public static final String ATT_NAME_DISPLAY = "Username";
    private String createAttributes;
    private String updateAttributes;
    // We will want this at some point but for now just use createAttributes for both cases:
    // private String registerAttributes;
    
    private static final Map<Form.Type, String> CONFIG_KEY_MAP; 
    static {
        CONFIG_KEY_MAP = new HashMap<Form.Type, String>();
        CONFIG_KEY_MAP.put(Form.Type.CreateIdentity, Configuration.CREATE_IDENTITY_FORM);
        CONFIG_KEY_MAP.put(Form.Type.UpdateIdentity, Configuration.UPDATE_IDENTITY_FORM);
        CONFIG_KEY_MAP.put(Form.Type.RegisterIdentity, Configuration.REGISTER_FORM);
    }

    public IdentityPolicyBean() 
    {
        if ("true".equals(getRequestParameter(BaseObjectBean.FORCE_LOAD))) {
            removeFromSession();
        }

        formEditor = (FormEditBean)getSessionScope().get(ATT_FORM_EDITOR);
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Actions
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Removes the ATT_FORM_EDITOR key and associated FormEditBean from the session.
     */
    public void removeFromSession() {
        getSessionScope().remove(ATT_FORM_EDITOR);
    }

    public boolean validateCreateForm(Form form)
        throws GeneralException
    {
        boolean containsNameField = false;
        boolean containsPasswordField = false;
        boolean containsPasswordConfirmField = false;

        for (Field field : Util.safeIterable(form.getEntireFields())) {
            if (field.getName().equals(IntegrationConfig.ATT_NAME)) {
                containsNameField = true;
            }

            if (field.getName().equals(IntegrationConfig.ATT_PASSWORD)) {
                containsPasswordField = true;
            }

            if (field.getName().equals(IntegrationConfig.ATT_PASSWORD_CONFIRM)) {
                containsPasswordConfirmField = true;
            }
        }

        /** The policy must have a "name" field, at a minimum, or it is invalid **/
        if (!containsNameField) {
            addMessage(new Message(Type.Error, MessageKeys.ERROR_IDENTITY_PROVISIONING_POLICY_NAME));
            return false;
        }

        Configuration sysConfig = getContext().getObjectByName(Configuration.class, Configuration.OBJ_NAME);
        boolean requirePassword = sysConfig.getBoolean(Configuration.LCM_REQUIRE_PASSWORD_IDENTITY_CREATE);

        /** If "Require passwords on Identity Creation" option is selected, The policy must have a "password" and "confirm" password field **/
        if (requirePassword) {
            if (!containsPasswordField) {
                addMessage(new Message(Type.Error, MessageKeys.ERROR_IDENTITY_PROVISIONING_POLICY_PASSWORD));
                return false;                    
            }
            if (!containsPasswordConfirmField) {
                addMessage(new Message(Type.Error, MessageKeys.ERROR_IDENTITY_PROVISIONING_POLICY_PASSWORD_CONFIRMATION));
                return false;                       
            }
        }

        return true;
    }

    /**
     * @return returns true if a password is required while creating a new identity, false otherwise
     * @throws GeneralException
     */
    public String getPasswordRequired() throws GeneralException{
    	Configuration sysConfig = getContext().getObjectByName(Configuration.class, Configuration.OBJ_NAME);
		return sysConfig.getBoolean(Configuration.LCM_REQUIRE_PASSWORD_IDENTITY_CREATE)?"true":"false";
    }

    /**
     * Gets the form with the specified usage from a list of forms.
     * @param forms The list of forms to check.
     * @param usage The desired usage.
     * @return The form if a match is found, null otherwise.
     */
    public Form getForm(List<Form> forms, Usage usage) {
        for (Form form : Util.safeIterable(forms)) {
            if (usage == form.convertFormType(form.getType())) {
                return form;
            }
        }

        return null;
    }

    /**
     * Update the persisted form whose type corresponds to the configKey.
     * @param type The form type
     * @param source The form
     * @param systemConfig The system configuration object.
     */
    public void updateForm(Form.Type type, Form source, Configuration systemConfig)
        throws GeneralException
    {
        String formKey = CONFIG_KEY_MAP.get(type);
        if (formKey == null) {
            log.warn("No system config setting was found corresponding to forms of type " +
                     type.toString() + " so the edited form was not set in the system configuration.");
            return;
        }

        // For the time being there should only be one form
        // TODO: At some point we will want to be able to pick
        // from and/or edit multiple forms with a given type.
        String previousFormName = (String) systemConfig.get(formKey);

        if (source != null) {
            Form destination = null;

            // Load the form using the previous name in case it was renamed.
            if (previousFormName != null) {
                destination = getContext().getObjectByName(Form.class, previousFormName);
            }

            if (destination == null) {
                destination = new Form();
            }

            // source form DTO
            FormDTO sourceDTO = new FormDTO(source);
            sourceDTO.setType(type);

            // Takes source FormDTO object and merges the
            // changes from this to the destination form.
            sourceDTO.commit(destination);

            // ensure the new form object
            // is committed to the session
            getContext().saveObject(destination);

            systemConfig.put(formKey, destination.getName());
            getContext().saveObject(systemConfig);
        } else {
            // Clear the value out of system config if there was no form.
            systemConfig.put(formKey, null);
            getContext().saveObject(systemConfig);
        }
    }

    /**
     * Delete any forms that were deleted from the UI.
     */
    public void deleteForms() throws GeneralException {
        List<String> deletedForms = this.getFormEditor().getDeletedFormIds();
        if (null != deletedForms) {
            Terminator t = new Terminator(getContext());
            for (String formId : deletedForms) {
                Form form = getContext().getObjectById(Form.class, formId);
                if (null != form) {
                    t.deleteObject(form);
                }
            }
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Utility Methods
    //
    //////////////////////////////////////////////////////////////////////

    private void buildFormEditor() throws GeneralException {

        List<Form> forms = new ArrayList<Form>();

        loadForm(forms, Form.Type.CreateIdentity);
        loadForm(forms, Form.Type.UpdateIdentity);
        loadForm(forms, Form.Type.RegisterIdentity);

        formEditor = new FormEditBean(forms);
        formEditor.generateFormToFormDtoMap(forms);

        formEditor.setUsages(Arrays.asList("CreateIdentity", "UpdateIdentity", "Register"));

        getSessionScope().put(ATT_FORM_EDITOR, formEditor);
    }

    /**
     * Load the form of the given type and (if found) store it and its usage in
     * the forms and usages lists.
     * 
     * @param  forms   The List to which to add the Form.
     * @param  type    The type of form to load.
     */
    private void loadForm(List<Form> forms, Form.Type type)
        throws GeneralException {

        Form form = getForm(type);
        if (null != form) {
            forms.add(form);
        }
    }

    /**
     * Return the Form mapped in system config for the given type, or null if
     * no Form is found.
     */
    private Form getForm(Form.Type type) throws GeneralException {
        Form form = null;
        String formName = (String) Configuration.getSystemConfig().get(CONFIG_KEY_MAP.get(type));
        if (formName != null) {
            form = getContext().getObjectByName(Form.class, formName) ;
        }
        return form;
    }

    public String getAvailableAttributes()
        throws GeneralException
    {
        if (isForm(Usage.UpdateIdentity)) {
            return getUpdateAttributes();
        } else if (isForm(Usage.Register)) {
            return getRegisterAttributes();
        } else {
            return getCreateAttributes();
        }
    }

    private boolean isForm(Usage usage)
        throws GeneralException
    {
        if (formEditor != null) {
            if (usage == formEditor.getUsageEnum()) {
                return true;
            }

            FormDTO formDTO = formEditor.getFormDTO();
            if (formDTO != null) {
                return usage == formDTO.getUsage();
            }
        }

        return false;
    }

    public String getUpdateAttributes()
    	throws GeneralException 
    {
    	if (updateAttributes == null) {
    		List<Map<String, Object>> attributes = new ArrayList<Map<String, Object>>();
    		UIConfig uiConfig = getUIConfig();
            ObjectConfig idConfig = getIdentityConfig();
            
            if (uiConfig != null && idConfig != null) {

                List<ObjectAttribute> atts = idConfig.getEditableAttributes();
                
                if (atts != null) {
                    for (ObjectAttribute att : atts) {
                        Map<String,Object> attribute = new HashMap<String,Object>();
                        attribute.put("name", att.getName());
                        attribute.put("displayName", att.getDisplayableName());
                        attribute.put("type",att.getType());
                        attributes.add(attribute);
                    }
                }
            }

            Collections.sort(attributes, new MapListComparator("name", true));

    		updateAttributes = JsonHelper.toJson(attributes);
    	}
    	
    	return updateAttributes;	
    }
    
    /** Returns a json version of the list of attributes available to the create
     * form to be used to create new fields in the form
     * @return
     * @throws GeneralException
     */
    public String getRegisterAttributes() throws GeneralException {
        // For now just treat this the same as a creation policy
        return getCreateAttributes();
    }

    /** Returns a json version of the list of attributes available to the create
     * form to be used to create new fields in the form
     * @return
     * @throws GeneralException
     */
    public String getCreateAttributes() throws GeneralException {
        if (createAttributes == null) {
            List<Map<String,Object>> attributes = new ArrayList<Map<String,Object>>();
            UIConfig uiConfig = getUIConfig();
            ObjectConfig idConfig = getIdentityConfig();
            
            if (uiConfig != null && idConfig != null) {

                List<ObjectAttribute> atts;
                /** This is an identity create **/
                atts = idConfig.getObjectAttributes();

                if (atts != null) {
                    for (ObjectAttribute att : atts) {
                        /** Leave the system attributes to the system **/
                        if (!att.isSystem()) {
                            Map<String,Object> attribute = new HashMap<String,Object>();
                            attribute.put("name", att.getName());
                            attribute.put("displayName", att.getDisplayableName());
                            attribute.put("type",att.getType());
                            attributes.add(attribute);
                        }
                    }
                }

                Collections.sort(attributes, new MapListComparator("name", true));

                /** Add Password/Username fields **/                
                Map<String,Object> passwordConfirm = new HashMap<String,Object>();
                passwordConfirm.put("name", IntegrationConfig.ATT_PASSWORD_CONFIRM);
                passwordConfirm.put("displayName", ATT_PASSWORD_CONFIRM_DISPLAY);
                passwordConfirm.put("type",PropertyInfo.TYPE_SECRET);
                attributes.add(0,passwordConfirm);

                Map<String,Object> password = new HashMap<String,Object>();
                password.put("name", IntegrationConfig.ATT_PASSWORD);
                password.put("displayName", ATT_PASSWORD_DISPLAY);
                password.put("type",PropertyInfo.TYPE_SECRET);
                attributes.add(0,password);

                Map<String,Object> username = new HashMap<String,Object>();
                username.put("name", IntegrationConfig.ATT_NAME);
                username.put("displayName", ATT_NAME_DISPLAY);
                username.put("type",PropertyInfo.TYPE_STRING);
                attributes.add(0,username);
            }
            createAttributes = JsonHelper.toJson(attributes);
        }
        return createAttributes;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Getters/Setters
    //
    //////////////////////////////////////////////////////////////////////

    public FormEditBean getFormEditor() throws GeneralException {
        if(formEditor==null) {
            buildFormEditor();
        }
        return formEditor;
    }

    public void setFormEditor(FormEditBean formEditor) {
        this.formEditor = formEditor;
    }
}
