/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
/**
 * The interface of a class that must be registred with
 * an IntegrationConfig to process requests to an IDM system.
 * This is an extension of sailpoint.integration.IntegrationInterface
 * to add some extra methods specific to the IIQ side of the integration.
 *
 * Author: Jeff
 *
 * NOTE WELL:
 *
 * Implmentors are REQUIRED to extend AbstractIntegrationExecutor
 * rather than directly implement this class.  This will allow
 * us to extend the interface more easily without breaking old executors.
 *
 * As of 5.2 this class is deprecated and should no longer be used
 * in new code.  It will be retained for backward compatibility with
 * with custom integration executors, but all stock integrations
 * should now use the Connector/Application model.
 *
 * It is temporarily still the foundation of the provisioning engine
 * (PlanEvaluator and PlanCompler) but eventually that will be redesigned
 * so that provisioning is implemented directly on Connector/Application
 * with special adapters to call older IntegrationExecutors when necessary.
 * 
 * The status of the integration/common package is unkown right now.
 * It is still a useful toolkit to have for REST communication but the
 * interface needs to be extended to support aggregation among other things.
 *
 * There are two interfaces with this name, one in sailpoint.integration
 * and one in sailpoint.object.  The one in sailpoint.object is the "old"
 * one and is maintained temporarily for backward compatibility with
 * a few custom executors (I think only for POCs, not sure about any
 * actual deployments).
 *
 * When we instantate the executor, IntegrationManager will check to 
 * see which class is being referenced and if it finds a reference
 * to one extending sailpoint.object.IntegrationExecutor it will wrap
 * it in an Adapter so that it may still be used with the new plumbing.
 * 
 */

package sailpoint.integration;

import sailpoint.api.SailPointContext;
import sailpoint.connector.Connector;
import sailpoint.integration.IntegrationInterface;
import sailpoint.integration.RoleDefinition;
import sailpoint.object.Bundle;
import sailpoint.object.Identity;
import sailpoint.object.IntegrationConfig;
import sailpoint.object.ProvisioningResult;
import sailpoint.unstructured.TargetCollector;

/**
 * The interface of a class that must be registered with
 * an IntegrationConfig to process requests to an IDM system.
 *
 * This is an extension of sailpoint.integration.IntegrationInterface
 * to add some extra methods specific to the IdentityIQ side of the integration.
 */
public interface IntegrationExecutor extends IntegrationInterface {

    /**
     * Perform initial configuration.  
     * This will be called before any other methods are called.
     *
     * It is permissible to save a copy of the SailPointContext and
     * InterationConfig for future use. It Is strongly recommended you
     * do not modify the IntegrationConfig to prevent accidental 
     * persistence of changes.
     *
     * Note that we also inherit a configure(Map) method from
     * IntegrationInterface.
     */
    public void configure(SailPointContext context,
                          IntegrationConfig config)
        throws Exception;

    /**
     * Return the Connector that is being wrapped by this executor.
     * This is only for a few special places in system code that 
     * are still designed around the old IntegrationExecutor interface
     * but want to have access to the full Connector for some things.
     */
    public Connector getConnector();

    /**
     * Return the target collector that is being wrapped by this executor.
     * This is only for a few special places in system code that are still
     * designed around the old IntegrationExecutor interface but want to
     * have access to the full target collector for some things.
     */
    public TargetCollector getTargetCollector();

    /**
     * Method called by the provisioning engine when it is ready to send down
     * a provisioning request. This takes different arguments than the
     * provision() method inherited from IntegrationInterface. Here we
     * a sailpoint.object.ProvisioningPlan which provides a richer model
     * and may be annotated with results.
     *
     * The implementation of this in AbstractIntegrationExecutor converts
     * it to a sailpoint.integration.ProvisioningPlan which is what
     * is expected by the older integrations.
     *
     */
    public ProvisioningResult provision(sailpoint.object.ProvisioningPlan plan)
        throws Exception;

    /**
     * An older extension of the IntegrationInterface that uses the
     * simplified plan but passes in the Identity object.
     * It will no longer be called directly by the provisioning engine,
     * instead the default implementation of the provision() method that
     * takes a sailpoint.object.ProvisioningPlan will convert the plan
     * to a sailpoint.integration.ProvisioningPlan and call this method.
     *
     * The default implementation of this in AbstractIntegrationExecutor
     * is to ignore the Identity and pass the plan along to the
     * provision() method inherited from IntegrationInterface.
     */
    public RequestResult provision(Identity identity,
                                   sailpoint.integration.ProvisioningPlan plan)
        throws Exception;

    /**
     * Check the status of a previous provisioning request.  
     * This replaces the older IntegrationInterface.getRequestStatus 
     * method to allow returning the newer ProvisioningResult model.
     */
    public ProvisioningResult checkStatus(String requestId)
        throws Exception;

    /**
     * Called prior to sending an addRole request to the IDM system.
     * The RoleDefinition to be sent may be modified with extra attributes
     * derived from the source role.
     *
     * @ignore
     * It would be more consistent with provision() if this were just
     * an alternate addRole() method that took both arguments.  It's
     * a little more complex though since this can be called once for
     * each role when flattening a hierarchy so for simple annotations
     * it's easier to have a per-role callback.
     */
    public void finishRoleDefinition(Bundle src, 
                                     RoleDefinition dest)
        throws Exception;

}
