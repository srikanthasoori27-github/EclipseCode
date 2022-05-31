package sailpoint.service.form.renderer.creator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.Attributes;
import sailpoint.object.Field;
import sailpoint.object.Identity;
import sailpoint.object.IdentityFilter;
import sailpoint.rest.ManagedAttributesResource;
import sailpoint.service.form.renderer.FormRenderer;
import sailpoint.service.form.renderer.FormRendererUtil;
import sailpoint.service.form.renderer.item.FormItemDTO;
import sailpoint.service.suggest.SuggestHelper;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Internationalizer;
import sailpoint.tools.Util;

/**
 * Provides a base layer of functionality for creating FormItemDTO objects.  This class is to be overrided
 * by FormItemDTOCreators that need to apply specific logic or properties to the FormItemDTO depending on how they
 * are to be rendered.
 * @author: pholcomb
 */
public abstract class AbstractFormItemDTOCreator {
    private static final Log log = LogFactory.getLog(AbstractFormItemDTOCreator.class);

    protected FormItemDTO _dto;
    protected Field _field;

    /**
     * A renderer utility class that carries around things like the context, locale, timezone, etc...
     */
    protected FormRendererUtil _rendererUtil;

    /**
     * The parent (section) id that we will use to set the id of this field
     */
    protected String _parentId;

    public AbstractFormItemDTOCreator() {}

    public AbstractFormItemDTOCreator(Field field, FormRendererUtil util, String parentId) {
        _field = field;
        _rendererUtil = util;
        _parentId = parentId;
    }


    public abstract FormItemDTO getDTO() throws GeneralException ;

    /**
     * Create the form item dto and setup basic properties on it
     */
    protected FormItemDTO createFormItemDTO(FormItemDTO dto) {
        // If the field name is not supplied we must generate one so we have a unique field id
        String name = _field.getName() != null ? _field.getName() :
                java.util.UUID.randomUUID().toString().replaceAll("-", "");
        dto.setName(_field.getName());
        dto.setItemId("field-" + _parentId + "-" + name);
        dto.setFieldLabel(_rendererUtil.localizedMessage(_field.getDisplayLabel()));
        String localizedHelp = Internationalizer.getMessage(_field.getHelpKey(), _rendererUtil.getLocale());
        dto.setHelpText(localizedHelp != null ? localizedHelp : _field.getHelpKey());
        dto.setFilter(_field.getFilterString());
        dto.setSortable(_field.isSortable());
        dto.setRequired(_field.isRequired());
        dto.setAttributes(_field.getAttributes());
        dto.setPreviousValue(_field.getPreviousValue());
        dto.setHidden(_field.isHidden());
        dto.setColumnSpan(_field.getColumnSpan());
        dto.setPostBack(_field.isPostBack());

        Attributes attrs = dto.getAttributes();
        if (!Util.isEmpty(attrs)) {
            if (attrs.containsKey("height")) {
                try {
                    dto.setHeight(attrs.getInteger("height"));
                } catch (Exception ex) {
                    log.warn("Ignoring invalid height attribute on form field: " + dto.getName());
                }
            }
        }

        if (_field.isReadOnly())  {
            dto.setDisabled(true);
        }

        dto.setPreviousValue(_field.getPreviousValue());

        this.setAllowedValues(dto);

        return dto;
    }

