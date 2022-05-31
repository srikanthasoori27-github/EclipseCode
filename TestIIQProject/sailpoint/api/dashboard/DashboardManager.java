/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.api.dashboard;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.SailPointContext;
import sailpoint.object.Configuration;
import sailpoint.object.Identity;
import sailpoint.service.IdentityResetService.Consts.SessionAttributes;
import sailpoint.service.quicklink.QuickLinkWrapper;
import sailpoint.service.quicklink.QuickLinksService;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.web.ui.DashboardCard;

/**
 * @author peter.holcomb
 *
 */
public class DashboardManager {

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    private static Log log = LogFactory.getLog(DashboardManager.class);

    /**
     * This list contains the contentName of DashboardContent that still exists
     * in the database, but should be removed from dashboards when they're loaded.
     * Content listed here will be filtered from the "Edit Dashboard" page as well.
     */
    private static final List<String> OBSOLETE_CONTENT_NAMES = Arrays.asList(
        "My Subordinates"
    );

    /**
     * The context providing persistence services.
     */
    SailPointContext _context;
    Identity _user;
    List<String> _dynamicScopeNames;

    QuickLinksService _quickLinksService;

    boolean _updated;

    private Boolean allowRequestForOthers;
    private Boolean allowRequestForSelf;

    //////////////////////////////////////////////////////////////////////
    //
    // Constructor
    //
    //////////////////////////////////////////////////////////////////////
    /**
     * 
     */
    @SuppressWarnings({ "unchecked" })
    public DashboardManager(SailPointContext con, Identity user, Map<String,Object> sessionScope) {
        _context = con;
        _user = user;
        _dynamicScopeNames = (List<String>)sessionScope.get(SessionAttributes.ATT_DYNAMIC_SCOPES.value());
        _quickLinksService = new QuickLinksService(con, user, sessionScope);
    }


    //////////////////////////////////////////////////////////////////////
    //
    // Angular UI dashboard methods
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * @return the list of dashboard cards
     */
    public List<DashboardCard> getDashboardCards() throws GeneralException {
        List<DashboardCard> cards = new ArrayList<DashboardCard>();

        DashboardCard approvalsCard = getApprovalsCard();
        if (approvalsCard != null) {
            cards.add(approvalsCard);
        }

        DashboardCard manageAccessCard = getManageAccessCard();
        if (manageAccessCard != null) {
            cards.add(manageAccessCard);
        }

        return cards;
    }

    /**
     * @return manage access card or null if card cannot be defined.
     */
    private DashboardCard getManageAccessCard() throws GeneralException {
        final String MANAGE_ACCESS_CARD_ID = "manageAccessCard";
        final String ALLOW_FOR_OTHERS_ATTR = "allowRequestForOthers";
        final String ALLOW_FOR_SELF_ATTR = "allowRequestForSelf";

        DashboardCard card = null;

        if (allowRequestForOthers == null && allowRequestForSelf == null) {
            initAllowRequestFlags();
        }

        if (isAllowRequestForOthers() || isAllowRequestForSelf()) {
            Map<String, Object> attrs = new HashMap<String, Object>();
            attrs.put(ALLOW_FOR_OTHERS_ATTR, isAllowRequestForOthers());
            attrs.put(ALLOW_FOR_SELF_ATTR, isAllowRequestForSelf());
            card = new DashboardCard(MANAGE_ACCESS_CARD_ID, attrs);
        }

        return card;
    }

    /**
     * @return approvals card or null if card cannot be defined.
     */
    private DashboardCard getApprovalsCard() {
        final String APPROVALS_CARD_ID = "approvalsCard";
        return new DashboardCard(APPROVALS_CARD_ID, Collections.<String, Object>emptyMap());
    }

    /**
     * Initialize allow request for self/others flags
     */
    private void initAllowRequestFlags() throws GeneralException {
        final String REQUEST_ACCESS_ACTION = "requestAccess";

        Map<String, List<QuickLinkWrapper>> qlMap = _quickLinksService.getQuickLinks();
        if (!Util.isEmpty(qlMap)) {
            for (List<QuickLinkWrapper> quickLinks : qlMap.values()) {
                for (QuickLinkWrapper wrapper : Util.iterate(quickLinks)) {
                    if (wrapper.getQuickLink() != null && REQUEST_ACCESS_ACTION.equals(wrapper.getQuickLink().getAction())) {
                        allowRequestForOthers = wrapper.isAllowOthers();
                        allowRequestForSelf = wrapper.isAllowSelf();
                        break;
                    }
                }
            }
        }
        
        if (allowRequestForOthers == null) {
            allowRequestForOthers = false;
        }
        if (allowRequestForSelf == null) {
            allowRequestForSelf = false;
        }
    }

    /**
     * Return whether or not current user is allowed to make access requests for others.
     *
     * @return
     */
    public boolean isAllowRequestForOthers() throws GeneralException {
        if (allowRequestForOthers == null) {
            initAllowRequestFlags();
        }
        return allowRequestForOthers == null ? false : allowRequestForOthers;
    }

    /**
     * Return whether or not current user is allowed to make access requests for self.
     *
     * @return
     */
    public boolean isAllowRequestForSelf() throws GeneralException {
        if (allowRequestForSelf == null) {
            initAllowRequestFlags();
        }
        return allowRequestForSelf == null ? false : allowRequestForSelf;
    }

    /**
     * Returns whether the Configuration.ENABLE_ROLE_SUN_ASSIGNMENT flag is true or false.
     *
     * @throws GeneralException
     * @return boolean
     */
    public boolean isUseSunriseDates() throws GeneralException {
        return _context.getConfiguration().getBoolean(Configuration.ENABLE_ROLE_SUN_ASSIGNMENT);
    }
}
