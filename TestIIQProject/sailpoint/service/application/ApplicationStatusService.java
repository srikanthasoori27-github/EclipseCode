/*
 *  (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.service.application;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import sailpoint.api.SailPointContext;
import sailpoint.integration.ListResult;
import sailpoint.object.Application;
import sailpoint.object.ColumnConfig;
import sailpoint.object.Filter;
import sailpoint.object.QueryOptions;
import sailpoint.object.Request;
import sailpoint.object.Server;
import sailpoint.request.ApplicationStatusRequestExecutor;
import sailpoint.service.BaseListService;
import sailpoint.service.BaseListServiceContext;
import sailpoint.service.ListServiceColumnSelector;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

public class ApplicationStatusService extends BaseListService<BaseListServiceContext> {


    Map<String, List<ApplicationStatusDTO.HostStatusDTO>> _appStatuses;


    public ApplicationStatusService(SailPointContext context, BaseListServiceContext listServiceContext, ListServiceColumnSelector columnSelector) {
        super(context, listServiceContext, columnSelector);
    }

    public ApplicationStatusService(SailPointContext context) {
        super(context, null, null);
    }

    public ListResult getApplicationStatuses(QueryOptions ops) throws GeneralException {
        int count = countResults(Application.class, ops);
        List<Map<String, Object>> results = getResults(Application.class, ops);


       List<ApplicationStatusDTO> dtos = new ArrayList<>();
       for (Map<String, Object> result : Util.safeIterable(results)) {
           dtos.add(new ApplicationStatusDTO(result, this.columnSelector.getColumns(), null));
       }

        return new ListResult(results, count);
    }

    public static final String COL_STATUS = "statuses";

    @Override
    protected void calculateColumn(ColumnConfig config, Map<String, Object> rawQueryResults, Map<String,Object> map)
        throws GeneralException {

        //Add Application Statuses to map
        if (COL_STATUS.equals(config.getDataIndex())) {
            map.put(COL_STATUS, getAppStatuses().get(String.valueOf(map.get("name"))));
        }

    }

    /**
     * Get statuses from all hosts for the given application
     * @param appName Application to get status for
     * @param includeRequests true to check for presence of outstanding Request for a given host
     * @return
     * @throws GeneralException
     */
    public ApplicationStatusDTO getAppStatus(String appName, Boolean includeRequests) throws GeneralException {

        //DO we need more here? -rap
        ApplicationStatusDTO dto = new ApplicationStatusDTO(appName);
        dto.setStatuses(getAppStatuses().get(appName));

        if (Util.otob(includeRequests)) {
            for (ApplicationStatusDTO.HostStatusDTO host : Util.safeIterable(dto.getStatuses())) {
                String servName = host.getServer();
                String requestName = String.format(ApplicationStatusRequestExecutor.REQUEST_NAME, appName, servName);
                QueryOptions ops = new QueryOptions();
                ops.add(Filter.eq("name", requestName));
                int count = context.countObjects(Request.class, ops);
                if (count > 0) {
                    host.setOutstandingRequest(true);
                } else {
                    host.setOutstandingRequest(false);
                }
            }
        }

        return dto;
    }

    public Map<String, List<ApplicationStatusDTO.HostStatusDTO>> getAppStatuses() throws GeneralException {

        if (_appStatuses == null) {
            Map<String, List<ApplicationStatusDTO.HostStatusDTO>> appStatuses = new HashMap<>();
            //We could get just the attributes, but Server object is light weight
            List<Server> servers = getContext().getObjects(Server.class);

            //Populate app statuses
            for (Server server : Util.safeIterable(servers)) {
                //Iterate each Server's stats and add them to the appStatuses map
                Map<String, Object> stats = (Map) server.get(Server.ATT_APPLICATION_STATUS);
                if (stats != null) {
                    for (String s : Util.safeIterable(stats.keySet())) {
                        if (!appStatuses.containsKey(s)) {
                            appStatuses.put(s, new ArrayList());
                        }
                        Map<String, Object> appStat = Util.otom(stats.get(s));
                        Boolean status = Util.otob(appStat.get(Server.ATT_APP_STATUS));
                        Date lastRefresh = Util.getDate(appStat.get(Server.ATT_APP_STATUS_DATE));
                        ApplicationStatusDTO.HostStatusDTO dto = new ApplicationStatusDTO.HostStatusDTO(server.getName(), status, lastRefresh, s);
                        if (!status) {
                            String msg = Util.otos(appStat.get(Server.ATT_APP_STATUS_ERROR));
                            if (Util.isNotNullOrEmpty(msg)) {
                                dto.setExceptionMessage(msg);
                            }
                        }
                        ((List) appStatuses.get(s)).add(dto);

                    }
                }
            }
            _appStatuses = appStatuses;
        }
        return _appStatuses;
    }

}