    /**
     * Based on the type or displayType of the form item, we configure the value (by casting it
     * to the appropriate value and add other necessary attributes that the display will need
     * @param dto The FormItemDTO we are updating
     * @return The FormItemDTO we are updating
     */
    protected FormItemDTO updateFormItemByType(FormItemDTO dto) {
        dto.setValue(_field.getValue());

        if (Field.TYPE_BOOLEAN.equals(_field.getType())){
            // bug#18198 normalize this to a Boolean, ExtJs doesn't allow upper case TRUE
            // bug#19144 but null is not the same as false. Leave null alone.
            Boolean value = null;
            if (_field.getValue() != null) {
                value = Util.otob(_field.getValue());
            }
            dto.setValue(value);
        } else if (Field.TYPE_DATE.equals(_field.getType())){
            if (_field.getValue() != null && !"".equals(_field.getValue())){
                if (_field.getValue() instanceof Long)
                    dto.setValue(new Date((Long)_field.getValue()));
                else if (_field.getValue() instanceof Date)
                    dto.setValue((Date)_field.getValue());
            }
        } else if (Field.TYPE_SECRET.equals(_field.getType())){
            if (_field.getValue() != null && !"".equals(_field.getValue()))
                dto.setValue(FormRenderer.SECRET_DUMMY_VALUE);
        } else if (Field.TYPE_MANAGED_ATTR.equals(_field.getType())){
            //Unless specified to use ID, we will post name
            if(!"id".equalsIgnoreCase((String)_field.getAttribute(Field.ATTR_VALUE_PROPERTY)))  {
                dto.setValueField(Field.ATTR_VALUE_PROPERTY_NAME);
            }

            if (_field.getValue() != null){
                Collection values;
                if (Collection.class.isAssignableFrom(_field.getValue().getClass())){
                    values = (Collection)_field.getValue();
                } else if (_field.isMulti()) {
                    // IIQETN-5124 only break up field if multi-valued field
                    values = Util.stringToList(_field.getValue().toString());
                } else {
                    values = Arrays.asList(_field.getValue().toString());
                }

                if (!values.isEmpty()){
                    List<Map<String, Object>> suggestResults = new ArrayList<Map<String,Object>>();
                    // Get items one at a time so we don't lose the ordering
                    try {
                        for(Object value : values){
                            suggestResults.add(ManagedAttributesResource.getSuggestObject(value.toString(), _rendererUtil.getContext(), _field.getFilterString()));
                        }
                        dto.setValue(_field.isMulti() ? suggestResults : suggestResults.get(0));
                    } catch(GeneralException ge) {
                        log.warn("Exception while getting suggest object: "+ge.getMessage(), ge);
                    }
                }
            }

            // if multi then all managed attributes are clickable.. for now only
            // indirect groups show up in a multi-select of type ManagedAttribute
            if (_field.isMulti()) {
                dto.setAllowValueClick(true);
            }
        } else {

            if (_field.isDynamic() && _field.getAllowedValuesDefinition() != null) {
                if (!_field.isMulti()) {
                    //Try and update the value to an allowed values model object
                    if (_field.getValue() != null && dto.getAllowedValues() != null) {
                        Iterator i = dto.getAllowedValues().iterator();
                        while (i.hasNext()) {
                            String[] allowVal = (String[]) i.next();
                            if (_field.getValue().equals(allowVal[0])) {
                                //Create an autoRecord so the store will initially have a representation of the value
                                dto.addAttribute("autoRecord", SuggestHelper.getResultMap(null, allowVal));
                                break;
                            }
                        }
                    }
                }
                dto.addAttribute("formBeanClass", _rendererUtil.getFormBeanClass());
                dto.addAttribute("formBeanState", _rendererUtil.getFormBeanState());
                dto.setDynamic(true);
            }

            dto.setValue(_field.getValue());
        }
        return dto;
    }

    /**
     * Setup the list of allowed values on the dto by iterating over the values stored on the field
     */
    protected void setAllowedValues(FormItemDTO dto) {
        if (_field.getAllowedValues() != null && _field.getAllowedValues().size() > 0) {
            for (Object val : _field.getAllowedValues()) {

                String value = null;
                String display = null;
                if (val instanceof List) {
                    List l = (List) val;
                    if (l.size() > 0) {
                        Object o = l.get(0);
                        if (o != null) {
                            value = o.toString();
                            display = value;
                            if (l.size() > 1) {
                                o = l.get(1);
                                if (o != null)
                                    display = o.toString();
                            }
                        }
                    }
                } else if (val != null) {
                    value = val.toString();
                    display = value;
                }

                if (value != null) {

                    if (display != null) {
                        display = _rendererUtil.localizedMessage(display);
                    }

                    dto.addAllowedValue(new String[]{value, display});
                }
            }
        }
    }

