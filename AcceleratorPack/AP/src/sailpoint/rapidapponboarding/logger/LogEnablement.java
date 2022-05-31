/*
Copyright © 2018 SailPoint Technologies, Inc. All Rights Reserved.
All logos, text, content, and works of authorship, including but not limited to underlying code, programming or scripting language, designs, and/or graphics,
that are used and/or depicted herein are protected under United States and international copyright and trademark laws and treaties,
and may not be used or reproduced without the prior express written permission of SailPoint Technologies, Inc.
*/
package sailpoint.rapidapponboarding.logger;
import org.apache.commons.logging.Log;
import sailpoint.object.SailPointObject;
import sailpoint.tools.GeneralException;
/**
 * Log Enablement
 * @author rohit.gupta
 *
 */
public class LogEnablement {
	/**
	 * Log Debug Enabled
	 * @param log
	 * @param message
	 */
	public static void isLogDebugEnabled(Log log,String message)
	{
		if(log.isDebugEnabled())
		{
			log.debug(message);
		}
	}
	/**
	 * Log Debug Enabled (Only for ToXml Methods)
	 * @param log
	 * @param object
	 * @throws GeneralException 
	 */
	public static void isLogDebugEnabledToXml(Log log,SailPointObject object) throws GeneralException
	{
		if(log.isDebugEnabled())
		{
			log.debug(object.toXml());
		}
	}
	/**
	 * Log Error Enabled
	 * @param log
	 * @param message
	 */
	public static void isLogErrorEnabled(Log log,String message)
	{
		if(log.isErrorEnabled())
		{
			log.error(message);
		}
	}
	/**
	 * Log Error Enabled
	 * @param log
	 * @param message
	 */
	public static void isLogErrorEnabled(Log log,Exception e)
	{
		if(log.isErrorEnabled())
		{
			log.error(e.getStackTrace());
		}
	}
	/**
	 * Log Trace Enabled
	 * @param log
	 * @param message
	 */
	public static void isLogTraceEnabled(Log log,String message)
	{
		if(log.isTraceEnabled())
		{
			log.trace(message);
		}
	}
	/**
	 * Log Warning Enabled
	 * @param log
	 * @param message
	 */
	public static void isLogWarningEnabled(Log log,String message)
	{
		if(log.isWarnEnabled())
		{
			log.warn(message);
		}
	}
	/**
	 * Log Information Enabled
	 * @param log
	 * @param message
	 */
	public static void isLogInfoEnabled(Log log,String message)
	{
		if(log.isInfoEnabled())
		{
			log.info(message);
		}
	}
}
