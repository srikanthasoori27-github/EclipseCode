/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.object;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.Filter.BooleanOperation;
import sailpoint.object.Filter.CompositeFilter;
import sailpoint.object.Filter.LeafFilter;
import sailpoint.tools.xml.AbstractXmlObject;
import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLProperty;
import sailpoint.web.FilterSelectBean;

/**
 * @exclude
 */
public class SearchItemFilter extends AbstractXmlObject {
	private static final Log log = LogFactory.getLog(SearchItemFilter.class);
	private String property;
	private String booleanOperation;
	private String logicalOperation;
	private boolean ignoreCase;
	private Object value;
	/**
	 * List of filters specified by the user to query the context for this search
	 */
	private Filter searchFilter;
	
	/**
	 * Any necessary joins for this search filter
	 */
	private Filter joinFilter;

	/** Search items are hierarchical, allowing them to be represented as a 
	 * composite filter on the ui that contain search items as children
	 */
	private List<SearchItemFilter> childSearchFilters;


	public SearchItemFilter() {
		// TODO Auto-generated constructor stub
	}

	public SearchItemFilter(String property, Object value) {
		this.property = property;
		this.value = value;
	}
	
	public SearchItemFilter(Filter filter) {
		if(filter instanceof LeafFilter) {
			LeafFilter f = (LeafFilter)filter;
			this.property = f.getProperty();
			this.value = f.getValue();
			this.logicalOperation = f.getOperation().name();
			this.ignoreCase = f.isIgnoreCase();
			this.searchFilter = f;
		} else if(filter instanceof CompositeFilter) {
			CompositeFilter f = (CompositeFilter)filter;
			this.booleanOperation = f.getOperation().name();
			this.childSearchFilters = new ArrayList<SearchItemFilter>();
			for(Filter child : f.getChildren()){
				childSearchFilters.add(new SearchItemFilter(child));
			}
		}
	}

	/** A convenience constructor, this can be called from the ui to build
	 * a search item filter from a filter select bean 
	 * @param filterBean
	 */
	public SearchItemFilter(FilterSelectBean filterBean) {
		if(filterBean!=null) {
			if(filterBean.isComposite() && filterBean.getChildren()!=null){
				this.booleanOperation = filterBean.getOperation();
				this.childSearchFilters = new ArrayList<SearchItemFilter>();
				for(FilterSelectBean child : filterBean.getChildren()){
					childSearchFilters.add(new SearchItemFilter(child));
				}
			}
			else {
				this.logicalOperation = filterBean.getOperation();
				this.property = filterBean.getProperty();
				this.value = filterBean.getValue();
				this.searchFilter = filterBean.getFilter();
				this.ignoreCase = filterBean.isIgnoreCase();
			}
			this.joinFilter = filterBean.getJoin();
		}
	}

	@XMLProperty
	public String getProperty() {
		return property;
	}

	public void setProperty(String property) {
		this.property = property;
	}

	@XMLProperty
	public Object getValue() {
		return value;
	}

	public void setValue(Object value) {
		this.value = value;
	}

	@XMLProperty(mode=SerializationMode.LIST)
	public List<SearchItemFilter> getChildSearchFilters() {
		return childSearchFilters;
	}

	public void setChildSearchFilters(List<SearchItemFilter> searchFilters) {
		this.childSearchFilters = searchFilters;
	}

	@XMLProperty
	public String getBooleanOperation() {
		if(booleanOperation==null)
			booleanOperation = BooleanOperation.AND.name();
		return booleanOperation;
	}

	public void setBooleanOperation(String booleanOperation) {
		this.booleanOperation = booleanOperation;
	}

	@XMLProperty
	public String getLogicalOperation() {
		return logicalOperation;
	}

	public void setLogicalOperation(String logicalOperation) {
		this.logicalOperation = logicalOperation;
	}


	/** Computes a filter for returning up to the SearchItem. If this SearchItemFilter
	 * contains more than one child SearchItemFilters, it will try to create a composite filter
	 * out of them. If it contains no child filters, it will return the filters that it currently
	 * contains in "getFilters()" **/
	public Filter getFilter() {
        /** If this filter is null and it has children, get the filter as a composite of their filters **/
        Filter filter = null;
        if(searchFilter==null) {
            List<Filter> filters = new ArrayList<Filter>();

            if(getChildSearchFilters() != null && !getChildSearchFilters().isEmpty()) {
                for(SearchItemFilter child : getChildSearchFilters()) {
                    filters.add(child.getFilter());
                }
                BooleanOperation op = Enum.valueOf(Filter.BooleanOperation.class, getBooleanOperation());
                if (log.isDebugEnabled())
                    log.debug("Creating Composite: Operation [" + op + "]");
                
                filter = new CompositeFilter(op, filters);
            }
            else if(!filters.isEmpty()) {
                filter = filters.get(0);
            }
        } else {
            filter = searchFilter;
        }
        
        if (log.isDebugEnabled())
            log.debug("New Filter: " + filter);
        
        return filter;
    }

	@XMLProperty
	public boolean isIgnoreCase() {
		return ignoreCase;
	}

	public void setIgnoreCase(boolean ignoreCase) {
		this.ignoreCase = ignoreCase;
	}

	@XMLProperty
	public Filter getSearchFilter() {
		return searchFilter;
	}

	public void setSearchFilter(Filter searchFilter) {
		this.searchFilter = searchFilter;
	}

	@XMLProperty
	public Filter getJoinFilter() {
		return joinFilter;
	}
	
	/** Gets all of the joins from this filter down to the joins of its children **/
	public List<Filter> getJoinFilters() {
		
		/** If this filter is null and it has children, get the joins of its children **/
		List<Filter> joins = null;
		if(joinFilter==null) {
			if(getChildSearchFilters()!=null && !getChildSearchFilters().isEmpty()) {
				joins = new ArrayList<Filter>();
				for(SearchItemFilter child : getChildSearchFilters()) {
                    if(child.getJoinFilters()!=null)
                        joins.addAll(child.getJoinFilters());
				}
			}
		} else{
			joins = new ArrayList<Filter>();
			joins.add(joinFilter);
		}
		return joins;
	}

	public void setJoinFilter(Filter joinFilter) {
		this.joinFilter = joinFilter;
	}
}
