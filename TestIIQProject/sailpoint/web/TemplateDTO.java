/* (c) Copyright 2009 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * @ignore
 * Not in use anymore in Form Editor.
 * Get rid of this DTO!
 */

package sailpoint.web;

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
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONWriter;

import sailpoint.object.Application;
import sailpoint.object.DynamicValue;
import sailpoint.object.Field;
import sailpoint.object.Filter;
import sailpoint.object.Form;
import sailpoint.object.FormRef;
import sailpoint.object.PropertyInfo;
import sailpoint.object.QueryOptions;
import sailpoint.object.Rule;
import sailpoint.object.Script;
import sailpoint.object.Template;
import sailpoint.object.Template.Usage;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.util.WebUtil;

/**
 * @author peter.holcomb
 *
 */
public class TemplateDTO extends BaseDTO {

    private static final Log log = LogFactory.getLog(TemplateDTO.class);
    public static final String TYPE_LITERAL = "literal";
    public static final String TYPE_RULE = "rule";
    public static final String TYPE_SCRIPT = "script";
    public static final String TYPE_NONE = "none";
    public static final String TYPE_ROLE_OWNER = "IIQRoleOwner";
    public static final String TYPE_APP_OWNER = "IIQApplicationOwner";
    public static final String TYPE_OWNER_REQUESTER = "IIQRequester";

    String name;
    String description;
    List<FieldDTO> fields;
    List<Field> fieldObjs;
    FormReferenceDTO formRefDTO;
    String fieldsString;
    String application;
    String owner;
    String ownerType;
    Usage usage;
    String schemaObjectType;
    DynamicValue ownerDefinition;

    /** We need to initialize the rule picker with the rule name if the owner is set to a rule - Bug 6386**/
    String ownerRuleName;

    String id;

    public TemplateDTO() {
        this.id = Util.uuid();
        this.fields = new ArrayList<FieldDTO>();
    }

    public TemplateDTO(Template src) {
        this.id = Util.uuid();
        this.usage = src.getUsage();
        this.schemaObjectType = src.getSchemaObjectType();

        FormRef refForm = src.getFormRef();
        if(refForm != null) {
            String formId = refForm.getId();
            String formName = refForm.getName();
            this.formRefDTO = new FormReferenceDTO(formId,formName);
            String formIdOrName = (!Util.isEmpty(formId))? formId : formName; 
            populateReferenceFormDetails(formIdOrName, Util.isEmpty(formId));
        }
        else {
            this.name = src.getName();
            this.description = src.getDescription();

            String[] ownerFields = getOwnerDefinition(src.getOwnerDefinition());
            this.owner = ownerFields[0];
            this.ownerType = ownerFields[1];
            
            // jsl - changed this to be an Application reference,
            // will auto-upgrade purview if we see it
            Application app = src.getApplication();
            if (app != null)
                this.application = app.getId();
            else 
                this.application = src.getPurview();

            this.fields = new ArrayList<FieldDTO>();
            List<Field> flds = src.getFields();
            if (flds != null) {
                for (Field field : flds) {
                    fields.add(new FieldDTO(field));
                }
                fieldObjs = src.getFields();
            }
        }
    }

    public TemplateDTO(TemplateDTO tempDTO) {
        this.id = tempDTO.getUid();
        this.name = tempDTO.getName();
        this.description = tempDTO.getDescription();
        this.fields = new ArrayList<FieldDTO>();
        this.usage = tempDTO.getUsage();
        this.schemaObjectType = tempDTO.getSchemaObjectType();
        this.application = tempDTO.getApplication();
        this.formRefDTO = tempDTO.getFormRefDTO();
        this.fieldObjs = tempDTO.fieldObjs;
        this.ownerDefinition = tempDTO.ownerDefinition;
        this.fields = tempDTO.getFields();
        this.fieldsString = tempDTO.fieldsString;
    }

    //In Role provisioning policy, we need to display Application name and description
    //Since in FormReference, we only save id, here fetching other fields explicitly
    public void populateReferenceFormDetails(String formIdOrName, boolean useName) {
        if (!Util.isEmpty(formIdOrName)) {
            try {

                Form form;
                if (useName) {
                    form = getContext().getObjectByName(Form.class, formIdOrName);
                } else {
                    form = getContext().getObjectById(Form.class, formIdOrName);
                }

                if (form != null) {
                    this.name = form.getName();
                    this.description = form.getDescription();
                    this.ownerDefinition = form.getOwnerDefinition();
                    // jsl - we now have a real Application reference if that helps
                    this.application = form.getApplicationId();
                    // bug#24476 - UI behaves like inline form, it should display the ref form popup
                    if (Util.isNullOrEmpty(this.formRefDTO.getReferenceId())) {
                        this.formRefDTO.setReferenceId(form.getId());
                    }
                }
            }
            catch (GeneralException e) {
                if (log.isWarnEnabled()) {
                    log.warn("Error while getting form details", e);
                }
            }
        }
    }


