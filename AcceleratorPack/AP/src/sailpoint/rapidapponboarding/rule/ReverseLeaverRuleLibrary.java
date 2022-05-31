/*
Copyright © 2018 SailPoint Technologies, Inc. All Rights Reserved.
All logos, text, content, and works of authorship, including but not limited to underlying code, programming or scripting language, designs, and/or graphics,
that are used and/or depicted herein are protected under United States and international copyright and trademark laws and treaties,
and may not be used or reproduced without the prior express written permission of SailPoint Technologies, Inc.
 */
package sailpoint.rapidapponboarding.rule;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.ObjectUtil;
import sailpoint.api.RequestManager;
import sailpoint.api.SailPointContext;
import sailpoint.object.Application;
import sailpoint.object.Attributes;
import sailpoint.object.Bundle;
import sailpoint.object.Identity;
import sailpoint.object.IdentityRequest;
import sailpoint.object.IdentitySnapshot;
import sailpoint.object.LinkSnapshot;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.ProvisioningPlan.AccountRequest;
import sailpoint.object.ProvisioningPlan.AttributeRequest;
import sailpoint.object.ProvisioningProject;
import sailpoint.object.Request;
import sailpoint.object.RequestDefinition;
import sailpoint.object.Workflow;
import sailpoint.rapidapponboarding.logger.LogEnablement;
import sailpoint.tools.GeneralException;
/**
 * Reverse Leaver Rule Library
 * @author rohit.gupta
 *
 */
public class ReverseLeaverRuleLibrary {
	static final String REVERSELEAVERPROCESS = "reverseleaverProcess";
	public static final String REVERSELEAVERFEATURE = "REVERSE LEAVER FEATURE";
	public static final String REVERSELEAVEROPTIONSPOPULATION = "apPopulationReverseLeaverOptions";
	public static final String OPTIONSREVPOPULATIONTOKEN = "apReversePopulation";
	public static final String OPTIONSREVTOKEN = "apReverseTerminationOption";
	public static final String OPTIONSREVTERMINATIONEXTENDEDRULETOKEN = "apReverseTerminationExtendedRule";
	private static Log reverseLogger = LogFactory.getLog("rapidapponboarding.rules");
	private static final int WORKFLOW_DELAY = 5; // seconds delay to start workflows

