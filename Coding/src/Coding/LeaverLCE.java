package Coding;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

import sailpoint.api.SailPointContext;
import sailpoint.object.Custom;
import sailpoint.object.Identity;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
//import org.apache.commons.logging.Log;
//import org.apache.commons.logging.LogFactory;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import sailpoint.api.RequestManager;
import sailpoint.api.SailPointContext;
import sailpoint.object.Application;
import sailpoint.object.Attributes;
import sailpoint.object.Filter;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.QueryOptions;
import sailpoint.object.Request;
import sailpoint.object.RequestDefinition;
import sailpoint.object.ProvisioningPlan.AccountRequest;
import sailpoint.workflow.WorkflowContext;
import sailpoint.object.Workflow;
import sailpoint.object.Attributes;
import sailpoint.object.Link;
import sailpoint.object.Application;
import sailpoint.api.IdentityService;

public class LeaverLCE {

	static SailPointContext context;

	// Log log = LogFactory.getLog("govtech.rulelibrary.leaver");
	
	/*
	 * Method to check if Leaver workflow needs to triggered or not. return true -
	 * if the date of outflow <= today's date and MovementType contains Termination.
	 * else - return false
	 * 
	 */

	public boolean isEligibleForLeaver(SailPointContext context, Identity newIdentity, Identity previousIdentity)
		{
			System.out.println("Enter isEligibleForLeaver "+newIdentity.getName());
			boolean flag= false;
	        if(newIdentity==null || newIdentity==void)
	        		return flag;
				List terminatingAccountsList=iterateTerminatingAccounts(context, newIdentity);
				if(terminatingAccountsList!=null && terminatingAccountsList.size()>0)
	      {
	        System.out.println("The termination list for the accounts is "+terminatingAccountsList);
					flag=true;
	      }
				System.out.println("Exit isEligibleForLeaver "+newIdentity.getName());
			return flag;
			
		}

	public List iterateTerminatingAccounts(SailPointContext context, Identity identity) {
		System.out.println("Enter iterateTerminatingAccounts " + identity.getName());
		String dateofOutflow = (String) identity.getAttribute("iamDateofOutflow");
		List positionIds = (List) identity.getAttribute("iamPositionId");
		List terminatingAccountsList = new ArrayList();
		List deletionAccountsList = new ArrayList();
		if (!Util.isNullOrEmpty(dateofOutflow) && isDateGreaterLessThenEqualToToday(context, dateofOutflow, "dd-MM-yyyy", EQUAL)) {
			String identityAgencyCode = (String) identity.getAttribute("iamAgencyCode");
			System.out.println("The identityAgencyCode is " + identityAgencyCode);
			terminatingAccountsList.add(identityAgencyCode);
			
			System.out.println("Terminating Agency Code list is " + terminatingAccountsList);
		}
		Application application = context.getObjectByName(Application.class, "Govtech POCDEX Target Application");
		IdentityService idService = new IdentityService(context);
		List<Link> links = idService.getLinks(identity, application);

		if (links != null && links.size() > 0) {
			for (Link link : links) {
				String positionEndDate = (String) link.getAttribute("Position End Date");
				String agencyCode = (String) link.getAttribute("Present Agency Code");
				System.out.println("The native identity is " + link.getNativeIdentity()+ "positionEndDate " + positionEndDate);
				if (!Util.isNullOrEmpty(positionEndDate)&& isDateGreaterLessThenEqualToToday(context, positionEndDate, "dd-MM-yyyy", EQUAL)) {
					deletionAccountsList.add(link.getNativeIdentity());
						terminatingAccountsList.add(agencyCode);
					
					System.out.println("Terminating Agency Code list is " + terminatingAccountsList);
				}
				else
				{
					if(terminatingAccountsList.contains(agencyCode))
					{
						terminatingAccountsList.remove(agencyCode);
					}
				}

			}
		}

		System.out.println("Exit iterateTerminatingAccounts");
		return terminatingAccountsList;
	}

	
	/*
	 * This method returns the time 22:00 hrs of the day. Used to schedule the
	 * Termination workflow at this time
	 * 
	 */

	public Date getEndofDay() {
		log.trace("Enter getEndofDay");
		Calendar cal = Calendar.getInstance();
		System.out.println("the current time is " + cal.getTime());
		cal.set(Calendar.HOUR_OF_DAY, 22);
		cal.set(Calendar.MINUTE, 00);
		cal.set(Calendar.SECOND, 00);
		log.trace("Exit getEndofDay");
		return cal.getTime();

	}

