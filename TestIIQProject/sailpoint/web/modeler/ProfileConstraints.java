/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.modeler;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

import javax.faces.context.FacesContext;
import javax.faces.model.SelectItem;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.AttributeDefinition;
import sailpoint.object.Filter;
import sailpoint.object.Filter.BooleanOperation;
import sailpoint.object.Filter.CompositeFilter;
import sailpoint.object.Filter.LeafFilter;
import sailpoint.object.Filter.LogicalOperation;
import sailpoint.object.PropertyInfo;
import sailpoint.object.SearchInputDefinition;
import sailpoint.object.SearchInputDefinition.InputType;
import sailpoint.object.SearchInputDefinition.PropertyType;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.AttrSelectBean;
import sailpoint.web.BaseDTO;
import sailpoint.web.FilterContainer;
import sailpoint.web.FilterSelectBean;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.util.FilterHelper;


public class ProfileConstraints extends BaseDTO implements FilterContainer {
    private static final long serialVersionUID = 1985960591244936689L;

    private static final Log log = LogFactory.getLog(ProfileConstraints.class);
    
    private static final String NEW_LINE = System.getProperty("line.separator");

    private String filterField;

    private String filterValue;
    
    private String entitlementFilterValue;
    
    private List<String> entitlementMultiFilterValue;
    
    private String booleanFilterValue;
    
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

    private List<FilterSelectBean> constraints;

    private ProfileDTO editedProfile;

    private ProfileFilterEditor profileFilterEditor;


    public ProfileConstraints(ProfileFilterEditor profileFilterEditor) {
        editedProfile = profileFilterEditor.getProfile();
        this.profileFilterEditor = profileFilterEditor;
    }

    /** Method that returns a string representation of the filterbeans for editing **/
    public String getFilterString() {
        ProfileDTO profile = editedProfile;

        if (profile != null)  {
            List<Filter> filterConstraints = new ArrayList<>();
            for (FilterSelectBean constraint : Util.safeIterable(this.constraints)) {
                filterConstraints.add(constraint.getFilter());
            }

            if (log.isDebugEnabled()) {
                if (filterConstraints.isEmpty()) {
                    log.debug("No filter constraints to log.");
                } else {
                    log.debug("Filters: " + filterConstraints);
                }
            }
            if (null == filterString || filterString.trim().length() == 0) {
                if (filterConstraints.size() > 1) {
                    String booleanOp = getGlobalBooleanOp();
                    // Default the op to AND
                    if (null == booleanOp) {
                        booleanOp = Filter.BooleanOperation.AND.name();
                    }
                    CompositeFilter newComposite =
                        new CompositeFilter(Enum.valueOf(Filter.BooleanOperation.class, booleanOp), filterConstraints);
                    filterString = newComposite.getExpression(true);
                }
                else if (!filterConstraints.isEmpty()) {
                    filterString =  filterConstraints.get(0).getExpression(true);
                }
            }
        } else {
            log.error("Profile was null.", new Exception());
        }
        log.debug("Getting Filter String: " + filterString);
        return filterString;
    }

