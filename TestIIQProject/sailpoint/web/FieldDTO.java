/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import sailpoint.object.Attributes;
import sailpoint.object.DynamicValue;
import sailpoint.object.Field;
import sailpoint.object.Field.ApplicationDependency;
import sailpoint.object.Form.Button;
import sailpoint.object.Rule;
import sailpoint.object.Script;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.form.editor.DynamicValueBooleanHolder;
import sailpoint.web.form.editor.FormDTO;
import sailpoint.web.util.WebUtil;

@SuppressWarnings("serial")
public class FieldDTO extends BaseDTO {
    private static final Log log = LogFactory.getLog(FieldDTO.class);

    ////////////////////////////////////////////////////////////////////////////
    //                                                                        //
    // Constants                                                              //
    //                                                                        //
    ////////////////////////////////////////////////////////////////////////////

    public static String TYPE_BUTTON = "Button";

    ////////////////////////////////////////////////////////////////////////////
    //                                                                        //
    // Fields                                                                 //
    //                                                                        //
    ////////////////////////////////////////////////////////////////////////////

    String name;

    /**
     * The name may be changed we need to keep the original
     * name around so that we merge properly.
     */
    transient String originalName; 

    String description;
    String helpKey;
    String displayName;
    //Used to store the localized version of displayName
    String localizedDisplayName;
    String type;
    String inputTemplate;

    String value;
    String previousValue;

    boolean reviewRequired;
    boolean required;
    boolean multi;
    boolean dynamic;

    int columnSpan;
    int priority;

    String dependencies;
    String script;
    String ruleId;
    String validationRuleId;
    String validationScript;
    String filter;

    List<Object> allowedValuesList;
    String allowedValuesRuleId;
    String allowedValuesScript;

    String ownerRuleId;
    String ownerScript;
    String ownerValue;
    String section;

    /** sailpoint.object.Form.Button stuff **/
    String label;
    String action;

    boolean postBack;
    boolean displayOnly;
    boolean authoritative;
    boolean permission;
    boolean incomplete;
    boolean sortable;

    String application;
    String role;
    String format;
    String template;
    String displayType;
    String categoryName;

    DynamicValueBooleanHolder readOnly = DynamicValueBooleanHolder.createDefault();
    DynamicValueBooleanHolder hidden =  DynamicValueBooleanHolder.createDefault();

    /**
     * These are stored as an ApplicationDependency object on the Field itself. Since the DTO is usually mapped to the view, and these
     * are usually displayed independent, we will use two strings to store this
     */
    String dependentAppName;
    String dependentSchemaAttr;

    /**
     * Extended attributes that can influence the form renderer.
     */
    private Attributes<String,Object> attributes;

    ////////////////////////////////////////////////////////////////////////////
    //                                                                        //
    // Constructor                                                            //
    //                                                                        //
    ////////////////////////////////////////////////////////////////////////////

    public FieldDTO() {}

    public FieldDTO(Button src) {
        this.name = src.getAction();
        this.action = src.getAction();
        this.label = src.getLabel();
        this.type = TYPE_BUTTON;        
    }

