/* (c) Copyright 2014 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * Bag of static methods that can be called from Central Forms. These 
 * are things that are common across central forms to prevent
 * duplicate logic.
 * 
 */

package sailpoint.web.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import sailpoint.api.SailPointContext;
import sailpoint.object.Application;
import sailpoint.object.DynamicValue;
import sailpoint.object.Field;
import sailpoint.object.Form;
import sailpoint.object.Resolver;
import sailpoint.object.Rule;
import sailpoint.object.Script;
import sailpoint.object.Scriptlet;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.web.BaseDTO;
import sailpoint.web.ButtonDTO;
import sailpoint.web.FieldDTO;
import sailpoint.web.FormReferenceDTO;
import sailpoint.web.form.editor.FormDTO;
import sailpoint.web.form.editor.FormItemNode;
import sailpoint.web.form.editor.RowDTO;
import sailpoint.web.form.editor.SectionDTO;
import sailpoint.web.workflow.ScriptDTO;

public class FormJsonUtil {
    @SuppressWarnings("unused")
    private static final Log log = LogFactory.getLog(FormJsonUtil.class);

    private static final String SECTIONS = "sections";
    private static final String FIELDS = "fields";
    private static final String BUTTONS = "buttons";

    /**
     * Constructor 
     */
    public FormJsonUtil() {}

    /**
     * Prepare FormDTO object form JSON data
     * 
     * @param form
     *            FormDTO object
     * @param formJSON
     *            JSON representation of form
     * @throws JSONException
     */
    public static void updateFormFromJSON(FormDTO form, JSONObject formJSON)
        throws JSONException {

        String formRefId = WebUtil.getJSONString(formJSON, "formRefId");

        // Setting name and description in both inline and reference form case.
        form.setName(WebUtil.getJSONString(formJSON, "name"));
        form.setDescription(WebUtil.getJSONString(formJSON, "description"));

        // Set Approval fields.
        form.setApprovalSend(WebUtil.getJSONString(formJSON, "sendVal"));
        form.setApprovalReturn(WebUtil.getJSONString(formJSON, "returnVal"));
        String owner = parseScriptFromJSON(formJSON, "owner", null);
        if (null != owner) {
            form.setApprovalOwner(owner);
            updateExplicitMethod(formJSON, "owner", form.getApprovalOwner());
        } else {
            form.setApprovalOwner(null);
        }

        // Extended attributes
        form.setAttributes(WebUtil.getJSONMap(formJSON, "attributes"));

        // Create the embedded Form only if a FormRef is not selected.
        if (Util.isNullOrEmpty(formRefId)) {
            // Remove FormRefDTO from existing FormDTO if present.
            // If reference form is removed and an inline form is added,
            // FormRefDTO is not removed unless workflow is saved.
            // So we have to remove it here.
            form.setFormRefDTO(null);

            updateFormItemsFromJSON(form, formJSON);
        } else {
            // Removing inline forms sections, fields
            // and buttons in case of Reference form
            form.setSections(null);
            form.setFields(null);
            form.setButtons(null);

            form.setFormRefDTO(new FormReferenceDTO(formRefId,
                    WebUtil.getJSONString(formJSON, "name")));
        }
    }

    /**
     * Create form items from the JSON
     *
     * @param form A FormDTO
     * @param jsonObj A JSON object containing form items data
     * @throws JSONException
     */
    public static void updateFormItemsFromJSON(FormDTO form, JSONObject jsonObj)
        throws JSONException {

        // Add sections and its fields.
        form.setSections(getSectionsFromJSON(jsonObj));

        // Add buttons. buttons will be always at the bottom for form items tree.
        form.setButtons(getButtonsFromJSON(jsonObj));
    }

    /**
     * Return List of SectionDTOs from JSON.
     */
    private static List<SectionDTO> getSectionsFromJSON(JSONObject jsonObj) throws JSONException {
        List<SectionDTO> sections = new ArrayList<SectionDTO>();
        JSONArray sectionsJSON = WebUtil.getJSONArray(jsonObj, SECTIONS);
        if (null != sectionsJSON) {
            for (int i = 0; i < sectionsJSON.length(); i++) {
                JSONObject sectionJSON = sectionsJSON.getJSONObject(i);

                if (sectionJSON != null) {
                    SectionDTO sectionDto = new SectionDTO(sectionJSON);
                    // Set List of FormItems [ RowDTOs and FieldDTOs ]
                    sectionDto.setFormItems(getSectionItemsFromJSON(sectionJSON));
                    sections.add(sectionDto);
                }
            }
        }
        return sections;
    }

