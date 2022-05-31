/*
 * (c) Copyright 2019 SailPoint Technologies, Inc., All Rights Reserved.
 */
package sailpoint.rapidsetup.plan;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.IdentityService;
import sailpoint.api.ManagedAttributer;
import sailpoint.api.PasswordGenerator;
import sailpoint.api.SailPointContext;
import sailpoint.object.Application;
import sailpoint.object.Attributes;
import sailpoint.object.Bundle;
import sailpoint.object.Configuration;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.IdentityEntitlement;
import sailpoint.object.Link;
import sailpoint.object.ManagedAttribute;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.ProvisioningPlan.AccountRequest;
import sailpoint.object.ProvisioningPlan.AttributeRequest;
import sailpoint.object.ProvisioningPlan.GenericRequest;
import sailpoint.object.ProvisioningPlan.PermissionRequest;
import sailpoint.object.QueryOptions;
import sailpoint.object.RapidSetupConfigUtils;
import sailpoint.object.Rule;
import sailpoint.rapidsetup.tools.LdapTools;
import sailpoint.rapidsetup.tools.LinkAndEntitlementTools;
import sailpoint.service.listfilter.ListFilterValue;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.workflow.IdentityLibrary;

public class LeaverPlanBuilder extends BasePlanBuilder {
    private static Log log = LogFactory.getLog(LeaverPlanBuilder.class);

    private final SailPointContext context;
    private final String identityName;
    private final Map<String, Object> additionalArgs;
    private final LeaverAppConfigProvider appConfigProvider;
    private final boolean isTerminateIdentity;

    public LeaverPlanBuilder(SailPointContext context,
                      String identityName,
                      Map<String,Object> additionalArgs,
                      LeaverAppConfigProvider appConfigProvider,
                      boolean isTerminateIdentity) {
        this.context = context;
        this.identityName = identityName;
        this.additionalArgs = Collections.unmodifiableMap(additionalArgs);
        this.appConfigProvider = appConfigProvider;
        this.isTerminateIdentity = isTerminateIdentity;
    }

    public SailPointContext getContext() {
        return this.context;
    }

    public String getIdentityName() {
        return this.identityName;
    }
    private interface LinkAction {
        void addRequest(String applicationName, ConfiguredLeaverRequest configuredRequests) throws GeneralException;
    };

    public ProvisioningPlan buildPlan() throws GeneralException {
        logDebugMessage("Begin: buildPlan for identity " + identityName);

        SailPointContext context = getContext();
        Identity identity = getIdentity(context, identityName);

        try {
            String comments = null;
            if (!Util.isEmpty(additionalArgs)) {
                comments = (String)additionalArgs.get("reasonComments");
            }

            // create empty plan
            final ProvisioningPlan plan = initProvisioningPlan(identity, comments);

            // add role removal requests (if any) to plan
            List<AccountRequest> roleRemovalRequests = buildRoleRemovalRequests(identity);
            for(AccountRequest req : Util.safeIterable(roleRemovalRequests)) {
                plan.add(req);
            }

            processApps(LeaverAppConfigProvider.OPT_MODE_IMMEDIATE, identity, (applicationName, configuredRequests) -> {
                boolean newAccountRequest = true;
                for (AccountRequest accountRequest : Util.safeIterable(configuredRequests.getAccountRequests())) {
                    for (AccountRequest currentRequest : Util.safeIterable(plan.getAccountRequests())) {
                        if (currentRequest.isMatch(accountRequest)) {
                            newAccountRequest = false;
                            break;
                        }
                    }
                    // add an account request only if it is new, rules could duplicate them
                    // in the case that there are multiple accounts for an identity on the same app
                    if (newAccountRequest) {
                        plan.add(accountRequest);
                        newAccountRequest = true;
                    }
                }
            });

            if (plan.isEmpty()) {
                logDebugMessage("Plan is empty");
                logDebugMessage("Exit: buildPlan for identity " + identityName);
                return null;
            }

            logDebugMessage("Exit: buildPlan for identity " + identityName);
            return plan;
        }
        finally {
            context.decache(identity);
        }
    }

