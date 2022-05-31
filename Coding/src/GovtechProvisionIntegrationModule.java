import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
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
	@Override
	public void configure(SailPointContext context, IntegrationConfig config) throws Exception {
		// TODO Auto-generated method stub
		this.context=context;
		super.configure(context, config);
	}

	@Override
	public SailPointContext getContext() throws GeneralException {
		// TODO Auto-generated method stub
		return super.getContext();
	}

	@Override
	public RequestResult getRequestStatus(String requestID) throws Exception {
		// TODO Auto-generated method stub
		return super.getRequestStatus(requestID);
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
				generateDisableOrEnableJSON(accReq.getNativeIdentity(),false);
			}
			if(operation != null && operation.equals(ProvisioningPlan.AccountRequest.Operation.Delete))
			{
				generateDeleteJSON(accReq.getNativeIdentity());
			}
			if(operation != null && operation.equals(ProvisioningPlan.AccountRequest.Operation.Enable))
			{
				generateDisableOrEnableJSON(accReq.getNativeIdentity(),true);
			}
			if(operation != null && operation.equals(ProvisioningPlan.AccountRequest.Operation.Modify))
			{
				generateModifyJSON(accReq.getNativeIdentity());
			}
			
		}
		ProvisioningResult pr = new ProvisioningResult();
		pr.setStatus(ProvisioningResult.STATUS_COMMITTED);
		return pr;
	}
	
	

	/*
	 * generateDisableOrEnableJSON - This will generate SCIM PatchOp for enable or disable
	 * @userid - the unique userid on which the operation needs to be performed
	 * @enableOrdisable - true for enable , false for disable the userid.
	 * 
	 * 
	 */
	public String generateDisableOrEnableJSON (String userid, boolean enableOrdisable)
	{
		  org.json.JSONObject request_body = new org.json.JSONObject();
		  org.json.JSONArray request_body_schemas = new org.json.JSONArray();
		  request_body_schemas.put("urn:ietf:params:scim:api:messages:2.0:PatchOp");
		  request_body.put("schemas", request_body_schemas); 
		  request_body.put("userid", userid);
		  org.json.JSONArray request_body_operation= new org.json.JSONArray();
		  org.json.JSONObject request_body_disable= new org.json.JSONObject();
		  request_body_disable.put("op", "replace");
		  request_body_disable.put("path", "active");
		  request_body_disable.put("value", enableOrdisable);
		  request_body_operation.put(request_body_disable);
		  request_body.put("Operations", request_body_operation);
		  System.out.println("JSON Object: "+request_body);
		  return request_body.toString();
	}
	/*
	 * This method will generate the JSON Format to disable the userid
	 * @userid - userid to disable
	 * 
	 */
	public String generateDeleteJSON(String userid)
	{
		 org.json.JSONObject request_body = new org.json.JSONObject();
		 request_body.put("userid", userid);
		 return request_body.toString();
	}
	
	public String generateModifyJSON(String userid) {
		// TODO Auto-generated method stub
		 org.json.JSONObject request_body = new org.json.JSONObject();
		  org.json.JSONArray request_body_schemas = new org.json.JSONArray();
		  request_body_schemas.put("urn:ietf:params:scim:api:messages:2.0:PatchOp");
		  request_body.put("schemas", request_body_schemas); 
		  request_body.put("userid", userid);
		  org.json.JSONArray request_body_operation= new org.json.JSONArray();
		  org.json.JSONObject request_body_disable= new org.json.JSONObject();
		  request_body_disable.put("op", "replace");
		  request_body_disable.put("path", "active");
		  request_body_disable.put("value", enableOrdisable);
		  request_body_operation.put(request_body_disable);
		  request_body.put("Operations", request_body_operation);
		  System.out.println("JSON Object: "+request_body);
		  return request_body.toString();
	}
	/*
	 * This method will write the JSON object to file. 
	 * @filePath - Path to which file needs to be written
	 * @fileName - Name of the file to which JSON to be written
	 * 
	 */
	 public void writeJSONToFile(String filePath,String fileName,JSONObject jsonObject)
		{
		 	  String absFilePath = filePath+"/"+fileName;
			  try  {
		            //We can write any JSONArray or JSONObject instance to the file
				  	FileWriter file = new FileWriter(absFilePath);
		            file.write(jsonObject.toString()); 
		            file.flush();
		 
		        } catch (IOException e) {
		            e.printStackTrace();
		        }
		}
	  
}
