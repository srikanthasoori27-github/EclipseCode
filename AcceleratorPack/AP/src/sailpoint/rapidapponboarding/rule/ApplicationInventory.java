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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.SailPointContext;
import sailpoint.object.Application;
import sailpoint.object.Configuration;
import sailpoint.object.Filter;
import sailpoint.object.QueryOptions;
import sailpoint.rapidapponboarding.logger.LogEnablement;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
/**
 * Application Inventory Util
 * @author rohit.gupta
 *
 */

public class ApplicationInventory {
	private static Log appLogger = LogFactory.getLog("rapidapponboarding.rules");
	/**
	 * Get Applications Information
	 * @param ctx
	 * @return
	 * @throws GeneralException
	 */
	public static List<HashMap> getAppsInfo(SailPointContext ctx) throws GeneralException {
		List props = new ArrayList();
		props.add("name");
		props.add("type");
		props.add("authoritative");
		props.add("featuresString");
		Filter filter = Filter.notnull("name");
		QueryOptions qo = new QueryOptions();
		qo.addFilter(filter);
		Iterator iter = ctx.search(Application.class, qo, props);
		List masterList = new ArrayList();
		if (iter != null && iter.hasNext()) {
			while (iter.hasNext()) {
				Object[] item = (Object[]) iter.next();
				if (item != null && item.length == 4) {
					Object name =  item[0];
					Object type =  item[1];
					Object authoritative =  item[2];
					Object featureString = item[3];
					HashMap appMap = new HashMap();
					appMap.put("name", name);
					appMap.put("type", type);
					appMap.put("authoritative", authoritative);
					appMap.put("featuresString", featureString);
					masterList.add(appMap);
				}
			}
		}
		return masterList;
	}
	/**
	 *
	 * @param ctx
	 * @param enableScoping
	 * @return
	 * @throws GeneralException
	 */
	public static List getScopedApps(SailPointContext ctx) throws GeneralException {
		LogEnablement.isLogDebugEnabled(appLogger,"Start...getScopedApps " );
		List props = new ArrayList();
		props.add("name");
		Filter filter = Filter.notnull("name");
		QueryOptions qo = new QueryOptions();
		qo.addFilter(filter);
		boolean scopingEnabled=false;
		Configuration sysConfig = Configuration.getSystemConfig();
		scopingEnabled = Util.otob(sysConfig.get(Configuration.SCOPING_ENABLED));
		LogEnablement.isLogDebugEnabled(appLogger,"scopingEnabled..."+scopingEnabled );
		if(scopingEnabled)
		{
			qo.setScopeResults(true);
		}
		Iterator iter = ctx.search(Application.class, qo, props);
		List appList = new ArrayList();
		if (iter != null && iter.hasNext()) {
			while (iter.hasNext()) {
				Object[] item = (Object[]) iter.next();
				if (item != null && item.length == 1) {
					Object name =  item[0];
					appList.add(name);
				}
			}
		}
		LogEnablement.isLogDebugEnabled(appLogger,"End...getScopedApps " );
		return appList;
	}

}