	/*
	 * This method returns the deffered plan to be executed.
	 * 
	 */
	public void executeDeferredPlan(Workflow wflow, ProvisioningPlan plan) throws GeneralException {
		System.out.println("Enter executeDeferredPlan");

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
		String requestName = "Deferred Leaver Request for " + plan.getIdentity().getName() + " to launch "
				+ Util.dateToString(startTime);

		allArgs.put("requestName", requestName);
		allArgs.put("identityName", plan.getIdentity().getName());
		System.out.println("requestName.." + requestName);

		Map launchArgsMap = new HashMap();
		launchArgsMap.put("plan", plan);
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
		log.trace("Exit executeDeferredPlan");
	}

	/*
	 * Returns the Authoritative apps in the system Also remove the ITSM Application
	 * from the Delete Provisioning Plan
	 * 
	 */
	public List getAuthoritativeApps() throws GeneralException {
		log.trace("Enter getAuthoritativeApps");
		List authAppList = new ArrayList();
		List propList = new ArrayList();
		propList.add("name");
		QueryOptions qo = new QueryOptions();
		qo.add(Filter.eq("authoritative", true));

		Iterator appsIterator = context.search(Application.class, qo, propList);

		while (appsIterator.hasNext()) {
			Object[] obj = (Object[]) appsIterator.next();
			authAppList.add(obj[0]);
		}

		log.trace("Exit getAuthoritativeApps");

		return authAppList;
	}

	/*
	 * Returns the provisioning plan that is filtered with Authoritative apps
	 * 
	 */
	public ProvisioningPlan filterAuthoritativeAppAccounts(ProvisioningPlan plan) throws GeneralException {
		log.trace("Enter filterAuthoritativeAppAccounts");

		if (plan != null && plan.getAccountRequests() != null && plan.getAccountRequests().size() > 0) {
			List authAppList = getAuthoritativeApps();
			List<AccountRequest> appFilterList = new ArrayList();
			String ITSMAppName = "Govtech ITSM Daily Aggregation";
			for (AccountRequest accRequest : plan.getAccountRequests()) {
				if (authAppList.contains(accRequest.getApplicationName())) {
					appFilterList.add(accRequest);
				}
				if (accRequest.getApplicationName().equalsIgnoreCase(ITSMAppName) && accRequest.getOperation()
						.equals(sailpoint.object.ProvisioningPlan.AccountRequest.Operation.Delete)) {
					appFilterList.add(accRequest);
				}
			}

			if (appFilterList != null && appFilterList.size() > 0) {
				for (AccountRequest filterAccRequest : appFilterList)
					plan.remove(filterAccRequest);

			}
		}

		log.trace("Exit filterAuthoritativeAppAccounts");
		return plan;
	}

	public Attributes getWorkflowArguments() {
		Attributes allArgs = new Attributes();
		allArgs.put("approvalScheme", "none");
		allArgs.put("notificationScheme", "none");
		allArgs.put("sessionOwner", "Scheduler");
		allArgs.put("source", "LCM");
		allArgs.put("launcher", "Scheduler");
		allArgs.put("workflow", "Govtech-Workflow-LCMProvisioning");
		allArgs.put("flow", "Lifecycle");
		allArgs.put("noTriggers", "true");

		return allArgs;
	}

	public ProvisioningPlan buildEventPlanForTerminatingAgencies(String op, String identityName,
			List terminatingAgencyCodes) throws GeneralException {

		System.out.println("Enter buildEventPlanForTerminatingAgencies");
		sailpoint.object.ProvisioningPlan.AccountRequest.Operation operation = null;
		if (op != null) {
			operation = AccountRequest.Operation.valueOf(op);
		}

		if (operation == null)
			throw new GeneralException("Operation (op) must be specified.");

		ProvisioningPlan plan = new ProvisioningPlan();
		Identity identity = context.getObject(Identity.class, identityName);
		if (null != identity) {
			List<Link> links = identity.getLinks();
			if ((null != links) && !links.isEmpty()) {
				plan = new ProvisioningPlan();
				plan.setIdentity(identity);

				for (Link link : links) {

					String agencyCode = (String) link.getExtendedAttribute("agencyCode");
					String appAgencyCode = (String) link.getApplication().getAttributeValue("iamAgencyName");
					if ((Util.isNotNullOrEmpty(agencyCode) && terminatingAgencyCodes.contains(agencyCode))
							|| (Util.isNotNullOrEmpty(appAgencyCode)
									&& terminatingAgencyCodes.contains(appAgencyCode))) {
						AccountRequest acctReq = new AccountRequest();
						acctReq.setApplication(link.getApplicationName());
						acctReq.setInstance(link.getInstance());
						acctReq.setNativeIdentity(link.getNativeIdentity());
						acctReq.setOperation(operation);
						plan.add(acctReq);
					}
				}
			}
		}
		System.out.println("Exit buildEventPlanForTerminatingAgencies");
		return plan;
	}

}
