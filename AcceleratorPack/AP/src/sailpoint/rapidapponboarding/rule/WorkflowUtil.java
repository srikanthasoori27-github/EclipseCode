/*
Copyright © 2018 SailPoint Technologies, Inc. All Rights Reserved.
All logos, text, content, and works of authorship, including but not limited to underlying code, programming or scripting language, designs, and/or graphics,
that are used and/or depicted herein are protected under United States and international copyright and trademark laws and treaties,
and may not be used or reproduced without the prior express written permission of SailPoint Technologies, Inc.
*/
package sailpoint.rapidapponboarding.rule;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import sailpoint.api.RequestManager;
import sailpoint.api.SailPointContext;
import sailpoint.object.Attributes;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.QueryOptions;
import sailpoint.object.Request;
import sailpoint.object.RequestDefinition;
import sailpoint.object.Workflow;
import sailpoint.rapidapponboarding.logger.LogEnablement;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
/**
 * Util for Workflow Objects
 * @author rohit.gupta
 *
 */
public class WorkflowUtil 
{
	private static final Log workflowUtilLogger = LogFactory.getLog("rapidapponboarding.rules");
	private static final String REQMANAGERNAME = "REQUEST MANAGER FEATURE BY ";
	private static List workflows = new ArrayList();
	/**
	 * Get All LCM Provisioning Workflows 
	 * @param context
	 * @return
	 * @throws GeneralException
	 */
	public static List getAllLcmWorkflows(SailPointContext context) throws GeneralException
	{
		LogEnablement.isLogDebugEnabled(workflowUtilLogger,"...Start getAllLcmWorkflows.");
		if(workflows!=null && workflows.size()<=0)
		{
			QueryOptions queryOptions = new QueryOptions();
			queryOptions.add(Filter.eq("type", "LCMProvisioning"));
			Iterator iter = context.search(Workflow.class, queryOptions, "name");
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
						workflows.add(name);
					}
				}
			}
			LogEnablement.isLogDebugEnabled(workflowUtilLogger,"...End getAllLcmWorkflows.");
			LogEnablement.isLogDebugEnabled(workflowUtilLogger,"...End getAllLcmWorkflows."+workflows);
			Util.flushIterator(iter);
			return workflows;
		}
		LogEnablement.isLogDebugEnabled(workflowUtilLogger,"...End getAllLcmWorkflows from static class variable."+workflows);
		return workflows;
	}
	/**
	 * Launch Request Manager Workflow
	 * @param context
	 * @param identityName
	 * @param plan
	 * @param autoVerifyIdentityRequest
	 * @param workflowName
	 * @param amountOfSeconds
	 * @param xDAYS
	 * @param noFiltering
	 * @param requestType
	 * @param flow
	 * @param comments
	 * @throws GeneralException
	 */
	public static void launchRequestManagerWorkflow(SailPointContext context, String identityName, ProvisioningPlan plan, String autoVerifyIdentityRequest, String workflowName, int amountOfSeconds, String xDAYS, String noFiltering, String requestType, String flow, String comments) throws GeneralException {
   	    LogEnablement.isLogDebugEnabled(workflowUtilLogger,"Start launchRequestManagerWorkflow");
		//Workflow launchArguments
		HashMap launchArgsMap = new HashMap();
		launchArgsMap.put("identityName",identityName);
		launchArgsMap.put("launcher","spadmin");		
		launchArgsMap.put("sessionOwner","spadmin");
		launchArgsMap.put("autoVerifyIdentityRequest",autoVerifyIdentityRequest);
		launchArgsMap.put("source","Workflow");
		launchArgsMap.put("trace","false");
		if(comments!=null)
		{
		launchArgsMap.put("workItemComments", comments);
		}
		if(flow!=null)
		{
		launchArgsMap.put("flow", flow);
		}
		else
		{
		launchArgsMap.put("flow", "AccessRequest");
		}
		launchArgsMap.put("notificationScheme","none");
		launchArgsMap.put("approvalScheme", "none");		
		launchArgsMap.put("fallbackApprover","spadmin");
		launchArgsMap.put("plan", plan);
		launchArgsMap.put("foregroundProvisioning", "true");
		launchArgsMap.put("noApplicationTemplates", "false");
		if(requestType!=null)
		{
		launchArgsMap.put("requestType", requestType);
		}
		else
		{
		launchArgsMap.put("requestType", "REQUEST MANAGER FEATURE");
		}
		if(null != noFiltering)
		{
			LogEnablement.isLogDebugEnabled(workflowUtilLogger,"noFiltering="+noFiltering);
			launchArgsMap.put("noFiltering", noFiltering);
		}
		LogEnablement.isLogDebugEnabled(workflowUtilLogger,"Start Request Manager Provisioning");
		if (null == workflowName || workflowName.isEmpty()) {
			workflowName=ROADUtil.DEFAULTWORKFLOW;
		}
		LogEnablement.isLogDebugEnabled(workflowUtilLogger,"Workflow name: " + workflowName);
		// Use the Request Launcher
		Request req = new Request();
		RequestDefinition reqdef = context.getObjectByName( RequestDefinition.class, "Workflow Request" );
		req.setDefinition( reqdef );
		Attributes allArgs = new Attributes();
		// IIQTC-321: Workflow needs to be called by name. (JOINER)
		allArgs.put("workflow", workflowName);
		// Start 5 seconds from now. 
		long current = System.currentTimeMillis();
		current += TimeUnit.SECONDS.toMillis(amountOfSeconds);
		String requestName = WorkflowUtil.REQMANAGERNAME + "spadmin " +current;
		if(xDAYS!=null)
		{
		requestName = WorkflowUtil.REQMANAGERNAME + xDAYS+" " +identityName+" "+current;
		}
		allArgs.put( "requestName", requestName );
		LogEnablement.isLogDebugEnabled(workflowUtilLogger,"requestName.."+requestName);
		allArgs.putAll( launchArgsMap );
		LogEnablement.isLogDebugEnabled(workflowUtilLogger,"launchArgsMap.."+launchArgsMap);
		req.setEventDate( new Date( current ) );
		Identity id = context.getObjectByName(Identity.class, "spadmin");
		req.setOwner(id);
		req.setName(requestName);
		req.setAttributes( reqdef, allArgs );
		// Actually launch the work flow via the request manager.
		RequestManager.addRequest(context, req);
		if(reqdef!=null)
		{
		context.decache(reqdef);
		}
		LogEnablement.isLogDebugEnabled(workflowUtilLogger,"End launchRequestManagerWorkflow");
    }
}
