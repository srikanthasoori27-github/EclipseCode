/*
 *  (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.service;

import sailpoint.api.SailPointContext;
import sailpoint.integration.ListResult;
import sailpoint.object.MonitoringStatistic;
import sailpoint.object.QueryOptions;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.web.UserContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class MonitoringStatisticListService extends BaseListService<BaseListServiceContext> {
    /**
     * Create a base list service.
     *
     * @param context            sailpoint context
     * @param listServiceContext list service context
     * @param columnSelector
     */
    public MonitoringStatisticListService(SailPointContext context, BaseListServiceContext listServiceContext, ListServiceColumnSelector columnSelector) {
        super(context, listServiceContext, columnSelector);
    }

    public ListResult getMonitoringStats(QueryOptions ops, UserContext context)
        throws GeneralException {
        int count = countResults(MonitoringStatistic.class, ops);
        List<Map<String, Object>> results = getResults(MonitoringStatistic.class, ops);
        List<MonitoringStatisticDTO> stats = new ArrayList<>();
        for(Map<String, Object> res : Util.safeIterable(results)) {
            stats.add(new MonitoringStatisticDTO(res, context));
        }

        return new ListResult(stats, count);
    }

}
