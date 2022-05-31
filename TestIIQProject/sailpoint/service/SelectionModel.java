/* (c) Copyright 2016 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.json.JSONException;
import org.json.JSONObject;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

/**
 * SelectionModel: Used to represent selections from the ui.  A selection model can indicate a list of item ids that
 * should either be excluded or included inside of a search.
 */
public class SelectionModel {
    private boolean isInclude;
    private List<String> itemIds;
    private Map<String, String> filterValues;
    private List<SelectionModel> groups;
    private String queryString;
    
    public static final String IS_INCLUDE = "isInclude";
    public static final String ITEM_IDS = "itemIds";
    public static final String FILTER_VALUES = "filterValues";
    public static final String GROUPS = "groups";
    public static final String QUERY_STRING = "queryString";

    /**
     * Default constructor. Create empty selection model.
     */
    public SelectionModel() {
        this.isInclude = true;
        this.itemIds = new ArrayList<>();
        this.groups = new ArrayList<>();
    }

    /**
     * Single selection constructor
     * @param id The ID of the single selection for the model
     */
    public SelectionModel(String id) {
        this();
        this.itemIds.add(id);
    }

    /**
     * Constructor.
     * @param isInclude True if itemIds are included in selection, false if they are excluded
     * @param itemIds List of IDs
     */
    public SelectionModel(boolean isInclude, List<String> itemIds) {
        this.isInclude = isInclude;
        this.itemIds = itemIds;
    }

    /**
     * Map constructor.
     * @param selectionModelMap Map representing the selection model
     */
    @SuppressWarnings("unchecked")
    public SelectionModel(Map<String, Object> selectionModelMap) throws GeneralException {
        this.isInclude = (Boolean)selectionModelMap.get(IS_INCLUDE);
        this.queryString = (String)selectionModelMap.get(QUERY_STRING);
        this.itemIds = Util.asList(selectionModelMap.get(ITEM_IDS));
        List<Map<String, Object>> groupsMap = Util.asList(selectionModelMap.get(GROUPS));
        if (!Util.isEmpty(groupsMap)) {
            this.groups = new ArrayList<>();
            for (Map<String, Object> groupMap : groupsMap) {
                this.groups.add(new SelectionModel(groupMap));
            }
        }
        Map<String, Object> filterMap = (Map<String, Object>)selectionModelMap.get(FILTER_VALUES);

        if (!Util.isEmpty(filterMap)) {
            this.filterValues = new HashMap<>();
            for (Map.Entry<String, Object> filterMapEntry : filterMap.entrySet()) {
                // when filterValues map value is a HashMap that contains entries for 'operation' and 'value'
                // we are serializing it here and it is being deserialized back into a map in
                // ListFilterService::getFilterValueMap() in order to create ListFilterValue objects

                // JSONObject.valueToString is being used here so that map key/value strings are wrapped in quotes to
                // prevent deserialization errors caused by special characters. the normal map.toString() does not
                // protect from special characters like '=' so when the value is something like 'CN=somecn,OU=test'
                // the string value of the map becomes {operation=Equal, value=CN=somecn,OU=test} which doesn't
                // deserialize properly.
                Object filterMapValue = filterMapEntry.getValue();

                String serializedValue;
                if (filterMapValue instanceof HashMap) {
                    try {
                        serializedValue = JSONObject.valueToString(filterMapValue);
                    }
                    catch(JSONException e) {
                        throw new GeneralException(e);
                    }
                }
                else {
                    serializedValue = Util.listToCsv(Util.asList(filterMapValue));
                }
                this.filterValues.put(filterMapEntry.getKey(), serializedValue);
            }
        }
    }

    /**
     * Utility function to determine if the user has selected everything.  SelectAll indicates that the user wants
     * all results from the query minus any that are in the itemIds list.  If isInclude is true, it means that anything
     * in the itemIds list is to be involved in the query.  If isInclude is false, that means that the user selected all
     * results and may have removed some (which would be in the itemIds list).
     * @return whether everything is selected or not
     */
    public boolean isSelectAll() {
        return !this.isInclude;
    }

    /**
     * Whether the list of item ids is a negative selection or a positive selection.  If isInclude is false,
     * we are indicating that the list of ids should be excluded from the search or filter
     * @return whether to include the ids or not
     */
    public boolean isInclude() {
        return isInclude;
    }

    public void setInclude(boolean include) {
        isInclude = include;
    }

    /**
     * The list of ids that represent the items in the selection
     * @return the list of ids of the selection
     */
    public List<String> getItemIds() {
        return itemIds;
    }

    public void setItemIds(List<String> itemIds) {
        this.itemIds = itemIds;
    }

    /**
     * @return Map of filter values to limit when isInclude is false (i.e. select all)
     */
    public Map<String, String> getFilterValues() {
        return filterValues;
    }

    public void setFilterValues(Map<String, String> filterValues) {
        this.filterValues = filterValues;
    }

    /**
     * The list of SelectionModel objects representing the groups
     * @return the list of selectionModel objects
     */
    public List<SelectionModel> getGroups() {
        return groups;
    }

    public void setGroups(List<SelectionModel> groups) {
        this.groups = groups;
    }

    public String getQueryString() {
        return queryString;
    }
}
