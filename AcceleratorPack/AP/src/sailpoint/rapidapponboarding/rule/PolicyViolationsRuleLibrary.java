/*
Copyright © 2018 SailPoint Technologies, Inc. All Rights Reserved.
All logos, text, content, and works of authorship, including but not limited to underlying code, programming or scripting language, designs, and/or graphics,
that are used and/or depicted herein are protected under United States and international copyright and trademark laws and treaties,
and may not be used or reproduced without the prior express written permission of SailPoint Technologies, Inc.
*/
package sailpoint.rapidapponboarding.rule;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
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
import sailpoint.object.Custom;
import sailpoint.object.Entitlement;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.Link;
import sailpoint.object.Policy;
import sailpoint.object.PolicyViolation;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.ProvisioningPlan.AccountRequest;
import sailpoint.object.ProvisioningPlan.AttributeRequest;
import sailpoint.object.QueryOptions;
import sailpoint.object.Workflow;
import sailpoint.rapidapponboarding.logger.LogEnablement;
import sailpoint.server.Servicer;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
/**
 * Policy Violation Feature
 * @author rohit.gupta
 *
 */
public class PolicyViolationsRuleLibrary {
	private static Log policyLogger = LogFactory.getLog("rapidapponboarding.rules");
	static final String POLICYMULTIPLEAPPLICATIONSCUSTOM = "Custom-PolicyViolation-AllowDenyExceptions";
	static final String ALLOWFILTER = "policySoftFilter";
	static final String DENYFILTER = "policyDenyFilter";
	private static final String ALLOWREQUESTVIOLTATIONS = "allowRequestsWithViolations";
	private static final String TOXICCOMBINATIONNAME = "txCombinationName";
	private static final String SOFTPOLICYVIOLATIONS = "softPolicyviolations";
	private static final String HARDPOLICYVIOLATIONS = "hardPolicyviolations";
	private static final String REQUIREVIOLATIONREVIEWCOMMENTS = "requireViolationReviewComments";
	private static final String ALLOWEXCEPTION = "Allow Exception";
	private static final String DENYEXCEPTION = "Deny Exception";
	private static final String POLICYTYPE = "policyType";
	private static final String SINGLEENTITLEMENT = "Single Entitlement";
	private static final String MULTIPLEENTITLEMENT = "Multiple Entitlement";
	private static final String POLICYTYPEEXCEPTIONS = "policytypeExceptions";
	private static final Object SINLGEENTITLEMENTMAP = "singleEntitlementMap";
	private static final Object EXCEPTIONS = "exceptions";
	private static final String POLICYELGIBILITYEXPRESSION = "eligibilityExpression";
	private static final String POLICYELGIBILITYTOKEN = "#IIQPolicyEligibility#";
	private static Custom customPolicy;
	/**
	 * This method is for toxic combinations
	 * @param context
	 * @param applicationName
	 * @param identityName
	 * @return
	 * @throws Exception
	 */
	public static PolicyViolation detectPolicyViolation(SailPointContext context, String applicationName, Identity identity)throws Exception 
	{
		LogEnablement.isLogDebugEnabled(policyLogger,"Entering PolicyViolationsRuleLibrary::detectPolicyViolation: "+ "..applicationName.."+ applicationName+ "..identityName.."+ identity.getName());
		boolean policyViolationFlag = false;
		String flaggedAccounts = "";
		String customObjectName = null;
		if (identity != null && identity.isCorrelated() && applicationName != null) 
		{
			Application app = null;
			List links = new ArrayList();
			IdentityService is = new IdentityService(context);
			app = context.getObjectByName(Application.class, applicationName);
			if (app != null) 
			{
				List<Link> linksService = is.getLinks(identity, app);
				LogEnablement.isLogDebugEnabled(policyLogger,"Get Application Links from IdentityService.."+ linksService);
				links.addAll(linksService);
				customObjectName = (String) app.getAttributeValue(PolicyViolationsRuleLibrary.TOXICCOMBINATIONNAME);
			}
			LogEnablement.isLogDebugEnabled(policyLogger,"Application Links.." + links);
			if (links != null && !links.isEmpty() && customObjectName != null) 
			{
				Map<String, Map.Entry> map = null;
				Custom customObj = context.getObject(Custom.class,customObjectName);
				LogEnablement.isLogDebugEnabled(policyLogger,"Get customObj");
				if (customObj != null)
				{
					map = (Map) customObj.get("ViolationsMap");
					LogEnablement.isLogDebugEnabled(policyLogger,"Get ViolationsMap");
					if (map != null) 
					{
						int linkCount = 0;
						Set linkSet = null;
						ArrayList<Entitlement> linkEntitlementsObjects = null;
						ArrayList linkEntitlements = null;
						ArrayList<HashMap> conflictSetList = new ArrayList();
						for (Map.Entry e : map.entrySet()) 
						{
							String comboID = (String) e.getKey();
							String conflictList = (String) e.getValue();
							LogEnablement.isLogDebugEnabled(policyLogger,"Get ViolationsMap conflictList "
									+ conflictList);
							HashMap conflictMap = new HashMap();
							conflictMap.put(comboID,new HashSet(Arrays.asList(conflictList.split(","))));
							LogEnablement.isLogDebugEnabled(policyLogger,"Conflicting List Split Map "+ conflictMap);
							conflictSetList.add(conflictMap);
						}
						Iterator linkIterator = links.iterator();
						while (linkIterator.hasNext()) 
						{
							Link link = (Link) linkIterator.next();
							boolean linkConflict = false;
							String conflictComboIDs = "";
							linkEntitlementsObjects = (ArrayList) link.getEntitlements(null, null);
							LogEnablement.isLogDebugEnabled(policyLogger,"Get Link Ents");
							linkEntitlements = new ArrayList();
							for (Entitlement e : linkEntitlementsObjects) 
							{
								linkEntitlements.add(e.getAttributeValue());
							}
							linkSet = new HashSet(linkEntitlements);
							for (HashMap<String, Map.Entry> conflictMap : conflictSetList) 
							{
								Set conflictSet = null;
								String comboID = "";
								for (Map.Entry e : conflictMap.entrySet()) 
								{
									comboID = (String) e.getKey();
									conflictSet = (Set) e.getValue();
								}
								if (linkSet.containsAll(conflictSet))
								{
									linkConflict = true;
									conflictComboIDs += " Grouping ID: "+ comboID + " Constraints: " + "("+ conflictSet + ")";
								}
							}
							if (linkConflict) 
							{
								policyViolationFlag = true;
								String linkName = link.getNativeIdentity();
								if (linkCount > 0) 
								{
									flaggedAccounts += "|" + " Account ID: "+ linkName + conflictComboIDs;
								} else
								{
									flaggedAccounts += "Account ID: "+ linkName + conflictComboIDs;
								}
								linkCount++;
							}
						}
					}
				}
				context.decache(customObj);
			}
			context.decache(app);
		}
		PolicyViolation pv = null;
		if (policyViolationFlag) 
		{
			pv = new PolicyViolation();
			pv.setActive(true);
			pv.setIdentity(identity);
			pv.setDescription("The following account(s) are in violation: "+ flaggedAccounts);
			pv.setStatus(sailpoint.object.PolicyViolation.Status.Open);
		}
		LogEnablement.isLogDebugEnabled(policyLogger,"Exit PolicyViolationsRuleLibrary::detectPolicyViolation: "+ "..applicationName.."+ applicationName+ "..identityName.."+ identity.getName() + "..pv.." + pv);
		return pv;
	}
	/**
	 * Detect Single Entitlement Policy Violations
	 * 
	 * @param context
	 * @param applicationName
	 * @param identityName
	 * @return
	 * @throws Exception
	 */
	public static PolicyViolation detectSingleEntitlementPolicyViolation(SailPointContext context, String applicationName, Identity identity)throws Exception 
	{
		LogEnablement.isLogDebugEnabled(policyLogger,"Entering PolicyViolationsRuleLibrary::detectSingleEntitlementPolicyViolation: "+ "..applicationName.."+ applicationName+ "..identityName.."+ identity.getName());
		boolean policyViolationFlag = false;
		String flaggedAccounts = "";
		if (identity != null && identity.isCorrelated()&& applicationName != null) 
		{
			Application app = null;
			List links = new ArrayList();
			IdentityService is = new IdentityService(context);
			app = context.getObjectByName(Application.class, applicationName);
			if (app != null) 
			{
				List<Link> linksService = is.getLinks(identity, app);
				LogEnablement.isLogDebugEnabled(policyLogger,"Get Application Links from IdentityService.."+ linksService);
				links.addAll(linksService);
			}
			LogEnablement.isLogDebugEnabled(policyLogger,"Application Links.." + links);
			if (!links.isEmpty())
			{
				int linkCount = 0;
				ArrayList<Entitlement> linkEntitlementsObjects = null;
				Iterator linkIterator = links.iterator();
				while (linkIterator.hasNext()) 
				{
					Link link = (Link) linkIterator.next();
					linkEntitlementsObjects = (ArrayList) link.getEntitlements(null, null);
					LogEnablement.isLogDebugEnabled(policyLogger,"...linkEntitlementsObjects = "
							+ linkEntitlementsObjects);
					// If the link has more than 1 entitlement, that is a violation
					if (linkEntitlementsObjects != null
							&& linkEntitlementsObjects.size() > 1) 
					{
						List linkEntitlements = new ArrayList();
						for (Entitlement e : linkEntitlementsObjects) 
						{
							linkEntitlements.add(e.getAttributeValue());
						}
						LogEnablement.isLogDebugEnabled(policyLogger,"...linkEntitlements = "+ linkEntitlements);
						// When returns true then cause a Policy Violation else
						// no Policy Violation
						boolean exceptionViolation = entitlementExceptionsViolation(context, applicationName, linkEntitlements);
						LogEnablement.isLogDebugEnabled(policyLogger,"...exceptionViolation = "+ exceptionViolation);
						if (exceptionViolation) 
						{
							policyViolationFlag = true;
						} else {
							policyViolationFlag = false;
						}
						LogEnablement.isLogDebugEnabled(policyLogger,"...policyViolationFlag = "+ policyViolationFlag);
						if (linkCount > 0) 
						{
							flaggedAccounts += "|" + link.getNativeIdentity();
						} 
						else 
						{
							flaggedAccounts += link.getNativeIdentity();
						}
						linkCount++;
					}
				}
			}
			context.decache(app);
		}
		PolicyViolation pv = null;
		if (policyViolationFlag) 
		{
			pv = new PolicyViolation();
			pv.setActive(true);
			pv.setIdentity(identity);
			pv.setDescription("The following account(s) are in violation, for having more than 1 entitlement: "+ flaggedAccounts);
			pv.setStatus(sailpoint.object.PolicyViolation.Status.Open);
		}
		LogEnablement.isLogDebugEnabled(policyLogger,"End PolicyViolationsRuleLibrary::detectSingleEntitlementPolicyViolation: "+ "..aplicationName.."+ applicationName+ "..identityName.."+ identity.getName() + "..v.." + pv);
		return pv;
	}
	/**
	 * Single Entitlement Option Exceptions
	 * 
	 * @param context
	 * @param applicationName
	 * @param entitlementList
	 * @return
	 * @throws GeneralException
	 */
	public static boolean entitlementExceptionsViolation(SailPointContext context, String applicationName,List entitlementList) throws GeneralException {
		LogEnablement.isLogDebugEnabled(policyLogger,"End entitlementExceptionsViolation: " + applicationName+ ":" + entitlementList);
		boolean retVal = false;
		String customObjectName = null;
		Application app = context.getObjectByName(Application.class,applicationName);
		Custom custom = null;
		try 
		{
			if (app != null) 
			{
				customObjectName = (String) app.getAttributeValue(PolicyViolationsRuleLibrary.POLICYTYPEEXCEPTIONS);
			}
			String exceptionEntitlements = "";
			List exceptionEntitlementsList = new ArrayList();
			if (customObjectName != null) 
			{
				custom = context.getObjectByName(Custom.class, customObjectName);
				Map singleEntitlementMap = (Map) custom.getAttributes().get(PolicyViolationsRuleLibrary.SINLGEENTITLEMENTMAP);
				if (singleEntitlementMap != null&& singleEntitlementMap.containsKey(PolicyViolationsRuleLibrary.EXCEPTIONS)) 
				{
					exceptionEntitlements = (String) singleEntitlementMap.get(PolicyViolationsRuleLibrary.EXCEPTIONS);
					if (exceptionEntitlements != null&& !exceptionEntitlements.equals("")) 
					{
						List exceptionsList = Util.csvToList(exceptionEntitlements);
						LogEnablement.isLogDebugEnabled(policyLogger,"...exceptionsList = " + exceptionsList);
						entitlementList.removeAll(exceptionsList);
						LogEnablement.isLogDebugEnabled(policyLogger,"...entitlementList = " + entitlementList);
						if (entitlementList.size() > 0) 
						{
							retVal = true;
						} 
						else 
						{
							retVal = false;
						}
					} 
					else 
					{
						retVal = true;
					}
				} 
				else 
				{
					retVal = true;
				}
			}
		} 
		finally 
		{
			if (app != null) 
			{
				context.decache(app);
			}
			if (custom != null) 
			{
				context.decache(custom);
			}
		}
		LogEnablement.isLogDebugEnabled(policyLogger,"End entitlementExceptionsViolation: " + applicationName+ ":" + "..entitlementList.." + entitlementList + "..retVal.."+ retVal);
		return retVal;
	}
	/**
	 * Returns a Custom object with policy violation exceptions
	 * 
	 * @param context
	 * @param requestType
	 * @param source
	 * @return
	 * @throws GeneralException
	 */
	public static Custom getPolicyViolationExceptionsConfig(SailPointContext context, String requestType, String source)throws GeneralException 
	{
		LogEnablement.isLogDebugEnabled(policyLogger,"Enter getPolicyViolationExceptionsConfig");
		if ((requestType != null && (requestType.equalsIgnoreCase(WrapperRuleLibrary.CART_REQUEST_FEATURE)))|| (source != null && source.equalsIgnoreCase(WrapperRuleLibrary.BATCH))) {
			// Get the Custom Object
			LogEnablement.isLogDebugEnabled(policyLogger,"Get Custom Object");
			customPolicy = getCustomObject(context);
			LogEnablement.isLogDebugEnabled(policyLogger,"Custom Policy Artifact " + customPolicy);
			LogEnablement.isLogDebugEnabled(policyLogger,"End getPolicyViolationExceptionsConfig "+ customPolicy);
			return customPolicy;
		}
		return null;
	}
	/**
	 * Get Custom-PolicyViolation-AllowDenyExceptions
	 * 
	 * @return custom
	 * @throws GeneralException
	 */
	synchronized static Custom getCustomObject(SailPointContext context)throws GeneralException 
	{
		// Adding second check to avoid re-initialization when the multiple
		// threads enter into the above if condition and waiting for this to be
		// initialized
		LogEnablement.isLogDebugEnabled(policyLogger,"Enter getCustomObject ");
		if (null == customPolicy|| null == customPolicy.getAttributes()|| !PolicyViolationsRuleLibrary.POLICYMULTIPLEAPPLICATIONSCUSTOM.equalsIgnoreCase(customPolicy.getName())) 
		{
			LogEnablement.isLogDebugEnabled(policyLogger,"...Entering getCustom");
			customPolicy = context.getObjectByName(Custom.class,PolicyViolationsRuleLibrary.POLICYMULTIPLEAPPLICATIONSCUSTOM);
			LogEnablement.isLogDebugEnabled(policyLogger,"...Exiting getCustom");
		} else {
            Date dbModified = Servicer.getModificationDate(context, customPolicy);
            if (Util.nullSafeCompareTo(dbModified, customPolicy.getModified()) > 0) {
                LogEnablement.isLogDebugEnabled(policyLogger,"...Returning updated customPolicy object");
                customPolicy = context.getObjectByName(Custom.class, PolicyViolationsRuleLibrary.POLICYMULTIPLEAPPLICATIONSCUSTOM);
            } else {
                LogEnablement.isLogDebugEnabled(policyLogger,"...Returning previously initialized customPolicy object");
            }
        }
		LogEnablement.isLogDebugEnabled(policyLogger,"End getCustomObject ");
		return customPolicy;
	}
	/*
	 * Returns a List of all Policy names in the Custom object and policies
	 * defined on Application
	 */
	public static String getPoliciesToCheck(SailPointContext context,ProvisioningPlan plan, String requestType, String source) throws GeneralException
	{
		LogEnablement.isLogDebugEnabled(policyLogger,"Enter getPoliciesToCheck");
		Set policySet = new HashSet();
		if ((requestType != null && (requestType.equalsIgnoreCase(WrapperRuleLibrary.CART_REQUEST_FEATURE)))|| (source != null && source.equalsIgnoreCase(WrapperRuleLibrary.BATCH))) 
		{
			//Get Policies from Global Definitions
			Custom customObject = getPolicyViolationExceptionsConfig(context,requestType, source);
			if (customObject != null) 
			{
				Attributes attributes = customObject.getAttributes();
				if (attributes != null) 
				{
					List policyList = new ArrayList();
					policyList = customObject.getAttributes().getKeys();
					if (policyList != null && policyList.size() > 0) {
						policySet = new HashSet(policyList);
					}
				}
				context.decache(customObject);
			}
			//Get Policies from Provisioning Plan
			HashMap map = getSoftHardPolicyViolationListFromProvisioningPlan(context, plan, requestType, source);
			if (map != null) 
			{
				if (map.containsKey("soft")) 
				{
					policySet.addAll((List) map.get("soft"));
				}
				if (map.containsKey("hard")) 
				{
					policySet.addAll((List) map.get("hard"));
				}
			}
			//Get Policies from Filters
			HashMap mapFilter = getFitlerBasedPolicyViolations(context);
			if (mapFilter != null) 
			{
				if (mapFilter.containsKey("soft")) 
				{
					policySet.addAll((List) mapFilter.get("soft"));
				}
				if (mapFilter.containsKey("hard")) 
				{
					policySet.addAll((List) mapFilter.get("hard"));
				}
			}
		}
        //Convert Set into List
		List policyList = new ArrayList(policySet);
		if (policyList != null && policyList.size() > 0) 
		{
			// Convert them into comma separated values
			String csvStr=Util.listToCsv(policyList);
			LogEnablement.isLogDebugEnabled(policyLogger,"End getPoliciesToCheck csvStr.."+csvStr);
			return csvStr;
		} 
		else 
		{
			LogEnablement.isLogDebugEnabled(policyLogger,"End getPoliciesToCheck Nothing");
			return null;
		}
	}
	/**
	 * Get Policy Violation List from Provisioning Plan Applications
	 * 
	 * @param plan
	 */
	public static HashMap getSoftHardPolicyViolationListFromProvisioningPlan(SailPointContext context, ProvisioningPlan plan,String requestType, String source) throws GeneralException
	{
		LogEnablement.isLogDebugEnabled(policyLogger,"Start getSoftHardPolicyViolationListFromProvisioningPlan ");
		HashMap masterPV = new HashMap();
		List softPvList = new ArrayList();
		List hardPvList = new ArrayList();
		HashSet roleListAppNames = new HashSet();
		HashSet entListAppNames = new HashSet();
		HashSet accountListAppNames = new HashSet();

        //hardPolicyviolations and softPolicyviolations properties are only supported in AP Beta.
        //Adding this validation will fix bug IIQSR-252 and regression IIQTC-347
        boolean policyCheckHardEnabled = Util.nullSafeEq("True",ObjectConfigAttributesRuleLibrary.extendedAttrHardPolicyEnabled(context));
        boolean policyCheckSoftEnabled = Util.nullSafeEq("True",ObjectConfigAttributesRuleLibrary.extendedAttrSoftPolicyEnabled(context));

        if (!policyCheckHardEnabled || !policyCheckSoftEnabled) {
            return masterPV;
        }

		if (plan != null)
		{
			if ((requestType != null && (requestType.equalsIgnoreCase(WrapperRuleLibrary.CART_REQUEST_FEATURE) || requestType.contains(WrapperRuleLibrary.JOINER)))|| (source != null && source.equalsIgnoreCase(WrapperRuleLibrary.BATCH)))
			{
				List<AccountRequest> acctReqList = plan.getAccountRequests();
				if (acctReqList != null) {
					for (AccountRequest acctReq : acctReqList)
					{
						String appName = acctReq.getApplicationName();
						List<AttributeRequest> attrReqList = acctReq.getAttributeRequests();
						if (appName != null&& attrReqList != null&& (appName.equalsIgnoreCase(ProvisioningPlan.APP_IIQ)|| appName.equalsIgnoreCase(ProvisioningPlan.IIQ_APPLICATION_NAME) || appName.equalsIgnoreCase(ProvisioningPlan.APP_IDM))) {
							// Get All Roles Application Names
							for (AttributeRequest attrReq : attrReqList) 
							{
								if (attrReq != null&& (attrReq.getOp() != null && attrReq.getOp().equals(ProvisioningPlan.Operation.Add) || (attrReq.getOp() != null && attrReq.getOp().equals(ProvisioningPlan.Operation.Set))))
								{
									String attrName = attrReq.getName();
									Object value = attrReq.getValue();
									List<String> valueList = new ArrayList();
									if (value != null && value instanceof List)
									{
										valueList = (List) value;
									} 
									else if (value != null) 
									{
										valueList.add(value.toString());
									}
									if (valueList != null) 
									{
										for (String valueStr : valueList) 
										{
											Bundle role = context.getObjectByName(Bundle.class,valueStr);
											// Applications will be empty for
											// business roles, however,
											// Permitted roles will have
											// applications
											Set<Application> applications = role.getApplications();
											if (applications != null && !applications.isEmpty()) 
											{
												for (Application appSet : applications) 
												{
													roleListAppNames.add(appSet.getName());
												}
											} 
											else 
											{
												List<Bundle> requiredBundles = role.getRequirements();
												if (requiredBundles != null) 
												{
													for (Bundle requiredBundle : requiredBundles) 
													{
														Set<Application> setReqAppNames = requiredBundle.getApplications();
														if (setReqAppNames != null
																&& !setReqAppNames
																.isEmpty()) {
															for (Application appSet : setReqAppNames) 
															{
																roleListAppNames.add(appSet.getName());
															}
														}
													}
												}
											}
										}
									}
								}
							}
						}
						// Get All Application Names from Entitlements
						else if (appName != null && attrReqList != null) 
						{
							for (AttributeRequest attrReq : attrReqList) 
							{
								if (attrReq != null
										&& (attrReq.getOp().equals(ProvisioningPlan.Operation.Add) || attrReq.getOp().equals(ProvisioningPlan.Operation.Set))) {
									entListAppNames.add(appName);
									break;
								}
							}
						} 
						else if (appName != null) 
						{
							accountListAppNames.add(appName);
						}
					}// End For Loop
				}// End If Account Request Not Null
			}// End if requestType is Cart Request or source is Batch Request
		}// End Plan Null Check
		List<String> masterListAppNames = new ArrayList();
		if (entListAppNames != null && entListAppNames.size() > 0) 
		{
			masterListAppNames.addAll(entListAppNames);
		}
		if (roleListAppNames != null && roleListAppNames.size() > 0) 
		{
			masterListAppNames.addAll(roleListAppNames);
		}
		if (accountListAppNames != null && accountListAppNames.size() > 0) 
		{
			masterListAppNames.addAll(accountListAppNames);
		}
		for (String masterAppName : masterListAppNames) 
		{
			QueryOptions qo = new QueryOptions();
			Filter queryFilter = Filter.eq("name", masterAppName);
			qo.addFilter(queryFilter);
			List<String> properties = new ArrayList();
			properties.add(PolicyViolationsRuleLibrary.SOFTPOLICYVIOLATIONS);
			properties.add(PolicyViolationsRuleLibrary.HARDPOLICYVIOLATIONS);
			Iterator it = context.search(Application.class, qo, properties);
			if (it != null) 
			{
				while (it.hasNext()) 
				{
					String softPv = null;
					String hardPv = null;
					Object[] retObjs = (Object[]) it.next();
					if (retObjs != null && retObjs.length == 2)
					{
						softPv = (String) retObjs[0];
						hardPv = (String) retObjs[1];
					}
					if (softPv != null) 
					{
						List UtilSoftPv = Util.csvToList(softPv);
						if (UtilSoftPv != null && UtilSoftPv.size() > 0) 
						{
							softPvList.addAll(UtilSoftPv);
						}
					}
					if (hardPv != null)
					{
						List UtilHardPv = Util.csvToList(hardPv);
						if (UtilHardPv != null && UtilHardPv.size() > 0) 
						{
							hardPvList.addAll(UtilHardPv);
						}
					}
				}
			}
			Util.flushIterator(it);
		}
		masterPV.put("soft", softPvList);
		masterPV.put("hard", hardPvList);
		LogEnablement.isLogDebugEnabled(policyLogger," softPvList "+softPvList);
		LogEnablement.isLogDebugEnabled(policyLogger," hardPvList "+hardPvList);
		LogEnablement.isLogDebugEnabled(policyLogger,"End getSoftHardPolicyViolationListFromProvisioningPlan "+masterPV);
		return masterPV;
	}
	/**
	 * Get Policy Violations Based on Filters
	 * @param context
	 * @return
	 * @throws GeneralException
	 */
	public static HashMap getFitlerBasedPolicyViolations(SailPointContext context) throws GeneralException
	{
		LogEnablement.isLogDebugEnabled(policyLogger,"Start getFitlerBasedPolicyViolations ");
		HashMap masterPV = new HashMap();
		List<String> softPvList = new ArrayList();
		List hardPvList = new ArrayList();
		String allowFilter=(String)ROADUtil.getGlobalDefinitionAttribute(context, PolicyViolationsRuleLibrary.ALLOWFILTER);
		LogEnablement.isLogDebugEnabled(policyLogger," allowFilter "+allowFilter);
		String denyFilter=(String)ROADUtil.getGlobalDefinitionAttribute(context, PolicyViolationsRuleLibrary.DENYFILTER);
		LogEnablement.isLogDebugEnabled(policyLogger," denyFilter "+denyFilter);
		List listProperties = new ArrayList();
		listProperties.add("name");
		QueryOptions qoPolicy;
		if(allowFilter!=null && allowFilter.length()>0)
		{
			Filter allowFilterCompiled=Filter.compile(allowFilter);
			if(allowFilterCompiled!=null)
			{
				LogEnablement.isLogDebugEnabled(policyLogger," allowFilterCompiled "+allowFilterCompiled.toString());
				qoPolicy = new QueryOptions();
				qoPolicy.add(allowFilterCompiled);
				String policyName=null;
				Iterator policyIterator = context.search(Policy.class, qoPolicy,listProperties);
				if (policyIterator != null)
				{
					while (policyIterator.hasNext()) 
					{
						Object[] rowApp = (Object[]) policyIterator.next();
						if(rowApp!=null && rowApp.length==1)
						{
							policyName = (String)rowApp[0];
							softPvList.add(policyName);
						}			            
					}
				}
				Util.flushIterator(policyIterator);
			}
		}
		if(denyFilter!=null && denyFilter.length()>0)
		{
			Filter denyFilterCompiled=Filter.compile(denyFilter);
			if(denyFilterCompiled!=null)
			{
				LogEnablement.isLogDebugEnabled(policyLogger," denyFilterCompiled "+denyFilterCompiled.toString());
				qoPolicy = new QueryOptions();
				qoPolicy.add(denyFilterCompiled);
				String policyName=null;
				Iterator policyIterator = context.search(Policy.class, qoPolicy,listProperties);
				if (policyIterator != null)
				{
					while (policyIterator.hasNext()) 
					{
						Object[] rowApp = (Object[]) policyIterator.next();
						if(rowApp!=null && rowApp.length==1)
						{
							policyName = (String)rowApp[0];
							hardPvList.add(policyName);
						}			            
					}
				}
				Util.flushIterator(policyIterator);
			}
		}
		masterPV.put("soft", softPvList);
		masterPV.put("hard", hardPvList);
		LogEnablement.isLogDebugEnabled(policyLogger," softPvList "+softPvList);
		LogEnablement.isLogDebugEnabled(policyLogger," hardPvList "+hardPvList);
		LogEnablement.isLogDebugEnabled(policyLogger,"End getFitlerBasedPolicyViolations "+masterPV);
		return masterPV;
	}
	/**
	 * Sets the value of the workflow variable PolicyViolationRuleLibrary.ALLOWREQUESTVIOLTATIONS to
	 * true, if there any soft policy violations defined and there are no
	 * multiple applications policy names for hard stop and no
	 * hardPolicyViolationNames
	 * @param context
	 * @param workflow
	 * @param requestType
	 * @param source
	 * @param plan
	 * @param preventivePolicyViolationsListMap
	 * @param allowRequestsWithViolations
	 * @param requireViolationReviewComments
	 * @throws GeneralException
	 */
	public static void setPolicyViolationActions(SailPointContext context,Workflow workflow, String requestType, String source,ProvisioningPlan plan, List<Map> preventivePolicyViolationsListMap, String allowRequestsWithViolations,String requireViolationReviewComments)throws GeneralException {
		LogEnablement.isLogDebugEnabled(policyLogger,"Entering setPolicyViolationActions::setPolicyViolationActions: ");
		LogEnablement.isLogDebugEnabled(policyLogger,"Entering requestType::requestType: "+requestType);
		LogEnablement.isLogDebugEnabled(policyLogger,"Entering preventivePolicyViolationsListMap::preventivePolicyViolationsListMap: "+preventivePolicyViolationsListMap);
		LogEnablement.isLogDebugEnabled(policyLogger,"Entering setPolicyViolationActions::allowRequestsWithViolations: "+allowRequestsWithViolations);
		LogEnablement.isLogDebugEnabled(policyLogger,"Entering setPolicyViolationActions::requireViolationReviewComments: "+requireViolationReviewComments);
		Attributes attributes = null;
		boolean foundMultipleAppsHardStop = false;
		boolean foundAppsHardStop = false;
		List<String> preventivePolicyViolations = new ArrayList();
		//Initialize as not allowed
		String allowViolations = "false";
		//Initialize from Global Definition
		if(allowRequestsWithViolations!=null && allowRequestsWithViolations.length()>0)
		{
			allowViolations=allowRequestsWithViolations;
		}
		// Multiple Applications Policy Violations
		Custom customObject = getPolicyViolationExceptionsConfig(context,requestType, source);
		//Get Allow and Deny Policy Violations - Provisioning Plan
		HashMap map = getSoftHardPolicyViolationListFromProvisioningPlan(context, plan, requestType, source);
		List<String> softPolicyViolationNames = new ArrayList();
		List<String> hardPolicyViolationNames = new ArrayList();
		if (map != null) 
		{
			if (map.containsKey("soft")) 
			{
				softPolicyViolationNames.addAll((List) map.get("soft"));
			}
			if (map.containsKey("hard")) 
			{
				hardPolicyViolationNames.addAll((List) map.get("hard"));
			}
		}
		//Get Allow and Deny Policy Violations From Filter
		HashMap mapFilter = getFitlerBasedPolicyViolations(context);
		List<String> softPolicyViolationNamesFilter = new ArrayList();
		List<String> hardPolicyViolationNamesFilter = new ArrayList();
		if (mapFilter != null) 
		{
			if (mapFilter.containsKey("soft")) 
			{
				softPolicyViolationNamesFilter.addAll((List) mapFilter.get("soft"));
			}
			if (mapFilter.containsKey("hard")) 
			{
				hardPolicyViolationNamesFilter.addAll((List) mapFilter.get("hard"));
			}
		}
		// Ssytem Detected Preventive Policy Violations in the Request
		if (preventivePolicyViolationsListMap != null && !preventivePolicyViolationsListMap.isEmpty()) 
		{
			for (Map policyViolation : preventivePolicyViolationsListMap) 
			{
				if (policyViolation != null) 
				{
					preventivePolicyViolations.add((String) policyViolation.get("policyName"));
				}
			}
		}
		// If there any hard policy violations in the access request just deny access request
		if (hardPolicyViolationNames != null&& hardPolicyViolationNames.size() > 0 && preventivePolicyViolations != null&& preventivePolicyViolations.size() > 0) 
		{
			for (String policyName : preventivePolicyViolations) 
			{
				if (hardPolicyViolationNames.contains(policyName)) 
				{
					allowViolations = "false";
					workflow.put(PolicyViolationsRuleLibrary.ALLOWREQUESTVIOLTATIONS, allowViolations);
					LogEnablement.isLogDebugEnabled(policyLogger,"One of the perventive policy violation - hard stop");
					foundAppsHardStop=true;
					break;
				}
			}
		}
		// If there any hard policy violations from filter just deny access request
		else if (hardPolicyViolationNamesFilter != null&& hardPolicyViolationNamesFilter.size() > 0 && preventivePolicyViolations != null&& preventivePolicyViolations.size() > 0) 
		{
			for (String policyName : preventivePolicyViolations) 
			{
				if (hardPolicyViolationNamesFilter.contains(policyName)) 
				{
					allowViolations = "false";
					workflow.put(PolicyViolationsRuleLibrary.ALLOWREQUESTVIOLTATIONS, allowViolations);
					LogEnablement.isLogDebugEnabled(policyLogger,"One of the perventive policy violation filter - hard stop");
					foundAppsHardStop=true;
					break;
				}
			}
		}
		// If there any multiple applications hard policy violations in the access request just deny access request
		else if (customObject != null && preventivePolicyViolationsListMap != null && preventivePolicyViolationsListMap.size() > 0) 
		{
			attributes = customObject.getAttributes();
			if (preventivePolicyViolations != null&& preventivePolicyViolations.size() > 0&& attributes != null) 
			{
				for (String policyName : preventivePolicyViolations)
				{
					Map policyMap = attributes.getMap();
					if (policyMap != null && policyMap.containsKey(policyName) && policyMap.get(policyName) != null) 
					{
						if (((String) policyMap.get(policyName)).equalsIgnoreCase(PolicyViolationsRuleLibrary.DENYEXCEPTION)) 
						{
							allowViolations = "false";
							foundMultipleAppsHardStop = true;
							break;
						} 
						else if (((String) policyMap.get(policyName)).equalsIgnoreCase(PolicyViolationsRuleLibrary.ALLOWEXCEPTION)) 
						{
							/*Allow access request for soft stop multiple applications policy name
							 * if we find ALL (no break here) policy violations are soft, the allow the access request
							 */
							allowViolations = "true";
						} 
						else 
						{
							// Default Just deny access request
							allowViolations = "false";
							foundMultipleAppsHardStop = true;
							break;
						}
					}
				}
			}
			/*If there are no multiple and single  applications hard policy violations, 
			 * Iterate through all soft policy violations and see if all
			 * of them are in the requested preventive PolicyViolations list
			 */
			if (!foundMultipleAppsHardStop && !foundAppsHardStop) 
			{
				if (softPolicyViolationNames != null && softPolicyViolationNames.size() > 0 && preventivePolicyViolations != null && preventivePolicyViolations.size() > 0) 
				{
					for (String policyName : preventivePolicyViolations) 
					{
						if (softPolicyViolationNames.contains(policyName)) 
						{
							/*If we find ALL policy
							 * violations are soft then allow the access request
							 */
							LogEnablement.isLogDebugEnabled(policyLogger,"Found ALL Preventive Soft Stop");
							allowViolations = "true";
						} 
						else 
						{
							// Default Just deny access request
							allowViolations = "false";
							break;
						}
					}
				}
				if (softPolicyViolationNamesFilter != null && softPolicyViolationNamesFilter.size() > 0 && preventivePolicyViolations != null && preventivePolicyViolations.size() > 0) 
				{
					for (String policyName : preventivePolicyViolations) 
					{
						if (softPolicyViolationNamesFilter.contains(policyName)) 
						{
							/*If we find ALL policy
							 * violations are soft then allow the access request
							 */
							LogEnablement.isLogDebugEnabled(policyLogger,"Found ALL Preventive Soft Stop Filter");
							allowViolations = "true";
						} 
						else 
						{
							// Default Just deny access request
							allowViolations = "false";
							break;
						}
					}
				}
			} 
		}
		//Initialize No Comments Required
		String requireViolreviewComments = "false";
		//Initialize from Global Definition
		if(requireViolationReviewComments!=null && requireViolationReviewComments.length()>0)
		{
			workflow.put(PolicyViolationsRuleLibrary.REQUIREVIOLATIONREVIEWCOMMENTS, requireViolationReviewComments);
		}
		// Require Comments for Soft Policy Violations
		if (allowViolations != null && allowViolations.equalsIgnoreCase("True")) 
		{
			requireViolreviewComments="true";
			workflow.put(PolicyViolationsRuleLibrary.REQUIREVIOLATIONREVIEWCOMMENTS, requireViolreviewComments);
		}
		workflow.put(PolicyViolationsRuleLibrary.ALLOWREQUESTVIOLTATIONS, allowViolations);
		LogEnablement.isLogDebugEnabled(policyLogger,"End setPolicyViolationActions::allowViolations: "+allowViolations);
		LogEnablement.isLogDebugEnabled(policyLogger,"End setPolicyViolationActions::requireViolreviewComments: "+requireViolreviewComments);
	}
	/**
	 * Get All Apps with Accelerator Pack Policy Violation Feature
	 * 
	 * @param context
	 * @return
	 * @throws GeneralException
	 */
	public static List getPVAppList(SailPointContext context)throws GeneralException 
	{
		LogEnablement.isLogDebugEnabled(policyLogger,"Enter getPVAppList ");
		QueryOptions qo = new QueryOptions();
		qo.addFilter(Filter.notnull("id"));
		java.util.Iterator<Object[]> itApp = context.search(Application.class,qo, "id");
		List<String> appIds = new ArrayList();
		if (itApp != null) 
		{
			while (itApp.hasNext()) 
			{
				Object[] objArr = (Object[]) itApp.next();
				if (objArr != null && objArr.length == 1 && objArr[0] != null) 
				{
					String appId = objArr[0].toString();
					appIds.add(appId);
				}
			}
		}
		Util.flushIterator(itApp);
		List<Application> listOfAppObjs = new ArrayList();
		for (String appId : appIds) 
		{
			Application appObj = context.getObjectById(Application.class, appId);
			listOfAppObjs.add(appObj);
		}
		List appNames = new ArrayList();
		String attr = PolicyViolationsRuleLibrary.POLICYTYPE;
		String attr1 = PolicyViolationsRuleLibrary.SINGLEENTITLEMENT;
		String attr2 = PolicyViolationsRuleLibrary.MULTIPLEENTITLEMENT;
		String policyType = "";
		for (Application application : listOfAppObjs) 
		{
			Attributes attrs = application.getAttributes();
			LogEnablement.isLogDebugEnabled(policyLogger,"attrs" + attrs);
			LogEnablement.isLogDebugEnabled(policyLogger,"app name->" + application.getName());
			if (attrs != null && attrs.containsKey(attr) && application != null) 
			{
				policyType = (String) application.getAttributeValue(attr);
				LogEnablement.isLogDebugEnabled(policyLogger,"policyType->" + policyType);
				if (policyType != null&& (policyType.contains(attr1) || policyType.contains(attr2))) 
				{
					appNames.add(application.getName());
				}
			}
			context.decache(application);
		}
		LogEnablement.isLogDebugEnabled(policyLogger,"End getPVAppList "+appNames);
		return appNames;
	}
	/**
	 * Single Entitlement Allowed
	 * 
	 * @param context
	 * @return
	 * @throws GeneralException
	 */
	public static List getSinglePVAppList(SailPointContext context)throws GeneralException 
	{
		LogEnablement.isLogDebugEnabled(policyLogger,"Enter getSinglePVAppList ");
		QueryOptions qo = new QueryOptions();
		qo.addFilter(Filter.notnull("id"));
		java.util.Iterator<Object[]> itApp = context.search(Application.class,qo, "id");
		List<String> appIds = new ArrayList();
		if (itApp != null && itApp.hasNext()) 
		{
			Object[] objArr = (Object[]) itApp.next();
			if (objArr != null && objArr.length == 1 && objArr[0] != null) 
			{
				String appId = objArr[0].toString();
				appIds.add(appId);
			}
		}
		Util.flushIterator(itApp);
		List<Application> listOfAppObjs = new ArrayList();
		for (String appId : appIds)
		{
			Application appObj = context.getObjectById(Application.class, appId);
			listOfAppObjs.add(appObj);
		}
		List appNames = new ArrayList();
		String attr = PolicyViolationsRuleLibrary.POLICYTYPE;
		String attr1 = PolicyViolationsRuleLibrary.SINGLEENTITLEMENT;
		String policyType = "";
		for (Application application : listOfAppObjs)
		{
			Attributes attrs = application.getAttributes();
			LogEnablement.isLogDebugEnabled(policyLogger,"attrs" + attrs);
			LogEnablement.isLogDebugEnabled(policyLogger,"app name->" + application.getName());
			if (attrs != null && attrs.containsKey(attr)) {
				policyType = (String) application.getAttributeValue(attr);
				LogEnablement.isLogDebugEnabled(policyLogger,"policyType->" + policyType);
				if (policyType != null && policyType.contains(attr1)) 
				{
					appNames.add(application.getName());
				}
			}
			context.decache(application);
		}
		LogEnablement.isLogDebugEnabled(policyLogger,"End getSinglePVAppList "+appNames);
		return appNames;
	}
	/**
	 * Is Identity Eligible for Application Access
	 * 
	 * @param context
	 * @param identityName
	 * @param appName
	 * @param name
	 * @param violationDesc
	 * @return
	 * @throws GeneralException
	 * @throws java.text.ParseException
	 */
	public static PolicyViolation isEligibleViolation(SailPointContext context,Identity identity, String appName, String name, String violationDesc)throws GeneralException, java.text.ParseException {
		boolean isEligible = false;
		LogEnablement.isLogDebugEnabled(policyLogger,"Enter isEligibleViolation");
		if (identity != null && identity.isCorrelated() && appName != null && violationDesc != null && name != null)
		{
			// Check the links and entitlements the Identity has to see if it
			// matches the Applications configured for Eligibility checks
			Application app = null;
			List links = new ArrayList();
			IdentityService is = new IdentityService(context);
			if (appName != null) 
			{
				app = context.getObjectByName(Application.class, appName);
				if (app != null) 
				{
					List<Link> appLinks = is.getLinks(identity, app);
					if (appLinks != null) 
					{
						links.addAll(appLinks);
					}
				}
				if (links != null && !links.isEmpty()) 
				{
					isEligible = PolicyViolationsRuleLibrary.isEligible(context,identity, appName);
					if (!isEligible) 
					{
						PolicyViolation violation = new PolicyViolation();
						violation.setActive(true);
						violation.setIdentity(identity);
						violation.setDescription(violationDesc);
						violation.setStatus(sailpoint.object.PolicyViolation.Status.Open);
						violation.setName(name);
						LogEnablement.isLogDebugEnabled(policyLogger,"Exit isEligibleViolation.."+violation);
						return violation;
					}
				}
			}
		}
		// Return null for Policy Violation
		LogEnablement.isLogDebugEnabled(policyLogger,"Exit isEligibleViolation = null");
		return null;
	}
	/**
	 * Used for Eligibility Policy Violations
	 * 
	 * @param identityName
	 * @param appName
	 * @return
	 * @throws GeneralException
	 */
	public static boolean isEligible(SailPointContext context,
			Identity identity, String appName) throws GeneralException {
		LogEnablement.isLogDebugEnabled(policyLogger,"Start isEligible.." + "identityName.."
				+ identity.getName() + "appName.." + appName);
		Application app = context.getObjectByName(Application.class, appName);
		if (app != null && identity != null) {
			String expression = (String) app
					.getAttributeValue(PolicyViolationsRuleLibrary.POLICYELGIBILITYEXPRESSION);
			LogEnablement.isLogDebugEnabled(policyLogger," isEligible.." + "expression.." + expression
					+ "appName.." + appName);
			if (expression != null) {
				String[] expressionArr = expression
						.split(PolicyViolationsRuleLibrary.POLICYELGIBILITYTOKEN);
				LogEnablement.isLogDebugEnabled(policyLogger,"expressionArr " + expressionArr);
				if (expressionArr != null && expressionArr.length == 2
						&& Util.isNotNullOrEmpty(expressionArr[0])
						&& Util.isNotNullOrEmpty(expressionArr[1])) {
					String identityAtr = expressionArr[0];
					LogEnablement.isLogDebugEnabled(policyLogger,"identityAtr " + identityAtr);
					String regex = expressionArr[1];
					LogEnablement.isLogDebugEnabled(policyLogger,"regex " + regex);
					if (identityAtr != null) {
						String text = (String) identity
								.getAttribute(identityAtr);
						LogEnablement.isLogDebugEnabled(policyLogger,"text " + text);
						if (text != null && regex != null
								&& ROADUtil.executeRegex(regex, text) >= 1) {
							return true;
						}
					}
				} else {
					LogEnablement.isLogDebugEnabled(policyLogger," isEligible.." + "matchPopulation..");
					int result = WrapperRuleLibrary.matchPopulation(context, identity, expression);
					if (result > 0) {
						LogEnablement.isLogDebugEnabled(policyLogger," isEligible.." + "matchPopulation..true");
						return true;
					}
				}
			}
			// No expression defined on the application
			else {
				LogEnablement.isLogDebugEnabled(policyLogger,"End isEligible.." + "identityName.."
						+ identity.getName() + "appName.." + appName
						+ "..true..");
				return true;
			}
			context.decache(app);
		}
		LogEnablement.isLogDebugEnabled(policyLogger,"End isEligible.." + "identityName.."
				+ identity.getName() + "appName.." + appName + "..false..");
		return false;
	}
}
