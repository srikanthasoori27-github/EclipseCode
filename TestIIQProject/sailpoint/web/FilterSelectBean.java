/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * 
 */
package sailpoint.web;

import java.io.Serializable;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import javax.faces.model.SelectItem;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.Filter;
import sailpoint.object.PropertyInfo;
import sailpoint.object.SearchItemFilter;
import sailpoint.object.Filter.BooleanOperation;
import sailpoint.object.Filter.CompositeFilter;
import sailpoint.object.Filter.LeafFilter;
import sailpoint.object.Filter.LogicalOperation;
import sailpoint.object.Filter.MatchMode;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;

/**
 * @author peter.holcomb
 *
 */
public class FilterSelectBean extends BaseDTO implements Serializable {
    private static final Log log = LogFactory.getLog(FilterSelectBean.class);
    private static final long serialVersionUID = -8464761678338241912L;
    private static final String NEW_LINE = System.getProperty("line.separator");
    
    boolean selected;
    boolean composite;
    Filter filter;
    Filter join;
    List<FilterSelectBean> children;
    private PropertyInfo inputDefinition;
    
    /* The ui-friendly property used in the filter */
    private String property;
    
    /* The string representation of the logical operation used in the filter */
    private String operation;
    
    /* The actual value being used in the filter */
    private Object value;
    
    /* Whether to ignore the case of the search */
    private boolean ignoreCase;
    
    /* Used to hide this filter bean from the ui */
    private boolean displayable;

    /* true if this bean contains a list value */
    private boolean list;

    /** This is a ui hack for this bean to let the ui know if it is the first element in the list **/
    boolean firstElement;
    

    public FilterSelectBean() {
        super();
        composite = false;
        log.debug("created instance");
        this.displayable = true;
    }
    
    /**
     * 
     * @param f
     * @param inputs Map of definitions for Searchable items.  In the case of advanced searches, this
     *               is a Map of SearchInputDefinitions.  In the case of Profile attribute rules, this
     *               is a Map of AttributeDefinitions.
     * @throws ClassCastException If the input map contains objects that don't implement Searchable
     */
    @SuppressWarnings("unchecked")
	public FilterSelectBean(Filter f, String displayProperty, String operation, Object value, Map<String, PropertyInfo> inputs) 
    	throws ClassCastException {
        if (f != null && f instanceof LeafFilter) {
        	LeafFilter leaf = (LeafFilter)f;
            inputDefinition = (PropertyInfo) inputs.get(leaf.getProperty());
        } 
        this.operation = operation;
        if (value instanceof String) {
        	list = false;
        	this.value = (String) value;
        } else if (value instanceof List) {
        	list = true;
        	StringBuffer valueToSet = new StringBuffer();
        	if (value != null && !((List<String>)value).isEmpty()) {
        		for (String listElement: (List<String>)value) {
        			valueToSet.append(listElement);
        			valueToSet.append(NEW_LINE);
        		}
        	}
        	this.value = valueToSet.toString();
        } else if (value instanceof Date) {
            this.value = value;
        } else {
        	throw new IllegalArgumentException("The FilterSelectBean only suppots values of type String or List<String>");
        }
        
        this.property = displayProperty;
        filter = f;
        this.displayable = true;
    }   
    