    /**
     * Return List of BaseDTOs [ RowDTOs and FieldDTOs ] from Section JSON.
     */
    private static List<BaseDTO> getSectionItemsFromJSON(JSONObject sectionJSON)
            throws JSONException {
        List<BaseDTO> dtos = new ArrayList<BaseDTO>();
        JSONArray formItemsJSON = WebUtil.getJSONArray(sectionJSON, FIELDS);

        if (null != formItemsJSON) {
            for (int j = 0; j < formItemsJSON.length(); j++) {
                JSONObject formItemJSON = formItemsJSON.getJSONObject(j);
                if (null != formItemJSON) {
                    if (formItemJSON.has(FIELDS)) {
                        // This is a Row
                        RowDTO rowDto = new RowDTO(formItemJSON);
                        dtos.add(rowDto);
                        JSONArray fieldsJSON = formItemJSON.getJSONArray(FIELDS);
                        if (null != fieldsJSON) {
                            for (int k = 0; k < fieldsJSON.length(); k++) {
                                JSONObject fieldJSON = fieldsJSON.getJSONObject(k);
                                FieldDTO fieldDto = new FieldDTO(fieldJSON);
                                rowDto.addField(fieldDto);
                            }
                        }
                    } else {
                        FieldDTO fieldDto = new FieldDTO(formItemJSON);
                        dtos.add(fieldDto);
                    }
                }
            }
        }
        return dtos;
    }

    /**
     * Return List of ButtonDTOs from JSON.
     */
    private static List<ButtonDTO> getButtonsFromJSON(JSONObject jsonObj) throws JSONException {
        List<ButtonDTO> buttons = new ArrayList<ButtonDTO>();
        JSONArray buttonsJSON = WebUtil.getJSONArray(jsonObj, BUTTONS);
        if (null != buttonsJSON) {
            for (int i = 0; i < buttonsJSON.length(); i++) {
                JSONObject buttonJSON = buttonsJSON.getJSONObject(i);
                if (null != buttonJSON) {
                    ButtonDTO buttonDto = new ButtonDTO(buttonJSON);
                    buttons.add(buttonDto);
                }
            }
        }
        return buttons;
    }

    /**
     * Get the form type from JSON and update the FormDTO
     *
     * @param formDTO
     *            FormDTO object
     * @param formJSON
     *            JSON representation of form
     * @throws JSONException
     */
    public static void updateTypeFromJSON(FormDTO formDTO, JSONObject formJSON) throws JSONException {
        String formType = WebUtil.getJSONString(formJSON, "formType");
        if (formType.equalsIgnoreCase(Form.Type.Application.toString())) {
            formDTO.setType(Form.Type.Application);
        } else if (formType.equalsIgnoreCase(Form.Type.Role.toString())) {
            formDTO.setType(Form.Type.Role);
        } else if (formType.equalsIgnoreCase(Form.Type.Workflow.toString())) {
            formDTO.setType(Form.Type.Workflow);
        }
    }

    /**
     * Get the application from JSON and update the FormDTO
     * 
     * @param formDTO FormDTO object
     * @param formJSON JSON representation of form
     * @throws GeneralException, JSONException
     */
    public static void updateApplicationFromJSON(Resolver res,
                                                 FormDTO formDTO,
                                                 JSONObject formJSON)
        throws GeneralException, JSONException {

        String applicationID = WebUtil.getJSONString(formJSON, "application");
        if (Util.isNotNullOrEmpty(applicationID)) {
            Application app = res.getObjectById(Application.class, applicationID);
            formDTO.setApplication(app);
        }
    }

    /**
     * Parse the script string from the given JSONObject, looking for attributes
     * using the given keys.  Return either a scriplet string that can be used
     * to construct a ScriptDTO or null if there is no script in the JSONObject.
     */
    public static String parseScriptFromJSON(JSONObject obj, String keyPrefix,
                                              String negateKey)
        throws JSONException {

        String script = null;
        String source = WebUtil.getJSONString(obj, keyPrefix + "Source");
        if(!Util.isNullOrEmpty(source)) {
            String method = cleanScript(WebUtil.getJSONString(obj, keyPrefix + "Method"));
            source = cleanScript(source);

            if (null != negateKey) {
                script = method + ":" + source;
                boolean negate = WebUtil.getJSONBoolean(obj, negateKey);
                if (negate) {
                    script = "!"+script;
                }
            } else {
                // negate == null, but we still may need to negate
                // for ref variables we could still get negated source
                // see bug #15876 in bugzilla for more details
                if (isRefAndSourceStartsWithNegate(method, source)) {
                    script = "!" + method + ":" + source.substring("!".length());
                } else {
                    script = method + ":" + source;
                }
            }
        }
        return script;
    }

    /** Any script attributes coming back from the front-end will have had certain html entities converted
     * We need to convert them back to their original form or else the xml engine will convert them again
     * Bug #5727 - PH
     * @param script
     * @return
     */
    public static String cleanScript(String script) {
        if(script!=null) {
            /** Convert things like '&amp;' to '&' **/
            script = StringEscapeUtils.unescapeHtml4(script);
        }

        return script;
    }