	/**
	 *
	 * Reverse Leaver LifeCycleEvent
	 * @param context
	 * @param previousIdentity
	 * @param newIdentity
	 * @return
	 * @throws Exception
	 */
	public static boolean isEligibleForReverseLeaver(SailPointContext context,
			sailpoint.object.Identity previousIdentity,
			sailpoint.object.Identity newIdentity)
					throws Exception {
		String identityName=null;
		if(newIdentity!=null)
		{
			identityName=newIdentity.getName();
		}
		LogEnablement.isLogDebugEnabled(reverseLogger,"Enter isEligibleForReverseLeaver..."+identityName);
		boolean flag = false;
		String personaEnabled=WrapperRuleLibrary.isPersonaEnabled(context);
		if (personaEnabled != null && personaEnabled.equalsIgnoreCase("TRUE")) {
			boolean valid=false;
			valid=WrapperRuleLibrary.validateTriggersBeforePersona( context, newIdentity,
					previousIdentity,  ReverseLeaverRuleLibrary.REVERSELEAVERPROCESS);
			if (!valid) {
				return valid;
			}
			if (WrapperRuleLibrary.checkIsNewRelationshipPersona(context,newIdentity,
					previousIdentity)) {
				LogEnablement.isLogDebugEnabled(reverseLogger,"Exit isEligibleForReverseLeaver = false (New Relationship) Handled in Joiner..."+identityName);
				return false;
			}
			// All Relationships are dropped
			flag = WrapperRuleLibrary.checkIsTerminationPersona(context,newIdentity,
					previousIdentity);
			if (flag) {
				LogEnablement.isLogDebugEnabled(reverseLogger,"...isEligibleForReverseLeaver  = false..."+identityName);
				return false;
			}
			// All Relationships are Added Back
			flag = WrapperRuleLibrary.checkIsReverseTerminationPersona(context,newIdentity,
					previousIdentity);
			if (flag) {
				LogEnablement.isLogDebugEnabled(reverseLogger,"...isEligibleForReverseLeaver  = true..."+identityName);
				return true;
			}
		}
		// Either Relationship or HR Events, Both cannot co-exist for Leaver
		else {
			flag = TriggersRuleLibrary.allowedForProcess(context,newIdentity, previousIdentity,
					ReverseLeaverRuleLibrary.REVERSELEAVERPROCESS, ReverseLeaverRuleLibrary.REVERSELEAVERFEATURE, "");
			LogEnablement.isLogDebugEnabled(reverseLogger,"Exit isEligibleForReverseLeaver = " + flag+"..identityName.."+identityName);
		}
		return flag;
	}
	/**
	 * Validate Leaver Identity Request Id PLan
	 * @param context
	 * @param identityRequestId
	 * @return
	 * @throws Exception
	 */
	public static boolean validateReverseLeaverPlan(SailPointContext context, String identityRequestId)throws Exception
	{
		LogEnablement.isLogDebugEnabled(reverseLogger,"Enter validateReverseLeaverPlan.."+identityRequestId);
		ProvisioningPlan plan=null;
		boolean result=true;
		if(identityRequestId!=null)
		{
			IdentityRequest identityReq = context.getObjectById(IdentityRequest.class,identityRequestId);
			String identityName=null;
			if(identityReq!=null)
			{
				LogEnablement.isLogDebugEnabled(reverseLogger," identityReq.."+identityReq.getName());
				ProvisioningProject provisioningProject= identityReq.getProvisionedProject();
				if(provisioningProject!=null)
				{
					plan=provisioningProject.getMasterPlan();
					//Make sure this is from Leaver
					Attributes existingPlanAttributes=plan.getArguments();
					if(existingPlanAttributes!=null && existingPlanAttributes.containsKey("requestType") && existingPlanAttributes.get("requestType")!=null
							&&  (((String) existingPlanAttributes.get("requestType")).equalsIgnoreCase(LeaverRuleLibrary.LEAVERFEATURE)||
									((String) existingPlanAttributes.get("requestType")).equalsIgnoreCase(LeaverRuleLibrary.IMMEDIATELEAVERFEATURE)))
					{
						String reqType=((String) existingPlanAttributes.get("requestType"));
						LogEnablement.isLogDebugEnabled(reverseLogger," reqType.."+reqType);
						// Account Selection will happen in wrapper workflow from Remove to ADD, or auto selector rules for roles will kick off or manual item needs to popup for roles
						Identity identityCube = context.getObjectByName(Identity.class,provisioningProject.getIdentity());
						if(identityCube!=null && plan!=null)
						{
							identityName=provisioningProject.getIdentity();
							LogEnablement.isLogDebugEnabled(reverseLogger," identityName.."+identityName);
							LogEnablement.isLogDebugEnabled(reverseLogger," identityCube.."+identityCube.getName());
							List<AccountRequest> accountRequests=plan.getAccountRequests();
							if(accountRequests!=null)
							{
								for(AccountRequest accountRequest:accountRequests)
								{
									String appName = accountRequest.getApplication();
									LogEnablement.isLogDebugEnabled(reverseLogger," appName.."+appName);
									//Reverse Operation
									if (accountRequest.getOperation().equals(AccountRequest.Operation.Create))
									{
										result=false;
									}
									if (accountRequest.getOperation().equals(AccountRequest.Operation.Enable))
									{
										result=false;
									}
									if(accountRequest.getOperation().equals(AccountRequest.Operation.Unlock))
									{
										result=false;
									}
								}
							}//Account Request Not Null
							else
							{
								result=false;
							}
							context.decache(identityCube);
						}//Identity Cube Not Null and Master Plan Not null
						else
						{
							result=false;
						}
					}//Leaver Request Type
					else
					{
						result=false;
					}
				}//Provisioning Project Not Null
				else
				{
					result=false;
				}
				context.decache(identityReq);
			}//Identity Request id Exists
			else
			{
				result=false;
			}
		}//Request Id is not null
		else
		{
			result=false;
		}
		LogEnablement.isLogDebugEnabled(reverseLogger,"Exit validateReverseLeaverPlan "+result);
		return result;
	}

