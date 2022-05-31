/*
 *  (c) Copyright 2017 SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.service;

import sailpoint.api.SailPointContext;
import sailpoint.integration.ListResult;
import sailpoint.object.QueryOptions;
import sailpoint.object.ServerStatistic;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.web.UserContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ServerStatisticListService extends BaseListService<BaseListServiceContext> {
    /**
     * Create a base list service.
     *
     * @param context            sailpoint context
     * @param listServiceContext list service context
     * @param columnSelector
     */
    public ServerStatisticListService(SailPointContext context, BaseListServiceContext listServiceContext, ListServiceColumnSelector columnSelector) {
        super(context, listServiceContext, columnSelector);
    }

    public ListResult getServerStatistics(QueryOptions qo, UserContext context) throws GeneralException {
        int count = countResults(ServerStatistic.class, qo);
        List<Map<String, Object>> results = getResults(ServerStatistic.class, qo);
        List<ServerStatisticDTO> serverStatistics = new ArrayList<ServerStatisticDTO>();
        for (Map<String, Object> result : Util.safeIterable(results)) {
            serverStatistics.add(new ServerStatisticDTO(result, context));
        }

        return new ListResult(serverStatistics, count);
    }
}
