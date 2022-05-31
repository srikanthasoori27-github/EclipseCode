/*
 * A proxy that wraps a target collector and implements various behavior.
 *
 * Author(s): Jeff Larson, Rahul Borate
 *
 */

package sailpoint.unstructured;

import java.util.List;
import java.util.Map;

import openconnector.ConnectorServices;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.connector.ConnectorException;
import sailpoint.connector.ConnectorProxy;
import sailpoint.object.Target;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.ProvisioningResult;
import sailpoint.tools.CloseableIterator;
import sailpoint.tools.GeneralException;
import sailpoint.unstructured.TargetCollector;

/**
 * A proxy that wraps a target collector and performs transformations between
 * the raw model used by the target collector and model we expose within IIQ.
 *
 * The primary operation is: evaluation of the "customization rule".
 */
public class TargetCollectorProxy implements TargetCollector {

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    private static Log log = LogFactory.getLog(TargetCollectorProxy.class);

    /**
     * The target collector.
     */
    private TargetCollector _collector;
    
    //////////////////////////////////////////////////////////////////////
    //
    // Constructor
    //
    //////////////////////////////////////////////////////////////////////

    public TargetCollectorProxy(TargetCollector collector) {
        _collector = collector;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Configuration
    //
    //////////////////////////////////////////////////////////////////////

    public List<String> getMessages() {
        return _collector.getMessages();
    }

    public List<String> getErrors() {
        return _collector.getErrors();
    }

    /**
     * Dummy method sets the ConnectorServices object in the TargetCollector.
     */
    @Override
    public void setConnectorServices(ConnectorServices conServices) {
        _collector.setConnectorServices(conServices);
    }

    /**
     * @return the ConnectorServices object from the TargetCollector.
     */
    @Override
    public ConnectorServices getConnectorServices() {
        return _collector.getConnectorServices();
    }

    ///////////////////////////////////////////////////////////////////////////
    //
    // Ping
    //
    ///////////////////////////////////////////////////////////////////////////
    /**
     * @see sailpoint.unstructured.TargetCollector#testConfiguration()
     * @ignore We can test the connection to the app
     */
    public void testConfiguration() throws GeneralException {
        _collector.testConfiguration();
    }

    ///////////////////////////////////////////////////////////////////////////
    //
    // Aggregation / get part
    //
    ///////////////////////////////////////////////////////////////////////////

    /**
     * Return an Iterator of CloseableIterator.
     */
    public CloseableIterator<Target> iterate(Map<String, Object> ops)
        throws GeneralException {

        return _collector.iterate(ops);
    }

    ///////////////////////////////////////////////////////////////////////////
    //
    // Provisioning
    //
    ///////////////////////////////////////////////////////////////////////////

    /**
     * make changes to nativeIdentity defined by a ProvisioningPlan.
     *
     * evaluates pre and post provisioning action "customization rule"
     */
    public ProvisioningResult provision(ProvisioningPlan plan)
    throws ConnectorException, GeneralException
    {
        ConnectorProxy.runBeforeProvisioningRule(plan);

        ProvisioningResult result = null;
        // now do the provisioning...
        result = _collector.provision(plan);

        ConnectorProxy.runAfterProvisioningRule(plan, result);

        return result;
    }

    public TargetCollector getCollector() {
        return _collector;
    }
}
