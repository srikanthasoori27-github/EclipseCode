/*
Copyright © 2018 SailPoint Technologies, Inc. All Rights Reserved.
All logos, text, content, and works of authorship, including but not limited to underlying code, programming or scripting language, designs, and/or graphics,
that are used and/or depicted herein are protected under United States and international copyright and trademark laws and treaties,
and may not be used or reproduced without the prior express written permission of SailPoint Technologies, Inc.
 */
package sailpoint.rapidapponboarding.rule;
import java.text.ParseException;
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
import sailpoint.object.Custom;
import sailpoint.object.Identity;
import sailpoint.object.Link;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.ProvisioningPlan.AccountRequest;
import sailpoint.object.ProvisioningPlan.AttributeRequest;
import sailpoint.object.Workflow;
import sailpoint.rapidapponboarding.logger.LogEnablement;
import sailpoint.server.Servicer;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
/**
 * Attribute Sync Life Cycle Event
 * @author rohit.gupta
 *
 */
public class AttributeSyncRuleLibrary {
	private static Log attrSyncLogger = LogFactory.getLog("rapidapponboarding.rules");
	private static Custom mappingObj = null;
	private static final String ATTRMAPPING="Custom-FrameworkAttributeSyncMapping";
	public static final String CREATEPOLICY = "CREATEPOLICY";
	public static final String EVALUATECREATEPOLICY = "evaluateCreateprovpolicy";
	public static final String PRIMARYSYNCACCOUNTS = "primarySyncaccounts";
	public static final String ATTRIBUTESYNCFEATURE = "ATTRIBUTE SYNCHRONIZATION FEATURE";
	public static final Object PARALLELREHIREATTRSYNC = "attributeSyncParallelRehire";
	public static final Object PARALLELMOVERATTRSYNC = "attributeSyncParallelMover";
	public static final Object SENDEMAILOPERATIONSATTRSYNCC = "apSendEmailToOperations";
	public static final String ATTRIBUTESYNCELIGIBLEERULE = "apAttributeSyncEligibleRule";
	/**
	 * Get Link Attribute Value / Native Id in a HashMap
	 * @param context
	 * @param identity
	 * @param schemaName
	 * @param appName
	 * @return
	 * @throws GeneralException
	 */
	public static HashMap<String,Object> getIdentityLinkValue(
			SailPointContext context, Identity identity,
			String schemaName, String appName)
					throws GeneralException {
		LogEnablement.isLogDebugEnabled(attrSyncLogger,"Enter getIdentityLinkValue");
		LogEnablement.isLogDebugEnabled(attrSyncLogger,"...appName = " + appName);
		boolean flag = false;
		IdentityService idService = new IdentityService(context);
		Application app = context.getObject(Application.class, appName);
		HashMap<String,Object> map = new HashMap();
		if (app != null)
		{
			List<Link> links = idService.getLinks(identity, app);
			if (links != null && links.size() > 0)
			{
				for (Link link : links)
				{
					Boolean primarySyncaccounts=(Boolean) app.getAttributeValue(AttributeSyncRuleLibrary.PRIMARYSYNCACCOUNTS);
					String nativeId=link.getNativeIdentity();
					if(primarySyncaccounts!=null && primarySyncaccounts.booleanValue()  && ROADUtil.isSecondaryAccount(link))
					{
						nativeId=null;
						LogEnablement.isLogDebugEnabled(attrSyncLogger,"...Secondary Link = " + nativeId);
					}
					if (link != null && nativeId != null)
					{
						Object valueFromLink = link.getAttribute(schemaName);
						if(link.getNativeIdentity()!=null)
						{
							map.put(nativeId, valueFromLink);
						}
					}
				}
			}
			context.decache(app);
		}
		LogEnablement.isLogDebugEnabled(attrSyncLogger,"Exit getIdentityLinkValue:[" + map + "]");
		return map;
	}
	/**
	 * Validates whether the attribute value changed based on the input
	 * Attribute name and Application. Compares the values between Identity
	 * Attribute value and Application's Attribute Value
	 * @param context
	 * @param newIdentity
	 * @param identityAttrName
	 * @param appAttrName
	 * @param attrApp
	 * @param nativeId
	 * @param valueFromLink
	 * @param identityValueorPPValue
	 * @param fromPolicy
	 * @retur if there is change in value false otherwise!
	 * @throws GeneralException
	 */
	public static boolean isAttributeValueChanged(SailPointContext context,
			Identity newIdentity, String identityAttrName, String attrName,
			String appName, String nativeId, Object valueFromLink,
			Object identityValueorPPValue,boolean fromPolicy) throws GeneralException
	{
		LogEnablement.isLogDebugEnabled(attrSyncLogger,"Enter isAttributeValueChanged");
		LogEnablement.isLogDebugEnabled(attrSyncLogger,"Enter isAttributeValueChanged identityValueorPPValue->"+identityValueorPPValue);
		LogEnablement.isLogDebugEnabled(attrSyncLogger,"Enter isAttributeValueChanged valueFromLink->"+valueFromLink);
		LogEnablement.isLogDebugEnabled(attrSyncLogger,"Enter identityAttrName=>"+identityAttrName);
		if(identityValueorPPValue instanceof String && identityValueorPPValue!=null)
		{
			identityValueorPPValue=((String)identityValueorPPValue).trim();
			LogEnablement.isLogDebugEnabled(attrSyncLogger,"identityValueorPPValue trim.."+identityValueorPPValue);
			LogEnablement.isLogDebugEnabled(attrSyncLogger,"identityValueorPPValue empty.."+((String)identityValueorPPValue).isEmpty());
		}
		if(valueFromLink instanceof String && valueFromLink!=null)
		{
			valueFromLink=((String)valueFromLink).trim();
			LogEnablement.isLogDebugEnabled(attrSyncLogger,"valueFromLink trim.."+valueFromLink);
			LogEnablement.isLogDebugEnabled(attrSyncLogger,"valueFromLink empty.."+((String)valueFromLink).isEmpty());
		}
		// Validate
		if (null == newIdentity || null == attrName || attrName.isEmpty()
				|| null == appName || appName.isEmpty() || null == nativeId
				|| nativeId.isEmpty()) {
			LogEnablement.isLogDebugEnabled(attrSyncLogger,"Input Identity is invalid or Identity attribute name/App Attribute Name/App Name is coming as null/empty. Returning false.");
			return false;
		}
		boolean flag = false;
		// Compare
		if(valueFromLink!=null && valueFromLink instanceof List && ((List) valueFromLink).size()>0
				&& identityValueorPPValue!=null && identityValueorPPValue instanceof List && ((List) identityValueorPPValue).size()>0)
		{
			LogEnablement.isLogDebugEnabled(attrSyncLogger,"Compare Previous and New List");
			List valueFromLinkList = ((List) valueFromLink);
			LogEnablement.isLogDebugEnabled(attrSyncLogger,"prevList.."+identityValueorPPValue);
			List identityValueorPPValueList = ((List) identityValueorPPValue);
			LogEnablement.isLogDebugEnabled(attrSyncLogger,"newList.."+identityValueorPPValueList);
			if(valueFromLinkList.size()!=identityValueorPPValueList.size() || !valueFromLinkList.containsAll(identityValueorPPValueList) || !identityValueorPPValueList.containsAll(valueFromLinkList))
			{
				LogEnablement.isLogDebugEnabled(attrSyncLogger,"isAttributeValueChanged List:[true]");
				return true;
			}
		}
		else if(valueFromLink!=null && valueFromLink instanceof List && ((List) valueFromLink).size()>0
				&&  (identityValueorPPValue == null|| (identityValueorPPValue instanceof List && ((List)identityValueorPPValue).size()<=0)))
		{
			LogEnablement.isLogDebugEnabled(attrSyncLogger,"isAttributeValueChanged List:[true]");
			return true;
		}
		else if(identityValueorPPValue!=null && identityValueorPPValue instanceof List && ((List) identityValueorPPValue).size()>0
				&&  (valueFromLink == null|| (valueFromLink instanceof List && ((List)valueFromLink).size()<=0)))
		{
			LogEnablement.isLogDebugEnabled(attrSyncLogger,"isAttributeValueChanged List:[true]");
			return true;
		}
		else if (valueFromLink instanceof Boolean && identityValueorPPValue instanceof Boolean
				&& (((Boolean)valueFromLink && !(Boolean)identityValueorPPValue)||(!(Boolean)valueFromLink && (Boolean)identityValueorPPValue))) {
			LogEnablement.isLogDebugEnabled(attrSyncLogger,"isAttributeValueChanged Boolean:[true]");
			return true;
		}
		else if (valueFromLink != null && valueFromLink instanceof String && identityValueorPPValue!=null && identityValueorPPValue instanceof String
				&& ((String)identityValueorPPValue).trim().length()>0 && ((String)valueFromLink).trim().length()>0
				&&!(((String) valueFromLink).equalsIgnoreCase((String)identityValueorPPValue))) {
			LogEnablement.isLogDebugEnabled(attrSyncLogger,"isAttributeValueChanged String:[true] "+valueFromLink);
			LogEnablement.isLogDebugEnabled(attrSyncLogger,"isAttributeValueChanged String:[true] "+identityValueorPPValue);
			return true;
		}
		else if (valueFromLink == null && identityValueorPPValue != null && identityValueorPPValue instanceof String && ((String)identityValueorPPValue).trim().length()>0) {
			LogEnablement.isLogDebugEnabled(attrSyncLogger,"isAttributeValueChanged:[true] Old Value Empty and New Value Not Empty");
			return true;
		}
		else if(valueFromLink != null && valueFromLink instanceof String && ((String)valueFromLink).trim().length()>0 && (identityValueorPPValue == null||
				(identityValueorPPValue instanceof String && ((String)identityValueorPPValue).toString().isEmpty())))
		{
			LogEnablement.isLogDebugEnabled(attrSyncLogger,"isAttributeValueChanged:[true] Old Value Not Empty and New Value Empty");
			return true;
		}
		LogEnablement.isLogDebugEnabled(attrSyncLogger,"isAttributeValueChanged:[" + flag + "]");
		return flag;
	}
	/**
	 * Custom Artifact for Mappings
	 * @param context
	 * @return
	 * @throws GeneralException
	 */
	synchronized static Custom getCustomAttributeSyncMapping(SailPointContext context)
			throws GeneralException {
		// Adding second check to avoid re-initialization when the multiple
		// threads enter into the above if condition and waiting for this to be
		// initialized
		if (null == mappingObj || null == mappingObj.getName()
				|| null == mappingObj.getAttributes()) {
			LogEnablement.isLogDebugEnabled(attrSyncLogger,"...Entering getCustomAttributeSyncMapping");
			mappingObj = context.getObjectByName(Custom.class,
					AttributeSyncRuleLibrary.ATTRMAPPING);
			LogEnablement.isLogDebugEnabled(attrSyncLogger,"...Exiting getCustomAttributeSyncMapping");
		} else {
		    Date dbModified = Servicer.getModificationDate(context, mappingObj);
		    if (Util.nullSafeCompareTo(dbModified, mappingObj.getModified()) > 0) {
		        LogEnablement.isLogDebugEnabled(attrSyncLogger,"...Returning updated AttributeSyncMapping object");
		        mappingObj = context.getObjectByName(Custom.class, AttributeSyncRuleLibrary.ATTRMAPPING);
		    } else {
		        LogEnablement.isLogDebugEnabled(attrSyncLogger,"...Returning previously initialized AttributeSyncMapping object");
		    }
		}
		return mappingObj;
	}
	/**
	 * Force Load Attribute Sync Settings
	 *
	 * @throws GeneralException
	 */
	public synchronized static void forceLoad(SailPointContext context)
			throws GeneralException {
		mappingObj = context.getObjectByName(Custom.class,
				AttributeSyncRuleLibrary.ATTRMAPPING);
	}
	/**
	 * Evaluates Attribute Sync Eligibility Criteria based on the Previous and
	 * New Identity attribute values
	 *
	 * @param context
	 *            SailPointContext
	 * @param previousIdentity
	 *            Identity Previous Identity
	 * @param newIdentity
	 *            New Identity
	 *  @param checkMultiple
	 *
	 * @return boolean true, if value changed false otherwise
	 * @throws Exception
	 */
	public static boolean isEligibleForAttributeSync(SailPointContext context,
			sailpoint.object.Identity previousIdentity,
			sailpoint.object.Identity newIdentity,boolean checkMultiple) throws Exception {
		String identityName=null;
		if(newIdentity!=null)
		{
			identityName=newIdentity.getName();
		}
		LogEnablement.isLogDebugEnabled(attrSyncLogger,"Enter isEligibleForAttributeSync.."+identityName);
		boolean flag = false;
		if (newIdentity == null) {
			LogEnablement.isLogDebugEnabled(attrSyncLogger,"...New identity object is null.."+identityName);
			return flag;
		}
		if (previousIdentity == null) {
			LogEnablement.isLogDebugEnabled(attrSyncLogger,"...Previous identity object is null.."+identityName);
			return flag;
		}
		Map map =ROADUtil.getCustomGlobalMap(context);
		String parallelRehireAttrSync="False";
		if(map!=null && map.containsKey(AttributeSyncRuleLibrary.PARALLELREHIREATTRSYNC) && map.get(AttributeSyncRuleLibrary.PARALLELREHIREATTRSYNC)!=null )
		{
			parallelRehireAttrSync=(String) map.get(AttributeSyncRuleLibrary.PARALLELREHIREATTRSYNC);
		}
		String parallelMoverAttrSync="False";
		if(map!=null && map.containsKey(AttributeSyncRuleLibrary.PARALLELMOVERATTRSYNC) && map.get(AttributeSyncRuleLibrary.PARALLELMOVERATTRSYNC)!=null )
		{
			parallelMoverAttrSync=(String) map.get(AttributeSyncRuleLibrary.PARALLELMOVERATTRSYNC);
		}
		// Identity is not human
		if (newIdentity != null
				&& newIdentity.getAttribute(WrapperRuleLibrary.SERVICE_CUBE_ATTR) != null
				&& ((String) newIdentity.getAttribute(WrapperRuleLibrary.SERVICE_CUBE_ATTR))
				.equalsIgnoreCase("TRUE")) {
			LogEnablement.isLogDebugEnabled(attrSyncLogger,"...Identity is not human..."+identityName);
			return flag;
		}
		// HR Event - This is a check for rehire
		if (checkMultiple && checkIsRehire(context, previousIdentity, newIdentity) && parallelRehireAttrSync.equalsIgnoreCase("False")) {
			LogEnablement.isLogDebugEnabled(attrSyncLogger,"..Joiner Rehire Skipping Attribute Sync.."+identityName);
			return flag;
		}
		// HR Event - This is a check for joiner
		if (checkMultiple && checkIsJoiner(context, previousIdentity, newIdentity)) {
			LogEnablement.isLogDebugEnabled(attrSyncLogger,"..Joiner Skipping Attribute Sync.."+identityName);
			return flag;
		}
		// HR Event - This is a check for leaver
		if (checkMultiple && checkIsLeaver(context, previousIdentity, newIdentity)) {
			LogEnablement.isLogDebugEnabled(attrSyncLogger,"..Leaver Skipping Attribute Sync.."+identityName);
			return flag;
		}
		// HR Event - This is a check for Mover
		if (checkMultiple && checkIsMover(context, previousIdentity, newIdentity) && parallelMoverAttrSync.equalsIgnoreCase("False")) {
			LogEnablement.isLogDebugEnabled(attrSyncLogger,"..Mover Skipping Attribute Sync.."+identityName);
			return flag;
		}
		IdentityService idService = new IdentityService(context);
		int countLinks = 0;
		countLinks = idService.countLinks(newIdentity);
		if (null == mappingObj)
		{
			mappingObj = getCustomAttributeSyncMapping(context);
		}
		else
		{
			LogEnablement.isLogDebugEnabled(attrSyncLogger,"Using Existing Mapping Object");
		}
		List<String> keyList = null;
		if (null != mappingObj && null != mappingObj.getAttributes()) {
			keyList = mappingObj.getAttributes().getKeys();
		}
		LogEnablement.isLogDebugEnabled(attrSyncLogger,"countLinks.."+countLinks);
		LogEnablement.isLogDebugEnabled(attrSyncLogger,"keyList.."+keyList);
		if (null != keyList && keyList.size() > 0 && countLinks >= 1)
		{
			for (String key : keyList)
			{
				String identityValue = "";
				// We are supporting comma separated key values
				// Very rare, will not expose this in documentation. However, it
				// was required by one customer
				List<String> keyAttributeList = Util.csvToList(key);
				LogEnablement.isLogDebugEnabled(attrSyncLogger,"key.."+key);
				LogEnablement.isLogDebugEnabled(attrSyncLogger,"keyAttributeList.."+keyAttributeList);
				if (keyAttributeList != null)
				{
					for (String singleAttribute : keyAttributeList)
					{
						LogEnablement.isLogDebugEnabled(attrSyncLogger,"singleAttribute.."+singleAttribute);
						List<String> appNameSchemaList = (List) mappingObj.getAttributes().get(singleAttribute);
						LogEnablement.isLogDebugEnabled(attrSyncLogger,"appNameSchemaList.."+appNameSchemaList);
						Object prevIdyAttr=null;
						Object newIdyAttr=null;
						String appName = null;
						String schemaName = null;
						if(appNameSchemaList!=null && appNameSchemaList.size()>0 && singleAttribute.equalsIgnoreCase(AttributeSyncRuleLibrary.CREATEPOLICY))
						{
							for (String appNameSchema : appNameSchemaList)
							{
								boolean comparisonResult=false;
								String[] appSchemaSplit = appNameSchema.split(":");
								if(appSchemaSplit!=null && appSchemaSplit.length == 2)
								{
									appName = appSchemaSplit[0];
									schemaName = appSchemaSplit[1];
									LogEnablement.isLogDebugEnabled(attrSyncLogger,"appName.."+appName);
									LogEnablement.isLogDebugEnabled(attrSyncLogger,"schemaName.."+schemaName);
								}
								if(appName!=null && schemaName!=null)
								{
									newIdyAttr=ROADUtil.getFieldValueFromProvisioningForms( context,  appName, newIdentity, schemaName,"Create",null);
									prevIdyAttr=ROADUtil.getFieldValueFromProvisioningForms( context,  appName, previousIdentity, schemaName,"Create",null);
									LogEnablement.isLogDebugEnabled(attrSyncLogger,"prevIdyAttr..pp.."+prevIdyAttr);
									LogEnablement.isLogDebugEnabled(attrSyncLogger,"newIdyAttr..pp.."+newIdyAttr);
									comparisonResult=comparePreviousNewAttributeSyncValues( prevIdyAttr,  newIdyAttr);
									LogEnablement.isLogDebugEnabled(attrSyncLogger,"comparisonResult...."+comparisonResult);
									if(comparisonResult)
									{
										LogEnablement.isLogDebugEnabled(attrSyncLogger,"Exit isEligibleForAttributeSync: true ..identityName.."+identityName);
										return true;
									}
								}
							}
						}
						else if(appNameSchemaList!=null && appNameSchemaList.size()>0 && singleAttribute.equalsIgnoreCase("RULE"))
						{
							//Execute Rule Here
							LogEnablement.isLogDebugEnabled(attrSyncLogger,"Execute App Rule..");
							for (String appNameSchema : appNameSchemaList)
							{
								String[] appSchemaSplit = appNameSchema.split(":");
								if(appSchemaSplit!=null && appSchemaSplit.length == 2)
								{
									appName = appSchemaSplit[0];
									schemaName = appSchemaSplit[1];
									LogEnablement.isLogDebugEnabled(attrSyncLogger,"appName.."+appName);
									LogEnablement.isLogDebugEnabled(attrSyncLogger,"schemaName.."+schemaName);
								}
								if(appName!=null && schemaName!=null)
								{
										Object result=ROADUtil.invokeExtendedRuleNoObjectReferences(context, null, AttributeSyncRuleLibrary.ATTRIBUTESYNCELIGIBLEERULE, appName, AttributeSyncRuleLibrary.ATTRIBUTESYNCFEATURE, null, null, null, identityName, null, true, null);
										LogEnablement.isLogDebugEnabled(attrSyncLogger,"result.."+result);
										if(result!=null && result instanceof Boolean && (Boolean)result)
										{
											//Use Case where there is a new cube for Rehire and there is no new and previous identity to compare
											//In this case,Previous Identity == New Identity
											LogEnablement.isLogDebugEnabled(attrSyncLogger,"Exit isEligibleForAttributeSync: true ..identityName.."+identityName);
											return true;
										}
										else
										{
											LogEnablement.isLogDebugEnabled(attrSyncLogger,"Rule Returned False.."+identityName);
										}
								}
							}
						}
						else
						{
							LogEnablement.isLogDebugEnabled(attrSyncLogger,"singleAttribute.."+singleAttribute);
							prevIdyAttr =  previousIdentity.getAttribute(singleAttribute);
							LogEnablement.isLogDebugEnabled(attrSyncLogger,"prevIdyAttr.."+prevIdyAttr);
							newIdyAttr =  newIdentity.getAttribute(singleAttribute);
							LogEnablement.isLogDebugEnabled(attrSyncLogger,"newIdyAttr.."+newIdyAttr);
							boolean result= comparePreviousNewAttributeSyncValues( prevIdyAttr,  newIdyAttr);
							LogEnablement.isLogDebugEnabled(attrSyncLogger,"Exit isEligibleForAttributeSync: " + result +"..identityName.."+identityName);
							if(result)
							{
								return result;
							}
						}
					}
				}
			}
		}
		LogEnablement.isLogDebugEnabled(attrSyncLogger,"Exit isEligibleForAttributeSync: " + flag +"..identityName.."+identityName);
		return flag;
	}
	/**
	 * Provides Attribute Sync Plan based on the input Identity and Attributes Changed based on the Custom Object Configuration
	 * @param context SailPointContext
	 * @param identityName Identity Name
	 * @param workflow Workflow
	 * @param appName
	 * @return ProvisioningPlan for Attribute Sync
	 * @throws Exception
	 */
	public static ProvisioningPlan buildAttributeSynPlan(SailPointContext context, String identityName, String appName, Workflow workflow) throws Exception {
		LogEnablement.isLogDebugEnabled(attrSyncLogger,"Enter buildAttributeSynPlan");
		Identity identity = context.getObjectByName(Identity.class, identityName);
		ProvisioningPlan plan = new ProvisioningPlan();
		IdentityService idService = new IdentityService(context);
		int countLinks=0;
		countLinks=idService.countLinks(identity);
		Custom mappingObj = context.getObjectByName(Custom.class, AttributeSyncRuleLibrary.ATTRMAPPING);
		Application app=null;
		Set<String> linksToCheckRename = new HashSet<>();
		if(mappingObj!=null)
		{
			LogEnablement.isLogDebugEnabled(attrSyncLogger,"mappingObj configured");
			if (countLinks>0)
			{
				AccountRequest.Operation acctOp = AccountRequest.Operation.Modify;
				List apps;
				if(mappingObj.getAttributes()!=null && mappingObj.getAttributes().getKeys()!=null)
				{
					LogEnablement.isLogDebugEnabled(attrSyncLogger,"mappingObj configured attributes and keys");
					List<String> keyList = mappingObj.getAttributes().getKeys();
					//Map to track the attribute change status flags against each attribute defined into Custom Attribute Sync Mappings
					Map attrChangeMap = new HashMap();
					for (String key : keyList)
					{
						LogEnablement.isLogDebugEnabled(attrSyncLogger,"...key = " + key);
						Object identityValue = "";
						if (identity.getAttribute(key) instanceof Boolean)
						{
							identityValue =identity.getAttribute(key);
							LogEnablement.isLogDebugEnabled(attrSyncLogger,"identityValue=>"+identityValue);
						}
						else if (identity.getAttribute(key) != null && identity.getAttribute(key) instanceof String)
						{
							identityValue = identity.getAttribute(key);
							LogEnablement.isLogDebugEnabled(attrSyncLogger,"identityValue=>"+identityValue);
						}
						else if (identity.getAttribute(key) != null && identity.getAttribute(key) instanceof List)
						{
							identityValue = identity.getAttribute(key);
							LogEnablement.isLogDebugEnabled(attrSyncLogger,"identityValue=>"+identityValue);
						}
						else
						{
							identityValue = "";
							LogEnablement.isLogDebugEnabled(attrSyncLogger,"identityValue=>"+identityValue);
						}
						if(mappingObj.getAttributes().get(key)!=null && mappingObj.getAttributes().get(key) instanceof List)
						{
							List<String> appList = (List) mappingObj.getAttributes().get(key);
							LogEnablement.isLogDebugEnabled(attrSyncLogger,"...appList = " + appList);
							for (String appAttr : appList)
							{
								LogEnablement.isLogDebugEnabled(attrSyncLogger,"...appAttr = " + appAttr);
								String[] appSplit = appAttr.split(":");
								String attrAppName = "";
								String attrSchemaAppAttrNameOrRuleName = "";
								boolean useRule = false;
								if (appSplit != null)
								{
									if (appSplit.length == 3)
									{
										if (appSplit[0].toUpperCase().equals("RULE"))
										{
											attrAppName = appSplit[1];
											attrSchemaAppAttrNameOrRuleName = appSplit[2];
											useRule = true;
										}
									}
									else if(appSplit.length == 2)
									{
										attrAppName = appSplit[0];
										attrSchemaAppAttrNameOrRuleName = appSplit[1];
										useRule = false;
									}
									if(attrAppName!=null && attrSchemaAppAttrNameOrRuleName!=null)
									{
										LogEnablement.isLogDebugEnabled(attrSyncLogger,"...attrAppName = " + attrAppName);
										LogEnablement.isLogDebugEnabled(attrSyncLogger,"...attrSchemaAppAttrNameOrRuleName = " + attrSchemaAppAttrNameOrRuleName);
										String nativeId=null;
										Object valueFromLink=null;
										app = context.getObjectByName(Application.class, attrAppName);
										List<Link> listLinks;
										if (app!=null && idService.countLinks(identity, app) >= 1 && (appName==null||appName.equalsIgnoreCase(app.getName())))
										{
											// Either build Attribute Synchronization for all Links or Use Primary Link
											// Primary Link will be identified based on Aggregation Regular Expression
											// Get Application Object
											// Get Primary Account Flag
											// If false,  build account request for all Links
											// If true, get regular expression from Application to identify secondary/privileged account, match regular expression, ignore secondary accounts
											// If expression not found, build account request for all Links
											Boolean primarySyncaccounts=(Boolean) app.getAttributeValue(AttributeSyncRuleLibrary.PRIMARYSYNCACCOUNTS);
											String privilegedAccountAttrExpression=(String) app.getAttributeValue(ObjectConfigAttributesRuleLibrary.PRIVILEGEDACCOUNTATTREXPRESSION);
											listLinks = idService.getLinks(identity, app);
											Boolean renameLDAPaccount=(Boolean) app.getAttributeValue("renameLDAPaccount");

											String psa=null;
											if(listLinks!=null)
											{
												for(Link link:listLinks)
												{
													nativeId = link.getNativeIdentity();
													valueFromLink=link.getAttribute(attrSchemaAppAttrNameOrRuleName);
													psa=((String)link.getAttribute("psAccount"));
													LogEnablement.isLogDebugEnabled(attrSyncLogger,"...nativeId = " + nativeId);
													LogEnablement.isLogDebugEnabled(attrSyncLogger,"...valueFromLink = " + valueFromLink);
													//Ignore secondary accounts if expression is defined and matched
													if(primarySyncaccounts!=null && primarySyncaccounts.booleanValue() && privilegedAccountAttrExpression!=null && ROADUtil.isSecondaryAccount(link))
													{
														nativeId=null;
													}
													if (!useRule)
													{
														//Lets see if Identity value is defined on create provisioning policy
														//Multiple Apps, What if one application has create policy ON and another application doesn't, need to clear identityValue
														String evaluateCreateprovpolicy=(String) app.getAttributeValue(AttributeSyncRuleLibrary.EVALUATECREATEPOLICY);
														if(evaluateCreateprovpolicy!=null && evaluateCreateprovpolicy.equalsIgnoreCase("TRUE") && key.equalsIgnoreCase(AttributeSyncRuleLibrary.CREATEPOLICY))
														{
															LogEnablement.isLogDebugEnabled(attrSyncLogger,"...evaluateCreateprovpolicy = " + evaluateCreateprovpolicy);
															Object newValuefromPP=ROADUtil.getFieldValueFromProvisioningForms( context,  app.getName(), identity, attrSchemaAppAttrNameOrRuleName,"Create",link);
															LogEnablement.isLogDebugEnabled(attrSyncLogger,"...newValuefromPP = " + newValuefromPP);
															if(newValuefromPP!=null)
															{
																//Compare target with source using native Id and Create Provisioning Policies
																if(nativeId!=null && nativeId.length()>0 && AttributeSyncRuleLibrary.isAttributeValueChanged(context, identity, key, attrSchemaAppAttrNameOrRuleName, attrAppName,nativeId,valueFromLink,newValuefromPP,true))
																{
																	AttributeSyncRuleLibrary.buildAttributeSyncPlan(plan,attrSchemaAppAttrNameOrRuleName,newValuefromPP,attrAppName,nativeId,valueFromLink);
																}
															}
														}
														//Compare target with source using native Id - Create Policy Evaluation is not ON
														if(nativeId!=null && nativeId.length()>0 && !key.equalsIgnoreCase(AttributeSyncRuleLibrary.CREATEPOLICY) && AttributeSyncRuleLibrary.isAttributeValueChanged(context, identity, key, attrSchemaAppAttrNameOrRuleName, attrAppName,nativeId,valueFromLink,identityValue,false))
														{
															AttributeSyncRuleLibrary.buildAttributeSyncPlan(plan,attrSchemaAppAttrNameOrRuleName,identityValue,attrAppName,nativeId,valueFromLink);
														}
													}
													else
													{
														LogEnablement.isLogDebugEnabled(attrSyncLogger,"...Use Attribute Sync Rule = ");
														String reqType = AttributeSyncRuleLibrary.ATTRIBUTESYNCFEATURE;
														if(nativeId!=null && nativeId.length()>0)
														{
															List acctReqs = (List) ROADUtil.invokeExtendedRuleNoObjectReferences(context,psa,attrSchemaAppAttrNameOrRuleName, app.getName(), reqType, null, "", "", identity.getName(), nativeId,false,null);
															if(null != plan && null != acctReqs && !acctReqs.isEmpty())
															{
																for(Iterator iterAcctReqs = acctReqs.iterator(); iterAcctReqs.hasNext();)
																{
																	plan.add((AccountRequest) iterAcctReqs.next());
																}
															}
														}
													}// End Use Rule
													//NativeId will be null if primary accounts is checked
													//Let's see if we need add Rename Attribute Request
													//This NativeId on the link will be based on Old DN
													if(nativeId!=null && renameLDAPaccount!=null && renameLDAPaccount.booleanValue())
													{
														linksToCheckRename.add(link.getId());
														//Add linkId to Set to be checked for rename at the end
													}
												}//Iterate through all links
											}//Synchronize All Accounts
										}//Identity has atleast one application account
									}// Application Name and Source Attribute is defined in custom artifact
								}// Colon is defined
							}//Iterate through Application  and Destination Attribute List
						}//Make sure it is a list item
					}//Iterate throug Identity source attribute list
				}//Custom Object is Initialized
				for(String linkId: linksToCheckRename) {
					Link link = idService.getLinkById(identity, linkId);
					if(link == null) {
						attrSyncLogger.warn("Something went wrong in Attribute Sync Plan Calculation, rename Link Id didn't find link");
						continue;
					}
					String psa = ((String)link.getAttribute("psAccount"));
					String nativeId = link.getNativeIdentity();
					String linkAppName = link.getApplicationName();
					String newDN = ROADUtil.getNativeIdentity(context, psa, linkAppName, identity, link);
					if(newDN!=null)
					{
						String newCN = WrapperRuleLibrary.getCNFromNativeId(newDN);
						//Add CN Change
						if(newCN!=null)
						{
							LogEnablement.isLogDebugEnabled(attrSyncLogger,"newCN..."+newCN);
							String oldCN = WrapperRuleLibrary.getCNFromNativeId(nativeId);
							if(oldCN!=null)
							{
								LogEnablement.isLogDebugEnabled(attrSyncLogger,"oldCN..."+oldCN);
								if(newCN!=null && oldCN!=null && newCN.length()>0 && oldCN.length()>0 && !newCN.equalsIgnoreCase(oldCN))
								{
									AttributeRequest renameAttrRequest= ROADUtil.renameCnFullName(newCN,linkAppName,nativeId,"IdentityIQ Modified the CN on");
									AccountRequest acctReq = new AccountRequest(acctOp, linkAppName, null, nativeId);
									if (acctReq != null && plan != null && renameAttrRequest!=null)
									{
										//Add CN Change
										acctReq.add(renameAttrRequest);
										plan.add(acctReq);
									}
								}
							}
						}

						//Add DN Change
						String strippedCNnewDNfromPP = WrapperRuleLibrary.getParentFromNativeId(newDN);
						LogEnablement.isLogDebugEnabled(attrSyncLogger,"newDN..."+newDN);
						LogEnablement.isLogDebugEnabled(attrSyncLogger,"nativeId..."+nativeId);
						if(strippedCNnewDNfromPP!=null  && !newDN.equalsIgnoreCase(nativeId))
						{
							AccountRequest acctReq = new AccountRequest(acctOp, linkAppName, null, nativeId);
							AttributeRequest moveAttrReq=WrapperRuleLibrary.moveOUAttrRequest( strippedCNnewDNfromPP,  linkAppName,  nativeId, "IdentityIQ Modified the DN on");
							if (acctReq != null && plan != null && moveAttrReq!=null)
							{
								acctReq.add(moveAttrReq);
								plan.add(acctReq);
							}
						}
					}
			}
				//Return Empty PLan
				if (plan != null)
				{
					if (plan.getAllRequests() == null)
					{
						plan = null;
					}
				}
			}// Source Identity has Accounts
			context.decache(mappingObj);
		}
		if(app!=null)
		{
			context.decache(app);
		}
		if(identity!=null)
		{
			context.decache(identity);
		}
		LogEnablement.isLogDebugEnabled(attrSyncLogger,"Exit buildAttributeSynPlan..."+identityName);
		return plan;
	}
	/**
	 * Build Attribute Sync Plan
	 * @param plan
	 * @param attrChange
	 * @param identityValue
	 * @param attrApp
	 * @param nativeId
	 * @param valueFromLink
	 */
	public static void buildAttributeSyncPlan(ProvisioningPlan plan,String attrChange,Object identityValue, String attrApp, String nativeId, Object valueFromLink)
	{
		LogEnablement.isLogDebugEnabled(attrSyncLogger,"Enter buildAttributeSyncPlan: valueFromLink->" + valueFromLink);
		LogEnablement.isLogDebugEnabled(attrSyncLogger,"Enter buildAttributeSyncPlan: identityValue->" + identityValue);
		LogEnablement.isLogDebugEnabled(attrSyncLogger,"Enter buildAttributeSyncPlan: nativeId->" + nativeId);
		if(plan!=null)
		{
			/**
			 * Handle Duplicate Attribute Requests
			 */
			boolean attributeNameRequestExists=false;
			List<AccountRequest> existingAccountRequests=plan.getAccountRequests();
			if(existingAccountRequests!=null && existingAccountRequests.size()>0)
			{
				for(AccountRequest existingAcctRequest:existingAccountRequests)
				{
					String nativeIdOnAcctRequest = existingAcctRequest.getNativeIdentity();
					String appName = existingAcctRequest.getApplication();
					if(Util.nullSafeCaseInsensitiveEq(nativeIdOnAcctRequest, nativeId) && Util.nullSafeCaseInsensitiveEq(appName, attrApp))
					{
						LogEnablement.isLogDebugEnabled(attrSyncLogger,"nativeIdOnAcctRequest->" + nativeIdOnAcctRequest);
						List existingAttrReq=existingAcctRequest.getAttributeRequests(attrChange);
						if(existingAttrReq!=null && existingAttrReq.size()>0)
						{
							attributeNameRequestExists=true;
							break;
						}
					}
				}
			}
			if(!attributeNameRequestExists)
			{
				AccountRequest.Operation acctOp = AccountRequest.Operation.Modify;
				AttributeRequest attrReq = new AttributeRequest();
				attrReq.setName(attrChange);
				if(valueFromLink!=null && valueFromLink instanceof String &&  (identityValue == null || (identityValue instanceof String && ((String)identityValue).trim()
						.isEmpty())) )
				{
					LogEnablement.isLogDebugEnabled(attrSyncLogger,"Clear out value from Link->" + valueFromLink);
					attrReq.setOperation(ProvisioningPlan.Operation.Remove);
					attrReq.setValue(valueFromLink);
				}
				else if(valueFromLink instanceof List && valueFromLink!=null  && ((List) valueFromLink).size()>0
						&& (identityValue==null|| (identityValue instanceof List && ((List)identityValue).size()<=0)))
				{
					LogEnablement.isLogDebugEnabled(attrSyncLogger,"Clear out value from Link->" + valueFromLink);
					attrReq.setOperation(ProvisioningPlan.Operation.Remove);
					attrReq.setValue(valueFromLink);
				}
				else
				{
					LogEnablement.isLogDebugEnabled(attrSyncLogger,"Set value for Link from Identity or PP->" + identityValue);
					attrReq.setOperation(ProvisioningPlan.Operation.Set);
					attrReq.setValue(identityValue);
				}
				AccountRequest acctReq = new AccountRequest(acctOp, attrApp, null, nativeId);
				if (acctReq != null && plan != null)
				{
					acctReq.add(attrReq);
					plan.add(acctReq);
				}
			}
		}
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
			Identity previousIdentity, Identity newIdentity) throws GeneralException, ParseException {
		boolean flag = false;
		flag = TriggersRuleLibrary
				.allowedForProcess(context,newIdentity, previousIdentity,
						LeaverRuleLibrary.LEAVERPROCESS, LeaverRuleLibrary.LEAVERFEATURE, "");
		return flag;
	}
	/**
	 * Check Is Joiner
	 * @param context
	 * @param previousIdentity
	 * @param newIdentity
	 * @return
	 * @throws Exception
	 */
	private static boolean checkIsJoiner(SailPointContext context,
			Identity previousIdentity, Identity newIdentity)
					throws Exception {
		boolean flag = false;
		flag = JoinerRuleLibrary.isEligibleForJoiner(context, previousIdentity,
				newIdentity);
		return flag;
	}
	/**
	 * Check is Rehire
	 * @param context
	 * @param previousIdentity
	 * @param newIdentity
	 * @return
	 * @throws Exception
	 */
	private static boolean checkIsRehire(SailPointContext context,
			Identity previousIdentity, Identity newIdentity)
					throws Exception {
		boolean flag = false;
		flag = JoinerRuleLibrary.isEligibleForRehire(context, previousIdentity,
				newIdentity);
		return flag;
	}
	/**
	 * Check is Mover
	 * @param context
	 * @param previousIdentity
	 * @param newIdentity
	 * @return
	 * @throws Exception
	 */
	private static boolean checkIsMover(SailPointContext context,
			Identity previousIdentity, Identity newIdentity)
					throws Exception {
		boolean flag = false;
		flag = MoverRuleLibrary.isEligibleForMover(context, previousIdentity,
				newIdentity);
		return flag;
	}
	/**
	 * Compare Previous and New Identity Attributes
	 * @param prevIdyAttr
	 * @param newIdyAttr
	 * @return
	 */
	private static boolean comparePreviousNewAttributeSyncValues(Object prevIdyAttr, Object newIdyAttr)
	{
		LogEnablement.isLogDebugEnabled(attrSyncLogger,"Start comparePreviousNewAttributeSyncValues...");
		if(prevIdyAttr instanceof String && prevIdyAttr!=null)
		{
			prevIdyAttr=((String)prevIdyAttr).trim();
			LogEnablement.isLogDebugEnabled(attrSyncLogger,"prevIdyAttr trim.."+prevIdyAttr);
		}
		if(newIdyAttr instanceof String && newIdyAttr!=null)
		{
			newIdyAttr=((String)newIdyAttr).trim();
			LogEnablement.isLogDebugEnabled(attrSyncLogger,"newIdyAttr trim.."+newIdyAttr);
		}
		if(prevIdyAttr instanceof Boolean && (Boolean)prevIdyAttr
				&& newIdyAttr instanceof Boolean && !(Boolean)newIdyAttr)
		{
			LogEnablement.isLogDebugEnabled(attrSyncLogger,"Attribute change found. Returning true. Previous Attribute True");
			LogEnablement.isLogDebugEnabled(attrSyncLogger,"End comparePreviousNewAttributeSyncValues...");
			return true;
		}
		if(prevIdyAttr instanceof List && ((List) prevIdyAttr).size()>0
				&& newIdyAttr instanceof List && ((List) newIdyAttr).size()>0)
		{
			LogEnablement.isLogDebugEnabled(attrSyncLogger,"Compare Previous and New List");
			List prevList = ((List) prevIdyAttr);
			LogEnablement.isLogDebugEnabled(attrSyncLogger,"prevList.."+prevList);
			List newList = ((List) newIdyAttr);
			LogEnablement.isLogDebugEnabled(attrSyncLogger,"newList.."+newList);
			if(prevList.size()!=newList.size() || !prevList.containsAll(newList) || !newList.containsAll(prevList))
			{
				LogEnablement.isLogDebugEnabled(attrSyncLogger,"Attribute change found. Mismatch of List..True");
				LogEnablement.isLogDebugEnabled(attrSyncLogger,"End comparePreviousNewAttributeSyncValues...");
				return true;
			}
		}
		if(prevIdyAttr!=null && prevIdyAttr instanceof List && ((List) prevIdyAttr).size()>0
				&&  (newIdyAttr == null|| (newIdyAttr instanceof List && ((List)newIdyAttr).size()<=0)))
		{
			LogEnablement.isLogDebugEnabled(attrSyncLogger,"Attribute change found. Mismatch of List..True");
			LogEnablement.isLogDebugEnabled(attrSyncLogger,"End comparePreviousNewAttributeSyncValues...");
			return true;
		}
		if(newIdyAttr!=null && newIdyAttr instanceof List && ((List) newIdyAttr).size()>0
				&&  (prevIdyAttr == null|| (prevIdyAttr instanceof List && ((List)prevIdyAttr).size()<=0)))
		{
			LogEnablement.isLogDebugEnabled(attrSyncLogger,"Attribute change found. Mismatch of List..True");
			LogEnablement.isLogDebugEnabled(attrSyncLogger,"End comparePreviousNewAttributeSyncValues...");
			return true;
		}
		if( prevIdyAttr instanceof Boolean && !(Boolean)prevIdyAttr
				&& newIdyAttr instanceof Boolean && (Boolean)newIdyAttr)
		{
			LogEnablement.isLogDebugEnabled(attrSyncLogger,"Attribute change found. Returning true. Previous Attribute False");
			return true;
		}
		if ((prevIdyAttr == null ||  (prevIdyAttr instanceof String && ((String)prevIdyAttr).trim().isEmpty()))
				&& newIdyAttr != null && newIdyAttr instanceof String
				&& !((String)newIdyAttr).trim().isEmpty()) {
			LogEnablement.isLogDebugEnabled(attrSyncLogger,"Attribute change found. Returning true. Previous Attribute Empty");
			LogEnablement.isLogDebugEnabled(attrSyncLogger,"End comparePreviousNewAttributeSyncValues...");
			return true;
		}
		if ((prevIdyAttr != null && prevIdyAttr instanceof String && !((String)prevIdyAttr).trim().isEmpty())
				&& (newIdyAttr == null || (newIdyAttr instanceof String && ((String)newIdyAttr).trim().isEmpty()))) {
			LogEnablement.isLogDebugEnabled(attrSyncLogger,"Clear Identity Fields Attribute change found. Returning true. New Attribute Empty");
			LogEnablement.isLogDebugEnabled(attrSyncLogger,"End comparePreviousNewAttributeSyncValues...");
			return true;
		}
		if (prevIdyAttr != null && prevIdyAttr instanceof String && !((String)prevIdyAttr).trim().isEmpty() && newIdyAttr != null
				&& newIdyAttr instanceof String && !((String)newIdyAttr).trim().isEmpty())
		{
			if (!(((String)newIdyAttr).equalsIgnoreCase((String)prevIdyAttr)))
			{
				LogEnablement.isLogDebugEnabled(attrSyncLogger,"Attribute change found. Returning true. Previous New No Match");
				LogEnablement.isLogDebugEnabled(attrSyncLogger,"End comparePreviousNewAttributeSyncValues...");
				return true;
			}
		}
		LogEnablement.isLogDebugEnabled(attrSyncLogger,"End comparePreviousNewAttributeSyncValues...false..");
		return false;
	}
}
