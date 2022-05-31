/*
Copyright © 2018 SailPoint Technologies, Inc. All Rights Reserved.
All logos, text, content, and works of authorship, including but not limited to underlying code, programming or scripting language, designs, and/or graphics,
that are used and/or depicted herein are protected under United States and international copyright and trademark laws and treaties,
and may not be used or reproduced without the prior express written permission of SailPoint Technologies, Inc.
 */
package sailpoint.rapidapponboarding.rule;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import javax.naming.ldap.LdapName;
import javax.naming.InvalidNameException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.Aggregator;
import sailpoint.api.IdentityService;
import sailpoint.api.ObjectUtil;
import sailpoint.api.RequestManager;
import sailpoint.api.SailPointContext;
import sailpoint.connector.Connector;
import sailpoint.object.AccountSelection;
import sailpoint.object.AccountSelection.AccountInfo;
import sailpoint.object.Application;
import sailpoint.object.ApprovalSet;
import sailpoint.object.Attributes;
import sailpoint.object.BatchRequestItem;
import sailpoint.object.Bundle;
import sailpoint.object.Configuration;
import sailpoint.object.Custom;
import sailpoint.object.Field;
import sailpoint.object.Filter;
import sailpoint.object.Form;
import sailpoint.object.GroupDefinition;
import sailpoint.object.Identity;
import sailpoint.object.IdentityChangeEvent;
import sailpoint.object.IdentityRequest;
import sailpoint.object.IdentityTrigger;
import sailpoint.object.IntegrationConfig;
import sailpoint.object.Link;
import sailpoint.object.ManagedAttribute;
import sailpoint.object.ManagedResource;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.ProvisioningPlan.AccountRequest;
import sailpoint.object.ProvisioningPlan.AttributeRequest;
import sailpoint.object.ProvisioningPlan.Operation;
import sailpoint.object.ProvisioningProject;
import sailpoint.object.ProvisioningTarget;
import sailpoint.object.QueryOptions;
import sailpoint.object.Request;
import sailpoint.object.RequestDefinition;
import sailpoint.object.ResourceObject;
import sailpoint.object.Rule;
import sailpoint.object.Schema;
import sailpoint.object.Template;
import sailpoint.object.WorkItem;
import sailpoint.object.Workflow;
import sailpoint.rapidapponboarding.logger.LogEnablement;
import sailpoint.server.Servicer;
import sailpoint.tools.GeneralException;
import sailpoint.tools.RFC4180LineParser;
import sailpoint.tools.Util;
import sailpoint.web.RegistrationBean;
import sailpoint.workflow.WorkflowContext;
/**
 * Wrapper Accelerator Pack Util
 *
 * @author rohit.gupta
 *
 */