    public void updateFields() {
        if(this.fieldsString!=null && !this.fieldsString.equals("")) {
            try {
                this.fields = new ArrayList<FieldDTO>();
                
                JSONArray fieldsJSON = new JSONArray(this.fieldsString);
                for(int i=0; i<fieldsJSON.length(); i++) {
                    JSONObject fieldJSON = fieldsJSON.getJSONObject(i);
                    String name = WebUtil.getJSONString(fieldJSON, "name");
                    if(name!=null && !name.equals("")) {

                        FieldDTO dto = new FieldDTO(fieldJSON);                

                        this.fields.add(dto);
                    }
                }

            } catch (JSONException jsoe) {
                if (log.isWarnEnabled())
                    log.warn("Exception during template commit: " + jsoe.getMessage(), jsoe);
            } 
        }
    }

    public void commit(Template template) {

        template.setDescription((String)Util.nullify(this.getDescription()));
        template.setUsage(usage);
        template.setSchemaObjectType(schemaObjectType);

        template.setName((String)Util.nullify(this.getName()));

        Application app = null;
        try {
            // jsl - can be null when creating new apps or the empty string
            if (!Util.isEmpty(this.application)) {
                app = getContext().getObjectById(Application.class, this.application);
            } else if (formRefDTO != null) {
                //in case of form reference, if application is empty, populate it from the form
                populateReferenceFormDetails(formRefDTO.getReferenceId(),false);
                app = getContext().getObjectById(Application.class, this.application);
            }

            template.setApplication(app);
            template.setPurview(null);
            if(formRefDTO != null) {
                template.setFormRef(formRefDTO.getFormRef());
                template.setOwnerDefinition(ownerDefinition);
            }
            else {
                if(this.getOwnerType()!=null && !this.getOwnerType().equals("")) {
                    if(this.getOwnerType().equals(TYPE_SCRIPT)) {
                        template.setOwnerDefinition(new DynamicValue(null, new Script(this.getOwner()), null));
                    } else if(this.getOwnerType().equals(TYPE_RULE)) {
                        try {
                            Rule rule = getContext().getObjectById(Rule.class, this.getOwner());
                            template.setOwnerDefinition(new DynamicValue(rule, null, null));
                        } catch(GeneralException ge) {
                            log.warn("Unable to set rule: " + ge.getMessage());
                        }
                    } else {
                        template.setOwnerDefinition(new DynamicValue(null, null, this.getOwner()));
                    }
                } else {
                    template.setOwnerDefinition(null);
                }
                this.updateFields();
                template.setFields(FieldDTO.commit(this.fields, this.fieldObjs));
            }
        }
        catch (GeneralException e) {
            // app would have to have been deleted out from under us
            log.error("GeneralException while commiting template: " + e.getMessage());
        }
    }

