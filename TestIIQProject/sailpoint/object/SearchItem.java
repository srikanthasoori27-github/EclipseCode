/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * 
 */
package sailpoint.object;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.Filter.BooleanOperation;
import sailpoint.object.Filter.CompositeFilter;
import sailpoint.tools.xml.AbstractXmlObject;
import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;

/**
 * @exclude
 * @author peter.holcomb
 * 
 * Object to hold the customized search that a user has created through the UI on the
 * slicer - dicer page.  Used similar to remembered bugzilla queries to allow users to 
 * rerun queries that they've built in the past.
 *
 */

@XMLClass
public class SearchItem extends AbstractXmlObject{
	private static final Log log = LogFactory.getLog(SearchItem.class);
    @XMLClass(xmlname="SearchItemType")
    public static enum Type {
        Activity,
        Identity,
        Audit,
        AdvancedAudit,
        Certification,
        AdvancedCertification,
        Role,
        AdvancedRole,
        AdvancedIdentity,
        // should we change this to ManagedAttribute?
        // not sure about the consequences - jsl
        AccountGroup,
        AdvancedAccountGroup,
        IdentityRequest,
        AdvancedIdentityRequest,
        IdentityRequestItem,
        Syslog,
        AdvancedSyslog,
        Link,
        AdvancedLink
    }
    
    /**
     * Name of the search
     */
    private String name;    
    /**
     * Description of the search
     */
    private String description;
    /**
     * Logical Operation of the search - This joins all the filters
     * in the list into one "And" or "Or" operation
     */
    private String operation;
    
    
    /** Type of the search **/
    private Type type;
    
    /**
     * List of column names to display in the table once the results are returned.
     */
    private List<String> identityFields;
    private List<String> activityFields;
    private List<String> riskFields;
    private List<String> auditFields;
    private List<String> certificationFields;
    private List<String> roleFields;
    private List<String> identityRequestFields;
    private List<String> identityRequestItemFields;
    private List<String> accountGroupFields;
    private List<String> searchTypeFields;
    private List<String> syslogFields;
    private List<String> linkFields;

    
    /**
     * List of search input definitions that allows the basic search to be rebuilt
     * from scratch
     */
    private List<SearchInputDefinition> inputDefinitions;
    
    
    private List<SearchItemFilter> searchFilters;
    
    /**
     * A list of filters derived from the underlying searchFilters.  
     * There are occasions when the filters need to be set on the search item
     * and not grabbed from the underlying search item filters
     */
    private List<Filter> calculatedFilters;
    
    /**
     * A list of filters that were converted from another search such as the
     * account group search. These are loaded and used in addition to the 
     * currently entered search item filters.
     */
    private boolean converted;
    private List<Filter> convertedFilters;

	
    /********************************************************************
     * 
     * Constructors
     * 
     * ******************************************************************/
    
    public SearchItem() {
    }
    
    public SearchItem(String name) {
        this.name = name;
    }
    
    /** Used by the Activity Policy to build a search item from its list of filters */
    public SearchItem(List<Filter> filters) {
    	if(filters!=null && !filters.isEmpty()) {
    		searchFilters = new ArrayList<SearchItemFilter>();
    		
    		/** If the only filter on this list is a composite filter, split it
    		 * and make the child filters filter their own child search item filters
    		 */
    		if(filters.size()==1 && (filters.get(0) instanceof CompositeFilter)) {
    			CompositeFilter composite = (CompositeFilter)filters.get(0);
    			operation = composite.getOperation().name();
    			for(Filter child : composite.getChildren()) {
    				SearchItemFilter childItemFilter = new SearchItemFilter(child);
    				searchFilters.add(childItemFilter);
    			}
    		} else {
    			for(Filter f : filters) {
    				SearchItemFilter itemFilter = new SearchItemFilter(f);
    				searchFilters.add(itemFilter);
    			}
    		}
    	}
    }
    /********************************************************************
     * 
     * Getters / Setters
     * 
     * ******************************************************************/

    /**
     * @return the name
     */
    @XMLProperty
    public String getName() {
        return name;
    }

    /**
     * @param name the name to set
     */
    public void setName(String name) {
        this.name = name;
    }
    
    
    
    /**
     * @param inputDefinitions the inputDefinitions to set
     */
    public void setInputDefinitions(List<SearchInputDefinition> inputDefinitions) {
        this.inputDefinitions = inputDefinitions;
    }

    /**
     * @return the components
     */
    @XMLProperty(mode=SerializationMode.LIST)
    public List<SearchInputDefinition> getInputDefinitions() {
        return inputDefinitions;
    }
    
