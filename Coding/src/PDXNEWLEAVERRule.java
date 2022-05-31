import java.util.ArrayList;
import java.util.List;


import sailpoint.api.IdentityService;
import sailpoint.api.ObjectUtil;
import sailpoint.api.Provisioner;
import sailpoint.api.SailPointContext;
import sailpoint.api.Terminator;
import sailpoint.object.Application;
import sailpoint.object.Attributes;
import sailpoint.object.AuditConfig;
import sailpoint.object.AuditEvent;
import sailpoint.object.Identity;
import sailpoint.object.Link;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.ProvisioningPlan.AccountRequest;
import sailpoint.object.ProvisioningPlan.AttributeRequest;
import sailpoint.server.Auditor;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

public class PDXNEWLEAVERRule {

	
	SailPointContext context;
	Identity newIdentity;
	Identity previousIdentity;
	
	
	// DELETION LIST IS USED TO DELETE MULTIPERSONA
	// TERMINATION LIST IS USED TO IDENTIFY IF THERE IS ANY TERMINATIONS TO BE PROCESSED. 
	// IF TERMINATION LIST IS EMPTY AND DELETION LIST IS NOT , THEN ONLY REMOVE THE POSITION ID'S BUT DO NOT TRIGGER LCE
	// TERMINATION LIST WILL BE CALCULATED ONLY WHEN ALL THE POSITION ID'S BELONGING TO THAT AGENCY ARE TERMINATED.
	// FOR IDENTITIES SET IsTerminated FLAG = YES IF ITS ALREADY PROCESSED. USED FOR BACKDATED TERMINATIONS. OTHERWISE THIS FLAG WILL BE EMPTY.
	// FOR MULTIPERSONA THERE IS NO NEED TO SET THE FLAG AS WE ARE DELETING THE ACCOUNT LINK.


	public List iterateTerminatingAccounts(SailPointContext context, Identity identity) {
		System.out.println("Enter iterateTerminatingAccounts " + identity.getName());
		String dateofOutflow = (String) identity.getAttribute("iamDateofOutflow");
		String isTerminated= (String)identity.getAttribute("isTerminated");
		String identityAgencyCode = (String) identity.getAttribute("iamAgencyCode");
		System.out.println("The identityAgencyCode is " + identityAgencyCode);
		//used when only identity profile is terminated but there are other profiles still active and termination list is empty. 
		boolean setTerminated = false;

		List terminatingAccountsList = new ArrayList();
		List deletionAccountsList = new ArrayList();
		if (!Util.isNullOrEmpty(dateofOutflow) && Util.isNullOrEmpty(isTerminated) && isDateGreaterLessThenEqualToToday(context, dateofOutflow, "dd-MM-yyyy", LESSEQUAL)) {
			
			
			setTerminated= true;
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
				if (!Util.isNullOrEmpty(positionEndDate)&& isDateGreaterLessThenEqualToToday(context, positionEndDate, "dd-MM-yyyy", "LESSEQUAL")) {
					deletionAccountsList.add(link);
					if(!terminatingAccountsList.contains(agencyCode))	
						terminatingAccountsList.add(agencyCode);

					System.out.println("Terminating Agency Code list is " + terminatingAccountsList);
				}

				// If there is any POSITION for the same agency but no POSITION END DATE Do NOT Trigger Termination.
				else
				{
					if(terminatingAccountsList.contains(agencyCode) )
					{
						terminatingAccountsList.remove(agencyCode);
					}
				}

			}
		}
		
