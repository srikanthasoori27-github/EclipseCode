/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web;

import java.util.List;
import java.util.Map;

import javax.faces.context.FacesContext;
import javax.faces.model.SelectItem;

import sailpoint.object.Filter;
import sailpoint.object.PropertyInfo;
import sailpoint.tools.Util;

/**
 * Pages that want to use the addFilters.xhtml page as an include should have a bean
 * that implements this interface.  They should also consider using the FilterHelper
 * class to provide some of the functionality required here.
 * @see sailpoint.web.util.FilterHelper
 * @author Bernie Margolis
 */
public interface FilterContainer {
    /**
     * Extract the global boolean operator from a filter string.
     *
     * @param filterString The filter string to parse for boolean value.
     * @return String representation of the boolean operator
     */
    static String getBooleanOpFromFilterString(String filterString) {
        //defect 21432 this value is not set, but we have the value in the filterString
        Filter filter = null;
        if(Util.isNotNullOrEmpty(filterString)) {
            filter = Filter.compile(filterString);
        }
        if (null != filter && filter instanceof Filter.CompositeFilter) {
            return ((Filter.CompositeFilter)filter).getOperation().toString();
        } else {
            return Filter.BooleanOperation.AND.name();
        }
    }

    String getInstructions();
    
    /**
     * @return Map of PropertyInfo objects keyed by the property name
     */
    Map<String, PropertyInfo> getPropertyInfo();

    /**
     * @return PropertyInfo associated with the filter field
     */
    PropertyInfo getPropertyInfoForField();


    /**
     * @return List of Filter objects held in this container
     */
    List<Filter> getFilters();
    
    /**
     * This is a method used by the FilterHelper.  It adds the designated Filter object
     * to the SailPointObject, where it will ultimately get persisted
     * @param f the Filter to persist
     */
    void addFilter(Filter f);
    
    /**
     * The Filter.BooleanOperation corresponding to the CompositeFilter into which the selected
     * Filters will be grouped
     * @return String representation of a Filter.BooleanOperation 
     */
    String getGlobalBooleanOp();
    /**
     * Set the Filter.BooleanOperation corresponding to the CompositeFilter into which the selected
     * Filters will be grouped
     * @param operation String representation of a Filter.BooleanOperation 
     */
    void setGlobalBooleanOp(String operation);

    /**
     * @return List of properties available as filters.  The value is a user-friendly name and the label is 
     * the raw propertyName 
     */
    List<SelectItem> getAvailableItemNames();
    
    /**
     * @return the property name of the LeafFilter that is being added
     */
    String getFilterField();

    /**
     * Sets the property name of the LeafFilter that is being added
     * @param fieldName property name
     */
    void setFilterField(String fieldName);
    
    void setFilterString(String filterString);
    
    String getFilterString();
    
    /**
     * The Filter.LogicalOperation corresponding to the LeafFilter that is being added
     * @return String representation of a Filter.LogicalOperation 
     */
    String getFilterLogicalOp();
    
    /**
     * Set the Filter.LogicalOperation corresponding to the LeafFilter that is being added
     * @param operation String representation of a Filter.LogicalOperation 
     */
    void setFilterLogicalOp(String operation);
    
    /**
     * This setting specifies the detailed behavior of 'Like' Filters.  Examples include 
     * 'starts with,' 'anywhere,' and 'ends with'
     * @return String representation of a Filter.MatchMode
     */
    String getFilterMatchMode();
    
    /**
     * Specify the detailed behavior of 'Like' Filters
     * @param matchMode String representation of a Filter.MatchMode 
     */
    void setFilterMatchMode(String matchMode);
    
    /**
     * @return true if the page has undergone initialization already; false otherwise
     */
    String getInitializationComplete();

    /**
     * @param initializationComplete true if the page has undergone initialization already; false otherwise
     */
    void setInitializationComplete(String initializationComplete);
    
    /**
     * Used by the addFilters.xhtml page to determine whether to present a text box 
     * to the user for them to enter text for the search item
     */
    boolean isTextFilter();

    /**
     * Used by the addFilters.xhtml page to determine whether to present a list box 
     * to the user for them to enter mutiple text values for the search item
     */
    boolean isMultiFilter();
    
    /**
     * Used by the addFilters.xhtml page to determine whether to present a true/false
     * box to the user for them to enter text for the search item
     */
    boolean isBoolFilter();
    
    boolean isIntegerFilter();
    
    boolean isEntitlementFilter();
    
    boolean isEntitlementMultiFilter();
    
    String getEntitlementFilterValue();
    
    void setEntitlementFilterValue(String entitlementFilterValue);
    
    String getEntitlementMultiFilterValue();
    
    void setEntitlementMultiFilterValue(String entitlementMultiFilterValue);
    
    /**
     * This is the value against which the property will be matched.  
     * The value may not apply to some LogicalOperations (ISNULL, for instance).
     * In the case of multi-valued operations (CONTAINS_ALL, for example) this 
     * will be a CSV 
     * @return String representing the value(s) against which the property will be matched
     */
    String getFilterValue();   
    /**
     * Set the value against which the property will be matched.  
     * The value may not apply to some LogicalOperations (ISNULL, for instance).
     * @value String representing the value(s) against which the property will be matched
     */ 
    void setFilterValue(String value);
    
