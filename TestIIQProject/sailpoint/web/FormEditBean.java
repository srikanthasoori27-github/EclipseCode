/* (c) Copyright 2016 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * Previously TemplateEditBean.
 * A backing bean to serve the functionalities of form editor.
 *
 * It is used by Role, Application and Identity provisioning policy forms. 
 *
 * @author rahul.borate
 */

package sailpoint.web;

import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
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
import sailpoint.object.Configuration;
import sailpoint.object.Form;
import sailpoint.object.Form.Usage;
import sailpoint.object.PropertyInfo;
import sailpoint.object.Schema;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Pair;
import sailpoint.tools.Util;
import sailpoint.web.form.editor.FormDTO;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.util.WebUtil;

public class FormEditBean extends BaseBean {

    private static final Log log = LogFactory.getLog(FormEditBean.class);

    ////////////////////////////////////////////////////////////////////////////
    //                                                                        //
    // FIELDS                                                                 //
    //                                                                        //
    ////////////////////////////////////////////////////////////////////////////

    private List<FormDTO> formDTOs;

    /**
     * A Map of FormDTO ID to Form ID.
     * This is only available if a constructor that accepts Forms was used.
     */
    private Map<String,String> formToFormDtoMap;

    private FormDTO formDTO;
    private Map<String, Boolean> formSelections;
    private String formId;
    private Usage usage;
    private String objectType;
    private String formRefId;
    private String formRefName;

    /** 
     * Flag to indicate saveReference is being invoked for Reference form
     * its required when modifying reference form to inline form
     * based on this flag, we will clear formRefernceDTO from formDTO
     */ 
    private boolean fromReference = false;

    /**
     * Some objects, such as applications, have a list of forms and their usage.
     * For example, the applications object has a use for a create, update, and delete form.
     * The usages are stored in the systemConfig and used to show the list of forms on the ui
     */
    private List<String> usages;
    private Map<String, FormDTO> formUsageMap;

    ////////////////////////////////////////////////////////////////////////////
    //                                                                        //
    // CONSTRUCTORS                                                           //
    //                                                                        //
    ////////////////////////////////////////////////////////////////////////////

    public FormEditBean() {}

    /**
     * Construct a FormEditBean from a List of Forms
     * @param  forms  The Forms for which to create this bean.
     */
    public FormEditBean(List<Form> forms) {
        formDTOs = new ArrayList<FormDTO>();
        if(forms!=null) {
            for(Form form : forms) {
                FormDTO formDTO = new FormDTO(form);
                formDTOs.add(formDTO);
            }
        }
    }

    ////////////////////////////////////////////////////////////////////////////
    //                                                                        //
    // METHODS                                                                //
    //                                                                        //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Keep a mapping of each form and dto created for the form.
     */
    public void generateFormToFormDtoMap(List<Form> forms) {
        formToFormDtoMap = new HashMap<String,String>();
        if (null != forms) {
            for (int i=0; i < forms.size(); i++) {
                Form form = forms.get(i);
                FormDTO formDTO = getFormDTOs().get(i);

                formToFormDtoMap.put(form.getId(), formDTO.getId());
            }
        }
    }

    /**
     * Returns a list of committed form objects for saving on the object
     */
    public List<Form> commitForms() {
        List<Form> forms = new ArrayList<Form>();
        if(formDTOs != null) {
            for(FormDTO dto : formDTOs) {
                Form form = dto.commit(null);
                forms.add(form);
            }
        }
        return forms;
    }

    /**
     * Return a non-null List of the IDs of the Forms that were deleted through
     * the UI.  This is only available if the edit bean was constructed using
     * Forms.
     */
    public List<String> getDeletedFormIds() {
        List<String> deleted = new ArrayList<String>();

        if (null != formToFormDtoMap) {
            for (Map.Entry<String,String> entry : formToFormDtoMap.entrySet()) {
                String formId = entry.getKey();
                String formDtoId = entry.getValue();

                // Check if we still have a DTO with the form ID.
                boolean found = false;
                for (FormDTO dto : getFormDTOs()) {
                    if (formDtoId.equals(dto.getId())) {
                        found = true;
                        break;
                    }
                }

                // If we didn't find a DTO for the form,
                // that means that it was deleted.
                if (!found) {
                    deleted.add(formId);
                }
            }
        }

        return deleted;
    }

