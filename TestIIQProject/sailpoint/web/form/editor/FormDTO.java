/* (c) Copyright 2016 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.form.editor;

import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONWriter;

import sailpoint.object.Application;
import sailpoint.object.Attributes;
import sailpoint.object.DynamicValue;
import sailpoint.object.Field;
import sailpoint.object.Filter;
import sailpoint.object.Form;
import sailpoint.object.FormItem;
import sailpoint.object.PropertyInfo;
import sailpoint.object.QueryOptions;
import sailpoint.object.Rule;
import sailpoint.object.Form.Button;
import sailpoint.object.Form.Section;
import sailpoint.object.Form.Type;
import sailpoint.object.Form.Usage;
import sailpoint.object.FormRef;
import sailpoint.object.Script;
import sailpoint.object.Scriptlet;
import sailpoint.object.Workflow.Approval;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.web.BaseDTO;
import sailpoint.web.ButtonDTO;
import sailpoint.web.FieldDTO;
import sailpoint.web.FormReferenceDTO;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.util.FormJsonUtil;
import sailpoint.web.util.WebUtil;
import sailpoint.web.workflow.ScriptDTO;

/**
 * @author peter.holcomb, rahul.borate
 *
 */
@SuppressWarnings("serial")
public class FormDTO extends BaseDTO {
    private static final Log log = LogFactory.getLog(FormDTO.class);

    /////////////////////////////////////////////////////
    //  Constants                                      //
    /////////////////////////////////////////////////////
    public static final String TYPE_LITERAL = "literal";
    public static final String TYPE_RULE = "rule";
    public static final String TYPE_SCRIPT = "script";
    public static final String TYPE_NONE = "none";
    public static final String TYPE_ROLE_OWNER = "IIQRoleOwner";
    public static final String TYPE_APP_OWNER = "IIQApplicationOwner";
    public static final String TYPE_OWNER_REQUESTER = "IIQRequester";

    /////////////////////////////////////////////////////
    //  Form attributes                                //
    /////////////////////////////////////////////////////
    private String _id;
    private String _name;
    private String _description;
    private Type _type;

    /////////////////////////////////////////////////////
    // App/Role provisioning policy form               //
    /////////////////////////////////////////////////////
    private Application _application;
    private DynamicValue _ownerDefinition;

    /////////////////////////////////////////////////////
    //  Reference to another form                      //
    /////////////////////////////////////////////////////
    private FormReferenceDTO _formRefDTO;

    /////////////////////////////////////////////////////
    //  Workflow form approval details                 //
    /////////////////////////////////////////////////////
    private String _approvalSend;
    private String _approvalReturn;
    private ScriptDTO _approvalOwner;

    /////////////////////////////////////////////////////
    //  Form items                                     //
    /////////////////////////////////////////////////////
    private List<SectionDTO> _sections;
    private List<ButtonDTO> _buttons;

    /**
     * @deprecated No longer supported in the form editor.
     */
    private List<FieldDTO> _fields;

    /////////////////////////////////////////////////////
    //  Extended attribute map                         //
    /////////////////////////////////////////////////////
    private Attributes<String,Object> _attributes;

    /////////////////////////////////////////////////////
    //  Attributes used by formEditBean class          //
    /////////////////////////////////////////////////////
    private Usage _usage;
    private String _schemaObjectType;
    private String _owner;
    private String _ownerType;
    private String _formItemsJSON;
    private String _ownerRuleName;

    ////////////////////////////////////////////////////////////////////////////
    //                                                                        //
    //  Constructor                                                           //
    //                                                                        //
    ////////////////////////////////////////////////////////////////////////////
    public FormDTO() {
        _id = Util.uuid();
    }

