package sailpoint.service.form;

////////////////////////////////////////////////////////////////////////////
//
// Master form building from the database
//
////////////////////////////////////////////////////////////////////////////

import sailpoint.object.Field;
import sailpoint.object.Form;
import sailpoint.object.FormItem;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

import java.util.HashMap;
import java.util.Map;

/**
 * Merges two form fields.
 * The secondary form gets merged into the primary form.
 */
public class FormMerger {
    private Form primaryForm;
    private Form secondaryForm;

    private Map<String, String> primaryMap;
    private Map<String, String> secondaryMap;

    private static final String EMPTY_SECTION = "";

    public FormMerger(Form primaryForm, Form secondaryForm) {
        this.primaryForm = primaryForm;
        this.secondaryForm = secondaryForm;
    }

    public Form getPrimaryForm() {
        return primaryForm;
    }

    public Form getSecondaryForm() {
        return secondaryForm;
    }

    public void merge() throws GeneralException {
        primaryMap = new HashMap<String, String>();
        secondaryMap = new HashMap<String, String>();

        populateMap(primaryMap, primaryForm);
        populateMap(secondaryMap, secondaryForm);

        for (String secondaryFieldName : secondaryMap.keySet()) {
            String secondarySectionName = secondaryMap.get(secondaryFieldName);
            Form.Section secondarySection = secondaryForm.getSection(secondarySectionName);
                /* If the section name is null or "", just return the first section */
            if(secondarySection==null && secondarySectionName.equals(EMPTY_SECTION)) {
                secondarySection = secondaryForm.getSection();
            }
            Field secondaryField = secondaryForm.getField(secondaryFieldName);
            if (!primaryMap.containsKey(secondaryFieldName)) {
                addFieldToPrimaryForm(secondaryField, secondarySection);
            }
        }
    }

    // this will add to existing section or create a new section and add it
    private void addFieldToPrimaryForm(Field secondaryField, Form.Section secondarySection) throws GeneralException {
        Form.Section section = primaryForm.getSection(secondarySection.getName());

            /* If the section wasn't found and the section's name is null, just add it to the
            first section */
        if(section == null && secondarySection.getName() == null) {
            section = primaryForm.getSection();
        }
        if (section == null) {
            section = new Form.Section();
            section.setName(secondarySection.getName());
            section.setLabel(secondarySection.getLabel());
            primaryForm.add(section);
        }

        section.add(secondaryField);
    }

    private void populateMap(Map<String, String> map, Form form) {
        for (Form.Section section : Util.safeIterable(form.getSections())) {
            String sectionName = section.getName();
            if (Util.isNullOrEmpty(sectionName)) {
                sectionName = EMPTY_SECTION;// just a dummy key
            }
            for (FormItem item : Util.safeIterable(section.getItems())) {
                if (item instanceof  Field) {
                    map.put(((Field) item).getName(), sectionName);
                }
            }
        }
    }
}