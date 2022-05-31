package sailpoint.tools.upgrade;

import sailpoint.api.SailPointContext;
import sailpoint.object.Server;
import sailpoint.object.ServiceDefinition;
import sailpoint.server.ImportExecutor;
import sailpoint.server.MonitoringService;
import sailpoint.server.upgrade.framework.BaseUpgrader;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Abstract upgrader - sub-classes must implement getModuleToAdd()
 */
public abstract class MonitoringConfigUpdater extends BaseUpgrader {

    abstract protected String getModuleToAdd();

    @Override
    public void performUpgrade(ImportExecutor.Context context) throws GeneralException {
        SailPointContext spContext = context.getContext();

        // Add the new module name to the monitoringConfig map inside the 'Monitoring' ServiceDefinition
        ServiceDefinition svcDef = spContext.getObjectByName(ServiceDefinition.class, MonitoringService.NAME);
        if (svcDef != null) {
            Map monitoringConfig = (Map)svcDef.get(Server.ATT_MONITORING_CFG);
            if (monitoringConfig == null) {
                monitoringConfig = new HashMap();
                svcDef.put(Server.ATT_MONITORING_CFG, monitoringConfig);
            }
            List<String> monitoredModules = Util.getStringList(monitoringConfig, Server.ATT_MONITORING_CFG_MODULES);
            if (monitoredModules == null) {
                monitoredModules = new ArrayList<>();
                monitoringConfig.put(Server.ATT_MONITORING_CFG_MODULES, monitoredModules);
            }

            // if module name isn't already in the list, add it
            String moduleToAdd = getModuleToAdd();
            if (Util.isNotNullOrEmpty(moduleToAdd)) {
                if (!monitoredModules.contains(moduleToAdd)) {
                    info("Registering '" + moduleToAdd + "' for monitoring");
                    monitoredModules.add(moduleToAdd);
                    monitoringConfig.put(Server.ATT_MONITORING_CFG_MODULES, monitoredModules);
                }
                spContext.saveObject(svcDef);
                spContext.commitTransaction();
                spContext.decache();
            }
        }
    }

}