    public FormDTO(Form form) {
        _id = Util.uuid();
        _type = form.getType();
        _usage = form.convertFormType(_type);
        _schemaObjectType = form.getObjectType();

        // Check an external references in the root form
        FormRef ref = form.getFormRef();

        if (null != ref) {
            populateReferenceFormDetails(ref.getName());
        } else {
            _name = form.getName();
            _description = form.getDescription();

            // Initialize application.
            // Helpful when Role provisioning policy form
            try {
                _application = form.getApplication(getContext());
            } catch (GeneralException ge) {
                log.warn("Unable to determine the application: " + ge.getMessage());
            }

            // Initialize owner details. Helpful when
            // Application/Role provisioning policy form
            String[] ownerFields = getOwnerDefinition(form.getOwnerDefinition());
            _owner = ownerFields[0];
            _ownerType = ownerFields[1];

            if (form.hasWrapperLessFields()) {
                _sections = enwrapFields(form);
            } else {
                _sections = new ArrayList<SectionDTO>();
                for (Section section : Util.iterate(form.getSections())) {
                    SectionDTO sectionDTO = new SectionDTO(section);
                    _sections.add(sectionDTO);
                }
            }

            _buttons = new ArrayList<ButtonDTO>();
            for (Button button : Util.iterate(form.getButtons())) {
                _buttons.add(new ButtonDTO(button));
            }

            // extended attributes
            _attributes = form.getAttributes();
        }
    }

    public FormDTO(FormDTO src) {
        _id = src.getId();
        _name = src.getName();
        _description = src.getDescription();

        _type = src.getType();
        _usage = src.getUsage();
        _schemaObjectType = src.getSchemaObjectType();

        _application = src.getApplication();

        _owner = src.getOwner();
        _ownerType = src.getOwnerType();

        // sections
        _sections = src.getSections();

        // buttons
        _buttons = src.getButtons();

        // extended attributes
        _attributes = src.getAttributes();

        // Reference form
        _formRefDTO = src.getFormRefDTO();
        if (null != _formRefDTO) {
            _name = _formRefDTO.getReferenceName();
        }
    }

    public FormDTO(Approval approval) {
        this(approval.getForm());
        _approvalSend = approval.getSend();
        _approvalReturn = approval.getReturn();
        _approvalOwner = new ScriptDTO(approval.getOwner(),
                                    approval.getOwnerScript(),
                                    Scriptlet.METHOD_STRING);
    }

    //////////////////////////////////////////////////////////////////////
    // Wrap top level fields of form with a appropriate section.
    //
    // Necessary for upgrading older databases.
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Wraps section less fields with a section wrapper.
     *
     * @ignore
     * If the field doesn't have a section attribute defined,
     * then enclose that field in a new section and don't set
     * any significant properties on section that will influence
     * a renderer.
     *
     * If the field has a section attribute defined, then enclose
     * that field in a new section and use fields section attribute
     * value to provide a name and label to a new section.
     */
    private List<SectionDTO> enwrapFields(Form form) {
        List<SectionDTO> sectionDTOs = new ArrayList<SectionDTO>();

        for (FormItem formItem : Util.iterate(form.getItems())) {
            if (formItem instanceof Field) {
                FieldDTO fieldDto = new FieldDTO((Field) formItem);
                String sectionAttr = Util.trimnull(fieldDto.getSection());

                enwrapField(sectionDTOs, fieldDto, sectionAttr);
            } else if (formItem instanceof Section) {
                sectionDTOs.add(new SectionDTO((Section) formItem));
            }
        }

        return sectionDTOs;
    }

    /**
     * Wrap a field with a section
     */
    private void enwrapField(List<SectionDTO> sectionDTOs,
                             FieldDTO fieldDto, String sectionAttr) {
        SectionDTO found = null;
        for (SectionDTO secDto : Util.iterate(sectionDTOs)) {
            if (Util.isNullOrEmpty(sectionAttr)) {
                if (secDto.isWrapper()) {
                    found = secDto;
                }
            } else if (sectionAttr.equals(secDto.getName())) {
                found = secDto;
                break;
            }
        }

        if (found != null) {
            found.addField(fieldDto);
        } else {
            SectionDTO section = new SectionDTO(sectionAttr);
            section.setLabel(sectionAttr);

            section.addField(fieldDto);

            sectionDTOs.add(section);
        }
    }