    public Map<String, List<DeferredPlan>> buildDeferredPlans() throws GeneralException {
        logDebugMessage("Begin: buildAppPlans for identity " + identityName);
        final Map<String, List<DeferredPlan>> appPlans =
                new HashMap<String, List<DeferredPlan>>();

        SailPointContext context = getContext();
        Identity identity = getIdentity(context, identityName);

        try {
            final String comments;
            if (!Util.isEmpty(additionalArgs)) {
                comments = (String)additionalArgs.get("reasonComments");
            } else {
                comments = null;
            }

            processApps(LeaverAppConfigProvider.OPT_MODE_LATER, identity, (applicationName, configuredRequests) -> {
                // There is the possibility of a special case where we need two deferred plans to be created for
                // this one set of configuredRequests. If the Deferred plan is to contain both a DeleteAccount option
                // and also a nonDeleteAccount option such as entitlement like disable account.
                // In this case, one plan for the DeleteAccount is needed (as its delay date will differ),
                // and another plan for the nonDeleteAccount options is needed.
                if (configuredRequests.isDeleteAccount() && configuredRequests.isNonDeleteAccount()) {
                    // First check for valid staggered dates on both delays.
                    // Throw error if the XML is a misconfiguration instead of creating deferred plans
                    int delayDeleteAccount = configuredRequests.getRequestConfigProvider().getDeleteDelayDays(applicationName, identity);
                    int delayNonDeleteAccount = configuredRequests.getRequestConfigProvider().getEntitlementDelayDays(applicationName, identity);
                    if (delayDeleteAccount <= delayNonDeleteAccount) {
                        throw new GeneralException("Expected a number for " + DefaultLeaverAppConfigProvider.OPT_DELETE_ACCT_DELAY
                                + " that is greater than " + DefaultLeaverAppConfigProvider.OPT_ENTITLEMENT_DELAY);
                    }
                    // If the application does not have a list of deferred plans yet, create an empty list
                    if (appPlans.get(applicationName) == null) {
                        appPlans.put(applicationName, new ArrayList<DeferredPlan>());
                    }
                    List<DeferredPlan> applicationDeferredPlans = appPlans.get(applicationName);
                    // init a new plan for this applications delete account options
                    ProvisioningPlan planDeleteAccount = initProvisioningPlan(identity, comments);
                    // add delete account requests if any exist
                    for (AccountRequest accountRequest : Util.safeIterable(configuredRequests.getAccountRequests())) {
                        if (accountRequest.getOperation() == AccountRequest.Operation.Delete) {
                            planDeleteAccount.add(accountRequest);
                        }
                    }

                    if (!planDeleteAccount.isEmpty()) {
                        DeferredPlan deferredPlanDeleteAccount = new DeferredPlan(delayDeleteAccount, planDeleteAccount);
                        // add the deferred plan to the list of plans for this app and put it into the appPlans
                        applicationDeferredPlans.add(deferredPlanDeleteAccount);
                        appPlans.put(applicationName, applicationDeferredPlans);
                    }

                    // second the remaining NonDeleteAccount accountRequests plan that will have a different delay value
                    ProvisioningPlan planNonDeleteAccount = initProvisioningPlan(identity, comments);
                    // add non delete account requests if any exist
                    for (AccountRequest accountRequest : Util.safeIterable(configuredRequests.getAccountRequests())) {
                        if (accountRequest.getOperation() != AccountRequest.Operation.Delete) {
                            planNonDeleteAccount.add(accountRequest);
                        }
                    }

                    if (!planNonDeleteAccount.isEmpty()) {
                        DeferredPlan deferredPlanNonDeleteAccount = new DeferredPlan(delayNonDeleteAccount, planNonDeleteAccount);
                        applicationDeferredPlans.add(deferredPlanNonDeleteAccount);
                        appPlans.put(applicationName, applicationDeferredPlans);
                    }
                }
                // This is the remaining cases where one of the following is true
                // 1. Deferred plan only contains DeleteAccount option
                // 2. Deferred plan only contains nonDeleteAccount options (entitlement, disable, etc)
                //  In these cases a single deferred plan is sufficient as there would only be a single delay time
                else {
                    // If the application does not have a list of deferred plans yet, create an empty list
                    if (appPlans.get(applicationName) == null) {
                        appPlans.put(applicationName, new ArrayList<DeferredPlan>());
                    }
                    List<DeferredPlan> applicationDeferredPlans = appPlans.get(applicationName);
                    // init a new plan for this applications deferred account requests
                    ProvisioningPlan plan = initProvisioningPlan(identity, comments);
                    // add all account requests if any exist
                    for (AccountRequest accountRequest : Util.safeIterable(configuredRequests.getAccountRequests())) {
                        plan.add(accountRequest);
                    }

                    if (!plan.isEmpty()) {
                        int delay;
                        if (configuredRequests.isDeleteAccount()) {
                            delay = configuredRequests.getRequestConfigProvider().
                                    getDeleteDelayDays(applicationName, identity);
                        } else {
                            delay = configuredRequests.getRequestConfigProvider().
                                    getEntitlementDelayDays(applicationName, identity);
                        }
                        DeferredPlan deferredPlan = new DeferredPlan(delay, plan);
                        applicationDeferredPlans.add(deferredPlan);
                        appPlans.put(applicationName, applicationDeferredPlans);
                    }
                }
            });
            return appPlans;
        }
        finally {
            context.decache(identity);
        }
    }

