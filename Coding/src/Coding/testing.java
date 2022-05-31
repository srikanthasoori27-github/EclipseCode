package Coding;

import java.awt.List;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;

import sailpoint.integration.Util;
import sailpoint.object.Application;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.Link;
import sailpoint.object.QueryOptions;
import sailpoint.object.Idenitty;
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
import sailpoint.api.SailPointContext;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import sailpoint.api.SailPointContext;
import sailpoint.api.TaskManager;
import sailpoint.object.Application;
import sailpoint.object.Attributes;
import sailpoint.object.Custom;
import sailpoint.object.EmailOptions;
import sailpoint.object.EmailTemplate;
import sailpoint.object.TaskDefinition;
import sailpoint.object.TaskResult;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

public class testing {
	
	Identity newIdentity;
	Identity previousIdentity;
	SailPointContext context;
	public static void main(String args[])
	{
		List parentlist = new ArrayList();
		List terminationList = new ArrayList();
		List deletionList = new ArrayList();
		
		parentlist.add("agency1");
		parentlist.add("agency2");
		parentlist.add("agency3");
		parentlist.add("agency4");
		parentlist.add("agency5");
		
		Identity newIdentity;
		Identity previousIdentity;
		
		Object newDepartmentID = newIdentity.getAttribute("iamDepartmentId");
		Object prevDepartmentID= previousIdentity.getAttribute("iamDepartmentId");
		
		
		Object newDepartmentName = newIdentity.getAttribute("iamDepartmentName");
		Object prevDepartmentName= previousIdentity.getAttribute("iamDepartmentName");
		
		Object newJobTitle = newIdentity.getAttribute("iamJobTitle");
		Object prevJobTitle= previousIdentity.getAttribute("iamJobTitle");
		
		if(!Util.nullSafeEq(newJobTitle, prevJobTitle) || !Util.nullSafeEq(prevDepartmentName, newDepartmentName) || !Util.nullSafeEq(newDepartmentID, prevDepartmentID))
		{
			log.error(" old and new jobtile "+prevJobTitle +"  "+newJobTitle);
			log.error(" old and new department id "+prevDepartmentID+ " "+newDepartmentID );
			log.error(" old and new department name "+prevDepartmentName+"  "+newDepartmentName);
		
			log.error(" Job title change "+ !Util.nullSafeEq(newJobTitle, prevJobTitle));
			log.error(" Dparment name change ? "+ !Util.nullSafeEq(prevDepartmentName, newDepartmentName));
			log.error(" department id change ? "+!Util.nullSafeEq(newDepartmentID, prevDepartmentID));
			
			return true;
		}
		
		
		
		Application application = context.getObjectByName(Application.class, "Govtech POCDEX Target Application");
		List links = (List) newIdentity.getLinks(application);
		List<Link> prevLinksList = (List) previousIdentity.getLinks(application);
		List departmentChangeAccountsList = new ArrayList();
		if (links != null && links.size() > 0) {
			for (Link currentLink : links) {
				//Get the prevLink for the identity and compare the values with current link.
				//If there is a change then trigger the department transfer. 
				Link prevLink = previousIdentity.getLink(currentLink.getId());
				if(prevLink==null)
					continue;
				String nativeIdentity = currentLink.getNativeIdentity();
				String newTargetPOSID = (String) currentLink.getAttribute("New Position ID");
				String prevTargetPOSID = (String) prevLink.getAttribute("New Position ID");
				String dateofOutflow = (String) currentLink.getAttribute("Position End Date");	
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
		
	}
	
	
	public Object departmentLCE()
	{
		

	   //Log log = LogFactory.getLog("govtech.rule.identitytrigger.mover");
				
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
			Object newDepartmentID = newIdentity.getAttribute("iamDepartmentId");
			Object prevDepartmentID= previousIdentity.getAttribute("iamDepartmentId");
			
			
			Object newDepartmentName = newIdentity.getAttribute("iamDepartmentName");
			Object prevDepartmentName= previousIdentity.getAttribute("iamDepartmentName");
			
			Object newJobTitle = newIdentity.getAttribute("iamJobTitle");
			Object prevJobTitle= previousIdentity.getAttribute("iamJobTitle");
			
			if(!Util.nullSafeEq(newJobTitle, prevJobTitle,true) || !Util.nullSafeEq(prevDepartmentName, newDepartmentName,true) || !Util.nullSafeEq(newDepartmentID, prevDepartmentID,true))
			{
				log.error(" old and new jobtile "+prevJobTitle +"  "+newJobTitle);
				log.error(" old and new department id "+prevDepartmentID+ " "+newDepartmentID );
				log.error(" old and new department name "+prevDepartmentName+"  "+newDepartmentName);
			
				log.error(" Job title change "+ !Util.nullSafeEq(newJobTitle, prevJobTitle,true));
				log.error(" Dparment name change ? "+ !Util.nullSafeEq(prevDepartmentName, newDepartmentName,true));
				log.error(" department id change ? "+!Util.nullSafeEq(newDepartmentID, prevDepartmentID,true));
				
				return true;
			}

			Application application = context.getObjectByName(Application.class, "Govtech POCDEX Target Application");
			List links = (List) newIdentity.getLinks(application);
			List<Link> prevLinksList = (List) previousIdentity.getLinks(application);
			List departmentChangeAccountsList = new ArrayList();
			if (links != null && links.size() > 0) {
				for (Link currentLink : links) {
					//Get the prevLink for the identity and compare the values with current link.
					//If there is a change then trigger the department transfer. 
					Link prevLink = previousIdentity.getLink(currentLink.getId());
				
					if(prevLink==null)
						continue;
					String nativeIdentity = currentLink.getNativeIdentity();
					String newTargetPOSID = (String) currentLink.getAttribute("New Position ID");
					String prevTargetPOSID = (String) prevLink.getAttribute("New Position ID");
					String dateofOutflow = (String) currentLink.getAttribute("Position End Date");	
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
