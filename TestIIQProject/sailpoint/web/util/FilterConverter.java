/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.Filter;
import sailpoint.object.SailPointObject;
import sailpoint.object.SearchInputDefinition;
import sailpoint.object.Filter.BooleanOperation;
import sailpoint.object.Filter.CompositeFilter;
import sailpoint.object.Filter.LeafFilter;
import sailpoint.object.Filter.LogicalOperation;
import sailpoint.search.ExtendedAttributeVisitor;
import sailpoint.tools.GeneralException;
import sailpoint.web.BaseBean;
import sailpoint.web.search.SearchBean;

public class FilterConverter extends BaseBean {
	
	private static final Log log = LogFactory.getLog(FilterConverter.class);
	
	public FilterConverter() {}
    
    /** Here's where the magic happens.  First, we need to clone the filters
     * in case hibernate decides to get cute and modifies the filters. That way
     * we can return the unchanged filters back to the user in case they want to edit them.
     */
    public static List<Filter> convertAndCloneFilters(List<Filter> filters, String operation, 
    		Map<String, SearchInputDefinition> inputs) {
        List<Filter> newFilters = null;
        if(filters!=null) {
        	newFilters = new ArrayList<Filter>();
	        /** Clone the list of filters first...**/
	        for(Filter f : filters) {
	            Filter clonedFilter = Filter.clone(f);
	            if(clonedFilter instanceof LeafFilter && ((LeafFilter)clonedFilter).getJoinProperty()!=null) 
	                newFilters.add(0, clonedFilter);
	            else
	                newFilters.add(clonedFilter);
	        }
	        
	        /** Next, we need to look at the list of filters to see if the list contains
	         * two or more filters for the same hibernate property.  If it does, and those
	         * filters apply to a collection, we need to convert them to a collectionCondition
	         * filter. Weeeee!!! */
	        if(newFilters!=null) {
	            newFilters = convertCollectionFilters(newFilters, 
	                    Enum.valueOf(Filter.BooleanOperation.class, operation), 
	                    inputs);
	        }
	        
	        /** Finally, we need to find any filters that have a calculated property name on them
	         * such as SPT_ and convert them to a filter that hibernate can understand 
	         */
	        newFilters = convertCalculatedFilters(newFilters);
        }
        return newFilters;
    }
    
    /** Searches through the list of filters provided to find any filters that may apply to 
     * a hibernate collection object.  If there are any, it puts them in a map and returns */
    private static boolean findCollectionFilters(List<Filter> filters, Map<String, List<Filter>> collections, 
    		List<Filter> newFilters, Map<String, SearchInputDefinition> inputs) {
        boolean foundCollection = false;
        
        for(Filter f : filters) {
            if(f instanceof LeafFilter) {
                String property = ((LeafFilter)f).getProperty();
                SearchInputDefinition def = SearchBean.getDefFromInputsByProperty(property, inputs);
                if(def!=null && 
                   def.getPropertyType().equals(SearchInputDefinition.PropertyType.Collection) &&
                   (((LeafFilter)f).getCollectionCondition()==null) ) {
                    
                    log.debug("Found Collection Filter: " + f);
                    List<Filter> theseFilters = collections.get(property);
                    if(theseFilters==null) {
                        theseFilters = new ArrayList<Filter>();
                    }                    
                    theseFilters.add(f);
                    
                    /** If we have found two filters on the same collection, we know we will
                     * have to handle these so return a true flag to tell the caller to
                     * expect to have to handle these filters */
                    if(theseFilters.size()>1)
                        foundCollection = true;
                    
                    collections.put(property, theseFilters);
                } else {
                    if(((LeafFilter)f).getJoinProperty()!=null) 
                        newFilters.add(0, f);
                    else 
                        newFilters.add(f);
                }
            } else if(f instanceof CompositeFilter){
                List<Filter> children = ((CompositeFilter)f).getChildren();
                BooleanOperation op = ((CompositeFilter)f).getOperation();
                List<Filter> newChildren = convertCollectionFilters(children,op,inputs);
                ((CompositeFilter)f).setChildren(newChildren);
                newFilters.add(f);
            }
        }
        return foundCollection;
    }
    
