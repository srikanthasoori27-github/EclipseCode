/* (c) Copyright 2009 SailPoint Technologies, Inc., All Rights Reserved. */
/*
 * FormRenderer manages the presentation of a Form object as an JSF/Extjs/AngularJS page.
 * The services provided are:
 *
 *  Rendering
 *    The conversion of a Form to JSF/Ext/AngularJS.
 *
 *  Assimilation
 *    The assimilation of JSF/Ext/AngularJS post data back into a Form.
 *
 *  Dynamic Fields
 *    Evaluation of field scripts in an Ajax request from the 
 *    form being presented.
 *
 *  Validation
 *    Evaluation of the field validation scripts after assimilation.
 *
 * FormRenderer is a utility class can handle the JSF page actions.
 * WorkItemFormBean is one of these consumers, LCM's AttributesRequestBean
 * is another.  So while FormRenderer is usually used in the context of
 * a WorkflowSession that is not required.  The FormRenderer may
 * also be used by one of our REST interface classes such as DynamicFieldResource.
 *
 * Rendering and Assimilation must be encapsulated here because they
 * are both related to the Ext rendering.
 *
 * Dynamic field evaluation must pass through here because the Ajax request
 * contains post data that must be assimilated before running the field scripts.
 * The actual running of the filed scripts is done by Formicator, where
 * most non-presentation form logic is encapsulated.
 *
 * Validation script evaluation was originally done here but that was mostly
 * moved to Formicator in 5.5.  What FormRenderer adds to validation is the
 * localization of the Message objects returned by Formicator since this
 * requires access to the HttpSession to know the browser locale.
 *
 * Issue 1: FormRenderer access to WorkflowSession
 * 
 * This is required to provide context for script evaluation.  All WorkItem
 * variables overlayed with form fields form the base Map of script arguments.
 * We could avoid this by giving FormRenderer a Map of arguments but that's
 * just one more thing to save on the HTTP session.  
 * 
 * If FormRenderer is used without a WorkflowSession, then how is extra state 
 * passed in?  This could be avoided by factoring out script evaluation logic
 * but whatever class did that would have the same issue, where to get
 * script context.
 * 
 * Issue 2: DynamicFieldResource depends on WorkflowSession
 *
 * The Ajax request handler for dynamic fields currently assumes
 * there is a WorkflowSession stored on the HTTP session.  This won't
 * work for forms rendered outside of workflows.  Currently the only example
 * of that is the Identity create form in LCM (or is the attributes 
 * request form?).
 *
 */

package sailpoint.service.form.renderer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.Formicator;
import sailpoint.api.SailPointContext;
import sailpoint.object.Field;
import sailpoint.object.Form;
import sailpoint.object.FormItem;
import sailpoint.object.Identity;
import sailpoint.service.form.renderer.creator.FormItemDTOCreator;
import sailpoint.service.form.renderer.item.ButtonDTO;
import sailpoint.service.form.renderer.item.FormItemDTO;
import sailpoint.tools.GeneralException;
import sailpoint.tools.JsonHelper;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.FormBean;
import sailpoint.web.extjs.Component;
import sailpoint.web.messages.MessageKeys;


/**
 * This class controls the rendering and processing of a Form object.
 * In order to use this class you should include formRenderer.xhtml
 * which instantiates a javascript sailpoint.form.FormPanel object
 * and establishes the binding between the page and the methods on
 * your FormRenderer instance.
 */
public class FormRenderer {

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    private static final Log log = LogFactory.getLog(FormRenderer.class);

    public static final String ATT_CURRENT_FIELD = "currentField";
    public static final String ATT_TAB_DIRECTION = "tabDir";

    /**
     * Dummy value sent to the browser for secret fields. We then compare
     * this value to the submitted value to determine if the user has
     * changed the value.
     */
    public static final String SECRET_DUMMY_VALUE = "tsewraf";

