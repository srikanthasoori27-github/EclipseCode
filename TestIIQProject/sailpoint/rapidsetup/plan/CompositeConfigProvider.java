/*
 * (c) Copyright 2020 SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.rapidsetup.plan;

import java.util.ArrayList;
import java.util.List;

import sailpoint.object.Identity;
import sailpoint.service.listfilter.ListFilterValue;

public class CompositeConfigProvider implements LeaverAppConfigProvider {

    List<LeaverAppConfigProvider> providers = new ArrayList<LeaverAppConfigProvider>();
    LeaverAppConfigProvider defaultProvider = null;

    public CompositeConfigProvider(LeaverAppConfigProvider defaultConfigProvider) {
        this.defaultProvider = defaultConfigProvider;
    }

    public void add(LeaverAppConfigProvider provider) {
        providers.add(provider);
    }

    @Override
    public boolean isConfigured(String appName, Identity identity, String mode, String key) {
        return getProvider(appName, identity).
                isConfigured(appName, identity, mode, key);
    }

    @Override
    public String getPasswordAttribute(String appName, Identity identity) {
        return getProvider(appName, identity).
                getPasswordAttribute(appName, identity);
    }

    @Override
    public String getCommentAttribute(String appName, Identity identity) {
        return getProvider(appName, identity).
                getCommentAttribute(appName, identity);
    }

    @Override
    public String getCommentString(String appName, Identity identity) {
        return getProvider(appName, identity).
                getCommentString(appName, identity);
    }

    @Override
    public String getMoveOU(String appName, Identity identity) {
        return getProvider(appName, identity).
                getMoveOU(appName, identity);
    }

    @Override
    public ArrayList<ListFilterValue> getEntitlementExceptionFilters(String appName, Identity identity) {
        return getProvider(appName, identity).
                getEntitlementExceptionFilters(appName, identity);
    }

    @Override
    public boolean isEmpty(String appName, Identity identity) {
        return getProvider(appName, identity).
                isEmpty(appName, identity);
    }

    @Override
    public int getEntitlementDelayDays(String appName, Identity identity) {
        return getProvider(appName, identity).
                getEntitlementDelayDays(appName, identity);
    }

    @Override
    public int getDeleteDelayDays(String appName, Identity identity) {
        return getProvider(appName, identity).
                getDeleteDelayDays(appName, identity);
    }

    LeaverAppConfigProvider getProvider(String appName, Identity identity) {
        for(LeaverAppConfigProvider provider : providers) {
            if(!provider.isEmpty(appName, identity)) {
                return provider;
            }
        }

        return defaultProvider;
    }
}
