package sailpoint.web.modeler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

import javax.faces.context.FacesContext;
import javax.faces.model.SelectItem;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.BaseAttributeDefinition;
import sailpoint.object.Filter;
import sailpoint.object.Filter.CompositeFilter;
import sailpoint.object.Filter.LeafFilter;
import sailpoint.object.Filter.LogicalOperation;
import sailpoint.object.Identity;
import sailpoint.object.ObjectAttribute;
import sailpoint.object.ObjectConfig;
import sailpoint.object.PropertyInfo;
import sailpoint.object.SearchInputDefinition;
import sailpoint.object.SearchInputDefinition.InputType;
import sailpoint.object.SearchInputDefinition.PropertyType;
import sailpoint.search.BaseFilterBuilder;
import sailpoint.search.ExternalAttributeFilterBuilder;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.web.BaseDTO;
import sailpoint.web.FilterContainer;
import sailpoint.web.FilterSelectBean;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.util.FilterHelper;
import sailpoint.web.util.SelectItemByLabelComparator;

public class IdentityFilterConstraints extends BaseDTO implements FilterContainer {
    private static final long serialVersionUID = -22001726533824839L;

    private static final Log log = LogFactory.getLog(IdentityFilterConstraints.class);
    
    private static final String NEW_LINE = System.getProperty("line.separator");
    
    private List<ObjectAttribute> identityAttributeDefinitions;
    
    private String filterField;

    private String filterValue;
    
    private String booleanFilterValue;
    
    private String entitlementFilterValue;
    
    private String entitlementMultiFilterValue;
    
    private List<String> filterListValue;

    private boolean filterIgnoreCase;

    private String filterLogicalOp;

    private String filterMatchMode;

    private String filterString;

    private String filterError;

    private boolean compilationError;

    private boolean showFilterSource;
    
    private String initializationComplete;
    
    /**
     * The match mode (such as 'AND' or 'OR') for the entire query. If there
     * are more than one filter submitted through the advanced search, they
     * will all be joined together using either an AND or an OR.
     */
    private String globalBooleanOp;

    private List<SelectItem> inputTypeChoices;
    
    private List<Filter> filters;
    
    private List<FilterSelectBean> filterBeans;
    
    public IdentityFilterConstraints(List<Filter> currentFilters) {
        ObjectConfig identityConfig = ObjectConfig.getObjectConfig(Identity.class);
        identityAttributeDefinitions = identityConfig.getObjectAttributes();
        filters = currentFilters;
    }
    
    public String getInitializationComplete() {
        return initializationComplete;
    }
    
    public void setInitializationComplete(String initializationComplete) {
        this.initializationComplete = initializationComplete;
    }
    

    /* default implementation as this FilterContainer does not support integer fields 
     * @see sailpoint.web.FilterContainer#getIntegerFilterValue()
     */
    public String getIntegerFilterValue() {
        return "";
    }

    /* default implementation as this FilterContainer does not support integer fields 
     * @see sailpoint.web.FilterContainer#setIntegerFilterValue(java.lang.String)
     */
    public void setIntegerFilterValue(String integerFilterValue) {
    }

    public String getBooleanFilterValue() {
        return booleanFilterValue;
    }

    public void setBooleanFilterValue(String booleanFilterValue) {
        this.booleanFilterValue = booleanFilterValue;
    }
    
    /**
     * @return Map of identity attribute names
     */
    public List<SelectItem> getAvailableItemNames() {
        List<SelectItem> availableItemNames = new ArrayList<SelectItem>();

        
        for (BaseAttributeDefinition attribute : identityAttributeDefinitions) {
            availableItemNames.add(
                new SelectItem(attribute.getName(), attribute.getDisplayableName()));
        }

        Comparator<SelectItem> availableNamesComparator = new SelectItemByLabelComparator(getLocale());
        Collections.sort(availableItemNames, availableNamesComparator);

        return availableItemNames;
    }
    
    public boolean isAllowedOperation() {
        BaseAttributeDefinition attributeDef = getAttributeDefinition(getFilterField());

        if (attributeDef == null || attributeDef.getAllowedOperations() == null)
            return false;

        String filterLogicalOp = getFilterLogicalOp();
        if (filterLogicalOp == null || filterLogicalOp.trim().length() == 0)
            return false;

        return attributeDef.getAllowedOperations().contains(LogicalOperation.valueOf(filterLogicalOp));
    }

