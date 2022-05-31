/*
 * (c) Copyright 2020 SailPoint Technologies, Inc., All Rights Reserved.
 */


package sailpoint.rapidsetup.plan;

import java.util.ArrayList;

import sailpoint.object.Identity;
import sailpoint.service.listfilter.ListFilterValue;

public interface LeaverAppConfigProvider {
    public static final String OPT_REMOVE_ENTITLEMENTS = "removeEntitlements";
    public static final String OPT_SCRAMBLE_PASSWORD   = "scramblePassword";
    public static final String OPT_ADD_COMMENT         = "addComment";
    public static final String OPT_DISABLE_ACCOUNT     = "disableAccount";
    public static final String OPT_DELETE_ACCOUNT      = "deleteAccount";
    public static final String OPT_MOVE_ACCOUNT        = "moveAccount";

    public static final String OPT_MODE_LATER          = "later";
    public static final String OPT_MODE_IMMEDIATE      = "immediate";



    /**
     * @param mode the map to search for the key in
     * @param key the key to determine if it has the specified mode
     * @return true if the value of the given key is equal to the given mode
     */
    public boolean isConfigured(String appName, Identity identity, String mode, String key);
    public String getPasswordAttribute(String appName, Identity identity);
    public String getCommentAttribute(String appName, Identity identity);
    public String getCommentString(String appName, Identity identity);
    public String getMoveOU(String appName, Identity identity);
    public ArrayList<ListFilterValue> getEntitlementExceptionFilters(String appName, Identity identity);
    public boolean isEmpty(String appName, Identity identity);
    public int getEntitlementDelayDays(String appName, Identity identity);
    public int getDeleteDelayDays(String appName, Identity identity);
}
