/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.task;

import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.ObjectUtil;
import sailpoint.api.SailPointContext;
import sailpoint.api.SailPointFactory;
import sailpoint.connector.CIBInterface;
import sailpoint.connector.Connector;
import sailpoint.connector.ConnectorFactory;
import sailpoint.connector.ConnectorProxy;
import sailpoint.object.Application;
import sailpoint.object.Attributes;
import sailpoint.object.Rule;
import sailpoint.object.TaskResult;
import sailpoint.object.TaskSchedule;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.SensitiveTraceReturn;
import sailpoint.tools.Untraced;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;

public class CIBSyncTask extends AbstractTaskExecutor {
	private boolean _terminate = false;
	private static Log log = LogFactory.getLog(CIBSyncTask.class);
	private static final String TASK_INPUT_CLOUD_APP = "cloudApp";
	private static final String TASK_INPUT_HOSTED_APP_NAMES = "hostedApps";
	private static final String TASK_INPUT_RULE_NAMES = "ruleNames";
	private static final String TASK_INPUT_SYNC_SYSTEM_CONFIG = "syncSystemConfiguration";
	private static final String TASK_OUTPUT_APP_SYNC_SUCCESSFUL = "appsSyncSuccessful";
	private static final String TASK_OUTPUT_APP_SYNC_FAILED = "appsSyncFailed";
	private static final String TASK_OUTPUT_RULE_SYNC_SUCCESSFUL = "rulesSyncSuccessful";
	private static final String TASK_OUTPUT_RULE_SYNC_FAILED = "rulesSyncFailed";
	private static final String TASK_OUTPUT_SYS_CONFIG_SYNC_SUCCESSFUL = "systemConfigSyncSuccessful";
	
	
	private Application getCloudApp( SailPointContext context, 
						Attributes<String, Object> args, 
						TaskResult result) 
						throws Exception {
		Object attr = null;
		Application cloudApp = null;
		attr = args.get((String)TASK_INPUT_CLOUD_APP);
		if(attr == null) {
			result.addMessage(new Message(Message.Type.Error,
					MessageKeys.ERR_MISSING_CLOUD_APP));
			return null;
		}
		else {
			cloudApp = ObjectUtil.getObject(context, Application.class, attr);
		}
		return cloudApp;
	}
	
	private List<Application> getHostedAppList( SailPointContext context, 
			Attributes<String, Object> args, 
			TaskResult result) 
			throws Exception {
			Object attr = null;
			List<Application> hostedAppList = null;
			attr = args.get((String)TASK_INPUT_HOSTED_APP_NAMES);
			if(attr == null) {
				result.addMessage(new Message(Message.Type.Error,
						MessageKeys.ERR_MISSING_CLOUD_HOST_APP));
				return null;
			}
			else {
				hostedAppList = ObjectUtil.getObjects(context, Application.class, attr);
			}
			return hostedAppList;
	}
	
	private List<Rule> getRulesList( SailPointContext context, 
			Attributes<String, Object> args, 
			TaskResult result) 
			throws Exception {
			Object attr = null;
			List<Rule> ruleList = null;
			attr = args.get((String)TASK_INPUT_RULE_NAMES);
			if(attr != null) {
				ruleList = ObjectUtil.getObjects(context, Rule.class, attr);
			}
			return ruleList;
	}
		
	private boolean isSyncSystemConfigRequired( Attributes<String, Object> args ) 
					throws Exception {
			boolean syncSystemConfig = false;
			syncSystemConfig = args.getBoolean(TASK_INPUT_SYNC_SYSTEM_CONFIG);
			return syncSystemConfig;
	}
	
	private void syncApps(Application cloudApp, List<Application> hostedAppsToSync, TaskResult result ) throws Exception {
		CIBInterface cloudConnector =  getConnector(cloudApp, result);
		if( cloudConnector == null)
			return;
		int appSyncSuccessful = 0;
		int appSyncFailed = 0;
		boolean syncResult = false;
		for( Application app : hostedAppsToSync ) {
			decryptSensitiveData(app);
			syncResult = cloudConnector.syncApplicationObject(app);
			if( syncResult == true )
				appSyncSuccessful++;
			else 
				appSyncFailed++;
			
			checkIfTaskIsTerminated();
		}
		
		result.setAttribute(TASK_OUTPUT_APP_SYNC_SUCCESSFUL, appSyncSuccessful);
		result.setAttribute(TASK_OUTPUT_APP_SYNC_FAILED, appSyncFailed);
		if(appSyncFailed > 0 ) {
			result.addMessage(new Message(Message.Type.Error, MessageKeys.TASK_OUT_CLOUD_BRIDGE_APPS_SYNC_FAILED));
		}
		
	}
	
	//////////////////////////////////////////////////////////////////////
    //
    // HELPER FUNCTIONS
    //
    //////////////////////////////////////////////////////////////////////
	 	