    ////////////////////////////////////////////////////////////////////////////
    //                                                                        //
    //  Core methods                                                          //
    //                                                                        //
    ////////////////////////////////////////////////////////////////////////////

    public Form commit(Form form) {
        // Remove the embedded Form if a FormRef is selected.
        if (form == null || null != _formRefDTO) {
            form = new Form();
        }

        commitFromDtoToDestinationForm(this, form);

        return form;
    }

    /**
     *
     * Takes a FormDTO and merges the changes from this to the destination form.
     * This needs to make sure that section information etc that are already
     * present in the destination form are not lost.
     *
     * @param source
     *            FormDTO containing the changes
     * @param destination
     *            Form that is being updated
     */
    private void commitFromDtoToDestinationForm(FormDTO source,
                                               Form destination) {

        FormReferenceDTO refFormDTO = source.getFormRefDTO();

        if (null != refFormDTO) {
            // KLUDGE: This seems awkward, but needed in app and role provisioning policy
            populateReferenceFormDetails(refFormDTO.getReferenceName());

            // Put FormRef to the item list.
            destination.setFormRef(refFormDTO.getFormRef());

            destination.setName(refFormDTO.getReferenceName());
            destination.setApplication(source.getApplication());
        } else {
            // Remove the previously associated FormRef.
            destination.setFormRef(null);

            destination.setName(source.getName());
            destination.setRealApplication(source.getApplication());

            // commit form items
            try {
                // Remove previously associated items.
                // Mostly useful for stanalone forms.
                destination.setItems(null);

                // Add sections and its fields
                commitSectionsAndFields(source, destination);

                // Don't forget to add buttons
                destination.setButtons(ButtonDTO.commit(source.getButtons(), null));
            } catch (GeneralException ge) {
                log.warn("Unable to commit form items. Exception: " + ge.getMessage());
            }
        }

        destination.setDescription(source.getDescription());
        destination.setObjectType(source.getSchemaObjectType());

        if (null != source.getType()) {
            destination.setType(source.getType());
        } else {
            destination.setType(destination.convertUsage(source.getUsage()));
        }

        // commit extended attributes
        commitAttributes(source, destination);
    }

    private void commitAttributes(FormDTO source, Form destination) {
        // discard older attributes
        destination.setAttributes(new Attributes<String,Object>());

        DynamicValue ownerDefinition = null;

        // Owner definition when App/Role provisioning policy form
        if(Util.isNotNullOrEmpty(source.getOwnerType())) {
            if(source.getOwnerType().equals(TYPE_SCRIPT)) {
                ownerDefinition = new DynamicValue(null, new Script(source.getOwner()), null);
            } else if(source.getOwnerType().equals(TYPE_RULE)) {
                try {
                    Rule rule = getContext().getObjectByName(Rule.class, source.getOwner());
                    ownerDefinition = new DynamicValue(rule, null, null);
                } catch(Exception e) {
                    log.warn("Unable to set rule: " + e.getMessage());
                }
            } else if (source.getOwnerType().equals(TYPE_NONE)) {
                // do nothing
            } else {
                // When app owner or role owner
                ownerDefinition = new DynamicValue(null, null, source.getOwner());
            }
        }
        // Owner definition when standalone App/role provisioning policy form
        else if (null != source.getOwnerDefinition()) {
            ownerDefinition = source.getOwnerDefinition();
        }

        // Set the owner definition to the destination form
        destination.setOwnerDefinition(ownerDefinition);

        // Set page title to the destination form
        destination.getAttributes().put(Form.ATT_PAGE_TITLE, source.getName());

        if (null == source.getAttributes()) {
            return;
        }

        // Put custom attributes to the destination form
        for (String key : source.getAttributes().keySet()) {
            if (key.equals(Form.ATT_PAGE_TITLE) ||
                    key.equals(Form.ATT_IIQ_TEMPLATE_OWNER_DEFINITION)) {
                continue;
            } else {
                destination.put(key, source.getAttributes().get(key));
            }
        }
    }

