/*
 * (c) Copyright 2016. SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.object;

import java.util.List;

import sailpoint.tools.xml.AbstractXmlObject;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;

/**
 * XML data holder for users table column preference data.
 * Users UIPreferences will contain a map of these keyed by a table identifier.
 * We keep track of the unselected columns to do lazy column upgrades.
 *
 * @author patrick.jeong
 */
@XMLClass
public class TableColumnPreference extends AbstractXmlObject {

    private static final long serialVersionUID = 5963360322628573243L;

    /**
     * List of column identifiers containing columns the user selected
     */
    private List<String> selectedColumns;

    /**
     * List of column identifiers containing columns the user UNSELECTED
     */
    private List<String> unSelectedColumns;

    public TableColumnPreference() {}
    
    /**
     * Constructor
     *
     * @param selectedColumns List of selected column identifiers
     * @param unSelectedColumns List of unselected column identifiers
     */
    public TableColumnPreference(List<String> selectedColumns, List<String> unSelectedColumns) {
        this.selectedColumns = selectedColumns;
        this.unSelectedColumns = unSelectedColumns;
    }

    @XMLProperty
    public List<String> getSelectedColumns() {
        return selectedColumns;
    }

    public void setSelectedColumns(List<String> selectedColumns) {
        this.selectedColumns = selectedColumns;
    }

    @XMLProperty
    public List<String> getUnSelectedColumns() {
        return unSelectedColumns;
    }

    public void setUnSelectedColumns(List<String> unSelectedColumns) {
        this.unSelectedColumns = unSelectedColumns;
    }

}
