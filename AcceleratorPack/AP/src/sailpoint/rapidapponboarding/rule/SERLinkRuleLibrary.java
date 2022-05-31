/*
Copyright © 2018 SailPoint Technologies, Inc. All Rights Reserved.
All logos, text, content, and works of authorship, including but not limited to underlying code, programming or scripting language, designs, and/or graphics,
that are used and/or depicted herein are protected under United States and international copyright and trademark laws and treaties,
and may not be used or reproduced without the prior express written permission of SailPoint Technologies, Inc.
 */
package sailpoint.rapidapponboarding.rule;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import sailpoint.api.SailPointContext;
import sailpoint.object.Attributes;
import sailpoint.object.Identity;
import sailpoint.rapidapponboarding.logger.LogEnablement;
/**
 * Epic/SER Link
 * @author rohit.gupta
 *
 */
public class SERLinkRuleLibrary {
	private static Log serLogger = LogFactory.getLog("rapidapponboarding.rules");
	private static final String EPICAPP="epicAppName";
	private static final String EPICID="epicIdAttr";
	private static final String SERAPP="serAppName";
	private static final String SERID="serIdAttr";
	public static final Object SENDEMAILOPERATIONSEPICSER = "apSendEmailToOperationsEpicSER";
	/**
	 * Link EMP SER
	 * 
	 * @param context
	 * @param previousIdentity
	 * @param newIdentity
	 * @param serIdSourceApp
	 * @param serIdSourceAttr
	 * @param epicAppName
	 * @param epicAppNameSeridAttr
	 * @return
	 * @throws Exception
	 */
	public static boolean isEligibleForSERLink(SailPointContext context,Identity previousIdentity, Identity newIdentity) throws Exception 
	{
		String identityName=null;
		if(newIdentity!=null)
		{
			identityName=newIdentity.getName();
		}
		LogEnablement.isLogDebugEnabled(serLogger,"Enter SERLinkRuleLibrary::isEligibleForSERLink..."+identityName);
		Attributes attributes=ROADUtil.getEpicSERAttributes(context);
		if (previousIdentity != null && newIdentity != null && attributes!=null) 
		{
			Object epicAppName= attributes.get(SERLinkRuleLibrary.EPICAPP);
			Object epicIdAttr= attributes.get(SERLinkRuleLibrary.EPICID);
			Object serAppName=attributes.get(SERLinkRuleLibrary.SERAPP);
			Object serIdAttr=attributes.get(SERLinkRuleLibrary.SERID);
			LogEnablement.isLogDebugEnabled(serLogger,"New identity not null...");
			if(epicAppName!=null && epicAppName instanceof String && ((String)epicAppName).length()>0 
					&& epicIdAttr!=null && epicIdAttr instanceof String && ((String)epicIdAttr).length()>0
					&& serAppName!=null && serAppName instanceof String && ((String)serAppName).length()>0
					&& serIdAttr!=null &&  serIdAttr instanceof String && ((String)serIdAttr).length()>0
					)
			{
				boolean hasEpicAccount = WrapperRuleLibrary.isLinkActive(context,newIdentity, (String)epicAppName);
				if (hasEpicAccount) 
				{
					LogEnablement.isLogDebugEnabled(serLogger,"New identity has Epic account..."+identityName);
					String currentProvId = WrapperRuleLibrary.getLinkAttributeValue(context, newIdentity,(String)epicAppName, (String)epicIdAttr);
					String epicId = WrapperRuleLibrary.getLinkAttributeValue(context, newIdentity, (String)serAppName, (String)serIdAttr);
					// If current providerID is null/empty, or if it is different
					// than the Epic ID, then the identity
					// is eligible for SER Link
					if (null == currentProvId || currentProvId.isEmpty()|| !currentProvId.equalsIgnoreCase(epicId))
					{
						LogEnablement.isLogDebugEnabled(serLogger,"End SERLinkRuleLibrary::isEligibleForSERLink...true");
						serLogger.debug("New identity PROV_ID is null, empty or different than current EPICID..."+identityName);
						return true;
					}
				}
			}
		}
		LogEnablement.isLogDebugEnabled(serLogger,"End SERLinkRuleLibrary::isEligibleForSERLink...false");
		return false;
	}
}
