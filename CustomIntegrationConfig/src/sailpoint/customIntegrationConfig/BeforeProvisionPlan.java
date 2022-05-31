package sailpoint.customIntegrationConfig;
import sailpoint.api.SailPointFactory;
import sailpoint.connector.Connector;
import sailpoint.api.SailPointContext;
import sailpoint.api.Provisioner;
import sailpoint.object.*;
import sailpoint.object.ProvisioningPlan.AttributeRequest;
import sailpoint.object.ProvisioningPlan.AccountRequest;
import sailpoint.object.ProvisioningPlan.Operation; 
import sailpoint.tools.Util;
import sailpoint.tools.Message;
import sailpoint.tools.GeneralException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.DriverManager;
import java.sql.Timestamp;
import java.util.*;
import java.util.Map.Entry;
import java.text.*;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.print.attribute.standard.JobOriginatingUserName;
import javax.sql.DataSource;

import sailpoint.api.Aggregator;
import sailpoint.api.IdentityService;
import sailpoint.api.IncrementalObjectIterator;
import sailpoint.api.PasswordGenerator;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import sailpoint.api.IdentityService;



public class BeforeProvisionPlan {
	private static Log aslogger = LogFactory.getLog("rule.SP.AttrSynch.RulesLibrary");
	private static ProvisioningPlan plan;
	public static void attrSynchBeforeProvisionRule(SailPointContext context, Workflow workflow) throws GeneralException{
		
		     
		aslogger.trace("Enter attrsynchBeforeProvisionRule");
			//aslogger.debug("XML Workflow:" + workflow.toXml());
			plan = (ProvisioningPlan)workflow.get("plan");
			aslogger.debug("original Plan:" + plan.toXml());
			if (plan.getIdentity() == null)
			{
			aslogger.trace("Provisioning plan has no Identity");
			}
			
			aslogger.debug("Before Identity");
			
			Identity identity = plan.getIdentity()!=null?plan.getIdentity():context.getObjectByName(Identity.class, (String)workflow.get("identityName"));
			
			
			if (identity!=null && plan.getAccountRequests()!=null)
			{
				//lists to store the delete account requests and create account request during the Recreate operation.
				//do not modify the iterator directly .. may cause concurrent Modification error 
				//Also set them and pass them to workflow and do the processing there. 
				//nativeIdentityMap is used to store newNativeIdentity and old native identity and is used to compare when deleting the existing account
				// this map will be passed into workflow as a variable. 
				// example nativeIdentityMap.put("applicationName-oldNativeIdentity", newNativeIdentity")
				// Existing account will be deleted only if the new account is created
				
			List<AccountRequest> deleteExistingAccountsRequestList= new ArrayList();
			List<AccountRequest> recreateAccountRequestsList = new ArrayList();
			Map<String,List> nativeIdentityMap= new HashMap();
			aslogger.debug("Plan has Account Requests");
			Iterator accountRequestit = plan.getAccountRequests().iterator();
					while (accountRequestit != null && accountRequestit.hasNext())
					{
						aslogger.debug("Iteration on Account Requests");
						AccountRequest accountRequest = (AccountRequest) accountRequestit.next();
						
							Link link = identity.getLink(accountRequest);
				
							aslogger.debug("the link value is "+link);
							aslogger.debug(link.toXml());
							aslogger.debug("Account Request application name: " + accountRequest.getApplicationName());
							aslogger.debug("Link application name: " + link.getApplicationName());
							if (accountRequest.getApplicationName().equalsIgnoreCase(link.getApplicationName()))
							{
							aslogger.debug("Account Request native Identity: " + accountRequest.getNativeIdentity());
							aslogger.debug("Link native Identity: " + link.getNativeIdentity());
							String appIdatt = link.getApplication().getAccountSchema().getIdentityAttribute();
							aslogger.debug("Application name: " + link.getApplicationName() + " ID attribute: "+ appIdatt);
							List<AttributeRequest> attrRequests = accountRequest.getAttributeRequests();
							
							boolean isNativeIdModified=false;
							String newNativeIdvalue = "";
							//using this variable to avoid looping again if needs to remove from plan.
							AttributeRequest removedAttrRequest=null;
							for(AttributeRequest attrRequest : attrRequests)
							{
								aslogger.debug("attrRequest name : "+attrRequest.getName() +" attrRequest Value :"+attrRequest.getValue());
								
								if(attrRequest.getName().equals(appIdatt))
								{
									aslogger.debug("Native identity value change in plan");
									isNativeIdModified=true;
									newNativeIdvalue=attrRequest.getValue().toString();
									removedAttrRequest=attrRequest;
									
								}
							}
							
							//if the native Identity is modified 
							
							if(isNativeIdModified)
							{
								
								String changeType=(String) link.getApplication().getExtendedAttribute("iamAccountChangeID");
								aslogger.debug("changeType in the appication : "+changeType);
								
								
								if(Util.isNotNullOrEmpty(changeType))
								{
									//If changeType is NoAction 
									
									if("NoAction".equalsIgnoreCase(changeType))
									{
										aslogger.debug("Native identity Removing from Attribute list "+accountRequest.toXml());
										accountRequest.remove(removedAttrRequest);
										aslogger.debug("Modified Account Request  is "+accountRequest.toXml());
										aslogger.debug("Modified ProvisioningPlan is "+accountRequest.toXml());
										
										
									}
									else if("Rename".equalsIgnoreCase(changeType))
									{
										aslogger.debug("Inside the Rename Action");
									}
									else if("Recreate".equalsIgnoreCase(changeType)) 
									{
										AccountRequest originalAccountRequest = accountRequest;
										
										
										String oldNativeIdentity = originalAccountRequest.getNativeIdentity();
										AccountRequest newAccountRequest = new AccountRequest();
										String applicationName = originalAccountRequest.getApplicationName();
										newAccountRequest.setApplication(applicationName);
										newAccountRequest.setNativeIdentity(newNativeIdvalue);
										
										List<Form> provisioningForms = link.getApplication().getProvisioningForms();
										  
										List fieldNames= new ArrayList();
										//How do you determine the Operation ? for now everything is Set as it is new Account
										for(Form form : provisioningForms)	
										{
											
											for(Field field: form.getEntireFields())
											{
											fieldNames.add(field.getName());
											}
										}
									
										for (Map.Entry att : link.getAttributes().entrySet())
										{
										aslogger.debug("Key: "+ att.getKey() + " &amp; Value: " + att.getValue());
										aslogger.debug("fieldNames.contains(att.getKey()) "+ fieldNames.contains(att.getKey()));
										if(fieldNames.contains(att.getKey()))
										{
										newAccountRequest.add(new AttributeRequest((String)att.getKey(), ProvisioningPlan.Operation.Set, att.getValue()));
										}
										}
										
										
										//if original Account request has some attribute requests add them in new plan and remove the ones set from old link
										
										if(!Util.isEmpty(originalAccountRequest.getAttributeRequests()) && originalAccountRequest.getAttributeRequests()!=null)
										{
										for(AttributeRequest originalAttrRequest :originalAccountRequest.getAttributeRequests())
										{
											
											AttributeRequest attrRequestToRemove= newAccountRequest.getAttributeRequest(originalAttrRequest.getName());
											newAccountRequest.add(originalAttrRequest);
											newAccountRequest.remove(attrRequestToRemove);
										}
										}
										aslogger.debug("New Account Request is "+newAccountRequest.toXml());
										recreateAccountRequestsList.add(newAccountRequest);
										deleteExistingAccountsRequestList.add(originalAccountRequest);
											
										// In case multiple account requests for the same application, use list to add the old and new native identity values
											if(nativeIdentityMap.get(applicationName)==null)
											{
												List temp = new ArrayList();
												temp.add(oldNativeIdentity+"$$$"+newNativeIdvalue);
												nativeIdentityMap.put(applicationName,temp);
											}
											else
											{
												nativeIdentityMap.get(applicationName).add(oldNativeIdentity+"$$$"+newNativeIdvalue);
											}
										
										aslogger.debug("Exit reCreate loop ");
										// Insert audit here
										} // end of Recreate 
										
										
									}
									
								}
								
							}
							
	
						}
					
					workflow.put("recreateAccountRequestsList", recreateAccountRequestsList);
					workflow.put("deleteExistingAccountsRequestList", deleteExistingAccountsRequestList);
					workflow.put("nativeIdentityMap",nativeIdentityMap);
					
					//Remove the deleteAccount Request from the original plan as they will be processed in the deleteExistingAccounts step. 
					if(!Util.isEmpty(deleteExistingAccountsRequestList)&& deleteExistingAccountsRequestList!=null && deleteExistingAccountsRequestList.size()>0)
					{
						for(AccountRequest oldAccReq : deleteExistingAccountsRequestList)
						{
							plan.remove(oldAccReq);
						}
					}
					if(!Util.isEmpty(recreateAccountRequestsList)&& recreateAccountRequestsList!=null && recreateAccountRequestsList.size()>0)
					{
						for(AccountRequest newAccReq :recreateAccountRequestsList )
						{
							newAccReq.setOperation(AccountRequest.Operation.Create);
							plan.add(newAccReq);
						}
					}
					
					}	
					
				aslogger.trace("Exit attrSynchBeforeProvisionRule");
		}
		/*
		 *  This Rule will take the accountRequest Object and create a delete ProvisioningPlan for that Object.
		 *  Then provisioner will execute the account deletion 
		 */
		public static void deleteExistingAccounts(SailPointContext context, Workflow workflow,String identityName,List<AccountRequest> deleteExistingAccountsRequestList) {
			aslogger.trace("Enter deleteExistingAccounts");
			Identity identity;
			try {
				identity = context.getObjectByName(Identity.class, identityName);
				Map nativeIdentityMap = (Map)workflow.get("nativeIdentityMap");
				aslogger.debug("navtiveIdentity Map "+nativeIdentityMap);
				for (AccountRequest originalAccountRequest : deleteExistingAccountsRequestList) {
					
					aslogger.trace("The accountRequest in deleteExistingAcounts is "+originalAccountRequest.toXml());
					String appName=originalAccountRequest.getApplicationName();
					List<String> oldAndNewNativeIds = (List) nativeIdentityMap.get(appName);
					aslogger.trace("oldAndNewNativeIds  is "+oldAndNewNativeIds);
					String modifiedNativeIdentity=null;
					for(String temp : oldAndNewNativeIds)
					{
						if(temp.contains(originalAccountRequest.getNativeIdentity())) {
							modifiedNativeIdentity =temp.substring(temp.indexOf("$$$")+3, temp.length());
						}
					}
					aslogger.trace("ModifiedNativeIdentity is "+modifiedNativeIdentity);
					
					if(Util.isNotNullOrEmpty(modifiedNativeIdentity))
					{
					// Only delete the previous link - if the account creation with new link is successful
					IdentityService identityService = new IdentityService(context);
					List<Link> links = identityService.getLinks(identity,originalAccountRequest.getApplication(context));
					boolean isaccountAlreadyRecreated= false;
					if(links!=null && links.size()>0)
					{
						for(Link link : links)
							if(link.getNativeIdentity().equalsIgnoreCase(modifiedNativeIdentity))
							{
								isaccountAlreadyRecreated=true;
								break;
							}
					}
					aslogger.debug("the account already recreated ? : "+isaccountAlreadyRecreated);
					if(Util.isNotNullOrEmpty(modifiedNativeIdentity)&&isaccountAlreadyRecreated)
					{
					ProvisioningPlan deleteProvisioningPlan = new ProvisioningPlan();
					deleteProvisioningPlan.setIdentity(identity);
					originalAccountRequest.setOperation(AccountRequest.Operation.Delete);
					//do i need to remove attribute requests ?
					originalAccountRequest.setAttributeRequests(null);
					deleteProvisioningPlan.add(originalAccountRequest);
					aslogger.debug(deleteProvisioningPlan.toXml());
					Provisioner p = new Provisioner(context);
					p.setNoLocking(true);
					p.setOptimisticProvisioning(true);
					p.execute(deleteProvisioningPlan);
					audit_event("WBC-AccountIDChange","Account ID and Name Change","Attribute Sync",identity.getDisplayName(),originalAccountRequest.getApplicationName(),originalAccountRequest.getNativeIdentity(),"displayName",originalAccountRequest.getNativeIdentity(),"Native Identity Deleted","","","");
					}
					}

				}
			} catch (GeneralException e2) {
				// TODO Auto-generated catch block
				e2.printStackTrace();
				aslogger.error("Error in deleteExistingAccounts method");
			}
			aslogger.trace("Exit deleteExistingAccounts");
		}
	