    /** This function uses reflection to check to see if the list of filters contains more than
     * one filter to apply to a collection property in hibernate.  If it does, it takes that list
     * of filters and creates a collection condition out of them. */
    public static List<Filter> convertCollectionFilters(List<Filter> filters, BooleanOperation op, 
    		Map<String, SearchInputDefinition> inputs) {
        Map<String, List<Filter>> collections = new HashMap<String, List<Filter>>();
        List<Filter> newFilters = new ArrayList<Filter>();
        
        findCollectionFilters(filters, collections, newFilters, inputs);
        
        
        log.debug("Collections: " + collections);
        if(!collections.isEmpty()) {
            Set<String> keys = collections.keySet();
            for(String property : keys) {
                List<Filter> colFilters = collections.get(property);
                
                /** If we found a list of filters that is greater than 1 in size, create the collection Filter. */
                if(colFilters.size()>1) {
                	newFilters.add(convertFiltersToCollection(colFilters, property, op));
                } else {
                    
                    Filter f = colFilters.get(0);
                    if(f instanceof LeafFilter) {
                        LeafFilter leaf = (LeafFilter)f;
                        /** Need to handle not equals as a collection condition **/
                        if(leaf.getOperation().equals(LogicalOperation.NE)) {
                        	newFilters.add(convertFiltersToCollection(colFilters, property, op));
                        }
                        else if(((LeafFilter)f).getJoinProperty()!=null) {
                        	newFilters.add(0, f);
                        }
                        else {
                        	newFilters.add(f);                        	
                        }
                    } else {
                        newFilters.add(f);
                    }
                }
            }
        }
        
        return newFilters;
    }
    
    public static Filter convertFiltersToCollection(List<Filter> filters, String property, BooleanOperation op) {
    	log.debug("Col Filters Size:" + filters.size());
    	Filter collectionFilter = null;
    	
        /** Split the property name **/
        String[] parts = property.split("\\.");
        log.debug("Property: " + property + " Parts Size: " + parts.length);
        
        if(parts.length>1) {
            String base = parts[0];
            log.debug("Base: " + base);
            String rest = property.substring(base.length()+1);
            log.debug("Rest: " + rest);
            Iterator<Filter> filterIter = filters.iterator();
            
            /** Convert the filters into a new collectionCondition Filter; **/
            while(filterIter.hasNext()) {
                LeafFilter temp = (LeafFilter)filterIter.next();
                temp.setProperty(rest);
                log.debug("New Filters: " + temp);
                
            }
            Filter newFilter = null;
            if(filters.size()>1) {
            	newFilter =	new CompositeFilter(op, filters);
            	collectionFilter = Filter.collectionCondition(base, newFilter);
            } else {
            	newFilter = filters.get(0);
            	if(newFilter instanceof CompositeFilter) {
            		collectionFilter = Filter.collectionCondition(base, newFilter);
            	} else if( newFilter instanceof LeafFilter 
            			&& ((LeafFilter)newFilter).getOperation().equals(LogicalOperation.NE)) {
            		((LeafFilter)newFilter).setOperation(LogicalOperation.EQ);
            		collectionFilter = Filter.collectionCondition(base, Filter.not(newFilter));            		
            	}
            }
            
            
            log.debug("New Collection Filter: " +  collectionFilter);                
        }
        return collectionFilter;
    }
    
    
    /** Looks for the calculated prefix on each of the filter's properties and strips it off
     * so that the filters are processable by hibernate.
     * @param filters
     * @return
     */
    private static List<Filter> convertCalculatedFilters(List<Filter> filters) {
    	for(Filter f : filters) {
            if(f instanceof LeafFilter) {
                String property = ((LeafFilter)f).getProperty();
        		/** Convert calculated properties - trip the SPT_ off the front of the property**/
        		if(property.startsWith(SearchBean.CALCULATED_COLUMN_PREFIX)) {
        			((LeafFilter)f).setProperty(property.substring(SearchBean.CALCULATED_COLUMN_PREFIX.length()));
        		}
                
            } else if(f instanceof CompositeFilter){
                List<Filter> children = ((CompositeFilter)f).getChildren();
                List<Filter> newChildren = convertCalculatedFilters(children);
                ((CompositeFilter)f).setChildren(newChildren);
            }
    	}
    	return filters;
    }
    