  /**
   * Build Reverse Leaver Plan for Each Application That has Restore All Access Option
   * @param context
   * @param identityRequestId
   * @param applicationName
   * @param enterpriseRoles
   * @param apps
   * @return
   * @throws Exception
   */
  public static ProvisioningProject buildExecuteReverseLeaverApplicationPlan(SailPointContext context, String identityRequestId, String applicationName, HashMap enterpriseRoles, List apps) throws Exception
  {
    LogEnablement.isLogDebugEnabled(reverseLogger,"Start ReverseLeaverRuleLibrary.buildExecuteReverseLeaverApplicationPlan: " + identityRequestId);
    LogEnablement.isLogDebugEnabled(reverseLogger,"ReverseLeaverRuleLibrary.buildExecuteReverseLeaverApplicationPlan: applicationName= " + applicationName);

    if (enterpriseRoles==null) {
      enterpriseRoles= new HashMap();
    }
    if (apps==null) {
      apps=new ArrayList();
    }
    ProvisioningProject provisioningProject = null;
    IdentityRequest identityReq = context.getObjectById(IdentityRequest.class,identityRequestId);
    String identityName=null;
    if (identityReq!=null) {
      provisioningProject= identityReq.getProvisionedProject();
      if (provisioningProject != null) {
        //plan=provisioningProject.getMasterPlan();
        List<ProvisioningPlan> plans = provisioningProject.getPlans();
        for (ProvisioningPlan plan : plans ) {
          LogEnablement.isLogDebugEnabled(reverseLogger,"ReverseLeaverRuleLibrary.buildExecuteReverseLeaverApplicationPlan: Looping through plans: " + plan.toXml());

          //Create  new provisioning plan
          ProvisioningPlan planCopy = new ProvisioningPlan();
          //Create new plan arguments
          Attributes planAttributes = new Attributes();
          //Set source attribute to LCM
          planAttributes.put("source", "LCM");
          //Set Plan Arguments
          planCopy.setArguments(planAttributes);
          // Account Selection will happen in wrapper workflow from Remove to ADD, or auto selector rules for roles will kick off or manual item needs to popup for roles
          Identity identityCube = context.getObjectByName(Identity.class,provisioningProject.getIdentity());
          if (identityCube!=null && plan!=null) {
            identityName=provisioningProject.getIdentity();
            List<AccountRequest> accountRequests=plan.getAccountRequests();
            //This map is used, as we are not sure target aggregation has happened or not
            HashMap mapChangeNativeId = new HashMap();
            //Both Moved DN and Changed CN Map
            HashMap movedChangedCN= new HashMap();
            if (accountRequests!=null) {
              for (AccountRequest accountRequest:accountRequests) {
                if (accountRequest.getApplicationName()!=null && accountRequest.getApplicationName().equalsIgnoreCase(applicationName)) {
                  String acctRequestAppName=accountRequest.getApplicationName();
                  LogEnablement.isLogDebugEnabled(reverseLogger,"Matched acctRequestAppname= "+acctRequestAppName);
                  LogEnablement.isLogDebugEnabled(reverseLogger,"Matched applicationName= "+applicationName);
                  List<AttributeRequest> attributeRequests=accountRequest.getAttributeRequests();
                  if (attributeRequests!=null) {
                    for (AttributeRequest attributeRequest:attributeRequests) {
                      if (attributeRequest.getOperation().equals(ProvisioningPlan.Operation.Set)
                          && attributeRequest.getName()!=null && attributeRequest.getName().equalsIgnoreCase("AC_NewParent")) {
                        String appNativeId=null;
                        if (accountRequest.getApplicationName()!=null && attributeRequest.getValue()!=null && accountRequest.getNativeIdentity()!=null) {
                          Object newDN=attributeRequest.getValue();
                          appNativeId = WrapperRuleLibrary.appendCNtoMovedDn(accountRequest.getNativeIdentity(),newDN);
                          movedChangedCN.put("newDN"+accountRequest.getApplicationName()+accountRequest.getNativeIdentity(),newDN);
                          mapChangeNativeId.put(accountRequest.getApplicationName()+accountRequest.getNativeIdentity(),appNativeId);
                        }
                      } else if (attributeRequest.getOperation().equals(ProvisioningPlan.Operation.Set)
                          && attributeRequest.getName()!=null && attributeRequest.getName().equalsIgnoreCase("AC_NewName")) {
                        if (accountRequest.getApplicationName()!=null && attributeRequest.getValue()!=null && accountRequest.getNativeIdentity()!=null) {
                          // Old OU is appended to New CN
                          String parentDn = WrapperRuleLibrary.getParentFromNativeId(accountRequest.getNativeIdentity());
                          String newCN=null;
                          if(attributeRequest.getValue() !=null && attributeRequest.getValue() instanceof String) {
                            newCN=(String) attributeRequest.getValue();
                          }
                          if (parentDn != null && newCN != null) {
                            movedChangedCN.put("newCN"+accountRequest.getApplicationName()+accountRequest.getNativeIdentity(),newCN);
                            mapChangeNativeId.put(accountRequest.getApplicationName() + accountRequest.getNativeIdentity(), newCN + "," + parentDn);
                          }
                        }
                      }
                    }
                  }
                }
              }
            }
            LogEnablement.isLogDebugEnabled(reverseLogger,"mapChangeNativeId..."+mapChangeNativeId);
            LogEnablement.isLogDebugEnabled(reverseLogger,"movedChangedCN..."+movedChangedCN);
            if (accountRequests!=null) {
              for (AccountRequest accountRequest:accountRequests) {
                if (accountRequest.getApplicationName()!=null && accountRequest.getApplicationName().equalsIgnoreCase(applicationName)) {
                  String acctRequestAppName= accountRequest.getApplicationName();
                  LogEnablement.isLogDebugEnabled(reverseLogger,"Matched acctRequestAppname= "+acctRequestAppName);
                  LogEnablement.isLogDebugEnabled(reverseLogger,"Matched applicationName= "+applicationName);
                  //Create new AccountRequest
                  AccountRequest planCopyAccountRequest = new AccountRequest();
                  String appName = accountRequest.getApplication();
                  if (appName!=null) {
                    //Copy Application Name
                    planCopyAccountRequest.setApplication(appName);
                  }
                  String nativeId = accountRequest.getNativeIdentity();
                  if (nativeId!=null) {
                    //Copy Native Id, Both Move and Rename
                    if (appName!=null &&  movedChangedCN.containsKey("newCN"+appName+nativeId) && movedChangedCN.containsKey("newDN"+appName+nativeId) ) {
                      if (movedChangedCN.get("newCN"+appName+nativeId)!=null && movedChangedCN.get("newDN"+appName+nativeId)!=null) {
                        planCopyAccountRequest.setNativeIdentity(movedChangedCN.get("newCN"+appName+nativeId)+","+movedChangedCN.get("newDN"+appName+nativeId) );
                      } else {
                        planCopyAccountRequest.setNativeIdentity(nativeId);
                      }
                    //Copy Native Id, Either Move or Rename
                    } else if (appName!=null && mapChangeNativeId.containsKey(appName+accountRequest.getNativeIdentity())) {
                      if (mapChangeNativeId.get(appName+accountRequest.getNativeIdentity())!=null) {
                        planCopyAccountRequest.setNativeIdentity((String)mapChangeNativeId.get(appName+accountRequest.getNativeIdentity()));
                      } else {
                        planCopyAccountRequest.setNativeIdentity(nativeId);
                      }
                    } else {
                      planCopyAccountRequest.setNativeIdentity(nativeId);
                    }
                  }
                  //Reverse Operation
                  if (accountRequest.getOperation().equals(AccountRequest.Operation.Delete)) {
                    planCopyAccountRequest.setOperation(AccountRequest.Operation.Create);
                  }
                  if (accountRequest.getOperation().equals(AccountRequest.Operation.Disable)) {
                    planCopyAccountRequest.setOperation(AccountRequest.Operation.Enable);
                  }
                  if (accountRequest.getOperation().equals(AccountRequest.Operation.Lock)) {
                   planCopyAccountRequest.setOperation(AccountRequest.Operation.Unlock);
                  }
                  if (accountRequest.getOperation().equals(AccountRequest.Operation.Modify)) {
                    planCopyAccountRequest.setOperation(AccountRequest.Operation.Modify);
                  }
                  List<AttributeRequest> attributeRequests=accountRequest.getAttributeRequests();
                  if (attributeRequests!=null) {
                    for (AttributeRequest attributeRequest:attributeRequests) {
                      //Remove to Add
                      if (attributeRequest.getOperation().equals(ProvisioningPlan.Operation.Remove)) {
                        AttributeRequest planCopyAttributeRequest = new AttributeRequest();
                        String name = attributeRequest.getName();
                        if (name!=null) {
                          //Copy Name
                          planCopyAttributeRequest.setName(name);
                        }
                        LogEnablement.isLogDebugEnabled(reverseLogger,"Add Attribute Request name= "+name);
                        Object value = attributeRequest.getValue();
                        if (value!=null) {
                          //Copy value
                          planCopyAttributeRequest.setValue(value);
                        }
                        LogEnablement.isLogDebugEnabled(reverseLogger,"Add Attribute Request value= "+value);
                        //Create new account request arguments
                        Attributes attributeRequestAttributes = new Attributes();
                        //Set Plan Arguments
                        planCopyAttributeRequest.setArguments(attributeRequestAttributes);
                        //Reverse Operation
                        planCopyAttributeRequest.setOperation(ProvisioningPlan.Operation.Add);
                        if (planCopyAttributeRequest!=null && planCopyAccountRequest!=null) {
                          LogEnablement.isLogDebugEnabled(reverseLogger,"Add Attribute Request Account Request "+appName);
                          planCopyAccountRequest.add(planCopyAttributeRequest);
                        }
                      }
                      if (attributeRequest.getOperation().equals(ProvisioningPlan.Operation.Set) && appName!=null && nativeId!=null
                          && attributeRequest.getName()!=null && attributeRequest.getName().equalsIgnoreCase("AC_NewParent")) {
                        AttributeRequest planCopyAttributeRequest = new AttributeRequest();
                        //Copy Name
                        planCopyAttributeRequest.setName( attributeRequest.getName());
                        //Create new attribute request arguments
                        Attributes attributeRequestAttributes = new Attributes();
                        //Set Plan Arguments
                        planCopyAttributeRequest.setArguments(attributeRequestAttributes);
                        //Move DN Value
                        LogEnablement.isLogDebugEnabled(reverseLogger,"RECOVER MOVE nativeId "+nativeId);
                        //Remove CN, OLD nativeId
                        String strippedCN = WrapperRuleLibrary.getParentFromNativeId(nativeId);
                        if (strippedCN!=null) {
                          planCopyAttributeRequest.setValue(strippedCN);
                          //Same Set Operation
                          planCopyAttributeRequest.setOperation(attributeRequest.getOperation());
                          if (planCopyAttributeRequest!=null && planCopyAccountRequest!=null) {
                            LogEnablement.isLogDebugEnabled(reverseLogger,"Add Attribute Request Account Request "+appName);
                            planCopyAccountRequest.add(planCopyAttributeRequest);
                          }
                        }
                      } else if(attributeRequest.getOperation().equals(ProvisioningPlan.Operation.Set) && appName!=null && nativeId!=null
                           && attributeRequest.getName()!=null && attributeRequest.getName().equalsIgnoreCase("AC_NewName")) {
                        AttributeRequest planCopyAttributeRequest = new AttributeRequest();
                        //Copy Name
                        planCopyAttributeRequest.setName( attributeRequest.getName());
                        //Create new attribute request arguments
                        Attributes attributeRequestAttributes = new Attributes();
                        //Set Plan Arguments
                        planCopyAttributeRequest.setArguments(attributeRequestAttributes);
                        //Old CN Native Id + DN
                        LogEnablement.isLogDebugEnabled(reverseLogger,"RECOVER MOVE nativeId "+nativeId);
                        //Remove DN Value, OLD nativeId
                        String oldCN = WrapperRuleLibrary.getCNFromNativeId(nativeId);
                        if (oldCN!=null) {
                          planCopyAttributeRequest.setValue(oldCN);
                          //Same Set Operation
                          planCopyAttributeRequest.setOperation(attributeRequest.getOperation());
                          if (planCopyAttributeRequest!=null && planCopyAccountRequest!=null) {
                            LogEnablement.isLogDebugEnabled(reverseLogger,"Add Attribute Request Account Request "+appName);
                            planCopyAccountRequest.add(planCopyAttributeRequest);
                          }
                        }
                      // Set to Set Value from SnapShot
                      } else if (attributeRequest.getOperation().equals(ProvisioningPlan.Operation.Set) && appName!=null && nativeId!=null) {
                        Object value=attributeRequest.getValue();
                        LogEnablement.isLogDebugEnabled(reverseLogger,"OldValue "+value);
                        String attrName=attributeRequest.getName();
                        LogEnablement.isLogDebugEnabled(reverseLogger,"attrName "+attrName);
                        Object[] info = ObjectUtil.getRecentSnapshotInfo(context, identityCube);
                        if (info != null) {
                          String snapId = (String)info[0];
                          LogEnablement.isLogDebugEnabled(reverseLogger,"snapId "+snapId);
                          Date created = (Date)info[1];
                          LogEnablement.isLogDebugEnabled(reverseLogger,"created "+created);
                          if (snapId!=null && attrName!=null) {
                            IdentitySnapshot snap = context.getObjectById(IdentitySnapshot.class, snapId);
                            List<LinkSnapshot> linkSnaps=snap.getLinks(appName);
                            if (linkSnaps!=null) {
                              for (LinkSnapshot linkSnap:linkSnaps) {
                                String linkNativeId=linkSnap.getNativeIdentity();
                                LogEnablement.isLogDebugEnabled(reverseLogger,"linkNativeId "+linkNativeId);
                                String linkAppName=linkSnap.getApplication();
                                LogEnablement.isLogDebugEnabled(reverseLogger,"linkAppName "+linkAppName);
                                if (linkNativeId!=null && linkAppName!=null && linkAppName.equalsIgnoreCase(appName) && nativeId.equalsIgnoreCase(linkNativeId)) {
                                  //Match Native Ids and App Names, there could be multiple links for same app
                                  Attributes attributes= linkSnap.getAttributes();
                                  if (attributes != null) {
                                    Object newValue=attributes.get(attrName);
                                    if (newValue == null) {
                                      LogEnablement.isLogDebugEnabled(reverseLogger, "Attribute not found in attributes in link snapshot: " + attrName);
                                      newValue = "";
                                    }
                                    LogEnablement.isLogDebugEnabled(reverseLogger,"newValue " + newValue);
                                    AttributeRequest planCopyAttributeRequest = new AttributeRequest();
                                    String name = attributeRequest.getName();
                                    if (name!=null) {
                                      //Copy Name
                                      planCopyAttributeRequest.setName(name);
                                    }
                                    //Create new attribute request arguments
                                    Attributes attributeRequestAttributes = new Attributes();
                                    //Set Plan Arguments
                                    planCopyAttributeRequest.setArguments(attributeRequestAttributes);
                                    //Snapshot Value
                                    planCopyAttributeRequest.setValue(newValue);
                                    //Same Set Operation
                                    planCopyAttributeRequest.setOperation(attributeRequest.getOperation());
                                    if (planCopyAttributeRequest!=null && planCopyAccountRequest!=null) {
                                      LogEnablement.isLogDebugEnabled(reverseLogger,"Set Attribute Request Account Request "+appName);
                                      planCopyAccountRequest.add(planCopyAttributeRequest);
                                    }
                                  }
                                }
                              } //End For Loop
                            } else {
                              LogEnablement.isLogErrorEnabled(reverseLogger,"No Application Snapshot for Reverse Leaver "+identityName);
                            }
                            if (snap!=null) {
                              context.decache(snap);
                            }
                          }//SnapId Null Check
                        }//Get Latest IdentityShapshot End
                        else
                        {
                          LogEnablement.isLogErrorEnabled(reverseLogger,"No Snapshot  for Reverse Leaver "+identityName);
                        }
                      }//Set Operation If end
                    }//Attribute Request Loop
                  }//Null Check Attribute Request
                  if (planCopy!=null && planCopyAccountRequest!=null && !planCopyAccountRequest.isEmpty()) {
                    LogEnablement.isLogDebugEnabled(reverseLogger,"Account Request To Plan"+appName);
                    planCopy.add(planCopyAccountRequest);
                  }
                } //Account Request Loop
                else if ((accountRequest.getApplicationName()!=null && accountRequest.getApplicationName().equalsIgnoreCase(ProvisioningPlan.APP_IIQ)
                  || accountRequest.getApplicationName().equalsIgnoreCase(ProvisioningPlan.APP_IDM)
                  || accountRequest.getApplicationName().equalsIgnoreCase(ProvisioningPlan.IIQ_APPLICATION_NAME)))
                {
                  List<AttributeRequest> attrReqList = accountRequest.getAttributeRequests();
                  if (attrReqList != null) {
                    for (AttributeRequest attrReq : attrReqList) {
                      if (attrReq.getOp().equals(ProvisioningPlan.Operation.Remove)) {
                        String attrName = attrReq.getName();
                        LogEnablement.isLogDebugEnabled(reverseLogger,"attrName.. "+attrName);
                        Object value = attrReq.getValue();
                        LogEnablement.isLogDebugEnabled(reverseLogger,"value.. "+value);
                        List<String> listRoles = new ArrayList();
                        /**
                         * All the values should be String, compiler can covert them into List May
                         * need to check for list too
                        */
                        if (value instanceof List) {
                          listRoles = ((List) value);
                        } else {
                          listRoles.add(value.toString());
                        }
                        LogEnablement.isLogDebugEnabled(reverseLogger,"Checking listRoles.."+listRoles);
                        if (listRoles!=null && listRoles.size()>0) {
                          List<AccountRequest> actRequests=null;
                          for (String roleName:listRoles) {
                            actRequests=reAssignBundlesReverseTerm(context, identityName, roleName,  applicationName, enterpriseRoles);
                          }
                          if (actRequests!=null && actRequests.size()>0) {
                            for (AccountRequest planCopyAccountRequest: actRequests) {
                              //Role Additions
                              if (planCopy!=null && planCopyAccountRequest!=null && !planCopyAccountRequest.isEmpty()) {
                                planCopy.add(planCopyAccountRequest);
                              }
                            }
                          }
                        }
                      }
                    }
                  }
                }
              }
            } //Null Check Account Request
            context.decache(identityCube);
          } //Identity Cube Not Null and Plan Not null
          if (planCopy!=null && !planCopy.isEmpty() && planCopy.getAccountRequests()!=null && planCopy.getAccountRequests().size()>0 &&
                  identityName!=null)
          {
             apps.add(applicationName);
             launchReverseLeaverPlan(context,identityName, planCopy, WORKFLOW_DELAY);
             LogEnablement.isLogDebugEnabled(reverseLogger,"Launch Reverse Termination for applicationName.."+applicationName);
          }
        } // looping through plans
      }//Provisioning Project Not Null
      context.decache(identityReq);
    }//Identity Request Not Null

    LogEnablement.isLogDebugEnabled(reverseLogger,"End buildExecuteReverseLeaverApplicationPlan for applicationName.."+applicationName);
    return provisioningProject;
  }

