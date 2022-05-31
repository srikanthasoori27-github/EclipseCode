/*
Copyright © 2018 SailPoint Technologies, Inc. All Rights Reserved.
All logos, text, content, and works of authorship, including but not limited to underlying code, programming or scripting language, designs, and/or graphics,
that are used and/or depicted herein are protected under United States and international copyright and trademark laws and treaties,
and may not be used or reproduced without the prior express written permission of SailPoint Technologies, Inc.
 */
package sailpoint.rapidapponboarding.rule;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.IdentityService;
import sailpoint.api.SailPointContext;
import sailpoint.object.Application;
import sailpoint.object.Attributes;
import sailpoint.object.Bundle;
import sailpoint.object.Filter;
import sailpoint.object.GroupDefinition;
import sailpoint.object.Identity;
import sailpoint.object.IdentitySelector;
import sailpoint.object.Link;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.ProvisioningPlan.AccountRequest;
import sailpoint.object.ProvisioningPlan.AttributeRequest;
import sailpoint.object.QueryOptions;
import sailpoint.object.Schema;
import sailpoint.object.Workflow;
import sailpoint.rapidapponboarding.logger.LogEnablement;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
/**
 * Joiner Lifecycle Events
 * @author rohit.gupta
 *
 */
public class JoinerRuleLibrary {
	private static final String JOINERPOPREGEX = "joinerPopulationRegex";
	private static final String JOINERROLES = "joinerRoles";
	private static final String JOINERTOKEN = "#IIQJoiner#";
	private static final String JOINERENABLED = "joinerEnabled";
	public static final String JOINERROLEBIRTHRIGHT = "isBirthright";
	public static final String JOINERENTBIRTHRIGHT = "isBirthright";
	private static final Object JOINERROLETYPEBIRTHRIGHT = "birthright";
	public static final String JOINERPROCESS = "joinerProcess";
	public static final String JOINERRTWLTDPROCESS = "rtwltdProcess";
	public static final String JOINERRTWLOAPROCESS = "rtwloaProcess";
	public static final String JOINERREHIREPROCESS = "rehireProcess";
	public static final String JOINERFEATURE = "JOINER FEATURE";
	public static final String JOINERREHIREFEATURE = "JOINER REHIRE FEATURE";
	public static final String JOINERLOAFEATURE = "JOINER LOA FEATURE";
	public static final String JOINERLTDFEATURE = "JOINER LTD FEATURE";
	public static final Object SENDEMAILOPERATIONSRLTD = "apSendEmailToOperationsRLTD";
	public static final Object SENDEMAILOPERATIONSRLOA = "apSendEmailToOperationsRLOA";
	public static final String JOINERFILTERDETECTEDROLES="apFilterDetectedRoles";
	public static final String JOINERATTRNEEDSJOINER = "needsJoiner";
	public static final String JOINERNEEDPROCESSING = "NEEDS PROCESSING";
	public static final String JOINERJOINERPROCESSED = "JOINER PROCESSED";
	private static Log joinerLogger = LogFactory.getLog("rapidapponboarding.rules");
	/**
	 * Joiner LifeCycleEvent
	 * @param context
	 * @param previousIdentity
	 * @param newIdentity
	 * @param personaEnabled
	 * @return
	 * @throws Exception
	 */
	public static boolean isEligibleForJoiner(SailPointContext context,
			sailpoint.object.Identity previousIdentity,
			sailpoint.object.Identity newIdentity) throws Exception {
		String identityName=null;
		if(newIdentity!=null)
		{
			identityName=newIdentity.getName();
		}
		LogEnablement.isLogDebugEnabled(joinerLogger,"Enter isEligibleForJoiner.."+identityName);
		boolean flag = false;
		//Make sure it is not joiner for existing identities
		//Someone may come back with no birthright account and needsJoiner flag was never processed
		if (isEligibleForRehire( context,previousIdentity,newIdentity))
		{
			//Do not Kick off Joiner
			LogEnablement.isLogDebugEnabled(joinerLogger,"...Skipping Joiner, Detected Rehire Condition = " + flag+"..identityName.."+identityName);
			return flag;
		}
		// Persona Package
		String personaEnabled=WrapperRuleLibrary.isPersonaEnabled(context);
		if (personaEnabled != null && personaEnabled.equalsIgnoreCase("TRUE")) {
			boolean valid=false;
			valid=WrapperRuleLibrary.validateTriggersBeforePersona( context, newIdentity,
					previousIdentity,  JoinerRuleLibrary.JOINERPROCESS);
			if (!valid) {
				return valid;
			}
			// All New Active Persona's
			flag = checkAllNewRelationshipsPersona(context, newIdentity,
					previousIdentity);
			if (flag) {
				return flag;
			}
			// Addition of New Persona
			flag = WrapperRuleLibrary.checkIsNewRelationshipPersona(context,
					newIdentity, previousIdentity);
			LogEnablement.isLogDebugEnabled(joinerLogger,"...New Relationship = " + flag+"..identityName.."+identityName);
			if (flag) {
				return flag;
			}
		}
		// New Cube
		flag = checkIsNewIdentity(context, newIdentity, previousIdentity);
		LogEnablement.isLogDebugEnabled(joinerLogger,"...Brand  New = " + flag+"..identityName.."+identityName);
		if (flag) {
			return flag;
		}
		// HR Events
		flag = TriggersRuleLibrary.allowedForProcess(context,newIdentity,
				previousIdentity, JoinerRuleLibrary.JOINERPROCESS, JoinerRuleLibrary.JOINERFEATURE, "");
		LogEnablement.isLogDebugEnabled(joinerLogger,"...HR Event = " + flag+"..identityName.."+identityName);
		if (flag) {
			return flag;
		}
		return flag;
	}
	/**
	 * Rehire LifeCycleEvent
	 * Mainly used for Employee to Contractor or Contractor to Employee Conversion
	 * @param context
	 * @param previousIdentity
	 * @param newIdentity
	 * @return
	 * @throws Exception
	 */
	public static boolean isEligibleForRehire(SailPointContext context,
			sailpoint.object.Identity previousIdentity,
			sailpoint.object.Identity newIdentity)
					throws Exception {
		LogEnablement.isLogDebugEnabled(joinerLogger,"Enter isEligibleForRehire");
		String identityName=null;
		if(newIdentity!=null)
		{
			identityName=newIdentity.getName();
		}
		boolean flag = false;
		//Persona Package
		String personaEnabled=WrapperRuleLibrary.isPersonaEnabled(context);
		if (personaEnabled != null && personaEnabled.equalsIgnoreCase("TRUE")) {
			boolean valid=false;
			valid=WrapperRuleLibrary.validateTriggersBeforePersona( context, newIdentity,
					previousIdentity,  JoinerRuleLibrary.JOINERREHIREPROCESS);
			if (!valid) {
				return valid;
			}
			//All New Active Persona's
			flag = checkAllNewRelationshipsPersona(context, newIdentity,
					previousIdentity);
			if (flag) {
				return flag;
			}
			// Addition of New Persona
			flag = WrapperRuleLibrary.checkIsNewRelationshipPersona(context,
					newIdentity, previousIdentity);
			LogEnablement.isLogDebugEnabled(joinerLogger,"...New Relationship = " + flag+"..identityName.."+identityName);
			if (flag) {
				return flag;
			}
		}
		//HR Events
		flag = TriggersRuleLibrary
				.allowedForProcess(context,newIdentity, previousIdentity,
						JoinerRuleLibrary.JOINERREHIREPROCESS, JoinerRuleLibrary.JOINERREHIREFEATURE, "");
		LogEnablement.isLogDebugEnabled(joinerLogger,"Exit isEligibleForRehire = " + flag+"..identityName.."+identityName);
		return flag;
	}
	/**
	 * Is New Identity
	 * @param context
	 * @param newIdentity
	 * @param previousIdentity
	 * @return
	 * @throws GeneralException
	 */
	private static boolean checkIsNewIdentity(SailPointContext context,
			Identity newIdentity, Identity previousIdentity)
					throws GeneralException {
		String identityName=null;
		if(newIdentity!=null)
		{
			identityName=newIdentity.getName();
		}
		LogEnablement.isLogDebugEnabled(joinerLogger,"Enter checkIsNewIdentity.."+identityName);
		boolean flag = false;
		if (newIdentity != null && previousIdentity == null) {
			flag = true;
		}
		LogEnablement.isLogDebugEnabled(joinerLogger,"Exit checkIsNewIdentity..."+identityName);
		return flag;
	}
	/**
	 * All New Persona LifeCycleEvent
	 * @param context
	 * @param newIdentity
	 * @param previousIdentity
	 * @return
	 * @throws GeneralException
	 */
	private static boolean checkAllNewRelationshipsPersona(SailPointContext context,
			Identity newIdentity, Identity previousIdentity)
					throws GeneralException {
		String identityName=null;
		if(newIdentity!=null)
		{
			identityName=newIdentity.getName();
		}
		LogEnablement.isLogDebugEnabled(joinerLogger,"Enter JoinerRuleLibrary::checkAllNewRelationshipsPersona.."+identityName);
		boolean flag = false;
		Object newRelationShips=null;
		if (newIdentity!=null)
		{
			newRelationShips=newIdentity.getAttribute(WrapperRuleLibrary.PERSONARELATIONSHIPS);
		}
		Object prevRelationShips=null;
		if (previousIdentity!=null)
		{
			prevRelationShips=previousIdentity.getAttribute(WrapperRuleLibrary.PERSONARELATIONSHIPS);
		}
		// previousIdentity==null ||prevRelationShips==null is not happening anymore.
		// This could be because of identity mapping application and global rules. Let's keep in case of direct mappings
		if ((newIdentity!=null && newRelationShips!=null
				&& newRelationShips instanceof List
				&& ((List)newRelationShips).size()>0)
				&& (previousIdentity == null||prevRelationShips==null)) {
			LogEnablement.isLogDebugEnabled(joinerLogger,"...NewIdentity and no PreviousIdentity.."+identityName);
			List<String> differentRelationships = WrapperRuleLibrary.getRelationshipChangesPersona(context,newIdentity,
					previousIdentity, false);
			if (differentRelationships != null
					&& !(differentRelationships.isEmpty())) {
				int differenceCount = 0;
				for (String eachDifference : differentRelationships) {
					if (eachDifference.startsWith(WrapperRuleLibrary.PERSONAADD)) {
						differenceCount += 1;
					}
				}
				if (differenceCount == ((List) newIdentity
						.getAttribute(WrapperRuleLibrary.PERSONARELATIONSHIPS)).size()) {
					flag = true;
				}
			}
		}
		LogEnablement.isLogDebugEnabled(joinerLogger,"Exit JoinerRuleLibrary:: checkAllNewRelationshipsPersona "+flag+"..identityName.."+identityName);
		return flag;
	}
	/**
	 * RTW LTD LifeCycleEvent
	 * @param context
	 * @param previousIdentity
	 * @param newIdentity
	 * @return
	 * @throws Exception
	 */
	public static boolean isEligibleForRTWLTD(SailPointContext context,
			Identity previousIdentity, Identity newIdentity) throws Exception {
		LogEnablement.isLogDebugEnabled(joinerLogger,"Enter LeaverRuleLibrary::isEligibleForRTWLTD");
		String identityName=null;
		if(newIdentity!=null)
		{
			identityName=newIdentity.getName();
		}
		boolean flag = false;
		flag = TriggersRuleLibrary
				.allowedForProcess(context,newIdentity, previousIdentity,
						JoinerRuleLibrary.JOINERRTWLTDPROCESS, JoinerRuleLibrary.JOINERLTDFEATURE, "");
		LogEnablement.isLogDebugEnabled(joinerLogger,"Exit LeaverRuleLibrary::isEligibleForRTWLTD = " + flag+"..identityName.."+identityName);
		return flag;
	}
	/**
	 * RTW LOA LifeCycleEvent
	 * @param context
	 * @param previousIdentity
	 * @param newIdentity
	 * @return
	 * @throws Exception
	 */
	public static boolean isEligibleForRTWLOA(SailPointContext context,
			Identity previousIdentity, Identity newIdentity) throws Exception {
		LogEnablement.isLogDebugEnabled(joinerLogger,"Enter LeaverRuleLibrary::isEligibleForRTWLOA");
		String identityName=null;
		if(newIdentity!=null)
		{
			identityName=newIdentity.getName();
		}
		boolean flag = false;
		flag = TriggersRuleLibrary
				.allowedForProcess(context,newIdentity, previousIdentity,
						JoinerRuleLibrary.JOINERRTWLOAPROCESS, JoinerRuleLibrary.JOINERLOAFEATURE, "");
		LogEnablement.isLogDebugEnabled(joinerLogger,"Exit LeaverRuleLibrary::isEligibleForRTWLOA = " + flag+"..identityName.."+identityName);
		return flag;
	}
	/**
	 * Get All Joiner Roles of an Identity for an application and
	 * exclude detected joiner roles which already exists on an Identity
	 * @param context
	 * @param identity
	 * @param applicationName
	 * @return
	 * @throws GeneralException
	 */
	public static List getRolesForJoinerApplication(SailPointContext context, Identity identity, String  applicationName) throws GeneralException
	{
		LogEnablement.isLogDebugEnabled(joinerLogger,"Enter getRolesForJoinerApplication");
		IdentityService idService = new IdentityService(context);
		List joinerRolesEvaluated = new ArrayList();
		//Default Filtering On
		boolean filter=true;
		Map map = ROADUtil.getCustomGlobalMap(context);
		if(map!=null && map.containsKey(JoinerRuleLibrary.JOINERFILTERDETECTEDROLES) && map.get(JoinerRuleLibrary.JOINERFILTERDETECTEDROLES)!=null && ((String)map.get(JoinerRuleLibrary.JOINERFILTERDETECTEDROLES)).length()>0)
		{
			String filterExistingRoles=(String) map.get(JoinerRuleLibrary.JOINERFILTERDETECTEDROLES);
			if(filterExistingRoles!=null && filterExistingRoles.equalsIgnoreCase("TRUE"))
			{
				//Default Custom Mapping True
				filter=true;
			}
			else if(filterExistingRoles!=null && filterExistingRoles.equalsIgnoreCase("FALSE"))
			{
				//From Custom Mapping, It Can be Disabled for Multiple Accounts/Persona Use Cases
				filter=false;
			}
			LogEnablement.isLogDebugEnabled(joinerLogger,"filterExistingRoles "+filterExistingRoles);
		}
		LogEnablement.isLogDebugEnabled(joinerLogger,"filter "+filter);
		List<String> existingDetectedBirthrightRoles = new ArrayList();
		Identity newIdentity=null;
		if (identity!=null)
		{
			String name = identity.getName();
			newIdentity=context.getObjectByName(Identity.class, name);
		}
		if(identity!=null && filter)
		{
			//we have seen lazy init errors here specially around reverse leaver
			List<Bundle> detectedRoles=newIdentity.getDetectedRoles();
			if(detectedRoles!=null && detectedRoles.size()>0)
			{
				for(Bundle detctedRole:detectedRoles)
				{
					existingDetectedBirthrightRoles.add(detctedRole.getName());
				}
				LogEnablement.isLogDebugEnabled(joinerLogger,"existingDetectedBirthrightRoles-> "+existingDetectedBirthrightRoles);
			}
		}
		QueryOptions qo = new QueryOptions();
		if (newIdentity != null && applicationName!=null)
		{
			Application application = context.getObjectByName(Application.class,applicationName);
			if (application!=null)
			{
				Attributes attrs=application.getAttributes();
				if(attrs!=null && attrs.containsKey(JoinerRuleLibrary.JOINERROLES) )
				{
					String joinerRoles= (String) application.getAttributeValue(JoinerRuleLibrary.JOINERROLES);
					String joinerPopulationRegex = (String) application.getAttributeValue(JoinerRuleLibrary.JOINERPOPREGEX);
					List matchedIdentityPopulations = getListofMatchedIdentityPopulations( context, joinerPopulationRegex,  identity);
					LogEnablement.isLogDebugEnabled(joinerLogger,"matchedIdentityPopulations->"+matchedIdentityPopulations);
					if(joinerRoles!=null && joinerRoles.length()>0 && matchedIdentityPopulations!=null)
					{
						List<String> listJoinerRoles=Util.csvToList(joinerRoles);
						for(String roleName:listJoinerRoles)
						{
							if(isRoleBirthright(context, roleName,matchedIdentityPopulations,joinerPopulationRegex))
							{
								if(existingDetectedBirthrightRoles==null || existingDetectedBirthrightRoles.size()<=0
										|| !existingDetectedBirthrightRoles.contains(roleName))
								{
									joinerRolesEvaluated.add(roleName);
								}
							}
						}
					}
				}
				context.decache(application);
			}
		}
		if (newIdentity != null)
		{
			context.decache(newIdentity);
		}
		LogEnablement.isLogDebugEnabled(joinerLogger,"joinerRolesEvaluated --->"+joinerRolesEvaluated);
		LogEnablement.isLogDebugEnabled(joinerLogger,"Exit getRolesForJoinerApplication");
		return joinerRolesEvaluated;
	}
	/**
	 * Get All Joiner Roles to remove from an Identity for an application
	 * @param context
	 * @param identity
	 * @param applicationName
	 * @return
	 * @throws GeneralException
	 */
	public static List getRemoveRolesForJoinerApplication(SailPointContext context, Identity identity, String  applicationName) throws GeneralException
	{
		LogEnablement.isLogDebugEnabled(joinerLogger,"Enter getRemoveRolesForJoinerApplication");
		IdentityService idService = new IdentityService(context);
		List joinerRolesRemEvaluated = new ArrayList();
		List<String> existingDetectedBirthrightRoles = new ArrayList();
		Identity newIdentity=null;
		if(identity!=null)
		{
			//we have seen lazy init errors here specially around reverse leaver
			String name = identity.getName();
			newIdentity=context.getObjectByName(Identity.class, name);
			List<Bundle> detectedRoles=newIdentity.getDetectedRoles();
			if(detectedRoles!=null && detectedRoles.size()>0)
			{
				for(Bundle detctedRole:detectedRoles)
				{
					if(detctedRole.getType()!=null && detctedRole.getType().equalsIgnoreCase((String) JoinerRuleLibrary.JOINERROLETYPEBIRTHRIGHT))
						existingDetectedBirthrightRoles.add(detctedRole.getName());
				}
				LogEnablement.isLogDebugEnabled(joinerLogger,"existingDetectedBirthrightRoles ->"+existingDetectedBirthrightRoles);
			}
		}
		QueryOptions qo = new QueryOptions();
		if (newIdentity != null && applicationName!=null && existingDetectedBirthrightRoles!=null && existingDetectedBirthrightRoles.size()>0)
		{
			Application application = context.getObjectByName(Application.class,applicationName);
			if (application!=null)
			{
				Attributes attrs=application.getAttributes();
				LogEnablement.isLogDebugEnabled(joinerLogger,"application name ->"+application.getName());
				String joinerRoles=null;
				String joinerPopulationRegexorPop=null;
				List matchedIdentityPopulations=null;
				List<String> listJoinerRoles=null;
				if(attrs!=null && attrs.containsKey(JoinerRuleLibrary.JOINERPOPREGEX) )
				{
					joinerPopulationRegexorPop = (String) application.getAttributeValue(JoinerRuleLibrary.JOINERPOPREGEX);
					matchedIdentityPopulations = getListofMatchedIdentityPopulations( context, joinerPopulationRegexorPop,  identity);
				}
				//Use Case 1: Some Joiner Roles and Population/Regex Defined and matched identity population existing roles are birthright roles
				if(attrs!=null && attrs.containsKey(JoinerRuleLibrary.JOINERROLES) )
				{
					joinerRoles= (String) application.getAttributeValue(JoinerRuleLibrary.JOINERROLES);
					LogEnablement.isLogDebugEnabled(joinerLogger,"matchedIdentityPopulations ->"+matchedIdentityPopulations);
					if(joinerRoles!=null && joinerRoles.length()>0 && matchedIdentityPopulations!=null)
					{
						listJoinerRoles=Util.csvToList(joinerRoles);
						LogEnablement.isLogDebugEnabled(joinerLogger,"listJoinerRoles ->"+listJoinerRoles);
						if(listJoinerRoles!=null && listJoinerRoles.size()>0)
						{
							//Make Sure Regex / Population is Defined
							if(joinerPopulationRegexorPop!=null && joinerPopulationRegexorPop.length()>0)
							{
								LogEnablement.isLogDebugEnabled(joinerLogger,"joinerPopulationRegex ->"+joinerPopulationRegexorPop);
								LogEnablement.isLogDebugEnabled(joinerLogger,"matchedIdentityPopulations ->"+matchedIdentityPopulations);
								//There could be existing RBAC Roles - Make sure it is a birthright role and doesn't match Identity Population
								// If it is a Joiner Regex - No need to match with Identity Population
								if(joinerPopulationRegexorPop!=null && joinerPopulationRegexorPop.contains(JoinerRuleLibrary.JOINERTOKEN))
								{
									for(String existingDetectedBirthrightRole:existingDetectedBirthrightRoles)
									{
										//Defined List of Roles is Authoritative Source
										if(!listJoinerRoles.contains(existingDetectedBirthrightRole) && isRoleBirthright(context, existingDetectedBirthrightRole) &&
												matchRoleAppName(context, existingDetectedBirthrightRole,application.getName()))
										{
											LogEnablement.isLogDebugEnabled(joinerLogger,"Remove Roles No Java Regex Match-> Detected Existing Birthright Roles Needs to be Removed -  Doesn't Match with Defined Roles -> "+application.getName());
											joinerRolesRemEvaluated.add(existingDetectedBirthrightRole);
										}
									}
								}
								else if (joinerPopulationRegexorPop!=null && joinerPopulationRegexorPop.length()>0 && matchedIdentityPopulations!=null )
								{
									for(String existingDetectedBirthrightRole:existingDetectedBirthrightRoles)
									{
										//Defined Population is Authoritative Source and Population is subtracted
										LogEnablement.isLogDebugEnabled(joinerLogger,"matchedIdentityPopulations.."+matchedIdentityPopulations);
										if(isRoleBirthright(context, existingDetectedBirthrightRole) &&
												matchRoleAppName(context, existingDetectedBirthrightRole,application.getName()) &&
												matchedIdentityPopulations.size()>0 && !matchRoleAndIdentityPopulation(context, existingDetectedBirthrightRole, matchedIdentityPopulations, joinerPopulationRegexorPop) )
										{
											LogEnablement.isLogDebugEnabled(joinerLogger,"Remove Roles Population subtracted-> Detected Existing Birthright Roles Needs to be Removed -   Doesn't Match with Defined Roles and their Population -> "+application.getName());
											joinerRolesRemEvaluated.add(existingDetectedBirthrightRole);
										}
										//Defined Population is Authoritative Source and No Population On Identity Detected
										else if(isRoleBirthright(context, existingDetectedBirthrightRole) &&
												matchRoleAppName(context, existingDetectedBirthrightRole,application.getName()) && matchedIdentityPopulations.size()==0)
										{
											LogEnablement.isLogDebugEnabled(joinerLogger,"Remove Roles No Population On Identity-> Detected Existing Birthright Roles Needs to be Removed -   Doesn't Match with Defined Roles and their Population -> "+application.getName());
											joinerRolesRemEvaluated.add(existingDetectedBirthrightRole);
										}
									}
								}
							}
							//Joiner Population or Regex is removed
							else
							{
								LogEnablement.isLogDebugEnabled(joinerLogger,"No Joiner Population and Regex Defined ->");
								//There could be existing RBAC Roles - Make sure it is a birthright role and doesn't match Identity Population
								for(String existingDetectedBirthrightRole:existingDetectedBirthrightRoles)
								{
									//Defined List of Roles is Authoritative Source
									if(!listJoinerRoles.contains(existingDetectedBirthrightRole) && isRoleBirthright(context, existingDetectedBirthrightRole) &&
											matchRoleAppName(context, existingDetectedBirthrightRole,application.getName()))
									{
										LogEnablement.isLogDebugEnabled(joinerLogger,"Remove Roles -> Detected Existing Roles Birthright and doesn't have Population/Regex defined");
										joinerRolesRemEvaluated.add(existingDetectedBirthrightRole);
									}
								}
							}
						}
					}
				}
				context.decache(application);
			}
		}
		if (newIdentity != null)
		{
			context.decache(newIdentity);
		}
		LogEnablement.isLogDebugEnabled(joinerLogger,"Exit getRemoveRolesForJoinerApplication.."+joinerRolesRemEvaluated);
		return joinerRolesRemEvaluated;
	}
	/**
	 * Match Role App Name
	 * @param context
	 * @param bundleName
	 * @param appName
	 * @return
	 * @throws GeneralException
	 */
	public static boolean matchRoleAppName (SailPointContext context, String bundleName, String appName) throws GeneralException
	{
		boolean result=false;
		LogEnablement.isLogDebugEnabled(joinerLogger,"Enter matchRoleAppName");
		LogEnablement.isLogDebugEnabled(joinerLogger,"Enter matchRoleAppName.bundleName."+bundleName);
		LogEnablement.isLogDebugEnabled(joinerLogger,"Enter matchRoleAppName.appName."+appName);
		if(appName!=null)
		{
			Bundle joinerBundle=null;
			if(bundleName!=null)
			{
				joinerBundle=context.getObjectByName(Bundle.class,bundleName);
			}
			if(joinerBundle!=null)
			{
				Set<Application> appObjs= joinerBundle.getApplications();
				if (appObjs != null && !appObjs.isEmpty())
				{
					for (Application appObj : appObjs)
					{
						if(appObj.getName()!=null && appObj.getName().equalsIgnoreCase(appName))
						{
							LogEnablement.isLogDebugEnabled(joinerLogger,"Matched matchRoleAppName.."+appObj.getName());
							result=true;
						}
					}
				}
			}
			if(joinerBundle!=null  )
			{
				context.decache(joinerBundle);
			}
		}
		LogEnablement.isLogDebugEnabled(joinerLogger,"Exit matchRoleAppName.."+result);
		return result;
	}
	/**
	 * Matches Bundle Population with Identity's Populations
	 * @param context
	 * @param bundleName
	 * @param matchedIdentityPopulations
	 * @param joinerPopulationRegex
	 * @return
	 * @throws GeneralException
	 */
	public static boolean matchRoleAndIdentityPopulation (SailPointContext context,String bundleName, List matchedIdentityPopulations, String joinerPopulationRegex) throws GeneralException
	{
		boolean result=false;
		LogEnablement.isLogDebugEnabled(joinerLogger,"Enter matchRoleAndIdentityPopulation");
		if(matchedIdentityPopulations!=null && matchedIdentityPopulations.size()>0)
		{
			//In case, population is defined on the application, make sure role population matches with identity population defined on the application
			Bundle joinerBundle=context.getObjectByName(Bundle.class,bundleName);
			if(joinerBundle!=null && joinerBundle.getSelector()!=null)
			{
				IdentitySelector idSelector = joinerBundle.getSelector();
				if(idSelector.getPopulation()!=null)
				{
					GroupDefinition rolePopulation=idSelector.getPopulation();
					String popName=rolePopulation.getName();
					if(matchedIdentityPopulations.contains(popName))
					{
						LogEnablement.isLogDebugEnabled(joinerLogger," Matched Role and Identity Population "+popName+" role name "+bundleName);
						result=true;
					}
				}
			}
			if(joinerBundle!=null)
			{
				context.decache(joinerBundle);
			}
		}
		LogEnablement.isLogDebugEnabled(joinerLogger,"Exit matchRoleAndIdentityPopulation.."+result);
		return result;
	}
	/**
	 * Is Bundle Birthright and Matches with Identity's Populations
	 * @param context
	 * @param bundleName
	 * @param matchedIdentityPopulations
	 * @param joinerPopulationRegex
	 * @return
	 * @throws GeneralException
	 */
	public static boolean isRoleBirthright (SailPointContext context,String bundleName, List matchedIdentityPopulations, String joinerPopulationRegex) throws GeneralException
	{
		boolean result=false;
		LogEnablement.isLogDebugEnabled(joinerLogger,"Enter isRoleBirthright");
		QueryOptions qo = new QueryOptions();
		Filter filter = Filter.and((Filter.ignoreCase(Filter.eq(JoinerRuleLibrary.JOINERROLEBIRTHRIGHT, "TRUE"))),Filter.eq("name",bundleName), Filter.eq("type",JoinerRuleLibrary.JOINERROLETYPEBIRTHRIGHT),Filter.eq("disabled",false));
		qo.addFilter(filter);
		// Use a projection query first to return minimal data.
		ArrayList returnCols = new ArrayList();
		returnCols.add("name");
		Bundle joinerBundle=null;
		// Execute the query against the IdentityIQ database.
		Iterator it = context.search(Bundle.class, qo, returnCols);
		if(it!=null)
		{
			while (it.hasNext())
			{
				Object[] retObjs = (Object[]) it.next();
				if(retObjs!=null && retObjs.length==1)
				{
					String bundleNameSer   = (String) retObjs[0];
					if(bundleNameSer!=null)
					{
						if(matchedIdentityPopulations!=null && matchedIdentityPopulations.size()>0)
						{
							//In case, population is defined on the application, make sure role population matches with identity population defined on the application
							joinerBundle=context.getObjectByName(Bundle.class,bundleNameSer);
							if(joinerBundle!=null && joinerBundle.getSelector()!=null)
							{
								IdentitySelector idSelector = joinerBundle.getSelector();
								if(idSelector.getPopulation()!=null)
								{
									GroupDefinition rolePopulation=idSelector.getPopulation();
									String popName=rolePopulation.getName();
									if(matchedIdentityPopulations.contains(popName))
									{
										result=true;
									}
								}
							}
							if(joinerBundle!=null)
							{
								context.decache(joinerBundle);
							}
						}
						else if(joinerPopulationRegex!=null && joinerPopulationRegex.contains(JoinerRuleLibrary.JOINERTOKEN))
						{
							//In case, there are no populations defined and there is just a regular expression, just return all birthright roles for an application
							result=true;
						}
					}
				}
			}
			Util.flushIterator(it);
		}
		LogEnablement.isLogDebugEnabled(joinerLogger,"Exit isRoleBirthright");
		return result;
	}
	/**
	 * Is Bundle Birthright
	 * @param context
	 * @param bundleName
	 * @return
	 * @throws GeneralException
	 */
	public static boolean isRoleBirthright (SailPointContext context,String bundleName) throws GeneralException
	{
		boolean result=false;
		LogEnablement.isLogDebugEnabled(joinerLogger,"Enter isRoleBirthright");
		QueryOptions qo = new QueryOptions();
		Filter filter = Filter.and((Filter.ignoreCase(Filter.eq(JoinerRuleLibrary.JOINERROLEBIRTHRIGHT, "TRUE"))),Filter.eq("name",bundleName), Filter.eq("type",JoinerRuleLibrary.JOINERROLETYPEBIRTHRIGHT),Filter.eq("disabled",false));
		qo.addFilter(filter);
		// Use a projection query first to return minimal data.
		ArrayList returnCols = new ArrayList();
		returnCols.add("name");
		Bundle joinerBundle=null;
		// Execute the query against the IdentityIQ database.
		Iterator it = context.search(Bundle.class, qo, returnCols);
		if(it!=null)
		{
			while (it.hasNext())
			{
				Object[] retObjs = (Object[]) it.next();
				if(retObjs!=null && retObjs.length==1)
				{
					String bundleNameSer   = (String) retObjs[0];
					if(bundleNameSer!=null)
					{
						result=true;
					}
				}
			}
			Util.flushIterator(it);
		}
		LogEnablement.isLogDebugEnabled(joinerLogger,"Exit isRoleBirthright->"+bundleName+" result "+result);
		return result;
	}
	/**
	 * Get List of Populations defined on Application that matches with Identity Population
	 * @param context
	 * @param joinerRegularAttrExpression
	 * @param identity
	 * @return
	 * @throws GeneralException
	 */
	public static List getListofMatchedIdentityPopulations(SailPointContext context, String joinerRegularAttrExpression, Identity identity) throws GeneralException
	{
		LogEnablement.isLogDebugEnabled(joinerLogger,"Enter getListofMatchedIdentityPopulations");
		List matchedIdentityPopulations = new ArrayList();
		if(joinerRegularAttrExpression!=null)
		{
			if(joinerRegularAttrExpression!= null && !joinerRegularAttrExpression.contains(JoinerRuleLibrary.JOINERTOKEN))
			{
				List<String> populations=Util.csvToList(joinerRegularAttrExpression);
				if(populations!=null && populations.size()>0)
				{
					for(String population:populations)
					{
						int result = matchPopulation(context,identity,population);
						if(result>0)
						{
							matchedIdentityPopulations.add(population);
						}
					}
				}
			}
		}
		LogEnablement.isLogDebugEnabled(joinerLogger,"Exit getListofMatchedIdentityPopulations");
		return matchedIdentityPopulations;
	}
	/**
	 * Match Population
	 * @param context
	 * @param identity
	 * @param populationName
	 * @return
	 * @throws GeneralException
	 */
	public static int matchPopulation(SailPointContext context, Identity identity, String populationName) throws GeneralException
	{
		LogEnablement.isLogDebugEnabled(joinerLogger,"Enter matchPopulation.."+populationName);
		int count=0;
		QueryOptions ops = new QueryOptions();
		GroupDefinition groupDefinition=context.getObjectByName(GroupDefinition.class, populationName);
		if(groupDefinition!=null)
		{
			Filter filterGd = groupDefinition.getFilter();
			if(filterGd!=null)
			{
				Filter combo = Filter.and(Filter.eq("id", identity.getId()),filterGd);
				ops.add(combo);
				count = context.countObjects(Identity.class, ops);
			}
			context.decache(groupDefinition);
		}
		LogEnablement.isLogDebugEnabled(joinerLogger,"Exit matchPopulation->"+count+".."+populationName);
		return count;
	}
	/**
	 * Get All Joiner Applications
	 * @param context
	 * @return
	 * @throws GeneralException
	 */
	public static List getAllJoinerApplications (SailPointContext context) throws GeneralException
	{
		LogEnablement.isLogDebugEnabled(joinerLogger,"Enter getAllJoinerApplications");
		List applicationIds= new ArrayList();
		QueryOptions qo = new QueryOptions();
		Filter filter = Filter.ignoreCase(Filter.eq(JoinerRuleLibrary.JOINERENABLED, "TRUE"));
		qo.addFilter(filter);
		// Use a projection query first to return minimal data.
		ArrayList returnCols = new ArrayList();
		returnCols.add("id");
		// Execute the query against the IdentityIQ database.
		Iterator it = context.search(Application.class, qo, returnCols);
		if(it!=null)
		{
			while (it.hasNext())
			{
				Object[] retObjs = (Object[]) it.next();
				if(retObjs!=null && retObjs.length==1)
				{
					if(retObjs[0]!=null)
					{
						applicationIds.add(retObjs[0]);
					}
				}
			}
			Util.flushIterator(it);
		}
		LogEnablement.isLogDebugEnabled(joinerLogger,"Exit getAllJoinerApplications->"+applicationIds);
		return applicationIds;
	}
	/**
	 * Get All Joiner Application Names
	 * @param context
	 * @return
	 * @throws GeneralException
	 */
	public static List getAllJoinerApplicationNames(SailPointContext context) throws GeneralException
	{
		LogEnablement.isLogDebugEnabled(joinerLogger,"Enter getAllJoinerApplicationNames");
		List applicationIds= new ArrayList();
		QueryOptions qo = new QueryOptions();
		Filter filter = Filter.ignoreCase(Filter.eq(JoinerRuleLibrary.JOINERENABLED, "TRUE"));
		qo.addFilter(filter);
		// Use a projection query first to return minimal data.
		ArrayList returnCols = new ArrayList();
		returnCols.add("name");
		// Execute the query against the IdentityIQ database.
		Iterator it = context.search(Application.class, qo, returnCols);
		if(it!=null)
		{
			while (it.hasNext())
			{
				Object[] retObjs = (Object[]) it.next();
				if(retObjs!=null && retObjs.length==1)
				{
					if(retObjs[0]!=null)
					{
						applicationIds.add(retObjs[0]);
					}
				}
			}
			Util.flushIterator(it);
		}
		LogEnablement.isLogDebugEnabled(joinerLogger,"Exit getAllJoinerApplicationNames->"+applicationIds);
		return applicationIds;
	}
	/**
	 * Get All Joiner Applications for an Identity
	 * Match Population of an Identity with Application Defined Joiner Populations
	 * @param context
	 * @param identity
	 * @return
	 * @throws GeneralException
	 */
	public static List getApplicationsForJoiner(SailPointContext context,Identity identity) throws GeneralException
	{
		LogEnablement.isLogDebugEnabled(joinerLogger,"Enter getApplicationsForJoiner");
		List applicationsForJoiner = new ArrayList();
		if (identity != null)
		{
			//joinerEnabled Flag True (allApps)
			List<String> allAppIds = getAllJoinerApplications(context);
			for (String appId: allAppIds)
			{
				Application application=context.getObjectById(Application.class,appId);
				Attributes attrs=application.getAttributes();
				if(attrs!=null)
				{
					String joinerPopulationRegex=(String) application.getAttributeValue(JoinerRuleLibrary.JOINERPOPREGEX);
					//Lets see if there is a regex for population identification, if yes, use regex to populate joiner list, otherwise joinerEnabled flag is enough
					if(joinerPopulationRegex!=null)
					{
						if( isPopulationMatched(context,joinerPopulationRegex,identity)!=null )
						{
							applicationsForJoiner.add(application.getName() );
						}
					}
					else
					{
						applicationsForJoiner.add(application.getName());
					}
				}
				if(application!=null)
				{
					context.decache(application);
				}
			}
		}
		LogEnablement.isLogDebugEnabled(joinerLogger,"Exit getApplicationsForJoiner->"+applicationsForJoiner);
		return applicationsForJoiner;
	}
	/**
	 * Match Identity Population with Any Application Population or use
	 * Java Regular Expression To Match
	 * @param joinerRegularAttrExpression
	 * @param identity
	 * @return
	 * @throws GeneralException
	 */
	public static String isPopulationMatched(SailPointContext context, String joinerRegularAttrExpression, Identity identity) throws GeneralException
	{
		LogEnablement.isLogDebugEnabled(joinerLogger,"Enter isPopulationMatched");
		String cubeAttrVal=null;
		if(joinerRegularAttrExpression!=null)
		{
			String[] joinerRegularAttrExpressionArr = joinerRegularAttrExpression.split(JoinerRuleLibrary.JOINERTOKEN);
			if(joinerRegularAttrExpressionArr != null && joinerRegularAttrExpressionArr.length == 2 && Util.isNotNullOrEmpty(joinerRegularAttrExpressionArr[0]) && Util.isNotNullOrEmpty(joinerRegularAttrExpressionArr[1]))
			{
				String cubeAttr=joinerRegularAttrExpressionArr[0];
				String regex=joinerRegularAttrExpressionArr[1];
				cubeAttrVal = (String) identity.getAttribute(cubeAttr);
				if(ROADUtil.executeRegex(regex,cubeAttrVal)>=1)
				{
					return cubeAttrVal;
				}
				else
				{
					return null;
				}
			}
			else if(joinerRegularAttrExpression != null)
			{
				List<String> populations=Util.csvToList(joinerRegularAttrExpression);
				if(populations!=null && populations.size()>0)
				{
					for(String population:populations)
					{
						int result = matchPopulation(context,identity,population);
						if(result>0)
						{
							cubeAttrVal= "Population Matched";
							break;
						}
					}
				}
			}
		}
		LogEnablement.isLogDebugEnabled(joinerLogger,"Exit isPopulationMatched->"+cubeAttrVal);
		return cubeAttrVal;
	}
	/**
	 * Build Joiner Roles Account Request
	 * @param identityName
	 * @param roleName
	 * @return
	 */
	public static List addToProvisioningPlanIIQ(String identityName, String roleName)
	{
		LogEnablement.isLogDebugEnabled(joinerLogger,"Enter addToProvisioningPlanIIQ");
		List allRequests = new ArrayList();
		AccountRequest.Operation op = AccountRequest.Operation.Modify;
		Attributes attrs = new Attributes();
		attrs.put("assignment", "true");
		AccountRequest acctReq = new AccountRequest(op, ProvisioningPlan.APP_IIQ, null, identityName);
		AttributeRequest attrReq = new AttributeRequest(ProvisioningPlan.ATT_IIQ_ASSIGNED_ROLES, ProvisioningPlan.Operation.Add, roleName);
		attrReq.setArguments(attrs);
		acctReq.add(attrReq);
		allRequests.add(acctReq);
		LogEnablement.isLogDebugEnabled(joinerLogger,"End addToProvisioningPlanIIQ");
		return allRequests;
	}
	/**
	 * Build Joiner Roles Remove Account Request
	 * @param identityName
	 * @param roleName
	 * @return
	 */
	public static List removeToProvisioningPlanIIQ(String identityName, String roleName)
	{
		LogEnablement.isLogDebugEnabled(joinerLogger,"Enter removeToProvisioningPlanIIQ");
		List allRequests = new ArrayList();
		AccountRequest.Operation op = AccountRequest.Operation.Modify;
		Attributes attrs = new Attributes();
		attrs.put("assignment", "true");
		AccountRequest acctReq = new AccountRequest(op, ProvisioningPlan.APP_IIQ, null, identityName);
		AttributeRequest attrReq = new AttributeRequest(ProvisioningPlan.ATT_IIQ_ASSIGNED_ROLES, ProvisioningPlan.Operation.Remove, roleName);
		attrReq.setArguments(attrs);
		acctReq.add(attrReq);
		allRequests.add(acctReq);
		LogEnablement.isLogDebugEnabled(joinerLogger,"End removeToProvisioningPlanIIQ");
		return allRequests;
	}
	/**
	 * Restore Birthright Access For Reverse Leaver Options
	 * @param context
	 * @param identity
	 * @param appName
	 * @return
	 * @throws GeneralException
	 */
	public static List restoreBirthrightBundles(SailPointContext context, Identity identity, String appName) throws GeneralException
	{
		LogEnablement.isLogDebugEnabled(joinerLogger,"Enter restoreBirthrightBundles");
		List<String> joinerRolesApplication = new ArrayList();
		List<String> joinerRolesReMApplication = new ArrayList();
		List accountRequestList = new ArrayList();
		//Lets check if we have Joiner Roles first
		joinerRolesApplication = getRolesForJoinerApplication(context,identity,appName);
		if(joinerRolesApplication!=null && joinerRolesApplication.size()>0)
		{
			for(String roleName:joinerRolesApplication )
			{
				List roleRequest = addToProvisioningPlanIIQ(identity.getName(), roleName);
				if(roleRequest!=null && roleRequest.size()>0)
				{
					accountRequestList.addAll(roleRequest);
				}
			}
		}
		//Lets check if we have Joiner Roles for Removal
		joinerRolesReMApplication = getRemoveRolesForJoinerApplication(context,identity,appName);
		if(joinerRolesReMApplication!=null && joinerRolesReMApplication.size()>0)
		{
			for(String roleRemName:joinerRolesReMApplication )
			{
				List roleRequest = removeToProvisioningPlanIIQ(identity.getName(), roleRemName);
				if(roleRequest!=null && roleRequest.size()>0)
				{
					accountRequestList.addAll(roleRequest);
				}
			}
		}
		LogEnablement.isLogDebugEnabled(joinerLogger,"Exit restoreBirthrightBundles--> "+accountRequestList);
		return accountRequestList;
	}
	/**
	 * Build Provisioning Plan Per Application Link
	 * @param appName
	 * @param identity
	 * @return
	 */
	public static List createNewAccountProvisioningPlan(SailPointContext context, String appName, Identity identity) throws Exception{
		LogEnablement.isLogDebugEnabled(joinerLogger,"Enter createNewAccountProvisioningPlan");
		List allRequests = new ArrayList();
		IdentityService idService = new IdentityService(context);
		String nativeId = "";
		Application app = context.getObjectByName(Application.class, appName);
		AccountRequest acctReq = null;
		if (idService.countLinks(identity, app) == 0)
		{
			nativeId = ROADUtil.getNativeIdentity(context, appName, identity);
			if (nativeId != null && !nativeId.equals(""))
			{
				acctReq = new AccountRequest(AccountRequest.Operation.Create, appName, null, nativeId);
				allRequests.add(acctReq);
			} else {
			    acctReq = new AccountRequest(AccountRequest.Operation.Create, appName, null, null);
                allRequests.add(acctReq);
			}
		}
		if(app!=null)
		{
			context.decache(app);
		}
		LogEnablement.isLogDebugEnabled(joinerLogger,"End createNewAccountProvisioningPlan");
		return allRequests;
	}
	/**
	 * Build Provisioning Plan Per Application Link
	 * for Non-Employee, and Service Account Repo's from Create and Edit
	 * QuickLinks
	 * @param appName
	 * @param identity
	 * @return
	 */
	public static List createOrUpdateNewAccountProvisioningPlanExternalRepo(SailPointContext context, String appName, Identity identity) throws Exception{
		LogEnablement.isLogDebugEnabled(joinerLogger,"Enter createOrUpdateNewAccountProvisioningPlanExternalRepo");
		List allRequests = new ArrayList();
		IdentityService idService = new IdentityService(context);
		String nativeId = "";
		Application app = context.getObjectByName(Application.class, appName);
		AccountRequest acctReq = null;
		if (idService.countLinks(identity, app) == 0)
		{
			nativeId = ROADUtil.getNativeIdentity(context, appName, identity);
			if (Util.isNotNullOrEmpty(nativeId))
			{
				acctReq = new AccountRequest(AccountRequest.Operation.Create, appName, null, nativeId);
				allRequests.add(acctReq);
			} else {
	            acctReq = new AccountRequest(AccountRequest.Operation.Create, appName, null, null);
	            allRequests.add(acctReq);
			}
		}
		else
		{
			List<Link> list = idService.getLinks(identity, app);
			if (list != null && list.size() > 0)
			{
				for (Link link:list)
				{
					if(link!=null)
					{
						nativeId = link.getNativeIdentity();
						if (nativeId != null && !nativeId.equals(""))
						{
							acctReq = new AccountRequest(AccountRequest.Operation.Modify, appName, null, nativeId);
							/**
							 * Let's build attribute request here
							 */
							Schema schema = app.getAccountSchema();
							List<String> attrNames=schema.getAttributeNames();
							if(attrNames!=null && attrNames.size()>0)
							{
								for(String attrName:attrNames)
								{
									Object attrValueFromPP=ROADUtil.getFieldValueFromProvisioningForms( context,  app.getName(), identity, attrName,"Update",null);
									if(attrValueFromPP!=null)
									{
										Object valueFromLink=link.getAttribute(attrName);
										if(AttributeSyncRuleLibrary.isAttributeValueChanged(context, identity, null, attrName, attrName,nativeId,valueFromLink,attrValueFromPP,true))
										{
											AttributeRequest attrReq = new AttributeRequest();
											attrReq.setOp(ProvisioningPlan.Operation.Set);
											attrReq.setName(attrName);
											attrReq.setValue(attrValueFromPP);
											acctReq.add(attrReq);
										}
									}
								}
							}
							if(acctReq!=null && acctReq.getAttributeRequests()!=null && acctReq.getAttributeRequests().size()>0)
							{
								allRequests.add(acctReq);
							}
						}
					}
				}
			}
		}
		if(app!=null)
		{
			context.decache(app);
		}
		LogEnablement.isLogDebugEnabled(joinerLogger,"End createOrUpdateNewAccountProvisioningPlanExternalRepo");
		return allRequests;
	}
	/**
	 * Build Joiner Provisioning Plan
	 * @param context
	 * @param identityName
	 * @param workflow
	 * @param appName
	 * @return
	 */
	public static ProvisioningPlan buildJoinerPlan(SailPointContext context, String identityName, Workflow workflow, String appName) throws Exception
	{
		LogEnablement.isLogDebugEnabled(joinerLogger,"Enter buildJoinerPlan");
		Identity identity = context.getObjectByName(Identity.class, identityName);
		ProvisioningPlan plan = null;
		LogEnablement.isLogDebugEnabled(joinerLogger,"...Start building ProvisioningPlan");
		List<String> applicationsForJoiner = new ArrayList();
		applicationsForJoiner = JoinerRuleLibrary.getApplicationsForJoiner(context,identity);
		// ITERATE THROUGH EACH APP AND THEN CREATE ACCOUNTREQUEST
		if (applicationsForJoiner != null && !(applicationsForJoiner.isEmpty()))
		{
			plan = new ProvisioningPlan();
			if (identity != null)
			{
				plan.setIdentity(identity);
				for (String singleApp : applicationsForJoiner)
				{
					if(appName==null || (singleApp!=null && appName!=null && singleApp.equalsIgnoreCase(appName)))
					{
						List<String> joinerRolesApplication = new ArrayList();
						List<String> joinerRolesReMApplication = new ArrayList();
						List<AccountRequest> accountRequest = new ArrayList();
						List<AccountRequest> accountRemRequest = new ArrayList();
						//Lets check if we have Add Joiner Roles first
						joinerRolesApplication = JoinerRuleLibrary.getRolesForJoinerApplication(context,identity,singleApp);
						joinerRolesReMApplication = JoinerRuleLibrary.getRemoveRolesForJoinerApplication(context,identity,singleApp);
						if(joinerRolesApplication!=null && joinerRolesApplication.size()>0)
						{
							for(String roleName:joinerRolesApplication )
							{
								accountRequest = JoinerRuleLibrary.addToProvisioningPlanIIQ(identityName, roleName);
								if (!(accountRequest.isEmpty() && plan != null))
								{
									for (AccountRequest acctReq : accountRequest)
									{
										plan.add(acctReq);
									}
								}
							}
						}
						//There are no joiner roles, let's just build account
						else
						{
							accountRequest = JoinerRuleLibrary.createNewAccountProvisioningPlan(context,singleApp,identity);
							if (!(accountRequest.isEmpty() && plan != null))
							{
								for (AccountRequest acctReq : accountRequest)
								{
									plan.add(acctReq);
								}
							}
						}
						//Let's see if we have to remove joiner roles
						if(joinerRolesReMApplication!=null && joinerRolesReMApplication.size()>0)
						{
							for(String roleName:joinerRolesReMApplication )
							{
								accountRemRequest = JoinerRuleLibrary.removeToProvisioningPlanIIQ(identity.getName(), roleName);
								if (!(accountRemRequest.isEmpty() && plan != null))
								{
									for (AccountRequest acctRemReq : accountRemRequest)
									{
										plan.add(acctRemReq);
									}
								}
							}
						}
					}
				}
			}
		}
		if(identity!=null)
		{
			context.decache(identity);
		}
		if (null != plan && plan.getAllRequests() == null) {
			plan = null;
		}
		LogEnablement.isLogDebugEnabled(joinerLogger,"Exit buildJoinerPlan");
		return plan;
	}

