/* (c) Copyright 2018 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import sailpoint.api.SailPointContext;
import sailpoint.integration.ListResult;
import sailpoint.object.Filter;
import sailpoint.object.ManagedAttribute;
import sailpoint.object.QueryOptions;
import sailpoint.service.BaseListResourceColumnSelector;
import sailpoint.service.ManagedAttributeService;
import sailpoint.service.ManagedAttributeServiceContext;
import sailpoint.service.listfilter.ListFilterService;
import sailpoint.tools.GeneralException;

/**
 * Simple service to consolidate the logic for fetching managed attribute suggest results. Put here so we can easily
 * share with FormSuggestBean.
 *
 * @exclude
 * Most of the code here was lifted directly from the resource so don't get mad at me if its ugly.
 */
public class ClassicManagedAttributeSuggestService {

    /**
     * Extension of ManagedAttributeServiceContext to include filter string
     */
    public interface ClassicManagedAttributeSuggestServiceContext extends ManagedAttributeServiceContext {
        String getFilterString();
    }

    private ClassicManagedAttributeSuggestServiceContext serviceContext;

    public ClassicManagedAttributeSuggestService(ClassicManagedAttributeSuggestServiceContext serviceContext) {
        this.serviceContext = serviceContext;
    }

    /**
     * Get the result map for the columns included in getProjectionColumns.
     * @param row Iterator result
     * @return Map of the result.
     */
    public static Map<String, Object> getResultMap(Object[] row) {
        Map<String, Object> values = new HashMap<String, Object>();
        values.put("id", row[0]);
        values.put("name", row[1]);
        values.put("displayName", row[2]);
        values.put("type", row[3]);
        values.put("attribute", row[4]);

        return values;
    }

    /**
     * @return the projection columns for the managed attribute suggest, matching up with
     * what will be interpreted by getResultMap
     */
    public static List<String> getProjectionColumns() {
        //Should put in projectionColums
        List<String> projectionColumns = new ArrayList<String>();
        projectionColumns.add("id");
        projectionColumns.add("value");
        //For now, we can use displayableName. If we ever decide to make these dispalyNames
        //uniform across the product, we may want to change this to reflect the update.
        projectionColumns.add("displayableName");
        projectionColumns.add("type");
        projectionColumns.add("attribute");

        return projectionColumns;
    }

    /**
     * Fetch the suggest results for managed attributes
     */
    public ListResult getManagedAttributeSuggestResult() throws GeneralException {
        int total = 0;
        List<Map<String, Object>> out = new ArrayList<Map<String, Object>>();

        SailPointContext spContext = this.serviceContext.getContext();
        ManagedAttributeService attributeService = new ManagedAttributeService(spContext, this.serviceContext,
                new BaseListResourceColumnSelector(ListFilterService.MANAGED_ATTRIBUTE_TYPE_WITH_APP));
        QueryOptions ops = attributeService.getSuggestQueryOptions(this.serviceContext.getQuery());

        if (this.serviceContext.getFilterString() != null && this.serviceContext.getFilterString().length() > 0) {
            ops.add(Filter.compile(this.serviceContext.getFilterString()));
        }

        ops.setDistinct(true);

        total = spContext.countObjects(ManagedAttribute.class, ops);

        if (total > 0) {
            if (this.serviceContext.getStart() > 0)
                ops.setFirstRow(this.serviceContext.getStart());

            if (this.serviceContext.getLimit() > 0)
                ops.setResultLimit(this.serviceContext.getLimit());

            Iterator<Object[]> results = spContext.search(ManagedAttribute.class, ops, getProjectionColumns());
            if(results != null) {
                while(results.hasNext()) {
                    out.add(getResultMap(results.next()));
                }
            }
        }

        return new ListResult(out, total);

    }
}
