/* (c) Copyright 2015 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.service.form.renderer.extjs;

import java.util.Locale;
import java.util.TimeZone;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.SailPointContext;
import sailpoint.object.Field;
import sailpoint.object.Form;
import sailpoint.object.FormItem;
import sailpoint.service.form.renderer.creator.ExtjsFormItemDTOCreator;
import sailpoint.service.form.renderer.extjs.item.FormItemDTO;
import sailpoint.tools.GeneralException;
import sailpoint.tools.JsonHelper;
import sailpoint.web.FormBean;
import sailpoint.web.extjs.Component;

/**
 * @author: peter.holcomb
 *
 * A form renderer class that provides custom logic for rendering a form inside of an extjs page.  Only logic
 * that is specific to extjs should go into this class
 */
public class FormRenderer extends sailpoint.service.form.renderer.FormRenderer {

    private static final Log log = LogFactory.getLog(FormRenderer.class);

    protected int tabIndex = 1;

    protected boolean hasRequiredItems;

    public FormRenderer() {
    }

    /**
     * Construct a FormRenderer for the given form.
     */
    public FormRenderer(Form form, FormBean formBean, Locale locale,
                        SailPointContext context, TimeZone tz) {
        super(form, formBean, locale, context, tz);
    }

    public FormRenderer(FormBean formBean, Locale locale, SailPointContext context, TimeZone tz) {
        super(formBean, locale, context, tz);
    }

    @Override
    public void init(Form form) {
        super.init(form);

        if (form != null) {
            // The FormHandler may have stashed the current field on the master
            // form.  If it is present, use this to set the current field.
            if (null != form.getAttributes()) {
                String currentField = (String) form.getAttributes().remove(ATT_CURRENT_FIELD);
                if (null != currentField) {
                    this.currentField = currentField;
                }
                // set tabDir even if it's null as that's important too!
                this.tabDir = (String) form.getAttributes().remove(ATT_TAB_DIRECTION);
            }
        }
    }

    @Override
    public FormDTO createDTO() throws GeneralException {
        if(form == null || this.getId() == null) {
            return null;
        }
        FormDTO formDTO = new FormDTO(this.getId(), form.getName(), _rendererUtil.localizedMessage(form.getTitle()));

        formDTO.setLabelAlign(getForm().getLabelAlign());
        formDTO.setCurrentField(this.currentField);
        formDTO.setHideRequiredItemsLegend(!this.hasRequiredItems);
        return (FormDTO)this.decorateDTO(formDTO);
    }

    @Override
    public SectionDTO createSectionDTO(Form.Section section) throws GeneralException {
        currentSection = section;
        SectionDTO sectionDTO = new SectionDTO(section);
        sectionDTO = (SectionDTO) this.decorateSectionDTO(sectionDTO, section);
        for (FormItem item : getSortedItems(section)) {
            if(item instanceof Field) {
                Field field = (Field) item;
                this.hasRequiredItems |= field.isRequired();
            } else {
                log.error("Non-field form objects cannot currently be nested.");
            }

        }
        return sectionDTO;
    }

    @Override
    public FormItemDTO createFormItemDTO(Field field, String parentId) throws GeneralException {
        ExtjsFormItemDTOCreator dtoCreator = new ExtjsFormItemDTOCreator(field, _rendererUtil, parentId, tabIndex++, getDefaultXType());
        return (FormItemDTO)dtoCreator.getDTO();
    }

    /**
     * For the given section, determine what the default field xtype
     * should be. This type will be used in fields where the xtype
     * is not easily derived by field type, and the field's xtype is
     * not specified.
     *
     * @return
     */
    private String getDefaultXType() {

        if (Form.Section.TYPE_DATA_TABLE.equals(currentSection.getType()))
            return Component.XTYPE_HTML_TEMPLATE;
        else if (Form.Section.TYPE_TEXT.equals(currentSection.getType()))
            return Component.XTYPE_HTML_TEMPLATE;
        else
            return Component.XTYPE_TEXT_FIELD;
    }

    public boolean isHasRequiredItems() {
        return hasRequiredItems;
    }

    public void setHasRequiredItems(boolean hasRequiredItems) {
        this.hasRequiredItems = hasRequiredItems;
    }

    public int getTabIndex() {
        return tabIndex;
    }


    public String getDataJson() {
        if (this.postData == null)
            return "";

        return JsonHelper.toJson(this.postData);
    }

    public void setDataJson(String json) throws GeneralException {
        if (json == null || json.length() == 0) {
            this.postData = null;
        } else {
            try {
                this.postData = JsonHelper.mapFromJson(String.class, Object.class, json);
            } catch (Exception e) {
                throw new GeneralException("Could not parse json", e);
            }
        }
    }

    public void setTabIndex(int tabIndex) {
        this.tabIndex = tabIndex;
    }
}
