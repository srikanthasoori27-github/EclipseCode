/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.unstructured;

import java.util.Map;
import java.util.List;

import openconnector.ConnectorServices;
import sailpoint.connector.ConnectorException;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.ProvisioningResult;
import sailpoint.object.Target;
import sailpoint.tools.GeneralException;
import sailpoint.tools.CloseableIterator;

/**
 * @author <a href="mailto:dan.smith@sailpoint.com">Dan Smith</a>
 */
public interface TargetCollector {

    /**
     * Returns a scalable Iterator that can be used to iterate
     * over the Targets.
     */
    public CloseableIterator<Target> iterate(Map<String, Object> ops) 
        throws GeneralException;

    /**
     * Test configuration of the TargetSource.  This method will
     * be called by the UI and should test everything necessary
     * to iterate over the TargetSource.
     */
    public void testConfiguration() throws GeneralException;

    /**
     * Execute a provisioning plan through unstructured target collector.
     * The returned result object will contain a status indicating
     * whether the request succeeded, failed, or needs to be retried.
     * In the case of a retry, the implementation is allowed to make
     * modifications to the passed plan in order to save state for the
     * retry. The plan is therefore both an input and an output from
     * this method.
     */
    public ProvisioningResult provision(ProvisioningPlan plan)
        throws ConnectorException, GeneralException;

    /**
     * Returns the errors that were collected during the collection
     * process.
     */
    public List<String> getErrors();

    /**
     * Returns the warnings that were collected during the collection
     * process.
     */
    public List<String> getMessages();

    /**
     * Sets the ConnectorServices object.
     */
    void setConnectorServices(ConnectorServices conServices);

    /**
     * @return the ConnectorServices object.
     */
    ConnectorServices getConnectorServices();
}
