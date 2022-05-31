package sailpoint.rest.ui.requestaccess;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import sailpoint.integration.ListResult;
import sailpoint.object.ColumnConfig;
import sailpoint.rest.BaseListResource;
import sailpoint.rest.BaseResource;
import sailpoint.rest.UserAccessUtil;
import sailpoint.service.useraccess.UserAccessSearchResults;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

import javax.ws.rs.core.MultivaluedMap;

/**
 * Base resource class with common functionality for searching access items
 */
public class BaseAccessItemListResource extends BaseListResource {

    public static final String PARAM_QUICK_LINK = "quickLink";

    /**
     * Name of the quickLink used for the requestAuthorities. This is optionally passed in with queryParams
     * If not present, default back to QuickLink Action
     */
    String _quickLink;

    public BaseAccessItemListResource(BaseResource parent) {
        super(parent);
        MultivaluedMap<String,String> params = this.uriInfo.getQueryParameters();
        _quickLink = params.getFirst(PARAM_QUICK_LINK);
    }

    /**
     * Use UserAccessUtil to get a ListResult of access items
     * @param columnsKeyPrefix Prefix of the UIConfig entries for roles and entitlement columns
     * @param additionalParams Params to add to the query parameter map sent to UserAccessUtil
     * @return ListResult
     * @throws sailpoint.tools.GeneralException
     */
    protected ListResult getAccessItems(String columnsKeyPrefix, Map<String, String> additionalParams) throws GeneralException {
        UserAccessUtil util = new UserAccessUtil(getContext(), getLocale());
        Map<String, List<ColumnConfig>> columns = new HashMap<String, List<ColumnConfig>>();
        columns.put(UserAccessUtil.ACCESS_TYPE_ENTITLEMENT, getColumns(columnsKeyPrefix + UserAccessUtil.ACCESS_TYPE_ENTITLEMENT));
        columns.put(UserAccessUtil.ACCESS_TYPE_ROLES, getColumns(columnsKeyPrefix + UserAccessUtil.ACCESS_TYPE_ROLES));

        Map<String, String> queryParams = getQueryParamMap();
        if (additionalParams != null) {
            queryParams.putAll(additionalParams);
        }
        // clear the id param if it exists because it doesn't apply here. In theory this will never have a value.
        queryParams.remove("id");
        UserAccessSearchResults results = util.getResults(queryParams, columns, getLoggedInUser());

        if (null != results && null != results.getResults()) {
            return new ListResult(convertRows(results.getResults(), columnsKeyPrefix), results.getTotalResultCount());
        } else {
            return new ListResult(new ArrayList(), 0);
        }
    }

    /**
     * Do standard row conversion to handle data indexes as well as dates and such. 
     */
    protected List<Map<String, Object>> convertRows(List<Map<String, Object>> rows, String columnsKeyPrefix) throws GeneralException {
        List<Map<String, Object>> converted = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> row : Util.safeIterable(rows)) {
            String columnsKey = columnsKeyPrefix + row.get("accessType");
            converted.add(convertRow(row, columnsKey));
        }
        return converted;
    }
}
