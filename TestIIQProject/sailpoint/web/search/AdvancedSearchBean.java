/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * 
 */
package sailpoint.web.search;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;

import javax.faces.context.FacesContext;
import javax.faces.model.SelectItem;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.ValidationException;
import sailpoint.object.Filter;
import sailpoint.object.Filter.BooleanOperation;
import sailpoint.object.Filter.CompositeFilter;
import sailpoint.object.Filter.LogicalOperation;
import sailpoint.object.Filter.MatchMode;
import sailpoint.object.PropertyInfo;
import sailpoint.object.SearchInputDefinition;
import sailpoint.object.SearchInputDefinition.InputType;
import sailpoint.object.SearchInputDefinition.PropertyType;
import sailpoint.object.SearchItem;
import sailpoint.object.SearchItemFilter;
import sailpoint.search.SelectItemComparator;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.BaseDTO;
import sailpoint.web.FilterContainer;
import sailpoint.web.FilterSelectBean;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.util.FilterConverter;
import sailpoint.web.util.FilterHelper;

/**
 * @author peter.holcomb
 *
 */
public class AdvancedSearchBean extends BaseDTO implements FilterContainer {

    private static final Log log = LogFactory.getLog(AdvancedSearchBean.class);
    private static final String NEW_LINE = System.getProperty("line.separator");
    private static final String DEFAULT_SESSION_KEY = "AdvancedSearchBeanDefaultKey";
    
    private String searchType;
    private SearchItem searchItem;
    private String sessionKey;
    private List<FilterSelectBean> filterBeans;
    private String filterField;
    private String filterValue;
    private String integerFilterValue;
    private String booleanFilterValue;
    private Date dateFilterValue;
    private String entitlementFilterValue;
    private String entitlementMultiFilterValue;
    private String filterLogicalOp;
    private String filterMatchMode;
    private String filterString;
    private boolean showFilterSource;
    private String filterError;
    private boolean compilationError;
    private List<String> filterListValue;
    private String initializationComplete;

    private boolean enableCalendar;
    private boolean excludeDateEquals;

    List<SelectItem> inputTypeChoices;
    Map<String, SearchInputDefinition> inputs;
    
    // this is the list of search fields that will not be displayed in
        // Identity Analyzer
    private static List<String> modelingSearchFields = new ArrayList<String>();
    static {
        modelingSearchFields.add("businessRole");
        modelingSearchFields.add("businessProcess");
        modelingSearchFields.add("advanced.businessRole");
        modelingSearchFields.add("advanced.businessProcess");
    }

    /** The match mode (such as 'AND' or 'OR') for the entire query.  If there are
     * more than one filter submitted through the advanced search, they will all be 
     * joined together using either an AND or an OR. */
    private String globalBooleanOp;

    private Map<String, PropertyInfo> propertyInfoMap;

    public AdvancedSearchBean() {
        this.initializationComplete = "false";
        this.searchItem = new SearchItem();
        this.searchType = IdentitySearchBean.ATT_SEARCH_TYPE_ADVANCED_IDENT;
        this.sessionKey = DEFAULT_SESSION_KEY;
    }

    public AdvancedSearchBean (SearchItem item, Map<String, SearchInputDefinition> inputs, 
    		String searchType, String sessionKey) {
        this.initializationComplete = "false";
    	this.searchType = searchType;
        this.inputs = inputs;
        this.searchItem = item;
        this.sessionKey = sessionKey;
        if (getAvailableItemNames().size() > 0)
            setFilterField((String)getAvailableItemNames().get(0).getValue());
        
        /** Load the SearchItemFilters that are serialized on the search item
         * and build the filter beans for displaying these on the ui 
         */
        buildFilterBeans(searchItem);
        
    }
    
    public String getInitializationComplete() {
        return initializationComplete;
    }
    
    public void setInitializationComplete(String initializationComplete) {
        this.initializationComplete = initializationComplete;
    }
    
    public String getBooleanFilterValue() {
        return booleanFilterValue;
    }

    public void setBooleanFilterValue(String booleanFilterValue) {
        this.booleanFilterValue = booleanFilterValue;
    }

    public String getIntegerFilterValue() {
        return integerFilterValue;
    }

    public void setIntegerFilterValue(String integerFilterValue) {
        this.integerFilterValue = integerFilterValue;
    }

    public Date getDateFilterValue() {
        if (dateFilterValue == null) {
            Calendar cal = Calendar.getInstance();
            dateFilterValue = cal.getTime();
        }
        return dateFilterValue;
    }

