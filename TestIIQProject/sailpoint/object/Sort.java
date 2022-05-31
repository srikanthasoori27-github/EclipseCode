/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.object;

import sailpoint.tools.xml.AbstractXmlObject;
import sailpoint.tools.xml.XMLProperty;

/**
 * @author jonathan.bryant@sailpoint.com
 */
public class Sort extends AbstractXmlObject {

    private String field;
    private boolean ascending;

    public Sort() {
    }

    public Sort(String field, boolean ascending) {
        this.field = field;
        this.ascending = ascending;
    }

    public String getField() {
        return field;
    }

    @XMLProperty
    public void setField(String field) {
        this.field = field;
    }

    public boolean isAscending() {
        return ascending;
    }

    @XMLProperty
    public void setAscending(boolean ascending) {
        this.ascending = ascending;
    }

    /**
     * Compare two Sort objects to see if they are the same.
     * If either field is null, return false since a sort without
     * a field value is not valid.
     */
    public boolean isSame(Sort sort){

        if (sort == null || this.field == null || sort.getField() == null)
            return false;

        if (this.ascending != sort.ascending)
            return false;

        return sort.getField().equals(this.field);
    }
}