public class WrapperRuleLibrary {
	private static Log fwLogger = LogFactory.getLog("rapidapponboarding.rules");
	private static Custom custom = null;
	private static Custom customPersona = null;
	//Ticket Integration
	private static final String AFTER_TICKET_PROVISIONING_RULE_ATTR = "afterticketprovisioningExtendedrule";
	private static final String AFTER_TICKET_PROVISIONING_RULE_OPTIONS = "afterTicketProvisioningOptions";
	private static final String AFTER_TICKET_PROVISIONING_RULE_OPTIONS_VALUE = "Extended Rule";
	//Service Cube
	public static final String SERVICE_CUBE_ATTR = "serviceCube";
	//Request Type
	public static final String CART_REQUEST_FEATURE = "CART REQUEST FEATURE";
	public static final String JOINER = "JOINER";
	public static final String BATCH = "BATCH";
	//Privileged Accounts
	private static final String PSAACCOUNT = "psAccount";
	private static final String ROLEPRIVILEGED = "rolePrivileged";
	private static final String PRIVILEGEDACCTTYPE ="apaccountType";
	public static final String ENTITLEMENTPRIVILEGED = "entPrivileged";
	private static final String PSAREGULARACCOUNTEXPRESSION = "psaRegularAttrExpression";
	//Global
	public static final String ACCELERATORPACKGLOBALSETTINGS = "SMARTSERVICES GLOBAL SETTINGS";
	static final String GLOBALROADCUSTOM = "Custom-Framework-Common-Settings";
	static final String GLOBALTARGETAGGREGATIONATTRS = "targetAggregationAttributes";
	private static final String SPLITELIGIBILITY = "Split Eligibility";
	private static final String MANAGERSERVICEOWNERAPPROVAL = "ManagerOrServiceAccountOwner";
	static final String ACNEWNAME = "AC_NewName";
	static final String ACNEWPARENT = "AC_NewParent";
	static final String GLOBALWAIT = "globalWait";
	//Persona
	private static final String MOVERPERSONACONFIGURATION = "Configuration-Mover-Partial-Leaver-Relationship";
	private static final String PERSONACUSTOM = "Custom-Persona-Settings";
	private static final String PERSONACUSTOMSETTINGS = "PERSONA SETTINGS";
	private static final String PERSONAMOVERRELATIONSHIPDROP = "moverRelationshipsDropIgnore";
	private static final String PERSONAENABLED = "enabledPersona";
	static final String PERSONAADD = "ADD:";
	static final String PERSONADROP = "DROP:";
	static final String PERSONAACTIVE = "[ACTIVE]";
	static final String PERSONAINACTIVE = "[INACTIVE]";
	static final String PERSONASUSPENDED = "[SUSPENDED]";
	static final String PERSONARELATIONSHIPS = "relationships";
	static final String PERSONAEMPLOYEE = "EMPLOYEE";
	static final String PERSONASTUDENT = "STUDENT";
	static final String PERSONANONEMPLOYEE = "NON-EMPLOYEE";
	//Password Constants
	private static final String TARGETPASSWORDSYNC = "targetpasswordSync";
	private static final String PRIMARYSYNCACCOUNTS = "primarypasswordSyncaccounts";
	private static final String PASSWORDSREQUEST = "PasswordsRequest";
	//Native Change Constants
	private static final String CERTNATIVECHANGE = "Certification-NativeChange";
	//Form Role
	private static final String FORMROLEFIELDKEY = "IIQEXTENDEDFORMIIQ";
	//Multiple Workflows
	private static final String ACCELERATORPACKENABLED = "acceleratorPackEnabled";
	private static final String EXISTINGACCESSREQUEST = "existingAccessRequest";
	private static final String EXISTINGACCOUNTREQUEST = "existingAccountRequest";
	private static final String EXISTINGBACCESSREQUEST = "existingBatchAccessRequest";
	private static final String EXISTINGBACCOUNTREQUEST = "existingBatchAccountRequest";
	private static final String FLOWACCESSREQUEST = "AccessRequest";
	private static final String FLOWACCOUNTREQUEST = "AccountsRequest";
	private static final String SOURCEBATCH = "Batch";
	private static final String UIINTVALERROR = "validationStringLeftRightError";
	private static final String SUBFLOWNAME = "subflowName";
	private static final String BATCHVALIDATIONERROR = "batchValidationError";
	public static String validationStringLeftRight = "";
	/**
	 * FireFighter Workflow
	 */
	private static final String UIINTVALFIRFIGHTERROR = "validationFireFighter";
	private static final String FIREFIGHTER = "FireFighter";
	private static final String FIREFIGHTERADMIN = "FireFighterAdmin";
	/**
	 * This method is used for Triggers
	 *
	 * @param newIdentity
	 * @param previousIdentity
	 * @param process
	 * @param feature
	 * @param ignoreTriggerCheck
	 * @return
	 * @throws GeneralException
	 * @throws ParseException
	 */
	public static boolean validateTriggersBeforePersona(SailPointContext context, Identity newIdentity,
			Identity previousIdentity, String process) throws GeneralException, ParseException {
		String identityName = null;
		if (newIdentity != null) {
			identityName = newIdentity.getName();
		}
		// Entry point for all of the Life cycle Events to see if the Identity
		// matches the pre-defined processes in the custom object
		LogEnablement.isLogDebugEnabled(fwLogger, "Enter validateTriggers.." + identityName);
		// newIdentity is null - Validation 1
		if (newIdentity == null) {
			LogEnablement.isLogDebugEnabled(fwLogger, "...No new identity.  Return false..." + identityName);
			return false;
		}
		// Previous Identity can be null - Validation 2
		if (!(process.equalsIgnoreCase("joinerProcess"))) {
			if (previousIdentity == null) {
				LogEnablement.isLogDebugEnabled(fwLogger, "...No previous identity.  Return false...." + identityName);
				return false;
			}
		}
		// Must be correlated
		if (newIdentity != null && false == newIdentity.isCorrelated()) {
			LogEnablement.isLogDebugEnabled(fwLogger, "...Identity is not correlated..." + identityName);
			return false;
		}
		// Must not be a service cube
		if (newIdentity != null && newIdentity.getAttribute(WrapperRuleLibrary.SERVICE_CUBE_ATTR) != null
				&& ((String) newIdentity.getAttribute(WrapperRuleLibrary.SERVICE_CUBE_ATTR)).equalsIgnoreCase("TRUE")) {
			LogEnablement.isLogDebugEnabled(fwLogger, "...Identity is not human...." + identityName);
			return false;
		}
		String identityTypeEnabled = ObjectConfigAttributesRuleLibrary.extendedAttrIdentityTypeEnabled(context);
		boolean identityTypeEnab = false;
		if (identityTypeEnabled != null && identityTypeEnabled.length() > 0
				&& identityTypeEnabled.equalsIgnoreCase("TRUE")) {
			identityTypeEnab = true;
		}
		if (identityTypeEnab) {
			// Must not be a service cube
			if (newIdentity != null && newIdentity.getType() != null
					&& newIdentity.getType().equalsIgnoreCase("service")) {
				LogEnablement.isLogDebugEnabled(fwLogger, "...Identity is not human..." + identityName);
				return false;
			}
		}
		return true;
	}
	/**
	 * Get Common FrameWork Settings
	 *
	 * @return custom
	 * @throws GeneralException
	 */
	static synchronized Custom getCustom(SailPointContext context) throws GeneralException {
		// Adding second check to avoid re-initialization when the multiple
		// threads enter into the above if condition and waiting for this to be
		// initialized
		if (null == custom || null == custom.getAttributes()
				|| null == custom.getAttributes().get(WrapperRuleLibrary.ACCELERATORPACKGLOBALSETTINGS)
				|| !WrapperRuleLibrary.GLOBALROADCUSTOM.equalsIgnoreCase(custom.getName())) {
			LogEnablement.isLogDebugEnabled(fwLogger, "...Entering getCustom");
			custom = context.getObjectByName(Custom.class, WrapperRuleLibrary.GLOBALROADCUSTOM);
			LogEnablement.isLogDebugEnabled(fwLogger, "...Exiting getCustom");
		} else {
            Date dbModified = Servicer.getModificationDate(context, custom);
            if (Util.nullSafeCompareTo(dbModified, custom.getModified()) > 0) {
                LogEnablement.isLogDebugEnabled(fwLogger,"...Returning updated customCommonFramework object");
                custom = context.getObjectByName(Custom.class, WrapperRuleLibrary.GLOBALROADCUSTOM);
            } else {
                LogEnablement.isLogDebugEnabled(fwLogger,"...Returning previously initialized customCommonFramework object");
            }
        }
		return custom;
	}
	/**
	 * Get Persona Settings
	 *
	 * @return custom
	 * @throws GeneralException
	 */
	static synchronized Custom getCustomPersona(SailPointContext context) throws GeneralException {
		// Adding second check to avoid re-initialization when the multiple
		// threads enter into the above if condition and waiting for this to be
		// initialized
		if (null == customPersona || null == customPersona.getAttributes()
				|| null == customPersona.getAttributes().get(WrapperRuleLibrary.PERSONACUSTOMSETTINGS)
				|| !WrapperRuleLibrary.PERSONACUSTOM.equalsIgnoreCase(customPersona.getName())) {
			LogEnablement.isLogDebugEnabled(fwLogger, "...Entering getCustomPersona");
			customPersona = context.getObjectByName(Custom.class, WrapperRuleLibrary.PERSONACUSTOM);
			LogEnablement.isLogDebugEnabled(fwLogger, "...Exiting getCustomPersona");
		} else {
            Date dbModified = Servicer.getModificationDate(context, customPersona);
            if (Util.nullSafeCompareTo(dbModified, customPersona.getModified()) > 0) {
                LogEnablement.isLogDebugEnabled(fwLogger,"...Returning updated customPersona object");
                customPersona = context.getObjectByName(Custom.class, WrapperRuleLibrary.PERSONACUSTOM);
            } else {
                LogEnablement.isLogDebugEnabled(fwLogger,"...Returning previously initialized customPersona object");
            }
        }
		return customPersona;
	}
	/**
	 * Load Custom Artifact At this point it is used to Create Snapshot based on
	 * configured Types and Mitigation Lifecycle Event to Get Mover Certification
	 * Reference
	 *
	 * @param parentKey
	 * @param childKey
	 * @return
	 * @throws GeneralException
	 */
	public static Object getCommonFrameworkCustom(SailPointContext context, String parentKey, String childKey)
			throws GeneralException {
		LogEnablement.isLogDebugEnabled(fwLogger, "Enter WrapperRuleLibrary::getCommonFrameworkCustom");
		Object returnVal = null;
		// Get the Custom Object
		if (null == custom || custom.getAttributes() == null || custom.getAttributes().isEmpty()) {
			LogEnablement.isLogDebugEnabled(fwLogger, "Get Custom Object");
			custom = getCustom(context);
		} else {
			LogEnablement.isLogDebugEnabled(fwLogger, "Cached Custom Artifact " + custom);
		}
		// Navigate to the SmartServices GLOBAL SETTINGS Key in the Map
		if (custom != null) {
			Map globalMap = (Map) custom.getAttributes().get(WrapperRuleLibrary.ACCELERATORPACKGLOBALSETTINGS);
			LogEnablement.isLogDebugEnabled(fwLogger, "globalMap " + globalMap);
			if (globalMap != null) {
				// Both Parent and Child Key is Provided
				if (parentKey != null && parentKey.length() > 0 && globalMap.containsKey(parentKey) && childKey != null
						&& childKey.length() > 0) {
					Map entryMap = (Map) globalMap.get(parentKey);
					if (entryMap != null) {
						returnVal = entryMap.get(childKey);
					}
					LogEnablement.isLogDebugEnabled(fwLogger, "childKey returnVal " + returnVal);
				}
				// Only Parent Key and there is no Child Key
				else if (parentKey != null && parentKey.length() > 0 && globalMap.containsKey(parentKey)
						&& (childKey == null || childKey.length() <= 0)) {
					Map entryMap = (Map) globalMap.get(parentKey);
					returnVal = entryMap;
					LogEnablement.isLogDebugEnabled(fwLogger, "parentKey returnVal " + returnVal);
				}
				// Both Parent and Child Key is Not Provided
				else if ((parentKey == null || parentKey.length() <= 0)
						&& (childKey == null || childKey.length() <= 0)) {
					returnVal = globalMap;
					LogEnablement.isLogDebugEnabled(fwLogger,
							"childKey and parentKey Not provided returnVal " + returnVal);
				}
			}
		}
		LogEnablement.isLogDebugEnabled(fwLogger, "Exit getCommonFrameworkCustom " + returnVal);
		return returnVal;
	}
	/**
	 * Match Group Definition
	 *
	 * @param identity
	 * @param populationName
	 * @return
	 * @throws GeneralException
	 */
	public static int matchPopulation(SailPointContext context, Identity identity, String populationName)
			throws GeneralException {
		LogEnablement.isLogDebugEnabled(fwLogger, "Enter matchPopulation.." + populationName + "..");
		int count = 0;
		QueryOptions ops = new QueryOptions();
		GroupDefinition groupDefinition = context.getObjectByName(GroupDefinition.class, populationName);
		if (groupDefinition != null) {
			Filter filterGd = groupDefinition.getFilter();
			LogEnablement.isLogDebugEnabled(fwLogger, "Enter filterGd.." + filterGd + "..");
			if (filterGd != null) {
				Filter combo = Filter.and(Filter.eq("id", identity.getId()), filterGd);
				ops.add(combo);
				LogEnablement.isLogDebugEnabled(fwLogger, "Enter matchPopulation ops.." + ops);
				count = context.countObjects(Identity.class, ops);
			}
			context.decache(groupDefinition);
		}
		LogEnablement.isLogDebugEnabled(fwLogger,
				"End matchPopulation populationName.." + populationName + ".." + count);
		return count;
	}
	/**
	 * Is Identity Active
	 *
	 * @param context
	 * @param identity
	 * @return
	 * @throws Exception
	 */
	public static boolean isIdentityActive(SailPointContext context, Identity identity) throws Exception {
		boolean active = false;
		if (identity != null) {
			boolean inactive = identity.isInactive();
			if (!inactive) {
				active = true;
			}
		}
		return active;
	}
	/**
	 * Given an Identity, Application and name of an attribute, returns the
	 * attribute value for the first Link on the Application. Returns "", an empty
	 * String if either no such attribute exists or the attribute is null.
	 *
	 * @param applicationName
	 * @param attributeName
	 * @return String Attribute Value
	 * @throws GeneralException
	 */
	public static String getLinkAttributeValue(SailPointContext context, Identity identity, String appName,
			String attributeName) throws GeneralException {
		LogEnablement.isLogDebugEnabled(fwLogger, "Enter WrapperRuleLibrary::getLinkAttributeValue");
		LogEnablement.isLogDebugEnabled(fwLogger,
				"Input appName=[" + appName + "] attributeName= [" + attributeName + "]");
		IdentityService idSrv = new IdentityService(context);
		Application app = context.getObjectByName(Application.class, appName);
		Iterator links = idSrv.getLinks(identity, app).iterator();
		String attrValue = "";
		while (links.hasNext()) {
			Link link = (Link) links.next();
			attrValue = (String) link.getAttribute(attributeName);
			break;
		}
		if (app != null) {
			context.decache(app);
		}
		LogEnablement.isLogDebugEnabled(fwLogger, "Attribute Value=[" + attrValue + "]");
		return attrValue;
	}
	public static Configuration getNativeChangeConfig(SailPointContext context) {
		LogEnablement.isLogDebugEnabled(fwLogger, "Enter WrapperRuleLibrary::getNativeChangeConfig");
		Configuration configuration = null;
		try {
			configuration = ObjectUtil.transactionLock(context, Configuration.class,
					WrapperRuleLibrary.CERTNATIVECHANGE);
		} catch (GeneralException e) {
			LogEnablement.isLogErrorEnabled(fwLogger, "Not found " + configuration);
		}
		LogEnablement.isLogDebugEnabled(fwLogger, "End getNativeChangeConfig " + configuration);
		return configuration;
	}
	/**
	 * Set Native Change in a Configuration Object
	 *
	 * @param context
	 * @param identityName
	 * @param message
	 * @return
	 * @throws GeneralException
	 */
	public static boolean setNativeChangeKeyValue(SailPointContext context, String identityName, List message)
			throws GeneralException {
		LogEnablement.isLogDebugEnabled(fwLogger, "Enter WrapperRuleLibrary::setNativeChangeKeyValue");
		boolean retVal = false;
		if (identityName != null) {
			try {
				Configuration configuration = getNativeChangeConfig(context);
				configuration.put(identityName, message);
				context.saveObject(configuration);
			} finally {
				// Doing Commit in the Finally Block
				// Anything bad we’ll always end the transaction and release the
				// lock
				context.commitTransaction();
			}
			retVal = true;
		}
		return retVal;
	}
	/**
	 * Get Native Changes from Custom Artifact
	 *
	 * @param identityName
	 * @return
	 * @throws GeneralException
	 */
	public static Object getNativeChangeKeyValue(SailPointContext context, String identityName)
			throws GeneralException {
		LogEnablement.isLogDebugEnabled(fwLogger, "Enter WrapperRuleLibrary::getNativeChangeKeyValue");
		Object result = null;
		if (identityName != null) {
			Configuration configuration = context.getObjectByName(Configuration.class,
					WrapperRuleLibrary.CERTNATIVECHANGE);
			LogEnablement.isLogDebugEnabled(fwLogger, "configuration " + configuration);
			if (configuration != null) {
				Attributes attributes = configuration.getAttributes();
				LogEnablement.isLogDebugEnabled(fwLogger, "attributes " + attributes);
				if (attributes != null) {
					Map mainMap = attributes.getMap();
					LogEnablement.isLogDebugEnabled(fwLogger, "mainMap " + mainMap);
					if (mainMap != null) {
						result = mainMap.get(identityName);
					}
				}
				context.decache(configuration);
			}
		}
		LogEnablement.isLogDebugEnabled(fwLogger, "End getNativeChangeKeyValue = " + result);
		return result;
	}
	/**
	 * Delete Native Change Key from Configuration Object
	 *
	 * @param identityName
	 * @throws GeneralException
	 */
	public static void deleteNativeChangeKeyValue(SailPointContext context, String identityName)
			throws GeneralException {
		LogEnablement.isLogDebugEnabled(fwLogger, "Enter WrapperRuleLibrary::deleteNativeChangeKeyValue");
		Configuration configuration = null;
		try {
			configuration = getNativeChangeConfig(context);
			Map existingMap = null;
			if (configuration != null) {
				Attributes existingAtttributes = configuration.getAttributes();
				if (existingAtttributes != null) {
					existingMap = existingAtttributes.getMap();
					if (existingMap != null && existingMap.containsKey(identityName)) {
						LogEnablement.isLogDebugEnabled(fwLogger, "Existing configuration Object " + configuration);
						existingMap.remove(identityName);
						existingAtttributes.setMap(existingMap);
						configuration.setAttributes(existingAtttributes);
						context.saveObject(configuration);
					}
				}
			}
		} catch (GeneralException e) {
			LogEnablement.isLogErrorEnabled(fwLogger, "Native Change Error.." + e.getMessage());
		} finally {
			// Doing Commit in the Finally Block
			// Anything bad we’ll always end the transaction and release the
			// lock
			context.commitTransaction();
		}
	}
	/**
	 * Check if all relationships are inactive
	 *
	 * @param newIdentity
	 * @param previousIdentity
	 * @return
	 * @throws GeneralException
	 */
	public static boolean checkIsTerminationPersona(SailPointContext context, Identity newIdentity,
			Identity previousIdentity) throws GeneralException {
		String identityName = null;
		if (newIdentity != null) {
			identityName = newIdentity.getName();
		}
		LogEnablement.isLogDebugEnabled(fwLogger,
				"Enter WrapperRuleLibrary::checkIsTerminationPersona..." + identityName);
		boolean flag = false;
		if (newIdentity == null) {
			LogEnablement.isLogDebugEnabled(fwLogger, "...No new identity.  Return false..." + identityName);
			LogEnablement.isLogDebugEnabled(fwLogger, "Exit checkIsTerminationPersona.." + identityName);
			return false;
		}
		if (previousIdentity == null) {
			LogEnablement.isLogDebugEnabled(fwLogger, "...No previous identity.  Return false..." + identityName);
			LogEnablement.isLogDebugEnabled(fwLogger, "Exit checkIsTerminationPersona..." + identityName);
			return false;
		}
		if (newIdentity != null && previousIdentity != null) {
			// CHECK TO SEE IF A IDENTITY MIGHT HAVE A NEW RELATIONSHIP
			List<String> differentRelationships = getRelationshipChangesPersona(context, newIdentity, previousIdentity,
					false);
			if (differentRelationships != null && !(differentRelationships.isEmpty())) {
				int differenceCount = 0;
				for (String eachDifference : differentRelationships) {
					if (eachDifference.startsWith(WrapperRuleLibrary.PERSONADROP)) {
						differenceCount += 1;
					}
				}
				LogEnablement.isLogDebugEnabled(fwLogger, "differenceCount = " + differenceCount);
				List<String> relationShips = null;
				if (newIdentity.getAttribute(WrapperRuleLibrary.PERSONARELATIONSHIPS) != null
						&& newIdentity.getAttribute(WrapperRuleLibrary.PERSONARELATIONSHIPS) instanceof List) {
					relationShips = (List) newIdentity.getAttribute(WrapperRuleLibrary.PERSONARELATIONSHIPS);
					LogEnablement.isLogDebugEnabled(fwLogger,
							"New Relationships = identityName.." + identityName + "...." + relationShips);
					if (relationShips != null && relationShips.size() > 0) {
						for (String relationship : relationShips) {
							if (relationship != null) {
								if (relationship.contains(WrapperRuleLibrary.PERSONAACTIVE)
										|| relationship.contains(WrapperRuleLibrary.PERSONASUSPENDED)) {
									differenceCount = 0;
									LogEnablement.isLogDebugEnabled(fwLogger,
											"New Relationships Contains Active or Suspended = identityName.."
													+ identityName + "...." + differenceCount);
								}
							}
						}
					}
				} else {
					LogEnablement.isLogDebugEnabled(fwLogger,
							"Empty Relationships = identityName.." + identityName + "...." + differenceCount);
					if (!(differenceCount == 0)) {
						flag = true;
					}
				}
				if (null != relationShips && !(differenceCount == 0)) {
					flag = true;
				}
			}
			LogEnablement.isLogDebugEnabled(fwLogger,
					"Exit checkIsTerminationPersona = identityName.." + identityName + "...." + flag);
			return flag;
		}
		LogEnablement.isLogDebugEnabled(fwLogger,
				"Exit checkIsTerminationPersona = identityName.." + identityName + "...." + flag);
		return flag;
	}
	/**
	 * Check if all relationships are Active
	 *
	 * @param newIdentity
	 * @param previousIdentity
	 * @return
	 * @throws GeneralException
	 */
	public static boolean checkIsReverseTerminationPersona(SailPointContext context, Identity newIdentity,
			Identity previousIdentity) throws GeneralException {
		String identityName = null;
		if (newIdentity != null) {
			identityName = newIdentity.getName();
		}
		LogEnablement.isLogDebugEnabled(fwLogger,
				"Enter WrapperRuleLibrary::checkIsReverseTerminationPersona.." + identityName);
		boolean flag = false;
		if (newIdentity == null) {
			LogEnablement.isLogDebugEnabled(fwLogger, "...No new identity.  Return false..." + identityName);
			LogEnablement.isLogDebugEnabled(fwLogger, "Exit checkIsReverseTerminationPersona..." + identityName);
			return false;
		}
		if (previousIdentity == null) {
			LogEnablement.isLogDebugEnabled(fwLogger, "...No previous identity.  Return false...." + identityName);
			LogEnablement.isLogDebugEnabled(fwLogger, "Exit checkIsReverseTerminationPersona..." + identityName);
			return false;
		}
		if (newIdentity != null && previousIdentity != null) {
			List<String> differentRelationships = getRelationshipChangesPersona(context, newIdentity, previousIdentity,
					false);
			if (differentRelationships != null && !(differentRelationships.isEmpty())) {
				int differenceCount = 0;
				for (String eachDifference : differentRelationships) {
					if (eachDifference.startsWith(WrapperRuleLibrary.PERSONAADD)) {
						differenceCount += 1;
					}
				}
				List<String> relationShips = null;
				if (newIdentity.getAttribute(WrapperRuleLibrary.PERSONARELATIONSHIPS) != null) {
					relationShips = (List) newIdentity.getAttribute(WrapperRuleLibrary.PERSONARELATIONSHIPS);
					for (String relationship : relationShips) {
						if (relationship != null) {
							if (relationship.contains(WrapperRuleLibrary.PERSONAINACTIVE)) {
								differenceCount = 0;
							}
						}
					}
				}
				if (null != relationShips && !(differenceCount == 0)) {
					flag = true;
				}
			}
			LogEnablement.isLogDebugEnabled(fwLogger, "Exit checkIsReverseTerminationPersona = " + flag);
			return flag;
		}
		return flag;
	}
	/**
	 * Detect Delete Use Cases
	 *
	 * @param newRelationships
	 * @param previousRelationships
	 * @return
	 * @throws GeneralException
	 */
	public static List didRelationshipGetDeleted(SailPointContext context, List<String> newRelationships,
			List<String> previousRelationships) throws GeneralException {
		LogEnablement.isLogDebugEnabled(fwLogger, "Start didRelationshipGetDeleted");
		List<String> retVal = new ArrayList();
		if (previousRelationships != null && !previousRelationships.isEmpty()) {
			for (String relationship : previousRelationships) {
				if (relationship != null) {
					/**
					 * Previous Relation (Active or Suspended) is Dropped from New Relationship
					 * Inactive status drop should be handled earlier and there is no need to check
					 * for Inactive drop in this METHOD
					 */
					if (relationship.contains(WrapperRuleLibrary.PERSONAACTIVE)
							|| relationship.contains(WrapperRuleLibrary.PERSONASUSPENDED)) {
						/**
						 * New Relationship is Empty, Everything is Dropped
						 */
						if (newRelationships == null || newRelationships.size() <= 0) {
							retVal.add(WrapperRuleLibrary.PERSONADROP + relationship);
						}
						/**
						 * This condition is handled previously and needs to be ignored in this method
						 * IN case this previous relationship has Active and now it is Inactive Ignore
						 */
						else if (newRelationships.contains(relationship.replace(WrapperRuleLibrary.PERSONAACTIVE,
								WrapperRuleLibrary.PERSONAINACTIVE))) {
							LogEnablement.isLogDebugEnabled(fwLogger, "Ignore. This is not a detect delete use case.");
						} else if (!newRelationships.contains(relationship)) {
							retVal.add(WrapperRuleLibrary.PERSONADROP + relationship);
						}
					}
				}
			}
		}
		LogEnablement.isLogDebugEnabled(fwLogger, "End didRelationshipGetDeleted " + retVal);
		return retVal;
	}
	/**
	 * Get Relationship Changes
	 *
	 * @param newIdentity
	 * @param previousIdentity
	 * @param useIgnore
	 * @return
	 * @throws GeneralException
	 */
	public static List getRelationshipChangesPersona(SailPointContext context, Identity newIdentity,
			Identity previousIdentity, boolean useIgnore) throws GeneralException {
		String identityName = null;
		if (newIdentity != null) {
			identityName = newIdentity.getName();
		}
		LogEnablement.isLogDebugEnabled(fwLogger,
				"Enter  WrapperRuleLibrary::getRelationshipChangesPersona.." + identityName);
		List<String> newRelationships = new ArrayList();
		List<String> previousRelationships = new ArrayList();
		List<String> relationshipChange = new ArrayList();
		List<String> dropToIgnore = new ArrayList();
		if (useIgnore) {
			dropToIgnore = getRelationshipDropsToIgnorePersona(context);
			LogEnablement.isLogDebugEnabled(fwLogger, "...dropToIgnore=" + dropToIgnore + "..." + identityName);
		}
		if (newIdentity.getAttribute(WrapperRuleLibrary.PERSONARELATIONSHIPS) != null
				&& ((List) newIdentity.getAttribute(WrapperRuleLibrary.PERSONARELATIONSHIPS)) instanceof List) {
			newRelationships = (List) newIdentity.getAttribute(WrapperRuleLibrary.PERSONARELATIONSHIPS);
			LogEnablement.isLogDebugEnabled(fwLogger, "...newRelationships=" + newRelationships + "..." + identityName);
		}
		if (previousIdentity != null) {
			if (previousIdentity.getAttribute(WrapperRuleLibrary.PERSONARELATIONSHIPS) != null
					&& ((List) previousIdentity
							.getAttribute(WrapperRuleLibrary.PERSONARELATIONSHIPS)) instanceof List) {
				previousRelationships = (List) previousIdentity.getAttribute(WrapperRuleLibrary.PERSONARELATIONSHIPS);
				LogEnablement.isLogDebugEnabled(fwLogger,
						"...previousRelationships=" + previousRelationships + "..." + identityName);
			}
		}
		// If new relationship is null or empty and there is previous relationship
		if (newRelationships == null || newRelationships.size() <= 0) {
			LogEnablement.isLogDebugEnabled(fwLogger, "...newRelationships=" + newRelationships + "..." + identityName);
			LogEnablement.isLogDebugEnabled(fwLogger,
					"...previousRelationships=" + previousRelationships + "..." + identityName);
			if (previousRelationships != null && previousRelationships.size() > 0) {
				for (String prevRelation : previousRelationships) {
					LogEnablement.isLogDebugEnabled(fwLogger, "prevRelation.." + prevRelation + "..." + identityName);
					/**
					 * PREVIOUS ACTIVE OR INACTIVE OR SUSPENDED RELATIONSHIP CALCULATION
					 */
					if (prevRelation.contains(WrapperRuleLibrary.PERSONAACTIVE)
							|| prevRelation.contains(WrapperRuleLibrary.PERSONASUSPENDED)
							|| prevRelation.contains(WrapperRuleLibrary.PERSONAINACTIVE)) {
						if (useIgnore) {
							boolean ignoreFound = false;
							if (dropToIgnore != null && dropToIgnore.size() > 0) {
								for (String singleDrop : dropToIgnore) {
									if (prevRelation.toUpperCase().startsWith(singleDrop.toUpperCase())) {
										ignoreFound = true;
									}
								}
							}
							if (!ignoreFound) {
								relationshipChange.add(WrapperRuleLibrary.PERSONADROP + prevRelation);
							}
						} else {
							relationshipChange.add(WrapperRuleLibrary.PERSONADROP + prevRelation);
						}
					}
				}
			}
		} else if (newRelationships != null && newRelationships.size() > 0) {
			for (String newRelation : newRelationships) {
				if (newRelation != null) {
					LogEnablement.isLogDebugEnabled(fwLogger, "newRelation.." + newRelation + "..." + identityName);
					/**
					 * ACTIVE RELATIONSHIP CALCULATIONS
					 */
					if (newRelation.contains(WrapperRuleLibrary.PERSONAACTIVE)) {
						if (previousRelationships != null && previousRelationships.size() > 0) {
							if (previousRelationships.contains(newRelation)) {
								LogEnablement.isLogDebugEnabled(fwLogger, "NO CHANGE FOR EXISTING ACTIVE RELATIONSHIP "
										+ newRelation + "..." + identityName);
							} else if (previousRelationships.contains(newRelation
									.replace(WrapperRuleLibrary.PERSONAACTIVE, WrapperRuleLibrary.PERSONAINACTIVE))) {
								LogEnablement.isLogDebugEnabled(fwLogger,
										"REACTIVATED SAME RELATIONSHIP ADD " + newRelation + "..." + identityName);
								relationshipChange.add(WrapperRuleLibrary.PERSONAADD + newRelation);
							} else if (previousRelationships.contains(newRelation
									.replace(WrapperRuleLibrary.PERSONAACTIVE, WrapperRuleLibrary.PERSONASUSPENDED))) {
								LogEnablement.isLogDebugEnabled(fwLogger,
										"REACTIVATED SAME RELATIONSHIP FROM LOA/LTD ADD. THIS NEEDS TO BE HANDLED BY HR EVENT RTW LOA / RTW LTD.."
												+ identityName);
								// relationshipChange.add(WrapperRuleLibrary.PERSONAADD+
								// newRelation);
							} else if (!previousRelationships.contains(newRelation)) {
								LogEnablement.isLogDebugEnabled(fwLogger, "NEW ACTIVE RELATIONSHIP ADD " + newRelation);
								relationshipChange.add(WrapperRuleLibrary.PERSONAADD + newRelation);
							}
						}
						// No Previous Relationship, EVERYTHING MUST BE ADD
						else {
							LogEnablement.isLogDebugEnabled(fwLogger, "NO PREVIOUS RELATIONSHIP ADD " + newRelation);
							relationshipChange.add(WrapperRuleLibrary.PERSONAADD + newRelation);
						}
					}
					/**
					 * INACTIVE RELATIONSHIP CALCULATIONS
					 */
					else if (newRelation.contains(WrapperRuleLibrary.PERSONAINACTIVE)) {
						if (previousRelationships != null && previousRelationships.size() > 0) {
							if (previousRelationships.contains(newRelation)) {
								LogEnablement.isLogDebugEnabled(fwLogger,
										"NO CHANGE FOR EXISTING INACTIVE RELATIONSHIP " + newRelation);
							} else if (previousRelationships.contains(newRelation
									.replace(WrapperRuleLibrary.PERSONAINACTIVE, WrapperRuleLibrary.PERSONAACTIVE))) {
								LogEnablement.isLogDebugEnabled(fwLogger,
										"NEW INACTIVE / PREVIOUS ACTIVE PARTIAL TERMINATION  DROP " + newRelation + ".."
												+ identityName);
								if (useIgnore) {
									boolean ignoreFound = false;
									if (dropToIgnore != null && dropToIgnore.size() > 0) {
										for (String singleDrop : dropToIgnore) {
											if (newRelation.toUpperCase().startsWith(singleDrop.toUpperCase())) {
												ignoreFound = true;
											}
										}
									}
									if (!ignoreFound) {
										relationshipChange.add(WrapperRuleLibrary.PERSONADROP + newRelation);
									}
								} else {
									relationshipChange.add(WrapperRuleLibrary.PERSONADROP + newRelation);
								}
							} else if (previousRelationships.contains(newRelation.replace(
									WrapperRuleLibrary.PERSONAINACTIVE, WrapperRuleLibrary.PERSONASUSPENDED))) {
								LogEnablement.isLogDebugEnabled(fwLogger,
										"NEW INACTIVE / PREVIOUS SUSPENDED LOA/LTD TO PARTIAL TERMINATION. "
												+ "THIS NEEDS TO BE HANDLED BY HR EVENTS LOA/LTD" + newRelation + "..."
												+ identityName);
								if (useIgnore) {
									boolean ignoreFound = false;
									if (dropToIgnore != null && dropToIgnore.size() > 0) {
										for (String singleDrop : dropToIgnore) {
											if (newRelation.toUpperCase().startsWith(singleDrop.toUpperCase())) {
												ignoreFound = true;
											}
										}
									}
									if (!ignoreFound) {
										// relationshipChange.add(WrapperRuleLibrary.PERSONADROP+
										// newRelation);
									}
								} else {
									// relationshipChange.add(WrapperRuleLibrary.PERSONADROP+
									// newRelation);
								}
							}
						}
					}
				} // End of FOR Loop for New Relationships
			} // Empty Check New Relationships
			/**
			 * This could be happen when DETECT DELETE IS ON or EDIT QUICK LiNK can drop the
			 * relationship USE CASE 1: PREVIOUS EMPLOYEE ACTIVE STUDENT ACTIVE NEW AND
			 * PREVIOUS EMPLOYEE ACTIVE STUDENT INACTIVE - Phase 1 - Mover /Leaver Event
			 * Kicked off Before, Use Method didRelationshipGetDeleted - returns nothing NEW
			 * EMPLOYEE ACTIVE DETECT DELETE ON - Phase 2 - We Must not kick off
			 * Mover/Leaver Event
			 *
			 * USE CASE 2: Use Method didRelationshipGetDeleted - returns DROP PREVIOUS
			 * EMPLOYEE ACTIVE STUDENT ACTIVE NEW EMPLOYEE ACTIVE DETECT DELETE ON - Phase 1
			 * - Must be a Mover/Leaver Event Kick off
			 */
			if (previousRelationships != null && previousRelationships.size() > 0) {
				List<String> droppedRelationships = didRelationshipGetDeleted(context, newRelationships,
						previousRelationships);
				LogEnablement.isLogDebugEnabled(fwLogger, "...droppedRelationships=" + droppedRelationships);
				if (droppedRelationships != null && droppedRelationships.size() > 0) {
					for (String droppedRelationship : droppedRelationships) {
						if (droppedRelationship.contains(WrapperRuleLibrary.PERSONADROP)) {
							LogEnablement.isLogDebugEnabled(fwLogger,
									"DETECT DELETE ON PARTIAL TERMINATION " + droppedRelationship);
							if (useIgnore) {
								boolean ignoreFound = false;
								if (dropToIgnore != null && dropToIgnore.size() > 0) {
									for (String singleDrop : dropToIgnore) {
										/**
										 * Starts With Operation on String is Used to Ignore Relationship Drops
										 */
										if (singleDrop != null && droppedRelationship != null && droppedRelationship
												.toUpperCase().startsWith(singleDrop.toUpperCase())) {
											ignoreFound = true;
										}
									}
								}
								if (!ignoreFound && droppedRelationship != null) {
									relationshipChange.add(droppedRelationship);
								}
							} else if (droppedRelationship != null) {
								relationshipChange.add(droppedRelationship);
							}
						}
					}
				}
			}
		}
		LogEnablement.isLogDebugEnabled(fwLogger, "Exit getRelationshipChangesPersona " + relationshipChange);
		return relationshipChange;
	}
	/**
	 * Is Link Active
	 *
	 * @param context
	 * @param identity
	 * @param applicationName
	 * @return
	 * @throws Exception
	 */
	public static boolean isLinkActive(SailPointContext context, Identity identity, String applicationName)
			throws Exception {
		LogEnablement.isLogDebugEnabled(fwLogger, "Enter WrapperRuleLibrary::isLinkActive");
		IdentityService idService = new IdentityService(context);
		int returnVal = 0;
		Application app = context.getObjectByName(Application.class, applicationName);
		List<Link> list = idService.getLinks(identity, app);
		LogEnablement.isLogDebugEnabled(fwLogger, "...ApplicationName = " + applicationName);
		if (null != list && list.size() > 0) {
			for (Link link : list) {
				LogEnablement.isLogDebugEnabled(fwLogger, "...Link Found");
				if (null != link && link.getAttribute("IIQDisabled") != null) {
					boolean disabled = false;
					if (link.getAttribute("IIQDisabled") instanceof String) {
						disabled = ("true".equals(link.getAttribute("IIQDisabled")));
					} else {
						disabled = (boolean) link.getAttribute("IIQDisabled");
					}
					if (disabled) {
						LogEnablement.isLogDebugEnabled(fwLogger, "...Link Disabled");
					} else {
						LogEnablement.isLogDebugEnabled(fwLogger, "...Link Not Disabled");
						returnVal += 1;
					}
				} else {
					returnVal += 1;
				}
			}
		}
		if (app != null) {
			context.decache(app);
		}
		LogEnablement.isLogDebugEnabled(fwLogger, "...Return Val = " + applicationName + ":" + returnVal);
		if (returnVal >= 1 && null != list && (list.size() == returnVal)) {
			LogEnablement.isLogDebugEnabled(fwLogger, "Exit isLinkActive");
			return true;
		} else {
			LogEnablement.isLogDebugEnabled(fwLogger, "Exit isLinkActive");
			return false;
		}
	}
	/**
	 * We may want to ignore some relationships during partial leaver
	 *
	 * @return
	 * @throws GeneralException
	 */
	public static String isPersonaEnabled(SailPointContext context) throws GeneralException {
		LogEnablement.isLogDebugEnabled(fwLogger, "Enter WrapperRuleLibrary::isPersonaEnabled");
		String entry = "false";
		if (null == customPersona || customPersona.getAttributes() == null) {
			LogEnablement.isLogDebugEnabled(fwLogger, "Load Custom Persona Object in Memory");
			customPersona = getCustomPersona(context);
		} else {
			LogEnablement.isLogDebugEnabled(fwLogger, "Existing Custom Persona Object in Memory");
		}
		if (customPersona != null && customPersona.getAttributes() != null) {
			Map personaMap = (Map) customPersona.getAttributes().get(WrapperRuleLibrary.PERSONACUSTOMSETTINGS);
			if (personaMap != null && personaMap.containsKey(WrapperRuleLibrary.PERSONAENABLED)) {
				entry = (String) personaMap.get(WrapperRuleLibrary.PERSONAENABLED);
			}
		}
		LogEnablement.isLogDebugEnabled(fwLogger, "Exit WrapperRuleLibrary::isPersonaEnabled " + entry);
		return entry;
	}
	/**
	 * We may want to ignore some relationships during partial leaver
	 *
	 * @return
	 * @throws GeneralException
	 */
	public static List getRelationshipDropsToIgnorePersona(SailPointContext context) throws GeneralException {
		LogEnablement.isLogDebugEnabled(fwLogger, "Enter WrapperRuleLibrary::getRelationshipDropsToIgnore");
		List entry = new ArrayList();
		if (null == customPersona || customPersona.getAttributes() == null) {
			LogEnablement.isLogDebugEnabled(fwLogger, "Load Custom Persona Object in Memory");
			customPersona = getCustomPersona(context);
		} else {
			LogEnablement.isLogDebugEnabled(fwLogger, "Existing Custom Persona Object in Memory");
		}
		if (customPersona != null && customPersona.getAttributes() != null) {
			Map personaMap = (Map) customPersona.getAttributes().get(WrapperRuleLibrary.PERSONACUSTOMSETTINGS);
			if (personaMap != null && personaMap.containsKey(WrapperRuleLibrary.PERSONAMOVERRELATIONSHIPDROP)) {
				entry = (List) personaMap.get(WrapperRuleLibrary.PERSONAMOVERRELATIONSHIPDROP);
			}
		}
		return entry;
	}
	/**
	 * Joiner Add of Relationship
	 *
	 * @param newIdentity
	 * @param previousIdentity
	 * @return
	 * @throws GeneralException
	 */
	public static boolean checkIsNewRelationshipPersona(SailPointContext context, Identity newIdentity,
			Identity previousIdentity) throws GeneralException {
		String identityName = null;
		if (newIdentity != null) {
			identityName = newIdentity.getName();
		}
		LogEnablement.isLogDebugEnabled(fwLogger,
				"Enter WrapperRuleLibrary::checkIsNewRelationshipPersona..." + identityName);
		boolean flag = false;
		if (newIdentity != null && previousIdentity != null) {
			LogEnablement.isLogDebugEnabled(fwLogger, "...NewIdentity and PreviousIdentity Found.." + identityName);
			// CHECK TO SEE IF A IDENTITY MIGHT HAVE A NEW RELATIONSHIP
			List<String> differentRelationships = getRelationshipChangesPersona(context, newIdentity, previousIdentity,
					false);
			List addedRelationships = new ArrayList();
			if (differentRelationships != null && !(differentRelationships.isEmpty())) {
				int differenceCount = 0;
				for (String eachDifference : differentRelationships) {
					if (eachDifference.startsWith(WrapperRuleLibrary.PERSONAADD)) {
						differenceCount += 1;
						addedRelationships.add(eachDifference.replace(WrapperRuleLibrary.PERSONAADD, ""));
					}
				}
				if (differenceCount >= 1) {
					flag = true;
				}
			}
		}
		LogEnablement.isLogDebugEnabled(fwLogger,
				"Exit WrapperRuleLibrary::checkIsNewRelationshipPersona = " + flag + "..." + identityName);
		return flag;
	}
	/**
	 * Mover Persona Message for Certification name
	 *
	 * @param identityName
	 * @param message
	 * @return
	 * @throws GeneralException
	 */
	public static boolean setRelationshipMessagePersona(SailPointContext context, String identityName, String message)
			throws GeneralException {
		LogEnablement.isLogDebugEnabled(fwLogger,
				"Enter WrapperRuleLibrary::setRelationshipMessagePersona.." + identityName);
		boolean retVal = false;
		try {
			if (identityName != null) {
				Configuration configuration = getLifecycleRelationshipConfigPersona(context);
				configuration.put(identityName, message);
				context.saveObject(configuration);
				retVal = true;
			}
		} finally {
			// Doing Commit in the Finally Block
			// anything bad we’ll always end the transaction and release the
			// lock
			context.commitTransaction();
		}
		return retVal;
	}
	/**
	 * Get Partial Leaver Mover Configuration
	 *
	 * @return
	 */
	public static Configuration getLifecycleRelationshipConfigPersona(SailPointContext context) {
		LogEnablement.isLogDebugEnabled(fwLogger, "Enter WrapperRuleLibrary::getLifecycleRelationshipConfigPersona");
		Configuration configuration = null;
		try {
			configuration = ObjectUtil.transactionLock(context, Configuration.class,
					WrapperRuleLibrary.MOVERPERSONACONFIGURATION);
		} catch (GeneralException e) {
			LogEnablement.isLogErrorEnabled(fwLogger, "Not found " + configuration);
		}
		LogEnablement.isLogDebugEnabled(fwLogger, "End getLifecycleRelationshipConfigPersona " + configuration);
		return configuration;
	}
	/**
	 * Get Partial Leaver Mover Certification Name
	 *
	 * @param identityName
	 * @return
	 * @throws GeneralException
	 */
	public static Object getRelationshipMessagePersona(SailPointContext context, String identityName)
			throws GeneralException {
		LogEnablement.isLogDebugEnabled(fwLogger,
				"Enter WrapperRuleLibrary::getRelationshipMessagePersona..." + identityName);
		Object result = null;
		if (identityName != null) {
			Configuration configuration = context.getObjectByName(Configuration.class,
					WrapperRuleLibrary.MOVERPERSONACONFIGURATION);
			LogEnablement.isLogDebugEnabled(fwLogger, "configuration " + configuration);
			if (configuration != null) {
				Attributes attributes = configuration.getAttributes();
				LogEnablement.isLogDebugEnabled(fwLogger, "attributes " + attributes);
				if (attributes != null) {
					Map mainMap = attributes.getMap();
					LogEnablement.isLogDebugEnabled(fwLogger, "mainMap " + mainMap);
					if (mainMap != null) {
						result = mainMap.get(identityName);
					}
				}
				context.decache(configuration);
			}
		}
		LogEnablement.isLogDebugEnabled(fwLogger,
				"End getRelationshipMessagePersona = " + result + "..." + identityName);
		return result;
	}
	/**
	 * Mover Partial Leaver Clean Cert Name
	 *
	 * @param identityName
	 * @throws GeneralException
	 */
	public static void deleteRelationshipMessagePersona(SailPointContext context, String identityName)
			throws GeneralException {
		LogEnablement.isLogDebugEnabled(fwLogger,
				"Enter WrapperRuleLibrary::deleteRelationshipMessagePersona..." + identityName);
		Configuration configuration = null;
		try {
			configuration = getLifecycleRelationshipConfigPersona(context);
			Map existingMap = null;
			if (configuration != null) {
				Attributes existingAtttributes = configuration.getAttributes();
				if (existingAtttributes != null) {
					existingMap = existingAtttributes.getMap();
					if (existingMap != null && existingMap.containsKey(identityName)) {
						LogEnablement.isLogDebugEnabled(fwLogger, "Existing configuration Object " + configuration);
						existingMap.remove(identityName);
						existingAtttributes.setMap(existingMap);
						configuration.setAttributes(existingAtttributes);
						context.saveObject(configuration);
					}
				}
			}
		} catch (GeneralException e) {
			LogEnablement.isLogErrorEnabled(fwLogger, "Error during deletion of Persona event.." + e.getMessage());
		} finally {
			// Doing Commit in the Finally Block
			// Anything bad we’ll always end the transaction and release the
			// lock
			context.commitTransaction();
		}
		LogEnablement.isLogDebugEnabled(fwLogger,
				"Exit WrapperRuleLibrary::deleteRelationshipMessagePersona..." + identityName);
	}
	/**
	 * Returns true if the specified identity has a privileged account on the
	 * specified Application. Returns false otherwise.
	 *
	 * @param identity
	 *            the Identity object
	 * @param appName
	 *            the Application name
	 * @return true if the specified identity has a privileged account on the
	 *         specified Application.
	 * @throws GeneralException
	 */
	public static boolean hasPrimaryAccount(SailPointContext context, Identity identity, Application app)
			throws GeneralException {
		LogEnablement.isLogDebugEnabled(fwLogger, "Enter hasPrimaryAccount");
		boolean hasPrimaryAccount = false;
		IdentityService is = new IdentityService(context);
		if (app != null) {
			List<Link> linksList = is.getLinks(identity, app);
			if (linksList != null) {
				for (Link link : linksList) {
					if (isPrimaryAccount(context, link)) {
						hasPrimaryAccount = true;
					}
				}
			}
		}
		LogEnablement.isLogDebugEnabled(fwLogger, "Exit hasPrimaryAccount: " + hasPrimaryAccount);
		return hasPrimaryAccount;
	}
	/**
	 * Returns true if the specified identity has a privileged account on the
	 * specified Application. Returns false otherwise.
	 *
	 * @param identity
	 *            the Identity object
	 * @param appName
	 *            the Application name
	 * @return true if the specified identity has a privileged account on the
	 *         specified Application.
	 * @throws GeneralException
	 */
	public static boolean hasPrivilegedAccount(SailPointContext context, Identity identity, Application app)
			throws GeneralException {
		LogEnablement.isLogDebugEnabled(fwLogger, "Enter hasPrivilegedAccount");
		boolean hasPrivilegedAccount = false;
		IdentityService is = new IdentityService(context);
		if (app != null) {
			List<Link> linksList = is.getLinks(identity, app);
			if (linksList != null) {
				for (Link link : linksList) {
					if (isPrivilegedAccount(context, link)) {
						hasPrivilegedAccount = true;
					}
				}
			}
		}
		LogEnablement.isLogDebugEnabled(fwLogger, "Exit hasPrivilegedAccount: " + hasPrivilegedAccount);
		return hasPrivilegedAccount;
	}
	/**
	 * Returns true if the specified identity has a privileged account on the
	 * specified Application. Returns false otherwise.
	 *
	 * @param identity
	 *            the Identity object
	 * @param appName
	 *            the Application name
	 * @return true if the specified identity has a privileged account on the
	 *         specified Application.
	 * @throws GeneralException
	 */
	public static boolean hasPrivilegedAccountNativeId(SailPointContext context, Identity identity, String appName,
			String nativeId) throws GeneralException {
		LogEnablement.isLogDebugEnabled(fwLogger, "Enter hasPrivilegedAccountNativeId");
		Application app = context.getObjectByName(Application.class, appName);
		boolean hasPrivilegedAccount = false;
		IdentityService is = new IdentityService(context);
		if (app != null) {
			List<Link> linksList = is.getLinks(identity, app);
			if (linksList != null) {
				for (Link link : linksList) {
					if (isPrivilegedAccountNativeId(context, link, nativeId)) {
						hasPrivilegedAccount = true;
					}
				}
			}
			context.decache(app);
		}
		LogEnablement.isLogDebugEnabled(fwLogger, "Exit hasPrivilegedAccountNativeId: " + hasPrivilegedAccount);
		return hasPrivilegedAccount;
	}
	/**
	 * Returns true if the specified Link is marked as privileged. Returns false
	 * otherwise.
	 *
	 * @param link
	 *            the Link object
	 * @return true if the specified Link is marked as privileged.
	 * @throws GeneralException
	 */
	public static boolean isPrivilegedAccountNativeId(SailPointContext context, Link link, String nativeId)
			throws GeneralException {
		LogEnablement.isLogDebugEnabled(fwLogger, "Enter isPrivilegedAccountNativeId");
		boolean isPrivilegedAccount = false;
		if (link != null) {
			LogEnablement.isLogDebugEnabled(fwLogger, "Link nativeIdentity: " + link.getNativeIdentity());
			LogEnablement.isLogDebugEnabled(fwLogger,
					"Link psAccount: " + link.getAttribute(WrapperRuleLibrary.PSAACCOUNT));
			if (nativeId != null && link.getNativeIdentity() != null
					&& nativeId.equalsIgnoreCase(link.getNativeIdentity())
					&& link.getAttribute(WrapperRuleLibrary.PSAACCOUNT) != null
					&& link.getAttribute(WrapperRuleLibrary.PSAACCOUNT).toString().equalsIgnoreCase("TRUE")) {
				isPrivilegedAccount = true;
			}
		}
		LogEnablement.isLogDebugEnabled(fwLogger, "Exit isPrivilegedAccountNativeId: " + isPrivilegedAccount);
		return isPrivilegedAccount;
	}
	/**
	 * Returns true if the specified Link is marked as privileged. Returns false
	 * otherwise.
	 *
	 * @param link
	 *            the Link object
	 * @return true if the specified Link is marked as privileged.
	 * @throws GeneralException
	 */
	public static boolean isPrivilegedAccount(SailPointContext context, Link link) throws GeneralException {
		LogEnablement.isLogDebugEnabled(fwLogger, "Enter isPrivilegedAccount");
		boolean isPrivilegedAccount = false;
		if (link != null) {
			LogEnablement.isLogDebugEnabled(fwLogger, "Link nativeIdentity: " + link.getNativeIdentity());
			LogEnablement.isLogDebugEnabled(fwLogger,
					"Link psAccount: " + link.getAttribute(WrapperRuleLibrary.PSAACCOUNT));
			if (link.getAttribute(WrapperRuleLibrary.PSAACCOUNT) != null
					&& link.getAttribute(WrapperRuleLibrary.PSAACCOUNT).toString().equalsIgnoreCase("TRUE")) {
				isPrivilegedAccount = true;
			}
		}
		LogEnablement.isLogDebugEnabled(fwLogger, "Exit isPrivilegedAccount: " + isPrivilegedAccount);
		return isPrivilegedAccount;
	}
	/**
	 * Returns true if the specified Link is regular/primary. Returns false
	 * otherwise.
	 *
	 * @param link
	 *            the Link object
	 * @return true if the specified Link is marked as privileged.
	 * @throws GeneralException
	 */
	public static boolean isPrimaryAccount(SailPointContext context, Link link) throws GeneralException {
		LogEnablement.isLogDebugEnabled(fwLogger, "Enter isPrimaryAccount");
		boolean isPrimaryAccount = false;
		if (link != null) {
			LogEnablement.isLogDebugEnabled(fwLogger, "Link nativeIdentity: " + link.getNativeIdentity());
			LogEnablement.isLogDebugEnabled(fwLogger,
					"Link isPrimaryAccount: " + link.getAttribute(WrapperRuleLibrary.PSAACCOUNT));
			if (link.getAttribute(WrapperRuleLibrary.PSAACCOUNT) == null
					|| link.getAttribute(WrapperRuleLibrary.PSAACCOUNT).toString().equalsIgnoreCase("FALSE")) {
				isPrimaryAccount = true;
			}
		}
		LogEnablement.isLogDebugEnabled(fwLogger, "Exit isPrimaryAccount: " + isPrimaryAccount);
		return isPrimaryAccount;
	}
	/**
	 * Returns the account type of the specified Link.
	 *
	 * @param link
	 *            the Link object
	 * @return true if the specified Link is marked as privileged.
	 * @throws GeneralException
	 */
	public static String getLinkAccountType(Link link) throws GeneralException {
		LogEnablement.isLogDebugEnabled(fwLogger, "Enter getLinkAccountType");
		String type = null;
		if (link != null)
		{
			String nativeId=link.getNativeIdentity();
			String appName=link.getApplicationName();
			LogEnablement.isLogDebugEnabled(fwLogger,"Link nativeIdentity: " + nativeId);
			LogEnablement.isLogDebugEnabled(fwLogger,"Link appName: " + appName);
			if (link.getAttribute(WrapperRuleLibrary.PRIVILEGEDACCTTYPE) != null)
			{
				type = link.getAttribute(WrapperRuleLibrary.PRIVILEGEDACCTTYPE).toString();
			}
		}
		fwLogger.debug("Exit getLinkAccountType: " + type);
		return type;
	}
	/**
	 * After Project Compilation
	 *
	 * @param requestType
	 * @param source
	 * @param provisioningProject
	 * @return
	 * @throws GeneralException
	 */
	public static boolean interrogateEligibilityForPrivilegedAccess(SailPointContext context, String requestType,
			String source, ProvisioningProject provisioningProject) throws GeneralException {
		LogEnablement.isLogDebugEnabled(fwLogger, "Enter interrogateEligibilityForPrivilegedAccess Project");
		{
			List<ProvisioningPlan> plans = provisioningProject.getPlans();
			LogEnablement.isLogDebugEnabled(fwLogger, " plans provisioningProject " + plans);
			List provisioningTargets = provisioningProject.getProvisioningTargets();
			if (provisioningTargets != null && provisioningTargets.size() > 0) {
				LogEnablement.isLogDebugEnabled(fwLogger,
						"End interrogateEligibilityForPrivilegedAccess provisioningTargets " + provisioningTargets);
				LogEnablement.isLogDebugEnabled(fwLogger,
						"End interrogateEligibilityForPrivilegedAccess provisioningTargets "
								+ provisioningTargets.size());
			}
			String requestee = provisioningProject.getIdentity();
			for (ProvisioningPlan plan : plans) {
				if (!interrogateEligibilityForPrivilegedAccess(context, requestType, source, plan, provisioningTargets,
						requestee)) {
					/**
					 * If one provisioning plan fails, fail everything
					 */
					return false;
				}
			}
		}
		LogEnablement.isLogDebugEnabled(fwLogger, "End interrogateEligibilityForPrivilegedAccess Project");
		return true;
	}
	/**
	 * Returns true if the requestType is a Cart Request or source is a Batch
	 * Request, which contains an Add or Set operation. Returns false otherwise.
	 *
	 * @param requestType
	 *            the request type
	 * @param source
	 *            the source workflow variable
	 * @param plan
	 *            the ProvisioningPlan object containing the requested items
	 * @return true if the requestType is a Cart Request which contains an Add
	 *         operation.
	 * @throws GeneralException
	 */
	public static boolean interrogateEligibilityForPrivilegedAccess(SailPointContext context, String requestType,
			String source, ProvisioningPlan plan, List provisioningTargetsCompiler, String requesteeStr)
					throws GeneralException {
		LogEnablement.isLogDebugEnabled(fwLogger, "Enter interrogateEligibilityForPrivilegedAccess");
		String roleAccountTypeEnabled=ObjectConfigAttributesRuleLibrary.extendedAttrPrivRoleAccTypesEnabled(context);
		String entAccountTypeEnabled=ObjectConfigAttributesRuleLibrary.extendedAttrPrivEntAccTypesEnabled(context);
		String linkAccountTypeEnabled=ObjectConfigAttributesRuleLibrary.extendedAttrLinkPrivAccountTypeEnabled(context);
		LogEnablement.isLogDebugEnabled(fwLogger, "Enter interrogateEligibilityForPrivilegedAccess..roleAccountTypeEnabled->"+roleAccountTypeEnabled);
		LogEnablement.isLogDebugEnabled(fwLogger, "Enter interrogateEligibilityForPrivilegedAccess..entAccountTypeEnabled->"+entAccountTypeEnabled);
		LogEnablement.isLogDebugEnabled(fwLogger, "Enter interrogateEligibilityForPrivilegedAccess..linkAccountTypeEnabled->"+linkAccountTypeEnabled);
		List<String> acctTypesList = null;
		IdentityService is = new IdentityService(context);
		if (plan != null) {
			Identity requestee = plan.getIdentity();
			if (requestee != null) {
				LogEnablement.isLogDebugEnabled(fwLogger, "requestee from plan.." + requestee.getName());
			}
			Identity newRequestee = null;
			if (requestee == null && requesteeStr != null) {
				newRequestee = context.getObjectByName(Identity.class, requesteeStr);
				if (newRequestee != null) {
					LogEnablement.isLogDebugEnabled(fwLogger, "requestee from arguments.." + newRequestee.getName());
				}
			}
			Attributes planAttributes = plan.getArguments();
			List<ProvisioningTarget> provisioningTargets = null;
			if (provisioningTargetsCompiler != null && provisioningTargetsCompiler.size() > 0) {
				LogEnablement.isLogDebugEnabled(fwLogger,
						"interrogateEligibilityForPrivilegedAccess...provisioningTargetsCompiler.."
								+ provisioningTargetsCompiler);
				provisioningTargets = provisioningTargetsCompiler;
			} else {
				provisioningTargets = plan.getProvisioningTargets();
			}
			String serviceCube = null;
			if (requestee != null) {
				serviceCube = (String) requestee.getAttribute(WrapperRuleLibrary.SERVICE_CUBE_ATTR);
			} else if (newRequestee != null) {
				serviceCube = (String) newRequestee.getAttribute(WrapperRuleLibrary.SERVICE_CUBE_ATTR);
				requestee = newRequestee;
			}
			Boolean enablepsaValidation = false;
			LogEnablement.isLogDebugEnabled(fwLogger, "serviceCube.." + serviceCube);
			if (serviceCube == null || serviceCube.equalsIgnoreCase("false")) {
				if ((requestType != null && (requestType.equalsIgnoreCase(WrapperRuleLibrary.CART_REQUEST_FEATURE)
						|| requestType.contains(WrapperRuleLibrary.JOINER)))
						|| (source != null && source.equalsIgnoreCase(WrapperRuleLibrary.BATCH))) {
					LogEnablement.isLogDebugEnabled(fwLogger,
							"interrogateEligibilityForPrivilegedAccess..requestType..." + requestType);
					LogEnablement.isLogDebugEnabled(fwLogger,
							"interrogateEligibilityForPrivilegedAccess..source..." + source);
					List<AccountRequest> acctReqList = plan.getAccountRequests();
					if (acctReqList != null) {
						for (AccountRequest acctReq : acctReqList)
						{
							if (acctReq != null && acctReq.getOperation() != null && acctReq.getOperation()
									.equals(ProvisioningPlan.AccountRequest.Operation.Enable)) {
								LogEnablement.isLogDebugEnabled(fwLogger, "Enabling  Account...");
								continue;
							}
							if (acctReq != null && acctReq.getOperation() != null && acctReq.getOperation()
									.equals(ProvisioningPlan.AccountRequest.Operation.Disable)) {
								LogEnablement.isLogDebugEnabled(fwLogger, "Disabling  Account...");
								continue;
							}
							if (acctReq != null && acctReq.getOperation() != null
									&& acctReq.getOperation().equals(ProvisioningPlan.AccountRequest.Operation.Lock)) {
								LogEnablement.isLogDebugEnabled(fwLogger, "Locking  Account...");
								continue;
							}
							if (acctReq != null && acctReq.getOperation() != null && acctReq.getOperation()
									.equals(ProvisioningPlan.AccountRequest.Operation.Unlock)) {
								LogEnablement.isLogDebugEnabled(fwLogger, "Unlocking  Account...");
								continue;
							}
							if (acctReq != null && acctReq.getOperation() != null && acctReq.getOperation()
									.equals(ProvisioningPlan.AccountRequest.Operation.Delete)) {
								LogEnablement.isLogDebugEnabled(fwLogger, "Deleting  Account...");
								continue;
							}
							String appName = acctReq.getApplicationName();
							Application app = context.getObjectByName(Application.class, appName);
							if ((appName.equalsIgnoreCase(ProvisioningPlan.APP_IIQ)
									|| appName.equalsIgnoreCase(ProvisioningPlan.IIQ_APPLICATION_NAME)
									|| appName.equalsIgnoreCase(ProvisioningPlan.APP_IDM))) {
								enablepsaValidation = true;
								// We are going to handle during attribute
								// request iteration
							} else if (app != null && app
									.getAttributeValue(ObjectConfigAttributesRuleLibrary.VALIDATEPRIVACCOUNT) != null) {
								enablepsaValidation = (Boolean) app
										.getAttributeValue(ObjectConfigAttributesRuleLibrary.VALIDATEPRIVACCOUNT);
							}
							if (enablepsaValidation)
							{
								boolean oneOfLinkIsPrivileged = hasPrivilegedAccount(context, requestee, app);
								boolean oneofLinkIsPrimary = hasPrimaryAccount(context, requestee, app);
								List<AttributeRequest> attrReqList = acctReq.getAttributeRequests();
								boolean roleRequest = false;
								if (attrReqList != null)
								{
									for (AttributeRequest attrReq : attrReqList) {
										acctTypesList = new ArrayList<String>();
										boolean isPrivilegedAccess = false;
										String roleNamePP = null;
										if (attrReq.getOp().equals(ProvisioningPlan.Operation.Add)
												|| attrReq.getOp().equals(ProvisioningPlan.Operation.Set)) {
											LogEnablement.isLogDebugEnabled(fwLogger,
													"interrogateEligibilityForPrivilegedAccess...ProvisioningPlan.Operation.Add/Set");
											String attrName = attrReq.getName();
											Object value = attrReq.getValue();
											String valueStr = "";
											/**
											 * All the values should be String, compiler can covert them into List May
											 * need to check for list too
											 */
											if (value != null) {
    											if (value instanceof List) {
    												valueStr = ((List) value).get(0).toString();
    											} else {
    												valueStr = value.toString();
    											}
											}
											
											LogEnablement.isLogDebugEnabled(fwLogger,
													"Checking requested access: appName=" + appName + " attrName="
															+ attrName + " valueStr=" + valueStr);
											QueryOptions qo = new QueryOptions();
											Filter queryFilter = null;
											// If a role was requested
											if ((appName.equalsIgnoreCase(ProvisioningPlan.APP_IIQ)
													|| appName.equalsIgnoreCase(ProvisioningPlan.APP_IDM)
													|| appName.equalsIgnoreCase(ProvisioningPlan.IIQ_APPLICATION_NAME))
													&& (attrName
															.equalsIgnoreCase(ProvisioningPlan.ATT_IIQ_ASSIGNED_ROLES)
															|| attrName.equalsIgnoreCase(
																	ProvisioningPlan.ATT_IIQ_DETECTED_ROLES))) {
												Bundle role = context.getObjectByName(Bundle.class, valueStr);
												Boolean bundlePsaValidation = false;
												roleRequest = true;
												Set<Application> applications = role.getApplications();
												LogEnablement.isLogDebugEnabled(fwLogger,
														"applications=" + applications);
												HashSet countApp = new HashSet();
												HashSet atleastOnePrivilegedAccount = new HashSet();
												HashSet atleastOneRegularAccount = new HashSet();
												// Applications will be empty
												// for business roles, however,
												// Permitted roles will have
												// applications
												if (applications != null && !applications.isEmpty()) {
													for (Application appSet : applications) {
														countApp.add(appSet.getName());
														if (appSet != null) {
															if (appSet.getAttributeValue(
																	ObjectConfigAttributesRuleLibrary.VALIDATEPRIVACCOUNT) != null) {
																enablepsaValidation = (Boolean) appSet
																		.getAttributeValue(
																				ObjectConfigAttributesRuleLibrary.VALIDATEPRIVACCOUNT);
															}
														}
														if (enablepsaValidation) {
															bundlePsaValidation = true;
														}
														if (hasPrivilegedAccount(context, requestee, appSet)) {
															atleastOnePrivilegedAccount.add(appSet.getName());
														}
														if (hasPrimaryAccount(context, requestee, appSet)) {
															atleastOneRegularAccount.add(appSet.getName());
														}
													}
												}
												// Lets explore IT Roles
												else {
													List<Bundle> requiredBundles = role.getRequirements();
													LogEnablement.isLogDebugEnabled(fwLogger,
															"...Role RequiredBundles  = " + requiredBundles);
													if (requiredBundles != null) {
														LogEnablement.isLogDebugEnabled(fwLogger,
																"...Role RequiredBundles  Size = "
																		+ requiredBundles.size());
														for (Bundle requiredBundle : requiredBundles) {
															Set<Application> setReqAppNames = requiredBundle
																	.getApplications();
															LogEnablement.isLogDebugEnabled(fwLogger,
																	"...Role Required Applications  = "
																			+ setReqAppNames);
															if (setReqAppNames != null && !setReqAppNames.isEmpty()) {
																for (Application appSet : setReqAppNames) {
																	if (appSet != null) {
																		if (appSet.getAttributeValue(
																				ObjectConfigAttributesRuleLibrary.VALIDATEPRIVACCOUNT) != null) {
																			enablepsaValidation = (Boolean) appSet
																					.getAttributeValue(
																							ObjectConfigAttributesRuleLibrary.VALIDATEPRIVACCOUNT);
																		}
																	}
																	if (enablepsaValidation) {
																		bundlePsaValidation = true;
																	}
																	countApp.add(appSet.getName());
																	if (hasPrivilegedAccount(context, requestee,
																			appSet)) {
																		atleastOnePrivilegedAccount
																		.add(appSet.getName());
																	}
																	if (hasPrimaryAccount(context, requestee, appSet)) {
																		atleastOneRegularAccount.add(appSet.getName());
																	}
																}
															}
														}
													}
												}
												LogEnablement.isLogDebugEnabled(fwLogger,
														"atleastOnePrivilegedAccount=" + atleastOnePrivilegedAccount);
												LogEnablement.isLogDebugEnabled(fwLogger, "countApp=" + countApp);
												if (atleastOnePrivilegedAccount.size() > 0) {
													if (countApp.size() == atleastOnePrivilegedAccount.size()) {
														oneOfLinkIsPrivileged = true;
													}
												}
												LogEnablement.isLogDebugEnabled(fwLogger,
														"atleastOneRegularAccount=" + atleastOneRegularAccount);
												LogEnablement.isLogDebugEnabled(fwLogger, "countApp=" + countApp);
												if (atleastOneRegularAccount.size() > 0) {
													if (countApp.size() == atleastOneRegularAccount.size()) {
														oneofLinkIsPrimary = true;
													}
												}
												roleNamePP = role.getName();
												if (role != null)
												{
													if (role.getAttribute(WrapperRuleLibrary.ROLEPRIVILEGED) != null
															&& role.getAttribute(WrapperRuleLibrary.ROLEPRIVILEGED)
															.toString().equalsIgnoreCase("TRUE"))
													{
														isPrivilegedAccess = true;
													}
													if(roleAccountTypeEnabled!=null && roleAccountTypeEnabled.equalsIgnoreCase("TRUE"))
													{
														if (role.getAttribute(WrapperRuleLibrary.PRIVILEGEDACCTTYPE) != null &&
																role.getAttribute(WrapperRuleLibrary.PRIVILEGEDACCTTYPE).toString().trim().length() > 0) {
															acctTypesList = Util.csvToList(role.getAttribute(WrapperRuleLibrary.PRIVILEGEDACCTTYPE).toString().trim());
														}
													}
													context.decache(role);
												}
												if (!bundlePsaValidation)
												{
													LogEnablement.isLogDebugEnabled(fwLogger,"Role PSA Validation Ignored= Application Validation Disabled");
													break;
												}
											}
											else
											{
												if(entAccountTypeEnabled!=null && entAccountTypeEnabled.equalsIgnoreCase("true"))
												{
													LogEnablement.isLogDebugEnabled(fwLogger, "Find Managed Attribute Object");
													// If an entitlement was requested
													queryFilter = Filter.and(Filter.eq("attribute", attrName), Filter.eq("value", valueStr), Filter.eq("application.name", appName));
													qo.addFilter(queryFilter);
													Iterator resultIteratorOb = context.search(ManagedAttribute.class, qo);
													ManagedAttribute ma = null;
													while (resultIteratorOb != null && resultIteratorOb.hasNext())
													{
														ma = (ManagedAttribute) resultIteratorOb.next();
														if (ma != null && ma.getAttribute(WrapperRuleLibrary.PRIVILEGEDACCTTYPE) != null && ma.getAttribute(WrapperRuleLibrary.PRIVILEGEDACCTTYPE).toString().trim().length() > 0) {
															acctTypesList = Util.csvToList(ma.getAttribute(WrapperRuleLibrary.PRIVILEGEDACCTTYPE).toString().trim());
															fwLogger.debug("Getting Account types from ManagedAttribute: " + acctTypesList);
														}
													}
													if(ma!=null)
													{
														context.decache(ma);
													}
												}
												LogEnablement.isLogDebugEnabled(fwLogger, "Find Managed Attribute Object");
												queryFilter = Filter.and(Filter.eq("attribute", attrName),
														Filter.eq("value", valueStr),
														Filter.eq("application.name", appName));
												qo.addFilter(queryFilter);
												List properties = new ArrayList();
												properties.add("id");
												properties.add(WrapperRuleLibrary.ENTITLEMENTPRIVILEGED);
												Iterator resultIterator = context.search(ManagedAttribute.class, qo,properties);
												LogEnablement.isLogDebugEnabled(fwLogger,"Find Managed Attribute resultIterator " + resultIterator);
												if (resultIterator != null) {
													while (resultIterator.hasNext()) {
														LogEnablement.isLogDebugEnabled(fwLogger,"resultIterator.hasNext()");
														Object[] retObjs = (Object[]) resultIterator.next();
														LogEnablement.isLogDebugEnabled(fwLogger, "retObjs " + retObjs);
														LogEnablement.isLogDebugEnabled(fwLogger,
																"retObjs.length " + retObjs.length);
														if (retObjs != null && retObjs.length == 2) {
															LogEnablement.isLogDebugEnabled(fwLogger,
																	"retObjs[0] " + retObjs[0]);
															LogEnablement.isLogDebugEnabled(fwLogger,
																	"retObjs[1] " + retObjs[1]);
															if (retObjs[0] != null && retObjs[1] != null) {
																String id = retObjs[0].toString();
																String entPrivileged = retObjs[1].toString();
																LogEnablement.isLogDebugEnabled(fwLogger,"entPrivileged " + entPrivileged);
																if (entPrivileged != null && entPrivileged.toString().equalsIgnoreCase("TRUE")) {
																	isPrivilegedAccess = true;
																}
															} else if (retObjs[0] != null && retObjs[1] == null)
															{
																// entPrivileged
																// has no value
																isPrivilegedAccess = false;
															}
															else if (retObjs[0] == null && retObjs[1] == null)
															{
																// Entitlement
																// doesn't exist
																// or or it is a
																// expansion
																// items
																isPrivilegedAccess = false;
																// Go to Next
																// Attribute
																// Request
																LogEnablement.isLogDebugEnabled(fwLogger,"Skip Validation because Entitlement doesn't exist or it is an expansion item");
																continue;
															}
														}
													}
													Util.flushIterator(resultIterator);
												}
											}
											//Calculate Mismatch of Account Types
											LogEnablement.isLogDebugEnabled(fwLogger,
													"ProvisioningTarget " + provisioningTargets);
											// Get Selected Native Id Link from
											// Provisioning Targets
											// In Case of batch Request Get
											// Native Id from Account Request
											boolean validateAgain = false;
											String nativeIdFromAccountSelectionPerApplication = null;
											if (provisioningTargets != null)
											{
												for (ProvisioningTarget target : provisioningTargets)
												{
													String applicationNameTarget = target.getApplication();
													LogEnablement.isLogDebugEnabled(fwLogger,"applicationNameTarget " + applicationNameTarget);
													String attributeTarget = null;
													Object valueTarget = null;
													String roleNameTarget = null;
													List<AccountSelection> accountSelectionsList = target.getAccountSelections();
													if (target.getRole() != null)
													{
														roleNameTarget = target.getRole();
													}
													else
													{
														attributeTarget = target.getAttribute();
														valueTarget = target.getValue();
														if (attributeTarget == null || valueTarget == null)
														{
															validateAgain = true;
														}
													}
													if (accountSelectionsList != null
															&& accountSelectionsList.size() > 0) {
														for (AccountSelection accountSelect : accountSelectionsList) {
															if (accountSelect != null
																	&& acctReq.getApplicationName() != null) {
																LogEnablement.isLogDebugEnabled(fwLogger,
																		"acctReq.getApplicationName() "
																				+ acctReq.getApplicationName());
																LogEnablement.isLogDebugEnabled(fwLogger,
																		"accountSelect.getApplicationName() "
																				+ accountSelect.getApplicationName());
															}
															if (accountSelect != null
																	&& accountSelect.getApplicationName() != null
																	&& acctReq.getApplicationName() != null
																	&& accountSelect.getApplicationName()
																	.equalsIgnoreCase(
																			acctReq.getApplicationName())) {
																nativeIdFromAccountSelectionPerApplication = accountSelect
																		.getSelection();
																LogEnablement.isLogDebugEnabled(fwLogger,
																		"nativeIdFromAccountSelectionPerApplication "
																				+ nativeIdFromAccountSelectionPerApplication);
															}
														}
													} else if (accountSelectionsList == null
															|| accountSelectionsList.size() <= 0) {
														validateAgain = true;
													}
													// Attribute and Value on
													// Account Selection can be
													// null for batch request
													if (accountSelectionsList != null
															&& (roleNameTarget != null && roleNamePP != null
															&& roleNameTarget.equalsIgnoreCase(roleNamePP))
															|| (attrName != null && attributeTarget != null
															&& attrName.equalsIgnoreCase(attributeTarget)
															&& valueTarget != null && valueStr != null
															&& valueTarget != null && valueStr
															.equalsIgnoreCase((String) valueTarget))) {
														for (AccountSelection accountSelection : accountSelectionsList)
														{
															String nativeId = accountSelection.getSelection();
															/**
															 * Implicit Create
															 */
															boolean isImplicitCreate = accountSelection
																	.isImplicitCreate();
															/**
															 * Additional Accounts
															 */
															boolean isDoCreate = accountSelection.isDoCreate();
															/**
															 * Application Name
															 */
															String accountSelectionApplicationName = accountSelection
																	.getApplicationName();
															/**
															 * Get Account Info Native Id
															 */
															List<AccountInfo> accountsInfos = accountSelection.getAccounts();
															if (isImplicitCreate || isDoCreate)
															{
																if (isPrivilegedAccess && !oneofLinkIsPrimary)
																{
																	LogEnablement.isLogDebugEnabled(fwLogger,"Exiting  - Primary Account must exist before priviliged access is requested...");
																	return false;
																}
																else
																{
																	LogEnablement.isLogDebugEnabled(fwLogger,"Existing Link is Primary and About to Create NEW Link...");
																	if (planAttributes != null)
																	{
																	    planAttributes.put("secondaryAccount", true);
																	    if(acctTypesList!=null && !acctTypesList.isEmpty())
																	    {
																	        planAttributes.put("accounTypes", Util.listToCsv(acctTypesList));
																	    }
																	}
																	break;
																}
															}
															boolean selectedAccountPrivileged = hasPrivilegedAccountNativeId(context, requestee, accountSelectionApplicationName,nativeId);
															String linkAccountType = null;
															Link link = is.getLink(requestee, app, null, nativeId);
															if(linkAccountTypeEnabled!=null && linkAccountTypeEnabled.equalsIgnoreCase("True"))
															{
																linkAccountType = getLinkAccountType(link);
															}
															fwLogger.debug("Account type in Link: " + linkAccountType + ". Allowed account types on entitlements/roles: " + acctTypesList);
															if(nativeId!=null)
															fwLogger.debug("nativeId = " + nativeId.toString());
															LogEnablement.isLogDebugEnabled(fwLogger,"selectedAccountPrivileged.."+ selectedAccountPrivileged + ","+ nativeId);
															LogEnablement.isLogDebugEnabled(fwLogger,"isPrivilegedAccess.." + isPrivilegedAccess+ ",Role," + roleNameTarget);
															LogEnablement.isLogDebugEnabled(fwLogger,
																	"isPrivilegedAccess.." + isPrivilegedAccess
																	+ ",Ent," + valueTarget);
															LogEnablement.isLogDebugEnabled(fwLogger,
																	"oneOfLinkIsPrivileged.." + oneOfLinkIsPrivileged);
															LogEnablement.isLogDebugEnabled(fwLogger,
																	"oneOfLinkIsPrimary.." + oneofLinkIsPrimary);
															// Regular Access Cannot be assigned to privileged account
															if (!isPrivilegedAccess && selectedAccountPrivileged)
															{
																LogEnablement.isLogDebugEnabled(fwLogger,"Exiting  - Non-privileged access and privileged account privilege level do not match...");
																return false;
															}
															// Privileged access cannot be assigned to regular account,if there is a privileged account
															else if (oneOfLinkIsPrivileged && isPrivilegedAccess && !selectedAccountPrivileged)
															{
																LogEnablement.isLogDebugEnabled(fwLogger,"Exiting  - Privileged access and non-privileged account privilege level do not match...");
																return false;
															}
															//Validation to ensure manually selected native id(Link) account type or existing native id(Link) account type from account selector rule matches with one of the requested entitlement/role account types
															//Entitlement/Role account types can be comma separated account type values for example Tier 1, Tier 2
															//User or Batch Request must select new account or return new account from account selector rule  to create new account Type for example Tier 3. This will be allowed only when requested entitlement/role accoun type is Tier 3
															//Only if selected accounttype matches with one of the requested entitlement/role account type
															else if (isPrivilegedAccess && acctTypesList!=null && !acctTypesList.isEmpty() && Util.isNotNullOrEmpty(linkAccountType) && !Util.containsAny(acctTypesList,Util.csvToList(linkAccountType.trim()))) {
																fwLogger.debug("Exiting - Account type " + nativeId + " doesn't match allowed account type(s) in requested access...");
																return false;
															}
															// Privileged access gets assigned to regular account, if there is no privileged account, create one If requestee identity doesn't
															// have a privileged account, that means all of the requested access will show as linked to the regular (non-privileged) account. This is valid, because later we'll modify the plan to create the privileged account and link the privileged access to it.
															// However, if the user does have a privileged account, that means each requested access should be properly linked to the right account
															else if (!oneOfLinkIsPrivileged && isPrivilegedAccess
																	&& !selectedAccountPrivileged)
															{
																// Modify the  Project Plan
																/**
																 * Before Plan Modification, Make sure there is at least
																 * one primary account
																 */
																LogEnablement.isLogDebugEnabled(fwLogger,
																		"Exiting  - Privileged Access, Modify the Project Plan...");
																accountSelection.setDoCreate(true);
																accountSelection.setSelection(null);
																accountSelection.setAccounts(null);
																if (!roleRequest)
																{
																	acctReq.setOperation(
																			AccountRequest.Operation.Create);
																	Attributes attribute = acctReq.getArguments();
																	if (attribute != null)
																	{
																		attribute.put("forceNewAccount", true);
																	}
																	acctReq.setNativeIdentity(null);
																}
																if (planAttributes != null)
																{
																	planAttributes.put("secondaryAccount", true);
																	if(acctTypesList!=null && !acctTypesList.isEmpty())
																	{
																		planAttributes.put("accounTypes", Util.listToCsv(acctTypesList));
																	}
																}
															}
														}
													}
												}
											}
											else
											{
												LogEnablement.isLogDebugEnabled(fwLogger, "No provisioning Targets");
												validateAgain = true;
											}
											if (validateAgain)
											{
												LogEnablement.isLogDebugEnabled(fwLogger, "Validating Again");
												// No Provisioning Targets, This
												// can happen for batch requests
												// AddEntitlement Batch Request
												// can have nativeId column in
												// csv, as a result, this puts
												// native id on account request,
												// before compilation
												/**
												 * Below Validation has nothing to do with native id Make sure if we are
												 * creating new accounts and access is privileged request (either role
												 * or entitlement), primary account exists first
												 */
												if (acctReq != null && acctReq.getOperation() != null
														&& acctReq.getOperation().equals(
																ProvisioningPlan.AccountRequest.Operation.Create))
												{
													if (isPrivilegedAccess && !oneofLinkIsPrimary)
													{
														LogEnablement.isLogDebugEnabled(fwLogger,
																"Exiting  - Primary Account must exist before priviliged access is requested...");
														return false;
													}
													else
													{
														LogEnablement.isLogDebugEnabled(fwLogger,
																"Primary Link Exists - Create NEW Link...");
														break;
													}
												}
												String nativeId = acctReq.getNativeIdentity();
												if (nativeId == null)
												{
													nativeId = nativeIdFromAccountSelectionPerApplication;
													LogEnablement.isLogDebugEnabled(fwLogger,
															"nativeIdFromAccountSelectionPerApplication..nativeIdFromAccountSelectionPerApplication.."
																	+ nativeIdFromAccountSelectionPerApplication);
													LogEnablement.isLogDebugEnabled(fwLogger,
															"nativeIdFromAccountSelectionPerApplication..nativeId.."
																	+ nativeId);
												}
												else
												{
													LogEnablement.isLogDebugEnabled(fwLogger,"nativeId..from accountrequest" + nativeId);
												}
												if (nativeId != null)
												{
													String linkAccountType = null;
													Link link = is.getLink(requestee, app, null, nativeId);
													if(linkAccountTypeEnabled!=null && linkAccountTypeEnabled.equalsIgnoreCase("True"))
													{
														linkAccountType = getLinkAccountType(link);
													}
													fwLogger.debug("Account type in Link: " + linkAccountType + ". Allowed account types on entitlements/roles: " + acctTypesList);
													fwLogger.debug("nativeId = " + nativeId.toString());
													boolean selectedAccountPrivileged = hasPrivilegedAccountNativeId(
															context, requestee, acctReq.getApplicationName(), nativeId);
													LogEnablement.isLogDebugEnabled(fwLogger,
															"selectedAccountPrivileged.." + selectedAccountPrivileged
															+ "," + nativeId);
													LogEnablement.isLogDebugEnabled(fwLogger,
															"isPrivilegedAccess.." + isPrivilegedAccess
															+ ",Role Validation Compilation," + roleNamePP);
													LogEnablement.isLogDebugEnabled(fwLogger,
															"isPrivilegedAccess.." + isPrivilegedAccess
															+ ",Ent Validation Compilation," + valueStr);
													LogEnablement.isLogDebugEnabled(fwLogger,
															"oneOfLinkIsPrivileged.." + oneOfLinkIsPrivileged);
													LogEnablement.isLogDebugEnabled(fwLogger,
															"oneOfLinkIsPrimary.." + oneofLinkIsPrimary);
													// Regular Access Cannot be
													// assigned to privileged
													// account
													if (!isPrivilegedAccess && selectedAccountPrivileged)
													{
														LogEnablement.isLogDebugEnabled(fwLogger,"Exiting  - Non-privileged access and privileged account privilege level do not match...");
														return false;
													}
													// Privileged access cannot
													// be assigned to regular
													// account, if there is a
													// privileged account
													else if (oneOfLinkIsPrivileged && isPrivilegedAccess
															&& !selectedAccountPrivileged)
													{
														LogEnablement.isLogDebugEnabled(fwLogger,"Exiting  - Privileged access and non-privileged account privilege level do not match...");
														return false;
													}
													//Validation to ensure manually selected native id(Link) account type or existing native id(Link) account type from account selector rule matches with one of the requested entitlement/role account types
													//Entitlement/Role account types can be comma separated account type values for example Tier 1, Tier 2
													//User or Batch Request must select new account or return new account from account selector rule  to create new account Type for example Tier 3. This will be allowed only when requested entitlement/role accoun type is Tier 3
													//Only if selected account type matches with one of the requested entitlement/role account type
													else if (isPrivilegedAccess && acctTypesList!=null && !acctTypesList.isEmpty() && Util.isNotNullOrEmpty(linkAccountType) && !Util.containsAny(acctTypesList,Util.csvToList(linkAccountType.trim())))
													{
														fwLogger.debug("Exiting - Account type " + nativeId + " doesn't match allowed account type(s) in requested access...");
														return false;
													}
													else if (!oneOfLinkIsPrivileged && isPrivilegedAccess && !selectedAccountPrivileged)
													{
														LogEnablement.isLogDebugEnabled(fwLogger,"Modify Project Plan...");
														// Modify the Project Plan
														if (!roleRequest)
														{
															acctReq.setOperation(AccountRequest.Operation.Create);
															Attributes attribute = acctReq.getArguments();
															if (attribute != null)
															{
																attribute.put("forceNewAccount", true);
															}
															acctReq.setNativeIdentity(null);
														}
														if (planAttributes != null)
														{
															planAttributes.put("secondaryAccount", true);
															if(acctTypesList!=null && !acctTypesList.isEmpty())
															{
																planAttributes.put("accounTypes", Util.listToCsv(acctTypesList));
															}
														}
													}
												}
												else
												{
													LogEnablement.isLogDebugEnabled(fwLogger,"NO PSA VALIDATION...WILL TRY AFTER PROJECT COMPILATION");
												}
											}
										} // Attribute Request Add or Set
									}
								}
							} // Enable PSA Validation / Application
							else {
								LogEnablement.isLogDebugEnabled(fwLogger,
										"Entitlement PSA Validation Ignored = Application Validation Disabled");
							}
							if (app != null) {
								context.decache(app);
							}
						}
					}
				}
			} // Only do this for Human cubes
			if (newRequestee != null) {
				context.decache(newRequestee);
			}
		}
		LogEnablement.isLogDebugEnabled(fwLogger,
				"Exit interrogateEligibilityForPrivilegedAccessLevel isEligible= " + true);
		return true;
	}
	/**
	 * Use Regular Expression to find secondary accounts, This could be a string
	 * comparison
	 */
	public static boolean isSecondaryAccount(SailPointContext context, Link link) {
		boolean result = false;
		LogEnablement.isLogDebugEnabled(fwLogger, "Enter....isSecondaryAccount");
		// Let's ignore secondary account
		if (link.getAttribute(WrapperRuleLibrary.PSAACCOUNT) != null
				&& ((String) link.getAttribute(WrapperRuleLibrary.PSAACCOUNT)).equalsIgnoreCase("TRUE")) {
			result = true;
		}
		LogEnablement.isLogDebugEnabled(fwLogger, "....isSecondaryAccount..." + result);
		return result;
	}
	/**
	 * Generate Intercepted Password Plan for Target Applications ONLY Service Cubes
	 * are Ignored Secondary Accounts are Ignored, if checked
	 *
	 * @param applicationName
	 * @param identityName
	 * @param nativeIdentity
	 * @param password
	 * @return
	 * @throws GeneralException
	 */
	public static ProvisioningPlan buildInterceptorPlan(SailPointContext context, String applicationName,
			String identityName, String nativeIdentity, String password) throws GeneralException {
		ProvisioningPlan plan = null;
		// Let's do Service Cube Check First
		Identity identity = context.getObjectByName(Identity.class, identityName);
		String serviceCube = (String) identity.getAttribute(WrapperRuleLibrary.SERVICE_CUBE_ATTR);
		List<String> targetList = null;
		if (serviceCube == null || serviceCube.equalsIgnoreCase("false")) {
			Application app = context.getObjectByName(Application.class, applicationName);
			if (app != null) {
				LogEnablement.isLogDebugEnabled(fwLogger, "requestedSourceAppName " + applicationName);
				String targetApps = (String) app.getAttributeValue(WrapperRuleLibrary.TARGETPASSWORDSYNC);
				String sourceattrName = ProvisioningPlan.ATT_PASSWORD;
				LogEnablement.isLogDebugEnabled(fwLogger, " targetApps " + targetApps);
				LogEnablement.isLogDebugEnabled(fwLogger, " sourceattrName " + sourceattrName);
				Boolean primarySyncaccounts = (Boolean) app.getAttributeValue(WrapperRuleLibrary.PRIMARYSYNCACCOUNTS);
				IdentityService idService = new IdentityService(context);
				List<Link> listLinks = idService.getLinks(identity, app);
				String psaRegularAttrExpression = (String) app
						.getAttributeValue(WrapperRuleLibrary.PSAREGULARACCOUNTEXPRESSION);
				LogEnablement.isLogDebugEnabled(fwLogger, "...listLinks = " + listLinks);
				LogEnablement.isLogDebugEnabled(fwLogger, "...psaRegularAttrExpression = " + psaRegularAttrExpression);
				LogEnablement.isLogDebugEnabled(fwLogger, "...primarySyncaccounts = " + primarySyncaccounts);
				targetList = Util.csvToList(targetApps);
				LogEnablement.isLogDebugEnabled(fwLogger, "targetList " + targetList);
				// There should be source application links
				if (listLinks != null && listLinks.size() >= 1) {
					plan = new ProvisioningPlan();
					plan.setIdentity(identity);
					LogEnablement.isLogDebugEnabled(fwLogger, "...Multiple Links = " + listLinks.size());
					for (Link link : listLinks) {
						String linkNativeId = link.getNativeIdentity();
						LogEnablement.isLogDebugEnabled(fwLogger, "...linkNativeId = " + linkNativeId);
						// If Primary Synch is checked, ensure this is not a
						// password for secondary account
						// If yes, go to next link
						if (linkNativeId != null && nativeIdentity != null
								&& !linkNativeId.equalsIgnoreCase(nativeIdentity)) {
							LogEnablement.isLogDebugEnabled(fwLogger,
									"...Intercepted Native Id Link Doesn't Match with Link Native Id " + app.getName()
									+ "..linkNativeId.." + linkNativeId + ".nativeId.." + nativeIdentity);
							continue;
						} else if (linkNativeId != null && nativeIdentity != null
								&& linkNativeId.equalsIgnoreCase(nativeIdentity) && primarySyncaccounts != null
								&& primarySyncaccounts.booleanValue() && psaRegularAttrExpression != null
								&& isSecondaryAccount(context, link)) {
							LogEnablement.isLogDebugEnabled(fwLogger,
									"...Secondary Account Stop Password Sync Go to Next Link " + app.getName()
									+ "..linkNativeId.." + linkNativeId);
							continue;
						} else if (linkNativeId != null && nativeIdentity != null
								&& linkNativeId.equalsIgnoreCase(nativeIdentity)) {
							LogEnablement.isLogDebugEnabled(fwLogger,
									"...Intercepted Native Id Link Does Match with Link Native Id " + app.getName()
									+ "..linkNativeId.." + linkNativeId + ".nativeId.." + nativeIdentity);
							LogEnablement.isLogDebugEnabled(fwLogger, "...Build Interceptor Plan..");
							addTargetApplicatonAccountRequestToPlan(context, identity, plan, targetList,
									applicationName, password, null, null);
						}
					}
				}
			}
			context.decache(app);
		}
		return plan;
	}
	/**
	 * Synchronize Password from source to target applications for change, forgot,
	 * expire flow event Also, ensure only primary source account is evaluated, if
	 * there are multiple source accounts
	 *
	 * @param plan
	 * @param identityName
	 * @throws GeneralException
	 */
	public static ProvisioningPlan syncTargetApplicationsPasswordPlan(SailPointContext context, String flow,
			ProvisioningPlan plan, Workflow workflow, String identityName, ProvisioningProject project)
					throws GeneralException {
		LogEnablement.isLogDebugEnabled(fwLogger, "Enter syncTargetApplicationsPasswordPlan");
		LogEnablement.isLogDebugEnabled(fwLogger, "flow " + flow);
		if (flow != null && flow.contains(WrapperRuleLibrary.PASSWORDSREQUEST)) {
			return plan;
		}
		Identity identity = context.getObjectByName(Identity.class, identityName);
		String serviceCube = (String) identity.getAttribute(WrapperRuleLibrary.SERVICE_CUBE_ATTR);
		String sourceValue = null;
		List<String> targetList = null;
		if (serviceCube == null || serviceCube.equalsIgnoreCase("false")) {
			ProvisioningPlan planCopy = (ProvisioningPlan) plan.deepCopy(context);
			LogEnablement.isLogDebugEnabled(fwLogger, "Enter  syncPasswordPlan");
			List<AccountRequest> acctReqs = planCopy.getAccountRequests();
			if (null != acctReqs || !acctReqs.isEmpty()) {
				for (AccountRequest acctReq : acctReqs) {
					String appName = acctReq.getApplicationName();
					String nativeId = acctReq.getNativeIdentity();
					Attributes acctReqAttrs = acctReq.getArguments();
					if (appName != null && nativeId != null) {
						LogEnablement.isLogDebugEnabled(fwLogger, "Enter appName " + appName);
						LogEnablement.isLogDebugEnabled(fwLogger, "nativeId " + nativeId);
						Application app = context.getObjectByName(Application.class, appName);
						if (app != null) {
							LogEnablement.isLogDebugEnabled(fwLogger, "requestedSourceAppName " + appName);
							String targetApps = (String) app.getAttributeValue(WrapperRuleLibrary.TARGETPASSWORDSYNC);
							String sourceattrName = ProvisioningPlan.ATT_PASSWORD;
							// Let's get password attribute from LDAP Connector attribute "passwordAttr"
							if (app != null && app.getAttributeValue("passwordAttr") != null) {
								sourceattrName = (String) app.getAttributeValue("passwordAttr");
							}
							LogEnablement.isLogDebugEnabled(fwLogger, " targetApps " + targetApps);
							LogEnablement.isLogDebugEnabled(fwLogger, " sourceattrName " + sourceattrName);
							Boolean primarySyncaccounts = (Boolean) app
									.getAttributeValue(WrapperRuleLibrary.PRIMARYSYNCACCOUNTS);
							IdentityService idService = new IdentityService(context);
							List<AttributeRequest> attrRequests = acctReq.getAttributeRequests();
							if (targetApps != null) {
								// Find all Links for requested source
								// application
								/**
								 * This is to ensure we are synchronizing on primary account only Joiner will
								 * have zero links change password, expire, forgot must have links Rehire may or
								 * may not have links
								 */
								List<Link> listLinks = idService.getLinks(identity, app);
								String psaRegularAttrExpression = (String) app
										.getAttributeValue(WrapperRuleLibrary.PSAREGULARACCOUNTEXPRESSION);
								LogEnablement.isLogDebugEnabled(fwLogger, "...listLinks = " + listLinks);
								LogEnablement.isLogDebugEnabled(fwLogger,
										"...psaRegularAttrExpression = " + psaRegularAttrExpression);
								LogEnablement.isLogDebugEnabled(fwLogger,
										"...primarySyncaccounts = " + primarySyncaccounts);
								targetList = Util.csvToList(targetApps);
								LogEnablement.isLogDebugEnabled(fwLogger, "targetList " + targetList);
								if (listLinks != null && listLinks.size() > 1) {
									LogEnablement.isLogDebugEnabled(fwLogger,
											"...Multiple Links = " + listLinks.size());
									for (Link link : listLinks) {
										String linkNativeId = link.getNativeIdentity();
										LogEnablement.isLogDebugEnabled(fwLogger, "...linkNativeId = " + linkNativeId);
										// If Primary Synch is checked, ensure
										// this is not a password for secondary
										// account
										// If yes, go to next link
										if (linkNativeId != null && nativeId != null
												&& linkNativeId.equalsIgnoreCase(nativeId)
												&& primarySyncaccounts != null && primarySyncaccounts.booleanValue()
												&& psaRegularAttrExpression != null
												&& isSecondaryAccount(context, link)) {
											LogEnablement.isLogDebugEnabled(fwLogger,
													"...Secondary Account Stop Password Sync Go to Next Link "
															+ app.getName() + "..linkNativeId.." + linkNativeId);
											continue;
										} else if (linkNativeId != null && nativeId != null
												&& !linkNativeId.equalsIgnoreCase(nativeId)) {
											LogEnablement.isLogDebugEnabled(fwLogger,
													"...Requested Native Id Link Doesn't Match with Link Native Id "
															+ app.getName() + "..linkNativeId.." + linkNativeId
															+ ".nativeId.." + nativeId);
											continue;
										} else if (linkNativeId != null && nativeId != null
												&& linkNativeId.equalsIgnoreCase(nativeId)) {
											if (attrRequests != null && attrRequests.size() > 0) {
												for (AttributeRequest attrReq : attrRequests) {
													String name = attrReq.getName();
													if (name != null && sourceattrName != null
															&& name.equalsIgnoreCase(sourceattrName)) {
														sourceValue = (String) attrReq.getValue();
														Attributes attrRequestsAttrs = attrReq.getArguments();
														if (sourceValue != null && sourceValue.length() > 0
																&& project != null) {
															syncTargetPassword(context, workflow, project, targetList,
																	appName, sourceValue);
															break;
														} else if (sourceValue != null && sourceValue.length() > 0) {
															addTargetApplicatonAccountRequestToPlan(context, identity,
																	plan, targetList, appName, sourceValue,
																	acctReqAttrs, attrRequestsAttrs);
															break;
														}
													}
												}
											}
										}
									}
								}
								// We don't have any source link or there is
								// only one target link
								else if (listLinks == null || listLinks.size() == 0 || listLinks.size() == 1) {
									if (listLinks != null) {
										LogEnablement.isLogDebugEnabled(fwLogger, "...One Link = " + listLinks.size());
									} else {
										LogEnablement.isLogDebugEnabled(fwLogger,
												"...Empty Links = " + listLinks.size());
									}
									if (attrRequests != null && attrRequests.size() > 0) {
										for (AttributeRequest attrReq : attrRequests) {
											String name = attrReq.getName();
											if (name != null && sourceattrName != null
													&& name.equalsIgnoreCase(sourceattrName)) {
												sourceValue = (String) attrReq.getValue();
												Attributes attrRequestsAttrs = attrReq.getArguments();
												if (sourceValue != null && sourceValue.length() > 0
														&& project != null) {
													syncTargetPassword(context, workflow, project, targetList, appName,
															sourceValue);
													break;
												} else if (sourceValue != null && sourceValue.length() > 0) {
													addTargetApplicatonAccountRequestToPlan(context, identity, plan,
															targetList, appName, sourceValue, acctReqAttrs,
															attrRequestsAttrs);
													break;
												}
											}
										}
									}
								}
							}
							if (app != null) {
								context.decache(app);
							}
						} else {
							LogEnablement.isLogDebugEnabled(fwLogger,
									"Application Not found, most likely IIQ Application " + appName);
						}
					}
				}
			}
		}
		if (identity != null) {
			context.decache(identity);
		}
		if (sourceValue != null && targetList != null && targetList.size() > 0) {
			LogEnablement.isLogDebugEnabled(fwLogger, "Putting New Plan in Workflow");
			workflow.put("plan", plan);
			if (project != null) {
				LogEnablement.isLogDebugEnabled(fwLogger, "Putting New Project in Workflow");
				workflow.put("project", project);
			}
		}
		LogEnablement.isLogDebugEnabled(fwLogger, "Exit syncTargetApplicationsPasswordPlan");
		return plan;
	}
	/**
	 * Synchronize Password Value From Source To Target Add Target Account Request,
	 * ONLY If account exists Change, Expire, Forgot Password Events Joiner, Rehire
	 * Events will not be Synchronized here because link doesn't exist on identity
	 * at this point
	 * @param identity
	 * @param plan
	 * @param targetList
	 * @param sourceValue
	 * @return
	 * @throws GeneralException
	 */
	public static boolean addTargetApplicatonAccountRequestToPlan(SailPointContext context, Identity identity,
			ProvisioningPlan plan, List<String> targetList, String requestedSourceAppName, String sourceValue,
			Attributes accountRequestAttrs, Attributes attributeRequestAttrs) throws GeneralException {
		LogEnablement.isLogDebugEnabled(fwLogger, "Enter addTargetApplicatonAccountRequestToPlan");
		LogEnablement.isLogDebugEnabled(fwLogger,
				"Enter addTargetApplicatonAccountRequestToPlan requestedSourceAppName.." + requestedSourceAppName);
		LogEnablement.isLogDebugEnabled(fwLogger,
				"Enter addTargetApplicatonAccountRequestToPlan targetList.." + targetList);
		boolean result = false;
		if (targetList != null && targetList.size() > 0 && sourceValue != null && sourceValue.length() > 0) {
			for (String targetAppName : targetList) {
				if (plan != null) {
					List<AccountRequest> acctReqs = plan.getAccountRequests(targetAppName, null);
					// If account request is there, there is no need to add
					// target application, this means it is already there
					// In case password is different, it will be synchronized
					// from source to target in workflow provision retries
					LogEnablement.isLogDebugEnabled(fwLogger, " acctReqs " + acctReqs);
					if (acctReqs == null || acctReqs.size() <= 0 && targetAppName != null) {
						// Modify Plan here, Make sure Link exists
						IdentityService idService = new IdentityService(context);
						Application app = context.getObjectByName(Application.class, targetAppName);
						if (app != null) {
							List<Link> listLinks = idService.getLinks(identity, app);
							if (listLinks != null && listLinks.size() > 0) {
								// Get All Target Links, there could be a
								// possibility where target links could have
								// multiple accounts
								// TO DO:We may want to take primary source
								// password and put it on target accounts.
								for (Link link : listLinks) {
									LogEnablement.isLogDebugEnabled(fwLogger,
											" link.getApplicationName() " + link.getApplicationName());
									if (link != null && link.getApplicationName() != null
											&& link.getApplicationName().equalsIgnoreCase(targetAppName)) {
										AccountRequest req = new AccountRequest();
										req.setApplication(targetAppName);
										req.setNativeIdentity(link.getNativeIdentity());
										req.setOperation(AccountRequest.Operation.Modify);
										if (accountRequestAttrs != null) {
											req.setArguments(accountRequestAttrs);
										}
										AttributeRequest areq = new AttributeRequest();
										areq.setName(ProvisioningPlan.ATT_PASSWORD);
										areq.setOperation(Operation.Set);
										areq.setValue(sourceValue);
										if (attributeRequestAttrs != null) {
											attributeRequestAttrs.put("secret", "true");
											areq.setArguments(attributeRequestAttrs);
										} else {
											attributeRequestAttrs = new Attributes();
											attributeRequestAttrs.put("secret", "true");
										}
										req.add(areq);
										plan.add(req);
										LogEnablement.isLogDebugEnabled(fwLogger,
												" Plan Modified Target Sources Added");
										result = true;
									}
								}
							}
							context.decache(app);
						}
					}
				}
			}
		}
		LogEnablement.isLogDebugEnabled(fwLogger, "Exit addTargetApplicatonAccountRequestToPlan " + result);
		return result;
	}
	/**
	 * Fire Fighter Check Required
	 *
	 * @param context
	 * @param plan
	 * @param workflow
	 * @param flow
	 * @param source
	 * @param identityName
	 * @param launcher
	 * @param requestType
	 * @throws GeneralException
	 */
	public static String isFireFighterStepRequired(SailPointContext context, ProvisioningPlan plan, Workflow workflow,
			String flow, String source, String identityName, String launcher, String requestType)
					throws GeneralException {
		LogEnablement.isLogDebugEnabled(fwLogger, "Start isFireFighterStepRequired ");
		LogEnablement.isLogDebugEnabled(fwLogger, "flow...." + flow);
		LogEnablement.isLogDebugEnabled(fwLogger, "source...." + source);
		LogEnablement.isLogDebugEnabled(fwLogger, "requestType...." + requestType);
		LogEnablement.isLogDebugEnabled(fwLogger, "identityName...." + identityName);
		LogEnablement.isLogDebugEnabled(fwLogger, "launcher...." + launcher);
		String fireFighterAccess = "false";
		if (launcher != null && identityName != null) {
			Identity launcherIdentity = context.getObjectByName(Identity.class, launcher);
			Identity requesteeIdentity = context.getObjectByName(Identity.class, identityName);
			if (launcherIdentity != null && launcherIdentity.getCapabilityManager() != null && requesteeIdentity != null
					&& requesteeIdentity.getCapabilityManager() != null) {
				if (launcherIdentity.getCapabilityManager().hasCapability(WrapperRuleLibrary.FIREFIGHTERADMIN)
						&& requesteeIdentity.getCapabilityManager().hasCapability(WrapperRuleLibrary.FIREFIGHTER)) {
					Configuration sysConfig = context.getConfiguration();
					Attributes sysConfigAttributes = null;
					if (sysConfig != null) {
						sysConfigAttributes = sysConfig.getAttributes();
					}
					if (sysConfigAttributes != null
							&& sysConfigAttributes.containsKey(Configuration.ENABLE_ROLE_SUN_ASSIGNMENT)
							&& (Boolean) sysConfigAttributes.get(Configuration.ENABLE_ROLE_SUN_ASSIGNMENT)) {
						fireFighterAccess = "true";
					}
				}
			}
			if (launcherIdentity != null) {
				context.decache(launcherIdentity);
			}
		}
		LogEnablement.isLogDebugEnabled(fwLogger, "End isFireFighterStepRequired " + fireFighterAccess);
		return fireFighterAccess;
	}
	/**
	 * FireFighter Access Enablement
	 *
	 * @param context
	 * @param worklfow
	 * @param flow
	 * @param source
	 * @param requestType
	 * @return
	 * @throws GeneralException
	 */
	public static String fireFighterAccessEnabled(SailPointContext context, ProvisioningPlan plan, Workflow workflow,
			String flow, String source, String identityName, String launcher, String requestType)
					throws GeneralException {
		LogEnablement.isLogDebugEnabled(fwLogger, "Start fireFighterAccessEnabled ");
		LogEnablement.isLogDebugEnabled(fwLogger, "flow...." + flow);
		LogEnablement.isLogDebugEnabled(fwLogger, "source...." + source);
		LogEnablement.isLogDebugEnabled(fwLogger, "requestType...." + requestType);
		LogEnablement.isLogDebugEnabled(fwLogger, "identityName...." + identityName);
		LogEnablement.isLogDebugEnabled(fwLogger, "launcher...." + launcher);
		String required = "false";
		if (flow != null || source != null) {
			if (plan != null && (flow != null && flow.equalsIgnoreCase(WrapperRuleLibrary.FLOWACCESSREQUEST)
					|| (source != null && source.equalsIgnoreCase(WrapperRuleLibrary.SOURCEBATCH)))) {
				required = isFireFighterStepRequired(context, plan, workflow, flow, source, identityName, launcher,
						requestType);
				if (required != null && required.equalsIgnoreCase("true")) {
					int check = validateFireFighterPlan(context, plan, identityName, launcher);
					LogEnablement.isLogDebugEnabled(fwLogger, "check...." + check);
					if (check == 1) {
						if (flow.equalsIgnoreCase(WrapperRuleLibrary.FLOWACCESSREQUEST)) {
							workflow.put(WrapperRuleLibrary.UIINTVALFIRFIGHTERROR, "Requestor cannot be requestee");
						} else if (source.equalsIgnoreCase(WrapperRuleLibrary.SOURCEBATCH)) {
							workflow.put(WrapperRuleLibrary.UIINTVALFIRFIGHTERROR, "Requestor cannot be requestee");
							workflow.put(WrapperRuleLibrary.BATCHVALIDATIONERROR, "Requestor cannot be requestee");
						}
					} else if (check == 2) {
						if (flow.equalsIgnoreCase(WrapperRuleLibrary.FLOWACCESSREQUEST)) {
							workflow.put(WrapperRuleLibrary.UIINTVALFIRFIGHTERROR, "Please select sunset date");
						} else if (source.equalsIgnoreCase(WrapperRuleLibrary.SOURCEBATCH)) {
							workflow.put(WrapperRuleLibrary.UIINTVALFIRFIGHTERROR, "Please select sunset date");
							workflow.put(WrapperRuleLibrary.BATCHVALIDATIONERROR, "Please select sunset date");
						}
					} else if (check == 0) {
						LogEnablement.isLogDebugEnabled(fwLogger, "Validation Passed...");
					}
				}
			}
		}
		LogEnablement.isLogDebugEnabled(fwLogger, "End fireFighterAccessEnabled " + required);
		return required;
	}
	/**
	 * Find if plan is Fire Fighter Access Enabled
	 *
	 * @param project
	 * @throws GeneralException
	 */
	public static int validateFireFighterPlan(SailPointContext context, ProvisioningPlan plan, String launcher,
			String identityName) throws GeneralException {
		LogEnablement.isLogDebugEnabled(fwLogger, "Enter validateFireFighterPlan");
		LogEnablement.isLogDebugEnabled(fwLogger, "plan " + plan);
		int totalAddorSetAttrRequest = 0;
		int totalEndDateAttrRequest = 0;
		if (launcher != null && identityName != null && launcher.equalsIgnoreCase(identityName)) {
			LogEnablement.isLogDebugEnabled(fwLogger, "End validateFireFighterPlan..Laucher is same as Requestee");
			return 1;
		} else if (plan != null) {
			List<AccountRequest> acctReqs = plan.getAccountRequests();
			if (acctReqs != null && acctReqs.size() > 0) {
				for (AccountRequest acctReq : acctReqs) {
					// This is a Role Request
					List<AttributeRequest> attrReqList = acctReq.getAttributeRequests();
					boolean roleRequest = false;
					if (attrReqList != null) {
						for (AttributeRequest attrReq : attrReqList) {
							Attributes attRattrReq = attrReq.getArguments();
							if (attRattrReq != null) {
								if (attrReq.getOp().equals(ProvisioningPlan.Operation.Add)
										|| attrReq.getOp().equals(ProvisioningPlan.Operation.Set)) {
									totalAddorSetAttrRequest = totalAddorSetAttrRequest + 1;
									if (attRattrReq.containsKey(ProvisioningPlan.ARG_REMOVE_DATE)
											&& attRattrReq.get(ProvisioningPlan.ARG_REMOVE_DATE) != null) {
										totalEndDateAttrRequest = totalEndDateAttrRequest + 1;
									}
								}
							}
						} // Iterate Through Attribute Request
					} // Attribute Request List
				} // Iterate Account Requests
			} // Accounts Request Not Empty
		} // Plan Not Empty
		if (totalAddorSetAttrRequest != totalEndDateAttrRequest) {
			LogEnablement.isLogDebugEnabled(fwLogger, "totalAddorSetAttrRequest.." + totalAddorSetAttrRequest);
			LogEnablement.isLogDebugEnabled(fwLogger, "totalEndDateAttrRequest.." + totalEndDateAttrRequest);
			LogEnablement.isLogDebugEnabled(fwLogger, "End validateFireFighterPlan..");
			return 2;
		}
		return 0;
	}
	/**
	 * Redirect Workflows
	 *
	 * @param context
	 * @param worklfow
	 * @param flow
	 * @param source
	 * @param requestType
	 * @return
	 * @throws GeneralException
	 */
	public static void redirectAcceleratorPackEnabled(SailPointContext context, ProvisioningPlan plan,
			Workflow workflow, String flow, String source, String requestType) throws GeneralException {
		LogEnablement.isLogDebugEnabled(fwLogger, "Start redirectAcceleratorPackEnabled ");
		String subflowName = "Normal Processing";
		workflow.put(WrapperRuleLibrary.SUBFLOWNAME, subflowName);
		LogEnablement.isLogDebugEnabled(fwLogger, "flow...." + flow);
		LogEnablement.isLogDebugEnabled(fwLogger, "source...." + source);
		LogEnablement.isLogDebugEnabled(fwLogger, "requestType...." + requestType);
		if (requestType != null) {
			// Break Glass Operations has flow AccessRequest requestType->RECOVERY TOOL
			// FEATURE
			// After Provisioning has flow AccessRequest requestType->REQUEST MANAGER
			// FEATURE
			// SCIM/REST requestType->EXTERNAL FEATURE
			subflowName = "Normal Processing";
			LogEnablement.isLogDebugEnabled(fwLogger, "subflowName...." + subflowName);
		} else if (flow != null || source != null) {
			Map map = ROADUtil.getCustomGlobalMap(context);
			String existingAccessRequest = WrapperRuleLibrary.EXISTINGACCESSREQUEST;
			String existingAccountRequest = WrapperRuleLibrary.EXISTINGACCOUNTREQUEST;
			String existingBatchAccessRequest = WrapperRuleLibrary.EXISTINGBACCESSREQUEST;
			String existingBatchAccountRequest = WrapperRuleLibrary.EXISTINGBACCOUNTREQUEST;
			if (map != null) {
				if (flow != null && map.containsKey(existingAccessRequest) && map.get(existingAccessRequest) != null
						&& ((String) map.get(existingAccessRequest)).length() > 0
						&& flow.equalsIgnoreCase(WrapperRuleLibrary.FLOWACCESSREQUEST)) {
					subflowName = (String) map.get(existingAccessRequest);
					LogEnablement.isLogDebugEnabled(fwLogger, "Access Request subflowName...." + subflowName);
				} else if (flow != null && map.containsKey(existingAccountRequest)
						&& map.get(existingAccountRequest) != null
						&& ((String) map.get(existingAccountRequest)).length() > 0
						&& flow.equalsIgnoreCase(WrapperRuleLibrary.FLOWACCOUNTREQUEST)) {
					subflowName = (String) map.get(existingAccountRequest);
					LogEnablement.isLogDebugEnabled(fwLogger, "Account Request subflowName...." + subflowName);
				} else if (source != null && map.containsKey(existingBatchAccessRequest)
						&& map.get(existingBatchAccessRequest) != null
						&& ((String) map.get(existingBatchAccessRequest)).length() > 0
						&& source.equalsIgnoreCase(WrapperRuleLibrary.SOURCEBATCH)) {
					subflowName = (String) map.get(existingBatchAccessRequest);
					LogEnablement.isLogDebugEnabled(fwLogger, "Batch Access Request subflowName...." + subflowName);
				} else if (source != null && map.containsKey(existingBatchAccountRequest)
						&& map.get(existingBatchAccountRequest) != null
						&& ((String) map.get(existingBatchAccountRequest)).length() > 0
						&& source.equalsIgnoreCase(WrapperRuleLibrary.SOURCEBATCH)) {
					subflowName = (String) map.get(existingBatchAccessRequest);
					LogEnablement.isLogDebugEnabled(fwLogger, "Batch Account Request subflowName...." + subflowName);
				}
				if (subflowName != null && plan != null && !subflowName.equalsIgnoreCase("Normal Processing")) {
					int check = isPlanAcceleratorPackEnabled(context, plan);
					LogEnablement.isLogDebugEnabled(fwLogger, "check...." + check);
					if (check == 0) {
						// Application and Role Combination is not Allowed
						LogEnablement.isLogDebugEnabled(fwLogger,
								"Validation Failure Left Right Combination...." + subflowName);
						subflowName = "Normal Processing";
						LogEnablement.isLogDebugEnabled(fwLogger, "subflowName...." + subflowName);
						if (flow.equalsIgnoreCase(WrapperRuleLibrary.FLOWACCOUNTREQUEST)
								|| flow.equalsIgnoreCase(WrapperRuleLibrary.FLOWACCESSREQUEST)) {
							workflow.put(WrapperRuleLibrary.UIINTVALERROR,
									"Combination of Applications and Roles Request is Not Allowed "
											+ WrapperRuleLibrary.validationStringLeftRight);
						} else if (source.equalsIgnoreCase(WrapperRuleLibrary.SOURCEBATCH)) {
							workflow.put(WrapperRuleLibrary.BATCHVALIDATIONERROR,
									"Combination of Applications and Roles Request is Not Allowed "
											+ WrapperRuleLibrary.validationStringLeftRight);
						}
					} else if (check == 1) {
						// Accelerator Pack Workflow
						LogEnablement.isLogDebugEnabled(fwLogger, "CALL Accelerator Pack Wrapper Workflow....");
					} else if (check == 2) {
						// Existing Workflow
						LogEnablement.isLogDebugEnabled(fwLogger, "CALL Existing Workflow...." + subflowName);
						workflow.put(WrapperRuleLibrary.SUBFLOWNAME, subflowName);
						sailpoint.object.Workflow subflow = context.getObjectByName(Workflow.class, subflowName);
						sailpoint.object.Workflow.Step step = workflow.getStep("Launch Existing Workflow");
						LogEnablement.isLogDebugEnabled(fwLogger, "subflow...." + subflow);
						step.setSubProcess(subflow);
					}
				} else {
					LogEnablement.isLogDebugEnabled(fwLogger, "No Existing Workflows...." + subflowName);
				}
			}
		}
		LogEnablement.isLogDebugEnabled(fwLogger, "End redirectAcceleratorPackEnabled ");
	}
	/**
	 * Find if plan is Accelerator Pack Enabled
	 *
	 * @param project
	 * @throws GeneralException
	 */
	public static int isPlanAcceleratorPackEnabled(SailPointContext context, ProvisioningPlan plan)
			throws GeneralException {
		LogEnablement.isLogDebugEnabled(fwLogger, "Enter isPlanAcceleratorPackEnabled");
		LogEnablement.isLogDebugEnabled(fwLogger, "plan " + plan);
		LinkedHashSet<String> applicationNames = new LinkedHashSet();
		LinkedHashSet<String> roleNames = new LinkedHashSet();
		if (plan != null) {
			List<AccountRequest> acctReqs = plan.getAccountRequests();
			if (acctReqs != null && acctReqs.size() > 0) {
				for (AccountRequest acctReq : acctReqs) {
					String appName = null;
					appName = acctReq.getApplicationName();
					if (appName != null && !appName.equalsIgnoreCase(ProvisioningPlan.APP_IIQ)
							&& !appName.equalsIgnoreCase(ProvisioningPlan.IIQ_APPLICATION_NAME)
							&& !appName.equalsIgnoreCase(ProvisioningPlan.APP_IDM)) {
						applicationNames.add(appName);
					} else {
						// This is a Role Request
						List<AttributeRequest> attrReqList = acctReq.getAttributeRequests();
						boolean roleRequest = false;
						if (attrReqList != null) {
							for (AttributeRequest attrReq : attrReqList) {
								String attrName = attrReq.getName();
								Object value = attrReq.getValue();
								if (value != null && value instanceof List) {
									List<String> valueList = (List) value;
									for (String valueStrg : valueList) {
										if (valueStrg != null && valueStrg.length() > 0) {
											roleNames.add(valueStrg);
										}
									}
								} else {
									String valueStr = "";
									valueStr = value.toString();
									if (valueStr != null && valueStr.length() > 0) {
										roleNames.add(valueStr);
									}
								}
							} // Iterate Through Attribute Request
						} // Attribute Request List
					} // Roles Request
				} // Iterate Account Requests
			} // Accounts Request Not Empty
		} // Plan Not Empty
		LogEnablement.isLogDebugEnabled(fwLogger,
				"Check isPlanAcceleratorPackEnabled Plan Application Names" + applicationNames);
		LogEnablement.isLogDebugEnabled(fwLogger, "Check isPlanAcceleratorPackEnabled Plan Role Names" + roleNames);
		LinkedHashSet totalRoleEnabled = new LinkedHashSet();
		LinkedHashSet totalAppEnabled = new LinkedHashSet();
		LinkedHashSet totalRoleNotEnabled = new LinkedHashSet();
		LinkedHashSet totalAppNotEnabled = new LinkedHashSet();
		for (String applicationName : applicationNames) {
			Application appObj = context.getObjectByName(Application.class, applicationName);
			if (appObj != null) {
				String appAcceleratorPackEnabled = appObj
						.getStringAttributeValue(WrapperRuleLibrary.ACCELERATORPACKENABLED);
				if (appAcceleratorPackEnabled != null && appAcceleratorPackEnabled.equalsIgnoreCase("TRUE")) {
					totalAppEnabled.add(applicationName);
				} else {
					totalAppNotEnabled.add(applicationName);
				}
				context.decache(appObj);
			}
		}
		for (String roleName : roleNames) {
			Bundle roleObj = context.getObjectByName(Bundle.class, roleName);
			if (roleObj != null) {
				String appAcceleratorPackEnabled = (String) roleObj
						.getAttribute(WrapperRuleLibrary.ACCELERATORPACKENABLED);
				if (appAcceleratorPackEnabled != null && appAcceleratorPackEnabled.equalsIgnoreCase("TRUE")) {
					totalRoleEnabled.add(roleName);
				} else {
					totalRoleNotEnabled.add(roleName);
				}
				context.decache(roleObj);
			}
		}
		LogEnablement.isLogDebugEnabled(fwLogger, " totalRoleEnabled Size " + totalRoleEnabled.size());
		LogEnablement.isLogDebugEnabled(fwLogger, " totalRoleEnabled " + totalRoleEnabled);
		LogEnablement.isLogDebugEnabled(fwLogger, " totalRoleNotEnabled Size " + totalRoleNotEnabled.size());
		LogEnablement.isLogDebugEnabled(fwLogger, " totalRoleNotEnabled " + totalRoleNotEnabled);
		LogEnablement.isLogDebugEnabled(fwLogger, " roleNames Size " + roleNames.size());
		LogEnablement.isLogDebugEnabled(fwLogger, " roleNames " + roleNames);
		LogEnablement.isLogDebugEnabled(fwLogger, " totalAppEnabled Size " + totalAppEnabled.size());
		LogEnablement.isLogDebugEnabled(fwLogger, " totalAppEnabled " + totalAppEnabled);
		LogEnablement.isLogDebugEnabled(fwLogger, " totalAppNotEnabled Size " + totalAppNotEnabled.size());
		LogEnablement.isLogDebugEnabled(fwLogger, " totalAppNotEnabled " + totalAppNotEnabled);
		LogEnablement.isLogDebugEnabled(fwLogger, " applicationNames Size " + applicationNames.size());
		LogEnablement.isLogDebugEnabled(fwLogger, " applicationNames " + applicationNames);
		if (totalRoleEnabled.size() == roleNames.size() && totalAppEnabled.size() == applicationNames.size()) {
			LogEnablement.isLogDebugEnabled(fwLogger, " isPlanAcceleratorPackEnabled " + "Call Wrapper Workflow");
			return 1;
		} else if (totalRoleNotEnabled.size() == roleNames.size()
				&& totalAppNotEnabled.size() == applicationNames.size()) {
			LogEnablement.isLogDebugEnabled(fwLogger, " isPlanAcceleratorPackEnabled " + "Call Existing Workflow");
			return 2;
		} else {
			LogEnablement.isLogDebugEnabled(fwLogger, " isPlanAcceleratorPackEnabled ");
			validationStringLeftRight = "LEFT (" + totalRoleEnabled.toString() + "," + totalAppEnabled.toString() + ")"
					+ " - RIGHT (" + totalRoleNotEnabled.toString() + "," + totalAppNotEnabled.toString() + ")";
			return 0;
		}
	}
	/**
	 * Unique Application Names from Provisioning Project
	 *
	 * @param project
	 * @throws GeneralException
	 */
	public static HashSet getUniqueAppNamesFromProvisioningProject(SailPointContext context,
			ProvisioningProject project) throws GeneralException {
		LogEnablement.isLogDebugEnabled(fwLogger, "Enter getUniqueAppNamesFromProvisioningProject");
		LogEnablement.isLogDebugEnabled(fwLogger, "project " + project);
		HashSet applicationNames = new HashSet();
		List<String> integrationIds = new ArrayList();
		if (project != null) {
			if (project != null) {
				String identityName = project.getIdentity();
				List<ProvisioningPlan> plans = project.getPlans();
				if (null != plans && plans.size() > 0 && identityName != null) {
					for (ProvisioningPlan plan : plans) {
						List<AccountRequest> acctReqs = plan.getAccountRequests();
						if (acctReqs != null && acctReqs.size() > 0) {
							for (AccountRequest acctReq : acctReqs) {
								String appName = null;
								appName = acctReq.getApplicationName();
								if (appName != null && !appName.equalsIgnoreCase(ProvisioningPlan.APP_IIQ)
										&& !appName.equalsIgnoreCase(ProvisioningPlan.IIQ_APPLICATION_NAME)
										&& !appName.equalsIgnoreCase(ProvisioningPlan.APP_IDM)) {
									applicationNames.add(appName);
								} else {
									// This is a Role Request
									// It looks like ticket integration
									// applications doesn't expand properly on
									// role request
									List<AttributeRequest> attrReqList = acctReq.getAttributeRequests();
									boolean roleRequest = false;
									if (attrReqList != null) {
										for (AttributeRequest attrReq : attrReqList) {
											String attrName = attrReq.getName();
											Object value = attrReq.getValue();
											/**
											 * All the values should be String, compiler can covert them into List May
											 * need to check for list too
											 */
											if (value != null && value instanceof List) {
												List<String> valueList = (List) value;
												for (String valueStrg : valueList) {
													if (valueStrg != null && valueStrg.length() > 0) {
														Bundle role = context.getObjectByName(Bundle.class, valueStrg);
														Boolean bundlePsaValidation = false;
														roleRequest = true;
														if (role != null) {
															Set<Application> applications = role.getApplications();
															LogEnablement.isLogDebugEnabled(fwLogger,
																	"applications=" + applications);
															// Applications will
															// be empty for
															// business roles,
															// however,
															// Permitted roles
															// will have
															// applications
															if (applications != null && !applications.isEmpty()) {
																for (Application appSet : applications) {
																	applicationNames.add(appSet.getName());
																}
															}
															// Lets explore IT
															// Roles
															else {
																List<Bundle> requiredBundles = role.getRequirements();
																LogEnablement.isLogDebugEnabled(fwLogger,
																		"...Role RequiredBundles  = "
																				+ requiredBundles);
																if (requiredBundles != null) {
																	LogEnablement.isLogDebugEnabled(fwLogger,
																			"...Role RequiredBundles  Size = "
																					+ requiredBundles.size());
																	for (Bundle requiredBundle : requiredBundles) {
																		Set<Application> setReqAppNames = requiredBundle
																				.getApplications();
																		LogEnablement.isLogDebugEnabled(fwLogger,
																				"...Role Required Applications  = "
																						+ setReqAppNames);
																		if (setReqAppNames != null
																				&& !setReqAppNames.isEmpty()) {
																			for (Application appSet : setReqAppNames) {
																				applicationNames.add(appSet.getName());
																			}
																		}
																	}
																}
															} // IT Roles
														} // Role Not Null
													} // Value
												}
											} else {
												String valueStr = "";
												valueStr = value.toString();
												if (valueStr != null && valueStr.length() > 0) {
													Bundle role = context.getObjectByName(Bundle.class, valueStr);
													Boolean bundlePsaValidation = false;
													roleRequest = true;
													if (role != null) {
														Set<Application> applications = role.getApplications();
														LogEnablement.isLogDebugEnabled(fwLogger,
																"applications=" + applications);
														// Applications will be
														// empty for business
														// roles, however,
														// Permitted roles will
														// have applications
														if (applications != null && !applications.isEmpty()) {
															for (Application appSet : applications) {
																applicationNames.add(appSet.getName());
															}
														}
														// Lets explore IT Roles
														else {
															List<Bundle> requiredBundles = role.getRequirements();
															LogEnablement.isLogDebugEnabled(fwLogger,
																	"...Role RequiredBundles  = " + requiredBundles);
															if (requiredBundles != null) {
																LogEnablement.isLogDebugEnabled(fwLogger,
																		"...Role RequiredBundles  Size = "
																				+ requiredBundles.size());
																for (Bundle requiredBundle : requiredBundles) {
																	Set<Application> setReqAppNames = requiredBundle
																			.getApplications();
																	LogEnablement.isLogDebugEnabled(fwLogger,
																			"...Role Required Applications  = "
																					+ setReqAppNames);
																	if (setReqAppNames != null
																			&& !setReqAppNames.isEmpty()) {
																		for (Application appSet : setReqAppNames) {
																			applicationNames.add(appSet.getName());
																		}
																	}
																}
															}
														} // IT Roles
													} // Role Not Null
												} // Value Not Null
											}
										} // Iterate Through Attribute Request
									} // Attribute Request List
								} // Roles Request
							} // Iterate Account Requests
						} // Accounts Request Not Empty
					} // Iterate Provisioning Plans
				} // Plans are not empty
			} // Project not null
		} // Project Not Empty
		LogEnablement.isLogDebugEnabled(fwLogger, "End getUniqueAppNamesFromProvisioningProject " + applicationNames);
		return applicationNames;
	}
	/**
	 * Launch Extended Rule For Ticket Integration Project will have multiple plans
	 * for each application
	 *
	 * @throws Exception
	 */
	public static void ticketIntegrationAfterProvisioningRule(SailPointContext context, ProvisioningProject project,
			String requestType) throws Exception {
		LogEnablement.isLogDebugEnabled(fwLogger, "Enter ticketIntegrationAfterProvisioningRule");
		LogEnablement.isLogDebugEnabled(fwLogger, "project " + project);
		LogEnablement.isLogDebugEnabled(fwLogger, "requestType " + requestType);
		HashSet applicationNames = new HashSet();
		List<String> integrationIds = new ArrayList();
		if (project != null) {
			QueryOptions qo = new QueryOptions();
			qo.addFilter(Filter.notnull("id"));
			java.util.Iterator<Object[]> it = context.search(IntegrationConfig.class, qo, "id");
			if (it != null) {
				while (it.hasNext()) {
					Object[] objArr = it.next();
					if (objArr != null && objArr.length == 1 && objArr[0] != null) {
						String integrationId = objArr[0].toString();
						integrationIds.add(integrationId);
					}
				}
			}
			Util.flushIterator(it);
			if (project != null) {
				String identityName = project.getIdentity();
				List<ProvisioningPlan> plans = project.getPlans();
				if (null != plans && plans.size() > 0 && identityName != null) {
					for (ProvisioningPlan plan : plans) {
						List<AccountRequest> acctReqs = plan.getAccountRequests();
						if (acctReqs != null && acctReqs.size() > 0) {
							for (AccountRequest acctReq : acctReqs) {
								String appName = null;
								appName = acctReq.getApplicationName();
								if (appName != null && !appName.equalsIgnoreCase(ProvisioningPlan.APP_IIQ)
										&& !appName.equalsIgnoreCase(ProvisioningPlan.IIQ_APPLICATION_NAME)
										&& !appName.equalsIgnoreCase(ProvisioningPlan.APP_IDM)) {
									Application app = context.getObjectByName(Application.class, appName);
									if (app != null
											&& app.getAttributeValue(
													WrapperRuleLibrary.AFTER_TICKET_PROVISIONING_RULE_OPTIONS) != null
													&& ((String) app.getAttributeValue(
															WrapperRuleLibrary.AFTER_TICKET_PROVISIONING_RULE_OPTIONS))
													.equalsIgnoreCase(
															WrapperRuleLibrary.AFTER_TICKET_PROVISIONING_RULE_OPTIONS_VALUE)
													&& (String) app.getAttributeValue(
															WrapperRuleLibrary.AFTER_TICKET_PROVISIONING_RULE_ATTR) != null)
									{
										applicationNames.add(app.getName());
									}
									if (app != null) {
										context.decache(app);
									}
								} else {
									// This is a Role Request
									// It looks like ticket integration
									// applications doesn't expand properly on
									// role request
									List<AttributeRequest> attrReqList = acctReq.getAttributeRequests();
									boolean roleRequest = false;
									if (attrReqList != null) {
										for (AttributeRequest attrReq : attrReqList) {
											String attrName = attrReq.getName();
											Object value = attrReq.getValue();
											/**
											 * All the values should be String, compiler can covert them into List May
											 * need to check for list too
											 */
											if (value != null && value instanceof List) {
												List<String> valueList = (List) value;
												for (String valueStrg : valueList) {
													if (valueStrg != null && valueStrg.length() > 0) {
														Bundle role = context.getObjectByName(Bundle.class, valueStrg);
														Boolean bundlePsaValidation = false;
														roleRequest = true;
														if (role != null) {
															Set<Application> applications = role.getApplications();
															LogEnablement.isLogDebugEnabled(fwLogger,
																	"applications=" + applications);
															// Applications will
															// be empty for
															// business roles,
															// however,
															// Permitted roles
															// will have
															// applications
															if (applications != null && !applications.isEmpty()) {
																for (Application appSet : applications) {
																	if (appSet != null && appSet.getAttributeValue(
																			WrapperRuleLibrary.AFTER_TICKET_PROVISIONING_RULE_OPTIONS) != null
																			&& ((String) appSet.getAttributeValue(
																					WrapperRuleLibrary.AFTER_TICKET_PROVISIONING_RULE_OPTIONS))
																			.equalsIgnoreCase(
																					WrapperRuleLibrary.AFTER_TICKET_PROVISIONING_RULE_OPTIONS_VALUE)
																			&& (String) appSet.getAttributeValue(
																					WrapperRuleLibrary.AFTER_TICKET_PROVISIONING_RULE_ATTR) != null)
																	{
																		applicationNames.add(appSet.getName());
																	}
																}
															}
															// Lets explore IT
															// Roles
															else {
																List<Bundle> requiredBundles = role.getRequirements();
																LogEnablement.isLogDebugEnabled(fwLogger,
																		"...Role RequiredBundles  = "
																				+ requiredBundles);
																if (requiredBundles != null) {
																	LogEnablement.isLogDebugEnabled(fwLogger,
																			"...Role RequiredBundles  Size = "
																					+ requiredBundles.size());
																	for (Bundle requiredBundle : requiredBundles) {
																		Set<Application> setReqAppNames = requiredBundle
																				.getApplications();
																		LogEnablement.isLogDebugEnabled(fwLogger,
																				"...Role Required Applications  = "
																						+ setReqAppNames);
																		if (setReqAppNames != null
																				&& !setReqAppNames.isEmpty()) {
																			for (Application appSet : setReqAppNames) {
																				if (appSet != null
																						&& appSet.getAttributeValue(
																								WrapperRuleLibrary.AFTER_TICKET_PROVISIONING_RULE_OPTIONS) != null
																								&& ((String) appSet
																										.getAttributeValue(
																												WrapperRuleLibrary.AFTER_TICKET_PROVISIONING_RULE_OPTIONS))
																								.equalsIgnoreCase(
																										WrapperRuleLibrary.AFTER_TICKET_PROVISIONING_RULE_OPTIONS_VALUE)
																								&& (String) appSet
																								.getAttributeValue(
																										WrapperRuleLibrary.AFTER_TICKET_PROVISIONING_RULE_ATTR) != null) {
																					applicationNames
																					.add(appSet.getName());
																				}
																			}
																		}
																	}
																}
															} // IT Roles
														} // Role Not Null
													}
												}
											} else {
												String valueStr = "";
												if (value != null) {
													valueStr = value.toString();
												}
												if (valueStr != null && valueStr.length() > 0) {
													Bundle role = context.getObjectByName(Bundle.class, valueStr);
													Boolean bundlePsaValidation = false;
													roleRequest = true;
													if (role != null) {
														Set<Application> applications = role.getApplications();
														LogEnablement.isLogDebugEnabled(fwLogger,
																"applications=" + applications);
														// Applications will be
														// empty for business
														// roles, however,
														// Permitted roles will
														// have applications
														if (applications != null && !applications.isEmpty()) {
															for (Application appSet : applications) {
																if (appSet != null && appSet.getAttributeValue(
																		WrapperRuleLibrary.AFTER_TICKET_PROVISIONING_RULE_OPTIONS) != null
																		&& ((String) appSet.getAttributeValue(
																				WrapperRuleLibrary.AFTER_TICKET_PROVISIONING_RULE_OPTIONS))
																		.equalsIgnoreCase(
																				WrapperRuleLibrary.AFTER_TICKET_PROVISIONING_RULE_OPTIONS_VALUE)
																		&& (String) appSet.getAttributeValue(
																				WrapperRuleLibrary.AFTER_TICKET_PROVISIONING_RULE_ATTR) != null)
																{
																	applicationNames.add(appSet.getName());
																}
															}
														}
														// Lets explore IT Roles
														else {
															List<Bundle> requiredBundles = role.getRequirements();
															LogEnablement.isLogDebugEnabled(fwLogger,
																	"...Role RequiredBundles  = " + requiredBundles);
															if (requiredBundles != null) {
																LogEnablement.isLogDebugEnabled(fwLogger,
																		"...Role RequiredBundles  Size = "
																				+ requiredBundles.size());
																for (Bundle requiredBundle : requiredBundles) {
																	Set<Application> setReqAppNames = requiredBundle
																			.getApplications();
																	LogEnablement.isLogDebugEnabled(fwLogger,
																			"...Role Required Applications  = "
																					+ setReqAppNames);
																	if (setReqAppNames != null
																			&& !setReqAppNames.isEmpty()) {
																		for (Application appSet : setReqAppNames) {
																			if (appSet != null
																					&& appSet.getAttributeValue(
																							WrapperRuleLibrary.AFTER_TICKET_PROVISIONING_RULE_OPTIONS) != null
																							&& ((String) appSet
																									.getAttributeValue(
																											WrapperRuleLibrary.AFTER_TICKET_PROVISIONING_RULE_OPTIONS))
																							.equalsIgnoreCase(
																									WrapperRuleLibrary.AFTER_TICKET_PROVISIONING_RULE_OPTIONS_VALUE)
																							&& (String) appSet
																							.getAttributeValue(
																									WrapperRuleLibrary.AFTER_TICKET_PROVISIONING_RULE_ATTR) != null) {
																				applicationNames.add(appSet.getName());
																			}
																		}
																	}
																}
															}
														} // IT Roles
													} // Role Not Null
												} // Value Not Null
											}
										} // Iterate Through Attribute Request
									} // Attribute Request List
								} // Roles Request
							} // Iterate Account Requests
						} // Accounts Request Not Empty
					} // Iterate Provisioning Plans
				} // Plans are not empty
			} // Project not null
		} // Project Not Empty
		if (applicationNames != null && integrationIds != null && project != null) {
			launchAfterProvisioningTicketExtendedRule(context, applicationNames, project, requestType, integrationIds);
		}
		LogEnablement.isLogDebugEnabled(fwLogger, "End ticketIntegrationAfterProvisioningRule");
	}
	/**
	 * Launch After Provisioning Rule for Each Applications
	 *
	 * @param applicationNames
	 * @param project
	 * @param requestType
	 * @param integrationIds
	 * @throws GeneralException
	 */
	public static void launchAfterProvisioningTicketExtendedRule(SailPointContext context, HashSet applicationNames,
			ProvisioningProject project, String requestType, List<String> integrationIds) throws GeneralException {
		LogEnablement.isLogDebugEnabled(fwLogger, "Start launchAfterProvisioningTicketExtendedRule");
		LogEnablement.isLogDebugEnabled(fwLogger, "applicationNames " + applicationNames);
		Iterator iter = applicationNames.iterator();
		while (iter.hasNext()) {
			String appName = (String) iter.next();
			if (appName != null) {
				Application app = context.getObjectByName(Application.class, appName);
				if (app != null
						&& app.getAttributeValue(WrapperRuleLibrary.AFTER_TICKET_PROVISIONING_RULE_OPTIONS) != null
						&& ((String) app.getAttributeValue(WrapperRuleLibrary.AFTER_TICKET_PROVISIONING_RULE_OPTIONS))
						.equalsIgnoreCase(WrapperRuleLibrary.AFTER_TICKET_PROVISIONING_RULE_OPTIONS_VALUE)) {
					String ruleName = (String) app
							.getAttributeValue(WrapperRuleLibrary.AFTER_TICKET_PROVISIONING_RULE_ATTR);
					if (ruleName != null && ruleName.length() > 0) {
						List<IntegrationConfig> listOfIntegrationConfigObs = new ArrayList();
						// Lets check if there is a integration config for this
						// application
						for (String integrationId : integrationIds) {
							IntegrationConfig listOfIntegrationConfig = context.getObjectById(IntegrationConfig.class,
									integrationId);
							listOfIntegrationConfigObs.add(listOfIntegrationConfig);
						}
						String managedResource = null;
						for (IntegrationConfig integrationConfig : listOfIntegrationConfigObs) {
							ManagedResource mres = integrationConfig.getManagedResource(app);
							if (mres != null) {
								LogEnablement.isLogDebugEnabled(fwLogger, "Enter IntegrationConfig " + mres);
								Rule ticketAfterProvisioningRule = context.getObjectByName(Rule.class, ruleName);
								if (ticketAfterProvisioningRule != null) {
									HashMap params = new HashMap();
									params.put("project", project);
									params.put("requestType", requestType);
									Object returnRule = context.runRule(ticketAfterProvisioningRule, params);
									LogEnablement.isLogDebugEnabled(fwLogger, "Return returnRule: " + returnRule);
									context.decache(ticketAfterProvisioningRule);
								}
								break;
							}
							context.decache(integrationConfig);
						}
					}
					if (app != null) {
						context.decache(app);
					}
				}
			}
		}
		LogEnablement.isLogDebugEnabled(fwLogger, "End launchAfterProvisioningTicketExtendedRule");
	}
	/**
	 * Joiner, Rehire Workflow Synchroize Password Plan should have Source and
	 * Target Account Request for password
	 *
	 * @throws GeneralException
	 */
	/**
	 *
	 * @param context
	 * @param flow
	 * @param workflow
	 * @throws GeneralException
	 */
	public static void doModifyProjectPasswordSync(SailPointContext context, String flow, Workflow workflow)
			throws GeneralException {
		LogEnablement.isLogDebugEnabled(fwLogger, "Enter doModifyProjectPasswordSync");
		ProvisioningProject project = null;
		String identityName = null;
		if (null != workflow) {
			project = (ProvisioningProject) workflow.get("project");
			if (project != null) {
				identityName = project.getIdentity();
				Identity identity = context.getObjectByName(Identity.class, identityName);
				String serviceCube = (String) identity.getAttribute(WrapperRuleLibrary.SERVICE_CUBE_ATTR);
				List<ProvisioningPlan> plans = project.getPlans();
				if (null != plans && plans.size() > 0) {
					for (ProvisioningPlan plan : plans) {
						syncTargetApplicationsPasswordPlan(context, flow, plan, workflow, identityName, project);
					}
				}
				if (identity != null) {
					context.decache(identity);
				}
			}
		}
		LogEnablement.isLogDebugEnabled(fwLogger, "End doModifyProjectPasswordSync");
	}
	/**
	 * Synchronize Password Value from Source To target Every application will have
	 * its own plan
	 *
	 * @param workflow
	 * @param project
	 * @param targetList
	 * @param sourceValue
	 * @return
	 */
	public static boolean syncTargetPassword(SailPointContext context, Workflow workflow, ProvisioningProject project,
			List<String> targetList, String appName, String sourceValue) {
		LogEnablement.isLogDebugEnabled(fwLogger, "Enter syncTargetPassword ");
		LogEnablement.isLogDebugEnabled(fwLogger, " syncTargetPassword source app name" + appName);
		boolean result = false;
		project = (ProvisioningProject) workflow.get("project");
		if (null != project && targetList != null && targetList.size() > 0) {
			for (String targetAppName : targetList) {
				List<ProvisioningPlan> plans = project.getPlans();
				if (null != plans && plans.size() > 0) {
					for (ProvisioningPlan plan : plans) {
						// Get Target Application Plan
						if (plan != null && plan.getTargetIntegration() != null
								&& targetAppName.equalsIgnoreCase((plan.getTargetIntegration()))) {
							LogEnablement.isLogDebugEnabled(fwLogger,
									" syncTargetPassword plan target integration app name"
											+ plan.getTargetIntegration());
							// Get Target Application Account Request - Joiner and Rehire
							List<AccountRequest> acctReqs = plan.getAccountRequests(targetAppName, null);
							if (acctReqs != null && acctReqs.size() > 0) {
								// Highly Unlikely, We may have multiple target
								// account requests
								for (AccountRequest acctReq : acctReqs) {
									LogEnablement.isLogDebugEnabled(fwLogger, " acctReq " + acctReq);
									String sourceAppName = acctReq.getApplicationName();
									LogEnablement.isLogDebugEnabled(fwLogger, " sourceAppName " + sourceAppName);
									List<AttributeRequest> attrRequests = acctReq.getAttributeRequests();
									if (attrRequests != null && attrRequests.size() > 0) {
										for (AttributeRequest attrReq : attrRequests) {
											String name = attrReq.getName();
											if (name != null && name.equalsIgnoreCase(ProvisioningPlan.ATT_PASSWORD)) {
												if (attrReq.getValue() == null || !((String) attrReq.getValue())
														.equalsIgnoreCase(sourceValue)) {
													LogEnablement.isLogDebugEnabled(fwLogger,
															" Password Synchronization from Source to Target ");
													attrReq.setValue(sourceValue);
													LogEnablement.isLogDebugEnabled(fwLogger,
															" syncTargetPassword Done From " + targetAppName);
													LogEnablement.isLogDebugEnabled(fwLogger,
															" syncTargetPassword Done To " + sourceAppName);
													result = true;
													break;
												} else if (attrReq.getValue() != null && ((String) attrReq.getValue())
														.equalsIgnoreCase(sourceValue)) {
													LogEnablement.isLogDebugEnabled(fwLogger,
															"No Need to Copy - Password is same");
													break;
												}
											}
										}
									}
								}
							} else {
								LogEnablement.isLogDebugEnabled(fwLogger,
										"Unable to Find Target Application Account Request ");
								LogEnablement.isLogDebugEnabled(fwLogger,
										" Skip Synchronization No Target Application Account Request ");
							}
						} else {
							LogEnablement.isLogDebugEnabled(fwLogger, "Unable to Find Target Application Plan  ");
							LogEnablement.isLogDebugEnabled(fwLogger,
									" Skip Synchronization No Target Application Plan");
						}
					}
				}
			} // Iterate through Target Applications
		}
		LogEnablement.isLogDebugEnabled(fwLogger, "Exit syncTargetPassword " + result);
		return result;
	}
	/**
	 * Validate Batch Request - May need this method in future
	 *
	 * @param identityName
	 * @param batchReqItemId
	 * @return
	 * @throws GeneralException
	 */
	public static boolean validateBatchRequest(SailPointContext context, String identityName, String batchReqItemId)
			throws GeneralException {
		LogEnablement.isLogDebugEnabled(fwLogger, "Enter validateBatchRequestEMR - Identity name: " + identityName
				+ " BatchRequestItem id: " + batchReqItemId);
		boolean isValid = true;
		String errorMsg = "";
		BatchRequestItem requestItem = null;
		try {
			requestItem = context.getObjectById(BatchRequestItem.class, batchReqItemId);
			if (requestItem != null) {
				String requestData = requestItem.getRequestData();
				RFC4180LineParser csvParser = new RFC4180LineParser(',');
				ArrayList<String> requestValues = csvParser.parseLine(requestData);
				if (identityName != null && requestValues != null && requestValues.get(0) != null
						&& !requestValues.get(0).isEmpty()) {
					if (requestValues.get(0).equalsIgnoreCase("ADDROLE")) {
						String roleName = "";
						if (requestValues.size() == 3) {
							if (requestValues.get(1) != null && !requestValues.get(1).isEmpty()) {
								roleName = requestValues.get(1);
								String[] roleList = roleName.split("\\|");
								if (roleList != null && roleList.length > 1) {
									for (String roleFromList : roleList) {
										// Place Holder for Any Roles Validation
									}
								} else if (roleName != null) {
									// Place Holder for Any Roles Validation
								}
							}
						}
					} else if (requestValues.get(0).equalsIgnoreCase("ADDENTITLEMENT")) {
						String appName = "";
						String entName = "";
						String entValue = "";
						/**
						 * 1. test pipe, 2. no pipe, 3. different app than epic, 4.epic email
						 */
						if (requestValues.size() >= 3) {
							if (requestValues.get(1) != null && !requestValues.get(1).isEmpty()) {
								appName = requestValues.get(1);
							}
							if (requestValues.get(2) != null && !requestValues.get(2).isEmpty()) {
								entName = requestValues.get(2);
							}
							if (requestValues.get(3) != null && !requestValues.get(3).isEmpty()) {
								entValue = requestValues.get(3);
								String[] entList = entValue.split("\\|");
								if (entList != null && entList.length > 1) {
									for (String entFromList : entList) {
										// Place Holder for Any Entitlement
										// Validation
									}
								} else if (entValue != null) {
									// Place Holder for Any Entitlement
									// Validation
								}
							}
						}
					}
				}
				context.decache(requestItem);
			}
		} catch (Exception e) {
			isValid = false;
			LogEnablement.isLogErrorEnabled(fwLogger, "Batch Request  Error: " + e.getMessage());
		}
		LogEnablement.isLogDebugEnabled(fwLogger, "End validateBatchRequest - Identity name: " + identityName
				+ " BatchRequestItem id: " + batchReqItemId + "..isValid.." + isValid);
		return isValid;
	}
	/**
	 * Start Request Manager for Lifecycle Event Workflows
	 *
	 * @param identityName
	 * @param trigger
	 * @param event
	 * @param workflowName
	 * @param amountOfSeconds
	 * @throws GeneralException
	 */
	public static void startRequestManager(SailPointContext ctx, String identityName, IdentityTrigger trigger,
			IdentityChangeEvent event, String workflowName, int amountOfSeconds, List nativeChangeList)
					throws GeneralException {
		if (trigger != null && event != null && ctx != null && workflowName != null) {
			LogEnablement.isLogDebugEnabled(fwLogger, "Start startRequestManager");
			LogEnablement.isLogDebugEnabled(fwLogger, "Start startRequestManager trigger name.." + trigger.getName());
			LogEnablement.isLogDebugEnabled(fwLogger, "Start startRequestManager event cause.." + event.getCause());
			LogEnablement.isLogDebugEnabled(fwLogger, "Start startRequestManager workflowName.." + workflowName);
			LogEnablement.isLogDebugEnabled(fwLogger,
					"Start startRequestManager nativeChangeList.." + nativeChangeList);
			LogEnablement.isLogDebugEnabled(fwLogger, "Start startRequestManager ctx.." + ctx);
			// Override Amount of Seconds
			Map map = ROADUtil.getCustomGlobalMap(ctx);
			if (map != null && map.containsKey(WrapperRuleLibrary.GLOBALWAIT)
					&& map.get(WrapperRuleLibrary.GLOBALWAIT) != null
					&& map.get(WrapperRuleLibrary.GLOBALWAIT) instanceof Integer) {
				Integer secs = (Integer) map.get(WrapperRuleLibrary.GLOBALWAIT);
				if (secs != null) {
					amountOfSeconds = secs.intValue();
					LogEnablement.isLogDebugEnabled(fwLogger,
							"Start startRequestManager amountOfSeconds override.." + amountOfSeconds);
				}
			}
			// Workflow launchArguments
			HashMap launchArgsMap = new HashMap();
			launchArgsMap.put("identityName", identityName);
			launchArgsMap.put("trigger", trigger);
			launchArgsMap.put("event", event);
			launchArgsMap.put("plan", new ProvisioningPlan());
			if (nativeChangeList != null && nativeChangeList.size() > 0) {
				launchArgsMap.put("nativeChangeList", nativeChangeList);
			}
			// Use the Request Launcher
			Request req = new Request();
			RequestDefinition reqdef = ctx.getObjectByName(RequestDefinition.class, "Workflow Request");
			req.setDefinition(reqdef);
			Attributes allArgs = new Attributes();
			// IIQTC-321: Workflow needs to be called by name. (MOVER)
			allArgs.put("workflow", workflowName);
			// Start 5 seconds from now.
			long current = System.currentTimeMillis();
			current += TimeUnit.SECONDS.toMillis(amountOfSeconds);
			String requestName = trigger.getName() + " FOR " + identityName + " " + current;
			allArgs.put("requestName", requestName);
			allArgs.putAll(launchArgsMap);
			req.setEventDate(new Date(current));
			Identity id = ctx.getObjectByName(Identity.class, "spadmin");
			req.setOwner(id);
			req.setName(requestName);
			req.setAttributes(reqdef, allArgs);
			// Launch the work flow via the request manager.
			RequestManager.addRequest(ctx, req);
			if (reqdef != null && ctx != null) {
				ctx.decache(reqdef);
			}
			if (id != null && ctx != null) {
				ctx.decache(id);
			}
			LogEnablement.isLogDebugEnabled(fwLogger, "End startRequestManager");
		}
	}
	/**
	 * Get Target Attributes for Rename and Move
	 *
	 * @return
	 * @throws GeneralException
	 */
	public static List getCommonFrameworkTargetAppAttributes(SailPointContext context) throws GeneralException {
		LogEnablement.isLogDebugEnabled(fwLogger, "Enter getCommonFrameworkTargetAppAttributes");
		boolean returnVal = false;
		// Get the Custom Object
		Custom custom = context.getObjectByName(Custom.class, WrapperRuleLibrary.GLOBALROADCUSTOM);
		// Navigate to the SmartServices GLOBAL SETTINGS Key in the Map
		List targetAggregationAttributesList = new ArrayList();
		HashMap map = new HashMap();
		if (custom != null) {
			map = (HashMap) custom.getAttributes().get(WrapperRuleLibrary.ACCELERATORPACKGLOBALSETTINGS);
			if (map != null && map.containsKey(WrapperRuleLibrary.GLOBALTARGETAGGREGATIONATTRS)
					&& map.get(WrapperRuleLibrary.GLOBALTARGETAGGREGATIONATTRS) instanceof List) {
				targetAggregationAttributesList = (List) map.get(WrapperRuleLibrary.GLOBALTARGETAGGREGATIONATTRS);
			}
		}
		LogEnablement.isLogDebugEnabled(fwLogger, "Exit getCommonFrameworkTargetAppAttributes");
		if (custom != null) {
			context.decache(custom);
		}
		return targetAggregationAttributesList;
	}
	/**
	 * Target Aggregate Application Native Id on Identity
	 *
	 * @param context
	 * @param identityName
	 * @param appName
	 * @param appNativeId
	 * @throws Exception
	 * @throws GeneralException
	 */
	public static void targetAggregateApplicationNativeIdOnIdentity(SailPointContext context, int waitTime,
			String identityName, String appName, String appNativeId) throws Exception, GeneralException {
		LogEnablement.isLogDebugEnabled(fwLogger, "Entering targetAggregateApplicationNativeIdOnIdentity");
		String errorMessage = "";
		Application appObject = context.getObjectByName(Application.class, appName);
		Identity identity = context.getObjectByName(Identity.class, identityName);
		LogEnablement.isLogDebugEnabled(fwLogger, "appNativeId.." + appNativeId);
		// Let's wait for 1 min. move takes time
		if (waitTime != 0) {
			LogEnablement.isLogDebugEnabled(fwLogger, "Wait..Start" + waitTime);
			Thread.sleep(waitTime);
			LogEnablement.isLogDebugEnabled(fwLogger, "Wait..End");
		}
		if (null != appObject && null != identity) {
			// Get Native Id from Provisioning Policy, if it is empty or null
			// This will execute Provisioning Policy Rule or Value Mappings
			if (appNativeId == null || appNativeId.length() <= 0) {
				appNativeId = getNativeIdentity(context, null, appObject, identity);
			}
			String appConnName = appObject.getConnector();
			// Now we get a handle to the connector
			Connector appConnector = sailpoint.connector.ConnectorFactory.getConnector(appObject, null);
			Schema schema = appObject.getSchema(sailpoint.connector.Connector.TYPE_ACCOUNT);
			String attrName = null;
			if (schema != null) {
				attrName = schema.getIdentityAttribute();
			}
			if (null == appNativeId) {
				errorMessage = "Failed to get native id";
				LogEnablement.isLogErrorEnabled(fwLogger, "Failed to get native id ");
			} else if (null == appConnector) {
				errorMessage = "Failed to construct an instance of connector [" + appConnName + "]";
				LogEnablement.isLogErrorEnabled(fwLogger,
						"Failed to construct an instance of connector [" + appConnName + "]");
			} else {
				LogEnablement.isLogDebugEnabled(fwLogger, "appObject..." + appObject);
				LogEnablement.isLogDebugEnabled(fwLogger, "appConnector..." + appConnector);
				LogEnablement.isLogDebugEnabled(fwLogger, "appConnName..." + appConnName);
				LogEnablement.isLogDebugEnabled(fwLogger, "schema..." + schema);
				LogEnablement.isLogDebugEnabled(fwLogger, "attrName..." + attrName);
				Application primaryAppObject = null;
				Connector primaryAppConnector = null;
				String primaryAppConnName = null;
				Schema primarySchema = null;
				String primaryAppName = null;
				String primaryAttrName = null;
				if (appObject.getConnector() != null
						&& appConnName.toString().equalsIgnoreCase("sailpoint.connector.DefaultLogicalConnector")) {
					LogEnablement.isLogDebugEnabled(fwLogger, "Connector is logical app connectors...");
					primaryAppObject = context.getObject(Application.class,
							appObject.getCompositeDefinition().getPrimaryTier());
					if (primaryAppObject != null) {
						primaryAppConnector = sailpoint.connector.ConnectorFactory.getConnector(primaryAppObject, null);
						primaryAppConnName = primaryAppObject.getConnector();
						primarySchema = primaryAppObject.getSchema(sailpoint.connector.Connector.TYPE_ACCOUNT);
						primaryAppName = primaryAppObject.getName();
						if (primarySchema != null) {
							primaryAttrName = primarySchema.getIdentityAttribute();
						}
						LogEnablement.isLogDebugEnabled(fwLogger, "Primary primaryAppObject..." + primaryAppObject);
						LogEnablement.isLogDebugEnabled(fwLogger, "Primary primaryAppConnName..." + primaryAppConnName);
						LogEnablement.isLogDebugEnabled(fwLogger,
								"Primary primaryAppConnector..." + primaryAppConnector);
						LogEnablement.isLogDebugEnabled(fwLogger, "Primary primaryAppName..." + primaryAppName);
						LogEnablement.isLogDebugEnabled(fwLogger, "Primary primaryAttrName..." + primaryAttrName);
						if (primaryAppConnName != null) {
							appConnName = primaryAppConnName;
						}
						if (primaryAppConnector != null) {
							appConnector = primaryAppConnector;
						}
						if (primaryAppObject != null) {
							appObject = primaryAppObject;
						}
						if (primaryAppName != null) {
							appName = primaryAppName;
						}
						if (primarySchema != null) {
							schema = primarySchema;
						}
						if (primaryAttrName != null) {
							attrName = primaryAttrName;
						}
						LogEnablement.isLogDebugEnabled(fwLogger,
								"Changing Requested App Connector...for logical app" + appObject.getConnector());
					}
				}
				LogEnablement.isLogDebugEnabled(fwLogger, "App schema " + schema);
				LogEnablement.isLogDebugEnabled(fwLogger, "appNativeId from Templates " + appNativeId);
				ResourceObject rObj = null;
				try {
					LogEnablement.isLogDebugEnabled(fwLogger, "Native Identity for account is " + appNativeId);
					rObj = appConnector.getObject("account", appNativeId, null);
				} catch (sailpoint.connector.ObjectNotFoundException onfe) {
					errorMessage = "Connector could not find account: [" + appNativeId + "]";
					errorMessage += "in application [" + appName + "]";
					LogEnablement.isLogErrorEnabled(fwLogger, errorMessage);
					LogEnablement.isLogErrorEnabled(fwLogger, onfe);
				} catch (Exception ex) {
					errorMessage = "Unexpected exception thrown";
					LogEnablement.isLogErrorEnabled(fwLogger, errorMessage);
					LogEnablement.isLogErrorEnabled(fwLogger, ex);
				}
				if (null == rObj) {
					errorMessage = "ERROR: Could not get ResourceObject for account: " + appNativeId;
					LogEnablement.isLogErrorEnabled(fwLogger, errorMessage);
				} else {
					Rule cRule = appObject.getCustomizationRule();
					if (null != cRule) {
						LogEnablement.isLogDebugEnabled(fwLogger,
								"Customization rule found for application " + appName);
						LogEnablement.isLogDebugEnabled(fwLogger, "Running customization rule");
						try {
							HashMap ruleArgs = new HashMap();
							ruleArgs.put("context", context);
							ruleArgs.put("log", fwLogger);
							ruleArgs.put("object", rObj);
							ruleArgs.put("application", appObject);
							ruleArgs.put("connector", appConnector);
							ruleArgs.put("state", null);
							ResourceObject newRObj = (ResourceObject) context.runRule(cRule, ruleArgs, null);
							// Lets see what we got back
							if (null != newRObj) {
								rObj = newRObj;
							}
						} catch (Exception ex) {
							LogEnablement.isLogErrorEnabled(fwLogger,
									"Error while running customization rule for " + appName);
						}
					} // end cRule null check
					// call aggregate method
					aggregateResourceObject(context, appObject, rObj);
				} // end rObj null check
			} // end appConnector null check
			context.decache(appObject);
			context.decache(identity);
		} // end appObj null check
		else {
			LogEnablement.isLogDebugEnabled(fwLogger, "Application object was null");
		}
		LogEnablement.isLogDebugEnabled(fwLogger, "Exit targetAggregateApplicationNativeIdOnIdentity");
	}