    @Override
    public PropertyInfo getPropertyInfoForField() {
        PropertyInfo inputForField = null;
        String filterField = getFilterField();

        if (filterField != null) {
            for (ObjectAttribute attributeDef : identityAttributeDefinitions) {
                if (attributeDef.getName().equals(filterField)) {
                    inputForField = new PropertyInfo();
                    inputForField.setAllowedOperations(attributeDef.getAllowedOperations());
                    inputForField.setDescription(attributeDef.getDescription());
                    inputForField.setValueType(attributeDef.getType());
                    break;
                }
            }
        }

        return inputForField;
    }
    
    public Map<String, PropertyInfo> getPropertyInfo() {
        Map<String, PropertyInfo> propertyInfoMap = new HashMap<String, PropertyInfo>();

        for (ObjectAttribute def : identityAttributeDefinitions) {
            PropertyInfo propertyInfo = new PropertyInfo();
            propertyInfo.setAllowedOperations(def.getAllowedOperations());
            propertyInfo.setDescription(def.getDisplayableName());
            propertyInfo.setValueType(def.getType());
            propertyInfoMap.put(def.getName(), propertyInfo);
            if (def.getPropertyType() == PropertyType.Identity) {
                propertyInfoMap.put(def.getName() + ".name", propertyInfo);
            }
            
        }

        log.debug("propertyInfoMap: " + propertyInfoMap.toString());
        
        return propertyInfoMap;
    }


    public String getFilterField() {
        // Provide a default
        if (filterField == null) {
            List<SelectItem> availableItemNames = getAvailableItemNames();
            if (availableItemNames != null && !availableItemNames.isEmpty()) {
                setFilterField((String) getAvailableItemNames().get(0).getValue());
                FilterHelper.refreshFilterAction(this);
            }
        }

        return filterField;
    }
    
    public void setFilterField(String fieldName) {
        filterField = fieldName;

        if (fieldName != null) {
            if (!isAllowedOperation()) {
                // Make sure we don't get an invalid op or the JSF SelectMenu
                // validator will blow chunks
                ObjectAttribute defForField = getAttributeDefinition(fieldName);

                if (defForField != null) {
                    List<LogicalOperation> allowedOps =
                        defForField.getAllowedOperations();

                    if (allowedOps != null && !allowedOps.isEmpty()) {
                        LogicalOperation defaultOp = allowedOps.get(0);
                        if (defaultOp != null) {
                            setFilterLogicalOp(defaultOp.toString());
                        }
                    }
                }
            }
        }
    }
    

    private ObjectAttribute getAttributeDefinition(String attributeName) {
        if (attributeName != null){
            for(ObjectAttribute def : identityAttributeDefinitions){
                if (def.getName().equals(attributeName)) {
                    return def;
                }
            }
        }
        return null;
    }

    public void addFilter(Filter f) {
        if (f != null) {
            if (filters == null) {
                filters = new ArrayList<Filter>();
            }
            
            filters.add(f);
        }        
    }

    public String compileFilterString() {
        Filter filter = FilterHelper.compileFilterFromString(this);
        setCompilationError(filter==null);
        return "filterCompiled";
    }

    public String convertStringToFilters() {
        String result = FilterHelper.convertStringToFilters(this);
        setCompilationError(result == null);
        setShowFilterSource(result == null);
        return "filtersGrouped";
    }

    public FacesContext getFacesContext() {
        return FacesContext.getCurrentInstance();
    }

    public List<FilterSelectBean> getFilterBeans() {
        String op = initFilters();
        if(op != null)
            setGlobalBooleanOp(op);

        if (filterBeans != null && !filterBeans.isEmpty()) {
            filterBeans.get(0).setFirstElement(true);
        }
        return filterBeans;
    }
    

    public void setFilterBeans(List<FilterSelectBean> filterBeans) {
        this.filterBeans = filterBeans;
    }

    public int getFilterCount() {
        final int count;
        List<FilterSelectBean> filterBeans = getFilterBeans();
        
        if (filterBeans == null)
            count = 0;
        else
            count = filterBeans.size();
        
        return count;
    }

    public String getFilterError() {
        return filterError;
    }

    public String getFilterLogicalOp() {
        return filterLogicalOp;
    }

    public void setFilterLogicalOp(String operation) {
        filterLogicalOp = operation;
    }

    public String getFilterMatchMode() {
        return filterMatchMode;
    }

    public void setFilterMatchMode(String matchMode) {
        this.filterMatchMode = matchMode;
    }

