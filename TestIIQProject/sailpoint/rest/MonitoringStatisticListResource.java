/*
 *  (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.rest;

import sailpoint.authorization.RightAuthorizer;
import sailpoint.integration.ListResult;
import sailpoint.object.Filter;
import sailpoint.object.QueryOptions;
import sailpoint.object.SPRight;
import sailpoint.object.UIConfig;
import sailpoint.service.BaseListResourceColumnSelector;
import sailpoint.service.BaseListServiceContext;
import sailpoint.service.MonitoringStatisticListService;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;

@Path("monitoringStatistics")
public class MonitoringStatisticListResource extends BaseListResource implements BaseListServiceContext {

    @Override
    protected String getColumnKey() {
        String key = super.getColumnKey();
        return key != null ? key : UIConfig.MON_STAT_COLUMNS;
    }

    @GET
    public ListResult getMonitoringStatistics(@QueryParam("excludeTemplates") Boolean excludeTemplates) throws GeneralException {
        authorize(new RightAuthorizer(SPRight.FullAccessEnvironmentMonitoring, SPRight.ViewEnvironmentMonitoring));

        QueryOptions ops = getQueryOptions(getColumnKey());

        if (Util.otob(excludeTemplates)) {
            ops.add(Filter.eq("template", false));
        }

        BaseListResourceColumnSelector selector = new BaseListResourceColumnSelector(getColumnKey());
        MonitoringStatisticListService svc = new MonitoringStatisticListService(getContext(), this, selector);
        return svc.getMonitoringStats(ops, this);
    }

}
