
package Coding;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import sailpoint.api.IdentityService;
import sailpoint.api.SailPointContext;
import sailpoint.object.AccountItem;
import sailpoint.object.Application;
import sailpoint.object.ApprovalItem;
import sailpoint.object.Certification;
import sailpoint.object.CertificationEntity;
import sailpoint.object.CertificationGroup;
import sailpoint.object.CertificationItem;
import sailpoint.object.Custom;
import sailpoint.object.Field;
import sailpoint.object.Filter;
import sailpoint.object.Form;
import sailpoint.object.Identity;
import sailpoint.object.Link;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.ProvisioningPlan.AccountRequest;
import sailpoint.object.ProvisioningProject;
import sailpoint.object.QueryOptions;
import sailpoint.object.Rule;
import sailpoint.object.Workflow;
import sailpoint.rapidsetup.logger.LogEnablement;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

public class DummyRule {
	
	SailPointContext context;
	Log log;
	
	
	
	public void creationRule()
	{
		Form form = context.getObject(Form.class, "Work Item Archive Report Form");
		Field reqField = (Field) form.getField("requesters");
		ProvisioningProject project = new ProvisioningProject();
		System.out.println(project.getIIQPlan());
		System.out.println(project.getUnmanagedPlan().getClass());
		System.out.println(project.getMasterPlan());
		System.out.println(project.getPlans());
		reqField.getDescription();
		ApprovalItem item = new ApprovalItem();
		AccountItem accItem =(AccountItem) item.getValue();
		
	}
	
	public void joinerCustom(SailPointContext context, String agencyCode)
	{
		String email ="spadmin@govtech.com.sg";
		
		java.util.logging.ErrorManager err;
		List emailAddresses = new ArrayList();
		emailAddresses.remove(null);
		emailAddresses.addAll(Util.csvToList(email));
		
	    Custom agencyInfoObject = getCustomObject(context,"GovTech-Agency-Profile-Custom");

	    if(agencyInfoObject!=null)
	    {
	      Map agencyInfo= (Map)agencyInfoObject.get(agencyCode);

	      if(agencyInfo!=null)
	      {
	        List<Map> adminProfile = (List) agencyInfo.get("adminProfile");
	        
	        if(!Util.isEmpty(adminProfile))
	        {
	        	for(Map adminMap : adminProfile)
	        	{
	        		emailAddresses.add(adminMap.get("email"));
	        	}
	        	email= Util.listToCsv(emailAddresses, true);
	        }
	      }
	    }

		
	}
	
	public Custom getCustomObject(SailPointContext context,String name){
	    log.trace("Enter getAgencyInfoMappingObject");
	    Custom mappingObj = context.getObjectByName(Custom.class,name);
	    log.trace("Exit getAgencyInfoMappingObject");
	    return mappingObj;
	  }