    @SuppressWarnings("unchecked")
    public FieldDTO(Field src) {
        this.name = src.getName();
        this.originalName = src.getName();
        this.description = src.getDescription();
        this.helpKey = src.getHelpKey();
        this.displayName = src.getDisplayName();
        this.localizedDisplayName = new Message(src.getDisplayName()).getLocalizedMessage(getLocale(),getUserTimeZone());
        this.type = src.getNormalizedType();
        this.inputTemplate = src.getInputTemplate();
        this.setValue(src.getValueXmlAttribute());
        this.reviewRequired = src.isReviewRequired();
        this.required = src.isRequired();    
        this.multi = src.isMulti();
        this.dynamic = src.isAllowedValuesDynamic();
        this.section = src.getSection();
        this.dependencies = src.getDependencies();
        this.columnSpan = src.getColumnSpan();
        this.postBack = src.isPostBack();
        this.displayOnly = src.isDisplayOnly();
        this.authoritative = src.isAuthoritative();
        this.filter = src.getFilterString();
        this.application = src.getApplication();
        this.role = src.getRole();
        this.format = src.getFormat();
        this.template = src.getTemplate();
        this.displayType = src.getDisplayType();
        this.priority = src.getPriority();
        this.categoryName = src.getCategoryName();
        this.previousValue = src.getPreviousValueXmlAttribute();

        // boolean properties
        this.incomplete = src.isIncomplete();
        this.permission = src.isPermission();
        this.sortable = src.isSortable();

        if(src.getScript()!=null)
            this.script = src.getScript().getSource();

        if(src.getRule()!=null) {
            this.ruleId = src.getRule().getName();
        }

        if(src.getValidationScript()!=null) {
            this.validationScript = src.getValidationScript().getSource();
        }

        if(src.getValidationRule()!=null) {
            this.validationRuleId = src.getValidationRule().getName();
        }

        DynamicValue allowedValues = src.getAllowedValuesDefinition();
        if(allowedValues!=null) {
            if(allowedValues.getScript()!=null) {
                this.setAllowedValuesScript(allowedValues.getScript().getSource());
            } else if(allowedValues.getRule()!=null) {
                this.setAllowedValuesRuleId(allowedValues.getRule().getName());
            } else if(allowedValues.getValue()!=null) {
                this.setAllowedValuesList((List<Object>)allowedValues.getValue());
            }
        } else if(src.getAllowedValues()!=null) {
            this.setAllowedValuesList(src.getAllowedValues());
        }

        DynamicValue owner = src.getOwnerDefinition();
        if(owner!=null) {
            if(owner.getScript()!=null) {
                this.setOwnerScript(owner.getScript().getSource());
            } else if(owner.getRule()!=null) {
                this.setOwnerRuleId(owner.getRule().getName());
            } else if(owner.getValue()!=null) {
                this.setOwnerValue((String)owner.getValue());
            }
        }

        ApplicationDependency ad = src.getAppDependency();
        if(ad!=null) {
            this.setDependentAppName(ad.getApplicationName());
            this.setDependentSchemaAttr(ad.getSchemaAttributeName());
        }

        // Check for extended attributes and clone it.
        // So that original Attributes object from the source form object won't disturb.
        this.attributes = (null != src.getAttributes()) ? src.getAttributes().mediumClone() : new Attributes<String,Object>();

        try {
            readOnly = DynamicValueBooleanHolder.createFromDynamicValueOrDefault(src.getReadOnlyDefinition(getContext()));
            hidden = DynamicValueBooleanHolder.createFromDynamicValueOrDefault(src.getHiddenDefinition(getContext()));
            this.attributes.put(Field.ATTR_READ_ONLY, readOnly.convertToJson());
            this.attributes.put(Field.ATTR_HIDDEN, hidden.convertToJson());
        } catch (JSONException jsoe) {
            log.warn("Unable to serialize FieldDTO to json: " + jsoe.getMessage());
        } catch (GeneralException ex) {
            // Should not be here but just in case throw it.
            throw new IllegalStateException(ex);
        }
    }

