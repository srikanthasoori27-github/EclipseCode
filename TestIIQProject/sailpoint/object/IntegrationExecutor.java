/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
/**
 * *** THIS INTERFACE IS OBSOLETE ***
 *
 * If you want to create an integration executor, look at the
 * files in sailpoint/integration, and either extend 
 * sailpoint.integration.AbstractIntegrationExecutor (recommended)
 * or implement sailpoint.integration.IntegrationExecutor.
 *
 * This interface is kept around for temporary backward compatibility
 * with older integrations.  If we find one of these at runtime
 * it will be wrapped in a sailpoint.integration.ExecutorAdapter
 * so it can be plugged into the new plumbing.
 *
 * Author: Jeff
 * 
 */

package sailpoint.object;

import sailpoint.integration.IntegrationInterface;
import sailpoint.integration.RoleDefinition;

/**
 * @deprecated
 *
 * The interface of a class that must be registered with
 * an IntegrationConfig to process requests to an IDM system.
 *
 * You should no longer use this interface. Use 
 * sailpoint.integration.AbstractIntegrationExecutor instead.
 *
 */
public interface IntegrationExecutor extends IntegrationInterface {

    /**
     * Called prior to sending an addRole request to the IDM system.
     * The RoleDefinition to be sent can be modified with extra attributes
     * derived from the source role.
     */
    public void finishRoleDefinition(IntegrationConfig config,
                                     Bundle src, 
                                     RoleDefinition dest)
        throws Exception;


}

