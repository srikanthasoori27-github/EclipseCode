package sailpoint.service;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.ObjectUtil;
import sailpoint.api.SailPointContext;
import sailpoint.integration.ListResult;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.ManagedAttribute;
import sailpoint.object.QueryOptions;
import sailpoint.object.QueryOptions.Ordering;
import sailpoint.object.SailPointObject;
import sailpoint.service.listfilter.ListFilterService;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

/**
 * Service to return ManagedAttributes.
 */
public class ManagedAttributeService extends BaseListService<ManagedAttributeServiceContext> {

    private static Log log = LogFactory.getLog(ManagedAttributeService.class);

    /**
     * Constructor if using instance of this service.
     */
    public ManagedAttributeService(SailPointContext context, ManagedAttributeServiceContext listServiceContext, 
            ListServiceColumnSelector columnSelector) {
        super(context, listServiceContext, columnSelector);
    }

    /**
     * Return the requested ManagedAttribute properties, optionally using a
     * distinct query.
     * 
     * @param qo QueryOptions to limit search by
     * @param isLCM true if this is for an LCM request false otherwise
     * @return ListResult
     */
    public ListResult getResults(QueryOptions qo, boolean isLCM)
        throws GeneralException {

        int count;
        boolean returnNothing = false;

        LCMConfigService configService = new LCMConfigService(getContext());
        if (isLCM) {
            //IIQTC-101: Removing scope if this is for an LCM request
            qo.setScopeResults(false);
            returnNothing = configService.addLCMAttributeAuthorityFilters(qo, getListServiceContext().getLoggedInUser(),
                    getListServiceContext().getRequesteeId());
        }
        if (returnNothing) {
            return new ListResult(new ArrayList(), 0);
        } else {
            count = countResults(ManagedAttribute.class, qo);
        }

        List<Map<String,Object>> resultList = super.getResults(ManagedAttribute.class, qo);
        return new ListResult(resultList, count);
    }

    /* (non-Javadoc)
     * @see sailpoint.service.BaseListService#countResults(java.lang.Class, sailpoint.object.QueryOptions)
     */
    @Override
    public int countResults(Class<? extends SailPointObject> scope, QueryOptions qo)
        throws GeneralException {
    	if (columnSelector.getColumnsKey().equals(ListFilterService.MANAGED_ATTRIBUTE_TYPE_WITH_APP)) {
            return countApplicationAttributesResults(ManagedAttribute.class, qo);
        } else if (qo.isDistinct()) {
            // Create a copy because we are going to reset the paging info.
            QueryOptions countOptions = new QueryOptions(qo);
            countOptions.setResultLimit(0);
            countOptions.setFirstRow(0);
            return ObjectUtil.countDistinctAttributeValues(getContext(),
                    ManagedAttribute.class,
                    countOptions, "attribute");
        } else {
            return super.countResults(scope, qo);
        }
    }

    /**
     * Count the application attributees - similar to BaseListBean.countResults() but grouping by
     * attributes and applications
     */
    private int countApplicationAttributesResults(Class<? extends SailPointObject> scope, QueryOptions qo)
        throws GeneralException {
        // Create a copy because we are going to reset the paging info.
        QueryOptions countOptions = new QueryOptions(qo);
        countOptions.setResultLimit(0);
        countOptions.setFirstRow(0);

        int count = 0;
        List<Ordering> orderBys = countOptions.getOrderings();
        List<String> groupBys = countOptions.getGroupBys();
        boolean distinct = countOptions.isDistinct();
        try {
            countOptions.setOrderings(new ArrayList<Ordering>());
            countOptions.setDistinct(false);
            countOptions.addGroupBy("attribute");
            countOptions.addGroupBy("application");
            String prop = "count(distinct attribute)";
            Iterator<Object[]> results = getContext().search(ManagedAttribute.class, countOptions, prop);
            while(results.hasNext()) {
                count += ((Long) results.next()[0]).intValue();
            }
        }
        finally {
            countOptions.setOrderings(orderBys);
            countOptions.setDistinct(distinct);
            countOptions.setGroupBys(groupBys);
        }
        return count;
    }


    /**
     * Return a query options for the SailPoint Suggest
     * @param query optional parameter to filter the displayableName by
     * @return adjusted QueryOptions
     */
    public QueryOptions getSuggestQueryOptions(String query) throws GeneralException {
        QueryOptions ops= new QueryOptions();
        if (!Util.isNullOrEmpty(query)) {
            ops.add(Filter.ignoreCase(Filter.like("displayableName", query, Filter.MatchMode.START)));
        }
        ops.setScopeResults(true);
        Identity identity = getListServiceContext().getLoggedInUser();
        if (identity != null) {
            ops.addOwnerScope(identity);
        }
        //For now, we only care about the Entitlement MAs in the SPCombos
        ops.add(Filter.ne("type", ManagedAttribute.Type.Permission.name()));
        return ops;
    }

    /**
     * A set of query options that only returns the requestable attributes that use "attribute" as the
     * like value of the query
     * @param query The query string to look for anything that has a value of attribute like it
     * @param limit The number of results to return
     * @param applications Any application ids we need to limit by
     * @return QueryOptions
     * @throws GeneralException
     */
    public QueryOptions getRequestableQueryOptions(String query, int start, int limit, List<String> applications) throws GeneralException {
        QueryOptions qo = this.getSuggestQueryOptions(null);
        qo.setDistinct(true);
        qo.add(Filter.eq("requestable", true));
        if (limit > 0) {
            qo.setResultLimit(limit);
        }
        // IIQTC-138: Query now uses the variable 'start' to enable 'load more' functionality.
        if (start > 0) {
            qo.setFirstRow(start);
        }
        if (!Util.isNullOrEmpty(query)) {
            qo.add(Filter.ignoreCase(Filter.like("attribute", query, Filter.MatchMode.START)));
        }
        //only return attributes for the selected applications
        if (!Util.isEmpty(applications)) {
            qo.addFilter(Filter.in("application.id", applications));
        }
        // IIQTC-138: Add ordering to make the suggestions more friendly.
        qo.addOrdering("attribute", true);
        qo.addOrdering("application", true);
        return qo;
    }

}