    /**
     * Builds a FieldDTO out of a JSON object
     */
    public FieldDTO(JSONObject fieldJSON) {
        try {
            this.setDescription(WebUtil.getJSONString(fieldJSON, "description"));
            this.setHelpKey(WebUtil.getJSONString(fieldJSON, "helpKey"));
            this.setName(WebUtil.getJSONString(fieldJSON, "name"));
            this.setOriginalName(WebUtil.getJSONString(fieldJSON, "originalName"));
            this.setDisplayName(WebUtil.getJSONString(fieldJSON, "displayName"));
            this.setLocalizedDisplayName(WebUtil.getJSONString(fieldJSON, "localizedDisplayName"));
            this.setType(WebUtil.getJSONString(fieldJSON, "type"));
            this.setInputTemplate(WebUtil.getJSONString(fieldJSON, "inputTemplate"));
            this.setColumnSpan(WebUtil.getJSONInt(fieldJSON, "columnSpan"));
            this.setReviewRequired(WebUtil.getJSONBoolean(fieldJSON, "reviewRequired"));
            this.setRequired(WebUtil.getJSONBoolean(fieldJSON, "required"));
            this.setMulti(WebUtil.getJSONBoolean(fieldJSON, "multi"));
            this.setDynamic(WebUtil.getJSONBoolean(fieldJSON, "dynamic"));
            this.setSection(WebUtil.getJSONString(fieldJSON, "section"));
            this.setDependencies(WebUtil.getJSONString(fieldJSON, "dependencies"));
            this.setPostBack(WebUtil.getJSONBoolean(fieldJSON, "postBack"));
            this.setDisplayOnly(WebUtil.getJSONBoolean(fieldJSON, "displayOnly"));
            this.setAuthoritative(WebUtil.getJSONBoolean(fieldJSON, "authoritative"));
            this.setLabel(WebUtil.getJSONString(fieldJSON, "label"));
            this.setAction(WebUtil.getJSONString(fieldJSON, "action"));
            this.setFilter(WebUtil.getJSONString(fieldJSON, "filter"));
            this.setTemplate(WebUtil.getJSONString(fieldJSON, "template"));
            this.setCategoryName(WebUtil.getJSONString(fieldJSON, "categoryName"));

            this.setApplication(WebUtil.getJSONString(fieldJSON, "application"));
            this.setRole(WebUtil.getJSONString(fieldJSON, "role"));

            this.setPermission(WebUtil.getJSONBoolean(fieldJSON, "permission"));
            this.setSortable(WebUtil.getJSONBoolean(fieldJSON, "sortable"));
            this.setIncomplete(WebUtil.getJSONBoolean(fieldJSON, "incomplete"));

            this.setScript(WebUtil.getJSONString(fieldJSON, "script"));
            this.setRuleId(WebUtil.getJSONString(fieldJSON, "rule"));

            this.setValue(WebUtil.getJSONString(fieldJSON, "defaultValue"));
            this.setPreviousValue(WebUtil.getJSONString(fieldJSON, "previousValue"));

            this.setValidationRuleId(WebUtil.getJSONString(fieldJSON, "validationRule"));
            this.setValidationScript(WebUtil.getJSONString(fieldJSON, "validationScript"));

            this.setDependentAppName(WebUtil.getJSONString(fieldJSON, "dependentApp"));
            this.setDependentSchemaAttr(WebUtil.getJSONString(fieldJSON, "dependentAttr"));

            this.setAllowedValues(fieldJSON);
            this.setOwner(fieldJSON);

            this.setPriority(WebUtil.getJSONInt(fieldJSON, "priority"));
            this.setFormat(WebUtil.getJSONString(fieldJSON, "format"));
            this.setDisplayType(WebUtil.getJSONString(fieldJSON, "displayType"));

            // Convert JSON to Dynamic object for extended attributes
            JSONObject attributesJSON = WebUtil.getJSONObject(fieldJSON, "attributes");
            if(null != attributesJSON) {
                JSONObject readOnlyJSONObject = WebUtil.getJSONObject(attributesJSON, "readOnly");
                JSONObject hiddenJSONObject = WebUtil.getJSONObject(attributesJSON, "hidden");

                readOnly = DynamicValueBooleanHolder.createFromJsonObjectOrDefault(readOnlyJSONObject);
                hidden = DynamicValueBooleanHolder.createFromJsonObjectOrDefault(hiddenJSONObject);
            }
            this.setAttributes(WebUtil.getJSONMap(fieldJSON, "attributes"));

        } catch (JSONException jsoe) {
            if (log.isWarnEnabled()) {
                log.warn("Exception during field constructor: " + jsoe.getMessage(), jsoe);
            }
        } catch (GeneralException ge) {
            if (log.isWarnEnabled()) {
                log.warn("Exception during field constructor: " + ge.getMessage(), ge);
            }
        }
    }

