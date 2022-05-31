/*
 *  (c) Copyright 2017 SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.rest;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import sailpoint.authorization.RightAuthorizer;
import sailpoint.integration.ListResult;
import sailpoint.integration.Util;
import sailpoint.object.Filter;
import sailpoint.object.QueryOptions;
import sailpoint.object.SPRight;
import sailpoint.object.ServerStatistic;
import sailpoint.object.UIConfig;
import sailpoint.rest.ui.Paths;
import sailpoint.rest.ui.suggest.SuggestResource;
import sailpoint.service.BaseListResourceColumnSelector;
import sailpoint.service.BaseListServiceContext;
import sailpoint.service.ServerStatisticListService;
import sailpoint.service.listfilter.ListFilterService;
import sailpoint.service.listfilter.ServerStatisticFilterContext;
import sailpoint.service.suggest.BaseSuggestAuthorizerContext;
import sailpoint.service.suggest.SuggestAuthorizerContext;
import sailpoint.tools.GeneralException;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;
import java.util.List;
import java.util.Map;

@Path("serverStatistics")
public class ServerStatisticListResource extends BaseListResource implements BaseListServiceContext {

    private static Log _log = LogFactory.getLog(ServerStatisticListResource.class);

    @Override
    protected String getColumnKey() {
        String key = super.getColumnKey();
        return key != null ? key : UIConfig.SERVER_STATISTIC_COLUMNS;
    }

    @GET
    public ListResult getServerStatistics(@QueryParam("snapshotName") String snapshotName,
                                @QueryParam("server") String server,
                                @QueryParam("stats") String stats) throws GeneralException {

        authorize(new RightAuthorizer(SPRight.ViewEnvironmentMonitoring, SPRight.FullAccessEnvironmentMonitoring));

        QueryOptions ops = getQueryOptions(getColumnKey());

        if (_log.isDebugEnabled()) {
            _log.debug("Full Query Params: " + uriInfo.getQueryParameters());
        }

        //Build Filters based on query params
        Map<String, String> queryParams = getOtherQueryParams();
        queryParams.remove(PARAM_QUERY);

        BaseListResourceColumnSelector selector = new BaseListResourceColumnSelector(getColumnKey());
        ServerStatisticListService svc = new ServerStatisticListService(getContext(), this, selector);
        ListFilterService filterSvc = new ListFilterService(getContext(), getLocale(), new ServerStatisticFilterContext());
        List<Filter> filters = filterSvc.convertQueryParametersToFilters(queryParams, true);
        if (!Util.isEmpty(filters)) {
            ops.add(filters.toArray(new Filter[0]));
        }

        if (Util.isNotNullOrEmpty(query)) {
            ops.add(Filter.eq("name", sailpoint.tools.Util.padID(query)));
        }

        return svc.getServerStatistics(ops, this);
    }

    @Path("{serverStatisticId}")
    public ServerStatisticResource getServerStatistic(@PathParam("serverStatisticId") String serverStatisticId) throws GeneralException {
        return new ServerStatisticResource(serverStatisticId, this);
    }

    @Path(Paths.SUGGEST)
    public SuggestResource getSuggest() throws GeneralException {
        authorize(new RightAuthorizer(SPRight.ViewEnvironmentMonitoring, SPRight.FullAccessEnvironmentMonitoring));
        SuggestAuthorizerContext authorizerContext = new BaseSuggestAuthorizerContext()
                .add(ServerStatistic.class.getSimpleName(), true, "snapshotName");
        return new SuggestResource(this, authorizerContext);
    }

}
