/* (c) Copyright 2013 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.rapidapponboarding.reports;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import sailpoint.api.SailPointContext;
import sailpoint.api.SailPointFactory;
import sailpoint.connector.Connector;
import sailpoint.connector.ConnectorException;
import sailpoint.integration.ListResult;
import sailpoint.object.Application;
import sailpoint.object.Application.Feature;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.IntegrationConfig;
import sailpoint.object.ManagedResource;
import sailpoint.object.ProvisioningConfig;
import sailpoint.object.QueryOptions;
import sailpoint.object.ResourceObject;
import sailpoint.rapidapponboarding.logger.LogEnablement;
import sailpoint.tools.CloseableIterator;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
/**
 * A resource to get the connection information for all applications .
 *
 * @author <a href="mailto:rohit.gupta@sailpoint.com">Rohit Gupta</a>
 */
public class AppHealthCheckReport {
	private static final Log appHealthCheckLogger = LogFactory
			.getLog(AppHealthCheckReport.class);
	private List<Map<String, Object>> _results = new ArrayList<Map<String, Object>>();
	private List<String> _instances;
	private SailPointContext _context;
	private String _typeConnection;
	private String _applicationName;
	private int _timeout;
	private int _threads;
	private List<Callable<Map<String, Object>>> callableList = new ArrayList<Callable<Map<String, Object>>>();
	public AppHealthCheckReport(SailPointContext context,
			String applicationName, String typeConnection, int timeout) {
		LogEnablement.isLogDebugEnabled(appHealthCheckLogger,"In Constructor SSAppHealthCheckReport");
		this._context = context;
		this._timeout = timeout;
		this._typeConnection = typeConnection;
		this._applicationName = applicationName;
		LogEnablement.isLogDebugEnabled(appHealthCheckLogger,"Out Constructor SSAppHealthCheckReport");
	}
	public AppHealthCheckReport(SailPointContext context, List applications,
			int threads, int timeout) {
		LogEnablement.isLogDebugEnabled(appHealthCheckLogger,"In Constructor SSAppHealthCheckReport");
		this._context = context;
		this._timeout = timeout;
		this._instances = applications;
		this._threads = threads;
		LogEnablement.isLogDebugEnabled(appHealthCheckLogger,"Out Constructor SSAppHealthCheckReport");
	}
	public AppHealthCheckReport(SailPointContext context) {
		LogEnablement.isLogDebugEnabled(appHealthCheckLogger,"In Constructor SSAppHealthCheckReport");
		this._context = context;
		LogEnablement.isLogDebugEnabled(appHealthCheckLogger,"Out Constructor SSAppHealthCheckReport");
	}
	public ListResult getAppsInfo() throws GeneralException {
		QueryOptions qo = new QueryOptions();
		LogEnablement.isLogDebugEnabled(appHealthCheckLogger,"timeout " + _timeout);
		LogEnablement.isLogDebugEnabled(appHealthCheckLogger,"instances " + _instances);
		LogEnablement.isLogDebugEnabled(appHealthCheckLogger,"threads " + _threads);
		if (this._instances != null) {
			qo.addFilter(Filter.in("name", this._instances));
		}
		LogEnablement.isLogDebugEnabled(appHealthCheckLogger,"QueryOptions " + qo.toString());
		int count = this._context.countObjects(Application.class, qo);
		LogEnablement.isLogDebugEnabled(appHealthCheckLogger,"count " + count);
		/**
		 * Create Callable list for each instance
		 */
		for (String app : this._instances) {
			SSAppHealthCheckReportCallable SSAppHealthCheckReportCallable = new SSAppHealthCheckReportCallable(
					app);
			callableList.add(SSAppHealthCheckReportCallable);
		}
		LogEnablement.isLogDebugEnabled(appHealthCheckLogger,"Total Callable Objects. " + callableList.size());
		int processors = Runtime.getRuntime().availableProcessors();
		LogEnablement.isLogDebugEnabled(appHealthCheckLogger,"Number of Processors " + processors);
		processors = processors - 1;
		if (processors <= 1) {
			processors = 1;
		}
		LogEnablement.isLogDebugEnabled(appHealthCheckLogger,"Number of Processors Subtracted " + processors);
		ThreadPoolTasksProcessor processor = new ThreadPoolTasksProcessor(
				_threads != 0 ? _threads : processors, _timeout != 0 ? _timeout
						: 5000, callableList, _results, false);
		LogEnablement.isLogDebugEnabled(appHealthCheckLogger,"All concurrent tasks about to start.");
		processor.start();
		LogEnablement.isLogDebugEnabled(appHealthCheckLogger,"All concurrent tasks ended.");
		/**
		 * Are All Tasks are Properly Invoked
		 */
		boolean resubmitRequest = false;
		for (Callable call : callableList) {
			LogEnablement.isLogDebugEnabled(appHealthCheckLogger,"App Name ->"
					+ ((AppHealthCheckReport.SSAppHealthCheckReportCallable) call)
							.getApp());
			LogEnablement.isLogDebugEnabled(appHealthCheckLogger,"Is invoked ->"
					+ ((AppHealthCheckReport.SSAppHealthCheckReportCallable) call)
							.isInvoked());
			if (!((AppHealthCheckReport.SSAppHealthCheckReportCallable) call)
					.isInvoked()) {
				resubmitRequest = true;
			}
		}
		LogEnablement.isLogDebugEnabled(appHealthCheckLogger,"RESUBMIT PROCESSOR REQUEST ->" + resubmitRequest);
		if (resubmitRequest) {
			_results = new ArrayList<Map<String, Object>>();
			LogEnablement.isLogDebugEnabled(appHealthCheckLogger,"Number of Restarted Processors Subtracted " + processors);
			ThreadPoolTasksProcessor restartProcessor = new ThreadPoolTasksProcessor(
					_threads != 0 ? _threads : processors,
					_timeout != 0 ? _timeout : 5000, callableList, _results,
					true);
			LogEnablement.isLogDebugEnabled(appHealthCheckLogger,"All concurrent tasks about to start.");
			restartProcessor.start();
			LogEnablement.isLogDebugEnabled(appHealthCheckLogger,"All concurrent tasks ended.");
			for (Callable call : callableList) {
				LogEnablement.isLogDebugEnabled(appHealthCheckLogger,"RESUBMIT RESULT App Name ->"
						+ ((AppHealthCheckReport.SSAppHealthCheckReportCallable) call)
								.getApp());
				LogEnablement.isLogDebugEnabled(appHealthCheckLogger,"RESUBMIT RESULT Is invoked ->"
						+ ((AppHealthCheckReport.SSAppHealthCheckReportCallable) call)
								.isInvoked());
			}
		}
		List<String> processedAps = new ArrayList<String>();
		List<String> unProcessedAps = new ArrayList<String>();
		for (String app : this._instances) {
			boolean processed = false;
			for (Map<String, Object> result : this._results) {
				if (app.equalsIgnoreCase((String) result.get("name"))) {
					processedAps.add(app);
					processed = true;
					break;
				}
			}
			if (!processed) {
				unProcessedAps.add(app);
			}
		}
		for (String appName : unProcessedAps) {
			_results.add(convertAppToMapRedFlag(appName));
		}
		LogEnablement.isLogDebugEnabled(appHealthCheckLogger,"Processed Apps " + processedAps.toString());
		LogEnablement.isLogDebugEnabled(appHealthCheckLogger,"Un Processed Apps " + unProcessedAps.toString());
		ListResult lr = new ListResult(_results, count);
		LogEnablement.isLogDebugEnabled(appHealthCheckLogger,"Final ListResult " + lr);
		return lr;
	}
	/**
	 * Since the inner class "SSAppConnectivityCallable" implements callable
	 * interface so there are no SailPointContext/s for this thread. Therefore
	 * this method is used to create context. It is called by other methods to
	 * get SailPointContext at the beginning.
	 *
	 */
	private SailPointContext init() throws Exception {
		SailPointContext _threadContext = SailPointFactory.createContext();
		LogEnablement.isLogDebugEnabled(appHealthCheckLogger,"Creating Sailpoint Context " + _threadContext);
		return _threadContext;
	}
	/**
	 * Method returns remmediators of app.
	 *
	 * @param app
	 *            the application name
	 * @return a Map
	 */
	public List getRemediators(String app) {
		LogEnablement.isLogDebugEnabled(appHealthCheckLogger,"Enter getRemediators");
		Application appObject = null;
		List<String> remediators = new ArrayList<String>();
		try {
			appObject = _context.getObjectByName(Application.class, app);
			if (appObject.getRemediators() != null) {
				for (Identity identity : appObject.getRemediators()) {
					String displayName = identity.getDisplayName();
					if (displayName == null) {
						displayName = identity.getName();
					}
					remediators.add(identity.getDisplayName());
				}
			}
			if (appObject != null) {
				_context.decache(appObject);
			}
		} catch (GeneralException e) {
			LogEnablement.isLogErrorEnabled(appHealthCheckLogger,"Error Finding Application Object " + e.getMessage());
		} catch (Exception e) {
			LogEnablement.isLogErrorEnabled(appHealthCheckLogger,"Error " + e.getMessage());
		}
		return remediators;
	}
	/**
	 * IDM Integrations Multiplex App Cloud GateWay
	 *
	 * @param app
	 *            the application name
	 * @return a Map
	 */
	public String getDualChannel(String app) {
		LogEnablement.isLogDebugEnabled(appHealthCheckLogger,"Enter dualChannel");
		Application appObject = null;
		String dualChannel = null;
		try {
			appObject = _context.getObjectByName(Application.class, app);
			if (appObject.getProxy() != null) {
				dualChannel = (String) appObject.getProxy().getName();
			}
			if (dualChannel == null) {
				dualChannel = managedResourceNameFromIntegrationProvisioningConfig(app);
			}
			if (appObject != null) {
				_context.decache(appObject);
			}
		} catch (GeneralException e) {
			LogEnablement.isLogErrorEnabled(appHealthCheckLogger,"Error Finding Application Object " + e.getMessage());
		} catch (Exception e) {
			LogEnablement.isLogErrorEnabled(appHealthCheckLogger,"Error " + e.getMessage());
		}
		LogEnablement.isLogDebugEnabled(appHealthCheckLogger,"End dualChannel " + dualChannel);
		return dualChannel;
	}
	/**
	 * Integration Config + Provisioning Config
	 *
	 * @param app
	 *            the application name
	 * @return a Map
	 * @throws GeneralException
	 */
	public String managedResourceNameFromIntegrationProvisioningConfig(
			String app) throws GeneralException {
		LogEnablement.isLogDebugEnabled(appHealthCheckLogger,"Enter managedResourceNameFromIntegrationProvisioningConfig");
		Application appObject = null;
		String managedResource = null;
		try {
			appObject = _context.getObjectByName(Application.class, app);
			QueryOptions qo = new QueryOptions();
			qo.addFilter(Filter.notnull("id"));
			java.util.Iterator<Object[]> it = _context.search(
					IntegrationConfig.class, qo, "id");
			List<String> integrationIds = new ArrayList();
			if (it != null) {
				while (it.hasNext()) {
					Object[] objArr = (Object[]) it.next();
					if (objArr != null && objArr.length == 1
							&& objArr[0] != null) {
						String integrationId = objArr[0].toString();
						integrationIds.add(integrationId);
					}
				}
			}
			Util.flushIterator(it);
			List<IntegrationConfig> listOfIntegrationConfigObs = new ArrayList();
			for (String integrationId : integrationIds) {
				IntegrationConfig listOfIntegrationConfig = _context
						.getObjectById(IntegrationConfig.class, integrationId);
				listOfIntegrationConfigObs.add(listOfIntegrationConfig);
			}
			for (IntegrationConfig integrationConfig : listOfIntegrationConfigObs) {
				ManagedResource mres = integrationConfig
						.getManagedResource(appObject);
				if (mres != null) {
					managedResource = mres.getName();
					LogEnablement.isLogDebugEnabled(appHealthCheckLogger,"Enter IntegrationConfig " + managedResource);
					break;
				}
			}
			/**
			 * Decaching
			 */
			for (IntegrationConfig integrationConfigdec : listOfIntegrationConfigObs) {
				_context.decache(integrationConfigdec);
			}
			QueryOptions qoA = new QueryOptions();
			qo.addFilter(Filter.notnull("id"));
			java.util.Iterator<Object[]> itApp = _context.search(
					Application.class, qoA, "id");
			List<String> appIds = new ArrayList();
			if (itApp != null) {
				while (itApp.hasNext()) {
					Object[] objArr = (Object[]) itApp.next();
					if (objArr != null && objArr.length == 1
							&& objArr[0] != null) {
						String appId = objArr[0].toString();
						appIds.add(appId);
					}
				}
			}
			Util.flushIterator(itApp);
			List<Application> listOfAppObjs = new ArrayList();
			for (String appId : appIds) {
				Application appObj = _context.getObjectById(Application.class,
						appId);
				listOfAppObjs.add(appObj);
			}
			for (Application appObj : listOfAppObjs) {
				ProvisioningConfig pc = appObj.getProvisioningConfig();
				if (pc != null) {
					ManagedResource mres = pc.getManagedResource(appObject);
					if (mres != null) {
						managedResource = mres.getName();
						LogEnablement.isLogDebugEnabled(appHealthCheckLogger,"Enter ProvisioningConfig " + managedResource);
						break;
					}
				}
			}
			/**
			 * Decaching
			 */
			for (Application appObjT : listOfAppObjs) {
				_context.decache(appObjT);
			}
		} catch (GeneralException e) {
			LogEnablement.isLogErrorEnabled(appHealthCheckLogger,"Error Finding Application Object " + e.getMessage());
		} catch (Exception e) {
			LogEnablement.isLogErrorEnabled(appHealthCheckLogger,"Error " + e.getMessage());
		}
		LogEnablement.isLogDebugEnabled(appHealthCheckLogger,"Enter managedResourceNameFromIntegrationProvisioningConfig "
				+ managedResource);
		if (appObject != null)
			_context.decache(appObject);
		return managedResource;
	}
	private Map<String, Object> convertAppToMapConnection(String app) {
		Application appObject = null;
		SailPointContext _threadContext = null;
		HashMap<String, Object> appAttributes = new HashMap<String, Object>();
		boolean isThreadInterrupted = false;
		try {
			_threadContext = init();
			appObject = _threadContext.getObjectByName(Application.class, app);
			appHealthCheckLogger.trace("Enter convertAppToMap ->" + app);
			LogEnablement.isLogDebugEnabled(appHealthCheckLogger,"Scanning appObject " + appObject.getName());
			appAttributes.put("name", appObject.getName());
			appAttributes.put("connection", "Red");
			appAttributes.put("groups", "Red");
			appAttributes.put("accounts", "Red");
			Connector appConnector = null;
			try {
				appConnector = sailpoint.connector.ConnectorFactory
						.getConnector(appObject, null);
				LogEnablement.isLogDebugEnabled(appHealthCheckLogger,"appConnector " + appConnector);
			} catch (GeneralException e) {
				LogEnablement.isLogDebugEnabled(appHealthCheckLogger,"Application connector settings are not right "
						+ e.getMessage());
			} catch (Exception e) {
				LogEnablement.isLogDebugEnabled(appHealthCheckLogger,"Application connector settings are not right "
						+ e.getMessage());
			} catch (NoClassDefFoundError e) {
				LogEnablement.isLogDebugEnabled(appHealthCheckLogger,"No Class Def Error " + e.getMessage());
			}
			if (Thread.interrupted()) {
				LogEnablement.isLogDebugEnabled(appHealthCheckLogger,"Thread is interrupted "
						+ Thread.currentThread().getName());
				isThreadInterrupted = true;
			}
			if (appConnector != null && !isThreadInterrupted) {
				try {
					LogEnablement.isLogDebugEnabled(appHealthCheckLogger,"appObject.getConnector() "
							+ appObject.getConnector());
					if (appObject.getConnector() != null
							&& appObject
									.getConnector()
									.toString()
									.equalsIgnoreCase(
											"sailpoint.connector.DefaultLogicalConnector")) {
						LogEnablement.isLogDebugEnabled(appHealthCheckLogger,"Scanning appObject " + appObject.getName()
								+ "->" + "Connection Yellow");
						appAttributes.put("connection", "Yellow");
					} else {
						appConnector.testConfiguration();
						LogEnablement.isLogDebugEnabled(appHealthCheckLogger,"Scanning appObject " + appObject.getName()
								+ "->" + "Connection Green");
						appAttributes.put("connection", "Green");
					}
				} catch (UnsupportedOperationException ex) {
					LogEnablement.isLogDebugEnabled(appHealthCheckLogger,"Scanning appObject " + appObject.getName()
							+ "->" + "Connection Yellow");
					appAttributes.put("connection", "Yellow");
					LogEnablement.isLogDebugEnabled(appHealthCheckLogger,"Application settings are not right "
							+ ex.getMessage());
				}
				catch (Exception ex) {
					LogEnablement.isLogDebugEnabled(appHealthCheckLogger,"Scanning appObject " + appObject.getName()
							+ "->" + "Connection Red");
					appAttributes.put("connection", "Red");
					LogEnablement.isLogDebugEnabled(appHealthCheckLogger,"Application settings are not right "
							+ ex.getMessage());
				} catch (NoClassDefFoundError e) {
					appAttributes.put("connection", "Red");
					LogEnablement.isLogDebugEnabled(appHealthCheckLogger,"No Class Def Error " + e.getMessage());
				}
				if (Thread.interrupted()) {
					appAttributes.put("connection", "Red");
					LogEnablement.isLogDebugEnabled(appHealthCheckLogger,"Thread is interrupted "
							+ Thread.currentThread().getName());
					isThreadInterrupted = true;
				}
			} else {
				appAttributes.put("connection", "Red");
			}
			appConnector = sailpoint.connector.ConnectorFactory.getConnector(
					appObject, null);
			if (appConnector != null && !isThreadInterrupted) {
				if (appObject.getAccountSchema() != null) {
					CloseableIterator<ResourceObject> iteratorAccounts = null;
					try {
						if (!appObject.supportsFeature(Feature.PROVISIONING)) {
							appAttributes.put("accounts", "Yellow");
						} else {
							iteratorAccounts = appConnector.iterateObjects(
									Application.SCHEMA_ACCOUNT, null, null);
							LogEnablement.isLogDebugEnabled(appHealthCheckLogger,"iteratorAccounts " + appObject.getName()
									+ iteratorAccounts);
							if (iteratorAccounts != null) {
								LogEnablement.isLogDebugEnabled(appHealthCheckLogger,"Closing Accounts for "
										+ appObject.getName());
								LogEnablement.isLogDebugEnabled(appHealthCheckLogger,"Scanning appObject "
										+ appObject.getName() + "->"
										+ "Accounts Green");
								appAttributes.put("accounts", "Green");
								iteratorAccounts.close();
							}
						}
					} catch (UnsupportedOperationException e) {
						LogEnablement.isLogErrorEnabled(appHealthCheckLogger,"Operation Not Supported " + e.getMessage());
						LogEnablement.isLogDebugEnabled(appHealthCheckLogger,"Scanning appObject " + appObject.getName()
								+ "->" + "Accounts Yellow");
						appAttributes.put("accounts", "Yellow");
					} catch (ConnectorException e) {
						LogEnablement.isLogErrorEnabled(appHealthCheckLogger,"Application account settings are not right Connector Exception"
								+ e.getMessage());
						LogEnablement.isLogDebugEnabled(appHealthCheckLogger,"Scanning appObject " + appObject.getName()
								+ "->" + "Accounts Red");
						appAttributes.put("accounts", "Red");
					} catch (RuntimeException e) {
						LogEnablement.isLogErrorEnabled(appHealthCheckLogger,"Application account settings are not right RunTime Exception"
								+ e.getMessage());
						LogEnablement.isLogDebugEnabled(appHealthCheckLogger,"Scanning appObject " + appObject.getName()
								+ "->" + "Accounts Yellow");
						appAttributes.put("accounts", "Yellow");
					} catch (Exception e) {
						LogEnablement.isLogErrorEnabled(appHealthCheckLogger,"Application account settings are not right  Exception"
								+ e.getMessage());
						LogEnablement.isLogDebugEnabled(appHealthCheckLogger,"Scanning appObject " + appObject.getName()
								+ "->" + "Accounts Yellow");
						appAttributes.put("accounts", "Red");
					} catch (NoClassDefFoundError e) {
						appAttributes.put("accounts", "Red");
						LogEnablement.isLogDebugEnabled(appHealthCheckLogger,"No Class Def Error " + e.getMessage());
					} finally {
						if (iteratorAccounts != null) {
							LogEnablement.isLogDebugEnabled(appHealthCheckLogger,"Closing Accounts " + appObject.getName());
							iteratorAccounts.close();
						}
					}
					if (Thread.interrupted()) {
						appAttributes.put("accounts", "Red");
						LogEnablement.isLogDebugEnabled(appHealthCheckLogger,"Thread is interrupted "
								+ Thread.currentThread().getName());
						isThreadInterrupted = true;
					}
				} else {
					/*
					 * No Schema
					 */
					appAttributes.put("accounts", "Yellow");
				}
			} else {
				appAttributes.put("accounts", "Red");
			}
			appConnector = null;
			appConnector = sailpoint.connector.ConnectorFactory.getConnector(
					appObject, null);
			LogEnablement.isLogDebugEnabled(appHealthCheckLogger,"appConnector object before scanning for groups is "
					+ appConnector);
			// End updates by M169712
			if (appConnector != null && !isThreadInterrupted) {
				if (appObject.getSchema(Application.SCHEMA_GROUP) != null) {
					CloseableIterator<ResourceObject> iteratorGroups = null;
					try {
						iteratorGroups = appConnector.iterateObjects(
								Application.SCHEMA_GROUP, null, null);
						if (iteratorGroups != null) {
							LogEnablement.isLogDebugEnabled(appHealthCheckLogger,"Closing Groups " + appObject.getName());
							LogEnablement.isLogDebugEnabled(appHealthCheckLogger,"Scanning appObject "
									+ appObject.getName() + "->"
									+ "Groups Green");
							appAttributes.put("groups", "Green");
							iteratorGroups.close();
						}
					} catch (UnsupportedOperationException e) {
						LogEnablement.isLogErrorEnabled(appHealthCheckLogger,"Operation Not Supported " + e.getMessage());
						LogEnablement.isLogDebugEnabled(appHealthCheckLogger,"Scanning appObject " + appObject.getName()
								+ "->" + "Groups Yellow");
						appAttributes.put("groups", "Yellow");
					} catch (ConnectorException e) {
						LogEnablement.isLogErrorEnabled(appHealthCheckLogger,"Application group settings are not right Connector Exception"
								+ e.getMessage());
						LogEnablement.isLogDebugEnabled(appHealthCheckLogger,"Scanning appObject " + appObject.getName()
								+ "->" + "Groups Red");
						appAttributes.put("groups", "Red");
					} catch (RuntimeException e) {
						LogEnablement.isLogErrorEnabled(appHealthCheckLogger,"Application group settings are not right RunTime Exception"
								+ e.getMessage());
						LogEnablement.isLogDebugEnabled(appHealthCheckLogger,"Scanning appObject " + appObject.getName()
								+ "->" + "Groups Yellow");
						appAttributes.put("groups", "Yellow");
					} catch (Exception e) {
						LogEnablement.isLogErrorEnabled(appHealthCheckLogger,"Application group settings are not right  Exception"
								+ e.getMessage());
						LogEnablement.isLogDebugEnabled(appHealthCheckLogger,"Scanning appObject " + appObject.getName()
								+ "->" + "Groups Red");
						appAttributes.put("groups", "Red");
					} catch (NoClassDefFoundError e) {
						appAttributes.put("groups", "Red");
						LogEnablement.isLogDebugEnabled(appHealthCheckLogger,"No Class Def Error " + e.getMessage());
					} finally {
						if (iteratorGroups != null) {
							LogEnablement.isLogDebugEnabled(appHealthCheckLogger,"Closing Groups " + appObject.getName());
							iteratorGroups.close();
						}
					}
					if (Thread.interrupted()) {
						appAttributes.put("groups", "Red");
						LogEnablement.isLogDebugEnabled(appHealthCheckLogger,"Thread is interrupted "
								+ Thread.currentThread().getName());
						isThreadInterrupted = true;
					}
				} else {
					/*
					 * No Schema
					 */
					appAttributes.put("groups", "Yellow");
				}
			} else {
				appAttributes.put("groups", "Red");
			}
		} catch (GeneralException e) {
			LogEnablement.isLogErrorEnabled(appHealthCheckLogger,"Error Finding Application Object " + _applicationName);
		} catch (Exception e) {
			LogEnablement.isLogErrorEnabled(appHealthCheckLogger," Error Creating Context " + e.getMessage());
		} catch (NoClassDefFoundError e) {
			LogEnablement.isLogDebugEnabled(appHealthCheckLogger,"No Class Def Error " + e.getMessage());
		} finally {
			try {
				if (appObject != null) {
					_threadContext.decache(appObject);
				}
				LogEnablement.isLogDebugEnabled(appHealthCheckLogger,"Releasing Context ");
				SailPointFactory.releaseContext(_threadContext);
			} catch (GeneralException e) {
				LogEnablement.isLogErrorEnabled(appHealthCheckLogger," Error releasing Context " + e.getMessage());
			} catch (Exception e) {
				LogEnablement.isLogErrorEnabled(appHealthCheckLogger," Error releasing Context " + e.getMessage());
			}
		}
		appHealthCheckLogger.trace("End convertAppToMap " + appAttributes);
		return appAttributes;
	}
	private Map<String, Object> convertAppToMapRedFlag(String app) {
		LogEnablement.isLogDebugEnabled(appHealthCheckLogger,"Enter convertAppToMapRedFlag");
		Application appObject = null;
		HashMap<String, Object> appAttributes = new HashMap<String, Object>();
		String redFlag = "Red";
		try {
			appObject = _context.getObjectByName(Application.class, app);
			appAttributes.put("connection", redFlag);
			appAttributes.put("accounts", redFlag);
			appAttributes.put("groups", redFlag);
			appAttributes.put("name", appObject.getName());
			if (appObject != null) {
				_context.decache(appObject);
			}
		} catch (GeneralException e) {
			LogEnablement.isLogErrorEnabled(appHealthCheckLogger,"Error Finding Application Object " + e.getMessage());
		} catch (Exception e) {
			LogEnablement.isLogErrorEnabled(appHealthCheckLogger,"Error " + e.getMessage());
		}
		LogEnablement.isLogDebugEnabled(appHealthCheckLogger,"End convertAppToMapRedFlag " + appAttributes);
		return appAttributes;
	}
	/*
	 * Inner Class This class is developed to create callable objects for each
	 * application. As a result, these objects can be used by
	 * SSThreadPoolTasksProcessor to create tasks for each callabale object.
	 */
	class SSAppHealthCheckReportCallable implements
			Callable<Map<String, Object>> {
		private String app;
		private boolean invoked = false;
		private Future future;
		private String type;
		public SSAppHealthCheckReportCallable(String app) {
			LogEnablement.isLogDebugEnabled(appHealthCheckLogger," Start SSAppHealthCheckReportCallable Constructor for app "
					+ app);
			setApp(app);
			LogEnablement.isLogDebugEnabled(appHealthCheckLogger," End SSAppHealthCheckReportCallable Constructor for app "
					+ app);
		}
		public SSAppHealthCheckReportCallable() {
			LogEnablement.isLogDebugEnabled(appHealthCheckLogger," Start SSAppHealthCheckReportCallable Constructor");
			LogEnablement.isLogDebugEnabled(appHealthCheckLogger," End SSAppHealthCheckReportCallable Constructor");
		}
		public String getType() {
			return type;
		}
		public void setType(String type) {
			this.type = type;
		}
		public boolean isInvoked() {
			return this.invoked;
		}
		public void setInvoked(boolean invoked) {
			this.invoked = invoked;
		}
		public void setApp(String app) {
			this.app = app;
		}
		public String getApp() {
			return app;
		}
		@Override
		public Map<String, Object> call() {
			try {
				LogEnablement.isLogDebugEnabled(appHealthCheckLogger," Calling  convertAppToMapConnection for " + app);
				this.invoked = true;
				Map map = convertAppToMapConnection(app);
				if (map != null) {
					LogEnablement.isLogDebugEnabled(appHealthCheckLogger,"Return map for " + app + "###" + map);
					return map;
				} else {
					Map redMap = convertAppToMapRedFlag(app);
					LogEnablement.isLogDebugEnabled(appHealthCheckLogger,"Return red map for " + app + "###" + redMap);
					return redMap;
				}
			} catch (Exception exception) {
				LogEnablement.isLogErrorEnabled(appHealthCheckLogger," Processor Thread Exception "
						+ exception.getMessage());
			}
			return null;
		}
	}
}
