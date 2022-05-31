/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.util;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import javax.faces.application.FacesMessage;
import javax.faces.context.FacesContext;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.Bundle;
import sailpoint.object.Filter;
import sailpoint.object.PropertyInfo;
import sailpoint.object.QueryOptions;
import sailpoint.object.Filter.CompositeFilter;
import sailpoint.object.Filter.LeafFilter;
import sailpoint.object.Filter.LogicalOperation;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Parser;
import sailpoint.tools.Util;
import sailpoint.web.BaseBean;
import sailpoint.web.FilterContainer;
import sailpoint.web.FilterSelectBean;
import sailpoint.web.search.IdentitySearchBean;

public class FilterHelper extends BaseBean{ 
    // No constructor needed because these are just a bunch of utility methods
    private FilterHelper(){}

    private static final Log log = LogFactory.getLog(FilterHelper.class);

    /**
     * Combines the selected LeafFilters into a CompositeFilter, as designated by 
     * the globalBooleanOp
     * @param backingBean FilterContainer that is performing the action
     * @return false if the grouped filter comes up empty; true otherwise
     */
    public static boolean groupFilters(FilterContainer backingBean) {
        final boolean persisted;

        List<FilterSelectBean> filterBeans = backingBean.getFilterBeans();

        if(filterBeans != null) {
            /**We only want to allow grouping of composite filters up to two levels...so if
             * we encounter a composite filter that has a composite filter as a child, we'll
             * return and throw an error.
             */
            for(FilterSelectBean filterBean : filterBeans) {
                if(filterBean.isSelected() && filterBean.getChildren()!=null){
                    for(FilterSelectBean childBean : filterBean.getChildren()) {
                        if(childBean.getChildren()!=null) {
                            addErrorMessage(backingBean.getFacesContext(), "Filters that contain more than one level of " +
                                    "composite filters cannot be grouped with other filters.", null);
                            return false;
                        }
                    }
                }
            }

            List<FilterSelectBean> beans = new ArrayList<FilterSelectBean>();            
            
            /** Find each selected filter bean and add it to a list of filter beans that will be the group **/
            for(Iterator<FilterSelectBean> filterIter = filterBeans.iterator(); filterIter.hasNext(); ) {
                FilterSelectBean filterBean = filterIter.next();
                if(filterBean.isSelected()) {
                    beans.add(filterBean);
                }
            }
            
            /** Build the new filter and set its children to the beans that were
             * selected for grouping */
            if(beans.size() > 1) {                
                String globalBooleanOp = backingBean.getGlobalBooleanOp();
                FilterSelectBean newBean = new FilterSelectBean();
                newBean.setOperation(globalBooleanOp);
                newBean.setComposite(true);
                
                beans.get(0).setFirstElement(true);
                newBean.setChildren(beans);
                filterBeans.add(newBean);
                
                persisted = true;
            } else {
                persisted = false;
            }
        } else {
            persisted = false;
        }
        
        /** Now that we have added the selected filters to the group, we want to remove the
         * individual filters */
        if(persisted){
            removeFilters(backingBean);
            filterBeans.get(0).setFirstElement(true);
        }
        
        backingBean.setFilterString(null);
        return persisted;
    }

    /**
     * Deletes the selected CompositeFilter and splits its children off into multiple 
     * LeafFilters
     * @param backingBean FilterContainer that is performing the action
     * @return false if the grouped filter comes up empty; true otherwise
     */
    public static boolean ungroupFilters(FilterContainer backingBean) {
        boolean ungrouped = false;

        List<FilterSelectBean> filterBeans = backingBean.getFilterBeans(); 
        
        if(filterBeans != null && !filterBeans.isEmpty()) {            
            /**We only want to allow ungrouping of composite filters so if any
             * of the filterBeans that are selected don't have children, return
             * an error.
             */
            for(FilterSelectBean filterBean : filterBeans) {
                if(filterBean.isSelected() && filterBean.getChildren()==null){                    
                    //addErrorMessage(backingBean.getFacesContext(), "Only composite filters may be ungrouped.", null);
                    return false;
                }
            }

            List<FilterSelectBean> beans = new ArrayList<FilterSelectBean>();
            for(Iterator<FilterSelectBean> filterIter = filterBeans.iterator(); filterIter.hasNext(); ) {
                FilterSelectBean filterBean = filterIter.next();
                if(filterBean.isSelected()) {
                	/**
                     * Break up the composite filter, create filter beans, and then add the new
                     * filters to the search item
                     */
                    for(FilterSelectBean child : filterBean.getChildren()) {
                    	/** Unselect the child so that it doesn't get removed by removeFilters() **/
                    	child.setSelected(false);
                    	child.setFirstElement(false);
                        beans.add(child);
                        ungrouped = true;
                    }
                }
            }
            filterBeans.addAll(beans);
            
            /** Remove the selected filters that were ungrouped **/
            if(ungrouped)
            	removeFilters(backingBean);
            filterBeans.get(0).setFirstElement(true);
            backingBean.setFilterString(null);
            
        }

        return ungrouped;
    }