    public ConfiguredLeaverRequest getAppRequests(
            SailPointContext context, String identityName, String appName, String mode, String nativeID)
            throws GeneralException {
        Identity identity = getIdentity(context, identityName);
        String reqType = null;
        if (!Util.isEmpty(additionalArgs)) {
            reqType = (String)additionalArgs.get("requestType");
        }

        IdentityService idService = new IdentityService(context);
        List<Link> links = idService.getLinks(identity, 0, 0);
        if(appName != null) {
            for (Link link : links) {
                if (appName.equalsIgnoreCase(link.getApplicationName()) &&
                        nativeID.equals(link.getNativeIdentity())) {
                    return buildLeaveLinkRequests(mode, link, identity, reqType, false);
                }
            }
        }

        return new ConfiguredLeaverRequest(appConfigProvider);
    }

    protected void processApps(String mode, Identity identity, LinkAction linkAction) throws GeneralException {
        // then, cleanup application-specific
        IdentityService idService = new IdentityService(context);
        int countLinks = idService.countLinks(identity);
        if (countLinks > 0) {
            String reqType = null;
            if (!Util.isEmpty(additionalArgs)) {
                reqType = (String)additionalArgs.get("requestType");
            }
            List<Link> links = idService.getLinks(identity,0,0);
            for (Link link : Util.safeIterable(links)) {
                if (isSupportedLink(link)) {
                    ConfiguredLeaverRequest configuredRequests =
                            buildLeaveLinkRequests(mode, link, identity, reqType, true);
                    linkAction.addRequest(link.getApplicationName(), configuredRequests);
                }
            }
        }
    }


    /**
     * Validate that the link is not null, that its corresponding
     * application that has been onboarded into RapidSetup, and that
     * that the link contains the required fields
     * to support building  a plan.
     * @param link the Link to validate
     * @return true if link is a supported Link, otherwise false
     */
    private boolean isSupportedLink(Link link) {
        if (link == null) {
            logDebugMessage("Unexpected null link");
            return false;
        }
        Application app = link.getApplication();
        if (app == null) {
            logDebugMessage("Unexpected null link application");
            return false;
        }
        String appName = app.getName();
        if (Util.isNullOrEmpty(appName)) {
            logDebugMessage("Unexpected null link application name");
            return false;
        }
        if (!RapidSetupConfigUtils.isApplicationConfigured(app.getName())) {
            logDebugMessage("Skipping app " + app.getName() + " because has no RapidSetup config");
            return false;
        }
        String nativeId = link.getNativeIdentity();
        if (Util.isNullOrEmpty(nativeId)) {
            logDebugMessage("Unexpected null link nativeIdentity");
            return false;
        }

        return true;
    }

    /**
     * Convenience method to create the list of AccountRequest objects needed to
     * remove the roles currently assigned to the identity
     * @param identity the identity that roles are to be removed from
     * @return the list of AccountRequest objects
     * @throws GeneralException unexpected database exception
     */
    private List<AccountRequest> buildRoleRemovalRequests(Identity identity) throws GeneralException {
        Map config = RapidSetupConfigUtils.getRapidSetupBusinessProcessConfiguration(
                isTerminateIdentity() ? Configuration.RAPIDSETUP_CONFIG_TERMINATE :
                        Configuration.RAPIDSETUP_CONFIG_LEAVER);
        LeaverRolePlanner rolePlanner = new LeaverRolePlanner(config);
        List<AccountRequest> reqs = rolePlanner.buildRequests(context, identity);
        return reqs;
    }

