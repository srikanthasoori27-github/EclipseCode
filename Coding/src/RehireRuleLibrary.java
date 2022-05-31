

import java.util.HashMap;
import java.util.List;

import sailpoint.api.SailPointContext;
import sailpoint.api.Workflower;
import sailpoint.integration.Util;
import sailpoint.object.Attributes;
import sailpoint.object.Filter;
import sailpoint.object.Filter.MatchMode;
import sailpoint.object.Identity;
import sailpoint.object.Link;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.ProvisioningPlan.AccountRequest;
import sailpoint.object.QueryOptions;
import sailpoint.object.Request;
import sailpoint.object.Workflow;
import sailpoint.object.WorkflowLaunch;
import sailpoint.tools.GeneralException;
import sailpoint.workflow.RapidSetupLibrary;
import sailpoint.workflow.WorkflowContext;
import sailpoint.object.Workflow;



public class RehireRuleLibrary {

	public SailPointContext context;
	public static final String VAR_IDENTITY_NAME = "identityName";
	public static final String ARG_DO_LOCKING = "false";
	String identityName="";
	public static RapidSetupLibrary rapidsetupLibrary = new RapidSetupLibrary();
	
	
	public void deleteDeferredLeaverPlans(String identityName) throws GeneralException
	{
		QueryOptions qo = new QueryOptions();
		qo.add(Filter.eq("owner.name",identityName));
		qo.add(Filter.like("name","Deferred Leaver Request for",MatchMode.START));
		List<Request> deferredObjectsList = context.getObjects(Request.class, qo);
	   if(!Util.isEmpty(deferredObjectsList))
	    {
		for(Request request : deferredObjectsList)
		{
			context.removeObject(request);
			context.commitTransaction();
			context.decache();
		}
	}
	}
	
	public void processJoinerAccounts(SailPointContext context,Workflow workflow,ProvisioningPlan plan) throws GeneralException
	{
		System.out.println("Enter the method processJoinerAccounts");
		String identityName=(String) workflow.get("identityName");
		
		if(plan!=null && plan.getAccountRequests()!=null && plan.getAccountRequests().size()>0)
		{
		System.out.println("The plan is "+plan.toXml());
		HashMap  map = new HashMap();
        map.put("launcher", "spadmin");
        map.put("identityName", identityName);
        Workflower wf = new Workflower(context);
        Workflow newWorkflow = context.getObjectByName(Workflow.class, "WBC-Workflow-ReHireBirthRightAccounts"); 
        if (newWorkflow != null) {
          WorkflowLaunch wfLaunch = wf.launchSafely(newWorkflow, "WBC-Workflow-ReHireBirthRightAccounts "+identityName, map); 
        }
        else
        {
        	System.out.println("Not able to find the workflow WBC-Workflow-ReHireBirthRightAccounts");
        }
		}
		System.out.println("Exit the method processJoinerAccounts");
	}
	public ProvisioningPlan buildEnableAccountsPlan(WorkflowContext wfc)
	       throws GeneralException {

		
		AccountRequest.Operation op=AccountRequest.Operation.Enable;
		ProvisioningPlan plan = new ProvisioningPlan();
	       Identity identity = getIdentity(wfc);
	       if (null != identity) {
	           List<Link> links = identity.getLinks();
	           if ((null != links) && !links.isEmpty()) {
	               
	               plan.setIdentity(identity);

	               for (Link link : links) {
	            	   
	            	   if(link.isDisabled())
	            	   {
	                   AccountRequest acctReq = new AccountRequest();
	                   acctReq.setApplication(link.getApplicationName());
	                   acctReq.setInstance(link.getInstance());
	                   acctReq.setNativeIdentity(link.getNativeIdentity());
	                   acctReq.setOperation(op);
	                   plan.add(acctReq);
	            	   }
	               }

	               
	           }
	       }
	       return plan;
	   }
	private Identity getIdentity(WorkflowContext wfc) throws GeneralException {
        Identity identity = null;

        Attributes<String,Object> args = wfc.getArguments();
        String name = args.getString(VAR_IDENTITY_NAME);
        if (name == null) {
            // We have historically fallen back on this workflow variable
            // so we didn't have to pass an arg.  I no longer like doing this
            // but we have to support it for older flows.
            name = wfc.getString(VAR_IDENTITY_NAME);
        }

        if (name == null)
            log.error("Missing identity name");
        else {        
            SailPointContext context = wfc.getSailPointContext();
            identity = context.getObjectByName(Identity.class, name);
            if (identity == null)  {
                // djs: 
                // This used to log an error moved to debug because 
                // during IIQ create cases we won't have the identity
                // in the database
                //
                log.debug("Invalid identity: " + name);
            }
        }

        return identity;
    }
	
}
