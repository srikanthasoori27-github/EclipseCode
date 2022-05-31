/* (c) Copyright 2015 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.service.useraccess;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import sailpoint.object.QuickLink;
import sailpoint.service.quicklink.QuickLinkCard;
import sailpoint.service.quicklink.QuickLinksService;
import sailpoint.tools.GeneralException;
import sailpoint.tools.JsonHelper;
import sailpoint.tools.Util;
import sailpoint.web.UserContext;


/**
 * A simple service that is used to retrieve configuration information about access requests.
 */
public class UserAccessConfigService {

    static String ALLOW_BULK = "allowBulk";
    static String ALLOW_SELF = "allowSelf";
    static String ALLOW_OTHERS = "allowOthers";

    static String REQUEST_ACCESS_DEFAULT = "actionRequestAccessDefault";
    /**
     * Return a map containing all applicable quickLinks for the given User with the configurations for each
     *
     * @param  context  The UserContext.
     *
     * @return Serialized JSON of the map containing quickLinks and their options
     */
    public static String getUserAccessConfigs(UserContext context) throws GeneralException {

        Map<String, Object> qlOptions = new HashMap<String, Object>();
        QuickLinksService qls = new QuickLinksService(context.getContext(), context.getLoggedInUser(),
                context.getLoggedInUserDynamicScopeNames());

        List<QuickLinkCard> cards = qls.getQuickLinkCards();

        for (QuickLinkCard c : Util.safeIterable(cards)) {
            Map<String, Object> ops = new HashMap<String, Object>();
            ops.put(ALLOW_BULK, c.isAllowBulk());
            ops.put(ALLOW_OTHERS, c.isAllowOthers());
            ops.put(ALLOW_SELF, c.isAllowSelf());
            qlOptions.put(c.getName(), ops);
        }

        //If a quickLink isn't specified, we will fall back to using action. Fetch the cards for the RequestAccess
        //action to use as a default
        List<QuickLinkCard> defaultCards = qls.getQuickLinkCardsForAction(QuickLink.LCM_ACTION_REQUEST_ACCESS);
        if (!Util.isEmpty(defaultCards)) {
            Map<String, Object> ops = new HashMap<String, Object>();
            for (QuickLinkCard dfltCard : defaultCards) {
                ops.put(ALLOW_BULK, Util.getBoolean(ops, ALLOW_BULK) || dfltCard.isAllowBulk());
                ops.put(ALLOW_OTHERS, Util.getBoolean(ops, ALLOW_OTHERS) || dfltCard.isAllowOthers());
                ops.put(ALLOW_SELF, Util.getBoolean(ops, ALLOW_SELF) || dfltCard.isAllowSelf());
            }
            qlOptions.put(REQUEST_ACCESS_DEFAULT, ops);
         }

        return JsonHelper.toJson(qlOptions);
    }

}
