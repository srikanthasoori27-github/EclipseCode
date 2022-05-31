/*
 * (c) Copyright 2016. SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import sailpoint.object.ColumnConfig;
import sailpoint.object.Identity;
import sailpoint.object.TableColumnPreference;
import sailpoint.object.UIConfig;
import sailpoint.object.UIPreferences;
import sailpoint.tools.GeneralException;
import sailpoint.tools.InvalidParameterException;
import sailpoint.tools.Util;
import sailpoint.web.UserContext;

/**
 * Service used to get/set the logged in users table column preferences.
 *
 * The tableId used is '[columnConfigKey]-[additional identifier]'. We need the additional identifier for cases where
 * the same column config key gets used for multiple tables. In the certification pages the 'Decisions Left' tab and
 * 'Complete' tab share the same table config key so we use the tab name as the additional identifier.
 *
 * The column id used in the TableColumnPreference selected/unselected lists is the column config header key or
 * property attribute.
 *
 * @author patrick.jeong
 */
public class TablePreferencesService {

    private UserContext userContext;

    /**
     * Constructor
     *
     * @param userContext The UserContext
     */
    public TablePreferencesService(UserContext userContext) throws GeneralException {
        if (userContext == null) {
            throw new InvalidParameterException("userContext");
        }
        
        this.userContext = userContext;
    }

    /**
     * Get the logged in users table column preferences for table id. We only need to return the selected columns list.
     *
     * Compare with column config to do lazy upgrading of column preferences. The returned column string list will
     * include new columns if new columns were added or automatically exclude columns that were removed.
     *
     * Columns with hideable set to false will automatically get added since they should not be able to get removed.
     *
     * @param tableId table identifier
     * @return list of users table columns preferences
     * @throws GeneralException
     */
    public List<String> getTableColumnPreferences(String tableId) throws GeneralException {
        // need table id
        if (Util.isNullOrEmpty(tableId)) {
            return Collections.emptyList();
        }

        // get the table column preferences map
        @SuppressWarnings("unchecked")
        Map<String, TableColumnPreference> tableColumnPreferenceMap =
                (Map<String, TableColumnPreference>)userContext.getLoggedInUser().getUIPreference(UIPreferences.PRF_TABLE_COLUMN_PREFERENCES);
        TableColumnPreference tableColumnPreference = (tableColumnPreferenceMap == null) ? null : tableColumnPreferenceMap.get(tableId);
        if (tableColumnPreference == null) {
            // If we dont already have a preference, we will lazy upgrade an empty one, which will give us a fresh start.
            tableColumnPreference = new TableColumnPreference();
        }

        // Remove any selected columns that are no longer valid, add any newly configured columns
        return lazyUpgradeSelectedColumns(tableId, tableColumnPreference);
    }

    /**
     * Get the logged in user's table grouping preference for the specified table
     * @param tableId The id of the table
     * @return The group by column name
     */
    public String getTableGroupingPreference(String tableId) throws GeneralException {
        Map<String, String> groupingPreferences = getGroupingPreferencesMap();
        return groupingPreferences.get(tableId);
    }

    /**
     * Get the logged in user's table grouping preference for the specified table
     * @param tableId The id of the table
     * @return The group by column name
     */
    public boolean hasTableGroupingPreference(String tableId) throws GeneralException {
        return getGroupingPreferencesMap().containsKey(tableId);
    }

    /**
     * Set the logged in user's table grouping preference for the specified table
     * @param tableId The id of the table to set the grouping for
     * @param columnName The name of the column
     */
    public void setTableGroupingPreference(String tableId, String columnName) throws GeneralException {
        Map<String, String> groupingPreferences = getGroupingPreferencesMap();
        groupingPreferences.put(tableId, columnName);
        /* Commit the changes */
        userContext.getContext().saveObject(userContext.getLoggedInUser());
        userContext.getContext().commitTransaction();
    }

