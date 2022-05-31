/*
Copyright © 2018 SailPoint Technologies, Inc. All Rights Reserved.
All logos, text, content, and works of authorship, including but not limited to underlying code, programming or scripting language, designs, and/or graphics,
that are used and/or depicted herein are protected under United States and international copyright and trademark laws and treaties,
and may not be used or reproduced without the prior express written permission of SailPoint Technologies, Inc.
 */
package sailpoint.rapidapponboarding.rule;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import sailpoint.api.ObjectUtil;
import sailpoint.api.SailPointContext;
import sailpoint.api.Workflower;
import sailpoint.object.Application;
import sailpoint.object.ApprovalItem;
import sailpoint.object.Attributes;
import sailpoint.object.AuthenticationAnswer;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.IdentityEntitlement;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.ProvisioningPlan.AccountRequest;
import sailpoint.object.ProvisioningPlan.AttributeRequest;
import sailpoint.object.QueryOptions;
import sailpoint.object.Rule;
import sailpoint.object.WorkItem;
import sailpoint.rapidapponboarding.logger.LogEnablement;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
/**
 * Leaver Lifecycle Events
 * @author rohit.gupta
 *
 */
public class LeaverRuleLibrary {
	public static final String LEAVERPROCESS = "terminationProcess";
	public static final String LEAVERFEATURE = "LEAVER FEATURE";
	public static final String LOAPROCESS = "loaProcess";
	public static final String LOAFEATURE = "LEAVER LOA FEATURE";
	public static final String LTDPROCESS = "ltdProcess";
	public static final String LTDFEATURE = "LEAVER LTD FEATURE";
	public static final String LEAVEROPTIONSPOPULATION = "apPopulationLeaverOptions";
	public static final String OPTIONSPOPULATIONTOKEN = "apPopulation";
	public static final String OPTIONSACCOUNTXTOKEN = "apDeferAccountXDays";
	public static final String OPTIONSACCESSXTOKEN = "apRemoveAccessXDays";
	public static final String OPTIONSTOKEN = "apTerminationOption";
	public static final String OPTIONSTERMINATIONEXTENDEDRULETOKEN = "apTerminationExtendedRule";
	public static final String IMMEDIATELEAVERFEATURE = "IMMEDIATE TERMINATION FEATURE";
	public static final Object SENDEMAILOPERATIONSLTD = "apSendEmailToOperationsLTD";
	public static final Object SENDEMAILOPERATIONSLOA = "apSendEmailToOperationsLOA";
	public static final String LEAVEREXCEPTIONTOKEN="#IIQEntitlementLeaverException#";
	public static final String LEAVEREXCEPTION="removeEntitlementLeaverExceptionExpression";
	public static final String LEAVERXDAYSRULE="Rule-FrameworkTerminateXDays";
	public static final String WORKITEMDISPLAYNAME = "Operations Approval - Account Changes for User: ";
	private static Log leaverLogger = LogFactory.getLog("rapidapponboarding.rules");
	/**
	 * Leaver LifeCycleEvent
	 * @param context
	 * @param previousIdentity
	 * @param newIdentity
	 * @param personaEnabled
	 * @return
	 * @throws Exception
	 */
	public static boolean isEligibleForLeaver(SailPointContext context,Identity previousIdentity, Identity newIdentity) throws Exception 
	{
		String identityName=null;
		if(newIdentity!=null)
		{
			identityName=newIdentity.getName();
		}
		LogEnablement.isLogDebugEnabled(leaverLogger,"Enter LeaverRuleLibrary::isEligibleForLeaver.."+identityName);
		boolean flag = false;
		// Persona Package
		String personaEnabled=WrapperRuleLibrary.isPersonaEnabled(context);
		if (personaEnabled != null && personaEnabled.equalsIgnoreCase("TRUE")) 
		{
			boolean valid=false;
			valid=WrapperRuleLibrary.validateTriggersBeforePersona( context, newIdentity,previousIdentity,  LeaverRuleLibrary.LEAVERPROCESS);
			if (!valid) 
			{
				return valid;
			}
			if (WrapperRuleLibrary.checkIsNewRelationshipPersona(context,newIdentity,previousIdentity)) 
			{
				LogEnablement.isLogDebugEnabled(leaverLogger,"Exit isEligibleForLeaver = false (New Relationship) Handled in Joiner.."+identityName);
				return false;
			}
			// All Relationships are dropped
			flag = WrapperRuleLibrary.checkIsTerminationPersona(context,newIdentity,previousIdentity);
			if (flag) 
			{
				LogEnablement.isLogDebugEnabled(leaverLogger,"Exit isEligibleForLeaver = true.."+identityName);
				LogEnablement.isLogDebugEnabled(leaverLogger,"...isTermination  = " + flag+".."+identityName);
				return true;
			}
		}
		// Either Relationship or HR Events, Both cannot co-exist for Leaver
		else {
			// HR Events
			flag = TriggersRuleLibrary.allowedForProcess(context,newIdentity,previousIdentity, LeaverRuleLibrary.LEAVERPROCESS, LeaverRuleLibrary.LEAVERFEATURE,"");
			LogEnablement.isLogDebugEnabled(leaverLogger,"Exit LeaverRuleLibrary::isEligibleForLeaver "+flag+"..."+identityName);
			return flag;
		}
		return flag;
	}
	/**
	 * LOA LifeCycleEvent
	 * @param context
	 * @param previousIdentity
	 * @param newIdentity
	 * @return
	 * @throws Exception
	 */
	public static boolean isEligibleForLOA(SailPointContext context,Identity previousIdentity, Identity newIdentity) throws Exception 
	{
		String identityName=null;
		if(newIdentity!=null)
		{
			identityName=newIdentity.getName();
		}
		LogEnablement.isLogDebugEnabled(leaverLogger,"Enter LeaverRuleLibrary::isEligibleForLOA.."+identityName);
		boolean flag = false;
		flag = TriggersRuleLibrary.allowedForProcess(context,newIdentity, previousIdentity, LeaverRuleLibrary.LOAPROCESS,LeaverRuleLibrary.LOAFEATURE, "");
		LogEnablement.isLogDebugEnabled(leaverLogger,"Exit LeaverRuleLibrary::isEligibleForLOA = " + flag+"..."+identityName);
		return flag;
	}
	/**
	 * LTD LifeCycleEvent
	 * @param context
	 * @param previousIdentity
	 * @param newIdentity
	 * @return
	 * @throws Exception
	 */
	public static boolean isEligibleForLTD(SailPointContext context,Identity previousIdentity, Identity newIdentity) throws Exception {
		String identityName=null;
		if(newIdentity!=null)
		{
			identityName=newIdentity.getName();
		}
		LogEnablement.isLogDebugEnabled(leaverLogger,"Enter isEligibleForLTD..."+identityName);
		boolean flag = false;
		flag = TriggersRuleLibrary.allowedForProcess(context,newIdentity, previousIdentity, LeaverRuleLibrary.LTDPROCESS,LeaverRuleLibrary.LTDFEATURE, "");
		LogEnablement.isLogDebugEnabled(leaverLogger,"Exit LeaverRuleLibrary::isEligibleForLTD = " + flag+"..."+identityName);
		return flag;
	}
	/**
	 * Clear Authentication Questions
	 * @param context
	 * @param identityName
	 * @throws GeneralException
	 */
	public static void clearAthenticationQuestionsOnTermination(SailPointContext context, String identityName)throws GeneralException
	{
	    LogEnablement.isLogDebugEnabled(leaverLogger,"Enter clearAthenticationQuestionsOnTermination..."+identityName);
	    if(identityName!=null)
	    {
	        QueryOptions qo = new QueryOptions();
	        qo.add(Filter.eq("name",identityName));
	        Iterator terminatedId = context.search(Identity.class, qo, "id");
	        while (terminatedId.hasNext()) 
	        {
	            Object[] stringsOb = (Object[]) terminatedId.next();
	            Identity terminatedIdentity=null;
	            if(stringsOb!=null && stringsOb.length==1)
	            {
	                String id = (String)stringsOb[0];
	                try {
	                    LogEnablement.isLogDebugEnabled(leaverLogger,"Lock Terminated Identity..."+identityName);
	                    terminatedIdentity=ObjectUtil.lockIdentity(context, id);
	                    if(terminatedIdentity!=null)
	                    {
	                        List terminatedIdentityanswers =terminatedIdentity.getAuthenticationAnswers();
	                        if(terminatedIdentityanswers!=null && terminatedIdentityanswers.size()>0)
	                        {
	                            while(terminatedIdentityanswers.size() != 0 )
	                            {
	                                AuthenticationAnswer answer= (AuthenticationAnswer) terminatedIdentityanswers.get(0);
	                                LogEnablement.isLogDebugEnabled(leaverLogger,"Clear Questions and Answers..."+identityName);
	                                terminatedIdentity.removeAuthenticationAnswer(answer);
                                }
                            }
                        }
	                } catch (GeneralException ge) {
	                    LogEnablement.isLogErrorEnabled(leaverLogger,"Unexpected error while clearing authentication questions for identity..." + identityName);
	                    throw ge;
	                } finally {
	                    try {
	                        LogEnablement.isLogDebugEnabled(leaverLogger,"UnLock Terminated Identity.."+identityName);
	                        // Save, Commit, Unlock
	                        ObjectUtil.unlockIdentity(context, terminatedIdentity);
	                    } catch (Throwable t) {
	                        LogEnablement.isLogErrorEnabled(leaverLogger,"Error Unable to unlock terminated identity..."+identityName);
	                    }
	                }
                }
            }
            LogEnablement.isLogDebugEnabled(leaverLogger,"End clearAthenticationQuestionsOnTermination..."+identityName);
        }
	}