	/**
	 * Build Joiner Mover Rehire Provisioning Plan
	 * @param context
	 * @param identityName
	 * @param workflow
	 * @param appName
	 * @return
	 */
	public static ProvisioningPlan buildJoinerMoverPlan(SailPointContext context, String identityName, Workflow workflow, String appName) throws Exception
	{
		LogEnablement.isLogDebugEnabled(joinerLogger,"Enter buildJoinerMoverPlan");
		Identity identity = context.getObjectByName(Identity.class, identityName);
		ProvisioningPlan plan = null;
		IdentityService idService = new IdentityService(context);
		int countLinks=0;
		countLinks=idService.countLinks(identity);
		LogEnablement.isLogDebugEnabled(joinerLogger,"...Start building ProvisioningPlan for Joiner After Mover");
		LogEnablement.isLogDebugEnabled(joinerLogger,"...countLinks.."+countLinks);
		List<String> applicationsForJoiner = new ArrayList();
		applicationsForJoiner = JoinerRuleLibrary.getApplicationsForJoiner(context,identity);
		LogEnablement.isLogDebugEnabled(joinerLogger,"...applicationsForJoiner pop match and app enabled..."+applicationsForJoiner);
		List joinerEnabledApps=getAllJoinerApplicationNames(context);
		LogEnablement.isLogDebugEnabled(joinerLogger,"...joinerEnabledApps..."+joinerEnabledApps);
		// ITERATE THROUGH EACH APP AND THEN CREATE ACCOUNTREQUEST
		List<Link> links;
		if (countLinks>0 && identity!=null && applicationsForJoiner!=null)
		{
			links = idService.getLinks(identity,0,0);
			for (Link link : links)
			{
				String linkAppName=link.getApplicationName();
				LogEnablement.isLogDebugEnabled(joinerLogger,"...linkAppName..."+linkAppName);
				if (!applicationsForJoiner.contains(linkAppName))
				{
					//Add link applicatons to joinerEnabled flag applications,
					//someone may come back in different position (contractor to Employee or Employee To Contractor)
					//or population defined may not be eligible after mover
					LogEnablement.isLogDebugEnabled(joinerLogger,"...linkAppName...is not calculated as part of population matches");
					if(linkAppName!=null && joinerEnabledApps!=null && joinerEnabledApps.contains(linkAppName))
					{
						LogEnablement.isLogDebugEnabled(joinerLogger,"...Add linkAppName..."+linkAppName);
						applicationsForJoiner.add(link.getApplicationName());
					}
				}
			}
		}
		LogEnablement.isLogDebugEnabled(joinerLogger,"...applicationsForJoiner..."+applicationsForJoiner);
		if (applicationsForJoiner != null && !(applicationsForJoiner.isEmpty()))
		{
			plan = new ProvisioningPlan();
			if (identity != null)
			{
				plan.setIdentity(identity);
				for (String singleApp : applicationsForJoiner)
				{
					if(appName==null || (singleApp!=null && appName!=null && singleApp.equalsIgnoreCase(appName)))
					{
						List<String> joinerRolesApplication = new ArrayList();
						List<String> joinerRolesReMApplication = new ArrayList();
						List<AccountRequest> accountRequest = new ArrayList();
						List<AccountRequest> accountRemRequest = new ArrayList();
						//Lets check if we have Add Joiner Roles first
						joinerRolesApplication = JoinerRuleLibrary.getRolesForJoinerApplication(context,identity,singleApp);
						joinerRolesReMApplication = JoinerRuleLibrary.getRemoveRolesForJoinerApplication(context,identity,singleApp);
						if(joinerRolesApplication!=null && joinerRolesApplication.size()>0)
						{
							for(String roleName:joinerRolesApplication )
							{
								accountRequest = JoinerRuleLibrary.addToProvisioningPlanIIQ(identityName, roleName);
								if (!(accountRequest.isEmpty() && plan != null))
								{
									for (AccountRequest acctReq : accountRequest)
									{
										plan.add(acctReq);
									}
								}
							}
						}
						//There are no joiner roles, let's just build account
						else
						{
							accountRequest = JoinerRuleLibrary.createNewAccountProvisioningPlan(context,singleApp,identity);
							if (!(accountRequest.isEmpty() && plan != null))
							{
								for (AccountRequest acctReq : accountRequest)
								{
									plan.add(acctReq);
								}
							}
						}
						//Let's see if we have to remove joiner roles
						if(joinerRolesReMApplication!=null && joinerRolesReMApplication.size()>0)
						{
							for(String roleName:joinerRolesReMApplication )
							{
								accountRemRequest = JoinerRuleLibrary.removeToProvisioningPlanIIQ(identity.getName(), roleName);
								if (!(accountRemRequest.isEmpty() && plan != null))
								{
									for (AccountRequest acctRemReq : accountRemRequest)
									{
										plan.add(acctRemReq);
									}
								}
							}
						}
					}
				}
			}
		}
		if(identity!=null)
		{
			context.decache(identity);
		}
		if (null != plan && plan.getAllRequests() == null) {
			plan = null;
		}
		LogEnablement.isLogDebugEnabled(joinerLogger,"Exit buildJoinerMoverRehirePlan");
		return plan;
	}
	/**
	 * Set Joiner Identity Attribute "needsProcessing" value "JOINER PROCESSED"
	 * @param context
	 * @param identityName
	 * @param message
	 * @throws GeneralException
	 */
	public static void markJoiner(SailPointContext context, String identityName, String message) throws GeneralException
	{
		LogEnablement.isLogDebugEnabled(joinerLogger,"Enter markJoiner=" );
		List<Map> triggerStatus = TriggersRuleLibrary.getCustomTriggers(context,TriggersRuleLibrary.AUTHORITATIVE_SOURCE_TRIGGERS, JoinerRuleLibrary.JOINERPROCESS);
		String attributeName=null;
		String joinerDisabled=ROADUtil.roadAttributeDisabled(context, "Identity", JoinerRuleLibrary.JOINERATTRNEEDSJOINER);
		boolean joinerEnabled=false;
		//IMPLICIT JOINER EVENT IS ENABLED
		if(joinerDisabled!=null && joinerDisabled.length()>0 && joinerDisabled.equalsIgnoreCase("FALSE"))
		{
			joinerEnabled=true;
		}
		if (triggerStatus != null && triggerStatus.size() > 0 && joinerEnabled)
		{
			// ITERATE THROUGH EACH MAP THERE CAN BE AN AND/OR OPERATIONs
			for (Map singleMap : triggerStatus)
			{
				attributeName=(String) singleMap.get("Attribute");
				LogEnablement.isLogDebugEnabled(joinerLogger,"attributeName="+attributeName );
				if(attributeName!=null && attributeName.equalsIgnoreCase(JoinerRuleLibrary.JOINERATTRNEEDSJOINER) && joinerEnabled)
				{
					Identity identity= context.getObjectByName(Identity.class, identityName);
					ProvisioningPlan plan = new ProvisioningPlan();
					plan.setIdentity(identity);
					AccountRequest acctReq = new AccountRequest();
					acctReq.setApplication(ProvisioningPlan.APP_IIQ);
					acctReq.setNativeIdentity(identityName);
					acctReq.setOperation(ProvisioningPlan.AccountRequest.Operation.Modify);
					AttributeRequest attrReq = new AttributeRequest();
					attrReq.setName(attributeName);
					attrReq.setOperation(ProvisioningPlan.Operation.Set);
					attrReq.setValue(message);
					if (acctReq != null)
					{
						acctReq.add(attrReq);
					}
					plan.add(acctReq);
					LogEnablement.isLogDebugEnabled(joinerLogger,"Launch Internal IIQ Plan" );
					ROADUtil.launchProvisionerPlan(plan,context);
					break;
				}
			}
		}
		LogEnablement.isLogDebugEnabled(joinerLogger,"End markJoiner" );
	}
}