    /**
     * Returns JSON object
     */
    public JSONObject getJSON() {
        JSONObject fieldJSON = new JSONObject();
        try {
            fieldJSON.put("id", FieldDTO.uuid());
            fieldJSON.put("name", this.getName());
            fieldJSON.put("originalName", this.getOriginalName());
            fieldJSON.put("type", this.getType());
            fieldJSON.put("displayName", this.getDisplayName());
            fieldJSON.put("localizedDisplayName", this.getLocalizedDisplayName());
            fieldJSON.put("description", this.getDescription());
            fieldJSON.put("helpKey", this.getHelpKey());
            fieldJSON.put("inputTemplate", this.getInputTemplate());
            fieldJSON.put("required", this.isRequired());
            fieldJSON.put("reviewRequired", this.isReviewRequired());
            fieldJSON.put("multi", this.isMulti());
            fieldJSON.put("dynamic", this.isDynamic());
            fieldJSON.put("section", this.getSection());
            fieldJSON.put("dependencies", this.getDependencies());
            fieldJSON.put("postBack", this.isPostBack());
            fieldJSON.put("displayOnly", this.isDisplayOnly());
            fieldJSON.put("authoritative", this.isAuthoritative());
            fieldJSON.put("label",  this.getLabel());
            fieldJSON.put("action",  this.getAction());
            fieldJSON.put("filter", this.getFilter());
            fieldJSON.put("columnSpan", this.getColumnSpan());
            fieldJSON.put("priority", this.getPriority());
            fieldJSON.put("format", this.getFormat());
            fieldJSON.put("displayType", this.getDisplayType());
            fieldJSON.put("template", this.getTemplate());
            fieldJSON.put("categoryName", this.getCategoryName());

            fieldJSON.put("defaultValue", this.getValue());
            fieldJSON.put("previousValue", this.getPreviousValue());

            fieldJSON.put("application", this.getApplication());
            fieldJSON.put("role", this.getRole());

            fieldJSON.put("permission", this.isPermission());
            fieldJSON.put("sortable", this.isSortable());
            fieldJSON.put("incomplete", this.isIncomplete());

            /** Get Allowed Values **/  
            if(this.getAllowedValuesList()!=null && !this.getAllowedValuesList().isEmpty()) {
                fieldJSON.put("allowedValues", this.getAllowedValuesList());
                fieldJSON.put("allowedValuesType", FormDTO.TYPE_LITERAL);
            } else if(this.getAllowedValuesRuleId()!=null) {
                fieldJSON.put("allowedValues", this.getAllowedValuesRuleId());
                fieldJSON.put("allowedValuesType", FormDTO.TYPE_RULE);
            } else if(this.getAllowedValuesScript()!=null) {
                fieldJSON.put("allowedValues", this.getAllowedValuesScript());
                fieldJSON.put("allowedValuesType", FormDTO.TYPE_SCRIPT);
            } else {
                fieldJSON.put("allowedValuesType", FormDTO.TYPE_NONE);
            }

            /** Get Owner Value **/
            if(this.getOwnerValue()!=null && !this.getOwnerValue().equals("")) {
                fieldJSON.put("owner", this.getOwnerValue());
                fieldJSON.put("ownerType", FormDTO.TYPE_LITERAL);
                if(this.getOwnerValue().equals(FormDTO.TYPE_APP_OWNER)) {
                    fieldJSON.put("ownerType", FormDTO.TYPE_APP_OWNER);
                } else if(this.getOwnerValue().equals(FormDTO.TYPE_ROLE_OWNER)){
                    fieldJSON.put("ownerType", FormDTO.TYPE_ROLE_OWNER);
                } else if(this.getOwnerValue().equals(FormDTO.TYPE_OWNER_REQUESTER)) {
                    fieldJSON.put("ownerType", TemplateDTO.TYPE_OWNER_REQUESTER);   
                }
            } else if(this.getOwnerRuleId()!=null) {
                fieldJSON.put("owner", this.getOwnerRuleId());
                fieldJSON.put("ownerType", FormDTO.TYPE_RULE);
            } else if(this.getOwnerScript()!=null) {
                fieldJSON.put("owner", this.getOwnerScript());
                fieldJSON.put("ownerType", FormDTO.TYPE_SCRIPT);
            } else {
                fieldJSON.put("ownerType", FormDTO.TYPE_NONE);
            }

            fieldJSON.put("script", this.getScript());
            fieldJSON.put("rule", this.getRuleId());
            fieldJSON.put("validationScript", this.getValidationScript());
            fieldJSON.put("validationRule", this.getValidationRuleId());
            fieldJSON.put("dependentApp", this.getDependentAppName());
            fieldJSON.put("dependentAttr", this.getDependentSchemaAttr());

            fieldJSON.put("attributes", this.getAttributes());

        } catch (JSONException jsoe) {
            log.warn("Unable to serialize FieldDTO to json: " + jsoe.getMessage());
        }
        return fieldJSON;
    }

