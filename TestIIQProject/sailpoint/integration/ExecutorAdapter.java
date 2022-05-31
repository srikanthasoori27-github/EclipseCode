/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
/**
 * Temporary kludge to ease the upgrade from the older 
 * sailpoint.object.IntegrationExecutor interface to the new, improved,
 * sailpoint.integration.IntegrationExecutor.
 *
 * When we get ready to instantiate the executor, we'll use reflection to
 * see which interface is being implemented and if we find the
 * old one it will be wrapped in this adapter class.
 *
 * Author: Jeff
 *
 * The wiring is a bit confusing here so pay attention.
 * We extend AbstractIntegrationExecutor because it can already
 * act as a wrapper around an IntegrationInterface.  Since
 * the old sailpoint.object.IntegrationExecutor extends 
 * IntegrationInterface we install it as the "proxy" in 
 * AbstractIntegrationExecutor so the calls to the basic methods are
 * passed through.
 *
 * For the one sailpoint.object.IntegrationExecutor extended method 
 * we maintain another proxy reference to the same object, but
 * typed correctly so we can call the old method without downcasting.
 *
 */

package sailpoint.integration;

import sailpoint.object.Bundle;

/**
 * Temporary support for migrating from the the older 
 * sailpoint.object.IntegrationExecutor interface to the new
 * sailpoint.integration.IntegrationExecutor.
 *
 * When we get ready to instantiate the executor, we will use reflection to
 * see which interface is being implemented and if we find the
 * old one it will be wrapped in this adapter class.
 */
public class ExecutorAdapter extends AbstractIntegrationExecutor {

    /**
     * The instance of the executor interface.
     */
    sailpoint.object.IntegrationExecutor _oldExecutor;


    public ExecutorAdapter(sailpoint.object.IntegrationExecutor exec) {

        // this will also be _proxy in AbstractIntegrationExecutor
        super(exec);

        // and stored again here downcase
        _oldExecutor = exec;
    }


    /**
     * This is the only extended method we need to proxy
     * to the old interface. Old method requires an IntegrationConfig
     * as an argument.
     */
    public void finishRoleDefinition(Bundle src, 
                                     RoleDefinition dest)
        throws Exception {

        if (_oldExecutor != null)
            _oldExecutor.finishRoleDefinition(_config, src, dest);
    }

}