    /**
     * Default field that is used for the value when a SailPoint object form field
     * is posted. When using the Model as the backing datasource, we will default
     * this to name when possible.
     */
    public static final String DEFAULT_POST_VALUE = "id";

    /**
     * Form ID. This is used on the client side to tie together
     * all the DOM elements. If an ID is not supplied we will
     * auto-generate one.
     */
    protected String id;

    /**
     * Underlying form object.
     */
    protected Form form;

    /**
     * Data submitted by the client. This data is posted as a single
     * hidden field in JSON format.
     */
    protected Map<String, Object> postData;

    /**
     * List of error messages to be sent to the client. These messages
     * will be displayed at the head of the form.
     */
    protected List<String> errorMessages;

    /**
     * Map of field-specific validation errors. The key is
     * the field name and the value is the error string. The
     * error will be displayed at the head of the form and
     * inline with the associated field.
     */
    protected Map<String, String> fieldValidation;

    /**
     * Form action. This is coming from the button which was clicked.
     */
    protected String action;

    /**
     * Form action parameter. This is coming from the button which was clicked.
     */
    protected String actionParameter;

    protected String actionParameterValue;

    /**
     * If this FormRenderer is being persisted in the session we may want to manually
     * manage the context.  This variable provides the means to do so.  As long as
     * this remains unset we'll fall back to the context managed by BaseBean.
     * If this gets set this context will be used instead.
     */
    protected SailPointContext manualContext;

    protected SailPointContext _context;

    protected FormRendererUtil _rendererUtil;

    /**
     * Keep track of the tab direction so when the form refreshes we can
     * focus the appropriate field.
     */
    protected String tabDir;

    /**
     * Name of the field that should have focus when the form is displayed.
     */
    protected String currentField;

    protected Form.Section currentSection;

    //////////////////////////////////////////////////////////////////////
    //
    // Constructor
    //
    //////////////////////////////////////////////////////////////////////

    public FormRenderer() {
    }

    /**
     * Construct a FormRenderer for the given form.
     */
    public FormRenderer(Form form, FormBean formBean, Locale locale,
                        SailPointContext context, TimeZone tz) {
        _context = context;
        String formBeanClass = null;
        Map<String,Object> formBeanState = null;
        if (null != formBean) {
            formBeanClass = formBean.getClass().getName();
            formBeanState = formBean.getFormBeanState();
        }
        this._rendererUtil = new FormRendererUtil(_context, form, formBeanClass, formBeanState, locale, tz);
        init(form);
    }

    public FormRenderer(FormBean formBean, Locale locale, SailPointContext context, TimeZone tz) {
        _context = context;
        this._rendererUtil = new FormRendererUtil(_context, null, formBean.getClass().getName(), formBean.getFormBeanState(), locale, tz);
    }

    public SailPointContext getManualContext() {
        return manualContext;
    }

    /**
     * Replaces the SailPointContext.  Only needed when this is being cached in the session.
     * If the manual context remains unset FormRender will continue to use the originally provided context.
     * @param manualContext SailPointContext to use
     */
    public void setManualContext(SailPointContext manualContext) {
        this.manualContext = manualContext;
        this._rendererUtil.setContext(manualContext);
    }

    /**
     * Returns the current context
     *
     * @return
     */
    public SailPointContext getContext() {
        if (manualContext == null) {
            return _context;
        } else {
            return manualContext;
        }
    }

    public void init(Form form) {
        this.form = form;

        if (form != null) {
            // Forms need a stable ID.  Assign one if we need to.
            if (null == form.getId()) {
                String id = java.util.UUID.randomUUID().toString();
                id = id.replaceAll("-", "");
                form.setId(id);
            }

            this.id = form.getId();
            this._rendererUtil.setForm(form);
        }
    }

    public String getCurrentField() {
        return currentField;
    }

    public void setCurrentField(String currentField) {
        this.currentField = currentField;
    }

