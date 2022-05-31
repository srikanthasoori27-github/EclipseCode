package Coding;

import java.text.ParseException;
import java.text.SimpleDateFormat;

import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ArrayList;
import sailpoint.tools.Util;
import sailpoint.object.Identity;
import sailpoint.object.Link;
import sailpoint.object.Workflow;
import java.util.Map;

import sailpoint.api.RequestManager;
import sailpoint.api.SailPointContext;
import sailpoint.object.Application;
import sailpoint.object.Attributes;
import sailpoint.object.Filter;

import sailpoint.object.ProvisioningPlan;
import sailpoint.object.ProvisioningPlan.AccountRequest;
import sailpoint.object.QueryOptions;
import sailpoint.object.Request;
import sailpoint.object.RequestDefinition;

import sailpoint.tools.GeneralException;

import sailpoint.workflow.WorkflowContext;

public class LeaverRuleLib {
	
	//private static Log log = LogFactory.getLog(LeaverDeferredPlanExecutor.class);
	SailPointContext context;
	
	private static final String GREATEREQUAL = "GREATEREQUAL";
	private static final String LESSEQUAL = "LESSEQUAL";
	private static final String EQUAL = "EQUAL";
	public boolean isEligibleForLeaver(SailPointContext context, Identity newIdentity, Identity previousIdentity)
	{
		System.out.println("Enter isEligibleForLeaver");
		boolean flag= false;
        if(newIdentity==null || newIdentity==void)
        		return flag;
		
		String dateofOutflow = (String) newIdentity.getAttribute("iamDateofOutflow"); 
		String movementType = (String) newIdentity.getAttribute("iamMovementType"); 
		String MovementType_Termination_PREFIX ="Termination From ";
		List agencyTerminationList = (List) newIdentity.getAttribute("iamAgencyTerminationList"); 
		try {
    
	  if(Util.isNullOrEmpty(dateofOutflow))
        return flag;
      if(Util.isNullOrEmpty(movementType))
          return flag;
	 
		// check if date of outflow less than or equal to today's termination date
		if(isDateGreaterLessThenEqualToToday(context,dateofOutflow,"dd-MM-yyyy",LESSEQUAL))
        {
        	System.out.println("date of outflow less than or equal to today's date "+dateofOutflow);
        	//check if movementType starts with temination from
        	// say movementType = "Termination from Agency A"
        	if(movementType.startsWith(MovementType_Termination_PREFIX))
        	{
        		String agencyList = movementType.substring(MovementType_Termination_PREFIX.length(), movementType.length());
        		List <String>terminatingAgenciesList=Util.csvToList(agencyList);
        		
        		if(Util.isEmpty(agencyTerminationList))
        			return true;
        		for(String terminatingAgency: terminatingAgenciesList)
        		{
        			if(!agencyTerminationList.contains(terminatingAgency))
        			{
        				flag=true;
        				break;
        			}
        		}
        		
        		
        	}
        }
		
		System.out.println("Exit isEligibleForLeaver");
		return flag;
		
	}
	
	/*
	 * This method is used to compare the dateString with current date.
	 * dateString - String format of the date that needs to be compared with today's date
	 * dateFormat - dateFormat to compare the dates. 
	 * dateOperation - Can be GREATEREQUAL,LESSEQUAL,EQUAL
	 * return true or false.
	 * 
	 */
	public static boolean isDateGreaterLessThenEqualToToday(
			SailPointContext context, String dateString, String dateFormat,
			String dateOperation) {
		String[] formatStrings = new String[1];
		if (dateFormat != null && dateFormat.length() > 0) {
			formatStrings[0] = dateFormat;
		} else {
			System.out.println("Exit isDateLessToday NO DATE FORMAT= false");
			return false;
		}
		// DEFAULT DATE OPERATION
		if (dateOperation == null || dateOperation.length() <= 0) {
			dateOperation = GREATEREQUAL;
		}
		System.out.println("Enter CommonRuleLibrary::isDateGreaterLessThenEqualToToday");
		if (!(dateString.equals(""))) {
			for (String formatString : formatStrings) {
				try {
					System.out.println("...dateString = " + dateString);
					System.out.println("...formatString = " + formatString);
					SimpleDateFormat sdf = new SimpleDateFormat(formatString);
					Date today = Calendar.getInstance().getTime();
					String todayDate = sdf.format(today);
					Date currentDate = sdf.parse(todayDate);
					Date anticipatedDate = new SimpleDateFormat(formatString).parse(dateString);
					int days = 0;
					System.out.println("...anticipatedDate = " + anticipatedDate);
					long timeDiffInMs = anticipatedDate.getTime()- currentDate.getTime();
					days = (int) (timeDiffInMs / (1000 * 60 * 60 * 24));
					System.out.println("...days = " + days);
					if (days < 0)
					{
						if (dateOperation.equalsIgnoreCase(GREATEREQUAL)) {
							System.out.println("Exit isDateLessToday LESS THAN TODAY= false");
							return false;
						} else if (dateOperation
								.equalsIgnoreCase(EQUAL)) {
							System.out.println("Exit isDateLessToday LESS THAN TODAY= false");
							return false;
						} else if (dateOperation
								.equalsIgnoreCase(LESSEQUAL)) {
							System.out.println("Exit isDateLessToday LESS THAN TODAY= true");
							return true;
						}
					}
					else if (days == 0)
					{
						System.out.println("Exit isDateEqualToday EQUAL TODAY= true");
						return true;
					}
					else
					{
						if (dateOperation
								.equalsIgnoreCase(GREATEREQUAL)) {
							System.out.println("Exit isDateGreaterToday GREATER THAN TODAY= true");
							return true;
						} else if (dateOperation
								.equalsIgnoreCase(EQUAL)) {
							System.out.println("Exit isDateGreaterToday GREATER THAN TODAY= false");
							return false;
						} else if (dateOperation
								.equalsIgnoreCase(LESSEQUAL)) {
							System.out.println("Exit isDateGreaterToday GREATER THAN TODAY= false");
							return false;
						}
					}
				} catch (ParseException e) {
					System.out.println("Date Format Exception = " + e.getMessage());
				}
			}
		}
		System.out.println("Exit isDateGreaterLessThenEqualToToday");
		return false;
	}
	
	public List getTerminatingAgenciesToProcess(String movementTypePrefix,String movementType,List agencyTerminationList)
	{
		List terminatingAgenciesToProcess = new ArrayList();
	
		if(movementType.startsWith(movementTypePrefix))
    	{
    		String agencyList = movementType.substring(movementTypePrefix.length(), movementType.length());
    		List <String>terminatingAgenciesList=Util.csvToList(agencyList);
    		
    		if(Util.isEmpty(agencyTerminationList))
    		{
    			terminatingAgenciesToProcess.addAll(terminatingAgenciesList);
    			return terminatingAgenciesToProcess;
    		}
    		else
    		{
    		for(String terminatingAgency: terminatingAgenciesList)
    		{
    			if(!agencyTerminationList.contains(terminatingAgency))
    			{
    				terminatingAgenciesToProcess.add(terminatingAgency);
    			}
    		}
    		}
    		
    		
    	}
	
	return terminatingAgenciesToProcess;
	
	}
	
	/*
	 * This method returns the time 22:00 hrs of the day. 
	 * Used to schedule the Termination workflow at this time
	 * 
	 */
	public Date getEndofDay()
	{
		Calendar cal = Calendar.getInstance();
		cal.set(Calendar.HOUR_OF_DAY, 22);
		cal.set(Calendar.MINUTE, 00);
		cal.set(Calendar.SECOND, 00);
		return cal.getTime();
		
	}
	
	/*
	 * This method returns the deffered plan to be executed.  
	 * 
	 */
	public void executeDeferredPlan(ProvisioningPlan plan)
            throws GeneralException {
        
      
        Date startTime = getEndofDay();
        System.out.println("Start Request Manager Provisioning");

        // Use the Request Launcher
        Request req = new Request();
        RequestDefinition reqdef = context.getObjectByName(RequestDefinition.class, "Workflow Request");
        req.setDefinition(reqdef);
       

        Identity planIdentity = plan.getIdentity();
        Attributes allArgs = getWorkflowArguments();
        String requesterIdentityName = "spadmin";
        req.setLauncher(requesterIdentityName);
        String requestName = "Deferred Leaver Request for " + plan.getIdentity().getName() +
                " to launch " + Util.dateToString(startTime);

        allArgs.put("requestName", requestName);
        allArgs.put("identityName", plan.getIdentity().getName());
        System.out.println("requestName.." + requestName);

        Map launchArgsMap = new HashMap();
        launchArgsMap.put("plan", plan);
        launchArgsMap.put("movementType", plan.getIdentity().getAttribute("iamMovementType"));
        System.out.println("launchArgsMap.." + launchArgsMap);
        allArgs.putAll(launchArgsMap);

        req.setEventDate(getEndofDay());
        // Change the owner to the plan identity so that will show under
        // the user's Events tab of Identity Warehouse
        req.setOwner(planIdentity);
        req.setName(requestName);
        req.setAttributes(reqdef, allArgs);
        // Actually launch the work flow via the request manager.
        RequestManager.addRequest(context, req);
        if (reqdef != null) {
            context.decache(reqdef);
        }

    }
	
	/*
	 * Returns the Authoritative apps in the system
	 * 
	 */
	public List getAuthoritativeApps() throws GeneralException
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
	/*
	 * Returns the provisioning plan that is filtered with Authoritative apps
	 * 
	 */
	public ProvisioningPlan filterAuthoritativeAppAccounts(ProvisioningPlan plan) throws GeneralException
	{
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
				//Remove the ITSM Application from the termination
				String ITSMAppName="Govtech ITSM Daily Aggregation";
				
				if(accRequest.getApplicationName().equalsIgnoreCase(ITSMAppName) && accRequest.getOperation().equals(sailpoint.object.ProvisioningPlan.AccountRequest.Operation.Delete))
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
	
	
	public Attributes getWorkflowArguments()
	{
		 Attributes allArgs= new Attributes();
		 allArgs.put("approvalScheme","none");
		 allArgs.put("notificationScheme","none");
		 allArgs.put("sessionOwner","Scheduler");
		 allArgs.put("source","Leaver");
		 allArgs.put("launcher","Scheduler");
		
		 
		 return allArgs;
	}
	
	
	public void updateAgencyTermList(Workflow workflow,String identityName)
	{
	   if(Util.isNotNullOrEmpty((String) workflow.get("movementType")))
	   {
		if(Util.isNotNullOrEmpty(identityName))
		{
			try {
				Identity identity = context.getObject(Identity.class, identityName);
				List terminationList = (List) identity.getAttribute("iamAgencyTerminationList");
				if(Util.isEmpty(terminationList))
				{
					terminationList= new ArrayList();
				}
				terminationList.add(workflow.get("movementType"));
				identity.setAttribute("iamAgencyTerminationList", terminationList);
				context.saveObject(identity);
				context.commitTransaction();
			} catch (GeneralException e) {
				e.printStackTrace();
			}
		}
	
	   }
	}
	
	 public boolean isEligibleForContractExpiry(SailPointContext context, Identity newIdentity, Identity previousIdentity)
		{
			log.trace("Enter isEligibleForContractExpiry");
			boolean flag= false;
	        if(newIdentity==null || newIdentity==void)
	        		return flag;
			
			String dateofOutflow = (String) newIdentity.getAttribute("iamDateofOutflow"); 
			String movementType = (String) newIdentity.getAttribute("iamMovementType"); 
			String MovementType_Termination_PREFIX ="ContractExpiry from ";
			List agencyTerminationList = (List) newIdentity.getAttribute("iamAgencyTerminationList"); 
		
	    
		  if(Util.isNullOrEmpty(dateofOutflow))
	        return flag;
	      if(Util.isNullOrEmpty(movementType))
	          return flag;
		 
			// check if date of outflow less than or equal to today's termination date
			if(isDateGreaterLessThenEqualToToday(context,dateofOutflow,"dd-MM-yyyy",LESSEQUAL))
	        {
	        	System.out.println("date of outflow less than or equal to today's date "+dateofOutflow);
	        	//check if movementType starts with temination from
	        	// say movementType = "Termination from Agency A"
	        	if(movementType.startsWith(MovementType_Termination_PREFIX))
	        	{
	        		//String agencyList = movementType.substring(MovementType_Termination_PREFIX.length(), movementType.length());
	        		//List &lt;String>terminatingAgenciesList=Util.csvToList(agencyList);
	            
	            //check if agencyTerminationList is empty
	            
	           if(Util.isEmpty(agencyTerminationList))
	        			return true;
	        		
	        		for(String terminatingAgency: agencyTerminationList)
	        		{
	        			if(!agencyTerminationList.contains(movementType))
	        			{
	        				flag=true;
	        				break;
	        			}
	        		}
	        		
	        		
	        	}
	        }
			
			log.trace("Exit isEligibleForContractExpiry");
			return flag;
			
		}
	  

	 
	 public ProvisioningPlan buildEventPlan(String op, String identityName) 
		        throws GeneralException {

		       
		        sailpoint.object.ProvisioningPlan.AccountRequest.Operation operation = null;
		        if ( op != null ) {
		            operation = AccountRequest.Operation.valueOf(op);
		        }
		        
		        if ( operation == null )
		            throw new GeneralException("Operation (op) must be specified.");
		        
		        ProvisioningPlan plan = new ProvisioningPlan();
		        Identity identity = context.getObject(Identity.class, identityName);
		        if (null != identity) {
		            List<Link> links = identity.getLinks();
		            if ((null != links) && !links.isEmpty()) {
		                plan = new ProvisioningPlan();
		                plan.setIdentity(identity);

		                for (Link link : links) {
		                	
		                	link.getAttribute(identityName)
		                    AccountRequest acctReq = new AccountRequest();
		                    acctReq.setApplication(link.getApplicationName());
		                    acctReq.setInstance(link.getInstance());
		                    acctReq.setNativeIdentity(link.getNativeIdentity());
		                    acctReq.setOperation(operation);
		                    
		                    
		                    plan.add(acctReq);
		                }
		            }
		        }
		        return plan;
		    }

}