    private static void commitSectionsAndFields(FormDTO source, Form destination) {
        // Add new items
        for (SectionDTO sectionDTO : Util.iterate(source.getSections())) {
            Section section = sectionDTO.commit(new Section());
            int cols = 0;

            // First pass to check presence of rows
            for (BaseDTO dto : Util.safeIterable(sectionDTO.getFormItemDTOs())) {
                if (dto instanceof RowDTO) {
                    // Set columns as max of rows size and columns
                    List<FieldDTO> fields = ((RowDTO) dto).getFieldDTOs();
                    if (!Util.isEmpty(fields)) {
                        // Update section columns as per column fields
                        cols = Math.max(fields.size(), cols);
                    }
                }
            }

            // Commit section columns after update if columns are more than 1
            cols = Math.min(cols, SectionDTO.MAX_COLS) > 1 ?
                    Math.min(cols, SectionDTO.MAX_COLS) : 0;
            sectionDTO.setColumns(cols);
            section.setColumns(cols);

            // Second pass to transform rows into section columns and fields spans
            commitRowsAndFields(sectionDTO, section);

            destination.add(section);
        }
    }

    /**
     * Commit Rows and Fields by setting section columns and field spans.
     * For direct fields span is set equal to section columns.
     * For row fields span is set as remaining columns / remaining fields,
     * so when we have am uneven col span of 3, last field in a row is made longer.
     *
     * @param source - SectionDTO containing FormItems to be committed
     * @param dest - Section to contain Fields and Rows
     */
    private static void commitRowsAndFields(SectionDTO source, Section dest) {
        for (BaseDTO dto : Util.safeIterable(source.getFormItemDTOs())) {
            if (dto instanceof FieldDTO) {
                // This is field outside row
                FieldDTO field = (FieldDTO) dto;

                // Set outside row span to full columns
                field.setColumnSpan(source.getColumns());
                commitField(field, dest);
            } else if (dto instanceof RowDTO) {
                // Set row fields columnSpan to remaining columns / remaining Fields
                int columns = source.getColumns();
                List<FieldDTO> rowFields = ((RowDTO) dto).getFieldDTOs();

                if (!Util.isEmpty(rowFields)) {
                    int size = rowFields.size();

                    for (FieldDTO field : rowFields) {
                        int span = columns / size;
                        field.setColumnSpan(span);
                        commitField(field, dest);

                        // Update remaining columns and fields
                        columns -= span;
                        size--;
                    }
                }
            }
        }
    }

    /**
     * Commit FieldDTO and add to Section.
     *
     * @param fieldDTO - FieldDTO to be committed and added to section
     * @param section - Section to add Field to
     */
    private static void commitField(FieldDTO fieldDTO, Section section) {
        Field field = fieldDTO.commit(new Field());
        section.add(field);
    }

    /**
     * On the workflow editor, we deal with forms that are stored on a step
     * within an approval, so when we commit the workflow, we take the form off
     * of the step and stick it into an approval and stick it on the step.
     *
     * @param approval
     * @return
     */
    public Approval commitToApproval(Approval approval) {
        if (approval == null) {
            approval = new Approval();
        }
        approval.setName(_name);
        approval.setSend(_approvalSend);
        approval.setReturn(_approvalReturn);

        approval.setForm(this.commit(approval.getForm()));

        if (_approvalOwner != null) {
            if (_approvalOwner.isScript()) {
                approval.setOwnerScript(_approvalOwner.getScript());
            }
            else {
                approval.setOwner(_approvalOwner.getScriptlet());

                // Explicitly remove existing owner script code from approval
                approval.setOwnerScript(null);
            }
        } else {
            approval.setOwner(null);
        }

        return approval;
    }