    /** Method takes as input a string representation of a filter and converts it into 
     * a list of filterbeans and filters and sets the backing bean accordingly.  Used for
     * editing the filter source directly **/
    public static String convertStringToFilters(FilterContainer backingBean) {

    	
        Filter newFilter = compileFilterFromString(backingBean);
        
        log.debug("New Filter: " + newFilter);
        if(newFilter==null) {
            return null;
        }
        
        List<FilterSelectBean> filterBeans = backingBean.getFilterBeans();

        if(backingBean.getFilters()!=null)
            backingBean.getFilters().clear();

        if(newFilter instanceof CompositeFilter) {
            List<FilterSelectBean> newFilterBeans = new ArrayList<FilterSelectBean>();
            List<Filter> children = ((CompositeFilter)newFilter).getChildren();

            if(children.size() == 1) {
                FilterSelectBean newBean = new FilterSelectBean(children.get(0), backingBean.getPropertyInfo());

                filterBeans.clear();
                filterBeans.add(newBean);
                backingBean.addFilter(newFilter);
                backingBean.setGlobalBooleanOp(((CompositeFilter)newFilter).getOperation().name());
            }
            else {
                for(Filter child : children) {
                    FilterSelectBean newBean = new FilterSelectBean(child, backingBean.getPropertyInfo());
                    newFilterBeans.add(newBean);
                    backingBean.addFilter(child);
                }
    
                filterBeans.clear();
                filterBeans.addAll(newFilterBeans);
                backingBean.setGlobalBooleanOp(((CompositeFilter)newFilter).getOperation().name());
            }
        }
        else {
            FilterSelectBean newBean = new FilterSelectBean(newFilter, backingBean.getPropertyInfo());
            log.debug("Name: " + newBean.getProperty());

            filterBeans.clear();
            filterBeans.add(newBean);
            backingBean.addFilter(newFilter);
        }
        backingBean.setFilterString(null);
        filterBeans.get(0).setFirstElement(true);
        
        return "success";
    }

    /** Method takes as input a string representation of a filter and converts it into 
     * a new filter.  Used for editing the filter source directly **/
    public static Filter compileFilterFromString(FilterContainer backingBean) {
        String filterString = backingBean.getFilterString();
        log.debug("Filter String: " + filterString);
        Filter newFilter = null;
        try {
            newFilter = Filter.compile(filterString);

        } catch(Parser.ParseException pe) {
            return null;
        }
        return newFilter;
    }
    
    /** Used on the ui to show the filter source to the user for editing **/
    public static String showSource(FilterContainer backingBean) {
        backingBean.setFilterString(null);
        backingBean.setShowFilterSource(true);
        return "showSource";
    }
    
    /** Used on the ui to hide the filter source from the user for editing **/
    public static String hideSource(FilterContainer backingBean) {
        backingBean.setFilterString(null);
        backingBean.setShowFilterSource(false);
        return "hideSource";
    }


    /**
     * Synchronizes the available operations with a newly changed property name
     * @param backingBean FilterContainer that is performing the action
     */
    public static void refreshFilterAction(FilterContainer backingBean) {
        final PropertyInfo propertyInfo = backingBean.getPropertyInfoForField();
        //bug#18459 -- propertInfo might be null if user selects dummy option
        if (propertyInfo == null) {
            backingBean.setFilterLogicalOp(null);
        } else {
            final List<LogicalOperation> allowedOps = propertyInfo.getAllowedOperations();
            final String operation = backingBean.getFilterLogicalOp();
    
            if (operation == null || operation.isEmpty() || !allowedOps.contains(LogicalOperation.valueOf(operation))) {
                backingBean.setFilterLogicalOp(allowedOps.get(0).toString());
            }
        }
    }
    
