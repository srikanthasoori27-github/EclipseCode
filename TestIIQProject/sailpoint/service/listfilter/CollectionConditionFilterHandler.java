/* (c) Copyright 2019 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.service.listfilter;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import sailpoint.object.Filter;
import sailpoint.object.SailPointObject;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

/**
 * Small helper class to encapsulate some of the weirdo logic we need to handle collection condition filters
 * in a ListFilterContext. Basically, we need to create placeholder filters in convertFilter and then in postProcessFilters
 * we can combine them into a collection condition filter.
 */
public class CollectionConditionFilterHandler {

    private Class<? extends SailPointObject> parentClass;
    private String propertyName;

    /**
     * Constructor
     * @param parentClass Class that will be searched on
     * @param propertyName Name of the property on the collection condition we are matching, i.e. classtifications.name
     */
    public CollectionConditionFilterHandler(Class<? extends SailPointObject> parentClass, String propertyName) {
        this.parentClass = parentClass;
        this.propertyName = propertyName;
    }

    /**
     * Creates a placeholder filter for the given ListFilterValue and String values.
     * IMPORTANT: You must later call postProcessFilters to get a correct collection condition filter
     */
    public Filter convertFilter(ListFilterValue filterValue, List<String> values) {

        Filter filter = Filter.in(propertyName, values);
        if (ListFilterValue.Operation.NotEquals.equals(filterValue.getOperation())) {
            // This doesnt strictly work on its own, it will be reformed in postProcessFilters
            filter = Filter.not(filter);
        }

        return filter;
    }

    /**
     * Process all the filters, looking for ones that match our property, then collapse those to a single
     * collection condition filter for equals and not equals cases.
     *
     * This is meant to be called from ListFilterContext.postProcessFilters.
     */
    public List<Filter> postProcessFilters(List<Filter> filters) throws GeneralException {
        if (Util.size(filters) > 0) {
            List<Filter> equalsFilters = new ArrayList<>();
            List<Filter> notEqualsFilters = new ArrayList<>();
            Iterator<Filter> filterIterator = filters.iterator();
            while (filterIterator.hasNext()) {
                Filter filter = filterIterator.next();
                if (filter instanceof Filter.LeafFilter) {
                    Filter.LeafFilter leafFilter = (Filter.LeafFilter)filter;
                    // This is the format of our filters for "equals" case, we want to pull these out and potentially use a collection condition we have multiples
                    if (propertyName.equals(leafFilter.getProperty()) && Filter.LogicalOperation.IN.equals(leafFilter.getOperation())) {
                        equalsFilters.add(filter);
                        filterIterator.remove();
                    }
                } else {
                    Filter.CompositeFilter compositeFilter = (Filter.CompositeFilter)filter;
                    //This is the format of our filters for "not equals" case, we want to pull these out and use a collection condition inside a negated subquery (weeeee)
                    if (Filter.BooleanOperation.NOT.equals(compositeFilter.getOperation()) && Util.size(compositeFilter.getChildren()) == 1) {
                        Filter.LeafFilter leafFilter = (Filter.LeafFilter)compositeFilter.getChildren().get(0);
                        if (propertyName.equals(leafFilter.getProperty()) && Filter.LogicalOperation.IN.equals(leafFilter.getOperation())) {
                            notEqualsFilters.add(leafFilter);
                            filterIterator.remove();
                        }
                    }
                }
            }

            int lastDotIndex = this.propertyName.lastIndexOf('.');
            if (lastDotIndex == -1) {
                throw new GeneralException("Invalid property name, it should be a property on a collection condition");
            }

            String collectionProperty = propertyName.substring(0, lastDotIndex);
            String collectionPropertyValue = propertyName.substring(lastDotIndex + 1);

            // Combine all the "equals" filters inside a collection condition. This is because multiple In filters
            // ANDed together will never match, since they share a common join
            if (equalsFilters.size() > 0) {
                // Change the property name to to the collection property value since these will be inside a collection condition
                equalsFilters.forEach((filter) -> ((Filter.LeafFilter)filter).setProperty(collectionPropertyValue));
                filters.add(Filter.collectionCondition(collectionProperty, Filter.and(equalsFilters)));
            }

            // We dont have a clear filer for "contains none". If you try to use NOT CONTAINS then it will still match
            // if ANY collection property value is not in the filter. So instead do a little trickery and use a subquery to specify
            // "not in the set of IDs of objects that match the given condition"
            // We cant use a simple collection condition with NOT IN filters because we might not have an inverse property.
            if (notEqualsFilters.size() > 0) {
                // Change the property name to the collection property value since these will be inside a collection condition
                notEqualsFilters.forEach((filter) -> ((Filter.LeafFilter)filter).setProperty(collectionPropertyValue));
                filters.add(Filter.not(Filter.subquery("id", this.parentClass, "id", Filter.collectionCondition(collectionProperty, Filter.and(notEqualsFilters)))));
            }
        }

        return filters;
    }
}
