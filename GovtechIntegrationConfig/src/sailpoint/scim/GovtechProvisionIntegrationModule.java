package sailpoint.scim;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONException;
import org.json.JSONObject;

import openconnector.Util;
import sailpoint.api.SailPointContext;
import sailpoint.integration.AbstractIntegrationExecutor;
import sailpoint.object.Application;
import sailpoint.object.Custom;
import sailpoint.object.IntegrationConfig;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.ProvisioningPlan.AccountRequest;
import sailpoint.object.ProvisioningPlan.AccountRequest.Operation;
import sailpoint.object.ProvisioningResult;
import sailpoint.tools.GeneralException;

public class GovtechProvisionIntegrationModule extends AbstractIntegrationExecutor {

	private SailPointContext context;
	private String customObjectName =null;
	private static final Log log = LogFactory.getLog(GovtechProvisionIntegrationModule.class);
	@Override
	public void configure(SailPointContext context, IntegrationConfig config)
			throws Exception {
	System.out.println("Enter configure()");
	super.configure(context, config);
		this.context  = context;
		this.setCustomObjectName((String) config.getAttribute("customObjectName"));
		System.out.println("Exit configure()");
		
		
	}

	
	  public void setCustomObjectName(String customObjectName) {
	        this.customObjectName = customObjectName;
	    }

	    public String getCustomObjectName() {
	        return this.customObjectName;
	    }
	    
	@Override
	public ProvisioningResult provision(ProvisioningPlan plan) throws Exception {
		System.out.println("Enter the method provision");
		System.out.println("Plan in integration config:"+plan.toXml());
		List<AccountRequest> accReqs = plan.getAccountRequests();
		for(int i=0;i<accReqs.size();i++)
		{
			AccountRequest accReq = accReqs.get(i);
			String applicationName =  accReq.getApplicationName();
			Operation operation  = accReq.getOperation();
			System.out.println("App Name:"+applicationName);
			Application app = context.getObjectByName(Application.class,applicationName);
			String agencyID=(String) app.getAttributeValue("iamAgencyName");
			String dateFormat = getDateFormat("yyyyMMdd");
			String inputFilePath="";
			String defaultPath="./";
			String filePath="";
		
			Custom custom = getIntegrationConfigMappingObject(context);
			if(custom!=null)
			{
			 inputFilePath=(String) custom.get("integrationConfigInputFilePath");
			 defaultPath=(String) custom.get("integrationConfigInputFiledefaultPath")!=null?(String) custom.get("integrationConfigInputFiledefaultPath"):"./";
			}
			if(agencyID==null)
			{
				System.out.println("Agency id is null .. so generating in the default folder");
			}
			
			if(Util.isNullOrEmpty(inputFilePath)||Util.isNullOrEmpty(agencyID)||Util.isNullOrEmpty(applicationName)||Util.isNullOrEmpty(operation.toString())||Util.isNullOrEmpty(dateFormat))
				{
				filePath=defaultPath; 
				}
			else
				filePath = inputFilePath+"/"+agencyID+"/"+applicationName+"/"+operation.toString()+"/"+dateFormat;
	
			System.out.println("filePath is "+filePath);
			if(operation != null && operation.equals(ProvisioningPlan.AccountRequest.Operation.Disable))
			{
				JSONObject jsonObject = generateDisableOrEnableJSON(accReq.getNativeIdentity(),false);
				writeJSONToFile(filePath,accReq.getNativeIdentity()+".json",jsonObject);
			}
			if(operation != null && operation.equals(ProvisioningPlan.AccountRequest.Operation.Delete))
			{
				JSONObject jsonObject =generateDeleteJSON(accReq.getNativeIdentity());
				writeJSONToFile(filePath,accReq.getNativeIdentity()+".json",jsonObject);
			}
			if(operation != null && operation.equals(ProvisioningPlan.AccountRequest.Operation.Enable))
			{
				JSONObject jsonObject =generateDisableOrEnableJSON(accReq.getNativeIdentity(),true);
				writeJSONToFile(filePath,accReq.getNativeIdentity()+".json",jsonObject);
			}
			
		}
		ProvisioningResult pr = new ProvisioningResult();
		pr.setStatus(ProvisioningResult.STATUS_COMMITTED);
		System.out.println("Exit the method provision");
		return pr;
	}
	
	 /*
		 * generateDisableOrEnableJSON - This will generate SCIM PatchOp for enable or disable
		 * @userid - the unique userid on which the operation needs to be performed
		 * @enableOrdisable - true for enable , false for disable the userid.
		 * 
		 * 
		 */
		public JSONObject generateDisableOrEnableJSON (String userid, boolean enableOrdisable)
		{
			  org.json.JSONObject request_body = new org.json.JSONObject();
			  org.json.JSONArray request_body_schemas = new org.json.JSONArray();
			  request_body_schemas.put("urn:ietf:params:scim:api:messages:2.0:PatchOp");
			  try {
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
			  } catch (JSONException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} 
			  return request_body;
		}
		/*
		 * This method will generate the JSON Format to disable the userid
		 * @userid - userid to disable
		 * 
		 */
		public JSONObject generateDeleteJSON(String userid)
		{
			 org.json.JSONObject request_body = new org.json.JSONObject();
			 try {
				request_body.put("userid", userid);
			} catch (JSONException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			 return request_body;
		}
		
		/*
		 * This method will write the JSON object to file. 
		 * @filePath - Path to which file needs to be written
		 * @fileName - Name of the file to which JSON to be written
		 * 
		 */
		 public void writeJSONToFile(String filePath,String fileName,JSONObject jsonObject)
			{
			 System.out.println("Enter the method writeJSONToFile");
			  File directory = new File(filePath);
			    if (! directory.exists()){
			        directory.mkdirs();
			    }
			 	  String absFilePath = filePath+"/"+fileName;
			 	  System.out.println("the absFilePath is "+absFilePath);
				  try  {
			            //We can write any JSONArray or JSONObject instance to the file
					  	FileWriter file = new FileWriter(new File(absFilePath));
			            file.write(jsonObject.toString()); 
			            file.flush();
			 
			        } catch (IOException e) {
			            e.printStackTrace();
			        }
				  System.out.println("Exit the method writeJSONToFile");
			}
		 /*
		  * Returns the integrationConfig Custom Object
		  * 
		  */
		 public Custom getIntegrationConfigMappingObject(SailPointContext context){
				System.out.println("Enter getIntegrationConfigMappingObject");
				
				Custom mappingObj = null;
				try {
					System.out.println("The custom object name is "+customObjectName);
					if(customObjectName==null)
						customObjectName="GovTech-Common-Settings";
					mappingObj = context.getObjectByName(Custom.class,customObjectName);
				} catch (GeneralException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				System.out.println("Exit getIntegrationConfigMappingObject: " + mappingObj);
				return mappingObj;
			}
		 
		 
		 /*
		  * Get Date Format for folderCreation
		  * 
		  */
		 
		 public String getDateFormat(String format)
		 {
			 System.out.println("Enter getDateFormat");
			 SimpleDateFormat simpleDateFormat = new SimpleDateFormat(format);
			 System.out.println("Exit getDateFormat" +simpleDateFormat.format(new Date()));
			 return simpleDateFormat.format(new Date());
		 }
			 
	  
}