    /**
     * Retrieves the grouping map from the logged in user's UIPreference
     * @return The grouping map
     */
    private Map<String, String> getGroupingPreferencesMap() throws GeneralException {
        Identity currentUser = userContext.getLoggedInUser();
        Map<String, String> groupingPreferences = (Map<String, String>) currentUser.getUIPreference(UIPreferences.PRF_TABLE_GROUPING_PREFERENCES);
        /* Do not return a null map */
        if (groupingPreferences == null) {
            groupingPreferences = new HashMap<>();
            currentUser.setUIPreference(UIPreferences.PRF_TABLE_GROUPING_PREFERENCES, groupingPreferences);
        }
        return groupingPreferences;
    }

    /**
     * Remove any selected columns that are no longer valid and add any newly configured columns.
     * Check to make sure all columns that have hideable set to false are in the list.
     *
     * Returns the edited selected columns list.
     *
     * @param tableId table identifier
     * @param tableColumnPreference TableColumnPreference for table
     * @return updated list of users table columns preferences
     */
    private List<String> lazyUpgradeSelectedColumns(String tableId, TableColumnPreference tableColumnPreference) {
        List<String> tableColumnPreferenceList = new ArrayList<>();

        String columnConfigKey = getColumnConfigKey(tableId);

        @SuppressWarnings("unchecked")
        List<ColumnConfig> columnConfigs = (List<ColumnConfig>)UIConfig.getUIConfig().getObject(columnConfigKey);

        // invalid column config key?
        if (Util.isEmpty(columnConfigs)) {
            return tableColumnPreferenceList;
        }

        // The list of valid column id keys
        Set<String> columnIdKeyList = new HashSet<>();
        List<String> selectedColumns = tableColumnPreference.getSelectedColumns();
        List<String> unselectedColumns = tableColumnPreference.getUnSelectedColumns();

        List<String> userPreferenceColumns = new ArrayList<>();
        if (!Util.isEmpty(selectedColumns)) {
            userPreferenceColumns.addAll(selectedColumns);
        }

        if (!Util.isEmpty(unselectedColumns)) {
            userPreferenceColumns.addAll(unselectedColumns);
        }

        List<String> newColumns = new ArrayList<>();
        List<String> mustShowColumns = new ArrayList<>();
        List<String> fixedLeftColumns = new ArrayList<>();
        List<String> fixedRightColumns = new ArrayList<>();

        // Save all the possible column id keys and check to see if the column config is new
        for (ColumnConfig columnConfig : Util.iterate(columnConfigs)) {
            // skip the field only columns
            if (columnConfig.isFieldOnly()) {
                continue;
            }

            String columnId = getColumnId(columnConfig);
            if (Util.isNullOrEmpty(columnId)) {
                continue;
            }
            columnIdKeyList.add(columnId);

            // Check to see if the column config is new and not hidden by default
            if (!userPreferenceColumns.contains(columnId) && !columnConfig.isHidden()) {
                newColumns.add(columnId);
            }

            // Check to see if column hideable set to false
            if (!columnConfig.isHideable()) {
                mustShowColumns.add(columnId);
            }
            
            // Checks if the column is fixed, for reordering later
            if (columnConfig.getFixed() != null) {
                switch (columnConfig.getFixed()) {
                case Left:
                    fixedLeftColumns.add(columnId);
                    break;
                case Right:
                    fixedRightColumns.add(columnId);
                    break;
                }
            }
        }

        // Add all the valid users selected columns
        for (String columnId : Util.iterate(selectedColumns)) {
            if (columnIdKeyList.contains(columnId)) {
                tableColumnPreferenceList.add(columnId);
            }
        }

        // Add all the new columns
        tableColumnPreferenceList.addAll(newColumns);

        // Make sure all non hideable columns are in the list
        for (String columnId : Util.iterate(mustShowColumns)) {
            if (!tableColumnPreferenceList.contains(columnId)) {
                tableColumnPreferenceList.add(columnId);
            }
        }
        
        for (String columnId : fixedLeftColumns) {
            // if the fixed left column is not first in the list
            if (tableColumnPreferenceList.indexOf(columnId) != 0) {
                // remove it and add it to the beginning of the list
                tableColumnPreferenceList.remove(columnId);
                tableColumnPreferenceList.add(0, columnId);
            }
        }
        for (String columnId : fixedRightColumns) {
            // if the fixed right column is not last in the list
            if (tableColumnPreferenceList.indexOf(columnId) != tableColumnPreferenceList.size()-1) {
                // remove it and add it to the end of the list
                tableColumnPreferenceList.remove(columnId);
                tableColumnPreferenceList.add(columnId);
            }
        }

        return tableColumnPreferenceList;
    }

