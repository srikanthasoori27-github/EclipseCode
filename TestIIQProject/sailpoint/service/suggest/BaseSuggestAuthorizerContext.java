/* (c) Copyright 2017 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.service.suggest;

import sailpoint.object.ManagedAttribute;
import sailpoint.service.listfilter.ListFilterDTO;
import sailpoint.service.listfilter.ListFilterDTO.DataTypes;
import sailpoint.tools.Util;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BaseSuggestAuthorizerContext implements SuggestAuthorizerContext {

    /**
     * Inner class to hold the definition of a suggest class
     */
    private static class SuggestClass {

        private String className;
        private List<String> columnNames;
        private boolean allowObjectList;

        private SuggestClass (String className, boolean allowObjectList, String... columnNames) {
            this.className = className;
            this.allowObjectList = allowObjectList;
            this.columnNames = Util.arrayToList(columnNames);
        }

        private boolean isClassSupported() {
            return this.allowObjectList;
        }

        private boolean isColumnSupported(String columnName) {
            return Util.nullSafeContains(this.columnNames, columnName);
        }

        private void merge(SuggestClass otherSuggestClass) {
            if (otherSuggestClass.allowObjectList) {
                this.allowObjectList = true;
            }

            for (String column : Util.iterate(otherSuggestClass.columnNames)) {
                if (!Util.nullSafeContains(this.columnNames, column)) {
                    this.columnNames.add(column);
                }
            }
        }

    }

    /**
     * Map to hold added suggest classes and columns. Keyed by object simple name.
     */
    private Map<String, SuggestClass> suggestObjectMap = new HashMap<>();

    /**
     * Constructor
     */
    public BaseSuggestAuthorizerContext() {}

    /**
     * Constructor for filter based authorizer context
     * @param filters List of ListFilterDTOs to pull allowed objects and columns from
     */
    public BaseSuggestAuthorizerContext(List<ListFilterDTO> filters) {
        add(filters);
    }

    /**
     * Add a class name that will be allowed
     * @param className Name of the class
     * @return BaseSuggestAuthorizerContext for chaining
     */
    public BaseSuggestAuthorizerContext add(String className) {
        add(className, true);
        return this;
    }

    /**
     * Add a class and column that will be allowed
     * @param className Name of the class
     * @param allowObjectList If true, allow the class itself to be a suggest. If false, only allow the column.
     * @param columnNames List of column names to allow
     * @return BaseSuggestAuthorizerContext for chaining
     */
    public BaseSuggestAuthorizerContext add(String className, boolean allowObjectList, String... columnNames) {
        String simpleClassName = getSimpleClassName(className);
        SuggestClass suggestClass = new SuggestClass(simpleClassName, allowObjectList, columnNames);
        if (!this.suggestObjectMap.containsKey(simpleClassName)) {
            this.suggestObjectMap.put(simpleClassName, suggestClass);
        } else {
            this.suggestObjectMap.get(simpleClassName).merge(suggestClass);
        }
        return this;
    }

    /**
     * Add a ListFilterDTO as an allowed suggest. This will add Identity class if the filter is Identity type, otherwise
     * will allow the configured class and column if Column type
     * @param listFilterDTO The ListFilterDTO to add
     * @return BaseSuggestAuthorizerContext for chaining
     */
    public BaseSuggestAuthorizerContext add(ListFilterDTO listFilterDTO) {
        DataTypes type = listFilterDTO.getDataType();
        switch (type) {
           case Application:
           case Identity: 
               add(type.toString());
               break;
           case Attribute:
               add(ManagedAttribute.class.getSimpleName());
               break;
           case Object:
               add((String)listFilterDTO.getAttribute(ListFilterDTO.ATTR_SUGGEST_CLASS), true);
           default:
               if (!Util.isNothing((String)listFilterDTO.getAttribute(ListFilterDTO.ATTR_SUGGEST_CLASS))) {
                   add((String)listFilterDTO.getAttribute(ListFilterDTO.ATTR_SUGGEST_CLASS), false, (String)listFilterDTO.getAttribute(ListFilterDTO.ATTR_SUGGEST_COLUMN));
               }
               break;
        }
        return this;
    }

    /**
     * Add a list of ListFilterDTO as allowed suggests. This will add Identity class if the filter is Identity type, otherwise
     * will allow the configured class and column if Column type
     * @param listFilterDTOList The list of ListFilterDTO to add
     * @return BaseSuggestAuthorizerContext for chaining
     */
    public BaseSuggestAuthorizerContext add(List<ListFilterDTO> listFilterDTOList) {

        for (ListFilterDTO listFilterDTO : Util.iterate(listFilterDTOList)) {
            add(listFilterDTO);
        }

        return this;
    }

    @Override
    public boolean isClassAllowed(String className) {
        if (new GlobalSuggestAuthorizerContext().isClassAllowed(className)) {
            return true;
        }

        SuggestClass suggestClass = this.suggestObjectMap.get(getSimpleClassName(className));
        return suggestClass != null && suggestClass.isClassSupported();
    }

    @Override
    public boolean isColumnAllowed(String className, String columnName) {
        if (new GlobalSuggestAuthorizerContext().isColumnAllowed(className, columnName)) {
            return true;
        }

        SuggestClass suggestClass = this.suggestObjectMap.get(getSimpleClassName(className));
        return suggestClass != null && suggestClass.isColumnSupported(columnName);
    }

    private String getSimpleClassName(String className) {
        String simpleClassName = className.startsWith(SAILPOINT_OBJECT_PACKAGE) ? className.substring(SAILPOINT_OBJECT_PACKAGE.length()) : className;
        return simpleClassName.toUpperCase();
    }
}