    /**
     * Build the list of AccountRequest
     * that will achieve the identity properly leaving the application.  What all this will do
     * is highly dependent on the rapidsetup global configuration, the application's rapidsetup
     * config, and the mode.
     * @param link the link that we are building the list of AccountRequest to accomplish leaving
     * @param identity the identity that is leaving
     * @param requestType what we are performing, usually "leaver"
     * @param processRule true if config rule is to be processed, false
     *                    if not.  This is set to false by getAppRequests
     *                    which is meant to be called from rules.  This avoids
     *                    infinite recursion.
     * @return the list of AccountRequest to achieve leaving the link
     * @throws GeneralException unexpected database exception
     */
    private ConfiguredLeaverRequest buildLeaveLinkRequests(
            String mode, Link link, Identity identity, String requestType,
            boolean processRule) throws GeneralException {
        logDebugMessage("Enter buildLeaveLinkRequests");

        String appName = link.getApplicationName();
        logDebugMessage("..link application = " + appName);

        ConfiguredLeaverRequest configuredRequests =
                new ConfiguredLeaverRequest(appConfigProvider);
        configuredRequests.setAccountRequests(new ArrayList<AccountRequest>());

        if (!Util.isEmpty(appName)) {
            String ruleName = getPlanRule(appName);
            boolean isUseRule = getUseRule(appName);
            if ((processRule) && isUseRule && (Util.isNotNullOrEmpty(ruleName))) {
                addRequestsFromRule(mode, ruleName, configuredRequests, link, requestType, identity);
            } else if ((!appConfigProvider.isEmpty(appName, identity))) {
                if (appConfigProvider.isConfigured(appName, identity, mode, LeaverAppConfigProvider.OPT_DELETE_ACCOUNT)) {
                    addDeleteAccount(configuredRequests.getAccountRequests(), link);
                    configuredRequests.setDeleteAccount(true);
                }
                if (!appConfigProvider.isConfigured(appName, identity, mode, LeaverAppConfigProvider.OPT_DELETE_ACCOUNT)
                        || (mode == LeaverAppConfigProvider.OPT_MODE_LATER &&
                        appConfigProvider.isConfigured(appName, identity, LeaverAppConfigProvider.OPT_MODE_LATER, LeaverAppConfigProvider.OPT_DELETE_ACCOUNT))) {
                    if (appConfigProvider.isConfigured(appName, identity, mode, LeaverAppConfigProvider.OPT_REMOVE_ENTITLEMENTS)) {
                        addRemoveEntitlementRequests(configuredRequests.getAccountRequests(), link, identity);
                        configuredRequests.setNonDeleteAccount(true);
                    }
                    if (appConfigProvider.isConfigured(appName, identity, mode, LeaverAppConfigProvider.OPT_SCRAMBLE_PASSWORD)) {
                        addScramblePassword(configuredRequests.getAccountRequests(), link, identity);
                        configuredRequests.setNonDeleteAccount(true);
                    }
                    if (appConfigProvider.isConfigured(appName, identity, mode, LeaverAppConfigProvider.OPT_ADD_COMMENT)) {
                        addComment(configuredRequests.getAccountRequests(), link, identity);
                        configuredRequests.setNonDeleteAccount(true);
                    }
                    if (appConfigProvider.isConfigured(appName, identity, mode, LeaverAppConfigProvider.OPT_DISABLE_ACCOUNT)) {
                        addDisableAccount(configuredRequests.getAccountRequests(), link);
                        configuredRequests.setNonDeleteAccount(true);
                    }
                    if (appConfigProvider.isConfigured(appName, identity, mode, LeaverAppConfigProvider.OPT_MOVE_ACCOUNT)) {
                        // The option to move later (an account) is not supported in 8.1p1.
                        if(Util.nullSafeEq(mode, appConfigProvider.OPT_MODE_LATER)) {
                            throw new GeneralException("The option to move later is not supported");
                        }
                        addMoveAccount(configuredRequests.getAccountRequests(), link, identity);
                        configuredRequests.setNonDeleteAccount(true);
                    }
                }
            } else {
                logDebugMessage("skipping ... empty " + requestType + " config for application  " + appName);
            }
        } else {
            logDebugMessage("skipping ... empty app name");
        }

        logDebugMessage("End buildLeaveLinkRequests..." + appName);
        return configuredRequests;
    }

    private String getPlanRule(String appName) {
        Map<String, Object> leaverConfig = RapidSetupConfigUtils.getApplicationBusinessProcessConfig(
                appName, Configuration.RAPIDSETUP_CONFIG_LEAVER);
        return RapidSetupConfigUtils.getString(leaverConfig, AbstractLeaverAppConfigProvider.OPT_PLAN_RULE);
    }

    private boolean getUseRule(String appName) {
        Map<String, Object> leaverConfig = RapidSetupConfigUtils.getApplicationBusinessProcessConfig(
                appName, Configuration.RAPIDSETUP_CONFIG_LEAVER);
        return RapidSetupConfigUtils.getBoolean(leaverConfig, AbstractLeaverAppConfigProvider.OPT_USE_RULE);
    }

