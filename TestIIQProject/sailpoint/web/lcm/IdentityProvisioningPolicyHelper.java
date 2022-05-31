package sailpoint.web.lcm;

import sailpoint.api.SailPointContext;
import sailpoint.object.*;
import sailpoint.object.Form.Section;
import sailpoint.object.ProvisioningPlan.AccountRequest;
import sailpoint.service.form.FormMerger;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.UserContext;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.util.IdentityAttributeComparator;
import sailpoint.web.util.WebUtil;

import java.util.*;

/**
 * Class that creates and populates the Identity Provisioning Forms for the
 * create and edit policies
 */
public class IdentityProvisioningPolicyHelper {
    private final SailPointContext context;
    private final UserContext userContext;

    /**
     * Constructor
     * @param context Context to look stuff up in
     */
    public IdentityProvisioningPolicyHelper(SailPointContext context, UserContext userContext) {
        this.context = context;
        this.userContext = userContext;
    }

    /**
     * Creates the master create identity form.  This has some somewhat surprising logic.
     * If a create policy form is defined it is loaded then whatever we have for an update policy form is added.
     * If there is no create policy then we create the default create policy form which contains name, password,
     * confirm password fields and fields for all the searchable identity attributes.
     *
     * @return Create policy form
     */
    public Form createMasterCreateIdentityForm() throws GeneralException {
        Form form = getForm(Form.Type.CreateIdentity);
        if (form != null) {
            Form updateForm = getForm(Form.Type.UpdateIdentity);
            if (updateForm != null) {
                FormMerger merger = new FormMerger(form, updateForm);
                merger.merge();
            }
        }

        if(form == null) {
            form = createDefaultCreatePolicyForm();
        }

        return form;
    }

    /**
     * Create the update policy form.
     * If a update policy is defined then we use that.  Otherwise we create a form with fields for all the editable
     * identity attributes
     *
     * @return The edit identity policy form
     */
    public Form createMasterUpdateIdentityForm() throws GeneralException {
        Form form = getForm(Form.Type.UpdateIdentity);
        if(form == null) {
            form = createDefaultUpdatePolicyForm();
        }
        return form;
    }

    /**
     * The attribute values from the identity and the account request are merged and then adde to the form
     * @param form The form to populate
     * @param identity The identity
     * @param currentRequests Any previous requests
     */
    public void populateForm(Form form, Identity identity, List<AccountRequest> currentRequests) throws GeneralException {
        Map<String, Object> identityAttrs = identityToMap(identity);
        overlayCurrentRequests(identityAttrs, currentRequests);

        // Populate the form with the data.
        for (Map.Entry<String,Object> entry : identityAttrs.entrySet()) {
            form.setFieldValue(entry.getKey(), entry.getValue());
        }
    }

    /**
     * Creates the default create policy form.  Has name maybe password and confirm password and all the searchable
     * identity attributes
     * @return The default create identity policy form
     */
    private Form createDefaultCreatePolicyForm() throws GeneralException {
        Form form = createFormStub();

        Section mainSection = new Section();
        form.add(mainSection);

        mainSection.add(createIdentityNameField());

        if (requirePasswordOnCreateIdentity()) {
            mainSection.add(createIdentityPasswordField());
            mainSection.add(createIdentityPasswordConfirmField());
        }

        List<ObjectAttribute> attrs = getDefaultCreatePolicyAttributes();
        addAttributesToSection(attrs, mainSection);

        return form;
    }

    /**
     * Returns all the searchable identity attributes
     * @return List of all the searchable identity attributes
     */
    private List<ObjectAttribute> getDefaultCreatePolicyAttributes() throws GeneralException {
        List<ObjectAttribute> attributes = new ArrayList<ObjectAttribute>();
        ObjectConfig idConfig = getIdentityConfig();

        if (idConfig != null) {
            copyAttributes(attributes, idConfig.getSearchableAttributes());
        }
        return attributes;
    }

    /**
     * Creates the default update policy form.  This consists of all the editable identity attributes
     * @return the default update identity policy form
     */
    private Form createDefaultUpdatePolicyForm() throws GeneralException {
        Form form = createFormStub();
        Section mainSection = new Section();
        form.add(mainSection);

        List<ObjectAttribute> attrs = getDefaultUpdatePolicyAttributes();
        addAttributesToSection(attrs, mainSection);

        return form;
    }