		// This is to check if the multipersona that belongs to same agency as identity profile is terminated but not the identity profile is terminated , then should not trigger LCE as there is still one position left. 
		if(!setTerminated && terminatingAccountsList.contains(identityAgencyCode))
		{
			terminatingAccountsList.remove(identityAgencyCode);
		}
		// IF THERE ARE CERTAIN POSITION STILL ACTIVE IN THE AGENCY BUT CERTAIN POSITIONS ARE TERMINATED
		if(deletionAccountsList!=null && deletionAccountsList.size()>0 && Util.isEmpty(terminatingAccountsList))
		{
			for(Link link : deletionAccountsList)
			{
				deleteLink(application,identity,link, true);
			}
		}
		if(Util.isEmpty(deletionAccountsList) && Util.isEmpty(terminatingAccountsList) && setTerminated)
		{
			setIdentityAttribute(context,identity.getName(),"isTerminated","YES");
		}
		System.out.println("Exit iterateTerminatingAccounts");
		return terminatingAccountsList;
	}
	
	public List getDeleteAccountsList(SailPointContext context, Identity identity) 
	{
		List deletionAccountsList = new ArrayList();
		Application application = context.getObjectByName(Application.class, "Govtech POCDEX Target Application");
		IdentityService idService = new IdentityService(context);
		List<Link> links = idService.getLinks(identity, application);

		if (links != null && links.size() > 0) {
			for (Link link : links) {
				String positionEndDate = (String) link.getAttribute("Position End Date");
				String agencyCode = (String) link.getAttribute("Present Agency Code");
				System.out.println("The native identity is " + link.getNativeIdentity()+ "positionEndDate " + positionEndDate);
				if (!Util.isNullOrEmpty(positionEndDate)&& isDateGreaterLessThenEqualToToday(context, positionEndDate, "dd-MM-yyyy", "LESSEQUAL")) {
					deletionAccountsList.add(link);
				}
			}
		}
		return deletionAccountsList;
	}
	 // Used to Set the Identity isTerminated Attribute to Yes
	public void setIdentityAttribute(SailPointContext context, String identityName, 
		      String attrName, Object attrVal) throws GeneralException{
		      System.out.println("Enter setIdentityAttribute");
		      System.out.println("The attrName is: " + attrName + ", attrVal: " + attrVal);
		      
		      if (identityName == null){
		         System.out.println("No identityName");
		         return;
		      }
		      
		      Identity identity = context.getObjectByName(Identity.class, identityName);
		      
		      if (identity == null){
		         System.out.println("No identity for " + identityName);
		         return;
		      }
		   
		      System.out.println("Set the attribute");
		      ProvisioningPlan setAttPlan = new ProvisioningPlan();
		      setAttPlan.setIdentity(identity);
		      AccountRequest accReq = new AccountRequest();
		      accReq.setApplication(ProvisioningPlan.APP_IIQ);
		      accReq.add(new AttributeRequest(attrName, attrVal));
		      setAttPlan.add(accReq);
		      Provisioner p = new Provisioner(context);
		      p.setNoLocking(true);
			  p.execute(setAttPlan);
		      System.out.println("Exit setIdentityAttribute");
		   }
	
	public void deleteLink(Application app, Identity ident, Link link, boolean lock)
	        throws GeneralException {  
	        // Lock the identity while we delete the link
	        if (lock) {
	            ident = ObjectUtil.lockIdentity(context,  ident);
	        }
	        try {
	            if (lock) {
	                link = ident.getLink(link.getId());
	                
	            }
	            // Link could have been deleted out from under us before we  
	            // acquired the lock. If so, nothing to do here.  
	            if (link != null) {
	            	String appName = link.getApplicationName();
	            	String accountName = link.getNativeIdentity();
	            	Attributes attrs = link.getAttributes();
	            	auditLinkDeletion(ident,appName,accountName,attrs);
	                Terminator t = new Terminator(context);
	                t.setNoDecache(true);
	                t.deleteObject(link);	
	                context.saveObject(ident);
                    context.commitTransaction();

	            } 
	        }
	        finally {
	            // Unlock the identity from which we deleted the link
	            if (lock && ident != null) {
	                ident.setLock(null);
	                context.saveObject(ident);
	                context.commitTransaction();
	            }
	        }
	    }

	 public void auditLinkDeletion(Identity ident, String applicationName, String accountName, Attributes attrs)
	            throws GeneralException {
	
	            AuditEvent event = new AuditEvent();
	            event.setAttributes(attrs);
	            event.setAction("PocdexMultiPersonaDeletion");
	            event.setTarget(ident.getName());
	            event.setApplication(applicationName);
	            event.setAccountName(accountName);
	 
	            Auditor.log(event);
	        
	    }
	 
	 
	 
	 public Object pdxTargetPOSIDCustomizationRule()
		{
			
			Log log = LogFactory.getLog("govtech.rule.pocdexTargetcustomizationrule");
			log.debug("****** Starting the customiztion Rule******");
			
		
			String newPOSID = object.getAttribute("New Position ID");
			String POSID=object.getAttribute("Position ID");
			String effectivePOSDate=object.getAttribute("Position Start Date");
			
			if(Util.isNullOrEmpty(effectivePOSDate) && !Util.isNullOrEmpty(newPOSID))
			{
				object.setAttribute("Position ID",newPOSID);
			}
			
			String Nric=object.getAttribute("ID No");
			object.setAttribute("IIQNRIC",Nric);
			object.remove("ID No");
			log.debug("****** Ending the customiztion Rule******");
			      return object;
		}
	 
		public boolean departmentTransferLCE() {

			// Log log = LogFactory.getLog("govtech.rule.identitytrigger.mover");

			if (newIdentity == null || previousIdentity == null)
				return false;
			// Checks  based on the New Position ID for Identity Profile
			// If New POSTION ID NOT NULL and POSITION END DATE NULL Trigger LCE. 
			if (newIdentity.getAttribute("iamNewPositionId") != null
				 && newIdentity.getAttribute("iamDateofOutflow") == null) {
				if (previousIdentity.getAttribute("iamNewPositionId") == null
						|| (previousIdentity != null && previousIdentity.getAttribute("iamNewPositionId") != null
								&& !newIdentity.getAttribute("iamNewPositionId")
										.equals(previousIdentity.getAttribute("iamNewPositionId")))) {
					log.error("Returing the true from loop2" + newIdentity.getAttribute("iamNewPositionId")
							+ " previous positionid " + previousIdentity.getAttribute("iamNewPositionId"));
					return true;
				}
			}
			
			
			// Checks based on the JOBTITLE, DEPARTMENT NAME , DEPARTMENT ID FOR IDENTITY PROFILE
			//Department ID|Department Name|Title
			if(!Util.nullSafeEq(newIdentity.getAttribute("iamDepartmentId"), previousIdentity.getAttribute("iamDepartmentId"),true) ||
					!Util.nullSafeEq(newIdentity.getAttribute("iamDepartmentName"), previousIdentity.getAttribute("iamDepartmentName"),true) ||
					!Util.nullSafeEq(newIdentity.getAttribute("iamJobTitle"), previousIdentity.getAttribute("iamJobTitle"),true)) {
			
				
				return true;
			}
			
			Application application = context.getObjectByName(Application.class, "Govtech POCDEX Target Application");
			List<Link> links = newIdentity.getLinks(application);
			List departmentChangeAccountsList = new ArrayList();
			if (links != null && links.size() > 0) {
				for (Link currentLink : links) {
					String nativeIdentity = currentLink.getNativeIdentity();
					String newDeptID = (String) currentLink.getAttribute("New Position ID");
					String dateofOutflow = (String) currentLink.getAttribute("Position End Date");
					
					// Check for the NEW POSID without Termination Date
					if (Util.isNotNullOrEmpty(newDeptID) && Util.isNullOrEmpty(dateofOutflow)) {
						if (previousIdentity != null) {
							Link previousLink = previousIdentity.getLink(application, nativeIdentity);
							if (previousLink != null) {
								String prevDeptID = (String) previousLink.getAttribute("New Position ID");
								if (prevDeptID == null || (prevDeptID != null && !prevDeptID.equals(newDeptID))) {
									log.error("Adding to the list of department changes " + prevDeptID + "  "
											+ newDeptID);
									departmentChangeAccountsList.add(nativeIdentity);
								}
							}
						}

					}
					
					//

				}
			}
			if (departmentChangeAccountsList != null && departmentChangeAccountsList.size() > 0)
				return true;
			return false;

		}

	}