    /**
     * Add to accountRequests any additional requests needed to scramble the password on the link
     * @param link the link that we are scrambling the password on
     * @param identity the identity that is leaving
     * @return void, although the accountRequests maye be updated with additional AccountRequest.
     * @throws GeneralException unexpected database exception
     */
    private void addScramblePassword(List<AccountRequest> accountRequests,
                                     Link link,
                                     Identity identity) throws GeneralException
    {

        Application app = link.getApplication();
        String appName = app.getName();
        String nativeId = link.getNativeIdentity();

        // First, check if the application has defined a custom scramble password attribute.
        // This is typically only needed for custom or in-house applications
        String scramblePasswordAttr = appConfigProvider.getPasswordAttribute(appName, identity);

        if (Util.isNullOrEmpty(scramblePasswordAttr)) {
            // Try password attribute from LDAP Connector attribute "passwordAttr"
            scramblePasswordAttr = (String)app.getAttributeValue("passwordAttr");
            if (Util.isNullOrEmpty(scramblePasswordAttr)) {
                // Give up and use the default connector password attribute
                scramblePasswordAttr = ProvisioningPlan.ATT_PASSWORD;
                logDebugMessage("Using default scramble password atttribute " + scramblePasswordAttr);
            }
            else {
                logDebugMessage("Using (passwordAttr) scramble password atttribute " + scramblePasswordAttr);
            }
        }
        else {
            logDebugMessage("Using overridden scramble password attribute " + scramblePasswordAttr);
        }

        if(scramblePasswordAttr != null) {

            // Build new scrambled password value
            String newPassword = null;
            PasswordGenerator pg = new PasswordGenerator(context);
            try {
                newPassword = pg.generatePassword(identity, app);
            }
            catch (GeneralException ex) {
                log.error("No password policy : " + ex.getMessage());
                newPassword = pg.generatePassword();
            }

            logDebugMessage("AttributeRequest to scramble password in attribute " + scramblePasswordAttr);

            // Build new AttributeRequest to change value
            AttributeRequest passwordAttributeRequest = generateSetAttributeRequest(scramblePasswordAttr, newPassword);
            passwordAttributeRequest.setDisplayValue("******");

            // Place the AttributeRequest into an AccountRequest
            AccountRequest acctReq = new AccountRequest(AccountRequest.Operation.Modify, appName, null, nativeId);
            acctReq.setNativeIdentity(nativeId);
            acctReq.add(passwordAttributeRequest);

            // Add the AccountRequest to existing list
            accountRequests.add(acctReq);
        }
    }

    /**
     * Add to accountRequests any additional requests needed to set a comment
     * value into an attribute of the link account.  The comment value and the name
     * of the attribute are configured on the application optionally with rapidsetup.
     * @param link the link that we are adding a comment to
     * @return void, although the accountRequests maye be updated with additional AccountRequest.
     */
    private void addComment(List<AccountRequest> accountRequests,
                            Link link, Identity identity)
    {

        Application app = link.getApplication();
        String appName = app.getName();
        String nativeId = link.getNativeIdentity();

        String commentAttr = appConfigProvider.getCommentAttribute(appName, identity);
        String comment = appConfigProvider.getCommentString(appName, identity);

        if(Util.isNotNullOrEmpty(commentAttr) && Util.isNotNullOrEmpty(comment)) {
            logDebugMessage("AttributeRequest to set comment '" +
                    comment + "' into attribute " + commentAttr);

            // Build new AttributeRequest to change value
            AttributeRequest commentAttributeRequest = generateSetAttributeRequest(commentAttr, comment);

            // Place the AttributeRequest into an AccountRequest
            AccountRequest acctReq = new AccountRequest(AccountRequest.Operation.Modify, appName, null, nativeId);
            acctReq.setNativeIdentity(nativeId);
            acctReq.add(commentAttributeRequest);

            // Add the AccountRequest to existing list
            accountRequests.add(acctReq);
        }
    }

    /**
     * Add to accountRequests any additional requests needed to disable
     * the account identified by link
     * @param link the link that refers to the account we want to disable
     * @return void, although the accountRequests maye be updated with additional AccountRequest.
     * @throws GeneralException unexpected database exception
     */
    private void addDisableAccount(List<AccountRequest> accountRequests, Link link) throws GeneralException
    {

        Application app = link.getApplication();
        String appName = app.getName();
        String nativeId = link.getNativeIdentity();

        // Build disable request
        AccountRequest acctReq = new AccountRequest(AccountRequest.Operation.Disable, appName, null, nativeId);

        // Need to add in "flow" so the account name will be filled in the workitem UI
        acctReq.addArgument(IdentityLibrary.VAR_FLOW, "AccountsRequest");

        logDebugMessage("AccountRequest to disable account " + nativeId  +  " on application " + appName);

        // Add the AccountRequest to existing list
        accountRequests.add(acctReq);
    }

    /**
     * Add to accountRequests any additional requests needed to change the OU of
     * the account identified by link
     * @param link the link that refers to the account we want to disable
     * @return void, although the accountRequests maye be updated with additional AccountRequest.
     * @throws GeneralException unexpected database exception
     */
    private void addMoveAccount(List<AccountRequest> accountRequests,
                                Link link, Identity identity)
    {

        Application app = link.getApplication();
        String appName = app.getName();
        String nativeId = link.getNativeIdentity();

        String moveOU_csv = appConfigProvider.getMoveOU(appName, identity);
        if (Util.isNotNullOrEmpty(moveOU_csv)) {
            String moveOU = LdapTools.findCompatibleOU(moveOU_csv, nativeId);
            if (Util.isNotNullOrEmpty(moveOU)) {
                AttributeRequest newDNAttributeRequest = new AttributeRequest();
                newDNAttributeRequest.setComments("IdentityIQ modified the DN on " + new java.util.Date());
                newDNAttributeRequest.setName("AC_NewParent");
                newDNAttributeRequest.setValue(moveOU);
                newDNAttributeRequest.setOperation(ProvisioningPlan.Operation.Set);

                // Build move request
                logDebugMessage("AccountRequest to move account " + nativeId  +  " on application " + appName + " to OU " + moveOU);
                AccountRequest acctReq = new AccountRequest(AccountRequest.Operation.Modify, appName, null, nativeId);
                acctReq.add(newDNAttributeRequest);
                accountRequests.add(acctReq);
            }
        }
    }

