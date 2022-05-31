/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
/**
 * Utility class to instantiate IntegrationExecutors.
 *
 * Author: Jeff
 *
 * I added this to encapsulate the logic that needs to 
 * be aware of the older sailpoint.object.IntegrationExecutor interface
 * and wrap it in an ExecutorAdapter if we find one.
 *
 * Now that we have this, it could be an interesting home for other
 * non-specific integration utilities.
 *
 */

package sailpoint.integration;

import sailpoint.object.IntegrationConfig;
import sailpoint.tools.GeneralException;

public class IntegrationManager {

    /**
     * Instantiate an executor for an integration configuration.
     */
    public static IntegrationExecutor getExecutor(IntegrationConfig config) 
        throws GeneralException {

        return getExecutor(config.getExecutor());
    }

    /**
     * Instantiate an executor given an class name.
     * If we find one of the older sailpoint.object.IntegrationExecutor
     * implementations we wrap it in an adapter class.
     */
    public static IntegrationExecutor getExecutor(String clsname) 
        throws GeneralException {

        IntegrationExecutor exec = null;

        if (clsname != null) {
            try {
                Class cls = Class.forName(clsname);
                Object o = cls.newInstance();
                if (o instanceof IntegrationExecutor)
                    exec = (IntegrationExecutor)o;

                else if (o instanceof sailpoint.object.IntegrationExecutor) {
                    // an old one, wrap it in an adapter
                    exec = new ExecutorAdapter((sailpoint.object.IntegrationExecutor)o);
                }
                else {
                    throw new GeneralException(cls.getName() + 
                                               " is not an IntegrationExecutor");
                }
            }
            catch (Throwable t) {
                throw new GeneralException(t);
            }
        }

        return exec;
    }

}
