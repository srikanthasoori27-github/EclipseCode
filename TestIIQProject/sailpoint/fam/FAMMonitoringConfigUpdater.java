package sailpoint.fam;

import sailpoint.tools.upgrade.MonitoringConfigUpdater;

public class FAMMonitoringConfigUpdater extends MonitoringConfigUpdater {

    @Override
    protected String getModuleToAdd() {
        return "FAM";
    }
}