	/*
	 *  This Rule will take the accountRequest Object and create a Create Account ProvisioningPlan.
	 *  Then provisioner will execute the plan and create new account 
	 */
	
	public static void recreateNewAccounts(SailPointContext context, Workflow workflow,String identityName,List<AccountRequest> recreateAccountRequestsList) {
		aslogger.trace("Enter recreateNewAccounts");
		Identity identity;
		try {
			identity = context.getObjectByName(Identity.class, identityName);
			for (AccountRequest createAccountRequest : recreateAccountRequestsList) {
				ProvisioningPlan createProvisioningPlan = new ProvisioningPlan();
				createProvisioningPlan.setIdentity(identity);
				createAccountRequest.setOperation(AccountRequest.Operation.Create);
				createProvisioningPlan.add(createAccountRequest);
				aslogger.debug(createProvisioningPlan.toXml());
				Provisioner p = new Provisioner(context);
				p.setNoLocking(true);
				p.setOptimisticProvisioning(true);
				p.execute(createProvisioningPlan);
				audit_event("WBC-AccountIDChange","Account ID and Name Change","Attribute Sync",identity.getDisplayName(),createAccountRequest.getApplicationName(),createAccountRequest.getNativeIdentity(),"displayName",createAccountRequest.getNativeIdentity(),"New Native Identity Recreated","","","");
			}
		} catch (GeneralException e2) {
			// TODO Auto-generated catch block
			e2.printStackTrace();
			aslogger.error("Error in recreateNewAccounts method");
		}
		aslogger.trace("Exit recreateNewAccounts");
	}
	