    public String getId() {
        return _id;
    }

    public void setId(String id) {
        _id = id;
    }

    public String getName() {
        return _name;
    }

    public void setName(String name) {
        _name = name;
    }

    public String getDescription() {
        return _description;
    }

    public void setDescription(String description) {
        _description = description;
    }

    public Type getType() {
        return _type;
    }

    public void setType(Type type) {
        _type = type;
    }

    public Application getApplication() {
        return _application;
    }

    public void setApplication(Application application) {
        _application = application;
    }

    public String getApprovalSend() {
        return _approvalSend;
    }

    public void setApprovalSend(String send) {
        _approvalSend = send;
    }

    public String getApprovalReturn() {
        return _approvalReturn;
    }

    public void setApprovalReturn(String ret) {
        _approvalReturn = ret;
    }

    public Attributes<String,Object> getAttributes() {
        return _attributes;
    }

    public void setAttributes(Attributes<String,Object> attributes) {
        _attributes = attributes;
    }

    public List<SectionDTO> getSections() {
        return _sections;
    }

    public void setSections(List<SectionDTO> sections) {
        _sections = sections;
    }

    public List<FieldDTO> getFields() {
        return _fields;
    }

    public void setFields(List<FieldDTO> fields) {
        _fields = fields;
    }

    public ScriptDTO getApprovalOwner() {
        return _approvalOwner;
    }

    public void setApprovalOwner(String owner) {
        _approvalOwner = new ScriptDTO(owner, null, Scriptlet.METHOD_STRING);
    }

    public List<ButtonDTO> getButtons() {
        return _buttons;
    }

    public void setButtons(List<ButtonDTO> _buttons) {
        this._buttons = _buttons;
    }

    public FormReferenceDTO getFormRefDTO() {
        return _formRefDTO;
    }

    public void setFormRefDTO(FormReferenceDTO frmRefDTO) {
        this._formRefDTO = frmRefDTO;
    }

    public DynamicValue getOwnerDefinition() {
        return _ownerDefinition;
    }

    public void setOwnerDefinition(DynamicValue ownerDefinition) {
        _ownerDefinition = ownerDefinition;
    }

    public Object getAttribute(String attr) {
        if (_attributes == null) {
            return null;
        }
        return _attributes.get(attr);
    }

    /**
     * Skips template Owner and template Application attribute of a form.
     *
     * Considers only those attribute which influences
     * form presentation when the form gets rendered.
     * For e.g pageTitle, title, subtitle and isWizard
     */
    public Attributes<String,Object> getPresentationalAttributes() {
        Attributes<String,Object> attributes = null;

        if (!Util.isEmpty(_attributes)) {
            attributes = new Attributes<String,Object>();

            for (String key : Util.iterate(_attributes.keySet())) {
                if (key.equals(Form.ATT_IIQ_TEMPLATE_OWNER_DEFINITION) ||
                        key.equals(Form.ATT_IIQ_TEMPLATE_APPLICATION)) {
                    continue;
                } else {
                    attributes.put(key, _attributes.get(key));
                }
            }
        }

        return attributes;
    }

    ////////////////////////////////////////////////////////////////////////////
    //                                                                        //
    //  JSF used GETTERS AND SETTERS                                          //
    //  Mainly used by inline Application, Role and identity policy forms     //
    ////////////////////////////////////////////////////////////////////////////

    public Usage getUsage() {
        return _usage;
    }

    public void setUsage(Usage usage) {
        _usage = usage;
    }

    public String getSchemaObjectType() {
        return _schemaObjectType;
    }

    public void setSchemaObjectType(String schemaObjectType) {
        _schemaObjectType = schemaObjectType;
    }

