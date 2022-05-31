package sailpoint.service.module;

import sailpoint.api.SailPointContext;
import sailpoint.object.Module;
import sailpoint.tools.GeneralException;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

public class ModuleFactory {
    public static ModuleHealthChecker healthChecker(String moduleName, SailPointContext ctx) throws GeneralException {
        Module module = ctx.getObjectByName(Module.class, moduleName);
        if (module == null) {
            throw new GeneralException("Unknown module " + moduleName);
        }

        return instantiateModuleHealthChecker(module);
    }

    private static ModuleHealthChecker instantiateModuleHealthChecker(Module module) throws GeneralException {
        String className = module.getString(Module.ATT_HEALTH_CHECK_CLASS);
        if (className == null) {
            throw new GeneralException("No health check class defined");
        }

        Class healthCheckClass = null;
        try {
            healthCheckClass = Class.forName(className);
        } catch (ClassNotFoundException e) {
            throw new GeneralException("Cannot find health check class " + className);
        }

        // the class should have a constructor that takes a Module parameter
        Constructor constructor = null;
        try {
            constructor = healthCheckClass.getConstructor(Module.class);
        } catch (NoSuchMethodException e) {
            throw new GeneralException("Cannot find required constructor in class " + className);
        }

        ModuleHealthChecker moduleHealthChecker = null;
        try {
            moduleHealthChecker = (ModuleHealthChecker) constructor.newInstance(module);
        } catch (InstantiationException e) {
            throw new GeneralException(e);
        } catch (IllegalAccessException e) {
            throw new GeneralException(e);
        } catch (InvocationTargetException e) {
            throw new GeneralException(e);
        }

        return moduleHealthChecker;
    }

}
