/* (c) Copyright 2018 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import sailpoint.api.IdentityService;
import sailpoint.api.ObjectUtil;
import sailpoint.integration.ListResult;
import sailpoint.object.Application;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.ManagedAttribute;
import sailpoint.object.ObjectAttribute;
import sailpoint.object.ObjectConfig;
import sailpoint.object.Policy;
import sailpoint.object.QueryOptions;
import sailpoint.service.suggest.SuggestHelper;
import sailpoint.service.suggest.SuggestService;
import sailpoint.service.suggest.SuggestServiceContext;
import sailpoint.service.suggest.SuggestServiceOptions;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.web.identity.IdentitiesSuggestBean;
import sailpoint.web.util.Sorter;
import sailpoint.web.util.WebUtil;

/**
 * Wrapper around logic that used to live in the "classic" SuggestResource (/rest/suggest).
 * Moved here so we can maintain existing logic while sharing between FormSuggestBean and SuggestResource, mostly so
 * they can be authorized effectively (see FormSuggestBean for more details)
 *
 * @exclude
 * Most of the code here was lifted directly from the resource so don't get mad at me if its ugly.
 */
public class ClassicSuggestService {

    /**
     * Extension of SuggestServiceContext with some specific pieces that SuggestResource used to reference.
     */
    public interface ClassicSuggestServiceContext extends SuggestServiceContext {
        String getSuggestContext();

        String getSuggestId();

        String getSortBy();

        String getSortDirection();

        String getRequestParameter(String name);
    }

    private static final String IDENTITY_CLASS = "sailpoint.object.Identity";

    private ClassicSuggestServiceContext suggestServiceContext;
    private SuggestService suggestService;

    public ClassicSuggestService(ClassicSuggestServiceContext suggestServiceContext) {
        this.suggestServiceContext = suggestServiceContext;
        this.suggestService = new SuggestService(this.suggestServiceContext);
    }

    /**
     * Get the suggest results for an object list
     * @return ListResult
     * @throws GeneralException
     */
    public ListResult getObjectSuggestResult() throws GeneralException {
        return this.suggestService.getObjects(getQueryOptions());
    }

    /**
     * Get the suggest results for column values list
     * @param suggestColumn Name of column on the suggest class
     * @param isLCM True if LCM, otherwise false.
     * @param quicklinkName Name of current quick link, if LCM.
     * @param exclude true if currently assigned items are to be excluded
     * @return ListResult
     * @throws GeneralException
     * @throws ClassNotFoundException
     */
    public ListResult getColumnSuggestResult(String suggestColumn, boolean isLCM, String quicklinkName, boolean exclude) throws GeneralException, ClassNotFoundException {
        Class clazz = this.suggestService.getSuggestClass(this.suggestServiceContext.getSuggestClass());

        // Unfortunately, this method was kind of overloaded to get two different types of results. Keep the special
        // behavior for ManagedAttribute with Identity type extended attribute in this resource only.
        boolean isAttributeTypeIdentity = isAttributeTypeIdentity(clazz, suggestColumn);
        ListResult listResult;
        if (!isAttributeTypeIdentity) {
            Filter additionalFilter = null;

            // Not sure what this is for, but keep it around.
            if (Application.class.equals(clazz) &&
                    !Util.otob(this.suggestServiceContext.getRequestParameter("showNonAggregable"))) {
                additionalFilter = Filter.eq("noAggregation", false);
            }

            listResult = suggestService.getColumnValues(SuggestServiceOptions.getInstance().setColumn(suggestColumn).
                    setLcm(isLCM).setLcmQuicklinkName(quicklinkName).setAdditionalFilter(additionalFilter).setExclude(exclude));
        } else {
            //Special handling for Identity attributes off ManagedAttribute class
            QueryOptions qo = suggestService.getColumnSuggestQueryOptions(clazz,
                    SuggestServiceOptions.getInstance().setColumn(suggestColumn).setLcm(isLCM).setLcmQuicklinkName(quicklinkName),
                    false);
            List<Map<String, Object>> out = new ArrayList<Map<String, Object>>();
            int total = 0;

            if (qo != null) {
                total = ObjectUtil.countDistinctAttributeValues(this.suggestServiceContext.getContext(), clazz, qo, suggestColumn);
                if (total > 0) {
                    QueryOptions idQO = buildIdentityQueryOptions(suggestColumn, this.suggestServiceContext.getQuery());
                    Iterator<Object[]> idIterator = this.suggestServiceContext.getContext().search(Identity.class, idQO, "id");
                    while (idIterator.hasNext()) {
                        Object[] obj = idIterator.next();
                        if (obj[0] != null && !obj[0].equals("null")) {
                            out.add(WebUtil.buildMapFromIdentity(obj[0].toString()));
                        }
                    }
                }
            }
            listResult = new ListResult(out, total);

        }

        return listResult;
    }

