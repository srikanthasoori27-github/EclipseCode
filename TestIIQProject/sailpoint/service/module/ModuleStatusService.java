/*
 *  (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.service.module;

import sailpoint.api.SailPointContext;
import sailpoint.integration.ListResult;
import sailpoint.object.ColumnConfig;
import sailpoint.object.Configuration;
import sailpoint.object.Filter;
import sailpoint.object.Module;
import sailpoint.object.QueryOptions;
import sailpoint.object.Request;
import sailpoint.object.Server;
import sailpoint.request.ModuleStatusRequestExecutor;
import sailpoint.service.BaseListService;
import sailpoint.service.BaseListServiceContext;
import sailpoint.service.ListServiceColumnSelector;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ModuleStatusService extends BaseListService<BaseListServiceContext> {


    Map<String, List<ModuleStatusDTO.HostStatusDTO>> _moduleStatuses;


    public ModuleStatusService(SailPointContext context, BaseListServiceContext listServiceContext, ListServiceColumnSelector columnSelector) {
        super(context, listServiceContext, columnSelector);
    }

    public ModuleStatusService(SailPointContext context) {
        super(context, null, null);
    }

    public ListResult getModuleStatuses(QueryOptions ops) throws GeneralException {
        int count = countResults(Module.class, ops);
        List<Map<String, Object>> results = getResults(Module.class, ops);


       List<ModuleStatusDTO> dtos = new ArrayList<>();
       for (Map<String, Object> result : Util.safeIterable(results)) {
           ModuleStatusDTO module = new ModuleStatusDTO(result, this.columnSelector.getColumns(), null);
           if (module.isEnabled() != null && module.isEnabled()) {
               dtos.add(module);
           }
       }

        return new ListResult(dtos, count);
    }

    public static final String COL_STATUS = "statuses";
    public static final String COL_ENABLED = "enabled";

    @Override
    protected void calculateColumn(ColumnConfig config, Map<String, Object> rawQueryResults, Map<String,Object> map)
        throws GeneralException {

        //Add Module Statuses to map
        if (COL_STATUS.equals(config.getDataIndex())) {
            map.put(COL_STATUS, getModuleStatuses().get(String.valueOf(map.get("name"))));
        } else if (COL_ENABLED.equals(config.getDataIndex())) {
            map.put(COL_ENABLED, isModuleEnabled(String.valueOf(map.get("id"))));
        }

    }

    /**
     * Use the specified system configuration key to determine whether this module is enabled.
     *
     * @param moduleId The ID of the module to check.
     * @return boolean indicating whether the module is enabled or not. If a system configuration key isn't
     * specified or can't be found, false will be returned.
     * @throws GeneralException
     */
    protected boolean isModuleEnabled(String moduleId) throws GeneralException {
        Module module = context.getObjectById(Module.class, moduleId);
        String key = module.getString("enabledConfigKey");

        if (key != null) {
            return Configuration.getSystemConfig().getBoolean(key);
        }

        return false;
    }

    /**
     * Get statuses from all hosts for the given module
     * @param moduleName Module to get status for
     * @param includeRequests true to check for presence of outstanding Request for a given host
     * @return
     * @throws GeneralException
     */
    public ModuleStatusDTO getModuleStatus(String moduleName, Boolean includeRequests) throws GeneralException {

        //DO we need more here? -rap
        ModuleStatusDTO dto = new ModuleStatusDTO(moduleName);
        dto.setStatuses(getModuleStatuses().get(moduleName));

        if (Util.otob(includeRequests)) {
            for (ModuleStatusDTO.HostStatusDTO host : Util.safeIterable(dto.getStatuses())) {
                String servName = host.getServer();
                String requestName = String.format(ModuleStatusRequestExecutor.REQUEST_NAME, moduleName, servName);
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

    public Map<String, List<ModuleStatusDTO.HostStatusDTO>> getModuleStatuses() throws GeneralException {

        if (_moduleStatuses == null) {
            Map<String, List<ModuleStatusDTO.HostStatusDTO>> moduleStatuses = new HashMap<>();
            //We could get just the attributes, but Server object is light weight
            List<Server> servers = getContext().getObjects(Server.class);

            //Populate module statuses
            for (Server server : Util.safeIterable(servers)) {
                //Iterate each Server's stats and add them to the moduleStatuses map
                Map<String, Object> stats = (Map) server.get(Server.ATT_MODULE_STATUS);
                if (stats != null) {
                    for (String s : Util.safeIterable(stats.keySet())) {
                        if (!moduleStatuses.containsKey(s)) {
                            moduleStatuses.put(s, new ArrayList());
                        }
                        Map<String, Object> moduleStat = Util.otom(stats.get(s));
                        Boolean status = Util.otob(moduleStat.get(Server.ATT_MOD_STATUS));
                        Date lastRefresh = Util.getDate(moduleStat.get(Server.ATT_MOD_STATUS_DATE));
                        ModuleStatusDTO.HostStatusDTO dto = new ModuleStatusDTO.HostStatusDTO(server.getName(), status, lastRefresh, s);
                        if (!status) {
                            String msg = Util.otos(moduleStat.get(Server.ATT_MOD_STATUS_ERROR));
                            if (Util.isNotNullOrEmpty(msg)) {
                                dto.setExceptionMessage(msg);
                            }
                        }
                        ((List) moduleStatuses.get(s)).add(dto);

                    }
                }
            }
            _moduleStatuses = moduleStatuses;
        }
        return _moduleStatuses;
    }

}
