/* (c) Copyright 2016 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.service.certification;

import org.apache.commons.lang3.StringUtils;

import sailpoint.api.Explanator;
import sailpoint.api.ObjectUtil;
import sailpoint.integration.ListResult;
import sailpoint.object.Application;
import sailpoint.object.Certification;
import sailpoint.object.CertificationItem;
import sailpoint.object.Filter;
import sailpoint.object.QueryOptions;
import sailpoint.service.suggest.SuggestHelper;
import sailpoint.service.suggest.SuggestServiceContext;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Service to help out with certification item specific suggests
 */
public class CertificationItemSuggestService {

    /**
     * Flag in the suggest results from entitlement names that indicates result refers to a permission.
     */
    public static final String IS_PERMISSION = "isPermission";
    
    /**
     * Suggest context for certification item suggests
     */
    public interface CertificationItemSuggestContext extends SuggestServiceContext {
        Certification getCertification();
    }
    
    private CertificationItemSuggestContext suggestContext;

    /**
     * Constructor
     * @param suggestContext The CertificationItemSuggestContext
     */
    public CertificationItemSuggestService(CertificationItemSuggestContext suggestContext) {
        this.suggestContext = suggestContext;
    }

    /**
     * Query for names of "entitlements" in the cert, combining both exception attributes and exception permissions
     * @param application The name of the exception application
     * @return ListResult containing suggest results for the names. In addition to normal 'id' and 'displayName' values,
     * there is an 'isPermission' flag which is set to true for permissions.
     * @throws GeneralException
     */
    public ListResult getEntitlementNames(String application) throws GeneralException {
        QueryOptions options = getEntitlementQueryOptions(application);
        List<Map<String, Object>> results = new ArrayList<Map<String,Object>>();
        int count = 0;
        
        // First get the attributes
        ListResult innerListResult = getSuggestResults(options, "exceptionAttributeName", this.suggestContext.getQuery());
        results.addAll(innerListResult.getObjects());
        count += innerListResult.getCount();
        
        // Then get the permissions
        innerListResult = getSuggestResults(options, "exceptionPermissionTarget", this.suggestContext.getQuery());
        for (Map<String, Object> permissionResult: (List<Map<String, Object>>)innerListResult.getObjects()) {
            permissionResult.put(IS_PERMISSION, true);
        }
        
        results.addAll(innerListResult.getObjects());
        count += innerListResult.getCount();
        
        return new ListResult(sortAndTrimResults(results), count);
    }

    /**
     * Query for values of "entitlements" in the cert, limited to the provided application and name.
     * @param application The name of the exception application
     * @param name The attribute name or permission target
     * @param isPermission If true, this is a permission. False, this is an attributes            
     * @return ListResult containing suggest results for the values
     * @throws GeneralException
     */
    public ListResult getEntitlementValues(String application, String name, boolean isPermission) throws GeneralException {
        // Get all the result so we can get their display name and whatnot. 
        QueryOptions options = getEntitlementQueryOptions(application);
        String nameColumn = (isPermission) ? "exceptionPermissionTarget" : "exceptionAttributeName";
        String valueColumn = (isPermission) ? "exceptionPermissionRight" : "exceptionAttributeValue";
        options.add(Filter.eq(nameColumn, name));
        
        // skip the query because we will apply that to display names
        ListResult listResult = getSuggestResults(options, valueColumn, null);

        List<Map<String, Object>> results = listResult.getObjects();
        String appId = ObjectUtil.getId(this.suggestContext.getContext(), Application.class, application);
        Iterator<Map<String, Object>> resultIterator = results.iterator();
        
        // Iterate through the results and try to get a display value for the attribute value. Also, filter out
        // any that do not match the query string.
        while (resultIterator.hasNext()) {
            Map<String, Object> result = resultIterator.next();
            String displayValue = Explanator.getDisplayValue(appId, name, (String)result.get("id"));
            if (!Util.isNothing(this.suggestContext.getQuery()) && !StringUtils.containsIgnoreCase(displayValue, this.suggestContext.getQuery())) {
                resultIterator.remove();    
            } else {
                result.put("displayName", displayValue);
            }
        }
        
        return new ListResult(sortAndTrimResults(results), results.size());
    }

    /**
     * Sort the results by display name and trim to the start and limit specified in the suggest context
     */
    private List<Map<String, Object>> sortAndTrimResults(List<Map<String, Object>> originalResults) {

        // Sort and trim
        Collections.sort(originalResults, new Comparator<Map<String, Object>>() {
            @Override
            public int compare(Map<String, Object> o1, Map<String, Object> o2) {
                return Util.nullSafeCompareTo((String)o1.get("displayName"), (String)o2.get("displayName"));
            }
        });
        
        int startIndex = this.suggestContext.getStart();
        int endIndex = this.suggestContext.getLimit() + startIndex;
        if (endIndex > originalResults.size()) {
            endIndex = originalResults.size();
        }
        
        return originalResults.subList(startIndex, endIndex);
    }

    /**
     * Create a standard set of query options for the cert and application
     */
    private QueryOptions getEntitlementQueryOptions(String application) {
        QueryOptions queryOptions = new QueryOptions();
        queryOptions.add(Filter.eq("parent.certification.id", this.suggestContext.getCertification().getId()));
        if (application != null) {
            queryOptions.add(Filter.eq("exceptionApplication", application));
        }
        queryOptions.setDistinct(true);
        return queryOptions;
    }

    /**
     * Limit the query options for the given column, handling sorting and query string
     */
    private void addColumnSpecifics(QueryOptions options, String column, String query) {
        options.addOrdering(column, true);
        if (!Util.isNullOrEmpty(query)) {
            options.add(Filter.like(column, query));    
        }
    }

    /**
     * Get the ListResult for the given column name
     */
    private ListResult getSuggestResults(QueryOptions options, String columnName, String query) throws GeneralException {
        QueryOptions clonedOptions = new QueryOptions(options);
        List<Map<String, Object>> results = new ArrayList<Map<String,Object>>();
        addColumnSpecifics(clonedOptions, columnName, query);
        int count = ObjectUtil.countDistinctAttributeValues(this.suggestContext.getContext(), CertificationItem.class, clonedOptions, columnName);
        
        Iterator<Object[]> searchResults = this.suggestContext.getContext().search(CertificationItem.class, clonedOptions, columnName);
        while(searchResults.hasNext()) {
            Object value = searchResults.next()[0];
            if (!Util.isNothing((String)value)) {
                results.add(SuggestHelper.getSuggestColumnValue(CertificationItem.class, columnName, value, this.suggestContext.getContext()));
            }
        }
        
        return new ListResult(results, count);
    }
}
