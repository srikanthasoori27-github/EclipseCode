/* (c) Copyright 2015 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.service.form.renderer.extjs;
/**
 * @author: peter.holcomb
 *
 * A DTO that represents an extjs specific Form.  Supports labelAlign and the concept of setting the currentField
 * on a form after validation fails.
 */
public class FormDTO extends sailpoint.service.form.renderer.FormDTO {

    private String currentField;

    private boolean hideRequiredItemsLegend;

    private String labelAlign;

    public FormDTO(String id, String name, String title) {
        super(id, name, title);
    }

    public String getCurrentField() {
        return currentField;
    }

    public void setCurrentField(String currentField) {
        this.currentField = currentField;
    }

    public boolean isHideRequiredItemsLegend() {
        return hideRequiredItemsLegend;
    }

    public void setHideRequiredItemsLegend(boolean hideRequiredItemsLegend) {
        this.hideRequiredItemsLegend = hideRequiredItemsLegend;
    }

    public String getLabelAlign() {
        return labelAlign;
    }

    public void setLabelAlign(String labelAlign) {
        this.labelAlign = labelAlign;
    }
}
