/*
 * (c) Copyright 2020 SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.rapidsetup.plan;

import java.util.Map;

import sailpoint.object.Configuration;
import sailpoint.object.Identity;
import sailpoint.object.RapidSetupConfigUtils;
import sailpoint.tools.Util;

public class DefaultLeaverAppConfigProvider extends AbstractLeaverAppConfigProvider {

    public String configSection;

    public DefaultLeaverAppConfigProvider(String configSection) {
        this.configSection = configSection;
    }

    @SuppressWarnings("unchecked")
    protected Map<String, Object> getConfig(String appName, Identity identity) {
        Map<String, Object> leaverSection = RapidSetupConfigUtils.getApplicationBusinessProcessConfig(
                appName, Configuration.RAPIDSETUP_CONFIG_LEAVER);
        Map<String, Object> leaverConfig = null;
        if(leaverSection != null) {
            // get the config that was requested
            Map<String, Object> leaverConfigs = (Map<String, Object>)leaverSection.get(OPT_LEAVER_CONFIGS_SECTION);
            if(leaverConfigs != null) {
                leaverConfig = (Map<String, Object>) leaverConfigs.get(configSection);

                // if this is the breakglass config AND they have selected to use
                // the normal config, return the normal config instead.
                if(configSection == OPT_LEAVER_CONFIG_BREAKGLASS) {
                    if(Util.getBoolean(leaverSection, OPT_USE_DEFAULT_FOR_BREAKGLASS)) {
                        leaverConfig = (Map<String, Object>)leaverConfigs.get(OPT_LEAVER_CONFIG_NORMAL);
                    }
                }
            }
        }

        return leaverConfig;
    }
}
