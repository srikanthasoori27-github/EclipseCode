/*
Copyright © 2018 SailPoint Technologies, Inc. All Rights Reserved.
All logos, text, content, and works of authorship, including but not limited to underlying code, programming or scripting language, designs, and/or graphics,
that are used and/or depicted herein are protected under United States and international copyright and trademark laws and treaties,
and may not be used or reproduced without the prior express written permission of SailPoint Technologies, Inc.
*/
package sailpoint.rapidapponboarding.rule;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.IdentityService;
import sailpoint.api.SailPointContext;
import sailpoint.object.AccountSelection;
import sailpoint.object.Application;
import sailpoint.object.ApprovalItem;
import sailpoint.object.ApprovalSet;
import sailpoint.object.Attributes;
import sailpoint.object.Custom;
import sailpoint.object.Identity;
import sailpoint.object.Link;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.ProvisioningPlan.AccountRequest;
import sailpoint.object.ProvisioningProject;
import sailpoint.object.ProvisioningTarget;
import sailpoint.object.Rule;
import sailpoint.object.Source;
import sailpoint.object.WorkItem;
import sailpoint.object.Workflow;
import sailpoint.rapidapponboarding.logger.LogEnablement;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.workflow.WorkflowContext;
/**
 * Dynamic Approvals
 * @author rohit.gupta
 *
 */
