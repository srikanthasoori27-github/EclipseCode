package sailpoint.customIntegrationConfig;

import java.util.Date;

import sailpoint.api.RequestManager;
import sailpoint.api.SailPointContext;
import sailpoint.object.Attributes;
import sailpoint.object.Identity;
import sailpoint.object.Request;
import sailpoint.object.RequestDefinition;
import sailpoint.object.Workflow;
import sailpoint.tools.GeneralException;
import sailpoint.workflow.StandardWorkflowHandler;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.SailPointContext;

import sailpoint.integration.AbstractIntegrationExecutor;
import sailpoint.object.Application;
import sailpoint.object.IntegrationConfig;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.ProvisioningPlan.AccountRequest;
import sailpoint.rest.WorkItemArchiveExtendedResource;
import sailpoint.object.ProvisioningResult;
import sailpoint.object.Schema;


public class ProvisionIntegrationModule extends AbstractIntegrationExecutor
{
	private SailPointContext context;
	private static final Log log = LogFactory.getLog(ProvisionIntegrationModule.class);
	@Override
	public void configure(SailPointContext context, IntegrationConfig config)
			throws Exception {
		// TODO Auto-generated method stub
		this.context  = context;
		super.configure(context, config);
	}

	@Override
	public ProvisioningResult provision(ProvisioningPlan plan) throws Exception 
	{
		// TODO Auto-generated method stub
		System.out.println("Plan in integration config:"+plan.toXml());
		List<AccountRequest> accReqs = plan.getAccountRequests();
		for(int i=0;i<accReqs.size();i++)
		{
			AccountRequest accReq = accReqs.get(i);
			String applicationName =  accReq.getApplicationName();
			ProvisioningPlan.AccountRequest.Operation operation = accReq.getOperation();
			System.out.println("App Name:"+applicationName);
			Application app = this.context.getObjectByName(Application.class,applicationName);
			
			if(operation != null && operation.equals(ProvisioningPlan.AccountRequest.Operation.Create))
			{
				launchworkflow("Custom Integration Config workflow",plan.getIdentity().getName(),plan);
			}
		}
		ProvisioningResult pr = new ProvisioningResult();
		pr.setStatus(ProvisioningResult.STATUS_COMMITTED);
		return pr;
		//return super.provision(plan);
	}
	
	
	public void launchworkflow(String workflowName, String identityName,ProvisioningPlan plan) throws GeneralException
	{
	
		//workflowName = "Custom Integration Config workflow";
		//sample args that can be passed to the workflow
		//Workflow case name g
		String argumentVal  = "sample arg value you can pass to workflow";

		String caseName     = "Run '" + workflowName + "' for: " + identityName;

		String requesterId  = "spadmin";

		Workflow eventWorkflow = context.getObject(Workflow.class, workflowName);

		if (null == eventWorkflow) {

		   log.error("Could not find a workflow named: " + workflowName);

		   throw new GeneralException("Invalid worklfow: " + workflowName);

		}

		// Simulate the request being submitted by a user. Default: spadmin.

		Identity id = context.getObjectByName(Identity.class, requesterId);

		if (null == id) {

		   log.error("Could not find a requester Identity: " + requesterId);

		   throw new GeneralException("Invalid identity: " + requesterId);

		}

		// Ask the Request Processor to start the workflow 5 seconds from now.

		// Append the time stamp to the workflow case name to ensure it's unique.

		long launchTime = System.currentTimeMillis() + 5000;

		caseName = caseName + "(" + launchTime + ")";
		System.out.println("Launching the workflow with case name "+caseName);

		// Build out a map of arguments to pass to the Request Scheduler.

		Attributes reqArgs = new Attributes();

		reqArgs.put(StandardWorkflowHandler.ARG_REQUEST_DEFINITION,sailpoint.request.WorkflowRequestExecutor.DEFINITION_NAME);

		reqArgs.put(sailpoint.workflow.StandardWorkflowHandler.ARG_WORKFLOW,workflowName);

		reqArgs.put(sailpoint.workflow.StandardWorkflowHandler.ARG_REQUEST_NAME,caseName);
        
		reqArgs.put( "requestName", caseName );           

		// Build a map of arguments to pass to the Workflow case when it launches.

		Attributes wfArgs = new Attributes();

		wfArgs.put("identityName",    identityName);

		wfArgs.put("exampleArgument", argumentVal);
		wfArgs.put("plan", plan);

		wfArgs.put("workflow",eventWorkflow.getName());

		reqArgs.putAll(wfArgs);

		// Use the Request Launcher to schedule the workflow reqeust.  This requires

		// a Request object to store the properties of the request item.

		Request req = new Request();

		RequestDefinition reqdef = context.getObject(RequestDefinition.class, "Workflow Request");

		req.setDefinition(reqdef);

		req.setEventDate( new Date( launchTime ) );

		req.setOwner(id);

		req.setName(caseName);

		req.setAttributes( reqdef, reqArgs );

		// Schedule the work flow via the request manager.

		RequestManager.addRequest(context, req);
	}
	
	
}
