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
import sailpoint.object.Custom;
import sailpoint.object.Difference;
import sailpoint.object.Identity;
import sailpoint.object.ObjectConfig;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.ProvisioningPlan.AccountRequest;
import sailpoint.object.Application;
import sailpoint.object.Attributes;
import sailpoint.object.ProvisioningPlan.AttributeRequest;
import sailpoint.object.ProvisioningProject;
import sailpoint.object.Workflow;
import sailpoint.object.NativeChangeDetection;
/**
 * Native Change Common Methods for Building Plan 
 * and Exclusion Rules
 * @author rohit.gupta
 *
 */
import sailpoint.rapidapponboarding.logger.LogEnablement;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
public class NativeChangeRuleLibrary {
	private static final Object INCLUDEPRIVACCESSONLY = "includeOnlyPrivilegedAccessNativeChangeCert";
	public static final Object SENDEMAILOPERATIONSNC = "apSendEmailToOperationsNC";
	public static final String NATIVECHANGEFEATURE = "NATIVE CHANGE DETECTION FEATURE";
	public static final String NATIVECHANGETEMPLATE = "NATIVE CHANGE CERTIFICATION TEMPLATE";
	public static final String NATIVECHASNGEPROCESS = "nativeChangeProcess";
	public static final Object POSTNATIVECHANGERULE = "postNativeChangeRule";
	private static Log nativeChangelogger = LogFactory.getLog("rapidapponboarding.rules");
	/**
	 * Include Privileged Access Only Check
	 * @param context
	 * @return
	 * @throws GeneralException
	 */
	public static boolean isOnlyPrivilegdAccessInclusion(SailPointContext context) throws GeneralException
	{
		LogEnablement.isLogDebugEnabled(nativeChangelogger,"Enter isOnlyPrivilegdAccessInclusion..");
		boolean managePrivilegedAccessOnly=false;
		Custom globalExclusionMap = CertificationRuleLibrary.loadCustomMap(context, false);
		boolean privAccessEnabled=ObjectConfigAttributesRuleLibrary.extendedAttrPrivRoleEntEnabled(context);
		if(privAccessEnabled && globalExclusionMap!=null && globalExclusionMap.getAttributes()!=null &&  
				globalExclusionMap.getAttributes().containsKey(NativeChangeRuleLibrary.INCLUDEPRIVACCESSONLY) && 
				globalExclusionMap.getAttributes().get(NativeChangeRuleLibrary.INCLUDEPRIVACCESSONLY) instanceof String &&
				((String)globalExclusionMap.getAttributes().get(NativeChangeRuleLibrary.INCLUDEPRIVACCESSONLY)).equalsIgnoreCase("TRUE"))
		{
			managePrivilegedAccessOnly=true;
			LogEnablement.isLogDebugEnabled(nativeChangelogger,"...managePrivilegedAccessOnly.."+managePrivilegedAccessOnly);
		}
		LogEnablement.isLogDebugEnabled(nativeChangelogger,"End isOnlyPrivilegdAccessInclusion.."+managePrivilegedAccessOnly);
		return managePrivilegedAccessOnly;
	} 
	/**
	 * Get Identity Native Changes from a configuration artifact "Certification-NativeChange"
	 * @param identity
	 * @param applicationName
	 * @param nativeId
	 * @throws GeneralException 
	 */
	public static List getNativeChanges(SailPointContext context,Identity identity, String applicationName, String nativeId) throws GeneralException 
	{
		String identityName=null;
		if(identity!=null)
		{
			identityName=identity.getName();
		}
		LogEnablement.isLogDebugEnabled(nativeChangelogger,"Start getNativeChanges.."+identityName);
		List retVal = new ArrayList();
		String ncdKey = "NCD%" + identity.getName() + "%" + applicationName;
		List ncdValue = new ArrayList();
		LogEnablement.isLogDebugEnabled(nativeChangelogger,"...ncdKey = " + ncdKey);
		if (WrapperRuleLibrary.getNativeChangeKeyValue(context,ncdKey) != null) 
		{
			ncdValue = (List) WrapperRuleLibrary.getNativeChangeKeyValue(context,ncdKey);
		}
		LogEnablement.isLogDebugEnabled(nativeChangelogger,"End getNativeChanges = " + ncdValue+"..."+identityName);
		return ncdValue;
	}
	/**
	 * Get List of Differences
	 * @param context
	 * @param allDifferces
	 * @param managePrivilegedAccessOnly
	 * @param nativeId
	 * @param applicationName
	 * @return
	 * @throws GeneralException
	 */
	public static List getListOfDifferences(SailPointContext context, List<Difference> allDifferces, boolean managePrivilegedAccessOnly, String nativeId, String applicationName) throws GeneralException
	{
		LogEnablement.isLogDebugEnabled(nativeChangelogger,"Start getListOfDifferences..managePrivilegedAccessOnly.."+managePrivilegedAccessOnly);
		LogEnablement.isLogDebugEnabled(nativeChangelogger,"Start getListOfDifferences..nativeId.."+nativeId);
		LogEnablement.isLogDebugEnabled(nativeChangelogger,"Start getListOfDifferences..applicationName.."+applicationName);
		List<Map> allEnts = new ArrayList();
		for (Difference diff : allDifferces) 
		{
			HashMap map = new HashMap();
			if (diff.getAddedValues() != null && diff.getAddedValues() instanceof List && diff.getAddedValues().size()>0 && diff.getAttribute()!=null) 
			{
				List newEntList = new ArrayList();
				List<String> existingAddedList = new ArrayList();
				existingAddedList=diff.getAddedValues();
				LogEnablement.isLogDebugEnabled(nativeChangelogger,"existingAddedList = " + existingAddedList);
				if(existingAddedList!=null)
				{
					for(String existingAddedStr:existingAddedList)
					{
						if(managePrivilegedAccessOnly && ROADUtil.isEntPrivileged(context,diff.getAttribute(),existingAddedStr,applicationName))
						{
							newEntList.add(existingAddedStr);
						}
						else if(!managePrivilegedAccessOnly)
						{
							newEntList.add(existingAddedStr);
						}
					}
				}
				LogEnablement.isLogDebugEnabled(nativeChangelogger,"newEntList = " + newEntList);
				if(newEntList!=null && newEntList.size()>0)
				{
					map.put(nativeId+diff.getAttribute(),newEntList);
				}
			}
			else if(diff.getNewValue() != null && diff.getAttribute()!=null)
			{
				//using new value, however on certification old value will not be restored
				//Single Valued Entitlement or Attribute Level Change
				if(managePrivilegedAccessOnly && ROADUtil.isEntPrivileged(context,diff.getAttribute(),diff.getNewValue(),applicationName))
				{
					map.put(nativeId+diff.getAttribute(),diff.getNewValue());
				}
				else if(!managePrivilegedAccessOnly)
				{
					map.put(nativeId+diff.getAttribute(),diff.getNewValue());
				}
			}
			if(map!=null && !map.isEmpty())
			{
				allEnts.add(map);
			}
		}
		LogEnablement.isLogDebugEnabled(nativeChangelogger,"End getListOfDifferences allEnts = " + allEnts);
		return allEnts;
	}
	/**
	 * Build Native Change Plan
	 * @param context
	 * @param workflow
	 * @param launcher
	 * @return
	 */
	public static ProvisioningPlan buildNativeChangePlan(SailPointContext context, String identityName, Workflow workflow, List<NativeChangeDetection> allNativeChanges, String launcher) 
			throws Exception {
		LogEnablement.isLogDebugEnabled(nativeChangelogger,"Enter buildNativeChangePlan");
		LogEnablement.isLogDebugEnabled(nativeChangelogger,"Enter buildNativeChangePlan identityName..."+identityName);
		Identity identity = context.getObjectByName(Identity.class, identityName);
		IdentityService idService = new IdentityService(context);
		String securityGroupName=null;
		List nativeMaps = ROADUtil.getListOfNativeChangeSettings(context);
		boolean managePrivilegedAccessOnly=false;
		managePrivilegedAccessOnly=NativeChangeRuleLibrary.isOnlyPrivilegdAccessInclusion(context);
		LogEnablement.isLogDebugEnabled(nativeChangelogger,"...managePrivilegedAccessOnly.."+managePrivilegedAccessOnly);
		if (nativeMaps != null && nativeMaps.size() > 0) 
		{
			Map singleMap = (HashMap) nativeMaps.get(0);
			if(singleMap!=null)
			{	
				if (singleMap.get("securityGroupName") != null) 
				{
					securityGroupName=(String) singleMap.get("securityGroupName");
					LogEnablement.isLogDebugEnabled(nativeChangelogger,"...securityGroupName.."+securityGroupName);
				}
			}
		}
		ProvisioningPlan plan=null;
		AccountRequest acctReq=null;
		boolean launchCert = false;
		List appsFound = new ArrayList();
		Identity manager = null;
		
		LogEnablement.isLogDebugEnabled(nativeChangelogger,"...Start building ProvisioningPlan");
		if (identity != null) 
		{
			//Every Native Change is per application
			if (allNativeChanges != null) 
			{
				plan = new ProvisioningPlan();                    
				plan.setIdentity(identity);
				for (NativeChangeDetection singleChange : allNativeChanges)
				{
					LogEnablement.isLogDebugEnabled(nativeChangelogger,"...singleChange=" + singleChange);
					if (singleChange.getDifferences() != null) 
					{
						List<Difference> allDifferces = singleChange.getDifferences();
						Application app;
						String applicationName = "";
						String nativeId = "";
						if (singleChange.getApplicationName() != null && singleChange.getNativeIdentity() != null) 
						{	        		
							applicationName = singleChange.getApplicationName();
							nativeId = singleChange.getNativeIdentity();
							if (allDifferces != null && applicationName != null && nativeId != null) 
							{
								String extSetting = "";
								app = context.getObjectByName(Application.class, applicationName);
								if (app != null) 
								{
									Object extObj = app.getAttributeValue(NATIVECHASNGEPROCESS);
									extSetting = (String) extObj;
									if (extSetting == null) 
									{
										ObjectConfig config = Application.getObjectConfig();
										if (config != null) 
										{
											extSetting = (String) config.getObjectAttribute(NATIVECHASNGEPROCESS).getDefaultValue();
										}
									}
									context.decache(app);
								}
								//Start  Manager Certification Applications
								if (extSetting != null && extSetting.contains("Manager Certification")) 
								{
									if (applicationName != null && !applicationName.equals("") && allDifferces != null && !allDifferces.isEmpty()) 
									{
										//Need to include native identity for ncdKey - there could be multiple accounts
										String ncdKey = "NCD%" + identityName + "%" + applicationName;
										//On set of entitlements / difference
										List allEnts = new ArrayList();
										if(allDifferces!=null && allDifferces.size()>0)
										{
											appsFound.add(app);
											launchCert=true;
										}
										allEnts=NativeChangeRuleLibrary.getListOfDifferences(context,allDifferces, managePrivilegedAccessOnly, nativeId, applicationName);
										LogEnablement.isLogDebugEnabled(nativeChangelogger,"...allEnts=" + allEnts);
										if(allEnts!=null && allEnts.size()>0)
										{
											WrapperRuleLibrary.setNativeChangeKeyValue(context,ncdKey, allEnts);
										}
									}
								}//End  Manager Certification Applications
								//Start Provisioning Plan Application
								for (Difference diff : allDifferces) 
								{                    	
									String newValue = "";
									String oldValue = "";
									String attribute = "";
									if (diff != null)
									{
										if (diff.getNewValue() != null) 
										{
											newValue = diff.getNewValue();
										}                      
										if (diff.getOldValue() != null) 
										{
											oldValue = diff.getOldValue();
										}
										if (diff.getAttribute() != null) 
										{
											attribute = diff.getAttribute();
										}
										if (extSetting != null && extSetting.contains("Automated")) 
										{
											LogEnablement.isLogDebugEnabled(nativeChangelogger,"...Automated");
											boolean ignoreRevocation=false;
											if(extSetting.contains("Manager Certification"))
											{
												LogEnablement.isLogDebugEnabled(nativeChangelogger,"...Ignore Revocation");
												//Manager Certification will take care of revocation
												ignoreRevocation=true;
											}
											else
											{
												LogEnablement.isLogDebugEnabled(nativeChangelogger,"...Automated Recovery and Revocation");
											}
											AccountRequest.Operation oper = AccountRequest.Operation.Modify;
											if (singleChange.getOperation() != null) 
											{
												if (singleChange.getOperation().equals(AccountRequest.Operation.Create))
												{
													LogEnablement.isLogDebugEnabled(nativeChangelogger,"...Delete AccountRequest Revocation");
													oper = AccountRequest.Operation.Delete;
													acctReq = new AccountRequest();
													acctReq.setApplication(applicationName);
													acctReq.setNativeIdentity(nativeId);
													acctReq.setOperation(oper);	
												}
												else if (singleChange.getOperation().equals(AccountRequest.Operation.Delete)) 
												{
													LogEnablement.isLogDebugEnabled(nativeChangelogger,"...Create AccountRequest Recovery");
													oper = AccountRequest.Operation.Create;
													acctReq = new AccountRequest();
													acctReq.setApplication(applicationName);
													acctReq.setNativeIdentity(nativeId);
													acctReq.setOperation(oper);	
												}
												else if (singleChange.getOperation().equals(AccountRequest.Operation.Modify)) 
												{
													LogEnablement.isLogDebugEnabled(nativeChangelogger,"...Modify AccountRequest");
													oper = AccountRequest.Operation.Modify;
													acctReq = new AccountRequest();
													acctReq.setApplication(applicationName);
													acctReq.setNativeIdentity(nativeId);
													acctReq.setOperation(oper);	
												}
												else if (singleChange.getOperation().equals(AccountRequest.Operation.Enable))
												{
													LogEnablement.isLogDebugEnabled(nativeChangelogger,"...Disable AccountRequest");
													oper = AccountRequest.Operation.Disable;
													acctReq = new AccountRequest();
													acctReq.setApplication(applicationName);
													acctReq.setNativeIdentity(nativeId);
													acctReq.setOperation(oper);	
												}
												//Single Value Entitlements
												if (diff.getAddedValues() == null && diff.getRemovedValues()==null && ((oldValue!=null && oldValue.length()>0)|| (newValue!=null && newValue.length()>0))) 
												{
													LogEnablement.isLogDebugEnabled(nativeChangelogger,"...oldValue=" + oldValue);
													LogEnablement.isLogDebugEnabled(nativeChangelogger,"...newValue=" + newValue);
													LogEnablement.isLogDebugEnabled(nativeChangelogger,"...diff.getAddedValues()=" + diff.getAddedValues());
													LogEnablement.isLogDebugEnabled(nativeChangelogger,"...diff.getRemovedValues()=" + diff.getRemovedValues());
													if (plan != null) 
													{
														LogEnablement.isLogDebugEnabled(nativeChangelogger,"...plan=" + plan);
														if (acctReq != null) 
														{
															LogEnablement.isLogDebugEnabled(nativeChangelogger,"...acctReq=" + acctReq);
															AttributeRequest attrReq;
															Attributes attrs = new Attributes();
															attrs.put("assignment", "true");
															//Account is Deleted on Application
															LogEnablement.isLogDebugEnabled(nativeChangelogger,"...acctReq.getOperation()" + acctReq.getOperation());
															if(acctReq.getOperation().equals(AccountRequest.Operation.Create))
															{
																LogEnablement.isLogDebugEnabled(nativeChangelogger,"...Create=Recovery");
																if(oldValue!=null && oldValue.length()>0)
																{
																	attrReq = new AttributeRequest(attribute, ProvisioningPlan.Operation.Set, oldValue);
																	attrReq.setArguments(attrs);
																	acctReq.add(attrReq);
																	//No Account Operation for Managing Privileged Access
																	if(!managePrivilegedAccessOnly)
																	{
																		plan.add(acctReq);
																	}
																}
															}
															//Account is Modified on Application
															else if(acctReq.getOperation().equals(AccountRequest.Operation.Modify))
															{
																LogEnablement.isLogDebugEnabled(nativeChangelogger,"...Modify=Recovery");
																if(oldValue!=null && oldValue.length()>0)
																{
																	LogEnablement.isLogDebugEnabled(nativeChangelogger,"...Modify=oldValue");
																	attrReq = new AttributeRequest(attribute, ProvisioningPlan.Operation.Set, oldValue);
																	attrReq.setArguments(attrs);
																	acctReq.add(attrReq);
																	if(managePrivilegedAccessOnly && ROADUtil.isEntPrivileged(context,attribute,oldValue,applicationName))
																	{
																		plan.add(acctReq);
																	}
																	else if(!managePrivilegedAccessOnly)
																	{
																		plan.add(acctReq);
																	}
																}
																//Old Single Entitlement was blank
																//Not Used for Manager Certification
																else if(newValue!=null && newValue.length()>0 && !ignoreRevocation)
																{
																	LogEnablement.isLogDebugEnabled(nativeChangelogger,"...Modify=newValue=Revocation");
																	attrReq = new AttributeRequest(attribute, ProvisioningPlan.Operation.Remove, newValue);
																	attrReq.setArguments(attrs);
																	acctReq.add(attrReq);
																	if(managePrivilegedAccessOnly && ROADUtil.isEntPrivileged(context,attribute,newValue,applicationName))
																	{
																		plan.add(acctReq);
																	}
																	else if(!managePrivilegedAccessOnly)
																	{
																		plan.add(acctReq);
																	}
																}
															}
														}
													}
												}
												// IF NATIVE CHANGE DETECTED ITEMS ADDED CREATE A PLAN TO HAVE THEM REMOVED
												// This will be handled by Manager certification if selected, so need to ignore
												if (diff.getAddedValues() != null && !ignoreRevocation) 
												{
													List<String> addedValues = diff.getAddedValues();
													if (plan != null) 
													{
														//This is at the account level
														if (acctReq != null) 
														{
															plan.add(acctReq);
														}
													}
													for (String val : addedValues) 
													{
														LogEnablement.isLogDebugEnabled(nativeChangelogger,"...addedValue=" + val);
														AccountRequest addedAcctReq = new AccountRequest();
														addedAcctReq = new AccountRequest(AccountRequest.Operation.Modify, applicationName, null, nativeId);
														addedAcctReq.setNativeIdentity(nativeId);
														AttributeRequest attrReq = new AttributeRequest(attribute, ProvisioningPlan.Operation.Remove, val);
														Attributes attrs = new Attributes();
														attrs.put("assignment", "true");
														attrReq.setArguments(attrs);
														addedAcctReq.add(attrReq);
														if (plan != null) 
														{
															if(managePrivilegedAccessOnly && ROADUtil.isEntPrivileged(context,attribute,val,applicationName))
															{
																plan.add(addedAcctReq);
															}
															else if(!managePrivilegedAccessOnly)
															{
																plan.add(addedAcctReq);
															}
														}
													}
												}
												// IF NATIVE CHANGE DETECTED ITEMS REMOVED CREATE A PLAN TO HAVE THEM ADDED
												if (diff.getRemovedValues() != null) 
												{
													List<String> removedValues = diff.getRemovedValues();
													if (plan != null) 
													{
														if (acctReq != null) 
														{
															plan.add(acctReq);
														}
													}
													for (String val : removedValues) 
													{
														LogEnablement.isLogDebugEnabled(nativeChangelogger,"...removedValue=" + val);
														AccountRequest addedAcctReq = new AccountRequest();
														addedAcctReq = new AccountRequest(AccountRequest.Operation.Modify, applicationName, null, nativeId);
														addedAcctReq.setNativeIdentity(nativeId);
														AttributeRequest attrReq = new AttributeRequest(attribute, ProvisioningPlan.Operation.Add, val);
														Attributes attrs = new Attributes();
														attrs.put("assignment", "true");
														attrReq.setArguments(attrs);
														addedAcctReq.add(attrReq);
														if (plan != null) 
														{
															if(managePrivilegedAccessOnly && ROADUtil.isEntPrivileged(context,attribute,val,applicationName))
															{
																plan.add(addedAcctReq);
															}
															else if(!managePrivilegedAccessOnly)
															{
																plan.add(addedAcctReq);
															}
														}
													}
												}
											}
										}
									}
								}//End Provisioning Plan Application
							}
						}
					}
				}//Iterate through all native change Applications
			}
		}
		if (launchCert) 
		{
			manager = identity.getManager();
			if (null == manager) 
			{
				manager = (Identity) context.getObjectByName(Identity.class, "No Manager Found");
			}
			if (appsFound != null && !appsFound.isEmpty()) 
			{
				Util.removeDuplicates(appsFound);
				if (identity != null && manager != null) 
				{
					String identityNameCert=identity.getName();
					String certifierName=manager.getName();
					//Override Security Officer Group Name instead of Manager
					if(securityGroupName!=null)
					{
						Identity securityGroupNameId = (Identity) context.getObjectByName(Identity.class, securityGroupName);
						if (securityGroupNameId != null) 
						{
							certifierName=securityGroupName;
							context.decache(securityGroupNameId);
						}
					}
					ROADUtil.launchCertification(context, identityNameCert, launcher, certifierName, getRequestTypeName(), appsFound, (String)NativeChangeRuleLibrary.NATIVECHANGETEMPLATE,true);
				}
			}
		}
		if (identity != null ) 
		{
			context.decache(identity);
		}
		if ( manager != null) 
		{
			context.decache(manager);
		}
		if (plan != null) 
		{
			if (plan.getAllRequests() == null)
			{
				plan = null;
			}
		} 
		return plan;
	}
	/**
	 * Return Native Change Request Type
	 * @return
	 */
	public static String getRequestTypeName()
	{
		String requestType = (String) NativeChangeRuleLibrary.NATIVECHANGEFEATURE;
		return requestType;
	}
	/**
	 * Execute Post Native Change Rule
	 * @param context
	 * @param identityName
	 * @param requestType
	 * @param project
	 * @throws GeneralException
	 */
	public static void postNativeChangeRule(SailPointContext context, String identityName, String requestType, ProvisioningProject project) throws GeneralException
	{
		LogEnablement.isLogDebugEnabled(nativeChangelogger,"Start  postNativeChangeRule" );
		LogEnablement.isLogDebugEnabled(nativeChangelogger,"..identityName..."+identityName );
		LogEnablement.isLogDebugEnabled(nativeChangelogger,"..requestType..."+requestType );
		LogEnablement.isLogDebugEnabled(nativeChangelogger,"..project..."+project );
		Map map = new HashMap();
		//Common Configuration
		map = ROADUtil.getCustomGlobalMap(context);
		if(map!=null && map.containsKey(POSTNATIVECHANGERULE))
		{
			String ruleName=(String) map.get(POSTNATIVECHANGERULE);
			if(ruleName!=null && ruleName.length()>0)
			{
				ROADUtil.invokePostExtendedRuleNoObjectReferences(context,null,ruleName, null,  requestType, null, null, null, identityName,null, project);
			}
		}
		LogEnablement.isLogDebugEnabled(nativeChangelogger,"End  postNativeChangeRule" );
	}
}