    public void addInputDefinition(SearchInputDefinition def) {
        if(inputDefinitions==null) {
            inputDefinitions = new ArrayList<SearchInputDefinition>();
        }
        inputDefinitions.add(def);
    }
    
    public void addOrSetInputDefinition(SearchInputDefinition def) {
        if(inputDefinitions==null) {
            inputDefinitions = new ArrayList<SearchInputDefinition>();
            inputDefinitions.add(def);
        } else {
            for(Iterator<SearchInputDefinition> defIter = inputDefinitions.iterator(); defIter.hasNext(); ) {
                SearchInputDefinition thisDef = defIter.next();
                if(def.getName().equals(thisDef.getName())) {
                    defIter.remove();
                    break;
                }
            }
            inputDefinitions.add(def);
        }
        
    }

    /**
     * @return the description
     */
    @XMLProperty
    public String getDescription() {
        return description;
    }

    /**
     * @param description the description to set
     */
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * @return the activityFields
     */
    @XMLProperty(mode=SerializationMode.LIST)
    public List<String> getActivityFields() {
        return activityFields;
    }

    /**
     * @param activityFields the activityFields to set
     */
    public void setActivityFields(List<String> activityFields) {
        this.activityFields = activityFields;
    }

    /**
     * @return the identityFields
     */
    @XMLProperty(mode=SerializationMode.LIST)
    public List<String> getIdentityFields() {
        return identityFields;
    }

    /**
     * @param identityFields the identityFields to set
     */
    public void setIdentityFields(List<String> identityFields) {
        this.identityFields = identityFields;
    }

    /**
     * @return the riskFields
     */
    @XMLProperty(mode=SerializationMode.LIST)
    public List<String> getRiskFields() {
        return riskFields;
    }

    /**
     * @param riskFields the riskFields to set
     */
    public void setRiskFields(List<String> riskFields) {
        this.riskFields = riskFields;
    }

    /**
     * @return the auditFields
     */
    @XMLProperty(mode=SerializationMode.LIST)
    public List<String> getAuditFields() {
        return auditFields;
    }

    /**
     * @param auditFields the auditFields to set
     */
    public void setAuditFields(List<String> auditFields) {
        this.auditFields = auditFields;
    }

    /**
     * @return the type
     */
    @XMLProperty
    public Type getType() {
        return type;
    }

    /**
     * @param type the type to set
     */
    public void setType(Type type) {
        this.type = type;
    }
    
    public void setTypeValue(String typeString) {
        for(Type type : Type.values()) {
            if(type.name().equals(typeString)) {
                this.type = type;
            }
        }
    }

    /**
     * @return the operation
     */
    @XMLProperty
    public String getOperation() {
    	if(operation==null)
			operation = BooleanOperation.AND.name();
        return operation;
    }

    /**
     * @param operation the operation to set
     */
    public void setOperation(String operation) {
        this.operation = operation;
    }

    @XMLProperty(mode=SerializationMode.LIST)
	public List<SearchItemFilter> getSearchFilters() {
		return searchFilters;
	}

	public void setSearchFilters(List<SearchItemFilter> searchFilters) {
		this.searchFilters = searchFilters;
	}
	
	public void addSearchFilter(SearchItemFilter searchFilter) {
		if(searchFilters==null)
			searchFilters = new ArrayList<SearchItemFilter>();
		searchFilters.add(searchFilter);
	}
	
	/** Builds a list of filters from the filters contained by all of the
	 * SearchItemFilters */
	public List<Filter> getFilters() {
		List<Filter> filters = null;
		
		if (log.isDebugEnabled())
		    log.debug("SearchItem Children: " + getSearchFilters());
		
		if(getSearchFilters()!=null && !getSearchFilters().isEmpty()) {
			filters = new ArrayList<Filter>();
			for(SearchItemFilter searchFilter : getSearchFilters()){
			    if (log.isDebugEnabled())
			        log.debug("Getting Filters: " + searchFilter.getFilter());
			    
				filters.add(searchFilter.getFilter());
			}
		}
		
		if (log.isDebugEnabled())
		    log.debug("SearchItem Filters: " + filters);
		
		return filters;		
	}
	
	/** Builds a list of joins from the joins contained by all of the
	 * SearchItemFilters */
	public List<Filter> getJoins() {
		
		List<Filter> joins = null;
		if(getSearchFilters()!=null && !getSearchFilters().isEmpty()) {			
			joins = new ArrayList<Filter>();
			for(SearchItemFilter searchFilter : getSearchFilters()){
			    if (log.isDebugEnabled())
			        log.debug("Getting Filters: " + searchFilter.getFilter());
				
				if(searchFilter.getJoinFilters()!=null)				
					joins.addAll(searchFilter.getJoinFilters());
			}
		}
		return joins;
	}
	
