package Coding;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import sailpoint.api.SailPointContext;
import sailpoint.object.Application;
import sailpoint.object.Custom;
import sailpoint.object.EmailOptions;
import sailpoint.object.EmailTemplate;
import sailpoint.object.Identity;
import sailpoint.object.Link;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class JoinerRuleLib {
	
	 Log log = LogFactory.getLog("govtech.rulelibrary.joiner");
	  log.info("Start govtech.rulelibrary.joiner");


	/*
	 * Method to check if Joiner workflow needs to triggered or not.  
	 * return true  -  if the date of inflow = today's date.
	 * else - return false
	 * 
	 */
	  
	public boolean isEligibleForJoiner(SailPointContext context, Identity newIdentity)
	{
		log.trace("Enter isEligibleForJoiner");
		boolean flag= false;
		SimpleDateFormat dateFormatter = new SimpleDateFormat("dd-MM-yyyy");
		String dateofInflow = (String) newIdentity.getAttribute("iamDateofInflow"); 
		try {
		Date inflowDate=dateFormatter.parse(dateofInflow); 
		if(Util.getDaysDifference(inflowDate, new Date())==0)
		{
			flag= true;
		}
		
		} catch (ParseException e) {
			// TODO Auto-generated catch block
			log.error("date of Inflow is invalid format "+dateofInflow);
			e.printStackTrace();
		}
		
		log.trace("Enter isEligibleForJoiner");
		return flag;
		
	}
	
	/*
	 * Method to retrieve the agencyAdministrator email
	 * Custom Object contains the information related to agency Administrator. Query Custom Obj and retreive the email from there.
	 * Fall back email if no email found. 
	 */
	
	public String getAgencyAdministratorEmail(SailPointContext context,String agencyCode) throws GeneralException
	{
		log.trace("Enter getAgencyAdministratorEmail");
		
		//fall back email if there is not agency email
			String email ="";
			Custom agencyInfoObject = getCustomObject(context,"Govtech-Custom-AgencyInformation");
			
			if(agencyInfoObject!=null)
			{
				Map agencyInfo= (Map)agencyInfoObject.get(agencyCode);
				
				if(agencyInfo!=null)
				{
				 email = (String) agencyInfo.get("AgencyAdminEmail");
				}
			}
			
		
		log.trace("Exit getAgencyAdministratorEmail");
		return email;
		
	}
	/*
	 * Retrieves the Custom Object with the name that is used for param.
	 * 
	 */
	public Custom getCustomObject(SailPointContext context,String name){
		log.trace("Enter getAgencyInfoMappingObject");
		Custom mappingObj = context.getObjectByName(Custom.class,name);
		log.trace("Exit getAgencyInfoMappingObject");
		return mappingObj;
	}
	
	/*
	 * Retrieves a new joiner email template. 
	 * 
	 */
	public String getNewJoinerEmailTemplate(SailPointContext context)
	{
		log.trace("Enter getNewJoinerEmailTemplate");
		Custom commSettings = getCustomObject(context,"GovTech-Common-Settings");
		if(commSettings!=null)
			return (String) commSettings.get("newJoinerEmailTemplateName");
		else
			return null;
	}
	/*
	 * Returns the identity display name.
	 * 
	 */
	public String getIdentityDisplayName(SailPointContext context, String identityName)
	{
		Identity identity = context.getObjectByName(Identity.class,identityName);
		if(identity!=null)
		{
			return identity.getDisplayName();
		}
	}
	
	public String getAgencyNameFromCode(String agencyCode) throws GeneralException
	{
		log.trace("Enter getAgencyNameFromCode");
    String agencyName="";
			Custom agencyInfoObject = getCustomObject(context,"Govtech-Custom-AgencyInformation");
			
			if(agencyInfoObject!=null)
			{
				Map agencyInfo= (Map)agencyInfoObject.get(agencyCode);
				
				if(agencyInfo!=null)
				{
				 agencyName = agencyInfo.get("AgencyName");
				}
			}
			
		
		log.trace("Exit getAgencyNameFromCode");
		return agencyName;
		
	}
	public void sendEmailToJoinerAgencyAdmin(SailPointContext context, Identity identity)
	{
		System.out.println("Enter sendEmailToJoinerAgencyAdmin");
		EmailTemplate template = context.getObject(EmailTemplate.class, getNewJoinerEmailTemplate(context));
		if(template!=null)
		{
		// Iterate the POCDEX Accounts 
		Application application = context.getObjectByName(Application.class,"Govtech POCDEX Target Application");
		if(application!=null)
		{
		List<Link> links = identity.getLinks(application);
		if(links!=null &&links.size()>0)
		{
			for(Link currentLink : links)
			{
				String nativeIdentity = currentLink.getNativeIdentity();
				String agencyCode= (String) currentLink.getAttribute("Present Agency Code"); 
				String department= (String) currentLink.getAttribute("Department Name"); 
				String dateofInflow = (String) currentLink.getAttribute("Position Start Date"); 
				if(dateofInflow!=null && isDateGreaterLessThenEqualToToday(context,dateofInflow,"dd-MM-yyyy","EQUAL") )
				{
					String to = getAgencyAdministratorEmail(context,agencyCode);
					
		            Map mailArgs = new HashMap();
		            mailArgs.put("startDate",dateofInflow);
		            mailArgs.put("agencyName",getAgencyNameFromCode(agencyCode));
		            mailArgs.put("department",currentLink.getAttribute("Department Name"));
		            mailArgs.put("title",currentLink.getAttribute("Title"));
		            mailArgs.put("email",currentLink.getAttribute("Email Address"));
		            EmailOptions options = new EmailOptions(to, mailArgs);
					context.sendEmailNotification(template, options);
				}	

			}
		}
		}
		//Check if the POCDEX Identity Profile
		String dateofInflow = (String) identity.getAttribute("iamDateofInflow");
		{
			if(dateofInflow!=null && isDateGreaterLessThenEqualToToday(context,dateofInflow,"dd-MM-yyyy","EQUAL") )
			{
				String agencyCode = (String) identity.getAttribute("iamPresentAgency");
				String to = getAgencyAdministratorEmail(context,agencyCode);
				
	            Map mailArgs = new HashMap();
	            mailArgs.put("startDate",dateofInflow);
	            mailArgs.put("agencyName",getAgencyNameFromCode(agencyCode));
	            mailArgs.put("department",identity.getAttribute("iamDepartmentName"));
	            mailArgs.put("title",identity.getAttribute("iamJobTitle"));
	            mailArgs.put("email",identity.getAttribute("email"));
	            EmailOptions options = new EmailOptions(to, mailArgs);
				context.sendEmailNotification(template, options);
				
			}
			
		}
		}
		System.out.println("Exit sendEmailToJoinerAgencyAdmin");
		
		
	}
}
