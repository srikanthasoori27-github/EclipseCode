package sailpoint.service.module;

import sailpoint.tools.GeneralException;

public interface ModuleHealthChecker {

    void checkHealthStatus() throws GeneralException;
}