    /** For backwards compatibility to the modeler **/
    public FilterSelectBean(Filter f, Map<String, PropertyInfo> inputs) throws ClassCastException {
        if (f != null) {
            if(f instanceof CompositeFilter) {
                composite = true;
                CompositeFilter composite = (CompositeFilter)f;
                children = new ArrayList<FilterSelectBean>();
                for (Filter filter : composite.getChildren()) {
                    FilterSelectBean fBean = new FilterSelectBean(filter, inputs);
                    children.add(fBean);
                }
                if(!children.isEmpty()) {
                    children.get(0).setFirstElement(true);
                }
                setOperation(composite.getOperation().name());
                this.displayable=true;
            } else {
                composite = false;
                LeafFilter leaf = (LeafFilter)f;
                inputDefinition = (PropertyInfo) inputs.get(leaf.getProperty());
                if (inputDefinition != null) {
                    this.operation = leaf.getOperation().toString();
                    Object leafValue = leaf.getValue();
                    
                    if(leafValue != null) {
                    	if (leafValue instanceof List) {
                    		list = true;
                    		this.value = leafValue;
                    	} else {
                    		list = false;
                    		this.value = leaf.getValue().toString();
                    	}
                    }
                    
                    this.property = inputDefinition.getDescription();
                    this.filter = f;
                    try {
                        coerceValue();
                    } catch (ParseException e) {
                        log.error("Parsing failed at position " + e.getErrorOffset() + " for the value for the " + leaf.getProperty() + " property on a Filter: " + leaf.getValue().toString(), e);
                    }
                } else if (leaf.getProperty() != null && leaf.getProperty().equals("IdentityExternalAttribute")) {
                    // Reconstruct this from the internal representation that we use to define the query for this
                    List<Filter> valueFilters = leaf.getCollectionCondition().getChildren();
                    String externalAttributeName = ((LeafFilter)((CompositeFilter)valueFilters.get(0)).getChildren().get(1)).getValue().toString();
                    inputDefinition = (PropertyInfo) inputs.get(externalAttributeName);
                    this.property = inputDefinition.getDescription();
                    this.operation = LogicalOperation.CONTAINS_ALL.getStringRepresentation();
                    // Now build a freaking list of values
                    List<String> values = new ArrayList<String>();
                    for (Filter valueFilter : valueFilters) {
                        CompositeFilter compFilter = (CompositeFilter) valueFilter;
                        String value = ((LeafFilter)compFilter.getChildren().get(2)).getValue().toString();
                        values.add(value);
                    }                    
                    this.list = true;
                    this.value = values;
                } else {
                    log.debug("inputDefinition is null for leaf property " + leaf.getProperty());
                }
                this.displayable = true;
                
                /** Don't show this filter on the list of filters on the ui if it's a join filter.  **/
                if(leaf.getJoinProperty()!=null) {
                    this.displayable = false;
                }
            }
            filter = f;
        }        
    }   
    
    public FilterSelectBean(SearchItemFilter searchFilter, Map<String, PropertyInfo> inputs) {
    	if(searchFilter!=null) {
    		
    		/** First, check to see if this guy has children...if so, this is a composite
    		 * filter select bean */
    		if(searchFilter.getChildSearchFilters()!=null && !searchFilter.getChildSearchFilters().isEmpty()) {
    			this.composite = true;
    			this.operation = searchFilter.getBooleanOperation();
    			this.children = new ArrayList<FilterSelectBean>();
    			for(SearchItemFilter child : searchFilter.getChildSearchFilters()) {
    				children.add(new FilterSelectBean(child, inputs));
    			}
    		/** If no children, we create a plain ol' filter select bean **/
    		} else {
    			this.operation = searchFilter.getLogicalOperation();
    			Object searchFilterValue = searchFilter.getValue();
    			
    			if(searchFilterValue!=null) {
    				if (searchFilterValue instanceof List) {
    					list = true;
						this.value = searchFilterValue;
    				} else {
    					list = false;
    					this.value = searchFilterValue.toString();
    				}
    			}
    			
    			this.ignoreCase = searchFilter.isIgnoreCase();
    			this.filter = searchFilter.getFilter();
    			inputDefinition = (PropertyInfo) inputs.get(searchFilter.getProperty());
    			
    			if(inputDefinition!=null) {
    				this.property = inputDefinition.getDescription();
    			} else {
    				this.property = searchFilter.getProperty();
    			}
    		}
    		this.displayable = true;
    		this.join = searchFilter.getJoinFilter();
    	}
    }

    /**
     * @return the composite
     */
    public boolean isComposite() {
        return composite;
    }

    /**
     * @param composite the composite to set
     */
    public void setComposite(boolean composite) {
        this.composite = composite;
    }
    
    /**
     * @return the operation
     */
    public String getOperationLabel() {
        final String operation = getOperation();
        String operationLabel = "";
        if(operation!=null) {
        	operationLabel = getMessage("filter_op_" + operation);
        	log.debug("Returning Operation label of " + operationLabel);
        }
        return operationLabel;
    }

    public String getOperation() {
        //defect 21432 there are times when the operation is not set, but we do have one
        if (Util.isNullOrEmpty(operation)) {
            if (null != filter && filter instanceof CompositeFilter) {
                return ((CompositeFilter)filter).getOperation().toString();
            }
            operation = null;
        }
        return operation;
    }

    /**
     * @param operation the operation to set
     */
    public void setOperation(String operation) {
        this.operation = operation;

        // If we have a valid operation value, clear out the filter
        // so that it can be rebuilt with the new operation value.
        if (!Util.isNullOrEmpty(operation)) {
            this.filter = null;
        }
    }

    /**
     * @return the property
     */
    public String getProperty() {
        return property;
    }

    /**
     * @param property the property to set
     */
    public void setProperty(String property) {
            this.property = property;
    }

