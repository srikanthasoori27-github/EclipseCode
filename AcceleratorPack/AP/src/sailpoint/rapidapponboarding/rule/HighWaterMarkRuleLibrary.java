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
import java.util.Set;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import sailpoint.api.Identitizer;
import sailpoint.api.ObjectUtil;
import sailpoint.api.PersistenceManager;
import sailpoint.api.SailPointContext;
import sailpoint.object.Identity;
import sailpoint.object.IdentityArchive;
import sailpoint.object.IdentityTrigger;
import sailpoint.object.QueryOptions;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.xml.XMLObjectFactory;
import sailpoint.object.Resolver;
import sailpoint.rapidapponboarding.logger.LogEnablement;
import sailpoint.object.Custom;
import sailpoint.object.Filter;
import sailpoint.object.GroupDefinition;
import sailpoint.object.Attributes;
import sailpoint.object.Rule;
import sailpoint.object.TaskResult;
/**
 * High Water Mark Functionality for Triggers
 * @author rohit.gupta
 *
 */
public class HighWaterMarkRuleLibrary
{
	private static Log hightWaterMarkLogger = LogFactory.getLog("rapidapponboarding.rules");
	public static final String PROCESKEY_TRIGGER_CUSTOM="Custom-Triggers-User-Interface-Form";
	private static final String HIGHWATERMARKTHRESHOLD = "HighwaterMarkThreshold";
	private static final String HIGHWATERMARKPOPULATION = "HighwaterMarkPopulation";
	private static final String HIGHWATERMARKTRIGGER = "TriggerName";
	private static final String HIGHWATERMARKMOVERCERTTRIGGERNAME = "MOVER CERTIFICATION EVENT";
	private static final String HIGHWATERMARKMOVERTRIGGERNAME = "MOVER FEATURE";
	private static final String HIGHWATERMARKNATIVECHANGECERTTRIGGERNAME = "NATIVE CHANGE CERTIFICATION EVENT";
	private static final String HIGHWATERMARKNATIVECHANGETRIGGERNAME = "NATIVE CHANGE DETECTION FEATURE";
	/**
	 * Return Result of Scan
	 * @param context
	 * @param taskResult
	 * @param config
	 * @return
	 * @throws GeneralException
	 */
	public static String executeHighWaterMarks(SailPointContext context, TaskResult taskResult, Map config) throws GeneralException 
	{
		String returnResult = "Success";
		List<String> errorMessages=executeHighWaterMarks(context) ;
		if (errorMessages != null && taskResult != null) 
		{
			for(String str:errorMessages)
			{
				taskResult.addMessage(Message.error(str));
				returnResult = "Error";
			}
		}
		return returnResult;
	}
	/**
	 * Execute High Water Mark Scan
	 * @param context
	 * @return
	 * @throws GeneralException
	 */
	private static List executeHighWaterMarks(SailPointContext context) throws GeneralException
	{
		LogEnablement.isLogDebugEnabled(hightWaterMarkLogger,"Enter executeHighWaterMarks..");
		String lockMode = PersistenceManager.LOCK_TYPE_TRANSACTION;
		List errorList= new ArrayList();
		List<Map> enbaledhmMTriggers=getHighWaterMarkEnabledTriggers(context);
		if(enbaledhmMTriggers!=null && enbaledhmMTriggers.size()>0)
		{
			for(Map map:enbaledhmMTriggers)
			{
				//This will execute for all enabled triggers
				if(map!=null && !map.isEmpty())
				{
					boolean scanDetectedHwMarks=false;
					LogEnablement.isLogDebugEnabled(hightWaterMarkLogger,"Trigger map Threshold.."+map);
					LogEnablement.isLogDebugEnabled(hightWaterMarkLogger,"Trigger map scanDetectedHwMarks.."+scanDetectedHwMarks);
					Set<Map.Entry<String, String>> set = map.entrySet();
					int futureTriggerCount=0;
					Filter filter=null;
					int thresholdCount=0;
					Rule ruleObj=null;
					String triggerName=null;
					IdentityTrigger idTrigger=null;
					for (Map.Entry entry : set) 
					{ 
						Object value= entry.getValue();
						String key=(String) entry.getKey();
						LogEnablement.isLogDebugEnabled(hightWaterMarkLogger,"value.."+value);
						LogEnablement.isLogDebugEnabled(hightWaterMarkLogger,"key.."+key);
						if(key.equalsIgnoreCase(HighWaterMarkRuleLibrary.HIGHWATERMARKTHRESHOLD) && value!=null)
						{
							thresholdCount=((Integer) value).intValue();
							LogEnablement.isLogDebugEnabled(hightWaterMarkLogger,"thresholdCount.."+thresholdCount);
						}
						else if(key.equalsIgnoreCase(HighWaterMarkRuleLibrary.HIGHWATERMARKPOPULATION) && value!=null)
						{
							filter=(Filter)value;
						}
						else if(key.equalsIgnoreCase(HighWaterMarkRuleLibrary.HIGHWATERMARKTRIGGER))
						{
							triggerName=(String) value;
						}
					}
					if(triggerName!=null && thresholdCount>0 && filter!=null)
					{
						LogEnablement.isLogDebugEnabled(hightWaterMarkLogger,"triggerName.."+triggerName);
						LogEnablement.isLogDebugEnabled(hightWaterMarkLogger,"thresholdCount.."+thresholdCount);
						LogEnablement.isLogDebugEnabled(hightWaterMarkLogger,"filter.."+filter.toString());
						idTrigger=ObjectUtil.transactionLock(context,IdentityTrigger.class,triggerName);
						LogEnablement.isLogDebugEnabled(hightWaterMarkLogger," Lock Trigger..");
						if(idTrigger!=null)
						{
							LogEnablement.isLogDebugEnabled(hightWaterMarkLogger," Found Trigger.."+triggerName);
							ruleObj= idTrigger.getRule();
							try
							{
								if(ruleObj!=null && filter!=null && thresholdCount>=0)
								{
									LogEnablement.isLogDebugEnabled(hightWaterMarkLogger," Found rule.."+ruleObj.getName());
									QueryOptions qo = new QueryOptions();
									qo.addFilter(filter);
									Iterator it = context.search(Identity.class, qo, "name"); 
									while ( (null != it) && (it.hasNext()) ) 
									{  
										String name =  (String) ((Object[]) it.next())[0];
										if(name!=null)
										{
											HashMap params = new HashMap();
											Identity newIdentity=context.getObjectByName(Identity.class, name);
											Identity previousIdentity=getPreviousIdentity( context,  newIdentity);
											if(newIdentity!=null && previousIdentity!=null)
											{
												params.put("context", context);
												params.put("newIdentity", newIdentity);
												params.put("previousIdentity", previousIdentity);
												Boolean eligible = (Boolean) context.runRule(ruleObj, params);
												LogEnablement.isLogDebugEnabled(hightWaterMarkLogger," eligible.."+eligible);
												LogEnablement.isLogDebugEnabled(hightWaterMarkLogger," futureTriggerCount.."+futureTriggerCount);
												if (eligible != null && eligible == true) 
												{
													LogEnablement.isLogDebugEnabled(hightWaterMarkLogger," futureTriggerCount.."+futureTriggerCount);
													futureTriggerCount++;
												}
											}
										}
										if(futureTriggerCount!=0 && thresholdCount!=0)
										{
											LogEnablement.isLogDebugEnabled(hightWaterMarkLogger," futureTriggerCount.."+futureTriggerCount);
											LogEnablement.isLogDebugEnabled(hightWaterMarkLogger," thresholdCount.."+thresholdCount);
											if(futureTriggerCount>thresholdCount)
											{
												//Disable the Trigger
												idTrigger.setDisabled(true);
												context.saveObject(idTrigger);
												scanDetectedHwMarks=true;
												errorList.add(idTrigger.getName()+" Defined High Water Mark Threshold Count "+thresholdCount+ " Succeeded Future Triggers Count "+futureTriggerCount);
												break;
											}
										}
									}
								}
							}
							finally
							{
								if(scanDetectedHwMarks)
								{
									LogEnablement.isLogDebugEnabled(hightWaterMarkLogger," Detected High Water Marks For .."+idTrigger.getName());
									//Unlock Commits the Transaction
									ObjectUtil.unlockObject(context, idTrigger, lockMode);
								}
								else
								{
									LogEnablement.isLogDebugEnabled(hightWaterMarkLogger," Passed High Water Marks Scan For."+idTrigger.getName());
								}
							}
						}//Lock and Save Trigger
					}//Valid Trigger with Threshold and Filter
				}//Make Sure Trigger Map is not empty
			}//Execute for Each Trigger Map
		}
		return errorList;
	}
	/**
	 * Get HighWater Marks for a Trigger
	 * @param context
	 * @return
	 * @throws GeneralException
	 */
	private static List<Map> getHighWaterMarkEnabledTriggers(SailPointContext context) throws GeneralException
	{
		LogEnablement.isLogDebugEnabled(hightWaterMarkLogger,"Enter getHighWaterMarkEnabledTriggers..");
		List highWaterMarkEnabledTriggers = new ArrayList();
		Map mapThresholdPop;
		List<Map> enbaledProcessApTriggers=getProceeKeyTriggerMappings(context);
		if(enbaledProcessApTriggers!=null && enbaledProcessApTriggers.size()>0)
		{
			for(Map map:enbaledProcessApTriggers)
			{
				if(map!=null && !map.isEmpty())
				{
					Set<Map.Entry<String, String>> set = map.entrySet();
					for (Map.Entry entry : set) 
					{ 
						String triggerName=(String) entry.getValue();
						LogEnablement.isLogDebugEnabled(hightWaterMarkLogger,"Enter triggerName.."+triggerName);
						String processKey=(String) entry.getKey();
						LogEnablement.isLogDebugEnabled(hightWaterMarkLogger,"Enter processKey.."+processKey);
						if(processKey!=null && triggerName!=null && triggerName.length()>0)
						{
							List<Map> triggerStatus = TriggersRuleLibrary.getCustomTriggers(context,TriggersRuleLibrary.AUTHORITATIVE_SOURCE_TRIGGERS, processKey);
							if (triggerStatus != null && triggerStatus.size() > 0)
							{
								for (Map singleMap : triggerStatus) 
								{
									LogEnablement.isLogDebugEnabled(hightWaterMarkLogger," singleMap.."+singleMap);
									if(singleMap!=null && !singleMap.isEmpty() && 
									singleMap.containsKey(HighWaterMarkRuleLibrary.HIGHWATERMARKTHRESHOLD) &&
									singleMap.containsKey(HighWaterMarkRuleLibrary.HIGHWATERMARKPOPULATION))
									{
										String highWaterMarkPopulation=(String) singleMap.get(HighWaterMarkRuleLibrary.HIGHWATERMARKPOPULATION);
										LogEnablement.isLogDebugEnabled(hightWaterMarkLogger," highWaterMarkPopulation.."+highWaterMarkPopulation);
										String highWaterMarkThreshold=(String) singleMap.get(HighWaterMarkRuleLibrary.HIGHWATERMARKTHRESHOLD);
										LogEnablement.isLogDebugEnabled(hightWaterMarkLogger," highWaterMarkThreshold.."+highWaterMarkThreshold);
										Integer highWaterMarkThresholdInt=0;
										boolean valid=false;
										try
										{
											if(highWaterMarkThreshold!=null)
											{
												highWaterMarkThresholdInt = Integer.valueOf(highWaterMarkThreshold);
												valid=true;
												LogEnablement.isLogDebugEnabled(hightWaterMarkLogger," Valid highWaterMarkThresholdInt.."+highWaterMarkThresholdInt);
											}
										} 
										catch (NumberFormatException mumberFormatException) 
										{
											highWaterMarkThresholdInt=0;
											valid=false;
											LogEnablement.isLogDebugEnabled(hightWaterMarkLogger," In Valid highWaterMarkThresholdInt.."+highWaterMarkThresholdInt);
											LogEnablement.isLogErrorEnabled(hightWaterMarkLogger," mumberFormatException.."+mumberFormatException.getMessage());
										}
										Filter filter=null;
										if(highWaterMarkPopulation!=null)
										{
											GroupDefinition definition=context.getObjectByName(GroupDefinition.class, highWaterMarkPopulation);
											if(definition!=null && definition.getFilter()!=null)
											{
												valid=true;
												filter=definition.getFilter();
												LogEnablement.isLogDebugEnabled(hightWaterMarkLogger," Valid definition.."+highWaterMarkPopulation);
												context.decache(definition);
											}
											else
											{
												LogEnablement.isLogDebugEnabled(hightWaterMarkLogger," In Valid definition.."+highWaterMarkPopulation);
												valid=false;
											}
										}
										LogEnablement.isLogDebugEnabled(hightWaterMarkLogger," valid.."+valid);
										if(valid && filter!=null && triggerName!=null)
										{
											LogEnablement.isLogDebugEnabled(hightWaterMarkLogger," filter.."+filter.toString());
											LogEnablement.isLogDebugEnabled(hightWaterMarkLogger," triggerName.."+triggerName);
											mapThresholdPop= new HashMap();
											mapThresholdPop.put(HighWaterMarkRuleLibrary.HIGHWATERMARKTRIGGER, triggerName);
											mapThresholdPop.put(HighWaterMarkRuleLibrary.HIGHWATERMARKPOPULATION, filter);
											mapThresholdPop.put(HighWaterMarkRuleLibrary.HIGHWATERMARKTHRESHOLD, highWaterMarkThresholdInt);
											highWaterMarkEnabledTriggers.add(mapThresholdPop);
										}
									}
								}
							}
						}
					}
				}
			}
		}
		LogEnablement.isLogDebugEnabled(hightWaterMarkLogger,"End getHighWaterMarkEnabledTriggers...."+highWaterMarkEnabledTriggers);
		return highWaterMarkEnabledTriggers;
	}
	/**
	 * Get Previous Identity
	 * @param context
	 * @param identity
	 * @return
	 * @throws GeneralException
	 */
	private static Identity getPreviousIdentity(SailPointContext context, Identity identity)throws GeneralException 
	{
		LogEnablement.isLogDebugEnabled(hightWaterMarkLogger,"Enter getPreviousIdentity..");
		Identity prevIdentity=null;
		if(identity!=null)
		{
			//Make a Deep Copy On Identity
			prevIdentity = (Identity) identity.deepCopy((Resolver) context);
			Map<String,Object> snapshots = (Map<String,Object>) identity.getAttribute(Identity.ATT_TRIGGER_SNAPSHOTS);
			LogEnablement.isLogDebugEnabled(hightWaterMarkLogger,"snapshots.."+snapshots);
			//All Accelerator Pack Triggers are Rules
			String key = "Rule";
			//Get only Rule SnapShot
			if(snapshots!=null && snapshots.size()>0 && snapshots.containsKey(key))
			{
				String idArchiveId = (String) snapshots.get(key);
				LogEnablement.isLogDebugEnabled(hightWaterMarkLogger,"idArchiveId.."+idArchiveId);
				if (null != idArchiveId) 
				{
					IdentityArchive archive =context.getObjectById(IdentityArchive.class, idArchiveId);
					LogEnablement.isLogDebugEnabled(hightWaterMarkLogger,"archive..");
					if (null != archive) 
					{	
						prevIdentity = getArchivedIdentity(context,archive);
						context.decache(archive);
						LogEnablement.isLogDebugEnabled(hightWaterMarkLogger,"decache archive..");
					}
				}
			}
		}
		LogEnablement.isLogDebugEnabled(hightWaterMarkLogger,"End getPreviousIdentity..");
		return prevIdentity;
	}
	/**
     * Get Archived Identity
     */
    private static Identity getArchivedIdentity(SailPointContext context,IdentityArchive arch) throws GeneralException
    {
    	LogEnablement.isLogDebugEnabled(hightWaterMarkLogger,"getArchivedIdentity..");
	    String xml = arch.getArchive();
        if (xml == null)
        {
        	throw new GeneralException("IdentityArchive is Empty");
        }
        XMLObjectFactory xMLObjectFactory = XMLObjectFactory.getInstance();
        Object obj = xMLObjectFactory.parseXml(context, xml, false);
        if (!(obj instanceof Identity))
        {
            throw new GeneralException("Object is not an Identity");
        }
        LogEnablement.isLogDebugEnabled(hightWaterMarkLogger,"End getArchivedIdentity..");
        return (Identity) obj;
    }
	/**
	 * Get Accelerator Pack Enabled Triggers
	 * @param context
	 * @return
	 * @throws GeneralException 
	 */
	private static List getProceeKeyTriggerMappings(SailPointContext context) throws GeneralException
	{
		LogEnablement.isLogDebugEnabled(hightWaterMarkLogger,"Enter getProceeKeyTriggerMappings..");
		List<Map> enabledTriggers = new ArrayList();
		HashMap mapProcessKeyTriggerName = new HashMap();
		Attributes mapAttr = new Attributes();
		Custom mappingObj = context.getObjectByName(Custom.class, PROCESKEY_TRIGGER_CUSTOM);
		/*
		 * Custom Artifact will have only WorkflowHandler Triggers
		 */
		if(mappingObj!=null && mappingObj.getAttributes()!=null)
		{
			mapAttr = mappingObj.getAttributes();	
			if( mapAttr!=null)
			{
				Set<Map.Entry<String, String>> set = mapAttr.entrySet();
				for (Map.Entry entry : set) 
				{ 
					String value=(String) entry.getValue();
					String key=(String) entry.getKey();
					LogEnablement.isLogDebugEnabled(hightWaterMarkLogger," key.."+key);
					LogEnablement.isLogDebugEnabled(hightWaterMarkLogger," value.."+value);
					if(value!=null)
					{
						boolean featureDisable=ROADUtil.roadFeatureDisabled(context,value);
						LogEnablement.isLogDebugEnabled(hightWaterMarkLogger," featureDisable.."+featureDisable);
						LogEnablement.isLogDebugEnabled(hightWaterMarkLogger," value.."+value);
						//If WorkflowHandler is Disabled for Mover, Check if CertificationHandler is enabled
						if(featureDisable && value.equalsIgnoreCase(HighWaterMarkRuleLibrary.HIGHWATERMARKMOVERTRIGGERNAME))
						{
							//Recalculate
							value=HighWaterMarkRuleLibrary.HIGHWATERMARKMOVERCERTTRIGGERNAME;
							featureDisable=ROADUtil.roadFeatureDisabled(context,value);
							LogEnablement.isLogDebugEnabled(hightWaterMarkLogger," featureDisable.."+featureDisable);
							LogEnablement.isLogDebugEnabled(hightWaterMarkLogger," value.."+value);
						}
						//If WorkflowHandler is Disabled for Native Change, Check if CertificationHandler is enabled
						if(featureDisable && value.equalsIgnoreCase(HighWaterMarkRuleLibrary.HIGHWATERMARKNATIVECHANGETRIGGERNAME))
						{
							//Recalculate
							value=HighWaterMarkRuleLibrary.HIGHWATERMARKNATIVECHANGECERTTRIGGERNAME;
							featureDisable=ROADUtil.roadFeatureDisabled(context,value);
							LogEnablement.isLogDebugEnabled(hightWaterMarkLogger," featureDisable.."+featureDisable);
							LogEnablement.isLogDebugEnabled(hightWaterMarkLogger," value.."+value);
						}
						if(!featureDisable)
						{
							mapProcessKeyTriggerName.put(key, value);
						}
					}
				}
			}
			context.decache(mappingObj);
		}
		enabledTriggers.add(mapProcessKeyTriggerName);
		LogEnablement.isLogDebugEnabled(hightWaterMarkLogger,"getProceeKeyTriggerMappings.."+enabledTriggers);
		return enabledTriggers;
	}
}