    public String getTabDir() {
        return tabDir;
    }

    public void setTabDir(String td) {
        this.tabDir = td;
    }

    public Form.Section getCurrentSection() {
        return currentSection;
    }

    public void setCurrentSection(Form.Section currentSection) {
        this.currentSection = currentSection;
    }

    /**
     * Unit test interface for injecting post data when we're not
     * actually being used interactively in an app server.
     * Not that we have to use the post convention of only string values
     * which will test value conversion.
     */
    public void addPostData(String name, String value) {
        if (name != null) {
            if (postData == null)
                postData = new HashMap<String, Object>();
            postData.put(name, value);
        }
    }

    public void addPostData(Map<String, Object> data) {
        if (data != null) {
            Iterator<String> it = data.keySet().iterator();
            while (it.hasNext()) {
                String key = it.next();
                Object value = data.get(key);
                // NOTE: Technically we should be doing the
                // same sort of outbound value transformation
                // that we do when we do real rendering, for the
                // current unit tests just leaving everything String
                // is fine, but if we have to support other types
                // we should proabably keep this somewhere outside of postData
                String svalue = (value != null) ? value.toString() : null;
                addPostData(key, svalue);
            }
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Post Data Assimilation
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Copy user-submitted values to internal form object.
     * This must be called once prior to doing validation.
     */
    public void populateForm() throws GeneralException {
        // jsl - eventually need to support sectionless forms
        if (form != null && form.getSections() != null) {
            for (Form.Section section : form.getSections()) {
                populateSection(section);
            }
        }

        if (form != null && form.getButtons() != null) {
            for (Form.Button b : form.getButtons()) {
                b.setClicked(isClickedButton(b));
            }
        }
    }

    private boolean isClickedButton(Form.Button button) {
        if (button != null) {
            return Util.nullSafeEq(button.getAction(), this.getAction()) &&
                    Util.nullSafeEq(button.getParameter(), this.getActionParameter()) &&
                    Util.nullSafeEq(button.getValue(), this.getActionParameterValue());
        }

        return false;
    }

    private void populateSection(Form.Section section) {
        if (section.getItems() != null) {
            for (FormItem item : section.getItems()) {
                if (item instanceof Field) {
                    Field field = (Field) item;
                    if (!field.isReadOnly() && section.isInteractive())
                        field.setValue(getSubmittedFormValue(field));
                } else if (item instanceof Form.Section) {
                    if (((Form.Section) item).isInteractive()) {
                        populateSection((Form.Section) item);
                    }
                }
            }
        }
    }

    /**
     * Determine the value to place in the given form Field.
     * This first gets the posted value then applies various transformations.
     *
     * @param field
     */
    private Object getSubmittedFormValue(Field field) {
        Object value = getPostData(field);
        if (value != null) {
            if (field.isMulti()) {
                List list = convertMultiValue(field, value);
                if (Field.FORMAT_CSV.equals(field.getFormat()) && list != null) {
                    return Util.listToCsv(list, true);
                } else {
                    return list;
                }
            } else {
                return convertSingleValue(field, value, _rendererUtil.getTimezone());
            }
        }

        return value;
    }

    private Object getPostData(Field field) {

        Object fieldValue = null;
        String fieldName = field.getName();

        Class spClass = field.getTypeClass();

        // Convert the key if this is an identity field coming from the UI
        // jsl - I don't know what is going on here but I don't like 
        // context sensitive shenanigans like this.  Let's discuss...
        Map<String, Object> postData = getData();
        if (postData != null && !postData.containsKey(fieldName)
                && Identity.class.equals(spClass)
                && getForm() != null) {
            fieldName = getForm().getName() + "-form-" + fieldName + "-field";
        }

        if (postData != null && postData.containsKey(fieldName)) {

            fieldValue = postData.get(fieldName);

            // TODO:  Transformations on the field value also take place in the 
            // sailpoint.form.Field.convertSimpleTypeValue() method.
            // We should consolidate the logic for the sake of consistency.
            // As it is now, changes made here may interfere with activities
            // there. --Bernie

            // If this is a secret field and the value from the form
            // does not differ from the dummy value, the input has not changed.
            if (Field.TYPE_SECRET.equals(field.getType()) &&
                    SECRET_DUMMY_VALUE.equals(fieldValue)) {
                fieldValue = field.getValue();
            }

            if (field.isMulti() && (fieldValue instanceof String)) {
                // Formerly used csvToList here but what the default component
                // actually submits is a newline deliminted string.  We also
                // need to allowe values with commas so people can enter DNs.
                // If someone uses a custom renderer that actually posts
                // csvs there will need to be awareness of that here...
                // On windows the delimiters will be both \r and \n,       
                // on Unix just \n
                List<String> list = null;
                String[] tokens = ((String) fieldValue).split("\n");
                if (tokens != null) {
                    for (int i = 0; i < tokens.length; i++) {
                        String token = tokens[i];
                        // trim surrounding whitespace so we can get
                        // rid of the extra \r on windows 
                        token = token.trim();
                        if (token.length() > 0) {
                            if (list == null)
                                list = new ArrayList<String>();
                            list.add(token);
                        }
                    }
                }
                fieldValue = list;
            }
        }

        return fieldValue;
    }

    /**
     * Converts a multi values property into a list of the
     * type specified by the field.
     *
     * @param field
     * @param value
     * @return List of items of the correct type or null
     */
    private List convertMultiValue(Field field, Object value) {

        List result = null;

        if (value != null && !"".equals(value)) {

            result = new ArrayList();

            if (value instanceof List) {
                for (Object v : (List) value) {
                    result.add(convertSingleValue(field, v));
                }
            } else {
                // auto upconvert scalar to list, this may not happen
                // with Ext but is generally allowed
                result.add(convertSingleValue(field, value));
            }

            if (result.isEmpty())
                result = null;
        }

        return result;
    }

    /**
     * Converts a single value into the appropriate type specified by
     * the field. Note the multi valued fields should use convertMultiValue().
     * If coercion fails, leave the value alone and it will be caught
     * later during validation.
     * <p/>
     * jsl - I don't fully undersatnd the purpose of this, is it because
     * Ext always posts Strings?  An alternative do this would be to
     * have Formicator.validate do the necessary coercion since that's
     * where we're going to be returning type errors anyway and we have
     * to go through much the same logic.
     */
    private Object convertSingleValue(Field field, Object value) {
        return convertSingleValue(field, value, null);
    }

    /**
     * Converts a single value into the appropriate type specified by
     * the field. Note the multi valued fields should use convertMultiValue().
     * If coercion fails, leave the value alone and it will be caught
     * later during validation.
     * <p/>
     * jsl - I don't fully undersatnd the purpose of this, is it because
     * Ext always posts Strings?  An alternative do this would be to
     * have Formicator.validate do the necessary coercion since that's
     * where we're going to be returning type errors anyway and we have
     * to go through much the same logic.
     */
    private Object convertSingleValue(Field field, Object value, TimeZone userTimeZone) {

        if (value == null || "".equals(value)) {
            // collapse "" to null
            return null;
        }

        String type = field.getType();
        boolean parseError = false;

        if (field.getTypeClass() != null) {
            value = _rendererUtil.getObjectValue(field, value);
        } else {
            try {
                value = Field.convertSimpleTypeValue(type, value, userTimeZone);
            } catch (Exception e) {
                parseError = true;
            }
        }

        // If we couldn't convert a String to another type, just
        // return the original value and let Formicator.validate()
        // return the appropriate error message.
        // Do we really need an extra level of logging?
        if (parseError) {
            log.info("Invalid value for field: " + field.getName() +
                    " = " + value);
        }

        return value;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Validation
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Calculate the allowed values for a field making the latest form
     * field values available to the script.
     * <p/>
     * This is the interface called by DynamicFieldResource.
     * Given a field name, lookup the Field and calculate the allowed values.
     * <p/>
     * FormRenderer will have been constructed with a WorkflowSession and
     * initialized with posted form data by first calling setDataJson()
     * and populateForm().
     */
    public List calculateAllowedValues(String fieldName, Map<String, Object> args)
            throws GeneralException {

        List allowed = null;
        Field field = this.form.getField(fieldName);

        if (field != null) {
            // Formicator encapsulates all script running
            Formicator cator = new Formicator(getContext());
            allowed = cator.getAllowedValues(this.form, field, args);
        }

        return allowed;
    }

    /**
     * Validate user submitted data.
     * Returns true if all form fields are valid.
     * If false is returned this.fieldValidation will have a set of
     * field-speific erorr messages.
     * <p/>
     * fieldValidation is a Map whose key is a Field name and whose
     * value is a String containing a localized error messsage.
     * TODO: Fields can in theory have more than one error message,
     * if that happens they get concatenated and seperated by a comma
     * in fieldValidation which isn't very good rendering.  Need to
     * think about this and possibly add more support at the Javascript level.
     * <p/>
     * Most of the validation logic has been moved to Formicator
     * to better encapsulate field script evaluation.  It first checks that
     * the field value matches the field type (numeric, date, etc.), then
     * checks for missing required fields, checks the value against the
     * allowed values list, and finally runs the validation script.
     */
    public boolean validate(Map<String, Object> args) throws GeneralException {

        this.fieldValidation = new HashMap<String, String>();
        // Hijacking the error messages for hidden validation errors.
        // It's not used for anything else right now.
        if (this.errorMessages == null) {
            this.errorMessages = new ArrayList<String>();
        } else {
            this.errorMessages.clear();
        }

        if (this.form != null) {

            Formicator cator = new Formicator(getContext());
            Map<String, List<Message>> errors = cator.validate(form, args);

            if (errors != null && errors.size() > 0) {

                // localize the errors

                Iterator<String> it = errors.keySet().iterator();
                while (it.hasNext()) {
                    String field = it.next();
                    String error = null;
                    List<Message> fieldErrors = errors.get(field);
                    if (fieldErrors != null) {
                        for (Message msg : fieldErrors) {
                            String lmsg = msg.getLocalizedMessage(_rendererUtil.getLocale(), _rendererUtil.getTimezone());
                            if (error == null)
                                error = lmsg;
                            else {
                                // TODO: need better support for multiple
                                // error messages...
                                error = error + ", " + lmsg;
                            }
                        }
                    }
                    if (error != null) {
                        this.fieldValidation.put(field, error);
                        Form form = this.getForm();
                        if (form != null) {
                            Field errorSource = form.getField(field);
                            if (errorSource != null && errorSource.isHidden()) {
                                String dependencies = errorSource.getDependencies();
                                // Report dependencies in addition to the validation failure when they are available.  
                                // If not just give dump out the validation failure.
                                if (!Util.isNullOrEmpty(dependencies)) {
                                    this.errorMessages.add(_rendererUtil.localizedMessage(MessageKeys.FORM_PANEL_VALIDATION_HIDDEN, field, error, dependencies));
                                } else {
                                    this.errorMessages.add(_rendererUtil.localizedMessage(MessageKeys.FORM_PANEL_VALIDATION_HIDDEN_NO_DEPS, field, error));
                                }
                            } else {
                                this.errorMessages.add(error);
                            }
                        }
                    }
                }
            }
        }

        // return true if no errors
        return (this.fieldValidation.size() == 0);
    }

    public void addFieldValidation(String field, String message) {
        if (fieldValidation == null)
            fieldValidation = new HashMap<String, String>();

        // When checking the field string, an empty string indicates this
        // is some kind of global validation message, not associated with
        // a particular field
        boolean isGlobal = "".equals(field);

        if (!isGlobal && fieldValidation.containsKey(field)) {
            String currentValidation = fieldValidation.get(field);
            fieldValidation.put(field, currentValidation + "," + message);
        } else if (isGlobal) {
            fieldValidation.put("__global" + Util.uuid(), message);
        } else {
            fieldValidation.put(field, message);
        }


    }

    //////////////////////////////////////////////////////////////////////
    //
    // Rendering
    //
    //////////////////////////////////////////////////////////////////////

    public FormDTO createDTO() throws GeneralException {
        if (form == null || this.getId() == null) {
            return null;
        }
        FormDTO formDTO = new FormDTO(this.getId(), form.getName(), _rendererUtil.localizedMessage(form.getTitle()));
        return this.decorateDTO(formDTO);
    }

    /**
     * A overrideable method for decorating a form dto.  Takes the form and sets all of the important fields on the
     * form dto.
     *
     * @param formDTO Takes in a formDTO and decorates it with buttonDTOs and sectionDTOs
     * @return FormDTO the updated formDTO object with buttons and sections
     */
    protected FormDTO decorateDTO(FormDTO formDTO) throws GeneralException {

        // Set the title from pageTitle attribute if it exists and the formDTO doesn't already have a title
        if(form.getAttribute(Form.ATT_PAGE_TITLE)!=null && formDTO.getTitle()==null) {
            formDTO.setTitle(_rendererUtil.localizedMessage(form.getString(Form.ATT_PAGE_TITLE)));
        }
        formDTO.setSubtitle(_rendererUtil.localizedMessage(form.getSubtitle()));
        formDTO.setWizard(form.isWizard());

        formDTO.setFormBeanClass(_rendererUtil.getFormBeanClass());
        formDTO.setFormBeanState(_rendererUtil.getFormBeanState());

        if (form.getAttribute(Form.ATT_READ_ONLY) != null)
            formDTO.setReadOnly(form.getAttributes().getBoolean(Form.ATT_READ_ONLY));

        if (form.getButtons() != null) {
            for (Form.Button b : form.getButtons()) {
                if (formDTO.isReadOnly() == b.isReadOnly()) {
                    ButtonDTO buttonDto = new ButtonDTO(b);
                    if (buttonDto.getText() != null)
                        buttonDto.setText(_rendererUtil.localizedMessage(buttonDto.getText()));
                    formDTO.addButton(buttonDto);
                }
            }
        }

        // jsl - eventually need to support sectionless forms
        if (form.getSections() != null) {
            for (Form.Section section : form.getSections()) {
                SectionDTO dto = this.createSectionDTO(section);
                formDTO.addItem(dto);
            }
        }
        return formDTO;
    }

    /**
     * Creates a section DTO out of a section.  Hands off to the SectionDTO for most of the heavy lifting
     *
     * @param section
     * @return
     */
    public SectionDTO createSectionDTO(Form.Section section) throws GeneralException {
        currentSection = section;
        SectionDTO dto = new SectionDTO(section);
        
        //IIQETN-6091: Attribute 'subtitle' is set after the constructor call, so that,
        // it can be translated using the utility: _rendererUtil.
        dto.setSubtitle(_rendererUtil.localizedMessage(section.getSubtitle()));

        // always set to default for responsive UI
        dto.setType(SectionDTO.SECTION_TYPE_DEFAULT);

        return this.decorateSectionDTO(dto, section);
    }

    /**
     * Decorates all of the base properties of a SectionDTO.
     *
     * @param dto     - The SectionDTO we are decorating
     * @param section - The Form.Section that is related to this dto
     * @return
     */
    protected SectionDTO decorateSectionDTO(SectionDTO dto, Form.Section section) throws GeneralException {
        if (dto.getTitle() != null) {
            dto.setTitle(_rendererUtil.localizedMessage(dto.getTitle()));
        }

        if (section.getItems() != null) {
            for (FormItem item : getSortedItems(section)) {
                if(item instanceof Field) {
                    Field field = (Field) item;
    
                    // If this is a datatable and the hidenulls attribute is set, strip out any
                    // items in the map with null values
                    if (section.hideNulls() && Component.XTYPE_DATA_TABLE.equals(section.getType())) {
                        Object value = field.getValue();
                        if (value != null && Map.class.isAssignableFrom(value.getClass())) {
                            Iterator<Map.Entry> iter = ((Map) value).entrySet().iterator();
                            while (iter.hasNext()) {
                                Map.Entry entry = iter.next();
                                if (entry.getValue() == null)
                                    iter.remove();
                            }
                            // set the whole thing to null if we removed all items
                            if (((Map) value).size() == 0)
                                field.setValue(null);
                        }
                    }
    
                    if (!section.hideNulls() || field.getValue() != null) {
                        FormItemDTO formItemDTO = createFormItemDTO(field, getId());
                        dto.addField(formItemDTO);
                    }
                } else {
                    log.error("Non-field form objects cannot currently be nested.");
                }
            }
        }

        return dto;
    }

    /**
     * Creates a FormItemDTO out of a field.
     *
     * @param field    - The field we are creating the DTO for
     * @param parentId - The parent id of the section that has this field.  We use this for
     *                 putting an id on this field that is related to its section.
     * @return FormItemDTO
     * @throws GeneralException
     */
    public FormItemDTO createFormItemDTO(Field field, String parentId) throws GeneralException {
        FormItemDTOCreator dtoCreator = new FormItemDTOCreator(field, _rendererUtil, parentId, currentSection);
        return dtoCreator.getDTO();
    }

    /**
     * Bug#17280 -- sort the form items based on priority
     * Move the compare logic from Form.java
     *
     * @param section
     * @return
     */
    protected List<FormItem> getSortedItems(Form.Section section) {
        if(!Util.isEmpty(section.getItems())) {
            List<FormItem> sortedItems = new ArrayList<FormItem>(section.getItems());
            Collections.sort(sortedItems, new Comparator<FormItem>() {
                public int compare(FormItem f1, FormItem f2) {
                    return f2.getPriority() - f1.getPriority();
                }
            });
            return sortedItems;
        }
        return new ArrayList<FormItem>();
    }

    //////////////////////////////////////////////////////////////////////
    //
    // UI Properties - these properties are used by the jsf page
    //
    //////////////////////////////////////////////////////////////////////

    public Form getForm() {
        return form;
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

    public String getFormJson() throws GeneralException {
        return JsonHelper.toJson(createDTO());
    }

    // Required for JSF, but we dont want to support setting JSON from client, so do nothing
    public void setFormJson(String formJson) {
        // Do nothing
    }

    /**
     * This ID is used to tie together all the dom components and hidden inputs
     * If the underlying Form object does not supply an ID, we will generate a Uid.
     *
     * @return
     */
    public String getId() {
        return id;
    }

    // Required for JSF, but we dont want to support setting ID from client, so do nothing
    public void setId(String id) {
        // Do nothing
    }

    public Map<String, Object> getData() {
        return this.postData;
    }

    public void setData(Map<String,Object> postData) throws GeneralException {
        this.postData = postData;
    }

    public Map<String, String> getFieldValidation() {
        return this.fieldValidation;
    }

    public String getFieldValidationJson() {
        if (fieldValidation == null)
            return "";
        return JsonHelper.toJson(fieldValidation);
    }

    public String getErrorMessageJson() {
        if (errorMessages == null)
            return "";
        return JsonHelper.toJson(errorMessages);
    }

    /** Needed for jsf */
    public void setErrorMessageJson(String msg) {}
    public void setFieldValidationJson(String msg) {}

    protected FormRendererUtil getRendererUtil() {
        return this._rendererUtil;
    }
}

