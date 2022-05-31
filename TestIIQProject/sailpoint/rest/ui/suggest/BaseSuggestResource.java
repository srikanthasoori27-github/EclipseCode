/* (c) Copyright 2019 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.rest.ui.suggest;

import java.util.List;
import java.util.Map;

import javax.ws.rs.QueryParam;

import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.QueryOptions;
import sailpoint.rest.BaseListResource;
import sailpoint.rest.BaseResource;
import sailpoint.service.suggest.GlobalSuggestAuthorizerContext;
import sailpoint.service.suggest.SuggestAuthorizerContext;
import sailpoint.service.suggest.SuggestHelper;
import sailpoint.service.suggest.SuggestService;
import sailpoint.service.suggest.SuggestServiceContext;
import sailpoint.tools.GeneralException;
import sailpoint.tools.JsonHelper;
import sailpoint.tools.Util;

/**
 * Base class with some common functionality for suggest resources
 */
public class BaseSuggestResource extends BaseListResource implements SuggestServiceContext {
    protected String suggestClass;
    protected Map<String,Object> extraParamsMap;  // a map to store deserialized extra parameters
    protected SuggestAuthorizerContext authorizerContext;

    public static final String PARAM_FILTER_STRING = "filterString";
    public static final String PARAM_EXTRA_PARAMS = "extraParams";

    @QueryParam(PARAM_FILTER_STRING) protected String filterString;
    @QueryParam(PARAM_EXTRA_PARAMS) protected String extraParams;

    public BaseSuggestResource() {
        super();
        this.authorizerContext = new GlobalSuggestAuthorizerContext();
    }

    public BaseSuggestResource(BaseResource parent) {
        super(parent);
    }

    public BaseSuggestResource(BaseResource parent, boolean exclude) {
        super(parent, exclude);
    }

    public BaseSuggestResource(BaseResource parent, SuggestAuthorizerContext authorizerContext) {
        // default to exclude currently assigned items
        this(parent, authorizerContext, true);
    }

    /**
     * Constructor
     * @param baseResource BaseListResource
     * @param suggestAuthorizerContext Authorizer context to use for endpoint auth, if not provided default is set up
     * @param exclude true if currently assigned items are to be excluded from the list
     */
    public BaseSuggestResource(BaseResource parent, SuggestAuthorizerContext authorizerContext, boolean exclude) {
        super(parent, exclude);
        this.authorizerContext = authorizerContext;
    }

    /**
     * Builds the query options for the generic suggest.
     *   - Adds the value of 'query' to the search as a starts-with-like filter
     *   - Compiles and adds the filter string specified on the filterString parameter
     * @return
     * @throws GeneralException
     */
    protected QueryOptions getQueryOptions() throws GeneralException {
        QueryOptions qo = super.getQueryOptions();

        //add default filters based on suggest class
        SuggestService suggestService = new SuggestService(this);
        Class clazz = suggestService.getSuggestClass(suggestClass);
        List<Filter> defaultFilters = suggestService.getDefaultFilters();
        for (Filter filter : Util.safeIterable(defaultFilters)) {
            qo.add(filter);
        }

        if (this.query != null && !"".equals(this.query))
            qo.add(SuggestHelper.getQueryFilter(clazz, this.query));

        qo.setScopeResults(true);
        qo.setDistinct(true);

        if (Util.isNotNullOrEmpty(this.filterString)) {
            qo.add(SuggestHelper.compileFilterString(getContext(), clazz, this.filterString));
        }

        /** Add name/id orderings to the query if there is no orderings already there */
        if(Util.isEmpty(qo.getOrderings())) {
            SuggestHelper.addDefaultOrderings(clazz, qo);
        }

        Identity identity = super.getLoggedInUser();
        if (identity != null) {
            qo.addOwnerScope(identity);
        }
        return qo;
    }

    public String getSuggestClass() {
        return suggestClass;
    }

    public String getFilterString() {
        return filterString;
    }

    public String getExtraParams() {
        return extraParams;
    }

    protected String getExtraParamsFromMap(Map<String, Object> extraParamsMap) {
        if(extraParamsMap == null) {
            return null;
        }

        return JsonHelper.toJson(extraParamsMap);
    }

    protected Map<String, Object> getExtraParamMap() throws GeneralException {
        if(extraParams!=null && extraParamsMap==null) {
            if (!Util.isNullOrEmpty(extraParams)) {
                extraParamsMap = JsonHelper.mapFromJson(String.class, Object.class, decodeRestUriComponent(extraParams, false));
            }
        }
        return extraParamsMap;
    }
}