	/*
	 * Rule to run the target account aggregation after the create and delete account requests.  
	 * This is required to execute only if the native identity is modified and existing account is deleted and new account with new native identity has to created. 
	 */
	
	public static void doTargetedAggregation(SailPointContext context, String nativeId, String appName){
		aslogger.trace("Enter doTargetedAggregation: " + nativeId);
		
		try {
			
			Application app = context.getObjectByName(Application.class, appName);
			Connector connector = sailpoint.connector.ConnectorFactory.getConnector(app, null);  
			ResourceObject rObj = connector.getObject("account", nativeId, null);
			Attributes argMap = new Attributes();
			argMap.put("checkDeleted", true);
			Aggregator agg = new Aggregator(context, argMap);
			aslogger.trace("Calling aggregate() method... ");
			TaskResult taskResult = agg.aggregate(app, rObj);
			aslogger.trace("aggregation complete. ");


		} catch (Exception e){
			aslogger.error("Exception during targeted aggregation: " + e.toString());
		}
		
		
		aslogger.trace("Exit doTargetedAggregation: " + nativeId);
	}
	
	public static void doTargetedAggregationForRecreatedAccounts(SailPointContext context, Workflow workflow)
	{
		aslogger.trace("Enter doTargetedAggregationForRecreatedAccounts: ");
		Map nativeIdentityMap = (Map)workflow.get("nativeIdentityMap");
		aslogger.trace("nativeIdentityMap is "+nativeIdentityMap);
		if(!Util.isEmpty(nativeIdentityMap))
		{
			Set<Entry> entryset = nativeIdentityMap.entrySet();
			for(Entry entry :entryset)
			{
				String appName = entry.getKey().toString();
				List<String> oldAndNewNativeIds = (List) entry.getValue();
				aslogger.trace("entry key and value are  is "+appName +" : "+oldAndNewNativeIds);
				for(String nativeIds : oldAndNewNativeIds)
				{
					String newNativeId = nativeIds.substring(nativeIds.indexOf("$$$")+3, nativeIds.length());
					aslogger.trace("newNativeId  is "+newNativeId);
					if(Util.isNotNullOrEmpty(newNativeId))
					{
						doTargetedAggregation(context,newNativeId,appName);
					}
				}
			
				
			
			}
			
		}
		aslogger.trace("Exit doTargetedAggregationForRecreatedAccounts: ");
	}
	
