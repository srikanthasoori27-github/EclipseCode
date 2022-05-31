/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.faces.context.FacesContext;

import sailpoint.Version;
import sailpoint.object.Configuration;
import sailpoint.server.Environment;
import sailpoint.service.identitypreferences.IdentityPreferencesService;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.web.MenuBuilder.CurrentLocationContext;
import sailpoint.web.MenuBuilder.DecoratedMenuItem;
import sailpoint.web.MenuBuilder.MenuBuilderContext;
import sailpoint.web.MenuBuilder.MenuItem;
import sailpoint.web.messages.MessageKeys;


/**
 * A JSF bean that supplied the menu and index page content.
 */
public class MenuBean
    extends BaseBean
    implements MenuBuilderContext, CurrentLocationContext {

    //////////////////////////////////////////////////////////////////////
    //
    // MenuItem Constants
    //
    //////////////////////////////////////////////////////////////////////

    //
    // Home
    //

    private static final MenuItem MENU_HOME =
        new MenuItem(MessageKeys.MENU_LABEL_HOME, "home.jsf");
    static {
        MENU_HOME.setNoCollapse(true);
    }

    //
    // My Work
    //

    // accessible to all with assigned certs
    private static final MenuItem ITEM_MY_CERTS =
        new MenuItem(MessageKeys.MENU_LABEL_MY_CERTS,
                     "certification/certification.jsf",
                     MessageKeys.MENU_DESC_MY_CERTS);

    private static final MenuItem ITEM_MY_ACCESS_REQUESTS =
        new MenuItem(MessageKeys.MENU_LABEL_ACCESS_REQUESTS,
                     "identityRequest/identityRequest.jsf",
                     MessageKeys.MENU_DESC_ACCESS_REQUESTS).enableForLCM();

    public static final MenuItem ITEM_POLICY_VIOLATIONS =  
        new MenuItem(MessageKeys.MENU_LABEL_POLICY_VIOLATIONS,
                     "policyViolation/policyViolation.jsf#/policyViolationsAll",
                     MessageKeys.MENU_DESC_POLICY_VIOLATIONS);

    // This menu item needs to know which identity auth attributes
    // enable it's display.
    static {
        Map<String, Object> authAttrs = new HashMap<String, Object>();
        authAttrs.put(LoginBean.ID_AUTH_ATTR_MGR_STATUS, true);
        authAttrs.put(LoginBean.ID_AUTH_ATTR_VIOLATION_OWNER, true);
        ITEM_POLICY_VIOLATIONS.setEnablingAttributes(authAttrs);
    }

    private static final MenuItem ITEM_WORKITEMS =
        new MenuItem(MessageKeys.MENU_LABEL_WORKITEMS,
                     "workitem/workItems.jsf",
                     MessageKeys.MENU_DESC_WORKITEMS);
    
    private static final MenuItem MENU_MY_WORK =
        new MenuItem(MessageKeys.MENU_LABEL_MY_WORK,
                     new MenuItem[] {
                         ITEM_MY_CERTS,
                         ITEM_MY_ACCESS_REQUESTS,
                         ITEM_POLICY_VIOLATIONS,
                         ITEM_WORKITEMS
                     });


    //
    // Identities
    //

    private static final MenuItem ITEM_IDENTITIES =
        new MenuItem(MessageKeys.MENU_LABEL_IDENTITY_WAREHOUSE,
                     "define/identity/identities.jsf",
                     MessageKeys.MENU_DESC_IDENTITIES);

    private static final MenuItem ITEM_IDENTITY_CORRELATION =
        new MenuItem(MessageKeys.MENU_LABEL_ID_CORRELATION,
                     "manage/correlation/manualCorrelation.jsf",
                     MessageKeys.MENU_DESC_ID_CORRELATION);

    private static final MenuItem ITEM_IDENTITY_RISK_MODEL =
        new MenuItem(MessageKeys.MENU_LABEL_ID_RISK_MODEL,
                     "define/riskScores/riskScores.jsf?forceLoad=true",
                     MessageKeys.MENU_DESC_ID_RISK_MODEL);

    private static final MenuItem ITEM_IDENTITY_OPERATIONS =
            new MenuItem(MessageKeys.MENU_LABEL_IDENTITY_OPERATIONS,
                     "identityOperations/identityOperations.jsf#/identityOperations",
                     MessageKeys.MENU_DESC_IDENTITY_OPERATIONS).enableForRapidSetup();

    private static final MenuItem MENU_IDENTITIES =
        new MenuItem(MessageKeys.MENU_LABEL_IDENTITIES,
                     new MenuItem[] {
                         ITEM_IDENTITIES,
                         ITEM_IDENTITY_CORRELATION,
                         ITEM_IDENTITY_RISK_MODEL,
                         ITEM_IDENTITY_OPERATIONS
                     });

    //
    // Applications
    //

    private static final MenuItem ITEM_APPLICATIONS =
        new MenuItem(MessageKeys.MENU_LABEL_APP_DEFINITION,
                     "define/applications/applications.jsf",
                     MessageKeys.MENU_DESC_APPS);

    private static final MenuItem ITEM_APPLICATION_ONBOARD =
        new MenuItem(MessageKeys.MENU_LABEL_APP_ONBOARDING,
                "application/onboard.jsf",
                MessageKeys.MENU_DESC_APP_ONBOARDING).enableForRapidSetup();

    private static final MenuItem ITEM_ENTITLEMENT_CATALOG =
        new MenuItem(MessageKeys.MENU_LABEL_ENTITLEMENT_CATALOG,
                     "define/groups/accountGroups.jsf?forceLoad=true",
                     MessageKeys.MENU_DESC_ENTITLEMENT_CATALOG);

    private static final MenuItem ITEM_APP_RISK_MODEL =
        new MenuItem(MessageKeys.MENU_LABEL_APP_RISK_MODEL,
                     "define/riskScores/appScores.jsf?forceLoad=true",
                     MessageKeys.MENU_DESC_APP_RISK_MODEL);
    
    private static final MenuItem ITEM_ACTIVITY_CATEGORIES =
        new MenuItem(MessageKeys.MENU_LABEL_ACTIVITY_TARGET_CATS,
                     "define/categories/activityCategories.jsf",
                     MessageKeys.MENU_DESC_ACTIVITY_TARGET_CATS);

    private static final MenuItem MENU_APPLICATIONS =
        new MenuItem(MessageKeys.MENU_LABEL_APPLICATIONS,
                     new MenuItem[] {
                         ITEM_APPLICATIONS,
                         ITEM_APPLICATION_ONBOARD,
                         ITEM_ENTITLEMENT_CATALOG,
                         ITEM_APP_RISK_MODEL,
                         ITEM_ACTIVITY_CATEGORIES
                     });
    
 
    //
    // Intelligence
    //

   private static final MenuItem ITEM_ADV_ANALYTICS =
        new MenuItem(MessageKeys.MENU_LABEL_ANALYZE_SEARCH, 
                     "analyze/analyzeTabs.jsf",
                     MessageKeys.MENU_DESC_ANALYZE_SEARCH);

    private static final MenuItem ITEM_REPORTS =
        new MenuItem(MessageKeys.MENU_LABEL_REPORTS,
                     "analyze/reports/viewReports.jsf?resetTab=true&forceLoad=true",
                     MessageKeys.MENU_DESC_REPORTS);

    private static final MenuItem ITEM_IDENTITY_RISK_SCORES =
        new MenuItem(MessageKeys.MENU_LABEL_ID_RISK_SCORES,
                     "manage/riskScores/riskScores.jsf?reset=true",
                     MessageKeys.MENU_DESC_ID_RISK_SCORES);

    private static final MenuItem ITEM_APP_RISK_SCORES =
        new MenuItem(MessageKeys.MENU_LABEL_APP_RISK_SCORES,
                     "manage/riskScores/appRiskScores.jsf",
                     MessageKeys.MENU_DESC_APP_RISK_SCORES);

    private static final MenuItem MENU_INTELLIGENCE =
        new MenuItem(MessageKeys.MENU_LABEL_INTELLIGENCE,
                     new MenuItem[] {
                         ITEM_ADV_ANALYTICS,
                         ITEM_REPORTS,
                         MenuItem.createSeparator(),
                         ITEM_IDENTITY_RISK_SCORES,
                         ITEM_APP_RISK_SCORES
                     });

    //
    // Setup
    //

    private static final MenuItem ITEM_SCHEDULE_CERTS =
        new MenuItem(MessageKeys.MENU_LABEL_CERT_SCHEDULES,
                     "monitor/scheduleCertifications/viewAndEditCertifications.jsf?resetTab=true",
                     MessageKeys.MENU_DESC_CERT_SCHEDULES);

    private static final MenuItem ITEM_ROLES =
        new MenuItem(MessageKeys.MENU_LABEL_ROLES,
                     "define/roles/roleTabs.jsf?forceLoad=true&tabState:tabPanelId=roleTabPanel",
                     MessageKeys.MENU_DESC_ROLES);

    private static final MenuItem ITEM_POLICIES =
        new MenuItem(MessageKeys.MENU_LABEL_POLICIES,
                     "define/policy/policies.jsf?forceLoad=true",
                     MessageKeys.MENU_DESC_POLICIES);

    private static final MenuItem ITEM_ALERTS =
        new MenuItem(MessageKeys.MENU_LABEL_ALERTS,
                    "define/alerts/alerts.jsf?forceLoad=true",
                    MessageKeys.MENU_DESC_ALERTS);

    private static final MenuItem ITEM_TASKS =
        new MenuItem(MessageKeys.MENU_LABEL_TASKS,
                     "monitor/tasks/viewTasks.jsf?resetTab=true",
                     MessageKeys.MENU_DESC_TASKS);

    private static final MenuItem ITEM_GROUPS =
        new MenuItem(MessageKeys.MENU_LABEL_GROUPS,
                     "define/groups/groups.jsf",
                     MessageKeys.MENU_DESC_GROUPS);

    private static final MenuItem ITEM_IDENTITY_TRIGGERS =
        new MenuItem(MessageKeys.MENU_LABEL_IDENTITY_TRIGGERS,
                     "define/trigger/triggers.jsf",
                     MessageKeys.MENU_DESC_IDENTITY_TRIGGERS).enableForLCM();
    
    private static final MenuItem ITEM_WORKFLOWS =
        new MenuItem(MessageKeys.MENU_LABEL_WORKFLOWS,
                     "define/workflow/workflow.jsf?forceLoad=true",
                     MessageKeys.MENU_DESC_WORKFLOWS);

    private static final MenuItem ITEM_BATCH_REQUESTS =
            new MenuItem(MessageKeys.MENU_LABEL_BATCH_REQUESTS,
                         "manage/batchRequest/batchRequests.jsf",
                         MessageKeys.MENU_DESC_BATCH_REQUESTS).enableForLCM();

    private static final MenuItem MENU_SETUP =
        new MenuItem(MessageKeys.MENU_LABEL_SETUP,
                     new MenuItem[] {
                         ITEM_SCHEDULE_CERTS,
                         ITEM_ROLES,
                         ITEM_POLICIES,
                         ITEM_ALERTS,
                         MenuItem.createSeparator(),
                         ITEM_TASKS,
                         ITEM_GROUPS,
                         ITEM_WORKFLOWS,
                         MenuItem.createSeparator(),
                         ITEM_IDENTITY_TRIGGERS,
                         ITEM_BATCH_REQUESTS
                     });

    //
    // Admin
    //

    private static final MenuItem ITEM_IDENTITYIQ_CONFIG =
        new MenuItem(MessageKeys.MENU_LABEL_IDENTITYIQ_CONFIG,
                     "systemSetup/system.jsf?forceLoad=true",
                     MessageKeys.MENU_DESC_SYS_CONFIG);

    private static final MenuItem ITEM_LOGIN_CONFIG =
        new MenuItem(MessageKeys.MENU_LABEL_LOGIN_CONFIG,
                     "systemSetup/loginConfig.jsf?forceLoad=true",
                     MessageKeys.MENU_DESC_LOGIN_CONFIG);

    private static final MenuItem ITEM_IDENTITY_MAPPINGS =
        new MenuItem(MessageKeys.MENU_LABEL_ID_MAPPINGS,
                     "systemSetup/identities/identitySourceMappings.jsf?forceLoad=true",
                     MessageKeys.MENU_DESC_ID_MAPPINGS);

    private static final MenuItem ITEM_ACCOUNT_MAPPINGS =
        new MenuItem(MessageKeys.MENU_LABEL_ACOUNT_MAPPINGS,
                     "systemSetup/accounts/accountSourceMapping.jsf?forceLoad=true",
                     MessageKeys.MENU_DESC_ACOUNT_MAPPINGS);

    private static final MenuItem ITEM_APPLICATION_ONBOARDING_CONFIG =
            new MenuItem(MessageKeys.MENU_LABEL_APP_ONBOARDING_CONFIG,
                    "systemSetup/onboardSettings.jsf",
                    MessageKeys.MENU_DESC_APP_ONBOARDING_CONFIG).enableForRapidSetup();

    private static final MenuItem ITEM_APPLICATION_CONFIG =
        new MenuItem(MessageKeys.MENU_LABEL_APPLICATION_CONFIG,
                     "systemSetup/appObjectConfig.jsf?forceLoad=true",
                     MessageKeys.MENU_DESC_APPLICATION_CONFIG);

    private static final MenuItem ITEM_MANAGED_ATTRIBUTE_CONFIG =
        new MenuItem(MessageKeys.MENU_LABEL_MANAGED_ATTRIBUTE_CONFIG,
                     "systemSetup/managedAttributeConfig.jsf?forceLoad=true",
                     MessageKeys.MENU_DESC_MANAGED_ATTRIBUTE_CONFIG);

    private static final MenuItem ITEM_FORMS =
        new MenuItem(MessageKeys.MENU_LABEL_FORMS,
                     "systemSetup/forms.jsf?forceLoad=true",
                     MessageKeys.MENU_DESC_FORMS);

    private static final MenuItem ITEM_ROLE_CONFIG =
        new MenuItem(MessageKeys.MENU_LABEL_ROLE_CONFIG,
                     "systemSetup/roleObjectConfig.jsf?forceLoad=true",
                     MessageKeys.MENU_DESC_ROLE_CONFIG);
    
    private static final MenuItem ITEM_QUICK_LINK_POPULATIONS =
            new MenuItem(MessageKeys.MENU_LABEL_QUICK_LINK_POPULATIONS,
                         "systemSetup/quicklinkPopulationsEditor.jsf?forceLoad=true",
                         MessageKeys.MENU_DESC_QUICK_LINK_POPULATIONS);

    private static final MenuItem ITEM_SCOPES =
        new MenuItem(MessageKeys.MENU_LABEL_SCOPES,
                     "systemSetup/scopes/scopes.jsf?forceLoad=true",
                     MessageKeys.MENU_DESC_SCOPES);

    private static final MenuItem ITEM_TIME_PERIODS =
        new MenuItem(MessageKeys.MENU_LABEL_TIME_PERIODS,
                     "systemSetup/timePeriods/timePeriods.jsf?forceLoad=true",
                     MessageKeys.MENU_DESC_TIME_PERIODS);

    private static final MenuItem ITEM_AUDIT_CONFIG =
        new MenuItem(MessageKeys.MENU_LABEL_AUDIT_CONFIG,
                     "systemSetup/audit.jsf?forceLoad=true",
                     MessageKeys.MENU_DESC_AUDIT_CONFIG);

    private static final MenuItem ITEM_E_SIG_CONFIG =
        new MenuItem(MessageKeys.MENU_LABEL_E_SIG_CONFIG,
                     "systemSetup/electronicSignatures.jsf?forceLoad=true",
                     MessageKeys.MENU_DESC_E_SIG_CONFIG);
    
    private static final MenuItem ITEM_OAUTH_CONFIG =
            new MenuItem(MessageKeys.MENU_LABEL_OAUTH_CONFIG,
                    "systemSetup/apiAuthentication.jsf",
                    MessageKeys.MENU_DESC_OAUTH_CONFIG);

    private static final MenuItem IAI_CONFIG =
        new MenuItem(MessageKeys.MENU_LABEL_IAI_CONFIG,
                     "systemSetup/iaiConfig.jsf?forceLoad=true",
                     MessageKeys.MENU_DESC_IAI_CONFIG).enableforIdentityAI();

    private static final MenuItem FAM_CONFIG =
            new MenuItem(MessageKeys.MENU_LABEL_FAM_CONFIG,
                         MenuBuilder.FAM_CONFIG_PATH,
                         MessageKeys.MENU_DESC_FAM_CONFIG).enableForFAM();

    private static final MenuItem ITEM_IMPORT =
            new MenuItem(MessageKeys.MENU_LABEL_IMPORT,
                    "systemSetup/import.jsf?forceLoad=true",
                    MessageKeys.MENU_DESC_IMPORT);

    private static final MenuItem SUB_MENU_ADMIN_IIQ =
            new MenuItem(MessageKeys.MENU_LABEL_GLOBAL_SETTINGS,
                         "systemSetup/index.jsf",
                         new MenuItem[] {
                             ITEM_IDENTITYIQ_CONFIG,
                             ITEM_LOGIN_CONFIG,
                             ITEM_IDENTITY_MAPPINGS,
                             ITEM_ACCOUNT_MAPPINGS,
                             ITEM_APPLICATION_ONBOARDING_CONFIG,
                             ITEM_APPLICATION_CONFIG,
                             ITEM_MANAGED_ATTRIBUTE_CONFIG,
                             ITEM_QUICK_LINK_POPULATIONS,
                             ITEM_FORMS,
                             ITEM_ROLE_CONFIG,
                             ITEM_SCOPES,
                             ITEM_TIME_PERIODS,
                             ITEM_AUDIT_CONFIG,
                             ITEM_E_SIG_CONFIG,
                             ITEM_OAUTH_CONFIG,
                             IAI_CONFIG,
                             FAM_CONFIG,
                             ITEM_IMPORT
                         });

    private static final MenuItem SUB_MENU_ADMIN_LCM =
            new MenuItem(MessageKeys.LIFECYCLE_MANAGER,
                    "systemSetup/lcm/lcmConfig.jsf?forceLoad=true").enableForLCM();

    private static final MenuItem SUB_MENU_ADMIN_COMPLIANCE =
            new MenuItem(MessageKeys.COMPLIANCE_MANAGER,
                    "systemSetup/systemCompliance.jsf?forceLoad=true");

    private static final MenuItem SUB_MENU_ADMIN_CONSOLE =
            new MenuItem(MessageKeys.ADMINISTRATOR_CONSOLE,
                    "adminConsole/index.jsf");

    private static final MenuItem SUB_MENU_PLUGINS =
            new MenuItem(MessageKeys.PLUGINS,
                    "plugins/plugins.jsf").enableForPlugins();

    private static final MenuItem ITEM_DEBUG_ABOUT =
        MenuItem.createDebugMenuItem("About", "debug/about.jsf", "");

    private static final MenuItem ITEM_DEBUG_OBJECT =
        MenuItem.createDebugMenuItem("Object", "debug/debug.jsf", "");

    private static final MenuItem ITEM_DEBUG_MEMORY =
        MenuItem.createDebugMenuItem("Memory", "debug/memory.jsf", "");

    private static final MenuItem ITEM_DEBUG_CACHES =
        MenuItem.createDebugMenuItem("Caches", "debug/caches.jsf", "");

    private static final MenuItem ITEM_DEBUG_BEANS =
        MenuItem.createDebugMenuItem("Beans", "debug/beans.jsf", "");

    private static final MenuItem ITEM_DEBUG_THREADS =
        MenuItem.createDebugMenuItem("Threads", "debug/threads.jsf", "");

    private static final MenuItem ITEM_DEBUG_METERS =
        MenuItem.createDebugMenuItem("Call Timings", "debug/metersGrid.jsf", "");
    
    private static final MenuItem ITEM_DEBUG_LOGGING =
        MenuItem.createDebugMenuItem("Logging", "debug/logging.jsf", "");

    private static final MenuItem ITEM_DEBUG_DATABASE =
        MenuItem.createDebugMenuItem("Database", "debug/database.jsf", "");

    private static final MenuItem ITEM_DEBUG_CONNECTIONS =
        MenuItem.createDebugMenuItem("Connections", "debug/connections.jsf", "");

    private static final MenuItem MENU_ADMIN_DEBUG =
            new MenuItem(null,
                         "debug/debug.jsf",
                         new MenuItem[] {
                             ITEM_DEBUG_ABOUT,
                             ITEM_DEBUG_OBJECT,
                             ITEM_DEBUG_MEMORY,
                             ITEM_DEBUG_CACHES,
                             ITEM_DEBUG_BEANS,
                             ITEM_DEBUG_THREADS,
                             ITEM_DEBUG_METERS,
                             ITEM_DEBUG_LOGGING,
                             ITEM_DEBUG_DATABASE,
                             ITEM_DEBUG_CONNECTIONS
                         });

    private static final MenuItem MENU_ADMIN =
            new MenuItem(null,
                    new MenuItem[] {
                            SUB_MENU_ADMIN_IIQ,
                            SUB_MENU_ADMIN_LCM,
                            SUB_MENU_ADMIN_COMPLIANCE,
                            SUB_MENU_ADMIN_CONSOLE,
                            SUB_MENU_PLUGINS
                    });
    
    //
    // FAM MenuItems
    //
    private static final MenuItem ITEM_FAM_DASHBOARD =
            new MenuItem(MessageKeys.MENU_LABEL_FAM_DASHBOARD,
                    "dashboard/admin-dashboard",
                    MessageKeys.MENU_DESC_FAM_DASHBOARD).enableForFAM();

    private static final MenuItem ITEM_FAM_ACTIVITY =
            new MenuItem(MessageKeys.MENU_LABEL_FAM_ACTIVITY,
                    "forensics/activities",
                    MessageKeys.MENU_DESC_FAM_ACTIVITY).enableForFAM();

    private static final MenuItem ITEM_FAM_DATA_CLASSIFICATION =
            new MenuItem(MessageKeys.MENU_LABEL_FAM_DATA_CLASSIFICATION,
                    "compliance/data-classification/dc-policies",
                    MessageKeys.MENU_DESC_FAM_DATA_CLASSIFICATION).enableForFAM();

    private static final MenuItem ITEM_FAM_OWNER_ELECTION =
            new MenuItem(MessageKeys.MENU_LABEL_FAM_OWNER_ELECTION,
                    "tasks/owners-election/overview",
                    MessageKeys.MENU_DESC_FAM_OWNER_ELECTION).enableForFAM();

    private static final MenuItem ITEM_FAM_RESOURCES =
            new MenuItem(MessageKeys.MENU_LABEL_FAM_RESOURCES,
                    "resources",
                    MessageKeys.MENU_DESC_FAM_RESOURCES).enableForFAM();
 
    private static final MenuItem ITEM_FAM_REPORTS =
            new MenuItem(MessageKeys.MENU_LABEL_FAM_REPORTS,
                    "reports/my-reports",
                    MessageKeys.MENU_DESC_FAM_REPORTS).enableForFAM();

    private static final MenuItem ITEM_FAM_TASKS =
            new MenuItem(MessageKeys.MENU_LABEL_FAM_TASKS,
                    "tasks/access-certification/overview",
                    MessageKeys.MENU_DESC_FAM_TASKS).enableForFAM();

    private static final MenuItem MENU_FAM =
            new MenuItem(MessageKeys.MENU_LABEL_FAM,
                         new MenuItem[] {
                             ITEM_FAM_DASHBOARD,
                             ITEM_FAM_ACTIVITY,
                             ITEM_FAM_DATA_CLASSIFICATION,
                             ITEM_FAM_OWNER_ELECTION,
                             ITEM_FAM_RESOURCES,
                             ITEM_FAM_REPORTS,
                             ITEM_FAM_TASKS
                         });

    static {
        MENU_ADMIN_DEBUG.setDebug(true);
        MENU_ADMIN_DEBUG.setRightAligned(true);
        MENU_ADMIN_DEBUG.setCssClass("fa-wrench");
        MENU_ADMIN_DEBUG.setAriaLabel(MessageKeys.MENU_LABEL_DEBUG);

        MENU_ADMIN.setRightAligned(true);
        MENU_ADMIN.setCssClass("fa-cog");
        MENU_ADMIN.setAriaLabel(MessageKeys.MENU_ADMIN_ARIA_LABEL);
    }

    //
    // Root
    //

    private static final MenuItem MENU_ROOT =
        new MenuItem(null,
                     new MenuItem[] {
                         MENU_HOME,
                         MENU_MY_WORK,
                         MENU_IDENTITIES,
                         MENU_APPLICATIONS,
                         MENU_INTELLIGENCE,
                         MENU_FAM,
                         MENU_SETUP,
                         MENU_ADMIN_DEBUG,
                         MENU_ADMIN
                     });

    //////////////////////////////////////////////////////////////////////
    //
    // MenuBean
    //
    //////////////////////////////////////////////////////////////////////

    private MenuBuilder builder;

    // Cached objects.
    private DecoratedMenuItem root;
    private List<DecoratedMenuItem> adminIndexPageItems;


    /**
     * Return the decorated root menu.
     */
    public DecoratedMenuItem getRoot() throws GeneralException {
        // Seems like opportunity to save this on the HttpSession!!
        if (null == this.root) {
            this.root = new DecoratedMenuItem(this, getMenuBuilder().getCompiledRoot());
        }
        return this.root;
    }

    /**
     * Return the MenuItems to display on the admin index page, or an empty list if
     * the user is not authorized to any of these.
     */
    public List<DecoratedMenuItem> getAdminIndexPageItems() throws GeneralException {
        if (null == this.adminIndexPageItems) {
            this.adminIndexPageItems = new ArrayList<DecoratedMenuItem>();
            List<MenuItem> items = getMenuBuilder().getIndexPageItems(MENU_ADMIN, Arrays.asList(SUB_MENU_ADMIN_LCM, SUB_MENU_ADMIN_COMPLIANCE, SUB_MENU_ADMIN_CONSOLE, SUB_MENU_PLUGINS));

            if (null != items) {
                for (MenuItem item : items) {
                    this.adminIndexPageItems.add(new DecoratedMenuItem(this, item));
                }
            }
        }
        return this.adminIndexPageItems;
    }

    private MenuBuilder getMenuBuilder() throws GeneralException {
        if (null == this.builder) {
            this.builder = new MenuBuilder(this);
            this.builder.compileRoot(MENU_ROOT);
        }

        return this.builder;
    }


    ////////////////////////////////////////////////////////////////////////////////////////////////
    //
    // MenuBuilderContext interface
    //
    ////////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public Authorizer getAuthorizer() {
        return Authorizer.getInstance();
    }

    @Override
    public Configuration getConfig(String configAlias) throws GeneralException {
        Configuration cfg = null;
        if (Util.isNullOrEmpty(configAlias)) {
            // null alias, default to system config
            cfg = getContext().getConfiguration();
        }
        else if ("system".equals(configAlias)) {
            cfg = getContext().getConfiguration();
        }
        else if ("rapidSetup".equals(configAlias)) {
            cfg = Configuration.getRapidSetupConfig();
        }
        else {
            // unknown alias ,return null
            cfg = null;
        }
        return cfg;
    }

    /**
     * Return true if we're in a debugging environment.
     * Used to decide if we should expose menus marked for debugging.
     * True if the system property "sailpoint.debugPages" is set
     * or if we're already in the /debug url.
     */
    @Override
    public boolean isDebugEnabled() {
        return System.getProperty("sailpoint.debugPages") != null ||
                FacesContext.getCurrentInstance().getExternalContext().
                getRequestServletPath().startsWith("/debug/");
    }

    @Override
    public boolean isHiddenEnabled() {
        return System.getProperty("sailpoint.hiddenPages") != null;
    }

    @Override
    public boolean isPluginsEnabled() {
        return Environment.getEnvironment().getPluginsConfiguration().isEnabled();
    }

    public boolean isPreferencesEnabled() throws GeneralException {
        IdentityPreferencesService preferencesService = new IdentityPreferencesService(getContext(),
                this.getLoggedInUser());

        return preferencesService.isPreferencesEnabled(this.isSsoAuthenticated());
    }

    /**
     * @return true if IdentityAI is enabled
     */
    @Override
    public boolean isIdentityAIEnabled() {
        return Version.isIdentityAIEnabled();
    }

    /**
     * @return true if disable notifications is allowed in system configuration, and disableNotification is set to true in user preferences.
     */
    public boolean isNotificationsDisabled() throws GeneralException {
        boolean allowDisableNotifications = getConfig(null).getBoolean(Configuration.ALLOW_DISABLE_NOTIFICATIONS, true);
        return allowDisableNotifications && sailpoint.integration.Util.getBoolean(this.getLoggedInUser().getPreferences(), IdentityPreferencesService.DISABLE_NOTIFICATIONS);
    }

}