    /**
     * Create the QueryOptions to use for object suggest
     * @return QueryOptions
     * @throws GeneralException
     */
    public QueryOptions getQueryOptions() throws GeneralException{

        QueryOptions ops = null;
        Class clazz = this.suggestService.getSuggestClass(this.suggestServiceContext.getSuggestClass());

        if (Identity.class.equals(clazz)) {
            if (Util.isNullOrEmpty(this.suggestServiceContext.getSuggestId()) && Util.isNullOrEmpty(this.suggestServiceContext.getSuggestContext())) {
                throw new GeneralException("Context or suggestId is required for identity suggest results");
            }

            IdentityService identitySvc = new IdentityService(this.suggestServiceContext.getContext());
            Map<String, Object> queryParams = new HashMap<>();
            queryParams.put("start", this.suggestServiceContext.getStart());
            queryParams.put("limit", this.suggestServiceContext.getLimit());
            queryParams.put("query", this.suggestServiceContext.getQuery());
            queryParams.put("sort", this.suggestServiceContext.getSortBy());
            queryParams.put("dir", this.suggestServiceContext.getSortDirection());
            queryParams.put("filter", this.suggestServiceContext.getFilterString());
            queryParams.put("context", this.suggestServiceContext.getSuggestContext());
            queryParams.put("suggestId", this.suggestServiceContext.getSuggestId());

            ops = identitySvc.getIdentitySuggestQueryOptions(queryParams, this.suggestServiceContext.getLoggedInUser());
            if (ops == null) {
                throw new GeneralException("Could not find IdentityFilter for identity suggest");
            }
        }

        // if we haven't found a filter yet, use the default.
        if (ops == null){
            ops = new QueryOptions();
            if (!Util.isNullOrEmpty(this.suggestServiceContext.getQuery())) {
                //IIQSAW-2625: keep consistent with REST UI implementation
                ops.add(SuggestHelper.getQueryFilter(clazz, this.suggestServiceContext.getQuery()));
            }
            //add ordering
            if (!Util.isNullOrEmpty(this.suggestServiceContext.getSortBy())) {
                ops.addOrdering(this.suggestServiceContext.getSortBy(), 
                        Sorter.isAscending(this.suggestServiceContext.getSortDirection()));
            }
            ops.setScopeResults(true);
            Identity identity = this.suggestServiceContext.getLoggedInUser();
            if (identity != null)
                ops.addOwnerScope(identity);

            if (Application.class.equals(clazz) && !Util.otob(this.suggestServiceContext.getRequestParameter("showNonAggregable"))) {
                ops.add(Filter.eq("noAggregation", false));
            }
            if (Policy.class.equals(clazz)) {
                ops.add(Filter.eq("template", false));
            }
        }
        ops.setDistinct(true);

        if (this.suggestServiceContext.getStart() > 0) {
            ops.setFirstRow(this.suggestServiceContext.getStart());
        }

        if (this.suggestServiceContext.getLimit() > 0) {
            ops.setResultLimit(this.suggestServiceContext.getLimit());
        }

        if (!Util.isNullOrEmpty(this.suggestServiceContext.getFilterString())) {
            ops.add(SuggestHelper.compileFilterString(this.suggestServiceContext.getContext(), clazz, this.suggestServiceContext.getFilterString()));
        }
        return ops;
    }

    /**
     * Build up a QueryOptions that filters existing ids in the ManagedAttribute with the input text
     * of the suggest.
     *
     * @param column Name of the column to query
     * @param query The text input from the suggest input
     * @return A QueryOptions object that filters Identities contained in ManagedAttributes.
     */
    private QueryOptions buildIdentityQueryOptions(String column, String query) {
        QueryOptions qo = new QueryOptions();

        if (Util.isNullOrEmpty(column)) {
            return qo;
        }

        // Create a subquery to filter the ManagedAttribute values
        Filter managedAttributeFilter = Filter.or(Filter.subquery("id", ManagedAttribute.class, column, null), Filter.subquery("name", ManagedAttribute.class, column, null));

        if (!Util.isNullOrEmpty(query)) {
            // Add the identity query parts.
            Filter nameFilter = IdentitiesSuggestBean.getNameFilter(query, IdentitiesSuggestBean.SUGGEST_TYPE_IDENTITY);

            // AND together the id and name query
            Filter combinedFilter = Filter.and(managedAttributeFilter, nameFilter);

            qo.addFilter(combinedFilter);
        }
        else {
            qo.addFilter(managedAttributeFilter);
        }

        // bug 28274 - adding a filter to include workgroups. We lost this when
        // the code was refactored in the 7.0 release. In previous releases (6.3 and earlier),
        // the query was much more general and returned workgroups.
        qo.add(ObjectUtil.buildWorkgroupInclusiveIdentityFilter());

        qo.setOrderBy("displayName");
        qo.addOrdering("firstname", true);
        qo.addOrdering("lastname", true);
        qo.addOrdering("name", true);

        return qo;
    }

    /**
     * True if requested attribute on suggestClass is of type Identity
     *
     * @param suggestClass The Class to get object config from
     * @param column the name of the extended attribute to find type of
     * @return true if attrType equals IDENTITY_CLASS
     */
    private boolean isAttributeTypeIdentity(Class suggestClass, String column) {
        String attributeType = null;
        // If suggestClass is ManagedAttribute, we need to pull the type of the column so we
        // can serve up Identity data if needed.
        if (ManagedAttribute.class.equals(suggestClass)) {
            ObjectConfig oConfig = ObjectConfig.getObjectConfig(suggestClass);
            if (oConfig != null) {
                ObjectAttribute attr = oConfig.getObjectAttribute(column);
                if (attr != null) {
                    attributeType = attr.getType();
                }
            }
        }
        if (attributeType != null && attributeType.equals(IDENTITY_CLASS)) {
            return true;
        }
        return false;
    }

}