	/**
	 * Target Aggregate Application Native Id on Identity
	 *
	 * @param context
	 * @param identityName
	 * @param appName
	 * @param appNativeId
	 * @throws Exception
	 * @throws GeneralException
	 */
	public static void targetAggregateApplicationNativeIdOnIdentity(SailPointContext context, int waitTime,
			String identityName, String requestType, ProvisioningPlan plan) throws Exception, GeneralException {
		LogEnablement.isLogDebugEnabled(fwLogger, "Entering targetAggregateApplicationNativeIdOnIdentity");
		LogEnablement.isLogDebugEnabled(fwLogger,
				"Entering targetAggregateApplicationNativeIdOnIdentity " + identityName);
		LogEnablement.isLogDebugEnabled(fwLogger,
				"Entering targetAggregateApplicationNativeIdOnIdentity " + requestType);
		if (waitTime != 0) {
			LogEnablement.isLogDebugEnabled(fwLogger, "Wait..Start" + waitTime);
			Thread.sleep(waitTime);
			LogEnablement.isLogDebugEnabled(fwLogger, "Wait..End");
		}
		String errorMessage = "";
		if (plan != null) {
			Identity identity = context.getObjectByName(Identity.class, identityName);
			Boolean isDisableoRLock = false;
			Boolean isEnableoRUnLock = false;
			LogEnablement.isLogDebugEnabled(fwLogger, "identity " + identity);
			List<AccountRequest> accountRequests = plan.getAccountRequests();
			LogEnablement.isLogDebugEnabled(fwLogger, "accountRequests " + accountRequests);
			for (AccountRequest acctRequest : accountRequests) {
				if (acctRequest.getOperation().equals(ProvisioningPlan.AccountRequest.Operation.Disable)
						|| acctRequest.getOperation().equals(ProvisioningPlan.AccountRequest.Operation.Lock)) {
					isDisableoRLock = true;
					LogEnablement.isLogDebugEnabled(fwLogger, "Found isDisableoRLock.." + isDisableoRLock);
				}
				if (acctRequest.getOperation().equals(ProvisioningPlan.AccountRequest.Operation.Enable)
						|| acctRequest.getOperation().equals(ProvisioningPlan.AccountRequest.Operation.Unlock)) {
					isEnableoRUnLock = true;
					LogEnablement.isLogDebugEnabled(fwLogger, "Found isEnableoRUnLock.." + isEnableoRUnLock);
				}
			}
			List targetAttributesList = getCommonFrameworkTargetAppAttributes(context);
			if (targetAttributesList != null && targetAttributesList.size() > 0) {
				for (AccountRequest acctRequest : accountRequests) {
					List<AttributeRequest> attrRequests = acctRequest.getAttributeRequests();
					LogEnablement.isLogDebugEnabled(fwLogger, "attrRequests " + attrRequests);
					if (attrRequests != null && attrRequests.size() > 0) {
						for (AttributeRequest attrRequest : attrRequests) {
							if (attrRequest != null) {
								LogEnablement.isLogDebugEnabled(fwLogger,
										"attrRequest.getName() " + attrRequest.getName());
							}
							if (attrRequest != null && attrRequest.getName() != null
									&& targetAttributesList.contains(attrRequest.getName())) {
								Object value = attrRequest.getValue();
								LogEnablement.isLogDebugEnabled(fwLogger, "value... " + value);
								LogEnablement.isLogDebugEnabled(fwLogger,
										"attrRequest.getName() " + attrRequest.getName());
								String appName = acctRequest.getApplicationName();
								LogEnablement.isLogDebugEnabled(fwLogger, "appName.." + appName);
								if (appName != null) {
									Application appObject = context.getObjectByName(Application.class, appName);
									LogEnablement.isLogDebugEnabled(fwLogger, "appObject " + appObject);
									if (null != appObject && null != identity) {
										// Get Native Id from Provisioning
										// Policy, if it is empty or null
										// This will execute Provisioning Policy
										// Rule or Value Mappings
										/**
										 * New DN will be used in case of modify/ enable / unlock In case of
										 * disable/lock/move, New DN cannot be used from create policies, we need
										 * to get disable container so get it from attribute request and append CN
										 */
										String appNativeId = null;
										if (!isDisableoRLock) {
											appNativeId = getNativeIdentity(context, null, appObject, identity);
										}

										if (isDisableoRLock || attrRequest.getName().equals(WrapperRuleLibrary.ACNEWPARENT)) {
										    if (value != null && value instanceof String) {
										        appNativeId = appendCNtoMovedDn(acctRequest.getNativeIdentity(), value);
										    }
										}

										String appConnName = appObject.getConnector();
										// Now we get a handle to the connector
										Connector appConnector = sailpoint.connector.ConnectorFactory
												.getConnector(appObject, null);
										Schema schema = appObject.getSchema(sailpoint.connector.Connector.TYPE_ACCOUNT);
										String attrName = null;
										if (schema != null) {
											attrName = schema.getIdentityAttribute();
										}
										if (null == appNativeId) {
											errorMessage = "Failed to get native id";
											LogEnablement.isLogErrorEnabled(fwLogger, "Failed to get native id ");
										} else if (null == appConnector) {
											errorMessage = "Failed to construct an instance of connector ["
													+ appConnName + "]";
											LogEnablement.isLogErrorEnabled(fwLogger,
													"Failed to construct an instance of connector [" + appConnName
													+ "]");
										} else {
											LogEnablement.isLogDebugEnabled(fwLogger, "appObject..." + appObject);
											LogEnablement.isLogDebugEnabled(fwLogger, "appConnector..." + appConnector);
											LogEnablement.isLogDebugEnabled(fwLogger, "appConnName..." + appConnName);
											LogEnablement.isLogDebugEnabled(fwLogger, "schema..." + schema);
											LogEnablement.isLogDebugEnabled(fwLogger, "attrName..." + attrName);
											Application primaryAppObject = null;
											Connector primaryAppConnector = null;
											String primaryAppConnName = null;
											Schema primarySchema = null;
											String primaryAppName = null;
											String primaryAttrName = null;
											if (appObject.getConnector() != null && appConnName.toString()
													.equalsIgnoreCase("sailpoint.connector.DefaultLogicalConnector")) {
												LogEnablement.isLogDebugEnabled(fwLogger,
														"Connector is logical app connectors...");
												primaryAppObject = context.getObject(Application.class,
														appObject.getCompositeDefinition().getPrimaryTier());
												if (primaryAppObject != null) {
													primaryAppConnector = sailpoint.connector.ConnectorFactory
															.getConnector(primaryAppObject, null);
													primaryAppConnName = primaryAppObject.getConnector();
													primarySchema = primaryAppObject
															.getSchema(sailpoint.connector.Connector.TYPE_ACCOUNT);
													primaryAppName = primaryAppObject.getName();
													if (primarySchema != null) {
														primaryAttrName = primarySchema.getIdentityAttribute();
													}
													LogEnablement.isLogDebugEnabled(fwLogger,
															"Primary primaryAppObject..." + primaryAppObject);
													LogEnablement.isLogDebugEnabled(fwLogger,
															"Primary primaryAppConnName..." + primaryAppConnName);
													LogEnablement.isLogDebugEnabled(fwLogger,
															"Primary primaryAppConnector..." + primaryAppConnector);
													LogEnablement.isLogDebugEnabled(fwLogger,
															"Primary primaryAppName..." + primaryAppName);
													LogEnablement.isLogDebugEnabled(fwLogger,
															"Primary primaryAttrName..." + primaryAttrName);
													if (primaryAppConnName != null) {
														appConnName = primaryAppConnName;
													}
													if (primaryAppConnector != null) {
														appConnector = primaryAppConnector;
													}
													if (primaryAppObject != null) {
														appObject = primaryAppObject;
													}
													if (primaryAppName != null) {
														appName = primaryAppName;
													}
													if (primarySchema != null) {
														schema = primarySchema;
													}
													if (primaryAttrName != null) {
														attrName = primaryAttrName;
													}
													LogEnablement.isLogDebugEnabled(fwLogger,
															"Changing Requested App Connector...for logical app"
																	+ appObject.getConnector());
												}
											}
											LogEnablement.isLogDebugEnabled(fwLogger, "App schema " + schema);
											LogEnablement.isLogDebugEnabled(fwLogger,
													"appNativeId from Templates " + appNativeId);
											ResourceObject rObj = null;
											try {
												LogEnablement.isLogDebugEnabled(fwLogger,
														"Native Identity for account is " + appNativeId);
												rObj = appConnector.getObject("account", appNativeId,
														null);
												// Object GUID on LDAP Resource Object Should be same as Existing Link
												// UUID
												if (fwLogger.isDebugEnabled())
													LogEnablement.isLogDebugEnabled(fwLogger,
															"Printing rObj " + rObj.toXml());
											} catch (sailpoint.connector.ObjectNotFoundException onfe) {
												errorMessage = "Connector could not find account: [" + appNativeId
														+ "]";
												errorMessage += "in application [" + appName + "]";
												LogEnablement.isLogErrorEnabled(fwLogger, errorMessage);
												LogEnablement.isLogErrorEnabled(fwLogger, onfe);
											} catch (Exception ex) {
												errorMessage = "Unexpected exception thrown";
												LogEnablement.isLogErrorEnabled(fwLogger, errorMessage);
												LogEnablement.isLogErrorEnabled(fwLogger, ex);
											}
											if (null == rObj) {
												errorMessage = "ERROR: Could not get ResourceObject for account: "
														+ appNativeId;
												LogEnablement.isLogErrorEnabled(fwLogger, errorMessage);
											} else {
												Rule cRule = appObject.getCustomizationRule();
												if (null != cRule) {
													LogEnablement.isLogDebugEnabled(fwLogger,
															"Customization rule found for application " + appName);
													LogEnablement.isLogDebugEnabled(fwLogger,
															"Running customization rule");
													try {
														HashMap ruleArgs = new HashMap();
														ruleArgs.put("context", context);
														ruleArgs.put("log", fwLogger);
														ruleArgs.put("object", rObj);
														ruleArgs.put("application", appObject);
														ruleArgs.put("connector", appConnector);
														ruleArgs.put("state", null);
														ResourceObject newRObj = (ResourceObject) context.runRule(cRule,
																ruleArgs, null);
														// Lets see what we got
														// back
														if (null != newRObj) {
															rObj = newRObj;
														}
													} catch (Exception ex) {
														LogEnablement.isLogErrorEnabled(fwLogger,
																"Error while running customization rule for "
																		+ appName);
													}
												} // end cRule null check
												// call aggregate method
												aggregateResourceObject(context, appObject, rObj);
											} // end rObj null check
										} // end appConnector null check
										context.decache(appObject);
										context.decache(identity);
									} // End appObj null check
								} // End appName not null
							} // End Move Check
							// Go to Next Account Request
							break;
						} // End For Attribute Request
					} // Make sure there is attribute request
				} // End For Account Request
			} // Make sure there is target attribute list
		} // End PLan Check
		else {
			LogEnablement.isLogDebugEnabled(fwLogger, "Plan  null");
		}
		LogEnablement.isLogDebugEnabled(fwLogger, "Exit targetAggregateApplicationNativeIdOnIdentity");
	}
	/**
	 * Aggregate Resource Object on Identity
	 *
	 * @param context
	 * @param appObject
	 * @param rObj
	 * @throws GeneralException
	 */
	private static void aggregateResourceObject(SailPointContext context, Application appObject, ResourceObject rObj)
			throws GeneralException {
		String errorMessage = "";
		Attributes argMap = new Attributes();
		argMap.put("promoteAttributes", "true");
		argMap.put("correlateEntitlements", "true");
		argMap.put("noOptimizeReaggregation", "true");
		Aggregator agg = new Aggregator(context, argMap);
		LogEnablement.isLogDebugEnabled(fwLogger, "Created aggregator");
		if (null == agg) {
			errorMessage = "Null Aggregator returned from constructor. Unable to Aggregate!";
			LogEnablement.isLogErrorEnabled(fwLogger, errorMessage);
		}
		agg.aggregate(appObject, rObj);
		LogEnablement.isLogDebugEnabled(fwLogger, "Aggregation Resource Object Completed");
	}
	/**
	 * Get Native Id from Provisioning Policies
	 *
	 * @param context
	 * @param appName
	 * @param identity
	 * @return
	 * @throws Exception
	 */
	public static String getNativeIdentity(SailPointContext context, String secondaryAccount, Application app,
			Identity identity) throws Exception {
		LogEnablement.isLogDebugEnabled(fwLogger, "Enter getNativeIdentity");
		String nativeId = "";
		Schema schema = app.getAccountSchema();
		String niField = schema.getIdentityAttribute();
		LogEnablement.isLogDebugEnabled(fwLogger, "...Got Application Schema for '" + app.getName() + "'");
		LogEnablement.isLogDebugEnabled(fwLogger, "...Got Application Schema for niField'" + niField + "'");
		List<Template> templates = app.getTemplates();
		LogEnablement.isLogDebugEnabled(fwLogger, "...Got Application templates '" + templates + "'");
		Template updateTemp = null;
		if (templates != null && templates.size() > 0) {
			for (Template temp : templates) {
				Template.Usage usage = temp.getUsage();
				LogEnablement.isLogDebugEnabled(fwLogger, "...Got Application templates usage '" + usage + "'");
				LogEnablement.isLogDebugEnabled(fwLogger,
						"...Got Application templates getSchemaObjectType '" + temp.getSchemaObjectType() + "'");
				if (temp.getSchemaObjectType().equalsIgnoreCase("account") && usage.equals(Template.Usage.Create)) {
					LogEnablement.isLogDebugEnabled(fwLogger,
							"...Got Application templates usage getSchemaObjectType'" + usage + "'");
					updateTemp = temp;
					break;
				}
			}
			if (updateTemp != null) {
				List<Field> fields = updateTemp.getFields(context);
				LogEnablement.isLogDebugEnabled(fwLogger, "...Got Application templates fields '" + fields + "'");
				if (fields != null && fields.size() > 0) {
					for (Field field : fields) {
						String fieldName = field.getName();
						String displayName = field.getDisplayName();
						if (niField != null && niField.compareTo(fieldName) == 0) {
							HashMap params = new HashMap();
							params.put("context", context);
							params.put("identity", identity);
							params.put("field", field);
							params.put("secondaryAccount", secondaryAccount);
							params.put("accountRequest", null);
							params.put("application", app);
							params.put("current", null);
							params.put("link", null);
							params.put("group", null);
							params.put("objectRequest", null);
							params.put("operation", null);
							params.put("project", null);
							params.put("role", null);
							params.put("template", updateTemp);
							Rule rule = field.getFieldRule();
							if (rule != null) {
								LogEnablement.isLogDebugEnabled(fwLogger, "....Got Rule '" + rule.getName() + "'");
								try {
									// TODO: Might need to have spExtAttrs in
									// Joiner Workflow
									nativeId = (String) context.runRule(rule, params);
								} catch (Exception re) {
									LogEnablement.isLogErrorEnabled(fwLogger,
											"*** EXCEPTION RUNNING RULE: " + re.toString());
									continue;
								}
							} else if (field.getScript() != null) {
								try {
									nativeId = (String) context.runScript(field.getScript(), params);
								} catch (Exception re) {
									LogEnablement.isLogErrorEnabled(fwLogger,
											"*** EXCEPTION RUNNING SCRIPT: " + re.toString());
									continue;
								}
							} else {
								LogEnablement.isLogDebugEnabled(fwLogger, "....No Rule ");
								nativeId = (String) field.getValue();
							}
						}
					}
				}
			}
		}
		LogEnablement.isLogDebugEnabled(fwLogger, "Exit getNativeIdentity = " + nativeId);
		return nativeId;
	}
	/**
	 * Get Native Id from Provisioning Policies
	 *
	 * @param context
	 * @param appName
	 * @param identity
	 * @param identity
	 * @return secondaryAccount
	 * @throws Exception
	 */
	public static String getNativeIdentityOnApplication(SailPointContext context, String secondaryAccount,
			String appName, String identityName) throws Exception {
		LogEnablement.isLogDebugEnabled(fwLogger, "Enter getNativeIdentityOnApplication");
		Application app = context.getObjectByName(Application.class, appName);
		Identity identity = context.getObjectByName(Identity.class, identityName);
		String nativeId = getNativeIdentity(context, secondaryAccount, app, identity);
		if (app != null) {
			context.decache(app);
		}
		if (identity != null) {
			context.decache(identity);
		}
		LogEnablement.isLogDebugEnabled(fwLogger, "Exit getNativeIdentityOnApplication = " + nativeId);
		return nativeId;
	}