    /**
     * Auto Reject Access Items for Terminated User
     * @param context
     * @param identityName
     * @throws GeneralException
     */
    public static void autoRejectPendingWorkItemsForTerminatedUserAccessRequest(SailPointContext context, String identityName) throws GeneralException
    {
        LogEnablement.isLogDebugEnabled(leaverLogger,"Enter autoRejectPendingWorkItemsForTerminatedUserAccessRequest..."+identityName);
        QueryOptions qo = new QueryOptions();
        Filter f1 = Filter.eq("targetName", identityName);
        qo.addFilter(f1);
        Iterator searchRes = context.search(WorkItem.class, qo, "id");
        WorkItem wi = null;
        List workItemIds = new ArrayList();
        List<String> workItems = new ArrayList();
        if (searchRes!= null) {
            while (searchRes.hasNext()) {
                Object[] stringsOb=(Object[]) searchRes.next();
                if (stringsOb!=null && stringsOb.length==1) {
                    String id = (String)stringsOb[0];
                    if (id!=null) {
                        wi = context.getObjectById(WorkItem.class, id);
                        if (null != wi) {
                            Map<String, String> extAttrs = (Map<String, String>) wi.getAttribute("spExtAttrs");
                            if (null != extAttrs) {
                                String requestType = extAttrs.get("requestType");
                                if (null != requestType && (requestType.equals(LEAVERFEATURE) || requestType.equals(IMMEDIATELEAVERFEATURE))) {
                                    // IIQSR-420 ignore any work items generated by the leaver LCE
                                    LogEnablement.isLogDebugEnabled(leaverLogger,"autoRejectPendingWorkItemsForTerminatedUserAccessRequest: Ignoring Work Item: " + wi.getDescription());
                                    context.decache(wi);
                                   continue;
                                }
                            }
                            String desc = wi.getDescription();
                            if (null != desc) {
                                desc = desc.trim();
                                Identity identity = context.getObjectByName(Identity.class,identityName);
                                LogEnablement.isLogDebugEnabled(leaverLogger,"autoRejectPendingWorkItemsForTerminatedUserAccessRequest: Found Work Item Description: " + desc);
                                if ( Util.nullSafeEq(wi.getType(), WorkItem.Type.Approval) && desc.equals(WORKITEMDISPLAYNAME + identity.getDisplayName())) {
                                    // IIQSR-416 Ignore, handled elsewhere
                                    LogEnablement.isLogDebugEnabled(leaverLogger,"autoRejectPendingWorkItemsForTerminatedUserAccessRequest: Ignoring Work Item: " + desc);
                                    context.decache(wi);
                                    continue;
                                }
                            }
                            workItemIds.add(wi.getIdentityRequestId());
                            workItems.add(id);
                            context.decache(wi);
                        }
                    }
                }
            }
        }
        LogEnablement.isLogDebugEnabled(leaverLogger,"workItemIds..."+workItemIds);
        if (searchRes != null && !workItemIds.isEmpty()) {
            for (String wkItem : workItems) {
                WorkItem work = context.getObjectById(WorkItem.class,wkItem);
                LogEnablement.isLogDebugEnabled(leaverLogger,"Found Work Item for Identity: " + identityName);
                if (work!=null) {
                    LogEnablement.isLogDebugEnabled(leaverLogger, "workName: " + work.getName());
                    LogEnablement.isLogDebugEnabled(leaverLogger, "workId: "+ work.getId());
                    LogEnablement.isLogDebugEnabled(leaverLogger, "workDesc: " + work.getDescription());
                    if (work.getApprovalSet()!=null) {
                        List<ApprovalItem> appItems = work.getApprovalSet().getItems();
                        String identityReqId = work.getIdentityRequestId();
                        LogEnablement.isLogDebugEnabled(leaverLogger,"identityReqId..."+identityReqId);
                        for (ApprovalItem item : appItems) {
                            LogEnablement.isLogDebugEnabled(leaverLogger,"Reject Approval Items..."+identityName);
                            item.reject();
                        }
                        Workflower wf = new Workflower(context);
                        LogEnablement.isLogDebugEnabled(leaverLogger,"Finish Workflow..."+identityName);
                        wf.finish(work);
                    }
                    context.decache(work);
                }
            }
        }
        Util.flushIterator(searchRes);
        LogEnablement.isLogDebugEnabled(leaverLogger,"End autoRejectPendingWorkItemsForTerminatedUserAccessRequest..."+identityName);
    }