    /**
     * Seletes the selected Filters
     * @param backingBean FilterContainer that is performing the action
     */
    public static void removeFilters(FilterContainer backingBean) {
        List<FilterSelectBean> filterBeans = backingBean.getFilterBeans(); 

        log.debug("Filter beans before remove: " + backingBean.getFilterBeans().toString());
        log.debug("Filters before remove: " + backingBean.getFilters());

        if(filterBeans != null && !filterBeans.isEmpty()) {
            for(Iterator<FilterSelectBean> filterIter = filterBeans.iterator(); filterIter.hasNext();) {
                FilterSelectBean filterBean = filterIter.next();
                if(filterBean.isSelected()) {
                	filterIter.remove();
                }
            }
        }
        backingBean.setFilterString(null);
        log.debug("Filters after remove: " + backingBean.getFilters());
    }
    
    /** Takes a list of filters and checks for the existence of filters on business roles.
     * Because business roles are now hierarchical, searching for any business role in particular
     * means that we also have to search for any of its parents **/
    public static List<Filter> getExtraBundleFilters(List<Filter> filters, String collection, BaseBean bean) {
        List<Filter> extraBundleFilters = null;
        for(Iterator<Filter> filterIter = filters.iterator(); filterIter.hasNext();) {
            Filter filter = filterIter.next();

            /** Handle Composite Filter **/
            if(filter instanceof CompositeFilter) {
                CompositeFilter composite = (CompositeFilter)filter;
                List<Filter> childExtras = getExtraBundleFilters(composite.getChildren(), collection, bean);
                if(childExtras!=null) {

                    /** If we had to swap out filters in the child of this filter, we'll need to
                     * build a new composite filter and return **/
                    for(Filter child : composite.getChildren()) 
                    {
                        if(child!=null)
                            childExtras.add(child);
                    }

                    if(extraBundleFilters==null)
                        extraBundleFilters = new ArrayList<Filter>();
                    filterIter.remove();
                    if(childExtras.size()>1) {
                        CompositeFilter newComposite = new CompositeFilter(composite.getOperation(), childExtras);
                        extraBundleFilters.add(newComposite);
                    } else if(!childExtras.isEmpty()){
                        extraBundleFilters.add(childExtras.get(0));
                    }
                }
            }

            /** Handle Leaf Filter **/
            else if(filter instanceof LeafFilter) {
                try {
                    LeafFilter leaf = (LeafFilter)filter;
                    
                    /** If the property of the filter does not match the collection we are building the hierarchy
                     * for, return null.  This prevents hierarchies over "assignedRole" from being applied to "businessRole"
                     * Special exception for "id" as it can get passed down from a collection condition query
                     */
                    if(!leaf.getProperty().startsWith(collection) && !leaf.getProperty().equals("id"))
                        return null;
                    
                    /** If this is a not equals search, return **/
                    if(leaf.getOperation().equals(Filter.LogicalOperation.NE))
                        return null;
                    
                    List<Filter> parentBundleFilters = new ArrayList<Filter>();
                    /** If this is a collection condition filter we need to pull the composite filter out it
                     * and build collection condition filters for each of its component parts **/
                    if(leaf.getCollectionCondition()!=null) {
                        filterIter.remove();
                        CompositeFilter collectionCondition = leaf.getCollectionCondition();
                        List<Filter> newFilters = getExtraBundleFilters(collectionCondition.getChildren(), collection, bean);
                        return(newFilters);
                    }
                    else {
                        /** Handle bundles.name coming from advanced search **/
                        if(leaf.getProperty().equals(IdentitySearchBean.PROPERTY_BUNDLES_NAME) || leaf.getProperty().equals(IdentitySearchBean.PROPERTY_ASSIGNED_ROLES_NAME)) {
                            /** Remove this filter from the list...not needed **/
                            filterIter.remove();
    
                            /** Build new list of filters on the bundle object **/
                            QueryOptions qo = new QueryOptions().add(Filter.like("name", leaf.getValue()));
                            List<Bundle> bundles = bean.getContext().getObjects(Bundle.class, qo);
                            if(bundles!=null) {
                                for(Bundle bundle : bundles) {
                                    parentBundleFilters.addAll(getParentBundleFilters(bundle, bean));                                
                                }
                            }
                        } else if((leaf.getProperty().equals(IdentitySearchBean.PROPERTY_BUNDLES_ID) || leaf.getProperty().equals(IdentitySearchBean.PROPERTY_ASSIGNED_ROLES_ID) || leaf.getProperty().equals("id")) && leaf.getValue()!=null) {
                            /** Remove this filter from the list...not needed **/
                            filterIter.remove();
                            Bundle bundle = bean.getContext().getObjectById(Bundle.class, (String)leaf.getValue());
                            if(bundle!=null)
                                parentBundleFilters.addAll(getParentBundleFilters(bundle, bean));
                        }
                    }

                    /** Take the list of new parent bundle filters, convert it to a 
                     * collection filter, and store it in the new filters to return **/
                    if(!parentBundleFilters.isEmpty()) {
                        if(extraBundleFilters==null)
                            extraBundleFilters = new ArrayList<Filter>();
                        extraBundleFilters.add(
                                Filter.collectionCondition(collection, 
                                        Filter.or(parentBundleFilters)));
                    }
                } catch(GeneralException ge) {
                    log.info("Exception encountered while fetching extra bundle filters.  Exception: " + ge.getMessage());
                }
            }
        }

        return extraBundleFilters;
    }