    /**
     * @return the value
     */
    public Object getValue() {
        return value;
    }
    
    public String getStringValue() {
        if(value instanceof String)
            return (String)value;
        else if(value!=null) 
            return value.toString();
        else
            return null;
    }

    /**
     * @param value the value to set
     */
    public void setValue(String value) {
        this.value = value;
    }
    
    public void setStringValue(String value) {
        this.value = value;
    }
    
    public boolean isIgnoreCase() {
        return ignoreCase;
    }

    public void setIgnoreCase(boolean ignoreCase) {
        if (filter instanceof LeafFilter) {
            ((LeafFilter) filter).setIgnoreCase(ignoreCase);
        }
    }

    /**
     * @return the filter
     */
    public Filter getFilter() {
        /** If this is a composite filter, get the filters from the children and
         * build a composite filter out of them **/
        if(filter==null) {
            if(isComposite() && children!=null && !children.isEmpty()) {
                List<Filter> filters = new ArrayList<Filter>();
                for(FilterSelectBean child : children) {
                    filters.add(child.getFilter());
                }
                //IIQSR-28 Adding empty operation check
                if(Util.isNullOrEmpty(operation)) {
                    operation = BooleanOperation.AND.name();
                }
                filter = new CompositeFilter(Enum.valueOf(BooleanOperation.class, operation), filters);
            } else {
                if(value!=null && !value.equals("")) {
                    filter = new LeafFilter(LogicalOperation.LIKE, property, value, MatchMode.START);
                }
            }
        }
        return filter;
    }

    /**
     * @param filter the filter to set
     */
    public void setFilter(Filter filter) {
        this.filter = filter;
    }

    /**
     * @return the selected
     */
    public boolean isSelected() {
        return selected;
    }

    /**
     * @param selected the selected to set
     */
    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    /**
     * @return the children
     */
    public List<FilterSelectBean> getChildren() {
        if (children != null && !children.isEmpty()) {
            children.get(0).setFirstElement(true);
        }
        return children;
    }
    
    public int getChildrenCount() {
        if(getChildren()==null) {
            return 0;
        }
        else return getChildren().size();
    }

    /**
     * @param children the children to set
     */
    public void setChildren(List<FilterSelectBean> children) {
        this.children = children;
    }

    public PropertyInfo getInputDefinition() {
        return inputDefinition;
    }

    public void setInputDefinition(PropertyInfo inputDefinition) {
        this.inputDefinition = inputDefinition;
    }

    /**
     * @return the matchMode
     */
    public String getMatchMode() {
        if (filter instanceof LeafFilter) {
            Filter.MatchMode mm = ((LeafFilter) filter).getMatchMode();
            final String matchString;
            if (mm == null) {
                matchString = null;
            } else {
                matchString = getMessage("filter_match_mode_" + mm.toString());
            }
            return matchString;
        } else {
            return null;
        }
    }

    /**
     * @param matchMode the matchMode to set
     */
    public void setMatchMode(String matchMode) {
        if (filter instanceof LeafFilter) {
            ((LeafFilter) filter).setMatchMode(Enum.valueOf(Filter.MatchMode.class, matchMode));
        } 
    }


    /**
     * @return the firstElement
     */
    public boolean isFirstElement() {
        return firstElement;
    }

    /**
     * @param firstElement the firstElement to set
     */
    public void setFirstElement(boolean firstElement) {
        this.firstElement = firstElement;
    }
    
    public boolean isList() {
    	return list;
    }
    
    /**
     * Returns a list of SelectItems representing the operations available 
     * for the AttributeDefinition for this filter. If the filter does not have
     * an attribute set it will return a single empty value labeled
     * "No Operation Available".
     * 
     * @return List of select items 
     */
    public List<SelectItem> getOperationList(){
        ArrayList<SelectItem> operationList = new ArrayList<SelectItem>();

        if (inputDefinition != null && inputDefinition.getAllowedOperations() != null)                    
        {   
            for (Filter.LogicalOperation operation : inputDefinition.getAllowedOperations()) {
                String operationValue = operation.toString();
                String operationLabel = getMessage("filter_op_" + operationValue);
                operationList.add(new SelectItem(operationValue, operationLabel));
            }
        }  

        if (operationList.isEmpty()){
            String noOperationAvailable = getMessage(MessageKeys.FILTER_OP_UNAVAILABLE);
            operationList.add(new SelectItem("", noOperationAvailable));                            
        }  

        return operationList;
    }
    
