/*
Copyright © 2018 SailPoint Technologies, Inc. All Rights Reserved.
All logos, text, content, and works of authorship, including but not limited to underlying code, programming or scripting language, designs, and/or graphics,
that are used and/or depicted herein are protected under United States and international copyright and trademark laws and treaties,
and may not be used or reproduced without the prior express written permission of SailPoint Technologies, Inc.
 */
package sailpoint.rapidapponboarding.rule;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import sailpoint.api.SailPointContext;
import sailpoint.object.Application;
import sailpoint.object.Attributes;
import sailpoint.object.Identity;
import sailpoint.object.ManagedAttribute;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.ProvisioningResult;
import sailpoint.object.ResourceObject;
import sailpoint.object.Rule;
import sailpoint.object.Schema;
import sailpoint.rapidapponboarding.logger.LogEnablement;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
/**
 * 
 * @author rohit.gupta
 *
 */
public class AggregationRuleLibrary {
	private static final String SERVICEACCOUNTOKEN="#IIQService#";
	private static final String SERVICEACCOUNTAPPLICATIONATTR="serviceAccountAttrExpression";
	private static final String SERVICEACCOUNTATTRIBUTENAME="apServiceAccountUniqueName";
	private static final String SERVICEACCOUNTAPPENDAPPLICATIONNAME="apAppendApplicationName";
	private static final String DEFAULTOBJECTTYPE = "group";
	private static final String AGGCUSTOMIZATIONAPRLE = "apAdditionalApplicationCustomizationRule";
	private static final String AGGCREATIONAPRLE = "apAdditionalApplicationCreationRule";
	private static final String AGGCORRELATIONAPRLE = "apAdditionalApplicationCorrelationRule";
	private static final String AGGMGATTRCUAPRLE = "apAdditionalApplicationMCustomizationRule";
	private static final String AGGMGATTRGRAPRLE = "apAdditionalApplicationMGroupRefreshRule";
	private static final String AGGAFTERPROVRLE = "apAdditionalApplicationAfterProvisioningRule";
	private static final String AGGPREITERRLE = "apAdditionalApplicationPreIterateRule";
	private static Log aggLogger = LogFactory.getLog("rapidapponboarding.rules");
	/**
	 * Check to see if passed in attribute name matches with application 
	 * group schema identity attribute name
	 * @param attr
	 * @param application
	 * @param objectType
	 * @return
	 */
	public static boolean isApplicationGroupIdentityAttribute(String attr,Application application, String objectType)
	{
		Schema groupSchema=null;
		List<Schema> groupSchemas = application.getGroupSchemas();
		if(groupSchemas!=null && groupSchemas.size()>=0)
		{
			for(Schema schema:groupSchemas)
			{
				if(objectType==null || (objectType!=null && objectType.equalsIgnoreCase(AggregationRuleLibrary.DEFAULTOBJECTTYPE)))
				{
					groupSchema=schema;
					break;
				}
				else if(objectType!=null  && schema.getObjectType()!=null && objectType.equalsIgnoreCase(schema.getObjectType()))
				{
					groupSchema=schema;
				}
			}
		}
		if(groupSchema!=null && groupSchema.getIdentityAttribute()!=null && attr!=null
				&& groupSchema.getIdentityAttribute().equalsIgnoreCase(attr))
		{
			return true;
		}
		return false;
	}
	/**
	 * Classify Privileged and Birthright Entitlements
	 * @param attrExpression
	 * @param attr
	 * @param operation
	 * @param text
	 * @param regex
	 * @param attribute
	 * @param tagName
	 * @param groupSchemaAttrs
	 * @param application
	 */
	public static void setPrivBirthEntsStrCompare(String attrExpression, String attr,String operation, Object text, String regex, ManagedAttribute attribute, String tagName, Attributes groupSchemaAttrs, Application application)
	{
		if(attrExpression!=null && attrExpression.equalsIgnoreCase(attr) && ROADUtil.executeStringComparison(text,operation,regex)>=1)
		{
			attribute.setAttribute(tagName,"TRUE");
		}
		else if(attrExpression!=null && attrExpression.equalsIgnoreCase(attr) )
		{
			attribute.setAttribute(tagName,null);
		}
		else if(attrExpression!=null && !attrExpression.equalsIgnoreCase(attr) )
		{
			//Attribute could be a group schema attribute
			if(groupSchemaAttrs!=null && groupSchemaAttrs.containsKey(attrExpression))
			{
				text=groupSchemaAttrs.get(attrExpression);
				if(text!=null && ROADUtil.executeStringComparison(text,operation, regex )>=1)
				{
					attribute.setAttribute(tagName,"TRUE");
				}
				else
				{
					attribute.setAttribute(tagName,null);
				}
			}
			else if(groupSchemaAttrs!=null && !groupSchemaAttrs.containsKey(attrExpression))
			{
				//Identity Attribute wouldn't be part of Entitlement Attributes
				boolean isMatch=isApplicationGroupIdentityAttribute( attrExpression, application, null);
				if (isMatch && ROADUtil.executeStringComparison(text,operation,regex)>=1)
				{
					attribute.setAttribute(tagName,"TRUE");
				}
				else if(isMatch)
				{
					attribute.setAttribute(tagName,null);
				}
			}
		}
	}
	/**
	 * Classify Privileged and Birthright Entitlements Dot Notation
	 * @param attrExpression
	 * @param attr
	 * @param text
	 * @param regex
	 * @param attribute
	 * @param tagName
	 * @param groupSchemaAttrs
	 * @param application
	 */
	public static void setPrivBirthEntsStrCompareDotNotation(String attrExpression, String attr,String operation, Object text, String regex, ManagedAttribute attribute, String tagName,Attributes groupSchemaAttrs, String type, Application application)
	{
		String refinedType=null;
		String refinedAttrExpression=null;
		if(type==null || type.length()<=0)
		{
			type=AggregationRuleLibrary.DEFAULTOBJECTTYPE;
		}
		if(attrExpression.contains("."))
		{
			if(attr != null && attr.contains("."))
			{
				//This is for LoopBack Connector - Some other connectors may have dot notation for the attribute names
				setPrivBirthEntsStrCompare( attrExpression,  attr, operation,  text,  regex,  attribute,  tagName,  groupSchemaAttrs, application);
			}
			{
				String[] attrExpressionArr = attrExpression.split("\\.");
				if(attrExpressionArr != null && attrExpressionArr.length == 2 && 
						Util.isNotNullOrEmpty(attrExpressionArr[0]) && Util.isNotNullOrEmpty(attrExpressionArr[1]))
				{
					refinedType=attrExpressionArr[0];
					refinedAttrExpression=attrExpressionArr[1];
					//Attribute on Group Schema has same name as Account Schema Attribute
					//attr-Account Schema 
					//refinedAttrExpression=Group Schema Attribute
					if(refinedAttrExpression!=null && refinedAttrExpression.equalsIgnoreCase(attr) && ROADUtil.executeStringComparison(text,operation, regex )>=1 && 
							type!=null && refinedType.equalsIgnoreCase(type))
					{
						attribute.setAttribute(tagName,"TRUE");
					}
					else if(refinedAttrExpression!=null && refinedAttrExpression.equalsIgnoreCase(attr) 
							&& type!=null && refinedType.equalsIgnoreCase(type))
					{
						//String Comparison doesn't match text - set extended attribute value to empty
						attribute.setAttribute(tagName,null);
					}
					else if(refinedAttrExpression!=null && !refinedAttrExpression.equalsIgnoreCase(attr) 
							&& type!=null && refinedType.equalsIgnoreCase(type))
					{
						//Attribute could be a group schema attribute
						if(groupSchemaAttrs!=null && groupSchemaAttrs.containsKey(refinedAttrExpression))
						{
							text=groupSchemaAttrs.get(refinedAttrExpression);
							if(text!=null && ROADUtil.executeStringComparison(text,operation, regex )>=1)
							{
								attribute.setAttribute(tagName,"TRUE");
							}
							else
							{
								attribute.setAttribute(tagName,null);
							}
						}
						else if(groupSchemaAttrs!=null && !groupSchemaAttrs.containsKey(refinedAttrExpression))
						{
							/**
							 * Account Schema - groups
							 * Group Schema - group
							 * Identity Attribute - name
							 * 
							 */
							//Identity Attribute wouldn't be part of Entitlement Attributes
							boolean isMatch=isApplicationGroupIdentityAttribute( refinedAttrExpression, application, type);
							if (isMatch && ROADUtil.executeStringComparison(text,operation,regex)>=1)
							{
								attribute.setAttribute(tagName,"TRUE");
							}
							else if(isMatch)
							{
								attribute.setAttribute(tagName,null);
							}
						}
					}
				}
			}
		}
	}
	/**
	 * Classify Privileged and Birthright Entitlements
	 * @param attrExpression
	 * @param attr
	 * @param text
	 * @param regex
	 * @param attribute
	 * @param tagName
	 * @param groupSchemaAttrs
	 * @param application
	 */
	public static void setPrivBirthEntsRegex(String attrExpression, String attr,Object text, String regex, ManagedAttribute attribute, String tagName, Attributes groupSchemaAttrs, Application application)
	{
		if(attrExpression!=null && attrExpression.equalsIgnoreCase(attr) && ROADUtil.executeRegex(regex,text )>=1)
		{
			attribute.setAttribute(tagName,"TRUE");
		}
		else if(attrExpression!=null && attrExpression.equalsIgnoreCase(attr) )
		{
			attribute.setAttribute(tagName,null);
		}
		else if(attrExpression!=null && !attrExpression.equalsIgnoreCase(attr) )
		{
			//Attribute could be a group schema attribute
			if(groupSchemaAttrs!=null && groupSchemaAttrs.containsKey(attrExpression))
			{
				text=groupSchemaAttrs.get(attrExpression);
				if(text!=null && ROADUtil.executeRegex(regex,text )>=1)
				{
					attribute.setAttribute(tagName,"TRUE");
				}
				else
				{
					attribute.setAttribute(tagName,null);
				}
			}
			else if(groupSchemaAttrs!=null && !groupSchemaAttrs.containsKey(attrExpression))
			{
				//Identity Attribute wouldn't be part of Entitlement Attributes
				boolean isMatch=isApplicationGroupIdentityAttribute( attrExpression, application, null);
				if (isMatch && ROADUtil.executeRegex(regex,text )>=1)
				{
					attribute.setAttribute(tagName,"TRUE");
				}
				else if(isMatch)
				{
					attribute.setAttribute(tagName,null);
				}
			}
		}
	}
	/**
	 * Classify Privileged and Birthright Entitlements Dot Notation
	 * @param attrExpression
	 * @param attr
	 * @param text
	 * @param regex
	 * @param attribute
	 * @param tagName
	 * @param groupSchemaAttrs
	 * @param application
	 */
	public static void setPrivBirthEntsRegexDotNotation(String attrExpression, String attr,Object text, String regex, ManagedAttribute attribute, String tagName, 
			Attributes groupSchemaAttrs, String type, Application application)
	{
		String refinedType=null;
		String refinedAttrExpression=null;
		if(type==null || type.length()<=0)
		{
			type=AggregationRuleLibrary.DEFAULTOBJECTTYPE;
		}
		if(attrExpression.contains("."))
		{
			if(attr != null && attr.contains("."))
			{
				//This is for LoopBack Connector - Some other connectors may have dot notation for the attribute names
				setPrivBirthEntsRegex(attrExpression,attr,text,regex,attribute,tagName,groupSchemaAttrs,application);
			}
			else
			{
				String[] attrExpressionArr = attrExpression.split("\\.");
				if(attrExpressionArr != null && attrExpressionArr.length == 2 && 
						Util.isNotNullOrEmpty(attrExpressionArr[0]) && Util.isNotNullOrEmpty(attrExpressionArr[1]))
				{
					refinedType=attrExpressionArr[0];
					refinedAttrExpression=attrExpressionArr[1];
					if(refinedAttrExpression!=null && refinedAttrExpression.equalsIgnoreCase(attr) && ROADUtil.executeRegex(regex,text )>=1 && 
							type!=null && refinedType.equalsIgnoreCase(type))
					{
						attribute.setAttribute(tagName,"TRUE");
					}
					else if(refinedAttrExpression!=null && refinedAttrExpression.equalsIgnoreCase(attr) 
							&& type!=null && refinedType.equalsIgnoreCase(type))
					{
						attribute.setAttribute(tagName,null);
					}
					else if(refinedAttrExpression!=null && !refinedAttrExpression.equalsIgnoreCase(attr) 
							&& type!=null && refinedType.equalsIgnoreCase(type))
					{
						//Attribute could be a group schema attribute
						if(groupSchemaAttrs!=null && groupSchemaAttrs.containsKey(refinedAttrExpression))
						{
							text=groupSchemaAttrs.get(refinedAttrExpression);
							if(text!=null && ROADUtil.executeRegex(regex,text )>=1)
							{
								attribute.setAttribute(tagName,"TRUE");
							}
							else
							{
								attribute.setAttribute(tagName,null);
							}
						}
						else if(groupSchemaAttrs!=null && !groupSchemaAttrs.containsKey(refinedAttrExpression))
						{
							//Identity Attribute wouldn't be part of Entitlement Attributes
							boolean isMatch=isApplicationGroupIdentityAttribute( refinedAttrExpression, application, type);
							if (isMatch && ROADUtil.executeRegex(regex,text )>=1)
							{
								attribute.setAttribute(tagName,"TRUE");
							}
							else if(isMatch)
							{
								attribute.setAttribute(tagName,null);
							}
						}
					}
				}
			}
		}
	}
	/**
	 * Set Logical Applications Requestable
	 * @param attribute
	 * @param application
	 */
	public static void setEntititlementRequestability( ManagedAttribute attribute, Application application)
	{
		if(attribute!=null && application!=null)
		{
			String appName = application.getName();
			Boolean busApp=(Boolean) application.getAttributeValue(ObjectConfigAttributesRuleLibrary.BUSAPP);
			String existingAppName=(String) attribute.getAttribute(ObjectConfigAttributesRuleLibrary.LOGICALAPP);
			if(busApp!=null && busApp.booleanValue())
			{
				//Synchronize Business Application and Logical Application
				if(existingAppName==null)
				{
					if(appName != null) 
					{
						attribute.setAttribute(ObjectConfigAttributesRuleLibrary.LOGICALAPP,appName);
					}
				}
				//All Business Application Entitlements are requestable
				//Set Requestable Flag to True
				attribute.setRequestable(true);
			}
			else
			{
				//No Logical Application Defined - Make them non-requestable
				if(existingAppName == null || existingAppName.equals("")) 
				{
					//Set Requestable Flag to false
					attribute.setRequestable(false);
				}
			}
		}
	}
	/**
	 * Get Service Account Expression
	 * @return
	 */
	public static String getSARegularAttrExpression(Application application)
	{
		//Get SA Account Expression
		String serviceAccountAttrExpression=null;
		serviceAccountAttrExpression=(String) application.getAttributeValue(AggregationRuleLibrary.SERVICEACCOUNTAPPLICATIONATTR);
		return serviceAccountAttrExpression;
	}
	/**
	 * Get Service Account Attribute Name Value from RO
	 * @return
	 */
	public static String getSAAttributeValue(Application application,ResourceObject account)
	{
		//Get SA Account Attribute Name Value from Resource Object
		String attributeName=null;
		String attributeValue=null;
		attributeName=(String) application.getAttributeValue(AggregationRuleLibrary.SERVICEACCOUNTATTRIBUTENAME);
		if(attributeName!=null && attributeName.length()>0)
		{
			attributeValue = account.getStringAttribute(attributeName);
		}
		return attributeValue;
	}
	/**
	 * Append Application Name to Service Account Name
	 * @param application
	 * @return
	 */
	public static boolean appendApplicationNameToServiceAccountName(Application application)
	{
		//Get SA Account Attribute Name Value from Resource Object
		Boolean result=false;
		if(application.getAttributeValue(AggregationRuleLibrary.SERVICEACCOUNTAPPENDAPPLICATIONNAME)!=null)
		{
			result=(Boolean) application.getAttributeValue(AggregationRuleLibrary.SERVICEACCOUNTAPPENDAPPLICATIONNAME);
		}
		return result.booleanValue();
	}
	/**
	 * Use Regular Expression to find service account, if matched, make sure it is not correlated to human cube
	 * @return
	 */
	public static String isSAAccountMatched(Application application, ResourceObject account)
	{
		String saText=null;
		String serviceAccountAttrExpression =getSARegularAttrExpression(application);
		if(serviceAccountAttrExpression!=null)
		{
			String[] saRegularAttrExpressionArr = serviceAccountAttrExpression.split(AggregationRuleLibrary.SERVICEACCOUNTOKEN);
			if(saRegularAttrExpressionArr != null && saRegularAttrExpressionArr.length == 3 && 
					Util.isNotNullOrEmpty(saRegularAttrExpressionArr[0]) && Util.isNotNullOrEmpty(saRegularAttrExpressionArr[1])&&
					Util.isNotNullOrEmpty(saRegularAttrExpressionArr[2]))
			{
				String saAttr=saRegularAttrExpressionArr[0];
				String saRegeX=saRegularAttrExpressionArr[1];
				String operation=saRegularAttrExpressionArr[2];
				saText = account.getStringAttribute(saAttr);
				if(ROADUtil.executeStringComparison(saText,operation, saRegeX)>=1)
				{
					return saText; 
				}
				else
				{
					return null;
				}
			}
			else if(saRegularAttrExpressionArr != null && saRegularAttrExpressionArr.length == 2 && Util.isNotNullOrEmpty(saRegularAttrExpressionArr[0]) && Util.isNotNullOrEmpty(saRegularAttrExpressionArr[1]))
			{
				String saAttr=saRegularAttrExpressionArr[0];
				String saRegeX=saRegularAttrExpressionArr[1];
				saText = account.getStringAttribute(saAttr);
				if(ROADUtil.executeRegex(saRegeX,saText)>=1)
				{
					return saText; 
				}
				else
				{
					return null;
				}
			}
		}
		return saText;
	}
	/**
	 * Run Application Customization Rule
	 * @param context
	 * @param application
	 * @param params
	 * @return
	 * @throws GeneralException 
	 */
	public static ResourceObject runApCustomizationRule(SailPointContext context,Application application,ResourceObject existingRO, HashMap params) throws GeneralException
	{
		LogEnablement.isLogDebugEnabled(aggLogger,"Enter runApCustomizationRule AP");
		String ruleName=(String) application.getAttributeValue(AggregationRuleLibrary.AGGCUSTOMIZATIONAPRLE);
		LogEnablement.isLogDebugEnabled(aggLogger,"Execute ruleName.."+ruleName);
		if(ruleName!=null && ruleName.trim().length()>0)
		{
			Rule rule = context.getObjectByName(Rule.class, ruleName);
			if(rule!=null)
			{
				Object obj=context.runRule(rule, params);
				if (obj!=null && obj instanceof ResourceObject)
				{
					existingRO=(ResourceObject) obj;
					LogEnablement.isLogDebugEnabled(aggLogger,"Modified Resource Object");
				}
			}
		}
		LogEnablement.isLogDebugEnabled(aggLogger,"End runApCustomizationRule");
		return existingRO;
	}
	/**
	 * Run Application After Provisioning Rule
	 * @param context
	 * @param application
	 * @param result
	 * @param application
	 * @throws GeneralException
	 */
	public static void runAfterProvisioningRule(SailPointContext context,ProvisioningPlan plan, Application application,ProvisioningResult result, Map params) throws GeneralException
	{
		LogEnablement.isLogDebugEnabled(aggLogger,"Enter runAfterProvisioningRule AP");
		String ruleName=(String) application.getAttributeValue(AggregationRuleLibrary.AGGAFTERPROVRLE);
		LogEnablement.isLogDebugEnabled(aggLogger,"Execute ruleName.."+ruleName);
		if(ruleName!=null && ruleName.trim().length()>0)
		{
			Rule rule = context.getObjectByName(Rule.class, ruleName);
			if(rule!=null)
			{
				Object obj=context.runRule(rule, params);
			}
		}
		LogEnablement.isLogDebugEnabled(aggLogger,"End runAfterProvisioningRule");
	}
	/**
	 * Run Application Pre Iterate Rule
	 * @param context
	 * @param application
	 * @param schema
	 * @param stats
	 * @param params
	 * @throws GeneralException
	 */
	public static void runPreIterateRule(SailPointContext context,Application application, Schema schema , Map stats, Map params) throws GeneralException
	{
		LogEnablement.isLogDebugEnabled(aggLogger,"Enter runPreIterateRule AP");
		String ruleName=(String) application.getAttributeValue(AggregationRuleLibrary.AGGPREITERRLE);
		LogEnablement.isLogDebugEnabled(aggLogger,"Execute ruleName.."+ruleName);
		if(ruleName!=null && ruleName.trim().length()>0)
		{
			Rule rule = context.getObjectByName(Rule.class, ruleName);
			if(rule!=null)
			{
				Object obj=context.runRule(rule, params);
			}
		}
		LogEnablement.isLogDebugEnabled(aggLogger,"End runPreIterateRule");
	}
	/**
	 * Run Application Group Refresh Rule
	 * @param context
	 * @param application
	 * @param attribute
	 * @param params
	 * @throws GeneralException
	 */
	public static ManagedAttribute runApGroupRefreshRuleRule(SailPointContext context,Application application,ManagedAttribute attribute, HashMap params) throws GeneralException
	{
		LogEnablement.isLogDebugEnabled(aggLogger,"Enter runApGroupRefreshRuleRule AP");
		String ruleName=(String) application.getAttributeValue(AggregationRuleLibrary.AGGMGATTRGRAPRLE);
		LogEnablement.isLogDebugEnabled(aggLogger,"Execute ruleName.."+ruleName);
		if(ruleName!=null && ruleName.trim().length()>0)
		{
			Rule rule = context.getObjectByName(Rule.class, ruleName);
			if(rule!=null)
			{
				Object obj=context.runRule(rule, params);
				if (obj!=null && obj instanceof ManagedAttribute)
				{
					attribute=(ManagedAttribute) obj;
					LogEnablement.isLogDebugEnabled(aggLogger,"Modified Existing Managed Attribute Group Refresh Rule");
				}
			}
		}
		LogEnablement.isLogDebugEnabled(aggLogger,"End runApGroupRefreshRuleRule");
		return attribute;
	}
	/**
	 * Run Application Managed Attribute Customization Rule
	 * @param context
	 * @param application
	 * @param attribute
	 * @param params
	 * @throws GeneralException
	 */
	public static void runApManagedAttributeCustomizationRule(SailPointContext context,Application application,ManagedAttribute attribute, HashMap params) throws GeneralException
	{
		LogEnablement.isLogDebugEnabled(aggLogger,"Enter runApManagedAttributeCustomizationRule AP");
		String ruleName=(String) application.getAttributeValue(AggregationRuleLibrary.AGGMGATTRCUAPRLE);
		LogEnablement.isLogDebugEnabled(aggLogger,"Execute ruleName.."+ruleName);
		if(ruleName!=null && ruleName.trim().length()>0)
		{
			Rule rule = context.getObjectByName(Rule.class, ruleName);
			if(rule!=null)
			{
				Object obj=context.runRule(rule, params);
				if (obj!=null && obj instanceof ManagedAttribute)
				{
					attribute=(ManagedAttribute) obj;
					LogEnablement.isLogDebugEnabled(aggLogger,"Modified Existing Managed Attribute Customization Rule");
				}
			}
		}
		LogEnablement.isLogDebugEnabled(aggLogger,"End runApManagedAttributeCustomizationRule");
	}
	/**
	 * Run Application Creation Rule
	 * @param context
	 * @param application
	 * @param params
	 * @return
	 * @throws GeneralException 
	 */
	public static void runApCreationRule(SailPointContext context,Application application,Identity existingIdentity, HashMap params) throws GeneralException
	{
		LogEnablement.isLogDebugEnabled(aggLogger,"Enter runApCreationRule AP");
		String ruleName=(String) application.getAttributeValue(AggregationRuleLibrary.AGGCREATIONAPRLE);
		LogEnablement.isLogDebugEnabled(aggLogger,"Execute ruleName.."+ruleName);
		if(ruleName!=null && ruleName.trim().length()>0)
		{
			Rule rule = context.getObjectByName(Rule.class, ruleName);
			if(rule!=null)
			{
				Object obj=context.runRule(rule, params);
				if (obj!=null && obj instanceof Identity)
				{
					existingIdentity=(Identity) obj;
					LogEnablement.isLogDebugEnabled(aggLogger,"Modified Existing Identity");
				}
			}
		}
		LogEnablement.isLogDebugEnabled(aggLogger,"End runApCreationRule");
	}
	/**
	 * Run Application Creation Rule
	 * @param context
	 * @param application
	 * @param params
	 * @return
	 * @throws GeneralException 
	 */
	public static Map runApCorrelationRule(SailPointContext context,Application application,Map correlationMap, HashMap params) throws GeneralException
	{
		LogEnablement.isLogDebugEnabled(aggLogger,"Enter runApCorrelationRule AP");
		String ruleName=(String) application.getAttributeValue(AggregationRuleLibrary.AGGCORRELATIONAPRLE);
		LogEnablement.isLogDebugEnabled(aggLogger,"Execute ruleName.."+ruleName);
		if(ruleName!=null && ruleName.trim().length()>0)
		{
			Rule rule = context.getObjectByName(Rule.class, ruleName);
			if(rule!=null)
			{
				Object obj=context.runRule(rule, params);
				if (obj!=null && obj instanceof Map)
				{
					correlationMap=(Map) obj;
					LogEnablement.isLogDebugEnabled(aggLogger,"Modified Existing Correlation Map.."+correlationMap);
				}
			}
		}
		LogEnablement.isLogDebugEnabled(aggLogger,"End runApCorrelationRule");
		return correlationMap;
	}
}