    public String getApplicationId() {
        if (_application != null) {
            return _application.getId();
        }
        return "";
    }

    public void setApplicationId(String applicationId) {
        if (Util.isNotNullOrEmpty(applicationId)) {
            if (_application == null ||
                    (_application != null &&
                        !Util.nullSafeEq(applicationId, _application.getId()))) {
                try {
                    _application = getContext().getObjectById(Application.class, applicationId);
                } catch (GeneralException ge) {
                    log.warn("Unable to determine the application: " + ge.getMessage());
                }
            }
        }
    }

    public String getApplicationName() {
        if (_application != null) {
            return _application.getName();
        }
        return "";
    }

    public String getOwner() {
         return _owner;
    }

    public void setOwner(String owner) {
        _owner = Util.trimnull(owner);
    }

    public String getOwnerType() {
         return _ownerType;
    }

    public void setOwnerType(String ownerType) {
        _ownerType = Util.trimnull(ownerType);
    }

    /**
     * Get the rule name if the owner is set to a rule
     */
    public String getOwnerRuleName() {
        if(getOwnerType()!= null && getOwner()!= null) {
            if(getOwnerType().equals(TYPE_RULE)) {
                QueryOptions qo = new QueryOptions();
                qo.add(Filter.eq("id", getOwner()));
                try {
                    Iterator<Object[]> rows = getContext().search(Rule.class, qo,
                                                                  Arrays.asList(new String [] { "name" }));
                    if(rows.hasNext()) {
                        Object[] row = rows.next();
                        _ownerRuleName = (String)row[0];
                    }
                } catch(GeneralException ge) {
                    log.warn("Unable to determine rule name: " + ge.getMessage());
                }
            }
        }
        return _ownerRuleName;
    }

    public void setOwnerRuleName(String ruleName) {
       _ownerRuleName = ruleName;
    }

    /**
     * Skips template Owner and template Application attribute of a form.
     *
     * Considers only those attribute which influences
     * form presentation when the form gets rendered.
     * For e.g pageTitle, title, subtitle and isWizard
     */
    public String getPresentationalAttributesJSON() {
        Attributes<String,Object> attributes = getPresentationalAttributes();

        if (!Util.isEmpty(attributes)) {
            Writer jsonString = new StringWriter();
            JSONWriter jsonWriter = new JSONWriter(jsonString);
            try {
                jsonWriter.object();

                jsonWriter.key("attributes");
                jsonWriter.value(attributes);

                jsonWriter.endObject();
            } catch (JSONException e) {
                log.error("Could not get JSON for the extended attributes of a Form");
            }

            return jsonString.toString();
        }

        return "";
    }

    /**
     * Create forms extended Attributes map from the JSON string.
     * @param attrJSON A JSON string containing forms extended attributes
     */
    public void setPresentationalAttributesJSON(String attrJSON) {
        if (Util.isNotNullOrEmpty(attrJSON)) {
            Attributes<String,Object> attr;

            try {
                attr = WebUtil.getJSONMap(new JSONObject(attrJSON), "attributes");
            } catch (JSONException e) {
                log.error("Could not get JSON for the extended attributes of a Form");
                return;
            }

            // Copy template Owner attribute
            if (!Util.isEmpty(_attributes) && _attributes.containsKey(Form.ATT_IIQ_TEMPLATE_OWNER_DEFINITION)) {
                attr.put(Form.ATT_IIQ_TEMPLATE_OWNER_DEFINITION,
                         _attributes.get(Form.ATT_IIQ_TEMPLATE_OWNER_DEFINITION));
            }

            // Copy template Application attribute
            if (!Util.isEmpty(_attributes) && _attributes.containsKey(Form.ATT_IIQ_TEMPLATE_APPLICATION)) {
                attr.put(Form.ATT_IIQ_TEMPLATE_APPLICATION,
                         _attributes.get(Form.ATT_IIQ_TEMPLATE_APPLICATION));
            }

            _attributes = attr;
        }
    }