public class ApprovalRuleLibrary {
	private static Log approvalLogger = LogFactory.getLog("rapidapponboarding.rules");
	private static final String CUSTOM_APPROVAL = "Custom-ApprovalFrameWorkMappings";
	private static final String APPROVAL_TYPES_RULE="Rule-FrameWork-GetApprovalTypes";
	private static final String APPROVAL_ELIGIBLE_TYPE="Eligible Request Type Approvals";
	private static final String APPROVAL_APPROVAL_TYPE="Approval Types";
	private static final Object CHECKREQUIREDAPPROVALRULE = "checkApprovalRequiredRule";
	private static final Object APPROVALREQUIRED = "ApprovalRequired";
	private static final String BATCHREQUESTCAPABILITY = "BatchRequestAdministrator";
	private static final String WORKFLOWSOURCE = "source";
	private static final String WORKFLOWLAUNCHER = "launcher";
	private static final String WORKFLOWREQUESTTYPE = "requestType";
	private static final String WORKFLOWAPPROVALSET = "approvalSet";
	private static final Object FALLBACKAPPROVAL = "FallBackApproval";
	private static final Object FALLBACKAPPROVALAPPNAME = "FallBackApprovalAppName";
	private static final Object RULEWORKFLOW = "workflow";
	private static final Object RULECONTEXT = "context";
	private static final Object RULEITEM = "item";
	private static final Object RULEAPPNAME = "applicationName";
	private static final String BSHREFLECTIONMETHOD = "method";
	private static final String BSHREFLECTIONRULE = "rule";
	private static final Object PARENTTEMPLATE = "template";
	private static final String REQUESTTYPEINDEX = "currentIndex";
	private static final String REQUESTTYPEPROPERTIES = "approvalTypeObj";
	private static final String WORKITEMPROPERTIES = "workItemConfig";
	public static final Object ELECTRONICSIGNATURE = "electronicSignature";
	private static final Object USEDEFAULTWORKITEMCONFIG = "useDefaultWorkItemConfig";
	public static final String DEFAULTWORKITEMCONFIG = "Default WorkItem Config";
	private static final Object WORKITEMDESCRIPTION = "workItemDescription";
	private static final Object WORKITEMDISPLAYNAME = "displayName";
	private static final String IDENTITYDISPLAYNAME = "identityDisplayName";
	private static final String OWNERSRULEKEY = "getApprovalBusApproversRule";
	public static final String  DEFAULTAPPROVALMODEKEY = "defaultApprovalMode";
	private static final String WORKITEMDISPLAYNAMEMIDDLE = " Approval - Account Changes for User: ";
	public static final String NOELECTRONICSIG = "No Electronic Signature Required";
	private static final String WORKFLOWAPPROVALMODE = "approvalMode";
	private static final String WORKFLOWCANCELWORKITEM = "canceledWorkItem";
	private static final String WORKFLOWEXPIREDWORKITEM = "expiredWorkItem";
	private static final String WORKFLOWIDENTITYREQUESTID = "identityRequestId";
	private static final Object NOTIFICATIONNAME = "notifyEmailTemplate";
	public static final Object OPERATIONSBATCHGROUPNAME = "byPassBatchApprovals";
	/**
	 * Get Mappings
	 * @param context
	 * @param entry
	 * @return
	 * @throws GeneralException
	 */
	public static Object getMappingObjectEntry(SailPointContext context, String entry) throws GeneralException{
		Custom mappingObj = context.getObjectByName(Custom.class, ApprovalRuleLibrary.CUSTOM_APPROVAL);
		if (mappingObj == null){
			return null;
		}
		Object entryObj = mappingObj.get(entry);
		context.decache(mappingObj);
		return entryObj;
	}
	/**
	 * Get All Approval Types
	 *
	 * @param context
	 * @param workflow
	 * @param opsGrpName
	 * @return
	 * @throws GeneralException
	 */
	public static List getAllApprovals(SailPointContext context,Workflow workflow,bsh.This thisBeanShellObj,String opsGrpName) throws GeneralException {
		LogEnablement.isLogDebugEnabled(approvalLogger,"Start...getAllApprovals " );
		List returnApprovalTypes = new ArrayList();
		String requestType = (String) workflow.get(ApprovalRuleLibrary.WORKFLOWREQUESTTYPE);
		LogEnablement.isLogDebugEnabled(approvalLogger,"requestType.. "+requestType );
		String approvalReqRuleName=null;
		Map resultMap=null;
		Map methodArgs=null;
		ApprovalSet approvalSet = (ApprovalSet) workflow.get(ApprovalRuleLibrary.WORKFLOWAPPROVALSET);
		List<ApprovalItem> items = approvalSet.getItems();
		if (!skipBatchApprovals(context, workflow,opsGrpName))
		{
			Map customApprovalTypesMap = (Map) getMappingObjectEntry(context,
					ApprovalRuleLibrary.APPROVAL_ELIGIBLE_TYPE);
			if (customApprovalTypesMap != null
					&& customApprovalTypesMap.containsKey(requestType)) {
				List<String> approvalTypesList = (List) customApprovalTypesMap
						.get(requestType);
				LogEnablement.isLogDebugEnabled(approvalLogger,"approvalTypesList.. "+approvalTypesList );
				if (approvalTypesList != null && approvalTypesList.size() > 0) {
					Attributes approvalTypesMap = (Attributes) getMappingObjectEntry(context, ApprovalRuleLibrary.APPROVAL_APPROVAL_TYPE);
					// Iterate through the ApprovalItems here to check if each
					// item requires the current approval type
					for (ApprovalItem item : items) {
						// Iterate through all the potential approval types for
						// the current request type
						for (String approvalType : approvalTypesList) {
							LogEnablement.isLogDebugEnabled(approvalLogger,"Process approvalType... "+approvalType );
							if (approvalTypesMap.get(approvalType) != null) {
								Attributes approvalTypeAttrs = (Attributes) approvalTypesMap
										.get(approvalType);
								// Each approval type has a method which
								// determines if the current approval type is
								// needed for the current approval item
								if (approvalTypeAttrs
										.get(ApprovalRuleLibrary.CHECKREQUIREDAPPROVALRULE) != null) {
									approvalReqRuleName = (String) approvalTypeAttrs
											.get(ApprovalRuleLibrary.CHECKREQUIREDAPPROVALRULE);
									methodArgs = new HashMap();
									methodArgs.put(ApprovalRuleLibrary.RULECONTEXT, context);
									methodArgs.put(ApprovalRuleLibrary.RULEITEM, item);
									methodArgs.put(ApprovalRuleLibrary.RULEWORKFLOW, workflow);
									LogEnablement.isLogDebugEnabled(approvalLogger,"Process approvalReqRuleName... "+approvalReqRuleName );
									resultMap = (Map) runApprovalWorkflowRule(
											context, approvalReqRuleName,
											methodArgs,thisBeanShellObj);
									// Process the result map and see if the
									// approval is required
									LogEnablement.isLogDebugEnabled(approvalLogger,"resultMap... "+resultMap );
									if (resultMap != null
											&& resultMap
											.get(ApprovalRuleLibrary.APPROVALREQUIRED) != null) {
										boolean isApprovalNeeded = Boolean
												.parseBoolean((String)resultMap
														.get(ApprovalRuleLibrary.APPROVALREQUIRED));
										// If this approval type is needed, add
										// it to the list of required approvals
										// only if it doesn't already exist
										if (isApprovalNeeded
												&& !returnApprovalTypes
												.contains(approvalType)) {
											returnApprovalTypes
											.add(approvalType);
										}
									}
									// If the result map contains fallback
									// approval types, then process them
									if (resultMap != null
											&& resultMap
											.get(ApprovalRuleLibrary.FALLBACKAPPROVAL) != null
											&& resultMap
											.get(ApprovalRuleLibrary.FALLBACKAPPROVALAPPNAME) != null) {
										String fallBackApproval = (String) resultMap
												.get(ApprovalRuleLibrary.FALLBACKAPPROVAL);
										String fallBackApprovalAppName = (String) resultMap
												.get(ApprovalRuleLibrary.FALLBACKAPPROVALAPPNAME);
										List<String> fallBackApprovalList = new ArrayList();
										if (fallBackApproval.indexOf(",") > 0) {
											fallBackApprovalList
											.addAll(Util
													.csvToList(fallBackApproval));
										} else {
											fallBackApprovalList
											.add(fallBackApproval);
										}
										for (String fallbackApprovalType : fallBackApprovalList) {
											if (approvalTypesMap
													.get(fallbackApprovalType) != null) {
												Attributes fallbackApprovalTypeAttrs = (Attributes) approvalTypesMap
														.get(fallbackApprovalType);
												// Each approval type has a
												// method which determines if
												// the current approval type is
												// needed for the current
												// approval item
												if (fallbackApprovalTypeAttrs
														.get(ApprovalRuleLibrary.CHECKREQUIREDAPPROVALRULE) != null) {
													approvalReqRuleName = (String) fallbackApprovalTypeAttrs
															.get(ApprovalRuleLibrary.CHECKREQUIREDAPPROVALRULE);
													methodArgs = new HashMap();
													methodArgs.put(ApprovalRuleLibrary.RULECONTEXT,
															context);
													methodArgs
													.put(ApprovalRuleLibrary.RULEITEM, item);
													methodArgs
													.put(ApprovalRuleLibrary.RULEAPPNAME,
															fallBackApprovalAppName);
													methodArgs.put(ApprovalRuleLibrary.RULEWORKFLOW,
															workflow);
													resultMap = (Map) runApprovalWorkflowRule(
															context,
															approvalReqRuleName,
															methodArgs,thisBeanShellObj);
													// Process the result map
													// and see if the approval
													// is required
													if (resultMap != null
															&& resultMap
															.get(ApprovalRuleLibrary.APPROVALREQUIRED) != null) {
														boolean isApprovalNeeded = Boolean
																.parseBoolean((String)resultMap
																		.get(ApprovalRuleLibrary.APPROVALREQUIRED));
														// If this approval type
														// is needed, add it to
														// the list of required
														// approvals only if it
														// doesn't already exist
														if (isApprovalNeeded
																&& !returnApprovalTypes
																.contains(fallbackApprovalType)) {
															returnApprovalTypes
															.add(fallbackApprovalType);
														}
													}
												}
											}
										}
									}
								} else if (!returnApprovalTypes
										.contains(approvalType)) {
									returnApprovalTypes.add(approvalType);
								}
							}
						}
					}
				}
			}
		}
		LogEnablement.isLogDebugEnabled(approvalLogger,"End...getAllApprovals ... " + returnApprovalTypes);
		return returnApprovalTypes;
	}
	/**
	 * Skip Batch Approval
	 *
	 * @param context
	 * @param workflow
	 * @param opsGrpName
	 * @return
	 * @throws GeneralException
	 */
	public static boolean skipBatchApprovals(SailPointContext context,
			Workflow workflow, String opsGrpName) throws GeneralException {
		boolean skipApprovals = false;
		LogEnablement.isLogDebugEnabled(approvalLogger,"Start...skipBatchApprovals ... " + opsGrpName);
		String source = (String) workflow.get(ApprovalRuleLibrary.WORKFLOWSOURCE);
		// If the requester has the BatchRequestAdministrator capability and is
		// member of Operations group,
		// and the source is Batch, then bypass all approvals
		if (opsGrpName!=null && source != null && source.equalsIgnoreCase(Source.Batch.toString()))
		{
			if(opsGrpName!=null && opsGrpName.equalsIgnoreCase("Operations"))
			{
				opsGrpName=acceleratorPackGetApproversOperationsGroupName(context);
			}
			if(opsGrpName!=null)
			{
				Identity opsWorkgroup = null;
				Identity launcherIdentity = context.getObjectByName(Identity.class,
						(String)workflow.get(ApprovalRuleLibrary.WORKFLOWLAUNCHER));
				if (launcherIdentity != null
						&& launcherIdentity.getCapabilityManager() != null) {
					opsWorkgroup = context.getObjectByName(Identity.class,
							opsGrpName);
					if (launcherIdentity.isInWorkGroup(opsWorkgroup)
							&& launcherIdentity.getCapabilityManager()
							.hasCapability(ApprovalRuleLibrary.BATCHREQUESTCAPABILITY)) {
						skipApprovals = true;
					}
				}
				if (launcherIdentity != null) {
					context.decache(launcherIdentity);
				}
				if (opsWorkgroup != null) {
					context.decache(opsWorkgroup);
				}
			}
		}
		LogEnablement.isLogDebugEnabled(approvalLogger,"End...skipBatchApprovals ... " + skipApprovals);
		return skipApprovals;
	}
	/**
	 * Get Possible Approval Types
	 * @param context
	 * @param workflow
	 * @return
	 * @throws GeneralException
	 */
	public static List getPossibleApprovalTypes(SailPointContext context, Workflow workflow) throws GeneralException{
		LogEnablement.isLogDebugEnabled(approvalLogger,"Start...getPossibleApprovalTypes ... " );
		Map params = new HashMap();
		params.put(ApprovalRuleLibrary.RULEWORKFLOW, workflow);
		Object obj= runRuleToGetPossibleApprovalTypes(context, ApprovalRuleLibrary.APPROVAL_TYPES_RULE, params);
		if(obj!=null && obj instanceof List)
		{
			List objList=(List)obj;
			LogEnablement.isLogDebugEnabled(approvalLogger,"End...getPossibleApprovalTypes ... " + objList);
			return objList;
		}
		LogEnablement.isLogDebugEnabled(approvalLogger,"End...getPossibleApprovalTypes ... " );
		return null;
	}
	/**
	 * Run Rule to Get Possible Approval Types
	 * This goes though all conditional rules too
	 * @param context
	 * @param ruleName
	 * @param params
	 * @param thisBshObject
	 * @return
	 * @throws GeneralException
	 */
	public static Object runRuleToGetPossibleApprovalTypes(SailPointContext context, String ruleName, Map params) throws GeneralException{
		LogEnablement.isLogDebugEnabled(approvalLogger,"Start...runRuleToGetPossibleApprovalTypes ... " );
		Object possibleApprovalTypes = null;
		Rule rule = context.getObjectByName(Rule.class, ruleName);
		if (rule == null){
			return null;
		}
		possibleApprovalTypes = context.runRule(rule, params);
		context.decache(rule);
		LogEnablement.isLogDebugEnabled(approvalLogger,"End...runRuleToGetPossibleApprovalTypes ... "+possibleApprovalTypes );
		return possibleApprovalTypes;
	}
	/**
	 * Use Bsh InvokeMethod to execute Methods
	 * @param context
	 * @param ruleName
	 * @param params
	 * @param thisBshInstance
	 * @return
	 * @throws GeneralException
	 */
	public static Object runApprovalWorkflowRule(SailPointContext context, String ruleName, Map params, bsh.This thisBshInstance) throws GeneralException{
		LogEnablement.isLogDebugEnabled(approvalLogger,"Start...runApprovalWorkflowRule ... " );
		Object result = null;
		boolean runRule = true;
		if (ruleName.indexOf(":") > 0){
			String[] split = ruleName.split(":");
			String pref = split[0];
			if (ApprovalRuleLibrary.BSHREFLECTIONMETHOD.equalsIgnoreCase(pref)){
				runRule = false;
			}
			if (ApprovalRuleLibrary.BSHREFLECTIONMETHOD.equalsIgnoreCase(pref)  || ApprovalRuleLibrary.BSHREFLECTIONRULE.equalsIgnoreCase(pref)){
				ruleName = split[1];
			}
		}
		if (runRule){
			Rule rule = context.getObjectByName(Rule.class, ruleName);
			if (rule == null){
				return null;
			}
			result = context.runRule(rule, params);
			context.decache(rule);
		}else {
			Object[] methodParams = {context, params};
			try {
				result = thisBshInstance.invokeMethod(ruleName, methodParams);
			} catch (Exception e){
				LogEnablement.isLogErrorEnabled(approvalLogger,"Exception with value of " + ruleName + ": " + e);
				result = null;
			}
		}
		LogEnablement.isLogDebugEnabled(approvalLogger,"End...runApprovalWorkflowRule ... "+result );
		return result;
	}
	/**
	 * This method converts In flight Create Requests to Modify Requests
	 * In case link exists with same native id
	 * @param workflow
	 * @param context
	 * @throws GeneralException
	 */
	public static void recalculateAccountSelection(Workflow workflow, SailPointContext context) throws GeneralException
	{
		LogEnablement.isLogDebugEnabled(approvalLogger,"Start...recalculateAccountSelection ... " );
		if(workflow!=null)
		{
			ProvisioningProject project = (ProvisioningProject) workflow.get("project");
			if(project!=null)
			{
				List<ProvisioningTarget> provisioningTargets = project.getProvisioningTargets();
				List<ProvisioningPlan> planList = project.getPlans();
				//Converts Inflight Create Requests to Modify Requests, incase link exists wih same native id
				if(provisioningTargets != null) {
					for(ProvisioningTarget provisioningTarget : provisioningTargets) {
						List<AccountSelection> accountSelections = provisioningTarget.getAccountSelections();
						if(accountSelections != null) {
							IdentityService is = new IdentityService(context);
							for(AccountSelection accountSelection : accountSelections) {
								if(accountSelection.isDoCreate()) {
									String identityName = project.getIdentity();
									String applicationName = accountSelection.getApplicationName();
									if(identityName != null && applicationName != null) {
										Identity identity = context.getObjectByName(Identity.class, identityName);
										Application application = context.getObjectByName(Application.class, applicationName);
										if(identity != null && application != null) {
											List<Link> links = is.getLinks(identity, application);
											if(links != null && links.size() > 0) {
												if(planList != null) {
													String newNativeIdentity = "";
													//Obtain the nativeIdentity of the new account that would be created based on the current AccountSelection
													for(ProvisioningPlan plan : planList) {
														List<AccountRequest> acctRequestList = plan.getAccountRequests(applicationName);
														if(acctRequestList != null && acctRequestList.size() > 0) {
															for(AccountRequest acctReq : acctRequestList) {
																if(acctReq.getOperation().equals(ProvisioningPlan.AccountRequest.Operation.Create) && acctReq.getNativeIdentity() != null) {
																	newNativeIdentity = acctReq.getNativeIdentity();
																	break;
																}
															}
														}
													}
													if(Util.isNotNullOrEmpty(newNativeIdentity)) {
														//Loop thru all Links, if the nativeIdentity of the account that would be created matches with any of
														//the existing Link's nativeIdentity, then use that account.
														//If no matches are found then create a new account
														for(Link link : links) {
															if(link.getNativeIdentity().equals(newNativeIdentity)) {
																LogEnablement.isLogDebugEnabled(approvalLogger,"Recalculated...recalculateAccountSelection ... " );
																accountSelection.setSelection(link.getNativeIdentity());
																accountSelection.addAccountInfo(link);
																accountSelection.setDoCreate(false);
																break;
															}
														}
													}
												}
											}
											context.decache(identity);
											context.decache(application);
										}
									}
								}
							}
						}
					}
				}
			}
		}
		LogEnablement.isLogDebugEnabled(approvalLogger,"End...recalculateAccountSelection ... " );
	}
	/**
	 * Iterate through all request types
	 * @param context
	 * @param workflow
	 * @param approvalTypes
	 * @param thisBeanShell
	 * @return
	 * @throws GeneralException
	 */
	public static String roadGetNextApprovalType(SailPointContext context,Workflow workflow, List approvalTypes,bsh.This thisBeanShell) throws GeneralException{
		LogEnablement.isLogDebugEnabled(approvalLogger,"Start...roadGetNextApprovalType ... " );
		int ci = 0;
  		String currentIndex = (String) workflow.get(ApprovalRuleLibrary.REQUESTTYPEINDEX);
		if (currentIndex == null){
			currentIndex = Integer.toString(ci);
		} else {
			ci = Integer.parseInt(currentIndex);
			ci += 1;
			currentIndex = Integer.toString(ci);
		}
		workflow.put(ApprovalRuleLibrary.REQUESTTYPEINDEX, currentIndex);
		String nextType = null;
		if (ci < approvalTypes.size()){
			nextType = (String) approvalTypes.get(ci);
		}
		if (nextType != null){
			addRequestTypeProperties(context, workflow, nextType,thisBeanShell);
		}
		LogEnablement.isLogDebugEnabled(approvalLogger,"End...roadGetNextApprovalType ... " +nextType);
		return nextType;
	}
	/**
	 * Add Request Type Properties and Parent Template Properties
	 * @param context
	 * @param workflow
	 * @param approvalType
	 * @param thisBeanShell
	 * @throws GeneralException
	 */
	public static void addRequestTypeProperties(SailPointContext context, Workflow workflow, String approvalType, bsh.This thisBeanShell) throws GeneralException{
		LogEnablement.isLogDebugEnabled(approvalLogger,"Start...addRequestTypeProperties ... " );
		/**
		 * First Get Request Type Properties
		 */
		Attributes approvalTypeObj = getApprovalTypeAttributes(context, approvalType);
		if (approvalTypeObj == null){
			return;
		}
		/**
		 * Get Request Type -  Template - Properties
		 */
		String template = (String) approvalTypeObj.get(ApprovalRuleLibrary.PARENTTEMPLATE);
		if (template != null){
			Attributes templateObj = getApprovalTypeAttributes(context, template);
			addTemplateProperties(context,approvalTypeObj, templateObj);
		}
		workflow.put(ApprovalRuleLibrary.REQUESTTYPEPROPERTIES, approvalTypeObj);
		/**
		 * Add Work Item Properties to Workflow
		 */
		addGlobalWorkItemConfigProperties(context, workflow, approvalTypeObj,thisBeanShell);
		LogEnablement.isLogDebugEnabled(approvalLogger,"End...addRequestTypeProperties ... " );
	}
	/**
	 * Add Template properties in case Request Type doesn't have that property
	 * @param context
	 * @param approvalTypeObj
	 * @param templateObj
	 * @throws GeneralException
	 */
	public static void addTemplateProperties(SailPointContext context,Attributes approvalTypeObj, Attributes templateObj) throws GeneralException
	{
		LogEnablement.isLogDebugEnabled(approvalLogger,"Start...addTemplateProperties ... " );
		List<String> keys = templateObj.getKeys();
		if (keys != null){
			for (String key : keys){
				if (!approvalTypeObj.containsKey(key)){
					approvalTypeObj.put(key,templateObj.get(key));
				}
			}
		}
		LogEnablement.isLogDebugEnabled(approvalLogger,"End...addTemplateProperties ... " );
	}
	/**
	 * Add Global Work Item Configuration Properties - if configured
	 * @param context
	 * @param workflow
	 * @param approvalTypeObj
	 * @param thisBeanShell
	 * @throws GeneralException
	 */
	public static void addGlobalWorkItemConfigProperties(SailPointContext context, Workflow workflow, Attributes approvalTypeObj, bsh.This thisBeanShell) throws GeneralException{
		LogEnablement.isLogDebugEnabled(approvalLogger,"Start...addGlobalWorkItemConfigProperties ... " );
		String approvalMode = (String)approvalTypeObj.get(DEFAULTAPPROVALMODEKEY);
		String useDefaultWIConfig = (String)approvalTypeObj.get(ApprovalRuleLibrary.USEDEFAULTWORKITEMCONFIG);
		/**
		 * This is used to filter rejects
		 */
		workflow.put(ApprovalRuleLibrary.WORKFLOWAPPROVALMODE, approvalMode);
		/**
		 * Get Work Item Properties from Request Type in case useDefaultWorkItemConfig is OFF
		 */
		Attributes wiConfig = null;
		if (useDefaultWIConfig != null && !Boolean.parseBoolean(useDefaultWIConfig)){
			wiConfig = (Attributes)approvalTypeObj.get(ApprovalRuleLibrary.WORKITEMPROPERTIES);
		} else {
			wiConfig = (Attributes) ApprovalRuleLibrary.getMappingObjectEntry(context, ApprovalRuleLibrary.DEFAULTWORKITEMCONFIG);
		}
		String elecSig = null;
		if (approvalTypeObj.containsKey(ApprovalRuleLibrary.ELECTRONICSIGNATURE)){
			elecSig = (String)approvalTypeObj.get(ApprovalRuleLibrary.ELECTRONICSIGNATURE);
		}
		if (elecSig == null || elecSig.trim().equals(""))
		{
			elecSig=(String) wiConfig.get(ApprovalRuleLibrary.ELECTRONICSIGNATURE);
			if (elecSig == null || elecSig.trim().equals(""))
			{
			elecSig = ApprovalRuleLibrary.NOELECTRONICSIG;
			}
		}
		wiConfig.put(ApprovalRuleLibrary.ELECTRONICSIGNATURE, elecSig);
		String identityDisplayName = (String) workflow.get(ApprovalRuleLibrary.IDENTITYDISPLAYNAME);
		String workItemDisplayName = (String) approvalTypeObj.get(ApprovalRuleLibrary.WORKITEMDISPLAYNAME);
		//Get Notification Name from Request Type - if Defined
		String notificationName = (String) approvalTypeObj.get(ApprovalRuleLibrary.NOTIFICATIONNAME);
		if(notificationName!=null && notificationName.length()>0)
		{
		 wiConfig.put(ApprovalRuleLibrary.NOTIFICATIONNAME, notificationName);
		}
		String wiDesc = workItemDisplayName + ApprovalRuleLibrary.WORKITEMDISPLAYNAMEMIDDLE + identityDisplayName;
		wiConfig.put(ApprovalRuleLibrary.WORKITEMDESCRIPTION, wiDesc);
		workflow.put(ApprovalRuleLibrary.WORKITEMPROPERTIES, wiConfig);
		LogEnablement.isLogDebugEnabled(approvalLogger,"End...addGlobalWorkItemConfigProperties ... " );
	}
	/**
	 * Get Request Type Approval Properties
	 * @param context
	 * @param approvalType
	 * @return
	 * @throws GeneralException
	 */
	public static Attributes getApprovalTypeAttributes(SailPointContext context, String approvalType) throws GeneralException{
		LogEnablement.isLogDebugEnabled(approvalLogger,"Start...getApprovalTypeAttributes ... " );
		Attributes retVal = null;
		Attributes entryObj = (Attributes) ApprovalRuleLibrary.getMappingObjectEntry(context,ApprovalRuleLibrary.APPROVAL_APPROVAL_TYPE);
		if (entryObj == null){
			return null;
		}
		retVal = (Attributes) entryObj.get(approvalType);
		LogEnablement.isLogDebugEnabled(approvalLogger,"End...getApprovalTypeAttributes ... "+retVal );
		return retVal;
	}
	/**
	 * Initialize Workflow Variable
	 * @param context
	 * @param workflow
	 * @param entry
	 * @return
	 */
	public static void  cancelorExpiredWorkItemState(WorkflowContext wfcontext, Workflow workflow, WorkItem item)
	{
		LogEnablement.isLogDebugEnabled(approvalLogger,"Start...cancelorExpiredWorkItemState ... " );
		try
		{
			if(wfcontext!=null)
			{
				Attributes args = wfcontext.getArguments();
				String irId = Util.getString(args,ApprovalRuleLibrary.WORKFLOWIDENTITYREQUESTID);
				if ( irId == null )
				{
				    WorkflowContext top = wfcontext.getRootContext();
				    irId = (String)top.getVariable(ApprovalRuleLibrary.WORKFLOWIDENTITYREQUESTID);
				}
			}
		}
		catch (Exception e)
		{
			LogEnablement.isLogErrorEnabled(approvalLogger,"Approval Exception.."+e.getMessage());
		}
		if (item != null)
		{
       		if(item.getState() != null && item.getState().equals(WorkItem.State.Canceled))
       		{
       			LogEnablement.isLogDebugEnabled(approvalLogger,"Canceled...cancelorExpiredWorkItemState ... " );
        		workflow.put(ApprovalRuleLibrary.WORKFLOWCANCELWORKITEM,true);
        	}
        	if((item.getState() != null && item.getState().equals(WorkItem.State.Expired)) || item.isExpired())
        	{
        		LogEnablement.isLogDebugEnabled(approvalLogger,"Expired...cancelorExpiredWorkItemState ... " );
        		workflow.put(ApprovalRuleLibrary.WORKFLOWEXPIREDWORKITEM,true);
      		}
		}
		LogEnablement.isLogDebugEnabled(approvalLogger,"End...cancelorExpiredWorkItemState ... " );
	}
	/**
	 * Get Request Type Owners Rule Property Value
	 * @param context
	 * @param workflow
	 * @param entry
	 * @return
	 */
	public static String getRequestTypePropertyValue(SailPointContext context, Workflow workflow, String entry){
		LogEnablement.isLogDebugEnabled(approvalLogger,"Start...getRequestTypePropertyValue ... " );
		String val = null;
		Attributes approvalTypeObj = (Attributes) workflow.get(ApprovalRuleLibrary.REQUESTTYPEPROPERTIES);
		if (approvalTypeObj == null){
			return null;
		}
		val = (String)approvalTypeObj.get(entry);
		LogEnablement.isLogDebugEnabled(approvalLogger,"End...getRequestTypePropertyValue ... "+ val);
		return val;
	}
	/**
	 * Get Approval Work Item Owners
	 * @param context
	 * @param workflow
	 * @param approvalType
	 * @param fallbackApprover
	 * @param thisBeanShell
	 * @return
	 * @throws GeneralException
	 */
	public static Object getWorkItemOwners(SailPointContext context, Workflow workflow, String approvalType, String fallbackApprover,bsh.This thisBeanShell) throws GeneralException{
		LogEnablement.isLogDebugEnabled(approvalLogger,"Start...getWorkItemOwners ... " );
		String val = null;
		String ruleName = "";
		String rn = getRequestTypePropertyValue(context, workflow, ApprovalRuleLibrary.OWNERSRULEKEY);
		ruleName =  (rn == null) ? ruleName : rn;
		Map params = new HashMap();
		params.put("workflow", workflow);
		Object approvers = ApprovalRuleLibrary.runApprovalWorkflowRule(context, ruleName, params,thisBeanShell);
		LogEnablement.isLogDebugEnabled(approvalLogger,"End...getWorkItemOwners ... "+ approvers);
		return approvers;
	}
	/**
	 * Is Electronic Signature Enabled
	 * @param workflow
	 * @return
	 */
	public static boolean eSigDisabled(Workflow workflow)
	{
		LogEnablement.isLogDebugEnabled(approvalLogger,"Start...eSigDisabled ... " );
		boolean disabledElectronicSig=true;
		if(workflow!=null)
		{
			Attributes wiConfig =(Attributes) workflow.get(ApprovalRuleLibrary.WORKITEMPROPERTIES);
			LogEnablement.isLogDebugEnabled(approvalLogger,"..wiConfig..."+wiConfig);
			if(wiConfig!=null)
			{
				String elecSig=(String) wiConfig.get(ApprovalRuleLibrary.ELECTRONICSIGNATURE);
				LogEnablement.isLogDebugEnabled(approvalLogger,"..elecSig..."+elecSig);
				if(elecSig!=null && !elecSig.equalsIgnoreCase(ApprovalRuleLibrary.NOELECTRONICSIG))
				{
					disabledElectronicSig=false;
				}
			}
			else
			{
				LogEnablement.isLogDebugEnabled(approvalLogger,"..wiconfig will be null for getting potential approval types..."+wiConfig);
				disabledElectronicSig=false;
			}
		}
		LogEnablement.isLogDebugEnabled(approvalLogger,"End...eSigDisabled ... "+ disabledElectronicSig);
		return disabledElectronicSig;
	}

	/**
	 * Get Operations Approver Group Name
	 *
	 * @param context
	 * @return
	 * @throws GeneralException
	 */
	public static String acceleratorPackGetApproversOperationsGroupName(SailPointContext context) throws GeneralException {
		LogEnablement.isLogDebugEnabled(approvalLogger,"Enter acceleratorPackGetApproversOperationsGroupName");
		Map commonMap = ROADUtil.getCustomGlobalMap(context);
		String operationsApprover = "";
		if (commonMap != null
				&& commonMap.containsKey(ApprovalRuleLibrary.OPERATIONSBATCHGROUPNAME)) {
			operationsApprover = (String) commonMap.get(ApprovalRuleLibrary.OPERATIONSBATCHGROUPNAME);
		}
		LogEnablement.isLogDebugEnabled(approvalLogger,"End acceleratorPackGetApproversOperationsGroupName." + operationsApprover);
		return operationsApprover;
	}
}