    /** takes as an input a bundle and gets all the bundles that are a parent of this bundle **/
    public static List<Filter> getParentBundleFilters(Bundle bundle, BaseBean bean) throws GeneralException {
        List<Filter> parentBundleFilters = new ArrayList<Filter>();
        List<Bundle> extras = bundle.getHierarchy(bean.getContext());
        for(Bundle b : extras) {      
            parentBundleFilters.add(Filter.eq("id", b.getId()));
        }
        return parentBundleFilters;
    }

    /**
     * @return Map of value/label pairs representing the possible Filter.MatchModes that 
     * apply to Filters with 'Like' operations 
     */
    public static Map<String, String> getMatchModeValues(Locale locale) {

        Map<String, String> values = new TreeMap<String, String>();
        for ( Filter.MatchMode mm : Filter.MatchMode.values() ) {
            Message mode = new Message("filter_match_mode_" + mm.toString());
            values.put(
                    mode.getLocalizedMessage(locale, null),
                    mm.toString());
        }
        return values;
    }

    private static void addErrorMessage(FacesContext context, String summary, String detail) {
        FacesMessage fm = 
            new FacesMessage(FacesMessage.SEVERITY_ERROR, summary, detail);
        context.addMessage(null, fm);
    }
    
    public static Filter getEqFilter(String prop, Object val)
    {
        if (null != val)
            return Filter.eq(prop, val);
        return null;
    }

    public static Filter and(Filter f1, Filter f2)
    {
        if (null == f1)
            return f2;
        if (null == f2)
            return f1;
        return Filter.and(f1, f2);
    }

    public static Filter or(Filter f1, Filter f2)
    {
        if (null == f1)
            return f2;
        if (null == f2)
            return f1;
        return Filter.or(f1, f2);
    }
    
    public static Filter getFilterFromList(List<Filter> filters, Filter.BooleanOperation operation) {
        Filter filter = null;
        if(filters!=null && !filters.isEmpty()) {
            if(filters.size()==1) {
                filter = filters.get(0);
            }
            else {
                filter = new CompositeFilter(operation, filters);
            }
        }
        
        return filter;
    }

    /**
     * Create isNull filter for the property, going up the chain to allow for parent
     * property to be null instead
     * @return Filter object
     */
    public static Filter getNullFilter(String property) {
        Filter filter = Filter.isnull(property);
        int dotIndex = property.lastIndexOf('.');
        if (dotIndex >= 0) {
            Filter parentFilter = getNullFilter(property.substring(0, dotIndex));
            if (parentFilter != null) {
                filter = Filter.or(parentFilter, filter);
            }
        } else if (Character.isUpperCase(property.charAt(0)) && Util.getSailPointObjectClass(property) != null) {
            // If this is the front of the chain and its a sailpoint object, we dont want to include a null
            // filter for it because it is a join property
            filter = null;
        }

        return filter;
    }
}
