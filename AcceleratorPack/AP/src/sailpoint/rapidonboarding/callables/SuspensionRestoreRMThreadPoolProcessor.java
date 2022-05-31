/*
Copyright © 2018 SailPoint Technologies, Inc. All Rights Reserved.
All logos, text, content, and works of authorship, including but not limited to underlying code, programming or scripting language, designs, and/or graphics,
that are used and/or depicted herein are protected under United States and international copyright and trademark laws and treaties,
and may not be used or reproduced without the prior express written permission of SailPoint Technologies, Inc.
*/
package sailpoint.rapidonboarding.callables;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import sailpoint.api.RequestManager;
import sailpoint.api.SailPointContext;
import sailpoint.api.SailPointFactory;
import sailpoint.object.Application;
import sailpoint.object.Attributes;
import sailpoint.object.Filter;
import sailpoint.object.GroupDefinition;
import sailpoint.object.Identity;
import sailpoint.object.Link;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.QueryOptions;
import sailpoint.object.ProvisioningPlan.AccountRequest;
import sailpoint.object.Request;
import sailpoint.object.RequestDefinition;
import sailpoint.object.Rule;
import sailpoint.object.Workflow;
import sailpoint.object.Application.Feature;
import sailpoint.rapidapponboarding.logger.LogEnablement;
import sailpoint.rapidapponboarding.rule.ROADUtil;
import sailpoint.rapidapponboarding.rule.ServiceDefinitionUtil;
import sailpoint.rapidapponboarding.rule.TaskUtil;
import sailpoint.rapidapponboarding.rule.WrapperRuleLibrary;
import sailpoint.request.RuleRequestExecutor;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
/**
 * Call Request Manager to Launch Workflows using Multiple Threads
 * @author Rohit Gupta
 *
 */
