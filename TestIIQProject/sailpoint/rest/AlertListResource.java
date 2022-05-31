/*
 *  (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved.
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
import sailpoint.object.UIConfig;
import sailpoint.rest.ui.Paths;
import sailpoint.rest.ui.suggest.SuggestResource;
import sailpoint.service.alert.AlertListService;
import sailpoint.service.BaseListResourceColumnSelector;
import sailpoint.service.BaseListServiceContext;
import sailpoint.service.listfilter.AlertFilterContext;
import sailpoint.service.listfilter.ListFilterDTO;
import sailpoint.service.listfilter.ListFilterService;
import sailpoint.service.suggest.BaseSuggestAuthorizerContext;
import sailpoint.tools.GeneralException;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;
import java.util.List;
import java.util.Map;

/**
 * Created by ryan.pickens on 8/15/16.
 */

@Path("alerts")
public class AlertListResource extends BaseListResource implements BaseListServiceContext {

    private static Log _log = LogFactory.getLog(AlertListResource.class);

    @Override
    protected String getColumnKey() {
        String key = super.getColumnKey();
        return key != null ? key : UIConfig.ALERT_COLUMNS;
    }

    @GET
    public ListResult getAlerts(@QueryParam("name") String name,
                                @QueryParam("source") String source,
                                @QueryParam("nativeId") String nativeId,
                                @QueryParam("type") String type,
                                @QueryParam("targetType") String targetType,
                                @QueryParam("targetDisplayName") String targetName,
                                @QueryParam("alertDateStart") String alertStartDate,
                                @QueryParam("alertDateEnd") String alertEndDate,
                                @QueryParam("lastProcessedStart") String lastProcessedStart,
                                @QueryParam("lastProcessedEnd") String lastProcessedEnd,
                                @QueryParam("actions") Boolean hasActions) throws GeneralException {

        authorize(new RightAuthorizer(SPRight.ViewAlert));
        QueryOptions ops = getQueryOptions(getColumnKey());

        if (_log.isDebugEnabled()) {
            _log.debug("Full Query Params: " + uriInfo.getQueryParameters());
        }

        //Build Filters based on query params
        Map<String, String> queryParams = getOtherQueryParams();
        queryParams.remove(PARAM_QUERY);

        BaseListResourceColumnSelector selector = new BaseListResourceColumnSelector(getColumnKey());
        AlertListService svc = new AlertListService(getContext(), this, selector);
        ListFilterService filterSvc = new ListFilterService(getContext(), getLocale(), new AlertFilterContext());
        List<Filter> filters = filterSvc.convertQueryParametersToFilters(queryParams, true);
        if (!Util.isEmpty(filters)) {
            ops.add(filters.toArray(new Filter[0]));
        }

        if (Util.isNotNullOrEmpty(query)) {
            ops.add(
                    Filter.or(
                            Filter.eq("name", sailpoint.tools.Util.padID(query)),
                            Filter.ignoreCase(Filter.like("source.name", query, Filter.MatchMode.START)),
                            Filter.ignoreCase(Filter.like("displayName", query, Filter.MatchMode.START))
                    )
            );
        }

        return svc.getAlerts(ops, this);
    }

    @GET
    @Path(Paths.FILTERS)
    public List<ListFilterDTO> getAlertFilters() throws GeneralException {
        authorize(new RightAuthorizer(SPRight.ViewAlert));

        AlertFilterContext filterContext = new AlertFilterContext();
        String suggestUrl = getMatchedUri().replace(Paths.FILTERS, Paths.SUGGEST);
        filterContext.setSuggestUrl(suggestUrl);
        return new ListFilterService(getContext(), getLocale(), filterContext).getListFilters(true);
    }

    @Path("{alertId}")
    public AlertResource getAlert(@PathParam("alertId") String alertId) throws GeneralException {
        return new AlertResource(alertId, this);
    }

    @Path(Paths.SUGGEST)
    public SuggestResource getSuggestResource() throws GeneralException {
        authorize(new RightAuthorizer(SPRight.ViewAlert));

        return new SuggestResource(this,  new BaseSuggestAuthorizerContext(getAlertFilters()));
    }
}
