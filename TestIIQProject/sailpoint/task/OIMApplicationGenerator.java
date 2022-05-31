package sailpoint.task;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import sailpoint.api.ObjectUtil;
import sailpoint.api.SailPointContext;
import sailpoint.connector.DefaultApplicationFactory;
import sailpoint.integration.oim.OIMConnector;
import sailpoint.object.Application;
import sailpoint.object.Attributes;
import sailpoint.object.Schema;
import sailpoint.object.TaskResult;
import sailpoint.object.TaskSchedule;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;



/**
 * This is a task to pull IT Resources from OIM 
 * and create a multiplexed application in IIQ.
 * 
 * @author ketan.avalaskar
 *
 */

public class OIMApplicationGenerator extends AbstractTaskExecutor {
	
	public static final String ARG_OIM_APP_IDS = "oimAppIds";
	private boolean _terminate;
	public static final String RESULT_APPS_CREATED = "appsCreated";
	public static final String RESULT_APPS_IGNORED = "appsIgnored";

	public void execute(SailPointContext context, TaskSchedule schedule,
			TaskResult result, Attributes<String, Object> args)
			throws Exception { 
		
		List<Application> _oimApps = null;
		List<Application> _resourceApps = new ArrayList<Application>();
		List<String> resources = null;
		Map map = new HashMap();
		
		Object _apps = args.get(ARG_OIM_APP_IDS);
		if (_apps == null) {
			/*result.addMessage(new Message(Message.Type.Error,
					MessageKeys.ERR_MISSING_BMC_APP_IDS));*/
			System.out.println("Debug: Error Missing OIM Application ID");
			return;
		} else 
			_oimApps = ObjectUtil.getObjects(context, Application.class, _apps);
		
		if (Util.size(_oimApps) == 0) {
			/*result.addMessage(new Message(Message.Type.Error,
					MessageKeys.ERR_MISSING_BMC_APP_IDS));*/
			System.out.println("Debug: Error Missing OIM Application ID");
			return;
		}
		
		int ignored = 0;
		int created = 0;
		
		for (Application _oimApp : _oimApps) {
			if (_terminate) {
			    result.setTerminated(true);
			    throw new GeneralException(
						"OIM Application Creator task was terminated");
			}
			if (null == _oimApp) {
				/*result.addMessage(new Message(Message.Type.Error,
						MessageKeys.ERR_BMC_APP_NOT_FOUND));*/
				System.out.println("Debug: Error OIM Application not found");
				return;
			}
			if (_oimApp.getProxy() != null)
				throw new GeneralException(
				"Cannot discover resource applications for multiplexed applications");
			
			
			//Check if its a main OIMApp and schema of the application is Null then set a new schema
			if (_oimApp.getProxy() == null && _oimApp.getSchemas().isEmpty()){
				String  appType = _oimApp.getType();
				// Get details from the connectorRegistry depending on the type of application
				Application defaultApp = DefaultApplicationFactory.getDefaultApplicationByTemplate(appType);
				if ( defaultApp != null )
					_oimApp.setSchemas(defaultApp.getSchemas());
			}
					
			OIMConnector connector = new OIMConnector(_oimApp);
			resources = connector.getResources();
			if (Util.isEmpty(resources))
				throw new GeneralException(
						"Cannot load Resource Applications from OIM Server");
			
			for (String resource: resources) {
				if (_terminate)
					throw new GeneralException(
							"OIM Application Creator task was terminated");
				
				Application multiplexed = connector.getProxiedApplication(resource, map);
							
				if (null != multiplexed) {
					multiplexed.setProxy(_oimApp);
					_resourceApps.add(multiplexed);
					
						Application existing = context.getObjectByName(Application.class, multiplexed.getName());
						if (null != existing){
							/*This existing application will not be null when there is up-gradation from old to new application
							   Example 55P3 upgrade to 6.0*/
							List<Schema> schemas = multiplexed.getSchemas();
							if (existing.getSchemas().isEmpty())
								existing.setSchemas(schemas);  		
							context.saveObject(existing);
							ignored++;
						}	
						else {
							if (_oimApp.getFeatures() != null)
								multiplexed.setFeatures(_oimApp.getFeatures());
							created++;
							context.saveObject(multiplexed);
						}
				}
				context.commitTransaction();
			}
		}
		result.setAttribute(RESULT_APPS_CREATED, created);
		result.setAttribute(RESULT_APPS_IGNORED, ignored);
		context.decache();
	}

	public boolean terminate() {
		_terminate = true;
		return _terminate;
	}

}