    String getIntegerFilterValue();
    
    void setIntegerFilterValue(String value);
    
    /**
     * This is the value against which the property will be matched.  
     * The value only appies field whose operation is of type boolean.
     * @return String representing the boolean value against which the property will be matched
     */
    String getBooleanFilterValue();
    
    /**
     * Set the value against which the property will be matched.  
     * The value only appies field whose operation is of type boolean.
     * @return String representing the boolean value against which the property will be matched
     */ 
    void setBooleanFilterValue(String value);
    
    /**
     * This is the value against which the property will be matched.
     * This value is only for use in multi-valued operations (CONTAINS_ALL, for example)
     * @return String representing the values against which the property will be matched.
     * The values are delimited by newline characters
     */
    String getFilterListValue();
    
    /**
     * This is the value against which the property will be matched.
     * This value is only for use in multi-valued operations (CONTAINS_ALL, for example)
     * @param String representing the values against which the property will be matched.
     * The values are delimited by newline characters
     */
    void setFilterListValue(String value);

    /**
     * If date functionality is enabled, this value specifies whether to
     * show the Faces calendar picker for date filters.
     *
     * @return Boolean indicating whether to show calendar picker.
     */
    default Boolean getEnableCalendar() {
        return false;
    }

    /**
     * If date functionality is enabled, this sets the value for whether
     * to show the Faces calendar picker for date filters.
     *
     * @param enableCalendar True if calendar picker should be shown.
     */
    default void setEnableCalendar(Boolean enableCalendar) {

    }

    /**
     * @return boolean indicating whether or not the Filter should be case-sensitive when 
     * doing matches
     */
    boolean isFilterIgnoreCase();
    /**
     * @param isIgnoreCase boolean indicating whether or not the Filter should be case-sensitive when 
     * doing matches
     */
    void setFilterIgnoreCase(boolean isIgnoreCase);
    
    boolean isCompilationError();
    
    void setCompilationError(boolean compilationError);

    /**
     * @return List of SelectItems.  The labels are internationalized strings corresponding 
     * to the available Filter.LogicalOperations.  The values are the Filter.LogicalOperations 
     * that are available for the given property
     */
    List<SelectItem> getInputTypeChoices();
    
    /**
     * @return Map of value/label pairs representing the possible Filter.MatchModes that 
     * apply to Filters with 'Like' operations 
     */
    Map<String, String> getMatchModeValues();


    /**
     * @return The number of Filters that are currently applied
     */
    int getFilterCount();

    /**
     * @return List of FilterSelectBeans representing the Filters that are currently being
     * applied
     */
    List<FilterSelectBean> getFilterBeans();

    /**
     * Set the List of FilterSelectBeans representing the Filters that are currently being
     * applied 
     * @param filterBeans List of FilterSelectBeans representing the Filters that are currently 
     * being applied
     */
    void setFilterBeans(List<FilterSelectBean> filterBeans);
    
    boolean isShowFilterSource();
    
    void setShowFilterSource(boolean show);
    
    /**
     * @return Current FacesContext
     */
    FacesContext getFacesContext();

    /**
     * Action that adds the new Filter to the set of Filters that is being edited
     * @return The outcome of the newFilter action.  This may vary from page to page,
     * depending on the navigations rules for that page
     */
    String newFilter();

    /**
     * Action that synchronizes the available operations and MatchModes with a newly changed
     * property name or operation
     * @return The outcome of the refreshOperationsAction action.  This may vary from page to page,
     * depending on the navigations rules for that page
     */
    String refreshOperationsAction();
    
    /**
     * Action that combines the selected LeafFilters into a CompositeFilter, as designated by 
     * the globalBooleanOp
     * @return The outcome of the groupFilters action.  This may vary from page to page,
     * depending on the navigations rules for that page
     */
    String groupFilters();

    /**
     * Action that deletes the selected CompositeFilter and splits its children off into multiple 
     * LeafFilters
     * @return The outcome of the ungroupFilters action.  This may vary from page to page,
     * depending on the navigations rules for that page
     */
    String ungroupFilters();

    /**
     * Action that deletes the selected Filters
     * @return The outcome of the removeFilters action.  This may vary from page to page,
     * depending on the navigations rules for that page
     */
    String removeFilters();
    
    /**
     * Action that allows a user to edit the source of the currently selected Filters
     * @return The outcome of the removeFilters action.  This may vary from page to page,
     * depending on the navigations rules for that page
     */
    String convertStringToFilters();
    
    /**
     * Action that checks to see if it can compile a given string into a filter
     * @return The outcome of the removeFilters action.  This may vary from page to page,
     * depending on the navigations rules for that page
     */
    String compileFilterString();
    
    /**
     * Because Ajax4jsf is unbelieveable evil and throws an IllegalStateException every
     * time I try to call FacesContext.addMessage(), I'm going to keep a variable that contains
     * any error I need to write to the page and just render it when it's not null.
     * 
     */
    String getFilterError();
    
    String getApplicationId();
}   