    public void setDateFilterValue(Date dateFilterValue) {
        this.dateFilterValue = dateFilterValue;
    }
    
    //////////////////////////////////////////////////////////////////////
    //
    // Actions
    //
    //////////////////////////////////////////////////////////////////////

    public String removeFilters() {
        FilterHelper.removeFilters(this);
        saveFilterBeans(searchItem);
        getSessionScope().put(getSessionKey(), searchItem);
        return "filtersRemoved";
    }

    public String groupFilters() {
        FilterHelper.groupFilters(this);
        saveFilterBeans(searchItem);
        getSessionScope().put(getSessionKey(), searchItem);
        return "filtersGrouped";
    }

    public String ungroupFilters() {
        FilterHelper.ungroupFilters(this);
        saveFilterBeans(searchItem);
        getSessionScope().put(getSessionKey(), searchItem);
        return "filtersUngrouped";
    }

    public String convertStringToFilters() {
        
        //defect21432 we need to clear out the filterBeans when we move from the source display to filters
        filterBeans = new ArrayList<FilterSelectBean>();    	
        String result = FilterHelper.convertStringToFilters(this);        
        
        /**If the filters didn't compile correctly, we need to flag with an error message **/
        setCompilationError(result==null);
        setShowFilterSource(result==null);
        saveFilterBeans(searchItem);
        getSessionScope().put(getSessionKey(), searchItem);
        return "convertedStringToFilter";
    }
    
    public String compileFilterString() {
        Filter filter = FilterHelper.compileFilterFromString(this);
        setCompilationError(filter==null);
        saveFilterBeans(searchItem);
        getSessionScope().put(getSessionKey(), searchItem);
        return "filterCompiled";
    }
    
    public String showSource() {
        return FilterHelper.showSource(this); 
    }
    
    public String hideSource() {
        //defect21432 need to save the searchItem when we hideSource - this is the cancel button
        saveFilterBeans(searchItem);
        getSessionScope().put(getSessionKey(), searchItem);
        return FilterHelper.hideSource(this); 
    }
    