    ////////////////////////////////////////////////////////////////////////////
    //                                                                        //
    // METHODS                                                                //
    //                                                                        //
    ////////////////////////////////////////////////////////////////////////////

    public Button commit(Button button) {
        if(button == null) {
            button = new Button();
        }
        button.setAction((String)Util.nullify(this.getAction()));
        button.setLabel((String)Util.nullify(this.getLabel()));

        return button;
    }

    public Field commit(Field field) {
        if(field == null) {
            field = new Field();
        }

        try {
            field.setName((String)Util.nullify(this.getName()));
            field.setOriginalName((String) Util.nullify(this.getOriginalName()));
            field.setDisplayName((String)Util.nullify(this.getDisplayName()));
            field.setDescription((String)Util.nullify(this.getDescription()));
            field.setHelpKey((String)Util.nullify(this.getHelpKey()));
            field.setNormalizedType((String)Util.nullify(this.getType()));
            field.setInputTemplate((String)Util.nullify(this.getInputTemplate()));

            field.setReviewRequired(this.isReviewRequired());
            field.setRequired(this.isRequired());
            field.setMulti(this.isMulti());
            field.setPostBack(this.postBack);
            field.setDisplayOnly(this.displayOnly);
            field.setAuthoritative(this.authoritative);
            field.setAllowedValuesDynamic(this.isDynamic());
            field.setSection(Util.trimnull(this.getSection()));
            field.setColumnSpan(this.getColumnSpan());
            field.setFilterString(this.filter);

            field.setDisplayType((String)Util.nullify(this.getDisplayType()));
            field.setApplication((String)Util.nullify(this.getApplication()));
            field.setRole((String)Util.nullify(this.getRole()));
            field.setFormat((String)Util.nullify(this.getFormat()));
            field.setTemplate((String)Util.nullify(this.getTemplate()));
            field.setIncomplete(this.isIncomplete());
            field.setPermission(this.isPermission());
            field.setPriority(this.getPriority());
            field.setDependencies((String)Util.nullify(this.getDependencies()));
            field.setCategoryName((String)Util.nullify(this.getCategoryName()));

            field.setSortable(this.isSortable());

            if(this.getScript()!=null && !this.getScript().equals("")) {
                field.setScript(new Script(this.getScript()));
            } else {
                field.setScript(null);
            }

            if(this.getRuleId()!=null && !this.getRuleId().equals("")) {
                Rule rule = getContext().getObjectByName(Rule.class, this.getRuleId());
                field.setFieldRule(rule);
            } else {
                field.setFieldRule(null);
            }

            field.setValue((String)Util.nullify(this.getValue()));
            field.setPreviousValue(Util.nullify(this.getPreviousValue()));

            if(this.getValidationRuleId()!=null && !this.getValidationRuleId().equals("")) {
                Rule rule = getContext().getObjectByName(Rule.class, this.getValidationRuleId());
                field.setValidationRule(rule);
            } else {
                field.setValidationRule(null);
            }

            if(this.getValidationScript()!=null && !this.getValidationScript().equals("")) {
                field.setValidationScript(new Script(this.getValidationScript()));
            } else {
                field.setValidationScript(null);
            }

            field.setAllowedValuesDefinition(getAllowedValues());
            // jsl - had been setting this older property redundantly, clear
            // it out to avoid confusion, must always be using the DynamicValue now 
            field.setAllowedValues(null);
            field.setOwnerDefinition(getOwner());
            field.setAttributes(this.getAttributes());
            field.setHiddenDefinition(hidden.convertToDynamicValue(getContext()));
            field.setReadOnlyDefinition(readOnly.convertToDynamicValue(getContext()));
            if(!Util.isNullOrEmpty(this.getDependentAppName()) && !Util.isNullOrEmpty(this.getDependentSchemaAttr())) {
                field.setAppDependency(this.getDependentAppName(), this.getDependentSchemaAttr());
            } else {
                field.setAppDependency(null);
            }
        } catch(GeneralException ge) {
            log.warn("Unable to commit field: " + ge.getMessage());
        }
        return field;
    }

