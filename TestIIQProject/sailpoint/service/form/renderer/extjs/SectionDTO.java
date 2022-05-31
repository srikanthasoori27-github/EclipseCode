/* (c) Copyright 2009 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.service.form.renderer.extjs;

import sailpoint.object.Form;

import java.util.HashMap;
import java.util.Map;

/**
 * @author: peter.holcomb
 *
 * A extjs specific form section renderer that handles xtype.
 */
public class SectionDTO extends sailpoint.service.form.renderer.SectionDTO {

    public SectionDTO(Form.Section section) {
        super(section);
    }

    @Override
    public Map<String, Object> toMap() {
        Map<String, Object> sectionMap = super.toMap();
        sectionMap.put("xtype", getXtype());
        sectionMap.put("defaults", getDefaults());
        return sectionMap;
    }

    public String getXtype() {
        if (Form.Section.TYPE_TEXT.equals(this.getType()))
            return "panel";

        return "fieldset";
    }

    public Map<String, Object> getDefaults() {
        Map<String, Object> result = new HashMap<String, Object>();
        result.put("anchor", "100%");

        return result;
    }
}