    /**
     * Returns all the editable identity attributes
     * @return All the editable identity attributes
     */
    private List<ObjectAttribute> getDefaultUpdatePolicyAttributes() throws GeneralException {
        List<ObjectAttribute> attributes = new ArrayList<ObjectAttribute>();
        ObjectConfig idConfig = getIdentityConfig();

        if (idConfig != null) {
            copyAttributes(attributes, idConfig.getEditableAttributes());
        }
        return attributes;
    }


    /**
     * Common form stub
     * @return Common form stub
     */
    private Form createFormStub() {
        Form form = new Form();
        form.setName("Default Attributes Form");
        return form;
    }

    /**
     * Adds fields for all the attributes to the section
     * @param attrs The attributes to add fields for
     * @param mainSection The Section to add fields to
     */
    private void addAttributesToSection(List<ObjectAttribute> attrs, Section mainSection) {
        for (ObjectAttribute attr : attrs) {
            mainSection.add(createFieldFromAttribute(attr));
        }
    }

    /**
     * Adds srcAttrs to destAttrs
     * @param destAttrs Destination list
     * @param srcAttrs Source list
     */
    private void copyAttributes(List<ObjectAttribute> destAttrs, List<ObjectAttribute> srcAttrs) {
        Collections.sort(srcAttrs, new IdentityAttributeComparator());

        if (srcAttrs != null) {
            destAttrs.addAll(srcAttrs);
        }
    }

    /**
     * Returns true if password is required on create
     * @return true if password is required on create
     */
    private boolean requirePasswordOnCreateIdentity() throws GeneralException {
        return Util.otob(WebUtil.getSystemConfigurationValue(Configuration.LCM_REQUIRE_PASSWORD_IDENTITY_CREATE, true));
    }

    /**
     * Creates the name field for the default create form
     * @return the name field for the default create form
     */
    private Field createIdentityNameField() {
        return createRequiredField("name", MessageKeys.LCM_IDENTITY_NAME, false);
    }

    /**
     * Creates the password field for the default create form
     * @return the password field for the default create form
     */
    private Field createIdentityPasswordField() {
        return createRequiredField(IntegrationConfig.ATT_PASSWORD, MessageKeys.PASSWORD, true);
    }

    /**
     * Creates the confirm password field for the default create form
     * @return the confirm password field for the default create form
     */
    private Field createIdentityPasswordConfirmField() {
        return createRequiredField(IntegrationConfig.ATT_PASSWORD_CONFIRM, MessageKeys.CONFIRM_PASSWORD, true);
    }

    /**
     * Creates a required field needed for the default create policy form
     * @param fieldName The name of the field
     * @param key The display name of the field
     * @param isPassword If the field is a password field
     * @return The field
     */
    private Field createRequiredField(String fieldName, String key, boolean isPassword) {
        Field field = createField(fieldName, getMessage(key), getMessage(key), ObjectAttribute.TYPE_STRING, false, true);
        if(isPassword) {
            field.setType("secret");
            field.addAttribute("inputType", "password");
        }

        return field;
    }

    /**
     * Create a field from an Object Attribute
     * @param attr The object attribute to make a field of
     * @return The field
     */
    private Field createFieldFromAttribute(ObjectAttribute attr) {
        String type = ObjectAttribute.TYPE_STRING;
        if (null != attr.getType()) {
            type = attr.getType();
        }
        String prompt = attr.getDisplayableName(userContext.getLocale());

        Field field = createField(attr.getName(), attr.getDisplayName(), prompt, type, attr.isMulti(), attr.isRequired());
        
        //adding allowed values for Identity Type field.
        if (Identity.ATT_TYPE.equals(attr.getName())) {
            List<Object> values = new ArrayList<Object>();

            for (IdentityTypeDefinition def : Util.safeIterable(Identity.getObjectConfig().getIdentityTypesList())) {
                List<String> option = new ArrayList<String>();
                option.add(def.getName());
                option.add(def.getDisplayableName());
                values.add(option);
            }
            field.setAllowedValues(values);
        }
        return field;
    }