    public static boolean isRefAndSourceStartsWithNegate(String method, String source) {
        if (method == null || source == null) {
            return false;
        }

        return ((method + ":").equals(Scriptlet.METHOD_REF) && source.startsWith("!"));
    }

    public static void updateExplicitMethod(JSONObject obj, String keyPrefix, ScriptDTO scriptDTO) {
        if (scriptDTO != null) {
            scriptDTO.setExplicitMethod(WebUtil.getJSONBoolean(obj, keyPrefix + "ExplicitMethod"));
        }
    }

    /**
     * Convert a Form object to a map of values
     * to display the Form's data on a Form Editor Window.
     * @param formDTO The Form data transmission object
     * @param resolver The SailPoint Object resolver
     * @return Map
     * @throws GeneralException, JSONException
     */
    public static Map<String, Object> convertFormToJSON(FormDTO formDTO, Resolver resolver)
        throws GeneralException, JSONException {

        if(null == formDTO) {
            return null;
        }

        Map<String, Object> formMap = new HashMap<String, Object>();
        formMap.put("id", formDTO.getUid());
        formMap.put("name", formDTO.getName());
        formMap.put("description", formDTO.getDescription());

        // Approval attributes
        formMap.put("sendVal", formDTO.getApprovalSend());
        formMap.put("returnVal", formDTO.getApprovalReturn());
        addScript(formMap, formDTO.getApprovalOwner(), "owner", null);

        // form reference
        addFormRef(formMap, formDTO, resolver);

        // Extended attributes
        formMap.put("attributes", formDTO.getAttributes());

        // formation of form item tree structure
        JSONObject rootJSON = new JSONObject();
        rootJSON.put("children", convertFormItemsToJSONTree(formDTO));
        formMap.put("root", rootJSON);

        return formMap;
    }

    /**
     * Convert a Form object to a map of values
     * to display the Form's data on a Form Editor Window.
     *
     * @param context The SailPointContext object
     * @param form The object representing a form
     * @param tree Return as tree or flat
     *
     * @return Map
     *
     * @throws GeneralException
     * @throws JSONException
     */
    public static Map<String, Object> convertFormToJSON(SailPointContext context,
                                                        Form form,
                                                        boolean tree)
            throws GeneralException, JSONException {

        if(form == null) {
            return null;
        }

        // convert to DTO
        FormDTO formDTO = new FormDTO(form);

        Map<String, Object> formMap = new HashMap<String, Object>();
        formMap.put("id", formDTO.getUid());
        formMap.put("name", formDTO.getName());
        formMap.put("formType", formDTO.getType());
        formMap.put("description", formDTO.getDescription());

        // Get Application
        Application app = form.getApplication(context);
        if (app != null) {
            formMap.put("appName", app.getName());
            formMap.put("appId", app.getId());
        }

        // Get Owner details.
        formMap.put("ownerType", formDTO.getOwnerType());
        formMap.put("owner", formDTO.getOwner());

        // Extended attributes
        formMap.put("attributes", formDTO.getPresentationalAttributes());

        // Get form items (sections, fields and buttons)
        if (tree) {
            JSONObject rootJSON = new JSONObject();
            rootJSON.put("children", convertFormItemsToJSONTree(formDTO));
            formMap.put("root", rootJSON);
        } else {
            JSONObject formItemsJson = convertFormItemsToJson(formDTO);
            formMap.put(SECTIONS, formItemsJson.getJSONArray(SECTIONS));
            formMap.put(BUTTONS, formItemsJson.getJSONArray(BUTTONS));
        }

        return formMap;
    }

    /**
     * Get the owner from JSON and update the FormDTO
     * 
     * @param res SailPointContext
     * @param form FormDTO object
     * @param formJSON JSON representation of form
     * @throws JSONException
     * @throws GeneralException 
     */
    public static void updateOwnerDefinition(Resolver res,
                                             FormDTO form,
                                             JSONObject formJSON)
        throws GeneralException, JSONException {

        String ownerType = WebUtil.getJSONString(formJSON, "ownerType");
        if (Util.isNotNullOrEmpty(ownerType)) {
            String ownerDefinition = WebUtil.getJSONString(formJSON, "owner");
            DynamicValue value = new DynamicValue();

            // In case of Owner Type as None save empty string in owner
            // In case of IIQApplicationOwner/IIQRoleOwner save respective value
            // jsl - convert all this to use OWNER_PARENT for both types
            if (ownerType.equals (Form.OWNER_APPLICATION) ||
                    ownerType.equals (Form.OWNER_ROLE) ||
                    ownerType.equals (Field.OWNER_REQUESTER)) {
                value.setValue(ownerDefinition);
                form.setOwnerDefinition(value);
                return;
            } else if (ownerType.equals("none")) {
                form.setOwnerDefinition(null);
                return;
            }

            // If Script/Rule owner contains null or empty value do not save it
            if(Util.isNotNullOrEmpty(ownerDefinition)) {
                if (ownerType.equals ("script")) {
                    Script script = new Script();
                    script.setSource(ownerDefinition);
                    value.setScript(script);
                }
                else if (ownerType.equals ("rule")) {
                    Rule rule = res.getObjectByName(Rule.class, ownerDefinition);
                    value.setRule(rule);
                }

                form.setOwnerDefinition(value);
            }
        }
    }