    public String getFormItemsJSON() {
        return _formItemsJSON;
    }

    public void setFormItemsJSON(String itemsJSON) {
        _formItemsJSON = itemsJSON;
    }

    /**
     * @return - ID of the referenced form within FormRef object
     */
    public String getFormRefId() {
        return (null != _formRefDTO) ? _formRefDTO.getReferenceId() : null;
    }

    /**
     * @return - Name of the referenced form within FormRef object
     */
    public String getFormRefName() {
        return (_formRefDTO != null) ? _formRefDTO.getReferenceName() : null;
    }

    /**
     * Prepares form items JSON string
     */
    public String getFormItems() {
        Writer jsonString = new StringWriter();
        JSONWriter jsonWriter = new JSONWriter(jsonString);

        try {
            jsonWriter.object();

            // Formation of item tree structure
            jsonWriter.key("root");
            JSONObject rootJSON = new JSONObject();
            rootJSON.put("children", FormJsonUtil.convertFormItemsToJSONTree(this));
            jsonWriter.value(rootJSON);

            jsonWriter.endObject();
        } catch (JSONException e) {
            log.error("Could not get JSON for the Form item list");
        }

        log.debug("json: " +jsonString);

        return jsonString.toString();
    }

    /**
     * Field Types JSON
     * @return JSON String
     */
    public String getTypesJSON() {
        Writer jsonString = new StringWriter();
        JSONWriter jsonWriter = new JSONWriter(jsonString);

        try {
            jsonWriter.object();

            jsonWriter.key("objects");
            List<JSONObject> fieldList = new ArrayList<JSONObject>();

            Map<String, String> fieldMap = new HashMap<String,String>();
            fieldMap.put("value", PropertyInfo.TYPE_BOOLEAN);
            fieldMap.put("name", getMessage(MessageKeys.OCONFIG_TYPE_BOOLEAN));
            fieldList.add(new JSONObject(fieldMap));

            fieldMap = new HashMap<String,String>();
            fieldMap.put("value", PropertyInfo.TYPE_DATE);
            fieldMap.put("name", getMessage(MessageKeys.OCONFIG_TYPE_DATE));
            fieldList.add(new JSONObject(fieldMap));

            fieldMap = new HashMap<String,String>();
            fieldMap.put("value", PropertyInfo.TYPE_INT);
            fieldMap.put("name", getMessage(MessageKeys.OCONFIG_TYPE_INT));
            fieldList.add(new JSONObject(fieldMap));

            fieldMap = new HashMap<String,String>();
            fieldMap.put("value", PropertyInfo.TYPE_LONG);
            fieldMap.put("name", getMessage(MessageKeys.OCONFIG_TYPE_LONG));
            fieldList.add(new JSONObject(fieldMap));

            fieldMap = new HashMap<String,String>();
            fieldMap.put("value", PropertyInfo.TYPE_IDENTITY);
            fieldMap.put("name", getMessage(MessageKeys.OCONFIG_TYPE_IDENTITY));
            fieldList.add(new JSONObject(fieldMap));

            fieldMap = new HashMap<String,String>();
            fieldMap.put("value", PropertyInfo.TYPE_SECRET);
            fieldMap.put("name", getMessage(MessageKeys.OCONFIG_TYPE_SECRET));
            fieldList.add(new JSONObject(fieldMap));

            fieldMap = new HashMap<String,String>();
            fieldMap.put("value", PropertyInfo.TYPE_STRING);
            fieldMap.put("name", getMessage(MessageKeys.OCONFIG_TYPE_STRING));
            fieldList.add(new JSONObject(fieldMap));

            jsonWriter.value(fieldList);
            jsonWriter.endObject();

        } catch (JSONException e) {
            log.error("Could not get the JSON for Form DTO");
        }

        return jsonString.toString();
    }

