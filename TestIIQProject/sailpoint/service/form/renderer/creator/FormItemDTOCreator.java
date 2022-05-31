package sailpoint.service.form.renderer.creator;

import sailpoint.object.Field;
import sailpoint.object.Form;
import sailpoint.object.ManagedAttribute;
import sailpoint.service.form.renderer.FormRendererUtil;
import sailpoint.service.form.renderer.item.FormItemDTO;
import sailpoint.service.form.renderer.item.FormItemDTO.FormItemType;
import sailpoint.tools.Util;
import sailpoint.web.extjs.Component;

import java.util.Arrays;
import java.util.List;

/**
 * @author: pholcomb
 * A form item creator that sets the type on the FormItemDTO for use with the angular/responsive ui
 * Anything that is specific to that ui (and not shared with the extjs ui) will need to go in this class
 */
public class FormItemDTOCreator extends AbstractFormItemDTOCreator {

    private Form.Section _section;

    public FormItemDTOCreator(Field field, FormRendererUtil util, String parentId, Form.Section section) {
        super(field, util, parentId);

        _section = section;
    }

    public FormItemDTO getDTO() {
        if (_dto == null) {
            _dto = this.createFormItemDTO(new FormItemDTO());
        }
        return _dto;
    }

    protected FormItemDTO createFormItemDTO(FormItemDTO dto) {
        dto = super.createFormItemDTO(dto);

        if(_field.getTypeClass()!=null) {
            this.handleSPClass(dto);
        } else {
            dto = this.updateFormItemByType(dto);
        }

        return dto;
    }

    /**
     * Set the type property on the suggest depending on whether it is multi-valued or not
     * @param dto
     */
    protected void handleSPClass(FormItemDTO dto) {
        super.handleSPClass(dto);
        dto.setType(_field.isMulti() ? FormItemType.MULTISUGGEST.getFieldName() : FormItemType.SUGGEST.getFieldName());
    }

    /**
     * Based on the type or displayType of the form item, we set the type of the FormItemDTO so that
     * the ui will know what kind of component to render
     * @param dto The FormItemDTO we are updating
     * @return The FormItemDTO we are updating
     */
    protected FormItemDTO updateFormItemByType(FormItemDTO dto) {
        dto = super.updateFormItemByType(dto);

        /* To support legacy forms, we need to look for the xtype property stored in the attributes */
        String xtypeAttr = Util.otos(_field.getAttribute(Component.PROPERTY_XTYPE));
        boolean isTextField = Field.TYPE_INT.equals(_field.getType()) || Field.TYPE_LONG.equals(_field.getType()) ||
                Field.TYPE_STRING.equals(_field.getType()) || Component.XTYPE_TEXT_FIELD.equals(xtypeAttr);

        /* Standard attributes */
        if (Field.TYPE_BOOLEAN.equals(_field.getType())) {
            dto.setType(FormItemType.CHECKBOX.getFieldName());
        } else if (Field.TYPE_DATE.equals(_field.getType()) || Util.nullSafeEq(xtypeAttr, Component.XTYPE_DATERANGE)) {
            dto.setType(FormItemType.DATE.getFieldName());
        } else if (Field.TYPE_SECRET.equals(_field.getType())) {
            dto.setType(FormItemType.SECRET.getFieldName());
        } else if (Field.TYPE_PERMISSION.equals(_field.getType())) {
            dto.setType(FormItemType.MULTISUGGEST.getFieldName());
        } else if (Field.TYPE_MANAGED_ATTR.equals(_field.getType())) {
            FormItemType type = _field.isMulti() ? FormItemType.MULTISUGGEST : FormItemType.SUGGEST;

            dto.setType(type.getFieldName());
            dto.setSuggestClass(ManagedAttribute.class.getName());
        } else if (!_section.isInteractive()) {
            dto.setType(Component.XTYPE_HTML_TEMPLATE);
        } else {
            handleTextField(dto);
        }
        return dto;
    }

