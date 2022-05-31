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
import sailpoint.rapidapponboarding.logger.LogEnablement;
import sailpoint.tools.GeneralException;
import sailpoint.object.SailPointObject;
/**
 * Installed Smart Services - Admin
 * @author rohit.gupta
 *
 */
public class InstalledSmartServicesLibrary {
	private static Log installedSmartServicesLogger = LogFactory
			.getLog("rapidapponboarding.rules");
	/**
	 *SailPoint Object Enabled
	 * @param context
	 * @param className
	 * @param name
	 * @return
	 */
	public static boolean isSailPointObjectEnabled(SailPointContext context, String className, String name)throws GeneralException
	{
		LogEnablement.isLogDebugEnabled(installedSmartServicesLogger,"Start isSailPointObjectEnabled");
		Class clazz=ROADUtil.getSailPointObjectClazzFromClassList(className);
		if(name!=null && clazz!=null && context!=null)
		{
			SailPointObject Obj = context.getObjectByName(clazz, name);
			if(Obj!=null)
			{
				context.decache(Obj);
				LogEnablement.isLogDebugEnabled(installedSmartServicesLogger,"End isSailPointObjectEnabled Enabled");
				return true;	
			}
		}
		LogEnablement.isLogDebugEnabled(installedSmartServicesLogger,"End isSailPointObjectEnabled Disabled");
		return false;
	}
	/**
	 * Object Config Enabled
	 * @param context
	 * @param objectName
	 * @param attrName
	 * @return
	 */
	public static boolean objectConfigAttributeEnabled(SailPointContext context, String objectName, String attrName)throws GeneralException
	{
		LogEnablement.isLogDebugEnabled(installedSmartServicesLogger,"Start objectConfigAttributeEnabled");
		String disabled=ROADUtil.roadAttributeDisabled(context, objectName, attrName);
		if(disabled!=null && disabled.equalsIgnoreCase("FALSE"))
		{
			LogEnablement.isLogDebugEnabled(installedSmartServicesLogger,"End objectConfigAttributeEnabled Enabled");
			return true;	
		}
		LogEnablement.isLogDebugEnabled(installedSmartServicesLogger,"End objectConfigAttributeEnabled Disabled");
		return false;
	}
	/**
	 * Identity Trigger Enabled
	 * @param context
	 * @param triggerName
	 * @return
	 */
	public static boolean identityTriggerAttributeEnabled(SailPointContext context, String triggerName)throws GeneralException
	{
		LogEnablement.isLogDebugEnabled(installedSmartServicesLogger,"Start identityTriggerAttributeEnabled");
		String disabled=ROADUtil.roadFeatureDisabledString(context, triggerName);
		if(disabled!=null && disabled.equalsIgnoreCase("FALSE"))
		{
			LogEnablement.isLogDebugEnabled(installedSmartServicesLogger,"End identityTriggerAttributeEnabled Enabled");
			return true;	
		}
		LogEnablement.isLogDebugEnabled(installedSmartServicesLogger,"End identityTriggerAttributeEnabled Disabled");
		return false;
	}
}
