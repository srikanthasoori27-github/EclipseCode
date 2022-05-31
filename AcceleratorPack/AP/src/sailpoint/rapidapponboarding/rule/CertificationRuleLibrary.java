/*
Copyright © 2018 SailPoint Technologies, Inc. All Rights Reserved.
All logos, text, content, and works of authorship, including but not limited to underlying code, programming or scripting language, designs, and/or graphics,
that are used and/or depicted herein are protected under United States and international copyright and trademark laws and treaties,
and may not be used or reproduced without the prior express written permission of SailPoint Technologies, Inc.
*/
package sailpoint.rapidapponboarding.rule;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import sailpoint.api.IdentityService;
import sailpoint.api.SailPointContext;
import sailpoint.object.AbstractCertifiableEntity;
import sailpoint.object.Application;
import sailpoint.object.Bundle;
import sailpoint.object.Certifiable;
import sailpoint.object.Custom;
import sailpoint.object.EntitlementGroup;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.QueryOptions;
import sailpoint.object.ManagedAttribute;
import sailpoint.object.RoleAssignment;
import sailpoint.rapidapponboarding.logger.LogEnablement;
import sailpoint.server.Servicer;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
/**
 * Used for All Certifications
 * @author rohit.gupta
 *
 */
public class CertificationRuleLibrary {
	private static Log certLogger = LogFactory.getLog("rapidapponboarding.rules");
	private static Custom globalInclusionExclusionCustom = null;
	private static final String CUSTOMGLOBALCERTNAME = "Custom-Framework-Exclusion-Logical-BusinessApplications-PrivilegedAccess";
	private static final Object LOGICALAPPLISTKEY = "LogicalBusinessApps";
	/**
	 * ForceLoad
	 **/
	public static Custom forceLoad(SailPointContext context) 
	{
		try {
			globalInclusionExclusionCustom = context.getObjectByName(Custom.class, CertificationRuleLibrary.CUSTOMGLOBALCERTNAME);
		}
		catch(Exception e) {
			LogEnablement.isLogErrorEnabled(certLogger,"Error while retrieving the global exclusion Custom object: " + e.getMessage());
		}
		LogEnablement.isLogDebugEnabled(certLogger,"Force Load Global Certification: ");
		return globalInclusionExclusionCustom;
	}
	/**
	 * Loads and returns a Custom object which holds the parameters for application and 
	 * entitlement/role exclusion. The object is only loaded once and kept in the CustomGlobal
	 * map for future executions of this rule. If the forceLoad parameter is true, the Custom
	 * object is forced to be loaded and added again
	 **/
	public static synchronized Custom loadCustomMapReload(SailPointContext context, boolean forceLoad) 
	{
		try {
			if(globalInclusionExclusionCustom == null || globalInclusionExclusionCustom.getAttributes()==null || globalInclusionExclusionCustom.getAttributes().isEmpty() || forceLoad ) 
			{
				globalInclusionExclusionCustom = context.getObjectByName(Custom.class, CertificationRuleLibrary.CUSTOMGLOBALCERTNAME);
			} else {
	            Date dbModified = Servicer.getModificationDate(context, globalInclusionExclusionCustom);
	            if (Util.nullSafeCompareTo(dbModified, globalInclusionExclusionCustom.getModified()) > 0) {
	                LogEnablement.isLogDebugEnabled(certLogger,"...Returning updated globalInclusionExclusionCustom object");
	                globalInclusionExclusionCustom = context.getObjectByName(Custom.class, CertificationRuleLibrary.CUSTOMGLOBALCERTNAME);
	            } else {
	                LogEnablement.isLogDebugEnabled(certLogger,"...Returning previously initialized globalInclusionExclusionCustom object");
	            }
	        }
		}
		catch(Exception e) {
			LogEnablement.isLogErrorEnabled(certLogger,"Error while retrieving the global exclusion Custom object: " + e.getMessage());
		}
		return globalInclusionExclusionCustom;
	}
	/**
	 * Loads and returns a Custom object which holds the parameters for application and 
	 * entitlement/role exclusion. The object is only loaded once and kept in the CustomGlobal
	 * map for future executions of this rule. If the forceLoad parameter is true, the Custom
	 * object is forced to be loaded and added again
	 **/
	public static Custom loadCustomMap(SailPointContext context, boolean forceLoad) 
	{
		try {
			//Make Sure Custom Artifact is Properly Loaded
			if(globalInclusionExclusionCustom != null && globalInclusionExclusionCustom.getAttributes()!=null &&
					!globalInclusionExclusionCustom.getAttributes().isEmpty()) 
			{
				LogEnablement.isLogDebugEnabled(certLogger,"Existing Certification Custom object: ");
				return globalInclusionExclusionCustom;
			}
			else
			{
				LogEnablement.isLogDebugEnabled(certLogger,"Load Once Certification Custom object: ");
				globalInclusionExclusionCustom = loadCustomMapReload( context,  forceLoad);
			}
		}
		catch(Exception e) {
			LogEnablement.isLogErrorEnabled(certLogger,"Error while retrieving the global exclusion Custom object: " + e.getMessage());
		}
		return globalInclusionExclusionCustom;
	}
	/**
	 * Returns a true or false if the Applications that should be ignored because it is Authoritative
	 * @param context
	 * @param appName
	 * @return
	 * @throws GeneralException
	 */
	public static boolean ignoreAuthoritativeApplication(SailPointContext context, String appName) throws GeneralException {
		Application app;
		boolean retVal = false;
		app = context.getObjectByName(Application.class, appName);
		if (app != null) {
			if (app.isAuthoritative()) {
				retVal = true;
			}
			else {
				retVal = false;
			}
			context.decache(app);
		}
		return retVal;
	}
	/**
	 * Returns a true or false if Bundle was requested via LCM
	 * @param identity
	 * @param bundleName
	 * @return
	 * @throws GeneralException
	 */
	public static boolean bundleWasRequested(Identity identity, String bundleName) throws GeneralException {	
		boolean retVal = false;
		if (identity.getRoleAssignments() != null) {
			List <RoleAssignment> identityRoleAssignments = identity.getRoleAssignments();
			if(identityRoleAssignments!=null)
			{
				for (RoleAssignment ra : identityRoleAssignments) {
					if ((ra.getSource().equalsIgnoreCase("LCM") || ra.getSource().equalsIgnoreCase("Batch") || ra.getSource().equalsIgnoreCase("UI")) && ra.getRoleName().equalsIgnoreCase(bundleName)) {
						retVal = true;
					}
				}
			}
		}
		return retVal;
	}
	/**
	 * Returns a true or false if Bundle was assigned
	 * @param identity
	 * @param bundleName
	 * @return
	 * @throws GeneralException
	 */
	public static boolean bundleWasAssigned(Identity identity, String bundleName) throws GeneralException {	
		boolean retVal = false;
		if (identity.getRoleAssignments() != null) {
			List <RoleAssignment> identityRoleAssignments = identity.getRoleAssignments();
			if(identityRoleAssignments!=null)
			{
				for (RoleAssignment ra : identityRoleAssignments) {
					if (ra.getSource().equalsIgnoreCase("Rule") && ra.getRoleName().equalsIgnoreCase(bundleName)) {
						retVal = true;
					}
				}
			}
		}
		return retVal;
	}
	/**
	 * * Exclude
	 * - Is Birthright set on Role or Entitlement
	 * - Role was automated
	 * - Role or Entitlement Doesn't Match Defined Business App List
	 * - Authoritative Application
	 * Include
	 * - Role or Entitlement Matches Defined Business App List
	 * - Role was requested
	 * - Accounts Only
	 * @param context
	 * @param certObj
	 * @param entity
	 * @param excludePrivilegedAccessAttr
	 * @return
	 * @throws GeneralException
	 */
	public static  boolean isRoleOrEntitlementExcluded(SailPointContext context,Certifiable certObj, AbstractCertifiableEntity entity, 
			String excludePrivilegedAccessAttr) throws GeneralException {
		String appName = "";
		String entName = "";
		String entValue = "";
		String isBirthright = "";
		String isPrivileged = "";
		String entAppName = "";
		String busApp=null;
		String nativeId=null;
		boolean privAccessEnabled=ObjectConfigAttributesRuleLibrary.extendedAttrPrivRoleEntEnabled(context);
		Custom globalExclusionMap = null;
		IdentityService service = new IdentityService(context);
		globalExclusionMap = CertificationRuleLibrary.loadCustomMap(context, false);
		Identity identity = (Identity) entity;
		String joinerDisabled=ObjectConfigAttributesRuleLibrary.extendedAttrJoinerBirthrightAppDisabled(context);
		boolean enabledJoiner=true;
		if(joinerDisabled!=null && joinerDisabled.length()>0 && joinerDisabled.equalsIgnoreCase("TRUE"))
		{
			enabledJoiner=false;
		}
		String businessAppEnabled=ObjectConfigAttributesRuleLibrary.extendedAttrBusAppEnabled(context);
		boolean enabledBusinessApp=false;
		if(businessAppEnabled!=null && businessAppEnabled.length()>0 && businessAppEnabled.equalsIgnoreCase("TRUE"))
		{
			enabledBusinessApp=true;
		}
		String logicalAppEnabled=ObjectConfigAttributesRuleLibrary.extendedAttrLogicalAppEnabled(context);
		boolean logApp=false;
		if(logicalAppEnabled!=null && logicalAppEnabled.length()>0 && logicalAppEnabled.equalsIgnoreCase("TRUE"))
		{
			logApp=true;
		}
		//Get a list of all the business applications to exclude
		List<String> logicalAppsList = new ArrayList();
		if(logApp && globalExclusionMap!=null && globalExclusionMap.getAttributes()!=null &&  globalExclusionMap.getAttributes().containsKey(CertificationRuleLibrary.LOGICALAPPLISTKEY) && globalExclusionMap.getAttributes().get(CertificationRuleLibrary.LOGICALAPPLISTKEY) instanceof List )
		{
		logicalAppsList=(List) globalExclusionMap.getAttributes().get(CertificationRuleLibrary.LOGICALAPPLISTKEY);
		}
		String excludePrivilegedAccess = (String) globalExclusionMap.getAttributes().get(excludePrivilegedAccessAttr);
		//TYPE IS ENTITLEMENT
		if(certObj instanceof EntitlementGroup) 
		{
			EntitlementGroup entGrp = (EntitlementGroup) certObj;
			appName = entGrp.getApplicationName();
			//If it is a business app, simply include it
			if(appName!=null && enabledBusinessApp)
			{
				QueryOptions qo = new QueryOptions();
				qo.addFilter(Filter.ignoreCase(Filter.eq("name", appName)));
				qo.addFilter(Filter.ignoreCase(Filter.eq(ObjectConfigAttributesRuleLibrary.BUSAPP, true)));
				List propsApp = new ArrayList();
				propsApp.add(ObjectConfigAttributesRuleLibrary.BUSAPP);
				Iterator iterApp = context.search(Application.class, qo, propsApp);
				if (iterApp != null)
				{
					try 
					{
						while (iterApp.hasNext()) 
						{
							Object[] rowApp = (Object[]) iterApp.next();
							if(rowApp!=null && rowApp.length==1)
							{
								busApp = (String)rowApp[0];
							}			            
						}
					}
					catch (Exception e) 
					{
						LogEnablement.isLogErrorEnabled(certLogger,"...Application Error "+e.getMessage());
					}
					Util.flushIterator(iterApp);
				}
			}
			//Obtain the entitlement name and its corresponding application
			if(!entGrp.isAccountOnly() && entGrp.getAttributes() != null) 
			{
				appName = entGrp.getApplicationName();
				entName = entGrp.getAttributeNames().get(0);
				entValue = (String) entGrp.getAttributes().get(entName);
				nativeId=entGrp.getNativeIdentity();
				QueryOptions qo = new QueryOptions();
				qo.addFilter(Filter.ignoreCase(Filter.eq("application.name", appName)));
				qo.addFilter(Filter.ignoreCase(Filter.eq("attribute", entName)));
				qo.addFilter(Filter.ignoreCase(Filter.eq("value", entValue)));
				List props = new ArrayList();
				if(logApp)
				{
					props.add(ObjectConfigAttributesRuleLibrary.LOGICALAPP);
				}
				if(enabledJoiner)
				{
					props.add(JoinerRuleLibrary.JOINERENTBIRTHRIGHT);
				}
				if(privAccessEnabled)
				{
					props.add(ObjectConfigAttributesRuleLibrary.ENTPRIVILEGED);
				}
				Iterator iter = context.search(ManagedAttribute.class, qo, props);
				if (iter != null)
				{
					try 
					{
						while (iter.hasNext()) 
						{
							Object[] rowEnt = (Object[]) iter.next();
							if(rowEnt.length==3 && logApp && enabledJoiner && privAccessEnabled )
							{
								entAppName = (String)rowEnt[0];
								isBirthright = (String)rowEnt[1];	
								isPrivileged = (String)rowEnt[2];	
							}
							if(rowEnt.length==2 && logApp && enabledJoiner)
							{
								entAppName = (String)rowEnt[0];
								isBirthright = (String)rowEnt[1];	
							}
							if(rowEnt.length==2 && enabledJoiner && privAccessEnabled)
							{
								isBirthright = (String)rowEnt[0];	
								isPrivileged = (String)rowEnt[1];	
							}
							else if(rowEnt.length==2 && logApp && privAccessEnabled)
							{
								entAppName = (String)rowEnt[0];
								isPrivileged = (String)rowEnt[1];	
							}
							else if(rowEnt.length==1  && logApp)
							{
								entAppName = (String)rowEnt[0];
							}
							else if(rowEnt.length==1  && enabledJoiner)
							{
								isBirthright = (String)rowEnt[0];	
							}
							else if(rowEnt.length==1  && privAccessEnabled)
							{
								isPrivileged = (String)rowEnt[0];	
							}
						}
					}
					catch (Exception e) 
					{
						LogEnablement.isLogErrorEnabled(certLogger,"...Managed Entitlement Error "+e.getMessage());
					}
					Util.flushIterator(iter);
				}
				LogEnablement.isLogDebugEnabled(certLogger,"...Managed Entitlement appName.. "+appName);
				LogEnablement.isLogDebugEnabled(certLogger,"...Managed Entitlement entName.. "+entName);
				LogEnablement.isLogDebugEnabled(certLogger,"...Managed Entitlement entValue.. "+entValue);
				LogEnablement.isLogDebugEnabled(certLogger,"...Managed Entitlement isPrivileged.. "+isPrivileged);
				LogEnablement.isLogDebugEnabled(certLogger,"...Managed Entitlement isBirthright.. "+isBirthright);
				LogEnablement.isLogDebugEnabled(certLogger,"...Managed Entitlement entAppName.. "+entAppName);
				List<String> entAppNameList=null;
				if(logApp && entAppName!=null && entAppName.contains(","))
				{
					entAppNameList=Util.csvToList(entAppName);    
				}
				//Exclude Privileged Entitlement if configured
				if(privAccessEnabled && excludePrivilegedAccess!=null && excludePrivilegedAccess.equalsIgnoreCase("TRUE") && isPrivileged!=null && isPrivileged.equalsIgnoreCase("TRUE"))
				{
					return true;
				}
				//Exclude Any Birthright Entitlement
				if (enabledJoiner && isBirthright != null && isBirthright.equalsIgnoreCase("true")) 
				{
					return true;
				}
				//Inlcude All Business Application Entitlement Access
				else if(enabledBusinessApp && (busApp!=null && busApp.equalsIgnoreCase("true")))
				{
					return false;
				}
				// Logical Application Not Defined means Application is not Onboarded
				else if (logApp && (entAppName == null || entAppName.equals("")))
				{
					return true;
				}
				else if (logApp && (entAppNameList != null && entAppNameList.size()>0 && null != logicalAppsList && logicalAppsList.size()>0)) 
				{
					for(String entAppNameStr: entAppNameList)
					{
						for(String busAppsStr: logicalAppsList)
						{
							if(entAppNameStr.equalsIgnoreCase(busAppsStr))
							{
								return true;
							}
						}
					}
				}
				else if (logApp && (entAppName != null && !entAppName.equals("") && null != logicalAppsList && logicalAppsList.size()>0 && logicalAppsList.contains(entAppName))) 
				{
					return true;
				}
				else if (logApp && (entAppName != null && !entAppName.equals("") && null != logicalAppsList && logicalAppsList.size()>0 && !logicalAppsList.contains(entAppName))) 
				{
					return false;
				}
			}
			else 
			{
				if (entGrp.getApplicationName() != null) 
				{
					 appName = entGrp.getApplicationName();
					 nativeId=entGrp.getNativeIdentity();
					 LogEnablement.isLogDebugEnabled(certLogger,"...Only Account  appName.. "+appName);
					 LogEnablement.isLogDebugEnabled(certLogger,"...Only Account nativeId.. "+nativeId);
					if (CertificationRuleLibrary.ignoreAuthoritativeApplication(context,appName)) 
					{
						return true;
					}
					//Exclude Privileged Account
					else if(privAccessEnabled && excludePrivilegedAccess!=null && excludePrivilegedAccess.equalsIgnoreCase("TRUE") && nativeId!=null && appName!=null )
					{
						if(ROADUtil.isAccountPrivilegedPerApp(context, appName, nativeId))
						{
							LogEnablement.isLogDebugEnabled(certLogger,"..Excluding Privileged Account.. "+nativeId);
							return true;
						}
					}
					//Include All Business Application  Access
					else if(enabledBusinessApp && busApp!=null && busApp.equalsIgnoreCase("true"))
					{
						return false;
					}
				}
			}		 
		} 
		//TYPE IS ROLE
		else if(certObj instanceof Bundle) 
		{
			Bundle bundleObj = (Bundle) certObj;
			Set<Application> setAppNames = bundleObj.getApplications();
			//If one of the application on the role is business application, just include access
			if(setAppNames!=null && !setAppNames.isEmpty() )
			{
				for(Application setValue: setAppNames) 
				{
					if(setValue!=null)
					{  
						Boolean busAppBoolean=(Boolean) setValue.getAttributeValue(ObjectConfigAttributesRuleLibrary.BUSAPP);
						if(busAppBoolean!=null && busAppBoolean.booleanValue())
						{
							busApp="true";
							break;	
						}
						else
						{
						}
					}
				}
			}
			else
			{
				List<Bundle> requiredBundles = bundleObj.getRequirements();
				if(requiredBundles!=null)
				{
					for (Bundle requiredBundle:requiredBundles)
					{
						Set<Application> setReqAppNames = requiredBundle.getApplications();
						if(setReqAppNames!=null && !setReqAppNames.isEmpty() )
						{
							for(Application setReqValue: setReqAppNames) 
							{
								if(setReqValue!=null)
								{  
									Boolean busAppBoolean=(Boolean) setReqValue.getAttributeValue(ObjectConfigAttributesRuleLibrary.BUSAPP);
									if(busAppBoolean!=null &&  busAppBoolean.booleanValue())
									{
										busApp="true";
										break;	
									}
								}
							}
						}
					}
				}
			}
			//Obtain the role name and its corresponding application
			entValue = bundleObj.getName();
			if(enabledJoiner)
			{
				isBirthright = (String) bundleObj.getAttribute(JoinerRuleLibrary.JOINERROLEBIRTHRIGHT);
			}
			else
			{
				isBirthright="";
			}
			if(privAccessEnabled)
			{
				isPrivileged = (String) bundleObj.getAttribute(ObjectConfigAttributesRuleLibrary.ROLEPRIVILEGED);
			}
			else
			{
				isPrivileged="";
			}
			String roleAppName = "";
			if(logApp)
			{
				roleAppName=(String) bundleObj.getAttribute(ObjectConfigAttributesRuleLibrary.LOGICALAPPROLE);
			}
			else
			{
				roleAppName="";
			}
			List<String> roleAppNameList=null;
			if(logApp && roleAppName!=null && roleAppName.length()>0 && roleAppName.contains(","))
			{
				roleAppNameList=Util.csvToList(roleAppName);    
			}
			//Exclude Privileged Entitlement if configured
			if(privAccessEnabled && excludePrivilegedAccess!=null && excludePrivilegedAccess.equalsIgnoreCase("TRUE") && isPrivileged!=null && isPrivileged.equalsIgnoreCase("TRUE"))
			{
				return true;
			}
			//Exclude Birthright Roles Logical and Business Application
			if (enabledJoiner && isBirthright != null && isBirthright.equalsIgnoreCase("true")) 
			{
				return true;
			}
			//Inlcude All Business Application Roles
			else if(enabledBusinessApp && busApp!=null && busApp.equalsIgnoreCase("true"))
			{
				return false;
			}
			else if (logApp && (roleAppName == null || roleAppName.equals("")))
			{
				return true;
			}
			else if (bundleWasAssigned(identity, bundleObj.getName())) 
			{
				return true;
			}
			else if (logApp && (roleAppNameList != null && roleAppNameList.size()>0 && null != logicalAppsList && logicalAppsList.size()>0)) 
			{
				for(String roleAppNameStr: roleAppNameList)
				{
					for(String busAppsStr: logicalAppsList)
					{
						if(roleAppNameStr.equalsIgnoreCase(busAppsStr))
						{
							return true;
						}
					}
				}
			}
			else if (logApp && (roleAppName != null && !roleAppName.equals("") && null != logicalAppsList && logicalAppsList.size()>0 && logicalAppsList.contains(roleAppName))) 
			{
				return true;
			}
			else if (logApp && (roleAppName != null && !roleAppName.equals("") && null != logicalAppsList && logicalAppsList.size()>0 && !logicalAppsList.contains(roleAppName))) 
			{
				return false;
			}
		}
		else 
		{
			return false;
		} 
		return false;
	}
}