    /**
     * Create form items from JSON string.
     * Only for inlile app/role and Identity provisioning policy form
     */
    public void updateItemsFromJSON() {
        if (Util.isNotNullOrEmpty(_formItemsJSON)) {
            try {
                JSONObject itemsJSON = new JSONObject(_formItemsJSON);
                FormJsonUtil.updateFormItemsFromJSON(this, itemsJSON);
            } catch (JSONException jsoe) {
                if (log.isWarnEnabled()) {
                    log.warn("Exception during form commit: " + jsoe.getMessage(), jsoe);
                }
            }
        } else {
            setSections(null);
            setFields(null);
            setButtons(null);
        }
    }

    /**
     * Bring an external form details into root form
     * @param formName
     */
    public void populateReferenceFormDetails(String formName) {
        if (Util.isNotNullOrEmpty(formName)) {
            Form refForm = null;

            try {
                refForm = getContext().getObjectByName(Form.class, formName);
            } catch (GeneralException ge) {
                log.warn("Unable to determine the external references of the form: " + ge.getMessage());
            }

            if (refForm != null) {
                _name = refForm.getName();
                _description = refForm.getDescription();

                // We now have a real Application reference
                try {
                    _application = refForm.getApplication(getContext());
                } catch (GeneralException ge) {
                    log.warn("Unable to determine the application: " + ge.getMessage());
                }

                // Extended attributes.
                _attributes = getExternalAttributes(refForm.getAttributes());

                // Initialize owner details. Helpful when
                // Application/Role provisioning policy form
                String[] ownerFields = getOwnerDefinition(refForm.getOwnerDefinition());
                _owner = ownerFields[0];
                _ownerType = ownerFields[1];

                _formRefDTO = new FormReferenceDTO(refForm.getId(), refForm.getName());
            }
        }
    }

    /**
     * Clones an external form attributes, but excludes ATT_IIQ_TEMPLATE_APPLICATION
     * @param refAtts
     */
    private Attributes<String, Object> getExternalAttributes(Attributes<String, Object> refAtts) {
        if (Util.isEmpty(refAtts)) {
            return null;
        }

        Attributes<String, Object> atts = new Attributes<String, Object>();
        for (Map.Entry<String, Object> entry : refAtts.entrySet()) {
            if (!entry.getKey().equals(Form.ATT_IIQ_TEMPLATE_APPLICATION)) {
                atts.put(entry.getKey(), entry.getValue());
            }
        }

        return atts;
    }

    /**
     * Convert Owner Definition to plane text.
     * @param {DynamicValue} the dynamicValue
     */
    private static String[] getOwnerDefinition(DynamicValue dynamicValue) {
        String[] ownerFields = new String[2];

        if(dynamicValue != null) {
            if(dynamicValue.getScript() != null) {
                ownerFields[0] = Util.trimnull(dynamicValue.getScript().getSource());
                ownerFields[1] = TYPE_SCRIPT;
            } else if(dynamicValue.getRule() != null) {
                ownerFields[0] = Util.trimnull(dynamicValue.getRule().getName());
                ownerFields[1] = TYPE_RULE;
            } else if(dynamicValue.getValue() != null) {
                ownerFields[0] = Util.trimnull(dynamicValue.getValue().toString());

                if (Util.isNotNullOrEmpty(ownerFields[0])) {
                    if (ownerFields[0].equals(TYPE_APP_OWNER)) {
                        ownerFields[1] = TYPE_APP_OWNER;
                    } else if (ownerFields[0].equals(TYPE_ROLE_OWNER)) {
                        ownerFields[1] = TYPE_ROLE_OWNER;
                    } else if (ownerFields[0].equals(TYPE_OWNER_REQUESTER)) {
                        ownerFields[1] = TYPE_OWNER_REQUESTER;
                    }
                }
            }
        }
        return ownerFields;
    }
}
