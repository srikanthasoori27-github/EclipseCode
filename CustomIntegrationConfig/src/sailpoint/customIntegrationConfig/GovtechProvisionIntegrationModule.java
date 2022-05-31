package sailpoint.customIntegrationConfig;

import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONException;
import org.json.JSONObject;
import sailpoint.api.SailPointContext;
import sailpoint.integration.AbstractIntegrationExecutor;
import sailpoint.integration.RequestResult;
import sailpoint.object.Application;
import sailpoint.object.IntegrationConfig;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.ProvisioningPlan.AccountRequest;
import sailpoint.object.ProvisioningPlan.AccountRequest.Operation;
import sailpoint.object.ProvisioningResult;
import sailpoint.tools.GeneralException;

public class GovtechProvisionIntegrationModule extends AbstractIntegrationExecutor {

	private SailPointContext context;
	private static final Log log = LogFactory.getLog(GovtechProvisionIntegrationModule.class);
	@Override
	public void configure(SailPointContext context, IntegrationConfig config)
			throws Exception {
		// TODO Auto-generated method stub
		this.context  = context;
		super.configure(context, config);
	}

	@Override
	public ProvisioningResult provision(ProvisioningPlan plan) throws Exception {
		System.out.println("Plan in integration config:"+plan.toXml());
		List<AccountRequest> accReqs = plan.getAccountRequests();
		for(int i=0;i<accReqs.size();i++)
		{
			AccountRequest accReq = accReqs.get(i);
			String applicationName =  accReq.getApplicationName();
			Operation operation  = accReq.getOperation();
			System.out.println("App Name:"+applicationName);
			Application app = this.context.getObjectByName(Application.class,applicationName);
			
			if(operation != null && operation.equals(ProvisioningPlan.AccountRequest.Operation.Disable))
			{
				//generateDisableOrEnableJSON(accReq.getNativeIdentity(),false);
			}
			if(operation != null && operation.equals(ProvisioningPlan.AccountRequest.Operation.Delete))
			{
				//generateDeleteJSON(accReq.getNativeIdentity());
			}
			if(operation != null && operation.equals(ProvisioningPlan.AccountRequest.Operation.Enable))
			{
				//generateDisableOrEnableJSON(accReq.getNativeIdentity(),true);
			}
			
		}
		ProvisioningResult pr = new ProvisioningResult();
		pr.setStatus(ProvisioningResult.STATUS_COMMITTED);
		return pr;
	}
	
	
	  
}
