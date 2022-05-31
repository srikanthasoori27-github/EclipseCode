/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.unstructured;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import sailpoint.connector.CollectorServices;
import sailpoint.connector.RPCService;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.ProvisioningResult;
import sailpoint.object.Target;
import sailpoint.object.TargetSource;
import sailpoint.object.ProvisioningPlan.AbstractRequest;
import sailpoint.object.ProvisioningPlan.ObjectOperation;
import sailpoint.tools.CloseableIterator;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

/**
 * @author <a href="mailto:dan.smith@sailpoint.com">Dan Smith</a>
 */
public abstract class AbstractTargetCollector extends CollectorServices 
                                              implements TargetCollector {

	public static final String DISABEL_HOSTNAME_VERIFICATION = "disableHostnameVerification";
    //////////////////////////////////////////////////////////////////////
    //
    // Fields / Constructor
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * The TargetSource holds the configuration settings that will
     * drive the target collection process.
     */
    private TargetSource _source;

    /**
     * List of accumilated messages.
     */
    protected List<String> _messages;

    /**
     * List of accumilated errors
     */
    protected List<String> _errors;

    public AbstractTargetCollector(TargetSource source) {
        super(source.getConfiguration());
        _source = source;
        _errors = new ArrayList<String>();
        _messages = new ArrayList<String>();
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Properties
    //
    //////////////////////////////////////////////////////////////////////

    public TargetSource getTargetSource() {
        return _source;
    }

    public void setTargetSource(TargetSource source) {
        _source = source;
    }

    public List<String> getErrors() {
        return _errors;
    }

    public List<String> getMessages() {
        return _messages;
    }

    ///////////////////////////////////////////////////////////////////////////
    //
    // Abstract methods that must be over-ridden by new Collectors
    //
    ///////////////////////////////////////////////////////////////////////////

    /**
     * Returns an Iterator over the Targets.
     */
    abstract public CloseableIterator<Target> iterate(Map<String, Object> ops) 
        throws GeneralException;

    /**
     * Test the TargetSource configuration.
     */
    abstract public void testConfiguration() throws GeneralException;

    /**
     * Default implementation that parses the plan and
     * calls down to a simpler set of methods that can be
     * implemented by the subclass.
     */
    public ProvisioningResult provision(ProvisioningPlan plan) throws GeneralException {

        ProvisioningResult result = new ProvisioningResult();
        List<AbstractRequest> requests = plan.getAllRequests();

        for (AbstractRequest req : requests ) {
            ObjectOperation op = req.getOp();
            switch (op) {
                case Create:
                break;
                case Modify: {
                    result = update(req);
                    req.setResult(result);
                }
                break;
                case Delete:
                break;
                case Disable:
                break;
                case Enable:
                break;
                case Unlock:
                break;
            }
        }

        return result;
    }

    /**
     * This should be implemented by target systems collector classes.
     * Please make sure to override this method in your class.
     * @param req
     * @return result;
     * @throws GeneralException
     */
    public ProvisioningResult update(AbstractRequest req) throws GeneralException {

        ProvisioningResult result = null;
        throwUnsupported("update");
        return result;
    }

    private void throwUnsupported(String method) {
        throw new UnsupportedOperationException(method + " not supported by " + getClass());
    }

	protected RPCService getService() throws GeneralException {
		return getService(false);
	}

	/**
	 * Creates RPCService instance based on IQService configuration set on
	 * application.
	 *
	 * @param performInit
	 * @return
	 * @throws GeneralException
	 */

	protected RPCService getService(boolean performInit)
			throws GeneralException {
		RPCService service = null;
		try {
			// IQService Configuration Info
			String iqServiceHost = null;
			String iqServicePort = null;
			boolean isUseTls = false;
			boolean disableHostNameVerification = false;
			iqServiceHost = getRequiredStringAttribute(RPCService.CONFIG_IQSERVICE_HOST);
			iqServicePort = getRequiredStringAttribute(RPCService.CONFIG_IQSERVICE_PORT);
			isUseTls = getBooleanAttribute(RPCService.CONFIG_IQSERVICE_TLS);
			disableHostNameVerification = getBooleanAttribute(DISABEL_HOSTNAME_VERIFICATION);
			int port = Integer.parseInt(iqServicePort);
			// IQService host and port are mandatory, throw error if not
			// available
			if (Util.isNullOrEmpty(iqServiceHost) || port <= 0) {
				throw new GeneralException(
						"IQService Host and/or Port must have a valid value.");
			}
			service = new RPCService(iqServiceHost, port, performInit,
					isUseTls, disableHostNameVerification);
			service.setConnectorServices(getConnectorServices());
		} catch (GeneralException e) {
			throw new GeneralException(e);
		}
		return service;
	}

}
