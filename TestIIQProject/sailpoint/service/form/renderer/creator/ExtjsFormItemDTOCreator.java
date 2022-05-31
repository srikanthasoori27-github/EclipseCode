package sailpoint.service.form.renderer.creator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.integration.JsonUtil;
import sailpoint.object.Application;
import sailpoint.object.Attributes;
import sailpoint.object.Field;
import sailpoint.rest.ConfigurationResource;
import sailpoint.service.form.renderer.FormRendererUtil;
import sailpoint.service.form.renderer.extjs.item.FormItemDTO;
import sailpoint.tools.GeneralException;
import sailpoint.tools.JsonHelper;
import sailpoint.tools.Util;
import sailpoint.web.extjs.Component;

/**
 * @author: pholcomb
 *
 * A sub class that adds handling of setting the xtype (and any other extjs specific values) on the FormItemDTOs
 * for being displayed on an ExtJS form.
 */
public class ExtjsFormItemDTOCreator extends AbstractFormItemDTOCreator {

    private static final Log log = LogFactory.getLog(FormRendererUtil.class);

    private static final String SUGGEST_URL = "/include/formSuggest.json";

    int _tabIndex;
    String _defaultXType;

    public ExtjsFormItemDTOCreator(Field field, FormRendererUtil util, String parentId, int tabIndex, String defaultXType) {
        super(field, util, parentId);
        _tabIndex = tabIndex;
        _defaultXType = defaultXType;
    }


    public sailpoint.service.form.renderer.item.FormItemDTO getDTO() throws GeneralException {
        if (_dto == null) {
            _dto = this.createFormItemDTO(new FormItemDTO());
        }
        return _dto;
    }

    protected FormItemDTO createFormItemDTO(FormItemDTO dto) throws GeneralException {
        dto = (FormItemDTO)super.createFormItemDTO(dto);

        dto.setDisplayOnly(_field.isDisplayOnly());
        dto.setAuthoritative(_field.isAuthoritative());
        dto.setTabIndex(_tabIndex);

        // Default the max width on each field to ATT_MAX_WIDTH
        Attributes attrs = dto.getAttributes();
        if (!Util.isEmpty(attrs)) {
            if (attrs.containsKey("width")) {
                try {
                    dto.setWidth(attrs.getInteger("width"));
                } catch (Exception ex) {
                    log.warn("Ignoring invalid width attribute on form field: " + dto.getName());
                }
            }
        }

        // If maxWidth is not specified and width is, transfer the value to maxWidth
        // See Bug #22955 for details
        if (!Util.isEmpty(attrs) && !attrs.containsKey(FormItemDTO.ATT_MAX_WIDTH) && attrs.containsKey(FormItemDTO.ATT_WIDTH)) {
            dto.addAttribute(FormItemDTO.ATT_MAX_WIDTH, attrs.getInt(FormItemDTO.ATT_WIDTH));
        } else if (Util.isEmpty(attrs) || !attrs.containsKey(FormItemDTO.ATT_MAX_WIDTH)) {
            dto.addAttribute(FormItemDTO.ATT_MAX_WIDTH, FormItemDTO.MAX_WIDTH);
        }

        String xtypeAttr = Util.otos(_field.getAttribute(Component.PROPERTY_XTYPE));

        Class spClass = _field.getTypeClass();
        if (spClass != null) {
            String column = (String)_field.getAttribute(Field.ATTR_VALUE_OBJ_COLUMN);
            setupSuggest(dto, spClass.getSimpleName(), column);
            dto.setXtype(_field.isMulti() ? Component.XTYPE_MULTISELECT : Component.XTYPE_COMBO);
        }

        if (xtypeAttr != null && xtypeAttr.equals("entitlementselector")) {
            setupSuggest(dto, Application.class.getSimpleName(), null);
            List<Map<String, String>> valueMap = new ArrayList<Map<String, String>>();
            if (_field.getValue() != null) {
                valueMap = JsonHelper.listOfMapsFromJson(String.class, String.class, _field.getValue().toString());
            }
            dto.setValue(valueMap);
        }

        if(_field.getTypeClass()!=null) {
            this.handleSPClass(dto);
        } else {
            dto = this.updateFormItemByType(dto);
        }
        return dto;
    }