public class SuspensionRestoreRMThreadPoolProcessor {
	private static final Log threadPoolLogger = LogFactory.getLog("rapidapponboarding.rules");
	private static final Object REQUESTMANAGERWAIT = "OperationsSuspensionWait";
	private static final Object REQUESTMANAGERQUEUETHREADS = "OperationsSuspensionThreads";
	private  String _launchOption;
	private  boolean _allApps=false;
	private  boolean _switchUITask=false;
	private  boolean _terminateTasks=false;
	private  SailPointContext _ctx;
	private  String _populationName;
	private  String _workflowRuleName=null;
	private  String _postPone=null;
	private  String _includePopulatioName=null;
	private  String _suspensionRestoreThreads=null;
	private  String _suspensionRestoreWait=null;
	private List<String> _appNames = new ArrayList();
	private int _amountOfSeconds=60;
	private int _rmQueueThreads=1;
	private static final String RESTORE = "Restore"; 
	private static final String SUSPEND = "Suspend"; 
	private static final String WORKFLOW = "Workflow"; 
	private static final String WORKFLOWREQUEST = "Workflow Request"; 
	private static final String RULEREQUEST = "Rule Request"; 
	private static final String WORKFLOWPROVISIONER = "Workflow-Provisioner"; 
	private static final String WORKFLOWPROVISIONERWRAPPER = ROADUtil.DEFAULTWORKFLOW;
	private static final String RULEPROVISIONER = "Rule-Provisioner"; 
	private static final String WORKFLOWPROVISIONEROPTION = "Workflow Provisioner"; 
	private static final String RULEPROVISIONEROPTION = "Rule Provisioner"; 
	private  String _enablePlan=SUSPEND;
	public SuspensionRestoreRMThreadPoolProcessor(SailPointContext ctx,List appNames,String populationName,
			String launchOption,String enablePlan,boolean allApps, boolean switchUITask, String postPone,boolean terminateTasks, String includePopulatioName,
			String suspensionRestoreThreads,String suspensionRestoreWait)
	{
		LogEnablement.isLogDebugEnabled(threadPoolLogger,"...RequestManagerFixedThreadPoolProcessor appNames.."+appNames);
		LogEnablement.isLogDebugEnabled(threadPoolLogger,"...RequestManagerFixedThreadPoolProcessor..populationName.."+populationName);
		LogEnablement.isLogDebugEnabled(threadPoolLogger,"...RequestManagerFixedThreadPoolProcessor..launchOption.."+launchOption);
		LogEnablement.isLogDebugEnabled(threadPoolLogger,"...RequestManagerFixedThreadPoolProcessor..enablePlan.."+enablePlan);
		LogEnablement.isLogDebugEnabled(threadPoolLogger,"...RequestManagerFixedThreadPoolProcessor..allApps.."+allApps);
		LogEnablement.isLogDebugEnabled(threadPoolLogger,"...RequestManagerFixedThreadPoolProcessor..switchUITask.."+switchUITask);
		LogEnablement.isLogDebugEnabled(threadPoolLogger,"...RequestManagerFixedThreadPoolProcessor..postPone.."+postPone);
		LogEnablement.isLogDebugEnabled(threadPoolLogger,"...RequestManagerFixedThreadPoolProcessor..terminateTasks.."+terminateTasks);
		LogEnablement.isLogDebugEnabled(threadPoolLogger,"...RequestManagerFixedThreadPoolProcessor..includePopulatioName.."+includePopulatioName);
		LogEnablement.isLogDebugEnabled(threadPoolLogger,"...RequestManagerFixedThreadPoolProcessor..suspensionRestoreThreads.."+suspensionRestoreThreads);
		LogEnablement.isLogDebugEnabled(threadPoolLogger,"...RequestManagerFixedThreadPoolProcessor..suspensionRestoreWait.."+suspensionRestoreWait);
		this._ctx=ctx;
		//Selected Apps
		if(appNames!=null && appNames.size()>0)
		{
			this._appNames.addAll(appNames);
		}
		this._populationName=populationName;
		this._workflowRuleName= SuspensionRestoreRMThreadPoolProcessor.WORKFLOWPROVISIONERWRAPPER;
		this._launchOption=launchOption;
		this._enablePlan=enablePlan;
		this._allApps=allApps;
		this._switchUITask=switchUITask;
		this._postPone=postPone;
		this._terminateTasks=terminateTasks;
		this._includePopulatioName=includePopulatioName;
		this._suspensionRestoreThreads=suspensionRestoreThreads;
		this._suspensionRestoreWait=suspensionRestoreWait;
	}
	/**
	 * Start Processing
	 * @throws GeneralException
	 * @throws ParseException
	 */
    public void startProcessing() throws GeneralException, ParseException
    {
    	if(this._launchOption!=null && this._launchOption.contains(SuspensionRestoreRMThreadPoolProcessor.WORKFLOWPROVISIONEROPTION))
		{
    		this._workflowRuleName=SuspensionRestoreRMThreadPoolProcessor.WORKFLOWPROVISIONER;
    		LogEnablement.isLogDebugEnabled(threadPoolLogger,"...RequestManagerFixedThreadPoolProcessor..this._workflowRuleName.."+this._workflowRuleName);
			launchEmergencyAction();
		}
		else if(this._launchOption!=null && this._launchOption.contains(SuspensionRestoreRMThreadPoolProcessor.RULEPROVISIONEROPTION))
		{
			this._workflowRuleName=SuspensionRestoreRMThreadPoolProcessor.RULEPROVISIONER;
			LogEnablement.isLogDebugEnabled(threadPoolLogger,"...RequestManagerFixedThreadPoolProcessor..this._workflowRuleName.."+this._workflowRuleName);
			launchEmergencyAction();
		}
		else
		{
			LogEnablement.isLogDebugEnabled(threadPoolLogger,"...RequestManagerFixedThreadPoolProcessor..this._workflowRuleName.."+this._workflowRuleName);
			launchEmergencyAction();
		}
    }
	/**
	 * Launch this via Rule in BackGround after Form Submission
	 * @param context
	 * @param appNames
	 * @param populationName
	 * @param diasble
	 * @param launchOption
	 * @throws GeneralException
	 * @throws ParseException 
	 */
	private void launchEmergencyAction() throws GeneralException, ParseException
	{
			LogEnablement.isLogDebugEnabled(threadPoolLogger,"...launchEmergencyAction.");
			LogEnablement.isLogDebugEnabled(threadPoolLogger,"...launchEmergencyAction..appNames.."+this._appNames);
			LogEnablement.isLogDebugEnabled(threadPoolLogger,"...launchEmergencyAction..Exclude populationName.."+this._populationName);
			LogEnablement.isLogDebugEnabled(threadPoolLogger,"...launchEmergencyAction..disable.."+this._enablePlan);
			LogEnablement.isLogDebugEnabled(threadPoolLogger,"...launchEmergencyAction..workflowName.."+this._workflowRuleName);
			LogEnablement.isLogDebugEnabled(threadPoolLogger,"...launchEmergencyAction..launchOption.."+this._launchOption);
			LogEnablement.isLogDebugEnabled(threadPoolLogger,"...launchEmergencyAction..postPone.."+this._postPone);
			LogEnablement.isLogDebugEnabled(threadPoolLogger,"...launchEmergencyAction..switchUITask.."+this._switchUITask);
			LogEnablement.isLogDebugEnabled(threadPoolLogger,"...launchEmergencyAction..Include includePopulatioName.."+this._includePopulatioName);
			//Switch Servers
			if(this._switchUITask)
			{
				ServiceDefinitionUtil.switchUIToTask(this._ctx);
				ServiceDefinitionUtil.stopStartServices(true, true);
			}
			//PostPhone Task Schedules
			if(this._postPone!=null)
			{
				//Default to 30 Days
				int days=30;
				try
				{
					days = Integer.parseInt(this._postPone);
				}
				catch (NumberFormatException numEx) 
				{
					LogEnablement.isLogDebugEnabled(threadPoolLogger,"...Parsing String To Integer.."+numEx.getMessage());
			   	}
				catch (Exception ex)
				{
					LogEnablement.isLogDebugEnabled(threadPoolLogger,"...Parsing String To Integer.."+ex.getMessage());
				}
				Date currentDate = new Date();
				LogEnablement.isLogDebugEnabled(threadPoolLogger,"...currentDate.."+currentDate);
				Calendar cal = Calendar.getInstance();
				cal.setTime(currentDate);
				cal.add(Calendar.DATE, days);
				Date newDate = cal.getTime();
				LogEnablement.isLogDebugEnabled(threadPoolLogger,"...newDate.."+newDate);
				if(newDate!=null)
				{
					TaskUtil.postPhoneAllTaskSchedules(this._ctx, newDate);
				}
			}
			//Terminate Tasks
			if(this._terminateTasks)
			{
				TaskUtil.terminatePendingTasks(this._ctx);
			}
			//Get Threads
			try
			{
				if(this._suspensionRestoreThreads!=null)
				{
					this._rmQueueThreads = Integer.parseInt(this._suspensionRestoreThreads);
				}
			}
			catch (NumberFormatException numEx) 
			{
				LogEnablement.isLogDebugEnabled(threadPoolLogger,"...Parsing String To Integer Threads.."+numEx.getMessage());
		   	}
			//Get Threads
			try
			{
				if(this._suspensionRestoreWait!=null)
				{
					this._amountOfSeconds = Integer.parseInt(this._suspensionRestoreWait);
				}
			}
			catch (NumberFormatException numEx) 
			{
				LogEnablement.isLogDebugEnabled(threadPoolLogger,"...Parsing String To Integer Wait Time.."+numEx.getMessage());
		   	}
			LogEnablement.isLogDebugEnabled(threadPoolLogger,"TOTAL TASKS THREADS " + this._rmQueueThreads);
			LogEnablement.isLogDebugEnabled(threadPoolLogger,"WAIT TIME " + this._amountOfSeconds);
			ExecutorService executor = null;
			ExecutorCompletionService completionService = null;
			long startTime = 0;
			try 
			{
				startTime = new Date().getTime();
				if(this._allApps)
				{
					this._appNames.addAll(getAllApps());
				}
				if( this._appNames!=null && this._appNames.size()>0)
				{
					List<String> listNames=buildIdNameList(this._ctx, this._includePopulatioName);
					if(listNames!=null && listNames.size()>0)
					{
						for(String identityName:listNames)
						{
							int matchPop=0;
							boolean exclude=false;
							HashMap nativeIdAppMap= new HashMap();
							if(this._populationName!=null)
							{
								matchPop=matchPopulationUsingName(this._ctx, identityName, this._populationName);
							}
							if(matchPop>0)
							{
								 exclude=true;
								 LogEnablement.isLogDebugEnabled(threadPoolLogger,"Exclude Admins.."+identityName);
							}
							else
							{
								 exclude=false;
								 LogEnablement.isLogDebugEnabled(threadPoolLogger,"Include Cubes.."+identityName);
							}
							if(!exclude)
							{
								//For Each Identity Get New Map Instance
								for(String appName:this._appNames)
								{
									//For Each Application On Identity - Fill the same map with key as appnames
									buildLinkMap( this._ctx,  appName, identityName,nativeIdAppMap);
								}
								if(nativeIdAppMap!=null && nativeIdAppMap.size()>0)
								{
									LogEnablement.isLogDebugEnabled(threadPoolLogger,"...launchEmergencyAction...."+identityName);
									LogEnablement.isLogDebugEnabled(threadPoolLogger,"...Launch Future Threads...."+nativeIdAppMap);
									/**
									 * Call Request Manager Directly
									 */
									if(this._rmQueueThreads==0 || this._rmQueueThreads==1)
									{
										LogEnablement.isLogDebugEnabled(threadPoolLogger,"... Call Request Manager Directly....");
										startRequestManagerCallables(nativeIdAppMap, identityName,this._ctx);
									}
									else
									{
										/**
										 * Call Request Manager InDirectly
										 * This uses Callable objects.
										 */
										LogEnablement.isLogDebugEnabled(threadPoolLogger,"...Future Callables Enable....");
										executor = Executors.newFixedThreadPool(this._rmQueueThreads);
										completionService = new ExecutorCompletionService(executor);
										Future future = (Future<Map<String, Object>>) completionService.submit(new RequestWorker(nativeIdAppMap,identityName));
									}
								}
							}
						}
					}
				}
			} 
			catch (Exception ex) 
			{
				LogEnablement.isLogErrorEnabled(threadPoolLogger,"Exception from RequestManagerFixedThreadPoolProcessor "+ ex.getMessage());
			} 
			finally 
			{
				LogEnablement.isLogDebugEnabled(threadPoolLogger,"TOTAL TIME - " + (new Date().getTime() - startTime)/ 1000 + " SECONDS");
				if (executor != null) 
				{
					executor.shutdown();
				}
			}
			LogEnablement.isLogDebugEnabled(threadPoolLogger,"...end launchEmergencyAction.");
	}
	/**
	 * Get All Apps
	 * @throws GeneralException 
	 */
	private List getAllApps() throws GeneralException
	{
		LogEnablement.isLogDebugEnabled(threadPoolLogger,"...End getAllApps.");
		String featureString = Feature.ENABLE.toString();
		QueryOptions qo = new QueryOptions(Filter.like("featuresString", featureString));
		Iterator iter = this._ctx.search(Application.class, qo, "name");
		List list = new ArrayList();
        String name=null;
		if (iter != null && iter.hasNext())
		{
	    	while (iter.hasNext()) 
	    	{
	    		 Object[] item = (Object[]) iter.next();
	    		 if(item!=null && item.length==1)
	    		 {
	    			 name = (String) item[0];	
	    			 list.add(name);
	    		 }
	      	}
	    }
		LogEnablement.isLogDebugEnabled(threadPoolLogger,"...End getAllApps.");
		LogEnablement.isLogDebugEnabled(threadPoolLogger,"...End getAllApps."+list);
	    Util.flushIterator(iter);
	    return list;
	}
	/**
	 * Build Enable or Disable Provisioning Plan
	 * nativeIdAppName - Key AppName and List of Native Id's
	 * @return
	 * @throws GeneralException 
	 */
	 private ProvisioningPlan buildPlan(HashMap<String,ArrayList> nativeIdAppName, String identityName) throws GeneralException
	 {
		 ProvisioningPlan provisioningPlan= new ProvisioningPlan();
		 LogEnablement.isLogDebugEnabled(threadPoolLogger,"buildPlan.."+nativeIdAppName);
		 LogEnablement.isLogDebugEnabled(threadPoolLogger,"buildPlan.."+identityName);
		 if(nativeIdAppName!=null && identityName!=null)
		 {
			 provisioningPlan.setNativeIdentity(identityName);
		     for (Map.Entry<String,ArrayList> entry : nativeIdAppName.entrySet())  
			 {
				 if(entry.getKey()!=null)
				 {
					 ArrayList<String> val=entry.getValue();
					 LogEnablement.isLogDebugEnabled(threadPoolLogger," buildPlan Application.."+val);
					 if(val!=null && val.size()>0)
					 {
						 for(String str:val)
						 { 
							 LogEnablement.isLogDebugEnabled(threadPoolLogger," buildPlan Native Id.."+str);
							 AccountRequest acctReq=null;
							 if(this._enablePlan!=null && this._enablePlan.equalsIgnoreCase(SuspensionRestoreRMThreadPoolProcessor.RESTORE) && str!=null)
							 { 
								 LogEnablement.isLogDebugEnabled(threadPoolLogger," buildPlan enablePlan.."+this._enablePlan);
								 acctReq = new AccountRequest(AccountRequest.Operation.Enable, entry.getKey(), null, str);
							 }
							 else if(str!=null)
							 {
								 LogEnablement.isLogDebugEnabled(threadPoolLogger," buildPlan enablePlan.."+this._enablePlan);
								 acctReq = new AccountRequest(AccountRequest.Operation.Disable, entry.getKey(), null, str);
							 }
							 provisioningPlan.add(acctReq);
						 }
					 }
				 }
			 }
		 }
		 LogEnablement.isLogDebugEnabled(threadPoolLogger,"End buildPlan..");
		 return provisioningPlan;
	 }
	/**
	 * Private Class of Callalbles
	 * @author rohit.gupta
	 *
	 */
	private class RequestWorker implements Callable 
	{
		HashMap<String,ArrayList> nativeIdHashMap;
		String identityName;
		SailPointContext context;
        RequestWorker( HashMap <String,ArrayList> nativeIdHashMap, String identityName) 
        {
            this.nativeIdHashMap = nativeIdHashMap;
            this.identityName = identityName;
        }
		@Override
		public Boolean call() throws Exception
		{
			try 
			{
				if(this.identityName!=null)
				{
					context = SailPointFactory.createContext(this.identityName);
					ProvisioningPlan plan=buildPlan(this.nativeIdHashMap, this.identityName);
					LogEnablement.isLogDebugEnabled(threadPoolLogger,"Create New Context..");
					LogEnablement.isLogDebugEnabled(threadPoolLogger,"_launchOption.."+_launchOption);
					if(_launchOption!=null  && plan!=null && plan.getAccountRequests()!=null)
					{
						LogEnablement.isLogDebugEnabled(threadPoolLogger,"Create New Context..");
						startRequestManagerCallables(this.nativeIdHashMap, this.identityName,context);
						LogEnablement.isLogDebugEnabled(threadPoolLogger,"launchProvisionerPlan..");
						return true;
					}
				}
			}
			finally 
			 {
                 try 
                 {
                     if (this.identityName != null ) 
                     {
                    	 SailPointFactory.releaseContext(context);
                     }
                 }
                 catch (Exception ex) 
                 {
                	 LogEnablement.isLogErrorEnabled(threadPoolLogger,"Unable to Release Context.."+ex.getMessage());
                 }
             }
			return false;
		}
	}
	/**Start Request Manager Callables To
	 * Suspend Application Access
	 * @param nativeIdHashMap
	 * @param identityName
	 * @param context
	 * @throws GeneralException
	 */
	public void startRequestManagerCallables(HashMap nativeIdHashMap, String identityName, SailPointContext context) throws GeneralException 
	{
		try
		{
			LogEnablement.isLogDebugEnabled(threadPoolLogger,"Start startRequestManagerCallables");
			LogEnablement.isLogDebugEnabled(threadPoolLogger,"Start startRequestManagerCallables ..identityName"+identityName);
		 	HashMap launchArgsMap = new HashMap();
			launchArgsMap.put("identityName", identityName);
		    //Prepare Disable Plan
			launchArgsMap.put("plan",buildPlan(nativeIdHashMap,identityName));
			if(this._launchOption!=null && this._launchOption.contains("No Request Id"))
			{
				LogEnablement.isLogDebugEnabled(threadPoolLogger,"Disable Request Id...");
				launchArgsMap.put("disableIdentityRequests", true);
			}
			if(_launchOption!=null && _launchOption.contains(SuspensionRestoreRMThreadPoolProcessor.WORKFLOW) )
			{
				LogEnablement.isLogDebugEnabled(threadPoolLogger,"Add Workflow Arguments...");
				launchArgsMap.put("requestType","SUSPEND FEATURE");
				launchArgsMap.put("source","Task");
				launchArgsMap.put("policyScheme", "None");
				launchArgsMap.put("foregroundProvisioning","true");
				launchArgsMap.put("autoVerifyIdentityRequest","true");
			}
			// Use the Request Launcher
			Request req = new Request();
			RequestDefinition reqdef = null;
			Attributes allArgs = new Attributes();
			if(_launchOption!=null && _launchOption.contains(SuspensionRestoreRMThreadPoolProcessor.WORKFLOW) )
			{
				LogEnablement.isLogDebugEnabled(threadPoolLogger,"context.."+context);
				LogEnablement.isLogDebugEnabled(threadPoolLogger,"context..name.."+context.getUserName());
				LogEnablement.isLogDebugEnabled(threadPoolLogger,"this._workflowRuleName.."+this._workflowRuleName);
				Workflow wf = (Workflow) context.getObjectByName(Workflow.class,this._workflowRuleName);
				LogEnablement.isLogDebugEnabled(threadPoolLogger,"Workflow Request.."+wf.getName());
				reqdef=context.getObjectByName(RequestDefinition.class, SuspensionRestoreRMThreadPoolProcessor.WORKFLOWREQUEST);
				allArgs.put("workflow", wf.getName());
			}
			else
			{
				LogEnablement.isLogDebugEnabled(threadPoolLogger,"context.."+context);
				LogEnablement.isLogDebugEnabled(threadPoolLogger,"context.."+context.getUserName());
				Rule rule = (Rule) context.getObjectByName(Rule.class,this._workflowRuleName);
				LogEnablement.isLogDebugEnabled(threadPoolLogger,"Rule Request..."+rule.getName());
				reqdef=context.getObjectByName(RequestDefinition.class, SuspensionRestoreRMThreadPoolProcessor.RULEREQUEST);
				req.put(RuleRequestExecutor.ARG_RULE, rule.getName());
				req.setAttribute(RuleRequestExecutor.ARG_RULE,  rule.getName());
				allArgs.put(RuleRequestExecutor.ARG_RULE,  rule.getName());
			}
			if(reqdef!=null)
			{
				req.setDefinition(reqdef);
				LogEnablement.isLogDebugEnabled(threadPoolLogger,"Set Request Definition..."+reqdef.getName());
			}
			// Start 5 seconds from now.
			long current = System.currentTimeMillis();
			//Pick this up from Global Definition
			int waitSec=this._amountOfSeconds;
			current += TimeUnit.SECONDS.toMillis(waitSec);
			String requestName = "Disable Request" + " FOR " + identityName+ " " + current;
			if(this._enablePlan!=null && this._enablePlan.equalsIgnoreCase(SuspensionRestoreRMThreadPoolProcessor.RESTORE))
			{
				requestName = "Enable Request" + " FOR " + identityName+ " " + current;
			}
			allArgs.put("requestName", requestName);
			allArgs.putAll(launchArgsMap);
			LogEnablement.isLogDebugEnabled(threadPoolLogger,"requestName..."+requestName);
			req.setEventDate(new Date(current));
			Identity id = context.getObjectByName(Identity.class, "spadmin");
			req.setOwner(id);
			req.setName(requestName);
			req.setAttributes(reqdef, allArgs);
			// Launch the work flow via the request manager.
			RequestManager.addRequest(context, req);
			LogEnablement.isLogDebugEnabled(threadPoolLogger,"Request Added to Queue...");
			if (reqdef != null && context != null) 
			{
				context.decache(reqdef);
			}
			if (id != null && context != null) {
				context.decache(id);
			}
			LogEnablement.isLogDebugEnabled(threadPoolLogger,"End startRequestManagerCallables");
		}
		catch(Exception ex)
		{
			LogEnablement.isLogErrorEnabled(threadPoolLogger,"End startRequestManagerCallables.."+ex.getMessage());
			ex.getStackTrace();
		}
	}
	/**
	 * Find All Identities based on Population, if provided
	 * @param context
	 * @param includePopulatioName
	 * @return
	 * @throws GeneralException
	 */
	private  List buildIdNameList(SailPointContext context, String includePopulatioName) throws GeneralException
	{
		LogEnablement.isLogDebugEnabled(threadPoolLogger,"...buildIdNameList.");
		QueryOptions qo = new QueryOptions();
        ArrayList trueAndFalse = new ArrayList();  
		trueAndFalse.add(new java.lang.Boolean(true));  
		trueAndFalse.add(new java.lang.Boolean(false));  
		qo.addFilter(Filter.in("workgroup", trueAndFalse)); 
        if(includePopulatioName!=null)
        {
        	Filter filter=	getGDFilter(context, includePopulatioName);
        	if(filter!=null)
        	{
        		qo.addFilter(filter);
        		LogEnablement.isLogDebugEnabled(threadPoolLogger,"...Add Inclusion Filter..."+filter.toString());
        	}
        }
        Iterator iter = context.search(Identity.class, qo, "name");
        List list = new ArrayList();
        String name=null;
		if (iter != null && iter.hasNext())
		{
	    	while (iter.hasNext()) 
	    	{
	    		 Object[] item = (Object[]) iter.next();
	    		 if(item!=null && item.length==1)
	    		 {
	    			 name = (String) item[0];	
	    			 list.add(name);
	    		 }
	      	}
	    }
		LogEnablement.isLogDebugEnabled(threadPoolLogger,"...End buildIdNameList.");
		LogEnablement.isLogDebugEnabled(threadPoolLogger,"...End buildIdNameList."+list);
	    Util.flushIterator(iter);
	    return list;
	}
	/**
	 * Find All Links based on Selected Applications 
	 * @param context
	 * @param appName
	 * @param identityName
	 * @param map
	 * @return
	 * @throws GeneralException
	 */
	private void buildLinkMap(SailPointContext context, String appName, String identityName, HashMap map) throws GeneralException
	{
		LogEnablement.isLogDebugEnabled(threadPoolLogger,"...buildLinkLMap.");
		QueryOptions qo = new QueryOptions();
        qo.add(Filter.ignoreCase(Filter.eq("application.name", appName)));
        qo.add(Filter.eq("identity.name", identityName));
        Iterator iter = context.search(Link.class, qo, "nativeIdentity");
        List list = new ArrayList();
        String name=null;
		if (iter != null && iter.hasNext())
		{
	    	while (iter.hasNext()) 
	    	{
	    		 Object[] item = (Object[]) iter.next();
	    		 if(item!=null && item.length==1)
	    		 {
	    			 name = (String) item[0];	
	    			 list.add(name);
	    		 }
	      	}
	    }
		if(list!=null && list.size()>0)
		{
			map.put(appName, list);
		}
		LogEnablement.isLogDebugEnabled(threadPoolLogger,"...End buildLinkMap.");
		Util.flushIterator(iter);
	}
	/**
	 * Match Group Definition
	 * 
	 * @param identity id
	 * @param populationName
	 * @return
	 * @throws GeneralException
	 */
	private int matchPopulationUsingName(SailPointContext context,
			String identityName, String populationName) throws GeneralException {
		LogEnablement.isLogDebugEnabled(threadPoolLogger,"Enter matchPopulation");
		int count = 0;
		QueryOptions ops = new QueryOptions();
		GroupDefinition groupDefinition = context.getObjectByName(
				GroupDefinition.class, populationName);
		if (groupDefinition != null) {
			Filter filterGd = groupDefinition.getFilter();
			if (filterGd != null) {
				Filter combo = Filter.and(Filter.eq("name", identityName),
						filterGd);
				ops.add(combo);
				count = context.countObjects(Identity.class, ops);
			}
			context.decache(groupDefinition);
		}
		LogEnablement.isLogDebugEnabled(threadPoolLogger,"End matchPopulation " + count);
		return count;
	}
	/**
	 * Get Group Definition Filter
	 * @param context
	 * @param populationName
	 * @return
	 * @throws GeneralException
	 */
	private Filter getGDFilter(SailPointContext context, String populationName) throws GeneralException
	{
		LogEnablement.isLogDebugEnabled(threadPoolLogger,"Enter addGDFilter");
		GroupDefinition groupDefinition = context.getObjectByName(GroupDefinition.class, populationName);
		Filter filterGd=null;
		if (groupDefinition != null) 
		{
			filterGd = groupDefinition.getFilter();
			context.decache(groupDefinition);
		}
		LogEnablement.isLogDebugEnabled(threadPoolLogger,"End addGDFilter "+filterGd );
		return filterGd;
	}
}
