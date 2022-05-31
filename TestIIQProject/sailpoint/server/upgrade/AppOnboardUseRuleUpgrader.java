/*
 * (c) Copyright 2020 SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.server.upgrade;

import java.util.Map;

import sailpoint.api.SailPointContext;
import sailpoint.object.Configuration;
import sailpoint.object.RapidSetupConfigUtils;
import sailpoint.rapidsetup.plan.AbstractLeaverAppConfigProvider;
import sailpoint.server.upgrade.framework.BaseUpgrader;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

/**
 * IIQMAG-2994
 * This upgrader will look at all the RapidSetup application onboarding leaver configs and set
 * a variable called useRule to true if the planRule is populated and to false if it is not.
 */
public class AppOnboardUseRuleUpgrader extends BaseUpgrader {

    @Override
    public void performUpgrade(Context context) throws GeneralException {
        SailPointContext spContext = context.getContext();
        Configuration cfg = spContext.getObjectByName(Configuration.class, Configuration.RAPIDSETUP_CONFIG);
        int appConfigsUpdated = 0;
        if (cfg == null) {
            info("Rapid Setup config didn't exist so there is nothing to do.");
            return;
        }
        Map<String,Object> appsMap = (Map<String,Object>)RapidSetupConfigUtils.get(cfg, Configuration.RAPIDSETUP_CONFIG_SECTION_APPS);
        if (appsMap == null) {
            info("No apps are set up in the Rapid Setup config so there is nothing to do.");
            return;
        }
        for (String appName : Util.safeIterable(appsMap.keySet())) {
            Map<String,Object> appMap = (Map<String,Object>)appsMap.get(appName);
            if (appMap != null) {
                Map<String, Object> appBizProcs = (Map<String, Object>) appMap.get(Configuration.RAPIDSETUP_CONFIG_SECTION_BUSINESS_PROCESSES);
                if (appBizProcs != null) {
                    Map<String, Object> leaverConfig = (Map<String, Object>) appBizProcs.get(Configuration.RAPIDSETUP_CONFIG_LEAVER);
                    if (leaverConfig != null) {
                        if (leaverConfig.get(AbstractLeaverAppConfigProvider.OPT_USE_RULE) != null) {
                            break; // This shouldn't happen but if the upgrader is run multiple times or accidentally run against
                            // a system that already has the useRule flags we don't want to clobber them
                        }
                        String planRule = RapidSetupConfigUtils.getString(leaverConfig, AbstractLeaverAppConfigProvider.OPT_PLAN_RULE);
                        if (planRule == null) {
                            leaverConfig.put(AbstractLeaverAppConfigProvider.OPT_USE_RULE, false);
                        } else {
                            leaverConfig.put(AbstractLeaverAppConfigProvider.OPT_USE_RULE, true);
                        }
                        appConfigsUpdated++;
                    }
                }
            }
        }
        if (appConfigsUpdated > 0) {
            spContext.saveObject(cfg);
            spContext.commitTransaction();
            info(appConfigsUpdated + " Rapid Setup application onboarding configurations were updated.");
            spContext.decache();
        } else {
            info("No Rapid Setup application onboarding configurations needed updating.");
        }
    }
}