    /**
     * Add to accountRequests any additional requests needed to delete
     * the account identified by link
     * @param link the link that refers to the account we want to delete
     * @return void, although the accountRequests maye be updated with additional AccountRequest.
     */
    private void addDeleteAccount(List<AccountRequest> accountRequests, Link link)
    {
        Application app = link.getApplication();
        String appName = app.getName();
        String nativeId = link.getNativeIdentity();

        // Build delete request
        AccountRequest acctReq = new AccountRequest(AccountRequest.Operation.Delete, appName, null, nativeId);

        // Need to add in "flow" so the account name will be filled in the workitem UI
        acctReq.addArgument(IdentityLibrary.VAR_FLOW, "AccountsRequest");

        logDebugMessage("AccountRequest to delete account " + nativeId  +  " on application " + appName);

        // Add the AccountRequest to existing list
        accountRequests.add(acctReq);
    }

    /**
     * Add to accountRequests any additional AccountRequest objects returned
     * from the given rule
     * @param ruleName the name of the rule to execute to get a list of AccountRequest
     * @param link the link that refers to the account we want to delete
     * @param requestType what we are performing, usually "leaver"
     * @param identity the identity that is leaving
     * @return void, although the accountRequests maye be updated with additional AccountRequest.
     * @throws GeneralException unexpected database exception
     */
    private void addRequestsFromRule(String mode,
                            String ruleName,
                            ConfiguredLeaverRequest configuredRequests,
                            Link link,
                            String requestType,
                            Identity identity) throws GeneralException
    {
        String appName = link.getApplicationName();
        String nativeId = link.getNativeIdentity();

        if (Util.isNotNullOrEmpty(ruleName)) {
            Rule rule = context.getObjectByName(Rule.class, ruleName);
            if (rule != null) {
                try {
                    HashMap<String,Object> params = new HashMap<>();
                    params.put("context", context);
                    params.put("identityName", identity.getName());
                    params.put("appName", appName);
                    params.put("nativeId", nativeId);
                    params.put("requestType", requestType);
                    params.put("mode", mode);
                    params.put("leaverPlanBuilder", this);
                    params.put("log", log);
                    logDebugMessage("Executing rule " + ruleName + " to get AccountRequest list");
                    Object ruleResultObj = context.runRule(rule, params);
                    if (ruleResultObj instanceof ConfiguredLeaverRequest) {
                        ConfiguredLeaverRequest ruleResult = (ConfiguredLeaverRequest)ruleResultObj;
                        List ruleResultList = ruleResult.getAccountRequests();
                        logDebugMessage("Rule returned " + Util.nullSafeSize(ruleResultList) + " objects");
                        configuredRequests.setAccountRequests(ruleResultList);
                        configuredRequests.setRequestConfigProvider(ruleResult.getRequestConfigProvider());
                        configuredRequests.setDeleteAccount(ruleResult.isDeleteAccount());
                        configuredRequests.setNonDeleteAccount(ruleResult.isNonDeleteAccount());
                        if(configuredRequests.getRequestConfigProvider() == null) {
                            configuredRequests.setRequestConfigProvider(appConfigProvider);
                        }
                    } else if (ruleResultObj != null ) {
                        throw new GeneralException("Unexpected return value of type " + ruleResultObj.getClass().getName() + " from rule " + ruleName);
                    } else {
                        throw new GeneralException("Unexpected null returned from rule " + ruleName);
                    }
                }
                finally {
                    context.decache(rule);
                }
            }
            else {
                throw new GeneralException("Cannot find rule '" + ruleName + "' during " + requestType + " planning");
            }
        }
    }

