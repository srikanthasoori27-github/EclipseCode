	
import sailpoint.object.Idenitty;
import sailpoint.object.Identity;

import org.apache.commons.logging.Log;
	import org.apache.commons.logging.LogFactory;
  import sailpoint.tools.Util;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import sailpoint.object.Application;
import sailpoint.object.Link;
import sailpoint.tools.GeneralException;
public class departmentTransferLCE {

Identity newIdentity;
Identity previousIdentity;


public boolean depratmentLCE()
{
   //Log log = LogFactory.getLog("govtech.rule.identitytrigger.mover");
			
			 if (newIdentity == null || previousIdentity == null)
				return false;
			log.error("The newIdentity is "+newIdentity.toXml());
log.error("The previous idenitty is "+previousIdentity.toXml());
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
			Object newDepartmentID = newIdentity.getAttribute("iamDepartmentId");
		Object prevDepartmentID= previousIdentity.getAttribute("iamDepartmentId");
		
		
		Object newDepartmentName = newIdentity.getAttribute("iamDepartmentName");
		Object prevDepartmentName= previousIdentity.getAttribute("iamDepartmentName");
		
		Object newJobTitle = newIdentity.getAttribute("iamJobTitle");
		Object prevJobTitle= previousIdentity.getAttribute("iamJobTitle");
		log.error(" old and new jobtile "+prevJobTitle +"  "+newJobTitle);
			log.error(" old and new department id "+prevDepartmentID+ " "+newDepartmentID );
			log.error(" old and new department name "+prevDepartmentName+"  "+newDepartmentName);
		
			log.error(" Job title change "+ !Util.nullSafeEq(newJobTitle, prevJobTitle,true));
			log.error(" Dparment name change ? "+ !Util.nullSafeEq(prevDepartmentName, newDepartmentName,true));
			log.error(" department id change ? "+!Util.nullSafeEq(newDepartmentID, prevDepartmentID,true));
			
		if(!Util.nullSafeEq(newJobTitle, prevJobTitle,true) || !Util.nullSafeEq(prevDepartmentName, newDepartmentName,true) || !Util.nullSafeEq(newDepartmentID, prevDepartmentID,true))
		{
			
			return true;
		}

Application application = context.getObjectByName(Application.class, "Govtech POCDEX Target Application");
		List links = (List) newIdentity.getLinks(application);
		List<Link> prevLinksList = (List) previousIdentity.getLinks(application);
log.error("The links are "+links);
log.error("The previous links are "+prevLinksList);
List departmentChangeAccountsList = new ArrayList();
		if (links != null && links.size() > 0) {
			for (Link currentLink : links) {
				//Get the prevLink for the identity and compare the values with current link.
				//If there is a change then trigger the department transfer. 
				Link prevLink = previousIdentity.getLink(currentLink.getId());
				log.error("The prevLink is "+prevLink);	
              if(prevLink==null)
					continue;
				String nativeIdentity = currentLink.getNativeIdentity();
				String newTargetPOSID = (String) currentLink.getAttribute("New Position ID");
				String prevTargetPOSID = (String) prevLink.getAttribute("New Position ID");
				String dateofOutflow = (String) currentLink.getAttribute("Position End Date");	
				log.error("current Link  NEW POSID "+newTargetPOSID);
        log.error("prev link NEW POSID "+prevTargetPOSID);
        log.error("current Link POS END DATE "+dateofOutflow);
       // log.error(" !newTargetPOSID.equals(prevTargetPOSID)  "+!newTargetPOSID.equals(prevTargetPOSID));	
              if (newTargetPOSID != null && dateofOutflow == null) {
						if (prevTargetPOSID == null|| ( prevTargetPOSID != null && !newTargetPOSID.equals(prevTargetPOSID))) {
							log.error("Returing the true from loop2" + newTargetPOSID+ " previous positionid " + prevTargetPOSID);
							departmentChangeAccountsList.add(currentLink);
						}
					}
				// check for department id, departname or job title changes
				else
				{
					Object newTargetDepartmentID = currentLink.getAttribute("Department ID");
					Object prevTargetDepartmentID= prevLink.getAttribute("Department ID");
					Object newTargetDepartmentName = currentLink.getAttribute("Department Name");
					Object prevTargetDepartmentName= prevLink.getAttribute("Department Name");
					Object newTargetJobTitle = currentLink.getAttribute("Title");
					Object prevTargetJobTitle= prevLink.getAttribute("Title");
					
					if(!Util.nullSafeEq(newTargetDepartmentID, prevTargetDepartmentID,true) || !Util.nullSafeEq(newTargetDepartmentName, prevTargetDepartmentName,true) || !Util.nullSafeEq(newTargetJobTitle, prevTargetJobTitle))
					{
					
						departmentChangeAccountsList.add(currentLink);
					}
				}

			}
		}
		if (departmentChangeAccountsList != null && departmentChangeAccountsList.size() > 0)
			return true;
			return false;
}
}