	public static void doTargetedAggregationForAccounts(SailPointContext context, Workflow workflow)
	{
		
		aslogger.trace("Enter doTargetedAggregationForAccounts: ");
		ProvisioningPlan plan = (ProvisioningPlan)workflow.get("plan");
		if(plan!=null&& plan.getAccountRequests()!=null && plan.getAccountRequests().size()>0)
		{
			for(AccountRequest accRequest:plan.getAccountRequests() )
			{
				doTargetedAggregation(context,accRequest.getNativeIdentity(), accRequest.getApplicationName());
			}
		}
		
		aslogger.trace("Exit doTargetedAggregationForAccounts: ");
	}
	
	public static void doTargetedAggregationForDeletedAccounts(SailPointContext context, Workflow workflow)
	{
		aslogger.trace("Enter doTargetedAggregationForDeletedAccounts: ");
		Map nativeIdentityMap = (Map)workflow.get("nativeIdentityMap");
		aslogger.trace("nativeIdentityMap is "+nativeIdentityMap);
		if(!Util.isEmpty(nativeIdentityMap))
		{
			Set<Entry> entryset = nativeIdentityMap.entrySet();
			for(Entry entry :entryset)
			{
				String appName = entry.getKey().toString();
				List<String> oldAndNewNativeIds = (List) entry.getValue();
				aslogger.trace("entry key and value are  is "+appName +" : "+oldAndNewNativeIds);
				for(String nativeIds : oldAndNewNativeIds)
				{
					String newNativeId = nativeIds.substring(0,nativeIds.indexOf("$$$"));
					aslogger.trace("newNativeId  is "+newNativeId);
					if(Util.isNotNullOrEmpty(newNativeId))
					{
						doTargetedAggregation(context,newNativeId,appName);
					}
				}
			
				
			
			}
			
		}
		aslogger.trace("Exit doTargetedAggregationForDeletedAccounts: ");
	}
	
