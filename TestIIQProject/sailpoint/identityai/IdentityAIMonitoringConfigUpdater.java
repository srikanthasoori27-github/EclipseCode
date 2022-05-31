package sailpoint.identityai;

import sailpoint.tools.upgrade.MonitoringConfigUpdater;

public class IdentityAIMonitoringConfigUpdater extends MonitoringConfigUpdater {

    protected String getModuleToAdd() {
        return "AIServices";
    }
}