    public static List<Field> commit(List<FieldDTO> dtos, List<Field> fieldObjs) 
        throws GeneralException {

        List<Field> fields = null;
        if (dtos != null && dtos.size() > 0) {
            fields = new ArrayList<Field>();
            for (FieldDTO dto : dtos) {

                Field field = findField(fieldObjs, dto.getOriginalName());

                fields.add(dto.commit(field));
            }
        }
        return fields;
    }

    private static Field findField(List<Field> fieldObjs, String name) {

        Field field = null;
        if (fieldObjs != null) {
            for (Field f : fieldObjs) {
                if (name.equals(f.getName())) {
                    field = f;
                    break;
                }
            }
        }
        return field;
    }

    private DynamicValue getAllowedValues() {
        try {
            if(this.getAllowedValuesRuleId()!=null) {
                Rule rule = getContext().getObjectByName(Rule.class, this.getAllowedValuesRuleId());
                return new DynamicValue(rule, null, null);
            } else if(this.getAllowedValuesScript()!=null) {
                return new DynamicValue(null, new Script(this.getAllowedValuesScript()), null);
            } else if (!Util.isEmpty(this.getAllowedValuesList())){
                return new DynamicValue(null, null, this.getAllowedValuesList());
            }
        } catch(GeneralException ge) {
            log.warn("Unable to get allowed values rule: " + ge.getMessage());
        }
        return null;
    }

    /** Sets the allowed values dynamic value on the field object based on what is coming back from the JSON **/
    private void setAllowedValues(JSONObject fieldJSON ) throws JSONException, GeneralException {

        String type = WebUtil.getJSONString(fieldJSON, "allowedValuesType");
        if(type!=null) {

            if(type.equals(FormDTO.TYPE_LITERAL)) {
                JSONArray allowedValues = WebUtil.getJSONArray(fieldJSON, "allowedValues");
                if(allowedValues!=null && allowedValues.length()>0) {
                    List<Object> values = new ArrayList<Object>();
                    for(int j=0; j<allowedValues.length(); j++) {
                        Object val = allowedValues.get(j);
                        // bug#27390 if val is JSONArray we need to convert to List
                        if (val instanceof JSONArray) {
                            List<String> allowedValuesList = new ArrayList<String>();
                            JSONArray allowedValuesJsonArray = (JSONArray)val;
                            if (allowedValuesJsonArray != null) {
                                int len = allowedValuesJsonArray.length();
                                for (int i=0; i < len; i++){
                                    allowedValuesList.add(allowedValuesJsonArray.get(i).toString());
                                }
                                values.add(allowedValuesList);
                            }
                        }
                        else {
                            values.add(val);
                        }
                    }
                    this.setAllowedValuesList(values);
                } 
            } else if(type.equals(FormDTO.TYPE_RULE)) {
                this.setAllowedValuesRuleId(WebUtil.getJSONString(fieldJSON, "allowedValues"));                
            } else if(type.equals(FormDTO.TYPE_SCRIPT)) {
                this.setAllowedValuesScript(WebUtil.getJSONString(fieldJSON, "allowedValues"));            
            }
        }
    }

