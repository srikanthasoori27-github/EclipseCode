/*
 * (c) Copyright 2020 SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.rapidsetup.plan;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.Matchmaker;
import sailpoint.api.SailPointContext;
import sailpoint.object.Application;
import sailpoint.object.Attributes;
import sailpoint.object.Identity;
import sailpoint.object.IdentitySelector;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.RapidSetupConfigUtils;
import sailpoint.object.RoleAssignment;
import sailpoint.rapidsetup.model.IdentSelectorDTO;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.web.lcm.LcmAccessRequestHelper;

public class JoinerPlanBuilder extends BasePlanBuilder {

    private static Log log = LogFactory.getLog(JoinerPlanBuilder.class);

    /**
     * This is the contract expected for the lambda passed to
     * setAllowAccountOnlyProvisioningFilter.  Called by
     * other plan builders (e.g. Mover) which delegate to
     * Joiner, but may need to turn off account-only
     * provisioning for particular applications.
     */
    public interface AllowAccountOnlyProvisioning {
        /**
         * Return true if the application should be considered
         * for account-only provisioning.
         * @param appName
         * @return
         */
        boolean allow(String appName);
    }

    private SailPointContext context = null;
    private String identityName = null;
    private List<RoleAssignment> birthrightRoleAssignments = null;
    private AllowAccountOnlyProvisioning allowAccountOnlyProvisioningFilter = (app) -> { return true; };

    //TODO: Dan - This is for the tests ... can it be fixed?
    protected JoinerPlanBuilder() {

    }

    public JoinerPlanBuilder(SailPointContext context,
                             String identityName,
                             List<RoleAssignment> birthrightRoleAssignments) {
        this.context = context;
        this.identityName = identityName;
        this.birthrightRoleAssignments = birthrightRoleAssignments;
    }

    public JoinerPlanBuilder setAllowAccountOnlyProvisioningFilter(AllowAccountOnlyProvisioning filter) {
        this.allowAccountOnlyProvisioningFilter = filter;
        return this;
    }

    public SailPointContext getContext() {
        return this.context;
    }

    public String getIdentityName() {
        return this.identityName;
    }

    public ProvisioningPlan buildPlan() throws GeneralException {
        String identityName = getIdentityName();
        log.debug("Begin: Build Joiner Plan for identity " + identityName);

        SailPointContext context = getContext();
        Identity identity = context.getObjectByName(Identity.class, identityName);
        if (identity == null) {
            log.debug("No plan because identity " + identityName + " not found.");
            return null;
        }

        // Which applications can this identity be bare-bone provisioned to?
        List<String> appsForProvisioning = getAppsProvisioningIdentity(identity);

        if(Util.isEmpty(birthrightRoleAssignments) && Util.isEmpty(appsForProvisioning)) {
            log.debug("No birthright roles to assign, and no accounts to provision");
            return null;
        }

        ProvisioningPlan plan = new ProvisioningPlan();
        plan.setIdentity(identity);

        // Add AccountRequests for the birthrole assignments
        for(RoleAssignment roleAssignment : Util.safeIterable(birthrightRoleAssignments)) {
            ProvisioningPlan.AccountRequest acctReq = buildRoleAssignmentAccountRequest(identity, roleAssignment);
            if (acctReq != null) {
                plan.addRequest(acctReq);
            }
        }

        // Add AccountRequests for bare-bone accounts
        for (String provAppName : Util.safeIterable(appsForProvisioning)) {
            ProvisioningPlan.AccountRequest acctReq = buildCreateAccountRequest(context, provAppName, identity);
            if (acctReq != null) {
                plan.addRequest(acctReq);
            }
        }

        LcmAccessRequestHelper.populateAssignmentIds(plan.getAccountRequests());

        if (plan.isEmpty()) {
            log.debug("Plan is empty, setting to null");
            plan = null;
        }

        log.debug("End: Build Joiner Plan for identity " + identityName);
        return plan;
    }

    /**
     * Return a list of the names of applications that are configured to perform
     * bare account provisioning, and for which the identity matches the optional
     * identity selector set on the application.
     * @param identity the identity which is being son
     * @return the names of application what we should create bare accounts for
     * the identity
     * @throws GeneralException if a database error
     */
    private List<String> getAppsProvisioningIdentity(Identity identity)
            throws GeneralException {
        List<String> appsForProvisioning = new ArrayList<>();

        List<String> joinerApplications = RapidSetupConfigUtils.getJoinerProvisioningAppNames();
        for (String appName: joinerApplications)  {
            if (allowAccountOnlyProvisioningFilter != null) {
                boolean includeApp = allowAccountOnlyProvisioningFilter.allow(appName);
                if (!includeApp) {
                    continue;
                }
            }
            Application application = context.getObjectByName(Application.class,appName);
            if (application == null) {
                continue;
            }
            Attributes attrs = application.getAttributes();
            if(attrs != null) {

                IdentitySelector identitySelector = RapidSetupConfigUtils.getApplicationJoinerIdentitySelector(appName);
                if (identitySelector != null) {
                    evaluateIdentitySelector(context, identitySelector, identity, application, appsForProvisioning);
                }
                else {
                    IdentSelectorDTO identSelectorDTO = RapidSetupConfigUtils.getApplicationJoinerIdentSelectorDTO(context, appName);
                    if (identSelectorDTO != null) {
                        identitySelector = identSelectorDTO.convert(context);
                        evaluateIdentitySelector(context, identitySelector, identity, application, appsForProvisioning);
                    }
                    else {
                        if (log.isDebugEnabled()) {
                            log.debug("Identity " + identity.getName() + " to be provisioned account (if not already present) on application "
                                    + application.getName() + " because no selector present");
                        }
                        appsForProvisioning.add(application.getName());
                    }
                }
            }
            if(application != null)  {
                context.decache(application);
            }
        }
        return appsForProvisioning;
    }

    /**
     * If the given Identity matches the given IdentitySelector, then add the given application's name
     * to the appsForProvisioning list.
     * @param context persistence context
     * @param identitySelector the IdentitySelector to match the Identity against
     * @param identity the Identity to match against the IdentitySelector
     * @param application the application that the IdentitySelector is from
     * @param appsForProvisioning the list to possibly the application's name if the identity matches the identitySelector
     * @throws GeneralException an unexpected error occurred
     */
    void evaluateIdentitySelector(SailPointContext context, IdentitySelector identitySelector, Identity identity, Application application, List<String> appsForProvisioning)
            throws GeneralException
    {
        Matchmaker match = new Matchmaker(context);
        boolean matches =  match.isMatch(identitySelector, identity);
        if (matches) {
            if (log.isDebugEnabled()) {
                log.debug("Identity " + identity.getName() + " to be provisioned account (if not already present) on application "
                        + application.getName() + " because selector passed");
            }
            appsForProvisioning.add(application.getName());
        }
        else {
            if (log.isDebugEnabled()) {
                log.debug("Identity " + identity.getName() + " will not be provisioned account on application " + application.getName() +
                        " because selector denied");
            }
        }
    }

}
