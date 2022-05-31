package sailpoint.identityai;

import sailpoint.object.Module;
import sailpoint.service.module.ModuleHealthChecker;
import sailpoint.tools.GeneralException;

public class IdentityAIHealthChecker implements ModuleHealthChecker {

    public IdentityAIHealthChecker(Module module) {
    }

    @Override
    public void checkHealthStatus() throws GeneralException {
        try {
            IdentityAIService.testPooledConnection();
        }
        catch (GeneralException e) {
            throw e;
        }
        catch (Exception e) {
            throw new GeneralException(e);
        }
    }
}
