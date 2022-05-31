/* (c) Copyright 2014 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * Implementation of IntegrationExecutor interface used to route requests
 * to unstructured target collector that support provisioning.
 * 
 * Author: Rahul Borate
 *
 * Starting 6.4, IdentityIQ support provisioning via unstructured target collectors
 * defined on Application.
 * 
 * This is a transitional adapter between IntegrationExecutor and Collector 
 * until we can retool PlanCompiler and PlanEvaluator to use Collector
 * directly, at which point IntegrationExecutor will be deprecated and
 * accessed through a special Collector proxy.
 */

package sailpoint.integration;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.integration.AbstractIntegrationExecutor;

import sailpoint.api.SailPointContext;
import sailpoint.object.IntegrationConfig;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.ProvisioningResult;
import sailpoint.object.TargetSource;
import sailpoint.tools.Util;
import sailpoint.unstructured.TargetCollector;
import sailpoint.unstructured.TargetCollectorFactory;

/**
 * Implementation of IntegrationExecutor interface used to route requests
 * to Collectors that support provisioning.
 */
public class TargetCollectorExecutor extends AbstractIntegrationExecutor {

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////
    
    private static Log log = LogFactory.getLog(TargetCollectorExecutor.class);

    SailPointContext _context;
    IntegrationConfig _config;
    TargetCollector _collector;

    //////////////////////////////////////////////////////////////////////
    //
    // IntegrationInterface
    //
    //////////////////////////////////////////////////////////////////////

    public TargetCollectorExecutor() {
    }

    public void configure(SailPointContext context, IntegrationConfig config)
        throws Exception {

        _context = context;
        _config = config;

        String collectorName = config.getName();

        if ( Util.isNullOrEmpty(collectorName) ) {
            log.error("Unnamed IntegrationConfig");
        }
        else {
            TargetSource targetSource = context.getObjectByName(TargetSource.class, collectorName);
            if ( null == targetSource ) {
                log.error("Unable to find target collector with the name ["+ collectorName +"]");
            }
            else {
                try {
                    _collector = TargetCollectorFactory.getTargetCollector(targetSource);
                }
                catch (Throwable t) {
                    log.error(t);
                }
            }
            if ( null == _collector ) {
                log.error("Unable to instantiate target collector ["+ collectorName +"]");
            }
        }
    }

    public TargetCollector getTargetCollector() {
        return _collector;
    }

    /**
     * Make changes to nativeIdentity defined by a ProvisioningPlan.
     *
     * The interface takes a sailpoint.integration.ProvisioningPlan
     * rather than a sailpoint.object.ProvisioningPlan. It would be nice
     * if executors that operated mostly within IdentityIQ could get the 
     * better plan, it is easier to deal with and something could get
     * lost in the translation?
     *
     */
    public ProvisioningResult provision(ProvisioningPlan plan)
        throws Exception {

        ProvisioningResult result = null;

        if (_collector == null) {
            // we will have logged this in configure() don't log again
            // for every request...too much clutter
        }
        else {
            result = _collector.provision(plan);
        }

        return result;
    }
}