	/**
	 * Override Leaver Option Settings based on Population Match
	 * In case an identity belongs to multiple population, only first matched population and leaver option is picked
	 * @param context
	 * @param app
	 * @param identity
	 * @return
	 * @throws GeneralException 
	 */
	public static Map overrideApplicationLeaverSettingsOnPopulationMatch(SailPointContext context, Application app, Identity identity) throws GeneralException
	{
		LogEnablement.isLogDebugEnabled(leaverLogger,"Enter overrideApplicationLeaverSettingsOnPopulationMatch..");
		String identityName=null;
		String appName=null;
		Map extSetting=null;
		if(identity!=null && app!=null)
		{
			identityName=identity.getName();
			appName=app.getName();
			try 
			{
				LogEnablement.isLogDebugEnabled(leaverLogger,"Enter overrideApplicationLeaverSettingsOnPopulationMatch..identityName..."+identityName);
				LogEnablement.isLogDebugEnabled(leaverLogger,"Enter overrideApplicationLeaverSettingsOnPopulationMatch..appName..."+appName);
				if(app!=null && app.getAttributes()!=null && identity!=null && app.getAttributeValue(LeaverRuleLibrary.LEAVEROPTIONSPOPULATION)!=null
						&& app.getAttributeValue(LeaverRuleLibrary.LEAVEROPTIONSPOPULATION) instanceof List )
				{
					List<Map> options=(List) app.getAttributeValue(LeaverRuleLibrary.LEAVEROPTIONSPOPULATION);
					if(options!=null && options.size()>0)
					{
						for(Map option:options)
						{
							if(option!=null && !option.isEmpty() && option.containsKey(LeaverRuleLibrary.OPTIONSPOPULATIONTOKEN)
									&&  (option.containsKey(LeaverRuleLibrary.OPTIONSTERMINATIONEXTENDEDRULETOKEN)||option.containsKey(LeaverRuleLibrary.OPTIONSTOKEN)))
							{
								LogEnablement.isLogDebugEnabled(leaverLogger,"option " + option);
								String populationName = (String) option.get(LeaverRuleLibrary.OPTIONSPOPULATIONTOKEN);
								String extendedRuleName = (String) option.get(LeaverRuleLibrary.OPTIONSTERMINATIONEXTENDEDRULETOKEN);
								String terminationOption = (String) option.get(LeaverRuleLibrary.OPTIONSTOKEN);
								if(populationName!=null)
								{
									populationName=populationName.trim();
								}
								if(extendedRuleName!=null)
								{
									extendedRuleName=extendedRuleName.trim();
								}
								if(terminationOption!=null)
								{
									terminationOption=terminationOption.trim();
								}
								LogEnablement.isLogDebugEnabled(leaverLogger,"populationName " + populationName);
								LogEnablement.isLogDebugEnabled(leaverLogger,"extendedRuleName " + extendedRuleName);
								LogEnablement.isLogDebugEnabled(leaverLogger,"terminationOption " + terminationOption);
								if (populationName != null && (extendedRuleName!=null||terminationOption!=null)) 
								{
									int result = WrapperRuleLibrary.matchPopulation(context, identity, populationName);
									if (result > 0) 
									{
										LogEnablement.isLogDebugEnabled(leaverLogger," Population Matcched.." + "matchPopulation..true.."+populationName);
										extSetting=option;
										LogEnablement.isLogDebugEnabled(leaverLogger,"End overrideApplicationLeaverSettingsOnPopulationMatch..."+identityName+"....overrideExtSetting.."+extSetting);
										break;
									}
									else
									{
										LogEnablement.isLogDebugEnabled(leaverLogger," Population Not Matcched.." + "matchPopulation..false.."+populationName);
									}
								}
							}
						}
					}
				}
			}
			catch (Exception ex)
			{
				LogEnablement.isLogErrorEnabled(leaverLogger,"ERROR: End overrideApplicationLeaverSettingsOnPopulationMatch..."+identityName+"...."+extSetting+".."+ex.getMessage());
			}
		}
		LogEnablement.isLogDebugEnabled(leaverLogger,"End overrideApplicationLeaverSettingsOnPopulationMatch..."+identityName+"...."+extSetting);
		return extSetting;
	}
	/**
	 * Provides the list of Attribute Requests for the entitlements against the input Application Name and Identity!
	 * @param context
	 * @param appName
	 * @param identity
	 * @param nativeId
	 * @return
	 * @throws GeneralException
	 */
	public static List frameworkRemoveAttributeRequestPerApplication(SailPointContext context,String appName, Identity identity,String nativeId) throws GeneralException {
		if(null == identity || null == appName || appName.isEmpty())
		{
			LogEnablement.isLogDebugEnabled(leaverLogger,"Input Identity (or) application name is null or void");
			throw new GeneralException("Input Identity is null or void");
		}
		Iterator rows = null;
		rows = frameworkQueryOptions(context,identity, appName,nativeId);
		List retVal = new ArrayList();
		if (rows != null) {
			retVal = frameworkRemoveAttributeRequests(context,rows, appName);
			Util.flushIterator(rows);
		}
		return retVal;
	}
	/**Get Connected or Disconnected Entitlements ONLY, Roles are Ignore
	 * 
	 * @param context
	 * @param identity
	 * @param appName
	 * @param nativeId
	 * @return
	 * @throws GeneralException
	 */
	public static Iterator frameworkQueryOptions(SailPointContext context, Identity identity, String appName, String nativeId) throws GeneralException 
	{
		LogEnablement.isLogDebugEnabled(leaverLogger,"Enter frameworkQueryOptions");
		ArrayList list = new ArrayList();
		QueryOptions qo = new QueryOptions();
		if (identity != null && appName != null && nativeId != null)
		{
			LogEnablement.isLogDebugEnabled(leaverLogger,"...Build Query Options");
			//EXCLUDE DETECTED AND ASSIGNED ROLES AS IDENTITY ENTITLEMENTS
			qo.addFilter(Filter.ignoreCase(Filter.eq("identity", identity)));
			qo.addFilter(Filter.ignoreCase(Filter.eq("application.name", appName)));
			list.add(Filter.ignoreCase(Filter.eq("aggregationState", "Connected")));
			list.add(Filter.ignoreCase(Filter.eq("aggregationState", "Disconnected")));
			qo.addFilter(Filter.or(list));
			//BELOW QO ENSURES WE ARE GETTING ONLY ENT NOT ROLES
			qo.addFilter(Filter.notnull("value"));
			qo.addFilter(Filter.notnull("name"));
			qo.addFilter(Filter.eq("nativeIdentity", nativeId));
		}
		LogEnablement.isLogDebugEnabled(leaverLogger,"...Perform Search");
		Iterator rows = context.search(IdentityEntitlement.class, qo,"id");
		LogEnablement.isLogDebugEnabled(leaverLogger,"Exit frameworkQueryOptions");        
		return rows;
	}
	/**
	 * Get All Entitlements to Remove for an Application
	 * @param context
	 * @param rows
	 * @param appName
	 * @return
	 * @throws GeneralException
	 */
	public static List frameworkRemoveAttributeRequests(SailPointContext context, Iterator rows, String appName) throws GeneralException 
	{
		ArrayList attrReqs = new ArrayList();
		Application appObj=null;
		if (rows != null) 
		{
			//Get Attribute Name and Expression from Input application
			String removeEntitlementLeaverExceptionExpression=null;
			String attrNameAttrExpression=null;
			String regex =null;
			String operation =null;
			appObj=context.getObjectByName(Application.class, appName);
			if(appObj!=null)
			{
				removeEntitlementLeaverExceptionExpression=(String) appObj.getAttributeValue(LeaverRuleLibrary.LEAVEREXCEPTION);
				if(removeEntitlementLeaverExceptionExpression!=null)
				{
					String[] removeEntitlementLeaverExceptionExpressionArr = removeEntitlementLeaverExceptionExpression.split(LeaverRuleLibrary.LEAVEREXCEPTIONTOKEN);
					if(removeEntitlementLeaverExceptionExpressionArr != null && removeEntitlementLeaverExceptionExpressionArr.length == 2 && Util.isNotNullOrEmpty(removeEntitlementLeaverExceptionExpressionArr[0]) && Util.isNotNullOrEmpty(removeEntitlementLeaverExceptionExpressionArr[1]))
					{
						attrNameAttrExpression=removeEntitlementLeaverExceptionExpressionArr[0];
						regex=removeEntitlementLeaverExceptionExpressionArr[1];
					}
					else if(removeEntitlementLeaverExceptionExpressionArr != null && removeEntitlementLeaverExceptionExpressionArr.length == 3 && Util.isNotNullOrEmpty(removeEntitlementLeaverExceptionExpressionArr[0]) && Util.isNotNullOrEmpty(removeEntitlementLeaverExceptionExpressionArr[1])
							&& Util.isNotNullOrEmpty(removeEntitlementLeaverExceptionExpressionArr[2]))
					{
						attrNameAttrExpression=removeEntitlementLeaverExceptionExpressionArr[0];
						regex=removeEntitlementLeaverExceptionExpressionArr[1];
						operation=removeEntitlementLeaverExceptionExpressionArr[2];
					}
				}
			}
			if(appObj!=null)
			{
				context.decache(appObj);
			}
			Attributes attrs = new Attributes();
			attrs.put("assignment", "true");
			while (rows.hasNext()) 
			{
				String identityEntId=null;
				Object [] identityEntIdArr=(Object[]) rows.next();
				if(identityEntIdArr!=null && identityEntIdArr.length==1)
				{
					if(identityEntIdArr[0]!=null)
					{
						identityEntId = (String)identityEntIdArr[0];
						LogEnablement.isLogDebugEnabled(leaverLogger,"..identityEntId.."+identityEntId);
					}
				}
				IdentityEntitlement item=null;
				if(identityEntId!=null)
				{
					item =context.getObjectById(IdentityEntitlement.class,identityEntId);
				}
				//FOUND ITEM LETS START ATTRIBUTE REQUEST
				AttributeRequest newAttrReq = new AttributeRequest();
				newAttrReq.setArguments(attrs);
				newAttrReq.setOp(ProvisioningPlan.Operation.Remove);
				//ATTRIBUTE REQUEST TO REMOVE ON BY DEFAULT
				boolean addAttributeRequest = true;
				if (item != null) 
				{
					LogEnablement.isLogDebugEnabled(leaverLogger,"...APP NAME = " + item.getAppName());
					Attributes attr = item.getAttributes();
					// ENTITLEMENTS GRANTED BY ROLES NEEDS TO BE REMOVED BY ROLE REMOVAL METHOD
					if ( item.isGrantedByRole()) 
					{
						// THIS CAN HAPPEN VIA IT DETECTION OR VIA REQUIRED/PERMITTED IT ROLES ON A BUSINESS ROLE
						if (attr != null) 
						{
							LogEnablement.isLogDebugEnabled(leaverLogger,"...attr = " + attr);
							// PERMITTED IT ROLES
							if (attr.get("sourceAssignableRoles") != null && attr.get("sourceDetectedRoles") != null) 
							{
								LogEnablement.isLogDebugEnabled(leaverLogger,"...Assigned Role/Permitted Detected Role Entitlement Removed "+item.getValue());
								//INDIRECT ENTITLEMENTS
								addAttributeRequest = false;
							}
							// REMOVE DIRECT ENTITLEMENTS RIGHT AWAY
							else 
							{
								// DIRECT ENTITLEMENTS, BIRTHRIGHT ROLES WILL BE CAPTURED HERE, THEY ARE ASSIGNED BY ROLES, 
								// BUT THEY ARE NOT ON CUBE AS ROLE ASSIGNMENTS
								addAttributeRequest = true;
							}
						}
					}//ITEM NOT GRANTED BY ROLE 
					else 
					{
						addAttributeRequest = true;
					}
				}//ITEM NOT NULL
				if (addAttributeRequest && item!= null ) 
				{
					// CHECK TO SEE IF IDENTITY ENTITLEMENT SHOULD BE REMOVED BY JAVA REGULAR EXPRESSION
					// JAVA REGULAR EXPRESSION CAN BE APPLIED TO ONLY ENTITLEMENTS
					boolean skipRemoval = false;
					if (item.getAppName() != null) 
					{    
						if (item.getName() != null && item.getValue() != null)
						{
							if ( attrNameAttrExpression!=null && operation!=null && operation.length()>0 && regex!=null && item.getName().equalsIgnoreCase(attrNameAttrExpression) && ROADUtil.executeStringComparison((String)item.getValue(),operation,regex)>=1) 
							{
								LogEnablement.isLogDebugEnabled(leaverLogger,"...attrNameAttrExpression = " + attrNameAttrExpression);
								LogEnablement.isLogDebugEnabled(leaverLogger,"...operation = " + operation);
								LogEnablement.isLogDebugEnabled(leaverLogger,"...getValue = " + item.getValue());
								skipRemoval = true;
								LogEnablement.isLogDebugEnabled(leaverLogger,"...skipRemoval = " + skipRemoval);
							}
							else if ( attrNameAttrExpression!=null && regex!=null && item.getName().equalsIgnoreCase(attrNameAttrExpression) && ROADUtil.executeRegex(regex,(String)item.getValue())>=1) 
							{
								skipRemoval = true;
							}
						}
					}
					if(!skipRemoval) 
					{
						if (item.getName() != null) 
						{
							newAttrReq.setName(item.getName());
						}
						if (item.getValue() != null) 
						{
							newAttrReq.setValue(item.getValue());
						}   
						attrReqs.add(newAttrReq);
					}
				}//AttributeRequest True and Item Not Null
				if(item!=null)
				{
					context.decache(item);
				}
			}//End While Loop for Each Row
			Util.flushIterator(rows);
		}  
		else
		{
			LogEnablement.isLogDebugEnabled(leaverLogger,"No Application entitlements found  returning empty list");
		}
		if(appObj!=null)
		{
			context.decache(appObj);
		}
		return attrReqs;
	}
	/**
	 * Invoke Termination X Day Rule
	 * @param context
	 * @param extendedRule
	 * @param allAcctRequests
	 * @param appName
	 * @param requestType
	 * @param spExtAttrs
	 * @param identityName
	 * @param nativeId
	 * @param xDays
	 * @return
	 * @throws GeneralException
	 */
	public static Object invokeXdaysRule(SailPointContext context, String extendedRule, List allAcctRequests, String appName, String requestType, Attributes spExtAttrs, String identityName, String nativeId, String xDays) throws GeneralException {
		LogEnablement.isLogDebugEnabled(leaverLogger,"Enter invokeXdaysRule");
		AccountRequest acctReq = null;
		Object retVal = new Object();
		Rule rule = context.getObjectByName(Rule.class, extendedRule);
		Object obj;
		if (rule == null) 
		{
			throw new GeneralException("Rule is not imported "+extendedRule);
		} 
		else 
		{
			HashMap params = new HashMap();
			params.put("context", context);
			params.put("identityName", identityName);
			params.put("appName", appName);
			params.put("nativeId", nativeId);
			params.put("requestType", requestType);
			params.put("spExtAttrs", spExtAttrs);
			params.put("allAcctRequests", allAcctRequests);
			params.put("xDays", xDays);
			try 
			{
				LogEnablement.isLogDebugEnabled(leaverLogger,"...Run the rule");
				obj = context.runRule(rule, params);                
				if (obj != null && obj instanceof List) 
				{			
					retVal = (List)obj;	
				}
				else if (obj != null && obj instanceof HashMap) 
				{			
					retVal = (HashMap)obj;	
				}
				else if (obj != null && obj instanceof String) 
				{			
					retVal = (String)obj;	
				}
			} 
			catch (Exception re) 
			{
				throw new GeneralException("Error During Rule Launch..."+extendedRule+" "+re.getMessage());
			}
		}
		LogEnablement.isLogDebugEnabled(leaverLogger,"Exit invokeXdaysRule");
		if(rule!=null)
		{
			context.decache(rule);
		}
		return retVal;
	}
	/**
	 * Remove RBAC Business Application Roles that are requested
	 * Remove Birthright Roles that are assigned via Joiner Event, never requested
	 * Remove RBAC Business Application Roles that are assigned via Assignment Rule
	 * @param identity
	 * @param bundleName
	 * @return
	 */
	public static AccountRequest removeRequestedAssignedBundle(SailPointContext context, Identity identity, String bundleName) {
		LogEnablement.isLogDebugEnabled(leaverLogger,"Enter removeRequestedAssignedBundle");
        AccountRequest acctReq = new AccountRequest();
        AttributeRequest attrReq = new AttributeRequest();
        acctReq.setApplication(ProvisioningPlan.APP_IIQ);
   		acctReq.setNativeIdentity(identity.getName());
		acctReq.setOperation(ProvisioningPlan.AccountRequest.Operation.Modify);			
        attrReq.setName(ProvisioningPlan.ATT_IIQ_ASSIGNED_ROLES);
        attrReq.setOperation(ProvisioningPlan.Operation.Remove);
        attrReq.setValue(bundleName);
        Attributes args = new Attributes();
		args.putClean("deassignEntitlements", new Boolean(true));
		args.putClean("negativeAssignment", new Boolean(true));
		args.putClean("assignment", new Boolean(true));
		if (attrReq != null) {
			attrReq.setArguments(args);
		}
		if (acctReq != null) 
        {
        	acctReq.add(attrReq);
        }
        LogEnablement.isLogDebugEnabled(leaverLogger,"Exit removeRequestedAssignedBundle");
       	return acctReq;
    }
}