    /**
     * Add to accountRequests any additional requests needed to remove entitlements from
     * the given link.
     * @param link the link that we are wanting to remove entitlements from
     * @param identity the identity that is leaving
     * @return void, although the accountRequests maye be updated with additional AccountRequest.
     * @throws GeneralException unexpected database exception
     */
    private void addRemoveEntitlementRequests(List<AccountRequest> accountRequests,
                                              Link link,
                                              Identity identity) throws GeneralException
    {
            String appName = link.getApplicationName();
            String nativeId = link.getNativeIdentity();
            List<GenericRequest> reqs =
                    getEntitlementRemovalRequests(appName, identity, nativeId);

            // add into accountRequests
            if (!Util.isEmpty(reqs)) {
                for (GenericRequest newAttrReq : reqs) {
                    AccountRequest acctReq = new AccountRequest(AccountRequest.Operation.Modify, appName, null, nativeId);
                    acctReq.setNativeIdentity(nativeId);
                    acctReq.add(newAttrReq);
                    if (acctReq != null) {
                        accountRequests.add(acctReq);
                    }
                }
            }
    }

    /**
     * @return the list AttributeRequest to remove the entitlements for the identity on the application for the
     * given native id
     */
    private List<GenericRequest> getEntitlementRemovalRequests(String appName,
                                                                 Identity identity,
                                                                 String nativeId) throws GeneralException
    {
        List<GenericRequest> requests = new ArrayList<>();
        Iterator<Object[]> rows = LinkAndEntitlementTools.getIdentityEntitlementIdIterator(
                context, identity, appName, nativeId);
        if (rows != null) {
            requests = getEntitlementRemovalRequestsById(rows, identity);
            Util.flushIterator(rows);
        }
        return requests;
    }

    /**
     * @return the list of requests to remove the entitlements (which are
     * passed in via iterator)
     */
    private List<GenericRequest> getEntitlementRemovalRequestsById(
            Iterator<Object[]> rows, Identity identity) throws GeneralException
    {
        ArrayList<GenericRequest> requests = new ArrayList<>();
        if (rows != null)  {
            Set<String> bRoleNames = null;

            while (rows.hasNext())  {
                Object[] identityEntIdArr = rows.next();
                if (identityEntIdArr == null || identityEntIdArr.length != 1 ||
                        identityEntIdArr[0] == null) {
                    // nothing to do here
                    continue;
                }

                String identityEntId = (String)identityEntIdArr[0];
                logDebugMessage("..identityEntId.." + identityEntId);
                IdentityEntitlement entitlement = context.getObjectById(IdentityEntitlement.class, identityEntId);

                //FOUND ITEM LETS START ATTRIBUTE REQUEST

                //ATTRIBUTE REQUEST TO REMOVE ON BY DEFAULT
                boolean addAttributeRequest = true;

                if (entitlement != null) {
                    try {
                        String appName = entitlement.getAppName();
                        if (log.isDebugEnabled()) {
                            logDebugMessage("...app=" + appName +
                                    ", entitlement=(" + entitlement.getName() + "," + entitlement.getStringValue() + ")");
                        }
                        Attributes attr = entitlement.getAttributes();
                        // ENTITLEMENTS GRANTED BY ROLES NEEDS TO BE REMOVED BY ROLE REMOVAL METHOD
                        if (entitlement.isGrantedByRole()) {
                            // THIS CAN HAPPEN VIA IT DETECTION OR VIA REQUIRED/PERMITTED IT ROLES ON A BUSINESS ROLE
                            if (attr != null) {
                                logDebugMessage("...attr = " + attr);
                                String sourceDetectedRolesCSV = (String)attr.get("sourceDetectedRoles");
                                String sourceAssignableRolesCSV = (String)attr.get("sourceAssignableRoles");
                                if (Util.isNotNullOrEmpty(sourceDetectedRolesCSV)) {
                                    if (Util.isNotNullOrEmpty(sourceAssignableRolesCSV)) {
                                        // IT Role.  This entitlement will be cleaned up
                                        // when its assignable role is removed.
                                        addAttributeRequest = false;
                                    }
                                    else {
                                        addAttributeRequest = true;
                                        List<String> sourceDetectedRoles = Util.csvToList(sourceDetectedRolesCSV);
                                        for (String sourceDetectableRole : Util.safeIterable(sourceDetectedRoles)) {
                                            if (bRoleNames == null) {
                                                bRoleNames = getBirthrightRoleNames();
                                            }
                                            if (bRoleNames.contains(sourceDetectableRole)) {
                                                addAttributeRequest = false;
                                                break;
                                            }
                                        }
                                    }
                                }
                                // REMOVE DIRECT ENTITLEMENTS RIGHT AWAY
                                else {
                                    // DIRECT ENTITLEMENTS, BIRTHRIGHT ROLES WILL BE CAPTURED HERE, THEY ARE ASSIGNED BY ROLES,
                                    // BUT THEY ARE NOT ON CUBE AS ROLE ASSIGNMENTS
                                    addAttributeRequest = true;
                                }
                            }
                        }
                        else {
                            //ITEM NOT GRANTED BY ROLE
                            addAttributeRequest = true;
                        }

                        if (addAttributeRequest) {
                            boolean skipRemoval = false;

                            // If an exception filter exists, exclude entitlements from removal if they match the filter
                            ArrayList<ListFilterValue> exceptionFilters = appConfigProvider.getEntitlementExceptionFilters(appName, identity);
                            for (ListFilterValue exceptionFilter : Util.safeIterable(exceptionFilters)) {
                                if (exceptionFilter != null && exceptionFilter.getProperty().equalsIgnoreCase(entitlement.getName())) {
                                    String filterValue = exceptionFilter.getValue().toString().toLowerCase();
                                    String entitlementValue = entitlement.getStringValue().toLowerCase();

                                    switch (exceptionFilter.getOperation()) {
                                        case Equals:
                                            List<String> values = Util.csvToList(filterValue);
                                            skipRemoval = values.contains(entitlementValue);
                                            break;
                                        case StartsWith:
                                            skipRemoval = entitlementValue.startsWith(filterValue);
                                            break;
                                        case EndsWith:
                                            skipRemoval = entitlementValue.endsWith(filterValue);
                                            break;
                                        case Contains:
                                            skipRemoval = entitlementValue.contains(filterValue);
                                            break;
                                        default:
                                            logDebugMessage("Unable to evaluate entitlement exception filter: unknown or unsupported operation");
                                    }
                                }

                                // Multiple filters are ANDed together. As soon as we encounter a filter that
                                // evaluates to false, break out of the loop.
                                if (!skipRemoval) {
                                    logDebugMessage("Entitlement exception filter evaluates to false. Entitlement will be removed.");
                                    break;
                                }
                            }

                            if (!skipRemoval) {
                                GenericRequest request = entitlement.getType() == ManagedAttribute.Type.Permission ?
                                        new PermissionRequest() :
                                        new AttributeRequest();

                                request.setAssignment(true);
                                request.setOp(ProvisioningPlan.Operation.Remove);

                                if (entitlement.getName() != null) {
                                    request.setName(entitlement.getName());
                                }
                                if (entitlement.getValue() != null) {
                                    request.setValue(entitlement.getValue());
                                }
                                if (entitlement.getName() != null && entitlement.getValue() != null) {
                                    //since we have a name and a value, let's look for the displayValue
                                    ManagedAttribute ma = ManagedAttributer.get(context, entitlement.getApplication(),
                                            entitlement.getName(), (String)entitlement.getValue());
                                    if (ma != null) {
                                        request.setDisplayValue(ma.getDisplayName());
                                    }
                                }
                                requests.add(request);

                                if (log.isDebugEnabled()) {
                                    logDebugMessage("AttributeRequest to remove entitlement " +
                                            entitlement.getName() + "/" + entitlement.getValue() + " from app " + appName);
                                }
                            } else {
                                logDebugMessage("Entitlement exception filter evaluates to true. Entitlement wasn't removed.");
                            }
                        }
                    }
                    finally {
                        context.decache(entitlement);
                    }
                }
            }//End While Loop for Each Row
            Util.flushIterator(rows);
        }
        else {
            logDebugMessage("No application entitlements found, returning empty list");
        }
        return requests;
    }