    /**
     * Based on the type or displayType of the form item, we set the type of the FormItemDTO so that
     * the ui will know what kind of component to render
     * @param dto The FormItemDTO we are updating
     * @return The FormItemDTO we are updating
     */
    protected FormItemDTO updateFormItemByType(FormItemDTO dto) {
        dto = (FormItemDTO)super.updateFormItemByType(dto);

        String xtypeAttr = Util.otos(_field.getAttribute(Component.PROPERTY_XTYPE));
        boolean isTextField = Field.TYPE_INT.equals(_field.getType()) || Field.TYPE_LONG.equals(_field.getType()) ||
                Field.TYPE_STRING.equals(_field.getType()) || Component.XTYPE_TEXT_FIELD.equals(xtypeAttr) ||
                (xtypeAttr == null && Component.XTYPE_TEXT_FIELD.equals(_defaultXType));

        if (Field.TYPE_BOOLEAN.equals(_field.getType())) {
            dto.setXtype(Component.XTYPE_BOOL_CHECKBOX);
        } else if (Field.TYPE_DATE.equals(_field.getType())) {
            boolean showTime = Util.otob(_field.getAttribute(Field.RENDER_SHOW_TIME));
            if (showTime)
                dto.setXtype(Component.XTYPE_DATETIME);
            else
                dto.setXtype(Component.XTYPE_DATE);
        } else if (Field.TYPE_SECRET.equals(_field.getType())) {
            dto.setXtype(Component.XTYPE_SECRET);
        } else if (Field.TYPE_PERMISSION.equals(_field.getType())) {
            dto.setDatasourceUrl(ConfigurationResource.URL);
            dto.setXtype(Component.XTYPE_MULTISELECT);
        } else if (Field.TYPE_MANAGED_ATTR.equals(_field.getType())) {
            //We need to treat ManagedAttribute Type special because it has no name attribute.
            //SuggestHelper by default adds name to the projectionColumns which breaks for MAs
            //Now it all goes through FormSuggestBean.
            //Note we never supported columns with ManagedAttribute type so don't start now.
            setupSuggest(dto, Field.TYPE_MANAGED_ATTR, null);
            dto.setXtype(_field.isMulti() ? Component.XTYPE_MULTISELECT : Component.XTYPE_COMBO);
        } else if (isTextField) {
            handleTextField(dto);
        } else {
            dto.setXtype(_defaultXType);
        }

        return dto;
    }

    /**
     * Adds the required base params and datasource url to point our class-based suggests at FormSuggestBean
     */
    private void setupSuggest(FormItemDTO dto, String suggestClass, String suggestColumn) {
        // ExtJS form suggests go through the FormSuggestBean for authorization.
        // Add all the interesting form stuff to the base params
        Map<String, Object> baseParams = dto.getBaseParams();
        if (baseParams == null) {
            baseParams = new HashMap<>();
        }
        baseParams.put("formBeanClass", _rendererUtil.getFormBeanClass());
        try {
            // Gotta serialize this for baseparams
            baseParams.put("formBeanState", JsonUtil.render(_rendererUtil.getFormBeanState()));
        } catch (Exception ex) {
            log.error("Unable to render JSON for formBeanState", ex);
        }
        baseParams.put("formId", _rendererUtil.getForm().getId());
        baseParams.put("fieldName", _field.getName());
        baseParams.put("suggestClass", suggestClass);
        if (!Util.isNothing(suggestColumn)) {
            baseParams.put("suggestColumn", suggestColumn);
        }
        dto.setBaseParams(baseParams);
        dto.setDatasourceUrl(SUGGEST_URL);
    }

