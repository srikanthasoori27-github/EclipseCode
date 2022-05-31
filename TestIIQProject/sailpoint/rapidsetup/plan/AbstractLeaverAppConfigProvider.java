/*
 * (c) Copyright 2020 SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.rapidsetup.plan;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import sailpoint.object.Identity;
import sailpoint.object.RapidSetupConfigUtils;
import sailpoint.service.listfilter.ListFilterValue;
import sailpoint.tools.Util;

public abstract class AbstractLeaverAppConfigProvider implements LeaverAppConfigProvider {

    public static final String OPT_PASSWORD_ATTR       = "passwordAttribute";
    public static final String OPT_COMMENT_ATTR        = "commentAttribute";
    public static final String OPT_COMMENT_STRING      = "commentString";
    public static final String OPT_MOVE_OU             = "moveOU";
    public static final String OPT_ENTITLEMENT_EXCPTN  = "entitlementException";
    public static final String OPT_ENTITLEMENT_EXCPTN_OPERATION = "operation";
    public static final String OPT_ENTITLEMENT_EXCPTN_PROPERTY = "property";
    public static final String OPT_ENTITLEMENT_EXCPTN_VALUE_ID = "id";
    public static final String OPT_ENTITLEMENT_EXCPTN_VALUE = "value";
    public static final String OPT_ENTITLEMENT_DELAY   = "daysToDelay";
    public static final String OPT_USE_DEFAULT_FOR_BREAKGLASS = "useDefaultForBreakGlass";
    public static final String OPT_DELETE_ACCT_DELAY        = "deleteAccountDelayDays";
    public static final String OPT_LEAVER_CONFIGS_SECTION   = "leaverConfigs";
    public static final String OPT_LEAVER_CONFIG_BREAKGLASS = "breakglass";
    public static final String OPT_LEAVER_CONFIG_NORMAL     = "normal";
    public static final String OPT_PLAN_RULE         = "planRule";
    public static final String OPT_USE_RULE          = "useRule";

    /**
     * @param mode the map to search for the key in
     * @param key the key to determine if it has the specified mode
     * @return true if the value of the given key is equal to the given mode
     */
    public boolean isConfigured(String appName, Identity identity, String mode, String key) {
        Map<String, Object> config = getConfig(appName, identity);

        boolean isConfigured = false;
        if (config != null) {
            String strVal = Util.otos(config.get(key));
            if (Util.isNotNullOrEmpty(strVal)) {
                isConfigured = strVal.equalsIgnoreCase(mode);
            }
        }
        return isConfigured;
    }

    @Override
    public String getPasswordAttribute(String appName, Identity identity) {
        return RapidSetupConfigUtils.getString(getConfig(appName, identity), OPT_PASSWORD_ATTR);
    }

    @Override
    public String getCommentAttribute(String appName, Identity identity) {
        return RapidSetupConfigUtils.getString(getConfig(appName, identity), OPT_COMMENT_ATTR);
    }

    @Override
    public String getCommentString(String appName, Identity identity) {
        return RapidSetupConfigUtils.getString(getConfig(appName, identity), OPT_COMMENT_STRING);
    }

    @Override
    public String getMoveOU(String appName, Identity identity) {
        return RapidSetupConfigUtils.getString(getConfig(appName, identity), OPT_MOVE_OU);
    }

    @Override
    public boolean isEmpty(String appName, Identity identity) {
        return Util.isEmpty(getConfig(appName, identity));
    }

    @SuppressWarnings("unchecked")
    public ArrayList<ListFilterValue> getEntitlementExceptionFilters(String appName, Identity identity) {
        ArrayList<Map> entitlementExceptions = (ArrayList<Map>)getConfig(appName, identity).get(OPT_ENTITLEMENT_EXCPTN);
        ArrayList<ListFilterValue> filterValues = new ArrayList<>();
        if (entitlementExceptions != null) {
            for (Map filterMap : entitlementExceptions) {
                ListFilterValue.Operation op = Enum.valueOf(ListFilterValue.Operation.class, (String) filterMap.get(ListFilterValue.FILTER_MAP_OPERATION));
                String property = (String) filterMap.get(ListFilterValue.FILTER_MAP_PROPERTY);
                ArrayList<String> values = new ArrayList<>();
                // For the equals case the UI is saving the value as list of maps with the value, display name so
                // we need to tease out just the value
                Object value = filterMap.get(ListFilterValue.FILTER_MAP_VALUE);
                if (value instanceof List) {
                    for (Map valueMap : (List<Map>)value) {
                        values.add((String) valueMap.get("id"));
                    }
                    filterValues.add(new ListFilterValue(Util.listToCsv(values), op, property));
                } else {
                    filterValues.add(new ListFilterValue(value, op, property));
                }
            }
        }
        return filterValues;
    }

    @Override
    public int getEntitlementDelayDays(String appName, Identity identity) {
        return RapidSetupConfigUtils.getInt(
                getConfig(appName, identity), OPT_ENTITLEMENT_DELAY);
    }

    @Override
    public int getDeleteDelayDays(String appName, Identity identity) {
        return RapidSetupConfigUtils.getInt(
                getConfig(appName, identity), OPT_DELETE_ACCT_DELAY);
    }

    @SuppressWarnings("unchecked")
    protected abstract Map<String, Object> getConfig(String appName, Identity identity);
}