    /**
     * Get the column identifier. Check for headerKey, dataIndex, and the property.
     *
     * @param columnConfig ColumnConfig object
     * @return String column identifier
     */
    private String getColumnId(ColumnConfig columnConfig) {
        String columnId = columnConfig.getHeaderKey();

        // if header key doesn't exist use the property attribute
        if (Util.isNullOrEmpty(columnId)) {
            columnId = columnConfig.getDataIndex();
        }

        // if its still empty try getting property
        if (Util.isNullOrEmpty(columnId)) {
            columnId = columnConfig.getProperty();
        }

        return columnId;
    }

    /**
     * Parse out the column config key using dash as the delimiter.
     *
     * @param tableId table identifier string
     * @return String column config key
     */
    public String getColumnConfigKey(String tableId) {
        String[] idComponents = tableId.split("-");
        return idComponents[0];
    }

    /**
     * Set the users table column preferences for tableId.
     *
     * The unselected table columns are calculated using the current column configs and selected columns so that it can
     * be used later for lazy upgrading.
     *
     * You should never be able to set the selected columns to null or empty.
     *
     * @param tableId table identifier
     * @param selectedColumns the list of selected columns ids
     * @throws GeneralException
     */
    public void setTableColumnPreferences(String tableId, List<String> selectedColumns) throws GeneralException {
        @SuppressWarnings("unchecked")
        Map<String, TableColumnPreference> tableColumnPreferenceMap =
                (Map<String, TableColumnPreference>)userContext.getLoggedInUser().getUIPreference(UIPreferences.PRF_TABLE_COLUMN_PREFERENCES);

        // initialize if it hasn't been initialized
        if (tableColumnPreferenceMap == null) {
            tableColumnPreferenceMap = new HashMap<>();
        }

        // Don't allow setting to empty or null. If you really need to reset column preferences you can
        // do it directly via Identity:setUIPreference.
        if (Util.isEmpty(selectedColumns)) {
            throw new UnsupportedOperationException("Can't set columns to empty or null");
        }

        // Calculate unselected columns
        List<String> unselectedColumns = getUnselectedColumns(tableId, selectedColumns);

        TableColumnPreference tableColumnPreference = new TableColumnPreference(selectedColumns, unselectedColumns);

        tableColumnPreferenceMap.put(tableId, tableColumnPreference);

        userContext.getLoggedInUser().setUIPreference(UIPreferences.PRF_TABLE_COLUMN_PREFERENCES, tableColumnPreferenceMap);
        userContext.getContext().saveObject(userContext.getLoggedInUser());
        userContext.getContext().commitTransaction();
    }

    /**
     * Calculate and return the list of unselected columns so that we can lazy upgrade when column configs change.
     *
     * @param tableId table identifier
     * @param selectedColumns the list of selected column ids
     * @return list of unselected columns
     */
    private List<String> getUnselectedColumns(String tableId, List<String> selectedColumns) {
        List<String> unselectedColumns = new ArrayList<>();

        String columnConfigKey = getColumnConfigKey(tableId);
        @SuppressWarnings("unchecked")
        List<ColumnConfig> columnConfigs = (List<ColumnConfig>)UIConfig.getUIConfig().getObject(columnConfigKey);

        for (ColumnConfig columnConfig : columnConfigs) {
            // skip the field only columns
            if (columnConfig.isFieldOnly()) {
                continue;
            }

            String columnId = getColumnId(columnConfig);
            if (Util.isNullOrEmpty(columnId)) {
                continue;
            }

            if (!selectedColumns.contains(columnId)) {
                unselectedColumns.add(columnId);
            }
        }

        return unselectedColumns;
    }
}