    private void setOwner(JSONObject fieldJSON) throws JSONException, GeneralException {
        String ownerType = WebUtil.getJSONString(fieldJSON, "ownerType");
        String owner = WebUtil.getJSONString(fieldJSON, "owner");

        if(owner!=null && !owner.equals("")) {
            if(ownerType.equals(FormDTO.TYPE_SCRIPT)) {
                this.setOwnerScript(owner);
            } else if(ownerType.equals(FormDTO.TYPE_RULE)) {
                this.setOwnerRuleId(owner);
            } else {
                this.setOwnerValue(owner);
            }
        }
    }

    private DynamicValue getOwner() {
        try {
            if(this.getOwnerRuleId()!=null) {
                Rule rule = getContext().getObjectByName(Rule.class, this.getOwnerRuleId());
                return new DynamicValue(rule, null, null);
            } else if(this.getOwnerScript()!=null) {
                return new DynamicValue(null, new Script(this.getOwnerScript()), null);
            } else {
                return new DynamicValue(null, null, this.getOwnerValue());
            }
        } catch(GeneralException ge) {
            log.warn("Unable to get owner rule: " + ge.getMessage());
        }
        return null;
    }

    ////////////////////////////////////////////////////////////////////////////
    //                                                                        //
    // GETTERS AND SETTERS                                                    //
    //                                                                        //
    ////////////////////////////////////////////////////////////////////////////

    public String getName() {
        return name;
    }
    
    public String getOriginalName() {
        return originalName;
    }
    
