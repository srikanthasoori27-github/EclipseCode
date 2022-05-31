/*
 * (c) Copyright 2020 SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.rapidsetup.plan;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.TreeMap;
import java.util.Map;

import sailpoint.api.SailPointContext;
import sailpoint.object.GroupDefinition;
import sailpoint.tools.GeneralException;

import static sailpoint.rapidsetup.plan.LeaverAppConfigProvider.*;
import static sailpoint.rapidsetup.plan.DefaultLeaverAppConfigProvider.*;

public class LeaverConfigBuilder {
    public static LeaverConfigBuilder forEveryone() {
        return new LeaverConfigBuilder();
    }

    public static LeaverConfigBuilder forPopulation(GroupDefinition groupDefinition)
            throws GeneralException {
        if(groupDefinition == null) {
            throw new GeneralException("GroupDefinition cannot be null");
        }
        return new LeaverConfigBuilder(groupDefinition);
    }

    public static LeaverConfigBuilder forPopulation(SailPointContext ctx, String groupDefinitionName)
            throws GeneralException {
        GroupDefinition groupDefinition = ctx.getObjectByName(GroupDefinition.class, groupDefinitionName);
        if(groupDefinition == null) {
            throw new GeneralException("GroupDefinition '" + groupDefinitionName + "' was not found.");
        }
        return LeaverConfigBuilder.forPopulation(groupDefinition);
    }

    private GroupDefinition groupDefinition;
    private Map<String, Map<String, Object>> appConfigs = new TreeMap<String, Map<String, Object>>(String.CASE_INSENSITIVE_ORDER);

    private LeaverConfigBuilder(GroupDefinition groupDefinition) {
        this.groupDefinition = groupDefinition;
    }

    private LeaverConfigBuilder() {
        this.groupDefinition = null;
    }

    public LeaverConfigBuilder setDisableAccount(String appName, String mode) {
        Map<String, Object> config = getAppConfig(appName);
        config.put(OPT_DISABLE_ACCOUNT, mode);
        appConfigs.put(appName, config);
        return this;
    }

    public LeaverConfigBuilder setScramblePassword(String appName,
                                                   String mode,
                                                   String passwordAttributeName) {
        Map<String, Object> config = getAppConfig(appName);
        config.put(OPT_SCRAMBLE_PASSWORD, mode);
        config.put(OPT_PASSWORD_ATTR, passwordAttributeName);
        appConfigs.put(appName, config);
        return this;
    }

    public LeaverConfigBuilder setComment(String appName,
                                          String mode,
                                          String commentAttributeName,
                                          String commentString) {
        Map<String, Object> config = getAppConfig(appName);
        config.put(OPT_ADD_COMMENT, mode);
        config.put(OPT_COMMENT_ATTR, commentAttributeName);
        config.put(OPT_COMMENT_STRING, commentString);
        appConfigs.put(appName, config);
        return this;
    }

    public LeaverConfigBuilder addEntitlementExceptionFilters(String appName,
                                                              String property,
                                                              String operation,
                                                              Object ... values) {
        Map<String, Object> config = getAppConfig(appName);
        List<Map<String, Object>> exceptions = (List<Map<String, Object>>)config.get(OPT_ENTITLEMENT_EXCPTN);
        if(exceptions == null) {
            exceptions = new ArrayList<Map<String, Object>>();
        }
        Map<String, Object> exception = new HashMap<String, Object>();
        exception.put(OPT_ENTITLEMENT_EXCPTN_OPERATION, operation);
        exception.put(OPT_ENTITLEMENT_EXCPTN_PROPERTY, property);
        List<Map<String, Object>> exceptionValues = new ArrayList<Map<String, Object>>();
        if(values != null) {
            for (Object value : values) {
                Map<String, Object> valueMap = new HashMap<String, Object>();
                valueMap.put(OPT_ENTITLEMENT_EXCPTN_VALUE_ID, value);
                exceptionValues.add(valueMap);
            }
        }
        exception.put(OPT_ENTITLEMENT_EXCPTN_VALUE, exceptionValues);
        exceptions.add(exception);
        config.put(OPT_ENTITLEMENT_EXCPTN, exceptions);
        appConfigs.put(appName, config);
        return this;
    }

    public LeaverConfigBuilder setRemoveEntitlements(String appName,
                                                     String mode) {
        Map<String, Object> config = getAppConfig(appName);
        config.put(OPT_REMOVE_ENTITLEMENTS, mode);
        appConfigs.put(appName, config);
        return this;
    }

    public LeaverConfigBuilder setDeleteAccount(String appName,
                                                String mode) {
        Map<String, Object> config = getAppConfig(appName);
        config.put(OPT_DELETE_ACCOUNT, mode);
        appConfigs.put(appName, config);
        return this;
    }

    public LeaverConfigBuilder setEntitlementDelay(String appName,
                                                   int delayDays) {
        Map<String, Object> config = getAppConfig(appName);
        config.put(OPT_ENTITLEMENT_DELAY, new Integer(delayDays));
        appConfigs.put(appName, config);
        return this;
    }

    public LeaverConfigBuilder setDeleteAccountDelay(String appName,
                                                     int delayDays) {
        Map<String, Object> config = getAppConfig(appName);
        config.put(OPT_DELETE_ACCT_DELAY, new Integer(delayDays));
        appConfigs.put(appName, config);
        return this;
    }

    public LeaverConfigBuilder setMoveAccount(String appName,
                                              String mode,
                                              String moveOU) {
        Map<String, Object> config = getAppConfig(appName);
        config.put(OPT_MOVE_ACCOUNT, mode);
        config.put(OPT_MOVE_OU, moveOU);
        appConfigs.put(appName, config);
        return this;
    }

    private Map<String, Object> getAppConfig(String appName) {
        Map<String, Object> config = appConfigs.get(appName);
        if(config == null) {
            config = new TreeMap<String, Object>(String.CASE_INSENSITIVE_ORDER);
        }

        return config;
    }
    public PopulationLeaverConfigProvider build(SailPointContext ctx) {
        return new PopulationLeaverConfigProvider(ctx, groupDefinition, this.appConfigs);
    }
}