    /**
     * Handles moving text related properties from the field object to the dto.  Will set the value, Xtype, allowedValues, etc. on the dto.
     */
    private void handleTextField(FormItemDTO dto) {

        if (!Util.isEmpty(_field.getAllowedValues()) && !_field.isDynamic()) {
            if (!_field.isMulti()) {
                handleSingleSelection(dto);
            } else {
                if (_field.DISPLAY_TYPE_CHECKBOX.equals(_field.getDisplayType())) {
                    dto.setXtype(Component.XTYPE_CHECKBOX_GRP);
                } else {
                    dto.setXtype(Component.XTYPE_MULTISELECT);
                }
            }
        } else if (_field.isDynamic() && _field.getAllowedValuesDefinition() != null) {
            if (!_field.isMulti()) {
                handleDynamicSingleSelection(dto);
            } else {
                dto.setXtype(Component.XTYPE_DYNAMIC_MULTISUGGEST);
            }
        } else {
            if (_field.isMulti()) {
                dto.setXtype(Component.XTYPE_MULT_TEXT);
            } else if (_field.DISPLAY_TYPE_TEXTAREA.equals(_field.getDisplayType())) {
                dto.setXtype(Component.XTYPE_TEXTAREA);
            } else if (_field.DISPLAY_TYPE_LABEL.equals(_field.getDisplayType())) {
                handleLabel(dto);
            } else {
                dto.setXtype(Component.XTYPE_TEXT_FIELD);
            }
        }
    }

    /**
     * When a field is not multi valued, we use this method to determine the Xtype
     * of the dto.  Typically if the field has 3 or more allowed values, it will
     * render as a combo box. If the field specifies a displayType then let's override
     * with the specific type.
     */
    private void handleSingleSelection(FormItemDTO dto) {

        if (_field == null)
            return;

        List<Object> allowedValues = _field.getAllowedValues();
        String componentXType = getDisplayTypeOverride(Util.size(allowedValues) >= 3 ? Component.XTYPE_COMBO : Component.XTYPE_RADIO_GRP);

        dto.setXtype(componentXType);
    }

    /**
     * Default to a dynamic combo box component, if a displayType is set on the field allow it
     * to override the combo box.
     */
    private void handleDynamicSingleSelection(FormItemDTO dto) {
        if (_field == null)
            return;

        String componentXType = getDisplayTypeOverride(Component.XTYPE_DYNAMIC_COMBO);
        dto.setXtype(componentXType);
    }

    /**
     * Consolidate this logic so it may be called from dynamic and non-dynamic single selections
     *
     * @param defaultComponentXType a default type
     * @return the correct component type
     */
    private String getDisplayTypeOverride(String defaultComponentXType) {
        if (defaultComponentXType == null)
            throw new RuntimeException("Can not set a null default component type");

        String componentXType = defaultComponentXType;
        if (_field != null) {
            String displayType = _field.getDisplayType();

            // override the component type if explicitly defined
            if (displayType != null) {
                if (_field.DISPLAY_TYPE_COMBOBOX.equals(displayType) ||
                        Util.otob(_field.getAttribute(_field.RENDER_USE_SELECT_BOX))) {
                    componentXType = Component.XTYPE_COMBO;
                } else if (_field.DISPLAY_TYPE_RADIO.equals(displayType)) {
                    componentXType = Component.XTYPE_RADIO_GRP;
                } else if (_field.DISPLAY_TYPE_CHECKBOX.equals(displayType)) {
                    componentXType = Component.XTYPE_CHECKBOX_GRP;
                }
            }
        }

        return componentXType;
    }

    /**
     * Handles moving label related properties from the field object to the dto.
     * Will set the value, Xtype on the dto. This will also handle the case where value is a message key.
     * <p/>
     * Sample usage: <Field displayType='label' displayName='msg_key_here'/>
     */
    private void handleLabel(FormItemDTO dto) {
        dto.setXtype(Component.XTYPE_LABEL);

        String value = Util.otos(_field.getDisplayName());
        if (value != null) {
            value = _rendererUtil.localizedMessage(value);
            dto.addAttribute("text", value);
        }
    }

}