	public Object buildMapRule()
	{


		log.error("Starting the BuildMap Rule");
		 Map map1 =new HashMap();

		Map map = DelimitedFileConnector.defaultBuildMap(cols, record);

		String pocdexID = map.get("POCDEX UID");
		pocdexID.sp
		String agencyCode = map.get("Present Agency Code");
		String positoinId = map.get("Position ID");
		 if(pocdexID!=null)
		  {

		    QueryOptions qo = new QueryOptions();
		    qo.add(Filter.eq("iamPocdexUid",pocdexID));
		    Iterator identitySearchitr= context.search(Identity.class, qo);
		    if(identitySearchitr.hasNext())
		    {
		      Identity identity = (Identity) identitySearchitr.next();
		      String identityAgencyCode = (String) identity.getAttribute("iamAgencyCode");
				if(identityAgencyCode!=null && identityAgencyCode.equals(agencyCode))
				{
				      log.error("identity found with the positionid and agencyCode "+positoinId+"  ,"+agencyCode);
				      return map;
				}
				 else
				{
				log.error("No identity found with the positionid and agencyCode "+positoinId+"  ,"+agencyCode);
				return map1;
		
				}
		  }
		    
		log.error("Ending the BuildMap Rule");
		return map;
		
	}
	
	public static void donothing()
	{
		
		
		String xml ="&lt;li&gt;Username: BHATIAK1\n &lt;li&gt;Password: XXXXX!R@\\n&lt;li&gt;Email: Kulbhushan.Bhatia@service.nsw.gov.au&lt;li&gt;Employee ID (to be used for verification purposes when contacting the GovConnect Service Desk): 00164229&lt;li&gt;Agency: SNSW Transport Team 4;";
		for(String s : xml.split("&lt;li&gt;"))
		{
			System.out.println("<li> "+s+" </li>");
			
		}
		
		Workflow workflow ;
		workflow.
		 List wogADIDList =identity.getAttribute("iamWogAdId");
		  String wogADID = link.getAttribute("WOG AD ID"); 
		  log.error("wogadidlist is "+wogADIDList);
		  log.error("wogadid is "+wogADID);
		  if(Util.isNotNullOrEmpty(wogADID))
		  {
		    if(wogADIDList!=null &amp;&amp; !wogADIDList.contains(wogADID))
		    {
		      wogADIDList.add(wogADID);
		    }
		    else if(Util.isEmpty(wogADIDList))
		    {
		      wogADIDList= new ArrayList();
		      wogADIDList.add(wogADID);			
		    }

		  }

		  return wogADIDList;
	}
	
	public static void main(String args[])
	{
		
		donothing();
	}

	
	public ProvisioningPlan filterAuthoritativeAppAccounts(ProvisioningPlan plan)
	{
		
		Rule rule = context.getObjectByName(Rule.class, "ass");
		if(plan!=null && plan.getAccountRequests()!=null && plan.getAccountRequests().size()>0)
		{
			List authAppList = getAuthoritativeApps();
			List<AccountRequest> appFilterList = new ArrayList();
			for(AccountRequest accRequest : plan.getAccountRequests())
			{
				if(authAppList.contains(accRequest.getApplicationName()))
						{
							appFilterList.add(accRequest);
						}
			}
			
			if(appFilterList!=null && appFilterList.size()>0)
			{
			   for(AccountRequest filterAccRequest: appFilterList)
				   plan.remove(filterAccRequest);
				   
			}
		}
		
		
		return plan;
	}
	
	public List getAuthoritativeApps()
	{
		List authAppList = new ArrayList();
		List propList = new ArrayList();
		propList.add("name");
		QueryOptions qo = new QueryOptions();
		qo.add(Filter.eq("authoritative", true));
		
		Iterator appsIterator = context.search(Application.class, qo,propList);
		
		while(appsIterator.hasNext())
		{
			Object[] obj=(Object[]) appsIterator.next();
			authAppList.add(obj[0]);
		}
		
		
		
		return authAppList;
	}
	public static void doabcd()
	{
		
		Certification certification;
		String arcName="spadmin";
		List certGroups= certification.getCertificationGroups();
		List certGroupIds= new ArrayList();
		for(CertificationGroup certGroup:certGroups)
		{
			certGroupIds.add(certGroup.getId());
		}
		List emailIdentities= new ArrayList();
		Application app = certification.getApplication(context);
		if(app!=null)
		{
			 arcName=app.getOwner().getName();
			 emailIdentities.add(arcName);
		}
		
		CertificationEntity entity;
		SailPointContext context;
		String recipientName;
		Map returnMap = new HashMap();
		List<CertificationItem> certItems = entity.getItems();
		List queryList = new ArrayList();
		queryList.add("iamArc");
		for(CertificationItem item : certItems)
		{
			String appName = item.getExceptionApplication();
			Application app = context.getObjectByName(Application.class,appName);
			
			recipientName = (String) app.getAttributeValue("iamArc");
			
			
			
			
			if (recipientName != null)
            {
              returnMap.put("recipientName", recipientName);
              returnMap.put("description","Delegating this to ARC");
              returnMap.put("reassign", Boolean.TRUE);
              LogEnablement.isLogDebugEnabled(certlogger,"returnMap..."+returnMap);
              return returnMap;
            }
		}
		
		
	}
	
	public boolean isValidHeader(String headerFromCSV,String headerFromCustomObject)
	{
		Util.atoi(headerFromCSV)
		if(Util.isNullOrEmpty(headerFromCSV))
			return false;
		if(Util.isNullOrEmpty(headerFromCustomObject))
			return false;
		if(headerFromCSV.equals(headerFromCustomObject))
			return true;
		else 
			return false;
	}
	
	public void doodo()
	{
		
		String endDate= Util.dateToString(new Date(),"ddMMyyyy");
		Calendar calendar1 = Calendar.getInstance();
        calendar1.setTime(time1);
        calendar1.add(Calendar.DATE, -5);
        String startDate= Util.dateToString(calendar1.getTime(),"ddMMyyyy");
	}
	public Object getValueFromKey(String key,String customObjectName) throws GeneralException
	{
		Object value;
		Custom customObj = context.getObjectByName(Custom.class,customObjectName);
		if(customObj!=null &amp;&amp; customObj.getAttributes()!=null)
		{
			value=customObj.get(key);
		}
		return value;
	}
	
	public void additionalEmailAddresses()
	{
		
		  //Log log = LogFactory.getLog("govtech.rule.identityAttribute.wogemail");
		  //log.info("Start govtech.rule.identityAttribute.wogemail");
		  IdentityService idService = new IdentityService(context);
		  Application pdxIdentityApplication = context.getObject(Application.class,"Govtech POCDEX Identity Application");
		  Application pdxTargetApplication = context.getObject(Application.class,"Govtech POCDEX Target Application");
		  Application itsmApplication = context.getObject(Application.class,"Govtech ITSM Daily Aggregation");
		  List appsList = new ArrayList();
		  if(pdxIdentityApplication!=null)
		  appsList.add(pdxIdentityApplication);
		  if(pdxTargetApplication!=null);
		  appsList.add(pdxTargetApplication);
		  if(itsmApplication!=null);
		  appsList.add(itsmApplication);
		  List emailList =new ArrayList();
		  List<Link>links = idService.getLinks(identity, appsList,null);

			for (Link appLink : links) {
				if (appLink.getApplicationName().equalsIgnoreCase(pdxIdentityApplication.getName())
						|| appLink.getApplicationName().equalsIgnoreCase(pdxTargetApplication.getName())) {
					String pdxAdditionalEmail = (String) appLink.getAttribute("Additional Email Addresses");
					if(Util.isNotNullOrEmpty(pdxAdditionalEmail))
					{
					for (String wogEmail : Util.csvToList(pdxAdditionalEmail)) {
						if (emailList != null && !emailList.contains(wogEmail)) {
							emailList.add(wogEmail);
						}
					}
					}
				}

				if (appLink.getApplicationName().equalsIgnoreCase(itsmApplication.getName())) {
					String itsmEmail = (String) appLink.getAttribute("Additional Email Addresses");
					if (Util.isNotNullOrEmpty(itsmEmail)) {
						emailList.add(itsmEmail);
					}
				}
			}
		  emailList.remove(null);
		  log.error("emailList is "+emailList);


		  return emailList;
	}
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
}
