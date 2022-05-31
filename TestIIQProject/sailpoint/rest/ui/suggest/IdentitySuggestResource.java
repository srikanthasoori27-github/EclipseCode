package sailpoint.rest.ui.suggest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.ws.rs.GET;
import javax.ws.rs.QueryParam;

import sailpoint.api.IdentityService;
import sailpoint.integration.ListResult;
import sailpoint.object.Identity;
import sailpoint.object.QueryOptions;
import sailpoint.service.suggest.SuggestHelper;
import sailpoint.tools.GeneralException;
import sailpoint.tools.InvalidParameterException;
import sailpoint.tools.Message;

/**
 * Suggest resource to list identities based on query string and IdentitySelectorConfiguration
 */
public class IdentitySuggestResource extends BaseSuggestResource {

    public static final String PARAM_SUGGEST_ID = "suggestId";
    public static final String PARAM_SUGGEST_CONTEXT = "context";

    @QueryParam(PARAM_SUGGEST_ID) protected String suggestId;
    @QueryParam(PARAM_SUGGEST_CONTEXT) protected String suggestContext;

    public IdentitySuggestResource() {
        super();
    }

    public IdentitySuggestResource(SuggestResource parent) throws GeneralException {
        super(parent);
        this.start = parent.getStart();
        this.limit = parent.getLimit();
        this.query = parent.getQuery();
        this.filterString = parent.getFilterString();
        this.extraParams = parent.getExtraParams();
        if(parent.getExtraParamMap()!=null) {
            this.suggestId = (String)parent.getExtraParamMap().remove(PARAM_SUGGEST_ID);
            this.suggestContext = (String)parent.getExtraParamMap().remove(PARAM_SUGGEST_CONTEXT);
        }
    }

    /**
     * List of identity maps that match a query. The filter and ordering is defined in
     * IdentitySelectorConfiguration.  Either suggestId or context parameter should match one
     * of the IdentityFilters in the configuration.
     *
     * @return ListResult
     * @throws GeneralException
     */
    @GET @SuppressWarnings("unchecked")
    public ListResult getIdentities()
            throws GeneralException {

        if (suggestId == null && suggestContext == null) {
            throw new InvalidParameterException(new Message("Context or suggestId is required"));
        }

        QueryOptions ops = getQueryOptions();
        if (ops == null) {
            throw new GeneralException("Unable to find IdentityFilter for the given params");
        }

        List<Map<String, Object>> results = new ArrayList<Map<String, Object>>();
        int total = getContext().countObjects(Identity.class, ops);

        if (total > 0) {
            if (this.limit > 0) {
                ops.setResultLimit(this.limit);
            }
            if (this.start > 0) {
                ops.setFirstRow(this.start);
            }
            results = SuggestHelper.getSuggestResults(Identity.class, ops, getContext());
        }

        return new ListResult(results, total);
    }

    /**
     * Get QueryParameters for the identity using IdentityService
     * @return QueryOptions for the suggest query
     * @throws GeneralException
     */
    protected QueryOptions getQueryOptions() throws GeneralException {
        IdentityService identitySvc = new IdentityService(getContext());
        Map<String, Object> queryParams = new HashMap<String, Object>();
        queryParams.put("start", this.start);
        queryParams.put("limit", this.limit);
        queryParams.put("query", this.query);
        queryParams.put("suggestId", this.suggestId);
        queryParams.put("filterString", this.filterString);
        queryParams.put("context", this.suggestContext);
        if (getExtraParamMap()!=null) {
            queryParams.putAll(getExtraParamMap());
        }

        return identitySvc.getIdentitySuggestQueryOptions(queryParams, getLoggedInUser());
    }

    public String getSuggestId() {
        return suggestId;
    }

    public String getSuggestContext() {
        return suggestContext;
    }
}