    public void setFilterString(String filterString) {
        log.debug("Setting Filter String: " + filterString);
        this.filterString = filterString;
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

    /* 
     * default implementation as this FilterContainer does not support integer fields 
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

    private boolean isManaged() {
    	AttributeDefinition attributeDef = ProfileUtil.getAppAttributeDefinition(getFilterField(), editedProfile);
        if (attributeDef != null) {
            return attributeDef.isManaged();
        }
        return false;
    }

    private boolean isSimpleManaged() {
        AttributeDefinition attributeDef = ProfileUtil.getAppAttributeDefinition(getSimpleFilterField(), editedProfile);
        if (attributeDef != null) {
            return attributeDef.isManaged();
        }
        return false;
    }

    public boolean isTextFilter() {
    	if (isManaged()) {
    		return false;
    	}
    	
        return !isMultiFilter();
    }
    
    /* default implementation as this FilterContainer does not support integer fields
     * @see sailpoint.web.FilterContainer#isIntegerFilter()
     */
    public boolean isIntegerFilter() {
        return false;
    }
    
    public boolean isMultiFilter() {
    	if (isManaged()) {
    		return false;
    	}
    	
    	Filter.LogicalOperation logicalOp = getLogicalOp();
    	return (logicalOp == LogicalOperation.IN || logicalOp == LogicalOperation.CONTAINS_ALL);
    }
    
    public boolean isBoolFilter() {
        return false;
    }
    
    public boolean isEntitlementFilter() {
    	if (!isManaged()) {
    		return false;
    	}
    	
    	return !isEntitlementMultiFilter();
    }

    public boolean isSimpleEntitlementFilter() {
        if (!isSimpleManaged()) {
            return false;
        }

        return !isSimpleEntitlementMultiFilter();
    }

    public boolean isEntitlementMultiFilter() {
    	if (!isManaged()) {
    		return false;
    	}
    	
    	Filter.LogicalOperation logicalOp = getLogicalOp();
    	return (logicalOp == LogicalOperation.IN || logicalOp == LogicalOperation.CONTAINS_ALL);
    }

    public boolean isSimpleEntitlementMultiFilter() {
        if (!isSimpleManaged()) {
            return false;
        }

        Filter.LogicalOperation logicalOp = getLogicalOp();
        return (logicalOp == LogicalOperation.CONTAINS_ALL);
    }

    public String getEntitlementFilterValue() {
    	if (entitlementFilterValue == null) {
    		return "";
    	}
    	
    	return entitlementFilterValue;
    }
    
    public void setEntitlementFilterValue(String entitlementFilterValue) {
    	this.entitlementFilterValue = entitlementFilterValue;
    }
    
    public String getEntitlementMultiFilterValue() {
    	return toListValue(entitlementMultiFilterValue);
    }
    
    public void setEntitlementMultiFilterValue(String entitlementMultiFilterValue) {
    	this.entitlementMultiFilterValue = fromListValue(entitlementMultiFilterValue);
    }

    /**
     * @return Map of attribute names for the current applications.
     */
    public List<SelectItem> getAvailableItemNames() {
        List<SelectItem> availableItemNames = new ArrayList<SelectItem>();
        availableItemNames.add(new SelectItem("", getMessage(MessageKeys.ROLE_SIMPLE_PLEASE_SELECT)));
        for (AttributeDefinition attribute : ProfileUtil.getAppAttributeDefinitions(editedProfile)) {
            String desc = attribute.getDescription();
            if (desc == null || desc.trim().length() == 0)
                desc = attribute.getName();
            availableItemNames.add(
                new SelectItem(attribute.getName(), desc));
        }

        return availableItemNames;
    }

    /**
     * @return Map of managed attribute names for the current applications.
     */
    public List<SelectItem> getAvailableSimpleItemNames() {
        List<SelectItem> availableItemNames = new ArrayList<SelectItem>();
        availableItemNames.add(new SelectItem("", getMessage(MessageKeys.ROLE_SIMPLE_PLEASE_SELECT)));
        for (AttributeDefinition attribute : ProfileUtil.getAppAttributeDefinitions(editedProfile)) {
            if (attribute.isManaged()) {
                String desc = attribute.getDescription();
                if (desc == null || desc.trim().length() == 0) {
                    desc = attribute.getName();
                }
                availableItemNames.add(new SelectItem(attribute.getName(), desc));
            }
        }

        return availableItemNames;
    }

    public boolean isAllowedOperation() {
        AttributeDefinition attributeDef = ProfileUtil.getAppAttributeDefinition(getFilterField(), editedProfile);

        if (attributeDef == null || attributeDef.getAllowedOperations() == null)
            return false;

        if (filterLogicalOp == null || filterLogicalOp.trim().length() == 0)
            return false;

        return attributeDef.getAllowedOperations().contains(LogicalOperation.valueOf(filterLogicalOp));
    }

    public List<FilterSelectBean> getFilterBeans() {
        String op = initProfileConstraints();
        if(op!=null)
            setGlobalBooleanOp(op);

        if (constraints != null && !constraints.isEmpty()) {
            constraints.get(0).setFirstElement(true);
        }
        return constraints;
    }

    /**
     * This setter populates the bean property as well as stashing the list
     * in the session.
     *
     * @param constraints
     *            Profile constraints list
     */
    @SuppressWarnings("unchecked")
    public void setFilterBeans(List<FilterSelectBean> constraints) {
        this.constraints = constraints;
    }

    public int getFilterCount() {
        final int count;
        List<FilterSelectBean> filterBeans = getFilterBeans();

        if (filterBeans == null) {
            count = 0;
        } else {
            count = filterBeans.size();
        }

        return count;
    }

    @SuppressWarnings("unchecked")
    public String groupFilters() {
        FilterHelper.groupFilters(this);
        return "filtersGrouped";
    }

    public String removeFilters() {
        FilterHelper.removeFilters(this);
        return "filtersRemoved";
    }

    @SuppressWarnings("unchecked")
    public String convertStringToFilters() {
        String result = FilterHelper.convertStringToFilters(this);
        setCompilationError(result==null);
        setShowFilterSource(result==null);
        return "filtersGrouped";
    }

    public String compileFilterString() {
        Filter filter = FilterHelper.compileFilterFromString(this);
        setCompilationError(filter==null);
        return "filterCompiled";
    }

    public String showSource() {
        return FilterHelper.showSource(this);
    }

    public String hideSource() {
        return FilterHelper.hideSource(this);
    }

    @Override
    public PropertyInfo getPropertyInfoForField() {
        PropertyInfo inputForField = null;
        String filterField = getFilterField();

        if (filterField != null) {
            for (AttributeDefinition attributeDef : ProfileUtil.getAppAttributeDefinitions(editedProfile)) {
                if (attributeDef.getName().equals(filterField)) {
                    inputForField = new PropertyInfo();
                    inputForField.setAllowedOperations(attributeDef.getAllowedOperations());
                    inputForField.setDescription(attributeDef.getDisplayableName());
                    inputForField.setValueType(attributeDef.getType());
                    break;
                }
            }
        }

        return inputForField;
    }

    private PropertyInfo getPropertyInfoForSimpleField() {
        PropertyInfo inputForField = null;
        String filterField = getSimpleFilterField();

        if (filterField != null) {
            for (AttributeDefinition attributeDef : ProfileUtil.getAppAttributeDefinitions(editedProfile)) {
                if (attributeDef.getName().equals(filterField)) {
                    inputForField = new PropertyInfo();
                    inputForField.setAllowedOperations(attributeDef.getAllowedOperations());
                    inputForField.setDescription(attributeDef.getDisplayableName());
                    inputForField.setValueType(attributeDef.getType());
                    break;
                }
            }
        }

        return inputForField;
    }

    @SuppressWarnings("unchecked")
    public String ungroupFilters() {
        FilterHelper.ungroupFilters(this);
        return "filtersGrouped";
    }

    public Map<String, PropertyInfo> getPropertyInfo() {
        Map<String, PropertyInfo>propertyInfoMap = new HashMap<String, PropertyInfo>();

        for (AttributeDefinition def : ProfileUtil.getAppAttributeDefinitions(editedProfile)) {
            PropertyInfo propertyInfo = new PropertyInfo();
            propertyInfo.setAllowedOperations(def.getAllowedOperations());
            propertyInfo.setDescription(def.getDisplayableName());
            propertyInfo.setValueType(def.getType());
            propertyInfoMap.put(def.getName(), propertyInfo);
        }

        return propertyInfoMap;
    }

    public void addFilter(Filter f) {
        try {
            editedProfile.addConstraint(f);
        } catch (Exception e) {
            log.error("Exception", e);
        }
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

    public String getSimpleFilterField() {
        // Provide a default
        if (filterField == null || filterField.isEmpty()) {
            List<SelectItem> itemNames = getAvailableSimpleItemNames();
            if (itemNames != null && !itemNames.isEmpty()) {
                if (!((String)getAvailableSimpleItemNames().get(0).getValue()).isEmpty()) {
                    setFilterField((String) getAvailableSimpleItemNames().get(0).getValue());
                    FilterHelper.refreshFilterAction(this);
                }
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
                AttributeDefinition defForField = ProfileUtil.getAppAttributeDefinition(fieldName, editedProfile);

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
        filterMatchMode = matchMode;
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

    public String getFilterValue() {
        if (filterValue == null)
            filterValue = "";

        return filterValue;
    }

    public void setFilterValue(String value) {
        filterValue = value;
    }
    
    private static List<String> fromListValue(String listValue) {
    	if (listValue == null) {
    		return new ArrayList<String>();
    	} else {
    		List<String> result = new ArrayList<String>();

            StringTokenizer tokenizer = new StringTokenizer(listValue, NEW_LINE);
            while (tokenizer.hasMoreTokens()) {
                //Ensure we don't get windoze carriage returns leftover
                String listItem = tokenizer.nextToken().replaceAll("[\r]", "");
                result.add(listItem);
            }
            return result;
    	}
    }
    
    private static String toListValue(List<String> valueList) {
    	StringBuilder listValue = new StringBuilder();
    	for (String listElement : Util.safeIterable(valueList)) {
    		listValue.append(listElement);
    		listValue.append(NEW_LINE);
    	}
    	
    	return listValue.toString();
    }
    
    public void setFilterListValue(String filterListValue) {
    	this.filterListValue = fromListValue(filterListValue);
    }
    
    public String getFilterListValue() {
    	return toListValue(filterListValue);
    }

    public boolean isFilterIgnoreCase() {
        return filterIgnoreCase;
    }

    public void setFilterIgnoreCase(boolean ignoreCase) {
        filterIgnoreCase = ignoreCase;
    }

    public List<Filter> getFilters() {
        List<Filter> retval = null;

        retval = editedProfile.getConstraints();

        if (retval == null) {
            retval = new ArrayList<Filter>();
        }
        return retval;
    }

    public String getGlobalBooleanOp() {
        // IIQHH-1260: applying the same fix as IIQSR-28 to handle empty globalBooleanOp check
        if (Util.isNullOrEmpty(globalBooleanOp)) {
            // There's no advantage to using the getter here. If a filter string
            // doesn't exist, simply return the default global boolean operator (AND)
            globalBooleanOp = FilterContainer.getBooleanOpFromFilterString(filterString);
        }
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

        return inputTypeChoices;
    }

    public List<SelectItem> getSimpleInputTypeChoices() {
        PropertyInfo propertyInfoForField = getPropertyInfoForSimpleField();

        if (inputTypeChoices == null) {
            inputTypeChoices = new ArrayList<SelectItem>();
        } else {
            inputTypeChoices.clear();
        }

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

        return inputTypeChoices;
    }

    public String refreshOperationsAction() {
        performA4JWorkaround();
        FilterHelper.refreshFilterAction(this);
        return "";
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

    /**
     * Public version for advanced entitlements created from the client.
     * @return
     */
    public String newFilter() {
        return newFilter(false);
    } // newFilter() action

    /**
     * The implementation of newFilter() for both simple and advanced entitlements.
     * @return
     */
    protected String newFilter(boolean isSimpleEntitlement) {
        log.debug("[newFilter] Filter Field: " + filterField);
        log.debug("[newFilter] Filter Type: " + filterLogicalOp);
        log.debug("[newFilter] Filter MatchMode: " + filterMatchMode);
        log.debug("[newFilter] Filter Value: " + filterValue);
        log.debug("[newFilter] Filter IgnoreCase: " + filterIgnoreCase);
        log.debug("Request params: " + getRequestParameters().toString());

        performA4JWorkaround();
        filterError = null;

        // For simple entitlements it's possible they haven't selected a field yet, in which case we just want
        // to send back an error without creating a filter.
        if (filterField == null || filterField.isEmpty()) {
            addMessage(new Message(Message.Type.Error, MessageKeys.SIMPLE_ENTITLEMENT_PROPERTY_REQ, (Object[]) null), null);
            return null;
        }

        Filter.LogicalOperation logicalOp = getLogicalOp();

        // For simple entitlements it's possible for operation to be empty; unlikely we would hit this without first
        // having failed on filterField above, but add this check out of an abundance of caution..
        if (logicalOp == null) {
            addMessage(new Message(Message.Type.Error, MessageKeys.SIMPLE_ENTITLEMENT_SEARCH_TYPE_REQ, (Object[]) null), null);
            return null;
        }

        // The rest of this handles the case of a null filter value; we just need to show a different error message
        // depending on if it's simple or advanced.
        final String valueRequiredKey = isSimpleEntitlement ? MessageKeys.SIMPLE_ENTITLEMENT_VALUE_REQ : MessageKeys.FILTER_VALUE_REQ;

        if(!(Filter.LogicalOperation.ISNULL == logicalOp) && !(Filter.LogicalOperation.NOTNULL == logicalOp)){
            if (isTextFilter() && Util.isNullOrEmpty(filterValue)) {
                addMessage(new Message(Message.Type.Error, valueRequiredKey, (Object[]) null), null);
                return null;
            } else if (isMultiFilter() && Util.isEmpty(filterListValue)) {
                addMessage(new Message(Message.Type.Error, valueRequiredKey, (Object[]) null), null);
                return null;
            } else if (isEntitlementMultiFilter() && Util.isEmpty(entitlementMultiFilterValue)) {
                addMessage(new Message(Message.Type.Error, valueRequiredKey, (Object[]) null), null);
                return null;
            } else if (isEntitlementFilter() && Util.isNullOrEmpty(entitlementFilterValue)) {
                addMessage(new Message(Message.Type.Error, valueRequiredKey, (Object[]) null), null);
                return null;
            }
        }

        // Create a dummy SearchInputDefintion to help us generate the
        // proper Filter
        SearchInputDefinition input = new SearchInputDefinition();
        input.setName(filterField);
        input.setPropertyName(filterField);
        input.setDescription(getPropertyInfo().get(filterField).getDescription());
        InputType inputType = null;

        log.debug("Logical op is " + logicalOp);

        if (logicalOp != null) {
            inputType = SearchInputDefinition.getInputType(logicalOp);
        }

        log.debug("Setting inputType to " + inputType);
        input.setInputType(inputType);

        Filter.MatchMode matchMode = null;

        if (logicalOp == Filter.LogicalOperation.LIKE && filterMatchMode != null)
            matchMode = Enum.valueOf(Filter.MatchMode.class, filterMatchMode);

        input.setMatchMode(matchMode);

        if (isEntitlementMultiFilter()) {
            List<String> valueList = new ArrayList<String>(entitlementMultiFilterValue);
            input.setValue(valueList);
            input.setPropertyType(PropertyType.StringList);
        } else if (input.getPropertyType() == PropertyType.Boolean) {
            input.setValue(booleanFilterValue);
            input.setPropertyType(PropertyType.String);
        } else if (isEntitlementFilter()) {
            input.setValue(entitlementFilterValue);
            input.setPropertyType(PropertyType.String);
        } else if (isMultiFilter()) {
            List<String> valueList = new ArrayList<String>(filterListValue);
            input.setValue(valueList);
            input.setPropertyType(PropertyType.StringList);
        } else if (isTextFilter()) {
            input.setValue(filterValue);
            input.setPropertyType(PropertyType.String);
        }

        log.debug("entitlementFilterValue was " + entitlementFilterValue +
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

            filter.setOperation(logicalOp);
            if (isLikeOperation()) {
                filter.setMatchMode(Enum.valueOf(Filter.MatchMode.class, filterMatchMode));
            }

            FilterSelectBean fBean =
                    new FilterSelectBean(filter, getPropertyInfo());
            fBean.setProperty(input.getName());
            fBean.setProperty(input.getDescription());
            List<FilterSelectBean> profileConstraints = getFilterBeans();
            profileConstraints.add(fBean);
            log.debug("[newFilter] fBean: " + fBean);
            addFilter(filter);
        } catch (GeneralException ge) {
            log.error("Unable to get filter for SearchInputDefinition def: " +
                    input.getDescription() + ". Exception: " + ge.getMessage(), ge);
            filterError = "You have entered an invalid filter.";
        }
        if (log.isDebugEnabled()) {
            log.debug("[newFilter] Final constraints: " + editedProfile.getConstraints());
        }
        setFilterString(null);
        return "filtersAdded";
    } // newFilter() action


    private void performA4JWorkaround() {
        // a4j isn't applying the model values.  No validation errors are cropping up,
        // so the current theory is that because we are embedding the a4j panel in a
        // <ui:fragment> that was not initially rendered the components are not being
        // properly synched.  For this reason, we attempt to set values from the request
        // when needed.
        Map request = getRequestParameters();
        if (filterField == null) {
            filterField = (String) request.get("editForm:profilesearchFieldList");
        }

        if (filterLogicalOp == null) {
            filterLogicalOp = (String) request.get("editForm:profileinputTypeChoices");
        }

        if (filterMatchMode == null) {
            filterMatchMode = (String) request.get("editForm:profilematchMode");
        }

        if (filterValue == null) {
            filterValue = (String) request.get("editForm:profilefilterValue");
        }

        if (filterListValue == null) {
            setFilterListValue((String) request.get("editForm:profilefilterListValue"));
        }
        
        if (entitlementFilterValue == null) {
        	entitlementFilterValue = (String)request.get("editForm:profileentitlementFilterValue");
        }
        
        if (entitlementMultiFilterValue == null) {
        	setEntitlementMultiFilterValue((String)request.get("editForm:profileentitlementMultiFilterValue"));
        }
        
        String ignoreCase = (String) request.get("editForm:profileignoreCase");

        if (ignoreCase != null) {
            filterIgnoreCase = "on".equals(ignoreCase);
        }
    }

    public FacesContext getFacesContext() {
        return FacesContext.getCurrentInstance();
    }

    public Map<String, String> getMatchModeValues() {
        return FilterHelper.getMatchModeValues(getLocale());
    }  // getMatchModeValues()

    public String getInstructions() {
        return getMessage(MessageKeys.INST_PROFILE_FILTERS);
    }

    public String getSimpleInstructions() {
        return getMessage(MessageKeys.INST_SIMPLE_PROFILE_FILTERS);
    }
    boolean prepareToSave() {
        boolean failures = false;
        List<Filter> filters = new ArrayList<Filter>();
        for (FilterSelectBean filterSelectBean : constraints) {
            Filter f = filterSelectBean.getFilter();

            if (f instanceof LeafFilter) {
                LeafFilter leafFilter = (LeafFilter) f;
                try {
                    if (!filterSelectBean.isUnaryOperation())
                        filterSelectBean.coerceValue();
                } catch (Exception ex) {
                    addMessage(new Message(Message.Type.Error, MessageKeys.ERR_CONVERTING_VALUE, new Object[] {leafFilter.getProperty(), ex.getLocalizedMessage()}), null);
                    failures = true;
                }

                String property = leafFilter.getProperty();
                if (property == null || property.length() == 0) {
                    addMessage(new Message(Message.Type.Error, MessageKeys.ATTR_RULES_MUST_HAVE_ATTR, (Object[]) null), null);
                    failures = true;
                }
            }

            filters.add(f);
        }

        if (failures)
            return false;

        /* Need to persist the boolean operation if there is more than one filter */
        if(filters!=null && filters.size()>1 && getGlobalBooleanOp()!=null) {
            BooleanOperation op = Enum.valueOf(Filter.BooleanOperation.class, getGlobalBooleanOp());
            CompositeFilter newFilter =
                new CompositeFilter(op, filters);
            List<Filter> newFilters = new ArrayList<Filter>();
            newFilters.add(newFilter);
            editedProfile.setConstraints(newFilters);
        } else {
            editedProfile.setConstraints(filters);
        }

        return true;
    }

    @SuppressWarnings("unchecked")
    protected List<Filter> processProfileConstraints(List<AttrSelectBean> accountAttributes) {
        List<Filter> filters = null;
        if (accountAttributes != null && accountAttributes.size() > 0) {
            filters = new ArrayList<Filter>();
            // Convert the _accountAttributes to a map where the value might
            // be a list. Since Multi-valued
            // attributes should uses a CONTAINS_ALL filter, we'll treat
            // them like lists even if only one is selected.
            Map<String, Object> attrMap = new HashMap<String, Object>();
            for (AttrSelectBean a : accountAttributes) {
                if (a.isSelected()) {
                    if (attrMap.containsKey(a.getName())) {
                        Object value = attrMap.get(a.getName());
                        if (value instanceof List) {
                            ((List) value).add(a.getValue());
                        } else {
                            attrMap.put(
                                a.getName(),
                                Util.arrayToList(
                                    new String[] {
                                        value.toString(),
                                        a.getValue() }));
                        }
                    } else if (a.isMultiValue()) { // multiValues should be
                                                    // treated as lists
                        attrMap.put(
                            a.getName(),
                            Util.arrayToList(
                                new String[] { a.getValue() }));
                    } else {
                        attrMap.put(a.getName(), a.getValue());
                    }
                }
            }

            // second, create filters. If the map value is a List, then use
            // CONTAINS_ALL, otherwise use EQ
            for (String key : attrMap.keySet()) {
                Filter.LeafFilter f =
                    new Filter.LeafFilter(
                       (attrMap.get(key) instanceof List) ? Filter.LogicalOperation.CONTAINS_ALL
                                                          : Filter.LogicalOperation.EQ,
                       key, attrMap.get(key));
                // this is overkill to re-use the FilterSelectBean,
                // but it handles the value coercion nicely
                FilterSelectBean lf =
                    new FilterSelectBean(f, getPropertyInfo());
                try {
                    lf.coerceValue();
                } catch (Exception ex) {
                    log.error("Unable to convert value for '" + key + "' to the correct type: ", ex);
                    addMessage(new Message(Message.Type.Error, MessageKeys.ERR_CONVERTING_VALUE, new Object[] {key, ex.getLocalizedMessage()}), null);
                }
                filters.add(lf.getFilter());
            }
        }

        return filters;
    }

    @SuppressWarnings("unchecked")
    String initProfileConstraints() {
        String operation = null;
        try {
            if (editedProfile != null) {
                log.debug("[initProfileConstraints: " + editedProfile.getConstraints());
                constraints =
                    (List<FilterSelectBean>) profileFilterEditor.getProfileFilters();
                if (constraints == null) {
                    constraints = new ArrayList<FilterSelectBean>();

                    List<Filter> profFilters = editedProfile.getConstraints();
                    if (profFilters != null) {
                        /**We now package the filters as one composite filter, so we'll
                         * want to unpackage it into it's children when we get it back */
                        if(profFilters.size()==1) {
                            Filter filter = profFilters.get(0);
                            if(filter instanceof CompositeFilter) {
                                for(Filter f : ((CompositeFilter)filter).getChildren())
                                    constraints.add(new FilterSelectBean(f, getPropertyInfo()));
                                operation = ((CompositeFilter)filter).getOperation().name();
                            }
                            else
                                constraints.add(new FilterSelectBean(filter, getPropertyInfo()));
                        } else {
                            for (Filter f : profFilters) {
                                constraints.add(
                                    new FilterSelectBean(f, getPropertyInfo()));
                            } // for f in profFilters
                        }

                    } // if _profFilters

                    profileFilterEditor.setProfileFilters(constraints);
                }
            } // if profile
        } catch (ClassCastException e) {
            log.error("ClassCastException during Filter editing", e);
        }
        return operation;
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
    
    private Filter.LogicalOperation getLogicalOp() {
    	Filter.LogicalOperation logicalOp;
    	
        if (filterLogicalOp == null || filterLogicalOp.isEmpty())
            logicalOp = null;
        else
            logicalOp = Enum.valueOf(Filter.LogicalOperation.class, filterLogicalOp);
        
        return logicalOp;
    }
    
    public String getApplicationId() {
    	if (editedProfile.getApplication() != null) {
    		return editedProfile.getApplication().getId();
    	}
    	
    	return null;
    }
}
