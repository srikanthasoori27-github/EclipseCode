package sailpoint.web.ui;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.dashboard.DashboardManager;
import sailpoint.object.Configuration;
import sailpoint.object.Identity;
import sailpoint.service.LCMConfigService;
import sailpoint.tools.GeneralException;
import sailpoint.tools.JsonHelper;
import sailpoint.web.BaseBean;


/**
 * A lighter weight dashboard bean for bootstrapping responsive UI.
 */
public class DashboardBean extends BaseBean {

    private static final Log log = LogFactory.getLog(DashboardBean.class);

    private Identity currentUser;
    private DashboardManager dashboardManager;

    //////////////////////////////////////////////////////////////////////
    //
    // Initialization
    //
    //////////////////////////////////////////////////////////////////////

    public DashboardBean() {
        super();

        this.init();
    }

    /**
     * Initialize bean.
     */
    private void init() {
        try {
            currentUser = getLoggedInUser();
            dashboardManager = new DashboardManager(getContext(), currentUser, getSessionScope());
        } 
        catch (GeneralException ge) {
            log.error("General Exception encountered during init: " + ge.getMessage());
        }
    }

    /**
     * Returns dashboard card data as a JSON string
     *
     * @return JSON string representation of dashboard card data
     */
    public String getDashboardCardsJSON() throws GeneralException {
        return JsonHelper.toJson(dashboardManager.getDashboardCards());
    }

    /**
     * Returns whether or not logged in user is allowed to make access requests for others.
     *
     * @return true if user can make access requests for others
     */
    public boolean isAllowRequestForOthers() throws GeneralException {
        return dashboardManager.isAllowRequestForOthers();
    }

    /**
     * Returns whether or not logged in user is allowed to make access requests for self.
     *
     * @return true if user can make access requests for others
     */
    public boolean isAllowRequestForSelf() throws GeneralException {
        return dashboardManager.isAllowRequestForSelf();
    }

    /**
     * Returns if requests should use sunrise/sunset dates.
     *
     * @throws GeneralException
     * @return boolean
     */
    public boolean isUseSunriseDates() throws GeneralException {
        return dashboardManager.isUseSunriseDates();
    }

    /**
     * Returns whether the current user is allowed to do a population based search on the request access
     * flow
     * @return boolean
     * @throws GeneralException
     */
    public boolean isAllowPopulationSearch() throws GeneralException {
        return isAllowSearch(Configuration.LCM_ALLOW_POPULATION_SEARCH);
    }

    /**
     * Returns whether the user is allowed to do an identities search on the request access flow
     * @return boolean
     * @throws GeneralException
     */
    public boolean isAllowIdentitiesSearch() throws GeneralException {
        return isAllowSearch(Configuration.LCM_ALLOW_IDENTITY_SEARCH);
    }

    /**
     * Utility function to check if the user has access to a type of request in the request access flow
     * @param cfgKey
     * @return boolean
     * @throws GeneralException
     */
    public boolean isAllowSearch(String cfgKey) throws GeneralException {
        // First, check system setting.
        boolean allow =
                getContext().getConfiguration().getBoolean(cfgKey, true);

        // If allowed globally, only allow if the user can request for others
        // since population searching can only happen over people you can
        // request for.
        if (allow) {
            LCMConfigService svc = new LCMConfigService(getContext());
            allow = svc.canRequestForOthers(getLoggedInUser(), getLoggedInUserDynamicScopeNames());
        }

        return allow;
    }
}
