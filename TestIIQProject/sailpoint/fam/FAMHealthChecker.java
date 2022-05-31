package sailpoint.fam;

import sailpoint.api.SailPointFactory;
import sailpoint.object.Configuration;
import sailpoint.object.Module;
import sailpoint.service.module.ModuleHealthChecker;
import sailpoint.tools.GeneralException;

public class FAMHealthChecker implements ModuleHealthChecker {

    public FAMHealthChecker(Module m) { }

    @Override
    public void checkHealthStatus() throws GeneralException {
        checkHealthStatus(Configuration.getFAMConfig());
    }
    
    public void checkHealthStatus(Configuration config) throws GeneralException {
        if (config == null) {
            throw new GeneralException("Configuration object can not be null when checking FAM health status");
        }
        
        FAMService svc = new FAMService(SailPointFactory.getCurrentContext());
        svc.checkHealth();
    }
}