    /**
     * A text field can be represented in several different ways
     *   - If the field has allowed values and IS NOT dynamic:
     *       - If it is not multi valued:
     *          - If it has more than three allowed values, we render it as a select
     *          - If it has less than three allowed values, we render it as a radio group
     *       - If it is multi valued,
     *          - If it is set to be displayed as a checkbox group, we render it as a checkbox group
     *          - Otherwise we render it as a multi select
     *
     *    - If this field IS dynamic and has allowed values:
     *       - If it is multi values, we render it as a multi select
     *       - If it is single valued, we render it as a single select
     *
     *    - If it is just multi valued, we render it as multi text.
     *    - If it is set to be a textarea, we render it as a textarea
     *    - If it is a label, we render it as a label
     *    - If it is an int or a long, we render it as a number field
     *    - Otherwise we render it as a string field
     */
    private void handleTextField(FormItemDTO dto) {
        if (!Util.isEmpty(_field.getAllowedValues()) && !_field.isDynamic()) {
            if (!_field.isMulti()) {
                handleSingleSelection(dto);
            } else {
                // no support for checkbox group in new UI as of yet so just comment out for
                // now in case we add it later we know the logic for it
                //if (Field.DISPLAY_TYPE_CHECKBOX.equals(_field.getDisplayType())) {
                //    dto.setType(FormItemType.CHECKBOX_GROUP.getFieldName());
                //} else {
                    dto.setType(FormItemType.MULTISUGGEST.getFieldName());
                //}
            }
        } else if (_field.isDynamic() && _field.getAllowedValuesDefinition() != null) {
            if (!_field.isMulti()) {
                handleDynamicSingleSelection(dto);
            } else {
                dto.setType(FormItemType.MULTISUGGEST.getFieldName());
            }
        } else {
            if (_field.isMulti()) {
                dto.setType(FormItemType.MULTITEXT.getFieldName());

                // if value is a string then promote it to a list
                Object val = dto.getValue();
                if (val != null && val instanceof String) {
                    dto.setValue(Arrays.asList(val));
                }
            } else if (Field.DISPLAY_TYPE_TEXTAREA.equals(_field.getDisplayType())) {
                dto.setType(FormItemType.TEXTAREA.getFieldName());
            } else if (isLabel()) {
                handleLabel(dto);
            } else if(dto.getSuggestClass()!=null || dto.getDatasourceUrl()!=null) {
                dto.setType(FormItemType.SUGGEST.getFieldName());
            } else {
                if(_field.getType()!=null &&
                        (_field.getType().equals(Field.TYPE_INT) || _field.getType().equals(Field.TYPE_LONG))) {
                    dto.setType(FormItemType.NUMBER.getFieldName());
                } else {
                    dto.setType(FormItemType.STRING.getFieldName());
                }
            }
        }
    }

    /**
     * Determines if the field is a label. For legacy reasons we will
     * not only inspect the displayType but also any xtype value that
     * exists in the attributes map.
     *
     * @return True if label, false otherwise.
     */
    private boolean isLabel() {
        final String XTYPE = "xtype";
        final String LABEL = "label";

        return Field.DISPLAY_TYPE_LABEL.equals(_field.getDisplayType()) ||
               LABEL.equals(_field.getAttribute(XTYPE));
    }

    /**
     * Default to a dynamic select component, if a displayType is set on the field allow it
     * to override the select.
     */
    private void handleDynamicSingleSelection(FormItemDTO dto) {
        if (_field == null)
            return;

        //TODO: We will need to build some extra code into this to handle dynamic values

        String type = getDisplayTypeOverride(FormItemType.SUGGEST.getFieldName());
        dto.setType(type);
    }

    /**
     * Handles moving label related properties from the field object to the dto.
     * Will set the value, type on the dto. This will also handle the case where value is a message key.
     * <p/>
     * Sample usage: <Field displayType='label' displayName='msg_key_here'/>
     */
    private void handleLabel(FormItemDTO dto) {
        final String TEXT = "text";

        dto.setType(FormItemType.LABEL.getFieldName());

        // first look for display name otherwise look for
        // text key in the attributes map
        String value = Util.otos(_field.getDisplayName());
        if (value != null) {
            dto.setFieldLabel(_rendererUtil.localizedMessage(value));
        } else if (_field.getAttribute(TEXT) != null) {
            dto.setFieldLabel(_field.getAttribute(TEXT).toString());
        }
    }

    /**
     * When a field is not multi valued, we use this method to determine the type
     * of the dto.  Typically if the field has 3 or more allowed values, it will
     * render as a select. If the field specifies a displayType then let's override
     * with the specific type.
     */
    private void handleSingleSelection(FormItemDTO dto) {

        if (_field == null)
            return;

        List<Object> allowedValues = _field.getAllowedValues();
        String type = getDisplayTypeOverride(Util.size(allowedValues) >= 3 ?
                FormItemType.SUGGEST.getFieldName() : FormItemType.RADIO_GROUP.getFieldName());

        dto.setType(type);
    }

    /**
     * Consolidate this logic so it may be called from dynamic and non-dynamic single selections
     * Allows us to override the default behavior that is applied when setting the type on the dto
     * by looking at the field's display type.
     *
     * @param defaultType a default type
     * @return the correct component type
     */
    private String getDisplayTypeOverride(String defaultType) {
        if (defaultType == null)
            throw new RuntimeException("Can not set a null default component type");

        String type = defaultType;
        if (_field != null) {
            String displayType = _field.getDisplayType();

            // override the component type if explicitly defined
            if (displayType != null) {
                if (Field.DISPLAY_TYPE_COMBOBOX.equals(displayType) ||
                        Util.otob(_field.getAttribute(Field.RENDER_USE_SELECT_BOX))) {
                    type = FormItemType.SUGGEST.getFieldName();
                } else if (Field.DISPLAY_TYPE_RADIO.equals(displayType)) {
                    type = FormItemType.RADIO_GROUP.getFieldName();
                } else if (Field.DISPLAY_TYPE_CHECKBOX.equals(displayType)) {
                    type = FormItemType.CHECKBOX_GROUP.getFieldName();
                }
            }
        }

        return type;
    }
}