	@Untraced
	@SensitiveTraceReturn
	private String decryptField(String value) {
		String decryptedText = null;
		SailPointContext sc = null;
		try {
			sc = SailPointFactory.getCurrentContext();
			decryptedText = sc.decrypt(value);
		}
		catch(GeneralException e) {
			if(log.isErrorEnabled() ) log.error("Unable to encrypted sensitive data as there is no context associated with this thread or encryption failed.", e);
		}
		return decryptedText;
	}
	
	 private void decryptSensitiveData( Application object) {
	    	
	    	String plainText = null;
			String decryptedText = null;

	    	String password = (String)object.getAttributeValue("password");
	    	if( password != null) {
	    		decryptedText = decryptField(password);
	    		object.setAttribute("password", decryptedText);
	    	}
	    	
	    	String sensitiveAttrCSV = (String) object.getAttributeValue("encrypted");
	    	if(sensitiveAttrCSV == null) 
	    		return;
	    	
	    	List<String> sensitiveAttrList = Util.csvToList(sensitiveAttrCSV);
	    	for( String attr : sensitiveAttrList ) {
				plainText = (String)object.getAttributeValue( attr );
				decryptedText = decryptField(plainText);
				object.setAttribute(attr,decryptedText);
			}
	}
	 
	
	 
	private void syncRules(Application cloudApp, List<Rule> RulesToSync, TaskResult result ) throws Exception {
	    CIBInterface cloudConnector =  getConnector(cloudApp, result);
		if( cloudConnector == null)
			return;
		
		int ruleSyncSuccessful = 0;
		int ruleSyncFailed = 0;
		boolean syncResult = false;
		for( Rule rule : RulesToSync ) {
			syncResult = cloudConnector.syncRuleObject(rule);
			if( syncResult == true )
				ruleSyncSuccessful++;
			else 
				ruleSyncFailed++;
		}
		result.setAttribute(TASK_OUTPUT_RULE_SYNC_SUCCESSFUL, ruleSyncSuccessful);
		result.setAttribute(TASK_OUTPUT_RULE_SYNC_FAILED, ruleSyncFailed);
		if(ruleSyncFailed > 0 )
			result.addMessage(new Message(Message.Type.Error, MessageKeys.TASK_OUT_CLOUD_BRIDGE_RULES_SYNC_FAILED));
	}
	
	private void syncSystemConfig(Application cloudApp, TaskResult result ) throws Exception {
	    CIBInterface cloudConnector =  getConnector(cloudApp, result);
		if( cloudConnector == null)
			return;
		
		boolean syncResult = false; 
		syncResult = cloudConnector.syncSystemConfiguration();
		if( syncResult == true )
			result.setAttribute(TASK_OUTPUT_SYS_CONFIG_SYNC_SUCCESSFUL, 1);
		else { 
			result.setAttribute(TASK_OUTPUT_SYS_CONFIG_SYNC_SUCCESSFUL, 0);
			result.addMessage(new Message(Message.Type.Error, MessageKeys.TASK_OUT_CLOUD_BRIDGE_SYSTEM_CONFIG__SYNC_FAILED));
		}
	}
	
    private CIBInterface getConnector(Application cloudApp, TaskResult result) throws Exception {
        
        Connector connector = ConnectorFactory.getConnector(cloudApp, null);
        if (connector != null && (((ConnectorProxy) connector).getInternalConnector()) instanceof CIBInterface) {
            return (CIBInterface) ((ConnectorProxy) connector).getInternalConnector();
        } else {
            result.addMessage(new Message(Message.Type.Error, MessageKeys.ERR_CANNOT_INSTANTIATE_CLOUD_CONNECTOR));
            return null;
        }
    }
	
	// Throws an exception if the task was terminated terminate while it was running. 
    private void checkIfTaskIsTerminated() throws GeneralException {
    	if (_terminate == true) {
			throw new GeneralException(
					"Cloud Identity Bridge Sync task was terminated");
		}
    }
    
    
	public void execute(SailPointContext context, TaskSchedule schedule,
			TaskResult result, Attributes<String, Object> args)
			throws Exception {
		
		Application cloudApp = getCloudApp(context, args, result);
		if(cloudApp == null)  return;

		checkIfTaskIsTerminated();
		// Sync the System Configuration Object every time.
		syncSystemConfig(cloudApp, result) ;
		
		
		checkIfTaskIsTerminated();
		
		//Rules will get sync before applications
		List<Rule> rulesToSync = getRulesList(context, args, result);
		if(rulesToSync != null && rulesToSync.size() > 0) 
		    syncRules( cloudApp, rulesToSync, result );

		List<Application> hostedAppsToSync = getHostedAppList(context, args, result);
		if(hostedAppsToSync == null) 
			return;
		else 
			syncApps(cloudApp, hostedAppsToSync, result);
	}

	public boolean terminate() {
		// TODO Auto-generated method stub
		_terminate = true;
		return _terminate;
	}

}
