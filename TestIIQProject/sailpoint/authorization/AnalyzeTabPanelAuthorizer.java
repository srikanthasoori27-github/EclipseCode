/* (c) Copyright 2018 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.authorization;

import sailpoint.object.SPRight;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.web.UserContext;
import sailpoint.web.analyze.AnalyzeControllerBean;
import sailpoint.web.messages.MessageKeys;

/**
 * Authorizer for the advanced analytics tab panels.
 */
public class AnalyzeTabPanelAuthorizer implements Authorizer {
    private String tabPanel;
    private static final String ROLE_ENTITLEMENT_ANALYSIS_PANEL = "entitlementAnalysisPanel";

    /**
     * Constructor
     * @param tabPanel Name of the tab panel to authorize (see AnalyzeControllerBean for most of the supported constants)
     */
    public AnalyzeTabPanelAuthorizer(String tabPanel) {
        this.tabPanel = tabPanel;
    }

    /**
     * Check if user is authorized for given tab panel
     * @param userContext UserContext
     * @param tabPanel Name of the tab panel to authorize
     * @return True if authorized, otherwise false.
     * @throws GeneralException
     */
    public static boolean isAuthorized(UserContext userContext, String tabPanel) throws GeneralException {
        Authorizer subAuthorizer = null;
        switch (tabPanel) {
            case AnalyzeControllerBean.IDENTITY_SEARCH_PANEL:
                subAuthorizer = new RightAuthorizer(SPRight.ViewIdentity,
                        SPRight.SetIdentityPassword,
                        SPRight.SetIdentityCapability,
                        SPRight.DeleteIdentityLink,
                        SPRight.MoveIdentityLink,
                        SPRight.DeleteIdentitySnapshot,
                        SPRight.MonitorIdentityActivity);
                break;
            case AnalyzeControllerBean.CERTIFICATION_SEARCH_PANEL:
                subAuthorizer = new RightAuthorizer(SPRight.ViewCertifications);
                break;
            case AnalyzeControllerBean.ROLE_SEARCH_PANEL:
                subAuthorizer = new RightAuthorizer(SPRight.ViewRole);
                break;
            case AnalyzeControllerBean.ACCOUNT_GROUP_SEARCH_PANEL:
                subAuthorizer = new RightAuthorizer(SPRight.ViewAccountGroups);
                break;
            case AnalyzeControllerBean.ACTIVITY_SEARCH_PANEL:
                subAuthorizer = new RightAuthorizer(SPRight.ViewActivity);
                break;
            case AnalyzeControllerBean.AUDIT_SEARCH_PANEL:
                subAuthorizer = new RightAuthorizer(SPRight.ViewAuditLog);
                break;
            case AnalyzeControllerBean.IDENTITY_REQUEST_SEARCH_PANEL:
                subAuthorizer = CompoundAuthorizer.and(new LcmEnabledAuthorizer(), new RightAuthorizer(SPRight.ViewIdentityRequest));
                break;
            case AnalyzeControllerBean.SYSLOG_SEARCH_PANEL:
                subAuthorizer = new RightAuthorizer(SPRight.ViewSyslog);
                break;
            case AnalyzeControllerBean.LINK_SEARCH_PANEL:
                subAuthorizer = new RightAuthorizer(SPRight.ViewLink);
                break;
            case AnalyzeControllerBean.PROCESS_INSTRUMENTATION_SEARCH_PANEL:
                subAuthorizer = new RightAuthorizer(SPRight.ViewProcessInstrumentation);
                break;
            case ROLE_ENTITLEMENT_ANALYSIS_PANEL:
                subAuthorizer = new RightAuthorizer(SPRight.ViewRole, SPRight.ManageRole);
                break;

        }

        try {
            // If it is null then the tab panel name is unsupported
            if (subAuthorizer == null) {
                return false;
            }
            // Authorize will throw
            subAuthorizer.authorize(userContext);
            return true;
        } catch (UnauthorizedAccessException ex) {
            return false;
        }
    }

    @Override
    public void authorize(UserContext userContext) throws GeneralException {
        if (!isAuthorized(userContext, this.tabPanel)) {
            throw new UnauthorizedAccessException(new Message(MessageKeys.UI_TAB_PANEL_UNAUTHORIZED_ACCESS));
        }
    }
}