    public String getFilterString() {
//        log.debug("Filters: " + filters);
        if(null == filterString || filterString.trim().length() == 0) {
            if(filters != null) {
                if(filters.size() > 1) {
                    String booleanOp = getGlobalBooleanOp();
                    // Default the op to AND
                    if (null == booleanOp) {
                        booleanOp = Filter.BooleanOperation.AND.name();
                    }
                    CompositeFilter newComposite =
                        new CompositeFilter(Enum.valueOf(Filter.BooleanOperation.class, booleanOp), filters);
                    filterString = newComposite.getExpression();
                } else if(!filters.isEmpty()){
                    filterString =  filters.get(0).getExpression();
                }
            }
        }

        log.debug("Getting Filter String: " + filterString);
        return filterString;
    }
    
    public void setFilterString(String filterString) {
        log.debug("Setting Filter String: " + filterString);
        this.filterString = filterString;
    }

    public String getFilterValue() {
        if (filterValue == null)
            filterValue = "";

        return filterValue;
    }
    
    public void setFilterValue(String filterValue) {
        this.filterValue = filterValue;
    }
    
    public void setFilterListValue(String filterListValue) {
		if (this.filterListValue == null) {
			this.filterListValue = new ArrayList<String>();
		} else {
			this.filterListValue.clear();
		}
		
		if (filterListValue != null && filterListValue.trim().length() > 0) {
            StringTokenizer tokenizer = new StringTokenizer(filterListValue, NEW_LINE);
            while (tokenizer.hasMoreTokens()) {
                String listItem = tokenizer.nextToken();
                this.filterListValue.add(listItem);
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

    public List<Filter> getFilters() {
        if (filters == null)
            filters = new ArrayList<Filter>();
        return filters;
    }

    public String getGlobalBooleanOp() {
        return globalBooleanOp;
    }
    
    public void setGlobalBooleanOp(String operation) {
        globalBooleanOp = operation;
    }

    public List<SelectItem> getInputTypeChoices() {
        PropertyInfo propertyInfoForField = getPropertyInfoForField();

        if (inputTypeChoices == null) {
            inputTypeChoices = new ArrayList<SelectItem>();
        } else {
            inputTypeChoices.clear();
        }

        if (Boolean.TRUE.toString().equalsIgnoreCase(initializationComplete)) {
            if (propertyInfoForField != null &&
                propertyInfoForField.getAllowedOperations() != null) {
                for (Filter.LogicalOperation operation : propertyInfoForField.getAllowedOperations()) {
                    String operationValue = operation.toString();
                    String operationLabel =
                        getMessage("filter_op_" + operationValue);
                    inputTypeChoices.add(new SelectItem(operationValue, operationLabel));
                }
            }
    
            if (inputTypeChoices.isEmpty()) {
                String noOperationAvailable =
                    getMessage(MessageKeys.FILTER_OP_UNAVAILABLE);
                inputTypeChoices.add(new SelectItem("", noOperationAvailable));
            }
        } else {
            // Add them all
            LogicalOperation [] allOps = LogicalOperation.values();
            for (int i = 0; i < allOps.length; ++i) {
                LogicalOperation operation = allOps[i];
                String operationValue = operation.toString();
                String operationLabel =
                    getMessage("filter_op_" + operationValue);
                inputTypeChoices.add(new SelectItem(operationValue, operationLabel));
            }
        }
        

        return inputTypeChoices;
    }

    public String getInstructions() {
        return getMessage(MessageKeys.INST_PROFILE_FILTERS);
    }

    public Map<String, String> getMatchModeValues() {
        return FilterHelper.getMatchModeValues(getLocale());
    }

    public String groupFilters() {
        FilterHelper.groupFilters(this);
        return "filtersGrouped";
    }

    public String removeFilters() {
        FilterHelper.removeFilters(this);
        convertFilterBeansToFilters();
        return "filtersRemoved";
    }

    public String ungroupFilters() {
        FilterHelper.ungroupFilters(this);
        return "filtersGrouped";
    }

    public String refreshOperationsAction() {
        FilterHelper.refreshFilterAction(this);
        return "";
    }

    public boolean isTextFilter() {
        boolean isTextFilter = true;
        PropertyInfo propertyInfo = getPropertyInfoForField();
        if (propertyInfo.getAllowedOperations().contains(LogicalOperation.CONTAINS_ALL))
            isTextFilter = false;
        return isTextFilter;
    }
    
    /* default implementation as this FilterContainer does not support integer fields 
     * @see sailpoint.web.FilterContainer#isIntegerFilter()
     */
    public boolean isIntegerFilter() {
        return false;
    }
    
    public boolean isMultiFilter() {
        boolean isMultiFilter = false;
        PropertyInfo propertyInfo = getPropertyInfoForField();
        if (propertyInfo.getAllowedOperations().contains(LogicalOperation.CONTAINS_ALL))
            isMultiFilter = true;
    	return isMultiFilter;
    }

    public boolean isBoolFilter() {
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
    
    public boolean isCompilationError() {
        return compilationError;
    }
    
    public void setCompilationError(boolean compilationError) {
        this.compilationError = compilationError;
    }

    public boolean isFilterIgnoreCase() {
        return filterIgnoreCase;
    }
    
    public void setFilterIgnoreCase(boolean filterIgnoreCase) {
        this.filterIgnoreCase = filterIgnoreCase;
    }

    public boolean isShowFilterSource() {
        return showFilterSource;
    }
    
    public void setShowFilterSource(boolean show) {
        this.showFilterSource = show;
    }
    
    public String showSource() {
        return FilterHelper.showSource(this);
    }

    public String hideSource() {
        return FilterHelper.hideSource(this);
    }
    
    public boolean isLikeOperation() {
        boolean result;

        if (filterLogicalOp == null || filterLogicalOp.trim().length() == 0) {
            result = false;
        } else {
            try {
                result = Filter.LogicalOperation.LIKE == Enum.valueOf(
                                Filter.LogicalOperation.class,
                                filterLogicalOp);
            } catch (IllegalArgumentException e) {
                result = false;
            }
        }

        return result;
    }

    public String newFilter() {
        log.debug("[newFilter] Filter Field: " + filterField);
        log.debug("[newFilter] Filter Type: " + filterLogicalOp);
        log.debug("[newFilter] Filter MatchMode: " + filterMatchMode);
        log.debug("[newFilter] Filter Value: " + filterValue);
        log.debug("[newFilter] Filter IgnoreCase: " + filterIgnoreCase);
        log.debug("Request params: " + getRequestParameters().toString());

        filterError = null;

        Filter.LogicalOperation logicalOp;
        if (filterLogicalOp == null)
            logicalOp = null;
        else
            logicalOp = Enum.valueOf(Filter.LogicalOperation.class, filterLogicalOp);

        if ((logicalOp == LogicalOperation.IN || logicalOp == LogicalOperation.CONTAINS_ALL)) {
        	if (filterListValue == null || filterListValue.isEmpty()) {
        		addMessage(new Message(Message.Type.Error, MessageKeys.FILTER_VALUE_REQ, (Object[]) null), null);
                return null;
        	}
        } else if ( (filterValue == null || filterValue.equals("")) &&
             (!(Filter.LogicalOperation.ISNULL == logicalOp) && !(Filter.LogicalOperation.NOTNULL == logicalOp)) ) {
            addMessage(new Message(Message.Type.Error, MessageKeys.FILTER_VALUE_REQ, (Object[]) null), null);
            return null;
        }

        InputType inputType = null;
        
        log.debug("Logical op is " + logicalOp);

        if (logicalOp != null) {
            inputType = SearchInputDefinition.getInputType(logicalOp);
        }

        // Create a dummy SearchInputDefintion to help us generate the
        // proper Filter.  Start with the identity SearchInputDefinition
        Map<String, PropertyInfo> propertyInfoMap = getPropertyInfo();
        SearchInputDefinition input = new SearchInputDefinition();
        input.setName(filterField);
        input.setDescription(propertyInfoMap.get(filterField).getDescription());
        IdentityAttributeInfo attributeInfo = getAttributeInfo(filterField);
        if (attributeInfo != null) {
            PropertyType propertyType = attributeInfo.getPropertyType();
            input.setPropertyType(propertyType);
            if (input.getPropertyType() == PropertyType.Identity) {
                String identityPropertyName = filterField + ".name";
                input.setPropertyName(identityPropertyName);
            } else {
                input.setPropertyName(filterField);
            }

            AttributeType attributeType = attributeInfo.getAttributeType();
            String builder;
            if (attributeType == AttributeType.System || attributeType == AttributeType.Standard) {
                builder = BaseFilterBuilder.class.getName();
            } else {
                builder = ExternalAttributeFilterBuilder.class.getName();
            }
            input.setFilterBuilder(builder);
            
            log.debug("Setting inputType to " + inputType);
            input.setInputType(inputType);
    
            if (logicalOp == LogicalOperation.IN || logicalOp == LogicalOperation.CONTAINS_ALL) {
                input.setValue(filterListValue);
                input.setPropertyType(PropertyType.StringList);
            } else if (input.getPropertyType() == PropertyType.Boolean) {
                input.setValue(booleanFilterValue);
            } else {
                input.setValue(filterValue);
            }

            Filter.MatchMode matchMode = null;
    
            if (logicalOp == Filter.LogicalOperation.LIKE && filterMatchMode != null)
                matchMode = Enum.valueOf(Filter.MatchMode.class, filterMatchMode);
    
            input.setMatchMode(matchMode);
    
            log.debug("filterValue was " + filterValue +
                      ".  Set input value to " + input.getValue());
    
            log.debug("[newFilter] input: " + input.getName() + " Value: "
                            + input.getValue() + " Op: "
                            + input.getInputTypeValue());
    
            input.setIgnoreCase(filterIgnoreCase);
    
            try {
                LeafFilter filter = (LeafFilter) input.getFilter(getContext());
    
                if (filter == null) {
                    throw new GeneralException(
                            new Message(Message.Type.Error,
                                    MessageKeys.ERR_CREATING_FILTER_FOR_INPUT_TYPE
                            , new Message(input.getInputType().getMessageKey())
                            , logicalOp.toString()));
                }

                if (isLikeOperation()) {
                    filter.setMatchMode(Enum.valueOf(Filter.MatchMode.class, filterMatchMode));
                }
    
                FilterSelectBean fBean =
                    new FilterSelectBean(filter, propertyInfoMap);
                fBean.setProperty(input.getName());
                fBean.setProperty(input.getDescription());
                List<FilterSelectBean> filterBeans = getFilterBeans();
                filterBeans.add(fBean);
                log.debug("[newFilter] fBean: " + fBean);
                addFilter(filter);
            } catch (GeneralException ge) {
                log.error("Unable to get filter for SearchInputDefinition def: " +
                          input.getDescription() + ". Exception: " + ge.getMessage(), ge);
                filterError = "You have entered an invalid filter.";
            }
            if (log.isDebugEnabled()) {
                log.debug("[newFilter] Final constraints: " + filters);
            }
            setFilterString(null);
        } else {
            log.error("The IdentityAttribute filter widget attempted to create a filter for an attribute that doesn't exist.");
            return null;
        }
        
        return "filtersAdded";
    }

    public String getApplicationId() {
    	return null;
    }
    
    /**
     * @return The root operation for a composite filter; null otherwise
     */
    public String initFilters() {
        String operation = null;
        try {
//            log.debug("[initFilters: " + filters);

            if (filterBeans == null) {
                filterBeans = new ArrayList<FilterSelectBean>();
            
                if (filters != null) {
                    /**We now package the filters as one composite filter, so we'll
                     * want to unpackage it into it's children when we get it back */
                    if(filters.size()==1) {
                        Filter filter = filters.get(0);
                        if(filter instanceof CompositeFilter) {
                            for(Filter f : ((CompositeFilter)filter).getChildren()) {
                                filterBeans.add(new FilterSelectBean(f, getPropertyInfo()));
                            }
               
                            operation = ((CompositeFilter)filter).getOperation().name();
                        } else {
                            filterBeans.add(new FilterSelectBean(filter, getPropertyInfo()));
                        }
                    } else {
                        for (Filter f : filters) {
                            filterBeans.add(new FilterSelectBean(f, getPropertyInfo()));
                        } 
                    }
                }
            }
        } catch (ClassCastException e) {
            log.error("ClassCastException during Filter editing", e);
        }
        return operation;
    }

    private IdentityAttributeInfo getAttributeInfo(String fieldName) {
        ObjectAttribute attributeDef = getAttributeDefinition(fieldName);
        IdentityAttributeInfo attributeInfo = null;
        if (attributeDef != null) {
            AttributeType attributeType;
            if (attributeDef.isSystem())
                attributeType = AttributeType.System;
            else if (attributeDef.isMulti())
                attributeType = AttributeType.Extended;
            else 
                attributeType = AttributeType.Standard;
            attributeInfo = new IdentityAttributeInfo(attributeDef.getPropertyType(), attributeType);
        }
        return attributeInfo;
    }
    
    private void convertFilterBeansToFilters() {
        filters.clear();
        if (filterBeans != null) {
            for (FilterSelectBean filterBean : filterBeans) {
                filters.add(filterBean.getFilter());
            }
        }
    }
    
    private enum AttributeType {
        System,
        Standard,
        Extended
    }
    
    private class IdentityAttributeInfo {
        private PropertyType propertyType;
        private AttributeType attributeType;
        private IdentityAttributeInfo(PropertyType propertyType, AttributeType attributeType) {
            this.propertyType = propertyType;
            this.attributeType = attributeType;
        }
        
        private PropertyType getPropertyType() {
            return propertyType;
        }
        
        private AttributeType getAttributeType() {
            return attributeType;
        }
    }
}
