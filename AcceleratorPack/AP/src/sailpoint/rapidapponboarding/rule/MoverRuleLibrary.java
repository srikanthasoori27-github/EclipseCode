/*
Copyright © 2018 SailPoint Technologies, Inc. All Rights Reserved.
All logos, text, content, and works of authorship, including but not limited to underlying code, programming or scripting language, designs, and/or graphics,
that are used and/or depicted herein are protected under United States and international copyright and trademark laws and treaties,
and may not be used or reproduced without the prior express written permission of SailPoint Technologies, Inc.
 */
package sailpoint.rapidapponboarding.rule;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import sailpoint.api.IdentityService;
import sailpoint.api.SailPointContext;
import sailpoint.object.Identity;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.ProvisioningPlan.AccountRequest;
import sailpoint.object.ProvisioningPlan.AttributeRequest;
import sailpoint.rapidapponboarding.logger.LogEnablement;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
/**
 * Mover Lifecycle Events
 * @author rohit.gupta
 *
 */
public class MoverRuleLibrary {
	public static final String MOVERPROCESS = "moverProcess";
	public static final String MOVERFEATURE = "MOVER FEATURE";
	private static Log moverLogger = LogFactory.getLog("rapidapponboarding.rules");
	/**
	 * Mover LifeCycleEvent
	 * @param context
	 * @param previousIdentity
	 * @param newIdentity
	 * @param personaEnabled
	 * @return
	 * @throws Exception
	 */
	public static boolean isEligibleForMover(SailPointContext context,
			sailpoint.object.Identity previousIdentity,
			sailpoint.object.Identity newIdentity) throws Exception {
		String identityName=null;
		if(newIdentity!=null)
		{
			identityName=newIdentity.getName();
		}
		LogEnablement.isLogDebugEnabled(moverLogger,"Enter isEligibleForMover: " + identityName);
		boolean flag = false;
		// Persona Package
		String personaEnabled=WrapperRuleLibrary.isPersonaEnabled(context);
		// HR Event - This is a check for rehire
		if (checkIsRehire(context, newIdentity, previousIdentity)) {
			LogEnablement.isLogDebugEnabled(moverLogger,"isEligibleForMover:..Rehire Skipping Mover..."+identityName);
			return flag;
		}
		// HR Event - This is a check for joiner
		if (checkIsJoiner(context, newIdentity, previousIdentity)) {
			LogEnablement.isLogDebugEnabled(moverLogger,"isEligibleForMover:..Joiner Skipping Mover..."+identityName);
			return flag;
		}
		// HR Event - This is a check for leaver
		if (checkIsLeaver(context, newIdentity, previousIdentity)) {
			LogEnablement.isLogDebugEnabled(moverLogger,"isEligibleForMover..Leaver Skipping Mover..."+identityName);
			return flag;
		}
		if (personaEnabled != null && personaEnabled.equalsIgnoreCase("TRUE")) 
		{
			LogEnablement.isLogDebugEnabled(moverLogger,"isEligibleForMover: persona is enabled..."+identityName);
			boolean valid=false;
			valid=WrapperRuleLibrary.validateTriggersBeforePersona( context, newIdentity,
					previousIdentity,  MoverRuleLibrary.MOVERPROCESS);
			if (!valid) {
				LogEnablement.isLogDebugEnabled(moverLogger,"Exit isEligibleForMover false, validateTriggersBeforePersona: false..."+identityName);
				return valid;
			}
			if (WrapperRuleLibrary.checkIsNewRelationshipPersona(context,newIdentity,
					previousIdentity)) {
				LogEnablement.isLogDebugEnabled(moverLogger,"Exit isEligibleForMover false, checkIsNewRelationshipPersona: true (New Relationship) Joiner Skipping Mover.."+identityName);
				return false;
			}
			if (WrapperRuleLibrary.checkIsTerminationPersona(context,newIdentity,
					previousIdentity)) {
				LogEnablement.isLogDebugEnabled(moverLogger,"Exit isEligibleForMover false, checkIsTerminationPersona: true (Termination) Leaver Skipping Mover...."+identityName);
				return false;
			}
			// Partial Leaver
			if (relationshipDropPersona(context, newIdentity, previousIdentity)) {
				LogEnablement.isLogDebugEnabled(moverLogger,"Exit isEligibleForMover true, relationshipDropPersona: true..."+identityName);
				return true;
			}
		}
		// HR Event
		if (checkIsMover(context, newIdentity, previousIdentity)) {
			LogEnablement.isLogDebugEnabled(moverLogger,"Exit isEligibleForMover HR Event: true..."+identityName);
			return true;
		}
		LogEnablement.isLogDebugEnabled(moverLogger,"Exit isEligibleForMover: false...."+identityName);
		return false;
	}
	/**
	 * Is Mover Event HR
	 * @param context
	 * @param newIdentity
	 * @param previousIdentity
	 * @return
	 * @throws GeneralException
	 * @throws ParseException
	 */
	private static boolean checkIsMover(SailPointContext context,
			Identity newIdentity, Identity previousIdentity) throws GeneralException, ParseException {
		boolean flag = false;
                if (ROADUtil.roadFeatureDisabled(context, MoverRuleLibrary.MOVERFEATURE)) {
                    LogEnablement.isLogDebugEnabled(moverLogger,"MoverRuleLibrary.checkIsMover: Mover feature disabled, returning false: " + 
                         MoverRuleLibrary.MOVERFEATURE);
                    return false;
                } else {
                    LogEnablement.isLogDebugEnabled(moverLogger,"MoverRuleLibrary.checkIsMover: Mover feature enabled, checking TriggersRuleLibrary.allowedForProcess...");  
                }
		flag = TriggersRuleLibrary
				.allowedForProcess(context,newIdentity, previousIdentity,
						MoverRuleLibrary.MOVERPROCESS, MoverRuleLibrary.MOVERFEATURE, "");
		return flag;
	}
	/**
	 * Is Joiner
	 * @param context
	 * @param newIdentity
	 * @param previousIdentity
	 * @return
	 * @throws GeneralException
	 * @throws ParseException
	 */
	private static boolean checkIsJoiner(SailPointContext context,
			Identity newIdentity, Identity previousIdentity) throws GeneralException, ParseException {
		boolean flag = false;
                if (ROADUtil.roadFeatureDisabled(context, JoinerRuleLibrary.JOINERFEATURE)) {
                    LogEnablement.isLogDebugEnabled(moverLogger,"MoverRuleLibrary.checkIsJoiner: Joiner feature disabled, returning false: " + 
                         JoinerRuleLibrary.JOINERFEATURE);
                    return false;
                } else {
                    LogEnablement.isLogDebugEnabled(moverLogger,"MoverRuleLibrary.checkIsJoiner: Joiner feature enabled, checking TriggersRuleLibrary.allowedForProcess...");  
                }

		flag =TriggersRuleLibrary
				.allowedForProcess(context,newIdentity, previousIdentity,
						JoinerRuleLibrary.JOINERPROCESS, JoinerRuleLibrary.JOINERFEATURE, "");
		return flag;
	}
	/**
	 * Is Leaver
	 * @param context
	 * @param newIdentity
	 * @param previousIdentity
	 * @return
	 * @throws GeneralException
	 * @throws ParseException
	 */
	private static boolean checkIsLeaver(SailPointContext context,
			Identity newIdentity, Identity previousIdentity) throws GeneralException, ParseException {
		boolean flag = false;
                if (ROADUtil.roadFeatureDisabled(context, LeaverRuleLibrary.LEAVERFEATURE)) {
                    LogEnablement.isLogDebugEnabled(moverLogger,"MoverRuleLibrary.checkIsLeaver: Leaver feature disabled, returning false: " + 
                         LeaverRuleLibrary.LEAVERFEATURE);
                    return false;
                } else {
                    LogEnablement.isLogDebugEnabled(moverLogger,"MoverRuleLibrary.checkIsLeaver: Leaver feature enabled, checking TriggersRuleLibrary.allowedForProcess...");  
                }
		flag = TriggersRuleLibrary
				.allowedForProcess(context,newIdentity, previousIdentity,
						LeaverRuleLibrary.LEAVERPROCESS, LeaverRuleLibrary.LEAVERFEATURE, "");
		return flag;
	}
	/**
	 * Is Rehire
	 * @param context
	 * @param newIdentity
	 * @param previousIdentity
	 * @return
	 * @throws GeneralException
	 * @throws ParseException
	 */
	private static boolean checkIsRehire(SailPointContext context,
			Identity newIdentity, Identity previousIdentity) throws GeneralException, ParseException {
		boolean flag = false;
                if (ROADUtil.roadFeatureDisabled(context, JoinerRuleLibrary.JOINERREHIREFEATURE)) {
                    LogEnablement.isLogDebugEnabled(moverLogger,"MoverRuleLibrary.checkIsRehire: Rehire feature disabled, returning false: " + 
                         JoinerRuleLibrary.JOINERREHIREFEATURE);
                    return false;
                } else {
                    LogEnablement.isLogDebugEnabled(moverLogger,"MoverRuleLibrary.checkIsRehire: Rehire feature enabled, checking TriggersRuleLibrary.allowedForProcess...");  
                }
		flag = TriggersRuleLibrary
				.allowedForProcess(context,newIdentity, previousIdentity,
						JoinerRuleLibrary.JOINERREHIREPROCESS, JoinerRuleLibrary.JOINERREHIREFEATURE, "");
		return flag;
	}
	/**
	 * Persona Drop Event
	 * @param context
	 * @param newIdentity
	 * @param previousIdentity
	 * @return
	 * @throws GeneralException
	 */
	private static boolean relationshipDropPersona(SailPointContext context,
			Identity newIdentity, Identity previousIdentity)
					throws GeneralException {
		LogEnablement.isLogDebugEnabled(moverLogger,"Enter relationshipDropPersona");
		IdentityService idService = new IdentityService(context);
		if (newIdentity == null) {
			LogEnablement.isLogDebugEnabled(moverLogger,"...No new identity.  Return false.");
			LogEnablement.isLogDebugEnabled(moverLogger,"Exit relationshipDropPersona");
			return false;
		}
		if (previousIdentity == null) {
			LogEnablement.isLogDebugEnabled(moverLogger,"...No previous identity.  Return false.");
			LogEnablement.isLogDebugEnabled(moverLogger,"Exit relationshipDropPersona");
			return false;
		}
		if (newIdentity != null && previousIdentity != null) 
		{
			/**
			 * In case, there are no instances of previous relationships, just return false.
			 */
			if (previousIdentity.getAttribute(WrapperRuleLibrary.PERSONARELATIONSHIPS) == null || (previousIdentity.getAttribute(WrapperRuleLibrary.PERSONARELATIONSHIPS)
					instanceof List  && ((List) previousIdentity.getAttribute(WrapperRuleLibrary.PERSONARELATIONSHIPS)).size()<=0) ) 
			{
				return false;
			}
			List<String> differentRelationships = WrapperRuleLibrary
					.getRelationshipChangesPersona(context,newIdentity,
							previousIdentity, true);
			if (differentRelationships != null
					&& !(differentRelationships.isEmpty())) {
				int count = 0;
				List relationshipMessage = new ArrayList();
				LogEnablement.isLogDebugEnabled(moverLogger,"...differentRelationships="
						+ differentRelationships);
				for (String eachDifference : differentRelationships) {
					LogEnablement.isLogDebugEnabled(moverLogger,"...eachDifference=" + eachDifference);
					if (eachDifference != null) {
						if (eachDifference.startsWith(WrapperRuleLibrary.PERSONADROP)) {
							LogEnablement.isLogDebugEnabled(moverLogger,"...DROP");
							count += 1;
							if (eachDifference.contains("[")) {
								eachDifference = eachDifference.replace(
										WrapperRuleLibrary.PERSONADROP, "");
								relationshipMessage
								.add(eachDifference
										.substring(
												0,
												eachDifference
												.indexOf("[") - 1)
										.trim());
							}
						}
					}
				}
				Util.removeDuplicates(relationshipMessage);
				LogEnablement.isLogDebugEnabled(moverLogger,"...relationshipMessage=" + relationshipMessage);
				LogEnablement.isLogDebugEnabled(moverLogger,"...count=" + count);
				if (count >= 1) 
				{
					LogEnablement.isLogDebugEnabled(moverLogger,"...Change in relationship so a Mover");
					WrapperRuleLibrary
					.setRelationshipMessagePersona(context,
							newIdentity.getName(),
							"[RELATIONSHIP CHANGE: "
									+ Util.listToCsv(relationshipMessage)
									+ "]");
					LogEnablement.isLogDebugEnabled(moverLogger,"Exit relationshipDropPersona = true");
					return true;
				}
			}
		}
		LogEnablement.isLogDebugEnabled(moverLogger,"Exit relationshipDropPersona = false");
		return false;
	}
	/**
	 * Launch Attribute Sync Plan
	 * @param context
	 * @param identityName
	 * @param appName
	 * @param plan
	 * @param launchSynchronousWorkflow
	 * @param executePlan
	 * @param map
	 * @param requestType
	 * @param comment
	 * @param workflowName
	 * @return
	 * @throws GeneralException
	 */
	public static List launchPlan(SailPointContext context, String identityName, ProvisioningPlan plan, String launchSynchronousWorkflow, 
			boolean executePlan, HashMap map, String requestType, String comment, String trace, String workflowName) throws GeneralException
	{
		LogEnablement.isLogDebugEnabled(moverLogger,"Start launchPlan");
		LogEnablement.isLogDebugEnabled(moverLogger,"Start launchPlan identityName "+identityName);
		LogEnablement.isLogDebugEnabled(moverLogger,"Start launchPlan launchSynchronousWorkflow "+launchSynchronousWorkflow);
		LogEnablement.isLogDebugEnabled(moverLogger,"Start launchPlan comment "+ comment);
		LogEnablement.isLogDebugEnabled(moverLogger,"Start launchPlan map "+ map);
		LogEnablement.isLogDebugEnabled(moverLogger,"Start launchPlan executePlan "+ executePlan);
		LogEnablement.isLogDebugEnabled(moverLogger,"Start launchPlan requestType "+ requestType);
		boolean result=false;
		List executionResults = new ArrayList();
		if(executePlan && plan!=null && identityName!=null)
		{
			if(launchSynchronousWorkflow!=null && launchSynchronousWorkflow.equalsIgnoreCase("TRUE"))
			{
				result = ROADUtil.launchSynchronousWorkflow(trace,context,plan,identityName,comment,workflowName,requestType,null);
				LogEnablement.isLogDebugEnabled(moverLogger,"Exit launchSynchronousWorkflow "+result);
				boolean runTargetAggregation=false;
				List<AccountRequest> targetAttributesList = WrapperRuleLibrary.getCommonFrameworkTargetAppAttributes(context);
				List<AccountRequest> accountRequests = plan.getAccountRequests();
				if(map!=null && map.get("apBeforeMoverAttrSync")!=null && accountRequests!=null)
				{
					if (targetAttributesList != null && targetAttributesList.size() > 0) 
					{
						for (AccountRequest acctRequest : accountRequests)
						{
							List<AttributeRequest> attrRequests = acctRequest.getAttributeRequests();
							LogEnablement.isLogDebugEnabled(moverLogger,"attrRequests " + attrRequests);
							if (attrRequests != null && attrRequests.size() > 0) 
							{
								for (AttributeRequest attrRequest : attrRequests) 
								{
									if (attrRequest != null) 
									{
										LogEnablement.isLogDebugEnabled(moverLogger,"attrRequest.getName() "+ attrRequest.getName());
									}
									if (attrRequest != null && attrRequest.getName() != null&& targetAttributesList.contains(attrRequest.getName())) 
									{
										runTargetAggregation=true;
										break;
									}
								}
							}
						}
					}
				}
				if(result &&  (map!=null && map.get("runTargetAggregation")!=null && ((String) map.get("runTargetAggregation")).equalsIgnoreCase("TRUE")||runTargetAggregation))
				{
					//Let's Run Target Aggregation
					//Pass in empty native id, native id will be calculated from provisioning policies
					try
					{
						String emptyNativeId=null;
						if(map!=null)
						{
							WrapperRuleLibrary.targetAggregateApplicationNativeIdOnIdentity( context,0, identityName , ((String)map.get("appName")),emptyNativeId);
						}
					}
					catch (Exception ex)
					{
						LogEnablement.isLogErrorEnabled(moverLogger,"Error Before Mover Rule "+identityName);
						executionResults.add("false");
					}
				}
				else if(!result)
				{
					executionResults.add("false");
				}
			}
			else if(executePlan)
			{
				//Use Provisioner
				ROADUtil.launchProvisionerPlan(plan,context);
				//Let's Run Target Aggregation
				//Pass in empty native id, native id will be calculated from provisioning policies
				try
				{
					String emptyNativeId=null;
					if(map!=null)
					{
						WrapperRuleLibrary.targetAggregateApplicationNativeIdOnIdentity( context,0, identityName , ((String)map.get("appName")),emptyNativeId);
					}
				}
				catch (Exception ex)
				{
					LogEnablement.isLogErrorEnabled(moverLogger,"Error Before Mover Rule "+identityName);
					executionResults.add("false");
				}
			}
		}
		return executionResults;
	}
	/**
	 * Is Leaver
	 * @param context
	 * @param newIdentity
	 * @param previousIdentity
	 * @return
	 * @throws Exception 
	 */
	private static boolean checkIsAttributeSync(SailPointContext context,
			Identity newIdentity, Identity previousIdentity) throws Exception {
		boolean flag = false;
		flag = AttributeSyncRuleLibrary.isEligibleForAttributeSync(context,newIdentity, previousIdentity,false);
		return flag;
	}
}