    ////////////////////////////////////////////////////////////////////////////
    //                                                                        //
    // ACTIONS                                                                //
    //                                                                        //
    ////////////////////////////////////////////////////////////////////////////
    /**
     * Load the form
     */
    public String loadForm() {
        // Forms cannot be uniquely identified by ID unless the FormBean
        // is stored on the session because they are IDs are freshly generated on
        // the FormDTO's creation.  For this reason we need to match on usage instead.
        // Unfortunately roles don't have usages so when we edit them we just grab the 
        // only form available and return it.
        // --Bernie
        formUsageMap = getFormUsageMap();
        if(usage == null || usage.equals("")) {
            // Assume this is a role, where it's OK to use IDs
            if (formId != null) {
                formDTO = formUsageMap.get(formId);
            }
            if (formDTO == null) {
                addForm();
            }
        } else if (Util.isNotNullOrEmpty(objectType)) {
            formDTO = getFormByObjectTypeUsageMap().get(
                    new Pair<String, String>(objectType, usage.toString()));
            if (formDTO == null) {
                addForm();
            }
            formId = formDTO.getId();
        } else {
            formDTO = formUsageMap.get(usage.toString());
            if (formDTO == null) {
                addForm();
            }
            formId = formDTO.getId();
        }

        // Load the Map if not already created.
        getFormRefMap();

        return "";
    }

    /**
     * Adds the new form
     */
    public String addForm() {
        formDTO = new FormDTO();
        formId = formDTO.getId();

        // If there is no usage assume this is a role.
        // Since the role edit keeps the bean persisted in
        // the session the ID will be stable for the duration 
        // of the editing session
        if (usage == null && formUsageMap != null) {
            formUsageMap.put(formId, formDTO);
        }

        if(usage != null) {
            formDTO.setUsage(usage);
            if (formUsageMap != null) {
                formUsageMap.put(usage.toString(), formDTO);
            }
        }

        if (Util.isNotNullOrEmpty(objectType)) {
            formDTO.setSchemaObjectType(objectType);
            if (formByObjectTypeUsageMap != null) {
                formByObjectTypeUsageMap.put(
                        new Pair<String, String>(objectType, usage.toString()), 
                        formDTO);
            }
        }

        return "";
    }

    /**
     * Create a FormDTO from the given form and add it to the formDTO
     * @param t Form to add
     * @return FormDTO created and added for the given form
     */
    public FormDTO addForm(Form f) {
        FormDTO dto = null;
        if (f != null) {
            dto = new FormDTO(f);
            if(formDTOs == null) {
                formDTOs = new ArrayList<FormDTO>();
            }

            //Add form to DTO list
            formDTOs.add(dto);

            //Add DTO to formUsageMap
            if(formUsageMap != null) {
                formUsageMap.put(dto.getUsage().toString(), dto);
            }

            //Add DTO to formByObjectTypeUsageMap
            if (dto.getSchemaObjectType() != null && formByObjectTypeUsageMap != null) {
                formByObjectTypeUsageMap.put(new Pair<String, String>(dto.getSchemaObjectType(), dto.getUsage().toString()), dto);
            }

            // Add FormRefID to formFormRefMap
            if (null != dto.getFormRefId()) {
                getFormRefMap().put(dto.getId(), dto.getFormRefId());
            }
        }

        return dto;
    }

