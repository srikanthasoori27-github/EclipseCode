/*
 * (c) Copyright 2020 SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.rapidsetup.plan;

import java.util.Collections;
import java.util.TreeMap;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.Matchmaker;
import sailpoint.api.SailPointContext;
import sailpoint.object.GroupDefinition;
import sailpoint.object.Identity;
import sailpoint.object.IdentitySelector;
import sailpoint.tools.GeneralException;

public class PopulationLeaverConfigProvider extends AbstractLeaverAppConfigProvider {

    private Matchmaker matchmaker;
    private IdentitySelector selector;
    private Map<String, Map<String, Object>> appConfigs = new TreeMap<String, Map<String, Object>>(String.CASE_INSENSITIVE_ORDER);

    private static Log log = LogFactory.getLog(PopulationLeaverConfigProvider.class);

    PopulationLeaverConfigProvider(SailPointContext ctx,
                                   GroupDefinition population,
                                   Map<String, Map<String, Object>>appConfigs) {
        if(population != null) {
            this.selector = new IdentitySelector();
            this.selector.setPopulation(population);
            this.matchmaker = new Matchmaker(ctx);
        }
        this.appConfigs.putAll(appConfigs);
    }

    @Override
    protected Map<String, Object> getConfig(String appName, Identity identity) {
        Map<String, Object> appConfig = null;
        try {
            if ((this.selector == null) || (matchmaker.isMatch(selector, identity))) {
                appConfig  = appConfigs.get(appName);
            }
        } catch (GeneralException ex) {
            log.error("Error matching identity to population", ex);
        }
        return appConfig == null ? Collections.emptyMap() : appConfig;
    }
}
