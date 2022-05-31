/*
Copyright © 2018 SailPoint Technologies, Inc. All Rights Reserved.
All logos, text, content, and works of authorship, including but not limited to underlying code, programming or scripting language, designs, and/or graphics,
that are used and/or depicted herein are protected under United States and international copyright and trademark laws and treaties,
and may not be used or reproduced without the prior express written permission of SailPoint Technologies, Inc.
 */
package sailpoint.rapidapponboarding.rule;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.IdentityService;
import sailpoint.api.SailPointContext;
import sailpoint.object.Custom;
import sailpoint.object.Identity;
import sailpoint.rapidapponboarding.logger.LogEnablement;
import sailpoint.server.Servicer;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
/**
 * Used for Triggers Definition
 * @author rohit.gupta
 *
 */
public class TriggersRuleLibrary {
	private static Log trigLogger = LogFactory.getLog("rapidapponboarding.rules");
	private static Custom customTriggers = null;
	/**
	 * Trigger Constants
	 *
	 */
	public static final String AUTHORITATIVE_SOURCE_TRIGGERS = "Authoritative Sources Triggers";
	private static final String OPERATION = "Operation";
	private static final String AND_OPERATION = "AND";
	private static final String OR_OPERATION = "OR";
	private static final String ATTRIBUTE = "Attribute";
	private static final String OLDVALUES = "OldValues";
	private static final String NEWVALUES = "NewValues";
	private static final String DATEFORMAT = "DateFormat";
	private static final String DATEFOPERATION = "DateOperation";
	private static final String OVERRIDEANDONNOCHANGE = "noChangeDetectedOverrideANDOperation";
	private static final String IGNORE = "IGNORE";
	private static final String SAME = "SAME";
	public static final String GROUPDEFINITION = "GROUPDEFINITION";
	public static final String POPULATION = "POPULATION";
	private static final String EMPTY = "EMPTY";
	private static final String WILDCHARSTAR = "*";
	private static final String DATE = "DATE";
	private static final String DATECLEARED = "DATE CLEARED";
	private static final String GREATEREQUAL = "GREATEREQUAL";
	private static final String LESSEQUAL = "LESSEQUAL";
	private static final String EQUAL = "EQUAL";
	public static final String TRIGGERS = "TRIGGERS";
	public static final String CUSTOMTRIGGERS = "Custom-Triggers";
	public static final String CUSTOMTRIGGERSFORM ="Custom-Triggers-User-Interface-Form";
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
	public static boolean allowedForProcess(SailPointContext context,
			Identity newIdentity, Identity previousIdentity, String process,
			String feature, String ignoreTriggerCheck) throws GeneralException,
	ParseException {
		String identityName=null;
		boolean skipCorrelationCheck=false;
		if(newIdentity!=null)
		{
			identityName=newIdentity.getName();
		}
		// Entry point for all of the Life cycle Events to see if the Identity
		// matches the pre-defined processes in the custom object
		LogEnablement.isLogDebugEnabled(trigLogger,"Enter TriggersRuleLibrary.allowedForProcess():" + process + ":"  + identityName);
		// newIdentity is null - Validation 1
		if (newIdentity == null) {
			LogEnablement.isLogDebugEnabled(trigLogger,"...No new identity. Return false..."+identityName);
		        LogEnablement.isLogDebugEnabled(trigLogger,"Exiting TriggersRuleLibrary.allowedForProcess()"); 
			return false;
		}
		// Previous Identity can be null - Validation 2
		if (!(process.equalsIgnoreCase(JoinerRuleLibrary.JOINERPROCESS))) {
			if (previousIdentity == null) {
				LogEnablement.isLogDebugEnabled(trigLogger,"...No previous identity. Return false...."+identityName);
		                LogEnablement.isLogDebugEnabled(trigLogger,"Exiting TriggersRuleLibrary.allowedForProcess()"); 
				return false;
			}
		}
		// Leaver accounts are not being provided from Authoritative Source, Skip correlation check
		if ((process.equalsIgnoreCase(LeaverRuleLibrary.LEAVERPROCESS)))
		{
			skipCorrelationCheck=true;
			LogEnablement.isLogDebugEnabled(trigLogger,"...Skip Correlation Check. for Leaver..."+identityName);
		}
		// Must be correlated
		if (newIdentity != null && !skipCorrelationCheck && false == newIdentity.isCorrelated()) {
			LogEnablement.isLogDebugEnabled(trigLogger,"...Identity is not correlated....Return false"+identityName);
		        LogEnablement.isLogDebugEnabled(trigLogger,"Exiting TriggersRuleLibrary.allowedForProcess()"); 
			return false;
		}
		// Must not be a service cube
		if (newIdentity != null
				&& newIdentity
				.getAttribute(WrapperRuleLibrary.SERVICE_CUBE_ATTR) != null && ((String) newIdentity
						.getAttribute(WrapperRuleLibrary.SERVICE_CUBE_ATTR))
				.equalsIgnoreCase("TRUE")) {
			LogEnablement.isLogDebugEnabled(trigLogger,"...Identity is not human....Return false"+identityName);
		        LogEnablement.isLogDebugEnabled(trigLogger,"Exiting TriggersRuleLibrary.allowedForProcess()"); 
			return false;
		}
		String identityTypeEnabled = ObjectConfigAttributesRuleLibrary.extendedAttrIdentityTypeEnabled(context);
		boolean identityTypeEnab=false;
		if(identityTypeEnabled!=null && identityTypeEnabled.length()>0 && identityTypeEnabled.equalsIgnoreCase("TRUE"))
		{
			identityTypeEnab=true;
		}
		if(identityTypeEnab)
		{
			LogEnablement.isLogDebugEnabled(trigLogger,"...identityTypeEnab..."+identityTypeEnab);
			// Must not be a service cube
			if (newIdentity != null && newIdentity.getType()!=null && newIdentity.getType().equalsIgnoreCase("service"))
			{
				LogEnablement.isLogDebugEnabled(trigLogger,"...Identity is not human....Return false"+identityName);
		                LogEnablement.isLogDebugEnabled(trigLogger,"Exiting TriggersRuleLibrary.allowedForProcess()"); 
				return false;
			}
		}
		IdentityService idService = new IdentityService(context);
		Map<String, String> outputMap = new HashMap();
		/**
		 * No operation defined, default is AND
		 */
		boolean isOr = false;
		// Each Map defined in the Custom Object will be evaluated as per
		// Feature
		LogEnablement.isLogDebugEnabled(trigLogger,"process..." + process);
		// Some features will not need to perform any checks on Identity data so
		// exit out of method
		if (ignoreTriggerCheck.equalsIgnoreCase("Y")) {
		        LogEnablement.isLogDebugEnabled(trigLogger,"TriggersRuleLibrary.allowedForProcess() ignoreTriggerCheck == Y"); 
		        LogEnablement.isLogDebugEnabled(trigLogger,"Exiting TriggersRuleLibrary.allowedForProcess() Return true"); 
			return true;
		}
		List<Map> triggerStatus = getCustomTriggers(context,TriggersRuleLibrary.AUTHORITATIVE_SOURCE_TRIGGERS, process);
		if (triggerStatus == null || triggerStatus.isEmpty())
		{
			// Lets reload, Process Trigger may have been added using Quick Link
			triggerStatus = getCustomTriggersReload(context,
					TriggersRuleLibrary.AUTHORITATIVE_SOURCE_TRIGGERS, process);
		}
		int countTriggers = 0;
		if (triggerStatus != null && triggerStatus.size() > 0)
		{
			// ITERATE THROUGH EACH MAP THERE CAN BE AN AND/OR OPERATIONs
			for (Map singleMap : triggerStatus)
			{
				LogEnablement.isLogDebugEnabled(trigLogger,"...singleMap=" + singleMap);
				countTriggers = countTriggers + 1;
				Map singleProcess = singleMap;
				// If OR operation found then set isOR flag
				if (singleProcess != null && singleProcess.get(TriggersRuleLibrary.OPERATION) != null) {
					// IF THE PROCESS MAP HAS AN OR OPERATOR (WHICH IS ALSO
					// DEFAULT) THEN ANY OF THE ATTRIBUTES CAN BE CHANGED
					if (singleProcess.get(TriggersRuleLibrary.OPERATION).toString().equalsIgnoreCase(TriggersRuleLibrary.OR_OPERATION)) {
						isOr = true;
						continue;
					}
					// IF THE PROCESS MAP HAS AN AND OPERATOR THEN ALL OF THE
					// ATTRIBUTES NEED TO BE CHANGED
					else if (singleProcess.get(TriggersRuleLibrary.OPERATION).toString().equalsIgnoreCase(TriggersRuleLibrary.AND_OPERATION)) {
						isOr = false;
					}
				}
				else
				{
					// Start here with Null Checks
					if (singleProcess != null && singleProcess.get(TriggersRuleLibrary.ATTRIBUTE) != null
							&& singleProcess.get(TriggersRuleLibrary.OLDVALUES) != null
							&& singleProcess.get(TriggersRuleLibrary.NEWVALUES) != null)
					{
						String attrName = (String) singleProcess.get(TriggersRuleLibrary.ATTRIBUTE);
						String oldValues = (String) singleProcess.get(TriggersRuleLibrary.OLDVALUES);
						String newValues = (String) singleProcess.get(TriggersRuleLibrary.NEWVALUES);
						String dateFormat = (String) singleProcess.get(TriggersRuleLibrary.DATEFORMAT);
						String dateOperation = (String) singleProcess.get(TriggersRuleLibrary.DATEFOPERATION);
						// Re-Initialize for every Map process
						String noChangeDetectedOverrideAND = "";
						if (singleProcess.get(TriggersRuleLibrary.OVERRIDEANDONNOCHANGE) != null)
						{
							if (!(isOr))
							{
								noChangeDetectedOverrideAND = (String) singleProcess.get(TriggersRuleLibrary.OVERRIDEANDONNOCHANGE);
							}
						}
						// START OLD AND NEW ANY VALUE
						if (oldValues.equals(TriggersRuleLibrary.WILDCHARSTAR) && newValues.equals(TriggersRuleLibrary.WILDCHARSTAR))
						{
							wildCharacterAnyValue( newIdentity, previousIdentity, outputMap, attrName, countTriggers, noChangeDetectedOverrideAND);
						}
						// END OLD AND NEW ANY VALUE
						// START OLD IGNORE NEW POPULATION MATCH NEGATE
						else if (oldValues.equalsIgnoreCase(TriggersRuleLibrary.IGNORE) &&
								(attrName.equalsIgnoreCase(TriggersRuleLibrary.GROUPDEFINITION)||attrName.equalsIgnoreCase(TriggersRuleLibrary.POPULATION)) && newIdentity != null)
						{
							oldIgnoreNewPopulation(context, newIdentity, previousIdentity, oldValues, newValues, outputMap, attrName, countTriggers, noChangeDetectedOverrideAND);
						}
						// END OLD IGNORE AND NEW POPULATION MATCH NEGATE
						// START NEW ANY OLD VALUE POPULATION MATCH NEGATE
						else if (oldValues!=null && newValues!=null &&
								!oldValues.equalsIgnoreCase(TriggersRuleLibrary.IGNORE) &&
								(attrName.equalsIgnoreCase(TriggersRuleLibrary.GROUPDEFINITION)||attrName.equalsIgnoreCase(TriggersRuleLibrary.POPULATION))
								&& newIdentity != null)
						{
							populationMatchOldNew( context, newIdentity, previousIdentity, oldValues, newValues, outputMap, attrName, countTriggers, noChangeDetectedOverrideAND);
						}
						// END NEW ANY OLD VALUE POPULATION MATCH NEGATE
						// START NEW ANY VALUE AND OLD IS EMPTY
						else if (oldValues.equalsIgnoreCase(TriggersRuleLibrary.EMPTY)&& newValues.equalsIgnoreCase(TriggersRuleLibrary.WILDCHARSTAR))
						{
							oldEmptyAndNewWildCard( newIdentity, previousIdentity, outputMap, attrName, countTriggers, noChangeDetectedOverrideAND);
						}
						// END NEW ANY VALUE AND OLD IS EMPTY
						// START OLD ANY VALUE AND NEW IS EMPTY
						else if (oldValues.equalsIgnoreCase(TriggersRuleLibrary.WILDCHARSTAR) && newValues.equalsIgnoreCase(TriggersRuleLibrary.EMPTY))
						{
							newEmptyAndOldWildCard( newIdentity, previousIdentity, outputMap, attrName, countTriggers, noChangeDetectedOverrideAND);
						}
						// END OLD ANY VALUE AND NEW IS EMPTY
						// START OLD ANY VALUE AND NEW COMMA SEPARATED OR SINGLE NEW VALUE
						else if (oldValues.equals(TriggersRuleLibrary.WILDCHARSTAR) && newIdentity.getAttribute(attrName) != null
								&& newIdentity.getAttribute(attrName).toString() != null && newIdentity.getAttribute(attrName).toString().length() > 0
								&& previousIdentity.getStringAttribute(attrName) != null
								&& previousIdentity.getAttribute(attrName).toString() != null
								&& previousIdentity.getAttribute(attrName).toString().length() > 0)
						{
							oldAnyAndNewSingleOrCommaSeparated(newIdentity, previousIdentity, newValues, outputMap, attrName, countTriggers, noChangeDetectedOverrideAND);
						}
						// END OLD ANY VALUE AND NEW COMMA SEPARATED OR SINGLE VALUE
						// START NEW ANY VALUE AND OLD COMMA SEPARATED OR SINGLE VALUE
						else if (newValues.equals(TriggersRuleLibrary.WILDCHARSTAR)
								&& newIdentity.getAttribute(attrName) != null
								&& newIdentity.getAttribute(attrName).toString() != null
								&& newIdentity.getAttribute(attrName).toString().length() > 0
								&& previousIdentity.getStringAttribute(attrName) != null
								&& previousIdentity.getAttribute(attrName).toString() != null
								&& previousIdentity.getAttribute(attrName).toString().length() > 0)
						{
							newAnyAndOldSingleOrCommaSeparated( newIdentity, previousIdentity, oldValues, outputMap, attrName, countTriggers, noChangeDetectedOverrideAND);
						}
						// END NEW ANY VALUE AND OLD COMMA SEPARATED OR SINGLE VALUE
						// START OLD SAME or IGNORE FOR JOINER AND NEW COMMA SEPARATED OR SINGLE
						// SAME - Backward Compatibility not shown on UI
						else if ((oldValues.equalsIgnoreCase(TriggersRuleLibrary.SAME) || oldValues.equalsIgnoreCase(TriggersRuleLibrary.IGNORE))
								&& (!attrName.equalsIgnoreCase(TriggersRuleLibrary.GROUPDEFINITION) &&!attrName.equalsIgnoreCase(TriggersRuleLibrary.POPULATION))
								&& newIdentity.getAttribute(attrName) != null
								&& newIdentity.getAttribute(attrName).toString() != null
								&& newIdentity.getAttribute(attrName).toString().length() > 0)
						{
							oldSameAndNewSingleOrCommaSeparated(newIdentity, previousIdentity, newValues, outputMap, attrName, countTriggers, noChangeDetectedOverrideAND);
						}
						// END OLD AND NEW VALUES SAME FOR JOINER
						// START DATE
						else if (oldValues.equalsIgnoreCase(TriggersRuleLibrary.DATE) && newValues.equalsIgnoreCase(TriggersRuleLibrary.DATE))
						{
							dateOldNewCompare( context, newIdentity, previousIdentity, outputMap, attrName, countTriggers, noChangeDetectedOverrideAND,dateFormat,dateOperation);
						}
						// END DATE
						// START DATE CLEARED
						// 3. Clear New Date (Reverse of Date Assignment)
						else if (oldValues.equalsIgnoreCase(TriggersRuleLibrary.IGNORE) && newValues.equalsIgnoreCase(TriggersRuleLibrary.DATECLEARED) && newIdentity.getAttribute(attrName) == null
								&& previousIdentity.getAttribute(attrName) != null )
						{
							oldIgnoredNewDateCleared( context, newIdentity, previousIdentity, outputMap, attrName, countTriggers, noChangeDetectedOverrideAND, dateFormat, dateOperation);
						}
						// END DATE CLEARED
						// START SINGLE VALUES
						else if(!newValues.contains(",") && !oldValues.contains(",") && newIdentity.getAttribute(attrName) != null && previousIdentity.getAttribute(attrName) != null)
						{
							//Implicit Joiner - NEEDS Processing is processed here
							singleValuesComparison( newIdentity, previousIdentity, newValues, oldValues, outputMap,attrName,countTriggers,noChangeDetectedOverrideAND);
						}
						// END SINGLE VALUES
						// START COMMA SEPARATED OLD AND NEW VALUES
						else
						{
							commaSeperatedValuesComparison( newIdentity, previousIdentity,newValues,oldValues,outputMap,attrName,countTriggers,noChangeDetectedOverrideAND);
						} // END COMMA SEPARATED OLD AND NEW VALUES
					}
				}
			}
		}
		// When all the Output maps from the above are evaluated then check to
		// see if isOR condition is set otherwise assume everything has to be
		// set to true
		int passedTriggerCondition = 0;
		if (outputMap != null && outputMap.size() > 0)
		{
			LogEnablement.isLogDebugEnabled(trigLogger,"...OutputMap Return = " + outputMap);
			LogEnablement.isLogDebugEnabled(trigLogger,"...Total Triggers = " + countTriggers);
			LogEnablement.isLogDebugEnabled(trigLogger,"...OR Condition = " + isOr);
			// If Operation Not Defined - Default is And Condition
			for (String key : outputMap.keySet())
			{
				String value = outputMap.get(key);
				LogEnablement.isLogDebugEnabled(trigLogger,"...value = " + value);
				if (value != null && value.equalsIgnoreCase("true"))
				{
					passedTriggerCondition += 1;
				}
			}
			if (isOr && passedTriggerCondition <= outputMap.size()
					&& passedTriggerCondition != 0)
			{
				LogEnablement.isLogDebugEnabled(trigLogger,"KICK OFF LCE Process..."+process);
				LogEnablement.isLogDebugEnabled(trigLogger,"Exit allowedForProcess() = TRUE OR CONDITION..."+identityName);
				return true;
			}
			else if (passedTriggerCondition == outputMap.size())
			{
				LogEnablement.isLogDebugEnabled(trigLogger,"KICK OFF LCE Process..."+process);
				LogEnablement.isLogDebugEnabled(trigLogger,"Exit allowedForProcess = TRUE AND CONDITION..."+identityName);
				return true;
			}
			else
			{
				LogEnablement.isLogDebugEnabled(trigLogger,"CONDITIONS FAILED FOR LCE Process..."+process);
				LogEnablement.isLogDebugEnabled(trigLogger,"Exit allowedForProcess = FALSE AND CONDITION..."+identityName);
				return false;
			}
		}
		else
		{
			LogEnablement.isLogDebugEnabled(trigLogger,"CONDITIONS FAILED FOR LCE Process..."+process);
			LogEnablement.isLogDebugEnabled(trigLogger,"OUTPUTMAP EMPTY   FOR LCE Process..."+process);
			LogEnablement.isLogDebugEnabled(trigLogger,"Exit allowedForProcess = FALSE...."+identityName);
			return false;
		}
	}
	/**
	 * Get Custom Trigger Settings
	 *
	 * @return
	 * @throws GeneralException
	 */
	static synchronized Custom getCustomTriggers(SailPointContext context)throws GeneralException
	{
		LogEnablement.isLogDebugEnabled(trigLogger,"...Entering getCustomTriggers");
		// Adding second check to avoid re-initialization when the multiple
		// threads enter into the above if condition and waiting for this to be
		// initialized
		if (null == customTriggers|| null == customTriggers.getAttributes()||
				null == customTriggers.getAttributes().get(TriggersRuleLibrary.TRIGGERS)||
				!TriggersRuleLibrary.CUSTOMTRIGGERS.equalsIgnoreCase(customTriggers.getName()))
		{
			LogEnablement.isLogDebugEnabled(trigLogger,"...Entering getCustomTriggers");
			customTriggers = context.getObjectByName(Custom.class,TriggersRuleLibrary.CUSTOMTRIGGERS);
			LogEnablement.isLogDebugEnabled(trigLogger,"...Exiting getCustomTriggers");
		} else {
            Date dbModified = Servicer.getModificationDate(context, customTriggers);
            if (Util.nullSafeCompareTo(dbModified, customTriggers.getModified()) > 0) {
                LogEnablement.isLogDebugEnabled(trigLogger,"...Returning updated customTriggers object");
                customTriggers = context.getObjectByName(Custom.class, TriggersRuleLibrary.CUSTOMTRIGGERS);
            } else {
                LogEnablement.isLogDebugEnabled(trigLogger,"...Returning previously initialized customTriggers object");
            }
        }
		LogEnablement.isLogDebugEnabled(trigLogger,"...End getCustomTriggers");
		return customTriggers;
	}
	/**
	 * Reload Custom Trigger Settings This is added in case someone adds new
	 * triggers via quick link
	 *
	 * @return
	 * @throws GeneralException
	 */
	static synchronized Custom getCustomTriggersReload(SailPointContext context)throws GeneralException
	{
		LogEnablement.isLogDebugEnabled(trigLogger,"...Entering getCustomTriggersReload");
		// Adding second check to avoid re-initialization when the multiple
		// threads enter into the above if
		// condition and waiting for this to be initialized
		LogEnablement.isLogDebugEnabled(trigLogger,"...Entering getCustomTriggers reload");
		customTriggers = context.getObjectByName(Custom.class,TriggersRuleLibrary.CUSTOMTRIGGERS);
		LogEnablement.isLogDebugEnabled(trigLogger,"...Exiting getCustomTriggers reload");
		return customTriggers;
	}
	/**
	 * Reload Custom Triggers Object This is added in case someone adds new
	 * triggers via quick link
	 *
	 * @param parentKey
	 * @param childKey
	 * @return
	 * @throws GeneralException
	 */
	public static List getCustomTriggersReload(SailPointContext context,
			String parentKey, String childKey) throws GeneralException {
		LogEnablement.isLogDebugEnabled(trigLogger,"Enter TriggersRuleLibrary::getCustomTriggersReload");
		List returnVal = null;
		if (null == parentKey || parentKey.isEmpty()) {
			LogEnablement.isLogErrorEnabled(trigLogger,"Input LCM Event is invalid!");
			return returnVal;
		}
		// Get the Custom Object Reloaded
		customTriggers = getCustomTriggersReload(context);
		if (customTriggers == null || customTriggers.getAttributes() == null) {
			return returnVal;
		}
		// Navigate to the TRIGGERS Key in the Map
		Map JMLMap = (Map) customTriggers.getAttributes().get(
				TriggersRuleLibrary.TRIGGERS);
		// Get the Parent Key
		Map entryMap = null;
		// TODO add null check after testing
		if (JMLMap.containsKey(parentKey)) {
			entryMap = (Map) JMLMap.get(parentKey);
		}
		if (entryMap != null) {
			if (entryMap.containsKey(childKey)) {
				LogEnablement.isLogDebugEnabled(trigLogger,"...entryMap = " + entryMap.get(childKey));
				returnVal = (List) entryMap.get(childKey);
			}
		}
		LogEnablement.isLogDebugEnabled(trigLogger,"Exit getCustomTriggersReload");
		return returnVal;
	}
	/**
	 * Load Custom Triggers Artifact This is used for all Lifecycle Events and
	 * Bean shell Joiner Rule Library to see if "needsJoiner" is enabled or not
	 *
	 * @param parentKey
	 * @param childKey
	 * @return
	 * @throws GeneralException
	 */
	public static List getCustomTriggers(SailPointContext context,String parentKey, String childKey) throws GeneralException
	{
		LogEnablement.isLogDebugEnabled(trigLogger,"Enter TriggersRuleLibrary::getCustomTriggers");
		List returnVal = null;
		if (null == parentKey || parentKey.isEmpty())
		{
			LogEnablement.isLogErrorEnabled(trigLogger,"Input LCM Event is invalid!");
			return returnVal;
		}
		
		customTriggers = getCustomTriggers(context);
		if (customTriggers == null)
		{
		    LogEnablement.isLogDebugEnabled(trigLogger,"Custom Object is null");
			return returnVal;
		}

		// Navigate to the TRIGGERS Key in the Map
		Map JMLMap = (Map) customTriggers.getAttributes().get(TriggersRuleLibrary.TRIGGERS);
		// Get the Parent Key
		Map entryMap = null;
		// TODO add null check after testing
		if (JMLMap.containsKey(parentKey))
		{
			entryMap = (Map) JMLMap.get(parentKey);
		}
		if (entryMap != null)
		{
			if (entryMap.containsKey(childKey))
			{
				LogEnablement.isLogDebugEnabled(trigLogger,"...entryMap = " + entryMap.get(childKey));
				returnVal = (List) entryMap.get(childKey);
			}
		}
		LogEnablement.isLogDebugEnabled(trigLogger,"Exit getCustomTriggers");
		return returnVal;
	}
	/**
	 * Force Load Triggers
	 *
	 * @return custom
	 * @throws GeneralException
	 */
	public static void forceLoadTriggers(SailPointContext context)throws GeneralException
	{
		customTriggers = context.getObjectByName(Custom.class,TriggersRuleLibrary.CUSTOMTRIGGERS);
	}
	/**
	 * Mostly Used for Date
	 *
	 * @param dateString
	 * @param dateFormat
	 * @return
	 */
	public static boolean isDateGreaterLessThenEqualToToday(
			SailPointContext context, String dateString, String dateFormat,
			String dateOperation) {
		String[] formatStrings = new String[1];
		if (dateFormat != null && dateFormat.length() > 0) {
			formatStrings[0] = dateFormat;
		} else {
			LogEnablement.isLogDebugEnabled(trigLogger,"Exit isDateLessToday NO DATE FORMAT= false");
			return false;
		}
		// DEFAULT DATE OPERATION
		if (dateOperation == null || dateOperation.length() <= 0) {
			dateOperation = TriggersRuleLibrary.GREATEREQUAL;
		}
		LogEnablement.isLogDebugEnabled(trigLogger,"Enter TriggersRuleLibrary::isDateGreaterLessThenEqualToToday");
		if (!(dateString.equals(""))) {
			for (String formatString : formatStrings) {
				try {
					LogEnablement.isLogDebugEnabled(trigLogger,"...dateString = " + dateString);
					LogEnablement.isLogDebugEnabled(trigLogger,"...formatString = " + formatString);
					SimpleDateFormat sdf = new SimpleDateFormat(formatString);
					Date today = Calendar.getInstance().getTime();
					String todayDate = sdf.format(today);
					Date currentDate = sdf.parse(todayDate);
					Date anticipatedDate = new SimpleDateFormat(formatString).parse(dateString);
					int days = 0;
					LogEnablement.isLogDebugEnabled(trigLogger,"...anticipatedDate = " + anticipatedDate);
					long timeDiffInMs = anticipatedDate.getTime()- currentDate.getTime();
					days = (int) (timeDiffInMs / (1000 * 60 * 60 * 24));
					LogEnablement.isLogDebugEnabled(trigLogger,"...days = " + days);
					if (days < 0)
					{
						if (dateOperation.equalsIgnoreCase(TriggersRuleLibrary.GREATEREQUAL)) {
							LogEnablement.isLogDebugEnabled(trigLogger,"Exit isDateLessToday LESS THAN TODAY= false");
							return false;
						} else if (dateOperation
								.equalsIgnoreCase(TriggersRuleLibrary.EQUAL)) {
							LogEnablement.isLogDebugEnabled(trigLogger,"Exit isDateLessToday LESS THAN TODAY= false");
							return false;
						} else if (dateOperation
								.equalsIgnoreCase(TriggersRuleLibrary.LESSEQUAL)) {
							LogEnablement.isLogDebugEnabled(trigLogger,"Exit isDateLessToday LESS THAN TODAY= true");
							return true;
						}
					}
					else if (days == 0)
					{
						LogEnablement.isLogDebugEnabled(trigLogger,"Exit isDateEqualToday EQUAL TODAY= true");
						return true;
					}
					else
					{
						if (dateOperation
								.equalsIgnoreCase(TriggersRuleLibrary.GREATEREQUAL)) {
							LogEnablement.isLogDebugEnabled(trigLogger,"Exit isDateGreaterToday GREATER THAN TODAY= true");
							return true;
						} else if (dateOperation
								.equalsIgnoreCase(TriggersRuleLibrary.EQUAL)) {
							LogEnablement.isLogDebugEnabled(trigLogger,"Exit isDateGreaterToday GREATER THAN TODAY= false");
							return false;
						} else if (dateOperation
								.equalsIgnoreCase(TriggersRuleLibrary.LESSEQUAL)) {
							LogEnablement.isLogDebugEnabled(trigLogger,"Exit isDateGreaterToday GREATER THAN TODAY= false");
							return false;
						}
					}
				} catch (ParseException e) {
					LogEnablement.isLogDebugEnabled(trigLogger,"Date Format Exception = " + e.getMessage());
				}
			}
		}
		LogEnablement.isLogDebugEnabled(trigLogger,"Exit isDateGreaterLessThenEqualToToday");
		return false;
	}
	/**
	 * Mostly Used for Date Logic
	 *
	 * @param dateString
	 * @param dateFormat
	 * @return
	 */
	public static boolean isDateGreaterLessThenEqualToToday(
			SailPointContext context, Date newPrevDateObj,Date now,
			String dateOperation)
	{
		// DEFAULT DATE OPERATION
		if (dateOperation == null || dateOperation.length() <= 0)
		{
			dateOperation = TriggersRuleLibrary.GREATEREQUAL;
		}
		if (newPrevDateObj!=null && now!=null)
		{
			LogEnablement.isLogDebugEnabled(trigLogger,"Enter TriggersRuleLibrary::isDateGreaterLessThenEqualToToday");
			LogEnablement.isLogDebugEnabled(trigLogger,"Date now.."+now);
			LogEnablement.isLogDebugEnabled(trigLogger,"Date newPrevDateObj.."+newPrevDateObj);
			long timeDiffInMs = newPrevDateObj.getTime() - now.getTime();
			int days = (int) (timeDiffInMs / (1000 * 60 * 60 * 24));
			int dayTimeUnit=(int) TimeUnit.DAYS.convert(timeDiffInMs, TimeUnit.MILLISECONDS);
			LogEnablement.isLogDebugEnabled(trigLogger,"days.."+days);
			LogEnablement.isLogDebugEnabled(trigLogger,"dayTimeUnit.."+dayTimeUnit);
			if (days < 0)
			{
				if (dateOperation.equalsIgnoreCase(TriggersRuleLibrary.GREATEREQUAL))
				{
					LogEnablement.isLogDebugEnabled(trigLogger,"Exit isDateLessToday LESS THAN TODAY= false");
					return false;
				} else if (dateOperation
						.equalsIgnoreCase(TriggersRuleLibrary.EQUAL)) {
					LogEnablement.isLogDebugEnabled(trigLogger,"Exit isDateLessToday LESS THAN TODAY= false");
					return false;
				} else if (dateOperation
						.equalsIgnoreCase(TriggersRuleLibrary.LESSEQUAL)) {
					LogEnablement.isLogDebugEnabled(trigLogger,"Exit isDateLessToday LESS THAN TODAY= true");
					return true;
				}
			}
			else if (days==0)
			{
				LogEnablement.isLogDebugEnabled(trigLogger,"Exit isDateEqualToday EQUAL TODAY= true");
				return true;
			}
			else
			{
				if (dateOperation.equalsIgnoreCase(TriggersRuleLibrary.GREATEREQUAL))
				{
					LogEnablement.isLogDebugEnabled(trigLogger,"Exit isDateGreaterToday GREATER THAN TODAY= true");
					return true;
				} else if (dateOperation
						.equalsIgnoreCase(TriggersRuleLibrary.EQUAL)) {
					LogEnablement.isLogDebugEnabled(trigLogger,"Exit isDateGreaterToday GREATER THAN TODAY= false");
					return false;
				} else if (dateOperation
						.equalsIgnoreCase(TriggersRuleLibrary.LESSEQUAL)) {
					LogEnablement.isLogDebugEnabled(trigLogger,"Exit isDateGreaterToday GREATER THAN TODAY= false");
					return false;
				}
			}
		}
		LogEnablement.isLogDebugEnabled(trigLogger,"Exit isDateGreaterLessThenEqualToToday");
		return false;
	}
	/**
	 * Override And behavior
	 * @param attrName
	 * @param countTriggers
	 * @param outputMap
	 * @param noChangeDetectedOverrideAND
	 */
	private static void overrideAndBehavior(String attrName, int countTriggers, Map outputMap, String noChangeDetectedOverrideAND)
	{
		LogEnablement.isLogDebugEnabled(trigLogger,"Enter overrideAndBehavior");
		// LET'S SEE IF WE WANT TO OVERRIDE AND BEHAVIOR
		if (noChangeDetectedOverrideAND != null&& noChangeDetectedOverrideAND.equalsIgnoreCase("YES"))
		{
			LogEnablement.isLogDebugEnabled(trigLogger,"...noChangeDetectedOverrideAND = "+ noChangeDetectedOverrideAND);
			outputMap.put(attrName + countTriggers,"true");
			LogEnablement.isLogDebugEnabled(trigLogger,"...CHANGE DETECTED OVERRIDE NEW ANY OLD EMPTY= "+ outputMap);
		}
		else
		{
			outputMap.put(attrName + countTriggers,"false");
		}
		LogEnablement.isLogDebugEnabled(trigLogger,"End overrideAndBehavior");
	}
	/**
	 * New Empty and Old Wild Card
	 * @param newIdentity
	 * @param previousIdentity
	 * @param outputMap
	 * @param attrName
	 * @param countTriggers
	 * @param noChangeDetectedOverrideAND
	 */
	private static void newEmptyAndOldWildCard(Identity newIdentity, Identity previousIdentity, Map outputMap, String attrName, int countTriggers, String noChangeDetectedOverrideAND)
	{
		LogEnablement.isLogDebugEnabled(trigLogger,"Start...newEmptyAndOldWildCard.." );
		if ((newIdentity.getAttribute(attrName) == null || (newIdentity.getAttribute(attrName) instanceof String && newIdentity.getAttribute(attrName).toString().length() <= 0))
				&& ((previousIdentity.getAttribute(attrName) != null) && (previousIdentity.getAttribute(attrName) instanceof String && previousIdentity.getAttribute(attrName).toString().length() > 0)))
		{
			LogEnablement.isLogDebugEnabled(trigLogger,"...IS EMPTY CHANGE DETECTED");
			outputMap.put(attrName + countTriggers, "true");
		}
		else
		{
			overrideAndBehavior( attrName, countTriggers, outputMap, noChangeDetectedOverrideAND);
		}
		//Boolean
		if (newIdentity.getAttribute(attrName) == null && previousIdentity.getAttribute(attrName) != null &&
				previousIdentity.getAttribute(attrName) instanceof Boolean &&
				((Boolean)previousIdentity.getAttribute(attrName) ))
		{
			LogEnablement.isLogDebugEnabled(trigLogger,"...IS EMPTY CHANGE DETECTED");
			outputMap.put(attrName + countTriggers, "true");
		}
		else
		{
			overrideAndBehavior( attrName, countTriggers, outputMap, noChangeDetectedOverrideAND);
		}
		//List
		if ((newIdentity.getAttribute(attrName) == null||(newIdentity.getAttribute(attrName) instanceof List && ((List)newIdentity.getAttribute(attrName)).size()<=0))
				&& previousIdentity.getAttribute(attrName) != null && previousIdentity.getAttribute(attrName) instanceof List && ((List)previousIdentity.getAttribute(attrName)).size()>0) {
			LogEnablement.isLogDebugEnabled(trigLogger,"...IS EMPTY CHANGE DETECTED");
			outputMap.put(attrName + countTriggers, "true");
		}
		else
		{
			overrideAndBehavior( attrName, countTriggers, outputMap, noChangeDetectedOverrideAND);
		}
		//Identity
		if (newIdentity.getAttribute(attrName) == null && previousIdentity.getAttribute(attrName) != null
				&& previousIdentity.getAttribute(attrName) instanceof Identity)
		{
			LogEnablement.isLogDebugEnabled(trigLogger,"...IS EMPTY CHANGE DETECTED");
			outputMap.put(attrName + countTriggers, "true");
		}
		else
		{
			overrideAndBehavior( attrName, countTriggers, outputMap, noChangeDetectedOverrideAND);
		}
		LogEnablement.isLogDebugEnabled(trigLogger,"End...newEmptyAndOldWildCard.." );
	}
	/**
	 * Old Empty and New Wild Card
	 * @param newIdentity
	 * @param previousIdentity
	 * @param outputMap
	 * @param attrName
	 * @param countTriggers
	 * @param noChangeDetectedOverrideAND
	 */
	private static void oldEmptyAndNewWildCard(Identity newIdentity, Identity previousIdentity, Map outputMap, String attrName, int countTriggers, String noChangeDetectedOverrideAND)
	{
		LogEnablement.isLogDebugEnabled(trigLogger,"Start...oldEmptyAndNewWildCard.." );
		if ((previousIdentity.getAttribute(attrName) == null || (previousIdentity.getAttribute(attrName) instanceof String && previousIdentity.getAttribute(attrName).toString().length() <= 0))
				&& ((newIdentity.getAttribute(attrName) != null) && (newIdentity.getAttribute(attrName) instanceof String && newIdentity.getAttribute(attrName).toString().length() > 0))) {
			LogEnablement.isLogDebugEnabled(trigLogger,"...IS EMPTY CHANGE DETECTED");
			outputMap.put(attrName + countTriggers, "true");
		}
		else
		{
			overrideAndBehavior( attrName, countTriggers, outputMap, noChangeDetectedOverrideAND);
		}
		//Boolean
		if (previousIdentity.getAttribute(attrName) == null && newIdentity.getAttribute(attrName) != null
				&& newIdentity.getAttribute(attrName) instanceof Boolean &&
				((Boolean)newIdentity.getAttribute(attrName)))
		{
			LogEnablement.isLogDebugEnabled(trigLogger,"...IS EMPTY CHANGE DETECTED");
			outputMap.put(attrName + countTriggers, "true");
		}
		else
		{
			overrideAndBehavior( attrName, countTriggers, outputMap, noChangeDetectedOverrideAND);
		}
		//List
		if ((previousIdentity.getAttribute(attrName) == null||(previousIdentity.getAttribute(attrName) instanceof List && ((List)previousIdentity.getAttribute(attrName)).size()<=0))
				&& newIdentity.getAttribute(attrName) != null && newIdentity.getAttribute(attrName) instanceof List && ((List)newIdentity.getAttribute(attrName)).size()>0) {
			LogEnablement.isLogDebugEnabled(trigLogger,"...IS EMPTY CHANGE DETECTED");
			outputMap.put(attrName + countTriggers, "true");
		}
		else
		{
			overrideAndBehavior( attrName, countTriggers, outputMap, noChangeDetectedOverrideAND);
		}
		//Identity
		if (previousIdentity.getAttribute(attrName) == null && newIdentity.getAttribute(attrName) != null
				&& newIdentity.getAttribute(attrName) instanceof Identity)
		{
			LogEnablement.isLogDebugEnabled(trigLogger,"...IS EMPTY CHANGE DETECTED");
			outputMap.put(attrName + countTriggers, "true");
		}
		else
		{
			overrideAndBehavior( attrName, countTriggers, outputMap, noChangeDetectedOverrideAND);
		}
		LogEnablement.isLogDebugEnabled(trigLogger,"End...oldEmptyAndNewWildCard.." );
	}
	/**
	 * Date Comparison Old and New
	 * @param context
	 * @param newIdentity
	 * @param previousIdentity
	 * @param outputMap
	 * @param attrName
	 * @param countTriggers
	 * @param noChangeDetectedOverrideAND
	 * @param dateFormat
	 * @param dateOperation
	 */
	private static void dateOldNewCompare(SailPointContext context, Identity newIdentity, Identity previousIdentity, Map outputMap, String attrName,
			int countTriggers, String noChangeDetectedOverrideAND, String dateFormat, String dateOperation)
	{
		LogEnablement.isLogDebugEnabled(trigLogger,"Start...dateOldNewCompare.." );
		LogEnablement.isLogDebugEnabled(trigLogger,"...Date - New Attribute = "+ newIdentity.getAttribute(attrName));
		LogEnablement.isLogDebugEnabled(trigLogger,"...Date - Previous Attribute = "+ previousIdentity.getAttribute(attrName));
		if (newIdentity.getAttribute(attrName) != null && newIdentity.getAttribute(attrName) instanceof String)
		{
			String newDate = (String) newIdentity.getAttribute(attrName);
			Object prevDate = previousIdentity.getAttribute(attrName);
			if (newDate != null && !(newDate.equals("")))
			{
				// 1. First Time Date Assignment Either
				// Joiner or Leaver
				if (newIdentity.getAttribute(attrName) != null && (prevDate == null || (prevDate instanceof String && prevDate .toString().length() <= 0)))
				{
					// CHECK TO SEE IF THE DATE IN A
					// SPECIFIED FORMAT IS GREATER THEN
					// TODAY
					if (isDateGreaterLessThenEqualToToday(context, newDate, dateFormat,dateOperation))
					{
						LogEnablement.isLogDebugEnabled(trigLogger,"...CHANGE DETECTED");
						outputMap.put(attrName+ countTriggers, "true");
					}
					else
					{
						overrideAndBehavior( attrName, countTriggers, outputMap, noChangeDetectedOverrideAND);
					}
				}
				// 2. Change in Leaver / Joiner Date
				// Assignment
				else if (newIdentity.getAttribute(attrName) != null && previousIdentity.getAttribute(attrName) != null
						&& newIdentity.getAttribute(attrName) instanceof String && previousIdentity.getAttribute(attrName) instanceof String)
				{
					if (!(((String) newIdentity.getAttribute(attrName)).equalsIgnoreCase((String) previousIdentity.getAttribute(attrName))))
					{
						// CHECK TO SEE IF THE DATE IN A
						// SPECIFIED FORMAT IS GREATER THEN
						// TODAY
						if (isDateGreaterLessThenEqualToToday(context, newDate,dateFormat, dateOperation))
						{
							LogEnablement.isLogDebugEnabled(trigLogger,"...CHANGE DETECTED");
							outputMap.put(attrName+ countTriggers,"true");
						}
						else
						{
							overrideAndBehavior( attrName, countTriggers, outputMap, noChangeDetectedOverrideAND);
						}
					}
					else
					{
						outputMap.put(attrName+ countTriggers, "false");
					}
				}
				else
				{
					outputMap.put(attrName + countTriggers,"false");
				}
			}
			else
			{
				outputMap.put(attrName + countTriggers,"false");
			}
		}
		else if (newIdentity.getAttribute(attrName) != null && newIdentity.getAttribute(attrName) instanceof Date)
		{
			Date newDate = (Date) newIdentity.getAttribute(attrName);
			Object prevDate = previousIdentity.getAttribute(attrName);
			if (newDate != null && !(newDate.equals("")))
			{
				// 1. First Time Date Assignment Either
				// Joiner or Leaver
				if (newDate != null && (prevDate == null) )
				{
					// CHECK TO SEE IF THE DATE IN A
					// SPECIFIED FORMAT IS GREATER THEN
					// TODAY
					Date now = new Date();
					if (isDateGreaterLessThenEqualToToday(context, newDate,now,dateOperation))
					{
						LogEnablement.isLogDebugEnabled(trigLogger,"...CHANGE DETECTED");
						outputMap.put(attrName+ countTriggers, "true");
					}
					else
					{
						overrideAndBehavior( attrName, countTriggers, outputMap, noChangeDetectedOverrideAND);
					}
				}
				else
				{
					outputMap.put(attrName + countTriggers,"false");
				}
				// 2. Change in Leaver / Joiner Date
				// Assignment
				if (newDate != null && prevDate != null
						&& newDate instanceof Date && prevDate instanceof Date)
				{
					Date prevDateObj=(Date)prevDate;
					long newDateLong=newDate.getTime();
					long prevDateLong=prevDateObj.getTime();
					long timeDiffInMs = newDateLong- prevDateLong;
					int days = (int) (timeDiffInMs / (1000 * 60 * 60 * 24));
					LogEnablement.isLogDebugEnabled(trigLogger,"days..new and previous.."+days);
					//Date Comparison
					if (days!=0)
					{
						// CHECK TO SEE IF THE DATE IN A
						// SPECIFIED FORMAT IS GREATER THEN
						// TODAY
						Date now = new Date();
						if (isDateGreaterLessThenEqualToToday(context,newDate, now, dateOperation))
						{
							LogEnablement.isLogDebugEnabled(trigLogger,"...CHANGE DETECTED");
							outputMap.put(attrName+ countTriggers,"true");
						} else
						{
							overrideAndBehavior( attrName, countTriggers, outputMap, noChangeDetectedOverrideAND);
						}
					}
					else
					{
						outputMap.put(attrName+ countTriggers, "false");
					}
				}
				else
				{
					outputMap.put(attrName + countTriggers,"false");
				}
			}
			else
			{
				outputMap.put(attrName + countTriggers,"false");
			}
		}
		LogEnablement.isLogDebugEnabled(trigLogger,"End...dateOldNewCompare.." );
	}
	/**
	 * Single Old and New Values Comma Separated
	 * @param newIdentity
	 * @param previousIdentity
	 * @param newValues
	 * @param oldvalues
	 * @param outputMap
	 * @param attrName
	 * @param countTriggers
	 * @param noChangeDetectedOverrideAND
	 */
	private static void singleValuesComparison(Identity newIdentity, Identity previousIdentity, String newValues, String oldValues, Map outputMap, String attrName, int countTriggers, String noChangeDetectedOverrideAND)
	{
		LogEnablement.isLogDebugEnabled(trigLogger,"Start...singleValuesComparison.." );
		LogEnablement.isLogDebugEnabled(trigLogger,"...Other - New Attribute = "+ attrName+"->"+newIdentity.getAttribute(attrName));
		LogEnablement.isLogDebugEnabled(trigLogger,"...Other - Previous Attribute = "+ attrName+"->"+ previousIdentity.getAttribute(attrName));
		if((newValues.equalsIgnoreCase("true") && newIdentity.getAttribute(attrName) instanceof Boolean && ((Boolean)newIdentity.getAttribute(attrName)).booleanValue())
				&& (oldValues.equalsIgnoreCase("false") && (previousIdentity.getAttribute(attrName)==null) || (previousIdentity.getAttribute(attrName)!=null
				&& previousIdentity.getAttribute(attrName) instanceof Boolean && !((Boolean)previousIdentity.getAttribute(attrName)).booleanValue()))
				)
		{
			LogEnablement.isLogDebugEnabled(trigLogger,"...NEW AND PREVIOUS BOOLEAN ATTRIBUTE VALUES MATCHES ANY OLD / ANY NEW VALUES");
			outputMap.put(attrName+ countTriggers,"true");
		}
		else
		{
			overrideAndBehavior( attrName, countTriggers, outputMap, noChangeDetectedOverrideAND);
		}
		if((oldValues.equalsIgnoreCase("true") && previousIdentity.getAttribute(attrName) instanceof Boolean && ((Boolean)previousIdentity.getAttribute(attrName)).booleanValue())
				&& (newValues.equalsIgnoreCase("false") && (newIdentity.getAttribute(attrName)==null) || (newIdentity.getAttribute(attrName)!=null
				&& newIdentity.getAttribute(attrName) instanceof Boolean && !((Boolean)newIdentity.getAttribute(attrName)).booleanValue()))
				)
		{
			LogEnablement.isLogDebugEnabled(trigLogger,"...NEW AND PREVIOUS BOOLEAN ATTRIBUTE VALUES MATCHES ANY OLD / ANY NEW VALUES");
			outputMap.put(attrName+ countTriggers,"true");
		}
		else
		{
			overrideAndBehavior( attrName, countTriggers, outputMap, noChangeDetectedOverrideAND);
		}
		if (newValues.indexOf(newIdentity.getStringAttribute(attrName)) != -1 && oldValues.indexOf(previousIdentity.getStringAttribute(attrName)) != -1)
		{
			LogEnablement.isLogDebugEnabled(trigLogger,"...NEW AND PREVIOUS ATTRIBUTE VALUES MATCHES ANY OLD / ANY NEW VALUES");
			outputMap.put(attrName+ countTriggers,"true");
		}
		else
		{
			overrideAndBehavior( attrName, countTriggers, outputMap, noChangeDetectedOverrideAND);
		}
		LogEnablement.isLogDebugEnabled(trigLogger,"End...singleValuesComparison.." );
		}
	/**
	 * Compare Old and New Values Comma Separated
	 * @param newIdentity
	 * @param previousIdentity
	 * @param newValues
	 * @param oldvalues
	 * @param outputMap
	 * @param attrName
	 * @param countTriggers
	 * @param noChangeDetectedOverrideAND
	 */
	private static void commaSeperatedValuesComparison(Identity newIdentity, Identity previousIdentity, String newValues, String oldValues, Map outputMap, String attrName, int countTriggers, String noChangeDetectedOverrideAND)
	{
		LogEnablement.isLogDebugEnabled(trigLogger,"Start...commaSeperatedValuesComparison.." );
		// NEW IDENTITY HAS SOME VALUE
		if (newIdentity.getAttribute(attrName) != null && newIdentity.getAttribute(attrName) instanceof String
				&& newIdentity.getAttribute(attrName).toString() != null
				&& newIdentity.getAttribute(attrName)
				.toString().length() > 0) {
			LogEnablement.isLogDebugEnabled(trigLogger,"...Other - New Attribute = "
					+ newIdentity.getAttribute(attrName));
			LogEnablement.isLogDebugEnabled(trigLogger,"...Other - Previous Attribute = "
					+ previousIdentity
					.getAttribute(attrName));
			// PREVIOUS IDENTITY HAS SOME VALUE
			if (previousIdentity.getStringAttribute(attrName) != null
					&& previousIdentity.getStringAttribute(attrName) instanceof String
					&& previousIdentity.getAttribute(
							attrName).toString() != null
							&& previousIdentity
							.getAttribute(attrName)
							.toString().length() > 0) {
				// New Identity Attribute Value Doesn't
				// Match Previous Identity Attribute Value
				if (!(newIdentity
						.getStringAttribute(attrName)
						.equalsIgnoreCase(previousIdentity
								.getStringAttribute(attrName)))) {
					LogEnablement.isLogDebugEnabled(trigLogger,"...NEW AND PREVIOUS ATTRIBUTE VALUES ARE DIFFERENT");
					LogEnablement.isLogDebugEnabled(trigLogger,"...newValues="
							+ newValues);
					LogEnablement.isLogDebugEnabled(trigLogger,"...oldValues="
							+ oldValues);
					if (newValues != null
							&& oldValues != null)
					{
						// New Identity Cube Attribute Value
						// Matches New Values and Previous
						// Identity Cube Attribute Value
						// Matches Old Values
						// START COMMA SEPARATED OR SINGLE
						// NEW AND OLD
						if (newValues.indexOf(newIdentity.getStringAttribute(attrName)) != -1 && oldValues.indexOf(previousIdentity.getStringAttribute(attrName)) != -1)
						{
							LogEnablement.isLogDebugEnabled(trigLogger,"...NEW AND PREVIOUS ATTRIBUTE VALUES MATCHES ANY OLD / ANY NEW VALUES");
							outputMap.put(attrName+ countTriggers,"true");
						}
						// END COMMA SEPARATED OR SINGLE NEW
						// AND OLD
						else
						{
							overrideAndBehavior( attrName, countTriggers, outputMap, noChangeDetectedOverrideAND);
						}
					}
					else
					{
						outputMap.put(attrName
								+ countTriggers, "false");
					}
				}
				// New Identity Attribute Value Matches
				// Previous Identity Attribute Value
				else {
					LogEnablement.isLogDebugEnabled(trigLogger,"...NEW AND PREVIOUS VALUES ARE SAME");
					// This flag was added when there is an
					// AND operation, comma separated values
					// have not changed but Date Assignment
					// has changed
					// This flag is set to true on comma
					// separated values
					if (noChangeDetectedOverrideAND != null
							&& noChangeDetectedOverrideAND
							.equalsIgnoreCase("YES")) {
						LogEnablement.isLogDebugEnabled(trigLogger,"...noChangeDetectedOverrideAND = "
								+ noChangeDetectedOverrideAND);
						if (newValues != null) {
							// Previous and Old Identity
							// Cube Attribute Value is same
							// as New Values
							if (newValues
									.indexOf(newIdentity
											.getStringAttribute(attrName)) != -1
											&& newValues
											.indexOf(previousIdentity
													.getStringAttribute(attrName)) != -1) {
								outputMap.put(attrName
										+ countTriggers,
										"true");
								LogEnablement.isLogDebugEnabled(trigLogger,"...outputMap = "
										+ outputMap);
							} else {
								outputMap.put(attrName+ countTriggers,"false");
							}
						}
						// When newValues is not found just
						// return false
						else {
							outputMap.put(attrName
									+ countTriggers,
									"false");
						}
					}
					// When no Override set then don't do
					// antyhing
					else
					{
						outputMap.put(attrName+ countTriggers, "false");
					}
				}
			}
			// Empty Previous Identity Attribute Value
			else
			{
				if (newValues != null&& newIdentity.getStringAttribute(attrName).toString() != null
						&& newIdentity.getStringAttribute(attrName).toString().length() > 0)
				{
					if (newValues.indexOf(newIdentity.getStringAttribute(attrName)) != -1)
					{
						outputMap.put(attrName+ countTriggers, "true");
					}
					else
					{
						overrideAndBehavior( attrName, countTriggers, outputMap, noChangeDetectedOverrideAND);
					}
				}
				else
				{
					outputMap.put(attrName + countTriggers,
							"false");
				}
			}
		}
		else
		{
			outputMap
			.put(attrName + countTriggers, "false");
		}
		LogEnablement.isLogDebugEnabled(trigLogger,"End...commaSeperatedValuesComparison.." );
	}
	/**
	 * Old Ignore New Date Clear
	 * @param context
	 * @param newIdentity
	 * @param previousIdentity
	 * @param outputMap
	 * @param attrName
	 * @param countTriggers
	 * @param noChangeDetectedOverrideAND
	 * @param dateFormat
	 * @param dateOperation
	 */
	private static void oldIgnoredNewDateCleared(SailPointContext context, Identity newIdentity, Identity previousIdentity, Map outputMap, String attrName, int countTriggers, String noChangeDetectedOverrideAND, String dateFormat,
			String dateOperation)
	{
		LogEnablement.isLogDebugEnabled(trigLogger,"Start...oldIgnoredNewDateCleared.." );
		Object prevDate = previousIdentity.getAttribute(attrName);
		if(prevDate instanceof String)
		{
			if (isDateGreaterLessThenEqualToToday(context,(String)prevDate, dateFormat, dateOperation))
			{
				LogEnablement.isLogDebugEnabled(trigLogger,"...CHANGE DETECTED");
				outputMap.put(attrName + countTriggers, "true");
			}
			else
			{
				overrideAndBehavior( attrName, countTriggers,outputMap,noChangeDetectedOverrideAND);
			}
		}
		else if(prevDate instanceof Date)
		{
			Date now = new Date();
			if (isDateGreaterLessThenEqualToToday(context,(Date)prevDate, now,dateOperation))
			{
				LogEnablement.isLogDebugEnabled(trigLogger,"...CHANGE DETECTED");
				outputMap.put(attrName + countTriggers, "true");
			}
			else
			{
				overrideAndBehavior( attrName,countTriggers,outputMap,noChangeDetectedOverrideAND);
			}
		}
		else
		{
			outputMap.put(attrName + countTriggers, "false");
		}
		LogEnablement.isLogDebugEnabled(trigLogger,"End...oldIgnoredNewDateCleared.." );
	}
	/**
	 *  Old Ignore and New Values Population Match
	 * @param context
	 * @param newIdentity
	 * @param previousIdentity
	 * @param oldValues
	 * @param newValues
	 * @param outputMap
	 * @param attrName
	 * @param countTriggers
	 * @param noChangeDetectedOverrideAND
	 * @throws GeneralException
	 */
	private static void oldIgnoreNewPopulation(SailPointContext context, Identity newIdentity, Identity previousIdentity, String oldValues, String newValues, Map outputMap, String attrName, int countTriggers, String noChangeDetectedOverrideAND) throws GeneralException
	{
		LogEnablement.isLogDebugEnabled(trigLogger,"Start...oldIgnoreNewPopulation.." );
		String groupDefinitionName = newValues;
		// If the identity is in Group Definition-> Identity
		// is eligible for event
		if (groupDefinitionName != null)
		{
			int result = WrapperRuleLibrary.matchPopulation(context,newIdentity, groupDefinitionName);
			//THIS IS A NEGATE CONDITION - IDENTITY MUST NOT BE A MEMBER
			if (result == 0)
			{
				outputMap.put(attrName + countTriggers,"true");
				LogEnablement.isLogDebugEnabled(trigLogger,"...CHANGE DETECTED, Filter No Match - kick off Event");
			}
			else
			{
				overrideAndBehavior( attrName, countTriggers, outputMap, noChangeDetectedOverrideAND);
			}
		}
		else
		{
			outputMap.put(attrName + countTriggers, "false");
		}
		LogEnablement.isLogDebugEnabled(trigLogger,"End...oldIgnoreNewPopulation.." );
	}
	/**
	 *  Match Old and New Values Population Matches
	 * @param context
	 * @param newIdentity
	 * @param previousIdentity
	 * @param oldValues
	 * @param newValues
	 * @param outputMap
	 * @param attrName
	 * @param countTriggers
	 * @param noChangeDetectedOverrideAND
	 * @throws GeneralException
	 */
	private static void populationMatchOldNew(SailPointContext context, Identity newIdentity, Identity previousIdentity, String oldValues, String newValues, Map outputMap, String attrName, int countTriggers, String noChangeDetectedOverrideAND) throws GeneralException
	{
		LogEnablement.isLogDebugEnabled(trigLogger,"Start...populationMatchOldNew.." );
		String groupDefinitionNameNew = newValues;
		// If the identity is in Group Definition-> Identity
		// is eligible for event
		String groupDefinitionNameOld = oldValues;
		if (groupDefinitionNameNew != null && groupDefinitionNameOld!=null)
		{
			LogEnablement.isLogDebugEnabled(trigLogger,"...Population Matches = "+ newIdentity.getName());
			int resultNew = WrapperRuleLibrary.matchPopulation(context,newIdentity, groupDefinitionNameNew);
			LogEnablement.isLogDebugEnabled(trigLogger,"...Population Match New = "+ groupDefinitionNameNew+"..Count.."+resultNew);
			int resultOld = WrapperRuleLibrary.matchPopulation(context,newIdentity, groupDefinitionNameOld);
			LogEnablement.isLogDebugEnabled(trigLogger,"...Population Match Old = "+ groupDefinitionNameOld+"..Count.."+resultOld);
			//THIS IS A NEGATE CONDITION result==0 - IDENTITY MUST NOT BE A MEMBER
			// Identity MUST BE A MEMBER OF OLD GROUP DEFINITION
			if (resultNew==0 && resultOld>=1)
			{
				outputMap.put(attrName + countTriggers,"true");
				LogEnablement.isLogDebugEnabled(trigLogger,"...CHANGE DETECTED, Filter No Match - kick off Event");
			}
			else
			{
				overrideAndBehavior( attrName, countTriggers, outputMap, noChangeDetectedOverrideAND);
			}
		}
		else
		{
			outputMap.put(attrName + countTriggers, "false");
		}
		LogEnablement.isLogDebugEnabled(trigLogger,"End...populationMatchOldNew.." );
	}
	/**
	 * New Any and Old Single or Commq Separated
	 * @param newIdentity
	 * @param previousIdentity
	 * @param oldValues
	 * @param outputMap
	 * @param attrName
	 * @param countTriggers
	 * @param noChangeDetectedOverrideAND
	 */
	private static void newAnyAndOldSingleOrCommaSeparated(Identity newIdentity, Identity previousIdentity, String oldValues, Map outputMap, String attrName, int countTriggers, String noChangeDetectedOverrideAND)
	{
		LogEnablement.isLogDebugEnabled(trigLogger,"Start...newAnyAndOldSingleOrCommaSeparated.." );
		LogEnablement.isLogDebugEnabled(trigLogger,"...* - New Attribute = "
				+ newIdentity.getAttribute(attrName));
		LogEnablement.isLogDebugEnabled(trigLogger,"...* - Previous Attribute = "
				+ previousIdentity.getAttribute(attrName));
		if (!(newIdentity.getStringAttribute(attrName)
				.equalsIgnoreCase(previousIdentity
						.getStringAttribute(attrName)))) {
			if (oldValues.indexOf(previousIdentity
					.getStringAttribute(attrName)) != -1) {
				LogEnablement.isLogDebugEnabled(trigLogger,"...CHANGE DETECTED");
				outputMap.put(attrName + countTriggers,
						"true");
			}
			else
			{
				overrideAndBehavior( attrName, countTriggers, outputMap, noChangeDetectedOverrideAND);
			}
		} else {
			outputMap
			.put(attrName + countTriggers, "false");
		}
		LogEnablement.isLogDebugEnabled(trigLogger,"End...newAnyAndOldSingleOrCommaSeparated.." );
	}
	/**
	 * Old Same and New Single or Comma Separated
	 * @param newIdentity
	 * @param previousIdentity
	 * @param newValues
	 * @param outputMap
	 * @param attrName
	 * @param countTriggers
	 * @param noChangeDetectedOverrideAND
	 */
	private static void oldSameAndNewSingleOrCommaSeparated(Identity newIdentity, Identity previousIdentity, String newValues, Map outputMap, String attrName, int countTriggers, String noChangeDetectedOverrideAND)
	{
		LogEnablement.isLogDebugEnabled(trigLogger,"Start...oldSameAndNewSingleOrCommaSeparated.." );
		LogEnablement.isLogDebugEnabled(trigLogger,"...* - New Attribute = "
				+ newIdentity.getAttribute(attrName));
		LogEnablement.isLogDebugEnabled(trigLogger,"...* - Previous Attribute = "
				+ previousIdentity.getAttribute(attrName));
		if (newValues.indexOf(newIdentity
				.getStringAttribute(attrName)) != -1) {
			LogEnablement.isLogDebugEnabled(trigLogger,"...Trigger Detected SAME");
			outputMap.put(attrName + countTriggers, "true");
		} else {
			overrideAndBehavior( attrName, countTriggers, outputMap, noChangeDetectedOverrideAND);
		}
		LogEnablement.isLogDebugEnabled(trigLogger,"End...oldSameAndNewSingleOrCommaSeparated.." );

	}
	/**
	 * Old Any and New Single or Commq Separated
	 * @param newIdentity
	 * @param previousIdentity
	 * @param newValues
	 * @param outputMap
	 * @param attrName
	 * @param countTriggers
	 * @param noChangeDetectedOverrideAND
	 */
	private static void oldAnyAndNewSingleOrCommaSeparated(Identity newIdentity, Identity previousIdentity, String newValues, Map outputMap, String attrName, int countTriggers, String noChangeDetectedOverrideAND)
	{
		LogEnablement.isLogDebugEnabled(trigLogger,"Start...oldAnyAndNewSingleOrCommaSeparated.." );
		LogEnablement.isLogDebugEnabled(trigLogger,"...* - New Attribute = "
				+ newIdentity.getAttribute(attrName));
		LogEnablement.isLogDebugEnabled(trigLogger,"...* - Previous Attribute = "
				+ previousIdentity.getAttribute(attrName));
		if (!(newIdentity.getStringAttribute(attrName)
				.equalsIgnoreCase(previousIdentity
						.getStringAttribute(attrName)))) {
			if (newValues.indexOf(newIdentity
					.getStringAttribute(attrName)) != -1) {
				LogEnablement.isLogDebugEnabled(trigLogger,"...CHANGE DETECTED");
				outputMap.put(attrName + countTriggers,
						"true");
			}
			else
			{
				overrideAndBehavior( attrName, countTriggers, outputMap, noChangeDetectedOverrideAND);
			}
		}
		else
		{
			outputMap
			.put(attrName + countTriggers, "false");
		}
		LogEnablement.isLogDebugEnabled(trigLogger,"End...oldAnyAndNewSingleOrCommaSeparated.." );
	}
	/**
	 * Any New and Any Old Value
	 * @param newIdentity
	 * @param previousIdentity
	 * @param outputMap
	 * @param attrName
	 * @param countTriggers
	 * @param noChangeDetectedOverrideAND
	 */
	private static void wildCharacterAnyValue(Identity newIdentity, Identity previousIdentity, Map outputMap, String attrName, int countTriggers, String noChangeDetectedOverrideAND)
	{
		LogEnablement.isLogDebugEnabled(trigLogger,"Start...wildCharacterAnyValue.." );
		if (newIdentity.getAttribute(attrName) != null && newIdentity.getAttribute(attrName) instanceof String &&
				((String) newIdentity.getAttribute(attrName)).length() > 0
				&& previousIdentity.getAttribute(attrName) != null && previousIdentity.getAttribute(attrName) instanceof String &&
				((String) previousIdentity.getAttribute(attrName)).length() > 0)
		{
			LogEnablement.isLogDebugEnabled(trigLogger,"...* - New Attribute = "+ newIdentity.getAttribute(attrName));
			LogEnablement.isLogDebugEnabled(trigLogger,"...* - Previous Attribute = "+ previousIdentity.getAttribute(attrName));
			if (!(newIdentity.getStringAttribute(attrName).equalsIgnoreCase(previousIdentity.getStringAttribute(attrName))))
			{
				LogEnablement.isLogDebugEnabled(trigLogger,"...CHANGE DETECTED");
				outputMap.put(attrName + countTriggers,"true");
			}
			else if ((newIdentity.getStringAttribute(attrName).equalsIgnoreCase(previousIdentity.getStringAttribute(attrName))))
			{
				overrideAndBehavior( attrName, countTriggers, outputMap, noChangeDetectedOverrideAND);
			}
		}
		else if(previousIdentity.getAttribute(attrName)!=null && previousIdentity.getAttribute(attrName) instanceof List && ((List) previousIdentity.getAttribute(attrName)).size()>0 &&
				newIdentity.getAttribute(attrName)!=null && newIdentity.getAttribute(attrName) instanceof List && ((List) newIdentity.getAttribute(attrName)).size()>0)
		{
			LogEnablement.isLogDebugEnabled(trigLogger,"Compare Previous and New List");
			List prevList = ((List) previousIdentity.getAttribute(attrName));
			LogEnablement.isLogDebugEnabled(trigLogger,"prevList.."+prevList);
			List newList = ((List) newIdentity.getAttribute(attrName));
			LogEnablement.isLogDebugEnabled(trigLogger,"newList.."+newList);
			if(prevList.size()!=newList.size() || !prevList.containsAll(newList) || !newList.containsAll(prevList))
			{
				LogEnablement.isLogDebugEnabled(trigLogger,"Attribute change found. Mismatch of List..True");
				outputMap.put(attrName + countTriggers,"true");
			}
			else
			{
				overrideAndBehavior( attrName, countTriggers, outputMap, noChangeDetectedOverrideAND);
			}
		}
		else if (newIdentity.getAttribute(attrName) instanceof Boolean && previousIdentity.getAttribute(attrName) instanceof Boolean)
		{
			LogEnablement.isLogDebugEnabled(trigLogger,"...* - New Attribute = "+ newIdentity.getAttribute(attrName));
			LogEnablement.isLogDebugEnabled(trigLogger,"...* - Previous Attribute = "+ previousIdentity.getAttribute(attrName));
			if ((((Boolean)newIdentity.getAttribute(attrName) && !(Boolean)previousIdentity.getAttribute(attrName))||(!(Boolean)newIdentity.getAttribute(attrName) && (Boolean)previousIdentity.getAttribute(attrName)))) {
				LogEnablement.isLogDebugEnabled(trigLogger,"...CHANGE DETECTED Old/New True/False");
				outputMap.put(attrName + countTriggers,"true");
			}
			else if ((((Boolean)newIdentity.getAttribute(attrName) && (Boolean)previousIdentity.getAttribute(attrName))||(!(Boolean)newIdentity.getAttribute(attrName) && !(Boolean)previousIdentity.getAttribute(attrName))))
			{
				overrideAndBehavior( attrName, countTriggers, outputMap, noChangeDetectedOverrideAND);
			}
		}
		else if (newIdentity.getAttribute(attrName) instanceof Identity && previousIdentity.getAttribute(attrName) instanceof Identity )
		{
			LogEnablement.isLogDebugEnabled(trigLogger,"...* - New Attribute = "+ newIdentity.getAttribute(attrName));
			LogEnablement.isLogDebugEnabled(trigLogger,"...* - Previous Attribute = "+ previousIdentity.getAttribute(attrName));
			if (!((Identity)newIdentity.getAttribute(attrName)).getId().equalsIgnoreCase(((Identity)previousIdentity.getAttribute(attrName)).getId()))
			{
				LogEnablement.isLogDebugEnabled(trigLogger,"...CHANGE DETECTED Old and New Identity Change");
				outputMap.put(attrName + countTriggers,"true");
			}
			else if (((Identity)newIdentity.getAttribute(attrName)).getId().equalsIgnoreCase(((Identity)previousIdentity.getAttribute(attrName)).getId()))
			{
				overrideAndBehavior( attrName, countTriggers, outputMap, noChangeDetectedOverrideAND);
			}
		}
		else
		{
			outputMap.put(attrName + countTriggers, "false");
		}
		LogEnablement.isLogDebugEnabled(trigLogger,"End...wildCharacterAnyValue.." );
	}
}