    /**
     * @return the set of names of the roles that are of birthright roles.
     * Set will be empty if no birthright roles found.
     */
    private Set<String> getBirthrightRoleNames() throws GeneralException {
        Set<String> bRoleNames = new HashSet<>();
        List<String> birthrightRoleTypes = RapidSetupConfigUtils.getRapidSetupBirthrightRoleTypeNames();
        if (!Util.isEmpty(birthrightRoleTypes)) {
            QueryOptions qo = new QueryOptions();
            qo.add(Filter.in("type", birthrightRoleTypes));
            Iterator<Object[]>  objArr = context.search(Bundle.class, qo, "name");
            if (objArr != null) {
                while (objArr.hasNext()) {
                    String roleName = (String) objArr.next()[0];
                    bRoleNames.add(roleName);
                }
            }
        }
        return bRoleNames;
    }

    Identity getIdentity(SailPointContext context, String identityName) throws GeneralException {
        Identity identity = context.getObjectByName(Identity.class, identityName);
        if (identity == null) {
            logDebugMessage("No plan because identity " + identityName + " not found.");
            return null;
        }
        return identity;
    }

    public boolean isTerminateIdentity() {
        return isTerminateIdentity;
    }

    // this will simply add a message to the log to indicate if we are in terminate or
    // leaver mode.
    private void logDebugMessage(String msg) {
        StringBuilder builder = new StringBuilder();

        if(isTerminateIdentity()) {
            builder.append("(Terminate) ");
        } else {
            builder.append("(Leaver) ");
        }
        builder.append(msg);

        log.debug(builder.toString());
    }
}