    /**
     * Append CN to Disabled Container
     *
     * @param acctReqNativeId
     * @param disbaledDNContainer
     * @return modified dn
     */
    public static String appendCNtoMovedDn(String acctReqNativeId, Object disabledDNContainer) {
        String newDn = null;
        LogEnablement.isLogDebugEnabled(fwLogger, "Start appendCNtoMovedDn.." + acctReqNativeId);
        if (acctReqNativeId != null && disabledDNContainer instanceof String) {
            String cn = getCNFromNativeId(acctReqNativeId);
            LogEnablement.isLogDebugEnabled(fwLogger, "cn... " + cn);
            if (cn != null) {
                newDn = cn + "," + disabledDNContainer.toString();
            }
        }
        LogEnablement.isLogDebugEnabled(fwLogger, "End appendCNtoMovedDn.." + newDn);

        return newDn;
	}

    /**
     * get parent from Native Id
     *
     * @param context
     * @param nativeId
     * @return parent dn
     */
    public static String getParentFromNativeId(String nativeId) {

        LogEnablement.isLogDebugEnabled(fwLogger, "Start getParentFromNativeId - nativeId is " + nativeId);

        String parentDn = null;

        try {
            LdapName dn = new LdapName(nativeId);
            parentDn = dn.getPrefix(dn.size() - 1).toString();
        } catch (InvalidNameException ine) {
            LogEnablement.isLogErrorEnabled(fwLogger, ine.getLocalizedMessage());
        }

        LogEnablement.isLogDebugEnabled(fwLogger, "getParentFromNativeId - parentDn: " + parentDn);

        return parentDn;
	}