	public static void auditAttributeSync(SailPointContext context, ProvisioningPlan plan,String identityName)
	{
		 
		    try {
					Identity identity = plan.getIdentity()!=null?plan.getIdentity():context.getObjectByName(Identity.class,identityName);
					if(plan!=null && plan.getAccountRequests()!=null && plan.getAccountRequests().size()>0)
					{
						
						for(AccountRequest accRequest:plan.getAccountRequests() )
						 {
							/*String str1 ="Modified Attributes List : ";
							String str2 ="";
							if(accRequest.getAttributeRequests()!=null && accRequest.getAttributeRequests().size()>0)
							{
								for(AttributeRequest attrRequest:accRequest.getAttributeRequests())
								{
									str1=str1.concat(attrRequest.getName()+" : "+attrRequest.getValue()+" ");
								}
							}*/
							
					        audit_event("WBC-AccountIDChange","Account ID and Name Change","Attribute Sync",identity.getDisplayName(),accRequest.getApplicationName(),accRequest.getNativeIdentity(),"","",accRequest.getOp().name(),"","","");
					      } 
						}
				}
		    catch (GeneralException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
	}
	public static void attrSynchAfterProvisionRule(SailPointContext context, Workflow workflow){
	    aslogger.trace("Enter attrSynchAfterProvisionRule");
	    
	    auditAttributeSync(context,(ProvisioningPlan)workflow.get("plan"),(String)workflow.get("identityName"));
	    aslogger.trace("Exit attrSynchAfterProvisionRule");
	  }


	private static void audit_event(String string, String string2, String string3, String displayName, String string4,
			String string5, String string6, String string7, String string8, String string9, String string10,
			String string11) {
		sailpoint.object.IdentityChangeEvent  evt = new sailpoint.object.IdentityChangeEvent();
		evt.getCause();
aslogger.debug(string+" "+string2+" "+string3+" "+displayName+" "+string4+" "+string5+" "+string6+" "+string7+" "+string8+" "+string9+" "+string10+" "+string11);
		
	}
	
	public List getUpdateAttributeRequests(SailPointContext context, Identity identity, Link link, Application app,
			String nativeId, String instance, Custom mappingObj){
			aslogger.trace("Enter getUpdateAttributeRequests");
			List attrReqs = new ArrayList();
			
			String appName = app.getName();
			String appType = app.getType();
			String appIdentityName= app.getAccountSchema().getIdentityAttribute();
			String identityName = identity.getName();
			String defField = null;
			Object defVal = null;
			
			aslogger.trace("Processing " + nativeId + " on " + appName);
			
			String passwordField = app.getAttributeValue("passwordField");
			
			if (passwordField == null){
				passwordField = "userPassword";
			}
			
			if (mappingObj != null){
				aslogger.trace("Have mappingObj: " + mappingObj.toXml());
			}
			
			aslogger.trace("Build list of fields to skip.");
			List skipFields = new ArrayList();
			Attributes entrySkipFields = mappingObj.get("Application Skip Fields");
			
			aslogger.trace("Have entrySkipFields: " + entrySkipFields);
			if (entrySkipFields != null){
				
				aslogger.trace("EntrySkipFields: " + entrySkipFields);
				aslogger.trace("See if it contains the appName: " + appName);
				if (entrySkipFields.containsKey(appName)){
					aslogger.trace("Getting skip fields based on appName: " + appName);
					skipFields = entrySkipFields.get(appName);
				} else if (entrySkipFields.containsKey(appType)){
					aslogger.trace("Getting skip fields based on appType: " + appType);
					skipFields = entrySkipFields.get(appType);
				}
			}
			
			aslogger.debug("Have list of fields to skip: " + skipFields);
			
			aslogger.trace("Get the provisioning policy (Template)");					
			List templates = app.getTemplates();
			Template updateTemp = null;
			
			if (templates != null &amp;&amp; templates.size() > 0){
				aslogger.trace("loop the templates");
				
				for (Template temp : templates){
					String tempName = temp.getName();
					String objType = temp.getSchemaObjectType();
					
					boolean isAccount = false;
					
					aslogger.trace("Check objType: " + objType + " and tempName: " + tempName);
					if (objType != null &amp;&amp; objType.equalsIgnoreCase("account") ){
						isAccount = true;
					} else if (tempName != null &amp;&amp; tempName.equalsIgnoreCase("account") ){
						isAccount = true;
					}
					
					aslogger.trace("Check if the template is for the account prov policy: " + isAccount);
					if (isAccount){
						Template.Usage usage = temp.getUsage();
						if (usage.equals(Template.Usage.Create)){
							aslogger.trace("Set to create template in case no update");
							updateTemp = temp;
							break;
						}
					}
				}
			}
			
			aslogger.debug("Have the provsioning policy");
			
			if (updateTemp != null){
			
				aslogger.trace("Get the prov policy fields");
				
				FormRef formRef = updateTemp.getFormRef();
				if(formRef != null) { 
					String formName = formRef.getName(); 
					aslogger.trace("Application is referencing a form: "+formName); 
					Form form = context.getObjectByName(Form.class, formName); 
					fields = form.getEntireFields();
				} else {
					fields = updateTemp.getFields();
				}
	            
				Map allNewVals = new HashMap();
				
				if (fields != null &amp;&amp; fields.size() > 0){
					for (Field field : fields){
						aslogger.trace("Get field name");
						String fieldName = field.getName();
						String displayName = field.getDisplayName();
						
						aslogger.trace("Check if field is a password field or in list of skip fields: " + fieldName);
						aslogger.trace("Against: " + skipFields);
						if (skipFields.contains(fieldName)){
							aslogger.trace("Skip field: " + fieldName);
							
							continue;
						}
						
						aslogger.trace("Didn't skip");
						
						
						try {
							aslogger.trace("No easy way to check if displayOnly so using the xml");
							String xml = field.toXml();
							
							if (xml.indexOf("displayOnly") > -1){
								aslogger.debug("Display only field so skip");
								continue;
							}
						
						} catch (Exception fe){
							aslogger.error("Exception checking the xml");
						}
						
						Object oldVal = link.getAttribute(fieldName);
						if(oldVal instanceof List || oldVal instanceof Map){
							oldVal = oldVal.clone();
						}
						
						if (oldVal == null &amp;&amp; displayName != null){
							oldVal = link.getAttribute(displayName);
						}
						
						boolean runRule = true;
						boolean runScript = false;
						
						aslogger.trace("Get the rule and script");
						Rule rule = field.getFieldRule();
						Script script = null;
						
						if (rule == null){
							aslogger.warn("No field rule.");
							runRule = false;
							script = field.getScript();
							
							if (script == null){
								aslogger.warn("No field script.");
								runScript = false;
								continue;
							} else {
								runScript = true;
							}
						}
						
						HashMap params = new HashMap();
						
						params.put("context", context);
						
						params.put("identity", identity);
						params.put("field", field);
						
						
						if (runScript){
							aslogger.debug("Is a script so put all of the vals from the previous fields in case they are needed by the script.");
							params.putAll(allNewVals);
						}
						
						Object val = null;
						
						try {
							if (runRule){
								aslogger.trace("Run the rule");
								val = context.runRule(rule, params);
							} else if (runScript){
								aslogger.trace("Run the script");
								val = context.runScript(script, params);
							} else {
								aslogger.warn("No field rule or script to run so skip.");
							}
						} catch (Exception re){
							aslogger.error("*** EXCEPTION RUNNING RULE/SCRIPT: " + re.toString());
							continue;
						}
						
						allNewVals.put(fieldName, val);
						
						aslogger.trace("Compare new and old val for field, " + fieldName + ".  New Val: " + val + " Old val: " + oldVal);
						
						boolean addRequest = false;
						
						aslogger.trace("check if defField: " + defField);
						if (defField != null &amp;&amp; fieldName.compareTo(defField) == 0){
							aslogger.trace("Process a default field value");
							addRequest = false;
							//handleFieldWithDefaultValues(fieldName, oldVal, val, defVal, attrReqs);
							
						} else {
							
							if ("distinguishedName".equals(fieldName) || "dn".equals(fieldName) || "entrydn".equals(fieldName)){
								//if ("Active Directory - Direct".equals(app.getType())){
									updateADDNNameFields(nativeId, val, attrReqs);
								//}
							}
							else {
								if (!nativeId.startsWith(fieldName)){
									aslogger.trace("Now check if the value was updated");
									if(!(appIdentityName.equals(fieldName) && nativeId==null))
									{
									addRequest = isFieldValueUpdated(oldVal, val);
									}
									{
										System.out.println("Native identity value is null and fieldName is equal to NativeIdentity. May be its account creation. So skip checking the update "+fieldName);
									}
								}
								
							}
						}
						
						if (addRequest){
							aslogger.trace(fieldName + " attribute value has changed, create attr request");
			
							if(field.isMulti()) {
							   AttributeRequest attrReq = new AttributeRequest(fieldName, ProvisioningPlan.Operation.Add, val); 
							   attrReqs.add(attrReq);
							} else { 
							   AttributeRequest attrReq = new AttributeRequest(fieldName, ProvisioningPlan.Operation.Set, val); 
							   attrReqs.add(attrReq);
	                  }	

						}
					}
				}
			}
			
			
			aslogger.trace("Exit getUpdateAttributeRequests: " + attrReqs);
			return attrReqs;
		}
	public boolean isNewLinksChanged(Identity previousIdentity, Identity newIdentity, Custom mappingObj){
		SailPointContext context;
		aslogger.trace("Enter isNewLinkChanged");
		boolean flag = false;
		
		
		List<String> checkLinks = new ArrayList();
		QueryOptions qo = new QueryOptions();
		qo.addFilter(Filter.eq("identity.id", newIdentity.getId()));
		Iterator itr = context.search(Link.class, qo,"application.name");
		
		while(itr.hasNext())
		{ 
		Object[] obj= (Object[]) itr.next();
		checkLinks.add((String) obj[0]);
		}
		
		
		IdentityService is = new IdentityService(context);
		Application app;
		
		aslogger.trace("Have link names to check: " + checkLinks);
		
		if (checkLinks == null || checkLinks.isEmpty()){
			aslogger.warn("No links to check");
			return false;
		}
		
		aslogger.trace("Loop the link names");
		for (String checkLink : checkLinks){
			aslogger.trace("Checking link: " + checkLink);
			
			app = context.getObjectByName(Application.class, checkLink);
			
			at
			List prevLinks = is.getLinks(previousIdentity, app);
			List newLinks = is.getLinks(newIdentity, app);
			
			aslogger.trace("Check if either list is empty: " + prevLinks + ", " + newLinks);
			
			if (prevLinks == null || prevLinks.isEmpty() || newLinks == null || newLinks.isEmpty()){
				aslogger.warn("One of the lists is empty");
				continue;
			}
			
			aslogger.trace("Making rather big assumption here that there's only only instance of an account that would be checked in this manner.");
			Link prevLink = (Link) prevLinks.get(0);
			Link newLink = (Link) newLinks.get(0);
		
			List<String> linkAttrs = newLink.getAttributes().getKeys();
			
			
			aslogger.trace("Have list of attrs to check: " + linkAttrs);
			
			if (linkAttrs == null || linkAttrs.isEmpty()){
				aslogger.warn("No attr names to compare");
				continue;
			}
			
			aslogger.trace("Loop the schema attrs");
			for (String attrName : linkAttrs){
				aslogger.trace("Comparing the old and new value of: " + attrName);
				
				Object prevVal = prevLink.getAttribute(attrName);
				Object newVal = newLink.getAttribute(attrName);
				
				aslogger.trace("Check if prev and new val are differnt");
				flag = isFieldValueUpdated(prevVal, newVal);
				
				aslogger.trace("Check flag val: " + flag);
				if (flag){
					aslogger.debug("Old and new value are different for: " + attrName);
					break;
				}
			}
			if (flag) {
				aslogger.trace("No need to check other links as we have a true");
				break;
			}
		}
		}
	
	
		aslogger.trace("Exit isNewLinkChanged: " + flag);
		return flag;
	}

}