    /**
     * Add a reference form entry to a form
     */
    public String addFormReference() {
        if(objectType != null && usage != null) {
            formDTO = getFormByObjectTypeUsageMap().remove(
                    new Pair<String, String>(objectType, usage.toString()));

            if(formDTO != null) {
                FormReferenceDTO formRefDTO = new FormReferenceDTO(formRefId, formRefName);
                formDTO.setFormRefDTO(formRefDTO);
                formByObjectTypeUsageMap.put(new Pair<String,String>(objectType,usage.toString()), formDTO);

                // Add FormRefID to formFormRefMap
                getFormRefMap().put(formDTO.getId(), formRefId);
                String formIdOrName = (!Util.isEmpty(formRefId))? formRefId : formRefName; 

                //populate application name and description
                formDTO.populateReferenceFormDetails(formIdOrName);
                fromReference = true;

                //Need to incorporate formDTO in formDTO list after inspection
                saveForm();
            }
        } else {
          //For Role provisioning policy
            boolean found = false;

            /** Figure out if reference form is already selected **/
            for(Iterator<FormDTO> formIter = getFormDTOs().iterator(); formIter.hasNext();) {
                FormDTO form = formIter.next();
                if(form.getFormRefDTO() != null && form.getFormRefId().equals(formRefId)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                FormReferenceDTO formRefDTO = new FormReferenceDTO(formRefId,formRefName);
                formDTO.setFormRefDTO(formRefDTO);
                //populate application name and description
                formDTO.populateReferenceFormDetails(formRefName);

                fromReference = true;
                saveForm();
            }
        }
        return "";
    }

    /**
     * Remove a form or list of forms based on a set of checkboxes
     */
    public String deleteForms() {      
        Map<String,Boolean> selections = getFormSelections();
        if (selections != null && getFormDTOs() != null) {
            for (String key : selections.keySet()) {
                if (selections.get(key)) {                    
                    for(Iterator<FormDTO> formIter = getFormDTOs().iterator(); formIter.hasNext();) {
                        FormDTO formDTO = formIter.next();
                        if(formDTO.getId().equals(key)) {
                            formIter.remove();
                        }
                    }
                }
            }
        }
        return "";
    }

    /**
     * Click a delete form link from a the list of usages
     */
    public String deleteForm() {
        formUsageMap = getFormUsageMap();
        List<FormDTO> formDTOs = getFormDTOs();
        FormDTO formToDelete = null;
        if (usage == null) {
            // Assume this is a role since it's the only form type
            // without a usage.  IDs are stable on the role editor
            // because the DTOs are persisted in the session
            formToDelete = formUsageMap.get(formId);
            formDTOs.remove(formToDelete);
        } else if (Util.isNotNullOrEmpty(objectType)) {
            formToDelete = getFormByObjectTypeUsageMap().get(
                    new Pair<String, String>(objectType, usage.toString()));
            formDTOs.remove(formToDelete);
        } else {
            if (formDTOs != null) {
                formToDelete = formUsageMap.get(usage.toString());
                formDTOs.remove(formToDelete);
            }
        }
        formUsageMap = null;
        formByObjectTypeUsageMap = null;
        // Remove deleted FormRefID from formFormRefMap
        if (null != formToDelete) {
            getFormRefMap().remove(formToDelete.getId());
        }
        return "";
    }

    /**
     * Save a Form
     */
    public String saveForm() {
        if(formDTOs == null) {
            formDTOs = new ArrayList<FormDTO>();
        }

        // update form items from JSON
        formDTO.updateItemsFromJSON();

        boolean found = false;

        /** Figure out where to set it on the list of form dtos **/
        for(Iterator<FormDTO> formIter = getFormDTOs().iterator(); formIter.hasNext();) {
            FormDTO form = formIter.next();
            if(Util.nullSafeEq(form.getId(), formId)) {
                form = formDTO;
                found = true;
                break;
            }
        }

        //while changing reference form to inline, clean formReferenceDTO
        if (!fromReference && formDTO.getFormRefId()!= null) {
         // Remove FormRefID to formFormRefMap
            if (usage != null) {
                getFormRefMap().remove(formDTO.getId());
            }
            formDTO.setFormRefDTO(null);
        } else {
            fromReference = false;
        }

        if(!found) {
            //form name gets lost without this copy constructor
            if(formDTO.getFormRefDTO() != null) {
                formDTOs.add(new FormDTO(formDTO));
            }
            else {
                formDTOs.add(formDTO);
            }
        }

        formUsageMap = null;
        formByObjectTypeUsageMap = null;

        return "";
    }

    ////////////////////////////////////////////////////////////////////////////
    //                                                                        //
    // GETTERS AND SETTERS                                                    //
    //                                                                        //
    ////////////////////////////////////////////////////////////////////////////

    public List<FormDTO> getFormDTOs() {
        return formDTOs;
    }

    public void setFormDTOs(List<FormDTO> DTOs) {
        formDTOs = DTOs;
    }

    public FormDTO getFormDTO() {
        if(formDTO == null) {
            loadForm();
        }
        return formDTO;
    }

    public void setFormDTO(FormDTO DTO) {
        formDTO = DTO;
    }

    public Map<String, Boolean> getFormSelections() {
        if (formSelections == null)
            formSelections = new HashMap<String,Boolean>();
        return formSelections;
    }

    public void setFormSelections(Map<String, Boolean> selections) {
        formSelections = selections;
    }

    public String getFormId() {
        return formId;
    }

    public void setFormId(String id) {
        formId = id;
    }

    public List<String> getUsages() {
        return usages;
    }

    public void setUsages(List<String> u) {
        usages = u;
    }

    public List<Usage> getUsageEnums() {
        List<Usage> usageEnums = new ArrayList<Usage>();
        if (!Util.isEmpty(usages)) {
            for (String usage : usages) {
                if (!Util.isNullOrEmpty(usage)) {
                    Usage usageEnum;
                    try {
                        usageEnum = Enum.valueOf(Usage.class, usage);
                    } catch (Exception e) {
                        usageEnum = null;
                        log.error("Failed to generate a enum for usage: " + usage, e);
                    }

                    if (usageEnum != null) {
                        usageEnums.add(usageEnum);
                    }
                }
            }
        }
        return usageEnums;
    }

    public String getTypesJSON() {
        Writer jsonString = new StringWriter();
        JSONWriter jsonWriter = new JSONWriter(jsonString);

        try {
            jsonWriter.object();
            jsonWriter.key("objects");

            List<JSONObject> fieldList = new ArrayList<JSONObject>();
            addFieldType(PropertyInfo.TYPE_BOOLEAN, MessageKeys.OCONFIG_TYPE_BOOLEAN, fieldList);
            addFieldType(PropertyInfo.TYPE_DATE, MessageKeys.OCONFIG_TYPE_DATE, fieldList);
            addFieldType(PropertyInfo.TYPE_INT, MessageKeys.OCONFIG_TYPE_INT, fieldList);
            addFieldType(PropertyInfo.TYPE_LONG, MessageKeys.OCONFIG_TYPE_LONG, fieldList);
            addFieldType(PropertyInfo.TYPE_IDENTITY, MessageKeys.OCONFIG_TYPE_IDENTITY, fieldList);
            addFieldType(PropertyInfo.TYPE_MANAGED_ATTRIBUTE, MessageKeys.OCONFIG_TYPE_MANAGED_ATTRIBUTE, fieldList);
            addFieldType(PropertyInfo.TYPE_SECRET, MessageKeys.OCONFIG_TYPE_SECRET, fieldList);
            addFieldType(PropertyInfo.TYPE_STRING, MessageKeys.OCONFIG_TYPE_STRING, fieldList);
            addFieldType(PropertyInfo.TYPE_APPLICATION, MessageKeys.OCONFIG_TYPE_APPLICATION, fieldList);
            addFieldType(PropertyInfo.TYPE_ROLE, MessageKeys.OCONFIG_TYPE_ROLE, fieldList);
            jsonWriter.value(fieldList);

            jsonWriter.endObject();
        } catch (JSONException e) {
            log.error("Could not get JSON for Form DTO");
        }
        return jsonString.toString();
    }

    /**
     * Add a JSONObject for a field with the given type and message key to the
     * fields List.
     */
    private void addFieldType(String type, String key, List<JSONObject> fields) {
        Map<String, String> fieldMap = new HashMap<String,String>();
        fieldMap.put("value", type);
        fieldMap.put("name", getMessage(key));
        fields.add(new JSONObject(fieldMap));
    }

    public Map<String, FormDTO> getFormUsageMap() {
        if(formUsageMap == null) {
            formUsageMap = new HashMap<String,FormDTO>();

            // Roles don't have usages but they keep the FormEditBean in the session
            // so it's OK to map by IDs in that case
            if (usages == null) {
                if(formDTOs != null && !formDTOs.isEmpty()) {
                    for(FormDTO dto : formDTOs) {
                        if(dto.getId() != null) {
                            formUsageMap.put(dto.getId(), dto);
                        }
                    }
                }
            } else {
                for(String usage : usages) {
                    if(formDTOs != null && !formDTOs.isEmpty()) {
                        for(FormDTO dto : formDTOs) {
                            if(dto.getUsage() != null &&
                                dto.getUsage().equals(Enum.valueOf(Usage.class, usage))) {
                                formUsageMap.put(usage.toString(), dto);
                                break;
                            }
                        }
                    }
                }
            }
        }
        return formUsageMap;
    }

    public void setFormUsageMap(Map<String, FormDTO> usageMap) {
        formUsageMap = usageMap;
    }

    public String getUsage() {
        String usageString = "";
        if (usage != null) {
            usageString = usage.toString();
        }
        return usageString;
    }

    public void setUsage(String usage) {
        if (!Util.isNullOrEmpty(usage)) {
            this.usage = Usage.valueOf(Usage.class, usage);
        } else {
            this.usage = null;
        }
    }

    /**
     * Returns the raw usage.  getUsage() would ideally do this, but I didn't want to 
     * change its signature and risk breaking HTML pages that reference it -- Bernie
     * @return Usage
     */
    public Usage getUsageEnum() {
        return usage;
    }

    ////////////////////////////////////////////////////////////////////////////
    //                                                                        //
    // Methods for Application Provisioning Polices                           //
    //                                                                        //
    ////////////////////////////////////////////////////////////////////////////
    private List<String> objectTypes;
    private List<String> accountUsages;
    private Map<String, List<String>> usageMapByObjectType;
    private Map<Pair<String, String>, FormDTO> formByObjectTypeUsageMap;

    // Mapping of Form ID and FormRef ID
    private Map<String, String> formRefMap;

    /**
     * This is for Application Provisioning Policies.
     * 
     * @param forms List of Forms including account, group and other object types.
     * @param accountUsageKey   key for account usages 
     */
    public FormEditBean(List<Form> forms, List<Schema> schemaList, String accountUsageKey) {
        this(forms);

        accountUsages = getAccountUsage(accountUsageKey);
        objectTypes = new ArrayList<String>();

        for(Schema schema : Util.safeIterable(schemaList)) {
            // IIQSAW-1887: Object type is rendered directly in the HTML so need to make double sure its safe
            String objectType = WebUtil.safeHTML(schema.getObjectType());
            if (!objectTypes.contains(objectType)) {
                objectTypes.add(objectType);
            }
        }
    }

    private List<String> getAccountUsage(String accountUsageKey) {
        if(accountUsageKey!=null) {
            try {
                Configuration config =
                    getContext().getObjectByName(Configuration.class, Configuration.OBJ_NAME);
                if (config != null) {
                    String usageCSV = config.getString(accountUsageKey);
                    if (usageCSV != null) {
                        return Util.csvToList(usageCSV);
                    }
                }
            } catch(GeneralException ge) {
                log.warn("Unable to load system configuration.  Error: " + ge.getMessage());
            }
        }
        return null;
    }

    public List<String> getObjectTypes() {
        return objectTypes;
    }

    public Map<String, List<String>> getObjectUsages() {
        if (usageMapByObjectType == null) {
            usageMapByObjectType = new HashMap<String, List<String>>();
            List<String> objTypes = getObjectTypes();
            for(String objType : objTypes) {
                if (objType.equals(Application.SCHEMA_ACCOUNT)) {
                    usageMapByObjectType.put(objType, accountUsages);
                } else {
                    List<String> objectTypeUsages = new ArrayList<String>();
                    objectTypeUsages.add(Usage.Create.toString());
                    objectTypeUsages.add(Usage.Update.toString());
                    usageMapByObjectType.put(objType, objectTypeUsages);
                }
            }
        }
        return usageMapByObjectType;
    }

    public Map<Pair<String, String>, FormDTO> getFormByObjectTypeUsageMap() {
        if ( formByObjectTypeUsageMap == null) {
            formByObjectTypeUsageMap = new HashMap<Pair<String, String>, FormDTO>();
            for(FormDTO dto : formDTOs) {
                String objectType = dto.getSchemaObjectType();
                String usage = dto.getUsage().toString();
                formByObjectTypeUsageMap.put(new Pair<String, String>(objectType, usage), dto);
            }
        }
        return formByObjectTypeUsageMap;
    }

    public String getObjectType() {
        return objectType;
    }

    public void setObjectType(String type) {
        objectType = type;
    }

    public void addObjectType(String type) {
        if (!objectTypes.contains(type)) {
            objectTypes.add(type);
            
            List<String> objectTypeUsages = new ArrayList<String>();
            objectTypeUsages.add(Usage.Create.toString());
            objectTypeUsages.add(Usage.Update.toString());
            getObjectUsages().put(type, objectTypeUsages);
        }
    }

    /**
     * Add objectType to FormEditBean with any associated forms
     * @param type - Object Type to add to the bean
     * @param forms - Forms associated with the object Type
     */
    public void addObjectType(String type, List<Form> forms) {
        addObjectType(type);

        //AddForms to formDTO if it doesn't already exist
        for(Form f : Util.safeIterable(forms)) {
            if (!getFormByObjectTypeUsageMap().containsKey(new Pair<String, String>(type,
                                               f.convertFormType(f.getType()).toString()))) {
                addForm(f);
            }
        }
    }

    public void setFormRefId(String refId) {
        formRefId = refId;
    }

    public String getFormRefId() {
        return formRefId;
    }

    public void setFormRefName(String refName) {
        formRefName = refName;
    }

    public String getFormRefName() {
        return formRefName;
    }

    public Map<String, String> getFormRefMap() {
        if (null == formRefMap) {
            formRefMap = new HashMap<String, String>();
            for (FormDTO dto : Util.iterate(formDTOs)) {
                if (null != dto.getFormRefId()) {
                    formRefMap.put(dto.getId(), dto.getFormRefId());
                }
            }
        }
        return formRefMap;
    }

    public String getLocalizedBeanTypeMessage(String beanType) {
        Message msg;
        String localizedMsg;
        if (beanType.equals("identity")) {
            msg = new Message(MessageKeys.FORM_BEANTYPE_IDENTITY);
            localizedMsg = msg.getLocalizedMessage(getLocale(), getUserTimeZone());
        }
        else if (beanType.equals("role")) {
            msg = new Message(MessageKeys.FORM_BEANTYPE_ROLE);
            localizedMsg = msg.getLocalizedMessage(getLocale(), getUserTimeZone());
        }
        else if ((beanType.equals("application"))) {
            msg = new Message(MessageKeys.FORM_BEANTYPE_APPLICATION);
            localizedMsg = msg.getLocalizedMessage(getLocale(), getUserTimeZone());
        }
        else if ((beanType.equals("workflow"))) {
            msg = new Message(MessageKeys.FORM_BEANTYPE_WORKFLOW);
            localizedMsg = msg.getLocalizedMessage(getLocale(), getUserTimeZone());
        }
        else {
            localizedMsg = beanType;
        }
        return localizedMsg;
    }
}