    /**
     * Get CN From Native Id
     *
     * @param context
     * @param nativeId
     * @return
     */
    public static String getCNFromNativeId(String nativeId) {

        LogEnablement.isLogDebugEnabled(fwLogger, "Start getCNFromNativeId - nativeId is " + nativeId);

        String cnValue = null;

        try {
            LdapName dn = new LdapName(nativeId);
            cnValue = dn.getSuffix(dn.size() - 1).toString();
        } catch (InvalidNameException ine) {
            LogEnablement.isLogErrorEnabled(fwLogger, ine.getLocalizedMessage());
        }

        LogEnablement.isLogDebugEnabled(fwLogger, "getCNFromNativeId - CN: " + cnValue);

        return cnValue;
    }

	/**
	 * Move LDAP Accounts
	 *
	 * @param acNewParent
	 *            OU container
	 * @param appName
	 *            Application Name
	 * @param nativeId
	 *            Native ID of account
	 * @return AttributeRequest
	 */
	public static AttributeRequest moveOUAttrRequest(String acNewParent, String appName, String nativeId,
			String comment) {
		// New OU, no CN Value
		AttributeRequest newDNAttributeRequest = new AttributeRequest();
		if (comment != null)
			newDNAttributeRequest.setComments(comment + " " + new Date());
		newDNAttributeRequest.setName(WrapperRuleLibrary.ACNEWPARENT);
		newDNAttributeRequest.setValue(acNewParent);
		newDNAttributeRequest.setOperation(ProvisioningPlan.Operation.Set);
		return newDNAttributeRequest;
	}
	/**
	 * Move LDAP Accounts
	 *
	 * @param acNewParent
	 *            OU container
	 * @param appName
	 *            Application Name
	 * @param nativeId
	 *            Native ID of account
	 * @param comment
	 * @return AccountRequest
	 */
	public static AccountRequest moveOU(String acNewParent, String appName, String nativeId, String comment) {
		// Modify Account Request...
		AccountRequest moveAccountRequest = new AccountRequest();
		moveAccountRequest.setOperation(AccountRequest.Operation.Modify);
		moveAccountRequest.setApplication(appName);
		// Old DN, This will have CN Value
		moveAccountRequest.setNativeIdentity(nativeId);
		moveAccountRequest.setComments(comment + " " + new Date());
		// New OU, no CN Value
		AttributeRequest newDNAttributeRequest = new AttributeRequest();
		newDNAttributeRequest.setName(WrapperRuleLibrary.ACNEWPARENT);
		newDNAttributeRequest.setValue(acNewParent);
		newDNAttributeRequest.setOperation(ProvisioningPlan.Operation.Set);
		// Add attribute Request
		moveAccountRequest.add(newDNAttributeRequest);
		return moveAccountRequest;
	}
	/**
	 * Rename LDAP Accounts
	 *
	 * @param fullName
	 *            CN
	 * @param appName
	 *            Application Name
	 * @param nativeId
	 *            Native ID of account
	 * @param comment
	 * @return AccountRequest
	 */
	public static AccountRequest rename(String fullName, String appName, String nativeId, String comment) {
		String acNewName = "cn=" + fullName;
		// Modify Account Request...
		AccountRequest renameAccountRequest = new AccountRequest();
		renameAccountRequest.setOperation(AccountRequest.Operation.Modify);
		renameAccountRequest.setApplication(appName);
		// Old DN, This will have CN Value
		renameAccountRequest.setNativeIdentity(nativeId);
		renameAccountRequest.setComments(comment + " " + new Date());
		// New CN Value, no OU
		AttributeRequest newCNAttributeRequest = new AttributeRequest();
		newCNAttributeRequest.setName(WrapperRuleLibrary.ACNEWNAME);
		newCNAttributeRequest.setValue(acNewName);
		newCNAttributeRequest.setOperation(ProvisioningPlan.Operation.Set);
		// Add attribute Request
		renameAccountRequest.add(newCNAttributeRequest);
		return renameAccountRequest;
	}
	/**
	 * Turn OFF LCE Events during external provisioning Turn ON internal
	 * provisioning (create, edit, register)
	 *
	 * @param flow
	 * @return
	 */
	public static String turnOffIdentityLCETriggers(String flow) {
		if (flow != null) {
			if (flow.equalsIgnoreCase(IdentityRequest.IDENTITY_CREATE_FLOW_CONFIG_NAME)
					|| flow.equalsIgnoreCase(IdentityRequest.IDENTITY_UPDATE_FLOW_CONFIG_NAME)
					|| flow.equalsIgnoreCase(RegistrationBean.FLOW_CONFIG_NAME)) {
				return "false";
			} else {
				return "true";
			}
		} else {
			return "true";
		}
	}
	/**
	 * Validate Project Has Plans with Account Requests
	 *
	 * @param context
	 * @param project
	 * @return
	 */
	public static boolean ensureProjectHasWellConstructedPlans(SailPointContext context, ProvisioningProject project) throws GeneralException {
		boolean flag = false;
		if (project != null) {
			List<ProvisioningPlan> plans = project.getPlans();
			//It's possible we don't have expansions done because we have a delayed
			//attribute assignment.
			if (plans == null || plans.size() == 0) {
			    plans = new ArrayList<ProvisioningPlan>();
			    plans.add(project.getMasterPlan());
			}
			if (plans != null && plans.size() > 0) {
				for (ProvisioningPlan plan : plans) {
					List reqs = plan.getAccountRequests();
					if (reqs != null && reqs.size() > 0) {
						flag = true;
						break;
					}
				}
			}
		}
		LogEnablement.isLogDebugEnabled(fwLogger, "ensureProjectHasWellConstructedPlans returning " + flag);
		return flag;
	}
	/**
	 *
	 * @param context
	 * @param wfcontext
	 * @param identityRequestId
	 */
	public static void storeIdentityRequestIdOnWfCaseVariable(SailPointContext context, WorkflowContext wfcontext,
			String identityRequestId) {
		try {
			WorkflowContext top = wfcontext.getRootContext();
			String irId = (String) top.getVariable(ROADUtil.REQUESTID);
			if (irId == null) {
				top.setVariable(ROADUtil.REQUESTID, identityRequestId);
			}
		} catch (Exception e) {
			LogEnablement.isLogErrorEnabled(fwLogger,
					"Exceptions storeIdentityRequestIdOnWfCaseVariable = " + e.getMessage());
		}
	}
	/**
	 * Build WorkItem Comments for Requestor Form
	 *
	 * @param workflow
	 * @param item
	 * @param workItemComments
	 * @param formAttVar
	 * @param modifyProjectVar
	 * @throws Exception
	 */
	public static void buildWorkItemCommentsAndFormAttributes(Workflow workflow, WorkItem item, List workItemComments,
			String formAttVar, String modifyProjectVar) throws Exception {
		if (workItemComments == null) {
			workItemComments = new ArrayList();
		}
		Form customForm = item.getForm();
		Iterator fieldIterator = customForm.iterateFields();
		Attributes formAttrs = new Attributes();
		while (fieldIterator.hasNext()) {
			Field field = (Field) fieldIterator.next();
			if (!field.isReadOnly()) {
				String name = field.getName();
				Object value = field.getValue();
				workItemComments.add(name + "=" + value);
				formAttrs.put(WrapperRuleLibrary.FORMROLEFIELDKEY + name, value);
			}
		}
		workflow.put(formAttVar, formAttrs);
		workflow.put(modifyProjectVar, true);
	}
	/**
	 * Split Required After Manager Level Approval
	 *
	 * @param requiredApprovalTypes
	 * @return
	 */
	public static boolean isNoSplitManagerApprovalRequired(List requiredApprovalTypes) {
		LogEnablement.isLogDebugEnabled(fwLogger, "Enter isNoSplitManagerApprovalRequired");
		boolean approvalRequired = false;
		if (requiredApprovalTypes != null
				&& requiredApprovalTypes.contains(WrapperRuleLibrary.MANAGERSERVICEOWNERAPPROVAL)) {
			approvalRequired = true;
		}
		LogEnablement.isLogDebugEnabled(fwLogger,
				"Exit isNoSplitManagerApprovalRequired - Result: " + approvalRequired);
		return approvalRequired;
	}
	/**
	 * Is this request eligible for split
	 *
	 * @param requiredApprovalTypes
	 * @param approvalSet
	 * @return
	 * @throws GeneralException
	 */
	public static boolean isEligibleForSplit(SailPointContext context, List<String> requiredApprovalTypes,
			ApprovalSet approvalSet) throws GeneralException {
		LogEnablement.isLogDebugEnabled(fwLogger, "Enter isEligibleForSplit");
		boolean isEligible = false;
		// If approvalSet only has 1 item, it is not eligible for split
		if (approvalSet != null && approvalSet.getItems() != null && approvalSet.getItems().size() == 1) {
			isEligible = false;
		} else {
			// If all approval types of requestType have false eligibility, it
			// is not eligible for split
			Map splitEligibilityMap = (Map) ApprovalRuleLibrary.getMappingObjectEntry(context,
					WrapperRuleLibrary.SPLITELIGIBILITY);
			if (splitEligibilityMap != null) {
				for (String reqApprovalType : requiredApprovalTypes) {
					String approvalEligibility = (String) splitEligibilityMap.get(reqApprovalType);
					if (approvalEligibility != null && approvalEligibility.equalsIgnoreCase("true")) {
						isEligible = true;
						break;
					}
				}
			}
		}
		LogEnablement.isLogDebugEnabled(fwLogger, "Exit isEligibleForSplit - Result: " + isEligible);
		return isEligible;
	}
	/**
	 * Force Load All WrapperRuleLibrary Custom Artifacts
	 *
	 * @return custom
	 * @throws GeneralException
	 */
	synchronized static void forceLoad(SailPointContext context) throws GeneralException {
		custom = context.getObjectByName(Custom.class, WrapperRuleLibrary.GLOBALROADCUSTOM);
		customPersona = context.getObjectByName(Custom.class, WrapperRuleLibrary.PERSONACUSTOM);
	}
	/**
	 * Force Load Global Definitions
	 *
	 * @return custom
	 * @throws GeneralException
	 */
	public static void forceLoadGlobalDefinitions(SailPointContext context) throws GeneralException {
		custom = context.getObjectByName(Custom.class, WrapperRuleLibrary.GLOBALROADCUSTOM);
	}
	/**
	 * Force Load Global Definitions
	 *
	 * @return custom
	 * @throws GeneralException
	 */
	public static void forceLoadGlobalPerson(SailPointContext context) throws GeneralException {
		customPersona = context.getObjectByName(Custom.class, WrapperRuleLibrary.PERSONACUSTOM);
	}
	/**
	 * Get Request Type
	 * @param requestType
	 * @param flow
	 * @param identityName
	 * @param workflow
	 * @param plan
	 * @param byPassApprovals
	 * @param emergReq
	 * @param fireFighterAccess
	 * @return
	 * @throws Exception
	 */
	public static String getRequestType(SailPointContext context, String requestType, String flow, String identityName,
			Workflow workflow, ProvisioningPlan plan, Object byPassApprovals, String emergReq, String fireFighterAccess)
					throws Exception {
		LogEnablement.isLogDebugEnabled(fwLogger, "..Start..getRequestType..flow." + flow);
		LogEnablement.isLogDebugEnabled(fwLogger, "..Start..getRequestType.requestType.." + requestType);
		LogEnablement.isLogDebugEnabled(fwLogger, "..Start..getRequestType.identityName.." + identityName);
		LogEnablement.isLogDebugEnabled(fwLogger, "..Start..getRequestType..fireFighterAccess." + fireFighterAccess);
		LogEnablement.isLogDebugEnabled(fwLogger, "..Start..getRequestType.byPassApprovals.." + byPassApprovals);
		LogEnablement.isLogDebugEnabled(fwLogger, "..Start..getRequestType.emergReq.." + emergReq);
		Identity identity = context.getObjectByName(Identity.class, identityName);
		String calculateRequestType = null;
		// Return what is passed in, mainly used for Request Manager, Lifecycle Events
		// and After Provisioning Rule Requests
		if (requestType != null) {
			return requestType;
		}
		if (requestType != null && requestType.equalsIgnoreCase("EXTERNAL FEATURE")) {
			workflow.put("flow", "External");
			return requestType;
		}
		if (fireFighterAccess != null && fireFighterAccess.equalsIgnoreCase("True") && byPassApprovals != null
				&& byPassApprovals instanceof Boolean && (Boolean) byPassApprovals) {
			requestType = "FIREFIGHTER FEATURE";
			workflow.put("foregroundProvisioning", "true");
			return requestType;
		}
               if (flow != null && flow.equalsIgnoreCase("UnlockAccount")) {
                       return "Unlock Account";
               }
		String joinerDisabled = ROADUtil.roadAttributeDisabled(context, "Identity", JoinerRuleLibrary.JOINERATTRNEEDSJOINER);
		boolean joinerEnabled = false;
		// IMPLICIT JOINER Event is ENABLED
		if (joinerDisabled != null && joinerDisabled.length() > 0 && joinerDisabled.equalsIgnoreCase("FALSE")) {
			joinerEnabled = true;
		}
		// SERVICE CUBE ENABLED
		String serviceAccountEnabled = ObjectConfigAttributesRuleLibrary.extendedAttrServiceAccountEnabled(context);
		boolean servAccEnabled = false;
		if (serviceAccountEnabled != null && serviceAccountEnabled.length() > 0
				&& serviceAccountEnabled.equalsIgnoreCase("TRUE")) {
			servAccEnabled = true;
		}
		// Identity TYPE ENABLED
		String identityTypeEnabled = ObjectConfigAttributesRuleLibrary.extendedAttrIdentityTypeEnabled(context);
		boolean identityTypeEnab = false;
		if (identityTypeEnabled != null && identityTypeEnabled.length() > 0
				&& identityTypeEnabled.equalsIgnoreCase("TRUE")) {
			identityTypeEnab = true;
		}
		String primaryServiceAccountOwner = null;
		String secondaryServiceAccountOwner = null;
		String administratorName = null;
		String managerFromRequest = null;
		String emailFromRequest = null;
		Identity stub = null;
		Identity managerFromRequestObj = null;
		Identity adminObj = null;
		boolean isServiceCube = false;
		String valueOfIdentityType = null;
		AccountRequest accountRequestCreateOrRegister = null;
		// Start Create, Edit, and Register
		if (flow != null) {
			if (flow.equalsIgnoreCase("IdentityCreateRequest") || flow.equalsIgnoreCase("Registration")
					|| flow.equalsIgnoreCase("IdentityEditRequest")) {
				if (plan != null) {
					List<AccountRequest> acctReqList = plan.getAccountRequests();
					if (acctReqList != null) {
						for (AccountRequest acctReq : acctReqList) {
							String appName = acctReq.getApplication();
							LogEnablement.isLogDebugEnabled(fwLogger, "..appName.." + appName);
							if (appName.equalsIgnoreCase(ProvisioningPlan.APP_IIQ)
									|| appName.equalsIgnoreCase(ProvisioningPlan.IIQ_APPLICATION_NAME)
									|| appName.equalsIgnoreCase(ProvisioningPlan.APP_IDM)) {
								List<AttributeRequest> attrRequests = acctReq.getAttributeRequests();
								accountRequestCreateOrRegister = acctReq;
								if (attrRequests != null && attrRequests.size() > 0) {
									for (AttributeRequest attrReq : attrRequests) {
										String name = attrReq.getName();
										LogEnablement.isLogDebugEnabled(fwLogger, "..name.." + name);
										// Create - Exclude Joiner for Service Cube
										if (servAccEnabled && name != null
												&& name.equalsIgnoreCase(WrapperRuleLibrary.SERVICE_CUBE_ATTR)
												&& flow.equalsIgnoreCase("IdentityCreateRequest")) {
											String value = (String) attrReq.getValue();
											if (value != null && value.equalsIgnoreCase("TRUE")) {
												isServiceCube = true;
												LogEnablement.isLogDebugEnabled(fwLogger,
														"..isServiceCube.." + isServiceCube);
											}
										}
										// Create - Identity Type
										if (identityTypeEnab && name != null && name.equalsIgnoreCase("type")
												&& flow.equalsIgnoreCase("IdentityCreateRequest")) {
											String value = (String) attrReq.getValue();
											if (value != null) {
												valueOfIdentityType = value;
												LogEnablement.isLogDebugEnabled(fwLogger,
														"..valueOfIdentityType.." + valueOfIdentityType);
											}
										}
										// Create - Administrator
										// Edit - Administrator
										if (identityTypeEnab && name != null && name.equalsIgnoreCase("administrator")
												&& (flow.equalsIgnoreCase("IdentityEditRequest")
														|| flow.equalsIgnoreCase("IdentityCreateRequest"))) {
											String value = (String) attrReq.getValue();
											administratorName = value;
											LogEnablement.isLogDebugEnabled(fwLogger,
													"..administratorName.." + administratorName);
										}
										// Create - New Primary Owner
										// Edit - Change Primary Owner
										if (servAccEnabled && name != null && name.equalsIgnoreCase("saccountOwnerone")
												&& (flow.equalsIgnoreCase("IdentityEditRequest")
														|| flow.equalsIgnoreCase("IdentityCreateRequest"))) {
											String value = (String) attrReq.getValue();
											primaryServiceAccountOwner = value;
										}
										// Registration - New Manager
										// Create - New Manager
										// Edit - Change Existing Manager
										if (name != null && name.equalsIgnoreCase("manager")) {
											String value = (String) attrReq.getValue();
											managerFromRequest = value;
										}
										// Registration - New Email
										// Create - New Email
										// Edit - Change Existing Email
										if (name != null && name.equalsIgnoreCase("email")) {
											String value = (String) attrReq.getValue();
											emailFromRequest = value;
										}
									}
								}
							}
						}
					}
				}
				LogEnablement.isLogDebugEnabled(fwLogger, "managerFromRequest " + managerFromRequest);
				LogEnablement.isLogDebugEnabled(fwLogger,
						"secondaryServiceAccountOwner " + secondaryServiceAccountOwner);
				LogEnablement.isLogDebugEnabled(fwLogger, "primaryServiceAccountOwner " + primaryServiceAccountOwner);
			}
			// Create Stub for Create and Register
			if (flow.equalsIgnoreCase("IdentityCreateRequest")
					|| flow.equalsIgnoreCase("Registration") && identity == null) {
				stub = new Identity();
				stub.setName(identityName);
				stub.setCorrelated(true);
				stub.setCorrelatedOverridden(true);
				// Next four attributes are special attributes to derive cube display name
				boolean firstNameSent = false;
				boolean lastNameSent = false;
				String firstName = null;
				String lastName = null;
				// Bug RAPIDSS-356
				// We need more attributes on the stub cube so the contractor sponsor knows what
				// he/she is approving
				// Find out possible attributes to pull by looking at cube attributes, including
				// standard ones
				List cubeAttributesList = ROADUtil.getCubeAttributesList(context, true, true, false, true);
				// When creating or registering an identity an IIQ account request will be sent
				// through, grab it
				AccountRequest iiqCreateRequest = plan.getIIQAccountRequest();
				if (iiqCreateRequest != null) {
					List<AttributeRequest> iiqAttributeRequests = iiqCreateRequest.getAttributeRequests();
					for (AttributeRequest attributeRequest : iiqAttributeRequests) {
						// if the attribute request name is in the cube attribute list, set the
						// attribute on the stub cube
						String attributeRequestName = attributeRequest.getName();
						if (cubeAttributesList.contains(attributeRequestName)) {
							stub.setAttribute(attributeRequestName, attributeRequest.getValue());
							if (attributeRequestName.equalsIgnoreCase("firstName")) {
								firstNameSent = true;
								firstName = (String) attributeRequest.getValue();
							} else if (attributeRequestName.equalsIgnoreCase("lastName")) {
								lastNameSent = true;
								lastName = (String) attributeRequest.getValue();
							}
						}
					}
					// Set Display name workflow attr if first and last name were sent
					if (firstNameSent && lastNameSent) {
						LogEnablement.isLogDebugEnabled(fwLogger,
								"setting identityDisplayName workflow attr because first and last name are being sent from form");
						workflow.put("identityDisplayName", firstName + " " + lastName);
					}
				}
			}
			// Registration Stub Attribute Set
			if (flow.equalsIgnoreCase("Registration") && stub != null) {
				// In case manager rejects the request, joiner needs to be turned off
				// This must come from Registration Form
				// Prune Task Will Clean Identity
				if (managerFromRequest != null) {
					managerFromRequestObj = context.getObjectByName(Identity.class, managerFromRequest);
					if (managerFromRequestObj != null) {
						stub.setManager(managerFromRequestObj);
					}
				}
				if (emailFromRequest != null) {
					stub.setEmail(emailFromRequest);
				}
				// Set Type
				if (identityTypeEnab && valueOfIdentityType != null) {
					stub.setType(valueOfIdentityType);
				}
				calculateRequestType = "REGISTRATION FEATURE";
				LogEnablement.isLogDebugEnabled(fwLogger, "calculateRequestType " + calculateRequestType);
			}
			// Create Stub Attribute Set
			if ((flow.equalsIgnoreCase("IdentityCreateRequest")) && stub != null) {
				// Set Type
				if (identityTypeEnab && valueOfIdentityType != null) {
					stub.setType(valueOfIdentityType);
				}
				// Set Administrator
				if (identityTypeEnab && administratorName != null) {
					if (administratorName != null) {
						adminObj = context.getObjectByName(Identity.class, administratorName);
						if (adminObj != null) {
							stub.setAdministrator(adminObj);
						}
					}
				}
				if (servAccEnabled && isServiceCube) {
					stub.setAttribute(WrapperRuleLibrary.SERVICE_CUBE_ATTR, "TRUE");
					LogEnablement.isLogDebugEnabled(fwLogger, "Set Service Cube True 7.2 " + isServiceCube);
				} else if (servAccEnabled && identityTypeEnab && valueOfIdentityType != null
						&& valueOfIdentityType.equalsIgnoreCase("Service")) {
					stub.setAttribute(WrapperRuleLibrary.SERVICE_CUBE_ATTR, "TRUE");
					LogEnablement.isLogDebugEnabled(fwLogger, "Set Service Cube True 7.3 True");
				}
				// In case manager rejects the request, joiner needs to be turned off
				// This must come from Create Form
				// Prune Task Will Clean Identity
				if (servAccEnabled && primaryServiceAccountOwner != null) {
					stub.setAttribute("saccountOwnerone", primaryServiceAccountOwner);
				}
				if (managerFromRequest != null) {
					managerFromRequestObj = context.getObjectByName(Identity.class, managerFromRequest);
					if (managerFromRequestObj != null) {
						stub.setManager(managerFromRequestObj);
					}
				}
				if (emailFromRequest != null) {
					stub.setEmail(emailFromRequest);
				}
				calculateRequestType = "CREATE IDENTITY FEATURE";
				LogEnablement.isLogDebugEnabled(fwLogger, "calculateRequestType " + calculateRequestType);
			}
			// Edit Identity Attribute Set
			if (flow.equalsIgnoreCase("IdentityEditRequest")) {
				calculateRequestType = "EDIT IDENTITY FEATURE";
				LogEnablement.isLogDebugEnabled(fwLogger, "calculateRequestType " + calculateRequestType);
			}
			// Save Stub for Create and Register
			if (stub != null && flow.equalsIgnoreCase("IdentityCreateRequest")
					|| flow.equalsIgnoreCase("Registration") && identity == null) {
				context.saveObject(stub);
				context.commitTransaction();
			}
			if (managerFromRequestObj != null) {
				context.decache(managerFromRequestObj);
			}
			if (adminObj != null) {
				context.decache(adminObj);
			}
		}
		// End Create, Edit, and Register
		if (flow != null && (flow.equalsIgnoreCase("ForgotPassword") || flow.equalsIgnoreCase("ExpirePassword")
				|| flow.equalsIgnoreCase("PasswordsChangeRequest"))) {
			calculateRequestType = "CHANGE PASSWORD FEATURE";
		} else if (flow != null && flow.equalsIgnoreCase("Interceptor")) {
			calculateRequestType = "PASSWORD INTERCEPTOR FEATURE";
		} else if (flow != null && flow.equalsIgnoreCase("PasswordsRequest")) {
			calculateRequestType = "MANAGE PASSWORDS FEATURE";
		}
		if (flow != null && (flow.equalsIgnoreCase("Lifecycle") || flow.equalsIgnoreCase("Interceptor")
				|| flow.equalsIgnoreCase("PasswordsRequest") || flow.equalsIgnoreCase("ForgotPassword")
				|| flow.equalsIgnoreCase("ExpirePassword") || flow.equalsIgnoreCase("PasswordsChangeRequest"))) {
			workflow.put("autoVerifyIdentityRequest", "true");
			workflow.put("foregroundProvisioning", "true");
		}
		if (flow != null && identity != null) {
			if (flow.equalsIgnoreCase("AccessRequest")) {
				calculateRequestType = "CART REQUEST FEATURE";
			}
		}
		if (flow != null && identity != null) {
			if (flow.equalsIgnoreCase("AccountsRequest")) {
				calculateRequestType = "MANAGE ACCOUNTS FEATURE";
			}
		}
		// This is where we are also calculating first-time BATCH REQUEST
		if (calculateRequestType == null) {
			calculateRequestType = requestType;
		}
		if (flow == null) {
			workflow.put("flow", requestType);
		}
		LogEnablement.isLogDebugEnabled(fwLogger, "calculateRequestType " + calculateRequestType);
		workflow.put("requestType", calculateRequestType);
		LogEnablement.isLogDebugEnabled(fwLogger, "workflow requestType " + calculateRequestType);
		LogEnablement.isLogDebugEnabled(fwLogger, "accountRequestCreateOrRegister " + accountRequestCreateOrRegister);
		if (flow != null && (flow.equalsIgnoreCase("IdentityCreateRequest") || flow.equalsIgnoreCase("Registration"))
				&& joinerEnabled && accountRequestCreateOrRegister != null) {
			// Add Joiner Request
			AttributeRequest attributeRequestJoiner = new AttributeRequest();
			attributeRequestJoiner.setName(JoinerRuleLibrary.JOINERATTRNEEDSJOINER);
			attributeRequestJoiner.setOp(ProvisioningPlan.Operation.Set);
			attributeRequestJoiner.setValue(JoinerRuleLibrary.JOINERNEEDPROCESSING);
			accountRequestCreateOrRegister.add(attributeRequestJoiner);
			LogEnablement.isLogDebugEnabled(fwLogger, "Added Joiner Attribute Request To Account Request");
			// Add Activate Cube Request
			AttributeRequest attributeRequestInactive = new AttributeRequest(Identity.ATT_INACTIVE,
					ProvisioningPlan.Operation.Set, false);
			accountRequestCreateOrRegister.add(attributeRequestInactive);
			LogEnablement.isLogDebugEnabled(fwLogger, "Added Inactive Attribute Request To Account Request");
		}
		if (identity != null) {
			context.decache(identity);
		}
		return calculateRequestType;
	}
	/**
	 * Get Policy Scheme Based on Request Type, Source, and Launcher
	 *
	 * @param context
	 * @param flow
	 * @param requestType
	 * @param source
	 * @param launcher
	 * @param extPolicyScheme
	 * @return
	 * @throws GeneralException
	 */
	public static String getPolicyScheme(SailPointContext context, String flow, String requestType, String source,
			String launcher, String extPolicyScheme) throws GeneralException {
		LogEnablement.isLogDebugEnabled(fwLogger, "Enter getPolicyScheme");
		LogEnablement.isLogDebugEnabled(fwLogger, "Enter getPolicyScheme flow " + flow);
		LogEnablement.isLogDebugEnabled(fwLogger, "Enter getPolicyScheme requestType " + requestType);
		LogEnablement.isLogDebugEnabled(fwLogger, "Enter getPolicyScheme launcher " + launcher);
		LogEnablement.isLogDebugEnabled(fwLogger, "Enter getPolicyScheme extPolicyScheme " + extPolicyScheme);
		String result = "none";
		String policyScheme = "none";
		policyScheme = (String) ROADUtil.getGlobalDefinitionAttribute(context, "policyScheme");
		// We only want to run below logic if policy scheme is not none
		if (flow != null) {
			if (requestType != null && requestType.contains("JOINER")) {
				LogEnablement.isLogDebugEnabled(fwLogger, " Policy Scheme for JOINER or JOINER REHIRE" + flow);
				result = "none";
			} else if (launcher != null
					&& (launcher.equalsIgnoreCase("Scheduler") || launcher.equalsIgnoreCase("RequestHandler"))) {
				LogEnablement.isLogDebugEnabled(fwLogger, "No Policy Scheme for Lifecycle events" + flow);
				result = "none";
			} else if (requestType != null && requestType.equalsIgnoreCase("REQUEST MANAGER FEATURE")) {
				LogEnablement.isLogDebugEnabled(fwLogger, "No Policy Scheme for REQUEST MANAGER FEATURE " + flow);
				result = "none";
			} else if ((flow.equalsIgnoreCase("AccessRequest"))
					|| ((source != null) && source.equalsIgnoreCase("Batch"))) {
				result = policyScheme;
			} else if (flow.equalsIgnoreCase("AccountsRequest")) {
				result = policyScheme;
			} else if (flow.equalsIgnoreCase("External")) {
				if (extPolicyScheme != null) {
					result = extPolicyScheme;
				} else {
					result = policyScheme;
				}
			} else {
				LogEnablement.isLogDebugEnabled(fwLogger, "... NO Policy Violation - Policy Scheme = none");
				result = "none";
			}
		} else {
			result = "none";
		}
		LogEnablement.isLogDebugEnabled(fwLogger, "Exit getPolicyScheme " + result);
		return result;
	}
	/**
	 * Updates the Extended Attributes with the input attribute name and value!
	 *
	 * @param spExtAttrs
	 * @param String
	 *            attribute name
	 * @param Object
	 *            attribute value
	 * @return Attributes Updated Attributes
	 */
	public static Attributes updateExtAttrs(Attributes spExtAttrs, String attrName, Object attrValue) {
		if (attrName != null && !attrName.isEmpty() && attrValue != null && spExtAttrs != null) {
			spExtAttrs.put(attrName, attrValue);
		}
		return spExtAttrs;
	}
	/**
	 * Execute Post Wrapper Rule
	 * @param context
	 * @param identityName
	 * @param requestType
	 * @param project
	 * @throws GeneralException
	 */
	public static void postWrapperRule(SailPointContext context, String identityName, String requestType,
			ProvisioningProject project) throws GeneralException {
		LogEnablement.isLogDebugEnabled(fwLogger, "Start  postWrapperRule");
		LogEnablement.isLogDebugEnabled(fwLogger, "..identityName..." + identityName);
		LogEnablement.isLogDebugEnabled(fwLogger, "..requestType..." + requestType);
		LogEnablement.isLogDebugEnabled(fwLogger, "..project..." + project.toXml());
		Map map = new HashMap();
		// Common Configuration
		map = ROADUtil.getCustomGlobalMap(context);
		if (map != null && map.containsKey("postWrapperRule")) {
			String ruleName = (String) map.get("postWrapperRule");
			if (ruleName != null && ruleName.length() > 0) {
				ROADUtil.invokePostExtendedRuleNoObjectReferences(context, null, ruleName, null, requestType, null,
						null, null, identityName, null, project);
			}
		}
		LogEnablement.isLogDebugEnabled(fwLogger, "End  postWrapperRule");
	}
	/**
	 * Set External Attributes. These are used by Provisioning Policies
	 *
	 * @param context
	 * @param plan
	 * @param requestType
	 * @param workflow
	 * @param flow
	 * @param launcher
	 * @return
	 */
	public static Attributes setPlanAndExternalAttributes(SailPointContext context, ProvisioningPlan plan,
			String requestType, Workflow workflow, String flow, String launcher) {
		if (plan != null) {
			Attributes attrs = plan.getArguments();
			// Put Request Type and Flow in the plan attributes
			if (attrs != null) {
				if (requestType != null)
					attrs.put("requestType", requestType);
				if (flow != null)
					attrs.put("flow", flow);
				if (launcher != null)
					attrs.put("launcher", launcher);
				plan.setArguments(attrs);
			} else {
				Attributes newAttrs = new Attributes();
				if (requestType != null)
					newAttrs.put("requestType", requestType);
				if (flow != null)
					newAttrs.put("flow", flow);
				if (launcher != null)
					newAttrs.put("launcher", launcher);
				plan.setArguments(newAttrs);
				attrs = plan.getArguments();
			}
			workflow.put("plan", plan);
			workflow.put("spExtAttrs", attrs);
			return attrs;
		}
		return null;
	}
	/**
	 * Get Email Subject
	 * @param requestType
	 * @param type
	 * @return
	 */
	public static String getEmailSubjectSubmissionCompletion(String requestType, String type)
	{
		LogEnablement.isLogDebugEnabled(fwLogger,"Start getEmailSubjectSubmissionCompletion "+requestType+"..type.."+type);
		if(requestType!=null && requestType.contains("CREATE"))
		{
			LogEnablement.isLogDebugEnabled(fwLogger,"End getEmailSubjectSubmissionCompletion "+requestType+"..type.."+type);
			return "New User Request "+type;
		}
		else if(requestType!=null && requestType.contains("REGISTRATION"))
		{
			LogEnablement.isLogDebugEnabled(fwLogger,"End getEmailSubjectSubmissionCompletion "+requestType+"..type.."+type);
			return "New Registration Request "+type;
		}
		else if(requestType!=null && requestType.contains("PASSWORD"))
		{
			LogEnablement.isLogDebugEnabled(fwLogger,"End getEmailSubjectSubmissionCompletion "+requestType+"..type.."+type);
			return "Password Request "+type;
		}
		else if(requestType!=null && requestType.contains("MANAGE ACCOUNTS"))
		{
			LogEnablement.isLogDebugEnabled(fwLogger,"End getEmailSubjectSubmissionCompletion "+requestType+"..type.."+type);
			return "Account Request "+type;
		}
		else if(requestType!=null && requestType.contains("EDIT"))
		{
			LogEnablement.isLogDebugEnabled(fwLogger,"End getEmailSubjectSubmissionCompletion "+requestType+"..type.."+type);
			return "Edit User Request "+type;
		}
		else if(requestType!=null && requestType.contains("EXTERNAL"))
		{
			LogEnablement.isLogDebugEnabled(fwLogger,"End getEmailSubjectSubmissionCompletion "+requestType+"..type.."+type);
			return "User Request "+type;
		}
		else
		{
			LogEnablement.isLogDebugEnabled(fwLogger,"End getEmailSubjectSubmissionCompletion "+requestType+"..type.."+type);
			return "Access Request "+type;
		}
	}
}