	/**
	 * Launch Reverse Leaver Plan
	 * @param context
	 * @param identityName
	 * @param plan
	 * @param amountOfSeconds
	 * @throws GeneralException
	 */
	public  static void launchReverseLeaverPlan(SailPointContext context, String identityName, ProvisioningPlan plan,
		int amountOfSeconds) throws GeneralException {
		LogEnablement.isLogDebugEnabled(reverseLogger,"ReverseLeaverRuleLibrary: Start launchReverseLeaverPlan for Identity: " + identityName);
		LogEnablement.isLogDebugEnabled(reverseLogger,"ReverseLeaverRuleLibrary: Provisioning Plan: " + plan.toXml());
		//Workflow launchArguments
		HashMap launchArgsMap = new HashMap();
		launchArgsMap.put("identityName",identityName);
		launchArgsMap.put("launcher","spadmin");
		launchArgsMap.put("sessionOwner","spadmin");
		launchArgsMap.put("source","Workflow");
		launchArgsMap.put("workItemComments", "Reverse leaver");
		launchArgsMap.put("flow", "AccessRequest");
		launchArgsMap.put("notificationScheme","none");
		launchArgsMap.put("approvalScheme", "none");
		launchArgsMap.put("fallbackApprover","spadmin");
		launchArgsMap.put("plan", plan);
		launchArgsMap.put("foregroundProvisioning", "true");
		launchArgsMap.put("noApplicationTemplates", "false");
		launchArgsMap.put("requestType", "REQUEST MANAGER FEATURE");
		LogEnablement.isLogDebugEnabled(reverseLogger,"Start Request Manager Provisioning");
		String workflowName=ROADUtil.DEFAULTWORKFLOW;
		LogEnablement.isLogDebugEnabled(reverseLogger,"Workflow name: " + workflowName);
		// Use the Request Launcher
		Request req = new Request();
		RequestDefinition reqdef = context.getObjectByName( RequestDefinition.class, "Workflow Request" );
		req.setDefinition( reqdef );
		Attributes allArgs = new Attributes();
		allArgs.put( "workflow", workflowName);
		// Start 5 seconds from now.
		long current = System.currentTimeMillis();
		current += TimeUnit.SECONDS.toMillis(amountOfSeconds);
		String requestName = "REQUEST MANAGER FEATURE BY " + "spadmin " +current;
		allArgs.put( "requestName", requestName );
		allArgs.putAll( launchArgsMap );
		req.setEventDate( new Date( current ) );
		Identity id = context.getObjectByName(Identity.class, "spadmin");
		req.setOwner(id);
		req.setName(requestName);
		req.setAttributes( reqdef, allArgs );
		// Actually launch the work flow via the request manager.
		RequestManager.addRequest(context, req);
		if(reqdef!=null)
		{
			context.decache(reqdef);
		}
		LogEnablement.isLogDebugEnabled(reverseLogger,"End launchReverseLeaverPlan");
	}
	/**
	 * Override Reverse Leaver Option Settings based on Population Match
	 * In case an identity belongs to multiple population, only first matched population and leaver option is picked
	 * @param context
	 * @param app
	 * @param identity
	 * @return
	 * @throws GeneralException
	 */
	public static Map overrideApplicationReverseLeaverSettingsOnPopulationMatch(SailPointContext context, Application app, Identity identity) throws GeneralException
	{
		LogEnablement.isLogDebugEnabled(reverseLogger,"Enter overrideApplicationReverseLeaverSettingsOnPopulationMatch..");
		String identityName=null;
		String appName=null;
		Map extSetting=null;
		if(identity!=null && app!=null)
		{
			identityName=identity.getName();
			appName=app.getName();
			try
			{
				LogEnablement.isLogDebugEnabled(reverseLogger,"Enter overrideApplicationReverseLeaverSettingsOnPopulationMatch..identityName..."+identityName);
				LogEnablement.isLogDebugEnabled(reverseLogger,"Enter overrideApplicationReverseLeaverSettingsOnPopulationMatch..appName..."+appName);
				if(app!=null && app.getAttributes()!=null && identity!=null && app.getAttributeValue(ReverseLeaverRuleLibrary.REVERSELEAVEROPTIONSPOPULATION)!=null
						&& app.getAttributeValue(ReverseLeaverRuleLibrary.REVERSELEAVEROPTIONSPOPULATION) instanceof List )
				{
					List<Map> options=(List) app.getAttributeValue(ReverseLeaverRuleLibrary.REVERSELEAVEROPTIONSPOPULATION);
					if(options!=null && options.size()>0)
					{
						for(Map option:options)
						{
							if(option!=null && !option.isEmpty() && option.containsKey(ReverseLeaverRuleLibrary.OPTIONSREVPOPULATIONTOKEN)
									&&  (option.containsKey(ReverseLeaverRuleLibrary.OPTIONSREVTERMINATIONEXTENDEDRULETOKEN)||option.containsKey(ReverseLeaverRuleLibrary.OPTIONSREVTOKEN)))
							{
								LogEnablement.isLogDebugEnabled(reverseLogger,"option " + option);
								String populationName = (String) option.get(ReverseLeaverRuleLibrary.OPTIONSREVPOPULATIONTOKEN);
								String extendedRuleName = (String) option.get(ReverseLeaverRuleLibrary.OPTIONSREVTERMINATIONEXTENDEDRULETOKEN);
								String terminationOption = (String) option.get(ReverseLeaverRuleLibrary.OPTIONSREVTOKEN);
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
								LogEnablement.isLogDebugEnabled(reverseLogger,"populationName " + populationName);
								LogEnablement.isLogDebugEnabled(reverseLogger,"extendedRuleName " + extendedRuleName);
								LogEnablement.isLogDebugEnabled(reverseLogger,"terminationOption " + terminationOption);
								if (populationName != null && (extendedRuleName!=null||terminationOption!=null))
								{
									int result = WrapperRuleLibrary.matchPopulation(context, identity, populationName);
									if (result > 0)
									{
										LogEnablement.isLogDebugEnabled(reverseLogger," Population Matcched.." + "matchPopulation..true.."+populationName);
										extSetting=option;
										LogEnablement.isLogDebugEnabled(reverseLogger,"End overrideApplicationReverseLeaverSettingsOnPopulationMatch..."+identityName+"....overrideExtSetting.."+extSetting);
										break;
									}
									else
									{
										LogEnablement.isLogDebugEnabled(reverseLogger," Population Not Matcched.." + "matchPopulation..false.."+populationName);
									}
								}
							}
						}
					}
				}
			}
			catch (Exception ex)
			{
				LogEnablement.isLogErrorEnabled(reverseLogger,"ERROR: End overrideApplicationReverseLeaverSettingsOnPopulationMatch..."+identityName+"...."+extSetting+".."+ex.getMessage());
			}
		}
		LogEnablement.isLogDebugEnabled(reverseLogger,"End overrideApplicationReverseLeaverSettingsOnPopulationMatch..."+identityName+"...."+extSetting);
		return extSetting;
	}
	/**
	 *  Add Bundles for Reverse Termination
	 * @param context
	 * @param identity
	 * @param bundleName
	 * @param appName
	 * @param enterpriseRoles
	 * @return
	 * @throws GeneralException
	 */
	public static List reAssignBundlesReverseTerm(SailPointContext context, String identityName, String bundleName, String appName, HashMap enterpriseRoles) throws GeneralException
	{
		LogEnablement.isLogDebugEnabled(reverseLogger,"Enter reAssignBundlesReverseTerm");
		String logicalAppEnabled=ObjectConfigAttributesRuleLibrary.extendedAttrLogicalAppEnabled(context);
		boolean logApp=false;
		if(logicalAppEnabled!=null && logicalAppEnabled.length()>0 && logicalAppEnabled.equalsIgnoreCase("TRUE"))
		{
			logApp=true;
		}
		List retVal = new ArrayList();
		String logicalAppNameOnRole=null;
		Bundle bundle = null;
		bundle = context.getObjectByName(Bundle.class, bundleName);
		LogEnablement.isLogDebugEnabled(reverseLogger,"...appName = " + appName);
		logicalAppNameOnRole=(String) bundle.getAttribute("appName");
		//Logical Application Name is same as Physical application - This means logical is not a virtual applications
		if (bundle != null && logApp && logicalAppNameOnRole != null && appName.equalsIgnoreCase(logicalAppNameOnRole))
		{
			if (retVal != null)
			{
				AccountRequest bundleAR = addRequestedAssignedBundle(context,identityName, bundleName);
				if (bundleAR != null)
				{
					retVal.add(bundleAR);
				}
			}
		}
		// Logical Application is Disabled
		// OR Logical Application is Not Defined on Role
		// OR Logical Application doesn't exist in IdentityIQ
		// OR Logical Application is comma separated. As a result, doesn't match with Identity Application
		else if(bundle!=null)
		{
			if( !enterpriseRoles.containsKey(bundle.getName()))
			{
				String businessAppNamePref=ROADUtil.setRoleAppName(bundle.getRequirements(),bundle.getApplications());
				LogEnablement.isLogDebugEnabled(reverseLogger,"...businessAppNamePref "+businessAppNamePref);
				if (businessAppNamePref != null && appName.equalsIgnoreCase(businessAppNamePref))
				{
					enterpriseRoles.put(bundle.getName(),businessAppNamePref);
					if (retVal != null)
					{
						AccountRequest bundleAR = addRequestedAssignedBundle(context,identityName, bundleName);
						if (bundleAR != null)
						{
							retVal.add(bundleAR);
						}
					}
				}
			}
		}
		if (bundle != null)
		{
			context.decache(bundle);
		}

		LogEnablement.isLogDebugEnabled(reverseLogger,"Exit reAssignBundlesReverseTerm");
		return retVal;
	}
	/**
	 * Add RBAC Business Application Roles that are requested
	 * Add Birthright Roles that are assigned via Joiner Event, never requested
	 * Add RBAC Business Application Roles that are assigned via Assignment Rule
	 * @param identityName
	 * @param bundleName
	 * @return
	 */
	public static AccountRequest addRequestedAssignedBundle(SailPointContext context, String identityName, String bundleName) {
		LogEnablement.isLogDebugEnabled(reverseLogger,"Enter addRequestedAssignedBundle");
		AccountRequest acctReq = new AccountRequest();
		AttributeRequest attrReq = new AttributeRequest();
		acctReq.setApplication(ProvisioningPlan.APP_IIQ);
		acctReq.setNativeIdentity(identityName);
		acctReq.setOperation(ProvisioningPlan.AccountRequest.Operation.Modify);
		attrReq.setName(ProvisioningPlan.ATT_IIQ_ASSIGNED_ROLES);
		attrReq.setOperation(ProvisioningPlan.Operation.Add);
		attrReq.setValue(bundleName);
		Attributes args = new Attributes();
		args.putClean("deassignEntitlements", new Boolean(false));
		args.putClean("negativeAssignment", new Boolean(false));
		args.putClean("assignment", new Boolean(true));
		if (attrReq != null) {
			attrReq.setArguments(args);
		}
		if (acctReq != null)
		{
			acctReq.add(attrReq);
		}
		LogEnablement.isLogDebugEnabled(reverseLogger,"Exit addRequestedAssignedBundle");
		return acctReq;
	}
}