    public void setOriginalName(String val) {
        originalName = val;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getHelpKey() {
        return helpKey;
    }

    public void setHelpKey(String helpKey) {
        this.helpKey = helpKey;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getLocalizedDisplayName() {
        return localizedDisplayName;
    }

    public void setLocalizedDisplayName(String localizedDisplayName) {
        this.localizedDisplayName = localizedDisplayName;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getInputTemplate() {
        return inputTemplate;
    }

    public void setInputTemplate(String inputTemplate) {
        this.inputTemplate = inputTemplate;
    }

    public boolean isReviewRequired() {
        return reviewRequired;
    }

    public void setReviewRequired(boolean reviewRequired) {
        this.reviewRequired = reviewRequired;
    }

    public boolean isRequired() {
        return required;
    }

    public void setRequired(boolean required) {
        this.required = required;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getScript() {
        return script;
    }

    public void setScript(String script) {
        this.script = script;
    }

    public String getRuleId() {
        return ruleId;
    }

    public void setRuleId(String ruleId) {
        this.ruleId = ruleId;
    }

    public String getValidationRuleId() {
        return validationRuleId;
    }

    public void setValidationRuleId(String validationRuleId) {
        this.validationRuleId = validationRuleId;
    }

    public String getValidationScript() {
        return validationScript;
    }

    public void setValidationScript(String validationScript) {
        this.validationScript = validationScript;
    }

    public String getAllowedValuesRuleId() {
        return allowedValuesRuleId;
    }

    public void setAllowedValuesRuleId(String allowedValuesRuleId) {
        this.allowedValuesRuleId = allowedValuesRuleId;
    }

    public String getAllowedValuesScript() {
        return allowedValuesScript;
    }

    public void setAllowedValuesScript(String allowedValuesScript) {
        this.allowedValuesScript = allowedValuesScript;
    }

    public String getOwnerRuleId() {
        return ownerRuleId;
    }

    public void setOwnerRuleId(String ownerRuleId) {
        this.ownerRuleId = ownerRuleId;
    }

    public String getOwnerScript() {
        return ownerScript;
    }

    public void setOwnerScript(String ownerScript) {
        this.ownerScript = ownerScript;
    }

    public String getOwnerValue() {
        return ownerValue;
    }

    public void setOwnerValue(String ownerValue) {
        this.ownerValue = ownerValue;
    } 
    
    public List<Object> getAllowedValuesList() {
        return allowedValuesList;
    }

    public void setAllowedValuesList(List<Object> allowedValuesList) {
        this.allowedValuesList = allowedValuesList;
    }

    public boolean isMulti() {
        return multi;
    }

    public void setMulti(boolean multi) {
        this.multi = multi;
    }
    
    public boolean isDynamic() {
        return dynamic;
    }

    public void setDynamic(boolean dynamic) {
        this.dynamic = dynamic;
    }

    public String getSection() {
        return section;
    }

    public void setSection(String section) {
        this.section = section;
    }

    public String getDependencies() {
        return dependencies;
    }

    public void setDependencies(String s) {
        dependencies = s;
    }

    public int getColumnSpan() {
        return columnSpan;
    }

    public void setColumnSpan(int columnSpan) {
        this.columnSpan = columnSpan;
    }

    public boolean isPostBack() {
        return postBack;
    }

    public void setPostBack(boolean postBack) {
        this.postBack = postBack;
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
    
    public DynamicValueBooleanHolder getReadOnly() {
        return readOnly;
    }
    
    public void setReadOnly(DynamicValueBooleanHolder val) {
        readOnly = val;
    }
    
    public DynamicValueBooleanHolder getHidden() {
        return hidden;
    }
    
    public String getDependentAppName() {
        return dependentAppName;
    }

    public void setDependentAppName(String dependentAppName) {
        this.dependentAppName = dependentAppName;
    }

    public String getDependentSchemaAttr() {
        return dependentSchemaAttr;
    }

    public void setDependentSchemaAttr(String dependentSchemaAttr) {
        this.dependentSchemaAttr = dependentSchemaAttr;
    }

    public void setHidden(DynamicValueBooleanHolder val) {
        hidden = val;
    }
    
    public boolean isDisplayOnly() {
        return displayOnly;
    }
    
    public void setDisplayOnly(boolean val) {
        displayOnly = val;
    }
    
    public boolean isAuthoritative() {
        return authoritative;
    }
    
    public void setAuthoritative(boolean val) {
        authoritative = val;
    }
    
    public String getFilter() {
        return filter;
    }
    
    public void setFilter(String val) {
        filter = val;
    }

    public boolean isPermission() {
        return permission;
    }

    public void setPermission(boolean permission) {
        this.permission = permission;
    }

    public boolean isIncomplete() {
        return incomplete;
    }

    public void setIncomplete(boolean incomplete) {
        this.incomplete = incomplete;
    }

    public String getApplication() {
        return application;
    }

    public void setApplication(String application) {
        this.application = application;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public String getTemplate() {
        return template;
    }

    public void setTemplate(String template) {
        this.template = template;
    }

    public String getDisplayType() {
        return displayType;
    }

    public void setDisplayType(String displayType) {
        this.displayType = displayType;
    }

    public Attributes<String,Object> getAttributes() {
        return attributes;
    }

    public void setAttributes(Attributes<String,Object> attributes) {
        this.attributes = attributes;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public boolean isSortable() {
        return sortable;
    }

    public void setSortable(boolean sortable) {
        this.sortable = sortable;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public void setCategoryName(String categoryName) {
        this.categoryName = categoryName;
    }

    public String getPreviousValue() {
        return previousValue;
    }

    public void setPreviousValue(String previousValue) {
        this.previousValue = previousValue;
    }
}