	@XMLProperty(mode=SerializationMode.LIST)
	public List<String> getCertificationFields() {
		return certificationFields;
	}

	public void setCertificationFields(List<String> certificationFields) {
		this.certificationFields = certificationFields;
	}
    
    @XMLProperty(mode=SerializationMode.LIST)
    public List<String> getRoleFields() {
        return roleFields;
    }

    public void setRoleFields(List<String> roleFields) {
        this.roleFields = roleFields;
    }
	
	@XMLProperty(mode=SerializationMode.LIST)
	public List<String> getSearchTypeFields() {
		return searchTypeFields;
	}

	public void setSearchTypeFields(List<String> searchTypeFields) {
		this.searchTypeFields = searchTypeFields;
	}
	
	public List<String> getSelectedFields() {
		List<String> selectedFields = new ArrayList<String>();
		if (this.type != null) {
    		if(getActivityFields()!=null && this.type.equals(Type.Activity))
    			selectedFields.addAll(getActivityFields());
    		if(getIdentityFields()!=null && this.type.equals(Type.Identity) || this.type.equals(Type.AdvancedIdentity))
    			selectedFields.addAll(getIdentityFields());
    		if(getRiskFields()!=null && this.type.equals(Type.Identity) || this.type.equals(Type.AdvancedIdentity))
    			selectedFields.addAll(getRiskFields());
    		if(getAuditFields()!=null && this.type.equals(Type.Audit))
    			selectedFields.addAll(getAuditFields());
            if(getRoleFields()!=null && this.type.equals(Type.Role))
                selectedFields.addAll(getRoleFields());
    		if(getCertificationFields()!=null && this.type.equals(Type.Certification))
    			selectedFields.addAll(getCertificationFields());
    		if(getSearchTypeFields()!=null && this.type.equals(Type.Certification))
    			selectedFields.addAll(getSearchTypeFields());
            if(getAccountGroupFields()!=null && this.type.equals(Type.AccountGroup))
                selectedFields.addAll(getAccountGroupFields());
            if(getIdentityRequestFields()!=null && this.type.equals(Type.IdentityRequest))
                selectedFields.addAll(getIdentityRequestFields());
            if(getIdentityRequestItemFields()!=null && this.type.equals(Type.IdentityRequest))
                selectedFields.addAll(getIdentityRequestItemFields());
            if(getSyslogFields()!=null && this.type.equals(Type.Syslog))
                selectedFields.addAll(getSyslogFields());
            if(getLinkFields()!=null && this.type.equals(Type.Link))
                selectedFields.addAll(getLinkFields());
		}
    		
		return selectedFields;
	}

    public List<Filter> getCalculatedFilters() {
        if(calculatedFilters==null) {
            calculatedFilters = getFilters();
        }
        return calculatedFilters;
    }

    public void setCalculatedFilters(List<Filter> calculatedFilters) {
        this.calculatedFilters = calculatedFilters;
    }

    @XMLProperty(mode=SerializationMode.LIST)
    public List<String> getAccountGroupFields() {
        return accountGroupFields;
    }

    public void setAccountGroupFields(List<String> accountGroupFields) {
        this.accountGroupFields = accountGroupFields;
    }
    
    @XMLProperty
    public boolean isConverted() {
        return converted;
    }

    public void setConverted(boolean converted) {
        this.converted = converted;
    }

    @XMLProperty(mode=SerializationMode.LIST)
    public List<Filter> getConvertedFilters() {
        return convertedFilters;
    }

    public void setConvertedFilters(List<Filter> convertedFilters) {
        this.convertedFilters = convertedFilters;
    }
    
    @XMLProperty(mode=SerializationMode.LIST)
    public List<String> getIdentityRequestFields() {
        return identityRequestFields;
    }

    public void setIdentityRequestFields(List<String> identityRequestFields) {
        this.identityRequestFields = identityRequestFields;
    }

    @XMLProperty(mode=SerializationMode.LIST)
    public List<String> getIdentityRequestItemFields() {
        return identityRequestItemFields;
    }

    public void setIdentityRequestItemFields(List<String> identityRequestItemFields) {
        this.identityRequestItemFields = identityRequestItemFields;
    }

    @XMLProperty(mode=SerializationMode.LIST)
    public List<String> getSyslogFields() {
        return syslogFields;
    }

    public void setSyslogFields(List<String> syslogFields) {
        this.syslogFields = syslogFields;
    }

    @XMLProperty(mode=SerializationMode.LIST)
    public List<String> getLinkFields() {
        return linkFields;
    }

    public void setLinkFields(List<String> linkFields) {
        this.linkFields = linkFields;
    }
}