    /**
     * Wrap the field constructor in something that takes all the parameters
     * @param name The name of the field
     * @param displayName The display name of the field
     * @param prompt The prompt for the field
     * @param type The type of the field
     * @param isMulti If the field is multi
     * @param isRequired If the field is required
     * @return The new field
     */
    private Field createField(String name, String displayName, String prompt, String type, boolean isMulti, boolean isRequired) {
        Field field = new Field();
        field.setName(name);
        field.setDisplayName(displayName);
        field.setPrompt(prompt);
        field.setType(type);
        field.setMulti(isMulti);
        field.setRequired(isRequired);
        return field;
    }

    /**
     * Return localized message
     * @param key Key of message to lookup
     * @return Localized message
     */
    String getMessage(String key) {
        Message msg = new Message(key);
        return msg.getLocalizedMessage(userContext.getLocale(), userContext.getUserTimeZone());
    }

    /**
     * Converts an Identity into a key value map of identity attributes
     * @param identity The identity to map
     * @return The map representation of the identity
     */
    private Map<String,Object> identityToMap(Identity identity) throws GeneralException {
        Map<String,Object> attrs = new HashMap<String,Object>();

        List<ObjectAttribute> attributes = getIdentityConfig().getObjectAttributes();
        if (!Util.isEmpty(attributes) && (null != identity)) {
            // Load all of the ObjectAttributes into the map.  Non-editable
            // attributes will be filtered out later.
            for (ObjectAttribute attribute : attributes) {
                if (identity.isExtendedIdentityType(attribute)) {
                    Identity relatedIdentity = identity.getExtendedIdentity(attribute.getExtendedNumber());
                    attrs.put(attribute.getName(), relatedIdentity == null? null : relatedIdentity.getName());
                } else {
                    attrs.put(attribute.getName(), identity.getAttribute(attribute.getName()));
                }
            }
        }

        return attrs;
    }

    /**
     * Merges the identity map with any previously existing account requests
     * @param attrs The identity map
     * @param currentRequests Any previous requests
     */
    private void overlayCurrentRequests(Map<String, Object> attrs, List<AccountRequest> currentRequests) {
        if(currentRequests!=null) {
            for(AccountRequest request : currentRequests) {
                if (null != request.getAttributeRequests()) {
                    for (ProvisioningPlan.AttributeRequest attrReq : request.getAttributeRequests()) {
                        attrs.put(attrReq.getName(), attrReq.getValue());
                    }
                }
            }
        }
    }

    /**
     * Wrapper around fetching the identity config object
     * @return The Identity ObjectConfig
     */
    private ObjectConfig getIdentityConfig() throws GeneralException {
        return ObjectConfig.getObjectConfig(Identity.class);
    }

    /**
     * Fetches the configured provisioning policy form for the passed type
     * @param type The type of form to fetch
     * @return The configured form if there is one
     */
    private Form getForm(Form.Type type) throws GeneralException {
        Form form = null;
        Configuration systemConfig = Configuration.getSystemConfig();
        String formKey = null;
        if (type == Form.Type.UpdateIdentity) {
            formKey = Configuration.UPDATE_IDENTITY_FORM;
        } else if (type == Form.Type.CreateIdentity) {
            formKey = Configuration.CREATE_IDENTITY_FORM;
        } else if (type == Form.Type.RegisterIdentity) {
            formKey = Configuration.REGISTER_FORM;
        }

        if (formKey == null) {
            // Fall back on legacy functionality if we don't have a system config entry for this form
            Iterator<Form> matchingForms =
                    context.search(Form.class, new QueryOptions(Filter.eq("type", type)));
            if (matchingForms != null && matchingForms.hasNext()) {
                form = matchingForms.next();
                Util.flushIterator(matchingForms);
            }
        } else {
            String formName = (String) systemConfig.get(formKey);
            if (formName != null) {
                form = context.getObjectByName(Form.class, formName);
            }
        }

        if (form != null) {
            form = (Form)form.deepCopy((Resolver)context);
        }

        return form;
    }
}
