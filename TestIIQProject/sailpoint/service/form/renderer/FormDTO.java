/* (c) Copyright 2009 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.service.form.renderer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import sailpoint.service.form.renderer.item.ButtonDTO;

/**
 * Json-serializable DTO which renders a form configuration.
 *
 * Note that some extended properties on the FormItemDTO object
 * require the use of the javascript class sailpoint.form.FormPanel rather
 * than the default ext FormPanel implementation.
 *
 * @author: jonathan.bryant@sailpoint.com
 */
public class FormDTO {

    private String id;

    private String name;

    /**
     * The id of the workitem tied to this form
     */
    private String workItemId;

    /**
     * Form title. If non-null an EXT menu bar with the given title will
     * be added to the form panel.
     */
    private String title;

    /**
     * Block of text displayed immediately below the title. Used for
     * form instructions and what have you.
     */
    private String subtitle;

    /**
     * List of Maps representing SectionDTO objects composing this form. At this point
     * we assume the field is composed of Sections rather than fields since
     * this is how sailpoint.object.Form is constructed. In the future we may
     * want to make this more flexible.
     */
    private List<Map<String, Object>> items;

    /**
     * Indicates the form is is read-only mode
     */
    private boolean readOnly;

    /**
     * True if this form is a wizard, ie it should be displayed
     * in a card layout with next/previous buttons.
     */
    private boolean wizard;

    /**
     * Representation of the form bean's state that belongs to this form so that we can
     * fetch the form later just with the data
     */
    Map<String,Object> formBeanState;

    /**
     * The class of the form bean.  Used to instantiate the form bean through reflection
     */
    String formBeanClass;

    /**
     * The electronic signature to display when completing the work item - non-null if an esignature
     * is required.
     */
    private String esigMeaning;

    /**
     * Form buttons
     */
    private List<ButtonDTO> buttons;

    public FormDTO() {
    }

    public FormDTO(String id, String name, String title) {
        this.id = id;
        this.name = name;
        this.title = title;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSubtitle() {
        return subtitle;
    }

    public void setSubtitle(String subtitle) {
        this.subtitle = subtitle;
    }

    public List<Map<String, Object>> getItems() {
        return items;
    }

    public void setItems(List<Map<String, Object>> items) {
        this.items = items;
    }

    public void addItem(SectionDTO dto) {
        if (items == null)
            items = new ArrayList<Map<String, Object>>();
        items.add(dto.toMap());
    }

    public List<ButtonDTO> getButtons() {
        return buttons;
    }

    public void setButtons(List<ButtonDTO> buttons) {
        this.buttons = buttons;
    }

    public void addButton(ButtonDTO dto) {
        if (buttons == null)
            buttons = new ArrayList<ButtonDTO>();
        buttons.add(dto);
    }

    public boolean isReadOnly() {
        return readOnly;
    }

    public void setReadOnly(boolean readOnly) {
        this.readOnly = readOnly;
    }

    public String getWorkItemId() {
        return workItemId;
    }

    public void setWorkItemId(String workItemId) {
        this.workItemId = workItemId;
    }

    public boolean isWizard() {
        return wizard;
    }

    public void setWizard(boolean wizard) {
        this.wizard = wizard;
    }

    public Map<String, Object> getFormBeanState() {
        return formBeanState;
    }

    public void setFormBeanState(Map<String, Object> formBeanState) {
        this.formBeanState = formBeanState;
    }

    public String getFormBeanClass() {
        return formBeanClass;
    }

    public void setFormBeanClass(String formBeanClass) {
        this.formBeanClass = formBeanClass;
    }

    public String getEsigMeaning() {
        return esigMeaning;
    }

    public void setEsigMeaning(String esigMeaning) {
        this.esigMeaning = esigMeaning;
    }
}
