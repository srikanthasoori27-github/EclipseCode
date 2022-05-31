/* (c) Copyright 2013 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.group;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import sailpoint.api.Formicator;
import sailpoint.api.SailPointContext;
import sailpoint.object.Attributes;
import sailpoint.object.Field;
import sailpoint.object.Form;
import sailpoint.object.Form.Section;
import sailpoint.object.ManagedAttribute;
import sailpoint.object.ObjectAttribute;
import sailpoint.object.ObjectConfig;
import sailpoint.web.FormHandler.FormStore;

public class ExtendedAttributeFormStore implements FormStore {
    private AccountGroupDTO group;
    
    public ExtendedAttributeFormStore(AccountGroupDTO group) {
        this.group = group;
    }
    
    public void storeMasterForm(Form form) {
        group.setExtendedAttributeMasterForm(form);
    }

    public Form retrieveMasterForm() {
        return group.getExtendedAttributeMasterForm();
    }

    public void clearMasterForm() {
        group.setExtendedAttributeMasterForm(null);
    }

    public void storeExpandedForm(Form form) {
        group.setExtendedAttributeExpandedForm(form);
    }

    public Form retrieveExpandedForm() {
        return group.getExtendedAttributeExpandedForm();
    }

    public void clearExpandedForm() {
        group.setExtendedAttributeExpandedForm(null);
        group.clearExtendedAttributeFormBean();
    }
    
    public Form createMasterForm(Map<String, Object> fields, boolean isNew, SailPointContext context, Locale locale) {
        List<Field> editFields = new ArrayList<Field>();        
        Form form = new Form();
                
        // For now always add extended attributes.  If we want to defer those to the form 
        // as well sometime in the future just comment this next line out.  If we do that we
        // need to quit blindly assigning all form attributes to the group section.
        editFields.addAll(createExtendedAttributeFields(fields, isNew, locale));
        Formicator formicator = new Formicator(context);
        formicator.assemble(form, editFields);

        // jsl - Form no longer requires a single Sections list, Formicator is
        // still building one, but we might want to make this more flexible...
        List<Section> sections = form.getSections();
        if (sections != null && !sections.isEmpty()) {
            for (Section section : sections) {
                Attributes<String, Object> attributes = new Attributes<String, Object>();
                attributes.put("xtype", "panel");
                String bodyStyle;
                Field field = (Field)section.getItems().get(0);
                if (field.getCategoryName() == null || field.getCategoryName().trim().length() == 0) {
                    bodyStyle = "border-style:none; width:450px";                    
                } else {
                    bodyStyle = "width:450px";
                } 
                attributes.put("bodyStyle", bodyStyle);
                section.setAttributes(attributes);
            }
        }
        
        return form;
    }
    
    private List<Field> createExtendedAttributeFields(Map<String, Object> fields, boolean isNew, Locale locale) {
        List<Field> extendedAttributeFields = new ArrayList<Field>();
        ObjectConfig config = ObjectConfig.getObjectConfig(ManagedAttribute.class);
        List<ObjectAttribute> extendedAttributes = config.getObjectAttributes();
        if (extendedAttributes != null && !extendedAttributes.isEmpty()) {
            for (ObjectAttribute extendedAttribute : extendedAttributes) {
                String extendedAttributeName = extendedAttribute.getName();
                if (extendedAttributeName != null) {
                    Field extendedAttributeField = new Field();
                    extendedAttributeField.setDisplayName(extendedAttribute.getDisplayableName(locale));
                    extendedAttributeField.setName(extendedAttributeName);
                    extendedAttributeField.setDescription(extendedAttribute.getDescription());
                    extendedAttributeField.setType(extendedAttribute.getType());
                    extendedAttributeField.setRequired(extendedAttribute.isRequired());
                    List<Object> allowedValues = extendedAttribute.getAllowedValues(); 
                    if (allowedValues != null && !allowedValues.isEmpty()) {
                        extendedAttributeField.setType(Field.DISPLAY_TYPE_COMBOBOX);
                        extendedAttributeField.setAllowedValues(allowedValues);
                    }
                    extendedAttributeField.setReadOnly(!extendedAttribute.isEditable());
                    Object value;
                    if (fields == null) {
                        value = extendedAttribute.getDefaultValue();                        
                    } else {
                        value = fields.get(extendedAttributeName);
                        if (value == null && isNew) {
                            value = extendedAttribute.getDefaultValue();
                        }
                    }
                    extendedAttributeField.setValue(value);
                    extendedAttributeFields.add(extendedAttributeField);
                    boolean hasCategory = false;
                    String category = extendedAttribute.getCategoryName();
                    if (category != null && category.trim().length() > 0) {
                        extendedAttributeField.setCategoryName(category);
                        extendedAttributeField.setSection(category);
                        hasCategory = true;
                    }
                    group.styleField(extendedAttributeField, hasCategory);
                }
            }
        }
        return extendedAttributeFields;
    }
}