    public boolean isListOperation() {
        final boolean isListOperation;
        if (filter instanceof LeafFilter) {
            Filter.LogicalOperation op = ((LeafFilter) filter).getOperation();
            isListOperation = 
                Filter.LogicalOperation.CONTAINS_ALL == op ||
                Filter.LogicalOperation.IN == op; 
        } else {
            isListOperation = false;
        }
                    
        return isListOperation;
    }  // isListOperation()

    public boolean isLikeOperation() {
        final boolean isLikeOperation;
        if (filter instanceof LeafFilter) {
            Filter.LogicalOperation op = ((LeafFilter) filter).getOperation();
            isLikeOperation = Filter.LogicalOperation.LIKE == op; 
        } else {
            isLikeOperation = false;
        }
    
        return isLikeOperation;
    }  // isLikeOperation()

    /**
    *
    * @return
    */
    public boolean isUnaryOperation() {
        final boolean isUnaryOperation;
        
        if (filter instanceof LeafFilter) {
            Filter.LogicalOperation op = ((LeafFilter) filter).getOperation();
            isUnaryOperation = 
                Filter.LogicalOperation.ISNULL == op ||
                Filter.LogicalOperation.NOTNULL == op; 
        } else {
            isUnaryOperation = false;
        }
    
        return isUnaryOperation;
    }  // isUnaryOperation()


    private Object coerceValue(Object origValue, String type) throws ParseException {
        Object retValue = origValue;

        if ( origValue == null )
            return null;

     // No conversion is needed for strings.
     // We should not see permissions here.
     // At some point we will likely have an encrypted data type
     //   in our application and will need to do something there.
        if ( ! PropertyInfo.TYPE_STRING.equals(type) &&
             ! PropertyInfo.TYPE_SECRET.equals(type) ) {

            if ( PropertyInfo.TYPE_BOOLEAN.equals(type) ) {
                if ( ! ( origValue instanceof Boolean ) ) {
                    retValue = Boolean.valueOf(origValue.toString());
                }
            } else if ( PropertyInfo.TYPE_DATE.equals(type) ) {
                if ( ! ( origValue instanceof Date ) ) {
                    retValue = Util.stringToDate(origValue.toString());
                }
            } else if ( PropertyInfo.TYPE_INT.equals(type) ) {
                if ( ! ( origValue instanceof Integer ) ) {
                    retValue = Integer.valueOf(origValue.toString());
                }
            } else if ( PropertyInfo.TYPE_LONG.equals(type) ) {
                if ( ! ( origValue instanceof Long ) ) {
                    retValue = Long.valueOf(origValue.toString());
                }
            }
        }

        return retValue;
    }  // coerceValue(Object, String)

     public void coerceValue() throws ParseException {
         if (filter instanceof LeafFilter) {
             LeafFilter leafFilter = (LeafFilter) filter;
             String valueType = PropertyInfo.TYPE_STRING;
             if ( inputDefinition != null ) {
                 valueType = inputDefinition.getValueType();
             }
        
             Object value = leafFilter.getValue();
             if ( value instanceof List ) {
                 List<Object> filterValues = new ArrayList<Object>();
                 for ( Object formValue : (List)value ) {
                     Object filterValue =
                         coerceValue(formValue, valueType);
                     filterValues.add(filterValue);
                 }
                 leafFilter.setValue(filterValues);
             } else {
                 Object filterValue = coerceValue(value, valueType);
                 leafFilter.setValue(filterValue);
             }
         }
     }
     
    /**       
     * If this is a LIKE/EXACT operation, then convert it to EQ as the Filter 
     * implementation does not support LIKE/EXACT correctly.        
     */
    @SuppressWarnings("unused")
    private void convertFilter() {
        if (filter instanceof LeafFilter) {
            LeafFilter leafFilter = (LeafFilter) filter;
            if (leafFilter.getOperation()!=null 
                    && leafFilter.getOperation().equals(Filter.LogicalOperation.LIKE) ) {
                Filter.MatchMode mode = leafFilter.getMatchMode();
                if ( mode != null && mode.equals(Filter.MatchMode.EXACT) ) {
                    leafFilter.setOperation(Filter.LogicalOperation.EQ);
                    leafFilter.setMatchMode(null);
                }           
            }
        }
    }  // convertFilter()

    /**
     * @return the displayable
     */
    public boolean isDisplayable() {
        return displayable;
    }

    /**
     * @param displayable the displayable to set
     */
    public void setDisplayable(boolean displayable) {
        this.displayable = displayable;
    }

	public Filter getJoin() {
		return join;
	}

	public void setJoin(Filter join) {
		this.join = join;
	}
}