    public static <T extends SailPointObject> List<Filter> convertFilters(List<Filter> filters, Class<T> clazz, String prefix) {
        List<Filter> convertedFilters = null;
        if(filters!=null) {
            convertedFilters = new ArrayList<Filter>();
            for(Filter filter : filters) {
                Filter newFilter = convertFilter(filter, clazz, prefix);
                convertedFilters.add(newFilter);
            }
        }
        return convertedFilters;
    }
    
    /**
     * This method makes sure that the extended attribute names are
     * converted to property names first before appending the prefix.
     * @param <T>
     * @param filter
     * @param The class that this filter belongs to e.g., Identity.class
     * @return
     * @throws GeneralException
     */
    public static <T extends SailPointObject> Filter convertFilter(Filter filter, Class<T> clazz) {

        return convertFilter(filter, clazz, null);
    }

    /**
     * This method makes sure that the extended attribute names are
     * converted to property names first before appending the prefix.
     * @param <T>
     * @param filter
     * @param clazz The class that this filter belongs to e.g., Identity.class
     * @param propertyName the property name such as "identity" by which the calling class refers it
     * it could be null
     * @return
     * @throws GeneralException
     */
    public static <T extends SailPointObject> Filter convertFilter(Filter filter, Class<T> clazz, String propertyName) {

        ExtendedAttributeVisitor visitor = new ExtendedAttributeVisitor(clazz);
        try {
            filter.accept(visitor);
        } catch (GeneralException ex) {
            log.error(ex);
        }

        String prefix;
        if (propertyName == null) {
            prefix = clazz.getSimpleName() + "."; 
        } else {
            prefix = propertyName + ".";
        }
        
        return convertFilter(filter, prefix);
    }
    
    /** Converts a filter to use a prefix so that it can be used by other queries **/
    private static Filter convertFilter(Filter filter, String prefix) {
        Filter newFilter = null;
        
        if(filter instanceof LeafFilter) {
            
            LeafFilter leaf = (LeafFilter)filter;
            String property = leaf.getProperty();
            String newProperty = prefix+leaf.getProperty();
            
            /** If the property starts with a join property, we should not attempt to convert it **/
            if(Character.isUpperCase(property.charAt(0)))
                newProperty = property;
            
            LeafFilter newLeaf = new LeafFilter(leaf.getOperation(), newProperty, leaf.getValue());
            
            if(leaf.getJoinProperty()!=null) {
                newLeaf.setJoinProperty(leaf.getJoinProperty());
            }
            
            if(leaf.getCollectionCondition()!=null) {
                /** if the property starts with a join, its capitalized, when need to convert the
                 * collection condition as well since it's a join.  Otherwise, just set the collection condition.
                 * bug 4598.
                 */
                if(Character.isUpperCase(property.charAt(0))){
                    newLeaf.setCollectionCondition((CompositeFilter)convertFilter(leaf.getCollectionCondition(), prefix));
                } else {
                    newLeaf.setCollectionCondition(leaf.getCollectionCondition());
                }
            }
            
            newLeaf.setMatchMode(leaf.getMatchMode());
            
            newLeaf.setIgnoreCase(leaf.isIgnoreCase());
            
            newFilter = newLeaf;
            
        } else if(filter instanceof CompositeFilter){
            CompositeFilter composite = (CompositeFilter)filter;
            List<Filter> children = composite.getChildren();
            List<Filter> newChildren = new ArrayList<Filter>();
            if(children!=null) {
                for(int i=0; i<children.size(); i++) {
                    Filter child = children.get(i);
                    newChildren.add(convertFilter(child, prefix));
                }
            }
            newFilter = new CompositeFilter(composite.getOperation(), newChildren);
        }
        return newFilter;
    }
    

}