    /**
     * Add the given (possibly null) ScriptDTO to the given map.
     * @param  addTo      The Map to which to add the script.
     * @param  script     The possibly-null script to add.
     * @param  keyPrefix  The prefix to use for the map keys.
     * @param  negateKey  The possibly-null map key to use to store whether to
     *                    the script expression is negated.
     */
    public static void addScript(Map<String,Object> addTo, ScriptDTO script,
                                  String keyPrefix, String negateKey) {
        if (null != script) {
            String method = script.getMethod();
            String source = script.getSource();

            if (null != negateKey) {
                addTo.put(negateKey, script.isNegate());
            }

            addTo.put(keyPrefix + "Method", method);
            addTo.put(keyPrefix + "Source", source);
            addTo.put(keyPrefix + "ExplicitMethod", script.isExplicitMethod());
        }
    }

    /**
     * Convert a FormDTO object to a list of tree nodes
     *
     * @param form
     *            FormDTO object
     * @throws GeneralException
     */
    public static List<JSONObject> convertFormItemsToJSONTree(FormDTO form)
        throws JSONException {

        List<JSONObject> formItems = new ArrayList<JSONObject>();

        // Add Section and its fields to list of tree nodes
        for (SectionDTO section : Util.iterate(form.getSections())) {
            FormItemNode sectionNode = new FormItemNode(section);
            sectionNode.addChildren(section.getFormItemDTOs());
            formItems.add(new JSONObject(sectionNode.toJSONString()));
        }

        // TODO : Add field outside section to list of tree nodes

        // Add buttons to list of tree node
        for (ButtonDTO button : Util.iterate(form.getButtons())) {
            FormItemNode buttonNode = new FormItemNode(button);
            formItems.add(new JSONObject(buttonNode.toJSONString()));
        }

        return formItems;
    }

    /**
     * Convert a FormDTO object to a JSON of FormItems
     *
     * @param form FormDTO object
     * @return JSONObject of FormItems in following form -
     *             {
     *               "sections" : [
     *                 "fields" : []
     *               ]
     *               "buttons" : []
     *             }
     * @throws JSONException - handles json exception while adding form items
     */
    private static JSONObject convertFormItemsToJson(FormDTO form) throws JSONException {
        JSONObject formItems = new JSONObject();

        // Put Section and its fields to sections array
        JSONArray sections = new JSONArray();
        for (SectionDTO section : Util.safeIterable(form.getSections())) {
            JSONObject sectionJson = section.getJSON();

            // Put Fields to fields array
            JSONArray fields = new JSONArray();
            for (FieldDTO field : Util.safeIterable(section.getFieldDTOs())) {
                JSONObject fieldJson = field.getJSON();
                fields.put(fieldJson);
            }
            sectionJson.put(FIELDS, fields);
            sections.put(sectionJson);
        }
        formItems.put(SECTIONS, sections);

        // Put Buttons to buttons array
        JSONArray buttons = new JSONArray();
        for (ButtonDTO button : Util.safeIterable(form.getButtons())) {
            JSONObject buttonJson = button.getJSON();
            buttons.put(buttonJson);
        }
        formItems.put(BUTTONS, buttons);

        return formItems;
    }

    /**
     * Add the formRefDTO that exists within the formDTO to the map
     * @param formMap the map that will be transformed to JSON
     * @param form the DTO representing the form
     * @param resolver necessary when the id doesn't exist
     */
    private static void addFormRef(Map<String,Object> formMap, FormDTO form,
                                   Resolver resolver) {
        FormReferenceDTO formRefDTO = form.getFormRefDTO();
        if (formRefDTO != null) {
            String refId = formRefDTO.getReferenceId();
            if (Util.isNullOrEmpty(refId)) {
                try {
                    Form referencedForm = resolver.getObjectByName(Form.class, formRefDTO.getReferenceName());
                    if (referencedForm != null) {
                        refId = referencedForm.getId();
                    }
                } catch (GeneralException e) {
                    log.warn("could not find form with name: " + formRefDTO.getReferenceName());
                }
            }
            formMap.put("formRefId", refId);
        }
    }
}