    public String refreshOperationsAction() {
        PropertyInfo input = getPropertyInfoForField();

        if (!isAllowedOperation()){
            filterLogicalOp = null;
        }

        // If the operation is null, use the first available operation in the list
        // for the current property. This assures that when it is displayed the UI will
        // render the form inputs correctly. 
        if (!hasOperation()){
            if (input.getAllowedOperations() != null &&
                    input.getAllowedOperations().size() > 0)
                filterLogicalOp = input.getAllowedOperations().get(0).toString();
        }

        //defect 21432 Need to save the searchItem when we make changes
        FilterHelper.refreshFilterAction(this);
        saveFilterBeans(searchItem);

        if (filterLogicalOp != null &&
                (LogicalOperation.valueOf(filterLogicalOp) == LogicalOperation.ISNULL ||
                 LogicalOperation.valueOf(filterLogicalOp) == LogicalOperation.NOTNULL)) {
            filterValue = "";
        }

        getSessionScope().put(getSessionKey(), searchItem);

        return "refreshOperations"; 
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Helpers
    //
    //////////////////////////////////////////////////////////////////////

    @SuppressWarnings("unchecked")
    public String newFilter() {
        Filter filter = null;
        filterError = null;
        
        /** Get the input definition from the input map that was entered by the user
         */
        Set<String> keys = inputs.keySet();
        for(String key : keys) {
            SearchInputDefinition input = (SearchInputDefinition) inputs.get(key);
            if(filterField.equals(input.getName())){

                InputType inputType = null;
                
                /* Set Logical Operation from input */
                Filter.LogicalOperation logicalOp;
                if (filterLogicalOp == null)
                    logicalOp = null;
                else
                    logicalOp = Enum.valueOf(Filter.LogicalOperation.class, filterLogicalOp);
                log.debug("Logical op is " + logicalOp);
                if (logicalOp != null) {
                    inputType = SearchInputDefinition.getInputType(logicalOp);
                }

                /* Set Input Type from input */
                log.debug("Setting inputType to " + inputType);
                input.setInputType(inputType);

                /* Set Match Mode */
                Filter.MatchMode matchMode = null;
                if (logicalOp == Filter.LogicalOperation.LIKE && filterMatchMode != null)
                    matchMode = Enum.valueOf(Filter.MatchMode.class, filterMatchMode);
                input.setMatchMode(matchMode);
                Object filterValueToSet;
                
                /* Set Value */
                if (logicalOp == LogicalOperation.IN || logicalOp == LogicalOperation.CONTAINS_ALL) {
                    if (filterListValue == null || filterListValue.isEmpty()) {
                        fixtheListValueIfNeeded();
                    }
                	filterValueToSet = filterListValue;
                    input.setValue(filterListValue);
                    input.setPropertyType(SearchInputDefinition.PropertyType.StringList);
                } else if (input.getPropertyType() == PropertyType.Boolean) {
                    filterValueToSet = booleanFilterValue;
                    input.setValue(filterValueToSet);
                } else if (input.getPropertyType() == PropertyType.Integer) {
                    filterValueToSet = integerFilterValue;
                    input.setValue(filterValueToSet);
                } else if (enableCalendar && input.getPropertyType() == PropertyType.Date) {
                    filterValueToSet = dateFilterValue;
                    input.setPropertyType(SearchInputDefinition.PropertyType.Date);

                    if (logicalOp == LogicalOperation.ISNULL || logicalOp == LogicalOperation.NOTNULL) {
                        filterValueToSet = "";
                    } else {
                        input.setValue(filterValueToSet);
                    }
                } else {
                	filterValueToSet = filterValue;
                    input.setValue(filterValue);
                }

                log.debug("filterValue was " + filterValue + ".  Set input value to " + input.getValue());
                log.debug("[newFilter] input: " + input.getName() + " Value: " + input.getValue() + " Op: " + input.getInputTypeValue());

                try {
                	/* Create the actual filter */
                    filter = (Filter) input.getFilter(getContext());
                    log.debug("[newFilter] filter: " + filter);
                    
                    FilterSelectBean fBean = 
                    		new FilterSelectBean(filter, input.getDescription(), filterLogicalOp, filterValueToSet, getPropertyInfo(inputs));
                    if(filter!=null) {
                    	fBean.setJoin(input.getJoin(getContext()));
                        filterBeans.add(fBean);
                        /** Add the filters and save the item on the session so that it gets picked up
                         * on the next refresh
                         */
                        saveFilterBeans(searchItem);
                        getSessionScope().put(getSessionKey(), searchItem);
                    }
                    log.debug("[newFilter] fBean: " + fBean + " filter: " + filter);                    
                } catch (ValidationException ve) {
                    log(input, ve);
                    Message msg = ve.getMessageInstance();
                    filterError = msg.getLocalizedMessage(getLocale(), getUserTimeZone());
                } catch (GeneralException ge) {
                    log(input, ge);
                    Message msg = new Message(MessageKeys.FILTER_ERROR);
                    filterError = msg.getLocalizedMessage(getLocale(), getUserTimeZone());
                }
                break;

            }
        }

        /**Reset the filterBeans so that the ajax refresh will force the page
         * to get the new filter off of the session */
        setFilterString(null);
        //filterBeans=null;
        //log.debug("Filters: " + getFilters());
        return "filtersAdded";
    }

    /**Takes a list of SearchItemFilter objects and builds a list of user-friendly filter beans.
     * The end result of this method should be a list of filter select beans that have
     * user-friendly property names. **/
    public void buildFilterBeans(SearchItem searchItem) {    	
        if(searchItem!=null && searchItem.getSearchFilters()!=null) {
            filterBeans = new ArrayList<FilterSelectBean>();
            for(SearchItemFilter searchFilter : searchItem.getSearchFilters()){
                
            	FilterSelectBean filterBean = new FilterSelectBean(searchFilter, getPropertyInfo(inputs));
                filterBeans.add(filterBean);
            }
            if(!filterBeans.isEmpty()) {
                filterBeans.get(0).setFirstElement(true);
            }
        } else {
            filterBeans = new ArrayList<FilterSelectBean>();
        }
    }
    
    /** Takes the list of save filter beans and converts them into SearchItemFilters
     * to be saved */
    public void saveFilterBeans(SearchItem searchItem) {    
        if(searchItem==null) 
            searchItem = new SearchItem();
        
        searchItem.setOperation(getGlobalBooleanOp());
        
    	if(filterBeans!=null) {    		
    		
    		
    		searchItem.setSearchFilters(null);
    		for(FilterSelectBean bean : filterBeans){
    			searchItem.addSearchFilter(new SearchItemFilter(bean));
    		}
    	}
    }
    //////////////////////////////////////////////////////////////////////
    //
    // Getters/Setters
    //
    //////////////////////////////////////////////////////////////////////


    public List<SelectItem> getAvailableItemNames() {
        return getSearchFieldList();
    }

    private List<SelectItem> getSearchFieldList() {
    	
        List<SelectItem> searchFieldList = null;
        
        if(inputs != null) {
            searchFieldList = new ArrayList<SelectItem>();
            Set<String> keys = inputs.keySet();
            for(String key : keys) {
                SearchInputDefinition input = (SearchInputDefinition) inputs.get(key);

                boolean isAdvancedIdentity = (searchType.equals(SearchBean.ATT_SEARCH_TYPE_ADVANCED_IDENT) &&
                        (input.getSearchType().equals(SearchBean.ATT_SEARCH_TYPE_ADVANCED_IDENT) ||
                                input.getSearchType().equals(SearchBean.ATT_SEARCH_TYPE_RISK)||
                                input.getSearchType().equals(SearchBean.ATT_SEARCH_TYPE_EXTENDED_LINK_IDENT)||
                                input.getSearchType().equals(SearchBean.ATT_SEARCH_TYPE_EXTERNAL_LINK)||
                                input.getSearchType().equals(SearchBean.ATT_SEARCH_TYPE_EXTENDED_MANAGED_ATTRIBUTE)||
                                input.getSearchType().equals(SearchBean.ATT_SEARCH_TYPE_EXTENDED_IDENT)));
                boolean isAdvancedActivity = (searchType.equals(SearchBean.ATT_SEARCH_TYPE_ADVANCED_ACT) &&
                        (input.getSearchType().equals(SearchBean.ATT_SEARCH_TYPE_ADVANCED_ACT)));
                boolean isAdvancedCert = (searchType.equals(SearchBean.ATT_SEARCH_TYPE_ADVANCED_CERT) &&
                        (input.getSearchType().equals(SearchBean.ATT_SEARCH_TYPE_ADVANCED_CERT)));
                boolean isAdvancedRole = (searchType.equals(SearchBean.ATT_SEARCH_TYPE_ADVANCED_ROLE) &&
                        (input.getSearchType().equals(SearchBean.ATT_SEARCH_TYPE_ADVANCED_ROLE)));
                boolean isAdvancedAccountGroup = (searchType.equals(SearchBean.ATT_SEARCH_TYPE_ADVANCED_ACCOUNT_GROUP) &&
                        (input.getSearchType().equals(SearchBean.ATT_SEARCH_TYPE_ADVANCED_ACCOUNT_GROUP) ||
                        input.getSearchType().equals(SearchBean.ATT_SEARCH_TYPE_EXTENDED_MANAGED_ATTRIBUTE)));
                boolean isAdvancedAudit = (searchType.equals(SearchBean.ATT_SEARCH_TYPE_ADVANCED_AUDIT) &&
                        (input.getSearchType().equals(SearchBean.ATT_SEARCH_TYPE_ADVANCED_AUDIT)));
                boolean isAdvancedIdentityRequest = (searchType.equals(SearchBean.ATT_SEARCH_TYPE_ADVANCED_IDENTITY_REQUEST) &&
                        (input.getSearchType().equals(SearchBean.ATT_SEARCH_TYPE_ADVANCED_IDENTITY_REQUEST) ||
                        input.getSearchType().equals(SearchBean.ATT_SEARCH_TYPE_IDENTITY_REQUEST_ITEM)));
                boolean isAdvancedSyslog = (searchType.equals(SearchBean.ATT_SEARCH_TYPE_ADVANCED_SYSLOG) &&
                        input.getSearchType().equals(SearchBean.ATT_SEARCH_TYPE_ADVANCED_SYSLOG));
                boolean isAdvancedLink = (searchType.equals(SearchBean.ATT_SEARCH_TYPE_ADVANCED_LINK) &&
                        (input.getSearchType().equals(SearchBean.ATT_SEARCH_TYPE_LINK) ||
                                input.getSearchType().equals(SearchBean.ATT_SEARCH_TYPE_ADVANCED_LINK)));

                /* Search through Advanced Fields */
                if (isAdvancedIdentity || isAdvancedActivity || isAdvancedCert || isAdvancedRole || isAdvancedAccountGroup
                        || isAdvancedAudit || isAdvancedLink || isAdvancedIdentityRequest || isAdvancedSyslog) {

                    SelectItem item = new SelectItem(input.getName(), getMessage(input.getHeaderKey()));
                    boolean duplicate = false;
                    for(SelectItem selectItem : searchFieldList) {
                        if(selectItem.getLabel().equals(item.getLabel())) {
                            duplicate = true;
                            break;
                        }
                    }

                    if(!duplicate) {
                        searchFieldList.add(item);
                    }
                    //log.debug("[getSearchFieldList] Item: " + input.getName() + " Label: " + input.getDescription());
                }
            }

            // Sort the list based on localized labels
            Collections.sort(searchFieldList, new SelectItemComparator(getLocale()));
        }

        return searchFieldList;
    }

    /**
     * @return the inputTypeChoices
     */
    public List<SelectItem> getInputTypeChoices() {
        if (inputTypeChoices == null) {
            inputTypeChoices = new ArrayList<SelectItem>();
        } else {
            inputTypeChoices.clear();
        }
        
        // The following hack requires some explanation:  our type choices are exposed as select items,
        // and we have to swap them out depending on the currently selected field option.  JSF isn't happy
        // about components being swapped out from under it, and it gets confused when it tries to validate
        // submissions that were not part of the original select item list.  In order to work around this, 
        // we return all possible options when the page is first initialized so that we can get a select item
        // list that properly validates against all possible submissions.  Once this initialization is complete
        // the browser will request the "real" input type choices that are appropriate to the selected field.
        // we use the initialization field's value to determine whether or not we're initializing
        if (Boolean.FALSE == Boolean.parseBoolean(initializationComplete)) {
            Filter.LogicalOperation [] allOps = Filter.LogicalOperation.values();
            for (int i = 0; i < allOps.length; ++i) {
                String operationValue = allOps[i].toString();
                String operationLabel = getMessage("filter_op_" + operationValue);
                inputTypeChoices.add(new SelectItem(operationValue, operationLabel));
            }
        } else {
            PropertyInfo propertyInfoForField = getPropertyInfoForField();
    
            if (propertyInfoForField != null && propertyInfoForField.getAllowedOperations() != null)                    
            {   
                for (Filter.LogicalOperation operation : propertyInfoForField.getAllowedOperations()) {
                    // Exclude Equals for Date filters if the excludeDateEquals flag is true.
                    if (!excludeDateEquals || operation != Filter.LogicalOperation.EQ || !isDateFilter()) {
                        String operationValue = operation.toString();
                        String operationLabel = getMessage("filter_op_" + operationValue);
                        inputTypeChoices.add(new SelectItem(operationValue, operationLabel));
                    }
                }
            }  
    
            if (inputTypeChoices.isEmpty()){
                String noOperationAvailable = getMessage(MessageKeys.FILTER_OP_UNAVAILABLE);
                inputTypeChoices.add(new SelectItem("", noOperationAvailable));                            
            }  
        }
        
        return inputTypeChoices;
    }

    public void setInputTypeChoices(List<SelectItem> choices) {
        inputTypeChoices = choices;
    }

    /**
     * @return the filterField
     */
    public String getFilterField() {
        return filterField;
    }

    /**
     * @param filterField the filterField to set
     */
    public void setFilterField(String filterField) {
        this.filterField = filterField;
    }

    /**
     * @return the filterLogicalOp
     */
    public String getFilterLogicalOp() {
        //log.debug("[getFilterLogicalOp] " + filterLogicalOp);
        return filterLogicalOp;
    }

    /**
     * @param filterLogicalOp the filterLogicalOp to set
     */
    public void setFilterLogicalOp(String filterLogicalOp) {
        this.filterLogicalOp = filterLogicalOp;
    }

    public String getFilterMatchMode() {
        return filterMatchMode;
    }

    public void setFilterMatchMode(String filterMatchMode) {
        this.filterMatchMode = filterMatchMode;
    }

    /**
     * @return the filterValue
     */
    public String getFilterValue() {
        return filterValue;
    }
    
    public boolean isTextFilter() {
        if(filterField != null && !isMultiFilter()) {
            SearchInputDefinition def = inputs.get(filterField);
            if(def!=null) {
                if (!def.getPropertyType().equals(PropertyType.Boolean)
                        && !def.getPropertyType().equals(PropertyType.Integer)
                        && (!this.enableCalendar || !def.getPropertyType().equals(PropertyType.Date))) {
                    return true;
                }
            }            
        }
        return false;
    }
    
    public boolean isIntegerFilter() {
        if(!Util.isNullOrEmpty(filterField) && !isMultiFilter()) {
            SearchInputDefinition def = inputs.get(filterField);
            if(def!=null) {
               if(def.getPropertyType().equals(PropertyType.Integer)) {
                   return true;
               }
            }            
        }
        return false;
    }

    public boolean isDateFilter() {
        if (filterField != null && !isMultiFilter()) {
            SearchInputDefinition def = inputs.get(filterField);
            if (def != null) {
                return def.getPropertyType().equals(PropertyType.Date);
            }
        }

        return false;
    }

    // Calendar getters/setters overridden from FilterContainer interface.
    // For Advanced Search, we support showing the Faces calendar picker and
    // Date values natively.
    public Boolean getEnableCalendar() {
        return this.enableCalendar;
    }

    public void setEnableCalendar(Boolean enableCalendar) {
        this.enableCalendar = enableCalendar != null ? enableCalendar : false;
    }

    // Flag to determine whether the Equals operator should be shown for
    // datetime filters. Most advanced search types with datetime filters
    // have Equals disabled, as searching for a specific date & time is usually
    // not useful.
    public boolean getExcludeDateEquals() {
        return this.excludeDateEquals;
    }

    public void setExcludeDateEquals(boolean excludeDateEquals) {
        this.excludeDateEquals = excludeDateEquals;
    }
    
    public boolean isMultiFilter() {
        if(!Util.isNullOrEmpty(filterField)) {
            SearchInputDefinition def = inputs.get(filterField);
            if(def!=null) {
               if(def.getPropertyType().equals(PropertyType.AndedList) ||
            	  def.getPropertyType().equals(PropertyType.OrdList) ||
            	  def.getPropertyType().equals(PropertyType.StringList) ||
            	  def.getPropertyType().equals(PropertyType.TimePeriodList)) {
                   return true;
               }
            }         
        }
        
        if (!Util.isNullOrEmpty(filterLogicalOp) && (LogicalOperation.valueOf(filterLogicalOp) == LogicalOperation.IN || LogicalOperation.valueOf(filterLogicalOp) == LogicalOperation.CONTAINS_ALL)) {
            return true;
        }
        
        return false;
    }
    
    public boolean isBoolFilter() {
        if(filterField!=null) {
            SearchInputDefinition def = inputs.get(filterField);
            if(def!=null) {
               if(def.getPropertyType().equals(PropertyType.Boolean)) {
                   return true;
               }
            }            
        }
        return false;
    }

    public boolean isEntitlementFilter() {
    	return false;
    }
    
    public boolean isEntitlementMultiFilter() {
    	return false;
    }
    
    public String getEntitlementFilterValue() {
    	return entitlementFilterValue;
    }
    
    public void setEntitlementFilterValue(String entitlementFilterValue) {
    	this.entitlementFilterValue = entitlementFilterValue;
    }
    
    public String getEntitlementMultiFilterValue() {
    	return entitlementMultiFilterValue;
    }
    
    public void setEntitlementMultiFilterValue(String entitlementMultiFilterValue) {
    	this.entitlementMultiFilterValue = entitlementMultiFilterValue;
    }
    
    /**
     * @param filterValue the filterValue to set
     */
    public void setFilterValue(String filterValue) {
        this.filterValue = filterValue;
    }
    
    public void setFilterListValue(String filterListValue) {
		if (this.filterListValue == null) {
			this.filterListValue = new ArrayList<String>();
		} else {
	    	StringTokenizer tokenizer = new StringTokenizer(filterListValue, NEW_LINE);
			this.filterListValue.clear();
	    	while (tokenizer.hasMoreTokens()) {
	    		String listItem = tokenizer.nextToken().trim();
	    		if (Util.isNotNullOrEmpty(listItem)) {
                    this.filterListValue.add(listItem);
                }
	    	}
		}
    }
    
    public String getFilterListValue() {
    	StringBuffer listValue = new StringBuffer();
    	if (filterListValue != null && !filterListValue.isEmpty()) {
    		for (String listElement: filterListValue) {
    			listValue.append(listElement);
    			listValue.append(NEW_LINE);
    		}
    	}
    	
    	return listValue.toString();
    }

    @Deprecated
    public boolean isFilterIgnoreCase() {
        return false;
    }

    @Deprecated
    public void setFilterIgnoreCase(boolean filterIgnoreCase) {
        log.warn("Advanced Search no longer supports Ignore Case, and cannot be set.");
    }

    /**
     * @return the filterBeans
     */
    public List<FilterSelectBean> getFilterBeans() {
        if(filterBeans != null && !filterBeans.isEmpty()) {
        	setFirstElement();
        }
        return filterBeans;
    }
    
    /** The UI needs to know which element is the first element of the array.
     * If there are more than one filter beans in the list, the UI will show a table
     * row with the option for choosing the boolean operation for the query.
     *
     */
    private void setFirstElement() {
    	for(int i=0; i<filterBeans.size(); i++) {
    		if(filterBeans.get(i).isDisplayable()) {
    			filterBeans.get(i).setFirstElement(true);
    			break;
    		}
    	}
    }

    /**
     * @param filterBeans the filterBeans to set
     */
    public void setFilterBeans(List<FilterSelectBean> filterBeans) {
        this.filterBeans = filterBeans;
    }

    /** Method that returns a string representation of the filterbeans for editing **/
    public String getFilterString() {
        if (Util.isNullOrEmpty(filterString) && getFilters() != null) {
            List<Filter> filters = FilterConverter.convertAndCloneFilters(getFilters(), getGlobalBooleanOp(), inputs);
            if (filters.size() > 1 || (filters.size() == 1 && getGlobalBooleanOp() == Filter.BooleanOperation.NOT.name())) {
                CompositeFilter newComposite =
                        new CompositeFilter(Enum.valueOf(Filter.BooleanOperation.class, getGlobalBooleanOp()), filters);
                filterString = newComposite.getExpression();
            }
            else if (!filters.isEmpty()) {
                filterString = filters.get(0).getExpression();
            }
        }
        return filterString;
    }

    public void setFilterString(String filterString) {
        this.filterString = filterString;
    }

    

    public boolean isLikeOperation() {
        boolean result;

        if (filterLogicalOp == null || filterLogicalOp.trim().length() == 0) {
            result = false;
        } else {
            try {
                result = Filter.LogicalOperation.LIKE == Enum.valueOf(Filter.LogicalOperation.class, filterLogicalOp);
            } catch (IllegalArgumentException e) {
                result = false;
            }
        }

        return result;
    } 

    private boolean isAllowedOperation(){
        // If the filter property has been changed and the current operation is no
        // longer valid for this then null it out. 
        PropertyInfo inputForField = getPropertyInfoForField();

        if (inputForField == null || inputForField.getAllowedOperations() == null)
            return false;

        return inputForField.getAllowedOperations().contains(Enum.valueOf(LogicalOperation.class, filterLogicalOp));
    }


    private boolean hasOperation(){
        return filterLogicalOp != null && !"".equals(filterLogicalOp);
    }    


    public Map<String, PropertyInfo> getPropertyInfo() {
        return getPropertyInfo(inputs);
    }

    @Override
    public PropertyInfo getPropertyInfoForField() {
        PropertyInfo inputForField = null;
        Set<String> keys = inputs.keySet();
        for(String key : keys) {
            SearchInputDefinition input = (SearchInputDefinition) inputs.get(key);
            if(filterField != null && filterField.equals(input.getName())) {
                inputForField = input.getPropertyInfo();
                break;
            }
        }

        return inputForField;
    }

    protected Map<String, PropertyInfo> getPropertyInfo(Map<String, SearchInputDefinition> inputs) {
        if (propertyInfoMap == null) {
            propertyInfoMap = new HashMap<String, PropertyInfo>();

            for (String inputKey : inputs.keySet()) {
                SearchInputDefinition input = inputs.get(inputKey);
                propertyInfoMap.put(input.getPropertyName(), input.getPropertyInfo());

                /** Also put on the map the nullPropertyName values so that we can convert
                 * back from filters that were created with the nullPropertyValue **/
                if(input.getNullPropertyName()!=null) {
                    propertyInfoMap.put(input.getNullPropertyName(), input.getPropertyInfo());
                }
            }
        }

        return propertyInfoMap;
    }
    /**
     * @return the globalBooleanOp
     */
    public String getGlobalBooleanOp() {
        //IIQSR-28 Adding empty globalBooleanOp check
        if(Util.isNullOrEmpty(globalBooleanOp)) {
            // There's no advantage to using the getter here. If a filter string
            // doesn't exist, simply return the default global boolean operator (AND)
            globalBooleanOp = FilterContainer.getBooleanOpFromFilterString(filterString);
        }
        return globalBooleanOp;
    }
    /**
     * @param globalBooleanOp the globalBooleanOp to set
     */
    public void setGlobalBooleanOp(String globalBooleanOp) {
        this.globalBooleanOp = globalBooleanOp;
    }

    public Map<String, String> getMatchModeValues() {
        Locale currentLocale = FacesContext.getCurrentInstance().getViewRoot().getLocale();
        Map<String, String> modeValues = FilterHelper.getMatchModeValues(currentLocale);
        
        /** Remove the "Exact" option since this is used only with likes **/
        Message mode = new Message("filter_match_mode_" + MatchMode.EXACT.toString());
        modeValues.remove(mode.getLocalizedMessage(getLocale(), null));
        return modeValues;
    }  // getMatchModeValues()

    /**
     * @return the searchType
     */
    public String getSearchType() {
        return searchType;
    }

    /**
     * @param searchType the searchType to set
     */
    public void setSearchType(String searchType) {
        this.searchType = searchType;
    }

    /**
     * @return the filters
     */
    public List<Filter> getFilters() {
        List<Filter> filters = null;
        if(filterBeans!=null && !filterBeans.isEmpty()) {
        	filters = new ArrayList<Filter>();
        	for(FilterSelectBean filterBean : filterBeans) {
        		filters.add(filterBean.getFilter());
        	}
        }
        return filters;
    }
    
    /** Takes the underlying filters and joins them into a composite filter if there are more than
     * one or returns a leaf filter if there is only one filter **/
    public Filter getFilter() {
    	Filter filter = null;
    	if(filterBeans!=null && !filterBeans.isEmpty()) {
    		if(filterBeans.size()>1) {
    			Filter.BooleanOperation op = Enum.valueOf(BooleanOperation.class, globalBooleanOp);
    			filter = new CompositeFilter(op, getFilters());
    		} else {
    			filter = filterBeans.get(0).getFilter();
    		}
    	}
    	return filter;
    }

    public int getFilterCount() {
        int i=0;
        if(getFilterBeans() != null) {
            for(FilterSelectBean bean : getFilterBeans()) {
                if(bean.isDisplayable())
                    i++;
            }
        }
        return i;
    }

    public void addFilter(Filter filter) {    }

    /**
     * @param filters the filters to set
     */
    public void setFilters(List<Filter> filters) {
        return;
    }

    public String getInstructions() {
        return getMessage(MessageKeys.INST_ADVANCED_SEARCH_FILTERS);
    }

    /**
     * @return the compilationError
     */
    public boolean isCompilationError() {
        return compilationError;
    }

    /**
     * @param compilationError the compilationError to set
     */
    public void setCompilationError(boolean compilationError) {
        this.compilationError = compilationError;
    }

    /**
     * @return the showFilterSource
     */
    public boolean isShowFilterSource() {
        return showFilterSource;
    }

    /**
     * @param showFilterSource the showFilterSource to set
     */
    public void setShowFilterSource(boolean showFilterSource) {
        this.showFilterSource = showFilterSource;
    }

    /**
     * @return the filterError
     */
    public String getFilterError() {
        return filterError;
    }
    
	public SearchItem getSearchItem() {
		return searchItem;
	}

	public void setSearchItem(SearchItem searchItem) {
		this.searchItem = searchItem;
	}

	public String getSessionKey() {
		return sessionKey;
	}

	public void setSessionKey(String sessionKey) {
		this.sessionKey = sessionKey;
	}
	
	public Map<String, SearchInputDefinition> getInputs() {
        return inputs;
    }

    public void setInputs(Map<String, SearchInputDefinition> inputs) {
        this.inputs = inputs;
    }


    public FacesContext getFacesContext() {
        return FacesContext.getCurrentInstance();
    }
    
    public String getApplicationId() {
    	return null;
    }
    
    private void fixtheListValueIfNeeded() {
        // This hack double checks to be sure the filter list was set correctly.
        String listVal = null;

        String searchType = this.searchType;
        if (searchType.startsWith("Advanced")) {
            searchType = "advanced" + searchType.substring(8);
        }

        String formName = searchType + "SearchForm";
        String filterListValue = "filterListValue";
        Map<String, String> params = getRequestParameters();
        for (String key : params.keySet()) {
            if (key.startsWith(formName) && key.endsWith(filterListValue)) {
                listVal = params.get(key);
                break;
            }
        }

        if (listVal != null && listVal.trim().length() > 0) {
            setFilterListValue(listVal);
        }
    }

    private void log(SearchInputDefinition input, GeneralException ge) {
        log.info("Unable to get filter for SearchInputDefinition def: " + input.getDescription() + 
                ". Exception: " + ge.getMessage());
    }
}
