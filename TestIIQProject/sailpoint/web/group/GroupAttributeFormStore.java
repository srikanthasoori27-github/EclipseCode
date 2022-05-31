/* (c) Copyright 2013 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.group;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.Formicator;
import sailpoint.api.SailPointContext;
import sailpoint.object.Application;
import sailpoint.object.AttributeDefinition;
import sailpoint.object.Attributes;
import sailpoint.object.Field;
import sailpoint.object.Filter;
import sailpoint.object.Form;
import sailpoint.object.Form.Section;
import sailpoint.object.Schema;
import sailpoint.object.Template;
import sailpoint.object.Template.Usage;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.web.FormHandler.FormStore;

public class GroupAttributeFormStore implements FormStore {
    private static Log log = LogFactory.getLog(GroupAttributeFormStore.class);
    public static final String ID_ALIAS = "nonIiqSysId";
    
    private AccountGroupDTO group;
    
    public GroupAttributeFormStore(AccountGroupDTO group) {
        this.group = group;
    }
    
    public void storeMasterForm(Form form) {
        group.setGroupAttributeMasterForm(form);
    }

    public Form retrieveMasterForm() {
        return group.getGroupAttributeMasterForm();
    }

    public void clearMasterForm() {
        group.setGroupAttributeMasterForm(null);
        group.setContainsGroupAttributes(false);
    }

    public void storeExpandedForm(Form form) {
        group.setGroupAttributeExpandedForm(form);
    }

    public Form retrieveExpandedForm() {
        return group.getGroupAttributeExpandedForm();
    }

    public void clearExpandedForm() {
        group.setGroupAttributeExpandedForm(null);
        group.clearGroupAttributeFormBean();
    }
    
    /**
     * Create a master form for the current group's attributes.  Unfortunately we're creating this even if the ManagedAttribute in question isn't actually a group.
     * Just dummy up a blank one instead to avoid weirdness in the UI
     * @param originalGroupFields The original values that this group had prior to being edited
     * @param app Application that this ManagedAttribute belongs to
     * @param isNew true if a new ManagedAttribute is being edited; false otherwise
     * @param isGroup true if this ManagedAttribute is a group; false otherwise
     * @param isProvisioningEnabled true if provisioning is enabled on this application; false otherwise
     * @param context SailPointContext
     * @return Master Form
     * @throws GeneralException
     */
    Form createMasterForm(Map<String, Object> originalGroupFields, Application app, boolean isNew, boolean isGroup, boolean isProvisioningEnabled, SailPointContext context) throws GeneralException {
        // We absolutely must have these because the templates need something to populate
        assert(originalGroupFields != null);
        List<Field> groupEditFields = new ArrayList<Field>();

        // Need to use different policies for create and update
        Usage currentUsage;
        if (isNew && isGroup) {
            currentUsage = Template.Usage.Create;
        } else if (isGroup) {
            currentUsage = Template.Usage.Update;
        } else {
            currentUsage = null;
        }
        
        boolean needDefaultTemplate;
        if (app != null && currentUsage != null) {
            // Look for the create and edit templates
           Template createTemplate = app.getOldTemplate(Template.Usage.Create, group.getType());
           Template editTemplate = app.getOldTemplate(Template.Usage.Update, group.getType());

           if (isNew && editTemplate != null) {
               // The original group fields come from the template for new groups so add fields from the UpdateGroup Template
               Map<String, Object> templateFieldValues = buildFieldValuesFromTemplate(editTemplate,context);
               if (!Util.isEmpty(templateFieldValues)) {
                   originalGroupFields.putAll(templateFieldValues);
               }
           }
           
           Map<String, Field> fieldMap = new HashMap<String, Field>();
           // Put everything in the edit template into the field map
           needDefaultTemplate = !addFieldsToMap(originalGroupFields, app, editTemplate, fieldMap, isProvisioningEnabled, context);
           
           if (currentUsage == Template.Usage.Create) {
               if (createTemplate != null) {
                   // The original group fields come from the template for new groups so add fields from the CreateGroup Template
                   Map<String, Object> templateFieldValues = buildFieldValuesFromTemplate(createTemplate,context);
                   if (!Util.isEmpty(templateFieldValues)) {
                       originalGroupFields.putAll(templateFieldValues);
                   }
               }
               
               // Override and/or add creation-specific fields if we're creating a new Group
               needDefaultTemplate &= !addFieldsToMap(originalGroupFields, app, createTemplate, fieldMap, isProvisioningEnabled, context);
           }
           
           if (!needDefaultTemplate) {
               // Set the priorities on the fields according to the order in which they were defined in the templates
               Set<String> addedFields = new HashSet<String>();
               int createSize = (createTemplate == null || createTemplate.getFields(context) == null) ? 0 : createTemplate.getFields(context).size();
               int updateSize = (editTemplate == null || editTemplate.getFields(context) == null) ? 0 : editTemplate.getFields(context).size();
               int currentPriority = createSize + updateSize + 1;
               // Prioritize fields from the create policy
               currentPriority = prioritizeFields(createTemplate, fieldMap, addedFields, currentPriority,context);
               prioritizeFields(editTemplate, fieldMap, addedFields, currentPriority,context);
               groupEditFields.addAll(fieldMap.values());
           }
        } else {
            needDefaultTemplate = true;
        }
        
        Form form = new Form();
        
        // Build a default template if needed
        if (needDefaultTemplate && isGroup) {
            groupEditFields = getDefaultGroupFields(app, originalGroupFields);
        }
        
        /* Note that at this point "id" transformations on Fields should have taken place.
         * Originally I was going to add a loop here to do that but since we've already
         * iterated over the Fields I opted to transform them in those iterations instead.
         * If we add more ways to create fields it might be best to just iterate again here.
         * See bug 16481 for why this needs to happen. --Bernie
         */
        
        group.setContainsGroupAttributes(!groupEditFields.isEmpty());
        
        if (group.isContainsGroupAttributes()) {
            Formicator formicator = new Formicator(context);
            formicator.assemble(form, groupEditFields);
            Iterator<Field> assembledFields = form.iterateFields();
            if (assembledFields != null) {
                while (assembledFields.hasNext()) {
                    Field groupEditField = assembledFields.next();
                    // If the logged in user doesn't have provisioning access set these to read-only
                    if (!group.isProvisioningEnabled() || !isProvisioningEnabled  || (app != null && !app.supportsGroupProvisioning(group.getType()))) {
                        groupEditField.setReadOnly(true);
                    }
                }
            }
        }

        // Override some of the form styling
        // jsl - eventually may need to support sectionless forms, depends on
        // who built these
        List<Section> sections = form.getSections();
        if (sections != null && !sections.isEmpty()) {
            for (Section section : sections) {
                Attributes<String, Object> attributes = new Attributes<String, Object>();
                attributes.put("xtype", "panel");
                attributes.put("bodyStyle", "border-style:none");
                section.setAttributes(attributes);
            }
        }
        
        return form;
    }
    
    /* 
     * Determine which template fields should be added to the field map, apply custom styling for the entitlement editor, 
     * and force them to be read-only when provisioning is disabled
     * @return false if there were no fields to add 
     * */
    private boolean addFieldsToMap(Map<String, Object> fields, Application app, Template appTemplate, Map<String, Field> fieldMap, boolean isProvisioningEnabled, SailPointContext context) {
        boolean hasFields;
        if (appTemplate == null) {
            hasFields = false;
        } else {
            List<Field> templateFields = appTemplate.getFields(context);
            if (templateFields != null && !templateFields.isEmpty()) {
                String nativeIdentityAttribute = group.getNativeIdentityAttribute(context, appTemplate.getSchemaObjectType());
                for (Field templateField : templateFields) {
                    try {
                        // Copy the group fields in case we need to make them read-only.  We don't want to muck with
                        // the original template in that case
                        Field templateFieldCopy = (Field)templateField.deepCopy(context);
                        group.styleField(templateFieldCopy, false);
                        String fieldName = templateField.getName();
                        
                       // Get the value now in case we have to transform the ID
                        Object value = null;
                        if (!Util.isEmpty(fields)) {
                            value = fields.get(fieldName);                        
                        }
                        
                        // "id" causes problems for us.  See bug 16481.
                        if ("id".equals(fieldName)) {
                            fieldName = ID_ALIAS;
                            templateFieldCopy.setName(fieldName);
                            templateFieldCopy.setDisplayName("id");
                        }
                        
                        // jsl - added this
                        templateFieldCopy.setValue(value);
                        if (!isProvisioningEnabled) {
                            templateFieldCopy.setReadOnly(true);                            
                        }
                        
                        // Handle cases where the native identity found its way onto the update
                        if (nativeIdentityAttribute != null && (nativeIdentityAttribute.equals(fieldName) || 
                                (nativeIdentityAttribute.equals("id") && fieldName.equals(ID_ALIAS)))) {
                            if (!group.isNew()) {
                                templateFieldCopy.setValue(group.getNativeIdentity());
                                templateFieldCopy.setReadOnly(true);
                            }
                        }

                        // bug#22572 For ManagedAttribute fields, add an implicit filter for this application.
                        // There will usually already be one for the object type.
                        if (Field.TYPE_MANAGED_ATTR.equals(templateFieldCopy.getType())) {
                            StringBuilder b = new StringBuilder();
                            b.append("application.name == \"");
                            b.append(app.getName());
                            b.append("\"");
                            String filter = templateFieldCopy.getFilterString();
                            if (!Util.isNullOrEmpty(filter)) {
                                b.append(" && ");
                                b.append(filter);
                            }
                            templateFieldCopy.setFilterString(b.toString());
                        }

                        fieldMap.put(fieldName, templateFieldCopy);

                    } catch (GeneralException e) {
                        log.error("The AccountGroupDTO failed to copy the template field named " + templateField.getDisplayableName() + ".  It will be left out of the form.", e);
                    }
                }
                hasFields = true;
            } else {
                hasFields = false;
            }
        }
        
        return hasFields;
    }
    
    /*
     * Prioritize fields in the specified map according to the order in which they were defined in the specified template and return the priority value where we left off
     */
    private int prioritizeFields(Template template, Map<String, Field> fieldMap, Set<String> addedFields, int currentPriority,SailPointContext context) {
        if (template != null) {
            List<Field> editFields = template.getFields(context);
            if (editFields != null) {
                for (Field editField : editFields) {
                    String editFieldName = editField.getName();
                    if (!addedFields.contains(editFieldName)) {
                        if (fieldMap.containsKey(editFieldName)) {
                            Field fieldCopy = fieldMap.get(editFieldName);
                            fieldCopy.setPriority(currentPriority);
                            addedFields.add(editFieldName);
                            currentPriority--;
                        }
                    }
                }
            }       
        }
        
        return currentPriority;
    }
    
    private List<Field> getDefaultGroupFields(Application application, Map<String, Object> fields) {
        List<Field> defaultFields = new ArrayList<Field>();
        
        if (application != null) {
            Schema groupSchema = application.getSchema(group.getType());
            if (groupSchema != null) {
                String nativeIdentityAttribute = groupSchema.getIdentityAttribute();
                List<AttributeDefinition> schemaAttributes = groupSchema.getAttributes();
                
                if (schemaAttributes != null && !schemaAttributes.isEmpty()) {
                    for (AttributeDefinition schemaAttribute : schemaAttributes) {
                        String schemaAttributeName = schemaAttribute.getName();
                        if (schemaAttributeName != null) {
                            Object value = null;
                            if (!Util.isEmpty(fields)) {
                                value = fields.get(schemaAttributeName);                                
                            }
                            Field schemaField = new Field();
                            if ("id".equals(schemaAttributeName)) {
                                // id screws things up.  See bug 16481.
                                schemaAttributeName = ID_ALIAS;
                                schemaField.setDisplayName("id");
                            }
                            
                            schemaField.setName(schemaAttributeName);
                            if (!group.isNew() && nativeIdentityAttribute != null && (nativeIdentityAttribute.equals(schemaAttributeName) || 
                                    (nativeIdentityAttribute.equals("id") && schemaAttributeName.equals(ID_ALIAS)))) {
                                schemaField.setValue(group.getNativeIdentity());
                            } else {
                                schemaField.setValue(value);                                    
                            }
                            // Make all fields read-only by default because we don't know whether or not
                            // they're safe to edit without a defined provisioning form
                            schemaField.setReadOnly(true);
                            schemaField.setMultiValued(schemaAttribute.isMultiValued());
                            schemaField.setType(schemaAttribute.getSchemaObjectType() != null ? Field.TYPE_MANAGED_ATTR : schemaAttribute.getType());
                            group.styleField(schemaField, false);
                            //Create default filterString if type is Managed Attribute
                            if (Field.TYPE_MANAGED_ATTR.equals(schemaField.getType())) {
                                schemaField.setFilterString(Filter.and(Filter.eq("application.name", application.getName()),
                                        Filter.eq("type", schemaAttribute.getSchemaObjectType())).toString());
                            }
                            defaultFields.add(schemaField);
                        }
                    }
                }
            }
        }
        
        return defaultFields;
    }
    
    private Map<String, Object> buildFieldValuesFromTemplate(Template template,SailPointContext context) {
        List<Field> templateFields = template.getFields(context);
        Map<String, Object> templateFieldMap;
        if (Util.isEmpty(templateFields)) {
            templateFieldMap = null;
        } else {
            templateFieldMap = new HashMap<String, Object>();
            for (Field templateField : templateFields) {
                Object value = templateField.getValue();
                if (value == null || (value instanceof String && Util.isNullOrEmpty((String) value))) {
                    value = templateField.getDefaultValue();
                }
                templateFieldMap.put(templateField.getName(), value);
            }
        }

        return templateFieldMap;
    }
}