    /** JSON Getters **/
    public String getJSON() {
        Writer jsonString = new StringWriter();
        JSONWriter jsonWriter = new JSONWriter(jsonString);

        try {
            jsonWriter.object();
            jsonWriter.key("totalCount");
            int count = (this.fields==null) ? 0 : this.fields.size();
            jsonWriter.value(count);

            jsonWriter.key("id");
            jsonWriter.value(this.id);

            jsonWriter.key("objects");
            List<JSONObject> fieldList = new ArrayList<JSONObject>();
            if(this.fields!=null) {
                for(FieldDTO dto : this.fields) {                    
                    fieldList.add(dto.getJSON());
                }
            }
            jsonWriter.value(fieldList);
            if(this.formRefDTO != null) {
                jsonWriter.key("FormRef");
                jsonWriter.value(formRefDTO.getJSON());
            }
            jsonWriter.endObject();

        } catch (JSONException e) {
            log.error("Could not get JSON for activity data sources");
        } 
        log.debug("json: " +jsonString);
        return jsonString.toString();
    }


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
            log.error("Could not get JSON for Tempalte DTO");
        }
        return jsonString.toString();
    }

    public static String[] getOwnerDefinition(DynamicValue dynamicValue) {
        String[] ownerFields = new String[2];

        if(dynamicValue!=null) {
            if(dynamicValue.getScript()!=null) {
                ownerFields[0] = dynamicValue.getScript().getSource();
                ownerFields[1] = TYPE_SCRIPT;
            } else if(dynamicValue.getRule()!=null) {
                ownerFields[0] = dynamicValue.getRule().getId();
                ownerFields[1] = TYPE_RULE;
            } else if(dynamicValue.getValue()!=null) {
                ownerFields[0] = dynamicValue.getValue().toString();
                if(ownerFields[0].equals(TYPE_APP_OWNER)) {
                    ownerFields[1] = TYPE_APP_OWNER;
                } else if(ownerFields[0].equals(TYPE_ROLE_OWNER)){
                    ownerFields[1] = TYPE_ROLE_OWNER;
                }  else if(ownerFields[0].equals(TYPE_OWNER_REQUESTER)) {
                    ownerFields[1] = TYPE_OWNER_REQUESTER;
                }
            }
        }
        return ownerFields;
    }

    /** Returns just the name, application and field names for this json.  Used for
     * validating field names on save (Bug #5924) */
    public String getSimpleJSON() {
        //in case of form reference, populate form details
        if(formRefDTO != null) {
            boolean useName = false;
            String formIdOrName = getFormRefId();
            if(Util.isEmpty(formIdOrName)) {
                formIdOrName = getFormRefName();
                useName = true;
            }
            populateReferenceFormDetails(formIdOrName, useName);
        }
        Writer jsonString = new StringWriter();
        JSONWriter jsonWriter = new JSONWriter(jsonString);
        try {
            jsonWriter.object();
            jsonWriter.key("name");
            jsonWriter.value(getName());

            jsonWriter.key("id");
            jsonWriter.value(this.getId());

            // jsl - wasn't sure if I could change to "application"
            jsonWriter.key("purview");
            jsonWriter.value(getApplicationName());

            jsonWriter.key("fields");
            List<JSONObject> fieldList = new ArrayList<JSONObject>();
            if(this.fields!=null) {
                for(FieldDTO dto : this.fields) {
                    JSONObject fieldJSON = new JSONObject();
                    fieldJSON.put("name", dto.getName());
                    fieldList.add(fieldJSON);
                }
            }
            jsonWriter.value(fieldList);

            if(formRefDTO != null) {
                jsonWriter.key("FormRef");
                jsonWriter.value(formRefDTO.getJSON());
            }
            jsonWriter.endObject();
        } catch (JSONException e) {
            log.error("Could not get simple JSON for Template DTO");
        }

        return jsonString.toString();
    }


    public String getName() {
        return name;
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

    public List<FieldDTO> getFields() {
        return fields;
    }

    public void setFields(List<FieldDTO> fields) {
        this.fields = fields;
    }

    public String getFieldsString() {
        return fieldsString;
    }

    public void setFieldsString(String fieldsString) {
        this.fieldsString = fieldsString;
    }

    public FormReferenceDTO getFormRefDTO() {
        return formRefDTO;
    }

    public void setFormRefDTO(FormReferenceDTO frmRefDTO) {
        this.formRefDTO = frmRefDTO;
    }

    public String getApplication() {
        return application;
    }

    public String getApplicationName() {

        if (this.application != null) {
            try {
                return WebUtil.getDisplayNameForId("Application", this.application);
            } catch (GeneralException ge) {
                log.warn("Unable to get display name for purview: " + this.application);
            }
        }
        return "";
    }

    public void setApplication(String name) {
        this.application = name;
    }

    // jsl - probably seen use this naming convention in the xhtml apges

    public String getPurview() {
        return getApplication();
    }

    public String getPurviewName() {
        return getApplicationName();
    }

    public void setPurview(String purview) {
        setApplication(purview);
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    public String getOwnerType() {
        return ownerType;
    }

    public void setOwnerType(String ownerType) {
        this.ownerType = ownerType;
    }

    /** Get the rule name if the owner is set to a rule **/
    public String getOwnerRuleName() {
        if(ownerRuleName==null && this.getOwnerType()!=null && this.getOwner()!=null) {
            if(this.getOwnerType().equals(TYPE_RULE)) {
                QueryOptions qo = new QueryOptions();
                qo.add(Filter.eq("id", getOwner()));
                try {
                    Iterator<Object[]> rows = getContext().search(Rule.class, qo, Arrays.asList(new String [] { "name" }));
                    if(rows.hasNext()) {
                        Object[] row = rows.next();
                        ownerRuleName = (String)row[0];
                    }
                } catch(GeneralException ge) {
                    log.warn("Unable to determine rule name: " + ge.getMessage());
                }
            }
        }
        return ownerRuleName;
    }

    public void setOwnerRuleName(String ownerRuleName) {
        this.ownerRuleName = ownerRuleName;
    }

    public Usage getUsage() {
        return usage;
    }

    public void setUsage(Usage usage) {
        this.usage = usage;
    }

    public String getSchemaObjectType() {
        return schemaObjectType;
    }

    public void setSchemaObjectType(String schemaObjectType) {
        this.schemaObjectType = schemaObjectType;
    }

    /**
     * @return - ID of the referenced form within FormRef object
     */
    public String getFormRefId() {
        return null != formRefDTO ? formRefDTO.getReferenceId() : null;
    }

    /**
     * @return - Name of the referenced form within FormRef object
     */
    public String getFormRefName() {
        return (formRefDTO != null) ? formRefDTO.getReferenceName() : null;
    }
}