    /**
     * Updates the dto if it has a type class set on the field.  Returns true if the field has an sp class on it
     */
    protected void handleSPClass(FormItemDTO dto) {
        Class spClass = _field.getTypeClass();
        handleForceSelectionForSuggest(_field, dto);
        //If the form is backed by a model and the field object has a unique name, we will use name as the post value.
        if (_rendererUtil.useNameAsValue(spClass, _field)) {
            dto.setValueField(Field.ATTR_VALUE_PROPERTY_NAME);
        }
        dto.setSuggestClass(spClass.getName());

        // Bug #18634 - Removing the isMulti check to allow multi-valued suggests fields to use IdentitySuggest configurations
        if (spClass == Identity.class) {
            String suggestId = _rendererUtil.getForm().getName() + "-form-" + dto.getName() + "-field";
            dto.setItemId(suggestId);
            Map<String, Object> baseParams = dto.getBaseParams();
            if (baseParams == null) {
                baseParams = new HashMap<String, Object>();
            }
            baseParams.put("suggestId", dto.getItemId());
            if (_field.getName() != null && _field.getName().equalsIgnoreCase(Identity.ATT_MANAGER)) {
                baseParams.put("context", IdentityFilter.IDENTITY_MANAGER_ATTRIBUTE);
            } else {
                baseParams.put("context", "CustomAttribute");
            }
            dto.setBaseParams(baseParams);
        }

        if (_field.getValue() != null) {
            Collection values;
            if (Collection.class.isAssignableFrom(_field.getValue().getClass())) {
                values = (Collection) _field.getValue();
            } else {
                // TODO:  Consider using Util.cvsToList(field.getValue().toString(), true) instead
                values = Util.stringToList(_field.getValue().toString());
            }

            if (!values.isEmpty()) {
                List<Map<String, Object>> suggestResults = new ArrayList<Map<String, Object>>();
                // Get items one at a time so we don't lose the ordering
                for (Object value : values) {
                    // Ideally the List won't contain any nulls, but we can't guarantee that.
                    // This if block effectively discards the null values
                    if (value != null) {
                        try {
                            Map<String, Object> suggestValue;
                            if (_field.getAttribute(Field.ATTR_VALUE_OBJ_COLUMN) != null) {
                                String columnName = (String) _field.getAttribute(Field.ATTR_VALUE_OBJ_COLUMN);
                                suggestValue = SuggestHelper.getSuggestColumnValue(spClass, columnName, value, _rendererUtil.getContext());
                            } else {
                                suggestValue = SuggestHelper.getSuggestObject(spClass, value.toString(), _rendererUtil.getContext());
                            }

                            // Get rid of null map
                            if(!Util.isEmpty(suggestValue)) {
                                suggestResults.add(suggestValue);
                            }
                        } catch (GeneralException ge) {
                            log.warn("Exception while getting suggest results: " + ge.getMessage(), ge);
                        }
                    }
                }

                // Check for non empty list
                if(!Util.isEmpty(suggestResults)) {
                    dto.setValue(_field.isMulti() ? suggestResults : suggestResults.get(0));
                }
            }

            if (_field.getValue() != null && dto.getValue() == null && !_field.isMulti()) {
                //We were not able to resolve the value in the suggest helper
                //See if we might have to extra recs in the store that would work
                if (_field.getAttribute(Field.ATTR_EXTRA_RECS) != null) {
                    for (Map m : (List<Map>) _field.getAttribute(Field.ATTR_EXTRA_RECS)) {
                        if (m.get(dto.getValueField() != null ? dto.getValueField() : "id").equals(_field.getValue())) {
                            dto.setValue(m);
                            //Have to set forceSelection to false because the corresponding record will not be added to
                            //the store until store.load is called
                            dto.addAttribute("forceSelection", "false");
                            break;
                        }
                    }
                }
            }
        }
    }

    /**
     * this determines whether a value must be selected
     * for the suggest
     *
     */
    private void handleForceSelectionForSuggest(Field field, FormItemDTO dto) {

        // CH: Application fields should default to force selection
        if (field.isRequired() || field.getType().equals("Application")) {
            dto.addAttribute("forceSelection", "true");
        }
    }
}
