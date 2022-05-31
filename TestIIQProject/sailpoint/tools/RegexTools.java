/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.tools;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class RegexTools
{
	private static Log log = LogFactory.getLog(RegexTools.class);

	public static String getGroup(String regex, int groupNumber, String source, String defaultValue)
	{
		String returnValue = defaultValue;
		String group = source;

		try
		{
			// ===== regex
			if (regex != null && regex.length() > 0)
			{
				Pattern pattern = Pattern.compile(regex, Pattern.DOTALL);
				Matcher matcher = pattern.matcher(source);
				matcher.find();
				if (matcher.matches())
				{
					if (matcher.groupCount() >= groupNumber)
					{
						group = matcher.group(groupNumber);
						returnValue = group;
					}
				}
			}
		}
		catch (Exception ex)
		{
			log.error(ex);
		}

		return returnValue;
	}

	public static String getGroup(String regex, int groupNumber, String source, String startMarker, String endMarker,
			String defaultValue)
	{
		String returnValue = defaultValue;
		String mark = source;
		String group = null;

		try
		{
			// ===== startMarker
			if (startMarker != null && startMarker.length() > 0)
			{
				int n = mark.indexOf(startMarker);
				if (n > 0)
				{
					mark = mark.substring(n);
				}
			}

			// ===== endMarker
			if (endMarker != null && endMarker.length() > 0)
			{
				int n = mark.indexOf(endMarker);
				if (n > 0)
				{
					mark = mark.substring(0, n + endMarker.length());
				}
			}

			returnValue = getGroup(regex, groupNumber, mark, defaultValue);
		}
		catch (Exception ex)
		{
			log.error(ex);
		}

		return returnValue;
	}

}
